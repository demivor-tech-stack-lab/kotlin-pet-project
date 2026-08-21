package com.vehiclerental.config

import org.jetbrains.exposed.sql.Transaction

/**
 * RANH GIỚI TRANSACTION, được tiêm vào tầng service.
 *
 * Bản pet project gọi `DatabaseFactory.dbQuery { }` như một hàm static toàn cục.
 * Cách đó chạy được nhưng có hai vấn đề thật:
 *
 *  1. **Trạng thái toàn cục ngầm.** Exposed ghi nhớ database "hiện tại" theo
 *     thread-local. Khi trong một tiến trình có nhiều kết nối lần lượt được tạo
 *     rồi đóng (đúng cảnh bộ test khởi động ứng dụng nhiều lần), một thread có thể
 *     còn giữ tham chiếu tới DataSource đã đóng và ném lỗi
 *     "HikariDataSource has been closed". Chỉ định database TƯỜNG MINH thì hết lỗi này.
 *
 *  2. **Không thay thế được khi test.** Service phụ thuộc cứng vào một object
 *     toàn cục thì không thể viết unit test cho nó mà không dựng cả database.
 *
 * Giải pháp là chính nguyên tắc đã áp dụng cho repository: phụ thuộc vào
 * INTERFACE, nhận qua constructor.
 */
interface TransactionRunner {

    /**
     * Chạy [block] trong MỘT transaction duy nhất.
     *
     * Ném exception ở bất kỳ đâu trong block => toàn bộ thay đổi bị rollback.
     * Đây chính là thứ bảo đảm "duyệt đơn thì cả bảng bookings lẫn bảng vehicles
     * cùng đổi, hoặc không đổi gì cả".
     */
    suspend fun <T> tx(block: suspend Transaction.() -> T): T
}
