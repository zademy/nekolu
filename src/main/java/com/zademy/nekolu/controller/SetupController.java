/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.controller;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.zademy.nekolu.exception.Exceptions;
import com.zademy.nekolu.service.TelegramService;

/**
 * First-run authentication wizard: shows the step TDLib is waiting for and
 * submits the phone number, verification code, and two-step password. The
 * state machine lives behind the Telegram seam; this controller only
 * shapes the presentation.
 */
@Controller
@RequestMapping("/setup")
public class SetupController {

    private final TelegramService telegramService;

    public SetupController(TelegramService telegramService) {
        this.telegramService = telegramService;
    }

    @GetMapping
    public String setup(Model model, @RequestParam(required = false) String error) {
        String authState = telegramService.getAuthState();
        if (TelegramService.AUTH_STATE_READY.equals(authState)) {
            return "redirect:/";
        }
        model.addAttribute("authState", authState);
        model.addAttribute("step", stepFor(authState));
        model.addAttribute("error", error);
        return "setup";
    }

    @PostMapping("/phone")
    public CompletableFuture<String> submitPhone(@RequestParam("phoneNumber") String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return CompletableFuture.completedFuture("redirect:/setup");
        }
        return telegramService.submitPhoneNumber(phoneNumber.trim())
            .<String>thenApply(_v -> "redirect:/setup")
            .exceptionally(ex -> "redirect:/setup?error=" + encode(Exceptions.unwrap(ex).getMessage()));
    }

    @PostMapping("/code")
    public CompletableFuture<String> submitCode(@RequestParam("code") String code) {
        if (code == null || code.isBlank()) {
            return CompletableFuture.completedFuture("redirect:/setup");
        }
        return telegramService.submitAuthCode(code.trim())
            .<String>thenApply(_v -> "redirect:/setup")
            .exceptionally(ex -> "redirect:/setup?error=" + encode(Exceptions.unwrap(ex).getMessage()));
    }

    @PostMapping("/password")
    public CompletableFuture<String> submitPassword(@RequestParam("password") String password) {
        if (password == null || password.isBlank()) {
            return CompletableFuture.completedFuture("redirect:/setup");
        }
        return telegramService.submitAuthPassword(password)
            .<String>thenApply(_v -> "redirect:/setup")
            .exceptionally(ex -> "redirect:/setup?error=" + encode(Exceptions.unwrap(ex).getMessage()));
    }

    private int stepFor(String authState) {
        if (TelegramService.AUTH_STATE_WAIT_CODE.equals(authState)) {
            return 2;
        }
        if (TelegramService.AUTH_STATE_WAIT_PASSWORD.equals(authState)) {
            return 3;
        }
        return 1;
    }

    private static String encode(String message) {
        return URLEncoder.encode(message != null ? message : "Authentication failed", StandardCharsets.UTF_8);
    }
}
