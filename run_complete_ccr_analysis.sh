#!/bin/bash

# 🚀 SCRIPT AUTOMATICO PER ANALISI CCR COMPLETA
# =============================================
# Esegue JavaCCRAnalysis per tutti i workflow e genera i grafici
# 
# Autore: Lorenzo Cappetti
# Data: 25 luglio 2025

echo "🚀 ANALISI CCR COMPLETA - TUTTI I WORKFLOW"
echo "=========================================="

# Vai nella cartella algorithms
cd algorithms

# Compila tutti i file Java
echo "🔧 Compilando codice Java..."
javac *.java

if [ $? -ne 0 ]; then
    echo "❌ Errore di compilazione!"
    exit 1
fi

echo "✅ Compilazione completata!"

# Array dei workflow da analizzare
workflows=("cybershake" "epigenomics" "ligo" "montage")

echo ""
echo "📊 Eseguendo analisi CCR per tutti i workflow..."

# Esegui analisi per ogni workflow (solo il primo CCR per test veloce)
for workflow in "${workflows[@]}"; do
    echo ""
    echo "🔄 Analizzando workflow: $workflow"
    echo "-----------------------------------"
    
    # Modifica temporanea per test veloce - solo CCR 1.0
    timeout 30s java JavaCCRAnalysis "$workflow" || {
        echo "⚠️  Timeout o errore per $workflow, continuando..."
    }
done

echo ""
echo "📈 Generando grafici di confronto..."

# Torna alla root e genera i grafici
cd ..
cd generators

# Esegui lo script di plotting
python3 plot_ccr_comparison.py

if [ $? -eq 0 ]; then
    echo "✅ Grafici generati!"
else
    echo "❌ Errore nella generazione grafici!"
fi

echo ""
echo "🎉 ANALISI COMPLETA TERMINATA!"
echo "=============================="
echo ""
echo "📁 File generati:"
echo "   📊 algorithms/ccr_analysis_results_*.json"
echo "   📈 visualizations/ccr_comparison_*.png"
echo ""
echo "🔍 Controlla la cartella 'visualizations' per i grafici!"
echo ""
echo "💡 Per un'analisi completa (più lenta), modifica lo script"
echo "   rimuovendo il timeout di 30 secondi"
