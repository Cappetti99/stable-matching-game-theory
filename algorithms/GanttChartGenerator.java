import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * GanttChartGenerator - Generates Gantt chart data in JSON format
 * 
 * Creates a JSON file containing scheduling information for visualization:
 * - Task assignments to VMs
 * - Start and finish times (AST/AFT)
 * - Critical path tasks
 * - Duplicated tasks
 * - Makespan and other metrics
 * 
 * Output folder: ../results/gantt_charts/
 * 
 * @author Lorenzo Cappetti
 * @version 1.0
 */
public class GanttChartGenerator {

    private static final String OUTPUT_DIR = "../results/gantt_charts";

    /**
     * Generate Gantt chart JSON file
     * 
     * @param workflow Workflow name (e.g., "cybershake")
     * @param numTasks Number of tasks
     * @param numVMs Number of VMs
     * @param ccr CCR value used
     * @param makespan Final makespan
     * @param vmSchedule VM assignments (vmId -> list of taskIds)
     * @param taskAST Task Actual Start Times
     * @param taskAFT Task Actual Finish Times
     * @param criticalPath Set of critical path task IDs
     * @param duplicatedTasks Map of duplicated tasks per VM
     * @param tasks List of all tasks
     * @param vms List of all VMs
     */
    public static void generateGanttChart(
            String workflow,
            int numTasks,
            int numVMs,
            double ccr,
            double makespan,
            Map<Integer, List<Integer>> vmSchedule,
            Map<Integer, Double> taskAST,
            Map<Integer, Double> taskAFT,
            Set<Integer> criticalPath,
            Map<Integer, Set<Integer>> duplicatedTasks,
            List<task> tasks,
            List<VM> vms) {

        ensureOutputDir();

        // Generate filename with timestamp
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String filename = String.format("%s_%d_tasks_%d_vms_ccr%.1f_%s.json",
                workflow, numTasks, numVMs, ccr, timestamp);
        File outputFile = new File(OUTPUT_DIR, filename);

        try (PrintWriter writer = new PrintWriter(outputFile)) {
            writer.println("{");
            
            // Metadata section
            writer.println("  \"metadata\": {");
            writer.printf("    \"workflow\": \"%s\",%n", workflow);
            writer.printf("    \"numTasks\": %d,%n", numTasks);
            writer.printf("    \"numVMs\": %d,%n", numVMs);
            writer.printf(Locale.US, "    \"ccr\": %.1f,%n", ccr);
            writer.printf(Locale.US, "    \"makespan\": %.4f,%n", makespan);
            writer.printf("    \"timestamp\": \"%s\",%n", timestamp);
            writer.printf("    \"criticalPathSize\": %d%n", criticalPath != null ? criticalPath.size() : 0);
            writer.println("  },");

            // VMs section
            writer.println("  \"vms\": [");
            writeVMsSection(writer, vms);
            writer.println("  ],");

            // Tasks section
            writer.println("  \"tasks\": [");
            writeTasksSection(writer, tasks, taskAST, taskAFT, criticalPath, vmSchedule, duplicatedTasks);
            writer.println("  ],");

            // Schedule section (Gantt chart data)
            writer.println("  \"schedule\": [");
            writeScheduleSection(writer, vmSchedule, taskAST, taskAFT, tasks, duplicatedTasks, criticalPath);
            writer.println("  ],");

            // Critical path section
            writer.println("  \"criticalPath\": [");
            writeCriticalPathSection(writer, criticalPath);
            writer.println("  ],");

            // Duplications section
            writer.println("  \"duplications\": [");
            writeDuplicationsSection(writer, duplicatedTasks);
            writer.println("  ]");

            writer.println("}");

            System.out.println("   Gantt chart saved: " + outputFile.getPath());

        } catch (IOException e) {
            System.err.println("   Failed to save Gantt chart: " + e.getMessage());
        }
    }

    /**
     * Write VMs section
     */
    private static void writeVMsSection(PrintWriter writer, List<VM> vms) {
        if (vms == null || vms.isEmpty()) return;

        int count = 0;
        for (VM vm : vms) {
            writer.print("    {");
            writer.printf("\"id\": %d, ", vm.getID());
            writer.printf(Locale.US, "\"processingCapacity\": %.4f", vm.getCapability("processingCapacity"));
            writer.print("}");
            count++;
            if (count < vms.size()) {
                writer.println(",");
            } else {
                writer.println();
            }
        }
    }

