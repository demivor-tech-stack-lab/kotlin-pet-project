# 🔧 Từ Pet Project Lên Production — Đã Đổi Những Gì Và Vì Sao

Tài liệu này là phần **học được nhiều nhất** của cả dự án: nó cho thấy khoảng cách
thật giữa "code chạy được" và "code dám cho người dùng thật dùng".

Đọc kèm `git log` để thấy từng thay đổi cụ thể.

---

## 🔴 Nhóm 1 — Lỗi làm HỎNG DỮ LIỆU

### 1.1. Ranh giới transaction đặt sai chỗ

**Trước:** mỗi hàm repository tự gọi `dbQuery { }` → mỗi câu lệnh SQL là một transaction riêng.

```kotlin
// BookingService.confirm() — bản cũ
bookingRepository.updateStatus(id, CONFIRMED)              // transaction 1 ✅ commit
vehicleRepository.updateStatus(vehicleId, RENTED)          // transaction 2 💥 lỗi
// Kết quả: đơn đã CONFIRMED nhưng xe vẫn AVAILABLE
//          -> người khác đặt chồng lên chính chiếc xe đó
```

**Sau:** repository **không** tự mở transaction nữa; **service** mở đúng một transaction
bao trọn một use case (mẫu **Unit of Work**).

```kotlin
suspend fun confirm(id: Long): BookingResponse = tx.tx {
    bookingRepository.updateStatus(id, CONFIRMED)
    vehicleRepository.updateStatus(booking.vehicleId, RENTED)
    // Cùng thành công, hoặc cùng rollback. Không có trạng thái nửa vời.
}
```

**Kiểm chứng:** `ConcurrencyTest.loi giua chung thi toan bo transaction bi rollback`
— ghi một thay đổi rồi cố tình ném exception, khẳng định thay đổi biến mất.

> 📌 Bài học: **ranh giới transaction phải trùng ranh giới nghiệp vụ**, không phải
> ranh giới của một câu lệnh SQL.

---

### 1.2. Race condition khi đặt xe (TOCTOU)

**Trước:** kiểm tra trùng lịch rồi mới ghi, không có gì ngăn hai người chen vào giữa.

```
A: hasOverlap() -> false ─┐
B: hasOverlap() -> false ─┤  (A chưa ghi xong nên B cũng thấy trống)
A: INSERT                 │
B: INSERT                 ┘  => 2 đơn trùng lịch trên cùng một xe
```

**Sau:** hai thay đổi, **cả hai đều bắt buộc**:

1. Bọc toàn bộ kiểm-tra-rồi-ghi trong **một** transaction.
2. `findByIdForUpdate()` sinh `SELECT ... FOR UPDATE` → **khóa dòng xe**.
   B phải xếp hàng chờ A commit xong mới chạy tiếp.

Cộng thêm một chi tiết dễ bỏ sót: **mức cô lập phải là `READ_COMMITTED`**.

```kotlin
// Trước: transactionIsolation = "TRANSACTION_REPEATABLE_READ"
// Sau:   transactionIsolation = "TRANSACTION_READ_COMMITTED"
```

Vì sao? Ở `REPEATABLE_READ`, transaction chụp một "ảnh" dữ liệu lúc bắt đầu và giữ
nguyên đến cuối. B chờ lấy được khóa, nhưng khi đọc lại bảng `bookings` nó **vẫn thấy
ảnh cũ** — không thấy đơn A vừa ghi → vẫn tạo đơn trùng. Khóa dòng trở nên vô nghĩa.

**Kiểm chứng:** `ConcurrencyTest.dat cung mot xe cung luc chi mot request thanh cong`
bắn 8 request đồng thời, khẳng định đúng 1 thành công.

> ✅ Test này đã được xác nhận là **có giá trị thật**: bỏ `findByIdForUpdate()` ra
> thì test đỏ với 6/8 request cùng tạo được đơn.

---

## 🔴 Nhóm 2 — Bảo mật

### 2.1. Secret nằm trong file đã commit lên GitHub

**Trước:** `application.yaml` chứa JWT secret thật, ai clone repo cũng tự ký được token admin.

**Sau:** toàn bộ cấu hình đọc từ **biến môi trường** (chuẩn 12-factor).
`AppConfig` **từ chối khởi động** nếu phát hiện cấu hình production không an toàn:

