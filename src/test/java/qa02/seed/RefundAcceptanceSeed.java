package qa02.seed;

import com.uteexpress.UteExpressApplication;
import com.uteexpress.cart.dto.AddCartProductRequest;
import com.uteexpress.cart.service.CartService;
import com.uteexpress.checkout.dto.CheckoutRequest;
import com.uteexpress.common.exception.*;
import com.uteexpress.fulfillment.dto.FulfillmentRequest;
import com.uteexpress.fulfillment.service.ShipperFulfillmentService;
import com.uteexpress.order.dto.*;
import com.uteexpress.order.service.*;
import com.uteexpress.payment.dto.*;
import com.uteexpress.security.authentication.UteExpressPrincipal;
import com.uteexpress.shipping.service.ShipmentAssignmentService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

/** Test-classpath-only fixture module; never packaged in the production application. */
public final class RefundAcceptanceSeed {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final OrderPlacementService placement;
    private final OrderLifecycleService lifecycle;
    private final CartService carts;
    private final ShipmentAssignmentService assignments;
    private final ShipperFulfillmentService fulfillment;
    private final CancelledPaidRefundService cancelledRefunds;
    private final String password;
    private long buyer,vendor,admin,manager,shipper,shop,category,address,provider;
    private RefundAcceptanceSeed(ConfigurableApplicationContext c) {
        jdbc=c.getBean(JdbcTemplate.class);passwords=c.getBean(PasswordEncoder.class);
        placement=c.getBean(OrderPlacementService.class);lifecycle=c.getBean(OrderLifecycleService.class);
        carts=c.getBean(CartService.class);assignments=c.getBean(ShipmentAssignmentService.class);
        fulfillment=c.getBean(ShipperFulfillmentService.class);cancelledRefunds=c.getBean(CancelledPaidRefundService.class);
        password=System.getenv("QA02_REFUND_DEMO_PASSWORD");
        require(password!=null&&password.length()>=16,"Provide a disposable QA02_REFUND_DEMO_PASSWORD (>=16 characters)");
    }
    public static void main(String[] args) {
        String url=System.getenv("DB_URL");
        require("YES".equals(System.getenv("QA02_DISPOSABLE_SEED_CONFIRM"))
                && url!=null&&url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/qa02_refund_seed"),
                "This fixture module only runs on its explicitly confirmed localhost disposable database");
        require(Arrays.stream(args).noneMatch(a->a.toLowerCase(Locale.ROOT).contains("prod")),"Production profile is forbidden");
        try(var context=SpringApplication.run(UteExpressApplication.class,args)) {
            require(!Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("prod"),"Production profile is forbidden");
            var seed=new RefundAcceptanceSeed(context);var result=seed.run();
            var mapper=context.getBean(tools.jackson.databind.ObjectMapper.class);
            System.out.println("QA02_REFUND_SEED_RESULT="+mapper.writeValueAsString(result));
        } finally { SecurityContextHolder.clearContext(); }
    }
    private Map<String,Object> run() {
        buyer=account("buyer","USER");account("foreign","USER");vendor=account("vendor","VENDOR");
        admin=account("admin","ADMIN");manager=account("manager","MANAGER");shipper=account("shipper","SHIPPER");
        shop=ensure("SELECT id FROM uteexpress.shops WHERE slug='qa02-refund-shop'",
                "INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'QA02 refund demo','qa02-refund-shop','Synthetic pickup','APPROVED') RETURNING id",vendor);
        require(jdbc.queryForObject("SELECT owner_id=? AND status='APPROVED' FROM uteexpress.shops WHERE id=?",Boolean.class,vendor,shop),"Existing seed Shop was changed; preserve it and review");
        category=ensure("SELECT id FROM uteexpress.categories WHERE slug='qa02-refund-category'",
                "INSERT INTO uteexpress.categories(name,slug,active) VALUES ('QA02 refund demo','qa02-refund-category',true) RETURNING id");
        address=ensure("SELECT id FROM uteexpress.addresses WHERE user_id="+buyer+" AND detail='QA02 refund synthetic address'",
                "INSERT INTO uteexpress.addresses(user_id,receiver_name,phone,province_code,district,detail,is_default) VALUES (?,'QA02 Buyer','0900000000','VN','Synthetic','QA02 refund synthetic address',true) RETURNING id",buyer);
        provider=ensure("SELECT id FROM uteexpress.shipping_providers WHERE code='QA02_REFUND_DEMO'",
                "INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES ('QA02_REFUND_DEMO','QA02 synthetic provider',true) RETURNING id");
        jdbc.update("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) VALUES (?,'STANDARD','VN',15000,true) ON CONFLICT(provider_id,service_code,destination_region) DO NOTHING",provider);
        jdbc.update("INSERT INTO uteexpress.commission_policies(rate_percent,effective_from,created_by) VALUES (7,'2020-01-01T00:00:00Z',?) ON CONFLICT(effective_from) DO NOTHING",admin);
        List<Map<String,Object>> scenarios=new ArrayList<>();
        for(String kind:List.of("returned-ready","returned-refunded","cancelled-paid-ready","cancelled-paid-refunded","cancelled-unpaid-negative","paid-new-negative")) {
            String checkout="QA02_REFUND_SEED_V1:"+kind;
            var existing=jdbc.queryForList("SELECT id FROM uteexpress.orders WHERE buyer_id=? AND checkout_key=?",Long.class,buyer,checkout);
            long order;
            if(existing.isEmpty())order=create(kind,checkout);
            else {require(existing.size()==1,"Duplicate seed key");order=existing.getFirst();}
            var row=jdbc.queryForMap("""
                    SELECT o.id,o.order_code,o.status,o.version,o.grand_total,
                        p.method AS payment_method,p.status AS payment_status,
                        (SELECT count(*) FROM uteexpress.refunds r WHERE r.order_id=o.id) AS refund_count,
                        (SELECT status FROM uteexpress.voucher_usages v WHERE v.order_id=o.id) AS voucher_status
                    FROM uteexpress.orders o JOIN uteexpress.payments p ON p.order_id=o.id WHERE o.id=?
                    """,order);
            var safe=new LinkedHashMap<String,Object>();safe.put("scenario",kind);safe.putAll(row);scenarios.add(safe);
        }
        var negativeChecks=verifyFixtures(scenarios);
        return Map.of("manifest","QA02_REFUND_SEED_V1","actors",Map.of("buyer",buyer,"vendor",vendor,"admin",admin,"manager",manager,"shipper",shipper),
                "scenarios",scenarios,"negativeChecks",negativeChecks,"paidFixtureProvenance","Synthetic persisted ONLINE/PAID evidence in an owned disposable DB only; not a callback or VNPAY Sandbox verification.",
                "rerunPolicy","Existing users, hashes, roles, product values and order progress are preserved; no status resets or duplicate payments/refunds.");
    }
    private Map<String,String> verifyFixtures(List<Map<String,Object>> scenarios) {
        var ids=new HashMap<String,Long>();for(var row:scenarios)ids.put((String)row.get("scenario"),((Number)row.get("id")).longValue());
        var checks=new LinkedHashMap<String,String>();long target=ids.get("cancelled-paid-ready");
        auth(manager,"MANAGER");
        try{cancelledRefunds.complete(new CancelledPaidRefundCommand(target,version(target),"QA02-NEGATIVE", "QA02:unauthorized"));throw new IllegalStateException("Manager unexpectedly refunded");}
        catch(org.springframework.security.access.AccessDeniedException denied){checks.put("managerDenied","PASS");}
        auth(admin,"ADMIN");
        for(String kind:List.of("cancelled-unpaid-negative","paid-new-negative")) {
            long id=ids.get(kind);String actual=jdbc.queryForObject("SELECT status FROM uteexpress.orders WHERE id=?",String.class,id);
            if(!actual.equals(kind.startsWith("cancelled")?"CANCELLED":"NEW")){checks.put(kind,"NOT RUN: existing fixture progressed; not reset");continue;}
            try{cancelledRefunds.complete(new CancelledPaidRefundCommand(id,version(id),"QA02-NEGATIVE","QA02:"+kind));throw new IllegalStateException("Invalid fixture unexpectedly refunded");}
            catch(ApplicationException denied){require(denied.errorCode()==ErrorCode.CONFLICT,"Unexpected negative result");checks.put(kind,"PASS");}
        }
        try{cancelledRefunds.complete(new CancelledPaidRefundCommand(target,version(target)," ","QA02:blank-proof"));throw new IllegalStateException("Blank proof accepted");}
        catch(ApplicationException denied){require(denied.errorCode()==ErrorCode.INVALID_REQUEST,"Unexpected proof result");checks.put("blankProofDenied","PASS");}
        long cancelled=ids.get("cancelled-paid-refunded");
        var c=jdbc.queryForMap("SELECT expected_order_version FROM uteexpress.refunds WHERE order_id=?",cancelled);
        require(cancelledRefunds.complete(new CancelledPaidRefundCommand(cancelled,((Number)c.get("expected_order_version")).longValue(),
                "QA02-SYNTHETIC-CANCELLED-PROOF","QA02_REFUND_SEED_V1:cancelled-paid-refunded:refund")).replay(),"Cancelled replay did not retain result");
        long returned=ids.get("returned-refunded");var r=jdbc.queryForMap("SELECT expected_order_version,expected_return_request_version FROM uteexpress.refunds WHERE order_id=?",returned);
        require(lifecycle.completeRefund(new ManualRefundCommand(returned,((Number)r.get("expected_order_version")).longValue(),
                ((Number)r.get("expected_return_request_version")).longValue(),"QA02-SYNTHETIC-COD-PROOF","QA02_REFUND_SEED_V1:returned-refunded:refund")).replay(),"Returned replay did not retain result");
        checks.put("cancelledReplay","PASS");checks.put("returnedReplay","PASS");return checks;
    }
    private long create(String kind,String checkout) {
        long product=ensure("SELECT id FROM uteexpress.products WHERE shop_id="+shop+" AND name='QA02 refund "+kind+"'",
                "INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,?,100000,10,'ACTIVE') RETURNING id",shop,category,"QA02 refund "+kind);
        String code="QA02REFUND_"+kind.replace('-','_').toUpperCase(Locale.ROOT);
        jdbc.update("""
                INSERT INTO uteexpress.vouchers(code,scope,type,shop_id,value,min_subtotal,starts_at,ends_at,total_limit,per_user_limit,created_by)
                VALUES (?,'SHOP','FIXED',?,10000,0,CURRENT_TIMESTAMP-INTERVAL '1 day',CURRENT_TIMESTAMP+INTERVAL '30 days',1,1,?) ON CONFLICT(code) DO NOTHING
                """,code,shop,vendor);
        auth(buyer,"USER");carts.addProduct(new AddCartProductRequest(product,2));
        long order=placement.placeOrder(new CheckoutRequest(checkout,List.of(new CheckoutRequest.Item(product,2)),address,provider,"STANDARD",CheckoutRequest.PaymentMethod.COD,code)).orderId();
        if(kind.startsWith("returned")) {
            auth(vendor,"VENDOR");lifecycle.transition(new OrderTransitionCommand(order,OrderStatus.NEW,version(order),OrderAction.CONFIRM,null));
            lifecycle.markReady(new OrderReadyCommand(order,version(order)));
            auth(admin,"ADMIN");assignments.assign(order,shipper,version(order));
            auth(shipper,"SHIPPER");
            for(OrderAction action:List.of(OrderAction.PICK_UP,OrderAction.START_SHIPPING,OrderAction.DELIVER))
                fulfillment.transition(order,action,new FulfillmentRequest(version(order),shipmentVersion(order),action==OrderAction.DELIVER?new BigDecimal("205000"):null));
            auth(buyer,"USER");lifecycle.requestReturn(new RequestReturnCommand(order,version(order),"QA02 synthetic return acceptance"));
            auth(vendor,"VENDOR");lifecycle.approveReturn(new ReturnDecisionCommand(order,version(order),returnVersion(order),null));
            lifecycle.receiveReturn(new ReceiveReturnCommand(order,version(order),returnVersion(order),"QA02-SYNTHETIC-RECEIPT"));
            if(kind.endsWith("refunded")){auth(admin,"ADMIN");lifecycle.completeRefund(new ManualRefundCommand(order,version(order),returnVersion(order),"QA02-SYNTHETIC-COD-PROOF",checkout+":refund"));}
        } else {
            if(!kind.equals("cancelled-unpaid-negative")) {
                // Deliberately synthetic prerequisite, never a production PAID writer or simulated gateway callback.
                // Use the persisted application timestamp: DB/JVM clock skew must not place PAID after cancellation.
                jdbc.update("""
                        UPDATE uteexpress.payments SET method='ONLINE',status='PAID',paid_at=created_at,
                            updated_at=created_at,provider_reference=?,amount=?
                        WHERE order_id=? AND status='UNPAID' AND method='COD'
                        ""","QA02_SYNTHETIC_"+kind,new BigDecimal("205000"),order);
            }
            if(!kind.equals("paid-new-negative")){auth(buyer,"USER");lifecycle.cancelForBuyer(new BuyerCancellationCommand(order,version(order)));}
            if(kind.equals("cancelled-paid-refunded")){auth(admin,"ADMIN");cancelledRefunds.complete(new CancelledPaidRefundCommand(order,version(order),"QA02-SYNTHETIC-CANCELLED-PROOF",checkout+":refund"));}
        }
        return order;
    }
    private long account(String key,String role) {
        String name="qa02_refund_"+key,email=name+"@example.test";
        var ids=jdbc.queryForList("SELECT id FROM uteexpress.users WHERE normalized_username=?",Long.class,name);
        if(!ids.isEmpty()) {
            require(ids.size()==1&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT normalized_email=? AND status='ACTIVE' FROM uteexpress.users WHERE id=?",Boolean.class,email,ids.getFirst())),"Existing seed account changed; not reset");
            require(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM uteexpress.user_roles ur JOIN uteexpress.roles r ON r.id=ur.role_id WHERE ur.user_id=? AND r.code=?)",Boolean.class,ids.getFirst(),role)),"Existing seed role changed; not reset");
            return ids.getFirst();
        }
        long id=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status,email_verified_at) VALUES (?,?,?,? ,?,'ACTIVE',CURRENT_TIMESTAMP) RETURNING id",Long.class,email,email,name,name,passwords.encode(password));
        jdbc.update("INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT ?,id FROM uteexpress.roles WHERE code=?",id,role);return id;
    }
    private long ensure(String select,String insert,Object... args) {
        var ids=jdbc.queryForList(select,Long.class);require(ids.size()<=1,"Ambiguous natural seed key");
        return ids.isEmpty()?jdbc.queryForObject(insert,Long.class,args):ids.getFirst();
    }
    private long version(long id){return jdbc.queryForObject("SELECT version FROM uteexpress.orders WHERE id=?",Long.class,id);}
    private long shipmentVersion(long id){return jdbc.queryForObject("SELECT version FROM uteexpress.shipments WHERE order_id=?",Long.class,id);}
    private long returnVersion(long id){return jdbc.queryForObject("SELECT version FROM uteexpress.return_requests WHERE order_id=?",Long.class,id);}
    private static void auth(long id,String role){var p=new UteExpressPrincipal(id,"QA02 synthetic",null,0,List.of(new SimpleGrantedAuthority("ROLE_"+role)),true);SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(p,null,p.getAuthorities()));}
    private static void require(boolean valid,String message){if(!valid)throw new IllegalStateException(message);}
}
