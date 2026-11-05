# Arbejdsplan — september til november 2026

Dette er et foreslået forløb, udarbejdet efter gruppens ønskede fordeling. Datoer, møder og sprintmål er en plan/skabelon og dokumenterer ikke tidligere fremmøde, reviews eller individuelle bidrag. Perioden slutter **30. november 2026**.

## Let Scrum-inspireret arbejdsform

- Korte sprints på cirka to uger; sidste sprint er en afslutningsperiode.
- Daily standup på cirka 10 minutter kl. 09.00 på aftalte arbejdsdage: fremdrift, dagens arbejde og blokeringer.
- Fælles board med To Do, In Progress, Review og Done. Opgaver får ejer, reviewer og acceptkriterier.
- Sprintplanlægning ved periodens start; kort demonstration og retrospektiv på sidste arbejdsdag.
- Roller er praktiske ansvarsområder; gruppen indfører ikke faste Scrum Master-/Product Owner-roller eller alle Scrum-ceremonier.
- Done kræver relevant test, opdaterede kontrakter/dokumentation og review af den samlede funktion.

## Foreslået sprintforløb

| Periode | Fælles mål | Ansvarsfordeling og review |
| --- | --- | --- |
| 1.–11. september | Krav, afgrænsning, arkitektur og første vertikale flow | Ahmad: kunde-/adgangskrav. Jeppe: katalogkrav. Asger: ordre-/betalingsflow. Laith: kørselsmiljø. Fælles review af krav og dataejerskab. |
| 14.–25. september | Selvstændige Maven-services, databaser, REST og demodata | Ahmad: Users. Jeppe: Products. Asger: Orders/Payments. Laith: Compose og gateway/frontend-opkobling. Review på tværs af databasegrænser. |
| 28. september–9. oktober | RabbitMQ, reservationer, projektion og fejlscenarier | Asger: saga og kompensation. Jeppe: produktreservation og katalog. Ahmad: kundevalidering. Laith: brokerkonfiguration og systemkontrol. Fælles købsgangsreview. |
| 12.–23. oktober | Adgangskontrol, GraphQL, AI og kontrakter | Ahmad: identitet/ejerskab. Jeppe: GraphQL og AI-udkast. Asger: ordre-/betalingskontrakter. Laith: kontraktkontrol i workflows. Review med gyldige og afviste requests. |
| 26. oktober–6. november | Migrationer, idempotens, recovery og tests | Ahmad: identitets-/sikkerhedstests. Jeppe: katalog-/AI-tests. Asger: deadlines, retries og PDF-job. Laith: CI, migration/onboarding og backup/restore. Fælles review af fejlgenopretning. |
| 9.–20. november | Kubernetes, monitoring og målinger | Laith: TLS, netværk, HPA/KEDA og dashboards. Asger: job-/saga-verifikation. Jeppe: katalogbenchmark og frontend. Ahmad: sikkerhedsgrænser og logkontrol. Review af målinger og deres begrænsninger. |
| 23.–30. november | Samlet kvalitet, rapport, UML og reproducerbar demonstration | Alle gennemgår eget område og reviewer en andens. Ahmad: adgang/dokumentation. Jeppe: katalog/API-figurer. Asger: flows/testevidens. Laith: drift/installationsvejledning. Fælles rapport- og systemreview. |

Sprintmål er ikke registrerede færdiggørelsesdatoer. Faktiske lokale kørsler har deres egne datoer i [EVIDENCE.md](EVIDENCE.md).

## Ejere og faste reviewpartnere

| Ejer | Hovedområde | Reviewer |
| --- | --- | --- |
| Ahmad Abdel Razak Hussein Alkaseb | Users, identitet og adgangskontrol | Jeppe Rønning Koch |
| Jeppe Rønning Koch | Products, katalog, GraphQL og AI | Asger Valdemar Bergøe |
| Asger Valdemar Bergøe | Orders, Payments, pålidelighed og PDF-job | Laith Abdel Razak Hussein Alkaseb |
| Laith Abdel Razak Hussein Alkaseb | Kubernetes, monitoring, levering og backup/restore | Ahmad Abdel Razak Hussein Alkaseb |

Frontend, tests, kontrakter og rapport følger alle fire spor. Reviewrotationen fordeler ansvar uden at tilskrive nogen eksisterende commits.

## Udkast til refleksionspunkter

Disse faglige udkast bygger på løsningens egenskaber. Deltagerne skal selv bekræfte personlige erfaringer og tilføje et faktisk eksempel før aflevering.

| Deltager | Fagligt refleksionsudkast | Eksempel til egen bekræftelse |
| --- | --- | --- |
| Ahmad | Login og adgangskontrol er forskellige grænser. Hver backend skal selv verificere identitet, rolle og ejerskab; skjulte knapper er ikke tilstrækkelige. | Et konkret review eller en test af adgang til en anden kundes ordre. |
| Jeppe | En læsemodel giver fleksible katalogforespørgsler, men data kan være forsinkede. AI-tekst kræver validering og menneskelig godkendelse; den må ikke bestemme pris eller lagerstatus. | Et konkret valg om et GraphQL-filter eller et afvist AI-output. |
| Asger | En vellykket lokal transaktion afslutter ikke et distribueret køb. Gentagelser, gamle beskeder og uklare betalinger kræver gemte tilstande og eksplicit recovery. | Et konkret forløb med reservation, kompensation eller manuel betalingsafklaring. |
| Laith | Containere er ikke i sig selv høj tilgængelighed. Migrationer, readiness, netværk, målinger og afprøvet restore skal vurderes sammen. | Et konkret driftsproblem og den måling eller log, som hjalp med at finde årsagen. |

## CI-02 — accepteret antagelse

Gruppen har valgt at gå ud fra, at det konfigurerede CI-/releaseflow fungerer. Det behandles derfor ikke som en aktiv implementeringsopgave i denne omgang. Det er en accepteret antagelse, ikke et observeret GitHub-resultat; der er ikke indsat opdigtede run-ID'er, image-digests, branch-regler eller rollback-kørsler. Se [CI-STATUS.md](CI-STATUS.md).
