package com.uteexpress.governance.service;

import com.uteexpress.common.exception.ApplicationException;
import com.uteexpress.common.exception.ErrorCode;
import com.uteexpress.governance.dto.CategoryOption;
import com.uteexpress.governance.entity.Category;
import com.uteexpress.governance.repository.CategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Collection;
import java.util.List;

@Service
public class CategoryQueryService {
    private final CategoryRepository categories;

    public CategoryQueryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional(readOnly = true)
    public List<CategoryOption> listActiveCategories() {
        return categories.findAllByActiveTrueOrderByNameAscIdAsc().stream().map(CategoryQueryService::option).toList();
    }

    @Transactional(readOnly = true)
    public CategoryOption requireActiveCategory(Long categoryId) {
        if (categoryId == null || categoryId <= 0) throw new ApplicationException(ErrorCode.VALIDATION_FAILED);
        return categories.findById(categoryId).filter(Category::isActive).map(CategoryQueryService::option)
                .orElseThrow(() -> new ApplicationException(ErrorCode.VALIDATION_FAILED));
    }

    /** Checkout takes the same category row lock as setActive before checking availability. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void requireActiveForCheckout(Long categoryId) {
        if (categoryId == null || categoryId <= 0) throw new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND);
        categories.findByIdForUpdate(categoryId).filter(Category::isActive)
                .orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public List<CategoryOption> findCategories(Collection<Long> categoryIds) {
        if (categoryIds == null || categoryIds.isEmpty()) return List.of();
        return categories.findAllByIdIn(categoryIds).stream().map(CategoryQueryService::option).toList();
    }

    private static CategoryOption option(Category category) {
        return new CategoryOption(category.getId(), category.getName(), category.isActive());
    }
}
