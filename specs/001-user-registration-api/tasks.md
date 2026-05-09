---

description: "Task list for User Registration API"
---

# Tasks: User Registration API

**Input**: Design documents from `/specs/001-user-registration-api/`
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/openapi.yaml, quickstart.md

**Tests**: REQUIRED. Constitution v1.0.0 Principle II ("Test Discipline:
Unit + Functional") is NON-NEGOTIABLE — every service method needs ≥1
happy + ≥1 failure unit test, every endpoint needs ≥1 success + ≥1
error functional test. Test tasks are therefore included throughout
this list, not optional.

**Organization**: Tasks are grouped by user story (P1: US1 — happy
register; P2: US2 — duplicate; P2: US3 — incomplete) so each story is
independently implementable, testable, and demoable.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks in the same phase)
- **[Story]**: Which user story this task belongs to (US1, US2, US3) — present only on user-story phases
- File paths are absolute or rooted at the repo (`src/main/...`, `src/test/...`)
- Constitution v1.0.0 (`.specify/memory/constitution.md`) governs all tasks

## Path Conventions

- Production code: `src/main/java/com/cat/user/service/<layer>/...`
- Unit tests: `src/test/java/com/cat/user/service/<layer>/unit/...`
- Functional tests (slice + E2E): `src/test/java/com/cat/user/service/<layer>/functional/...` and `src/test/java/com/cat/user/service/functional/...`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization — dependencies and package skeleton — so every later phase compiles.

