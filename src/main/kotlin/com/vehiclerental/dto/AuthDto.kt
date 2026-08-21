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
data class UserResponse(
    val id: Long,
    val email: String,
    val fullName: String,
    val phone: String,
    val role: UserRole,   // enum tự serialize thành "CUSTOMER"/"ADMIN"
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime
)

@Serializable
data class AuthResponse(
    val token: String,
    val expiresInSeconds: Long,
    val user: UserResponse
)

/**
 * EXTENSION FUNCTION — một trong những thứ Kotlin nhất.
 * Ta "gắn thêm" hàm toResponse() vào lớp User mà không cần sửa lớp User.
 * Bên trong, `this` chính là đối tượng User đang gọi.
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
