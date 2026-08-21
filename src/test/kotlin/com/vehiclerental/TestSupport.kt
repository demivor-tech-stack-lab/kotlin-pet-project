package com.vehiclerental

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.test.assertEquals

/**
 * Hàm dùng chung cho mọi test — gom lại một chỗ để test khác chỉ tập trung
 * vào thứ nó muốn kiểm tra.
 */

/** Tài khoản do DataSeeder tạo sẵn. */
const val ADMIN_EMAIL = "admin@vehiclerental.com"
const val CUSTOMER_EMAIL = "khach@vehiclerental.com"
const val SEED_PASSWORD = "matkhau123"

/**
 * Nạp file cấu hình dành riêng cho test.
 * Cùng đường đi với lúc chạy thật (vẫn qua AppConfig.load) nên vẫn kiểm chứng
 * được cả phần đọc cấu hình, chỉ khác giá trị.
 */
fun ApplicationTestBuilder.useTestConfig(path: String = "application-test.yaml") {
    environment { config = ApplicationConfig(path) }
}

fun iso(dateTime: LocalDateTime): String =
    dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

/** Mỗi test dùng email riêng vì H2 in-memory được chia sẻ trong cả JVM chạy test. */
fun uniqueEmail(prefix: String) = "$prefix${System.nanoTime()}@test.com"

suspend fun HttpResponse.jsonBody(): JsonObject =
    Json.parseToJsonElement(bodyAsText()).jsonObject

suspend fun HttpResponse.str(field: String): String? =
    jsonBody()[field]?.jsonPrimitive?.content

/** Đăng nhập và trả về cặp (accessToken, refreshToken). */
suspend fun ApplicationTestBuilder.login(
    email: String,
    password: String = SEED_PASSWORD
): Pair<String, String> {
    val response = client.post("/api/auth/login") {
        contentType(ContentType.Application.Json)
        setBody("""{"email":"$email","password":"$password"}""")
    }
    assertEquals(HttpStatusCode.OK, response.status, "Dang nhap that bai: ${response.bodyAsText()}")
    val body = response.jsonBody()
    return body["accessToken"]!!.jsonPrimitive.content to body["refreshToken"]!!.jsonPrimitive.content
}

suspend fun ApplicationTestBuilder.accessToken(email: String, password: String = SEED_PASSWORD): String =
    login(email, password).first

/** Mốc thời gian trong tương lai, lệch nhau theo `offsetDays` để các test không đụng lịch nhau. */
fun futureStart(offsetDays: Long): LocalDateTime =
    LocalDateTime.now()
        .plusDays(offsetDays)
        .withHour(8).withMinute(0).withSecond(0).withNano(0)
