const {test} = require("node:test");
const assert = require("node:assert/strict");
const vm = require("node:vm");
const fs = require("node:fs");
const path = require("node:path");
const source = fs.readFileSync(path.resolve(__dirname, "../../main/resources/static/js/notifications.js"), "utf8");
const settle = () => new Promise(resolve => setImmediate(resolve));
function browser(status = 200, inboxFetch) {
    const sockets = [], timers = new Map(), intervals = new Map(), requests = [], events = {};
    const badge = {textContent: "Thông báo"};
    Object.defineProperty(badge, "innerHTML", {set() { throw Error("unsafe HTML write"); }});
    class Socket {
        constructor(url, protocol) { this.url = String(url); this.protocol = protocol; this.sent = []; this.readyState = 1; sockets.push(this); }
        send(frame) { this.sent.push(frame); }
        close() { this.closed = true; }
    }
    Socket.OPEN = 1;
    let serial = 0, now = 0;
    class TestDate extends Date { static now() { return now; } }
    vm.runInNewContext(source, {
        URL, Date: TestDate, WebSocket: Socket, HTMLFormElement: class {},
        window: {WebSocket: Socket, addEventListener(name, callback) { events[name] = callback; }},
        document: {querySelector: () => badge, addEventListener(name, callback) { events[name] = callback; }},
        location: {href: "https://shop.example/orders", protocol: "https:"},
        fetch: async (url, options) => {
            requests.push({url, options});
            if (url.endsWith("inbox") && inboxFetch) return inboxFetch();
            return {status, ok: status === 200, json: async () => url.endsWith("realtime-session")
                ? {headerName: "X-XSRF-TOKEN", token: "masked-token"}
                : [{readAt: null}, {readAt: "2026-01-01T00:00:00Z"}]};
        },
        setTimeout(callback, delay) { const id = ++serial; timers.set(id, {callback, delay}); return id; },
        clearTimeout(id) { timers.delete(id); },
        setInterval(callback, delay) { const id = ++serial; intervals.set(id, {callback, delay}); return id; },
        clearInterval(id) { intervals.delete(id); }
    });
    return {sockets, timers, intervals, badge, requests, events, advance(ms) { now += ms; }};
}

test("authenticated connection uses masked CSRF and private queue; reconnect reloads durable inbox", async () => {
    const page = browser(); await settle();
    const first = page.sockets[0];
    assert.equal(first.url, "wss://shop.example/ws");
    assert.equal(first.protocol, "v12.stomp");
    first.onopen();
    assert.match(first.sent[0], /X-XSRF-TOKEN:masked-token/);
    first.onmessage({data: "CONNECTED\nversion:1.2\n\n\0"}); await settle();
    assert.match(first.sent[1], /destination:\/user\/queue\/notifications/);
    assert.equal(page.badge.textContent, "Thông báo (1+)");
    first.onmessage({data: 'MESSAGE\n\n<img src=x onerror=alert(1)>\0'}); await settle();
    assert.equal(page.badge.textContent, "Thông báo (1+)");
    first.onclose({code: 1006});
    const timer = [...page.timers.values()][0];
    assert.ok(timer.delay >= 1000 && timer.delay < 1500);
    timer.callback(); await settle();
    const second = page.sockets[1]; second.onopen();
    second.onmessage({data: "CONNECTED\n\n\0"}); await settle();
    assert.equal(page.requests.filter(r => r.url.endsWith("inbox")).length, 3);
    assert.ok(page.requests.every(r => r.options.credentials === "same-origin" && r.options.cache === "no-store"));
});

test("policy close stops reconnect and pagehide closes the socket", async () => {
    const page = browser(); await settle();
    page.sockets[0].onclose({code: 1008});
    assert.equal(page.timers.size, 0);
    assert.equal(page.sockets[0].closed, true);
    page.events.pagehide();
});

test("unauthorized bootstrap never creates a socket or reconnect loop", async () => {
    const page = browser(401); await settle();
    assert.equal(page.sockets.length, 0);
    assert.equal(page.timers.size, 0);
});

