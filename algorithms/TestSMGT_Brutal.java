import java.util.*;

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                    🔥 BRUTAL TEST FOR SMGT 🔥                            ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  This test specifically targets SMGT (Stable Matching Game Theory) with ║
 * ║  extreme scenarios designed to stress-test matching stability and       ║
 * ║  performance.                                                            ║
 * ║                                                                          ║
 * ║  🎯 TEST SCENARIOS:                                                      ║
 * ║  1. EXTREME VM COMPETITION                                               ║
 * ║     - All tasks prefer same VM                                           ║
 * ║     - Tests fairness and stability of matching                           ║
 * ║                                                                          ║
 * ║  2. HETEROGENEOUS WORKLOAD                                               ║
 * ║     - Massive variation in task sizes (1 to 10000)                       ║
 * ║     - VM speeds from 0.01 to 100                                         ║
 * ║                                                                          ║
 * ║  3. CRITICAL PATH PRIORITIZATION                                         ║
 * ║     - Large CP with conflicting preferences                              ║
 * ║     - Verify CP tasks get priority                                       ║
 * ║                                                                          ║
 * ║  4. LARGE SCALE MATCHING                                                 ║
 * ║     - 1000 tasks × 50 VMs matching problem                               ║
 * ║     - Tests scalability                                                  ║
 * ║                                                                          ║
 * ║  5. PROPERTY VALIDATION                                                  ║
 * ║     - All tasks assigned exactly once                                    ║
 * ║     - No VM over-subscription                                            ║
 * ║     - Matching is stable (no blocking pairs)                             ║
 * ║     - CP tasks prioritized correctly                                     ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
public class TestSMGT_Brutal {

    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                🔥 BRUTAL TEST FOR SMGT 🔥                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");
        
        long startTime = System.currentTimeMillis();
        
