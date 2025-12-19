#!/bin/bash

################################################################################
#                     AUTOMATED TEST SUITE RUNNER                              #
################################################################################
# This script compiles and runs all test files in the algorithms directory
# and generates a comprehensive test report.
#
# Usage:
#   ./run_all_tests.sh                    # Run all tests
#   ./run_all_tests.sh --verbose          # Run with verbose output
#   ./run_all_tests.sh --fast             # Skip large-scale tests
################################################################################

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color
BOLD='\033[1m'

# Configuration
VERBOSE=false
FAST_MODE=false
TEST_DIR="."
RESULTS_DIR="../results/test_reports"

# Create results directory if it doesn't exist
mkdir -p "$RESULTS_DIR"

REPORT_FILE="$RESULTS_DIR/TEST_REPORT_$(date +%Y%m%d_%H%M%S).txt"

# Parse arguments
for arg in "$@"; do
    case $arg in
        --verbose|-v)
            VERBOSE=true
            shift
            ;;
        --fast|-f)
            FAST_MODE=true
            shift
            ;;
        --help|-h)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --verbose, -v    Show detailed test output"
            echo "  --fast, -f       Skip large-scale tests (faster execution)"
            echo "  --help, -h       Show this help message"
            exit 0
            ;;
    esac
done

# Test tracking
TOTAL_TESTS=0
PASSED_TESTS=0
FAILED_TESTS=0
declare -a FAILED_TEST_NAMES

# Start time
START_TIME=$(date +%s)

echo -e "${BOLD}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║          AUTOMATED TEST SUITE RUNNER                         ║${NC}"
echo -e "${BOLD}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${CYAN}Date:${NC} $(date)"
echo -e "${CYAN}Mode:${NC} $([ "$FAST_MODE" = true ] && echo "FAST (skipping large-scale tests)" || echo "FULL")"
echo -e "${CYAN}Verbose:${NC} $VERBOSE"
echo ""

# Initialize report
{
    echo "═══════════════════════════════════════════════════════════════"
    echo "           AUTOMATED TEST SUITE REPORT"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
    echo "Date: $(date)"
    echo "Mode: $([ "$FAST_MODE" = true ] && echo "FAST" || echo "FULL")"
    echo ""
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
} > "$REPORT_FILE"

################################################################################
# Helper Functions
################################################################################

run_test() {
    local test_file=$1
    local test_name=$(basename "$test_file" .java)
    
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    echo -e "${BOLD}Running: ${CYAN}$test_name${NC}"
    echo -e "${BLUE}━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━${NC}"
    
    TOTAL_TESTS=$((TOTAL_TESTS + 1))
    
    # Compile
    echo -e "${YELLOW}⚙  Compiling...${NC}"
    if ! javac "$test_file" 2>&1 | tee -a "$REPORT_FILE"; then
        echo -e "${RED}❌ COMPILATION FAILED${NC}"
        echo "" | tee -a "$REPORT_FILE"
        echo "RESULT: COMPILATION FAILED" >> "$REPORT_FILE"
        echo "" >> "$REPORT_FILE"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        FAILED_TEST_NAMES+=("$test_name (compilation)")
        return 1
    fi
    
    # Run test
    echo -e "${YELLOW}🚀 Executing...${NC}"
    TEST_START=$(date +%s)
    
    {
        echo "───────────────────────────────────────────────────────────────"
        echo "Test: $test_name"
        echo "───────────────────────────────────────────────────────────────"
    } >> "$REPORT_FILE"
    
    if [ "$VERBOSE" = true ]; then
        if java "$test_name" 2>&1 | tee -a "$REPORT_FILE"; then
            TEST_RESULT=$?
        else
            TEST_RESULT=$?
        fi
    else
        if java "$test_name" >> "$REPORT_FILE" 2>&1; then
            TEST_RESULT=0
        else
            TEST_RESULT=$?
        fi
    fi
    
    TEST_END=$(date +%s)
    TEST_DURATION=$((TEST_END - TEST_START))
    
    echo "" >> "$REPORT_FILE"
    echo "Duration: ${TEST_DURATION}s" >> "$REPORT_FILE"
    
    # Check result
    if [ $TEST_RESULT -eq 0 ]; then
        echo -e "${GREEN}✅ PASSED${NC} (${TEST_DURATION}s)"
        echo "RESULT: PASSED" >> "$REPORT_FILE"
        PASSED_TESTS=$((PASSED_TESTS + 1))
    else
        echo -e "${RED}❌ FAILED${NC} (exit code: $TEST_RESULT)"
        echo "RESULT: FAILED (exit code: $TEST_RESULT)" >> "$REPORT_FILE"
        FAILED_TESTS=$((FAILED_TESTS + 1))
        FAILED_TEST_NAMES+=("$test_name")
    fi
    
    echo "" >> "$REPORT_FILE"
    echo ""
}

