package com.vehiclerental.service

import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.domain.model.UserRole
import com.vehiclerental.dto.AuthResponse
import com.vehiclerental.dto.LoginRequest
import com.vehiclerental.dto.RegisterRequest
import com.vehiclerental.dto.UserResponse
import com.vehiclerental.dto.toResponse
import com.vehiclerental.repository.UserRepository
import com.vehiclerental.security.JwtService
import com.vehiclerental.security.PasswordHasher

/**
 * TẦNG SERVICE: chứa LOGIC NGHIỆP VỤ.
 *
 * Nguyên tắc phân chia trách nhiệm trong dự án này:
 *   Route      -> nhận HTTP, đọc body/param, gọi service, trả response. KHÔNG có if nghiệp vụ.
 *   Service    -> kiểm tra quy tắc nghiệp vụ, tính toán, điều phối nhiều repository.
 *   Repository -> chỉ đọc/ghi DB, KHÔNG biết gì về quy tắc nghiệp vụ.
 *
 * Service nhận repository qua CONSTRUCTOR (constructor injection) chứ không tự
 * new ra bên trong -> khi test có thể truyền vào một repository giả.
 */
class AuthService(
    private val userRepository: UserRepository,
    private val jwtService: JwtService
) {

    suspend fun register(request: RegisterRequest): AuthResponse {
        validateRegister(request)

        // Kiểm tra trùng email ở tầng service để trả lỗi 409 thân thiện,
        // thay vì để DB ném lỗi unique constraint thành 500.
        if (userRepository.findByEmail(request.email) != null) {
            throw AppException.Conflict("Email ${request.email} đã được đăng ký", "EMAIL_EXISTS")
        }

        val user = userRepository.create(
            email = request.email.trim().lowercase(),
            passwordHash = PasswordHasher.hash(request.password),
            fullName = request.fullName.trim(),
            phone = request.phone.trim(),
            role = UserRole.CUSTOMER   // đăng ký công khai luôn là CUSTOMER
        )

        return AuthResponse(
            token = jwtService.generateToken(user),
            expiresInSeconds = jwtService.expiresInSeconds,
            user = user.toResponse()
        )
    }

    suspend fun login(request: LoginRequest): AuthResponse {
        val user = userRepository.findByEmail(request.email.trim().lowercase())
            ?: throw AppException.Unauthorized("Email hoặc mật khẩu không đúng")

        // CỐ Ý trả về cùng một thông báo cho cả 2 trường hợp (sai email / sai mật khẩu):
        // nếu báo rõ "email không tồn tại" thì kẻ tấn công dò được email nào có thật.
        if (!PasswordHasher.verify(request.password, user.passwordHash)) {
            throw AppException.Unauthorized("Email hoặc mật khẩu không đúng")
        }

        return AuthResponse(
            token = jwtService.generateToken(user),
            expiresInSeconds = jwtService.expiresInSeconds,
            user = user.toResponse()
        )
    }

    suspend fun me(userId: Long): UserResponse {
        val user = userRepository.findById(userId)
            ?: throw AppException.NotFound("người dùng", userId)
        return user.toResponse()
    }

    /**
     * private fun: chỉ dùng nội bộ trong lớp này.
     * Gom validate ra riêng cho register() gọn và dễ đọc.
     */
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
        if (request.fullName.isBlank()) {
            throw AppException.BadRequest("Họ tên không được để trống")
        }
        if (!PHONE_REGEX.matches(request.phone.trim())) {
            throw AppException.BadRequest("Số điện thoại không hợp lệ (9-11 chữ số)", "INVALID_PHONE")
        }
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 6
        private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        private val PHONE_REGEX = Regex("^0[0-9]{8,10}$")
    }
}
