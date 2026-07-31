# GCP deployment

The production backend uses:

- Cloud Run service `pl-timetable-api` in `asia-northeast3`
- Cloud SQL for PostgreSQL instance `pl-timetable-db`
- Artifact Registry repository `pl-timetable`
- Secret Manager for database and OAuth secrets
- A private Cloud Storage bucket for the ignored academic SQL bundle
- Gemini 3.5 Flash-Lite vision through Vertex AI for transcript OCR without
  storing uploaded originals

## Runtime profiles

- `prod,gcp`: Cloud SQL-backed API with Google OAuth disabled
- `prod,gcp,google`: Cloud SQL-backed API with the Google OAuth web client enabled

Google login is server-mediated. The browser starts at
`GET /api/v1/auth/google`, Google redirects to
`/login/oauth2/code/google`, and the backend establishes the JDBC-backed
application session.

Production uses `prod,gcp,google`. The OAuth client ID and secret are mounted
from Secret Manager, and the registered callback is:

`https://pl-timetable-api-532874992461.asia-northeast3.run.app/login/oauth2/code/google`

The callback is also set explicitly through Spring's Google registration
redirect URI environment variable so the external HTTPS URI is preserved
behind Cloud Run's proxy.

The frontend redirects are configured separately from the Google callback:

- `GOOGLE_OAUTH_SUCCESS_REDIRECT_URI=https://pl-timetable-project-fe.vercel.app/?auth=google-success`
- `GOOGLE_OAUTH_FAILURE_REDIRECT_URI=https://pl-timetable-project-fe.vercel.app/?auth=google-failure`

Keep `SESSION_COOKIE_SECURE=true` and `SESSION_COOKIE_SAME_SITE=none` because
the Vercel frontend and Cloud Run API use different sites.

## Transcript OCR

`POST /api/v1/completed-courses/ocr` sends the in-memory image directly to
`gemini-3.5-flash-lite` at the `global` endpoint. It does not use Cloud Vision
and does not persist the uploaded original.

Required runtime configuration:

- Vertex AI API enabled
- runtime service account role `roles/aiplatform.user`
- `GEMINI_PROJECT_ID`
- `GEMINI_LOCATION=global`
- `GEMINI_MODEL=gemini-3.5-flash-lite`

## Academic data loader

Build `deploy/gcp/data-loader/Dockerfile` from the repository root. The Cloud
Run Job downloads the private bundle using its runtime service account,
verifies all payload checksums, performs idempotent imports, normalizes
academic units, and runs the row-count/integrity verifier.

Required environment variables:

- `ACADEMIC_DATA_BUCKET`
- `PGHOST=/cloudsql/<project>:<region>:<instance>`
- `PGDATABASE`
- `PGUSER`
- `PGPASSWORD` from Secret Manager
