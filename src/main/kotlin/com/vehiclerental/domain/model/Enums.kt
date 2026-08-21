package com.vehiclerental.domain.model

/**
 * `enum class` trong Kotlin: tập giá trị cố định, an toàn hơn dùng String.
 * Exposed sẽ lưu xuống DB dưới dạng tên chuỗi ("ADMIN", "CUSTOMER"...).
 */
enum class UserRole {
    CUSTOMER,   // khách thuê xe
    ADMIN       // quản trị viên: thêm xe, duyệt đơn
}

enum class VehicleStatus {
    AVAILABLE,      // đang rảnh, có thể đặt
    RENTED,         // đang có người thuê
    MAINTENANCE     // đang bảo dưỡng, không cho đặt
}

/**
 * Vòng đời một đơn đặt xe:
 * PENDING --confirm--> CONFIRMED --complete--> COMPLETED
 *    |                     |
 *    +------ cancel -------+---> CANCELLED
 */
enum class BookingStatus {
    PENDING,
    CONFIRMED,
    COMPLETED,
    CANCELLED;

    /** Hàm tiện ích: đơn ở trạng thái này còn "chiếm chỗ" của xe hay không. */
    fun isActive(): Boolean = this == PENDING || this == CONFIRMED
}
