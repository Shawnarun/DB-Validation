#!/bin/bash

# Oracle Fixes Verification Script
# This script verifies that all Oracle serialization fixes have been properly applied

set -e

echo "=================================================="
echo "Oracle Data Migration Validator - Fixes Verification"
echo "=================================================="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Counters
CHECKS_PASSED=0
CHECKS_FAILED=0
TOTAL_CHECKS=0

# Function to print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[✓ PASS]${NC} $1"
    ((CHECKS_PASSED++))
}

print_fail() {
    echo -e "${RED}[✗ FAIL]${NC} $1"
    ((CHECKS_FAILED++))
}

print_warning() {
    echo -e "${YELLOW}[⚠ WARN]${NC} $1"
}

# Function to check if a file exists and contains specific content
check_file_content() {
    local file="$1"
    local pattern="$2"
    local description="$3"
    
    ((TOTAL_CHECKS++))
    
    if [ ! -f "$file" ]; then
        print_fail "$description - File $file not found"
        return 1
    fi
    
    if grep -q "$pattern" "$file"; then
        print_success "$description"
        return 0
    else
        print_fail "$description - Pattern not found in $file"
        return 1
    fi
}

# Function to check if dependency exists in pom.xml
check_dependency() {
    local artifactId="$1"
    local description="$2"
    
    check_file_content "pom.xml" "$artifactId" "$description"
}

print_info "Starting verification of Oracle serialization fixes..."
echo

# Check 1: Spring Retry dependency
print_info "1. Checking dependencies..."
check_dependency "spring-retry" "Spring Retry dependency added"
check_dependency "spring-aspects" "Spring Aspects dependency added"
echo

# Check 2: Database configuration
print_info "2. Checking database configuration..."
check_file_content "src/main/resources/application.yml" "defaultTransactionIsolation: 2" "READ_COMMITTED isolation level configured"
check_file_content "src/main/resources/application.yml" "isolation-level-for-create: read_committed" "Batch isolation level configured"
check_file_content "src/main/resources/application.yml" "oracle.jdbc.autoCommitSpecCompliant: false" "Oracle JDBC properties configured"
echo

# Check 3: Retry annotations
print_info "3. Checking retry logic..."
check_file_content "src/main/java/com/oracle/migration/validator/service/ValidationJobLauncher.java" "@Retryable" "Retry annotations present"
check_file_content "src/main/java/com/oracle/migration/validator/service/ValidationJobLauncher.java" "DataAccessException.class" "DataAccessException retry configured"
check_file_content "src/main/java/com/oracle/migration/validator/service/ValidationJobLauncher.java" "isSerializationError" "Oracle error detection method present"
echo

# Check 4: Transaction management
print_info "4. Checking transaction configuration..."
check_file_content "src/main/java/com/oracle/migration/validator/config/DatabaseConfiguration.java" "@EnableRetry" "Retry enabled in configuration"
check_file_content "src/main/java/com/oracle/migration/validator/config/DatabaseConfiguration.java" "DataSourceTransactionManager" "Transaction manager configured"
echo

# Check 5: Batch configuration
print_info "5. Checking batch job configuration..."
check_file_content "src/main/java/com/oracle/migration/validator/batch/ValidationJobConfiguration.java" "retryLimit(3)" "Batch retry limit configured"
check_file_content "src/main/java/com/oracle/migration/validator/batch/ValidationJobConfiguration.java" "throttleLimit(1)" "Batch throttle limit configured"
check_file_content "src/main/java/com/oracle/migration/validator/batch/ValidationJobConfiguration.java" "ExponentialBackOffPolicy" "Exponential backoff configured"
echo

# Check 6: Unique job parameters
print_info "6. Checking unique job parameters..."
check_file_content "src/main/java/com/oracle/migration/validator/service/ValidationJobLauncher.java" "UUID.randomUUID()" "UUID generation for unique parameters"
check_file_content "src/main/java/com/oracle/migration/validator/service/ValidationJobLauncher.java" "createUniqueJobParameters" "Unique job parameters method present"
echo

# Check 7: Documentation
print_info "7. Checking documentation..."
check_file_content "ORACLE_TROUBLESHOOTING.md" "ORA-08177" "Oracle troubleshooting guide exists"
check_file_content "README.md" "Oracle Troubleshooting Guide" "README references Oracle guide"
echo

# Check 8: Error handling
print_info "8. Checking error handling..."
check_file_content "src/main/java/com/oracle/migration/validator/service/ValidationJobLauncher.java" "ORA-08177" "ORA-08177 error detection"
check_file_content "src/main/java/com/oracle/migration/validator/service/ValidationJobLauncher.java" "ORA-00060" "ORA-00060 error detection"
check_file_content "src/main/java/com/oracle/migration/validator/service/ValidationJobLauncher.java" "ORA-00054" "ORA-00054 error detection"
echo

# Check 9: Test coverage
print_info "9. Checking test coverage..."
check_file_content "src/test/java/com/oracle/migration/validator/service/ValidationJobLauncherTest.java" "testSerializationErrorDetection" "Serialization error test present"
check_file_content "src/test/java/com/oracle/migration/validator/service/ValidationJobLauncherTest.java" "testUniqueJobParametersGeneration" "Unique parameters test present"
echo

# Check 10: Configuration values
print_info "10. Checking configuration values..."
check_file_content "src/main/resources/application.yml" "max-retry-attempts: 3" "Max retry attempts configured"
check_file_content "src/main/resources/application.yml" "retry-backoff-delay: 2000" "Retry backoff delay configured"
echo

# Summary
echo "=================================================="
echo "VERIFICATION SUMMARY"
echo "=================================================="
echo "Total checks: $TOTAL_CHECKS"
echo -e "Passed: ${GREEN}$CHECKS_PASSED${NC}"
echo -e "Failed: ${RED}$CHECKS_FAILED${NC}"

if [ $CHECKS_FAILED -eq 0 ]; then
    echo
    print_success "All Oracle serialization fixes have been properly applied!"
    echo
    echo "Next steps:"
    echo "1. Build the application: mvn clean package"
    echo "2. Test with Oracle database"
    echo "3. Monitor for ORA-08177 errors in logs"
    echo "4. Refer to ORACLE_TROUBLESHOOTING.md if issues persist"
    exit 0
else
    echo
    print_fail "Some fixes are missing or incomplete. Please review the failed checks."
    echo
    echo "Required actions:"
    echo "1. Review the failed checks above"
    echo "2. Apply missing fixes as described in ORACLE_FIXES_SUMMARY.md"
    echo "3. Re-run this verification script"
    exit 1
fi