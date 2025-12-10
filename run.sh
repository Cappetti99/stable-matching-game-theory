#!/bin/bash

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║     SM-CPTD PAPER EXPERIMENTS - Full Benchmark Suite          ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Vai nella directory degli algoritmi
cd algorithms

echo "🔨 Compilazione in corso..."
javac Main.java 2>&1

if [ $? -ne 0 ]; then
    echo "❌ Errore di compilazione!"
    exit 1
fi

echo "✅ Compilazione completata!"
echo ""
echo "🚀 Avvio esperimenti..."
echo ""

# Esegui il Main
java Main

exit_code=$?

echo ""
if [ $exit_code -eq 0 ]; then
    echo "✅ Esecuzione completata con successo!"
    echo ""
    echo "📁 Risultati salvati in:"
    echo "   - results/experiments_results.csv"
    echo "   - results/experiments_results.json"
    echo "   - results/figures/ (grafici)"
else
    echo "⚠️  Esecuzione completata con codice $exit_code"
fi

exit $exit_code
