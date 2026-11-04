# Confirmation job

Consumes at most one `order.completed` event from its own durable `aj.confirmations`
queue, generates a PDF, writes through the authenticated Orders internal API,
acknowledges only after storage, and exits. No shared database or filesystem.

Build: `mvn -pl confirmation-job -am verify`.
Compose demonstration: `docker compose --profile jobs build confirmation-job`, then
`docker compose run --rm confirmation-job`. Kubernetes uses a KEDA ScaledJob.

Configure RABBITMQ_URL, ORDERS_URL and CONFIRMATION_TOKEN. Malformed payloads are
dead-lettered; temporary owner failures requeue and exit unsuccessfully.
Orders stores the first PDF per order atomically and ignores duplicate writes.
Download requires order ownership or administrator access.
