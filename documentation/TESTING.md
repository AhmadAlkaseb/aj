# Tests og GitHub Actions

## De tre testtyper

| Type | Filer | Hvad kontrolleres | Kommando fra roden |
| --- | --- | --- | --- |
| Unit | `service-common/.../ApiTest.java`, `apigateway/.../RoutingTest.java` | GUID-/beløbsvalidering, obligatoriske felter, statusvalg og routing til rette service | `mvn test` |
| Integration | `service-common/.../HttpBoundaryIT.java`, `products/.../ProductRepositoryIT.java` | Rigtige lokale HTTP-servere, JSON-fejlsvar gennem proxy, utilgængelig upstream og Hibernate/databasemapping | `mvn verify -DskipUnitTests=true` |
| System | `scripts/smoke.py` | Hele applikationen gennem frontend-proxy, gateway, rigtige PostgreSQL-/MongoDB-databaser og RabbitMQ | `python scripts/smoke.py --failures` |

Den seneste isolerede Java-verifikation omfatter **28 unit-tests og 14 integrationstests**, alle bestået sammen med SpotBugs. Optællingen og kildens fingerprint findes i [EVIDENCE.md](EVIDENCE.md). Systemkontrollerne måler særskilte scenarier og lægges ikke sammen til en påstået dækningsprocent. Domænetests findes i alle seks backends; GraphQL har resolver-/grænsetests, gatewayen signaturtests og PDF-jobbet en test af det faktiske dokument.

ProductRepositoryIT bruger H2 i PostgreSQL-tilstand og kræver ikke Docker. Systemtestene
kontrollerer de faktiske PostgreSQL-, MongoDB- og RabbitMQ-containere.
HttpBoundaryIT starter HTTP-servere på tilfældige ledige porte og stopper dem igen.

