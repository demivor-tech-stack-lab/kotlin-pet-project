# 🚗 Vehicle Rental API

API cho thuê xe viết bằng **Kotlin + Ktor + Exposed + Koin + JWT**, đã được làm
**production-ready**: transaction đúng ranh giới, chống race condition, refresh token
thu hồi được, rate limit, health probe, metrics, Docker + CI.

Dự án đồng thời là tài liệu học: mỗi file đều có chú thích giải thích *tại sao* viết
như vậy, chứ không chỉ *viết cái gì*.

---

## 1. Chạy thử trong 30 giây

Yêu cầu: **JDK 17+**. Không cần cài Gradle, không cần Docker, không cần database.

```bash
gradlew.bat run        # Windows
./gradlew run          # Git Bash / Linux / macOS
```

Mở <http://localhost:8080/api/vehicles>

```bash
gradlew.bat test       # 54 test
gradlew.bat build      # build + test + đóng gói
```

**Tài khoản có sẵn** (chỉ ở chế độ dev):

| Email | Mật khẩu | Quyền |
|---|---|---|
| `admin@vehiclerental.com` | `matkhau123` | ADMIN |
| `khach@vehiclerental.com` | `matkhau123` | CUSTOMER |

> Mặc định dev dùng **H2 in-memory** — tắt app là mất dữ liệu, restart là sạch.
> Production dùng **PostgreSQL**, chỉ khác biến môi trường, không đổi một dòng code.

## Triển khai thật

👉 **[`docs/DEPLOY.md`](docs/DEPLOY.md)** — hướng dẫn đầy đủ: VPS, Docker Compose, HTTPS,
backup, giám sát, checklist trước khi mở cho người dùng.

```bash
cp .env.example .env      # rồi điền JWT_SECRET, POSTGRES_PASSWORD, CORS_ALLOWED_HOSTS
docker compose up -d --build
```

---

## 2. Bản đồ thư mục

```text
src/main/kotlin/com/vehiclerental/
├── Application.kt              ★ Điểm khởi động — đọc file này ĐẦU TIÊN
│
├── config/
│   ├── AppConfig.kt            ★ Cấu hình từ biến môi trường + CHẶN cấu hình prod không an toàn
│   ├── DatabaseFactory.kt      ★ Hikari pool, Flyway, health check, ranh giới transaction
│   ├── TransactionRunner.kt    ★ Interface transaction, tiêm vào service
│   └── DataSeeder.kt           Dữ liệu mẫu (chỉ dev)
│
├── di/AppModule.kt             ★ Koin: khai báo "ai phụ thuộc vào ai"
│
├── db/table/Tables.kt          Schema kiểu Exposed (users, vehicles, bookings, refresh_tokens)
│
├── domain/                     Tầng trong cùng — không phụ thuộc Ktor hay DB
│   ├── model/Models.kt         User, Vehicle, Booking, RefreshToken
│   ├── model/Enums.kt          UserRole, VehicleStatus, BookingStatus
│   └── exception/AppException.kt  ★ sealed class lỗi nghiệp vụ
│
├── dto/                        Hình dạng JSON vào/ra + hàm toResponse()
│
├── repository/                 ★ Chỉ đọc/ghi DB — KHÔNG tự mở transaction
│   ├── UserRepository.kt  VehicleRepository.kt  BookingRepository.kt
│   ├── RefreshTokenRepository.kt
│   └── Mappers.kt              ResultRow -> domain model
│
├── service/                    ★ LOGIC NGHIỆP VỤ + nơi DUY NHẤT mở transaction
│   ├── AuthService.kt          đăng ký/đăng nhập/refresh/logout/đổi mật khẩu
│   ├── VehicleService.kt       lọc, phân trang, thống kê
│   ├── BookingService.kt       ★★ state machine + chống race condition
│   └── PricingPolicy.kt        công thức tính tiền (hàm thuần)
│
├── routes/                     Khai báo endpoint, KHÔNG chứa logic
│   ├── AuthRoutes.kt  VehicleRoutes.kt  BookingRoutes.kt
│   └── OpsRoutes.kt            ★ health/live, health/ready, metrics
│
├── security/
│   ├── JwtService.kt           access token (15 phút)
│   ├── SecureTokens.kt         sinh + băm refresh token
│   ├── PasswordHasher.kt       BCrypt
│   └── AuthUser.kt             requireUser() / requireAdmin()
│
├── plugins/
│   ├── Routing.kt              ★ ghép tất cả route
│   ├── Security.kt             cấu hình "auth-jwt"
│   ├── Serialization.kt        JSON
│   ├── StatusPages.kt          ★ xử lý lỗi tập trung
│   ├── Monitoring.kt           ★ request-id, log, CORS, security headers, metrics
│   └── RateLimiting.kt         ★ chống bruteforce
│
└── util/
    ├── TimeProvider.kt         ★ nguồn thời gian duy nhất (test được, đúng múi giờ)
    ├── Serializers.kt          LocalDateTime & BigDecimal cho JSON
    └── CallExtensions.kt       longParam(), enumQuery(), clientInfo()...

src/main/resources/
├── application.yaml            Giá trị mặc định cho DEV (prod dùng biến môi trường)
├── logback.xml                 Log dạng chữ (dev)
├── logback-prod.xml            Log dạng JSON (production)
└── db/migration/               ★ Flyway migration
    ├── V1__init_schema.sql
    └── V2__reference_data.sql
```

