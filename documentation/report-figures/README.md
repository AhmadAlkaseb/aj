# Report figure sources

Compact overviews for readable A4 figures. Complete editable UML and field-level models
remain in [application](../application/README.md); these views deliberately omit details.
Regenerate SVGs with `python scripts/render_report_figures.py`.

## architecture

```mermaid
flowchart TB
    Browser[Browser] --> Frontend[Frontend] --> Gateway[API Gateway]
    Gateway --> Identity[Keycloak: identity]
    Gateway --> Domains[Users / Products / Orders / Payments]
    Gateway --> View[Product View: GraphQL catalogue]
    Domains --> SQL[Four private PostgreSQL stores]
    Domains <--> MQ[RabbitMQ: events and recovery]
    MQ --> Sync[Product Synchronizer]
    Sync --> View --> Mongo[Private MongoDB read model]
    MQ --> Job[One-shot PDF job]
    Job --> Domains
```

## databases

```mermaid
flowchart TB
    Users["Users owner<br/>Customer UUID + mutable profile<br/>Deletion marker + inbox/outbox"]
    Products["Products owner<br/>Car UUID + price + availability<br/>Reservation signals + inbox/outbox"]
    Orders["Orders owner<br/>Order UUID + customer/car UUIDs<br/>Status + deadline + audit + PDF"]
    Payments["Payments owner<br/>Payment UUID + order/customer UUIDs<br/>Amount + terminal outcome + audit"]
    Mongo["Product View owner<br/>MongoDB car snapshot<br/>Version + deletion tombstone"]
    Users -. customer UUID .-> Orders
    Products -. car UUID .-> Orders
    Orders -. order UUID .-> Payments
    Products -- asynchronous snapshot --> Mongo
```

## api-contracts

```mermaid
flowchart TB
    Public["Public reads<br/>GET /products<br/>POST /graphql"]
    Customer["Customer + admin<br/>POST /users/me<br/>POST /orders<br/>GET own orders / history / PDF"]
    Admin["Administrator<br/>Customer/car maintenance<br/>POST /products/description-draft<br/>POST /payments; PUT payment result"]
    Boundary["Frontend / Gateway: /api/v1<br/>Each domain backend enforces its own access rules"]
    Internal["Internal writers<br/>PUT product projection: service token<br/>PUT order confirmation: service token"]
    Public --> Boundary
    Customer --> Boundary
    Admin --> Boundary
    Internal -. bypass public gateway .-> Owners[Product View / Orders owner APIs]
```

## api-responses

```mermaid
flowchart TB
    REST["REST JSON<br/>200 read/update; 201 creation<br/>204 deletion or stored internal write"]
    Business["Business references<br/>guid + linked resource UUIDs<br/>status + authoritative amounts<br/>Pending amount can be null"]
    GraphQL["GraphQL catalogue<br/>data.products: items, total, hasMore<br/>data.product: product or null<br/>Query failures: errors"]
    Failure["Error boundary<br/>status + message<br/>400 / 401 / 403 / 404 / 409 / 503"]
    Special["Other results<br/>AI: description + requiresApproval<br/>Confirmation: application/pdf<br/>Status: UP or DEGRADED"]
    REST --> Business
    REST --> Failure
    GraphQL --> Special
```
