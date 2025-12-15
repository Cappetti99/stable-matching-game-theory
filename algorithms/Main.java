
import java.io.*;
import java.util.*;

public class Main {

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║     SM-CPTD PAPER EXPERIMENTS - Full Benchmark Suite          ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Experiment 1: CCR Effect (Figures 3-8)                       ║");
        System.out.println("║  Experiment 2: VM Count Effect (Figures 9-10)                 ║");
        System.out.println("║  Metrics: SLR, AVU, VF                                        ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Run full experiments
        System.out.println("🚀 Starting full experiment suite...");
        System.out.println();

        // Call ExperimentRunner
        ExperimentRunner.main(args);

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    GENERATING FIGURES                          ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
        System.out.println();

        // Generate figures using Python scripts
        generateFigures();

        System.out.println();
        System.out.println("╔════════════════════════════════════════════════════════════════╗");
        System.out.println("║                    ALL DONE! ✓                                 ║");
        System.out.println("╠════════════════════════════════════════════════════════════════╣");
        System.out.println("║  Results saved in: results/                                    ║");
        System.out.println("║  Figures saved in: results/figures/                            ║");
        System.out.println("╚════════════════════════════════════════════════════════════════╝");
    }

    private static void generateFigures() {
        System.out.println("📊 Generating paper figures...");

        try {
            // Check if Python is available
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.out.println("   ⚠️  Python3 not found. Skipping figure generation.");
                System.out.println("   💡 To generate figures manually, run:");
                System.out.println("      cd generators && python3 generate_paper_figures.py");
                return;
            }

            // Run Python script to generate figures
            System.out.println("   📈 Running Python script: generate_paper_figures.py");
            pb = new ProcessBuilder("python3", "generate_paper_figures.py", "--auto");
            pb.directory(new File("../generators"));
            pb.redirectErrorStream(true);

            process = pb.start();

            // Print output
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("      " + line);
            }

            exitCode = process.waitFor();

