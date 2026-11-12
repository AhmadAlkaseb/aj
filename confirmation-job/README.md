# Confirmation job

Consumes at most one `order.completed` event from its own durable `aj.confirmations`
queue, generates a PDF, writes through the authenticated Orders internal API,
acknowledges only after storage, and exits. No shared database or filesystem.

Build: `mvn -pl confirmation-job -am verify`.
Normal root Compose startup runs this application continuously with
`CONFIRMATION_WORKER=true`. For a bounded demonstration use
`docker compose run --rm -e CONFIRMATION_WORKER=false confirmation-job`.
Kubernetes uses the default one-task-and-exit mode through a KEDA ScaledJob.

Configure RABBITMQ_URL, ORDERS_URL and CONFIRMATION_TOKEN. Malformed payloads are
dead-lettered; temporary owner failures requeue and exit unsuccessfully.
Orders stores the first PDF per order atomically and ignores duplicate writes.
Download requires order ownership or administrator access.
