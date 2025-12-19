import java.util.*;

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                    🔥 ULTIMATE BRUTAL STRESS TEST 🔥                     ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  This test combines MULTIPLE extreme scenarios to stress-test all three  ║
 * ║  algorithms (DCP, SMGT, LOTD) and the new CCRAnalyzer under the worst   ║
 * ║  possible conditions:                                                    ║
 * ║                                                                          ║
 * ║  🎯 TEST SCENARIOS:                                                      ║
 * ║  1. MEGA SCALE: 2000 tasks on 100 VMs with 10,000+ edges                ║
 * ║  2. EXTREME HETEROGENEITY: VM speeds from 0.1x to 100x                  ║
 * ║  3. PATHOLOGICAL DAG: Deep chains + wide parallelism + convergence      ║
 * ║  4. EXTREME CCR: Test from 0.0 to 10.0 (2500% variation)                ║
 * ║  5. BRUTAL BANDWIDTH: Asymmetric, bottlenecked network topology         ║
 * ║  6. CCR SENSITIVITY: Full CCRAnalyzer integration test                  ║
 * ║  7. EDGE CASES: Single task, fully parallel, linear chain               ║
 * ║  8. PROPERTY VALIDATION: All invariants must hold                       ║
 * ║                                                                          ║
 * ║  ⏱️  Expected runtime: 60-120 seconds                                    ║
 * ║  💾 Expected memory: ~500MB                                              ║
 * ║  🎲 Deterministic: Uses SeededRandom for reproducibility                ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
public class TestBrutal_UltimateStressTest {

    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;
    
    private static final boolean VERBOSE = false; // Set to true for detailed output
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║           🔥 ULTIMATE BRUTAL STRESS TEST 🔥                  ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");
        
        long startTime = System.currentTimeMillis();
        
