-- V8: durable daily budget for OpenAlex calls (release decision R4, H2).
-- One row per UTC day (the provider's allowance resets at midnight UTC). `calls` counts HTTP attempts sent or about
-- to be sent; it is reserved atomically before each attempt, so concurrent requests (also across processes) can
-- never claim the same remaining budget, and restarts keep the count.
CREATE TABLE provider_call_budget (
    day        DATE        PRIMARY KEY,
    calls      INTEGER     NOT NULL CHECK (calls >= 0),
    updated_at TIMESTAMPTZ NOT NULL
);
