# AJ Motors — resterende arbejde

**Opdateret:** 5. november 2026. **Aflevering:** 11. december 2026.

De implementerede og lokalt verificerede opgaver er fjernet. Der er ingen åbne P0-afklaringer. Accepterede valg og navngivne ejere/reviewers findes i [IMPLEMENTATION-PLAN.md](IMPLEMENTATION-PLAN.md), dokumentationen i [Report.md](Report.md), og faktiske lokale resultater i [EVIDENCE.md](EVIDENCE.md).

## Afgrænsning — CI-02

Efter brugerens beslutning går vi ud fra, at CI-/releaseflowet fungerer. CI-02 er derfor taget ud af den aktive opgaveliste. Det er ikke markeret som remote verificeret; faktisk status og den eventuelle senere procedure findes i [CI-STATUS.md](CI-STATUS.md).

## P1 — DOC-01: forfatternes afsluttende gennemlæsning

Den foreslåede periode 1. september–30. november, sprintmål, daily standups, balanceret ansvarsfordeling og fire individuelle faglige refleksionsudkast er nu skrevet i [TEAM-PLAN.md](TEAM-PLAN.md) og rapportens afsnit 5 og 7.

- [ ] Hver deltager bekræfter eget refleksionsudkast og tilføjer et faktisk eksempel, før teksten bruges som personlig erfaring eller historik.

Planen indeholder ingen opdigtede commits, fremmøderegistreringer eller afsluttede møder. Datoerne er et foreslået forløb. Rapport/PDF er opdateret med denne skelnen.

## Lokale grænser, som ikke skjules

Første performancekørsel overskred tosekundersmålet; en roligere gentagelse nåede p95 1,139 sekunder. Begge er bevaret. Det er en direkte backendmåling, ikke et internet-SLA eller bevis for 99 % availability. SQL/broker-workers er bevidst begrænset til én replika; frontend HPA og isolerede KEDA-jobs er faktisk demonstreret. Azure er en beskrevet cloudplan, og det lokale setup er en eksamensdemo.
