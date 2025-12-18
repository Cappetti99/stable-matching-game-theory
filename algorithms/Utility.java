import java.util.*;

/**
 * Utility class per operazioni comuni su task e VM
 */
public class Utility {

    /** HA SENSO METTERLI?
     * Trova tutti i task di uscita (senza successori).
     * Sono ammessi più exit task.
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
     * Trova tutti i task di ingresso (senza predecessori)
     */
    public static List<task> findEntryTasks(List<task> tasks) {
        List<task> entryTasks = new ArrayList<>();
        for (task t : tasks) {
            if (t.getPre() == null || t.getPre().isEmpty()) {
                entryTasks.add(t);
            }
        }
        return entryTasks;
    }

    /**
     * Organizza i task in livelli topologici usando BFS
     * 
     * @param tasks lista dei task
     * @return mappa livello -> lista di task ID
     */
    public static Map<Integer, List<Integer>> organizeTasksByLevels(List<task> tasks) {
        Map<Integer, List<Integer>> levels = new HashMap<>();
        Map<Integer, Integer> taskLevels = new HashMap<>();
        Map<Integer, Integer> inDegree = new HashMap<>();
        Map<Integer, Set<Integer>> successors = new HashMap<>();

        // Inizializza in-degree e successori
        for (task t : tasks) {
            int id = t.getID();
            inDegree.put(id, t.getPre() == null ? 0 : t.getPre().size());
            successors.put(id, new HashSet<>(t.getSucc() != null ? t.getSucc() : new ArrayList<>()));
        }

        // Queue per BFS
        Queue<Integer> queue = new LinkedList<>();
        for (task t : tasks) {
            if (inDegree.get(t.getID()) == 0) {
                queue.offer(t.getID());
                taskLevels.put(t.getID(), 0);
            }
        }

        while (!queue.isEmpty()) {
            int current = queue.poll();
            int currentLevel = taskLevels.get(current);

            for (task t : tasks) {
                if (t.getPre() != null && t.getPre().contains(current)) {
                    int id = t.getID();
                    inDegree.put(id, inDegree.get(id) - 1);
                    int newLevel = currentLevel + 1;
                    taskLevels.put(id, Math.max(taskLevels.getOrDefault(id, 0), newLevel));

                    if (inDegree.get(id) == 0) {
                        queue.offer(id);
                    }
                }
            }
        }

        // Raggruppa task per livello
        for (Map.Entry<Integer, Integer> entry : taskLevels.entrySet()) {
            int taskId = entry.getKey();
            int level = entry.getValue();
            levels.computeIfAbsent(level, k -> new ArrayList<>()).add(taskId);
        }

        return levels;
    }

    /**
     * Somma delle dimensioni di una lista di task
     * 
     * @param tasks lista dei task
     * @return somma delle dimensioni
     */
    public static double sumTaskSizes(List<task> tasks) {
        double sum = 0.0;
        for (task t : tasks) {
            sum += t.getSize();
        }
        return sum;
    }

    /**
     * Somma della capacità di una lista di VM
     * 
     * @param vms lista delle VM
     * @return somma delle capacità
     */
    public static double sumVMCapacities(List<VM> vms) {
        double sum = 0.0;
        for (VM vm : vms) {
            Map<String, Double> caps = vm.getProcessingCapabilities();
            if (!caps.isEmpty()) {
                sum += caps.values().iterator().next(); // prende la prima capacità disponibile
            } else {
                sum += 1.0; // default capacity
            }
        }
        return sum;
    }

}
