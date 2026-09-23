package com.uteexpress.governance.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "categories", schema = "uteexpress")
public class Category {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 120)
    private String name;
    @Column(nullable = false, length = 120, unique = true)
    private String slug;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version private Long version;

    protected Category() { }
    public Category(String name, String slug) {
        this.name = name;
        this.slug = slug;
        this.active = true;
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }
    public void rename(String name, String slug) {
        this.name = name;
        this.slug = slug;
        this.updatedAt = Instant.now();
    }
    public void changeActive(boolean active) {
        this.active = active;
        this.updatedAt = Instant.now();
    }
    public Long getId() { return id; }
    public String getName() { return name; }
    public String getSlug() { return slug; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
}
