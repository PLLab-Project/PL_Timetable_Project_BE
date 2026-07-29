# GCP deployment

The production backend uses:

- Cloud Run service `pl-timetable-api` in `asia-northeast3`
- Cloud SQL for PostgreSQL instance `pl-timetable-db`
- Artifact Registry repository `pl-timetable`
- Secret Manager for database and OAuth secrets
- A private Cloud Storage bucket for the ignored academic SQL bundle
- Cloud Vision API for transcript OCR without storing uploaded originals

## Runtime profiles

- `prod,gcp`: Cloud SQL-backed API with Google OAuth disabled
- `prod,gcp,google`: Cloud SQL-backed API with the Google OAuth web client enabled

Google login is server-mediated. The browser starts at
`GET /api/v1/auth/google`, Google redirects to
`/login/oauth2/code/google`, and the backend establishes the JDBC-backed
application session.

The standard Google web OAuth client itself must be created once in Cloud
Console by a signed-in human account; Google does not expose that operation
through a supported provisioning API. Until then, keep `GOOGLE_OAUTH_ENABLED`
false and use `prod,gcp`.

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
