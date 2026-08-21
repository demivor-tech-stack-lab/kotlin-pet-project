package com.vehiclerental.repository

import com.vehiclerental.db.table.Bookings
import com.vehiclerental.domain.model.Booking
import com.vehiclerental.domain.model.BookingStatus
import com.vehiclerental.util.TimeProvider
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.count
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.sum
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDateTime

/** ⚠️ Mọi hàm ở đây phải được gọi bên trong transaction do service mở. */
interface BookingRepository {
    suspend fun create(
        userId: Long,
        vehicleId: Long,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        totalDays: Int,
        totalPrice: BigDecimal,
        depositAmount: BigDecimal,
        note: String?
    ): Booking

    suspend fun findById(id: Long): Booking?
    suspend fun findByUser(userId: Long, status: BookingStatus?): List<Booking>
    suspend fun findByVehicle(vehicleId: Long, onlyActive: Boolean): List<Booking>
    suspend fun findAll(status: BookingStatus?): List<Booking>
    suspend fun updateStatus(id: Long, status: BookingStatus): Boolean
    suspend fun hasOverlap(vehicleId: Long, startAt: LocalDateTime, endAt: LocalDateTime): Boolean
    suspend fun countByStatus(): Map<BookingStatus, Long>
    suspend fun totalRevenue(): BigDecimal
}

class BookingRepositoryImpl(
    private val timeProvider: TimeProvider
) : BookingRepository {

    override suspend fun create(
        userId: Long,
        vehicleId: Long,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        totalDays: Int,
        totalPrice: BigDecimal,
        depositAmount: BigDecimal,
        note: String?
    ): Booking {
        val now = timeProvider.now()
        val statement = Bookings.insert {
            it[Bookings.userId] = userId
            it[Bookings.vehicleId] = vehicleId
            it[Bookings.startAt] = startAt
            it[Bookings.endAt] = endAt
            it[Bookings.totalDays] = totalDays
            it[Bookings.totalPrice] = totalPrice
            it[Bookings.depositAmount] = depositAmount
            it[status] = BookingStatus.PENDING
            it[Bookings.note] = note
            it[createdAt] = now
        }

        return Booking(
            id = statement[Bookings.id],
            userId = userId,
            vehicleId = vehicleId,
            startAt = startAt,
            endAt = endAt,
            totalDays = totalDays,
            totalPrice = totalPrice,
            depositAmount = depositAmount,
            status = BookingStatus.PENDING,
            note = note,
            createdAt = now
        )
    }

    override suspend fun findById(id: Long): Booking? =
        Bookings.selectAll().where { Bookings.id eq id }.singleOrNull()?.toBooking()

    override suspend fun findByUser(userId: Long, status: BookingStatus?): List<Booking> {
        val query = Bookings.selectAll().where { Bookings.userId eq userId }
        status?.let { value -> query.andWhere { Bookings.status eq value } }
        return query.orderBy(Bookings.id to SortOrder.DESC).map { it.toBooking() }
    }

    override suspend fun findByVehicle(vehicleId: Long, onlyActive: Boolean): List<Booking> {
        val query = Bookings.selectAll().where { Bookings.vehicleId eq vehicleId }
        if (onlyActive) {
            query.andWhere { Bookings.status inList ACTIVE_STATUSES }
        }
        return query.orderBy(Bookings.startAt to SortOrder.ASC).map { it.toBooking() }
    }

    override suspend fun findAll(status: BookingStatus?): List<Booking> {
        val query = Bookings.selectAll()
        status?.let { value -> query.andWhere { Bookings.status eq value } }
        return query.orderBy(Bookings.id to SortOrder.DESC).map { it.toBooking() }
    }

    override suspend fun updateStatus(id: Long, status: BookingStatus): Boolean =
        Bookings.update({ Bookings.id eq id }) {
            it[Bookings.status] = status
        } > 0

    /**
     * KIỂM TRA TRÙNG LỊCH.
     *
     * Hai khoảng thời gian [aStart, aEnd) và [bStart, bEnd) GIAO NHAU khi và chỉ khi:
     *      aStart < bEnd  VÀ  aEnd > bStart
     *
     * Chỉ tính các đơn còn hiệu lực (PENDING/CONFIRMED); đơn đã HỦY hay đã XONG
     * thì không còn chiếm chỗ nữa.
     *
     * ⚠️ Bản thân hàm này KHÔNG chống được race condition — nó chỉ đọc.
     * Việc chống đua nằm ở chỗ service khóa dòng xe bằng `findByIdForUpdate()`
     * TRƯỚC khi gọi hàm này, trong cùng một transaction.
     */
    override suspend fun hasOverlap(
        vehicleId: Long,
        startAt: LocalDateTime,
        endAt: LocalDateTime
    ): Boolean =
        Bookings.selectAll()
            .where {
                (Bookings.vehicleId eq vehicleId) and
                    (Bookings.status inList ACTIVE_STATUSES) and
                    (Bookings.startAt less endAt) and
                    (Bookings.endAt greater startAt)
            }
            .empty()
            .not()

    override suspend fun countByStatus(): Map<BookingStatus, Long> {
        val counter = Bookings.id.count()
        return Bookings
            .select(Bookings.status, counter)
            .groupBy(Bookings.status)
            .associate { row -> row[Bookings.status] to row[counter] }
    }

    /** SUM() trả về NULL khi không có dòng nào khớp -> phải quy về 0. */
    override suspend fun totalRevenue(): BigDecimal {
        val total = Bookings.totalPrice.sum()
        return Bookings
            .select(total)
            .where { Bookings.status eq BookingStatus.COMPLETED }
            .firstOrNull()
            ?.get(total)
            ?: BigDecimal.ZERO
    }

    companion object {
        private val ACTIVE_STATUSES = listOf(BookingStatus.PENDING, BookingStatus.CONFIRMED)
    }
}
