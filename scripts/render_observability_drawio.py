"""Figure 11: declared AJ metrics/log paths in the supplied reference's draw.io style."""
from pathlib import Path
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'documentation/report-figures'


def main():
    reference = E.parse(OUT / 'microservices-scaling (1).drawio')
    styles = {c.get('id'): c.get('style', '') for c in reference.findall('.//mxCell')}
    document = E.Element('mxfile', host='app.diagrams.net')
    diagram = E.SubElement(document, 'diagram', id='aj-observability', name='Metrics and logs — exam overview')
    model = E.SubElement(diagram, 'mxGraphModel', page='0', background='#ffffff')
    root = E.SubElement(model, 'root')
    E.SubElement(root, 'mxCell', id='0')
    E.SubElement(root, 'mxCell', id='1', parent='0')
    text = 'text;html=1;whiteSpace=wrap;align=center;verticalAlign=middle;fontFamily=Helvetica;fontSize=22;fontColor=#1f2937;'

    def box(id, value, x, y, w, h, style):
        cell = E.SubElement(root, 'mxCell', id=id, value=value, style=style, vertex='1', parent='1')
        E.SubElement(cell, 'mxGeometry', x=str(x), y=str(y), width=str(w), height=str(h), **{'as': 'geometry'})
        return cell

    box('title', 'Observability — metrics and application logs', 100, 5, 1400, 60, text + 'fontSize=34;')
    box('subtitle', 'The same monitoring design in Compose and Kubernetes · separate runtime instances', 100, 72, 1400, 35, text)

    def frame(id, label, x, y, w, h, color):
        box(id, '', x, y, w, h, f'rounded=1;arcSize=8;container=1;pointerEvents=0;fillColor=none;strokeColor={color};strokeWidth=2;dashed=1;dashPattern=8 5;')
        box(id + '-label', label, x+25, y+12, w-50, 34, text + f'fontStyle=1;fontColor={color};align=left;')

    frame('metrics-lane', 'METRICS · Prometheus pulls measurements', 30, 140, 1080, 405, '#326ce5')
    frame('logs-lane', 'LOGS · Alloy forwards application records', 30, 590, 1080, 255, '#da8300')
    frame('dashboards', 'ONE OPERATOR VIEW', 1220, 140, 390, 705, '#247338')

    def node(id, heading, detail, x, y, w=310, h=80, dashed=False):
        return box(id, f'<b>{heading}</b><br><font style="font-size:20px">{detail}</font>', x, y, w, h,
                   'rounded=1;arcSize=7;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;spacing=8;fontFamily=Helvetica;fontSize=23;strokeWidth=1.5;strokeColor=#333333;fillColor=#ffffff;' + ('dashed=1;' if dashed else ''))

    node('apps', 'Application metrics', 'HTTP · outbox · projection lag', 90, 205)
    node('rabbit', 'RabbitMQ metrics', 'Queue depth · publish · ACK', 90, 320)
    node('kube', 'kube-state-metrics', 'Kubernetes only: pods / HPA / Jobs', 90, 435, 350, 80, True)
    box('prometheus', '', 850, 265, 100, 85, styles['9Pa7ic3lTRVfPU3D9U-_-7'])
    box('prometheus-name', 'Prometheus', 790, 360, 220, 35, text + 'fontStyle=1;fontSize=25;')
    box('prometheus-note', 'Time-series storage<br>Alert rules', 770, 405, 260, 75, text)

    node('app-logs', 'Application logs', 'Structured JSON · service labels', 90, 685, 310, 85)
    node('alloy', 'Alloy', 'Collect · forward', 490, 685, 240, 85)
    box('loki', '', 865, 675, 80, 80, styles['9Pa7ic3lTRVfPU3D9U-_-46'])
    box('loki-name', 'Loki', 810, 775, 190, 35, text + 'fontStyle=1;fontSize=25;')

    box('grafana', '', 1360, 355, 110, 100, styles['fplhNtZ_AOYMdlBqhHf_-24'])
    box('grafana-name', 'Grafana', 1280, 470, 270, 40, text + 'fontStyle=1;fontSize=27;')
    box('grafana-note', '<b>6 provisioned dashboards</b><br>Operations · HTTP · queues<br>Logs · Kubernetes<br>Control Center / GUI links', 1255, 550, 320, 140, text)
    box('grafana-query-note', 'Queries both data sources', 1255, 735, 320, 50, text + 'fontSize=20;')

    def edge(id, source, target, label, ports='', points=(), dashed=False):
        cell = E.SubElement(root, 'mxCell', id=id, value=label, source=source, target=target, edge='1', parent='1',
            style='edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=block;strokeColor=#333333;strokeWidth=1.5;fontFamily=Helvetica;fontSize=20;labelBackgroundColor=#ffffff;' + ('dashed=1;' if dashed else '') + ports)
        geometry = E.SubElement(cell, 'mxGeometry', relative='1', **{'as': 'geometry'})
        if points:
            array = E.SubElement(geometry, 'Array', **{'as': 'points'})
            for x, y in points:
                E.SubElement(array, 'mxPoint', x=str(x), y=str(y))

    edge('pull-apps', 'prometheus', 'apps', 'Scrape /metrics · :7000',
         'exitX=0;exitY=0.2;entryX=1;entryY=0.5;', [(700, 282), (700, 245)])
    edge('pull-rabbit', 'prometheus', 'rabbit', 'Scrape :15692',
         'exitX=0;exitY=0.6;entryX=1;entryY=0.5;', [(650, 316), (650, 360)])
    edge('pull-kube', 'prometheus', 'kube', 'Scrape :8080',
         'exitX=0;exitY=0.9;entryX=1;entryY=0.5;', [(700, 341.5), (700, 475)], True)
    edge('collect-logs', 'app-logs', 'alloy', 'Collect', 'exitX=1;exitY=0.5;entryX=0;entryY=0.5;')
    edge('push-logs', 'alloy', 'loki', 'Push :3100', 'exitX=1;exitY=0.5;entryX=0;entryY=0.5;')
    edge('promql', 'grafana', 'prometheus', 'PromQL',
         'exitX=0;exitY=0.2;entryX=1;entryY=0.5;', [(1160, 375), (1160, 307.5)])
    edge('logql', 'grafana', 'loki', 'LogQL',
         'exitX=0;exitY=0.8;entryX=1;entryY=0.5;', [(1160, 435), (1160, 715)])

    box('footer', 'Arrows show scrape requests, log forwarding and Grafana queries; dashed arrows = Kubernetes-only source.<br>Business correlation IDs connect HTTP and broker records. Database logs and sensitive payloads are excluded.',
        40, 880, 1560, 70, text + 'fontSize=20;')
    # Preserve true containment for each lane; edge waypoints remain in page coordinates.
    for cell in root.findall('mxCell'):
        if cell.get('vertex') != '1' or cell.get('id') in {'title', 'subtitle', 'footer', 'metrics-lane', 'logs-lane', 'dashboards'}:
            continue
        geometry = cell.find('mxGeometry')
        x, y = float(geometry.get('x')), float(geometry.get('y'))
        parent, px, py = ('dashboards', 1220, 140) if x >= 1220 else ('logs-lane', 30, 590) if y >= 590 else ('metrics-lane', 30, 140)
        cell.set('parent', parent)
        geometry.set('x', str(x-px))
        geometry.set('y', str(y-py))
    E.indent(document)
    E.ElementTree(document).write(OUT / 'observability.drawio', encoding='utf-8', xml_declaration=True)


if __name__ == '__main__':
    main()
