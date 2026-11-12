"""Native Grafana home dashboard: one entry point for every local administration UI."""
import html
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
HOME = '/d/aj-home'
CATEGORIES = [
    ('PostgreSQL · alle databaser', '#5794f2', [
        ('Users', 'Kunder og inbox/outbox', 'http://localhost:18080/?pgsql=users-db&username=admin&db=users'),
        ('Products', 'Biler, priser og reservationer', 'http://localhost:18080/?pgsql=products-db&username=admin&db=products'),
        ('Orders', 'Ordrer, historik og PDF', 'http://localhost:18080/?pgsql=orders-db&username=admin&db=orders'),
        ('Payments', 'Betalinger og audit', 'http://localhost:18080/?pgsql=payments-db&username=admin&db=payments'),
        ('Keycloak DB', 'Identitetsdatabasen', 'http://localhost:18080/?pgsql=keycloak-db&username=admin&db=keycloak')]),
    ('MongoDB · katalog', '#73bf69', [
        ('Mongo Express', 'Databaser, collections og dokumenter', 'http://localhost:18081/'),
        ('Product View', 'Den afledte læsemodel i Grafana', '/d/aj-platform'),
        ('Databaseoversigt', 'Åbn alle PostgreSQL-servere', 'http://localhost:18080/')]),
    ('Kubernetes · Headlamp', '#b877d9', [
        ('Kubernetes GUI', 'Nodes, pods, workloads, Services og PVC', 'http://localhost:14466/'),
        ('Kubernetes-målinger', 'HPA, replikaer og genstarter i aktiv Kubernetes-kørsel', '/d/aj-kubernetes'),
        ('Kubernetes-projekt', 'Officiel Headlamp-dokumentation', 'https://headlamp.dev/docs/latest/')]),
    ('Beskeder og identitet', '#ffb357', [
        ('RabbitMQ', 'Exchanges, køer, Ready og Unacked', 'http://localhost:15672/'),
        ('Keycloak', 'Brugere, roller og clients', 'http://localhost:8180/admin/'),
        ('Beskedlevering', 'Outbox, publish, confirms og ACK', '/d/aj-messaging')]),
    ('Overvågning og fejlsøgning', '#56d2d9', [
        ('Driftsoverblik', 'HTTP, afhængigheder og køer', '/d/aj-platform'),
        ('HTTP og services', 'Svartider, trafik og fejl', '/d/aj-http'),
        ('Logs · Loki', 'Søg på service, eventId eller correlationId', '/d/aj-logs'),
        ('Prometheus', 'Targets, PromQL og alarmer', 'http://localhost:9090/'),
        ('Grafana Explore', 'Ad hoc-analyse af metrics og logs', '/explore')]),
    ('Udvikling og CI/CD', '#f2495c', [
        ('AJ Motors', 'Åbn programmet', 'http://localhost:3000/'),
        ('Swagger · API GUI', 'Interaktive API-kontrakter', 'http://localhost:3000/swagger/'),
        ('CI · pipeline og resultater', 'Workflows og resultater · nye CI-regler aktiveres ved push', 'https://github.com/AhmadAlkaseb/aj/actions'),
        ('Release og deployment', 'Verificering → staging → produktion', 'https://github.com/AhmadAlkaseb/aj/actions/workflows/release.yaml'),
        ('Open WebUI · valgfri', 'Eksisterende separat AI-værktøj; kræver at det er startet', 'http://localhost:3001/')])]


def text_panel(identifier, title, content, x, y, w, h):
    return {'id': identifier, 'type': 'text', 'title': title, 'transparent': True,
        'gridPos': {'x': x, 'y': y, 'w': w, 'h': h},
        'options': {'mode': 'html', 'content': content}}


def home_dashboard():
    panels = [text_panel(1, '', '<h1>AJ Control Center</h1><p>Alle værktøjer, databaser og pipelines samlet ét sted. '
        'Vælg et værktøj nedenfor. Eksterne GUI’er åbner i en ny fane.</p>'
        '<p><strong>Fælles administrator:</strong> admin · brug samme lokale kodeord i værktøjerne. '
        'Headlamp bruger lokal læseadgang. Kubernetes-workloads kan stå i standby, mens Compose kører.</p>', 0, 0, 24, 4)]
    for index, (title, color, entries) in enumerate(CATEGORIES):
        items = []
        for label, description, url in entries:
            external = url.startswith('http')
            target = ' target="_blank" rel="noopener noreferrer"' if external else ''
            items.append('<p style="margin:0 0 18px 0"><a style="font-size:18px;font-weight:600;color:' + color + '" href="' +
                html.escape(url, quote=True) + '"' + target + '>' + html.escape(label) + ' ↗</a><br>' +
                '<span style="color:#a6adbb">' + html.escape(description) + '</span></p>')
        content = '<div style="border-top:3px solid ' + color + ';padding:18px 12px">' + ''.join(items) + '</div>'
        panels.append(text_panel(index + 2, title, content, (index % 3) * 8, 4 + (index // 3) * 13, 8, 13))
    return {'uid': 'aj-home', 'title': 'AJ · Control Center', 'schemaVersion': 39, 'version': 1,
        'tags': ['AJ Motors', 'home'], 'editable': True, 'refresh': '30s',
        'time': {'from': 'now-30m', 'to': 'now'}, 'panels': panels,
        'links': [{'title': 'Alle dashboards', 'type': 'dashboards', 'tags': ['AJ Motors'], 'asDropdown': True}]}


def add_navigation(dashboard):
    links = dashboard.setdefault('links', [])
    if not any(link.get('url') == HOME for link in links):
        links.insert(0, {'title': '⌂ Control Center', 'type': 'link', 'url': HOME, 'icon': 'home', 'targetBlank': False})
    return dashboard


def main():
    destination = ROOT / 'dashboards'
    destination.mkdir(exist_ok=True)
    (destination / 'aj-home.json').write_text(json.dumps(home_dashboard(), indent=2) + '\n', encoding='utf-8')
    for path in [ROOT / 'dashboard.json', *destination.glob('*.json')]:
        if path.name == 'aj-home.json':
            continue
        dashboard = add_navigation(json.loads(path.read_text(encoding='utf-8')))
        path.write_text(json.dumps(dashboard, indent=2) + '\n', encoding='utf-8')


if __name__ == '__main__':
    main()
