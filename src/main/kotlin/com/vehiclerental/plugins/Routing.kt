package com.vehiclerental.plugins

import com.vehiclerental.config.AppConfig
import com.vehiclerental.config.DatabaseFactory
import com.vehiclerental.routes.authRoutes
import com.vehiclerental.routes.bookingRoutes
import com.vehiclerental.routes.opsRoutes
import com.vehiclerental.routes.vehicleRoutes
import com.vehiclerental.service.AuthService
import com.vehiclerental.service.BookingService
import com.vehiclerental.service.VehicleService
import com.vehiclerental.util.RateLimitNames
import com.vehiclerental.util.rateLimited
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import org.koin.ktor.ext.inject

/**
 * ĐIỂM TẬP HỢP CỦA TOÀN BỘ API.
 *
 * Mỗi nhóm endpoint nằm ở file riêng trong package `routes`, ở đây chỉ ghép lại.
 * Muốn biết dự án có những API nào -> mở đúng file này là thấy toàn cảnh.
 */
fun Application.configureRouting(
    appConfig: AppConfig,
    databaseFactory: DatabaseFactory,
    meterRegistry: PrometheusMeterRegistry
) {

    // Lấy service từ Koin. `by inject()` là lazy: chỉ thực sự lấy khi dùng lần đầu.
    val authService by inject<AuthService>()
    val vehicleService by inject<VehicleService>()
    val bookingService by inject<BookingService>()

    routing {

        get("/") {
            call.respondText(
                "Vehicle Rental API. Kiem tra tinh trang tai /health/ready",
                ContentType.Text.Plain
            )
        }

        /**
         * Endpoint vận hành nằm NGOÀI rate limit.
         *
         * Rất quan trọng: nếu health probe bị tính vào giới hạn tần suất thì khi
         * hệ thống đang quá tải, probe sẽ nhận 429 -> Docker tưởng app chết ->
         * restart container -> càng quá tải. Vòng lặp tự hủy kinh điển.
         */
        opsRoutes(databaseFactory, appConfig, meterRegistry)

        // Auth tự quản lý rate limit riêng (chặt hơn) bên trong authRoutes.
        authRoutes(authService, appConfig.rateLimit.enabled)

        // Phần còn lại dùng giới hạn chung.
        rateLimited(RateLimitNames.GLOBAL, appConfig.rateLimit.enabled) {
            vehicleRoutes(vehicleService, bookingService)
            bookingRoutes(bookingService)
        }
    }
}
