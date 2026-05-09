# Feature Specification: User Registration API

**Feature Branch**: `001-user-registration-api`
**Created**: 2026-05-05
**Status**: Draft
**Input**: User description: "Como desarrollador requiero crear un API de registro de usuarios para almacenar la informacion de los mismo. Como criterios de aceptacion: El api debe ser con el context path /users, El api debe ser un tipo POST que recibe un JSON con los siguientes datos del usuario: nombre, apellido, direccion, telefono, correo. El servicio debe almacenar los datos del usuario en memoria y el API debe responder los mismo datos del usuario con un ID de tipo UUID autogenerado. Validar que el correo no sea repetido, es decir, tiene que ser unico. Validar que los campos nombre, apellido, direccion, telefono y correo sean obligatorios, si algun campo de los requeridos no se envia, debera retornar un error 400 indicando que campo falta llenar. Si el servicio funciona, debe retornar un status 201. Si el usuario ya se encuentra creado, debe retornar un 400 con un mensaje de usuario existente."

## Clarifications

### Session 2026-05-05

- Q: The original input said "context path `/users`", but the project
  constitution (Principle III) mandates `/api/v{N}/...` for every public
  endpoint. Which path wins?
  → A: Option A — final path is `POST /api/v1/users`. The `/users` in the
  input is interpreted as the resource segment inside the versioned API,
  honoring both the user's intent (a `users` resource) and the
  constitution (no unversioned endpoints, no need to justify an
  exception in Complexity Tracking).
- Q: What JSON shape do error responses use? The input only said the
  body "indicates which field is missing" and carries "a user-exists
  message", without fixing the schema.
  → A: Option A — RFC 9457 Problem Details. Envelope: `{"type",
  "title","status":400,"detail","instance","errors":[{"field":"<name>",
  "message":"<reason>"}]}`. Both validation failures and duplicate-user
  use this same envelope; only `title`, `detail`, and the `errors`
  contents differ. (Locale of the human-readable text decided in Q3
  below.)
- Q: In what language are the human-readable strings (`title`,
  `detail`, `errors[].message`) returned? The input was written in
  Spanish ("usuario existente", "campo falta llenar") but no explicit
  locale rule was given.
  → A: Option A — Spanish across the whole error payload. Validation
  failures use `title:"Validación fallida"` and per-field messages like
  `"el campo nombre es obligatorio"`. Duplicate-user uses
  `title:"Usuario ya existente"` and
  `errors:[{"field":"correo","message":"el correo ya está registrado"}]`.
  No client-side locale negotiation in this version.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Register a new user successfully (Priority: P1)

A consumer system (e.g., a sign-up form, an integration partner, or an
internal admin tool) needs to register a brand-new person by submitting
their personal contact information. The user-registration service stores
the person and returns a stable identifier so the consumer can later
reference that user.

**Why this priority**: This is the entire MVP. Without the happy-path
registration working, no other capability of the service has any value.
It is also the precondition for any later feature (login, profile update,
password reset, etc.) the project might add.

**Independent Test**: Send a registration request containing all required
fields with values that have never been used before, then verify that the
service confirms creation, returns a newly generated unique identifier,
and echoes back the submitted personal data.

**Acceptance Scenarios**:

1. **Given** no user with the email `ana.perez@example.com` exists,
   **When** a request is submitted with name "Ana", last name "Pérez",
   address "Calle 1 #2-3", phone "3001234567", and email
   "ana.perez@example.com",
   **Then** the service confirms successful creation with status code 201
   and the response body contains the same five submitted fields plus a
   newly generated unique identifier.
2. **Given** a successful registration response was just returned,
   **When** the consumer reads the identifier from that response,
   **Then** the identifier is a globally unique value distinct from any
   previously issued identifier.

---

### User Story 2 - Reject duplicate registration (Priority: P2)

