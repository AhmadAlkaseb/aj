# Supplementary report details

Supporting implementation notes and full coverage matrices. The submitted report is [Report.md](Report.md); its compact appendices A–E are included in [Report.pdf](Report.pdf). This supplement is additional repository detail, not a missing delivered appendix. The current submission index is [here](submission/README.md).


This report covers the technical and project-management topics in Mandatory Assignments 1 and 2 and follows the report structure in the final project description. It distinguishes implemented behaviour from proposed work. It does not claim that the application already satisfies every final-project requirement. The remaining work is recorded in [TODO.md](TODO.md). Administrative upload instructions and presentation material are outside this report's scope. [1], [2], [3]

The report separates logical architecture (Figure 1), concrete Compose deployment (Figure 7) and local Kubernetes deployment (Figure 8). Supporting figures explain data ownership, contracts, delivery, recovery and monitoring. All exported models are vector figures; editable sources are indexed in [report-figures](report-figures/README.md), with full UML and field-level models in [application](application/README.md). Pandoc/Tectonic supplies an unnumbered cover, numbered contents and a page-count check against the thirty-page limit for four students. [5]

AJ Motors is an online platform for a small, selected collection of exclusive cars. A customer should be able to inspect a car, register their details, reserve an available vehicle and complete an order when payment is recorded. Staff need to maintain listings and follow reservations and payments. The business idea is taken from the supplied project example; a claim that scarcity increases sales has not been measured by this project. [4]

The implemented development system contains customer management, car management, a separate product catalogue, orders, manual payment registration and asynchronous catalogue synchronisation. Its frontend remains available when individual backend components fail and reports which functions are unavailable. It demonstrates distributed coordination rather than real financial settlement: the payment service records a result; it does not move money.

For examination demonstrations, startup automatically inserts 20 fictional customers, cars, orders and payments with stable references. Six examples are completed, four have pending payments and ten cars remain available after failed historical payments. Each service seeds only its own SQL data; catalogue snapshots use the existing outbox and projection workflow. Existing edits and soft-deleted identities are preserved. See [demonstration data](DEMO_DATA.md) for configuration and verification.

Stripe remains outside scope. Identity uses a self-hosted third-party Keycloak/OpenID Connect provider with imported exam accounts and self-registration. Signed access tokens are verified independently in each backend; customers access only their own profile/orders/payments and administrator actions are protected server-side. This is local OIDC integration, not federation to an external Google or Microsoft account. See [AUTH.md](AUTH.md) and [the identity decision](decisions/001-local-oidc.md). [3], [20], [31]

This view describes responsibilities and communication independently of the runtime. The frontend uses Keycloak for browser login, and backends validate tokens using its public keys. Products remains the write authority; Product Synchronizer relays events through Product View's internal API, so only Product View accesses MongoDB. The PDF processor writes through Orders rather than accessing its database. Figures 7 and 8 map these same responsibilities to different deployment mechanisms.

Users owns stable customer UUIDs, profile details and deletion markers in PostgreSQL. Unique email addresses are enforced locally. Imported identities carry administrator-controlled customer references; self-registered identities receive a stable profile derived from the trusted issuer/subject. Profile and login identity remain separate. Users consumes customer.check and emits customer.valid/invalid in its own transaction, without reading another database.

The original shared-database access was replaced by an explicit ownership boundary, recorded in [ADR-002](decisions/002-projection-ownership.md). MongoDB is on a private network connected only to Product View; its port is not exposed in normal startup. The internal writer is not forwarded by the public gateway and requires a separate service credential. Existing data and volumes are retained. See [PROJECTION.md](PROJECTION.md) for the interface and recovery contract. [3]

Creating an order stores PENDING and `customer.check` atomically. Users validates the customer asynchronously; Orders then becomes RESERVING and publishes `quantitycheck`. Products reserves under its own lock and returns the authoritative amount/currency. Reservation events move the order to RESERVED or REJECTED; the initial response has null amount/currency. Payment results can subsequently move it to COMPLETED or FAILED. Cancellation publishes `order.cancelled`, allowing Products to compensate by releasing the reservation.

