# Stable Matching Algorithms

This folder contains the stable-matching-based workflow scheduling algorithms.

## 📋 Key Files

- `Main.java` - Entry point and algorithm coordinator
- `DCP.java` - Dominant Critical Path algorithm
- `SMGT.java` - Stable Matching Game Theory scheduler
- `LOTD.java` - List of Task Duplication optimizer
- `Metrics.java` - Performance metrics
- `VM.java` - Virtual Machine model
- `task.java` - Task model

## 🚀 Running

```bash
# Compile
javac *.java

# Run
java Main
```

## 📊 Implemented Algorithms

### DCP (Dominant Critical Path)
- Identifies the DAG critical path
- Ranks tasks by priority
- Minimizes workflow makespan

### SMGT (Stable Matching Game Theory)
- Game-theory-based matching
- Stable matching between tasks and VMs
- Respects mutual preferences

### LOTD (List of Task Duplication)
- Intelligent task duplication
- Reduces communication time
- Improves schedule quality

## 📈 Metrics

- **Makespan** - Total execution time
- **SLR** - Scheduling Length Ratio
- **AVU** - Average VM Utilization
- **VF** - Variance of Fairness

## 📁 Required Inputs

Algorithms read the following CSV files from `../data/`:

- `task.csv` - Task list with sizes
- `dag.csv` - Task dependencies
- `vm.csv` - Bandwidth matrix between VMs
- `processing_capacity.csv` - VM processing capacities
