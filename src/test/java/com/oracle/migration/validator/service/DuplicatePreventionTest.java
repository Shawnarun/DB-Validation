package com.oracle.migration.validator.service;

import com.oracle.migration.validator.model.ValidationResult;
import com.oracle.migration.validator.model.ValidationScenario;
import com.oracle.migration.validator.repository.MigrationAuditRepository;
import com.oracle.migration.validator.repository.MigrationDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test class for duplicate prevention functionality
 */
@ExtendWith(MockitoExtension.class)
class DuplicatePreventionTest {
    
    @Mock
    private MigrationDataRepository dataRepository;
    
    @Mock
    private MigrationAuditRepository auditRepository;
    
    private MigrationValidationServiceImpl validationService;
    
    @BeforeEach
    void setUp() {
        validationService = new MigrationValidationServiceImpl(dataRepository, auditRepository);
        
        // Mock initialization
        when(auditRepository.findAllValidatedMobileNumbers()).thenReturn(new HashSet<>());
    }
    
    @Test
    void testConcurrentValidationPreventsDuplicates() throws InterruptedException {
        // Given
        String mobileNo = "9876543210";
        ValidationResult successResult = ValidationResult.success(mobileNo, 
            Map.of(ValidationScenario.SR1, "Y", ValidationScenario.SR4, "Y"));
        
        // Mock that mobile doesn't exist initially
        when(auditRepository.existsByMobileNo(mobileNo)).thenReturn(false);
        when(dataRepository.validateMobileNumber(mobileNo)).thenReturn(successResult);
        
        // Create multiple threads to validate the same mobile number
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        List<Future<ValidationResult>> futures = new ArrayList<>();
        
        // When - Submit concurrent validation tasks
        for (int i = 0; i < threadCount; i++) {
            Future<ValidationResult> future = executor.submit(() -> {
                try {
                    latch.countDown();
                    latch.await(); // All threads start at the same time
                    return validationService.validateMobileNumber(mobileNo);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return ValidationResult.error(mobileNo, "Interrupted");
                }
            });
            futures.add(future);
        }
        
        // Then - Collect results
        List<ValidationResult> results = new ArrayList<>();
        for (Future<ValidationResult> future : futures) {
            results.add(future.get(5, TimeUnit.SECONDS));
        }
        
        executor.shutdown();
        
        // Verify only one successful validation occurred
        long successCount = results.stream()
            .filter(result -> !result.hasErrors())
            .count();
        
        long alreadyProcessingCount = results.stream()
            .filter(ValidationResult::hasErrors)
            .filter(result -> result.errorMessage().contains("Currently being processed"))
            .count();
        
        assertEquals(1, successCount, "Only one thread should successfully validate");
        assertEquals(threadCount - 1, alreadyProcessingCount, 
                    "Other threads should be blocked from processing");
        
        // Verify save was called only once
        verify(auditRepository, times(1)).save(any());
    }
    
    @Test
    void testBatchDeduplication() {
        // Given
        List<String> mobileNumbers = Arrays.asList(
            "9876543210", "9876543211", "9876543210", // duplicate
            "9876543212", "9876543211", "9876543213"  // duplicates
        );
        
        // Mock that none exist in database initially
        when(auditRepository.existsByMobileNo(anyString())).thenReturn(false);
        
        ValidationResult result1 = ValidationResult.success("9876543210", Map.of());
        ValidationResult result2 = ValidationResult.success("9876543211", Map.of());
        ValidationResult result3 = ValidationResult.success("9876543212", Map.of());
        ValidationResult result4 = ValidationResult.success("9876543213", Map.of());
        
        when(dataRepository.validateMobileNumber("9876543210")).thenReturn(result1);
        when(dataRepository.validateMobileNumber("9876543211")).thenReturn(result2);
        when(dataRepository.validateMobileNumber("9876543212")).thenReturn(result3);
        when(dataRepository.validateMobileNumber("9876543213")).thenReturn(result4);
        
        // When
        List<ValidationResult> results = validationService.validateMobileNumbers(mobileNumbers);
        
        // Then
        assertEquals(4, results.size(), "Should process only unique mobile numbers");
        
        Set<String> processedNumbers = results.stream()
            .map(ValidationResult::mobileNo)
            .collect(Collectors.toSet());
        
        assertEquals(4, processedNumbers.size(), "All processed numbers should be unique");
        assertTrue(processedNumbers.contains("9876543210"));
        assertTrue(processedNumbers.contains("9876543211"));
        assertTrue(processedNumbers.contains("9876543212"));
        assertTrue(processedNumbers.contains("9876543213"));
    }
    
