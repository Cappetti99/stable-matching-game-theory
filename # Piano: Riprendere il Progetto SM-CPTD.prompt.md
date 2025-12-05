# Piano: Riprendere il Progetto SM-CPTD

## Contesto

Il progetto implementa l'algoritmo **SM-CPTD** (Stable Matching Cloud-based Parallel Task Duplication) dal paper Springer per lo scheduling di workflow scientifici su cloud.

**Paper di riferimento**: `docs/s11227-021-03742-3.pdf`

---

## Stato Attuale ✅

| Componente | Status |
|------------|--------|
| Algoritmi (DCP, SMGT, LOTD, SM-CPTD) | ✅ Implementati |
| 4 Workflow (CyberShake, Montage, LIGO, Epigenomics) | ✅ Generati |
| Analisi CCR (0.4 → 2.0) | ✅ Completata |
| Bug CSV parsing | ✅ Risolti |

### Risultati CCR Attuali

| Workflow | SLR Range | Makespan Range |
|----------|-----------|----------------|
| CyberShake | 2.03 → 2.06 | 11.25 → 11.43 |
| Montage | 1.67 → 1.69 | 12.33 → 12.53 |
| LIGO | 2.03 → 2.06 | 11.90 → 12.10 |
| Epigenomics | 1.42 → 1.44 | 13.86 → 14.05 |

---

## 📋 Todolist

### 1. Rileggere il Paper
- [ ] Aprire `docs/s11227-021-03742-3.pdf`
- [ ] Rivedere la sezione "Experimental Setup" per configurazione
- [ ] Annotare i valori delle tabelle risultati (SLR, Makespan, AVU, VF)
- [ ] Verificare le formule delle metriche

### 2. Confrontare Risultati
- [ ] Confrontare SLR nostri vs paper per ogni workflow
- [ ] Confrontare Makespan nostri vs paper
- [ ] Identificare eventuali discrepanze significative

### 3. Verificare Metriche
- [ ] Controllare `algorithms/Metrics.java`
- [ ] Verificare formula SLR = Makespan / CP_min
- [ ] Verificare calcolo AVU (Average VM Utilization)
- [ ] Verificare calcolo VF (Variance of Fairness)

### 4. Pulizia Progetto
- [ ] Decidere su script .sh (cleanup.sh, quick_cleanup.sh, view_plots.sh)
- [ ] Rimuovere documentazione ridondante se necessario
- [ ] Eliminare file .class compilati

---

## File Chiave

### Algoritmi (`algorithms/`)
- `SMCPTD.java` - Algoritmo principale
- `DCP.java` - Dynamic Critical Path
- `SMGT.java` - Stable Matching Game Theory
- `LOTD.java` - List of Task Duplication
- `Metrics.java` - Calcolo metriche

### Generatori (`generators/`)
- `creareDAG.py` - CyberShake
- `creareMontage.py` - Montage
- `creareLIGO.py` - LIGO
- `creareEpigenomics.py` - Epigenomics

### Risultati
- `algorithms/ccr_analysis_results_*.json`

---

## Domande Aperte

1. I risultati corrispondono a quelli del paper?
2. Servono test con configurazioni diverse (più task/VM)?
3. Quali file .sh eliminare?
