package com.vehiclerental.repository

import com.vehiclerental.db.table.RefreshTokens
import com.vehiclerental.domain.model.RefreshToken
import com.vehiclerental.util.TimeProvider
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.time.LocalDateTime

/** ⚠️ Mọi hàm ở đây phải được gọi bên trong transaction do service mở. */
interface RefreshTokenRepository {
    suspend fun create(
        userId: Long,
        tokenHash: String,
        expiresAt: LocalDateTime,
        userAgent: String?,
        ipAddress: String?
    ): RefreshToken

    suspend fun findByHash(tokenHash: String): RefreshToken?
    suspend fun revoke(id: Long): Boolean
    suspend fun revokeAllForUser(userId: Long): Int
    suspend fun deleteExpiredBefore(cutoff: LocalDateTime): Int
}

class RefreshTokenRepositoryImpl(
    private val timeProvider: TimeProvider
) : RefreshTokenRepository {

    override suspend fun create(
        userId: Long,
        tokenHash: String,
        expiresAt: LocalDateTime,
        userAgent: String?,
        ipAddress: String?
    ): RefreshToken {
        val now = timeProvider.now()
        val statement = RefreshTokens.insert {
            it[RefreshTokens.userId] = userId
            it[RefreshTokens.tokenHash] = tokenHash
            it[RefreshTokens.expiresAt] = expiresAt
            it[createdAt] = now
            // Cắt bớt user-agent quá dài để không vỡ giới hạn cột.
            it[RefreshTokens.userAgent] = userAgent?.take(255)
            it[RefreshTokens.ipAddress] = ipAddress?.take(64)
        }

        return RefreshToken(
            id = statement[RefreshTokens.id],
            userId = userId,
            tokenHash = tokenHash,
            expiresAt = expiresAt,
            revokedAt = null,
            createdAt = now,
            userAgent = userAgent,
            ipAddress = ipAddress
        )
    }

    override suspend fun findByHash(tokenHash: String): RefreshToken? =
        RefreshTokens.selectAll()
            .where { RefreshTokens.tokenHash eq tokenHash }
            .singleOrNull()
            ?.toRefreshToken()

    /**
     * Đánh dấu thu hồi thay vì xóa hẳn: giữ lại dấu vết để điều tra sự cố
     * ("token này bị thu hồi lúc nào, từ IP nào").
     * Điều kiện `revokedAt is null` giúp hàm này idempotent.
     */
    override suspend fun revoke(id: Long): Boolean =
        RefreshTokens.update({ (RefreshTokens.id eq id) and RefreshTokens.revokedAt.isNull() }) {
            it[revokedAt] = timeProvider.now()
        } > 0

    /** Dùng khi logout mọi thiết bị, hoặc khi đổi mật khẩu. */
    override suspend fun revokeAllForUser(userId: Long): Int =
        RefreshTokens.update({ (RefreshTokens.userId eq userId) and RefreshTokens.revokedAt.isNull() }) {
            it[revokedAt] = timeProvider.now()
        }

    /**
     * Dọn token đã quá hạn — nếu không, bảng này chỉ có phình to mãi.
     * Được gọi định kỳ bởi job dọn dẹp lúc chạy nền.
     */
    override suspend fun deleteExpiredBefore(cutoff: LocalDateTime): Int =
        RefreshTokens.deleteWhere { RefreshTokens.expiresAt less cutoff }
}
