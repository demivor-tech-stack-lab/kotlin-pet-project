package com.vehiclerental.service

import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.domain.model.Booking
import com.vehiclerental.domain.model.BookingStatus
import com.vehiclerental.domain.model.UserRole
import com.vehiclerental.domain.model.VehicleStatus
import com.vehiclerental.dto.BookingResponse
import com.vehiclerental.dto.CreateBookingRequest
import com.vehiclerental.dto.QuoteResponse
import com.vehiclerental.dto.toResponse
import com.vehiclerental.repository.BookingRepository
import com.vehiclerental.repository.VehicleRepository
import com.vehiclerental.security.AuthUser
import java.time.LocalDateTime

/**
 * Service phức tạp nhất của dự án - đây là chỗ đáng đọc kỹ nhất.
 * Nó phối hợp 2 repository và bảo vệ các quy tắc nghiệp vụ:
 *
 *   1. Thời gian thuê phải hợp lệ (bắt đầu < kết thúc, không đặt trong quá khứ).
 *   2. Xe phải tồn tại và không đang bảo dưỡng.
 *   3. Không được trùng lịch với đơn đang hiệu lực khác.
 *   4. Chỉ chủ đơn (hoặc ADMIN) mới xem/hủy được đơn.
 *   5. Chuyển trạng thái phải đúng luồng (state machine).
 */
