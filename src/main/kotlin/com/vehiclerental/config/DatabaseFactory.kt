package com.vehiclerental.config

import com.vehiclerental.db.table.Bookings
import com.vehiclerental.db.table.Users
import com.vehiclerental.db.table.VehicleTypes
import com.vehiclerental.db.table.Vehicles
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.server.config.*
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction

/**
 * Khởi tạo kết nối DB + tạo bảng.
 *
 * `object` = singleton: cả app chỉ có một DatabaseFactory duy nhất.
 */
object DatabaseFactory {

    fun init(config: ApplicationConfig) {
        val dbConfig = config.config("database")

        // HikariCP = connection pool. Mở/đóng kết nối DB rất tốn kém,
        // nên ta giữ sẵn N kết nối và tái sử dụng.
        val hikariConfig = HikariConfig().apply {
            driverClassName = dbConfig.property("driver").getString()
            jdbcUrl = dbConfig.property("url").getString()
            username = dbConfig.property("user").getString()
            password = dbConfig.property("password").getString()
            maximumPoolSize = dbConfig.property("poolSize").getString().toInt()
            isAutoCommit = false          // Exposed tự quản lý commit trong transaction {}
            transactionIsolation = "TRANSACTION_REPEATABLE_READ"
            validate()
        }

        Database.connect(HikariDataSource(hikariConfig))

        // Tạo bảng nếu chưa có. Ở dự án thật bạn sẽ dùng Flyway/Liquibase để
        // quản lý migration, nhưng với pet project thì cách này đủ và gọn.
        transaction {
            SchemaUtils.create(Users, VehicleTypes, Vehicles, Bookings)
        }
    }

    /**
     * Hàm helper quan trọng nhất file này.
     *
     * `suspend fun` = hàm có thể tạm dừng (suspend) mà không chặn thread.
     * `<T>` = generic: gọi hàm này trả về đúng kiểu mà block bên trong trả về.
     * `block: suspend () -> T` = tham số là một hàm (higher-order function).
     *
     * newSuspendedTransaction(Dispatchers.IO): chạy truy vấn JDBC (vốn là blocking)
     * trên pool thread dành riêng cho I/O, tránh làm nghẽn thread xử lý HTTP.
     */
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
}
