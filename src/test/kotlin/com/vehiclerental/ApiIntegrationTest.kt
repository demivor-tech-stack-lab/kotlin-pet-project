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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * INTEGRATION TEST: khởi động ứng dụng thật (kể cả DB H2 và JWT) trong bộ nhớ,
 * rồi gọi API qua HTTP client giả lập - KHÔNG mở cổng mạng thật.
 *
 * testApplication { } lo hết phần dựng server; bên trong dùng `client` như
 * đang gọi API thật.
 */
class ApiIntegrationTest {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    /** Hàm phụ: mỗi test dùng một email riêng để không đụng nhau (DB dùng chung cả JVM). */
    private fun uniqueEmail(prefix: String) = "$prefix${System.nanoTime()}@test.com"

    private fun iso(dateTime: LocalDateTime): String =
        dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

    /**
     * Nạp đúng file application.yaml của app -> test chạy trên cùng cấu hình
     * với lúc chạy thật, tránh cảnh "test xanh nhưng chạy thật thì đỏ".
     */
    private fun ApplicationTestBuilder.useAppConfig() {
        environment { config = ApplicationConfig("application.yaml") }
    }

    private suspend fun HttpResponse.jsonBody(): JsonObject =
        Json.parseToJsonElement(bodyAsText()).jsonObject

    @Test
    fun `health tra ve UP`() = testApplication {
        useAppConfig()

        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("UP", response.jsonBody()["status"]?.jsonPrimitive?.content)
    }

    @Test
    fun `dang ky roi dang nhap thanh cong`() = testApplication {
        useAppConfig()
        val email = uniqueEmail("newuser")

        val register = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"email":"$email","password":"matkhau123","fullName":"Test User","phone":"0912345678"}"""
            )
        }
        assertEquals(HttpStatusCode.Created, register.status)
        assertTrue(register.jsonBody()["token"]?.jsonPrimitive?.content?.isNotBlank() == true)

        val login = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"matkhau123"}""")
        }
        assertEquals(HttpStatusCode.OK, login.status)
    }

    @Test
    fun `dang ky voi mat khau qua ngan bi tu choi`() = testApplication {
        useAppConfig()

        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(
                """{"email":"${uniqueEmail("weak")}","password":"123","fullName":"A","phone":"0912345678"}"""
            )
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("WEAK_PASSWORD", response.jsonBody()["errorCode"]?.jsonPrimitive?.content)
    }

    @Test
    fun `sai mat khau tra ve 401`() = testApplication {
        useAppConfig()

        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"admin@vehiclerental.com","password":"sai-mat-khau"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `danh sach xe co du lieu mau va phan trang`() = testApplication {
        useAppConfig()

        val response = client.get("/api/vehicles?page=1&size=2")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.jsonBody()
        assertEquals(2, body["size"]?.jsonPrimitive?.content?.toInt())
        assertTrue((body["totalItems"]?.jsonPrimitive?.content?.toLong() ?: 0) >= 6)
    }

    @Test
    fun `xem xe khong ton tai tra ve 404 kem errorCode`() = testApplication {
        useAppConfig()

        val response = client.get("/api/vehicles/999999")

        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("NOT_FOUND", response.jsonBody()["errorCode"]?.jsonPrimitive?.content)
    }

    @Test
    fun `goi API booking khi chua dang nhap tra ve 401`() = testApplication {
        useAppConfig()

        val response = client.get("/api/bookings/my")

        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `khach thuong khong duoc them xe`() = testApplication {
        useAppConfig()
        val token = loginAs("khach@vehiclerental.com", "123456")

        val response = client.post("/api/admin/vehicles") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                """{"plateNumber":"99Z-00001","brand":"Kia","model":"Morning","year":2023,"typeId":2,"pricePerDay":"600000"}"""
            )
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("FORBIDDEN", response.jsonBody()["errorCode"]?.jsonPrimitive?.content)
    }

    @Test
    fun `admin them xe moi thanh cong`() = testApplication {
        useAppConfig()
        val token = loginAs("admin@vehiclerental.com", "123456")
        val plate = "88X-" + (System.nanoTime() % 100000)

        val response = client.post("/api/admin/vehicles") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(
                """{"plateNumber":"$plate","brand":"Kia","model":"Morning","year":2023,"typeId":2,"pricePerDay":"600000"}"""
            )
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals("AVAILABLE", response.jsonBody()["status"]?.jsonPrimitive?.content)
    }

    /**
     * TEST QUAN TRỌNG NHẤT: toàn bộ luồng đặt xe.
     * Đặt -> đặt trùng lịch bị chặn -> hủy -> hủy lần hai bị chặn.
     */
    @Test
    fun `luong dat xe day du`() = testApplication {
        useAppConfig()
        val token = loginAs("khach@vehiclerental.com", "123456")

        val start = LocalDateTime.now().plusDays(40).withHour(8).withMinute(0).withSecond(0).withNano(0)
        val end = start.plusDays(3)

        // 1. Báo giá trước
        val quote = client.post("/api/bookings/quote") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"vehicleId":3,"startAt":"${iso(start)}","endAt":"${iso(end)}"}""")
        }
        assertEquals(HttpStatusCode.OK, quote.status)
        assertEquals(3, quote.jsonBody()["totalDays"]?.jsonPrimitive?.content?.toInt())

        // 2. Đặt xe
        val created = client.post("/api/bookings") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"vehicleId":3,"startAt":"${iso(start)}","endAt":"${iso(end)}","note":"Test"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val bookingId = created.jsonBody()["id"]!!.jsonPrimitive.content
        assertEquals("PENDING", created.jsonBody()["status"]?.jsonPrimitive?.content)

        // 3. Đặt trùng lịch -> phải bị chặn
        val overlap = client.post("/api/bookings") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"vehicleId":3,"startAt":"${iso(start.plusDays(1))}","endAt":"${iso(end.plusDays(1))}"}""")
        }
        assertEquals(HttpStatusCode.Conflict, overlap.status)
        assertEquals("BOOKING_OVERLAP", overlap.jsonBody()["errorCode"]?.jsonPrimitive?.content)

        // 4. Hủy đơn
        val cancelled = client.patch("/api/bookings/$bookingId/cancel") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, cancelled.status)
        assertEquals("CANCELLED", cancelled.jsonBody()["status"]?.jsonPrimitive?.content)

        // 5. Hủy lần hai -> phải báo lỗi
        val cancelAgain = client.patch("/api/bookings/$bookingId/cancel") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, cancelAgain.status)
    }

    @Test
    fun `dat xe voi thoi gian nguoc bi tu choi`() = testApplication {
        useAppConfig()
        val token = loginAs("khach@vehiclerental.com", "123456")
        val start = LocalDateTime.now().plusDays(60)

        val response = client.post("/api/bookings") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"vehicleId":1,"startAt":"${iso(start)}","endAt":"${iso(start.minusDays(1))}"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("INVALID_PERIOD", response.jsonBody()["errorCode"]?.jsonPrimitive?.content)
    }

    /** Hàm phụ dùng lại ở nhiều test: đăng nhập và lấy token. */
    private suspend fun ApplicationTestBuilder.loginAs(email: String, password: String): String {
        val response = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status, "Dang nhap that bai: ${response.bodyAsText()}")
        return Json.parseToJsonElement(response.bodyAsText())
            .jsonObject["token"]!!.jsonPrimitive.content
    }
}
