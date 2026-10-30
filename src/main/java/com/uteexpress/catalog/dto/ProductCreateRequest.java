package com.uteexpress.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class ProductCreateRequest {
    @NotBlank(message = "Tên sản phẩm là bắt buộc.")
    @Size(max = 200, message = "Tên sản phẩm không được vượt quá 200 ký tự.")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]+$", message = "Tên sản phẩm chứa ký tự không hợp lệ.")
    private String name;

    @Size(max = 4000, message = "Mô tả không được vượt quá 4000 ký tự.")
    @Pattern(regexp = "^[^\\p{Cc}\\p{Cf}]*$", message = "Mô tả chứa ký tự không hợp lệ.")
    private String description;

    @NotNull(message = "Giá sản phẩm là bắt buộc.")
    @DecimalMin(value = "1", message = "Giá phải lớn hơn 0.")
    @Digits(integer = 17, fraction = 0, message = "Giá phải là số nguyên VND.")
    private BigDecimal price;

    @NotNull(message = "Tồn kho là bắt buộc.")
    @Min(value = 0, message = "Tồn kho không được âm.")
    private Integer stock;

    @NotNull(message = "Danh mục là bắt buộc.")
    @Positive(message = "Danh mục không hợp lệ.")
    private Long categoryId;

    public String getName() { return name; }
    public void setName(String name) { this.name = trim(name); }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = blankToNull(description); }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public Integer getStock() { return stock; }
    public void setStock(Integer stock) { this.stock = stock; }
    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }

    private static String trim(String value) { return value == null ? null : value.trim(); }
    private static String blankToNull(String value) {
        String trimmed = trim(value);
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
