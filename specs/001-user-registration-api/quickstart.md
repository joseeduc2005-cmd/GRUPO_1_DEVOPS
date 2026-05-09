# Quickstart — User Registration API

**Branch**: `001-user-registration-api`
**Audience**: A developer who just pulled the branch and wants to run,
exercise, and test the feature locally.

## Prerequisites

- JDK 25 (the Gradle toolchain in `build.gradle` will auto-provision if
  you have a recent Gradle install with toolchain support).
- No external services. The store is in-memory.

## 1. Build & test

```bash
./gradlew build
```

This MUST pass before any PR is merged (Constitution Principle II
gate). It runs:

- `compileJava` (production)
- `compileTestJava` (tests)
- `test` (JUnit 5: unit + functional)

If you only want the test suite:

```bash
./gradlew test
```

## 2. Run the service

```bash
./gradlew bootRun
```

The service starts on `http://localhost:8080`. There is one endpoint:
`POST /api/v1/users`.

## 3. Exercise the happy path

```bash
curl -i -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Ana",
    "apellido": "Pérez",
    "direccion": "Calle 1 #2-3",
    "telefono": "3001234567",
    "correo": "ana.perez@example.com"
  }'
```

Expected response: `201 Created` with a JSON body containing the same
five fields plus a generated `id` (UUID v4).

## 4. Exercise the duplicate-email path

Send the **same** request body again (any case/whitespace variation of
the email also counts):

```bash
curl -i -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{
    "nombre": "Ana",
    "apellido": "Pérez",
    "direccion": "Calle 1 #2-3",
    "telefono": "3001234567",
    "correo": "ANA.PEREZ@example.com"
  }'
```

Expected response: `400 Bad Request`,
`Content-Type: application/problem+json`, body:

```json
{
  "type": "about:blank",
  "title": "Usuario ya existente",
  "status": 400,
  "detail": "El usuario con el correo proporcionado ya se encuentra registrado.",
  "instance": "/api/v1/users",
  "errors": [
    { "field": "correo", "message": "el correo ya está registrado" }
  ]
}
```

## 5. Exercise the missing-fields path

```bash
curl -i -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d '{ "nombre": "Ana", "correo": "x@y.co" }'
```

Expected response: `400 Bad Request`, body lists **every** missing
field in the `errors` array (not just the first one — FR-004):

```json
{
  "type": "about:blank",
  "title": "Validación fallida",
  "status": 400,
  "detail": "Uno o más campos requeridos no fueron enviados o están vacíos.",
  "instance": "/api/v1/users",
  "errors": [
    { "field": "apellido",  "message": "el campo apellido es obligatorio" },
    { "field": "direccion", "message": "el campo direccion es obligatorio" },
    { "field": "telefono",  "message": "el campo telefono es obligatorio" }
  ]
}
```

## 6. Exercise the malformed-JSON path

```bash
curl -i -X POST http://localhost:8080/api/v1/users \
  -H "Content-Type: application/json" \
  -d 'not-json'
```

Expected response: `400 Bad Request`, `title: "Solicitud inválida"`,
empty `errors` array.

## 7. Reset

The store is in-memory. To clear it, restart the process:

```bash
# Ctrl-C the bootRun, then:
./gradlew bootRun
```

## 8. Where things live (after the feature is implemented)

- Controller, AOP, exception handler — see [plan.md §Project Structure](./plan.md)
- HTTP contract — [contracts/openapi.yaml](./contracts/openapi.yaml)
- Domain & DTO shapes — [data-model.md](./data-model.md)
- Why each technical choice was made — [research.md](./research.md)

## 9. Constitution gates a PR must pass

Before opening a PR for this feature, verify:

1. `./gradlew build` is green (unit + functional tests pass).
2. The endpoint is `/api/v1/users` (Principle III).
3. Every service method has at least one happy-path AND one
   failure-path unit test (Principle II).
4. The controller has at least one success AND one error functional
   test (Principle II).
5. No `log.*` call exists outside `aop/` and `exceptions/` packages
   (`grep -RIn "log\." src/main/java | grep -v aop/ | grep -v
   exceptions/` should return nothing) — Principle IV.
6. Layer boundaries are respected: controller imports no repository,
   domain imports no Spring web annotation — Principle I.
