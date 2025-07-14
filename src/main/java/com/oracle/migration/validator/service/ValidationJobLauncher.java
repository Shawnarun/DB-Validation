package com.oracle.migration.validator.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Service for launching and managing validation jobs with retry support
 */
@Service
public class ValidationJobLauncher {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidationJobLauncher.class);
    
    private final JobLauncher jobLauncher;
    private final Job migrationValidationJob;
    private final MigrationValidationService validationService;
    
    @Autowired
    public ValidationJobLauncher(JobLauncher jobLauncher,
                               @Qualifier("migrationValidationJob") Job migrationValidationJob,
                               MigrationValidationService validationService) {
        this.jobLauncher = jobLauncher;
        this.migrationValidationJob = migrationValidationJob;
        this.validationService = validationService;
    }
    
    /**
     * Launch the migration validation job
     * @return JobExecution result
     */
    public JobExecution launchValidationJob() {
        return launchValidationJob(false);
    }
    
    /**
     * Launch the migration validation job with options and retry support
     * @param restart whether to restart from where it left off
     * @return JobExecution result
     */
    @Retryable(
        retryFor = {DataAccessException.class, Exception.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public JobExecution launchValidationJob(boolean restart) {
        try {
            logger.info("Launching migration validation job (restart={})", restart);
            
            // Check current progress
            var progress = validationService.getValidationProgress();
            logger.info("Current progress: {}/{} mobile numbers validated ({}%)", 
                       progress.validatedMobileNumbers(), 
                       progress.totalMobileNumbers(),
                       String.format("%.2f", progress.progressPercentage()));
            
            if (validationService.isValidationComplete()) {
                logger.info("Validation is already complete!");
                return null;
            }
            
            // Create unique job parameters to avoid conflicts
            JobParameters jobParameters = createUniqueJobParameters(restart);
            
            JobExecution execution = jobLauncher.run(migrationValidationJob, jobParameters);
            
            logger.info("Migration validation job launched successfully with execution ID: {}", 
                       execution.getId());
            
            return execution;
            
        } catch (Exception e) {
            logger.error("Failed to launch migration validation job: {}", e.getMessage(), e);
            
            // Check if it's a serialization error
            if (isSerializationError(e)) {
                logger.warn("Oracle serialization error detected, will retry...");
                throw new DataAccessException("Oracle serialization error", e) {};
            }
            
            throw new RuntimeException("Failed to launch validation job", e);
        }
    }
    
    /**
     * Create unique job parameters to avoid Oracle serialization conflicts
     */
    private JobParameters createUniqueJobParameters(boolean restart) {
        return new JobParametersBuilder()
            .addString("startTime", LocalDateTime.now().toString())
            .addString("restart", String.valueOf(restart))
            .addString("uniqueId", UUID.randomUUID().toString())
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters();
    }
    
    /**
     * Check if the exception is related to Oracle serialization
     */
    private boolean isSerializationError(Exception e) {
        Throwable cause = e;
        while (cause != null) {
            String message = cause.getMessage();
            if (message != null && (
                message.contains("ORA-08177") ||  // Can't serialize access
                message.contains("ORA-00060") ||  // Deadlock detected
                message.contains("ORA-00054")     // Resource busy
            )) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
    
    /**
     * Launch validation job without retry (for internal calls)
     */
    @Transactional(isolation = Isolation.READ_COMMITTED, readOnly = true)
    public JobExecution launchValidationJobNoRetry(boolean restart) {
        try {
            return launchValidationJobInternal(restart);
        } catch (Exception e) {
            logger.error("Failed to launch validation job without retry", e);
            throw new RuntimeException("Failed to launch validation job", e);
        }
    }
    
    /**
     * Internal method for launching job without retry annotation
     */
    private JobExecution launchValidationJobInternal(boolean restart) throws Exception {
        JobParameters jobParameters = createUniqueJobParameters(restart);
        return jobLauncher.run(migrationValidationJob, jobParameters);
    }
    
    /**
     * Get the current validation progress
     * @return validation progress information
     */
    @Transactional(readOnly = true)
    public MigrationValidationService.ValidationProgress getProgress() {
        return validationService.getValidationProgress();
    }
    
    /**
     * Check if validation is complete
     * @return true if validation is complete
     */
    @Transactional(readOnly = true)
    public boolean isValidationComplete() {
        return validationService.isValidationComplete();
    }
    
    /**
     * Get validation statistics with retry support
     * @return validation statistics
     */
    @Retryable(
        retryFor = {DataAccessException.class},
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000)
    )
    @Transactional(readOnly = true)
    public String getValidationStatisticsReport() {
        var progress = validationService.getValidationProgress();
        var statistics = validationService.getValidationStatistics();
        
        return String.format("""
            Migration Validation Report
            ==========================
            Total Mobile Numbers: %d
            Validated: %d (%.2f%%)
            Remaining: %d
            
            Validation Results:
            - SR1 (CAM Core): %d passed
            - SR2 (Reserved): %d passed
            - SR4 (Phone Register): %d passed
            - SR5 (SIM+IMSI Match): %d passed
            - SR6 (IMSI in Switch): %d passed
            
            Last Updated: %s
            """,
            progress.totalMobileNumbers(),
            progress.validatedMobileNumbers(),
            progress.progressPercentage(),
            progress.remainingMobileNumbers(),
            statistics.sr1Passed(),
            statistics.sr2Passed(),
            statistics.sr4Passed(),
            statistics.sr5Passed(),
            statistics.sr6Passed(),
            LocalDateTime.now()
        );
    }
}