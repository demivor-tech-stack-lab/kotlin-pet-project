package com.vehiclerental.repository

import com.vehiclerental.config.DatabaseFactory.dbQuery
import com.vehiclerental.db.table.VehicleTypes
import com.vehiclerental.db.table.Vehicles
import com.vehiclerental.domain.model.Vehicle
import com.vehiclerental.domain.model.VehicleStatus
import com.vehiclerental.domain.model.VehicleType
import org.jetbrains.exposed.sql.JoinType
import org.jetbrains.exposed.sql.Query
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.andWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.lowerCase
import org.jetbrains.exposed.sql.or
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.update
import java.math.BigDecimal

/**
 * Gói các điều kiện lọc lại thành một data class thay vì truyền 4 tham số rời rạc.
 * Tất cả đều nullable + có default -> gọi kiểu VehicleFilter(status = AVAILABLE) là đủ.
 */
data class VehicleFilter(
    val typeId: Long? = null,
    val status: VehicleStatus? = null,
    val keyword: String? = null,
    val maxPricePerDay: BigDecimal? = null
)

interface VehicleRepository {
    suspend fun findAll(filter: VehicleFilter, page: Int, size: Int): Pair<List<Vehicle>, Long>
    suspend fun findById(id: Long): Vehicle?
    suspend fun existsByPlateNumber(plateNumber: String): Boolean
    suspend fun create(
        plateNumber: String,
        brand: String,
        model: String,
        year: Int,
        typeId: Long,
        pricePerDay: BigDecimal
    ): Vehicle
    suspend fun updateStatus(id: Long, status: VehicleStatus): Boolean
    suspend fun findAllTypes(): List<VehicleType>
    suspend fun typeExists(typeId: Long): Boolean
}

class VehicleRepositoryImpl : VehicleRepository {

    /**
     * private fun dùng lại cho cả findAll lẫn việc đếm tổng số bản ghi.
     * LEFT JOIN để lấy kèm tên loại xe (typeName) chỉ trong 1 câu SQL,
     * tránh lỗi kinh điển N+1 query (query lặp lại trong vòng lặp).
     */
    private fun baseQuery(filter: VehicleFilter): Query {
        val query = Vehicles
            .join(VehicleTypes, JoinType.LEFT, Vehicles.typeId, VehicleTypes.id)
            .selectAll()

        // ?.let { } : chỉ chạy block khi giá trị khác null -> ghép điều kiện động
        filter.typeId?.let { value -> query.andWhere { Vehicles.typeId eq value } }
        filter.status?.let { value -> query.andWhere { Vehicles.status eq value } }
        filter.maxPricePerDay?.let { value -> query.andWhere { Vehicles.pricePerDay lessEq value } }
        filter.keyword?.let { kw ->
            val pattern = "%" + kw.lowercase() + "%"
            query.andWhere {
                (Vehicles.brand.lowerCase() like pattern)
                    .or(Vehicles.model.lowerCase() like pattern)
                    .or(Vehicles.plateNumber.lowerCase() like pattern)
            }
        }
        return query
    }

    /**
     * Trả về Pair(danh sách của trang hiện tại, tổng số bản ghi khớp bộ lọc).
     * Pair là kiểu có sẵn của Kotlin; lấy ra bằng .first / .second
     * hoặc destructuring: val (items, total) = repo.findAll(...)
     */
    override suspend fun findAll(filter: VehicleFilter, page: Int, size: Int): Pair<List<Vehicle>, Long> = dbQuery {
        val total = baseQuery(filter).count()

        val items = baseQuery(filter)
            .orderBy(Vehicles.id to SortOrder.ASC)
            .limit(size, offset = (page - 1).toLong() * size)
            .map { it.toVehicle() }

        items to total   // "a to b" tạo ra Pair(a, b)
    }

    override suspend fun findById(id: Long): Vehicle? = dbQuery {
        Vehicles
            .join(VehicleTypes, JoinType.LEFT, Vehicles.typeId, VehicleTypes.id)
            .selectAll()
            .where { Vehicles.id eq id }
            .singleOrNull()
            ?.toVehicle()
    }

    override suspend fun existsByPlateNumber(plateNumber: String): Boolean = dbQuery {
        Vehicles.selectAll()
            .where { Vehicles.plateNumber eq plateNumber }
            .empty()
            .not()
    }

    override suspend fun create(
        plateNumber: String,
        brand: String,
        model: String,
        year: Int,
        typeId: Long,
        pricePerDay: BigDecimal
    ): Vehicle = dbQuery {
        val statement = Vehicles.insert {
            it[Vehicles.plateNumber] = plateNumber
            it[Vehicles.brand] = brand
            it[Vehicles.model] = model
            it[Vehicles.year] = year
            it[Vehicles.typeId] = typeId
            it[Vehicles.pricePerDay] = pricePerDay
            it[status] = VehicleStatus.AVAILABLE
        }
        val newId = statement[Vehicles.id]

        val typeName = VehicleTypes.selectAll()
            .where { VehicleTypes.id eq typeId }
            .singleOrNull()
            ?.get(VehicleTypes.name)

        Vehicle(newId, plateNumber, brand, model, year, typeId, typeName, pricePerDay, VehicleStatus.AVAILABLE)
    }

    /** update() trả về SỐ DÒNG bị ảnh hưởng -> lớn hơn 0 nghĩa là có cập nhật thật. */
    override suspend fun updateStatus(id: Long, status: VehicleStatus): Boolean = dbQuery {
        Vehicles.update({ Vehicles.id eq id }) {
            it[Vehicles.status] = status
        } > 0
    }

    override suspend fun findAllTypes(): List<VehicleType> = dbQuery {
        VehicleTypes.selectAll()
            .orderBy(VehicleTypes.id to SortOrder.ASC)
            .map { it.toVehicleType() }
    }

    override suspend fun typeExists(typeId: Long): Boolean = dbQuery {
        VehicleTypes.selectAll().where { VehicleTypes.id eq typeId }.empty().not()
    }
}
