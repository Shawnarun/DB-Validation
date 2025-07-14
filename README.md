# Oracle Data Migration Validation Tool

A comprehensive Spring Boot application for validating data migration from ATDM schema to OM schema in Oracle Database. The tool performs business rule validations and maintains audit logs for tracking validation progress.

## Features

- **Efficient Batch Processing**: Uses Spring Batch with virtual threads for high-performance validation
- **Business Rule Validation**: Implements 4 predefined validation scenarios (SR1, SR4, SR5, SR6)
- **Idempotent Operations**: Skips already validated records to support resume functionality
- **Progress Tracking**: Real-time progress monitoring and statistics
- **REST API**: Web endpoints for job management and monitoring
- **Audit Logging**: Comprehensive validation results stored in temporary audit table
- **Fault Tolerance**: Skip limits and error handling for production robustness

## Technology Stack

- **Java 21** with Virtual Threads
- **Spring Boot 3.2**
- **Spring Batch** for job processing
- **Oracle JDBC** for database connectivity
- **Logback/SLF4J** for logging
- **Maven** for dependency management

## Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   REST API      │    │  Spring Batch    │    │  Oracle DB      │
│   Controller    │────│  Job Processor   │────│  ATDM/OM/CCBS2  │
└─────────────────┘    └──────────────────┘    └─────────────────┘
         │                       │                       │
         │              ┌──────────────────┐             │
         └──────────────│  Validation      │─────────────┘
                        │  Service Layer   │
                        └──────────────────┘
```

## Validation Scenarios

### SR1: Airtel Number Validation via CAM Core Tables
Validates mobile numbers against CAM subscriber profile, contracts, and connection tables.

### SR4: Phone Number Register Status Check
Verifies `STATUS = 'A'` and `BLOCKED_STATUS = 'U'` in `PHONE_NO_REGISTER`.

### SR5: SIM + IMSI Pair Validation
Ensures SIM+IMSI pairs in `DYN_1_CONNECTION` match `IMSI_SIM_REGISTER`.

### SR6: IMSI Existence in Provisioning Switch
Checks if IMSI exists in `PROV_SWITCH_IMAGE` table.

## Database Schema

### Source Data
```sql
-- Mobile numbers to validate
SELECT DISTINCT MOBILE_NO FROM ATDM.AT_DYN_1_CONNECTION_MIG;
```

### Audit Table
```sql
CREATE TABLE OM.TEMP_MIGRATION_AUDIT (
    MOBILE_NO     VARCHAR2(20),
    V1            VARCHAR2(10),  -- SR1 result
    V2            VARCHAR2(10),  -- SR2 result (reserved)
    V3            VARCHAR2(10),  -- SR4 result
    V4            VARCHAR2(10),  -- SR5 result
    V5            VARCHAR2(10),  -- SR6 result
    CREATED_DATE  DATE DEFAULT SYSDATE
);
```

## Configuration

### Database Connection
```yaml
spring:
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:XE
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: oracle.jdbc.OracleDriver
```

### Batch Processing
```yaml
migration:
  validation:
    batch-size: 1000      # Records per batch
    chunk-size: 100       # Records per chunk
    thread-pool-size: 4   # Number of processing threads
    skip-limit: 10        # Max errors before job failure
    restart-on-error: true
```

## Quick Start

### Prerequisites
- Java 21+
- Oracle Database with ATDM, OM, and CCBS2 schemas
- Maven 3.6+

### Build and Run
```bash
# Clone the repository
git clone <repository-url>
cd oracle-data-migration-validator

# Build the application
mvn clean package

# Run with custom configuration
java -jar target/data-migration-validator-1.0.0.jar \
  --DB_HOST=oracle-server \
  --DB_USERNAME=migration_user \
  --DB_PASSWORD=secure_password
```

### Docker Deployment
```bash
# Build Docker image
docker build -t migration-validator .

# Run with environment variables
docker run -d \
  -e DB_HOST=oracle-server \
  -e DB_USERNAME=migration_user \
  -e DB_PASSWORD=secure_password \
  -p 8080:8080 \
  migration-validator
```

## API Usage

### Start Validation Job
```bash
curl -X POST http://localhost:8080/api/validation/start?restart=false
```

### Check Progress
```bash
curl http://localhost:8080/api/validation/progress
```

### Get Statistics
```bash
curl http://localhost:8080/api/validation/statistics
```

### Validate Single Mobile Number
```bash
curl -X POST http://localhost:8080/api/validation/validate/9876543210
```

### Get Detailed Report
```bash
curl http://localhost:8080/api/validation/report
```

## Monitoring and Operations

### Health Check
```bash
curl http://localhost:8080/actuator/health
```

### Batch Job Monitoring
```bash
curl http://localhost:8080/actuator/batch
```

### Application Metrics
```bash
curl http://localhost:8080/actuator/metrics
```

## Performance Characteristics

- **Throughput**: ~1000-5000 records/minute (depends on database performance)
- **Memory Usage**: ~500MB-1GB heap for 1M records
- **Concurrent Processing**: 4-8 virtual threads recommended
- **Resume Capability**: Automatic restart from last processed record

## Error Handling

### Skip Policies
- Database connection errors: Retry 3 times
- Validation errors: Log and continue
- Skip limit: Configurable (default: 10 errors per job)

### Logging
- **DEBUG**: Individual record processing
- **INFO**: Batch progress and statistics
- **WARN**: Validation failures and retries
- **ERROR**: Job failures and critical errors

## Production Deployment

### Environment Variables
```bash
export DB_HOST=prod-oracle-server
export DB_PORT=1521
export DB_SID=PROD
export DB_USERNAME=migration_prod
export DB_PASSWORD=secure_prod_password
export JAVA_OPTS="-Xmx2g -XX:+UseG1GC"
```

### JVM Tuning
```bash
-Xmx2g -Xms1g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+EnableDynamicAgentLoading
--enable-preview
```

### Database Optimization
- Create indexes on `MOBILE_NO` columns
- Enable database connection pooling
- Monitor database locks and waits
- Consider partition tables for large datasets

## Troubleshooting

### Common Issues

1. **ORA-08177 Serialization Error**: See [Oracle Troubleshooting Guide](ORACLE_TROUBLESHOOTING.md)
2. **Connection Timeout**: Increase `connection-timeout` in datasource config
3. **Memory Issues**: Reduce `chunk-size` and increase heap size
4. **Slow Performance**: Check database indexes and network latency
5. **Job Won't Start**: Verify Spring Batch metadata tables exist

### Oracle-Specific Issues
For detailed Oracle database troubleshooting, including serialization errors, deadlocks, and performance issues, see the comprehensive [Oracle Troubleshooting Guide](ORACLE_TROUBLESHOOTING.md).

### Debug Mode
```bash
java -jar app.jar --logging.level.com.oracle.migration=DEBUG
```

## Contributing

1. Fork the repository
2. Create a feature branch
3. Add tests for new functionality
4. Ensure all tests pass
5. Submit a pull request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
- Create GitHub issues for bugs
- Check logs in `logs/migration-validator.log`
- Monitor application via Actuator endpoints