import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class MainCCRAnalysis {

    public static void main(String[] args) throws IOException {
        System.out.println("=== CCR Analysis for Montage Workflow ===");
        
        // File paths
        String taskFile = "../data/task.csv";
        String dagFile = "../data/dag.csv";
        String vmFile = "../data/vm.csv";
        String processingCapacityFile = "../data/processing_capacity.csv";
        String resultsFile = "ccr_analysis_results.json";

        // CCR range configuration
        double ccrMin = 0.4;
        double ccrMax = 2.0;
        int numPoints = 9;  // 0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0
        
        System.out.println("CCR Range: " + ccrMin + " to " + ccrMax + " (" + numPoints + " points)");
        
        // Load data once
        System.out.println("\nLoading workflow data...");
        Map<String, task> taskMap = loadTasks(taskFile);
        System.out.println("Loaded " + taskMap.size() + " tasks");

        loadDependencies(dagFile, taskMap);
        System.out.println("Loaded task dependencies");

        List<task> taskList = new ArrayList<>(taskMap.values());
        
        Map<Integer, VM> vmMapping = loadVMs(vmFile);
        System.out.println("Loaded " + vmMapping.size() + " VMs");

        loadProcessingCapacities(processingCapacityFile, vmMapping);
        System.out.println("Loaded processing capacities");

        // Find exit task
        task exitTask = findExitTask(taskList);
        System.out.println("Using Task " + exitTask.getID() + " as exit task");

        // Analyze CCR impact
        System.out.println("\n=== Starting CCR Analysis ===");
        List<CCRResult> results = new ArrayList<>();
        
        for (int i = 0; i < numPoints; i++) {
            double ccr = ccrMin + (ccrMax - ccrMin) * i / (numPoints - 1);
            System.out.println("\nTest " + (i + 1) + "/" + numPoints + ": CCR = " + String.format("%.2f", ccr));
            
            try {
                CCRResult result = runAnalysisForCCR(taskList, vmMapping, exitTask, ccr);
                results.add(result);
                
                System.out.println("  Makespan: " + String.format("%.2f", result.makespan));
                System.out.println("  SLR: " + String.format("%.3f", result.slr));
                
            } catch (Exception e) {
                System.out.println("  Error in analysis: " + e.getMessage());
            }
        }
        
        // Save results to JSON file
        saveResultsToJson(results, resultsFile);
        System.out.println("\nResults saved to: " + resultsFile);
        System.out.println("Total successful tests: " + results.size() + "/" + numPoints);
    }
    
    private static CCRResult runAnalysisForCCR(List<task> taskList, Map<Integer, VM> vmMapping, 
                                              task exitTask, double ccr) {
        // Generate communication costs with specific CCR
        Map<String, Double> communicationCosts = generateCommunicationCosts(taskList, vmMapping, ccr);
        
        // Organize tasks by levels
        Map<Integer, List<Integer>> levels = DCP.organizeTasksByLevels(taskList);
        
        // Execute DCP algorithm
        Set<Integer> criticalPath = DCP.executeDCP(taskList, levels, exitTask, communicationCosts, vmMapping);
        
        // Calculate metrics
        Map<String, task> taskMap = taskList.stream()
                .collect(Collectors.toMap(t -> "t" + t.getID(), t -> t));
        
        // We need to simulate task assignments and finish times for makespan calculation
        // For now, use a simplified approach with DCP critical path
        double makespan = calculateSimplifiedMakespan(taskList, vmMapping, communicationCosts);
        double slr = Metrics.SLR(makespan, criticalPath, taskMap, vmMapping, "processingCapacity");
        
        return new CCRResult(ccr, makespan, slr, criticalPath.size());
    }
    
    private static Map<String, Double> generateCommunicationCosts(List<task> tasks, Map<Integer, VM> vms, double ccr) {
        // Calculate average bandwidth between VMs
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
        
        // Calculate communication costs for each DAG edge using specified CCR
        Map<String, Double> commCosts = new HashMap<>();
        for (task t : tasks) {
            if (t.getSucc() != null) {
                for (int succId : t.getSucc()) {
                    // Formula: communication_cost = (size_task * CCR) / (average bandwidth between VMs)
                    double cost = (t.getSize() * ccr) / averageBandwidth;
                    commCosts.put(t.getID() + "_" + succId, cost);
                }
            }
        }
        return commCosts;
    }
    
    private static double calculateSimplifiedMakespan(List<task> taskList, Map<Integer, VM> vmMapping, 
                                                     Map<String, Double> communicationCosts) {
        // Simplified makespan calculation using critical path approach
        // This is a basic implementation - in practice you'd want a full scheduling simulation
        
        double maxExecutionTime = 0.0;
        
        // Calculate execution time for each task on its best VM
        for (task t : taskList) {
            double minExecTime = Double.POSITIVE_INFINITY;
            
            // Find the VM with minimum execution time for this task
            for (VM vm : vmMapping.values()) {
                double execTime = Metrics.ET(t, vm, "processingCapacity");
                minExecTime = Math.min(minExecTime, execTime);
            }
            
            // Add communication costs (simplified)
            double totalTaskTime = minExecTime;
            if (t.getSucc() != null) {
                for (int succId : t.getSucc()) {
                    String commKey = t.getID() + "_" + succId;
                    Double commCost = communicationCosts.get(commKey);
                    if (commCost != null) {
                        totalTaskTime += commCost;
                    }
                }
            }
            
            maxExecutionTime = Math.max(maxExecutionTime, totalTaskTime);
        }
        
        return maxExecutionTime;
    }
    
    private static task findExitTask(List<task> taskList) {
        return taskList.stream()
                .filter(t -> t.getSucc() == null || t.getSucc().isEmpty())
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No exit task found"));
    }
    
    private static void saveResultsToJson(List<CCRResult> results, String filename) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("{");
            writer.println("  \"analysis_type\": \"CCR_vs_SLR_Makespan\",");
            writer.println("  \"workflow\": \"Montage\",");
            writer.println("  \"results\": [");
            
            for (int i = 0; i < results.size(); i++) {
                CCRResult result = results.get(i);
                writer.print("    {");
                writer.print("\"ccr\": " + result.ccr + ", ");
                writer.print("\"makespan\": " + result.makespan + ", ");
                writer.print("\"slr\": " + result.slr + ", ");
                writer.print("\"critical_path_size\": " + result.criticalPathSize);
                writer.print("}");
                if (i < results.size() - 1) writer.print(",");
                writer.println();
            }
            
            writer.println("  ]");
            writer.println("}");
        } catch (IOException e) {
            System.err.println("Error saving results: " + e.getMessage());
        }
    }
    
    // Helper methods copied from Main.java
    private static Map<String, task> loadTasks(String filename) throws IOException {
        Map<String, task> taskMap = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    String taskName = parts[0];
                    double size = Double.parseDouble(parts[1]);
                    
                    int taskId = Integer.parseInt(taskName.substring(1));
                    task t = new task(taskId);
                    t.setSize(size);
                    taskMap.put(taskName, t);
                }
            }
        }
        return taskMap;
    }
    
    private static void loadDependencies(String filename, Map<String, task> taskMap) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    String fromTaskName = parts[0];
                    String toTaskName = parts[1];
                    
                    task fromTask = taskMap.get(fromTaskName);
                    task toTask = taskMap.get(toTaskName);
                    
                    if (fromTask != null && toTask != null) {
                        int fromId = fromTask.getID();
                        int toId = toTask.getID();
                        
                        if (fromTask.getSucc() == null) {
                            fromTask.setSucc(new ArrayList<>());
                        }
                        fromTask.getSucc().add(toId);
                        
                        if (toTask.getPre() == null) {
                            toTask.setPre(new ArrayList<>());
                        }
                        toTask.getPre().add(fromId);
                    }
                }
            }
        }
    }
    
    private static Map<Integer, VM> loadVMs(String filename) throws IOException {
        Map<Integer, VM> vmMapping = new HashMap<>();
        
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean isFirstLine = true;
            
            while ((line = br.readLine()) != null) {
                // Skip comments
                if (line.startsWith("#")) continue;
                if (line.isBlank()) continue;
                
                // Parse with comma or spaces
                String[] parts = line.contains(",") ? line.trim().split(",") : line.trim().split("\\s+");
                
                if (isFirstLine) {
                    // First line is header: VM,vm0,vm1,vm2,vm3,vm4
                    isFirstLine = false;
                    continue;
                }
                
                // Data rows: vm0,1000,26.3,20.4,22.0,20.0
                String vmName = parts[0].trim();
                int vmId = Integer.parseInt(vmName.substring(2)); // vm0 -> 0, vm1 -> 1
                
                // Create VM
                VM vm = new VM(vmId);
                
                // Add bandwidth to other VMs
                for (int i = 1; i < parts.length; i++) {
                    double bandwidth = Double.parseDouble(parts[i].trim());
                    vm.setBandwidthToVM(i - 1, bandwidth);
                }
                
                vmMapping.put(vmId, vm);
            }
        }
        
        return vmMapping;
    }
    
    private static void loadProcessingCapacities(String filename, Map<Integer, VM> vmMapping) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                
                String[] parts = line.split("\\s+");
                if (parts.length >= 2) {
                    String vmName = parts[0];
                    double capacity = Double.parseDouble(parts[1]);
                    
                    // vm0 -> 0, vm1 -> 1, etc.
                    int vmId = Integer.parseInt(vmName.substring(2));
                    VM vm = vmMapping.get(vmId);
                    if (vm != null) {
                        vm.addCapability("processingCapacity", capacity);
                    }
                }
            }
        }
    }
    
    // Inner class to store CCR analysis results
    private static class CCRResult {
        double ccr;
        double makespan;
        double slr;
        int criticalPathSize;
        
        CCRResult(double ccr, double makespan, double slr, int criticalPathSize) {
            this.ccr = ccr;
            this.makespan = makespan;
            this.slr = slr;
            this.criticalPathSize = criticalPathSize;
        }
    }
}
