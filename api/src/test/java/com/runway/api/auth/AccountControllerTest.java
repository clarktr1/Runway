package com.runway.api.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String email;
    private String token;

    @BeforeEach
    void registerAndAuthenticate() throws Exception {
        email = "account-" + System.nanoTime() + "@runway.dev";
        String body = objectMapper.writeValueAsString(
                Map.of("name", "Account Tester", "email", email, "password", "correct-horse-battery"));

        String response = mockMvc
                .perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        token = objectMapper.readTree(response).get("token").asString();
    }

    @Test
    void deniesRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/account")).andExpect(status().isUnauthorized());
    }

    @Test
    void returnsTheCurrentUsersProfile() throws Exception {
        mockMvc.perform(get("/api/account").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.name").value("Account Tester"));
    }

    @Test
    void updatesEmailWithTheCorrectCurrentPassword() throws Exception {
        String newEmail = "updated-" + System.nanoTime() + "@runway.dev";
        String body = objectMapper.writeValueAsString(
                Map.of("currentPassword", "correct-horse-battery", "email", newEmail));

        mockMvc.perform(patch("/api/account/email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(newEmail));
    }

    @Test
    void rejectsAnEmailChangeToAnAlreadyRegisteredAddress() throws Exception {
        String otherEmail = "taken-" + System.nanoTime() + "@runway.dev";
        String registerOther = objectMapper.writeValueAsString(
                Map.of("name", "Other User", "email", otherEmail, "password", "correct-horse-battery"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerOther))
                .andExpect(status().isOk());

        String body = objectMapper.writeValueAsString(
                Map.of("currentPassword", "correct-horse-battery", "email", otherEmail));

        mockMvc.perform(patch("/api/account/email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsAnEmailChangeWithTheWrongCurrentPassword() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("currentPassword", "totally-wrong-password", "email", "new-" + System.nanoTime() + "@runway.dev"));

        mockMvc.perform(patch("/api/account/email")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updatesPasswordAndAllowsLoginWithTheNewOne() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("currentPassword", "correct-horse-battery", "newPassword", "new-correct-horse-battery"));

        mockMvc.perform(patch("/api/account/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", org.hamcrest.Matchers.notNullValue()));

        String loginBody =
                objectMapper.writeValueAsString(Map.of("email", email, "password", "new-correct-horse-battery"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(loginBody))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsAPasswordChangeWithTheWrongCurrentPassword() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("currentPassword", "totally-wrong-password", "newPassword", "new-correct-horse-battery"));

        mockMvc.perform(patch("/api/account/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }
}
