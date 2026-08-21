package com.vehiclerental.routes

import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.domain.model.BookingStatus
import com.vehiclerental.dto.CreateBookingRequest
import com.vehiclerental.security.requireAdmin
import com.vehiclerental.security.requireUser
import com.vehiclerental.service.BookingService
import com.vehiclerental.util.enumQuery
import com.vehiclerental.util.longParam
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.bookingRoutes(bookingService: BookingService) {

    // TẤT CẢ endpoint booking đều yêu cầu đăng nhập
    authenticate("auth-jwt") {

        route("/api/bookings") {

            /** Báo giá thử, không tạo đơn. */
            post("/quote") {
                call.requireUser()
                val request = call.receive<CreateBookingRequest>()
                call.respond(
                    HttpStatusCode.OK,
                    bookingService.quote(request.vehicleId, request.startAt, request.endAt)
                )
            }

            post {
                val me = call.requireUser()
                val request = call.receive<CreateBookingRequest>()
                call.respond(HttpStatusCode.Created, bookingService.create(me.id, request))
            }

            /**
             * Đặt "/my" trước "/{id}" cho dễ đọc. Thật ra Ktor ưu tiên đoạn đường dẫn
             * cố định hơn tham số nên thứ tự khai báo không ảnh hưởng kết quả.
             */
            get("/my") {
                val me = call.requireUser()
                call.respond(HttpStatusCode.OK, bookingService.listMine(me.id, call.enumQuery<BookingStatus>("status")))
            }

            get("/{id}") {
                val me = call.requireUser()
                call.respond(HttpStatusCode.OK, bookingService.getById(call.longParam("id"), me))
            }

            patch("/{id}/cancel") {
                val me = call.requireUser()
                call.respond(HttpStatusCode.OK, bookingService.cancel(call.longParam("id"), me))
            }
        }

        // ---------- Khu vực ADMIN ----------
        route("/api/admin/bookings") {

            get {
                call.requireAdmin()
                call.respond(HttpStatusCode.OK, bookingService.listAll(call.enumQuery<BookingStatus>("status")))
            }

            patch("/{id}/confirm") {
                call.requireAdmin()
                call.respond(HttpStatusCode.OK, bookingService.confirm(call.longParam("id")))
            }

            patch("/{id}/complete") {
                call.requireAdmin()
                call.respond(HttpStatusCode.OK, bookingService.complete(call.longParam("id")))
            }
        }
    }
}
