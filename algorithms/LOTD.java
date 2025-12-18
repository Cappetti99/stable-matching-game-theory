
import java.util.*;

public class LOTD {

    private static final double RULE2_EPSILON = 1e-6;
    private SMGT smgt;
    private List<VM> vms;
    private List<task> tasks;

    // Communication cost model (time at avgBandwidth=25, keyed as "predId_succId")
    // If provided, it allows CCR to influence duplication decisions.
    private Map<String, Double> communicationCosts = Collections.emptyMap();

    // State of the schedule
    private Map<Integer, Double> taskAST; // Actual Start Time
    private Map<Integer, Double> taskAFT; // Actual Finish Time
    private Map<Integer, List<Integer>> vmSchedule; // VM ID -> List of task IDs
    private Map<Integer, Integer> taskToVM; // Task ID -> VM ID

    // Duplicates management
    private Map<String, TaskDuplicate> duplicates; // "taskId_vmId" -> Duplicate Info
    private Map<Integer, Set<Integer>> duplicatedTasksOnVM; // VM ID -> Set of duplicated task IDs

    // Inner class for Duplicate info
    private static class TaskDuplicate {
        int vmId;
        double ast;
        double aft;

        TaskDuplicate(int vmId, double ast, double aft) {
            this.vmId = vmId;
            this.ast = ast;
            this.aft = aft;
        }
    }

    private static class IdleSlot {
        double start;
        double end;
        double duration;

        IdleSlot(double start, double end) {
            this.start = start;
            this.end = end;
            this.duration = end - start;
        }
    }

    public LOTD(SMGT smgt) {
        this.smgt = smgt;
        this.vms = smgt.getVMs();
        this.tasks = smgt.getTasks();
        this.taskAST = new HashMap<>();
        this.taskAFT = new HashMap<>();
        this.vmSchedule = new HashMap<>();
        this.taskToVM = new HashMap<>();
        this.duplicates = new HashMap<>();
        this.duplicatedTasksOnVM = new HashMap<>();

        for (VM vm : vms) {
            duplicatedTasksOnVM.put(vm.getID(), new HashSet<>());
        }
    }

    public void setCommunicationCosts(Map<String, Double> communicationCosts) {
        this.communicationCosts = (communicationCosts == null) ? Collections.emptyMap() : communicationCosts;
    }