`*Test.java` køres af Surefire i `test`-fasen; `*IT.java` køres af Failsafe i
`integration-test` og verificeres i `verify`. Se [Maven Failsafe](https://maven.apache.org/surefire/maven-failsafe-plugin/index.html).
Brug derfor `verify`, når integrationstests skal med.

```powershell
# Alle Java-tests og pakning af alle projekter:
mvn clean verify

# En enkelt service plus service-common:
mvn -pl users -am test
mvn -pl users -am verify -DskipUnitTests=true

# Systemtest mod den lokale applikation:
docker compose up -d --build --wait --wait-timeout 180
python scripts/smoke.py --failures
```

`--failures` stopper og genstarter projektets synchronizer, RabbitMQ, Users-database
og gateway. `finally` sikrer genstart ved testfejl. Uden flaget køres de første fem
systemtests, og nedbrudstesten springes over. Testdata mærkes og bevares som audithistorik.
Kør fejltesten alene, så to testkørsler ikke stopper hinandens afhængigheder.

Den ekstra browserkontrol i `scripts/browser_check.py` kan stadig bruges manuelt;
den indgår i system-workflowen og kræver Playwright/Chromium.

## Én YAML pr. service

I `.github/workflows/` findes:

- `users.yaml`
- `products.yaml`
- `orders.yaml`
- `payments.yaml`
- `product-view.yaml`
- `product-synchronizer.yaml`
- `apigateway.yaml`
- `frontend.yaml`

De starter ved push/pull request for den relevante mappe eller fælles kode og kan
også startes manuelt med `workflow_dispatch`. Alle bruger `service-ci.yaml`, som:

1. Henter kode og Java 17 på en isoleret GitHub-runner.
2. Kører unit-tests for den valgte service og dens Maven-afhængigheder.
3. Kører integrationstests og pakker servicen.
4. Gemmer Surefire-/Failsafe-rapporter som artifacts, også ved fejl.

Alle services bruger service-common og får derfor dets validerings- og
HTTP-integrationstests med via `-am`. Gateway og Products har desuden deres egne
tests. Et grønt service-job betyder ikke, at der findes selvstændige domænetests
for hver eneste service. `service-common` er et bibliotek og har ikke en separat app-workflow.
Genbrug følger [GitHub Actions reusable workflows](https://docs.github.com/en/actions/how-tos/reuse-automations/reuse-workflows).

`system.yaml` kører hele Docker-systemet én gang pr. relevant ændring og tester
købsgang, konkurrerende reservationer, betalingsfejl, soft delete og genopretning
efter nedbrud. Systemtests gentages ikke i alle otte service-workflows. Containerlogs
og testrapport gemmes før oprydning. Dets volumes slettes kun på den isolerede CI-runner.

GitHub-workflows bliver først udført på GitHub, når filerne pushes til repositoryet.
Lokalt kan de samme Maven-, Docker- og Python-kommandoer køres som ovenfor.

## Lokal OIDC-login

Fem unit-tests i gatewayens `TokenVerifierTest` kontrollerer JWT-signatur og tokenclaims.
`python scripts/check_oidc.py --failures` tester rigtigt Keycloak-login med PKCE,
rolleclaims, SSO ved reload, logout og providerudfald. Browserkontrollen installeres
og køres nu automatisk i system-workflowen. Se [AUTH.md](AUTH.md).

## Produktprojektion og dataejerskab

Tre HTTP-integrationstests i Product View kontrollerer det beskyttede ejerinterface.
Tre relay-integrationstests i Product Synchronizer kontrollerer envelope og retry-svar.
`python scripts/check_projection.py --failures` kontrollerer den rigtige database,
versionsrækkefølge, samtidige opdateringer, tombstones og køet genopretning ved
udfald af Product View/MongoDB. Testen kører i system-workflowen. Se [PROJECTION.md](PROJECTION.md).

## Asynkron backendkommunikation

Tre unit-tests i `EventsTest` kontrollerer metadata, korrelationsarv,
skemaversion og legacy-beskeder. `python scripts/check_async.py --failures`
kører fem fokuserede scenarier direkte mod backend-porte og en rigtig RabbitMQ:
autoritativ pris/betalingsgodkendelse, asynkrone afvisninger, dead-letter ved ukendt
schema, genopretning efter consumer-nedbrud og frigivelse af en sen reservation
til en slettet ordre. Modtagerservices genstartes i finally-blokke.
Testen bruger egne UUID'er og midlertidige auditkøer, som slettes efter kørslen.
Den beholder mærkede forretningsposter ligesom systemtesten og kræver en isoleret
udviklings-/CI-stak. Den kører i `system.yaml`. Se [ASYNC.md](ASYNC.md).

## Udvidet lokal verifikation

```powershell
# Isoleret kildekopi undgår konflikt med IDE'ens Java-kompilering:
python scripts/verify_java.py --quality
python scripts/configure_identity.py
python scripts/check_security.py
python scripts/check_reliability.py
python scripts/browser_check.py
python scripts/cooperation.py --exclusive
python scripts/check_oidc.py --failures
python scripts/backup_restore.py --verify
python scripts/performance.py
python scripts/collect_evidence.py
```

Kør nedbrudskontroller sekventielt mod samme stack. Cooperation starter selv fire ejere, deres rigtige PostgreSQL-databaser, broker og Keycloak i et unikt Compose-projekt, uden frontend/gateway. `--exclusive` stopper og gendanner root-stack for at passe til lokal RAM; kun fixture-projektets volumes fjernes. Testen kontrollerer Flyway, pris/reservation, betalingskompensation og behandlede inbox-beskeder.

Monitoring og Kubernetes har egne start-/stopprocedurer og kørselstests i [OBSERVABILITY.md](OBSERVABILITY.md) og [KUBERNETES.md](KUBERNETES.md). Første p95 var 3,782 sekunder; en roligere gentagelse målte 1,139 sekunder ved 10.000 biler/20 klienter. Begge kørsler er bevaret; [PERFORMANCE.md](PERFORMANCE.md) viser miljø og begrænsninger.

Kontraktkontrol: `python scripts/generate_contracts.py --check`, `python scripts/check_contracts.py` og `npm run validate --prefix tools/contracts` efter installation af afhængigheder. `quality.yaml` kører Java/SpotBugs, kontrakter og Actionlint. `codeql.yaml` analyserer koden. `release.yaml` afventer quality og system for samme revision før valgfri SHA-tagget GHCR-publicering. Faktiske remote run-ID'er må ikke udledes af lokale tests.

Rapporteksport kræver Pandoc, Tectonic og Python-afhængighederne i `scripts/requirements-report.txt`. På Windows konfigurerer `./scripts/setup_report.ps1` værktøjerne og Chromium. Kør `python scripts/export_report.py`; efter ændringer i de kompakte figurers kilder køres først `python scripts/render_report_figures.py`. Eksporten laver vektorfigurer, syntaxfarvede kodebokse, unummereret forside og sidetal fra 1. Den validerer indholdsfortegnelse/figurliste mod PDF-destinationerne og afviser mere end 30 fysiske sider. Metadata gemmes i documentation/evidence; kilde og LaTeX-log i test-results/report-latex. Java/Graphviz kræves kun ved valgfri genrendering af de fulde UML-modeller.

## Pipeline- og deploymenttests

`python -m unittest discover -s scripts/tests -v` kører 12 særskilte Python-tests af immutable image-manifester, blandede revisioner, bevaret målkonfiguration, migrationsfejl før appændringer, proof-binding, workerbegrænsninger og artifact-/rollbackkontrol. De tælles ikke med i de 28 Java-unit-tests eller 14 Java-integrationstests.

Den nye HTTPS-smoke er desuden prøvet i både læse- og business-tilstand via en midlertidig lokal TLS-proxy til de rigtige Compose-services. Den kontrollerer login, GraphQL, reservation og betalingskompensation; det er ikke en faktisk GitHub-/Kubernetes-release. [CI-CD.md](CI-CD.md) beskriver de deployklare workflows og nødvendig målkonfiguration. Actionlint kontrollerer alle YAML-workflows.
