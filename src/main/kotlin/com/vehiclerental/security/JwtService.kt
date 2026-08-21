package com.vehiclerental.security

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.vehiclerental.domain.model.User
import io.ktor.server.config.*
import java.util.Date

/**
 * Sinh và kiểm tra JWT (JSON Web Token).
 *
 * Token gồm 3 phần: header.payload.signature
 * - payload chứa "claim" (userId, role, hạn dùng) — AI CŨNG ĐỌC ĐƯỢC (chỉ là base64),
 *   nên KHÔNG bao giờ nhét mật khẩu / dữ liệu nhạy cảm vào đây.
 * - signature ký bằng `secret` — chỉ server biết, nên client không sửa được payload.
 *
 * `class JwtService(private val config: ApplicationConfig)` là primary constructor:
 * khai báo tham số và thuộc tính cùng lúc.
 */
class JwtService(config: ApplicationConfig) {

    private val secret = config.property("jwt.secret").getString()
    val issuer = config.property("jwt.issuer").getString()
    val audience = config.property("jwt.audience").getString()
    val realm = config.property("jwt.realm").getString()
    val expiresInSeconds = config.property("jwt.expiresInSeconds").getString().toLong()

    private val algorithm = Algorithm.HMAC256(secret)

    /** Ktor dùng verifier này để tự kiểm tra token trên mỗi request cần đăng nhập. */
    val verifier: JWTVerifier = JWT.require(algorithm)
        .withAudience(audience)
        .withIssuer(issuer)
        .build()

    fun generateToken(user: User): String = JWT.create()
        .withAudience(audience)
        .withIssuer(issuer)
        .withClaim(CLAIM_USER_ID, user.id)
        .withClaim(CLAIM_ROLE, user.role.name)
        .withClaim(CLAIM_EMAIL, user.email)
        .withExpiresAt(Date(System.currentTimeMillis() + expiresInSeconds * 1000))
        .sign(algorithm)

    /**
     * `companion object` = nơi chứa thành viên "static" của lớp.
     * Gọi từ ngoài: JwtService.CLAIM_USER_ID
     */
    companion object {
        const val CLAIM_USER_ID = "userId"
        const val CLAIM_ROLE = "role"
        const val CLAIM_EMAIL = "email"
    }
}
