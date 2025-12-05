#!/bin/bash

# 🧹 PULIZIA RAPIDA PROGETTO
# ==========================
# Rimuove solo file compilati superflui
# 
# Autore: Lorenzo Cappetti

echo "🧹 PULIZIA RAPIDA"
echo "=================="

cd "$(dirname "$0")"

# Rimuovi file .class Java
echo "🗑️  Rimuovendo file .class..."
find algorithms/ -name "*.class" -delete
class_count=$(find algorithms/ -name "*.class" 2>/dev/null | wc -l)

if [ $class_count -eq 0 ]; then
    echo "✅ File .class rimossi"
else
    echo "⚠️  Alcuni file .class potrebbero essere ancora presenti"
fi

# Rimuovi cache Python
echo "🗑️  Rimuovendo cache Python..."
find . -name "__pycache__" -type d -exec rm -rf {} + 2>/dev/null || true
find . -name "*.pyc" -delete 2>/dev/null || true

# Rimuovi file temporanei
echo "🗑️  Rimuovendo file temporanei..."
find . -name "*.tmp" -delete 2>/dev/null || true
find . -name "*~" -delete 2>/dev/null || true
find . -name ".DS_Store" -delete 2>/dev/null || true

echo "✅ Pulizia completata!"
echo ""
echo "📁 File preservati:"
echo "   ✅ Codice sorgente (.java, .py)"
echo "   ✅ Risultati analisi (.json)"
echo "   ✅ Grafici (.png)"
echo "   ✅ Dati (.csv)"
