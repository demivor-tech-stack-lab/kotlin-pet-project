package com.vehiclerental.util

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * NGUỒN THỜI GIAN DUY NHẤT CỦA ỨNG DỤNG.
 *
 * Trước đây service gọi thẳng `LocalDateTime.now()`. Cách đó có 2 vấn đề ở production:
 *
 *  1. **Phụ thuộc múi giờ máy chủ.** Container Docker mặc định chạy UTC, máy dev chạy
 *     giờ Việt Nam. Cùng một dòng code cho ra hai kết quả lệch 7 tiếng — nghĩa là
 *     quy tắc "không cho đặt xe trong quá khứ" hoạt động khác nhau ở hai nơi.
 *
 *  2. **Không test được.** Muốn kiểm tra "đơn hết hạn sau 30 ngày" thì phải chờ 30 ngày,
 *     hoặc phải viết test phụ thuộc vào đồng hồ thật (dễ đỏ lúc nửa đêm, lúc đổi giờ mùa).
 *
 * Giải pháp: bọc đồng hồ sau một interface rồi tiêm vào qua Koin.
 * Production dùng [SystemTimeProvider], test dùng [FixedTimeProvider] đứng yên tại
 * một mốc chọn trước.
 */
interface TimeProvider {

    /** Giờ "treo tường" theo múi giờ nghiệp vụ — dùng cho giờ nhận/trả xe. */
    fun now(): LocalDateTime

    /** Mốc thời gian tuyệt đối — dùng cho hạn token, thời điểm ghi log. */
    fun instant(): Instant

    /** Múi giờ nghiệp vụ đang áp dụng. */
    val zone: ZoneId
}

/** Bản dùng thật: lấy giờ hệ thống rồi quy về múi giờ nghiệp vụ đã cấu hình. */
class SystemTimeProvider(override val zone: ZoneId) : TimeProvider {
    private val clock: Clock = Clock.system(zone)
    override fun now(): LocalDateTime = LocalDateTime.now(clock)
    override fun instant(): Instant = clock.instant()
}

/**
 * Bản dùng trong test: đồng hồ đứng yên, hoặc tua tới bằng [advanceSeconds].
 * Nhờ nó mà test "refresh token hết hạn" chạy trong mili giây thay vì 30 ngày.
 */
class FixedTimeProvider(
    private var current: Instant,
    override val zone: ZoneId
) : TimeProvider {
    override fun now(): LocalDateTime = LocalDateTime.ofInstant(current, zone)
    override fun instant(): Instant = current

    fun advanceSeconds(seconds: Long) {
        current = current.plusSeconds(seconds)
    }
}
