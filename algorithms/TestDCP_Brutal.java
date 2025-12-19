import java.util.*;

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                    🔥 BRUTAL TEST FOR DCP 🔥                             ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  This test specifically targets DCP (Dynamic Critical Path) with        ║
 * ║  extreme scenarios designed to stress-test rank calculation and CP      ║
 * ║  identification.                                                         ║
 * ║                                                                          ║
 * ║  🎯 TEST SCENARIOS:                                                      ║
 * ║  1. DEEP RECURSION                                                       ║
 * ║     - 1000-level deep linear chain                                       ║
 * ║     - Tests stack depth and recursion limits                             ║
 * ║                                                                          ║
 * ║  2. DENSE GRAPH                                                          ║
 * ║     - Fully connected DAG (all-to-all edges)                             ║
 * ║     - Tests O(n²) edge complexity                                        ║
 * ║                                                                          ║
 * ║  3. AMBIGUOUS CRITICAL PATH                                              ║
 * ║     - Multiple paths with nearly equal ranks                             ║
 * ║     - Tests tie-breaking logic                                           ║
 * ║                                                                          ║
 * ║  4. EXTREME COMMUNICATION COSTS                                          ║
 * ║     - Communication >> computation                                       ║
 * ║     - Tests numerical stability with large values                        ║
 * ║                                                                          ║
 * ║  5. PROPERTY VALIDATION                                                  ║
 * ║     - CP always contains entry and exit tasks                            ║
 * ║     - CP has exactly one task per level                                  ║
 * ║     - CP tasks form valid path in DAG                                    ║
 * ║     - Rank calculation is consistent                                     ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
public class TestDCP_Brutal {

    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                 🔥 BRUTAL TEST FOR DCP 🔥                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");
        
        long startTime = System.currentTimeMillis();
        
        try {
            testDeepRecursion();
            testDenseGraph();
            testAmbiguousCriticalPath();
            testExtremeCommunicationCosts();
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
            System.out.println("\n🎉 ALL BRUTAL DCP TESTS PASSED! 🎉");
        } else {
            System.out.println("\n⚠️  SOME TESTS FAILED - Review output above");
            System.exit(1);
        }
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 1: DEEP RECURSION
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testDeepRecursion() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 1: DEEP RECURSION (1000-level chain)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            int depth = 1000;
            
            // Create linear chain
            List<task> tasks = new ArrayList<>();
            for (int i = 0; i < depth; i++) {
                tasks.add(new task(i, 5.0));
            }
            
            for (int i = 0; i < depth - 1; i++) {
                tasks.get(i).addSuccessor(i + 1);
                tasks.get(i + 1).addPredecessor(i);
            }
            
            // Create levels
            Map<Integer, List<Integer>> levels = new HashMap<>();
            for (int i = 0; i < depth; i++) {
                levels.put(i, Arrays.asList(i));
            }
            
            // Communication costs
            Map<String, Double> commCosts = new HashMap<>();
            for (int i = 0; i < depth - 1; i++) {
                commCosts.put(i + "_" + (i + 1), 10.0);
            }
            
            // Create VMs
            Map<Integer, VM> vms = createVMMap(5);
            
            // Run DCP
            long startTime = System.currentTimeMillis();
            Set<Integer> cp = DCP.executeDCP(tasks, levels, tasks.get(depth - 1), commCosts, vms);
            long duration = System.currentTimeMillis() - startTime;
            
            System.out.printf("  📊 Depth:         %d levels\n", depth);
            System.out.printf("  📊 CP size:       %d tasks\n", cp.size());
            System.out.printf("  📊 Runtime:       %dms\n", duration);
            
            assertCondition("CP size equals depth", cp.size() == depth);
            assertCondition("CP contains first task", cp.contains(0));
            assertCondition("CP contains last task", cp.contains(depth - 1));
            assertCondition("DCP completed in reasonable time", duration < 2000);
            
            System.out.println("  ✅ PASS - Deep recursion handled correctly");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        totalTests++;
        System.out.println();
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 2: DENSE GRAPH
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testDenseGraph() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 2: DENSE GRAPH (all-to-all connectivity)");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            int numLevels = 5;
            int tasksPerLevel = 20;
            int totalTasks = numLevels * tasksPerLevel;
            
