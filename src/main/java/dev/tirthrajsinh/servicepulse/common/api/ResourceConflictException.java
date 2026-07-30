package dev.tirthrajsinh.servicepulse.common.api;

public class ResourceConflictException extends RuntimeException {

    public ResourceConflictException(String detail) {
        super(detail);
    }
}
