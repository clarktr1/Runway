package com.runway.api.auth;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void registerCreatesAnAccountAndReturnsAToken() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterFixture("Ada Lovelace", "ada@runway.dev", "correct-horse-battery"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()))
                .andExpect(jsonPath("$.role").value("OWNER"))
                .andExpect(jsonPath("$.organizationName").value("Ada Lovelace's Organization"));
    }

    @Test
    void registerRejectsADuplicateEmail() throws Exception {
        String body = objectMapper.writeValueAsString(
                new RegisterFixture("Grace Hopper", "grace@runway.dev", "correct-horse-battery"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginSucceedsWithCorrectPasswordAndFailsWithWrongPassword() throws Exception {
        String registerBody = objectMapper.writeValueAsString(
                new RegisterFixture("Alan Turing", "alan@runway.dev", "correct-horse-battery"));
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(registerBody))
                .andExpect(status().isOk());

        String goodLogin = objectMapper.writeValueAsString(new LoginFixture("alan@runway.dev", "correct-horse-battery"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(goodLogin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token", notNullValue()));

        String badLogin = objectMapper.writeValueAsString(new LoginFixture("alan@runway.dev", "wrong-password"));
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(badLogin))
                .andExpect(status().isUnauthorized());
    }

    private record RegisterFixture(String name, String email, String password) {
    }

    private record LoginFixture(String email, String password) {
    }
}
