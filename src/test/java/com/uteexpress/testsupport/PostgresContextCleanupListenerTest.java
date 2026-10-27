package com.uteexpress.testsupport;

import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.test.annotation.DirtiesContext.HierarchyMode;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PostgresContextCleanupListenerTest {
    @Testcontainers static class ContainerCase { }
    static class PlainCase { }
    final PostgresContextCleanupListener listener=new PostgresContextCleanupListener();
    @Test void closesOnlyLoadedContainerContexts() {
        var context=mock(TestContext.class);doReturn(ContainerCase.class).when(context).getTestClass();
        when(context.hasApplicationContext()).thenReturn(true);
        listener.afterTestClass(context);
        verify(context).markApplicationContextDirty(HierarchyMode.EXHAUSTIVE);
        verify(context,never()).getApplicationContext();
        assertThat(listener.getOrder()).isEqualTo(DirtiesContextTestExecutionListener.ORDER+1);
    }
    @Test void nonContainerContextCacheIsUnaffected() {
        var context=mock(TestContext.class);doReturn(PlainCase.class).when(context).getTestClass();
        listener.afterTestClass(context);verify(context,never()).markApplicationContextDirty(any());
        verify(context,never()).getApplicationContext();
    }
    @Test void doesNotRecreateAlreadyClosedOrUnloadedContainerContext() {
        var context=mock(TestContext.class);doReturn(ContainerCase.class).when(context).getTestClass();
        when(context.hasApplicationContext()).thenReturn(false);
        listener.afterTestClass(context);verify(context,never()).markApplicationContextDirty(any());
        verify(context,never()).getApplicationContext();
    }
    @Test void testOnlyMetadataRegistersListener() throws Exception {
        var resources=getClass().getClassLoader().getResources("META-INF/spring.factories");boolean found=false;
        while(resources.hasMoreElements()) {
            var properties=new Properties();try(var input=resources.nextElement().openStream()){properties.load(input);}
            if(properties.getProperty("org.springframework.test.context.TestExecutionListener","").contains(PostgresContextCleanupListener.class.getName())) found=true;
        }
        assertThat(found).isTrue();
    }
}
