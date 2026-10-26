package com.uteexpress.notification.realtime;

import com.uteexpress.security.service.SocketIdentityService;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.util.Arrays;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.*;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

@Component
public class SocketHandshake implements HandshakeInterceptor {
    public static final String IDENTITY = "uteexpress.socket.identity";
    private final SocketIdentityService identities;
    private final SocketObservability observations;
    public SocketHandshake(SocketIdentityService identities, SocketObservability observations) { this.identities = identities; this.observations = observations; }
    @Override public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Map<String, Object> attributes) {
        try {
            if (!(request instanceof ServletServerHttpRequest servlet) || request.getPrincipal() == null
                    || request.getURI().getRawQuery() != null || !sameOrigin(request)) throw new IllegalArgumentException();
            Cookie[] cookies = servlet.getServletRequest().getCookies();
            var tokens = cookies == null ? java.util.List.<Cookie>of() : Arrays.stream(cookies)
                    .filter(c -> identities.cookieName().equals(c.getName())).toList();
            if (tokens.size() != 1 || tokens.getFirst().getValue().length() > 4096) throw new IllegalArgumentException();
            var identity = identities.authenticate(tokens.getFirst().getValue());
            if (!identity.getName().equals(request.getPrincipal().getName())) throw new IllegalArgumentException();
            attributes.put(IDENTITY, identity);
            return true;
        } catch (RuntimeException denied) {
            observations.denied(SocketObservability.Reason.HANDSHAKE_DENIED, denied);
            response.setStatusCode(HttpStatus.FORBIDDEN);
            return false; // never log request headers, token or identity claims
        }
    }
    private static boolean sameOrigin(ServerHttpRequest request) {
        var origins = request.getHeaders().getOrigin();
        if (origins == null || request.getHeaders().get("Origin").size() != 1) return false;
        URI origin = URI.create(origins);
        URI target = request.getURI();
        return origin.getHost() != null && origin.getUserInfo() == null && origin.getRawQuery() == null
                && origin.getRawFragment() == null && (origin.getRawPath() == null || origin.getRawPath().isEmpty())
                && origin.getScheme().equalsIgnoreCase(target.getScheme())
                && origin.getHost().equalsIgnoreCase(target.getHost()) && port(origin) == port(target);
    }
    private static int port(URI uri) { return uri.getPort() >= 0 ? uri.getPort() : ("https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80); }
    @Override public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
            WebSocketHandler handler, Exception exception) {}
}
