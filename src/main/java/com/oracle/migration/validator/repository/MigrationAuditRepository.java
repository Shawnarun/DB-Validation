package com.oracle.migration.validator.repository;

import com.oracle.migration.validator.model.MigrationAudit;

import java.util.List;
import java.util.Set;

/**
 * Repository interface for migration audit operations
 */
public interface MigrationAuditRepository {
    
    /**
     * Save a single audit record
     * @param audit the audit record to save
     */
    void save(MigrationAudit audit);
    
    /**
     * Save multiple audit records in batch
     * @param audits list of audit records to save
     */
    void saveAll(List<MigrationAudit> audits);
    
    /**
     * Check if a mobile number has already been validated
     * @param mobileNo mobile number to check
     * @return true if already validated, false otherwise
     */
    boolean existsByMobileNo(String mobileNo);
    
    /**
     * Get all validated mobile numbers
     * @return set of validated mobile numbers
     */
    Set<String> findAllValidatedMobileNumbers();
    
    /**
     * Get count of validated records
     * @return count of validated records
     */
    long getValidatedCount();
    
    /**
     * Create the audit table if it doesn't exist
     */
    void createAuditTableIfNotExists();
    
    /**
     * Clear all audit records (useful for testing)
     */
    void clearAll();
    
    /**
     * Get validation statistics
     * @return statistics about validation results
     */
    ValidationStatistics getValidationStatistics();
    
    /**
     * Record class for validation statistics
     */
    record ValidationStatistics(
        long totalValidated,
        long sr1Passed,
        long sr2Passed,
        long sr4Passed,
        long sr5Passed,
        long sr6Passed
    ) {}
}