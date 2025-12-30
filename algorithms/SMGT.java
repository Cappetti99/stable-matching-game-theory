import java.util.*;
import java.io.*;

/**
 * SMGT - Stable Matching Game Theory Algorithm
 * 
 * Versione migliorata con:
 * - Calcolo threshold più robusto
 * - Gestione deterministica dei task CP
 * - Logging dettagliato per debug
 * - Compatibilità completa con SMCPTD e DCP
 * 
 * @author Lorenzo Cappetti
 * @version 3.0 - Improved
 */
public class SMGT {
    private List<VM> vms;
    private List<task> tasks;
    private Map<Integer, Integer> taskLevels; // task ID -> livello
    private Map<Integer, List<Integer>> levelTasks; // livello -> lista task IDs

    // Configurazione
    private static final boolean VERBOSE = false; // Imposta a true per debug dettagliato

    public SMGT() {
        this.vms = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.taskLevels = new HashMap<>();
        this.levelTasks = new HashMap<>();
    }

    // ==================== SETTERS E GETTERS ====================

    public void setTasks(List<task> tasks) {
        this.tasks = tasks;
    }

    public void setVMs(List<VM> vms) {
        this.vms = vms;
    }

    public List<VM> getVMs() {
        return vms;
    }

    public List<task> getTasks() {
        return tasks;
    }

    public task getTaskById(int taskId) {
        return tasks.stream()
                .filter(t -> t.getID() == taskId)
                .findFirst()
                .orElse(null);
    }

    // ==================== DATA LOADING ====================

    // Note: Load methods removed - use DataLoader directly instead

    // ==================== TASK LEVELS CALCULATION ====================

    /**
     * Calcola i livelli del DAG usando BFS topologico
     */
    public void calculateTaskLevels() {
        taskLevels.clear();
        levelTasks.clear();

        Queue<Integer> queue = new LinkedList<>();
        Map<Integer, Integer> inDegree = new HashMap<>();

        // Inizializza in-degree
        for (task t : tasks) {
            int degree = (t.getPre() == null) ? 0 : t.getPre().size();
            inDegree.put(t.getID(), degree);

            if (degree == 0) {
                queue.offer(t.getID());
                taskLevels.put(t.getID(), 0);
                levelTasks.computeIfAbsent(0, k -> new ArrayList<>()).add(t.getID());
            }
        }

        // BFS per calcolare livelli
        while (!queue.isEmpty()) {
            int currentTaskId = queue.poll();
            int currentLevel = taskLevels.get(currentTaskId);
            task currentTask = getTaskById(currentTaskId);

            if (currentTask == null || currentTask.getSucc() == null)
                continue;

            for (int succId : currentTask.getSucc()) {
                int newDegree = inDegree.get(succId) - 1;
                inDegree.put(succId, newDegree);

                if (newDegree == 0) {
                    int succLevel = currentLevel + 1;
                    taskLevels.put(succId, succLevel);
                    levelTasks.computeIfAbsent(succLevel, k -> new ArrayList<>()).add(succId);
                    queue.offer(succId);
                }
            }
        }

        if (VERBOSE) {
            System.out.println("📊 Task Levels calculated:");
            levelTasks.forEach(
                    (level, taskIds) -> System.out.println("   Level " + level + ": " + taskIds.size() + " tasks"));
        }
    }

    // ==================== MAIN ALGORITHM ====================

