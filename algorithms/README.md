# Algoritmi di Stable Matching

Questa cartella contiene l'implementazione degli algoritmi di stable matching per workflow scheduling.

## 📋 File Principali

- `Main.java` - Programma principale e coordinatore algoritmi
- `DCP.java` - Algoritmo Dominant Critical Path
- `SMGT.java` - Algoritmo Stable Matching Game Theory
- `LOTD.java` - Algoritmo List of Task Duplication
- `Metrics.java` - Calcolo metriche di performance
- `VM.java` - Classe per gestione Virtual Machine
- `task.java` - Classe per gestione task

## 🚀 Esecuzione

```bash
# Compilazione
javac *.java

# Esecuzione
java Main
```

## 📊 Algoritmi Implementati

### DCP (Dominant Critical Path)
- Identifica il cammino critico del DAG
- Calcola task priority tramite rank
- Ottimizza il makespan del workflow

### SMGT (Stable Matching Game Theory)
- Approccio basato su teoria dei giochi
- Matching stabile tra task e VM
- Considera preferenze reciproche

### LOTD (List of Task Duplication)
- Duplicazione intelligente dei task
- Riduzione tempo comunicazione
- Ottimizzazione scheduling

## 📈 Metriche Calcolate

- **Makespan** - Tempo totale di esecuzione
- **SLR** - Scheduling Length Ratio
- **AVU** - Average VM Utilization
- **VF** - Variance of Fairness

## 📁 Input Richiesti

Gli algoritmi leggono i seguenti file CSV dalla cartella `../data/`:

- `task.csv` - Lista task con dimensioni
- `dag.csv` - Dipendenze tra task
- `vm.csv` - Matrice bandwidth tra VM
- `processing_capacity.csv` - Capacità elaborazione VM