################################################################################
# Main Test Execution
################################################################################

echo -e "${BOLD}${MAGENTA}PHASE 1: Individual Algorithm Tests${NC}"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# DCP Tests
if [ -f "TestDCP_Evil.java" ]; then
    run_test "TestDCP_Evil.java"
fi

if [ -f "TestDCP_Brutal.java" ]; then
    run_test "TestDCP_Brutal.java"
fi

# SMGT Tests
if [ -f "TestSMGT_Evil.java" ]; then
    run_test "TestSMGT_Evil.java"
fi

if [ -f "TestSMGT_Brutal.java" ]; then
    run_test "TestSMGT_Brutal.java"
fi

# LOTD Tests
if [ -f "TestLOTD_SuperEvil.java" ]; then
    run_test "TestLOTD_SuperEvil.java"
fi

if [ -f "TestLOTD_Brutal.java" ]; then
    run_test "TestLOTD_Brutal.java"
fi

echo ""
echo -e "${BOLD}${MAGENTA}PHASE 2: Integration Tests${NC}"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Integration Tests
if [ -f "Test_Apocalypse_AllSchedulers.java" ]; then
    run_test "Test_Apocalypse_AllSchedulers.java"
fi

# Ultimate Brutal Test
if [ -f "TestBrutal_UltimateStressTest.java" ]; then
    if [ "$FAST_MODE" = false ]; then
        run_test "TestBrutal_UltimateStressTest.java"
    else
        echo -e "${YELLOW}⏩ Skipping TestBrutal_UltimateStressTest.java (fast mode)${NC}"
        echo ""
    fi
fi

################################################################################
# Generate Summary Report
################################################################################

END_TIME=$(date +%s)
TOTAL_DURATION=$((END_TIME - START_TIME))

echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║                    FINAL REPORT                              ║${NC}"
echo -e "${BOLD}╚══════════════════════════════════════════════════════════════╝${NC}"
echo ""
echo -e "${CYAN}Total Tests:${NC}     $TOTAL_TESTS"
echo -e "${GREEN}Passed:${NC}          $PASSED_TESTS ($(awk "BEGIN {printf \"%.1f\", ($PASSED_TESTS/$TOTAL_TESTS)*100}")%)"
echo -e "${RED}Failed:${NC}          $FAILED_TESTS ($(awk "BEGIN {printf \"%.1f\", ($FAILED_TESTS/$TOTAL_TESTS)*100}")%)"
echo -e "${CYAN}Total Duration:${NC}  ${TOTAL_DURATION}s ($(awk "BEGIN {printf \"%.1f\", $TOTAL_DURATION/60}")m)"
echo ""

# Write summary to report
{
    echo "═══════════════════════════════════════════════════════════════"
    echo "                    SUMMARY"
    echo "═══════════════════════════════════════════════════════════════"
    echo ""
    echo "Total Tests:     $TOTAL_TESTS"
    echo "Passed:          $PASSED_TESTS ($(awk "BEGIN {printf \"%.1f\", ($PASSED_TESTS/$TOTAL_TESTS)*100}")%)"
    echo "Failed:          $FAILED_TESTS ($(awk "BEGIN {printf \"%.1f\", ($FAILED_TESTS/$TOTAL_TESTS)*100}")%)"
    echo "Total Duration:  ${TOTAL_DURATION}s"
    echo ""
} >> "$REPORT_FILE"

# List failed tests if any
if [ $FAILED_TESTS -gt 0 ]; then
    echo -e "${RED}Failed Tests:${NC}"
    for test in "${FAILED_TEST_NAMES[@]}"; do
        echo -e "  ${RED}❌${NC} $test"
        echo "  - $test" >> "$REPORT_FILE"
    done
    echo "" | tee -a "$REPORT_FILE"
fi

# Final status
if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}${BOLD}🎉 🎉 🎉  ALL TESTS PASSED!  🎉 🎉 🎉${NC}"
    echo "" | tee -a "$REPORT_FILE"
    echo "Status: ALL TESTS PASSED ✅" >> "$REPORT_FILE"
    EXIT_CODE=0
else
    echo -e "${RED}${BOLD}⚠️  SOME TESTS FAILED - Review output above${NC}"
    echo "" | tee -a "$REPORT_FILE"
    echo "Status: SOME TESTS FAILED ❌" >> "$REPORT_FILE"
    EXIT_CODE=1
fi

echo ""
echo -e "${CYAN}Report saved to:${NC} $REPORT_FILE"
echo ""

exit $EXIT_CODE
