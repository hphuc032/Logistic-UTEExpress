/* Native WebSocket, fixed STOMP 1.2 subset; no credentials in URL or application logs. */
(() => {
    "use strict";
    const badge = document.querySelector("[data-notification-status]");
    if (!badge || !window.WebSocket) return;
    let socket, retry, heartbeat, lastInbound = 0, stopped = false, delay = 1000, connected = false, buffer = "", refreshRunning = false;
    const frame = (command, headers = {}) => command + "\n" + Object.entries(headers)
        .map(([key, value]) => key + ":" + value).join("\n") + "\n\n\0";
    async function recover() {
        if (refreshRunning || stopped) return;
        refreshRunning = true;
        try {
            const response = await fetch("/notifications/inbox", {credentials: "same-origin", cache: "no-store"});
            if (response.status === 401 || response.status === 403) { stop(); return; }
            if (!response.ok) return;
            const items = await response.json();
            const unread = items.filter(item => item.readAt === null).length;
            badge.textContent = unread ? "Thông báo (" + unread + "+)" : "Thông báo";
        } catch (_) { /* Durable inbox remains available by navigation. */ }
        finally { refreshRunning = false; }
    }
    function stop() {
        stopped = true; clearTimeout(retry); clearInterval(heartbeat);
        if (socket) socket.close();
    }
    async function open() {
        if (stopped) return;
        try {
            const response = await fetch("/notifications/realtime-session", {credentials: "same-origin", cache: "no-store"});
            if (response.status === 401 || response.status === 403) { stop(); return; }
            if (!response.ok) throw new Error("unavailable");
            const csrf = await response.json();
            if (typeof csrf.headerName !== "string" || typeof csrf.token !== "string"
                    || /[\r\n\0:]/.test(csrf.headerName) || /[\r\n\0]/.test(csrf.token)) { stop(); return; }
            const url = new URL("/ws", location.href);
            url.protocol = location.protocol === "https:" ? "wss:" : "ws:";
            connected = false; buffer = "";
            const ws = new WebSocket(url, "v12.stomp");
            socket = ws;
            ws.onopen = () => ws.send(frame("CONNECT", {"accept-version": "1.2", "heart-beat": "10000,10000", [csrf.headerName]: csrf.token}));
            ws.onmessage = event => {
                if (socket !== ws || stopped) return;
                lastInbound = Date.now();
                if (typeof event.data !== "string" || buffer.length + event.data.length > 32768) { ws.close(); return; }
                buffer += event.data;
                let end;
                while ((end = buffer.indexOf("\0")) >= 0) {
                    const received = buffer.slice(0, end).replace(/^\n+/, "");
                    buffer = buffer.slice(end + 1);
                    const command = received.split("\n", 1)[0].trim();
                    if (command === "CONNECTED" && !connected) {
                        connected = true; delay = 1000;
                        clearInterval(heartbeat);
                        heartbeat = setInterval(() => {
                            if (socket !== ws || stopped) return;
                            if (Date.now() - lastInbound > 30000) ws.close();
                            else if (ws.readyState === WebSocket.OPEN) ws.send("\n");
                        }, 10000);
                        ws.send(frame("SUBSCRIBE", {id: "notifications", destination: "/user/queue/notifications", ack: "auto"}));
                        recover(); // Always recover on reconnect; live hints may be lost or duplicated.
                    } else if (command === "MESSAGE" && connected) recover();
                    else if (command === "ERROR") ws.close();
                }
            };
            ws.onclose = event => {
                if (socket !== ws) return;
                clearInterval(heartbeat);
                connected = false;
                if (event.code === 1008) { stop(); return; }
                schedule();
            };
            ws.onerror = () => ws.close();
        } catch (_) { schedule(); }
    }
    function schedule() {
        if (stopped) return;
        clearTimeout(retry);
        retry = setTimeout(open, delay + Math.floor(Math.random() * 500));
        delay = Math.min(delay * 2, 30000);
    }
    document.addEventListener("submit", event => {
        if (event.target instanceof HTMLFormElement && new URL(event.target.action).pathname === "/logout") stop();
    });
    window.addEventListener("pagehide", stop);
    open();
})();
