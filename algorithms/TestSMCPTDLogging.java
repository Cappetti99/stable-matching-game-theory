import java.util.*;

/**
 * Test to demonstrate the new validation and logging in SMCPTD.calculateFinalMetrics
 */
public class TestSMCPTDLogging {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║     📋 SMCPTD LOGGING & VALIDATION TEST                     ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝\n");

        testNormalExecution();
        
        System.out.println("\n╔══════════════════════════════════════════════════════════════╗");
        System.out.println("║                   TEST SUMMARY                               ║");
        System.out.println("╚══════════════════════════════════════════════════════════════╝");
        System.out.println("✅ SMCPTD logging and validation test completed!");
        System.out.println("📊 Check the output above to see:");
        System.out.println("   - Makespan source logging (LOTD AFT vs fallback)");
        System.out.println("   - Validation checks (> 0 and not NaN)");
        System.out.println("   - Warning messages for fallback usage");
    }

    private static void testNormalExecution() {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("TEST 1: Normal SM-CPTD Execution with Logging");
        System.out.println("═══════════════════════════════════════════════════════════════\n");

        try {
            // Create simple test data
            List<VM> vms = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                VM vm = new VM(i);
                vm.addCapability("processingCapacity", 1.0 + i);
                vms.add(vm);
            }

            // Create simple DAG: t0 -> t1 -> t2 -> t3 -> t4
            List<task> tasks = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                task t = new task(i, 100.0);
                tasks.add(t);
            }
            
            // Add dependencies
            for (int i = 0; i < 4; i++) {
                tasks.get(i).addSuccessor(i + 1);
                tasks.get(i + 1).addPredecessor(i);
            }

            // Create communication costs
            Map<String, Double> commCosts = new HashMap<>();
            for (int i = 0; i < 4; i++) {
                commCosts.put(i + "_" + (i + 1), 10.0);
            }

            // Create VM mapping
            Map<Integer, VM> vmMapping = new HashMap<>();
            for (VM vm : vms) {
                vmMapping.put(vm.getID(), vm);
            }

            // Execute SM-CPTD
            SMCPTD smcptd = new SMCPTD();
            smcptd.setInputData(tasks, vms);
            
            System.out.println("Executing SM-CPTD...");
            Map<Integer, List<Integer>> result = smcptd.executeSMCPTD(commCosts, vmMapping);

            System.out.println("\n═══════════════════════════════════════════════════════════════");
            System.out.println("OBSERVATIONS FROM LOGGING:");
            System.out.println("═══════════════════════════════════════════════════════════════");
            System.out.println("✅ Notice the makespan calculation logging:");
            System.out.println("   - Shows whether LOTD AFT or fallback was used");
            System.out.println("   - Displays the makespan value with source information");
            System.out.println("   - Includes validation confirmation (> 0 and not NaN)");
            System.out.println("   - Warnings displayed if fallback method is used");
            System.out.println("\n✅ Final metrics:");
            System.out.printf("   - Makespan: %.3f%n", smcptd.getMakespan());
            System.out.printf("   - SLR: %.3f%n", smcptd.getSLR());
            System.out.printf("   - Tasks scheduled: %d%n", 
                result.values().stream().mapToInt(List::size).sum());

        } catch (IllegalStateException e) {
            System.err.println("❌ Validation error (expected for testing): " + e.getMessage());
        } catch (Exception e) {
            System.err.println("❌ Test failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
