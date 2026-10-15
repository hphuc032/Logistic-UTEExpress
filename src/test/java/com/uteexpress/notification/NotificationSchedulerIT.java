package com.uteexpress.notification;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.postgresql.PostgreSQLContainer;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest(properties = {"uteexpress.notifications.reconciliation-enabled=true",
        "uteexpress.notifications.reconcile-initial-delay-ms=100", "uteexpress.notifications.reconcile-delay-ms=100"})
@Testcontainers @DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationSchedulerIT {
    @Container @ServiceConnection static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17.6");
    @Autowired JdbcTemplate jdbc;
    @Test @Timeout(30) void scheduledWorkerRecoversHistoryWithoutAnInMemoryEvent() {
        String name=UUID.randomUUID().toString().replace("-", "").substring(0,20);
        long user=jdbc.queryForObject("INSERT INTO uteexpress.users(email,normalized_email,username,normalized_username,password_hash,status) VALUES (?,?,?,?,'test','ACTIVE') RETURNING id",Long.class,name+"@test.local",name+"@test.local",name,name);
        long shop=jdbc.queryForObject("INSERT INTO uteexpress.shops(owner_id,name,slug,pickup_address,status) VALUES (?,'Scheduler shop',?,'Pickup','APPROVED') RETURNING id",Long.class,user,UUID.randomUUID().toString());
        String key=UUID.randomUUID().toString();
        long order=jdbc.queryForObject("INSERT INTO uteexpress.orders(order_code,checkout_key,request_hash,buyer_id,shop_id,status,receiver_name,phone,province_code,district,detail,subtotal,discount_total,shipping_fee,grand_total,commission_amount,commission_rate_snapshot) VALUES (?,?,'hash',?,?,'CONFIRMED','Receiver','0900000000','VN','District','Street',100,0,0,100,0,0) RETURNING id",Long.class,key,key,user,shop);
        jdbc.update("INSERT INTO uteexpress.order_status_history(order_id,from_status,to_status,actor_id) VALUES (?,'NEW','CONFIRMED',?)",order,user);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        int count=0;
        while(System.nanoTime()<deadline){
            count=jdbc.queryForObject("SELECT count(*) FROM uteexpress.notifications WHERE order_id=?",Integer.class,order);
            if(count==1) break;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(20));
        }
        assertThat(count).as("Production scheduler projects durable history without an event callback").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT recipient_id FROM uteexpress.notifications WHERE order_id=?",Long.class,order)).isEqualTo(user);
    }
}
