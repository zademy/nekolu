/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultHandlers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.zademy.nekolu.exception.TelegramOperationException;
import com.zademy.nekolu.service.FakeTelegramService;
import com.zademy.nekolu.service.FakeTelegramService.SessionState;
import com.zademy.nekolu.service.impl.FileServiceImpl;
import com.zademy.nekolu.service.impl.MetadataIndexServiceImpl;
import com.zademy.nekolu.service.impl.UploadStagingArea;

/**
 * HTTP error-contract tests over standalone MockMvc: the controllers are
 * instantiated with the in-memory seam adapter, so the typed error modes
 * translate to their HTTP statuses without TDLib or a Spring context.
 */
class FileControllerTest {

    @TempDir
    File stagingDir;

    private FakeTelegramService telegram;
    private MockMvc filesApi;
    private MockMvc foldersApi;

    @BeforeEach
    void setUp() {
        telegram = new FakeTelegramService();
        FileServiceImpl fileService = new FileServiceImpl(
            telegram,
            new MetadataIndexServiceImpl(),
            new UploadStagingArea(stagingDir.toPath()),
            Caffeine.newBuilder().build());

        filesApi = MockMvcBuilders.standaloneSetup(new FileController(fileService, telegram))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
        foldersApi = MockMvcBuilders.standaloneSetup(new TelegramController(telegram))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
    }

    @Test
    void unauthorizedSessionYieldsUniform401() throws Exception {
        telegram.inState(SessionState.UNAUTHORIZED);

        MvcResult started = filesApi.perform(get("/api/telegram/files/500"))
            .andExpect(request().asyncStarted())
            .andReturn();

        filesApi.perform(asyncDispatch(started))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.status").value(401));
    }

    @Test
    void notInitializedModuleYields503() throws Exception {
        telegram.inState(SessionState.UNINITIALIZED);

        MvcResult started = filesApi.perform(get("/api/telegram/files/500"))
            .andExpect(request().asyncStarted())
            .andReturn();

        filesApi.perform(asyncDispatch(started))
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.status").value(503));
    }

    @Test
    void telegramRejectedOperationYields502() throws Exception {
        telegram.failingWith(new TelegramOperationException(400, "PEER_ID_INVALID"));

        MvcResult started = filesApi.perform(get("/api/telegram/files/500"))
            .andExpect(request().asyncStarted())
            .andReturn();

        filesApi.perform(asyncDispatch(started))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.status").value(502));
    }

    @Test
    void unknownFileYields404() throws Exception {
        MvcResult started = filesApi.perform(get("/api/telegram/files/404"))
            .andExpect(request().asyncStarted())
            .andReturn();

        filesApi.perform(asyncDispatch(started))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rejectedFolderCreationStaysABusinessResponse() throws Exception {
        // A Telegram-rejected operation is a business outcome: 400 with the
        // folder DTO reporting failure, not a transport error.
        telegram.failingWith(new TelegramOperationException(400, "TITLE_INVALID"));

        MvcResult started = foldersApi
            .perform(post("/api/telegram/folders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"bad?\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        foldersApi.perform(asyncDispatch(started))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void unauthorizedFolderCreationYields401NotABusinessResponse() throws Exception {
        // Session failures must not be masked as business outcomes.
        telegram.inState(SessionState.UNAUTHORIZED);

        MvcResult started = foldersApi
            .perform(post("/api/telegram/folders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"x\"}"))
            .andExpect(request().asyncStarted())
            .andReturn();

        foldersApi.perform(asyncDispatch(started))
            .andExpect(status().isUnauthorized());
    }

    private static org.springframework.test.web.servlet.RequestBuilder asyncDispatch(MvcResult result) {
        return MockMvcRequestBuilders.asyncDispatch(result);
    }
}
