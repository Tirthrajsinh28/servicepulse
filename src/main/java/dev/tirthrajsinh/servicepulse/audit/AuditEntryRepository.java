package dev.tirthrajsinh.servicepulse.audit;

import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface AuditEntryRepository extends Repository<AuditEntry, UUID> {

    AuditEntry save(AuditEntry entry);
}
