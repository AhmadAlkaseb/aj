# Local Kubernetes demonstration

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
