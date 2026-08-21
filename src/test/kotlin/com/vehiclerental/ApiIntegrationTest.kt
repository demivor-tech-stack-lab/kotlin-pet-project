package com.vehiclerental

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * INTEGRATION TEST: khởi động ứng dụng thật (kể cả Flyway migration, DB H2 và JWT)
 * trong bộ nhớ, rồi gọi API qua HTTP client giả lập — KHÔNG mở cổng mạng thật.
 */
class ApiIntegrationTest {

    // ---------------------------------------------------------------- HEALTH

    @Test
    fun `liveness luon UP`() = testApplication {
        useTestConfig()
        val response = client.get("/health/live")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("UP", response.str("status"))
    }

    @Test
    fun `readiness kiem tra ca database`() = testApplication {
        useTestConfig()
        val response = client.get("/health/ready")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("UP", response.jsonBody()["checks"]!!.jsonObjectOrNull()?.get("database")?.jsonPrimitive?.content)
    }

    @Test
    fun `metrics tra ve dinh dang Prometheus`() = testApplication {
        useTestConfig()
        val response = client.get("/metrics")
        assertEquals(HttpStatusCode.OK, response.status)
        // Ktor tự ghi nhận chỉ số request; jvm_memory_used_bytes đến từ JvmMemoryMetrics.
        assertTrue(response.bodyAsText().contains("jvm_memory_used_bytes"))
    }

    // ---------------------------------------------------------------- SECURITY HEADERS

    @Test
    fun `moi response deu co header bao mat va request id`() = testApplication {
        useTestConfig()
        val response = client.get("/api/vehicles")

        assertEquals("nosniff", response.headers["X-Content-Type-Options"])
        assertEquals("DENY", response.headers["X-Frame-Options"])
        assertNotNull(response.headers["X-Request-Id"], "Thieu X-Request-Id de truy vet su co")
        // Khong duoc lo ten framework
        assertEquals(null, response.headers["X-Engine"])
    }

    @Test
    fun `request id do client gui duoc giu nguyen de truy vet xuyen dich vu`() = testApplication {
        useTestConfig()
        val myId = "abc-123-def"
        val response = client.get("/api/vehicles") {
            header(HttpHeaders.XRequestId, myId)
        }
        assertEquals(myId, response.headers["X-Request-Id"])
    }

    // ---------------------------------------------------------------- AUTH