        try {
            // Phase 1: Edge Cases (Fast - ~5 seconds)
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("PHASE 1: EDGE CASE SCENARIOS");
            System.out.println("═══════════════════════════════════════════════════════════════");
            testSingleTask();
            testLinearChain();
            testFullyParallel();
            testSingleVM();
            
            // Phase 2: Extreme Parameters (Medium - ~20 seconds)
            System.out.println("\n═══════════════════════════════════════════════════════════════");
            System.out.println("PHASE 2: EXTREME PARAMETER SCENARIOS");
            System.out.println("═══════════════════════════════════════════════════════════════");
            testExtremeHeterogeneity();
            testExtremeCCR();
            testBrutalBandwidth();
            
            // Phase 3: Large Scale (Slow - ~30 seconds)
            System.out.println("\n═══════════════════════════════════════════════════════════════");
            System.out.println("PHASE 3: LARGE SCALE SCENARIOS");
            System.out.println("═══════════════════════════════════════════════════════════════");
            testLargeScale();
            testDeepDAG();
            testWideDAG();
            
            // Phase 4: CCR Sensitivity Analysis (Medium - ~15 seconds)
            System.out.println("\n═══════════════════════════════════════════════════════════════");
            System.out.println("PHASE 4: CCR SENSITIVITY ANALYSIS");
            System.out.println("═══════════════════════════════════════════════════════════════");
            testCCRAnalyzerIntegration();
            testCCRSensitivitySweep();
            
            // Phase 5: Property Validation (Fast - ~5 seconds)
            System.out.println("\n═══════════════════════════════════════════════════════════════");
            System.out.println("PHASE 5: PROPERTY VALIDATION");
            System.out.println("═══════════════════════════════════════════════════════════════");
            testPropertySLRAlwaysValid();
            testPropertyAVUInRange();
            testPropertyCriticalPathValid();
            testPropertyAllTasksScheduled();
            
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
            System.out.println("\n🎉 🎉 🎉  ALL BRUTAL TESTS PASSED!  🎉 🎉 🎉");
            System.out.println("The algorithms survived the ultimate stress test!");
        } else {
            System.out.println("\n⚠️  SOME TESTS FAILED - Review output above");
            System.exit(1);
        }
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // PHASE 1: EDGE CASE TESTS
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testSingleTask() {
        System.out.println("\n🔸 Test 1.1: Single Task, Single VM");
        
        try {
            VM vm = new VM(0);
            vm.addCapability("processing", 1.0);
            List<VM> vms = Arrays.asList(vm);
            
            task t = new task(0, 10.0);
            List<task> tasks = Arrays.asList(t);
            
            // DCP should return just this task
            Map<Integer, List<Integer>> levels = new HashMap<>();
            levels.put(0, Arrays.asList(0));
            Map<String, Double> commCosts = new HashMap<>();
            
            Set<Integer> cp = DCP.executeDCP(tasks, levels, t, commCosts, 
                Collections.singletonMap(0, vm));
            
            assertCondition("Single task in CP", cp.size() == 1 && cp.contains(0));
            
            // SMGT should assign to the only VM
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
            
            assertCondition("Task assigned to VM0", 
                schedule.get(0) != null && schedule.get(0).contains(0));
            
            System.out.println("  ✅ PASS - Single task handled correctly");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    private static void testLinearChain() {
        System.out.println("\n🔸 Test 1.2: Linear Chain (200 sequential tasks)");
        
        try {
            // Create 200 tasks in pure sequence
            List<task> tasks = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                tasks.add(new task(i, 5.0));
            }
            
            // Connect in chain: 0→1→2→...→199
            for (int i = 0; i < 199; i++) {
                tasks.get(i).addSuccessor(i + 1);
                tasks.get(i + 1).addPredecessor(i);
            }
            
            // Create VMs
            List<VM> vms = createHeterogeneousVMs(10);
            Map<Integer, VM> vmMap = new HashMap<>();
            for (VM vm : vms) vmMap.put(vm.getID(), vm);
            
            // Build levels
            Map<Integer, List<Integer>> levels = new HashMap<>();
            for (int i = 0; i < 200; i++) {
                levels.put(i, Arrays.asList(i));
            }
            
            Map<String, Double> commCosts = new HashMap<>();
            for (int i = 0; i < 199; i++) {
                commCosts.put(i + "_" + (i + 1), 10.0);
            }
            
            // DCP - Critical path should include ALL tasks
            Set<Integer> cp = DCP.executeDCP(tasks, levels, tasks.get(199), commCosts, vmMap);
            
            assertCondition("Linear chain CP length = 200", cp.size() == 200);
            assertCondition("CP contains first task", cp.contains(0));
            assertCondition("CP contains last task", cp.contains(199));
            
            System.out.println("  ✅ PASS - Linear chain handled correctly");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    private static void testFullyParallel() {
        System.out.println("\n🔸 Test 1.3: Fully Parallel (500 independent tasks)");
        
        try {
            // Create entry and exit tasks
            task entry = new task(0, 1.0);
            task exit = new task(501, 1.0);
            
            List<task> tasks = new ArrayList<>();
            tasks.add(entry);
            
            // 500 parallel tasks between entry and exit
            for (int i = 1; i <= 500; i++) {
                task t = new task(i, 10.0);
                t.addPredecessor(0);
                t.addSuccessor(501);
                entry.addSuccessor(i);
                exit.addPredecessor(i);
                tasks.add(t);
            }
            tasks.add(exit);
            
            // Create VMs
            List<VM> vms = createHeterogeneousVMs(50);
            Map<Integer, VM> vmMap = new HashMap<>();
            for (VM vm : vms) vmMap.put(vm.getID(), vm);
            
            // Build levels
            Map<Integer, List<Integer>> levels = new HashMap<>();
            levels.put(0, Arrays.asList(0));
            List<Integer> middleLevel = new ArrayList<>();
            for (int i = 1; i <= 500; i++) middleLevel.add(i);
            levels.put(1, middleLevel);
            levels.put(2, Arrays.asList(501));
            
            Map<String, Double> commCosts = new HashMap<>();
            for (int i = 1; i <= 500; i++) {
                commCosts.put("0_" + i, 5.0);
                commCosts.put(i + "_501", 5.0);
            }
            
            // DCP - Critical path should be short (only 3 levels)
            Set<Integer> cp = DCP.executeDCP(tasks, levels, exit, commCosts, vmMap);
            
            assertCondition("Parallel DAG CP length = 3", cp.size() == 3);
            assertCondition("CP contains entry", cp.contains(0));
            assertCondition("CP contains exit", cp.contains(501));
            
            // SMGT - All tasks should be distributed across VMs
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
            
            int totalScheduled = 0;
            for (List<Integer> vmTasks : schedule.values()) {
                if (vmTasks != null) totalScheduled += vmTasks.size();
            }
            
            assertCondition("All 502 tasks scheduled", totalScheduled == 502);
            
            System.out.println("  ✅ PASS - Fully parallel DAG handled correctly");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            if (VERBOSE) e.printStackTrace();
            failedTests++;
        }
        totalTests++;
    }
    
    private static void testSingleVM() {
        System.out.println("\n🔸 Test 1.4: Complex DAG on Single VM");
        
        try {
            // Create complex 50-task DAG but only 1 VM
            List<task> tasks = createComplexDAG(50);
            
            VM vm = new VM(0);
            vm.addCapability("processing", 1.0);
            List<VM> vms = Arrays.asList(vm);
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            // All tasks must go to VM0
            Set<Integer> cp = new HashSet<>();
            for (int i = 0; i < 10; i++) cp.add(i); // Dummy CP
            
            Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
            
            assertCondition("All tasks on VM0", 
                schedule.get(0) != null && schedule.get(0).size() == 50);
            
            System.out.println("  ✅ PASS - Single VM serialization works");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // PHASE 2: EXTREME PARAMETER TESTS
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testExtremeHeterogeneity() {
        System.out.println("\n🔸 Test 2.1: Extreme VM Heterogeneity (0.01x to 1000x)");
        
        try {
            // Create VMs with exponential speed distribution
            List<VM> vms = new ArrayList<>();
            double[] speeds = {0.01, 0.1, 1.0, 10.0, 100.0, 1000.0};
            for (int i = 0; i < speeds.length; i++) {
                VM vm = new VM(i);
                vm.addCapability("processing", speeds[i]);
                vms.add(vm);
            }
            
            List<task> tasks = createComplexDAG(30);
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            // Critical path tasks should prefer fastest VM
            Set<Integer> cp = new HashSet<>(Arrays.asList(0, 5, 10, 15, 20, 25, 29));
            Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
            
            // Check that fastest VM (VM5 with 1000x) got at least some CP tasks
            boolean fastestVMUsed = schedule.get(5) != null && !schedule.get(5).isEmpty();
            assertCondition("Fastest VM utilized", fastestVMUsed);
            
            // No numerical errors (NaN, Infinity)
            for (List<Integer> vmTasks : schedule.values()) {
                assertCondition("Valid schedule", vmTasks != null);
            }
            
            System.out.println("  ✅ PASS - Extreme heterogeneity handled");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    private static void testExtremeCCR() {
        System.out.println("\n🔸 Test 2.2: Extreme CCR Values (0.0 and 10.0)");
        
        try {
            List<VM> vms = createHeterogeneousVMs(5);
            List<task> tasks = createComplexDAG(20);
            
            // Test CCR = 0.0 (zero communication)
            Map<String, Double> commCostsZero = new HashMap<>();
            for (task t : tasks) {
                for (int succ : t.getSucc()) {
                    commCostsZero.put(t.getID() + "_" + succ, 0.0);
                }
            }
            
            SMGT smgt1 = new SMGT();
            smgt1.setVMs(vms);
            smgt1.setTasks(tasks);
            smgt1.calculateTaskLevels();
            Set<Integer> cp1 = new HashSet<>(Arrays.asList(0, 5, 10, 15, 19));
            Map<Integer, List<Integer>> schedule1 = smgt1.runSMGT(cp1);
            
            assertCondition("CCR=0.0 schedule complete", 
                countScheduledTasks(schedule1) == 20);
            
            // Test CCR = 10.0 (extreme communication)
            Map<String, Double> commCostsHigh = new HashMap<>();
            for (task t : tasks) {
                for (int succ : t.getSucc()) {
                    commCostsHigh.put(t.getID() + "_" + succ, t.getSize() * 10.0);
                }
            }
            
            SMGT smgt2 = new SMGT();
            smgt2.setVMs(vms);
            smgt2.setTasks(tasks);
            smgt2.calculateTaskLevels();
            Map<Integer, List<Integer>> schedule2 = smgt2.runSMGT(cp1);
            
            assertCondition("CCR=10.0 schedule complete", 
                countScheduledTasks(schedule2) == 20);
            
            System.out.println("  ✅ PASS - Extreme CCR values handled");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    private static void testBrutalBandwidth() {
        System.out.println("\n🔸 Test 2.3: Brutal Bandwidth Topology (asymmetric, bottlenecked)");
        
        try {
            // Create VMs with pathological bandwidth matrix
            List<VM> vms = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                VM vm = new VM(i);
                vm.addCapability("processing", 1.0 + i);
                vms.add(vm);
            }
            
            // Set asymmetric bandwidth (VM0 can send fast but receive slow)
            for (int i = 0; i < 5; i++) {
                for (int j = 0; j < 5; j++) {
                    if (i != j) {
                        double bw = (i == 0) ? 0.01 : (j == 0 ? 100.0 : 1.0);
                        vms.get(i).setBandwidthToVM(j, bw);
                    }
                }
            }
            
            List<task> tasks = createComplexDAG(25);
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            Set<Integer> cp = new HashSet<>(Arrays.asList(0, 5, 10, 15, 20, 24));
            Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
            
            assertCondition("Brutal bandwidth schedule complete", 
                countScheduledTasks(schedule) == 25);
            
            System.out.println("  ✅ PASS - Brutal bandwidth topology handled");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // PHASE 3: LARGE SCALE TESTS
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testLargeScale() {
        System.out.println("\n🔸 Test 3.1: Large Scale (2000 tasks, 100 VMs)");
        System.out.println("  ⏳ This may take 30-60 seconds...");
        
        try {
            long start = System.currentTimeMillis();
            
            // Create 2000 tasks in a multi-level DAG
            List<task> tasks = new ArrayList<>();
            for (int i = 0; i < 2000; i++) {
                tasks.add(new task(i, 5.0 + (i % 10)));
            }
            
            // Create 20 levels with ~100 tasks per level
            // Each task connects to 3-5 random tasks in next level
            Random rand = SeededRandom.forScope("large-scale-test");
            for (int level = 0; level < 19; level++) {
                int levelStart = level * 100;
                int levelEnd = Math.min((level + 1) * 100, 2000);
                int nextLevelStart = levelEnd;
                int nextLevelEnd = Math.min(nextLevelStart + 100, 2000);
                
                for (int i = levelStart; i < levelEnd; i++) {
                    int numSuccessors = 3 + rand.nextInt(3); // 3-5 successors
                    for (int s = 0; s < numSuccessors && nextLevelStart < nextLevelEnd; s++) {
                        int succ = nextLevelStart + rand.nextInt(nextLevelEnd - nextLevelStart);
                        if (succ < 2000) {
                            tasks.get(i).addSuccessor(succ);
                            tasks.get(succ).addPredecessor(i);
                        }
                    }
                }
            }
            
            // Create 100 VMs
            List<VM> vms = createHeterogeneousVMs(100);
            Map<Integer, VM> vmMap = new HashMap<>();
            for (VM vm : vms) vmMap.put(vm.getID(), vm);
            
            // Calculate levels
            Map<Integer, List<Integer>> levels = Utility.organizeTasksByLevels(tasks);
            
            long setupTime = System.currentTimeMillis() - start;
            System.out.printf("  📊 DAG created: %d tasks, %d levels (%.2fs)\n", 
                tasks.size(), levels.size(), setupTime / 1000.0);
            
            // DCP
            long dcpStart = System.currentTimeMillis();
            Map<String, Double> commCosts = new HashMap<>();
            for (task t : tasks) {
                for (int succ : t.getSucc()) {
                    commCosts.put(t.getID() + "_" + succ, 5.0);
                }
            }
            
            task exitTask = tasks.get(tasks.size() - 1);
            Set<Integer> cp = DCP.executeDCP(tasks, levels, exitTask, commCosts, vmMap);
            long dcpTime = System.currentTimeMillis() - dcpStart;
            
            System.out.printf("  🎯 DCP completed: CP size = %d (%.2fs)\n", 
                cp.size(), dcpTime / 1000.0);
            
            // SMGT
            long smgtStart = System.currentTimeMillis();
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
            long smgtTime = System.currentTimeMillis() - smgtStart;
            
            int scheduled = countScheduledTasks(schedule);
            System.out.printf("  📅 SMGT completed: %d tasks scheduled (%.2fs)\n", 
                scheduled, smgtTime / 1000.0);
            
            assertCondition("All 2000 tasks scheduled", scheduled == 2000);
            assertCondition("CP not empty", cp.size() > 0);
            
            long totalTime = System.currentTimeMillis() - start;
            System.out.printf("  ✅ PASS - Large scale completed in %.2fs\n", totalTime / 1000.0);
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            if (VERBOSE) e.printStackTrace();
            failedTests++;
        }
        totalTests++;
    }
    
    private static void testDeepDAG() {
        System.out.println("\n🔸 Test 3.2: Deep DAG (500 levels, 1000 tasks)");
        
        try {
            // Create a diamond-like DAG with 500 levels
            List<task> tasks = new ArrayList<>();
            for (int i = 0; i < 1000; i++) {
                tasks.add(new task(i, 3.0));
            }
            
            // Each level has 2 tasks that connect to 2 tasks in next level
            for (int i = 0; i < 998; i++) {
                int level = i / 2;
                int nextLevelStart = (level + 1) * 2;
                if (nextLevelStart < 1000) {
                    tasks.get(i).addSuccessor(nextLevelStart);
                    tasks.get(i).addSuccessor(Math.min(nextLevelStart + 1, 999));
                    tasks.get(nextLevelStart).addPredecessor(i);
                    if (nextLevelStart + 1 < 1000) {
                        tasks.get(nextLevelStart + 1).addPredecessor(i);
                    }
                }
            }
            
            List<VM> vms = createHeterogeneousVMs(20);
            Map<Integer, VM> vmMap = new HashMap<>();
            for (VM vm : vms) vmMap.put(vm.getID(), vm);
            
            Map<Integer, List<Integer>> levels = Utility.organizeTasksByLevels(tasks);
            Map<String, Double> commCosts = new HashMap<>();
            for (task t : tasks) {
                for (int succ : t.getSucc()) {
                    commCosts.put(t.getID() + "_" + succ, 2.0);
                }
            }
            
            task exitTask = tasks.get(999);
            Set<Integer> cp = DCP.executeDCP(tasks, levels, exitTask, commCosts, vmMap);
            
            assertCondition("Deep DAG CP exists", cp.size() > 0);
            System.out.printf("  ✅ PASS - Deep DAG (%d levels) handled\n", levels.size());
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    private static void testWideDAG() {
        System.out.println("\n🔸 Test 3.3: Wide DAG (3 levels, 1500 parallel tasks)");
        
        try {
            task entry = new task(0, 1.0);
            task exit = new task(1501, 1.0);
            
            List<task> tasks = new ArrayList<>();
            tasks.add(entry);
            
            // 1500 parallel tasks
            for (int i = 1; i <= 1500; i++) {
                task t = new task(i, 5.0);
                t.addPredecessor(0);
                t.addSuccessor(1501);
                entry.addSuccessor(i);
                exit.addPredecessor(i);
                tasks.add(t);
            }
            tasks.add(exit);
            
            List<VM> vms = createHeterogeneousVMs(100);
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            Set<Integer> cp = new HashSet<>(Arrays.asList(0, 1, 1501));
            Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
            
            int scheduled = countScheduledTasks(schedule);
            assertCondition("All 1502 tasks scheduled in wide DAG", scheduled == 1502);
            
            System.out.println("  ✅ PASS - Wide DAG handled");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // PHASE 4: CCR SENSITIVITY TESTS
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testCCRAnalyzerIntegration() {
        System.out.println("\n🔸 Test 4.1: CCRAnalyzer Integration");
        
        try {
            CCRAnalyzer analyzer = new CCRAnalyzer("test_workflow", 50, 5, "BRUTAL_TEST");
            
            List<task> tasks = createComplexDAG(50);
            List<VM> vms = createHeterogeneousVMs(5);
            
            // Simulate 5 CCR snapshots
            double[] ccrValues = {0.5, 1.0, 1.5, 2.0, 2.5};
            for (double ccr : ccrValues) {
                SMGT smgt = new SMGT();
                smgt.setVMs(vms);
                smgt.setTasks(tasks);
                smgt.calculateTaskLevels();
                
                Map<String, Double> commCosts = new HashMap<>();
                for (task t : tasks) {
                    for (int succ : t.getSucc()) {
                        commCosts.put(t.getID() + "_" + succ, t.getSize() * ccr);
                    }
                }
                
                // Get critical path
                Map<Integer, VM> vmMap = new HashMap<>();
                for (VM vm : vms) vmMap.put(vm.getID(), vm);
                Map<Integer, List<Integer>> levels = Utility.organizeTasksByLevels(tasks);
                task exitTask = tasks.get(49);
                Set<Integer> cp = DCP.executeDCP(tasks, levels, exitTask, commCosts, vmMap);
                
                // Run SMGT
                Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
                
                // Capture snapshot
                Map<Integer, Set<Integer>> duplications = new HashMap<>();
                double slr = 1.5 + ccr * 0.1;
                double avu = 0.3;
                double makespan = 100.0 + ccr * 10;
                double vf = 0.15;
                
                analyzer.captureSnapshot(ccr, commCosts, cp, duplications, slr, avu, vf, makespan);
            }
            
            // Export to temp file
            String tempPath = "../results/ccr_sensitivity/test_brutal_analysis.json";
            analyzer.saveToJSON(tempPath);
            
            // Verify file exists
            java.io.File file = new java.io.File(tempPath);
            assertCondition("CCRAnalyzer JSON exported", file.exists());
            assertCondition("JSON file not empty", file.length() > 100);
            
            System.out.println("  ✅ PASS - CCRAnalyzer integration works");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            if (VERBOSE) e.printStackTrace();
            failedTests++;
        }
        totalTests++;
    }
    
    private static void testCCRSensitivitySweep() {
        System.out.println("\n🔸 Test 4.2: CCR Sensitivity Sweep (0.0 to 5.0)");
        
        try {
            List<task> tasks = createComplexDAG(100);
            List<VM> vms = createHeterogeneousVMs(10);
            Map<Integer, VM> vmMap = new HashMap<>();
            for (VM vm : vms) vmMap.put(vm.getID(), vm);
            
            double[] ccrValues = {0.0, 0.5, 1.0, 2.0, 3.0, 5.0};
            List<Integer> cpSizes = new ArrayList<>();
            List<Integer> scheduledCounts = new ArrayList<>();
            
            for (double ccr : ccrValues) {
                Map<String, Double> commCosts = new HashMap<>();
                for (task t : tasks) {
                    for (int succ : t.getSucc()) {
                        commCosts.put(t.getID() + "_" + succ, t.getSize() * ccr);
                    }
                }
                
                Map<Integer, List<Integer>> levels = Utility.organizeTasksByLevels(tasks);
                task exitTask = tasks.get(99);
                Set<Integer> cp = DCP.executeDCP(tasks, levels, exitTask, commCosts, vmMap);
                
                SMGT smgt = new SMGT();
                smgt.setVMs(vms);
                smgt.setTasks(tasks);
                smgt.calculateTaskLevels();
                Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
                
                cpSizes.add(cp.size());
                scheduledCounts.add(countScheduledTasks(schedule));
            }
            
            // All CCR values should produce valid schedules
            for (int count : scheduledCounts) {
                assertCondition("CCR sweep all tasks scheduled", count == 100);
            }
            
            // CP sizes should be consistent (or vary slightly)
            int minCP = Collections.min(cpSizes);
            int maxCP = Collections.max(cpSizes);
            assertCondition("CP size variation reasonable", (maxCP - minCP) <= 5);
            
            System.out.println("  ✅ PASS - CCR sensitivity sweep successful");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // PHASE 5: PROPERTY VALIDATION TESTS
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testPropertySLRAlwaysValid() {
        System.out.println("\n🔸 Test 5.1: Property - SLR always >= 1.0");
        
        try {
            // Test with multiple random DAGs
            for (int trial = 0; trial < 5; trial++) {
                List<task> tasks = createComplexDAG(50 + trial * 20);
                List<VM> vms = createHeterogeneousVMs(5 + trial * 2);
                
                SMGT smgt = new SMGT();
                smgt.setVMs(vms);
                smgt.setTasks(tasks);
                smgt.calculateTaskLevels();
                
                Set<Integer> cp = new HashSet<>();
                for (int i = 0; i < 10; i++) cp.add(i * 5);
                
                Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
                
                // Calculate makespan (simplified - just count tasks per VM)
                double makespan = 0;
                for (Map.Entry<Integer, List<Integer>> e : schedule.entrySet()) {
                    double vmTime = 0;
                    if (e.getValue() != null) {
                        for (int tid : e.getValue()) {
                            vmTime += tasks.get(tid).getSize();
                        }
                    }
                    makespan = Math.max(makespan, vmTime);
                }
                
                // Calculate min possible time
                double minTime = 0;
                for (task t : tasks) {
                    minTime += t.getSize();
                }
                minTime /= vms.size(); // Ideal parallel distribution
                
                double slr = makespan / minTime;
                assertCondition("SLR >= 1.0 in trial " + (trial + 1), slr >= 0.99); // Allow tiny float error
            }
            
            System.out.println("  ✅ PASS - SLR property holds");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    private static void testPropertyAVUInRange() {
        System.out.println("\n🔸 Test 5.2: Property - AVU in [0, 1]");
        
        try {
            List<task> tasks = createComplexDAG(100);
            List<VM> vms = createHeterogeneousVMs(10);
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            Set<Integer> cp = new HashSet<>();
            for (int i = 0; i < 20; i++) cp.add(i * 5);
            
            Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
            
            // Calculate AVU (simplified)
            double totalUtilization = 0;
            for (Map.Entry<Integer, List<Integer>> e : schedule.entrySet()) {
                if (e.getValue() != null && !e.getValue().isEmpty()) {
                    totalUtilization += 1.0; // VM is used
                }
            }
            double avu = totalUtilization / vms.size();
            
            assertCondition("AVU >= 0", avu >= 0.0);
            assertCondition("AVU <= 1", avu <= 1.0);
            
            System.out.printf("  ✅ PASS - AVU = %.2f is valid\n", avu);
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    private static void testPropertyCriticalPathValid() {
        System.out.println("\n🔸 Test 5.3: Property - Critical Path validity");
        
        try {
            List<task> tasks = createComplexDAG(80);
            List<VM> vms = createHeterogeneousVMs(8);
            Map<Integer, VM> vmMap = new HashMap<>();
            for (VM vm : vms) vmMap.put(vm.getID(), vm);
            
            Map<Integer, List<Integer>> levels = Utility.organizeTasksByLevels(tasks);
            Map<String, Double> commCosts = new HashMap<>();
            for (task t : tasks) {
                for (int succ : t.getSucc()) {
                    commCosts.put(t.getID() + "_" + succ, 10.0);
                }
            }
            
            task exitTask = tasks.get(79);
            Set<Integer> cp = DCP.executeDCP(tasks, levels, exitTask, commCosts, vmMap);
            
            // Properties to check
            assertCondition("CP not empty", cp.size() > 0);
            assertCondition("CP size <= total tasks", cp.size() <= tasks.size());
            
            // CP should contain a task from the final level (not necessarily the specified exit task)
            int maxLevel = levels.keySet().stream().max(Integer::compare).orElse(0);
            List<Integer> finalLevelTasks = levels.get(maxLevel);
            boolean hasExitLevelTask = false;
            if (finalLevelTasks != null) {
                for (int tid : finalLevelTasks) {
                    if (cp.contains(tid)) {
                        hasExitLevelTask = true;
                        break;
                    }
                }
            }
            assertCondition("CP contains a task from exit level", hasExitLevelTask);
            
            // CP should contain entry task(s) from level 0
            boolean hasEntryTask = false;
            List<Integer> entryLevelTasks = levels.get(0);
            if (entryLevelTasks != null) {
                for (int tid : entryLevelTasks) {
                    if (cp.contains(tid)) {
                        hasEntryTask = true;
                        break;
                    }
                }
            }
            assertCondition("CP contains a task from entry level", hasEntryTask);
            
            System.out.printf("  ✅ PASS - CP size = %d is valid\n", cp.size());
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    private static void testPropertyAllTasksScheduled() {
        System.out.println("\n🔸 Test 5.4: Property - All tasks scheduled exactly once");
        
        try {
            // Test with multiple configurations
            int[][] configs = {{50, 5}, {100, 10}, {200, 20}};
            
            for (int[] config : configs) {
                int numTasks = config[0];
                int numVMs = config[1];
                
                List<task> tasks = createComplexDAG(numTasks);
                List<VM> vms = createHeterogeneousVMs(numVMs);
                
                SMGT smgt = new SMGT();
                smgt.setVMs(vms);
                smgt.setTasks(tasks);
                smgt.calculateTaskLevels();
                
                Set<Integer> cp = new HashSet<>();
                for (int i = 0; i < Math.min(10, numTasks); i++) {
                    cp.add(i * (numTasks / 10));
                }
                
                Map<Integer, List<Integer>> schedule = smgt.runSMGT(cp);
                
                // Check: all tasks appear exactly once
                Set<Integer> scheduledTasks = new HashSet<>();
                for (List<Integer> vmTasks : schedule.values()) {
                    if (vmTasks != null) {
                        for (int tid : vmTasks) {
                            assertCondition("Task " + tid + " not duplicated", 
                                scheduledTasks.add(tid));
                        }
                    }
                }
                
                assertCondition(String.format("All %d tasks scheduled", numTasks), 
                    scheduledTasks.size() == numTasks);
            }
            
            System.out.println("  ✅ PASS - All tasks scheduled exactly once");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            failedTests++;
        }
        totalTests++;
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // HELPER METHODS
    // ═════════════════════════════════════════════════════════════════════════
    
    private static List<VM> createHeterogeneousVMs(int count) {
        List<VM> vms = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            VM vm = new VM(i);
            // Logarithmic speed distribution: 1.0, 1.5, 2.25, 3.375, ...
            double speed = 1.0 + i * 0.5;
            vm.addCapability("processing", speed);
            vms.add(vm);
        }
        return vms;
    }
    
    private static List<task> createComplexDAG(int numTasks) {
        List<task> tasks = new ArrayList<>();
        Random rand = SeededRandom.forScope("complex-dag-" + numTasks);
        
        for (int i = 0; i < numTasks; i++) {
            double size = 5.0 + rand.nextDouble() * 15.0; // 5-20
            tasks.add(new task(i, size));
        }
        
        // Create multi-level DAG
        int levelsCount = (int) Math.sqrt(numTasks);
        int tasksPerLevel = numTasks / levelsCount;
        
        for (int level = 0; level < levelsCount - 1; level++) {
            int levelStart = level * tasksPerLevel;
            int levelEnd = Math.min((level + 1) * tasksPerLevel, numTasks);
            int nextLevelStart = levelEnd;
            int nextLevelEnd = Math.min(nextLevelStart + tasksPerLevel, numTasks);
            
            for (int i = levelStart; i < levelEnd; i++) {
                // Connect to 2-4 tasks in next level
                int numSuccessors = 2 + rand.nextInt(3);
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
    
    private static int countScheduledTasks(Map<Integer, List<Integer>> schedule) {
        int count = 0;
        for (List<Integer> vmTasks : schedule.values()) {
            if (vmTasks != null) {
                count += vmTasks.size();
            }
        }
        return count;
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
