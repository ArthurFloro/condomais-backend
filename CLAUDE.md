# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

This project has no `mvn` on PATH — always use the wrapper.

```powershell
# Compile (offline is fine once dependencies are cached)
.\mvnw.cmd -o compile -DskipTests

# Run all tests — requires a local Postgres reachable with the `local` profile
# (the committed application.properties has placeholder DB credentials on purpose)
.\mvnw.cmd test -Dspring.profiles.active=local

# Run a single test class / method
.\mvnw.cmd test -Dspring.profiles.active=local -Dtest=CondomaisBackendApplicationTests
.\mvnw.cmd test -Dspring.profiles.active=local -Dtest=CondomaisBackendApplicationTests#contextLoads

# Run the app locally
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"

# Build the jar and run it directly
.\mvnw.cmd clean package -DskipTests
java -jar target\condomais-backend-0.0.1-SNAPSHOT.jar --spring.profiles.active=local
```

Bash/Git Bash equivalents use `./mvnw` instead of `.\mvnw.cmd`.

Once running: API on `http://localhost:8080`, Swagger UI on `http://localhost:8080/swagger-ui.html` (publicly accessible; the underlying business routes still require a bearer token).

Production runs the `prod` profile (`application-prod.properties`): every sensitive value comes from env vars with **no defaults** (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `JWT_SECRET`, `CORS_ALLOWED_ORIGINS`), so a missing one fails startup on purpose. `ANTHROPIC_API_KEY` is the one optional exception (empty default; without it only the assistant is off). Deploy target is Render (Docker, `Dockerfile` sets `SPRING_PROFILES_ACTIVE=prod`) + Neon; a fresh database is created with `docs/sql/deploy/01-schema.sql` (Hibernate-generated from the entities; regenerate with `schema-generation.scripts.action=create` when entities change) and `02-dados-iniciais.sql`. Step-by-step in `docs/DEPLOY.md`.

`src/main/resources/application-local.properties` is gitignored and holds real local DB credentials, overriding the committed `application.properties` placeholders when `-Dspring.profiles.active=local` (or `SPRING_PROFILES_ACTIVE=local`) is active. `spring.jpa.hibernate.ddl-auto=update` only applies in that local profile; the default profile uses `validate`.

## Architecture

Spring Boot 4.1 / Java 17 REST API for **Condo+**, a multi-tenant condominium management system (one deployment serves many condomínios). Code is organized by business module under `br.com.condomais`, each following the same `controller / service / repository / model / dto` layout:

- `auth` — `Usuario` (users, one per condomínio, with a `perfil` role: `ADMIN`/`PORTARIA`/`MORADOR`), CPF+senha login, first-access password creation, and the admin-only `/usuarios` CRUD (`UsuarioService`): users are pre-registered without a password (`senha` NULL until `/auth/primeiro-acesso`), MORADOR links to an `Apartamento` with `vinculo` PROPRIETARIO/INQUILINO, `status` INATIVO blocks login and invalidates live tokens. CPF is stored formatted (`000.000.000-00`, check digits validated) because login compares it verbatim.
- `condominio` — **Módulo 2**: `Condominio` → `Torre` (optional) → `Apartamento`, plus `AreaComum`. This is the structural/tenant backbone every other module hangs off of.
- `portaria` — **Módulo 3**: visitantes, `Visita` (entrada/saída), `Encomenda`.
- `interativo` — **Módulo 4**: `Aviso` (announcements) and `Reserva` (common-area booking engine).
- `atendimento` — **Módulo 5**: `Chamado` (support tickets), `CategoriaChamado`, `HistoricoChamado`.
- `assistente` — virtual assistant for residents (`POST /assistente/mensagens`, MORADOR only) built on the Claude API (`anthropic-java`). `AssistenteService` runs the tool-use loop; `FerramentasAssistente` exposes read-only queries always scoped to the resident from the token (the model picks the query, never whose data). Stateless: the front sends the history (max 20 messages). Runs on its own 4-thread executor so the HTTP thread doesn't hold a pool connection under open-in-view. Without `ANTHROPIC_API_KEY` it throws `ServicoIndisponivelException` (503) and the rest of the API works; the model comes from `ASSISTENTE_MODELO` (default `claude-opus-5-5`).
- `core.security` — JWT auth plumbing (see below).
- `core.config` — `SwaggerConfig` (springdoc OpenAPI, bearer auth scheme).

### Multi-tenant isolation via JWT

There is no per-request tenant header — the condomínio is derived from the authenticated user:

1. `AuthController.login` authenticates via CPF/senha (`AutenticacaoService` is the `UserDetailsService`, wraps `Usuario` in `UsuarioAutenticado`), then `TokenService.gerarToken` issues a JWT carrying `id`, `perfil`, and `condominio_id` claims.
2. `SecurityFilter` (a `OncePerRequestFilter`) reads the bearer token on every request, looks the user back up by CPF (the JWT subject), and puts a `UsuarioAutenticado` into `SecurityContextHolder`.
3. Controllers that need to scope a write to the caller's condomínio/user pull it back out with a local helper, e.g.:
   ```java
   var auth = (UsuarioAutenticado) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
   auth.getId();            // Usuario id
   auth.getCondominioId();  // tenant id
   ```
   See `VisitaController`, `ChamadoController`, `AvisoController`, `ReservaController`, `EncomendaController` for the pattern — it's duplicated per controller rather than centralized. Follow the existing convention when adding a new authenticated write endpoint rather than introducing a new extraction mechanism.
