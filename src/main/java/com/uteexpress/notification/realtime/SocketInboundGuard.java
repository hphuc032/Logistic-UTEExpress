package com.uteexpress.notification.realtime;

import com.uteexpress.security.dto.SocketIdentity;
import com.uteexpress.security.service.SocketIdentityService;
import java.util.Set;
import org.springframework.messaging.*;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.messaging.support.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.messaging.web.csrf.XorCsrfChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class SocketInboundGuard implements ChannelInterceptor {
    private static final String CONNECTED = "uteexpress.socket.connected";
    private static final String SUBSCRIPTION = "uteexpress.socket.subscription";
    private static final Set<String> IDENTITY_HEADERS = Set.of("login", "passcode", "authorization", "cookie", "userid", "recipientid", "role", "roles", "user");
    private final SocketIdentityService identities;
    private final SocketObservability observations;
    private final SocketConnections connections;
    private final XorCsrfChannelInterceptor csrf = new XorCsrfChannelInterceptor();
    public SocketInboundGuard(SocketIdentityService identities, SocketConnections connections, SocketObservability observations) {
        this.identities = identities; this.connections = connections; this.observations = observations;
    }
    @Override public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (headers == null) throw new AccessDeniedException("Invalid socket frame");
        try {
            var attrs = headers.getSessionAttributes();
            if (attrs == null) throw denied();
            var identity = (SocketIdentity) attrs.get(SocketHandshake.IDENTITY);
            if (headers.toNativeHeaderMap().keySet().stream().anyMatch(h -> IDENTITY_HEADERS.contains(h.toLowerCase(java.util.Locale.ROOT)))) throw denied();
            identities.runIfCurrent(identity, () -> {});
            headers.setUser(identity); // never use client-native identity/routing headers
            synchronized (attrs) {
                var command = headers.getCommand();
                if (command == StompCommand.CONNECT || command == StompCommand.STOMP) {
                    if (Boolean.TRUE.equals(attrs.get(CONNECTED))) throw denied();
                    csrf.preSend(message, channel);
                    attrs.put(CONNECTED, true);
                } else {
                    if (!Boolean.TRUE.equals(attrs.get(CONNECTED))) throw denied();
                    if (command == StompCommand.SUBSCRIBE) {
                        String id = headers.getSubscriptionId();
                        if (!"/user/queue/notifications".equals(headers.getDestination()) || id == null
                                || !id.matches("[A-Za-z0-9_-]{1,64}") || attrs.containsKey(SUBSCRIPTION)) throw denied();
                        attrs.put(SUBSCRIPTION, id);
                    } else if (command == StompCommand.UNSUBSCRIBE) {
                        if (!java.util.Objects.equals(attrs.get(SUBSCRIPTION), headers.getSubscriptionId())) throw denied();
                        attrs.remove(SUBSCRIPTION);
                    } else if (command != StompCommand.DISCONNECT && command != null) throw denied();
                }
            }
            return message;
        } catch (RuntimeException failure) {
            observations.denied(SocketObservability.Reason.FRAME_DENIED, failure);
            connections.closeSession(headers.getSessionId());
            // Drop and close; do not let framework exception logging render untrusted native headers.
            return null;
        }
    }
    private static AccessDeniedException denied() { return new AccessDeniedException("Socket frame denied"); }
}
