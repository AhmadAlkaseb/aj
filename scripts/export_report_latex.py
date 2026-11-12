"""Pandoc + Tectonic report export with vector figures and an unnumbered cover."""
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT/'test-results/tools/python-deps'))
from pypdf import PdfReader
from playwright.sync_api import sync_playwright


def executable(name):
    installed=shutil.which(name)
    if installed:return installed
    portable=list((ROOT/'test-results/tools/report'/name).rglob(name+'.exe'))
    if portable:return str(portable[0])
    raise SystemExit(name+' missing. Run scripts/setup_report.ps1 first.')


def run(args,**kwargs):
    args=[str(x) for x in args]
    if args[0] in ['pandoc','tectonic']:args[0]=executable(args[0])
    kwargs.setdefault('cwd',ROOT)
    return subprocess.run(args,check=True,**kwargs)


def tex_escape(value):
    return ''.join({'\\':r'\textbackslash{}','&':r'\&','%':r'\%','$':r'\$','#':r'\#','_':r'\_','{':r'\{','}':r'\}'}.get(c,c) for c in value)


def render_assets(source,out):
    assets=out/'assets';assets.mkdir(parents=True,exist_ok=True)
    mermaid=ROOT/'test-results/report-export/mermaid-10.9.3.min.js'
    if not mermaid.exists():
        mermaid.parent.mkdir(parents=True,exist_ok=True)
        with urllib.request.urlopen('https://cdn.jsdelivr.net/npm/mermaid@10.9.3/dist/mermaid.min.js',timeout=45) as response:mermaid.write_bytes(response.read())
    with sync_playwright() as p:
        candidates=sorted((Path(os.environ.get('LOCALAPPDATA',''))/'ms-playwright').glob('chromium-*/chrome-win*/chrome.exe'),reverse=True) if sys.platform=='win32' else []
        browser=p.chromium.launch(**({'executable_path':str(candidates[0])} if candidates else {}))
        page=browser.new_page();page.set_content('<html><body></body></html>');page.add_script_tag(path=str(mermaid))
        page.evaluate("mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:'neutral',flowchart:{htmlLabels:false}})")
        sequence=0
        def vector(svg):
            nonlocal sequence
            sequence+=1
            xml=ET.fromstring(svg);view=xml.attrib.get('viewBox','').replace(',',' ').split()
            width=float(view[2]) if len(view)==4 else float(re.sub('[^0-9.]','',xml.attrib['width']))
            height=float(view[3]) if len(view)==4 else float(re.sub('[^0-9.]','',xml.attrib['height']))
            # A sized SVG produces a cropped vector PDF, rather than a screenshot.
            drawing=browser.new_page()
            drawing.set_content('<html><head><style>@page{margin:0}html,body{margin:0;padding:0}svg{display:block;width:100%;height:100%}</style></head><body>'+svg+'</body></html>')
            drawing.evaluate('async()=>await document.fonts.ready')
            destination=assets/f'figure-{sequence}.pdf'
            drawing.pdf(path=str(destination),width=f'{width}px',height=f'{height}px',margin={'top':'0','bottom':'0','left':'0','right':'0'},print_background=True)
            drawing.close();return destination
        def image(match):
            nonlocal sequence
            caption=match[1];target=match[2].strip('<>')
            path=ROOT/'documentation'/target
            if path.suffix.lower()=='.svg':destination=vector(path.read_text(encoding='utf-8'))
            else:
                sequence+=1;destination=assets/(f'figure-{sequence}'+path.suffix.lower());shutil.copy2(path,destination)
            return figure(destination,caption)
        def figure(path,caption):
            caption=re.sub(r'^Figure\s+\d+\s*[—–.-]\s*','',caption)
            return '\n'+r'\begin{figure}[H]'+'\n'+r'\centering'+'\n'+r'\includegraphics[width=\linewidth,height=0.48\textheight,keepaspectratio]{\detokenize{'+path.relative_to(out).as_posix()+'}}\n'+r'\caption{'+tex_escape(caption)+'}\n'+r'\end{figure}'+'\n'
        source=re.sub(r'^!\[([^\]]+)\]\(([^\n]+)\)\s*$',image,source,flags=re.M)
        def diagram(match):
            rendered=page.evaluate('async text => (await mermaid.render("report-diagram-"+Math.random().toString(36).slice(2),text)).svg',match[1])
            return figure(vector(rendered),'Local Kubernetes deployment' if 'subgraph Cluster' in match[1] else 'Verification and gated release pipeline' if 'Manual main-branch release' in match[1] else 'Proposed Azure deployment')
        source=re.sub(r'```mermaid\n(.*?)\n```',diagram,source,flags=re.S)
        browser.close()
    # The LaTeX figures supply automatic captions/list entries. Keep prose annotations.
    source=re.sub(r'^\*Figure \d+\. (.*?)\*\s*$',lambda m: re.sub(r'^.*?\.\s*','',m[1],count=1)+'\n',source,flags=re.M)
    return source


