package com.uteexpress.security;

import jakarta.servlet.http.Cookie;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.*;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real DB/JWT and rendered cookie/masked tokens; no csrf() or mock authenticated principals. */
@SpringBootTest(properties="management.health.mail.enabled=false")
@AutoConfigureMockMvc @Testcontainers @Import(Qa01CookieCsrfIT.UnsafeFixture.class)
class Qa01CookieCsrfIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.6");
    static final String PASSWORD="Qa01BrowserPassword1!";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired UnsafeFixture fixture;
    @BeforeEach void clear(){fixture.calls.set(0);}

    @Test void anotherBrowserTokenOrCookieAloneCannotMutateAndOwnMaskedTokenEscapesPlainText() throws Exception {
        Browser victim=browser("USER"),other=browser("USER");
        String ownToken=victim.formToken(),otherToken=other.formToken();
        var before=users();
        victim.send(post("/user/profile").param("fullName","Untrusted update").param("_csrf",otherToken))
                .andExpect(status().isForbidden());
        victim.send(post("/user/profile").param("fullName","Untrusted update"))
                .andExpect(status().isForbidden());
        assertThat(users()).isEqualTo(before);
        String text="<script>qa01()</script>";
        victim.send(post("/user/profile").param("fullName",text).param("phone","0900000000").param("_csrf",ownToken)
                .param("userId",Long.toString(other.id)).param("roles","ADMIN").param("tokenVersion","900"))
                .andExpect(status().is3xxRedirection());
        assertThat(jdbc.queryForObject("SELECT full_name FROM uteexpress.users WHERE id=?",String.class,victim.id)).isEqualTo(text);
        assertThat(jdbc.queryForObject("SELECT token_version FROM uteexpress.users WHERE id=?",Long.class,victim.id)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.user_roles ur JOIN uteexpress.roles r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code='ADMIN'",Long.class,victim.id)).isZero();
        assertThat(jdbc.queryForObject("SELECT full_name FROM uteexpress.users WHERE id=?",String.class,other.id)).isNull();
        victim.send(get("/user/profile")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;qa01()&lt;/script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(text))));
    }

    @Test void flashSessionCannotRestoreAuthenticationWithoutTheJwtCookie() throws Exception {
        Browser buyer=browser("USER");String token=buyer.formToken();
        var changed=buyer.send(post("/user/profile").param("fullName","QA buyer").param("_csrf",token))
                .andExpect(status().is3xxRedirection()).andReturn();
        var flashSession=(MockHttpSession)changed.getRequest().getSession(false);
        assertThat(flashSession).isNotNull();
        assertThat(flashSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)).isNull();
        mvc.perform(get("/user/profile").session(flashSession).secure(true)).andExpect(status().isUnauthorized());
        buyer.send(get("/user/profile")).andExpect(status().isOk());
    }

    @ParameterizedTest @ValueSource(strings={"PATCH","DELETE"})
    void unsafeAjaxMethodsRequireTheRenderedMaskedHeader(String method) throws Exception {
        Browser buyer=browser("USER");String token=buyer.formToken();var before=users();
        buyer.send(request(HttpMethod.valueOf(method),"/test-fixtures/qa01/unsafe")).andExpect(status().isForbidden());
        buyer.send(request(HttpMethod.valueOf(method),"/test-fixtures/qa01/unsafe").header("X-XSRF-TOKEN","invalid"))
                .andExpect(status().isForbidden());
        assertThat(fixture.calls).hasValue(0);assertThat(users()).isEqualTo(before);
        buyer.send(request(HttpMethod.valueOf(method),"/test-fixtures/qa01/unsafe").header("X-XSRF-TOKEN",token))
                .andExpect(status().isOk());
        assertThat(fixture.calls).hasValue(1);assertThat(users()).isEqualTo(before);
    }

    @ParameterizedTest @CsvSource({
        "GUEST,/register","GUEST,/verify-otp","GUEST,/verify-otp/resend",
        "GUEST,/forgot-password","GUEST,/reset-password","USER,/logout",
        "USER,/user/profile","USER,/user/shop/register","USER,/orders/1/return",
        "MANAGER,/manager/categories","SHIPPER,/shipper/orders/1/delivery-failed"})
    void representativeIdentityShopOrderGovernanceAndShippingCommandsRejectCookieOnlyCsrf(String role,String path) throws Exception {
        Browser actor=role.equals("GUEST")?new Browser(0):browser(role);
        actor.formToken(); // The browser has a real CSRF cookie; cookie alone is insufficient.
        var before=users();
        var counts=jdbc.queryForMap("SELECT (SELECT count(*) FROM uteexpress.orders) orders,(SELECT count(*) FROM uteexpress.shops) shops,(SELECT count(*) FROM uteexpress.otp_tokens) otp,(SELECT count(*) FROM uteexpress.audit_logs) audit");
        actor.send(post(path)).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        assertThat(users()).isEqualTo(before);
        assertThat(jdbc.queryForMap("SELECT (SELECT count(*) FROM uteexpress.orders) orders,(SELECT count(*) FROM uteexpress.shops) shops,(SELECT count(*) FROM uteexpress.otp_tokens) otp,(SELECT count(*) FROM uteexpress.audit_logs) audit")).isEqualTo(counts);
    }

    private List<Map<String,Object>> users(){return jdbc.queryForList("SELECT id,full_name,phone,status,token_version,version FROM uteexpress.users ORDER BY id");}
    private Browser browser(String role) throws Exception {
        String name="qac"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?,?,'ACTIVE') RETURNING id",
                Long.class,name+"@example.test",name+"@example.test",name,name,passwords.encode(PASSWORD));
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role);
        var browser=new Browser(id);String csrf=browser.formToken();
        var loggedIn=browser.send(post("/login").param("identifier",name).param("password",PASSWORD).param("_csrf",csrf))
                .andExpect(status().is3xxRedirection()).andReturn();
        assertThat(loggedIn.getResponse().getHeaders("Set-Cookie")).anySatisfy(header ->
                assertThat(header).contains("UTEEXPRESS_AUTH=","Secure","HttpOnly","SameSite=Lax"));
        return browser;
    }
    class Browser {
        final long id;final Map<String,Cookie> cookies=new HashMap<>();
        Browser(long id){this.id=id;}
        ResultActions send(MockHttpServletRequestBuilder request) throws Exception {
            request.secure(true);if(!cookies.isEmpty())request.cookie(cookies.values().toArray(Cookie[]::new));
            var result=mvc.perform(request);
            for(var cookie:result.andReturn().getResponse().getCookies()) {
                if(cookie.getMaxAge()==0) cookies.remove(cookie.getName());else cookies.put(cookie.getName(),cookie);
            }
            return result;
        }
        String formToken() throws Exception {
            String html=send(get("/login")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            var match=Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"").matcher(html);
            assertThat(match.find()).as("actual rendered CSRF field").isTrue();return match.group(1);
        }
    }
    /** Test-only handler proves PATCH/DELETE admission, not a production business operation. */
    @RestController static class UnsafeFixture {
        final AtomicInteger calls=new AtomicInteger();
        @RequestMapping(path="/test-fixtures/qa01/unsafe",method={RequestMethod.PATCH,RequestMethod.DELETE})
        String mutate(){calls.incrementAndGet();return "ok";}
    }
}
