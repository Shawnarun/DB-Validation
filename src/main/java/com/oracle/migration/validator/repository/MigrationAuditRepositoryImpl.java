package com.oracle.migration.validator.repository;

import com.oracle.migration.validator.model.MigrationAudit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Implementation of MigrationAuditRepository using JDBC
 */
@Repository
public class MigrationAuditRepositoryImpl implements MigrationAuditRepository {
    
    private static final Logger logger = LoggerFactory.getLogger(MigrationAuditRepositoryImpl.class);
    
    private final JdbcTemplate jdbcTemplate;
    
    @Autowired
    public MigrationAuditRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        createAuditTableIfNotExists();
    }
    
    @Override
    @Transactional
    public void save(MigrationAudit audit) {
        String sql = """
            INSERT INTO OM.TEMP_MIGRATION_AUDIT 
            (MOBILE_NO, V1, V2, V3, V4, V5, CREATED_DATE) 
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        
        try {
            jdbcTemplate.update(sql,
                audit.mobileNo(),
                audit.v1(),
                audit.v2(),
                audit.v3(),
                audit.v4(),
                audit.v5(),
                Timestamp.valueOf(audit.createdDate())
            );
            logger.debug("Saved audit record for mobile: {}", audit.mobileNo());
        } catch (DataAccessException e) {
            logger.error("Failed to save audit record for mobile: {}, error: {}", 
                        audit.mobileNo(), e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    @Transactional
    public void saveAll(List<MigrationAudit> audits) {
        String sql = """
            INSERT INTO OM.TEMP_MIGRATION_AUDIT 
            (MOBILE_NO, V1, V2, V3, V4, V5, CREATED_DATE) 
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
        
        try {
            List<Object[]> batchArgs = audits.stream()
                .map(audit -> new Object[]{
                    audit.mobileNo(),
                    audit.v1(),
                    audit.v2(),
                    audit.v3(),
                    audit.v4(),
                    audit.v5(),
                    Timestamp.valueOf(audit.createdDate())
                })
                .toList();
            
            jdbcTemplate.batchUpdate(sql, batchArgs);
            logger.debug("Saved {} audit records in batch", audits.size());
        } catch (DataAccessException e) {
            logger.error("Failed to save audit records in batch, error: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public boolean existsByMobileNo(String mobileNo) {
        String sql = "SELECT COUNT(*) FROM OM.TEMP_MIGRATION_AUDIT WHERE MOBILE_NO = ?";
        
        try {
            Integer count = jdbcTemplate.queryForObject(sql, Integer.class, mobileNo);
            return count != null && count > 0;
        } catch (DataAccessException e) {
            logger.warn("Failed to check existence for mobile: {}, error: {}", mobileNo, e.getMessage());
            return false;
        }
    }
    
    @Override
    public Set<String> findAllValidatedMobileNumbers() {
        String sql = "SELECT MOBILE_NO FROM OM.TEMP_MIGRATION_AUDIT";
        
        try {
            List<String> mobileNumbers = jdbcTemplate.queryForList(sql, String.class);
            return new HashSet<>(mobileNumbers);
        } catch (DataAccessException e) {
            logger.error("Failed to fetch validated mobile numbers, error: {}", e.getMessage(), e);
            return new HashSet<>();
        }
    }
    
    @Override
    public long getValidatedCount() {
        String sql = "SELECT COUNT(*) FROM OM.TEMP_MIGRATION_AUDIT";
        
        try {
            Long count = jdbcTemplate.queryForObject(sql, Long.class);
            return count != null ? count : 0L;
        } catch (DataAccessException e) {
            logger.error("Failed to get validated count, error: {}", e.getMessage(), e);
            return 0L;
        }
    }
    
    @Override
    public void createAuditTableIfNotExists() {
        String checkTableSql = """
            SELECT COUNT(*) FROM user_tables WHERE table_name = 'TEMP_MIGRATION_AUDIT'
            """;
        
        try {
            Integer count = jdbcTemplate.queryForObject(checkTableSql, Integer.class);
            
            if (count == null || count == 0) {
                String createTableSql = """
                    CREATE TABLE OM.TEMP_MIGRATION_AUDIT (
                        MOBILE_NO     VARCHAR2(20) NOT NULL,
                        V1            VARCHAR2(10),
                        V2            VARCHAR2(10),
                        V3            VARCHAR2(10),
                        V4            VARCHAR2(10),
                        V5            VARCHAR2(10),
                        CREATED_DATE  DATE DEFAULT SYSDATE,
                        CONSTRAINT PK_TEMP_MIGRATION_AUDIT PRIMARY KEY (MOBILE_NO)
                    )
                    """;
                
                jdbcTemplate.execute(createTableSql);
                logger.info("Created TEMP_MIGRATION_AUDIT table with unique constraint on MOBILE_NO");
                
            } else {
                logger.info("TEMP_MIGRATION_AUDIT table already exists");
                
                // Check if unique constraint exists, add if missing
                ensureUniqueConstraintExists();
            }
        } catch (DataAccessException e) {
            logger.error("Failed to create audit table, error: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Ensure unique constraint exists on MOBILE_NO column
     */
    private void ensureUniqueConstraintExists() {
        try {
            String checkConstraintSql = """
                SELECT COUNT(*) FROM user_constraints 
                WHERE table_name = 'TEMP_MIGRATION_AUDIT' 
                AND constraint_type = 'P'
                """;
            
            Integer constraintCount = jdbcTemplate.queryForObject(checkConstraintSql, Integer.class);
            
            if (constraintCount == null || constraintCount == 0) {
                logger.info("Adding unique constraint to existing TEMP_MIGRATION_AUDIT table");
                
                // First remove any existing duplicates
                removeDuplicateRecords();
                
                // Add primary key constraint
                String addConstraintSql = """
                    ALTER TABLE OM.TEMP_MIGRATION_AUDIT 
                    ADD CONSTRAINT PK_TEMP_MIGRATION_AUDIT PRIMARY KEY (MOBILE_NO)
                    """;
                
                jdbcTemplate.execute(addConstraintSql);
                logger.info("Successfully added unique constraint on MOBILE_NO column");
                
            } else {
                logger.debug("Unique constraint already exists on MOBILE_NO column");
            }
            
        } catch (DataAccessException e) {
            logger.warn("Could not add unique constraint (may already exist): {}", e.getMessage());
        }
    }
    
    /**
     * Remove duplicate records keeping only the first occurrence
     */
    private void removeDuplicateRecords() {
        try {
            String removeDuplicatesSql = """
                DELETE FROM OM.TEMP_MIGRATION_AUDIT 
                WHERE ROWID NOT IN (
                    SELECT MIN(ROWID) 
                    FROM OM.TEMP_MIGRATION_AUDIT 
                    GROUP BY MOBILE_NO
                )
                """;
            
            int deletedRows = jdbcTemplate.update(removeDuplicatesSql);
            if (deletedRows > 0) {
                logger.info("Removed {} duplicate records from audit table", deletedRows);
            }
            
        } catch (DataAccessException e) {
            logger.warn("Could not remove duplicate records: {}", e.getMessage());
        }
    }
    
    @Override
    @Transactional
    public void clearAll() {
        String sql = "DELETE FROM OM.TEMP_MIGRATION_AUDIT";
        
        try {
            int deletedRows = jdbcTemplate.update(sql);
            logger.info("Cleared {} rows from audit table", deletedRows);
        } catch (DataAccessException e) {
            logger.error("Failed to clear audit table, error: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    @Override
    public ValidationStatistics getValidationStatistics() {
        String sql = """
            SELECT 
                COUNT(*) as total_validated,
                SUM(CASE WHEN V1 = 'Y' THEN 1 ELSE 0 END) as sr1_passed,
                SUM(CASE WHEN V2 = 'Y' THEN 1 ELSE 0 END) as sr2_passed,
                SUM(CASE WHEN V3 = 'Y' THEN 1 ELSE 0 END) as sr4_passed,
                SUM(CASE WHEN V4 = 'Y' THEN 1 ELSE 0 END) as sr5_passed,
                SUM(CASE WHEN V5 = 'Y' THEN 1 ELSE 0 END) as sr6_passed
            FROM OM.TEMP_MIGRATION_AUDIT
            """;
        
        try {
            return jdbcTemplate.queryForObject(sql, (rs, rowNum) -> 
                new ValidationStatistics(
                    rs.getLong("total_validated"),
                    rs.getLong("sr1_passed"),
                    rs.getLong("sr2_passed"),
                    rs.getLong("sr4_passed"),
                    rs.getLong("sr5_passed"),
                    rs.getLong("sr6_passed")
                )
            );
        } catch (EmptyResultDataAccessException e) {
            return new ValidationStatistics(0, 0, 0, 0, 0, 0);
        } catch (DataAccessException e) {
            logger.error("Failed to get validation statistics, error: {}", e.getMessage(), e);
            return new ValidationStatistics(0, 0, 0, 0, 0, 0);
        }
    }
}