# SM-CPTD: Stable Matching Cloud-based Parallel Task Duplication

Implementation of the **SM-CPTD algorithm** for task scheduling in cloud computing environments, based on stable matching game theory.

> 📄 **Reference Paper**: *"A stable matching-based algorithm for task scheduling in cloud computing"* - Springer, Journal of Supercomputing (2021)

---

## 🎯 Overview

SM-CPTD is a three-phase algorithm that optimizes task scheduling in heterogeneous cloud environments:

1. **DCP** (Dynamic Critical Path) - Identifies and schedules critical path tasks
2. **SMGT** (Stable Matching Game Theory) - Assigns remaining tasks using stable matching
3. **LOTD** (List of Task Duplication) - Duplicates tasks to reduce communication overhead

![Algorithm Flow](assets/algorithm_flow.png)

---

## 📁 Project Structure

```
stable-matching-game-theory/
├── algorithms/           # Java implementation
│   ├── SMCPTD.java      # Main SM-CPTD algorithm
│   ├── DCP.java         # Dynamic Critical Path
│   ├── SMGT.java        # Stable Matching Game Theory
│   ├── LOTD.java        # List of Task Duplication
│   ├── Main.java        # Entry point
│   ├── JavaCCRAnalysis.java  # CCR analysis tool
│   ├── task.java        # Task model
│   ├── VM.java          # Virtual Machine model
│   └── Metrics.java     # Performance metrics
├── generators/           # Python scripts
│   ├── creareDAG.py     # Generate synthetic DAG
│   ├── creareMontage.py # Montage workflow generator
│   ├── creareEpigenomics.py
│   ├── creareLIGO.py
│   └── visualize_dag.py # DAG visualization
├── data/                 # Default dataset
├── data_test_cybershake/ # CyberShake workflow
├── data_test_epigenomics/# Epigenomics workflow
├── data_test_ligo/       # LIGO workflow
├── data_test_montage/    # Montage workflow
├── results/              # Analysis results (JSON)
├── assets/               # Images for documentation
├── docs/                 # Papers and documentation
└── cleanup.sh            # Cleanup script
```

---

## 🚀 Quick Start

### Prerequisites
- Java 8+ (JDK)
- Python 3.x (for generators and visualization)

### 1. Compile the algorithms

```bash
cd algorithms
javac *.java
```

### 2. Run with default dataset

```bash
java Main
```

### 3. Run CCR Analysis on scientific workflows

```bash
# Available workflows: montage, cybershake, ligo, epigenomics
java JavaCCRAnalysis montage
java JavaCCRAnalysis cybershake
java JavaCCRAnalysis ligo
java JavaCCRAnalysis epigenomics
```

---

## 📊 Algorithm Details

### Phase 1: DCP (Dynamic Critical Path)

```
1. Calculate rank(ti) = ET(ti) + max{rank(tj) + Ttrans(ti,tj)} for all tasks
2. For each level l:
   - Select task with maximum rank → Critical Path
3. Schedule CP tasks to VM with minimum Finish Time
```

### Phase 2: SMGT (Stable Matching Game Theory)

```
1. For each level l:
   - Tl = {tasks in level l} \ CP  (exclude Critical Path tasks)
   - Calculate threshold θ = |Tl| / |V|
   - Apply stable matching with threshold
   - Tasks propose to VMs based on Execution Time
```

### Phase 3: LOTD (List of Task Duplication)

```
1. For each level 0 task ti:
   - VM_candidates = {VMs where successors of ti are assigned}
   - For each candidate VM:
     - Find idle slot before successor's start time
     - Verify Rule 2: AFT_new ≤ AFT_old (don't delay other tasks)
     - If valid: duplicate ti to VM
```

---

## 📈 Performance Metrics

| Metric | Formula | Description |
|--------|---------|-------------|
| **Makespan** | `max{FT(ti)}` | Total execution time |
| **SLR** | `makespan / Σmin{ET(ti,VMk)}` | Schedule Length Ratio (lower = better) |
| **Speedup** | `sequential_time / makespan` | Parallelization efficiency |

### Key Formulas

- **Execution Time**: `ET(ti, VMk) = si / pk` (task size / VM capacity)
- **Start Time**: `ST(ti, VMk) = max{FT(predecessor) + Ttrans}`
- **Finish Time**: `FT(ti, VMk) = ST + ET`
- **Transfer Time**: `Ttrans(ti, tj) = 0` if same VM, else `data_size / bandwidth`

---

## 🔬 Supported Scientific Workflows

| Workflow | Domain | Structure | Tasks |
|----------|--------|-----------|-------|
| **Montage** | Astronomy | Pipeline + parallel | 50 |
| **CyberShake** | Seismology | Fork-join | 50 |
| **LIGO** | Gravitational waves | Pipeline | 50 |
| **Epigenomics** | Bioinformatics | Complex DAG | 50 |

### Generate new workflow data

```bash
cd generators
python3 creareMontage.py     # Generate Montage workflow
python3 creareEpigenomics.py # Generate Epigenomics workflow
python3 creareLIGO.py        # Generate LIGO workflow
```

---

## 📋 Results

CCR Analysis results are stored in `results/` folder as JSON files:

```json
{
  "workflow_type": "montage",
  "parameters": { "target_tasks": 50, "target_vms": 5 },
  "results": [
    { "ccr": 0.4, "slr": 2.054, "makespan": 15.20 },
    { "ccr": 1.0, "slr": 2.064, "makespan": 15.28 },
    { "ccr": 2.0, "slr": 2.081, "makespan": 15.40 }
  ]
}
```

### CCR Analysis Results

![SM-CPTD Results](assets/smcptd_real_results.png)

The plot shows **SLR (Schedule Length Ratio)** vs **CCR (Communication-to-Computation Ratio)** for all 4 scientific workflows. Key observations:

- **Epigenomics** achieves the best SLR (~1.85) due to its complex structure allowing better parallelization
- All workflows show **linear correlation** between CCR and SLR (expected behavior)
- SLR increase is contained to **1-2%** even when doubling CCR, demonstrating SM-CPTD's robustness

### Results Summary

| Workflow | SLR Range | Makespan Range | SLR Increase |
|----------|-----------|----------------|--------------|
| Montage | 2.05 → 2.08 | 15.2 → 15.4 | +1.3% |
| CyberShake | 2.12 → 2.15 | 11.8 → 11.9 | +1.5% |
| LIGO | 2.03 → 2.07 | 12.0 → 12.2 | +1.7% |
| Epigenomics | 1.85 → 1.87 | 18.0 → 18.2 | +1.0% |

### Regenerate plots

```bash
python3 generators/plot_ccr_comparison.py
```

---

## 🧹 Maintenance

Clean compiled files and generated outputs:

```bash
./cleanup.sh
```

---

## 📚 References

- Original Paper: `docs/s11227-021-03742-3.pdf`
- QESM Paper: `docs/QESM.pdf`

---

## 👤 Author

**Lorenzo Cappetti**

---

## 📝 License

This project is for educational and research purposes.

## 🛠️ Sviluppo

Per contribuire al progetto, seguire la struttura delle cartelle e documentare le modifiche.
