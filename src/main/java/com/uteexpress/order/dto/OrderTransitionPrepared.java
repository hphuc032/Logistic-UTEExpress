package com.uteexpress.order.dto;

/** Internal lifecycle fact after history flush, before commit. Never a client command or committed event. */
public record OrderTransitionPrepared(Long orderId) { }
