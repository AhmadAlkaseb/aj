# API og beskeder

Basis: `http://localhost:3000/api/v1` (frontend-proxy) eller `http://localhost:8080/api/v1` (gateway).
JSON bruger camelCase. Offentlige referencer er UUID'er; interne SQL-ID'er forlader ikke en service.
Forretningskald er fortsat offentlige indtil SEC-01. Login/logout bruger lokal Keycloak/OIDC.
`GET /api/v1/auth/session` kræver et gyldigt access-token som Bearer og returnerer
`subject`, `username`, `name`, `customerGuid` og `roles`; manglende/ugyldigt token giver 401.
`GET /auth/config` i frontend returnerer offentlig OIDC-klientkonfiguration. Se [AUTH.md](AUTH.md).
Betalinger registreres manuelt; ingen Stripe.

## Brugere

`POST /users` → 201, `GET /users` → 200, `GET /users/{userGuid}` → 200,
`PUT /users/{userGuid}` → 200, `DELETE /users/{userGuid}` → 204.

POST og PUT:

```json
{
  "firstName": "Anne",
  "lastName": "Jensen",
  "email": "anne@example.dk",
  "phoneNumber": "12345678",
  "address": "Bilvej 1",
  "postalCode": "2100",
  "city": "København"
}
```

Fornavn, efternavn og gyldig e-mail er obligatoriske. E-mail er unik, også efter soft delete.
PUT returnerer `guid` og `updatedAt`. Andre læsesvar indeholder de offentlige brugerfelter.

## Biler

`POST /products` → 201, `GET /products` → 200, `GET /products?status=AVAILABLE` → 200,
`GET /products/{productGuid}` → 200, `PUT /products/{productGuid}` → 200,
`DELETE /products/{productGuid}` → 204.

POST og PUT (PUT erstatter bilens redigerbare felter):

```json
{
  "brand": "Volvo",
  "model": "EX30",
  "year": 2026,
  "description": "Velholdt bil med god komfort.",
  "price": 279995.00,
  "currency": "DKK",
  "status": "AVAILABLE",
  "mileage": 12000,
  "color": "Grøn",
  "fuelType": "El",
  "transmission": "Automatisk",
  "engine": { "type": "El", "powerHp": 272, "capacityLiters": 0 },
  "performance": { "topSpeedKmh": 180, "zeroTo100": 5.7 },
  "dimensions": { "length": 4233, "width": 1837, "height": 1550 },
  "equipment": ["Bakkamera", "Sædevarme"],
  "images": []
}
```

Mærke, model og pris er obligatoriske. Pris er ikke-negativ med højst to decimaler.
Dimensioner er i millimeter. `status` kan sættes til AVAILABLE eller UNAVAILABLE.
RESERVED og SOLD styres af ordreflowet. Reserverede/solgte biler kan hverken redigeres eller slettes.

Gatewayen sender GET til Product View/MongoDB og skrivninger til Products/PostgreSQL.
Svar fra skrivninger er autoritative; kataloget opdateres asynkront og kan derfor være lidt bagefter.
Produktbeskeder indeholder et komplet snapshot med en stigende `version`.
Slettehændelser bevares som skjulte tombstones i MongoDB for at blokere gamle beskeder.

## Ordrer

`POST /orders` → 201:

```json
{
  "userGuid": "UUID fra Users",
  "productGuid": "UUID fra Products",
  "shippingAddress": "Bilvej 1, 2100 København",
  "notes": "Kontakt mig før levering"
}
```

Én ordre vedrører én bil. Services bruger GUID-referencer uden foreign keys mellem databaser.
Ordren starter PENDING med null beløb/valuta. Kundevalidering og reservation sker
via RabbitMQ: PENDING → RESERVING → RESERVED eller REJECTED. Products fastsætter
pris og valuta atomisk med reservationen; klientens prisfelter ignoreres.
201 betyder modtaget anmodning, og GET viser senere resultatet. Se [ASYNC.md](ASYNC.md).

`GET /orders`, `GET /orders/{orderGuid}`, `GET /users/{userGuid}/orders` → 200.
`PUT /orders/{orderGuid}` → 200 kan ændre `shippingAddress`, `notes` eller sætte `status: "CANCELLED"`,
mens ordren er PENDING, RESERVING eller RESERVED. `DELETE /orders/{orderGuid}` → 204 udfører soft delete og
frigiver en eventuel reservation. PAYMENT_PENDING/COMPLETED kan ikke slettes.

Statusforløb:

```text
PENDING → RESERVING → RESERVED → PAYMENT_PENDING → COMPLETED / FAILED
    └→ REJECTED       └→ CANCELLED
PENDING / RESERVING kan også annulleres; afvist bil giver REJECTED.
```

## Betalinger

`POST /payments` → 201:

```json
{ "orderGuid": "UUID fra Orders", "paymentMethod": "MANUAL" }
```

