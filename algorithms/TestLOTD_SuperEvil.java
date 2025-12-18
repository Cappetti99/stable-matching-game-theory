import java.util.*;

public class TestLOTD_SuperEvil {

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("      SUPER EVIL TEST SUITE: LOTD         ");
        System.out.println("==========================================\n");

        runSuperEvilScenario();

        System.out.println("\nAll super evil LOTD tests completed.");
    }

    // ------------------------------------------
    // Helper methods
    // ------------------------------------------
    private static SMGT setupBaseline() {
        return new SMGT();
    }

    private static void printResult(String scenario, boolean condition, String successMsg, String failMsg) {
        System.out.println("Test " + scenario + ":");
        if (condition) {
            System.out.println("  [PASS] " + successMsg);
        } else {
            System.out.println("  [FAIL] " + failMsg);
        }
        System.out.println("------------------------------------------");
    }

    // ------------------------------------------
    // Super Evil Scenario
    // ------------------------------------------
    private static void runSuperEvilScenario() {
        SMGT smgt = setupBaseline();

        // --- VM setup ---
        VM vm0 = new VM(0); vm0.addCapability("processing", 1.0);
        VM vm1 = new VM(1); vm1.addCapability("processing", 2.0);
        VM vm2 = new VM(2); vm2.addCapability("processing", 5.0);
        VM vm3 = new VM(3); vm3.addCapability("processing", 3.0);
        VM vm4 = new VM(4); vm4.addCapability("processing", 1.5);

        // Bandwidth setup (simulate slow and fast links)
        vm0.setBandwidthToVM(1, 0.5); vm0.setBandwidthToVM(2, 1.0); vm0.setBandwidthToVM(3, 2.0); vm0.setBandwidthToVM(4, 1.5);
        vm1.setBandwidthToVM(0, 0.5); vm1.setBandwidthToVM(2, 2.5); vm1.setBandwidthToVM(3, 1.0); vm1.setBandwidthToVM(4, 0.8);
        vm2.setBandwidthToVM(0, 1.0); vm2.setBandwidthToVM(1, 2.5); vm2.setBandwidthToVM(3, 0.5); vm2.setBandwidthToVM(4, 3.0);
        vm3.setBandwidthToVM(0, 2.0); vm3.setBandwidthToVM(1, 1.0); vm3.setBandwidthToVM(2, 0.5); vm3.setBandwidthToVM(4, 1.0);
        vm4.setBandwidthToVM(0, 1.5); vm4.setBandwidthToVM(1, 0.8); vm4.setBandwidthToVM(2, 3.0); vm4.setBandwidthToVM(3, 1.0);

        smgt.setVMs(Arrays.asList(vm0, vm1, vm2, vm3, vm4));

        // --- Task DAG setup ---
        List<task> tasks = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            task t = new task(i);
            t.setSize(10 + i*5); // increasing size to force some duplication choices
            tasks.add(t);
        }

        // Define DAG edges (successors and predecessors)
        // Multiple levels, multiple successors
        tasks.get(0).addSuccessor(1); tasks.get(0).addSuccessor(2); tasks.get(0).addSuccessor(3);
        tasks.get(1).addPredecessor(0); tasks.get(1).addSuccessor(4); tasks.get(1).addSuccessor(5);
        tasks.get(2).addPredecessor(0); tasks.get(2).addSuccessor(5);
        tasks.get(3).addPredecessor(0); tasks.get(3).addSuccessor(6);
        tasks.get(4).addPredecessor(1); tasks.get(4).addSuccessor(7);
        tasks.get(5).addPredecessor(1); tasks.get(5).addPredecessor(2); tasks.get(5).addSuccessor(7); tasks.get(5).addSuccessor(8);
        tasks.get(6).addPredecessor(3); tasks.get(6).addSuccessor(9);
        tasks.get(7).addPredecessor(4); tasks.get(7).addPredecessor(5); tasks.get(7).addSuccessor(10);
        tasks.get(8).addPredecessor(5); tasks.get(8).addSuccessor(10);
        tasks.get(9).addPredecessor(6); tasks.get(9).addSuccessor(11);
        tasks.get(10).addPredecessor(7); tasks.get(10).addPredecessor(8); tasks.get(10).addSuccessor(12);
        tasks.get(11).addPredecessor(9); tasks.get(11).addSuccessor(12);
        tasks.get(12).addPredecessor(10); tasks.get(12).addPredecessor(11); tasks.get(12).addSuccessor(13);
        tasks.get(13).addPredecessor(12); tasks.get(13).addSuccessor(14);
        tasks.get(14).addPredecessor(13);

        smgt.setTasks(tasks);

        // --- Initial schedule ---
        Map<Integer, List<Integer>> schedule = new HashMap<>();
        schedule.put(0, Arrays.asList(0, 1, 4));
        schedule.put(1, Arrays.asList(2, 5));
        schedule.put(2, Arrays.asList(3, 6));
        schedule.put(3, Arrays.asList(7, 8, 9));
        schedule.put(4, Arrays.asList(10, 11, 12, 13, 14));

        // --- Execute LOTD ---
        LOTD lotd = new LOTD(smgt);
        lotd.executeLOTDCorrect(schedule);

        // --- Checks ---
        boolean allTasksAssigned = true;
        Set<Integer> allTasks = new HashSet<>();
        for (int i = 0; i < tasks.size(); i++) allTasks.add(i);

        Set<Integer> assignedTasks = new HashSet<>();
        for (List<Integer> lst : schedule.values()) assignedTasks.addAll(lst);

        allTasksAssigned = assignedTasks.containsAll(allTasks);

        boolean someDuplicated = lotd.getDuplicatedTasks().values().stream().anyMatch(s -> !s.isEmpty());

        printResult("SUPER EVIL DAG - All tasks assigned", allTasksAssigned,
                "All tasks assigned exactly once.",
                "Some tasks are missing!");

        printResult("SUPER EVIL DAG - Duplications occurred", someDuplicated,
                "At least one task was duplicated to improve schedule.",
                "No duplication occurred!");

        System.out.println("\nFinal schedule per VM:");
        for (Map.Entry<Integer,List<Integer>> e : schedule.entrySet()) {
            System.out.println("VM" + e.getKey() + " -> " + e.getValue());
        }

        System.out.println("\nDuplicated tasks per VM:");
        for (Map.Entry<Integer,Set<Integer>> e : lotd.getDuplicatedTasks().entrySet()) {
            System.out.println("VM" + e.getKey() + " duplicated -> " + e.getValue());
        }
    }
}
