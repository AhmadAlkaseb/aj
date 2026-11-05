"""Browser QA: pip install playwright; playwright install chromium; python scripts/browser_check.py.
Screenshots are saved under ignored test-results/. Use --failures to verify outage UI.
"""
from pathlib import Path
import json
import os
import subprocess
import sys
import uuid
from playwright.sync_api import sync_playwright, expect

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / 'test-results'
OUT.mkdir(exist_ok=True)


def compose(*args):
    subprocess.run(['docker', 'compose', *args], cwd=ROOT, check=True,
                   stdout=subprocess.DEVNULL)


with sync_playwright() as p:
    # Reuse an installed browser if the machine has one from a previous Playwright version.
    candidates = sorted((Path(os.environ.get('LOCALAPPDATA', '/tmp')) / 'ms-playwright').glob('chromium-*/chrome-win64/chrome.exe'), reverse=True)
    browser = p.chromium.launch(headless=True, **({'executable_path': str(candidates[0])} if candidates else {}))
    page = browser.new_page(viewport={'width': 1440, 'height': 1100}, device_scale_factor=1)
    errors = []
    page.on('pageerror', lambda e: errors.append(str(e)))
    page.goto('http://localhost:3000')
    expect(page.locator('#login-button')).to_be_enabled(timeout=30000)
    page.locator('#login-button').click()
    page.locator('#username').fill('eksamen-admin'); page.locator('#password').fill('Admin123!'); page.locator('#kc-login').click()
    expect(page.locator('#identity-label')).to_have_text('eksamen-admin',timeout=30000)
    expect(page.locator('#system-label')).to_have_text('Alle systemer klar', timeout=30000)
    expect(page.get_by_role('heading', name='Et nyt kapitel.')).to_be_visible()
    page.screenshot(path=str(OUT / 'desktop.png'), full_page=True)
    page.get_by_role('button', name='Opret bil', exact=False).first.click()
    page.get_by_label('Mærke', exact=True).fill('Browser Test')
    model = uuid.uuid4().hex[:6]
    page.get_by_label('Model', exact=True).fill(model)
    page.get_by_label('Pris', exact=True).fill('299000')
    page.get_by_label('Drivmiddel').fill('El')
    page.get_by_label('Gearkasse').fill('Automatisk')
    if '--ai' in sys.argv:
        with page.expect_response(lambda r:r.url.endswith('/api/v1/products/description-draft'),timeout=60000) as drafted:
            page.locator('#draft-description').click()
        assert drafted.value.status==200, drafted.value.status
        assert drafted.value.json()['requiresApproval'] is True
        expect(page.locator('[name="description"]')).not_to_have_value('',timeout=10000)
    page.get_by_role('button', name='Gem bil', exact=False).click()
    expect(page.locator('#modal')).not_to_be_visible(timeout=12000)
    page.locator('#search').fill(model)
    expect(page.locator('.car-card')).to_have_count(1, timeout=15000)
    page.locator('[data-detail]').click()
    expect(page.locator('#modal')).to_be_visible()
    expect(page.get_by_role('button', name='Reservér bilen', exact=False)).to_be_enabled()
    page.get_by_role('button', name='Luk vindue').click()
    page.locator('#search').fill('')
    page.get_by_role('button', name='Kunder', exact=True).click()
    page.get_by_role('button', name='Opret kunde', exact=False).click()
    page.get_by_label('Fornavn', exact=True).fill('Browser')
    page.get_by_label('Efternavn', exact=True).fill('Test')
    page.get_by_label('E-mail', exact=True).fill(uuid.uuid4().hex[:10] + '@browser.test')
    page.get_by_role('button', name='Gem kunde', exact=False).click()
    expect(page.locator('#modal')).not_to_be_visible(timeout=12000)
    expect(page.locator('#customer-list')).to_contain_text('Browser Test')
    page.get_by_role('button', name='Biler', exact=True).click()
    page.locator('#search').fill(model)
    expect(page.locator('.car-card')).to_have_count(1, timeout=15000)
    page.locator('[data-detail]').click()
    page.get_by_role('button', name='Reservér bilen', exact=False).click()
    page.get_by_label('Leveringsadresse', exact=True).fill('Browservej 10, 2100 Koebenhavn')
    with page.expect_response(lambda r: r.url.endswith('/api/v1/orders') and r.request.method == 'POST') as created:
        page.get_by_role('button', name='Opret reservation', exact=False).click()
    order_guid = created.value.json()['guid']
    order_row = page.locator('#order-list tbody tr').filter(has_text=order_guid[:8])
    expect(order_row).to_contain_text('Reserveret', timeout=20000)
    order_row.get_by_role('button', name='Opret betaling', exact=False).click()
    page.get_by_role('button', name='Opret betaling', exact=False).last.click()
    payment_row = page.locator('#payment-list tbody tr').filter(has_text=order_guid[:8])
    expect(payment_row.get_by_role('button', name='Markér modtaget')).to_be_visible(timeout=20000)
    payment_row.get_by_role('button', name='Markér modtaget').click()
    page.get_by_role('button', name='Bekræft', exact=False).click()
    expect(payment_row).to_contain_text('Afsluttet', timeout=12000)
    for name, section in [('Ordrer', '#order-list'), ('Betalinger', '#payment-list')]:
        page.get_by_role('button', name=name, exact=True).click()
        expect(page.locator(section)).to_be_visible()
    page.get_by_role('button', name='Biler', exact=True).click()
    page.set_viewport_size({'width':390,'height':844})
    page.screenshot(path=str(OUT / 'mobile.png'), full_page=True)
    assert page.evaluate('document.documentElement.scrollWidth <= innerWidth'), 'Mobile page overflows horizontally'
    if '--failures' in sys.argv:
        try:
            compose('stop','product-view')
            expect(page.locator('#outage')).to_be_visible(timeout=25000)
            expect(page.locator('#outage-message')).to_contain_text('Bilkatalog', timeout=25000)
            expect(page.locator('#cars')).to_contain_text('midlertidigt utilgængeligt', timeout=20000)
            page.screenshot(path=str(OUT / 'service-down.png'), full_page=True)
        finally:
            compose('start','product-view')
        page.locator('#retry').click()
        expect(page.locator('#outage')).not_to_be_visible(timeout=30000)
        try:
            compose('stop','apigateway')
            expect(page.locator('#outage-title')).to_have_text('Vi kan ikke få forbindelse lige nu', timeout=25000)
            page.screenshot(path=str(OUT / 'gateway-down.png'), full_page=True)
        finally:
            compose('start','apigateway')
        expect(page.locator('#system-label')).to_have_text('Alle systemer klar', timeout=30000)
    browser.close()
    assert not errors, '\n'.join(errors)
    print(json.dumps({'result':'PASS','javascriptErrors':errors,'screenshots':str(OUT)},indent=2))
