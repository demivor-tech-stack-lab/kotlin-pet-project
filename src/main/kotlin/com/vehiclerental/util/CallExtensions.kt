package com.vehiclerental.util

import com.vehiclerental.domain.exception.AppException
import io.ktor.server.application.*

/**
 * Đọc path parameter dạng số, ví dụ /api/vehicles/{id}
 * Nếu thiếu hoặc không phải số -> 400 chứ không phải 500.
 *
 * `?:` là ELVIS OPERATOR: "lấy vế trái, nếu null thì lấy vế phải".
 */
fun ApplicationCall.longParam(name: String): Long =
    parameters[name]?.toLongOrNull()
        ?: throw AppException.BadRequest("Tham số '$name' phải là số nguyên")

/** Đọc query parameter dạng số, có giá trị mặc định khi client không truyền. */
fun ApplicationCall.intQuery(name: String, default: Int): Int =
    request.queryParameters[name]?.toIntOrNull() ?: default

fun ApplicationCall.stringQuery(name: String): String? =
    request.queryParameters[name]?.takeIf { it.isNotBlank() }
