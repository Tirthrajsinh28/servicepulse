package dev.tirthrajsinh.servicepulse.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import dev.tirthrajsinh.servicepulse.audit.AuditEntry;
import dev.tirthrajsinh.servicepulse.audit.AuditEntryRepository;
import dev.tirthrajsinh.servicepulse.common.api.ResourceConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class UserRegistrationService {

    private final UserAccountRepository users;
    private final AuditEntryRepository auditEntries;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    UserRegistrationService(
        UserAccountRepository users,
        AuditEntryRepository auditEntries,
        PasswordEncoder passwordEncoder,
        Clock clock
    ) {
        this.users = users;
        this.auditEntries = auditEntries;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    RegisteredUser register(String email, String displayName, String password) {
        String normalizedEmail = normalizeEmail(email);
        if (users.findByEmailIgnoreCase(normalizedEmail).isPresent()) {
            throw new ResourceConflictException("A user with this email already exists.");
        }

        Instant now = clock.instant();
        UserAccount user = UserAccount.registered(
            UUID.randomUUID(),
            normalizedEmail,
            displayName.strip(),
            passwordEncoder.encode(password),
            now
        );
        try {
            users.save(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResourceConflictException("A user with this email already exists.");
        }
        auditEntries.save(AuditEntry.user(
            "USER_REGISTERED",
            user.getId(),
            "User self-registered without automatic workspace membership.",
            now
        ));
        return new RegisteredUser(user.getId(), user.getEmail(), user.getDisplayName());
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.strip().toLowerCase(Locale.ROOT);
    }

    record RegisteredUser(UUID id, String email, String displayName) {
    }
}
