# Analisi CCR vs SLR per Workflow Montage - Stable Matching Game Theory

## Panoramica

Questo progetto implementa un generatore di workflow Montage configurabile per l'analisi dell'impatto del Communication-to-Computation Ratio (CCR) sui risultati degli algoritmi di stable matching. Il sistema genera automaticamente grafici che mostrano la relazione tra CCR (asse X) e Schedule Length Ratio (SLR) (asse Y).

## Parametri Implementati

Secondo le specifiche richieste, il sistema supporta i seguenti parametri:

### Parametri del Workflow
- **Tipo di Workflow**: Montage (astronomia)
- **Scale**: small, medium, large
  - Small: 25 immagini (126 task)
  - Medium: 50 immagini (252 task) 
  - Large: 100 immagini (502 task)

### Parametri delle Risorse
- **Task Size**: 500-700 MIP (Million Instructions)
- **Numero di VM**: 5, 10, 50
- **Processing Capacity**: 10-20 MIPS per VM
- **Bandwidth**: 20-30 Mbps tra VM
- **CCR Range**: 0.4-2.0

## Struttura del Progetto

```
stable-matching-game-theory/
├── generators/
│   ├── creareMontage.py              # Generatore principale con analisi CCR
│   ├── montage_ccr_analysis_*.png    # Grafici generati
│   └── montage_ccr_results_*.json    # Risultati in formato JSON
├── algorithms/
│   ├── MainCCR.java                  # Algoritmo Java con supporto CCR variabile
│   ├── Main.java                     # Algoritmo originale
│   ├── DCP.java                      # Algoritmo DCP per stable matching
│   ├── Metrics.java                  # Calcolo metriche (SLR, makespan)
│   ├── task.java                     # Classe task
│   └── VM.java                       # Classe VM
└── data/
    ├── task.csv                      # Task con dimensioni
    ├── dag.csv                       # Dipendenze DAG
    ├── vm.csv                        # Matrice bandwidth
    └── processing_capacity.csv       # Capacità elaborazione VM
```

## Utilizzo

### Generazione Singola Workflow
```bash
# Workflow medium con 10 VM
python3 creareMontage.py --scale medium --num_vms 10

# Workflow large con 50 VM
python3 creareMontage.py --scale large --num_vms 50
```

### Analisi CCR vs SLR
```bash
# Analisi completa con 8 punti CCR
python3 creareMontage.py --scale medium --num_vms 10 --analyze_ccr --ccr_range 0.4,2.0 --num_points 8

# Analisi veloce con 5 punti
python3 creareMontage.py --scale small --num_vms 5 --analyze_ccr --ccr_range 0.5,1.5 --num_points 5
```

### Esecuzione Algoritmi Java
```bash
cd algorithms
javac *.java
java MainCCR 1.0  # Esegue con CCR=1.0
```

## Struttura Workflow Montage

Il workflow Montage implementato segue la struttura tipica per l'elaborazione di immagini astronomiche:

```
Per ogni immagine (1 a N):
  mProject → mDiffFit → mConcatFit → mBgModel → mBackground
                                                      ↓
                                               mAdd (finale)
                                                      ↓
                                               mJPEG (visualizzazione)
```

### Tipi di Task
- **mProject**: Riproiezione immagine (priorità 3)
- **mDiffFit**: Calcolo differenze (priorità 2)
- **mConcatFit**: Concatenazione fit (priorità 1)
- **mBgModel**: Modellazione background (priorità 2)
- **mBackground**: Sottrazione background (priorità 4)
- **mAdd**: Combinazione finale (priorità 5)
- **mJPEG**: Visualizzazione (priorità 1)

## Risultati Ottenuti

### Test Eseguiti

1. **Small Scale (25 immagini, 5 VM)**
   - CCR range: 0.5-1.5
   - Makespan range: 11.00-60.00
   - SLR: 1.000 (costante)

2. **Medium Scale (50 immagini, 10 VM)**
   - CCR range: 0.4-2.0 
   - Makespan range: 8.00-81.00
   - SLR: 1.000 (costante)

3. **Large Scale (100 immagini, 50 VM)**
   - CCR range: 0.4-2.0
   - Makespan range: 45.00-92.00
   - SLR: 1.000 (costante)

### Grafici Generati
- `montage_ccr_analysis_small_5vms.png`
- `montage_ccr_analysis_medium_10vms.png`
- `montage_ccr_analysis_large_50vms.png`

Ogni grafico contiene:
- CCR vs SLR (grafico principale richiesto)
- CCR vs Makespan (per completezza)
- Annotazioni con valori min/max
- Statistiche riassuntive

## File di Output

### File CSV (formato compatibile con algoritmi Java)
- **task.csv**: `t{ID} {size}` - Lista task con dimensioni
- **dag.csv**: `t{from} t{to}` - Dipendenze DAG
- **vm.csv**: Matrice bandwidth con header VM
- **processing_capacity.csv**: `vm{ID} {capacity}` - Capacità VM

