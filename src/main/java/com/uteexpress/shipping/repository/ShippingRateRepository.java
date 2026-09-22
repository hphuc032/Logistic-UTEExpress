package com.uteexpress.shipping.repository;

import com.uteexpress.shipping.entity.ShippingRate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface ShippingRateRepository extends Repository<ShippingRate,Long> {
    @EntityGraph(attributePaths="provider")
    Optional<ShippingRate> findById(Long id);
    @EntityGraph(attributePaths="provider")
    Page<ShippingRate> findAll(Pageable pageable);
    ShippingRate saveAndFlush(ShippingRate rate);
    @Query("select r from ShippingRate r join fetch r.provider p where p.id=:providerId and p.active=true "
            + "and r.active=true and r.serviceCode=:service and r.destinationRegion=:region")
    Optional<ShippingRate> findQuote(@Param("providerId") Long providerId,
            @Param("service") String service, @Param("region") String region);
}
