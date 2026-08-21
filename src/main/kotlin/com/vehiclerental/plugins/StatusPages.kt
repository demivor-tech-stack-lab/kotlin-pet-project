package com.vehiclerental.plugins

import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

/**
 * BỘ XỬ LÝ LỖI TẬP TRUNG - một trong những plugin đáng giá nhất của Ktor.
 *
 * Nhờ nó, service chỉ cần `throw AppException.NotFound("xe", 9)` là xong;
 * không cần try/catch rải rác trong từng route, cũng không cần route nào
 * cũng phải nhớ định dạng JSON lỗi cho giống nhau.
 */
fun Application.configureStatusPages() {
    val logger = LoggerFactory.getLogger("ErrorHandler")

    install(StatusPages) {

        /**
         * exception<T> { call, cause -> } : bắt mọi exception kiểu T (và lớp con của T).
         * AppException là sealed class nên nhánh này gom hết mọi lỗi nghiệp vụ.
         */
        exception<AppException> { call, cause ->
            call.respond(
                cause.status,
                ErrorResponse(cause.errorCode, cause.message, call.request.local.uri)
            )
        }

        /** JSON gửi lên sai cú pháp / thiếu field bắt buộc -> Ktor ném BadRequestException. */
        exception<BadRequestException> { call, cause ->
            logger.warn("Body không hợp lệ: {}", cause.message)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    errorCode = "INVALID_BODY",
                    message = "Dữ liệu gửi lên không hợp lệ: " + (cause.cause?.message ?: cause.message ?: ""),
                    path = call.request.local.uri
                )
            )
        }

        /**
         * Lưới an toàn cuối cùng. Ghi log đầy đủ stacktrace cho lập trình viên,
         * nhưng KHÔNG trả stacktrace về client (lộ cấu trúc hệ thống -> rủi ro bảo mật).
         */
        exception<Throwable> { call, cause ->
            logger.error("Lỗi không lường trước tại ${call.request.local.uri}", cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse("INTERNAL_ERROR", "Có lỗi xảy ra phía máy chủ", call.request.local.uri)
            )
        }

        /** Đường dẫn không tồn tại -> trả JSON thay vì trang HTML mặc định. */
        status(HttpStatusCode.NotFound) { call, status ->
            call.respond(
                status,
                ErrorResponse("ROUTE_NOT_FOUND", "Không có endpoint ${call.request.local.uri}")
            )
        }
    }
}
