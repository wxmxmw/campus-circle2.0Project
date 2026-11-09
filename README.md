# Campus Circle

A course project for a campus community. The backend is a Spring Boot API; the frontend is a uni-app application. Features include posts, polls, errands, chat, notifications, an information center, and optional AI assistance.

## Requirements

- JDK 17 and Maven 3.9+
- HBuilderX for the uni-app frontend
- MySQL and Redis are optional for local development. The default profile uses a local H2 database and disables Redis.

## Local setup

1. Copy `backend/.env.example` to `backend/.env` and replace `JWT_SECRET` with a unique random value. Never commit the resulting file.
2. From `backend/`, run `./start-with-env.ps1` on PowerShell. Alternatively, set `JWT_SECRET` in your shell and run `./mvnw.cmd spring-boot:run` on Windows or `./mvnw spring-boot:run` on Unix.
3. Open `frontend/` in HBuilderX and run the H5 application. The development API is `http://localhost:8080/api`.

The backend uses a local H2 file under `backend/data/`. The API health endpoint is `http://localhost:8080/actuator/health`.

For a MySQL database, set `SPRING_PROFILES_ACTIVE=local-mysql` and the three `SPRING_DATASOURCE_*` variables shown in the example environment file. The `prod` profile requires the same variables and validates the schema instead of changing it.

An administrator account is created only when `APP_BOOTSTRAP_ADMIN_USERNAME`, `APP_BOOTSTRAP_ADMIN_EMAIL`, and `APP_BOOTSTRAP_ADMIN_PASSWORD` are all set. The password must be at least 16 characters. Existing accounts are never promoted automatically.

See [deployment instructions](DEPLOYMENT_GUIDE.md) and the [public release checklist](SECURITY.md) before hosting this application or making a repository public.

## Project structure

- `backend/`: Java API, data model, and tests
- `frontend/`: uni-app application
- `backend/.env.example`: environment variable template

## Tests

Run `mvn test` from `backend/`. Tests use an in-memory H2 database and a test-only JWT secret.
