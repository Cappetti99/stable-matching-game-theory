#!/bin/bash
# Script to clean compiled and generated files

echo "Cleaning compiled and generated files..."

# Remove .class files
echo "  - Removing .class files..."
find . -name "*.class" -type f -delete

# Remove generated CSV directories under data/
echo "  - Removing generated CSV folders..."
rm -rf ../data/cybershake_*
rm -rf ../data/epigenomics_*
rm -rf ../data/ligo_*
rm -rf ../data/montage_*

# Remove Python caches
echo "  - Removing Python caches..."
find . -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null
find . -name "*.pyc" -type f -delete 2>/dev/null

# Optional: remove results (uncomment if you want)
# echo "  - Removing results..."
# rm -f results/experiments_results.json
# rm -f results/experiments_results.csv
# rm -f results/figures/*.png
# rm -f results/figures/*.pdf
# rm -f assets/*.png
# rm -f assets/*.pdf

echo "Done."
