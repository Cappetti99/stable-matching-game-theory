import java.util.*;
import java.io.*;

public class SMGT {
    private List<VM> vms;
    private List<task> tasks;
    private Map<Integer, Integer> taskLevels; // Maps task ID to its level in the DAG
    private Map<Integer, List<Integer>> levelTasks; // Maps level to list of task IDs at that level

    /**
     * Classe per gestire threshold dinamico
     */
    private static class VMThreshold {
        int remaining;
        List<Integer> waitingList;

        VMThreshold(int initial) {
            this.remaining = initial;
            this.waitingList = new ArrayList<>();
        }
    }

    public SMGT() {
        this.vms = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.taskLevels = new HashMap<>();
        this.levelTasks = new HashMap<>();
    }

    /**
     * Calcola il threshold cumulativo come nel paper.
     *
     * Semantica del parametro `level`:
     * - `level` è il livello $l$ del paper (1-indexed).
     * - I livelli del DAG in questa implementazione sono 0-indexed.
     *
     * Formula (pseudocodice): threshold(VM_k, l) = ⌊(Σ_{v=0}^{l-1} n_v / Σp_i) ×
     * p_k⌋
     */
    public int calculateInitialThreshold(int vmIndex, int level) {
        if (vmIndex >= vms.size() || level < 0) {
            return 0;
        }

        // Somma task da livello 0 a l-1 (livelli 0-indexed del DAG)
        double sumTasks = 0.0;
        for (int v = 0; v < level; v++) { // v < level, NON v <= level
            sumTasks += getNumberOfTasksAtLevel(v);
        }

        // Somma capacità di tutte le VM
        double sumCapacities = 0.0;
        for (VM vm : vms) {
            sumCapacities += getVMProcessingCapacity(vm);
        }

        if (sumCapacities == 0)
            return 0;

        double pk = getVMProcessingCapacity(vms.get(vmIndex));
        double result = (sumTasks / sumCapacities) * pk;

        return (int) Math.floor(result); // Floor, come da pseudocodice
    }

    /**
     * Gets the number of tasks at a specific level in the DAG
     * 
     * @param level The level to check
     * @return Number of tasks at the given level
     */
    private int getNumberOfTasksAtLevel(int level) {
        return levelTasks.getOrDefault(level, new ArrayList<>()).size();
    }

    /**
     * Gets the processing capacity of a VM
     * For simplicity, we use the first capability value or a default method
     * 
     * @param vm The VM to get capacity for
     * @return The processing capacity
     */
    private double getVMProcessingCapacity(VM vm) {
        Map<String, Double> capabilities = vm.getProcessingCapabilities();
        if (!capabilities.isEmpty()) {
            // Return the first capability value
            return capabilities.values().iterator().next();
        }
        return 1.0; // Default capacity if no capabilities are set
    }

    /**
     * Sets the task list (used when loading data externally)
     */
    public void setTasks(List<task> tasks) {
        this.tasks = tasks;
    }

    /**
     * Sets the VM list (used when loading data externally)
     */
    public void setVMs(List<VM> vms) {
        this.vms = vms;
    }

    /**
     * Loads VM data from processing_capacity.csv
     */
    public void loadVMsFromCSV(String filename) throws IOException {
        vms = DataLoader.loadVMsFromCSV(filename);
    }

    /**
     * Loads bandwidth data from bandwidth.csv
     * Format: vm_i,vm_j,bandwidth
     */
    public void loadBandwidthFromCSV(String filename) throws IOException {
        DataLoader.loadBandwidthFromCSV(filename, vms);
    }

    /**
     * Loads task data and builds the DAG structure
     */
    public void loadTasksFromCSV(String dagFilename, String taskFilename) throws IOException {
        tasks = DataLoader.loadTasksFromCSV(dagFilename, taskFilename);

        // Calculate levels for all tasks
        calculateTaskLevels();
    }

    public task getTaskById(int taskId) {
        return tasks.stream()
                .filter(t -> t.getID() == taskId)
                .findFirst()
                .orElse(null);
    }

