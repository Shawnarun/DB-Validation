package com.oracle.migration.validator.service;

import com.oracle.migration.validator.model.MigrationAudit;
import com.oracle.migration.validator.model.ValidationResult;
import com.oracle.migration.validator.repository.MigrationAuditRepository;
import com.oracle.migration.validator.repository.MigrationAuditRepository.ValidationStatistics;
import com.oracle.migration.validator.repository.MigrationDataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Implementation of MigrationValidationService with race condition prevention
 */
@Service
public class MigrationValidationServiceImpl implements MigrationValidationService {
    
    private static final Logger logger = LoggerFactory.getLogger(MigrationValidationServiceImpl.class);
    
    private final MigrationDataRepository dataRepository;
    private final MigrationAuditRepository auditRepository;
    
    // In-memory cache to prevent duplicate processing within the same JVM instance
    private final Set<String> processingNumbers = ConcurrentHashMap.newKeySet();
    private final Set<String> processedNumbers = ConcurrentHashMap.newKeySet();
    
    @Autowired
    public MigrationValidationServiceImpl(MigrationDataRepository dataRepository,
                                        MigrationAuditRepository auditRepository) {
        this.dataRepository = dataRepository;
        this.auditRepository = auditRepository;
        
        // Initialize processed numbers cache from database
        initializeProcessedNumbersCache();
    }
    
    /**
     * Initialize cache with already processed numbers from database
     */
    private void initializeProcessedNumbersCache() {
        try {
            Set<String> existingNumbers = auditRepository.findAllValidatedMobileNumbers();
            processedNumbers.addAll(existingNumbers);
            logger.info("Initialized cache with {} already processed mobile numbers", existingNumbers.size());
        } catch (Exception e) {
            logger.warn("Failed to initialize processed numbers cache: {}", e.getMessage());
        }
    }
    
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ValidationResult validateMobileNumber(String mobileNo) {
        logger.debug("Validating mobile number: {}", mobileNo);
        
        // Check in-memory cache first (fastest)
        if (processedNumbers.contains(mobileNo)) {
            logger.debug("Mobile number {} already in processed cache, skipping", mobileNo);
            return ValidationResult.error(mobileNo, "Already validated");
        }
        
        // Check if currently being processed by another thread
        if (!processingNumbers.add(mobileNo)) {
            logger.debug("Mobile number {} is currently being processed by another thread, skipping", mobileNo);
            return ValidationResult.error(mobileNo, "Currently being processed");
        }
        
        try {
            // Double-check in database (in case of multiple JVM instances)
            if (auditRepository.existsByMobileNo(mobileNo)) {
                logger.debug("Mobile number {} already validated in database, skipping", mobileNo);
                processedNumbers.add(mobileNo); // Update cache
                return ValidationResult.error(mobileNo, "Already validated");
            }
            
            // Perform validation
            ValidationResult result = dataRepository.validateMobileNumber(mobileNo);
            
            if (!result.hasErrors()) {
                // Save immediately in the same transaction to prevent duplicates
                saveValidationResultAtomic(result);
                processedNumbers.add(mobileNo); // Update cache
            }
            
            return result;
            
        } catch (Exception e) {
            logger.error("Error validating mobile number {}: {}", mobileNo, e.getMessage(), e);
            return ValidationResult.error(mobileNo, "Validation error: " + e.getMessage());
        } finally {
            // Always remove from processing set
            processingNumbers.remove(mobileNo);
        }
    }
    
