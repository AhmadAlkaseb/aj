"""Render compact A4 overview SVGs; retain the complete UML as separate artifacts."""
import os
import re
import sys
import urllib.request
from pathlib import Path
ROOT=Path(__file__).resolve().parent.parent
sys.path.insert(0,str(ROOT/'test-results/tools/python-deps'))
from playwright.sync_api import sync_playwright


def main():
    directory=ROOT/'documentation/report-figures'
    source=(directory/'README.md').read_text(encoding='utf-8')
    diagrams=re.findall(r'## ([a-z-]+)\n\n```mermaid\n(.*?)\n```',source,re.S)
    script=ROOT/'test-results/report-export/mermaid-10.9.3.min.js'
    if not script.exists():
        script.parent.mkdir(parents=True,exist_ok=True)
        with urllib.request.urlopen('https://cdn.jsdelivr.net/npm/mermaid@10.9.3/dist/mermaid.min.js',timeout=45) as response:script.write_bytes(response.read())
    with sync_playwright() as p:
        candidates=sorted((Path(os.environ.get('LOCALAPPDATA',''))/'ms-playwright').glob('chromium-*/chrome-win*/chrome.exe'),reverse=True) if sys.platform=='win32' else []
        browser=p.chromium.launch(**({'executable_path':str(candidates[0])} if candidates else {}))
        page=browser.new_page();page.set_content('<html><body></body></html>');page.add_script_tag(path=str(script))
        page.evaluate("mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:'base',themeVariables:{fontFamily:'Arial, Helvetica, sans-serif',fontSize:'20px',primaryColor:'#eef5fc',primaryTextColor:'#172b4d',primaryBorderColor:'#2563a6',lineColor:'#475569',secondaryColor:'#edf8f3',tertiaryColor:'#f4f0fa',edgeLabelBackground:'#ffffff'},flowchart:{htmlLabels:false,nodeSpacing:25,rankSpacing:35,curve:'linear'}})")
        for name,diagram in diagrams:
            svg=page.evaluate('async ({name,diagram})=>(await mermaid.render("overview-"+name,diagram)).svg',{'name':name,'diagram':diagram})
            (directory/(name+'.svg')).write_text(svg,encoding='utf-8')
        browser.close()
    print('Rendered '+str(len(diagrams))+' compact report overview figures.')
    from render_architecture_models import main as render_models
    render_models()


if __name__=='__main__':main()
