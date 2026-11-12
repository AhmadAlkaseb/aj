"""Build the two editable assignment presentations and printable slide previews.

Install scripts/requirements-report.txt. Assets are rendered from repository SVGs;
slides, speaker notes and previews use only local content and free tooling.
"""
import html
import json
import os
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'test-results/tools/presentation-deps'))
sys.path.insert(0, str(ROOT / 'test-results/tools/python-deps'))
from pptx import Presentation
from pptx.dml.color import RGBColor
from pptx.util import Inches, Pt
from playwright.sync_api import sync_playwright

OUT = ROOT / 'documentation/submission'
ASSETS = ROOT / 'test-results/submission-assets'
REPO = 'https://github.com/AhmadAlkaseb/aj'
AUTHORS = 'Ahmad Alkaseb · Jeppe Koch · Asger Bergøe · Laith Alkaseb'
NAVY = '172B4D'
BLUE = '2563A6'
GREEN = '187A69'

DECKS = {
    'mandatory-assignment-1': ('Mandatory Assignment 1', [
        ('AJ Motors', ['Distributed car sales · architecture and delivery strategy', AUTHORS, REPO], None,
         'Current design presentation prepared from the implemented repository. Do not claim it documents the original classroom presentation.'),
        ('Problem and acceptance criteria', ['Coordinate customer, vehicle, order and manual payment ownership.', 'One car must not be reserved by two buyers.', 'Frontend consumes GraphQL catalogue queries and REST commands.', 'Latency, durability and access boundaries need explicit tests.'], None,
         'Report 1.1–1.2. Manual payment records an outcome; no money transfer or Stripe integration.'),
        ('System architecture', ['Five domain APIs; synchronizer and confirmation processor are additional workers.'], 'architecture',
         'Report 2.1–2.3. Databases, broker, frontend and gateway are not counted among the five domain microservices.'),
        ('Why these technologies?', ['Java 17/Javalin/Maven: consistent backend and build tooling.', 'PostgreSQL: owner constraints and transactions; MongoDB: catalogue snapshots.', 'RabbitMQ: asynchronous delivery; REST + GraphQL: commands and queries.', 'Compose locally; kind, Calico, HPA and KEDA for the production-like demo.'], None,
         'Report 1.3. Technology choice is a trade-off, not proof of scalability.'),
        ('Communication and patterns', ['AMQP coordinates reservation, payment outcomes and catalogue snapshots.', 'CQRS, tombstones/snapshots, idempotence and selected commutative handling.', 'Saga compensation, deadlines and explicit manual review.', 'Outbox/inbox retain work and deduplicate selected redeliveries.'], 'purchase-saga',
         'Report 2.3–2.4. At-least-once delivery is not universal exactly-once processing.'),
        ('Balanced Scrum-inspired work model', ['Four workstreams: identity; catalogue/AI; commerce; operations.', 'Each participant owns one stream and reviews another.', 'Short planning cycles, shared board, brief standups and acceptance review.', 'Allocation/reflection drafts need participant confirmation; no invented attendance.'], None,
         'Report 5 and 7. This is the adopted balanced model, not measured equal hours or fabricated historical sprint evidence.'),
        ('CI/CD and testing plan', ['Change plan → selected quality → affected services → images → cooperation.', 'Independent security/docs tracks join one strict Ready to merge gate.', 'Unit + integration must pass before JAR packaging.', 'Local results and actual GitHub runs remain different evidence.'], 'ci-pipeline',
         'Report 3.2.2 and 4; CI-CD.md. Native summaries and free/open-source quality/security tools.'),
        ('Cloud, operations and versioning', ['Selected cloud plan: AKS, PostgreSQL, native MongoDB and RabbitMQ.', 'Private identity/AI; KEDA PDF Jobs; secrets and tested recovery.', 'Prometheus/Grafana/Alloy/Loki; Git, Flyway, API v1 and event schema.', 'Cloud resources are planned, not purchased or deployed.'], 'azure-deployment',
         'Report 3.3 and 5.3. Primary references: Microsoft AKS baseline; RabbitMQ confirms; Keycloak; GitHub Actions.'),
        ('Delivery and open evidence', ['Report with included appendices; monorepo and installation procedure.', 'Design and implementation figures are editable repository assets.', 'External GitHub login needs an owned OAuth app and real browser verification.', 'Upload this PowerPoint, report and repository link to itsLearning.'], None,
         'Check documentation/submission/README.md before upload. Actual GitHub runs and participant history must not be inferred from slides.'),
    ]),
    'mandatory-assignment-2': ('Mandatory Assignment 2', [
        ('AJ Motors', ['Working distributed system · implementation and verification', AUTHORS, REPO], None,
         'Current implementation presentation for Assignment 2. Evidence dates remain explicit.'),
        ('What works locally?', ['Catalogue, customer profiles, exclusive reservation and manual payment.', 'Real REST/GraphQL frontend workflow and protected backend ownership.', 'Actual local AI drafts; bounded PDF confirmation work.', 'Selected failure/recovery scenarios and persistent demonstration data.'], None,
         'Report 1, 2 and 4; EVIDENCE.md. Avoid promising production HA or universal fault coverage.'),
        ('Compose development architecture', ['24 named services under aj; persistent owner stores and shared local administration.'], 'docker-compose',
         'Report 3.1. Adminer/Mongo Express have explicit local admin network access. kind control plane is separate.'),
        ('Asynchronous cooperation', ['Order → customer validation → inventory reservation → payment outcome.', 'Failure/cancellation compensates eligible stock.', 'Backend cooperation fixture starts private stores, broker and four owners.', 'No UI or gateway is required for the dedicated cooperation scenario.'], 'purchase-saga',
         'Report 2.3 and 4.4. Durable queues, retained saga state and outbox/inbox recover selected failures.'),
        ('Delivery and failure visibility', ['Outbox: waiting to send; Ready: waiting in broker; Unacked: processing.', 'Publisher confirmation differs from consumer acknowledgement.', 'Correlated metadata links requests, events and logs without payload secrets.'], 'message-delivery',
         'Report 2.4.7. Fast deliveries can occur between metrics scrapes; use correlated logs for observed history.'),
        ('Testing and CI gates', ['Unit, API/database/message integration and backend cooperation are separate.', 'Change-aware CI skips unrelated work; required failures block dependants.', '47 local Python policy/deployment/federation tests passed at review.', 'GitHub-hosted execution remains unverified without account authentication.'], 'testing-strategy',
         'Report 4. Java results are separately dated; no remote run ID is invented.'),
        ('Kubernetes deployment', ['Active demo: TLS ingress, owner PVCs, migration gates, HPA and KEDA Jobs.', 'Standby: Headlamp reads resources while Compose serves the application.', 'Single-node simulation is not host-failure high availability.'], 'kubernetes-deployment',
         'Report 3.2. Previous active-runtime evidence is different from current standby status.'),
        ('Monitoring and GUI portal', ['Grafana Control Center links SQL/Mongo/Kubernetes and all operations tools.', 'Six dashboards; metrics + logs + RabbitMQ delivery status.', 'Local browser checks passed all database GUIs and read-only Headlamp.'], 'observability',
         'Report 3.2.3; GUI checker confirmed nodes readable and Secrets denied. Common local admin is not enterprise SSO.'),
        ('Deployment plan and delivery', ['AKS cloud plan includes identity, AI, stores, broker, jobs and observability.', 'Installation and verification instructions are included in the report.', 'Remaining external inputs: owned OAuth app, real login and authentic contributions.', 'Upload PowerPoint, report and repository link to itsLearning.'], None,
         'Report Appendices B–E and submission README. Actual cloud deployment is not required.'),
    ]),
}


