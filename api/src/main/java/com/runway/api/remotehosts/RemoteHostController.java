package com.runway.api.remotehosts;

import com.runway.api.auth.AuthPrincipal;
import com.runway.api.remotehosts.dto.RemoteHostRequest;
import com.runway.api.remotehosts.dto.RemoteHostResponse;
import com.runway.api.remotehosts.dto.TestConnectionResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/remote-hosts")
public class RemoteHostController {

    private final RemoteHostService remoteHostService;

    public RemoteHostController(RemoteHostService remoteHostService) {
        this.remoteHostService = remoteHostService;
    }

    @GetMapping
    public List<RemoteHostResponse> list(@AuthenticationPrincipal AuthPrincipal principal) {
        return remoteHostService.list(principal.organizationId());
    }

    @GetMapping("/{id}")
    public RemoteHostResponse get(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return remoteHostService.get(id, principal.organizationId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RemoteHostResponse create(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody RemoteHostRequest request) {
        return remoteHostService.create(principal.organizationId(), request);
    }

    @PatchMapping("/{id}")
    public RemoteHostResponse update(
            @AuthenticationPrincipal AuthPrincipal principal,
            @PathVariable UUID id,
            @Valid @RequestBody RemoteHostRequest request) {
        return remoteHostService.update(id, principal.organizationId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        remoteHostService.delete(id, principal.organizationId());
    }

    @PostMapping("/{id}/test-connection")
    public TestConnectionResponse testConnection(
            @AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return remoteHostService.testConnection(id, principal.organizationId());
    }

    @PostMapping("/{id}/repin")
    public TestConnectionResponse repin(@AuthenticationPrincipal AuthPrincipal principal, @PathVariable UUID id) {
        return remoteHostService.repin(id, principal.organizationId());
    }
}
