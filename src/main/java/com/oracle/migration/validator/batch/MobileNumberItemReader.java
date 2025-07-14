package com.oracle.migration.validator.batch;

import com.oracle.migration.validator.service.MigrationValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemReader;
import org.springframework.batch.item.NonTransientResourceException;
import org.springframework.batch.item.ParseException;
import org.springframework.batch.item.UnexpectedInputException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.Iterator;

/**
 * Spring Batch ItemReader for reading mobile numbers from the database with pagination
 */
public class MobileNumberItemReader implements ItemReader<String> {
    
    private static final Logger logger = LoggerFactory.getLogger(MobileNumberItemReader.class);
    
    private final MigrationValidationService validationService;
    private final int pageSize;
    
    private Iterator<String> currentPageIterator;
    private int currentPage = 0;
    private boolean hasMorePages = true;
    
    public MobileNumberItemReader(MigrationValidationService validationService) {
        this(validationService, 1000); // Default page size
    }
    
    public MobileNumberItemReader(MigrationValidationService validationService, int pageSize) {
        this.validationService = validationService;
        this.pageSize = pageSize;
    }
    
    @Override
    public String read() throws Exception, UnexpectedInputException, ParseException, NonTransientResourceException {
        // If current page iterator is null or exhausted, load next page
        if (currentPageIterator == null || !currentPageIterator.hasNext()) {
            if (!hasMorePages) {
                logger.info("No more mobile numbers to read");
                return null; // End of data
            }
            
            loadNextPage();
        }
        
        return currentPageIterator != null && currentPageIterator.hasNext() ? 
               currentPageIterator.next() : null;
    }
    
    private void loadNextPage() {
        try {
            Pageable pageable = PageRequest.of(currentPage, pageSize);
            Page<String> page = validationService.getMobileNumbersForValidation(pageable, true); // Skip validated
            
            logger.debug("Loading page {} with {} elements", currentPage, page.getNumberOfElements());
            
            if (page.hasContent()) {
                currentPageIterator = page.getContent().iterator();
                currentPage++;
                hasMorePages = page.hasNext();
            } else {
                hasMorePages = false;
                currentPageIterator = null;
                logger.info("No more data available. Processed {} pages", currentPage);
            }
            
        } catch (Exception e) {
            logger.error("Error loading page {}: {}", currentPage, e.getMessage(), e);
            hasMorePages = false;
            currentPageIterator = null;
            throw new NonTransientResourceException("Failed to load mobile numbers from database", e);
        }
    }
    
    /**
     * Get the current progress information
     * @return current page number
     */
    public int getCurrentPage() {
        return currentPage;
    }
    
    /**
     * Check if there are more pages to read
     * @return true if more pages available
     */
    public boolean hasMorePages() {
        return hasMorePages;
    }
}