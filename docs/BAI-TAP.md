# 🛠️ 15 Bài Tập Code Thật

Mỗi bài có: **mục tiêu học** → **yêu cầu** → **gợi ý** → **cách tự kiểm tra**.
Làm theo thứ tự. Sau mỗi bài chạy `gradlew.bat test` — **phải xanh** mới đi tiếp.

> Quy tắc vàng khi làm bài: **thêm test trước hoặc ngay sau khi code**.
> Bài nào cũng yêu cầu viết ít nhất 1 test — đó chính là thứ giúp bạn "sửa mà không sợ hỏng".

---

## 🟢 Mức 1 — Làm quen (bài 1–5)

### Bài 1 — Thêm field vào response
**Học:** dữ liệu chảy qua 4 hình dạng (DB → domain → DTO → JSON).

**Yêu cầu:** thêm `seats` (số chỗ ngồi) vào `VehicleResponse`, lấy từ bảng `vehicle_types` qua JOIN.

**Gợi ý:** sửa theo đúng chuỗi `Tables.kt` (đã có sẵn `seats`) → `Models.kt` (thêm `typeSeats: Int?`)
→ `Mappers.kt` (`getOrNull(VehicleTypes.seats)`) → `VehicleDto.kt` → `toResponse()`.

**Kiểm tra:** `GET /api/vehicles/3` trả về `"seats": 4`.

---

### Bài 2 — Thêm endpoint đọc đơn giản
**Học:** đi hết một lát cắt dọc route → service → repository.

**Yêu cầu:** `GET /api/vehicle-types/{id}` trả về chi tiết một loại xe, 404 nếu không có.

**Gợi ý:** thêm `findTypeById` vào `VehicleRepository` (cả interface lẫn Impl),
`getTypeById` vào `VehicleService` (ném `AppException.NotFound`), rồi route trong `VehicleRoutes.kt`.

**Kiểm tra:** viết 2 test — id hợp lệ trả 200, id 999 trả 404 với `errorCode = "NOT_FOUND"`.

---

### Bài 3 — Thêm bộ lọc mới
**Học:** cách ghép điều kiện động trong Exposed.

**Yêu cầu:** thêm query param `minPricePerDay` cho `GET /api/vehicles`
(dùng chung được với `maxPricePerDay`).

**Gợi ý:** thêm field vào `VehicleFilter`, một dòng `?.let { query.andWhere { ... greaterEq it } }`
trong `baseQuery()`, và đọc param trong route.

**Kiểm tra:** `?minPricePerDay=500000&maxPricePerDay=800000` chỉ trả xe trong khoảng đó.

---

### Bài 4 — Sửa quy tắc nghiệp vụ
**Học:** phân biệt "sửa chính sách" và "sửa logic".

**Yêu cầu:** đổi tiền cọc từ 30% thành 20%, và thời gian thuê tối đa từ 30 lên 45 ngày.

**Gợi ý:** đúng **2 dòng**, ở 2 file khác nhau. Nếu bạn phải sửa nhiều hơn 2 chỗ
thì bạn đang sửa sai chỗ.

**Kiểm tra:** `PricingPolicyTest` sẽ đỏ → sửa lại kỳ vọng trong test cho khớp.
Suy nghĩ: vì sao test đỏ lại là chuyện **tốt** ở đây?

---

### Bài 5 — Đổi thông tin cá nhân
**Học:** endpoint ghi dữ liệu + quyền "chỉ sửa của mình".

**Yêu cầu:** `PATCH /api/auth/me` cho phép user đổi `fullName` và `phone` (không đổi email, không đổi role).

**Gợi ý:** thêm `update` vào `UserRepository` dùng `Users.update({ Users.id eq id }) { ... }`.
Validate số điện thoại bằng `PHONE_REGEX` có sẵn — tách nó ra thành hàm dùng chung.

**Kiểm tra:** đổi xong gọi `GET /api/auth/me` thấy dữ liệu mới; gọi khi chưa đăng nhập → 401.

---

## 🔵 Mức 2 — Nghiệp vụ thật (bài 6–10)

### Bài 6 — Sắp xếp linh hoạt
**Yêu cầu:** thêm `sortBy` (`price` | `year` | `id`) và `sortDir` (`asc` | `desc`) cho `GET /api/vehicles`.

**Gợi ý:** map chuỗi sang `Column<*>` bằng `when`. **Tuyệt đối không** nối chuỗi tên cột
vào SQL — hãy nghĩ xem vì sao (SQL injection).
Giá trị không hợp lệ → `AppException.BadRequest`, không được im lặng bỏ qua.

---

### Bài 7 — Lịch bận của xe
**Yêu cầu:** `GET /api/vehicles/{id}/availability?from=...&to=...` trả về danh sách khoảng
thời gian đã bị đặt (chỉ `PENDING` + `CONFIRMED`). Endpoint **công khai**.

**Gợi ý:** dùng lại tư duy của `hasOverlap()` nhưng trả về danh sách thay vì boolean.
Đây là bài quan trọng — nó là thứ mà giao diện đặt xe thật nào cũng cần.

---

### Bài 8 — Không cho khách hủy sát giờ
**Yêu cầu:** chỉ cho hủy nếu còn **ít nhất 24 giờ** trước `startAt`. ADMIN thì vẫn hủy được bất kỳ lúc nào.

**Gợi ý:** sửa `BookingService.cancel()`. Hằng số đặt trong `companion object`.
Chú ý: `requester.role == UserRole.ADMIN` là điều kiện miễn trừ.

**Kiểm tra:** test cần tạo đơn có `startAt` cách hiện tại 2 giờ → khách hủy phải nhận 409.

