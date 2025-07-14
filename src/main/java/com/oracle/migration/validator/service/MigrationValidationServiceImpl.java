package com.oracle.migration.validator.service;

import com.oracle.migration.validator.model.MigrationAudit;
import com.oracle.migration.validator.model.ValidationResult;
import com.oracle.migration.validator.repository.MigrationAuditRepository;
import com.oracle.migration.validator.repository.MigrationAuditRepository.ValidationStatistics;
import com.oracle.migration.validator.repository.MigrationDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Implementation of MigrationValidationService
 */
@Service
public class MigrationValidationServiceImpl implements MigrationValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(MigrationValidationServiceImpl.class);
    
    private final MigrationDataRepository dataRepository;
    private final MigrationAuditRepository auditRepository;
    
    @Autowired
    public MigrationValidationServiceImpl(MigrationDataRepository dataRepository,
                                        MigrationAuditRepository auditRepository) {
        this.dataRepository = dataRepository;
        this.auditRepository = auditRepository;
    }
    
    @Override
    public ValidationResult validateMobileNumber(String mobileNo) {
        logger.debug("Validating mobile number: {}", mobileNo);
        
        // Check if already validated to avoid re-processing
        if (auditRepository.existsByMobileNo(mobileNo)) {
            logger.debug("Mobile number {} already validated, skipping", mobileNo);
            return ValidationResult.error(mobileNo, "Already validated");
        }
        
        return dataRepository.validateMobileNumber(mobileNo);
    }
    
    @Override
    public List<ValidationResult> validateMobileNumbers(List<String> mobileNumbers) {
        logger.info("Validating {} mobile numbers", mobileNumbers.size());
        
        // Filter out already validated numbers
        List<String> unvalidatedNumbers = mobileNumbers.stream()
            .filter(mobileNo -> !auditRepository.existsByMobileNo(mobileNo))
            .toList();
        
        if (unvalidatedNumbers.size() != mobileNumbers.size()) {
            logger.info("Filtered out {} already validated numbers. Processing {} numbers",
                       mobileNumbers.size() - unvalidatedNumbers.size(), unvalidatedNumbers.size());
        }
        
        return dataRepository.validateMobileNumbers(unvalidatedNumbers);
    }
    
    @Override
    public Page<String> getMobileNumbersForValidation(Pageable pageable, boolean skipValidated) {
        if (skipValidated) {
            return dataRepository.fetchUnvalidatedMobileNumbers(pageable);
        } else {
            return dataRepository.fetchMobileNumbers(pageable);
        }
    }
    
    @Override
    @Transactional
    public void saveValidationResults(List<ValidationResult> results) {
        logger.info("Saving {} validation results", results.size());
        
        List<MigrationAudit> audits = results.stream()
            .filter(result -> !result.hasErrors()) // Only save successful validations
            .map(ValidationResult::toAuditRecord)
            .toList();
        
        if (!audits.isEmpty()) {
            auditRepository.saveAll(audits);
            logger.info("Saved {} audit records", audits.size());
        }
        
        // Log errors separately
        List<ValidationResult> errors = results.stream()
            .filter(ValidationResult::hasErrors)
            .toList();
        
        if (!errors.isEmpty()) {
            logger.warn("Found {} validation errors:", errors.size());
            errors.forEach(error -> 
                logger.warn("Error for mobile {}: {}", error.mobileNo(), error.errorMessage()));
        }
    }
    
    @Override
    public ValidationProgress getValidationProgress() {
        long totalMobileNumbers = dataRepository.getTotalMobileNumbersCount();
        long validatedMobileNumbers = dataRepository.getValidatedMobileNumbersCount();
        long remainingMobileNumbers = totalMobileNumbers - validatedMobileNumbers;
        
        double progressPercentage = totalMobileNumbers > 0 ? 
            (double) validatedMobileNumbers / totalMobileNumbers * 100.0 : 0.0;
        
        return new ValidationProgress(
            totalMobileNumbers,
            validatedMobileNumbers,
            remainingMobileNumbers,
            progressPercentage
        );
    }
    
    @Override
    public ValidationStatistics getValidationStatistics() {
        return auditRepository.getValidationStatistics();
    }
    
    @Override
    public boolean isValidationComplete() {
        ValidationProgress progress = getValidationProgress();
        return progress.remainingMobileNumbers() == 0;
    }
}