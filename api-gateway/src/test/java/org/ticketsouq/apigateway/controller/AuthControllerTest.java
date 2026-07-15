package org.ticketsouq.apigateway.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.ticketsouq.apigateway.dto.AuthResponse;
import org.ticketsouq.apigateway.dto.LoginRequest;
import org.ticketsouq.apigateway.dto.RegisterRequest;
import org.ticketsouq.apigateway.dto.ResetPasswordRequest;
import org.ticketsouq.apigateway.service.AuthService;
import org.ticketsouq.apigateway.service.AuthTokenService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private AuthTokenService authTokenService;

    @Test
    void register_shouldReturn201() throws Exception {
        var req = new RegisterRequest("test@test.com", "Test User", "password123", null);
        doNothing().when(authService).register(any(RegisterRequest.class));

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated());

        verify(authService).register(any(RegisterRequest.class));
    }

    @Test
    void login_shouldReturn200() throws Exception {
        var req = new LoginRequest("test@test.com", "password");
        when(authService.login(any(LoginRequest.class)))
            .thenReturn(new AuthResponse("access", "refresh"));

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.access").value("access"))
            .andExpect(jsonPath("$.refresh").value("refresh"));
    }

    @Test
    void logout_shouldReturn204() throws Exception {
        doNothing().when(authService).logout("token");

        mockMvc.perform(post("/api/v1/auth/logout")
                .with(authentication(new UsernamePasswordAuthenticationToken("user", null, List.of(new SimpleGrantedAuthority("ROLE_USER")))))
                .header("Authorization", "Bearer token"))
            .andExpect(status().isNoContent());

        verify(authService).logout("token");
    }

//    @Test
//    void logoutAll_shouldReturn204() throws Exception {
//        doNothing().when(authService).logoutFromAllDevices(any(UUID.class));
//
//
//        mockMvc.perform(post("/api/v1/auth/logout-all")
//            .with(user("550e8400-e29b-41d4-a716-446655440000")));
//
//        verify(authService).logoutFromAllDevices(any(UUID.class));
//    }

    @Test
    void emailVarification_shouldTriggerVerificationEmail() throws Exception {
        doNothing().when(authService).triggerVerificationEmail("test@test.com");

        mockMvc.perform(get("/api/v1/auth/email-varification")
                .param("email", "test@test.com"))
            .andExpect(status().isOk());
    }

    @Test
    void emailVarification_shouldVerifyEmail() throws Exception {
        doNothing().when(authService).verifyEmail("verify-token");

        mockMvc.perform(post("/api/v1/auth/email-varification")
                .contentType(MediaType.TEXT_PLAIN)
                .content("verify-token"))
            .andExpect(status().isOk());
    }

    @Test
    void passwordForgot_shouldTriggerPasswordReset() throws Exception {
        doNothing().when(authService).triggerPasswordReset("test@test.com");

        mockMvc.perform(get("/api/v1/auth/password-forgot")
                .param("email", "test@test.com"))
            .andExpect(status().isOk());
    }

    @Test
    void passwordForgot_shouldResetPassword() throws Exception {
        var req = new ResetPasswordRequest("reset-token", "newPassword123");
        doNothing().when(authService).resetPassword(any(ResetPasswordRequest.class));

        mockMvc.perform(post("/api/v1/auth/password-forgot")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk());
    }

//    @Test
//    void changePassword_shouldReturn204() throws Exception {
//        var req = new ChangePasswordRequest("currentPass", "newPass123");
//        doNothing().when(authService).changePassword(any(UUID.class), any(ChangePasswordRequest.class));
//
//        Authentication authentication = new UsernamePasswordAuthenticationToken(
//            "550e8400-e29b-41d4-a716-446655440000",
//            null,
//            Collections.emptyList());
//
//        mockMvc.perform(put("/api/v1/auth/password")
//                .with(authentication(authentication))
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(req)))
//            .andExpect(status().isNoContent());
//    }
//
//    @Test
//    void deactivateAccount_shouldReturn204() throws Exception {
//        doNothing().when(authService).deactivateAccount(any(UUID.class));
//
//        Authentication authentication = new UsernamePasswordAuthenticationToken(
//            "550e8400-e29b-41d4-a716-446655440000",
//            null,
//            Collections.emptyList());
//
//        mockMvc.perform(delete("/api/v1/auth")
//                .with(authentication(authentication)))
//            .andExpect(status().isNoContent());
//    }
//
//    @Test
//    void generateAccounts_shouldReturn200() throws Exception {
//        var req = new GenerateAccountRequest(1, 0);
//        var accounts = List.of(new GeneratedAccount("uid", "agent@test.com", "pass", "ORG_Agent"));
//        when(authService.generateAccountsForOrg(any(UUID.class), any(GenerateAccountRequest.class)))
//            .thenReturn(accounts);
//
//        Authentication authentication = new UsernamePasswordAuthenticationToken(
//            "550e8400-e29b-41d4-a716-446655440000",
//            null,
//            Collections.emptyList());
//
//        mockMvc.perform(post("/api/v1/auth/org/generate-accounts")
//                .with(authentication(authentication))
//                .contentType(MediaType.APPLICATION_JSON)
//                .content(objectMapper.writeValueAsString(req)))
//            .andExpect(status().isOk())
//            .andExpect(jsonPath("$[0].email").value("agent@test.com"));
//    }
}
