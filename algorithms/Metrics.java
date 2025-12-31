import java.util.*;

/**
 * Utility class for workflow scheduling metrics.
 * 
 * STREAMLINED VERSION: Contains only actively-used methods.
 * Removed: FT, ST, Ttrans, MS, makespan (0 external usages)
 */
public class Metrics {

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
        double pk = vmK.getProcessingCapacity();
        if (pk <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        
        return ti.getSize() / pk;
    }

    /**
     * Scheduling Length Ratio (Paper Equation 7).
     * 
     * SLR = makespan / Σmin{ET(ti, VMk)} for tasks on the CRITICAL PATH only
     * 
     * The denominator is the sum of minimum execution times for critical path tasks,
     * representing the theoretical lower bound (critical path on fastest VM).
     * 
     * @param makespan The actual schedule makespan
     * @param criticalPathTasks Tasks on the critical path 
     * @param vms Available VMs
     */
    public static double SLR(double makespan, List<task> criticalPathTasks, Map<Integer, VM> vms) {
        double sumMinET = 0.0;
        for (task t : criticalPathTasks) {
            double minET = Double.POSITIVE_INFINITY;
            for (VM vm : vms.values()) minET = Math.min(minET, ET(t, vm, "processingCapacity"));
            if (minET != Double.POSITIVE_INFINITY) sumMinET += minET;
            else {
                // If any critical path task cannot be executed, SLR return error 
                throw new IllegalArgumentException(
                    "Cannot compute SLR: Task " + t.getID() + " cannot be executed on any VM.");

            }
        }
        if(sumMinET > 0) {
            return makespan / sumMinET;
        } else {
            throw new IllegalArgumentException("Cannot compute SLR: Sum of minimum ETs is zero.");
        }
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
     */
    
    public static double VF(List<task> tasks,
                            Map<Integer, VM> vms,
                            Map<Integer, List<task>> vmTaskAssignments,
                            String capabilityName) {

        // --------- NULL VALIDATION ---------
        if (tasks == null || vms == null || vmTaskAssignments == null) {
            throw new IllegalArgumentException("tasks, vms and vmTaskAssignments must not be null");
        }

        // Build map once O(m*k), then lookup in O(1) per task
        Map<Integer, VM> taskToVM = new HashMap<>();
        for (Map.Entry<Integer, List<task>> entry : vmTaskAssignments.entrySet()) {
            VM vm = vms.get(entry.getKey());
            if (vm == null || entry.getValue() == null) continue;

            for (task t : entry.getValue()) {
                if (t != null) {
                    taskToVM.put(t.getID(), vm);
                }
            }
        }

        List<Double> satisfactions = new ArrayList<>();

        for (task t : tasks) {
            if (t == null) continue;

            // O(1) lookup
            VM assignedVM = taskToVM.get(t.getID());
            if (assignedVM == null) continue;

            // --------- VM CAPABILITY VALIDATION ---------
            double vmCapability = assignedVM.getProcessingCapacity();
            if (!Double.isFinite(vmCapability) || vmCapability <= 0) {
                System.err.println("⚠️  Warning: VM " + assignedVM.getID()
                        + " has invalid capacity: " + vmCapability);
                continue;
            }

            // --------- ACTUAL EXECUTION TIME ---------
            double actualET = ET(t, assignedVM, capabilityName);
            if (!Double.isFinite(actualET) || actualET <= 0) {
                System.err.println("⚠️  Warning: Invalid actualET for task " + t.getID()
                        + " on VM " + assignedVM.getID() + ": " + actualET);
                continue;
            }

            // --------- FASTEST POSSIBLE EXECUTION TIME ---------
            double fastestET = Double.POSITIVE_INFINITY;
            for (VM vm : vms.values()) {
                if (vm == null) continue;

                double et = ET(t, vm, capabilityName);
                if (Double.isFinite(et) && et > 0) {
                    fastestET = Math.min(fastestET, et);
                }
            }

            if (Double.isFinite(fastestET) && fastestET > 0) {
                double satisfaction =  fastestET / actualET ;
                if (Double.isFinite(satisfaction)) {
                    satisfactions.add(satisfaction);
                }
            }
        }

        // --------- FINAL FAIRNESS VARIANCE ---------
        if (satisfactions.isEmpty()) return 0.0;

        double avg = satisfactions.stream()
                                .mapToDouble(Double::doubleValue)
                                .average()
                                .orElse(0.0);

        double sumSq = 0.0;
        for (double s : satisfactions) {
            double d = avg - s;
            sumSq += d * d;
        }

        // population variance
        return avg;
    }


}
