package com.runway.api.executions;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class ExecutionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;

    @BeforeEach
    void registerAndAuthenticate() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Execution Tester",
                "email", "executions-" + System.nanoTime() + "@runway.dev",
                "password", "correct-horse-battery"));

        String response = mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        token = objectMapper.readTree(response).get("token").asString();
    }

    private String createJob(String cronExpression) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Nightly Backup",
                "type", "SHELL",
                "configuration", Map.of("command", "/opt/scripts/backup.sh"),
                "enabled", true,
                "maxConcurrency", 1,
                "cronExpression", cronExpression));

        String response = mockMvc.perform(post("/api/jobs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).get("id").asString();
    }

    @Test
    void runningAJobManuallyQueuesAnExecution() throws Exception {
        String jobId = createJob("0 2 * * *");

        mockMvc.perform(post("/api/jobs/" + jobId + "/run").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.triggerType").value("MANUAL"))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.attempt").value(1));

        mockMvc.perform(get("/api/executions?jobId=" + jobId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].jobId").value(jobId))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void runningAnUnknownJobReturnsNotFound() throws Exception {
        mockMvc.perform(post("/api/jobs/" + java.util.UUID.randomUUID() + "/run")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void listingExecutionsCanFilterByStatus() throws Exception {
        String jobId = createJob("0 2 * * *");
        mockMvc.perform(post("/api/jobs/" + jobId + "/run").header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/executions?status=QUEUED").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
