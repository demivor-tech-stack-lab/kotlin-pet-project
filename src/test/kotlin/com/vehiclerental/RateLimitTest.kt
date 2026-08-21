package com.vehiclerental

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Kiểm chứng lớp phòng thủ chống bruteforce mật khẩu.
 *
 * Dùng file cấu hình riêng (`application-ratelimit-test.yaml`) đặt giới hạn
 * xuống 3 lần/phút, để test chạy nhanh mà vẫn kiểm tra đúng cơ chế.
 * Các test khác dùng cấu hình tắt rate limit — nếu bật, chúng sẽ đỏ vì 429
 * chứ không phải vì code sai.
 */
class RateLimitTest {

    private fun ApplicationTestBuilder.useRateLimitConfig() =
        useTestConfig("application-ratelimit-test.yaml")

    @Test
    fun `dang nhap sai lien tuc bi chan boi rate limit`() = testApplication {
        useRateLimitConfig()

        val statuses = (1..6).map {
            client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"$ADMIN_EMAIL","password":"doan-mo-lan-$it"}""")
            }.status
        }

        // 3 lần đầu được xử lý bình thường (và trả 401 vì sai mật khẩu)
        assertEquals(HttpStatusCode.Unauthorized, statuses[0])
        assertEquals(HttpStatusCode.Unauthorized, statuses[1])
        assertEquals(HttpStatusCode.Unauthorized, statuses[2])

        // Từ lần thứ 4 trở đi bị chặn
        assertTrue(
            statuses.drop(3).all { it == HttpStatusCode.TooManyRequests },
            "Rate limit khong hoat dong. Thuc te: $statuses"
        )
    }

    @Test
    fun `response 429 tra ve JSON dung dinh dang chung`() = testApplication {
        useRateLimitConfig()

        var lastResponse = client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"a@b.com","password":"x"}""")
        }
        repeat(6) {
            lastResponse = client.post("/api/auth/login") {
                contentType(ContentType.Application.Json)
                setBody("""{"email":"a@b.com","password":"x"}""")
            }
        }

        assertEquals(HttpStatusCode.TooManyRequests, lastResponse.status)
        assertEquals("RATE_LIMITED", lastResponse.str("errorCode"))
    }

    /**
     * Health probe KHÔNG được dính rate limit.
     *
     * Nếu dính: lúc hệ thống quá tải, probe nhận 429 -> Docker tưởng app chết ->
     * restart container -> mất luôn phần dung lượng còn phục vụ được.
     * Một vòng lặp tự hủy rất dễ mắc phải.
     */
    @Test
    fun `health probe khong bi rate limit`() = testApplication {
        useRateLimitConfig()

        val statuses = (1..30).map { client.get("/health/live").status }

        assertTrue(
            statuses.all { it == HttpStatusCode.OK },
            "Health probe bi rate limit - rat nguy hiem o production!"
        )
    }
}
