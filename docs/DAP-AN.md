# ✅ Đáp Án — 60 Câu Hỏi

> ⚠️ **LƯU Ý VỀ PHIÊN BẢN**
>
> Bộ tài liệu này được viết cho **phiên bản pet project ban đầu**. Dự án sau đó đã
> được nâng cấp lên production-ready, nên một số câu trả lời/bài tập mô tả các lỗ hổng
> **giờ đã được vá**. Điều đó KHÔNG làm tài liệu mất giá trị — ngược lại: hiểu lỗ hổng
> trước rồi mới xem cách vá là cách học hiệu quả nhất.
>
> Chỗ nào đã đổi và vì sao: xem **[`PRODUCTION-CHANGES.md`](PRODUCTION-CHANGES.md)**.
>
> Vài điểm khác so với code hiện tại: mật khẩu tài khoản mẫu giờ là `matkhau123`
> (không phải `123456`); đăng nhập trả về `accessToken` + `refreshToken` chứ không
> phải một trường `token`; repository **không** còn tự mở transaction.

---


> Chỉ mở file này **sau khi** đã tự trả lời. Đáp án ghi ngắn gọn phần cốt lõi;
> nếu bạn diễn đạt khác nhưng đúng ý thì vẫn tính là đúng.

---

## 🟢 CẤP 1 — Kotlin cơ bản

**1.** `val` = gán một lần rồi không đổi được (read-only); `var` = đổi được.
Chọn `val` vì domain model được đọc từ nhiều coroutine cùng lúc — bất biến thì
không sợ một chỗ sửa làm chỗ khác sai. Muốn "đổi" thì dùng `copy()` tạo bản mới.

**2.** `equals()`, `hashCode()`, `toString()`, `copy()`, và `component1()...componentN()`
(phục vụ destructuring). Nhờ `equals()` mà `assertEquals` so sánh được 2 object theo
**giá trị** thay vì theo địa chỉ bộ nhớ.

**3.** `String?` = có thể null, `String` = trình biên dịch **đảm bảo** không bao giờ null.
`vehicleType.description.length` sẽ **không biên dịch được** — Kotlin bắt bạn xử lý null
trước. Đây là điểm mạnh nhất của Kotlin so với Java: NullPointerException bị chặn ngay
lúc build chứ không đợi chạy mới nổ.

**4.**
- `?.` (safe call): `a?.b` → nếu `a` null thì cả biểu thức là null, không gọi `b`.
  VD: `.singleOrNull()?.toUser()` trong `UserRepositoryImpl`.
- `?:` (elvis): `a ?: b` → lấy `a`, nếu null thì lấy `b`.
  VD: `?: throw AppException.NotFound(...)` trong `BookingService.findOrThrow`.
- `!!` : "tôi chắc chắn không null, null thì cứ ném NPE". **Tránh dùng** trong code thật;
  trong dự án chỉ xuất hiện ở file test, nơi null là dấu hiệu test sai.

**5.** **Extension function** (hàm mở rộng) — gắn thêm hàm vào lớp `ApplicationCall`
mà không sửa mã nguồn lớp đó. Khác biệt: gọi được kiểu `call.longParam("id")`,
đọc tự nhiên như thể Ktor có sẵn hàm này, và IDE gợi ý ngay khi gõ `call.`.
Bản chất khi biên dịch vẫn là hàm static nhận `call` làm tham số đầu.

**6.** `object` = **singleton**: JVM chỉ tạo đúng một thể hiện duy nhất, không cần `new`.
Dùng khi lớp **không có trạng thái riêng** cho từng đối tượng — chỉ gom hàm tiện ích
(`PasswordHasher`, `PricingPolicy`) hoặc mô tả một thứ duy nhất (bảng `Users`).

**7.** `companion object` chứa thành viên "static" gắn với lớp chứ không gắn với đối tượng.
Gọi từ ngoài: `JwtService.CLAIM_USER_ID` (không cần tạo `JwtService()`).
Xem `security/AuthUser.kt` — nó dùng đúng cách này.

