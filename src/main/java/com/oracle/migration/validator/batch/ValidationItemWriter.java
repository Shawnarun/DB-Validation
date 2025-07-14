package com.oracle.migration.validator.batch;

import com.oracle.migration.validator.model.ValidationResult;
import com.oracle.migration.validator.service.MigrationValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;

import java.util.List;

/**
 * Spring Batch ItemWriter for writing validation results to audit table
 */
public class ValidationItemWriter implements ItemWriter<ValidationResult> {
    
    private static final Logger logger = LoggerFactory.getLogger(ValidationItemWriter.class);
    
    private final MigrationValidationService validationService;
    
    public ValidationItemWriter(MigrationValidationService validationService) {
        this.validationService = validationService;
    }
    
    @Override
    public void write(Chunk<? extends ValidationResult> chunk) throws Exception {
        List<ValidationResult> results = chunk.getItems();
        
        if (results.isEmpty()) {
            return;
        }
        
        logger.debug("Writing {} validation results", results.size());
        
        try {
            validationService.saveValidationResults(results);
            
            // Log summary
            long successCount = results.stream()
                .mapToLong(result -> result.hasErrors() ? 0 : 1)
                .sum();
            
            long errorCount = results.size() - successCount;
            
            logger.info("Successfully saved {} validation results, {} errors", 
                       successCount, errorCount);
            
        } catch (Exception e) {
            logger.error("Error writing validation results: {}", e.getMessage(), e);
            throw e;
        }
    }
}