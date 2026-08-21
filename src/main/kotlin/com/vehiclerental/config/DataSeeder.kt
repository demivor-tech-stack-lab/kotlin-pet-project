package com.vehiclerental.config

import com.vehiclerental.db.table.Vehicles
import com.vehiclerental.domain.model.UserRole
import com.vehiclerental.domain.model.VehicleStatus
import com.vehiclerental.repository.UserRepository
import com.vehiclerental.repository.VehicleRepository
import com.vehiclerental.security.PasswordHasher
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import org.slf4j.LoggerFactory
import java.math.BigDecimal

/**
 * Nạp DỮ LIỆU MẪU để bạn có cái mà nghịch ngay khi chạy dev.
 *
 * ⚠️ KHÔNG BAO GIỜ chạy ở production: nó tạo tài khoản admin với mật khẩu ai cũng
 * đoán được. [AppConfig] sẽ CHẶN KHỞI ĐỘNG nếu phát hiện APP_ENV=prod mà
 * SEED_DATA=true — thà không deploy được còn hơn deploy kèm cửa hậu.
 *
 * Phân biệt với dữ liệu THAM CHIẾU (danh sách loại xe): thứ đó production cũng cần,
 * nên nằm trong migration V2__reference_data.sql chứ không nằm ở đây.
 */
class DataSeeder(
    private val tx: TransactionRunner,
    private val userRepository: UserRepository,
    private val vehicleRepository: VehicleRepository,
    private val appConfig: AppConfig
) {
    private val logger = LoggerFactory.getLogger(DataSeeder::class.java)

    suspend fun seed() {
        // Lớp bảo vệ thứ hai, phòng khi ai đó gọi thẳng hàm này.
        if (appConfig.environment.isProd) {
            logger.error("Tu choi seed du lieu mau o moi truong production")
            return
        }

        tx.tx {
            val alreadySeeded = Vehicles.selectAll().empty().not()
            if (alreadySeeded) {
                logger.info("Da co du lieu mau, bo qua buoc seed")
                return@tx
            }

            seedUsers()
            seedVehicles()
        }
    }

    private suspend fun seedUsers() {
        if (userRepository.findByEmail(ADMIN_EMAIL) == null) {
            userRepository.create(
                email = ADMIN_EMAIL,
                passwordHash = PasswordHasher.hash(DEFAULT_PASSWORD),
                fullName = "Quan tri vien",
                phone = "0900000001",
                role = UserRole.ADMIN
            )
        }
        if (userRepository.findByEmail(CUSTOMER_EMAIL) == null) {
            userRepository.create(
                email = CUSTOMER_EMAIL,
                passwordHash = PasswordHasher.hash(DEFAULT_PASSWORD),
                fullName = "Nguyen Van Khach",
                phone = "0900000002",
                role = UserRole.CUSTOMER
            )
        }
    }

    /**
     * Loại xe đã được migration V2 tạo sẵn, nên ở đây chỉ TRA CỨU theo tên.
     * Không hardcode id 1/2/3: id do database sinh, phụ thuộc thứ tự chạy migration.
     */
    private suspend fun seedVehicles() {
        val motorbike = vehicleRepository.findTypeByName("Xe may")
        val car4 = vehicleRepository.findTypeByName("O to 4 cho")
        val car7 = vehicleRepository.findTypeByName("O to 7 cho")

        if (motorbike == null || car4 == null || car7 == null) {
            logger.warn("Chua co du lieu loai xe, bo qua seed xe. Kiem tra migration V2.")
            return
        }

        val samples = listOf(
            VehicleSeed("59X1-12345", "Honda", "Vision", 2022, motorbike.id, "150000"),
            VehicleSeed("59X1-67890", "Yamaha", "Janus", 2021, motorbike.id, "140000"),
            VehicleSeed("51A-11111", "Toyota", "Vios", 2023, car4.id, "700000"),
            VehicleSeed("51A-22222", "Hyundai", "Accent", 2022, car4.id, "680000"),
            VehicleSeed("51B-33333", "Mitsubishi", "Xpander", 2023, car7.id, "950000"),
            VehicleSeed("51B-44444", "Toyota", "Innova", 2021, car7.id, "900000")
        )

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

        logger.info("Seed xong: {} xe mau va 2 tai khoan demo", samples.size)
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

        /** Đủ 8 ký tự để qua được validate mới của AuthService. */
        const val DEFAULT_PASSWORD = "matkhau123"
    }
}
