package com.runway.api.jobs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
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
class JobControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void registerAndAuthenticate() throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("name", "Job Tester", "email", "jobs-" + System.nanoTime() + "@runway.dev", "password", "correct-horse-battery"));

        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        token = objectMapper.readTree(response).get("token").asString();
    }

    @Test
    void deniesRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsListsUpdatesAndDeletesAJob() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "name", "Nightly Backup",
                "type", "SHELL",
                "configuration", Map.of("command", "/opt/scripts/backup.sh"),
                "enabled", true,
                "maxConcurrency", 1,
                "cronExpression", "0 2 * * *"));

        String createResponse = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nightly Backup"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createResponse);
        String jobId = created.get("id").asString();

        mockMvc.perform(get("/api/jobs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(jobId));

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "name", "Nightly Backup (renamed)",
                "type", "SHELL",
                "configuration", Map.of("command", "/opt/scripts/backup.sh"),
                "enabled", false,
                "maxConcurrency", 1,
                "cronExpression", "0 3 * * *"));

        mockMvc.perform(patch("/api/jobs/" + jobId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Nightly Backup (renamed)"))
                .andExpect(jsonPath("$.enabled").value(false));

        mockMvc.perform(delete("/api/jobs/" + jobId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/jobs/" + jobId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAShellJobMissingItsCommand() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Broken Job", "type", "SHELL", "configuration", Map.of(), "enabled", true, "maxConcurrency", 1));

        mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
