package com.vehiclerental.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.vehiclerental.config.AppConfig
import com.vehiclerental.domain.model.User
import com.vehiclerental.util.TimeProvider
import java.util.Date

/**
 * Sinh và kiểm tra ACCESS TOKEN (JWT).
 *
 * Token gồm 3 phần: header.payload.signature
 * - payload chứa claim (userId, role, hạn dùng) — AI CŨNG ĐỌC ĐƯỢC (chỉ là base64),
 *   nên KHÔNG bao giờ nhét mật khẩu / dữ liệu nhạy cảm vào đây.
 * - signature ký bằng secret — chỉ server biết, nên client không sửa được payload.
 *
 * ⚠️ ĐIỂM YẾU CỐ HỮU CỦA JWT: server không lưu trạng thái nào cả, nên **không thu hồi
 * được** một token đã phát ra. Đó là lý do access token ở đây chỉ sống 15 phút
 * (thay vì 24 giờ như bản pet project). Muốn duy trì đăng nhập lâu dài thì dùng
 * refresh token — xem [RefreshTokenService], loại token đó lưu trong DB nên thu hồi được.
 */
class JwtService(
    private val config: AppConfig.JwtConfig,
    private val timeProvider: TimeProvider
) {

    private val algorithm = Algorithm.HMAC256(config.secret)

    val realm: String get() = config.realm
    val accessTokenTtlSeconds: Long get() = config.accessTokenTtlSeconds

    /** Ktor dùng verifier này để tự kiểm tra token trên mỗi request cần đăng nhập. */
    val verifier: JWTVerifier = JWT.require(algorithm)
        .withAudience(config.audience)
        .withIssuer(config.issuer)
        .build()

    fun generateAccessToken(user: User): String {
        val issuedAt = timeProvider.instant()
        return JWT.create()
            .withAudience(config.audience)
            .withIssuer(config.issuer)
            .withSubject(user.id.toString())
            .withClaim(CLAIM_USER_ID, user.id)
            .withClaim(CLAIM_ROLE, user.role.name)
            .withClaim(CLAIM_EMAIL, user.email)
            .withIssuedAt(Date.from(issuedAt))
            .withExpiresAt(Date.from(issuedAt.plusSeconds(config.accessTokenTtlSeconds)))
            .sign(algorithm)
    }

    companion object {
        const val CLAIM_USER_ID = "userId"
        const val CLAIM_ROLE = "role"
        const val CLAIM_EMAIL = "email"
    }
}
