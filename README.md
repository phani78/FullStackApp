# Bank Credit Card Origination System

Local Windows 10 development monorepo for a credit-card application workflow. It uses Java 21, Gradle Kotlin DSL, portable PostgreSQL, native Kafka in KRaft mode, Spring Boot, React, Redux Toolkit/RTK Query, and Playwright. No containers are required.

> **Demonstration only.** Product pricing, scoring rules, mock personas, notices, and all stored data are synthetic assumptions. Do not enter real SSNs, dates of birth, customer data, or production credentials. Obtain Legal/Compliance, fair-lending, security, and product approval before any real use.

## Modules

| Path | Purpose |
|---|---|
| `common-domain` | DTOs, event contracts, statuses, security constants, and PII masking |
| `origination-api` | Spring REST API, stateless JWT/RBAC, PostgreSQL/Flyway, mock adapters, outbox, Kafka underwriting, and review workbench |
| `origination-batch` | Restartable Spring Batch embossing, bureau report, expiry, and adverse-action notice preparation |
| `origination-ui` | React 19 + TypeScript + Vite + Tailwind + Redux Toolkit + RTK Query |
| `e2e-tests` | Playwright API and browser tests with synthetic identities |

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for diagrams, policy assumptions, database model, event/API contracts, RBAC, and sign-off items.

## Prerequisites

- Windows 10 PowerShell 5.1 or later
- Java 21 (`java -version`)
- Gradle Wrapper (included; downloads Gradle 8.14.7 on first use)
- Node.js 20+ and npm
- Portable PostgreSQL Windows binaries containing `initdb.exe`, `pg_ctl.exe`, and `psql.exe`
- Apache Kafka Windows distribution (3.9+ recommended), running in native KRaft mode

Keep PostgreSQL and Kafka data directories under the ignored workspace `data\` folder, or set explicit paths. Do not commit credentials or local PII.

## 1. Configure local secrets

In the PowerShell window from which API and batch processes will be launched, create one shared development JWT key and SSN hash salt. These are process-scoped and must match across both backend processes.

```powershell
$jwtBytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($jwtBytes)
$env:JWT_SECRET_BASE64 = [Convert]::ToBase64String($jwtBytes)

$saltBytes = New-Object byte[] 48
$rng.GetBytes($saltBytes)
$env:SSN_HASH_SALT = [Convert]::ToBase64String($saltBytes)
$rng.Dispose()
```

The PostgreSQL setup script prompts securely for three distinct passwords: the temporary-use PostgreSQL bootstrap superuser (`postgres`), Flyway schema owner (`origination_admin`), and DML-only runtime user (`origination_svc_user`). The script does not print passwords.

## 2. Initialize portable PostgreSQL and apply migrations

Download and extract the PostgreSQL Windows binaries ZIP from the official PostgreSQL Windows distribution. Example extraction layout:

```text
C:\Tools\PostgreSQL\pgsql\bin\initdb.exe
C:\Tools\PostgreSQL\pgsql\bin\pg_ctl.exe
C:\Tools\PostgreSQL\pgsql\bin\psql.exe
```

Set the portable binary path and run the bootstrap/migration script:

```powershell
$env:POSTGRES_BIN = 'C:\Tools\PostgreSQL\pgsql\bin'
.\scripts\setup-postgres.ps1
```

The script initializes and starts PostgreSQL on `127.0.0.1:5432`, creates `card_origination_db`, creates `origination_admin` as a non-superuser database/schema owner and `origination_svc_user` as a separate non-superuser with DML-only grants, then runs Flyway V1/V2 migrations and reference-data inserts. It leaves the local service connection settings (`DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`) in the current PowerShell process for the backend commands below.

Useful local checks:

```powershell
& "$env:POSTGRES_BIN\pg_ctl.exe" -D '.\data\postgres' status
& "$env:POSTGRES_BIN\psql.exe" -h 127.0.0.1 -p 5432 -U origination_admin -d card_origination_db -c 'SELECT product_code FROM card_product ORDER BY product_code;'
```

The Flyway owner password is held in `FLYWAY_PASSWORD`; do not print or persist it in shell history. The runtime applications never run Flyway with their DML-only account.

## 3. Initialize and start native Kafka (KRaft)

Download and extract the Apache Kafka binary distribution. In the same PowerShell environment:

```powershell
$env:KAFKA_HOME = 'C:\Tools\Kafka\kafka_2.13-3.9.1'
.\scripts\setup-kafka.ps1
& "$env:KAFKA_HOME\bin\windows\kafka-server-start.bat" '.\data\kafka\server.properties'
```

Keep that terminal open. The script creates a single-node local KRaft configuration on loopback ports 9092/9093 and formats storage once. Local Kafka uses plaintext loopback listeners and is not suitable for network exposure.

## 4. Build and start backend services

Use the PowerShell session where `JWT_SECRET_BASE64`, `SSN_HASH_SALT`, and database environment variables are set:

```powershell
.\gradlew.bat build
.\gradlew.bat :origination-api:bootRun
```

In another terminal, pass the same `JWT_SECRET_BASE64` and `DATABASE_PASSWORD` values (or start the process from the configured shell):

```powershell
.\gradlew.bat :origination-batch:bootRun
```

API: `http://127.0.0.1:8080`  
Batch operations: `http://127.0.0.1:8081`  
Health: `http://127.0.0.1:8080/actuator/health`

