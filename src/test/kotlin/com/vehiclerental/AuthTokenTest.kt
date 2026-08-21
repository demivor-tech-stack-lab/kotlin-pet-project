package com.vehiclerental

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Kiểm chứng vòng đời token — phần bảo mật được thêm mới ở bản production.
 *
 * Ba lời hứa cần chứng minh:
 *   1. Refresh token đổi được sang access token mới.
 *   2. Refresh token bị XOAY VÒNG: dùng lại token cũ là thất bại.
 *   3. Logout và đổi mật khẩu THU HỒI được token — thứ mà JWT thuần không làm được.
 */
class AuthTokenTest {

    private suspend fun ApplicationTestBuilder.registerNew(email: String) =
        client.post("/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"matkhau123","fullName":"Token Test","phone":"0912345678"}""")
        }.jsonBody()

    @Test
    fun `refresh token doi duoc access token moi`() = testApplication {
        useTestConfig()
        val email = uniqueEmail("refresh")
        val refreshToken = registerNew(email)["refreshToken"]!!.jsonPrimitive.content

        val response = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }

        assertEquals(HttpStatusCode.OK, response.status)
        val body = response.jsonBody()
        assertTrue(body["accessToken"]!!.jsonPrimitive.content.isNotBlank())
        assertEquals(email, body["user"]!!.let { (it as kotlinx.serialization.json.JsonObject)["email"] }!!.jsonPrimitive.content)
    }

    /**
     * XOAY VÒNG TOKEN (rotation) — cơ chế phát hiện token bị đánh cắp.
     *
     * Mỗi lần refresh, token cũ bị thu hồi ngay. Nếu kẻ trộm dùng trước, lần refresh
     * tiếp theo của người dùng thật sẽ 401 — một tín hiệu rõ ràng để cảnh báo.
     */
    @Test
    fun `refresh token cu bi vo hieu sau khi da dung`() = testApplication {
        useTestConfig()
        val tokenCu = registerNew(uniqueEmail("rotate"))["refreshToken"]!!.jsonPrimitive.content

        val lanDau = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$tokenCu"}""")
        }
        assertEquals(HttpStatusCode.OK, lanDau.status)
        val tokenMoi = lanDau.jsonBody()["refreshToken"]!!.jsonPrimitive.content
        assertNotEquals(tokenCu, tokenMoi, "Refresh token phai duoc xoay vong, khong duoc tra lai token cu")

        // Dùng lại token cũ -> phải bị từ chối
        val dungLai = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$tokenCu"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, dungLai.status)
    }

    @Test
    fun `refresh token bia dat bi tu choi`() = testApplication {
        useTestConfig()
        val response = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"toi-tu-bia-ra-chuoi-nay"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `logout thu hoi refresh token`() = testApplication {
        useTestConfig()
        val refreshToken = registerNew(uniqueEmail("logout"))["refreshToken"]!!.jsonPrimitive.content

        val logout = client.post("/api/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(HttpStatusCode.OK, logout.status)

        val sauLogout = client.post("/api/auth/refresh") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"$refreshToken"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, sauLogout.status, "Refresh token van dung duoc sau khi logout!")
    }

    /** Logout với token không tồn tại vẫn trả 200 — không tiết lộ token nào có thật. */
    @Test
    fun `logout voi token khong ton tai van tra ve 200`() = testApplication {
        useTestConfig()
        val response = client.post("/api/auth/logout") {
            contentType(ContentType.Application.Json)
            setBody("""{"refreshToken":"khong-ton-tai"}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `doi mat khau thu hoi toan bo phien dang nhap`() = testApplication {
        useTestConfig()
        val email = uniqueEmail("changepw")
        val dangKy = registerNew(email)
        val accessToken = dangKy["accessToken"]!!.jsonPrimitive.content
        val refreshToken = dangKy["refreshToken"]!!.jsonPrimitive.content

        // Đăng nhập thêm ở "thiết bị thứ hai"
        val (_, refreshTokenThietBi2) = login(email, "matkhau123")

        val doiMatKhau = client.post("/api/auth/change-password") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"oldPassword":"matkhau123","newPassword":"matkhaumoi456"}""")
        }
        assertEquals(HttpStatusCode.OK, doiMatKhau.status)

        // CẢ HAI thiết bị đều bị đá ra
        listOf(refreshToken, refreshTokenThietBi2).forEach { token ->
            val response = client.post("/api/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refreshToken":"$token"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status, "Phien cu van song sau khi doi mat khau!")
        }

        // Mật khẩu mới dùng được, mật khẩu cũ thì không
        assertEquals(HttpStatusCode.OK, tryLogin(email, "matkhaumoi456"))
        assertEquals(HttpStatusCode.Unauthorized, tryLogin(email, "matkhau123"))
    }

    @Test
    fun `doi mat khau voi mat khau cu sai bi tu choi`() = testApplication {
        useTestConfig()
        val email = uniqueEmail("wrongold")
        val accessToken = registerNew(email)["accessToken"]!!.jsonPrimitive.content

        val response = client.post("/api/auth/change-password") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody("""{"oldPassword":"sai-mat-khau-cu","newPassword":"matkhaumoi456"}""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `logout tat ca thiet bi`() = testApplication {
        useTestConfig()
        val email = uniqueEmail("logoutall")
        val dangKy = registerNew(email)
        val accessToken = dangKy["accessToken"]!!.jsonPrimitive.content
        val refresh1 = dangKy["refreshToken"]!!.jsonPrimitive.content
        val (_, refresh2) = login(email, "matkhau123")

        val response = client.post("/api/auth/logout-all") {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        listOf(refresh1, refresh2).forEach { token ->
            val refresh = client.post("/api/auth/refresh") {
                contentType(ContentType.Application.Json)
                setBody("""{"refreshToken":"$token"}""")
            }
            assertEquals(HttpStatusCode.Unauthorized, refresh.status)
        }
    }

    private suspend fun ApplicationTestBuilder.tryLogin(email: String, password: String): HttpStatusCode =
        client.post("/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody("""{"email":"$email","password":"$password"}""")
        }.status
}
