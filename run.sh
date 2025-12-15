#!/bin/bash

set -euo pipefail

# Esegui sempre dalla root del repo (indipendente dalla cwd)
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$SCRIPT_DIR"

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║     SM-CPTD PAPER EXPERIMENTS - Full Benchmark Suite          ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# Vai nella directory degli algoritmi
cd "$REPO_ROOT/algorithms"

echo "📊 Generazione dati workflow con parametri del paper..."
echo "   - Task sizes: [500, 700] distribuzione uniforme"
echo "   - VM capacities: [20, 30] distribuzione uniforme"
echo "   - Bandwidth: [20, 30] distribuzione uniforme"
echo ""

javac PegasusXMLParser.java
java PegasusXMLParser > /dev/null 2>&1

if [ $? -ne 0 ]; then
    echo "❌ Errore nella generazione dei dati!"
    exit 1
fi

echo "✅ Dati generati con successo!"
echo ""

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
