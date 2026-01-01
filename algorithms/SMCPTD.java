/**
 * SM-CPTD: Stable Matching Cloud-based Parallel Task Duplication Algorithm
 *
 * This class implements the full SM-CPTD pipeline as described in the paper.
 * The algorithm combines three main components:
 *
 * 1. DCP (Dynamic Critical Path):
 *    - Computes task ranks recursively
 *    - Identifies the Critical Path of the DAG
 *
 * 2. SMGT (Stable Matching Game Theory):
 *    - Performs the main scheduling phase
 *    - Assigns tasks to VMs using stable matching
 *    - Gives priority to Critical Path tasks
 *
 * 3. LOTD (List of Task Duplication):
 *    - Applies task duplication heuristics
 *    - Reduces communication delays and overall makespan
 *
 * Input (provided by ExperimentRunner):
 * - List<task> tasks: workflow tasks with precedence constraints (DAG)
 * - List<VM> vms: available virtual machines
 * - Map<String, Double> communicationCosts: inter-task communication costs
 * - double CCR: Communication-to-Computation Ratio
 *
 * Output:
 * - Map<Integer, List<Integer>>: final VM → task assignment
 * - Performance metrics: makespan, SLR, AVU, VF
 */
public class SMCPTD {

    // ==================== ALGORITHM COMPONENTS ====================

    /** SMGT scheduler (core scheduling phase) */
    private SMGT smgt;

    /** LOTD optimizer (task duplication phase) */
    private LOTD lotd;

    // ==================== INTERMEDIATE RESULTS ====================

    /** Set of task IDs belonging to the Critical Path (output of DCP) */
    private Set<Integer> criticalPath;

    /** Schedule produced by SMGT (before duplication) */
    private Map<Integer, List<Integer>> smgtSchedule;

    /** Final schedule after LOTD optimization */
    private Map<Integer, List<Integer>> finalSchedule;

    // ==================== FINAL METRICS ====================

    /** Overall workflow makespan */
    private double makespan;

    /** Mapping of DAG levels: level → list of task IDs */
    private Map<Integer, List<Integer>> taskLevels;

    /** Enable/disable Gantt chart generation */
    private boolean generateGanttChart = false;

    /** Workflow name used in Gantt chart output */
    private String ganttChartWorkflow = "";

    /** CCR value associated with the Gantt chart */
    private double ganttChartCCR = 0.0;

    /**
     * Default constructor.
     *
     * Initializes internal components and resets all intermediate
     * and final results. The actual input data must be provided
     * via setInputData() before executing the algorithm.
     */
    public SMCPTD() {
        this.smgt = new SMGT();
        this.criticalPath = new HashSet<>();
        this.smgtSchedule = new HashMap<>();
        this.finalSchedule = new HashMap<>();
        this.makespan = 0.0;
    }

