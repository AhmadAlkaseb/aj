# AJ Motors — resterende arbejde

**Grundlag:** [Report.md](Report.md), de to mandatory assignments og [Final project description.pdf](<mandatory assignments/Final project description.pdf>).  
**Gennemgang:** 4. november 2026.  
**Afleveringsdato:** 11. december 2026.

Rapporten beskriver den implementerede prototype og markerer planlagte dele. Denne liste er arbejdsgrundlaget for den efterfølgende implementering. Kravene fra Itslearning-aflevering og præsentation er udeladt. Ingen af de nedenstående nye applikationsfunktioner er implementeret som en del af rapportskrivningen.

**Prioritet:** P0 = afklaring, som påvirker løsningen; P1 = krav eller væsentlig mangel før finalprojektet kan beskrives som færdigt; P2 = produktforbedring eller yderligere hærdning. En opgave er først færdig, når acceptkriterier, tests og dokumentation er opfyldt. Gruppemedlemmer aftaler selv en ansvarlig og reviewer for hver opgave.

## Anbefalet rækkefølge

1. Bekræft arbejdsfordelingen: DOC-01.
2. Fastlæg standardkontrakter og migrationer: CONTRACT-01, DATA-01. Beskedmetadata og asynkront købsflow er implementeret; se [ASYNC.md](ASYNC.md).
3. Implementér identitet, GraphQL og AI med relevante tests: SEC-01, API-01, AI-01.
4. Afslut idempotens, beskedrækkefølge og saga-genopretning: REL-01–REL-03.
5. Lav lokal Kubernetes, observability og en isoleret serverless-opgave: OPS-01–OPS-03.
6. Afslut testtyper, statisk analyse, releaseflow og dokumentation: TEST-01–TEST-04, CI-01–CI-02, DOC-02.

Opgaver kan overlappe, når deres afhængigheder er afklaret. Kør ikke to nedbrudstests mod samme lokale miljø samtidigt.

## P1 — API, sikkerhed og AI

### SEC-01 — tokenbaseret autentifikation og autorisation

- [ ] Definér brugeridentitet, kunde-/administratorroller og ejerskab af kunde- og ordreressourcer.
- [ ] Implementér tokenvalidering og beskyttelse i de relevante backends; skjulte frontendknapper er ikke adgangskontrol.
- [ ] Adskil kundeprofilen fra loginidentiteten. Kunde-CRUD opretter aktuelt ikke en login-konto.
- [ ] Beskyt interne endpoints og undgå utilsigtet omgåelse via direkte serviceporte i det produktionslignende miljø.
- [ ] Håndtér hemmeligheder og TLS i det planlagte deployment. Brug den etablerede Keycloak/OIDC-integration som identitetskilde.

**Accept:** Kunde og administrator kan kun udføre tilladte handlinger, og TEST-03 bekræfter dette. **Grundlag:** den implementerede [Keycloak/OIDC-integration](AUTH.md). **Rapport:** 1.2, 4.5.

### API-01 — GraphQL i et reelt frontendflow

- [ ] Tilføj et faktisk GraphQL-schema og endpoint i AJ Motors, fx typed katalogforespørgsler i Product View.
- [ ] Lad frontend bruge GraphQL til bilsøgning/detaljer og REST til den efterfølgende ordre/reservation.
- [ ] Håndtér inputgrænser, fejl og manglende backend uden at lade klienten bestemme pris eller lagerstatus.
- [ ] Tilføj schema-/resolvertests og en test, der viser, at frontend reelt bruger begge API-stilarter.

**Accept:** Et dokumenteret, reproducerbart bilvalg/reservationsflow bruger både GraphQL og REST. GraphQL-øvelsen under `system-integration/` tæller ikke som denne integration. **Afhængighed:** CONTRACT-01; dataejerskabet er fastlagt i [ADR-002](decisions/002-projection-ownership.md). **Rapport:** 2.3.

### AI-01 — rigtig AI-integration i en backend