    @Override
    public List<ValidationResult> validateMobileNumbers(List<String> mobileNumbers) {
        logger.info("Validating {} mobile numbers", mobileNumbers.size());
        
        // Filter out already processed numbers using cache and database
        List<String> unprocessedNumbers = mobileNumbers.stream()
            .filter(mobileNo -> !processedNumbers.contains(mobileNo))
            .filter(mobileNo -> !auditRepository.existsByMobileNo(mobileNo))
            .distinct() // Remove duplicates within the same batch
            .toList();
        
        if (unprocessedNumbers.size() != mobileNumbers.size()) {
            logger.info("Filtered out {} already processed numbers. Processing {} numbers",
                       mobileNumbers.size() - unprocessedNumbers.size(), unprocessedNumbers.size());
        }
        
        if (unprocessedNumbers.isEmpty()) {
            logger.info("All mobile numbers already processed, skipping validation");
            return List.of();
        }
        
        // Process each number individually to maintain atomicity
        return unprocessedNumbers.stream()
            .map(this::validateMobileNumber)
            .filter(result -> !result.hasErrors()) // Only return successful validations
            .toList();
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
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void saveValidationResults(List<ValidationResult> results) {
        logger.info("Saving {} validation results", results.size());
        
        if (results.isEmpty()) {
            return;
        }
        
        // Filter out results for already processed numbers
        List<ValidationResult> newResults = results.stream()
            .filter(result -> !result.hasErrors())
            .filter(result -> !processedNumbers.contains(result.mobileNo()))
            .filter(result -> !auditRepository.existsByMobileNo(result.mobileNo()))
            .toList();
        
        if (newResults.isEmpty()) {
            logger.info("All validation results already exist, skipping save");
            return;
        }
        
        List<MigrationAudit> audits = newResults.stream()
            .map(ValidationResult::toAuditRecord)
            .toList();
        
        try {
            auditRepository.saveAll(audits);
            logger.info("Saved {} new audit records", audits.size());
            
            // Update cache with newly processed numbers
            newResults.forEach(result -> processedNumbers.add(result.mobileNo()));
            
        } catch (DataIntegrityViolationException e) {
            logger.warn("Some records may have been inserted by another process, handling individually");
            saveRecordsIndividually(audits);
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
    
    /**
     * Save validation result atomically in the same transaction
     */
    private void saveValidationResultAtomic(ValidationResult result) {
        try {
            MigrationAudit audit = result.toAuditRecord();
            auditRepository.save(audit);
            logger.debug("Saved audit record for mobile: {}", result.mobileNo());
        } catch (DataIntegrityViolationException e) {
            logger.debug("Audit record for mobile {} already exists, skipping", result.mobileNo());
        } catch (Exception e) {
            logger.error("Failed to save audit record for mobile {}: {}", result.mobileNo(), e.getMessage());
            throw e;
        }
    }
    
    /**
     * Save records individually to handle any remaining duplicates gracefully
     */
    private void saveRecordsIndividually(List<MigrationAudit> audits) {
        int saved = 0;
        int skipped = 0;
        
        for (MigrationAudit audit : audits) {
            try {
                if (!auditRepository.existsByMobileNo(audit.mobileNo())) {
                    auditRepository.save(audit);
                    processedNumbers.add(audit.mobileNo());
                    saved++;
                } else {
                    skipped++;
                }
            } catch (DataIntegrityViolationException e) {
                logger.debug("Duplicate record for mobile {}, skipping", audit.mobileNo());
                processedNumbers.add(audit.mobileNo());
                skipped++;
            } catch (Exception e) {
                logger.error("Failed to save individual record for mobile {}: {}", 
                           audit.mobileNo(), e.getMessage());
            }
        }
        
        logger.info("Individual save completed: {} saved, {} skipped", saved, skipped);
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
    
    /**
     * Clear the in-memory cache (useful for testing or cache refresh)
     */
    public void clearCache() {
        processingNumbers.clear();
        processedNumbers.clear();
        logger.info("Cleared validation cache");
    }
    
    /**
     * Get cache statistics for monitoring
     */
    public CacheStatistics getCacheStatistics() {
        return new CacheStatistics(
            processedNumbers.size(),
            processingNumbers.size()
        );
    }
    
    /**
     * Record for cache statistics
     */
    public record CacheStatistics(
        int processedCount,
        int currentlyProcessingCount
    ) {}
}