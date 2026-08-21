package com.vehiclerental.dto

import kotlinx.serialization.Serializable

/**
 * Mọi lỗi trả về đều có CÙNG một hình dạng JSON:
 *   { "errorCode": "NOT_FOUND", "message": "...", "path": "/api/vehicles/99" }
 * Client chỉ cần viết code xử lý lỗi một lần.
 */
@Serializable
data class ErrorResponse(
    val errorCode: String,
    val message: String,
    val path: String? = null
)
