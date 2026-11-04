# Application UML

Diagrammerne dokumenterer den implementerede AJ Motors-applikation med lokal Keycloak/OIDC-login og uden Stripe.
Hver `.puml`-fil er en redigerbar PlantUML-kilde. SVG-versionen kan åbnes direkte
i en browser og forstørres uden kvalitetstab.

| Diagram | UML-kilde | SVG |
| --- | --- | --- |
| Architecture | [Architecture.puml](Architecture.puml) | [Architecture.svg](Architecture.svg) |
| Databases | [Databases.puml](Databases.puml) | [Databases.svg](Databases.svg) |
| API Contracts | [API Contracts.puml](<API Contracts.puml>) | [API Contracts.svg](<API Contracts.svg>) |
| API Contract Responses | [API Contract Responses.puml](<API Contract Responses.puml>) | [API Contract Responses.svg](<API Contract Responses.svg>) |

Architecture viser synkrone HTTP-kald, asynkrone RabbitMQ-beskeder og databaseejerskab.
Databases viser de aktuelle PostgreSQL-tabeller med felter, typer, nøgler og relationer
samt MongoDB-dokumentet og indlejrede felter. GUID-afhængigheder mellem services er
logiske referencer; kun lokale relationer har foreign keys.

API Contracts viser endpoints og request bodies. API Contract Responses viser svarfelter,
HTTP-statuskoder, fejlformat og driftsendpoints. Detaljeret tekst findes i [API.md](../API.md).
Diagrammerne viser de implementerede fulde produkt-/ordre-/betalingssvar, også for lister,
og de ekstra GUID-felter, som blev nødvendige for at forbinde ordre, bil og betaling.

SQL-diagrammet er sammenholdt med `information_schema` i alle fire kørende databaser.
Det viser eksempelvis, at `orders_details.notes` aktuelt er VARCHAR og at SQL-tidsstempler
følger de konkrete JPA-typer. MongoDB gemmer snapshot-tidsstempler som ISO-strenge.

## Render igen

Med Java 17 og Maven fra repository-roden:

```powershell
mvn -N org.apache.maven.plugins:maven-dependency-plugin:3.8.1:get '-Dartifact=net.sourceforge.plantuml:plantuml:1.2025.2' -Dtransitive=false
$plantuml = Join-Path $env:USERPROFILE '.m2/repository/net/sourceforge/plantuml/plantuml/1.2025.2/plantuml-1.2025.2.jar'
java -jar $plantuml -charset UTF-8 -checkonly documentation/application
java -jar $plantuml -charset UTF-8 -tsvg documentation/application
```

Diagrammerne bruger PlantUMLs indbyggede Smetana-layout og kræver ikke en separat
Graphviz-installation. Hvis Maven bruger et andet lokalt repository, tilpasses JAR-stien.
