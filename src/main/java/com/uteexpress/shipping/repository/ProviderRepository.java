package com.uteexpress.shipping.repository;

import com.uteexpress.shipping.entity.ShippingProvider;
import org.springframework.data.domain.*;
import org.springframework.data.repository.Repository;
import java.util.*;

public interface ProviderRepository extends Repository<ShippingProvider,Long> {
    Optional<ShippingProvider> findById(Long id);
    Page<ShippingProvider> findAll(Pageable pageable);
    List<ShippingProvider> findByActiveTrueOrderByNameAsc();
    ShippingProvider saveAndFlush(ShippingProvider provider);
}
