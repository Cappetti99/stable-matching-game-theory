import java.util.*;
import java.io.*;

public class SMGT {
    private List<VM> vms;
    private List<task> tasks;
    private Map<Integer, Integer> taskLevels; // Maps task ID to its level in the DAG
    private Map<Integer, List<Integer>> levelTasks; // Maps level to list of task IDs at that level
    
    public SMGT() {
        this.vms = new ArrayList<>();
        this.tasks = new ArrayList<>();
        this.taskLevels = new HashMap<>();
        this.levelTasks = new HashMap<>();
    }
    
    /**
     * Calculates the threshold for VM k at level l based on the formula:
     * threshold(k, l) = (∑(v=0 to l) nv / ∑(i=0 to m-1) pi) × pk
     * 
     * @param k VM index (0-based)
     * @param l Level in the DAG (0-based)
     * @return The calculated threshold value as an integer
     */
    public int threshold(int k, int l) {
        if (k >= vms.size() || l < 0) {
            throw new IllegalArgumentException("Invalid VM index or level");
        }
        
        // Calculate sum of tasks from level 0 to l (inclusive)
        double sumTasksUpToLevel = 0.0;
        for (int v = 0; v <= l; v++) {
            int nv = getNumberOfTasksAtLevel(v);
            sumTasksUpToLevel += nv;
        }
        
        // Calculate sum of all VM processing capacities
        double sumAllProcessingCapacities = 0.0;
        for (VM vm : vms) {
            double processingCapacity = getVMProcessingCapacity(vm);
            sumAllProcessingCapacities += processingCapacity;
        }
        
        // Get processing capacity of VM k
        double pk = getVMProcessingCapacity(vms.get(k));
        
        // Apply the threshold formula
        if (sumAllProcessingCapacities == 0) {
            return 1; // Avoid division by zero, return minimum threshold
        }
        
        double result = (sumTasksUpToLevel / sumAllProcessingCapacities) * pk;
        
        // Convert to integer: if between 0 and 1 (inclusive of 0), return 1; otherwise round
        if (result >= 0 && result < 1) {
            return 1;
        } else {
            return (int) Math.round(result);
        }
    }
    
    /**
     * Gets the number of tasks at a specific level in the DAG
     * @param level The level to check
     * @return Number of tasks at the given level
     */
    private int getNumberOfTasksAtLevel(int level) {
        return levelTasks.getOrDefault(level, new ArrayList<>()).size();
    }
    
