const {test} = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");
const source = fs.readFileSync(path.resolve(__dirname, "../../main/resources/static/js/shipment-assignment.js"), "utf8");
function browser({dataset = {}, shipperValue = "9007199254740993", response = {status: 201}, fetcher, present = true} = {}) {
    const listeners = {}, requests = [], attributes = {};
    let reloads = 0;
    const shipper = {value: shipperValue}, button = {disabled: true}, status = {textContent: ""}, refresh = {hidden: true};
    Object.defineProperty(status, "innerHTML", {set() {throw Error("unsafe HTML");}});
    const form = {dataset: {action: "/admin/orders/11/assign", versionField: "expectedOrderVersion", version: "9007199254740995",
        csrfHeader: "X-XSRF-TOKEN", csrfToken: "masked-token", ...dataset},
        querySelector: s => ({"[name=shipperId]": shipper, "[data-assignment-submit]": button,
            "[data-assignment-status]": status, "[data-assignment-refresh]": refresh})[s],
        addEventListener: (name, fn) => {listeners[name] = fn;},
        setAttribute: (k, v) => {attributes[k] = v;}, removeAttribute: k => {delete attributes[k];}};
    vm.runInNewContext(source, {URL, document: {querySelector: () => present ? form : null}, window: {
        location: {href: "https://shop.example/admin/orders/11", origin: "https://shop.example", reload: () => {reloads++;}},
        fetch: async (url, options) => {requests.push({url, options});return fetcher ? fetcher() : response;}
    }});
    return {button, status, refresh, shipper, requests, attributes, get reloads(){return reloads;},
        submit: () => listeners.submit?.({preventDefault(){}})};
}
test("assignment preserves Long precision and uses same-origin JSON plus masked CSRF", async () => {
    const p = browser();assert.equal(p.button.disabled, false);await p.submit();
    assert.equal(p.requests.length, 1);const {url, options} = p.requests[0];
    assert.equal(url, "https://shop.example/admin/orders/11/assign");
    assert.equal(options.body, '{"shipperId":9007199254740993,"expectedOrderVersion":9007199254740995}');
    assert.equal(options.credentials, "same-origin");assert.equal(options.redirect, "error");
    assert.equal(options.headers["X-XSRF-TOKEN"], "masked-token");assert.equal(p.reloads, 1);
});
test("reassignment sends the Shipment token, not an Order token", async () => {
    const p = browser({dataset:{action:"/manager/orders/11/reassign",versionField:"expectedShipmentVersion",version:"4"},response:{status:200}});
    await p.submit();assert.equal(p.requests[0].options.body, '{"shipperId":9007199254740993,"expectedShipmentVersion":4}');assert.equal(p.reloads,1);
});
test("foreign URL, wrong route, query, fragment or token key mismatch stays disabled", async () => {
    for (const dataset of [{action:"https://other.example/admin/orders/11/assign"},{action:"/admin/accounts/11"},
        {action:"/admin/orders/11/assign?actor=7"},{action:"/admin/orders/11/assign#x"},{versionField:"actorId"},
        {csrfHeader:"X-XSRF-TOKEN\r\nInjected"},{csrfToken:"bad\nvalue"}]) {
        const p=browser({dataset});await p.submit();assert.equal(p.requests.length,0);assert.equal(p.button.disabled,true);
    }
});
test("invalid version and Shipper digits never become executable or rounded JSON", async () => {
    for (const version of ["-1","01","1e2","9223372036854775808",'1,"actorId":8']) {
        const p=browser({dataset:{version}});await p.submit();assert.equal(p.requests.length,0);
    }
    for (const shipperValue of ["", "0", "01", "1e2", '1,"actorId":8']) {
        const p=browser({shipperValue});await p.submit();assert.equal(p.requests.length,0);
    }
});
test("stale response is safe text, never automatic replay, and requires refresh", async () => {
    const p=browser({response:{status:409}});await p.submit();await p.submit();
    assert.equal(p.requests.length,1);assert.equal(p.reloads,0);assert.equal(p.refresh.hidden,false);
    assert.match(p.status.textContent,/đã thay đổi/);assert.equal(p.button.disabled,true);
});
test("network ambiguity never repeats a possibly committed assignment", async () => {
    const p=browser({fetcher:()=>{throw Error("private response");}});await p.submit();await p.submit();
    assert.equal(p.requests.length,1);assert.equal(p.refresh.hidden,false);assert.doesNotMatch(p.status.textContent,/private/);
});
test("two submits while a request is pending issue one command", async () => {
    let resolve;const waiting=new Promise(r=>{resolve=r;});const p=browser({fetcher:()=>waiting});
    const first=p.submit();await p.submit();assert.equal(p.requests.length,1);resolve({status:201});await first;assert.equal(p.reloads,1);
});
test("pages without an assignment form remain inert", async () => {const p=browser({present:false});await p.submit();assert.equal(p.requests.length,0);});