    /**
     * Sets input data coming from the ExperimentRunner.
     *
     * This method MUST be called before executeSMCPTD(),
     * as it initializes tasks, VMs, and DAG levels.
     *
     * @param tasks List of workflow tasks with dependencies (DAG)
     * @param vms   List of available virtual machines
     */
    public void setInputData(List<task> tasks, List<VM> vms) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks must be non-empty");
        }
        if (vms == null || vms.isEmpty()) {
            throw new IllegalArgumentException("vms must be non-empty");
        }

        // Pass input data to SMGT
        smgt.setTasks(tasks);
        smgt.setVMs(vms);

        // Precompute DAG levels for scheduling
        smgt.calculateTaskLevels();

        // Reset any previous execution state
        this.criticalPath = new HashSet<>();
        this.smgtSchedule = new HashMap<>();
        this.finalSchedule = new HashMap<>();
        this.makespan = 0.0;
        this.taskLevels = null;
        this.exitTask = null;

        System.out.println("✅ Input data set: " + tasks.size() +
                " tasks, " + vms.size() + " VMs");
    }

    /**
     * MAIN ALGORITHM: Executes the full SM-CPTD (Stable Matching - Critical Path Task Duplication)
     * 
     * Workflow:
     * 1. DCP: Identifies the Critical Path within the DAG.
     * 2. SMGT: Performs complete scheduling using Stable Matching Game Theory.
     * 3. LOTD: Optimizes the schedule through task duplication.
     * 4. Metrics: Calculates final performance metrics (e.g., Makespan).
     * 
     * @param communicationCosts Communication costs between tasks (key format: "taskId_succId")
     * @param vmMapping Mapping of VM IDs to their respective VM objects
     * @param ccr Communication-to-Computation Ratio
     * @return The final optimized scheduling map (vmID -> list of taskIDs)
     */
    public Map<Integer, List<Integer>> executeSMCPTD(
            Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping,
            double ccr) {

        System.out.println("\n" + "=".repeat(70));
        System.out.println("🎯 EXECUTING SM-CPTD ALGORITHM");
        System.out.println("=".repeat(70));

        // Input data validation
        if (smgt.getTasks() == null || smgt.getTasks().isEmpty()) {
            throw new IllegalStateException(
                "Input data not set. Call setInputData() or loadData() first!");
        }

        try {
            // STEP 1: DCP - Dynamic Critical Path
            System.out.println("\n📍 STEP 1: DCP - Identifying Critical Path");
            System.out.println("-".repeat(70));
            
            criticalPath = executeDCP(communicationCosts, vmMapping);
            
            System.out.println("✅ Critical Path identified:");
            System.out.println("   Size: " + criticalPath.size() + " tasks");
            System.out.println("   Tasks: " + criticalPath);

            // STEP 2: SMGT - Stable Matching Scheduling
            System.out.println("\n🎮 STEP 2: SMGT - Stable Matching Scheduling");
            System.out.println("-".repeat(70));
            System.out.println("SMGT processes each level:");
            System.out.println("  1. Assigns CP tasks to fastest VM");
            System.out.println("  2. Calculates threshold for remaining tasks");
            System.out.println("  3. Uses stable matching for non-CP tasks");
            
            smgtSchedule = smgt.runSMGT(criticalPath);
            
            System.out.println("\n✅ SMGT scheduling completed:");
            printScheduleSummary(smgtSchedule, "SMGT");

            // STEP 3: LOTD - Task Duplication Optimization
            System.out.println("\n📋 STEP 3: LOTD - Task Duplication Optimization");
            System.out.println("-".repeat(70));
            
            finalSchedule = executeLOTD(ccr);
            
            System.out.println("\n✅ LOTD optimization completed:");
            printScheduleSummary(finalSchedule, "LOTD");
            
            // Log task duplication statistics
            if (lotd != null) {
                Map<Integer, Set<Integer>> duplicates = lotd.getDuplicatedTasks();
                int totalDups = duplicates.values().stream()
                    .mapToInt(Set::size)
                    .sum();
                if (totalDups > 0) {
                    System.out.println("   📋 Duplicated tasks: " + totalDups);
                    for (Map.Entry<Integer, Set<Integer>> entry : duplicates.entrySet()) {
                        if (!entry.getValue().isEmpty()) {
                            System.out.println("      VM" + entry.getKey() + 
                                " has duplicates: " + entry.getValue());
                        }
                    }
                }
            }

            // STEP 4: Makespan Calculation
            System.out.println("\n📊 STEP 4: Calculating Makespan");
            System.out.println("-".repeat(70));
            
            calculateMakespan(vmMapping);

            // FINAL RESULTS SUMMARY
            System.out.println("\n" + "=".repeat(70));
            System.out.println("🎉 SM-CPTD COMPLETED SUCCESSFULLY!");
            System.out.println("=".repeat(70));
            printFinalResults();

            // Gantt chart generation (if enabled) 
            if (generateGanttChart) {
                System.out.println("\n📊 Generating Gantt chart JSON...");
                GanttChartGenerator.generateGanttChart(
                    ganttChartWorkflow,
                    smgt.getTasks().size(),
                    smgt.getVMs().size(),
                    ganttChartCCR,
                    makespan,
                    finalSchedule,
                    lotd.getTaskAST(),
                    lotd.getTaskAFT(),
                    criticalPath,
                    lotd.getDuplicatedTasks(),
                    lotd.getDuplicateAST(),
                    lotd.getDuplicateAFT(),
                    smgt.getTasks(),
                    smgt.getVMs()
                );
            }

            return finalSchedule;

        } catch (Exception e) {
            System.err.println("\n❌ ERROR during SM-CPTD execution: " + e.getMessage());
            e.printStackTrace();
            // Fallback: return initial SMGT results if duplication optimization fails
            return smgtSchedule != null ? smgtSchedule : new HashMap<>();
        }
    }

    /**
     * DCP Execution.
     * Organizes tasks by levels and identifies the Critical Path of the DAG.
     * 
     * @return Set of task IDs identified as Critical Path.
     */
    private Set<Integer> executeDCP(
            Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {

        System.out.println("   🔍 Organizing tasks by levels...");
        taskLevels = Utility.organizeTasksByLevels(smgt.getTasks());
        System.out.println("   ✓ DAG has " + taskLevels.size() + " levels");

        System.out.println("   🔍 Running DCP algorithm...");
        Set<Integer> cp = DCP.executeDCP(
            smgt.getTasks(),
            taskLevels,
            communicationCosts,
            vmMapping
        );

        return cp;
    }
    
    /**
     * Optimizes the SMGT schedule via task duplication
     */
    private Map<Integer, List<Integer>> executeLOTD(double ccr) {

        try {
            System.out.println("   🔧 Initializing LOTD with SMGT schedule...");
            
            lotd = new LOTD(smgt);
            lotd.setCCR(ccr);

            System.out.println("   🔧 Running LOTD optimization...");
            Map<Integer, List<Integer>> optimizedSchedule = 
                lotd.executeLOTDCorrect(smgtSchedule);

            return optimizedSchedule;

        } catch (Exception e) {
            System.err.println("   ⚠️  LOTD failed: " + e.getMessage());
            System.err.println("   ↪ Falling back to SMGT schedule");
            e.printStackTrace();
            
            // Fallback: use SMGT scheduling
            return new HashMap<>(smgtSchedule);
        }
    }

    /**
     * STEP 4: Makespan calculation (Paper Formula)
     * 
     * makespan = max{MS(VM_k)} for k in {0,...,m-1}
     * where MS(VM_k) is the finish time of the last task assigned to VM_k
     */
    private void calculateMakespan(Map<Integer, VM> vmMapping) {
        System.out.println("   📈 Calculating makespan...");
        System.out.println("   ℹ️  Formula: makespan = max{MS(VM_k)} for all VMs");

        double maxVMFinishTime = 0.0;
        
        // Get task AFT from LOTD if available
        Map<Integer, Double> taskAFT = (lotd != null) ? lotd.getTaskAFT() : null;
        
        for (Map.Entry<Integer, List<Integer>> entry : finalSchedule.entrySet()) {
            int vmId = entry.getKey();
            List<Integer> assignedTasks = entry.getValue();
            
            if (assignedTasks.isEmpty()) continue;
            
            double vmFinishTime = 0.0;
            
            if (taskAFT != null && !taskAFT.isEmpty()) {
                // MS(VM_k) = max AFT of tasks assigned to this VM
                for (Integer taskId : assignedTasks) {
                    Double aft = taskAFT.get(taskId);
                    if (aft != null) {
                        vmFinishTime = Math.max(vmFinishTime, aft);
                    }
                }
            } else {
                //warning fallback
                System.out.println("   ⚠️  Warning: Task AFT data not available from LOTD, " +
                    "using fallback calculation based on ET sums.");
            }
            System.out.println("      VM" + vmId + ": MS = " + String.format("%.3f", vmFinishTime));
            
            // makespan = max{MS(VM_k)}
            maxVMFinishTime = Math.max(maxVMFinishTime, vmFinishTime);
        }
        makespan = maxVMFinishTime;

        // Validation
        if (Double.isNaN(makespan) || makespan <= 0) {
            throw new IllegalStateException("Invalid makespan: " + makespan);
        }
        
        System.out.println("   ✅ Makespan = " + String.format("%.3f", makespan));
    }

    /**
     * Prints a schedule summary
     */
    private void printScheduleSummary(Map<Integer, List<Integer>> schedule, String phase) {
        int totalTasks = schedule.values().stream()
            .mapToInt(List::size)
            .sum();
        
        System.out.println("   " + phase + " schedule:");
        for (Map.Entry<Integer, List<Integer>> entry : schedule.entrySet()) {
            System.out.println("      VM" + entry.getKey() + ": " + 
                entry.getValue().size() + " tasks");
        }
        System.out.println("   Total tasks assigned: " + totalTasks);
    }

    /**
     * Prints final results
     */
    private void printFinalResults() {
        System.out.println("\n📋 FINAL RESULTS:");
        System.out.println("   🎯 Critical Path Size: " + criticalPath.size());
        System.out.println("   📊 Makespan: " + String.format("%.3f", makespan));
        
        // Display total LOTD duplications
        if (lotd != null) {
            int totalDups = lotd.getTotalDuplicationCount();
            System.out.println("   📋 Total Task Duplications (LOTD): " + totalDups);
        }
        
        System.out.println("\n   🖥️  VM Assignments:");

        for (Map.Entry<Integer, List<Integer>> entry : finalSchedule.entrySet()) {
            if (entry.getValue().isEmpty()) continue;
            
            String taskList = entry.getValue().stream()
                .sorted()
                .map(id -> "t" + id)
                .reduce((a, b) -> a + ", " + b)
                .orElse("(empty)");
            
            System.out.println("      VM" + entry.getKey() + ": " + taskList);
        }
    }

    // ==================== GETTERS ====================

    public Set<Integer> getCriticalPath() {
        return criticalPath;
    }

    public Map<Integer, List<Integer>> getSMGTSchedule() {
        return smgtSchedule;
    }

    public Map<Integer, List<Integer>> getFinalSchedule() {
        return finalSchedule;
    }

    public double getMakespan() {
        return makespan;
    }

    public SMGT getSMGT() {
        return smgt;
    }

    public LOTD getLOTD() {
        return lotd;
    }

    /**
     * Configure Gantt chart generation settings
     * 
     * @param generate Whether to generate Gantt charts
     * @param workflow Workflow name for chart title
     * @param ccr CCR value for chart title
     */
    public void setGanttChartSettings(boolean generate, String workflow, double ccr) {
        this.generateGanttChart = generate;
        this.ganttChartWorkflow = workflow;
        this.ganttChartCCR = ccr;
    }

    /**
     * Enable or disable Gantt chart generation
     * 
     * @param generate Whether to generate Gantt charts
     */
    public void setGenerateGanttChart(boolean generate) {
        this.generateGanttChart = generate;
    }

    /**
     * @return Whether Gantt chart generation is enabled
     */
    public boolean isGenerateGanttChart() {
        return generateGanttChart;
    }

    /**
     * @return Workflow name for Gantt chart
     */
    public String getGanttChartWorkflow() {
        return ganttChartWorkflow;
    }

    /**
     * @return CCR value for Gantt chart
     */
    public double getGanttChartCCR() {
        return ganttChartCCR;
    }
}