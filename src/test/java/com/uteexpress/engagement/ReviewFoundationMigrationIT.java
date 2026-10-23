package com.uteexpress.engagement;

import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

class ReviewFoundationMigrationIT {
    @ParameterizedTest @ValueSource(strings={"fresh","shipping","refund"})
    void upgradePreservesHistoryAndBindsReviewToRealBuyerItemAndProduct(String baseline) throws Exception {
        try(var db=new PostgreSQLContainer("postgres:17.6")) {
            db.start();
            var ds=new DriverManagerDataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword());
            var jdbc=new JdbcTemplate(ds);
            migration(db,baseline.equals("fresh") ? null : baseline.equals("shipping") ? "20261018110000" : "20261022090000").migrate();
            long buyer=user(jdbc,"buyer"), other=user(jdbc,"other");
            long shop=jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Shop','review-shop','Pickup','APPROVED') RETURNING id",Long.class,other);
            long cat=jdbc.queryForObject("INSERT INTO uteexpress.categories(name,slug,active) VALUES ('Cat','review-cat',true) RETURNING id",Long.class);
            long product=jdbc.queryForObject("INSERT INTO uteexpress.products(shop_id,category_id,name,price,stock,status) VALUES (?,?,'Product',10,2,'ACTIVE') RETURNING id",Long.class,shop,cat);
            long order=jdbc.queryForObject("""
                    INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,receiver_name,phone,province_code,district,detail,
                        subtotal,discount_total,shipping_fee,grand_total,commission_amount,commission_rate_snapshot)
                    VALUES ('REV','REV','hash',?,?,'DELIVERED','Receiver','0900000000','VN','District','Detail',10,0,0,10,0,0) RETURNING id
                    """,Long.class,buyer,shop);
            long item=jdbc.queryForObject("""
                    INSERT INTO uteexpress.order_items(order_id,product_id,product_name_snapshot,unit_price,discount_snapshot,final_unit_price,quantity,line_total)
                    VALUES (?,?,'Product',10,0,10,1,10) RETURNING id
                    """,Long.class,order,product);
            var original=jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?",order);
            var checksums=jdbc.queryForList("SELECT version,checksum FROM uteexpress.flyway_schema_history ORDER BY installed_rank");
            var latest=migration(db,null);latest.migrate();
            assertThat(latest.validateWithResult().validationSuccessful).isTrue();
            assertThat(latest.migrate().migrationsExecuted).isZero();
            assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?",order)).isEqualTo(original);
            var after=jdbc.queryForList("SELECT version,checksum FROM uteexpress.flyway_schema_history ORDER BY installed_rank");
            assertThat(after.subList(0,checksums.size())).isEqualTo(checksums);
            invalid(() -> review(jdbc,item,order,other,product,5,"x".repeat(50)));
            invalid(() -> review(jdbc,item,order,buyer,product+99999,5,"x".repeat(50)));
            invalid(() -> review(jdbc,item,order,buyer,product,0,"x".repeat(50)));
            invalid(() -> review(jdbc,item,order,buyer,product,6,"x".repeat(50)));
            invalid(() -> review(jdbc,item,order,buyer,product,3,"short"));
            invalid(() -> review(jdbc,item,order,buyer,product,3," ".repeat(50)));
            // Concurrent inserts rely on the unique OrderItem constraint, not a check-then-insert.
            var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Boolean> writer=() -> { start.await();try {review(jdbc,item,order,buyer,product,5,"x".repeat(50));return true;} catch(DataIntegrityViolationException duplicate){return false;} };
            try {
                var a=pool.submit(writer);var b=pool.submit(writer);start.countDown();
                assertThat(List.of(a.get(10,java.util.concurrent.TimeUnit.SECONDS),b.get(10,java.util.concurrent.TimeUnit.SECONDS))).containsExactlyInAnyOrder(true,false);
            } finally {pool.shutdownNow();}
            long id=jdbc.queryForObject("SELECT id FROM uteexpress.reviews WHERE order_item_id=?",Long.class,item);
            invalid(() -> media(jdbc,id,0,"../unsafe.svg","image/png"));
            invalid(() -> media(jdbc,id,0,"reviews/"+UUID.randomUUID()+".png","image/svg+xml"));
            for(int i=0;i<3;i++) media(jdbc,id,i,"reviews/"+UUID.randomUUID()+".png","image/png");
            invalid(() -> media(jdbc,id,3,"reviews/"+UUID.randomUUID()+".png","image/png"));
            invalid(() -> media(jdbc,id,0,"reviews/"+UUID.randomUUID()+".png","image/png"));
            invalid(() -> media(jdbc,id,1,"../unsafe.svg","image/svg+xml"));
            assertThat(jdbc.queryForObject("SELECT count(*) FROM uteexpress.review_media WHERE review_id=?",Integer.class,id)).isEqualTo(3);
            assertThat(jdbc.queryForMap("SELECT * FROM uteexpress.orders WHERE id=?",order)).isEqualTo(original);
        }
    }
    private long user(JdbcTemplate j,String name) {return j.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?, 'test','ACTIVE') RETURNING id",Long.class,name+"@review.test",name+"@review.test",name,name);}
    private void review(JdbcTemplate j,long item,long order,long buyer,long product,int rating,String text) {j.update("INSERT INTO uteexpress.reviews(order_item_id,order_id,user_id,product_id,rating,comment) VALUES (?,?,?,?,?,?)",item,order,buyer,product,rating,text);}
    private void media(JdbcTemplate j,long review,int position,String key,String type) {j.update("INSERT INTO uteexpress.review_media(review_id,position,storage_key,media_type) VALUES (?,?,?,?)",review,position,key,type);}
    private static void invalid(Runnable action) {assertThatThrownBy(action::run).isInstanceOf(DataIntegrityViolationException.class);}
    private Flyway migration(PostgreSQLContainer db,String target) {var c=Flyway.configure().dataSource(db.getJdbcUrl(),db.getUsername(),db.getPassword()).schemas("uteexpress").defaultSchema("uteexpress").locations("classpath:db/migration");if(target!=null)c.target(MigrationVersion.fromVersion(target));return c.load();}
}
