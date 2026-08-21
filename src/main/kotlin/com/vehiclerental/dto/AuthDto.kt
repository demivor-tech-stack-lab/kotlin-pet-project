package com.vehiclerental.dto

import com.vehiclerental.domain.model.User
import com.vehiclerental.domain.model.UserRole
import com.vehiclerental.util.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.LocalDateTime

/**
 * DTO = Data Transfer Object: hình dạng JSON đi vào / đi ra.
 * @Serializable là annotation của kotlinx.serialization; plugin biên dịch sẽ
 * SINH SẴN code đọc/ghi JSON lúc build (không dùng reflection -> nhanh & an toàn).
 */
@Serializable
data class RegisterRequest(
    val email: String,
    val password: String,
    val fullName: String,
    val phone: String
)

@Serializable
data class LoginRequest(
    val email: String,
    val password: String
)

@Serializable
data class RefreshTokenRequest(
    val refreshToken: String
)

@Serializable
data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)

@Serializable
data class UserResponse(
    val id: Long,
    val email: String,
    val fullName: String,
    val phone: String,
    val role: UserRole,   // enum tự serialize thành "CUSTOMER"/"ADMIN"
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime
)

/**
 * Trả về CẶP token thay vì một token duy nhất như bản trước:
 *
 *  - accessToken  : JWT, sống ngắn (15 phút), gửi kèm mọi request.
 *                   Không thu hồi được -> phải ngắn.
 *  - refreshToken : chuỗi ngẫu nhiên, sống 30 ngày, chỉ dùng để xin access token mới.
 *                   Lưu trong DB nên thu hồi được khi logout / nghi bị lộ.
 *
 * Client nên lưu refreshToken ở nơi an toàn nhất có thể (httpOnly cookie với web,
 * secure storage với mobile), TUYỆT ĐỐI không để trong localStorage.
 */
@Serializable
data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresInSeconds: Long,
    val user: UserResponse
)

@Serializable
data class MessageResponse(val message: String)

/**
 * EXTENSION FUNCTION — một trong những thứ Kotlin nhất.
 * Ta "gắn thêm" hàm toResponse() vào lớp User mà không cần sửa lớp User.
 *
 * Đây là chỗ passwordHash bị loại bỏ -> không bao giờ rò rỉ ra API.
 */
fun User.toResponse(): UserResponse = UserResponse(
    id = id,
    email = email,
    fullName = fullName,
    phone = phone,
    role = role,
    createdAt = createdAt
)
