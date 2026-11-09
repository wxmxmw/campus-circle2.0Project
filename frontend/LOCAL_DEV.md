# Frontend local development

Open this directory in HBuilderX and run the H5 application. The default development backend is `http://localhost:8080`, with API requests under `/api`.

If HBuilderX serves H5 from a different origin, set `APP_CORS_ALLOWED_ORIGINS` in `backend/.env` to that exact origin (for example, `http://localhost:8081`) and restart the backend.

For device testing, use `setDevBackendOrigin('http://YOUR_LAN_IP:8080')` from `utils/api.js`. A phone's `localhost` points to the phone itself. Call `clearDevBackendOrigin()` to restore the default.

Do not place database passwords, JWT signing keys, or AI provider keys in frontend files.
