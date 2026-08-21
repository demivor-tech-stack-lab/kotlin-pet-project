# 🚀 Triển Khai Lên VPS Bằng Docker Compose

Hướng dẫn đưa API lên một VPS thật, có HTTPS, có backup, có giám sát.

---

## 1. Chuẩn bị VPS

Cấu hình tối thiểu: **2 vCPU, 2 GB RAM, 20 GB SSD**. Ubuntu 22.04/24.04.

```bash
# --- Cài Docker (script chính thức) ---
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
docker compose version   # phải ra v2.x

# --- Tường lửa: CHỈ mở 22, 80, 443 ---
sudo ufw allow OpenSSH
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw enable
```

> ⚠️ **Không mở cổng 8080 và 5432 ra ngoài.** `docker-compose.yml` đã bind chúng vào
> `127.0.0.1` nên chúng chỉ truy cập được từ chính VPS. Đây là điểm sai phổ biến nhất
> khi tự quản VPS: mở cổng PostgreSQL ra Internet, vài giờ sau là có người dò được mật khẩu.

---

## 2. Lấy mã nguồn và tạo file bí mật

```bash
git clone https://github.com/demivor-tech-stack-lab/kotlin-pet-project.git
cd kotlin-pet-project

cp .env.example .env
```

Sinh các bí mật (**đừng tự nghĩ chuỗi trong đầu — máy sinh ngẫu nhiên an toàn hơn nhiều**):

```bash
openssl rand -base64 48   # -> JWT_SECRET
openssl rand -base64 32   # -> POSTGRES_PASSWORD
openssl rand -hex 32      # -> METRICS_TOKEN
```

Sửa `.env`:

```dotenv
POSTGRES_PASSWORD=<chuỗi vừa sinh>
JWT_SECRET=<chuỗi vừa sinh>
CORS_ALLOWED_HOSTS=app.tenmien.com
METRICS_TOKEN=<chuỗi vừa sinh>
```

```bash
chmod 600 .env     # chỉ chủ sở hữu đọc được
```

---

## 3. Khởi động

```bash
docker compose up -d --build

docker compose ps          # cả hai container phải ở trạng thái "healthy"
docker compose logs -f api
```

Kiểm tra ngay tại VPS:

```bash
curl localhost:8080/health/ready
# {"status":"UP","checks":{"database":"UP"}}
```

**Nếu container `api` khởi động rồi tắt ngay:** đó thường là **chủ ý** — `AppConfig`
từ chối chạy với cấu hình không an toàn. Đọc log để biết thiếu gì:

```bash
docker compose logs api | grep -A 10 "Cấu hình không hợp lệ"
```

---

## 4. HTTPS bằng Caddy (đơn giản nhất)

Caddy tự xin và tự gia hạn chứng chỉ Let's Encrypt, không cần cấu hình gì thêm.

`/etc/caddy/Caddyfile`:

```caddyfile
api.tenmien.com {
    reverse_proxy 127.0.0.1:8080

    # /metrics lộ thông tin nội bộ -> chặn từ Internet.
    # Muốn xem thì SSH vào VPS rồi curl localhost, hoặc dùng SSH tunnel.
    @metrics path /metrics
    respond @metrics 404

    header {
        -Server
    }
}
```

```bash
sudo apt install -y caddy
sudo systemctl reload caddy
```

<details>
<summary>Dùng nginx thay cho Caddy</summary>

```nginx
server {
    listen 443 ssl http2;
    server_name api.tenmien.com;

    ssl_certificate     /etc/letsencrypt/live/api.tenmien.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.tenmien.com/privkey.pem;

    location /metrics { return 404; }

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host              $host;
        proxy_set_header X-Real-IP         $remote_addr;
        # Bắt buộc: nếu thiếu, app thấy mọi request đến từ IP của nginx
        # -> rate limit chặn nhầm toàn bộ người dùng như thể họ là một người.
        proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```
</details>

---

## 5. Tạo tài khoản admin đầu tiên

`SEED_DATA=false` ở production nên **không có tài khoản mặc định** (đó là chủ ý —
tài khoản `admin/matkhau123` mà lên production thì coi như mở toang cửa).

Cách làm: đăng ký một tài khoản bình thường qua API, rồi nâng quyền bằng SQL.

```bash
curl -X POST https://api.tenmien.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@tenmien.com","password":"<mật khẩu mạnh>","fullName":"Quản trị","phone":"0912345678"}'

docker compose exec db psql -U vehiclerental -d vehiclerental \
  -c "UPDATE users SET role='ADMIN' WHERE email='admin@tenmien.com';"
```

---

## 6. Cập nhật phiên bản mới

```bash
git pull
docker compose up -d --build
```

Điều gì xảy ra khi chạy lệnh trên:

1. Image mới được build.
2. Container `api` cũ nhận **SIGTERM** → Ktor ngừng nhận request mới, xử lý nốt
   request đang chạy (`shutdownGracePeriod`), đóng connection pool.
3. Container mới khởi động, **Flyway tự chạy migration còn thiếu**.
4. Healthcheck xanh thì mới coi là chạy được.

> Có downtime vài giây. Muốn zero-downtime thì cần chạy 2 bản sao + rolling update,
> nhưng với một VPS thì vài giây gián đoạn khi deploy là chấp nhận được.

