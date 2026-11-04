# Tests og GitHub Actions

## De tre testtyper

| Type | Filer | Hvad kontrolleres | Kommando fra roden |
| --- | --- | --- | --- |
| Unit | `service-common/.../ApiTest.java`, `apigateway/.../RoutingTest.java` | GUID-/beløbsvalidering, obligatoriske felter, statusvalg og routing til rette service | `mvn test` |
| Integration | `service-common/.../HttpBoundaryIT.java`, `products/.../ProductRepositoryIT.java` | Rigtige lokale HTTP-servere, JSON-fejlsvar gennem proxy, utilgængelig upstream og Hibernate/databasemapping | `mvn verify -DskipUnitTests=true` |
| System | `scripts/smoke.py` | Hele applikationen gennem frontend-proxy, gateway, rigtige PostgreSQL-/MongoDB-databaser og RabbitMQ | `python scripts/smoke.py --failures` |

Der er tretten unit-tests, ni integrationstests og seks systemtests. Det er et lille
repræsentativt sæt, ikke fuld testdækning af alle services.
Tre af unit-testene kontrollerer eksamensdatasættets identiteter, betalingsreferencer
og konsistente statusfordeling. Seedning og genstart kan derudover kontrolleres med
`python scripts/check_demo.py --restart`; se [DEMO_DATA.md](DEMO_DATA.md).
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
den indgår ikke i GitHub Actions-workflowen og kræver Playwright/Chromium.

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