New customer/car validation expires after five minutes; an unpaid reservation after thirty minutes. Compensation releases eligible inventory. A PAYMENT_PENDING timeout becomes REVIEW_REQUIRED and keeps the car reserved while reconciliation asks Payments for its stored outcome. A late verified outcome remains valid. Historical demonstration fixtures are intentionally timeless. [RELIABILITY.md](RELIABILITY.md) documents these rules and their limits.

Payments privately stores a REQUESTED record before publishing payment.requested. Orders claims eligible inventory and replies with authoritative invoice fields; Payments becomes PENDING or REJECTED. A per-order lock and deterministic payment identity serialise concurrent requests. Retries return the existing payment and current state. A terminal result cannot be changed, and ambiguous outcomes require investigation rather than an automatic financial-failure assumption.

The diagrams reference generated, validated [OpenAPI contracts](contracts/openapi/frontend.json), a vendored Swagger UI at /swagger/index.html, the [GraphQL schema](../product-view/src/main/resources/catalogue.graphqls) and [AsyncAPI contracts](contracts/asyncapi.json). Per-service READMEs explain responsibility, startup, configuration and tests. The standard contracts complement the diagrams rather than replacing behavioural explanation. [21], [22]

The queues are `aj.users`, `aj.products`, `aj.orders`, `aj.payments`, `aj.product-sync` and the isolated `aj.confirmations` queue. Invalid messages can be moved through the dead-letter exchange to `aj.dead`; transient dependency failures are retried. Transient retries use backoff without an automatic retry-count cap; poison messages dead-letter. An operator replay tool and central queue/dead-letter alerts are configured; operator decisions remain necessary for persistent faults.

Reservation requests include order/product/customer references and initially null price fields; confirmation adds the authoritative amount/currency. Payment requests include order/payment identifiers; authorisation adds invoice fields, and outcomes include the product reference. Product messages contain the complete public snapshot. Consumers validate common metadata and accept old raw payloads to drain existing queues; the local stack must be upgraded together. Timeout, retry and compatibility rules are in [ASYNC.md](ASYNC.md). Aggregate and per-service AsyncAPI contracts document payloads, producers, consumers and queue bindings. [4], [22]

HTTP provides immediate user-facing commands and queries, health status and OIDC/JWKS exchange. Customer validation, product reservation, payment initiation and outcomes use RabbitMQ, so these domain interactions can wait for an unavailable consumer. Nested collections filter local data and return empty lists for unknown UUIDs. The remaining internal projection HTTP call preserves Product View as sole MongoDB owner and supplies storage confirmation before broker acknowledgement. These boundaries are recorded in [ADR-003](decisions/003-asynchronous-commerce.md). [3]

Products is the command-side authority, while Product View serves a separate MongoDB query representation. This is a concrete application of CQRS to a read-oriented catalogue rather than a requirement to split every domain. The additional projection introduces eventual consistency and another operational dependency. CQRS permits different read and write models, but does not by itself prove better performance; the project has no comparative benchmark. [6]

The immutable information is deliberately scoped: removal markers, versioned snapshots and append-only order/payment status audit. Profile and product detail fields remain mutable, and confirmed outbox entries are removed. There is no full event-sourcing archive or general time-travel query. Exam retention keeps identity/deletion fences, inbox keys, history and tombstones; a production erasure schedule requires a separate policy.

Users, Products and Orders support identity-scoped Idempotency-Key headers. A request fingerprint, original result and outbox commit in the owning SQL transaction. Identical concurrent retries return one resource; reusing a key with another body returns 409. Payments deliberately uses one stable payment per order and returns its current result on retry. Requests without command keys do not gain a general creation guarantee.

Products retains reservation signals so cancellation/failure received before quantitycheck blocks a late reservation. Paid inventory cannot be freed by a stale cancellation. Orders retains early matching payment outcomes until a valid claim exists. Projection CAS and tombstones converge to the newest version under reordering. Selected permutations and duplicate deliveries are tested; contradictory producer outcomes are not declared equivalent histories. Workers remain single-replica with Recreate rollouts, while the stateless frontend scales independently. [3]

A domain update and its outgoing outbox record commit in one local SQL transaction. A worker publishes the stored message and removes the outbox record only after broker confirmation. If publication succeeds but the database acknowledgement is interrupted, the same event may be published again. Inbox deduplication and projection versions handle selected duplicate cases. This implements the central outbox idea without a database/broker distributed transaction. [9]