            if (exitCode == 0) {
                System.out.println("   ✅ Figures generated successfully!");
            } else {
                System.out.println("   ⚠️  Figure generation completed with warnings (exit code: " + exitCode + ")");
            }

        } catch (Exception e) {
            System.out.println("   ⚠️  Error generating figures: " + e.getMessage());
            System.out.println("   💡 To generate figures manually, run:");
            System.out.println("      cd generators && python3 generate_paper_figures.py");
        }
    }

    // Legacy test method - kept for backward compatibility
    private static void runLegacyTest() throws Exception {
        System.out.println("=== DCP Algorithm Test ===");

        // Parse workflow XML and generate CSV files
        String workflowFile = "../workflow/montage/Montage_50.xml";
        String dataDir = "../data";
        int numVMs = 3;

        System.out.println("\n1. Parsing workflow from XML...");
        PegasusXMLParser.parseAndConvert(workflowFile, dataDir, numVMs);

        // File paths for generated CSV files
        String taskFile = dataDir + "/task.csv";
        String dagFile = dataDir + "/dag.csv";
        String vmFile = dataDir + "/vm.csv";
        String processingCapacityFile = dataDir + "/processing_capacity.csv";

        // Test 2: Load and validate data
        System.out.println("\n2. Loading data from generated CSV files...");
        Map<String, task> taskMap = loadTasks(taskFile);
        System.out.println("   Loaded " + taskMap.size() + " tasks");

        loadDependencies(dagFile, taskMap);
        System.out.println("   Loaded task dependencies");

        List<task> taskList = new ArrayList<>(taskMap.values());

        Map<Integer, VM> vmMapping = loadVMs(vmFile);
        System.out.println("   Loaded " + vmMapping.size() + " VMs");

        // Load processing capacities from the new CSV file
        loadProcessingCapacities(processingCapacityFile, vmMapping);
        System.out.println("   Loaded processing capacities");

        // Display VM information with processing capacities
        System.out.println("   VM Processing Capacities:");
        for (VM vm : vmMapping.values()) {
            double processingCapacity = vm.getCapability("processingCapacity");
            System.out.println("     VM" + (vm.getID() + 1) + ": " + processingCapacity);
        }

        // Test 3: Display task information
        System.out.println("\n3. Task Information:");
        taskList.stream()
                .sorted((t1, t2) -> Integer.compare(t1.getID(), t2.getID()))
                .forEach(t -> System.out.println("   " + t));

        // Test 4: Find entry and exit tasks
        System.out.println("\n4. Finding entry and exit tasks...");

        // Find all entry tasks (tasks without predecessors)
        List<task> entryTasks = taskList.stream()
                .filter(t -> t.getPre() == null || t.getPre().isEmpty())
                .sorted((t1, t2) -> Integer.compare(t1.getID(), t2.getID()))
                .toList();

        // Find all exit tasks (tasks without successors)
        List<task> exitTasks = taskList.stream()
                .filter(t -> t.getSucc() == null || t.getSucc().isEmpty())
                .sorted((t1, t2) -> Integer.compare(t1.getID(), t2.getID()))
                .toList();

        System.out.println("   Entry tasks (no predecessors): " + entryTasks.size() + " found");
        for (task t : entryTasks) {
            System.out.println("     Task " + t.getID() + " (size: " + String.format("%.1f", t.getSize()) + ")");
        }

        System.out.println("   Exit tasks (no successors): " + exitTasks.size() + " found");
        for (task t : exitTasks) {
            System.out.println("     Task " + t.getID() + " (size: " + String.format("%.1f", t.getSize()) + ")");
        }

        // For the DCP algorithm, we need a single exit task
        task exitTask = exitTasks.stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No exit task found"));
        System.out.println("   Using Task " + exitTask.getID() + " as main exit task for DCP algorithm");

        // Test 4: Generate communication costs
        System.out.println("\n4. Generating communication costs...");
        Map<String, Double> communicationCosts = generateDummyCommunicationCosts(taskList, vmMapping);
        System.out.println("   Generated " + communicationCosts.size() + " communication cost entries");

        // Display some communication costs
        System.out.println("   Sample communication costs:");
        communicationCosts.entrySet().stream()
                .limit(5)
                .forEach(entry -> System.out
                        .println("     " + entry.getKey() + " -> " + String.format("%.2f", entry.getValue())));

        // Test 5: Organize tasks by levels
        System.out.println("\n5. Organizing tasks by levels...");
        Map<Integer, List<Integer>> levels = DCP.organizeTasksByLevels(taskList);
        System.out.println("   Found " + levels.size() + " levels");

        // Display levels
        for (Map.Entry<Integer, List<Integer>> levelEntry : levels.entrySet()) {
            System.out.println("   Level " + levelEntry.getKey() + ": " + levelEntry.getValue());
        }

        // Test 6: Calculate task ranks
        System.out.println("\n6. Calculating task ranks...");
        Map<Integer, Double> taskRanks = DCP.calculateTaskRanks(taskList, exitTask, communicationCosts, vmMapping);
        System.out.println("   Calculated ranks for " + taskRanks.size() + " tasks");

        // Display task ranks sorted by rank (descending)
        System.out.println("   Task ranks (sorted by rank):");
        taskRanks.entrySet().stream()
                .sorted((e1, e2) -> Double.compare(e2.getValue(), e1.getValue()))
                .forEach(entry -> System.out
                        .println("     Task " + entry.getKey() + ": " + String.format("%.2f", entry.getValue())));

        // Test 7: Execute DCP algorithm
        System.out.println("\n7. Executing DCP algorithm...");
        Set<Integer> criticalPath = DCP.executeDCP(taskList, levels, exitTask, communicationCosts, vmMapping);

        // Display results
        System.out.println("\n=== DCP RESULTS ===");
        System.out.println("Critical Path Task IDs: " + criticalPath);
        System.out.println("Critical Path Size: " + criticalPath.size());

        // Display critical path tasks with their ranks
        System.out.println("\nCritical Path Tasks (with ranks):");
        criticalPath.stream()
                .sorted()
                .forEach(taskId -> {
                    double rank = taskRanks.getOrDefault(taskId, 0.0);
                    task t = taskMap.get("t" + taskId);
                    System.out.println("  Task " + taskId + ": rank=" + String.format("%.2f", rank) +
                            ", size=" + (t != null ? String.format("%.1f", t.getSize()) : "N/A"));
                });

        // Test 8: Performance metrics
        System.out.println("\n8. Performance Metrics:");
        double totalRank = criticalPath.stream()
                .mapToDouble(taskId -> taskRanks.getOrDefault(taskId, 0.0))
                .sum();
        System.out.println("   Total Critical Path Rank: " + String.format("%.2f", totalRank));

        double avgRank = totalRank / criticalPath.size();
        System.out.println("   Average Critical Path Task Rank: " + String.format("%.2f", avgRank));

        // Test 9: Testing Metrics Implementation
        System.out.println("\n9. Testing Metrics Implementation:");
        testMetrics(taskList, vmMapping, communicationCosts, criticalPath, taskMap);

        // Test 10: SMGT Algorithm
        System.out.println("\n10. Testing SMGT Algorithm:");
        testSMGTAlgorithm(taskFile, dagFile, processingCapacityFile);

        // Test 11: LOTD Algorithm
        System.out.println("\n11. Testing LOTD Algorithm:");
        testLOTDAlgorithm();

        System.out.println("\n=== TEST COMPLETED ===");
    }

    // Carica i task da file CSV
    private static Map<String, task> loadTasks(String filePath) throws IOException {
        Map<String, task> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header
                }
                if (line.startsWith("#") || line.isBlank())
                    continue;
                String[] parts = line.trim().split(",");
                int taskID = Integer.parseInt(parts[0]);
                double size = Double.parseDouble(parts[1]);
                task t = new task(taskID);
                t.setSize(size);
                map.put("t" + taskID, t);
            }
        }
        return map;
    }

    // Carica le dipendenze e aggiorna pre/succ dei task
    private static void loadDependencies(String filePath, Map<String, task> taskMap) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header
                }
                if (line.startsWith("#") || line.isBlank())
                    continue;
                String[] parts = line.trim().split(",");
                int predId = Integer.parseInt(parts[0]);
                int succId = Integer.parseInt(parts[1]);
                task from = taskMap.get("t" + predId);
                task to = taskMap.get("t" + succId);
                if (from != null && to != null) {
                    from.addSuccessor(to.getID());
                    to.addPredecessor(from.getID());
                }
            }
        }
    }

    // Carica le VM e la matrice di larghezza di banda
    private static Map<Integer, VM> loadVMs(String filePath) throws IOException {
        Map<Integer, VM> vms = new HashMap<>();

        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isFirstLine = true;

            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header "id"
                }
                if (line.startsWith("#") || line.isBlank())
                    continue;

                int vmId = Integer.parseInt(line.trim());
                VM vm = new VM(vmId);
                vms.put(vmId, vm);
            }
        }
        return vms;
    }

    // Carica le capacità di elaborazione dal file processing_capacity.csv
    private static void loadProcessingCapacities(String filePath, Map<Integer, VM> vmMapping) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            boolean isFirstLine = true;
            while ((line = br.readLine()) != null) {
                if (isFirstLine) {
                    isFirstLine = false;
                    continue; // Skip header "vm_id,processing_capacity"
                }
                if (line.startsWith("#") || line.isBlank())
                    continue;
                String[] parts = line.trim().split(",");

                int vmId = Integer.parseInt(parts[0]);
                double processingCapacity = Double.parseDouble(parts[1]);

                // Aggiunge la capacità di elaborazione alla VM corrispondente
                VM vm = vmMapping.get(vmId);
                if (vm != null) {
                    vm.addCapability("processingCapacity", processingCapacity);
                } else {
                    System.out.println("   WARNING: VM " + vmId + " not found in vmMapping");
                }
            }
        }
    }

    // Genera costi di comunicazione usando la formula: (size_task * CCR) / (average
    // bandwidth between VMs)
    private static Map<String, Double> generateDummyCommunicationCosts(List<task> tasks, Map<Integer, VM> vms) {
        final double CCR = 0.4; // Communication-to-Computation Ratio

        // Calcola la larghezza di banda media tra tutte le VM
        double totalBandwidth = 0.0;
        int bandwidthCount = 0;

        for (VM vm1 : vms.values()) {
            for (VM vm2 : vms.values()) {
                if (vm1.getID() != vm2.getID()) {
                    double bandwidth = vm1.getBandwidthToVM(vm2.getID());
                    if (bandwidth > 0) {
                        totalBandwidth += bandwidth;
                        bandwidthCount++;
                    }
                }
            }
        }

        double averageBandwidth = bandwidthCount > 0 ? totalBandwidth / bandwidthCount : 1.0;
        System.out.println("   Average bandwidth between VMs: " + String.format("%.2f", averageBandwidth));

        // Calcola i costi di comunicazione per ogni arco del DAG
        Map<String, Double> commCosts = new HashMap<>();
        for (task t : tasks) {
            if (t.getSucc() != null) {
                for (int succId : t.getSucc()) {
                    // Formula: communication_cost = (size_task * CCR) / (average bandwidth between
                    // VMs)
                    double cost = (t.getSize() * CCR) / averageBandwidth;
                    commCosts.put(t.getID() + "_" + succId, cost);
                }
            }
        }
        return commCosts;
    }

    // Test delle metriche implementate
    private static void testMetrics(List<task> tasks, Map<Integer, VM> vms, Map<String, Double> communicationCosts,
            Set<Integer> dcpCriticalPath, Map<String, task> taskMap) {
        System.out.println("   Testing Ttrans, ST, ET, and FT metrics with real CSV data...");

        // Trova task specifici dal CSV per esempi realistici
        task task2 = tasks.stream().filter(t -> t.getID() == 2).findFirst().orElse(null);
        task task3 = tasks.stream().filter(t -> t.getID() == 3).findFirst().orElse(null);
        task task0 = tasks.stream().filter(t -> t.getID() == 0).findFirst().orElse(null);

        if (task2 == null || task3 == null) {
            System.out.println("   Required tasks (t2, t3) not found in CSV data.");
            return;
        }

        // Usa VM specifiche
        VM vm0 = vms.get(0); // vm1 nel CSV
        VM vm1 = vms.get(1); // vm2 nel CSV

        if (vm0 == null || vm1 == null) {
            System.out.println("   Required VMs not found.");
            return;
        }

        System.out.println("   Using real CSV data:");
        System.out.println("     Task2: ID=" + task2.getID() + ", size=" + String.format("%.1f", task2.getSize())
                + ", successors=" + task2.getSucc());
        System.out.println("     Task3: ID=" + task3.getID() + ", size=" + String.format("%.1f", task3.getSize())
                + ", predecessors=" + task3.getPre());
        System.out.println("     VM0: ID=" + vm0.getID() + ", capacity="
                + String.format("%.2f", vm0.getCapability("processingCapacity")));
        System.out.println("     VM1: ID=" + vm1.getID() + ", capacity="
                + String.format("%.2f", vm1.getCapability("processingCapacity")));
        System.out.println("     Bandwidth VM0→VM1: " + String.format("%.2f", vm0.getBandwidthToVM(vm1.getID())));

        // Test 1: Ttrans - Transmission Time (usando comunicazione reale dal DAG)
        System.out.println("\n   Testing Ttrans (Transmission Time) - Real DAG edge t2→t3:");
        double CCR = 0.4; // Communication-to-Computation Ratio
        double ttrans = Metrics.Ttrans(task2, task3, vm0, vm1, CCR);
        double calculatedDataSize = task2.getSize() * CCR; // TTi,j = si * CCR
        System.out.println("     Ttrans(t2→t3, vm0→vm1) = " + String.format("%.4f", ttrans));
        System.out.println("     TTi,j = si * CCR = " + String.format("%.1f", task2.getSize()) + " * " + CCR + " = "
                + String.format("%.2f", calculatedDataSize));
        System.out.println("     Formula: TTi,j(" + String.format("%.2f", calculatedDataSize) + ") / bandwidth("
                + vm0.getBandwidthToVM(vm1.getID()) + ") = " + String.format("%.4f", ttrans));

        // Test special case: Same VM transmission
        System.out.println("\n   Testing Ttrans special case - Same VM:");
        double ttrans_same_vm = Metrics.Ttrans(task2, task3, vm0, vm0, CCR);
        System.out.println("     Ttrans(t2→t3, vm0→vm0) = " + String.format("%.4f", ttrans_same_vm));
        System.out.println("     (Should be 0.0000 because both tasks are on the same VM)");

        // Test 2: ET - Execution Time (usando task reali dal CSV)
        System.out.println("\n   Testing ET (Execution Time) with real task sizes:");
        double et_t2_vm0 = Metrics.ET(task2, vm0, "processingCapacity");
        double et_t3_vm1 = Metrics.ET(task3, vm1, "processingCapacity");
        System.out.println("     ET(t2, vm0) = " + String.format("%.4f", et_t2_vm0));
        System.out.println("     Formula: size(" + String.format("%.1f", task2.getSize()) + ") / capacity("
                + String.format("%.2f", vm0.getCapability("processingCapacity")) + ") = "
                + String.format("%.4f", et_t2_vm0));
        System.out.println("     ET(t3, vm1) = " + String.format("%.4f", et_t3_vm1));
        System.out.println("     Formula: size(" + String.format("%.1f", task3.getSize()) + ") / capacity("
                + String.format("%.2f", vm1.getCapability("processingCapacity")) + ") = "
                + String.format("%.4f", et_t3_vm1));

        // Test 3: ST - Start Time per entry task (t0 se esiste)
        System.out.println("\n   Testing ST (Start Time):");
        if (task0 != null && (task0.getPre() == null || task0.getPre().isEmpty())) {
            double st_t0 = Metrics.ST(task0, vm0);
            System.out
                    .println("     ST(t0, vm0) = " + String.format("%.4f", st_t0) + " (entry task - no predecessors)");
        }

        // Test per task con predecessori (t3 ha t2 come predecessore dal DAG)
        if (task3.getPre() != null && !task3.getPre().isEmpty()) {
            System.out.println("     ST for t3 (has predecessors " + task3.getPre() + "):");

            // Crea mappe realistiche per i predecessori di t3
            Map<Integer, Double> predecessorFinishTimes = new HashMap<>();
            Map<Integer, task> predecessorTasks = new HashMap<>();
            Map<Integer, VM> predecessorVMs = new HashMap<>();
            Map<Integer, Double> dataTransferSizes = new HashMap<>();

            // Per ogni predecessore di t3
            for (Integer predId : task3.getPre()) {
                task predTask = tasks.stream().filter(t -> t.getID() == predId).findFirst().orElse(null);
                if (predTask != null) {
                    // Calcola finish time del predecessore (simulato)
                    double predET = Metrics.ET(predTask, vm0, "processingCapacity");
                    predecessorFinishTimes.put(predId, predET); // Assumendo ST=0 per entry task
                    predecessorTasks.put(predId, predTask);
                    predecessorVMs.put(predId, vm0);

                    // TTi,j viene ora calcolato automaticamente in Ttrans usando si * CCR
                    // Non serve più dataTransferSizes, ma lo manteniamo per compatibilità
                    dataTransferSizes.put(predId, predTask.getSize() * 0.4);

                    System.out.println("       Predecessor t" + predId + ": FT=" + String.format("%.4f", predET)
                            + ", TTi,j=" + String.format("%.2f", predTask.getSize() * 0.4));
                }
            }

            double st_t3 = Metrics.ST(task3, vm1, false,
                    predecessorFinishTimes, predecessorTasks,
                    predecessorVMs, dataTransferSizes);
            System.out.println("     ST(t3, vm1) = " + String.format("%.4f", st_t3));
            System.out.println("     (Calculated as max{FT_predecessor + Ttrans} for all predecessors)");
        }

        // Test 4: FT - Finish Time (usando dati reali)
        System.out.println("\n   Testing FT (Finish Time) with real data:");
        double ft_t2 = Metrics.FT(task2, vm0, "processingCapacity");
        System.out.println("     FT(t2, vm0) = " + String.format("%.4f", ft_t2) + " (entry task)");
        System.out.println("     Formula: ST(0.0000) + ET(" + String.format("%.4f", et_t2_vm0) + ") = "
                + String.format("%.4f", ft_t2));

        // For t3 (which has predecessors), calculate correctly using ST + ET
        if (task3.getPre() != null && !task3.getPre().isEmpty()) {
            System.out.println("     FT(t3, vm1) - t3 has predecessors " + task3.getPre() + ":");

            // Create proper maps for predecessors to calculate ST correctly
            Map<Integer, Double> predecessorFinishTimes = new HashMap<>();
            Map<Integer, task> predecessorTasks = new HashMap<>();
            Map<Integer, VM> predecessorVMs = new HashMap<>();
            Map<Integer, Double> dataTransferSizes = new HashMap<>();

            // For each predecessor of t3, use the real calculated FT
            for (Integer predId : task3.getPre()) {
                task predTask = tasks.stream().filter(t -> t.getID() == predId).findFirst().orElse(null);
                if (predTask != null) {
                    // Use the actual finish time of the predecessor
                    double predFT = (predId == 2) ? ft_t2 : Metrics.FT(predTask, vm0, "processingCapacity");
                    predecessorFinishTimes.put(predId, predFT);
                    predecessorTasks.put(predId, predTask);
                    predecessorVMs.put(predId, vm0);
                    dataTransferSizes.put(predId, predTask.getSize() * 0.4);

                    System.out.println("       Predecessor t" + predId + ": FT=" + String.format("%.4f", predFT));
                }
            }

            // Calculate ST for t3 considering predecessors
            double st_t3 = Metrics.ST(task3, vm1, false,
                    predecessorFinishTimes, predecessorTasks,
                    predecessorVMs, dataTransferSizes);

            // Calculate FT = ST + ET
            double ft_t3 = st_t3 + et_t3_vm1;

            System.out.println("     ST(t3, vm1) = " + String.format("%.4f", st_t3) + " (considering predecessors)");
            System.out.println("     ET(t3, vm1) = " + String.format("%.4f", et_t3_vm1));
            System.out.println("     FT(t3, vm1) = ST(" + String.format("%.4f", st_t3) + ") + ET("
                    + String.format("%.4f", et_t3_vm1) + ") = " + String.format("%.4f", ft_t3));

            // Alternative: use the complete FT method
            double ft_t3_complete = Metrics.FT(task3, vm1, "processingCapacity", false,
                    predecessorFinishTimes, predecessorTasks,
                    predecessorVMs, dataTransferSizes);
            System.out.println("     FT(t3, vm1) using complete method = " + String.format("%.4f", ft_t3_complete)
                    + " (should match above)");

        } else {
            double ft_t3 = Metrics.FT(task3, vm1, "processingCapacity");
            System.out.println("     FT(t3, vm1) = " + String.format("%.4f", ft_t3) + " (entry task)");
        }

        // Test 5: Scenario completo per una dipendenza reale dal DAG
        System.out.println("\n   Complete scenario test (t2→t3 dependency from DAG):");
        System.out.println("     1. t2 executes on vm0: ET = " + String.format("%.4f", et_t2_vm0));
        System.out.println("     2. t2 finishes at: FT = " + String.format("%.4f", ft_t2));
        System.out.println("     3. TTi,j = si * CCR = " + String.format("%.1f", task2.getSize()) + " * 0.4 = "
                + String.format("%.2f", task2.getSize() * 0.4));
        System.out.println("     4. Data transmission t2→t3 (vm0→vm1): Ttrans = " + String.format("%.4f", ttrans));
        System.out.println("     5. t3 can start at: ST(t3) = FT(t2) + Ttrans = " + String.format("%.4f", ft_t2) + " + "
                + String.format("%.4f", ttrans) + " = " + String.format("%.4f", ft_t2 + ttrans));
        System.out.println("     6. t3 execution time on vm1: ET = " + String.format("%.4f", et_t3_vm1));
        System.out.println("     7. t3 finishes at: FT(t3) = ST(t3) + ET(t3) = " + String.format("%.4f", ft_t2 + ttrans)
                + " + " + String.format("%.4f", et_t3_vm1) + " = " + String.format("%.4f", ft_t2 + ttrans + et_t3_vm1));

        // Test 6: Makespan calculation
        System.out.println("\n   Testing Makespan calculation:");

        // Create sample task finish times
        Map<Integer, Double> allTaskFinishTimes = new HashMap<>();
        allTaskFinishTimes.put(task2.getID(), ft_t2);
        if (task3.getPre() != null && !task3.getPre().isEmpty()) {
            allTaskFinishTimes.put(task3.getID(), ft_t2 + ttrans + et_t3_vm1);
        } else {
            allTaskFinishTimes.put(task3.getID(), Metrics.FT(task3, vm1, "processingCapacity"));
        }

        // Handle task0 - check if it has predecessors
        if (task0 != null) {
            if (task0.getPre() == null || task0.getPre().isEmpty()) {
                // Entry task - can calculate FT directly
                allTaskFinishTimes.put(task0.getID(), Metrics.FT(task0, vm0, "processingCapacity"));
                System.out.println("     Task0 is entry task: FT = "
                        + String.format("%.4f", Metrics.FT(task0, vm0, "processingCapacity")));
            } else {
                // Task has predecessors - skip for makespan test or use estimated value
                System.out.println(
                        "     Task0 has predecessors " + task0.getPre() + " - using estimated FT for makespan test");
                // Use a reasonable estimated value instead of Infinity
                double estimatedFT = Metrics.ET(task0, vm0, "processingCapacity") + 10.0; // Estimate: ET + some delay
                allTaskFinishTimes.put(task0.getID(), estimatedFT);
                System.out.println("     Task0 estimated FT = " + String.format("%.4f", estimatedFT) + " (ET + 10.0)");
            }
        }

        // Test VM-specific makespan
        Map<Integer, List<task>> vmAssignments = new HashMap<>();
        List<task> vm0Tasks = new ArrayList<>();
        List<task> vm1Tasks = new ArrayList<>();

        vm0Tasks.add(task2);
        if (task0 != null)
            vm0Tasks.add(task0);
        vm1Tasks.add(task3);

        vmAssignments.put(0, vm0Tasks);
        vmAssignments.put(1, vm1Tasks);

        // Calculate makespan using the complete method: makespan = max(MS(VMk))
        double workflowMakespan = Metrics.makespan(vms, vmAssignments, allTaskFinishTimes);
        System.out.println("     Workflow makespan = " + String.format("%.4f", workflowMakespan));
        System.out.println("     (Calculated as max(MS(VMk)) across all VMs)");

        double ms_vm0 = Metrics.MS(vm0, vm0Tasks, allTaskFinishTimes);
        double ms_vm1 = Metrics.MS(vm1, vm1Tasks, allTaskFinishTimes);

        System.out.println("     MS(VM0) = " + String.format("%.4f", ms_vm0) + " (tasks: "
                + vm0Tasks.stream().map(t -> "t" + t.getID()).toList() + ")");
        System.out.println("     MS(VM1) = " + String.format("%.4f", ms_vm1) + " (tasks: "
                + vm1Tasks.stream().map(t -> "t" + t.getID()).toList() + ")");
        System.out.println("     Workflow makespan = max(MS(VM0), MS(VM1)) = max(" + String.format("%.4f", ms_vm0)
                + ", " + String.format("%.4f", ms_vm1) + ") = " + String.format("%.4f", Math.max(ms_vm0, ms_vm1)));

        // Test 7: SLR with real DCP Critical Path
        if (dcpCriticalPath != null && !dcpCriticalPath.isEmpty()) {
            System.out.println("\n   Testing SLR (Scheduling Length Ratio) with DCP Critical Path:");
            System.out.println("     DCP Critical Path: " + dcpCriticalPath);

            // Calculate SLR using the real critical path from DCP algorithm
            double dcpSLR = Metrics.SLR(workflowMakespan, dcpCriticalPath, taskMap, vms, "processingCapacity");
            System.out.println("     DCP SLR = " + String.format("%.4f", dcpSLR));

            // Show detailed breakdown
            System.out.println("     DCP Critical Path minimum execution times:");
            double dcpSumMinET = 0.0;
            for (Integer taskId : dcpCriticalPath.stream().sorted().toList()) {
                task cpTask = taskMap.get("t" + taskId);
                if (cpTask != null) {
                    double minET = Double.POSITIVE_INFINITY;
                    VM bestVM = null;

                    // Find minimum ET across all VMs
                    for (VM vm : vms.values()) {
                        double et = Metrics.ET(cpTask, vm, "processingCapacity");
                        if (et < minET) {
                            minET = et;
                            bestVM = vm;
                        }
                    }

                    dcpSumMinET += minET;
                    System.out.println("       Task " + taskId + ": min ET = " + String.format("%.4f", minET)
                            + " (on VM" + (bestVM.getID() + 1) + ")");
                }
            }

            System.out.println("     DCP Sum of minimum execution times = " + String.format("%.4f", dcpSumMinET));
            System.out.println("     DCP SLR = makespan / sum_min_ET = " + String.format("%.4f", workflowMakespan)
                    + " / " + String.format("%.4f", dcpSumMinET) + " = " + String.format("%.4f", dcpSLR));

            // DCP SLR Interpretation
            if (dcpSLR < 1.5) {
                System.out.println("     DCP SLR Interpretation: Excellent scheduling efficiency (SLR < 1.5)");
            } else if (dcpSLR < 2.0) {
                System.out.println("     DCP SLR Interpretation: Good scheduling efficiency (1.5 ≤ SLR < 2.0)");
            } else if (dcpSLR < 3.0) {
                System.out.println("     DCP SLR Interpretation: Fair scheduling efficiency (2.0 ≤ SLR < 3.0)");
            } else {
                System.out.println("     DCP SLR Interpretation: Poor scheduling efficiency (SLR ≥ 3.0)");
            }
        }

        // Test 8: AVU - Average VM Utilization
        System.out.println("\n   Testing AVU (Average VM Utilization):");

        // Calculate individual VM utilizations
        System.out.println("     Individual VM utilizations:");
        double vu_vm0 = Metrics.VU(vm0, vm0Tasks, workflowMakespan, "processingCapacity");
        double vu_vm1 = Metrics.VU(vm1, vm1Tasks, workflowMakespan, "processingCapacity");

        System.out.println(
                "     VU(VM0) = " + String.format("%.4f", vu_vm0) + " (" + String.format("%.1f%%", vu_vm0 * 100) + ")");
        System.out.println("       Tasks on VM0: " + vm0Tasks.stream().map(t -> "t" + t.getID()).toList());

        // Calculate sum of execution times for VM0
        double sumET_vm0 = 0.0;
        for (task t : vm0Tasks) {
            double et = Metrics.ET(t, vm0, "processingCapacity");
            sumET_vm0 += et;
            System.out.println("         ET(t" + t.getID() + ", VM0) = " + String.format("%.4f", et));
        }
        System.out.println("       Sum of ET on VM0 = " + String.format("%.4f", sumET_vm0));
        System.out.println("       VU(VM0) = " + String.format("%.4f", sumET_vm0) + " / "
                + String.format("%.4f", workflowMakespan) + " = " + String.format("%.4f", vu_vm0));

        System.out.println(
                "     VU(VM1) = " + String.format("%.4f", vu_vm1) + " (" + String.format("%.1f%%", vu_vm1 * 100) + ")");
        System.out.println("       Tasks on VM1: " + vm1Tasks.stream().map(t -> "t" + t.getID()).toList());

        // Calculate sum of execution times for VM1
        double sumET_vm1 = 0.0;
        for (task t : vm1Tasks) {
            double et = Metrics.ET(t, vm1, "processingCapacity");
            sumET_vm1 += et;
            System.out.println("         ET(t" + t.getID() + ", VM1) = " + String.format("%.4f", et));
        }
        System.out.println("       Sum of ET on VM1 = " + String.format("%.4f", sumET_vm1));
        System.out.println("       VU(VM1) = " + String.format("%.4f", sumET_vm1) + " / "
                + String.format("%.4f", workflowMakespan) + " = " + String.format("%.4f", vu_vm1));

        // Calculate Average VM Utilization
        double avu = Metrics.AVU(vms, vmAssignments, workflowMakespan, "processingCapacity");
        System.out.println("     AVU = (VU(VM0) + VU(VM1)) / 2 = (" + String.format("%.4f", vu_vm0) + " + "
                + String.format("%.4f", vu_vm1) + ") / 2 = " + String.format("%.4f", avu));
        System.out
                .println("     AVU = " + String.format("%.4f", avu) + " (" + String.format("%.1f%%", avu * 100) + ")");

        // AVU Interpretation
        if (avu > 0.8) {
            System.out.println("     AVU Interpretation: Excellent VM utilization (AVU > 80%)");
        } else if (avu > 0.6) {
            System.out.println("     AVU Interpretation: Good VM utilization (60% < AVU ≤ 80%)");
        } else if (avu > 0.4) {
            System.out.println("     AVU Interpretation: Fair VM utilization (40% < AVU ≤ 60%)");
        } else {
            System.out.println("     AVU Interpretation: Poor VM utilization (AVU ≤ 40%)");
        }

        // Test 9: VF - Variance of Fairness
        System.out.println("\n   Testing VF (Variance of Fairness):");

        // Calculate VF using all tasks and their assignments
        double vf = Metrics.VF(tasks, vms, vmAssignments, allTaskFinishTimes, "processingCapacity");
        System.out.println("     VF = " + String.format("%.6f", vf));

        // Show detailed breakdown of task satisfactions
        System.out.println("     Task satisfaction breakdown:");
        double sumSatisfactions = 0.0;
        int satisfactionCount = 0;

        for (task t : Arrays.asList(task2, task3, task0).stream().filter(Objects::nonNull).toList()) {
            // Find assigned VM
            VM assignedVM = null;
            for (Map.Entry<Integer, List<task>> entry : vmAssignments.entrySet()) {
                if (entry.getValue().contains(t)) {
                    assignedVM = vms.get(entry.getKey());
                    break;
                }
            }

            if (assignedVM != null) {
                double satisfaction = Metrics.taskSatisfaction(t, assignedVM, vms, "processingCapacity");
                sumSatisfactions += satisfaction;
                satisfactionCount++;

                // Calculate AET and EET for detailed output
                double aet = Metrics.ET(t, assignedVM, "processingCapacity");
                double eet = Double.POSITIVE_INFINITY;
                VM fastestVM = null;

                for (VM vm : vms.values()) {
                    double et = Metrics.ET(t, vm, "processingCapacity");
                    if (et < eet) {
                        eet = et;
                        fastestVM = vm;
                    }
                }

                System.out.println("       Task " + t.getID() + ": AET=" + String.format("%.4f", aet) +
                        " (VM" + (assignedVM.getID() + 1) + "), EET=" + String.format("%.4f", eet) +
                        " (VM" + (fastestVM.getID() + 1) + "), S=" + String.format("%.4f", satisfaction));
            }
        }

        // Calculate and show average satisfaction
        double avgSatisfaction = satisfactionCount > 0 ? sumSatisfactions / satisfactionCount : 0.0;
        System.out.println("     Average satisfaction (M) = " + String.format("%.4f", avgSatisfaction));

        // Show VF formula breakdown
        System.out.println("     VF formula: VF = (1/n) * ∑(M - Si)²");
        double sumSquaredDeviations = 0.0;

        for (task t : Arrays.asList(task2, task3, task0).stream().filter(Objects::nonNull).toList()) {
            VM assignedVM = null;
            for (Map.Entry<Integer, List<task>> entry : vmAssignments.entrySet()) {
                if (entry.getValue().contains(t)) {
                    assignedVM = vms.get(entry.getKey());
                    break;
                }
            }

            if (assignedVM != null) {
                double satisfaction = Metrics.taskSatisfaction(t, assignedVM, vms, "processingCapacity");
                double deviation = avgSatisfaction - satisfaction;
                double squaredDeviation = deviation * deviation;
                sumSquaredDeviations += squaredDeviation;

                System.out.println("       Task " + t.getID() + ": (M - S" + t.getID() + ")² = (" +
                        String.format("%.4f", avgSatisfaction) + " - " + String.format("%.4f", satisfaction) +
                        ")² = " + String.format("%.6f", squaredDeviation));
            }
        }

        System.out.println("     VF = " + String.format("%.6f", sumSquaredDeviations) + " / " + satisfactionCount +
                " = " + String.format("%.6f", vf));

        // VF Interpretation
        if (vf < 0.01) {
            System.out
                    .println("     VF Interpretation: Excellent fairness (VF < 0.01) - very uniform task satisfaction");
        } else if (vf < 0.05) {
            System.out.println(
                    "     VF Interpretation: Good fairness (0.01 ≤ VF < 0.05) - moderate task satisfaction variance");
        } else if (vf < 0.1) {
            System.out.println(
                    "     VF Interpretation: Fair fairness (0.05 ≤ VF < 0.1) - noticeable task satisfaction differences");
        } else {
            System.out.println("     VF Interpretation: Poor fairness (VF ≥ 0.1) - high variance in task satisfaction");
        }

        System.out.println("\n   Metrics testing completed with real CSV data!");
    }

    // Test dell'algoritmo SMGT
    private static void testSMGTAlgorithm(String taskFile, String dagFile, String processingCapacityFile) {
        try {
            System.out.println("   Initializing SMGT algorithm...");

            SMGT smgt = new SMGT();

            // Load VM data from CSV
            smgt.loadVMsFromCSV(processingCapacityFile);
            System.out.println("   Loaded VM processing capacities");

            // Load task data and DAG structure
            smgt.loadTasksFromCSV(dagFile, taskFile);
            System.out.println("   Loaded DAG structure and task data");

            // Display VM processing capacities
            System.out.println("\n   VM Processing Capacities:");
            for (int i = 0; i < smgt.getVMs().size(); i++) {
                VM vm = smgt.getVMs().get(i);
                double capacity = vm.getCapability("processing");
                System.out.println("     VM " + i + ": " + capacity);
            }

            // Display task levels
            System.out.println("\n   DAG Level Information:");
            for (int level : smgt.getLevelTasks().keySet()) {
                int taskCount = smgt.getLevelTasks().get(level).size();
                List<Integer> tasksAtLevel = smgt.getLevelTasks().get(level);
                System.out.println("     Level " + level + ": " + taskCount + " tasks " +
                        tasksAtLevel.stream().map(t -> "t" + t).reduce((a, b) -> a + ", " + b).orElse(""));
            }

            // Generate and display preference matrices
            smgt.printPreferenceMatrices();

            // Display threshold calculations
            System.out.println("\n   Threshold Calculations:");
            int maxLevel = smgt.getLevelTasks().keySet().stream().max(Integer::compare).orElse(0);

            for (int vm = 0; vm < smgt.getVMs().size(); vm++) {
                System.out.println("     VM" + vm + " thresholds:");
                for (int level = 0; level <= maxLevel; level++) {
                    try {
                        int threshold = smgt.calculateInitialThreshold(vm, level);
                        System.out.printf("       threshold(%d, %d) = %d%n", vm, level, threshold);
                    } catch (Exception e) {
                        System.out.println(
                                "       Error calculating threshold(" + vm + ", " + level + "): " + e.getMessage());
                    }
                }
            }

            // Run the complete SMGT algorithm
            System.out.println("\n   Executing SMGT Algorithm:");
            Map<Integer, List<Integer>> finalAssignments = smgt.runSMGTAlgorithm();

            // Additional analysis
            System.out.println("\n   SMGT Algorithm Analysis:");

            // Count total tasks assigned
            int totalAssignedTasks = finalAssignments.values().stream()
                    .mapToInt(List::size)
                    .sum();
            System.out.println("     Total tasks assigned: " + totalAssignedTasks);

            // Calculate load balance
            double avgTasksPerVM = (double) totalAssignedTasks / smgt.getVMs().size();
            System.out.println("     Average tasks per VM: " + String.format("%.2f", avgTasksPerVM));

            // Show load distribution
            System.out.println("     Load distribution:");
            for (Map.Entry<Integer, List<Integer>> entry : finalAssignments.entrySet()) {
                int vmIndex = entry.getKey();
                int taskCount = entry.getValue().size();
                double loadRatio = taskCount / avgTasksPerVM;
                System.out.println("       VM" + vmIndex + ": " + taskCount + " tasks (load ratio: " +
                        String.format("%.2f", loadRatio) + ")");
            }

            // Calculate estimated makespan for SMGT
            System.out.println("\n     Estimated SMGT Performance:");
            double maxVMFinishTime = 0.0;

            for (Map.Entry<Integer, List<Integer>> entry : finalAssignments.entrySet()) {
                int vmIndex = entry.getKey();
                List<Integer> assignedTasks = entry.getValue();
                VM vm = smgt.getVMs().get(vmIndex);

                double vmExecutionTime = 0.0;
                for (Integer taskId : assignedTasks) {
                    task t = smgt.getTaskById(taskId);
                    if (t != null) {
                        double executionTime = t.getSize() / vm.getCapability("processing");
                        vmExecutionTime += executionTime;
                    }
                }

                maxVMFinishTime = Math.max(maxVMFinishTime, vmExecutionTime);
                System.out.println("       VM" + vmIndex + " total execution time: " +
                        String.format("%.4f", vmExecutionTime));
            }

            System.out.println("     Estimated SMGT makespan: " + String.format("%.4f", maxVMFinishTime));

            System.out.println("\n   SMGT algorithm testing completed successfully!");

        } catch (IOException e) {
            System.err.println("   Error running SMGT algorithm: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("   Unexpected error in SMGT algorithm: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Test the LOTD (List of Task Duplication) algorithm
     */
    public static void testLOTDAlgorithm() {
        System.out.println("\n=== LOTD Algorithm Test ===");

        try {
            // File paths
            String taskFile = "../data/task.csv";
            String dagFile = "../data/dag.csv";
            String processingCapacityFile = "../data/processing_capacity.csv";

            System.out.println("\n1. Loading data for LOTD algorithm...");

            // Create and configure SMGT instance
            SMGT smgt = new SMGT();
            smgt.loadTasksFromCSV(dagFile, taskFile);
            smgt.loadVMsFromCSV(processingCapacityFile);

            System.out.println("   Data loaded successfully");
            System.out.println("   Tasks: " + smgt.getTasks().size());
            System.out.println("   VMs: " + smgt.getVMs().size());

            // Create and execute LOTD algorithm
            System.out.println("\n2. Creating LOTD instance...");
            LOTD lotd = new LOTD(smgt);

            System.out.println("\n3. Executing LOTD algorithm...");
            Map<Integer, List<Integer>> finalSchedule = lotd.executeLOTD();

            // Display results
            System.out.println("\n4. LOTD Algorithm Results:");
            System.out.println("   Final Task Assignments:");
            for (Map.Entry<Integer, List<Integer>> entry : finalSchedule.entrySet()) {
                System.out.println("     VM" + entry.getKey() + ": " + entry.getValue());
            }

            System.out.println("\n   Task Start Times (AST):");
            Map<Integer, Double> taskAST = lotd.getTaskAST();
            for (Map.Entry<Integer, Double> entry : taskAST.entrySet()) {
                System.out.println("     Task" + entry.getKey() + ": " + String.format("%.4f", entry.getValue()));
            }

            System.out.println("\n   Task Finish Times (AFT):");
            Map<Integer, Double> taskAFT = lotd.getTaskAFT();
            for (Map.Entry<Integer, Double> entry : taskAFT.entrySet()) {
                System.out.println("     Task" + entry.getKey() + ": " + String.format("%.4f", entry.getValue()));
            }

            System.out.println("\n   Duplicated Tasks:");
            Map<Integer, Set<Integer>> duplicatedTasks = lotd.getDuplicatedTasks();
            for (Map.Entry<Integer, Set<Integer>> entry : duplicatedTasks.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    System.out.println("     VM" + entry.getKey() + ": " + entry.getValue());
                }
            }

            // Calculate makespan
            double makespan = taskAFT.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            System.out.println("\n   LOTD Makespan: " + String.format("%.4f", makespan));

            System.out.println("\n   LOTD algorithm testing completed successfully!");

        } catch (IOException e) {
            System.err.println("   Error running LOTD algorithm: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("   Unexpected error in LOTD algorithm: " + e.getMessage());
            e.printStackTrace();
        }
    }
}