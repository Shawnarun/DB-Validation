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

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test class to verify virtual thread compatibility with duplicate prevention
 */
@ExtendWith(MockitoExtension.class)
class VirtualThreadDuplicatePreventionTest {
    
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
    void testVirtualThreadConcurrentValidationPreventsDuplicates() throws InterruptedException, ExecutionException {
        // Given
        String mobileNo = "9876543210";
        ValidationResult successResult = ValidationResult.success(mobileNo, 
            Map.of(ValidationScenario.SR1, "Y", ValidationScenario.SR4, "Y"));
        
        // Mock that mobile doesn't exist initially
        when(auditRepository.existsByMobileNo(mobileNo)).thenReturn(false);
        when(dataRepository.validateMobileNumber(mobileNo)).thenReturn(successResult);
        
        // Use virtual thread executor
        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            // Create many virtual threads to validate the same mobile number
            int virtualThreadCount = 100;
            CountDownLatch latch = new CountDownLatch(virtualThreadCount);
            List<Future<ValidationResult>> futures = new ArrayList<>();
            
            // When - Submit concurrent validation tasks using virtual threads
            for (int i = 0; i < virtualThreadCount; i++) {
                Future<ValidationResult> future = virtualExecutor.submit(() -> {
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
                results.add(future.get(10, TimeUnit.SECONDS));
            }
            
            // Verify only one successful validation occurred
            long successCount = results.stream()
                .filter(result -> !result.hasErrors())
                .count();
            
            long alreadyProcessingCount = results.stream()
                .filter(ValidationResult::hasErrors)
                .filter(result -> result.errorMessage().contains("Currently being processed"))
                .count();
            
            assertEquals(1, successCount, "Only one virtual thread should successfully validate");
            assertEquals(virtualThreadCount - 1, alreadyProcessingCount, 
                        "Other virtual threads should be blocked from processing");
            
            // Verify save was called only once
            verify(auditRepository, times(1)).save(any());
        }
    }
    
    @Test
    void testVirtualThreadHighConcurrencyStressTest() throws InterruptedException, ExecutionException {
        // Given
        int virtualThreadCount = 1000; // Much higher than platform thread limit
        int uniqueMobileCount = 50;
        
        // Create mobile numbers
        List<String> mobileNumbers = IntStream.range(0, uniqueMobileCount)
            .mapToObj(i -> "987654321" + String.format("%02d", i))
            .toList();
        
        when(auditRepository.existsByMobileNo(anyString())).thenReturn(false);
        when(dataRepository.validateMobileNumber(anyString()))
            .thenAnswer(invocation -> {
                String mobile = invocation.getArgument(0);
                return ValidationResult.success(mobile, Map.of());
            });
        
        // Use virtual thread executor
        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            CountDownLatch startLatch = new CountDownLatch(virtualThreadCount);
            List<Future<ValidationResult>> futures = new ArrayList<>();
            
            // When - Submit many virtual threads processing overlapping numbers
            for (int t = 0; t < virtualThreadCount; t++) {
                Future<ValidationResult> future = virtualExecutor.submit(() -> {
                    try {
                        startLatch.countDown();
                        startLatch.await();
                        
                        // Each virtual thread processes a random mobile number
                        Random random = new Random();
                        String randomMobile = mobileNumbers.get(random.nextInt(mobileNumbers.size()));
                        
                        return validationService.validateMobileNumber(randomMobile);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return ValidationResult.error("", "Interrupted");
                    }
                });
                futures.add(future);
            }
            
            // Then - Collect all results
            Set<String> allProcessedNumbers = new HashSet<>();
            int totalSuccessResults = 0;
            
            for (Future<ValidationResult> future : futures) {
                ValidationResult result = future.get(15, TimeUnit.SECONDS);
                
                if (!result.hasErrors()) {
                    allProcessedNumbers.add(result.mobileNo());
                    totalSuccessResults++;
                }
            }
            
            // Verify duplicate prevention worked across all virtual threads
            assertEquals(uniqueMobileCount, allProcessedNumbers.size(),
                        "Each unique mobile number should be processed exactly once");
            
            assertEquals(uniqueMobileCount, totalSuccessResults,
                        "Total successful results should equal unique mobile numbers");
            
            // Verify cache statistics
            var stats = validationService.getCacheStatistics();
            assertEquals(uniqueMobileCount, stats.processedCount());
            assertEquals(0, stats.currentlyProcessingCount());
            
            System.out.println("Virtual thread stress test completed: " + 
                              virtualThreadCount + " virtual threads, " + 
                              totalSuccessResults + " successful validations, " + 
                              allProcessedNumbers.size() + " unique numbers processed");
        }
    }
    
