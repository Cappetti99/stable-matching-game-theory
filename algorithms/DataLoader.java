import java.io.*;
import java.util.*;

/**
 * Utility class for loading workflow data from CSV files
 * Handles loading of tasks, VMs, bandwidth, and DAG structure
 * 
 * IMPORTANT: CSV files are used ONLY for DAG structure (predecessors/successors)
 * All numeric values (task sizes, VM capacities, bandwidth) are generated with uniform distribution:
 * - Task sizes: [500, 700] MIPS
 * - VM capacities: [10, 20] MIPS  
 * - Bandwidth: [20, 30] Mbps
 * - Ranks: calculated by DCP algorithm, not loaded from CSV
 */
public class DataLoader {
    
    /**
     * Loads VM data from processing_capacity.csv
     * NOTE: Capacities are generated with uniform distribution [10, 20] MIPS
     * CSV is only used to determine number and IDs of VMs
     * @param filename Path to processing_capacity.csv
     * @return List of VM objects with random capacities
     */
    public static List<VM> loadVMsFromCSV(String filename) throws IOException {
        return loadVMsFromCSV(filename, -1);
    }

    /**
     * Loads VM data from processing_capacity.csv with run-specific seed
     * @param filename Path to processing_capacity.csv
     * @param runIdx Run index for seed variation (-1 for default behavior)
     * @return List of VM objects with random capacities
     */
    public static List<VM> loadVMsFromCSV(String filename, int runIdx) throws IOException {
        List<VM> vms = new ArrayList<>();
        Random rand = (runIdx >= 0) 
            ? SeededRandom.forScopeAndRun("DataLoader.loadVMsFromCSV:" + filename, runIdx)
            : SeededRandom.forScope("DataLoader.loadVMsFromCSV:" + filename);

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                // Skip header, comments and empty lines
                if (firstLine) {
                    firstLine = false;
                    if (line.contains("vm_id") || line.contains("processing"))
                        continue; // Skip CSV header
                }
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                // Support both CSV (comma) and whitespace separators
                String[] parts;
                if (line.contains(",")) {
                    parts = line.trim().split(",");
                } else {
                    parts = line.trim().split("\\s+");
                }

                if (parts.length >= 1) {
                    // Parse various formats: vm0, 0, vm_0 -> 0
                    String vmIdStr = parts[0].trim().toLowerCase().replace("vm_", "").replace("vm", "");
                    int vmId = Integer.parseInt(vmIdStr);
                    
                    // Generate random capacity: [10, 20] MIPS
                    double capacity = 10.0 + rand.nextDouble() * 10.0;

                    VM vm = new VM(vmId);
                    vm.setProcessingCapacity(capacity);
                    vms.add(vm);
                }
            }
        }
        return vms;
    }

    /**
     * Generates random bandwidth between all VM pairs
     * NOTE: Bandwidth is generated with uniform distribution [20, 30] Mbps
     * CSV file is ignored - bandwidth generated for all VM pairs
     * @param filename Path to bandwidth.csv (not used, kept for compatibility)
     * @param vms List of VMs to apply bandwidth to
     */
    public static void loadBandwidthFromCSV(String filename, List<VM> vms) throws IOException {
        loadBandwidthFromCSV(filename, vms, -1);
    }

    /**
     * Generates random bandwidth between all VM pairs with run-specific seed
     * @param filename Path to bandwidth.csv (not used, kept for compatibility)
     * @param vms List of VMs to apply bandwidth to
     * @param runIdx Run index for seed variation (-1 for default behavior)
     */
    public static void loadBandwidthFromCSV(String filename, List<VM> vms, int runIdx) throws IOException {
        Random rand = (runIdx >= 0)
            ? SeededRandom.forScopeAndRun("DataLoader.loadBandwidthFromCSV:" + filename, runIdx)
            : SeededRandom.forScope("DataLoader.loadBandwidthFromCSV:" + filename);
        
        // Generate random bandwidth for all VM pairs: [20, 30] Mbps
        for (VM vmI : vms) {
            for (VM vmJ : vms) {
                if (vmI.getID() != vmJ.getID()) {
                    double bandwidth = 20.0 + rand.nextDouble() * 10.0;
                    vmI.setBandwidthToVM(vmJ.getID(), bandwidth);
                }
            }
        }
    }

    /**
     * Loads task basic info from task.csv
     * NOTE: Task sizes are generated with uniform distribution [500, 700] MIPS
     * Ranks are NOT loaded (will be calculated by DCP algorithm)
     * CSV is only used to determine task IDs
     * @param filename Path to task.csv
     * @return List of task objects with random sizes
     */
    public static List<task> loadTaskBasicInfo(String filename) throws IOException {
        return loadTaskBasicInfo(filename, -1);
    }

    /**
     * Loads task basic info from task.csv with run-specific seed
     * @param filename Path to task.csv
     * @param runIdx Run index for seed variation (-1 for default behavior)
     * @return List of task objects with random sizes
     */
    public static List<task> loadTaskBasicInfo(String filename, int runIdx) throws IOException {
        List<task> tasks = new ArrayList<>();
        Random rand = (runIdx >= 0)
            ? SeededRandom.forScopeAndRun("DataLoader.loadTaskBasicInfo:" + filename, runIdx)
            : SeededRandom.forScope("DataLoader.loadTaskBasicInfo:" + filename);

        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                // Skip header, comments and empty lines
                if (firstLine) {
                    firstLine = false;
                    if (line.contains("id") || line.contains("size"))
                        continue; // Skip CSV header
                }
                if (line.startsWith("#") || line.trim().isEmpty()) {
                    continue;
                }

                // Support both CSV (comma) and whitespace separators
                String[] parts;
                if (line.contains(",")) {
                    parts = line.trim().split(",");
                } else {
                    parts = line.trim().split("\\s+");
                }

                if (parts.length >= 1) {
                    int taskId = Integer.parseInt(parts[0].replace("t", "").trim());
                    
                    // Generate random task size: [500, 700] MIPS
                    double size = 500.0 + rand.nextDouble() * 200.0;
                    
                    // Rank will be calculated by DCP, not loaded from CSV
                    double rank = 0.0;

                    task t = new task(taskId);
                    t.setSize(size);
                    t.setRank(rank);
                    tasks.add(t);
                }
            }
        }
        return tasks;
    }

    /**
     * Loads DAG structure from dag.csv and builds predecessor/successor relationships
     * @param filename Path to dag.csv
     * @param tasks List of tasks to build relationships for (will be modified)
     */
    public static void loadDAGStructure(String filename, List<task> tasks) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(filename))) {
            String line;
            boolean firstLine = true;

            while ((line = reader.readLine()) != null) {
                if (firstLine) {
                    firstLine = false;
                    continue; // Skip header
                }

                // Support both CSV (comma) and whitespace separators
                String[] parts;
                if (line.contains(",")) {
                    parts = line.trim().split(",");
                } else {
                    parts = line.trim().split("\\s+");
                }

                if (parts.length >= 2) {
                    int predId = Integer.parseInt(parts[0].replace("t", "").trim());
                    int succId = Integer.parseInt(parts[1].replace("t", "").trim());

                    // BUG FIX: Add null checks to prevent NullPointerException
                    task predTask = getTaskById(predId, tasks);
                    task succTask = getTaskById(succId, tasks);
                    
                    if (predTask != null && succTask != null) {
                        // Add relationships
                        predTask.getSucc().add(succId);
                        succTask.getPre().add(predId);
                    } else {
                        // Log warning if task not found (possible data inconsistency)
                        if (predTask == null) {
                            System.err.println("Warning: DAG references non-existent predecessor task ID: " + predId);
                        }
                        if (succTask == null) {
                            System.err.println("Warning: DAG references non-existent successor task ID: " + succId);
                        }
                    }
                }
            }
        }
    }

    /**
     * Finds a task by ID in the list
     */
    private static task getTaskById(int taskId, List<task> tasks) {
        return tasks.stream()
                .filter(t -> t.getID() == taskId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Complete data loading: tasks + DAG structure
     * @param dagFilename Path to dag.csv
     * @param taskFilename Path to task.csv
     * @return List of tasks with complete structure
     */
    public static List<task> loadTasksFromCSV(String dagFilename, String taskFilename) throws IOException {
        return loadTasksFromCSV(dagFilename, taskFilename, -1);
    }

    /**
     * Complete data loading: tasks + DAG structure with run-specific seed
     * @param dagFilename Path to dag.csv
     * @param taskFilename Path to task.csv
     * @param runIdx Run index for seed variation (-1 for default behavior)
     * @return List of tasks with complete structure
     */
    public static List<task> loadTasksFromCSV(String dagFilename, String taskFilename, int runIdx) throws IOException {
        List<task> tasks = new ArrayList<>();
        
        // First load task basic info if task.csv exists
        File taskFile = new File(taskFilename);
        if (taskFile.exists()) {
            tasks = loadTaskBasicInfo(taskFilename, runIdx);
        }

        // Load DAG structure (structure doesn't change, no runIdx needed)
        loadDAGStructure(dagFilename, tasks);

        return tasks;
    }
}