Metode: MANUAL, BANK_TRANSFER eller CASH. Beløb og valuta hentes fra ordren.
Betalingen starter REQUESTED med null beløb/valuta. Orders godkender en RESERVED
ordre asynkront og sender autoritative fakturafelter; betalingen bliver PENDING.
Ukendt eller ikke-betalbar ordre giver REJECTED. Kun PENDING kan registreres modtaget/mislykket.
Ét betalingsforsøg pr. ordre; gentagen oprettelse returnerer eksisterende betaling med 200.
Efter mislykket betaling kan kunden oprette en ny ordre på den frigivne bil.

`GET /payments`, `GET /payments/{paymentGuid}`, `GET /orders/{orderGuid}/payments` → 200.
`PUT /payments/{paymentGuid}` → 200 med `{"status":"COMPLETED"}` eller `{"status":"FAILED"}`.
En afsluttet betaling kan ikke ændre resultat; samme resultat kan gentages sikkert.
COMPLETED registrerer `paidAt`. Et lokalt `transactionReference` er en registreringsreference,
ikke dokumentation fra en betalingsudbyder.

Betalingsclaim udføres gennem `payment.requested` og `payment.authorized/rejected`.
Ordrelåsen forhindrer samtidig godkendelse af annullering og betalingsstart.
Der er ingen intern HTTP-betalingsroute. Anmodningen er gemt før claimet;
outbox-genlevering færdiggør forløbet efter midlertidigt nedbrud uden klientretry.
Nested samlinger returnerer `200 []` ved syntaktisk gyldige, ukendte referencer.

## Fejl og drift

400: ugyldigt input; 404: ukendt/slettet objekt; 409: unik konflikt eller ugyldig statusovergang;
503: database eller en nødvendig HTTP-service kan ikke kontaktes.

```json
{
  "status": 503,
  "service": "apigateway",
  "message": "Forbindelsen til tjenesten er afbrudt. ...",
  "timestamp": "2026-11-04T08:00:00Z"
}
```

`GET /health/live` viser, at processen kører. `GET /health` kontrollerer egne nødvendige
database-/RabbitMQ-forbindelser. Gatewayens `/health` kontrollerer selve gatewayen;
`GET /api/v1/status` samler de seks backend-services og Keycloak og returnerer UP eller DEGRADED.
Status-API'et returnerer 200 også ved DEGRADED, så frontend kan vise præcise delvise fejl.

## RabbitMQ

Exchange `aj.events` er en durable topic exchange. Der er fem durable forbrugerkøer:

| Kø | Routing keys | Ansvar |
| --- | --- | --- |
| aj.products | quantitycheck, order.cancelled, paymentfail, payment.completed | Reservér, frigiv eller sælg bilen |
| aj.orders | customer.valid, customer.invalid, quantityreserved, quantityrejected, payment.requested, paymentfail, payment.completed | Orkestrér ordre/reservation og godkend betalingsstart |
| aj.users | customer.check | Validér aktiv kunde |
| aj.payments | payment.authorized, payment.rejected | Afslut betalingsoprettelse |
| aj.product-sync | product.# | Opdatér MongoDB-læsemodellen |

Alle nye beskeder har `eventId`, `eventType`, `occurredAt`, `correlationId`,
`schemaVersion: 1` og `payload`. Event-ID matcher AMQP `messageId`;
produktets `payload.version` er en separat tilstandsversion.
Payloadfelter, korrelationsregler, bagudkompatibilitet og tidsgrænser er beskrevet i
[ASYNC.md](ASYNC.md). Gamle rå payloads accepteres ved dræning af eksisterende køer.

Alle SQL-skrivninger og deres outbox-beskeder gemmes i samme transaktion. Udsendelse bruger
persistent messages og publisher confirms. Consumeren kvitterer først efter behandling.
SQL-inbox forhindrer dobbeltbehandling. Mongo-projektionen ignorerer gamle/duplikerede versioner.
Fejl i forbindelser genforsøges; ugyldige beskeder flyttes til `aj.dead` via dead-letter exchange.
Køerne oprettes, før der publiceres, også hvis en modtagerservice endnu ikke er startet.

Implementationen følger [RabbitMQs beskrivelse af acknowledgements og publisher confirms](https://www.rabbitmq.com/docs/confirms).
Kør én instans af hver worker i denne lokale opsætning; replikering kræver yderligere koordinering af outbox og projektion.

## Internt projektionsinterface

Synchronizer videresender `product.*`-snapshots til Product View gennem
`PUT /internal/projections/products/{productGuid}` med `X-Projection-Token`.
Kun Product View tilgår MongoDB. Ejerens 204-svar gives efter lagring eller ved
en allerede overhalet version; herefter kvitteres RabbitMQ-beskeden.
Fejl medfører genforsøg, mens ugyldige 400-events dead-letteres.
Se [PROJECTION.md](PROJECTION.md) for envelope, health, adgang og netværk.
