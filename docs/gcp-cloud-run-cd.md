# GCP Cloud Run continuous deployment

## Trigger

The .github/workflows/deploy-cloud-run.yml workflow runs after every push to main
and can also be started manually from the default branch.

The job:

1. runs the complete backend test suite;
2. builds an immutable image tagged with the Git commit SHA;
3. pushes the image to Artifact Registry;
4. updates the existing pl-timetable-api Cloud Run service; and
5. verifies that /api/v1/health/live reports the same commit SHA.

A failed test or build prevents deployment. Production deploys are serialized and
an in-progress deploy is not cancelled.

## Authentication and access scope

GitHub Actions authenticates through Workload Identity Federation. No service
account JSON key is stored in GitHub.

- Workload Identity Pool: github-actions
- Provider: pl-timetable-main
- Deploy service account:
  pl-timetable-github-deployer@pl-timetable-project.iam.gserviceaccount.com
- Trusted repository ID: 1301430101
- Trusted organization owner ID: 249942934
- Trusted Git ref: refs/heads/main only

The deploy service account can write images to the pl-timetable Artifact Registry
repository, update Cloud Run services, consume enabled GCP APIs, and act as the
existing Cloud Run runtime service account. It is not a project owner.

Repository Actions variables hold only non-secret resource identifiers. Database,
Google OAuth, and other runtime secrets remain attached to the Cloud Run service
and are preserved by gcloud run services update.

`GOOGLE_OAUTH_REDIRECT_URI` is also a non-secret repository variable. The workflow
maps it to
`SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_REDIRECT_URI` on every deploy so
the production callback remains on the Vercel same-origin proxy:

`https://pl-timetable-project-fe.vercel.app/login/oauth2/code/google`

## Required review flow

Feature branches and pull requests do not deploy production. A reviewed change
starts deployment only after it lands on main.
