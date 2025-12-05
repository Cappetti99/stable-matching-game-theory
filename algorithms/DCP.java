    
import java.util.*;

public class DCP {
    
    /**
     * Result class to hold complete DCP results including CP scheduling
     */
    public static class DCPScheduleResult {
        public Set<Integer> criticalPath;
        public Map<Integer, Integer> cpTaskToVM;      // task ID -> VM ID assignment
        public Map<Integer, Double> cpTaskAST;        // task ID -> Actual Start Time
        public Map<Integer, Double> cpTaskAFT;        // task ID -> Actual Finish Time
        public Map<Integer, Double> taskRanks;        // all task ranks
        
        public DCPScheduleResult() {
            this.criticalPath = new HashSet<>();
            this.cpTaskToVM = new HashMap<>();
            this.cpTaskAST = new HashMap<>();
            this.cpTaskAFT = new HashMap<>();
            this.taskRanks = new HashMap<>();
        }
    }
    
    /**
     * Complete DCP algorithm following pseudocode lines 1-25
     * Identifies critical path AND schedules CP tasks to VMs with minimum finish time
     */
    public static DCPScheduleResult executeDCPWithScheduling(List<task> tasks, 
                                                              Map<Integer, List<Integer>> taskLevels,
                                                              task exitTask, 
                                                              Map<String, Double> communicationCosts,
                                                              Map<Integer, VM> vmMapping) {
        
        DCPScheduleResult result = new DCPScheduleResult();
        
        // Step 1: Initialize CP with an empty set
        Set<Integer> CP = new HashSet<>();
        
        // Step 2-10: Calculate ranks for all tasks
        Map<Integer, Double> taskRanks = new HashMap<>();
        Map<Integer, task> taskMap = new HashMap<>();
        
        // Create task lookup map
        for (task t : tasks) {
            taskMap.put(t.getID(), t);
        }
        
        // Calculate rank of exit task first (line 4-5)
        double exitRank = calculateTaskWeight(exitTask, vmMapping);
        taskRanks.put(exitTask.getID(), exitRank);
        
        // Calculate rank for all other tasks (lines 6-9)
        for (task t : tasks) {
            if (t.getID() != exitTask.getID()) {
                calculateRankIterative(t.getID(), taskMap, taskRanks, communicationCosts, vmMapping);
            }
        }
        
        // Step 12-16: Select task with max rank per level for CP
        // Sort levels to process in order
        List<Integer> sortedLevels = new ArrayList<>(taskLevels.keySet());
        Collections.sort(sortedLevels);
        
        for (Integer level : sortedLevels) {
            List<Integer> tasksInLevel = taskLevels.get(level);
            int maxRankTask = selectMaxRankTask(tasksInLevel, taskRanks);
            if (maxRankTask != -1) {
                CP.add(maxRankTask);
            }
        }
        
        // Step 19-23: Schedule CP tasks to VM with minimum finish time
        // Track VM availability (when each VM becomes free)
        Map<Integer, Double> vmAvailableTime = new HashMap<>();
        for (Integer vmId : vmMapping.keySet()) {
            vmAvailableTime.put(vmId, 0.0);
        }
        
        // Process CP tasks in topological order (by level)
        for (Integer level : sortedLevels) {
            for (Integer taskId : CP) {
                // Check if this task belongs to current level
                if (!taskLevels.get(level).contains(taskId)) {
                    continue;
                }
                
                task cpTask = taskMap.get(taskId);
                if (cpTask == null) continue;
                
                // Find VM with minimum finish time for this task (line 20)
                int bestVM = -1;
                double minFT = Double.POSITIVE_INFINITY;
                double bestST = 0.0;
                
                for (Map.Entry<Integer, VM> vmEntry : vmMapping.entrySet()) {
                    int vmId = vmEntry.getKey();
                    VM vm = vmEntry.getValue();
                    
                    // Calculate start time considering predecessors
                    double st = calculateCPTaskStartTime(cpTask, vmId, result.cpTaskAFT, 
                                                         result.cpTaskToVM, taskMap, 
                                                         communicationCosts, vmMapping);
                    
                    // ST must be at least when VM becomes available
                    st = Math.max(st, vmAvailableTime.get(vmId));
                    
                    // Calculate execution time: ET = size / capacity
                    double et = calculateTaskWeight(cpTask, vmMapping); // Average ET
                    double processingCapacity = vm.getCapability("processingCapacity");
                    if (processingCapacity > 0) {
                        et = cpTask.getSize() / processingCapacity;
                    }
                    
                    // Calculate finish time: FT = ST + ET
                    double ft = st + et;
                    
                    if (ft < minFT) {
                        minFT = ft;
                        bestVM = vmId;
                        bestST = st;
                    }
                }
                
                // Assign task to best VM (lines 21-22)
                if (bestVM != -1) {
                    result.cpTaskToVM.put(taskId, bestVM);
                    result.cpTaskAST.put(taskId, bestST);
                    result.cpTaskAFT.put(taskId, minFT);
                    vmAvailableTime.put(bestVM, minFT); // Update VM availability
                }
            }
        }
        
        result.criticalPath = CP;
        result.taskRanks = taskRanks;
        
        return result;
    }
    
