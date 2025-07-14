package com.oracle.migration.validator.service;

import com.oracle.migration.validator.model.ValidationResult;
import com.oracle.migration.validator.repository.MigrationAuditRepository.ValidationStatistics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for migration validation operations
 */
public interface MigrationValidationService {
    
    /**
     * Validate a single mobile number
     * @param mobileNo mobile number to validate
     * @return validation result
     */
    ValidationResult validateMobileNumber(String mobileNo);
    
    /**
     * Validate multiple mobile numbers
     * @param mobileNumbers list of mobile numbers to validate
     * @return list of validation results
     */
    List<ValidationResult> validateMobileNumbers(List<String> mobileNumbers);
    
    /**
     * Get mobile numbers for validation with pagination
     * @param pageable pagination information
     * @param skipValidated whether to skip already validated numbers
     * @return page of mobile numbers
     */
    Page<String> getMobileNumbersForValidation(Pageable pageable, boolean skipValidated);
    
    /**
     * Save validation results to audit table
     * @param results list of validation results to save
     */
    void saveValidationResults(List<ValidationResult> results);
    
    /**
     * Get validation progress
     * @return validation progress information
     */
    ValidationProgress getValidationProgress();
    
    /**
     * Get validation statistics
     * @return validation statistics
     */
    ValidationStatistics getValidationStatistics();
    
    /**
     * Check if validation is complete
     * @return true if all mobile numbers have been validated
     */
    boolean isValidationComplete();
    
    /**
     * Record class for validation progress information
     */
    record ValidationProgress(
        long totalMobileNumbers,
        long validatedMobileNumbers,
        long remainingMobileNumbers,
        double progressPercentage
    ) {}
}