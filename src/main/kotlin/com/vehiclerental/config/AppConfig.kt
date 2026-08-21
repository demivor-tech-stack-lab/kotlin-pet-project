package com.vehiclerental.config

import io.ktor.server.config.*

/**
 * CẤU HÌNH TẬP TRUNG THEO CHUẨN 12-FACTOR.
 *
 * Nguyên tắc: **mọi thứ khác nhau giữa các môi trường đều là biến môi trường**,
 * không phải file khác nhau. Cùng một artifact (cùng một Docker image) chạy được
 * ở dev, staging, production — chỉ khác biến môi trường.
 *
 * Vì sao không để trong application.yaml như trước?
 *   - File yaml bị commit lên Git => secret lộ cho mọi người đọc được repo.
 *   - Muốn đổi mật khẩu DB phải build lại app.
 *
 * `application.yaml` giờ chỉ còn giá trị MẶC ĐỊNH cho môi trường dev.
 */
data class AppConfig(
    val environment: Environment,
    val database: DatabaseConfig,
    val jwt: JwtConfig,
    val cors: CorsConfig,
    val rateLimit: RateLimitConfig,
    val metrics: MetricsConfig,
    val seedData: Boolean,
    /**
     * Mui gio nghiep vu. Gio nhan/tra xe la "gio treo tuong" cua cong ty cho thue,
     * nen moi cho goi LocalDateTime.now() deu phai di qua TimeProvider dung mui gio nay,
     * thay vi phu thuoc vao mui gio cua may chu (container thuong chay UTC).
     */
    val timezone: java.time.ZoneId
) {

    enum class Environment {
        DEV, PROD;

        val isProd: Boolean get() = this == PROD
    }

    data class DatabaseConfig(
        val driver: String,
        val url: String,
        val user: String,
        val password: String,
        val poolSize: Int,
        val connectionTimeoutMs: Long,
        val leakDetectionThresholdMs: Long
    )

    data class JwtConfig(
        val secret: String,
        val issuer: String,
        val audience: String,
        val realm: String,
        /** Access token cố tình để NGẮN: lộ token thì thiệt hại cũng chỉ trong 15 phút. */
        val accessTokenTtlSeconds: Long,
        /** Refresh token dài hạn nhưng lưu trong DB nên THU HỒI ĐƯỢC. */
        val refreshTokenTtlSeconds: Long
    )

    data class CorsConfig(
        /** Danh sách host được phép, ví dụ "app.example.com,admin.example.com". */
        val allowedHosts: List<String>,
        val allowAnyHost: Boolean
    )

    data class RateLimitConfig(
        val enabled: Boolean,
        /** Số lần gọi tối đa cho nhóm endpoint đăng nhập/đăng ký. */
        val authRequestsPerMinute: Int,
        /** Số lần gọi tối đa cho toàn bộ API còn lại. */
        val globalRequestsPerMinute: Int
    )

    data class MetricsConfig(
        val enabled: Boolean,
        /** Nếu đặt, /metrics yêu cầu header Authorization: Bearer <token>. */
        val token: String?
    )

    companion object {

        /** Giá trị mặc định của dev — nếu thấy chuỗi này ở prod thì phải chặn ngay. */
        const val INSECURE_DEV_SECRET = "dev-only-secret-khong-duoc-dung-o-production-32chars"

        private const val MIN_SECRET_LENGTH = 32

        /**
         * Đọc cấu hình theo thứ tự ưu tiên: **biến môi trường** > giá trị trong application.yaml.
         *
         * Ném [IllegalStateException] với thông báo rõ ràng nếu cấu hình prod không an toàn.
         * Fail fast lúc khởi động luôn tốt hơn là chạy được nhưng mất an toàn âm thầm.
         */
        fun load(config: ApplicationConfig): AppConfig {
            val env = when (readEnv("APP_ENV", config, "app.environment", "dev").lowercase()) {
                "prod", "production" -> Environment.PROD
                else -> Environment.DEV
            }

            val jwt = JwtConfig(
                secret = readEnv("JWT_SECRET", config, "jwt.secret", INSECURE_DEV_SECRET),
                issuer = readEnv("JWT_ISSUER", config, "jwt.issuer", "vehicle-rental-api"),
                audience = readEnv("JWT_AUDIENCE", config, "jwt.audience", "vehicle-rental-users"),
                realm = readEnv("JWT_REALM", config, "jwt.realm", "Vehicle Rental"),
                accessTokenTtlSeconds = readEnv("JWT_ACCESS_TTL_SECONDS", config, "jwt.accessTokenTtlSeconds", "900").toLong(),
                refreshTokenTtlSeconds = readEnv("JWT_REFRESH_TTL_SECONDS", config, "jwt.refreshTokenTtlSeconds", "2592000").toLong()
            )

            val database = DatabaseConfig(
                driver = readEnv("DB_DRIVER", config, "database.driver", "org.h2.Driver"),
                url = readEnv("DB_URL", config, "database.url", "jdbc:h2:mem:vehiclerental;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;LOCK_TIMEOUT=10000;CASE_INSENSITIVE_IDENTIFIERS=TRUE"),
                user = readEnv("DB_USER", config, "database.user", "sa"),
                password = readEnv("DB_PASSWORD", config, "database.password", ""),
                poolSize = readEnv("DB_POOL_SIZE", config, "database.poolSize", "10").toInt(),
                connectionTimeoutMs = readEnv("DB_CONNECTION_TIMEOUT_MS", config, "database.connectionTimeoutMs", "10000").toLong(),
                leakDetectionThresholdMs = readEnv("DB_LEAK_DETECTION_MS", config, "database.leakDetectionThresholdMs", "20000").toLong()
            )

            val allowedHostsRaw = readEnv("CORS_ALLOWED_HOSTS", config, "cors.allowedHosts", "")
            val cors = CorsConfig(
                allowedHosts = allowedHostsRaw.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                allowAnyHost = readEnv("CORS_ALLOW_ANY_HOST", config, "cors.allowAnyHost", "true").toBoolean()
            )

            val rateLimit = RateLimitConfig(
                enabled = readEnv("RATE_LIMIT_ENABLED", config, "rateLimit.enabled", "true").toBoolean(),
                authRequestsPerMinute = readEnv("RATE_LIMIT_AUTH_PER_MINUTE", config, "rateLimit.authRequestsPerMinute", "10").toInt(),
                globalRequestsPerMinute = readEnv("RATE_LIMIT_GLOBAL_PER_MINUTE", config, "rateLimit.globalRequestsPerMinute", "300").toInt()
            )

            val metrics = MetricsConfig(
                enabled = readEnv("METRICS_ENABLED", config, "metrics.enabled", "true").toBoolean(),
                token = readEnv("METRICS_TOKEN", config, "metrics.token", "").ifBlank { null }
            )

            val appConfig = AppConfig(
                environment = env,
                database = database,
                jwt = jwt,
                cors = cors,
                rateLimit = rateLimit,
                metrics = metrics,
                seedData = readEnv("SEED_DATA", config, "app.seedData", if (env.isProd) "false" else "true").toBoolean(),
                timezone = java.time.ZoneId.of(readEnv("APP_TIMEZONE", config, "app.timezone", "Asia/Ho_Chi_Minh"))
            )

            appConfig.validate()
            return appConfig
        }

        /**
         * Ưu tiên biến môi trường, sau đó tới application.yaml, cuối cùng là default.
         * Trả về default thay vì ném lỗi khi thiếu -> app luôn chạy được ở dev
         * mà không cần khai báo 20 biến môi trường.
         */
        private fun readEnv(
            envName: String,
            config: ApplicationConfig,
            configPath: String,
            default: String
        ): String {
            System.getenv(envName)?.takeIf { it.isNotBlank() }?.let { return it }
            return config.propertyOrNull(configPath)?.getString()?.takeIf { it.isNotBlank() } ?: default
        }
    }

    /**
     * CÁC RÀNG BUỘC AN TOÀN CHO PRODUCTION.
     *
     * Đây là lớp bảo vệ quan trọng nhất của file này: nó biến một sự cố bảo mật
     * âm thầm (deploy nhầm với secret dev) thành một lỗi khởi động ồn ào.
     * Container không start được thì bạn sửa ngay; app chạy với secret dev thì
     * vài tháng sau mới biết, lúc đó đã muộn.
     */
    private fun validate() {
        val errors = mutableListOf<String>()

        if (jwt.secret.length < MIN_SECRET_LENGTH) {
            errors += "JWT_SECRET phải dài tối thiểu $MIN_SECRET_LENGTH ký tự (hiện tại ${jwt.secret.length})"
        }
        if (jwt.accessTokenTtlSeconds <= 0 || jwt.refreshTokenTtlSeconds <= 0) {
            errors += "TTL của token phải lớn hơn 0"
        }
        if (database.poolSize !in 1..100) {
            errors += "DB_POOL_SIZE phải trong khoảng 1..100 (hiện tại ${database.poolSize})"
        }

        if (environment.isProd) {
            if (jwt.secret == INSECURE_DEV_SECRET) {
                errors += "JWT_SECRET đang dùng giá trị mặc định của dev. Sinh secret mới: openssl rand -base64 48"
            }
            if (database.driver.contains("h2", ignoreCase = true)) {
                errors += "Không được dùng H2 in-memory ở production, hãy đặt DB_DRIVER/DB_URL trỏ tới PostgreSQL"
            }
            if (database.password.isBlank()) {
                errors += "DB_PASSWORD không được để trống ở production"
            }
            if (cors.allowAnyHost) {
                errors += "CORS_ALLOW_ANY_HOST=true ở production là không an toàn. Đặt CORS_ALLOW_ANY_HOST=false và liệt kê CORS_ALLOWED_HOSTS"
            }
            if (!cors.allowAnyHost && cors.allowedHosts.isEmpty()) {
                errors += "Cần khai báo CORS_ALLOWED_HOSTS khi đã tắt allowAnyHost"
            }
            if (seedData) {
                errors += "SEED_DATA=true ở production sẽ tạo tài khoản admin mật khẩu 123456. Đặt SEED_DATA=false"
            }
            if (!rateLimit.enabled) {
                errors += "RATE_LIMIT_ENABLED=false ở production khiến endpoint đăng nhập bị bruteforce tự do"
            }
        }

        if (errors.isNotEmpty()) {
            throw IllegalStateException(
                buildString {
                    appendLine("Cấu hình không hợp lệ, ứng dụng dừng khởi động:")
                    errors.forEach { appendLine("  - $it") }
                    appendLine("Xem file .env.example để biết danh sách biến môi trường.")
                }
            )
        }
    }
}
