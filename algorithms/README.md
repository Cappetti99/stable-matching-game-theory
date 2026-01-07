# SM-CPTD (algorithms/)
## Overview

This folder contains the Java implementation of the SM-CPTD workflow scheduler, together with the components required to compute the evaluation metrics used in the associated paper.

SM-CPTD is structured as a three-stage scheduling pipeline:
1) **DCP** (Dynamic Critical Path): computes task ranks and identifies the **Critical Path (CP)** of the workflow.
2) **SMGT** (Stable Matching Game Theory): assigns tasks to virtual machines (VMs) on a level-by-level basis, prioritizing tasks belonging to the CP.
3) **LOTD** (List of Task Duplication): improves the schedule by selectively **duplicating tasks** in order to reduce communication overhead.

---

### Execution Entry Points

Two main entry points are provided:

1) **`Main.java`** 
This is the preferred entry point when executing the complete project workflow.
	- Calls `ExperimentRunner.main(args)`.
	- Then tries to run Python figure generation (`../generators/generate_paper_figures.py --auto`) if Python + `pandas` are installed.

2) **`ExperimentRunner.java`** 
This entry point is primarily intended for paper reproduction experiments.
	- Loads or generates datasets for each workflow and problem size.
	- Executes the scheduling pipeline via `SMCPTD.executeSMCPTD(...)`.
	- Computes evaluation metrics (SLR/AVU/VF/AvgSatisfaction/makespan) and writes results to the `../results/` directory.

When executed from the repository root, the `run.sh` script automates the full workflow (data generation → compile → run → optional figures).

---

### Data Flow 

The end-to-end execution pipeline proceeds as follows:

1) **Pegasus XML workflows** 
	- Input workflows are provided as XML files under `../workflow/<workflow>/*.xml`.

2) **XML → CSV Conversion** (`PegasusXMLParser`)
	- Converts a selected XML into a dataset directory `../data/<workflow>_<tasks>/`.
	- Generates the following CSV files:
	  - `dag.csv`: workflow DAG edges (predecessor → successor).
	  - `task.csv`: task IDs.
	  - `processing_capacity.csv`: VM IDs.
	  - `bandwidth.csv`: VM pair bandwidths.
	  - (`vm.csv` may also be written by the parser, but is not required by the loaders)         DECIDERE MEGLIO SE SCRIVERLO O WHAT

3) **CSV → In-Memory Representation** (`DataLoader`)
	- Loads the **DAG structure** from `dag.csv` (predecessor/successor lists).
	- Loads **task IDs** from `task.csv`.
	- Loads **VM IDs** from `processing_capacity.csv`.

4) **Important assumption: numeric values are generated, not read**
	- Numeric values are not read directly from the CSV files.
	- Instead, `DataLoader` generates numeric values uniformly at random:
	  - Task size in the range [500, 700].
	  - VM capacity in the range [10, 20].
	  - Bandwidth in the range [20, 30].
	- This means the CSV files are used primarily to define **structure and cardinality**, rather than exact numeric parameters.

5) **Scheduling and duplication**
	- The `SMCPTD` pipeline executes DCP → SMGT → LOTD.

6) **Metrics and outputs**
	- `Metrics` computes execution time, SLR, AVU/VU, VF, AvgSatisfaction, and communication cost.
	- Results are persisted under `../results/`.

---

### Schedule Representation 
Throughout this project, a schedule is generally represented as:

```text
Map<Integer, List<Integer>>
```  
where: 
- The **key** corresponds to a VM identifier (in practice, indices `0..m-1`)
- The **value** is an ordered list of task IDs assigned to that VM.

---

## Core Classes 

### `SMCPTD.java`

Coordinates the complete scheduling pipeline:
- Organizes tasks into DAG levels using `Utility.organizeTasksByLevels(...)`.
- Executes `DCP.executeDCP(...)` to compute task ranks and extract the Critical Path.
- Generates an initial schedule with `SMGT.runSMGT(criticalPath)`.
- Runs `LOTD.executeLOTD(smgtSchedule)` to add task duplicates and recompute timings.
- Computes the final makespan, primarily using LOTD’s AFT (Actual Finish Time) values.

