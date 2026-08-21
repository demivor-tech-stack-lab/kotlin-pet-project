package com.vehiclerental.routes

import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.domain.model.VehicleStatus
import com.vehiclerental.dto.CreateVehicleRequest
import com.vehiclerental.security.requireAdmin
import com.vehiclerental.service.VehicleService
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

fun Route.vehicleRoutes(vehicleService: VehicleService) {

    // ---------- Public: ai cũng xem được danh sách xe ----------
    route("/api/vehicles") {

        get {
            // Ví dụ: /api/vehicles?status=AVAILABLE&keyword=vios&page=1&size=10
            val status = call.stringQuery("status")?.let { raw ->
                // enumValues<T>() lấy toàn bộ giá trị của enum; tìm cái khớp tên (không phân biệt hoa thường)
                enumValues<VehicleStatus>().firstOrNull { it.name.equals(raw, ignoreCase = true) }
                    ?: throw AppException.BadRequest("status không hợp lệ: $raw")
            }

            val maxPrice = call.stringQuery("maxPricePerDay")?.let { raw ->
                raw.toBigDecimalOrNull()
                    ?: throw AppException.BadRequest("maxPricePerDay phải là số")
            }

            val result = vehicleService.list(
                typeId = call.stringQuery("typeId")?.toLongOrNull(),
                status = status,
                keyword = call.stringQuery("keyword"),
                maxPricePerDay = maxPrice,
                page = call.intQuery("page", 1),
                size = call.intQuery("size", 10)
            )
            call.respond(HttpStatusCode.OK, result)
        }

        get("/{id}") {
            val id = call.longParam("id")
            call.respond(HttpStatusCode.OK, vehicleService.getById(id))
        }
    }

    get("/api/vehicle-types") {
        call.respond(HttpStatusCode.OK, vehicleService.listTypes())
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
    }
}

/**
 * DTO nhỏ chỉ dùng cho đúng một endpoint thì để ngay cạnh endpoint đó cũng được -
 * không phải cái gì cũng phải nhét vào package dto.
 */
@kotlinx.serialization.Serializable
data class UpdateStatusBody(val status: String)
