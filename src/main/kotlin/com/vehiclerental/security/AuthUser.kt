package com.vehiclerental.security

import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.domain.model.UserRole
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*

/**
 * Thông tin người dùng đã đăng nhập, trích ra từ JWT.
 * Không truy vấn DB -> rất nhanh, đây là ưu điểm chính của JWT.
 */
data class AuthUser(
    val id: Long,
    val email: String,
    val role: UserRole
)

/**
 * Extension function trên ApplicationCall — dùng trong route handler:
 *
 *     val me = call.requireUser()
 *
 * Nếu không có token hợp lệ thì ném Unauthorized, StatusPages sẽ đổi thành 401.
 */
fun ApplicationCall.requireUser(): AuthUser {
    val principal = principal<JWTPrincipal>()
        ?: throw AppException.Unauthorized()

    val id = principal.payload.getClaim(JwtService.CLAIM_USER_ID).asLong()
        ?: throw AppException.Unauthorized("Token thiếu thông tin userId")
    val email = principal.payload.getClaim(JwtService.CLAIM_EMAIL).asString().orEmpty()
    val roleName = principal.payload.getClaim(JwtService.CLAIM_ROLE).asString()
        ?: throw AppException.Unauthorized("Token thiếu thông tin role")

    return AuthUser(id = id, email = email, role = UserRole.valueOf(roleName))
}

/** Chỉ cho phép ADMIN đi tiếp. */
fun ApplicationCall.requireAdmin(): AuthUser {
    val user = requireUser()
    if (user.role != UserRole.ADMIN) {
        throw AppException.Forbidden("Chức năng này chỉ dành cho ADMIN")
    }
    return user
}
