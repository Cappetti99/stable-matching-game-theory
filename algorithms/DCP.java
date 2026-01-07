import java.util.*;

/**
 * DCP: Dynamic Critical Path Algorithm.
 *
 * Recursive algorithm used to compute Task ranks and identify
 * the Critical Path of a workflow DAG.
 *
 * Overview:
 * 1. Recursively computes the rank of each Task (bottom-up from exit tasks).
 * 2. For each DAG level, selects the Task with the highest rank.
 * 3. Returns the Critical Path as a set of Task IDs.
 *
 * Rank definition (recursive):
 * - rank(t_exit) = W_exit
 * - rank(t_i) = W_i + max_{t_j ∈ succ(t_i)} ( c_{i,j} + rank(t_j) )
 *
 * Where:
 * - W_i is the average computation time of Task i across all VMs
 * - c_{i,j} is the average communication cost between Task i and j
 *
 * Communication cost:
 * c_{i,j} = (1 / (m · (m − 1))) × Σ_{k=0..m−1} Σ_{l=0..m−1, l≠k}
 *           [ TT_{i,j} / B(VM_k, VM_l) ]
 *
 * With:
 * - TT_{i,j} = st_i × CCR (data transferred from Task i to Task j)
 * - m = number of VMs
 * - B(VM_k, VM_l) = bandwidth between VM_k and VM_l
 *
 * The communication cost is averaged over all possible VM pairs.
 */
public class DCP {

