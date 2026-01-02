#!/bin/bash

set -euo pipefail

# Always run from the repo root (independent of current working directory)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║     SM-CPTD PAPER EXPERIMENTS - Full Benchmark Suite          ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Go to the algorithms directory
cd "$REPO_ROOT/algorithms"

echo "Generating workflow data with the paper parameters..."
echo "   - Task sizes: [500, 700] uniform"
echo "   - VM capacities: [10, 20] uniform"
echo "   - Bandwidth: [20, 30] uniform"
echo ""

javac PegasusXMLParser.java
java PegasusXMLParser > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo "Error generating data."
    exit 1
fi

echo "Data generated successfully."
echo ""

echo "Compiling..."
javac Main.java 2>&1

if [ $? -ne 0 ]; then
    echo "Compilation error."
    exit 1
fi

echo "Compilation completed."
echo ""
echo "Running experiments..."
echo ""

# Entry point: Main -> ExperimentRunner
# You can pass parameters, e.g.:
#   ./run.sh --exp1
#   ./run.sh --exp2
#   ./run.sh --workflow=cybershake
#   ./run.sh --seed 123
java Main "$@"

exit_code=$?

echo ""
if [ $exit_code -eq 0 ]; then
    echo "Run completed successfully."
    echo ""
    echo "Outputs written to:"
    echo "   - results/experiments_results.csv"
    echo "   - results/experiments_results.json"
    echo "   - results/figures/ (optional; requires Python dependencies)"
else
    echo "Run finished with exit code $exit_code"
fi

exit $exit_code