Optional functionality includes Gantt chart JSON generation via `GanttChartGenerator`.

### `DCP.java` - Dynamic Critical Path

Computes task ranks recursively according to:

$$\text{rank}(t_i) = W_i + \max_{t_j \in succ(t_i)} (c_{i,j} + \text{rank}(t_j))$$

Where:
- $W_i$ is the average execution time of task $i$ across all VMs.
- $c_{i,j}$ is the **average** communication cost between tasks $i$ and $j$ across all VM pairs.

The Critical Path is constructed by selecting the maximum-rank task at each DAG level.

### `SMGT.java` - Stable Matching Game Theory scheduler

Performs level-by-level task scheduling:
- Separates tasks into Critical Path and non-Critical Path sets.
- Assigns CP tasks to the fastest VM.
- Computes per-VM capacity thresholds (how many tasks a VM can accept).
- Uses a stable matching process for non-CP tasks based on task/VM preferences.

The `VM.waitingList` is used to track assigned tasks and enforces capacity constraints (`waitingList.size() < threshold`).

### `LOTD.java` - List of Task Duplication

This implementation focuses on a targeted duplication strategy: 
- Only **entry tasks** are considered for duplication.
- Duplicates are placed on VMs hosting the children of the entry task when this reduces communication overhead.
- Maintains detailed timing information (`taskAST`, `taskAFT`, `duplicateAST`, `duplicateAFT`).
- Uses an insertion-based scheduling approach to exploit idle gaps on VM timelines.

### `Metrics.java`

Implements all evaluation metrics used by the experimental runners, including:
- Execution Time `ET(task, VM)`.
- Schedule Length Ratio `SLR(makespan, criticalPathTasks, vms)`.
- VM Utilization `VU(vm, assignedTasks, makespan)` and Average VM Utilization `AVU(vms, vmTaskAssignments, makespan)`.
- Variance Factor `VF(tasks, vms, vmTaskAssignments)`.
- Average Satisfaction `AvgSatisfaction(tasks, vms, vmTaskAssignments)`.
- Communication cost computation `CommunicationCostCalculator` (including “average across all VM pairs” for DCP).

---

## Inputs and Dataset Generation

### Dataset Structure

Experiments require datasets organized as:

```text
../data/<workflow>_<tasks>/
  dag.csv
  task.csv
  processing_capacity.csv
  bandwidth.csv
```
Only the DAG structure encoded in `dag.csv` is semantically relevant; numeric values in other files are regenerated at runtime.


### Workflow generation

CSV datasets can be generated from Pegasus XML workflows by executing:

```bash
cd algorithms
java PegasusXMLParser
```
The runners may also automatically generate missing datasets by selecting the closest matching XML workflow.

---

### Reproducibility

Randomness is centralized in `SeededRandom`.

Set a fixed seed:

```bash
cd algorithms
java Main --seed=123
```

By default, the runners vary an internal run index to produce multiple independent runs under the same base seed. This means each run will use different random data (e.g., different bandwidth values, task execution times).

### Fixed Seed Mode

If you want all runs to use **exactly the same data** (useful for comparing ablation study results with normal runs), use the `--fixed-seed` flag:

```bash
cd algorithms

# Run normal experiments with fixed data across all runs
java ExperimentRunner --seed=123 --fixed-seed

# Run ablation study with the same fixed data
java AblationExperimentRunner --seed=123 --fixed-seed
```

With `--fixed-seed`, all runs will use identical random data, making results directly comparable between different algorithm variants.

---

## Outputs

All outputs are written relative to the repository root:

- Results (written by `ExperimentRunner`):
  - `../results/experiments_results.csv`
  - `../results/experiments_results.json`

- Figures (if Python generation is enabled):
  - `../results/figures/*.png`

- Optional Gantt chart JSON (if enabled):
  - `../results/gantt_charts/*.json`

---
