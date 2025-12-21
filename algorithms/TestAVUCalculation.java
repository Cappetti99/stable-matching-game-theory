import java.util.*;

/**
 * Test per verificare il calcolo di AVU
 * 
 * Crea uno scenario semplice e calcola AVU manualmente per verificare la correttezza
 */
public class TestAVUCalculation {
    
    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║     🔍 AVU CALCULATION VERIFICATION TEST                    ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");
        
        testScenario1_AllTasksOnOneVM();
        testScenario2_TasksDistributedEvenly();
        testScenario3_WithCommunication();
    }
    
    /**
     * SCENARIO 1: Tutti i task su 1 VM
     * 
     * Setup:
     * - 5 task, ciascuno size=100
     * - 3 VMs, tutte con capacity=10
     * - Tutti i task assegnati a VM0
     * - Nessuna comunicazione (stesso VM)
     * 
     * Expected:
     * - ET per task = 100/10 = 10
     * - Makespan = 5 * 10 = 50
     * - VU(VM0) = (5*10) / 50 = 1.0 (100%)
     * - VU(VM1) = 0 / 50 = 0.0
     * - VU(VM2) = 0 / 50 = 0.0
     * - AVU = (1.0 + 0.0 + 0.0) / 3 = 0.333 (33.3%)
     */
    private static void testScenario1_AllTasksOnOneVM() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("SCENARIO 1: All tasks on one VM");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        // Create VMs
        VM vm0 = new VM(0);
        vm0.addCapability("processingCapacity", 10.0);
        
        VM vm1 = new VM(1);
        vm1.addCapability("processingCapacity", 10.0);
        
        VM vm2 = new VM(2);
        vm2.addCapability("processingCapacity", 10.0);
        
        Map<Integer, VM> vms = new HashMap<>();
        vms.put(0, vm0);
        vms.put(1, vm1);
        vms.put(2, vm2);
        
        // Create tasks (all size = 100)
        List<task> allTasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            task t = new task(i, 100.0);
            allTasks.add(t);
        }
        
        // Assign all tasks to VM0
        Map<Integer, List<task>> assignments = new HashMap<>();
        assignments.put(0, new ArrayList<>(allTasks));
        assignments.put(1, new ArrayList<>());
        assignments.put(2, new ArrayList<>());
        
        // Makespan = 5 tasks * 10 ET each = 50
        double makespan = 50.0;
        
        // Calculate AVU
        double avu = Metrics.AVU(vms, assignments, makespan, "processingCapacity");
        
        // Manual calculation
        double vm0_et_sum = 5 * (100.0 / 10.0); // 5 tasks * ET(task, vm0)
        double vm0_vu = vm0_et_sum / makespan;  // Should be 50/50 = 1.0
        double expected_avu = (vm0_vu + 0.0 + 0.0) / 3; // Average across 3 VMs
        
        System.out.println("  📊 Setup:");
        System.out.println("     Tasks: 5 (each size=100)");
        System.out.println("     VMs: 3 (each capacity=10)");
        System.out.println("     Assignment: All tasks on VM0");
        System.out.println("     Makespan: " + makespan);
        System.out.println();
        System.out.println("  📈 Manual Calculation:");
        System.out.println("     VM0 ET sum: " + vm0_et_sum);
        System.out.println("     VM0 VU: " + vm0_vu + " (should be 1.0)");
        System.out.println("     VM1 VU: 0.0");
        System.out.println("     VM2 VU: 0.0");
        System.out.println("     Expected AVU: " + expected_avu);
        System.out.println();
        System.out.println("  🔍 Calculated AVU: " + avu);
        System.out.println();
        
        if (Math.abs(avu - expected_avu) < 0.001) {
            System.out.println("  ✅ PASS - AVU calculation correct");
        } else {
            System.out.println("  ❌ FAIL - AVU calculation incorrect");
            System.out.println("     Expected: " + expected_avu);
            System.out.println("     Got: " + avu);
        }
        System.out.println();
    }
    
    /**
     * SCENARIO 2: Task distribuiti uniformemente
     * 
     * Setup:
     * - 6 task, ciascuno size=100
     * - 3 VMs, tutte con capacity=10
     * - 2 task per VM
     * - Makespan = 20 (ciascuna VM esegue 2 task in sequenza)
     * 
     * Expected:
     * - ET per task = 100/10 = 10
     * - VU per VM = (2*10) / 20 = 1.0 (100%)
     * - AVU = (1.0 + 1.0 + 1.0) / 3 = 1.0 (100%)
     */
    private static void testScenario2_TasksDistributedEvenly() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("SCENARIO 2: Tasks distributed evenly");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        // Create VMs
        Map<Integer, VM> vms = new HashMap<>();
        for (int i = 0; i < 3; i++) {
            VM vm = new VM(i);
            vm.addCapability("processingCapacity", 10.0);
            vms.put(i, vm);
        }
        
        // Create tasks and distribute evenly
        Map<Integer, List<task>> assignments = new HashMap<>();
        for (int vmId = 0; vmId < 3; vmId++) {
            List<task> vmTasks = new ArrayList<>();
            for (int j = 0; j < 2; j++) {
                task t = new task(vmId * 2 + j, 100.0);
                vmTasks.add(t);
            }
            assignments.put(vmId, vmTasks);
        }
        
        // Makespan = 2 tasks * 10 ET = 20 (all VMs finish at same time)
        double makespan = 20.0;
        
        // Calculate AVU
        double avu = Metrics.AVU(vms, assignments, makespan, "processingCapacity");
        
        // Expected: all VMs fully utilized
        double expected_avu = 1.0;
        
        System.out.println("  📊 Setup:");
        System.out.println("     Tasks: 6 (each size=100)");
        System.out.println("     VMs: 3 (each capacity=10)");
        System.out.println("     Assignment: 2 tasks per VM");
        System.out.println("     Makespan: " + makespan);
        System.out.println();
        System.out.println("  📈 Expected:");
        System.out.println("     Each VM VU: 1.0 (fully utilized)");
        System.out.println("     Expected AVU: " + expected_avu);
        System.out.println();
        System.out.println("  🔍 Calculated AVU: " + avu);
        System.out.println();
        
        if (Math.abs(avu - expected_avu) < 0.001) {
            System.out.println("  ✅ PASS - AVU calculation correct");
        } else {
            System.out.println("  ❌ FAIL - AVU calculation incorrect");
            System.out.println("     Expected: " + expected_avu);
            System.out.println("     Got: " + avu);
        }
        System.out.println();
    }
    
    /**
     * SCENARIO 3: Con tempo di comunicazione
     * 
     * Setup:
     * - 2 task, ciascuno size=100
     * - 2 VMs con capacity=10
     * - Task 0 su VM0, Task 1 su VM1
     * - Tempo comunicazione = 50 (CCR=0.5)
     * - Makespan reale = 10 (task0) + 50 (comm) + 10 (task1) = 70
     * 
     * Expected:
     * - VU(VM0) = 10 / 70 = 0.143 (14.3%)
     * - VU(VM1) = 10 / 70 = 0.143 (14.3%)
     * - AVU = 0.143 (14.3%)
     * 
     * Note: AVU is low because communication time is NOT counted as "utilization"
     */
    private static void testScenario3_WithCommunication() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("SCENARIO 3: With communication overhead");
        System.out.println("═══════════════════════════════════════════════════════════════\n");
        
        // Create VMs
        VM vm0 = new VM(0);
        vm0.addCapability("processingCapacity", 10.0);
        
        VM vm1 = new VM(1);
        vm1.addCapability("processingCapacity", 10.0);
        
        Map<Integer, VM> vms = new HashMap<>();
        vms.put(0, vm0);
        vms.put(1, vm1);
        
        // Create tasks
        task t0 = new task(0, 100.0);
        task t1 = new task(1, 100.0);
        
        // Assignments
        Map<Integer, List<task>> assignments = new HashMap<>();
        assignments.put(0, Arrays.asList(t0));
        assignments.put(1, Arrays.asList(t1));
        
        // Makespan includes communication
        // t0: 0-10, comm: 10-60, t1: 60-70
        double makespan = 70.0;
        
        // Calculate AVU
        double avu = Metrics.AVU(vms, assignments, makespan, "processingCapacity");
        
        // Expected calculation
        double vm0_et = 100.0 / 10.0; // 10
        double vm1_et = 100.0 / 10.0; // 10
        double vm0_vu = vm0_et / makespan; // 10/70 = 0.143
        double vm1_vu = vm1_et / makespan; // 10/70 = 0.143
        double expected_avu = (vm0_vu + vm1_vu) / 2; // 0.143
        
        System.out.println("  📊 Setup:");
        System.out.println("     Tasks: 2 (each size=100)");
        System.out.println("     VMs: 2 (each capacity=10)");
        System.out.println("     Assignment: 1 task per VM");
        System.out.println("     Computation time per task: 10");
        System.out.println("     Communication time: 50");
        System.out.println("     Makespan: " + makespan + " (includes comm overhead)");
        System.out.println();
        System.out.println("  📈 Expected:");
        System.out.println("     VM0 VU: " + String.format("%.3f", vm0_vu) + " (10/70)");
        System.out.println("     VM1 VU: " + String.format("%.3f", vm1_vu) + " (10/70)");
        System.out.println("     Expected AVU: " + String.format("%.3f", expected_avu));
        System.out.println();
        System.out.println("  🔍 Calculated AVU: " + String.format("%.3f", avu));
        System.out.println();
        System.out.println("  💡 Note: AVU is low because communication time");
        System.out.println("     is NOT counted as VM \"utilization\" in this metric.");
        System.out.println("     This is by design - AVU measures only computation time.");
        System.out.println();
        
        if (Math.abs(avu - expected_avu) < 0.001) {
            System.out.println("  ✅ PASS - AVU calculation correct");
        } else {
            System.out.println("  ❌ FAIL - AVU calculation incorrect");
            System.out.println("     Expected: " + expected_avu);
            System.out.println("     Got: " + avu);
        }
        System.out.println();
    }
}
