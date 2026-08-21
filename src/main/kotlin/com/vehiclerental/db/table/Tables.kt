package com.vehiclerental.db.table

import com.vehiclerental.domain.model.BookingStatus
import com.vehiclerental.domain.model.UserRole
import com.vehiclerental.domain.model.VehicleStatus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.javatime.datetime

/**
 * ĐỊNH NGHĨA SCHEMA THEO KIỂU "TABLE DSL" CỦA EXPOSED.
 *
 * Mỗi bảng là một `object` (singleton — chỉ tồn tại đúng 1 thể hiện trong JVM),
 * mỗi cột là một `val` kiểu Column<T>. Nhờ vậy câu query được kiểm tra kiểu
 * ngay lúc BIÊN DỊCH: viết `Users.email eq 123` sẽ đỏ ngay, không đợi chạy mới lỗi.
 */
object Users : Table("users") {
    val id = long("id").autoIncrement()
    val email = varchar("email", 255).uniqueIndex()      // uniqueIndex -> DB tự chặn email trùng
    val passwordHash = varchar("password_hash", 100)
    val fullName = varchar("full_name", 120)
    val phone = varchar("phone", 20)
    // enumerationByName: lưu tên enum dạng chuỗi, dễ đọc khi mở DB xem trực tiếp
    val role = enumerationByName("role", 20, UserRole::class)
    val createdAt = datetime("created_at")

    // Bắt buộc: khai báo khóa chính. `override` vì đang ghi đè thuộc tính của Table.
    override val primaryKey = PrimaryKey(id)
}

object VehicleTypes : Table("vehicle_types") {
    val id = long("id").autoIncrement()
    val name = varchar("name", 100).uniqueIndex()
    val seats = integer("seats")
    val description = varchar("description", 255).nullable()  // nullable() <-> String? bên Kotlin

    override val primaryKey = PrimaryKey(id)
}

object Vehicles : Table("vehicles") {
    val id = long("id").autoIncrement()
    val plateNumber = varchar("plate_number", 20).uniqueIndex()
    val brand = varchar("brand", 60)
    val model = varchar("model", 60)
    val year = integer("manufacture_year")   // ten cot khac ten thuoc tinh: "year" la tu khoa SQL
    // references() tạo FOREIGN KEY: không thể thêm xe với type_id không tồn tại
    val typeId = long("type_id").references(VehicleTypes.id)
    // TIỀN LUÔN DÙNG decimal, KHÔNG dùng double (double làm tròn sai: 0.1 + 0.2 != 0.3)
    val pricePerDay = decimal("price_per_day", precision = 12, scale = 2)
    val status = enumerationByName("status", 20, VehicleStatus::class)

    override val primaryKey = PrimaryKey(id)
}

object Bookings : Table("bookings") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val vehicleId = long("vehicle_id").references(Vehicles.id)
    val startAt = datetime("start_at")
    val endAt = datetime("end_at")
    val totalDays = integer("total_days")
    val totalPrice = decimal("total_price", 12, 2)
    val depositAmount = decimal("deposit_amount", 12, 2)
    val status = enumerationByName("status", 20, BookingStatus::class)
    val note = varchar("note", 500).nullable()
    val createdAt = datetime("created_at")

    override val primaryKey = PrimaryKey(id)

    // Index (không unique) giúp truy vấn "tìm đơn của xe X" nhanh hơn khi dữ liệu lớn
    init {
        index("idx_bookings_vehicle_status", isUnique = false, vehicleId, status, startAt, endAt)
        index("idx_bookings_user_id", isUnique = false, userId)
    }
}

/**
 * Refresh token lưu trong DB để có thể THU HỒI.
 *
 * Access token (JWT) không thu hồi được — server không giữ trạng thái nào cả,
 * token còn hạn là còn dùng được. Đó là lý do access token chỉ sống 15 phút.
 * Refresh token thì ngược lại: sống 30 ngày nhưng nằm trong bảng này,
 * nên logout / đổi mật khẩu / phát hiện bị đánh cắp là vô hiệu hóa được ngay.
 *
 * `tokenHash` lưu SHA-256 chứ không lưu token gốc — cùng nguyên tắc với mật khẩu.
 */
object RefreshTokens : Table("refresh_tokens") {
    val id = long("id").autoIncrement()
    val userId = long("user_id").references(Users.id)
    val tokenHash = varchar("token_hash", 64).uniqueIndex()
    val expiresAt = datetime("expires_at")
    val revokedAt = datetime("revoked_at").nullable()
    val createdAt = datetime("created_at")
    val userAgent = varchar("user_agent", 255).nullable()
    val ipAddress = varchar("ip_address", 64).nullable()

    override val primaryKey = PrimaryKey(id)
}
