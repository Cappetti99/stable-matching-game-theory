import java.io.IOException;

public class SMGTTest {
    public static void main(String[] args) {
        try {
            SMGT smgt = new SMGT();
            
            // Load VM data from CSV
            smgt.loadVMsFromCSV("processing_capacity.csv");
            
            // Load task data and DAG structure
            smgt.loadTasksFromCSV("dag.csv", "task.csv");
            
            System.out.println("SMGT Threshold Calculation Demo");
            System.out.println("===============================");
            
            // Display VM processing capacities
            System.out.println("\nVM Processing Capacities:");
            for (int i = 0; i < smgt.getVMs().size(); i++) {
                VM vm = smgt.getVMs().get(i);
                double capacity = vm.getCapability("processing");
                System.out.println("VM " + i + ": " + capacity);
            }
            
            // Display task levels
            System.out.println("\nDAG Level Information:");
            for (int level : smgt.getLevelTasks().keySet()) {
                int taskCount = smgt.getLevelTasks().get(level).size();
                System.out.println("Level " + level + ": " + taskCount + " tasks");
            }
            
            // Generate and display preference matrices
            smgt.printPreferenceMatrices();
            
            // Run the complete SMGT algorithm
            smgt.runSMGTAlgorithm();
            
            // Calculate thresholds for different VMs and levels (for reference)
            System.out.println("\n=== THRESHOLD REFERENCE TABLE ===");
            System.out.println("Format: threshold(VM_k, level_l) = value");
            
            int maxLevel = smgt.getLevelTasks().keySet().stream().max(Integer::compare).orElse(0);
            
            for (int vm = 0; vm < smgt.getVMs().size(); vm++) {
                for (int level = 0; level <= maxLevel; level++) {
                    try {
                        int threshold = smgt.threshold(vm, level);
                        System.out.printf("threshold(%d, %d) = %d%n", vm, level, threshold);
                    } catch (Exception e) {
                        System.out.println("Error calculating threshold(" + vm + ", " + level + "): " + e.getMessage());
                    }
                }
                System.out.println(); // Empty line between VMs
            }
            
        } catch (IOException e) {
            System.err.println("Error loading data: " + e.getMessage());
        }
    }
}