- [x] T001 Add `org.springframework.boot:spring-boot-starter-validation` and `org.springframework.boot:spring-boot-starter-aop` to the `dependencies { ... }` block in `build.gradle` (research R-002, R-006)
- [x] T002 [P] Create the seven N-layer package directories under `src/main/java/com/cat/user/service/`: `controller`, `service`, `domain`, `repository`, `dto`, `exceptions`, `aop` (each with a `package-info.java` file documenting that layer's role per Constitution Principle I)
- [x] T003 [P] Add `spring.jackson.deserialization.fail-on-unknown-properties: false` to `src/main/resources/application.yaml` so a client-supplied `id` in the request body is silently ignored per FR-009

**Checkpoint**: `./gradlew build` still succeeds; new packages exist; new dependencies resolved.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core building blocks that every user story depends on.

**⚠️ CRITICAL**: No user story work can begin until this phase is complete.

- [x] T004 [P] Create immutable `User` domain entity in `src/main/java/com/cat/user/service/domain/User.java` with fields `UUID id`, `String nombre`, `String apellido`, `String direccion`, `String telefono`, `String correo` (Lombok `@Value` + `@Builder`, no Spring annotations per Constitution Principle I)
- [x] T005 [P] Create `UserRepository` interface in `src/main/java/com/cat/user/service/repository/UserRepository.java` with `User save(User user)` and `Optional<User> findById(UUID id)` (per data-model.md §5)
- [x] T006 [P] Create `DuplicateUserException` (extends `RuntimeException`, carries the offending `correo`) in `src/main/java/com/cat/user/service/exceptions/DuplicateUserException.java`
- [x] T007 [P] Create `UserRequest` DTO **without** validation annotations yet (Lombok `@Getter` + no-arg constructor for Jackson, fields `nombre`, `apellido`, `direccion`, `telefono`, `correo`) in `src/main/java/com/cat/user/service/dto/UserRequest.java` — annotations are added in Phase 5
- [x] T008 [P] Create `UserResponse` DTO in `src/main/java/com/cat/user/service/dto/UserResponse.java` with `UUID id` plus the five user fields (Lombok `@Value` + `@Builder`)
- [x] T009 [P] Create `UserService` interface in `src/main/java/com/cat/user/service/service/UserService.java` exposing `UserResponse register(UserRequest request)` (DIP per Constitution Principle V)
- [x] T010 [P] Create `ApiVersions` constants class in `src/main/java/com/cat/user/service/controller/ApiVersions.java` with `public static final String V1 = "/api/v1";` (Constitution Principle III)
- [x] T011 Implement `InMemoryUserRepository` (depends on T004, T005, T006) in `src/main/java/com/cat/user/service/repository/InMemoryUserRepository.java` annotated `@Repository`; two `ConcurrentHashMap`s (`usersById`, `idByEmailKey`); `save()` uses `putIfAbsent` on `correo.trim().toLowerCase(Locale.ROOT)` and throws `DuplicateUserException` on conflict (research R-004)
- [x] T012 Implement `UserMapper` (depends on T004, T007, T008) in `src/main/java/com/cat/user/service/dto/UserMapper.java` as final class with private constructor and two static methods: `User toDomain(UserRequest req, UUID id)` (trims every field) and `UserResponse toResponse(User user)`
- [x] T013 Create `ApiExceptionHandler` scaffolding (depends on T006) in `src/main/java/com/cat/user/service/exceptions/ApiExceptionHandler.java`: `@RestControllerAdvice` extending `ResponseEntityExceptionHandler`, `@Slf4j`, **no handler methods yet** — handlers are added in their respective user-story phases
- [x] T014 [P] Create `LoggingAspect` (controller entry/exit) in `src/main/java/com/cat/user/service/aop/LoggingAspect.java` — `@Aspect` `@Component` `@Slf4j`; pointcut `within(com.cat.user.service.controller..*)`; `@Around` logs INFO `"--> {method}"` before and `"<-- {method} ({duration}ms)"` after (research R-006)
- [x] T015 [P] Create `ServiceLoggingAspect` (service entry/exit + duration) in `src/main/java/com/cat/user/service/aop/ServiceLoggingAspect.java` — `@Aspect` `@Component` `@Slf4j`; pointcut `within(com.cat.user.service.service..*)`; `@Around` logs DEBUG with method name + masked `correo` argument
- [x] T016 [P] Unit test `InMemoryUserRepository` in `src/test/java/com/cat/user/service/repository/unit/InMemoryUserRepositoryTest.java`: (a) saves and finds by id, (b) second save with case-different + whitespace-padded email throws `DuplicateUserException`, (c) two threads racing the same email produce exactly one stored user (use `CountDownLatch` + small executor) — covers FR-005 + edge case "rapid succession"

**Checkpoint**: Foundation ready; `./gradlew test` runs T016 green; user-story phases can now begin.

---

## Phase 3: User Story 1 — Register a new user successfully (Priority: P1) 🎯 MVP

**Goal**: A consumer can `POST /api/v1/users` with all five required fields and receive `201 Created` plus the stored payload (with a system-generated UUID).

**Independent Test**: `curl -X POST http://localhost:8080/api/v1/users -H 'Content-Type: application/json' -d '{"nombre":"Ana","apellido":"Pérez","direccion":"Calle 1","telefono":"3001234567","correo":"ana@example.com"}'` returns HTTP 201 and a body whose `id` is a UUID v4 and whose other five fields equal the request.

### Implementation for User Story 1

- [x] T017 [P] [US1] Implement `UserServiceImpl` in `src/main/java/com/cat/user/service/service/UserServiceImpl.java` — `@Service`, `@RequiredArgsConstructor`, depends on `UserRepository`; `register()` generates `UUID.randomUUID()`, calls `UserMapper.toDomain(req, id)`, calls `repository.save(user)`, returns `UserMapper.toResponse(saved)`. **NO logger calls** (Constitution Principle IV).
- [x] T018 [US1] Implement `UserController` (depends on T017, T010) in `src/main/java/com/cat/user/service/controller/UserController.java` — `@RestController`, `@RequestMapping(ApiVersions.V1 + "/users")`, `@RequiredArgsConstructor`; method `@PostMapping public ResponseEntity<UserResponse> register(@RequestBody UserRequest request)` returns `ResponseEntity.status(HttpStatus.CREATED).body(userService.register(request))`. **No `@Valid` yet** — added in T027.

### Tests for User Story 1

- [x] T019 [P] [US1] Unit test `UserServiceImpl.register()` happy path in `src/test/java/com/cat/user/service/service/unit/UserServiceImplTest.java`: mock `UserRepository.save(...)` to return the input user; assert response contains a non-null UUID and echoes the trimmed input fields. Also a failure-path test that asserts the exception bubbles up when the repo throws.
- [x] T020 [P] [US1] Functional slice test in `src/test/java/com/cat/user/service/controller/functional/UserControllerWebMvcTest.java` using `@WebMvcTest(UserController.class)` + `@MockBean UserService`: assert `POST /api/v1/users` with valid body returns `201` and JSON body matches the mocked `UserResponse` (id + 5 fields).
- [x] T021 [P] [US1] End-to-end test in `src/test/java/com/cat/user/service/functional/UserRegistrationE2ETest.java` using `@SpringBootTest` + `MockMvc` (real `InMemoryUserRepository`, real aspects): a single `POST /api/v1/users` with valid body returns `201`, body has a UUID-shaped `id`, and a second-call with a different email also returns `201` with a different UUID (covers SC-004 — distinct identifiers).

**Checkpoint**: User Story 1 demoable end-to-end. The MVP is shippable. Note: until US2 lands, a duplicate-email request returns 500 (no handler); until US3 lands, a missing-field request returns 500 — both behaviors are expected gaps and are addressed in their own phases.

---

## Phase 4: User Story 2 — Reject duplicate registration (Priority: P2)

**Goal**: A second `POST /api/v1/users` carrying an already-registered `correo` (in any case / with surrounding whitespace) returns `400` with a Problem Details body whose `title` is `"Usuario ya existente"`.

**Independent Test**: After registering `ana@example.com`, send a second registration with `Ana@Example.com` (or with leading/trailing spaces). Response is `400`, `Content-Type: application/problem+json`, body contains `title: "Usuario ya existente"` and `errors: [{ field: "correo", message: "el correo ya está registrado" }]`. The first stored record remains intact.

### Implementation for User Story 2

- [x] T022 [US2] Add `@ExceptionHandler(DuplicateUserException.class)` method to `ApiExceptionHandler` in `src/main/java/com/cat/user/service/exceptions/ApiExceptionHandler.java`: build `ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "El usuario con el correo proporcionado ya se encuentra registrado.")`, set `title = "Usuario ya existente"`, set `instance` to the request URI, set property `errors = List.of(Map.of("field", "correo", "message", "el correo ya está registrado"))`; return `ResponseEntity.badRequest().contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem)`. Log the exception via `@Slf4j` (only allowed in `exceptions/`, Principle IV).

### Tests for User Story 2

- [x] T023 [P] [US2] Extend `src/test/java/com/cat/user/service/service/unit/UserServiceImplTest.java` (from T019) with a unit test that simulates the repository throwing `DuplicateUserException` on `save()` and asserts that `UserServiceImpl.register()` propagates it unchanged.
- [x] T024 [P] [US2] Extend `src/test/java/com/cat/user/service/controller/functional/UserControllerWebMvcTest.java` (from T020): mock `UserService.register(...)` to throw `DuplicateUserException("ana@example.com")`; assert response is `400`, `Content-Type` is `application/problem+json`, body has `title=="Usuario ya existente"`, `status==400`, and `errors[0].field=="correo"`, `errors[0].message=="el correo ya está registrado"`.
- [x] T025 [US2] Extend `src/test/java/com/cat/user/service/functional/UserRegistrationE2ETest.java` (from T021): register a user, then submit a second request with the same email (one variant case-different, one variant whitespace-padded); assert both produce the duplicate ProblemDetail and that a third lookup-style request (same email again) also still produces the duplicate error (proving the original record was not replaced — US2 AC2).

**Checkpoint**: User Stories 1 AND 2 both work independently end-to-end.

---

## Phase 5: User Story 3 — Reject incomplete registration (Priority: P2)

**Goal**: A `POST /api/v1/users` whose body is missing or has blank values for any of the five required fields returns `400` with a Problem Details body that lists **every** offending field. A body that is not valid JSON also returns `400` with a clear message.

**Independent Test**: Send a body that omits `apellido`, `direccion`, and `telefono` simultaneously. Response is `400`, body has `title: "Validación fallida"` and an `errors` array containing three entries (one per missing field), not just the first one detected. Send a body of `not-json`; response is `400` with `title: "Solicitud inválida"`.

### Implementation for User Story 3

- [x] T026 [US3] Add Bean Validation annotations to every field of `src/main/java/com/cat/user/service/dto/UserRequest.java` (extending T007): `@NotBlank(message = "el campo <name> es obligatorio")` on all five fields, plus `@Email(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "el correo no tiene un formato valido")` on `correo`.
- [x] T027 [US3] Edit `src/main/java/com/cat/user/service/controller/UserController.java` (extending T018): add `@Valid` on the `@RequestBody UserRequest request` parameter so Bean Validation runs before the controller body.
- [x] T028 [US3] In `src/main/java/com/cat/user/service/exceptions/ApiExceptionHandler.java` (extending T022): override `handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request)`; flatten `ex.getBindingResult().getFieldErrors()` into the `errors` array (`{field, message}` per error, deduplicated by field+message); return `ProblemDetail` with `title="Validación fallida"`, `detail="Uno o más campos requeridos no fueron enviados o están vacíos."`.
- [x] T029 [US3] In `src/main/java/com/cat/user/service/exceptions/ApiExceptionHandler.java` (extending T028): override `handleHttpMessageNotReadable(HttpMessageNotReadableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request)`; return `ProblemDetail` with `title="Solicitud inválida"`, `detail="El cuerpo de la petición no es un JSON válido."`, empty `errors` array.

### Tests for User Story 3

- [x] T030 [P] [US3] Extend `src/test/java/com/cat/user/service/controller/functional/UserControllerWebMvcTest.java` (from T024) with three test methods: (a) body omits a single field → `errors[]` contains exactly that field with the Spanish required message; (b) body omits THREE fields → `errors[]` contains three entries (verify all three present, one assertion per field); (c) body has `nombre: "   "` (whitespace) → treated as missing (US3 AC3).
- [x] T031 [US3] Extend `src/test/java/com/cat/user/service/functional/UserRegistrationE2ETest.java` (from T025) with: (a) end-to-end missing-field scenario asserting Spanish ProblemDetail; (b) malformed-JSON scenario (`-d 'not-json'`) returning `title="Solicitud inválida"`; (c) invalid email format (`correo: "not-an-email"`) returning a validation error on `correo` with the format message from T026.

**Checkpoint**: All three user stories independently functional. The full FR-001..FR-011 surface is covered by tests.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Operational polish that touches multiple stories.

- [x] T032 [P] Add a per-request correlation-id `MDC` filter in `src/main/java/com/cat/user/service/aop/CorrelationIdFilter.java` (`@Component` + `OncePerRequestFilter`): set `MDC.put("correlationId", UUID.randomUUID().toString())` before the chain, `MDC.remove(...)` in `finally`. Adjust the log pattern in `src/main/resources/application.yaml` (`logging.pattern.level: "%5p [%X{correlationId}]"`) so every log line in a request shares the id (research R-006).
- [x] T033 Performance smoke test for SC-005 in `src/test/java/com/cat/user/service/functional/UserRegistrationPerformanceTest.java` (`@SpringBootTest` + `MockMvc`): execute 100 sequential `POST /api/v1/users` calls with unique emails inside a `@Test` method; capture per-call duration; assert that the 95th-percentile is < 200 ms. Annotate `@Tag("performance")` so CI can opt-in/opt-out.
- [x] T034 [P] Walk through every curl example in `specs/001-user-registration-api/quickstart.md` against a freshly started `./gradlew bootRun` and confirm each response matches the documented expected output. Record any drift back into either `quickstart.md` or the relevant production code.
- [x] T035 Run `./gradlew build` from a clean state and confirm: compile passes, all tests green (T016, T019, T020, T021, T023, T024, T025, T030, T031, T033), no warnings about missing dependencies. This is the Constitution Principle II merge gate.

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — start immediately.
- **Foundational (Phase 2)**: Depends on Setup. **Blocks every user story.**
- **US1 (Phase 3, P1)**: Depends only on Foundational. MVP-shippable in isolation.
- **US2 (Phase 4, P2)**: Depends on Foundational. Independent of US1 in principle, but US1's tests give the natural setup data; can be implemented in parallel by a different developer once Foundational is done.
- **US3 (Phase 5, P2)**: Depends on Foundational + Phase 4's `ApiExceptionHandler` skeleton (T022 already added the `@RestControllerAdvice`). Independent of US1/US2 functionally; implementation order within US3 is T026 → T027 → T028 → T029 (each builds on the previous).
- **Polish (Phase 6)**: Depends on Phases 3–5. T035 is the very last task.

### Within-Story Order

- US1: T017 → T018 (controller depends on service); tests T019, T020, T021 can be written in parallel against the contracts and run after their target classes compile.
- US2: T022 first (handler), then tests T023, T024, T025 in parallel.
- US3: T026 → T027 → T028 → T029 (linear), then tests T030, T031.

### Parallel Opportunities

- All Phase 1 tasks marked `[P]` (T002, T003) run in parallel with each other after T001.
- All Phase 2 `[P]` tasks (T004, T005, T006, T007, T008, T009, T010, T014, T015, T016) run in parallel; T011 waits on T004+T005+T006; T012 waits on T004+T007+T008; T013 waits on T006.
- Within US1: T017 is `[P]` against T019; T020 and T021 are `[P]` against each other and against T019 (different files).
- Across stories: once Phase 2 is checkpointed, three developers can take US1, US2, and US3 in parallel — they touch different files except for the shared `ApiExceptionHandler.java` (US2 vs US3) and `UserController.java` / `UserRequest.java` (US1 vs US3 add `@Valid` and annotations on top of US1's bare versions). Coordinate those two file points or sequence US3 after US2 finishes T022.

---

## Parallel Example: Phase 2 (Foundational)

```bash
# After T001 lands, launch the independent skeleton tasks together:
Task: "T004 [P] Create User domain entity in src/main/java/com/cat/user/service/domain/User.java"
Task: "T005 [P] Create UserRepository interface in src/main/java/com/cat/user/service/repository/UserRepository.java"
Task: "T006 [P] Create DuplicateUserException in src/main/java/com/cat/user/service/exceptions/DuplicateUserException.java"
Task: "T007 [P] Create UserRequest DTO (no validation yet) in src/main/java/com/cat/user/service/dto/UserRequest.java"
Task: "T008 [P] Create UserResponse DTO in src/main/java/com/cat/user/service/dto/UserResponse.java"
Task: "T009 [P] Create UserService interface in src/main/java/com/cat/user/service/service/UserService.java"
Task: "T010 [P] Create ApiVersions constants in src/main/java/com/cat/user/service/controller/ApiVersions.java"
Task: "T014 [P] Create LoggingAspect in src/main/java/com/cat/user/service/aop/LoggingAspect.java"
Task: "T015 [P] Create ServiceLoggingAspect in src/main/java/com/cat/user/service/aop/ServiceLoggingAspect.java"
```

T011, T012, T013, T016 then run as soon as their inputs are ready.

---

## Implementation Strategy

### MVP First (User Story 1 only)

1. Phase 1 → Phase 2 → Phase 3.
2. **Stop and validate**: run `./gradlew test`, then `./gradlew bootRun` and the curl from quickstart §3.
3. The MVP can be demoed: a consumer can register a complete user and receive a UUID. Duplicates and invalid bodies will return 500 — that is acceptable for MVP since the consumer is, by definition, sending a valid, never-seen-before payload.

### Incremental Delivery

1. Setup + Foundational → foundation green.
2. + US1 → demo MVP.
3. + US2 → demo duplicate handling (the API now refuses a re-registration with a clean Spanish error).
4. + US3 → demo full validation surface (missing fields, malformed JSON, invalid email format).
5. + Polish → correlation id in logs, performance smoke pass, final gate.

### Parallel Team Strategy

1. One developer takes Setup + Foundational and pushes T001..T016.
2. Once Phase 2 checkpoints, three developers split US1, US2, US3:
   - Dev A: US1 (T017–T021).
   - Dev B: US2 (T022–T025) — must coordinate with Dev C on `ApiExceptionHandler`.
   - Dev C: US3 (T026–T031) — must rebase on Dev B's T022 before adding T028, and on Dev A's T018 before adding T027.
3. One developer (or any of the above after their story merges) handles Phase 6.

---

## Notes

- `[P]` tasks touch different files. Tasks listed sequentially (no `[P]`) typically extend a file produced earlier in the same phase.
- `[Story]` label maps a task to its user-story phase for traceability.
- Constitution v1.0.0 is the gate for every PR. Verify Principle IV ("no `log.*` outside `aop/` and `exceptions/`") with `grep -RIn "log\." src/main/java | grep -vE 'aop/|exceptions/'` — should return nothing.
- Stop at any checkpoint to validate the latest story independently.
- Avoid: vague task descriptions, same-file conflicts marked `[P]`, cross-story dependencies that break the independent-test promise.
