package com.vehiclerental.routes

import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.domain.model.VehicleStatus
import com.vehiclerental.dto.CreateVehicleRequest
import com.vehiclerental.security.requireAdmin
import com.vehiclerental.service.BookingService
import com.vehiclerental.service.VehicleService
import com.vehiclerental.util.enumQuery
import com.vehiclerental.util.intQuery
import com.vehiclerental.util.longParam
import com.vehiclerental.util.stringQuery
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.math.BigDecimal

fun Route.vehicleRoutes(
    vehicleService: VehicleService,
    bookingService: BookingService
) {

    // ---------- Public: ai cũng xem được danh sách xe ----------
    route("/api/vehicles") {

        get {
            // Ví dụ: /api/vehicles?status=AVAILABLE&keyword=vios&page=1&size=10
            val result = vehicleService.list(
                typeId = call.stringQuery("typeId")?.toLongOrNull(),
                status = call.enumQuery<VehicleStatus>("status"),
                keyword = call.stringQuery("keyword"),
                minPricePerDay = call.decimalQuery("minPricePerDay"),
                maxPricePerDay = call.decimalQuery("maxPricePerDay"),
                page = call.intQuery("page", 1),
                size = call.intQuery("size", 10)
            )
            call.respond(HttpStatusCode.OK, result)
        }

        get("/{id}") {
            call.respond(HttpStatusCode.OK, vehicleService.getById(call.longParam("id")))
        }

        /**
         * Lịch bận của xe — giao diện đặt xe dùng để chặn ngày trước khi người dùng bấm.
         * Công khai và cố ý không lộ ai đang thuê.
         */
        get("/{id}/availability") {
            call.respond(HttpStatusCode.OK, bookingService.busyPeriods(call.longParam("id")))
        }
    }

    route("/api/vehicle-types") {
        get {
            call.respond(HttpStatusCode.OK, vehicleService.listTypes())
        }
        get("/{id}") {
            call.respond(HttpStatusCode.OK, vehicleService.getTypeById(call.longParam("id")))
        }
    }

    // ---------- Admin: cần token VÀ role = ADMIN ----------
    authenticate("auth-jwt") {

        route("/api/admin/vehicles") {

            post {
                call.requireAdmin()   // ném 403 nếu không phải admin
                val request = call.receive<CreateVehicleRequest>()
                call.respond(HttpStatusCode.Created, vehicleService.create(request))
            }

            patch("/{id}/status") {
                call.requireAdmin()
                val id = call.longParam("id")
                val body = call.receive<UpdateStatusBody>()
                val status = enumValues<VehicleStatus>()
                    .firstOrNull { it.name.equals(body.status, ignoreCase = true) }
                    ?: throw AppException.BadRequest(
                        "status phải là một trong: " + enumValues<VehicleStatus>().joinToString { it.name }
                    )
                call.respond(HttpStatusCode.OK, vehicleService.updateStatus(id, status))
            }
        }

        /** Số liệu tổng quan cho trang quản trị. */
        get("/api/admin/stats") {
            call.requireAdmin()
            call.respond(HttpStatusCode.OK, vehicleService.stats())
        }
    }
}

/** Đọc query parameter kiểu tiền tệ, sai định dạng thì 400 thay vì 500. */
private fun ApplicationCall.decimalQuery(name: String): BigDecimal? =
    stringQuery(name)?.let { raw ->
        raw.toBigDecimalOrNull()
            ?: throw AppException.BadRequest("$name phải là số", "INVALID_NUMBER")
    }

/**
 * DTO nhỏ chỉ dùng cho đúng một endpoint thì để ngay cạnh endpoint đó cũng được —
 * không phải cái gì cũng phải nhét vào package dto.
 */
@kotlinx.serialization.Serializable
data class UpdateStatusBody(val status: String)
