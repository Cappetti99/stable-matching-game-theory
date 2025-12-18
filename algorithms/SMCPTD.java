import java.util.*;
import java.io.*;

/**
 * SM-CPTD: Stable Matching Cloud-based Parallel Task Duplication Algorithm
 * 
 * Questo algoritmo combina tre componenti principali:
 * 1. DCP (Dynamic Critical Path) - Identifica il cammino critico
 * 2. SMGT (Stable Matching Game Theory) - Assegna task alle VM usando game
 * theory
 * 3. LOTD (List of Task Duplication) - Duplica task per ottimizzare le
 * performance
 * 
 * Sequenza di esecuzione: call DCP → call SMGT → call LOTD
 * 
 * @author Lorenzo Cappetti
 * @version 1.0
 */
public class SMCPTD {

    private SMGT smgt;
    private LOTD lotd;

    // Risultati dell'algoritmo
    private Set<Integer> criticalPath;
    private Map<Integer, List<Integer>> smgtAssignments;
    private Map<Integer, List<Integer>> finalAssignments;
    private double makespan;
    private double slr;

    // Dati del DAG (necessari per ricalcolare il critical path)
    private Map<Integer, List<Integer>> taskLevels;
    private task exitTask;

    /**
     * Costruttore dell'algoritmo SM-CPTD
     */
    public SMCPTD() {
        this.smgt = new SMGT();
        this.criticalPath = new HashSet<>();
        this.smgtAssignments = new HashMap<>();
        this.finalAssignments = new HashMap<>();
        this.makespan = 0.0;
        this.slr = 0.0;
    }

