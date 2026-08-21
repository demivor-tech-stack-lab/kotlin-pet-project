package com.vehiclerental

import com.vehiclerental.config.AppConfig
import com.vehiclerental.config.DataSeeder
import com.vehiclerental.config.DatabaseFactory
import com.vehiclerental.di.appModule
import com.vehiclerental.plugins.configureMonitoring
import com.vehiclerental.plugins.configureRateLimiting
import com.vehiclerental.plugins.configureRouting
import com.vehiclerental.plugins.configureSecurity
import com.vehiclerental.plugins.configureSerialization
import com.vehiclerental.plugins.configureStatusPages
import com.vehiclerental.plugins.createMeterRegistry
import com.vehiclerental.service.AuthService
import io.ktor.server.application.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import org.slf4j.LoggerFactory

/**
 * ĐIỂM KHỞI ĐỘNG CỦA ỨNG DỤNG.
 *
 * Luồng chạy khi bạn gõ `gradlew run`:
 *   1. main() gọi EngineMain.main(args)
 *   2. EngineMain đọc src/main/resources/application.yaml
 *   3. Trong file đó có dòng `modules: - com.vehiclerental.ApplicationKt.module`
 *   4. Ktor dùng reflection gọi hàm Application.module() bên dưới
 *
 * Vì sao là "ApplicationKt"? Kotlin cho phép viết hàm ngoài class; khi biên dịch,
 * các hàm top-level của file Application.kt được gom vào một class Java tên
 * <TênFile>Kt = ApplicationKt. Đổi tên file -> phải sửa lại application.yaml.
 */
fun main(args: Array<String>) {
    io.ktor.server.netty.EngineMain.main(args)
}

/**
 * "Module" của Ktor — nơi lắp ráp toàn bộ ứng dụng.
 *
 * THỨ TỰ Ở ĐÂY CÓ Ý NGHĨA:
 *   1. Đọc + kiểm tra cấu hình TRƯỚC TIÊN — cấu hình sai thì dừng ngay,
 *      đừng mở cổng rồi mới phát hiện thiếu secret.
 *   2. Kết nối DB + chạy migration trước khi nhận request đầu tiên.
 *   3. Koin phải cài trước vì configureSecurity/configureRouting đều inject từ nó.
 *   4. StatusPages cài trước routing để bắt được lỗi ném ra từ route.
 */
fun Application.module() {
    val logger = LoggerFactory.getLogger("Bootstrap")

    // ---- 1. Cấu hình (fail fast nếu không an toàn) ----
    val appConfig = AppConfig.load(environment.config)
    logger.info(
        "Khoi dong o moi truong {} | timezone {} | seedData={}",
        appConfig.environment, appConfig.timezone, appConfig.seedData
    )

    // ---- 2. Database ----
    val databaseFactory = DatabaseFactory(appConfig.database)
    databaseFactory.connect()
    databaseFactory.migrate()
    if (!appConfig.environment.isProd) {
        // Chỉ cảnh báo ở dev: bắt sớm việc sửa Tables.kt mà quên viết migration.
        databaseFactory.warnIfSchemaDrifted()
    }

    // ---- 3. Dependency Injection ----
    install(Koin) {
        slf4jLogger()
        modules(appModule(appConfig, databaseFactory))
    }

    // ---- 4. Các plugin dùng chung ----
    val meterRegistry = createMeterRegistry()
    configureSerialization()
    configureMonitoring(appConfig, meterRegistry)
    configureRateLimiting(appConfig)
    configureSecurity()
    configureStatusPages(appConfig)

    // ---- 5. Khai báo API ----
    configureRouting(appConfig, databaseFactory, meterRegistry)

    // ---- 6. Dữ liệu mẫu (chỉ dev) ----
    if (appConfig.seedData) {
        val seeder by inject<DataSeeder>()
        // runBlocking: chặn thread khởi động cho tới khi seed xong.
        // Chỉ dùng được ở lúc khởi động như thế này, TUYỆT ĐỐI không dùng trong route handler.
        runBlocking { seeder.seed() }
    }

    // ---- 7. Công việc chạy nền ----
    scheduleTokenCleanup()

    // ---- 8. Tắt máy êm ái ----
    configureGracefulShutdown(databaseFactory, meterRegistry)
}

/**
 * DỌN REFRESH TOKEN HẾT HẠN.
 *
 * Không dọn thì bảng refresh_tokens chỉ có phình to: mỗi lần đăng nhập và mỗi lần
 * refresh đều sinh thêm một dòng. Sau vài tháng nó thành bảng lớn nhất hệ thống
 * và làm chậm chính thao tác đăng nhập.
 *
 * Chạy trong coroutine của application scope, nên tự dừng khi app tắt.
 */
private fun Application.scheduleTokenCleanup() {
    val logger = LoggerFactory.getLogger("TokenCleanup")
    val authService by inject<AuthService>()

    launch {
        while (true) {
            try {
                val deleted = authService.cleanupExpiredTokens()
                if (deleted > 0) logger.info("Da xoa {} refresh token het han", deleted)
            } catch (e: Exception) {
                // Job nền lỗi thì ghi log rồi chạy tiếp — KHÔNG được để nó
                // làm chết cả ứng dụng.
                logger.warn("Don refresh token that bai: {}", e.message)
            }
            kotlinx.coroutines.delay(CLEANUP_INTERVAL_MS)
        }
    }
}

/**
 * TẮT MÁY ÊM ÁI (graceful shutdown).
 *
 * Khi bạn chạy `docker compose down` hoặc deploy phiên bản mới, Docker gửi tín hiệu
 * SIGTERM. Nếu không xử lý, tiến trình chết ngay lập tức và:
 *   - request đang xử lý dở bị cắt giữa chừng (khách mất tiền mà không có đơn),
 *   - connection pool không được đóng, để lại kết nối rác phía PostgreSQL.
 *
 * `shutdownGracePeriod` trong application.yaml cho Ktor thời gian xử lý nốt
 * request đang chạy; hàm này lo dọn tài nguyên sau đó.
 */
private fun Application.configureGracefulShutdown(
    databaseFactory: DatabaseFactory,
    meterRegistry: io.micrometer.prometheus.PrometheusMeterRegistry
) {
    val logger = LoggerFactory.getLogger("Shutdown")

    environment.monitor.subscribe(ApplicationStopping) {
        logger.info("Nhan tin hieu dung, dang don dep tai nguyen...")
    }

    environment.monitor.subscribe(ApplicationStopped) {
        databaseFactory.close()
        meterRegistry.close()
        logger.info("Da dong connection pool va metrics registry. Tam biet.")
    }
}

private const val CLEANUP_INTERVAL_MS = 6 * 60 * 60 * 1000L   // 6 tiếng một lần
