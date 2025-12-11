#!/bin/bash
# Script per pulire file compilati e temporanei

echo "🧹 Pulizia file compilati e temporanei..."

# Rimuovi file .class
echo "  - Rimozione file .class..."
find . -name "*.class" -type f -delete

# Rimuovi directory temporanee data_temp_*
echo "  - Rimozione directory data_temp_*..."
rm -rf ../data/data_temp_*

# Rimuovi file temporanei Python
echo "  - Rimozione cache Python..."
find . -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null
find . -name "*.pyc" -type f -delete 2>/dev/null

# Opzionale: rimuovi risultati (decommenta se vuoi)
# echo "  - Rimozione risultati..."
# rm -f results/experiments_results.json
# rm -f results/experiments_results.csv
# rm -f results/figures/*.png
# rm -f results/figures/*.pdf
# rm -f assets/*.png
# rm -f assets/*.pdf

echo "✅ Pulizia completata!"
