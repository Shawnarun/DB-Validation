package com.oracle.migration.validator.model;

import java.util.Map;

/**
 * Model representing validation results for a mobile number
 */
public record ValidationResult(
    String mobileNo,
    Map<ValidationScenario, String> results,
    boolean hasErrors,
    String errorMessage
) {
    
    public static ValidationResult success(String mobileNo, Map<ValidationScenario, String> results) {
        return new ValidationResult(mobileNo, results, false, null);
    }
    
    public static ValidationResult error(String mobileNo, String errorMessage) {
        return new ValidationResult(mobileNo, Map.of(), true, errorMessage);
    }
    
    public String getResult(ValidationScenario scenario) {
        return results.getOrDefault(scenario, "N");
    }
    
    public MigrationAudit toAuditRecord() {
        return MigrationAudit.builder()
            .mobileNo(mobileNo)
            .v1(getResult(ValidationScenario.SR1))
            .v2(getResult(ValidationScenario.SR2))
            .v3(getResult(ValidationScenario.SR4))
            .v4(getResult(ValidationScenario.SR5))
            .v5(getResult(ValidationScenario.SR6))
            .build();
    }
}