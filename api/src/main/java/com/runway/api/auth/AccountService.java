package com.runway.api.auth;

import com.runway.api.auth.dto.AccountResponse;
import com.runway.api.auth.dto.UpdateEmailRequest;
import com.runway.api.auth.dto.UpdatePasswordRequest;
import com.runway.api.auth.dto.UpdatePasswordResponse;
import com.runway.api.common.BadRequestException;
import com.runway.api.common.UnauthorizedException;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AccountService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public AccountResponse get(UUID userId) {
        return AccountResponse.from(findUser(userId));
    }

    @Transactional
    public AccountResponse updateEmail(UUID userId, UpdateEmailRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        if (!request.email().equals(user.getEmail()) && userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("An account with this email already exists");
        }
        user.updateEmail(request.email());
        return AccountResponse.from(user);
    }

    @Transactional
    public UpdatePasswordResponse updatePassword(UUID userId, UUID organizationId, UpdatePasswordRequest request) {
        User user = findUser(userId);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
        user.updatePasswordHash(passwordEncoder.encode(request.newPassword()));

        AuthPrincipal principal = new AuthPrincipal(user.getId(), organizationId, user.getEmail());
        return new UpdatePasswordResponse(jwtService.issue(principal));
    }

    private User findUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new UnauthorizedException("User not found"));
    }
}
