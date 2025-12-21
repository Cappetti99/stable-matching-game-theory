import java.util.*;

/**
 * Utility class for workflow scheduling metrics.
 */
public class Metrics {

    /** Default Communication-to-Computation Ratio */
    public static final double DEFAULT_CCR = 0.4;

    /**
     * Transmission time between two tasks.
     * 
     * VALIDATED: Returns 0 if same VM, POSITIVE_INFINITY if invalid bandwidth
     */
    public static double Ttrans(task ti, task tj, VM vmK, VM vmL, double CCR) {
        if (vmK.getID() == vmL.getID()) return 0.0;
        
        // VALIDATION: Check for zero/negative task size
        double taskSize = ti.getSize();
        if (taskSize <= 0) return 0.0;
        
        double dataSize = taskSize * CCR;
        double bandwidth = vmK.getBandwidthToVM(vmL.getID());
        
        // VALIDATION: Check for zero/negative bandwidth
        if (bandwidth <= 0) return Double.POSITIVE_INFINITY;
        
        return dataSize / bandwidth;
    }

    public static double Ttrans(task ti, task tj, VM vmK, VM vmL) {
        return Ttrans(ti, tj, vmK, vmL, DEFAULT_CCR);
    }

    /**
     * Start time of a task on a VM.
     */
    public static double ST(task ti, VM vmK,
                            Map<Integer, Double> taskFinishTimes,
                            Map<Integer, VM> taskVMAssignments,
                            Map<Integer, task> allTasks,
                            double CCR) {

        if (ti.getPre() == null || ti.getPre().isEmpty()) return 0.0;

        double maxStartTime = 0.0;

        for (Integer predId : ti.getPre()) {
            Double ftj = taskFinishTimes.get(predId);
            task tj = allTasks.get(predId);
            VM vmL = taskVMAssignments.get(predId);
            if (ftj == null || tj == null || vmL == null) continue;

            double potentialStart = ftj + Ttrans(tj, ti, vmL, vmK, CCR);
            maxStartTime = Math.max(maxStartTime, potentialStart);
        }
        return maxStartTime;
    }

    public static double ST(task ti, VM vmK) {
        return 0.0;
    }

