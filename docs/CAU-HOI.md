# 📝 60 Câu Hỏi Kiểm Tra Hiểu Dự Án

Cách dùng:
- Trả lời bằng lời của bạn, **không nhìn đáp án trước**.
- Với câu có nhắc tên file → mở file đó ra đọc rồi trả lời.
- Trả lời xong một cấp độ mới mở `DAP-AN.md` đối chiếu.
- Chấm điểm: đúng ≥ 80% một cấp độ thì đi tiếp; dưới 60% thì đọc lại code phần đó.

**Mốc đánh giá:** trả lời được cấp 1–3 = đủ sửa bug và thêm endpoint đơn giản.
Cấp 4–5 = đủ nhận task trung bình và tự tin không làm hỏng hệ thống.

---

## 🟢 CẤP 1 — Kotlin cơ bản (câu 1–14)

1. Trong `domain/model/Models.kt`, tất cả thuộc tính đều khai báo bằng `val`. `val` khác `var` chỗ nào, và vì sao ở đây lại chọn `val`?

2. `data class User(...)` — khi viết `data`, trình biên dịch Kotlin tự sinh thêm những hàm nào cho bạn? Kể ít nhất 4.

3. Trong `VehicleType`, thuộc tính `description: String?` có dấu `?`, còn `name: String` thì không. Sự khác nhau thực tế là gì? Nếu tôi viết `vehicleType.description.length` thì chuyện gì xảy ra?

4. Giải thích 3 toán tử null-safety này và cho ví dụ trong dự án: `?.` , `?:` , `!!` .

5. Trong `util/CallExtensions.kt`:
   ```kotlin
   fun ApplicationCall.longParam(name: String): Long =
       parameters[name]?.toLongOrNull() ?: throw AppException.BadRequest(...)
   ```
   Đây là loại hàm gì? Nó khác gì với việc viết `fun longParam(call: ApplicationCall, name: String)`?

6. `object PasswordHasher { ... }` dùng `object` chứ không phải `class`. `object` nghĩa là gì? Khi nào nên dùng `object` thay cho `class`?

7. Trong `security/JwtService.kt` có `companion object { const val CLAIM_USER_ID = "userId" }`. `companion object` để làm gì? Gọi hằng số đó từ file khác như thế nào?

8. `const val` khác `val` thường ở điểm nào? Vì sao `EMAIL_REGEX` trong `AuthService` là `private val` mà không phải `const val`?

9. Trong `BookingService.cancel()` có khối `when (booking.status) { ... }`. `when` khác `switch` của Java ở những điểm nào? Vì sao ở đây **không cần** nhánh `else`?

10. Nhánh `BookingStatus.PENDING, BookingStatus.CONFIRMED -> Unit` — `Unit` ở đây nghĩa là gì?

11. Trong `VehicleService.list()`:
    ```kotlin
    val (items, total) = vehicleRepository.findAll(...)
    ```
    Cú pháp này gọi là gì? Nó hoạt động được nhờ đâu?

12. `items to total` trong `VehicleRepositoryImpl.findAll()` tạo ra cái gì?

13. Trong `DataSeeder`, `listOf(Triple("Xe may", 2, "..."))` rồi `.map { (name, seats, desc) -> ... }`. Giải thích `Triple` và cách destructuring trong lambda.

14. Trong lambda `Vehicles.insert { it[brand] = ... }`, biến `it` là gì? Khi nào Kotlin cho phép dùng `it`, khi nào phải đặt tên tham số?

---

## 🔵 CẤP 2 — Kiến trúc & vai trò từng tầng (câu 15–28)

15. Dự án có 3 tầng chính: `routes` → `service` → `repository`. Mô tả **trách nhiệm** và **điều cấm kỵ** của từng tầng.

16. Vì sao lại có **cả** `domain/model/Models.kt` (class `User`) **và** `dto/AuthDto.kt` (class `UserResponse`)? Chúng gần giống nhau — sao không dùng chung một class cho gọn?

17. Chỉ ra chính xác dòng code nào đảm bảo `passwordHash` không bao giờ bị trả về cho client.

18. `UserRepository` là `interface`, `UserRepositoryImpl` là `class` implement nó. Lợi ích cụ thể của việc tách đôi này là gì? Nêu 2 lợi ích.

19. Trong `di/AppModule.kt` có `single<UserRepository> { UserRepositoryImpl() }`. Giải thích dòng này bằng lời thường. Nếu mai tôi viết `PostgresUserRepository` thì phải sửa bao nhiêu file?

