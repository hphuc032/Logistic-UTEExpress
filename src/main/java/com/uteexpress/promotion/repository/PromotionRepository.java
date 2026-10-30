package com.uteexpress.promotion.repository;

import com.uteexpress.promotion.entity.Promotion;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PromotionRepository extends Repository<Promotion, Long> {
    List<Promotion> findAllByProductIdOrderByStartsAtDescIdDesc(Long productId);
    Optional<Promotion> findByIdAndProductId(Long id, Long productId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Promotion p where p.id = :id and p.productId = :productId")
    Optional<Promotion> findOwnedForUpdate(@Param("id") Long id, @Param("productId") Long productId);
    @Query("select count(p) from Promotion p where p.productId = :productId and p.active = true "
            + "and p.startsAt < :end and :start < p.endsAt and (:excludeId is null or p.id <> :excludeId)")
    long countOverlaps(@Param("productId") Long productId, @Param("start") Instant start,
            @Param("end") Instant end, @Param("excludeId") Long excludeId);
    Promotion saveAndFlush(Promotion promotion);
}