    /**
     * Calculates the level of each task in the DAG using topological ordering
     */
    public void calculateTaskLevels() {
        taskLevels.clear();
        levelTasks.clear();

        // Find tasks with no predecessors (level 0)
        Queue<Integer> queue = new LinkedList<>();
        Map<Integer, Integer> inDegree = new HashMap<>();

        // Initialize in-degree count
        for (task t : tasks) {
            inDegree.put(t.getID(), t.getPre().size());
            if (t.getPre().isEmpty()) {
                queue.offer(t.getID());
                taskLevels.put(t.getID(), 0);
                if (!levelTasks.containsKey(0)) {
                    levelTasks.put(0, new ArrayList<>());
                }
                levelTasks.get(0).add(t.getID());
            }
        }

        // Process tasks level by level
        while (!queue.isEmpty()) {
            int currentTaskId = queue.poll();
            int currentLevel = taskLevels.get(currentTaskId);
            task currentTask = getTaskById(currentTaskId);

            // Process successors
            for (int succId : currentTask.getSucc()) {
                inDegree.put(succId, inDegree.get(succId) - 1);

                if (inDegree.get(succId) == 0) {
                    int succLevel = currentLevel + 1;
                    taskLevels.put(succId, succLevel);
                    if (!levelTasks.containsKey(succLevel)) {
                        levelTasks.put(succLevel, new ArrayList<>());
                    }
                    levelTasks.get(succLevel).add(succId);
                    queue.offer(succId);
                }
            }
        }
    }

    // Getters for accessing the data
    public List<VM> getVMs() {
        return vms;
    }

    public List<task> getTasks() {
        return tasks;
    }

    public Map<Integer, Integer> getTaskLevels() {
        return taskLevels;
    }

    public Map<Integer, List<Integer>> getLevelTasks() {
        return levelTasks;
    }

    /**
     * Calculates the finish time of a task on a specific VM
     * 
     * @param taskId  The task ID
     * @param vmIndex The VM index
     * @return The finish time
     */
    private double calculateFinishTime(int taskId, int vmIndex) {
        task t = getTaskById(taskId);
        VM vm = vms.get(vmIndex);

        if (t == null || vm == null) {
            return Double.MAX_VALUE;
        }

        // Simple finish time calculation: task_size / vm_processing_capacity
        double taskSize = t.getSize();
        double vmCapacity = getVMProcessingCapacity(vm);

        if (vmCapacity == 0) {
            return Double.MAX_VALUE;
        }

        return taskSize / vmCapacity;
    }

    /**
     * Generates preference list for a task based on ascending order of finish time
     * on different VMs
     * 
     * @param taskId The task ID
     * @return List of VM indices ordered by preference (best finish time first)
     */
    public List<Integer> generateTaskPreferences(int taskId) {
        List<Integer> preferences = new ArrayList<>();

        // Create pairs of (VM index, finish time)
        List<Map.Entry<Integer, Double>> vmFinishTimes = new ArrayList<>();

        for (int vmIndex = 0; vmIndex < vms.size(); vmIndex++) {
            double finishTime = calculateFinishTime(taskId, vmIndex);
            vmFinishTimes.add(new AbstractMap.SimpleEntry<>(vmIndex, finishTime));
        }

        // Sort by finish time (ascending order - best first)
        vmFinishTimes.sort(Map.Entry.comparingByValue());

        // Extract VM indices in preference order
        for (Map.Entry<Integer, Double> entry : vmFinishTimes) {
            preferences.add(entry.getKey());
        }

        return preferences;
    }

