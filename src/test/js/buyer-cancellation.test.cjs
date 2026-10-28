const {test} = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");
const source = fs.readFileSync(path.resolve(__dirname, "../../main/resources/static/js/buyer-cancellation.js"), "utf8");

function browser({confirmed = true, response, fetcher, dataset = {}, present = true} = {}) {
    const listeners = {}, requests = [], confirmations = [], attributes = {};
    let reloads = 0;
    const button = {disabled: true, addEventListener: (name, fn) => { listeners[name] = fn; }};
    const status = {textContent: ""};
    Object.defineProperty(status, "innerHTML", {set() { throw Error("unsafe HTML write"); }});
    const refresh = {hidden: true};
    const panel = {
        dataset: {cancelUrl: "/shop/buyer/orders/9007199254740993/cancel", cancelVersion: "7",
            csrfHeader: "X-XSRF-TOKEN", csrfToken: "masked-token", ...dataset},
        querySelector: selector => ({"[data-cancel-button]": button, "[data-cancel-status]": status, "[data-cancel-refresh]": refresh})[selector],
        setAttribute: (key, value) => { attributes[key] = value; },
        removeAttribute: key => { delete attributes[key]; }
    };
    const page = {button, status, refresh, requests, confirmations, attributes, get reloads() { return reloads; },
        click: () => listeners.click?.()};
    vm.runInNewContext(source, {
        URL,
        document: {querySelector: () => present ? panel : null},
        window: {
            location: {href: "https://shop.example/shop/orders/9007199254740993", origin: "https://shop.example", reload: () => { reloads++; }},
            confirm: text => { confirmations.push(text); return confirmed; },
            fetch: async (url, options) => {
                requests.push({url, options});
                return fetcher ? fetcher() : response || {status: 200, json: async () => ({result: "OK"})};
            }
        }
    });
    return page;
}

test("confirmation sends the rendered URL/version and masked CSRF, then refreshes committed state", async () => {
    const page = browser();
    assert.equal(page.button.disabled, false);
    await page.click();
    assert.equal(page.confirmations.length, 1);
    assert.equal(page.requests.length, 1);
    const {url, options} = page.requests[0];
    assert.equal(url, "https://shop.example/shop/buyer/orders/9007199254740993/cancel");
    assert.equal(options.method, "POST");
    assert.equal(options.credentials, "same-origin");
    assert.equal(options.redirect, "error");
    assert.equal(options.headers["Content-Type"], "application/json");
    assert.equal(options.headers.Accept, "application/json");
    assert.equal(options.headers["X-XSRF-TOKEN"], "masked-token");
    assert.equal(options.body, '{"expectedOrderVersion":7}');
    assert.equal(page.reloads, 1);
    assert.equal(page.button.disabled, true);
    assert.match(page.status.textContent, /Đã hủy/);
});

test("declining confirmation does not submit or disable the action", async () => {
    const page = browser({confirmed: false});
    await page.click();
    assert.equal(page.requests.length, 0);
    assert.equal(page.reloads, 0);
    assert.equal(page.button.disabled, false);
});

test("double clicks during a pending request produce one confirmation and one POST", async () => {
    let finish;
    const pending = new Promise(resolve => { finish = resolve; });
    const page = browser({fetcher: () => pending});
    const first = page.click();
    await page.click();
    assert.equal(page.requests.length, 1);
    assert.equal(page.confirmations.length, 1);
    assert.equal(page.attributes["aria-busy"], "true");
    finish({status: 200, json: async () => ({result: "OK"})});
    await first;
    await page.click();
    assert.equal(page.requests.length, 1);
    assert.equal(page.reloads, 1);
    assert.equal(page.attributes["aria-busy"], undefined);
});

for (const [code, message] of [[401, /đăng nhập lại/], [403, /phiên bảo mật/], [404, /Không tìm thấy/], [409, /đã thay đổi/]]) {
    test(`${code} is a safe terminal response requiring navigation rather than a second POST`, async () => {
        const page = browser({response: {status: code, json: async () => { throw Error("must not display server body"); }}});
        await page.click();
        assert.match(page.status.textContent, message);
        assert.equal(page.refresh.hidden, false);
        assert.equal(page.button.disabled, true);
        assert.equal(page.reloads, 0);
        await page.click();
        assert.equal(page.requests.length, 1);
    });
}

for (const [name, options] of [
    ["network failure", {fetcher: () => Promise.reject(Error("private diagnostic"))}],
    ["server error", {response: {status: 500}}],
    ["unexpected success body", {response: {status: 200, json: async () => ({result: "<img onerror=alert(1)>"})}}],
    ["malformed body", {response: {status: 200, json: async () => { throw Error("not JSON"); }}}]
]) {
    test(`${name} never retries an uncertain cancellation or reveals raw errors`, async () => {
        const page = browser(options);
        await page.click(); await page.click();
        assert.match(page.status.textContent, /tải lại trang/);
        assert.doesNotMatch(page.status.textContent, /private|img|not JSON/);
        assert.equal(page.requests.length, 1);
        assert.equal(page.reloads, 0);
        assert.equal(page.button.disabled, true);
        assert.equal(page.refresh.hidden, false);
    });
}

test("the complete nonnegative Long version is sent without JavaScript numeric rounding", async () => {
    const page = browser({dataset: {cancelVersion: "9223372036854775807"}});
    await page.click();
    assert.equal(page.requests[0].options.body, '{"expectedOrderVersion":9223372036854775807}');
});

for (const [name, dataset] of [
    ["missing CSRF", {csrfToken: ""}], ["invalid header", {csrfHeader: "X-CSRF\r\nOther"}],
    ["negative version", {cancelVersion: "-1"}], ["missing version", {cancelVersion: undefined}],
    ["forged version", {cancelVersion: '0,"actorId":1'}], ["overflow", {cancelVersion: "9223372036854775808"}],
    ["foreign origin", {cancelUrl: "https://foreign.example/buyer/orders/1/cancel"}],
    ["query override", {cancelUrl: "/buyer/orders/1/cancel?expectedOrderVersion=0"}]
]) {
    test(`${name} leaves the action disabled and cannot send credentials`, async () => {
        const page = browser({dataset}); await page.click();
        assert.equal(page.button.disabled, true);
        assert.equal(page.requests.length, 0);
        assert.equal(page.confirmations.length, 0);
    });
}

test("ineligible pages without the rendered control attach no handler", async () => {
    const page = browser({present: false});
    await page.click();
    assert.equal(page.requests.length, 0);
    assert.equal(page.button.disabled, true);
});
