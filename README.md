# 🚗 Vehicle Rental API — Pet Project Kotlin

API cho thuê xe, viết bằng **Kotlin + Ktor + Exposed + Koin + JWT**.
Dự án được thiết kế để **học**: mỗi file đều có chú thích giải thích *tại sao* viết như vậy,
chứ không chỉ *viết cái gì*.

---

## 1. Chạy thử trong 30 giây

Yêu cầu: **JDK 17 trở lên** (máy bạn đang có JDK 21 ✅). Không cần cài Gradle, không cần Docker.

```bash
# Windows PowerShell / CMD
gradlew.bat run

# Git Bash / Linux / macOS
./gradlew run
```

Mở trình duyệt: <http://localhost:8080/api/vehicles>

```bash
gradlew.bat test      # chạy 18 test
gradlew.bat build     # build + test + đóng gói
```

### Tài khoản có sẵn

| Email | Mật khẩu | Quyền |
|---|---|---|
| `admin@vehiclerental.com` | `123456` | ADMIN |
| `khach@vehiclerental.com` | `123456` | CUSTOMER |

Dữ liệu mẫu (3 loại xe, 6 xe, 2 user) được nạp tự động lúc khởi động
— xem `config/DataSeeder.kt`, tắt bằng `app.seedData: false` trong `application.yaml`.

> ⚠️ Database là **H2 in-memory**: tắt app là mất sạch dữ liệu. Đó là chủ ý —
> bạn nghịch thoải mái, restart là sạch. Xem mục 7 để đổi sang PostgreSQL.

---

## 2. Bản đồ thư mục

```text
src/main/kotlin/com/vehiclerental/
├── Application.kt              ★ Điểm khởi động — đọc file này ĐẦU TIÊN
│
├── config/
│   ├── DatabaseFactory.kt      Kết nối DB, tạo bảng, hàm dbQuery{}
│   └── DataSeeder.kt           Nạp dữ liệu mẫu
│
├── di/AppModule.kt             ★ Koin: khai báo "ai phụ thuộc vào ai"
│
├── db/table/Tables.kt          Schema DB kiểu Exposed (users, vehicles, bookings...)
│
├── domain/                     Tầng trong cùng — không phụ thuộc vào Ktor hay DB
│   ├── model/Models.kt         Domain model: User, Vehicle, Booking
│   ├── model/Enums.kt          UserRole, VehicleStatus, BookingStatus
│   └── exception/AppException.kt  ★ sealed class lỗi nghiệp vụ
│
├── dto/                        Hình dạng JSON vào/ra + hàm toResponse()
│   ├── AuthDto.kt  VehicleDto.kt  BookingDto.kt  ErrorDto.kt
│
├── repository/                 ★ Chỉ nói chuyện với DB
│   ├── Mappers.kt              ResultRow -> domain model
│   ├── UserRepository.kt       (interface + Impl)
│   ├── VehicleRepository.kt    lọc + phân trang + JOIN
│   └── BookingRepository.kt    ★ hasOverlap() — check trùng lịch
│
├── service/                    ★ LOGIC NGHIỆP VỤ — trái tim của dự án
│   ├── AuthService.kt          đăng ký / đăng nhập / validate
│   ├── VehicleService.kt       lọc, phân trang, thêm xe
│   ├── BookingService.kt       ★★ state machine của đơn đặt xe
│   └── PricingPolicy.kt        công thức tính tiền (pure function)
│
├── routes/                     Khai báo endpoint, KHÔNG chứa logic
│   ├── AuthRoutes.kt  VehicleRoutes.kt  BookingRoutes.kt
│
├── security/
│   ├── JwtService.kt           tạo & verify token
│   ├── PasswordHasher.kt       BCrypt
│   └── AuthUser.kt             requireUser() / requireAdmin()
│
├── plugins/                    Cấu hình các plugin Ktor
│   ├── Routing.kt              ★ ghép tất cả route lại
│   ├── Security.kt             cấu hình "auth-jwt"
│   ├── Serialization.kt        JSON
│   ├── StatusPages.kt          ★ xử lý lỗi tập trung
│   └── Monitoring.kt           log, CORS, headers
│
└── util/
    ├── Serializers.kt          dạy JSON đọc/ghi LocalDateTime & BigDecimal
    └── CallExtensions.kt       longParam(), intQuery(), stringQuery()
```

`★` = những file nên đọc kỹ nhất.

---

## 3. Một request đi qua những đâu?

Ví dụ `POST /api/bookings` (đặt xe):

