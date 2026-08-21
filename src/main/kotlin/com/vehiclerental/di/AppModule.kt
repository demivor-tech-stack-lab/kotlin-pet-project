package com.vehiclerental.di

import com.vehiclerental.config.DataSeeder
import com.vehiclerental.repository.BookingRepository
import com.vehiclerental.repository.BookingRepositoryImpl
import com.vehiclerental.repository.UserRepository
import com.vehiclerental.repository.UserRepositoryImpl
import com.vehiclerental.repository.VehicleRepository
import com.vehiclerental.repository.VehicleRepositoryImpl
import com.vehiclerental.security.JwtService
import com.vehiclerental.service.AuthService
import com.vehiclerental.service.BookingService
import com.vehiclerental.service.VehicleService
import io.ktor.server.config.*
import org.koin.dsl.module

/**
 * DEPENDENCY INJECTION bằng Koin.
 *
 * Không có DI, bạn sẽ phải tự tay viết trong Application.kt:
 *     val userRepo = UserRepositoryImpl()
 *     val jwt = JwtService(config)
 *     val authService = AuthService(userRepo, jwt)
 *     ...
 * Càng nhiều lớp thì đoạn "lắp ráp" đó càng rối. Koin làm hộ việc đó.
 *
 * - single { }        : tạo MỘT thể hiện duy nhất, dùng lại cho cả vòng đời app (singleton).
 * - single<A> { B() } : "khi ai cần A, hãy đưa cho họ B" -> đây chính là chỗ ta gắn
 *                       interface với implementation. Đổi sang PostgresUserRepository
 *                       chỉ cần sửa đúng một dòng ở đây.
 * - get()             : "lấy giúp tôi thứ có kiểu này" - Koin suy ra kiểu từ constructor.
 */
fun appModule(config: ApplicationConfig) = module {

    // ----- Hạ tầng -----
    single { JwtService(config) }

    // ----- Repository (interface -> implementation) -----
    single<UserRepository> { UserRepositoryImpl() }
    single<VehicleRepository> { VehicleRepositoryImpl() }
    single<BookingRepository> { BookingRepositoryImpl() }

    // ----- Service -----
    // AuthService(userRepository, jwtService): get() thứ nhất suy ra UserRepository,
    // get() thứ hai suy ra JwtService - dựa vào kiểu tham số của constructor.
    single { AuthService(get(), get()) }
    single { VehicleService(get()) }
    single { BookingService(get(), get()) }

    // ----- Tiện ích khởi động -----
    single { DataSeeder(get(), get()) }
}