| Kiểm tra | Vì sao |
|---|---|
| `JWT_SECRET` không phải giá trị mặc định của dev | Deploy nhầm = ai cũng giả được admin |
| `JWT_SECRET` ≥ 32 ký tự | Secret ngắn có thể bị dò |
| Không dùng H2 ở prod | Restart là mất sạch dữ liệu |
| `DB_PASSWORD` không rỗng | — |
| CORS không mở cho mọi host | Website bất kỳ gọi được API thay người dùng |
| `SEED_DATA=false` | Tránh tạo `admin` với mật khẩu ai cũng đoán được |
| `RATE_LIMIT_ENABLED=true` | Không có = bruteforce tự do |

> 📌 Nguyên tắc: biến một **sự cố bảo mật âm thầm** thành một **lỗi khởi động ồn ào**.
> Container không start được thì bạn sửa trong 5 phút. App chạy với secret dev thì
> vài tháng sau mới biết — lúc đó đã muộn.

**Kiểm chứng:** `AppConfigTest` — 11 test, không cần database, chạy trong mili giây.

---

### 2.2. Token 24 giờ, không thu hồi được

**Trước:** một JWT sống 24 giờ. Đổi mật khẩu xong token cũ **vẫn dùng được**, vì JWT
không lưu trạng thái nào ở server nên không có cách nào hủy.

**Sau:** tách làm hai loại token với hai vai trò rõ ràng:

| | Access token | Refresh token |
|---|---|---|
| Dạng | JWT tự mang thông tin | Chuỗi ngẫu nhiên 256 bit |
| Sống | **15 phút** | 30 ngày |
| Lưu ở server | Không | Có (bảng `refresh_tokens`) |
| Thu hồi được | ❌ | ✅ |
| Dùng để | Gọi mọi API | Chỉ để xin access token mới |

Ba cơ chế bảo vệ đi kèm:

- **Xoay vòng (rotation):** mỗi lần refresh, token cũ bị thu hồi ngay. Nếu kẻ trộm
  dùng trước, lần refresh tiếp theo của người dùng thật sẽ 401 — một tín hiệu rõ ràng.
- **Băm trước khi lưu:** cột `token_hash` lưu SHA-256, không lưu token gốc. Kẻ đọc
  trộm database cũng không đăng nhập được — đúng nguyên tắc đã áp dụng cho mật khẩu.
- **Đổi mật khẩu = đá văng mọi phiên.** Vì lý do đổi mật khẩu thường là "nghi bị lộ".

**Kiểm chứng:** `AuthTokenTest` — 8 test phủ refresh, rotation, logout, logout-all, đổi mật khẩu.

---

### 2.3. Bruteforce mật khẩu tự do

**Trước:** không có giới hạn nào. Một script gọi `/api/auth/login` vài nghìn lần/phút.

**Sau:** plugin `RateLimit` với hai nhóm:
- `auth`: 10 lần/phút (đăng ký, đăng nhập, refresh) — đếm theo **IP**.
- `global`: 300 lần/phút — đếm theo **userId** nếu đã đăng nhập, theo IP nếu chưa.

> Vì sao đã đăng nhập thì đếm theo userId? Vì nhiều người dùng chung một IP
> (văn phòng, NAT của nhà mạng) sẽ chặn nhầm lẫn nhau.

⚠️ **Health probe cố tình nằm NGOÀI rate limit.** Nếu probe bị tính vào giới hạn thì
lúc hệ thống quá tải, probe nhận 429 → Docker tưởng app chết → restart container →
mất luôn phần dung lượng còn phục vụ được. Một vòng lặp tự hủy rất dễ mắc phải.

**Kiểm chứng:** `RateLimitTest`, gồm cả test khẳng định health probe không bị chặn.

---

### 2.4. Những chi tiết nhỏ nhưng đáng giá

| Thay đổi | Lý do |
|---|---|
| Mật khẩu tối thiểu 6 → **8** ký tự, tối đa 72 | BCrypt chỉ dùng 72 byte đầu |
| Sai email cũng chạy `verify()` với hash giả | Thời gian phản hồi hai trường hợp bằng nhau → chống dò qua thời gian |
| Header `Server` đổi thành `api` | Mặc định Ktor lộ cả tên **và phiên bản** framework |
| Thêm `X-Content-Type-Options`, `X-Frame-Options`, CSP, `Referrer-Policy` | Rẻ tiền, chặn được vài lớp tấn công phổ biến |
| `Strict-Transport-Security` chỉ bật ở prod | Bật ở localhost sẽ làm hỏng việc dev qua http |
| Chi tiết lỗi parse JSON chỉ hiện ở dev | Ở prod nó tiết lộ cấu trúc nội bộ |
| Giới hạn độ dài từ khóa tìm kiếm | `LIKE '%...%'` với chuỗi khổng lồ làm nghẽn DB rất rẻ |
| Lịch bận của xe không trả `userId` | Endpoint công khai — lộ ai thuê xe nào là rò rỉ dữ liệu cá nhân |

