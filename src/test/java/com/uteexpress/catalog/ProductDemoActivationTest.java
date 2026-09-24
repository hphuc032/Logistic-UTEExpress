package com.uteexpress.catalog;

import com.uteexpress.catalog.service.ProductDemoSeeder;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ProductDemoActivationTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(ProductDemoSeeder.class)
            .withBean(JdbcTemplate.class, () -> mock(JdbcTemplate.class));

    @Test
    void requiresDemoProfileAndExplicitOptInAndNeverRunsWithProd() {
        context.run(c -> assertThat(c).doesNotHaveBean(ProductDemoSeeder.class));
        context.withPropertyValues("uteexpress.demo.catalog.enabled=true")
                .run(c -> assertThat(c).doesNotHaveBean(ProductDemoSeeder.class));
        context.withPropertyValues("spring.profiles.active=demo")
                .run(c -> assertThat(c).doesNotHaveBean(ProductDemoSeeder.class));
        context.withPropertyValues("spring.profiles.active=demo", "uteexpress.demo.catalog.enabled=true")
                .run(c -> assertThat(c).hasSingleBean(ProductDemoSeeder.class));
        context.withPropertyValues("spring.profiles.active=demo,prod", "uteexpress.demo.catalog.enabled=true")
                .run(c -> assertThat(c).doesNotHaveBean(ProductDemoSeeder.class));
    }
}
