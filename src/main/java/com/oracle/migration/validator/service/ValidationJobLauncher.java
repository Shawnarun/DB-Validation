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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Service for launching and managing validation jobs
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
     * Launch the migration validation job with options
     * @param restart whether to restart from where it left off
     * @return JobExecution result
     */
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
            
            // Create job parameters with timestamp to ensure unique job instance
            JobParameters jobParameters = new JobParametersBuilder()
                .addString("startTime", LocalDateTime.now().toString())
                .addString("restart", String.valueOf(restart))
                .toJobParameters();
            
            JobExecution execution = jobLauncher.run(migrationValidationJob, jobParameters);
            
            logger.info("Migration validation job launched with execution ID: {}", 
                       execution.getId());
            
            return execution;
            
        } catch (Exception e) {
            logger.error("Failed to launch migration validation job", e);
            throw new RuntimeException("Failed to launch validation job", e);
        }
    }
    
    /**
     * Get the current validation progress
     * @return validation progress information
     */
    public MigrationValidationService.ValidationProgress getProgress() {
        return validationService.getValidationProgress();
    }
    
    /**
     * Check if validation is complete
     * @return true if validation is complete
     */
    public boolean isValidationComplete() {
        return validationService.isValidationComplete();
    }
    
    /**
     * Get validation statistics
     * @return validation statistics
     */
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
            """,
            progress.totalMobileNumbers(),
            progress.validatedMobileNumbers(),
            progress.progressPercentage(),
            progress.remainingMobileNumbers(),
            statistics.sr1Passed(),
            statistics.sr2Passed(),
            statistics.sr4Passed(),
            statistics.sr5Passed(),
            statistics.sr6Passed()
        );
    }
}