package com.vehiclerental.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

/**
 * ContentNegotiation = "thương lượng kiểu nội dung".
 * Sau khi cài plugin này:
 *   - call.receive<RegisterRequest>()  : JSON -> data class
 *   - call.respond(userResponse)       : data class -> JSON
 * mà bạn không phải viết một dòng parse nào.
 */
fun Application.configureSerialization() {
    install(ContentNegotiation) {
        json(
            Json {
                prettyPrint = true          // JSON trả về xuống dòng đẹp, tiện khi học/debug
                isLenient = false           // không chấp nhận JSON viết ẩu
                ignoreUnknownKeys = true    // client gửi thừa field thì bỏ qua thay vì lỗi
                encodeDefaults = true       // vẫn ghi ra field có giá trị mặc định
            }
        )
    }
}
