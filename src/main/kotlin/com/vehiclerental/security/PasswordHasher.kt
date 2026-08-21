package com.vehiclerental.security

import at.favre.lib.crypto.bcrypt.BCrypt

/**
 * BCrypt: hàm băm mật khẩu MỘT CHIỀU (không giải ngược được).
 * Mỗi lần hash sinh ra "salt" ngẫu nhiên khác nhau, nên hash cùng 1 mật khẩu
 * hai lần cho ra 2 chuỗi khác nhau -> bắt buộc phải dùng verify() để so sánh,
 * KHÔNG được so sánh chuỗi bằng ==.
 */
object PasswordHasher {

    private const val COST = 12   // càng cao càng chậm (an toàn hơn). 10-12 là hợp lý.

    fun hash(rawPassword: String): String =
        BCrypt.withDefaults().hashToString(COST, rawPassword.toCharArray())

    fun verify(rawPassword: String, hash: String): Boolean =
        BCrypt.verifyer().verify(rawPassword.toCharArray(), hash).verified
}