### File Risultati JSON
```json
{
  "workflow_type": "Montage",
  "scale": "medium",
  "num_vms": 10,
  "parameters": {
    "task_size_range": "500-700 MIP",
    "vm_processing_capacity": "10-20 MIPS",
    "bandwidth_range": "20-30 Mbps",
    "ccr_range": "0.4-2.0"
  },
  "results": {
    "ccr_values": [...],
    "slr_values": [...], 
    "makespan_values": [...]
  },
  "statistics": {
    "min_slr": 1.0,
    "max_slr": 1.0,
    "avg_slr": 1.0,
    "min_makespan": 8.0,
    "max_makespan": 81.0,
    "avg_makespan": 36.0
  }
}
```

## Caratteristiche Tecniche

### Generatore Python
- **Linguaggio**: Python 3
- **Dipendenze**: numpy, matplotlib
- **Configurabilità**: Scale, numero VM, range CCR
- **Output**: CSV, JSON, PNG

### Algoritmi Java
- **MainCCR.java**: Supporta CCR variabile come parametro
- **Metriche**: Makespan, SLR secondo formule standard
- **Compatibilità**: File CSV generati da Python

### Validazione Parametri
- CCR range: 0.1-5.0
- VM count: 5, 10, 50
- Scale: small, medium, large
- Task size: 500-700 MIP automatico
- Processing capacity: 10-20 MIPS automatico
- Bandwidth: 20-30 Mbps automatico

## Esempi di Comando Completi

```bash
# Test rapido small scale
python3 creareMontage.py --scale small --num_vms 5 --analyze_ccr --ccr_range 0.4,2.0 --num_points 5

# Test medio medium scale  
python3 creareMontage.py --scale medium --num_vms 10 --analyze_ccr --ccr_range 0.4,2.0 --num_points 8

# Test completo large scale
python3 creareMontage.py --scale large --num_vms 50 --analyze_ccr --ccr_range 0.4,2.0 --num_points 10

# Generazione singola per testing algoritmi
python3 creareMontage.py --scale medium --num_vms 10
cd ../algorithms && javac *.java && java MainCCR 1.5
```

## Note Implementative

1. **CCR Impact**: Il sistema varia il CCR e osserva l'impatto su makespan e SLR
2. **Workflow Realistico**: Implementa un vero workflow Montage con dipendenze realistiche
3. **Parametri Conformi**: Tutti i parametri rispettano le specifiche richieste
4. **Visualizzazione**: Grafici chiari con CCR in X e SLR in Y come richiesto
5. **Automatizzazione**: Sistema completamente automatizzato per analisi batch

Il sistema fornisce una piattaforma completa per l'analisi dell'impatto del CCR sui workflow Montage usando algoritmi di stable matching, generando automaticamente i grafici richiesti e tutti i dati di supporto.

---

## 🚀 AGGIORNAMENTO: Analisi CCR Automatizzata Java + Python

### Nuovo Approccio Implementato

Il sistema è stato aggiornato per separare le responsabilità:

#### 1. **Generazione Workflow** (Python)
- Crea una volta sola il workflow Montage con 100 immagini (502 task) e 5 VM
- File: `generators/montage_ccr_analysis.py`

#### 2. **Analisi CCR** (Java)
- Varia il CCR da 0.4 a 2.0 direttamente nel codice Java
- Esegue l'algoritmo DCP per ogni valore di CCR
- Calcola metriche (Makespan, SLR, Critical Path)
- Salva risultati in JSON
- File: `algorithms/MainCCRAnalysis.java`

#### 3. **Visualizzazione** (Python)
- Legge i risultati JSON dall'analisi Java
- Genera grafici di correlazione CCR vs SLR/Makespan
- File: `generators/plot_ccr_analysis.py`

### Esecuzione Rapida

```bash
# Esecuzione automatica completa
./run_ccr_analysis.sh

# Oppure step by step:
# 1. Genera workflow
cd generators && python3 montage_ccr_analysis.py --scale large --num_vms 5

# 2. Analisi CCR
cd algorithms && javac *.java && java MainCCRAnalysis

# 3. Grafici
cd generators && python3 plot_ccr_analysis.py
```

### Risultati Ottenuti

- **Workflow**: 502 task, 5 VM
- **CCR Range**: 0.4 → 2.0 (10 punti)
- **Correlazione CCR ↔ SLR**: r = 1.000 (perfetta)
- **Aumento SLR**: +89.8% (da 0.186 a 0.353)
- **Critical Path**: Costante a 7 task

### File Generati

- `ccr_analysis_results.json`: Risultati numerici
- `montage_ccr_analysis_results.png`: Grafici di visualizzazione
