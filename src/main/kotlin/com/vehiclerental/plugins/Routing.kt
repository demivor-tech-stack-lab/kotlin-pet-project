package com.vehiclerental.plugins

import com.vehiclerental.routes.authRoutes
import com.vehiclerental.routes.bookingRoutes
import com.vehiclerental.routes.vehicleRoutes
import com.vehiclerental.service.AuthService
import com.vehiclerental.service.BookingService
import com.vehiclerental.service.VehicleService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable
import org.koin.ktor.ext.inject

@Serializable
data class HealthResponse(val status: String, val service: String, val version: String)

/**
 * ĐIỂM TẬP HỢP CỦA TOÀN BỘ API.
 *
 * Mỗi nhóm endpoint nằm ở file riêng trong package `routes`, ở đây chỉ ghép lại.
 * Muốn biết dự án có những API nào -> mở đúng file này là thấy toàn cảnh.
 */
fun Application.configureRouting() {

    // Lấy service từ Koin. `by inject()` là lazy: chỉ thực sự lấy khi dùng lần đầu.
    val authService by inject<AuthService>()
    val vehicleService by inject<VehicleService>()
    val bookingService by inject<BookingService>()

    routing {
        get("/") {
            call.respondText(
                "Vehicle Rental API dang chay. Xem huong dan tai /api/health",
                ContentType.Text.Plain
            )
        }

        get("/api/health") {
            call.respond(
                HttpStatusCode.OK,
                HealthResponse(status = "UP", service = "vehicle-rental-api", version = "0.0.1")
            )
        }

        authRoutes(authService)
        vehicleRoutes(vehicleService)
        bookingRoutes(bookingService)
    }
}