- [ ] Byg fx en Products-funktion til et beskrivelsesudkast baseret på validerede biloplysninger.
- [ ] Integrér en AI-API i backenden, fx en containeriseret lokal Ollama-model. Fastlæg request/response-kontrakt, modelvalg og ressourcer.
- [ ] Validér output og tidsgrænser. Send ikke unødvendige kunde-/betalingsoplysninger til modellen.
- [ ] Behold menneskelig godkendelse af udkast; AI må ikke ændre pris, reservation eller betalingsresultat.
- [ ] Test validt output, timeout, ugyldigt output og nedbrud. Dokumentér mindst én gennemført kørsel mod en faktisk model.

**Accept:** En backend bruger en rigtig AI-API i et meningsfuldt applikationsflow; en stub eller en separat chatbrugerflade alene er ikke nok. **Rapport:** 3.2.1.

## P1 — data og distribueret korrekthed

### DATA-01 — versionsstyrede migrationer

- [ ] Indfør Flyway eller et tilsvarende migrationsværktøj for hver af de fire PostgreSQL-ejere.
- [ ] Lav en sikker baseline og et initialt migrationsforløb; bevar eksisterende data og slet ikke udviklingsvolumes som en skjult opgradering.
- [ ] Erstat Hibernate `update` som versionsstrategi med migration efterfulgt af skemavalidering.
- [ ] Test både tom database og opgradering af et repræsentativt tidligere skema med data.
- [ ] Definér versions-/genopbygningsstrategi for MongoDB-projektionen.

**Accept:** Databaseskema og data kan bringes til samme kendte version reproducerbart uden manuelle SQL-rettelser. **Rapport:** 5.3.

### REL-01 — idempotente klientkommandoer

- [ ] Tilføj en politik for retry-nøgler til relevante oprettelser, især ordreoprettelse.
- [ ] Gem nøgle, request-fingerprint og det oprindelige resultat atomisk med ændringen.
- [ ] Definér svar på samme nøgle med forskellig payload og samtidige gentagelser.
- [ ] Bevar eksisterende betalings-/eventdeduplikering og test den sammen med den nye politik.

**Accept:** Gentagne identiske klientrequests skaber ikke ekstra ordrer eller andre uønskede sideeffekter. Eksisterende unik e-mail og event-ID-deduplikering er ikke en generel POST-garanti. **Rapport:** 2.4.3.

### REL-02 — beskedrækkefølge og sikre parallelle workers

- [ ] Definér, hvad der sker, hvis annullering eller betalingsresultat ankommer før reservationen, og hvis gamle beskeder ankommer efter terminal status.
- [ ] Indfør nødvendigt tilstandshukommelse/versioner og regler, så relevant rækkefølge ikke ændrer det endelige forretningsresultat.
- [ ] Test permutationer, dubletter og konflikter; skeln mellem samme event-ID og samme forretningshandling med nyt event-ID.
- [ ] Gør projektionsopdateringen atomisk ved flere workers, eller dokumentér og håndhæv en enkelt ejer/worker.
- [ ] Verificér outbox-/inbox-koordinering ved flere replikaer, før autoskalering aktiveres.

**Accept:** Udvalgte ækvivalente beskedrækkefølger konvergerer til samme korrekte resultat med dokumenterede forudsætninger. Nuværende sekventielle MongoDB-versionstjek alene er ikke tilstrækkeligt. **Rapport:** 2.4.4, 3.2.4.

### REL-03 — færdiggør saga-genopretning

- [ ] Fastlæg udløb for PENDING/RESERVED og behandling af forladte PAYMENT_PENDING-forløb.
- [ ] Udbyg den implementerede outbox-genoptagelse med reconciliation for permanent fastlåste eller forladte betalingsforløb; midlertidigt consumer-nedbrud er dækket i `check_async.py`.
- [ ] Gør kompensation idempotent og samordn den med REL-02, så et timeout ikke kan frigive en allerede solgt bil.
- [ ] Test nedbrud ved hvert væsentligt transaktions-/beskedskel, også når en klient ikke gentager sin request.

**Accept:** Et forløb ender i en gyldig terminal tilstand eller en tydeligt håndteret manuel afklaringsstatus; det forbliver ikke uopdaget fastlåst. **Rapport:** 2.2 Orders/Payments, 2.4.5.

## P1 — drift og deployment

### OPS-01 — lokal Kubernetes-simulering