```text
Client gửi JSON
    │
    ▼
[CallLogging]      ghi log "POST /api/bookings"
    │
    ▼
[Authentication]   đọc header Authorization, verify JWT
    │                  └─ sai/thiếu token ──► 401, DỪNG tại đây
    ▼
[BookingRoutes]    call.receive<CreateBookingRequest>()  (JSON -> data class)
    │              call.requireUser()                    (lấy userId từ token)
    ▼
[BookingService]   validatePeriod()      thời gian hợp lệ?
    │              vehicleRepository.findById()
    │              bookingRepository.hasOverlap()   trùng lịch?
    │              PricingPolicy.calculateTotal()   tính tiền
    │                  └─ vi phạm quy tắc ──► throw AppException
    ▼
[BookingRepository] INSERT INTO bookings ...  (trong newSuspendedTransaction)
    │
    ▼
[Route]            call.respond(201, booking.toResponse())
    │                                       └─ data class -> JSON
    ▼
Client nhận JSON

Nếu có exception ở BẤT KỲ đâu ──► [StatusPages] ──► JSON lỗi thống nhất
```

**Quy tắc vàng của dự án này:**

| Tầng | ĐƯỢC làm | KHÔNG được làm |
|---|---|---|
| `routes` | đọc body/param, gọi service, respond | viết `if` kiểm tra nghiệp vụ, gọi DB |
| `service` | validate nghiệp vụ, tính toán, gọi nhiều repository | biết về `call`, `HttpStatusCode`, viết SQL |
| `repository` | SELECT/INSERT/UPDATE | kiểm tra quyền, tính tiền |

Nếu bạn định thêm code mà không biết đặt ở đâu → hỏi: *"code này có cần biết về HTTP không?
Có cần biết về SQL không?"* Câu trả lời sẽ chỉ đúng tầng.

---

## 4. Danh sách API

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/health` | — | Kiểm tra server sống |
| POST | `/api/auth/register` | — | Đăng ký (luôn ra role CUSTOMER) |
| POST | `/api/auth/login` | — | Đăng nhập → nhận JWT |
| GET | `/api/auth/me` | 🔒 | Thông tin tài khoản đang đăng nhập |
| GET | `/api/vehicle-types` | — | Danh sách loại xe |
| GET | `/api/vehicles` | — | Danh sách xe + lọc + phân trang |
| GET | `/api/vehicles/{id}` | — | Chi tiết một xe |
| POST | `/api/admin/vehicles` | 👑 | Thêm xe mới |
| PATCH | `/api/admin/vehicles/{id}/status` | 👑 | Đổi trạng thái xe |
| POST | `/api/bookings/quote` | 🔒 | Báo giá thử (không lưu DB) |
| POST | `/api/bookings` | 🔒 | Tạo đơn đặt xe |
| GET | `/api/bookings/my` | 🔒 | Đơn của tôi |
| GET | `/api/bookings/{id}` | 🔒 | Chi tiết đơn (chỉ chủ đơn hoặc admin) |
| PATCH | `/api/bookings/{id}/cancel` | 🔒 | Hủy đơn |
| GET | `/api/admin/bookings` | 👑 | Tất cả đơn |
| PATCH | `/api/admin/bookings/{id}/confirm` | 👑 | Duyệt đơn → xe thành RENTED |
| PATCH | `/api/admin/bookings/{id}/complete` | 👑 | Tất toán → xe thành AVAILABLE |

🔒 = cần đăng nhập &nbsp;&nbsp; 👑 = cần role ADMIN

**Query param của `GET /api/vehicles`:** `typeId`, `status`, `keyword`, `maxPricePerDay`, `page`, `size`

Ví dụ: `/api/vehicles?status=AVAILABLE&keyword=toyota&maxPricePerDay=800000&page=1&size=5`

📄 Toàn bộ ví dụ gọi API: xem file **`api.http`** (mở bằng IntelliJ IDEA hoặc VS Code + REST Client, bấm ▶ để chạy).

---

## 5. Vòng đời một đơn đặt xe (state machine)

```text
         POST /api/bookings
                 │
                 ▼
            ┌─────────┐   admin confirm    ┌───────────┐  admin complete  ┌───────────┐
            │ PENDING │ ─────────────────► │ CONFIRMED │ ───────────────► │ COMPLETED │
            └─────────┘                    └───────────┘                  └───────────┘
                 │                               │                      (xe -> AVAILABLE)
                 │ cancel                        │ cancel          (chỉ trước giờ nhận xe)
                 ▼                               ▼
            ┌───────────┐                   ┌───────────┐
            │ CANCELLED │ ◄──────────────── │ CANCELLED │  (xe -> AVAILABLE)
            └───────────┘                   └───────────┘
                                        (xe -> RENTED khi CONFIRMED)
