import java.util.*;
import java.io.*;

/**
 * SMGT - Stable Matching Game Theory Algorithm.
 *
 * This class implements the core logic for assigning tasks to virtual machines
 * using a game-theoretic stable matching approach, while respecting
 * DAG dependencies between tasks.
 */
public class SMGT {

    /** List of available virtual machines */
    private List<VM> vms;

    /** List of tasks composing the workflow DAG */
    private List<task> tasks;

    /** Mapping: task ID -> topological level in the DAG */
    private Map<Integer, Integer> taskLevels;

    /** Mapping: topological level -> list of task IDs */
    private Map<Integer, List<Integer>> levelTasks;

    // ==================== CONFIGURATION ====================

    /**
     * Enables verbose logging for debugging and analysis purposes.
     * Should be disabled in production runs.
     */
    private static final boolean VERBOSE = false;

    public SMGT() {
        this.vms = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.taskLevels = new HashMap<>();
        this.levelTasks = new HashMap<>();
    }

    // ==================== SETTERS AND GETTERS ====================

    public void setTasks(List<task> tasks) {
        this.tasks = tasks;
    }

    public void setVMs(List<VM> vms) {
        this.vms = vms;
    }

    public List<VM> getVMs() {
        return vms;
    }

    public List<task> getTasks() {
        return tasks;
    }

