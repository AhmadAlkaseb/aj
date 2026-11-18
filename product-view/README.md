# AJ Motors: product-view

Owns: **MongoDB product_view**. Local port: **7006**; container HTTP port: **7000**.

Paginated GraphQL searches use a process-local cache-aside cache keyed by validated
filters, offset and limit. Results expire after 20 seconds; the cache retains at
most 256 entries, each at most 256 KiB of serialised JSON, with LRU eviction.
Successful projection writes clear the cache, and generation checks prevent stale
in-flight reads from refilling it. Other replicas expire independently. REST lists,
single-product reads and private data are not cached. Products remains the authority
for reservation price and availability. `mvn -pl product-view -am verify -Pquality`
runs expiry, capacity, invalidation/race, immutable-result and store-boundary checks.

Start the common infrastructure with `docker compose -f compose.infrastructure.yml up -d`,
then `docker compose -f product-view/docker-compose.yml up -d --build` from the repository root.
Use either individual Compose projects or the root stack, because host ports overlap.
For a JVM build: `mvn -pl product-view -am verify` followed by `java -jar product-view/target/app.jar`.

Configuration uses environment variables in the service Compose file. SQL owners use
DB_URL/DB_USER/DB_PASSWORD, Flyway migrations then Hibernate validation. Existing
local volumes explicitly opt into the reviewed baseline using DB_BASELINE_EXISTING.
RABBITMQ_URL selects the broker; OIDC_ISSUER/OIDC_JWKS_URL select the trusted provider.
Never disable backend token verification to make a test pass.

`GET /health/live` checks the process; `GET /health` checks required dependencies;
`GET /metrics` exposes internal operational counters. The gateway exposes business
routes and status, but never forwards internal writer endpoints or metrics.

Contracts: [OpenAPI](../documentation/contracts/openapi/product-view.json),
[AsyncAPI](../documentation/contracts/asyncapi.json), [API guide](../documentation/API.md).
See [tests](../documentation/TESTING.md), [identity](../documentation/AUTH.md),
[async flow](../documentation/ASYNC.md) and [implementation decisions](../documentation/IMPLEMENTATION-PLAN.md).
Unit/integration checks run through Maven and the per-service GitHub workflow.
Docker-backed cooperation/security checks run once in the system workflow.
