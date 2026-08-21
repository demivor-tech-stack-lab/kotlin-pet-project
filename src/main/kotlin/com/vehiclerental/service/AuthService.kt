package com.vehiclerental.service

import com.vehiclerental.config.AppConfig
import com.vehiclerental.config.TransactionRunner
import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.domain.model.User
import com.vehiclerental.domain.model.UserRole
import com.vehiclerental.dto.AuthResponse
import com.vehiclerental.dto.ChangePasswordRequest
import com.vehiclerental.dto.LoginRequest
import com.vehiclerental.dto.RegisterRequest
import com.vehiclerental.dto.UserResponse
import com.vehiclerental.dto.toResponse
import com.vehiclerental.repository.RefreshTokenRepository
import com.vehiclerental.repository.UserRepository
import com.vehiclerental.security.JwtService
import com.vehiclerental.security.PasswordHasher
import com.vehiclerental.security.SecureTokens
import com.vehiclerental.util.TimeProvider
import org.slf4j.LoggerFactory

/** Thông tin về thiết bị gọi API, lưu kèm refresh token để người dùng biết phiên nào là của mình. */
data class ClientInfo(
    val userAgent: String?,
    val ipAddress: String?
)

/**
 * TẦNG SERVICE: chứa LOGIC NGHIỆP VỤ.
 *
 * Nguyên tắc phân chia trách nhiệm:
 *   Route      -> nhận HTTP, đọc body/param, gọi service, trả response. KHÔNG có if nghiệp vụ.
 *   Service    -> kiểm tra quy tắc nghiệp vụ, MỞ TRANSACTION, điều phối nhiều repository.
 *   Repository -> chỉ đọc/ghi DB trong transaction có sẵn, KHÔNG biết gì về nghiệp vụ.
 *
 * ⚠️ Service là nơi DUY NHẤT được mở transaction (`tx.tx { }`).
 * Một use case = một transaction: hoặc thành công trọn vẹn, hoặc rollback sạch.
 */
