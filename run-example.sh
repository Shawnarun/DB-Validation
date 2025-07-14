#!/bin/bash

# Oracle Data Migration Validator - Usage Examples
# This script demonstrates how to use the migration validator tool

set -e

echo "=================================================="
echo "Oracle Data Migration Validator - Usage Examples"
echo "=================================================="

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

BASE_URL="${BASE_URL:-http://localhost:8080}"

# Function to print colored output
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Function to check if application is running
check_health() {
    print_info "Checking application health..."
    
    if curl -s -f "$BASE_URL/actuator/health" > /dev/null; then
        print_success "Application is running and healthy"
        return 0
    else
        print_error "Application is not running or unhealthy"
        return 1
    fi
}

# Function to get validation status
get_status() {
    print_info "Getting validation status..."
    
    response=$(curl -s "$BASE_URL/api/validation/status")
    echo "$response" | jq '.' 2>/dev/null || echo "$response"
}

# Function to get validation progress
get_progress() {
    print_info "Getting validation progress..."
    
    response=$(curl -s "$BASE_URL/api/validation/progress")
    echo "$response" | jq '.' 2>/dev/null || echo "$response"
}

# Function to get validation statistics
get_statistics() {
    print_info "Getting validation statistics..."
    
    response=$(curl -s "$BASE_URL/api/validation/statistics")
    echo "$response" | jq '.' 2>/dev/null || echo "$response"
}

# Function to get validation report
get_report() {
    print_info "Getting validation report..."
    
    curl -s "$BASE_URL/api/validation/report"
}

# Function to start validation job
start_validation() {
    local restart=${1:-false}
    print_info "Starting validation job (restart=$restart)..."
    
    response=$(curl -s -X POST "$BASE_URL/api/validation/start?restart=$restart")
    echo "$response"
}

# Function to validate single mobile number
validate_single() {
    local mobile_no=$1
    
    if [ -z "$mobile_no" ]; then
        print_error "Mobile number is required"
        return 1
    fi
    
    print_info "Validating single mobile number: $mobile_no"
    
    response=$(curl -s -X POST "$BASE_URL/api/validation/validate/$mobile_no")
    echo "$response"
}

# Function to monitor validation progress
monitor_progress() {
    print_info "Monitoring validation progress (press Ctrl+C to stop)..."
    
    while true; do
        clear
        echo "=================================================="
        echo "Oracle Data Migration Validator - Live Monitoring"
        echo "=================================================="
        echo
        
        # Get current status
        status=$(curl -s "$BASE_URL/api/validation/status")
        progress=$(echo "$status" | jq -r '.progressPercentage // "N/A"' 2>/dev/null || echo "N/A")
        validated=$(echo "$status" | jq -r '.validatedCount // "N/A"' 2>/dev/null || echo "N/A")
        total=$(echo "$status" | jq -r '.totalCount // "N/A"' 2>/dev/null || echo "N/A")
        is_complete=$(echo "$status" | jq -r '.isComplete // false' 2>/dev/null || echo "false")
        
        echo "Progress: $progress%"
        echo "Validated: $validated / $total"
        echo "Complete: $is_complete"
        echo
        
        # Progress bar
        if [ "$progress" != "N/A" ]; then
            progress_int=$(echo "$progress" | cut -d'.' -f1)
            filled=$((progress_int / 2))
            empty=$((50 - filled))
            
            printf "["
            printf "%*s" $filled | tr ' ' '='
            printf "%*s" $empty | tr ' ' '-'
            printf "] %s%%\n" "$progress"
        fi
        
        echo
        echo "Last updated: $(date)"
        
        if [ "$is_complete" = "true" ]; then
            print_success "Validation completed!"
            break
        fi
        
        sleep 5
    done
}

# Function to run demo
run_demo() {
    print_info "Running demonstration..."
    
    echo
    print_info "1. Checking application health"
    if ! check_health; then
        print_error "Please start the application first"
        return 1
    fi
    
    echo
    print_info "2. Getting initial status"
    get_status
    
    echo
    print_info "3. Testing single mobile number validation"
    validate_single "9876543210"
    
    echo
    print_info "4. Getting validation statistics"
    get_statistics
    
    echo
    print_info "5. Starting full validation job"
    start_validation false
    
    echo
    print_info "6. Getting final report"
    sleep 2
    get_report
}

# Function to show usage
show_usage() {
    echo "Usage: $0 [COMMAND]"
    echo
    echo "Commands:"
    echo "  health              Check application health"
    echo "  status              Get validation status"
    echo "  progress            Get validation progress"
    echo "  statistics          Get validation statistics"
    echo "  report              Get detailed validation report"
    echo "  start [restart]     Start validation job (restart=true/false)"
    echo "  validate <mobile>   Validate single mobile number"
    echo "  monitor             Monitor validation progress in real-time"
    echo "  demo                Run complete demonstration"
    echo "  help                Show this help message"
    echo
    echo "Environment Variables:"
    echo "  BASE_URL            Application base URL (default: http://localhost:8080)"
    echo
    echo "Examples:"
    echo "  $0 health"
    echo "  $0 start false"
    echo "  $0 validate 9876543210"
    echo "  $0 monitor"
    echo "  BASE_URL=http://prod-server:8080 $0 status"
}

# Main script logic
case "${1:-help}" in
    "health")
        check_health
        ;;
    "status")
        get_status
        ;;
    "progress")
        get_progress
        ;;
    "statistics")
        get_statistics
        ;;
    "report")
        get_report
        ;;
    "start")
        start_validation "${2:-false}"
        ;;
    "validate")
        validate_single "$2"
        ;;
    "monitor")
        monitor_progress
        ;;
    "demo")
        run_demo
        ;;
    "help"|*)
        show_usage
        ;;
esac