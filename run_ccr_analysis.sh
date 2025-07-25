#!/bin/bash

echo "🚀 AUTOMATED CCR ANALYSIS - PURE JAVA WORKFLOW"
echo "=============================================="

# Parametri
WORKFLOW_TYPE="montage"    # Tipo di workflow (cybershake, epigenomics, ligo, montage)
NUM_TASKS=50               # Numero task generati dinamicamente
NUM_VMS=5
CCR_MIN=0.4
CCR_MAX=2.0

echo "📋 Parametri analisi:"
echo "  - Workflow: $WORKFLOW_TYPE (generato dinamicamente in Java)"
echo "  - Numero task: $NUM_TASKS"
echo "  - Numero VM: $NUM_VMS"
echo "  - Range CCR: $CCR_MIN - $CCR_MAX (9 punti)"
echo ""

# Step 1: Esegui analisi CCR completa in Java
echo "📊 Step 1: Analisi CCR completa con Java (generazione + algoritmi)..."
cd algorithms

# Compila i file Java se necessario
echo "🔨 Compilazione Java..."
javac *.java
if [ $? -ne 0 ]; then
    echo "❌ Errore di compilazione Java"
    exit 1
fi

# Esegui analisi CCR completa (generazione DAG + analisi + salvataggio)
echo "🏃 Esecuzione analisi CCR completa..."
java JavaCCRAnalysis $WORKFLOW_TYPE
if [ $? -ne 0 ]; then
    echo "❌ Errore nell'esecuzione dell'analisi Java"
    exit 1
fi

echo "✅ Analisi CCR completata"
echo ""

# Step 2: Genera visualizzazioni complete con tool unificato
echo "📈 Step 2: Generazione visualizzazioni complete..."
cd ..
python3 visualize_results.py
if [ $? -ne 0 ]; then
    echo "❌ Errore nella generazione delle visualizzazioni"
    exit 1
fi

echo "✅ Visualizzazioni complete generate"
echo ""

# Step 3: Riassunto finale
echo "🎉 ANALISI COMPLETATA!"
echo "====================="
echo ""

# Pulizia automatica file .class
echo "🧹 Pulizia file temporanei..."
cd algorithms
rm -f *.class 2>/dev/null
echo "✅ File temporanei rimossi"
echo ""

echo "📁 File generati:"
echo "  - algorithms/ccr_analysis_results.json     (risultati analisi CCR)"
echo "  - visualizations/workflow_comparison.png   (confronto workflow)"
echo "  - visualizations/*_detailed_analysis.png   (analisi dettagliate)"
echo "  - visualizations/analysis_summary.txt      (report testuale)"
echo ""
echo "📊 Risultati chiave:"
echo "  - Workflow: $WORKFLOW_TYPE (generato dinamicamente con API Pegasus-style)"
echo "  - Task: $NUM_TASKS task con struttura realistica"
echo "  - VM: $NUM_VMS VM con capacità variabili"
echo "  - CCR testati: $CCR_MIN - $CCR_MAX (9 punti)"
echo "  - Algoritmo: SMGT con stable matching per ottimizzazione task-VM"
echo ""
echo "🔍 Per visualizzare i risultati:"
echo "  - Apri: visualizations/workflow_comparison.png"
echo "  - Leggi: visualizations/analysis_summary.txt"
echo "  - Dettagli: visualizations/[workflow]_detailed_analysis.png"
echo ""
echo "💡 Per testare altri workflow:"
echo "  - Modifica WORKFLOW_TYPE (cybershake, epigenomics, ligo, montage)"
echo "  - Riesegui: ./run_ccr_analysis.sh"
echo ""
echo "🎨 Modalità visualizzazione:"
echo "  - Completa: python3 visualize_results.py"
echo "  - Solo report: python3 visualize_results.py report"
echo "  - Workflow corrente: python3 visualize_results.py single"