    /**
     * Calculate start time for a CP task considering its predecessors in CP
     */
    private static double calculateCPTaskStartTime(task cpTask, int targetVMId,
                                                    Map<Integer, Double> cpTaskAFT,
                                                    Map<Integer, Integer> cpTaskToVM,
                                                    Map<Integer, task> taskMap,
                                                    Map<String, Double> communicationCosts,
                                                    Map<Integer, VM> vmMapping) {
        
        // Entry task starts at 0
        if (cpTask.getPre() == null || cpTask.getPre().isEmpty()) {
            return 0.0;
        }
        
        double maxPredFinishWithComm = 0.0;
        
        for (Integer predId : cpTask.getPre()) {
            Double predAFT = cpTaskAFT.get(predId);
            if (predAFT == null) {
                // Predecessor not yet scheduled (not in CP), assume it will be handled by SMGT
                continue;
            }
            
            Integer predVMId = cpTaskToVM.get(predId);
            if (predVMId == null) continue;
            
            // Calculate communication cost
            double commCost = 0.0;
            if (predVMId != targetVMId) {
                // Different VMs - add communication cost
                String commKey = predId + "_" + cpTask.getID();
                commCost = communicationCosts.getOrDefault(commKey, 0.0);
                
                // If no specific cost, calculate based on bandwidth
                if (commCost == 0.0) {
                    task predTask = taskMap.get(predId);
                    if (predTask != null) {
                        VM predVM = vmMapping.get(predVMId);
                        VM targetVM = vmMapping.get(targetVMId);
                        if (predVM != null && targetVM != null) {
                            double bandwidth = predVM.getBandwidthToVM(targetVMId);
                            if (bandwidth > 0) {
                                commCost = predTask.getSize() * 0.4 / bandwidth; // CCR default 0.4
                            }
                        }
                    }
                }
            }
            // Same VM: no communication cost
            
            double finishWithComm = predAFT + commCost;
            maxPredFinishWithComm = Math.max(maxPredFinishWithComm, finishWithComm);
        }
        
        return maxPredFinishWithComm;
    }
    
    // Original method kept for backward compatibility
    public static Set<Integer> executeDCP(List<task> tasks, Map<Integer, List<Integer>> taskLevels, 
                                         task exitTask, Map<String, Double> communicationCosts,
                                         Map<Integer, VM> vmMapping) {
        
        // Step 1: Initialize CP with an empty set
        Set<Integer> CP = new HashSet<>();
        
        // Step 2: Calculate the rank of t_exit
        Map<Integer, Double> taskRanks = new HashMap<>();
        Map<Integer, task> taskMap = new HashMap<>();
        
        // Create task lookup map
        for (task t : tasks) {
            taskMap.put(t.getID(), t);
        }
        
        // Calculate rank of exit task first
        double exitRank = calculateTaskWeight(exitTask, vmMapping); // W_exit (no successors)
        taskRanks.put(exitTask.getID(), exitRank);
        
        // Step 3: For each task t_i in the DAG except the exit task do
        // Calculate the rank of t_i by rank(t_i) = W_i + max_{t_j ∈ succ(t_i)}(c_{i,j} + rank(t_j))
        for (task t : tasks) {
            if (t.getID() != exitTask.getID()) {
                calculateRankIterative(t.getID(), taskMap, taskRanks, communicationCosts, vmMapping);
            }
        }
        
        // Step 4: For each level l in the DAG do
        // Select the task with the maximum rank in level l and add the task into CP
        for (Map.Entry<Integer, List<Integer>> levelEntry : taskLevels.entrySet()) {
            List<Integer> tasksInLevel = levelEntry.getValue();
            
            // Find task with maximum rank in this level
            int maxRankTask = selectMaxRankTask(tasksInLevel, taskRanks);
            if (maxRankTask != -1) {
                CP.add(maxRankTask);
            }
        }
        
        return CP;
    }
    
