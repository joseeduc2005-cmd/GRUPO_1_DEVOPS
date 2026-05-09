# user-service Development Guidelines

Auto-generated from all feature plans. Last updated: 2026-05-05

## Active Technologies

- Java 25 (toolchain pinned in `build.gradle`) + Spring Boot 4.0.6 (`spring-boot-starter-webmvc`, (001-user-registration-api)

## Project Structure

```text
backend/
frontend/
tests/
```

## Commands

# Add commands for Java 25 (toolchain pinned in `build.gradle`)

## Code Style

Java 25 (toolchain pinned in `build.gradle`): Follow standard conventions

## Recent Changes

- 001-user-registration-api: Added Java 25 (toolchain pinned in `build.gradle`) + Spring Boot 4.0.6 (`spring-boot-starter-webmvc`,

<!-- MANUAL ADDITIONS START -->

## Constitution v1.0.0 — NON-NEGOTIABLE rules (always apply)

See `.specify/memory/constitution.md` for the full text.

1. **N-Layer packages** under `com.cat.user.service.*`: `controller`,
   `service`, `domain`, `repository`, `dto`, `exceptions`, `aop`. No
   cross-layer leakage (no JPA entity in controller, no Spring web
   annotation in domain, no repository injected into controller).
2. **Tests**: every service method needs ≥1 happy + ≥1 failure unit
   test (Mockito); every endpoint needs ≥1 success + ≥1 error
   functional test (`@WebMvcTest` and/or `@SpringBootTest`+`MockMvc`).
   `./gradlew build` is the gate.
3. **API versioning**: every public endpoint MUST live under
   `/api/v{N}/...`. Use the `ApiVersions` constants class in the
   `controller` package.
4. **Logging via AOP only**: business code (service, domain, repository,
   dto) MUST NOT contain `log.*`, `System.out`, etc. Loggers belong
   exclusively in `aop/` and `exceptions/`.
5. **SOLID/DRY/YAGNI**: constructor injection only (Lombok
   `@RequiredArgsConstructor`); no speculative abstractions; rule of
   three before extracting duplication.

## Real project layout (overrides the auto-generated `Project Structure` block above)

```
src/main/java/com/cat/user/service/
├── UserServiceApplication.java
├── controller/        # ApiVersions, UserController
├── service/           # UserService (interface) + UserServiceImpl
├── domain/            # User (immutable, no Spring annotations)
├── repository/        # UserRepository + InMemoryUserRepository
├── dto/               # UserRequest, UserResponse, UserMapper
├── exceptions/        # ApiExceptionHandler (@RestControllerAdvice), DuplicateUserException
└── aop/               # LoggingAspect, ServiceLoggingAspect
src/test/java/com/cat/user/service/
├── service/unit/                     # Mockito unit tests
├── repository/unit/                  # Plain JUnit unit tests
├── controller/functional/            # @WebMvcTest slice tests
└── functional/                       # @SpringBootTest + MockMvc end-to-end
```

## Required dependencies (to add to `build.gradle`)

- `org.springframework.boot:spring-boot-starter-validation`
- `org.springframework.boot:spring-boot-starter-aop`

## Error response contract

All `400` responses MUST be RFC 9457 `ProblemDetail` with
`Content-Type: application/problem+json`. Human-readable strings
(`title`, `detail`, `errors[].message`) are in **Spanish**. See
`specs/001-user-registration-api/contracts/openapi.yaml`.

<!-- MANUAL ADDITIONS END -->
