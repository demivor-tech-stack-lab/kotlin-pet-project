package com.vehiclerental.domain.model

import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * ĐÂY LÀ DOMAIN MODEL (model nghiệp vụ) — khác với DTO ở package `dto`.
 *
 * - Domain model: hình dạng dữ liệu bên TRONG ứng dụng (có cả passwordHash).
 * - DTO: hình dạng dữ liệu đi RA/VÀO qua HTTP (không bao giờ lộ passwordHash).
 *
 * `data class` tự sinh: equals(), hashCode(), toString(), copy(), componentN().
 * `val` = bất biến (immutable) -> an toàn khi dùng chung giữa nhiều coroutine.
 */
data class User(
    val id: Long,
    val email: String,
    val passwordHash: String,   // KHÔNG BAO GIỜ trả ra API
    val fullName: String,
    val phone: String,
    val role: UserRole,
    val createdAt: LocalDateTime
)

data class VehicleType(
    val id: Long,
    val name: String,           // "Xe máy", "Ô tô 4 chỗ"...
    val seats: Int,
    val description: String?    // dấu `?` = nullable, có thể null
)

data class Vehicle(
    val id: Long,
    val plateNumber: String,    // biển số, là duy nhất
    val brand: String,
    val model: String,
    val year: Int,
    val typeId: Long,
    val typeName: String?,      // lấy từ JOIN, nên có thể null
    val pricePerDay: BigDecimal,// dùng BigDecimal cho tiền, KHÔNG dùng Double
    val status: VehicleStatus
)

data class Booking(
    val id: Long,
    val userId: Long,
    val vehicleId: Long,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val totalDays: Int,
    val totalPrice: BigDecimal,
    val depositAmount: BigDecimal,
    val status: BookingStatus,
    val note: String?,
    val createdAt: LocalDateTime
)

/**
 * Refresh token da luu trong DB.
 *
 * `tokenHash` la SHA-256 cua token goc. Server KHONG bao gio luu token goc,
 * nen ke doc trom database cung khong the dung lai duoc - giong het cach ta
 * xu ly mat khau.
 */
data class RefreshToken(
    val id: Long,
    val userId: Long,
    val tokenHash: String,
    val expiresAt: LocalDateTime,
    val revokedAt: LocalDateTime?,
    val createdAt: LocalDateTime,
    val userAgent: String?,
    val ipAddress: String?
) {
    /** Token chi dung duoc khi chua bi thu hoi VA chua qua han. */
    fun isUsable(now: LocalDateTime): Boolean =
        revokedAt == null && expiresAt.isAfter(now)
}