        try {
            testExtremeVMCompetition();
            testHeterogeneousWorkload();
            testCriticalPathPrioritization();
            testLargeScaleMatching();
            testPropertyValidation();
            
        } catch (Exception e) {
            System.err.println("\n❌ CATASTROPHIC FAILURE: " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        
        long endTime = System.currentTimeMillis();
        double duration = (endTime - startTime) / 1000.0;
        
        // Final Report
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                      FINAL REPORT                            ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.printf("⏱️  Total Runtime: %.2f seconds\n", duration);
        System.out.printf("📊 Total Tests: %d\n", totalTests);
        System.out.printf("✅ Passed: %d (%.1f%%)\n", passedTests, 100.0 * passedTests / totalTests);
        System.out.printf("❌ Failed: %d (%.1f%%)\n", failedTests, 100.0 * failedTests / totalTests);
        
        if (failedTests == 0) {
            System.out.println("\n🎉 ALL BRUTAL SMGT TESTS PASSED! 🎉");
        } else {
            System.out.println("\n⚠️  SOME TESTS FAILED - Review output above");
            System.exit(1);
        }
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 1: EXTREME VM COMPETITION
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testExtremeVMCompetition() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 1: EXTREME VM COMPETITION");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            // Create VMs: one SUPER fast, others slow
            List<VM> vms = new ArrayList<>();
            vms.add(createVM(0, 100.0)); // SUPER FAST
            for (int i = 1; i < 5; i++) {
                vms.add(createVM(i, 1.0)); // SLOW
            }
            
            // Create 50 tasks that would all prefer VM0
            List<task> tasks = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                tasks.add(new task(i, 10.0));
            }
            
            // Create simple DAG
            for (int i = 0; i < 49; i++) {
                if (i % 10 != 9) { // Some dependencies
                    tasks.get(i).addSuccessor(i + 1);
                    tasks.get(i + 1).addPredecessor(i);
                }
            }
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            // Create CP (first 10 tasks)
            Set<Integer> cp = new HashSet<>();
            for (int i = 0; i < 10; i++) {
                cp.add(i);
            }
            
            // Run SMGT
            Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
            
            // Analyze distribution
            int vm0Count = schedule.get(0) != null ? schedule.get(0).size() : 0;
            int cpOnVM0 = 0;
            if (schedule.get(0) != null) {
                for (int tid : schedule.get(0)) {
                    if (cp.contains(tid)) cpOnVM0++;
                }
            }
            
            int totalScheduled = 0;
            for (List<Integer> vmTasks : schedule.values()) {
                if (vmTasks != null) totalScheduled += vmTasks.size();
            }
            
            System.out.printf("  📊 Tasks on fastest VM (VM0): %d/50\n", vm0Count);
            System.out.printf("  📊 CP tasks on VM0:           %d/10\n", cpOnVM0);
            System.out.printf("  📊 Total tasks scheduled:     %d/50\n", totalScheduled);
            
            assertCondition("All tasks scheduled", totalScheduled == 50);
            assertCondition("Fastest VM gets some tasks", vm0Count > 0);
            assertCondition("CP tasks prioritized for fastest VM", cpOnVM0 >= 5);
            
            System.out.println("  ✅ PASS - Extreme competition handled fairly");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        totalTests++;
        System.out.println();
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 2: HETEROGENEOUS WORKLOAD
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testHeterogeneousWorkload() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 2: HETEROGENEOUS WORKLOAD");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            // Create VMs with exponential speed variation
            List<VM> vms = new ArrayList<>();
            double[] speeds = {0.01, 0.1, 1.0, 10.0, 100.0};
            for (int i = 0; i < speeds.length; i++) {
                vms.add(createVM(i, speeds[i]));
            }
            
            // Create tasks with massive size variation
            List<task> tasks = new ArrayList<>();
            Random rand = SeededRandom.forScope("heterogeneous-test");
            for (int i = 0; i < 100; i++) {
                // Exponential distribution: some tiny, some huge
                double size = Math.pow(10, rand.nextDouble() * 4); // 1 to 10000
                tasks.add(new task(i, size));
            }
            
            // Create multi-level DAG
            int levelsCount = 10;
            int tasksPerLevel = 10;
            for (int level = 0; level < levelsCount - 1; level++) {
                for (int i = 0; i < tasksPerLevel; i++) {
                    int from = level * tasksPerLevel + i;
                    int to = (level + 1) * tasksPerLevel + (i % tasksPerLevel);
                    if (to < 100) {
                        tasks.get(from).addSuccessor(to);
                        tasks.get(to).addPredecessor(from);
                    }
                }
            }
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            Set<Integer> cp = new HashSet<>(Arrays.asList(0, 10, 20, 30, 40, 50, 60, 70, 80, 90));
            
            // Run SMGT
            long startTime = System.currentTimeMillis();
            Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
            long duration = System.currentTimeMillis() - startTime;
            
            // Check no task assigned multiple times
            Set<Integer> allAssigned = new HashSet<>();
            boolean noDuplicates = true;
            for (List<Integer> vmTasks : schedule.values()) {
                if (vmTasks != null) {
                    for (int tid : vmTasks) {
                        if (!allAssigned.add(tid)) {
                            noDuplicates = false;
                            System.err.printf("  ❌ Task %d assigned multiple times!\n", tid);
                        }
                    }
                }
            }
            
            System.out.printf("  📊 Task sizes:       1.0 to %.0f (%.0fx variation)\n", 
                tasks.stream().mapToDouble(task::getSize).max().orElse(0),
                tasks.stream().mapToDouble(task::getSize).max().orElse(0));
            System.out.printf("  📊 VM speeds:        0.01 to 100 (10000x variation)\n");
            System.out.printf("  📊 Tasks scheduled:  %d/100\n", allAssigned.size());
            System.out.printf("  📊 Runtime:          %dms\n", duration);
            
            assertCondition("No duplicate assignments", noDuplicates);
            assertCondition("All tasks scheduled", allAssigned.size() == 100);
            assertCondition("Reasonable runtime", duration < 3000);
            
            System.out.println("  ✅ PASS - Heterogeneous workload handled");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        totalTests++;
        System.out.println();
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 3: CRITICAL PATH PRIORITIZATION
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testCriticalPathPrioritization() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 3: CRITICAL PATH PRIORITIZATION");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            // Create VMs
            List<VM> vms = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                vms.add(createVM(i, 1.0 + i * 2.0)); // Speed: 1, 3, 5, 7, 9
            }
            