---

## 🟠 Nhóm 3 — Vận hành được

### 3.1. Flyway thay cho `SchemaUtils.create()`

`SchemaUtils.create()` chỉ **tạo bảng nếu chưa có**. Nó không bao giờ `ALTER`.
Nghĩa là từ lần deploy thứ hai trở đi, mọi thay đổi schema đều bị bỏ qua âm thầm.

Flyway ghi lại từng phiên bản đã chạy vào bảng `flyway_schema_history`, và
`validateOnMigrate` sẽ **chặn khởi động** nếu file migration đã chạy bị sửa nội dung.

> ⚠️ Quy tắc bất di bất dịch: **không bao giờ sửa file migration đã chạy.**
> Cần đổi thì tạo file `V3__...sql` mới.

Hai chi tiết thú vị gặp phải khi viết migration chạy được trên **cả H2 lẫn PostgreSQL**:

1. **`year` là từ khóa dành riêng trong H2 2.x** → `CREATE TABLE` lỗi cú pháp.
   Đổi tên cột thành `manufacture_year` (thuộc tính Kotlin vẫn tên `year`).
2. **H2 gấp identifier chưa trích dẫn thành CHỮ HOA, PostgreSQL gấp thành chữ thường.**
   Exposed trích dẫn cột `role` (là từ khóa SQL) thành `"role"` → khớp ở Postgres nhưng
   lệch ở H2. Bật `CASE_INSENSITIVE_IDENTIFIERS=TRUE` để H2 hành xử giống Postgres.

> 📌 Đây chính là kiểu lỗi nguy hiểm nhất: **test xanh nhưng production đỏ** (hoặc
> ngược lại). Làm cho môi trường dev giống production hết mức có thể là cách phòng duy nhất.

### 3.2. Health probe tách đôi

| Endpoint | Kiểm tra DB? | Trả NO thì sao |
|---|---|---|
| `/health/live` | **Không** | Docker **khởi động lại** container |
| `/health/ready` | **Có** | Tạm ngừng gửi request, **không** restart |

Nhầm hai cái này gây hậu quả thật: nếu liveness kiểm tra cả database, thì mỗi lần
database chớp tắt vài giây, toàn bộ container app bị restart hàng loạt — biến một
sự cố nhỏ thành sự cố toàn hệ thống.

### 3.3. Mỗi request một mã định danh

Plugin `CallId` gắn `X-Request-Id` cho mọi request và đưa vào MDC của log. Kết quả:

```
13:39:02.301 [worker-1] INFO BookingService [3d144cd1-...] - Tao don thanh cong: bookingId=7
```

Khi API trả 500, client nhận kèm chính mã đó. Khách báo lỗi → `grep` một phát là ra
trọn vẹn dòng đời request. Nếu client (hoặc API gateway) đã gửi sẵn header thì dùng lại,
để một request đi qua nhiều dịch vụ vẫn giữ chung một mã.

### 3.4. Metrics, tắt máy êm ái, dọn rác

- **`/metrics`** cho Prometheus: số request, độ trễ, phân bố status code, bộ nhớ JVM,
  GC, thread, connection pool. Bảo vệ bằng `METRICS_TOKEN` (so sánh theo thời gian hằng số).
- **Graceful shutdown:** `SIGTERM` → ngừng nhận request mới → xử lý nốt request đang
  chạy → đóng connection pool. Không có bước này thì `docker compose down` cắt ngang
  request đang dở (khách mất tiền mà không có đơn) và để lại kết nối rác ở PostgreSQL.
- **Job dọn refresh token hết hạn** chạy 6 tiếng/lần. Không dọn thì bảng này chỉ phình
  to mãi và làm chậm chính thao tác đăng nhập.

### 3.5. HikariCP được cấu hình tử tế

