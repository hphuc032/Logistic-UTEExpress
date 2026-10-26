package com.uteexpress.notification.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.messaging.simp.config.*;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.messaging.web.socket.server.CsrfTokenHandshakeInterceptor;
import org.springframework.web.socket.config.annotation.*;

@Configuration(proxyBeanMethods = false)
@EnableWebSocketMessageBroker
@EnableScheduling
public class NotificationSocketConfiguration implements WebSocketMessageBrokerConfigurer {
    private final SocketHandshake handshake;
    private final SocketInboundGuard guard;
    private final SocketConnections connections;
    private final TaskScheduler heartbeats;
    public NotificationSocketConfiguration(SocketHandshake handshake, SocketInboundGuard guard, SocketConnections connections,
            @Qualifier("wsBrokerScheduler") TaskScheduler heartbeats) {
        this.handshake = handshake; this.guard = guard; this.connections = connections; this.heartbeats = heartbeats;
    }
    @Bean(name = "wsBrokerScheduler")
    static ThreadPoolTaskScheduler brokerScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1); scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }
    @Bean(name = "taskScheduler")
    static ThreadPoolTaskScheduler socketSweepScheduler() {
        var scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(2); scheduler.setThreadNamePrefix("notification-scheduled-");
        return scheduler;
    }
    @Override public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws").addInterceptors(handshake, new CsrfTokenHandshakeInterceptor());
    }
    @Override public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/queue").setTaskScheduler(heartbeats).setHeartbeatValue(new long[] {10000, 10000});
        registry.setUserDestinationPrefix("/user");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setPreservePublishOrder(true);
    }
    @Override public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(guard);
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4).queueCapacity(256);
    }
    @Override public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor().corePoolSize(2).maxPoolSize(4).queueCapacity(256);
    }
    @Override public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setMessageSizeLimit(8192).setSendBufferSizeLimit(32768).setSendTimeLimit(2000)
                .setTimeToFirstMessage(10000).addDecoratorFactory(connections::decorate);
    }
}
