package com.runway.api.auth;

import com.runway.api.auth.dto.AuthResponse;
import com.runway.api.auth.dto.LoginRequest;
import com.runway.api.auth.dto.RegisterRequest;
import com.runway.api.common.BadRequestException;
import com.runway.api.common.UnauthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final OrganizationRepository organizationRepository;
    private final MembershipRepository membershipRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            OrganizationRepository organizationRepository,
            MembershipRepository membershipRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.organizationRepository = organizationRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("An account with this email already exists");
        }

        User user = userRepository.save(
                new User(request.email(), passwordEncoder.encode(request.password()), request.name()));
        Organization organization =
                organizationRepository.save(new Organization(request.name() + "'s Organization"));
        Membership membership =
                membershipRepository.save(new Membership(user, organization, MembershipRole.OWNER));

        return toAuthResponse(user, organization, membership);
    }

    public AuthResponse login(LoginRequest request) {
        User user = userRepository
                .findByEmail(request.email())
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        Membership membership = membershipRepository
                .findFirstByUserId(user.getId())
                .orElseThrow(() -> new IllegalStateException("User has no organization membership"));

        return toAuthResponse(user, membership.getOrganization(), membership);
    }

    private AuthResponse toAuthResponse(User user, Organization organization, Membership membership) {
        AuthPrincipal principal = new AuthPrincipal(user.getId(), organization.getId(), user.getEmail());
        String token = jwtService.issue(principal);

        return new AuthResponse(
                token,
                user.getId(),
                user.getEmail(),
                user.getName(),
                organization.getId(),
                organization.getName(),
                membership.getRole().name());
    }
}
