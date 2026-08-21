package com.vehiclerental.dto

import com.vehiclerental.domain.model.Booking
import com.vehiclerental.domain.model.BookingStatus
import com.vehiclerental.util.BigDecimalSerializer
import com.vehiclerental.util.LocalDateTimeSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.LocalDateTime

@Serializable
data class CreateBookingRequest(
    val vehicleId: Long,
    @Serializable(with = LocalDateTimeSerializer::class)
    val startAt: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val endAt: LocalDateTime,
    val note: String? = null   // có giá trị mặc định -> client được phép bỏ qua field này
)

@Serializable
data class BookingResponse(
    val id: Long,
    val userId: Long,
    val vehicleId: Long,
    @Serializable(with = LocalDateTimeSerializer::class)
    val startAt: LocalDateTime,
    @Serializable(with = LocalDateTimeSerializer::class)
    val endAt: LocalDateTime,
    val totalDays: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val totalPrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val depositAmount: BigDecimal,
    val status: BookingStatus,
    val note: String?,
    @Serializable(with = LocalDateTimeSerializer::class)
    val createdAt: LocalDateTime
)

@Serializable
data class QuoteResponse(
    val totalDays: Int,
    @Serializable(with = BigDecimalSerializer::class)
    val pricePerDay: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val totalPrice: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class)
    val depositAmount: BigDecimal
)

fun Booking.toResponse(): BookingResponse = BookingResponse(
    id = id,
    userId = userId,
    vehicleId = vehicleId,
    startAt = startAt,
    endAt = endAt,
    totalDays = totalDays,
    totalPrice = totalPrice,
    depositAmount = depositAmount,
    status = status,
    note = note,
    createdAt = createdAt
)
