package com.uteexpress.engagement.dto;
import java.time.Instant;
import java.util.List;
public record ReviewView(long id, int rating, String comment, Instant createdAt, List<Long> mediaIds) {
    public ReviewView { mediaIds=List.copyOf(mediaIds); }
}
