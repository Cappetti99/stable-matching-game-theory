#!/bin/bash

# 🖼️ VISUALIZZATORE GRAFICI
# =========================
# Apre tutti i grafici generati dal progetto
# 
# Autore: Lorenzo Cappetti
# Data: 25 luglio 2025

echo "🖼️  VISUALIZZATORE GRAFICI STABLE MATCHING"
echo "=========================================="

VISUALIZATION_DIR="visualizations"

if [ ! -d "$VISUALIZATION_DIR" ]; then
    echo "❌ Cartella visualizations non trovata!"
    echo "💡 Esegui prima: python3 generators/plot_ccr_comparison.py"
    exit 1
fi

echo "📊 Cercando grafici in $VISUALIZATION_DIR..."

# Lista dei grafici disponibili
png_files=$(find "$VISUALIZATION_DIR" -name "*.png" -type f)

if [ -z "$png_files" ]; then
    echo "❌ Nessun grafico PNG trovato!"
    echo "💡 Esegui prima: python3 generators/plot_ccr_comparison.py"
    exit 1
fi

echo "📈 Grafici trovati:"
echo "$png_files" | while read file; do
    echo "   📊 $(basename "$file")"
done

echo ""
echo "🖼️  Aprendo grafici..."

# Apri tutti i file PNG
if command -v open >/dev/null 2>&1; then
    # macOS
    echo "$png_files" | while read file; do
        echo "   📈 Aprendo: $(basename "$file")"
        open "$file"
    done
elif command -v xdg-open >/dev/null 2>&1; then
    # Linux
    echo "$png_files" | while read file; do
        echo "   📈 Aprendo: $(basename "$file")"
        xdg-open "$file"
    done
elif command -v start >/dev/null 2>&1; then
    # Windows (Git Bash)
    echo "$png_files" | while read file; do
        echo "   📈 Aprendo: $(basename "$file")"
        start "$file"
    done
else
    echo "⚠️  Sistema operativo non riconosciuto"
    echo "📁 Apri manualmente i file in: $VISUALIZATION_DIR/"
    ls -la "$VISUALIZATION_DIR"/*.png
fi

echo ""
echo "✅ Visualizzazione completata!"
