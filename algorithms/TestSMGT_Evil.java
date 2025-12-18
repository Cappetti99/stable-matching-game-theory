import java.util.*;

public class TestSMGT_Evil {

    public static void main(String[] args) {

        System.out.println("\n🔥🔥🔥 EVIL SMGT TEST START 🔥🔥🔥");

        // ===============================
        // 1️⃣ VM SETUP (molto eterogenee)
        // ===============================
        VM vm0 = new VM(0); vm0.addProcessingCapability("cpu", 1.0);
        VM vm1 = new VM(1); vm1.addProcessingCapability("cpu", 3.0);
        VM vm2 = new VM(2); vm2.addProcessingCapability("cpu", 8.0);
        VM vm3 = new VM(3); vm3.addProcessingCapability("cpu", 20.0); // FASTEST

        List<VM> vms = Arrays.asList(vm0, vm1, vm2, vm3);

        // ===============================
        // 2️⃣ TASK CREATION
        // ===============================
        Map<Integer, task> T = new HashMap<>();
        for (int i = 1; i <= 20; i++) {
            // task grandi sui livelli profondi
            int size = (i < 10) ? 20 : (i < 15 ? 40 : 80);
            T.put(i, new task(i, size));
        }

        // ===============================
        // 3️⃣ DAG DEPENDENCIES
        // ===============================
        connect(T,1,5); connect(T,2,6); connect(T,3,7); connect(T,4,8);
        connect(T,5,9); connect(T,6,9); connect(T,7,10); connect(T,8,10);
        connect(T,9,11); connect(T,10,12);
        connect(T,11,13); connect(T,12,13);
        connect(T,13,14);
        connect(T,14,15); connect(T,14,16); connect(T,14,17);
        connect(T,15,18); connect(T,16,18); connect(T,17,18);
        connect(T,18,19);
        connect(T,19,20);

        List<task> tasks = new ArrayList<>(T.values());

        // ===============================
        // 4️⃣ SMGT INIT
        // ===============================
        SMGT smgt = new SMGT();
        smgt.setVMs(vms);
        smgt.setTasks(tasks);
        smgt.calculateTaskLevels();

        // ===============================
        // 5️⃣ STAMPA LIVELLI
        // ===============================
        System.out.println("\n--- DAG LEVELS ---");
        smgt.getTaskLevels().forEach((t,l) ->
                System.out.println("t" + t + " -> level " + l));

        // ===============================
        // 6️⃣ CRITICAL PATH (molto pesante)
        // ===============================
        Set<Integer> CP = Set.of(
                3,7,10,12,13,14,16,18,19,20
        );

        System.out.println("\nCRITICAL PATH = " + CP);

        // ===============================
        // 7️⃣ RUN SMGT
        // ===============================
        Map<Integer, List<Integer>> schedule = smgt.runSMGT(CP);

        // ===============================
        // 8️⃣ RISULTATI FINALI
        // ===============================
        System.out.println("\n=== FINAL SCHEDULE ===");
        for (int vm = 0; vm < vms.size(); vm++) {
            System.out.println("VM" + vm + " -> " + schedule.get(vm));
        }

        // ===============================
        // 9️⃣ CHECK DURISSIMI
        // ===============================
        System.out.println("\n--- EVIL CHECKS ---");

        Set<Integer> assigned = new HashSet<>();
        for (List<Integer> list : schedule.values()) {
            for (int t : list) {
                if (!assigned.add(t)) {
                    System.err.println("❌ DUPLICATE TASK t" + t);
                }
            }
        }

        if (assigned.size() != tasks.size()) {
            System.err.println("❌ MISSING TASKS: expected "
                    + tasks.size() + ", found " + assigned.size());
        } else {
            System.out.println("✔ All tasks assigned exactly once");
        }

        // CP check
        int fastestVM = 3;
        for (int t : CP) {
            if (!schedule.get(fastestVM).contains(t)) {
                System.err.println("❌ CP task t" + t +
                        " NOT on fastest VM!");
            }
        }

        System.out.println("\n🔥🔥🔥 EVIL SMGT TEST END 🔥🔥🔥");
    }

    // Utility per collegare DAG
    private static void connect(Map<Integer, task> T, int from, int to) {
        T.get(from).addSucc(to);
        T.get(to).addPre(from);
    }
}
