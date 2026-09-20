package com.runway.api.credentials;

import com.runway.api.auth.AuthPrincipal;
import com.runway.api.credentials.dto.SshCredentialRequest;
import com.runway.api.credentials.dto.SshCredentialResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ssh-credentials")
public class SshCredentialController {

    private final SshCredentialService sshCredentialService;

    public SshCredentialController(SshCredentialService sshCredentialService) {
        this.sshCredentialService = sshCredentialService;
    }

    @GetMapping
    public List<SshCredentialResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return sshCredentialService.list(principal.organizationId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SshCredentialResponse create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody SshCredentialRequest request) {
        return sshCredentialService.create(principal.organizationId(), principal.userId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        sshCredentialService.delete(id, principal.organizationId());
    }
}
