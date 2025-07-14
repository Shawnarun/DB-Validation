package com.oracle.migration.validator.model;

/**
 * Enum representing different validation scenarios
 */
public enum ValidationScenario {
    SR1("Airtel number validation via CAM core tables"),
    SR2("Reserved for future validation scenario"),
    SR4("Check BLOCKED_STATUS = 'U' and STATUS = 'A' in PHONE_NO_REGISTER"),
    SR5("SIM + IMSI pair in DYN_1_CONNECTION must match IMSI_SIM_REGISTER"),
    SR6("Check if IMSI exists in PROV_SWITCH_IMAGE");
    
    private final String description;
    
    ValidationScenario(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
    }
}