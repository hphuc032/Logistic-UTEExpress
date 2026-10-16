package com.uteexpress.operations;

import com.uteexpress.checkout.dto.CheckoutQuote;
import com.uteexpress.checkout.dto.OrderTotals;
import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.entity.*;
import com.uteexpress.order.repository.*;
import com.uteexpress.order.service.OrderLifecycleService;
import com.uteexpress.operations.dto.OpsCancellationInput;
import com.uteexpress.operations.service.OperationsCancellationService;
import com.uteexpress.payment.service.PaymentService;
import com.uteexpress.shipping.service.ShipmentAssignmentService;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Testcontainers
class OperationsCancellationIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderRepository orders;
    @Autowired OrderItemRepository items;
    @Autowired OrderStatusHistoryRepository history;
    @Autowired PaymentService payments;
    @Autowired OrderLifecycleService lifecycle;
    @Autowired ShipmentAssignmentService assignments;
    @Autowired OperationsCancellationService cancellation;
    @Autowired PlatformTransactionManager transactions;
    long order, admin, vendor, shipper, product;

    @BeforeEach void setup() {
        admin = createUser("ADMIN"); vendor = createUser("VENDOR"); shipper = createUser("SHIPPER");
        long buyer = createUser("USER");
        long shop = jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Ops HTTP shop',?,'Pickup','APPROVED') RETURNING id",Long.class,vendor,UUID.randomUUID().toString());
        long category = jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Category',?,true) RETURNING id",Long.class,UUID.randomUUID().toString());
        product = jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Product',100000,8,'ACTIVE') RETURNING id",Long.class,shop,category);
        long provider = jdbc.queryForObject("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,'Carrier',true) RETURNING id",Long.class,"OPS_"+UUID.randomUUID().toString().replace("-", "").substring(0,16).toUpperCase());
        var quote = new CheckoutQuote(shop,List.of(new CheckoutQuote.ItemSnapshot(product,"Product",new BigDecimal("100000"),BigDecimal.ZERO,new BigDecimal("100000"),1,new BigDecimal("100000"))),
                new CheckoutQuote.AddressSnapshot("Receiver","0900000000","VN","District","Address"),
                OrderTotals.calculate(new BigDecimal("100000"),BigDecimal.ZERO,BigDecimal.ZERO),provider,"STANDARD",null,BigDecimal.ZERO,BigDecimal.ZERO);
        var created = orders.saveAndFlush(new Order(buyer,"OPS-HTTP-"+UUID.randomUUID(),UUID.randomUUID().toString(),"hash",quote,Instant.now()));
        order = created.getId();
        items.saveAllAndFlush(quote.items().stream().map(item -> new OrderItem(order,item)).toList());
        history.saveAndFlush(new OrderStatusHistory(order,null,OrderStatus.NEW,buyer,Instant.now(),null));
        new TransactionTemplate(transactions).executeWithoutResult(s -> payments.initializeCodForNewOrder(order));
        auth(admin,"ADMIN");
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @ParameterizedTest @ValueSource(strings={"ADMIN","MANAGER"})
    void jsonCancelsThroughRealLifecycleAndKeepsPayment(String role) throws Exception {
        long actor = createUser(role);
        var paymentBefore = jdbc.queryForList("SELECT * FROM uteexpress.payments WHERE order_id=?",order);
        mvc.perform(post(path(role)).with(user(principal(actor,role))).with(csrf()).contentType("application/json").content(json("NEW",0,"EXPECTED_ABSENT",null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.toStatus").value("CANCELLED")).andExpect(jsonPath("$.actorId").value(actor));
        assertThat(jdbc.queryForObject("SELECT stock FROM uteexpress.products WHERE id=?",Integer.class,product)).isEqualTo(9);
        assertThat(jdbc.queryForList("SELECT * FROM uteexpress.payments WHERE order_id=?",order)).isEqualTo(paymentBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.order_status_history WHERE order_id=? AND to_status='CANCELLED'",Long.class,order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.audit_logs WHERE target_type='ORDER' AND target_id=? AND action='ORDER_OPS_CANCELLED' AND actor_id=?",Long.class,order,actor)).isEqualTo(1);
        var after=state();
        mvc.perform(post(path(role)).with(user(principal(actor,role))).with(csrf()).contentType("application/json").content(json("NEW",0,"EXPECTED_ABSENT",null))).andExpect(status().isConflict());
        assertThat(state()).isEqualTo(after);
    }

    @Test void csrfRoleValidationAndPersistedEligibilityFailWithoutEffects() throws Exception {
        var before=state();
        String body=json("NEW",0,"EXPECTED_ABSENT",null);
        mvc.perform(post(path("ADMIN")).with(user(principal(admin,"ADMIN"))).contentType("application/json").content(body)).andExpect(status().isForbidden());
        for(String role:List.of("USER","VENDOR","SHIPPER"))
            mvc.perform(post(path("MANAGER")).with(user(principal(admin,role))).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post(path("ADMIN")).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/json").content(body.replace("UNABLE_TO_FULFILL","free-text"))).andExpect(status().isBadRequest());
        mvc.perform(post(path("ADMIN")).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        mvc.perform(post(path("ADMIN")).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/json")
                .content(body.replace("}",",\"actorId\":999}"))).andExpect(status().isBadRequest());
        mvc.perform(post(path("ADMIN")).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/x-www-form-urlencoded")
                .param("expectedOrderStatus","NEW").param("expectedOrderVersion","0").param("expectedShipment","EXPECTED_ABSENT")
                .param("reasonCode","UNABLE_TO_FULFILL").param("ownerId","999")).andExpect(status().isBadRequest());
        jdbc.update("UPDATE uteexpress.users SET status='LOCKED' WHERE id=?",admin);
        mvc.perform(post(path("ADMIN")).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        assertThat(state()).isEqualTo(before);
    }

    @Test void assignedShipmentRequiresObservedVersionAndCancelsAtomically() throws Exception {
        assigned();
        var before=state();
        mvc.perform(post(path("ADMIN")).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/json").content(json("CONFIRMED",2,"EXPECTED_ABSENT",null))).andExpect(status().isConflict());
        mvc.perform(post(path("ADMIN")).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/json").content(json("CONFIRMED",2,"EXPECTED_PRESENT",null))).andExpect(status().isBadRequest());
        auth(admin,"ADMIN"); assignments.reassign(order,createUser("SHIPPER"),0L);
        mvc.perform(post(path("ADMIN")).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/json").content(json("CONFIRMED",2,"EXPECTED_PRESENT",0L))).andExpect(status().isConflict());
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?",String.class,order)).isEqualTo("CONFIRMED");
        mvc.perform(post(path("ADMIN")).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/json").content(json("CONFIRMED",2,"EXPECTED_PRESENT",1L))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM uteexpress.shipments WHERE order_id=?",String.class,order)).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT version FROM uteexpress.shipments WHERE order_id=?",Long.class,order)).isEqualTo(2);
    }

    @Test void formHasCsrfAndExpectedFactsAndCallsBoundaryWithoutOuterTransaction() throws Exception {
        mvc.perform(get("/admin/orders/{id}",order).with(user(principal(admin,"ADMIN"))).accept("text/html"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"_csrf\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("EXPECTED_ABSENT")));
        mvc.perform(post(path("ADMIN")).with(user(principal(admin,"ADMIN"))).with(csrf()).contentType("application/x-www-form-urlencoded")
                .param("expectedOrderStatus","NEW").param("expectedOrderVersion","0").param("expectedShipment","EXPECTED_ABSENT").param("reasonCode","UNABLE_TO_FULFILL"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/admin/orders/"+order));
        mvc.perform(get("/admin/orders/{id}",order).with(user(principal(admin,"ADMIN"))).accept("text/html"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("name=\"reasonCode\""))));
    }

    @Test void accidentalOuterTransactionRemainsRejected() {
        var before=state();
        assertThatThrownBy(() -> new TransactionTemplate(transactions).executeWithoutResult(s -> cancellation.cancel(order,
                new OpsCancellationInput(OrderStatus.NEW,0L,OpsShipmentExpectation.EXPECTED_ABSENT,null,OpsCancellationReason.UNABLE_TO_FULFILL))))
                .isInstanceOfSatisfying(ApplicationException.class,e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
        assertThat(state()).isEqualTo(before);
    }

    private void assigned() {
        auth(vendor,"VENDOR"); lifecycle.transition(new OrderTransitionCommand(order,OrderStatus.NEW,0L,OrderAction.CONFIRM,null));
        lifecycle.markReady(new OrderReadyCommand(order,1L)); auth(admin,"ADMIN"); assignments.assign(order,shipper,2L);
    }
    private String path(String role) { return "/"+(role.equals("ADMIN")?"admin":"manager")+"/orders/"+order+"/cancel"; }
    private String json(String status,long version,String presence,Long shipmentVersion) {
        return "{\"expectedOrderStatus\":\""+status+"\",\"expectedOrderVersion\":"+version+",\"expectedShipment\":\""+presence+"\",\"expectedShipmentVersion\":"+shipmentVersion+",\"reasonCode\":\"UNABLE_TO_FULFILL\"}";
    }
    private List<Object> state() {
        return List.of(jdbc.queryForList("SELECT * FROM uteexpress.orders WHERE id=?",order),jdbc.queryForList("SELECT * FROM uteexpress.shipments WHERE order_id=?",order),
                jdbc.queryForList("SELECT * FROM uteexpress.payments WHERE order_id=?",order),jdbc.queryForList("SELECT * FROM uteexpress.order_status_history WHERE order_id=? ORDER BY id",order),
                jdbc.queryForList("SELECT * FROM uteexpress.products WHERE id=?",product));
    }
    private long createUser(String role) {
        String name=UUID.randomUUID().toString().replace("-","").substring(0,20);
        long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?,'test','ACTIVE') RETURNING id",Long.class,name+"@test.local",name+"@test.local",name,name);
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role); return id;
    }
    private UteExpressPrincipal principal(long id,String role) { return new UteExpressPrincipal(id,"ops",null,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)),true); }
    private void auth(long id,String role) { var p=principal(id,role); SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities())); }
}