    /**
     * Gets the processing capacity of a VM
     * For simplicity, we use the first capability value or a default method
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
     * Loads VM data from processing_capacity.csv
     */
    public void loadVMsFromCSV(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        boolean firstLine = true;
        
        while ((line = reader.readLine()) != null) {
            if (firstLine) {
                firstLine = false;
                continue; // Skip header
            }
            
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                double capacity = Double.parseDouble(parts[1]);
                
                VM vm = new VM(vms.size()); // Use index as ID
                vm.addCapability("processing", capacity);
                vms.add(vm);
            }
        }
        reader.close();
    }
    
    /**
     * Loads task data and builds the DAG structure
     */
    public void loadTasksFromCSV(String dagFilename, String taskFilename) throws IOException {
        // First load task basic info if task.csv exists
        File taskFile = new File(taskFilename);
        if (taskFile.exists()) {
            loadTaskBasicInfo(taskFilename);
        }
        
        // Load DAG structure
        loadDAGStructure(dagFilename);
        
        // Calculate levels for all tasks
        calculateTaskLevels();
    }
    
    private void loadTaskBasicInfo(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        
        while ((line = reader.readLine()) != null) {
            // Skip comments and empty lines
            if (line.startsWith("#") || line.trim().isEmpty()) {
                continue;
            }
            
            // Split by whitespace (spaces or tabs)
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                int taskId = Integer.parseInt(parts[0].replace("t", ""));
                double size = Double.parseDouble(parts[1]);
                double rank = 0.0; // Default rank if not provided
                
                // If rank is provided as third column
                if (parts.length >= 3) {
                    rank = Double.parseDouble(parts[2]);
                }
                
                task t = new task(taskId);
                t.setSize(size);
                t.setRank(rank);
                tasks.add(t);
            }
        }
        reader.close();
    }
    
    private void loadDAGStructure(String filename) throws IOException {
        BufferedReader reader = new BufferedReader(new FileReader(filename));
        String line;
        boolean firstLine = true;
        
        while ((line = reader.readLine()) != null) {
            if (firstLine) {
                firstLine = false;
                continue; // Skip header
            }
            
            String[] parts = line.trim().split("\\s+");
            if (parts.length >= 2) {
                int predId = Integer.parseInt(parts[0].replace("t", ""));
                int succId = Integer.parseInt(parts[1].replace("t", ""));
                
                // Ensure tasks exist
                ensureTaskExists(predId);
                ensureTaskExists(succId);
                
                // Add relationships
                getTaskById(predId).getSucc().add(succId);
                getTaskById(succId).getPre().add(predId);
            }
        }
        reader.close();
    }
    
    private void ensureTaskExists(int taskId) {
        if (getTaskById(taskId) == null) {
            task t = new task(taskId);
            tasks.add(t);
        }
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
    private void calculateTaskLevels() {
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
     * @param taskId The task ID
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
     * Generates preference list for a task based on ascending order of finish time on different VMs
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
     * Generates preference list for a VM based on ascending order of task finish times on this VM
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
     * @return Map where key is task ID and value is list of VM indices in preference order
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
     * @return Map where key is VM index and value is list of task IDs in preference order
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
     * Prints the preference matrices for debugging
     */
    public void printPreferenceMatrices() {
        System.out.println("\n=== TASK PREFERENCE MATRICES ===");
        Map<Integer, List<Integer>> taskPrefs = generateAllTaskPreferences();
        for (Map.Entry<Integer, List<Integer>> entry : taskPrefs.entrySet()) {
            System.out.println("Task t" + entry.getKey() + " preferences: " + 
                entry.getValue().stream()
                    .map(vm -> "VM" + vm)
                    .reduce((a, b) -> a + " > " + b)
                    .orElse(""));
        }
        
        System.out.println("\n=== VM PREFERENCE MATRICES ===");
        Map<Integer, List<Integer>> vmPrefs = generateAllVMPreferences();
        for (Map.Entry<Integer, List<Integer>> entry : vmPrefs.entrySet()) {
            System.out.println("VM" + entry.getKey() + " preferences: " + 
                entry.getValue().stream()
                    .map(task -> "t" + task)
                    .reduce((a, b) -> a + " > " + b)
                    .orElse(""));
        }
    }
    
    /**
     * Finds the critical task in a level (task on critical path with highest priority)
     * For now, we'll use the task with the highest rank as a proxy for critical path
     */
    private Integer findCriticalTask(List<Integer> tasksInLevel) {
        if (tasksInLevel.isEmpty()) {
            return null;
        }
        
        Integer criticalTask = tasksInLevel.get(0);
        double maxRank = getTaskById(criticalTask).getRank();
        
        for (Integer taskId : tasksInLevel) {
            task t = getTaskById(taskId);
            if (t.getRank() > maxRank) {
                maxRank = t.getRank();
                criticalTask = taskId;
            }
        }
        
        return criticalTask;
    }
    
    /**
     * Finds the VM with the fastest processing speed
     */
    private int findFastestVM() {
        int fastestVM = 0;
        double maxCapacity = getVMProcessingCapacity(vms.get(0));
        
        for (int i = 1; i < vms.size(); i++) {
            double capacity = getVMProcessingCapacity(vms.get(i));
            if (capacity > maxCapacity) {
                maxCapacity = capacity;
                fastestVM = i;
            }
        }
        
        return fastestVM;
    }
    
    /**
     * Gets the position of a task in a VM's preference matrix
     */
    private int getTaskPositionInVMPreference(int taskId, int vmIndex, Map<Integer, List<Integer>> vmPreferences) {
        List<Integer> vmPrefList = vmPreferences.get(vmIndex);
        return vmPrefList.indexOf(taskId);
    }
    
    /**
     * Finds the task with the largest preference value (lowest preference) in a VM's waiting list
     */
    private Integer findLargestPreferenceTask(int vmIndex, List<Integer> vmWaitingList, 
                                             Map<Integer, List<Integer>> vmPreferences) {
        if (vmWaitingList.isEmpty()) {
            return null;
        }
        
        Integer largestPrefTask = vmWaitingList.get(0);
        int maxPosition = getTaskPositionInVMPreference(largestPrefTask, vmIndex, vmPreferences);
        
        for (Integer taskId : vmWaitingList) {
            int position = getTaskPositionInVMPreference(taskId, vmIndex, vmPreferences);
            if (position > maxPosition) {
                maxPosition = position;
                largestPrefTask = taskId;
            }
        }
        
        return largestPrefTask;
    }
    
    /**
     * SMGT Algorithm following the correct pseudocode
     * @param level The level to process
     * @param vmWaitingLists Map to store VM waiting lists (VM index -> list of waiting task IDs)
     */
    public void assignTasksInLevelCorrect(int level, Map<Integer, List<Integer>> vmWaitingLists) {
        // Get all tasks in this level
        List<Integer> tasksInLevel = new ArrayList<>(levelTasks.getOrDefault(level, new ArrayList<>()));
        if (tasksInLevel.isEmpty()) {
            return;
        }
        
        System.out.println("\n=== ASSIGNING TASKS IN LEVEL " + level + " ===");
        System.out.println("Tasks in level: " + tasksInLevel.stream().map(t -> "t" + t).reduce((a, b) -> a + ", " + b).orElse("none"));
        
        // Initialize VM waiting lists if needed
        for (int vmIndex = 0; vmIndex < vms.size(); vmIndex++) {
            vmWaitingLists.putIfAbsent(vmIndex, new ArrayList<>());
        }
        
        // Generate VM preference matrix for this level
        Map<Integer, List<Integer>> vmPreferences = generateAllVMPreferences();
        
        // Step 1: Assign critical task to fastest VM that has capacity
        Integer criticalTask = findCriticalTask(tasksInLevel);
        if (criticalTask != null) {
            int fastestVM = findFastestVM();
            int fastestVMThreshold = threshold(fastestVM, level);
            List<Integer> fastestVMWaitingList = vmWaitingLists.get(fastestVM);
            
            if (fastestVMWaitingList.size() < fastestVMThreshold) {
                // Fastest VM has capacity under threshold
                fastestVMWaitingList.add(criticalTask);
                System.out.println("Critical task t" + criticalTask + " assigned to fastest VM" + fastestVM + 
                                 " (capacity: " + fastestVMWaitingList.size() + "/" + fastestVMThreshold + ")");
                tasksInLevel.remove(criticalTask);
            } else {
                // Fastest VM is at threshold, find alternative VM with capacity
                boolean criticalTaskAssigned = false;
                System.out.println("Fastest VM" + fastestVM + " at threshold (" + fastestVMThreshold + 
                                 "), finding alternative for critical task t" + criticalTask);
                
                // Try other VMs in order of processing capacity
                List<Integer> vmsByCapacity = new ArrayList<>();
                for (int i = 0; i < vms.size(); i++) {
                    if (i != fastestVM) vmsByCapacity.add(i);
                }
                vmsByCapacity.sort((vm1, vm2) -> Double.compare(
                    getVMProcessingCapacity(vms.get(vm2)), 
                    getVMProcessingCapacity(vms.get(vm1))
                ));
                
                for (Integer vmIndex : vmsByCapacity) {
                    int vmThreshold = threshold(vmIndex, level);
                    List<Integer> vmWaitingList = vmWaitingLists.get(vmIndex);
                    
                    if (vmWaitingList.size() < vmThreshold) {
                        vmWaitingList.add(criticalTask);
                        System.out.println("Critical task t" + criticalTask + " assigned to VM" + vmIndex + 
                                         " (capacity: " + vmWaitingList.size() + "/" + vmThreshold + ")");
                        tasksInLevel.remove(criticalTask);
                        criticalTaskAssigned = true;
                        break;
                    }
                }
                
                if (!criticalTaskAssigned) {
                    System.out.println("⚠️ WARNING: Critical task t" + criticalTask + 
                                     " cannot be assigned - all VMs at threshold capacity!");
                    // Keep the task in the list to be processed normally
                }
            }
        }
        
        // Step 2: Assign remaining tasks
        while (!tasksInLevel.isEmpty()) {
            // Generate task preference matrix for remaining tasks
            Map<Integer, List<Integer>> taskPreferences = generateAllTaskPreferences();
            
            // Get first task from the list
            Integer currentTask = tasksInLevel.get(0);
            List<Integer> taskPrefList = taskPreferences.get(currentTask);
            boolean taskAssigned = false;
            
            System.out.println("\nAssigning task t" + currentTask);
            
            // Try each VM in order of task's preference
            for (int j = 0; j < taskPrefList.size(); j++) {
                Integer vmIndex = taskPrefList.get(j);
                int thresholdValue = threshold(vmIndex, level);
                List<Integer> vmWaitingList = vmWaitingLists.get(vmIndex);
                
                System.out.println("  Trying VM" + vmIndex + " (total assigned: " + vmWaitingList.size() + 
                                 ", cumulative threshold: " + thresholdValue + ")");
                
                // CRITICAL FIX: Check cumulative threshold - VM can't exceed threshold(vm, level)
                // threshold(vm, level) is cumulative for all tasks assigned to VM up to level l
                if (vmWaitingList.size() < thresholdValue) {
                    // VM has capacity under cumulative threshold, assign task
                    vmWaitingList.add(currentTask);
                    System.out.println("  ✓ Task t" + currentTask + " assigned to VM" + vmIndex + 
                                     " (total assigned: " + vmWaitingList.size() + "/" + thresholdValue + ")");
                    tasksInLevel.remove(currentTask);
                    taskAssigned = true;
                    break;
                    
                } else if (vmWaitingList.size() == thresholdValue) {
                    // VM is at cumulative threshold capacity, check if we can replace a task
                    int currentTaskPosition = getTaskPositionInVMPreference(currentTask, vmIndex, vmPreferences);
                    Integer largestPrefTask = findLargestPreferenceTask(vmIndex, vmWaitingList, vmPreferences);
                    
                    if (largestPrefTask != null) {
                        int largestTaskPosition = getTaskPositionInVMPreference(largestPrefTask, vmIndex, vmPreferences);
                        
                        System.out.println("    VM" + vmIndex + " at threshold (" + thresholdValue + "), checking replacement:");
                        System.out.println("    Current task t" + currentTask + " position: " + currentTaskPosition + 
                                         ", least preferred task t" + largestPrefTask + " position: " + largestTaskPosition);
                        
                        if (currentTaskPosition < largestTaskPosition) {
                            // Current task is preferred over the least preferred task, replace it
                            vmWaitingList.remove(largestPrefTask);
                            vmWaitingList.add(currentTask);
                            tasksInLevel.remove(currentTask);
                            tasksInLevel.add(largestPrefTask); // Add replaced task back to task list
                            
                            System.out.println("  ✓ Task t" + currentTask + " replaced t" + largestPrefTask + 
                                             " in VM" + vmIndex + " (threshold preserved: " + vmWaitingList.size() + "/" + thresholdValue + ")");
                            taskAssigned = true;
                            break;
                        } else {
                            System.out.println("    Task t" + currentTask + " not preferred over t" + largestPrefTask + 
                                             ", trying next VM");
                            // Continue to next VM
                        }
                    } else {
                        System.out.println("    VM" + vmIndex + " at threshold but no task to replace, trying next VM");
                    }
                }
            }
            
            if (!taskAssigned) {
                System.out.println("  ✗ Could not assign task t" + currentTask + " to any VM according to preferences");
                
                // Try to find any VM that still has capacity (respecting thresholds)
                boolean foundVMWithCapacity = false;
                for (int vmIndex = 0; vmIndex < vms.size(); vmIndex++) {
                    int thresholdValue = threshold(vmIndex, level);
                    List<Integer> vmWaitingList = vmWaitingLists.get(vmIndex);
                    
                    if (vmWaitingList.size() < thresholdValue) {
                        vmWaitingList.add(currentTask);
                        tasksInLevel.remove(currentTask);
                        System.out.println("  ! Task t" + currentTask + " assigned to VM" + vmIndex + 
                                         " (has available capacity: " + vmWaitingList.size() + "/" + thresholdValue + ")");
                        foundVMWithCapacity = true;
                        break;
                    }
                }
                
                // If no VM has capacity, this means all VMs are at their threshold limits
                // In this case, we cannot assign the task without violating SMGT constraints
                if (!foundVMWithCapacity) {
                    System.out.println("  ⚠️ WARNING: Task t" + currentTask + " cannot be assigned - all VMs at threshold capacity!");
                    System.out.println("     Current VM loads vs thresholds:");
                    for (int vmIndex = 0; vmIndex < vms.size(); vmIndex++) {
                        int thresholdValue = threshold(vmIndex, level);
                        int currentLoad = vmWaitingLists.get(vmIndex).size();
                        System.out.println("       VM" + vmIndex + ": " + currentLoad + "/" + thresholdValue);
                    }
                    // Remove the task from tasksInLevel to avoid infinite loop
                    tasksInLevel.remove(currentTask);
                    System.out.println("     Task t" + currentTask + " removed from assignment queue");
                }
            }
        }
        
        // Print final waiting lists for this level
        System.out.println("\nFinal waiting lists for level " + level + ":");
        for (Map.Entry<Integer, List<Integer>> entry : vmWaitingLists.entrySet()) {
            System.out.println("VM" + entry.getKey() + ": " + 
                entry.getValue().stream()
                    .map(task -> "t" + task)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(empty)"));
        }
    }
    
    /**
     * Runs the complete SMGT algorithm following the correct pseudocode
     */
    public Map<Integer, List<Integer>> runSMGTAlgorithmCorrect() {
        Map<Integer, List<Integer>> vmWaitingLists = new HashMap<>();
        
        // Initialize VM waiting lists
        for (int vmIndex = 0; vmIndex < vms.size(); vmIndex++) {
            vmWaitingLists.put(vmIndex, new ArrayList<>());
        }
        
        System.out.println("\n=== RUNNING SMGT ALGORITHM (CORRECT VERSION) ===");
        
        // Get all levels and sort them
        List<Integer> levels = new ArrayList<>(levelTasks.keySet());
        levels.sort(Integer::compareTo);
        
        // Process each level
        for (Integer level : levels) {
            assignTasksInLevelCorrect(level, vmWaitingLists);
        }
        
        // Print final assignments
        System.out.println("\n=== FINAL TASK ASSIGNMENTS ===");
        for (Map.Entry<Integer, List<Integer>> entry : vmWaitingLists.entrySet()) {
            System.out.println("VM" + entry.getKey() + ": " + 
                entry.getValue().stream()
                    .map(task -> "t" + task)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(no tasks)"));
        }
        
        return vmWaitingLists;
    }
    
    /**
     * Finds the task with the largest preference value (least preferred) by a VM
     * @param vmIndex The VM index
     * @param vmTaskList Current tasks assigned to the VM
     * @param vmPreferences VM preference matrices
     * @return The task ID to reassign, or null if none found
     */
    private Integer findLeastPreferredTask(int vmIndex, List<Integer> vmTaskList, 
                                          Map<Integer, List<Integer>> vmPreferences) {
        List<Integer> vmPrefList = vmPreferences.get(vmIndex);
        Integer leastPreferredTask = null;
        int maxPreferenceValue = -1;
        
        for (Integer taskId : vmTaskList) {
            int preferenceValue = vmPrefList.indexOf(taskId);
            if (preferenceValue > maxPreferenceValue) {
                maxPreferenceValue = preferenceValue;
                leastPreferredTask = taskId;
            }
        }
        
        return leastPreferredTask;
    }
    
    /**
     * Finds the VM with minimum current load
     * @param vmAssignments Current VM assignments
     * @return VM index with minimum load
     */
    private int findVMWithMinimumLoad(Map<Integer, List<Integer>> vmAssignments) {
        int minLoadVM = 0;
        int minLoad = vmAssignments.get(0).size();
        
        for (int vmIndex = 1; vmIndex < vms.size(); vmIndex++) {
            int load = vmAssignments.get(vmIndex).size();
            if (load < minLoad) {
                minLoad = load;
                minLoadVM = vmIndex;
            }
        }
        
        return minLoadVM;
    }
    
    /**
     * Runs the complete SMGT algorithm for all levels
     * @return Map containing final assignments (VM index -> list of assigned task IDs)
     */
    public Map<Integer, List<Integer>> runSMGTAlgorithm() {
        return runSMGTAlgorithmCorrect();
    }
}
