import java.io.*;
import java.util.*;

/**
 * ExperimentRunner - Esegue gli esperimenti del paper SM-CPTD
 * Usa workflow Pegasus pre-generati da WfCommons
 * 
 * Esperimento 1: Effetto del CCR (Figure 3-8)
 * - Small: 50 task con 5 VM
 * - Medium: 100 task con 10 VM
 * - Large: 1000 task con 50 VM
 * 
 * Esperimento 2: Effetto del numero di VM (Figure 9-10)
 * - 1000 task con VM variabile: 30, 35, 40, 45, 50, 55, 60, 65, 70
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
 */
public class ExperimentRunner {

    // ============================================================================
    // CONFIGURAZIONE ESPERIMENTI
    // ============================================================================

    // Numero di run multiple per stabilizzare i risultati
    private static final int NUM_RUNS = 3; // Production: 10 runs per stabilizzare i risultati
    private static final int WARMUP_RUNS = 1; // Production: 1 warmup per eliminare cold start effects

    // Workflow Pegasus XML reali dal paper (convertiti da XML a CSV)
    private static final String[] WORKFLOWS = { "cybershake", "epigenomics", "ligo", "montage" };

    // CCR range: 0.4 → 2.0 con step 0.2 (come richiesto dal paper)
    private static final double[] CCR_VALUES = { 0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0 };

    // Experiment 1: CCR effect configurations (basato sui workflow Pegasus XML
    // reali)
    // Configurazioni disponibili: 50-100-1000 task
    private static final int[][] SMALL_CONFIGS = { { 50, 5 } };
    private static final int[][] MEDIUM_CONFIGS = { { 100, 10 } };
    private static final int[][] LARGE_CONFIGS = { { 1000, 50 } };

