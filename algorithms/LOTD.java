import java.util.*;

public class LOTD {
    private SMGT smgt;
    private List<VM> vms;
    private List<task> tasks;
    private Map<Integer, Double> taskAST; // Actual Start Time for each task
    private Map<Integer, Double> taskAFT; // Actual Finish Time for each task
    private Map<Integer, Set<Integer>> duplicatedTasks; // VM ID -> Set of duplicated task IDs
    private Map<Integer, List<Integer>> vmSchedule; // VM ID -> List of task IDs (ordered by execution)
    private Map<Integer, Integer> taskToVM; // Task ID -> VM ID (original assignment)
    
    public LOTD() {
        this.taskAST = new HashMap<>();
        this.taskAFT = new HashMap<>();
        this.duplicatedTasks = new HashMap<>();
        this.vmSchedule = new HashMap<>();
        this.taskToVM = new HashMap<>();
    }
    
    public LOTD(SMGT smgt) {
        this();
        this.smgt = smgt;
        this.vms = smgt.getVMs();
        this.tasks = smgt.getTasks();
        
        // Initialize duplicated tasks sets for each VM
        for (VM vm : vms) {
            duplicatedTasks.put(vm.getID(), new HashSet<>());
        }
    }
    
    /**
     * Sets the pre-schedule from SMGT results
     */
    public void setPreSchedule(Map<Integer, List<Integer>> smgtAssignments) {
        this.vmSchedule = new HashMap<>();
        this.taskToVM = new HashMap<>();
        
        for (Map.Entry<Integer, List<Integer>> entry : smgtAssignments.entrySet()) {
            int vmId = entry.getKey();
            List<Integer> taskList = new ArrayList<>(entry.getValue());
            vmSchedule.put(vmId, taskList);
            
            for (Integer taskId : taskList) {
                taskToVM.put(taskId, vmId);
            }
        }
    }
    
    /**
     * Executes the LOTD algorithm following the pseudocode:
     * - Only considers tasks in level 0 (entry tasks) for duplication
     * - Finds VM candidates where successors are assigned
     * - Checks idle slots and Rule 2 (no AFT increase)
     * 
     * @param preSchedule The pre-schedule from DCP+SMGT (VM ID -> list of task IDs)
     * @return Updated schedule after duplication optimization
     */
    public Map<Integer, List<Integer>> executeLOTDCorrect(Map<Integer, List<Integer>> preSchedule) {
        System.out.println("\n=== EXECUTING LOTD ALGORITHM (CORRECTED VERSION) ===");
        
        // Initialize from pre-schedule
        setPreSchedule(preSchedule);
        
        // Initialize task timing
        initializeTaskTimingCorrect();
        
        // Line 2: T₀ ← {task nel livello 0} - Get only level 0 tasks (entry tasks)
        List<Integer> T0 = getLevel0Tasks();
        System.out.println("Level 0 tasks (entry tasks): " + T0);
        
        if (T0.isEmpty()) {
            System.out.println("No level 0 tasks found, skipping LOTD");
            return vmSchedule;
        }
        
        // Lines 4-51: Process each entry task for potential duplication
        for (Integer taskId : T0) {
            task ti = smgt.getTaskById(taskId);
            if (ti == null) continue;
            
            // Line 5: Get original VM assignment
            Integer vmOrig = taskToVM.get(taskId);
            if (vmOrig == null) {
                System.out.println("Task t" + taskId + " has no VM assignment, skipping");
                continue;
            }
            
            System.out.println("\n--- Processing entry task t" + taskId + " (assigned to VM" + vmOrig + ") ---");
            
            // Line 8: Find VM candidates where successors are assigned
            Set<Integer> vmCandidates = new HashSet<>();
            for (Integer succId : ti.getSucc()) {
                Integer succVM = taskToVM.get(succId);
                if (succVM != null) {
                    vmCandidates.add(succVM);
                }
            }
            System.out.println("VM candidates (where successors are): " + vmCandidates);
            
            // Lines 10-50: Try duplication on each candidate VM
            for (Integer vmK : vmCandidates) {
                // Line 11: Skip if same VM
                if (vmK.equals(vmOrig)) {
                    continue;
                }
                
                System.out.println("  Checking duplication to VM" + vmK);
                
                // Line 15: Find idle slots on VMk
                List<IdleSlot> idleSlots = findIdleSlots(vmK);
                
                // Calculate ET of task on this VM
                double etOnVMk = calculateET(ti, vmK);
                System.out.println("    ET(t" + taskId + ", VM" + vmK + ") = " + etOnVMk);
                
                // Lines 17-48: Check each idle slot
                for (IdleSlot slot : idleSlots) {
                    // Line 18: Check if slot is large enough
                    if (slot.duration >= etOnVMk) {
                        System.out.println("    Found suitable slot: [" + slot.start + ", " + slot.end + "] (duration=" + slot.duration + ")");
                        
                        // Lines 20-26: Check Rule 2 - verify AFT doesn't increase
                        Map<Integer, Double> oldAFT = new HashMap<>(taskAFT);
                        
                        // Line 23: Simulate insertion
                        boolean canInsert = simulateInsertion(taskId, vmK, slot);
                        
                        if (canInsert) {
                            // Line 26: Check if AFT_new <= AFT_old for all tasks
                            boolean rule2Satisfied = checkRule2(oldAFT);
                            
                            if (rule2Satisfied) {
                                // Lines 28-40: Duplication is beneficial
                                System.out.println("    ✓ Rule 2 satisfied - duplicating task");
                                
                                // Create duplicate and assign
                                performDuplication(taskId, vmK, slot);
                                
                                // Lines 33-40: Update successors to use duplicate if beneficial
                                updateSuccessorsForDuplicate(taskId, vmK);
                                
                                // Line 42: Break from slot loop for this VM
                                break;
                            } else {
                                // Lines 43-45: Rollback
                                System.out.println("    ✗ Rule 2 not satisfied - rolling back");
                                rollbackInsertion(taskId, vmK, oldAFT);
                            }
                        }
                    }
                }
            }
        }
        
        System.out.println("\n=== LOTD ALGORITHM COMPLETED ===");
        printFinalSchedule();
        
        return vmSchedule;
    }
    