    /**
     * Write tasks section with dependencies
     */
    private static void writeTasksSection(
            PrintWriter writer, 
            List<task> tasks,
            Map<Integer, Double> taskAST,
            Map<Integer, Double> taskAFT,
            Set<Integer> criticalPath,
            Map<Integer, List<Integer>> vmSchedule,
            Map<Integer, Set<Integer>> duplicatedTasks) {

        if (tasks == null || tasks.isEmpty()) return;

        // Build task-to-VM map
        Map<Integer, Integer> taskToVM = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : vmSchedule.entrySet()) {
            for (Integer taskId : entry.getValue()) {
                if (!taskToVM.containsKey(taskId)) {
                    taskToVM.put(taskId, entry.getKey());
                }
            }
        }

        // Check if task is duplicated
        Set<Integer> allDuplicatedTasks = new HashSet<>();
        if (duplicatedTasks != null) {
            for (Set<Integer> dups : duplicatedTasks.values()) {
                allDuplicatedTasks.addAll(dups);
            }
        }

        int count = 0;
        for (task t : tasks) {
            int taskId = t.getID();
            double ast = taskAST != null ? taskAST.getOrDefault(taskId, 0.0) : 0.0;
            double aft = taskAFT != null ? taskAFT.getOrDefault(taskId, 0.0) : 0.0;
            boolean isCritical = criticalPath != null && criticalPath.contains(taskId);
            boolean isDuplicated = allDuplicatedTasks.contains(taskId);
            Integer vmId = taskToVM.get(taskId);

            writer.print("    {");
            writer.printf("\"id\": %d, ", taskId);
            writer.printf(Locale.US, "\"size\": %.4f, ", t.getSize());
            writer.printf(Locale.US, "\"ast\": %.4f, ", ast);
            writer.printf(Locale.US, "\"aft\": %.4f, ", aft);
            writer.printf("\"vmId\": %s, ", vmId != null ? vmId.toString() : "null");
            writer.printf("\"isCritical\": %b, ", isCritical);
            writer.printf("\"isDuplicated\": %b, ", isDuplicated);
            
            // Predecessors
            writer.print("\"predecessors\": [");
            if (t.getPre() != null && !t.getPre().isEmpty()) {
                List<Integer> preds = new ArrayList<>(t.getPre());
                for (int i = 0; i < preds.size(); i++) {
                    writer.print(preds.get(i));
                    if (i < preds.size() - 1) writer.print(", ");
                }
            }
            writer.print("], ");

            // Successors
            writer.print("\"successors\": [");
            if (t.getSucc() != null && !t.getSucc().isEmpty()) {
                List<Integer> succs = new ArrayList<>(t.getSucc());
                for (int i = 0; i < succs.size(); i++) {
                    writer.print(succs.get(i));
                    if (i < succs.size() - 1) writer.print(", ");
                }
            }
            writer.print("]");

            writer.print("}");
            count++;
            if (count < tasks.size()) {
                writer.println(",");
            } else {
                writer.println();
            }
        }
    }

    /**
     * Write schedule section (main Gantt chart data)
     * Organized by VM, each with list of scheduled tasks
     */
    private static void writeScheduleSection(
            PrintWriter writer,
            Map<Integer, List<Integer>> vmSchedule,
            Map<Integer, Double> taskAST,
            Map<Integer, Double> taskAFT,
            List<task> tasks,
            Map<Integer, Set<Integer>> duplicatedTasks,
            Set<Integer> criticalPath) {

        if (vmSchedule == null || vmSchedule.isEmpty()) return;

        // Build task lookup map
        Map<Integer, task> taskMap = new HashMap<>();
        if (tasks != null) {
            for (task t : tasks) {
                taskMap.put(t.getID(), t);
            }
        }

        List<Integer> vmIds = new ArrayList<>(vmSchedule.keySet());
        Collections.sort(vmIds);

        int vmCount = 0;
        for (Integer vmId : vmIds) {
            List<Integer> taskIds = vmSchedule.get(vmId);
            if (taskIds == null) taskIds = new ArrayList<>();

            writer.println("    {");
            writer.printf("      \"vmId\": %d,%n", vmId);
            writer.println("      \"tasks\": [");

            // Sort tasks by AST
            List<Integer> sortedTaskIds = new ArrayList<>(taskIds);
            sortedTaskIds.sort((a, b) -> {
                double astA = taskAST != null ? taskAST.getOrDefault(a, 0.0) : 0.0;
                double astB = taskAST != null ? taskAST.getOrDefault(b, 0.0) : 0.0;
                return Double.compare(astA, astB);
            });

            int taskCount = 0;
            for (Integer taskId : sortedTaskIds) {
                double ast = taskAST != null ? taskAST.getOrDefault(taskId, 0.0) : 0.0;
                double aft = taskAFT != null ? taskAFT.getOrDefault(taskId, 0.0) : 0.0;
                task t = taskMap.get(taskId);
                double size = t != null ? t.getSize() : 0.0;
                
                boolean isCritical = criticalPath != null && criticalPath.contains(taskId);
                boolean isDuplicate = duplicatedTasks != null && 
                                     duplicatedTasks.get(vmId) != null && 
                                     duplicatedTasks.get(vmId).contains(taskId);

                writer.print("        {");
                writer.printf("\"taskId\": %d, ", taskId);
                writer.printf(Locale.US, "\"start\": %.4f, ", ast);
                writer.printf(Locale.US, "\"end\": %.4f, ", aft);
                writer.printf(Locale.US, "\"duration\": %.4f, ", aft - ast);
                writer.printf(Locale.US, "\"size\": %.4f, ", size);
                writer.printf("\"isCritical\": %b, ", isCritical);
                writer.printf("\"isDuplicate\": %b", isDuplicate);
                writer.print("}");

                taskCount++;
                if (taskCount < sortedTaskIds.size()) {
                    writer.println(",");
                } else {
                    writer.println();
                }
            }

            writer.println("      ]");
            writer.print("    }");

            vmCount++;
            if (vmCount < vmIds.size()) {
                writer.println(",");
            } else {
                writer.println();
            }
        }
    }

    /**
     * Write critical path section
     */
    private static void writeCriticalPathSection(PrintWriter writer, Set<Integer> criticalPath) {
        if (criticalPath == null || criticalPath.isEmpty()) return;

        List<Integer> sorted = new ArrayList<>(criticalPath);
        Collections.sort(sorted);

        for (int i = 0; i < sorted.size(); i++) {
            writer.print("    " + sorted.get(i));
            if (i < sorted.size() - 1) {
                writer.println(",");
            } else {
                writer.println();
            }
        }
    }

    /**
     * Write duplications section
     */
    private static void writeDuplicationsSection(PrintWriter writer, Map<Integer, Set<Integer>> duplicatedTasks) {
        if (duplicatedTasks == null || duplicatedTasks.isEmpty()) return;

        // Count non-empty entries
        List<Map.Entry<Integer, Set<Integer>>> nonEmpty = new ArrayList<>();
        for (Map.Entry<Integer, Set<Integer>> entry : duplicatedTasks.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isEmpty()) {
                nonEmpty.add(entry);
            }
        }

        int count = 0;
        for (Map.Entry<Integer, Set<Integer>> entry : nonEmpty) {
            writer.print("    {");
            writer.printf("\"vmId\": %d, ", entry.getKey());
            writer.print("\"duplicatedTaskIds\": [");
            
            List<Integer> taskIds = new ArrayList<>(entry.getValue());
            Collections.sort(taskIds);
            for (int i = 0; i < taskIds.size(); i++) {
                writer.print(taskIds.get(i));
                if (i < taskIds.size() - 1) writer.print(", ");
            }
            
            writer.print("]}");
            count++;
            if (count < nonEmpty.size()) {
                writer.println(",");
            } else {
                writer.println();
            }
        }
    }

    /**
     * Ensure output directory exists
     */
    private static void ensureOutputDir() {
        File dir = new File(OUTPUT_DIR);
        if (!dir.exists()) {
            dir.mkdirs();
            System.out.println("   Created Gantt charts directory: " + OUTPUT_DIR);
        }
    }

    /**
     * Get the output directory path
     */
    public static String getOutputDir() {
        return OUTPUT_DIR;
    }
}