20. `single { AuthService(get(), get()) }` — hai cái `get()` này trả về gì? Koin làm sao biết được?

21. `AuthService` nhận `UserRepository` qua constructor thay vì tự `val repo = UserRepositoryImpl()` bên trong. Kiểu này gọi là gì, và nó giúp việc viết test dễ hơn ra sao?

22. Trong `Application.module()`, vì sao `install(Koin)` **bắt buộc** phải đứng trước `configureSecurity()` và `configureRouting()`?

23. `DatabaseFactory.init(config)` được gọi trước `install(Koin)`. Nếu đảo ngược thứ tự thì có sao không? Vì sao?

24. `AppException` là `sealed class`. `sealed` khác `abstract` ở chỗ nào? Lợi ích của nó trong dự án này là gì?

25. Mỗi `AppException` mang theo `status: HttpStatusCode`. Nhưng `service` thì "không được biết về HTTP" — có mâu thuẫn không? Bạn nghĩ sao về đánh đổi này?

26. Trong `plugins/StatusPages.kt` có 3 handler `exception<...>`. Nếu `AuthService` ném `AppException.NotFound`, handler nào bắt được? Vì sao không phải handler `Throwable`?

27. Handler `exception<Throwable>` ghi log stacktrace nhưng chỉ trả về `"Có lỗi xảy ra phía máy chủ"`. Vì sao không trả stacktrace về cho client?

28. `PricingPolicy` được tách riêng khỏi `BookingService`. Lý do kỹ thuật là gì? (gợi ý: nhìn file `PricingPolicyTest.kt`)

---

## 🟣 CẤP 3 — Ktor & luồng request (câu 29–40)

29. Kể lại theo thứ tự: từ lúc gõ `gradlew run` đến lúc hàm `Application.module()` được gọi, đã xảy ra những gì?

30. Trong `application.yaml` có `- com.vehiclerental.ApplicationKt.module`. Tại sao lại là `ApplicationKt` mà không phải `Application`? Nếu đổi tên file `Application.kt` thành `Main.kt` thì phải sửa gì?

31. Plugin `ContentNegotiation` làm gì? Nếu gỡ nó ra thì dòng `call.receive<RegisterRequest>()` sẽ ra sao?

32. `@Serializable` trên `data class RegisterRequest` có tác dụng gì? Nếu quên annotation này thì lỗi xuất hiện lúc **biên dịch** hay lúc **chạy**?

33. `util/Serializers.kt` định nghĩa `LocalDateTimeSerializer`. Vì sao `LocalDateTime` cần serializer tự viết trong khi `String` và `Int` thì không?

34. Trong DTO, `pricePerDay` được truyền dưới dạng **chuỗi** `"600000"` chứ không phải số `600000`. Lý do là gì?

35. `authenticate("auth-jwt") { ... }` — chuỗi `"auth-jwt"` này khớp với cái gì ở đâu? Nếu gõ nhầm thành `"auth-jwtt"` thì lỗi xảy ra lúc nào?

36. Trong `plugins/Security.kt`, khối `validate { credential -> ... }` chạy **trước** hay **sau** khi Ktor kiểm tra chữ ký token? Trả về `null` trong khối này thì client nhận status gì?

37. Phân biệt rõ `401 Unauthorized` và `403 Forbidden` trong dự án này. Hàm nào ném cái nào?

38. `call.requireUser()` lấy `userId` từ đâu — từ database hay từ token? Ưu và nhược điểm của cách đó?

39. Trong `BookingRoutes.kt`, route `/my` và route `/{id}` cùng nằm dưới `/api/bookings`. Nếu gọi `GET /api/bookings/my`, Ktor chọn route nào và vì sao?

40. `Route.authRoutes(authService: AuthService)` nhận service qua tham số, còn `Application.configureRouting()` thì dùng `by inject()`. Vì sao lại làm hai kiểu khác nhau?

---

## 🟠 CẤP 4 — Database, Exposed & Coroutines (câu 41–52)

41. Trong `db/table/Tables.kt`, mỗi bảng là `object ... : Table("users")`. Vì sao dùng `object` chứ không phải `class`?

42. `val email = varchar("email", 255).uniqueIndex()` — `uniqueIndex()` tạo ra cái gì ở tầng database? Nhưng `AuthService.register()` vẫn kiểm tra email trùng bằng code. Có thừa không?

43. Giải thích `enumerationByName("status", 20, BookingStatus::class)`. Nếu mai tôi đổi tên `PENDING` thành `WAITING` trong enum thì dữ liệu cũ trong DB sẽ ra sao?

