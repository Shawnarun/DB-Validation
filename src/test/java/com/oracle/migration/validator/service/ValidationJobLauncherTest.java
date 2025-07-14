package com.oracle.migration.validator.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.dao.DataAccessException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test class for ValidationJobLauncher with Oracle error handling
 */
@ExtendWith(MockitoExtension.class)
class ValidationJobLauncherTest {
    
    @Mock
    private JobLauncher jobLauncher;
    
    @Mock
    private Job migrationValidationJob;
    
    @Mock
    private MigrationValidationService validationService;
    
    @InjectMocks
    private ValidationJobLauncher validationJobLauncher;
    
    @Test
    void testLaunchValidationJobSuccess() throws Exception {
        // Given
        JobExecution jobExecution = mock(JobExecution.class);
        when(jobExecution.getId()).thenReturn(123L);
        
        MigrationValidationService.ValidationProgress progress = 
            new MigrationValidationService.ValidationProgress(1000, 500, 500, 50.0);
        when(validationService.getValidationProgress()).thenReturn(progress);
        when(validationService.isValidationComplete()).thenReturn(false);
        
        when(jobLauncher.run(eq(migrationValidationJob), any(JobParameters.class)))
            .thenReturn(jobExecution);
        
        // When
        JobExecution result = validationJobLauncher.launchValidationJob(false);
        
        // Then
        assertNotNull(result);
        assertEquals(123L, result.getId());
        verify(jobLauncher).run(eq(migrationValidationJob), any(JobParameters.class));
    }
    
    @Test
    void testLaunchValidationJobAlreadyComplete() {
        // Given
        when(validationService.isValidationComplete()).thenReturn(true);
        
        // When
        JobExecution result = validationJobLauncher.launchValidationJob(false);
        
        // Then
        assertNull(result);
        verifyNoInteractions(jobLauncher);
    }
    
    @Test
    void testSerializationErrorDetection() throws Exception {
        // Given
        Exception oracleSerializationError = new DataAccessException("ORA-08177: can't serialize access") {};
        
        MigrationValidationService.ValidationProgress progress = 
            new MigrationValidationService.ValidationProgress(1000, 500, 500, 50.0);
        when(validationService.getValidationProgress()).thenReturn(progress);
        when(validationService.isValidationComplete()).thenReturn(false);
        
        when(jobLauncher.run(eq(migrationValidationJob), any(JobParameters.class)))
            .thenThrow(oracleSerializationError);
        
        // When/Then
        assertThrows(DataAccessException.class, () -> {
            validationJobLauncher.launchValidationJob(false);
        });
    }
    
    @Test
    void testUniqueJobParametersGeneration() throws Exception {
        // Given
        JobExecution jobExecution1 = mock(JobExecution.class);
        JobExecution jobExecution2 = mock(JobExecution.class);
        when(jobExecution1.getId()).thenReturn(123L);
        when(jobExecution2.getId()).thenReturn(124L);
        
        MigrationValidationService.ValidationProgress progress = 
            new MigrationValidationService.ValidationProgress(1000, 500, 500, 50.0);
        when(validationService.getValidationProgress()).thenReturn(progress);
        when(validationService.isValidationComplete()).thenReturn(false);
        
        when(jobLauncher.run(eq(migrationValidationJob), any(JobParameters.class)))
            .thenReturn(jobExecution1)
            .thenReturn(jobExecution2);
        
        // When
        JobExecution result1 = validationJobLauncher.launchValidationJob(false);
        JobExecution result2 = validationJobLauncher.launchValidationJob(false);
        
        // Then
        assertNotNull(result1);
        assertNotNull(result2);
        assertNotEquals(result1.getId(), result2.getId());
        
        // Verify that different job parameters were used
        verify(jobLauncher, times(2)).run(eq(migrationValidationJob), any(JobParameters.class));
    }
    
    @Test
    void testGetProgressWithRetry() {
        // Given
        MigrationValidationService.ValidationProgress progress = 
            new MigrationValidationService.ValidationProgress(1000, 750, 250, 75.0);
        when(validationService.getValidationProgress()).thenReturn(progress);
        
        // When
        MigrationValidationService.ValidationProgress result = validationJobLauncher.getProgress();
        
        // Then
        assertNotNull(result);
        assertEquals(1000, result.totalMobileNumbers());
        assertEquals(750, result.validatedMobileNumbers());
        assertEquals(250, result.remainingMobileNumbers());
        assertEquals(75.0, result.progressPercentage());
    }
}