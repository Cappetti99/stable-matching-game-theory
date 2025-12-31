import java.io.*;
import java.util.*;

/**
 * AblationExperimentRunner - Ablation Study for SM-CPTD Components
 * 
 * Tests 4 algorithm configurations:
 * 1. SMGT only (baseline)
 * 2. DCP + SMGT
 * 3. SMGT + LOTD
 * 4. DCP + SMGT + LOTD (full SM-CPTD)
 * 
 * This validates the contribution of each component as shown in Figure 12 of the paper.
 */
public class AblationExperimentRunner {

    private static final int NUM_RUNS = 3;  // Reduced for faster execution
    private static final int WARMUP_RUNS = 1;
    
    // Test on large-scale workflows only (as in paper Figure 12)
    private static final String[] WORKFLOWS = {"cybershake", "epigenomics", "ligo", "montage"};
    private static final int TASKS = 1000;
    private static final int VMS = 50;
    private static final double CCR = 1.0;
    
    // Algorithm variants
    private enum AlgorithmVariant {
        SMGT_ONLY,           // No DCP, No LOTD
        DCP_SMGT,            // With DCP, No LOTD
        SMGT_LOTD,           // No DCP, With LOTD
        SM_CPTD              // Full: DCP + SMGT + LOTD
    }
    
    private static List<AblationResult> results = new ArrayList<>();
    
    public static class AblationResult {
        String workflow;
        String algorithm;
        double slr;
        double avu;
        double vf;
        double makespan;
        
        public AblationResult(String workflow, String algorithm, 
                             double slr, double avu, double vf, double makespan) {
            this.workflow = workflow;
            this.algorithm = algorithm;
            this.slr = slr;
            this.avu = avu;
            this.vf = vf;
            this.makespan = makespan;
        }
        
        @Override
        public String toString() {
            return String.format(Locale.US, "%s,%s,%.4f,%.4f,%.6f,%.4f",
                    workflow, algorithm, slr, avu, vf, makespan);
        }
    }
    
