package com.vehiclerental.plugins

import com.vehiclerental.config.AppConfig
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.ktor.server.metrics.micrometer.*
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics
import io.micrometer.core.instrument.binder.system.ProcessorMetrics
import org.slf4j.event.Level
import java.util.UUID

/**
 * Các plugin "hạ tầng": nhận diện request, log, CORS, header bảo mật, metrics.
 */
fun Application.configureMonitoring(
    appConfig: AppConfig,
    meterRegistry: PrometheusMeterRegistry
) {

    /**
     * ĐỌC IP THẬT CỦA CLIENT.
     *
     * Ở production app chạy sau nginx/Traefik. Nếu không có plugin này thì
     * `remoteHost` luôn là IP của reverse proxy => log vô dụng và rate limit
     * sẽ chặn nhầm tất cả người dùng như thể họ là một người.
     *
     * ⚠️ CHỈ AN TOÀN khi bạn thật sự đứng sau proxy tin cậy: header X-Forwarded-For
     * do client tự đặt được, nên nếu app phơi thẳng ra Internet thì kẻ tấn công
     * có thể giả IP để né rate limit.
     */
    install(XForwardedHeaders)

    /**
     * MỖI REQUEST MỘT ĐỊNH DANH.
     *
     * Đây là thứ biến việc gỡ lỗi production từ "mò kim đáy bể" thành "grep một chuỗi":
     * khách báo lỗi kèm mã X-Request-Id, bạn grep mã đó trong log là ra đúng
     * toàn bộ dòng đời của request đó.
     *
     * Nếu client (hoặc API gateway) đã gửi sẵn header thì dùng lại, để một
     * request đi qua nhiều dịch vụ vẫn giữ chung một mã.
     */
    install(CallId) {
        header(HttpHeaders.XRequestId)
        generate { UUID.randomUUID().toString() }
        // Chỉ chấp nhận mã do client gửi khi nó "sạch" — tránh bị nhét ký tự
        // điều khiển vào log (log injection).
        verify { callId -> callId.isNotBlank() && callId.length <= 64 && callId.all { it.isLetterOrDigit() || it == '-' } }

        // Dung appendIfAbsent thay cho replyToHeader(): replyToHeader co the ghi
        // header hai lan (mot lan khi nhan request, mot lan khi tra response),
        // ma header trung lap de lam roi client va cong cu doc log.
        reply { call, callId ->
            if (call.response.headers[HttpHeaders.XRequestId] == null) {
                call.response.header(HttpHeaders.XRequestId, callId)
            }
        }
    }

    install(CallLogging) {
        level = Level.INFO
        filter { call -> call.request.path().startsWith("/api") }

        // Đưa requestId vào MDC => mọi dòng log sinh ra TRONG request đó đều tự
        // kèm mã này, kể cả log viết từ service hay repository.
        // Ktor lo phần khó nhất: truyền MDC qua các ranh giới coroutine.
        callIdMdc("requestId")

        format { call ->
            val status = call.response.status()?.value ?: "-"
            val method = call.request.httpMethod.value
            "$method ${call.request.path()} -> $status"
        }
    }

    /**
     * CORS: trình duyệt chặn JS ở domain A gọi API ở domain B trừ khi server cho phép.
     *
     * Ở dev cho phép mọi host cho tiện. Ở production, [AppConfig] BẮT BUỘC phải
     * khai báo danh sách host cụ thể — nếu không app sẽ từ chối khởi động.
     */
    install(CORS) {
        if (appConfig.cors.allowAnyHost) {
            anyHost()
        } else {
            appConfig.cors.allowedHosts.forEach { host ->
                allowHost(host, schemes = listOf("https", "http"))
            }
        }
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowHeader(HttpHeaders.XRequestId)
        exposeHeader(HttpHeaders.XRequestId)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
        allowCredentials = !appConfig.cors.allowAnyHost
        maxAgeInSeconds = 3600
    }

    /**
     * HEADER BẢO MẬT gắn vào mọi response.
     * Rẻ tiền nhưng chặn được vài lớp tấn công phổ biến — không có lý do gì không bật.
     */
    install(DefaultHeaders) {
        // Chặn trình duyệt "đoán" kiểu nội dung: kẻ tấn công upload file .txt
        // chứa JavaScript rồi dụ trình duyệt chạy nó như script.
        header("X-Content-Type-Options", "nosniff")

        // Chặn nhúng trang này vào iframe của site khác (clickjacking).
        header("X-Frame-Options", "DENY")

        // Không gửi URL đầy đủ (có thể chứa token) sang site thứ ba.
        header("Referrer-Policy", "no-referrer")

        // Tắt sẵn các API nhạy cảm của trình duyệt.
        header("Permissions-Policy", "geolocation=(), microphone=(), camera=()")

        // API thuần JSON thì không cần chạy script nào cả.
        header("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'")

        header(HttpHeaders.Server, "api")

        if (appConfig.environment.isProd) {
            // Buộc trình duyệt chỉ dùng HTTPS trong 1 năm tới.
            // CHỈ bật ở production: bật ở localhost sẽ làm hỏng việc dev qua http.
            header("Strict-Transport-Security", "max-age=31536000; includeSubDomains")
        }

        // Ghi đè header "Server". Mặc định Ktor tự gắn "Server: Ktor/2.3.11",
        // tức là công khai tên VÀ phiên bản framework — kẻ tấn công chỉ cần tra
        // danh sách lỗ hổng đã biết của đúng phiên bản đó. Đặt một giá trị vô nghĩa
        // để không cho thông tin gì (bỏ hẳn thì Ktor lại điền mặc định).
    }

    /**
     * METRICS cho Prometheus.
     *
     * Ktor tự ghi nhận số request, thời gian phản hồi, phân bố theo status code.
     * Kèm theo các chỉ số JVM: bộ nhớ, GC, thread, CPU — đủ để trả lời
     * "API chậm vì code hay vì máy chủ hết RAM?".
     */
    if (appConfig.metrics.enabled) {
        install(MicrometerMetrics) {
            registry = meterRegistry
            meterBinders = listOf(
                JvmMemoryMetrics(),
                JvmGcMetrics(),
                JvmThreadMetrics(),
                ProcessorMetrics()
            )
        }
    }
}

/** Tạo registry ở ngoài để endpoint /metrics dùng chung một thể hiện. */
fun createMeterRegistry(): PrometheusMeterRegistry =
    PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
