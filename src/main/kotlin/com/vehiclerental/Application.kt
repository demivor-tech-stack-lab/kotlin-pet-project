package com.vehiclerental

import com.vehiclerental.config.DataSeeder
import com.vehiclerental.config.DatabaseFactory
import com.vehiclerental.di.appModule
import com.vehiclerental.plugins.configureMonitoring
import com.vehiclerental.plugins.configureRouting
import com.vehiclerental.plugins.configureSecurity
import com.vehiclerental.plugins.configureSerialization
import com.vehiclerental.plugins.configureStatusPages
import io.ktor.server.application.*
import kotlinx.coroutines.runBlocking
import org.koin.ktor.ext.inject
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger

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
 * "Module" của Ktor - nơi lắp ráp toàn bộ ứng dụng.
 * THỨ TỰ Ở ĐÂY CÓ Ý NGHĨA:
 *   - Koin phải cài TRƯỚC vì configureSecurity/configureRouting đều inject từ nó.
 *   - StatusPages nên cài trước routing để bắt được lỗi ném ra từ route.
 */
fun Application.module() {
    val config = environment.config

    // 1. Kết nối database + tạo bảng
    DatabaseFactory.init(config)

    // 2. Dependency Injection
    install(Koin) {
        slf4jLogger()               // in ra log các bean được Koin tạo
        modules(appModule(config))
    }

    // 3. Các plugin dùng chung
    configureSerialization()
    configureMonitoring()
    configureSecurity()
    configureStatusPages()

    // 4. Khai báo API
    configureRouting()

    // 5. Nạp dữ liệu mẫu (bật/tắt bằng app.seedData trong application.yaml)
    val seedEnabled = config.propertyOrNull("app.seedData")?.getString()?.toBoolean() ?: false
    if (seedEnabled) {
        val seeder by inject<DataSeeder>()
        // runBlocking: chặn thread khởi động cho tới khi seed xong.
        // Chỉ nên dùng ở lúc khởi động như thế này, TUYỆT ĐỐI không dùng trong route handler.
        runBlocking { seeder.seed() }
    }
}