    @Test
    void testVirtualThreadMemoryEfficiency() throws InterruptedException {
        // Given
        int virtualThreadCount = 500; // Would be impossible with platform threads
        String mobileNo = "9876543210";
        
        when(auditRepository.existsByMobileNo(mobileNo)).thenReturn(false);
        when(dataRepository.validateMobileNumber(mobileNo))
            .thenAnswer(invocation -> {
                // Simulate some processing time
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return ValidationResult.success(mobileNo, Map.of());
            });
        
        // Use virtual thread executor
        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            long memoryBefore = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            
            CountDownLatch latch = new CountDownLatch(virtualThreadCount);
            List<Future<ValidationResult>> futures = new ArrayList<>();
            
            // When - Create many virtual threads simultaneously
            for (int i = 0; i < virtualThreadCount; i++) {
                Future<ValidationResult> future = virtualExecutor.submit(() -> {
                    try {
                        latch.countDown();
                        latch.await();
                        return validationService.validateMobileNumber(mobileNo);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return ValidationResult.error(mobileNo, "Interrupted");
                    }
                });
                futures.add(future);
            }
            
            // Wait for all to complete
            for (Future<ValidationResult> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
            
            long memoryAfter = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memoryUsed = memoryAfter - memoryBefore;
            
            // Virtual threads should use much less memory than platform threads
            // Each platform thread uses ~2MB, virtual threads use ~2KB
            long expectedPlatformThreadMemory = virtualThreadCount * 2 * 1024 * 1024; // 2MB per thread
            
            assertTrue(memoryUsed < expectedPlatformThreadMemory / 10,
                      "Virtual threads should use much less memory than platform threads. " +
                      "Used: " + memoryUsed + " bytes, Expected max: " + (expectedPlatformThreadMemory / 10));
            
            System.out.println("Memory efficiency test: " + 
                              virtualThreadCount + " virtual threads used " + 
                              (memoryUsed / 1024 / 1024) + " MB (vs expected " + 
                              (expectedPlatformThreadMemory / 1024 / 1024) + " MB for platform threads)");
        }
    }
    
    @Test
    void testVirtualThreadScalabilityBeyondPlatformThreads() throws InterruptedException, ExecutionException {
        // Given - Scale that would be impossible with platform threads
        int virtualThreadCount = 2000;
        List<String> mobileNumbers = IntStream.range(0, 100)
            .mapToObj(i -> "987654321" + String.format("%02d", i))
            .toList();
        
        when(auditRepository.existsByMobileNo(anyString())).thenReturn(false);
        when(dataRepository.validateMobileNumber(anyString()))
            .thenAnswer(invocation -> {
                String mobile = invocation.getArgument(0);
                return ValidationResult.success(mobile, Map.of());
            });
        
        // Use virtual thread executor
        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            long startTime = System.currentTimeMillis();
            
            List<Future<ValidationResult>> futures = IntStream.range(0, virtualThreadCount)
                .mapToObj(i -> virtualExecutor.submit(() -> {
                    Random random = new Random();
                    String randomMobile = mobileNumbers.get(random.nextInt(mobileNumbers.size()));
                    return validationService.validateMobileNumber(randomMobile);
                }))
                .toList();
            
            // Collect results
            Set<String> processedNumbers = new HashSet<>();
            for (Future<ValidationResult> future : futures) {
                ValidationResult result = future.get(20, TimeUnit.SECONDS);
                if (!result.hasErrors()) {
                    processedNumbers.add(result.mobileNo());
                }
            }
            
            long duration = System.currentTimeMillis() - startTime;
            
            // Verify scalability
            assertEquals(mobileNumbers.size(), processedNumbers.size(),
                        "All unique mobile numbers should be processed");
            
            assertTrue(duration < 30000, // Should complete in under 30 seconds
                      "Virtual threads should handle high concurrency efficiently");
            
            System.out.println("Scalability test: " + virtualThreadCount + 
                              " virtual threads processed " + processedNumbers.size() + 
                              " unique numbers in " + duration + " ms");
        }
    }
    
    @Test
    void testVirtualThreadCompatibilityWithConcurrentHashMap() {
        // Given
        int virtualThreadCount = 200;
        String mobileNo = "9876543210";
        
        when(auditRepository.existsByMobileNo(mobileNo)).thenReturn(false);
        when(dataRepository.validateMobileNumber(mobileNo))
            .thenReturn(ValidationResult.success(mobileNo, Map.of()));
        
        // Use virtual thread executor
        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor()) {
            
            // When - Multiple virtual threads access ConcurrentHashMap
            List<Future<String>> futures = IntStream.range(0, virtualThreadCount)
                .mapToObj(i -> virtualExecutor.submit(() -> {
                    ValidationResult result = validationService.validateMobileNumber(mobileNo);
                    return result.hasErrors() ? "error" : "success";
                }))
                .toList();
            
            // Then - Collect results
            Map<String, Long> resultCounts = futures.stream()
                .map(future -> {
                    try {
                        return future.get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        return "exception";
                    }
                })
                .collect(Collectors.groupingBy(
                    result -> result,
                    Collectors.counting()
                ));
            
            // Verify ConcurrentHashMap worked correctly with virtual threads
            assertEquals(1, resultCounts.getOrDefault("success", 0L),
                        "Only one virtual thread should succeed");
            
            assertEquals(virtualThreadCount - 1, resultCounts.getOrDefault("error", 0L),
                        "Other virtual threads should be prevented");
            
            assertEquals(0, resultCounts.getOrDefault("exception", 0L),
                        "No exceptions should occur with ConcurrentHashMap");
        }
    }
}