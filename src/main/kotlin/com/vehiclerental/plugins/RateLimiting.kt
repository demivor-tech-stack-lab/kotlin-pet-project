package com.vehiclerental.plugins

import com.vehiclerental.config.AppConfig
import com.vehiclerental.dto.ErrorResponse
import com.vehiclerental.util.RateLimitNames
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import kotlin.time.Duration.Companion.minutes

/**
 * GIỚI HẠN TẦN SUẤT GỌI API.
 *
 * Không có nó, một script đơn giản có thể gọi /api/auth/login vài nghìn lần mỗi phút
 * để dò mật khẩu, hoặc gọi /api/vehicles liên tục làm cạn connection pool.
 * Đây là thứ bắt buộc phải có trước khi mở API ra Internet.
 *
 * Hai nhóm giới hạn:
 *   - AUTH   : rất chặt (mặc định 10 lần/phút), áp cho đăng ký/đăng nhập/refresh.
 *   - GLOBAL : rộng hơn (mặc định 300 lần/phút), áp cho phần còn lại.
 *
 * ⚠️ Giới hạn này nằm TRONG BỘ NHỚ của từng tiến trình. Khi bạn chạy nhiều bản sao
 * (scale ngang), mỗi bản có bộ đếm riêng — tổng số lần gọi cho phép sẽ nhân lên
 * theo số bản sao. Muốn chính xác tuyệt đối thì phải chuyển bộ đếm sang Redis.
 * Với một VPS chạy một container thì cách này là đủ.
 */
fun Application.configureRateLimiting(appConfig: AppConfig) {
    if (!appConfig.rateLimit.enabled) return

    install(RateLimit) {

        register(RateLimitNames.AUTH) {
            rateLimiter(limit = appConfig.rateLimit.authRequestsPerMinute, refillPeriod = 1.minutes)

            // Đếm theo IP. Endpoint đăng nhập thì chưa biết người dùng là ai,
            // nên IP là thứ duy nhất để phân biệt.
            requestKey { call -> call.request.origin.remoteHost }

            modifyResponse { call, state ->
                call.response.headers.appendIfAbsent(
                    "X-RateLimit-Limit",
                    appConfig.rateLimit.authRequestsPerMinute.toString()
                )
                if (state is RateLimiter.State.Available) {
                    call.response.headers.appendIfAbsent("X-RateLimit-Remaining", state.remainingTokens.toString())
                }
            }
        }

        register(RateLimitNames.GLOBAL) {
            rateLimiter(limit = appConfig.rateLimit.globalRequestsPerMinute, refillPeriod = 1.minutes)

            /**
             * Người đã đăng nhập thì đếm theo userId, chưa đăng nhập thì đếm theo IP.
             *
             * Vì sao không đếm theo IP cho tất cả? Vì nhiều người dùng chung một IP
             * (văn phòng, NAT của nhà mạng) sẽ chặn nhầm lẫn nhau. Đếm theo userId
             * khi có thể là công bằng hơn.
             */
            requestKey { call ->
                call.principal<JWTPrincipal>()
                    ?.payload
                    ?.getClaim(com.vehiclerental.security.JwtService.CLAIM_USER_ID)
                    ?.asLong()
                    ?.let { userId -> "user:$userId" }
                    ?: "ip:${call.request.origin.remoteHost}"
            }
        }
    }
}

/**
 * Ktor mặc định trả 429 với body rỗng. Ta trả JSON đúng định dạng chung
 * để client xử lý lỗi nhất quán, kèm gợi ý chờ bao lâu.
 *
 * Hàm này được gọi từ StatusPages.
 */
suspend fun respondRateLimited(call: ApplicationCall) {
    // Ktor dat san header Retry-After khi biet con phai cho bao lau.
    // Neu khong co thi tra ve cau chung chung, khong ghep bua thanh "sau it phut giay".
    val retryAfter = call.response.headers[HttpHeaders.RetryAfter]
    val goiY = if (retryAfter != null) "sau $retryAfter giây" else "sau ít phút"

    call.respond(
        HttpStatusCode.TooManyRequests,
        ErrorResponse(
            errorCode = "RATE_LIMITED",
            message = "Bạn thao tác quá nhanh. Vui lòng thử lại $goiY.",
            path = call.request.local.uri
        )
    )
}