    /**
     * ALGORITMO PRINCIPALE: SMGT con Critical Path prioritario
     * 
     * Per ogni livello l:
     * 1. Assegna task del CP alla VM più veloce
     * 2. Calcola threshold per le altre VM
     * 3. Usa stable matching per i task non-CP
     * 
     * @param criticalPath Set di task IDs appartenenti al Critical Path
     * @return Mappa VM_ID -> Lista di task IDs assegnati
     */
    public Map<Integer, List<Integer>> runSMGT(Set<Integer> criticalPath) {
        if (VERBOSE) {
            System.out.println("\n" + "=".repeat(70));
            System.out.println("🎮 SMGT ALGORITHM START");
            System.out.println("=".repeat(70));
            System.out.println("Critical Path: " + criticalPath);
            System.out.println("Total tasks: " + tasks.size());
            System.out.println("Total VMs: " + vms.size());
        }

        // Inizializza schedule vuoto
        Map<Integer, List<Integer>> schedule = new HashMap<>();
        for (int i = 0; i < vms.size(); i++) {
            schedule.put(i, new ArrayList<>());
        }

        // Ottieni livelli ordinati
        List<Integer> levels = new ArrayList<>(levelTasks.keySet());
        Collections.sort(levels);

        if (VERBOSE) {
            System.out.println("DAG Levels: " + levels);
        }

        // Processa ogni livello
        for (Integer level : levels) {
            processLevel(level, criticalPath, schedule);
        }

        // Verifica finale
        int totalAssigned = schedule.values().stream()
                .mapToInt(List::size)
                .sum();

        if (VERBOSE || totalAssigned != tasks.size()) {
            System.out.println("\n📊 SMGT Summary:");
            System.out.println("   Tasks assigned: " + totalAssigned + "/" + tasks.size());
            if (totalAssigned != tasks.size()) {
                System.err.println("   ⚠️  WARNING: Not all tasks assigned!");
            }
        }

        return schedule;
    }

