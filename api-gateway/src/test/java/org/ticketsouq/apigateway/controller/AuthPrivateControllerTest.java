package org.ticketsouq.apigateway.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.ticketsouq.apigateway.service.AuthService;
import org.ticketsouq.apigateway.service.AuthTokenService;

import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthPrivateController.class)
class AuthPrivateControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private AuthService authService;
    @MockitoBean private AuthTokenService authTokenService;

    @Test
    @WithMockUser
    void shouldUnlockOrg() throws Exception {
        UUID orgHeadId = UUID.randomUUID();
        doNothing().when(authService).unlockOrg(orgHeadId);

        mockMvc.perform(post("/api/v1/service/auth/unlock-org")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("\"" + orgHeadId + "\""))
                .andExpect(status().isOk());

        verify(authService).unlockOrg(orgHeadId);
    }
}
