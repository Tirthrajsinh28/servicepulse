package dev.tirthrajsinh.servicepulse.identity;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Email or password is invalid");
    }
}
