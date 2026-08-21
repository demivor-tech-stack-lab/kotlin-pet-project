package com.vehiclerental.config

import com.vehiclerental.db.table.Bookings
import com.vehiclerental.db.table.RefreshTokens
import com.vehiclerental.db.table.Users
import com.vehiclerental.db.table.VehicleTypes
import com.vehiclerental.db.table.Vehicles
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.Dispatchers
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import org.slf4j.LoggerFactory
import javax.sql.DataSource

/**
 * QUẢN LÝ VÒNG ĐỜI KẾT NỐI DATABASE.
 *
 * Đổi từ `object` sang `class` so với bản pet project, vì production cần:
 *   - **Đóng được connection pool** khi app tắt (graceful shutdown).
 *   - **Kiểm tra sức khỏe** để endpoint /health/ready trả lời trung thực.
 *   - Tạo được nhiều thể hiện độc lập trong test.
 *
 * Hàm helper [dbQuery] vẫn nằm trong companion object nên mọi nơi gọi
 * `DatabaseFactory.dbQuery { }` như cũ.
 */
class DatabaseFactory(private val config: AppConfig.DatabaseConfig) : TransactionRunner {

    private val logger = LoggerFactory.getLogger(DatabaseFactory::class.java)

    private var dataSource: HikariDataSource? = null

    /**
     * Giu tham chieu TUONG MINH toi database nay, thay vi dua vao "database mac dinh"
     * toan cuc cua Exposed. Xem [TransactionRunner] de biet vi sao.
     */
    private var database: Database? = null

    fun connect(): Database {
        val hikariConfig = HikariConfig().apply {
            driverClassName = config.driver
            jdbcUrl = config.url
            username = config.user
            password = config.password
            maximumPoolSize = config.poolSize
            isAutoCommit = false
            /**
             * READ_COMMITTED - mac dinh cua PostgreSQL, va la muc BAT BUOC de
             * mau "SELECT ... FOR UPDATE" hoat dong dung.
             *
             * Vi sao khong dung REPEATABLE_READ? O muc do, transaction chup mot
             * "anh" du lieu ngay luc bat dau va giu nguyen anh do den cuoi.
             * Nghia la: transaction B cho lay duoc khoa dong xe, nhung khi doc lai
             * bang bookings no VAN THAY anh cu - khong thay don ma A vua ghi
             * => B tuong con trong va tao don trung lich. Khoa dong tro nen vo nghia.
             *
             * Voi READ_COMMITTED, moi cau lenh doc du lieu MOI NHAT DA COMMIT,
             * nen sau khi cho A xong, B doc lai la thay ngay don cua A.
             */
            transactionIsolation = "TRANSACTION_READ_COMMITTED"

            // ---- Các tham số CHỈ production mới cần, nhưng bật luôn cho đồng nhất ----

            // Chờ tối đa bao lâu để mượn được một connection từ pool.
            // Không đặt thì mặc định 30s: một sự cố DB sẽ khiến toàn bộ request treo
            // 30 giây rồi mới lỗi, người dùng tưởng web chết.
            connectionTimeout = config.connectionTimeoutMs

            // Cảnh báo vào log khi một connection bị giữ quá lâu mà không trả về pool.
            // Đây là cách phát hiện "connection leak" — bug âm thầm làm cạn pool
            // sau vài giờ chạy rồi mới sập.
            leakDetectionThreshold = config.leakDetectionThresholdMs

            // Vòng đời tối đa của một connection. Giúp tránh dùng phải connection
            // đã bị firewall/proxy cắt ngầm mà pool chưa biết.
            maxLifetime = 30 * 60 * 1000L
            idleTimeout = 10 * 60 * 1000L
            validationTimeout = 5_000L
            poolName = "vehicle-rental-pool"
        }

        val ds = HikariDataSource(hikariConfig)
        dataSource = ds
        logger.info("Da ket noi database: {} (pool size {})", maskUrl(config.url), config.poolSize)

        val db = Database.connect(ds)
        database = db
        return db
    }