    /**
     * Main Entry point for the validation logic
     */
    public Map<Integer, List<Integer>> executeLOTDCorrect(Map<Integer, List<Integer>> preSchedule) {
        System.out.println("\n=== FASE 1: INPUT E CONTESTO INIZIALE ===");

        // --- FASE 1: Input e Contesto Iniziale ---
        initializeState(preSchedule);

        // --- FASE 2: Identificazione dei Candidati (Loop Esterno) ---
        System.out.println("\n=== FASE 2: IDENTIFICAZIONE CANDIDATI ===");
        List<Integer> T0 = getLevel1Tasks();
        System.out.println("Candidate Tasks (Level 1): " + T0);

        if (T0.isEmpty())
            return vmSchedule;

        // Iterate candidates
        for (Integer tiId : T0) {
            task ti = smgt.getTaskById(tiId);
            if (ti == null)
                continue;

            System.out.println("\nProcessing Candidate Task: t" + tiId);

            // --- FASE 3: Analisi dei Successori (Loop Interno) ---
            List<Integer> successors = ti.getSucc();
            if (successors == null || successors.isEmpty()) {
                System.out.println("  No successors, skipping.");
                continue;
            }

            // Identify Target VMs based on successors
            // We want to try duplicating `ti` on the VM of `tsucc`
            Set<Integer> targetVMs = new HashSet<>();
            for (Integer succId : successors) {
                Integer vmSucc = taskToVM.get(succId);
                if (vmSucc != null) {
                    targetVMs.add(vmSucc);
                }
            }

            for (Integer vmTargetId : targetVMs) {
                // Rule 1: Pertinenza - The VM MUST host a successor
                // Implicitly handled by constructing targetVMs from successors

                // Skip if ti is already on vmTarget (original)
                Integer currentVM = taskToVM.get(tiId);
                if (currentVM != null && currentVM.equals(vmTargetId)) {
                    continue;
                }
                // Skip if already duplicated there
                if (duplicatedTasksOnVM.get(vmTargetId).contains(tiId)) {
                    continue;
                }

                System.out.println("  Analyzing Target VM: " + vmTargetId);

                // --- FASE 4: Verifica dei Vincoli (Safety Checks) ---

                // Calcola quanto dura la copia su questa VM
                double executionTime = calculateET(ti, vmTargetId);

                // Calcola tempo di arrivo dati originale (per vedere se conviene)
                Double originalAFT = taskAFT.get(tiId);
                double transTime = calculateTransmissionTime(tiId, -1, currentVM, vmTargetId);
                double arrivalTime = originalAFT + transTime;

                // Check 1: Disponibilità Temporale (Idle Slot)
                // Dobbiamo trovare uno slot PRIMA dell'inizio del successore

                List<IdleSlot> slots = findIdleSlots(vmTargetId);
                boolean success = false;

                for (IdleSlot slot : slots) {
                    if (slot.duration >= executionTime) {
                        // Check Convenienza: Duplicare conviene solo se finisce PRIMA dell'arrivo dati
                        if ((slot.start + executionTime) >= arrivalTime) {
                            continue;
                        }

                        // Check 2: Integrità della Coda (Rule 2)
                        // Simuliamo inserimento
                        // Verifichiamo che l'inserimento non causi problemi

                        if (attemptDuplicationWithSafety(tiId, vmTargetId, slot, executionTime)) {
                            success = true;
                            break; // Duplication done for this Target VM
                        }
                    }
                }

                if (!success) {
                    System.out.println("    Failed to duplicate t" + tiId + " on VM" + vmTargetId);
                }
            }
        }

        System.out.println("\n=== FASE 5: FINAL SCHEDULE ===");
        printFinalSchedule();
        return vmSchedule;
    }

    // --- PHASE 4 & 5 LOGIC ---

    private boolean attemptDuplicationWithSafety(int taskId, int vmId, IdleSlot slot, double duration) {
        // --- FASE 4: CHECK 2 (Simulazione) ---

        // Provisional parameters for the duplicate
        double dupStart = slot.start;
        double dupFinish = dupStart + duration;

        // Verify it doesn't overlap next task in schedule (implicit in IdleSlot, but
        // safety first)
        // Verify specifically against existing tasks on this VM
        if (!isSlotSafe(vmId, dupStart, dupFinish)) {
            return false;
        }

        // --- FASE 5: ESECUZIONE E AGGIORNAMENTO ---
        System.out.println("    ✓ Checks passed. Duplicating t" + taskId + " on VM" + vmId + " [" + dupStart + "-"
                + dupFinish + "]");

        performDuplication(taskId, vmId, dupStart, dupFinish);

        // Aggiornamento Parametri e Successori
        updateSuccessors(taskId, vmId);

        return true;
    }

    private boolean isSlotSafe(int vmId, double start, double end) {
        List<Integer> tasksOnVm = vmSchedule.get(vmId);
        if (tasksOnVm == null)
            return true;

        for (Integer tid : tasksOnVm) {
            Double s = taskAST.get(tid);
            Double f = taskAFT.get(tid);
            if (s == null || f == null)
                continue;

            // Check overlap: non (End <= s OR Start >= f)
            if (!(end <= s + RULE2_EPSILON || start >= f - RULE2_EPSILON)) {
                return false; // Overlap detected
            }
        }
        return true;
    }

    private void performDuplication(int taskId, int vmId, double ast, double aft) {
        // Registra duplicato
        TaskDuplicate dup = new TaskDuplicate(vmId, ast, aft);
        duplicates.put(taskId + "_" + vmId, dup);
        duplicatedTasksOnVM.get(vmId).add(taskId);
    }