    /**
     * Generates preference list for a VM based on ascending order of task finish
     * times on this VM
     * 
     * @param vmIndex The VM index
     * @return List of task IDs ordered by preference (best finish time first)
     */
    public List<Integer> generateVMPreferences(int vmIndex) {
        List<Integer> preferences = new ArrayList<>();

        // Create pairs of (task ID, finish time)
        List<Map.Entry<Integer, Double>> taskFinishTimes = new ArrayList<>();

        for (task t : tasks) {
            double finishTime = calculateFinishTime(t.getID(), vmIndex);
            taskFinishTimes.add(new AbstractMap.SimpleEntry<>(t.getID(), finishTime));
        }

        // Sort by finish time (ascending order - best first)
        taskFinishTimes.sort(Map.Entry.comparingByValue());

        // Extract task IDs in preference order
        for (Map.Entry<Integer, Double> entry : taskFinishTimes) {
            preferences.add(entry.getKey());
        }

        return preferences;
    }

    /**
     * Generates preference matrices for all tasks
     * 
     * @return Map where key is task ID and value is list of VM indices in
     *         preference order
     */
    public Map<Integer, List<Integer>> generateAllTaskPreferences() {
        Map<Integer, List<Integer>> allTaskPreferences = new HashMap<>();

        for (task t : tasks) {
            List<Integer> preferences = generateTaskPreferences(t.getID());
            allTaskPreferences.put(t.getID(), preferences);
        }

        return allTaskPreferences;
    }

    /**
     * Generates preference matrices for all VMs
     * 
     * @return Map where key is VM index and value is list of task IDs in preference
     *         order
     */
    public Map<Integer, List<Integer>> generateAllVMPreferences() {
        Map<Integer, List<Integer>> allVMPreferences = new HashMap<>();

        for (int vmIndex = 0; vmIndex < vms.size(); vmIndex++) {
            List<Integer> preferences = generateVMPreferences(vmIndex);
            allVMPreferences.put(vmIndex, preferences);
        }

        return allVMPreferences;
    }

    /**
     * CORREZIONE 3: Implementazione corretta di SMGT con threshold dinamico
     */
    public Map<Integer, List<Integer>> runSMGT(Set<Integer> criticalPath) {
        Map<Integer, List<Integer>> finalAssignments = new HashMap<>();

        // Inizializza schedule vuoto
        for (int i = 0; i < vms.size(); i++) {
            finalAssignments.put(i, new ArrayList<>());
        }

        // Ottieni livelli ordinati
        List<Integer> levels = new ArrayList<>(levelTasks.keySet());
        Collections.sort(levels);

        System.out.println("=== SMGT Algorithm Start ===");
        System.out.println("Critical Path: " + criticalPath);

        // Processa ogni livello
        for (Integer level : levels) {
            processLevel(level, criticalPath, null, finalAssignments);
        }

        return finalAssignments;
    }

    /**
     * Variante di SMGT che integra uno scheduling pre-calcolato per i task del Critical Path.
     * 
     * Nota: la mappa cpSchedule deve usare gli stessi identificativi VM usati in SMGT
     * (in questa implementazione, gli indici 0..|VM|-1).
     */
    public Map<Integer, List<Integer>> runSMGT(Set<Integer> criticalPath, Map<Integer, Integer> cpSchedule) {
        Map<Integer, List<Integer>> finalAssignments = new HashMap<>();

        // Inizializza schedule vuoto
        for (int i = 0; i < vms.size(); i++) {
            finalAssignments.put(i, new ArrayList<>());
        }

        // Ottieni livelli ordinati
        List<Integer> levels = new ArrayList<>(levelTasks.keySet());
        Collections.sort(levels);

        System.out.println("=== SMGT Algorithm Start ===");
        System.out.println("Critical Path: " + criticalPath);
        System.out.println("CP schedule provided: " + (cpSchedule != null));

        // Processa ogni livello
        for (Integer level : levels) {
            processLevel(level, criticalPath, cpSchedule, finalAssignments);
        }

        return finalAssignments;
    }

