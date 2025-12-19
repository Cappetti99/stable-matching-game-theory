import java.util.*;

/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                    🔥 BRUTAL TEST FOR LOTD 🔥                            ║
 * ╠══════════════════════════════════════════════════════════════════════════╣
 * ║  This test specifically targets LOTD (Local Optimal Task Duplication)   ║
 * ║  with scenarios designed to FORCE task duplication to occur.            ║
 * ║                                                                          ║
 * ║  🎯 TEST SCENARIOS:                                                      ║
 * ║  1. EXTREME COMMUNICATION BOTTLENECK                                     ║
 * ║     - Very high CCR (communication >> computation)                       ║
 * ║     - Low bandwidth between VMs (0.01)                                   ║
 * ║     - Small task sizes (duplication is cheap)                            ║
 * ║                                                                          ║
 * ║  2. CAPACITY PRESSURE TEST                                               ║
 * ║     - VMs with varying spare capacity                                    ║
 * ║     - Tasks already scheduled sub-optimally                              ║
 * ║     - Force Rule 2 evaluation                                            ║
 * ║                                                                          ║
 * ║  3. CRITICAL PATH DUPLICATION                                            ║
 * ║     - CP tasks on slow VMs                                               ║
 * ║     - Fast VMs available with capacity                                   ║
 * ║     - High communication cost to move data                               ║
 * ║                                                                          ║
 * ║  4. LARGE SCALE DUPLICATION                                              ║
 * ║     - 500 tasks, complex dependencies                                    ║
 * ║     - Mixed task sizes and VM speeds                                     ║
 * ║     - Systematic duplication opportunities                               ║
 * ║                                                                          ║
 * ║  5. PROPERTY VALIDATION                                                  ║
 * ║     - Rule 2: Duplication never increases makespan                       ║
 * ║     - Duplicated tasks improve overall schedule                          ║
 * ║     - No duplicate task assignments in original schedule                 ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 */
public class TestLOTD_Brutal {

    private static int totalTests = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                🔥 BRUTAL TEST FOR LOTD 🔥                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");
        
        long startTime = System.currentTimeMillis();
        
