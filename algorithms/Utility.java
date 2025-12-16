import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

public class Utility {
      /**
     * Trova il task di uscita (senza successori)
     */
    private task findExitTask(List<task> tasks) {
        // Cerca task senza successori
        for (task t : tasks) {
            if (t.getSucc().isEmpty()) {
                return t;
            }
        }
        return tasks.get(tasks.size() - 1); // Fallback: ultimo task
    }

    // CALCOLA LEVEL TASK 

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


    // CALCOLA ENTRY E EXIT TASKS

    // CALCOLA SOMMA DELLE DIMENSIONI DEI TASK

    // CALCOLA SOMMA DELLE CAPACITA' DELLE VM

    


}
