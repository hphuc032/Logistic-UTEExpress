package com.uteexpress.integration;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

/** Real HTTP headers/cookies and rendered CSRF fields, rather than param-only MockMvc requests. */
@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,properties={
        "uteexpress.security.jwt.cookie-secure=false","management.health.mail.enabled=false"})
@Testcontainers
class PromotionBrowserFormsIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Value("${local.server.port}") int port;
    final String password=UUID.randomUUID()+"aA1!";
    Fixture owner,other;
    Browser user;
    record Fixture(long account,long shop,long product,Browser browser) { }

    @BeforeEach void fixture() throws Exception { owner=vendor();other=vendor();user=account("USER").browser(); }

    @Test void realVoucherCreateAndUpdateAcceptNormalTransportHeaders() throws Exception {
        var fields=voucher();String code=fields.get("code");
        assertRedirect(owner.browser().post("/vendor/vouchers",fields));
        long id=jdbc.queryForObject("SELECT id FROM uteexpress.vouchers WHERE shop_id=? AND code=?",Long.class,owner.shop(),code);
        fields.put("version",jdbc.queryForObject("SELECT version FROM uteexpress.vouchers WHERE id=?",Long.class,id).toString());
        fields.put("value","2000");
        assertRedirect(owner.browser().post("/vendor/vouchers/"+id+"/update",fields));
        assertThat(jdbc.queryForObject("SELECT value FROM uteexpress.vouchers WHERE id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("2000");
    }
    @Test void realPromotionCreateAndUpdateAcceptNormalTransportHeaders() throws Exception {
        var fields=promotion();String path="/vendor/products/"+owner.product()+"/promotions";
        assertRedirect(owner.browser().post(path,fields));
        long id=jdbc.queryForObject("SELECT id FROM uteexpress.promotions WHERE product_id=?",Long.class,owner.product());
        fields.put("version",jdbc.queryForObject("SELECT version FROM uteexpress.promotions WHERE id=?",Long.class,id).toString());fields.put("discountPercent","12");
        assertRedirect(owner.browser().post(path+"/"+id+"/update",fields));
        assertThat(jdbc.queryForObject("SELECT discount_percent FROM uteexpress.promotions WHERE id=?",java.math.BigDecimal.class,id)).isEqualByComparingTo("12");
    }
    @Test void forgedFormOwnershipAndPathIdentifiersRemainRejected() throws Exception {
        var voucher=voucher();voucher.put("shopId",Long.toString(other.shop()));
        assertThat(owner.browser().post("/vendor/vouchers",voucher).statusCode()).isEqualTo(200);
        var promotion=promotion();promotion.put("productId",Long.toString(other.product()));
        assertThat(owner.browser().post("/vendor/products/"+owner.product()+"/promotions",promotion).statusCode()).isEqualTo(200);
        assertThat(other.browser().post("/vendor/products/"+owner.product()+"/promotions",promotion()).statusCode()).isEqualTo(404);
        assertNoWrites();
    }
    @Test void realPromotionDisableAcceptsHeadersAndRetainsOwnershipCsrfAndFieldValidation() throws Exception {
        String path="/vendor/products/"+owner.product()+"/promotions";
        assertRedirect(owner.browser().post(path,promotion()));
        long id=jdbc.queryForObject("SELECT id FROM uteexpress.promotions WHERE product_id=?",Long.class,owner.product());
        String disable=path+"/"+id+"/disable";
        var fields=Map.of("version",jdbc.queryForObject("SELECT version FROM uteexpress.promotions WHERE id=?",Long.class,id).toString());
        assertThat(other.browser().post(disable,fields).statusCode()).isEqualTo(404);
        assertThat(user.post(disable,fields).statusCode()).isEqualTo(403);
        assertThat(owner.browser().post(disable,fields,false,Map.of()).statusCode()).isEqualTo(403);
        assertThat(owner.browser().post(disable,Map.of(),true,Map.of("version",fields.get("version"))).statusCode()).isEqualTo(400);
        assertThat(owner.browser().post(disable,Map.of("version",fields.get("version"),"discountPercent","25")).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT active FROM uteexpress.promotions WHERE id=?",Boolean.class,id)).isTrue();
        assertRedirect(owner.browser().post(disable,fields));
        assertThat(jdbc.queryForMap("SELECT active,product_id,discount_percent FROM uteexpress.promotions WHERE id=?",id))
                .containsEntry("active",false).containsEntry("product_id",owner.product());
        assertThat(jdbc.queryForObject("SELECT discount_percent FROM uteexpress.promotions WHERE id=?",java.math.BigDecimal.class,id))
                .isEqualByComparingTo("10");
        assertThat(owner.browser().post(disable,fields).statusCode()).isEqualTo(409);
    }
    @Test void csrfAndRoleChecksStillRunBeforeBusinessMutation() throws Exception {
        assertThat(owner.browser().post("/vendor/vouchers",voucher(),false,Map.of()).statusCode()).isEqualTo(403);
        assertThat(user.post("/vendor/vouchers",voucher()).statusCode()).isEqualTo(403);
        assertNoWrites();
    }
    @Test void headersCannotSupplyMissingBusinessFormFields() throws Exception {
        var fields=voucher();fields.remove("code");
        assertThat(owner.browser().post("/vendor/vouchers",fields,true,Map.of("code","HEADER_ONLY_CODE")).statusCode()).isEqualTo(200);
        var promo=promotion();promo.remove("name");
        assertThat(owner.browser().post("/vendor/products/"+owner.product()+"/promotions",promo,true,Map.of("name","Header only promotion")).statusCode()).isEqualTo(200);
        assertNoWrites();
    }
    private void assertNoWrites() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.vouchers WHERE shop_id=?",Integer.class,owner.shop())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.promotions WHERE product_id=?",Integer.class,owner.product())).isZero();
    }
    private void assertRedirect(HttpResponse<String> response) {assertThat(response.statusCode()).isIn(302,303);}
    private Map<String,String> voucher() {
        var fields=window();fields.putAll(Map.of("code","BROWSER_"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT),"type","FIXED","value","1000","minSubtotal","0","totalLimit","10","perUserLimit","2"));return fields;
    }
    private Map<String,String> promotion() {var fields=window();fields.putAll(Map.of("name","Browser promotion","discountPercent","10"));return fields;}
    private Map<String,String> window() {
        var now=LocalDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"));var fmt=DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
        return new LinkedHashMap<>(Map.of("startsAt",now.minusDays(1).format(fmt),"endsAt",now.plusDays(1).format(fmt),"active","true"));
    }
    private Fixture account(String role) throws Exception {
        String name="browser_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
        long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES(?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id",Long.class,name+"@example.test",name+"@example.test",name,name,passwords.encode(password));
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role);
        var browser=new Browser();var login=browser.post("/login",Map.of("identifier",name,"password",password));assertRedirect(login);
        return new Fixture(id,0,0,browser);
    }
    private Fixture vendor() throws Exception {
        var a=account("VENDOR");String slug="browser-"+a.account();
        long shop=jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES(?,'Browser shop',?,'Synthetic pickup','APPROVED') RETURNING id",Long.class,a.account(),slug);
        long category=jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug) VALUES('Browser category',?) RETURNING id",Long.class,slug);
        long product=jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES(?,?,'Browser product',100000,10,'ACTIVE') RETURNING id",Long.class,shop,category);
        return new Fixture(a.account(),shop,product,a.browser());
    }
    class Browser {
        final HttpClient client=HttpClient.newBuilder().cookieHandler(new CookieManager(null,CookiePolicy.ACCEPT_ALL)).connectTimeout(Duration.ofSeconds(5)).build();
        URI uri(String path) {return URI.create("http://localhost:"+port+path);}
        String token() throws Exception {
            var page=client.send(HttpRequest.newBuilder(uri("/login")).timeout(Duration.ofSeconds(10)).GET().build(),HttpResponse.BodyHandlers.ofString());
            var matcher=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(page.body());assertThat(matcher.find()).isTrue();return matcher.group(1);
        }
        HttpResponse<String> post(String path,Map<String,String> fields) throws Exception {return post(path,fields,true,Map.of());}
        HttpResponse<String> post(String path,Map<String,String> fields,boolean csrf,Map<String,String> headers) throws Exception {
            var values=new LinkedHashMap<>(fields);if(csrf) values.put("_csrf",token());
            String body=values.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));
            var builder=HttpRequest.newBuilder(uri(path)).timeout(Duration.ofSeconds(15)).header("Content-Type","application/x-www-form-urlencoded").header("User-Agent","QA03 browser-form regression").POST(HttpRequest.BodyPublishers.ofString(body));headers.forEach(builder::header);
            return client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
        }
    }
}