def textbox(slide, x, y, w, h, text, size=22, color=NAVY, bold=False):
    shape = slide.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
    frame = shape.text_frame
    frame.word_wrap = True
    for i, line in enumerate(text.split('\n')):
        paragraph = frame.paragraphs[0] if i == 0 else frame.add_paragraph()
        paragraph.text = line
        paragraph.font.name = 'Aptos'
        paragraph.font.size = Pt(size)
        paragraph.font.bold = bold
        paragraph.font.color.rgb = RGBColor.from_string(color)
        paragraph.space_after = Pt(12)
    return shape


def main():
    OUT.mkdir(exist_ok=True)
    ASSETS.mkdir(parents=True, exist_ok=True)
    result = {}
    with sync_playwright() as p:
        candidates = sorted((Path(os.environ.get('LOCALAPPDATA', '')) / 'ms-playwright').glob('chromium-*/chrome-win*/chrome.exe'), reverse=True)
        browser = p.chromium.launch(**({'executable_path': str(candidates[0])} if candidates else {}))
        page = browser.new_page(viewport={'width': 1600, 'height': 1100}, device_scale_factor=1.5)
        image_paths = {}
        for _, slides in DECKS.values():
            for _, _, figure, _ in slides:
                if not figure or figure in image_paths:
                    continue
                svg = ROOT / 'documentation/report-figures' / (figure + '.svg')
                page.goto(svg.as_uri())
                destination = ASSETS / (figure + '.png')
                page.locator('svg').screenshot(path=str(destination))
                image_paths[figure] = destination
        for slug, (assignment, slides) in DECKS.items():
            deck = Presentation()
            deck.slide_width, deck.slide_height = Inches(13.333), Inches(7.5)
            deck.core_properties.title = 'AJ Motors · ' + assignment
            deck.core_properties.author = AUTHORS
            deck.core_properties.subject = 'Current project review · 12 November 2026'
            sections = []
            for index, (title, points, figure, notes) in enumerate(slides):
                slide = deck.slides.add_slide(deck.slide_layouts[6])
                textbox(slide, .55, .25, 12.2, .3, assignment.upper(), 12, BLUE, True)
                textbox(slide, .55, .72, 12.2, .65, title, 30, NAVY, True)
                if figure:
                    # Wide/short figures occupy the right panel without distorting their aspect.
                    from PIL import Image
                    with Image.open(image_paths[figure]) as img:
                        ratio = img.width / img.height
                    width = 7.5
                    height = width / ratio
                    if height > 4.9:
                        height = 4.9
                        width = height * ratio
                    slide.shapes.add_picture(str(image_paths[figure]), Inches(5.3 + (7.5-width)/2), Inches(1.65 + (4.9-height)/2), width=Inches(width))
                    textbox(slide, .55, 1.72, 4.45, 4.9, '\n'.join('• ' + p for p in points), 20)
                else:
                    textbox(slide, .75, 1.85, 11.8, 4.8, '\n'.join('• ' + p for p in points), 25)
                textbox(slide, .55, 7.05, 11.4, .25, 'AJ MOTORS  /  Technical review · 12 November 2026', 10, '64748B')
                textbox(slide, 12, 7.05, .65, .25, f'{index+1:02}', 10, BLUE)
                slide.notes_slide.notes_text_frame.text = notes + '\nRepository: ' + REPO
                image_tag = f'<img src="../report-figures/{figure}.svg" alt="{html.escape(title)}">' if figure else ''
                sections.append('<section><header>'+html.escape(assignment.upper())+'</header><h1>'+html.escape(title)+'</h1><div class="body '+('with-image' if figure else '')+'"><ul>'+''.join('<li>'+html.escape(point)+'</li>' for point in points)+'</ul>'+image_tag+'</div><footer>AJ MOTORS · 12 November 2026 <span>'+str(index+1).zfill(2)+'</span></footer></section>')
            destination = OUT / (slug + '.pptx')
            deck.save(destination)
            assert len(Presentation(destination).slides) == len(slides)
            preview = OUT / (slug + '.html')
            preview.write_text('<!doctype html><html lang="en"><meta charset="utf-8"><title>AJ Motors · '+assignment+'</title><style>@page{size:13.333in 7.5in;margin:0}*{box-sizing:border-box}body{margin:0;background:#e2e8f0;font-family:Arial,sans-serif;color:#172b4d}section{position:relative;width:1280px;height:720px;background:white;margin:24px auto;padding:28px 52px;page-break-after:always;overflow:hidden}header{font-size:14px;letter-spacing:2px;color:#2563a6;font-weight:bold}h1{font-size:38px;margin:34px 0}.body{display:flex;height:470px;align-items:center}.body ul{font-size:29px;line-height:1.5;padding-left:30px;margin:0}.body li{margin:0 0 22px}.with-image ul{width:38%;font-size:22px;padding-right:30px}.with-image img{width:62%;max-height:470px;object-fit:contain}footer{position:absolute;bottom:24px;left:52px;right:52px;font-size:12px;color:#64748b}footer span{float:right}@media print{body{background:white}section{width:13.333in;height:7.5in;margin:0}}</style>'+''.join(sections)+'</html>', encoding='utf-8')
            page.goto(preview.as_uri())
            page.pdf(path=str(OUT / (slug + '.pdf')), prefer_css_page_size=True, print_background=True)
            result[slug] = {'slides': len(slides), 'editable': destination.name, 'preview': slug + '.pdf'}
        browser.close()
    (OUT / 'presentations.json').write_text(json.dumps(result, indent=2) + '\n', encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
