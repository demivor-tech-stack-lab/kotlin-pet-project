package com.vehiclerental.service

import com.vehiclerental.config.TransactionRunner
import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.domain.model.Booking
import com.vehiclerental.domain.model.BookingStatus
import com.vehiclerental.domain.model.UserRole
import com.vehiclerental.domain.model.VehicleStatus
import com.vehiclerental.dto.BookingResponse
import com.vehiclerental.dto.BusyPeriodResponse
import com.vehiclerental.dto.CreateBookingRequest
import com.vehiclerental.dto.QuoteResponse
import com.vehiclerental.dto.toResponse
import com.vehiclerental.repository.BookingRepository
import com.vehiclerental.repository.VehicleRepository
import com.vehiclerental.security.AuthUser
import com.vehiclerental.util.TimeProvider
import org.slf4j.LoggerFactory
import java.time.LocalDateTime

/**
 * Service phức tạp nhất của dự án — đáng đọc kỹ nhất.
 * Nó phối hợp 2 repository và bảo vệ các quy tắc nghiệp vụ:
 *
 *   1. Thời gian thuê phải hợp lệ (bắt đầu < kết thúc, không đặt trong quá khứ).
 *   2. Xe phải tồn tại và không đang bảo dưỡng.
 *   3. Không được trùng lịch với đơn đang hiệu lực khác — kể cả khi hai người
 *      bấm đặt CÙNG MỘT LÚC (xem [create]).
 *   4. Chỉ chủ đơn (hoặc ADMIN) mới xem/hủy được đơn.
 *   5. Chuyển trạng thái phải đúng luồng (state machine), và mọi thay đổi liên
 *      quan tới nhiều bảng phải NGUYÊN TỬ.
 */