- [ ] Vælg en lokal clusterløsning og tilføj manifests/overlays for applikationer, interne Services og ingress.
- [ ] Konfigurér secrets, konfiguration, ressourcer, readiness/liveness og persistent storage for stateful komponenter.
- [ ] Indarbejd databaseinitiering/migrationer og bevar det fastlagte dataejerskab fra ADR-002.
- [ ] Konfigurér og demonstrér HPA for en egnet HTTP-service; tilføj nødvendige metrics.
- [ ] Kør fra et rent lokalt miljø og dokumentér startup, verifikation, nedbrud, rollout og oprydning.

**Accept:** Clusteropstart og et fungerende distribueret flow kan gentages af en anden udvikler. Compose og sundhedstjek alene tæller ikke som Kubernetes. **Afhængighed:** DATA-01; REL-02 før workers skaleres. **Rapport:** 3.2.

### OPS-02 — distribueret logging og monitoring

- [ ] Indfør strukturerede logs og korrelations-ID på tværs af HTTP og RabbitMQ.
- [ ] Opsæt central logindsamling, fx Loki/Grafana, og metrics, fx Prometheus.
- [ ] Lav dashboard for latency, fejl, kødybde, ældste outbox-post, projektionsforsinkelse og dead letters.
- [ ] Vis en konkret alarm under et kontrolleret nedbrud og et spor gennem flere services.
- [ ] Redigér følsomme oplysninger og undgå at logge tokens eller adgangskoder.

**Accept:** Et problem kan spores på tværs af services, og en voksende kø eller fejlende afhængighed bliver synlig og alarmeret. Containerlogs og frontendstatus alene opfylder ikke dette. **Rapport:** 3.2.3.

### OPS-03 — isoleret serverless-opgave

- [ ] Implementér fx en afgrænset ordrebekræftelsesopgave som en containeriseret jobfunktion.
- [ ] Giv opgaven sin egen kø og et tydeligt resultat, så den ikke konkurrerer med lager-/ordreconsumers om deres beskeder.
- [ ] Konfigurér KEDA ScaledJob eller en tilsvarende serverless-mekanisme i den lokale Kubernetes-løsning.
- [ ] Implementér idempotent behandling, retry og fejlspor; processen skal terminere efter den afgrænsede opgave.
- [ ] Dokumentér en trigger, gennemførsel og genafvikling uden duplikatresultat.

**Accept:** En reel isoleret opgave køres og skaleres på begivenheder. En permanent kørende almindelig service eller synchronizer omdøbt til serverless er ikke nok. **Afhængighed:** OPS-01, CONTRACT-01. **Rapport:** 3.2.1.

## P1 — test og CI/CD

### TEST-01 — domæne-unit-tests i hver backend

- [ ] Users: validering og regler for kundedata.
- [ ] Products: pris-/tilgængelighedsregler og eksklusiv reservation.
- [ ] Orders: tilladte transitions, annullering og betalingsclaim.
- [ ] Payments: beløbskilde, idempotens og terminale resultater.
- [ ] Product View: querygrænser, filtrering og skjulte tombstones.
- [ ] Product Synchronizer eller den afklarede projiceringsejer: versionspræcedens, dubletter og sletning.
- [ ] Kør dem som relevante unit-tests i service-workflows. Flyt logik til testbare grænser, hvor det er nødvendigt.

**Accept:** Hver backend har tests af egen kerneadfærd. Fælles `ApiTest` og tests af getters er ikke en erstatning. **Rapport:** 4.2.

### TEST-02 — fokuserede integrationstests

- [ ] REST og GraphQL: reelle HTTP-grænser med validering og fejlkontrakter.
- [ ] RabbitMQ: udbyg `check_async.py` (rigtig broker, metadata, dead letter og genopretning) med isolerede assertions for dubletter og ack/retry.
- [ ] Databaser: relevante PostgreSQL-/MongoDB-mappings og constraints i rigtige testdatabaser, inklusive migrationer.
- [ ] Isolér testdata og afhængigheder; få CI til at starte og stoppe dem reproducerbart.

**Accept:** De tre integrationsområder kan identificeres og køres automatisk. Den nuværende H2-test og samlede købsgang er nyttige, men er ikke hele dækningen. **Rapport:** 4.3.

### TEST-03 — sikkerhedstests

