# DCI Data Aggregation — Design

Related issue: [#419](https://github.com/netxms/netxms/issues/419)

## Motivation

Provide automated rollup of DCI history into coarser time buckets (hourly, daily) for:
- **Storage reduction** — long-term raw data is expensive; aggregates compress 60×–1440×
- **Query performance** — raw queries over months/years are slow; aggregates keep graph queries fast

Prior art: Zabbix (history + trends), Graphite/statsd tiered retention, MikroTik The Dude.

## Tier Model

Three fixed tiers:

| Tier | Bucket | Typical range served |
|---|---|---|
| Raw | collection interval | minutes → hours |
| Hourly | 1 hour | days → months |
| Daily | 1 day | months → years |

**Rationale for fixed 3 tiers**:
- 15-min tier gives only 15× compression over 60s raw — marginal
- 8-hour tier gives only 8× over hourly — below threshold
- Hour/day align to calendar reporting boundaries; 15m/8h do not
- Configurable N tiers rejected: ~3× schema/code/UI complexity, breaks TSDB continuous aggregates

## Aggregated Values

Every aggregate row stores **min, max, avg, count**.

- Sum omitted — derivable as `avg × count`
- `count` enables correct weighted re-aggregation (daily-from-hourly)
- min/max enables band-graph rendering

## Row Shape

```
item_id         integer          not null
bucket_start    <timestamp>      not null    -- SQL_INT64 non-TSDB, timestamptz TSDB
min_value       double precision null
max_value       double precision null
avg_value       double precision null
sample_count    integer          not null
PRIMARY KEY (item_id, bucket_start)
```

Native `double precision` (no `varchar(255)` — string DCIs are excluded from aggregation).

## Storage Layout

### Non-TSDB backends

Per-object tables mirror existing `idata_<N>` pattern:
- `idata_1h_<N>` — hourly aggregates for object N
- `idata_1d_<N>` — daily aggregates for object N

Created lazily on first rollup for that object. Dropped alongside `idata_<N>` on object deletion.

Sizing for a large deployment (5,000 nodes × 100 DCIs × 60s collection, 1-year hourly retention):
- ~876k hourly rows per node — comfortable
- vs. a single shared `idata_1h` table of 4.38B rows, which would force per-backend partitioning

### TSDB backend

Two global hypertables:
- `idata_1h` — materialized by CAGG over view `idata` (UNION of raw `idata_sc_*`)
- `idata_1d` — CAGG-on-CAGG over `idata_1h` (avoids re-scanning raw)

**No sc_ split for aggregates**. Rationale: per-DCI retention cannot be honored on TSDB anyway (CAGG writes to one target; no per-row routing), so sc_ buckets provide no benefit. Chunk-drop retention applied to the single hypertable per tier.

TSDB minimum version bumped to one that supports CAGG over views (≥ 2.10). Document in release notes.

### String / table DCIs

Excluded entirely:
- String DCIs — min/max/avg meaningless for text
- Table DCIs (`tdata`) — multi-row/multi-column; aggregation would be a different feature

Counters need no special handling — aggregate `idata_value` (already rate after delta calc).

### Rule B — eligibility threshold

A tier is active for a DCI only when **collection interval ≤ bucket / 2** (guarantees ≥ 2 samples per bucket on average; min/max/avg are genuinely non-degenerate):

| Collection interval | Hourly? | Daily? |
|---|---|---|
| ≤ 30 min | ✓ | ✓ |
| 30 min < interval ≤ 12 h | ✗ | ✓ |
| > 12 h | ✗ | ✗ (raw already at daily granularity) |

Applies to non-TSDB rollup. On TSDB we accept CAGG output as-is (some degenerate 1-sample buckets in return for avoiding per-DCI metadata joins in the CAGG).

## Rollup Engine

### Non-TSDB — batch rollup

Two scheduled tasks:
- `DataCollection.Aggregation.HourlyRollup` — cron `5 * * * *`
- `DataCollection.Aggregation.DailyRollup` — cron `30 0 * * *`

Per run, for each eligible DCI:
1. Read `aggregation_watermark` from `items` (null → start from `now − close_window`)
2. For each bucket in `[watermark, now − close_window)`:
   - `SELECT min, max, avg, count FROM idata_<N> WHERE item_id=? AND idata_timestamp in [bucket_start, bucket_end)`
   - If `count = 0` — skip (chart gap)
   - Else — UPSERT into `idata_1h_<N>` / `idata_1d_<N>` (PG `ON CONFLICT`, MySQL `ON DUPLICATE KEY UPDATE`, MSSQL `MERGE`, SQLite `INSERT OR REPLACE`)
3. Advance watermark; null if fully caught up

Per-object parallelism via existing data collection thread pool.

### TSDB — continuous aggregates

```sql
CREATE MATERIALIZED VIEW idata_1h WITH (timescaledb.continuous) AS
  SELECT item_id,
         time_bucket('1 hour', idata_timestamp) AS bucket_start,
         min(CAST(idata_value AS double precision)) AS min_value,
         max(CAST(idata_value AS double precision)) AS max_value,
         avg(CAST(idata_value AS double precision)) AS avg_value,
         count(*) AS sample_count
  FROM idata
  WHERE idata_value ~ '^[-+]?[0-9]*\.?[0-9]+([eE][-+]?[0-9]+)?$'
  GROUP BY 1, 2;

SELECT add_continuous_aggregate_policy('idata_1h',
    start_offset => INTERVAL '30 days',
    end_offset   => INTERVAL '1 hour',
    schedule_interval => INTERVAL '10 minutes');
```

`idata_1d` defined analogously over `idata_1h` (weighted avg: `sum(avg*count)/sum(count)`).

### Late-data correctness

**Problem**: NetXMS agents queue data during connectivity outages. A site offline for 12 hours sends chronologically-ordered cached data on reconnect. Without special handling, aggregate rows for the outage window would remain missing after rollup advances past them.

**Non-TSDB solution**: per-DCI `aggregation_watermark` column on `items`.

- Hot-path raw insert in `datacoll.cpp`: if incoming timestamp < current watermark, atomic `UPDATE items SET aggregation_watermark = LEAST(aggregation_watermark, ?) WHERE item_id = ?`
- Chronological cached data means one `UPDATE` per outage (first post-reconnect insert), not per row
- Next scheduled rollup processes from new watermark; UPSERT handles overwriting any previously-empty bucket

**TSDB solution**: native CAGG invalidation log. `start_offset` of the refresh policy (default 30 days) caps the outage window that can be recovered.

## Query Dispatch

Extend the server-side DCI history query path with two parameters:
- `tier` — `auto | raw | hourly | daily` (default `auto`)
- `function` — `avg | min | max | minmax` (default `avg`)

### Auto-select heuristic

1. String/table DCI, or DCI with no aggregates populated → **raw**
2. `(end − start) / pollingInterval ≤ MaxAutoSelectPoints` (default 5000) → **raw**
3. `(end − start) / 3600 ≤ MaxAutoSelectPoints` and range fits within hourly retention → **hourly**
4. Else → **daily**

Fallback: if the chosen tier has no data for the range (e.g., aggregation recently enabled), dispatcher transparently reads a denser tier and serves what's available. Gaps remain gaps.

### Band-graph support

`function = minmax` returns both `min_value` and `max_value` columns in a single response — one round-trip instead of two.

### API surface

- **NXCP** — extend `CMD_GET_DCI_VALUES` with `VID_DCI_TIER` and `VID_DCI_AGG_FUNCTION`. Legacy clients unaffected (absent fields → auto + avg).
- **Java client** — new `NXCSession.getCollectedData()` overload with `DciTier` and `DciAggregationFunction` enums; legacy overload delegates to `(AUTO, AVG)`.
- **NXSL** — `GetDCIValues(dci, from, to, tier?, function?)` — trailing optional string parameters.

## Configuration

All new server configuration variables live under `DataCollection.Aggregation.*`:

| Variable | Type | Default | Purpose |
|---|---|---|---|
| `Enabled` | boolean | false | Master switch |
| `DefaultHourlyRetentionTime` | days | 365 | Global hourly retention |
| `DefaultDailyRetentionTime` | days | 1825 | Global daily retention |
| `HourlyCloseWindow` | seconds | 300 | Lag before rolling up a closed hour |
| `DailyCloseWindow` | seconds | 1800 | Lag before rolling up a closed day |
| `BackfillOnEnable` | boolean | true | Initialize watermarks to earliest raw timestamp |
| `MaxAutoSelectPoints` | int | 5000 | Auto-dispatch threshold |
| `TSDB.RefreshStartOffset` | days | 30 | CAGG refresh lookback (caps recoverable outage) |
| `TSDB.RefreshScheduleInterval` | seconds | 600 | CAGG refresh cadence |

## User Interface

### DCI properties (nxmc desktop + web)

New section in the **General** tab:
- **Aggregation mode**: *Default (inherit) / Enabled / Disabled*
- **Hourly retention**: *Default / Override (days)*
- **Daily retention**: *Default / Override (days)*

Info lines shown when DCI is ineligible:
- *"Aggregation not applicable: data type is STRING"*
- *"Aggregation not applicable: this is a table DCI"*
- *"Hourly aggregation not applicable: polling interval exceeds 30 minutes"*

On TSDB backend, an info note explains that per-DCI retention overrides are not honored and disable is query-level only.

### Graph widget

- **Data resolution**: *Auto / Raw / Hourly / Daily* dropdown
- **Show min/max band** checkbox — enables `function=minmax`; renders filled band with avg line
- Legend includes tier when not raw (e.g., *"Interface Load (hourly avg)"*)
- Hover tooltip shows `sample_count` when viewing aggregates

### Server configuration screen

New "Aggregation" section under Data Collection. TSDB-specific rows visible only on TSDB backend.

## Upgrade Path

Schema migration in the next available upgrade slot:

1. Add to `items`:
   - `aggregation_mode CHAR(1)` — `I` (inherit, default) / `E` (enabled) / `D` (disabled)
   - `hourly_retention INT NULL` — per-DCI override
   - `daily_retention INT NULL` — per-DCI override
   - `aggregation_watermark SQL_INT64 NULL` — non-TSDB only
2. Insert `DataCollection.Aggregation.*` config variables via `CreateConfigParam()`
3. Register the two scheduled tasks
4. On TSDB: create `idata_1h` and `idata_1d` hypertables, CAGGs, refresh and retention policies
5. Bump `DB_SCHEMA_VERSION_MINOR` in `src/server/include/netxmsdb.h`

Non-TSDB per-object aggregate tables (`idata_1h_<N>`, `idata_1d_<N>`) are **not** created during migration — they are created lazily on first rollup for that object, matching existing `idata_<N>` lifecycle.

### Enable-on-existing

When the admin flips the master switch:
- If `BackfillOnEnable = true`:
  - Non-TSDB: `UPDATE items SET aggregation_watermark = (SELECT MIN(idata_timestamp) FROM idata_<N> ...)` per eligible DCI
  - TSDB: `CALL refresh_continuous_aggregate('idata_1h', NULL, now())`, then same for `idata_1d`
- If false: watermarks stay null; aggregation starts from next bucket close

No server restart required.

## Housekeeper Integration

- **Non-TSDB retention enforcement**: new housekeeper task walks each object's aggregate tables and deletes rows older than retention. Per-DCI overrides honored via partitioned `WHERE item_id IN (...)` deletes.
- **TSDB retention enforcement**: native chunk-drop via `add_retention_policy` — no server-side code.
- **Object deletion**: the existing cleanup path is extended with `DROP TABLE IF EXISTS idata_1h_<N>, idata_1d_<N>` (non-TSDB) or `DELETE ... WHERE item_id = ?` on the aggregate hypertables (TSDB).

## Known TSDB Tradeoffs

Documented in the admin guide:
- **Per-DCI aggregation disable** is a query-time filter only — aggregates are still produced on disk for all DCIs
- **Per-DCI aggregate retention is not honored** — retention is driven by a single chunk-drop policy per aggregate hypertable
- **Per-DCI retention longer than global default is impossible** — chunk drops are global

Same class of tradeoff as the existing raw storage-class model, which approximates per-DCI retention via the `idata_sc_*` buckets.

## Compatibility

All schema changes are additive. NXCP gains optional fields (legacy clients ignore them). Java client and NXSL gain new overloads that preserve existing signatures. No breaking changes.

## Open Items for Implementation

- Exact TSDB minimum version to require in release notes
- Housekeeper task naming and integration point for non-TSDB aggregate retention deletes
- Precise UI field labels and help text (en/de/cs/ru/es/pt translations)
- Whether to expose aggregate `sample_count` in the API as a distinct response column or only via tooltip
