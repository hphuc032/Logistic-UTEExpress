package com.uteexpress.notification.realtime;

import com.uteexpress.notification.dto.NotificationCommitted;
import java.util.Map;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
public class NotificationSocketListener {
    private final SimpMessagingTemplate broker;
    public NotificationSocketListener(SimpMessagingTemplate broker) { this.broker = broker; }
    @EventListener public void committed(NotificationCommitted event) {
        broker.convertAndSendToUser("uteexpress:user:" + event.recipientId(), "/queue/notifications",
                Map.of("id", event.notificationId(), "title", "Có thông báo mới", "link", "/notifications"));
    }
}
