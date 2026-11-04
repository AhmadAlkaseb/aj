# Data til eksamen

Ved opstart tilføjer hver SQL-service automatisk sit eget fiktive datasæt:

| API | Demoposter | Indhold |
| --- | --- | --- |
| Users | 20 | Fiktive kunder med navne, e-mail og adresse |
| Products | 20 | Biler med pris, årgang, kilometer, specifikationer og udstyr |
| Product View | 20 | De samme biler projiceret til MongoDB gennem RabbitMQ |
| Orders | 20 | Historiske/demoordrer med gyldige kunde-, bil- og betalingsreferencer |
| Payments | 20 | Manuelle betalingsregistreringer for de 20 demoordrer |

API Gateway og Product Synchronizer har ingen egne forretningsposter. Gatewayen viser
data fra de relevante ejere, og Synchronizer opdaterer læsemodellen. Frontenden viser
de samme data. Hver SQL-hovedpost har en tilhørende detailpost; slettetabeller fyldes
ikke med kunstige sletninger, så de 20 eksempler kan ses i brugerfladen.

## Start

```powershell
docker compose up -d --build --wait --wait-timeout 180
```

Åbn **http://localhost:3000** og vent på, at katalogets asynkrone opdatering er gennemført.
Kataloget bliver ikke fyldt direkte i MongoDB: Products gemmer almindelige produktsnapshots
i sin outbox, og den eksisterende RabbitMQ/Synchronizer-kæde afleverer dem til Product Views
interne API. Product View ejer alene MongoDB og anvender projektionen.

De oprindelige forløb er fordelt sådan:

| Eksempler | Bil | Ordre | Betaling |
| --- | --- | --- | --- |
| 1–6 | SOLD | COMPLETED | COMPLETED med betalingstidspunkt |
| 7–10 | RESERVED | PAYMENT_PENDING | PENDING |
| 11–20 | AVAILABLE | FAILED | FAILED uden betalingstidspunkt |

Der er dermed 10 ledige biler til at demonstrere et nyt køb. De 4 afventende betalinger
kan bruges til at vise succes eller fejl. Beløb og DKK-valuta matcher på tværs af data.
Historiske forløb oprettes som konsistente fixtures; opstart afspiller ikke 20 nye
reservationer eller betalinger og udfører ingen rigtige pengeoverførsler.

## Genstart og eksisterende data

Demoposter har deterministiske UUID'er i et særskilt `aj-demo-v1`-navnerum. Betalinger
bruger den samme identitetsregel som den normale idempotente betalingsoprettelse.
Hver service skriver kun i sin egen SQL-database og behøver ikke vente på de andre
services for at seede. En databaselås forhindrer konkurrerende opstartsseedninger.

Kun manglende demoposter indsættes. Genstart overskriver ikke kundens redigerede navn,
bilens nye pris eller et betalingsresultat, som er ændret under demonstrationen.
Soft-deletede demoposter genoprettes ikke, da deres hovedposter stadig findes.
Andre data, inklusive tidligere testposter, bevares. Der kan derfor være mere end
20 poster i et API, og efter sletning kan der være færre end 20 synlige demoposter.

Hvis MongoDB slettes, mens Products-databasen beholdes, kræves genopbygning af
projektionen; en SQL-genstart publicerer ikke alle tidligere events igen.
Fixture-seedning erstatter ikke backup eller en generel read-model-rebuild.

## Slå automatisk seedning fra

Standard er `DEMO_DATA_ENABLED=true`. Sæt fx følgende i en lokal `.env` ved root-Compose:

```dotenv
DEMO_DATA_ENABLED=false
```

Kør derefter `docker compose up -d`, så applikationscontainere får den nye konfiguration.
Det stopper fremtidig seedning og sletter ingen eksisterende data. Ved direkte
JVM-kørsel sættes miljøvariablen på den enkelte proces. Per-service Compose understøtter
også variablen; brug den relevante Compose-konfigurations `.env` eller procesmiljø.

Alle kunder, referencer, adresser, priser og bilspecifikationer er fiktive eksempler,
ikke faktiske kunder eller markedsoplysninger. E-mailadresserne bruger `.example`.
Telefonnumre og billed-URL'er er bevidst tomme; bilillustrationerne virker lokalt.

## Kontrol

```powershell
mvn clean verify
python scripts/check_demo.py
# Genstarter kun de fire SQL-applikationer og kontrollerer dubletter/bevarede ændringer:
python scripts/check_demo.py --restart
```

Kontrollen kræver de synlige demoidentiteter og køres før bevidst sletning af dem.
Genstartskontrollen ændrer et demo-kundenavn midlertidigt og gendanner det bagefter.
Den kontrollerer 20 demoposter pr. API, MongoDB-kataloget, GUID-referencer, beløb
og at en genstart bevarer ændringer. Kør den separat fra andre nedbrudstests.