---

## 7. Backup database — làm NGAY, đừng để mai

Backup chỉ có giá trị khi bạn **đã từng phục hồi thử thành công**. Một file backup
chưa bao giờ được kiểm tra thì chỉ là niềm tin, không phải bản sao lưu.

`/opt/backup-db.sh`:

```bash
#!/bin/bash
set -euo pipefail

BACKUP_DIR=/opt/backups
cd /home/$USER/kotlin-pet-project
mkdir -p "$BACKUP_DIR"

FILE="$BACKUP_DIR/vehiclerental-$(date +%F-%H%M).sql.gz"
docker compose exec -T db pg_dump -U vehiclerental vehiclerental | gzip > "$FILE"

# Chỉ giữ 14 bản gần nhất
ls -1t "$BACKUP_DIR"/*.sql.gz | tail -n +15 | xargs -r rm

echo "Đã backup: $FILE ($(du -h "$FILE" | cut -f1))"
```

```bash
chmod +x /opt/backup-db.sh
crontab -e
# Chạy 3 giờ sáng mỗi ngày
0 3 * * * /opt/backup-db.sh >> /var/log/backup-db.log 2>&1
```

**Phục hồi:**

```bash
gunzip -c /opt/backups/vehiclerental-2026-08-21-0300.sql.gz \
  | docker compose exec -T db psql -U vehiclerental -d vehiclerental
```

> 💡 Backup nằm trên cùng VPS với database thì ổ cứng hỏng là mất cả hai.
> Đồng bộ sang nơi khác (S3, Backblaze B2, một VPS khác) bằng `rclone`.

---

## 8. Giám sát

### Xem metrics an toàn (không phơi ra Internet)

```bash
# Từ máy của bạn, tạo SSH tunnel
ssh -L 8080:localhost:8080 user@vps

# Rồi mở trên máy mình
curl -H "Authorization: Bearer <METRICS_TOKEN>" http://localhost:8080/metrics
```

### Vài chỉ số đáng theo dõi

| Chỉ số | Ý nghĩa | Ngưỡng cần chú ý |
|---|---|---|
| `ktor_http_server_requests_seconds_count{status="5xx"}` | Số lỗi máy chủ | Tăng đột biến |
| `ktor_http_server_requests_seconds_max` | Request chậm nhất | > 2 giây |
| `jvm_memory_used_bytes{area="heap"}` | Bộ nhớ heap | Sát trần liên tục |
| `hikaricp_connections_pending` | Request đang chờ connection | > 0 kéo dài = pool quá nhỏ |

### Tìm lại một sự cố cụ thể

Khi API trả 500, client nhận kèm mã sự cố. Dùng mã đó tra log:

```bash
docker compose logs api | grep "3d144cd1-48dd-4935"
```

Vì mọi dòng log trong một request đều mang cùng `requestId`, bạn thấy được trọn vẹn
diễn biến — thay vì đoán mò giữa hàng nghìn dòng log lẫn lộn của nhiều request.

---

## 9. Danh sách kiểm tra trước khi mở cho người dùng thật

- [ ] `.env` đã có `chmod 600`, và **không** nằm trong Git (`git ls-files .env` phải trống).
- [ ] `JWT_SECRET`, `POSTGRES_PASSWORD` do `openssl rand` sinh, không phải chuỗi tự nghĩ.
- [ ] `curl https://api.tenmien.com/health/ready` trả `UP` qua HTTPS.
- [ ] `curl https://api.tenmien.com/metrics` trả **404** (bị proxy chặn).
- [ ] `sudo ufw status` chỉ mở 22/80/443.
- [ ] Tài khoản admin đã tạo, mật khẩu mạnh, và **không** phải `admin@vehiclerental.com`.
- [ ] Cron backup đã chạy, và **đã phục hồi thử thành công một lần**.
- [ ] `docker compose logs api | grep -i error` sạch sau 24 giờ chạy.
- [ ] Đăng nhập sai 15 lần liên tiếp → nhận `429` (rate limit hoạt động).

---

## 10. Xử lý sự cố

| Triệu chứng | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| `api` restart liên tục | Cấu hình bị `AppConfig` từ chối | `docker compose logs api` — thông báo nói rõ thiếu biến nào |
| `api` không kết nối được `db` | Sai `POSTGRES_PASSWORD` giữa hai service | Cả hai đọc chung `.env`; sửa xong phải `docker compose down && up -d` |
| Flyway báo "checksum mismatch" | Đã sửa nội dung file migration **đã chạy** | Không được sửa file cũ. Tạo file `V3__...sql` mới |
| Mọi người dùng đều bị `429` | Reverse proxy không gửi `X-Forwarded-For` | Thêm `proxy_set_header X-Forwarded-For` (mục 4) |
| Trình duyệt báo lỗi CORS | Domain frontend chưa khai báo | Thêm vào `CORS_ALLOWED_HOSTS`, `docker compose up -d` |
| `OOMKilled` trong `docker ps -a` | Vượt `API_MEMORY_LIMIT` | Tăng `API_MEMORY_LIMIT` trong `.env`, hoặc tìm rò rỉ bộ nhớ qua metrics |
| Ổ cứng đầy | Log Docker hoặc backup phình to | `docker system prune -a`; kiểm tra `/opt/backups` |
