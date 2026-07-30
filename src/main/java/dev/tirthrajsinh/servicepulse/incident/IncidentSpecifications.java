package dev.tirthrajsinh.servicepulse.incident;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.tirthrajsinh.servicepulse.incident.IncidentApplicationService.IncidentSearchCriteria;
import dev.tirthrajsinh.servicepulse.incident.domain.Incident;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

final class IncidentSpecifications {

    private IncidentSpecifications() {
    }

    static Specification<Incident> matching(IncidentSearchCriteria criteria) {
        return (root, query, builder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(builder.equal(root.get("workspaceId"), criteria.workspaceId()));
            if (criteria.serviceId() != null) {
                predicates.add(builder.equal(root.get("serviceId"), criteria.serviceId()));
            }
            if (criteria.status() != null) {
                predicates.add(builder.equal(root.get("status"), criteria.status()));
            }
            if (criteria.severity() != null) {
                predicates.add(builder.equal(root.get("severity"), criteria.severity()));
            }
            if (criteria.query() != null && !criteria.query().isBlank()) {
                String pattern = "%"
                    + escapeLike(criteria.query().strip().toLowerCase(Locale.ROOT))
                    + "%";
                predicates.add(builder.or(
                    builder.like(builder.lower(root.get("title")), pattern, '\\'),
                    builder.like(builder.lower(root.get("summary")), pattern, '\\')
                ));
            }
            return builder.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}