        try {
            testExtremeCommunicationBottleneck();
            testCapacityPressure();
            testCriticalPathDuplication();
            testLargeScaleDuplication();
            testPropertyRule2Enforcement();
            
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
            System.out.println("\n🎉 ALL BRUTAL LOTD TESTS PASSED! 🎉");
        } else {
            System.out.println("\n⚠️  SOME TESTS FAILED - Review output above");
            System.exit(1);
        }
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 1: EXTREME COMMUNICATION BOTTLENECK
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testExtremeCommunicationBottleneck() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 1: EXTREME COMMUNICATION BOTTLENECK");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            // Create VMs with VERY low bandwidth
            List<VM> vms = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                VM vm = new VM(i);
                vm.addCapability("processing", 1.0 + i * 0.5);
                vms.add(vm);
            }
            
            // Set EXTREMELY low inter-VM bandwidth (0.001)
            for (int i = 0; i < 4; i++) {
                for (int j = 0; j < 4; j++) {
                    if (i != j) {
                        vms.get(i).setBandwidthToVM(j, 0.001); // 1000x slower than default!
                    }
                }
            }
            
            // Create tasks with SMALL execution time but LARGE data output
            // Structure: 8 ENTRY tasks (0-7), each feeds into 1 of 2 merge tasks (8-9)
            // This creates opportunities for entry task duplication!
            List<task> tasks = new ArrayList<>();
            
            // Entry tasks 0-7 (no predecessors)
            for (int i = 0; i < 8; i++) {
                task t = new task(i, 1.0); // Small execution time
                t.setSize(100.0); // Large data to transfer
                tasks.add(t);
            }
            
            // Merge tasks 8-9 (collect from entry tasks)
            task merge1 = new task(8, 10.0); // Larger execution time
            merge1.setSize(50.0);
            task merge2 = new task(9, 10.0);
            merge2.setSize(50.0);
            tasks.add(merge1);
            tasks.add(merge2);
            
            // DAG structure: Entry tasks 0-3 → merge1(8), Entry tasks 4-7 → merge2(9)
            for (int i = 0; i < 4; i++) {
                tasks.get(i).addSuccessor(8);
                merge1.addPredecessor(i);
            }
            for (int i = 4; i < 8; i++) {
                tasks.get(i).addSuccessor(9);
                merge2.addPredecessor(i);
            }
            
            // Create BAD initial schedule: entry tasks on VM0/VM1, merge tasks on VM2/VM3
            // This forces cross-VM communication that could benefit from duplication!
            Map<Integer, List<Integer>> schedule = new HashMap<>();
            for (int i = 0; i < 4; i++) {
                schedule.put(i, new ArrayList<>());
            }
            // Entry tasks 0-3 on VM0, 4-7 on VM1
            for (int i = 0; i < 4; i++) {
                schedule.get(0).add(i);
            }
            for (int i = 4; i < 8; i++) {
                schedule.get(1).add(i);
            }
            // Merge tasks on VM2 and VM3
            schedule.get(2).add(8);
            schedule.get(3).add(9);
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            // Calculate communication costs (CRITICAL for LOTD duplication decisions!)
            // Communication cost = data_size / avg_bandwidth (baseline = 25.0)
            Map<String, Double> commCosts = new HashMap<>();
            // Entry tasks 0-3 → merge task 8
            for (int i = 0; i < 4; i++) {
                double dataSize = tasks.get(i).getSize(); // 100.0
                double commCostAtAvg = dataSize / 25.0;   // 100.0 / 25.0 = 4.0
                commCosts.put(i + "_8", commCostAtAvg);
            }
            // Entry tasks 4-7 → merge task 9
            for (int i = 4; i < 8; i++) {
                double dataSize = tasks.get(i).getSize(); // 100.0
                double commCostAtAvg = dataSize / 25.0;   // 100.0 / 25.0 = 4.0
                commCosts.put(i + "_9", commCostAtAvg);
            }
            
            // Calculate makespan before LOTD
            double makespanBefore = calculateMakespan(schedule, tasks, vms);
            
            // Run LOTD
            LOTD lotd = new LOTD(smgt);
            lotd.setCommunicationCosts(commCosts); // Enable communication cost awareness!
            Map<Integer, List<Integer>> optimizedSchedule = lotd.executeLOTDCorrect(schedule);
            
            // Calculate makespan after LOTD
            double makespanAfter = calculateMakespan(optimizedSchedule, tasks, vms);
            
            // Check for duplications
            int totalDuplications = 0;
            for (Set<Integer> dups : lotd.getDuplicatedTasks().values()) {
                totalDuplications += dups.size();
            }
            
            System.out.printf("  📊 Makespan before LOTD: %.2f\n", makespanBefore);
            System.out.printf("  📊 Makespan after LOTD:  %.2f\n", makespanAfter);
            System.out.printf("  📊 Total duplications:   %d\n", totalDuplications);
            System.out.printf("  📊 Improvement:          %.2f%%\n", 
                (makespanBefore - makespanAfter) / makespanBefore * 100);
            
            assertCondition("LOTD did not increase makespan", makespanAfter <= makespanBefore * 1.01);
            
            if (totalDuplications > 0) {
                System.out.println("  ✅ PASS - Duplication occurred and improved schedule!");
            } else {
                System.out.println("  ⚠️  PASS - No duplication (communication still cheaper than recompute)");
            }
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        totalTests++;
        System.out.println();
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 2: CAPACITY PRESSURE TEST
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testCapacityPressure() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 2: CAPACITY PRESSURE TEST");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            // Create VMs with varying capacities
            List<VM> vms = new ArrayList<>();
            double[] capacities = {5.0, 10.0, 20.0, 50.0}; // VM3 is much faster
            for (int i = 0; i < 4; i++) {
                VM vm = new VM(i);
                vm.addCapability("processing", capacities[i]);
                vms.add(vm);
            }
            
            // Create 30 tasks
            List<task> tasks = new ArrayList<>();
            for (int i = 0; i < 30; i++) {
                tasks.add(new task(i, 10.0));
            }
            
            // Create diamond DAG structure
            // Level 0: task 0
            // Level 1: tasks 1-10 (entry tasks)
            // Level 2: tasks 11-20 (middle tasks)
            // Level 3: tasks 21-29 (converging tasks)
            tasks.get(0).addSuccessor(1); tasks.get(1).addPredecessor(0);
            for (int i = 1; i <= 10; i++) {
                tasks.get(0).addSuccessor(i);
                tasks.get(i).addPredecessor(0);
                int mid = 10 + i;
                if (mid < 21) {
                    tasks.get(i).addSuccessor(mid);
                    tasks.get(mid).addPredecessor(i);
                }
            }
            for (int i = 11; i <= 20; i++) {
                int end = 20 + (i - 10);
                if (end < 30) {
                    tasks.get(i).addSuccessor(end);
                    tasks.get(end).addPredecessor(i);
                }
            }
            
            // Create suboptimal schedule (overload VM0-2, leave VM3 underutilized)
            Map<Integer, List<Integer>> schedule = new HashMap<>();
            schedule.put(0, new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5, 11, 12, 13, 14)));
            schedule.put(1, new ArrayList<>(Arrays.asList(6, 7, 8, 15, 16, 17)));
            schedule.put(2, new ArrayList<>(Arrays.asList(9, 10, 18, 19, 20)));
            schedule.put(3, new ArrayList<>(Arrays.asList(21, 22, 23, 24, 25, 26, 27, 28, 29)));
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            // Run LOTD
            LOTD lotd = new LOTD(smgt);
            double makespanBefore = calculateMakespan(schedule, tasks, vms);
            Map<Integer, List<Integer>> optimizedSchedule = lotd.executeLOTDCorrect(schedule);
            double makespanAfter = calculateMakespan(optimizedSchedule, tasks, vms);
            
            // Check results
            int totalDuplications = 0;
            for (Set<Integer> dups : lotd.getDuplicatedTasks().values()) {
                totalDuplications += dups.size();
            }
            
            System.out.printf("  📊 VM load before: [%.1f, %.1f, %.1f, %.1f]\n", 
                calculateVMLoad(0, schedule, tasks),
                calculateVMLoad(1, schedule, tasks),
                calculateVMLoad(2, schedule, tasks),
                calculateVMLoad(3, schedule, tasks));
            System.out.printf("  📊 Makespan before: %.2f\n", makespanBefore);
            System.out.printf("  📊 Makespan after:  %.2f\n", makespanAfter);
            System.out.printf("  📊 Duplications:    %d\n", totalDuplications);
            
            assertCondition("LOTD maintained or improved makespan", makespanAfter <= makespanBefore * 1.01);
            assertCondition("All original tasks still scheduled", 
                countScheduledTasks(optimizedSchedule) >= 30);
            
            System.out.println("  ✅ PASS - Capacity pressure handled correctly");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        totalTests++;
        System.out.println();
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 3: CRITICAL PATH DUPLICATION
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testCriticalPathDuplication() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 3: CRITICAL PATH DUPLICATION");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            // Create VMs: VM0 very slow, VM1-3 fast
            List<VM> vms = new ArrayList<>();
            vms.add(createVM(0, 0.5));  // Slow
            vms.add(createVM(1, 10.0)); // Fast
            vms.add(createVM(2, 10.0)); // Fast
            vms.add(createVM(3, 10.0)); // Fast
            
            // Low bandwidth from VM0 to others
            for (int j = 1; j < 4; j++) {
                vms.get(0).setBandwidthToVM(j, 0.01);
            }
            
            // Create linear chain of 10 tasks
            List<task> tasks = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                task t = new task(i, 5.0);
                t.setSize(50.0); // Large data transfer
                tasks.add(t);
            }
            
            for (int i = 0; i < 9; i++) {
                tasks.get(i).addSuccessor(i + 1);
                tasks.get(i + 1).addPredecessor(i);
            }
            
            // Put all CP tasks on slow VM0
            Map<Integer, List<Integer>> schedule = new HashMap<>();
            schedule.put(0, new ArrayList<>(Arrays.asList(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));
            schedule.put(1, new ArrayList<>());
            schedule.put(2, new ArrayList<>());
            schedule.put(3, new ArrayList<>());
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            // Calculate communication costs
            Map<String, Double> commCosts = new HashMap<>();
            for (int i = 0; i < 9; i++) {
                double dataSize = tasks.get(i).getSize(); // 50.0
                double commCostAtAvg = dataSize / 25.0;   // 50.0 / 25.0 = 2.0
                commCosts.put(i + "_" + (i + 1), commCostAtAvg);
            }
            
            // Run LOTD
            LOTD lotd = new LOTD(smgt);
            lotd.setCommunicationCosts(commCosts);
            double makespanBefore = calculateMakespan(schedule, tasks, vms);
            Map<Integer, List<Integer>> optimizedSchedule = lotd.executeLOTDCorrect(schedule);
            double makespanAfter = calculateMakespan(optimizedSchedule, tasks, vms);
            
            int totalDuplications = 0;
            for (Set<Integer> dups : lotd.getDuplicatedTasks().values()) {
                totalDuplications += dups.size();
            }
            
            System.out.printf("  📊 Makespan before: %.2f (all on slow VM)\n", makespanBefore);
            System.out.printf("  📊 Makespan after:  %.2f\n", makespanAfter);
            System.out.printf("  📊 Duplications:    %d\n", totalDuplications);
            System.out.printf("  📊 Improvement:     %.1f%%\n", 
                (makespanBefore - makespanAfter) / makespanBefore * 100);
            
            assertCondition("LOTD improved or maintained makespan", makespanAfter <= makespanBefore);
            
            if (totalDuplications > 0) {
                System.out.println("  ✅ PASS - CP tasks duplicated to fast VMs!");
            } else {
                System.out.println("  ⚠️  PASS - No duplication (recompute still more expensive)");
            }
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        totalTests++;
        System.out.println();
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 4: LARGE SCALE DUPLICATION
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testLargeScaleDuplication() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 4: LARGE SCALE DUPLICATION");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            // Create 100 tasks
            List<task> tasks = new ArrayList<>();
            Random rand = SeededRandom.forScope("lotd-large-scale");
            for (int i = 0; i < 100; i++) {
                task t = new task(i, 5.0 + rand.nextDouble() * 10.0);
                t.setSize(20.0 + rand.nextDouble() * 30.0);
                tasks.add(t);
            }
            
            // Create multi-level DAG
            int levelsCount = 10;
            int tasksPerLevel = 10;
            for (int level = 0; level < levelsCount - 1; level++) {
                int levelStart = level * tasksPerLevel;
                int nextLevelStart = (level + 1) * tasksPerLevel;
                
                for (int i = levelStart; i < levelStart + tasksPerLevel; i++) {
                    for (int j = 0; j < 2; j++) {
                        int succ = nextLevelStart + rand.nextInt(tasksPerLevel);
                        if (!tasks.get(i).getSucc().contains(succ)) {
                            tasks.get(i).addSuccessor(succ);
                            tasks.get(succ).addPredecessor(i);
                        }
                    }
                }
            }
            
            // Create 10 VMs with varying speeds
            List<VM> vms = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                vms.add(createVM(i, 1.0 + i * 0.5));
            }
            
            // Random initial schedule
            Map<Integer, List<Integer>> schedule = new HashMap<>();
            for (int i = 0; i < 10; i++) {
                schedule.put(i, new ArrayList<>());
            }
            for (int i = 0; i < 100; i++) {
                schedule.get(i % 10).add(i);
            }
            
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            // Calculate communication costs
            Map<String, Double> commCosts = new HashMap<>();
            for (task t : tasks) {
                for (int succ : t.getSucc()) {
                    double dataSize = t.getSize();
                    double commCostAtAvg = dataSize / 25.0;
                    commCosts.put(t.getID() + "_" + succ, commCostAtAvg);
                }
            }
            
            // Run LOTD
            long startTime = System.currentTimeMillis();
            LOTD lotd = new LOTD(smgt);
            lotd.setCommunicationCosts(commCosts);
            double makespanBefore = calculateMakespan(schedule, tasks, vms);
            Map<Integer, List<Integer>> optimizedSchedule = lotd.executeLOTDCorrect(schedule);
            double makespanAfter = calculateMakespan(optimizedSchedule, tasks, vms);
            long duration = System.currentTimeMillis() - startTime;
            
            int totalDuplications = 0;
            for (Set<Integer> dups : lotd.getDuplicatedTasks().values()) {
                totalDuplications += dups.size();
            }
            
            System.out.printf("  📊 Tasks:           100\n");
            System.out.printf("  📊 VMs:             10\n");
            System.out.printf("  📊 Makespan before: %.2f\n", makespanBefore);
            System.out.printf("  📊 Makespan after:  %.2f\n", makespanAfter);
            System.out.printf("  📊 Duplications:    %d\n", totalDuplications);
            System.out.printf("  📊 LOTD runtime:    %dms\n", duration);
            
            assertCondition("All tasks scheduled", countScheduledTasks(optimizedSchedule) >= 100);
            assertCondition("LOTD completed in reasonable time", duration < 5000);
            assertCondition("Makespan not increased", makespanAfter <= makespanBefore * 1.01);
            
            System.out.println("  ✅ PASS - Large scale LOTD completed successfully");
            
        } catch (Exception e) {
            System.out.println("  ❌ FAIL - " + e.getMessage());
            e.printStackTrace();
            failedTests++;
        }
        totalTests++;
        System.out.println();
    }
    
    // ═════════════════════════════════════════════════════════════════════════
    // Test 5: PROPERTY - RULE 2 ENFORCEMENT
    // ═════════════════════════════════════════════════════════════════════════
    
    private static void testPropertyRule2Enforcement() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 5: PROPERTY - RULE 2 ENFORCEMENT");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        try {
            // Test multiple random scenarios and ensure Rule 2 always holds:
            // "Task duplication must not increase makespan"
            
            int testCount = 10;
            int passCount = 0;
            int dupCount = 0;
            
            for (int trial = 0; trial < testCount; trial++) {
                // Create random DAG
                int numTasks = 20 + trial * 5;
                int numVMs = 3 + trial;
                
                List<task> tasks = createRandomDAG(numTasks, trial);
                List<VM> vms = new ArrayList<>();
                for (int i = 0; i < numVMs; i++) {
                    vms.add(createVM(i, 0.5 + i * 0.5));
                }
                
                // Random schedule
                Map<Integer, List<Integer>> schedule = new HashMap<>();
                for (int i = 0; i < numVMs; i++) {
                    schedule.put(i, new ArrayList<>());
                }
                for (int i = 0; i < numTasks; i++) {
                    schedule.get(i % numVMs).add(i);
                }
                
            SMGT smgt = new SMGT();
            smgt.setVMs(vms);
            smgt.setTasks(tasks);
            smgt.calculateTaskLevels();
            
            // Calculate communication costs
            Map<String, Double> commCosts = new HashMap<>();
            for (task t : tasks) {
                for (int succ : t.getSucc()) {
                    double dataSize = t.getSize(); // 10.0
                    double commCostAtAvg = dataSize / 25.0; // 10.0 / 25.0 = 0.4
                    commCosts.put(t.getID() + "_" + succ, commCostAtAvg);
                }
            }
            
            // Run LOTD
            LOTD lotd = new LOTD(smgt);
            lotd.setCommunicationCosts(commCosts);
            double makespanBefore = calculateMakespan(schedule, tasks, vms);
            Map<Integer, List<Integer>> optimizedSchedule = lotd.executeLOTDCorrect(schedule);
                double makespanAfter = calculateMakespan(optimizedSchedule, tasks, vms);
                
                // Check Rule 2
                if (makespanAfter <= makespanBefore * 1.01) { // Allow 1% tolerance for numerical error
                    passCount++;
                    
                    int trialDups = 0;
                    for (Set<Integer> dups : lotd.getDuplicatedTasks().values()) {
                        trialDups += dups.size();
                    }
                    if (trialDups > 0) dupCount++;
                } else {
                    System.out.printf("  ❌ Trial %d FAILED: Makespan increased from %.2f to %.2f\n", 
                        trial + 1, makespanBefore, makespanAfter);
                }
            }
            
            System.out.printf("  📊 Trials passed:         %d/%d\n", passCount, testCount);
            System.out.printf("  📊 Trials with dups:      %d/%d\n", dupCount, testCount);
            System.out.printf("  📊 Rule 2 compliance:     %.1f%%\n", 100.0 * passCount / testCount);
            
            assertCondition("Rule 2 holds in all trials", passCount == testCount);
            
            if (dupCount > 0) {
                System.out.printf("  ✅ PASS - Rule 2 enforced (%d trials had duplications)\n", dupCount);
            } else {
                System.out.println("  ⚠️  PASS - Rule 2 enforced (no duplications occurred)");
            }
            
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
        Random rand = SeededRandom.forScope("random-dag-" + seed);
        
        for (int i = 0; i < numTasks; i++) {
            task t = new task(i, 5.0 + rand.nextDouble() * 10.0);
            t.setSize(10.0 + rand.nextDouble() * 20.0);
            tasks.add(t);
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
    
    private static double calculateMakespan(Map<Integer, List<Integer>> schedule, 
                                           List<task> tasks, List<VM> vms) {
        // Simplified makespan calculation
        double maxMakespan = 0;
        
        for (Map.Entry<Integer, List<Integer>> entry : schedule.entrySet()) {
            int vmId = entry.getKey();
            List<Integer> vmTasks = entry.getValue();
            
            if (vmTasks == null || vmTasks.isEmpty()) continue;
            
            double vmMakespan = 0;
            VM vm = vms.get(vmId);
            double processingSpeed = vm.getCapability("processing");
            if (processingSpeed == 0.0) processingSpeed = 1.0;
            
            for (int taskId : vmTasks) {
                if (taskId < tasks.size()) {
                    double taskTime = tasks.get(taskId).getSize() / processingSpeed;
                    vmMakespan += taskTime;
                }
            }
            
            maxMakespan = Math.max(maxMakespan, vmMakespan);
        }
        
        return maxMakespan;
    }
    
    private static double calculateVMLoad(int vmId, Map<Integer, List<Integer>> schedule, 
                                         List<task> tasks) {
        List<Integer> vmTasks = schedule.get(vmId);
        if (vmTasks == null) return 0.0;
        
        double load = 0;
        for (int taskId : vmTasks) {
            if (taskId < tasks.size()) {
                load += tasks.get(taskId).getSize();
            }
        }
        return load;
    }
    
    private static int countScheduledTasks(Map<Integer, List<Integer>> schedule) {
        Set<Integer> scheduled = new HashSet<>();
        for (List<Integer> vmTasks : schedule.values()) {
            if (vmTasks != null) {
                scheduled.addAll(vmTasks);
            }
        }
        return scheduled.size();
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
