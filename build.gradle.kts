plugins {
    java
    id("org.springframework.boot") version "3.4.4" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("org.flywaydb.flyway") version "11.3.4" apply false
}

allprojects {
    group = "com.bank.origination"
    version = "0.1.0-SNAPSHOT"

    repositories {
        mavenCentral()
    }
}

subprojects {
    apply(plugin = "java")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion = JavaLanguageVersion.of(21)
        }
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}

tasks.register("printLocalRequirements") {
    group = "help"
    description = "Prints the local Java requirement."
    doLast {
        println("Java 21 is required. See README.md for PostgreSQL, Kafka, API, batch, and UI startup.")
    }
}