class BookingService(
    private val tx: TransactionRunner,
    private val bookingRepository: BookingRepository,
    private val vehicleRepository: VehicleRepository,
    private val timeProvider: TimeProvider
) {

    private val logger = LoggerFactory.getLogger(BookingService::class.java)

    /** Báo giá trước khi đặt — không ghi gì vào DB. */
    suspend fun quote(vehicleId: Long, startAt: LocalDateTime, endAt: LocalDateTime): QuoteResponse {
        validatePeriod(startAt, endAt)

        return tx.tx {
            val vehicle = vehicleRepository.findById(vehicleId)
                ?: throw AppException.NotFound("xe", vehicleId)

            val days = PricingPolicy.calculateDays(startAt, endAt)
            val total = PricingPolicy.calculateTotal(vehicle.pricePerDay, days)

            QuoteResponse(
                totalDays = days,
                pricePerDay = vehicle.pricePerDay,
                totalPrice = total,
                depositAmount = PricingPolicy.calculateDeposit(total)
            )
        }
    }

    /**
     * TẠO ĐƠN ĐẶT XE — chỗ sửa lỗi race condition.
     *
     * Bản pet project có lỗ hổng kinh điển TOCTOU (time-of-check to time-of-use):
     *   A kiểm tra trùng lịch -> chưa có -> A chuẩn bị ghi
     *   B kiểm tra trùng lịch -> A CHƯA GHI XONG nên B cũng thấy trống -> B ghi
     *   A ghi  => hai đơn trùng lịch trên cùng một chiếc xe.
     *
     * Cách sửa ở đây gồm 2 phần, cả hai đều bắt buộc:
     *   1. `tx.tx { }` bọc TOÀN BỘ kiểm tra + ghi trong MỘT transaction.
     *   2. `findByIdForUpdate()` sinh câu `SELECT ... FOR UPDATE`, KHÓA dòng xe lại.
     *      B muốn khóa cùng dòng đó sẽ phải xếp hàng chờ A commit xong, lúc đó
     *      B mới chạy hasOverlap() và sẽ thấy đơn của A => bị từ chối đúng như mong muốn.
     *
     * Đánh đổi: các request đặt CÙNG một chiếc xe bị tuần tự hóa. Chấp nhận được,
     * vì đặt khác xe vẫn chạy song song bình thường (khóa theo dòng, không phải theo bảng).
     */
    suspend fun create(userId: Long, request: CreateBookingRequest): BookingResponse {
        validatePeriod(request.startAt, request.endAt)

        return tx.tx {
            val vehicle = vehicleRepository.findByIdForUpdate(request.vehicleId)
                ?: throw AppException.NotFound("xe", request.vehicleId)

            if (vehicle.status == VehicleStatus.MAINTENANCE) {
                throw AppException.Conflict("Xe ${vehicle.plateNumber} đang bảo dưỡng", "VEHICLE_UNAVAILABLE")
            }

            if (bookingRepository.hasOverlap(request.vehicleId, request.startAt, request.endAt)) {
                throw AppException.Conflict(
                    "Xe đã có người đặt trong khoảng thời gian này",
                    "BOOKING_OVERLAP"
                )
            }

            val days = PricingPolicy.calculateDays(request.startAt, request.endAt)
            val total = PricingPolicy.calculateTotal(vehicle.pricePerDay, days)

            val booking = bookingRepository.create(
                userId = userId,
                vehicleId = request.vehicleId,
                startAt = request.startAt,
                endAt = request.endAt,
                totalDays = days,
                totalPrice = total,
                depositAmount = PricingPolicy.calculateDeposit(total),
                note = request.note?.trim()
            )

            logger.info("Tao don thanh cong: bookingId={}, userId={}, vehicleId={}", booking.id, userId, request.vehicleId)
            booking.toResponse()
        }
    }

    suspend fun listMine(userId: Long, status: BookingStatus?): List<BookingResponse> = tx.tx {
        bookingRepository.findByUser(userId, status).map { it.toResponse() }
    }

    suspend fun listAll(status: BookingStatus?): List<BookingResponse> = tx.tx {
        bookingRepository.findAll(status).map { it.toResponse() }
    }

    /**
     * Lịch bận của một chiếc xe — endpoint công khai để giao diện đặt xe
     * chặn ngày trước khi người dùng bấm đặt.
     *
     * Chỉ trả về khoảng thời gian, KHÔNG trả về ai đặt: đây là dữ liệu công khai,
     * lộ userId ra là rò rỉ thông tin cá nhân.
     */
    suspend fun busyPeriods(vehicleId: Long): List<BusyPeriodResponse> = tx.tx {
        vehicleRepository.findById(vehicleId) ?: throw AppException.NotFound("xe", vehicleId)

        bookingRepository.findByVehicle(vehicleId, onlyActive = true)
            .map { BusyPeriodResponse(startAt = it.startAt, endAt = it.endAt, status = it.status) }
    }

    suspend fun getById(id: Long, requester: AuthUser): BookingResponse = tx.tx {
        val booking = findOrThrow(id)
        ensureCanAccess(booking, requester)
        booking.toResponse()
    }

    /**
     * Khách tự hủy đơn của mình. ADMIN cũng hủy được.
     *
     * Cả việc đổi trạng thái đơn lẫn trả xe về AVAILABLE nằm trong MỘT transaction.
     */
    suspend fun cancel(id: Long, requester: AuthUser): BookingResponse = tx.tx {
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

        val now = timeProvider.now()
        val isAdmin = requester.role == UserRole.ADMIN

        if (!isAdmin) {
            if (booking.startAt.isBefore(now)) {
                throw AppException.Conflict("Không thể hủy đơn đã tới giờ nhận xe", "TOO_LATE_TO_CANCEL")
            }
            // Hủy sát giờ khiến chiếc xe gần như chắc chắn ế cả ngày hôm đó.
            if (booking.startAt.isBefore(now.plusHours(MIN_HOURS_BEFORE_CANCEL))) {
                throw AppException.Conflict(
                    "Chỉ được hủy trước giờ nhận xe ít nhất $MIN_HOURS_BEFORE_CANCEL giờ. " +
                        "Vui lòng liên hệ tổng đài để được hỗ trợ.",
                    "TOO_LATE_TO_CANCEL"
                )
            }
        }

        bookingRepository.updateStatus(id, BookingStatus.CANCELLED)

        // Nếu xe đang bị giữ cho đơn này thì trả xe về trạng thái rảnh
        if (booking.status == BookingStatus.CONFIRMED) {
            vehicleRepository.updateStatus(booking.vehicleId, VehicleStatus.AVAILABLE)
        }

        logger.info("Huy don: bookingId={}, boi userId={}", id, requester.id)
        findOrThrow(id).toResponse()
    }

    /** ADMIN duyệt đơn: PENDING -> CONFIRMED, đồng thời đánh dấu xe đang được thuê. */
    suspend fun confirm(id: Long): BookingResponse = tx.tx {
        val booking = findOrThrow(id)
        if (booking.status != BookingStatus.PENDING) {
            throw AppException.Conflict(
                "Chỉ đơn ở trạng thái PENDING mới được duyệt (đơn này đang ${booking.status})",
                "INVALID_STATE"
            )
        }

        // HAI thao tác ghi trên HAI bảng, trong CÙNG một transaction.
        // Đây chính là lỗi 🔴 của bản pet project: trước kia mỗi lệnh là một
        // transaction riêng, hỏng giữa chừng là đơn CONFIRMED nhưng xe vẫn AVAILABLE.
        bookingRepository.updateStatus(id, BookingStatus.CONFIRMED)
        vehicleRepository.updateStatus(booking.vehicleId, VehicleStatus.RENTED)

        logger.info("Duyet don: bookingId={}, vehicleId={}", id, booking.vehicleId)
        findOrThrow(id).toResponse()
    }

    /** ADMIN tất toán đơn khi khách trả xe: CONFIRMED -> COMPLETED. */
    suspend fun complete(id: Long): BookingResponse = tx.tx {
        val booking = findOrThrow(id)
        if (booking.status != BookingStatus.CONFIRMED) {
            throw AppException.Conflict(
                "Chỉ đơn CONFIRMED mới tất toán được (đơn này đang ${booking.status})",
                "INVALID_STATE"
            )
        }

        bookingRepository.updateStatus(id, BookingStatus.COMPLETED)
        vehicleRepository.updateStatus(booking.vehicleId, VehicleStatus.AVAILABLE)

        logger.info("Tat toan don: bookingId={}, vehicleId={}", id, booking.vehicleId)
        findOrThrow(id).toResponse()
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
        // timeProvider thay cho LocalDateTime.now(): kết quả không còn phụ thuộc
        // múi giờ của máy chủ, và test có thể "đóng băng" thời gian.
        if (startAt.isBefore(timeProvider.now().minusMinutes(5))) {
            throw AppException.BadRequest("Không thể đặt xe trong quá khứ", "INVALID_PERIOD")
        }
        if (PricingPolicy.calculateDays(startAt, endAt) > MAX_RENTAL_DAYS) {
            throw AppException.BadRequest("Thời gian thuê tối đa là $MAX_RENTAL_DAYS ngày", "PERIOD_TOO_LONG")
        }
    }

    companion object {
        private const val MAX_RENTAL_DAYS = 30
        private const val MIN_HOURS_BEFORE_CANCEL = 24L
    }
}
