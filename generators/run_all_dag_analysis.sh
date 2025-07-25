#!/bin/bash

echo "🚀 CONFRONTO CCR TRA 4 DAG (50 task, 5 VM)"
echo "=========================================="
echo "DAG: CyberShake, Epigenomics, LIGO, Montage"
echo "CCR: 0.4 → 2.0 (step 0.2, 9 punti)"
echo ""

# Array dei DAG da testare (tutti e 4)
declare -a dags=("cybershake" "epigenomics" "ligo" "montage")

# Rimuovi grafici precedenti
rm -f *_ccr_analysis.png 2>/dev/null

echo "📊 Esecuzione analisi per tutti i DAG..."
echo ""

for dag in "${dags[@]}"; do
    echo "🔄 Processando $dag..."
    cd ../algorithms
    java JavaCCRAnalysis $dag
    cd ../generators
    
    if [ $? -eq 0 ]; then
        echo "✅ $dag completato"
    else
        echo "❌ $dag fallito"
    fi
    echo ""
done

echo "🎉 ANALISI COMPLETATA!"
echo ""
echo "📊 Grafici generati:"
ls -la *_ccr_analysis.png 2>/dev/null | awk '{print "  " $9}'

echo ""
echo "� Confronto finale:"
python3 compare_dag_results.py

echo ""
echo "�📋 Per vedere tutti i risultati:"
echo "  open *.png  # macOS"
echo "  eog *.png   # Linux"
