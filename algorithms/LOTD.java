import java.util.*;

/**
 * LOTD: List of Task Duplication
 * 
 * Simplified implementation focused on duplicating ONLY entry tasks (level 0)
 * onto VMs hosting their children, when it reduces communication overhead.
 * 
 * Algorithm:
 * 1. Identify entry tasks (tasks with no predecessors)
 * 2. For each entry task:
 * - Find VMs hosting its successor tasks
 * - Check if VM has idle time before successor starts
 * - Duplicate if: duplicate_finish < original_finish + comm_cost
 * 3. Recalculate schedule with duplicates
 * 4. Return optimized schedule
 * 
 * @author Lorenzo Cappetti
 * @version 4.0 - Simplified Entry Task Duplication
 */
public class LOTD {

    private SMGT smgt;
    private List<VM> vms;
    private List<task> tasks;

    // Communication costs model
    private Map<String, Double> communicationCosts;

    // Current schedule state
    private Map<Integer, Double> taskAST; // Actual Start Time
    private Map<Integer, Double> taskAFT; // Actual Finish Time
    private Map<Integer, List<Integer>> vmSchedule; // VM -> tasks (original schedule)
    private Map<Integer, Integer> taskToVM; // task -> VM

    // Duplication tracking
    private Map<Integer, Set<Integer>> duplicatedTasks; // VM -> Set of duplicated task IDs

    private static final boolean VERBOSE = false;

    /**
     * Constructor
     */
    public LOTD(SMGT smgt) {
        this.smgt = smgt;
        this.vms = smgt.getVMs();
        this.tasks = smgt.getTasks();
        this.communicationCosts = new HashMap<>();
        this.taskAST = new HashMap<>();
        this.taskAFT = new HashMap<>();
        this.vmSchedule = new HashMap<>();
        this.taskToVM = new HashMap<>();
        this.duplicatedTasks = new HashMap<>();

        for (VM vm : vms) {
            duplicatedTasks.put(vm.getID(), new HashSet<>());
        }
    }

    /**
     * Sets communication costs (from ExperimentRunner)
     */
    public void setCommunicationCosts(Map<String, Double> communicationCosts) {
        this.communicationCosts = (communicationCosts != null)
                ? communicationCosts
                : new HashMap<>();
    }

    /**
     * MAIN ENTRY POINT: Execute LOTD algorithm
     * 
     * @param preSchedule Schedule from SMGT (VM_ID -> task_IDs)
     * @return Optimized schedule with entry task duplications
     */
    public Map<Integer, List<Integer>> executeLOTDCorrect(Map<Integer, List<Integer>> preSchedule) {

        if (VERBOSE) {
            System.out.println("\n=== LOTD: Entry Task Duplication ===");
        }

        // Step 1: Initialize state from SMGT schedule
        initializeFromSchedule(preSchedule);

        // Step 2: Calculate initial timing (AST/AFT)
        calculateInitialTiming();

        // Step 3: Find entry tasks (level 0, no predecessors)
        List<Integer> entryTasks = findEntryTasks();

        if (VERBOSE) {
            System.out.println("Entry tasks found: " + entryTasks);
        }

        if (entryTasks.isEmpty()) {
            System.out.println("   No entry tasks to duplicate, returning original schedule");
            return vmSchedule;
        }

        // Step 4: Try to duplicate each entry task
        for (Integer entryTaskId : entryTasks) {
            tryDuplicateEntryTask(entryTaskId);
        }

        // Step 5: Final timing recalculation
        calculateFinalTiming();

        if (VERBOSE) {
            printFinalState();
        }

        return vmSchedule;
    }

    /**
     * Step 1: Initialize internal state from SMGT schedule
     */
    private void initializeFromSchedule(Map<Integer, List<Integer>> preSchedule) {
        vmSchedule.clear();
        taskToVM.clear();

        // Deep copy schedule
        for (Map.Entry<Integer, List<Integer>> entry : preSchedule.entrySet()) {
            int vmId = entry.getKey();
            vmSchedule.put(vmId, new ArrayList<>(entry.getValue()));

            // Build task->VM mapping
            for (Integer taskId : entry.getValue()) {
                taskToVM.put(taskId, vmId);
            }
        }
    }

