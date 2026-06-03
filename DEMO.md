# OpenTelemetry + Grafana LGTM Demo

## Objective

This demo shows how a Spring Boot application can publish observability signals to a Grafana LGTM stack using OpenTelemetry. The goal is to make logs, traces, and metrics visible in one place, then use those signals to explain system behavior during normal traffic, latency spikes, failures, and business checkout activity.

The demo is designed for a showcase conversation, not just a technical proof. It lets you tell a complete story:

- What is happening in the application right now?
- Is traffic increasing or decreasing?
- Are users seeing errors?
- Which business tenant is affected?
- Are checkout failures concentrated by region?
- Can an operator move from a high-level KPI to raw logs and traces?

## Components

The project contains three main runtime services:

- `app`: Spring Boot service running on `http://localhost:8080`
- `grafana-lgtm`: Grafana all-in-one observability stack with Loki, Grafana, Tempo, Prometheus, and OpenTelemetry Collector
- `loadgen`: optional k6 traffic generator enabled through the Compose `loadgen` profile

The app exports OpenTelemetry data over OTLP HTTP:

- Logs: `http://grafana-lgtm:4318/v1/logs`
- Traces: `http://grafana-lgtm:4318/v1/traces`
- Metrics: `http://grafana-lgtm:4318/v1/metrics`

Grafana is available at:

```text
http://localhost:3000
```

Default credentials:

```text
admin / admin
```

## Architecture

```text
Browser / k6 / curl
        |
        v
Spring Boot app :8080
        |
        | OTLP HTTP logs, traces, metrics
        v
Grafana LGTM :4318
        |
        +-- Loki for logs
        +-- Tempo for traces
        +-- Prometheus for metrics
        +-- Grafana dashboard on :3000
```

The dashboard is provisioned automatically from:

```text
dashboards/logs-dashboard.json
```

The provisioning config is mounted from:

```text
dashboards/dashboards-provisioning.yaml
```

## Application Endpoints

Use these endpoints to create different observability patterns.

| Endpoint | Purpose |
| --- | --- |
| `/` | Normal low-latency request with an `INFO` log |
| `/greet/{name}` | Normal request with path variable and small simulated work |
| `/slow` | Latency scenario with a 500 ms delay |
| `/unstable?failPercent=60` | Synthetic reliability issue with configurable error rate |
| `/chatter/{count}` | Burst of many log lines |
| `/checkout/{tenant}?failRate=0.25&items=4` | Business checkout event with tenant, region, amount, latency, and success/failure |

Checkout logs are intentionally structured as logfmt-style business events:

```text
event=checkout tenant=acme region=us-east status=SUCCESS amount=52.30 items=4 latency_ms=311 payment_method=card
event=checkout tenant=acme region=us-west status=FAILED amount=41.20 items=4 latency_ms=870 payment_method=wallet error_code=PAYMENT_DECLINED
```

These structured logs power the enterprise panels in Grafana.

## Start The Demo

From WSL:

```bash
docker compose -f /mnt/d/demo/otel/compose.yaml up -d --build
```

Open Grafana:

```text
http://localhost:3000
```

The dashboard should open as:

```text
OTEL Logs Dashboard
```

If you change the dashboard JSON, recreate Grafana so provisioning reloads:

```bash
docker compose -f /mnt/d/demo/otel/compose.yaml up -d --force-recreate grafana-lgtm
```

## Generate Traffic

Start continuous k6 load:

```bash
docker compose -f /mnt/d/demo/otel/compose.yaml --profile loadgen up -d loadgen
```

Generate normal traffic:

```bash
for i in {1..300}; do curl -s http://localhost:8080/ >/dev/null; done
```

Generate latency traffic:

```bash
for i in {1..150}; do curl -s http://localhost:8080/slow >/dev/null; done
```

Generate error traffic:

```bash
for i in {1..200}; do curl -s "http://localhost:8080/unstable?failPercent=80" >/dev/null; done
```

Generate business checkout traffic:

```bash
for tenant in acme globex initech umbrella wayne; do
  for i in {1..80}; do curl -s "http://localhost:8080/checkout/$tenant?failRate=0.25&items=4" >/dev/null; done
done
```

Generate a log burst:

```bash
curl -s http://localhost:8080/chatter/200 >/dev/null
```

## Dashboard Panels

The provisioned dashboard includes platform reliability and business observability panels.

Reliability panels:

- `Log Throughput`: total log rate from the service
- `Error Throughput`: error-like log rate
- `Error Ratio (5m)`: percentage of logs that look like failures
- `Application Logs`: raw live application logs
- `Error Log Stream`: focused error and failure logs

