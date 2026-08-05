-- Experiments and their variants are configured up-front via the API.
CREATE TABLE experiments (
    id         TEXT PRIMARY KEY,
    name       TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE variants (
    experiment_id TEXT NOT NULL REFERENCES experiments (id),
    name          TEXT NOT NULL,
    PRIMARY KEY (experiment_id, name)
);

-- Raw events are the single source of truth. The primary key on event_id
-- makes ingestion idempotent: plugin retries (same event_id) can never be
-- counted twice, regardless of concurrency or arrival order.
CREATE TABLE events (
    event_id      TEXT PRIMARY KEY,
    visitor_id    TEXT        NOT NULL,
    experiment_id TEXT        NOT NULL,
    variant       TEXT        NOT NULL,
    type          TEXT        NOT NULL CHECK (type IN ('exposure', 'conversion')),
    occurred_at   TIMESTAMPTZ NOT NULL,
    received_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    FOREIGN KEY (experiment_id, variant) REFERENCES variants (experiment_id, name)
);

-- Serves the results query (unique visitors per experiment/variant/type)
-- as an index-only scan.
CREATE INDEX idx_events_results ON events (experiment_id, variant, type, visitor_id);
