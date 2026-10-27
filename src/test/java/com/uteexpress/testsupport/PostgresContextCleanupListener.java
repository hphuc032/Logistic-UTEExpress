package com.uteexpress.testsupport;

import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.testcontainers.junit.jupiter.Testcontainers;

/** A cached Spring context must not outlive its class-scoped JUnit Testcontainers database. */
public class PostgresContextCleanupListener extends AbstractTestExecutionListener {
    @Override public int getOrder() { return DirtiesContextTestExecutionListener.ORDER + 1; }
    @Override public void afterTestClass(TestContext context) {
        // No lazy context creation; leave non-container unit/web test caching unchanged.
        // Current suite uses sequential classes. Parallel shared contexts need a separate lifecycle design.
        if (AnnotatedElementUtils.hasAnnotation(context.getTestClass(), Testcontainers.class)
                && context.hasApplicationContext()) context.markApplicationContextDirty(HierarchyMode.EXHAUSTIVE);
    }
}
