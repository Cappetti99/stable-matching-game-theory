import java.io.*;
import java.util.*;

/**
 * ExperimentRunner - Esegue gli esperimenti del paper SM-CPTD
 * Usa workflow Pegasus pre-generati da WfCommons
 * 
 * Esperimento 1: Effetto del CCR (Figure 3-8)
 * - Small: 50, 100 task con 5 VM
 * - Medium: 500 task con 10 VM
 * - Large: 1000, 1500 task con 50 VM
 * 
 * Esperimento 2: Effetto del numero di VM (Figure 9-10)
 * - 1000 task con VM variabile: 10, 20, 30, 40, 50
 * - CCR fisso a 1.0
 * 
 * Workflow Pegasus supportati:
 * - MONTAGE: astronomical image mosaic
 * - EPIGENOMICS: genome sequencing pipeline
 * - CYCLES: agricultural simulation (sostituisce LIGO)
 * - SRASEARCH: bioinformatics search (sostituisce CYBERSHAKE)
 * 
 * Metriche: SLR, AVU, VF
 * 
 * @author Lorenzo Cappetti
 */
public class ExperimentRunner {
    
    // Workflow Pegasus XML reali dal paper (convertiti da XML a CSV)
    private static final String[] WORKFLOWS = {"cybershake", "epigenomics", "ligo", "montage"};
    
    // CCR range: 0.4 → 2.0 con step 0.2 (come richiesto dal paper)
    private static final double[] CCR_VALUES = {0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0};
    
    // Experiment 1: CCR effect configurations (basato sui workflow Pegasus XML reali)
    // Configurazioni disponibili: 30-50-100-1000 task
    private static final int[][] SMALL_CONFIGS = {{30, 5}, {50, 5}};  
    private static final int[][] MEDIUM_CONFIGS = {{100, 10}};
    private static final int[][] LARGE_CONFIGS = {{1000, 50}};
    
    // Experiment 2: VM effect configurations
    private static final int[] VM_COUNTS = {10, 20, 30, 40, 50};
    private static final int FIXED_TASKS = 1000;
    private static final double FIXED_CCR = 1.0;
    
    // Results storage
    private static List<ExperimentResult> results = new ArrayList<>();
    
    public static class ExperimentResult {
        String experiment;
        String workflow;
        int numTasks;
        int numVMs;
        double ccr;
        double slr;
        double avu;
        double vf;
        double makespan;
        
        public ExperimentResult(String experiment, String workflow, int numTasks, int numVMs, 
                               double ccr, double slr, double avu, double vf, double makespan) {
            this.experiment = experiment;
            this.workflow = workflow;
            this.numTasks = numTasks;
            this.numVMs = numVMs;
            this.ccr = ccr;
            this.slr = slr;
            this.avu = avu;
            this.vf = vf;
            this.makespan = makespan;
        }
        
