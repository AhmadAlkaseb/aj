# Local Kubernetes demonstration

## Kubernetes-GUI under Compose

Grafanas **AJ Control Center** linker til Headlamp på http://localhost:14466.
`python scripts/kubernetes.py standby` opretter eller klargør den lokale klynge
uden at starte en ekstra kopi af applikationen: AJ-deployments står på nul,
og confirmation-jobbet er pauseret. Compose kører fortsat programmet.
Headlamp bruger en lokal servicekonto med læseadgang til ressourcer og logs,
uden adgang til Secrets eller ændringer. Kubeconfig opbevares kun under den
ignorerede mappe `test-results/admin`. Normal Compose-start bevarer denne standby.
Aktiv Kubernetes-kørsel og dens særskilte database- og loginopsætning beskrives nedenfor.

## Daglig drift og overvågning

Start med `./scripts/start.ps1 -Runtime kubernetes`. Scriptet bygger de aktuelle
applikationsimages og starter hele systemet i klyngen. Når klyngen allerede kører,
vælges Kubernetes med `./scripts/start.ps1 -Runtime kubernetes`. Standardstart
bruger nu den samlede Compose-gruppe **aj** med fælles lokalt login. Compose og Kubernetes har
separate databaser; Compose-data flyttes ikke automatisk til Kubernetes.

Programmet: **https://localhost:8443** (lokalt udviklingscertifikat).
Grafana: **http://localhost:13001**. RabbitMQ: **http://localhost:15672**.
Genåbn forbindelserne efter genstart med `python scripts/kubernetes.py access`.
Port-forward-processerne kører i baggrunden og binder kun til loopback.
Viderestillinger genoprettes automatisk, når en pod udskiftes. Efter længere
afbrydelser eller genstart af computeren kan de åbnes igen med `access`.

Grafana-brugeren er `admin`; RabbitMQ-brugeren er `aj`. Adgangskoderne er
genereret separat fra Compose og findes lokalt i den ignorerede fil
`test-results/k8s/secrets.json` som `grafana-password` og `rabbit-password`.
Del eller commit ikke denne fil.

Vis kun de to UI-adgangskoder lokalt:

```powershell
Get-Content test-results/k8s/secrets.json | ConvertFrom-Json | Select-Object grafana-password,rabbit-password
```

Klyngen omfatter Prometheus, Loki, Alloy, Grafana og kube-state-metrics med
vedvarende volumes og begrænsede rettigheder. Prometheus finder hver app-pod,
så skalering ikke skjuler replikaer bag én service-adresse. Alloy indsamler
applikationslogs gennem Kubernetes API. Database-logs indsamles ikke.
Dashboardene viser HTTP, RabbitMQ/outbox, logs og Kubernetes-replikaer,
genstarter, readiness, HPA og lagring.

```powershell
kubectl --context kind-aj-exam -n aj get pods,deploy,hpa,pvc
kubectl --context kind-aj-exam -n aj get events --sort-by=.lastTimestamp
python scripts/check_operations.py --runtime kubernetes
python scripts/kubernetes.py verify
```

`check_operations.py` kontrollerer de seks dashboards, datakilder, metrics,
logs og en isoleret RabbitMQ-køs forløb fra publicering til ventende besked
og modtagelse. Testkøen slettes bagefter. `verify` tester en handel, PDF-job,
databaseisolering og automatisk skalering. Ændringer i overvågningen kan
anvendes med `python scripts/kubernetes.py monitoring`.

Keycloak har ekstra CPU og op til 15 minutters opstartstid til første
databaseinitialisering/import. Det forhindrer, at en kold opstart afbrydes
af en for tidlig genstart. Loginopsætningen genforsøger kortvarige ingressfejl.
Ændringer i lokale applikationsimages markeres med image-ID på pod-skabelonen,
så genbrug af `:local` udløser en ny udrulning, når indholdet er ændret.

Lokal kontrol 11. november 2026: fem dashboards, 35 panelqueries, ti metrics-kilder,
Loki og en isoleret RabbitMQ-afsendelse/ventende kø/modtagelse bestod.
Kubernetes-testen bestod TLS-handel, pod-erstatning, PDF-job, databaseisolering
og skalering fra én til to klarstående frontend-pods. Resultaterne findes lokalt
i `test-results/operations-results.json` og `test-results/kubernetes-results.json`.

