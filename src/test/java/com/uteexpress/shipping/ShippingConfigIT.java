package com.uteexpress.shipping;

import com.uteexpress.common.exception.*;
import com.uteexpress.shipping.dto.*;
import com.uteexpress.shipping.service.*;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import jakarta.validation.ConstraintViolationException;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties="uteexpress.demo.shipping.enabled=true")
@ActiveProfiles("demo") @AutoConfigureMockMvc @Testcontainers
class ShippingConfigIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES=new PostgreSQLContainer("postgres:17.6");
    @Autowired ShippingConfigService config;
    @Autowired ShippingQuoteService quotes;
    @Autowired ShippingDemoSeeder seeder;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;
    Long actor;
    String username;

    @BeforeEach void setup() {
        jdbc.update("DELETE FROM uteexpress.audit_logs");
        jdbc.update("DELETE FROM uteexpress.shipping_rates");
        jdbc.update("DELETE FROM uteexpress.shipping_providers");
        seeder.run(null);
        username="ship_"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        actor=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) "
                +"VALUES (?,?,?,?,?,'ACTIVE') RETURNING id",Long.class,username+"@example.test",username+"@example.test",
                username,username,passwords.encode("test-only-password"));
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code='ADMIN'",actor);
        authenticate(actor,"ADMIN");
    }
    @AfterEach void clearSecurity() { SecurityContextHolder.clearContext(); }
    void authenticate(Long id,String role) {
        var authorities=List.of(new SimpleGrantedAuthority("ROLE_"+role));
        var principal=new UteExpressPrincipal(id,username,null,0,authorities,true);
        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(principal,null,authorities));
    }
    Long demoId() { return jdbc.queryForObject("SELECT id FROM uteexpress.shipping_providers WHERE code='DEMO_SHIP'",Long.class); }
    ShippingQuoteCommand command(Long id,String service,String region) { return new ShippingQuoteCommand(1L,id,service,region,"Demo district","Demo address"); }
    ShippingRateRequest request(Long id,String service,String region,String fee,Long version) {
        return new ShippingRateRequest(id,service,region,new BigDecimal(fee),true,version);
    }
    @Test void returnsDatabaseFeeAndVersionAndNeverFallsBack() {
        var quote=quotes.quote(command(demoId(),"STANDARD","DEMO_REGION"));
        assertThat(quote.shippingFee()).isEqualByComparingTo("30000.00");
        assertThat(quote.shippingFee().scale()).isEqualTo(2);
        assertThat(quote.rateVersion()).isZero();
        for(var command:List.of(command(demoId(),"UNKNOWN","DEMO_REGION"),command(demoId(),"STANDARD","UNKNOWN"),
                command(demoId(),"STANDARD","DEMO_DISABLED"),command(Long.MAX_VALUE,"STANDARD","DEMO_REGION"))) {
            assertThatThrownBy(() -> quotes.quote(command)).isInstanceOfSatisfying(ApplicationException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        }
    }
    @Test void disabledProviderBlocksAllRatesAndCanBeReenabled() {
        Long id=demoId();
        var provider=config.provider(id);
        config.disableProvider(id,provider.version());
        assertThatThrownBy(() -> quotes.quote(command(id,"STANDARD","DEMO_REGION"))).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> config.createRate(request(id,"NEW","DEMO_REGION","100",null))).isInstanceOf(ApplicationException.class);
        var disabled=config.provider(id);
        config.updateProvider(id,new ShippingProviderRequest(disabled.code(),disabled.name(),true,disabled.version()));
        assertThat(quotes.quote(command(id,"STANDARD","DEMO_REGION")).shippingFee()).isEqualByComparingTo("30000");
    }
    @Test void wholeDongValidationDuplicateRouteAndStaleVersion() {
        Long id=demoId();
        for(String fee:List.of("-1","1.5","100000000000000000")) {
            assertThatThrownBy(() -> config.createRate(request(id,"NEW","DEMO_REGION",fee,null))).isInstanceOf(ConstraintViolationException.class);
        }
        var rate=config.createRate(request(id,"FREE","DEMO_REGION","0.00",null));
        assertThat(quotes.quote(command(id,"FREE","DEMO_REGION")).shippingFee()).isEqualByComparingTo("0");
        assertThatThrownBy(() -> config.createRate(request(id,"FREE","DEMO_REGION","100",null))).isInstanceOfSatisfying(ApplicationException.class,
                e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        config.updateRate(rate.id(),request(id,"FREE","DEMO_REGION","100",rate.version()));
        assertThat(quotes.quote(command(id,"FREE","DEMO_REGION")).rateVersion()).isEqualTo(1L);
        assertThatThrownBy(() -> config.disableRate(rate.id(),rate.version())).isInstanceOf(ApplicationException.class);
        config.disableRate(rate.id(),1L);
        assertThatThrownBy(() -> quotes.quote(command(id,"FREE","DEMO_REGION"))).isInstanceOf(ApplicationException.class);
    }
    @Test void routeKeysAndProviderCodesCannotBeReassigned() {
        var rate=config.createRate(request(demoId(),"NEW","DEMO_REGION","100",null));
        assertThatThrownBy(() -> config.updateRate(rate.id(),request(demoId(),"OTHER","DEMO_REGION","100",0L))).isInstanceOf(ApplicationException.class);
        var provider=config.provider(demoId());
        assertThatThrownBy(() -> config.updateProvider(provider.id(),new ShippingProviderRequest("OTHER","Name",true,provider.version())))
                .isInstanceOf(ApplicationException.class);
    }
    @Test void auditActorAndAtomicRollbackAreEnforced() {
        var p=config.createProvider(new ShippingProviderRequest("TEST","Test",true,null));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE actor_id=? AND target_type='SHIPPING_PROVIDER' AND target_id=?",
                Integer.class,actor,p.id())).isEqualTo(1);
        authenticate(Long.MAX_VALUE,"ADMIN");
        assertThatThrownBy(() -> config.createProvider(new ShippingProviderRequest("ROLLBACK","Test",true,null)))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.shipping_providers WHERE code='ROLLBACK'",Integer.class)).isZero();
    }
    @Test void methodAuthorizationAndInvalidCommandValidation() {
        for(String role:List.of("USER","VENDOR","SHIPPER")) {
            authenticate(actor,role);
            assertThatThrownBy(() -> config.providers(0)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> config.createProvider(new ShippingProviderRequest("OTHER","Other",true,null))).isInstanceOf(AccessDeniedException.class);
        }
        assertThatThrownBy(() -> quotes.quote(null)).isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> quotes.quote(new ShippingQuoteCommand(-1L,demoId(),"STANDARD","DEMO_REGION","District","Detail")))
                .isInstanceOf(ApplicationException.class);
        assertThatThrownBy(() -> quotes.quote(new ShippingQuoteCommand(1L,demoId(),"STANDARD","DEMO_REGION"," ","Detail")))
                .isInstanceOf(ApplicationException.class);
    }
    @Test void seedRerunPreservesEditsAndDoesNotDuplicateAudits() {
        jdbc.update("UPDATE uteexpress.shipping_rates SET fee=12345,active=false,version=7 WHERE provider_id=? AND service_code='STANDARD' AND destination_region='DEMO_REGION'",demoId());
        seeder.run(null); seeder.run(null);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.shipping_providers",Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.shipping_rates",Integer.class)).isEqualTo(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE reason='DEMO_SHIP00_V1'",Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT fee FROM uteexpress.shipping_rates WHERE provider_id=? AND service_code='STANDARD' AND destination_region='DEMO_REGION'",
                BigDecimal.class,demoId())).isEqualByComparingTo("12345");
    }
    @Test void databaseConstraintsAndMigrations() {
        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThatThrownBy(() -> jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee) VALUES (?,'BAD','DEMO',1.5)",demoId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee) VALUES (?,'BAD','DEMO',-1)",demoId()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM uteexpress.shipping_providers WHERE id=?",demoId())).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void realJwtLoginCanCreateProviderAndAuditUsesLoggedInId() throws Exception {
        SecurityContextHolder.clearContext();
        var result=mvc.perform(post("/login").with(csrf()).param("identifier",username).param("password","test-only-password"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var cookie=result.getResponse().getCookie("UTEEXPRESS_AUTH");
        assertThat(cookie).isNotNull();
        mvc.perform(post("/admin/shipping/providers").with(csrf()).cookie(cookie)
                .param("code","HTTP").param("name","HTTP Provider").param("active","true").param("actorId","999"))
                .andExpect(redirectedUrl("/admin/shipping/providers"));
        assertThat(jdbc.queryForObject("SELECT a.actor_id FROM uteexpress.audit_logs a JOIN uteexpress.shipping_providers p ON p.id=a.target_id "
                +"WHERE a.target_type='SHIPPING_PROVIDER' AND p.code='HTTP'",Long.class)).isEqualTo(actor);
        mvc.perform(get("/admin/shipping/providers").cookie(cookie)).andExpect(status().isOk());
    }
}
