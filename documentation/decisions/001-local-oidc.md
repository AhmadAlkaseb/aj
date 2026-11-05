# ADR-001: lokal Keycloak som identitetsudbyder

**Status:** Besluttet og implementeret lokalt.  
**Dato:** 4. november 2026.  
**Relateret opgave:** den afsluttede P0-afklaring af OAuth/OpenID Connect.

## Kontekst

Finalbeskrivelsen kræver tokenbaseret sikkerhed og en tredjepartsidentitetsudbyder.
Brugeren har ændret den tidligere beslutning om at udelade OAuth og bedt om en
integration, som kan bruges uden selv at oprette en ekstern konto.

## Beslutning

Brug Keycloak, et tredjepartsidentitetsprodukt, i en lokal Docker-container med egen
PostgreSQL-database. Importér klient, realm, kundelogins og administrator automatisk.
Brug Authorization Code + PKCE S256 og validerede JWT-access-tokens i gatewayens session-API.
Frontend benytter den officielle, lokalt vendorerede adapter til login, refresh og logout.
Stripe forbliver udeladt.

## Konsekvenser

- Ingen Google-/Microsoft-/GitHub-registrering eller cloudkonto er nødvendig.
- Login er et reelt OIDC-flow mod en selvhostet IdP, ikke en mock.
- Der er ikke tilføjet federation til en ekstern kontoudbyder. Denne forskel fremgår af rapporten.
- Keycloak og dets database er to nye infrastrukturkomponenter og tæller ikke som domænemicroservices.
- Kunde-/administratorroller og kundereferencer er klargjort, men håndhævelse af
  adgang og ejerskab i forretningsservices fortsætter i `SEC-01`.
- Den lokale HTTP-/demokonfiguration kræver en særskilt sikker produktionskonfiguration.

Detaljer og verificering: [AUTH.md](../AUTH.md).

## Implementation update — 5 November 2026

The SEC-01 follow-up mentioned above is now implemented: owning backends independently verify JWTs and enforce roles/resource ownership, while internal projection/PDF writes require service credentials. Real-token direct-port, ownership and self-registration checks passed. See [AUTH.md](../AUTH.md) and [EVIDENCE.md](../EVIDENCE.md). This update preserves the original decision context.
