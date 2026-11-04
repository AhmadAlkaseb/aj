# AJ Motors: frontend

Owns: **No database; same-origin browser proxy**. Local port: **3000**; container HTTP port: **7000**.

Start the common infrastructure with `docker compose -f compose.infrastructure.yml up -d`,
then `docker compose -f frontend/docker-compose.yml up -d --build` from the repository root.
Use either individual Compose projects or the root stack, because host ports overlap.
For a JVM build: `mvn -pl frontend -am verify` followed by `java -jar frontend/target/app.jar`.

Configuration uses environment variables in the service Compose file. SQL owners use
DB_URL/DB_USER/DB_PASSWORD, Flyway migrations then Hibernate validation. Existing
local volumes explicitly opt into the reviewed baseline using DB_BASELINE_EXISTING.
RABBITMQ_URL selects the broker; OIDC_ISSUER/OIDC_JWKS_URL select the trusted provider.
Never disable backend token verification to make a test pass.

`GET /health/live` checks the process; `GET /health` checks required dependencies;
`GET /metrics` exposes internal operational counters. The gateway exposes business
routes and status, but never forwards internal writer endpoints or metrics.

Contracts: [OpenAPI](../documentation/contracts/openapi/frontend.json),
[AsyncAPI](../documentation/contracts/asyncapi.json), [API guide](../documentation/API.md).
See [tests](../documentation/TESTING.md), [identity](../documentation/AUTH.md),
[async flow](../documentation/ASYNC.md) and [implementation decisions](../documentation/IMPLEMENTATION-PLAN.md).
Unit/integration checks run through Maven and the per-service GitHub workflow.
Docker-backed cooperation/security checks run once in the system workflow.