    /**
     * Execution time of a task on a VM.
     * 
     * VALIDATED: Returns POSITIVE_INFINITY if VM capacity ≤ 0 or task size ≤ 0
     */
    public static double ET(task ti, VM vmK, String capabilityType) {
        // VALIDATION: Check task size
        if (ti.getSize() <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        
        // VALIDATION: Check VM capability
        double pk = vmK.getCapability(capabilityType);
        if (pk <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        
        return ti.getSize() / pk;
    }

    public static double ET(task ti, VM vmK) {
        // VALIDATION: Check task size
        if (ti.getSize() <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        
        Map<String, Double> caps = vmK.getProcessingCapabilities();
        if (caps.isEmpty()) return Double.POSITIVE_INFINITY;
        
        double pk = caps.values().iterator().next();
        
        // VALIDATION: Check VM capability
        if (pk <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        
        return ti.getSize() / pk;
    }

    /**
     * Finish time of a task on a VM.
     */
    public static double FT(task ti, VM vmK, String capabilityType,
                            Map<Integer, Double> taskFinishTimes,
                            Map<Integer, VM> taskVMAssignments,
                            Map<Integer, task> allTasks,
                            double CCR) {
        return ST(ti, vmK, taskFinishTimes, taskVMAssignments, allTasks, CCR)
                + ET(ti, vmK, capabilityType);
    }

    public static double FT(task ti, VM vmK) {
        return (ti.getPre() == null || ti.getPre().isEmpty())
                ? ET(ti, vmK)
                : Double.POSITIVE_INFINITY;
    }

    public static double FT(task ti, VM vmK, String capabilityType) {
        return (ti.getPre() == null || ti.getPre().isEmpty())
                ? ET(ti, vmK, capabilityType)
                : Double.POSITIVE_INFINITY;
    }

    /**
     * Makespan of a VM.
     */
    public static double MS(VM vmK, List<task> assignedTasks, Map<Integer, Double> taskFinishTimes) {
        double maxFinishTime = 0.0;
        for (task t : assignedTasks) {
            Double ft = taskFinishTimes.get(t.getID());
            if (ft != null) maxFinishTime = Math.max(maxFinishTime, ft);
        }
        return maxFinishTime;
    }

    /**
     * Makespan of the entire workflow.
     */
    public static double makespan(Map<Integer, VM> vms,
                                  Map<Integer, List<task>> vmTaskAssignments,
                                  Map<Integer, Double> taskFinishTimes) {
        double max = 0.0;
        for (VM vm : vms.values()) {
            List<task> tasks = vmTaskAssignments.getOrDefault(vm.getID(), new ArrayList<>());
            max = Math.max(max, MS(vm, tasks, taskFinishTimes));
        }
        return max;
    }

    /**
     * Scheduling Length Ratio.
     */
    public static double SLR(double makespan, List<task> criticalPathTasks, Map<Integer, VM> vms) {
        double sumMinET = 0.0;
        for (task t : criticalPathTasks) {
            double minET = Double.POSITIVE_INFINITY;
            for (VM vm : vms.values()) minET = Math.min(minET, ET(t, vm));
            if (minET != Double.POSITIVE_INFINITY) sumMinET += minET;
        }
        return (sumMinET > 0) ? makespan / sumMinET : Double.POSITIVE_INFINITY;
    }

    /**
     * VM Utilization.
     */
    public static double VU(VM vmK, List<task> assignedTasks, double makespan, String capabilityName) {
        if (makespan <= 0 || assignedTasks == null || assignedTasks.isEmpty()) return 0.0;
        double sumET = 0.0;
        for (task t : assignedTasks) {
            double et = ET(t, vmK, capabilityName);
            if (et != Double.POSITIVE_INFINITY) sumET += et;
        }
        return sumET / makespan;
    }

    /**
     * Average VM Utilization.
     */
    public static double AVU(Map<Integer, VM> vms, Map<Integer, List<task>> vmTaskAssignments,
                             double makespan, String capabilityName) {
        if (vms == null || vms.isEmpty() || makespan <= 0) return 0.0;
        double sumVU = 0.0;
        int count = 0;
        for (VM vm : vms.values()) {
            List<task> tasks = vmTaskAssignments.getOrDefault(vm.getID(), new ArrayList<>());
            sumVU += VU(vm, tasks, makespan, capabilityName);
            count++;
        }
        return count > 0 ? sumVU / count : 0.0;
    }

    /**
     * Variance of Fairness.
     * 
     * OPTIMIZED VERSION - O(n*m*k) → O(n*m)
     * Pre-builds reverse lookup map for O(1) task-to-VM assignment lookup.
     */
    public static double VF(List<task> tasks, Map<Integer, VM> vms,
                            Map<Integer, List<task>> vmTaskAssignments,
                            Map<Integer, Double> taskFinishTimes,
                            String capabilityName) {

        // OPTIMIZATION: Pre-build reverse lookup map: task → VM
        // OLD: For each task, search through all VM assignments O(n*m*k)
        // NEW: Build map once O(m*k), then lookup in O(1) per task
        Map<Integer, VM> taskToVM = new HashMap<>();
        for (Map.Entry<Integer, List<task>> entry : vmTaskAssignments.entrySet()) {
            VM vm = vms.get(entry.getKey());
            if (vm != null) {
                for (task t : entry.getValue()) {
                    taskToVM.put(t.getID(), vm);
                }
            }
        }

        List<Double> satisfactions = new ArrayList<>();
        for (task t : tasks) {
            // O(1) lookup instead of O(m*k) search
            VM assignedVM = taskToVM.get(t.getID());
            if (assignedVM == null) continue;

            // VALIDATION: Check for zero/negative VM capacity
            double vmCapability = assignedVM.getCapability(capabilityName);
            if (vmCapability <= 0) {
                System.err.println("⚠️  Warning: VM " + assignedVM.getID() + 
                                   " has invalid capacity: " + vmCapability);
                continue;
            }

            double actualET = ET(t, assignedVM, capabilityName);
            
            // VALIDATION: Check for invalid actualET
            if (actualET == Double.POSITIVE_INFINITY || actualET <= 0) {
                System.err.println("⚠️  Warning: Invalid actualET for task " + t.getID() + 
                                   " on VM " + assignedVM.getID() + ": " + actualET);
                continue;
            }

            double fastestET = Double.POSITIVE_INFINITY;
            for (VM vm : vms.values()) {
                double et = ET(t, vm, capabilityName);
                if (et != Double.POSITIVE_INFINITY && et > 0) {
                    fastestET = Math.min(fastestET, et);
                }
            }

            if (fastestET > 0 && fastestET != Double.POSITIVE_INFINITY)
                satisfactions.add(actualET / fastestET);
        }

        if (satisfactions.isEmpty()) return 0.0;

        double avg = satisfactions.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double sumSq = 0.0;
        for (double s : satisfactions) sumSq += (avg - s) * (avg - s);
        return sumSq / satisfactions.size();
    }

}