class AuthService(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val jwtService: JwtService,
    private val jwtConfig: AppConfig.JwtConfig,
    private val timeProvider: TimeProvider
) {

    private val logger = LoggerFactory.getLogger(AuthService::class.java)

    suspend fun register(request: RegisterRequest, client: ClientInfo): AuthResponse {
        validateRegister(request)
        val email = request.email.trim().lowercase()

        // Cả việc tạo user lẫn phát refresh token nằm trong MỘT transaction:
        // không thể xảy ra cảnh user được tạo nhưng token thì không.
        return tx.tx {
            if (userRepository.findByEmail(email) != null) {
                throw AppException.Conflict("Email $email đã được đăng ký", "EMAIL_EXISTS")
            }

            val user = userRepository.create(
                email = email,
                passwordHash = PasswordHasher.hash(request.password),
                fullName = request.fullName.trim(),
                phone = request.phone.trim(),
                role = UserRole.CUSTOMER   // đăng ký công khai luôn là CUSTOMER
            )

            logger.info("Tai khoan moi duoc tao: userId={}", user.id)
            issueTokens(user, client)
        }
    }

    suspend fun login(request: LoginRequest, client: ClientInfo): AuthResponse {
        val email = request.email.trim().lowercase()

        return tx.tx {
            val user = userRepository.findByEmail(email)

            // CỐ Ý trả về cùng một thông báo cho cả 2 trường hợp (sai email / sai mật khẩu):
            // nếu báo rõ "email không tồn tại" thì kẻ tấn công dò được email nào có thật.
            //
            // Ngoài ra vẫn chạy verify() với một hash giả khi user không tồn tại,
            // để thời gian phản hồi hai trường hợp bằng nhau — chống dò qua thời gian
            // (timing attack).
            if (user == null) {
                PasswordHasher.verify(request.password, DUMMY_HASH)
                logger.warn("Dang nhap that bai: email khong ton tai")
                throw AppException.Unauthorized("Email hoặc mật khẩu không đúng")
            }

            if (!PasswordHasher.verify(request.password, user.passwordHash)) {
                logger.warn("Dang nhap that bai: sai mat khau, userId={}", user.id)
                throw AppException.Unauthorized("Email hoặc mật khẩu không đúng")
            }

            issueTokens(user, client)
        }
    }

    /**
     * ĐỔI REFRESH TOKEN LẤY ACCESS TOKEN MỚI — có XOAY VÒNG TOKEN (rotation).
     *
     * Mỗi lần refresh, token cũ bị thu hồi và phát ra token mới. Nhờ vậy nếu một
     * refresh token bị đánh cắp và kẻ trộm dùng trước, thì lần refresh tiếp theo của
     * người dùng thật sẽ thất bại — dấu hiệu rõ ràng để phát hiện sự cố.
     */
    suspend fun refresh(rawToken: String, client: ClientInfo): AuthResponse {
        if (rawToken.isBlank()) {
            throw AppException.BadRequest("Thiếu refreshToken", "MISSING_REFRESH_TOKEN")
        }

        val tokenHash = SecureTokens.hash(rawToken)

        return tx.tx {
            val stored = refreshTokenRepository.findByHash(tokenHash)
                ?: throw AppException.Unauthorized("Refresh token không hợp lệ")

            if (!stored.isUsable(timeProvider.now())) {
                logger.warn("Refresh token het han hoac da bi thu hoi: userId={}", stored.userId)
                throw AppException.Unauthorized("Refresh token đã hết hạn hoặc bị thu hồi")
            }

            val user = userRepository.findById(stored.userId)
                ?: throw AppException.Unauthorized("Tài khoản không còn tồn tại")

            // Thu hồi token vừa dùng rồi mới phát token mới — cùng một transaction.
            refreshTokenRepository.revoke(stored.id)
            issueTokens(user, client)
        }
    }

    /** Đăng xuất khỏi thiết bị hiện tại: chỉ thu hồi đúng refresh token được gửi lên. */
    suspend fun logout(rawToken: String) {
        if (rawToken.isBlank()) return

        tx.tx {
            refreshTokenRepository.findByHash(SecureTokens.hash(rawToken))?.let { stored ->
                refreshTokenRepository.revoke(stored.id)
                logger.info("Dang xuat: userId={}", stored.userId)
            }
        }
        // Cố ý KHÔNG báo lỗi khi token không tồn tại: logout phải luôn "thành công"
        // dưới góc nhìn client, và không tiết lộ token nào có thật.
    }

    /** Đăng xuất khỏi TẤT CẢ thiết bị. */
    suspend fun logoutAll(userId: Long): Int = tx.tx {
        val count = refreshTokenRepository.revokeAllForUser(userId)
        logger.info("Dang xuat tat ca thiet bi: userId={}, thu hoi {} token", userId, count)
        count
    }

    suspend fun changePassword(userId: Long, request: ChangePasswordRequest) {
        if (request.newPassword.length < MIN_PASSWORD_LENGTH) {
            throw AppException.BadRequest(
                "Mật khẩu mới phải có ít nhất $MIN_PASSWORD_LENGTH ký tự",
                "WEAK_PASSWORD"
            )
        }
        if (request.oldPassword == request.newPassword) {
            throw AppException.BadRequest("Mật khẩu mới phải khác mật khẩu cũ", "SAME_PASSWORD")
        }

        tx.tx {
            val user = userRepository.findById(userId)
                ?: throw AppException.NotFound("người dùng", userId)

            if (!PasswordHasher.verify(request.oldPassword, user.passwordHash)) {
                throw AppException.Unauthorized("Mật khẩu hiện tại không đúng")
            }

            userRepository.updatePasswordHash(userId, PasswordHasher.hash(request.newPassword))

            // QUAN TRỌNG: đổi mật khẩu phải đá văng mọi phiên đang đăng nhập,
            // vì lý do đổi mật khẩu thường là "nghi bị lộ tài khoản".
            refreshTokenRepository.revokeAllForUser(userId)
            logger.info("Doi mat khau thanh cong, da thu hoi toan bo phien: userId={}", userId)
        }
    }

    suspend fun me(userId: Long): UserResponse = tx.tx {
        userRepository.findById(userId)?.toResponse()
            ?: throw AppException.NotFound("người dùng", userId)
    }

    /** Dọn refresh token đã quá hạn. Được job nền gọi định kỳ. */
    suspend fun cleanupExpiredTokens(): Int = tx.tx {
        refreshTokenRepository.deleteExpiredBefore(timeProvider.now())
    }

    // ----- hàm phụ trợ -----

    /**
     * Phát cặp token mới. Hàm này PHẢI được gọi bên trong transaction có sẵn
     * (nó ghi vào bảng refresh_tokens) — nên nó là private và không tự mở dbQuery.
     */
    private suspend fun issueTokens(user: User, client: ClientInfo): AuthResponse {
        val rawRefreshToken = SecureTokens.generate()

        refreshTokenRepository.create(
            userId = user.id,
            tokenHash = SecureTokens.hash(rawRefreshToken),
            expiresAt = timeProvider.now().plusSeconds(jwtConfig.refreshTokenTtlSeconds),
            userAgent = client.userAgent,
            ipAddress = client.ipAddress
        )

        return AuthResponse(
            accessToken = jwtService.generateAccessToken(user),
            refreshToken = rawRefreshToken,
            expiresInSeconds = jwtService.accessTokenTtlSeconds,
            user = user.toResponse()
        )
    }

    private fun validateRegister(request: RegisterRequest) {
        if (!EMAIL_REGEX.matches(request.email.trim())) {
            throw AppException.BadRequest("Email không hợp lệ", "INVALID_EMAIL")
        }
        if (request.password.length < MIN_PASSWORD_LENGTH) {
            throw AppException.BadRequest(
                "Mật khẩu phải có ít nhất $MIN_PASSWORD_LENGTH ký tự",
                "WEAK_PASSWORD"
            )
        }
        if (request.password.length > MAX_PASSWORD_LENGTH) {
            // BCrypt chỉ dùng 72 byte đầu; chặn sớm để tránh hiểu nhầm
            // "mật khẩu 200 ký tự của tôi an toàn hơn".
            throw AppException.BadRequest(
                "Mật khẩu tối đa $MAX_PASSWORD_LENGTH ký tự",
                "PASSWORD_TOO_LONG"
            )
        }
        if (request.fullName.isBlank()) {
            throw AppException.BadRequest("Họ tên không được để trống")
        }
        if (!PHONE_REGEX.matches(request.phone.trim())) {
            throw AppException.BadRequest("Số điện thoại không hợp lệ (9-11 chữ số)", "INVALID_PHONE")
        }
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 8
        private const val MAX_PASSWORD_LENGTH = 72
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        private val PHONE_REGEX = Regex("^0[0-9]{8,10}$")

        /**
         * Hash của một mật khẩu ngẫu nhiên, chỉ dùng để "đốt" thời gian cho bằng
         * trường hợp email có thật. Không bao giờ khớp với input nào.
         */
        private val DUMMY_HASH = PasswordHasher.hash("khong-ai-doan-duoc-chuoi-nay-2026")
    }
}