**8.** `const val` được thay thẳng vào code lúc **biên dịch**, nên chỉ dùng được cho
kiểu nguyên thủy và `String`, và phải ở top-level / trong `object` / `companion object`.
`EMAIL_REGEX` là đối tượng `Regex` — phải tạo lúc **chạy**, nên chỉ có thể là `val`.

**9.** `when` là **biểu thức** (trả về giá trị được), khớp được nhiều kiểu điều kiện
(enum, khoảng `in 1..10`, kiểu `is String`, biểu thức boolean). Không cần `else`
vì `BookingStatus` là enum và 4 nhánh đã phủ hết mọi giá trị — trình biên dịch tự kiểm tra.
👉 Lợi ích lớn: mai bạn thêm `BookingStatus.EXPIRED`, chỗ này **báo lỗi biên dịch ngay**,
nhắc bạn xử lý. Nếu có `else` thì nó âm thầm rơi vào `else` và sinh bug.

**10.** `Unit` ≈ `void` của Java — "không trả về gì". Ở đây nghĩa là "hai trạng thái này
hợp lệ, không làm gì cả, đi tiếp xuống dưới".

**11.** **Destructuring declaration** — tách một object thành nhiều biến.
Hoạt động được vì `Pair` có `component1()` và `component2()`. Mọi `data class` đều có sẵn.

**12.** `items to total` tạo `Pair(items, total)`. `to` là một infix function của Kotlin
(cùng cái `to` bạn thấy trong `mapOf("a" to 1)`).

**13.** `Triple` = bộ 3 giá trị (`Pair` là bộ 2). `{ (name, seats, desc) -> ... }`
là destructuring ngay trong tham số lambda.
👉 Lưu ý thực tế: `Triple` chỉ nên dùng cho dữ liệu tạm nội bộ như seeder.
Dữ liệu nghiệp vụ thật thì dùng `data class` có tên rõ ràng, vì `.first`/`.second`
đọc không hiểu gì.

**14.** `it` là tên mặc định của tham số khi lambda có **đúng 1 tham số** và bạn không đặt tên.
Nhiều hơn 1 tham số thì bắt buộc đặt tên (`{ call, cause -> }`).
👉 Khi lambda lồng nhau, nên đặt tên rõ để khỏi nhầm `it` nào của `it` nào —
đó là lý do trong `baseQuery()` mình viết `?.let { value -> ... }`.

---

## 🔵 CẤP 2 — Kiến trúc

**15.**
| Tầng | Trách nhiệm | Cấm |
|---|---|---|
| `routes` | đọc body/query/path, gọi service, `call.respond` | logic nghiệp vụ, truy vấn DB |
| `service` | validate nghiệp vụ, tính toán, điều phối nhiều repository | đụng vào `call`, `HttpStatusCode`, viết SQL |
| `repository` | SELECT/INSERT/UPDATE, map ResultRow | kiểm tra quyền, tính tiền, ném lỗi nghiệp vụ |

**16.** Vì chúng **thay đổi vì lý do khác nhau**:
- `User` đổi khi cấu trúc DB đổi.
- `UserResponse` đổi khi hợp đồng API với client đổi.

Nếu dùng chung một class: thêm cột `internalNote` vào DB là lập tức lộ ra API;
và `passwordHash` sẽ đi thẳng ra ngoài. Tách ra tốn thêm ít code nhưng mua được
sự an toàn và tự do thay đổi DB mà không phá client.

**17.** Hàm `User.toResponse()` trong `dto/AuthDto.kt` — nó chỉ chép 6 field và
**cố ý bỏ** `passwordHash`. Vì `UserResponse` không hề có field đó nên dù có lỡ tay
cũng không gán vào được.

