# ADR-002: Product View ejer produktlæsemodellen

**Status:** Besluttet og implementeret.  
**Dato:** 4. november 2026.  
**Grundlag:** Brugeren har valgt Product View som eneste MongoDB-ejer med internt API til Synchronizer.

## Beslutning

Product View har alene MongoDB-driver, databasekonfiguration og adgang til databasenetværket.
Product Synchronizer bevares som selvstændig RabbitMQ-consumer, der afleverer det oprindelige
event og snapshot til Product Views interne, autentificerede HTTP-interface.

## Konsekvenser

- Ingen backend skriver eller læser direkte i en anden services database.
- Den eksisterende CQRS-kæde bevares med en ekstra HTTP-grænse mellem relay og dataejer.
- Product View håndhæver produktversioner/tombstones og versionsbetingede erstatninger.
- En besked kvitteres først efter ejerens succesfulde svar; fejl giver genforsøg.
- MongoDB-volume og dokumentformat bevares. Ændringen kræver ingen nulstilling.
- Intern adgangsnøgle og privat databasenetværk indgår i den lokale løsning;
  generel identitets-/adgangshærdning fortsætter i `SEC-01`.
- Der er fortsat fem domæne/API-backends plus Synchronizer, så gateway/frontend ikke
  behøver tælle med for at opfylde minimumskravet til backendapplikationer.

Kontrakt, konfiguration og testprocedure findes i [PROJECTION.md](../PROJECTION.md).

## Implementation update — 5 November 2026

The SEC-01 follow-up mentioned above is now implemented: owning backends independently verify JWTs and enforce roles/resource ownership, while internal projection/PDF writes require service credentials. Real-token direct-port, ownership and self-registration checks passed. See [AUTH.md](../AUTH.md) and [EVIDENCE.md](../EVIDENCE.md). This update preserves the original decision context.