    /**
     * Step 2: Calculate initial timing using topological order
     */
    private void calculateInitialTiming() {
        taskAST.clear();
        taskAFT.clear();

        List<task> sorted = topologicalSort();

        for (task t : sorted) {
            calculateTaskTiming(t.getID());
        }

        if (VERBOSE) {
            System.out.println("\nInitial timing:");
            for (task t : sorted) {
                System.out.printf("  t%d: AST=%.2f, AFT=%.2f\n",
                        t.getID(), taskAST.get(t.getID()), taskAFT.get(t.getID()));
            }
        }
    }

    /**
     * Calculate timing for a single task
     */
    private void calculateTaskTiming(int taskId) {
        task t = getTaskById(taskId);
        Integer vmId = taskToVM.get(taskId);

        if (t == null || vmId == null) {
            taskAST.put(taskId, 0.0);
            taskAFT.put(taskId, 0.0);
            return;
        }

        // 1. Data Ready Time (when all predecessors finish + communication)
        double drt = 0.0;
        if (t.getPre() != null) {
            for (Integer predId : t.getPre()) {
                Double predAFT = taskAFT.get(predId);
                if (predAFT == null)
                    continue;

                // Check if duplicate exists on same VM
                if (duplicatedTasks.get(vmId).contains(predId)) {
                    // Local duplicate, no communication cost
                    drt = Math.max(drt, predAFT);
                } else {
                    // Remote predecessor
                    Integer predVM = taskToVM.get(predId);
                    double commCost = (predVM != null && predVM != vmId)
                            ? getCommunicationCost(predId, taskId, predVM, vmId)
                            : 0.0;
                    drt = Math.max(drt, predAFT + commCost);
                }
            }
        }

        // 2. Machine Ready Time (when VM finishes previous task)
        double mrt = getVMReadyTime(vmId, taskId);

        // 3. Actual Start Time = max(DRT, MRT)
        double ast = Math.max(drt, mrt);

        // 4. Execution time
        double et = calculateExecutionTime(t, vmId);

        // 5. Actual Finish Time
        double aft = ast + et;

        taskAST.put(taskId, ast);
        taskAFT.put(taskId, aft);
    }

    /**
     * Step 3: Find all entry tasks (no predecessors)
     */
    private List<Integer> findEntryTasks() {
        List<Integer> entryTasks = new ArrayList<>();

        for (task t : tasks) {
            if (t.getPre() == null || t.getPre().isEmpty()) {
                entryTasks.add(t.getID());
            }
        }

        return entryTasks;
    }

    /**
     * Step 4: Try to duplicate an entry task onto VMs with its children
     */
    private void tryDuplicateEntryTask(int entryTaskId) {
        task entryTask = getTaskById(entryTaskId);
        if (entryTask == null || entryTask.getSucc() == null)
            return;

        Integer originalVM = taskToVM.get(entryTaskId);
        if (originalVM == null)
            return;

        if (VERBOSE) {
            System.out.println("\n--- Analyzing entry task t" + entryTaskId + " ---");
            System.out.println("Currently on VM" + originalVM);
            System.out.println("Successors: " + entryTask.getSucc());
        }

        // For each successor, check if duplication helps
        for (Integer succId : entryTask.getSucc()) {
            Integer succVM = taskToVM.get(succId);

            // Skip if successor on same VM or successor not assigned
            if (succVM == null || succVM.equals(originalVM))
                continue;

            // Skip if already duplicated on this VM
            if (duplicatedTasks.get(succVM).contains(entryTaskId))
                continue;

            if (VERBOSE) {
                System.out.println("\nChecking duplication on VM" + succVM +
                        " (for successor t" + succId + ")");
            }

            // Check if duplication is beneficial
            if (shouldDuplicate(entryTaskId, succVM, succId)) {
                performDuplication(entryTaskId, succVM);

                if (VERBOSE) {
                    System.out.println("✓ Duplicated t" + entryTaskId + " on VM" + succVM);
                }
            } else {
                if (VERBOSE) {
                    System.out.println("✗ Duplication not beneficial");
                }
            }
        }
    }

