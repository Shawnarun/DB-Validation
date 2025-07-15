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
import org.springframework.core.task.VirtualThreadTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;

import java.util.concurrent.Executors;

/**
 * Spring Batch configuration optimized for virtual threads and Oracle compatibility
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
            // Virtual threads handle concurrency better than throttling
            .throttleLimit(threadPoolSize * 10) // Higher limit for virtual threads
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
    
    /**
     * Virtual Thread Task Executor for optimal performance
     * Virtual threads are perfect for I/O-intensive database operations
     */
    @Bean
    public TaskExecutor validationTaskExecutor() {
        logger.info("Configuring validation task executor with virtual threads");
        
        // Use virtual threads for better resource utilization
        VirtualThreadTaskExecutor executor = new VirtualThreadTaskExecutor("validation-vt-");
        
        // Virtual threads don't need traditional pool configuration
        // They are created on-demand and are very lightweight
        
        return executor;
    }
    
    /**
     * Alternative: Custom virtual thread executor for more control
     */
    @Bean("customVirtualThreadExecutor")
    public TaskExecutor customVirtualThreadExecutor() {
        logger.info("Creating custom virtual thread executor");
        
        return new TaskExecutor() {
            private final java.util.concurrent.ExecutorService virtualExecutor = 
                Executors.newVirtualThreadPerTaskExecutor();
            
            @Override
            public void execute(Runnable task) {
                virtualExecutor.submit(task);
            }
        };
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
                logger.info("Using virtual threads for batch processing");
                
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