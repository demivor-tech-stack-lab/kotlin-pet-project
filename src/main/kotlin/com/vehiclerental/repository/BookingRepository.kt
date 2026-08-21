package com.vehiclerental.repository

import com.vehiclerental.config.DatabaseFactory.dbQuery
import com.vehiclerental.db.table.Bookings
import com.vehiclerental.domain.model.Booking
import com.vehiclerental.domain.model.BookingStatus
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal
import java.time.LocalDateTime

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
    suspend fun findAll(status: BookingStatus?): List<Booking>
    suspend fun updateStatus(id: Long, status: BookingStatus): Boolean
    suspend fun hasOverlap(vehicleId: Long, startAt: LocalDateTime, endAt: LocalDateTime): Boolean
}

class BookingRepositoryImpl : BookingRepository {

    override suspend fun create(
        userId: Long,
        vehicleId: Long,
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        totalDays: Int,
        totalPrice: BigDecimal,
        depositAmount: BigDecimal,
        note: String?
    ): Booking = dbQuery {
        val now = LocalDateTime.now()
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

        Booking(
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

    override suspend fun findById(id: Long): Booking? = dbQuery {
        Bookings.selectAll().where { Bookings.id eq id }.singleOrNull()?.toBooking()
    }

    override suspend fun findByUser(userId: Long, status: BookingStatus?): List<Booking> = dbQuery {
        val query = Bookings.selectAll().where { Bookings.userId eq userId }
        status?.let { value -> query.andWhere { Bookings.status eq value } }
        query.orderBy(Bookings.id to SortOrder.DESC).map { it.toBooking() }
    }

    override suspend fun findAll(status: BookingStatus?): List<Booking> = dbQuery {
        val query = Bookings.selectAll()
        status?.let { value -> query.andWhere { Bookings.status eq value } }
        query.orderBy(Bookings.id to SortOrder.DESC).map { it.toBooking() }
    }

    override suspend fun updateStatus(id: Long, status: BookingStatus): Boolean = dbQuery {
        Bookings.update({ Bookings.id eq id }) {
            it[Bookings.status] = status
        } > 0
    }

    /**
     * KIỂM TRA TRÙNG LỊCH - phần "khó" nhất của repository này.
     *
     * Hai khoảng thời gian [aStart, aEnd) và [bStart, bEnd) GIAO NHAU khi và chỉ khi:
     *      aStart < bEnd  VÀ  aEnd > bStart
     *
     * Chỉ tính các đơn còn hiệu lực (PENDING/CONFIRMED); đơn đã HỦY hay đã XONG
     * thì không còn chiếm chỗ nữa.
     */
    override suspend fun hasOverlap(
        vehicleId: Long,
        startAt: LocalDateTime,
        endAt: LocalDateTime
    ): Boolean = dbQuery {
        val activeStatuses = listOf(BookingStatus.PENDING, BookingStatus.CONFIRMED)

        Bookings.selectAll()
            .where {
                (Bookings.vehicleId eq vehicleId) and
                    (Bookings.status inList activeStatuses) and
                    (Bookings.startAt less endAt) and
                    (Bookings.endAt greater startAt)
            }
            .empty()
            .not()
    }
}
