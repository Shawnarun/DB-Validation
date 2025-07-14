package com.oracle.migration.validator.exception;

/**
 * Custom exception for validation operations
 */
public class ValidationException extends RuntimeException {
    
    private final String mobileNo;
    private final String validationScenario;
    
    public ValidationException(String message) {
        super(message);
        this.mobileNo = null;
        this.validationScenario = null;
    }
    
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        this.mobileNo = null;
        this.validationScenario = null;
    }
    
    public ValidationException(String mobileNo, String validationScenario, String message) {
        super(String.format("Validation failed for mobile %s in scenario %s: %s", 
                           mobileNo, validationScenario, message));
        this.mobileNo = mobileNo;
        this.validationScenario = validationScenario;
    }
    
    public ValidationException(String mobileNo, String validationScenario, String message, Throwable cause) {
        super(String.format("Validation failed for mobile %s in scenario %s: %s", 
                           mobileNo, validationScenario, message), cause);
        this.mobileNo = mobileNo;
        this.validationScenario = validationScenario;
    }
    
    public String getMobileNo() {
        return mobileNo;
    }
    
    public String getValidationScenario() {
        return validationScenario;
    }
}

/**
 * Exception for database-related validation errors
 */
class DatabaseValidationException extends ValidationException {
    
    public DatabaseValidationException(String message) {
        super(message);
    }
    
    public DatabaseValidationException(String message, Throwable cause) {
        super(message, cause);
    }
    
    public DatabaseValidationException(String mobileNo, String validationScenario, String message, Throwable cause) {
        super(mobileNo, validationScenario, message, cause);
    }
}

/**
 * Exception for batch processing errors
 */
class BatchProcessingException extends ValidationException {
    
    public BatchProcessingException(String message) {
        super(message);
    }
    
    public BatchProcessingException(String message, Throwable cause) {
        super(message, cause);
    }
}