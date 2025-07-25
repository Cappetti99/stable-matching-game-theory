# Stable Matching Game Theory Project

## 📁 Struttura del Progetto

```
stable-matching-game-theory/
├── algorithms/          # Algoritmi di stable matching
├── generators/          # Generatori di workflow
├── data/               # File CSV generati
├── docs/               # Documentazione e paper
├── workflows/          # Script e configurazioni workflow
└── README.md           # Questo file
```

## 🚀 Come Iniziare

### 1. Generare un Workflow

**CyberShake (geofisica sismica):**
```bash
python3 generators/creareDAG.py --sites 10
```

**Montage (astronomia):**
```bash
python3 generators/creareMontage.py --images 15
```

### 2. Eseguire Algoritmi Stable Matching

```bash
cd algorithms
javac *.java
java Main
```

## 📊 Algoritmi Implementati

- **DCP** - Dominant Critical Path
- **SMGT** - Stable Matching Game Theory  
- **LOTD** - List of Task Duplication

## 🔬 Workflow Supportati

- **CyberShake** - Simulazioni sismiche
- **Montage** - Mosaici astronomici

## 📖 Documentazione

Vedi cartella `docs/` per paper e documentazione completa.

## 🛠️ Sviluppo

Per contribuire al progetto, seguire la struttura delle cartelle e documentare le modifiche.
