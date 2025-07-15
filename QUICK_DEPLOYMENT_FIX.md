# Quick Fix for Deployment Issue

## Problem
The application fails to start with the error:
```
Unable to determine Dialect without JDBC metadata (please set 'jakarta.persistence.jdbc.url' for common cases or 'hibernate.dialect' when a custom Dialect implementation must be provided)
```

## Root Cause
The application cannot connect to the Oracle database, so Hibernate cannot determine the dialect automatically.

## Immediate Fix

### Step 1: Set Environment Variables
Before running the application, set the required database environment variables:

```bash
# Replace with your actual database details
export DB_HOST=your_database_host
export DB_PORT=1521
export DB_SID=your_database_sid_or_service_name
export DB_USERNAME=your_database_username
export DB_PASSWORD=your_database_password
```

### Step 2: Use Production Profile
Run the application with the production profile which includes explicit Hibernate dialect configuration:

```bash
java -jar target/oracle-data-migration-validator-1.0.0.jar --spring.profiles.active=prod
```

### Step 3: Alternative - Set Dialect Explicitly
If you still face issues, you can set the dialect explicitly:

```bash
java -jar target/oracle-data-migration-validator-1.0.0.jar \
  --spring.profiles.active=prod \
  --spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.OracleDialect
```

## Complete Example

```bash
# Set environment variables
export DB_HOST=192.168.1.100
export DB_PORT=1521
export DB_SID=ORCL
export DB_USERNAME=migration_user
export DB_PASSWORD=your_password

# Run the application
java -jar target/oracle-data-migration-validator-1.0.0.jar --spring.profiles.active=prod
```

## Using the Deployment Script

For easier deployment, use the provided deployment script:

```bash
# Make script executable
chmod +x deploy-production.sh

# Run deployment script (it will prompt for database details)
./deploy-production.sh
```

## Verification

1. Check that the application starts without errors
2. Verify database connection by visiting: `http://localhost:8080/actuator/health`
3. Check logs: `tail -f logs/application.log`

## Common Issues and Solutions

### Issue 1: Environment Variables Not Set
```bash
# Check if variables are set
echo "DB_HOST: $DB_HOST"
echo "DB_PORT: $DB_PORT"
echo "DB_SID: $DB_SID"
echo "DB_USERNAME: $DB_USERNAME"
```

### Issue 2: Database Not Accessible
```bash
# Test database connectivity
telnet $DB_HOST $DB_PORT

# Test with sqlplus (if available)
sqlplus $DB_USERNAME/$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_SID
```

### Issue 3: Port Already in Use
```bash
# Use different port
java -jar target/oracle-data-migration-validator-1.0.0.jar \
  --spring.profiles.active=prod \
  --server.port=8081
```

## Need More Help?

Refer to the comprehensive troubleshooting guide:
- `DEPLOYMENT_TROUBLESHOOTING.md` - Complete troubleshooting guide
- `ORACLE_TROUBLESHOOTING.md` - Oracle-specific issues
- `README.md` - General usage instructions