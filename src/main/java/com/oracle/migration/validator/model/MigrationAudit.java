package com.oracle.migration.validator.model;

import java.time.LocalDateTime;

/**
 * Model representing the TEMP_MIGRATION_AUDIT table
 */
public record MigrationAudit(
    String mobileNo,
    String v1,
    String v2,
    String v3,
    String v4,
    String v5,
    LocalDateTime createdDate
) {
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private String mobileNo;
        private String v1;
        private String v2;
        private String v3;
        private String v4;
        private String v5;
        private LocalDateTime createdDate;
        
        public Builder mobileNo(String mobileNo) {
            this.mobileNo = mobileNo;
            return this;
        }
        
        public Builder v1(String v1) {
            this.v1 = v1;
            return this;
        }
        
        public Builder v2(String v2) {
            this.v2 = v2;
            return this;
        }
        
        public Builder v3(String v3) {
            this.v3 = v3;
            return this;
        }
        
        public Builder v4(String v4) {
            this.v4 = v4;
            return this;
        }
        
        public Builder v5(String v5) {
            this.v5 = v5;
            return this;
        }
        
        public Builder createdDate(LocalDateTime createdDate) {
            this.createdDate = createdDate;
            return this;
        }
        
        public MigrationAudit build() {
            return new MigrationAudit(mobileNo, v1, v2, v3, v4, v5, 
                                    createdDate != null ? createdDate : LocalDateTime.now());
        }
    }
}