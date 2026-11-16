---
mode: 'agent'
description: 'Generate a contract-first Spring Boot REST endpoint with DTOs, MapStruct, JPA, Security, and Tests'
---

Build a complete, enterprise-grade REST API feature based on the user's requirements. Adhere strictly to `.github/copilot-instructions.md` and generate the following layers:

### 1. OpenAPI Specification & DTOs
- **Request/Response DTOs (`com.<org>.<service>.api.dto`):** Create immutable Java `record` classes for Request and Response payloads. Never expose JPA `@Entity` classes directly.
- Apply strict Jakarta Validation (`@NotNull`, `@Size`, `@Pattern`, `@DecimalMin`) on all Request DTOs.
- Annotate all DTOs and Controller methods with Swagger/OpenAPI 3 annotations (`@Schema`, `@Operation`, `@ApiResponses`) documenting HTTP status codes `200`, `201`, `400`, `401`, `403`, `404`, and `422`.

### 2. Controller Layer (`com.<org>.<service>.api.controller`)
- Create a `@RestController` versioned under `/api/v1/<resource>`.
- Use constructor injection only (`private final` fields).
- Secure every endpoint with method-level security (`@PreAuthorize("hasAuthority('SCOPE_<permission>')")`).
- Support idempotency headers (`X-Idempotency-Key`) for state-mutating `POST` or `PATCH` financial endpoints.
- Return `ResponseEntity<T>` with appropriate HTTP status codes and `Location` headers on creation.

### 3. Service & Mapping Layer (`com.<org>.<service>.service` & `mapper`)
- Create a `Service` interface and a `ServiceImpl` class annotated with `@Service`.
- Annotate write methods with `@Transactional` and read methods with `@Transactional(readOnly = true)`.
- Use a **MapStruct** interface (`@Mapper(componentModel = "spring")`) to map between Entities and DTOs.
- Throw domain-specific runtime exceptions (e.g., `ResourceNotFoundException`, `BusinessRuleViolationException`) that are handled by a centralized `@RestControllerAdvice` returning RFC 7807 `ProblemDetail`.

### 4. Persistence Layer (`com.<org>.<service>.domain`)
- Create or update the JPA `@Entity` with auditing fields (`createdAt`, `updatedAt`, `createdBy`, `updatedBy` using Spring Data Auditing) and `@Version` for optimistic locking.
- Create the corresponding `JpaRepository` interface using explicit JPQL queries or Spring Data derived queries with `FetchType.LAZY` on relationships.

### 5. Comprehensive Test Suite
- **Controller Slice Test (`@WebMvcTest`):** Test request validation errors (`400`), missing/unauthorized roles (`401`/`403`), and happy-path JSON serialization (`200`/`201`).
- **Service Unit Test (JUnit 5 + Mockito):** Test business logic, optimistic locking conflicts, and edge cases.
