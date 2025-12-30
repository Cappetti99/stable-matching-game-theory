import java.util.*;
import java.io.*;

/**
 * SM-CPTD: Stable Matching Cloud-based Parallel Task Duplication Algorithm
 * 
 * Pipeline dell'algoritmo (come da paper):
 * 1. DCP (Dynamic Critical Path) - Identifica il Critical Path del DAG
 * 2. SMGT (Stable Matching Game Theory) - Scheduling completo con stable matching
 * 3. LOTD (List of Task Duplication) - Ottimizzazione tramite duplicazione task
 * 
 * Input (da ExperimentRunner):
 * - List<task> tasks: task del workflow con dipendenze (DAG)
 * - List<VM> vms: virtual machines disponibili
 * - Map<String, Double> communicationCosts: costi comunicazione tra task
 * - double CCR: Communication to Computation Ratio
 * 
 * Output:
 * - Map<Integer, List<Integer>>: assegnamento finale vmID -> [taskIDs]
 * - Metriche: makespan, SLR, AVU, VF
 * 
 * @author Lorenzo Cappetti
 * @version 2.0 - Refactored
 */
public class SMCPTD {

    // Componenti dell'algoritmo
    private SMGT smgt;
    private LOTD lotd;

    // Risultati intermedi
    private Set<Integer> criticalPath;           // Output DCP
    private Map<Integer, List<Integer>> smgtSchedule;  // Output SMGT
    private Map<Integer, List<Integer>> finalSchedule; // Output LOTD

    // Metriche finali
    private double makespan;

    // Dati necessari per il calcolo
    private task exitTask;
    private Map<Integer, List<Integer>> taskLevels;

    // Gantt chart settings
    private boolean generateGanttChart = false;
    private String ganttChartWorkflow = "";
    private double ganttChartCCR = 0.0;

    /**
     * Costruttore
     */
    public SMCPTD() {
        this.smgt = new SMGT();
        this.criticalPath = new HashSet<>();
        this.smgtSchedule = new HashMap<>();
        this.finalSchedule = new HashMap<>();
        this.makespan = 0.0;
    }

