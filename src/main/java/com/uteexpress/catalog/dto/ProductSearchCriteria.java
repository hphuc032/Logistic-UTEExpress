package com.uteexpress.catalog.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** Browser-owned discovery fields only; no status, ownership, entity, or raw SQL field is accepted. */
public class ProductSearchCriteria {
    public static final int DEFAULT_SIZE = 12;
    public static final int MAX_SIZE = 48;

    @Size(max = 120, message = "Từ khóa không được vượt quá 120 ký tự.")
    private String q;

    @Size(max = 120, message = "Cửa hàng không hợp lệ.")
    @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "Cửa hàng không hợp lệ.")
    private String shop;

    @Size(max = 120, message = "Danh mục không hợp lệ.")
    @Pattern(regexp = "[a-z0-9]+(?:-[a-z0-9]+)*", message = "Danh mục không hợp lệ.")
    private String category;

    @DecimalMin(value = "0", message = "Giá tối thiểu không được âm.")
    @Digits(integer = 17, fraction = 0, message = "Giá tối thiểu phải là số nguyên VND.")
    private BigDecimal minPrice;

    @DecimalMin(value = "0", message = "Giá tối đa không được âm.")
    @Digits(integer = 17, fraction = 0, message = "Giá tối đa phải là số nguyên VND.")
    private BigDecimal maxPrice;

    @Size(max = 64, message = "Cách sắp xếp không hợp lệ.")
    private String sort = ProductSort.NEWEST.parameter();

    @Min(value = 0, message = "Trang không được âm.")
    private Integer page = 0;

    @Min(value = 1, message = "Kích thước trang phải từ 1 đến 48.")
    @Max(value = MAX_SIZE, message = "Kích thước trang phải từ 1 đến 48.")
    private Integer size = DEFAULT_SIZE;

    public String getQ() { return q; }
    public void setQ(String q) { this.q = blankToNull(q); }
    public String getShop() { return shop; }
    public void setShop(String shop) { this.shop = blankToNull(shop); }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = blankToNull(category); }
    public BigDecimal getMinPrice() { return minPrice; }
    public void setMinPrice(BigDecimal minPrice) { this.minPrice = minPrice; }
    public BigDecimal getMaxPrice() { return maxPrice; }
    public void setMaxPrice(BigDecimal maxPrice) { this.maxPrice = maxPrice; }
    public String getSort() { return sort; }
    public void setSort(String sort) { this.sort = blankToNull(sort); }
    public Integer getPage() { return page; }
    public void setPage(Integer page) { this.page = page; }
    public Integer getSize() { return size; }
    public void setSize(Integer size) { this.size = size; }

    @AssertTrue(message = "Giá tối thiểu không được lớn hơn giá tối đa.")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }

    public ProductSearchCriteria normalized() {
        ProductSearchCriteria normalized = new ProductSearchCriteria();
        normalized.q = blankToNull(q);
        normalized.shop = blankToNull(shop);
        normalized.category = blankToNull(category);
        normalized.minPrice = minPrice;
        normalized.maxPrice = maxPrice;
        normalized.sort = ProductSort.fromParameter(sort).parameter();
        normalized.page = page == null ? 0 : page;
        normalized.size = size == null ? DEFAULT_SIZE : size;
        return normalized;
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
