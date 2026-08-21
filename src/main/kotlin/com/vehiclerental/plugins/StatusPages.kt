package com.vehiclerental.plugins

import com.vehiclerental.config.AppConfig
import com.vehiclerental.domain.exception.AppException
import com.vehiclerental.dto.ErrorResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.*
import io.ktor.server.plugins.callid.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import kotlinx.serialization.SerializationException
import org.slf4j.LoggerFactory

/**
 * BỘ XỬ LÝ LỖI TẬP TRUNG — một trong những plugin đáng giá nhất của Ktor.
 *
 * Nhờ nó, service chỉ cần `throw AppException.NotFound("xe", 9)` là xong;
 * không cần try/catch rải rác trong từng route, cũng không cần route nào
 * cũng phải nhớ định dạng JSON lỗi cho giống nhau.
 */
fun Application.configureStatusPages(appConfig: AppConfig) {
    val logger = LoggerFactory.getLogger("ErrorHandler")

    install(StatusPages) {

        /**
         * exception<T> { call, cause -> } : bắt mọi exception kiểu T (và lớp con của T).
         * AppException là sealed class nên nhánh này gom hết mọi lỗi nghiệp vụ.
         */
        exception<AppException> { call, cause ->
            // Lỗi nghiệp vụ là chuyện bình thường (nhập sai, hết chỗ...), log mức DEBUG
            // để không làm nhiễu log production. Riêng 5xx thì mới đáng báo động.
            logger.debug("Loi nghiep vu {}: {}", cause.errorCode, cause.message)
            call.respond(
                cause.status,
                ErrorResponse(cause.errorCode, cause.message, call.request.local.uri)
            )
        }

        /** JSON gửi lên sai cú pháp / thiếu field bắt buộc -> Ktor ném BadRequestException. */
        exception<BadRequestException> { call, cause ->
            logger.warn("Body khong hop le: {}", cause.message)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse(
                    errorCode = "INVALID_BODY",
                    // Ở production KHÔNG trả chi tiết lỗi parse: nó tiết lộ tên field
                    // và cấu trúc nội bộ. Ở dev thì trả để bạn debug nhanh.
                    message = if (appConfig.environment.isProd) {
                        "Dữ liệu gửi lên không hợp lệ"
                    } else {
                        "Dữ liệu gửi lên không hợp lệ: " + (cause.cause?.message ?: cause.message ?: "")
                    },
                    path = call.request.local.uri
                )
            )
        }

        exception<SerializationException> { call, cause ->
            logger.warn("Khong parse duoc JSON: {}", cause.message)
            call.respond(
                HttpStatusCode.BadRequest,
                ErrorResponse("INVALID_JSON", "JSON không đúng định dạng", call.request.local.uri)
            )
        }

        /**
         * Lưới an toàn cuối cùng.
         *
         * Ghi log kèm requestId để người vận hành lần ra đúng request bị lỗi,
         * và trả CHÍNH requestId đó cho client. Khi khách báo lỗi kèm mã này,
         * bạn grep một phát là ra toàn bộ stacktrace — thay vì đoán mò.
         *
         * KHÔNG trả stacktrace về client: nó lộ tên thư viện, phiên bản,
         * đường dẫn file trên máy chủ — món quà cho kẻ tấn công.
         */
        exception<Throwable> { call, cause ->
            val requestId = call.callId ?: "-"
            logger.error("Loi khong luong truoc tai {} [requestId={}]", call.request.local.uri, requestId, cause)
            call.respond(
                HttpStatusCode.InternalServerError,
                ErrorResponse(
                    errorCode = "INTERNAL_ERROR",
                    message = "Có lỗi xảy ra phía máy chủ. Vui lòng cung cấp mã sự cố $requestId khi báo lỗi.",
                    path = call.request.local.uri
                )
            )
        }

        /** Vượt quá giới hạn tần suất — trả JSON thay vì body rỗng. */
        status(HttpStatusCode.TooManyRequests) { call, _ ->
            respondRateLimited(call)
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