---

### Bài 9 — Thống kê cho admin
**Yêu cầu:** `GET /api/admin/stats` trả về: tổng số xe, số xe theo từng trạng thái,
tổng số đơn theo từng trạng thái, tổng doanh thu từ đơn `COMPLETED`.

**Gợi ý:** dùng aggregate của Exposed:
```kotlin
val count = Vehicles.status.count()
Vehicles.select(Vehicles.status, count).groupBy(Vehicles.status).forEach { row -> ... }
```
Với doanh thu dùng `Bookings.totalPrice.sum()`. Nhớ: `sum()` trả về `null` khi không có dòng nào.

---

### Bài 10 — Đổi mật khẩu
**Yêu cầu:** `POST /api/auth/change-password` với body `{oldPassword, newPassword}`.
Sai mật khẩu cũ → 401. Mật khẩu mới trùng mật khẩu cũ → 400.

**Gợi ý:** `PasswordHasher.verify()` rồi `PasswordHasher.hash()`.
**Câu hỏi suy nghĩ:** token cũ có nên còn hiệu lực sau khi đổi mật khẩu không?
Với JWT thuần thì bạn **không** vô hiệu hóa được token cũ — vì sao?

---

## 🟣 Mức 3 — Nâng cao (bài 11–15)

### Bài 11 — Unit test không cần database
**Học:** đây là lý do tồn tại của `interface UserRepository`.

**Yêu cầu:** viết `AuthServiceTest` dùng `FakeUserRepository` (một class trong thư mục test,
lưu dữ liệu trong `mutableMapOf`), **không** khởi động server, **không** đụng DB.
Test: đăng ký thành công, email trùng → `Conflict`, mật khẩu ngắn → `BadRequest`.

**Gợi ý:**
```kotlin
class FakeUserRepository : UserRepository {
    private val users = mutableMapOf<String, User>()
    private var nextId = 1L
    override suspend fun findByEmail(email: String) = users[email]
    // ...
}
```
Test hàm `suspend` thì bọc trong `runBlocking { }`.

---

### Bài 12 — Sửa lỗi mất nhất quán dữ liệu ⚠️
**Học:** transaction — kiến thức bắt buộc để làm task thật.

**Yêu cầu:** hiện tại `BookingService.confirm()` gọi 2 lần update trong **2 transaction riêng**.
Nếu cái thứ hai lỗi thì đơn đã CONFIRMED nhưng xe vẫn AVAILABLE. Hãy gói cả hai vào **một** transaction.

**Gợi ý:** thêm vào mỗi repository một phiên bản **không tự mở transaction**
(ví dụ `updateStatusInTx`) rồi ở service gọi:
```kotlin
DatabaseFactory.dbQuery {
    bookingRepository.updateStatusInTx(...)
    vehicleRepository.updateStatusInTx(...)
}
```
Áp dụng tương tự cho `complete()` và `cancel()`.

**Câu hỏi:** vì sao **không** thể chỉ bọc `dbQuery { }` quanh code cũ mà xong?
(gợi ý: `newSuspendedTransaction` lồng nhau xử lý thế nào?)

---

### Bài 13 — Chuyển sang PostgreSQL + Flyway
**Yêu cầu:** thay H2 bằng PostgreSQL, thay `SchemaUtils.create()` bằng migration Flyway.

**Gợi ý:** cần cài Docker Desktop. Viết `docker-compose.yml`, thêm dependency
`org.postgresql:postgresql` và `org.flywaydb:flyway-core`, viết
`src/main/resources/db/migration/V1__init.sql`.

**Nhận xét quan trọng:** khi làm xong, đếm xem bạn phải sửa bao nhiêu file **trong package
`service`**. Con số đó (0) chính là phần thưởng cho việc phân tầng.

---

### Bài 14 — Refresh token
**Yêu cầu:** access token sống 15 phút, refresh token sống 7 ngày, lưu trong bảng `refresh_tokens`.
Thêm `POST /api/auth/refresh` và `POST /api/auth/logout` (xóa refresh token).

**Gợi ý:** đây là bài đầu tiên bạn phải **tự thiết kế bảng mới** từ đầu:
`Tables.kt` → `Models.kt` → repository → service → route → test. Đi đúng thứ tự đó.

---

### Bài 15 — Hợp đồng, thanh toán và phí phạt
**Yêu cầu:** hiện thực nốt phần nghiệp vụ mà guide gốc đề ra: bảng `contracts`, `payments`, `penalties`.
Khi `complete()` một đơn, tính phí trả muộn bằng `PricingPolicy.calculateLateFee()`
(hàm đã viết sẵn nhưng **chưa ai gọi**) và tạo bản ghi thanh toán cuối.

**Gợi ý:** `complete()` cần nhận thêm `actualReturnAt`. Suy nghĩ: nên đặt nó vào body request
hay lấy `LocalDateTime.now()`? (gợi ý: cái nào test được?)

---

## 📌 Checklist trước khi coi một bài là "xong"

- [ ] `gradlew.bat build` xanh (biên dịch + toàn bộ test).
- [ ] Có ít nhất **1 test mới** cho phần vừa viết, và ít nhất **1 test cho trường hợp lỗi**.
- [ ] Đã thử endpoint mới bằng `api.http`.
- [ ] Code đặt **đúng tầng** (route không chứa logic, service không viết SQL).
- [ ] Lỗi trả về dùng `AppException`, không dùng `call.respond(HttpStatusCode.BadRequest, "...")` thô.
- [ ] Đã cập nhật bảng API trong `README.md` và thêm ví dụ vào `api.http`.
