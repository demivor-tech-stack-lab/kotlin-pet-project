// Đọc version từ gradle.properties. `by project` là "delegated property" của Kotlin:
// nó gọi project.property("ktor_version") giúp bạn.
val ktor_version: String by project
val kotlin_version: String by project
val logback_version: String by project
val exposed_version: String by project
val h2_version: String by project
val hikari_version: String by project
val koin_version: String by project
val bcrypt_version: String by project
val flyway_version: String by project
val postgres_version: String by project
val micrometer_version: String by project
val logstash_encoder_version: String by project

plugins {
    kotlin("jvm") version "2.0.0"
    // Bật kotlinx.serialization: cho phép dùng annotation @Serializable
    kotlin("plugin.serialization") version "2.0.0"
    // Plugin của Ktor: tự thêm BOM (nên các dependency ktor bên dưới không cần ghi version)
    // và áp dụng luôn `application` plugin => có task `run`, `installDist`...
    id("io.ktor.plugin") version "2.3.11"
}

group = "com.vehiclerental"
version = "0.0.1"

application {
    // EngineMain đọc cấu hình từ src/main/resources/application.yaml rồi khởi động Netty
    mainClass.set("io.ktor.server.netty.EngineMain")

    val isDevelopment: Boolean = project.ext.has("development")
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=$isDevelopment")
}

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    // ----- Ktor server core -----
    implementation("io.ktor:ktor-server-core-jvm")
    implementation("io.ktor:ktor-server-netty-jvm")        // engine chạy HTTP
    implementation("io.ktor:ktor-server-config-yaml")      // đọc application.yaml

    // ----- Ktor plugins -----
    implementation("io.ktor:ktor-server-content-negotiation-jvm") // tự động JSON <-> data class
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm")
    implementation("io.ktor:ktor-server-status-pages-jvm")        // bắt exception -> JSON lỗi
    implementation("io.ktor:ktor-server-call-logging-jvm")        // log mỗi request
    implementation("io.ktor:ktor-server-auth-jvm")                // khung xác thực
    implementation("io.ktor:ktor-server-auth-jwt-jvm")            // xác thực bằng JWT
    implementation("io.ktor:ktor-server-cors-jvm")                // cho phép gọi từ FE khác domain
    implementation("io.ktor:ktor-server-default-headers-jvm")
    implementation("io.ktor:ktor-server-forwarded-header-jvm")    // doc IP that sau reverse proxy
    implementation("io.ktor:ktor-server-call-id-jvm")             // gan request-id vao moi log
    implementation("io.ktor:ktor-server-rate-limit-jvm")          // chan bruteforce dang nhap
    implementation("io.ktor:ktor-server-metrics-micrometer-jvm")  // /metrics cho Prometheus
    implementation("io.micrometer:micrometer-registry-prometheus:$micrometer_version")

    // ----- Database: Exposed (ORM của JetBrains) + H2 + connection pool -----
    implementation("org.jetbrains.exposed:exposed-core:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposed_version")
    implementation("org.jetbrains.exposed:exposed-java-time:$exposed_version") // cột kiểu LocalDateTime
    implementation("com.h2database:h2:$h2_version")                    // dev/test
    implementation("org.postgresql:postgresql:$postgres_version")      // production
    implementation("org.flywaydb:flyway-core:$flyway_version")         // migration co phien ban
    implementation("com.zaxxer:HikariCP:$hikari_version")

    // ----- Dependency Injection -----
    implementation("io.insert-koin:koin-ktor:$koin_version")
    implementation("io.insert-koin:koin-logger-slf4j:$koin_version")

    // ----- Bảo mật mật khẩu -----
    implementation("at.favre.lib:bcrypt:$bcrypt_version")

    // ----- Log -----
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("net.logstash.logback:logstash-logback-encoder:$logstash_encoder_version") // log JSON cho prod

    // ----- Test -----
    testImplementation("io.ktor:ktor-server-test-host-jvm")
    testImplementation("io.ktor:ktor-client-content-negotiation-jvm")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
}

tasks.test {
    testLogging {
        events("passed", "skipped", "failed")
    }
}
