package com.oracle.migration.validator.repository;

import com.oracle.migration.validator.model.ValidationResult;
import com.oracle.migration.validator.model.ValidationScenario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Set;

/**
 * Repository interface for migration data operations
 */
public interface MigrationDataRepository {
    
    /**
     * Fetch mobile numbers from ATDM schema with pagination
     * @param pageable pagination information
     * @return page of mobile numbers
     */
    Page<String> fetchMobileNumbers(Pageable pageable);
    
    /**
     * Fetch mobile numbers that haven't been validated yet
     * @param pageable pagination information
     * @return page of unvalidated mobile numbers
     */
    Page<String> fetchUnvalidatedMobileNumbers(Pageable pageable);
    
    /**
     * Get already validated mobile numbers
     * @return set of validated mobile numbers
     */
    Set<String> getValidatedMobileNumbers();
    
    /**
     * Get the count of total mobile numbers
     * @return total count
     */
    long getTotalMobileNumbersCount();
    
    /**
     * Get the count of validated mobile numbers
     * @return validated count
     */
    long getValidatedMobileNumbersCount();
    
    /**
     * Validate SR1: Airtel number validation via CAM core tables
     * @param mobileNo mobile number to validate
     * @return "Y" if validation passes, "N" otherwise
     */
    String validateSR1(String mobileNo);
    
    /**
     * Validate SR4: Check BLOCKED_STATUS = 'U' and STATUS = 'A' in PHONE_NO_REGISTER
     * @param mobileNo mobile number to validate
     * @return "Y" if validation passes, "N" otherwise
     */
    String validateSR4(String mobileNo);
    
    /**
     * Validate SR5: SIM + IMSI pair in DYN_1_CONNECTION must match IMSI_SIM_REGISTER
     * @param mobileNo mobile number to validate
     * @return "Y" if validation passes, "N" otherwise
     */
    String validateSR5(String mobileNo);
    
    /**
     * Validate SR6: Check if IMSI exists in PROV_SWITCH_IMAGE
     * @param mobileNo mobile number to validate
     * @return "Y" if validation passes, "N" otherwise
     */
    String validateSR6(String mobileNo);
    
    /**
     * Perform all validations for a given mobile number
     * @param mobileNo mobile number to validate
     * @return validation result
     */
    ValidationResult validateMobileNumber(String mobileNo);
    
    /**
     * Batch validate multiple mobile numbers
     * @param mobileNumbers list of mobile numbers to validate
     * @return list of validation results
     */
    List<ValidationResult> validateMobileNumbers(List<String> mobileNumbers);
}