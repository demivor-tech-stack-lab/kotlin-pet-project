package com.vehiclerental.dto

import com.vehiclerental.domain.model.Vehicle
import com.vehiclerental.domain.model.VehicleStatus
import com.vehiclerental.domain.model.VehicleType
import com.vehiclerental.util.BigDecimalSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal

@Serializable
data class VehicleTypeResponse(
    val id: Long,
    val name: String,
    val seats: Int,
    val description: String?
)

@Serializable
data class CreateVehicleRequest(
    val plateNumber: String,
    val brand: String,
    val model: String,
    val year: Int,
    val typeId: Long,
    @Serializable(with = BigDecimalSerializer::class)
    val pricePerDay: BigDecimal
)

@Serializable
data class VehicleResponse(
    val id: Long,
    val plateNumber: String,
    val brand: String,
    val model: String,
    val year: Int,
    val typeId: Long,
    val typeName: String?,
    @Serializable(with = BigDecimalSerializer::class)
    val pricePerDay: BigDecimal,
    val status: VehicleStatus
)

/**
 * Kết quả phân trang. Cố tình KHÔNG dùng generic <T> để giữ mọi thứ đơn giản,
 * dễ debug khi mới học.
 */
@Serializable
data class VehiclePageResponse(
    val items: List<VehicleResponse>,
    val page: Int,
    val size: Int,
    val totalItems: Long,
    val totalPages: Int
)

fun Vehicle.toResponse(): VehicleResponse = VehicleResponse(
    id = id,
    plateNumber = plateNumber,
    brand = brand,
    model = model,
    year = year,
    typeId = typeId,
    typeName = typeName,
    pricePerDay = pricePerDay,
    status = status
)

fun VehicleType.toResponse(): VehicleTypeResponse =
    VehicleTypeResponse(id = id, name = name, seats = seats, description = description)
