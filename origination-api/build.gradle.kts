plugins {
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    id("org.flywaydb.flyway")
}

dependencies {
    implementation(project(":common-domain"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-oauth2-resource-server")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("org.springframework.retry:spring-retry")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
}

flyway {
    url = System.getenv("FLYWAY_URL") ?: "jdbc:postgresql://localhost:5432/card_origination_db"
    user = System.getenv("FLYWAY_USER") ?: "origination_admin"
    password = System.getenv("FLYWAY_PASSWORD") ?: ""
    schemas = arrayOf("public")
    locations = arrayOf("filesystem:src/main/resources/db/migration")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveFileName.set("origination-api.jar")
}
