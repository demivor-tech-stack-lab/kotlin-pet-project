# =====================================================================
# MULTI-STAGE BUILD
#
# Chia lam 2 giai doan de image cuoi cung KHONG chua Gradle, khong chua ma nguon,
# khong chua JDK day du - chi con JRE + file jar. Ket qua: nho hon nhieu lan
# va be mat tan cong nho hon (it thu de khai thac hon).
# =====================================================================

# ---------- Giai doan 1: BUILD ----------
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Copy rieng cac file khai bao dependency TRUOC.
# Docker cache theo tung lop: chung nao gradle.properties / build.gradle.kts
# chua doi thi lop tai dependency duoc dung lai => sua code xong build lai
# chi mat vai giay thay vi tai lai toan bo thu vien.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts gradle.properties ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon --quiet || true

# Gio moi copy ma nguon
COPY src ./src

# installDist tao thu muc chay duoc (script + toan bo jar), nhanh hon shadowJar
# va khong can them plugin nao.
RUN ./gradlew installDist --no-daemon -x test

# ---------- Giai doan 2: RUNTIME ----------
FROM eclipse-temurin:21-jre-alpine

# curl phuc vu HEALTHCHECK ben duoi
RUN apk add --no-cache curl tzdata

# KHONG chay bang root.
# Neu ke tan cong thoat duoc ra khoi ung dung, ho se o trong mot tai khoan
# khong co quyen gi thay vi lam chu ca container.
RUN addgroup -S app && adduser -S -G app app

WORKDIR /app
COPY --from=builder --chown=app:app /build/build/install/vehicle-rental-api ./

USER app

EXPOSE 8080

# Tham so JVM cho container:
#  - MaxRAMPercentage: JVM tu tinh heap theo RAM ma container duoc cap,
#    thay vi doan theo RAM cua may chu (nguyen nhan kinh dien gay OOMKilled).
#  - UseContainerSupport: bat nhan dien gioi han cgroup.
#  - user.timezone=UTC: dong ho HE THONG chay UTC; mui gio NGHIEP VU
#    do bien APP_TIMEZONE quyet dinh (xem TimeProvider).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseContainerSupport -Duser.timezone=UTC -Dfile.encoding=UTF-8"

# Docker/compose dua vao ket qua nay de biet container da san sang nhan request chua.
# Dung /health/ready (co kiem tra DB) chu khong phai /health/live.
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/health/ready || exit 1

# Dung dang "exec" de tien trinh java la PID 1 => nhan duoc SIGTERM truc tiep
# tu `docker stop`, nho vay graceful shutdown moi chay.
ENTRYPOINT ["./bin/vehicle-rental-api"]
