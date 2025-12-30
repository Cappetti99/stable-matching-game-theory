import java.util.*;

/**
 * Utility class providing common operations on tasks and virtual machines.
 */
public class Utility {

    /**
     * Finds all exit tasks (tasks with no successors).
     * <p>
     * A workflow may contain multiple exit tasks.
     *
     * @param tasks the list of tasks to analyze
     * @return a list of exit tasks
     * @throws IllegalArgumentException if the task list is null or empty
     * @throws NoSuchElementException if no exit task is found
     */

    public static List<task> findExitTasks(List<task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks must not be null or empty");
        }

        List<task> exits = new ArrayList<>();
        for (task t : tasks) {
            if (t != null && (t.getSucc() == null || t.getSucc().isEmpty())) {
                exits.add(t);
            }
        }

        if (exits.isEmpty()) {
            throw new NoSuchElementException("No exit task found (no task with empty successors list).");
        }

        return exits;
    }

    /**
     * Organizes tasks into topological levels using BFS.
     *
     * Optimized algorithm with O(V + E) complexity instead of O(V²):
     * - Uses taskMap for O(1) lookup
     * - Traverses the graph using the successors list (forward traversal)
     *
     * @param tasks list of tasks
     * @return a map level -> list of task IDs
     */

    public static Map<Integer, List<Integer>> organizeTasksByLevels(List<task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new IllegalArgumentException("tasks must not be null or empty");
        }

        Map<Integer, List<Integer>> levelMap = new HashMap<>();

        // First pass: compute levels using a topological algorithm
        Map<Integer, Integer> taskToLevel = new HashMap<>();
        Map<Integer, task> taskMap = new HashMap<>();

        // Build a map for fast task lookup
        for (task t : tasks) {
            if (t == null) {
                continue;
            }
            taskMap.put(t.getID(), t);
        }

        // Find tasks with no predecessors (entry tasks) - level 0
        Queue<Integer> queue = new LinkedList<>();
        Map<Integer, Integer> inDegree = new HashMap<>();

        // Initialize in-degree for all tasks (null-safe access)
        for (task t : tasks) {
            if (t == null) {
                continue;
            }
            List<Integer> predecessors =
                t.getPre() == null ? Collections.emptyList() : t.getPre();

            inDegree.put(t.getID(), predecessors.size());

            if (predecessors.isEmpty()) {
                queue.offer(t.getID());
                taskToLevel.put(t.getID(), 0);
            }
        }

        // BFS to compute task levels
        while (!queue.isEmpty()) {
            int currentId = queue.poll();
            int currentLevel = taskToLevel.get(currentId);
            task current = taskMap.get(currentId);

            if (current == null) {
                continue;
            }

            List<Integer> successors =
                current.getSucc() == null ? Collections.emptyList() : current.getSucc();

            // Process all successors
            for (int succId : successors) {
                // The successor level must be at least currentLevel + 1
                int newLevel = currentLevel + 1;
                taskToLevel.put(
                    succId,
                    Math.max(taskToLevel.getOrDefault(succId, 0), newLevel)
                );

                // Decrease in-degree
                inDegree.put(succId, inDegree.getOrDefault(succId, 0) - 1);

                // If all predecessors have been processed, add to the queue
                if (inDegree.get(succId) == 0) {
                    queue.offer(succId);
                }
            }
        }

        // Second pass: build the map level -> [taskIds]
        for (Map.Entry<Integer, Integer> entry : taskToLevel.entrySet()) {
            int taskId = entry.getKey();
            int level = entry.getValue();

            levelMap
                .computeIfAbsent(level, k -> new ArrayList<>())
                .add(taskId);
        }

        return levelMap;
    }

}