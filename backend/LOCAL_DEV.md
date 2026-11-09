# Backend local development

Copy `.env.example` to `.env`, generate a unique `JWT_SECRET`, and run `./start-with-env.ps1` in PowerShell. The script loads the environment file and starts the packaged application. The default profile stores H2 data in `./data/` and does not require MySQL or Redis.

To use MySQL, set `SPRING_PROFILES_ACTIVE=local-mysql` and provide `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, and `SPRING_DATASOURCE_PASSWORD` in `.env`. Keep `.env` out of Git.

The health endpoint is `http://localhost:8080/actuator/health` and the API documentation is at `http://localhost:8080/swagger-ui.html`.