The API and batch processes share the JWT signing key, issuer, and audience. Set `JWT_ISSUER`/`JWT_AUDIENCE` consistently if overriding local defaults. `DATABASE_PASSWORD` and `JWT_SECRET_BASE64` are mandatory; `SSN_HASH_SALT` is required by the API.

## 5. Start the frontend

```powershell
Set-Location .\origination-ui
npm.cmd install
npm.cmd run dev
```

Open `http://127.0.0.1:5173`. The login is a **local-only demo token issuer**; it is available only under the local Spring profile and must never be enabled in a production deployment. Select a role to test applicant, underwriter, supervisor, or operations screens.

The application wizard's local persona selector exercises deterministic synthetic responses. It is not available outside the local/test profiles. Never enter real identity values.

## 6. Run unit, embedded broker, batch, and end-to-end tests

Backend tests include focused unit tests, an `@EmbeddedKafka` retry/DLT test, and an `@SpringBatchTest` chunk-processing test:

```powershell
.\gradlew.bat build
```

With PostgreSQL, Kafka, API, batch, and Vite running, install Playwright dependencies and browser:

```powershell
Set-Location ..\e2e-tests
npm.cmd install
npx.cmd playwright install chromium
npm.cmd test
```

Playwright uses only reserved synthetic SSN-shaped test values and synthetic DOBs. It covers prime auto-approval, thin-file manual review and supervisor approval, generic decline/sensitive-validation responses, RBAC, batch launch, and the browser application wizard.

## Configuration reference

| Environment variable | Used by | Description |
|---|---|---|
| `DATABASE_URL` | API, batch, Flyway | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | API, batch | Runtime account; default `origination_svc_user` |
| `DATABASE_PASSWORD` | API, batch | Runtime account secret |
| `FLYWAY_URL` / `FLYWAY_USER` / `FLYWAY_PASSWORD` | setup script | Separate schema-owner migration connection |
| `JWT_SECRET_BASE64` | API, batch | At least 32 random bytes, Base64-encoded |
| `SSN_HASH_SALT` | API | Secret salt used for SHA-256 SSN hashing and request fingerprints |
| `KAFKA_BOOTSTRAP_SERVERS` | API | Default `localhost:9092` |
| `POSTGRES_BIN` | setup script | Portable PostgreSQL `bin` directory |
| `KAFKA_HOME` | Kafka setup script | Extracted Kafka distribution directory |
| `BATCH_OUTPUT_DIRECTORY` | batch | Restricted directory for generated files; default `.\data\outputs` |

