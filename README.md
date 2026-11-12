# AJ Motors

Et lokalt bilsalgssystem med selvstændige Java 17 / Maven / Javalin-microservices,
fire PostgreSQL-databaser, en MongoDB-læsemodel og RabbitMQ. Lokal Keycloak/OpenID Connect-login; ingen Stripe.

## Start hele systemet

Krav: Docker Desktop med Linux-containere og Docker Compose. Java/Maven er kun nødvendige
for builds uden Docker. Kør fra repository-roden:

```powershell
./scripts/start.ps1
```

Alle lokale containere samles i Docker Desktop under **aj** med faste navne som
`rabbitmq`, `users`, `users-db`, `product-view-db` og `grafana`.
Fælles lokalt administratorlogin: **`admin / Admin123!`** til AJ Motors, Grafana,
RabbitMQ, Keycloak og databaserne. Det kan ændres centralt via `.env`; se
[lokal drift og login](documentation/LOCAL_SETUP.md).

Start eller vælg Kubernetes eksplicit med `./scripts/start.ps1 -Runtime kubernetes`;
programmet åbnes da på **https://localhost:8443**, og Grafana/RabbitMQ bruger
klyngens genererede adgangskoder. Se [Kubernetes-drift](documentation/KUBERNETES.md).

Åbn **http://localhost:3000**. Første build henter Maven-afhængigheder og Docker-images.
Startscriptet starter også Loki, Alloy, Grafana og Prometheus. Se logs og dashboard på
**http://localhost:13001** (`admin` / `Admin123!`, medmindre `AJ_ADMIN_PASSWORD` er sat).
Se [vejledning til logs og overvågning](documentation/OBSERVABILITY.md).
Grafanas **AJ Control Center** samler alle GUI’er: fem PostgreSQL-databaser,
MongoDB, Kubernetes, RabbitMQ, Keycloak, Swagger og CI/CD. Databaseværktøjerne
bruger samme lokale administratorlogin. Kubernetes-GUI’en har læseadgang.
Ved opstart seedes 20 fiktive kunder, 20 biler, 20 ordrer og 20 betalinger automatisk.
Der er 10 ledige biler til nye reservationer. Genstart bevarer ændringer uden dubletter;
se [eksamensdata](documentation/DEMO_DATA.md) for forløb, kontrol og deaktivering.
Vent på grøn systemstatus. Tryk **Log ind** og brug `kunde01 / Demo123!` eller
`admin / Admin123!`. Se [loginvejledningen](documentation/AUTH.md).
Backends beskytter roller og ejerskab. Kunder ser egne oplysninger og ordrer; administratoren vedligeholder biler og registrerer betalinger. Opret en kunde og en bil, reservér bilen, og gå til Ordrer
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
| confirmation-job | — | Afgrænset PDF-job; egen RabbitMQ-kø, skriver gennem Orders API |
| service-common | — | Fælles buildbibliotek; ingen kørende service |

De otte Java-applikationer bruger intern app-port 7000. Lokale porte er bundet til 127.0.0.1.
Fælles administrationslogin er `admin / Admin123!`. RabbitMQ UI: http://localhost:15672.
Applikationernes interne servicekonti bevares. Dette er udviklingsoplysninger til den lokale opsætning.

Hver SQL-database er på sit eget Docker-netværk og kan kun nås internt af sin egen app.
Product View er eneste MongoDB-ejer. Synchronizer sender snapshots gennem et internt
API og har hverken MongoDB-driver, databasekonfiguration eller adgang til databasenetværket.
Se [projektionskontrakten](documentation/PROJECTION.md).

## Start ét projekt ad gangen

Hver mappe har sin egen Compose-fil og Dockerfile. Start først fælles infrastruktur:

```powershell
docker compose -f compose.infrastructure.yml up -d --wait --wait-timeout 240
python scripts/configure_identity.py
docker compose -f users/docker-compose.yml up -d --build
```

