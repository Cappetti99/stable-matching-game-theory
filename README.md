# SM-CPTD — Stable Matching Cloud-based Parallel Task Duplication

SM-CPTD is a three-phase workflow scheduler for heterogeneous clouds:
1) **DCP** (Dynamic Critical Path) finds the critical path and ranks tasks; 2) **SMGT** (Stable Matching Game Theory) assigns tasks to VMs via stable matching; 3) **LOTD** (List of Task Duplication) duplicates tasks to cut communication delays.

📄 Reference: *A stable matching-based algorithm for task scheduling in cloud computing* (Journal of Supercomputing, 2021).

---

# SM-CPTD — Stable Matching + Parallel Task Duplication

This repository implements the SM-CPTD workflow scheduler and the experiment code used to reproduce the paper-style figures and metrics.

SM-CPTD is a three-phase pipeline:
1) DCP (Dynamic Critical Path): identifies a critical path / task priorities
2) SMGT (Stable Matching Game Theory): assigns tasks to VMs using stable matching
3) LOTD (List of Task Duplication): duplicates tasks to reduce communication delays

Reference: “A stable matching-based algorithm for task scheduling in cloud computing” (Journal of Supercomputing, 2021).

---

## Quick Start (what to run)

Recommended (one command):

```bash
./run.sh
```

What that script does, in order:
1) Compiles and runs `PegasusXMLParser` to (re)generate CSV workflows under `data/`
2) Compiles `Main.java`
3) Runs `java Main` (forwarding any CLI args)
4) `Main` calls `ExperimentRunner` and then tries to generate figures using Python (if available)

If you are on Windows, run the bash scripts via WSL or Git Bash, or run the Java commands manually (see below).

---

## Manual Run (no bash scripts)

Compile Java:

```bash
cd algorithms
javac *.java
```

Generate workflow CSVs (optional — runners also auto-generate as needed):

```bash
cd algorithms
java PegasusXMLParser
```

Run the main experiments:

```bash
cd algorithms
java Main
```

---

## Data Flow (extremely explicit)

When you run experiments, the data flows like this:

1) XML workflows (input)
	 - Stored in `workflow/<name>/*.xml`
	 - Examples: `workflow/montage/Montage_100.xml`, `workflow/cybershake/CyberShake_50.xml`

2) XML → CSV conversion
	 - `algorithms/PegasusXMLParser.java` converts a chosen XML into a folder like:
		 - `data/<workflow>_<tasks>/task.csv`
		 - `data/<workflow>_<tasks>/dag.csv`
		 - `data/<workflow>_<tasks>/processing_capacity.csv`
		 - `data/<workflow>_<tasks>/bandwidth.csv`

3) CSV → in-memory objects
	 - `algorithms/DataLoader.java` loads:
		 - task IDs + DAG edges (predecessors/successors)
		 - VM IDs
	 - Important: the numeric values in the CSVs are not used as “real” values by the Java runs.

4) Randomized numeric model (important assumption)
	 - Even though the CSVs contain numbers, `DataLoader` generates the numeric values uniformly at random:
		 - task size in [500, 700]
		 - VM capacity in [10, 20]
		 - bandwidth in [20, 30]
	 - This is deterministic by default due to `SeededRandom` (see “Reproducibility”).

5) Scheduling + metrics
	 - `SMCPTD.executeSMCPTD(...)` runs DCP → SMGT → LOTD
	 - `Metrics` computes SLR, AVU, VF, etc.

---

## Reproducibility (seeded randomness)

By default runs are deterministic.

- Set the seed explicitly:

```bash
cd algorithms
java Main --seed=123
```

- The experiment runners also vary a run index internally to create multiple runs with different generated values.

If you truly want non-deterministic randomness, `algorithms/Main.java` contains a commented line you can enable:

```java
// SeededRandom.setUseSeed(false);
```

---

## What Each Entry Point Does

### Main

- File: `algorithms/Main.java`
- Calls `ExperimentRunner.main(args)`
- Then attempts to run `generators/generate_paper_figures.py --auto` if Python + `pandas` are available