    private boolean updateSuccessors(int originalTaskId, int hostVmId) {
        task t = smgt.getTaskById(originalTaskId);
        boolean changed = false;
        Set<Integer> visited = new HashSet<>();

        if (t != null && t.getSucc() != null) {
            for (Integer succId : t.getSucc()) {
                Integer succVm = taskToVM.get(succId);
                // Check if successor is on the same VM as the duplicate to benefit from local
                // data
                if (succVm != null && succVm == hostVmId) {
                    if (recalculateValidation(succId)) {
                        changed = true;
                        propagateUpdate(succId, visited);
                    }
                }
            }
        }
        return changed;
    }

    private void propagateUpdate(int taskId, Set<Integer> visited) {
        if (visited.contains(taskId))
            return;
        visited.add(taskId);

        task t = smgt.getTaskById(taskId);
        if (t == null || t.getSucc() == null)
            return;

        for (Integer succId : t.getSucc()) {
            if (recalculateValidation(succId)) {
                propagateUpdate(succId, visited);
            }
        }
    }

    // --- UTILITIES ---

    private void initializeState(Map<Integer, List<Integer>> preSchedule) {
        this.vmSchedule = new HashMap<>();
        this.taskToVM = new HashMap<>();

        // Deep copy schedule mapping
        for (Map.Entry<Integer, List<Integer>> entry : preSchedule.entrySet()) {
            this.vmSchedule.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            for (Integer tid : entry.getValue()) {
                this.taskToVM.put(tid, entry.getKey());
            }
        }

        // Calculate initial AST/AFT based on this schedule
        recalculateFullSchedule();
    }

    private void recalculateFullSchedule() {
        // Clean timing maps
        taskAST.clear();
        taskAFT.clear();

        // Topological execution to determine times
        List<task> sorted = topologicalSort();
        for (task t : sorted) {
            recalculateValidation(t.getID());
        }
    }

    // Calcola EST e AFT di un task specifico basandosi sui predecessori e
    // disponibilità VM
    private boolean recalculateValidation(int taskId) {
        task t = smgt.getTaskById(taskId);
        Integer vmId = taskToVM.get(taskId);
        if (vmId == null || t == null)
            return false;

        double oldAST = taskAST.getOrDefault(taskId, -1.0);
        double oldAFT = taskAFT.getOrDefault(taskId, -1.0);

        // 1. Data Ready Time (DRT) -> max(AFT_pred + Comm)
        double drt = 0.0;
        for (Integer predId : t.getPre()) {
            Double predFinish = taskAFT.get(predId);
            if (predFinish == null)
                continue;

            double commTime = 0.0;
            Integer predVM = taskToVM.get(predId);

            // CHECK DUPLICATES: Se esiste un duplicato del predecessore sulla MIA vm
            // (vmId), usa quello
            if (duplicates.containsKey(predId + "_" + vmId)) {
                TaskDuplicate dup = duplicates.get(predId + "_" + vmId);
                predFinish = dup.aft;
                commTime = 0.0;
            } else {
                if (predVM != null && !predVM.equals(vmId)) {
                    commTime = calculateTransmissionTime(predId, taskId, predVM, vmId);
                }
            }

            drt = Math.max(drt, predFinish + commTime);
        }

        // 2. Machine Ready Time (MRT) -> Fine dell'ultimo task eseguito su questa VM
        // prima di me
        double mrt = 0.0;
        List<Integer> taskList = vmSchedule.get(vmId);
        int myIndex = taskList.indexOf(taskId);
        if (myIndex > 0) {
            Integer prevTask = taskList.get(myIndex - 1);
            Double prevFin = taskAFT.get(prevTask);
            if (prevFin != null)
                mrt = prevFin;
        }

        double est = Math.max(drt, mrt);
        double et = calculateET(t, vmId);
        double aft = est + et;

        taskAST.put(taskId, est);
        taskAFT.put(taskId, aft);

        return Math.abs(oldAST - est) > 1e-6 || Math.abs(oldAFT - aft) > 1e-6;
    }

