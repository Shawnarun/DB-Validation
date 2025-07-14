# Oracle Database Troubleshooting Guide

This guide addresses common Oracle database issues encountered with the Migration Validation Tool.

## ORA-08177: Can't Serialize Access for This Transaction

### Problem Description
The error `ORA-08177: can't serialize access for this transaction` occurs when Oracle detects a serialization conflict between concurrent transactions. This is common in Spring Batch applications when multiple processes try to access the same batch metadata tables.

### Root Causes
1. **High Concurrency**: Multiple threads accessing batch metadata simultaneously
2. **Serializable Isolation**: Default Oracle isolation level causing conflicts
3. **Long-Running Transactions**: Transactions holding locks for extended periods
4. **Concurrent Job Launches**: Multiple validation jobs starting at the same time

### Solutions Applied

#### 1. Database Configuration Changes
```yaml
spring:
  datasource:
    hikari:
      data-source-properties:
        # Set READ_COMMITTED isolation level
        defaultTransactionIsolation: 2
        oracle.jdbc.autoCommitSpecCompliant: false
```

#### 2. Transaction Management
```java
@Transactional(isolation = Isolation.READ_COMMITTED)
@Retryable(
    retryFor = {DataAccessException.class},
    maxAttempts = 3,
    backoff = @Backoff(delay = 2000, multiplier = 2)
)
```

#### 3. Reduced Concurrency
```yaml
migration:
  validation:
    thread-pool-size: 2  # Reduced from 4
    chunk-size: 50       # Reduced from 100
```

#### 4. Unique Job Parameters
```java
JobParameters jobParameters = new JobParametersBuilder()
    .addString("startTime", LocalDateTime.now().toString())
    .addString("uniqueId", UUID.randomUUID().toString())
    .addLong("timestamp", System.currentTimeMillis())
    .toJobParameters();
```

### Immediate Fixes

#### Option 1: Restart with Delay
```bash
# Wait a few seconds and retry
sleep 5
curl -X POST http://localhost:8080/api/validation/start?restart=false
```

#### Option 2: Use Alternative Job Name
```bash
# Add timestamp to make job unique
curl -X POST "http://localhost:8080/api/validation/start?restart=false&timestamp=$(date +%s)"
```

#### Option 3: Clear Batch Metadata (Development Only)
```sql
-- WARNING: Only use in development environment
DELETE FROM BATCH_JOB_EXECUTION_CONTEXT;
DELETE FROM BATCH_JOB_EXECUTION_PARAMS;
DELETE FROM BATCH_JOB_EXECUTION;
DELETE FROM BATCH_JOB_INSTANCE;
DELETE FROM BATCH_STEP_EXECUTION_CONTEXT;
DELETE FROM BATCH_STEP_EXECUTION;
COMMIT;
```

### Prevention Strategies

#### 1. Database Tuning
```sql
-- Increase undo retention
ALTER SYSTEM SET undo_retention = 3600;

-- Monitor locks
SELECT * FROM V$LOCKED_OBJECT;
SELECT * FROM V$LOCK WHERE TYPE = 'TX';
```

#### 2. Connection Pool Settings
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10     # Reduced pool size
      minimum-idle: 2
      connection-timeout: 30000
      idle-timeout: 600000
```

#### 3. Application Settings
```yaml
migration:
  validation:
    max-retry-attempts: 3
    retry-backoff-delay: 2000
    thread-pool-size: 1      # Single thread for critical sections
```

### Monitoring and Detection

#### 1. Enable Debug Logging
```yaml
logging:
  level:
    org.springframework.transaction: DEBUG
    org.springframework.jdbc: DEBUG
    oracle.jdbc: DEBUG
```

#### 2. Monitor Oracle Sessions
```sql
-- Check active sessions
SELECT username, machine, program, status, sql_id
FROM v$session 
WHERE username IS NOT NULL;

-- Monitor serialization errors
SELECT * FROM v$sqlstats 
WHERE sql_text LIKE '%BATCH_JOB%' 
AND executions_total > 0;
```

#### 3. Application Metrics
```bash
# Check retry attempts
curl http://localhost:8080/actuator/metrics/spring.retry.attempts

# Monitor job executions
curl http://localhost:8080/actuator/batch
```

## Other Common Oracle Issues

### ORA-00060: Deadlock Detected

#### Solution
```yaml
spring:
  batch:
    job:
      execution:
        timeout: 300000  # 5 minutes
```

### ORA-00054: Resource Busy

#### Solution
```java
@Retryable(
    retryFor = {DataAccessException.class},
    maxAttempts = 5,
    backoff = @Backoff(delay = 1000, multiplier = 1.5)
)
```

### Connection Pool Exhaustion

#### Solution
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      leak-detection-threshold: 60000
```

## Performance Optimization

### 1. Index Creation
```sql
-- Ensure proper indexes exist
CREATE INDEX IDX_BATCH_JOB_INST_NAME ON BATCH_JOB_INSTANCE(JOB_NAME);
CREATE INDEX IDX_BATCH_JOB_EXEC_STATUS ON BATCH_JOB_EXECUTION(STATUS);
```

### 2. Statistics Update
```sql
-- Update table statistics
BEGIN
  DBMS_STATS.GATHER_TABLE_STATS('MIGRATION_USER', 'BATCH_JOB_INSTANCE');
  DBMS_STATS.GATHER_TABLE_STATS('MIGRATION_USER', 'BATCH_JOB_EXECUTION');
END;
/
```

### 3. Parallel Processing
```yaml
migration:
  validation:
    chunk-size: 200          # Larger chunks
    thread-pool-size: 3      # Moderate parallelism
    batch-size: 2000         # Larger batches
```

## Emergency Procedures

### 1. Stop All Processing
```bash
# Graceful shutdown
curl -X POST http://localhost:8080/actuator/shutdown

# Force stop jobs
docker stop migration-validator
```

### 2. Clear Stuck Jobs
```sql
-- Mark failed jobs as abandoned
UPDATE BATCH_JOB_EXECUTION 
SET STATUS = 'ABANDONED', END_TIME = SYSDATE 
WHERE STATUS IN ('STARTED', 'STARTING');

COMMIT;
```

### 3. Restart Clean
```bash
# Remove containers
docker-compose down

# Clear volumes (if needed)
docker volume prune

# Restart
docker-compose up -d
```

## Contact and Support

### Log Analysis
```bash
# Check for serialization errors
grep -i "ORA-08177" logs/migration-validator.log

# Monitor retry patterns
grep -i "retry" logs/migration-validator.log | tail -20
```

### Database Health Check
```sql
-- Check tablespace usage
SELECT tablespace_name, 
       ROUND(used_percent, 2) as used_percent
FROM dba_tablespace_usage_metrics;

-- Check undo usage
SELECT tablespace_name, status, 
       ROUND(bytes/1024/1024, 2) as mb_used
FROM dba_undo_extents 
WHERE status = 'ACTIVE';
```

For additional support, please check the application logs and provide the specific error messages when reporting issues.