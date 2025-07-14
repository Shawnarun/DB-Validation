package com.oracle.migration.validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Main Spring Boot application class for Oracle Data Migration Validator
 */
@SpringBootApplication
public class MigrationValidatorApplication {
    
    private static final Logger logger = LoggerFactory.getLogger(MigrationValidatorApplication.class);
    
    public static void main(String[] args) {
        try {
            logger.info("Starting Oracle Data Migration Validator Application");
            
            ConfigurableApplicationContext context = SpringApplication.run(MigrationValidatorApplication.class, args);
            
            logger.info("Oracle Data Migration Validator Application started successfully");
            logger.info("Available endpoints:");
            logger.info("  - Health Check: http://localhost:8080/actuator/health");
            logger.info("  - Batch Jobs: http://localhost:8080/actuator/batch");
            logger.info("  - Metrics: http://localhost:8080/actuator/metrics");
            
        } catch (Exception e) {
            logger.error("Failed to start Oracle Data Migration Validator Application", e);
            System.exit(1);
        }
    }
}