- [ ] Test manglende, ugyldige, udløbne og korrekt signerede tokens.
- [ ] Test kundens adgang til egne versus andres ressourcer.
- [ ] Test, at kundetokens ikke giver administratoradgang, og at backend håndhæver reglerne.
- [ ] Udbyg den eksisterende Keycloak-login-/tokenkontrol med tests af rolle- og ejerskabshåndhævelse.
- [ ] Lad fejl blokere CI som andre testfejl.

**Accept:** Autentifikation og autorisation er testet; inputvalidering tælles ikke som sikkerhedsdækning af disse krav. **Afhængighed:** SEC-01. **Rapport:** 4.5.

### TEST-04 — backendfokuseret system-cooperation-test

- [ ] Tilføj en test, der selv starter mindst to relevante backends og deres nødvendige containerafhængigheder.
- [ ] Fokusér fx på Orders → RabbitMQ → Products → RabbitMQ → Orders og assert både beskedsamarbejde og sluttilstand.
- [ ] Undlad frontend og browser som testens drivende grænse; test serviceinteraktionen direkte.
- [ ] Kør testen automatisk i CI, gem diagnostics og ryd testmiljøet op ved succes og fejl.
- [ ] Bevar de nuværende API-købsgangs-/recoverytests som supplerende systemtest.

**Accept:** En særskilt test opfylder finalbeskrivelsens formulering om service-cooperation og er ikke kun en end-to-end-brugerrejse. **Rapport:** 4.4.

### CI-01 — statisk analyse i pipeline

- [ ] Vælg Java-kvalitets-/sikkerhedsanalyse, fx SpotBugs og en egnet sårbarhedsanalyse eller CodeQL afhængigt af repository og adgang.
- [ ] Konfigurér et automatisk CI-trin, rapporter og en aftalt failure-grænse.
- [ ] Gennemgå fund og begrund konkrete undtagelser; skjul ikke alle fejl med brede suppressioner.
- [ ] Overvej også at køre Actionlint og kontraktvalidering automatisk.

**Accept:** En relevant statisk kodeanalyse bliver faktisk kørt i CI og kan gøre pipeline rød. Maven-kompilering eller lokal YAML-validering alene er ikke dette krav. **Rapport:** 4.6.

### CI-02 — remote bevis, versionsbaseline og releaseflow

- [ ] Review og commit/push ændringerne med korrekt forfatterskab; bekræft public monorepo-adgang, som opgaven kræver.
- [ ] Gem vellykkede GitHub-run-ID'er og test-/analyserapporter for en bestemt commit.
- [ ] Konfigurér relevante required checks og PR-reviewpolitik.
- [ ] Lad en release vente på alle nødvendige testtyper og analysen for samme revision.
- [ ] Byg og publicér immutable image-tags og tilføj kontrolleret Kubernetes-release med migrationsgate og efterfølgende smoke check.
- [ ] Dokumentér app-rollback og kompatibilitet med den migrerede database. Clouddeployment er ikke et krav; der er ikke behov for at købe cloudkapacitet for at afslutte den lokale demonstration.

**Accept:** En kendt commit kan reproduceres og verificeres; remote testbevis holdes adskilt fra lokale logs. **Afhængighed:** relevante tests, DATA-01, OPS-01. **Rapport:** 3.2.2, 5.3.

## P1 — kontrakter, rapport og samarbejde

### CONTRACT-01 — maskinlæsbare kontrakter og service-READMEs

- [ ] Opret en README for hver af de otte applikationsmapper med ansvar, porte, dataejerskab, konfiguration, tests og startvejledning.
- [ ] Tilføj OpenAPI/Swagger for alle REST-grænser, inklusive de relevante interne og operationelle endpoints med tydelig eksponering.
- [ ] Tilføj GraphQL-schema og endpointvejledning efter API-01.
- [ ] Tilføj AsyncAPI eller tilsvarende formaliseret standard med broker/køer, routings, alle beskedskemaer, producenter, consumers og flows.
- [ ] Validér kontrakter og forbind dem til UML og `API.md`; dokumentér v1-kompatibilitet.

**Accept:** Prosa/UML suppleres med brugbare standardkontrakter. Skemaer stemmer med implementeret JSON, inklusive fejl, nulls, tidsstempler og metadata. **Rapport:** 2.3, 5.4.

