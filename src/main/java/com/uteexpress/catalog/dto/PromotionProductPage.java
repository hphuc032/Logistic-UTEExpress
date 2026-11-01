package com.uteexpress.catalog.dto;
import java.util.List;
public record PromotionProductPage(List<PromotionProductOption> items, String query, int page, boolean hasPrevious, boolean hasNext) { }
