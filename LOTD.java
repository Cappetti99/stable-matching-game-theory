import java.util.*;

public class LOTD {
    private SMGT smgt;
    private List<VM> vms;
    private List<task> tasks;
    private Map<Integer, Double> taskAST; // Actual Start Time for each task
    private Map<Integer, Double> taskAFT; // Actual Finish Time for each task
    private Map<Integer, Set<Integer>> replicatedTasks; // VM ID -> Set of replicated task IDs
    private Map<Integer, List<Integer>> vmWaitingLists; // VM ID -> List of task IDs in waiting list
    
    public LOTD() {
        this.taskAST = new HashMap<>();
        this.taskAFT = new HashMap<>();
        this.replicatedTasks = new HashMap<>();
        this.vmWaitingLists = new HashMap<>();
    }
    
    public LOTD(SMGT smgt) {
        this();
        this.smgt = smgt;
        this.vms = smgt.getVMs();
        this.tasks = smgt.getTasks();
        
        // Initialize replicated tasks sets for each VM
        for (VM vm : vms) {
            replicatedTasks.put(vm.getID(), new HashSet<>());
        }
    }
    
    /**
     * Executes the LOTD (List of Task Duplication) algorithm
     * @return Updated task assignments after duplication
     */
    public Map<Integer, List<Integer>> executeLOTD() {
        // Step 1: Call DCP and SMGT to generate pre-schedule S
        System.out.println("=== EXECUTING LOTD ALGORITHM ===");
        System.out.println("Step 1: Generating pre-schedule using DCP and SMGT...");
        
        // Generate initial schedule using SMGT
        vmWaitingLists = smgt.runSMGTAlgorithmCorrect();
        
        // Initialize task timing information from the initial schedule
        initializeTaskTiming();
        
        System.out.println("Initial pre-schedule generated successfully.");
        
        // Step 2: LOTD main algorithm
        System.out.println("\nStep 2: Executing LOTD duplication algorithm...");
        
        // For k=0, 1, ..., m-1 (for each VM)
        for (int k = 0; k < vms.size(); k++) {
            System.out.println("\n--- Processing VM " + k + " ---");
            
            // Get the first task in the waiting list of VM k
            List<Integer> waitingList = vmWaitingLists.get(k);
            if (waitingList == null || waitingList.isEmpty()) {
                System.out.println("VM " + k + " has no tasks in waiting list, skipping...");
                continue;
            }
            
            int taskId = waitingList.get(0); // t <- the first task in the waiting list of VM k
            task t = smgt.getTaskById(taskId);
            
            System.out.println("Processing task " + taskId + " (first in VM " + k + " waiting list)");
            
            // Check if t.AST != 0 (i.e., task has predecessors and meaningful start time)
            double currentAST = taskAST.getOrDefault(taskId, 0.0);
            if (currentAST > 0.0 && !t.getPre().isEmpty()) {
                System.out.println("Task " + taskId + " has AST = " + currentAST + ", checking for beneficial duplication...");
                
                double minST = Double.POSITIVE_INFINITY; // minST <- inf
                int minPredecessor = -1; // minPredecessor <- index of task with minST
                
                // For each predecessor p of t do
                for (int predecessorId : t.getPre()) {
                    System.out.println("  Checking predecessor " + predecessorId);
                    
                    // Calculate the start time of t if we duplicate this predecessor to VM k
                    double st = calculateStartTimeAfterDuplication(taskId, predecessorId, k);
                    System.out.println("    Start time if predecessor " + predecessorId + " is duplicated to VM" + k + ": " + st);
                    
                    // if st < minST then
                    if (st < minST) {
                        minPredecessor = predecessorId;
                        minST = st;
                        System.out.println("    New minimum start time: " + minST + " with predecessor " + minPredecessor);
                    }
                }
                
                // if minST < t.AST then
                if (minST < currentAST && minPredecessor != -1) {
                    System.out.println("  Duplication beneficial: minST (" + minST + ") < current AST (" + currentAST + ")");
                    System.out.println("  Duplicating and assigning task " + minPredecessor + " to VM " + k);
                    
                    // Duplicate and assign the task with minPredecessor to VM k
                    duplicateAndAssignTask(minPredecessor, k);
                    
                    // Update AST and AFT of each task in DAG
                    updateTaskTiming();
                    
                    System.out.println("  Task timing updated after duplication");
                } else {
                    System.out.println("  Duplication not beneficial: minST (" + minST + ") >= current AST (" + currentAST + ")");
                }
            } else {
                System.out.println("Task " + taskId + " is an entry task or has AST = 0, no duplication needed");
            }
        }
        
        System.out.println("\n=== LOTD ALGORITHM COMPLETED ===");
        return vmWaitingLists;
    }
    
