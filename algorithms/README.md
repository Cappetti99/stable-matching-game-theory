# SM-CPTD Java Module (algorithms/)

This folder contains the Java implementation of the SM-CPTD workflow scheduler and the code that computes the paper-style metrics.

At a high level, SM-CPTD is a 3-stage pipeline:
1) **DCP** computes task ranks and selects a **Critical Path (CP)**.
2) **SMGT** assigns tasks to VMs level-by-level using a stable matching strategy, prioritizing CP tasks.
3) **LOTD** improves the schedule by **duplicating tasks** to reduce communication overhead.

This README is written by reading the actual code in this folder (not just the paper).

---

## What Runs When You Launch It

There are two “entry points” you should know:

1) **`Main.java`** (preferred when running the whole project)
	- Calls `ExperimentRunner.main(args)`.
	- Then tries to run Python figure generation (`../generators/generate_paper_figures.py --auto`) if Python + `pandas` are installed.

2) **`ExperimentRunner.java`** (paper reproduction)
	- Loads or generates a dataset for each workflow/size.
	- Runs `SMCPTD.executeSMCPTD(...)`.
	- Computes metrics (SLR/AVU/VF/AvgSatisfaction/makespan) and writes results under `../results/`.

If you run from the repository root, `run.sh` automates the full process (data generation → compile → run → optional figures).

---

## Data Flow (from files to schedules)

The execution pipeline is:

1) **Pegasus XML workflows** (inputs)
	- Located under `../workflow/<workflow>/*.xml`.

2) **XML → CSV conversion** (`PegasusXMLParser`)
	- Converts a selected XML into a dataset folder like `../data/<workflow>_<tasks>/`.
	- Writes these CSVs:
	  - `dag.csv` (DAG edges: predecessor → successor)
	  - `task.csv` (task IDs)
	  - `processing_capacity.csv` (VM IDs)
	  - `bandwidth.csv` (VM pair bandwidths)
	  - (`vm.csv` may also be written by the parser, but is not required by the loaders)

3) **CSV → in-memory objects** (`DataLoader`)
	- Loads the **DAG structure** from `dag.csv` (predecessor/successor lists).
	- Loads **task IDs** from `task.csv`.
	- Loads **VM IDs** from `processing_capacity.csv`.

4) **Important assumption: numeric values are generated, not read**
	- Even though the CSVs contain numbers, `DataLoader` generates numeric values uniformly at random:
	  - Task size in [500, 700]
	  - VM capacity in [10, 20]
	  - Bandwidth in [20, 30]
	- This means the CSVs are used primarily to provide **structure and counts**.

5) **Scheduling and duplication**
	- `SMCPTD` runs DCP → SMGT → LOTD.

6) **Metrics & outputs**
	- `Metrics` provides ET, SLR, AVU/VU, VF, AvgSatisfaction, and communication cost helpers.
	- Runners persist results under `../results/`.

---

## Schedule Representation (critical to understand the code)

Throughout this project, a schedule is generally represented as:

```text
Map<Integer, List<Integer>>
  key   = VM identifier (in practice 0..m-1)
  value = ordered list of task IDs assigned to that VM
```

### VM IDs vs VM indices

`SMGT.runSMGT(...)` initializes the schedule keys as `0..vms.size()-1`.

In this repository, datasets are generated so that VM IDs are also `0..m-1`, therefore **VM index == VM ID** in normal runs.
If you ever change VM IDs to something else, you must revisit schedule keying and VM mappings.

### Duplicated tasks

`LOTD` can insert a task ID into **multiple VM schedules**.
In this implementation, LOTD focuses on duplicating **entry tasks** (tasks with no predecessors) when that reduces the child’s communication delay.

---

## Core Classes (what each file does)

### `SMCPTD.java`

Orchestrates the full pipeline:
- Uses `Utility.organizeTasksByLevels(...)` to build DAG levels.
- Runs `DCP.executeDCP(...)` to compute task ranks and select the Critical Path.
- Runs `SMGT.runSMGT(criticalPath)` to create an initial schedule.
- Runs `LOTD.executeLOTD(smgtSchedule)` to add task duplicates and recompute timings.
- Computes makespan primarily from LOTD’s AFT map (finish times).

