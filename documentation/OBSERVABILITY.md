# Metrics, central logs and alerts

Start `docker compose -f compose.observability.yml up -d` alongside the root stack.
Grafana: http://localhost:13001 (`admin` / `aj-monitoring-local`, configurable).
Prometheus: http://localhost:9090. Loki is local on port 3100.
Monitoring may be stopped while Kubernetes runs to fit the workstation memory budget.

Applications expose internal /metrics endpoints; the gateway does not proxy them.
The dashboard covers request rate/latency/errors, queue depth, outbox age/count,
projection delivery lag and dead letters. RabbitMQ's Prometheus plugin enables
per-object queue metrics. Prometheus can reach both backend and frontend networks.

HTTP logs use bounded route patterns, numeric status, duration and correlation IDs.
The resulting order UUID follows command responses/proxy hops and all derived broker
messages. Alloy keeps separate container/service streams and collects only application
containers, not database logs with possible row values. It parses application timestamps,
ignores old container history at ingestion and persists its positions. Loki retains newly
collected logs for seven days. Tokens, passwords and request payloads are not logged.
The local Docker log collector is trusted operational infrastructure with Docker API access.

`python scripts/check_observability.py --failures` verifies nine live targets, the
provisioned Grafana dashboard, a correlated trace through Users/Orders/Products,
and a real firing dependency/outbox alert during a controlled broker interruption.
The broker is restarted in finally. It does not send email or messages to an external account.

Warnings require operator action; they do not automatically release paid inventory.
A dead-letter alert can remain active for a deliberately malformed examination message.
Inspect the event identity/reason before using the audited ReplayDeadLetter tool.