    /**
     * Retrieves a task by its unique identifier.
     *
     * @param taskId the task ID
     * @return the corresponding task, or null if not found
     */
    public task getTaskById(int taskId) {
        return tasks.stream()
                .filter(t -> t.getID() == taskId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Computes the topological level of each task in the workflow DAG.
     *
     * Tasks at level 0 have no predecessors (entry tasks).
     * Each subsequent level contains tasks whose predecessors
     * belong to strictly lower levels.
     *
     * The method relies on a BFS-based topological traversal implemented
     * in the Utility class.
     */
    public void calculateTaskLevels() {
        // Clear previous results before recomputation
        taskLevels.clear();

        // Organize tasks by topological levels
        levelTasks = Utility.organizeTasksByLevels(tasks);

        /*
         * Build the inverse mapping (taskId -> level).
         * This representation is more convenient for scheduling
         * and matching decisions in later stages of SMGT.
         */
        for (Map.Entry<Integer, List<Integer>> entry : levelTasks.entrySet()) {
            Integer level = entry.getKey();
            for (Integer taskId : entry.getValue()) {
                taskLevels.put(taskId, level);
            }
        }

        if (VERBOSE) {
            System.out.println("📊 Task levels successfully computed:");
            levelTasks.forEach(
                (level, taskIds) ->
                    System.out.println(
                        "   Level " + level + ": " + taskIds.size() + " tasks"
                    )
            );
        }
    }

// ==================== MAIN ALGORITHM ====================

/**
 * Main SMGT algorithm with Critical Path prioritization.
 *
 * The algorithm processes tasks level by level:
 * 1. Assigns Critical Path (CP) tasks to the fastest VM.
 * 2. Computes thresholds for all VMs at this level.
 * 3. Uses stable matching to assign non-CP tasks based on preferences.
 *
 * @param criticalPath Set of task IDs belonging to the Critical Path
 * @return Map VM_ID -> list of assigned task IDs
 */
public Map<Integer, List<Integer>> runSMGT(Set<Integer> criticalPath) {
    if (VERBOSE) {
        System.out.println("\n" + "=".repeat(70));
        System.out.println("SMGT ALGORITHM START");
        System.out.println("=".repeat(70));
        System.out.println("Critical Path: " + criticalPath);
        System.out.println("Total tasks: " + tasks.size());
        System.out.println("Total VMs: " + vms.size());
    }

    // Initialize empty schedule
    Map<Integer, List<Integer>> schedule = new HashMap<>();
    for (int i = 0; i < vms.size(); i++) {
        schedule.put(i, new ArrayList<>());
    }

    // Sort levels in topological order
    List<Integer> levels = new ArrayList<>(levelTasks.keySet());
    Collections.sort(levels);

    if (VERBOSE) {
        System.out.println("DAG Levels: " + levels);
    }

    // Process each level sequentially
    for (Integer level : levels) {
        processLevel(level, criticalPath, schedule);
    }

    // Final verification
    int totalAssigned = schedule.values().stream()
            .mapToInt(List::size)
            .sum();

    if (VERBOSE || totalAssigned != tasks.size()) {
        System.out.println("\n📊 SMGT Summary:");
        System.out.println("   Tasks assigned: " + totalAssigned + "/" + tasks.size());
        if (totalAssigned != tasks.size()) {
            System.err.println("   ⚠️  WARNING: Not all tasks assigned!");
        }
    }

    return schedule;
}

/**
 * Processes a single DAG level by assigning tasks to VMs.
 *
 * Critical Path tasks are prioritized and assigned to the fastest VM,
 * while non-CP tasks are assigned using a stable matching based on
 * preference lists.
 *
 * @param level        DAG level to process
 * @param criticalPath Set of task IDs in the Critical Path
 * @param schedule     Current schedule map (modified in place)
 */
private void processLevel(int level, Set<Integer> criticalPath,
        Map<Integer, List<Integer>> schedule) {

    if (VERBOSE) {
        System.out.println("\n" + "-".repeat(70));
        System.out.println("📍 Processing Level " + level);
    }

    List<Integer> levelTaskIds = levelTasks.getOrDefault(level, new ArrayList<>());

    if (levelTaskIds.isEmpty()) {
        if (VERBOSE) System.out.println("   (empty level, skipping)");
        return;
    }

    // Separate Critical Path tasks from non-CP tasks
    List<Integer> cpTasks = new ArrayList<>();
    List<Integer> nonCpTasks = new ArrayList<>();
    for (Integer taskId : levelTaskIds) {
        if (criticalPath.contains(taskId)) {
            cpTasks.add(taskId);
        } else {
            nonCpTasks.add(taskId);
        }
    }

    if (VERBOSE) {
        System.out.println("   Total tasks: " + levelTaskIds.size());
        System.out.println("   CP tasks: " + cpTasks.size());
        System.out.println("   Non-CP tasks: " + nonCpTasks.size());
    }

    // STEP 1: Compute thresholds for each VM (max tasks allowed at this level)
    calculateAndSetThresholds(level, levelTaskIds.size());

    // STEP 2: Assign CP tasks to the fastest VM and update waiting list
    int fastestVM = getFastestVM();
    for (Integer cpTaskId : cpTasks) {
        schedule.get(fastestVM).add(cpTaskId);
        vms.get(fastestVM).addToWaitingList(cpTaskId);

        if (VERBOSE) {
            VM vm = vms.get(fastestVM);
            System.out.println("   ✓ CP task t" + cpTaskId + " → VM" + fastestVM +
                " (waitingList: " + vm.getWaitingListSize() + "/" + vm.getThreshold() + ")");
        }
    }

    // STEP 3: If no non-CP tasks remain, level is complete
    if (nonCpTasks.isEmpty()) {
        if (VERBOSE) System.out.println("   (no non-CP tasks, level complete)");
        return;
    }

    // STEP 4: Generate preference lists for non-CP tasks and VMs
    Map<Integer, List<Integer>> taskPreferences = new HashMap<>();
    for (Integer taskId : nonCpTasks) {
        taskPreferences.put(taskId, generateTaskPreferences(taskId));
    }
    Map<Integer, List<Integer>> vmPreferences = generateAllVMPreferences();

    // STEP 5: Apply stable matching algorithm to assign non-CP tasks
    stableMatchingForLevel(nonCpTasks, taskPreferences, vmPreferences, schedule);

    if (VERBOSE) {
        System.out.println("   Level " + level + " completed:");
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            System.out.println("      VM" + vmIdx + ": " + vm.getWaitingListSize() + "/" + vm.getThreshold() + " slots used");
        }
    }
}
// ==================== THRESHOLD CALCULATION ====================

/**
 * Computes and sets the task threshold for each VM at a given DAG level.
 *
 * Formula:
 * threshold(VM_k, level) = ceil((Σ_{v=0}^{level} n_v / Σp_i) × p_k)
 *
 * The threshold determines the maximum number of tasks a VM can handle
 * at this level. It accumulates over levels. A VM is considered full when
 * waitingList.size() >= threshold.
 *
 * @param level               DAG level
 * @param tasksInCurrentLevel Number of tasks in the current level (unused, included for API consistency)
 */
private void calculateAndSetThresholds(int level, int tasksInCurrentLevel) {
    // Sum tasks from level 0 to current
    double sumTasksUpToLevel = 0.0;
    for (int l = 0; l <= level; l++) {
        sumTasksUpToLevel += levelTasks.getOrDefault(l, new ArrayList<>()).size();
    }

    // Sum VM capacities
    double sumCapacities = 0.0;
    for (VM vm : vms) {
        sumCapacities += getVMProcessingCapacity(vm);
    }

    // Fallback to uniform distribution if capacities are zero
    if (sumCapacities == 0) {
        System.err.println("WARNING: All VM capacities are zero, not possible to compute thresholds accurately.");
    }

    // Compute thresholds for each VM
    int totalThreshold = 0;
    for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
        VM vm = vms.get(vmIdx);
        double capacity = getVMProcessingCapacity(vm);
        int threshold = (int) Math.ceil((sumTasksUpToLevel / sumCapacities) * capacity);
        vm.setThreshold(threshold);
        //  totalThreshold += threshold;        PER ORA COMMENTATO PER CAPIRE SE SERVE O NO

        if (VERBOSE) {
            System.out.println("   VM" + vmIdx + " threshold: " + threshold +
                    " (waitingList: " + vm.getWaitingListSize() + ")");
        }
    }

    /*
    // Distribute deficit if total thresholds are insufficient
    int deficit = (int) sumTasksUpToLevel - totalThreshold;
    if (deficit > 0) {
        if (VERBOSE) System.out.println("   Threshold deficit: " + deficit + " tasks");
        List<Map.Entry<Integer, Double>> vmCapacityFractions = new ArrayList<>();
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            double fraction = getVMProcessingCapacity(vms.get(vmIdx)) / sumCapacities;
            vmCapacityFractions.add(new AbstractMap.SimpleEntry<>(vmIdx, fraction));
        }

        vmCapacityFractions.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));

        int remaining = deficit;
        int roundRobinIdx = 0;
        while (remaining > 0) {
            int vmIdx = vmCapacityFractions.get(roundRobinIdx % vmCapacityFractions.size()).getKey();
            VM vm = vms.get(vmIdx);
            vm.setThreshold(vm.getThreshold() + 1);
            remaining--;
            roundRobinIdx++;

            if (VERBOSE) System.out.println("      → +1 to VM" + vmIdx);
        }
    }
    */
}

// ==================== STABLE MATCHING ====================

/**
 * Stable matching algorithm for non-Critical Path tasks at a given level.
 * VMs are full when waitingList.size() >= threshold.
 *
 * @param unassignedTasks List of tasks to assign
 * @param taskPreferences Map taskID -> ordered list of preferred VM indices
 * @param vmPreferences   Map vmID -> ordered list of preferred task IDs
 * @param schedule        Current schedule (modified in place)
 */
private void stableMatchingForLevel(
        List<Integer> unassignedTasks,
        Map<Integer, List<Integer>> taskPreferences,
        Map<Integer, List<Integer>> vmPreferences,
        Map<Integer, List<Integer>> schedule) {

    Set<Integer> remaining = new HashSet<>(unassignedTasks);

    while (!remaining.isEmpty()) {
        Integer taskId = remaining.iterator().next();
        remaining.remove(taskId);

        List<Integer> prefs = taskPreferences.get(taskId);

        // If no preferences left, assign to best available VM
        if (prefs == null || prefs.isEmpty()) {
            assignTask(taskId, findBestAvailableVM(taskId), schedule);
            continue;
        }

        Integer vmIdx = prefs.get(0);
        VM vm = vms.get(vmIdx);

        // Assign directly if VM has space
        if (!vm.isFull()) {
            assignTask(taskId, vmIdx, schedule);
            continue;
        }

        // VM full: find worst non-CP task to possibly replace
        Integer worstTask = findWorstNonCPTask(vm.getWaitingList(), vmIdx, vmPreferences, taskPreferences);

        if (worstTask == null) {
            prefs.remove(0);
            remaining.add(taskId);
            continue;
        }

        // Compare finish times
        double ftTask = calculateFinishTime(taskId, vmIdx);
        double ftWorst = calculateFinishTime(worstTask, vmIdx);

        if (ftTask < ftWorst) {
            unassignTask(worstTask, vmIdx, schedule);
            assignTask(taskId, vmIdx, schedule);

            List<Integer> worstPrefs = taskPreferences.get(worstTask);
            if (worstPrefs != null) worstPrefs.remove((Integer) vmIdx);
            remaining.add(worstTask);

            if (VERBOSE) System.out.println("   ↔ Replaced t" + worstTask + " with t" + taskId + " on VM" + vmIdx);
        } else {
            prefs.remove(0);
            remaining.add(taskId);
        }
    }
}

// ==================== UTILITY METHODS ====================

/**
 * Assign a task to a VM (updates schedule and VM waiting list)
 */
private void assignTask(int taskId, int vmIdx, Map<Integer, List<Integer>> schedule) {
    schedule.get(vmIdx).add(taskId);
    VM vm = vms.get(vmIdx);
    vm.addToWaitingList(taskId);

    if (VERBOSE) {
        System.out.println("   ✓ Assigned t" + taskId + " → VM" + vmIdx +
                " (waitingList: " + vm.getWaitingListSize() + "/" + vm.getThreshold() + ")");
    }
}

/**
 * Unassign a task from a VM (removes from schedule and waiting list)
 */
private void unassignTask(int taskId, int vmIdx, Map<Integer, List<Integer>> schedule) {
    schedule.get(vmIdx).remove((Integer) taskId);
    vms.get(vmIdx).getWaitingList().remove((Integer) taskId);
}

/**
 * Finds the best available VM for a task (non-full, minimal finish time)
 */
private int findBestAvailableVM(int taskId) {
    int bestVM = -1;
    double bestFT = Double.MAX_VALUE;

    for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
        VM vm = vms.get(vmIdx);
        if (!vm.isFull()) {
            double ft = calculateFinishTime(taskId, vmIdx);
            if (ft < bestFT) {
                bestFT = ft;
                bestVM = vmIdx;
            }
        }
    }

