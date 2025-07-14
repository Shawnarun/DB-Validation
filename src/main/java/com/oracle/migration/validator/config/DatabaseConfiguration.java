package com.oracle.migration.validator.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.sql.Connection;

/**
 * Database configuration for Oracle connections with serialization handling
 */
@Configuration
@EnableTransactionManagement
@EnableRetry
public class DatabaseConfiguration {
    
    /**
     * Configure JdbcTemplate with custom settings for Oracle
     */
    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setQueryTimeout(300); // 5 minutes timeout for long queries
        jdbcTemplate.setFetchSize(1000); // Optimize fetch size for large result sets
        return jdbcTemplate;
    }
    
    /**
     * Configure transaction manager with READ_COMMITTED isolation level
     * to avoid Oracle serialization issues
     */
    @Bean
    public PlatformTransactionManager transactionManager(DataSource dataSource) {
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        
        // Set default isolation level to READ_COMMITTED to avoid ORA-08177
        transactionManager.setDefaultTimeout(300); // 5 minutes
        transactionManager.setNestedTransactionAllowed(true);
        
        return transactionManager;
    }
    
    /**
     * Configuration properties for migration validation
     */
    @ConfigurationProperties(prefix = "migration.validation")
    public static class ValidationProperties {
        private int batchSize = 1000;
        private int chunkSize = 100;
        private int threadPoolSize = 4;
        private int skipLimit = 10;
        private boolean restartOnError = true;
        
        // Getters and setters
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        
        public int getChunkSize() { return chunkSize; }
        public void setChunkSize(int chunkSize) { this.chunkSize = chunkSize; }
        
        public int getThreadPoolSize() { return threadPoolSize; }
        public void setThreadPoolSize(int threadPoolSize) { this.threadPoolSize = threadPoolSize; }
        
        public int getSkipLimit() { return skipLimit; }
        public void setSkipLimit(int skipLimit) { this.skipLimit = skipLimit; }
        
        public boolean isRestartOnError() { return restartOnError; }
        public void setRestartOnError(boolean restartOnError) { this.restartOnError = restartOnError; }
    }
}