**18.** (a) **Test dễ**: viết `FakeUserRepository` trả dữ liệu cứng, test `AuthService`
không cần database. (b) **Đổi hạ tầng dễ**: viết `PostgresUserRepository` mới,
service không đổi một dòng. Đây là chữ **D** trong SOLID (Dependency Inversion).

**19.** Đọc là: *"khi có ai đó cần một `UserRepository`, hãy đưa cho họ một
`UserRepositoryImpl` — tạo một lần và dùng chung cả vòng đời app"*.
Đổi sang implementation mới: sửa **đúng 1 dòng** trong `di/AppModule.kt`.

**20.** `get()` = "Koin ơi, lấy giúp tôi thứ có kiểu phù hợp". Koin biết nhờ **kiểu tham số
của constructor** `AuthService(userRepository: UserRepository, jwtService: JwtService)`
— nó suy ra `get()` thứ nhất cần `UserRepository`, thứ hai cần `JwtService`.
👉 Vì vậy nếu bạn đổi **thứ tự** tham số constructor mà 2 tham số khác kiểu nhau thì
vẫn chạy đúng; nhưng nếu 2 tham số **cùng kiểu** thì Koin không phân biệt được —
lúc đó phải dùng `named()` qualifier.

**21.** **Constructor injection**. Test dễ hơn vì bạn truyền thẳng đối tượng giả vào:
```kotlin
val service = AuthService(FakeUserRepository(), jwtService)   // không cần DB, không cần Koin
```
Nếu service tự `new` bên trong thì không có cách nào thay thế được.

**22.** Vì `configureSecurity()` gọi `by inject<JwtService>()` và `configureRouting()`
gọi `by inject<AuthService>()`. Chưa cài Koin thì chưa có "kho" để lấy ra →
lỗi lúc khởi động. Nguyên tắc chung: **cài cái cung cấp trước, cái tiêu thụ sau.**

**23.** Có sao. `DatabaseFactory.init()` gọi `Database.connect()` và tạo bảng.
Nếu nó chạy sau, các repository do Koin tạo ra vẫn OK (chúng chỉ tạo object rỗng),
nhưng `DataSeeder.seed()` chạy ở cuối `module()` sẽ nổ vì chưa có kết nối.
Nói chung: **hạ tầng trước, DI sau, plugin sau nữa, dữ liệu cuối cùng.**

**24.** `sealed` = chỉ các lớp con khai báo trong **cùng module** mới kế thừa được →
trình biên dịch **biết hết** danh sách lớp con. `abstract` thì ai ở đâu cũng kế thừa được.
Lợi ích ở đây: `when` trên `AppException` là exhaustive, và một handler
`exception<AppException>` trong StatusPages bắt trọn mọi lỗi nghiệp vụ.

**25.** Có mâu thuẫn nhẹ, và đây là **đánh đổi có chủ ý**. Cái lợi: không cần một bảng
"map lỗi → HTTP status" riêng, ném lỗi ở đâu cũng ra đúng status. Cái hại: nếu mai
dự án thêm giao diện gRPC hay CLI thì `HttpStatusCode` trở nên vô nghĩa ở đó.
👉 Cách "sạch" hơn: exception chỉ mang `errorCode`, còn StatusPages tự map sang HTTP.
Với pet project thì cách hiện tại đáng giá hơn.

**26.** Handler `exception<AppException>` bắt, vì Ktor chọn handler theo **kiểu cụ thể nhất**
khớp được. `AppException.NotFound` là con của `AppException` → khớp trước `Throwable`.

**27.** Stacktrace lộ tên package, tên thư viện, phiên bản, đôi khi cả câu SQL và
đường dẫn file trên server — là món quà cho kẻ tấn công. Lập trình viên vẫn xem được
đầy đủ trong log server.

**28.** Vì `PricingPolicy` là **pure function**: cùng input luôn ra cùng output, không đụng
DB, không đụng mạng, không đụng thời gian hệ thống. Nhờ đó `PricingPolicyTest` chạy
trong vài mili giây, không cần khởi động gì. Phần dễ tính sai nhất (tiền) lại thành
phần dễ test nhất.

