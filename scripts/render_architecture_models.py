"""Render editable, print-sized SVG architecture models without external services."""
from html import escape
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'documentation/report-figures'
INK = '#172b4d'
BLUE = '#2563a6'
GREEN = '#187a69'
PURPLE = '#7556a6'
AMBER = '#9c6720'


class Figure:
    def __init__(self, name, title, subtitle, height):
        self.name = name
        self.parts = [f'<svg xmlns="http://www.w3.org/2000/svg" width="1200" height="{height}" viewBox="0 0 1200 {height}" role="img" aria-labelledby="title desc">',
            f'<title id="title">{escape(title)}</title><desc id="desc">{escape(subtitle)}</desc>',
            '<defs>' + ''.join(f'<marker id="arrow-{color[1:]}" markerWidth="9" markerHeight="9" refX="8" refY="4.5" orient="auto" markerUnits="userSpaceOnUse"><path d="M0,0 L9,4.5 L0,9 Z" fill="{color}"/></marker>' for color in [INK, BLUE, GREEN, PURPLE, AMBER]) + '</defs>',
            '<style>text{font-family:Arial,Helvetica,sans-serif} .body{font-size:22px;fill:#172b4d}.small{font-size:19px;fill:#475569}.heading{font-size:19px;font-weight:700;letter-spacing:.5px}</style>',
            f'<rect width="1200" height="{height}" fill="white"/>']
        self.text(24, 36, title, size=30, weight='700')
        self.text(24, 68, subtitle, size=20, color='#475569')

    def text(self, x, y, value, size=22, color=INK, weight='400', anchor='start'):
        self.parts.append(f'<text x="{x}" y="{y}" font-size="{size}" fill="{color}" font-weight="{weight}" text-anchor="{anchor}">{escape(value)}</text>')

    def rect(self, x, y, w, h, fill='#f5f8fc', stroke='#cbd5e1', radius=12, dash=False):
        self.parts.append(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{radius}" fill="{fill}" stroke="{stroke}" stroke-width="1.6"' + (' stroke-dasharray="7 5"' if dash else '') + '/>')

    def card(self, x, y, w, h, title, lines, color=BLUE, fill='#eef5fc', size=22):
        self.rect(x, y, w, h, '#ffffff', '#cbd5e1')
        self.parts.append(f'<path d="M{x+12},{y} H{x+w-12} Q{x+w},{y} {x+w},{y+12} V{y+43} H{x} V{y+12} Q{x},{y} {x+12},{y}Z" fill="{fill}"/>')
        # Small vector pictograms keep print output sharp and require no remote assets.
        self.icon(x + 15, y + 10, title, color)
        self.text(x + 48, y + 28, title, size=16, color=color, weight='700')
        for index, line in enumerate(lines):
            self.text(x + 18, y + 64 + index * 29, line, size=size)

    def icon(self, x, y, title, color):
        self.parts.append(f'<g transform="translate({x} {y})" fill="none" stroke="{color}" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">')
        if any(word in title for word in ['STORE', 'STORAGE', 'DATABASE', 'MongoDB']):
            self.parts.append('<path d="M1 5v15c0 6 25 6 25 0V5M1 12c0 6 25 6 25 0"/><ellipse cx="13.5" cy="5" rx="12.5" ry="4"/>')
        elif any(word in title for word in ['BUS', 'ASYNCHRONOUS', 'RABBIT', 'MESSAGE']):
            self.parts.append('<rect x="1" y="4" width="26" height="19" rx="3"/><path d="m2 5 12 9L26 5M6 19h5m4 0h6"/>')
        elif any(word in title for word in ['IDENTITY', 'TLS', 'LIFECYCLE']):
            self.parts.append('<path d="m14 1 11 4v8c0 7-11 12-11 12S3 20 3 13V5Z"/><path d="m8 12 4 4 8-9"/>')
        elif any(word in title for word in ['MONITOR', 'OBSERV', 'GRAFANA', 'METRICS', 'LOG']):
            self.parts.append('<path d="M2 1v23h25M5 17l6-7 6 4 8-11"/><circle cx="11" cy="10" r="2"/><circle cx="17" cy="14" r="2"/>')
        elif title == 'USER':
            self.parts.append('<circle cx="14" cy="7" r="5"/><path d="M3 26v-4c0-10 22-10 22 0v4Z"/>')
        elif any(word in title for word in ['NETWORK', 'NAMESPACE', 'CLUSTER']):
            self.parts.append('<path d="M14 7v8M4 15h20M4 15v5m20-5v5"/><rect x="8" y="0" width="12" height="8" rx="2"/><rect x="0" y="20" width="9" height="7" rx="1"/><rect x="19" y="20" width="9" height="7" rx="1"/>')
        else:
            self.parts.append('<rect x="1" y="2" width="26" height="23" rx="3"/><path d="M1 9h26M6 5h1m3 0h1M7 15l-3 3 3 3m14-6 3 3-3 3m-6-7-3 8"/>')
        self.parts.append('</g>')

    def arrow(self, points, color=BLUE, dashed=False):
        coords = ' '.join(f'{x},{y}' for x, y in points)
        self.parts.append(f'<polyline points="{coords}" fill="none" stroke="{color}" stroke-width="2.5" marker-end="url(#arrow-{color[1:]})"' + (' stroke-dasharray="7 5"' if dashed else '') + '/>')

    def save(self):
        OUT.mkdir(exist_ok=True)
        (OUT / (self.name + '.svg')).write_text('\n'.join(self.parts + ['</svg>']) + '\n', encoding='utf-8')


def system():
    f = Figure('architecture', 'System architecture',
        'Logical responsibilities and communication | independent of Compose or Kubernetes', 670)
    f.card(24, 135, 190, 109, 'USER', ['Browser', 'AJ Motors UI'], size=21)
    f.card(264, 135, 240, 109, 'PRESENTATION', ['Frontend', 'Same-origin API proxy'], size=20)
    f.card(554, 135, 240, 109, 'PUBLIC API', ['API Gateway', 'Routing + token checks'], size=20)
    f.card(934, 135, 240, 109, 'IDENTITY', ['Keycloak: OIDC', 'Own PostgreSQL store'], GREEN, '#edf8f3', size=20)
    f.arrow([(214, 189), (264, 189)])
    f.arrow([(504, 189), (554, 189)])
    f.arrow([(794, 189), (934, 189)])
    f.text(864, 174, 'JWKS', size=19, anchor='middle')
    f.arrow([(384, 135), (384, 104), (1054, 104), (1054, 135)])
    f.text(718, 96, 'Browser login: OIDC + PKCE', size=19, anchor='middle')
    f.card(24, 325, 450, 145, 'AUTHORITATIVE DOMAIN APIS',
        ['Users · Products · Orders · Payments', 'Local SQL state + inbox/outbox', 'Commands and business decisions'], size=22)
    f.card(514, 325, 260, 145, 'ASYNCHRONOUS BUS',
        ['RabbitMQ', 'Durable queues', 'Confirms + consumer ACKs'], AMBER, '#fff7e8', size=19)
    f.card(814, 325, 360, 145, 'READ SIDE',
        ['Product Synchronizer → owner API', 'Product View: REST / GraphQL', 'Only Product View uses MongoDB'], size=19)
    f.arrow([(674, 244), (674, 283), (249, 283), (249, 325)])
    f.text(411, 272, 'Commands', size=19, anchor='middle')
    f.arrow([(734, 244), (734, 283), (994, 283), (994, 325)])
    f.text(891, 272, 'Catalogue reads', size=19, anchor='middle')
    f.arrow([(474, 370), (514, 370)], AMBER, True)
    f.arrow([(514, 418), (474, 418)], AMBER, True)
    f.arrow([(774, 370), (814, 370)], AMBER, True)
    f.card(24, 535, 450, 96, 'FOUR PRIVATE PostgreSQL STORES',
        ['Users · Products · Orders · Payments'], GREEN, '#edf8f3', size=21)
    f.arrow([(249, 470), (249, 535)], GREEN)
    f.card(514, 535, 260, 96, 'PDF CONFIRMATION', ['Writes via Orders API'], AMBER, '#fff7e8', size=19)
    f.arrow([(644, 470), (644, 535)], AMBER, True)
    f.arrow([(514, 583), (494, 583), (494, 500), (350, 500), (350, 470)])
    f.text(365, 523, 'Orders HTTP', size=18)
    f.card(814, 535, 360, 96, 'PRIVATE MongoDB READ MODEL', ['Versioned product snapshots'], GREEN, '#edf8f3', size=21)
    f.arrow([(994, 470), (994, 535)], GREEN)
    f.text(24, 659, 'Solid: HTTP / owner storage. Dashed: RabbitMQ events. Optional: Products → Ollama for reviewed drafts.', size=19)
    f.save()


def compose():
    f = Figure('docker-compose', 'Docker Compose system architecture',
        'One Docker Desktop project: aj | 24 named containers | loopback host access', 790)
    f.rect(20, 92, 1160, 628, '#f8fafc', BLUE, dash=True)
    f.card(40, 120, 360, 147, 'EDGE NETWORK | 2 CONTAINERS',
        ['frontend :3000', 'apigateway :8080', 'Gateway also joins backend'], size=21)
    f.card(450, 120, 710, 147, 'BACKEND NETWORK | 9 CONTAINERS',
        ['users · products · orders · payments', 'product-view · product-synchronizer', 'rabbitmq · confirmation-job · keycloak'], size=22)
    f.arrow([(400, 185), (450, 185)])
    f.text(424, 167, 'HTTP', size=16, anchor='middle')
    f.card(40, 335, 580, 174, 'PRIVATE OWNER NETWORKS | 6 STORES',
        ['users-db · products-db · orders-db', 'payments-db · keycloak-db', 'product-view-db (MongoDB)'], GREEN, '#edf8f3')
    f.card(660, 335, 500, 174, 'OBSERVABILITY | 4 CONTAINERS',
        ['prometheus :9090 · grafana :13001', 'alloy → loki :3100', 'Prometheus joins edge + backend'], PURPLE, '#f4f0fa', size=21)
    f.arrow([(550, 267), (550, 335)], GREEN)
    f.text(530, 306, 'Application access follows ownership', size=18, color=GREEN, anchor='end')
    f.arrow([(920, 267), (920, 335)], PURPLE, True)
    f.text(943, 306, 'Metrics / logs', size=19, color=PURPLE)
    f.card(40, 548, 1120, 148, 'LOCAL ADMINISTRATION | 3 CONTAINERS',
        ['database-gui :18080 → five SQL stores | mongo-gui :18081 → MongoDB',
         'kubernetes-gui :14466 → kind control plane (read-only; no Secrets)',
         'Grafana Control Center links all GUIs; explicit local admin network access'], PURPLE, '#f4f0fa', size=20)
    f.text(24, 750, 'Named volumes retain SQL, MongoDB, broker state and monitoring history.', size=21)
    f.text(24, 779, 'The kind control-plane container is separate from the 24-service Compose project.', size=19)
    f.save()


def kubernetes():
    f = Figure('kubernetes-deployment', 'Local Kubernetes deployment',
        'kind-aj-exam | one Docker-hosted node | namespace aj | separate data from Compose', 690)
    f.rect(20, 94, 838, 536, '#f8fafc', BLUE, dash=True)
    f.text(40, 124, 'APPLICATION NAMESPACE: aj', size=18, color=BLUE, weight='700')
    f.card(40, 147, 242, 114, 'TLS ENTRY', ['Traefik + aj-tls', ':8443 → NodePort 30443'], size=18)
    f.card(321, 147, 250, 114, 'HTTP FRONT DOOR', ['Frontend: 1–2 replicas', 'API Gateway: ClusterIP'], size=19)
    f.card(610, 147, 228, 114, 'IDENTITY', ['Keycloak', 'Private identity DB'], size=20)
    f.arrow([(282, 206), (321, 206)])
    f.arrow([(571, 206), (610, 206)])
    f.card(40, 321, 390, 141, 'OWNER APIS + READ MODEL',
        ['Users · Products · Orders · Payments', 'Product View + Synchronizer', 'Probes, requests and limits'], size=19)
    f.card(470, 321, 368, 141, 'ASYNCHRONOUS WORK',
        ['RabbitMQ + persistent volume', 'KEDA → confirmation Job (0–2)', 'Job → Orders PDF API → ACK'], AMBER, '#fff7e8', size=20)
    f.arrow([(430, 392), (470, 392)], AMBER, True)
    f.arrow([(444, 261), (444, 291), (235, 291), (235, 321)])
    f.card(40, 511, 390, 98, 'OWNER STORAGE', ['5 PostgreSQL stores + MongoDB', '6 owner PVCs; Calico isolation'], GREEN, '#edf8f3', size=19)
    f.arrow([(235, 462), (235, 511)], GREEN)
    f.card(470, 511, 368, 98, 'IN-CLUSTER MONITORING', ['Prometheus · Grafana · Loki', 'Alloy · kube-state-metrics'], PURPLE, '#f4f0fa', size=20)
    f.arrow([(654, 462), (654, 511)], PURPLE, True)
    f.card(884, 94, 292, 260, 'CLUSTER CONTROLLERS',
        ['System namespaces:', 'Calico: network policy', 'KEDA: event-driven Jobs', 'metrics-server: CPU data', 'HPA controller: replicas', 'Replica recovery controller'], size=19)
    f.card(884, 381, 292, 249, 'LIFECYCLE AND DATA',
        ['Build and load local images', 'Apply Secrets and manifests', 'SQL init: Flyway migration', 'Readiness gates HTTP traffic', 'PVCs survive pod replacement', 'Node deletion removes local', 'cluster storage'], GREEN, '#edf8f3', size=18)
    f.text(24, 664, 'Solid: HTTP / owner storage. Dashed: asynchronous delivery or telemetry. Single-node operation is not HA.', size=19)
    f.save()


def delivery():
    f = Figure('message-delivery', 'Reliable message delivery',
        'One eventId across retries | one correlationId across the business flow', 448)
    columns = [(40, 'OWNER DATABASE', GREEN), (330, 'PUBLISHER', BLUE), (620, 'RABBITMQ', AMBER), (910, 'CONSUMER', BLUE)]
    for x, title, color in columns:
        f.rect(x, 105, 250, 58, '#f5f8fc', color)
        f.text(x + 125, 141, title, size=19, color=color, weight='700', anchor='middle')
        f.parts.append(f'<line x1="{x+125}" y1="168" x2="{x+125}" y2="369" stroke="#cbd5e1" stroke-dasharray="5 5"/>')
    f.text(184, 194, '1  State + outbox commit', size=19, color=GREEN)
    f.arrow([(165, 213), (455, 213)], GREEN)
    f.text(485, 198, '2  Publish persistent event', size=19)
    f.arrow([(455, 230), (745, 230)], AMBER, True)
    f.text(490, 265, '3  Broker confirms receipt', size=19, color=AMBER)
    f.arrow([(745, 277), (455, 277)], AMBER, True)
    f.text(182, 313, '4  Commit outbox removal', size=19, color=GREEN)
    f.arrow([(455, 324), (165, 324)], GREEN)
    f.text(765, 198, '5  Ready → delivery', size=19)
    f.arrow([(745, 230), (1035, 230)], AMBER, True)
    f.text(1051, 268, '6  Commit', size=18)
    f.text(1051, 292, 'inbox + state', size=18)
    f.text(800, 339, '7  Consumer ACK', size=19)
    f.arrow([(1035, 352), (745, 352)], AMBER, True)
    f.rect(20, 386, 1160, 47, '#f4f0fa', '#d7cce8')
    f.text(40, 416, 'Outbox = waiting to send | Ready = queued | Unacked = processing | Loki = correlated delivery history', size=20)
    f.save()


def observability():
    f = Figure('observability', 'Observability and message visibility',
        'Six Grafana dashboards: one GUI portal and five operational views', 474)
    f.card(24, 112, 332, 109, 'TELEMETRY SOURCES', ['Apps: HTTP + outbox metrics', 'RabbitMQ: queue + ACK metrics'], size=20)
    f.card(423, 112, 305, 109, 'METRICS', ['Prometheus', 'Queue depth, rate and alerts'], PURPLE, '#f4f0fa', size=20)
    f.card(24, 284, 332, 109, 'STRUCTURED LOGS', ['HTTP + broker event metadata', 'eventId and correlationId'], size=20)
    f.card(423, 284, 305, 109, 'LOG COLLECTION', ['Alloy → Loki', 'Application logs; no DB logs'], PURPLE, '#f4f0fa', size=19)
    f.card(810, 112, 366, 311, 'GRAFANA | SIX VIEWS',
        ['1  Control Center: all GUI links', '2  Operations overview', '3  HTTP and services', '4  RabbitMQ and delivery', '5  Logs and troubleshooting', '6  Kubernetes operation', '    (active cluster metrics only)'], PURPLE, '#f4f0fa', size=20)
    f.arrow([(356, 166), (423, 166)], PURPLE)
    f.arrow([(356, 338), (423, 338)], PURPLE)
    f.arrow([(728, 166), (810, 166)], PURPLE)
    f.arrow([(728, 338), (810, 338)], PURPLE)
    f.text(24, 459, 'Headlamp reads cluster resources in standby; active in-cluster monitoring supplies Kubernetes metrics.', size=19)
    f.save()


def recovery():
    f = Figure('kubernetes-recovery', 'Kubernetes recovery and bounded scaling',
        'Three independently verified control loops; scaling does not replicate state stores', 352)
    rows = [
        (102, 'POD RECOVERY', ['Frontend pod removed', 'Deployment replaces pod', 'Readiness restores traffic'], BLUE, '#eef5fc'),
        (181, 'CPU AUTOSCALING', ['CPU exceeds target', 'metrics-server → HPA', 'Frontend grows: 1 → 2'], GREEN, '#edf8f3'),
        (260, 'EVENT-DRIVEN JOB', ['order.completed queued', 'KEDA starts one-shot Job', 'Store PDF → ACK → exit'], AMBER, '#fff7e8')]
    for y, label, steps, color, fill in rows:
        f.text(24, y + 35, label, size=17, color=color, weight='700')
        for i, step in enumerate(steps):
            x = 228 + 320 * i
            f.rect(x, y, 290, 59, fill, color)
            f.text(x + 145, y + 36, step, size=19, anchor='middle')
            if i < 2:
                f.arrow([(x + 290, y + 30), (x + 320, y + 30)], color)
    f.save()


def purchase():
    f = Figure('purchase-saga', 'Reservation and payment saga',
        'Each arrow crosses a message boundary; each service commits its own local transaction', 460)
    steps = [
        (24, 120, 'ORDERS', ['PENDING', 'customer.check']),
        (424, 120, 'USERS + ORDERS', ['customer.valid → RESERVING', 'Orders emits quantitycheck']),
        (824, 120, 'PRODUCTS', ['Lock and reserve car', 'Authoritative price']),
        (824, 302, 'ORDERS', ['RESERVED → PAYMENT_PENDING', 'Claim order + issue invoice']),
        (424, 302, 'PAYMENTS', ['Record manual outcome', 'COMPLETED / FAILED']),
        (24, 302, 'ORDERS + PRODUCTS', ['Complete order + mark SOLD', 'Or compensate: release car'])]
    for x, y, title, lines in steps:
        f.card(x, y, 352, 112, title, lines, size=19)
    f.arrow([(376, 176), (424, 176)], AMBER, True)
    f.arrow([(776, 176), (824, 176)], AMBER, True)
    f.arrow([(1000, 232), (1000, 302)], AMBER, True)
    f.arrow([(824, 358), (776, 358)], AMBER, True)
    f.arrow([(424, 358), (376, 358)], AMBER, True)
    f.text(24, 445, 'Simplified successful path. Validation rejection, cancellation and deadlines can branch to recovery.', size=19)
    f.save()


def testing():
    f = Figure('testing-strategy', 'Verification from local rules to business flows',
        'Different test boundaries answer different questions; local evidence does not imply a remote release', 445)
    cards = [
        (24, 115, 'UNIT', ['Business rules and transitions', 'Fast, isolated feedback']),
        (624, 115, 'INTEGRATION', ['Owner database + contracts', 'Transactions and persistence']),
        (24, 282, 'SYSTEM', ['Real APIs + RabbitMQ + identity', 'Reservation and compensation']),
        (624, 282, 'RELEASE GATES', ['Security + images + migration', 'Readiness and smoke evidence'])]
    for x, y, title, lines in cards:
        f.card(x, y, 552, 118, title, lines, size=22)
    f.arrow([(576, 174), (624, 174)])
    f.arrow([(900, 233), (900, 258), (300, 258), (300, 282)])
    f.arrow([(576, 341), (624, 341)], PURPLE)
    f.text(24, 432, 'Cross-cutting checks: access control, duplicate delivery, dependency outage and retained evidence.', size=20)
    f.save()


def pipeline():
    f = Figure('ci-pipeline', 'Change-aware CI and verified release',
        'Explicit dependencies | failures block dependants | only declared skips are accepted', 850)
    f.rect(16, 96, 770, 720, '#f8fafc', BLUE, dash=True)
    f.card(36, 120, 730, 100, '01 CHANGE PLAN',
        ['Changed paths → affected services, selected checks and RUN / SKIP reasons'], size=20)
    f.card(36, 270, 340, 110, '02 QUALITY', ['Formatting + static analysis', 'API and deployment contracts'], size=20)
    f.card(426, 270, 340, 110, 'PARALLEL TRACKS', ['Selected security + audits', 'Documentation integrity'], PURPLE, '#f4f0fa', size=20)
    f.arrow([(206, 220), (206, 270)])
    f.arrow([(596, 220), (596, 270)], PURPLE)
    f.card(36, 435, 340, 135, '03 SERVICE MATRIX', ['Validate', 'Unit + integration → JAR', 'At most three services at once'], size=20)
    f.arrow([(206, 380), (206, 435)])
    f.card(36, 650, 340, 135, '04 CONTAINER VERIFICATION', ['Verify downloaded JAR hash', 'Build that runtime artifact', 'Trivy scan; no PR publication'], size=20)
    f.arrow([(206, 570), (206, 650)])
    f.card(426, 650, 340, 135, '05 SYSTEM ACCEPTANCE', ['Selected API + browser checks', 'Backend cooperation fixture', 'Only after required builds pass'], size=20)
    f.arrow([(376, 718), (426, 718)])
    f.card(426, 435, 340, 135, 'READY TO MERGE', ['Always-run gate', 'Reject errors / cancellations', 'Reject unexpected skipped jobs'], GREEN, '#edf8f3', size=20)
    f.arrow([(596, 650), (596, 570)], GREEN)
    f.arrow([(596, 380), (596, 435)], PURPLE)
    f.card(834, 120, 342, 135, 'MANUAL TRUSTED RELEASE', ['All nine service pipelines', 'Full quality + system checks', 'Full applicable security checks'], AMBER, '#fff7e8', size=20)
    f.card(834, 320, 342, 135, 'BUILD AND SCAN', ['Checksummed JAR artifacts', 'SBOM + provenance', 'One revision; nine digests'], AMBER, '#fff7e8', size=20)
    f.arrow([(1005, 255), (1005, 320)], AMBER)
    f.card(834, 520, 342, 110, 'STAGING', ['Migrate → rollout → readiness', 'HTTPS + identity + smoke'], AMBER, '#fff7e8', size=20)
    f.arrow([(1005, 455), (1005, 520)], AMBER)
    f.card(834, 705, 342, 110, 'PRODUCTION', ['Promote identical digests', 'Configured environment gate'], GREEN, '#edf8f3', size=20)
    f.arrow([(1005, 630), (1005, 705)], GREEN)
    f.save()


def cloud():
    f = Figure('azure-deployment', 'Proposed Azure deployment',
        'Selected cloud target | not provisioned | private data and operator access', 710)
    f.card(24, 118, 350, 110, 'PUBLIC HTTPS ENTRY', ['Browser → TLS ingress', 'Application and login only'], size=21)
    f.card(424, 118, 350, 110, 'RELEASE ARTIFACTS', ['GHCR: verified image digests', 'Same revision promoted'], size=21)
    f.card(824, 118, 350, 110, 'SECRETS AND ADMIN', ['Key Vault: workload identity', 'Admin GUIs stay private'], GREEN, '#edf8f3', size=21)
    f.card(24, 323, 350, 140, 'IDENTITY ON AKS', ['Keycloak: HTTPS OIDC issuer', 'External GitHub broker', 'Separate identity database'], GREEN, '#edf8f3', size=21)
    f.card(424, 323, 350, 140, 'APPLICATIONS ON AKS', ['Frontend + API Gateway', 'Domain APIs + Synchronizer', 'Bounded workers; HTTP HPA'], size=21)
    f.card(824, 323, 350, 140, 'ISOLATED COMPUTE', ['Ollama: private AI inference', 'KEDA: terminating PDF Jobs', 'Confirmation → Orders API'], AMBER, '#fff7e8', size=21)
    f.arrow([(199, 228), (199, 274), (599, 274), (599, 323)])
    f.arrow([(599, 228), (599, 323)])
    f.arrow([(999, 228), (999, 274), (706, 274), (706, 323)], GREEN)
    f.arrow([(424, 392), (374, 392)], GREEN)
    f.arrow([(774, 392), (824, 392)], AMBER, True)
    f.card(24, 563, 350, 110, 'RELATIONAL OWNERS', ['Azure PostgreSQL + HA plan', 'Separate databases and roles'], GREEN, '#edf8f3', size=21)
    f.card(424, 563, 350, 110, 'STATEFUL CLUSTERS', ['Native MongoDB replica set', 'RabbitMQ: 3-node quorum'], GREEN, '#edf8f3', size=21)
    f.card(824, 563, 350, 110, 'OPERATIONS', ['Prometheus / Grafana / Alloy', 'Loki → private Blob Storage'], PURPLE, '#f4f0fa', size=21)
    f.arrow([(199, 463), (199, 563)], GREEN)
    f.arrow([(524, 463), (524, 511), (282, 511), (282, 563)], GREEN)
    f.arrow([(599, 463), (599, 563)], GREEN)
    f.arrow([(706, 463), (706, 511), (999, 511), (999, 563)], PURPLE)
    f.text(24, 700, 'Replicas need separate nodes/zones and tested backups. The local single-node demo does not establish cloud HA.', size=19)
    f.save()


def main():
    for draw in [system, compose, kubernetes, delivery, observability, recovery, purchase, testing, pipeline, cloud]:
        draw()
    print('Rendered ten architecture and operational SVG models.')


if __name__ == '__main__':
    main()
