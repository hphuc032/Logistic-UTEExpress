package com.uteexpress.shipping;

import com.uteexpress.shipping.service.ShippingDemoSeeder;
import com.uteexpress.governance.service.AuditLogService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThat;

class ShippingDemoActivationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ShippingDemoSeeder.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class))
            .withBean(AuditLogService.class, () -> mock(AuditLogService.class));

    @Test void requiresDemoProfileAndExplicitOptInAndNeverRunsWithProd() {
        context.run(c -> assertThat(c).doesNotHaveBean(ShippingDemoSeeder.class));
        context.withPropertyValues("uteexpress.demo.shipping.enabled=true")
                .run(c -> assertThat(c).doesNotHaveBean(ShippingDemoSeeder.class));
        context.withPropertyValues("spring.profiles.active=demo")
                .run(c -> assertThat(c).doesNotHaveBean(ShippingDemoSeeder.class));
        context.withPropertyValues("spring.profiles.active=demo", "uteexpress.demo.shipping.enabled=true")
                .run(c -> assertThat(c).hasSingleBean(ShippingDemoSeeder.class));
        context.withPropertyValues("spring.profiles.active=demo,prod", "uteexpress.demo.shipping.enabled=true")
                .run(c -> assertThat(c).doesNotHaveBean(ShippingDemoSeeder.class));
    }
}
