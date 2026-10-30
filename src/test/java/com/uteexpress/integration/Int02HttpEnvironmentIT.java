package com.uteexpress.integration;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

/** Real embedded HTTP + isolated clean PostgreSQL. Deliberately not browser/responsive evidence. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "uteexpress.security.jwt.cookie-secure=false","management.health.mail.enabled=false"})
@Testcontainers
class Int02HttpEnvironmentIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.6");
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired Flyway flyway;

    @ParameterizedTest @ValueSource(strings={"USER","VENDOR","ADMIN","MANAGER","SHIPPER"})
    void cleanMigratedApplicationServesRealAuthenticatedRoleJourneysAndLogout(String role) throws Exception {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        String username="http02_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        String password=UUID.randomUUID().toString(); // Synthetic per-invocation credential, never a report field.
        long id=jdbc.queryForObject("""
                INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at)
                VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id
                """,Long.class,username+"@example.test",username+"@example.test",username,username,passwords.encode(password));
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role);
        if(role.equals("VENDOR")) jdbc.update("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'HTTP Shop',?,'Pickup','APPROVED')",id,username.replace('_','-'));
        var client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL))
                .connectTimeout(Duration.ofSeconds(5)).build();
        assertThat(get(client,"/actuator/health").body()).contains("UP");
        assertThat(get(client,"/products").statusCode()).isEqualTo(200);
        assertThat(get(client,"/css/app.css").statusCode()).isEqualTo(200);
        assertThat(get(client,"/orders").statusCode()).isEqualTo(401);
        var login=get(client,"/login");
        String csrf=capture(login.body(),"name=\"_csrf\"[^>]*value=\"([^\"]+)\"");
        String form="identifier="+enc(username)+"&password="+enc(password)+"&_csrf="+enc(csrf);
        var authenticated=client.send(HttpRequest.newBuilder(uri("/login")).timeout(Duration.ofSeconds(8))
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(authenticated.statusCode()).isIn(302,303);
        assertThat(authenticated.headers().allValues("set-cookie").stream()
                .anyMatch(h -> h.startsWith("UTEEXPRESS_AUTH=") && h.contains("HttpOnly") && h.contains("SameSite=Lax"))).isTrue();
        List<String> pages=switch(role) {
            case "USER" -> List.of("/user/profile","/user/cart/view","/user/checkout/view","/orders","/notifications");
            case "VENDOR" -> List.of("/vendor/products","/vendor/orders","/vendor/dashboard","/notifications");
            case "ADMIN" -> List.of("/admin/dashboard","/admin/dashboard/revenue","/admin/audit-logs","/admin/orders","/notifications");
            case "MANAGER" -> List.of("/manager/dashboard","/manager/orders","/notifications");
            default -> List.of("/shipper/dashboard","/shipper/shipments/view","/notifications");
        };
        for(String path:pages) {
            var page=get(client,path);
            assertThat(page.statusCode()).as(role+" "+path).isEqualTo(200);
            assertThat(page.body()).as(path).contains("UTEExpress").doesNotContain("password_hash","org.postgresql.util","stackTrace");
        }
        if(role.equals("MANAGER")) {
            assertThat(get(client,"/admin/dashboard/revenue").statusCode()).isEqualTo(403);
            assertThat(get(client,"/admin/audit-logs").statusCode()).isEqualTo(403);
            assertThat(get(client,"/manager/dashboard").body()).doesNotContain("/admin/dashboard/revenue");
        }
        String token=capture(get(client,"/notifications/realtime-session").body(),"\"token\"\\s*:\\s*\"([^\"]+)\"");
        var logout=client.send(HttpRequest.newBuilder(uri("/logout")).timeout(Duration.ofSeconds(8))
                .header("X-XSRF-TOKEN",token).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofString());
        assertThat(logout.statusCode()).isIn(302,303);
        assertThat(get(client,"/notifications/inbox").statusCode()).isEqualTo(401);
        assertThat(jdbc.queryForObject("SELECT token_version FROM uteexpress.users WHERE id=?",Long.class,id)).isEqualTo(1);
    }

    private URI uri(String path) { return URI.create("http://localhost:"+port+path); }
    private HttpResponse<String> get(HttpClient client,String path) throws Exception {
        String accept=path.equals("/actuator/health") || path.endsWith("/realtime-session") || path.endsWith("/inbox")
                ? "application/json" : path.endsWith(".css") ? "*/*" : "text/html";
        return client.send(HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(8)).header("Accept",accept).GET().build(),HttpResponse.BodyHandlers.ofString());
    }
    private static String capture(String body,String expression) {
        var matcher=Pattern.compile(expression).matcher(body);
        assertThat(matcher.find()).as("expected CSRF bootstrap field, without echoing secret content").isTrue();
        return matcher.group(1);
    }
    private static String enc(String text) { return URLEncoder.encode(text,StandardCharsets.UTF_8); }
}
