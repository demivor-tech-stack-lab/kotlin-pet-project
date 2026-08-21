package com.vehiclerental.dto

import com.vehiclerental.util.BigDecimalSerializer
import com.vehiclerental.util.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

/**
 * Số liệu tổng quan cho trang quản trị.
 *
 * `Map<String, Long>` thay vì `Map<VehicleStatus, Long>`: kotlinx.serialization
 * serialize key của Map thành chuỗi, dùng String ngay từ đầu cho tường minh.
 */
@Serializable
data class StatsResponse(
    val totalVehicles: Long,
    val vehiclesByStatus: Map<String, Long>,
    val totalBookings: Long,
    val bookingsByStatus: Map<String, Long>,
    @Serializable(with = BigDecimalSerializer::class)
    val totalRevenue: BigDecimal,
    @Serializable(with = LocalDateTimeSerializer::class)
    val generatedAt: LocalDateTime
)

/**
 * Kết quả kiểm tra sức khỏe, dùng cho /health/live và /health/ready.
 *
 * Chỉ trả thông tin tối thiểu: probe của Docker/K8s chỉ quan tâm HTTP status,
 * còn body thì để người vận hành đọc khi debug. Không lộ phiên bản thư viện,
 * không lộ chuỗi kết nối DB.
 */
@Serializable
data class HealthResponse(
    val status: String,
    val checks: Map<String, String> = emptyMap()
)
