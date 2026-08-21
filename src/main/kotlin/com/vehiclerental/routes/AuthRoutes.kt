package com.vehiclerental.routes

import com.vehiclerental.dto.LoginRequest
import com.vehiclerental.dto.RegisterRequest
import com.vehiclerental.security.requireUser
import com.vehiclerental.service.AuthService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * TẦNG ROUTE: chỉ làm 3 việc - đọc dữ liệu vào, gọi service, trả kết quả ra.
 * Nếu bạn thấy mình viết if kiểm tra nghiệp vụ ở đây thì nghĩa là đặt sai chỗ,
 * hãy đẩy xuống service.
 *
 * `fun Route.authRoutes(...)` là extension function trên Route:
 * viết như thể Route có sẵn hàm này, nhờ vậy ghép route rất gọn.
 *
 * Service được TRUYỀN VÀO qua tham số (không tự new bên trong)
 * -> route không phụ thuộc vào cách service được tạo ra.
 */
fun Route.authRoutes(authService: AuthService) {

    route("/api/auth") {

        post("/register") {
            // call.receive<T>() đọc body JSON và tự đổi thành data class nhờ ContentNegotiation
            val request = call.receive<RegisterRequest>()
            val result = authService.register(request)
            // 201 Created là status đúng cho "vừa tạo tài nguyên mới"
            call.respond(HttpStatusCode.Created, result)
        }

        post("/login") {
            val request = call.receive<LoginRequest>()
            call.respond(HttpStatusCode.OK, authService.login(request))
        }

        /**
         * authenticate("auth-jwt") { } bọc quanh các route CẦN ĐĂNG NHẬP.
         * Ktor tự đọc header `Authorization: Bearer <token>`, kiểm tra chữ ký
         * và hạn dùng. Sai/thiếu token -> trả 401 mà không vào block bên trong.
         */
        authenticate("auth-jwt") {
            get("/me") {
                val me = call.requireUser()
                call.respond(HttpStatusCode.OK, authService.me(me.id))
            }
        }
    }
}
