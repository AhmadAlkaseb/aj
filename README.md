# AJ Motors

Et lokalt bilsalgssystem med selvstændige Java 17 / Maven / Javalin-microservices,
fire PostgreSQL-databaser, en MongoDB-læsemodel og RabbitMQ. Lokal Keycloak/OpenID Connect-login; ingen Stripe.

## Start hele systemet

Krav: Docker Desktop med Linux-containere og Docker Compose. Java/Maven er kun nødvendige
for builds uden Docker. Kør fra repository-roden:

```powershell
docker compose up -d --build
```

Åbn **http://localhost:3000**. Første build henter Maven-afhængigheder og Docker-images.
Ved opstart seedes 20 fiktive kunder, 20 biler, 20 ordrer og 20 betalinger automatisk.
Der er 10 ledige biler til nye reservationer. Genstart bevarer ændringer uden dubletter;
se [eksamensdata](documentation/DEMO_DATA.md) for forløb, kontrol og deaktivering.
Vent på grøn systemstatus. Tryk **Log ind** og brug `kunde01 / Demo123!` eller
`eksamen-admin / Admin123!`. Se [loginvejledningen](documentation/AUTH.md).
Rolle- og ejerskabsbeskyttelse af forretnings-API’erne fortsætter i SEC-01. Opret en kunde og en bil, reservér bilen, og gå til Ordrer
for at oprette en manuel betaling. Under Betalinger kan resultatet registreres som
modtaget eller mislykket. Kataloget opdateres asynkront.

Frontenden starter uafhængigt af backend. Den viser en statusoversigt og en dansk
fejlbesked med genforsøg, hvis gatewayen eller andre services er utilgængelige.
Formularer bevarer indholdet ved fejl. Tilgængelige dele kan stadig bruges.

```powershell
docker compose ps
docker compose logs -f orders
docker compose stop payments
docker compose start payments
docker compose down
```

`down` bevarer databaser og beskeder i navngivne volumes. Brug samme Compose-opsætning
ved efterfølgende start. Projektet ændrer ikke mapperne under `system-integration`.

## Projekter og porte

| Maven-projekt | Lokal port | Database / afhængighed |
| --- | --- | --- |
| frontend | 3000 | HTTP til gateway; ingen database |
| apigateway | 8080 | HTTP til services; ingen database |
| users | 7002 | Egen PostgreSQL, localhost:5433/users; RabbitMQ |
| products | 7003 | Egen PostgreSQL, localhost:5434/products; RabbitMQ |
| orders | 7004 | Egen PostgreSQL, localhost:5435/orders; RabbitMQ |
| payments | 7005 | Egen PostgreSQL, localhost:5436/payments; RabbitMQ |
| product-view | 7006 | Egen privat MongoDB product_view; debug-port 27018 er valgfri |
| product-synchronizer | 7007 | RabbitMQ → internt API i Product View; ingen databaseadgang |
| Keycloak | 8180 | Egen identitetsdatabase; importerer demo-login automatisk |
| service-common | — | Fælles buildbibliotek; ingen kørende service |

De otte Java-applikationer bruger intern app-port 7000. Lokale porte er bundet til 127.0.0.1.
PostgreSQL bruger `postgres / postgres`. RabbitMQ UI: http://localhost:15672,
login `aj / aj-local`. Dette er udviklingsoplysninger til den lokale opsætning.

Hver SQL-database er på sit eget Docker-netværk og kan kun nås internt af sin egen app.
Product View er eneste MongoDB-ejer. Synchronizer sender snapshots gennem et internt
API og har hverken MongoDB-driver, databasekonfiguration eller adgang til databasenetværket.
Se [projektionskontrakten](documentation/PROJECTION.md).

## Start ét projekt ad gangen

Hver mappe har sin egen Compose-fil og Dockerfile. Start først fælles infrastruktur:

```powershell
docker compose -f compose.infrastructure.yml up -d
docker compose -f users/docker-compose.yml up -d --build
```

Users-filen starter kun Users og dens database. Tilsvarende gælder Products, Orders og
Payments. RabbitMQ kører separat og genbruges. Product View starter sin MongoDB;
start den før Product Synchronizer. Gateway og frontend kan startes uden de øvrige.

Alternativt: `./scripts/start.ps1 -Service users` eller `./scripts/start.ps1` for alt.
Brug enten root-opsætningen eller mappeopsætningerne i samme kørsel, da de bruger de
samme porte og DNS-navne. De to former har separate databasevolumes.

