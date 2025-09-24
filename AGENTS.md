# Repository Guidelines

## Project Structure & Module Organization
- `backend/` houses the Spring Boot service (Java 21); domain code sits in `src/main/java/com/cloud_ml_app_thesis/**`, while configuration and SQL assets live in `src/main/resources`.
- Tests in `backend/src/test/java` split into `unit_tests` for isolated logic and `intergration` for cross-component flows.
- `frontend/` contains the Vite + TypeScript UI: routing in `src/app`, feature pages in `src/features`, shared helpers in `src/shared`, state in `src/core`, and static assets in `public/`.
- Docker manifests (`docker-compose.yml`, `docker-compose.dev.yml`) wire the API, Postgres, MinIO, and MailHog using values defined in `.env`.

## Build, Test, and Development Commands
- Backend: `./mvnw spring-boot:run` starts the API with the active profile; `./mvnw test` runs unit plus integration suites; `docker compose up --build` (inside `backend/`) provisions the full stack on ports 8080/5433/9000.
- Frontend: `npm install` (or `npm ci`) restores dependencies; `npm run dev` launches Vite with hot reload; `npm run build` performs the type-check and emits the production bundle in `dist/`.

## Coding Style & Naming Conventions
- Java follows 4-space indentation, Lombok for boilerplate, and Spring naming (`*Controller`, `*Service`, `*Repository`). Keep enums under `enumeration/` and reusable helpers under `util/` or `helper/`.
- TypeScript runs in `strict` mode; keep 2-space indentation, double-quoted imports, `camelCase` for functions/variables, and `PascalCase` for components or stores.

## Testing Guidelines
- Backend tests run with JUnit 5; name unit classes `*Test` and integration flows `*IT`. Use `./mvnw -Dtest=ClassName test` to scope execution and keep mocks close to the unit under test.
- The UI currently lacks an automated harness—add `vitest` or Playwright when extending features, document the new script in `package.json`, and gate PRs behind `npm run build`.

## Commit & Pull Request Guidelines
- The snapshot ships without shared Git history; use Conventional Commits (`feat:`, `fix:`, `chore:`) with subjects ≤72 chars and body text that notes rationale or breaking changes.
- Pull requests should outline scope, link issues, list verification steps (`./mvnw test`, `npm run build`), and attach screenshots or API contract diffs when UI or schema changes land. Request reviews from maintainers responsible for the affected module.

## Security & Configuration Tips
- Load secrets through `.env` and `src/main/resources/keys`; never commit real credentials. Update sample configs whenever new variables or key files appear.
- `application-local.yaml` drives developer runs, while `application-docker.yaml` powers container deployments. Set `SPRING_PROFILES_ACTIVE=local` before using `./mvnw spring-boot:run` outside Docker, and scrub sensitive artifacts from shared volumes (`SHARED_VOLUME`) and MinIO buckets.