    if (bestVM == -1) bestVM = getFastestVM(); // fallback

    return bestVM;
}

/**
 * Finds the worst finish-time task in a VM's waiting list (non-CP tasks only)
 */
private Integer findWorstNonCPTask(List<Integer> taskList, int vmIdx,
        Map<Integer, List<Integer>> vmPreferences,
        Map<Integer, List<Integer>> taskPreferences) {

    if (taskList.isEmpty()) return null;

    Integer worst = null;
    double maxFT = -1;

    for (Integer taskId : taskList) {
        if (!taskPreferences.containsKey(taskId)) continue; // skip CP tasks
        double ft = calculateFinishTime(taskId, vmIdx);
        if (ft > maxFT) {
            maxFT = ft;
            worst = taskId;
        }
    }

    return worst;
}

/**
 * Calculates finish time of a task on a VM using Metrics.ET
 */
private double calculateFinishTime(int taskId, int vmIdx) {
    task t = getTaskById(taskId);
    if (t == null || vmIdx >= vms.size()) return Double.MAX_VALUE;
    return Metrics.ET(t, vms.get(vmIdx), "processingCapacity");
}

/**
 * Returns processing capacity of a VM, defaulting to 1.0 if undefined
 */
private double getVMProcessingCapacity(VM vm) {
    double cap = vm.getProcessingCapacity();
    return cap > 0 ? cap : 1.0;
}