    /**
     * Represents an idle slot on a VM
     */
    private static class IdleSlot {
        double start;
        double end;
        double duration;
        
        IdleSlot(double start, double end) {
            this.start = start;
            this.end = end;
            this.duration = end - start;
        }
    }
    
    /**
     * Get tasks at level 0 (entry tasks with no predecessors)
     */
    private List<Integer> getLevel0Tasks() {
        List<Integer> level0 = new ArrayList<>();
        for (task t : tasks) {
            if (t.getPre() == null || t.getPre().isEmpty()) {
                level0.add(t.getID());
            }
        }
        return level0;
    }
    
    /**
     * Initialize task timing from current schedule
     */
    private void initializeTaskTimingCorrect() {
        taskAST.clear();
        taskAFT.clear();
        
        // Process tasks in topological order
        List<task> sortedTasks = topologicalSort();
        
        for (task t : sortedTasks) {
            int taskId = t.getID();
            Integer vmId = taskToVM.get(taskId);
            
            if (vmId == null) {
                // Task not assigned, skip
                continue;
            }
            
            double st;
            if (t.getPre() == null || t.getPre().isEmpty()) {
                // Entry task
                st = 0.0;
            } else {
                // Calculate ST = max{FT(pred) + Ttrans(pred, ti)}
                st = 0.0;
                for (Integer predId : t.getPre()) {
                    Double predAFT = taskAFT.get(predId);
                    if (predAFT == null) continue;
                    
                    Integer predVM = taskToVM.get(predId);
                    double transTime = 0.0;
                    if (predVM != null && !predVM.equals(vmId)) {
                        // Different VMs - add transmission time
                        transTime = calculateTransmissionTime(predId, taskId, predVM, vmId);
                    }
                    st = Math.max(st, predAFT + transTime);
                }
            }
            
            double et = calculateET(t, vmId);
            double ft = st + et;
            
            taskAST.put(taskId, st);
            taskAFT.put(taskId, ft);
        }
    }
    
    /**
     * Calculate Execution Time: ET = size / processing_capacity
     */
    private double calculateET(task t, int vmId) {
        if (vmId < 0 || vmId >= vms.size()) {
            return t.getSize(); // Default
        }
        VM vm = vms.get(vmId);
        double capacity = vm.getCapability("processingCapacity");
        if (capacity <= 0) {
            capacity = vm.getCapability("processing");
        }
        if (capacity <= 0) {
            return t.getSize();
        }
        return t.getSize() / capacity;
    }
    
