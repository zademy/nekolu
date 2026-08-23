/*
 * Author: Zademy
 * Website: https://zademy.com
 * Last modified: 2026-08-22
 */

package com.zademy.nekolu.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.zademy.nekolu.config.SetupRedirectInterceptor;
import com.zademy.nekolu.exception.TelegramOperationException;
import com.zademy.nekolu.service.FakeTelegramService;
import com.zademy.nekolu.service.TelegramService;

/**
 * First-run wizard tests over standalone MockMvc with the in-memory seam
 * adapter: step rendering per auth state, submissions, error surfacing,
 * and the unauthenticated redirect interceptor.
 */
class SetupControllerTest {

    private FakeTelegramService telegram;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        telegram = new FakeTelegramService();
        mockMvc = MockMvcBuilders.standaloneSetup(new SetupController(telegram))
            // Real redirects + an inert view: the wizard template needs
            // Thymeleaf, which standalone MockMvc does not render —
            // assertions check the view name and model instead.
            .setViewResolvers(
                (viewName, locale) -> viewName.startsWith("redirect:")
                    ? new org.springframework.web.servlet.view.RedirectView(viewName.substring("redirect:".length()))
                    : null,
                (viewName, locale) -> new org.springframework.web.servlet.view.AbstractView() {
                    @Override
                    protected void renderMergedOutputModel(
                            java.util.Map<String, Object> model,
                            jakarta.servlet.http.HttpServletRequest request,
                            jakarta.servlet.http.HttpServletResponse response) {
                        response.setStatus(200);
                    }
                })
            .build();
    }

    @Test
    void setupShowsThePhoneStepWhenWaitingForPhoneNumber() throws Exception {
        telegram.withAuthState(TelegramService.AUTH_STATE_WAIT_PHONE_NUMBER);

        mockMvc.perform(get("/setup"))
            .andExpect(status().isOk())
            .andExpect(view().name("setup"))
            .andExpect(model().attribute("step", 1));
    }

    @Test
    void setupShowsTheCodeStepWhenWaitingForTheCode() throws Exception {
        telegram.withAuthState(TelegramService.AUTH_STATE_WAIT_CODE);

        mockMvc.perform(get("/setup"))
            .andExpect(model().attribute("step", 2));
    }

    @Test
    void setupShowsThePasswordStepWhenWaitingForTwoStepVerification() throws Exception {
        telegram.withAuthState(TelegramService.AUTH_STATE_WAIT_PASSWORD);

        mockMvc.perform(get("/setup"))
            .andExpect(model().attribute("step", 3));
    }

    @Test
    void setupRedirectsHomeWhenAlreadyAuthorized() throws Exception {
        telegram.withAuthState(TelegramService.AUTH_STATE_READY);

        mockMvc.perform(get("/setup"))
            .andExpect(redirectedUrl("/"));
    }

    @Test
    void submittingThePhoneRecordsItAndReturnsToTheWizard() throws Exception {
        telegram.withAuthState(TelegramService.AUTH_STATE_WAIT_PHONE_NUMBER);

        MvcResult started = mockMvc.perform(post("/setup/phone").param("phoneNumber", "+52 55 0000 0000"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(started))
            .andExpect(redirectedUrl("/setup"));

        assertEquals(List.of("phone:+52 55 0000 0000"), telegram.authSubmissions());
    }

    @Test
    void aRejectedSubmissionSurfacesTheTelegramMessage() throws Exception {
        telegram
            .withAuthState(TelegramService.AUTH_STATE_WAIT_CODE)
            .failingWith(new TelegramOperationException(400, "PHONE_CODE_INVALID"));

        MvcResult started = mockMvc.perform(post("/setup/code").param("code", "00000"))
            .andExpect(request().asyncStarted())
            .andReturn();

        String target = mockMvc.perform(asyncDispatch(started))
            .andExpect(status().is3xxRedirection())
            .andReturn().getResponse().getRedirectedUrl();

        assertTrue(target.startsWith("/setup?error="));
        assertTrue(target.contains("PHONE_CODE_INVALID"));
    }

    @Test
    void submittingThePasswordRecordsIt() throws Exception {
        telegram.withAuthState(TelegramService.AUTH_STATE_WAIT_PASSWORD);

        MvcResult started = mockMvc.perform(post("/setup/password").param("password", "cloud-secret"))
            .andExpect(request().asyncStarted())
            .andReturn();

        mockMvc.perform(asyncDispatch(started))
            .andExpect(redirectedUrl("/setup"));

        assertEquals(List.of("password:cloud-secret"), telegram.authSubmissions());
    }

    @Test
    void interceptorRedirectsToSetupWhenUnauthorized() throws Exception {
        telegram.inState(FakeTelegramService.SessionState.UNAUTHORIZED);
        SetupRedirectInterceptor interceptor = new SetupRedirectInterceptor(telegram);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertFalse(proceed);
        assertEquals("/setup", response.getRedirectedUrl());
    }

    @Test
    void interceptorLetsAuthorizedTrafficThrough() throws Exception {
        SetupRedirectInterceptor interceptor = new SetupRedirectInterceptor(telegram);
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean proceed = interceptor.preHandle(new MockHttpServletRequest(), response, new Object());

        assertTrue(proceed);
        assertEquals(null, response.getRedirectedUrl());
    }
}