/**
 * Returns index of the fastest VM based on processing capacity
 */
private int getFastestVM() {
    int bestVM = 0;
    double maxCapacity = -1;
    for (int i = 0; i < vms.size(); i++) {
        double cap = getVMProcessingCapacity(vms.get(i));
        if (cap > maxCapacity) {
            maxCapacity = cap;
            bestVM = i;
        }
    }
    return bestVM;
}

// ==================== PREFERENCE GENERATION ====================

/**
 * Generates VM preference list for a task (sorted by finish time ascending)
 */
public List<Integer> generateTaskPreferences(int taskId) {
    List<Map.Entry<Integer, Double>> vmFinishTimes = new ArrayList<>();
    for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
        vmFinishTimes.add(new AbstractMap.SimpleEntry<>(vmIdx, calculateFinishTime(taskId, vmIdx)));
    }
    vmFinishTimes.sort(Map.Entry.comparingByValue());

    List<Integer> preferences = new ArrayList<>();
    for (Map.Entry<Integer, Double> entry : vmFinishTimes) preferences.add(entry.getKey());
    return preferences;
}

/**
 * Generates task preference list for a VM (sorted by finish time ascending)
 */
public List<Integer> generateVMPreferences(int vmIdx) {
    List<Map.Entry<Integer, Double>> taskFinishTimes = new ArrayList<>();
    for (task t : tasks) {
        taskFinishTimes.add(new AbstractMap.SimpleEntry<>(t.getID(), calculateFinishTime(t.getID(), vmIdx)));
    }
    taskFinishTimes.sort(Map.Entry.comparingByValue());

    List<Integer> preferences = new ArrayList<>();
    for (Map.Entry<Integer, Double> entry : taskFinishTimes) preferences.add(entry.getKey());
    return preferences;
}

/**
 * Generates preference lists for all VMs
 */
public Map<Integer, List<Integer>> generateAllVMPreferences() {
    Map<Integer, List<Integer>> allPreferences = new HashMap<>();
    for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
        allPreferences.put(vmIdx, generateVMPreferences(vmIdx));
    }
    return allPreferences;
}
