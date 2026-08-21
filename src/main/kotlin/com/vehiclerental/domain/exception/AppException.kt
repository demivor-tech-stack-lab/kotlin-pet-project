package com.vehiclerental.domain.exception

import io.ktor.http.*

/**
 * Cây exception nghiệp vụ của ứng dụng.
 *
 * `sealed class` = lớp niêm phong: chỉ các lớp con khai báo trong CÙNG module
 * mới được kế thừa. Nhờ vậy `when` trên nó có thể exhaustive (không cần `else`).
 *
 * Mỗi exception tự mang theo HTTP status của nó -> StatusPages chỉ việc đọc ra,
 * service layer không cần biết gì về HTTP.
 */
sealed class AppException(
    val status: HttpStatusCode,
    val errorCode: String,
    override val message: String
) : RuntimeException(message) {

    /** 400 - dữ liệu client gửi lên sai định dạng / vi phạm ràng buộc nghiệp vụ. */
    class BadRequest(message: String, errorCode: String = "BAD_REQUEST") :
        AppException(HttpStatusCode.BadRequest, errorCode, message)

    /** 401 - chưa đăng nhập hoặc token sai/hết hạn. */
    class Unauthorized(message: String = "Bạn cần đăng nhập") :
        AppException(HttpStatusCode.Unauthorized, "UNAUTHORIZED", message)

    /** 403 - đã đăng nhập nhưng không đủ quyền. */
    class Forbidden(message: String = "Bạn không có quyền thực hiện thao tác này") :
        AppException(HttpStatusCode.Forbidden, "FORBIDDEN", message)

    /** 404 - không tìm thấy tài nguyên. */
    class NotFound(resource: String, id: Any) :
        AppException(HttpStatusCode.NotFound, "NOT_FOUND", "Không tìm thấy $resource với id = $id")

    /** 409 - xung đột trạng thái (email đã tồn tại, xe đã có người đặt trùng lịch...). */
    class Conflict(message: String, errorCode: String = "CONFLICT") :
        AppException(HttpStatusCode.Conflict, errorCode, message)
}
