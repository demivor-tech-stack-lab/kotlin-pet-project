package com.vehiclerental.routes

import com.vehiclerental.dto.ChangePasswordRequest
import com.vehiclerental.dto.LoginRequest
import com.vehiclerental.dto.MessageResponse
import com.vehiclerental.dto.RefreshTokenRequest
import com.vehiclerental.dto.RegisterRequest
import com.vehiclerental.security.requireUser
import com.vehiclerental.service.AuthService
import com.vehiclerental.util.RateLimitNames
import com.vehiclerental.util.rateLimited
import com.vehiclerental.util.clientInfo
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

/**
 * TẦNG ROUTE: chỉ làm 3 việc — đọc dữ liệu vào, gọi service, trả kết quả ra.
 *
 * `rateLimit(RateLimitNames.AUTH) { }` bọc quanh các endpoint không cần đăng nhập.
 * Đây là tuyến phòng thủ bắt buộc ở production: nếu không có nó, kẻ tấn công
 * gọi /login vài nghìn lần một phút để dò mật khẩu mà không gì cản được.
 * Giới hạn được cấu hình qua RATE_LIMIT_AUTH_PER_MINUTE.
 */
fun Route.authRoutes(authService: AuthService, rateLimitEnabled: Boolean) {

    route("/api/auth") {

        rateLimited(RateLimitNames.AUTH, rateLimitEnabled) {

            post("/register") {
                // call.receive<T>() đọc body JSON và tự đổi thành data class nhờ ContentNegotiation
                val request = call.receive<RegisterRequest>()
                val result = authService.register(request, call.clientInfo())
                // 201 Created là status đúng cho "vừa tạo tài nguyên mới"
                call.respond(HttpStatusCode.Created, result)
            }

            post("/login") {
                val request = call.receive<LoginRequest>()
                call.respond(HttpStatusCode.OK, authService.login(request, call.clientInfo()))
            }

            /**
             * Đổi refresh token lấy access token mới.
             * Cũng bị giới hạn tần suất: refresh token bị lộ thì kẻ trộm cũng
             * không thể quay vòng token hàng loạt.
             */
            post("/refresh") {
                val request = call.receive<RefreshTokenRequest>()
                call.respond(HttpStatusCode.OK, authService.refresh(request.refreshToken, call.clientInfo()))
            }
        }

        /**
         * Logout KHÔNG yêu cầu access token còn hạn — chỉ cần refresh token.
         * Lý do: access token hết hạn sau 15 phút, người dùng vẫn phải đăng xuất được.
         */
        post("/logout") {
            val request = call.receive<RefreshTokenRequest>()
            authService.logout(request.refreshToken)
            call.respond(HttpStatusCode.OK, MessageResponse("Đã đăng xuất"))
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

            /** Đăng xuất khỏi mọi thiết bị — dùng khi nghi ngờ tài khoản bị lộ. */
            post("/logout-all") {
                val me = call.requireUser()
                val revoked = authService.logoutAll(me.id)
                call.respond(HttpStatusCode.OK, MessageResponse("Đã đăng xuất khỏi $revoked thiết bị"))
            }

            post("/change-password") {
                val me = call.requireUser()
                val request = call.receive<ChangePasswordRequest>()
                authService.changePassword(me.id, request)
                call.respond(
                    HttpStatusCode.OK,
                    MessageResponse("Đổi mật khẩu thành công. Vui lòng đăng nhập lại trên mọi thiết bị.")
                )
            }
        }
    }
}