```

Mọi phép chuyển trạng thái **không nằm trên sơ đồ** đều trả `409 Conflict` với
`errorCode: "INVALID_STATE"`. Logic nằm ở `BookingService.confirm/complete/cancel`.

---

## 6. Tài liệu học tập kèm theo

| File | Dùng khi nào |
|---|---|
| **`docs/CAU-HOI.md`** | 60 câu hỏi kiểm tra hiểu bài, chia 5 cấp độ |
| **`docs/DAP-AN.md`** | Đáp án — chỉ mở SAU khi đã tự trả lời |
| **`docs/BAI-TAP.md`** | 15 bài tập code thật, từ dễ đến khó, có gợi ý |
| **`api.http`** | Bấm chạy thử từng endpoint |

**Lộ trình đề xuất:**
1. Chạy `gradlew run`, nghịch `api.http` cho quen với API.
2. Đọc code theo thứ tự: `Application.kt` → `plugins/Routing.kt` → `routes/AuthRoutes.kt` → `service/AuthService.kt` → `repository/UserRepository.kt`. Đây là một "lát cắt dọc" đủ để hiểu toàn bộ kiến trúc.
3. Trả lời `docs/CAU-HOI.md` cấp 1–2, đối chiếu `docs/DAP-AN.md`.
4. Làm `docs/BAI-TAP.md` bài 1–5.
5. Đọc `service/BookingService.kt` + `repository/BookingRepository.kt` (phần khó nhất).
6. Trả lời câu hỏi cấp 3–5, làm bài tập 6–15.

---

## 7. Đổi sang PostgreSQL (khi bạn sẵn sàng)

Chỉ 2 bước, không phải sửa một dòng code nghiệp vụ nào — đó chính là lợi ích
của việc repository chỉ phụ thuộc vào Exposed chứ không phụ thuộc H2:

**Bước 1** — thêm driver vào `build.gradle.kts`:
```kotlin
implementation("org.postgresql:postgresql:42.7.3")
```

**Bước 2** — sửa `src/main/resources/application.yaml`:
```yaml
database:
  driver: "org.postgresql.Driver"
  url: "jdbc:postgresql://localhost:5432/vehiclerental"
  user: "postgres"
  password: "postgres"
  poolSize: 10
```

---

## 8. Những chỗ CỐ TÌNH làm đơn giản (bài tập nâng cao cho bạn)

Dự án này chạy ổn định nhưng chưa "production-ready". Các điểm sau là **cố ý**
để bạn tự nâng cấp — chi tiết trong `docs/BAI-TAP.md`:

- `SchemaUtils.create()` thay vì **Flyway migration**.
- JWT secret nằm trong `application.yaml` thay vì **biến môi trường**.
- `hasOverlap()` kiểm tra rồi mới ghi → còn **race condition** nếu 2 request cùng lúc.
- Chưa có **refresh token**, chưa có **logout**.
- Chưa có bảng `contracts`, `payments`, `penalties` (đã có sẵn `PricingPolicy.calculateLateFee()` chờ dùng).
- Chưa có **rate limiting**, chưa có **OpenAPI/Swagger**.

---

## 9. Sự cố thường gặp

| Triệu chứng | Nguyên nhân & cách xử lý |
|---|---|
| `Address already in use: bind` | Cổng 8080 đang bị chiếm. Đổi `ktor.deployment.port` trong `application.yaml`, hoặc `netstat -ano \| findstr :8080` rồi `taskkill /PID <pid> /F`. |
| `401` dù vừa đăng nhập | Thiếu chữ `Bearer ` trước token, hoặc token đã hết hạn (mặc định 24h). |
| `NoSuchElementException: Key ... is missing` | Thiếu key trong `application.yaml` (ví dụ vừa thêm config mới mà quên khai báo). |
| Đổi tên file `Application.kt` xong app không chạy | Phải sửa luôn dòng `modules: - com.vehiclerental.ApplicationKt.module` trong `application.yaml`. |
| Build treo / lỗi lạ | `gradlew.bat --stop` rồi `gradlew.bat clean build`. |
| Sửa code mà không thấy đổi | Ktor không hot-reload mặc định. Dừng (Ctrl+C) và `gradlew run` lại. |
