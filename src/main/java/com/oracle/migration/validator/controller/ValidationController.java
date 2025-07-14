package com.oracle.migration.validator.controller;

import com.oracle.migration.validator.service.MigrationValidationService;
import com.oracle.migration.validator.service.ValidationJobLauncher;
import com.oracle.migration.validator.repository.MigrationAuditRepository.ValidationStatistics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.JobExecution;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for migration validation operations
 */
@RestController
@RequestMapping("/api/validation")
public class ValidationController {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidationController.class);
    
    private final ValidationJobLauncher jobLauncher;
    private final MigrationValidationService validationService;
    
    @Autowired
    public ValidationController(ValidationJobLauncher jobLauncher,
                              MigrationValidationService validationService) {
        this.jobLauncher = jobLauncher;
        this.validationService = validationService;
    }
    
    /**
     * Start the validation job
     */
    @PostMapping("/start")
    public ResponseEntity<String> startValidation(@RequestParam(defaultValue = "false") boolean restart) {
        try {
            logger.info("Starting validation job via REST API (restart={})", restart);
            
            if (jobLauncher.isValidationComplete()) {
                return ResponseEntity.ok("Validation is already complete!");
            }
            
            JobExecution execution = jobLauncher.launchValidationJob(restart);
            
            if (execution != null) {
                return ResponseEntity.ok("Validation job started with execution ID: " + execution.getId());
            } else {
                return ResponseEntity.ok("Validation is already complete!");
            }
            
        } catch (Exception e) {
            logger.error("Failed to start validation job", e);
            return ResponseEntity.internalServerError()
                .body("Failed to start validation job: " + e.getMessage());
        }
    }
    
    /**
     * Get validation progress
     */
    @GetMapping("/progress")
    public ResponseEntity<MigrationValidationService.ValidationProgress> getProgress() {
        try {
            MigrationValidationService.ValidationProgress progress = jobLauncher.getProgress();
            return ResponseEntity.ok(progress);
        } catch (Exception e) {
            logger.error("Failed to get validation progress", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get validation statistics
     */
    @GetMapping("/statistics")
    public ResponseEntity<ValidationStatistics> getStatistics() {
        try {
            ValidationStatistics statistics = validationService.getValidationStatistics();
            return ResponseEntity.ok(statistics);
        } catch (Exception e) {
            logger.error("Failed to get validation statistics", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Get validation report
     */
    @GetMapping("/report")
    public ResponseEntity<String> getReport() {
        try {
            String report = jobLauncher.getValidationStatisticsReport();
            return ResponseEntity.ok(report);
        } catch (Exception e) {
            logger.error("Failed to get validation report", e);
            return ResponseEntity.internalServerError()
                .body("Failed to generate validation report: " + e.getMessage());
        }
    }
    
    /**
     * Check if validation is complete
     */
    @GetMapping("/status")
    public ResponseEntity<ValidationStatus> getStatus() {
        try {
            boolean isComplete = jobLauncher.isValidationComplete();
            MigrationValidationService.ValidationProgress progress = jobLauncher.getProgress();
            
            ValidationStatus status = new ValidationStatus(
                isComplete,
                progress.progressPercentage(),
                progress.validatedMobileNumbers(),
                progress.totalMobileNumbers()
            );
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            logger.error("Failed to get validation status", e);
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Validate a specific mobile number
     */
    @PostMapping("/validate/{mobileNo}")
    public ResponseEntity<String> validateMobileNumber(@PathVariable String mobileNo) {
        try {
            logger.info("Validating single mobile number: {}", mobileNo);
            
            var result = validationService.validateMobileNumber(mobileNo);
            
            if (result.hasErrors()) {
                return ResponseEntity.badRequest()
                    .body("Validation failed: " + result.errorMessage());
            }
            
            // Save the result
            validationService.saveValidationResults(java.util.List.of(result));
            
            return ResponseEntity.ok("Mobile number validated successfully: " + 
                result.results().toString());
                
        } catch (Exception e) {
            logger.error("Failed to validate mobile number: {}", mobileNo, e);
            return ResponseEntity.internalServerError()
                .body("Failed to validate mobile number: " + e.getMessage());
        }
    }
    
    /**
     * Record class for validation status response
     */
    public record ValidationStatus(
        boolean isComplete,
        double progressPercentage,
        long validatedCount,
        long totalCount
    ) {}
}