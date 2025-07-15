# Oracle Data Migration Validator - Deployment Troubleshooting Guide

## Common Deployment Issues and Solutions

### 1. Database Connection Issues

#### Error: "Unable to determine Dialect without JDBC metadata"

**Cause**: Hibernate cannot connect to the database to determine the dialect automatically.

**Solutions**:

1. **Check Environment Variables**:
   ```bash
   echo "DB_HOST: $DB_HOST"
   echo "DB_PORT: $DB_PORT"
   echo "DB_SID: $DB_SID"
   echo "DB_USERNAME: $DB_USERNAME"
   echo "DB_PASSWORD: [hidden]"
   ```

2. **Set Environment Variables**:
   ```bash
   export DB_HOST=your_database_host
   export DB_PORT=1521
   export DB_SID=your_database_sid
   export DB_USERNAME=your_username
   export DB_PASSWORD=your_password
   ```

3. **Test Database Connection**:
   ```bash
   # Using sqlplus (if available)
   sqlplus $DB_USERNAME/$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_SID
   
   # Using telnet to test connectivity
   telnet $DB_HOST $DB_PORT
   ```

4. **Use Production Configuration**:
   ```bash
   java -jar target/oracle-data-migration-validator-1.0.0.jar --spring.profiles.active=prod
   ```

#### Error: "ORA-12514: TNS:listener does not currently know of service requested"

**Cause**: Database service name (SID) is incorrect or database is not running.

**Solutions**:
1. Verify database SID/service name
2. Check if database is running
3. Verify TNS listener is running
4. Check Oracle Net configuration

#### Error: "ORA-01017: invalid username/password"

**Cause**: Incorrect database credentials.

**Solutions**:
1. Verify username and password
2. Check if user account is locked
3. Ensure user has necessary privileges

### 2. Application Startup Issues

#### Error: "Address already in use: bind"

**Cause**: Port 8080 is already in use.

**Solutions**:
1. Change port:
   ```bash
   java -jar target/oracle-data-migration-validator-1.0.0.jar --server.port=8081
   ```

2. Kill existing process:
   ```bash
   lsof -i :8080
   kill -9 <PID>
   ```

#### Error: "java.lang.OutOfMemoryError: Java heap space"

**Cause**: Insufficient heap memory.

**Solutions**:
1. Increase heap size:
   ```bash
   export JAVA_OPTS="-Xms2g -Xmx8g"
   java $JAVA_OPTS -jar target/oracle-data-migration-validator-1.0.0.jar
   ```

2. Use G1 garbage collector:
   ```bash
   export JAVA_OPTS="-Xms2g -Xmx8g -XX:+UseG1GC"
   ```

### 3. Virtual Thread Issues

#### Error: Virtual threads not supported

**Cause**: Java version is below 21.

**Solutions**:
1. Upgrade to Java 21 or higher
2. Verify Java version:
   ```bash
   java -version
   ```

3. Enable virtual threads explicitly:
   ```bash
   java -XX:+EnableVirtualThreads -jar target/oracle-data-migration-validator-1.0.0.jar
   ```

### 4. Permission Issues

#### Error: "Permission denied"

**Cause**: Insufficient file system permissions.

**Solutions**:
1. Create necessary directories:
   ```bash
   mkdir -p logs temp
   chmod 755 logs temp
   ```

2. Set proper ownership:
   ```bash
   chown -R oracle:oracle /opt/oracle-migration-validator
   ```

### 5. Oracle Driver Issues

#### Error: "ClassNotFoundException: oracle.jdbc.OracleDriver"

**Cause**: Oracle JDBC driver not found in classpath.

**Solutions**:
1. Ensure Oracle JDBC driver is included in the JAR (Maven dependency)
2. Check pom.xml for Oracle dependency:
   ```xml
   <dependency>
       <groupId>com.oracle.database.jdbc</groupId>
       <artifactId>ojdbc11</artifactId>
       <version>21.9.0.0</version>
   </dependency>
   ```

### 6. Network and Firewall Issues

#### Error: "Connection timed out"

**Cause**: Network connectivity issues or firewall blocking connection.

**Solutions**:
1. Check network connectivity:
   ```bash
   ping $DB_HOST
   telnet $DB_HOST $DB_PORT
   ```

2. Check firewall rules:
   ```bash
   # On CentOS/RHEL
   firewall-cmd --list-all
   
   # On Ubuntu
   ufw status
   ```

