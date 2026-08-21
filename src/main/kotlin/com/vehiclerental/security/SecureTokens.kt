package com.vehiclerental.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Sinh và băm REFRESH TOKEN.
 *
 * Khác với access token (JWT tự mang thông tin), refresh token ở đây chỉ là một
 * chuỗi ngẫu nhiên vô nghĩa — mọi thông tin đều tra từ database. Cách này tốt hơn
 * vì token trở thành một "chìa khóa" mà server có thể vứt đi bất cứ lúc nào.
 */
object SecureTokens {

    /**
     * SecureRandom chứ KHÔNG phải Random hay Math.random().
     * Random dùng thuật toán tuyến tính, đoán được các giá trị tiếp theo nếu biết
     * vài giá trị trước đó — dùng nó để sinh token là mở cửa cho kẻ tấn công.
     */
    private val random = SecureRandom()

    private const val TOKEN_BYTES = 32   // 256 bit, đủ để không ai dò trúng

    /** Sinh token mới, dạng Base64 URL-safe để nhét vào JSON/header thoải mái. */
    fun generate(): String {
        val bytes = ByteArray(TOKEN_BYTES)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /**
     * Băm token trước khi lưu DB.
     *
     * Vì sao dùng SHA-256 mà không dùng BCrypt như mật khẩu?
     *   - Mật khẩu do người đặt nên entropy thấp, dễ bị dò từ điển => cần BCrypt
     *     cố tình chạy chậm.
     *   - Token là 256 bit ngẫu nhiên, không thể dò được dù hàm băm chạy nhanh.
     *     Dùng BCrypt ở đây chỉ làm mỗi lần refresh chậm đi vô ích.
     */
    fun hash(token: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(token.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { byte -> "%02x".format(byte) }
    }
}
