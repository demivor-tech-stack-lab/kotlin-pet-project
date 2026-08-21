package com.vehiclerental.di

import com.vehiclerental.config.AppConfig
import com.vehiclerental.config.DataSeeder
import com.vehiclerental.config.TransactionRunner
import com.vehiclerental.repository.BookingRepository
import com.vehiclerental.repository.BookingRepositoryImpl
import com.vehiclerental.repository.RefreshTokenRepository
import com.vehiclerental.repository.RefreshTokenRepositoryImpl
import com.vehiclerental.repository.UserRepository
import com.vehiclerental.repository.UserRepositoryImpl
import com.vehiclerental.repository.VehicleRepository
import com.vehiclerental.repository.VehicleRepositoryImpl
import com.vehiclerental.security.JwtService
import com.vehiclerental.service.AuthService
import com.vehiclerental.service.BookingService
import com.vehiclerental.service.VehicleService
import com.vehiclerental.util.SystemTimeProvider
import com.vehiclerental.util.TimeProvider
import org.koin.dsl.module

/**
 * DEPENDENCY INJECTION bằng Koin.
 *
 * Không có DI, bạn sẽ phải tự tay viết trong Application.kt:
 *     val timeProvider = SystemTimeProvider(zone)
 *     val userRepo = UserRepositoryImpl(timeProvider)
 *     val jwt = JwtService(config.jwt, timeProvider)
 *     val authService = AuthService(userRepo, refreshRepo, jwt, config.jwt, timeProvider)
 *     ...
 * Càng nhiều lớp thì đoạn "lắp ráp" đó càng rối. Koin làm hộ việc đó.
 *
 * - single { }        : tạo MỘT thể hiện duy nhất, dùng lại cho cả vòng đời app (singleton).
 * - single<A> { B() } : "khi ai cần A, hãy đưa cho họ B" -> đây chính là chỗ ta gắn
 *                       interface với implementation. Đổi sang implementation khác
 *                       chỉ cần sửa đúng một dòng ở đây.
 * - get()             : "lấy giúp tôi thứ có kiểu này" - Koin suy ra kiểu từ constructor.
 */
fun appModule(appConfig: AppConfig, transactionRunner: TransactionRunner) = module {

    // ----- Cấu hình -----
    // Đăng ký chính AppConfig để các thành phần khác inject được, thay vì
    // đọc lại System.getenv() rải rác khắp nơi.
    single { appConfig }
    single { appConfig.jwt }

    // ----- Hạ tầng -----
    // Ranh giới transaction được tiêm vào service như một dependency bình thường,
    // thay vì gọi hàm static toàn cục. Xem TransactionRunner để biết vì sao.
    single { transactionRunner }
    // TimeProvider được đăng ký như một dependency bình thường, nhờ vậy test
    // có thể thay bằng đồng hồ đứng yên mà không đụng tới code nghiệp vụ.
    single<TimeProvider> { SystemTimeProvider(appConfig.timezone) }
    single { JwtService(appConfig.jwt, get()) }

    // ----- Repository (interface -> implementation) -----
    single<UserRepository> { UserRepositoryImpl(get()) }
    single<VehicleRepository> { VehicleRepositoryImpl() }
    single<BookingRepository> { BookingRepositoryImpl(get()) }
    single<RefreshTokenRepository> { RefreshTokenRepositoryImpl(get()) }

    // ----- Service -----
    // Mỗi get() được Koin suy ra từ KIỂU của tham số trong constructor.
    single { AuthService(get(), get(), get(), get(), get(), get()) }
    single { VehicleService(get(), get(), get(), get()) }
    single { BookingService(get(), get(), get(), get()) }

    // ----- Tiện ích khởi động -----
    single { DataSeeder(get(), get(), get(), get()) }
}