`★` = những file nên đọc kỹ nhất.

---

## 3. Một request đi qua những đâu?

Ví dụ `POST /api/bookings` (đặt xe):

```text
Client gửi JSON
    │
    ▼
[XForwardedHeaders] đọc IP thật của client (sau reverse proxy)
[CallId]            gắn X-Request-Id -> mọi dòng log của request này đều mang mã đó
[CallLogging]       ghi log "POST /api/bookings"
[RateLimit]         quá 300 lần/phút ──► 429, DỪNG
[Authentication]    verify JWT ──► sai/thiếu ──► 401, DỪNG
    │
    ▼
[BookingRoutes]     call.receive<CreateBookingRequest>()   JSON -> data class
                    call.requireUser()                     lấy userId từ token
    │
    ▼
[BookingService]    validatePeriod()
    │  ┌──────────── MỘT TRANSACTION ────────────┐
    │  │ findByIdForUpdate()  🔒 KHÓA dòng xe    │
    │  │ hasOverlap()            trùng lịch?     │
    │  │ PricingPolicy           tính tiền       │
    │  │ bookingRepository.create()  INSERT      │
    │  └── lỗi ở bất kỳ đâu => ROLLBACK toàn bộ ─┘
    ▼
[Route]             call.respond(201, booking.toResponse())
    ▼
Client nhận JSON  (kèm X-Request-Id + các header bảo mật)

Exception ở BẤT KỲ đâu ──► [StatusPages] ──► JSON lỗi thống nhất
```

**Quy tắc vàng:**

| Tầng | ĐƯỢC làm | KHÔNG được làm |
|---|---|---|
| `routes` | đọc body/param, gọi service, respond | `if` nghiệp vụ, gọi DB |
| `service` | validate, tính toán, **mở transaction**, gọi nhiều repository | biết về `call`, `HttpStatusCode`, viết SQL |
| `repository` | SELECT/INSERT/UPDATE **trong transaction có sẵn** | tự mở transaction, kiểm tra quyền, tính tiền |

Không biết đặt code ở đâu → hỏi: *"code này có cần biết về HTTP không? Có cần biết về SQL không?"*

---

## 4. Danh sách API

### Vận hành

| Method | Endpoint | Mô tả |
|---|---|---|
| GET | `/health/live` | Tiến trình còn sống? (**không** chạm DB) |
| GET | `/health/ready` | Sẵn sàng nhận request? (**có** kiểm tra DB) |
| GET | `/metrics` | Prometheus (bảo vệ bằng `METRICS_TOKEN`) |

### Xác thực

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| POST | `/api/auth/register` | ⏱ | Đăng ký → cặp token |
| POST | `/api/auth/login` | ⏱ | Đăng nhập → cặp token |
| POST | `/api/auth/refresh` | ⏱ | Đổi refresh token lấy access token mới (có xoay vòng) |
| POST | `/api/auth/logout` | — | Thu hồi refresh token của thiết bị này |
| POST | `/api/auth/logout-all` | 🔒 | Đăng xuất mọi thiết bị |
| POST | `/api/auth/change-password` | 🔒 | Đổi mật khẩu (thu hồi toàn bộ phiên) |
| GET | `/api/auth/me` | 🔒 | Thông tin tài khoản |

