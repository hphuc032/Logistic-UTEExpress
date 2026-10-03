package com.uteexpress.governance.repository;

import com.uteexpress.governance.entity.Category;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.Collection;
import java.util.List;

/** No delete operation: product references must survive category visibility changes. */
public interface CategoryRepository extends Repository<Category, Long> {
    Optional<Category> findById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Category c where c.id = :id")
    Optional<Category> findByIdForUpdate(@Param("id") Long id);
    List<Category> findAllByActiveTrueOrderByNameAscIdAsc();
    List<Category> findAllByIdIn(Collection<Long> ids);
    Page<Category> findAll(Pageable pageable);
    Category saveAndFlush(Category category);
}
