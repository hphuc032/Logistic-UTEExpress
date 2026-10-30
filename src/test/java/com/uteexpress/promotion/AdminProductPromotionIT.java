package com.uteexpress.promotion;

import com.uteexpress.cart.dto.AddCartProductRequest;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.catalog.dto.*;
import com.uteexpress.catalog.service.*;
import com.uteexpress.checkout.dto.*;
import com.uteexpress.checkout.service.CheckoutQuoteService;
import com.uteexpress.common.exception.*;
import com.uteexpress.engagement.service.EngagementService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.service.*;
import com.uteexpress.promotion.dto.*;
import com.uteexpress.promotion.service.PromotionPricingService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.math.BigDecimal;
import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class AdminProductPromotionIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Autowired jakarta.persistence.EntityManager entities;
    @Autowired MockMvc mvc;
    @Autowired AdminProductPromotionService management;
    @Autowired ProductPromotionManagementService vendorManagement;
    @Autowired com.uteexpress.promotion.service.PromotionManagementService writer;
    @Autowired CatalogQueryService catalog;
    @Autowired CheckoutQuoteService quotes;
    @Autowired OrderPlacementService placement;
    @Autowired PasswordEncoder passwords;
    @Autowired PlatformTransactionManager transactions;
    @Autowired CartService carts;
    long admin,vendor,buyer,shop,category,product,otherProduct,address,provider;
    @BeforeEach void fixture() {
        for (String table : List.of("voucher_usages","vouchers","order_status_history","payments","order_items","orders","commission_policies","promotions")) jdbc.update("DELETE FROM uteexpress."+table);
        String key=UUID.randomUUID().toString().replace("-","");
        admin=account("a"+key.substring(0,15),"ADMIN");vendor=account("v"+key.substring(0,15),"VENDOR");buyer=account("b"+key.substring(0,15),"USER");
        shop=jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Promo shop',?,'Pickup','APPROVED') RETURNING id",Long.class,vendor,key);
        category=jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id",Long.class,key);
        product=product();otherProduct=product();
        address=jdbc.queryForObject("INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default) VALUES (?,'Receiver','0900000000','VN','District','Detail',true) RETURNING id",Long.class,buyer);
        provider=jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Provider',true) RETURNING id",Long.class,"P"+key.substring(0,20).toUpperCase(Locale.ROOT));
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'STANDARD','VN',5000,true)",provider);
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (7.1234,'2020-01-01',?)",admin);
    }
    @AfterEach void clear() {SecurityContextHolder.clearContext();}
    @Test void adminNavigatesSelectCreateEditAndDisableWithAuditAndServerActor() throws Exception {
        mvc.perform(get("/admin/promotions").with(user(principal(admin,"ADMIN")))).andExpect(status().isOk()).andExpect(content().string(containsString(base(product))));
        mvc.perform(get(base(product)+"/new").with(user(principal(admin,"ADMIN")))).andExpect(status().isOk()).andExpect(content().string(containsString("name=\"_csrf\"")));
        mvc.perform(valid(post(base(product))).with(user(principal(admin,"ADMIN"))).with(csrf())).andExpect(status().is3xxRedirection());
        long id=jdbc.queryForObject("SELECT id FROM uteexpress.promotions WHERE product_id=?",Long.class,product);
        assertThat(row(id)).containsEntry("product_id",product).containsEntry("created_by",admin);
        assertThat(audits(id)).isEqualTo(1);
        mvc.perform(get(path(id,"/edit")).with(user(principal(admin,"ADMIN")))).andExpect(status().isOk()).andExpect(content().string(containsString("name=\"version\"")));
        var update=valid(post(path(id,"/update"))).param("version","0");override(update,"discountPercent","25");
        mvc.perform(update.with(user(principal(admin,"ADMIN"))).with(csrf())).andExpect(status().is3xxRedirection());
        mvc.perform(get(path(id,"/disable")).with(user(principal(admin,"ADMIN")))).andExpect(status().isOk()).andExpect(content().string(containsString("Hủy, giữ nguyên")));
        mvc.perform(post(path(id,"/disable")).param("version","1").with(user(principal(admin,"ADMIN"))).with(csrf())).andExpect(status().is3xxRedirection());
        assertThat(row(id)).containsEntry("active",false).containsEntry("version",2L);
        assertThat(audits(id)).isEqualTo(3);
    }
    @ParameterizedTest @ValueSource(strings={"MANAGER","VENDOR","USER","SHIPPER","ANONYMOUS"})
    void roleMatrixProtectsAllRoutesAndService(String role) throws Exception {
        long id=create();
        for(var request:List.of(get("/admin/promotions"),get(base(product)),get(base(product)+"/new"),get(path(id,"/edit")),get(path(id,"/disable")),valid(post(base(product))),valid(post(path(id,"/update"))).param("version","0"),post(path(id,"/disable")).param("version","0"))) {
            request.with(csrf());if(!role.equals("ANONYMOUS")) request.with(user(principal(admin,role)));
            mvc.perform(request).andExpect(role.equals("ANONYMOUS")?status().isUnauthorized():status().isForbidden());
        }
        if(!role.equals("ANONYMOUS")) {
            auth(admin,role);assertThatThrownBy(() -> management.list(product)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        }
    }
    @ParameterizedTest @ValueSource(strings={"productId","id","shopId","createdBy","actorId","ownerId","scope","price","discountAmount","orderId"})
    void forgedFieldsRejectedForAllMutations(String field) throws Exception {
        long id=create();var before=row(id);
        for(String action:List.of("create","update","disable")) {
            var request=action.equals("create")?valid(post(base(otherProduct))):action.equals("update")?valid(post(path(id,"/update"))):post(path(id,"/disable"));
            request.param("version","0").param(field,"99999");
            var response=mvc.perform(request.with(user(principal(admin,"ADMIN"))).with(csrf())).andReturn();
            assertThat(response.getResponse().getStatus()).isIn(200,400);
            assertThat(row(id)).isEqualTo(before);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.promotions WHERE product_id=?",Long.class,otherProduct)).isZero();
        }
    }
    @ParameterizedTest @CsvSource({"name,''","discountPercent,0","discountPercent,101","discountPercent,bad","startsAt,invalid","endsAt,2019-01-01T00:00"})
    void invalidDefinitionsRedisplaySafeValidation(String field,String value) throws Exception {
        var request=valid(post(base(product)));override(request,field,value);
        mvc.perform(request.with(user(principal(admin,"ADMIN"))).with(csrf())).andExpect(status().isOk()).andExpect(model().attributeHasErrors("promotionForm"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.promotions WHERE product_id=?",Long.class,product)).isZero();
    }
    @ParameterizedTest @ValueSource(strings={"create","update","disable"})
    void unsafeActionsRequireValidCsrf(String action) throws Exception {
        long id=create();var before=row(id);
        for(boolean invalid:List.of(false,true)) {
            var request=action.equals("create")?valid(post(base(otherProduct))):action.equals("update")?valid(post(path(id,"/update"))):post(path(id,"/disable"));
            request.param("version","0").with(user(principal(admin,"ADMIN")));if(invalid) request.with(csrf().useInvalidToken());
            mvc.perform(request).andExpect(status().isForbidden());
        }
        assertThat(row(id)).isEqualTo(before);
    }
    @Test void staleVersionsCrossProductIdsAndOverlapRejectWithoutMutation() {
        long id=create();var before=row(id);auth(admin,"ADMIN");
        var update=updateForm(9);update.setDiscountPercent(new BigDecimal("30"));
        reject(() -> management.update(product,id,update),ErrorCode.CONFLICT);
        reject(() -> management.disable(product,id,9L),ErrorCode.CONFLICT);
        reject(() -> management.get(otherProduct,id),ErrorCode.RESOURCE_NOT_FOUND);
        reject(() -> management.disable(otherProduct,id,0L),ErrorCode.RESOURCE_NOT_FOUND);
        reject(() -> management.create(product,form()),ErrorCode.CONFLICT);
        assertThat(row(id)).isEqualTo(before);assertThat(audits(id)).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"HIDDEN","MODERATED","SUSPENDED"})
    void unavailableTargetCannotCreateOrUpdateButCanReadAndDisable(String state) {
        long id=create();auth(admin,"ADMIN");
        if(state.equals("SUSPENDED")) jdbc.update("UPDATE uteexpress.shops SET status='SUSPENDED',moderation_reason='Test moderation' WHERE id=?",shop);
        else jdbc.update("UPDATE uteexpress.products SET status=?,moderation_reason='Test moderation' WHERE id=?",state,product);
        reject(() -> management.update(product,id,updateForm(0)),ErrorCode.RESOURCE_NOT_FOUND);
        reject(() -> management.create(product,form()),ErrorCode.RESOURCE_NOT_FOUND);
        assertThat(management.get(product,id).id()).isEqualTo(id);
        management.disable(product,id,0L);assertThat(row(id)).containsEntry("active",false);
    }
    @ParameterizedTest @ValueSource(strings={"LOCKED","DISABLED","PENDING_VERIFICATION","REVOKED"})
    void staleAdminPrincipalCannotAuthorizeInactiveOrRevokedActor(String state) {
        auth(admin,"ADMIN");
        if(state.equals("REVOKED")) jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",admin);
        else jdbc.update("UPDATE uteexpress.users SET status=? WHERE id=?",state,admin);
        reject(() -> management.create(product,form()),ErrorCode.ACCESS_DENIED);
    }
    @Test void vendorCannotUseAdminWriterAndAdminCannotUseVendorWriter() {
        auth(vendor,"VENDOR");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(tx -> writer.createForAdministrator(product,form())))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        auth(admin,"ADMIN");
        assertThatThrownBy(() -> new TransactionTemplate(transactions).execute(tx -> writer.create(product,admin,vendorForm())))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void schedulesRemainProductOwnedAndCreatorImmutableAcrossAdminVendorEdits() {
        long id=create();auth(vendor,"VENDOR");
        var update=new VendorPromotionUpdateForm();fill(update);update.setDiscountPercent(new BigDecimal("30"));update.setVersion(0L);
        vendorManagement.update(product,id,update);
        assertThat(row(id)).containsEntry("created_by",admin);
        auth(admin,"ADMIN");var adminUpdate=updateForm(1);adminUpdate.setDiscountPercent(new BigDecimal("40"));management.update(product,id,adminUpdate);
        assertThat(row(id)).containsEntry("created_by",admin);
    }
    @Test void auditFailureRollsBackCreateUpdateAndDisable() {
        long id=create();var before=row(id);auth(admin,"ADMIN");
        jdbc.execute("CREATE FUNCTION uteexpress.promo03_reject_product_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action LIKE 'PRODUCT_PROMOTION_%' THEN RAISE EXCEPTION 'forced promotion audit failure'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER promo03_product_audit_failure BEFORE INSERT ON uteexpress.audit_logs FOR EACH ROW EXECUTE FUNCTION uteexpress.promo03_reject_product_audit()");
        try {
            assertThatThrownBy(() -> management.create(otherProduct,form())).hasStackTraceContaining("forced promotion audit failure");
            var update=updateForm(0);update.setDiscountPercent(new BigDecimal("30"));
            assertThatThrownBy(() -> management.update(product,id,update)).hasStackTraceContaining("forced promotion audit failure");
            assertThatThrownBy(() -> management.disable(product,id,0L)).hasStackTraceContaining("forced promotion audit failure");
            assertThat(row(id)).isEqualTo(before);assertThat(audits(id)).isEqualTo(1);
        } finally {jdbc.execute("DROP TRIGGER promo03_product_audit_failure ON uteexpress.audit_logs");jdbc.execute("DROP FUNCTION uteexpress.promo03_reject_product_audit()");}
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void checkoutWaitsForAdminScheduleCommitOrRollback(boolean commit) throws Exception {
        auth(buyer,"USER");carts.addProduct(new AddCartProductRequest(product,2));
        try(var pool=Executors.newSingleThreadExecutor()) {
            Future<com.uteexpress.order.dto.PlaceOrderResult>[] pending=new Future[1];
            auth(admin,"ADMIN");
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                management.create(product,form());
                pending[0]=pool.submit(() -> {auth(buyer,"USER");try{return placement.placeOrder(request());}finally{clear();}});
                awaitProductWait();if(!commit)tx.setRollbackOnly();
            });
            var placed=pending[0].get(20,TimeUnit.SECONDS);
            assertThat(placed.grandTotal()).isEqualByComparingTo(commit?"205000":"255000");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.orders WHERE id=?",Long.class,placed.orderId())).isEqualTo(1);
        }
    }
    @Test void adminDisableWaitsForCheckoutAndNeverRewritesItsSnapshot() throws Exception {
        long id=create();auth(buyer,"USER");carts.addProduct(new AddCartProductRequest(product,2));
        try(var pool=Executors.newSingleThreadExecutor()) {
            Future<?>[] pending=new Future[1];
            var placed=new TransactionTemplate(transactions).execute(tx -> {
                var order=placement.placeOrder(request());
                pending[0]=pool.submit(() -> {auth(admin,"ADMIN");try{management.disable(product,id,0L);}finally{clear();}});
                awaitProductWait();return order;
            });
            pending[0].get(20,TimeUnit.SECONDS);
            assertThat(jdbc.queryForObject("SELECT final_unit_price FROM uteexpress.order_items WHERE order_id=?",BigDecimal.class,placed.orderId())).isEqualByComparingTo("100000");
            assertThat(catalog.requirePurchasableProducts(Set.of(product)).getFirst().finalUnitPrice()).isEqualByComparingTo("125000");
        }
    }
    @Test void vendorOverlapWaitsForAdminProductSentinelThenRejectsCommittedSchedule() throws Exception {
        try(var pool=Executors.newSingleThreadExecutor()) {
            Future<Long>[] pending=new Future[1];
            auth(admin,"ADMIN");
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                management.create(product,form());
                pending[0]=pool.submit(() -> {auth(vendor,"VENDOR");try{return vendorManagement.create(product,vendorForm());}finally{clear();}});
                awaitProductWait();
            });
            assertThatThrownBy(() -> pending[0].get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(ApplicationException.class);
            assertThat(management.list(product)).hasSize(1);
        }
    }
    @Test void shopRefreshRejectsSuspensionCommittedWhileAdminWaits() throws Exception {
        try(var pool=Executors.newSingleThreadExecutor()) {
            Future<?>[] pending=new Future[1];
            new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                jdbc.queryForObject("SELECT id FROM uteexpress.shops WHERE id=? FOR UPDATE",Long.class,shop);
                pending[0]=pool.submit(() -> {
                    auth(admin,"ADMIN");
                    try {new TransactionTemplate(transactions).executeWithoutResult(inner -> {
                        entities.find(com.uteexpress.shop.entity.Shop.class,shop); // Intentionally cache APPROVED.
                        management.create(product,form());
                    });} finally {clear();}
                });
                awaitWait("shops");
                jdbc.update("UPDATE uteexpress.shops SET status='SUSPENDED',moderation_reason='Test moderation' WHERE id=?",shop);
            });
            assertThatThrownBy(() -> pending[0].get(20,TimeUnit.SECONDS)).hasCauseInstanceOf(ApplicationException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.promotions WHERE product_id=?",Long.class,product)).isZero();
        }
    }
    private void awaitWait(String table) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
        while(!waiting && System.nanoTime()<deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            waiting=jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND cardinality(pg_blocking_pids(pid))>0 AND query ILIKE ?",Integer.class,"%"+table+"%")>0;
            if(!waiting)try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
        }
        assertThat(waiting).as("observed row lock on %s",table).isTrue();
    }
    private void awaitProductWait() {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);boolean waiting=false;
        while(!waiting && System.nanoTime()<deadline) {
            jdbc.execute("SELECT pg_stat_clear_snapshot()");
            waiting=jdbc.queryForObject("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND cardinality(pg_blocking_pids(pid))>0 AND query ILIKE '%products%'",Integer.class)>0;
            if(!waiting)try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}
        }
        assertThat(waiting).as("observed Product sentinel wait").isTrue();
    }
    private long create(){auth(admin,"ADMIN");return management.create(product,form());}
    private AdminPromotionForm form(){var f=new AdminPromotionForm();fill(f);return f;}
    private VendorPromotionForm vendorForm(){var f=new VendorPromotionForm();fill(f);return f;}
    private AdminPromotionUpdateForm updateForm(long v){var f=new AdminPromotionUpdateForm();fill(f);f.setVersion(v);return f;}
    private void fill(PromotionDefinitionForm f){f.setName("Admin sale");f.setDiscountPercent(new BigDecimal("20"));f.setStartsAt(LocalDateTime.of(2020,1,2,0,0));f.setEndsAt(LocalDateTime.of(2100,1,1,0,0));}
    private com.uteexpress.checkout.dto.CheckoutRequest request(){return new com.uteexpress.checkout.dto.CheckoutRequest(UUID.randomUUID().toString(),List.of(new com.uteexpress.checkout.dto.CheckoutRequest.Item(product,2)),address,provider,"STANDARD",com.uteexpress.checkout.dto.CheckoutRequest.PaymentMethod.COD,null);}
    private Map<String,Object> row(long id){return jdbc.queryForMap("SELECT * FROM uteexpress.promotions WHERE id=?",id);}
    private long audits(long id){return jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='PROMOTION' AND target_id=?",Long.class,id);}
    private long product(){return jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Admin eligible product',125000,10,'ACTIVE') RETURNING id",Long.class,shop,category);}
    private long account(String name,String role){long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,?,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id",Long.class,name+"@example.test",name+"@example.test",name,name,passwords.encode("PromoAdmin1!"));jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role);return id;}
    private UteExpressPrincipal principal(long id,String role){return new UteExpressPrincipal(id,"account"+id,null,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)),true);}
    private void auth(long id,String role){var p=principal(id,role);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));}
    private String path(long id,String suffix){return base(product)+"/"+id+suffix;}
    private static String base(long p){return "/admin/promotions/products/"+p;}
    private static void reject(Runnable f,ErrorCode code){assertThatThrownBy(f::run).isInstanceOfSatisfying(ApplicationException.class,e->assertThat(e.errorCode()).isEqualTo(code));}
    private static MockHttpServletRequestBuilder valid(MockHttpServletRequestBuilder b){return b.param("name","Admin sale").param("discountPercent","20").param("startsAt","2020-01-02T00:00").param("endsAt","2100-01-01T00:00").param("active","true");}
    private static void override(MockHttpServletRequestBuilder b,String field,String value){b.with(http->{http.setParameter(field,value);return http;});}
}
