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
     * Organizza i task in livelli topologici usando BFS
     * 
     * Algoritmo ottimizzato con complessità O(V+E) invece di O(V²):
     * - Usa taskMap per lookup O(1)
     * - Attraversa il grafo usando la lista dei successori (forward traversal)
     * 
     * @param tasks lista dei task
     * @return mappa livello -> lista di task ID
     */
    public static Map<Integer, List<Integer>> organizeTasksByLevels(List<task> tasks) {
        Map<Integer, List<Integer>> levelMap = new HashMap<>();
        
        // Prima passa: calcola i livelli usando un algoritmo topologico
        Map<Integer, Integer> taskToLevel = new HashMap<>();
        Map<Integer, task> taskMap = new HashMap<>();
        
        // Crea mappa per accesso rapido
        for (task t : tasks) {
            taskMap.put(t.getID(), t);
        }
        
        // Trova i task senza predecessori (entry tasks) - livello 0
        Queue<Integer> queue = new LinkedList<>();
        Map<Integer, Integer> inDegree = new HashMap<>();
        
        // Inizializza in-degree per tutti i task
        for (task t : tasks) {
            inDegree.put(t.getID(), t.getPre().size());
            if (t.getPre().isEmpty()) {
                queue.offer(t.getID());
                taskToLevel.put(t.getID(), 0);
            }
        }
        
        // BFS per calcolare i livelli
        while (!queue.isEmpty()) {
            int currentId = queue.poll();
            int currentLevel = taskToLevel.get(currentId);
            task current = taskMap.get(currentId);
            
            if (current == null) continue;
            
            // Processa tutti i successori
            for (int succId : current.getSucc()) {
                // Il livello del successore è almeno currentLevel + 1
                int newLevel = currentLevel + 1;
                taskToLevel.put(succId, Math.max(taskToLevel.getOrDefault(succId, 0), newLevel));
                
                // Decrementa in-degree
                inDegree.put(succId, inDegree.get(succId) - 1);
                
                // Se tutti i predecessori sono stati processati, aggiungi alla coda
                if (inDegree.get(succId) == 0) {
                    queue.offer(succId);
                }
            }
        }
        
        // Seconda passa: costruisci la mappa level -> [taskIds]
        for (Map.Entry<Integer, Integer> entry : taskToLevel.entrySet()) {
            int taskId = entry.getKey();
            int level = entry.getValue();
            
            levelMap.computeIfAbsent(level, k -> new ArrayList<>()).add(taskId);
        }
        
        return levelMap;
    }

}
