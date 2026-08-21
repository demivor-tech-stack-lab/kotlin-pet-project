package com.vehiclerental.repository

import com.vehiclerental.db.table.Bookings
import com.vehiclerental.db.table.RefreshTokens
import com.vehiclerental.db.table.Users
import com.vehiclerental.db.table.VehicleTypes
import com.vehiclerental.db.table.Vehicles
import com.vehiclerental.domain.model.Booking
import com.vehiclerental.domain.model.RefreshToken
import com.vehiclerental.domain.model.User
import com.vehiclerental.domain.model.Vehicle
import com.vehiclerental.domain.model.VehicleType
import org.jetbrains.exposed.sql.ResultRow

/**
 * Chuyển một dòng dữ liệu thô (ResultRow) thành domain model.
 *
 * Gom hết ở đây để nếu đổi tên cột thì chỉ sửa MỘT chỗ.
 * `internal` = chỉ nhìn thấy được trong module này.
 */
internal fun ResultRow.toUser() = User(
    id = this[Users.id],
    email = this[Users.email],
    passwordHash = this[Users.passwordHash],
    fullName = this[Users.fullName],
    phone = this[Users.phone],
    role = this[Users.role],
    createdAt = this[Users.createdAt]
)

internal fun ResultRow.toVehicleType() = VehicleType(
    id = this[VehicleTypes.id],
    name = this[VehicleTypes.name],
    seats = this[VehicleTypes.seats],
    description = this[VehicleTypes.description]
)

/**
 * getOrNull() thay vì [] : khi câu query KHÔNG join sang bảng vehicle_types
 * thì cột đó không tồn tại trong ResultRow -> [] sẽ ném exception, getOrNull trả null.
 */
internal fun ResultRow.toVehicle() = Vehicle(
    id = this[Vehicles.id],
    plateNumber = this[Vehicles.plateNumber],
    brand = this[Vehicles.brand],
    model = this[Vehicles.model],
    year = this[Vehicles.year],
    typeId = this[Vehicles.typeId],
    typeName = this.getOrNull(VehicleTypes.name),
    pricePerDay = this[Vehicles.pricePerDay],
    status = this[Vehicles.status]
)

internal fun ResultRow.toBooking() = Booking(
    id = this[Bookings.id],
    userId = this[Bookings.userId],
    vehicleId = this[Bookings.vehicleId],
    startAt = this[Bookings.startAt],
    endAt = this[Bookings.endAt],
    totalDays = this[Bookings.totalDays],
    totalPrice = this[Bookings.totalPrice],
    depositAmount = this[Bookings.depositAmount],
    status = this[Bookings.status],
    note = this[Bookings.note],
    createdAt = this[Bookings.createdAt]
)

internal fun ResultRow.toRefreshToken() = RefreshToken(
    id = this[RefreshTokens.id],
    userId = this[RefreshTokens.userId],
    tokenHash = this[RefreshTokens.tokenHash],
    expiresAt = this[RefreshTokens.expiresAt],
    revokedAt = this[RefreshTokens.revokedAt],
    createdAt = this[RefreshTokens.createdAt],
    userAgent = this[RefreshTokens.userAgent],
    ipAddress = this[RefreshTokens.ipAddress]
)
