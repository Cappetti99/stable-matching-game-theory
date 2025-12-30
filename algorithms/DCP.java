import java.util.*;

/**
 * DCP: Dynamic Critical Path Algorithm
 * 
 * Algoritmo ricorsivo per calcolare i rank dei task e identificare il Critical
 * Path.
 * 
 * Funzionamento:
 * 1. Calcola ricorsivamente i rank di tutti i task (bottom-up dall'exit task)
 * 2. Per ogni livello del DAG, seleziona il task con rank massimo
 * 3. Restituisce il Critical Path come insieme di task ID
 * 
 * Formula del rank (ricorsiva):
 * - rank(t_exit) = W_exit (peso computazionale dell'exit task)
 * - rank(t_i) = W_i + max_{t_j ∈ succ(t_i)}(c_{i,j} + rank(t_j))
 * 
 * Dove:
 * - W_i = average computation time del task su tutte le VM
 * - c_{i,j} = costo di comunicazione tra task i e j
 * 
 * Communication cost formula:
 * c_{i,j} = (1 / m(m-1)) × Σ(k=0 to m-1) Σ(l=0,l≠k to m-1) [TT_{i,j} / B(VM_k, VM_l)]
 * 
 * Where:
 * - TT_{i,j} = st_i × CCR (data transfer size from task i to j)
 * - m = number of VMs
 * - B(VM_k, VM_l) = bandwidth from VM_k to VM_l
 * - Sum averages over all possible VM pairs (k ≠ l)
 * 
 */
public class DCP {

