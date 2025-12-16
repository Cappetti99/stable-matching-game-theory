
import java.util.*;

public class DCP {

    /**
     * Result class to hold complete DCP results including CP scheduling
     * utilizzimo delle classi annidate perchè DCP deve restituire tante info, sono
     * tutte qui dentro perchè esistono
     * in funzione dell'algoritmo DCP
     */

    
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
        // Calculate the rank of t_i by rank(t_i) = W_i + max_{t_j ∈ succ(t_i)}(c_{i,j}
        // + rank(t_j))
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
        // Calculate the rank of t_i by rank(t_i) = W_i + max_{t_j ∈ succ(t_i)}(c_{i,j}
        // + rank(t_j))
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
        throw new IllegalArgumentException(
            "Cannot compute Wi (average computation time): vmMapping is null or empty."
            );
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

    // Calculate rank iteratively following topological order (from exit to entry
    // tasks)
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

            // Get communication cost c_{i,j} (average communication time for edge (t_i,
            // t_j))
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
        public Set<Integer> getCriticalPath() {
            return criticalPath;
        }

        public Map<Integer, Double> getTaskRanks() {
            return taskRanks;
        }

        public Map<Integer, List<Integer>> getTaskLevels() {
            return taskLevels;
        }

        @Override
        public String toString() {
            return "DCPResult{" +
                    "criticalPath=" + criticalPath +
                    ", taskRanks=" + taskRanks +
                    ", taskLevels=" + taskLevels +
                    '}';
        }
    }

}    