## Maven og lokal JVM

```powershell
mvn clean verify
mvn install -DskipTests
# Byg et enkelt projekt samt det fælles bibliotek:
mvn -pl users -am package
java -jar users/target/app.jar
```

Alle app-JARs er selvstændige og indeholder deres runtime-afhængigheder.
Docker bygger fra repository-roden, så den fælles Maven-forælder og biblioteket er med.
Der er ingen fælles runtime eller delt SQL-database.

App-konfiguration kan sættes med miljøvariabler: `PORT`, `DB_URL`, `DB_USER`,
`DB_PASSWORD`, `RABBITMQ_URL`, `MONGO_URI`, `MONGO_DB`, `USERS_URL`, `PRODUCTS_URL`,
`ORDERS_URL`, `PAYMENTS_URL`, `PRODUCT_VIEW_URL`, `PRODUCT_SYNCHRONIZER_URL` og `GATEWAY_URL`.
Default-JDBC-porte matcher tabellen. Ved JVM-kørsel af gateway skal de
relevante HTTP-URL'er sættes til localhost-porte, f.eks. `USERS_URL=http://localhost:7002`.
Compose konfigurerer automatisk containeradresserne. Frontend bruger samme origin til API.

Tabeller og ekstra felter oprettes af Hibernate. Eksisterende data fra ældre versioner
kan kræve en datamigrering, især de nye GUID-referencer. Denne opsætning bruger nye
Compose-volumes og sletter ikke gamle data.

## Verifikation

Der er nu særskilte unit-, integrations- og systemtests samt en GitHub Actions-YAML
pr. service. Se [test- og CI-vejledningen](documentation/TESTING.md).
UML-kilder og SVG-diagrammer findes i [documentation/application](documentation/application/README.md).
Den samlede [projektrapport](documentation/Report.md) dækker opgavebeskrivelserne og
skelner mellem implementeret og planlagt arbejde. Se [resterende opgaver](documentation/TODO.md)
for krav, prioritering og acceptkriterier frem mod finalprojektet.

```powershell
mvn clean verify
python scripts/smoke.py
# Udvider testen med kontrollerede nedbrud af projektets egne containere:
python scripts/smoke.py --failures
```

Smoke-testen går gennem frontend → gateway → services og rigtige databaser/RabbitMQ.
Den kontrollerer GUID'er, validering, unikke e-mails, korrekt pris, læseprojektion,
samtidige reservationer, annullering, betalingsresultater, idempotens og soft delete.
Fejltesten stopper/genstarter synchronizer, RabbitMQ, Users-databasen og gatewayen.
Testposter er mærket Integrationstest/Testbil og bevares som salgs-/audithistorik.
Browserkontrol kan køres med `python scripts/browser_check.py`, hvis Python Playwright og Chromium er installeret.

Se [API- og beskedkontrakter](documentation/API.md) for payloads, statusovergange,
fejlformat, outbox/inbox og eventnavne.

---

# **Project is done by**

---

**Ahmad**  
[cph-aa540@stud.ek.dk](mailto:cph-aa540@stud.ek.dk)  
Github @AhmadAlkaseb  

**Jeppe**  
[cph-jk469@stud.ek.dk](mailto:cph-jk469@stud.ek.dk)  
Github @JeppeKoch  

**Asger**  
[asbe0002@stud.ek.dk](mailto:cph-jk469@stud.ek.dk)  
Github @Driconaari

---

**[First Version Report]([docs.google.com/document/d/1bvrWB1yuKqz0LznAQS-6mo2Pu-GV8CDFQPbbM9FmNow/edit?usp=sharing]())**

**[First Assignment PowerPoint]([docs.google.com/presentation/d/1o6h3GBRoAhebTp93YhD_QFX7DsmflgfU6wmw3hH3xmI/edit?usp=sharing](https://docs.google.com/presentation/d/1o6h3GBRoAhebTp93YhD_QFX7DsmflgfU6wmw3hH3xmI/edit?usp=sharing))**

Ordrevalidering, reservation og betalingsstart bruger RabbitMQ uden direkte HTTP-kald
mellem SQL-domænerne. Oprettelse returnerer en ventestatus; se [ASYNC.md](documentation/ASYNC.md).
Backend-/brokertest: `python scripts/check_async.py --failures`.
