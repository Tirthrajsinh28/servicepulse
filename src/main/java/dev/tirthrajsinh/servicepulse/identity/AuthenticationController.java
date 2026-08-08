package dev.tirthrajsinh.servicepulse.identity;

import java.util.UUID;

import dev.tirthrajsinh.servicepulse.identity.AuthenticationService.TokenPair;
import dev.tirthrajsinh.servicepulse.identity.UserRegistrationService.RegisteredUser;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final UserRegistrationService registrationService;

    AuthenticationController(
        AuthenticationService authenticationService,
        UserRegistrationService registrationService
    ) {
        this.authenticationService = authenticationService;
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    @SecurityRequirements
    ResponseEntity<RegistrationResponse> register(@Valid @RequestBody RegistrationRequest request) {
        RegisteredUser user = registrationService.register(
            request.email(),
            request.displayName(),
            request.password()
        );
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(RegistrationResponse.from(user));
    }

    @PostMapping("/login")
    @SecurityRequirements
    TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return TokenResponse.from(authenticationService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    @SecurityRequirements
    TokenResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return TokenResponse.from(authenticationService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    CurrentUser me(Authentication authentication) {
        return new CurrentUser(UUID.fromString(authentication.getName()));
    }

    record LoginRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(max = 200) String password
    ) {
    }

    record RegistrationRequest(
        @NotBlank @Email @Size(max = 320) String email,
        @NotBlank @Size(min = 2, max = 120) String displayName,
        @NotBlank @Size(min = 12, max = 200) String password
    ) {
    }

    record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {
    }

    record TokenResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn
    ) {
        static TokenResponse from(TokenPair pair) {
            return new TokenResponse(
                pair.accessToken(),
                pair.refreshToken(),
                "Bearer",
                pair.expiresInSeconds()
            );
        }
    }

    record RegistrationResponse(UUID id, String email, String displayName) {
        static RegistrationResponse from(RegisteredUser user) {
            return new RegistrationResponse(user.id(), user.email(), user.displayName());
        }
    }

    record CurrentUser(UUID id) {
    }
}
