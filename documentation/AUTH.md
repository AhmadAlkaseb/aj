# Lokal OpenID Connect-login

**Beslutning:** Keycloak er projektets selvhostede tredjepartsidentitetsudbyder.
Det kræver ingen brugerregistrering hos Google, Microsoft eller andre eksterne tjenester.
Keycloak implementerer OAuth 2.0/OpenID Connect og køres i Docker sammen med en separat
PostgreSQL-database. Det er en rigtig OIDC-integration med et tredjepartsprodukt,
men der er ikke konfigureret federation til en ekstern Google-/Microsoft-konto.

## Start og demologin

```powershell
docker compose up -d --build --wait --wait-timeout 240
```

Åbn **http://localhost:3000** og tryk **Log ind**. Første Keycloak-opstart importerer
realm, public client, roller og demobrugere automatisk fra `identity/aj-realm.json`.

| Formål | Brugernavn | Adgangskode |
| --- | --- | --- |
| Kunde | `kunde01` til `kunde20` | `Demo123!` |
| Administrator i AJ Motors | `eksamen-admin` | `Admin123!` |
| Drift af Keycloak på http://localhost:8180/admin/ | `keycloak-admin` | `KeycloakAdmin123!` |

Kundelogins har rollen `customer`; eksamensadministratoren har rollen `admin`.
Kunderne har en `customerGuid`-claim, der matcher de faste kundeidentiteter i eksamensdataene.
Driftskontoen til Keycloak er adskilt fra AJ Motors-administratoren.
Alle konti og adgangskoder her er offentlige lokale demodata; opsætningen bruger
udviklingstilstand og HTTP og er ikke en produktionsopsætning.

Keycloaks database gemmes i sit eget volume. Et eksisterende realm overskrives ikke ved
genstart/import. Ændringer i realm-JSON skal derfor anvendes som en bevidst konfigurationsændring
i eksisterende miljøer, ikke ved at slette forretningsdatabaser.

Ved start af enkeltprojekter:

```powershell
docker compose -f compose.infrastructure.yml up -d --wait --wait-timeout 240
```

Det starter fælles RabbitMQ og Keycloak med identitetsdatabasen. Start derefter de
ønskede applikationer fra deres Compose-filer som beskrevet i projektets README.

## Implementeret flow

1. Frontend henter offentlig klientkonfiguration fra `/auth/config`.
2. Keycloaks officielle JavaScript-adapter starter Authorization Code-flow med PKCE S256,
   state og nonce. Klienten er public og indeholder ingen klienthemmelighed.
3. Brugeren logger ind på Keycloaks side; AJ Motors modtager ikke brugerens adgangskode.
4. Adapteren udveksler koden med tokens, håndterer refresh og holder access-/refresh-tokens
   i hukommelsen. Midlertidig callback-/PKCE-state håndteres af adapteren.
5. Frontend sender access-tokenet som Bearer til `/api/v1/auth/session` via frontend-proxyen.
6. Gatewayen validerer RS256-signatur mod konfigurerede JWKS, issuer, audience `aj-api`,
   udløb og påkrævede claims. ID-tokens accepteres ikke som access-token.
7. En valideret identitet vises i headeren. Silent SSO kan genetablere login ved genindlæsning
   uden at gemme access-token i localStorage.
8. **Log ud** afslutter Keycloak-sessionen og fjerner browserens aktive tokens.

Eksisterende signerede access-tokens kan fortsat være kryptografisk gyldige indtil udløb
(fem minutter plus valideringens korte clock-skew-tolerance). Der er ikke indført
per-request-introspection eller øjeblikkelig JWT-revocation ved logout.

Den officielle adapter er versionsfastlåst og vendoreret i frontendens `public/vendor/`;
den hentes ikke fra en CDN ved eksamen. Version, downloadkilde og npm-integritet er
registreret i `keycloak-version.json`, og licensen følger med.

## Grænse for denne todo-opgave

P0-afklaringen leverer en valgt og kørende identitetsudbyder, login/logout, valideret
identitet, demokonti og en dokumenteret rollemodel. **Rolle- og ejerskabsregler for
forretnings-API'erne er fortsat opgaven `SEC-01`.** Kunder, biler, ordrer og betalinger
kan stadig tilgås uden et token, indtil det trin er implementeret. En `admin`-claim
giver dermed endnu ikke håndhævet eneret til administrative handlinger.

Tokens videresendes gennem proxyen som forberedelse til de næste sikkerhedsopgaver.
Direkte serviceporte og interne endpoints skal også beskyttes i `SEC-01`.
Stripe forbliver ude af scope.

## Konfiguration og drift

| Indstilling | Default ved lokal JVM | Docker |
| --- | --- | --- |
| `OIDC_PUBLIC_URL` i frontend | `http://localhost:8180` | Samme browseradresse |
| `OIDC_ISSUER` i gateway | `http://localhost:8180/realms/aj` | Samme issuer som i tokenet |
| `OIDC_JWKS_URL` i gateway | Issuer + `/protocol/openid-connect/certs` | `http://keycloak:8180/realms/aj/protocol/openid-connect/certs` |
| `KEYCLOAK_URL` i gateway | `http://localhost:8180` | `http://keycloak:8180` |

Browseren bruger localhost, mens gatewayen henter offentlige signaturnøgler via intern
Docker-DNS. Signaturnøgler kommer kun fra den konfigurerede URL; tokenet vælger ikke selv en key-server.
Aktuelle nøgler caches. Et udfald hos Keycloak betyder derfor ikke nødvendigvis,
at allerede udstedte, fortsat gyldige tokens øjeblikkeligt afvises.

Gatewayens status inkluderer nu seks domænebackends og Keycloak. Hvis identitetsudbyderen
er nede, vises en status-/loginfejl, mens katalog og anden eksisterende gæsteadgang fortsat fungerer.

## Tests

```powershell
mvn -pl apigateway -am verify
python -m pip install playwright==1.64.1
python -m playwright install chromium
python scripts/check_oidc.py
# Med stop/start af Keycloak og kontrol af fortsat frontend/katalog:
python scripts/check_oidc.py --failures
```

`TokenVerifierTest` kontrollerer korrekt signatur samt forkert issuer/audience,
udløb, manglende/malformed token, ID-token og forfalsket signatur.
Browserkontrollen bruger de rigtige Keycloak-loginformularer, Authorization Code + PKCE,
valideret session i gatewayen, demokundens GUID, SSO ved reload og logout for både kunde og admin.
Tokens og adgangskoder skrives ikke til testrapporten. Testen ligger også i system-workflowen.

Se [Keycloak Docker](https://www.keycloak.org/getting-started/getting-started-docker),
[realm-import](https://www.keycloak.org/server/importExport),
[JavaScript-adapter](https://www.keycloak.org/securing-apps/javascript-adapter) og
[Nimbus JWT-validering](https://connect2id.com/products/nimbus-jose-jwt/examples/validating-jwt-access-tokens).