---

## 🟣 CẤP 3 — Ktor

**29.**
1. Gradle biên dịch code, chạy class `io.ktor.server.netty.EngineMain` (khai báo ở `application { mainClass }`).
2. `main()` gọi `EngineMain.main(args)`.
3. EngineMain tìm `application.yaml` (hoặc `.conf`) trong resources.
4. Đọc `ktor.deployment.port` → 8080, đọc `ktor.application.modules`.
5. Dùng reflection gọi `com.vehiclerental.ApplicationKt.module`.
6. Trong `module()`: kết nối DB → cài Koin → cài plugin → khai báo route → seed.
7. Netty bắt đầu lắng nghe cổng 8080.

**30.** Kotlin cho phép viết hàm ở ngoài class. Khi biên dịch sang bytecode JVM
(vốn bắt buộc mọi hàm phải nằm trong class), Kotlin gom các hàm top-level của file
`Application.kt` vào một class tên `ApplicationKt`. Đổi tên file thành `Main.kt`
→ phải sửa `application.yaml` thành `com.vehiclerental.MainKt.module`, nếu không
app sẽ chết lúc khởi động với `ClassNotFoundException`.

**31.** Nó tự chuyển JSON ↔ data class theo header `Content-Type`/`Accept`.
Gỡ ra thì `call.receive<RegisterRequest>()` ném lỗi
`Cannot transform this request's content` (lúc **chạy**, không phải lúc build),
và `call.respond(dto)` cũng không biết serialize thế nào.

**32.** Plugin `kotlin("plugin.serialization")` thấy annotation này sẽ **sinh sẵn code**
đọc/ghi JSON cho class đó ngay lúc biên dịch (không dùng reflection → nhanh hơn Jackson/Gson).
Quên annotation: lỗi lúc **biên dịch** (`Serializer has not been found for type ...`) —
đó là điều tốt, sai là biết ngay.

**33.** `String`, `Int`, `Boolean`… đã có serializer dựng sẵn trong thư viện.
`LocalDateTime` là lớp của Java, không có annotation `@Serializable`, và kotlinx cũng
không thể tự đoán bạn muốn format nào (ISO? timestamp? có timezone không?).
Nên bạn phải tự khai báo — xem `util/Serializers.kt`.

**34.** JSON number ở nhiều ngôn ngữ (đặc biệt JavaScript) là **double 64-bit**.
Truyền số tiền lớn hoặc có phần lẻ qua double sẽ bị làm tròn sai. Truyền chuỗi
thì con số đến tay client **nguyên vẹn từng chữ số**, client tự parse sang kiểu decimal.

**35.** Khớp với tên đặt trong `plugins/Security.kt`: `jwt("auth-jwt") { ... }`.
Gõ nhầm → Ktor ném exception **ngay lúc khởi động**
(`Authentication provider auth-jwtt was not registered`), không phải đợi có request.
Đây là kiểu lỗi "fail fast" rất đáng quý.

**36.** Chạy **sau**. Ktor kiểm chữ ký + issuer + audience + hạn dùng trước
(bằng `verifier`), qua được mới gọi `validate` để bạn kiểm tra thêm ở tầng nghiệp vụ
(ví dụ: user có bị khóa không). Trả `null` → client nhận **401**, và khối `challenge`
của mình sẽ trả JSON lỗi thay vì body rỗng.

**37.**
- **401 Unauthorized** = "tôi không biết bạn là ai" — thiếu token, token sai, token hết hạn,
  hoặc sai mật khẩu lúc login. Ném bởi `requireUser()` và `AuthService.login()`.
- **403 Forbidden** = "tôi biết bạn là ai, nhưng bạn không đủ quyền". Ném bởi
  `requireAdmin()` và `BookingService.ensureCanAccess()`.

