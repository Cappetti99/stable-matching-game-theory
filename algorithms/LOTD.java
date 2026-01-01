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
 * - Check if VM has available time before successor starts
 * - Duplicate if: duplicate_finish < original_finish + comm_cost
 * 3. Add duplicates to VM schedules
 * 4. Recalculate schedule timing with duplicates
 * 5. Return optimized schedule with duplicates included
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
    private Map<Integer, List<Integer>> vmSchedule; // VM -> tasks (includes duplicates)
    private Map<Integer, Integer> taskToVM; // task -> VM

    // Execution order tracking: VM -> list of tasks in execution order (sorted by AST)
    private Map<Integer, List<ExecutionSlot>> vmExecutionOrder;

    // Duplication tracking
    private Map<Integer, Set<Integer>> duplicatedTasks; // VM -> Set of duplicated task IDs
    private int totalDuplicationCount; // Counter for total duplications

    // Per-VM timing for duplicated tasks (key: "taskId_vmId")
    private Map<String, Double> duplicateAST; // Duplicate Actual Start Time per VM
    private Map<String, Double> duplicateAFT; // Duplicate Actual Finish Time per VM

    private static final boolean VERBOSE = false;  // Set to true for debugging

    /**
     * Represents a scheduled execution slot on a VM.
     * Tracks the task, its start time, and finish time for proper ordering.
     */
    private static class ExecutionSlot implements Comparable<ExecutionSlot> {
        final int taskId;
        double ast;  // Actual Start Time
        double aft;  // Actual Finish Time

        ExecutionSlot(int taskId, double ast, double aft) {
            this.taskId = taskId;
            this.ast = ast;
            this.aft = aft;
        }

        @Override
        public int compareTo(ExecutionSlot other) {
            // Sort by start time (AST)
            int cmp = Double.compare(this.ast, other.ast);
            if (cmp != 0) return cmp;
            // Tie-breaker: sort by task ID for determinism
            return Integer.compare(this.taskId, other.taskId);
        }

        @Override
        public String toString() {
            return String.format("t%d[%.2f-%.2f]", taskId, ast, aft);
        }
    }

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
        this.vmExecutionOrder = new HashMap<>();
        this.duplicatedTasks = new HashMap<>();
        this.totalDuplicationCount = 0;
        this.duplicateAST = new HashMap<>();
        this.duplicateAFT = new HashMap<>();

        for (VM vm : vms) {
            duplicatedTasks.put(vm.getID(), new HashSet<>());
            vmExecutionOrder.put(vm.getID(), new ArrayList<>());
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
        vmExecutionOrder.clear();

        // Deep copy schedule and initialize execution order tracking
        for (Map.Entry<Integer, List<Integer>> entry : preSchedule.entrySet()) {
            int vmId = entry.getKey();
            vmSchedule.put(vmId, new ArrayList<>(entry.getValue()));
            vmExecutionOrder.put(vmId, new ArrayList<>());

            // Build task->VM mapping
            for (Integer taskId : entry.getValue()) {
                taskToVM.put(taskId, vmId);
            }
        }
    }

    /**
     * Step 2: Calculate initial timing using waitingList order within each level.
     * 
     * This ensures that critical path tasks (added first to waitingList) are
     * scheduled to execute first on their assigned VM at each level.
     */
    private void calculateInitialTiming() {
        taskAST.clear();
        taskAFT.clear();

        // Use waitingList order instead of arbitrary topological sort
        List<task> sorted = getTasksInWaitingListOrder();

        for (task t : sorted) {
            calculateTaskTiming(t.getID());
        }

        if (VERBOSE) {
            System.out.println("\nInitial timing (waitingList order):");
            for (task t : sorted) {
                System.out.printf("  t%d: AST=%.2f, AFT=%.2f\n",
                        t.getID(), taskAST.get(t.getID()), taskAFT.get(t.getID()));
            }
        }
    }

    /**
     * Calculate timing for a single task.
     * 
     * This method computes:
     * 1. Data Ready Time (DRT): when all predecessor data is available
     * 2. Machine Ready Time (MRT): when the VM finishes all prior tasks
     * 3. AST = max(DRT, MRT)
     * 4. AFT = AST + execution time
     * 
     * IMPORTANT: This method also updates the VM execution order to ensure
     * correct MRT calculation for subsequent tasks.
     */
    private void calculateTaskTiming(int taskId) {
        task t = Utility.getTaskById(taskId, tasks);
        Integer vmId = taskToVM.get(taskId);

        if (t == null || vmId == null) {
            taskAST.put(taskId, 0.0);
            taskAFT.put(taskId, 0.0);
            return;
        }

        // 1. Data Ready Time (when all predecessors finish + communication)
        double drt = calculateDataReadyTime(t, vmId);

        // 2. Machine Ready Time (when VM can start this task based on execution order)
        double mrt = calculateMachineReadyTime(vmId, taskId, drt);

        // 3. Actual Start Time = max(DRT, MRT)
        double ast = Math.max(drt, mrt);

        // 4. Execution time
        double et = calculateExecutionTime(t, vmId);

        // 5. Actual Finish Time
        double aft = ast + et;

        taskAST.put(taskId, ast);
        taskAFT.put(taskId, aft);

        // 6. Update VM execution order with this task's slot
        updateVMExecutionOrder(vmId, taskId, ast, aft);
    }

    /**
     * Calculate Data Ready Time (DRT) for a task.
     * DRT = max over all predecessors of (predAFT + communication cost)
     */
    private double calculateDataReadyTime(task t, int vmId) {
        double drt = 0.0;
        
        if (t.getPre() != null) {
            for (Integer predId : t.getPre()) {
                Double predAFT = taskAFT.get(predId);
                if (predAFT == null)
                    continue;

                // Check if duplicate exists on same VM (no communication cost)
                if (duplicatedTasks.containsKey(vmId) && 
                    duplicatedTasks.get(vmId).contains(predId)) {
                    // Local duplicate available - use duplicate's finish time if available
                    String dupKey = predId + "_" + vmId;
                    Double dupAFT = duplicateAFT.get(dupKey);
                    if (dupAFT != null) {
                        drt = Math.max(drt, dupAFT);
                    } else {
                        drt = Math.max(drt, predAFT);
                    }
                } else {
                    // Remote predecessor - add communication cost
                    Integer predVM = taskToVM.get(predId);
                    double commCost = (predVM != null && !predVM.equals(vmId))
                            ? getCommunicationCost(predId, t.getID(), predVM, vmId)
                            : 0.0;
                    
                    double newDRT = predAFT + commCost;                  
                    drt = Math.max(drt, newDRT);
                }
            }
        }
        
        return drt;
    }

    /**
     * Calculate Machine Ready Time (MRT) for a task.
     * 
     * MRT is the earliest time the VM can start executing this task, considering:
     * 1. When all currently scheduled tasks on this VM will be done
     * 2. Finding a gap in the execution schedule if possible (insertion-based scheduling)
     * 
     * The actual start time will be max(DRT, MRT) where DRT is the data ready time.
     * 
     * IMPORTANT: This method finds the earliest slot where the task can execute
     * without overlapping with existing tasks. It returns the VM availability time,
     * not the actual task start time (which depends on data readiness).
     * 
     * @param vmId The VM where the task will execute
     * @param taskId The task being scheduled
     * @param drt The Data Ready Time (earliest this task's data is available)
     * @return The earliest time the VM can start this task (may still need to wait for data)
     */
    private double calculateMachineReadyTime(int vmId, int taskId, double drt) {
        List<ExecutionSlot> slots = vmExecutionOrder.get(vmId);
        
        if (slots == null || slots.isEmpty()) {
            // VM is idle, can start immediately (at time 0)
            return 0.0;
        }

        // Calculate execution time for this task
        task t = Utility.getTaskById(taskId, tasks);
        double et = (t != null) ? calculateExecutionTime(t, vmId) : 0.0;

        // Try insertion-based scheduling: find the earliest slot where this task fits
        // Task needs a contiguous time window of size 'et' that doesn't overlap with existing tasks

        // First, check if we can fit before the first scheduled task
        ExecutionSlot first = slots.get(0);
        // Task would start at max(0, drt) = drt (since drt >= 0)
        // Task would end at drt + et
        // This fits before first task if drt + et <= first.ast
        if (drt + et <= first.ast) {
            // Can fit before first task
            return 0.0;  // VM is ready at time 0
        }

        // Try to fit in gaps between existing tasks
        for (int i = 0; i < slots.size() - 1; i++) {
            ExecutionSlot current = slots.get(i);
            ExecutionSlot next = slots.get(i + 1);
            
            // The earliest this task can start in this gap is max(drt, current.aft)
            // because we need data AND the previous task to finish
            double earliestStartInGap = Math.max(drt, current.aft);
            // The task would end at earliestStartInGap + et
            // This fits in the gap if earliestStartInGap + et <= next.ast
            
            if (earliestStartInGap + et <= next.ast) {
                // Found a gap where this task fits
                // Return when VM is ready (after current task finishes)
                return current.aft;
            }
        }

        // No gap found - task must go after the last scheduled task
        ExecutionSlot last = slots.get(slots.size() - 1);
        return last.aft;
    }

    /**
     * Update the VM execution order with a newly scheduled task.
     * Maintains the list sorted by AST for efficient gap-finding.
     */
    private void updateVMExecutionOrder(int vmId, int taskId, double ast, double aft) {
        List<ExecutionSlot> slots = vmExecutionOrder.get(vmId);
        if (slots == null) {
            slots = new ArrayList<>();
            vmExecutionOrder.put(vmId, slots);
        }

        // Remove any existing slot for this task (in case of recalculation)
        slots.removeIf(slot -> slot.taskId == taskId);

        // Create new slot and insert in sorted order
        ExecutionSlot newSlot = new ExecutionSlot(taskId, ast, aft);
        
        // Binary search for insertion point to maintain sorted order
        int insertIdx = Collections.binarySearch(slots, newSlot);
        if (insertIdx < 0) {
            insertIdx = -(insertIdx + 1);
        }
        slots.add(insertIdx, newSlot);

        // Validate: check for overlaps (debug mode only)
        if (VERBOSE) {
            validateNoOverlaps(vmId, slots);
        }
    }

    /**
     * Validate that no tasks overlap on a VM (debug helper)
     */
    private void validateNoOverlaps(int vmId, List<ExecutionSlot> slots) {
        for (int i = 0; i < slots.size() - 1; i++) {
            ExecutionSlot current = slots.get(i);
            ExecutionSlot next = slots.get(i + 1);
            
            if (current.aft > next.ast + 1e-9) {  // Small epsilon for floating point
                System.err.println("WARNING: Overlap detected on VM" + vmId + 
                    ": " + current + " overlaps with " + next);
            }
        }
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
        task entryTask = Utility.getTaskById(entryTaskId, tasks);
        if (entryTask == null || entryTask.getSucc() == null)
            return;

        Integer originalVM = taskToVM.get(entryTaskId);
        if (originalVM == null)
            return;

        if (VERBOSE) {
            System.out.println("\n--- Analyzing entry task t" + entryTaskId + " ---");
            System.out.println("Currently on VM" + originalVM);
            System.out.println("Successors: " + entryTask.getSucc());
            // DEBUG: Show current state of vmExecutionOrder for each VM
            System.out.println("DEBUG: Current vmExecutionOrder state:");
            for (Map.Entry<Integer, List<ExecutionSlot>> entry : vmExecutionOrder.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    System.out.println("  VM" + entry.getKey() + ": " + entry.getValue());
                }
            }
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
            double[] duplicateTiming = shouldDuplicate(entryTaskId, succVM, succId);
            if (duplicateTiming != null) {
                performDuplication(entryTaskId, succVM, duplicateTiming[0], duplicateTiming[1]);

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
     * 1. VM must have time to execute duplicate before successor starts
     * 2. Duplicate must finish before data arrives from original
     * 
     * @return double[] {AST, AFT} if beneficial, null otherwise
     */
    private double[] shouldDuplicate(int entryTaskId, int targetVM, int succId) {
        task entryTask = Utility.getTaskById(entryTaskId, tasks);
        Integer originalVM = taskToVM.get(entryTaskId);

        if (entryTask == null || originalVM == null)
            return null;

        // 1. Calculate execution time for duplicate
        double duplicateET = calculateExecutionTime(entryTask, targetVM);

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

        // 4. Find earliest idle slot where duplicate can fit BEFORE successor starts
        // The duplicate must start after entry task's predecessors finish on target VM
        double earliestStart = 0.0;
        
        // Check predecessors of entry task to see when their data arrives on target VM
        for (Integer predId : entryTask.getPre()) {
            Double predFinish = taskAFT.get(predId);
            if (predFinish != null) {
                Integer predVM = taskToVM.get(predId);
                if (predVM != null && predVM != targetVM) {
                    double predCommCost = getCommunicationCost(predId, entryTaskId, predVM, targetVM);
                    earliestStart = Math.max(earliestStart, predFinish + predCommCost);
                } else if (predVM != null) {
                    // Predecessor on same VM, just wait for it to finish
                    earliestStart = Math.max(earliestStart, predFinish);
                }
            }
        }

        // Find idle slot on target VM where duplicate can fit
        double idleSlotStart = findIdleSlot(targetVM, earliestStart, succAST, duplicateET);
        
        if (idleSlotStart < 0) {
            // No suitable idle slot found
            if (VERBOSE) {
                System.out.printf("  No idle slot found (earliest=%.2f, deadline=%.2f, duration=%.2f)\n",
                        earliestStart, succAST, duplicateET);
            }
            return null;
        }

        double duplicateFinish = idleSlotStart + duplicateET;

        if (VERBOSE) {
            System.out.printf("  Idle slot: %.2f - %.2f\n", idleSlotStart, duplicateFinish);
            System.out.printf("  Data arrival: %.2f (original=%.2f + comm=%.2f)\n",
                    dataArrival, originalFinish, commCost);
            System.out.printf("  Successor starts: %.2f\n", succAST);
        }

        // Check conditions:
        // A) Duplicate finishes before successor starts (fits in idle slot)
        boolean hasTimeSlot = duplicateFinish <= succAST;

        // B) Duplicate finishes before data arrives (performance gain)
        boolean isBeneficial = duplicateFinish < dataArrival;

        if (VERBOSE) {
            System.out.printf("  hasTimeSlot=%b, isBeneficial=%b\n", hasTimeSlot, isBeneficial);
        }

        if (hasTimeSlot && isBeneficial) {
            return new double[] { idleSlotStart, duplicateFinish };
        }
        return null;
    }

    /**
     * Find an idle time slot on VM where a task of given duration can fit.
     * 
     * Uses the vmExecutionOrder for accurate slot detection, which maintains
     * tasks sorted by their Actual Start Time (AST).
     * 
     * @param vmId Target VM
     * @param earliestStart Earliest time the task can start (based on data availability)
     * @param deadline Latest time the task must finish
     * @param duration Task execution time
     * @return Start time of idle slot, or -1 if no slot found
     */
    private double findIdleSlot(int vmId, double earliestStart, double deadline, double duration) {
        List<ExecutionSlot> slots = vmExecutionOrder.get(vmId);
        
        if (slots == null || slots.isEmpty()) {
            // VM has no tasks, can start immediately
            if (earliestStart + duration <= deadline) {
                return earliestStart;
            }
            return -1;
        }

        // Try to fit in gap before first task
        if (!slots.isEmpty()) {
            ExecutionSlot first = slots.get(0);
            double gapEnd = Math.min(first.ast, deadline);
            if (earliestStart + duration <= gapEnd) {
                return earliestStart;
            }
        }

        // Try to fit in gaps between tasks
        for (int i = 0; i < slots.size() - 1; i++) {
            ExecutionSlot current = slots.get(i);
            ExecutionSlot next = slots.get(i + 1);
            
            double gapStart = Math.max(earliestStart, current.aft);
            double gapEnd = Math.min(deadline, next.ast);
            
            if (gapEnd - gapStart >= duration) {
                return gapStart;
            }
        }

        // Try to fit after last task
        if (!slots.isEmpty()) {
            ExecutionSlot last = slots.get(slots.size() - 1);
            double slotStart = Math.max(earliestStart, last.aft);
            if (slotStart + duration <= deadline) {
                return slotStart;
            }
        }

        return -1; // No suitable slot found
    }

    /**
     * Perform the actual duplication
     * 
     * @param taskId Task to duplicate
     * @param targetVM VM to duplicate onto
     * @param dupAST Actual Start Time for this duplicate on targetVM
     * @param dupAFT Actual Finish Time for this duplicate on targetVM
     */
    private void performDuplication(int taskId, int targetVM, double dupAST, double dupAFT) {
        if (VERBOSE) {
            System.out.println("DEBUG performDuplication: t" + taskId + " -> VM" + targetVM + 
                " AST=" + dupAST + ", AFT=" + dupAFT);
            System.out.println("  Before update, VM" + targetVM + " execution order: " + 
                vmExecutionOrder.get(targetVM));
        }
        
        // Mark as duplicated (this affects future timing calculations)
        duplicatedTasks.get(targetVM).add(taskId);
        totalDuplicationCount++; // Increment counter

        // Store per-VM timing for this duplicate
        String key = taskId + "_" + targetVM;
        this.duplicateAST.put(key, dupAST);
        this.duplicateAFT.put(key, dupAFT);

        // Add duplicate to vmSchedule (real task, not phantom)
        // This ensures AVU and other metrics account for duplicate execution time
        if (!vmSchedule.get(targetVM).contains(taskId)) {
            vmSchedule.get(targetVM).add(taskId);
        }

        // Add duplicate to VM execution order
        // Use a special key format for duplicates to distinguish from original
        updateVMExecutionOrder(targetVM, taskId, dupAST, dupAFT);
        
        if (VERBOSE) {
            System.out.println("  After update, VM" + targetVM + " execution order: " + 
                vmExecutionOrder.get(targetVM));
        }

        // Recalculate timing for affected successors
        task t = Utility.getTaskById(taskId, tasks);
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
                task t = Utility.getTaskById(taskId, tasks);
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
     * 
     * Performs a complete recalculation of all task timings with duplicates in place.
     * This ensures consistency after all duplications have been performed.
     * 
     * IMPORTANT: Duplicates must be added to vmExecutionOrder FIRST, so that when
     * we recalculate original task timing, the duplicates are already "reserved"
     * in the schedule and original tasks won't overlap with them.
     * 
     * Uses waitingList order to ensure CP tasks execute first on their VM.
     */
    private void calculateFinalTiming() {
        // Clear execution order for fresh recalculation
        for (List<ExecutionSlot> slots : vmExecutionOrder.values()) {
            slots.clear();
        }

        // STEP 1: Add all duplicates to vmExecutionOrder FIRST
        // This reserves their time slots before we calculate original task timing
        for (Map.Entry<Integer, Set<Integer>> entry : duplicatedTasks.entrySet()) {
            int vmId = entry.getKey();
            for (Integer taskId : entry.getValue()) {
                String key = taskId + "_" + vmId;
                Double dupAST = duplicateAST.get(key);
                Double dupAFT = duplicateAFT.get(key);
                if (dupAST != null && dupAFT != null) {
                    // Add duplicate to execution order to reserve its slot
                    updateVMExecutionOrder(vmId, taskId, dupAST, dupAFT);
                }
            }
        }

        // STEP 2: Full recalculation of original tasks with duplicates already in place
        // Use waitingList order to ensure CP tasks execute first on their VM
        List<task> sorted = getTasksInWaitingListOrder();

        for (task t : sorted) {
            calculateTaskTiming(t.getID());
        }

        // Validate final schedule
        if (VERBOSE) {
            validateFinalSchedule();
        }
    }

    /**
     * Validate the final schedule for correctness.
     * Checks:
     * 1. No overlapping tasks on any VM
     * 2. All predecessor tasks finish before successors start
     */
    private void validateFinalSchedule() {
        System.out.println("\n--- Validating Final Schedule ---");
        boolean valid = true;

        // Check 1: No overlaps on any VM
        for (Map.Entry<Integer, List<ExecutionSlot>> entry : vmExecutionOrder.entrySet()) {
            int vmId = entry.getKey();
            List<ExecutionSlot> slots = entry.getValue();
            
            for (int i = 0; i < slots.size() - 1; i++) {
                ExecutionSlot current = slots.get(i);
                ExecutionSlot next = slots.get(i + 1);
                
                if (current.aft > next.ast + 1e-9) {
                    System.err.println("ERROR: Overlap on VM" + vmId + 
                        ": " + current + " overlaps with " + next);
                    valid = false;
                }
            }
        }

        // Check 2: DAG dependencies respected
        for (task t : tasks) {
            Double taskStart = taskAST.get(t.getID());
            if (taskStart == null) continue;

            if (t.getPre() != null) {
                for (Integer predId : t.getPre()) {
                    Double predFinish = taskAFT.get(predId);
                    if (predFinish == null) continue;

                    Integer taskVM = taskToVM.get(t.getID());
                    Integer predVM = taskToVM.get(predId);

                    // Check if duplicate exists on same VM
                    boolean hasDuplicate = taskVM != null && 
                        duplicatedTasks.containsKey(taskVM) &&
                        duplicatedTasks.get(taskVM).contains(predId);

                    if (hasDuplicate) {
                        // Use duplicate's finish time
                        String dupKey = predId + "_" + taskVM;
                        Double dupFinish = duplicateAFT.get(dupKey);
                        if (dupFinish != null && dupFinish > taskStart + 1e-9) {
                            System.err.println("ERROR: Task t" + t.getID() + 
                                " starts at " + taskStart + 
                                " but duplicate of pred t" + predId + 
                                " finishes at " + dupFinish);
                            valid = false;
                        }
                    } else if (predVM != null && taskVM != null && predVM.equals(taskVM)) {
                        // Same VM, no communication
                        if (predFinish > taskStart + 1e-9) {
                            System.err.println("ERROR: Task t" + t.getID() + 
                                " starts at " + taskStart + 
                                " but predecessor t" + predId + 
                                " finishes at " + predFinish);
                            valid = false;
                        }
                    }
                    // Note: For remote predecessors, we don't check here because 
                    // communication time is added to DRT, not subtracted from predFinish
                }
            }
        }

        if (valid) {
            System.out.println("Schedule validation PASSED");
        } else {
            System.err.println("Schedule validation FAILED");
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
        VM src = Utility.getVMById(srcVM, vms);
        if (src == null)
            return 0.0;

        double bandwidth = src.getBandwidthToVM(dstVM);
        if (bandwidth <= 0)
            bandwidth = 25.0; // Default

        // Get communication cost from map (stored at avgBandwidth=25)
        String key = srcTaskId + "_" + dstTaskId;
        double commTimeAtAvg = communicationCosts.getOrDefault(key, 0.0);

        // Scale by actual bandwidth
        double result = commTimeAtAvg * 25.0 / bandwidth;
    
        return result;
    }

    /**
     * Calculate execution time of task on VM
     */
    private double calculateExecutionTime(task t, int vmId) {
        VM vm = Utility.getVMById(vmId, vms);
        if (vm == null)
            return t.getSize();

        double capacity = vm.getProcessingCapacity();
        if (capacity <= 0)
            return t.getSize();

        return t.getSize() / capacity;
    }
    
    /**
     * Get tasks ordered by waitingList order within each DAG level.
     * 
     * This ensures that:
     * 1. Tasks are processed level by level (respecting DAG dependencies)
     * 2. Within each level, tasks on each VM are processed in the order
     *    they were added to the VM's waitingList (CP tasks first, then non-CP)
     * 
     * This guarantees that critical path tasks execute first on their assigned VM.
     * 
     * @return List of tasks in the correct execution order
     */
    private List<task> getTasksInWaitingListOrder() {
        List<task> result = new ArrayList<>();
        Set<Integer> processed = new HashSet<>();
        
        // Get task levels using Utility
        Map<Integer, List<Integer>> levelTasks = Utility.organizeTasksByLevels(tasks);
        
        // Sort levels in ascending order (0, 1, 2, ...)
        List<Integer> sortedLevels = new ArrayList<>(levelTasks.keySet());
        Collections.sort(sortedLevels);
        
        // Build taskId -> level mapping for quick lookup
        Map<Integer, Integer> taskToLevel = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : levelTasks.entrySet()) {
            int level = entry.getKey();
            for (Integer taskId : entry.getValue()) {
                taskToLevel.put(taskId, level);
            }
        }
        
        // Process each level
        for (Integer level : sortedLevels) {
            // For each VM, get tasks from this level in waitingList order
            for (VM vm : vms) {
                List<Integer> waitingList = vm.getWaitingList();
                
                // Process tasks from this VM's waitingList that belong to current level
                for (Integer taskId : waitingList) {
                    if (processed.contains(taskId)) {
                        continue;
                    }
                    
                    Integer taskLevel = taskToLevel.get(taskId);
                    if (taskLevel != null && taskLevel.equals(level)) {
                        task t = Utility.getTaskById(taskId, tasks);
                        if (t != null) {
                            result.add(t);
                            processed.add(taskId);
                        }
                    }
                }
            }
            
            // Safety: add any tasks from this level not in any waitingList
            // (shouldn't happen, but defensive programming)
            for (Integer taskId : levelTasks.get(level)) {
                if (!processed.contains(taskId)) {
                    task t = Utility.getTaskById(taskId, tasks);
                    if (t != null) {
                        result.add(t);
                        processed.add(taskId);
                        if (VERBOSE) {
                            System.out.println("WARNING: Task t" + taskId + 
                                " at level " + level + " not found in any VM waitingList");
                        }
                    }
                }
            }
        }
        
        return result;
    }



    /**
     * Print final state for debugging
     */
    private void printFinalState() {
        System.out.println("\n=== LOTD Final State ===");

        System.out.println("\nVM Schedules (task assignment):");
        for (Map.Entry<Integer, List<Integer>> entry : vmSchedule.entrySet()) {
            System.out.println("VM" + entry.getKey() + ": " + entry.getValue());
        }

        System.out.println("\nVM Execution Order (actual timing):");
        for (Map.Entry<Integer, List<ExecutionSlot>> entry : vmExecutionOrder.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                System.out.println("VM" + entry.getKey() + ": " + entry.getValue());
            }
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

    public int getTotalDuplicationCount() {
        return totalDuplicationCount;
    }

    public Map<Integer, Double> getTaskAST() {
        return taskAST;
    }

    public Map<Integer, Double> getTaskAFT() {
        return taskAFT;
    }

    /**
     * Get per-VM AST for duplicated tasks
     * Key format: "taskId_vmId"
     */
    public Map<String, Double> getDuplicateAST() {
        return duplicateAST;
    }

    /**
     * Get per-VM AFT for duplicated tasks
     * Key format: "taskId_vmId"
     */
    public Map<String, Double> getDuplicateAFT() {
        return duplicateAFT;
    }

    /**
     * Get the execution order for all VMs.
     * Returns a map of vmId -> list of (taskId, AST, AFT) sorted by AST.
     * Useful for Gantt chart generation and schedule validation.
     */
    public Map<Integer, List<double[]>> getVMExecutionOrder() {
        Map<Integer, List<double[]>> result = new HashMap<>();
        
        for (Map.Entry<Integer, List<ExecutionSlot>> entry : vmExecutionOrder.entrySet()) {
            List<double[]> slots = new ArrayList<>();
            for (ExecutionSlot slot : entry.getValue()) {
                slots.add(new double[]{slot.taskId, slot.ast, slot.aft});
            }
            result.put(entry.getKey(), slots);
        }
        
        return result;
    }
}