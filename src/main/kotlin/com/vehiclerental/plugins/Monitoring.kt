package com.vehiclerental.plugins

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.cors.routing.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.request.*
import org.slf4j.event.Level

/**
 * Các plugin "hạ tầng" gom chung một chỗ: log request, CORS, header mặc định.
 */
fun Application.configureMonitoring() {

    install(CallLogging) {
        level = Level.INFO
        // Chỉ log request vào /api, bỏ qua favicon và tài nguyên tĩnh cho đỡ nhiễu
        filter { call -> call.request.path().startsWith("/api") }
        format { call ->
            val status = call.response.status()?.value ?: "-"
            val method = call.request.httpMethod.value
            "$method ${call.request.local.uri} -> $status"
        }
    }

    /**
     * CORS: trình duyệt chặn JS ở domain A gọi API ở domain B trừ khi server cho phép.
     * anyHost() rất tiện khi học nhưng KHÔNG được dùng ở production -
     * lúc đó hãy thay bằng allowHost("app.cua-ban.com", schemes = listOf("https")).
     */
    install(CORS) {
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Patch)
        allowMethod(HttpMethod.Delete)
    }

    install(DefaultHeaders) {
        header("X-Engine", "Ktor")
    }
}
