package com.oracle.migration.validator.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

@Component
public class DatabaseConfigValidator {
    
    private static final Logger logger = LoggerFactory.getLogger(DatabaseConfigValidator.class);
    
    @Value("${spring.datasource.url}")
    private String databaseUrl;
    
    @Value("${spring.datasource.username}")
    private String databaseUsername;
    
    private final DataSource dataSource;
    
    public DatabaseConfigValidator(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    
    @EventListener(ApplicationStartedEvent.class)
    public void validateDatabaseConnection() {
        logger.info("Validating database connection...");
        
        try {
            validateConnectionProperties();
            testDatabaseConnection();
            logger.info("Database connection validation successful");
        } catch (Exception e) {
            logger.error("Database connection validation failed", e);
            provideTroubleshootingInfo();
        }
    }
    
    private void validateConnectionProperties() {
        logger.info("Database URL: {}", databaseUrl);
        logger.info("Database Username: {}", databaseUsername);
        
        if (databaseUrl == null || databaseUrl.trim().isEmpty()) {
            throw new IllegalStateException("Database URL is not configured. Please set DB_HOST, DB_PORT, and DB_SID environment variables.");
        }
        
        if (databaseUsername == null || databaseUsername.trim().isEmpty()) {
            throw new IllegalStateException("Database username is not configured. Please set DB_USERNAME environment variable.");
        }
        
        // Check if URL contains placeholder values
        if (databaseUrl.contains("${")) {
            throw new IllegalStateException("Database URL contains unresolved placeholders. Please check environment variables.");
        }
    }
    
    private void testDatabaseConnection() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            logger.info("Connected to database: {} {}", 
                       metaData.getDatabaseProductName(), 
                       metaData.getDatabaseProductVersion());
            
            // Test basic query
            try (var statement = connection.prepareStatement("SELECT 1 FROM DUAL")) {
                try (var resultSet = statement.executeQuery()) {
                    if (resultSet.next()) {
                        logger.info("Database query test successful");
                    }
                }
            }
        }
    }
    
    private void provideTroubleshootingInfo() {
        logger.error("=== DATABASE CONNECTION TROUBLESHOOTING ===");
        logger.error("Please check the following:");
        logger.error("1. Database server is running and accessible");
        logger.error("2. Environment variables are set correctly:");
        logger.error("   - DB_HOST (database host, e.g., localhost)");
        logger.error("   - DB_PORT (database port, e.g., 1521)");
        logger.error("   - DB_SID (database service name, e.g., XE)");
        logger.error("   - DB_USERNAME (database username)");
        logger.error("   - DB_PASSWORD (database password)");
        logger.error("3. Network connectivity to database server");
        logger.error("4. Database user has required permissions");
        logger.error("5. Oracle JDBC driver is available in classpath");
        logger.error("==========================================");
    }
}