A consumer attempts to register a person whose email is already on file.
The service must refuse the registration and explain why, so the consumer
can react (e.g., prompt the end-user to log in instead).

**Why this priority**: Without this rule the in-memory store would
accumulate duplicate records that ruin every later lookup, lookup-by-email,
and any future authentication flow. It is the second-highest priority
because it defends data integrity for every subsequent story.

**Independent Test**: Register a user once with a given email, then submit
a second registration with the same email and verify the service rejects
it with the documented error.

**Acceptance Scenarios**:

1. **Given** a user with email "ana.perez@example.com" was successfully
   registered,
   **When** a second registration is submitted with the same email (any
   combination of other field values),
   **Then** the service rejects the request with status code 400 and a
   message indicating that the user already exists.
2. **Given** the duplicate registration was rejected,
   **When** the existing user is looked up later,
   **Then** the original registration is still intact and unchanged (the
   rejected attempt did not modify or replace it).

---

### User Story 3 - Reject incomplete registration (Priority: P2)

A consumer submits a registration in which one or more required fields
are absent or empty. The service must refuse the request and report which
field is missing, so the consumer can correct the input.

**Why this priority**: Without this rule the service could persist
half-formed records, which would break every downstream consumer that
assumes the five fields are always present. It is required for
MVP-quality data but does not block the happy path itself.

**Independent Test**: Submit a registration request that omits one
required field at a time and verify the service rejects each request with
a clear indication of which field is missing.

**Acceptance Scenarios**:

1. **Given** a registration request that omits the `nombre` field,
   **When** the request is submitted,
   **Then** the service responds with status code 400 and a message that
   identifies `nombre` as the missing required field.
2. **Given** a registration request that omits multiple required fields,
   **When** the request is submitted,
   **Then** the service responds with status code 400 and a message that
   identifies every missing required field (not only the first one).
3. **Given** a registration request in which a required field is present
   but contains only whitespace,
   **When** the request is submitted,
   **Then** the service treats that field as missing and responds with the
   same 400 error as for a fully absent field.

---

### Edge Cases

- A request whose body is not valid JSON, or is empty, MUST be rejected
  with status 400 and a message describing the malformed payload (without
  exposing internal error traces).
- A request whose `correo` field has a value that is not a syntactically
  valid email address MUST be rejected with status 400 and a message
  identifying `correo` as invalid (see Assumptions for the validation
  rule applied).
- Two requests submitted in rapid succession with the same email MUST
  result in exactly one stored record; the second MUST receive the
  duplicate error.
- The auto-generated identifier MUST NOT be accepted from the request
  body; if a client supplies one, the service MUST ignore it and generate
  its own.
- Field values containing leading/trailing whitespace MUST be trimmed
  before storage and uniqueness comparison so that "ana@x.com" and
  " ana@x.com " are treated as the same address.
- Email uniqueness comparison MUST be case-insensitive
  ("Ana@Example.com" and "ana@example.com" are the same user).
- Because storage is in memory, all registered users are lost when the
  service restarts; consumers MUST NOT depend on persistence across
  restarts in this version.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: The system MUST expose a single registration endpoint at the
  path `/api/v1/users` that accepts only the HTTP POST method. The
  `/api/v1` prefix is mandated by the project constitution (Principle III,
  API Versioning); the `users` segment is the resource originally
  requested as `/users` in the feature input.
- **FR-002**: The system MUST accept a JSON request body containing the
  fields `nombre`, `apellido`, `direccion`, `telefono`, and `correo`.
- **FR-003**: The system MUST treat all five fields above as required;
  any missing or blank-only field MUST cause the request to be rejected.
- **FR-004**: When a required field is missing or blank, the system MUST
  respond with HTTP status 400 and an RFC 9457 Problem Details body
  with `title:"Validación fallida"` whose `errors` array lists every
  offending field as `{"field":"<name>","message":"el campo <name> es
  obligatorio"}`, so the caller can correct all of them in one
  round-trip.
