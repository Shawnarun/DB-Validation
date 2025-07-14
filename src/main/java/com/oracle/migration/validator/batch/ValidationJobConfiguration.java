package com.oracle.migration.validator.batch;

import com.oracle.migration.validator.model.ValidationResult;
import com.oracle.migration.validator.service.MigrationValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobExecutionListener;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;

/**
 * Spring Batch configuration for migration validation job with Oracle support
 */
@Configuration
@EnableBatchProcessing
public class ValidationJobConfiguration {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidationJobConfiguration.class);
    
    @Value("${migration.validation.chunk-size:100}")
    private int chunkSize;
    
    @Value("${migration.validation.thread-pool-size:4}")
    private int threadPoolSize;
    
    @Value("${migration.validation.skip-limit:10}")
    private int skipLimit;
    
    @Autowired
    private MigrationValidationService validationService;
    
    @Bean
    public Job migrationValidationJob(JobRepository jobRepository, Step validationStep) {
        return new JobBuilder("migrationValidationJob", jobRepository)
            .listener(validationJobListener())
            .start(validationStep)
            .build();
    }
    
    @Bean
    public Step validationStep(JobRepository jobRepository, 
                              PlatformTransactionManager transactionManager,
                              ItemReader<String> mobileNumberReader,
                              ItemProcessor<String, ValidationResult> validationProcessor,
                              ItemWriter<ValidationResult> validationWriter) {
        return new StepBuilder("validationStep", jobRepository)
            .<String, ValidationResult>chunk(chunkSize, transactionManager)
            .reader(mobileNumberReader)
            .processor(validationProcessor)
            .writer(validationWriter)
            .faultTolerant()
            .skip(Exception.class)
            .skipLimit(skipLimit)
            .retryLimit(3)
            .retry(org.springframework.dao.DataAccessException.class)
            .backOffPolicy(exponentialBackOffPolicy())
            .taskExecutor(validationTaskExecutor())
            // Use synchronous processing for Oracle to avoid serialization issues
            .throttleLimit(1)
            .build();
    }
    
    @Bean
    public ItemReader<String> mobileNumberReader() {
        return new MobileNumberItemReader(validationService);
    }
    
    @Bean
    public ItemProcessor<String, ValidationResult> validationProcessor() {
        return new ValidationItemProcessor(validationService);
    }
    
    @Bean
    public ItemWriter<ValidationResult> validationWriter() {
        return new ValidationItemWriter(validationService);
    }
    
    @Bean
    public TaskExecutor validationTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        // Reduce concurrency to avoid Oracle serialization issues
        executor.setCorePoolSize(Math.min(threadPoolSize, 2));
        executor.setMaxPoolSize(Math.min(threadPoolSize * 2, 4));
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("validation-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
    
    /**
     * Exponential backoff policy for retries
     */
    @Bean
    public org.springframework.retry.backoff.BackOffPolicy exponentialBackOffPolicy() {
        org.springframework.retry.backoff.ExponentialBackOffPolicy backOffPolicy = 
            new org.springframework.retry.backoff.ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(1000);
        backOffPolicy.setMaxInterval(10000);
        backOffPolicy.setMultiplier(2.0);
        return backOffPolicy;
    }
    
    @Bean
    public JobExecutionListener validationJobListener() {
        return new JobExecutionListener() {
            
            @Override
            public void beforeJob(JobExecution jobExecution) {
                logger.info("Starting migration validation job: {}", jobExecution.getJobInstance().getJobName());
                logger.info("Job parameters: {}", jobExecution.getJobParameters().toProperties());
                
                try {
                    var progress = validationService.getValidationProgress();
                    logger.info("Initial progress: {}/{} mobile numbers validated ({}%)", 
                               progress.validatedMobileNumbers(), 
                               progress.totalMobileNumbers(),
                               String.format("%.2f", progress.progressPercentage()));
                } catch (Exception e) {
                    logger.warn("Could not retrieve initial progress: {}", e.getMessage());
                }
            }
            
            @Override
            public void afterJob(JobExecution jobExecution) {
                logger.info("Completed migration validation job: {} with status: {}", 
                           jobExecution.getJobInstance().getJobName(),
                           jobExecution.getStatus());
                
                try {
                    var progress = validationService.getValidationProgress();
                    logger.info("Final progress: {}/{} mobile numbers validated ({}%)", 
                               progress.validatedMobileNumbers(), 
                               progress.totalMobileNumbers(),
                               String.format("%.2f", progress.progressPercentage()));
                    
                    var statistics = validationService.getValidationStatistics();
                    logger.info("Validation statistics: Total={}, SR1={}, SR2={}, SR4={}, SR5={}, SR6={}", 
                               statistics.totalValidated(),
                               statistics.sr1Passed(),
                               statistics.sr2Passed(),
                               statistics.sr4Passed(),
                               statistics.sr5Passed(),
                               statistics.sr6Passed());
                } catch (Exception e) {
                    logger.warn("Could not retrieve final statistics: {}", e.getMessage());
                }
                
                if (jobExecution.getStatus().isUnsuccessful()) {
                    logger.error("Job failed with {} failures", 
                                jobExecution.getAllFailureExceptions().size());
                    jobExecution.getAllFailureExceptions().forEach(ex -> {
                        if (ex.getMessage() != null && ex.getMessage().contains("ORA-08177")) {
                            logger.error("Oracle serialization error detected: {}", ex.getMessage());
                        } else {
                            logger.error("Job failure: ", ex);
                        }
                    });
                }
            }
        };
    }
}