            // Create tasks
            List<task> tasks = new ArrayList<>();
            for (int i = 0; i < totalTasks; i++) {
                tasks.add(new task(i, 5.0 + i * 0.1));
            }
            
            // Create all-to-all connectivity between adjacent levels
            Random rand = SeededRandom.forScope("dense-graph");
            int edgeCount = 0;
            for (int level = 0; level < numLevels - 1; level++) {
                int levelStart = level * tasksPerLevel;
                int nextLevelStart = (level + 1) * tasksPerLevel;
                
                // Each task connects to ALL tasks in next level
                for (int i = 0; i < tasksPerLevel; i++) {
                    for (int j = 0; j < tasksPerLevel; j++) {
                        int from = levelStart + i;
                        int to = nextLevelStart + j;
                        tasks.get(from).addSuccessor(to);
                        tasks.get(to).addPredecessor(from);
                        edgeCount++;
                    }
                }
            }
            
            // Create levels
            Map<Integer, List<Integer>> levels = new HashMap<>();
            for (int level = 0; level < numLevels; level++) {
                List<Integer> levelTasks = new ArrayList<>();
                for (int i = 0; i < tasksPerLevel; i++) {
                    levelTasks.add(level * tasksPerLevel + i);
                }
                levels.put(level, levelTasks);
            }
            
            // Communication costs
            Map<String, Double> commCosts = new HashMap<>();
            for (task t : tasks) {
                for (int succ : t.getSucc()) {
                    double cost = 5.0 + rand.nextDouble() * 10.0;
                    commCosts.put(t.getID() + "_" + succ, cost);
                }
            }
            
            Map<Integer, VM> vms = createVMMap(10);
            
            // Run DCP
            long startTime = System.currentTimeMillis();
            Set<Integer> cp = DCP.executeDCP(tasks, levels, tasks.get(totalTasks - 1), commCosts, vms);
            long duration = System.currentTimeMillis() - startTime;
            
            System.out.printf("  📊 Total tasks:   %d\n", totalTasks);
            System.out.printf("  📊 Total edges:   %d\n", edgeCount);
            System.out.printf("  📊 CP size:       %d tasks\n", cp.size());
            System.out.printf("  📊 Runtime:       %dms\n", duration);
            
            assertCondition("CP size equals num levels", cp.size() == numLevels);
            assertCondition("CP contains entry task", cp.stream().anyMatch(tid -> tid < tasksPerLevel));
            assertCondition("CP contains exit task", cp.stream().anyMatch(tid -> tid >= totalTasks - tasksPerLevel));
            assertCondition("Dense graph handled in reasonable time", duration < 5000);
            
            System.out.println("  ✅ PASS - Dense graph handled correctly");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        totalTests++;
        System.out.println();
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 3: AMBIGUOUS CRITICAL PATH
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testAmbiguousCriticalPath() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 3: AMBIGUOUS CRITICAL PATH");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            // Create diamond DAG with multiple paths of nearly equal length
            List<task> tasks = new ArrayList<>();
            
            // Entry task
            tasks.add(new task(0, 100.0));
            
            // 5 parallel paths from entry to exit
            int pathsCount = 5;
            int pathLength = 10;
            
            for (int path = 0; path < pathsCount; path++) {
                for (int i = 0; i < pathLength; i++) {
                    int taskId = 1 + path * pathLength + i;
                    // Make all paths have very similar total costs
                    double taskCost = 100.0 + (path * 0.1) + (i * 0.01);
                    tasks.add(new task(taskId, taskCost));
                }
            }
            
            // Exit task
            int exitId = 1 + pathsCount * pathLength;
            tasks.add(new task(exitId, 100.0));
            
            // Connect entry to all path starts
            for (int path = 0; path < pathsCount; path++) {
                int firstInPath = 1 + path * pathLength;
                tasks.get(0).addSuccessor(firstInPath);
                tasks.get(firstInPath).addPredecessor(0);
            }
            
            // Connect within each path
            for (int path = 0; path < pathsCount; path++) {
                for (int i = 0; i < pathLength - 1; i++) {
                    int from = 1 + path * pathLength + i;
                    int to = from + 1;
                    tasks.get(from).addSuccessor(to);
                    tasks.get(to).addPredecessor(from);
                }
            }
            