### Xe

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| GET | `/api/vehicles` | — | Danh sách + lọc + phân trang |
| GET | `/api/vehicles/{id}` | — | Chi tiết |
| GET | `/api/vehicles/{id}/availability` | — | Lịch bận (không lộ ai đặt) |
| GET | `/api/vehicle-types` | — | Danh sách loại xe |
| GET | `/api/vehicle-types/{id}` | — | Chi tiết loại xe |
| POST | `/api/admin/vehicles` | 👑 | Thêm xe |
| PATCH | `/api/admin/vehicles/{id}/status` | 👑 | Đổi trạng thái xe |
| GET | `/api/admin/stats` | 👑 | Thống kê tổng quan |

### Đặt xe

| Method | Endpoint | Quyền | Mô tả |
|---|---|---|---|
| POST | `/api/bookings/quote` | 🔒 | Báo giá thử (không lưu DB) |
| POST | `/api/bookings` | 🔒 | Tạo đơn |
| GET | `/api/bookings/my` | 🔒 | Đơn của tôi |
| GET | `/api/bookings/{id}` | 🔒 | Chi tiết (chủ đơn hoặc admin) |
| PATCH | `/api/bookings/{id}/cancel` | 🔒 | Hủy (khách phải hủy trước 24h; admin thì bất kỳ lúc nào) |
| GET | `/api/admin/bookings` | 👑 | Tất cả đơn |
| PATCH | `/api/admin/bookings/{id}/confirm` | 👑 | Duyệt → xe thành RENTED |
| PATCH | `/api/admin/bookings/{id}/complete` | 👑 | Tất toán → xe về AVAILABLE |

⏱ = giới hạn 10 lần/phút &nbsp;&nbsp; 🔒 = cần đăng nhập &nbsp;&nbsp; 👑 = cần ADMIN

**Query của `GET /api/vehicles`:** `typeId`, `status`, `keyword`, `minPricePerDay`,
`maxPricePerDay`, `page`, `size` (tối đa 50)

📄 Ví dụ gọi API đầy đủ: **[`api.http`](api.http)** (mở bằng IntelliJ hoặc VS Code + REST Client)

---

## 5. Vòng đời một đơn đặt xe

```text
         POST /api/bookings
                 │
                 ▼
            ┌─────────┐   admin confirm    ┌───────────┐  admin complete  ┌───────────┐
            │ PENDING │ ─────────────────► │ CONFIRMED │ ───────────────► │ COMPLETED │
            └─────────┘   xe -> RENTED     └───────────┘  xe -> AVAILABLE └───────────┘
                 │                               │
                 │ cancel                        │ cancel  (xe -> AVAILABLE)
                 ▼                               ▼
            ┌───────────────────── CANCELLED ─────────────────────┐
            └─────────────────────────────────────────────────────┘
```

Mọi phép chuyển **không nằm trên sơ đồ** đều trả `409` với `errorCode: "INVALID_STATE"`.
Mỗi phép chuyển ghi vào **2 bảng trong 1 transaction** — không có trạng thái nửa vời.

---

## 6. Cấu hình

Mọi thứ đọc theo thứ tự: **biến môi trường** → `application.yaml` → mặc định.
Danh sách đầy đủ trong [`.env.example`](.env.example).

Khi `APP_ENV=prod`, ứng dụng **từ chối khởi động** nếu:
JWT secret là giá trị dev hoặc ngắn hơn 32 ký tự · dùng H2 · `DB_PASSWORD` rỗng ·
CORS mở cho mọi host · `SEED_DATA=true` · rate limit bị tắt.

> Container không start được thì bạn sửa trong 5 phút. App chạy với secret dev thì
> vài tháng sau mới biết.

---

## 7. Tài liệu

