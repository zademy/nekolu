/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-12
 */

package com.zademy.nekolu.config;

import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import com.zademy.nekolu.service.TelegramService;

/**
 * Web MVC configuration for internationalization support and the first-run
 * setup redirect.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final TelegramService telegramService;

    public WebConfig(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @Bean
    public LocaleResolver localeResolver() {
        AcceptHeaderLocaleResolver resolver = new AcceptHeaderLocaleResolver();
        resolver.setDefaultLocale(Locale.ENGLISH);
        return resolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        return interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
        registry.addInterceptor(new SetupRedirectInterceptor(telegramService))
            .addPathPatterns("/**")
            .excludePathPatterns(
                "/setup",
                "/setup/**",
                "/css/**",
                "/js/**",
                "/vendors/**",
                "/api/**",
                "/actuator/**",
                "/error",
                "/favicon.ico");
    }
}