44. Vì sao `pricePerDay` dùng `decimal(12, 2)` mà không dùng `double`? Cho một ví dụ số cụ thể chứng minh `double` sai.

45. Trong `DatabaseFactory` có:
    ```kotlin
    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }
    ```
    Giải thích: `<T>` là gì, `block: suspend () -> T` là gì, và `Dispatchers.IO` để làm gì?

46. `suspend fun` khác `fun` thường ở điểm nào? Vì sao gần như mọi hàm trong `repository` đều là `suspend`?

47. Điều gì xảy ra nếu ta bỏ `newSuspendedTransaction` và gọi thẳng JDBC trong route handler? (gợi ý: nghĩ tới số lượng thread của Netty)

48. Trong `VehicleRepositoryImpl.baseQuery()`, các điều kiện lọc được ghép bằng `filter.status?.let { query.andWhere { ... } }`. Giải thích cách hoạt động và vì sao nó cho phép lọc "động".

49. `Vehicles.join(VehicleTypes, JoinType.LEFT, Vehicles.typeId, VehicleTypes.id)` — vì sao dùng JOIN thay vì query riêng tên loại xe cho từng chiếc? Vấn đề "N+1 query" là gì?

50. Trong `Mappers.kt`, `typeName` dùng `getOrNull(VehicleTypes.name)` còn các cột khác dùng `this[...]`. Vì sao khác nhau?

51. Đọc `BookingRepositoryImpl.hasOverlap()`. Viết ra công thức toán học của việc "hai khoảng thời gian giao nhau" và giải thích vì sao chỉ cần 2 điều kiện.

52. `hasOverlap()` chỉ tính đơn `PENDING` và `CONFIRMED`. Vì sao không tính `CANCELLED` và `COMPLETED`?

---

## 🔴 CẤP 5 — Nghiệp vụ, gỡ lỗi & mở rộng (câu 53–60)

53. Vẽ lại state machine của `BookingStatus`. Từ `COMPLETED` có thể về `PENDING` không? Code nào chặn điều đó?

54. Khi admin gọi `confirm`, có **2** thay đổi dữ liệu xảy ra. Đó là gì? Nếu thay đổi thứ nhất thành công mà thứ hai thất bại thì hệ thống rơi vào trạng thái nào? (đây là một bug thật — bạn sửa thế nào?)

55. `BookingService.create()` có comment nói còn **race condition**. Mô tả kịch bản cụ thể 2 người đặt cùng lúc dẫn tới trùng lịch. Nêu 2 hướng khắc phục.

56. `PricingPolicy.calculateDays()` làm tròn LÊN. Thuê từ 8:00 ngày 1 đến 9:00 ngày 2 thì tính mấy ngày, tốn bao nhiêu tiền nếu giá 700.000/ngày?

57. Trong `AuthService.login()`, sai email và sai mật khẩu đều trả về **cùng một** thông báo. Vì sao cố tình làm vậy?

58. **Tình huống:** khách báo "tôi đặt xe mà bị 409 dù xe đang AVAILABLE". Liệt kê thứ tự các bước bạn sẽ kiểm tra để tìm nguyên nhân, kèm tên file cần mở.

59. **Tình huống:** sếp yêu cầu thêm endpoint `GET /api/vehicles/{id}/bookings` (xem lịch bận của một xe, chỉ admin). Liệt kê **từng file** bạn phải sửa/tạo và **sửa gì** trong đó — theo đúng kiến trúc hiện tại.

60. **Tình huống:** cần thêm trường `licenseNumber` (số bằng lái) vào user, bắt buộc khi đăng ký. Liệt kê tất cả file phải sửa và giải thích vì sao **không thể** chỉ sửa mỗi `Models.kt`.

---

## 🎯 Tự chấm

| Cấp độ | Số câu | Bạn đúng |
|---|---|---|
| 1 — Kotlin cơ bản | 14 | ___ |
| 2 — Kiến trúc | 14 | ___ |
| 3 — Ktor | 12 | ___ |
| 4 — DB & Coroutines | 12 | ___ |
| 5 — Nghiệp vụ & gỡ lỗi | 8 | ___ |
| **Tổng** | **60** | ___ |

- **< 30**: đọc lại README mục 2–3, làm bài tập 1–5 trước.
- **30–44**: nền tốt. Tập trung vào cấp 4 (Exposed/coroutines) — đây là chỗ hay gây bug thật.
- **45–54**: đủ sức nhận task trung bình.
- **≥ 55**: hiểu dự án tốt. Chuyển sang `BAI-TAP.md` bài 10–15.