Image builds use a bounded matrix, registry cache, SBOM and provenance. Fixable HIGH/CRITICAL image findings block promotion. The assembled artifact binds all nine digests to one verified source revision. Staging runs first; production promotes the same images without rebuilding. Environment-specific runner access, HTTPS endpoints, credentials and registry pull access must be configured as described in [CI-CD.md](CI-CD.md).

Deployment reads live target configuration and changes application images/revision annotations. Server-side validation and four bounded owner Flyway Jobs precede application changes; proof files bind migrations to the release and unchanged target images. Readiness is followed by real HTTPS, catalogue and identity checks, plus synthetic reservation/payment-compensation smoke in staging. Production defaults to read-only smoke. Event workers retain single-replica Recreate deployments; zero downtime is not promised.

A separate workflow restores an earlier successful release's digests only with explicit compatibility with the current schema. Automatic restoration is optional under the same reviewed compatibility policy and requires a complete previous snapshot. Failed deployments remain failed even if restoration succeeds. Migration, rollout and smoke evidence is retained without credential files. The latest combined Python suite passed 47 deployment/CI/federation checks; earlier TLS-proxied Compose smoke passed; remote GitHub execution and a digest-based Kubernetes rollout are not inferred from those checks.

Compose monitoring belongs to project `aj`; Kubernetes deploys its own Prometheus, Loki, Alloy, Grafana and kube-state-metrics. The 12 November checks validated six dashboards and thirty-five panel queries, with nine healthy Compose targets; the earlier active Kubernetes baseline had ten targets. An isolated broker test observed publish, queue readiness and consumption. Earlier correlation/outage checks remain separately identified in [EVIDENCE.md](EVIDENCE.md). These measurements describe observed operation, not long-term availability. See [OBSERVABILITY.md](OBSERVABILITY.md).

AJ Motors has a functional local distributed prototype with five domain/API backends, an additional projection worker, a frontend, a gateway, isolated SQL databases and RabbitMQ. Selected workflows demonstrate exclusive reservation, manual payment outcomes, catalogue projection and recovery after dependency outages. The inspected Java test evidence contains 28 passing unit tests and 14 passing integration tests; the previously reviewed system run contains six passing scenarios. Automatic fictional data supports a demonstration without entering every example manually.

The implementation now covers the local commerce, identity, GraphQL, AI, migration, reliability, contract and operational work described here. The evidence table distinguishes successfully verified behaviour from definitions awaiting an actual run. The user selected local verification as the alternative to unavailable GitHub authentication; remote results remain explicitly unverified. The dated collaboration plan and individual reflection drafts require author confirmation before they are described as historical experience.

The project demonstrates separate ownership, asynchronous business coordination, mixed API styles, local identity/AI integration and operational verification. The practical lesson is that independent deployment requires explicit contracts, retained state and recovery rules. Local evidence, remote delivery and the authors’ reflection remain separate claims; no production availability or unverified collaboration history is asserted.

# 10. Appendix

## Appendix A. Mandatory Assignment 1 coverage

The matrix maps the substantive requirements of Assignment 1 to this report. A section containing a plan is not evidence that the corresponding system feature has already been built.

| Assignment topic | Report coverage | Remaining qualification |
| --- | --- | --- |
| Group and project idea | Cover; 1.1; 5.1 | Full names confirmed; group-process evidence still needed |
| Functional requirements | 1.2, FR-01–FR-13 | Implemented and missing capabilities are separated |
| Non-functional requirements | 1.2, NFR-01–NFR-11 | Two measured performance runs; availability remains a target |
| Project-management strategy | 5.1–5.2 | Agreed lightweight process; actual historical examples require author confirmation |
| Technology stack and choices | 1.3 | Corrected to the actual frontend and manual payment scope |
| Public GitHub monorepo | Cover; 5.2–5.3 | Public visibility verified; actual per-student contribution evidence still required |
| Architecture diagram | 2.1, Figure 1 | Current implementation diagram |
| Frontend with REST and GraphQL | 2.3 | Implemented GraphQL catalogue and REST commands |
| Communication channels | 2.3 | HTTP, AMQP, producers and consumers described |
| Architectural patterns | 2.4 | Implemented mechanisms and missing guarantees distinguished |
| Team responsibility division | 5.2 | Balanced named future owners/reviewers agreed; historical work still requires evidence |
| Cloud provider investigation/selection | 3.3 | AWS/Google/Azure compared; Azure proposed, not provisioned |
| CI/CD pipeline | 3.2.2 | Gated quality/system/release workflows defined; remote run and publication evidence separate |
| Unit/integration/cooperation strategy | 4.1–4.8 | Test evidence and coverage gaps documented |
| Production logging/monitoring options | 3.2.3 | Prometheus, Loki, Grafana and a real controlled-alert demonstration |
| Documentation strategy | 5.4 | Current files and required standards identified |
| Code/database/API versioning | 5.3 | Flyway migrations, persisted-message v1 compatibility and bounded rollout policy |
| Begin final-report structure | Sections 1–10 | This working report follows the required structure |

