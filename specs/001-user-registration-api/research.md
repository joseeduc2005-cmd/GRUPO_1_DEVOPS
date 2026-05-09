# Phase 0 — Research: User Registration API

**Branch**: `001-user-registration-api`
**Date**: 2026-05-05
**Inputs**: [spec.md](./spec.md), [plan.md](./plan.md), Constitution v1.0.0

The spec was clarified before this phase, so there were no `NEEDS CLARIFICATION`
markers entering Phase 0. This document records the technical decisions taken
to satisfy the spec under the constraints of the project constitution, plus the
alternatives considered for each decision.

## R-001 — RFC 9457 Problem Details in Spring Boot 4.0

**Decision**: Use the built-in `org.springframework.http.ProblemDetail`
(`forStatusAndDetail` + `setProperty("errors", ...)`) returned from a single
`@RestControllerAdvice` (`exceptions.ApiExceptionHandler`). Set
`Content-Type: application/problem+json` automatically by returning
`ResponseEntity<ProblemDetail>` from the advice methods.

**Rationale**:
- Native to Spring Framework 6.x / Spring Boot 3+ and 4.0; no third-party
  dependency.
- Matches FR-004 / FR-006 / FR-011 exactly: standardized envelope with
  custom `errors` extension property.
- Centralized in `exceptions/` so the controller stays free of error-shape
  knowledge (Principle I — N-layer separation).

**Alternatives considered**:
- Custom `ErrorResponse` POJO. Rejected: invents a non-standard schema for
  no benefit; harder to swap to a future Spring `ErrorResponse` type.
- Spring's older `ResponseEntityExceptionHandler.handleMethodArgumentNotValid`
  signature (returning `ResponseEntity<Object>`). Rejected on its own — but
  we WILL extend it for the binding errors path so we get every
  `BindingResult` field automatically (FR-004 requires reporting every
  offending field, not the first).

## R-002 — Bean Validation strategy for required fields

**Decision**: Add `spring-boot-starter-validation` and annotate every field
of `dto.UserRequest` with `@NotBlank` (which covers null, empty, and
whitespace-only — exactly the AC of US3 scenario 3). Add
`@Email(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")` on `correo` to enforce
AS-002's practical email rule. Annotate the controller method with
`@Valid @RequestBody`. Override
`ResponseEntityExceptionHandler.handleMethodArgumentNotValid` to flatten
the `BindingResult` field errors into the `errors` array of the
`ProblemDetail`.

**Rationale**:
- `@NotBlank` is the canonical answer to "required + non-whitespace" — no
  custom validator code, no risk of forgetting an edge case.
