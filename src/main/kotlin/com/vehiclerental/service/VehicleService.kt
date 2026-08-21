package com.vehiclerental.service

import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.domain.model.VehicleStatus
import com.vehiclerental.dto.CreateVehicleRequest
import com.vehiclerental.dto.VehiclePageResponse
import com.vehiclerental.dto.VehicleResponse
import com.vehiclerental.dto.VehicleTypeResponse
import com.vehiclerental.dto.toResponse
import com.vehiclerental.repository.VehicleFilter
import com.vehiclerental.repository.VehicleRepository
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.math.ceil

class VehicleService(
    private val vehicleRepository: VehicleRepository
) {

    suspend fun list(
        typeId: Long?,
        status: VehicleStatus?,
        keyword: String?,
        maxPricePerDay: BigDecimal?,
        page: Int,
        size: Int
    ): VehiclePageResponse {
        // coerceAtLeast / coerceIn: "kẹp" giá trị vào khoảng cho phép.
        // Chặn ở đây để client không thể gửi size=1000000 làm sập server.
        val safePage = page.coerceAtLeast(1)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)

        val (items, total) = vehicleRepository.findAll(
            filter = VehicleFilter(typeId, status, keyword, maxPricePerDay),
            page = safePage,
            size = safeSize
        )

        return VehiclePageResponse(
            items = items.map { it.toResponse() },
            page = safePage,
            size = safeSize,
            totalItems = total,
            totalPages = ceil(total.toDouble() / safeSize).toInt()
        )
    }

    suspend fun getById(id: Long): VehicleResponse {
        val vehicle = vehicleRepository.findById(id)
            ?: throw AppException.NotFound("xe", id)
        return vehicle.toResponse()
    }

    suspend fun listTypes(): List<VehicleTypeResponse> =
        vehicleRepository.findAllTypes().map { it.toResponse() }

    suspend fun create(request: CreateVehicleRequest): VehicleResponse {
        val plate = request.plateNumber.trim().uppercase()

        if (plate.isBlank()) {
            throw AppException.BadRequest("Biển số không được để trống")
        }
        if (vehicleRepository.existsByPlateNumber(plate)) {
            throw AppException.Conflict("Biển số $plate đã tồn tại", "PLATE_EXISTS")
        }
        if (!vehicleRepository.typeExists(request.typeId)) {
            throw AppException.NotFound("loại xe", request.typeId)
        }
        // BigDecimal so sánh bằng compareTo, KHÔNG dùng == (== còn so cả scale:
        // BigDecimal("1.0") == BigDecimal("1.00") cho ra false!)
        if (request.pricePerDay <= BigDecimal.ZERO) {
            throw AppException.BadRequest("Giá thuê phải lớn hơn 0", "INVALID_PRICE")
        }
        val currentYear = LocalDate.now().year
        if (request.year !in MIN_YEAR..currentYear + 1) {
            throw AppException.BadRequest("Năm sản xuất phải trong khoảng $MIN_YEAR-${currentYear + 1}")
        }

        return vehicleRepository.create(
            plateNumber = plate,
            brand = request.brand.trim(),
            model = request.model.trim(),
            year = request.year,
            typeId = request.typeId,
            pricePerDay = request.pricePerDay
        ).toResponse()
    }

    suspend fun updateStatus(id: Long, status: VehicleStatus): VehicleResponse {
        vehicleRepository.findById(id) ?: throw AppException.NotFound("xe", id)
        vehicleRepository.updateStatus(id, status)
        return getById(id)
    }

    companion object {
        private const val MAX_PAGE_SIZE = 50
        private const val MIN_YEAR = 1990
    }
}
