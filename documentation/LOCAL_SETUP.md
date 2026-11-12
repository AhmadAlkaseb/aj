# Samlet lokal opsætning

Start hele systemet med `./scripts/start.ps1`. Alle 24 services, databaser og
overvågningsværktøjer ligger under Compose-projektet **aj**. Containerne har
faste navne uden projektnavn og nummer, eksempelvis `rabbitmq`, `orders`,
`users-db`, `keycloak-db`, `product-view-db`, `grafana` og `loki`.

## Ét lokalt administratorlogin

Brug **admin / Admin123!** overalt i den lokale opsætning:

| Værktøj | Adresse |
| --- | --- |
| AJ Motors | http://localhost:3000 |
| Grafana | http://localhost:13001 |
| Alle PostgreSQL-databaser · Adminer | http://localhost:18080 |
| MongoDB · Mongo Express | http://localhost:18081 |
| Kubernetes · Headlamp | http://localhost:14466 |
| RabbitMQ | http://localhost:15672 |
| Keycloak | http://localhost:8180/admin/ |
| Users PostgreSQL | localhost:5433, database `users` |
| Products PostgreSQL | localhost:5434, database `products` |
| Orders PostgreSQL | localhost:5435, database `orders` |
| Payments PostgreSQL | localhost:5436, database `payments` |

MongoDB har samme administrator i databasen `admin`; brug `authSource=admin`.
MongoDB og Keycloaks database er kun tilgængelige internt i Docker-netværkene;
database-GUI’erne har eksplicit lokal administrationsadgang til de relevante netværk.
Prometheus og Loki har ingen separat browserlogin i denne lokale opsætning.
Kundekonti som `kunde01 / Demo123!` bevares, når kundeadgang skal demonstreres.

Dette er det samme brugernavn og kodeord i værktøjerne. AJ Motors bruger fortsat
Keycloak-login; værktøjerne bruger deres egne loginformularer. Headlamp bruger
en lokal Kubernetes-identitet med læserettigheder, så du ikke skal kopiere tokens.

## Grafana som fælles startside

Åbn **http://localhost:13001**. **AJ Control Center** er nu hjemmesiden og har
adgang til samtlige fem PostgreSQL-databaser, MongoDB, Kubernetes, RabbitMQ,
Keycloak, Swagger, Prometheus, logs og GitHub Actions. GUI’erne åbner i nye faner;
alle Grafana-dashboards har et link tilbage til Control Center. Der installeres
ingen betalte Grafana-plugins eller iframe-omgåelser.

Adminer-forbindelser er forudvalgt pr. database; brug samme lokale administrator.
Mongo Express bruger samme browserlogin. GUI-porte er kun bundet til loopback.
Den eksisterende Open WebUI er markeret som valgfri og administreres separat.

Headlamp viser nodes, workloads, Services, netværk og PVC’er gennem en specifik
læserolle, som ikke kan ændre ressourcer eller læse Secrets. En eksisterende
klynge holdes i **standby**: kontrolplanet kører, mens AJ-deployments skaleres
til nul og KEDA-job pauseres. Compose er dermed den eneste aktive app-kopi.
Hvis der endnu ikke er en klynge, opret den med `python scripts/kubernetes.py standby`.
`./scripts/start.ps1 -Runtime kubernetes` aktiverer Kubernetes-applikationen igen.

Kopiér `.env.example` til `.env`, hvis det fælles login skal ændres, og ret
`AJ_ADMIN_USER` og `AJ_ADMIN_PASSWORD`. Kør startscriptet igen. Det opdaterer
eksisterende brugere i Grafana, RabbitMQ, Keycloak, PostgreSQL og MongoDB.
Applikationernes interne servicekonti ændres ikke og skal ikke indtastes ved normal brug.

## Drift og bevarelse af data

```powershell
./scripts/start.ps1
docker compose ps
docker compose logs -f orders
docker compose stop
docker compose start
python scripts/check_operations.py
python scripts/check_gui_hub.py
```

PDF-servicen `confirmation-job` kører som en vedvarende worker i Compose,
så den også er startet og håndterer nye bekræftelser automatisk. Kubernetes
bruger fortsat engangsjob styret af KEDA.

Ved overgangen kopierer startscriptet tidligere `aj-monitoring`-volumes til
`aj`-volumes, når målet er tomt. De oprindelige volumes bevares. Databaserne
genbruger de eksisterende `aj`-volumes. En eksisterende Kubernetes-klynge sættes i standby
med sine data bevaret, og dens port-forward-processer lukkes for at frigive porte.
Vælg den separat med `./scripts/start.ps1 -Runtime kubernetes`.

`docker compose down` bevarer volumes. `docker compose down -v` sletter de
Compose-ejede databasevolumes; overvågningens eksterne volumes bevares også dér.
Overvågningen er en del af samme projekt og stoppes sammen med resten.
