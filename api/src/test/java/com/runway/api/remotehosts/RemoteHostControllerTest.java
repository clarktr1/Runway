package com.runway.api.remotehosts;

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
class RemoteHostControllerTest {

    private static final String ED25519_PRIVATE_KEY =
            """
            -----BEGIN OPENSSH PRIVATE KEY-----
            b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW
            QyNTUxOQAAACBhcQqEXXrBSLaF6QmSVbHtU7gu/aub9/ik7pivAwCiBgAAAJhstTV+bLU1
            fgAAAAtzc2gtZWQyNTUxOQAAACBhcQqEXXrBSLaF6QmSVbHtU7gu/aub9/ik7pivAwCiBg
            AAAED+G5biVjxsRt2rGCaDzNCtrNXgCw3RvgTwAeJuXTIeEWFxCoRdesFItoXpCZJVse1T
            uC79q5v3+KTumK8DAKIGAAAAD3J1bndheS10ZXN0LWtleQECAwQFBg==
            -----END OPENSSH PRIVATE KEY-----
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private String token;
    private String credentialId;

    @BeforeEach
    void registerAuthenticateAndCreateACredential() throws Exception {
        token = registerAndGetToken("remote-hosts-" + System.nanoTime() + "@runway.dev");
        credentialId = createCredential(token, "Deploy Key");
    }

    @Test
    void deniesRequestsWithoutAToken() throws Exception {
        mockMvc.perform(get("/api/remote-hosts")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsListsUpdatesAndDeletesAHost() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "name", "Web Server 1",
                "hostname", "web1.internal",
                "port", 22,
                "username", "deploy",
                "sshCredentialId", credentialId));

        String createResponse = mockMvc
                .perform(post("/api/remote-hosts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Web Server 1"))
                .andExpect(jsonPath("$.pinnedHostKeyFingerprint").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String hostId = objectMapper.readTree(createResponse).get("id").asString();

        mockMvc.perform(get("/api/remote-hosts").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(hostId));

        String updateBody = objectMapper.writeValueAsString(Map.of(
                "name", "Web Server 1 (renamed)",
                "hostname", "web1.internal",
                "port", 2222,
                "username", "deploy",
                "sshCredentialId", credentialId));

        mockMvc.perform(patch("/api/remote-hosts/" + hostId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Web Server 1 (renamed)"))
                .andExpect(jsonPath("$.port").value(2222));

        mockMvc.perform(delete("/api/remote-hosts/" + hostId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/remote-hosts/" + hostId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void rejectsAnUnknownSshCredentialId() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name", "Broken Host",
                "hostname", "example.internal",
                "port", 22,
                "username", "deploy",
                "sshCredentialId", "00000000-0000-0000-0000-000000000000"));

        mockMvc.perform(post("/api/remote-hosts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void isolatesRemoteHostsBetweenOrganizations() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "name", "Org A Host",
                "hostname", "a.internal",
                "port", 22,
                "username", "deploy",
                "sshCredentialId", credentialId));
        String createResponse = mockMvc
                .perform(post("/api/remote-hosts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String hostId = objectMapper.readTree(createResponse).get("id").asString();

        String otherToken = registerAndGetToken("other-org-" + System.nanoTime() + "@runway.dev");

        mockMvc.perform(get("/api/remote-hosts/" + hostId).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/remote-hosts").header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void testConnectionAgainstAnUnreachableHostFailsWithoutPinning() throws Exception {
        String createBody = objectMapper.writeValueAsString(Map.of(
                "name", "Unreachable Host",
                "hostname", "localhost",
                "port", 1,
                "username", "deploy",
                "sshCredentialId", credentialId));
        String createResponse = mockMvc
                .perform(post("/api/remote-hosts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String hostId = objectMapper.readTree(createResponse).get("id").asString();

        mockMvc.perform(post("/api/remote-hosts/" + hostId + "/test-connection")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.newlyPinned").value(false));

        mockMvc.perform(get("/api/remote-hosts/" + hostId).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinnedHostKeyFingerprint").doesNotExist());
    }

    private String registerAndGetToken(String email) throws Exception {
        String body = objectMapper.writeValueAsString(
                Map.of("name", "Host Tester", "email", email, "password", "correct-horse-battery"));
        String response = mockMvc
                .perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).get("token").asString();
    }

    private String createCredential(String authToken, String name) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", name, "privateKey", ED25519_PRIVATE_KEY));
        String response = mockMvc
                .perform(post("/api/ssh-credentials")
                        .header("Authorization", "Bearer " + authToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode created = objectMapper.readTree(response);
        return created.get("id").asString();
    }
}
