package com.uteexpress.governance.repository;

import com.uteexpress.governance.entity.Category;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import java.util.Optional;
import java.util.Collection;
import java.util.List;

/** No delete operation: product references must survive category visibility changes. */
public interface CategoryRepository extends Repository<Category, Long> {
    Optional<Category> findById(Long id);
    List<Category> findAllByActiveTrueOrderByNameAscIdAsc();
    List<Category> findAllByIdIn(Collection<Long> ids);
    Page<Category> findAll(Pageable pageable);
    Category saveAndFlush(Category category);
}
