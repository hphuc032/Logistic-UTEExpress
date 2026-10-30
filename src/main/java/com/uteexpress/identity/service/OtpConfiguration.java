package com.uteexpress.identity.service;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({OtpProperties.class, VerificationMailProperties.class})
class OtpConfiguration {
}
