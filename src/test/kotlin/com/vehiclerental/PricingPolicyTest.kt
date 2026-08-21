package com.vehiclerental

import com.vehiclerental.service.PricingPolicy
import java.math.BigDecimal
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * UNIT TEST cho PricingPolicy.
 *
 * Không cần khởi động server, không cần database -> chạy trong vài mili giây.
 * Đây là lý do ta tách công thức tính tiền ra thành object thuần túy:
 * phần dễ sai nhất lại là phần dễ test nhất.
 */
class PricingPolicyTest {

    private val base = LocalDateTime.of(2026, 9, 1, 8, 0)

    @Test
    fun `thue dung 1 ngay tinh 1 ngay`() {
        assertEquals(1, PricingPolicy.calculateDays(base, base.plusDays(1)))
    }

    @Test
    fun `thue 25 tieng phai lam tron len 2 ngay`() {
        assertEquals(2, PricingPolicy.calculateDays(base, base.plusHours(25)))
    }

    @Test
    fun `thue duoi 24 tieng van tinh toi thieu 1 ngay`() {
        assertEquals(1, PricingPolicy.calculateDays(base, base.plusHours(3)))
    }

    @Test
    fun `tong tien bang gia ngay nhan so ngay`() {
        val total = PricingPolicy.calculateTotal(BigDecimal("700000"), 3)
        // compareTo == 0 thay vì assertEquals: BigDecimal("2100000") khác
        // BigDecimal("2100000.00") khi so bằng equals() vì lệch scale.
        assertTrue(total.compareTo(BigDecimal("2100000.00")) == 0, "Thuc te: $total")
    }

    @Test
    fun `tien coc bang 30 phan tram tong tien`() {
        val deposit = PricingPolicy.calculateDeposit(BigDecimal("2100000.00"))
        assertTrue(deposit.compareTo(BigDecimal("630000.00")) == 0, "Thuc te: $deposit")
    }

    @Test
    fun `tra xe dung han thi khong bi phat`() {
        val fee = PricingPolicy.calculateLateFee(BigDecimal("700000"), base, base)
        assertTrue(fee.compareTo(BigDecimal.ZERO) == 0)
    }

    @Test
    fun `tra xe muon 1 ngay bi phat 150 phan tram gia ngay`() {
        val fee = PricingPolicy.calculateLateFee(BigDecimal("700000"), base, base.plusHours(5))
        assertTrue(fee.compareTo(BigDecimal("1050000.00")) == 0, "Thuc te: $fee")
    }
}
