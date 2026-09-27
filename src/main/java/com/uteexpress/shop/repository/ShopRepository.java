package com.uteexpress.shop.repository;

import com.uteexpress.shop.entity.Shop;
import com.uteexpress.shop.entity.ShopStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface ShopRepository extends Repository<Shop, Long> {
    Optional<Shop> findByOwnerId(Long ownerId);

    boolean existsByOwnerId(Long ownerId);

    boolean existsBySlug(String slug);

    Shop saveAndFlush(Shop shop);

    Page<Shop> findByStatus(ShopStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Shop s where s.id = :id")
    Optional<Shop> findByIdForUpdate(@Param("id") Long id);

    Optional<Shop> findById(Long id);
}
