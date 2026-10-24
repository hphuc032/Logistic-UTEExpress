package com.uteexpress.notification.realtime;

import com.uteexpress.security.dto.SocketIdentity;
import com.uteexpress.security.service.SocketIdentityService;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.*;

@Component
public class SocketConnections {
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final SocketIdentityService identities;
    public SocketConnections(SocketIdentityService identities) { this.identities = identities; }

    public WebSocketHandler decorate(WebSocketHandler delegate) {
        return new WebSocketHandlerDecorator(delegate) {
            @Override public void afterConnectionEstablished(WebSocketSession original) throws Exception {
                if (original instanceof org.springframework.web.socket.adapter.standard.StandardWebSocketSession standard) {
                    // Tomcat's blocking socket write must not hold the Account lock indefinitely.
                    standard.getNativeSession().getUserProperties().put("org.apache.tomcat.websocket.BLOCKING_SEND_TIMEOUT", 2000L);
                }
                var identity = (SocketIdentity) original.getAttributes().get(SocketHandshake.IDENTITY);
                identities.runIfCurrent(identity, () -> {});
                WebSocketSession guarded = new WebSocketSessionDecorator(
                        new ConcurrentWebSocketSessionDecorator(original, 2000, 32768)) {
                    @Override public void sendMessage(WebSocketMessage<?> message) throws IOException {
                        try {
                            identities.runIfCurrent(identity, () -> {
                                try { super.sendMessage(message); }
                                catch (IOException failure) { throw new UncheckedIOException(failure); }
                            });
                        } catch (RuntimeException denied) {
                            closeSession(getId());
                            throw new IOException("Socket delivery unavailable");
                        }
                    }
                };
                synchronized (sessions) {
                    long own = sessions.values().stream().filter(s -> s.getAttributes().get(SocketHandshake.IDENTITY) instanceof SocketIdentity existing && identity.accountId().equals(existing.accountId())).count();
                    if (sessions.size() >= 256 || own >= 4) { original.close(CloseStatus.POLICY_VIOLATION); return; }
                    sessions.put(original.getId(), guarded);
                }
                try { super.afterConnectionEstablished(guarded); }
                catch (Exception failure) { closeSession(original.getId()); throw failure; }
            }
            @Override public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
                sessions.remove(session.getId());
                super.afterConnectionClosed(session, status);
            }
        };
    }
    public void closeSession(String id) {
        if (id == null) return;
        var session = sessions.remove(id);
        if (session != null) try { session.close(CloseStatus.POLICY_VIOLATION); } catch (IOException ignored) { /* already closed */ }
    }
    @Scheduled(fixedDelayString = "${uteexpress.notifications.socket-sweep-ms:1000}")
    public void sweep() {
        sessions.forEach((id, session) -> {
            try { identities.runIfCurrent((SocketIdentity) session.getAttributes().get(SocketHandshake.IDENTITY), () -> {}); }
            catch (RuntimeException revoked) { closeSession(id); }
        });
    }
}