    /**
     * Calculate transmission time between two tasks on different VMs
     */
    private double calculateTransmissionTime(int srcTaskId, int dstTaskId, int srcVM, int dstVM) {
        if (srcVM == dstVM) return 0.0;
        
        task srcTask = smgt.getTaskById(srcTaskId);
        if (srcTask == null) return 0.0;
        
        VM src = vms.get(srcVM);
        double bandwidth = src.getBandwidthToVM(dstVM);
        if (bandwidth <= 0) bandwidth = 25.0; // Default bandwidth
        
        double dataSize = srcTask.getSize() * 0.4; // CCR = 0.4
        return dataSize / bandwidth;
    }
    
    /**
     * Find idle slots on a VM based on current task schedule
     */
    private List<IdleSlot> findIdleSlots(int vmId) {
        List<IdleSlot> slots = new ArrayList<>();
        List<Integer> vmTasks = vmSchedule.getOrDefault(vmId, new ArrayList<>());
        
        if (vmTasks.isEmpty()) {
            // Entire timeline is idle
            slots.add(new IdleSlot(0.0, Double.MAX_VALUE));
            return slots;
        }
        
        // Sort tasks by their start time on this VM
        List<double[]> intervals = new ArrayList<>();
        for (Integer taskId : vmTasks) {
            Double st = taskAST.get(taskId);
            Double ft = taskAFT.get(taskId);
            if (st != null && ft != null) {
                intervals.add(new double[]{st, ft});
            }
        }
        intervals.sort((a, b) -> Double.compare(a[0], b[0]));
        
        // Find gaps
        double currentEnd = 0.0;
        for (double[] interval : intervals) {
            if (interval[0] > currentEnd) {
                slots.add(new IdleSlot(currentEnd, interval[0]));
            }
            currentEnd = Math.max(currentEnd, interval[1]);
        }
        
        // Add slot at the end
        slots.add(new IdleSlot(currentEnd, currentEnd + 1000.0));
        
        return slots;
    }
    
    /**
     * Simulate inserting a task duplicate in a slot
     */
    private boolean simulateInsertion(int taskId, int vmId, IdleSlot slot) {
        task t = smgt.getTaskById(taskId);
        if (t == null) return false;
        
        double et = calculateET(t, vmId);
        double newST = slot.start;
        double newFT = newST + et;
        
        // Check it fits in the slot
        if (newFT > slot.end && slot.end < Double.MAX_VALUE) {
            return false;
        }
        
        // Temporarily update timing
        taskAST.put(-taskId, newST); // Use negative ID for duplicate
        taskAFT.put(-taskId, newFT);
        
        return true;
    }
    