    public static void main(String[] args) {
        SeededRandom.initFromArgs(args);
        
        System.out.println("═══════════════════════════════════════════════════════");
        System.out.println("   ABLATION STUDY: SM-CPTD Components");
        System.out.println("   Figure 12 Reproduction");
        System.out.println("═══════════════════════════════════════════════════════\n");
        
        try {
            for (String workflow : WORKFLOWS) {
                runAblationForWorkflow(workflow);
            }
            
            saveResults();
            printSummary();
            generateFigures();
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Run all 4 algorithm variants for one workflow
     */
    private static void runAblationForWorkflow(String workflow) throws Exception {
        System.out.println("\n" + "─".repeat(60));
        System.out.println("📊 Workflow: " + workflow.toUpperCase());
        System.out.println("─".repeat(60));
        
        // Adjust task count for epigenomics
        int tasks = workflow.equals("epigenomics") ? 997 : TASKS;
        
        for (AlgorithmVariant variant : AlgorithmVariant.values()) {
            System.out.printf("\n🔬 Testing: %s ... ", variant.name());
            
            AblationResult result = runSingleVariant(workflow, tasks, VMS, CCR, variant);
            
            if (result != null) {
                results.add(result);
                System.out.printf("✓ SLR=%.4f, AVU=%.4f, VF=%.6f\n", 
                        result.slr, result.avu, result.vf);
            } else {
                System.out.println("⚠️  Skipped");
            }
        }
    }
    
    /**
     * Run a single algorithm variant (averaged over multiple runs)
     */
    private static AblationResult runSingleVariant(
            String workflow, int numTasks, int numVMs, double ccr,
            AlgorithmVariant variant) throws Exception {
        
        // Find workflow directory
        String workflowDir = ExperimentRunner.findPegasusWorkflowDir(
                workflow.toLowerCase(), numTasks, numVMs);
        
        if (workflowDir == null) {
            System.out.println("\n   ⚠️  Workflow not found");
            return null;
        }
        
        List<RunMetrics> runs = new ArrayList<>();
        int totalRuns = WARMUP_RUNS + NUM_RUNS;
        
        for (int runIdx = 0; runIdx < totalRuns; runIdx++) {
            boolean isWarmup = runIdx < WARMUP_RUNS;
            
            // Load data with run-specific seed
            int seedRunIdx = runIdx; // Vary seed per run for statistical analysis
            List<task> tasks = DataLoader.loadTasksFromCSV(
                    workflowDir + "/dag.csv", workflowDir + "/task.csv", seedRunIdx);
            List<VM> vms = DataLoader.loadVMsFromCSV(
                    workflowDir + "/processing_capacity.csv", seedRunIdx);
            DataLoader.loadBandwidthFromCSV(
                    workflowDir + "/bandwidth.csv", vms, seedRunIdx);
            
            // Create VM mapping using VM index (to match SMGT's schedule format)
            // SMGT uses indices 0, 1, 2... as VM keys, not VM.getID()
            Map<Integer, VM> vmMapping = new HashMap<>();
            for (int i = 0; i < vms.size(); i++) {
                vmMapping.put(i, vms.get(i));
            }
            
            // Calculate communication costs ONCE (no 2-pass)
            SMGT smgt = new SMGT();
            smgt.setTasks(tasks);
            smgt.setVMs(vms);
            smgt.calculateTaskLevels();
            
            Map<String, Double> commCosts = calculateCommunicationCostsForDCP(smgt, ccr);
            
            // Calculate critical path for SLR computation
            Map<Integer, List<Integer>> taskLevels = Utility.organizeTasksByLevels(tasks);
            Set<Integer> criticalPath = DCP.executeDCP(tasks, taskLevels, commCosts, vmMapping);
            List<task> criticalPathTasks = new ArrayList<>();
            for (task t : tasks) {
                if (criticalPath.contains(t.getID())) {
                    criticalPathTasks.add(t);
                }
            }
            
            // Run the specific algorithm variant
            Map<Integer, List<Integer>> assignments = executeVariant(
                    variant, tasks, vms, commCosts, vmMapping, smgt);
            
            // Calculate metrics with proper precedence and communication costs
            double makespan = calculateMakespan(assignments, tasks, vmMapping, commCosts);
            double slr = Metrics.SLR(makespan, criticalPathTasks, vmMapping);
            double avu = calculateAVU(smgt, assignments, makespan);
            double vf = calculateVF(smgt, assignments, makespan);
            
            // Save metrics (only if not warmup)
            if (!isWarmup) {
                runs.add(new RunMetrics(slr, avu, vf, makespan));
            }
        }
        
        // Average results
        double avgSLR = runs.stream().mapToDouble(r -> r.slr).average().orElse(0);
        double avgAVU = runs.stream().mapToDouble(r -> r.avu).average().orElse(0);
        double avgVF = runs.stream().mapToDouble(r -> r.vf).average().orElse(0);
        double avgMakespan = runs.stream().mapToDouble(r -> r.makespan).average().orElse(0);
        
        return new AblationResult(workflow, variant.name(), avgSLR, avgAVU, avgVF, avgMakespan);
    }
    
    /**
     * Execute a specific algorithm variant
     */
    private static Map<Integer, List<Integer>> executeVariant(
            AlgorithmVariant variant,
            List<task> tasks,
            List<VM> vms,
            Map<String, Double> commCosts,
            Map<Integer, VM> vmMapping,
            SMGT smgt) throws Exception {
        
        switch (variant) {
            case SMGT_ONLY:
                return executeSMGTOnly(tasks, vms);
                
            case DCP_SMGT:
                return executeDCP_SMGT(tasks, vms, commCosts, vmMapping);
                
            case SMGT_LOTD:
                return executeSMGT_LOTD(tasks, vms, commCosts, smgt);
                
            case SM_CPTD:
                return executeFull_SMCPTD(tasks, vms, commCosts, vmMapping);
                
            default:
                throw new IllegalArgumentException("Unknown variant: " + variant);
        }
    }
    
    /**
     * VARIANT 1: SMGT only (baseline)
     * No DCP (empty critical path), No LOTD
     */
    private static Map<Integer, List<Integer>> executeSMGTOnly(
            List<task> tasks, List<VM> vms) {
        
        // Clear VM waiting lists before execution
        for (VM vm : vms) {
            vm.clearWaitingList();
        }
        
        SMGT smgt = new SMGT();
        smgt.setTasks(tasks);
        smgt.setVMs(vms);
        smgt.calculateTaskLevels();
        
        // Run SMGT with EMPTY critical path (no DCP prioritization)
        Set<Integer> emptyCriticalPath = new HashSet<>();
        return smgt.runSMGT(emptyCriticalPath);
    }
    
    /**
     * VARIANT 2: DCP + SMGT
     * With DCP, No LOTD
     */
    private static Map<Integer, List<Integer>> executeDCP_SMGT(
            List<task> tasks, List<VM> vms,
            Map<String, Double> commCosts,
            Map<Integer, VM> vmMapping) {
        
        // Clear VM waiting lists before execution
        for (VM vm : vms) {
            vm.clearWaitingList();
        }
        
        SMGT smgt = new SMGT();
        smgt.setTasks(tasks);
        smgt.setVMs(vms);
        smgt.calculateTaskLevels();
        
        // Execute DCP to find critical path
        Map<Integer, List<Integer>> taskLevels = Utility.organizeTasksByLevels(tasks);
        
        Set<Integer> criticalPath = DCP.executeDCP(
                tasks, taskLevels, commCosts, vmMapping);
        
        // Run SMGT with critical path (but no LOTD)
        return smgt.runSMGT(criticalPath);
    }
    
    /**
     * VARIANT 3: SMGT + LOTD
     * No DCP, With LOTD
     */
    private static Map<Integer, List<Integer>> executeSMGT_LOTD(
            List<task> tasks, List<VM> vms,
            Map<String, Double> commCosts,
            SMGT smgt) {
        
        // Clear VM waiting lists before execution
        for (VM vm : vms) {
            vm.clearWaitingList();
        }
        
        // Create fresh SMGT instance to avoid state pollution
        SMGT freshSmgt = new SMGT();
        freshSmgt.setTasks(tasks);
        freshSmgt.setVMs(vms);
        freshSmgt.calculateTaskLevels();
        
        // Run SMGT with empty critical path
        Set<Integer> emptyCriticalPath = new HashSet<>();
        Map<Integer, List<Integer>> smgtSchedule = freshSmgt.runSMGT(emptyCriticalPath);
        
        // Apply LOTD optimization
        LOTD lotd = new LOTD(freshSmgt);
        lotd.setCommunicationCosts(commCosts);
        
        return lotd.executeLOTDCorrect(smgtSchedule);
    }
    
    /**
     * VARIANT 4: Full SM-CPTD
     * DCP + SMGT + LOTD (complete algorithm)
     * Uses 2-pass approach for bandwidth calculation
     */
    private static Map<Integer, List<Integer>> executeFull_SMCPTD(
            List<task> tasks, List<VM> vms,
            Map<String, Double> commCosts,
            Map<Integer, VM> vmMapping) throws Exception {
        
        // 2-PASS APPROACH (matching ExperimentRunner)
        // Pass 1: Use average bandwidth (from commCosts parameter)
        
        // IMPORTANT: Clear VM waiting lists before Pass 1 to avoid state pollution
        for (VM vm : vms) {
            vm.clearWaitingList();
        }
        
        SMCPTD smcptdPass1 = new SMCPTD();
        smcptdPass1.setInputData(tasks, vms);
        Map<Integer, List<Integer>> assignmentsPass1 = smcptdPass1.executeSMCPTD(commCosts, vmMapping);
        
        // Build task-to-VM mapping from Pass 1
        Map<Integer, Integer> taskToVM = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : assignmentsPass1.entrySet()) {
            int vmId = entry.getKey();
            for (Integer taskId : entry.getValue()) {
                taskToVM.put(taskId, vmId);
            }
        }
        
        // Pass 2: Recalculate comm costs with VM-specific bandwidth
        
        // IMPORTANT: Clear VM waiting lists before Pass 2 to avoid state pollution
        for (VM vm : vms) {
            vm.clearWaitingList();
        }
        
        SMGT smgt = new SMGT();
        smgt.setTasks(tasks);
        smgt.setVMs(vms);
        smgt.calculateTaskLevels();
        Map<String, Double> commCostsPass2 = calculateCommunicationCostsVMSpecific(smgt, taskToVM);
        
        // Execute full pipeline with refined costs
        SMCPTD smcptd = new SMCPTD();
        smcptd.setInputData(tasks, vms);
        return smcptd.executeSMCPTD(commCostsPass2, vmMapping);
    }
    
    // ============================================================================
    // HELPER METHODS (copied from ExperimentRunner for consistency)
    // ============================================================================
    
    private static Map<String, Double> calculateCommunicationCostsForDCP(SMGT smgt, double ccr) {
        Map<String, Double> costs = new HashMap<>();
        List<VM> vms = smgt.getVMs();
        int m = vms.size();
        
        if (m <= 1) {
            for (task t : smgt.getTasks()) {
                for (int succId : t.getSucc()) {
                    costs.put(t.getID() + "_" + succId, 0.0);
                }
            }
            return costs;
        }
        
        int expectedPairs = m * (m - 1);
        
        for (task t : smgt.getTasks()) {
            for (int succId : t.getSucc()) {
                String key = t.getID() + "_" + succId;
                double TTij = t.getSize() * ccr;
                double sumCosts = 0.0;
                
                for (int k = 0; k < m; k++) {
                    VM vmK = vms.get(k);
                    for (int l = 0; l < m; l++) {
                        if (k == l) continue;
                        VM vmL = vms.get(l);
                        double bandwidth = vmK.getBandwidthToVM(vmL.getID());
                        sumCosts += TTij / bandwidth;
                    }
                }
                
                costs.put(key, sumCosts / expectedPairs);
            }
        }
        
        return costs;
    }
    
    /**
     * Calculate communication costs using VM-specific bandwidth (Pass 2)
     * Used after initial assignment to refine costs based on actual VM pairs
     */
    private static Map<String, Double> calculateCommunicationCostsVMSpecific(
            SMGT smgt, Map<Integer, Integer> taskToVMIdx) {
        
        Map<String, Double> costs = new HashMap<>();
        List<VM> vms = smgt.getVMs();
        
        // Build VM lookup using index (taskToVMIdx uses VM indices, not VM.getID())
        Map<Integer, VM> vmMap = new HashMap<>();
        for (int i = 0; i < vms.size(); i++) {
            vmMap.put(i, vms.get(i));
        }
        
        // Calculate average bandwidth as fallback
        double avgBandwidth = 0.0;
        int pairCount = 0;
        for (int i = 0; i < vms.size(); i++) {
            VM vmI = vms.get(i);
            for (int j = 0; j < vms.size(); j++) {
                if (i == j) continue;
                VM vmJ = vms.get(j);
                avgBandwidth += vmI.getBandwidthToVM(vmJ.getID());
                pairCount++;
            }
        }
        if (pairCount > 0) avgBandwidth /= pairCount;
        
        // Calculate costs using VM-pair-specific bandwidth when available
        for (task t : smgt.getTasks()) {
            for (int succId : t.getSucc()) {
                String key = t.getID() + "_" + succId;
                double dataSize = t.getSize(); // Already includes CCR from task size
                
                // Get VM indices for source and destination tasks
                Integer sourceVMIdx = taskToVMIdx.get(t.getID());
                Integer destVMIdx = taskToVMIdx.get(succId);
                
                if (sourceVMIdx != null && destVMIdx != null) {
                    if (sourceVMIdx.equals(destVMIdx)) {
                        // Same VM: no communication cost
                        costs.put(key, 0.0);
                        continue;
                    }
                    
                    VM sourceVM = vmMap.get(sourceVMIdx);
                    VM destVM = vmMap.get(destVMIdx);
                    if (sourceVM != null && destVM != null && sourceVM.hasBandwidthToVM(destVM.getID())) {
                        double bandwidth = sourceVM.getBandwidthToVM(destVM.getID());
                        costs.put(key, dataSize / bandwidth);
                        continue;
                    }
                }
                
                // Fallback: use average bandwidth
                costs.put(key, dataSize / avgBandwidth);
            }
        }
        
        return costs;
    }
    
    private static double calculateMakespan(
            Map<Integer, List<Integer>> assignments,
            List<task> tasks,
            Map<Integer, VM> vmMapping,
            Map<String, Double> commCosts) {
        
        // Build task lookup
        Map<Integer, task> taskMap = new HashMap<>();
        for (task t : tasks) {
            taskMap.put(t.getID(), t);
        }
        
        // Build task-to-VM mapping (handle duplicates by keeping first assignment)
        // Note: assignments use VM index (0, 1, 2...) as keys
        Map<Integer, Integer> taskToVMIdx = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmIdx = entry.getKey();
            for (Integer taskId : entry.getValue()) {
                if (!taskToVMIdx.containsKey(taskId)) {
                    taskToVMIdx.put(taskId, vmIdx);
                }
            }
        }
        
        // Track VM ready times (when VM finishes its last assigned task)
        Map<Integer, Double> vmReadyTime = new HashMap<>();
        for (Integer vmIdx : vmMapping.keySet()) {
            vmReadyTime.put(vmIdx, 0.0);
        }
        
        // Calculate AFT (Actual Finish Time) for each task respecting precedences AND VM availability
        Map<Integer, Double> taskAST = new HashMap<>();
        Map<Integer, Double> taskAFT = new HashMap<>();
        
        // Process tasks in topological order (by levels)
        Map<Integer, List<Integer>> taskLevels = Utility.organizeTasksByLevels(tasks);
        List<Integer> sortedLevels = new ArrayList<>(taskLevels.keySet());
        Collections.sort(sortedLevels);
        
        for (int level : sortedLevels) {
            // For each level, process tasks and update VM ready times
            List<Integer> levelTasks = taskLevels.get(level);
            
            for (Integer taskId : levelTasks) {
                task t = taskMap.get(taskId);
                if (t == null || !taskToVMIdx.containsKey(taskId)) continue;
                
                int vmIdx = taskToVMIdx.get(taskId);
                VM vm = vmMapping.get(vmIdx);
                if (vm == null) continue;
                
                // Calculate execution time
                double execTime = Metrics.ET(t, vm, "processingCapacity");
                if (execTime == Double.POSITIVE_INFINITY) continue;
                
                // Find Data Ready Time (DRT): max finish time of predecessors + communication
                double dataReadyTime = 0.0;
                for (int predId : t.getPre()) {
                    if (!taskAFT.containsKey(predId)) continue;
                    
                    double predFinish = taskAFT.get(predId);
                    
                    // Add communication cost if tasks on different VMs
                    int predVmIdx = taskToVMIdx.getOrDefault(predId, -1);
                    if (predVmIdx != vmIdx && predVmIdx != -1) {
                        String commKey = predId + "_" + taskId;
                        double commCost = commCosts.getOrDefault(commKey, 0.0);
                        predFinish += commCost;
                    }
                    
                    dataReadyTime = Math.max(dataReadyTime, predFinish);
                }
                
                // Machine Ready Time (MRT): when VM becomes available
                double machineReadyTime = vmReadyTime.getOrDefault(vmIdx, 0.0);
                
                // AST = max(DRT, MRT) - task starts when both data and machine are ready
                double ast = Math.max(dataReadyTime, machineReadyTime);
                
                // AFT = AST + execution time
                double aft = ast + execTime;
                
                taskAST.put(taskId, ast);
                taskAFT.put(taskId, aft);
                
                // Update VM ready time (this VM is busy until this task finishes)
                vmReadyTime.put(vmIdx, aft);
            }
        }
        
        // Makespan = maximum AFT across all tasks
        return taskAFT.values().stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0.0);
    }
    
    private static task getTaskById(int taskId, List<task> tasks) {
        return tasks.stream()
                .filter(t -> t.getID() == taskId)
                .findFirst()
                .orElse(null);
    }
    
    private static double calculateAVU(SMGT smgt, Map<Integer, List<Integer>> assignments, double makespan) {
        if (makespan <= 0) return 0;
        
        Map<Integer, task> taskMap = new HashMap<>();
        for (task t : smgt.getTasks()) {
            taskMap.put(t.getID(), t);
        }
        
        // IMPORTANT: SMGT uses VM index (0, 1, 2...) as keys, not VM.getID()
        // Build vmMap using index as key to match SMGT's schedule format
        List<VM> vmList = smgt.getVMs();
        Map<Integer, VM> vmMap = new HashMap<>();
        for (int i = 0; i < vmList.size(); i++) {
            vmMap.put(i, vmList.get(i));  // Use index as key, not VM.getID()
        }
        
        // For AVU, we need to count ALL task executions (including duplicates)
        // because each execution contributes to VM utilization
        Map<Integer, List<task>> taskAssignments = new HashMap<>();
        
        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmIdx = entry.getKey();  // This is VM index, not VM.getID()
            List<task> vmTasks = new ArrayList<>();
            for (int taskId : entry.getValue()) {
                task t = taskMap.get(taskId);
                if (t != null) {
                    vmTasks.add(t);  // Add even if duplicate (counts as VM utilization)
                }
            }
            taskAssignments.put(vmIdx, vmTasks);
        }
        
        return Metrics.AVU(vmMap, taskAssignments, makespan, "processingCapacity");
    }
    
    private static double calculateVF(SMGT smgt, Map<Integer, List<Integer>> assignments, double makespan) {
        if (makespan <= 0) return 0;
        
        Map<Integer, task> taskMap = new HashMap<>();
        for (task t : smgt.getTasks()) {
            taskMap.put(t.getID(), t);
        }
        
        // IMPORTANT: SMGT uses VM index (0, 1, 2...) as keys, not VM.getID()
        // Build vmMap using index as key to match SMGT's schedule format
        List<VM> vmList = smgt.getVMs();
        Map<Integer, VM> vmMap = new HashMap<>();
        for (int i = 0; i < vmList.size(); i++) {
            vmMap.put(i, vmList.get(i));  // Use index as key, not VM.getID()
        }
        
        // For VF, don't count duplicates (each task's satisfaction counted once)
        Set<Integer> assignedTaskIds = new HashSet<>();
        Map<Integer, List<task>> taskAssignments = new HashMap<>();
        
        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmIdx = entry.getKey();  // This is VM index, not VM.getID()
            List<task> tasks = new ArrayList<>();
            for (int taskId : entry.getValue()) {
                task t = taskMap.get(taskId);
                if (t != null && !assignedTaskIds.contains(taskId)) {
                    tasks.add(t);
                    assignedTaskIds.add(taskId);
                }
            }
            taskAssignments.put(vmIdx, tasks);
        }
        
        double vf = Metrics.VF(smgt.getTasks(), vmMap, taskAssignments, "processingCapacity");
        return Double.isNaN(vf) || Double.isInfinite(vf) ? Double.NaN : vf;
    }
    
    private static class RunMetrics {
        double slr, avu, vf, makespan;
        RunMetrics(double slr, double avu, double vf, double makespan) {
            this.slr = slr;
            this.avu = avu;
            this.vf = vf;
            this.makespan = makespan;
        }
    }
    
    // ============================================================================
    // RESULTS SAVING & REPORTING
    // ============================================================================
    
    private static void saveResults() {
        // CSV
        File resultsDir = new File("../results");
        resultsDir.mkdirs();
        
        try (PrintWriter writer = new PrintWriter(new File(resultsDir, "ablation_study.csv"))) {
            writer.println("workflow,algorithm,slr,avu,vf,makespan");
            for (AblationResult r : results) {
                writer.println(r.toString());
            }
            System.out.println("\n✅ Results saved: results/ablation_study.csv");
        } catch (Exception e) {
            System.err.println("❌ Error saving CSV: " + e.getMessage());
        }
        
        // JSON
        try (PrintWriter writer = new PrintWriter(new File(resultsDir, "ablation_study.json"))) {
            writer.println("{");
            writer.println("  \"ablation_experiments\": [");
            
            for (int i = 0; i < results.size(); i++) {
                AblationResult r = results.get(i);
                writer.printf(Locale.US, 
                    "    {\"workflow\": \"%s\", \"algorithm\": \"%s\", " +
                    "\"slr\": %.4f, \"avu\": %.4f, \"vf\": %.6f, \"makespan\": %.4f}",
                    r.workflow, r.algorithm, r.slr, r.avu, r.vf, r.makespan);
                
                if (i < results.size() - 1) writer.println(",");
                else writer.println();
            }
            
            writer.println("  ]");
            writer.println("}");
            System.out.println("✅ Results saved: results/ablation_study.json");
        } catch (Exception e) {
            System.err.println("❌ Error saving JSON: " + e.getMessage());
        }
    }
    
    private static void printSummary() {
        System.out.println("\n" + "═".repeat(60));
        System.out.println("📊 ABLATION STUDY SUMMARY");
        System.out.println("═".repeat(60));
        
        // Group by algorithm
        Map<String, List<AblationResult>> byAlgorithm = new HashMap<>();
        for (AblationResult r : results) {
            byAlgorithm.computeIfAbsent(r.algorithm, k -> new ArrayList<>()).add(r);
        }
        
        // Print averages for each algorithm
        for (AlgorithmVariant variant : AlgorithmVariant.values()) {
            String algName = variant.name();
            List<AblationResult> algResults = byAlgorithm.get(algName);
            
            if (algResults == null || algResults.isEmpty()) continue;
            
            double avgSLR = algResults.stream().mapToDouble(r -> r.slr).average().orElse(0);
            double avgAVU = algResults.stream().mapToDouble(r -> r.avu).average().orElse(0);
            double avgVF = algResults.stream().mapToDouble(r -> r.vf).average().orElse(0);
            
            System.out.printf("\n%s (n=%d workflows):\n", algName, algResults.size());
            System.out.printf("   Avg SLR: %.4f\n", avgSLR);
            System.out.printf("   Avg AVU: %.4f (%.1f%%)\n", avgAVU, avgAVU * 100);
            System.out.printf("   Avg VF:  %.6f\n", avgVF);
        }
        
        System.out.println("\n" + "═".repeat(60));
        System.out.println("✅ Ablation study complete!");
        System.out.println("   Run visualization: cd generators && python3 generate_paper_figures.py --ablation");
        System.out.println("═".repeat(60));
    }
    
    /**
     * Generate ablation study figures (Figure 12)
     */
    private static void generateFigures() {
        System.out.println("\n" + "═".repeat(60));
        System.out.println("📊 GENERATING ABLATION FIGURES (Figure 12)");
        System.out.println("═".repeat(60));
        
        try {
            // Check if Python 3 is available
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.out.println("Python3 not found. Skipping figure generation.");
                System.out.println("To generate figures manually: cd generators && python3 generate_paper_figures.py --ablation");
                return;
            }

            // Check Python dependencies
            pb = new ProcessBuilder("python3", "-c", "import pandas, matplotlib");
            pb.redirectErrorStream(true);
            process = pb.start();
            exitCode = process.waitFor();
            if (exitCode != 0) {
                System.out.println("Python deps not found (missing 'pandas' or 'matplotlib'). Skipping figure generation.");
                System.out.println("To enable figures: pip3 install pandas matplotlib");
                System.out.println("Then rerun: cd generators && python3 generate_paper_figures.py --ablation");
                return;
            }

            // Run the figure generation script
            pb = new ProcessBuilder("python3", "generate_paper_figures.py", "--ablation");
            pb.directory(new File("../generators"));
            pb.redirectErrorStream(true);
            process = pb.start();

            try (java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }
            }

            exitCode = process.waitFor();
            if (exitCode != 0) {
                System.out.println("Figure generation exited with code: " + exitCode);
            } else {
                System.out.println("\n✅ Ablation figures generated successfully!");
                System.out.println("   Check: results/figures/figure12_ablation_study.png");
            }
        } catch (Exception e) {
            System.out.println("Error generating figures: " + e.getMessage());
            System.out.println("To generate figures manually: cd generators && python3 generate_paper_figures.py --ablation");
        }
    }
}