- **FR-005**: The system MUST enforce that the value of `correo` is
  unique across all currently stored users; uniqueness comparison MUST be
  case-insensitive and ignore leading/trailing whitespace.
- **FR-006**: When a registration is submitted with a `correo` that
  already belongs to a stored user, the system MUST respond with HTTP
  status 400 and an RFC 9457 Problem Details body with
  `title:"Usuario ya existente"` and an `errors` entry of the form
  `{"field":"correo","message":"el correo ya está registrado"}`.
- **FR-007**: When a registration passes all validations, the system MUST
  generate a new globally unique identifier in UUID form, store the user
  in an in-memory data store keyed (or indexed) by that identifier, and
  return HTTP status 201.
- **FR-008**: The 201 success response body MUST contain the generated
  identifier together with the same five submitted field values (after
  trimming).
- **FR-009**: The system MUST NOT accept or honor an identifier supplied
  by the caller; it MUST always generate its own.
- **FR-010**: The system MUST persist registered users only in memory for
  this version; no database, file, or external store is used. The data
  is expected to be lost on restart.
- **FR-011**: All error responses (validation failures, duplicate-user,
  and malformed payload) MUST use HTTP status 400 and MUST return an
  RFC 9457 Problem Details JSON body
  (`Content-Type: application/problem+json`) with the human-readable
  fields in Spanish per Q3. Internal stack traces or framework-specific
  default error formats MUST NOT leak to the caller.

### Key Entities

- **User**: Represents one registered person. Attributes: a
  system-generated unique identifier (UUID), and the five caller-supplied
  fields `nombre` (first name), `apellido` (last name), `direccion`
  (postal/street address), `telefono` (phone number), and `correo`
  (email address). The combination of stored users forms the in-memory
  user registry; uniqueness inside that registry is enforced on `correo`.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: A consumer can complete a successful registration in a
  single request and receive a confirmed identifier 100% of the time
  when all five required fields are supplied with valid, previously
  unused values.
- **SC-002**: 100% of registration attempts that are missing one or more
  required fields are rejected, and the response identifies every
  missing field (not only the first detected).
- **SC-003**: 100% of registration attempts that reuse an
  already-registered email are rejected with the documented duplicate-
  user message, and the pre-existing record remains unchanged.
- **SC-004**: Every successful registration returns a unique identifier;
  in any sequence of N successful registrations, the service produces N
  distinct identifiers (no collisions, no reuse).
- **SC-005**: A successful registration response is returned within 200
  milliseconds at the 95th percentile under a load of 100 sequential
  requests on a developer-class machine.

## Assumptions

- **AS-001**: The five field names submitted by callers are exactly
  `nombre`, `apellido`, `direccion`, `telefono`, `correo` (Spanish, all
  lowercase, no accents). Other casings or translations are out of scope
  for this version.
- **AS-002**: Email format validation follows the common practical rule
  "non-empty local part, single `@`, non-empty domain with at least one
  `.`". Full RFC 5322 conformance is out of scope for this version.
- **AS-003**: Phone-number format is not validated beyond the
  required/non-blank rule; any non-empty string is accepted. A future
  feature may add regional or E.164 validation.
- **AS-004**: Address is treated as a single free-form string; structured
  address parsing (street, city, postal code, country) is out of scope.
- **AS-005**: There is no authentication or authorization on the
  registration endpoint in this version; any caller that can reach the
  service can register a user. Securing the endpoint is a separate
  feature.
- **AS-006**: The in-memory store is a single-process structure; running
  multiple instances of the service would each have an independent store
  and could accept the same email in each instance. Multi-instance
  deployment is out of scope for this version.
- **AS-007**: The response payload returns the values exactly as stored
  (after trimming); no normalization (e.g., title-casing names,
  lowercasing email in the response) is applied beyond the trimming and
  case-insensitive comparison used for uniqueness.
