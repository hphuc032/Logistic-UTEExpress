package com.uteexpress.shipping.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="shipping_rates", schema="uteexpress")
public class ShippingRate {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="provider_id", nullable=false) private ShippingProvider provider;
    @Column(name="service_code", nullable=false, length=32) private String serviceCode;
    @Column(name="destination_region", nullable=false, length=20) private String destinationRegion;
    @Column(nullable=false, precision=19, scale=2) private BigDecimal fee;
    @Column(nullable=false) private boolean active;
    @Version private Long version;
    protected ShippingRate() { }
    public ShippingRate(ShippingProvider provider, String serviceCode, String destinationRegion, BigDecimal fee) {
        this.provider=provider; this.serviceCode=serviceCode; this.destinationRegion=destinationRegion;
        this.fee=fee; this.active=true;
    }
    public void update(BigDecimal fee, boolean active) { this.fee=fee; this.active=active; }
    public Long getId() { return id; }
    public ShippingProvider getProvider() { return provider; }
    public String getServiceCode() { return serviceCode; }
    public String getDestinationRegion() { return destinationRegion; }
    public BigDecimal getFee() { return fee; }
    public boolean isActive() { return active; }
    public Long getVersion() { return version; }
}
