package com.oracle.migration.validator.batch;

import com.oracle.migration.validator.model.ValidationResult;
import com.oracle.migration.validator.service.MigrationValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;

/**
 * Spring Batch ItemProcessor for validating mobile numbers
 */
public class ValidationItemProcessor implements ItemProcessor<String, ValidationResult> {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidationItemProcessor.class);
    
    private final MigrationValidationService validationService;
    
    public ValidationItemProcessor(MigrationValidationService validationService) {
        this.validationService = validationService;
    }
    
    @Override
    public ValidationResult process(String mobileNo) throws Exception {
        logger.debug("Processing mobile number: {}", mobileNo);
        
        try {
            ValidationResult result = validationService.validateMobileNumber(mobileNo);
            
            if (result.hasErrors()) {
                logger.warn("Validation failed for mobile number {}: {}", 
                           mobileNo, result.errorMessage());
            } else {
                logger.debug("Validation completed for mobile number: {}", mobileNo);
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error processing mobile number {}: {}", mobileNo, e.getMessage(), e);
            // Return error result instead of throwing exception to continue processing
            return ValidationResult.error(mobileNo, "Processing error: " + e.getMessage());
        }
    }
}