    // Calculate rank for all tasks following the pseudocode approach
    public static Map<Integer, Double> calculateTaskRanks(List<task> tasks, task exitTask, 
                                                         Map<String, Double> communicationCosts,
                                                         Map<Integer, VM> vmMapping) {
        
        Map<Integer, Double> ranks = new HashMap<>();
        Map<Integer, task> taskMap = new HashMap<>();
        
        // Create task lookup map
        for (task t : tasks) {
            taskMap.put(t.getID(), t);
        }
        
        // Step 1: Calculate the rank of t_exit
        double exitRank = calculateTaskWeight(exitTask, vmMapping); // W_exit (no successors)
        ranks.put(exitTask.getID(), exitRank);
        
        // Step 2: For each task t_i in the DAG except the exit task do
        // Calculate the rank of t_i by rank(t_i) = W_i + max_{t_j ∈ succ(t_i)}(c_{i,j} + rank(t_j))
        for (task t : tasks) {
            if (t.getID() != exitTask.getID()) {
                calculateRankIterative(t.getID(), taskMap, ranks, communicationCosts, vmMapping);
            }
        }
        
        return ranks;
    }
    
    // Calculate task weight W_i (average computation time for t_i across all VMs)
    private static double calculateTaskWeight(task t, Map<Integer, VM> vmMapping) {
        if (vmMapping == null || vmMapping.isEmpty()) {
            // If no VM mapping, use task size as computation time
            return t.getSize();
        }
        
        double totalComputationTime = 0.0;
        int vmCount = 0;
        
        // Calculate average computation time across all VMs
        // W_i = average of (task_size / processing_capacity) for all VMs
        for (VM vm : vmMapping.values()) {
            double processingCapacity = vm.getCapability("processingCapacity");
            if (processingCapacity > 0) {
                double computationTime = t.getSize() / processingCapacity;
                totalComputationTime += computationTime;
                vmCount++;
            }
        }
        
        // Return average computation time across all VMs
        return vmCount > 0 ? totalComputationTime / vmCount : t.getSize();
    }
    
    // Select task with maximum rank in a given level
    private static int selectMaxRankTask(List<Integer> tasksInLevel, Map<Integer, Double> taskRanks) {
        if (tasksInLevel == null || tasksInLevel.isEmpty()) {
            return -1;
        }
        
        int maxRankTask = -1;
        double maxRank = Double.NEGATIVE_INFINITY;
        
        for (int taskId : tasksInLevel) {
            double rank = taskRanks.getOrDefault(taskId, 0.0);
            if (rank > maxRank) {
                maxRank = rank;
                maxRankTask = taskId;
            }
        }
        
        return maxRankTask;
    }
    