## Appendix B. Mandatory Assignment 2 coverage

| Assignment topic | Current evidence | Report section |
| --- | --- | --- |
| Continue implementing Assignment 1 design | Functional car/customer/order/payment prototype; differences from example disclosed | 1.1; 2.2 |
| At least partly functional development system | Docker Compose topology and selected passing business flows | 3.1; 4.7 |
| Containerised services, databases and queue | Application Dockerfiles; four PostgreSQL containers; MongoDB; RabbitMQ | 3.1 |
| Basic automated unit or integration tests executable in CI | Surefire/Failsafe tests and service workflows | 4.2–4.3; 4.7 |
| At least one asynchronous microservice interaction | Reservation results, payment outcomes and product projection | 2.3–2.4 |
| Existing-system documentation | Monorepo README, API/testing guides, UML/SVGs | 5.4 |
| Clear deployment plan; deployment not yet mandatory at this stage | Verified local Kubernetes; separately proposed Azure environment | 3.2–3.3 |
| Continue report with implementation work | Current system behaviour and verification evidence | 2; 3.1; 4 |

## Appendix C. Final-project requirement assessment

| Requirement | Implemented evidence / boundary |
| --- | --- |
| At least five backend applications | Users, Products, Orders, Payments and Product View; separate synchronizer and isolated job |
| Frontend uses multiple services and both API styles | GraphQL catalogue/details followed by REST reservation/administration |
| Functional/non-functional requirements | Section 1.2; measured targets remain distinct from demonstrated guarantees |
| Primarily asynchronous backend interaction | Customer check, inventory reservation, payment initiation/outcomes and projection events |
| Token identity and authorisation | Real Keycloak/OIDC, independent backend verification, owner filtering and administrator operations |
| Actual AI API integration | Products-to-local-Ollama adapter, bounded output and human-approved description workflow |
| Independent deployment and own data | Separate images/processes, private owner stores, authenticated projection/PDF writer APIs |
| CQRS and immutable information | Separate catalogue projection, versioned snapshots, deletion tombstones and status audit |
| Idempotence | Identity-scoped command keys; deterministic payment identity; inbox/atomic projection/first PDF |
| Selected commutative handling | Retained release/payment signals and version precedence; contradictory producer histories excluded |
| Saga compensation/recovery | Deadlines, eligible inventory release, reconciliation and explicit manual review |
| Development environment | Root and per-service Compose, automatic demonstration fixtures |
| Production-like local deployment | kind manifests, owner policies, PVCs, TLS, migration gates, CPU HPA and KEDA ScaledJob; runtime result in EVIDENCE.md |
| Distributed monitoring | Prometheus/Grafana/Alloy/Loki; correlated trace and firing dependency alert verified |
| Versioning | Git, owner Flyway history, API v1, separate event schema and product-state versions |
| Unit/integration/security tests | Per-backend rules, real API/broker/database boundaries and signed-token/ownership checks |
| Self-starting backend cooperation | Four backends plus private dependencies, no frontend/gateway, migration/inbox/state assertions |
| Static checks and CI failure gates | SpotBugs, contract validation, Actionlint and configured CodeQL; actual remote evidence tracked separately |
| README and standard interfaces | Monorepo/service guides, OpenAPI/Swagger, GraphQL SDL and aggregate/per-service AsyncAPI |
| Backup/restore and onboarding | Isolated restore verification, local startup and Kubernetes lifecycle procedures |
| Source/release evidence | Existing Git history and configured gated release; successful run IDs/digests require actual publication |
| Collaboration/reflection | Agreed lightweight Scrum-inspired process and balanced allocation; historical individual evidence remains for authors |
| Final document | Report.md plus checked PDF export, figures, references and coverage appendices |

