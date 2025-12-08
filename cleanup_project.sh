#!/bin/bash

echo "🧹 Inizio pulizia del progetto..."

# 1. Rimuovi file compilati Java (.class)
echo "🗑️  Rimozione file .class..."
find . -name "*.class" -type f -delete

# 2. Rimuovi cache Python (__pycache__)
echo "🗑️  Rimozione directory __pycache__..."
find . -name "__pycache__" -type d -exec rm -rf {} +

# 3. Rimuovi file di sistema macOS (.DS_Store)
echo "🗑️  Rimozione file .DS_Store..."
find . -name ".DS_Store" -type f -delete

# 4. Rimuovi file di log temporanei (.log)
echo "🗑️  Rimozione file .log..."
find . -name "*.log" -type f -delete

# 5. Rimuovi file temporanei di output
echo "🗑️  Rimozione file temporanei (.tmp)..."
find . -name "*.tmp" -type f -delete

echo "✨ Pulizia completata! Il progetto è ora più leggero."