    /**
     * Processa un singolo livello secondo pseudocodice
     */
        private void processLevel(int level, Set<Integer> criticalPath, Map<Integer, Integer> cpSchedule,
            Map<Integer, List<Integer>> finalAssignments) {

        System.out.println("\n--- Processing Level " + level + " ---");

        // Linea 4: Tₗ ← {task nel livello l} \ CP
        List<Integer> levelTaskIds = levelTasks.getOrDefault(level, new ArrayList<>());

        List<Integer> cpTasks = new ArrayList<>();
        Set<Integer> unassigned = new HashSet<>();

        for (Integer taskId : levelTaskIds) {
            if (criticalPath.contains(taskId)) {
                cpTasks.add(taskId);
            } else {
                unassigned.add(taskId);
            }
        }

        int fastestVM = getFastestVM();

        // Assegna i task CP usando lo schedule fornito, altrimenti fallback alla VM più veloce.
        Map<Integer, Integer> cpCountPerVm = new HashMap<>();
        for (Integer t : cpTasks) {
            int targetVm = fastestVM;
            if (cpSchedule != null) {
                Integer mapped = cpSchedule.get(t);
                if (mapped != null) {
                    targetVm = mapped;
                }
            }
            finalAssignments.get(targetVm).add(t);
            cpCountPerVm.put(targetVm, cpCountPerVm.getOrDefault(targetVm, 0) + 1);
            System.out.println("✓ CP task t" + t + " assigned to VM" + targetVm);
        }

        System.out.println("Tasks to assign: " + unassigned);

        // Linee 7-11: Calcola threshold e inizializza waiting lists
        Map<Integer, VMThreshold> vmThresholds = new HashMap<>();
        int totalThreshold = 0;
        for (int vmIndex = 0; vmIndex < vms.size(); vmIndex++) {
            int threshold = calculateInitialThreshold(vmIndex, level + 1);

            // Riduci la capacità del livello per i task CP già allocati su questa VM.
            int cpOnThisVm = cpCountPerVm.getOrDefault(vmIndex, 0);
            threshold -= cpOnThisVm;
            threshold = Math.max(0, threshold);

            vmThresholds.put(vmIndex, new VMThreshold(threshold));

            totalThreshold += threshold;

            System.out.println("VM" + vmIndex + " threshold: " + threshold);
        }

        // Se per arrotondamento (floor) la capacità totale del livello è insufficiente,
        // garantisci comunque che tutti i task del livello possano essere assegnati.
        int deficit = unassigned.size() - totalThreshold;
        if (deficit > 0) {
            VMThreshold fastest = vmThresholds.get(fastestVM);
            fastest.remaining += deficit;
            System.out.println("⚠️ Threshold deficit " + deficit + " -> added to VM" + fastestVM);
        }

        // Linee 14-19: Genera preference list per task (DINAMICA)
        Map<Integer, List<Integer>> taskPreferences = new HashMap<>();
        for (Integer taskId : unassigned) {
            taskPreferences.put(taskId, generateTaskPreferences(taskId));
        }

        // Linee 22-27: Genera preference list per VM
        Map<Integer, List<Integer>> vmPreferences = generateAllVMPreferences();

        // Linee 30-61: Processo di matching stabile
        while (!unassigned.isEmpty()) {
            // Linea 32: preleva task
            Integer ti = unassigned.iterator().next();
            unassigned.remove(ti);

            // Linea 33: prima VM in preference(ti)
            List<Integer> tiPrefs = taskPreferences.get(ti);
            
            if (tiPrefs.isEmpty()) {
                int bestVM = -1;
                double bestFT = Double.MAX_VALUE;

                for (int vm = 0; vm < vms.size(); vm++) {
                    double ft = calculateFinishTime(ti, vm);
                    if (ft < bestFT) {
                        bestFT = ft;
                        bestVM = vm;
                    }
                }

                finalAssignments.get(bestVM).add(ti);
                System.out.println("⚠️ Forced best-FT assignment of t" + ti + " to VM" + bestVM);
                continue;
            }


            Integer vmk = tiPrefs.get(0);
            VMThreshold vmThreshold = vmThresholds.get(vmk);

            System.out.println("\nTrying to assign t" + ti + " to VM" + vmk +
                    " (remaining: " + vmThreshold.remaining + ")");

            // Linea 35: threshold(VMₖ, l) > 0?
            if (vmThreshold.remaining > 0) {
                // Linee 37-40: VM ha spazio
                vmThreshold.waitingList.add(ti);
                finalAssignments.get(vmk).add(ti);
                vmThreshold.remaining--; // CORREZIONE: decrementa threshold!

                System.out.println("✓ Assigned t" + ti + " to VM" + vmk);

            } else {
                // Linee 42-60: VM piena, cerca task da rigettare
                Integer tWorst = findWorstTask(vmThreshold.waitingList, vmk, vmPreferences);

                if (tWorst == null) {
                    // Nessun task da rigettare, prova prossima VM
                    System.out.println("✗ VM" + vmk + " full, no task to replace");
                    tiPrefs.remove(0); // Linea 57
                    unassigned.add(ti); // Linea 58
                    continue;
                }

                double ftTi = calculateFinishTime(ti, vmk);
                double ftWorst = calculateFinishTime(tWorst, vmk);

                // Linea 45: FT(tᵢ, VMₖ) < FT(t_worst, VMₖ)?
                if (ftTi < ftWorst) {
                    // Linee 47-54: ti è preferito, sostituisci
                    vmThreshold.waitingList.remove(tWorst);
                    vmThreshold.waitingList.add(ti);
                    finalAssignments.get(vmk).remove(tWorst);
                    finalAssignments.get(vmk).add(ti);

                    // CORREZIONE: rimuovi VMk da preference(t_worst)
                    taskPreferences.get(tWorst).remove(vmk);
                    unassigned.add(tWorst); // Linea 54

                    System.out.println("✓ Replaced t" + tWorst + " with t" + ti + " on VM" + vmk);

                } else {
                    // Linee 56-58: t_worst rimane, rigetta ti
                    tiPrefs.remove(0); // Linea 57: rimuovi VMk da preference(ti)
                    unassigned.add(ti); // Linea 58

                    System.out.println("✗ VM" + vmk + " prefers t" + tWorst + " over t" + ti);
                }
            }
        }

        // Debug: stato finale del livello
        System.out.println("\nLevel " + level + " assignments:");
        for (int vmIndex = 0; vmIndex < vms.size(); vmIndex++) {
            VMThreshold vmt = vmThresholds.get(vmIndex);
            System.out.println("VM" + vmIndex + ": " + vmt.waitingList +
                    " (used " + vmt.waitingList.size() +
                    "/" + (vmt.waitingList.size() + vmt.remaining) + ")");
        }
    }