        @Override
        public String toString() {
            return String.format(Locale.US, "%s,%s,%d,%d,%.1f,%.4f,%.4f,%.6f,%.4f",
                experiment, workflow, numTasks, numVMs, ccr, slr, avu, vf, makespan);
        }
    }
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║     SM-CPTD PAPER EXPERIMENTS - Full Benchmark Suite          ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Experiment 1: CCR Effect (Figures 3-8)                       ║");
        System.out.println("║  Experiment 2: VM Count Effect (Figures 9-10)                 ║");
        System.out.println("║  Metrics: SLR, AVU, VF                                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();
        
        // Parse arguments
        boolean runExp1 = true;
        boolean runExp2 = true;
        String singleWorkflow = null;
        
        for (String arg : args) {
            if (arg.equals("--exp1")) {
                runExp1 = true;
                runExp2 = false;
            } else if (arg.equals("--exp2")) {
                runExp1 = false;
                runExp2 = true;
            } else if (arg.startsWith("--workflow=")) {
                singleWorkflow = arg.substring(11).toUpperCase();
            }
        }
        
        String[] workflowsToRun = singleWorkflow != null ? 
            new String[]{singleWorkflow} : WORKFLOWS;
        
        try {
            if (runExp1) {
                runExperiment1_CCREffect(workflowsToRun);
            }
            
            if (runExp2) {
                runExperiment2_VMEffect(workflowsToRun);
            }
            
            // Save results
            saveResultsToCSV();
            saveResultsToJSON();
            
            // Print summary
            printSummary();
            
        } catch (Exception e) {
            System.err.println("❌ Errore durante l'esecuzione: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Esperimento 1: Effetto del CCR sui workflow
     */
    private static void runExperiment1_CCREffect(String[] workflows) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📊 ESPERIMENTO 1: Effetto del CCR (Figure 3-8)");
        System.out.println("=".repeat(70));
        
        // Small workflows
        System.out.println("\n📦 SMALL WORKFLOWS (30, 50, 100 task - 5 VM)");
        for (int[] config : SMALL_CONFIGS) {
            runCCRExperiment(workflows, config[0], config[1], "EXP1_SMALL");
        }
        
        // Medium workflows
        System.out.println("\n📦 MEDIUM WORKFLOWS (500 task - 10 VM)");
        for (int[] config : MEDIUM_CONFIGS) {
            runCCRExperiment(workflows, config[0], config[1], "EXP1_MEDIUM");
        }
        
        // Large workflows
        System.out.println("\n📦 LARGE WORKFLOWS (1000, 1500 task - 50 VM)");
        for (int[] config : LARGE_CONFIGS) {
            runCCRExperiment(workflows, config[0], config[1], "EXP1_LARGE");
        }
    }
    
    /**
     * Esperimento 2: Effetto del numero di VM
     */
    private static void runExperiment2_VMEffect(String[] workflows) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📊 ESPERIMENTO 2: Effetto del numero di VM (Figure 9-10)");
        System.out.println("=".repeat(70));
        System.out.println("   Task fissi: " + FIXED_TASKS + ", CCR fisso: " + FIXED_CCR);
        
        for (String workflow : workflows) {
            System.out.println("\n🔬 Workflow: " + workflow);
            
            for (int numVMs : VM_COUNTS) {
                System.out.printf("   VM=%d: ", numVMs);
                
                try {
                    ExperimentResult result = runSingleExperiment(
                        workflow, FIXED_TASKS, numVMs, FIXED_CCR, "EXP2_VM"
                    );
                    results.add(result);
                    System.out.printf("SLR=%.4f, AVU=%.4f, VF=%.6f%n", 
                        result.slr, result.avu, result.vf);
                } catch (Exception e) {
                    System.out.println("❌ Errore: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Esegue esperimenti CCR per una configurazione specifica
     */
    private static void runCCRExperiment(String[] workflows, int numTasks, int numVMs, String expName) {
        for (String workflow : workflows) {
            System.out.println("\n🔬 Workflow: " + workflow + " (" + numTasks + " task, " + numVMs + " VM)");
            
            for (double ccr : CCR_VALUES) {
                System.out.printf("   CCR=%.1f: ", ccr);
                
                try {
                    ExperimentResult result = runSingleExperiment(
                        workflow, numTasks, numVMs, ccr, expName
                    );
                    results.add(result);
                    System.out.printf("SLR=%.4f, AVU=%.4f, VF=%.6f%n", 
                        result.slr, result.avu, result.vf);
                } catch (Exception e) {
                    System.out.println("❌ Errore: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Esegue un singolo esperimento usando workflow Pegasus pre-generati
     */
    private static ExperimentResult runSingleExperiment(String workflow, int numTasks, 
                                                        int numVMs, double ccr, String expName) 
            throws Exception {
        
        // 1. Trova la directory del workflow Pegasus
        String workflowDir;
        if (expName.equals("EXP2_VM")) {
            // Esperimento 2: cerca directory con VM specificato
            workflowDir = findPegasusWorkflowDir(workflow.toLowerCase(), numTasks, numVMs);
        } else {
            // Esperimento 1: cerca solo per numero task
            workflowDir = findPegasusWorkflowDir(workflow.toLowerCase(), numTasks);
        }
        
        if (workflowDir == null) {
            if (expName.equals("EXP2_VM")) {
                System.out.println("⚠️ Workflow non trovato per " + workflow + " " + numTasks + " task, " + numVMs + " VMs");
            } else {
                System.out.println("⚠️ Workflow non trovato per " + workflow + " " + numTasks + " task");
            }
            return null;
        }
        
        // 2. Carica dati in SMGT
        SMGT smgt = new SMGT();
        smgt.loadTasksFromCSV(workflowDir + "/dag.csv", workflowDir + "/task.csv");
        smgt.loadVMsFromCSV(workflowDir + "/processing_capacity.csv");
        
        // 3. Calcola costi di comunicazione con CCR specificato
        Map<String, Double> commCosts = calculateCommunicationCosts(smgt, ccr);
        
        // 4. Crea mapping VM
        Map<Integer, VM> vmMapping = new HashMap<>();
        for (VM vm : smgt.getVMs()) {
            vmMapping.put(vm.getID(), vm);
        }
        
        // 5. Esegui SM-CPTD
        SMCPTD smcptd = new SMCPTD();
        smcptd.loadData(workflowDir + "/dag.csv", workflowDir + "/task.csv", workflowDir + "/processing_capacity.csv");
        
        Map<Integer, List<Integer>> assignments = smcptd.executeSMCPTD(commCosts, vmMapping);
        
        // 6. Calcola metriche
        double makespan = smcptd.getMakespan();
        double slr = calculateSLR(smgt, makespan);
        double avu = calculateAVU(smgt, assignments, makespan);
        double vf = calculateVF(smgt, assignments, makespan);
        
        return new ExperimentResult(expName, workflow, smgt.getTasks().size(), numVMs, ccr, slr, avu, vf, makespan);
    }
    
    /**
     * Trova la directory del workflow Pegasus più vicina al numero di task richiesto
     */
    private static String findPegasusWorkflowDir(String workflow, int targetTasks) {
        return findPegasusWorkflowDir(workflow, targetTasks, -1);
    }
    
    /**
     * Trova la directory del workflow Pegasus con supporto per VM variabili
     */
    private static String findPegasusWorkflowDir(String workflow, int targetTasks, int targetVMs) {
        File pegasusDir = new File("../data_pegasus_xml");
        if (!pegasusDir.exists()) {
            System.out.println("   ⚠️ Directory data_pegasus_xml non trovata: " + pegasusDir.getAbsolutePath());
            return null;
        }
        
        String bestMatch = null;
        int bestDiff = Integer.MAX_VALUE;
        String searchPrefix = workflow.toLowerCase() + "_";
        
        for (File dir : pegasusDir.listFiles()) {
            if (dir.isDirectory() && dir.getName().startsWith(searchPrefix)) {
                String name = dir.getName();
                
                // Se targetVMs > 0, cerca match esatto con VM
                if (targetVMs > 0) {
                    // Formato atteso: workflow_1000tasks_20vms
                    if (!name.contains("vms")) continue;
                    
                    // Estrai numero VM
                    int vmsIdx = name.lastIndexOf("_");
                    String vmsStr = name.substring(vmsIdx + 1).replace("vms", "");
                    try {
                        int vms = Integer.parseInt(vmsStr);
                        if (vms != targetVMs) continue;
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    
                    // Estrai numero task
                    int taskStart = name.indexOf("_") + 1;
                    int taskEnd = name.indexOf("tasks");
                    if (taskStart > 0 && taskEnd > taskStart) {
                        String taskStr = name.substring(taskStart, taskEnd);
                        try {
                            int tasks = Integer.parseInt(taskStr);
                            int diff = Math.abs(tasks - targetTasks);
                            if (diff < bestDiff) {
                                bestDiff = diff;
                                bestMatch = dir.getAbsolutePath();
                            }
                        } catch (NumberFormatException e) {
                            // Ignora
                        }
                    }
                } else {
                    // Ricerca originale: solo per numero task (Esperimento 1)
                    // Ignora directory con "vms" nel nome
                    if (name.contains("vms")) continue;
                    
                    int idx = name.lastIndexOf("_");
                    if (idx > 0) {
                        String taskStr = name.substring(idx + 1).replace("tasks", "");
                        try {
                            int tasks = Integer.parseInt(taskStr);
                            int diff = Math.abs(tasks - targetTasks);
                            if (diff < bestDiff) {
                                bestDiff = diff;
                                bestMatch = dir.getAbsolutePath();
                            }
                        } catch (NumberFormatException e) {
                            // Ignora
                        }
                    }
                }
            }
        }
        
        return bestMatch;
    }
    
    /**
     * Calcola costi di comunicazione basati su CCR
     */
    private static Map<String, Double> calculateCommunicationCosts(SMGT smgt, double ccr) {
        Map<String, Double> costs = new HashMap<>();
        
        // Calcola media computation time
        double avgComputationTime = 0;
        for (task t : smgt.getTasks()) {
            double avgET = 0;
            for (VM vm : smgt.getVMs()) {
                avgET += t.getSize() / vm.getCapability("processingCapacity");
            }
            avgET /= smgt.getVMs().size();
            avgComputationTime += avgET;
        }
        avgComputationTime /= smgt.getTasks().size();
        
        // Costo comunicazione = CCR * avgComputationTime
        double baseCost = ccr * avgComputationTime;
        
        // Assegna costi a tutte le coppie di task connessi
        for (task t : smgt.getTasks()) {
            for (int succId : t.getSucc()) {
                String key = t.getID() + "_" + succId;
                // Variazione ±20% per realismo
                double variation = 0.8 + Math.random() * 0.4;
                costs.put(key, baseCost * variation);
            }
        }
        
        return costs;
    }
    
    /**
     * Calcola SLR (Schedule Length Ratio)
     * SLR = Makespan / CP_min
     * dove CP_min è la somma dei tempi minimi di esecuzione dei task sul critical path
     */
    private static double calculateSLR(SMGT smgt, double makespan) {
        // Trova il critical path e calcola CP_min
        double cpMin = calculateCriticalPathMinTime(smgt);
        
        if (cpMin <= 0) {
            // Fallback: usa la somma di tutti i minET
            cpMin = 0;
            for (task t : smgt.getTasks()) {
                double minET = Double.MAX_VALUE;
                for (VM vm : smgt.getVMs()) {
                    double et = t.getSize() / vm.getCapability("processingCapacity");
                    if (et < minET) minET = et;
                }
                cpMin += minET;
            }
        }
        
        return makespan / cpMin;
    }
    
    /**
     * Calcola il tempo minimo del critical path
     */
    private static double calculateCriticalPathMinTime(SMGT smgt) {
        List<task> tasks = smgt.getTasks();
        List<VM> vms = smgt.getVMs();
        
        // Mappa task ID -> task
        Map<Integer, task> taskMap = new HashMap<>();
        for (task t : tasks) {
            taskMap.put(t.getID(), t);
        }
        
        // Calcola minET per ogni task
        Map<Integer, Double> minET = new HashMap<>();
        for (task t : tasks) {
            double min = Double.MAX_VALUE;
            for (VM vm : vms) {
                double et = t.getSize() / vm.getCapability("processingCapacity");
                if (et < min) min = et;
            }
            minET.put(t.getID(), min);
        }
        
        // Calcola il longest path (critical path) usando programmazione dinamica
        // Per ogni task, calcola il tempo minimo dal task fino alla fine
        Map<Integer, Double> longestPathFrom = new HashMap<>();
        
        // Trova i task di uscita (senza successori)
        List<task> exitTasks = new ArrayList<>();
        for (task t : tasks) {
            if (t.getSucc().isEmpty()) {
                exitTasks.add(t);
            }
        }
        
        // Inizializza task di uscita
        for (task t : exitTasks) {
            longestPathFrom.put(t.getID(), minET.get(t.getID()));
        }
        
        // Ordine topologico inverso (da exit a entry)
        Set<Integer> processed = new HashSet<>(longestPathFrom.keySet());
        boolean changed = true;
        
        while (changed) {
            changed = false;
            for (task t : tasks) {
                if (processed.contains(t.getID())) continue;
                
                // Verifica se tutti i successori sono stati processati
                boolean allSuccProcessed = true;
                for (int succId : t.getSucc()) {
                    if (!processed.contains(succId)) {
                        allSuccProcessed = false;
                        break;
                    }
                }
                
                if (allSuccProcessed) {
                    // Calcola il longest path da questo task
                    double maxSuccPath = 0;
                    for (int succId : t.getSucc()) {
                        double succPath = longestPathFrom.getOrDefault(succId, 0.0);
                        if (succPath > maxSuccPath) {
                            maxSuccPath = succPath;
                        }
                    }
                    longestPathFrom.put(t.getID(), minET.get(t.getID()) + maxSuccPath);
                    processed.add(t.getID());
                    changed = true;
                }
            }
        }
        
        // Il critical path è il massimo tra tutti i task di ingresso
        double cpMin = 0;
        for (task t : tasks) {
            if (t.getPre().isEmpty()) {  // Entry task
                double pathLength = longestPathFrom.getOrDefault(t.getID(), 0.0);
                if (pathLength > cpMin) {
                    cpMin = pathLength;
                }
            }
        }
        
        // Se non ci sono entry task espliciti, prendi il massimo
        if (cpMin == 0) {
            for (Double path : longestPathFrom.values()) {
                if (path > cpMin) cpMin = path;
            }
        }
        
        return cpMin;
    }
    
    /**
     * Calcola AVU (Average VM Utilization)
     */
    private static double calculateAVU(SMGT smgt, Map<Integer, List<Integer>> assignments, double makespan) {
        if (makespan <= 0) return 0;
        
        double totalUtilization = 0;
        int vmCount = 0;
        
        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmId = entry.getKey();
            List<Integer> taskIds = entry.getValue();
            
            VM vm = null;
            for (VM v : smgt.getVMs()) {
                if (v.getID() == vmId) {
                    vm = v;
                    break;
                }
            }
            
            if (vm != null) {
                double busyTime = 0;
                for (int taskId : taskIds) {
                    for (task t : smgt.getTasks()) {
                        if (t.getID() == taskId) {
                            busyTime += t.getSize() / vm.getCapability("processingCapacity");
                            break;
                        }
                    }
                }
                totalUtilization += busyTime / makespan;
                vmCount++;
            }
        }
        
        return vmCount > 0 ? totalUtilization / vmCount : 0;
    }
    
    /**
     * Calcola VF (Variance of Fairness)
     */
    private static double calculateVF(SMGT smgt, Map<Integer, List<Integer>> assignments, double makespan) {
        if (makespan <= 0) return 0;
        
        // Calcola satisfaction per ogni task
        List<Double> satisfactions = new ArrayList<>();
        
        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmId = entry.getKey();
            List<Integer> taskIds = entry.getValue();
            
            VM vm = null;
            for (VM v : smgt.getVMs()) {
                if (v.getID() == vmId) {
                    vm = v;
                    break;
                }
            }
            
            if (vm != null) {
                for (int taskId : taskIds) {
                    for (task t : smgt.getTasks()) {
                        if (t.getID() == taskId) {
                            // Satisfaction = 1 - (actual_ET / min_ET)
                            double actualET = t.getSize() / vm.getCapability("processingCapacity");
                            double minET = Double.MAX_VALUE;
                            for (VM v2 : smgt.getVMs()) {
                                double et = t.getSize() / v2.getCapability("processingCapacity");
                                if (et < minET) minET = et;
                            }
                            double satisfaction = minET / actualET; // 0 to 1
                            satisfactions.add(satisfaction);
                            break;
                        }
                    }
                }
            }
        }
        
        if (satisfactions.isEmpty()) return 0;
        
        // Calcola media
        double mean = 0;
        for (double s : satisfactions) mean += s;
        mean /= satisfactions.size();
        
        // Calcola varianza
        double variance = 0;
        for (double s : satisfactions) {
            variance += Math.pow(s - mean, 2);
        }
        variance /= satisfactions.size();
        
        return variance;
    }
    
    /**
     * Salva risultati in CSV
     */
    private static void saveResultsToCSV() {
        try (PrintWriter writer = new PrintWriter("../results/experiments_results.csv")) {
            writer.println("experiment,workflow,tasks,vms,ccr,slr,avu,vf,makespan");
            for (ExperimentResult r : results) {
                writer.println(r.toString());
            }
            System.out.println("\n✅ Risultati salvati: results/experiments_results.csv");
        } catch (Exception e) {
            System.err.println("❌ Errore salvataggio CSV: " + e.getMessage());
        }
    }
    
    /**
     * Salva risultati in JSON
     */
    private static void saveResultsToJSON() {
        try (PrintWriter writer = new PrintWriter("../results/experiments_results.json")) {
            writer.println("{");
            writer.println("  \"experiments\": [");
            
            for (int i = 0; i < results.size(); i++) {
                ExperimentResult r = results.get(i);
                writer.printf(Locale.US, "    {\"experiment\": \"%s\", \"workflow\": \"%s\", \"tasks\": %d, " +
                             "\"vms\": %d, \"ccr\": %.1f, \"slr\": %.4f, \"avu\": %.4f, " +
                             "\"vf\": %.6f, \"makespan\": %.4f}%s%n",
                    r.experiment, r.workflow, r.numTasks, r.numVMs, r.ccr,
                    r.slr, r.avu, r.vf, r.makespan,
                    i < results.size() - 1 ? "," : "");
            }
            
            writer.println("  ]");
            writer.println("}");
            System.out.println("✅ Risultati salvati: results/experiments_results.json");
        } catch (Exception e) {
            System.err.println("❌ Errore salvataggio JSON: " + e.getMessage());
        }
    }
    
    /**
     * Stampa riepilogo risultati
     */
    private static void printSummary() {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📋 RIEPILOGO ESPERIMENTI");
        System.out.println("=".repeat(70));
        System.out.println("Totale esperimenti eseguiti: " + results.size());
        
        // Raggruppa per esperimento
        Map<String, List<ExperimentResult>> byExperiment = new HashMap<>();
        for (ExperimentResult r : results) {
            byExperiment.computeIfAbsent(r.experiment, k -> new ArrayList<>()).add(r);
        }
        
        for (String exp : byExperiment.keySet()) {
            List<ExperimentResult> expResults = byExperiment.get(exp);
            
            double avgSLR = expResults.stream().mapToDouble(r -> r.slr).average().orElse(0);
            double avgAVU = expResults.stream().mapToDouble(r -> r.avu).average().orElse(0);
            double avgVF = expResults.stream().mapToDouble(r -> r.vf).average().orElse(0);
            
            System.out.printf("\n%s (%d runs):%n", exp, expResults.size());
            System.out.printf("   Avg SLR: %.4f%n", avgSLR);
            System.out.printf("   Avg AVU: %.4f (%.1f%%)%n", avgAVU, avgAVU * 100);
            System.out.printf("   Avg VF:  %.6f%n", avgVF);
        }
        
        System.out.println("\n✅ Esperimenti completati!");
    }
}
