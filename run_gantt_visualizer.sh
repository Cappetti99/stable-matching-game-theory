#!/bin/bash

# GanttChartVisualizer - Run Script
# Usage: ./run_gantt_visualizer.sh <path_to_json_file>

# Colors for output
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Check if file path argument is provided
if [ -z "$1" ]; then
    echo -e "${RED}Error: No JSON file specified.${NC}"
    echo
    echo "Usage: ./run_gantt_visualizer.sh <path-to-json-file>"
    echo
    echo "Example:"
    echo "  ./run_gantt_visualizer.sh results/gantt_charts/ligo_50_tasks_5_vms_ccr1.8_run7.json"
    echo
    echo "Or use the example script to auto-select a file:"
    echo "  ./example_run_visualizer.sh"
    exit 1
fi

JSON_FILE="$1"

# Check if file exists
if [ ! -f "$JSON_FILE" ]; then
    echo -e "${RED}Error: File not found: $JSON_FILE${NC}"
    exit 1
fi

echo -e "${GREEN}========================================${NC}"
echo -e "${GREEN}  Gantt Chart Visualizer${NC}"
echo -e "${GREEN}========================================${NC}"
echo
echo -e "${GREEN}Loading: $JSON_FILE${NC}"
echo

# Check if we're in the algorithms directory
if [ ! -f "GanttChartVisualizer.java" ]; then
    echo -e "${YELLOW}Changing to algorithms directory...${NC}"
    cd algorithms 2>/dev/null || {
        echo -e "${RED}Error: Could not find algorithms directory${NC}"
        echo "Please run this script from the project root or algorithms directory"
        exit 1
    }
    # Adjust JSON file path
    JSON_FILE="../$JSON_FILE"
fi

# Check for Gson library
if [ ! -f "../lib/gson-2.10.1.jar" ]; then
    echo -e "${YELLOW}Gson library not found. Downloading...${NC}"
    mkdir -p ../lib
    curl -L -o ../lib/gson-2.10.1.jar https://repo1.maven.org/maven2/com/google/code/gson/gson/2.10.1/gson-2.10.1.jar
    if [ $? -ne 0 ]; then
        echo -e "${RED}Failed to download Gson library${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Gson library downloaded${NC}"
fi

# Compile if needed
if [ ! -f "GanttChartVisualizer.class" ] || [ "GanttChartVisualizer.java" -nt "GanttChartVisualizer.class" ]; then
    echo -e "${YELLOW}Compiling GanttChartVisualizer...${NC}"
    javac -cp "../lib/gson-2.10.1.jar" GanttChartVisualizer.java
    
    if [ $? -ne 0 ]; then
        echo -e "${RED}Compilation failed${NC}"
        exit 1
    fi
    echo -e "${GREEN}✓ Compilation successful${NC}"
else
    echo -e "${GREEN}✓ Using existing compiled class${NC}"
fi

# Run the visualizer
echo
echo -e "${GREEN}Starting Gantt Chart Visualizer...${NC}"
echo

java -cp "../lib/gson-2.10.1.jar:." GanttChartVisualizer "$JSON_FILE"