test("heartbeats keep a live connection and close a silent connection for reconnect", async () => {
    const page = browser(); await settle();
    const socket = page.sockets[0]; socket.onopen();
    assert.match(socket.sent[0], /heart-beat:10000,10000/);
    socket.onmessage({data: "CONNECTED\n\n\0"}); await settle();
    const heartbeat = [...page.intervals.values()][0];
    page.advance(15000); socket.onmessage({data: "\n"});
    page.advance(15000); heartbeat.callback();
    assert.equal(socket.sent.at(-1), "\n");
    assert.notEqual(socket.closed, true);
    page.advance(31000); heartbeat.callback();
    assert.equal(socket.closed, true);
    socket.onclose({code: 1006});
    assert.equal(page.intervals.size, 0);
    assert.equal(page.timers.size, 1);
});

function deferred() {
    let resolve, reject;
    const promise = new Promise((yes, no) => { resolve = yes; reject = no; });
    return {promise, resolve, reject};
}
const inboxResponse = (count, status = 200) => ({status, ok: status === 200,
    json: async () => Array.from({length: count}, () => ({readAt: null}))});
const hint = socket => socket.onmessage({data: "MESSAGE\n\n{}\0"});
const inboxRequests = page => page.requests.filter(r => r.url.endsWith("inbox")).length;

test("delayed inbox responses coalesce hints and preserve another hint during the trailing refresh", async () => {
    const reads = [deferred(), deferred(), deferred()]; let index = 0;
    const page = browser(200, () => reads[index++].promise); await settle();
    const socket = page.sockets[0]; socket.onmessage({data: "CONNECTED\n\n\0"});
    for (let i = 0; i < 10; i++) hint(socket);
    assert.equal(inboxRequests(page), 1); // no concurrent fetch or request per hint
    reads[0].resolve(inboxResponse(0)); await settle();
    assert.equal(inboxRequests(page), 2);
    for (let i = 0; i < 10; i++) hint(socket);
    reads[1].resolve(inboxResponse(1)); await settle();
    assert.equal(inboxRequests(page), 3);
    reads[2].resolve(inboxResponse(2)); await settle();
    assert.equal(page.badge.textContent, "Thông báo (2+)");
    assert.equal(inboxRequests(page), 3); // no hint-free refresh loop
});

test("a hint during delayed JSON parsing triggers a fresh inbox read", async () => {
    const body = deferred(); let calls = 0;
    const page = browser(200, async () => ++calls === 1
        ? {status: 200, ok: true, json: () => body.promise} : inboxResponse(3));
    await settle(); const socket = page.sockets[0];
    socket.onmessage({data: "CONNECTED\n\n\0"}); await settle();
    hint(socket); body.resolve([]); await settle();
    assert.equal(inboxRequests(page), 2);
    assert.equal(page.badge.textContent, "Thông báo (3+)");
});

for (const failure of ["http", "network"]) test("pending refresh survives " + failure + " failure without unbounded retries", async () => {
    const first = deferred(); let calls = 0;
    const page = browser(200, () => ++calls === 1 ? first.promise : Promise.resolve(inboxResponse(2)));
    await settle(); const socket = page.sockets[0]; socket.onmessage({data: "CONNECTED\n\n\0"});
    hint(socket);
    if (failure === "http") first.resolve(inboxResponse(0, 500)); else first.reject(Error("offline"));
    await settle();
    assert.equal(inboxRequests(page), 2);
    assert.equal(page.badge.textContent, "Thông báo (2+)");
});

for (const status of [401, 403]) test("unauthorized inbox " + status + " cancels pending refresh", async () => {
    const first = deferred();
    const page = browser(200, () => first.promise); await settle();
    const socket = page.sockets[0]; socket.onmessage({data: "CONNECTED\n\n\0"}); hint(socket);
    first.resolve(inboxResponse(4, status)); await settle();
    assert.equal(inboxRequests(page), 1);
    assert.equal(socket.closed, true);
    assert.equal(page.timers.size, 0);
    assert.equal(page.badge.textContent, "Thông báo");
});

for (const reason of ["pagehide", "policy"]) test(reason + " prevents late badge updates and pending fetches", async () => {
    const body = deferred();
    const page = browser(200, async () => ({status:200, ok:true, json: () => body.promise})); await settle();
    const socket = page.sockets[0]; socket.onmessage({data: "CONNECTED\n\n\0"}); await settle(); hint(socket);
    if (reason === "pagehide") page.events.pagehide(); else socket.onclose({code:1008});
    body.resolve(Array.from({length:4}, () => ({readAt:null}))); await settle();
    assert.equal(inboxRequests(page), 1);
    assert.equal(page.badge.textContent, "Thông báo");
    assert.equal(socket.closed, true);
    assert.equal(page.timers.size, 0);
});