    /**
     * Check if duplicating entry task on target VM is beneficial
     * 
     * Conditions:
     * 1. VM must be idle before successor starts
     * 2. Duplicate must finish before data arrives from original
     */
    private boolean shouldDuplicate(int entryTaskId, int targetVM, int succId) {
        task entryTask = getTaskById(entryTaskId);
        Integer originalVM = taskToVM.get(entryTaskId);

        if (entryTask == null || originalVM == null)
            return false;

        // 1. Calculate when duplicate would finish
        double duplicateET = calculateExecutionTime(entryTask, targetVM);

        // Entry tasks have no predecessors, so they start at time 0
        // But VM might be busy with other tasks
        double vmReadyTime = getVMReadyTime(targetVM, -1); // -1 = new task (not yet in schedule)
        double duplicateFinish = vmReadyTime + duplicateET;

        // 2. Calculate when data would arrive from original VM
        Double originalFinish = taskAFT.get(entryTaskId);
        if (originalFinish == null)
            originalFinish = 0.0;

        double commCost = getCommunicationCost(entryTaskId, succId, originalVM, targetVM);
        double dataArrival = originalFinish + commCost;

        // 3. Get when successor starts on target VM
        Double succAST = taskAST.get(succId);
        if (succAST == null)
            succAST = Double.MAX_VALUE;

        if (VERBOSE) {
            System.out.printf("  VM ready time: %.2f\n", vmReadyTime);
            System.out.printf("  Duplicate finish: %.2f\n", duplicateFinish);
            System.out.printf("  Data arrival: %.2f (original=%.2f + comm=%.2f)\n",
                    dataArrival, originalFinish, commCost);
            System.out.printf("  Successor starts: %.2f\n", succAST);
        }

        // Check conditions:
        // A) Duplicate finishes before successor starts (idle slot available)
        boolean hasIdleSlot = duplicateFinish <= succAST;

        // B) Duplicate finishes before data arrives (performance gain)
        boolean isBeneficial = duplicateFinish < dataArrival;

        return hasIdleSlot && isBeneficial;
    }

    /**
     * Perform the actual duplication
     */
    private void performDuplication(int taskId, int targetVM) {
        // Mark as duplicated (this affects future timing calculations)
        duplicatedTasks.get(targetVM).add(taskId);

        // Note: We DON'T add to vmSchedule because duplicates are "phantom" tasks
        // They execute opportunistically in idle time but don't change the main
        // schedule

        // Recalculate timing for affected successors
        task t = getTaskById(taskId);
        if (t != null && t.getSucc() != null) {
            for (Integer succId : t.getSucc()) {
                Integer succVM = taskToVM.get(succId);
                if (succVM != null && succVM == targetVM) {
                    // This successor is on the same VM as duplicate
                    // Recalculate its timing (will now see local duplicate)
                    propagateTimingUpdate(succId);
                }
            }
        }
    }

    /**
     * Propagate timing update through successors
     */
    private void propagateTimingUpdate(int startTaskId) {
        Set<Integer> visited = new HashSet<>();
        Queue<Integer> queue = new LinkedList<>();
        queue.offer(startTaskId);

        while (!queue.isEmpty()) {
            int taskId = queue.poll();
            if (!visited.add(taskId))
                continue;

            // Recalculate this task
            double oldAFT = taskAFT.getOrDefault(taskId, 0.0);
            calculateTaskTiming(taskId);
            double newAFT = taskAFT.getOrDefault(taskId, 0.0);

            // If timing changed significantly, propagate to successors
            if (Math.abs(newAFT - oldAFT) > 1e-6) {
                task t = getTaskById(taskId);
                if (t != null && t.getSucc() != null) {
                    for (Integer succId : t.getSucc()) {
                        queue.offer(succId);
                    }
                }
            }
        }
    }