    /**
     * Main entry point for the DCP algorithm.
     *
     * Executes the full Dynamic Critical Path computation:
     * - Computes Task ranks using recursive memoization
     * - Builds the Critical Path by selecting the highest-rank Task at each level
     *
     * @param tasks              Complete list of DAG tasks
     * @param taskLevels         Map level -> list of Task IDs
     * @param communicationCosts Precomputed communication costs (key: "taskId_succId")
     * @param vmMapping          Map VM ID -> VM object
     * @return Set containing the Task IDs belonging to the Critical Path
     */
    public static Set<Integer> executeDCP(
            List<Task> tasks,
            Map<Integer, List<Integer>> taskLevels,
            Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {

        System.out.println("   🔍 DCP: Starting recursive rank computation...");

        // STEP 1: Build a Task lookup map for O(1) access
        Map<Integer, Task> taskMap = new HashMap<>();
        for (Task t : tasks) {
            taskMap.put(t.getID(), t);
        }

        // STEP 2: Recursively compute ranks for all tasks
        Map<Integer, Double> taskRanks = calculateTaskRanksRecursive(
                taskMap,
                communicationCosts,
                vmMapping);

        System.out.println("   ✓ Ranks computed for " + taskRanks.size() + " tasks");

        // STEP 3: Build the Critical Path by selecting the highest-rank Task at each level
        Set<Integer> criticalPath = buildCriticalPath(taskLevels, taskRanks);

        System.out.println("   ✓ Critical Path constructed (" + criticalPath.size() + " tasks)");

        return criticalPath;
    }

    /**
     * STEP 2: Recursively computes the rank of all tasks.
     *
     * Uses memoization to avoid redundant computations:
     * - If a Task rank has already been computed, it is returned immediately
     * - Otherwise, the rank is computed recursively starting from successor tasks
     *
     * @param taskMap            Map taskId -> Task object
     * @param communicationCosts Communication cost map
     * @param vmMapping          VM mapping used to compute average execution times
     * @return Map taskId -> computed rank value
     */
    private static Map<Integer, Double> calculateTaskRanksRecursive(
            Map<Integer, Task> taskMap,
            Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {

        Map<Integer, Double> ranks = new HashMap<>();

        // Trigger recursive rank computation for each Task
        for (Task t : taskMap.values()) {
            calculateRankRecursive(
                    t.getID(),
                    taskMap,
                    ranks,
                    communicationCosts,
                    vmMapping);
        }

        return ranks;
    }

    /**
     * Recursive function used to compute the rank of a single Task.
     *
     * Rank definition:
     * rank(t_i) = W_i + max_{t_j ∈ succ(t_i)} ( c_{i,j} + rank(t_j) )
     *
     * The computation proceeds bottom-up, starting from exit tasks.
     * Memoization is used to avoid recomputing ranks for already visited tasks.
     *
     * @param taskId             ID of the Task whose rank is being computed
     * @param taskMap            Complete mapping of taskId -> Task object
     * @param ranks              Memoization map storing already computed ranks
     * @param communicationCosts Precomputed communication costs
     * @param vmMapping          VM mapping used to compute execution times
     */
    private static void calculateRankRecursive(
            int taskId,
            Map<Integer, Task> taskMap,
            Map<Integer, Double> ranks,
            Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {

        // MEMOIZATION: return immediately if the rank is already computed
        if (ranks.containsKey(taskId)) {
            return;
        }

        Task currentTask = taskMap.get(taskId);
        if (currentTask == null) {
            System.err.println("Warning: Task with ID " + taskId + " is not defined. Assigning rank 0.0.");
            ranks.put(taskId, 0.0);
            return;
        }

        // Compute computational weight W_i (average execution time)
        double Wi = calculateTaskWeight(currentTask, vmMapping);

        // Retrieve successors of the current Task
        List<Integer> successors = currentTask.getSucc();

        // exit Task (no successors)
        if (successors == null || successors.isEmpty()) {
            ranks.put(taskId, Wi);
            return;
        }

        double maxSuccessorValue = 0.0;

        // Recursive evaluation of successor contributions
        for (int successorId : successors) {

            // Ensure successor rank is computed
            if (!ranks.containsKey(successorId)) {
                calculateRankRecursive(
                        successorId,
                        taskMap,
                        ranks,
                        communicationCosts,
                        vmMapping);
            }

            // Communication cost c_{i,j}
            String commKey = taskId + "_" + successorId;
            double communicationCost = communicationCosts.getOrDefault(commKey, 0.0);

            // Rank of successor Task
            double successorRank = ranks.getOrDefault(successorId, 0.0);

            // Contribution of this successor: c_{i,j} + rank(t_j)
            double totalValue = communicationCost + successorRank;
            maxSuccessorValue = Math.max(maxSuccessorValue, totalValue);
        }

        // FINAL FORMULA: rank(t_i) = W_i + max_{successor}(c_{i,j} + rank(t_j))
        double rank = Wi + maxSuccessorValue;
        ranks.put(taskId, rank);
    }

    /**
     * STEP 3: Builds the Critical Path by selecting, for each DAG level,
     * the Task with the maximum rank.
     *
     * This approach ensures that the resulting Critical Path:
     * - respects DAG topological ordering
     * - captures the most time-critical Task at each level
     *
     * @param taskLevels Map level -> list of Task IDs
     * @param taskRanks  Map taskId -> computed rank
     * @return Set containing the Task IDs belonging to the Critical Path
     */
    private static Set<Integer> buildCriticalPath(
            Map<Integer, List<Integer>> taskLevels,
            Map<Integer, Double> taskRanks) {

        Set<Integer> criticalPath = new HashSet<>();

        // Sort levels to preserve DAG order (0, 1, 2, ...)
        List<Integer> sortedLevels = new ArrayList<>(taskLevels.keySet());
        Collections.sort(sortedLevels);

        System.out.println("   🔍 Building Critical Path from " + sortedLevels.size() + " levels...");

        // Select the highest-rank Task at each level
        for (Integer level : sortedLevels) {
            List<Integer> tasksInLevel = taskLevels.get(level);

            if (tasksInLevel == null || tasksInLevel.isEmpty()) {
                continue;
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

            // Add selected Task to the Critical Path
            if (maxRankTask != -1) {
                criticalPath.add(maxRankTask);
            }
        }

        return criticalPath;
    }

    /**
     * Computes the computational weight W_i of a Task.
     *
     * W_i is defined as the average execution time of the Task
     * across all available virtual machines:
     *
     * W_i = avg(ET(t_i, VM_k))  ∀ VM_k
     *
     * @param t         Task whose weight is computed
     * @param vmMapping Map of available VMs
     * @return Average computational weight of the Task
     */
    private static double calculateTaskWeight(Task t, Map<Integer, VM> vmMapping) {

        if (vmMapping == null || vmMapping.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot compute Task weight: vmMapping is null or empty");
        }

        double totalComputationTime = 0.0;
        int vmCount = 0;

        // Compute average execution time using Metrics.ET
        for (VM vm : vmMapping.values()) {
            double computationTime = Metrics.ET(t, vm);

            if (computationTime != Double.POSITIVE_INFINITY) {
                totalComputationTime += computationTime;
                vmCount++;
            }
        }

        // Fallback: use Task size if no valid VM execution time is available
        return vmCount > 0 ? totalComputationTime / vmCount : t.getSize();
    }

}