    @Test
    void testCacheInitializationAndUsage() {
        // Given
        Set<String> existingNumbers = Set.of("9876543210", "9876543211");
        when(auditRepository.findAllValidatedMobileNumbers()).thenReturn(existingNumbers);
        
        // Create new instance to test initialization
        MigrationValidationServiceImpl service = new MigrationValidationServiceImpl(
            dataRepository, auditRepository);
        
        // When - Try to validate already processed number
        ValidationResult result = service.validateMobileNumber("9876543210");
        
        // Then
        assertTrue(result.hasErrors());
        assertEquals("Already validated", result.errorMessage());
        
        // Verify cache statistics
        var stats = service.getCacheStatistics();
        assertEquals(2, stats.processedCount());
        assertEquals(0, stats.currentlyProcessingCount());
        
        // Verify no database call was made for validation
        verify(dataRepository, never()).validateMobileNumber(anyString());
    }
    
    @Test
    void testDatabaseConstraintHandling() {
        // Given
        String mobileNo = "9876543210";
        ValidationResult successResult = ValidationResult.success(mobileNo, Map.of());
        
        when(auditRepository.existsByMobileNo(mobileNo)).thenReturn(false);
        when(dataRepository.validateMobileNumber(mobileNo)).thenReturn(successResult);
        
        // Simulate database constraint violation on save
        doThrow(new DataIntegrityViolationException("Duplicate key"))
            .when(auditRepository).save(any());
        
        // When
        ValidationResult result = validationService.validateMobileNumber(mobileNo);
        
        // Then - Should handle gracefully without failing
        assertFalse(result.hasErrors(), "Should not propagate constraint violation as error");
        
        // Verify save was attempted
        verify(auditRepository, times(1)).save(any());
    }
    
    @Test
    void testCacheClearFunctionality() {
        // Given
        when(auditRepository.findAllValidatedMobileNumbers())
            .thenReturn(Set.of("9876543210"));
        
        MigrationValidationServiceImpl service = new MigrationValidationServiceImpl(
            dataRepository, auditRepository);
        
        // Verify cache is populated
        assertEquals(1, service.getCacheStatistics().processedCount());
        
        // When
        service.clearCache();
        
        // Then
        assertEquals(0, service.getCacheStatistics().processedCount());
        assertEquals(0, service.getCacheStatistics().currentlyProcessingCount());
    }
    
    @Test
    void testHighConcurrencyStressTest() throws InterruptedException, ExecutionException {
        // Given
        int threadCount = 50;
        int numbersPerThread = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        
        // Create overlapping mobile numbers to test race conditions
        List<String> baseMobileNumbers = IntStream.range(0, 10)
            .mapToObj(i -> "987654321" + i)
            .toList();
        
        when(auditRepository.existsByMobileNo(anyString())).thenReturn(false);
        when(dataRepository.validateMobileNumber(anyString()))
            .thenAnswer(invocation -> {
                String mobile = invocation.getArgument(0);
                return ValidationResult.success(mobile, Map.of());
            });
        
        CountDownLatch startLatch = new CountDownLatch(threadCount);
        List<Future<List<ValidationResult>>> futures = new ArrayList<>();
        
        // When - Submit multiple threads processing overlapping numbers
        for (int t = 0; t < threadCount; t++) {
            Future<List<ValidationResult>> future = executor.submit(() -> {
                startLatch.countDown();
                startLatch.await();
                
                // Each thread processes random subset of numbers
                List<String> numbersToProcess = new ArrayList<>();
                Random random = new Random();
                for (int i = 0; i < numbersPerThread; i++) {
                    numbersToProcess.add(baseMobileNumbers.get(
                        random.nextInt(baseMobileNumbers.size())));
                }
                
                return validationService.validateMobileNumbers(numbersToProcess);
            });
            futures.add(future);
        }
        
        // Then - Collect all results
        Set<String> allProcessedNumbers = new HashSet<>();
        int totalResults = 0;
        
        for (Future<List<ValidationResult>> future : futures) {
            List<ValidationResult> results = future.get(10, TimeUnit.SECONDS);
            totalResults += results.size();
            
            for (ValidationResult result : results) {
                assertFalse(result.hasErrors(), 
                    "No validation should fail due to race conditions");
                allProcessedNumbers.add(result.mobileNo());
            }
        }
        
        executor.shutdown();
        
        // Verify no duplicates were processed across all threads
        assertEquals(baseMobileNumbers.size(), allProcessedNumbers.size(),
                    "Each unique mobile number should be processed exactly once");
        
        // Verify cache statistics
        var stats = validationService.getCacheStatistics();
        assertEquals(baseMobileNumbers.size(), stats.processedCount());
        assertEquals(0, stats.currentlyProcessingCount());
        
        System.out.println("Stress test completed: " + threadCount + " threads, " + 
                          totalResults + " total validations, " + 
                          allProcessedNumbers.size() + " unique numbers processed");
    }
}