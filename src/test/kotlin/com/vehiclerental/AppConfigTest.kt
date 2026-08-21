package com.vehiclerental

import com.vehiclerental.config.AppConfig
import io.ktor.server.config.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Test cho LỚP BẢO VỆ CẤU HÌNH.
 *
 * Đây là loại test rẻ nhất mà giá trị cao nhất: nó bảo đảm rằng một lần deploy
 * nhầm cấu hình sẽ làm container KHÔNG START, thay vì âm thầm chạy với secret
 * của môi trường dev suốt vài tháng.
 *
 * Không cần khởi động server, không cần database.
 */
class AppConfigTest {

    /** Dựng cấu hình tối thiểu hợp lệ, rồi ghi đè từng giá trị để thử. */
    private fun config(vararg overrides: Pair<String, String>): ApplicationConfig {
        val base = mutableMapOf(
            "app.environment" to "prod",
            "app.seedData" to "false",
            "app.timezone" to "Asia/Ho_Chi_Minh",
            "database.driver" to "org.postgresql.Driver",
            "database.url" to "jdbc:postgresql://db:5432/vehiclerental",
            "database.user" to "postgres",
            "database.password" to "mat-khau-that",
            "database.poolSize" to "10",
            "database.connectionTimeoutMs" to "10000",
            "database.leakDetectionThresholdMs" to "20000",
            "jwt.secret" to "day-la-secret-that-dai-tren-32-ky-tu-nhe",
            "jwt.issuer" to "vehicle-rental-api",
            "jwt.audience" to "vehicle-rental-users",
            "jwt.realm" to "Vehicle Rental",
            "jwt.accessTokenTtlSeconds" to "900",
            "jwt.refreshTokenTtlSeconds" to "2592000",
            "cors.allowAnyHost" to "false",
            "cors.allowedHosts" to "app.example.com",
            "rateLimit.enabled" to "true",
            "rateLimit.authRequestsPerMinute" to "10",
            "rateLimit.globalRequestsPerMinute" to "300",
            "metrics.enabled" to "true",
            "metrics.token" to "metrics-secret"
        )
        overrides.forEach { (key, value) -> base[key] = value }
        return MapApplicationConfig(*base.map { it.key to it.value }.toTypedArray())
    }

    @Test
    fun `cau hinh production hop le thi load duoc`() {
        val appConfig = AppConfig.load(config())

        assertEquals(AppConfig.Environment.PROD, appConfig.environment)
        assertTrue(appConfig.environment.isProd)
        assertEquals(listOf("app.example.com"), appConfig.cors.allowedHosts)
        assertEquals(900, appConfig.jwt.accessTokenTtlSeconds)
    }

    @Test
    fun `prod voi secret mac dinh cua dev bi tu choi`() {
        val error = assertFailsWith<IllegalStateException> {
            AppConfig.load(config("jwt.secret" to AppConfig.INSECURE_DEV_SECRET))
        }
        assertTrue(error.message!!.contains("JWT_SECRET"), error.message!!)
    }

    @Test
    fun `secret ngan hon 32 ky tu bi tu choi o moi moi truong`() {
        val error = assertFailsWith<IllegalStateException> {
            AppConfig.load(config("app.environment" to "dev", "jwt.secret" to "qua-ngan"))
        }
        assertTrue(error.message!!.contains("32 ký tự"), error.message!!)
    }

    @Test
    fun `prod dung H2 bi tu choi`() {
        val error = assertFailsWith<IllegalStateException> {
            AppConfig.load(config("database.driver" to "org.h2.Driver"))
        }
        assertTrue(error.message!!.contains("H2"), error.message!!)
    }

    @Test
    fun `prod voi CORS mo cho moi host bi tu choi`() {
        val error = assertFailsWith<IllegalStateException> {
            AppConfig.load(config("cors.allowAnyHost" to "true"))
        }
        assertTrue(error.message!!.contains("CORS"), error.message!!)
    }

    @Test
    fun `prod bat seedData bi tu choi`() {
        val error = assertFailsWith<IllegalStateException> {
            AppConfig.load(config("app.seedData" to "true"))
        }
        assertTrue(error.message!!.contains("SEED_DATA"), error.message!!)
    }

    @Test
    fun `prod tat rate limit bi tu choi`() {
        val error = assertFailsWith<IllegalStateException> {
            AppConfig.load(config("rateLimit.enabled" to "false"))
        }
        assertTrue(error.message!!.contains("RATE_LIMIT"), error.message!!)
    }

    @Test
    fun `prod khong co mat khau database bi tu choi`() {
        val error = assertFailsWith<IllegalStateException> {
            AppConfig.load(config("database.password" to ""))
        }
        assertTrue(error.message!!.contains("DB_PASSWORD"), error.message!!)
    }

    @Test
    fun `moi truong dev de dai hon`() {
        // Cùng bộ cấu hình "lỏng lẻo" đó, ở dev thì chấp nhận được.
        val appConfig = AppConfig.load(
            config(
                "app.environment" to "dev",
                "database.driver" to "org.h2.Driver",
                "database.password" to "",
                "cors.allowAnyHost" to "true",
                "app.seedData" to "true"
            )
        )
        assertEquals(AppConfig.Environment.DEV, appConfig.environment)
        assertTrue(appConfig.seedData)
    }

    @Test
    fun `bao loi liet ke TAT CA van de cung luc`() {
        val error = assertFailsWith<IllegalStateException> {
            AppConfig.load(
                config(
                    "jwt.secret" to AppConfig.INSECURE_DEV_SECRET,
                    "database.driver" to "org.h2.Driver",
                    "cors.allowAnyHost" to "true"
                )
            )
        }
        // Liệt kê hết một lượt để người deploy sửa một lần, thay vì sửa-chạy-lỗi-sửa
        val message = error.message!!
        assertTrue(message.contains("JWT_SECRET"))
        assertTrue(message.contains("H2"))
        assertTrue(message.contains("CORS"))
    }
}
