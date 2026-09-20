package com.runway.api.credentials;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class SshCredentialControllerTest {

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

    @BeforeEach
    void registerAndAuthenticate() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "name",
                "Credential Tester",
                "email",
                "ssh-creds-" + System.nanoTime() + "@runway.dev",
                "password",
                "correct-horse-battery"));

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
        mockMvc.perform(get("/api/ssh-credentials")).andExpect(status().isUnauthorized());
    }

    @Test
    void createsListsAndDeletesACredentialWithoutEverExposingKeyMaterial() throws Exception {
        String createBody = objectMapper.writeValueAsString(
                Map.of("name", "Deploy Key", "privateKey", ED25519_PRIVATE_KEY));

        String createResponse = mockMvc
                .perform(post("/api/ssh-credentials")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Deploy Key"))
                .andExpect(jsonPath("$.keyFingerprint", org.hamcrest.Matchers.startsWith("SHA256:")))
                .andExpect(jsonPath("$.publicKeyPreview", org.hamcrest.Matchers.startsWith("ssh-ed25519 ")))
                .andExpect(jsonPath("$.privateKey").doesNotExist())
                .andExpect(jsonPath("$.encryptedPrivateKey").doesNotExist())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode created = objectMapper.readTree(createResponse);
        String credentialId = created.get("id").asString();

        mockMvc.perform(get("/api/ssh-credentials").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(credentialId))
                .andExpect(jsonPath("$[0].privateKey").doesNotExist());

        mockMvc.perform(
                        delete("/api/ssh-credentials/" + credentialId).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/ssh-credentials").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void rejectsUnparseableKeyMaterial() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("name", "Bad Key", "privateKey", "not a real key"));

        mockMvc.perform(post("/api/ssh-credentials")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refusesToDeleteACredentialStillUsedByARemoteHost() throws Exception {
        String createCredentialBody = objectMapper.writeValueAsString(
                Map.of("name", "In Use Key", "privateKey", ED25519_PRIVATE_KEY));
        String credentialResponse = mockMvc
                .perform(post("/api/ssh-credentials")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createCredentialBody))
                .andExpect(status().isCreated())
                .andReturn()
                .getResponse()
                .getContentAsString();
        String credentialId = objectMapper.readTree(credentialResponse).get("id").asString();

        String createHostBody = objectMapper.writeValueAsString(Map.of(
                "name", "Web Server",
                "hostname", "example.internal",
                "port", 22,
                "username", "deploy",
                "sshCredentialId", credentialId));
        mockMvc.perform(post("/api/remote-hosts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createHostBody))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        delete("/api/ssh-credentials/" + credentialId).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }
}