| Tham số | Vì sao |
|---|---|
| `connectionTimeout = 10s` | Mặc định 30s: sự cố DB làm mọi request treo 30 giây, người dùng tưởng web chết |
| `leakDetectionThreshold = 20s` | Phát hiện connection bị giữ quên trả — bug âm thầm làm cạn pool sau vài giờ |
| `maxLifetime = 30 phút` | Tránh dùng phải connection đã bị firewall/proxy cắt ngầm |

---

## 🟡 Nhóm 4 — Kiến trúc dễ test hơn

### 4.1. `TimeProvider` thay cho `LocalDateTime.now()`

Gọi thẳng `LocalDateTime.now()` có hai vấn đề:

1. **Phụ thuộc múi giờ máy chủ.** Container Docker chạy UTC, máy dev chạy giờ Việt Nam
   → cùng một dòng code cho hai kết quả lệch 7 tiếng. Quy tắc "không cho đặt xe trong
   quá khứ" hoạt động khác nhau ở hai nơi.
2. **Không test được.** Muốn kiểm tra "token hết hạn sau 30 ngày" thì phải chờ 30 ngày.

Giải pháp: bọc đồng hồ sau một interface, tiêm qua Koin. Production dùng
`SystemTimeProvider` với múi giờ nghiệp vụ cấu hình được (`APP_TIMEZONE`),
test dùng `FixedTimeProvider` đứng yên tại một mốc chọn trước.

### 4.2. `TransactionRunner` thay cho hàm static toàn cục

Ngoài lợi ích test, cách này còn sửa một lỗi thật gặp phải khi viết bộ test:
Exposed ghi nhớ database "hiện tại" theo **thread-local**. Khi bộ test khởi động ứng
dụng nhiều lần, một thread có thể còn giữ tham chiếu tới `DataSource` đã đóng và ném
`HikariDataSource has been closed`. Chỉ định database **tường minh** thì hết lỗi.

> 📌 Trạng thái toàn cục ngầm luôn quay lại cắn bạn — thường là ở nơi bạn không ngờ nhất.

---

## 📊 Tóm tắt bằng con số

| | Pet project | Production |
|---|---|---|
| Số test | 18 | **54** |
| Test về đồng thời / transaction | 0 | **3** |
| Test về cấu hình an toàn | 0 | **11** |
| Test về vòng đời token | 0 | **8** |
| Access token sống | 24 giờ | 15 phút |
| Thu hồi được token | ❌ | ✅ |
| Quản lý schema | `SchemaUtils.create()` | Flyway có phiên bản |
| Secret | Trong file đã commit | Biến môi trường + kiểm tra fail-fast |
| Rate limit | ❌ | ✅ |
| Health probe | 1 cái, không rõ vai trò | live + ready, tách đúng vai trò |
| Truy vết sự cố | Không có | `X-Request-Id` xuyên suốt log |
| Triển khai | `gradlew run` | Docker + Compose + CI |

---

## 🎯 Câu hỏi tự kiểm tra (nâng cao)

Trả lời được những câu này nghĩa là bạn đã hiểu phần khó nhất:

1. Vì sao chỉ dùng `SELECT ... FOR UPDATE` là **chưa đủ** để chống trùng lịch? Cần thêm điều kiện gì?
2. Vì sao `REPEATABLE_READ` lại làm khóa dòng trở nên vô dụng trong tình huống này?
3. Access token 15 phút "an toàn hơn" 24 giờ ở điểm nào, khi mà cả hai đều không thu hồi được?
4. Vì sao lưu SHA-256 của refresh token mà **không** dùng BCrypt như với mật khẩu?
5. Điều gì xảy ra nếu health probe bị tính vào rate limit, lúc hệ thống đang quá tải?
6. Vì sao liveness probe **không nên** kiểm tra database?
7. `X-Forwarded-For` giúp gì cho rate limit? Nó có thể bị lợi dụng khi nào?
8. Vì sao migration đã chạy thì **tuyệt đối không được sửa nội dung**?
9. Nếu bỏ `TimeProvider` và quay lại `LocalDateTime.now()`, test nào sẽ hỏng trước — và vì sao nó chỉ hỏng trên CI chứ không hỏng trên máy bạn?
10. `AppConfig` từ chối khởi động khi cấu hình sai. Vì sao "không chạy được" lại **tốt hơn** "chạy được nhưng không an toàn"?

Đáp án nằm rải rác trong chính tài liệu này và trong chú thích của code.
