package com.uteexpress.promotion;

import com.uteexpress.common.exception.*;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.service.PlatformVoucherManagementService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.util.HtmlUtils;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class PlatformVoucherManagementIT {
    @Container @ServiceConnection
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformVoucherManagementService management;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired com.uteexpress.identity.service.AccountIdentityService identities;
    long admin, vendor, buyer, shop, ownVoucher, shopVoucher;
    String code, shopCode;
    @BeforeEach void fixture() {
        admin = account("ADMIN"); vendor = account("VENDOR"); buyer = account("USER");
        shop = jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop',?,'Pickup','APPROVED') RETURNING id", Long.class, vendor, UUID.randomUUID().toString());
        code = "ADMIN_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        shopCode = "SHOP_" + UUID.randomUUID().toString().replace("-", "").toUpperCase(Locale.ROOT);
        ownVoucher = voucher(code, "PLATFORM", null);
        shopVoucher = voucher(shopCode, "SHOP", shop);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test void adminListDetailNewAndEditRenderOnlyPlatformWithCsrfAndVersion() throws Exception {
        mvc.perform(get("/admin/vouchers").with(user(principal(admin, "ADMIN"))))
                .andExpect(status().isOk()).andExpect(content().string(containsString(code)))
                .andExpect(content().string(not(containsString(shopCode))))
                .andExpect(content().string(not(containsString("Chưa có voucher"))))
                .andExpect(content().string(containsString("/admin/vouchers/" + ownVoucher + "/disable")))
                .andExpect(content().string(containsString("name=\"_csrf\"")));
        for (String path : List.of("/new", "/" + ownVoucher, "/" + ownVoucher + "/edit", "/" + ownVoucher + "/disable"))
            mvc.perform(get("/admin/vouchers" + path).with(user(principal(admin, "ADMIN"))))
                    .andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")));
    }
    @Test void createCanonicalizesAndDerivesScopeShopAndActorWithAtomicAudit() throws Exception {
        String next = code.toLowerCase(Locale.ROOT) + "_NEW";
        mvc.perform(valid(post("/admin/vouchers"), next).with(csrf()).with(user(principal(admin, "ADMIN"))))
                .andExpect(status().is3xxRedirection());
        var v = jdbc.queryForMap("SELECT * FROM uteexpress.vouchers WHERE code=?", next.toUpperCase(Locale.ROOT));
        assertThat(v).containsEntry("scope", "PLATFORM").containsEntry("shop_id", null).containsEntry("created_by", admin);
        assertThat(jdbc.queryForObject("SELECT starts_at AT TIME ZONE 'UTC' FROM uteexpress.vouchers WHERE id=?", LocalDateTime.class, v.get("id")))
                .isEqualTo(LocalDateTime.of(2020, 1, 1, 17, 0));
        assertThat(audits((Long)v.get("id"))).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"MANAGER","VENDOR","USER","SHIPPER","ANONYMOUS"})
    void everyRouteRejectsOtherRoles(String role) throws Exception {
        for (var request : List.of(get("/admin/vouchers"), get("/admin/vouchers/new"), get(path(ownVoucher,"")), get(path(ownVoucher,"/edit")),
                valid(post("/admin/vouchers"), code + "_NEW"), valid(post(path(ownVoucher,"/update")), code).param("version","0"),
                post(path(ownVoucher,"/disable")).param("version","0"))) {
            request.with(csrf());
            if (!role.equals("ANONYMOUS")) request.with(user(principal(admin, role)));
            mvc.perform(request).andExpect(role.equals("ANONYMOUS") ? status().isUnauthorized() : status().isForbidden());
        }
        assertThat(row(ownVoucher)).containsEntry("version",0L).containsEntry("active",true);
    }
    @ParameterizedTest @ValueSource(strings={"MANAGER","VENDOR","USER","SHIPPER"})
    void methodSecurityRejectsDirectServiceInvocation(String role) {
        authenticate(admin,role);
        assertThatThrownBy(management::list).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @ParameterizedTest @ValueSource(strings={"LOCKED","DISABLED","PENDING_VERIFICATION","REVOKED"})
    void stalePrincipalCannotAuthorizePersistedInactiveOrRevokedAdmin(String state) {
        authenticate(admin,"ADMIN");
        if (state.equals("REVOKED")) jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?", admin);
        else jdbc.update("UPDATE uteexpress.users SET status=? WHERE id=?",state,admin);
        assertThatThrownBy(() -> management.create(createForm())).isInstanceOfSatisfying(ApplicationException.class,
                error -> assertThat(error.errorCode()).isEqualTo(ErrorCode.ACCESS_DENIED));
        assertThat(audits(ownVoucher)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"create","update","disable"})
    void missingAndInvalidCsrfRejectEveryMutation(String operation) throws Exception {
        for (boolean invalid : List.of(false,true)) {
            var request = valid(post(operation.equals("create") ? "/admin/vouchers" : path(ownVoucher,"/"+operation)),code).param("version","0")
                    .with(user(principal(admin,"ADMIN")));
            if (invalid) request.with(csrf().useInvalidToken());
            mvc.perform(request).andExpect(status().isForbidden());
        }
        assertThat(row(ownVoucher)).containsEntry("version",0L);
    }
    @ParameterizedTest @ValueSource(strings={"scope","shopId","createdBy","actorId","ownerId","usageCount","used","orderId","discountAmount","id"})
    void forgedFieldsCannotCreateOrUpdateOrDisable(String field) throws Exception {
        for (String operation : List.of("create","update","disable")) {
            var request = operation.equals("disable") ? post(path(ownVoucher,"/disable"))
                    : valid(post(operation.equals("create") ? "/admin/vouchers" : path(ownVoucher,"/update")),code+"_NEW");
            request.param("version","0").param(field,field.equals("scope") ? "SHOP" : "999999");
            var result = mvc.perform(request.with(csrf()).with(user(principal(admin,"ADMIN")))).andReturn();
            assertThat(result.getResponse().getStatus()).isIn(200,400);
            assertThat(result.getModelAndView()).isNotNull();
            assertThat(row(ownVoucher)).containsEntry("version",0L).containsEntry("active",true);
        }
    }
    @ParameterizedTest @CsvSource({"code,bad code","code,''","type,INVALID","value,0","value,101","value,garbage","maxDiscount,-1","maxDiscount,0.5","minSubtotal,-1","minSubtotal,0.5","totalLimit,0","perUserLimit,0","endsAt,2019-01-01T00:00","startsAt,invalid"})
    void invalidDefinitionDoesNotPersist(String field,String value) throws Exception {
        var request=valid(post("/admin/vouchers"),code+"_NEW"); override(request,field,value);
        mvc.perform(request.with(csrf()).with(user(principal(admin,"ADMIN")))).andExpect(status().isOk())
                .andExpect(model().attributeHasErrors("voucherForm"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.vouchers WHERE code=?",Long.class,code+"_NEW")).isZero();
    }
    @Test void duplicateAcrossShopScopeAndInvalidFixedAmountRejected() throws Exception {
        mvc.perform(valid(post("/admin/vouchers"),shopCode.toLowerCase(Locale.ROOT)).with(csrf()).with(user(principal(admin,"ADMIN"))))
                .andExpect(status().isOk()).andExpect(model().attributeHasErrors("voucherForm"));
        var fixed=valid(post("/admin/vouchers"),code+"_NEW"); override(fixed,"type","FIXED");override(fixed,"value","1.5");
        mvc.perform(fixed.with(csrf()).with(user(principal(admin,"ADMIN")))).andExpect(status().isOk()).andExpect(model().attributeHasErrors("voucherForm"));
    }
    @Test void unusedUpdateAndDisableRequireFreshVersionsAndAuditOnlyCommittedChanges() throws Exception {
        var request=valid(post(path(ownVoucher,"/update")),code+"_EDIT").param("version","0"); override(request,"value","20");
        mvc.perform(request.with(csrf()).with(user(principal(admin,"ADMIN")))).andExpect(status().is3xxRedirection());
        assertThat(row(ownVoucher)).containsEntry("version",1L);
        mvc.perform(valid(post(path(ownVoucher,"/update")),code).param("version","0").with(csrf()).with(user(principal(admin,"ADMIN"))))
                .andExpect(model().attributeHasErrors("voucherForm"));
        mvc.perform(post(path(ownVoucher,"/disable")).param("version","0").with(csrf()).with(user(principal(admin,"ADMIN"))))
                .andExpect(status().isConflict());
        mvc.perform(post(path(ownVoucher,"/disable")).param("version","1").with(csrf()).with(user(principal(admin,"ADMIN"))))
                .andExpect(status().is3xxRedirection());
        assertThat(row(ownVoucher)).containsEntry("active",false).containsEntry("version",2L);
        assertThat(audits(ownVoucher)).isEqualTo(2);
        mvc.perform(post(path(ownVoucher,"/disable")).param("version","2").with(csrf()).with(user(principal(admin,"ADMIN"))))
                .andExpect(status().is3xxRedirection());
        assertThat(audits(ownVoucher)).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"","/edit","/update","/disable"})
    void shopResourceCannotBeManagedThroughPlatform(String suffix) throws Exception {
        var request=suffix.equals("/update") ? valid(post(path(shopVoucher,suffix)),shopCode).param("version","0")
                : suffix.equals("/disable") ? post(path(shopVoucher,suffix)).param("version","0") : get(path(shopVoucher,suffix));
        mvc.perform(request.with(csrf()).with(user(principal(admin,"ADMIN")))).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString(shopCode))));
    }
    @ParameterizedTest @ValueSource(strings={"REDEEMED","RELEASED"})
    void usedRulesReuseExistingModelAndPreserveHistoricalRows(String state) {
        usage(state); authenticate(admin,"ADMIN");
        var history=jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages WHERE voucher_id=?",ownVoucher);
        var orders=jdbc.queryForList("SELECT * FROM uteexpress.orders WHERE voucher_id=?",ownVoucher);
        var form=updateForm();form.setTotalLimit(20L);form.setPerUserLimit(3L);form.setEndsAt(LocalDateTime.of(2101,1,1,0,0));
        management.update(ownVoucher,form);
        assertThat(row(ownVoucher)).containsEntry("total_limit",20L).containsEntry("per_user_limit",3L).containsEntry("version",1L);
        for (String change:List.of("code","type","value","maximum","minimum","total","user")) {
            var invalid=updateForm();invalid.setVersion(1L);invalid.setTotalLimit(20L);invalid.setPerUserLimit(3L);
            switch(change) {
                case "code" -> invalid.setCode(code+"_CHANGED"); case "type" -> invalid.setType(VoucherType.FIXED);
                case "value" -> invalid.setValue(new BigDecimal("20")); case "maximum" -> invalid.setMaxDiscount(new BigDecimal("1"));
                case "minimum" -> invalid.setMinSubtotal(new BigDecimal("1")); case "total" -> invalid.setTotalLimit(19L);
                case "user" -> invalid.setPerUserLimit(2L);
            }
            assertThatThrownBy(() -> management.update(ownVoucher,invalid)).isInstanceOfSatisfying(ApplicationException.class,
                    e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT));
        }
        var version=new VoucherVersionForm();version.setVersion(1L);management.disable(ownVoucher,version);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.voucher_usages WHERE voucher_id=?",ownVoucher)).isEqualTo(history);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.orders WHERE voucher_id=?",ownVoucher)).isEqualTo(orders);
    }
    @Test void auditFailureRollsBackCreateUpdateAndDisable() {
        authenticate(admin,"ADMIN");
        jdbc.execute("CREATE FUNCTION uteexpress.promo03_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action LIKE 'PLATFORM_VOUCHER_%' THEN RAISE EXCEPTION 'forced audit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER promo03_audit_failure BEFORE INSERT ON uteexpress.audit_logs FOR EACH ROW EXECUTE FUNCTION uteexpress.promo03_reject_audit()");
        var before=row(ownVoucher);
        try {
            assertThatThrownBy(() -> management.create(createForm())).isInstanceOf(RuntimeException.class);
            var update=updateForm();update.setValue(new BigDecimal("20"));
            assertThatThrownBy(() -> management.update(ownVoucher,update)).isInstanceOf(RuntimeException.class);
            var version=new VoucherVersionForm();version.setVersion(0L);
            assertThatThrownBy(() -> management.disable(ownVoucher,version)).isInstanceOf(RuntimeException.class);
            assertThat(row(ownVoucher)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.vouchers WHERE code=?",Long.class,code+"_NEW")).isZero();
            assertThat(audits(ownVoucher)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER promo03_audit_failure ON uteexpress.audit_logs");
            jdbc.execute("DROP FUNCTION uteexpress.promo03_reject_audit()");
        }
    }
    @Test void managementSeesUsageCommittedByCheckoutWhileWaitingForVoucherLock() throws Exception {
        var form=updateForm();form.setValue(new BigDecimal("20"));
        try(var executor=Executors.newSingleThreadExecutor()) {
            var future=new TransactionTemplate(transactions).execute(tx -> {
                jdbc.queryForObject("SELECT id FROM uteexpress.users WHERE id=? FOR UPDATE",Long.class,buyer);
                jdbc.queryForObject("SELECT id FROM uteexpress.vouchers WHERE id=? FOR UPDATE",Long.class,ownVoucher);
                var pending=executor.submit(() -> {
                    authenticate(admin,"ADMIN");
                    try { management.update(ownVoucher,form); return null; }
                    catch(ApplicationException e) { return e.errorCode(); }
                    finally { clear(); }
                });
                awaitBlocked("vouchers");usage("REDEEMED");return pending;
            });
            assertThat(future.get(20,TimeUnit.SECONDS)).isEqualTo(ErrorCode.CONFLICT);
            assertThat(row(ownVoucher)).containsEntry("version",0L);
        }
    }
    @ParameterizedTest @ValueSource(strings={"role","status"})
    void accountLockWaitRechecksRevocationCommittedBeforeManagement(String revoke) throws Exception {
        try(var executor=Executors.newSingleThreadExecutor()) {
            var future=new TransactionTemplate(transactions).execute(tx -> {
                jdbc.queryForObject("SELECT id FROM uteexpress.users WHERE id=? FOR UPDATE",Long.class,admin);
                var pending=executor.submit(() -> {
                    authenticate(admin,"ADMIN");
                    try {
                        new TransactionTemplate(transactions).executeWithoutResult(inner -> {
                            identities.getProfile(admin); // Intentionally preload ACTIVE in the JPA first-level cache.
                            management.create(createForm());
                        });
                        return null;
                    } catch(ApplicationException e) { return e.errorCode(); }
                    finally { clear(); }
                });
                awaitBlocked("users");
                if (revoke.equals("role")) jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",admin);
                else jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?",admin);
                return pending;
            });
            assertThat(future.get(20,TimeUnit.SECONDS)).isEqualTo(ErrorCode.ACCESS_DENIED);
        }
    }
    private void awaitBlocked(String table) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
        while(!waiting && System.nanoTime()<deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            waiting=jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND cardinality(pg_blocking_pids(pid))>0 AND query ILIKE ?",Integer.class,"%"+table+"%")>0;
            if(!waiting) try {Thread.sleep(20);} catch(InterruptedException e) {Thread.currentThread().interrupt();throw new AssertionError(e);}
        }
        assertThat(waiting).as("observed PostgreSQL lock wait on %s",table).isTrue();
    }
    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void realJwtCookieAndRenderedCsrfTokensAuthorizeCreateEditAndDisableForms() throws Exception {
        String username = jdbc.queryForObject("SELECT username FROM uteexpress.users WHERE id=?", String.class, admin);
        // csrf() installs a test repository on the shared filter. Isolate this real-cookie proof.
        var loginPage = mvc.perform(get("/login")).andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN")).andReturn().getResponse();
        Cookie auth = mvc.perform(post("/login").cookie(loginPage.getCookie("XSRF-TOKEN"))
                        .param("_csrf", token(loginPage.getContentAsString())).param("identifier", username).param("password", "TestAdmin1!"))
                .andExpect(status().is3xxRedirection()).andExpect(cookie().exists("UTEEXPRESS_AUTH"))
                .andReturn().getResponse().getCookie("UTEEXPRESS_AUTH");
        var createPage = mvc.perform(get("/admin/vouchers/new").cookie(auth)).andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN")).andReturn().getResponse();
        String newCode = code + "_NEW";
        mvc.perform(valid(post("/admin/vouchers"), newCode).cookie(auth, createPage.getCookie("XSRF-TOKEN"))
                        .param("_csrf", token(createPage.getContentAsString())))
                .andExpect(status().is3xxRedirection());
        long id = jdbc.queryForObject("SELECT id FROM uteexpress.vouchers WHERE code=?", Long.class, newCode);
        var editPage = mvc.perform(get(path(id, "/edit")).cookie(auth)).andExpect(status().isOk()).andReturn().getResponse();
        mvc.perform(valid(post(path(id, "/update")), newCode).param("version", "0")
                        .cookie(auth, editPage.getCookie("XSRF-TOKEN")).param("_csrf", token(editPage.getContentAsString())))
                .andExpect(status().is3xxRedirection());
        var listPage = mvc.perform(get(path(id, "/disable")).cookie(auth)).andExpect(status().isOk()).andReturn().getResponse();
        mvc.perform(post(path(id, "/disable")).param("version", "1").cookie(auth, listPage.getCookie("XSRF-TOKEN"))
                        .param("_csrf", token(listPage.getContentAsString())))
                .andExpect(status().is3xxRedirection());
        assertThat(row(id)).containsEntry("active", false);
    }

    private String token(String html) {
        var matcher = Pattern.compile("name=\"_csrf\" value=\"([^\"]+)\"").matcher(html);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
    private long account(String role) {
        String name="p"+UUID.randomUUID().toString().replace("-","").substring(0,20);
        long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id",Long.class,name+"@example.test",name+"@example.test",name,name,passwords.encode("TestAdmin1!"));
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role);return id;
    }
    private long voucher(String code,String scope,Long shop) {
        return jdbc.queryForObject("INSERT INTO uteexpress.vouchers(code,scope,shop_id,type,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,active,created_by) VALUES (?,?,?,'PERCENTAGE',10,0,'2020-01-01','2100-01-01',10,2,true,?) RETURNING id",Long.class,code,scope,shop,admin);
    }
    // Buyer differs from the management actor: a real checkout locks its own buyer before Voucher.
    // Reusing Admin here would insert a buyer FK after Voucher against the actor lock, inventing a lock inversion.
    private void usage(String state) {
        String key=UUID.randomUUID().toString();
        long order=jdbc.queryForObject("""
            INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,
                receiver_name,phone,province_code,district,detail,subtotal,discount_total,shipping_fee,
                grand_total,commission_amount,commission_rate_snapshot,voucher_id,voucher_code,voucher_scope)
            VALUES (?,?,?, ?,?,'NEW','Receiver','0900','VN','District','Detail',10000,1000,0,9000,0,0,?,?,'PLATFORM') RETURNING id
            """,Long.class,"ORD-"+key,key,"hash-"+key,buyer,shop,ownVoucher,code);
        jdbc.update("INSERT INTO uteexpress.voucher_usages(voucher_id,user_id,order_id,status,discount_amount,released_at) VALUES (?,?,?,?,1000,CASE WHEN ?='RELEASED' THEN CURRENT_TIMESTAMP ELSE NULL END)",ownVoucher,buyer,order,state,state);
    }
    private Map<String,Object> row(long id) {return jdbc.queryForMap("SELECT * FROM uteexpress.vouchers WHERE id=?",id);}
    private long audits(long id) {return jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='VOUCHER' AND target_id=?",Long.class,id);}
    private static String path(long id,String suffix) {return "/admin/vouchers/"+id+suffix;}
    private UteExpressPrincipal principal(long id,String role) {return new UteExpressPrincipal(id,"account"+id,null,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)),true);}
    private void authenticate(long id,String role) {
        var p=principal(id,role);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));
    }
    private PlatformVoucherForm createForm() {var f=new PlatformVoucherForm();fill(f);f.setCode(code+"_NEW");return f;}
    private PlatformVoucherUpdateForm updateForm() {var f=new PlatformVoucherUpdateForm();fill(f);f.setVersion(0L);return f;}
    private void fill(VoucherDefinitionForm f) {
        f.setCode(code);f.setType(VoucherType.PERCENTAGE);f.setValue(new BigDecimal("10"));
        f.setStartsAt(LocalDateTime.of(2020,1,2,0,0));f.setEndsAt(LocalDateTime.of(2100,1,1,0,0));f.setTotalLimit(10L);f.setPerUserLimit(2L);
    }
    private static MockHttpServletRequestBuilder valid(MockHttpServletRequestBuilder request,String code) {
        return request.param("code",code).param("type","PERCENTAGE").param("value","10").param("minSubtotal","0")
                .param("startsAt","2020-01-02T00:00").param("endsAt","2100-01-01T00:00").param("totalLimit","10").param("perUserLimit","2").param("active","true");
    }
    private static void override(MockHttpServletRequestBuilder request,String field,String value) { request.with(http -> {http.setParameter(field,value);return http;}); }
}