    @Test
    fun `dang ky tra ve ca access token lan refresh token`() = testApplication {
        useTestConfig()
        val email = uniqueEmail("newuser")

        val register = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"matkhau123","fullName":"Test User","phone":"0912345678"}""")
        }

        assertEquals(HttpStatusCode.Created, register.status)
        val body = register.jsonBody()
        assertTrue(body["accessToken"]!!.jsonPrimitive.content.isNotBlank())
        assertTrue(body["refreshToken"]!!.jsonPrimitive.content.isNotBlank())
        assertEquals("Bearer", body["tokenType"]?.jsonPrimitive?.content)
        // Access token phai NGAN han: 900 giay = 15 phut
        assertEquals(900, body["expiresInSeconds"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `response dang ky khong bao gio chua passwordHash`() = testApplication {
        useTestConfig()
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"${uniqueEmail("leak")}","password":"matkhau123","fullName":"A","phone":"0912345678"}""")
        }
        val raw = response.bodyAsText()
        assertTrue(!raw.contains("passwordHash"), "Ro ri passwordHash ra API!")
        assertTrue(!raw.contains("\$2a\$"), "Ro ri chuoi hash BCrypt ra API!")
    }

    @Test
    fun `mat khau duoi 8 ky tu bi tu choi`() = testApplication {
        useTestConfig()
        val response = client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"${uniqueEmail("weak")}","password":"1234567","fullName":"A","phone":"0912345678"}""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("WEAK_PASSWORD", response.str("errorCode"))
    }

    @Test
    fun `sai email va sai mat khau tra ve cung mot thong bao`() = testApplication {
        useTestConfig()

        val wrongPassword = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$ADMIN_EMAIL","password":"sai-mat-khau"}""")
        }
        val noSuchEmail = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"khong-ton-tai@test.com","password":"sai-mat-khau"}""")
        }

        assertEquals(HttpStatusCode.Unauthorized, wrongPassword.status)
        assertEquals(HttpStatusCode.Unauthorized, noSuchEmail.status)
        // Thong bao GIONG HET nhau -> ke tan cong khong do duoc email nao co that
        assertEquals(wrongPassword.str("message"), noSuchEmail.str("message"))
    }

    // ---------------------------------------------------------------- VEHICLES

    @Test
    fun `danh sach xe co du lieu mau va phan trang`() = testApplication {
        useTestConfig()
        val response = client.get("/api/vehicles?page=1&size=2")

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.jsonBody()
        assertEquals(2, body["size"]?.jsonPrimitive?.content?.toInt())
        assertTrue((body["totalItems"]?.jsonPrimitive?.content?.toLong() ?: 0) >= 6)
    }

    @Test
    fun `size vuot gioi han bi kep ve 50`() = testApplication {
        useTestConfig()
        val response = client.get("/api/vehicles?size=100000")
        assertEquals(50, response.jsonBody()["size"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `loc theo khoang gia`() = testApplication {
        useTestConfig()
        val response = client.get("/api/vehicles?minPricePerDay=600000&maxPricePerDay=800000")
        assertEquals(HttpStatusCode.OK, response.status)

        val items = response.jsonBody()["items"]!!.jsonArray
        assertTrue(items.isNotEmpty())
        items.forEach { item ->
            val price = item.jsonObjectOrNull()!!["pricePerDay"]!!.jsonPrimitive.content.toBigDecimal()
            assertTrue(price >= "600000".toBigDecimal() && price <= "800000".toBigDecimal(), "Gia ngoai khoang: $price")
        }
    }

    @Test
    fun `status khong hop le tra ve 400 kem danh sach gia tri dung`() = testApplication {
        useTestConfig()
        val response = client.get("/api/vehicles?status=KHONG_CO_THAT")
        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("INVALID_ENUM_VALUE", response.str("errorCode"))
        assertTrue(response.str("message")!!.contains("AVAILABLE"))
    }

    @Test
    fun `xem xe khong ton tai tra ve 404 kem errorCode`() = testApplication {
        useTestConfig()
        val response = client.get("/api/vehicles/999999")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("NOT_FOUND", response.str("errorCode"))
    }

    // ---------------------------------------------------------------- PHAN QUYEN

    @Test
    fun `goi API booking khi chua dang nhap tra ve 401`() = testApplication {
        useTestConfig()
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/bookings/my").status)
    }

    @Test
    fun `khach thuong khong duoc them xe`() = testApplication {
        useTestConfig()
        val token = accessToken(CUSTOMER_EMAIL)

        val response = client.post("/api/admin/vehicles") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"plateNumber":"99Z-00001","brand":"Kia","model":"Morning","year":2023,"typeId":2,"pricePerDay":"600000"}""")
        }

        assertEquals(HttpStatusCode.Forbidden, response.status)
        assertEquals("FORBIDDEN", response.str("errorCode"))
    }

    @Test
    fun `khach thuong khong xem duoc thong ke`() = testApplication {
        useTestConfig()
        val token = accessToken(CUSTOMER_EMAIL)
        val response = client.get("/api/admin/stats") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Forbidden, response.status)
    }

    @Test
    fun `admin xem duoc thong ke`() = testApplication {
        useTestConfig()
        val token = accessToken(ADMIN_EMAIL)
        val response = client.get("/api/admin/stats") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue((response.jsonBody()["totalVehicles"]!!.jsonPrimitive.content.toLong()) >= 6)
    }

    // ---------------------------------------------------------------- BOOKING

    @Test
    fun `luong dat xe day du`() = testApplication {
        useTestConfig()
        val token = accessToken(CUSTOMER_EMAIL)
        val start = futureStart(120)
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
        val bookingId = created.str("id")
        assertEquals("PENDING", created.str("status"))

        // 3. Đặt trùng lịch -> phải bị chặn
        val overlap = client.post("/api/bookings") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"vehicleId":3,"startAt":"${iso(start.plusDays(1))}","endAt":"${iso(end.plusDays(1))}"}""")
        }
        assertEquals(HttpStatusCode.Conflict, overlap.status)
        assertEquals("BOOKING_OVERLAP", overlap.str("errorCode"))

        // 4. Lịch bận hiển thị công khai và KHÔNG lộ ai đặt
        val availability = client.get("/api/vehicles/3/availability")
        assertEquals(HttpStatusCode.OK, availability.status)
        val rawAvailability = availability.bodyAsText()
        assertTrue(rawAvailability.contains("startAt"))
        assertTrue(!rawAvailability.contains("userId"), "Lich ban khong duoc lo userId")

        // 5. Hủy đơn
        val cancelled = client.patch("/api/bookings/$bookingId/cancel") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.OK, cancelled.status)
        assertEquals("CANCELLED", cancelled.str("status"))

        // 6. Hủy lần hai -> phải báo lỗi
        val cancelAgain = client.patch("/api/bookings/$bookingId/cancel") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }
        assertEquals(HttpStatusCode.Conflict, cancelAgain.status)
    }

    @Test
    fun `khach khong huy duoc don sat gio nhan xe`() = testApplication {
        useTestConfig()
        val customerToken = accessToken(CUSTOMER_EMAIL)

        // Đặt xe bắt đầu sau 2 tiếng — dưới ngưỡng 24 giờ
        val start = LocalDateTimeNowPlusHours(2)
        val created = client.post("/api/bookings") {
            header(HttpHeaders.Authorization, "Bearer $customerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"vehicleId":6,"startAt":"${iso(start)}","endAt":"${iso(start.plusDays(1))}"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val id = created.str("id")

        val cancel = client.patch("/api/bookings/$id/cancel") {
            header(HttpHeaders.Authorization, "Bearer $customerToken")
        }
        assertEquals(HttpStatusCode.Conflict, cancel.status)
        assertEquals("TOO_LATE_TO_CANCEL", cancel.str("errorCode"))

        // Nhưng ADMIN thì vẫn hủy được
        val adminToken = accessToken(ADMIN_EMAIL)
        val adminCancel = client.patch("/api/bookings/$id/cancel") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, adminCancel.status)
    }

    @Test
    fun `dat xe voi thoi gian nguoc bi tu choi`() = testApplication {
        useTestConfig()
        val token = accessToken(CUSTOMER_EMAIL)
        val start = futureStart(200)

        val response = client.post("/api/bookings") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody("""{"vehicleId":1,"startAt":"${iso(start)}","endAt":"${iso(start.minusDays(1))}"}""")
        }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        assertEquals("INVALID_PERIOD", response.str("errorCode"))
    }

    @Test
    fun `admin duyet don thi xe chuyen sang RENTED`() = testApplication {
        useTestConfig()
        val customerToken = accessToken(CUSTOMER_EMAIL)
        val adminToken = accessToken(ADMIN_EMAIL)
        val start = futureStart(300)

        val created = client.post("/api/bookings") {
            header(HttpHeaders.Authorization, "Bearer $customerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"vehicleId":5,"startAt":"${iso(start)}","endAt":"${iso(start.plusDays(2))}"}""")
        }
        val id = created.str("id")

        val confirmed = client.patch("/api/admin/bookings/$id/confirm") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.OK, confirmed.status)
        assertEquals("CONFIRMED", confirmed.str("status"))

        // HAI bang phai thay doi CUNG NHAU
        assertEquals("RENTED", client.get("/api/vehicles/5").str("status"))

        val completed = client.patch("/api/admin/bookings/$id/complete") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals("COMPLETED", completed.str("status"))
        assertEquals("AVAILABLE", client.get("/api/vehicles/5").str("status"))

        // Tat toan lan hai -> 409
        val again = client.patch("/api/admin/bookings/$id/complete") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }
        assertEquals(HttpStatusCode.Conflict, again.status)
        assertEquals("INVALID_STATE", again.str("errorCode"))
    }

    @Test
    fun `endpoint khong ton tai tra ve JSON chu khong phai HTML`() = testApplication {
        useTestConfig()
        val response = client.get("/api/khong-ton-tai")
        assertEquals(HttpStatusCode.NotFound, response.status)
        assertEquals("ROUTE_NOT_FOUND", response.str("errorCode"))
    }
}

// ----- vài helper nhỏ chỉ dùng trong file này -----

private fun LocalDateTimeNowPlusHours(hours: Long) =
    java.time.LocalDateTime.now().plusHours(hours).withSecond(0).withNano(0)

private fun kotlinx.serialization.json.JsonElement.jsonObjectOrNull() =
    this as? kotlinx.serialization.json.JsonObject
