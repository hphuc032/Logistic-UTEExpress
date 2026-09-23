package com.uteexpress.shipping.entity;

import jakarta.persistence.*;

@Entity @Table(name="shipping_providers", schema="uteexpress")
public class ShippingProvider {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false, unique=true, length=32) private String code;
    @Column(nullable=false, length=120) private String name;
    @Column(nullable=false) private boolean active;
    @Version private Long version;
    protected ShippingProvider() { }
    public ShippingProvider(String code, String name) { this.code=code; this.name=name; this.active=true; }
    public void update(String name, boolean active) { this.name=name; this.active=active; }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
}