    /**
     * Step 5: Final timing calculation
     */
    private void calculateFinalTiming() {
        // Full recalculation with duplicates in place
        List<task> sorted = topologicalSort();

        for (task t : sorted) {
            calculateTaskTiming(t.getID());
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Get communication cost between two tasks on different VMs
     */
    private double getCommunicationCost(int srcTaskId, int dstTaskId,
            int srcVM, int dstVM) {
        if (srcVM == dstVM)
            return 0.0;

        // Get bandwidth
        VM src = getVMById(srcVM);
        if (src == null)
            return 0.0;

        double bandwidth = src.getBandwidthToVM(dstVM);
        if (bandwidth <= 0)
            bandwidth = 25.0; // Default

        // Get communication cost from map (stored at avgBandwidth=25)
        String key = srcTaskId + "_" + dstTaskId;
        double commTimeAtAvg = communicationCosts.getOrDefault(key, 0.0);

        // Scale by actual bandwidth
        return commTimeAtAvg * 25.0 / bandwidth;
    }

    /**
     * Calculate execution time of task on VM
     */
    private double calculateExecutionTime(task t, int vmId) {
        VM vm = getVMById(vmId);
        if (vm == null)
            return t.getSize();

        double capacity = vm.getCapability("processingCapacity");
        if (capacity <= 0)
            capacity = vm.getCapability("processing");
        if (capacity <= 0)
            return t.getSize();

        return t.getSize() / capacity;
    }

    /**
     * Get when VM is ready for next task
     * 
     * @param vmId          VM to check
     * @param excludeTaskId Task to exclude from calculation (-1 for new task)
     */
    private double getVMReadyTime(int vmId, int excludeTaskId) {
        List<Integer> tasksOnVM = vmSchedule.get(vmId);
        if (tasksOnVM == null || tasksOnVM.isEmpty())
            return 0.0;

        double maxFinish = 0.0;

        for (Integer taskId : tasksOnVM) {
            if (taskId == excludeTaskId)
                continue;

            Double aft = taskAFT.get(taskId);
            if (aft != null) {
                maxFinish = Math.max(maxFinish, aft);
            }
        }

        return maxFinish;
    }

    /**
     * Topological sort of tasks
     */
    private List<task> topologicalSort() {
        List<task> result = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Set<Integer> visiting = new HashSet<>();

        for (task t : tasks) {
            if (!visited.contains(t.getID())) {
                topologicalVisit(t.getID(), visited, visiting, result);
            }
        }

        Collections.reverse(result);
        return result;
    }

    private void topologicalVisit(int taskId, Set<Integer> visited,
            Set<Integer> visiting, List<task> result) {
        if (visited.contains(taskId))
            return;

        visiting.add(taskId);
        task t = getTaskById(taskId);

        if (t != null && t.getSucc() != null) {
            for (Integer succId : t.getSucc()) {
                if (!visited.contains(succId)) {
                    topologicalVisit(succId, visited, visiting, result);
                }
            }
        }

        visited.add(taskId);
        visiting.remove(taskId);
        if (t != null) {
            result.add(t);
        }
    }

    /**
     * Helper: get task by ID
     */
    private task getTaskById(int taskId) {
        return smgt.getTaskById(taskId);
    }

    /**
     * Helper: get VM by ID
     */
    private VM getVMById(int vmId) {
        for (VM vm : vms) {
            if (vm.getID() == vmId)
                return vm;
        }
        return null;
    }

    /**
     * Print final state for debugging
     */
    private void printFinalState() {
        System.out.println("\n=== LOTD Final State ===");

        System.out.println("\nOriginal Schedule:");
        for (Map.Entry<Integer, List<Integer>> entry : vmSchedule.entrySet()) {
            System.out.println("VM" + entry.getKey() + ": " + entry.getValue());
        }

        System.out.println("\nDuplicated Tasks:");
        for (Map.Entry<Integer, Set<Integer>> entry : duplicatedTasks.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                System.out.println("VM" + entry.getKey() + ": " + entry.getValue());
            }
        }

        System.out.println("\nFinal Makespan: " + calculateMakespan());
    }

    /**
     * Calculate makespan
     */
    private double calculateMakespan() {
        return taskAFT.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0.0);
    }

    // ==================== GETTERS ====================

    public Map<Integer, Set<Integer>> getDuplicatedTasks() {
        return duplicatedTasks;
    }

    public Map<Integer, Double> getTaskAST() {
        return taskAST;
    }

    public Map<Integer, Double> getTaskAFT() {
        return taskAFT;
    }
}