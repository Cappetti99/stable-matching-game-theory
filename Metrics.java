public class Metrics {
    
    /**
     * Calculates the transmission time between two tasks.
     * Formula: Ttrans(ti, tj) = TTi,j / B(VMk, VMl)
     * where TTi,j = si * CCR (si = size of source task, CCR = Communication-to-Computation Ratio)
     * 
     * Special cases:
     * - Ttrans(ti, tj) = 0 if the two tasks are executed on the same VM
     * - B(VMk, VMl) = 0 if k = l (bandwidth to itself is 0)
     * 
     * @param ti The source task
     * @param tj The destination task
     * @param vmK The VM hosting task ti
     * @param vmL The VM hosting task tj
     * @param CCR The Communication-to-Computation Ratio (default 0.4)
     * @return The transmission time
     */
    public static double Ttrans(task ti, task tj, VM vmK, VM vmL, double CCR) {
        // Special case: If both tasks are on the same VM, no transmission needed
        if (vmK.getID() == vmL.getID()) {
            return 0.0;
        }
        
        // Calculate TTi,j = si * CCR (data size to transmit)
        double dataSize = ti.getSize() * CCR;
        
        // Get the bandwidth between VM k and VM l
        double bandwidth = vmK.getBandwidthToVM(vmL.getID());
        
        // Special case: B(VMk, VMl) = 0 if k = l (should already be handled above, but safety check)
        if (vmK.getID() == vmL.getID()) {
            bandwidth = 0.0;
        }
        
        // If there's no bandwidth configured or bandwidth is 0, return infinity
        if (bandwidth <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        
        // Calculate transmission time: TTi,j / B(VMk, VMl)
        return dataSize / bandwidth;
    }
    
    /**
     * Overloaded method that uses default CCR = 0.4
     * 
     * @param ti The source task
     * @param tj The destination task
     * @param vmK The VM hosting task ti
     * @param vmL The VM hosting task tj
     * @return The transmission time using CCR = 0.4
     */
    public static double Ttrans(task ti, task tj, VM vmK, VM vmL) {
        return Ttrans(ti, tj, vmK, vmL, 0.4);
    }
    
    /**
     * Calculates the start time of a task on a VM.
     * Formula: ST(ti, VMk) = {
     *   0                                    if ti = tentry
     *   max{FTj + Ttrans(tj, ti)}           if ti ≠ tentry, for all tj ∈ pre(ti)
     * }
     * 
     * @param ti The task for which to calculate start time
     * @param vmK The VM hosting the task
     * @param isEntryTask Whether this task is an entry task (has no predecessors)
     * @param predecessorFinishTimes Map of predecessor task IDs to their finish times
     * @param predecessorTasks Map of predecessor task IDs to their task objects
     * @param predecessorVMs Map of predecessor task IDs to their hosting VMs
     * @param dataTransferSizes Map of predecessor task IDs to data transfer sizes to current task
     * @return The start time of the task on the VM
     */
    public static double ST(task ti, VM vmK, boolean isEntryTask, 
                           java.util.Map<Integer, Double> predecessorFinishTimes,
                           java.util.Map<Integer, task> predecessorTasks,
                           java.util.Map<Integer, VM> predecessorVMs,
                           java.util.Map<Integer, Double> dataTransferSizes) {
        
        // If this is an entry task (tentry), start time is 0
        if (isEntryTask || ti.getPre().isEmpty()) {
            return 0.0;
        }
        
        double maxStartTime = 0.0;
        
        // For each predecessor task tj in pre(ti)
        for (Integer tjId : ti.getPre()) {
            // Get finish time of predecessor task tj
            Double ftj = predecessorFinishTimes.get(tjId);
            if (ftj == null) {
                continue; // Skip if finish time not available
            }
            
            // Get predecessor task object
            task tj = predecessorTasks.get(tjId);
            if (tj == null) {
                continue; // Skip if task object not available
            }
            
            // Get VM hosting predecessor task tj
            VM vmL = predecessorVMs.get(tjId);
            if (vmL == null) {
                continue; // Skip if VM not available
            }
            
            // Get data transfer size from tj to ti
            Double dataSize = dataTransferSizes.get(tjId);
            if (dataSize == null) {
                dataSize = 0.0; // Default to 0 if no data transfer
            }
            
            // Calculate transmission time from tj to ti
            double ttrans = Ttrans(tj, ti, vmL, vmK, 0.4);
            
            // Calculate potential start time: FTj + Ttrans(tj, ti)
            double potentialStartTime = ftj + ttrans;
            
            // Keep track of maximum
            maxStartTime = Math.max(maxStartTime, potentialStartTime);
        }
        
        return maxStartTime;
    }
    
    /**
     * Simplified version of ST calculation for entry tasks
     * 
     * @param ti The task
     * @param vmK The VM hosting the task
     * @return 0.0 for entry tasks
     */
    public static double ST(task ti, VM vmK) {
        // Assume this is an entry task if no other parameters provided
        return 0.0;
    }
    
    /**
     * Calculates the execution time of a task on a VM.
     * Formula: ET(ti, VMk) = si / pk
     * where si is the size of the task and pk is the processing capacity of VMk
     * 
     * @param ti The task to execute
     * @param vmK The VM hosting the task
     * @param capabilityType The type of capability required by the task
     * @return The execution time of the task on the VM
     */
    public static double ET(task ti, VM vmK, String capabilityType) {
        // Get the size of the task (si)
        double taskSize = ti.getSize();
        
        // Get the processing capacity of VMk for the required capability (pk)
        double processingCapacity = vmK.getCapability(capabilityType);
        
        // If processing capacity is 0 or VM doesn't have the capability, return infinity
        if (processingCapacity <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        
        // Calculate execution time: si / pk
        return taskSize / processingCapacity;
    }
    
    /**
     * Overloaded method that uses a default capability type
     * 
     * @param ti The task to execute
     * @param vmK The VM hosting the task
     * @return The execution time assuming a default processing capability
     */
    public static double ET(task ti, VM vmK) {
        // Get the size of the task (si)
        double taskSize = ti.getSize();
        
        // Get the first available processing capability as default
        var capabilities = vmK.getProcessingCapabilities();
        if (capabilities.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        
        // Use the first available capability
        double processingCapacity = capabilities.values().iterator().next();
        
        // If processing capacity is 0, return infinity
        if (processingCapacity <= 0) {
            return Double.POSITIVE_INFINITY;
        }
        
        // Calculate execution time: si / pk
        return taskSize / processingCapacity;
    }
    
    /**
     * Calculates the finish time of a task on a VM.
     * Formula: FT(ti, VMk) = ST(ti, VMk) + ET(ti, VMk)
     * 
     * @param ti The task
     * @param vmK The VM hosting the task
     * @param capabilityType The type of capability required by the task
     * @param isEntryTask Whether this task is an entry task
     * @param predecessorFinishTimes Map of predecessor task IDs to their finish times
     * @param predecessorTasks Map of predecessor task IDs to their task objects
     * @param predecessorVMs Map of predecessor task IDs to their hosting VMs
     * @param dataTransferSizes Map of predecessor task IDs to data transfer sizes to current task
     * @return The finish time of the task on the VM
     */
    public static double FT(task ti, VM vmK, String capabilityType, boolean isEntryTask,
                           java.util.Map<Integer, Double> predecessorFinishTimes,
                           java.util.Map<Integer, task> predecessorTasks,
                           java.util.Map<Integer, VM> predecessorVMs,
                           java.util.Map<Integer, Double> dataTransferSizes) {
        
        // Calculate start time ST(ti, VMk)
        double startTime = ST(ti, vmK, isEntryTask, predecessorFinishTimes, predecessorTasks, predecessorVMs, dataTransferSizes);
        
        // Calculate execution time ET(ti, VMk)
        double executionTime = ET(ti, vmK, capabilityType);
        
        // Calculate finish time: FT(ti, VMk) = ST(ti, VMk) + ET(ti, VMk)
        return startTime + executionTime;
    }
    
    /**
     * Simplified version of FT calculation - automatically detects if task is entry or has predecessors
     * 
     * @param ti The task
     * @param vmK The VM hosting the task
     * @return The finish time
     */
    public static double FT(task ti, VM vmK) {
        // Check if this is an entry task (no predecessors)
        if (ti.getPre() == null || ti.getPre().isEmpty()) {
            // For entry tasks, start time is 0
            double startTime = 0.0;
            
            // Calculate execution time with default capability
            double executionTime = ET(ti, vmK);
            
            // Calculate finish time: FT(ti, VMk) = ST(ti, VMk) + ET(ti, VMk)
            return startTime + executionTime;
        } else {
            // Task has predecessors - cannot calculate FT without predecessor information
            System.err.println("Warning: Cannot calculate FT for task " + ti.getID() + 
                             " with predecessors " + ti.getPre() + " without predecessor finish times and VM assignments.");
            return Double.POSITIVE_INFINITY;
        }
    }
    
    /**
     * Calculates finish time with specified capability type - automatically detects if task is entry or has predecessors
     * 
     * @param ti The task
     * @param vmK The VM hosting the task
     * @param capabilityType The type of capability required by the task
     * @return The finish time
     */
    public static double FT(task ti, VM vmK, String capabilityType) {
        // Check if this is an entry task (no predecessors)
        if (ti.getPre() == null || ti.getPre().isEmpty()) {
            // For entry tasks, start time is 0
            double startTime = 0.0;
            
            // Calculate execution time with specified capability
            double executionTime = ET(ti, vmK, capabilityType);
            
            // Calculate finish time: FT(ti, VMk) = ST(ti, VMk) + ET(ti, VMk)
            return startTime + executionTime;
        } else {
            // Task has predecessors - cannot calculate FT without predecessor information
            System.err.println("Warning: Cannot calculate FT for task " + ti.getID() + 
                             " with predecessors " + ti.getPre() + " without predecessor finish times and VM assignments.");
            return Double.POSITIVE_INFINITY;
        }
    }
    
    /**
     * Utility method to calculate FT for a task with predecessors using simulated predecessor data
     * This is useful for testing or when you have limited predecessor information
     * 
     * @param ti The task
     * @param vmK The VM hosting the task
     * @param capabilityType The type of capability required by the task
     * @param defaultPredecessorFT Default finish time to assume for all predecessors
     * @param defaultPredecessorVM Default VM to assume for all predecessors
     * @return The finish time
     */
    public static double FT_withSimulatedPredecessors(task ti, VM vmK, String capabilityType, 
                                                     double defaultPredecessorFT, VM defaultPredecessorVM) {
        // Check if this is an entry task
        if (ti.getPre() == null || ti.getPre().isEmpty()) {
            return FT(ti, vmK, capabilityType);
        }
        
        // Create simulated maps for predecessors
        java.util.Map<Integer, Double> predecessorFinishTimes = new java.util.HashMap<>();
        java.util.Map<Integer, task> predecessorTasks = new java.util.HashMap<>();
        java.util.Map<Integer, VM> predecessorVMs = new java.util.HashMap<>();
        java.util.Map<Integer, Double> dataTransferSizes = new java.util.HashMap<>();
        
        // Simulate data for all predecessors
        for (Integer predId : ti.getPre()) {
            predecessorFinishTimes.put(predId, defaultPredecessorFT);
            // Create a dummy task for the predecessor
            task dummyPredTask = new task(predId);
            dummyPredTask.setSize(40.0); // Default size
            predecessorTasks.put(predId, dummyPredTask);
            predecessorVMs.put(predId, defaultPredecessorVM);
            dataTransferSizes.put(predId, 16.0); // Default data transfer size
        }
        
        // Use the complete FT method
        return FT(ti, vmK, capabilityType, false, predecessorFinishTimes, predecessorTasks, predecessorVMs, dataTransferSizes);
    }
    
    /**
     * Calculates the makespan of a VM, which is the maximum completion time of all tasks assigned to that VM.
     * 
     * @param vmK The VM for which to calculate the makespan
     * @param assignedTasks List of tasks assigned to this VM
     * @param taskFinishTimes Map of task IDs to their finish times on this VM
     * @return The makespan of the VM (MS(VMk))
     */
    public static double MS(VM vmK, java.util.List<task> assignedTasks, 
                           java.util.Map<Integer, Double> taskFinishTimes) {
        double maxFinishTime = 0.0;
        
        // Find the maximum finish time among all tasks assigned to this VM
        for (task t : assignedTasks) {
            Double finishTime = taskFinishTimes.get(t.getID());
            if (finishTime != null) {
                maxFinishTime = Math.max(maxFinishTime, finishTime);
            }
        }
        
        return maxFinishTime;
    }
    
    /**
     * Calculates the makespan of the entire workflow.
     * The makespan equals the maximum completion time of all tasks in the workflow.
     * Formula: makespan = max(MS(VMk)), 0 ≤ k ≤ m-1
     * where MS(VMk) denotes the makespan of VMk
     * 
     * @param vms Map of all VMs in the system
     * @param vmTaskAssignments Map of VM IDs to lists of tasks assigned to each VM
     * @param taskFinishTimes Map of task IDs to their finish times
     * @return The makespan of the workflow
     */
    public static double makespan(java.util.Map<Integer, VM> vms,
                                 java.util.Map<Integer, java.util.List<task>> vmTaskAssignments,
                                 java.util.Map<Integer, Double> taskFinishTimes) {
        double workflowMakespan = 0.0;
        
        // Calculate makespan for each VM and find the maximum
        for (VM vm : vms.values()) {
            java.util.List<task> assignedTasks = vmTaskAssignments.getOrDefault(vm.getID(), new java.util.ArrayList<>());
            double vmMakespan = MS(vm, assignedTasks, taskFinishTimes);
            workflowMakespan = Math.max(workflowMakespan, vmMakespan);
        }
        
        return workflowMakespan;
    }
    
    /**
     * Calculates the Scheduling Length Ratio (SLR).
     * SLR normalizes the makespan to avoid large differences caused by different parameters.
     * Formula: SLR = makespan / ∑|CP|−1 i=0 (min∑m−1 k=0 ET(ti, VMk))
     * 
     * @param makespan The makespan of the workflow
     * @param criticalPathTaskIds Set of task IDs in the critical path
     * @param allTasks Map of all tasks (taskId -> task object)
     * @param vms Map of all available VMs
     * @param capabilityName The name of the capability to use for execution time calculation
     * @return The Scheduling Length Ratio
     */
    public static double SLR(double makespan, 
                            java.util.Set<Integer> criticalPathTaskIds, 
                            java.util.Map<String, task> allTasks,
                            java.util.Map<Integer, VM> vms, 
                            String capabilityName) {
        
        if (criticalPathTaskIds == null || criticalPathTaskIds.isEmpty()) {
            return Double.POSITIVE_INFINITY; // Invalid critical path
        }
        
        // Convert task IDs to task objects and calculate sum of minimum execution times
        double sumMinExecutionTimes = 0.0;
        
        for (Integer taskId : criticalPathTaskIds) {
            task cpTask = allTasks.get("t" + taskId);
            if (cpTask != null) {
                double minET = Double.POSITIVE_INFINITY;
                
                // Find minimum execution time across all VMs for this task
                for (VM vm : vms.values()) {
                    double et = ET(cpTask, vm, capabilityName);
                    minET = Math.min(minET, et);
                }
                
                // Add to sum (handle edge case where no valid ET found)
                if (minET != Double.POSITIVE_INFINITY) {
                    sumMinExecutionTimes += minET;
                }
            }
        }
        
        // Avoid division by zero
        if (sumMinExecutionTimes == 0.0) {
            return Double.POSITIVE_INFINITY;
        }
        
        return makespan / sumMinExecutionTimes;
    }
    
    /**
     * Calculates the VM Utilization (VU) for a specific VM.
     * Formula: VU(VMk) = ∑ET(ti,VMk) / makespan, i ∈ VMk.waiting
     * where VMk.waiting represents the tasks assigned to VMk
     * 
     * @param vmK The VM for which to calculate utilization
     * @param assignedTasks List of tasks assigned to this VM
     * @param makespan The makespan of the workflow
     * @param capabilityName The name of the capability to use for execution time calculation
     * @return The utilization rate of VMk (between 0.0 and 1.0)
     */
    public static double VU(VM vmK, 
                           java.util.List<task> assignedTasks, 
                           double makespan, 
                           String capabilityName) {
        
        if (makespan <= 0.0 || assignedTasks == null || assignedTasks.isEmpty()) {
            return 0.0; // No utilization if no makespan or no tasks
        }
        
        // Calculate sum of execution times for all tasks assigned to this VM
        double sumExecutionTimes = 0.0;
        
        for (task t : assignedTasks) {
            double et = ET(t, vmK, capabilityName);
            if (et != Double.POSITIVE_INFINITY) {
                sumExecutionTimes += et;
            }
        }
        
        // VU(VMk) = ∑ET(ti,VMk) / makespan
        return sumExecutionTimes / makespan;
    }
    
    /**
     * Calculates the Average VM Utilization (AVU) across all VMs in the system.
     * AVU is the average of VU(VMk) for all VMs.
     * 
     * @param vms Map of all VMs in the system
     * @param vmTaskAssignments Map of VM IDs to lists of tasks assigned to each VM
     * @param makespan The makespan of the workflow
     * @param capabilityName The name of the capability to use for execution time calculation
     * @return The Average VM Utilization (between 0.0 and 1.0)
     */
    public static double AVU(java.util.Map<Integer, VM> vms,
                            java.util.Map<Integer, java.util.List<task>> vmTaskAssignments,
                            double makespan,
                            String capabilityName) {
        
        if (vms == null || vms.isEmpty() || makespan <= 0.0) {
            return 0.0; // No utilization if no VMs or invalid makespan
        }
        
        double sumUtilizations = 0.0;
        int vmCount = 0;
        
        // Calculate VU for each VM and sum them
        for (VM vm : vms.values()) {
            java.util.List<task> assignedTasks = vmTaskAssignments.getOrDefault(vm.getID(), new java.util.ArrayList<>());
            double vu = VU(vm, assignedTasks, makespan, capabilityName);
            sumUtilizations += vu;
            vmCount++;
        }
        
        // AVU = (∑VU(VMk)) / m, where m is the number of VMs
        return vmCount > 0 ? sumUtilizations / vmCount : 0.0;
    }
    
    /**
     * Calculates the Variance of Fairness (VF) metric.
     * VF measures the fairness between tasks based on their satisfaction.
     * 
     * The satisfaction of each task Si is calculated as: Si = AETi / EETi
     * where:
     * - AETi is the actual execution time of task i
     * - EETi is the expected execution time of task i on the fastest VM
     * 
     * VF is formulated as: VF = (1/n) * ∑(M - Si)²
     * where:
     * - n is the number of tasks
     * - M is the average of all task satisfaction values
     * - Si is the satisfaction of task i
     * 
     * @param tasks List of all tasks in the workflow
     * @param vms Map of all VMs in the system
     * @param vmTaskAssignments Map of VM IDs to lists of tasks assigned to each VM
     * @param taskFinishTimes Map of task IDs to their actual finish times
     * @param capabilityName The name of the capability to use for execution time calculation
     * @return The Variance of Fairness value (lower values indicate better fairness)
     */
    public static double VF(java.util.List<task> tasks,
                           java.util.Map<Integer, VM> vms,
                           java.util.Map<Integer, java.util.List<task>> vmTaskAssignments,
                           java.util.Map<Integer, Double> taskFinishTimes,
                           String capabilityName) {
        
        if (tasks == null || tasks.isEmpty() || vms == null || vms.isEmpty()) {
            return 0.0; // No variance if no tasks or VMs
        }
        
        java.util.List<Double> satisfactions = new java.util.ArrayList<>();
        
        // Calculate satisfaction for each task
        for (task t : tasks) {
            // Find the VM this task was assigned to
            VM assignedVM = null;
            for (java.util.Map.Entry<Integer, java.util.List<task>> entry : vmTaskAssignments.entrySet()) {
                if (entry.getValue().contains(t)) {
                    assignedVM = vms.get(entry.getKey());
                    break;
                }
            }
            
            if (assignedVM == null) {
                continue; // Skip tasks that are not assigned to any VM
            }
            
            // Calculate AETi (Actual Execution Time)
            double actualET = ET(t, assignedVM, capabilityName);
            
            // Calculate EETi (Expected Execution Time on fastest VM)
            double fastestET = Double.POSITIVE_INFINITY;
            for (VM vm : vms.values()) {
                double et = ET(t, vm, capabilityName);
                if (et < fastestET) {
                    fastestET = et;
                }
            }
            
            // Calculate satisfaction Si = AETi / EETi
            if (fastestET > 0.0 && fastestET != Double.POSITIVE_INFINITY) {
                double satisfaction = actualET / fastestET;
                satisfactions.add(satisfaction);
            }
        }
        
        if (satisfactions.isEmpty()) {
            return 0.0; // No valid satisfactions calculated
        }
        
        // Calculate average satisfaction M
        double sumSatisfactions = satisfactions.stream().mapToDouble(Double::doubleValue).sum();
        double averageSatisfaction = sumSatisfactions / satisfactions.size();
        
        // Calculate VF = (1/n) * ∑(M - Si)²
        double sumSquaredDeviations = 0.0;
        for (double satisfaction : satisfactions) {
            double deviation = averageSatisfaction - satisfaction;
            sumSquaredDeviations += deviation * deviation;
        }
        
        return sumSquaredDeviations / satisfactions.size();
    }
    
    /**
     * Helper method to calculate task satisfaction.
     * Si = AETi / EETi
     * 
     * @param task The task to calculate satisfaction for
     * @param assignedVM The VM the task is assigned to
     * @param vms Map of all VMs to find the fastest one
     * @param capabilityName The capability name for execution time calculation
     * @return The satisfaction value for the task
     */
    public static double taskSatisfaction(task task, VM assignedVM, 
                                        java.util.Map<Integer, VM> vms, 
                                        String capabilityName) {
        
        // Calculate AETi (Actual Execution Time on assigned VM)
        double actualET = ET(task, assignedVM, capabilityName);
        
        // Calculate EETi (Expected Execution Time on fastest VM)
        double fastestET = Double.POSITIVE_INFINITY;
        for (VM vm : vms.values()) {
            double et = ET(task, vm, capabilityName);
            if (et < fastestET) {
                fastestET = et;
            }
        }
        
        // Return satisfaction Si = AETi / EETi
        if (fastestET > 0.0 && fastestET != Double.POSITIVE_INFINITY) {
            return actualET / fastestET;
        }
        
        return 1.0; // Default satisfaction if calculation fails
    }
}
