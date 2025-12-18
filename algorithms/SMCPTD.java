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
    private double slr;

    // Dati necessari per il calcolo
    private task exitTask;
    private Map<Integer, List<Integer>> taskLevels;

    /**
     * Costruttore
     */
    public SMCPTD() {
        this.smgt = new SMGT();
        this.criticalPath = new HashSet<>();
        this.smgtSchedule = new HashMap<>();
        this.finalSchedule = new HashMap<>();
        this.makespan = 0.0;
        this.slr = 0.0;
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
        this.slr = 0.0;
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
            // STEP 4: Calcolo Metriche Finali
            // ============================================================
            System.out.println("\n📊 STEP 4: Calculating Final Metrics");
            System.out.println("-".repeat(70));
            
            calculateFinalMetrics(communicationCosts, vmMapping);

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
        taskLevels = DCP.organizeTasksByLevels(smgt.getTasks());
        System.out.println("   ✓ DAG has " + taskLevels.size() + " levels");

        System.out.println("   🔍 Running DCP algorithm...");
        Set<Integer> cp = DCP.executeDCP(
            smgt.getTasks(),
            taskLevels,
            exitTask,
            communicationCosts,
            vmMapping
        );

        return cp;
    }

    /**
     * Helper: Trova exit task (deterministicamente)
     * Se ci sono più exit task, sceglie quello con ID massimo
     */
    private task findExitTask(List<task> tasks) {
        List<task> exits = Utility.findExitTasks(tasks);
        
        if (exits.isEmpty()) {
            throw new IllegalStateException("No exit task found in DAG!");
        }
        
        // Scegli deterministicamente: ID massimo
        task exitTask = exits.get(0);
        for (task t : exits) {
            if (t != null && t.getID() > exitTask.getID()) {
                exitTask = t;
            }
        }
        
        return exitTask;
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
     * STEP 4: Calcolo metriche finali
     * - Makespan: tempo totale di esecuzione
     * - SLR: Schedule Length Ratio (Paper Eq. 7)
     */
    private void calculateFinalMetrics(
            Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {

        System.out.println("   📈 Calculating makespan...");

        // Calcola makespan usando AFT da LOTD
        if (lotd != null && lotd.getTaskAFT() != null && !lotd.getTaskAFT().isEmpty()) {
            Map<Integer, Double> taskAFT = lotd.getTaskAFT();
            makespan = taskAFT.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);

            System.out.println("   ✓ Makespan calculated from LOTD AFT: " + 
                String.format("%.3f", makespan));
        } else {
            // Fallback: calcolo semplificato
            System.out.println("   ⚠️  LOTD AFT not available, using fallback");
            makespan = calculateMakespanFallback();
        }

        // Calcola SLR (Paper Equation 7)
        // SLR = makespan / Σ(min_ET(task_i) for task_i in CP)
        System.out.println("   📈 Calculating SLR...");
        
        if (!criticalPath.isEmpty()) {
            double sumMinET = 0.0;
            
            for (Integer taskId : criticalPath) {
                task cpTask = smgt.getTaskById(taskId);
                if (cpTask != null) {
                    // Trova minimum execution time tra tutte le VM
                    double minET = Double.POSITIVE_INFINITY;
                    for (VM vm : vmMapping.values()) {
                        double capacity = vm.getCapability("processingCapacity");
                        if (capacity <= 0) {
                            capacity = vm.getCapability("processing");
                        }
                        if (capacity > 0) {
                            double et = cpTask.getSize() / capacity;
                            minET = Math.min(minET, et);
                        }
                    }
                    
                    if (minET != Double.POSITIVE_INFINITY) {
                        sumMinET += minET;
                    }
                }
            }

            if (sumMinET > 0) {
                slr = makespan / sumMinET;
                System.out.println("   ✓ SLR calculated: " + String.format("%.3f", slr));
            } else {
                slr = Double.POSITIVE_INFINITY;
                System.out.println("   ⚠️  Invalid SLR calculation (sumMinET = 0)");
            }
        } else {
            System.out.println("   ⚠️  Empty critical path, cannot calculate SLR");
            slr = Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Fallback per calcolo makespan quando LOTD AFT non è disponibile
     */
    private double calculateMakespanFallback() {
        double maxFinishTime = 0.0;

        for (Map.Entry<Integer, List<Integer>> entry : finalSchedule.entrySet()) {
            int vmId = entry.getKey();
            List<Integer> assignedTasks = entry.getValue();

            if (assignedTasks.isEmpty()) continue;

            VM vm = null;
            for (VM v : smgt.getVMs()) {
                if (v.getID() == vmId) {
                    vm = v;
                    break;
                }
            }

            if (vm == null) continue;

            double vmFinishTime = 0.0;
            double capacity = vm.getCapability("processingCapacity");
            if (capacity <= 0) {
                capacity = vm.getCapability("processing");
            }

            for (Integer taskId : assignedTasks) {
                task t = smgt.getTaskById(taskId);
                if (t != null && capacity > 0) {
                    double execTime = t.getSize() / capacity;
                    vmFinishTime += execTime;
                }
            }

            maxFinishTime = Math.max(maxFinishTime, vmFinishTime);
        }

        return maxFinishTime;
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
        System.out.println("   📈 SLR: " + String.format("%.3f", slr));
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

    public double getSLR() {
        return slr;
    }

    public SMGT getSMGT() {
        return smgt;
    }
}