    // Utility method to organize tasks by levels using BFS (topological levels)
    public static Map<Integer, List<Integer>> organizeTasksByLevels(List<task> tasks) {
        Map<Integer, List<Integer>> levels = new HashMap<>();
        Map<Integer, Integer> taskLevels = new HashMap<>();
        Map<Integer, Set<Integer>> taskPredecessors = new HashMap<>();
        Map<Integer, Integer> inDegree = new HashMap<>();
        
        // Initialize data structures
        for (task t : tasks) {
            int taskId = t.getID();
            taskPredecessors.put(taskId, new HashSet<>());
            inDegree.put(taskId, 0);
        }
        
        // Build predecessor relationships and calculate in-degrees
        for (task t : tasks) {
            List<Integer> predecessors = t.getPre();
            if (predecessors != null) {
                for (int predId : predecessors) {
                    taskPredecessors.get(t.getID()).add(predId);
                    inDegree.put(t.getID(), inDegree.get(t.getID()) + 1);
                }
            }
        }
        
        // BFS approach for level calculation
        Queue<Integer> queue = new LinkedList<>();
        
        // Start with entry tasks (tasks with no predecessors)
        for (task t : tasks) {
            if (inDegree.get(t.getID()) == 0) {
                queue.offer(t.getID());
                taskLevels.put(t.getID(), 0);
            }
        }
        
        // Process tasks level by level using BFS
        while (!queue.isEmpty()) {
            int currentTask = queue.poll();
            int currentLevel = taskLevels.get(currentTask);
            
            // Find all tasks that have this task as predecessor
            for (task t : tasks) {
                if (taskPredecessors.get(t.getID()).contains(currentTask)) {
                    // Decrease in-degree
                    inDegree.put(t.getID(), inDegree.get(t.getID()) - 1);
                    
                    // Update level (maximum of all predecessor levels + 1)
                    int newLevel = currentLevel + 1;
                    if (!taskLevels.containsKey(t.getID()) || taskLevels.get(t.getID()) < newLevel) {
                        taskLevels.put(t.getID(), newLevel);
                    }
                    
                    // If all predecessors processed, add to queue
                    if (inDegree.get(t.getID()) == 0) {
                        queue.offer(t.getID());
                    }
                }
            }
        }
        
        // Group tasks by their levels
        for (Map.Entry<Integer, Integer> entry : taskLevels.entrySet()) {
            int taskId = entry.getKey();
            int level = entry.getValue();
            
            if (!levels.containsKey(level)) {
                levels.put(level, new ArrayList<>());
            }
            levels.get(level).add(taskId);
        }
        
        return levels;
    }
    
    // Result class to hold DCP algorithm results
    public static class DCPResult {
        private Set<Integer> criticalPath;
        private Map<Integer, Double> taskRanks;
        private Map<Integer, List<Integer>> taskLevels;
        
        public DCPResult(Set<Integer> criticalPath, Map<Integer, Double> taskRanks, 
                        Map<Integer, List<Integer>> taskLevels) {
            this.criticalPath = criticalPath;
            this.taskRanks = taskRanks;
            this.taskLevels = taskLevels;
        }
        
        // Getters
        public Set<Integer> getCriticalPath() { return criticalPath; }
        public Map<Integer, Double> getTaskRanks() { return taskRanks; }
        public Map<Integer, List<Integer>> getTaskLevels() { return taskLevels; }
        
        @Override
        public String toString() {
            return "DCPResult{" +
                    "criticalPath=" + criticalPath +
                    ", taskRanks=" + taskRanks +
                    ", taskLevels=" + taskLevels +
                    '}';
        }
    }
    
    // Calculate rank iteratively following topological order (from exit to entry tasks)
    private static void calculateRankIterative(int taskId, Map<Integer, task> taskMap, 
                                              Map<Integer, Double> taskRanks,
                                              Map<String, Double> communicationCosts,
                                              Map<Integer, VM> vmMapping) {
        
        // If already calculated, return
        if (taskRanks.containsKey(taskId)) {
            return;
        }
        
        task currentTask = taskMap.get(taskId);
        if (currentTask == null) {
            taskRanks.put(taskId, 0.0);
            return;
        }
        
        // Get task weight W_i
        double Wi = calculateTaskWeight(currentTask, vmMapping);
        
        // Get successors
        List<Integer> successors = currentTask.getSucc();
        
        // If no successors, rank = W_i (this should only be exit task)
        if (successors == null || successors.isEmpty()) {
            taskRanks.put(taskId, Wi);
            return;
        }
        
        // Calculate max_{t_j ∈ succ(t_i)}(c_{i,j} + rank(t_j))
        double maxSuccessorValue = 0.0;
        
        for (int successorId : successors) {
            // Ensure successor rank is calculated first (recursive dependency)
            if (!taskRanks.containsKey(successorId)) {
                calculateRankIterative(successorId, taskMap, taskRanks, communicationCosts, vmMapping);
            }
            
            // Get communication cost c_{i,j} (average communication time for edge (t_i, t_j))
            String commKey = taskId + "_" + successorId;
            double communicationCost = communicationCosts.getOrDefault(commKey, 0.0);
            
            // Get rank of successor
            double successorRank = taskRanks.getOrDefault(successorId, 0.0);
            
            double totalValue = communicationCost + successorRank;
            maxSuccessorValue = Math.max(maxSuccessorValue, totalValue);
        }
        
        // rank(t_i) = W_i + max_{t_j ∈ succ(t_i)}(c_{i,j} + rank(t_j))
        double rank = Wi + maxSuccessorValue;
        taskRanks.put(taskId, rank);
    }
}
