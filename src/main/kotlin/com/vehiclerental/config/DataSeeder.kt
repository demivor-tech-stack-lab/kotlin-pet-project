package com.vehiclerental.config

import com.vehiclerental.config.DatabaseFactory.dbQuery
import com.vehiclerental.db.table.VehicleTypes
import com.vehiclerental.db.table.Vehicles
import com.vehiclerental.domain.model.UserRole
import com.vehiclerental.domain.model.VehicleStatus
import com.vehiclerental.repository.UserRepository
import com.vehiclerental.repository.VehicleRepository
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import java.math.BigDecimal

/**
 * Nạp dữ liệu mẫu khi khởi động để bạn có cái mà nghịch ngay.
 *
 * Hàm này PHẢI an toàn khi chạy nhiều lần (idempotent): kiểm tra "đã có chưa"
 * trước khi thêm. Với H2 in-memory thì DB mất khi tắt app nên lần nào cũng nạp lại.
 */
class DataSeeder(
    private val userRepository: UserRepository,
    private val vehicleRepository: VehicleRepository
) {
    private val logger = LoggerFactory.getLogger(DataSeeder::class.java)

    suspend fun seed() {
        val alreadySeeded = dbQuery { VehicleTypes.selectAll().empty().not() }
        if (alreadySeeded) {
            logger.info("Da co du lieu mau, bo qua buoc seed")
            return
        }

        // ----- Tài khoản mẫu -----
        if (userRepository.findByEmail(ADMIN_EMAIL) == null) {
            userRepository.create(
                email = ADMIN_EMAIL,
                passwordHash = com.vehiclerental.security.PasswordHasher.hash(DEFAULT_PASSWORD),
                fullName = "Quan tri vien",
                phone = "0900000001",
                role = UserRole.ADMIN
            )
        }
        if (userRepository.findByEmail(CUSTOMER_EMAIL) == null) {
            userRepository.create(
                email = CUSTOMER_EMAIL,
                passwordHash = com.vehiclerental.security.PasswordHasher.hash(DEFAULT_PASSWORD),
                fullName = "Nguyen Van Khach",
                phone = "0900000002",
                role = UserRole.CUSTOMER
            )
        }

        // ----- Loại xe -----
        val typeIds = dbQuery {
            listOf(
                Triple("Xe may", 2, "Xe so hoac tay ga"),
                Triple("O to 4 cho", 4, "Sedan/Hatchback"),
                Triple("O to 7 cho", 7, "SUV/MPV gia dinh")
            ).map { (name, seats, desc) ->
                VehicleTypes.insert {
                    it[VehicleTypes.name] = name
                    it[VehicleTypes.seats] = seats
                    it[description] = desc
                }[VehicleTypes.id]
            }
        }

        // ----- Xe -----
        val samples = listOf(
            VehicleSeed("59X1-12345", "Honda", "Vision", 2022, typeIds[0], "150000"),
            VehicleSeed("59X1-67890", "Yamaha", "Janus", 2021, typeIds[0], "140000"),
            VehicleSeed("51A-11111", "Toyota", "Vios", 2023, typeIds[1], "700000"),
            VehicleSeed("51A-22222", "Hyundai", "Accent", 2022, typeIds[1], "680000"),
            VehicleSeed("51B-33333", "Mitsubishi", "Xpander", 2023, typeIds[2], "950000"),
            VehicleSeed("51B-44444", "Toyota", "Innova", 2021, typeIds[2], "900000")
        )

        dbQuery {
            samples.forEach { sample ->
                Vehicles.insert {
                    it[plateNumber] = sample.plate
                    it[brand] = sample.brand
                    it[model] = sample.model
                    it[year] = sample.year
                    it[typeId] = sample.typeId
                    it[pricePerDay] = BigDecimal(sample.price)
                    it[status] = VehicleStatus.AVAILABLE
                }
            }
        }

        logger.info("Seed xong: {} loai xe, {} xe, 2 tai khoan mau", typeIds.size, samples.size)
    }

    /** data class private dùng nội bộ cho gọn - không cần đưa ra ngoài. */
    private data class VehicleSeed(
        val plate: String,
        val brand: String,
        val model: String,
        val year: Int,
        val typeId: Long,
        val price: String
    )

    companion object {
        const val ADMIN_EMAIL = "admin@vehiclerental.com"
        const val CUSTOMER_EMAIL = "khach@vehiclerental.com"
        const val DEFAULT_PASSWORD = "123456"
    }
}