Users-filen starter kun Users og dens database. Tilsvarende gælder Products, Orders og
Payments. RabbitMQ kører separat og genbruges. Product View starter sin MongoDB;
start den før Product Synchronizer. Gateway og frontend kan startes uden de øvrige.

Alternativt: `./scripts/start.ps1 -Service users` eller `./scripts/start.ps1` for alt.
Brug enten root-opsætningen eller mappeopsætningerne i samme kørsel, da de bruger de
samme porte og DNS-navne. De to former har separate databasevolumes.

## Opdatér containere efter kodeændringer

Kodeændringer kommer først ind i en kørende container, når dens image er bygget igen,
og containeren er erstattet. En almindelig `docker compose restart` genstarter den
eksisterende version og bygger ikke den ændrede kode.

### Hele systemet med root-opsætningen

Gem ændringerne, eller hent den nyeste kode fra Git, og kør fra repository-roden:

```powershell
.\scripts\start.ps1
```

Scriptet bygger images, erstatter containere med ændrede images og venter på sundhedstjek.
Det konfigurerer også lokal identitet idempotent. Databaser og RabbitMQ beholder deres
navngivne volumes. Flyway anvender nye SQL-migrationer ved applikationsstart, før Hibernate
validerer skemaet. Tilføj en ny migrationsfil ved skemaændringer; redigér ikke allerede
anvendte migrationer. Se [MIGRATIONS.md](documentation/MIGRATIONS.md).

### Kun én service i den kørende root-opsætning

Hvis resten af systemet allerede kører, kan eksempelvis Users opdateres alene:

```powershell
docker compose up -d --build --no-deps --wait --wait-timeout 240 users
docker compose ps
docker compose logs --tail 100 users
```

Erstat `users` med det relevante servicenavn. Ved ændringer i `service-common` opdateres
hele systemet, fordi flere applikationer bruger biblioteket. Frontendens HTML/CSS/JavaScript
er også bygget ind i dens image og kræver et nyt frontend-build.

### Hvis I bruger de separate mappeopsætninger

Opdatér med samme Compose-fil som ved opstart; kør eksempelvis fra repository-roden:

```powershell
docker compose -f users/docker-compose.yml up -d --build --wait --wait-timeout 240
```

Brug fortsat mappeopsætningens volumes. Root- og mappeopsætningerne har forskellige volumes
og må ikke blandes. Normal opdatering kræver ikke `down`; `down -v` sletter volumes og
skal ikke bruges til at opdatere kode. Ved fejl kan en service være kortvarigt utilgængelig;
kontrollér sundhedstjek og logs før næste ændring.

### Deployment gennem GitHub Actions

1. Commit/push ændringerne, og få dem ind på `main`.
2. Åbn **Actions → Verified release and deployment → Run workflow** på `main`.
3. Vælg `publish=true` og `target=staging` eller `target=production`.

Pipelinen tester koden, bygger/scanner images og publicerer dem til GHCR. Deployment
kører migrationer og opdaterer Kubernetes-applikationerne til de præcise image-digests.
Produktion bruger samme digests som den først verificerede staging-version. Et almindeligt
push starter CI-checks; selve release/deployment startes manuelt og opdaterer ikke automatisk
jeres lokale Compose-containere.

Runner, Kubernetes-adgang og environment-secrets skal være konfigureret som beskrevet i
[CI-CD.md](documentation/CI-CD.md). Under **Actions** kan I følge de enkelte trin og hente
test-/deploymentrapporter.

## Generér rapporten som PDF

Rapportens kilde er [documentation/Report.md](documentation/Report.md). Layoutet er
inspireret af den tidligere Database-rapport: sidehoveder, blå links, figurtekster,
indholdsfortegnelse med sidetal og kodeeksempler med syntaksfarver i indrammede bokse.
Forsiden har intet sidetal; siden efter forsiden starter på **1**.

Første gang på Windows, kør fra repository-roden:

```powershell
.\scripts\setup_report.ps1
```

