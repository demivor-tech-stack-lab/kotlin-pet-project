package com.vehiclerental.routes

import com.vehiclerental.config.AppConfig
import com.vehiclerental.config.DatabaseFactory
import com.vehiclerental.dto.ErrorResponse
import com.vehiclerental.dto.HealthResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * CÁC ENDPOINT VẬN HÀNH — không phải nghiệp vụ, nhưng thiếu chúng thì không
 * deploy được đàng hoàng.
 *
 * Điểm mấu chốt: **liveness và readiness là hai câu hỏi khác nhau.**
 *
 *   - liveness  = "tiến trình còn sống không?"  Trả lời NO -> Docker/K8s KHỞI ĐỘNG LẠI container.
 *   - readiness = "sẵn sàng nhận request chưa?" Trả lời NO -> tạm NGỪNG GỬI request tới,
 *                 nhưng KHÔNG khởi động lại.
 *
 * Nhầm hai cái này gây hậu quả thật: nếu để liveness kiểm tra cả database,
 * thì mỗi lần database chớp tắt vài giây, toàn bộ container app sẽ bị restart
 * hàng loạt — biến một sự cố nhỏ thành sự cố toàn hệ thống.
 */
fun Route.opsRoutes(
    databaseFactory: DatabaseFactory,
    appConfig: AppConfig,
    meterRegistry: PrometheusMeterRegistry
) {

    /**
     * LIVENESS — cố tình KHÔNG kiểm tra database.
     * Chỉ cần trả lời được câu này nghĩa là JVM còn chạy và Netty còn nhận request.
     */
    get("/health/live") {
        call.respond(HttpStatusCode.OK, HealthResponse(status = "UP"))
    }

    /**
     * READINESS — có kiểm tra database.
     *
     * Chạy trên Dispatchers.IO vì kiểm tra kết nối là thao tác blocking;
     * để nó trên thread của Netty là tự làm nghẽn chính mình khi DB chậm.
     */
    get("/health/ready") {
        val dbOk = withContext(Dispatchers.IO) { databaseFactory.isHealthy() }

        val body = HealthResponse(
            status = if (dbOk) "UP" else "DOWN",
            checks = mapOf("database" to if (dbOk) "UP" else "DOWN")
        )
        // 503 chứ không phải 200-với-body-DOWN: bộ điều phối chỉ đọc HTTP status.
        call.respond(if (dbOk) HttpStatusCode.OK else HttpStatusCode.ServiceUnavailable, body)
    }

    /** Giữ lại đường dẫn cũ để không phá client đang dùng. */
    get("/api/health") {
        call.respond(HttpStatusCode.OK, HealthResponse(status = "UP"))
    }

    /**
     * METRICS cho Prometheus.
     *
     * Endpoint này lộ khá nhiều thông tin nội bộ (số request, đường dẫn, độ trễ,
     * bộ nhớ JVM), nên KHÔNG nên phơi ra Internet. Hai lớp bảo vệ:
     *   1. Đặt METRICS_TOKEN thì phải có header Authorization mới đọc được.
     *   2. Ở docker-compose, cổng app chỉ mở cho reverse proxy, và chặn
     *      /metrics từ bên ngoài tại nginx.
     */
    if (appConfig.metrics.enabled) {
        get("/metrics") {
            val requiredToken = appConfig.metrics.token
            if (requiredToken != null) {
                val provided = call.request.header(HttpHeaders.Authorization)
                    ?.removePrefix("Bearer ")
                    ?.trim()

                // Dùng so sánh thường là đủ ở đây, nhưng so sánh theo thời gian
                // hằng số vẫn tốt hơn khi đối chiếu bí mật.
                if (provided == null || !constantTimeEquals(provided, requiredToken)) {
                    call.respond(
                        HttpStatusCode.Unauthorized,
                        ErrorResponse("UNAUTHORIZED", "Cần token để xem metrics")
                    )
                    return@get
                }
            }
            call.respondText(meterRegistry.scrape(), ContentType.Text.Plain)
        }
    }
}

/**
 * So sánh hai chuỗi trong thời gian không phụ thuộc vào vị trí ký tự khác nhau.
 *
 * So sánh `==` thông thường thoát ra ngay khi gặp ký tự lệch đầu tiên, nên thời gian
 * chạy tiết lộ "bạn đã đoán đúng bao nhiêu ký tự đầu" — đủ để dò dần ra cả chuỗi.
 */
private fun constantTimeEquals(a: String, b: String): Boolean {
    val aBytes = a.toByteArray()
    val bBytes = b.toByteArray()
    if (aBytes.size != bBytes.size) return false

    var result = 0
    for (i in aBytes.indices) {
        result = result or (aBytes[i].toInt() xor bBytes[i].toInt())
    }
    return result == 0
}