3. Configure firewall to allow Oracle port:
   ```bash
   # CentOS/RHEL
   firewall-cmd --add-port=1521/tcp --permanent
   firewall-cmd --reload
   
   # Ubuntu
   ufw allow 1521/tcp
   ```

## Production Deployment Checklist

### Pre-Deployment
- [ ] Java 21+ is installed
- [ ] Database is accessible from server
- [ ] Required environment variables are set
- [ ] Application JAR file is built
- [ ] Log directory is created with proper permissions
- [ ] Database schemas exist (ATDM, OM)
- [ ] Database user has required privileges

### Deployment Steps
1. **Build Application**:
   ```bash
   mvn clean package -DskipTests
   ```

2. **Set Environment Variables**:
   ```bash
   export DB_HOST=your_db_host
   export DB_PORT=1521
   export DB_SID=your_db_sid
   export DB_USERNAME=your_username
   export DB_PASSWORD=your_password
   export SPRING_PROFILES_ACTIVE=prod
   ```

3. **Run Deployment Script**:
   ```bash
   ./deploy-production.sh
   ```

4. **Start Application**:
   ```bash
   ./start-app.sh
   ```

### Post-Deployment Verification
- [ ] Application starts without errors
- [ ] Database connection is successful
- [ ] Health check endpoint responds: `http://localhost:8080/actuator/health`
- [ ] Logs are being written to logs/application.log
- [ ] Can start a validation job via API

## Environment-Specific Configurations

### Development Environment
```yaml
spring:
  profiles:
    active: dev
  datasource:
    url: jdbc:oracle:thin:@localhost:1521:XE
    username: system
    password: oracle
```

### Production Environment
```yaml
spring:
  profiles:
    active: prod
  datasource:
    url: jdbc:oracle:thin:@${DB_HOST}:${DB_PORT}:${DB_SID}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

## Monitoring and Logging

### Application Logs
- Location: `logs/application.log`
- Log level: INFO (production), DEBUG (development)
- Rotation: 100MB max size, 30 days retention

### Health Monitoring
- Health check: `http://localhost:8080/actuator/health`
- Metrics: `http://localhost:8080/actuator/metrics`
- Batch info: `http://localhost:8080/actuator/batch`

### Database Monitoring
- Connection pool metrics available via JMX
- Database query logging (if enabled)
- Audit table for validation results

## Performance Tuning

### JVM Tuning
```bash
export JAVA_OPTS="-Xms2g -Xmx8g -XX:+UseG1GC -XX:+EnableVirtualThreads -XX:MaxGCPauseMillis=200"
```

### Database Tuning
- Connection pool size: 20 (default)
- Batch size: 1000 (configurable)
- Chunk size: 100 (configurable)

### Virtual Thread Configuration
```yaml
migration:
  validation:
    virtual-threads:
      enabled: true
      max-concurrent-validations: 100
```

## Emergency Procedures

### Application Won't Start
1. Check logs: `tail -f logs/application.log`
2. Verify database connectivity
3. Check environment variables
4. Restart with debug logging: `--logging.level.root=DEBUG`

### Database Connection Issues
1. Test connection outside application
2. Check database listener status
3. Verify network connectivity
4. Review database logs

### High Memory Usage
1. Monitor heap usage: `jstat -gc <PID>`
2. Generate heap dump: `jmap -dump:live,format=b,file=heap.hprof <PID>`
3. Analyze with tools like Eclipse MAT

### Application Hanging
1. Generate thread dump: `jstack <PID>`
2. Check for deadlocks
3. Review database locks
4. Check virtual thread status

## Support and Troubleshooting Commands

### Useful Commands
```bash
# Check application status
ps aux | grep oracle-migration-validator

# Monitor memory usage
top -p <PID>

# Check network connections
netstat -an | grep 8080

# View recent logs
tail -f logs/application.log

# Search for errors
grep -i error logs/application.log

# Check database connections
lsof -i :1521

# Monitor Java processes
jps -v
```

### Log Analysis
```bash
# Count error messages
grep -c "ERROR" logs/application.log

# Find specific errors
grep -A 5 -B 5 "ORA-" logs/application.log

# Monitor real-time logs
tail -f logs/application.log | grep -E "(ERROR|WARN)"
```

## Contact Information

For additional support:
- Check Oracle documentation
- Review Spring Boot documentation
- Consult database administrator
- Check application logs for detailed error messages