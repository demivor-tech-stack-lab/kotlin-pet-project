package com.vehiclerental.repository

import com.vehiclerental.db.table.Users
import com.vehiclerental.domain.model.User
import com.vehiclerental.domain.model.UserRole
import com.vehiclerental.util.TimeProvider
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update

/**
 * TẦNG REPOSITORY: chỉ làm việc với database, KHÔNG chứa logic nghiệp vụ.
 *
 * ⚠️ HỢP ĐỒNG QUAN TRỌNG (đổi so với bản pet project):
 * Các hàm ở đây **giả định đã có một transaction đang mở** và KHÔNG tự mở transaction.
 * Tầng service chịu trách nhiệm bọc `DatabaseFactory.dbQuery { }` quanh một use case.
 *
 * Vì sao đổi? Trước đây mỗi hàm repository là một transaction riêng, nên một thao tác
 * nghiệp vụ ghi vào 2 bảng sẽ chạy trong 2 transaction — hỏng giữa chừng là dữ liệu
 * mâu thuẫn. Giờ một use case = một transaction, hoặc thành công trọn vẹn, hoặc rollback sạch.
 *
 * Gọi nhầm ngoài transaction thì Exposed ném lỗi ngay, không âm thầm sai.
 */
interface UserRepository {
    suspend fun findByEmail(email: String): User?
    suspend fun findById(id: Long): User?
    suspend fun create(
        email: String,
        passwordHash: String,
        fullName: String,
        phone: String,
        role: UserRole
    ): User
    suspend fun updatePasswordHash(id: Long, passwordHash: String): Boolean
}

class UserRepositoryImpl(
    private val timeProvider: TimeProvider
) : UserRepository {

    override suspend fun findByEmail(email: String): User? =
        Users.selectAll()
            .where { Users.email eq email }
            .singleOrNull()
            ?.toUser()

    override suspend fun findById(id: Long): User? =
        Users.selectAll()
            .where { Users.id eq id }
            .singleOrNull()
            ?.toUser()

    override suspend fun create(
        email: String,
        passwordHash: String,
        fullName: String,
        phone: String,
        role: UserRole
    ): User {
        val now = timeProvider.now()

        val statement = Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
            it[Users.fullName] = fullName
            it[Users.phone] = phone
            it[Users.role] = role
            it[createdAt] = now
        }

        return User(
            id = statement[Users.id],
            email = email,
            passwordHash = passwordHash,
            fullName = fullName,
            phone = phone,
            role = role,
            createdAt = now
        )
    }

    override suspend fun updatePasswordHash(id: Long, passwordHash: String): Boolean =
        Users.update({ Users.id eq id }) {
            it[Users.passwordHash] = passwordHash
        } > 0
}
