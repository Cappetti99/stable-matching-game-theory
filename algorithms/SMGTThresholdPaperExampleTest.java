import java.util.*;

/**
 * Minimal deterministic check for the SMGT threshold formula as used in the paper.
 *
 * NOTE: This repo doesn't use JUnit; tests are runnable mains.
 */
public class SMGTThresholdPaperExampleTest {

    private static void assertEquals(int expected, int actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }

    private static void assertEquals(int expected, int actual) {
        assertEquals(expected, actual, "assertEquals failed");
    }

    public static void main(String[] args) {
        // Build a tiny DAG with:
        // - Level 0: 2 entry tasks
        // - Level 1: 13 tasks depending on level-0 tasks
        SMGT smgt = new SMGT();

        List<VM> vms = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            VM vm = new VM(i);
            // Use deterministic capacities to make thresholds deterministic.
            // Sum capacities = 2.0, so with sumTasks(level0)=2 we get threshold=1 on VM0/VM1.
            double cap = (i == 0 || i == 1) ? 1.0 : 0.0;
            vm.addCapability("processingCapacity", cap);
            vms.add(vm);
        }

        List<task> tasks = new ArrayList<>();
        for (int id = 0; id <= 14; id++) {
            task t = new task(id);
            t.setSize(100.0);
            tasks.add(t);
        }

        // Level 0 tasks: 0 and 1
        task t0 = tasks.get(0);
        task t1 = tasks.get(1);

        // Level 1 tasks: 2..14 depend on 0 and 1
        for (int id = 2; id <= 14; id++) {
            task t = tasks.get(id);
            t.addPredecessor(0);
            t.addPredecessor(1);
            t0.addSuccessor(id);
            t1.addSuccessor(id);
        }

        smgt.setVMs(vms);
        smgt.setTasks(tasks);
        smgt.calculateTaskLevels();

        int n0 = smgt.getLevelTasks().getOrDefault(0, List.of()).size();
        int n1 = smgt.getLevelTasks().getOrDefault(1, List.of()).size();
        assertEquals(2, n0, "Expected 2 tasks at level 0");
        assertEquals(13, n1, "Expected 13 tasks at level 1");

        // Paper-level l=1 => sum_{v=0}^{0} n_v = n0 = 2
        // threshold(VM_k, 1) = floor((2 / sumCap) * p_k)
        assertEquals(1, smgt.calculateInitialThreshold(0, 1));
        assertEquals(1, smgt.calculateInitialThreshold(1, 1));

        System.out.println("OK: threshold matches expected paper-level example.");
    }
}
