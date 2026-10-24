package com.uteexpress.engagement;

import com.uteexpress.common.exception.*;
import com.uteexpress.common.storage.*;
import com.uteexpress.engagement.service.*;
import com.uteexpress.order.service.OrderLifecycleService;
import com.uteexpress.order.dto.RequestReturnCommand;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.*;
import java.time.Instant;
import java.sql.Timestamp;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class ReviewIntegrationIT {
    @Container @ServiceConnection static final PostgreSQLContainer DB=new PostgreSQLContainer("postgres:17.6");
    static final Path ROOT=root();
    private static Path root() {try{return Files.createTempDirectory("rev01-it-");}catch(Exception e){throw new IllegalStateException(e);}}
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {r.add("uteexpress.storage.root",ROOT::toString);}
    @Autowired JdbcTemplate jdbc;
    @Autowired ReviewService reviews;
    @Autowired PublicReviewService publicReviews;
    @Autowired OrderLifecycleService orders;
    @Autowired MockMvc mvc;
    @Autowired PlatformTransactionManager manager;
    @MockitoSpyBean ReviewWriter writer;
    @MockitoSpyBean com.uteexpress.order.service.ReturnOrderAuthority returnAuthority;
    long buyer,foreign,vendor,product,order,item,otherItem;
    String comment="Sản phẩm đã nhận đúng mô tả, chất lượng tốt và đóng gói cẩn thận.";
    @BeforeEach void setup() {
        buyer=createAccount("USER");foreign=createAccount("USER");vendor=createAccount("VENDOR");
        long shop=jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Review shop',?,'Pickup','APPROVED') RETURNING id",Long.class,vendor,UUID.randomUUID().toString());
        long cat=jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Review',?,true) RETURNING id",Long.class,UUID.randomUUID().toString());
        product=jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Product',100,5,'ACTIVE') RETURNING id",Long.class,shop,cat);
        order=order(buyer,shop);item=item(order);otherItem=item(order(foreign,shop));auth(buyer,"USER");
    }
    @AfterEach void clear(){SecurityContextHolder.clearContext();}
    @Test void realBoundaryCreatesServerFactsAndServesEscapedPublicImages() throws Exception {
        var before=orderState();
        String text="<script>alert('bad')</script>"+" Nội dung đánh giá hợp lệ, đầy đủ và chi tiết.";
        long id=reviews.create(item,5,text,List.of(png(),png(),png()));
        assertThat(jdbc.queryForMap("SELECT user_id,product_id,order_item_id,order_id FROM uteexpress.reviews WHERE id=?",id))
                .containsEntry("user_id",buyer).containsEntry("product_id",product).containsEntry("order_id",order).containsEntry("order_item_id",item);
        var page=publicReviews.list(product,0);assertThat(page).hasSize(1);assertThat(page.getFirst().mediaIds()).hasSize(3);
        mvc.perform(get("/products/{id}/reviews",product)).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("&lt;script&gt;")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("<script>alert('bad')"))));
        long media=page.getFirst().mediaIds().getFirst();
        mvc.perform(get("/products/{p}/reviews/media/{m}",product,media)).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(content().contentType("image/png"));
        assertThat(orderState()).isEqualTo(before);
        jdbc.update("UPDATE uteexpress.reviews SET visibility='HIDDEN' WHERE id=?",id);
        assertThat(publicReviews.list(product,0)).isEmpty();
        denied(() -> publicReviews.image(product,media),ErrorCode.RESOURCE_NOT_FOUND);
        jdbc.update("UPDATE uteexpress.reviews SET visibility='PUBLIC' WHERE id=?",id);
        jdbc.update("UPDATE uteexpress.products SET status='HIDDEN' WHERE id=?",product);
        denied(() -> publicReviews.list(product,0),ErrorCode.RESOURCE_NOT_FOUND);
        denied(() -> publicReviews.image(product,media),ErrorCode.RESOURCE_NOT_FOUND);
    }
    @Test void httpJsonMultipartCsrfRoleAndFieldAllowlistsUseTrustedIdentity() throws Exception {
        String route="/order-items/"+item+"/review";
        mvc.perform(get(route).with(user(principal(buyer,"USER")))).andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("_csrf")));
        String json="{\"rating\":5,\"comment\":\""+comment+"\"}";
        mvc.perform(post(route).with(user(principal(buyer,"USER"))).contentType("application/json").content(json)).andExpect(status().isForbidden());
        for(String role:List.of("ADMIN","MANAGER","SHIPPER"))
            mvc.perform(post(route).with(user(principal(buyer,role))).with(csrf()).contentType("application/json").content(json)).andExpect(status().isForbidden());
        mvc.perform(post(route).with(user(principal(buyer,"USER"))).with(csrf()).contentType("application/json").content(json.substring(0,json.length()-1)+",\"userId\":999}"))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(route).file(new MockMultipartFile("images","photo.png","image/png",png().bytes())).param("rating","4").param("comment",comment).param("actorId","999").with(user(principal(buyer,"USER"))).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(multipart(route).file(new MockMultipartFile("images","photo.png","image/png",png().bytes())).param("rating","4").param("comment",comment).with(user(principal(buyer,"USER"))).with(csrf()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/orders"));
        mvc.perform(post(route).with(user(principal(buyer,"USER"))).with(csrf()).contentType("application/json").content(json)).andExpect(status().isConflict());
        assertThat(count()).isEqualTo(1);
        mvc.perform(get("/products/{id}/reviews",product).param("page","-1")).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={"RETURN_REQUESTED","RETURNED","REFUNDED","SHIPPING","NEW"})
    void actualWriterRejectsNonDeliveredState(String status) {
        jdbc.update("UPDATE uteexpress.orders SET status=? WHERE id=?",status,order);
        denied(() -> reviews.create(item,5,comment,List.of()),ErrorCode.CONFLICT);assertThat(count()).isZero();
    }
    @Test void foreignLockedRevokedAndOuterTransactionsCannotWrite() {
        denied(() -> reviews.create(otherItem,5,comment,List.of()),ErrorCode.RESOURCE_NOT_FOUND);
        jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?",buyer);
        denied(() -> reviews.create(item,5,comment,List.of()),ErrorCode.ACCESS_DENIED);
        jdbc.update("UPDATE uteexpress.users SET status='ACTIVE' WHERE id=?",buyer);
        jdbc.update("DELETE FROM uteexpress.user_roles WHERE user_id=?",buyer);
        denied(() -> reviews.create(item,5,comment,List.of()),ErrorCode.ACCESS_DENIED);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code='VENDOR'",buyer);
        new TransactionTemplate(manager).executeWithoutResult(tx -> denied(() -> reviews.create(item,5,comment,List.of()),ErrorCode.INVALID_REQUEST));
        auth(buyer,"VENDOR");assertThat(reviews.create(item,5,comment,List.of())).isPositive();
    }
    @ParameterizedTest @ValueSource(strings={"image","mediaSql","writer"})
    void dbAndStoredFilesRollbackTogether(String stage) throws Exception {
        long files=files();
        if(stage.equals("writer")) doAnswer(call -> {call.callRealMethod();throw new ApplicationException(ErrorCode.CONFLICT);}).when((ReviewWriter)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(writer)).write(any(),any(),any());
        if(stage.equals("mediaSql")) jdbc.execute("ALTER TABLE uteexpress.review_media ADD CONSTRAINT rev_test_reject_media CHECK (position<0) NOT VALID");
        try {
            var images=stage.equals("image") ? List.of(png(),new UploadContent(new byte[]{1,2,3},"bad.png","image/png")) : List.of(png());
            assertThatThrownBy(() -> reviews.create(item,5,comment,images)).isInstanceOf(ApplicationException.class);
            assertThat(count()).isZero();assertThat(files()).isEqualTo(files);
        } finally {if(stage.equals("mediaSql")) jdbc.execute("ALTER TABLE uteexpress.review_media DROP CONSTRAINT rev_test_reject_media");}
    }
    @Test void duplicateActualWritersCommitExactlyOneReview() throws Exception {
        var start=new CountDownLatch(1);var pool=Executors.newFixedThreadPool(2);
        java.util.concurrent.Callable<Boolean> action=() -> {auth(buyer,"USER");try {start.await();try {reviews.create(item,5,comment,List.of());return true;}catch(ApplicationException e){assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFLICT);return false;}}finally{SecurityContextHolder.clearContext();}};
        try {var a=pool.submit(action);var b=pool.submit(action);start.countDown();assertThat(List.of(a.get(15,TimeUnit.SECONDS),b.get(15,TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);assertThat(count()).isEqualTo(1);}finally{pool.shutdownNow();}
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void realReviewAndReturnSerializeAtOrderInBothSchedules(boolean reviewFirst) throws Exception {
        var held=new CountDownLatch(1);var release=new CountDownLatch(1);var waiter=new AtomicInteger();
        var pool=Executors.newFixedThreadPool(2);long[] holder={0};
        if(reviewFirst) doAnswer(call -> {long id=(Long)call.callRealMethod();holder[0]=jdbc.queryForObject("SELECT pg_backend_pid()",Long.class);held.countDown();assertThat(release.await(12,TimeUnit.SECONDS)).isTrue();return id;}).when((ReviewWriter)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(writer)).write(any(),any(),any());
        try {
            Future<?> first;
            if(reviewFirst) first=pool.submit(() -> {auth(buyer,"USER");try{return reviews.create(item,5,comment,List.of(png()));}finally{SecurityContextHolder.clearContext();}});
            else {
                doAnswer(call -> {
                    call.callRealMethod();holder[0]=jdbc.queryForObject("SELECT pg_backend_pid()",Long.class);
                    held.countDown();assertThat(release.await(12,TimeUnit.SECONDS)).isTrue();return null;
                }).when((com.uteexpress.order.service.ReturnOrderAuthority)org.springframework.test.util.AopTestUtils.getUltimateTargetObject(returnAuthority)).create(any(),anyString(),any());
                first=pool.submit(() -> {auth(buyer,"USER");try{return orders.requestReturn(new RequestReturnCommand(order,0L,"Wrong item"));}finally{SecurityContextHolder.clearContext();}});
            }
            assertThat(held.await(10,TimeUnit.SECONDS)).isTrue();
            Future<?> second=pool.submit(() -> {auth(buyer,"USER");try {
                waiter.set(jdbc.queryForObject("SELECT 0",Integer.class));
                if(reviewFirst) return orders.requestReturn(new RequestReturnCommand(order,0L,"Wrong item"));
                denied(() -> reviews.create(item,5,comment,List.of()),ErrorCode.CONFLICT);return true;
            } finally {SecurityContextHolder.clearContext();}});
            // Verify a real blocked Order query using backend activity, not a timed success assumption.
            long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);boolean blocked=false;
            while(System.nanoTime()<end) {
                var waiting=jdbc.queryForList("SELECT pid FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND cardinality(pg_blocking_pids(pid))>0",Integer.class);
                for(int pid:waiting) if(jdbc.queryForList("SELECT unnest(pg_blocking_pids(?))",Long.class,pid).contains(holder[0])) {blocked=true;break;}
                if(blocked)break;Thread.onSpinWait();
            }
            assertThat(blocked).isTrue();release.countDown();first.get(15,TimeUnit.SECONDS);second.get(15,TimeUnit.SECONDS);
            assertThat(count()).isEqualTo(reviewFirst ? 1 : 0);
        } finally {release.countDown();pool.shutdownNow();}
    }
    @Test void buyerDetailLinksToPersistedItemAndJsonCreatesWithoutSensitiveFields() throws Exception {
        mvc.perform(get("/orders/{id}",order).accept("text/html").with(user(principal(buyer,"USER"))))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("/order-items/"+item+"/review")));
        mvc.perform(post("/order-items/{id}/review",item).with(user(principal(buyer,"USER"))).with(csrf())
                .contentType("application/json").content("{\"rating\":5,\"comment\":\""+comment+"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.reviewId").isNumber());
        assertThat(count()).isEqualTo(1);
    }
    @ParameterizedTest @ValueSource(strings={"count","oversized","svg","metadata"})
    void forbiddenUploadsRollbackBeforeAnyCommittedReview(String kind) throws Exception {
        long before=files();
        var invalid=switch(kind) {
            case "count" -> List.of(png(),png(),png(),png());
            case "oversized" -> List.of(new UploadContent(new byte[5*1024*1024+1],"large.png","image/png"));
            case "svg" -> List.of(new UploadContent("<svg xmlns='http://www.w3.org/2000/svg'/>".getBytes(java.nio.charset.StandardCharsets.UTF_8),"a.svg","image/svg+xml"));
            default -> List.of(new UploadContent(png().bytes(),"fake.jpg","image/jpeg"));
        };
        denied(() -> reviews.create(item,5,comment,invalid),ErrorCode.VALIDATION_FAILED);
        assertThat(count()).isZero();assertThat(files()).isEqualTo(before);
    }
    private long count(){return jdbc.queryForObject("SELECT count(*) FROM uteexpress.reviews WHERE order_item_id=?",Long.class,item);}
    private Map<String,Object> orderState(){return jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?",order);}
    private long files() throws Exception {try(var paths=Files.walk(ROOT)){return paths.filter(Files::isRegularFile).count();}}
    private static UploadContent png(){try {var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(2,2,java.awt.image.BufferedImage.TYPE_INT_RGB),"png",out);return new UploadContent(out.toByteArray(),"photo.png","image/png");}catch(Exception e){throw new IllegalStateException(e);}}
    private long createAccount(String role){String n="rev"+UUID.randomUUID().toString().replace("-","").substring(0,20);long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?, 'test','ACTIVE') RETURNING id",Long.class,n+"@test.invalid",n+"@test.invalid",n,n);jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role);return id;}
    private long order(long owner,long shop){return jdbc.queryForObject("""
            INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,receiver_name,phone,province_code,district,detail,subtotal,discount_total,shipping_fee,grand_total,commission_amount,commission_rate_snapshot,delivered_at)
            VALUES (?,?,'hash',?,?,'DELIVERED','Buyer','0900000000','VN','District','Address',100,0,0,100,0,0,?) RETURNING id
            """,Long.class,UUID.randomUUID().toString(),UUID.randomUUID().toString(),owner,shop,Timestamp.from(Instant.now().minusSeconds(60)));}
    private long item(long id){return jdbc.queryForObject("INSERT INTO uteexpress.order_items(order_id,product_id,product_name_snapshot,unit_price,discount_snapshot,final_unit_price,quantity,line_total) VALUES (?,?,'Product',100,0,100,1,100) RETURNING id",Long.class,id,product);}
    private static UteExpressPrincipal principal(long id,String role){return new UteExpressPrincipal(id,"buyer",null,0L,List.of(new SimpleGrantedAuthority("ROLE_"+role)),true);}
    private static void auth(long id,String role){var p=principal(id,role);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));}
    private static void denied(Runnable work,ErrorCode code){assertThatThrownBy(work::run).isInstanceOfSatisfying(ApplicationException.class,e -> assertThat(e.errorCode()).isEqualTo(code));}
}