    /**
     * Check Rule 2: AFT_new <= AFT_old for all tasks
     */
    private boolean checkRule2(Map<Integer, Double> oldAFT) {
        for (Map.Entry<Integer, Double> entry : taskAFT.entrySet()) {
            int taskId = entry.getKey();
            if (taskId < 0) continue; // Skip duplicates
            
            Double oldFT = oldAFT.get(taskId);
            Double newFT = entry.getValue();
            
            if (oldFT != null && newFT != null && newFT > oldFT + 0.001) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Perform the actual duplication
     */
    private void performDuplication(int taskId, int vmId, IdleSlot slot) {
        task t = smgt.getTaskById(taskId);
        if (t == null) return;
        
        // Add to duplicated tasks set
        duplicatedTasks.get(vmId).add(taskId);
        
        // Add to VM schedule
        vmSchedule.get(vmId).add(taskId);
        
        // Update timing
        double et = calculateET(t, vmId);
        taskAST.put(taskId, slot.start);
        taskAFT.put(taskId, slot.start + et);
        
        System.out.println("    Duplicated t" + taskId + " to VM" + vmId + 
                          " [ST=" + slot.start + ", FT=" + (slot.start + et) + "]");
    }
    
    /**
     * Update successors to use the duplicate if beneficial
     */
    private void updateSuccessorsForDuplicate(int taskId, int vmK) {
        task ti = smgt.getTaskById(taskId);
        if (ti == null) return;
        
        Integer origVM = taskToVM.get(taskId);
        Double dupAFT = taskAFT.get(taskId);
        
        for (Integer succId : ti.getSucc()) {
            Integer succVM = taskToVM.get(succId);
            if (succVM == null || !succVM.equals(vmK)) continue;
            
            // Calculate transmission time from original vs duplicate
            double transFromOrig = (origVM != null && !origVM.equals(vmK)) ? 
                calculateTransmissionTime(taskId, succId, origVM, vmK) : 0.0;
            double transFromDup = 0.0; // Same VM, no transmission
            
            Double origAFT = taskAFT.get(taskId);
            
            if (dupAFT != null && origAFT != null) {
                double availFromOrig = origAFT + transFromOrig;
                double availFromDup = dupAFT + transFromDup;
                
                if (availFromDup < availFromOrig) {
                    // Update successor to use duplicate
                    Double oldSuccAST = taskAST.get(succId);
                    Double newSuccAST = availFromDup;
                    
                    if (oldSuccAST != null && newSuccAST < oldSuccAST) {
                        task succ = smgt.getTaskById(succId);
                        double succET = calculateET(succ, vmK);
                        taskAST.put(succId, newSuccAST);
                        taskAFT.put(succId, newSuccAST + succET);
                        System.out.println("    Updated successor t" + succId + " to use duplicate");
                    }
                }
            }
        }
    }
    
    /**
     * Rollback a simulated insertion
     */
    private void rollbackInsertion(int taskId, int vmId, Map<Integer, Double> oldAFT) {
        taskAST.remove(-taskId);
        taskAFT.remove(-taskId);
        taskAFT.putAll(oldAFT);
    }
    
    /**
     * Perform topological sort of tasks
     */
    private List<task> topologicalSort() {
        List<task> result = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Set<Integer> visiting = new HashSet<>();
        
        for (task t : tasks) {
            if (!visited.contains(t.getID())) {
                topologicalSortUtil(t.getID(), visited, visiting, result);
            }
        }
        
        Collections.reverse(result);
        return result;
    }
    
    private void topologicalSortUtil(int taskId, Set<Integer> visited, Set<Integer> visiting, List<task> result) {
        if (visiting.contains(taskId) || visited.contains(taskId)) {
            return;
        }
        
        visiting.add(taskId);
        task currentTask = smgt.getTaskById(taskId);
        
        if (currentTask != null) {
            for (int succId : currentTask.getSucc()) {
                topologicalSortUtil(succId, visited, visiting, result);
            }
            result.add(currentTask);
        }
        
        visiting.remove(taskId);
        visited.add(taskId);
    }
    
    /**
     * Print final schedule
     */
    private void printFinalSchedule() {
        System.out.println("\nFinal Schedule after LOTD:");
        for (Map.Entry<Integer, List<Integer>> entry : vmSchedule.entrySet()) {
            int vmId = entry.getKey();
            Set<Integer> dups = duplicatedTasks.getOrDefault(vmId, new HashSet<>());
            System.out.println("  VM" + vmId + ": " + entry.getValue() + 
                              (dups.isEmpty() ? "" : " (duplicates: " + dups + ")"));
        }
    }
    
    // ========== Original method kept for backward compatibility ==========
    
    /**
     * Original executeLOTD - kept for backward compatibility
     * @deprecated Use executeLOTDCorrect instead
     */
    public Map<Integer, List<Integer>> executeLOTD() {
        // Step 1: Call DCP and SMGT to generate pre-schedule S
        System.out.println("=== EXECUTING LOTD ALGORITHM (LEGACY) ===");
        
        // Generate initial schedule using SMGT
        Map<Integer, List<Integer>> preSchedule = smgt.runSMGTAlgorithmCorrect();
        
        // Use the corrected version
        return executeLOTDCorrect(preSchedule);
    }
    
    // Getter methods for accessing results
    public Map<Integer, Double> getTaskAST() {
        return new HashMap<>(taskAST);
    }
    
    public Map<Integer, Double> getTaskAFT() {
        return new HashMap<>(taskAFT);
    }
    
    public Map<Integer, Set<Integer>> getDuplicatedTasks() {
        return new HashMap<>(duplicatedTasks);
    }
    
    public Map<Integer, List<Integer>> getVMSchedule() {
        return new HashMap<>(vmSchedule);
    }
}
