package com.uteexpress.payment.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "uteexpress.payment.vnpay.enabled", havingValue = "true")
@EnableConfigurationProperties(VnpayProperties.class)
public class VnpayConfiguration { }