**38.** Từ **token** (claim `userId`). Ưu điểm: không tốn một query DB cho mỗi request →
rất nhanh, và server không cần lưu session (dễ scale ra nhiều máy).
Nhược điểm: thông tin trong token là **ảnh chụp lúc đăng nhập**. Nếu admin hạ quyền
một user, token cũ vẫn còn quyền cho tới khi hết hạn (24h). Khắc phục: token ngắn hạn
+ refresh token, hoặc danh sách token bị thu hồi.

**39.** Chọn `/my`. Ktor xây route thành cây và ưu tiên **đoạn hằng** (`my`) cao hơn
**đoạn tham số** (`{id}`), bất kể thứ tự bạn khai báo trong code. (Ở Express.js thì
thứ tự lại quan trọng — đừng mang thói quen đó sang.)

**40.** `configureRouting()` là extension trên `Application` — nơi Koin đã sẵn sàng,
nên `by inject()` dùng được. `authRoutes` là extension trên `Route`, chỉ là hàm khai báo
đường dẫn; nhận service qua tham số giúp nó **không phụ thuộc vào Koin** →
trong test có thể gọi trực tiếp với một service giả. Ranh giới: **chỗ lắp ráp thì inject,
chỗ được lắp ráp thì nhận qua tham số.**

---

## 🟠 CẤP 4 — DB & Coroutines

**41.** Bảng `users` chỉ có **một** trong toàn hệ thống, không có lý do gì tạo 2 thể hiện.
`object` còn cho phép viết `Users.email` ở mọi nơi mà không cần truyền đối tượng đi khắp nơi.

**42.** `uniqueIndex()` tạo **UNIQUE constraint** trong DB — DB sẽ từ chối insert email trùng.
Kiểm tra trong code **không thừa**, vì hai lớp phục vụ hai mục đích khác nhau:
- Code check → trả `409` + thông báo tiếng Việt dễ hiểu.
- DB constraint → lưới an toàn cuối cùng, chặn cả khi có race condition hoặc khi
  ai đó insert thẳng vào DB.

Bỏ check trong code thì user nhận `500` xấu xí. Bỏ constraint DB thì có ngày dữ liệu bẩn.

**43.** Nó lưu **tên enum dạng chuỗi** ("PENDING") vào cột varchar(20), thay vì lưu số thứ tự.
Đổi `PENDING` → `WAITING`: dữ liệu cũ trong DB vẫn là chuỗi `"PENDING"`, và khi đọc lên
Exposed gọi `BookingStatus.valueOf("PENDING")` → ném `IllegalArgumentException`.
👉 Muốn đổi tên enum thì phải chạy kèm câu `UPDATE bookings SET status='WAITING' WHERE status='PENDING'`.
Đây là bẫy rất hay gặp khi làm task thật.

**44.** `double` lưu số theo hệ nhị phân nên không biểu diễn chính xác được số thập phân hệ 10.
Ví dụ kinh điển: `0.1 + 0.2 == 0.30000000000000004`. Với tiền, sai một xu nhân lên
hàng nghìn giao dịch là lệch sổ. `decimal(12,2)` lưu chính xác 12 chữ số, 2 số lẻ.

**45.**
- `<T>` = **generic**: hàm trả về đúng kiểu mà `block` trả về. Nhờ vậy `dbQuery { ... }`
  trả `User?` khi block trả `User?`, trả `List<Vehicle>` khi block trả list.
- `block: suspend () -> T` = tham số là một **hàm** không nhận gì, trả `T`, và có thể suspend
  (**higher-order function**).
- `Dispatchers.IO` = pool thread dành riêng cho tác vụ chờ I/O (đọc file, gọi DB, gọi mạng).
  JDBC là API **blocking** — nó chặn cứng thread khi chờ DB trả lời.

**46.** `suspend fun` có thể **tạm dừng rồi chạy tiếp** mà không giữ thread. Trong lúc chờ DB,
thread được trả về để phục vụ request khác. Repository là `suspend` vì mọi thao tác DB
đều là chờ I/O — đúng chỗ cần nhường thread nhất.

