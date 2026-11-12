# GitHub-login gennem Keycloak

GitHub bliver ekstern identitetsudbyder; Keycloak udsteder fortsat AJ-tokens.
De eksisterende backendkontroller af issuer, audience, rolle og ejerskab bevares.
Lokale kundekonti og administratoren slettes ikke. Løsningen kræver ingen betalt tjeneste.

## Engangsregistrering hos GitHub

Registreringen skal ske med en konto, I ejer. GitHub CLI eller push-adgang er ikke nødvendigt.
Åbn [New OAuth App](https://github.com/settings/applications/new) i jeres normale browser:

| Felt | Lokal Compose-værdi |
| --- | --- |
| Application name | AJ Motors local login |
| Homepage URL | `http://localhost:3000/` |
| Authorization callback URL | `http://localhost:8180/realms/aj/broker/github/endpoint` |

Generér en klienthemmelighed. Kør derefter fra repository-roden:

```powershell
python scripts/configure_federation.py setup
python scripts/configure_federation.py check
python scripts/configure_federation.py verify
```

`setup` beder lokalt om Client ID og skjult Client secret og konfigurerer den
eksisterende realm idempotent. Hemmeligheden gemmes kun i den ignorerede fil
`test-results/identity/github-oauth.json` og i Keycloak. Den skal ikke stå i chat,
rapport, frontend, billeder eller Git. Automatiseret konfiguration kan bruge
`GITHUB_OAUTH_CLIENT_ID` og `GITHUB_OAUTH_CLIENT_SECRET` med kommandoen `apply`.
`check` returnerer kun sanitiseret status. En ubrugt eller falsk provider aktiveres ikke.

## Verifikation af rigtig bruger

`verify` følger Keycloaks rigtige autorisationsflow frem til GitHub-redirect og
kontrollerer app-id, callback og state. Den logger ikke ind på en persons vegne
og er ikke bevis for et afsluttet eksternt login.

Åbn AJ Motors i et privat browservindue, vælg **Log ind → GitHub**, godkend den
registrerede app hos GitHub og gennemfør Keycloaks første-login/profiltrin.
Kontrollér derefter egen profil/ordre og afvist administratoradgang for kunden.
Logout og nyt login skal også fungere. Gem kun sanitiserede observationer som evidens.
Har en eksisterende lokal konto samme email, skal brugeren bevise kontrol over
den konto; email-lighed alene giver ikke automatisk kontolinkning.

Providerkonfigurationen beder kun om `user:email`, ingen repository-adgang,
gemmer ikke GitHub-tokens og tilføjer ikke administratorroller. Nye brugere får
realm-standardrollen `customer`; Users opretter deres egen profil ud fra den
betroede issuer/subject som ved lokal selvregistrering. Et GitHub-login giver
ikke adgang til en importeret kundes profil eller til `admin`.

Aktiv Kubernetes og cloud har andre issuer/callback-adresser og særskilte
realm-data. Opret/konfigurer den relevante OAuth-app til det miljø; klienthemmeligheder
ligger i runtime Secrets/Key Vault og ikke i et delt eksempelmanifest.

## Aktuel evidensgrænse

Konfigurationskode og sikkerhedstests kan køres uden en ekstern konto.
En fuldført integration kræver en ejerregistreret OAuth-app og faktisk browserlogin;
ingen af delene antages ud fra en lokal test. Se afleveringschecklisten.

Kilder: [Keycloak identity brokering](https://www.keycloak.org/docs/latest/server_admin/index.html#_github),
[GitHub OAuth app registration](https://docs.github.com/en/apps/oauth-apps/building-oauth-apps/creating-an-oauth-app).
