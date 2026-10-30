package com.uteexpress.engagement.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class EngagementWebConfig implements WebMvcConfigurer {
    private final EngagementPageInterceptor interceptor;

    public EngagementWebConfig(EngagementPageInterceptor interceptor) { this.interceptor = interceptor; }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/products/*");
    }
}