| File | Dùng khi nào |
|---|---|
| **[`docs/PRODUCTION-CHANGES.md`](docs/PRODUCTION-CHANGES.md)** | ★ Từ pet project lên production đã đổi gì, vì sao. Phần học được nhiều nhất |
| **[`docs/DEPLOY.md`](docs/DEPLOY.md)** | Triển khai VPS: Docker, HTTPS, backup, giám sát |
| **[`docs/CAU-HOI.md`](docs/CAU-HOI.md)** | 60 câu hỏi kiểm tra hiểu bài, 5 cấp độ |
| **[`docs/DAP-AN.md`](docs/DAP-AN.md)** | Đáp án — mở SAU khi đã tự trả lời |
| **[`docs/BAI-TAP.md`](docs/BAI-TAP.md)** | 20 bài tập code thật, có gợi ý (5 bài mới cho bản production) |
| **[`api.http`](api.http)** | Bấm ▶ chạy thử từng endpoint |

**Lộ trình đề xuất:**
1. `gradlew run`, nghịch `api.http` cho quen API.
2. Đọc theo lát cắt dọc: `Application.kt` → `plugins/Routing.kt` → `routes/AuthRoutes.kt` → `service/AuthService.kt` → `repository/UserRepository.kt`.
3. Trả lời `docs/CAU-HOI.md` cấp 1–2, làm `docs/BAI-TAP.md` bài 1–5.
4. Đọc `service/BookingService.kt` + `repository/BookingRepository.kt` (phần khó nhất).
5. Đọc `docs/PRODUCTION-CHANGES.md` — hiểu vì sao code production khác code chạy được.
6. Trả lời câu hỏi cấp 3–5 và 10 câu nâng cao cuối `PRODUCTION-CHANGES.md`.

---

## 8. Kiểm thử

```bash
gradlew.bat test
```

| Bộ test | Số lượng | Kiểm chứng điều gì |
|---|---|---|
| `PricingPolicyTest` | 7 | Công thức tính tiền (không cần DB, chạy mili giây) |
| `AppConfigTest` | 11 | Cấu hình prod không an toàn thì **phải** chặn khởi động |
| `ApiIntegrationTest` | 25 | Toàn bộ API trên ứng dụng thật (Flyway + H2 + JWT) |
| `AuthTokenTest` | 8 | Refresh, xoay vòng token, logout, đổi mật khẩu |
| `ConcurrencyTest` | 3 | ★ Race condition + rollback transaction |
| `RateLimitTest` | 3 | Chống bruteforce, và health probe **không** bị chặn |

`ConcurrencyTest` là bộ đáng chú ý nhất: nó bắn 8 request đặt cùng một xe cùng lúc
và khẳng định đúng 1 thành công. Đã xác nhận test này có giá trị thật — bỏ khóa dòng
`SELECT ... FOR UPDATE` ra thì test đỏ ngay.

---

## 9. Sự cố thường gặp

| Triệu chứng | Nguyên nhân & cách xử lý |
|---|---|
| `Address already in use` | Cổng 8080 bị chiếm. `netstat -ano \| findstr :8080` rồi `taskkill /PID <pid> /F` |
| App tắt ngay, log báo "Cấu hình không hợp lệ" | **Đúng như thiết kế** — đọc danh sách lỗi trong log, sửa biến môi trường |
| `401` dù vừa đăng nhập | Access token chỉ sống 15 phút. Gọi `/api/auth/refresh` |
| `429 RATE_LIMITED` khi đang test | Giới hạn 10 lần/phút cho nhóm auth. Chờ 1 phút, hoặc đặt `RATE_LIMIT_ENABLED=false` khi dev |
| Flyway báo "checksum mismatch" | Đã sửa file migration **đã chạy**. Không được sửa — tạo `V3__...sql` mới |
| Sửa `Tables.kt` xong app báo "column not found" | Quên viết migration. Ở dev app sẽ **cảnh báo trong log** lúc khởi động |
| Đổi tên `Application.kt` xong app không chạy | Sửa luôn `modules: - com.vehiclerental.ApplicationKt.module` trong `application.yaml` |
| Build treo / lỗi lạ | `gradlew.bat --stop` rồi `gradlew.bat clean build` |
| Sửa code mà không thấy đổi | Ktor không hot-reload. Ctrl+C rồi `gradlew run` lại |
