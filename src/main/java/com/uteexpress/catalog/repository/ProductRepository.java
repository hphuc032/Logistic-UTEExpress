package com.uteexpress.catalog.repository;

import com.uteexpress.catalog.entity.Product;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Narrow persistence foundation; product mutations remain VENDOR-02 scope. */
public interface ProductRepository extends Repository<Product, Long> {
    @Query("select p from Product p where :query = '' or locate(lower(:query), lower(p.name)) > 0")
    Page<Product> searchForModeration(@Param("query") String query, Pageable pageable);
    Optional<Product> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    Optional<Product> findByIdAndShopId(Long id, Long shopId);

    List<Product> findAllByShopIdOrderByIdDesc(Long shopId);

    Product saveAndFlush(Product product);

    void flush();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id and p.shopId = :shopId")
    Optional<Product> findByIdAndShopIdForUpdate(@Param("id") Long id, @Param("shopId") Long shopId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);
}