Kubernetes styrer containerne i sine egne pods. Det overtager ikke allerede
kørende Compose-containere. Deployments opretholder replikaer og erstatter
mistede pods; probes afgør, hvornår trafik kan sendes, og hvornår containere
skal genstartes. Stateful infrastruktur bruger én replika, Recreate og PVC'er
for at undgå samtidig brug af samme datavolume. kind-noden genstarter med
Docker Desktop. `python scripts/kubernetes.py stop` **sletter demoklyngen og
dens Kubernetes-data** og starter Compose igen; brug ikke denne kommando som
en almindelig pause. Luk i stedet Docker Desktop for at bevare klyngen.

The local cluster is **kind-aj-exam**, separate from every Compose database volume.
`k8s/generate.py` produces `k8s/base.json` from the reviewed service configuration.
`python scripts/kubernetes.py start` stops Compose, creates the named kind cluster,
installs Calico, KEDA and metrics-server, loads local application images, generates
runtime secrets/TLS, applies the resources and waits for rollout readiness.
`python scripts/kubernetes.py verify` drives a TLS purchase/PDF flow, tests database
isolation and exercises frontend HPA. `python scripts/kubernetes.py stop` deletes only
that demonstration cluster and restores Compose. No cloud account is used.

The startup script uses the kind binary in test-results/tools/kind.exe on Windows.
Download the pinned v0.33.0 release from the official kind distribution if absent.
The cluster pins Kubernetes v1.35.0, Calico v3.33.0, KEDA v2.21.0 and metrics-server v0.9.0.
Initial image/operator downloads need internet access; running the prepared demo does
not require a newly registered account.

Every owner has its own persistent claim. Calico enforces ingress policies so only the
owning app can reach its PostgreSQL/Mongo store. SQL init containers migrate before the
application validates its schema. Public traffic goes through Traefik and a development
TLS certificate at https://localhost:8443. Backend/database Services are ClusterIP only;
JWT/ownership checks still apply inside each backend.

Random database, broker, provider-administration and internal-service credentials are
stored in a Kubernetes Secret. Runtime values and private TLS files remain under ignored
test-results/k8s; they are never embedded in committed manifests or container build context.
The imported examination user passwords are deliberately documented demo credentials.
The local self-signed certificate is not a production trust configuration.

Frontend HPA scales from one to two replicas using actual CPU metrics. A temporary load
Job and narrowly scoped ingress policy are removed after verification. Broker workers
remain bounded; the confirmation function uses a distinct KEDA ScaledJob and queue.
An empty queue has no permanent confirmation process. Each Job handles one event and exits.

RabbitMQ uses a stable node name/hostname so recreated pods can find their persistent
state. The local metrics-server insecure-kubelet-TLS option is specific to kind; a real
cluster must use valid kubelet certificates. This single-node cluster demonstrates orchestration,
not host-failure availability or production database/broker HA.

Official references: [kind](https://kind.sigs.k8s.io/docs/user/quick-start/),
[Calico requirements](https://docs.tigera.io/calico/latest/getting-started/kubernetes/requirements),
[KEDA RabbitMQ](https://keda.sh/docs/2.21/scalers/rabbitmq-queue/).

The verified bootstrap enables MutatingAdmissionPolicy together with its v1beta1 API and installs Calico's v1beta1 vendor CRDs before the operator. Re-running start preserves the generated runtime secrets; it does not force-overwrite operator-managed CRD fields. KEDA's broker URL uses the full `rabbitmq.aj.svc.cluster.local` address because its operator runs in another namespace. The ownership probe waits for a real terminated container and checks exit status plus logs, avoiding kubectl attach races and image-pull false positives.

The 5 November local run passed HTTPS commerce, PDF job execution, the negative cross-owner database probe and CPU-driven frontend scaling from 1 to 2 replicas. `stop` subsequently restored all 16 normal Compose containers without deleting their volumes. See [EVIDENCE.md](EVIDENCE.md).
