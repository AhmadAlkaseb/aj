# Metrics, central logs and alerts

## Dashboards og beskedstatus

Grafana har seks provisionerede dashboards. **AJ Control Center** er den fælles
startside med links til alle database-GUI’er, Kubernetes og de øvrige værktøjer.
Se [lokal opsætning](LOCAL_SETUP.md) for adgang og fælles login.
De fem driftsdashboards er **AJ Motors operations**,
**AJ · HTTP og services**, **AJ · RabbitMQ og beskedlevering**,
**AJ · Logs og fejlsøgning** og **AJ · Kubernetes drift**.
Kubernetes-dashboardets data kræver Kubernetes-kørsel; Compose har ingen pods.
Dashboards genereres med `python observability/generate_dashboards.py`.

| Status | Hvor ses den? | Betydning |
| --- | --- | --- |
| Venter på afsendelse | Grafana: outbox pending/alder | Gemt i appens database, endnu ikke bekræftet af broker |
| Sendt til RabbitMQ | Publish/confirm-rater og `broker.published` i Loki | Broker har bekræftet modtagelsen; outbox-rækken fjernes efter transaktionens commit |
| Venter på modtager | RabbitMQ: Ready | Ligger i køen og er klar til levering |
| Under behandling | RabbitMQ: Unacked | Leveret til en forbruger, afventer kvittering |
| Behandlet | Ack-rate og `broker.acked` i Loki | Appen har behandlet beskeden og sendt kvittering |
| Afvist | `aj.dead` og `broker.rejected` | Kræver operatørens undersøgelse |

Åbn RabbitMQ → **Queues and Streams** → vælg eksempelvis `aj.orders`.
Se **Ready**, **Unacked**, **Total** og grafer for indgående beskeder og
kvitteringer. Appens workers bruger polling (`basic.get`), så Consumers kan
vise nul selv om beskeder behandles. Hurtige beskeder kan være behandlet
mellem to målinger. Søg i Loki efter eventId/correlationId for historikken.
En HTTP-forespørgsel bliver kun synlig i RabbitMQ, hvis den udløser en event;
almindelige læseforespørgsler går gennem HTTP.

RabbitMQ er ikke et arkiv over allerede kvitterede beskedpayloads. Brug ikke
**Get messages**, purge eller delete på forretningskøer til at undersøge
historikken, da det kan påvirke leveringen. Loki gemmer metadata om forløbet.
Broker-confirm er ikke en garanti for, at en forretningsproces er afsluttet;
ved genforsøg kan den samme event forekomme flere gange, og Inbox deduplikerer.

Se [Kubernetes-vejledningen](KUBERNETES.md) for drift, adgangskoder og kontrol
af den samlede opsætning i klyngen.

The following commands apply to Compose. For Kubernetes use
`python scripts/kubernetes.py monitoring` and `python scripts/check_operations.py --runtime kubernetes`.
Monitoring belongs to the root **aj** Compose project and starts with `./scripts/start.ps1`.
The normal `./scripts/start.ps1` starts monitoring automatically with the full root stack.
Monitoring containers restart automatically with Docker Desktop unless explicitly stopped.
Grafana: http://localhost:13001 (`admin` / `Admin123!`, centrally configurable with `AJ_ADMIN_PASSWORD`).
Prometheus: http://localhost:9090. Loki is local on port 3100.
Monitoring may be stopped while Kubernetes runs to fit the workstation memory budget.

In Grafana, open **Explore**, select **Loki**, and use `{project="aj"}` for all
application logs or `{project="aj", service="orders"}` for Orders. Add
`|= "your-order-uuid"` to follow an order across services. The provisioned
**AJ Motors operations** dashboard also includes central logs.

Check startup with `docker compose ps` and
`curl.exe http://localhost:3100/ready` (expect `ready`; initial startup can take a minute).
Stop monitoring separately with `docker compose stop prometheus loki alloy grafana`.
The root stack's `docker compose down` also stops monitoring. Named volumes
preserve logs and Grafana settings across container recreation.

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
