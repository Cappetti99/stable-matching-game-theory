import java.util.*;

public class TestDCP_Evil {

    public static void main(String[] args) {
        System.out.println("==========================================");
        System.out.println("        SUPER EVIL TEST SUITE: DCP        ");
        System.out.println("==========================================\n");

        testScenarioSuperEvil();

        System.out.println("\nAll evil DCP tests completed.");
    }

    private static void printResult(String scenario, boolean condition,
                                    String successMsg, String failMsg) {
        System.out.println("Test " + scenario + ":");
        if (condition) {
            System.out.println("  [PASS] " + successMsg);
        } else {
            System.out.println("  [FAIL] " + failMsg);
        }
        System.out.println("------------------------------------------");
    }

    private static Map<Integer, VM> createVMs(int n) {
        Map<Integer, VM> vms = new HashMap<>();
        for (int i = 0; i < n; i++) {
            VM vm = new VM(i);
            vm.addCapability("processingCapacity", 1.0 + i * 0.5); // diverse capacità
            vms.put(i, vm);
        }
        return vms;
    }

    private static void testScenarioSuperEvil() {
        Map<Integer, VM> vms = createVMs(5); // 5 VM diverse

        // DAG super complesso con 15 task, dipendenze multiple, convergenze e divergenze
        task[] tasks = new task[15];
        for (int i = 0; i < 15; i++) {
            tasks[i] = new task(i);
            tasks[i].setSize(10 + i * 5); // dimensioni diverse
        }

        // Creiamo un DAG “tortuoso”
        tasks[0].addSuccessor(1); tasks[0].addSuccessor(2); tasks[0].addSuccessor(3);
        tasks[1].addPredecessor(0); tasks[1].addSuccessor(4);
        tasks[2].addPredecessor(0); tasks[2].addSuccessor(4); tasks[2].addSuccessor(5);
        tasks[3].addPredecessor(0); tasks[3].addSuccessor(5);
        tasks[4].addPredecessor(1); tasks[4].addPredecessor(2); tasks[4].addSuccessor(6);
        tasks[5].addPredecessor(2); tasks[5].addPredecessor(3); tasks[5].addSuccessor(6); tasks[5].addSuccessor(7);
        tasks[6].addPredecessor(4); tasks[6].addPredecessor(5); tasks[6].addSuccessor(8);
        tasks[7].addPredecessor(5); tasks[7].addSuccessor(8); tasks[7].addSuccessor(9);
        tasks[8].addPredecessor(6); tasks[8].addPredecessor(7); tasks[8].addSuccessor(10);
        tasks[9].addPredecessor(7); tasks[9].addSuccessor(10); tasks[9].addSuccessor(11);
        tasks[10].addPredecessor(8); tasks[10].addPredecessor(9); tasks[10].addSuccessor(12);
        tasks[11].addPredecessor(9); tasks[11].addSuccessor(12); tasks[11].addSuccessor(13);
        tasks[12].addPredecessor(10); tasks[12].addPredecessor(11); tasks[12].addSuccessor(14);
        tasks[13].addPredecessor(11); tasks[13].addSuccessor(14);
        tasks[14].addPredecessor(12); tasks[14].addPredecessor(13); // exit

        List<task> taskList = Arrays.asList(tasks);

        // Organizza livelli (manualmente, super evil)
        Map<Integer, List<Integer>> levels = new HashMap<>();
        levels.put(0, Arrays.asList(0));
        levels.put(1, Arrays.asList(1,2,3));
        levels.put(2, Arrays.asList(4,5));
        levels.put(3, Arrays.asList(6,7));
        levels.put(4, Arrays.asList(8,9));
        levels.put(5, Arrays.asList(10,11));
        levels.put(6, Arrays.asList(12,13));
        levels.put(7, Arrays.asList(14));

        // Costi di comunicazione “estremi” per creare conflitti
        Map<String, Double> comm = new HashMap<>();
        for (task t : tasks) {
            for (int succ : t.getSucc()) {
                double val = 1.0 + Math.random() * 50; // costi random alti
                comm.put(t.getID() + "_" + succ, val);
            }
        }

        // Exit task
        task exitTask = tasks[14];

        Set<Integer> cp = DCP.executeDCP(taskList, levels, exitTask, comm, vms);

        // Check: exit task deve essere nel CP, almeno una radice iniziale, e numero corretto di task
        boolean ok = cp.contains(exitTask.getID()) && cp.contains(0) && cp.size() >= 7;
        printResult("Super Evil DAG", ok,
                "Critical path includes exit and root tasks, CP size >= 7",
                "Critical path incorrect or incomplete.");
    }
}