def main():
    executable('tectonic');executable('pandoc')
    out=ROOT/'test-results/report-latex';out.mkdir(parents=True,exist_ok=True)
    original=(ROOT/'documentation/Report.md').read_text(encoding='utf-8')
    source_characters=len((ROOT/'documentation/Report.md').read_bytes().decode('utf-8'))
    if source_characters>30*2400:
        raise RuntimeError(f'Report source contains {source_characters} characters including whitespace; maximum is 72000.')
    metadata=re.match(r'^---\n(.*?)\n---\n',original,re.S)
    metadata_path=out/'metadata.yaml'
    metadata_path.write_text(metadata[1] if metadata else 'fontsize: 10pt\npapersize: a4\ngeometry: margin=16mm\n',encoding='utf-8')
    source=re.sub(r'^---\n.*?\n---\n','',original,count=1,flags=re.S)
    source=re.sub(r'<!-- PDF layout.*?-->\s*\\thispagestyle\{empty\}\s*\\clearpage\s*\\pagenumbering\{arabic\}\s*','',source,flags=re.S)
    source=render_assets(source,out)
    split=source.index('## List of figures')
    cover=source[:split].strip()
    cover=cover.replace('# AJ Motors','```{=latex}\n'+r'{\Huge\bfseries AJ Motors\par}'+'\n'+r'\vspace{8mm}'+'\n```')
    cover=cover.replace('## Development of Large Systems — Project Report','```{=latex}\n'+r'{\Large Development of Large Systems\par}'+'\n'+r'{\large Project Report\par}'+'\n'+r'\vspace{10mm}'+'\n```')
    cover=re.sub(r'^- (.+)$',lambda m:tex_escape(m[1])+r'\par',cover,flags=re.M)
    cover=run(['pandoc','-f','markdown+gfm_auto_identifiers','-t','latex'],input=cover.encode('utf-8'),stdout=subprocess.PIPE).stdout.decode('utf-8')
    cover=r'\hypersetup{pageanchor=false}'+'\n'+r'\begin{titlepage}'+'\n'+r'\thispagestyle{empty}'+'\n'+r'\centering'+'\n'+r'\vspace*{8mm}'+'\n'+cover+'\n'+r'\end{titlepage}'+'\n'+r'\pagenumbering{arabic}'+'\n'+r'\hypersetup{pageanchor=true}'+'\n'
    body=source[split:]
    body=re.sub(r'## List of figures\n.*?(?=## List of appendices)',lambda _:r'\listoffigures'+'\n\n',body,flags=re.S)
    body=body.replace('## List of appendices',r'\section*{List of appendices}')
    body=re.sub(r'## Table of contents\n.*?(?=\n# 1\.)',lambda _:r'\clearpage'+'\n'+r'\tableofcontents'+'\n'+r'\clearpage'+'\n',body,flags=re.S)
    prepared=out/'report.md';prepared.write_text(cover+'\n'+body,encoding='utf-8')
    tex=out/'Report.tex'
    run(['pandoc',prepared,'-f','markdown+gfm_auto_identifiers','-t','latex','--standalone','--highlight-style=tango',
        '--include-in-header',ROOT/'documentation/report-style.tex','--lua-filter',ROOT/'scripts/report_tables.lua',
        '--metadata-file',metadata_path,'-o',tex])
    # float must precede hyperref/bookmark to avoid duplicate figure destinations.
    latex=tex.read_text(encoding='utf-8').replace(r'\usepackage{float}','',1)
    latex=latex.replace(r'\usepackage{xcolor}',r'\usepackage{float}'+'\n'+r'\usepackage{xcolor}',1)
    tex.write_text(latex,encoding='utf-8')
    run(['tectonic','--keep-logs','--keep-intermediates','--outdir','.',tex.name],cwd=out)
    pdf=out/'Report.pdf';reader=PdfReader(pdf);count=len(reader.pages)
    if count>30:raise RuntimeError(f'LaTeX report is {count} pages; limit is 30. Editable source and logs retained.')
    # The cover is genuinely unnumbered; subsequent pages use logical numbers from one.
    for index,page in enumerate(reader.pages):
        text=page.extract_text() or ''
        if index==0 and re.search(r'\n\s*1\s*$',text):raise RuntimeError('Unexpected page number on the cover')
        if index>0 and not re.search(r'\b'+str(index)+r'\s*$',text):raise RuntimeError('Missing/incorrect printed page number on PDF page '+str(index+1))
    contents={};figure_pages={}
    for extension,target in [('toc',contents),('lof',figure_pages)]:
        text=(out/('Report.'+extension)).read_text(encoding='utf-8')
        for title,number,destination_name in re.findall(r'\\contentsline\s*\{[^}]+\}\{(.*?)\}\{(\d+)\}\{([^}]+)\}',text):
            destination=reader.named_destinations.get(destination_name)
            if destination is None or reader.get_destination_page_number(destination)!=int(number):
                raise RuntimeError('Contents/figure page does not match its PDF destination: '+destination_name)
            target[title]=int(number)
    if not contents or len(figure_pages)!=len(re.findall(r'^\\caption\{',latex,re.M)):
        raise RuntimeError('Contents or figure-list entries are incomplete')
    destination=ROOT/'documentation/Report.pdf';destination.write_bytes(pdf.read_bytes())
    evidence={'engine':'Pandoc + Tectonic (LaTeX)','pages':count,'limit':30,'sourceCharactersIncludingWhitespace':source_characters,'characterLimit':72000,'coverNumbered':False,'firstPrintedPage':1,
        'toc':contents,'figures':figure_pages,'sha256':hashlib.sha256(pdf.read_bytes()).hexdigest(),'sourceSha256':hashlib.sha256(original.encode()).hexdigest(),
        'styleSha256':hashlib.sha256((ROOT/'documentation/report-style.tex').read_bytes()).hexdigest()}
    (ROOT/'documentation/evidence/report-export.json').write_text(json.dumps(evidence,indent=2)+'\n',encoding='utf-8')
    print(f'Exported documentation/Report.pdf: {count} pages; unnumbered cover, numbered contents, syntax-highlighted code and vector figures.')


if __name__=='__main__':main()
