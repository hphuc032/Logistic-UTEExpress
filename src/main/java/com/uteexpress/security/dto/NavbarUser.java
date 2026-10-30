package com.uteexpress.security.dto;

/** Presentation flags only; endpoint authorization remains in the security/service layers. */
public record NavbarUser(String displayUsername, boolean vendor, boolean manager, boolean admin, boolean shipper) {
}