    /**
     * Processa un singolo livello del DAG
     * 
     * @param level        Livello da processare
     * @param criticalPath Set dei task nel Critical Path
     * @param schedule     Schedule corrente (viene modificato)
     */
    private void processLevel(int level, Set<Integer> criticalPath,
            Map<Integer, List<Integer>> schedule) {

        if (VERBOSE) {
            System.out.println("\n" + "-".repeat(70));
            System.out.println("📍 Processing Level " + level);
        }

        List<Integer> levelTaskIds = levelTasks.getOrDefault(level, new ArrayList<>());

        if (levelTaskIds.isEmpty()) {
            if (VERBOSE)
                System.out.println("   (empty level, skipping)");
            return;
        }

        // Separa task CP da task non-CP
        List<Integer> cpTasks = new ArrayList<>();
        List<Integer> nonCpTasks = new ArrayList<>();

        for (Integer taskId : levelTaskIds) {
            if (criticalPath.contains(taskId)) {
                cpTasks.add(taskId);
            } else {
                nonCpTasks.add(taskId);
            }
        }

        if (VERBOSE) {
            System.out.println("   Total tasks: " + levelTaskIds.size());
            System.out.println("   CP tasks: " + cpTasks.size());
            System.out.println("   Non-CP tasks: " + nonCpTasks.size());
        }

        // STEP 1: Calcola threshold per ogni VM (per tutte le task del livello)
        calculateAndSetThresholds(level, levelTaskIds.size());

        // STEP 2: Assegna task CP alla VM più veloce e aggiorna waitingList
        int fastestVM = getFastestVM();

        for (Integer cpTaskId : cpTasks) {
            schedule.get(fastestVM).add(cpTaskId);
            vms.get(fastestVM).addToWaitingList(cpTaskId);

            if (VERBOSE) {
                VM vm = vms.get(fastestVM);
                System.out.println("   ✓ CP task t" + cpTaskId + " → VM" + fastestVM +
                    " (waitingList: " + vm.getWaitingListSize() + "/" + vm.getThreshold() + ")");
            }
        }

        // STEP 3: Se non ci sono task non-CP, termina
        if (nonCpTasks.isEmpty()) {
            if (VERBOSE)
                System.out.println("   (no non-CP tasks, level complete)");
            return;
        }

        // STEP 4: Genera preference lists
        Map<Integer, List<Integer>> taskPreferences = new HashMap<>();
        for (Integer taskId : nonCpTasks) {
            taskPreferences.put(taskId, generateTaskPreferences(taskId));
        }

        Map<Integer, List<Integer>> vmPreferences = generateAllVMPreferences();

        // STEP 5: Stable matching per task non-CP (uno ad uno)
        stableMatchingForLevel(nonCpTasks, taskPreferences, vmPreferences, schedule);

        if (VERBOSE) {
            System.out.println("   Level " + level + " completed:");
            for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
                VM vm = vms.get(vmIdx);
                System.out.println("      VM" + vmIdx + ": " + vm.getWaitingListSize() + "/" + vm.getThreshold() + " slots used");
            }
        }
    }

    // ==================== THRESHOLD CALCULATION ====================

    /**
     * Calcola e setta il threshold per ogni VM
     * 
     * Formula:
     * threshold(VM_k, l) = ceil((Σ_{v=0}^{l} n_v / Σp_i) × p_k)
     * 
     * Il threshold viene settato direttamente sulla VM e aumenta ad ogni livello.
     * La VM è piena quando waitingList.size() >= threshold
     */
    private void calculateAndSetThresholds(int level, int tasksInCurrentLevel) {

        // Calcola somma task nei livelli da 0 a level (incluso)
        double sumTasksUpToLevel = 0.0;
        for (int l = 0; l <= level; l++) {
            sumTasksUpToLevel += levelTasks.getOrDefault(l, new ArrayList<>()).size();
        }

        // Calcola somma capacità VM
        double sumCapacities = 0.0;
        for (VM vm : vms) {
            sumCapacities += getVMProcessingCapacity(vm);
        }

        if (sumCapacities == 0) {
            // Fallback: distribuzione uniforme
            int baseThreshold = (int) Math.ceil((double) sumTasksUpToLevel / vms.size());
            for (VM vm : vms) {
                vm.setThreshold(baseThreshold);
            }
            return;
        }

        // Calcola e setta threshold per ogni VM
        int totalThreshold = 0;
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            double capacity = getVMProcessingCapacity(vm);

            // Formula: threshold(VMk, l) = ceil((Σ(v=0 to l) nv / Σpi) × pk)
            double rawThreshold = (sumTasksUpToLevel / sumCapacities) * capacity;
            int threshold = (int) Math.ceil(rawThreshold);

            vm.setThreshold(threshold);
            totalThreshold += threshold;
            
            if (VERBOSE) {
                System.out.println("   VM" + vmIdx + " threshold: " + threshold + 
                    " (waitingList: " + vm.getWaitingListSize() + ")");
            }
        }

        // CORREZIONE: Se threshold totale insufficiente, distribuisci proporzionalmente
        int deficit = (int) sumTasksUpToLevel - totalThreshold;
        if (deficit > 0) {
            if (VERBOSE) {
                System.out.println("   Threshold deficit: " + deficit + " tasks");
            }
            
            // Create list of (vmIdx, remainingCapacityFraction) for proportional distribution
            List<Map.Entry<Integer, Double>> vmCapacityFractions = new ArrayList<>();
            for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
                double capacity = getVMProcessingCapacity(vms.get(vmIdx));
                double fraction = capacity / sumCapacities;
                vmCapacityFractions.add(new AbstractMap.SimpleEntry<>(vmIdx, fraction));
            }
            
            // Sort by capacity fraction (descending) to distribute fairly
            vmCapacityFractions.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
            
            // Distribute deficit using round-robin weighted by capacity
            int remaining = deficit;
            int roundRobinIdx = 0;
            while (remaining > 0) {
                int vmIdx = vmCapacityFractions.get(roundRobinIdx % vmCapacityFractions.size()).getKey();
                VM vm = vms.get(vmIdx);
                vm.setThreshold(vm.getThreshold() + 1);
                remaining--;
                roundRobinIdx++;
                
                if (VERBOSE) {
                    System.out.println("      → +1 to VM" + vmIdx);
                }
            }
        }
    }

    // ==================== STABLE MATCHING ====================

    /**
     * Stable matching per i task non-CP di un livello
     * La VM è piena quando waitingList.size() >= threshold
     */
    private void stableMatchingForLevel(
            List<Integer> unassignedTasks,
            Map<Integer, List<Integer>> taskPreferences,
            Map<Integer, List<Integer>> vmPreferences,
            Map<Integer, List<Integer>> schedule) {

        Set<Integer> remaining = new HashSet<>(unassignedTasks);

        while (!remaining.isEmpty()) {
            // Prendi un task da assegnare
            Integer taskId = remaining.iterator().next();
            remaining.remove(taskId);

            List<Integer> prefs = taskPreferences.get(taskId);

            // Se non ha più preferenze, assegna alla migliore disponibile
            if (prefs == null || prefs.isEmpty()) {
                int bestVM = findBestAvailableVM(taskId);
                assignTask(taskId, bestVM, schedule);
                continue;
            }

            // Prova prima VM in preferenza
            Integer vmIdx = prefs.get(0);
            VM vm = vms.get(vmIdx);

            // Se VM ha spazio (waitingList.size() < threshold), assegna direttamente
            if (!vm.isFull()) {
                assignTask(taskId, vmIdx, schedule);
                continue;
            }

            // VM piena: cerca task da sostituire (solo tra non-CP tasks)
            Integer worstTask = findWorstNonCPTask(vm.getWaitingList(), vmIdx, vmPreferences, taskPreferences);

            if (worstTask == null) {
                // Nessun task da sostituire, prova prossima VM
                prefs.remove(0);
                remaining.add(taskId);
                continue;
            }

            // Confronta finish time
            double ftTask = calculateFinishTime(taskId, vmIdx);
            double ftWorst = calculateFinishTime(worstTask, vmIdx);

            if (ftTask < ftWorst) {
                // Sostituisci worst con task corrente
                unassignTask(worstTask, vmIdx, schedule);
                assignTask(taskId, vmIdx, schedule);

                // Rimetti worst nella coda (solo se ha preferenze - non-CP tasks)
                List<Integer> worstPrefs = taskPreferences.get(worstTask);
                if (worstPrefs != null) {
                    worstPrefs.remove((Integer) vmIdx);
                }
                remaining.add(worstTask);

                if (VERBOSE) {
                    System.out.println("   ↔ Replaced t" + worstTask +
                            " with t" + taskId + " on VM" + vmIdx);
                }
            } else {
                // worst rimane, rigetta task corrente
                prefs.remove(0);
                remaining.add(taskId);
            }
        }
    }

    // ==================== UTILITY METHODS ====================

    /**
     * Assegna un task a una VM (aggiorna schedule e waitingList)
     */
    private void assignTask(int taskId, int vmIdx,
            Map<Integer, List<Integer>> schedule) {
        schedule.get(vmIdx).add(taskId);
        VM vm = vms.get(vmIdx);
        vm.addToWaitingList(taskId);

        if (VERBOSE) {
            System.out.println("   ✓ Assigned t" + taskId + " → VM" + vmIdx +
                " (waitingList: " + vm.getWaitingListSize() + "/" + vm.getThreshold() + ")");
        }
    }

    /**
     * Rimuove assegnamento di un task (rimuove da schedule e waitingList)
     */
    private void unassignTask(int taskId, int vmIdx,
            Map<Integer, List<Integer>> schedule) {
        schedule.get(vmIdx).remove((Integer) taskId);
        VM vm = vms.get(vmIdx);
        vm.getWaitingList().remove((Integer) taskId);
    }

    /**
     * Trova VM con capacità residua migliore per un task
     * La VM ha spazio se waitingList.size() < threshold
     */
    private int findBestAvailableVM(int taskId) {
        int bestVM = -1;
        double bestFT = Double.MAX_VALUE;

        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            VM vm = vms.get(vmIdx);
            if (!vm.isFull()) {
                double ft = calculateFinishTime(taskId, vmIdx);
                if (ft < bestFT) {
                    bestFT = ft;
                    bestVM = vmIdx;
                }
            }
        }

        // Fallback: usa VM più veloce
        if (bestVM == -1) {
            bestVM = getFastestVM();
            if (VERBOSE) {
                System.out.println("   ⚠️  No VM available, forcing to VM" + bestVM);
            }
        }

        return bestVM;
    }

    /**
     * Trova task con finish time peggiore in una lista (solo tra non-CP tasks)
     * CP tasks non possono essere sostituiti perché hanno priorità
     */
    private Integer findWorstNonCPTask(List<Integer> taskList, int vmIdx,
            Map<Integer, List<Integer>> vmPreferences,
            Map<Integer, List<Integer>> taskPreferences) {
        if (taskList.isEmpty())
            return null;

        Integer worst = null;
        double maxFT = -1;

        for (Integer taskId : taskList) {
            // Skip CP tasks - they cannot be replaced
            if (!taskPreferences.containsKey(taskId)) {
                continue;
            }
            
            double ft = calculateFinishTime(taskId, vmIdx);
            if (ft > maxFT) {
                maxFT = ft;
                worst = taskId;
            }
        }

        return worst;
    }

    /**
     * Trova task con finish time peggiore in una lista
     */
    private Integer findWorstTask(List<Integer> taskList, int vmIdx,
            Map<Integer, List<Integer>> vmPreferences) {
        if (taskList.isEmpty())
            return null;

        Integer worst = null;
        double maxFT = -1;

        for (Integer taskId : taskList) {
            double ft = calculateFinishTime(taskId, vmIdx);
            if (ft > maxFT) {
                maxFT = ft;
                worst = taskId;
            }
        }

        return worst;
    }

    /**
     * Calcola finish time di un task su una VM usando Metrics.ET
     */
    private double calculateFinishTime(int taskId, int vmIdx) {
        task t = getTaskById(taskId);
        if (t == null || vmIdx >= vms.size()) {
            return Double.MAX_VALUE;
        }

        VM vm = vms.get(vmIdx);
        
        // Usa Metrics.ET invece del calcolo manuale
        return Metrics.ET(t, vm, "processingCapacity");
    }

    /**
     * Ottiene capacità di processing di una VM
     */
    private double getVMProcessingCapacity(VM vm) {
        // Prova "processingCapacity" prima
        double cap = vm.getCapability("processingCapacity");
        if (cap > 0)
            return cap;

        // Fallback a "processing"
        cap = vm.getCapability("processing");
        if (cap > 0)
            return cap;

        // Ultimo fallback: prima capability disponibile
        Map<String, Double> caps = vm.getProcessingCapabilities();
        if (!caps.isEmpty()) {
            return caps.values().iterator().next();
        }

        return 1.0; // Default
    }

    /**
     * Trova indice della VM più veloce
     */
    private int getFastestVM() {
        int bestVM = 0;
        double maxCapacity = -1;

        for (int i = 0; i < vms.size(); i++) {
            double cap = getVMProcessingCapacity(vms.get(i));
            if (cap > maxCapacity) {
                maxCapacity = cap;
                bestVM = i;
            }
        }

        return bestVM;
    }

    // ==================== PREFERENCE GENERATION ====================

    /**
     * Genera lista di preferenza per un task (VMs ordinate per finish time)
     */
    public List<Integer> generateTaskPreferences(int taskId) {
        List<Map.Entry<Integer, Double>> vmFinishTimes = new ArrayList<>();

        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            double finishTime = calculateFinishTime(taskId, vmIdx);
            vmFinishTimes.add(new AbstractMap.SimpleEntry<>(vmIdx, finishTime));
        }

        // Ordina per finish time ascendente
        vmFinishTimes.sort(Map.Entry.comparingByValue());

        List<Integer> preferences = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : vmFinishTimes) {
            preferences.add(entry.getKey());
        }

        return preferences;
    }

    /**
     * Genera lista di preferenza per una VM (tasks ordinati per finish time)
     */
    public List<Integer> generateVMPreferences(int vmIdx) {
        List<Map.Entry<Integer, Double>> taskFinishTimes = new ArrayList<>();

        for (task t : tasks) {
            double finishTime = calculateFinishTime(t.getID(), vmIdx);
            taskFinishTimes.add(new AbstractMap.SimpleEntry<>(t.getID(), finishTime));
        }

        // Ordina per finish time ascendente
        taskFinishTimes.sort(Map.Entry.comparingByValue());

        List<Integer> preferences = new ArrayList<>();
        for (Map.Entry<Integer, Double> entry : taskFinishTimes) {
            preferences.add(entry.getKey());
        }

        return preferences;
    }

    /**
     * Genera preference lists per tutte le VM
     */
    public Map<Integer, List<Integer>> generateAllVMPreferences() {
        Map<Integer, List<Integer>> allPreferences = new HashMap<>();

        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            allPreferences.put(vmIdx, generateVMPreferences(vmIdx));
        }

        return allPreferences;
    }

}