package com.runway.api.auth;

import com.runway.api.auth.dto.AccountResponse;
import com.runway.api.auth.dto.UpdateEmailRequest;
import com.runway.api.auth.dto.UpdatePasswordRequest;
import com.runway.api.auth.dto.UpdatePasswordResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public AccountResponse get(@AuthenticationPrincipal AuthPrincipal principal) {
        return accountService.get(principal.userId());
    }

    @PatchMapping("/email")
    public AccountResponse updateEmail(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody UpdateEmailRequest request) {
        return accountService.updateEmail(principal.userId(), request);
    }

    @PatchMapping("/password")
    public UpdatePasswordResponse updatePassword(
            @AuthenticationPrincipal AuthPrincipal principal, @Valid @RequestBody UpdatePasswordRequest request) {
        return accountService.updatePassword(principal.userId(), principal.organizationId(), request);
    }
}
