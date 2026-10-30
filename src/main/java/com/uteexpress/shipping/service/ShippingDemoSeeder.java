package com.uteexpress.shipping.service;

import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.service.AuditLogService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.Map;

/** Explicit opt-in, synthetic demo data only. Never reset changed rows on rerun. */
@Component
@Profile("demo & !prod")
@ConditionalOnProperty(name="uteexpress.demo.shipping.enabled",havingValue="true")
public class ShippingDemoSeeder implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final AuditLogService audit;
    public ShippingDemoSeeder(JdbcTemplate jdbc,AuditLogService audit) { this.jdbc=jdbc; this.audit=audit; }

    @Override @Transactional
    public void run(ApplicationArguments args) {
        seedProvider("DEMO_SHIP","Đơn vị giao hàng mẫu",true);
        seedProvider("DEMO_PAUSED","Đơn vị mẫu tạm dừng",false);
        Long provider=jdbc.queryForObject("SELECT id FROM uteexpress.shipping_providers WHERE code='DEMO_SHIP'",Long.class);
        seedRate(provider,"STANDARD","DEMO_REGION",30000,true);
        seedRate(provider,"EXPRESS","DEMO_REGION",50000,true);
        seedRate(provider,"STANDARD","DEMO_DISABLED",40000,false);
    }
    private void seedProvider(String code,String name,boolean active) {
        var ids=jdbc.queryForList("INSERT INTO uteexpress.shipping_providers(code,name,active) VALUES (?,?,?) "
                +"ON CONFLICT (code) DO NOTHING RETURNING id",Long.class,code,name,active);
        for(Long id:ids) log("PROVIDER_SEEDED","SHIPPING_PROVIDER",id);
    }
    private void seedRate(Long provider,String service,String region,int fee,boolean active) {
        var ids=jdbc.queryForList("INSERT INTO uteexpress.shipping_rates(provider_id,service_code,destination_region,fee,active) "
                +"VALUES (?,?,?,?,?) ON CONFLICT (provider_id,service_code,destination_region) DO NOTHING RETURNING id",
                Long.class,provider,service,region,fee,active);
        for(Long id:ids) log("RATE_SEEDED","SHIPPING_RATE",id);
    }
    private void log(String action,String target,Long id) {
        audit.append(new AuditEntry(null,action,target,id,Map.of(),Map.of(),"DEMO_SHIP00_V1"));
    }
}