### DOC-01 — faktisk arbejdsfordeling og grupperefleksion

- [x] Bekræft de fire fulde navne og afleveringsdatoen; de er indsat i rapporten.
- [ ] Aftal navngivne ejere og reviewers for arbejdsområderne i rapportens afsnit 5.2.
- [ ] Tilføj reelle sprint-/issue-/revieweksempler og alle fire deltageres refleksion.
- [ ] Kontrollér, at Git-historikken synliggør de faktiske bidrag fra hver studerende uden at opfinde forfatterskab.
- [ ] Bekræft funktionelle krav, ikke-funktionelle mål og Definition of Done.

**Accept:** Rapportens samarbejdsafsnit afspejler det, gruppen faktisk har gjort, og foreslåede roller er erstattet af bekræftede oplysninger. **Rapport:** 5 og 7.

### DOC-02 — afslut rapport og installationsvejledning

- [ ] Opdatér status og evidens i `Report.md`, efterhånden som opgaverne afsluttes.
- [ ] Tilføj de faktiske Kubernetes-/observability-/AI-/GraphQL-diagrammer og kontraktlinks; hold planlagte miljøer markeret som planlagte, indtil de er kørt.
- [ ] Beskriv den valgte cloudplan, prisforudsætninger, availability, data, sikkerhed og releasevalg uden at opfinde en udført clouddeployment.
- [ ] Afprøv onboarding på en ren maskine/cluster med databaser, migrationer og alle operationelle afhængigheder.
- [ ] Kontrollér kildehenvisninger, figurer, bilag og dækningsmatrix mod alle tre PDF'er.
- [ ] Eksportér til det ønskede slutformat med pagineret indholdsfortegnelse og kontrollér den relevante sidegrænse; det refererede kursuskatalog angiver højst 30 sider for fire studerende.

**Accept:** Rapporten passer til den afsluttede kode, har reproducerbar evidens og ingen uafklarede påstande om implementerede features. **Afhængighed:** alle relevante P1-krav. **Rapport:** hele dokumentet.

## P2 — supplerende hærdning og produktforbedringer

### DATA-02 — retention, sletning og historik

- [ ] Aftal opbevaring af sletteposter, snapshots, inbox/outbox og kundedata.
- [ ] Afklar, om egentlig ændringshistorik er nødvendig; de nuværende details-tabeller overskrives.
- [ ] Beskriv genopbygning af læsemodellen og eventuel arkivering af beskeder.

**Accept:** Rapporten og datamodellen skelner mellem soft delete, snapshots, audit og fuld historik.

### PERF-01 — målbar performance og store datamængder

- [ ] Tilføj pagination og relevante querygrænser, før kataloget testes med mange poster.
- [ ] Definér datasæt, samtidige brugere, latency-percentiler og et reproducerbart testmiljø.
- [ ] Mål tosekundersmålet og flaskehalse i API, databaser og køer; dokumentér observationer frem for antagelser.
- [ ] Aftal, hvordan availability-målet måles over tid.

**Accept:** Performance- og availability-afsnit bygger på målinger eller er fortsat tydeligt markeret som mål.

### UI-01 — fuldfør de valgte katalogfunktioner

- [ ] Tilføj pris-/årgang-/kilometerfiltre, hvis de bevares i det aftalte produktscope.
- [ ] Afklar billedhåndtering og visning af faktiske bilbilleder.
- [ ] Tilpas kunde-/administratoroplevelsen til de implementerede adgangsregler og personlige ordrer.
- [ ] Udfør en relevant tilgængeligheds- og mobilgennemgang.

**Accept:** Produktkrav og frontend stemmer overens; alle nye brugerhandlinger har backendvalidering.

### OPS-04 — backup, restore og robust drift

- [ ] Definér backup og gennemfør en faktisk restore-test for relevante data.
- [ ] Indfør operatørprocedure for dead letters, fastlåste flows og genafspilning.
- [ ] Afklar broker-/database-HA og den fysiske placering af data i cloudplanen.
- [ ] Fjern afhængighed af standardudviklingscredentials uden at ødelægge den dokumenterede lokale onboarding.

**Accept:** Persistente volumes forveksles ikke med backup eller høj tilgængelighed, og kritiske manuelle genopretninger er dokumenteret.
