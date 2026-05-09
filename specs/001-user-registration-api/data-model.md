# Phase 1 — Data Model: User Registration API

**Branch**: `001-user-registration-api`
**Date**: 2026-05-05
**Input**: [spec.md](./spec.md), [research.md](./research.md)

The feature has **one** domain entity (`User`) and **two** transport DTOs
(`UserRequest`, `UserResponse`). The repository contract and the
in-memory implementation strategy are also recorded here for
traceability.

## 1. Domain entity — `User`

Package: `com.cat.user.service.domain`

| Field | Type | Origin | Constraint |
|---|---|---|---|
| `id` | `java.util.UUID` | system-generated | NOT NULL, immutable, unique |
| `nombre` | `String` | request | NOT NULL, non-blank, trimmed |
| `apellido` | `String` | request | NOT NULL, non-blank, trimmed |
| `direccion` | `String` | request | NOT NULL, non-blank, trimmed |
| `telefono` | `String` | request | NOT NULL, non-blank, trimmed |
| `correo` | `String` | request | NOT NULL, non-blank, trimmed, must match practical email regex (AS-002), unique under `trim().toLowerCase(Locale.ROOT)` |

Notes:
- The entity is a Lombok `@Value` (immutable, all-args constructor) with
  `@Builder`. Once constructed it cannot mutate — there are no setters,
  no update flow, and no state machine in this version.
- `correo` is stored as the trimmed original (preserving case the user
  typed); the case-insensitive form is only used as the uniqueness key
  inside the repository.
- The entity contains no Spring or web annotations (Constitution
  Principle I — domain stays framework-light).

### Lifecycle

```
                     register()
   (no instance) ─────────────────► CREATED (only state)
```

There is no UPDATE, DELETE, or ARCHIVED state. Once created, the user
exists for the lifetime of the JVM process (FR-010, AS-006).

## 2. Request DTO — `UserRequest`

Package: `com.cat.user.service.dto`

| JSON field | Java field | Type | Validation annotations |
|---|---|---|---|
| `nombre` | `nombre` | `String` | `@NotBlank(message = "el campo nombre es obligatorio")` |
| `apellido` | `apellido` | `String` | `@NotBlank(message = "el campo apellido es obligatorio")` |
| `direccion` | `direccion` | `String` | `@NotBlank(message = "el campo direccion es obligatorio")` |
| `telefono` | `telefono` | `String` | `@NotBlank(message = "el campo telefono es obligatorio")` |
| `correo` | `correo` | `String` | `@NotBlank(message = "el campo correo es obligatorio")`, `@Email(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "el correo no tiene un formato valido")` |

Notes:
- Lombok `@Getter` + Jackson default constructor (or `@Data` if a setter
  is needed for deserialization — Jackson 2.16+ supports records, but
  Lombok keeps the file shorter for now).
- An incoming `id` field is **ignored** (Jackson's default behavior on
  unknown vs known fields: we set
  `spring.jackson.deserialization.fail-on-unknown-properties=false` so a
  client-supplied `id` is silently dropped — see FR-009).

## 3. Response DTO — `UserResponse`

Package: `com.cat.user.service.dto`

| JSON field | Java field | Type | Source |
|---|---|---|---|
| `id` | `id` | `UUID` (serialized as canonical 8-4-4-4-12 string) | system-generated |
| `nombre` | `nombre` | `String` | echo of trimmed request |
| `apellido` | `apellido` | `String` | echo of trimmed request |
| `direccion` | `direccion` | `String` | echo of trimmed request |
| `telefono` | `telefono` | `String` | echo of trimmed request |
| `correo` | `correo` | `String` | echo of trimmed request (original case preserved, AS-007) |

This DTO is returned in the `201 Created` response body (FR-008).

## 4. Mapping — `UserMapper`

Package: `com.cat.user.service.dto`

Static helpers (no Spring bean — pure functions, easier to unit-test):
- `User toDomain(UserRequest req, UUID id)` — trims every field, builds
  the `User`.
- `UserResponse toResponse(User user)` — copies fields verbatim.

Mapper has no logging (Principle IV); no validation (handled upstream by
Bean Validation); no I/O.

## 5. Repository contract — `UserRepository`

Package: `com.cat.user.service.repository`

```java
public interface UserRepository {
    /**
     * Atomically reserve the email and store the user.
     *
     * @throws DuplicateUserException if another user already holds the
     *         same trimmed, case-folded email.
     */
    User save(User user);

    /** For tests / future GET endpoint. */
    Optional<User> findById(UUID id);
}
```

Notes:
- The `throws` is a documentation convention — `DuplicateUserException`
  is unchecked (extends `RuntimeException`) so it propagates without
  polluting service signatures.
- No `findAll`, `delete`, or `update` methods (YAGNI — Principle V). Add
  them only when a real consumer needs them.

## 6. In-memory implementation — `InMemoryUserRepository`

Package: `com.cat.user.service.repository`

State (private, final):

```java
private final ConcurrentHashMap<UUID, User> usersById = new ConcurrentHashMap<>();
private final ConcurrentHashMap<String, UUID> idByEmailKey = new ConcurrentHashMap<>();
```

`save(User user)` algorithm:

1. Compute `String key = user.getCorreo().trim().toLowerCase(Locale.ROOT)`.
2. `UUID previous = idByEmailKey.putIfAbsent(key, user.getId())`.
3. If `previous != null` → throw `DuplicateUserException(user.getCorreo())`.
4. Else → `usersById.put(user.getId(), user)`; return `user`.

`findById(UUID id)` returns `Optional.ofNullable(usersById.get(id))`.

This satisfies:
- FR-005 (case-insensitive trimmed uniqueness).
- The "rapid succession" edge case (atomic `putIfAbsent`).
- The "user-supplied id ignored" rule (FR-009) is enforced upstream in
  the service, which always passes its own UUID.

## 7. Exception types

Package: `com.cat.user.service.exceptions`

| Class | Extends | Carries | Mapped to |
|---|---|---|---|
| `DuplicateUserException` | `RuntimeException` | the offending `correo` (so the advice can include it in the `errors[].message`) | 400 + `ProblemDetail(title="Usuario ya existente")` |

`MethodArgumentNotValidException` (thrown by Spring on `@Valid` failure)
is handled by `ApiExceptionHandler` overriding the inherited
`handleMethodArgumentNotValid(...)` — no custom exception class needed
for missing/blank fields. `HttpMessageNotReadableException` (malformed
JSON) is also handled there, returning the same Problem Details
envelope.

## 8. Cross-references to spec requirements

| Requirement | Implementation hook |
|---|---|
| FR-001 | `UserController` `@RequestMapping(ApiVersions.V1 + "/users")`, method `@PostMapping` |
| FR-002 | `UserRequest` shape |
| FR-003, FR-004 | Bean Validation annotations + `ApiExceptionHandler.handleMethodArgumentNotValid` |
| FR-005 | `InMemoryUserRepository.save` (key normalization + `putIfAbsent`) |
| FR-006 | `DuplicateUserException` thrown by repo, mapped to ProblemDetail by advice |
| FR-007 | `UserServiceImpl.register` generates `UUID.randomUUID()` and calls repo |
| FR-008 | `UserMapper.toResponse(savedUser)` returned with HTTP 201 |
| FR-009 | `UserRequest` has no `id` field; even if a client adds one, Jackson ignores it |
| FR-010 | `InMemoryUserRepository` is a singleton bean with no persistence |
| FR-011 | `ApiExceptionHandler` produces `application/problem+json` for every error path |