- `@Email` with the constrained regex avoids over-strict RFC-5322 checks
  (which Hibernate Validator's default does not enforce anyway) while
  rejecting clearly malformed values per AS-002.
- Bean Validation runs before the controller body, so duplicate-email
  checks (which require service/repo access) only run after structural
  validation. This naturally satisfies the implicit precedence in the
  spec: missing-field errors win over duplicate errors when both apply.

**Alternatives considered**:
- Hand-rolled validation in the service. Rejected: violates DRY and SRP,
  scatters error wording across layers, makes test fixtures larger.
- `@NotEmpty` instead of `@NotBlank`. Rejected: would accept `"   "` as
  valid, violating US3 scenario 3.

## R-003 — Validation order vs duplicate-email check

**Decision**: Bean Validation always runs first. If any field is missing
or blank, the response is the validation `ProblemDetail` and the service
is never invoked. Only when every field is present does the service check
for duplicate `correo` and throw `DuplicateUserException` (caught by the
advice → 400 + duplicate-user `ProblemDetail`).

**Rationale**:
- This is Spring's default request-binding lifecycle; we get it for free.
- It produces predictable, testable behavior: the test for
  "missing nombre + duplicate correo" asserts the validation response,
  not the duplicate response. Documented in
  `UserControllerWebMvcTest.duplicateAttemptWithMissingFieldReportsValidationFirst`.
- No additional code or precedence flag needed (YAGNI).

**Alternatives considered**:
- Aggregate both validation and duplicate errors into one response.
  Rejected: increases complexity, blurs which layer owns which error,
  and the spec does not require it.

## R-004 — Thread-safe in-memory store

**Decision**: `InMemoryUserRepository` keeps two structures:
1. `ConcurrentHashMap<UUID, User> usersById`
2. `ConcurrentHashMap<String, UUID> idByEmailKey` where the key is the
   email after `trim().toLowerCase(Locale.ROOT)`.

Insertion uses `idByEmailKey.putIfAbsent(emailKey, generatedId)`:
- If the returned previous value is non-null, the email is already taken;
  throw `DuplicateUserException`.
- Otherwise, put the user in `usersById`. The two writes are not in a
  single transaction, but readers query `usersById` via the email index,
  so a brief inconsistency (email reserved before user materialized) is
  observable only as "user not yet readable" — never as a duplicate
  acceptance, which is the only invariant the spec enforces (FR-005).

**Rationale**:
- `putIfAbsent` is the canonical atomic check-and-reserve primitive in
  `ConcurrentHashMap`. It satisfies the "two requests in rapid
  succession" edge case without `synchronized`.
- Two-map structure costs O(1) per write and O(1) per email lookup.
- Single-process scope is acceptable per AS-006.

**Alternatives considered**:
- Single map with `synchronized` block around contains-then-put.
  Rejected: coarser locking, no functional benefit.
- Insert into `usersById` first then check email. Rejected: would have to
  roll back on conflict.

## R-005 — UUID generation

**Decision**: `UUID.randomUUID()` (RFC 4122 v4). Generated inside
`UserServiceImpl.register(...)`, never accepted from the request DTO.

**Rationale**:
- v4 is uniformly random, sufficient for SC-004 (no collisions in any
  realistic N).
- Standard JDK call; no extra dependency.

**Alternatives considered**:
- UUID v7 (time-ordered). Rejected: not needed for in-memory store with
  no index or pagination requirement (YAGNI).
- Sequential `long`. Rejected: violates FR-007's UUID requirement.

## R-006 — AOP logging strategy

**Decision**: Add `org.springframework.boot:spring-boot-starter-aop`. Two
aspects in `aop/`:

- `LoggingAspect` — pointcut
  `within(com.cat.user.service.controller..*)` — logs INFO entry with
  HTTP method + path (read from `RequestContextHolder`) and INFO exit
  with status code + duration.
- `ServiceLoggingAspect` — pointcut
  `within(com.cat.user.service.service..*) && !@within(...)` — logs
  DEBUG entry with method name + sanitized arguments (correo masked
  except domain) and DEBUG exit with duration.

Exception logging stays inside `ApiExceptionHandler` (the only other
package allowed to use `@Slf4j` per Constitution Principle IV). Trace
correlation: a `MDC` value `correlationId` is set per request by a tiny
filter (placed in `aop` package as a one-class follow-up) so every log
line in a request shares an id.

**Rationale**:
- Principle IV is non-negotiable: AOP is the only place loggers live.
- Two aspects (controller + service) cover the meaningful boundaries
  without instrumenting domain/repository code that would just be noise.
- A 4-line filter for `MDC` is YAGNI-compliant and dramatically improves
  log readability under load.

**Alternatives considered**:
- One mega-aspect for everything. Rejected: violates SRP.
- Method-level `@Loggable` annotation. Rejected: requires the developer
  to opt in on every method, defeating the centralization goal.

## R-007 — Test architecture

**Decision**:
- **Unit tests** (`*.unit` packages): plain JUnit 5 + Mockito. Mock
  `UserRepository` in `UserServiceImplTest`; assert state via verify and
  return-value checks. `InMemoryUserRepositoryTest` is plain JUnit (no
  Spring context).
- **Functional tests** (`*.functional` packages):
  - `UserControllerWebMvcTest` — `@WebMvcTest(UserController.class)`
    with `@MockBean UserService`. Asserts HTTP status, JSON shape,
    `ProblemDetail` Spanish strings, and that `@Valid` errors are
    aggregated. Fast, focused on the controller slice.
  - `UserRegistrationE2ETest` — `@SpringBootTest` + `MockMvc`. Boots the
    full context (real `InMemoryUserRepository`, real aspects).
    Exercises happy path, duplicate path, missing-field path, malformed
    JSON path. This is the gate for FR-001..FR-011.

**Rationale**:
- Two functional layers (slice + full) catch different defects: the slice
  is fast and isolates controller behavior; the full E2E is slow but
  catches AOP/wiring/serialization regressions that the slice cannot.
- Constitution Principle II requires both unit AND functional tests; this
  satisfies both with the minimum number of files.

**Alternatives considered**:
- Skip the slice tests and rely only on E2E. Rejected: slower feedback
  loop on controller regressions.
- Use `RestAssured` / `WebTestClient`. Rejected: stack is Servlet MVC
  (`spring-boot-starter-webmvc`); `MockMvc` is the idiomatic match and
  already on the test classpath.

## R-008 — Trimming and case-folding policy

**Decision**: Inputs are trimmed (`String.strip()`) inside the service
layer before persistence. The trimmed value is what gets stored AND what
gets returned in the 201 body. `correo` is additionally lowercased only
for the uniqueness key — the original (trimmed but original-case)
`correo` is what the response echoes back. This honors AS-007 (no
output normalization beyond trimming) while satisfying FR-005
(case-insensitive uniqueness).

**Rationale**: Mixing the trim/lowercase logic in the controller would
leak a domain rule into the web layer (Principle I violation). Doing it
in the repository would force every caller to know the normalization
rule. The service layer is the right home.

**Alternatives considered**:
- Lowercase the email for both storage and response. Rejected: violates
  AS-007 and surprises users who registered as `Ana@Example.com`.

## R-009 — Constitutional follow-ups deferred to future iterations

These are not blocking for this feature but are flagged for later work:

- **Static enforcement of "no `log.*` in business code"** (Principle IV).
  A `Checkstyle`/`ArchUnit` rule would belong in this repo. Recorded as a
  follow-up; for now the rule is enforced by code review only.
- **`Sunset` / `Deprecation` headers** (Principle III). Only relevant once
  a `v2` is introduced; trivial to add later.
- **Multi-instance deployment** (AS-006). Out of scope; would require an
  external store (Redis, Postgres) and is the natural next feature.