## Appendix D. Installation and verification procedure

Use Docker Desktop with Linux containers, Java 17/Maven for local builds, and Python with the documented browser/validation dependencies. The demonstrated workstation has 16 GB RAM; Compose, extended monitoring/AI and the local cluster can run in separate demonstrations.

```powershell
./scripts/start.ps1
# Equivalent local bootstrap (preserves/migrates volumes and configures logins):
python scripts/start_local.py
```

Open http://localhost:3000. The shared local administrator is admin / Admin123!, centrally configurable through AJ_ADMIN_USER and AJ_ADMIN_PASSWORD; imported customers kunde01..kunde20 retain Demo123!. These are fictional development accounts. Self-registration creates a separately owned customer profile. SQL owners automatically apply migrations and insert missing stable fixtures without overwriting edits.

```powershell
python scripts/verify_java.py --quality
python scripts/check_demo.py
python scripts/check_security.py
python scripts/check_reliability.py
python scripts/cooperation.py --exclusive
python scripts/backup_restore.py --verify
python scripts/check_observability.py --failures
python scripts/kubernetes.py start
python scripts/kubernetes.py verify
python scripts/kubernetes.py stop
```

Install Playwright/Chromium and validation dependencies as described in TESTING.md. Report export has a separate setup command, `./scripts/setup_report.ps1`, followed by `python scripts/export_report.py`; the root README explains the LaTeX toolchain. Run outage checks sequentially. The isolated Java copy avoids editor background compilation changing Maven output. Cooperation uses its own project/volumes; Kubernetes uses its own named cluster/PVCs and restores Compose afterwards. Never use volume deletion as an upgrade procedure. Source/procedure links: [KUBERNETES.md](KUBERNETES.md), [MIGRATIONS.md](MIGRATIONS.md), [OPERATIONS.md](OPERATIONS.md), [TESTING.md](TESTING.md).

## Appendix E. Evidence, documentation and outstanding author input

The [verification summary](EVIDENCE.md) links compact machine-readable local observations. Full logs, browser captures, temporary keys and backups remain outside version control. [TODO.md](TODO.md) contains only unfulfilled acceptance work; definitions are not presented as successful deployments.

The supplied example is not evidence of attendance, a completed sprint or a student's personal experience. The adopted process uses brief daily standups, short sprints, shared review and a lightweight retrospective. The responsibility allocation organises future work fairly; actual individual contributions must remain visible in genuine Git history. Authors must supply or confirm their historical examples and first-person reflections.

At the 12 November review the user could not authenticate GitHub and requested local verification instead; no remote success is assumed. [CI-STATUS.md](CI-STATUS.md) preserves its unverified remote status; no run IDs or published digests are asserted. Remote CI execution, branch protection and registry publication are documented only after actual GitHub operations. Local passing checks do not imply remote successful runs. The local single-node environment and the cloud deployment plan remain distinct.


[1]: <mandatory assignments/Mandatory assignment 1.pdf>

[2]: <mandatory assignments/Mandatory assignment 2.pdf>

[3]: <mandatory assignments/Final project description.pdf>

[4]: #9-references

[5]: https://katalog.kea.dk/course/9942251/2025-2026

[6]: https://learn.microsoft.com/en-us/azure/architecture/patterns/cqrs

[7]: https://www.rabbitmq.com/docs/confirms

[8]: https://microservices.io/patterns/data/saga.html

[9]: https://microservices.io/patterns/data/transactional-outbox.html

[10]: https://kubernetes.io/docs/concepts/workloads/controllers/deployment/

[11]: https://kubernetes.io/docs/concepts/workloads/autoscaling/horizontal-pod-autoscale/

[12]: https://keda.sh/docs/2.21/concepts/scaling-jobs/

[13]: https://docs.aws.amazon.com/AmazonECS/latest/developerguide/Welcome.html

[14]: https://docs.cloud.google.com/run/docs/overview/what-is-cloud-run

[15]: https://learn.microsoft.com/en-us/azure/container-apps/overview