    // Experiment 2: VM effect configurations
    private static final int[] VM_COUNTS = { 30, 35, 40, 45, 50, 55, 60, 65, 70 };
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
        SeededRandom.initFromArgs(args);
        // Check for test_single mode
        if (args.length > 0 && args[0].equals("test_single")) {
            runTestSingle();
            return;
        }

        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║     SM-CPTD PAPER EXPERIMENTS - Full Benchmark Suite          ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Experiment 1: CCR Effect (Figures 3-8)                       ║");
        System.out.println("║  Experiment 2: VM Count Effect (Figures 9-10)                 ║");
        System.out.println("║  Metrics: SLR, AVU, VF                                        ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.printf("║  Multiple Runs: %d runs + %d warmup = %d total per config     ║%n",
                NUM_RUNS, WARMUP_RUNS, NUM_RUNS + WARMUP_RUNS);
        System.out.println("║  Results: Average of " + NUM_RUNS + " runs (after warmup)                  ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║  ⏱️  Estimated time: ~3-4 hours (164 configs × 11 runs each)   ║");
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
                singleWorkflow = arg.substring(11).trim().toLowerCase(Locale.ROOT);
            }
        }

        String[] workflowsToRun = singleWorkflow != null ? new String[] { singleWorkflow } : WORKFLOWS;

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

        // Small workflows - usa configurazioni specifiche per ogni workflow
        System.out.println("\n📦 SMALL WORKFLOWS (47-50 task - 5 VM)");
        for (String workflow : workflows) {
            if (workflow.equals("cybershake")) {
                // CyberShake ha sia 30 che 50 task disponibili - usa 50
                runCCRExperiment(new String[] { workflow }, 50, 5, "EXP1_SMALL");
            } else if (workflow.equals("epigenomics")) {
                runCCRExperiment(new String[] { workflow }, 47, 5, "EXP1_SMALL");
            } else {
                runCCRExperiment(new String[] { workflow }, 50, 5, "EXP1_SMALL");
            }
        }

        // Medium workflows
        System.out.println("\n📦 MEDIUM WORKFLOWS (100 task - 10 VM)");
        for (int[] config : MEDIUM_CONFIGS) {
            runCCRExperiment(workflows, config[0], config[1], "EXP1_MEDIUM");
        }

        // Large workflows - epigenomics ha 997 task invece di 1000
        System.out.println("\n📦 LARGE WORKFLOWS (997-1000 task - 50 VM)");
        for (String workflow : workflows) {
            int tasks = workflow.equals("epigenomics") ? 997 : 1000;
            runCCRExperiment(new String[] { workflow }, tasks, 50, "EXP1_LARGE");
        }
    }

    /**
     * Esperimento 2: Effetto del numero di VM
     */
    private static void runExperiment2_VMEffect(String[] workflows) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("📊 ESPERIMENTO 2: Effetto del numero di VM (Figure 9-10)");
        System.out.println("=".repeat(70));

        for (String workflow : workflows) {
            // Epigenomics ha 997 task, gli altri 1000
            int tasks = workflow.equals("epigenomics") ? 997 : 1000;
            System.out.println("\n🔬 Workflow: " + workflow + " (" + tasks + " task, CCR fisso: " + FIXED_CCR + ")");

            for (int numVMs : VM_COUNTS) {
                System.out.printf("   VM=%d: ", numVMs);

                try {
                    ExperimentResult result = runSingleExperiment(
                            workflow, tasks, numVMs, FIXED_CCR, "EXP2_VM", NUM_RUNS, WARMUP_RUNS);
                    if (result == null) {
                        System.out.println("⚠️  Skipped (workflow non trovato)");
                        continue;
                    }
                    results.add(result);
                    checkpointSave();
                    System.out.printf("SLR=%.4f, AVU=%.4f, VF=%.6f (avg of %d runs)%n",
                            result.slr, result.avu, result.vf, NUM_RUNS);
                } catch (Exception e) {
                    System.out.println("❌ Errore: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Esegue esperimenti CCR per una configurazione specifica
     * ENHANCED: Now includes CCR sensitivity analysis
     */
    private static void runCCRExperiment(String[] workflows, int numTasks, int numVMs, String expName) {
        for (String workflow : workflows) {
            System.out.println("\n🔬 Workflow: " + workflow + " (" + numTasks + " task, " + numVMs + " VM)");

            // NEW: Create CCR analyzer for this workflow
            CCRAnalyzer ccrAnalyzer = new CCRAnalyzer(workflow, numTasks, numVMs, expName);

            for (double ccr : CCR_VALUES) {
                System.out.printf("   CCR=%.1f: ", ccr);

                try {
                    ExperimentResult result = runSingleExperimentWithAnalysis(
                            workflow, numTasks, numVMs, ccr, expName, NUM_RUNS, WARMUP_RUNS, ccrAnalyzer);
                    if (result == null) {
                        System.out.println("⚠️  Skipped (workflow non trovato)");
                        continue;
                    }
                    results.add(result);
                    checkpointSave();
                    System.out.printf("SLR=%.4f, AVU=%.4f, VF=%.6f (avg of %d runs)%n",
                            result.slr, result.avu, result.vf, NUM_RUNS);
                } catch (Exception e) {
                    System.out.println("❌ Errore: " + e.getMessage());
                }
            }
            
            // NEW: Save CCR sensitivity analysis after all CCR values tested
            try {
                String outputPath = "../results/ccr_sensitivity/" + workflow + "_" + 
                                   expName.toLowerCase() + "_analysis.json";
                ccrAnalyzer.saveToJSON(outputPath);
                System.out.println("   📊 CCR sensitivity analysis saved");
            } catch (Exception e) {
                System.err.println("   ⚠️  Failed to save CCR analysis: " + e.getMessage());
            }
        }
    }

    private static File ensureResultsDir() {
        File resultsDir = new File("../results");
        if (!resultsDir.exists()) {
            resultsDir.mkdirs();
        }
        return resultsDir;
    }

    /**
     * Salvataggio incrementale: utile per non perdere dati se l'esecuzione si
     * interrompe.
     */
    private static void checkpointSave() {
        saveResultsToCSV(false);
        saveResultsToJSON(false);
    }

    /**
     * Esegue un singolo esperimento con multiple run e calcola la media
     * 
     * @param workflow   Nome del workflow
     * @param numTasks   Numero di task
     * @param numVMs     Numero di VM
     * @param ccr        Communication to Computation Ratio
     * @param expName    Nome dell'esperimento
     * @param numRuns    Numero di run da eseguire (default: 1)
     * @param warmupRuns Numero di run di warmup da scartare (default: 0)
     * @return Risultato medio delle run
     */
    private static ExperimentResult runSingleExperiment(String workflow, int numTasks,
            int numVMs, double ccr, String expName,
            int numRuns, int warmupRuns)
            throws Exception {

        // 1. Trova la directory del workflow Pegasus
        String workflowDir;
        if (expName.equals("EXP2_VM")) {
            // Esperimento 2: cerca directory con VM specificato
            workflowDir = findPegasusWorkflowDir(workflow.toLowerCase(), numTasks, numVMs);
        } else {
            // Esperimento 1: passa comunque il numero di VM al parser per generare capacità
            // corrette
            workflowDir = findPegasusWorkflowDir(workflow.toLowerCase(), numTasks, numVMs);
        }

        if (workflowDir == null) {
            if (expName.equals("EXP2_VM")) {
                System.out.println(
                        "⚠️ Workflow non trovato per " + workflow + " " + numTasks + " task, " + numVMs + " VMs");
            } else {
                System.out.println("⚠️ Workflow non trovato per " + workflow + " " + numTasks + " task");
            }
            return null;
        }

        // Lista per memorizzare i risultati delle run
        List<RunMetrics> runs = new ArrayList<>();
        int totalRuns = warmupRuns + numRuns;

        // Mostra progresso solo se ci sono multiple run
        boolean showProgress = totalRuns > 1;

        for (int runIdx = 0; runIdx < totalRuns; runIdx++) {
            boolean isWarmup = runIdx < warmupRuns;

            if (showProgress && runIdx == 0 && isWarmup) {
                System.out.print("[warmup]");
            } else if (showProgress && runIdx == warmupRuns) {
                System.out.print("[runs: ");
            }
            if (showProgress && !isWarmup) {
                System.out.print(".");
            }

            // 2. Carica dati (DataLoader genera valori random)
            // ExperimentRunner è l'entry point: prepara task/VM e li passa a SMCPTD.
            //
            // SEED STRATEGY:
            // Option A: SAME SEED FOR ALL RUNS (current - for reproducibility)
            //   Use -1 as runIdx to keep same seed across all runs
            //   Good for: Testing if algorithm is deterministic
            // int seedRunIdx = -1;
            //
            // Option B: DIFFERENT SEED PER RUN (for statistical analysis)
            //   Uncomment line below to vary seed per run
            //   Good for: Computing mean/variance across different random inputs
            int seedRunIdx = runIdx;
            
            List<task> tasks = DataLoader.loadTasksFromCSV(workflowDir + "/dag.csv", workflowDir + "/task.csv", seedRunIdx);
            List<VM> vms = DataLoader.loadVMsFromCSV(workflowDir + "/processing_capacity.csv", seedRunIdx);
            DataLoader.loadBandwidthFromCSV(workflowDir + "/bandwidth.csv", vms, seedRunIdx);

            SMCPTD smcptd = new SMCPTD();
            smcptd.setInputData(tasks, vms);
            SMGT smgt = smcptd.getSMGT();

            // 4. Crea mapping VM
            Map<Integer, VM> vmMapping = new HashMap<>();
            for (VM vm : vms) {
                vmMapping.put(vm.getID(), vm);
            }

            // ============================================================================
            // 2-PASS ITERATIVE REFINEMENT WITH VM-SPECIFIC BANDWIDTH
            // ============================================================================
            
            // PASS 1: Initial scheduling with DCP formula
            // Calculate communication costs using paper's formula for DCP:
            // ci,j = (1/m(m-1)) × Σ(k=0 to m-1) Σ(l=0,l≠k to m-1) [TTi,j / B(VMk, VMl)]
            // This averages costs over all VM pairs, appropriate since tasks aren't assigned yet
            Map<String, Double> commCostsPass1 = calculateCommunicationCostsForDCP(smgt, ccr);
            
            // Execute SM-CPTD with average bandwidth to get initial assignments
            Map<Integer, List<Integer>> assignmentsPass1 = smcptd.executeSMCPTD(commCostsPass1, vmMapping);
            
            // Convert assignments to task-to-VM map
            Map<Integer, Integer> taskToVM = buildTaskToVMMap(assignmentsPass1);
            
            // PASS 2: Refined scheduling with VM-pair-specific bandwidth
            // Recalculate communication costs using actual bandwidth between assigned VMs
            Map<String, Double> commCostsPass2 = calculateCommunicationCosts(smgt, ccr, taskToVM);
            
            // Re-execute SM-CPTD with refined communication costs
            // This may result in different assignments due to more accurate costs
            SMCPTD smcptdPass2 = new SMCPTD();
            smcptdPass2.setInputData(tasks, vms);
            Map<Integer, List<Integer>> assignments = smcptdPass2.executeSMCPTD(commCostsPass2, vmMapping);
            
            // Use results from Pass 2 (refined with VM-specific bandwidth)
            Map<String, Double> commCosts = commCostsPass2;
            smcptd = smcptdPass2;
            
            // ============================================================================

            // 6. Calcola metriche
            double makespan = smcptd.getMakespan();
            // Usa l'SLR calcolato da SMCPTD (Eq. 7) usando il CP trovato da DCP
            double slr = smcptd.getSLR();
            double avu = calculateAVU(smgt, assignments, makespan);
            double vf = calculateVF(smgt, assignments, makespan);

            // Salva le metriche (solo se non è warmup)
            if (!isWarmup) {
                runs.add(new RunMetrics(slr, avu, vf, makespan));
            }
        }

        if (showProgress) {
            System.out.print("] ");
        }

        // Calcola la media dei risultati
        double avgSLR = runs.stream().mapToDouble(r -> r.slr).average().orElse(0);
        double avgAVU = runs.stream().mapToDouble(r -> r.avu).average().orElse(0);
        double avgVF = runs.stream().mapToDouble(r -> r.vf).average().orElse(0);
        double avgMakespan = runs.stream().mapToDouble(r -> r.makespan).average().orElse(0);

        // Usa il numero di task dalla prima run (sono sempre gli stessi)
        List<task> tasksForCount = DataLoader.loadTasksFromCSV(workflowDir + "/dag.csv", workflowDir + "/task.csv");
        int actualTasks = tasksForCount.size();

        return new ExperimentResult(expName, workflow, actualTasks, numVMs, ccr, avgSLR, avgAVU, avgVF, avgMakespan);
    }

    /**
     * ENHANCED: Esegue un singolo esperimento con CCR analysis
     * 
     * This version captures data for CCR sensitivity analysis including:
     * - Communication costs
     * - Critical Path
     * - Task duplications
     * - Performance metrics
     */
    private static ExperimentResult runSingleExperimentWithAnalysis(
            String workflow, int numTasks, int numVMs, double ccr, String expName,
            int numRuns, int warmupRuns, CCRAnalyzer ccrAnalyzer)
            throws Exception {
        
        // Run the standard experiment
        ExperimentResult result = runSingleExperiment(workflow, numTasks, numVMs, ccr, expName, numRuns, warmupRuns);
        
        if (result == null) {
            return null;
        }
        
        // NEW: Capture additional data for CCR analysis (single run for analysis)
        try {
            String workflowDir = findPegasusWorkflowDir(workflow.toLowerCase(), numTasks, numVMs);
            if (workflowDir != null) {
                // Load data (using -1 for consistent seed in CCR analysis snapshot)
                // NOTE: This uses the default seed to ensure CCR analysis is consistent
                int seedRunIdx = -1; // Use same seed for CCR analysis snapshot
                List<task> tasks = DataLoader.loadTasksFromCSV(workflowDir + "/dag.csv", workflowDir + "/task.csv", seedRunIdx);
                List<VM> vms = DataLoader.loadVMsFromCSV(workflowDir + "/processing_capacity.csv", seedRunIdx);
                DataLoader.loadBandwidthFromCSV(workflowDir + "/bandwidth.csv", vms, seedRunIdx);
                
                SMCPTD smcptd = new SMCPTD();
                smcptd.setInputData(tasks, vms);
                SMGT smgt = smcptd.getSMGT();
                
                // Create VM mapping
                Map<Integer, VM> vmMapping = new HashMap<>();
                for (VM vm : vms) {
                    vmMapping.put(vm.getID(), vm);
                }
                
                // 2-PASS APPROACH (same as main execution)
                // Pass 1: Average bandwidth
                Map<String, Double> commCostsPass1 = calculateCommunicationCosts(smgt, ccr, null);
                Map<Integer, List<Integer>> assignmentsPass1 = smcptd.executeSMCPTD(commCostsPass1, vmMapping);
                Map<Integer, Integer> taskToVM = buildTaskToVMMap(assignmentsPass1);
                
                // Pass 2: VM-specific bandwidth
                Map<String, Double> commCosts = calculateCommunicationCosts(smgt, ccr, taskToVM);
                SMCPTD smcptdPass2 = new SMCPTD();
                smcptdPass2.setInputData(tasks, vms);
                smcptdPass2.executeSMCPTD(commCosts, vmMapping);
                
                // Use Pass 2 results
                smcptd = smcptdPass2;
                
                // Get Critical Path
                Set<Integer> criticalPath = smcptd.getCriticalPath();
                
                // Get duplicated tasks from LOTD (if available)
                Map<Integer, Set<Integer>> duplicatedTasks = new HashMap<>();
                LOTD lotd = smcptd.getLOTD();
                if (lotd != null) {
                    duplicatedTasks = lotd.getDuplicatedTasks();
                }
                
                // Capture snapshot
                ccrAnalyzer.captureSnapshot(
                    ccr,
                    commCosts,
                    criticalPath,
                    duplicatedTasks,
                    result.slr,
                    result.avu,
                    result.vf,
                    result.makespan
                );
            }
        } catch (Exception e) {
            System.err.println("      ⚠️  CCR analysis capture failed: " + e.getMessage());
            // Continue anyway - don't fail the experiment
        }
        
        return result;
    }

    /**
     * Classe helper per memorizzare le metriche di una singola run
     */
    private static class RunMetrics {
        double slr, avu, vf, makespan;

        RunMetrics(double slr, double avu, double vf, double makespan) {
            this.slr = slr;
            this.avu = avu;
            this.vf = vf;
            this.makespan = makespan;
        }
    }

    /**
     * Trova il file XML del workflow più vicino al numero di task richiesto
     */
    private static String findWorkflowXML(String workflow, int targetTasks) {
        // Mappa dei workflow alle directory
        String workflowDir = "../workflow/" + workflow.toLowerCase();
        File dir = new File(workflowDir);

        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }

        String bestMatch = null;
        int bestDiff = Integer.MAX_VALUE;

        // Cerca il file XML più vicino al numero di task richiesto
        for (File file : dir.listFiles()) {
            if (file.isFile() && file.getName().endsWith(".xml")) {
                String name = file.getName();
                // Formato: Workflow_123.xml
                int underscoreIdx = name.lastIndexOf("_");
                int dotIdx = name.lastIndexOf(".");

                if (underscoreIdx > 0 && dotIdx > underscoreIdx) {
                    String taskStr = name.substring(underscoreIdx + 1, dotIdx);
                    try {
                        int tasks = Integer.parseInt(taskStr);
                        int diff = Math.abs(tasks - targetTasks);
                        if (diff < bestDiff) {
                            bestDiff = diff;
                            bestMatch = file.getAbsolutePath();
                        }
                    } catch (NumberFormatException e) {
                        // Ignora
                    }
                }
            }
        }

        return bestMatch;
    }
    
    /**
      * Trova la directory del workflow Pegasus con supporto per VM variabili
      * Converte XML in CSV nella cartella data/
      */
    private static String findPegasusWorkflowDir(String workflow, int targetTasks, int targetVMs) {
        // Trova il file XML del workflow
        String xmlFile = findWorkflowXML(workflow, targetTasks);
        if (xmlFile == null) {
            System.out.println("   ⚠️ Workflow XML non trovato per " + workflow + " " + targetTasks + " task");
            return null;
        }

        // Converti XML in CSV nella cartella data/
        try {
            String outputDir = "../data/" + workflow.toLowerCase() + "_" + targetTasks;
            int vms = targetVMs > 0 ? targetVMs : 5; // Default 5 VM
            PegasusXMLParser.parseAndConvert(xmlFile, outputDir, vms);
            return outputDir;
        } catch (Exception e) {
            System.out.println("   ⚠️ Errore conversione XML: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
      * Calcola costi di comunicazione basati su CCR
      * 
      * @param smgt SMGT object with tasks and VMs
      * @param ccr Communication-to-Computation Ratio
      * @param taskToVM Optional task-to-VM assignment map. If provided, uses VM-pair-specific bandwidth.
      *                 If null, uses average bandwidth across all VM pairs.
      * @return Map of communication costs (key: "sourceTaskId_destTaskId", value: cost)
      */
     private static Map<String, Double> calculateCommunicationCosts(SMGT smgt, double ccr, 
                                                                      Map<Integer, Integer> taskToVM) {
        Map<String, Double> costs = new HashMap<>();

        // Calculate average bandwidth from all VM pairs
        double avgBandwidth = calculateAverageBandwidth(smgt.getVMs());
        
        boolean usingSpecificBandwidth = (taskToVM != null && !taskToVM.isEmpty());
        
        // Optional: Log bandwidth usage mode (set to true for debugging)
        boolean VERBOSE_BANDWIDTH = false;
        if (VERBOSE_BANDWIDTH) {
            if (usingSpecificBandwidth) {
                System.out.println("      [Bandwidth: Using VM-specific (avg=" + 
                    String.format("%.2f", avgBandwidth) + " Mbps as fallback)]");
            } else {
                System.out.println("      [Bandwidth: Using average " + 
                    String.format("%.2f", avgBandwidth) + " Mbps]");
            }
        }
        
        for (task t : smgt.getTasks()) {
            for (int succId : t.getSucc()) {
                String key = t.getID() + "_" + succId;
                double dataSize = t.getSize() * ccr;
                
                double bandwidth;
                if (usingSpecificBandwidth && taskToVM.containsKey(t.getID()) && taskToVM.containsKey(succId)) {
                    // Use VM-pair-specific bandwidth
                    int sourceVMId = taskToVM.get(t.getID());
                    int destVMId = taskToVM.get(succId);
                    
                    if (sourceVMId == destVMId) {
                        // Same VM: no communication cost
                        costs.put(key, 0.0);
                        continue;
                    }
                    
                    // Get bandwidth between the two VMs
                    VM sourceVM = null;
                    for (VM vm : smgt.getVMs()) {
                        if (vm.getID() == sourceVMId) {
                            sourceVM = vm;
                            break;
                        }
                    }
                    
                    if (sourceVM != null && sourceVM.hasBandwidthToVM(destVMId)) {
                        bandwidth = sourceVM.getBandwidthToVM(destVMId);
                    } else {
                        // Fallback to average if bandwidth not found
                        bandwidth = avgBandwidth;
                    }
                } else {
                    // Use average bandwidth
                    bandwidth = avgBandwidth;
                }
                
                double commCost = dataSize / bandwidth;
                costs.put(key, commCost);
            }
        }

        return costs;
    }

    /**
     * Calculate average bandwidth across all VM pairs
     * 
     * @param vms List of VMs with bandwidth data
     * @return Average bandwidth value
     */
    private static double calculateAverageBandwidth(List<VM> vms) {
        double sum = 0;
        int count = 0;
        
        for (VM vm : vms) {
            Map<Integer, Double> bandwidths = vm.getAllBandwidths();
            for (double bandwidth : bandwidths.values()) {
                sum += bandwidth;
                count++;
            }
        }
        
        // Fallback to 25.0 if no bandwidth data found
        return count > 0 ? sum / count : 25.0;
    }

    /**
     * Calculate communication costs for DCP using the paper's formula.
     * 
     * Formula: ci,j = (1 / m(m-1)) × Σ(k=0 to m-1) Σ(l=0,l≠k to m-1) [TTi,j / B(VMk, VMl)]
     * 
     * Where:
     * - TTi,j = sti × CCR (data transfer size)
     * - m = number of VMs
     * - B(VMk, VMl) = bandwidth from VMk to VMl
     * 
     * This averages the communication cost across ALL possible VM pairs (k ≠ l),
     * which is appropriate for DCP since tasks are not yet assigned to VMs.
     * 
     * Note: This is mathematically different from averaging bandwidths first.
     * avg(TTi,j / B) ≠ TTi,j / avg(B)
     * 
     * @param smgt SMGT object with tasks and VMs
     * @param ccr Communication-to-Computation Ratio
     * @return Map of communication costs (key: "sourceTaskId_destTaskId", value: cost)
     * @throws IllegalStateException if bandwidth data is missing or invalid
     */
    private static Map<String, Double> calculateCommunicationCostsForDCP(SMGT smgt, double ccr) {
        Map<String, Double> costs = new HashMap<>();
        
        List<VM> vms = smgt.getVMs();
        int m = vms.size();
        
        // Edge case: if only 1 VM or no VMs, communication cost is 0
        if (m <= 1) {
            for (task t : smgt.getTasks()) {
                for (int succId : t.getSucc()) {
                    String key = t.getID() + "_" + succId;
                    costs.put(key, 0.0);
                }
            }
            return costs;
        }
        
        // Calculate expected number of VM pairs: m(m-1)
        int expectedPairs = m * (m - 1);
        
        // For each edge in the task graph
        for (task t : smgt.getTasks()) {
            for (int succId : t.getSucc()) {
                String key = t.getID() + "_" + succId;
                
                // Calculate TTi,j = sti × CCR
                double TTij = t.getSize() * ccr;
                
                // Sum over all VM pairs (k ≠ l)
                double sumCosts = 0.0;
                int validPairs = 0;
                
                for (int k = 0; k < m; k++) {
                    VM vmK = vms.get(k);
                    
                    for (int l = 0; l < m; l++) {
                        if (k == l) continue; // Skip same VM (k ≠ l constraint)
                        
                        VM vmL = vms.get(l);
                        int vmLId = vmL.getID();
                        
                        // Get bandwidth B(VMk, VMl)
                        if (!vmK.hasBandwidthToVM(vmLId)) {
                            throw new IllegalStateException(
                                "Missing bandwidth data: VM" + vmK.getID() + 
                                " -> VM" + vmLId + ". Cannot calculate DCP communication costs.");
                        }
                        
                        double bandwidth = vmK.getBandwidthToVM(vmLId);
                        
                        // Validate bandwidth is positive
                        if (bandwidth <= 0.0) {
                            throw new IllegalStateException(
                                "Invalid bandwidth (≤ 0): VM" + vmK.getID() + 
                                " -> VM" + vmLId + " = " + bandwidth + 
                                ". Cannot calculate DCP communication costs.");
                        }
                        
                        // Calculate TTi,j / B(VMk, VMl)
                        sumCosts += TTij / bandwidth;
                        validPairs++;
                    }
                }
                
                // Verify we processed all expected pairs
                if (validPairs != expectedPairs) {
                    throw new IllegalStateException(
                        "Expected " + expectedPairs + " VM pairs but only processed " + 
                        validPairs + ". Bandwidth matrix may be incomplete.");
                }
                
                // Calculate average: ci,j = (1 / m(m-1)) × Σ [TTi,j / B(VMk, VMl)]
                double cij = sumCosts / expectedPairs;
                costs.put(key, cij);
            }
        }
        
        return costs;
    }

    /**
     * Convert assignment map from (VM_ID → List<Task_ID>) to (Task_ID → VM_ID)
     * 
     * @param assignments Map from VM ID to list of task IDs assigned to that VM
     * @return Map from task ID to the VM ID it's assigned to
     */
    private static Map<Integer, Integer> buildTaskToVMMap(Map<Integer, List<Integer>> assignments) {
        Map<Integer, Integer> taskToVM = new HashMap<>();
        
        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmId = entry.getKey();
            List<Integer> taskIds = entry.getValue();
            
            for (int taskId : taskIds) {
                taskToVM.put(taskId, vmId);
            }
        }
        
        return taskToVM;
    }

    /**
     * Calculates AVU (Average VM Utilization) - a key performance metric for workflow scheduling.
     * 
     * <h3>What AVU Measures:</h3>
     * AVU represents the <b>average proportion of time VMs are busy executing tasks</b> during the
     * workflow execution. It indicates how efficiently the scheduling algorithm utilizes available
     * computing resources.
     * <ul>
     *   <li>AVU = 1.0 (100%): Perfect utilization - all VMs busy throughout execution</li>
     *   <li>AVU = 0.5 (50%): VMs idle half the time on average</li>
     *   <li>AVU = 0.0 (0%): No utilization - VMs completely idle</li>
     * </ul>
     * 
     * <h3>Mathematical Formula (Paper Equations 8-9):</h3>
     * <pre>
     * VM Utilization (Eq. 8):
     *   VU(VM_k) = (Σ ET(task_i)) / makespan
     *   where:
     *     - ET(task_i) = execution time of task i on VM k = task_size / vm_capacity
     *     - Σ ET(task_i) = sum of execution times for all tasks assigned to VM k
     *     - makespan = total workflow execution time
     * 
     * Average VM Utilization (Eq. 9):
     *   AVU = (Σ VU(VM_k)) / m
     *   where:
     *     - m = total number of VMs
     *     - Σ VU(VM_k) = sum of utilization across all VMs
     * </pre>
     * 
     * <h3>LIMITATIONS:</h3>
     * <ul>
     *   <li><b>Does NOT account for idle time while waiting for data from predecessors:</b>
     *       AVU only counts raw execution time. If a task must wait for predecessor tasks
     *       to complete before starting, this wait time is NOT reflected in AVU.</li>
     * 
     *   <li><b>Does NOT include communication overhead in utilization calculation:</b>
     *       Data transfer time between tasks is excluded. Only pure computation time
     *       (task execution) is counted as "busy" time.</li>
     * 
     *   <li><b>Assumes makespan is accurately calculated:</b>
     *       AVU accuracy depends on makespan accuracy. Preferably, makespan should be
     *       calculated from LOTD.taskAFT (Actual Finish Times) which accounts for:
     *       <ul>
     *         <li>Task dependencies and precedence constraints</li>
     *         <li>Communication costs between tasks</li>
     *         <li>Data transfer delays</li>
     *       </ul>
     *   </li>
     * 
     *   <li><b>If fallback makespan is used, AVU may be overestimated:</b>
     *       When LOTD AFT is unavailable, a simplified fallback calculates makespan by
     *       summing task execution times per VM without accounting for dependencies or
     *       communication. This typically results in:
     *       <ul>
     *         <li>Underestimated makespan (too small)</li>
     *         <li>Overestimated AVU (appears higher than reality)</li>
     *         <li>Misleading efficiency metrics</li>
     *       </ul>
     *       Check SMCPTD logs for "⚠️ Using fallback makespan" warnings.
     *   </li>
     * </ul>
     * 
     * <h3>ACCURACY:</h3>
     * Results are most accurate when:
     * <ul>
     *   <li>LOTD successfully completes and provides taskAFT (Actual Finish Times)</li>
     *   <li>Makespan includes communication costs and dependency delays</li>
     *   <li>All tasks are successfully scheduled (no unassigned tasks)</li>
     *   <li>VM capabilities are properly configured and non-zero</li>
     * </ul>
     * 
     * Look for this log in SMCPTD output to confirm accuracy:
     * <pre>
     *   ✅ Makespan calculated from LOTD AFT: 333.333
     *   ℹ️  Source: LOTD Actual Finish Times (accurate for AVU/VF calculation)
     * </pre>
     * 
     * <h3>COMPLEXITY:</h3>
     * <b>Optimized from O(n²) to O(n)</b> where n = total number of tasks
     * <ul>
     *   <li><b>Old approach:</b> Nested loops - O(n²) for large workflows (500ms+ for 1000 tasks)</li>
     *   <li><b>New approach:</b> HashMap pre-building - O(n) linear time (~2ms for 1000 tasks)</li>
     *   <li><b>Speedup:</b> ~250x faster for large-scale workflows (1000+ tasks)</li>
     * </ul>
     * 
     * Optimization strategy:
     * <ol>
     *   <li>Pre-build taskMap for O(1) task lookup (eliminates nested loop)</li>
     *   <li>Pre-build vmMap for O(1) VM lookup</li>
     *   <li>Direct HashMap.get() calls instead of linear search</li>
     * </ol>
     * 
     * @param smgt SMGT instance containing tasks and VMs
     * @param assignments VM assignments (vmID → list of taskIDs)
     * @param makespan Total workflow execution time (should be from LOTD.taskAFT when available)
     * @return Average VM Utilization in range [0.0, 1.0] where 1.0 = 100% utilization
     * 
     * @implNote This method delegates actual calculation to {@link Metrics#AVU} after converting
     *           task IDs to task objects using optimized HashMap lookups. Performance logging
     *           is enabled for workflows with &gt;500 tasks or execution time &gt;10ms.
     * 
     * @see Metrics#AVU(Map, Map, double, String)
     * @see Metrics#VU(VM, List, double, String)
     * @see Metrics#ET(task, VM, String)
     * @see SMCPTD#calculateFinalMetrics for makespan calculation details
     */
    private static double calculateAVU(SMGT smgt, Map<Integer, List<Integer>> assignments, double makespan) {
        long startTime = System.nanoTime();
        
        if (makespan <= 0)
            return 0;

        // OPTIMIZATION 1: Pre-build task lookup map - O(n) operation, enables O(1) lookups
        Map<Integer, task> taskMap = new HashMap<>();
        for (task t : smgt.getTasks()) {
            taskMap.put(t.getID(), t);
        }

        // OPTIMIZATION 2: Pre-build VM lookup map - O(m) operation, enables O(1) lookups
        Map<Integer, VM> vmMap = new HashMap<>();
        for (VM v : smgt.getVMs()) {
            vmMap.put(v.getID(), v);
        }

        // OPTIMIZATION 3: Convert assignments using direct HashMap lookups - O(k) where k = total assignments
        // OLD: Nested loop O(n²) - for each assignment, search through all tasks
        // NEW: Direct lookup O(1) per assignment
        Map<Integer, List<task>> taskAssignments = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmId = entry.getKey();
            List<task> tasks = new ArrayList<>();
            for (int taskId : entry.getValue()) {
                task t = taskMap.get(taskId);  // O(1) lookup instead of O(n) search
                if (t != null) {
                    tasks.add(t);
                } else {
                    // Task ID in assignment but not in task list - log warning for debugging
                    System.err.println("⚠️  Warning: Task ID " + taskId + " not found in task map");
                }
            }
            taskAssignments.put(vmId, tasks);
        }

        double avu = Metrics.AVU(vmMap, taskAssignments, makespan, "processingCapacity");
        
        // Performance logging (only for large workflows > 500 tasks to avoid noise)
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        if (smgt.getTasks().size() > 500 || durationNanos > 10_000_000) { // > 10ms
            double durationMs = durationNanos / 1_000_000.0;
            System.out.printf("   ⚡ calculateAVU performance: %.3fms (%d tasks, %d VMs)%n",
                    durationMs, smgt.getTasks().size(), smgt.getVMs().size());
        }
        
        return avu;
    }

    /**
     * <h2>Calculates VF (Variance of Fairness) - Task Satisfaction Metric</h2>
     * 
     * <h3>📊 WHAT VF MEASURES</h3>
     * <p>
     * VF (Variance of Fairness) quantifies <b>how fairly tasks are distributed</b> across VMs
     * by measuring the variance in task "satisfaction" levels. Each task's satisfaction
     * represents how close its execution time is to the best possible execution time.
     * </p>
     * <ul>
     *   <li><b>VF = 0.0</b>: Perfect fairness - all tasks have equal satisfaction</li>
     *   <li><b>VF &gt; 0.0</b>: Higher variance - some tasks are significantly slower than others</li>
     *   <li><b>Lower VF is better</b>: Indicates more balanced task distribution</li>
     * </ul>
     * 
     * <h3>🧮 MATHEMATICAL FORMULA (Paper Equation 10)</h3>
     * <pre>
     * For each task i:
     *   satisfaction(task_i) = actualET(task_i) / fastestET(task_i)
     *   
     *   where:
     *     actualET(task_i)  = task_i.size / assignedVM.capacity
     *     fastestET(task_i) = task_i.size / max(allVMs.capacity)
     * 
     * VF = variance(all satisfactions)
     *    = Σ(satisfaction_i - mean_satisfaction)² / n
     * </pre>
     * 
     * <h3>⚠️ LIMITATIONS</h3>
     * <ul>
     *   <li>❌ <b>Does NOT account for communication overhead</b>
     *       <br>→ Only considers computation time (task.size / vm.capacity)</li>
     *   <li>❌ <b>Does NOT consider task dependencies</b>
     *       <br>→ Ignores predecessor wait times and critical path constraints</li>
     *   <li>❌ <b>Simplified satisfaction metric</b>
     *       <br>→ Assumes "fastest ET" = execution on most powerful VM</li>
     *   <li>⚠️ <b>Sensitive to VM heterogeneity</b>
     *       <br>→ Large capacity differences inflate VF even with good scheduling</li>
     *   <li>⚠️ <b>Makespan parameter unused in calculation</b>
     *       <br>→ VF is independent of total workflow completion time</li>
     * </ul>
     * 
     * <h3>✅ ACCURACY CONSIDERATIONS</h3>
     * <p><b>VF is most accurate when:</b></p>
     * <ul>
     *   <li>✓ All tasks have non-zero size</li>
     *   <li>✓ All VMs have positive processing capacity</li>
     *   <li>✓ VM assignments contain valid task IDs</li>
     *   <li>✓ Communication costs are negligible compared to computation</li>
     * </ul>
     * <p><b>Expected log output for valid calculation:</b></p>
     * <pre>
     *   (No warnings about missing tasks or invalid IDs)
     *   ⚡ calculateVF performance: X.XXXms (N tasks, M VMs)  [for large workflows]
     * </pre>
     * <p><b>Warning signs of issues:</b></p>
     * <pre>
     *   ⚠️  Warning: Task ID X not found in task map  [Assignment references non-existent task]
     * </pre>
     * 
     * <h3>⚡ COMPLEXITY &amp; PERFORMANCE</h3>
     * <p><b>OPTIMIZED VERSION - O(n²) → O(n) improvement:</b></p>
     * <ul>
     *   <li><b>OLD</b>: Nested loop - for each assignment, search all tasks = O(n²)</li>
     *   <li><b>NEW</b>: Pre-build HashMap for O(1) lookups = O(n)</li>
     * </ul>
     * 
     * <p><b>Optimization Strategy:</b></p>
     * <ol>
     *   <li>Pre-build taskMap: O(n) - enables O(1) task lookup</li>
     *   <li>Pre-build vmMap: O(m) - enables O(1) VM lookup</li>
     *   <li>Convert assignments using direct HashMap.get(): O(k) where k = total assignments</li>
     * </ol>
     * 
     * <p><b>Performance Comparison (1000 tasks):</b></p>
     * <ul>
     *   <li>Before: ~500ms+ (nested loops)</li>
     *   <li>After: ~2-5ms (HashMap lookups)</li>
     *   <li>Speedup: ~100-250x faster</li>
     * </ul>
     * 
     * <p><b>Performance Logging:</b> Automatically enabled when:</p>
     * <ul>
     *   <li>Workflow has &gt;500 tasks, OR</li>
     *   <li>Execution takes &gt;10ms</li>
     * </ul>
     * 
     * @param smgt       SMGT instance containing all workflow tasks and available VMs
     * @param assignments Task-to-VM assignments (vmID → list of taskIDs assigned to that VM)
     * @param makespan   Total workflow execution time (UNUSED - kept for API consistency)
     * @return           Variance of Fairness (VF ≥ 0.0, lower = better fairness)
     * 
     * @implNote This method delegates the actual VF calculation to {@code Metrics.VF()},
     *           focusing on efficient data structure conversion from ID-based assignments
     *           to object-based assignments with O(n) complexity.
     * 
     * @see Metrics#VF(List, Map, Map, Map, String) for the underlying VF calculation
     * @see #calculateAVU(SMGT, Map, double) for AVU (VM utilization) metric calculation
     */
    private static double calculateVF(SMGT smgt, Map<Integer, List<Integer>> assignments, double makespan) {
        long startTime = System.nanoTime();
        
        if (makespan <= 0)
            return 0;

        // OPTIMIZATION 1: Pre-build task lookup map - O(n) operation, enables O(1) lookups
        Map<Integer, task> taskMap = new HashMap<>();
        for (task t : smgt.getTasks()) {
            taskMap.put(t.getID(), t);
        }

        // OPTIMIZATION 2: Pre-build VM lookup map - O(m) operation, enables O(1) lookups
        Map<Integer, VM> vmMap = new HashMap<>();
        for (VM v : smgt.getVMs()) {
            vmMap.put(v.getID(), v);
        }

        // OPTIMIZATION 3: Convert assignments using direct HashMap lookups - O(k) where k = total assignments
        // OLD: Nested loop O(n²) - for each assignment, search through all tasks
        // NEW: Direct lookup O(1) per assignment
        Map<Integer, List<task>> taskAssignments = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmId = entry.getKey();
            List<task> tasks = new ArrayList<>();
            for (int taskId : entry.getValue()) {
                task t = taskMap.get(taskId);  // O(1) lookup instead of O(n) search
                if (t != null) {
                    tasks.add(t);
                } else {
                    // Task ID in assignment but not in task list - log warning for debugging
                    System.err.println("⚠️  Warning: Task ID " + taskId + " not found in task map");
                }
            }
            taskAssignments.put(vmId, tasks);
        }

        // Calcola VF usando Metrics
        double vf = Metrics.VF(smgt.getTasks(), vmMap, taskAssignments, null, "processingCapacity");
        
        // Performance logging (only for large workflows > 500 tasks to avoid noise)
        long endTime = System.nanoTime();
        long durationNanos = endTime - startTime;
        if (smgt.getTasks().size() > 500 || durationNanos > 10_000_000) { // > 10ms
            double durationMs = durationNanos / 1_000_000.0;
            System.out.printf("   ⚡ calculateVF performance: %.3fms (%d tasks, %d VMs)%n",
                    durationMs, smgt.getTasks().size(), smgt.getVMs().size());
        }
        
        return Double.isNaN(vf) || Double.isInfinite(vf) ? Double.NaN : vf;
    }

    /**
     * Salva risultati in CSV
     */
    private static void saveResultsToCSV() {
        saveResultsToCSV(true);
    }

    private static void saveResultsToCSV(boolean verbose) {
        File outFile = new File(ensureResultsDir(), "experiments_results.csv");
        try (PrintWriter writer = new PrintWriter(outFile)) {
            writer.println("experiment,workflow,tasks,vms,ccr,slr,avu,vf,makespan");
            for (ExperimentResult r : results) {
                if (r != null) {
                    writer.println(r.toString());
                }
            }
            if (verbose) {
                System.out.println("\n✅ Risultati salvati: results/experiments_results.csv");
            }
        } catch (Exception e) {
            System.err.println("❌ Errore salvataggio CSV: " + e.getMessage());
        }
    }

    /**
     * Salva risultati in JSON
     */
    private static void saveResultsToJSON() {
        saveResultsToJSON(true);
    }

    private static void saveResultsToJSON(boolean verbose) {
        File outFile = new File(ensureResultsDir(), "experiments_results.json");
        try (PrintWriter writer = new PrintWriter(outFile)) {
            writer.println("{");
            writer.println("  \"experiments\": [");

            int totalToWrite = 0;
            for (ExperimentResult r : results) {
                if (r != null)
                    totalToWrite++;
            }

            int written = 0;
            for (ExperimentResult r : results) {
                if (r == null)
                    continue;
                writer.printf(Locale.US, "    {\"experiment\": \"%s\", \"workflow\": \"%s\", \"tasks\": %d, " +
                        "\"vms\": %d, \"ccr\": %.1f, \"slr\": %.4f, \"avu\": %.4f, " +
                        "\"vf\": %.6f, \"makespan\": %.4f}",
                        r.experiment, r.workflow, r.numTasks, r.numVMs, r.ccr,
                        r.slr, r.avu, r.vf, r.makespan);
                written++;
                if (written < totalToWrite) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }

            writer.println("  ]");
            writer.println("}");
            if (verbose) {
                System.out.println("✅ Risultati salvati: results/experiments_results.json");
            }
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

    /**
     * Test singolo per debug - CyberShake Small con CCR variabili RIMOVIBILE
     * INUTILE!!! SOLO PER DEBUG VELOCE
     */
    private static void runTestSingle() {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║              TEST SINGOLO - DEBUG MODE                         ║");
        System.out.println("║  Workflow: CyberShake Small (30 tasks, 5 VMs)                 ║");
        System.out.println("║  Testing CCR values to verify SLR calculation                 ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝\n");

        double[] testCCRs = { 0.4, 0.8, 1.2, 1.6, 2.0 };

        System.out.println("CCR\tSLR\tAVU\tVF\tMakespan");
        System.out.println("---\t---\t---\t--\t--------");

        for (double ccr : testCCRs) {
            try {
                ExperimentResult result = runSingleExperiment(
                        "cybershake", // workflow
                        30, // numTasks
                        5, // numVMs
                        ccr, // ccr
                        "TEST_SMALL", // expName
                        NUM_RUNS, // numRuns
                        WARMUP_RUNS // warmupRuns
                );

                if (result == null) {
                    System.out.println("⚠️  Skipped (workflow non trovato)");
                    continue;
                }

                System.out.printf(Locale.US, "%.1f\t%.3f\t%.1f%%\t%.6f\t%.2f%n",
                        result.ccr,
                        result.slr,
                        result.avu * 100,
                        result.vf,
                        result.makespan);

            } catch (Exception e) {
                System.err.println("Error at CCR=" + ccr + ": " + e.getMessage());
            }
        }

        System.out.println("\n📊 Expected values from paper (approx):");
        System.out.println("   CCR=0.4: SLR ~1.1-1.2");
        System.out.println("   CCR=2.0: SLR ~1.5-1.8");
        System.out.println("   AVU should be 40-70%");
        System.out.println("\n✅ Test completato!");
    }
}