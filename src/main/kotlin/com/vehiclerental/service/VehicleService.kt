package com.vehiclerental.service

import com.vehiclerental.config.TransactionRunner
import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.domain.model.VehicleStatus
import com.vehiclerental.dto.CreateVehicleRequest
import com.vehiclerental.dto.StatsResponse
import com.vehiclerental.dto.VehiclePageResponse
import com.vehiclerental.dto.VehicleResponse
import com.vehiclerental.dto.VehicleTypeResponse
import com.vehiclerental.dto.toResponse
import com.vehiclerental.repository.BookingRepository
import com.vehiclerental.repository.VehicleFilter
import com.vehiclerental.repository.VehicleRepository
import com.vehiclerental.util.TimeProvider
import org.slf4j.LoggerFactory
import java.math.BigDecimal
import kotlin.math.ceil

class VehicleService(
    private val tx: TransactionRunner,
    private val vehicleRepository: VehicleRepository,
    private val bookingRepository: BookingRepository,
    private val timeProvider: TimeProvider
) {

    private val logger = LoggerFactory.getLogger(VehicleService::class.java)

    suspend fun list(
        typeId: Long?,
        status: VehicleStatus?,
        keyword: String?,
        minPricePerDay: BigDecimal?,
        maxPricePerDay: BigDecimal?,
        page: Int,
        size: Int
    ): VehiclePageResponse {
        // coerceAtLeast / coerceIn: "kẹp" giá trị vào khoảng cho phép.
        // Chặn ở đây để client không thể gửi size=1000000 làm sập server.
        val safePage = page.coerceAtLeast(1)
        val safeSize = size.coerceIn(1, MAX_PAGE_SIZE)

        // Chặn từ khóa quá dài: LIKE '%...%' với chuỗi khổng lồ là một kiểu tấn công
        // làm nghẽn database rất rẻ tiền.
        val safeKeyword = keyword?.take(MAX_KEYWORD_LENGTH)

        return tx.tx {
            val (items, total) = vehicleRepository.findAll(
                filter = VehicleFilter(typeId, status, safeKeyword, maxPricePerDay, minPricePerDay),
                page = safePage,
                size = safeSize
            )

            VehiclePageResponse(
                items = items.map { it.toResponse() },
                page = safePage,
                size = safeSize,
                totalItems = total,
                totalPages = ceil(total.toDouble() / safeSize).toInt()
            )
        }
    }

    suspend fun getById(id: Long): VehicleResponse = tx.tx {
        vehicleRepository.findById(id)?.toResponse()
            ?: throw AppException.NotFound("xe", id)
    }

    suspend fun listTypes(): List<VehicleTypeResponse> = tx.tx {
        vehicleRepository.findAllTypes().map { it.toResponse() }
    }

    suspend fun getTypeById(id: Long): VehicleTypeResponse = tx.tx {
        vehicleRepository.findAllTypes().firstOrNull { it.id == id }?.toResponse()
            ?: throw AppException.NotFound("loại xe", id)
    }

    suspend fun create(request: CreateVehicleRequest): VehicleResponse {
        val plate = request.plateNumber.trim().uppercase()

        if (plate.isBlank()) {
            throw AppException.BadRequest("Biển số không được để trống")
        }
        // BigDecimal so sánh bằng compareTo, KHÔNG dùng == (== còn so cả scale:
        // BigDecimal("1.0") == BigDecimal("1.00") cho ra false!)
        if (request.pricePerDay <= BigDecimal.ZERO) {
            throw AppException.BadRequest("Giá thuê phải lớn hơn 0", "INVALID_PRICE")
        }
        if (request.pricePerDay > MAX_PRICE_PER_DAY) {
            throw AppException.BadRequest("Giá thuê vượt quá giới hạn cho phép", "INVALID_PRICE")
        }
        val currentYear = timeProvider.now().year
        if (request.year !in MIN_YEAR..currentYear + 1) {
            throw AppException.BadRequest("Năm sản xuất phải trong khoảng $MIN_YEAR-${currentYear + 1}")
        }

        return tx.tx {
            if (vehicleRepository.existsByPlateNumber(plate)) {
                throw AppException.Conflict("Biển số $plate đã tồn tại", "PLATE_EXISTS")
            }
            if (!vehicleRepository.typeExists(request.typeId)) {
                throw AppException.NotFound("loại xe", request.typeId)
            }

            val created = vehicleRepository.create(
                plateNumber = plate,
                brand = request.brand.trim(),
                model = request.model.trim(),
                year = request.year,
                typeId = request.typeId,
                pricePerDay = request.pricePerDay
            )
            logger.info("Them xe moi: vehicleId={}, bienSo={}", created.id, plate)
            created.toResponse()
        }
    }

    suspend fun updateStatus(id: Long, status: VehicleStatus): VehicleResponse = tx.tx {
        vehicleRepository.findById(id) ?: throw AppException.NotFound("xe", id)
        vehicleRepository.updateStatus(id, status)
        vehicleRepository.findById(id)!!.toResponse()
    }

    /**
     * Thống kê cho admin. Cả 3 truy vấn nằm trong MỘT transaction nên số liệu
     * nhất quán với nhau — không xảy ra cảnh "tổng số xe" đọc lúc 10:00:00
     * còn "số xe đang thuê" đọc lúc 10:00:01 sau khi có đơn mới chen vào.
     */
    suspend fun stats(): StatsResponse = tx.tx {
        val vehicleCounts = vehicleRepository.countByStatus()
        val bookingCounts = bookingRepository.countByStatus()
        val revenue = bookingRepository.totalRevenue()

        StatsResponse(
            totalVehicles = vehicleCounts.values.sum(),
            vehiclesByStatus = vehicleCounts.mapKeys { it.key.name },
            totalBookings = bookingCounts.values.sum(),
            bookingsByStatus = bookingCounts.mapKeys { it.key.name },
            totalRevenue = revenue,
            generatedAt = timeProvider.now()
        )
    }

    companion object {
        private const val MAX_PAGE_SIZE = 50
        private const val MAX_KEYWORD_LENGTH = 100
        private const val MIN_YEAR = 1990
        private val MAX_PRICE_PER_DAY = BigDecimal("100000000")
    }
}
