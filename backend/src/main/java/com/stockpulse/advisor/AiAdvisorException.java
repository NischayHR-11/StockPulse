package com.stockpulse.advisor;

public class AiAdvisorException extends RuntimeException {
    public AiAdvisorException(String message) {
        super(message);
    }

    public AiAdvisorException(String message, Throwable cause) {
        super(message, cause);
    }
}