[16]: https://learn.microsoft.com/en-us/azure/aks/what-is-aks

[17]: https://learn.microsoft.com/en-us/azure/postgresql/high-availability/concepts-high-availability

[18]: https://azure.microsoft.com/en-us/pricing/details/container-apps/

[19]: https://graphql.org/learn/

[20]: https://openid.net/specs/openid-connect-core-1_0.html

[21]: https://spec.openapis.org/oas/v3.1.1.html

[22]: https://www.asyncapi.com/docs/reference/specification/v2.6.0

[23]: https://documentation.red-gate.com/flyway/flyway-concepts/migrations

[24]: https://grafana.com/docs/loki/latest/get-started/overview/

[25]: https://prometheus.io/docs/introduction/overview/

[26]: https://docs.ollama.com/api/generate

[27]: https://maven.apache.org/surefire/maven-failsafe-plugin/

[28]: https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows

[29]: https://docs.github.com/en/code-security/code-scanning/introduction-to-code-scanning/about-code-scanning

[30]: https://www.mongodb.com/docs/manual/data-modeling/best-practices/

[31]: https://www.keycloak.org/securing-apps/javascript-adapter


## Additional implementation detail

The cost is distributed coordination. A database transaction cannot atomically commit an order, reserve a car and record a payment in three independent stores. Synchronous validation adds availability dependencies; asynchronous processing adds lag and requires explicit outcomes, retries and compensation. More deployable units also mean more configuration, health checks and diagnostic work.

Architecture decisions should record context, alternatives and consequences. The identity-provider scope decision is now recorded in ADR-001. Projection ownership is resolved in ADR-002 and asynchronous commerce in ADR-003; worker concurrency is explicitly bounded to one replica with Recreate deployments. Proposed features must remain labelled as proposed until their implementation and evidence are reviewed.

The worker exposes health endpoints but no public business API. Health covers RabbitMQ and the authenticated projection-owner connection. A delivery is acknowledged only after the owner's successful HTTP response. Network, storage and credential failures cause retry; invalid payloads rejected with 400 follow the dead-letter path. The worker accesses neither Products PostgreSQL nor MongoDB.

The frontend provides a public catalogue and role-aware customer, order and payment views. Product search/details use GraphQL; reservations and administrative operations use REST through the same-origin proxy and gateway. Customer lists are restricted to their own records by the backend, independently of hidden frontend controls. An administrator can request an AI description draft and review it before saving.

Frontend HPA uses real CPU metrics with a one-to-two replica range. A temporary workload verifies scaling and is removed afterwards. Confirmation jobs scale from their own queue and terminate after one task. SQL/broker workers remain at one replica with Recreate rollout, avoiding an unverified overlap of old/new workers. Projection CAS still protects concurrent HTTP snapshot writes. [11], [12]

Application, messaging and database scaling must be evaluated separately. More HTTP replicas may increase throughput until database connections or locks become the bottleneck. More worker replicas may reduce backlog only if their state transitions and duplicate handling remain correct. A single broker or database host still limits availability even when application replicas increase.

Documentation belongs next to the source it describes. The monorepo README explains onboarding, `API.md` describes current REST/messages, `TESTING.md` records verification commands, and `application/` contains editable UML and viewable SVGs. This report explains system-level decisions and should reference those contracts rather than embed all API details again.

The dashboards cover operations, HTTP/services, RabbitMQ/delivery, logs and Kubernetes operation. The delivery view distinguishes outbox backlog, Ready/Unacked queues, publish/confirm/acknowledgement rates and dead letters. Broker logs include `broker.published`, `broker.processed` and `broker.acked` with event/correlation IDs. An ordinary HTTP read produces request telemetry but no RabbitMQ event.

CQRS separates catalogue reads from authoritative writes while adding projection lag and recovery work. Tombstones and state audit preserve specific facts rather than a complete immutable database. Outbox/inbox support at-least-once recovery; stored saga signals and deadlines address selected ordering/abandonment cases. These mechanisms reduce particular failures while increasing operational complexity.

Product View exposes typed GraphQL catalogue queries used by the actual frontend. Selecting a car leads to a REST order request; Products still determines price and availability during the asynchronous reservation. The query API cannot mutate inventory or choose invoice amounts. This is application interoperability, separate from the older exercises in system-integration/. [3], [19]

