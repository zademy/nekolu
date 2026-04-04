/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-04-04
 */

package com.zademy.nekolu.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * MVC controller for Thymeleaf views.
 */
@Controller
public class WebController {

    @GetMapping("/")
    public String dashboard(Model model) {
        model.addAttribute("activePage", "dashboard");
        return "dashboard";
    }

    @GetMapping("/files")
    public String files(Model model) {
        model.addAttribute("activePage", "files");
        return "files";
    }

    @GetMapping("/stats")
    public String stats(Model model) {
        model.addAttribute("activePage", "stats");
        return "stats";
    }

    @GetMapping("/folders")
    public String folders(Model model) {
        model.addAttribute("activePage", "folders");
        return "folders";
    }

    @GetMapping("/folders/{chatId}/files")
    public String folderFiles(@PathVariable long chatId, Model model) {
        model.addAttribute("activePage", "folders");
        model.addAttribute("chatId", chatId);
        return "folder-files";
    }
}
