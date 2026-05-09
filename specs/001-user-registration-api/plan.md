# Implementation Plan: User Registration API

**Branch**: `001-user-registration-api` | **Date**: 2026-05-05 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-user-registration-api/spec.md`

## Summary

Expose a single versioned HTTP endpoint `POST /api/v1/users` that accepts a
JSON body with five required string fields (`nombre`, `apellido`,
`direccion`, `telefono`, `correo`), validates them, enforces case-insensitive
uniqueness on `correo`, generates a UUID, stores the user in an in-memory
registry, and returns `201 Created` with the stored payload. Validation
failures and duplicate-email attempts return `400` with an RFC 9457
**Problem Details** body whose human-readable strings are in Spanish.

Technical approach (Spring Boot 4.0 + Java 25):
- Strict N-layer packages per Constitution Principle I (`controller`,
  `service`, `domain`, `repository`, `dto`, `exceptions`, plus `aop`).
- Bean Validation (`jakarta.validation`) on the request DTO drives FR-003
  / FR-004; a service-level uniqueness check drives FR-005 / FR-006.
- In-memory store: `ConcurrentHashMap<UUID, User>` plus an auxiliary
  `ConcurrentHashMap<String, UUID>` for atomic email-uniqueness.
- Centralized `@RestControllerAdvice` produces `ProblemDetail` responses
  for all error paths (Principle IV — only the advice and the AOP package
  may use `@Slf4j`).
- AOP aspects (`spring-boot-starter-aop`) log controller and service
  entry/exit + duration; business code stays free of `log.*` calls.
- Tests: JUnit 5 + Mockito for service unit tests; `@WebMvcTest` for
  controller slice tests; `@SpringBootTest` + `MockMvc` for end-to-end
  functional tests. Both unit and functional tests are mandatory per
  Principle II.

## Technical Context

**Language/Version**: Java 25 (toolchain pinned in `build.gradle`)
**Primary Dependencies**: Spring Boot 4.0.6 (`spring-boot-starter-webmvc`,
add `spring-boot-starter-validation`, add `spring-boot-starter-aop`),
Lombok (compileOnly + annotationProcessor)
**Storage**: In-memory only — `ConcurrentHashMap`. No database, no file
persistence (FR-010, AS-006). Data is lost on restart by design.
**Testing**: JUnit 5 + Mockito (unit, `*.unit` packages) +
`@WebMvcTest` / `@SpringBootTest` + `MockMvc` (functional, `*.functional`
packages). `spring-boot-starter-webmvc-test` is already present.
**Target Platform**: JVM 25 server (Linux/macOS dev box, container in any
JVM-compatible host)
**Project Type**: web-service (single Spring Boot backend, no frontend)
**Performance Goals**: SC-005 — successful registration p95 < 200 ms over
100 sequential requests on a developer-class machine
**Constraints**:
- Constitutional: N-layer boundaries, AOP-only logging, `/api/v1/...`
  prefix, RFC 9457 Problem Details body, Spanish error text, SOLID/DRY/
  YAGNI.
- Functional: case-insensitive trimmed email uniqueness; multiple
  missing-field errors must be reported in a single response.
**Scale/Scope**: 1 endpoint, 1 entity, single-process in-memory store,
~10 production classes + ~10 test classes.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Constitution version pinned: **1.0.0** (`.specify/memory/constitution.md`).

| # | Principle | Plan compliance |
|---|---|---|
| I | N-Layer Architecture (NON-NEGOTIABLE) | PASS — packages `controller`, `service`, `domain`, `repository`, `dto`, `exceptions`, `aop` created under `com.cat.user.service.*`. Controller depends only on service+dto+exceptions; service depends on repository+domain+exceptions; no JPA entity exposed at controller boundary (no JPA at all). Mapper kept in `dto` package. |
| II | Test Discipline: Unit + Functional (NON-NEGOTIABLE) | PASS — every service method gets ≥1 happy + ≥1 failure unit test (Mockito). Every endpoint gets ≥1 success + ≥1 error functional test (`MockMvc`). Tests live under `src/test/java` mirroring production packages, in `*.unit` / `*.functional` subpackages. `./gradlew test` is the gate. |
| III | API Versioning (NON-NEGOTIABLE) | PASS — endpoint is `/api/v1/users`. Centralized `ApiVersions.V1 = "/api/v1"` constant in the controller package. No unversioned endpoint exists. |
| IV | Centralized Logging via AOP (NON-NEGOTIABLE) | PASS — `LoggingAspect` (controller-entry/exit) + `ServiceLoggingAspect` (service-entry/exit + duration) + exception logging inside the `@RestControllerAdvice`. Business code (service, domain, repository) MUST NOT use `log.*`. Reviewer enforces; CI grep gate listed in research.md as a follow-up nice-to-have. |
| V | SOLID, DRY, YAGNI (NON-NEGOTIABLE) | PASS — `UserService` interface (DIP) with one impl; constructor injection only (Lombok `@RequiredArgsConstructor`); single endpoint, single entity, no speculative GET/PUT/DELETE; no premature abstractions. |

**Result**: All gates PASS. No entries needed in Complexity Tracking.

## Project Structure

### Documentation (this feature)

```text
specs/001-user-registration-api/
├── plan.md              # This file
├── spec.md              # Feature spec (already exists)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── openapi.yaml     # Phase 1 output (HTTP contract)
├── checklists/
│   └── requirements.md  # Spec quality checklist (already exists)
└── tasks.md             # NOT created by /speckit.plan
```

### Source Code (repository root)

```text
src/
├── main/
│   ├── java/com/cat/user/service/
│   │   ├── UserServiceApplication.java           # already exists
│   │   ├── controller/
│   │   │   ├── ApiVersions.java                  # constants for "/api/v1"
│   │   │   └── UserController.java               # POST /api/v1/users
│   │   ├── service/
│   │   │   ├── UserService.java                  # interface (DIP)
│   │   │   └── UserServiceImpl.java              # business logic only
│   │   ├── domain/
│   │   │   └── User.java                         # domain entity (UUID + 5 fields)
│   │   ├── repository/
│   │   │   ├── UserRepository.java               # interface
│   │   │   └── InMemoryUserRepository.java       # ConcurrentHashMap impl
│   │   ├── dto/
│   │   │   ├── UserRequest.java                  # @NotBlank-validated request
│   │   │   ├── UserResponse.java                 # 201 body
│   │   │   └── UserMapper.java                   # request↔domain↔response
│   │   ├── exceptions/
│   │   │   ├── DuplicateUserException.java
│   │   │   └── ApiExceptionHandler.java          # @RestControllerAdvice (Spanish ProblemDetail)
│   │   └── aop/
│   │       ├── LoggingAspect.java                # controller entry/exit
│   │       └── ServiceLoggingAspect.java         # service entry/exit + duration
│   └── resources/
│       └── application.yaml                      # already exists
└── test/
    └── java/com/cat/user/service/
        ├── UserServiceApplicationTests.java      # context-loads (already exists)
        ├── service/unit/
        │   └── UserServiceImplTest.java          # Mockito unit tests
        ├── repository/unit/
        │   └── InMemoryUserRepositoryTest.java   # plain JUnit unit tests
        ├── controller/functional/
        │   └── UserControllerWebMvcTest.java     # @WebMvcTest slice
        └── functional/
            └── UserRegistrationE2ETest.java      # @SpringBootTest + MockMvc
```

**Structure Decision**: Single Spring Boot project using the standard
Maven/Gradle layout. No multi-module split — the feature is small enough
that one module honors YAGNI (Principle V). Layer separation is enforced
by package boundaries (Principle I), not by module boundaries.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No constitutional violations to track. Section intentionally empty.