Product View is the sole owner of the MongoDB projection. It does not consume RabbitMQ directly: its internal authenticated HTTP interface accepts full snapshots relayed by Product Synchronizer. The public query interface excludes deletion tombstones. A committed product update may temporarily be absent or outdated because delivery is asynchronous.

The implemented message envelope uses schemaVersion 1, separate from the product business-state version. Event identity/type/time/correlation are validated consistently. Unknown envelope versions dead-letter, and legacy raw payloads remain readable. This is persisted-message compatibility, not mixed-binary rolling-upgrade compatibility; [ASYNC.md](ASYNC.md) documents the deployment boundary.

Its PostgreSQL database supports local transactions and locking around competing reservations. Price and currency are checked again when the reservation command is processed, preventing an order based on an outdated quoted amount from being silently accepted at a different price. A matching successful payment changes the car to SOLD; an eligible cancellation or failure releases it.

The implemented reservation/payment flow uses local database transactions and compensating release events. Successful reservation followed by `paymentfail` returns an eligible car to AVAILABLE and marks the order FAILED. Cancellation similarly releases a reservation. This is a partial choreography-based saga: services react to outcomes rather than participating in one distributed transaction. [8]

Users can temporarily be unavailable while an accepted order waits in the durable workflow. Backend role checks and local ownership filters prevent direct-port access to another customer’s resources. Deleted profiles are hidden, while logical references and historical orders are retained according to the examination policy. A single SQL owner is still an availability boundary.

The main technical problem is coordinating changes that belong to different services. Reserving a car, creating an order and registering payment must produce a consistent business outcome even though their records are stored in different databases. An interrupted connection must not silently lose an accepted update, and competing buyers must not both obtain the same car.

The frontend also provides Keycloak login/logout, silent SSO and a validated identity display. Its public client uses Authorization Code + PKCE S256; tokens remain in memory and gateway verification checks signature, issuer, audience and lifetime. Identity data belongs to Keycloak, with customer UUID attributes linking the imported demonstration identities to customer profiles. [20], [31]

The current application therefore demonstrates an educational distributed architecture. A small real-world car retailer might reasonably begin with a modular monolith and one transactional database. Microservices are justified here by the course's distributed-systems objectives, not by an unmeasured claim that this small catalogue requires large-scale infrastructure. [3]

Public REST resources use `/api/v1` and JSON field names in camelCase. UUIDs identify resources. The common success responses are 200, 201 and 204; validation, absence, conflicts and unavailability use structured 400, 404, 409 and 503 responses respectively. Product list responses currently contain full snapshots, not a separate reduced listing DTO.

The gateway routes product reads to Product View, writes to Products and the remaining resource operations to their owning services. It aggregates backend health for the status view. This centralises public routing but also makes the gateway a failure boundary. If it is stopped, the frontend still serves its assets and displays an API connectivity error; no alternate gateway is automatically selected.

There are five separately runnable domain/API backends: Users, Products, Product View, Orders and Payments. Product Synchronizer is an additional backend worker. This count does not include the frontend, the gateway, databases, RabbitMQ or the shared build library. The design therefore has at least five backend applications without relying on the gateway's inclusion. [3]

Product Synchronizer consumes `product.*` snapshots and relays the original event identity, topic and snapshot to Product View. It has no MongoDB driver, database configuration or database-network access. Product View applies the newer version using conditional replacement and retains removal tombstones; stale and duplicate snapshots are acknowledged without overwriting newer state.

The amount is taken from an eligible order rather than accepted from the client. A deterministic payment identity and a unique order reference make repeated creation return the same record. A terminal result cannot be changed to the opposite result. Successful and failed outcomes are published as `payment.completed` and `paymentfail`.

Users, Products, Orders and Payments each use a main identifier table, a details table and a deletion table. The details relation is one-to-one; the deletion relation permits at most one marker through a unique local foreign key. This layout separates identity and removal metadata, but the details remain mutable. It is not an immutable history of every change.

The service consumes `quantitycheck`, `order.cancelled`, `paymentfail` and `payment.completed`. A successful reservation changes the car to RESERVED and publishes `quantityreserved`; rejection publishes `quantityrejected`. Product changes produce `product.created`, `product.updated`, `product.reserved` or `product.removed` for the read model.