4. `SecurityConfig` permits `/auth/login`, `/auth`, and the Swagger routes without auth; everything else requires an authenticated principal. There are no role-based (`hasRole`) restrictions yet — `perfil` is available as a `ROLE_<PERFIL>` authority but nothing currently enforces it at the security-config level.

5. CORS is configured in `SecurityConfig` for the front-end (repo `haveso27/condo_mais`, Vite dev server on port 5173). Allowed origins come from `api.cors.allowed-origins` (comma-separated), defaulting to `http://localhost:5173,http://127.0.0.1:5173`. No credentials/cookies — the token travels in the `Authorization` header.

### Condomínio module conventions (the most actively developed part)

- `Condominio` and `Torre` are still returned as raw JPA entities from some endpoints; `Apartamento`, `Torre`, and `AreaComum` GET endpoints were migrated to `*ResponseDTO` records with a static `from(entity)` mapper — follow that pattern for new read endpoints instead of exposing entities directly.
- Ownership is fixed at creation time: PUT endpoints on `Torre`/`Apartamento`/`AreaComum` ignore any `condominioId` in the request body — the entity's condomínio never changes after creation. `Apartamento` can be reassigned between towers, but only within the same condomínio (validated in `CondominioService.atualizarApartamento`).
- Deletes are guarded against orphaning child records: `CondominioService.excluir(repository, entidade)` does `delete` + `flush` so FK violations from other modules surface as a caught `DataIntegrityViolationException` immediately, rather than at commit time. Reuse this helper for new delete endpoints in this module.
- `AreaComum` uses a soft-delete boolean (`ativo`) instead of hard deletion, since reservation history must be preserved — see the `/areas-comuns/{id}/ativar` and `/desativar` endpoints.
- `docs/sql/` holds ad-hoc migration SQL snippets (there's no Flyway/Liquibase yet) — check there when a model change needs a matching DDL note.

### Error handling

`core.exception.GlobalExceptionHandler` (`@RestControllerAdvice`) maps exceptions to HTTP responses with a standard body, `ErroResponseDTO { status, erro, mensagem, caminho, timestamp }`. When adding validations in services, throw:

- `RecursoNaoEncontradoException` → 404 (record not found / outside the caller's condomínio)
- `ConflitoException` → 409 (duplicates, linked records blocking a delete, booking conflicts)
- plain `IllegalArgumentException` → 400 (other business-rule violations)
- `SecurityException` → 403 (cross-tenant access)
- `ServicoIndisponivelException` → 503 (optional external dependency switched off or down, e.g. the assistant without an API key)

`RecursoNaoEncontradoException` and `ConflitoException` extend `IllegalArgumentException`; `ServicoIndisponivelException` extends `RuntimeException`. Bare `orElseThrow()` becomes 404, login failures (`AuthenticationException`) become 401, Spring MVC exceptions keep their own status, and anything else is a 500 with a generic message (logged server-side). Missing/invalid/expired tokens never throw in `SecurityFilter`; the request continues unauthenticated and `SecurityConfig`'s entry point answers 401 in the same JSON shape. `/error` is public so unhandled errors show their real status instead of 403.

`AuthController.primeiroAcesso` still returns plain-string bodies (`ResponseEntity<String>`) rather than `ErroResponseDTO`.

## Team workflow

- Commit messages in Brazilian Portuguese, with conventional-commit prefixes kept in English (`feat:`, `fix:`, `docs:`, `chore:`), as in the existing history.
- Work on a branch and open a PR to `main`; never push straight to `main`.
- Front-end: repo `haveso27/condo_mais` (React/Vite/pnpm). Contributors push branches straight to it (no fork). Login is by CPF on both sides, sent formatted (`000.000.000-00`). To edit it from a backend session, add its local clone with `/add-dir`.
- Production: front on Vercel (`https://condo-mais.vercel.app`, published from a fork that needs "Sync fork" after each merge to the original repo), API on Render (`https://condomais-backend.onrender.com`, **no auto-deploy**: "Manual Deploy → Deploy latest commit" after each merge; health check `/v3/api-docs`), database on Neon. When a PR changes the model, its SQL in `docs/sql/` must be run by hand in Neon's SQL Editor. Details in `docs/DEPLOY.md`.

## Known gaps (agreed backlog, in priority order)

1. Tenant scoping on the server: `/condominios/*` listings accept a `condominioId` query param without checking it against the token (cross-tenant leak). Fix this first.
2. Front route guard: no session or expired token should redirect to login.
3. Wire the operational modules into the front (avisos, encomendas, visitantes, reservas, chamados): in API mode they are empty and new records stay in memory only.
4. Creating PORTARIA/ADMIN users from the UI (the API already supports it).
5. A unit without a tower can't receive a resident through the form.
6. Password recovery (no endpoint yet).