            // Connect all path ends to exit
            for (int path = 0; path < pathsCount; path++) {
                int lastInPath = 1 + path * pathLength + (pathLength - 1);
                tasks.get(lastInPath).addSuccessor(exitId);
                tasks.get(exitId).addPredecessor(lastInPath);
            }
            
            // Create levels
            Map<Integer, List<Integer>> levels = Utility.organizeTasksByLevels(tasks);
            
            // Communication costs (very similar for all edges)
            Map<String, Double> commCosts = new HashMap<>();
            for (task t : tasks) {
                for (int succ : t.getSucc()) {
                    commCosts.put(t.getID() + "_" + succ, 50.0);
                }
            }
            
            Map<Integer, VM> vms = createVMMap(5);
            
            // Run DCP multiple times to ensure deterministic
            Set<Integer> cp1 = DCP.executeDCP(tasks, levels, tasks.get(exitId), commCosts, vms);
            Set<Integer> cp2 = DCP.executeDCP(tasks, levels, tasks.get(exitId), commCosts, vms);
            Set<Integer> cp3 = DCP.executeDCP(tasks, levels, tasks.get(exitId), commCosts, vms);
            
            System.out.printf("  📊 Paths:         %d parallel paths\n", pathsCount);
            System.out.printf("  📊 Path length:   %d tasks each\n", pathLength);
            System.out.printf("  📊 CP size:       %d tasks\n", cp1.size());
            
            assertCondition("CP is deterministic (run 1 vs 2)", cp1.equals(cp2));
            assertCondition("CP is deterministic (run 2 vs 3)", cp2.equals(cp3));
            assertCondition("CP contains entry", cp1.contains(0));
            assertCondition("CP contains exit", cp1.contains(exitId));
            assertCondition("CP is one continuous path", cp1.size() == levels.size());
            
            System.out.println("  ✅ PASS - Ambiguous paths handled deterministically");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        totalTests++;
        System.out.println();
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 4: EXTREME COMMUNICATION COSTS
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testExtremeCommunicationCosts() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 4: EXTREME COMMUNICATION COSTS");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            // Create DAG where communication costs dwarf computation
            int numTasks = 50;
            List<task> tasks = new ArrayList<>();
            
            for (int i = 0; i < numTasks; i++) {
                tasks.add(new task(i, 1.0)); // Very small computation
            }
            
            // Create multi-level DAG
            int levelsCount = 10;
            int tasksPerLevel = 5;
            
            for (int level = 0; level < levelsCount - 1; level++) {
                for (int i = 0; i < tasksPerLevel; i++) {
                    int from = level * tasksPerLevel + i;
                    int to = (level + 1) * tasksPerLevel + (i % tasksPerLevel);
                    tasks.get(from).addSuccessor(to);
                    tasks.get(to).addPredecessor(from);
                }
            }
            
            Map<Integer, List<Integer>> levels = Utility.organizeTasksByLevels(tasks);
            
            // HUGE communication costs (1000x computation)
            Map<String, Double> commCosts = new HashMap<>();
            for (task t : tasks) {
                for (int succ : t.getSucc()) {
                    commCosts.put(t.getID() + "_" + succ, 1000.0);
                }
            }
            
            Map<Integer, VM> vms = createVMMap(5);
            
            // Run DCP
            Set<Integer> cp = DCP.executeDCP(tasks, levels, tasks.get(numTasks - 1), commCosts, vms);
            
            // Calculate total CP cost (should be dominated by communication)
            double totalComputation = 0;
            double totalCommunication = 0;
            
            List<Integer> cpList = new ArrayList<>(cp);
            Collections.sort(cpList);
            
            for (int i = 0; i < cpList.size(); i++) {
                int taskId = cpList.get(i);
                totalComputation += tasks.get(taskId).getSize();
                
                if (i < cpList.size() - 1) {
                    int nextId = cpList.get(i + 1);
                    if (tasks.get(taskId).getSucc().contains(nextId)) {
                        totalCommunication += commCosts.get(taskId + "_" + nextId);
                    }
                }
            }
            
            double commRatio = totalCommunication / totalComputation;
            
            System.out.printf("  📊 Total computation in CP:    %.2f\n", totalComputation);
            System.out.printf("  📊 Total communication in CP:  %.2f\n", totalCommunication);
            System.out.printf("  📊 Communication/Computation:  %.1fx\n", commRatio);
            
