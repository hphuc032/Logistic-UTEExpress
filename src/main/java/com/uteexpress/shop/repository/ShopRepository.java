package com.uteexpress.shop.repository;

import com.uteexpress.shop.entity.Shop;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface ShopRepository extends Repository<Shop, Long> {
    Optional<Shop> findByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);

    boolean existsBySlug(String slug);

    Shop saveAndFlush(Shop shop);
}