Det kræver Python 3.12 og internet ved første opsætning. Scriptet bruger eksisterende
Pandoc/Tectonic-installationer eller henter fastlåste portable Windows-versioner i den
ignorerede `test-results/tools/report/`-mappe. Python-afhængigheder installeres lokalt
under `test-results/tools/python-deps/`, og Chromium installeres til diagramkonvertering.
Tectonic er LaTeX-motoren og henter de nødvendige TeX-pakker ved første PDF-build;
en fuld MiKTeX-/TeX Live-installation er ikke nødvendig.

Når I har ændret rapporten, genereres PDF'en med:

```powershell
python scripts/export_report.py
```

Resultatet er [documentation/Report.pdf](documentation/Report.pdf). Eksporten laver
vektorfigurer, opdaterer indholdsfortegnelse og figurliste, kontrollerer sidetal og
afviser en rapport over 30 fysiske sider, inklusive den unummererede forside.
Mellemfiler og LaTeX-log gemmes i `test-results/report-latex/`; eksportmetadata findes i
[documentation/evidence/report-export.json](documentation/evidence/report-export.json).
Luk PDF'en i en fremviser, der låser filen, hvis Windows afviser at overskrive den.

Ændr tekst og kodeblokke i `Report.md`; skriv eksempelvis `java`, `sql`, `json` eller
`graphql` efter kodeblokkens tre backticks for syntaksfarver. Papirstørrelse, margener og
grundskrift står i rapportens YAML-forside. Sidehoveder, sidefod og kodebokse tilpasses i
[report-style.tex](documentation/report-style.tex). Markdown-visningen har ingen faste
papirsider; de endelige sidetal dannes automatisk ved PDF-eksport.

De kompakte A4-figurers kilder findes i
[report-figures/README.md](documentation/report-figures/README.md). Efter ændringer i dem:

```powershell
python scripts/render_report_figures.py
python scripts/export_report.py
```

De fulde UML-modeller ligger fortsat i `documentation/application/`. De kan valgfrit
renderes igen med `python scripts/render_uml.py`, hvilket kræver Java 17 og Graphviz.
På Linux/macOS installeres Pandoc og Tectonic med en passende pakkehåndtering samt
Python-pakkerne fra `scripts/requirements-report.txt`; selve eksportkommandoen er den samme.

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

Flyway opretter og opdaterer SQL-skemaerne med versionsstyrede migrationer; Hibernate
validerer derefter skemaet. Eksisterende volumes bevares. Se
[migrationsvejledningen](documentation/MIGRATIONS.md) for baseline af ældre databaser.

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

## Implementering og dokumentation

Kataloget bruger GraphQL med filtre og pagination. Administratorer kan få et lokalt AI-udkast til bilbeskrivelser og godkende det før lagring. Færdige ordrer kan få en PDF-bekræftelse fra et isoleret job. SQL-ejerne bruger Flyway, audit og idempotente kommandoer.

- [Rapport](documentation/Report.md), [PDF](documentation/Report.pdf) og [resterende arbejde](documentation/TODO.md).
- [Lokalt Kubernetes med TLS, HPA og KEDA](documentation/KUBERNETES.md).
- [Monitoring og logging](documentation/OBSERVABILITY.md), [backup og drift](documentation/OPERATIONS.md).
- [AI](documentation/AI.md), [migrationer](documentation/MIGRATIONS.md), [reliability](documentation/RELIABILITY.md).
- [Kontrakter](documentation/API.md), [testvejledning](documentation/TESTING.md) og [lokal evidens](documentation/EVIDENCE.md).

Swagger UI kører på http://localhost:3000/swagger/index.html. Monitoring startes separat; Grafana bruger http://localhost:13001. Kubernetes, AI og monitoring kan demonstreres separat på en maskine med 16 GB RAM.

CI/CD er opdelt i servicechecks, quality/system/security-gates, image-build/scanning, staging, produktion og rollback. Se [CI/CD-vejledningen](documentation/CI-CD.md) for runner, cluster, environments og secrets. Start en release fra Actions på main; GitHub-deployment kræver den beskrevne målkonfiguration.