    /**
     * Initialize task timing information from the current schedule
     */
    private void initializeTaskTiming() {
        System.out.println("Initializing task timing information...");
        
        // Use topological ordering to calculate timing correctly
        List<task> sortedTasks = topologicalSort();
        
        for (task t : sortedTasks) {
            int taskId = t.getID();
            
            // For entry tasks (no predecessors), AST = 0
            if (t.getPre().isEmpty()) {
                taskAST.put(taskId, 0.0);
                double execTime = calculateExecutionTime(taskId, getAssignedVM(taskId));
                taskAFT.put(taskId, execTime);
                System.out.println("  Entry task " + taskId + ": AST=0.0, AFT=" + execTime);
            } else {
                // For non-entry tasks, calculate based on predecessors
                double maxPredFinishTime = 0.0;
                for (int predId : t.getPre()) {
                    double predFinishTime = taskAFT.getOrDefault(predId, 0.0);
                    // Add communication cost if tasks are on different VMs
                    int predVM = getAssignedVM(predId);
                    int currentVM = getAssignedVM(taskId);
                    if (predVM != currentVM) {
                        predFinishTime += 1.0; // Simple communication cost
                    }
                    maxPredFinishTime = Math.max(maxPredFinishTime, predFinishTime);
                }
                
                taskAST.put(taskId, maxPredFinishTime);
                double execTime = calculateExecutionTime(taskId, getAssignedVM(taskId));
                taskAFT.put(taskId, maxPredFinishTime + execTime);
                System.out.println("  Task " + taskId + ": AST=" + maxPredFinishTime + ", AFT=" + (maxPredFinishTime + execTime));
            }
        }
        
        System.out.println("Task timing initialization completed");
    }
    
    /**
     * Perform topological sort of tasks to ensure correct dependency order
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
        
        Collections.reverse(result); // Reverse to get correct order
        return result;
    }
    
    /**
     * Utility method for topological sort
     */
    private void topologicalSortUtil(int taskId, Set<Integer> visited, Set<Integer> visiting, List<task> result) {
        if (visiting.contains(taskId)) {
            return; // Cycle detected, skip
        }
        if (visited.contains(taskId)) {
            return;
        }
        
        visiting.add(taskId);
        task currentTask = smgt.getTaskById(taskId);
        
        if (currentTask != null) {
            for (int succId : currentTask.getSucc()) {
                topologicalSortUtil(succId, visited, visiting, result);
            }
        }
        
        visiting.remove(taskId);
        visited.add(taskId);
        if (currentTask != null) {
            result.add(currentTask);
        }
    }
    