Business panels:

- `Checkout Success Ratio (5m)`: successful checkout percentage
- `Checkout Revenue / min`: estimated revenue rate from successful checkout logs
- `Checkout P95 Latency (5m)`: checkout latency from structured log fields
- `Checkout Volume (5m)`: checkout event count
- `Checkout Throughput By Tenant`: tenant-level business traffic
- `Checkout Failures By Region`: regional failure distribution
- `Checkout Event Stream`: raw checkout business events

Interactive filters:

- `Service`: service-level selector from Loki labels
- `Tenant`: filters checkout panels by tenant (`All`, `acme`, `globex`, `initech`, `umbrella`, `wayne`)

## Dashboard Panel Logic

All dashboard panels use Loki as the primary data source. The dashboard turns application logs into operational and business signals using LogQL selectors, text filters, `logfmt` parsing, rates, counts, and percentile calculations.

### `Log Throughput`

Purpose:

Shows total log volume per second for the selected service. This is the first signal that traffic is reaching the application and logs are being ingested by Loki.

Logic:

```logql
sum(rate({service_name=~"$service"}[1m]))
```

How to explain it:

When normal traffic or load generation starts, this panel rises. If this panel is flat while traffic is expected, check app logs, OTLP export, or Loki ingestion first.

### `Error Throughput`

Purpose:

Shows how many failure-like logs are being emitted per second.

Logic:

```logql
sum(rate({service_name=~"$service"} |~ "(?i)(error|exception|failed|synthetic demo failure)" [1m])) or vector(0)
```

How to explain it:

This panel moves when `/unstable` fails or checkout failures occur. It uses a case-insensitive regex so it catches both structured `ERROR` logs and message text containing words like `failed`.

### `Error Ratio (5m)`

Purpose:

Shows the percentage of recent logs that look like failures. This gives a fast health signal instead of only showing raw error count.

Logic:

```logql
(100 * (sum(rate({service_name=~"$service"} |~ "(?i)(error|exception|failed|synthetic demo failure)" [5m])) / sum(rate({service_name=~"$service"}[5m])))) or vector(0)
```

How to explain it:

This converts error log rate into a percentage of total log rate over five minutes. During `/unstable?failPercent=80`, this should rise sharply. During normal traffic, it should fall back toward zero.

### `Application Logs`

Purpose:

Shows the raw live log stream for the selected service.

Logic:

```logql
{service_name=~"$service"}
```

How to explain it:

This is the operator's direct evidence panel. It shows severity, timestamps, trace IDs, span IDs, and messages. It is useful after a KPI changes and someone asks, "What exactly happened?"

### `Error Log Stream`

Purpose:

Shows only logs that look like failures.

Logic:

```logql
{service_name=~"$service"} |~ "(?i)(error|exception|failed|synthetic demo failure)"
```

How to explain it:

This narrows the raw log stream to incidents. It is useful for demonstrating a workflow from high-level error spike to concrete failed requests.

### `Checkout Success Ratio (5m)`

Purpose:

Shows business reliability: successful checkout events as a percentage of all checkout events.

Logic:

```logql
(100 * (sum(rate({service_name=~"$service"} |= "event=checkout" |= "status=SUCCESS" | logfmt | tenant=~"$tenant" [5m])) / sum(rate({service_name=~"$service"} |= "event=checkout" | logfmt | tenant=~"$tenant" [5m])))) or vector(0)
```

How to explain it:

The app logs checkout events as structured `logfmt`. This panel parses the log fields, filters by tenant, and compares successful checkouts against total checkout attempts.

### `Checkout Revenue / min`

Purpose:

Shows estimated revenue per minute from successful checkout logs.

Logic:

```logql
(sum(rate({service_name=~"$service"} |= "event=checkout" | logfmt | tenant=~"$tenant" | status="SUCCESS" | unwrap amount | __error__="" [1m])) * 60) or vector(0)
```

How to explain it:

The `amount` field is parsed from checkout logs and unwrapped into a numeric value. The panel sums successful checkout amount rate and multiplies by 60 to present revenue per minute.

### `Checkout P95 Latency (5m)`

Purpose:

Shows the 95th percentile checkout latency, derived from the structured `latency_ms` field in logs.

Logic:

```logql
max(quantile_over_time(0.95, {service_name=~"$service"} |= "event=checkout" | logfmt | tenant=~"$tenant" | unwrap latency_ms | __error__="" [5m])) or vector(0)
```

How to explain it:

This panel demonstrates that structured logs can be used for latency analysis even when the source is a log line. It answers, "How slow are the slowest normal checkout experiences?"

### `Checkout Volume (5m)`

Purpose:

Shows total checkout attempts in the last five minutes.

Logic:

```logql
sum(count_over_time({service_name=~"$service"} |= "event=checkout" | logfmt | tenant=~"$tenant" [5m])) or vector(0)
```

How to explain it:

This is the business traffic counter. It helps distinguish "no errors because nothing is happening" from "no errors while checkouts are actively running."

### `Checkout Throughput By Tenant`

Purpose:

Shows checkout traffic split by tenant.

Logic:

```logql
sum by (tenant) (rate({service_name=~"$service"} |= "event=checkout" | logfmt | tenant=~"$tenant" [5m]))
```

How to explain it:

The `tenant` field is parsed from checkout logs. This panel makes it easy to compare customer traffic patterns and then use the `Tenant` filter to zoom in.

### `Checkout Failures By Region`

Purpose:

Shows failed checkout rate grouped by region.

Logic:

```logql
sum by (region) (rate({service_name=~"$service"} |= "event=checkout" |= "status=FAILED" | logfmt | tenant=~"$tenant" [5m])) or vector(0)
```

How to explain it:

This panel turns failure logs into a regional incident signal. If one region is higher than the others, the demo can pivot into region-specific investigation.

### `Checkout Event Stream`

Purpose:

Shows raw business checkout events after parsing them with `logfmt`.

Logic:

```logql
{service_name=~"$service"} |= "event=checkout" | logfmt | tenant=~"$tenant"
```

How to explain it:

This is the detail view for the business panels. It shows the actual tenant, region, status, amount, item count, latency, payment method, and error code behind the summary numbers.

## Suggested Demo Story

1. Start with the dashboard quiet and explain that the app is exporting OpenTelemetry signals to Grafana LGTM.
2. Start the k6 load generator and show `Log Throughput` rising.
3. Trigger `/slow` and explain latency as a user-impact signal.
4. Trigger `/unstable?failPercent=80` and show `Error Throughput`, `Error Ratio`, and `Error Log Stream`.
5. Trigger checkout traffic and move to business panels.
6. Use the `Tenant` filter to isolate one customer and show tenant-specific checkout health.
7. Use `Checkout Failures By Region` to show how logs can become operational insight.
8. Open raw logs and point out trace IDs, span IDs, severity, and structured checkout fields.
9. Move to Grafana Explore for Loki or Tempo if you want to show investigation from logs to traces.

## Useful Explore Queries

All logs for the service:

```logql
{service_name="otel"}
```

Only failures:

```logql
{service_name="otel"} |~ "(?i)(error|exception|failed|synthetic demo failure)"
```

Checkout events:

```logql
{service_name="otel"} |= "event=checkout"
```

Checkout failures:

```logql
{service_name="otel"} |= "event=checkout" |= "status=FAILED"
```

Checkout traffic by tenant:

```logql
sum by (tenant) (rate({service_name="otel"} |= "event=checkout" | logfmt [5m]))
```

Checkout P95 latency:

```logql
max(quantile_over_time(0.95, {service_name="otel"} |= "event=checkout" | logfmt | unwrap latency_ms | __error__="" [5m]))
```

## Troubleshooting

Check containers:

```bash
docker ps
```

Check app logs:

```bash
docker compose -f /mnt/d/demo/otel/compose.yaml logs --tail=200 app
```

Check LGTM logs:

```bash
docker compose -f /mnt/d/demo/otel/compose.yaml logs --tail=200 grafana-lgtm
```

If Grafana panels show no data:

- Set time range to `Last 1 hour` or `Last 3 hours`
- Run `{service_name="otel"}` in Grafana Explore with Loki selected
- Generate fresh traffic with `curl`
- Recreate Grafana after dashboard JSON changes

If the dashboard shows old panel definitions:

```bash
docker compose -f /mnt/d/demo/otel/compose.yaml up -d --force-recreate grafana-lgtm
```

If application changes do not appear:

```bash
docker compose -f /mnt/d/demo/otel/compose.yaml up -d --build app
```

Stop everything:

```bash
docker compose -f /mnt/d/demo/otel/compose.yaml --profile loadgen down
```

## Demo Outcome

By the end of the demo, the audience should see that OpenTelemetry and Grafana can provide a single operational view across technical and business signals. The dashboard demonstrates how raw application logs can support reliability monitoring, tenant-level diagnosis, regional failure analysis, and business KPI tracking without changing tools during investigation.