    /**
     * METODO PRINCIPALE: Esegue l'algoritmo DCP completo
     * 
     * @param tasks              Lista completa dei task del DAG
     * @param taskLevels         Mappa livello -> lista di task ID
     * @param communicationCosts Costi di comunicazione tra task (key:
     *                           "taskId_succId")
     * @param vmMapping          Mappa ID VM -> oggetto VM
     * @return Set contenente i task ID del Critical Path
     */
    public static Set<Integer> executeDCP(
            List<task> tasks,
            Map<Integer, List<Integer>> taskLevels,
            Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {

        System.out.println("   🔍 DCP: Starting recursive rank calculation...");

        // STEP 1: Crea mappa task lookup per accesso rapido
        Map<Integer, task> taskMap = new HashMap<>();
        for (task t : tasks) {
            taskMap.put(t.getID(), t);
        }

        // STEP 2: Calcola ricorsivamente i rank di tutti i task
        Map<Integer, Double> taskRanks = calculateTaskRanksRecursive(
                taskMap,
                communicationCosts,
                vmMapping);

        System.out.println("   ✓ Ranks calculated for " + taskRanks.size() + " tasks");

        // Debug: mostra alcuni rank
        if (taskRanks.size() <= 10) {
            System.out.println("   📊 Task ranks:");
            taskRanks.entrySet().stream()
                    .sorted(Map.Entry.<Integer, Double>comparingByValue().reversed())
                    .forEach(e -> System.out.printf("      t%d: %.3f%n", e.getKey(), e.getValue()));
        }

        // STEP 3: Per ogni livello, seleziona il task con rank massimo
        Set<Integer> criticalPath = buildCriticalPath(taskLevels, taskRanks);

        System.out.println("   ✓ Critical Path constructed: " + criticalPath.size() + " tasks");

        return criticalPath;
    }

    /**
     * STEP 2: Calcola ricorsivamente i rank di tutti i task
     * 
     * Usa memoization per evitare ricalcoli:
     * - Se il rank è già stato calcolato, lo ritorna subito
     * - Altrimenti, calcola ricorsivamente partendo dai successori
     * 
     * @param taskMap            Mappa ID -> task object
     * @param communicationCosts Costi di comunicazione
     * @param vmMapping          Mappa delle VM
     * @return Mappa taskId -> rank
     */
    private static Map<Integer, Double> calculateTaskRanksRecursive(
            Map<Integer, task> taskMap,
            Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {

        Map<Integer, Double> ranks = new HashMap<>();

        for (task t : taskMap.values()) {
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
     * Funzione ricorsiva per calcolare il rank di un singolo task
     * 
     * Formula: rank(t_i) = W_i + max_{t_j ∈ succ(t_i)}(c_{i,j} + rank(t_j))
     * 
     * @param taskId             ID del task da calcolare
     * @param taskMap            Mappa completa dei task
     * @param ranks              Mappa dei rank (memoization)
     * @param communicationCosts Costi di comunicazione
     * @param vmMapping          Mappa delle VM
     */
    private static void calculateRankRecursive(
            int taskId,
            Map<Integer, task> taskMap,
            Map<Integer, Double> ranks,
            Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {

        // MEMOIZATION: Se già calcolato, ritorna subito
        if (ranks.containsKey(taskId)) {
            return;
        }

        task currentTask = taskMap.get(taskId);
        if (currentTask == null) {
            ranks.put(taskId, 0.0);
            return;
        }

        // Calcola peso computazionale W_i
        double Wi = calculateTaskWeight(currentTask, vmMapping);

        // Ottieni lista successori
        List<Integer> successors = currentTask.getSucc();

        // CASO BASE: Nessun successore (exit tasks)
        if (successors == null || successors.isEmpty()) {
            ranks.put(taskId, Wi);
            return;
        }

        double maxSuccessorValue = 0.0;

        for (int successorId : successors) {
            // Ricorsione
            if (!ranks.containsKey(successorId)) {
                calculateRankRecursive(
                        successorId,
                        taskMap,
                        ranks,
                        communicationCosts,
                        vmMapping);
            }

            // Ottieni costo comunicazione c_{i,j}
            String commKey = taskId + "_" + successorId;
            double communicationCost = communicationCosts.getOrDefault(commKey, 0.0);

            // Ottieni rank del successore
            double successorRank = ranks.getOrDefault(successorId, 0.0);

            // Calcola c_{i,j} + rank(t_j)
            double totalValue = communicationCost + successorRank;
            maxSuccessorValue = Math.max(maxSuccessorValue, totalValue);
        }

        // FORMULA FINALE: rank(t_i) = W_i + max_{successor}(...)
        double rank = Wi + maxSuccessorValue;
        ranks.put(taskId, rank);
    }

    /**
     * STEP 3: Costruisce il Critical Path selezionando il task con rank massimo
     * per ogni livello del DAG
     * 
     * @param taskLevels Mappa livello -> lista di task ID
     * @param taskRanks  Mappa taskId -> rank
     * @return Set contenente i task ID del Critical Path
     */
    private static Set<Integer> buildCriticalPath(
            Map<Integer, List<Integer>> taskLevels,
            Map<Integer, Double> taskRanks) {

        Set<Integer> criticalPath = new HashSet<>();

        // Ordina i livelli (0, 1, 2, ...)
        List<Integer> sortedLevels = new ArrayList<>(taskLevels.keySet());
        Collections.sort(sortedLevels);

        System.out.println("   🔍 Building Critical Path from " + sortedLevels.size() + " levels...");

        // Per ogni livello, seleziona il task con rank massimo
        for (Integer level : sortedLevels) {
            List<Integer> tasksInLevel = taskLevels.get(level);

            if (tasksInLevel == null || tasksInLevel.isEmpty()) {
                continue;
            }

            // Trova task con rank massimo in questo livello
            int maxRankTask = -1;
            double maxRank = Double.NEGATIVE_INFINITY;

            for (int taskId : tasksInLevel) {
                double rank = taskRanks.getOrDefault(taskId, 0.0);
                if (rank > maxRank) {
                    maxRank = rank;
                    maxRankTask = taskId;
                }
            }

            // Aggiungi al Critical Path
            if (maxRankTask != -1) {
                criticalPath.add(maxRankTask);
            }
        }

        return criticalPath;
    }

    /**
     * Calcola il peso computazionale W_i di un task usando Metrics.ET
     * 
     * W_i = average(execution_time) su tutte le VM
     * 
     * @param t         Task da calcolare
     * @param vmMapping Mappa delle VM
     * @return Peso computazionale medio
     */
    private static double calculateTaskWeight(task t, Map<Integer, VM> vmMapping) {

        if (vmMapping == null || vmMapping.isEmpty()) {
            throw new IllegalArgumentException(
                    "Cannot compute task weight: vmMapping is null or empty");
        }

        double totalComputationTime = 0.0;
        int vmCount = 0;

        // Calcola tempo medio di esecuzione su tutte le VM usando Metrics.ET
        for (VM vm : vmMapping.values()) {
            double computationTime = Metrics.ET(t, vm, "processingCapacity");
            
            if (computationTime != Double.POSITIVE_INFINITY) {
                totalComputationTime += computationTime;
                vmCount++;
            }
        }

        // Ritorna la media (o size del task se non ci sono VM valide)
        return vmCount > 0 ? totalComputationTime / vmCount : t.getSize();
    }

}