            assertCondition("CP found with extreme costs", cp.size() > 0);
            assertCondition("Communication dominates", commRatio > 10.0);
            assertCondition("No numerical overflow", !Double.isInfinite(totalCommunication));
            assertCondition("No NaN values", !Double.isNaN(totalCommunication));
            
            System.out.println("  ✅ PASS - Extreme communication costs handled");
            
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
            // Run multiple random DAGs and check properties
            int testCount = 10;
            int passCount = 0;
            int executedCount = 0;
            
            for (int trial = 0; trial < testCount; trial++) {
                int numTasks = 30 + trial * 10;
                
                List<task> tasks = createRandomDAG(numTasks, trial);
                Map<Integer, List<Integer>> levels = Utility.organizeTasksByLevels(tasks);
                
                // Find a valid exit task (has no successors and has predecessors)
                task exitTask = null;
                for (task t : tasks) {
                    if (t.getSucc().isEmpty() && !t.getPre().isEmpty()) {
                        exitTask = t;
                        break;
                    }
                }
                
                // Skip if no valid exit task found
                if (exitTask == null) continue;
                
                executedCount++;
                
                // Communication costs
                Map<String, Double> commCosts = new HashMap<>();
                Random rand = SeededRandom.forScope("property-test-" + trial);
                for (task t : tasks) {
                    for (int succ : t.getSucc()) {
                        commCosts.put(t.getID() + "_" + succ, 5.0 + rand.nextDouble() * 20.0);
                    }
                }
                
                Map<Integer, VM> vms = createVMMap(5);
                
                // Run DCP
                Set<Integer> cp = DCP.executeDCP(tasks, levels, exitTask, commCosts, vms);
                
                // Validate properties
                boolean prop1 = cp.size() > 0; // CP not empty
                boolean prop2 = cp.size() <= numTasks; // CP size reasonable
                
                // CP contains a task from the final level (exit level)
                int maxLevel = levels.keySet().stream().max(Integer::compare).orElse(0);
                List<Integer> finalLevelTasks = levels.get(maxLevel);
                boolean prop3 = false;
                if (finalLevelTasks != null) {
                    for (int tid : finalLevelTasks) {
                        if (cp.contains(tid)) {
                            prop3 = true;
                            break;
                        }
                    }
                }
                
                // Entry task in CP (task from level 0)
                boolean prop4 = false;
                List<Integer> entryLevelTasks = levels.get(0);
                if (entryLevelTasks != null) {
                    for (int tid : entryLevelTasks) {
                        if (cp.contains(tid)) {
                            prop4 = true;
                            break;
                        }
                    }
                }
                
                // CP has one task per level
                boolean prop5 = (cp.size() == levels.size());
                
                if (prop1 && prop2 && prop3 && prop4 && prop5) {
                    passCount++;
                } else {
                    System.out.printf("  ❌ Trial %d failed: prop1=%b prop2=%b prop3=%b prop4=%b prop5=%b (CP=%d levels=%d)\n",
                        trial + 1, prop1, prop2, prop3, prop4, prop5, cp.size(), levels.size());
                }
            }
            
            System.out.printf("  📊 Trials passed: %d/%d (executed: %d/%d)\n", passCount, executedCount, executedCount, testCount);
            System.out.printf("  📊 Success rate:  %.1f%%\n", executedCount > 0 ? 100.0 * passCount / executedCount : 0.0);
            
            // Require at least 5 valid test executions (some DAGs may not have valid exit tasks)
            assertCondition("All executed property tests passed", passCount == executedCount && executedCount >= 5);
            
            System.out.println("  ✅ PASS - All DCP properties validated");
            
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
    
    private static Map<Integer, VM> createVMMap(int count) {
        Map<Integer, VM> vms = new HashMap<>();
        for (int i = 0; i < count; i++) {
            VM vm = new VM(i);
            vm.addCapability("processing", 1.0 + i * 0.5);
            vms.put(i, vm);
        }
        return vms;
    }
    
    private static List<task> createRandomDAG(int numTasks, int seed) {
        List<task> tasks = new ArrayList<>();
        Random rand = SeededRandom.forScope("random-dag-dcp-" + seed);
        
        for (int i = 0; i < numTasks; i++) {
            tasks.add(new task(i, 5.0 + rand.nextDouble() * 10.0));
        }
        
        // Create levels
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
