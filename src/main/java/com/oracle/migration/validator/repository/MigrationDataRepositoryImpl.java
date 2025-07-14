package com.oracle.migration.validator.repository;

import com.oracle.migration.validator.model.ValidationResult;
import com.oracle.migration.validator.model.ValidationScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Implementation of MigrationDataRepository using JDBC
 */
@Repository
public class MigrationDataRepositoryImpl implements MigrationDataRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(MigrationDataRepositoryImpl.class);
    
    private final JdbcTemplate jdbcTemplate;
    private final MigrationAuditRepository auditRepository;
    private final ExecutorService executorService;
    
    @Autowired
    public MigrationDataRepositoryImpl(JdbcTemplate jdbcTemplate, 
                                     MigrationAuditRepository auditRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.auditRepository = auditRepository;
        this.executorService = Executors.newVirtualThreadPerTaskExecutor();
    }
    
    @Override
    public Page<String> fetchMobileNumbers(Pageable pageable) {
        String countSql = "SELECT COUNT(DISTINCT MOBILE_NO) FROM ATDM.AT_DYN_1_CONNECTION_MIG";
        String dataSql = """
            SELECT * FROM (
                SELECT DISTINCT MOBILE_NO, ROW_NUMBER() OVER (ORDER BY MOBILE_NO) as rn
                FROM ATDM.AT_DYN_1_CONNECTION_MIG
            ) WHERE rn > ? AND rn <= ?
            """;
        
        long total = jdbcTemplate.queryForObject(countSql, Long.class);
        
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        
        List<String> mobileNumbers = jdbcTemplate.queryForList(
            dataSql, 
            String.class, 
            offset, 
            offset + limit
        );
        
        return new PageImpl<>(mobileNumbers, pageable, total);
    }
    
    @Override
    public Page<String> fetchUnvalidatedMobileNumbers(Pageable pageable) {
        String countSql = """
            SELECT COUNT(DISTINCT ATD.MOBILE_NO) 
            FROM ATDM.AT_DYN_1_CONNECTION_MIG ATD
            LEFT JOIN OM.TEMP_MIGRATION_AUDIT TMA ON ATD.MOBILE_NO = TMA.MOBILE_NO
            WHERE TMA.MOBILE_NO IS NULL
            """;
            
        String dataSql = """
            SELECT * FROM (
                SELECT DISTINCT ATD.MOBILE_NO, ROW_NUMBER() OVER (ORDER BY ATD.MOBILE_NO) as rn
                FROM ATDM.AT_DYN_1_CONNECTION_MIG ATD
                LEFT JOIN OM.TEMP_MIGRATION_AUDIT TMA ON ATD.MOBILE_NO = TMA.MOBILE_NO
                WHERE TMA.MOBILE_NO IS NULL
            ) WHERE rn > ? AND rn <= ?
            """;
        
        long total = jdbcTemplate.queryForObject(countSql, Long.class);
        
        int offset = (int) pageable.getOffset();
        int limit = pageable.getPageSize();
        
        List<String> mobileNumbers = jdbcTemplate.queryForList(
            dataSql, 
            String.class, 
            offset, 
            offset + limit
        );
        
        return new PageImpl<>(mobileNumbers, pageable, total);
    }
    
    @Override
    public Set<String> getValidatedMobileNumbers() {
        return auditRepository.findAllValidatedMobileNumbers();
    }
    
    @Override
    public long getTotalMobileNumbersCount() {
        String sql = "SELECT COUNT(DISTINCT MOBILE_NO) FROM ATDM.AT_DYN_1_CONNECTION_MIG";
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
    
    @Override
    public long getValidatedMobileNumbersCount() {
        return auditRepository.getValidatedCount();
    }
    
    @Override
    public String validateSR1(String mobileNo) {
        String sql = """
            SELECT COUNT(*)
            FROM CCBS2.CAM_SUBSCRIBER_PROFILE CSP,
                 CCBS2.CAM_SUBSCRIBER_NODE CSN,
                 CCBS2.CAM_CONTRACT CC,
                 CCBS2.PR_PRIORITY_TYPES PRT,
                 CCBS2.PROV_SWITCH_IMAGE PSI,
                 CCBS2.CAM_CONTRACT_PACKAGE CCP,
                 CCBS2.CAM_CONNECTION CCON,
                 CCBS2.DYN_1_CONNECTION DYN
            WHERE CSP.SUBSCRIBER_PROFILE_ID = CSN.SUBSCRIBER_PROFILE_ID
              AND CSN.SUBSCRIBER_NODE_ID = CC.SUBSCRIBER_NODE_ID
              AND CC.CONTRACT_ID = CCP.CONTRACT_ID
              AND CCP.CONTRACT_PACKAGE_ID = CCON.PACKAGE_CONTRACT_ID
              AND CCON.CONNECTION_ID = DYN.CONNECTION_ID
              AND PSI.IMSI_NO = DYN.IMSI
              AND DYN.SERVICE_ID IN (1, 2, 3, 450, 500)
              AND DYN.MOBILE_NO = ?
              AND ROWNUM = 1
            """;
        
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, mobileNo);
            return (count != null && count > 0) ? "Y" : "N";
        } catch (Exception e) {
            logger.warn("SR1 validation failed for mobile: {}, error: {}", mobileNo, e.getMessage());
            return "N";
        }
    }
    
    @Override
    public String validateSR4(String mobileNo) {
        String sql = """
            SELECT COUNT(*)
            FROM CCBS2.PHONE_NO_REGISTER PNR
            WHERE PNR.STATUS = 'A'
              AND PNR.BLOCKED_STATUS = 'U'
              AND PNR.PHONE_NO = ?
            """;
        
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, mobileNo);
            return (count != null && count > 0) ? "Y" : "N";
        } catch (Exception e) {
            logger.warn("SR4 validation failed for mobile: {}, error: {}", mobileNo, e.getMessage());
            return "N";
        }
    }
    
    @Override
    public String validateSR5(String mobileNo) {
        String sql = """
            SELECT COUNT(*)
            FROM CCBS2.IMSI_SIM_REGISTER ISR,
                 CCBS2.DYN_1_CONNECTION DC
            WHERE (ISR.SUBSTRSIM || ISR.IMSI) = (DC.SIM || DC.IMSI)
              AND DC.MOBILE_NO = ?
            """;
        
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, mobileNo);
            return (count != null && count > 0) ? "Y" : "N";
        } catch (Exception e) {
            logger.warn("SR5 validation failed for mobile: {}, error: {}", mobileNo, e.getMessage());
            return "N";
        }
    }
    
    @Override
    public String validateSR6(String mobileNo) {
        String sql = """
            SELECT COUNT(*)
            FROM CCBS2.PROV_SWITCH_IMAGE PSI,
                 CCBS2.DYN_1_CONNECTION D
            WHERE PSI.IMSI_NO = D.IMSI
              AND D.MOBILE_NO = ?
            """;
        
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, mobileNo);
            return (count != null && count > 0) ? "Y" : "N";
        } catch (Exception e) {
            logger.warn("SR6 validation failed for mobile: {}, error: {}", mobileNo, e.getMessage());
            return "N";
        }
    }
    
    @Override
    public ValidationResult validateMobileNumber(String mobileNo) {
        try {
            Map<ValidationScenario, String> results = new HashMap<>();
            
            // Perform all validations
            results.put(ValidationScenario.SR1, validateSR1(mobileNo));
            results.put(ValidationScenario.SR2, "N"); // Reserved for future use
            results.put(ValidationScenario.SR4, validateSR4(mobileNo));
            results.put(ValidationScenario.SR5, validateSR5(mobileNo));
            results.put(ValidationScenario.SR6, validateSR6(mobileNo));
            
            return ValidationResult.success(mobileNo, results);
            
        } catch (Exception e) {
            logger.error("Validation failed for mobile: {}, error: {}", mobileNo, e.getMessage(), e);
            return ValidationResult.error(mobileNo, e.getMessage());
        }
    }
    
    @Override
    public List<ValidationResult> validateMobileNumbers(List<String> mobileNumbers) {
        List<CompletableFuture<ValidationResult>> futures = mobileNumbers.stream()
            .map(mobileNo -> CompletableFuture.supplyAsync(
                () -> validateMobileNumber(mobileNo), 
                executorService
            ))
            .toList();
        
        return futures.stream()
            .map(CompletableFuture::join)
            .toList();
    }
}