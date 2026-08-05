# the-magic-theory

Web service for measuring website experiments (A/B tests).

Websites show visitors different versions of a page — variant A or variant B —
and want to know which one convinces more people to act (buy, sign up, etc.).
A plugin on the website sends this service two kinds of **events**: an
*exposure* ("this visitor saw variant B") and a *conversion* ("this visitor converted"). This service stores those events and answers the question:
**which variant is winning, and by how much?**

The hard part is that real event traffic is messy: the same event can arrive
twice (the plugin retries), one visitor may be exposed five times but is still
one person, and events arrive late or out of order. This service is built so
the numbers stay correct despite all of that — see [Design notes](#design-notes).

## Live service

- Base URL: `https://the-magic-theory-production.up.railway.app`
- Interactive docs (Swagger): [`/swagger-ui.html`](https://the-magic-theory-production.up.railway.app/swagger-ui.html)
- Health check: [`/health`](https://the-magic-theory-production.up.railway.app/health) — no key needed

All `/api/...` endpoints require an API key in the `X-API-Key` header.
**The key is shared separately and is not in this repository.** In Swagger,
click **Authorize** and paste the key; with curl, add `-H "X-API-Key: <key>"`.

## API

| Method | Path | What it does |
|---|---|---|
| `POST` | `/api/experiments` | Define an experiment and its variants |
| `GET` | `/api/experiments` | List experiments |
| `POST` | `/api/events` | Ingest one event or an array of events |
| `GET` | `/api/experiments/{id}/results` | Per-variant results: exposed, conversions, conversion rate |
| `GET` | `/api/experiments/{id}/summary` | Short plain-language AI summary of the results |
| `GET` | `/health` | Service + database liveness |

### Try it against the live service

```bash
BASE=https://the-magic-theory-production.up.railway.app
KEY=<the key shared separately>

# See the experiments and their results
curl -H "X-API-Key: $KEY" $BASE/api/experiments
curl -H "X-API-Key: $KEY" $BASE/api/experiments/EXP-1/results
curl -H "X-API-Key: $KEY" $BASE/api/experiments/EXP-1/summary
```

The provided sample data (130 events, in `data/sample-events.json`) is already
loaded. Expected results, which the service reproduces exactly:

| Experiment | Variant | Exposed | Conversions | Rate |
|---|---|---|---|---|
| EXP-1 | A | 20 | 5 | 25.0% |
| EXP-1 | B | 22 | 9 | 40.9% |
| EXP-2 | control | 15 | 3 | 20.0% |
| EXP-2 | treatment | 18 | 8 | 44.4% |

Re-posting the whole sample file is harmless — every event is recognised as a
duplicate and ignored:

```bash
curl -X POST $BASE/api/events -H "X-API-Key: $KEY" \
  -H 'Content-Type: application/json' --data-binary @data/sample-events.json
# -> {"received":130,"inserted":0,"duplicates":130,"rejected":[]}
```

## Design notes

### Data model

Three tables (created by a Flyway migration on startup):

- **experiments** — id and name, defined up-front through the API.
- **variants** — the variants belonging to each experiment.
- **events** — every event ever received, exactly once. `event_id` is the
  primary key. Each event references a configured experiment/variant.

The raw events table is the single source of truth. Nothing is ever updated or
counted twice at write time, which is what makes the correctness guarantees
below simple to reason about.

### How the counts stay correct

- **Duplicates (plugin retries).** Inserts use `ON CONFLICT (event_id) DO
  NOTHING`, so the database itself guarantees an event is stored at most once.
  This holds even when the same event arrives on two connections at the same
  instant — verified with 10 parallel identical requests, which resulted in
  exactly one stored row.
- **Repeat exposures and conversions.** Results count *unique visitors*
  (`COUNT(DISTINCT visitor_id)`), so a visitor exposed five times counts as
  one exposure, and a visitor with two conversion events counts as one
  conversion.
- **Late and out-of-order events.** Counting never depends on when an event
  arrives or on its timestamp — an event either adds a new row or it doesn't,
  and results are recomputed from the rows at read time. Any arrival order
  produces identical results. Timestamps are stored for audit only.

### Aggregation: compute on read

Results are calculated by a single indexed SQL query at read time, not by
maintaining counters as events arrive. Two reasons:

1. Exposure events vastly outnumber result queries, so the write path should
   be as cheap as possible: one idempotent insert, no locks, no read-modify-write.
2. Counters can drift when retries and races are involved; a count recomputed
   from the raw events cannot. Correctness is the core requirement here, so
   the design removes the entire class of "counter got out of sync" bugs.

### Scale

The write path (one indexed insert per event) comfortably handles high event
volume; Postgres and the connection pool would be the first bottleneck, and
both scale with hardware before any redesign is needed. The read path is a
`COUNT(DISTINCT ...)` served by a covering index; it slows gradually as an
experiment accumulates millions of events. When that day comes, the natural
evolution is a small rollup table (per experiment/variant, the set of already
counted visitors and running totals) refreshed incrementally — the raw events
table already contains everything needed to build it without downtime. At
much larger scale: batched/queued ingestion in front, approximate unique
counting (HyperLogLog) behind.

### The AI summary

`GET /api/experiments/{id}/summary` sends the computed numbers (never raw
user data) to Google Gemini and returns 2–3 plain sentences. The model is
treated as unreliable by design: if the key is missing, the call times out,
the API errors, the answer is truncated or empty — the endpoint returns a
correct summary computed locally from the numbers instead, marked
`"source": "fallback"` rather than `"source": "llm"`. The client always gets
a usable answer and never an error caused by the model. The Gemini key stays
on the server and is never exposed.

### Security

A single static API key (`X-API-Key` header) protects all business endpoints.
The comparison is constant-time, the app refuses to start without a key
configured, and the key lives only in environment variables. Deliberately
simple: no user accounts, roles, or key rotation

### Trade-offs and known limitations

- **Events must reference a configured experiment.** Unknown experiment or
  variant events are rejected (with a clear reason in the response) rather
  than stored for later. A production pipeline would dead-letter them instead.
- **No length limits on ids.** `event_id` / `visitor_id` of any length are
  accepted; a misbehaving client could store very large values. Adding a
  sane cap (e.g. 256 chars) is a known next step.
- **Conflicting retries.** If the same `event_id` ever arrives with
  *different* content, the first version wins and the rest are ignored —
  retries are assumed to be identical copies.
- **A conversion without an exposure still counts** — the results report
  unique converters as defined, they do not require a prior exposure event.
- **One shared API key**, no rate limiting, no per-client quotas.

## Running locally

Requirements: JDK 17 and a PostgreSQL
database. Configuration comes from environment variables — see
[`.env.example`](.env.example) for the full list.

```bash
export DATABASE_JDBC_URL=jdbc:postgresql://HOST:PORT/DATABASE
export DATABASE_USERNAME=...
export DATABASE_PASSWORD=...
export API_KEY=local-dev-key
export GEMINI_API_KEY=...   # optional; summary falls back without it

./mvnw spring-boot:run
```

The schema is created automatically on first start (Flyway). Check
`http://localhost:8080/health`, then use the curl examples above with
`BASE=http://localhost:8080`.

From IntelliJ: run `DemoApplication` with the environment variables above, or
keep them in a gitignored `src/main/resources/application-local.properties`
and set the active profile to `local`.

## Deployment (Railway)

The service and its Postgres database run on Railway. The web service needs
these variables (`PORT` is injected automatically):

| Variable | Value |
|---|---|
| `DATABASE_JDBC_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
| `DATABASE_USERNAME` | `${{Postgres.PGUSER}}` |
| `DATABASE_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
| `API_KEY` | a strong random key (`openssl rand -hex 32`) |
| `GEMINI_API_KEY` | Gemini API key (optional) |

Every push to `main` triggers a build and deploy; Flyway applies any new
migrations on startup.
