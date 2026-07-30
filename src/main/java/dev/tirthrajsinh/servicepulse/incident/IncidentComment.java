package dev.tirthrajsinh.servicepulse.incident;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "incident_comments")
class IncidentComment {

    @Id
    private UUID id;

    @Column(name = "incident_id", nullable = false)
    private UUID incidentId;

    @Column(name = "author_id", nullable = false)
    private UUID authorId;

    @Column(nullable = false, length = 4000)
    private String body;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected IncidentComment() {
    }

    private IncidentComment(UUID incidentId, UUID authorId, String body, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.incidentId = Objects.requireNonNull(incidentId);
        this.authorId = Objects.requireNonNull(authorId);
        this.body = requireBody(body);
        this.createdAt = Objects.requireNonNull(createdAt);
        this.updatedAt = createdAt;
    }

    static IncidentComment add(UUID incidentId, UUID authorId, String body, Instant createdAt) {
        return new IncidentComment(incidentId, authorId, body, createdAt);
    }

    private static String requireBody(String body) {
        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("comment body must not be blank");
        }
        return body.strip();
    }

    UUID getId() {
        return id;
    }

    UUID getIncidentId() {
        return incidentId;
    }

    UUID getAuthorId() {
        return authorId;
    }

    String getBody() {
        return body;
    }

    Instant getCreatedAt() {
        return createdAt;
    }

    Instant getUpdatedAt() {
        return updatedAt;
    }
}
