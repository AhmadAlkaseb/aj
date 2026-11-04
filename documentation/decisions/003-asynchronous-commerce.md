# ADR-003 — Asynkron kundevalidering, reservation og betalingsstart

**Status:** Accepteret og implementeret, 4. november 2026.

Orders var afhængig af synkrone Users-/Products-opslag. Payments claimede ordren
over HTTP før lokal lagring; nedbrud imellem de to trin kunne efterlade et claim
uden en gemt betaling. Det øgede tilgængelighedskoblingen i det normale købsflow.

Vi gemmer først en PENDING-ordre eller REQUESTED-betaling og en outbox-besked i
samme transaktion. Kundevalidering sker før reservationsanmodningen. Products
reserverer og fastsætter pris under samme lås. Orders autoriserer betalingsstart
under ordrelåsen og sender fakturafelterne til Payments i sin egen outbox.
Alle nye events har fælles metadata og en korrelation, som følger ordren.

Konsekvensen er eventual consistency også ved oprettelse. 201 betyder modtaget
anmodning; GET viser senere godkendelse eller afvisning. Beløb/valuta er null før
ejerens svar, og frontend tilbyder først betaling/registrering, når status tillader det.
Nested samlinger bruger lokale filtre og returnerer tomme lister ved ukendte referencer.

HTTP bevares til brugerens request/response, status, OIDC og det interne interface
hos MongoDB-ejeren. Ingen af de fire SQL-domæner kalder hinanden over HTTP.
Beslutningen løser ikke udløb, vilkårlig beskedrækkefølge, multi-worker-koordinering
eller generelle migrationer. Detaljer, kompatibilitet, begrænsninger og tests:
[ASYNC.md](../ASYNC.md).
