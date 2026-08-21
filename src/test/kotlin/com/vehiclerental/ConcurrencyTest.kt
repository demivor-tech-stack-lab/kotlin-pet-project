package com.vehiclerental

import com.vehiclerental.config.TransactionRunner
import com.vehiclerental.db.table.Vehicles
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.testing.*
import org.koin.ktor.ext.getKoin
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * ĐÂY LÀ BỘ TEST QUAN TRỌNG NHẤT CỦA PHIÊN BẢN PRODUCTION.
 *
 * Nó kiểm chứng hai bảo đảm mà bản pet project KHÔNG có:
 *   1. Hai người đặt cùng một chiếc xe, cùng khoảng thời gian, cùng lúc
 *      -> chỉ đúng MỘT đơn được tạo.
 *   2. Một thao tác nghiệp vụ ghi nhiều bảng -> hoặc thành công hết,
 *      hoặc rollback sạch, không để lại trạng thái nửa vời.
 *
 * Loại lỗi này không bao giờ lộ ra khi bạn tự bấm thử bằng tay — phải có test
 * cố tình tạo ra tình huống đua thì mới thấy.
 */
class ConcurrencyTest {

    /**
     * CHỐNG ĐUA KHI ĐẶT XE.
     *
     * Bắn 8 request đặt CÙNG một xe, CÙNG một khoảng thời gian, đồng thời.
     * Trước khi sửa: nhiều request cùng vượt qua hasOverlap() -> nhiều đơn trùng lịch.
     * Sau khi sửa (transaction + SELECT ... FOR UPDATE): đúng 1 thành công, còn lại 409.
     */
    @Test
    fun `dat cung mot xe cung luc chi mot request thanh cong`() = testApplication {
        useTestConfig()
        val token = accessToken(CUSTOMER_EMAIL)
        val start = futureStart(500)
        val end = start.plusDays(2)
        val vehicleId = 4

        // Khởi động app trước khi bắn request đồng thời, để thời gian khởi tạo
        // không làm nhiễu kết quả.
        client.get("/health/live")

        val responses = coroutineScope {
            (1..CONCURRENT_REQUESTS).map {
                async {
                    client.post("/api/bookings") {
                        header(HttpHeaders.Authorization, "Bearer $token")
                        contentType(ContentType.Application.Json)
                        setBody("""{"vehicleId":$vehicleId,"startAt":"${iso(start)}","endAt":"${iso(end)}"}""")
                    }
                }
            }.awaitAll()
        }

        val created = responses.count { it.status == HttpStatusCode.Created }
        val conflicts = responses.count { it.status == HttpStatusCode.Conflict }
        val others = responses.filter {
            it.status != HttpStatusCode.Created && it.status != HttpStatusCode.Conflict
        }

        if (others.isNotEmpty()) {
            fail("Co response ngoai du kien: " + others.joinToString { it.status.toString() })
        }

        assertEquals(
            1, created,
            "Phai co DUNG 1 don duoc tao. Thuc te: $created don -> race condition van con!"
        )
        assertEquals(CONCURRENT_REQUESTS - 1, conflicts)

        // Kiểm chứng lại ở tầng dữ liệu, không chỉ tin vào HTTP status
        val list = client.get("/api/vehicles/$vehicleId/availability").bodyAsText()
        val occurrences = Regex("\"startAt\"").findAll(list).count()
        assertEquals(1, occurrences, "Database dang co $occurrences khoang dat trung nhau")
    }

    /**
     * TRANSACTION PHẢI ROLLBACK KHI CÓ LỖI.
     *
     * Đây là nền tảng cho lời hứa "confirm() là nguyên tử". Ta chứng minh trực tiếp:
     * ghi một thay đổi rồi cố tình ném exception trong cùng `dbQuery { }`,
     * sau đó kiểm tra thay đổi ấy KHÔNG còn dấu vết.
     *
     * Nếu ranh giới transaction bị đặt sai (mỗi câu lệnh một transaction như bản cũ),
     * test này sẽ đỏ.
     */
    @Test
    fun `loi giua chung thi toan bo transaction bi rollback`() = testApplication {
        useTestConfig()
        // Lấy chính TransactionRunner mà ứng dụng đang dùng, thay vì tự tạo kết nối
        // riêng — như vậy test mới kiểm tra đúng thứ chạy thật.
        lateinit var tx: TransactionRunner
        application { tx = getKoin().get<TransactionRunner>() }
        client.get("/health/live")   // kích hoạt khởi động app + DB

        val vehicleId = 2L
        val statusTruoc = client.get("/api/vehicles/$vehicleId").str("status")

        val exception = runCatching {
            tx.tx {
                Vehicles.update({ Vehicles.id eq vehicleId }) {
                    it[brand] = "DA-BI-SUA"
                }
                // Ghi thành công rồi mới nổ — đúng kịch bản "hỏng giữa chừng"
                throw IllegalStateException("Loi gia lap sau khi da ghi")
            }
        }.exceptionOrNull()

        assertTrue(exception is IllegalStateException, "Exception phai duoc nem ra ngoai")

        // Thay đổi ở trên PHẢI biến mất
        val brandSau = tx.tx {
            Vehicles.selectAll().where { Vehicles.id eq vehicleId }.single()[Vehicles.brand]
        }
        assertTrue(brandSau != "DA-BI-SUA", "Transaction KHONG rollback - du lieu da bi ghi de!")
        assertEquals(statusTruoc, client.get("/api/vehicles/$vehicleId").str("status"))
    }

    /**
     * Duyệt đơn ghi vào 2 bảng. Test này xác nhận sau khi duyệt, cả hai bảng
     * đều đã đổi — không có chuyện đơn CONFIRMED mà xe vẫn AVAILABLE.
     */
    @Test
    fun `duyet don cap nhat ca don lan xe trong cung mot transaction`() = testApplication {
        useTestConfig()
        val customerToken = accessToken(CUSTOMER_EMAIL)
        val adminToken = accessToken(ADMIN_EMAIL)
        val start = futureStart(600)
        val vehicleId = 1

        val created = client.post("/api/bookings") {
            header(HttpHeaders.Authorization, "Bearer $customerToken")
            contentType(ContentType.Application.Json)
            setBody("""{"vehicleId":$vehicleId,"startAt":"${iso(start)}","endAt":"${iso(start.plusDays(1))}"}""")
        }
        assertEquals(HttpStatusCode.Created, created.status)
        val bookingId = created.str("id")

        assertEquals("AVAILABLE", client.get("/api/vehicles/$vehicleId").str("status"))

        client.patch("/api/admin/bookings/$bookingId/confirm") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }

        // Doc lai TU DATABASE, khong tin vao response cua chinh lenh confirm
        val bookingStatus = client.get("/api/bookings/$bookingId") {
            header(HttpHeaders.Authorization, "Bearer $adminToken")
        }.str("status")
        val vehicleStatus = client.get("/api/vehicles/$vehicleId").str("status")

        assertEquals("CONFIRMED", bookingStatus)
        assertEquals("RENTED", vehicleStatus, "Don da CONFIRMED nhung xe chua RENTED - du lieu lech nhau!")
    }

    companion object {
        private const val CONCURRENT_REQUESTS = 8
    }
}