    /**
     * Chạy migration bằng Flyway TRƯỚC khi ứng dụng nhận request đầu tiên.
     *
     * `baselineOnMigrate = true` cho phép chạy Flyway lần đầu trên một database
     * đã có sẵn bảng (ví dụ database cũ tạo bằng SchemaUtils).
     * `validateOnMigrate = true` sẽ CHẶN khởi động nếu file migration đã chạy
     * bị sửa nội dung — bảo vệ khỏi tình trạng mỗi môi trường một schema.
     */
    fun migrate() {
        val ds = requireNotNull(dataSource) { "Phải gọi connect() trước khi migrate()" }

        val flyway = Flyway.configure()
            .dataSource(ds)
            .locations("classpath:db/migration")
            .baselineOnMigrate(true)
            .validateOnMigrate(true)
            .load()

        val result = flyway.migrate()
        if (result.migrationsExecuted == 0) {
            logger.info("Database da o phien ban moi nhat, khong co migration nao can chay")
        } else {
            logger.info(
                "Da chay {} migration, schema hien tai o phien ban {}",
                result.migrationsExecuted,
                result.targetSchemaVersion
            )
        }
    }

    /**
     * Đối chiếu định nghĩa bảng trong code (Exposed) với schema thật trong DB.
     *
     * Chỉ CHẠY Ở DEV và chỉ GHI LOG, không tự sửa gì. Mục đích: bắt sớm tình huống
     * bạn thêm cột vào `Tables.kt` nhưng quên viết file migration tương ứng —
     * lỗi này ở production sẽ biểu hiện thành "column not found" giữa đêm.
     */
    fun warnIfSchemaDrifted() {
        transaction(db = database) {
            val missing = SchemaUtils.statementsRequiredToActualizeScheme(
                Users, VehicleTypes, Vehicles, Bookings, RefreshTokens
            )
            if (missing.isNotEmpty()) {
                logger.warn(
                    "CANH BAO: dinh nghia bang trong code khong khop voi schema DB. " +
                        "Co the ban da sua Tables.kt ma quen viet migration. Cac lenh con thieu:"
                )
                missing.forEach { logger.warn("   {}", it) }
            }
        }
    }

    /**
     * Kiểm tra database còn trả lời được không — phục vụ readiness probe.
     * Không dùng `SELECT 1` thủ công mà dùng [DataSource.getConnection] + isValid
     * để kiểm tra cả pool lẫn kết nối.
     */
    fun isHealthy(): Boolean = try {
        dataSource?.connection?.use { it.isValid(2) } ?: false
    } catch (e: Exception) {
        logger.warn("Health check database that bai: {}", e.message)
        false
    }

    /** Đóng pool khi ứng dụng tắt. Thiếu bước này thì container không thoát sạch. */
    fun close() {
        dataSource?.let {
            logger.info("Dang dong connection pool...")
            it.close()
        }
        dataSource = null
        database = null
    }

    /** Che mật khẩu nếu ai đó nhét credential thẳng vào JDBC URL. */
    private fun maskUrl(url: String): String =
        url.replace(Regex("password=[^&;]*", RegexOption.IGNORE_CASE), "password=***")

    /**
     * Chay mot khoi lenh trong MOT transaction, tren thread pool danh cho I/O.
     *
     * ⚠️ THAY DOI QUAN TRONG SO VOI BAN PET PROJECT:
     * Truoc day MOI HAM repository tu goi dbQuery, nghia la moi cau lenh la mot
     * transaction rieng. Hau qua: `confirm()` cap nhat bang bookings roi cap nhat
     * bang vehicles trong 2 transaction - hong giua chung la du lieu lech nhau.
     *
     * Gio **repository khong tu mo transaction nua**; tang service mo dung mot
     * transaction bao tron mot use case. Day la mau "Unit of Work":
     * ranh gioi transaction trung voi ranh gioi nghiep vu.
     *
     * `db = database` chi dinh TUONG MINH database can dung, thay vi de Exposed
     * doan theo thread-local.
     */
    override suspend fun <T> tx(block: suspend Transaction.() -> T): T {
        val db = requireNotNull(database) { "Phai goi connect() truoc khi chay transaction" }
        return newSuspendedTransaction(Dispatchers.IO, db = db) { block() }
    }
}
