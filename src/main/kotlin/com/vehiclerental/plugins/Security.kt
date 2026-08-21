package com.vehiclerental.plugins

import com.vehiclerental.dto.ErrorResponse
import com.vehiclerental.security.JwtService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import org.koin.ktor.ext.inject

/**
 * Cấu hình xác thực JWT.
 *
 * "auth-jwt" là TÊN của cấu hình này. Ở route ta viết authenticate("auth-jwt") { }
 * để nói "khu vực này cần token". Đặt sai tên -> Ktor ném lỗi ngay khi khởi động.
 */
fun Application.configureSecurity() {
    // by inject() : lấy JwtService từ Koin (đã đăng ký trong appModule).
    // `by` = property delegation, giá trị chỉ được lấy ra khi dùng lần đầu (lazy).
    val jwtService by inject<JwtService>()

    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtService.realm

            // Ktor dùng verifier để kiểm chữ ký + issuer + audience + hạn dùng
            verifier(jwtService.verifier)

            /**
             * validate chạy SAU khi token hợp lệ về mặt chữ ký.
             * Trả về principal (khác null) = cho đi tiếp; trả null = từ chối (401).
             * Đây là chỗ bạn có thể kiểm tra thêm, ví dụ user có bị khóa không.
             */
            validate { credential ->
                val userId = credential.payload.getClaim(JwtService.CLAIM_USER_ID).asLong()
                val role = credential.payload.getClaim(JwtService.CLAIM_ROLE).asString()

                if (userId != null && !role.isNullOrBlank()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }

            /** Không có challenge thì Ktor trả 401 với body rỗng - khó debug. */
            challenge { _, _ ->
                call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(
                        errorCode = "UNAUTHORIZED",
                        message = "Token không hợp lệ hoặc đã hết hạn",
                        path = call.request.local.uri
                    )
                )
            }
        }
    }
}