Optional:
- Can emit a Gantt chart JSON via `GanttChartGenerator` when enabled by the runner.

### `DCP.java` (Dynamic Critical Path)

Computes a rank for each task recursively:

$$\text{rank}(t_i) = W_i + \max_{t_j \in succ(t_i)} (c_{i,j} + \text{rank}(t_j))$$

Where:
- $W_i$ is the average execution time of task $i$ across all VMs.
- $c_{i,j}$ is the **average** communication cost between tasks $i$ and $j$ across all VM pairs.

Then it builds the Critical Path by selecting the maximum-rank task at each DAG level.

### `SMGT.java` (Stable Matching Game Theory scheduler)

Schedules tasks level-by-level:
- Splits tasks at each level into CP and non-CP.
- Assigns CP tasks to the fastest VM.
- Computes per-VM capacity thresholds (how many tasks a VM can accept).
- Uses a stable-matching process for non-CP tasks based on task/VM preferences.

Important internal detail:
- `VM.waitingList` is used to track assigned tasks and fullness (`waitingList.size() >= threshold`).

### `LOTD.java` (List of Task Duplication)

This implementation is intentionally “focused”:
- It attempts to duplicate **entry tasks only**.
- A duplicate is placed on VMs that host the entry task’s children when it reduces the child’s communication overhead.
- It maintains per-task timing (`taskAST`, `taskAFT`) and per-VM duplicate timing (`duplicateAST`, `duplicateAFT`).
- Scheduling is “insertion-based”: it tries to fit tasks in gaps on the VM timeline.

### `Metrics.java`

Provides the metrics and helper formulas used by the runners:
- `ET(task, VM)`
- `SLR(makespan, criticalPathTasks, vms)`
- `VU(vm, assignedTasks, makespan)` and `AVU(vms, vmTaskAssignments, makespan)`
- `VF(tasks, vms, vmTaskAssignments)`
- `AvgSatisfaction(tasks, vms, vmTaskAssignments)`
- `CommunicationCostCalculator` (including “average across all VM pairs” for DCP)

---

## Inputs and Where They Come From

### Datasets on disk

Experiments expect a dataset directory like:

```text
../data/<workflow>_<tasks>/
  dag.csv
  task.csv
  processing_capacity.csv
  bandwidth.csv
```

Notes:
- `dag.csv` is the only file whose numeric values matter for structure (edges).
- Task/VM numeric values in the CSVs are not trusted during runs; the loaders generate values.

### Workflow generation

You can generate the CSV datasets from XML with:

```bash
cd algorithms
java PegasusXMLParser
```

The runners can also auto-generate the dataset folder on demand by selecting the closest XML under `../workflow/<workflow>/`.

---

## How to Build and Run (from this folder)

Compile:

```bash
cd algorithms
javac *.java
```

Run the main experiment pipeline:

```bash
cd algorithms
java Main
```

Run just the experiment runner:

```bash
cd algorithms
java ExperimentRunner --exp1
```

---

## Reproducibility (seed)

Randomness is centralized in `SeededRandom`.

Set a fixed seed:

```bash
cd algorithms
java Main --seed=123
```

The runners also vary a run index internally to produce multiple independent runs (NUM_RUNS) under the same base seed.

---

## Outputs

This folder itself does not store outputs; outputs are written relative to the repo root:

- Results (written by `ExperimentRunner`):
  - `../results/experiments_results.csv`
  - `../results/experiments_results.json`

- Figures (Python, if enabled):
  - `../results/figures/*.png`

- Optional Gantt chart JSON (if enabled):
  - `../results/gantt_charts/*.json`

---

## Common “gotchas” (things that confuse graders)

- The CSV numeric values are not the source of truth for task/VM/bandwidth values; `DataLoader` generates them.
- SMGT schedules are keyed by VM **index**; this works because VM IDs are 0..m-1 in the generated datasets.
- LOTD duplicates tasks by repeating the same task ID on multiple VMs; those duplicates are intended to represent real execution.