    /**
     * Imposta i dati di input (tipicamente forniti da ExperimentRunner) e prepara SMGT.
     * Questo è il percorso consigliato quando i task/VM sono già stati caricati altrove.
     */
    public void setInputData(List<task> tasks, List<VM> vms) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks must be non-empty");
        }
        if (vms == null || vms.isEmpty()) {
            throw new IllegalArgumentException("vms must be non-empty");
        }

        smgt.setTasks(tasks);
        smgt.setVMs(vms);
        smgt.calculateTaskLevels();

        // Reset state derived from previous runs
        this.criticalPath = new HashSet<>();
        this.smgtAssignments = new HashMap<>();
        this.finalAssignments = new HashMap<>();
        this.makespan = 0.0;
        this.slr = 0.0;
        this.taskLevels = null;
        this.exitTask = null;
    }

    /**
     * CLI entry point.
     *
     * Usage:
     * - java SMCPTD
     * - java SMCPTD --workflowDir ../data_pegasus_xml/cybershake_30 --ccr 1.0
     * - java SMCPTD --seed 123 --workflowDir ../data_pegasus_xml/epigenomics_47 --ccr 0.5
     */
    public static void main(String[] args) throws Exception {
        SeededRandom.initFromArgs(args);

        String workflowDir = null;
        double ccr = 1.0;

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                String a = args[i];
                if (a == null) {
                    continue;
                }
                if (a.equals("--workflowDir") && i + 1 < args.length) {
                    workflowDir = args[++i];
                    continue;
                }
                if (a.startsWith("--workflowDir=")) {
                    workflowDir = a.substring("--workflowDir=".length());
                    continue;
                }
                if (a.equals("--ccr") && i + 1 < args.length) {
                    ccr = Double.parseDouble(args[++i]);
                    continue;
                }
                if (a.startsWith("--ccr=")) {
                    ccr = Double.parseDouble(a.substring("--ccr=".length()));
                }
            }
        }

        if (workflowDir == null || workflowDir.isBlank()) {
            workflowDir = "../data_pegasus_xml/cybershake_30";
        }

        String dag = workflowDir + "/dag.csv";
        String taskCsv = workflowDir + "/task.csv";
        String proc = workflowDir + "/processing_capacity.csv";
        String bw = workflowDir + "/bandwidth.csv";

        System.out.println("\n========================");
        System.out.println("SMCPTD ENTRY POINT");
        System.out.println("seed=" + SeededRandom.getSeed() + ", ccr=" + ccr);
        System.out.println("workflowDir=" + workflowDir);
        System.out.println("========================\n");

        SMCPTD smcptd = new SMCPTD();
        smcptd.loadData(dag, taskCsv, proc);

        // Bandwidth is generated deterministically; file is used only for compatibility/scope
        DataLoader.loadBandwidthFromCSV(bw, smcptd.getSMGT().getVMs());

        Map<String, Double> commCosts = calculateCommunicationCosts(smcptd.getSMGT(), ccr);
        Map<Integer, VM> vmMapping = new HashMap<>();
        for (VM vm : smcptd.getSMGT().getVMs()) {
            vmMapping.put(vm.getID(), vm);
        }

        Map<Integer, List<Integer>> finalAssignments = smcptd.executeSMCPTD(commCosts, vmMapping);

        System.out.println("\n=== RESULT ===");
        System.out.println("criticalPathLength=" + smcptd.getCriticalPath().size());
        System.out.printf("makespan=%.6f%n", smcptd.getMakespan());
        System.out.printf("slr=%.6f%n", smcptd.getSLR());
        System.out.println("assignedVMs=" + finalAssignments.size());
    }

    /**
     * Communication cost model consistent with ExperimentRunner:
     * commCost(u->v) = (size(u) * CCR) / avgBandwidth, with avgBandwidth=25.
     */
    private static Map<String, Double> calculateCommunicationCosts(SMGT smgt, double ccr) {
        Map<String, Double> costs = new HashMap<>();

        double avgBandwidth = 25.0;
        for (task t : smgt.getTasks()) {
            List<Integer> succ = t.getSucc();
            if (succ == null) {
                continue;
            }
            for (int succId : succ) {
                String key = t.getID() + "_" + succId;
                double dataSize = t.getSize() * ccr;
                double commCost = dataSize / avgBandwidth;
                costs.put(key, commCost);
            }
        }

        return costs;
    }

    /**
     * Carica i dati necessari per l'esecuzione dell'algoritmo
     * 
     * @param dagFilename  File CSV contenente la struttura del DAG
     * @param taskFilename File CSV contenente i dati dei task
     * @param vmFilename   File CSV contenente le capacità delle VM
     * @throws IOException Se ci sono errori nella lettura dei file
     */
    public void loadData(String dagFilename, String taskFilename, String vmFilename) throws IOException {
        System.out.println("🚀 SM-CPTD: Caricamento dati...");

        // Carica dati usando DataLoader
        List<task> tasks = DataLoader.loadTasksFromCSV(dagFilename, taskFilename);
        List<VM> vms = DataLoader.loadVMsFromCSV(vmFilename);

        // Imposta i dati in SMGT (stesso percorso usato da ExperimentRunner)
        setInputData(tasks, vms);

        System.out.println("✅ Dati caricati: " + tasks.size() + " task, " + vms.size() + " VM");
    }

    /**
     * Esegue l'algoritmo SM-CPTD completo
     * Sequenza: DCP → SMGT → LOTD
     * 
     * @param communicationCosts Mappa dei costi di comunicazione tra task
     * @param vmMapping          Mapping delle VM per ID
     * @return Assegnamenti finali task→VM dopo tutte le ottimizzazioni
     */
    public Map<Integer, List<Integer>> executeSMCPTD(Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {
        System.out.println("\n🎯 ESECUZIONE ALGORITMO SM-CPTD");
        System.out.println("==================================================");

        if (smgt.getTasks() == null || smgt.getTasks().isEmpty() || smgt.getVMs() == null || smgt.getVMs().isEmpty()) {
            throw new IllegalStateException(
                    "Input data not set. Call setInputData(...) or loadData(...) before executeSMCPTD().");
        }

        try {
            // STEP 1: DCP - Identifica il Critical Path
            System.out.println("\n📍 STEP 1: DCP - Dynamic Critical Path");
            Set<Integer> criticalPath = executeDCP(communicationCosts, vmMapping);
            System.out.println("   ✅ Critical Path identificato: " + criticalPath.size() + " task");
            System.out.println("   📋 Task critici: " + criticalPath);

            // STEP 2: SMGT - Stable Matching per scheduling completo
            // SMGT gestisce internamente:
            // - Per ogni livello: prima assegna task CP alla VM più veloce
            // - Poi calcola threshold e assegna task non-CP con stable matching
            System.out.println("\n🎮 STEP 2: SMGT - Stable Matching Scheduling");
            smgtAssignments = smgt.runSMGT(criticalPath);
            System.out.println("   ✅ SMGT completato:");
            for (Map.Entry<Integer, List<Integer>> entry : smgtAssignments.entrySet()) {
                System.out.println("      VM" + entry.getKey() + ": " + entry.getValue().size() + " task");
            }

            // STEP 3: LOTD - Duplicazione task per ottimizzazione
            System.out.println("\n📋 STEP 3: LOTD - List of Task Duplication");
            executeLOTD(communicationCosts);
            System.out.println("   ✅ LOTD completato:");
            for (Map.Entry<Integer, List<Integer>> entry : finalAssignments.entrySet()) {
                System.out.println("      VM" + entry.getKey() + ": " + entry.getValue().size() + " task");
            }

            // STEP 4: Calcolo metriche finali
            System.out.println("\n📊 STEP 4: Calcolo metriche finali");
            calculateFinalMetrics(communicationCosts, vmMapping);

            System.out.println("\n🎉 SM-CPTD COMPLETATO CON SUCCESSO!");
            printResults();

            return finalAssignments;

        } catch (Exception e) {
            System.err.println("❌ Errore durante l'esecuzione SM-CPTD: " + e.getMessage());
            e.printStackTrace();
            return smgtAssignments; // Fallback ai risultati SMGT
        }
    }



    /**
     * STEP 1: Esecuzione DCP (versione standard)
     * Identifica il Critical Path
     */
    private Set<Integer> executeDCP(Map<String, Double> communicationCosts, Map<Integer, VM> vmMapping) {
        System.out.println("   🔍 Identificazione del cammino critico...");

        // Trova exit task e salvalo come campo della classe
        exitTask = findExitTask(smgt.getTasks());

        // Organizza task per livelli e salvalo come campo della classe
        taskLevels = DCP.organizeTasksByLevels(smgt.getTasks());

        // Esegui algoritmo DCP (versione standard)
        Set<Integer> criticalPathSet = DCP.executeDCP(
                smgt.getTasks(), taskLevels, exitTask, communicationCosts, vmMapping);

        // Salva il critical path (anche se executeDCP ritorna un set, lo salviamo in
        // campo per coerenza)
        this.criticalPath = criticalPathSet;

        System.out.println("   ✅ Critical path identificato: " + criticalPathSet.size() + " task critici");
        System.out.println("   📋 Task critici: " + criticalPathSet.toString());

        return criticalPathSet;
    }

    /**
     * Helper: Trova (in modo deterministico) un singolo exit task.
     * In alcuni workflow possono esistere più exit task: scegliamo quello con ID massimo.
     */
    private task findExitTask(List<task> tasks) {
        List<task> exits = Utility.findExitTasks(tasks);
        task best = exits.get(0);
        for (task t : exits) {
            if (t != null && t.getID() > best.getID()) {
                best = t;
            }
        }
        return best;
    }



    /**
     * STEP 3: Esecuzione LOTD - Duplicazione task per ottimizzazione
     */
    private void executeLOTD(Map<String, Double> communicationCosts) {
        try {
            // Crea istanza LOTD con i dati SMGT
            lotd = new LOTD(smgt);
            lotd.setCommunicationCosts(communicationCosts);

            // Esegui algoritmo LOTD sui risultati SMGT
            finalAssignments = lotd.executeLOTDCorrect(smgtAssignments);

            // Mostra duplicati
            Map<Integer, Set<Integer>> duplicates = lotd.getDuplicatedTasks();
            int totalDups = duplicates.values().stream().mapToInt(Set::size).sum();
            if (totalDups > 0) {
                System.out.println("   📋 Task duplicati: " + totalDups);
            }

        } catch (Exception e) {
            System.err.println("   ⚠️  Errore in LOTD, uso risultati SMGT: " + e.getMessage());
            e.printStackTrace();
            finalAssignments = new HashMap<>(smgtAssignments);
        }
    }





    /**
     * STEP 4: Calcolo metriche finali
     */
    private void calculateFinalMetrics(Map<String, Double> communicationCosts, Map<Integer, VM> vmMapping) {
        System.out.println("   📈 Calcolo makespan e SLR...");

        try {
            // CORREZIONE: Calcola makespan usando AFT da LOTD
            // Paper Equazione 6: makespan = max(MS(VMk))
            if (lotd != null && lotd.getTaskAFT() != null && !lotd.getTaskAFT().isEmpty()) {
                Map<Integer, Double> taskAFT = lotd.getTaskAFT();
                makespan = taskAFT.values().stream()
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(0.0);

                System.out.println("   ✓ Makespan from LOTD AFT: " + String.format("%.3f", makespan));
            } else {
                // Fallback: calcolo semplificato
                System.out.println("   ⚠️  LOTD AFT not available, using fallback calculation");
                makespan = calculateMakespanFallback(finalAssignments, smgt.getTasks(), vmMapping, communicationCosts);
            }

            // Calcola SLR usando il critical path corretto
            Map<String, task> taskMap = new HashMap<>();
            for (task t : smgt.getTasks()) {
                taskMap.put("t" + t.getID(), t);
            }

            if (!criticalPath.isEmpty()) {
                double sumMinExecutionTimes = 0.0;
                for (Integer taskId : criticalPath) {
                    task cpTask = taskMap.get("t" + taskId);
                    if (cpTask != null) {
                        double minET = Double.POSITIVE_INFINITY;
                        for (VM vm : vmMapping.values()) {
                            double et = cpTask.getSize() / vm.getCapability("processing");
                            minET = Math.min(minET, et);
                        }
                        if (minET != Double.POSITIVE_INFINITY) {
                            sumMinExecutionTimes += minET;
                        }
                    }
                }

                if (sumMinExecutionTimes > 0) {
                    slr = makespan / sumMinExecutionTimes;
                } else {
                    slr = Double.POSITIVE_INFINITY;
                }
            }

            System.out.println("   ✅ Metriche calcolate: Makespan=" +
                    String.format("%.3f", makespan) + ", SLR=" + String.format("%.3f", slr));

        } catch (Exception e) {
            System.err.println("   ⚠️  Errore calcolo metriche: " + e.getMessage());
            e.printStackTrace();
            makespan = 0.0;
            slr = Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Fallback per calcolo makespan quando LOTD AFT non disponibile
     */
    private double calculateMakespanFallback(Map<Integer, List<Integer>> assignments,
            List<task> tasks,
            Map<Integer, VM> vmMapping,
            Map<String, Double> communicationCosts) {
        double maxFinishTime = 0.0;

        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmId = entry.getKey();
            List<Integer> assignedTasks = entry.getValue();

            if (assignedTasks.isEmpty())
                continue;

            VM vm = vmMapping.get(vmId);
            double vmFinishTime = 0.0;

            for (Integer taskId : assignedTasks) {
                task t = findTaskById(tasks, taskId);
                if (t != null) {
                    double execTime = t.getSize() / vm.getCapability("processing");
                    vmFinishTime += execTime;
                }
            }

            maxFinishTime = Math.max(maxFinishTime, vmFinishTime);
        }

        return maxFinishTime;
    }

    /**
     * Stampa i risultati finali dell'algoritmo SM-CPTD
     */
    private void printResults() {
        System.out.println("\n📋 RISULTATI SM-CPTD:");
        System.out.println("==============================");
        System.out.println("🎯 Critical Path Size: " + criticalPath.size());
        System.out.println("📊 Final Makespan: " + String.format("%.3f", makespan));
        System.out.println("📈 Schedule Length Ratio: " + String.format("%.3f", slr));
        System.out.println("🖥️  VM Assignments:");

        for (Map.Entry<Integer, List<Integer>> entry : finalAssignments.entrySet()) {
            String taskList = entry.getValue().stream()
                    .map(taskId -> "t" + taskId)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(no tasks)");
            System.out.println("   VM" + entry.getKey() + ": " + taskList);
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Trova un task per ID
     */
    private task findTaskById(List<task> tasks, int taskId) {
        for (task t : tasks) {
            if (t.getID() == taskId) {
                return t;
            }
        }
        return null;
    }

    // ==================== GETTERS ====================

    public Set<Integer> getCriticalPath() {
        return criticalPath;
    }

    public Map<Integer, List<Integer>> getSMGTAssignments() {
        return smgtAssignments;
    }

    public Map<Integer, List<Integer>> getFinalAssignments() {
        return finalAssignments;
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

    /**
     * Classe per contenere i risultati dell'algoritmo SM-CPTD
     */
    public static class SMCPTDResult {
        public final double ccr;
        public final double slr;
        public final double makespan;
        public final int criticalPathLength;
        public final String workflowType;
        public final Map<Integer, List<Integer>> assignments;

        public SMCPTDResult(double ccr, double slr, double makespan, int criticalPathLength,
                String workflowType, Map<Integer, List<Integer>> assignments) {
            this.ccr = ccr;
            this.slr = slr;
            this.makespan = makespan;
            this.criticalPathLength = criticalPathLength;
            this.workflowType = workflowType;
            this.assignments = new HashMap<>(assignments);
        }

        @Override
        public String toString() {
            return String.format("SMCPTDResult{ccr=%.2f, slr=%.3f, makespan=%.3f, workflow=%s}",
                    ccr, slr, makespan, workflowType);
        }
    }
}