**47.** Netty chỉ có vài chục thread xử lý request. Gọi JDBC blocking trực tiếp trên đó →
mỗi request đang chờ DB **giữ chặt** một thread. Vài chục request đồng thời là hết thread,
server đứng hình, request mới không được nhận dù CPU đang rảnh. Đây là bug hiệu năng
kinh điển và rất khó nhận ra khi test một mình.

**48.** `?.let { }` chỉ chạy block khi giá trị **khác null**. Nên `filter.status` null thì
điều kiện đó không được thêm vào SQL; khác null thì `andWhere` nối thêm `AND status = ?`.
Kết quả: một hàm sinh ra được 16 câu SQL khác nhau tùy client truyền gì —
mà không cần một dòng `if` nào lồng nhau, cũng không cần nối chuỗi SQL (→ miễn nhiễm SQL injection).

**49.** JOIN lấy xe **và** tên loại xe trong **một** câu SQL. Nếu không JOIN, bạn sẽ
lặp qua 10 chiếc xe và query tên loại 10 lần → **1 query lấy danh sách + N query chi tiết = N+1**.
Với 100 xe là 101 lần đi lại database. Đây là nguyên nhân chậm phổ biến nhất trong ứng dụng CRUD.

**50.** `this[column]` ném exception nếu cột không có trong kết quả. `findById` và `findAll`
có JOIN nên có `VehicleTypes.name`, nhưng nếu mai bạn viết một query **không** JOIN mà vẫn
gọi `toVehicle()` thì `[...]` sẽ nổ. `getOrNull()` trả `null` → hàm map dùng lại được
cho cả hai loại query. Đúng tinh thần `typeName: String?` bên domain model.

**51.** Hai khoảng `[aStart, aEnd)` và `[bStart, bEnd)` giao nhau ⟺
`aStart < bEnd  AND  aEnd > bStart`.
Chỉ cần 2 điều kiện vì mọi kiểu chồng lấn (A bọc B, B bọc A, A lấn đầu B, A lấn đuôi B)
đều thỏa cả hai; còn hai trường hợp **không** giao (A hoàn toàn trước B, A hoàn toàn sau B)
thì mỗi trường hợp vi phạm đúng một điều kiện. Thử vẽ trục thời gian ra giấy sẽ thấy ngay.

**52.** Vì chúng không còn "giữ chỗ" chiếc xe nữa: `CANCELLED` là đơn đã hủy,
`COMPLETED` là đã trả xe xong. Nếu tính cả chúng thì một chiếc xe từng được thuê
sẽ vĩnh viễn không ai đặt lại được trong khoảng thời gian đó.

---

## 🔴 CẤP 5 — Nghiệp vụ & gỡ lỗi

**53.**
```
PENDING ──confirm──► CONFIRMED ──complete──► COMPLETED
   │                     │
   └──cancel──► CANCELLED ◄──cancel──┘
```
Không thể về `PENDING`. Chặn bởi `BookingService.confirm()` — nó yêu cầu
`booking.status == PENDING`, còn `complete()` yêu cầu `== CONFIRMED`; mọi trạng thái
khác → `409 INVALID_STATE`. Không tồn tại hàm nào set về `PENDING` sau khi đã rời khỏi nó.

**54.** Hai thay đổi: (1) `bookings.status` → `CONFIRMED`, (2) `vehicles.status` → `RENTED`.
Nếu (1) thành công mà (2) hỏng (mất kết nối DB chẳng hạn) → **dữ liệu không nhất quán**:
đơn đã duyệt nhưng xe vẫn hiện AVAILABLE, người khác có thể đặt tiếp.

