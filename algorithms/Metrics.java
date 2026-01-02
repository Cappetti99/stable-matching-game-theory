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
    public static double ET(task ti, VM vmK) {
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
            for (VM vm : vms.values()) minET = Math.min(minET, ET(t, vm));
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
    public static double VU(VM vmK, List<task> assignedTasks, double makespan) {
        if (makespan <= 0 || assignedTasks == null || assignedTasks.isEmpty()) return 0.0;
        double sumET = 0.0;
        for (task t : assignedTasks) {
            double et = ET(t, vmK);
            if (et != Double.POSITIVE_INFINITY) sumET += et;
        }
        return sumET / makespan;
    }

    /**
     * Average VM Utilization.
     */
    public static double AVU(Map<Integer, VM> vms, Map<Integer, List<task>> vmTaskAssignments,
                             double makespan) {
        if (vms == null || vms.isEmpty() || makespan <= 0) return 0.0;
        double sumVU = 0.0;
        int count = 0;
        for (VM vm : vms.values()) {
            List<task> tasks = vmTaskAssignments.getOrDefault(vm.getID(), new ArrayList<>());
            sumVU += VU(vm, tasks, makespan);
            count++;
        }
        return count > 0 ? sumVU / count : 0.0;
    }

    /**
     * Variance of Fairness.
     */
    
    public static double VF(List<task> tasks,
                            Map<Integer, VM> vms,
                            Map<Integer, List<task>> vmTaskAssignments) {

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
            double actualET = ET(t, assignedVM);
            if (!Double.isFinite(actualET) || actualET <= 0) {
                System.err.println("⚠️  Warning: Invalid actualET for task " + t.getID()
                        + " on VM " + assignedVM.getID() + ": " + actualET);
                continue;
            }

            // --------- FASTEST POSSIBLE EXECUTION TIME ---------
            double fastestET = Double.POSITIVE_INFINITY;
            for (VM vm : vms.values()) {
                if (vm == null) continue;

                double et = ET(t, vm);
                if (Double.isFinite(et) && et > 0) {
                    fastestET = Math.min(fastestET, et);
                }
            }

            if (Double.isFinite(fastestET) && fastestET > 0) {
                double satisfaction =  actualET / fastestET  ;
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
        return sumSq/satisfactions.size();
    }

    /**
     * Average Satisfaction - Mean of task satisfaction levels.
     * 
     * Measures the average satisfaction of tasks in the schedule, where each task's
     * satisfaction is the ratio of its actual execution time to the fastest possible
     * execution time across all VMs.
     * 
     * Formula:
     *   satisfaction(task_i) = actualET(task_i) / fastestET(task_i)
     *   AvgSatisfaction = Σ(satisfaction_i) / n
     * 
     * Where:
     *   - actualET = execution time on assigned VM = task.size / assignedVM.capacity
     *   - fastestET = minimum execution time across all VMs = task.size / max(VM.capacity)
     *   - n = number of tasks
     * 
     * Interpretation:
     *   - AvgSatisfaction = 1.0: Perfect - all tasks run on their fastest VM
     *   - AvgSatisfaction > 1.0: Some tasks run on slower VMs (higher = less optimal)
     *   - Lower values are better (closer to 1.0 = more efficient allocation)
     * 
     * @param tasks List of all workflow tasks
     * @param vms Map of available VMs (vmID → VM object)
     * @param vmTaskAssignments Task-to-VM assignments (vmID → list of tasks)
     * @return Average satisfaction (≥ 1.0, or 0.0 if no valid tasks)
     */
    public static double AvgSatisfaction(List<task> tasks,
                                         Map<Integer, VM> vms,
                                         Map<Integer, List<task>> vmTaskAssignments) {

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
            double actualET = ET(t, assignedVM);
            if (!Double.isFinite(actualET) || actualET <= 0) {
                System.err.println("⚠️  Warning: Invalid actualET for task " + t.getID()
                        + " on VM " + assignedVM.getID() + ": " + actualET);
                continue;
            }

            // --------- FASTEST POSSIBLE EXECUTION TIME ---------
            double fastestET = Double.POSITIVE_INFINITY;
            for (VM vm : vms.values()) {
                if (vm == null) continue;

                double et = ET(t, vm);
                if (Double.isFinite(et) && et > 0) {
                    fastestET = Math.min(fastestET, et);
                }
            }

            if (Double.isFinite(fastestET) && fastestET > 0) {
                double satisfaction = actualET / fastestET;
                if (Double.isFinite(satisfaction)) {
                    satisfactions.add(satisfaction);
                }
            }
        }

        // --------- COMPUTE AVERAGE SATISFACTION ---------
        if (satisfactions.isEmpty()) return 0.0;

        double avg = satisfactions.stream()
                                .mapToDouble(Double::doubleValue)
                                .average()
                                .orElse(0.0);

        return avg;
    }

    /**
     * Communication Cost Calculator
     * 
     * Calculates the communication cost between two tasks when executed on different VMs.
     * The cost is based on data transfer size and inter-VM bandwidth.
     */
    public static class CommunicationCostCalculator {
        
        /**
         * Calculate communication cost between two tasks on different VMs.
         * 
         * Formula: cost = (taskSize × CCR) / bandwidth
         * 
         * @param srcTask Source task (sender)
         * @param dstTask Destination task (receiver) - not used but kept for API consistency
         * @param srcVM Source VM where srcTask executes
         * @param dstVM Destination VM where dstTask executes
         * @param ccr Communication-to-Computation Ratio (multiplier for data size)
         * @return Communication time cost (0.0 if same VM)
         */
        public static double calculate(task srcTask, task dstTask, 
                                       VM srcVM, VM dstVM, double ccr) {
            if (srcVM == null || dstVM == null) {
                return 0.0;
            }
            
            if (srcVM.getID() == dstVM.getID()) {
                return 0.0; // Same VM, no communication cost
            }
            
            double dataSize = srcTask.getSize() * ccr;
            double bandwidth = srcVM.getBandwidthToVM(dstVM.getID());
            
            if (bandwidth <= 0) {
                throw new IllegalStateException(
                    "Invalid bandwidth between VM" + srcVM.getID() + 
                    " and VM" + dstVM.getID() + ": " + bandwidth);
            }
            
            return dataSize / bandwidth;
        }
        
        /**
         * Calculate average communication cost over all VM pairs.
         * 
         * Used for DCP rank calculation where tasks aren't assigned yet.
         * 
         * Formula: avgCost = Σ[(taskSize × CCR) / B(k,l)] / (m × (m-1))
         * where m = number of VMs, and k ≠ l
         * 
         * @param srcTask Source task
         * @param dstTask Destination task (not used but kept for consistency)
         * @param vms List of all available VMs
         * @param ccr Communication-to-Computation Ratio
         * @return Average communication cost across all VM pairs
         */
        public static double calculateAverage(task srcTask, task dstTask,
                                             List<VM> vms, double ccr) {
            if (vms == null || vms.size() < 2) {
                return 0.0;
            }
            
            double dataSize = srcTask.getSize() * ccr;
            double sumCosts = 0.0;
            int validPairs = 0;
            
            for (VM vmK : vms) {
                for (VM vmL : vms) {
                    if (vmK.getID() == vmL.getID()) continue; // Skip same VM
                    
                    double bandwidth = vmK.getBandwidthToVM(vmL.getID());
                    if (bandwidth <= 0) {
                        throw new IllegalStateException(
                            "Invalid bandwidth between VM" + vmK.getID() + 
                            " and VM" + vmL.getID() + ": " + bandwidth);
                    }
                    
                    sumCosts += dataSize / bandwidth;
                    validPairs++;
                }
            }
            
            return validPairs > 0 ? sumCosts / validPairs : 0.0;
        }
    }

}
