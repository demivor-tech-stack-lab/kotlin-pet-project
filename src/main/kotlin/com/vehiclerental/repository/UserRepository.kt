package com.vehiclerental.repository

import com.vehiclerental.config.DatabaseFactory.dbQuery
import com.vehiclerental.db.table.Users
import com.vehiclerental.domain.model.User
import com.vehiclerental.domain.model.UserRole
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.time.LocalDateTime

/**
 * TẦNG REPOSITORY: chỉ làm việc với database, KHÔNG chứa logic nghiệp vụ.
 *
 * Khai báo `interface` trước rồi mới `class ...Impl` — vì:
 *   1. Service chỉ phụ thuộc vào interface -> khi viết test có thể thay bằng bản giả (fake).
 *   2. Đổi từ H2 sang PostgreSQL/MongoDB chỉ cần viết Impl mới.
 * Đây chính là Dependency Inversion trong SOLID.
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
}

class UserRepositoryImpl : UserRepository {

    override suspend fun findByEmail(email: String): User? = dbQuery {
        // selectAll().where { } là cú pháp DSL của Exposed, tương đương:
        // SELECT * FROM users WHERE email = ? LIMIT 1
        Users.selectAll()
            .where { Users.email eq email }
            .singleOrNull()      // 0 dòng -> null, >1 dòng -> ném lỗi
            ?.toUser()           // ?. = safe call: chỉ gọi toUser() khi khác null
    }

    override suspend fun findById(id: Long): User? = dbQuery {
        Users.selectAll()
            .where { Users.id eq id }
            .singleOrNull()
            ?.toUser()
    }

    override suspend fun create(
        email: String,
        passwordHash: String,
        fullName: String,
        phone: String,
        role: UserRole
    ): User = dbQuery {
        val now = LocalDateTime.now()

        // insert { } nhận một lambda; bên trong `it` là "builder" để gán giá trị cột
        val statement = Users.insert {
            it[Users.email] = email
            it[Users.passwordHash] = passwordHash
            it[Users.fullName] = fullName
            it[Users.phone] = phone
            it[Users.role] = role
            it[createdAt] = now      // trong lambda có thể bỏ tiền tố `Users.`
        }

        User(
            id = statement[Users.id],   // lấy id mà DB vừa tự sinh
            email = email,
            passwordHash = passwordHash,
            fullName = fullName,
            phone = phone,
            role = role,
            createdAt = now
        )
    }
}
