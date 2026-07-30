package dev.tirthrajsinh.servicepulse.common.api;

import java.util.UUID;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String resource, UUID id) {
        super("%s with id %s was not found".formatted(resource, id));
    }
}
