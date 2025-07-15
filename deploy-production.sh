#!/bin/bash

# Oracle Data Migration Validator - Production Deployment Script
# This script helps deploy the application in production environment

set -e

echo "===== Oracle Data Migration Validator - Production Deployment ====="

# Function to check if environment variable is set
check_env_var() {
    local var_name=$1
    local var_value=$(eval echo \$$var_name)
    if [ -z "$var_value" ]; then
        echo "ERROR: Environment variable $var_name is not set"
        return 1
    else
        echo "✓ $var_name is set"
        return 0
    fi
}

# Function to validate database connection
validate_db_connection() {
    echo "Validating database connection..."
    
    # Create a temporary SQL script to test connection
    cat > /tmp/test_connection.sql << EOF
SELECT 'Database connection successful' as status FROM DUAL;
EOF
    
    # Try to connect to database using sqlplus if available
    if command -v sqlplus &> /dev/null; then
        echo "Testing database connection with sqlplus..."
        if echo "exit" | sqlplus -S $DB_USERNAME/$DB_PASSWORD@$DB_HOST:$DB_PORT/$DB_SID @/tmp/test_connection.sql; then
            echo "✓ Database connection successful"
        else
            echo "✗ Database connection failed"
            return 1
        fi
    else
        echo "sqlplus not available, skipping database connection test"
    fi
    
    rm -f /tmp/test_connection.sql
}

# Function to setup environment variables
setup_environment() {
    echo "Setting up environment variables..."
    
    # Database configuration
    echo "Please provide database configuration:"
    
    if [ -z "$DB_HOST" ]; then
        read -p "Database Host (e.g., localhost): " DB_HOST
        export DB_HOST
    fi
    
    if [ -z "$DB_PORT" ]; then
        read -p "Database Port (default: 1521): " DB_PORT
        DB_PORT=${DB_PORT:-1521}
        export DB_PORT
    fi
    
    if [ -z "$DB_SID" ]; then
        read -p "Database SID (e.g., XE): " DB_SID
        export DB_SID
    fi
    
    if [ -z "$DB_USERNAME" ]; then
        read -p "Database Username: " DB_USERNAME
        export DB_USERNAME
    fi
    
    if [ -z "$DB_PASSWORD" ]; then
        read -s -p "Database Password: " DB_PASSWORD
        echo
        export DB_PASSWORD
    fi
    
    # Application configuration
    export SPRING_PROFILES_ACTIVE=prod
    export JAVA_OPTS="-Xms1g -Xmx4g -XX:+UseG1GC -XX:+EnableVirtualThreads"
    
    echo "Environment variables configured"
}

# Function to create necessary directories
create_directories() {
    echo "Creating necessary directories..."
    mkdir -p logs
    mkdir -p temp
    echo "✓ Directories created"
}

# Function to check prerequisites
check_prerequisites() {
    echo "Checking prerequisites..."
    
    # Check Java version
    if command -v java &> /dev/null; then
        JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}')
        echo "✓ Java version: $JAVA_VERSION"
        
        # Check if Java 21 or higher
        if java -version 2>&1 | grep -q "21\|22\|23\|24\|25"; then
            echo "✓ Java 21+ detected"
        else
            echo "⚠ Warning: Java 21 or higher is recommended for virtual threads"
        fi
    else
        echo "✗ Java not found"
        return 1
    fi
    
    # Check if JAR file exists
    if [ -f "target/oracle-data-migration-validator-1.0.0.jar" ]; then
        echo "✓ Application JAR file found"
    else
        echo "✗ Application JAR file not found. Please run 'mvn clean package' first"
        return 1
    fi
}

# Function to start the application
start_application() {
    echo "Starting Oracle Data Migration Validator..."
    
    # Set JVM options for production
    export JAVA_OPTS="-Xms1g -Xmx4g -XX:+UseG1GC -XX:+EnableVirtualThreads -Dspring.profiles.active=prod"
    
    # Create startup script
    cat > start-app.sh << 'EOF'
#!/bin/bash
export JAVA_OPTS="-Xms1g -Xmx4g -XX:+UseG1GC -XX:+EnableVirtualThreads"
export SPRING_PROFILES_ACTIVE=prod

java $JAVA_OPTS -jar target/oracle-data-migration-validator-1.0.0.jar \
    --spring.profiles.active=prod \
    --logging.file.name=logs/application.log \
    --server.port=8080
EOF
    
    chmod +x start-app.sh
    
    echo "Application startup script created: start-app.sh"
    echo "To start the application, run: ./start-app.sh"
}

# Function to create systemd service (optional)
create_systemd_service() {
    if [ "$EUID" -eq 0 ]; then
        echo "Creating systemd service..."
        
        cat > /etc/systemd/system/oracle-migration-validator.service << EOF
[Unit]
Description=Oracle Data Migration Validator
After=network.target

[Service]
Type=simple
User=oracle
Group=oracle
WorkingDirectory=/opt/oracle-migration-validator
ExecStart=/bin/bash /opt/oracle-migration-validator/start-app.sh
Restart=always
RestartSec=10
StandardOutput=journal
StandardError=journal

Environment=DB_HOST=$DB_HOST
Environment=DB_PORT=$DB_PORT
Environment=DB_SID=$DB_SID
Environment=DB_USERNAME=$DB_USERNAME
Environment=DB_PASSWORD=$DB_PASSWORD
Environment=SPRING_PROFILES_ACTIVE=prod

[Install]
WantedBy=multi-user.target
EOF
        
        systemctl daemon-reload
        echo "✓ Systemd service created"
        echo "To start service: systemctl start oracle-migration-validator"
        echo "To enable on boot: systemctl enable oracle-migration-validator"
    else
        echo "Skipping systemd service creation (requires root)"
    fi
}

# Main deployment flow
main() {
    echo "Starting deployment process..."
    
    # Check if environment variables are already set
    if check_env_var DB_HOST && check_env_var DB_PORT && check_env_var DB_SID && check_env_var DB_USERNAME && check_env_var DB_PASSWORD; then
        echo "✓ All environment variables are set"
    else
        echo "Setting up environment variables..."
        setup_environment
    fi
    
    # Check prerequisites
    if ! check_prerequisites; then
        echo "Prerequisites check failed. Please fix the issues and try again."
        exit 1
    fi
    
    # Create directories
    create_directories
    
    # Validate database connection
    validate_db_connection
    
    # Start application
    start_application
    
    # Offer to create systemd service
    read -p "Do you want to create a systemd service? (y/n): " create_service
    if [ "$create_service" = "y" ] || [ "$create_service" = "Y" ]; then
        create_systemd_service
    fi
    
    echo ""
    echo "===== Deployment Complete ====="
    echo "Application is ready to start!"
    echo ""
    echo "To start the application:"
    echo "  ./start-app.sh"
    echo ""
    echo "To check logs:"
    echo "  tail -f logs/application.log"
    echo ""
    echo "Web interface will be available at:"
    echo "  http://localhost:8080"
    echo ""
    echo "Health check endpoint:"
    echo "  http://localhost:8080/actuator/health"
    echo ""
    echo "============================="
}

# Run main function
main "$@"