👉 **ĐÃ ĐƯỢC SỬA trong bản production** (xem `PRODUCTION-CHANGES.md` mục 1.1).
Cách sửa: gói cả hai vào **một transaction** để hoặc cùng thành công, hoặc cùng
bị rollback. Hiện tại mỗi repository tự mở transaction riêng (`dbQuery{}` bọc từng hàm).
Cách làm: thêm vào `BookingService`
```kotlin
DatabaseFactory.dbQuery {
    bookingRepository.updateStatusInTx(id, CONFIRMED)   // phiên bản KHÔNG tự mở transaction
    vehicleRepository.updateStatusInTx(booking.vehicleId, RENTED)
}
```
(Đây chính là **bài tập 12** trong `BAI-TAP.md`.)

**55.** Kịch bản: A và B cùng gửi request đặt xe 5 cho ngày 1–3 lúc 10:00:00.000.
- A chạy `hasOverlap()` → chưa có đơn nào → `false`.
- B chạy `hasOverlap()` → A **chưa insert xong** → cũng `false`.
- A insert. B insert. → Hai đơn trùng lịch cùng tồn tại.

Khoảng thời gian giữa "kiểm tra" và "ghi" gọi là **TOCTOU** (time-of-check to time-of-use).

👉 **ĐÃ ĐƯỢC SỬA trong bản production** bằng hướng 1 (khóa dòng), cộng thêm một
chi tiết mà đáp án gốc bỏ sót: phải dùng mức cô lập `READ_COMMITTED`, vì
`REPEATABLE_READ` làm khóa dòng trở nên vô dụng. Xem `PRODUCTION-CHANGES.md` mục 1.2.

Hai hướng khắc phục:
1. **Khóa ở DB**: `SELECT ... FOR UPDATE` trên dòng xe trước khi kiểm tra, buộc B phải chờ A xong.
2. **Ràng buộc ở DB**: dùng exclusion constraint theo khoảng thời gian
   (PostgreSQL có `EXCLUDE USING gist`), để DB tự từ chối đơn thứ hai.
   Rồi bắt lỗi đó và trả 409.

(Hướng 3 đơn giản hơn cho pet project: một `Mutex` theo `vehicleId` trong bộ nhớ —
nhưng chỉ đúng khi chạy **một** instance server.)

**56.** Từ 8:00 ngày 1 đến 9:00 ngày 2 = **25 giờ** → `ceil(25/24) = 2` ngày.
Tiền: 700.000 × 2 = **1.400.000 đ**. Tiền cọc 30% = 420.000 đ.
👉 Đây là quyết định **nghiệp vụ**, không phải bug: bên cho thuê tính theo ngày.
Nếu khách phàn nàn thì phải đổi `PricingPolicy`, không phải sửa `BookingService`.

**57.** Nếu báo "email không tồn tại" thì kẻ tấn công có thể **dò xem email nào đã đăng ký**
(user enumeration) — dùng để lừa đảo có mục tiêu hoặc thử mật khẩu hàng loạt.
Trả cùng một thông báo thì họ không phân biệt được.

**58.** Thứ tự kiểm tra:
1. Đọc `errorCode` trong body lỗi — `BOOKING_OVERLAP` hay `VEHICLE_UNAVAILABLE`? Hai nguyên nhân hoàn toàn khác nhau.
2. Nếu `VEHICLE_UNAVAILABLE` → mở `BookingService.create()`: xe đang `MAINTENANCE`. Kiểm tra `GET /api/vehicles/{id}`.
3. Nếu `BOOKING_OVERLAP` → mở `BookingRepositoryImpl.hasOverlap()`. Nhớ: **`vehicles.status` và lịch đặt là hai chuyện khác nhau** — xe vẫn `AVAILABLE` khi có đơn `PENDING` chưa được duyệt (chỉ khi `confirm` xe mới thành `RENTED`). Đây gần như chắc chắn là nguyên nhân.
4. Xác minh: `GET /api/admin/bookings?status=PENDING` xem có đơn nào của xe đó trùng khoảng thời gian không.
5. Nếu vẫn không thấy: bật log SQL (`logback.xml`, đổi `<logger name="Exposed" level="DEBUG"/>`) và đọc câu SQL thật.
6. Kiểm tra múi giờ / định dạng `startAt`, `endAt` client gửi lên.