    /**
     * Trova il task con FT massimo nella waiting list
     * (Linea 43: t_worst ← task con FT massimo)
     */
    private Integer findWorstTask(List<Integer> waitingList, int vmIndex,
            Map<Integer, List<Integer>> vmPreferences) {
        if (waitingList.isEmpty())
            return null;

        Integer worst = null;
        double maxFT = -1;

        for (Integer taskId : waitingList) {
            double ft = calculateFinishTime(taskId, vmIndex);
            if (ft > maxFT) {
                maxFT = ft;
                worst = taskId;
            }
        }

        return worst;
    }

    private int getFastestVM() {
        int bestVm = 0;
        double maxCapacity = -1;

        for (int i = 0; i < vms.size(); i++) {
            double cap = getVMProcessingCapacity(vms.get(i));
            if (cap > maxCapacity) {
                maxCapacity = cap;
                bestVm = i;
            }
        }
        return bestVm;
    }

        /**
     * Wrapper per generare pre-schedule senza critical path
     * Necessario per LOTD
     */
    public Map<Integer, List<Integer>> runSMGTAlgorithmCorrect() {
        return runSMGT(new HashSet<>()); // CP vuoto
    }

        /**
     * Restituisce i task in ordine topologico (livelli del DAG)
     */
    public List<task> getTasksTopologicallySorted() {
        List<task> sorted = new ArrayList<>();
        List<Integer> levels = new ArrayList<>(levelTasks.keySet());
        Collections.sort(levels);
        for (int l : levels) {
            for (int taskId : levelTasks.get(l)) {
                task t = getTaskById(taskId);
                if (t != null) sorted.add(t);
            }
        }
        return sorted;
    }



}
