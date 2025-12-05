#!/bin/bash

# 🧹 SCRIPT DI PULIZIA PROGETTO
# =============================
# Rimuove file temporanei e superflui dal progetto
# 
# Autore: Lorenzo Cappetti
# Data: 25 luglio 2025

echo "🧹 PULIZIA PROGETTO STABLE MATCHING"
echo "==================================="

# Conta file prima della pulizia
echo "📊 Analizzando progetto..."
total_before=$(find . -type f | wc -l)
echo "File totali prima della pulizia: $total_before"

# Rimuovi file .class Java
echo ""
echo "🗑️  Rimuovendo file .class Java..."
class_files=$(find . -name "*.class" -type f)
if [ -n "$class_files" ]; then
    echo "$class_files" | while read file; do
        echo "   Rimuovendo: $file"
        rm "$file"
    done
    class_count=$(find . -name "*.class" -type f 2>/dev/null | wc -l)
    echo "✅ Rimossi file .class"
else
    echo "✅ Nessun file .class trovato"
fi

# Rimuovi file JSON di risultati duplicati (mantieni solo quelli specifici per workflow)
echo ""
echo "🗑️  Pulizia file JSON risultati..."
if [ -f "algorithms/ccr_analysis_results.json" ]; then
    echo "   Rimuovendo: algorithms/ccr_analysis_results.json (duplicato)"
    rm "algorithms/ccr_analysis_results.json"
fi

# Rimuovi file temporanei comuni
echo ""
echo "🗑️  Rimuovendo file temporanei..."
temp_patterns=(
    "*.tmp"
    "*.temp" 
    "*~"
    ".DS_Store"
    "*.log"
    "*.bak"
    "*.swp"
    "*.swo"
)

for pattern in "${temp_patterns[@]}"; do
    found_files=$(find . -name "$pattern" -type f 2>/dev/null)
    if [ -n "$found_files" ]; then
        echo "$found_files" | while read file; do
            echo "   Rimuovendo: $file"
            rm "$file"
        done
    fi
done

# Rimuovi cartelle vuote
echo ""
echo "🗑️  Rimuovendo cartelle vuote..."
find . -type d -empty -delete 2>/dev/null || true

# Statistiche finali
echo ""
echo "📊 Statistiche pulizia:"
total_after=$(find . -type f | wc -l)
removed=$((total_before - total_after))
echo "File totali dopo pulizia: $total_after"
echo "File rimossi: $removed"

echo ""
echo "📁 Struttura finale ottimizzata:"
echo "algorithms/     - Solo file .java e risultati workflow-specifici"
echo "generators/     - Script Python per generazione e visualizzazione"
echo "data/          - File CSV generati"
echo "visualizations/ - Grafici PNG generati"
echo "docs/          - Documentazione"

echo ""
echo "🎉 PULIZIA COMPLETATA!"
echo "===================="
echo ""
echo "✅ Progetto ottimizzato e pronto per l'uso!"