**59.** `GET /api/vehicles/{id}/bookings` (admin):

| File | Sửa gì |
|---|---|
| `repository/BookingRepository.kt` | Thêm `suspend fun findByVehicle(vehicleId: Long): List<Booking>` vào **interface**, rồi implement bằng `Bookings.selectAll().where { Bookings.vehicleId eq vehicleId }` |
| `service/BookingService.kt` | Thêm `suspend fun listByVehicle(vehicleId: Long)`; kiểm tra xe có tồn tại không (`vehicleRepository.findById` → `NotFound`), rồi map sang DTO |
| `routes/VehicleRoutes.kt` *hoặc* `BookingRoutes.kt` | Thêm route trong khối `authenticate("auth-jwt")`, gọi `call.requireAdmin()` |
| `plugins/Routing.kt` | Chỉ sửa **nếu** bạn đặt route vào `VehicleRoutes` — vì hàm đó hiện chỉ nhận `vehicleService`, phải truyền thêm `bookingService` |
| `src/test/.../ApiIntegrationTest.kt` | Thêm test: admin gọi được, khách gọi bị 403, xe không tồn tại → 404 |
| `api.http`, `README.md` | Thêm ví dụ + một dòng vào bảng API |

👉 **Không** phải sửa `Tables.kt` hay `Models.kt` — không có dữ liệu mới nào cả.

**60.** Thêm `licenseNumber` vào user, bắt buộc khi đăng ký:

| File | Sửa gì |
|---|---|
| `db/table/Tables.kt` | Thêm cột `val licenseNumber = varchar("license_number", 30)` vào `Users` |
| `domain/model/Models.kt` | Thêm field vào `data class User` |
| `repository/Mappers.kt` | Thêm dòng map `licenseNumber = this[Users.licenseNumber]` |
| `repository/UserRepository.kt` | Thêm tham số vào **interface** `create(...)` và cả `Impl` |
| `dto/AuthDto.kt` | Thêm vào `RegisterRequest`; cân nhắc có đưa vào `UserResponse` không; sửa `toResponse()` |
| `service/AuthService.kt` | Thêm validate (rỗng? đúng định dạng?), truyền xuống repository |
| `config/DataSeeder.kt` | Hai lệnh `userRepository.create()` bây giờ thiếu tham số → **không biên dịch được** |
| `src/test/...` | Mọi JSON đăng ký trong test đều thiếu field → test đỏ |
| `api.http` | Cập nhật ví dụ |

**Vì sao không chỉ sửa `Models.kt`?** Vì `User` chỉ là một mắt xích. Dữ liệu đi qua
**4 hình dạng** khác nhau: JSON (`RegisterRequest`) → domain (`User`) → cột DB (`Users`)
→ JSON trả về (`UserResponse`). Mỗi hình dạng phải được cập nhật.

👉 **Tin tốt:** trình biên dịch Kotlin sẽ chỉ cho bạn gần hết các chỗ cần sửa —
thêm field không có giá trị mặc định vào `data class` là mọi nơi tạo nó đều báo lỗi đỏ.
Cứ chạy `gradlew build` và sửa từng lỗi cho tới khi xanh. Đó là cách làm task
"đổi model" an toàn nhất, và là lý do dự án này ưu tiên `val` + kiểu tường minh.

👉 **Lưu ý DB thật:** với H2 in-memory thì restart là bảng được tạo lại nên không sao.
Với PostgreSQL bạn còn phải viết migration `ALTER TABLE users ADD COLUMN license_number VARCHAR(30)`,
và phải quyết định giá trị cho các dòng đã tồn tại (cho phép NULL trước, backfill, rồi mới NOT NULL).