    /**
     * Check if a task is replicated on any VM
     */
    private boolean isTaskReplicated(int taskId) {
        for (Set<Integer> vmReplicated : replicatedTasks.values()) {
            if (vmReplicated.contains(taskId)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Calculate start time of a task after duplication of its predecessor
     */
    private double calculateStartTimeAfterDuplication(int taskId, int predecessorId, int vmId) {
        // Get the current AST of the predecessor
        double predecessorAST = taskAST.getOrDefault(predecessorId, 0.0);
        
        // Calculate execution time of predecessor on the target VM
        double predExecTimeOnTargetVM = calculateExecutionTime(predecessorId, vmId);
        
        // The predecessor would finish at: AST + ET on target VM
        double predecessorFinishOnTargetVM = predecessorAST + predExecTimeOnTargetVM;
        
        // Since the task and its duplicated predecessor are on the same VM,
        // there's no communication cost, so the task can start immediately
        // after the predecessor finishes
        double newStartTime = predecessorFinishOnTargetVM;
        
        System.out.println("      Predecessor " + predecessorId + " AST: " + predecessorAST);
        System.out.println("      Predecessor " + predecessorId + " ET on VM" + vmId + ": " + predExecTimeOnTargetVM);
        System.out.println("      Predecessor " + predecessorId + " would finish on VM" + vmId + " at: " + predecessorFinishOnTargetVM);
        System.out.println("      Task " + taskId + " could start at: " + newStartTime + " (no communication cost)");
        
        return newStartTime;
    }
    
    /**
     * Duplicate and assign a task to a specific VM
     */
    private void duplicateAndAssignTask(int taskId, int vmId) {
        System.out.println("    Duplicating task " + taskId + " to VM " + vmId);
        
        // Add task to the replicated tasks set for this VM
        replicatedTasks.get(vmId).add(taskId);
        
        // Add task to VM's waiting list if not already present
        List<Integer> waitingList = vmWaitingLists.get(vmId);
        if (!waitingList.contains(taskId)) {
            waitingList.add(taskId);
        }
        
        System.out.println("    Task " + taskId + " successfully duplicated to VM " + vmId);
    }
    
    /**
     * Update AST and AFT of each task in DAG after duplication
     */
    private void updateTaskTiming() {
        System.out.println("    Updating task timing information...");
        
        // Recalculate timing for all tasks considering the new duplications
        // This is a simplified update - in practice would use topological sorting
        // and consider all dependencies and VM assignments
        
        for (task t : tasks) {
            if (t.getPre().isEmpty()) {
                // Entry tasks remain the same
                continue;
            }
            
            // Recalculate based on potentially updated predecessor finish times
            double maxPredFinishTime = 0.0;
            for (int predId : t.getPre()) {
                double predFinishTime = taskAFT.getOrDefault(predId, 0.0);
                
                // Consider if predecessor is replicated on the same VM as current task
                int currentTaskVM = getAssignedVM(t.getID());
                if (replicatedTasks.get(currentTaskVM).contains(predId)) {
                    // No communication cost if replicated on same VM
                    predFinishTime = Math.min(predFinishTime, 
                        calculateExecutionTime(predId, currentTaskVM));
                }
                
                maxPredFinishTime = Math.max(maxPredFinishTime, predFinishTime);
            }
            
            // Update timing
            taskAST.put(t.getID(), maxPredFinishTime);
            taskAFT.put(t.getID(), maxPredFinishTime + calculateExecutionTime(t.getID(), getAssignedVM(t.getID())));
        }
        
        System.out.println("    Task timing update completed");
    }
    
    /**
     * Calculate execution time of a task on a specific VM
     */
    private double calculateExecutionTime(int taskId, int vmId) {
        task t = smgt.getTaskById(taskId);
        if (t == null || vmId >= vms.size() || vmId < 0) {
            return 1.0; // Default execution time
        }
        
        // Use actual task size and assume VM processing rate of 1.0
        // In a real implementation, this would consider VM capabilities
        double taskSize = t.getSize();
        double processingRate = 1.0; // Simplified processing rate
        
        return taskSize / processingRate;
    }
    
    /**
     * Get the VM ID where a task is currently assigned
     */
    private int getAssignedVM(int taskId) {
        // Find which VM has this task in its waiting list
        for (Map.Entry<Integer, List<Integer>> entry : vmWaitingLists.entrySet()) {
            if (entry.getValue().contains(taskId)) {
                return entry.getKey();
            }
        }
        return 0; // Default to VM 0 if not found
    }
    
    // Getter methods for accessing results
    public Map<Integer, Double> getTaskAST() {
        return new HashMap<>(taskAST);
    }
    
    public Map<Integer, Double> getTaskAFT() {
        return new HashMap<>(taskAFT);
    }
    
    public Map<Integer, Set<Integer>> getReplicatedTasks() {
        return new HashMap<>(replicatedTasks);
    }
    
    public Map<Integer, List<Integer>> getVMWaitingLists() {
        return new HashMap<>(vmWaitingLists);
    }
}
