# Deployment guide

This is a generic deployment outline. Review the security checklist in [SECURITY.md](SECURITY.md) before exposing the service to the internet.

## Backend

Build with `mvn clean package` from `backend/`. Run the JAR with the `prod` profile and provide these environment variables through your deployment platform or a protected environment file:

```text
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/campus_circle
SPRING_DATASOURCE_USERNAME=campus_app
SPRING_DATASOURCE_PASSWORD=<unique database password>
JWT_SECRET=<unique random secret of at least 32 bytes>
SERVER_URL=https://your-domain.example
UPLOAD_PATH=/srv/campus-circle/uploads
APP_CORS_ALLOWED_ORIGINS=https://your-domain.example
```

Create the database schema through a controlled migration or a one-time run with the `init` profile. The `prod` profile validates the schema. Keep database, Redis, and upload storage private. Use TLS at a reverse proxy and restrict database access to the application host.

Optional AI features read `OPENAI_API_KEY`, `OPENAI_BASE_URL`, and `OPENAI_MODEL` (or their `LLM_*` aliases). Leave the key unset to disable provider-backed calls. Do not put provider keys in the frontend.

The optional administrator bootstrap variables are described in the README. Set them only for initial provisioning, then remove them from the runtime environment. Change any administrator password that was used in earlier deployments.

## Frontend

Open `frontend/` in HBuilderX and build the H5 application. Host it at the root of a domain. If the frontend and backend use different origins, set `APP_CORS_ALLOWED_ORIGINS` to the exact frontend origin. Configure the reverse proxy to send `/api/` requests to the backend.

For local device debugging, call `setDevBackendOrigin('http://YOUR_LAN_IP:8080')` from `frontend/utils/api.js`, then use `clearDevBackendOrigin()` to reset it. Do not use a production credential in a development build.

## Verification

- Confirm `/actuator/health` reports healthy without exposing environment details.
- Confirm unauthenticated write requests return HTTP 401.
- Confirm CORS accepts only configured origins.
- Confirm uploads, post images, and login work through the reverse proxy.