class BookingService(
    private val bookingRepository: BookingRepository,
    private val vehicleRepository: VehicleRepository
) {

    /** Báo giá trước khi đặt - không ghi gì vào DB. */
    suspend fun quote(vehicleId: Long, startAt: LocalDateTime, endAt: LocalDateTime): QuoteResponse {
        validatePeriod(startAt, endAt)
        val vehicle = vehicleRepository.findById(vehicleId)
            ?: throw AppException.NotFound("xe", vehicleId)

        val days = PricingPolicy.calculateDays(startAt, endAt)
        val total = PricingPolicy.calculateTotal(vehicle.pricePerDay, days)

        return QuoteResponse(
            totalDays = days,
            pricePerDay = vehicle.pricePerDay,
            totalPrice = total,
            depositAmount = PricingPolicy.calculateDeposit(total)
        )
    }

    suspend fun create(userId: Long, request: CreateBookingRequest): BookingResponse {
        validatePeriod(request.startAt, request.endAt)

        val vehicle = vehicleRepository.findById(request.vehicleId)
            ?: throw AppException.NotFound("xe", request.vehicleId)

        if (vehicle.status == VehicleStatus.MAINTENANCE) {
            throw AppException.Conflict("Xe ${vehicle.plateNumber} đang bảo dưỡng", "VEHICLE_UNAVAILABLE")
        }

        // Lưu ý: kiểm tra rồi mới ghi -> vẫn có kẽ hở nếu 2 request vào cùng lúc.
        // Với pet project thì chấp nhận được; muốn chặt chẽ phải khóa dòng ở DB
        // (SELECT ... FOR UPDATE) hoặc đặt unique constraint theo khoảng thời gian.
        if (bookingRepository.hasOverlap(request.vehicleId, request.startAt, request.endAt)) {
            throw AppException.Conflict(
                "Xe đã có người đặt trong khoảng thời gian này",
                "BOOKING_OVERLAP"
            )
        }

        val days = PricingPolicy.calculateDays(request.startAt, request.endAt)
        val total = PricingPolicy.calculateTotal(vehicle.pricePerDay, days)

        return bookingRepository.create(
            userId = userId,
            vehicleId = request.vehicleId,
            startAt = request.startAt,
            endAt = request.endAt,
            totalDays = days,
            totalPrice = total,
            depositAmount = PricingPolicy.calculateDeposit(total),
            note = request.note?.trim()
        ).toResponse()
    }

    suspend fun listMine(userId: Long, status: BookingStatus?): List<BookingResponse> =
        bookingRepository.findByUser(userId, status).map { it.toResponse() }

    suspend fun listAll(status: BookingStatus?): List<BookingResponse> =
        bookingRepository.findAll(status).map { it.toResponse() }

    suspend fun getById(id: Long, requester: AuthUser): BookingResponse {
        val booking = findOrThrow(id)
        ensureCanAccess(booking, requester)
        return booking.toResponse()
    }

    /** Khách tự hủy đơn của mình. ADMIN cũng hủy được. */
    suspend fun cancel(id: Long, requester: AuthUser): BookingResponse {
        val booking = findOrThrow(id)
        ensureCanAccess(booking, requester)

        // `when` là switch "có não" của Kotlin: dùng được với enum, khoảng giá trị, điều kiện...
        when (booking.status) {
            BookingStatus.CANCELLED ->
                throw AppException.Conflict("Đơn đã được hủy trước đó", "ALREADY_CANCELLED")
            BookingStatus.COMPLETED ->
                throw AppException.Conflict("Đơn đã hoàn tất, không thể hủy", "ALREADY_COMPLETED")
            BookingStatus.PENDING, BookingStatus.CONFIRMED -> Unit  // hợp lệ, đi tiếp
        }

        if (booking.startAt.isBefore(LocalDateTime.now())) {
            throw AppException.Conflict("Không thể hủy đơn đã tới giờ nhận xe", "TOO_LATE_TO_CANCEL")
        }

        bookingRepository.updateStatus(id, BookingStatus.CANCELLED)

        // Nếu xe đang bị giữ cho đơn này thì trả xe về trạng thái rảnh
        if (booking.status == BookingStatus.CONFIRMED) {
            vehicleRepository.updateStatus(booking.vehicleId, VehicleStatus.AVAILABLE)
        }

        return findOrThrow(id).toResponse()
    }

    /** ADMIN duyệt đơn: PENDING -> CONFIRMED, đồng thời đánh dấu xe đang được thuê. */
    suspend fun confirm(id: Long): BookingResponse {
        val booking = findOrThrow(id)
        if (booking.status != BookingStatus.PENDING) {
            throw AppException.Conflict(
                "Chỉ đơn ở trạng thái PENDING mới được duyệt (đơn này đang ${booking.status})",
                "INVALID_STATE"
            )
        }

        bookingRepository.updateStatus(id, BookingStatus.CONFIRMED)
        vehicleRepository.updateStatus(booking.vehicleId, VehicleStatus.RENTED)
        return findOrThrow(id).toResponse()
    }

    /** ADMIN tất toán đơn khi khách trả xe: CONFIRMED -> COMPLETED. */
    suspend fun complete(id: Long): BookingResponse {
        val booking = findOrThrow(id)
        if (booking.status != BookingStatus.CONFIRMED) {
            throw AppException.Conflict(
                "Chỉ đơn CONFIRMED mới tất toán được (đơn này đang ${booking.status})",
                "INVALID_STATE"
            )
        }

        bookingRepository.updateStatus(id, BookingStatus.COMPLETED)
        vehicleRepository.updateStatus(booking.vehicleId, VehicleStatus.AVAILABLE)
        return findOrThrow(id).toResponse()
    }

    // ----- các hàm phụ trợ dùng chung -----

    private suspend fun findOrThrow(id: Long): Booking =
        bookingRepository.findById(id) ?: throw AppException.NotFound("đơn đặt xe", id)

    /** Khách chỉ thao tác được trên đơn của chính mình; ADMIN thì thoải mái. */
    private fun ensureCanAccess(booking: Booking, requester: AuthUser) {
        if (requester.role != UserRole.ADMIN && booking.userId != requester.id) {
            throw AppException.Forbidden("Bạn không có quyền truy cập đơn này")
        }
    }

    private fun validatePeriod(startAt: LocalDateTime, endAt: LocalDateTime) {
        if (!startAt.isBefore(endAt)) {
            throw AppException.BadRequest("Thời gian bắt đầu phải trước thời gian kết thúc", "INVALID_PERIOD")
        }
        if (startAt.isBefore(LocalDateTime.now().minusMinutes(5))) {
            throw AppException.BadRequest("Không thể đặt xe trong quá khứ", "INVALID_PERIOD")
        }
        if (PricingPolicy.calculateDays(startAt, endAt) > MAX_RENTAL_DAYS) {
            throw AppException.BadRequest("Thời gian thuê tối đa là $MAX_RENTAL_DAYS ngày", "PERIOD_TOO_LONG")
        }
    }

    companion object {
        private const val MAX_RENTAL_DAYS = 30
    }
}