### ExperimentRunner (paper reproduction)

- File: `algorithms/ExperimentRunner.java`
- Runs two experiment families and writes results to `results/`:

Experiment 1 (CCR sweep; paper Figures 3–8 style):
- For each workflow and each CCR in {0.4, 0.6, …, 2.0}, run SM-CPTD and record metrics.

Experiment 2 (VM-count sweep; paper Figures 9–10 style):
- Fix CCR = 1.0 and vary VM count in {30, 35, …, 70}.

CLI flags:

```bash
cd algorithms
java ExperimentRunner            # run both experiments
java ExperimentRunner --exp1     # CCR sweep only
java ExperimentRunner --exp2     # VM-count sweep only
java ExperimentRunner --workflow=montage
```

CCR sensitivity snapshots:
- During Experiment 1, `CCRAnalyzer` captures extra data per CCR value and writes JSON under:
	- `results/ccr_sensitivity/<workflow>_<experiment>_analysis.json`

### AblationExperimentRunner (component ablation)

- File: `algorithms/AblationExperimentRunner.java`
- Runs the same workflow with 4 variants:
	1) SMGT only
	2) DCP + SMGT
	3) SMGT + LOTD
	4) Full SM-CPTD

```bash
cd algorithms
java AblationExperimentRunner
```

---

## Metrics (what they mean in this code)

- Makespan: total schedule length (max VM finish time)
- SLR: makespan normalized by a critical-path baseline
- AVU: Average VM Utilization across the makespan
- VF: Variance of (per-task) satisfaction / fairness proxy (lower is “fairer”)

Task duplication note:
- LOTD can schedule the same logical task multiple times on different VMs.
- In this project’s interpretation, duplicated executions are real work and should be counted as work when computing utilization/fairness.

---

## Outputs (what files you should expect)

Generated data (created on demand):
- `data/<workflow>_<tasks>/...` (CSV files generated from XML)

Experiment results:
- `results/experiments_results.json` (always)
- `results/experiments_results.csv` (written by `ExperimentRunner`)
- `results/ccr_sensitivity/` (created when running Experiment 1)

Figures:
- `results/figures/*.png` created by `generators/generate_paper_figures.py`

---

## Python Figure Generation

Scripts are in `generators/`:
- `generate_paper_figures.py`: reads `results/experiments_results.json` and writes plots under `results/figures/`
- `analyze_ccr_sensitivity.py`: consumes `results/ccr_sensitivity/*.json`
- `visualize_dag.py`: workflow visualization utility

Install dependencies if needed:

```bash
pip3 install pandas matplotlib
```

Run manually:

```bash
cd generators
python3 generate_paper_figures.py --auto
```

---

## Repo Structure (as in this workspace)

```
stable-matching-game-theory/
├── algorithms/                 Java sources
│   ├── Main.java               Entry point: runs ExperimentRunner and optional figures
│   ├── ExperimentRunner.java   Paper experiments (CCR sweep + VM sweep)
│   ├── AblationExperimentRunner.java  Ablation study runner
│   ├── SMCPTD.java             Full pipeline (DCP → SMGT → LOTD)
│   ├── DCP.java, SMGT.java, LOTD.java Core algorithms
│   ├── CCRAnalyzer.java        CCR sensitivity capture + JSON output
│   ├── DataLoader.java         Loads DAG structure; generates random sizes/capacities/bw
│   ├── PegasusXMLParser.java   Converts Pegasus XML → CSV folders under data/
│   ├── Metrics.java            SLR/AVU/VF and communication cost helpers
│   ├── VM.java, task.java      Core models
│   └── Utility.java            Shared helpers
├── generators/                 Python plotting and analysis
├── workflow/                   Input XML workflows
├── results/                    Saved results and generated figures
├── run.sh                      Bash: generate data → compile → run Main
└── clean.sh                    Bash: remove .class and generated data folders
```

---

## Maintenance

Remove compiled Java classes and generated CSV workflow folders:

```bash
./clean.sh
```

---

## References

- docs/s11227-021-03742-3.pdf
- docs/QESM.pdf
