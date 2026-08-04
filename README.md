# the-magic-theory

Experiment event ingestion and results service.

## Run locally (IntelliJ)

1. Open this project as a Maven project.
2. Edit Run Configuration for `DemoApplication`:
   - **Active profiles:** `local`
3. Run. Health check: [http://localhost:8080/health](http://localhost:8080/health)

The `local` profile loads `src/main/resources/application-local.properties` (gitignored) for the Railway Postgres connection.

Alternatively, set env vars from `.env.example` instead of using the local profile.
