# Generatori di Workflow

Questa cartella contiene i generatori di workflow scientifici per il testing degli algoritmi di stable matching.

## 🌊 CyberShake Generator (`creareDAG.py`)

Genera workflow CyberShake per simulazioni sismiche.

### Utilizzo:
```bash
python3 creareDAG.py --sites 10                           # 10 siti sismici
python3 creareDAG.py --sites 5 --sgt-variations 3         # 5 siti con 3 variazioni SGT
python3 creareDAG.py --sites 20 --psa-filters 4           # 20 siti con 4 filtri PSA
```

### Parametri:
- `--sites` (-s): Numero di siti sismici (1-100)
- `--sgt-variations`: Variazioni SGT per sito (1-10)
- `--psa-filters`: Filtri PSA per sito (1-10)

### Workflow generato:
```
PreCVM → N×GenCVM → N×M×GenSGT → N×P×PSA → N×ZipPSA → PostProcess
```

## 🌌 Montage Generator (`creareMontage.py`)

Genera workflow Montage per mosaici astronomici.

### Utilizzo:
```bash
python3 creareMontage.py --images 15                      # 15 immagini astronomiche
python3 creareMontage.py --images 25 --degrees 2.0        # 25 immagini, mosaico 2 gradi
```

### Parametri:
- `--images` (-i): Numero di immagini astronomiche (1-200)
- `--degrees` (-d): Dimensione mosaico in gradi (0.1-10.0)

### Workflow generato:
```
N×[mProject → mDiffFit → mConcatFit → mBgModel → mBackground] → mAdd → mJPEG
```

## 🌊 LIGO Generator (`creareLIGO.py`)

Genera workflow LIGO per rilevamento onde gravitazionali.

### Utilizzo:
```bash
python3 creareLIGO.py --segments 20                       # 20 segmenti di dati
python3 creareLIGO.py --segments 50 --templates 15        # 50 segmenti con 15 template
```

### Parametri:
- `--segments` (-s): Numero di segmenti di dati (1-1000)
- `--duration`: Durata segmento in secondi (default: 4096)
- `--templates`: Template per analisi inspiral (1-50)

### Workflow generato:
```
N×DataFind → TemplateBank → N×M×Inspiral → Coincidence → TrigBank → Injection → Thinca → PostProcess
```

## 🧬 Epigenomics Generator (`creareEpigenomics.py`)

Genera workflow Epigenomics per analisi bioinformatiche dell'epigenoma.

### Utilizzo:
```bash
python3 creareEpigenomics.py --samples 10                 # 10 campioni biologici
python3 creareEpigenomics.py --samples 25 --analyses 5    # 25 campioni con 5 tipi di analisi
python3 creareEpigenomics.py --samples 15 --chromosomes 24 # 15 campioni, 24 cromosomi
```

### Parametri:
- `--samples` (-s): Numero di campioni biologici (1-100)
- `--chromosomes`: Cromosomi da analizzare (1-24, default: 22)
- `--analyses`: Tipi di analisi epigenetiche (1-5, default: 3)

### Workflow generato:
```
N×2×FastQC → N×Trimming → N×Alignment → N×Sorting → N×Deduplication 
→ N×M×PeakCalling → N×Annotation → DifferentialAnalysis → Visualization → FinalReport
```

## 📄 File Generati

Tutti i generatori creano i seguenti file CSV compatibili con Main.java:

- `task.csv` - Task con formato 't{ID} size'
- `dag.csv` - Dipendenze con formato 't{from} t{to}'
- `vm.csv` - Matrice bandwidth VM
- `processing_capacity.csv` - Capacità elaborazione VM

I file vengono salvati nella cartella `../data/`

## 🚀 Utilizzo Tramite Script Principale

```bash
./run.sh cybershake 10      # CyberShake con 10 siti
./run.sh montage 20         # Montage con 20 immagini  
./run.sh ligo 30            # LIGO con 30 segmenti
./run.sh epigenomics 15     # Epigenomics con 15 campioni
```
