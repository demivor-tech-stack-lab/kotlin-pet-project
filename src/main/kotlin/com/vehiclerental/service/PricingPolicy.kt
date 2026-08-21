package com.vehiclerental.service

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Duration
import java.time.LocalDateTime
import kotlin.math.ceil

/**
 * Tách toàn bộ CÔNG THỨC TÍNH TIỀN ra một object riêng.
 *
 * Vì sao tách?
 *   - Đây là phần dễ thay đổi nhất (khuyến mãi, giá cuối tuần, phụ thu...).
 *   - Nó là hàm thuần (pure function): cùng input luôn cho cùng output,
 *     không đụng DB, không đụng mạng -> viết unit test cực dễ, chạy trong mili giây.
 */
object PricingPolicy {

    /** Tiền cọc = 30% tổng tiền thuê. */
    private val DEPOSIT_RATE = BigDecimal("0.30")

    /** Phí phạt mỗi ngày trả xe muộn = 150% giá thuê một ngày. */
    private val LATE_FEE_RATE = BigDecimal("1.50")

    /**
     * Quy đổi khoảng thời gian thuê ra SỐ NGÀY TÍNH TIỀN.
     * Quy tắc: làm tròn LÊN, tối thiểu 1 ngày.
     * Ví dụ: thuê 25 tiếng -> tính 2 ngày. Thuê 3 tiếng -> tính 1 ngày.
     */
    fun calculateDays(startAt: LocalDateTime, endAt: LocalDateTime): Int {
        val hours = Duration.between(startAt, endAt).toMinutes() / 60.0
        return ceil(hours / 24.0).toInt().coerceAtLeast(1)
    }

    /**
     * setScale(2, HALF_UP): luôn giữ 2 chữ số thập phân, làm tròn theo kiểu
     * quen thuộc (0.5 làm tròn lên). Bắt buộc phải chốt scale khi tính tiền.
     */
    fun calculateTotal(pricePerDay: BigDecimal, days: Int): BigDecimal =
        pricePerDay.multiply(BigDecimal(days)).setScale(2, RoundingMode.HALF_UP)

    fun calculateDeposit(totalPrice: BigDecimal): BigDecimal =
        totalPrice.multiply(DEPOSIT_RATE).setScale(2, RoundingMode.HALF_UP)

    /**
     * Tính phí phạt khi trả xe muộn.
     * Trả về BigDecimal.ZERO nếu trả đúng hạn hoặc sớm.
     */
    fun calculateLateFee(
        pricePerDay: BigDecimal,
        plannedEndAt: LocalDateTime,
        actualReturnAt: LocalDateTime
    ): BigDecimal {
        if (!actualReturnAt.isAfter(plannedEndAt)) return BigDecimal.ZERO

        val lateDays = calculateDays(plannedEndAt, actualReturnAt)
        return pricePerDay
            .multiply(LATE_FEE_RATE)
            .multiply(BigDecimal(lateDays))
            .setScale(2, RoundingMode.HALF_UP)
    }
}
