package com.stockpulse.advisor;

public class AiAdvisorException extends RuntimeException {
    public enum Category {
        CONFIGURATION,
        AUTHENTICATION,
        INVALID_ENDPOINT_OR_MODEL,
        RATE_LIMIT_OR_QUOTA,
        PROVIDER_ERROR,
        TIMEOUT,
        NETWORK,
        MALFORMED_RESPONSE,
        UNKNOWN
    }

    private final Category category;
    private final Integer httpStatus;
    private final String detail;

    public AiAdvisorException(String message) {
        this(message, Category.UNKNOWN, null, null, null);
    }

    public AiAdvisorException(String message, Throwable cause) {
        this(message, Category.UNKNOWN, null, null, cause);
    }

    public AiAdvisorException(String message, Category category, Integer httpStatus, String detail) {
        this(message, category, httpStatus, detail, null);
    }

    public AiAdvisorException(String message, Category category, Integer httpStatus,
                              String detail, Throwable cause) {
        super(message, cause);
        this.category = category;
        this.httpStatus = httpStatus;
        this.detail = detail;
    }

    public Category getCategory() {
        return category;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getDetail() {
        return detail;
    }
}
