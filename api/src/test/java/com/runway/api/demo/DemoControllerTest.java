package com.runway.api.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DemoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void listsTheFixedDemoJobsWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/demo/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].id").value("http-get"))
                .andExpect(jsonPath("$[0].type").value("HTTP"))
                .andExpect(jsonPath("$[2].id").value("shell-live-logs"))
                .andExpect(jsonPath("$[3].retryPolicy.maxAttempts").value(3));
    }

    @Test
    void rejectsAnUnknownDemoJob() throws Exception {
        mockMvc.perform(post("/api/demo/jobs/not-a-demo/run")).andExpect(status().isNotFound());
    }

    @Test
    void leavesTheRestOfTheApiClosedToAnonymousCallers() throws Exception {
        mockMvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/jobs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/executions")).andExpect(status().isUnauthorized());
    }

    @Test
    void streamsALiveShellRunToAnAnonymousCaller() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/demo/jobs/shell-live-logs/run"))
                .andExpect(request().asyncStarted())
                .andReturn();

        String body = "";
        long deadline = System.currentTimeMillis() + 20_000;
        while (System.currentTimeMillis() < deadline && !body.contains("\"status\":\"SUCCESS\"")) {
            Thread.sleep(200);
            body = result.getResponse().getContentAsString();
        }

        assertThat(body)
                .contains("event:status", "\"status\":\"RUNNING\"")
                .contains("event:log", "Step 1 of 5", "Step 5 of 5", "Done.")
                .contains("\"stream\":\"STDERR\"", "Something worth a warning")
                .contains("\"status\":\"SUCCESS\"", "\"exitCode\":0");
    }
}
