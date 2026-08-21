package com.vehiclerental.util

import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.service.ClientInfo
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.ratelimit.RateLimitName
import io.ktor.server.plugins.ratelimit.rateLimit
import io.ktor.server.request.*
import io.ktor.server.routing.Route

/** Tên các nhóm giới hạn tần suất. Gom vào một chỗ để không gõ sai chuỗi ở hai nơi. */
object RateLimitNames {
    val AUTH = RateLimitName("auth")
    val GLOBAL = RateLimitName("global")
}

/**
 * Bọc một nhóm route bằng giới hạn tần suất, nhưng CHỈ KHI nó được bật.
 *
 * Vì sao cần hàm này? Ktor ném lỗi ngay lúc khởi động nếu route dùng
 * `rateLimit("auth")` mà plugin RateLimit chưa đăng ký tên đó. Mà bộ test
 * lại cần tắt rate limit (hàng trăm request trong vài giây sẽ dính 429 và
 * làm test đỏ vì lý do không liên quan tới thứ đang kiểm tra).
 *
 * Viết `if` ngay trong từng file route sẽ lặp lại ở nhiều chỗ, nên gom vào đây.
 */
fun Route.rateLimited(
    name: RateLimitName,
    enabled: Boolean,
    build: Route.() -> Unit
) {
    if (enabled) {
        rateLimit(name) { build() }
    } else {
        build()
    }
}

/**
 * Đọc path parameter dạng số, ví dụ /api/vehicles/{id}
 * Nếu thiếu hoặc không phải số -> 400 chứ không phải 500.
 *
 * `?:` là ELVIS OPERATOR: "lấy vế trái, nếu null thì lấy vế phải".
 */
fun ApplicationCall.longParam(name: String): Long =
    parameters[name]?.toLongOrNull()
        ?: throw AppException.BadRequest("Tham số '$name' phải là số nguyên")

/** Đọc query parameter dạng số, có giá trị mặc định khi client không truyền. */
fun ApplicationCall.intQuery(name: String, default: Int): Int =
    request.queryParameters[name]?.toIntOrNull() ?: default

fun ApplicationCall.stringQuery(name: String): String? =
    request.queryParameters[name]?.takeIf { it.isNotBlank() }

/**
 * Thông tin thiết bị gọi API, lưu kèm refresh token.
 *
 * `request.origin.remoteHost` đọc được IP THẬT của client nhờ plugin ForwardedHeaders:
 * khi app chạy sau nginx/Traefik trong Docker, nếu không có plugin đó thì mọi request
 * đều hiện IP của reverse proxy — vô dụng cho việc điều tra sự cố và cho rate limit.
 */
fun ApplicationCall.clientInfo(): ClientInfo = ClientInfo(
    userAgent = request.userAgent(),
    ipAddress = request.origin.remoteHost
)

/**
 * Đọc query parameter kiểu enum, không phân biệt hoa thường.
 * Giá trị sai -> 400 kèm danh sách giá trị hợp lệ, thay vì im lặng bỏ qua bộ lọc
 * (im lặng bỏ qua là kiểu bug rất khó phát hiện: API trả sai dữ liệu mà không báo gì).
 *
 * `inline` + `reified` cho phép hàm generic biết được kiểu T lúc chạy,
 * nhờ vậy `enumValues<T>()` mới hoạt động.
 */
inline fun <reified T : Enum<T>> ApplicationCall.enumQuery(name: String): T? {
    val raw = stringQuery(name) ?: return null
    return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) }
        ?: throw AppException.BadRequest(
            "$name phải là một trong: " + enumValues<T>().joinToString { it.name },
            "INVALID_ENUM_VALUE"
        )
}