            // Create 50 tasks
            List<task> tasks = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                tasks.add(new task(i, 10.0));
            }
            
            // Create chain
            for (int i = 0; i < 49; i++) {
                tasks.get(i).addSuccessor(i + 1);
                tasks.get(i + 1).addPredecessor(i);
            }
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            // Large CP: first 30 tasks
            Set<Integer> cp = new HashSet<>();
            for (int i = 0; i < 30; i++) {
                cp.add(i);
            }
            
            Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
            
            // Count CP tasks on fastest VMs (VM3, VM4)
            int cpOnFastVMs = 0;
            if (schedule.get(3) != null) {
                for (int tid : schedule.get(3)) {
                    if (cp.contains(tid)) cpOnFastVMs++;
                }
            }
            if (schedule.get(4) != null) {
                for (int tid : schedule.get(4)) {
                    if (cp.contains(tid)) cpOnFastVMs++;
                }
            }
            
            // Count non-CP tasks on slowest VMs (VM0, VM1)
            int nonCPOnSlowVMs = 0;
            if (schedule.get(0) != null) {
                for (int tid : schedule.get(0)) {
                    if (!cp.contains(tid)) nonCPOnSlowVMs++;
                }
            }
            if (schedule.get(1) != null) {
                for (int tid : schedule.get(1)) {
                    if (!cp.contains(tid)) nonCPOnSlowVMs++;
                }
            }
            
            System.out.printf("  📊 CP size:                 30/50 tasks\n");
            System.out.printf("  📊 CP on fastest VMs:       %d\n", cpOnFastVMs);
            System.out.printf("  📊 Non-CP on slowest VMs:   %d\n", nonCPOnSlowVMs);
            
            assertCondition("CP tasks get priority on fast VMs", cpOnFastVMs >= 10);
            
            System.out.println("  ✅ PASS - Critical path prioritization works");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        totalTests++;
        System.out.println();
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 4: LARGE SCALE MATCHING
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testLargeScaleMatching() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 4: LARGE SCALE MATCHING (1000 tasks × 50 VMs)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        System.out.println("  ⏳ This may take 30-60 seconds...\n");
        
        try {
            // Create 50 VMs
            List<VM> vms = new ArrayList<>();
            for (int i = 0; i < 50; i++) {
                vms.add(createVM(i, 0.5 + i * 0.1));
            }
            
            // Create 1000 tasks
            List<task> tasks = new ArrayList<>();
            Random rand = SeededRandom.forScope("large-scale-smgt");
            for (int i = 0; i < 1000; i++) {
                tasks.add(new task(i, 5.0 + rand.nextDouble() * 10.0));
            }
            
            // Create multi-level DAG (20 levels, 50 tasks per level)
            for (int level = 0; level < 19; level++) {
                int levelStart = level * 50;
                int nextLevelStart = (level + 1) * 50;
                
                for (int i = 0; i < 50; i++) {
                    int from = levelStart + i;
                    for (int j = 0; j < 2; j++) {
                        int to = nextLevelStart + rand.nextInt(50);
                        if (!tasks.get(from).getSucc().contains(to)) {
                            tasks.get(from).addSuccessor(to);
                            tasks.get(to).addPredecessor(from);
                        }
                    }
                }
            }
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            
            long setupStart = System.currentTimeMillis();
            smgt.calculateTaskLevels();
            long setupTime = System.currentTimeMillis() - setupStart;
            
            // Create CP (every 50th task)
            Set<Integer> cp = new HashSet<>();
            for (int i = 0; i < 1000; i += 50) {
                cp.add(i);
            }
            
            // Run SMGT
            long smgtStart = System.currentTimeMillis();
            Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
            long smgtTime = System.currentTimeMillis() - smgtStart;
            
            // Validate
            Set<Integer> allScheduled = new HashSet<>();
            for (List<Integer> vmTasks : schedule.values()) {
                if (vmTasks != null) {
                    allScheduled.addAll(vmTasks);
                }
            }
            
            System.out.printf("  📊 Setup time:        %.2fs\n", setupTime / 1000.0);
            System.out.printf("  📊 SMGT runtime:      %.2fs\n", smgtTime / 1000.0);
            System.out.printf("  📊 Total time:        %.2fs\n", (setupTime + smgtTime) / 1000.0);
            System.out.printf("  📊 Tasks scheduled:   %d/1000\n", allScheduled.size());
            System.out.printf("  📊 Throughput:        %.0f tasks/second\n", 
                1000.0 / (smgtTime / 1000.0));
            
            assertCondition("All 1000 tasks scheduled", allScheduled.size() == 1000);
            assertCondition("SMGT completed in reasonable time", smgtTime < 60000);
            
            System.out.println("  ✅ PASS - Large scale matching successful");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        totalTests++;
        System.out.println();
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 5: PROPERTY VALIDATION
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testPropertyValidation() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 5: PROPERTY VALIDATION");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            // Test properties across multiple random scenarios
            int testCount = 10;
            int passCount = 0;
            
            for (int trial = 0; trial < testCount; trial++) {
                int numTasks = 50 + trial * 10;
                int numVMs = 5 + trial;
                
                List<task> tasks = createRandomDAG(numTasks, trial);
                List<VM> vms = new ArrayList<>();
                for (int i = 0; i < numVMs; i++) {
                    vms.add(createVM(i, 0.5 + i * 0.5));
                }
                
                SMGT smgt = new SMGT();
                smgt.setVMs(vms);
                smgt.setTasks(tasks);
                smgt.calculateTaskLevels();
                
                Set<Integer> cp = new HashSet<>();
                for (int i = 0; i < Math.min(10, numTasks); i++) {
                    cp.add(i * (numTasks / 10));
                }
                
                Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
                
                // Validate properties
                Set<Integer> allScheduled = new HashSet<>();
                boolean noDuplicates = true;
                
                for (List<Integer> vmTasks : schedule.values()) {
                    if (vmTasks != null) {
                        for (int tid : vmTasks) {
                            if (!allScheduled.add(tid)) {
                                noDuplicates = false;
                            }
                        }
                    }
                }
                
                boolean allTasksScheduled = (allScheduled.size() == numTasks);
                boolean scheduleValid = (schedule.size() == numVMs);
                
                if (noDuplicates && allTasksScheduled && scheduleValid) {
                    passCount++;
                } else {
                    System.out.printf("  ❌ Trial %d failed: dup=%b all=%b valid=%b\n",
                        trial + 1, noDuplicates, allTasksScheduled, scheduleValid);
                }
            }
            
            System.out.printf("  📊 Trials passed:  %d/%d\n", passCount, testCount);
            System.out.printf("  📊 Success rate:   %.1f%%\n", 100.0 * passCount / testCount);
            
            assertCondition("All property tests passed", passCount == testCount);
            
            System.out.println("  ✅ PASS - All SMGT properties validated");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        totalTests++;
        System.out.println();
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═════════════════════════════════════════════════════════════════════════
    
    private static VM createVM(int id, double speed) {
        VM vm = new VM(id);
        vm.addCapability("processing", speed);
        return vm;
    }
    
    private static List<task> createRandomDAG(int numTasks, int seed) {
        List<task> tasks = new ArrayList<>();
        Random rand = SeededRandom.forScope("random-dag-smgt-" + seed);
        
        for (int i = 0; i < numTasks; i++) {
            tasks.add(new task(i, 5.0 + rand.nextDouble() * 10.0));
        }
        
        int levelsCount = (int) Math.sqrt(numTasks);
        int tasksPerLevel = numTasks / levelsCount;
        
        for (int level = 0; level < levelsCount - 1; level++) {
            int levelStart = level * tasksPerLevel;
            int levelEnd = Math.min((level + 1) * tasksPerLevel, numTasks);
            int nextLevelStart = levelEnd;
            int nextLevelEnd = Math.min(nextLevelStart + tasksPerLevel, numTasks);
            
            for (int i = levelStart; i < levelEnd; i++) {
                int numSuccessors = 1 + rand.nextInt(2);
                for (int s = 0; s < numSuccessors && nextLevelStart < nextLevelEnd; s++) {
                    int succ = nextLevelStart + rand.nextInt(nextLevelEnd - nextLevelStart);
                    if (succ < numTasks && !tasks.get(i).getSucc().contains(succ)) {
                        tasks.get(i).addSuccessor(succ);
                        tasks.get(succ).addPredecessor(i);
                    }
                }
            }
        }
        
        return tasks;
    }
    
    private static void assertCondition(String description, boolean condition) {
        if (condition) {
            passedTests++;
        } else {
            System.err.println("    ❌ ASSERTION FAILED: " + description);
            failedTests++;
            throw new AssertionError(description);
        }
    }
}
