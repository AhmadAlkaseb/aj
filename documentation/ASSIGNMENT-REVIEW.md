# Kravgennemgang af de tre opgaver · 12. november 2026

Grundlag: de faktiske PDF’er i [mandatory assignments](mandatory%20assignments/),
den aktuelle [rapport](Report.md), kildekode og registrerede lokale resultater.
`Final project description.pdf` er slutprojektet, ikke en tredje delaflevering
med samme itsLearning-/PowerPoint-krav som Assignment 1 og 2.

| Krav og PDF-side | Leverance / ændring | Evidensgrænse |
| --- | --- | --- |
| Assignment 1, s. 1: idé, krav, stack og ansvar | Rapport 1, 2 og 5; balanceret workstream-/reviewmodel | Arbejdsmodel er ikke målt historisk ligelig arbejdsindsats |
| Assignment 1, s. 1: frontend REST/GraphQL, arkitektur og kanaler | Rapport 2.1–2.3, arkitektur-/kontraktfigurer, eventtabel | Rigtige frontendflows, ikke kun API-definitioner |
| Assignment 1, s. 1: cloud, CI, test, logs og versionering | Rapport 3–5, diagram, konkret AKS-valg | Cloud er en plan; faktiske GitHub-runs kræver konto/repository-adgang |
| Assignment 1 og 2, s. 2: præsentation, rapport og repository-link | To PPTX-filer med PDF-previews og afleveringsindex | Upload og klassefremlæggelse kræver studenterhandling |
| Assignment 2, s. 1: delvis funktionelt system, tests og async | Compose, ejerdata, RabbitMQ, tests og rapport 4 | Observerede lokale resultater adskilt fra CI-hosting |
| Final, s. 1–3: fem backends, frontend, REST/GraphQL, async | Users, Products, Orders, Payments, Product View; separat synchronizer/job | Gateway/infrastruktur tælles ikke med |
| Final, s. 3: eksternt OAuth/OIDC-login | `configure_federation.py`, fem policytests og EXTERNAL-LOGIN.md | Kræver ejet OAuth-app og rigtigt browserlogin; lokal Keycloak er ikke stiltiende godkendt |
| Final, s. 3: dataejerskab og servicebeskrivelser | Rapport 2.2 udvidet med syv applikationsansvar, API/events, lagring og fejl/skalering | GUI-administration er en eksplicit lokal undtagelse, ikke business-service deling |
| Final, s. 3: CQRS, immutable data, idempotens, commutativitet og saga | Rapport 2.4 og kontrakter/tests | Testede histories afgrænsning bevares |
| Final, s. 4: AI, isolated serverless, monitoring og CI-tests | Ollama-workflow, KEDA PDF, seks dashboards, afhængige testgates | Brokerkonfiguration er ikke bevis for eksternt login; lokale tests er ikke GitHub-run |
| Final, s. 5–7: rapportstruktur, modeller og cloudplan | Rapportens tre hovedmodeller, komplet cloudplan, bilag A–E indlejret i PDF | PDF og figurer regenereres og valideres |
| Final, s. 1 og 7: bidrag, metode og refleksion | Scrum-inspireret model og faglige refleksionsudkast | Git-historikken viser 22 Ahmad- og 7 Jeppe-commits i HEAD ved gennemgangen; manglende attribution kræver autentisk dokumentation |

## Hvad der er rettet

21 containere er rettet til 24; GUI-administration og standby er beskrevet.
Users-beskrivelsen har nu korrekt uafhængig rolle-/ejerskabskontrol. CI-figuren
viser både change-aware validering og komplet releasegate. Cloudplanen vælger
konkrete komponenter og placerer Keycloak, Ollama og confirmation Jobs.
Bilagene leveres i PDF’en. Obligatoriske PowerPoints er genereret.

## Afgrænsning ved manglende kontoadgang

Brugeren oplyste, at GitHub-login i terminalen ikke er muligt og bad om en anden
løsning. Derfor bruges lokale test-/buildresultater som sessionens alternativ.
Det ændrer ikke slutkravet om repository og synlige autentiske bidrag; der opfindes
ingen run-id’er, OAuth-klienter, udgivelser eller historiske samarbejdserfaringer.
Se [afleveringsindex](submission/README.md) for filer og præcise eksterne trin.
