package com.uteexpress.notification;

import com.uteexpress.notification.service.NotificationService;
import com.uteexpress.notification.realtime.SocketConnections;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.security.jwt.JwtTokenService;
import com.uteexpress.security.service.SocketIdentityService;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"uteexpress.security.jwt.cookie-secure=false", "uteexpress.notifications.socket-sweep-ms=100", "management.health.mail.enabled=false"})
@Import(NotificationSocketIT.ClockConfig.class)
@Testcontainers
@Timeout(40)
class NotificationSocketIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @TestConfiguration(proxyBeanMethods = false) static class ClockConfig {
        @Bean @Primary TestClock socketTestClock() { return new TestClock(); }
    }
    static class TestClock extends Clock {
        final AtomicReference<Instant> now = new AtomicReference<>(Instant.now());
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    }
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtTokenService tokens;
    @Autowired JwtEncoder encoder;
    @Autowired SocketIdentityService socketIdentities;
    @Autowired NotificationService notifications;
    @Autowired PlatformTransactionManager manager;
    @Autowired TestClock clock;
    @Autowired SimpUserRegistry users;
    @MockitoSpyBean SocketConnections connections;
    @Autowired SimpleBrokerMessageHandler broker;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final List<RawSocket> opened = new ArrayList<>();
    @BeforeEach void resetClock() { clock.now.set(Instant.now()); }
    @AfterEach void close() { opened.forEach(RawSocket::abort); clock.now.set(Instant.now()); }
    record Account(long id, String jwt) {}
    record Browser(Account account, String cookie, String csrf) {}
    String origin() { return "http://localhost:" + port; }
    URI uri(String path) { return URI.create(origin() + path); }

    @Test void privateQueueSendsOnlyAfterCommitWithMinimalPayloadAndReconnectRecovers() throws Exception {
        var owner = browser(account("USER"));
        var foreign = browser(account("USER"));
        var mine = connected(owner);
        var theirs = connected(foreign);
        subscribe(mine, owner.account()); subscribe(theirs, foreign.account());
        String key = "socket-commit:" + UUID.randomUUID();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            notifications.record(owner.account.id, null, "SYSTEM", "Sensitive stored title", "Private detail", key);
            assertThat(mine.frames).isEmpty();
        });
        String frame = mine.next("MESSAGE");
        long id = jdbc.queryForObject("SELECT id FROM uteexpress.notifications WHERE dedup_key=?", Long.class, key);
        assertThat(frame).contains("\"id\":" + id, "Có thông báo mới", "/notifications")
                .doesNotContain("Sensitive stored title", "Private detail", "recipientId", "payment", "phone", "address");
        assertThat(theirs.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
        new TransactionTemplate(manager).executeWithoutResult(status -> {
            notifications.record(owner.account.id, null, "SYSTEM", "Duplicate", "Duplicate", key);
            notifications.record(owner.account.id, null, "SYSTEM", "Rollback", "Rollback", key + ":rollback");
            status.setRollbackOnly();
        });
        assertThat(mine.frames.poll(300, TimeUnit.MILLISECONDS)).isNull();
        mine.abort();
        var reconnected = connected(owner); subscribe(reconnected, owner.account());
        var inbox = get("/notifications/inbox", owner.cookie);
        assertThat(inbox.statusCode()).isEqualTo(200);
        assertThat(inbox.body()).contains("Sensitive stored title", "Private detail").doesNotContain("Rollback");
        assertThat(get("/notifications/inbox", foreign.cookie).body()).doesNotContain("Sensitive stored title");
    }

    @ParameterizedTest @ValueSource(strings = {"/queue/notifications", "/topic/orders", "/user/uteexpress:user:1/queue/notifications", "/app/orders"})
    void foreignOrBrokerSubscriptionsCloseConnection(String destination) throws Exception {
        var browser = browser(account("USER")); var socket = connected(browser);
        socket.send("SUBSCRIBE\nid:foreign\ndestination:" + destination + "\n\n\0");
        assertThat(socket.closed.await(8, TimeUnit.SECONDS)).isTrue();
    }

    @Test void clientPublishAndForgedIdentityAreRejected() throws Exception {
        var browser = browser(account("USER")); var socket = connected(browser);
        socket.send("SEND\ndestination:/queue/notifications\n\nforged\0");
        assertThat(socket.closed.await(8, TimeUnit.SECONDS)).isTrue();
        var forged = raw(browser.cookie, origin());
        forged.send(connectFrame(browser.csrf) .replace("\n\n", "\nuserId:1\n\n"));
        assertThat(forged.closed.await(8, TimeUnit.SECONDS)).isTrue();
    }

    @ParameterizedTest @ValueSource(strings = {"", "incorrect"})
    void connectRequiresMaskedCsrf(String token) throws Exception {
        var browser = browser(account("USER")); var socket = raw(browser.cookie, origin());
        socket.send(token.isEmpty() ? "CONNECT\naccept-version:1.2\n\n\0" : connectFrame(token));
        assertThat(socket.closed.await(8, TimeUnit.SECONDS)).isTrue();
        assertThat(socket.frames.stream()).noneMatch(frame -> frame.startsWith("CONNECTED"));
    }

    @Test void handshakeRejectsMissingForgedRevokedTokensAndMissingForeignOrigin() throws Exception {
        var browser = browser(account("USER"));
        assertHandshakeDenied("", origin());
        assertHandshakeDenied("UTEEXPRESS_AUTH=forged", origin());
        assertHandshakeDenied(browser.cookie, null);
        assertHandshakeDenied(browser.cookie, "https://foreign.invalid");
        assertHandshakeDenied(browser.cookie, origin() + ".foreign.invalid");
        jdbc.update("UPDATE uteexpress.users SET token_version=token_version+1 WHERE id=?", browser.account.id);
        assertHandshakeDenied(browser.cookie, origin());
    }

    @ParameterizedTest @ValueSource(strings = {"logout", "lock", "role"})
    void committedGovernanceOrLogoutClosesIdleSocketAndPreventsFurtherPush(String operation) throws Exception {
        var target = browser(account("USER")); var socket = connected(target); subscribe(socket, target.account());
        Browser actor = operation.equals("logout") ? target : browser(account("ADMIN"));
        String path = switch (operation) {
            case "logout" -> "/logout";
            case "lock" -> "/admin/accounts/" + target.account.id + "/lock";
            default -> "/admin/accounts/" + target.account.id + "/roles/USER/revoke";
        };
        var response = http.send(HttpRequest.newBuilder(uri(path)).header("Cookie", actor.cookie)
                .header("X-XSRF-TOKEN", actor.csrf).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("version=0")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isIn(302, 303);
        assertThat(socket.closed.await(8, TimeUnit.SECONDS)).isTrue();
        new TransactionTemplate(manager).executeWithoutResult(status -> notifications.record(target.account.id,
                null, "SYSTEM", "Revoked", "Must not push", "revoked:" + UUID.randomUUID()));
        assertThat(socket.frames.stream()).noneMatch(frame -> frame.startsWith("MESSAGE"));
        assertHandshakeDenied(target.cookie, origin());
    }

    @Test void strictJwtExpiryClosesIdleSessionAndBlocksNewHandshakeEvenWithinClockSkew() throws Exception {
        var browser = browser(account("USER")); var socket = connected(browser); subscribe(socket, browser.account());
        clock.now.updateAndGet(now -> now.plus(Duration.ofMinutes(30)).plusSeconds(1));
        assertThat(socket.closed.await(8, TimeUnit.SECONDS)).isTrue();
        assertHandshakeDenied(browser.cookie, origin());
    }

    @ParameterizedTest @ValueSource(strings = {"issuer", "audience", "signature"})
    void rejectsBadIssuerAudienceAndSignature(String invalidClaim) throws Exception {
        var account = account("USER");
        var claims = JwtClaimsSet.builder().subject("uteexpress:user:" + account.id)
                .issuer(invalidClaim.equals("issuer") ? "foreign" : "uteexpress")
                .audience(List.of(invalidClaim.equals("audience") ? "foreign" : "uteexpress-web"))
                .issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(120))
                .claim("tokenVersion", 0L).build();
        String jwt = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        if (invalidClaim.equals("signature")) {
            var parts = jwt.split("\\.");
            jwt = parts[0] + "." + parts[1] + "." + (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        }
        assertHandshakeDenied("UTEEXPRESS_AUTH=" + jwt, origin());
    }

    @Test void vendorConfirmPublishesCommittedHistoryToBuyerOverRealSocket() throws Exception {
        var buyer = browser(account("USER")); var vendor = browser(account("VENDOR"));
        var socket = connected(buyer); subscribe(socket, buyer.account());
        long shop = jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Socket shop',?,'Pickup','APPROVED') RETURNING id",
                Long.class, vendor.account.id, UUID.randomUUID().toString());
        String key = UUID.randomUUID().toString();
        long order = jdbc.queryForObject("INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,receiver_name,phone,province_code,district,detail,subtotal,discount_total,shipping_fee,grand_total,commission_amount,commission_rate_snapshot) VALUES (?,?,'hash',?,?,'NEW','Receiver','0900000000','VN','District','Private street',100,0,0,100,0,0) RETURNING id",
                Long.class, key, key, buyer.account.id, shop);
        jdbc.update("INSERT INTO uteexpress.order_status_history(order_id,from_status,to_status,actor_id) VALUES (?,NULL,'NEW',?)", order, buyer.account.id);
        jdbc.update("INSERT INTO uteexpress.payments(order_id,method,status,amount,attempt_key) VALUES (?,'COD','UNPAID',100,?)", order, key);
        var response = http.send(HttpRequest.newBuilder(uri("/vendor/orders/" + order + "/confirm"))
                .header("Cookie", vendor.cookie).header("X-XSRF-TOKEN", vendor.csrf)
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString("{\"expectedVersion\":0}")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        String pushed = socket.next("MESSAGE");
        long notice = jdbc.queryForObject("SELECT id FROM uteexpress.notifications WHERE order_id=? AND recipient_id=?", Long.class, order, buyer.account.id);
        assertThat(pushed).contains("\"id\":" + notice).doesNotContain("0900000000", "Private street", "receiver");
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?", String.class, order)).isEqualTo("CONFIRMED");
    }

    @Test void accountLockProtectsDeliveryCallbackAgainstConcurrentAdminLock() throws Exception {
        var target = browser(account("USER")); var admin = browser(account("ADMIN"));
        var identity = socketIdentities.authenticate(target.account.jwt);
        var locked = new CountDownLatch(1); var release = new CountDownLatch(1);
        var pid = new java.util.concurrent.atomic.AtomicInteger();
        var pool = Executors.newFixedThreadPool(2);
        try {
            var delivery = pool.submit(() -> socketIdentities.runIfCurrent(identity, () -> {
                pid.set(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                locked.countDown();
                try { if (!release.await(12, TimeUnit.SECONDS)) throw new IllegalStateException("release timed out"); }
                catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted); }
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            var governance = pool.submit(() -> http.send(HttpRequest.newBuilder(uri("/admin/accounts/" + target.account.id + "/lock"))
                    .header("Cookie", admin.cookie).header("X-XSRF-TOKEN", admin.csrf)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("version=0")).build(), HttpResponse.BodyHandlers.ofString()));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            boolean blocked = false;
            while (System.nanoTime() < deadline) {
                blocked = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE ? = ANY(pg_blocking_pids(pid)))", Boolean.class, pid.get()));
                if (blocked) break;
                Thread.sleep(20);
            }
            assertThat(blocked).as("Governance waits for Account lock held through delivery callback").isTrue();
            assertThat(governance.isDone()).isFalse();
            release.countDown(); delivery.get(5, TimeUnit.SECONDS);
            assertThat(governance.get(5, TimeUnit.SECONDS).statusCode()).isIn(302, 303);
            assertThatThrownBy(() -> socketIdentities.runIfCurrent(identity, () -> fail("Revoked callback must not run")))
                    .isInstanceOf(RuntimeException.class);
        } finally { release.countDown(); pool.shutdownNow(); pool.awaitTermination(5, TimeUnit.SECONDS); }
    }

    @Test void outboundRechecksRevocationEvenBeforeIdleSweepRuns() throws Exception {
        var target = browser(account("USER")); var admin = browser(account("ADMIN"));
        var socket = connected(target); subscribe(socket, target.account());
        org.mockito.Mockito.doNothing().when(connections).sweep();
        var response = http.send(HttpRequest.newBuilder(uri("/admin/accounts/" + target.account.id + "/lock"))
                .header("Cookie", admin.cookie).header("X-XSRF-TOKEN", admin.csrf)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString("version=0")).build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isIn(302, 303);
        assertThat(socket.closed.getCount()).isEqualTo(1);
        new TransactionTemplate(manager).executeWithoutResult(status -> notifications.record(target.account.id,
                null, "SYSTEM", "Revoked", "Must not push", "outbound-revoked:" + UUID.randomUUID()));
        assertThat(socket.closed.await(8, TimeUnit.SECONDS)).isTrue();
        assertThat(socket.frames.stream()).noneMatch(frame -> frame.startsWith("MESSAGE"));
    }

    @Test void boundsConcurrentSessionsPerAccount() throws Exception {
        var browser = browser(account("USER"));
        for (int i = 0; i < 4; i++) connected(browser);
        var fifth = raw(browser.cookie, origin());
        assertThat(fifth.closed.await(8, TimeUnit.SECONDS)).isTrue();
    }

    private Account account(String role) {
        String name = "sock" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
        long id = jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?,'test','ACTIVE') RETURNING id",
                Long.class, name + "@test.invalid", name + "@test.invalid", name, name);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?", id, role);
        return new Account(id, tokens.issue(new UteExpressPrincipal(id, name, null, 0,
                List.of(new SimpleGrantedAuthority("ROLE_" + role)), true)));
    }
    private Browser browser(Account account) throws Exception {
        String cookie = "UTEEXPRESS_AUTH=" + account.jwt;
        var response = get("/notifications/realtime-session", cookie);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        String token = field(response.body(), "token");
        String csrfCookie = response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow().split(";", 2)[0];
        return new Browser(account, cookie + "; " + csrfCookie, token);
    }
    private HttpResponse<String> get(String path, String cookie) throws Exception {
        return http.send(HttpRequest.newBuilder(uri(path)).header("Cookie", cookie).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String field(String json, String name) {
        var matcher = java.util.regex.Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(json);
        assertThat(matcher.find()).isTrue(); return matcher.group(1);
    }
    private RawSocket raw(String cookie, String origin) throws Exception {
        var listener = new RawSocket();
        var builder = http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(5)).subprotocols("v12.stomp");
        if (!cookie.isEmpty()) builder.header("Cookie", cookie);
        if (origin != null) builder.header("Origin", origin);
        listener.socket = builder.buildAsync(URI.create("ws://localhost:" + port + "/ws"), listener).get(8, TimeUnit.SECONDS);
        opened.add(listener); return listener;
    }
    private RawSocket connected(Browser browser) throws Exception {
        var socket = raw(browser.cookie, origin()); socket.send(connectFrame(browser.csrf));
        socket.next("CONNECTED"); return socket;
    }
    private static String connectFrame(String csrf) { return "CONNECT\naccept-version:1.2\nheart-beat:0,0\nX-XSRF-TOKEN:" + csrf + "\n\n\0"; }
    private void subscribe(RawSocket socket, Account account) throws Exception {
        socket.send("SUBSCRIBE\nid:notifications\ndestination:/user/queue/notifications\nack:auto\n\n\0");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(6);
        while (System.nanoTime() < deadline) {
            var user = users.getUser("uteexpress:user:" + account.id);
            if (user != null) for (var session : user.getSessions()) {
                var accessor = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
                accessor.setDestination("/queue/notifications-user" + session.getId());
                if (!broker.getSubscriptionRegistry().findSubscriptions(MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders())).isEmpty()) return;
            }
            Thread.sleep(20);
        }
        fail("Private subscription did not reach broker");
    }
    private void assertHandshakeDenied(String cookie, String origin) {
        assertThatThrownBy(() -> raw(cookie, origin)).isInstanceOf(ExecutionException.class);
    }
    static class RawSocket implements WebSocket.Listener {
        WebSocket socket;
        final BlockingQueue<String> frames = new LinkedBlockingQueue<>();
        final CountDownLatch closed = new CountDownLatch(1);
        final StringBuilder buffer = new StringBuilder();
        @Override public void onOpen(WebSocket socket) { socket.request(1); }
        @Override public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
            synchronized (buffer) {
                buffer.append(data);
                int end;
                while ((end = buffer.indexOf("\0")) >= 0) {
                    frames.add(buffer.substring(0, end).stripLeading()); buffer.delete(0, end + 1);
                }
            }
            socket.request(1); return null;
        }
        @Override public CompletionStage<?> onClose(WebSocket socket, int status, String reason) { closed.countDown(); return null; }
        @Override public void onError(WebSocket socket, Throwable error) { closed.countDown(); }
        void send(String text) { socket.sendText(text, true).join(); }
        String next(String command) throws Exception {
            String frame = frames.poll(8, TimeUnit.SECONDS);
            assertThat(frame).isNotNull().startsWith(command); return frame;
        }
        void abort() { if (socket != null) socket.abort(); }
    }
}
