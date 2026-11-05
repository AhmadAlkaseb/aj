"""Render the four editable diagrams locally with a pinned PlantUML version."""
import subprocess
import urllib.request
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
jar=ROOT/'test-results/tools/plantuml-1.2025.2.jar'
jar.parent.mkdir(parents=True,exist_ok=True)
if not jar.exists():
    with urllib.request.urlopen('https://repo.maven.apache.org/maven2/net/sourceforge/plantuml/plantuml/1.2025.2/plantuml-1.2025.2.jar',timeout=60) as response:jar.write_bytes(response.read())
subprocess.run(['java','-jar',str(jar),'-charset','UTF-8','-tsvg','-nometadata',*[str(p) for p in (ROOT/'documentation/application').glob('*.puml')]],check=True)