    private List<IdleSlot> findIdleSlots(int vmId) {
        List<IdleSlot> slots = new ArrayList<>();

        List<double[]> busyIntervals = new ArrayList<>();

        // 1. Task standard
        List<Integer> standardTasks = vmSchedule.getOrDefault(vmId, new ArrayList<>());
        for (Integer tid : standardTasks) {
            Double s = taskAST.get(tid);
            Double f = taskAFT.get(tid);
            if (s != null && f != null) {
                busyIntervals.add(new double[] { s, f });
            }
        }

        // 2. Duplicati già piazzati
        for (TaskDuplicate dup : duplicates.values()) {
            if (dup.vmId == vmId) {
                busyIntervals.add(new double[] { dup.ast, dup.aft });
            }
        }

        busyIntervals.sort((a, b) -> Double.compare(a[0], b[0]));

        // Scansiona per trovare buchi
        double currentTime = 0.0;
        for (double[] interval : busyIntervals) {
            if (interval[0] > currentTime + RULE2_EPSILON) {
                slots.add(new IdleSlot(currentTime, interval[0]));
            }
            currentTime = Math.max(currentTime, interval[1]);
        }

        slots.add(new IdleSlot(currentTime, Double.MAX_VALUE));

        return slots;
    }

    private List<Integer> getLevel1Tasks() {
        List<Integer> level1 = new ArrayList<>();
        for (task t : tasks) {
            if (t.getPre() == null || t.getPre().isEmpty()) {
                level1.add(t.getID());
            }
        }
        return level1;
    }

    private double calculateET(task t, int vmId) {
        VM target = null;
        for (VM v : vms)
            if (v.getID() == vmId) {
                target = v;
                break;
            }
        if (target == null)
            return t.getSize();

        double capacity = target.getCapability("processingCapacity");
        if (capacity <= 0)
            capacity = target.getCapability("processing");
        if (capacity <= 0)
            return t.getSize();

        return t.getSize() / capacity;
    }

    private double calculateTransmissionTime(int srcTaskId, int dstTaskId, int srcVM, int dstVM) {
        if (srcVM == dstVM)
            return 0.0;
        VM src = null;
        for (VM v : vms)
            if (v.getID() == srcVM) {
                src = v;
                break;
            }

        if (src == null)
            return 0.0;

        double bandwidth = src.getBandwidthToVM(dstVM);
        if (bandwidth <= 0)
            bandwidth = 25.0; // Default

        // communicationCosts stores time assuming avgBandwidth=25.
        // Convert back to an equivalent dataSize and re-apply the actual bandwidth.
        String commKey = srcTaskId + "_" + dstTaskId;
        double commTimeAtAvgBw = communicationCosts.getOrDefault(commKey, 0.0);
        return commTimeAtAvgBw * 25.0 / bandwidth;
    }

    private List<task> topologicalSort() {
        List<task> result = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();
        Set<Integer> visiting = new HashSet<>();
        for (task t : tasks) {
            if (!visited.contains(t.getID()))
                visit(t.getID(), visited, visiting, result);
        }
        Collections.reverse(result);
        return result;
    }

    private void visit(int u, Set<Integer> visited, Set<Integer> visiting, List<task> result) {
        if (visited.contains(u))
            return;
        visiting.add(u);
        task t = smgt.getTaskById(u);
        if (t != null) {
            for (Integer v : t.getSucc())
                visit(v, visited, visiting, result);
            result.add(t);
        }
        visited.add(u);
        visiting.remove(u);
    }

    private void printFinalSchedule() {
        for (Map.Entry<Integer, List<Integer>> entry : vmSchedule.entrySet()) {
            int vmId = entry.getKey();
            System.out.println("VM" + vmId + ": " + entry.getValue());
            if (duplicatedTasksOnVM.containsKey(vmId) && !duplicatedTasksOnVM.get(vmId).isEmpty()) {
                System.out.println("   + Duplicates: " + duplicatedTasksOnVM.get(vmId));
            }
        }
    }

    public Map<Integer, Set<Integer>> getDuplicatedTasks() {
        return duplicatedTasksOnVM;
    }

    public Map<Integer, Double> getTaskAST() {
        return taskAST;
    }

    public Map<Integer, Double> getTaskAFT() {
        return taskAFT;
    }
}
