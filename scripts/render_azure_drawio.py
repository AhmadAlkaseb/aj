"""Figure 12: application hosting separated from externally operated managed services."""
import base64
import json
from pathlib import Path
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'documentation/report-figures'
ASSETS = OUT / 'assets'


def main():
    reference = E.parse(OUT / 'microservices-scaling (1).drawio')
    styles = {c.get('id'): c.get('style', '') for c in reference.findall('.//mxCell')}
    file = E.Element('mxfile', host='app.diagrams.net')
    diagram = E.SubElement(file, 'diagram', id='external-services', name='External hosting — managed services')
    model = E.SubElement(diagram, 'mxGraphModel', page='0', background='#ffffff')
    root = E.SubElement(model, 'root')
    E.SubElement(root, 'mxCell', id='0')
    E.SubElement(root, 'mxCell', id='1', parent='0')
    text = 'text;html=1;whiteSpace=wrap;align=center;verticalAlign=middle;fontFamily=Helvetica;fontSize=22;fontColor=#243247;'

    def box(id, value, x, y, w, h, style, parent='1'):
        c = E.SubElement(root, 'mxCell', id=id, value=value, style=style, vertex='1', parent=parent)
        E.SubElement(c, 'mxGeometry', x=str(x), y=str(y), width=str(w), height=str(h), **{'as': 'geometry'})
        return c

    def image_style(name):
        data = base64.b64encode((ASSETS / (name + '.svg')).read_bytes()).decode()
        return 'shape=image;html=1;aspect=fixed;imageAspect=0;image=data:image/svg+xml,' + data + ';'

    box('title', 'External hosting — your application, managed services', 30, 0, 1670, 60, text + 'fontSize=35;fontStyle=1;')
    box('subtitle', 'Providers can differ from the application host · examples in a proposed deployment', 70, 68, 1590, 40, text + 'fontSize=23;')

    # Independent provider cards deliberately sit outside the application's hosting boundary.
    box('host', '', 590, 145, 550, 740,
        'rounded=1;arcSize=8;container=1;pointerEvents=0;fillColor=#F1F6FE;strokeColor=#326CE5;strokeWidth=2.5;dashed=1;dashPattern=8 5;')
    box('aks-logo', '', 35, 25, 68, 60,
        'image;aspect=fixed;html=1;points=[];image=img/lib/azure2/compute/Kubernetes_Services.svg;', 'host')
    box('host-title', '<b>APPLICATION HOST</b><br>Azure AKS — example', 115, 22, 405, 70,
        text + 'fontColor=#2256B0;fontSize=24;', 'host')

    def module(id, title, detail, x, y, w, h, fill, color='#243247'):
        box(id, f'<b>{title}</b><br><font style="font-size:20px">{detail}</font>', x-590, y-145, w, h,
            f'rounded=1;arcSize=9;html=1;whiteSpace=wrap;align=center;verticalAlign=middle;spacing=10;fontFamily=Helvetica;fontSize=24;fillColor={fill};strokeColor=none;fontColor={color};', 'host')

    module('frontend', 'Frontend + API Gateway', 'Public HTTPS entry / OIDC client', 650, 265, 430, 90, '#DCEAFF')
    module('alloy', 'Alloy collector', 'Scrape app metrics + forward logs', 650, 405, 430, 90, '#D9F3ED')
    module('apis', 'Business APIs + read model', 'Users · Products · Orders · Payments<br>Product View + Synchronizer', 650, 550, 430, 115, '#DCEAFF')
    module('jobs', 'KEDA PDF Jobs', 'Orders PDF API → ACK', 650, 730, 235, 85, '#FFE8BA')
    module('config', 'Settings', 'URLs / keys', 915, 730, 165, 85, '#334155', '#FFFFFF')
    box('host-note', 'Only app containers, Jobs and a collector.<br>No local database, broker or GPU server.', 30, 680, 490, 50,
        text + 'fontSize=19;fontColor=#2256B0;', 'host')

    def provider(id, title, detail, category, x, y, fill, color, logo):
        box(id, '', x, y, 380, 180,
            f'rounded=1;arcSize=10;container=1;pointerEvents=0;html=1;fillColor={fill};strokeColor={color};strokeWidth=2;')
        box(id+'-logo', '', 20, 62, 68, 68, logo, id)
        box(id+'-category', category, 115, 17, 245, 25,
            text + f'align=left;fontSize=16;fontStyle=1;fontColor={color};', id)
        box(id+'-title', title, 115, 49, 245, 48,
            text + f'align=left;fontStyle=1;fontSize=25;fontColor={color};', id)
        box(id+'-detail', detail, 115, 102, 245, 65,
            text + 'align=left;fontSize=20;', id)

    provider('postgres', 'PostgreSQL', 'e.g. Azure PostgreSQL<br>Four owner DBs / roles', 'MANAGED DATABASE', 30, 180,
             '#EAF3FC', '#336791', image_style('postgresql'))
    provider('mongo', 'MongoDB Atlas', 'Managed MongoDB<br>Catalogue read model', 'MANAGED DATABASE', 30, 410,
             '#E9F7ED', '#16834A', image_style('mongodb'))
    provider('rabbit', 'CloudAMQP', 'Managed RabbitMQ<br>Events + PDF work queue', 'MANAGED MESSAGE BROKER', 30, 640,
             '#FFF0E1', '#D85D00', image_style('rabbitmq'))
    provider('identity', 'Cloud-IAM', 'Managed Keycloak<br>OIDC login / tokens', 'MANAGED IDENTITY', 1320, 180,
             '#E7F5FA', '#087B9F', image_style('keycloak'))
    provider('monitoring', 'Grafana Cloud', 'Hosted metrics + logs<br>Dashboards / alerts', 'HOSTED OBSERVABILITY', 1320, 410,
             '#FFF5E4', '#B46A00', styles['fplhNtZ_AOYMdlBqhHf_-24'])
    provider('ai', 'Ollama Cloud', 'Hosted inference API<br>No GPU on the app host', 'HOSTED AI INFERENCE', 1320, 640,
             '#F0EBFC', '#6950A1', json.loads((ASSETS/'ollama-style.json').read_text())['style'])

    def edge(id, source, target, label, ports, points=(), color='#52637A', dashed=False):
        c = E.SubElement(root, 'mxCell', id=id, value=label, source=source, target=target, edge='1', parent='1',
            style=f'edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=block;strokeWidth=2;strokeColor={color};fontColor={color};fontFamily=Helvetica;fontSize=20;labelBackgroundColor=#ffffff;' + ('dashed=1;' if dashed else '') + ports)
        geometry = E.SubElement(c, 'mxGeometry', relative='1', **{'as': 'geometry'})
        if points:
            array = E.SubElement(geometry, 'Array', **{'as': 'points'})
            for x, y in points:
                E.SubElement(array, 'mxPoint', x=str(x), y=str(y))

    edge('sql', 'apis', 'postgres', 'SQL / TLS', 'exitX=0;exitY=0.2;entryX=1;entryY=0.5;',
         [(510, 573), (510, 270)], '#336791')
    edge('catalogue', 'apis', 'mongo', 'MongoDB / TLS', 'exitX=0;exitY=0.55;entryX=1;entryY=0.5;',
         [(465, 613.25), (465, 500)], '#16834A')
    edge('publish', 'apis', 'rabbit', 'AMQPS', 'exitX=0;exitY=0.9;entryX=1;entryY=0.3;',
         [(550, 653.5), (550, 694)], '#D85D00', True)
    edge('consume', 'rabbit', 'jobs', 'Work queue', 'exitX=1;exitY=0.8;entryX=0;entryY=0.55;', color='#D85D00', dashed=True)
    edge('oidc', 'frontend', 'identity', 'OIDC / HTTPS', 'exitX=1;exitY=0.5;entryX=0;entryY=0.5;', color='#087B9F')
    edge('telemetry', 'alloy', 'monitoring', 'HTTPS telemetry', 'exitX=1;exitY=0.5;entryX=0;entryY=0.5;', color='#B46A00')
    edge('inference', 'apis', 'ai', 'HTTPS + API key', 'exitX=1;exitY=0.55;entryX=0;entryY=0.5;',
         [(1210, 613.25), (1210, 730)], '#6950A1')

    box('footer', '<b>Less setup to operate:</b> providers run databases, messaging, identity, monitoring and AI.<br>Plan: configure TLS endpoints, credentials and adapters; validate migrations, compatibility, latency and service limits.',
        35, 930, 1660, 75, text + 'fontSize=21;')
    E.indent(file)
    E.ElementTree(file).write(OUT/'azure-deployment.drawio', encoding='utf-8', xml_declaration=True)


if __name__ == '__main__':
    main()
