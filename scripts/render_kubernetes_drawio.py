"""Figure 8: AJ deployment in the visual style of the supplied scaling example."""
from pathlib import Path
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'documentation/report-figures'


def main():
    reference = E.parse(OUT / 'microservices-scaling (1).drawio')
    styles = {c.get('id'): c.get('style', '') for c in reference.findall('.//mxCell')}
    document = E.Element('mxfile', host='app.diagrams.net')
    diagram = E.SubElement(document, 'diagram', id='aj-local-kubernetes', name='Local Kubernetes deployment')
    model = E.SubElement(diagram, 'mxGraphModel', page='0', background='#ffffff')
    root = E.SubElement(model, 'root')
    E.SubElement(root, 'mxCell', id='0')
    E.SubElement(root, 'mxCell', id='1', parent='0')

    def box(id, label, x, y, w, h, style):
        cell = E.SubElement(root, 'mxCell', id=id, value=label, style=style, vertex='1', parent='1')
        E.SubElement(cell, 'mxGeometry', x=str(x), y=str(y), width=str(w), height=str(h), **{'as': 'geometry'})
        return cell

    text = 'text;html=1;whiteSpace=wrap;align=center;verticalAlign=middle;fontFamily=Helvetica;fontSize=18;'
    plain = 'rounded=0;whiteSpace=wrap;html=1;fillColor=#ffffff;strokeColor=#333333;fontFamily=Helvetica;fontSize=18;'
    box('title', 'Local Kubernetes deployment', 210, 0, 1040, 70, text + 'fontSize=36;')
    box('cluster', '', 220, 150, 1210, 720, 'rounded=1;arcSize=8;fillColor=none;strokeColor=#326ce5;strokeWidth=2;dashed=1;')
    box('cluster-name', 'Kubernetes Cluster — kind-aj-exam', 365, 92, 820, 45, text + 'fontSize=26;fontStyle=1;')
    box('cluster-icon', '', 195, 95, 100, 100, styles['9Pa7ic3lTRVfPU3D9U-_-18'])
    box('namespace', 'Namespace aj · one Docker-hosted node · ClusterIP services', 415, 160, 850, 30, text)

    def node(id, label, x, y, w=170, h=85):
        return box(id, label, x, y, w, h, plain)

    def icon(id, source, label, x, y):
        box(id, '', x, y, 80, 65, styles[source])
        box(id + '-label', label, x-40, y+67, 160, 45, text)

    node('entry', 'Browser / localhost<br>HTTPS :8443', 10, 410, 180, 80)
    node('frontend', 'Traefik TLS ingress<br>Frontend + API Gateway', 270, 410, 210, 80)
    node('owners', 'Users · Products<br>Orders · Payments<br>Owner API Deployments', 530, 410, 215, 100)
    node('identity', 'Keycloak<br>Private PostgreSQL + PVC', 270, 610, 210, 80)
    node('stores', 'Owner-only stores + PVCs<br>4 PostgreSQL + MongoDB<br>Calico network policies', 530, 635, 215, 100)
    icon('loki', '9Pa7ic3lTRVfPU3D9U-_-46', 'Loki', 590, 205)
    icon('grafana', 'fplhNtZ_AOYMdlBqhHf_-24', 'Grafana', 795, 205)
    icon('prometheus', '9Pa7ic3lTRVfPU3D9U-_-7', 'Prometheus', 1010, 205)
    node('alloy', 'Alloy<br>Collect pod logs', 320, 245, 160, 70)
    icon('rabbit', '9Pa7ic3lTRVfPU3D9U-_-33', 'RabbitMQ + PVC<br>Events / work queue', 825, 420)
    icon('keda', '9Pa7ic3lTRVfPU3D9U-_-24', 'KEDA<br>Queue-driven Jobs', 1240, 255)
    node('worker', 'Confirmation Job<br>0–2 concurrent Jobs<br>Orders PDF API → ACK', 1170, 485, 215, 100)
    node('projection', 'Product Synchronizer<br>→ Product View<br>→ MongoDB read model', 790, 655, 230, 100)
    node('hpa', 'metrics-server → HPA<br>Frontend: 1–2 replicas', 1115, 715, 270, 65)

    def edge(id, source, target, label='', extra='', dashed=False):
        c = E.SubElement(root, 'mxCell', id=id, value=label, source=source, target=target, edge='1', parent='1',
            style='edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=block;strokeColor=#333333;fontSize=15;labelBackgroundColor=#ffffff;' + ('dashed=1;' if dashed else '') + extra)
        E.SubElement(c, 'mxGeometry', relative='1', **{'as': 'geometry'})

    edge('tls', 'entry', 'frontend')
    edge('http', 'frontend', 'owners', 'HTTP')
    edge('oidc', 'frontend', 'identity', 'OIDC')
    edge('sql', 'owners', 'stores', 'Owner access')
    edge('publish', 'owners', 'rabbit', 'Publish', dashed=True)
    edge('consume', 'rabbit', 'worker', 'Consume / ACK', dashed=True)
    edge('project', 'rabbit', 'projection', 'Events', 'exitX=1;exitY=1;entryX=0.7;entryY=0;', True)
    edge('queue', 'rabbit', 'keda', 'Poll queue depth', 'exitX=1;exitY=0.2;entryX=0;entryY=0.2;', True)
    edge('scale', 'keda', 'worker', 'Create Jobs', dashed=True)
    edge('logs', 'owners', 'alloy', 'Pod logs', 'exitX=0.2;exitY=0;entryX=1;entryY=1;', True)
    edge('ship', 'alloy', 'loki', 'Send logs', dashed=True)
    edge('query-logs', 'grafana', 'loki', 'Query')
    edge('query-metrics', 'grafana', 'prometheus', 'Query')
    edge('scrape', 'prometheus', 'rabbit', 'Scrape :15692', 'exitX=0.5;exitY=1;entryX=1;entryY=0;', True)
    box('monitor-note', 'Prometheus also scrapes APIs and kube-state-metrics.', 930, 195, 470, 10, text + 'fontSize=13;')
    box('footer', 'Solid arrows: HTTP / storage. Dashed: events, telemetry and scaling. SQL init containers run Flyway migrations.<br>Single-node simulation; PVCs survive pod replacement. HPA scales Frontend; KEDA creates confirmation Jobs.', 225, 800, 1195, 60, text + 'fontSize=16;')
    # Nest contained shapes so validation recognises the deliberate cluster boundary.
    for cell in root.findall('mxCell'):
        if cell.get('vertex') == '1' and cell.get('id') not in {'title', 'cluster', 'cluster-name', 'cluster-icon', 'entry'}:
            geometry = cell.find('mxGeometry')
            cell.set('parent', 'cluster')
            geometry.set('x', str(float(geometry.get('x', '0')) - 220))
            geometry.set('y', str(float(geometry.get('y', '0')) - 150))
    E.indent(document)
    E.ElementTree(document).write(OUT / 'kubernetes-deployment.drawio', encoding='utf-8', xml_declaration=True)


if __name__ == '__main__':
    main()
