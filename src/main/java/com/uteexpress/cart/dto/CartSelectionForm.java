package com.uteexpress.cart.dto;

import jakarta.validation.constraints.NotNull;

public class CartSelectionForm {
    @NotNull
    private Boolean selected = false;
    public Boolean getSelected() { return selected; }
    public void setSelected(Boolean selected) { this.selected = selected; }
}