    /**
     * Imposta i dati di input dall'ExperimentRunner
     * Questo metodo DEVE essere chiamato prima di executeSMCPTD()
     * 
     * @param tasks Lista dei task con dipendenze (DAG)
     * @param vms Lista delle VM disponibili
     */
    public void setInputData(List<task> tasks, List<VM> vms) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks must be non-empty");
        }
        if (vms == null || vms.isEmpty()) {
            throw new IllegalArgumentException("vms must be non-empty");
        }

        // Imposta dati in SMGT
        smgt.setTasks(tasks);
        smgt.setVMs(vms);
        smgt.calculateTaskLevels(); // Calcola livelli del DAG

        // Reset stato precedente
        this.criticalPath = new HashSet<>();
        this.smgtSchedule = new HashMap<>();
        this.finalSchedule = new HashMap<>();
        this.makespan = 0.0;
        this.taskLevels = null;
        this.exitTask = null;

        System.out.println("✅ Input data set: " + tasks.size() + " tasks, " + vms.size() + " VMs");
    }

    /**
     * Carica dati da file CSV (alternativa a setInputData)
     * Usato per testing standalone
     */
    public void loadData(String dagFilename, String taskFilename, String vmFilename) throws IOException {
        System.out.println("🚀 SM-CPTD: Loading data from CSV files...");

        List<task> tasks = DataLoader.loadTasksFromCSV(dagFilename, taskFilename);
        List<VM> vms = DataLoader.loadVMsFromCSV(vmFilename);

        setInputData(tasks, vms);

        System.out.println("✅ Data loaded successfully");
    }

    /**
     * ALGORITMO PRINCIPALE: Esegue SM-CPTD completo
     * 
     * Sequenza:
     * 1. DCP: identifica Critical Path
     * 2. SMGT: scheduling completo con stable matching
     * 3. LOTD: ottimizzazione con duplicazione
     * 4. Calcolo metriche finali
     * 
     * @param communicationCosts Costi di comunicazione tra task (key: "taskId_succId")
     * @param vmMapping Mapping vmID -> VM object
     * @return Scheduling finale ottimizzato (vmID -> taskIDs)
     */
    public Map<Integer, List<Integer>> executeSMCPTD(
            Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {

        System.out.println("\n" + "=".repeat(70));
        System.out.println("🎯 EXECUTING SM-CPTD ALGORITHM");
        System.out.println("=".repeat(70));

        // Validazione input
        if (smgt.getTasks() == null || smgt.getTasks().isEmpty()) {
            throw new IllegalStateException(
                "Input data not set. Call setInputData() or loadData() first!");
        }

        try {
            // ============================================================
            // STEP 1: DCP - Dynamic Critical Path
            // ============================================================
            System.out.println("\n📍 STEP 1: DCP - Identifying Critical Path");
            System.out.println("-".repeat(70));
            
            criticalPath = executeDCP(communicationCosts, vmMapping);
            
            System.out.println("✅ Critical Path identified:");
            System.out.println("   Size: " + criticalPath.size() + " tasks");
            System.out.println("   Tasks: " + criticalPath);

            // ============================================================
            // STEP 2: SMGT - Stable Matching Scheduling
            // ============================================================
            System.out.println("\n🎮 STEP 2: SMGT - Stable Matching Scheduling");
            System.out.println("-".repeat(70));
            System.out.println("SMGT processes each level:");
            System.out.println("  1. Assigns CP tasks to fastest VM");
            System.out.println("  2. Calculates threshold for remaining tasks");
            System.out.println("  3. Uses stable matching for non-CP tasks");
            
            smgtSchedule = smgt.runSMGT(criticalPath);
            
            System.out.println("\n✅ SMGT scheduling completed:");
            printScheduleSummary(smgtSchedule, "SMGT");

            // ============================================================
            // STEP 3: LOTD - Task Duplication Optimization
            // ============================================================
            System.out.println("\n📋 STEP 3: LOTD - Task Duplication Optimization");
            System.out.println("-".repeat(70));
            
            finalSchedule = executeLOTD(communicationCosts);
            
            System.out.println("\n✅ LOTD optimization completed:");
            printScheduleSummary(finalSchedule, "LOTD");
            
            // Mostra duplicazioni
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

            // ============================================================
            // STEP 4: Calcolo Makespan
            // ============================================================
            System.out.println("\n📊 STEP 4: Calculating Makespan");
            System.out.println("-".repeat(70));
            
            calculateMakespan(vmMapping);

            // ============================================================
            // RISULTATI FINALI
            // ============================================================
            System.out.println("\n" + "=".repeat(70));
            System.out.println("🎉 SM-CPTD COMPLETED SUCCESSFULLY!");
            System.out.println("=".repeat(70));
            printFinalResults();

            return finalSchedule;

        } catch (Exception e) {
            System.err.println("\n❌ ERROR during SM-CPTD execution: " + e.getMessage());
            e.printStackTrace();
            // Fallback: ritorna risultati SMGT se LOTD fallisce
            return smgtSchedule != null ? smgtSchedule : new HashMap<>();
        }
    }

    /**
     * STEP 1: Esecuzione DCP
     * Identifica il Critical Path del DAG
     */
    private Set<Integer> executeDCP(
            Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {

        System.out.println("   🔍 Finding exit task...");
        exitTask = findExitTask(smgt.getTasks());
        System.out.println("   ✓ Exit task: t" + exitTask.getID());

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
     * Helper: Trova exit task (deterministicamente)
     * Se ci sono più exit task, sceglie quello con peso computazionale massimo.
     * In caso di parità, sceglie quello con ID minimo per determinismo.
     */
    private task findExitTask(List<task> tasks) {
        List<task> exits = Utility.findExitTasks(tasks);
        
        if (exits.isEmpty()) {
            throw new IllegalStateException("No exit task found in DAG!");
        }
        
        // Scegli deterministicamente: peso massimo (avg execution time su tutte le VM)
        // In caso di parità, ID minimo
        task exitTask = exits.get(0);
        double maxWeight = calculateTaskWeight(exitTask);
        
        for (task t : exits) {
            if (t == null) continue;
            
            double weight = calculateTaskWeight(t);
            
            // Priorità: peso massimo, poi ID minimo in caso di parità
            if (weight > maxWeight || (weight == maxWeight && t.getID() < exitTask.getID())) {
                maxWeight = weight;
                exitTask = t;
            }
        }
        
        if (exits.size() > 1) {
            System.out.println("   ⚠️  Multiple exit tasks found (" + exits.size() + "), " +
                    "selected t" + exitTask.getID() + " with max weight: " + 
                    String.format("%.3f", maxWeight));
        }
        
        return exitTask;
    }
    
    /**
     * Calcola il peso computazionale di un task (avg execution time su tutte le VM)
     */
    private double calculateTaskWeight(task t) {
        if (smgt.getVMs() == null || smgt.getVMs().isEmpty()) {
            return t.getSize();
        }
        
        double totalTime = 0.0;
        int count = 0;
        
        for (VM vm : smgt.getVMs()) {
            double et = Metrics.ET(t, vm, "processingCapacity");
            if (et != Double.POSITIVE_INFINITY) {
                totalTime += et;
                count++;
            }
        }
        
        return count > 0 ? totalTime / count : t.getSize();
    }

    /**
     * STEP 3: Esecuzione LOTD
     * Ottimizza lo scheduling SMGT tramite duplicazione task
     */
    private Map<Integer, List<Integer>> executeLOTD(
            Map<String, Double> communicationCosts) {

        try {
            System.out.println("   🔧 Initializing LOTD with SMGT schedule...");
            
            lotd = new LOTD(smgt);
            lotd.setCommunicationCosts(communicationCosts);

            System.out.println("   🔧 Running LOTD optimization...");
            Map<Integer, List<Integer>> optimizedSchedule = 
                lotd.executeLOTDCorrect(smgtSchedule);

            return optimizedSchedule;

        } catch (Exception e) {
            System.err.println("   ⚠️  LOTD failed: " + e.getMessage());
            System.err.println("   ↪ Falling back to SMGT schedule");
            e.printStackTrace();
            
            // Fallback: usa scheduling SMGT
            return new HashMap<>(smgtSchedule);
        }
    }

    /**
     * STEP 4: Calcolo Makespan (Paper Formula)
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
     * Stampa sommario di uno schedule
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
     * Stampa risultati finali
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