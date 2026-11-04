"""Real signed-token, ownership, registration and direct-port access tests."""
import json
import os
import uuid
from pathlib import Path
from playwright.sync_api import sync_playwright, expect
from test_identity import admin_headers

OUT = Path(__file__).resolve().parent.parent / 'test-results'
OUT.mkdir(exist_ok=True)


def main():
    with sync_playwright() as p:
        candidates = sorted((Path(os.environ.get('LOCALAPPDATA', '/tmp')) / 'ms-playwright').glob('chromium-*/chrome-win64/chrome.exe'), reverse=True)
        browser = p.chromium.launch(headless=True, **({'executable_path': str(candidates[0])} if candidates else {}))
        context = browser.new_context()
        page = context.new_page()
        for port, path in [(7002, '/users'), (7004, '/orders'), (7005, '/payments')]:
            assert context.request.get(f'http://127.0.0.1:{port}/api/v1{path}').status == 401
        assert context.request.post('http://127.0.0.1:7003/api/v1/products', data={}).status == 401
        assert context.request.get('http://127.0.0.1:7006/api/v1/products').status == 200
        page.goto('http://localhost:3000')
        expect(page.locator('#login-button')).to_be_enabled(timeout=30000)
        page.locator('#login-button').click()
        page.locator('#username').fill('kunde01'); page.locator('#password').fill('Demo123!'); page.locator('#kc-login').click()
        expect(page.locator('#identity-label')).to_have_text('kunde01', timeout=30000)
        headers = page.evaluate('async()=>await window.ajAuth.headers()')
        customer = page.evaluate('window.ajAuth.session.customerGuid')
        own = context.request.get('http://127.0.0.1:7002/api/v1/users', headers=headers)
        assert own.status == 200 and [u['guid'] for u in own.json()] == [customer]
        for domain, port in [('orders', 7004), ('payments', 7005)]:
            rows = context.request.get(f'http://127.0.0.1:{port}/api/v1/{domain}', headers=headers)
            assert rows.status == 200
            all_rows = context.request.get(f'http://127.0.0.1:{port}/api/v1/{domain}', headers=admin_headers()).json()
            own_ids = {r['guid'] for r in rows.json()}
            other = next(r for r in all_rows if r['guid'] not in own_ids)
            assert context.request.get(f'http://127.0.0.1:{port}/api/v1/{domain}/'+other['guid'], headers=headers).status == 403
        assert context.request.post('http://127.0.0.1:7003/api/v1/products', headers=headers, data={}).status == 403
        assert context.request.post('http://127.0.0.1:7005/api/v1/payments', headers=headers, data={}).status == 403
        assert context.request.post('http://127.0.0.1:7004/api/v1/orders', headers=headers,
            data={'userGuid': str(uuid.uuid4()), 'productGuid': str(uuid.uuid4()), 'shippingAddress': 'Test'}).status == 403
        assert context.request.delete('http://127.0.0.1:7002/api/v1/users/'+customer, headers=headers).status == 403
        expect(page.locator('#add-product')).to_be_hidden()
        context.close()

        # A new identity receives its own profile; no email-based takeover of existing profiles.
        context = browser.new_context(); page = context.new_page()
        page.goto('http://localhost:3000')
        expect(page.locator('#login-button')).to_be_enabled(timeout=30000)
        page.locator('#login-button').click()
        page.locator('#kc-registration a').click()
        name = 'registration-' + uuid.uuid4().hex[:10]
        for field, value in [('username', name), ('firstName', 'Registration'), ('lastName', 'Test'),
                             ('email', name+'@example.test'), ('password', 'Registration123!'), ('password-confirm', 'Registration123!')]:
            page.locator('#'+field).fill(value)
        page.locator('input[type=submit],button[type=submit]').last.click()
        expect(page.locator('#identity-label')).to_have_text(name, timeout=30000)
        identity = page.evaluate('window.ajAuth.session')
        assert 'customer' in identity['roles'] and 'admin' not in identity['roles']
        headers = page.evaluate('async()=>await window.ajAuth.headers()')
        rows = context.request.get('http://127.0.0.1:7002/api/v1/users', headers=headers).json()
        assert len(rows) == 1 and rows[0]['guid'] == identity['customerGuid'] and rows[0]['email'] == name+'@example.test'
        context.close(); browser.close()
    result = {'result': 'PASS', 'directPorts': 'protected', 'customerOwnership': 'enforced',
              'adminOperations': 'denied to customers', 'selfRegistration': 'linked to own profile'}
    (OUT/'security-results.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print(json.dumps(result, indent=2))


if __name__ == '__main__':
    main()
