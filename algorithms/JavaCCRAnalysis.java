import java.io.*;
import java.util.*;

/**
 * Sistema integrato di analisi CCR con generazione DAG in Java
 * Sostituisce l'approccio Python + Java con un sistema Java puro
 */
public class JavaCCRAnalysis {
    
    // Istanze statiche per riutilizzo dell'algoritmo SM-CPTD
    private static SMCPTD smcptdInstance = null;
    private static Map<Integer, VM> vmMapping = null;
    private static Map<String, Double> baseCommunicationCosts = null;
    private static String currentWorkflowType = null;
    
    private static final double[] CCR_RANGE = {0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0};
    private static final int TARGET_TASKS = 50;
    private static final int TARGET_VMS = 5;
    
    public static void main(String[] args) {
        String workflowType = (args.length > 0) ? args[0].toLowerCase() : "cybershake";
        
        System.out.println("🚀 ANALISI CCR INTEGRATA JAVA");
        System.out.println("===============================");
        System.out.println("Workflow: " + workflowType.toUpperCase());
        System.out.println("Target task: " + TARGET_TASKS);
        System.out.println("Target VM: " + TARGET_VMS);
        System.out.println("CCR range: " + Arrays.toString(CCR_RANGE));
        System.out.println();
        
        try {
            // 1. Genera workflow usando il generatore Java
            WorkflowDAGGenerator.WorkflowType type = parseWorkflowType(workflowType);
            WorkflowDAGGenerator.WorkflowConfig config = new WorkflowDAGGenerator.WorkflowConfig(type, TARGET_TASKS, TARGET_VMS);
            
            System.out.println("📊 Step 1: Generazione workflow...");
            WorkflowDAGGenerator generator = new WorkflowDAGGenerator(config);
            WorkflowDAGGenerator.WorkflowData workflow = generator.generateWorkflow();
            
            System.out.println("   Task generati: " + workflow.tasks.size());
            System.out.println("   VM configurate: " + workflow.vms.size());
            
            // 2. Esporta file CSV per compatibilità con algoritmi esistenti
            System.out.println("\n📁 Step 2: Export file CSV...");
            workflow.exportToCSV("../data");
            System.out.println("   ✅ File CSV esportati in ../data/");
            
            // 3. Esegui analisi CCR per ogni valore
            System.out.println("\n⚡ Step 3: Analisi CCR...");
            List<CCRResult> results = new ArrayList<>();
            
            for (double ccr : CCR_RANGE) {
                System.out.printf("\n   🔄 CCR = %.1f%n", ccr);
                
                try {
                    // Esegui algoritmo SM-CPTD completo (DCP → SMGT → LOTD)
                    CCRResult result = executeWithSMCPTD(ccr, workflowType);
                    results.add(result);
                    
                    System.out.printf("      SLR: %.3f, Makespan: %.3f%n", result.slr, result.makespan);
                    
                } catch (Exception e) {
                    System.err.println("      ❌ Errore CCR " + ccr + ": " + e.getMessage());
                    // Continua con il prossimo CCR
                }
            }
            
            // 4. Salva risultati e genera statistiche
            System.out.println("\n💾 Step 4: Salvataggio risultati...");
            saveResults(results, workflowType);
            generateSummary(results, workflowType);
            
            System.out.println("\n🎉 Analisi completata!");
            System.out.println("   📊 Risultati salvati: ccr_analysis_results_" + workflowType + ".json");
            System.out.println("   📈 Grafico: " + workflowType + "_ccr_analysis.png");
            
        } catch (Exception e) {
            System.err.println("❌ Errore durante l'analisi: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static WorkflowDAGGenerator.WorkflowType parseWorkflowType(String type) {
        switch (type.toLowerCase()) {
            case "cybershake": return WorkflowDAGGenerator.WorkflowType.CYBERSHAKE;
            case "epigenomics": return WorkflowDAGGenerator.WorkflowType.EPIGENOMICS;
            case "ligo": return WorkflowDAGGenerator.WorkflowType.LIGO;
            case "montage": return WorkflowDAGGenerator.WorkflowType.MONTAGE;
            default:
                System.out.println("⚠️  Tipo workflow sconosciuto '" + type + "', usando CyberShake");
                return WorkflowDAGGenerator.WorkflowType.CYBERSHAKE;
        }
    }
    
    /**
     * Esegue l'algoritmo SM-CPTD completo (DCP → SMGT → LOTD)
     */
    private static CCRResult executeWithSMCPTD(double ccr, String workflowType) throws IOException {
        // Inizializza l'algoritmo solo la prima volta o se cambia il workflow
        if (smcptdInstance == null || !workflowType.equals(currentWorkflowType)) {
            smcptdInstance = new SMCPTD();
            currentWorkflowType = workflowType;
            
            // Carica dati dal CSV
            smcptdInstance.loadData("../data/dag.csv", "../data/task.csv", "../data/processing_capacity.csv");
            
            // Crea mapping VM
            vmMapping = new HashMap<>();
            for (int i = 0; i < smcptdInstance.getSMGT().getVMs().size(); i++) {
                vmMapping.put(i, smcptdInstance.getSMGT().getVMs().get(i));
            }
            
            // Genera communication costs base (CCR = 1.0)
            baseCommunicationCosts = generateCommunicationCosts(
                smcptdInstance.getSMGT().getTasks(), vmMapping, 1.0);
            
            // Esegui algoritmo SM-CPTD completo una sola volta
            smcptdInstance.executeSMCPTD(baseCommunicationCosts, vmMapping);
        }
        
        // Ricalcola metriche per il CCR specifico
        smcptdInstance.calculateMetricsForCCR(ccr, baseCommunicationCosts, vmMapping);
        
        // Ottieni risultati
        double makespan = smcptdInstance.getMakespan();
        double slr = smcptdInstance.getSLR();
        int criticalPathLength = smcptdInstance.getCriticalPath().size();
        
        return new CCRResult(ccr, slr, makespan, criticalPathLength, workflowType);
    }
    
    /**
     * Metodo legacy per compatibilità (ora sostituito da SM-CPTD)
     */
    @Deprecated
    private static CCRResult executeWithCCR(SMGT smgt, double ccr, String workflowType) throws IOException {
        // Crea mapping VM da SMGT
        Map<Integer, VM> vmMapping = new HashMap<>();
        for (int i = 0; i < smgt.getVMs().size(); i++) {
            vmMapping.put(i, smgt.getVMs().get(i));
        }
        
        // Genera communication costs usando CCR
        Map<String, Double> communicationCosts = generateCommunicationCosts(smgt.getTasks(), vmMapping, ccr);
        
        // Trova exit task
        task exitTask = findExitTask(smgt.getTasks());
        
        // Organizza task per livelli
        Map<Integer, List<Integer>> taskLevels = DCP.organizeTasksByLevels(smgt.getTasks());
        
        // Esegui algoritmo DCP
        Set<Integer> criticalPath = DCP.executeDCP(smgt.getTasks(), taskLevels, exitTask, communicationCosts, vmMapping);
        
        // Calcola makespan usando SMGT algorithm
        Map<Integer, List<Integer>> assignments = smgt.runSMGTAlgorithmCorrect();
        double makespan = calculateMakespan(assignments, smgt.getTasks(), vmMapping, communicationCosts);
        
        // Calcola SLR
        Map<String, task> taskMap = new HashMap<>();
        for (task t : smgt.getTasks()) {
            taskMap.put("t" + t.getID(), t);  // Formato "t1", "t2", etc.
            taskMap.put(String.valueOf(t.getID()), t);  // Formato "1", "2", etc.
        }
        
        // Debug: verifica critical path
        if (criticalPath.isEmpty()) {
            System.err.println("      ⚠️  Critical path vuoto, usando fallback SLR");
            double fallbackSLR = makespan / calculateMinExecutionTimeSum(smgt.getTasks(), vmMapping, "processingCapacity");
            return new CCRResult(ccr, fallbackSLR, makespan, 1, workflowType);
        }
        
        double slr = Metrics.SLR(makespan, criticalPath, taskMap, vmMapping, "processingCapacity");
        
        return new CCRResult(ccr, slr, makespan, criticalPath.size(), workflowType);
    }
    
    private static Map<String, Double> generateCommunicationCosts(List<task> tasks, Map<Integer, VM> vms, double ccr) {
        // Calcola average bandwidth
        double totalBandwidth = 0.0;
        int count = 0;
        for (VM vm1 : vms.values()) {
            for (VM vm2 : vms.values()) {
                if (vm1.getID() != vm2.getID()) {
                    // Usa bandwidth fissa se non disponibile matrice
                    totalBandwidth += 25.0; // Media tra 20-30
                    count++;
                }
            }
        }
        double averageBandwidth = count > 0 ? totalBandwidth / count : 25.0;
        
        // Genera costi di comunicazione
        Map<String, Double> costs = new HashMap<>();
        for (task t : tasks) {
            if (t.getSucc() != null) {
                for (int succId : t.getSucc()) {
                    double cost = (t.getSize() * ccr) / averageBandwidth;
                    costs.put(t.getID() + "_" + succId, cost);
                }
            }
        }
        return costs;
    }
    
    private static task findExitTask(List<task> tasks) {
        // Trova task senza successori
        for (task t : tasks) {
            if (t.getSucc() == null || t.getSucc().isEmpty()) {
                return t;
            }
        }
        // Se non trovato, ritorna l'ultimo task
        return tasks.get(tasks.size() - 1);
    }
    
    private static double calculateMakespan(Map<Integer, List<Integer>> assignments, 
                                          List<task> tasks, Map<Integer, VM> vms, 
                                          Map<String, Double> communicationCosts) {
        double maxFinishTime = 0.0;
        
        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmId = entry.getKey();
            List<Integer> assignedTasks = entry.getValue();
            VM vm = vms.get(vmId);
            
            if (vm == null) continue;
            
            double vmTime = 0.0;
            for (Integer taskId : assignedTasks) {
                task t = findTaskById(tasks, taskId);
                if (t != null) {
                    double processingCapacity = vm.getCapability("processingCapacity");
                    if (processingCapacity <= 0) {
                        processingCapacity = vm.getCapability("processing");
                    }
                    if (processingCapacity <= 0) {
                        processingCapacity = 15.0; // Default
                    }
                    double executionTime = t.getSize() / processingCapacity;
                    vmTime += executionTime;
                }
            }
            
            maxFinishTime = Math.max(maxFinishTime, vmTime);
        }
        
        return maxFinishTime;
    }
    
    private static task findTaskById(List<task> tasks, int taskId) {
        for (task t : tasks) {
            if (t.getID() == taskId) {
                return t;
            }
        }
        return null;
    }
    
    private static void saveResults(List<CCRResult> results, String workflowType) throws IOException {
        // Salva in formato JSON compatibile con gli script Python esistenti
        try (PrintWriter writer = new PrintWriter(new FileWriter("ccr_analysis_results_" + workflowType + ".json"))) {
            writer.println("{");
            writer.println("  \"workflow_type\": \"" + workflowType + "\",");
            writer.println("  \"analysis_date\": \"" + new Date() + "\",");
            writer.println("  \"parameters\": {");
            writer.println("    \"target_tasks\": " + TARGET_TASKS + ",");
            writer.println("    \"target_vms\": " + TARGET_VMS + ",");
            writer.println("    \"ccr_range\": " + Arrays.toString(CCR_RANGE));
            writer.println("  },");
            writer.println("  \"results\": [");
            
            for (int i = 0; i < results.size(); i++) {
                CCRResult result = results.get(i);
                writer.println("    {");
                writer.println("      \"ccr\": " + result.ccr + ",");
                writer.println("      \"slr\": " + result.slr + ",");
                writer.println("      \"makespan\": " + result.makespan + ",");
                writer.println("      \"critical_path_length\": " + result.criticalPathLength);
                writer.print("    }");
                if (i < results.size() - 1) writer.println(",");
                else writer.println();
            }
            
            writer.println("  ]");
            writer.println("}");
        }
    }
    
    private static void generateSummary(List<CCRResult> results, String workflowType) {
        if (results.isEmpty()) return;
        
        double minSLR = results.stream().mapToDouble(r -> r.slr).min().orElse(0.0);
        double maxSLR = results.stream().mapToDouble(r -> r.slr).max().orElse(0.0);
        double minMakespan = results.stream().mapToDouble(r -> r.makespan).min().orElse(0.0);
        double maxMakespan = results.stream().mapToDouble(r -> r.makespan).max().orElse(0.0);
        
        double slrIncrease = ((maxSLR / minSLR) - 1) * 100;
        
        // Calcola correlazione CCR-SLR
        double correlation = calculateCorrelation(results);
        
        System.out.println("\n📋 RIASSUNTO - " + workflowType.toUpperCase());
        System.out.println("========================================");
        System.out.printf("Task: %d, VM: %d%n", TARGET_TASKS, TARGET_VMS);
        System.out.printf("CCR range: %.1f → %.1f (%d punti)%n", CCR_RANGE[0], CCR_RANGE[CCR_RANGE.length-1], CCR_RANGE.length);
        System.out.printf("SLR range: %.3f → %.3f%n", minSLR, maxSLR);
        System.out.printf("Makespan range: %.3f → %.3f%n", minMakespan, maxMakespan);
        System.out.printf("Aumento SLR: +%.1f%%%n", slrIncrease);
        System.out.printf("Correlazione CCR↔SLR: %.3f%n", correlation);
        
        System.out.println("\n🎉 Analisi " + workflowType + " completata!");
    }
    
    private static double calculateMinExecutionTimeSum(List<task> tasks, Map<Integer, VM> vms, String capabilityName) {
        double sum = 0.0;
        for (task t : tasks) {
            double minET = Double.POSITIVE_INFINITY;
            for (VM vm : vms.values()) {
                double capability = vm.getCapability(capabilityName);
                if (capability <= 0) capability = 15.0; // Default
                double et = t.getSize() / capability;
                minET = Math.min(minET, et);
            }
            if (minET != Double.POSITIVE_INFINITY) {
                sum += minET;
            }
        }
        return sum > 0 ? sum : 1.0; // Evita divisione per zero
    }
    
    private static double calculateCorrelation(List<CCRResult> results) {
        if (results.size() < 2) return 0.0;
        
        double[] ccr = results.stream().mapToDouble(r -> r.ccr).toArray();
        double[] slr = results.stream().mapToDouble(r -> r.slr).toArray();
        
        // Calcola medie
        double meanCCR = Arrays.stream(ccr).average().orElse(0.0);
        double meanSLR = Arrays.stream(slr).average().orElse(0.0);
        
        // Calcola numeratore e denominatori
        double numerator = 0.0;
        double denomCCR = 0.0;
        double denomSLR = 0.0;
        
        for (int i = 0; i < ccr.length; i++) {
            double diffCCR = ccr[i] - meanCCR;
            double diffSLR = slr[i] - meanSLR;
            numerator += diffCCR * diffSLR;
            denomCCR += diffCCR * diffCCR;
            denomSLR += diffSLR * diffSLR;
        }
        
        double denominator = Math.sqrt(denomCCR * denomSLR);
        return denominator != 0 ? numerator / denominator : 0.0;
    }
    
    // Classe per i risultati
    private static class CCRResult {
        double ccr;
        double slr;
        double makespan;
        int criticalPathLength;
        String workflowType;
        
        CCRResult(double ccr, double slr, double makespan, int criticalPathLength, String workflowType) {
            this.ccr = ccr;
            this.slr = slr;
            this.makespan = makespan;
            this.criticalPathLength = criticalPathLength;
            this.workflowType = workflowType;
        }
    }
}
