# the-magic-theory

Experiment event ingestion and results service: ingests `exposure` / `conversion`
events (idempotently, tolerating retries and out-of-order delivery) and reports
unique-visitor results per experiment variant.

## API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/api/experiments` | Create an experiment: `{"id":"EXP-1","name":"...","variants":["A","B"]}` |
| `GET` | `/api/experiments` | List experiments |
| `POST` | `/api/events` | Ingest one event or an array of events |
| `GET` | `/api/experiments/{id}/results` | Per-variant exposed / conversions / conversion_rate |
| `GET` | `/health` | Liveness + DB connectivity |

Swagger UI at `/swagger-ui.html`.

### Authentication

All `/api/**` endpoints require an API key in the `X-API-Key` header (the key
is provided separately, never committed to this repository):

```bash
curl -H "X-API-Key: $API_KEY" $BASE/api/experiments
```

In Swagger UI, click **Authorize** and paste the key. `/health` and the
Swagger/OpenAPI docs are intentionally open.

### Load the sample data

```bash
BASE=http://localhost:8080
KEY=local-dev-key
curl -X POST $BASE/api/experiments -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"id":"EXP-1","name":"Homepage hero test","variants":["A","B"]}'
curl -X POST $BASE/api/experiments -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  -d '{"id":"EXP-2","name":"Checkout button test","variants":["control","treatment"]}'
curl -X POST $BASE/api/events -H "X-API-Key: $KEY" -H 'Content-Type: application/json' \
  --data-binary @data/sample-events.json
curl -H "X-API-Key: $KEY" $BASE/api/experiments/EXP-1/results
curl -H "X-API-Key: $KEY" $BASE/api/experiments/EXP-2/results
```

Loading the file again is harmless: every event is deduplicated by `event_id`.

## Run locally (IntelliJ)

1. Open this project as a Maven project.
2. Edit Run Configuration for `DemoApplication`:
   - **Active profiles:** `local`
3. Run. Health check: [http://localhost:8080/health](http://localhost:8080/health)

The `local` profile loads `src/main/resources/application-local.properties` (gitignored) for the Railway Postgres connection.

Alternatively, set env vars from `.env.example` instead of using the local profile.

## Railway

Set these variables on the **web service** (link them from the Postgres plugin):

| Variable | Example / Railway reference |
|---|---|
| `DATABASE_JDBC_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DATABASE_USERNAME` | `${{Postgres.PGUSER}}` |
| `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `API_KEY` | a strong random key, e.g. from `openssl rand -hex 32` |
