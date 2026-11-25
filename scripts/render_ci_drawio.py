"""Figure 10: one real change-aware CI path, styled after the supplied draw.io example."""
from pathlib import Path
import xml.etree.ElementTree as E

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'documentation/report-figures'


def main():
    reference = E.parse(OUT / 'microservices-scaling (1).drawio')
    docker_style = next(c.get('style') for c in reference.findall('.//mxCell')
                        if c.get('id') == 'eP0o5e_dgutJD-ohwvqB-94')
    file = E.Element('mxfile', host='app.diagrams.net')
    diagram = E.SubElement(file, 'diagram', id='frontend-ci-example', name='Frontend CI — exam overview')
    model = E.SubElement(diagram, 'mxGraphModel', page='0', background='#ffffff')
    root = E.SubElement(model, 'root')
    E.SubElement(root, 'mxCell', id='0')
    E.SubElement(root, 'mxCell', id='1', parent='0')
    text = 'text;html=1;whiteSpace=wrap;align=center;verticalAlign=middle;fontFamily=Helvetica;fontSize=21;fontColor=#1f2937;'

    def box(id, label, x, y, w, h, style, parent='1'):
        cell = E.SubElement(root, 'mxCell', id=id, value=label, style=style, vertex='1', parent=parent)
        E.SubElement(cell, 'mxGeometry', x=str(x), y=str(y), width=str(w), height=str(h), **{'as': 'geometry'})
        return cell

    box('title', 'Continuous integration — one frontend change', 170, 5, 1200, 55, text + 'fontSize=34;')
    box('subtitle', 'Example: app.js changes in a pull request · ci.yaml selects the required checks', 170, 65, 1200, 34, text)
    box('runner', '', 30, 125, 1480, 670,
        'rounded=1;arcSize=8;container=1;pointerEvents=0;fillColor=none;strokeColor=#326ce5;strokeWidth=2;dashed=1;dashPattern=8 5;')
    box('runner-label', 'GitHub Actions · isolated Linux runners', 65, 133, 650, 34, text + 'align=left;fontSize=20;fontColor=#326ce5;fontStyle=1;')

    def stage(id, heading, detail, x, y, w=200, h=100, color='#333333', fill='#ffffff'):
        label = f'<b>{heading}</b><br><font style="font-size:19px">{detail}</font>'
        return box(id, label, x, y, w, h,
                   f'rounded=1;arcSize=7;whiteSpace=wrap;html=1;align=center;verticalAlign=middle;spacing=9;fontFamily=Helvetica;fontSize=23;strokeWidth=1.5;strokeColor={color};fillColor={fill};fontColor=#1f2937;')

    stage('trigger', 'Pull request', 'app.js modified', 70, 195, 180)
    stage('plan', '01 · Plan', 'Select frontend<br>Explain RUN / SKIP', 320, 195)
    stage('prettier', '02a · Prettier', 'Formatting must pass', 590, 195)
    stage('quality', '02b · Quality', 'ESLint JavaScript<br>Other linters skipped', 860, 195, 220)
    stage('validate', '03 · Frontend service', 'Validate Maven module', 1160, 195, 270)
    stage('unit', 'Unit tests', 'Surefire + JaCoCo', 1170, 380, 250, 75)
    stage('integration', 'Integration tests', 'Failsafe + JaCoCo', 1170, 500, 250, 75)
    stage('package', 'Verified JAR', 'Both test jobs pass<br>Package + checksum', 890, 430)
    stage('container', '04 · Container', 'Build · Trivy scan<br>CycloneDX SBOM', 610, 430, 210, 100, '#cf8300')
    box('docker-logo', '', 683, 370, 68, 44, docker_style)
    stage('browser', '05 · Browser', 'Purchase · roles · OIDC', 340, 430)
    stage('ready', 'Ready to merge', 'All required jobs pass', 70, 430, 200, 100, '#247338', '#f0f8f2')
    stage('security', '06 · Security — runs in parallel', 'Trivy secrets + Gitleaks history scan', 330, 650, 500, 85, '#326ce5')
    box('parallel-note', 'Independent security results also reach the final gate.', 880, 650, 540, 70, text + 'fontSize=20;')
    box('policy', '<b>Failure policy:</b> a failed check blocks dependent jobs; the final gate turns red.<br>Only skips declared by the change plan are accepted. Merge protection must require “Ready to merge”.',
        65, 820, 1400, 65, text + 'fontSize=20;')

    def edge(id, source, target, label='', ports='', waypoints=(), dashed=False):
        cell = E.SubElement(root, 'mxCell', id=id, source=source, target=target, value=label, edge='1', parent='1',
            style='edgeStyle=orthogonalEdgeStyle;rounded=0;html=1;endArrow=block;strokeColor=#333333;strokeWidth=1.5;fontFamily=Helvetica;fontSize=18;labelBackgroundColor=#ffffff;'
                  + ('dashed=1;strokeColor=#326ce5;jumpStyle=arc;jumpSize=12;' if dashed else '') + ports)
        geometry = E.SubElement(cell, 'mxGeometry', relative='1', **{'as': 'geometry'})
        if waypoints:
            points = E.SubElement(geometry, 'Array', **{'as': 'points'})
            for x, y in waypoints:
                E.SubElement(points, 'mxPoint', x=str(x), y=str(y))

    right = 'exitX=1;exitY=0.5;entryX=0;entryY=0.5;'
    left = 'exitX=0;exitY=0.5;entryX=1;entryY=0.5;'
    edge('pr-plan', 'trigger', 'plan', ports=right)
    edge('plan-format', 'plan', 'prettier', ports=right)
    edge('format-quality', 'prettier', 'quality', ports=right)
    edge('quality-validate', 'quality', 'validate', ports=right)
    edge('validate-unit', 'validate', 'unit', ports='exitX=0.3;exitY=1;entryX=0.5;entryY=0;')
    edge('validate-integration', 'validate', 'integration', ports='exitX=1;exitY=0.5;entryX=1;entryY=0.5;',
         waypoints=[(1465, 245), (1465, 537.5)])
    edge('unit-package', 'unit', 'package', ports='exitX=0;exitY=0.5;entryX=1;entryY=0.2;', waypoints=[(1130, 417.5), (1130, 450)])
    edge('integration-package', 'integration', 'package', ports='exitX=0;exitY=0.5;entryX=1;entryY=0.8;', waypoints=[(1130, 537.5), (1130, 510)])
    edge('jar-container', 'package', 'container', ports=left)
    edge('container-browser', 'container', 'browser', ports=left)
    edge('browser-ready', 'browser', 'ready', ports=left)
    edge('plan-security', 'plan', 'security', ports='exitX=0.2;exitY=1;entryX=0;entryY=0.5;',
         waypoints=[(303, 330), (303, 692.5)], dashed=True)
    edge('security-ready', 'security', 'ready', ports='exitX=0;exitY=0.8;entryX=0.5;entryY=1;',
         waypoints=[(170, 718)], dashed=True)

    # Real containment keeps the boundary separate from the workflow geometry.
    outside = {'title', 'subtitle', 'runner', 'policy'}
    for cell in root.findall('mxCell'):
        if cell.get('vertex') == '1' and cell.get('id') not in outside:
            geometry = cell.find('mxGeometry')
            cell.set('parent', 'runner')
            geometry.set('x', str(float(geometry.get('x')) - 30))
            geometry.set('y', str(float(geometry.get('y')) - 125))
    E.indent(file)
    E.ElementTree(file).write(OUT / 'ci-pipeline.drawio', encoding='utf-8', xml_declaration=True)


if __name__ == '__main__':
    main()
