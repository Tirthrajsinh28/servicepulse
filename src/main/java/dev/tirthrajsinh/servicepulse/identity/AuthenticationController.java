package dev.tirthrajsinh.servicepulse.identity;

import java.util.UUID;

import dev.tirthrajsinh.servicepulse.identity.AuthenticationService.TokenPair;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
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

    AuthenticationController(AuthenticationService authenticationService) {
        this.authenticationService = authenticationService;
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
        @NotBlank @Email @jakarta.validation.constraints.Size(max = 320) String email,
        @NotBlank @jakarta.validation.constraints.Size(max = 200) String password
    ) {
    }

    record RefreshRequest(@NotBlank @jakarta.validation.constraints.Size(max = 200) String refreshToken) {
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

    record CurrentUser(UUID id) {
    }
}
