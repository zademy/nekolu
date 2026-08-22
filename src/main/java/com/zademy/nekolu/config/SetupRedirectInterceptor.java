/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.config;

import java.io.IOException;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import com.zademy.nekolu.service.TelegramService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Sends web visitors to the first-run authentication wizard while no
 * Telegram session exists. The REST API is excluded on purpose: it keeps
 * answering typed 401s for programmatic clients.
 */
@Component
public class SetupRedirectInterceptor implements HandlerInterceptor {

    private final TelegramService telegramService;

    public SetupRedirectInterceptor(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (telegramService.isAuthorized()) {
            return true;
        }
        response.sendRedirect("/setup");
        return false;
    }
}
