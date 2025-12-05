import java.util.*;
import java.io.*;

/**
 * SM-CPTD: Stable Matching Cloud-based Parallel Task Duplication Algorithm
 * 
 * Questo algoritmo combina tre componenti principali:
 * 1. DCP (Dynamic Critical Path) - Identifica il cammino critico
 * 2. SMGT (Stable Matching Game Theory) - Assegna task alle VM usando game theory
 * 3. LOTD (List of Task Duplication) - Duplica task per ottimizzare le performance
 * 
 * Sequenza di esecuzione: call DCP → call SMGT → call LOTD
 * 
 * @author Lorenzo Cappetti
 * @version 1.0
 */
public class SMCPTD {
    
    private SMGT smgt;
    private LOTD lotd;
    
    // Risultati dell'algoritmo
    private Set<Integer> criticalPath;
    private Map<Integer, List<Integer>> smgtAssignments;
    private Map<Integer, List<Integer>> finalAssignments;
    private double makespan;
    private double slr;
    
    // Dati del DAG (necessari per ricalcolare il critical path)
    private Map<Integer, List<Integer>> taskLevels;
    private task exitTask;
    
    /**
     * Costruttore dell'algoritmo SM-CPTD
     */
    public SMCPTD() {
        this.smgt = new SMGT();
        this.criticalPath = new HashSet<>();
        this.smgtAssignments = new HashMap<>();
        this.finalAssignments = new HashMap<>();
        this.makespan = 0.0;
        this.slr = 0.0;
    }
    
    /**
     * Carica i dati necessari per l'esecuzione dell'algoritmo
     * 
     * @param dagFilename File CSV contenente la struttura del DAG
     * @param taskFilename File CSV contenente i dati dei task
     * @param vmFilename File CSV contenente le capacità delle VM
     * @throws IOException Se ci sono errori nella lettura dei file
     */
    public void loadData(String dagFilename, String taskFilename, String vmFilename) throws IOException {
        System.out.println("🚀 SM-CPTD: Caricamento dati...");
        
        // Carica dati in SMGT (che gestisce task e VM)
        smgt.loadTasksFromCSV(dagFilename, taskFilename);
        smgt.loadVMsFromCSV(vmFilename);
        
        System.out.println("✅ Dati caricati: " + smgt.getTasks().size() + " task, " + smgt.getVMs().size() + " VM");
    }
    
    /**
     * Esegue l'algoritmo SM-CPTD completo
     * Sequenza: DCP → SMGT → LOTD
     * 
     * @param communicationCosts Mappa dei costi di comunicazione tra task
     * @param vmMapping Mapping delle VM per ID
     * @return Assegnamenti finali task→VM dopo tutte le ottimizzazioni
     */
    public Map<Integer, List<Integer>> executeSMCPTD(Map<String, Double> communicationCosts, Map<Integer, VM> vmMapping) {
        System.out.println("\n🎯 ESECUZIONE ALGORITMO SM-CPTD (VERSIONE CORRETTA)");
        System.out.println("==================================================");
        
        try {
            // STEP 1: DCP - Dynamic Critical Path Detection + Scheduling
            System.out.println("\n📍 STEP 1: Esecuzione DCP (Dynamic Critical Path + Scheduling)");
            DCP.DCPScheduleResult dcpResult = executeDCPWithScheduling(communicationCosts, vmMapping);
            
            // STEP 2: SMGT - Stable Matching Game Theory (excluding CP tasks)
            System.out.println("\n🎮 STEP 2: Esecuzione SMGT (escludendo task del Critical Path)");
            executeSMGTWithCP(dcpResult);
            
            // STEP 3: LOTD - List of Task Duplication
            System.out.println("\n📋 STEP 3: Esecuzione LOTD (List of Task Duplication)");
            executeLOTDCorrect();
            
            // STEP 4: Calcolo metriche finali
            System.out.println("\n📊 STEP 4: Calcolo metriche finali");
            calculateFinalMetrics(communicationCosts, vmMapping);
            
            System.out.println("\n🎉 SM-CPTD COMPLETATO CON SUCCESSO!");
            printResults();
            
            return finalAssignments;
            
        } catch (Exception e) {
            System.err.println("❌ Errore durante l'esecuzione SM-CPTD: " + e.getMessage());
            e.printStackTrace();
            return smgtAssignments; // Fallback ai risultati SMGT
        }
    }
    
    /**
     * STEP 1: Esecuzione DCP con scheduling (nuova versione)
     * Identifica il Critical Path E schedula i task CP sulle VM
     */
    private DCP.DCPScheduleResult executeDCPWithScheduling(Map<String, Double> communicationCosts, Map<Integer, VM> vmMapping) {
        System.out.println("   🔍 Identificazione e scheduling del cammino critico...");
        
        // Trova exit task e salvalo come campo della classe
        exitTask = findExitTask(smgt.getTasks());
        
        // Organizza task per livelli e salvalo come campo della classe
        taskLevels = DCP.organizeTasksByLevels(smgt.getTasks());
        
        // Esegui algoritmo DCP con scheduling (nuova versione)
        DCP.DCPScheduleResult dcpResult = DCP.executeDCPWithScheduling(
            smgt.getTasks(), taskLevels, exitTask, communicationCosts, vmMapping);
        
        // Salva il critical path
        criticalPath = dcpResult.criticalPath;
        
        System.out.println("   ✅ Critical path identificato e schedulato: " + criticalPath.size() + " task critici");
        System.out.println("   📋 Task critici: " + criticalPath.toString());
        System.out.println("   📋 Assegnazioni CP:");
        for (Map.Entry<Integer, Integer> entry : dcpResult.cpTaskToVM.entrySet()) {
            System.out.println("      t" + entry.getKey() + " → VM" + entry.getValue() + 
                " (AFT=" + String.format("%.2f", dcpResult.cpTaskAFT.get(entry.getKey())) + ")");
        }
        
        return dcpResult;
    }
    
    /**
     * STEP 2: Esecuzione SMGT con esclusione task CP (nuova versione)
     * Assegna i task NON nel Critical Path usando stable matching
     */
    private void executeSMGTWithCP(DCP.DCPScheduleResult dcpResult) {
        System.out.println("   🎯 Assegnamento task non-CP usando Game Theory...");
        
        // Esegui algoritmo SMGT escludendo i task del Critical Path
        smgtAssignments = smgt.runSMGTWithCP(dcpResult.criticalPath, dcpResult.cpTaskToVM);
        
        System.out.println("   ✅ SMGT completato:");
        for (Map.Entry<Integer, List<Integer>> entry : smgtAssignments.entrySet()) {
            System.out.println("      VM" + entry.getKey() + ": " + entry.getValue().size() + " task");
        }
    }
    
    /**
     * STEP 3: Esecuzione LOTD corretto (nuova versione)
     */
    private void executeLOTDCorrect() {
        System.out.println("   📂 Ottimizzazione con duplicazione task (entry tasks)...");
        
        try {
            // Crea istanza LOTD con i dati SMGT
            lotd = new LOTD(smgt);
            
            // Esegui algoritmo LOTD corretto sui risultati SMGT
            finalAssignments = lotd.executeLOTDCorrect(smgtAssignments);
            
            System.out.println("   ✅ LOTD completato:");
            for (Map.Entry<Integer, List<Integer>> entry : finalAssignments.entrySet()) {
                System.out.println("      VM" + entry.getKey() + ": " + entry.getValue().size() + " task");
            }
            
            // Mostra duplicati
            Map<Integer, Set<Integer>> duplicates = lotd.getDuplicatedTasks();
            int totalDups = duplicates.values().stream().mapToInt(Set::size).sum();
            if (totalDups > 0) {
                System.out.println("   📋 Task duplicati: " + totalDups);
            }
            
        } catch (Exception e) {
            System.err.println("   ⚠️  Errore in LOTD, uso risultati SMGT: " + e.getMessage());
            e.printStackTrace();
            finalAssignments = new HashMap<>(smgtAssignments);
        }
    }
    
    // ========== Metodi legacy per backward compatibility ==========
    
    /**
     * STEP 1 Legacy: Esecuzione DCP (solo identificazione, senza scheduling)
     * @deprecated Use executeDCPWithScheduling instead
     */
    private void executeDCP(Map<String, Double> communicationCosts, Map<Integer, VM> vmMapping) {
        System.out.println("   🔍 Identificazione cammino critico...");
        
        // Trova exit task e salvalo come campo della classe
        exitTask = findExitTask(smgt.getTasks());
        
        // Organizza task per livelli e salvalo come campo della classe
        taskLevels = DCP.organizeTasksByLevels(smgt.getTasks());
        
        // Esegui algoritmo DCP
        criticalPath = DCP.executeDCP(smgt.getTasks(), taskLevels, exitTask, communicationCosts, vmMapping);
        
        System.out.println("   ✅ Critical path identificato: " + criticalPath.size() + " task critici");
        System.out.println("   📋 Task critici: " + criticalPath.toString());
    }
    
    /**
     * STEP 2 Legacy: Esecuzione SMGT (senza esclusione CP)
     * @deprecated Use executeSMGTWithCP instead
     */
    private void executeSMGT() {
        System.out.println("   🎯 Assegnamento task usando Game Theory...");
        
        // Esegui algoritmo SMGT
        smgtAssignments = smgt.runSMGTAlgorithmCorrect();
        
        System.out.println("   ✅ SMGT completato:");
        for (Map.Entry<Integer, List<Integer>> entry : smgtAssignments.entrySet()) {
            System.out.println("      VM" + entry.getKey() + ": " + entry.getValue().size() + " task");
        }
    }
    
    /**
     * STEP 3 Legacy: Esecuzione LOTD
     * @deprecated Use executeLOTDCorrect instead
     */
    private void executeLOTD() {
        System.out.println("   📂 Ottimizzazione con duplicazione task...");
        
        try {
            // Crea istanza LOTD con i dati SMGT
            lotd = new LOTD(smgt);
            
            // Esegui algoritmo LOTD sui risultati SMGT
            finalAssignments = lotd.executeLOTD();
            
            System.out.println("   ✅ LOTD completato:");
            for (Map.Entry<Integer, List<Integer>> entry : finalAssignments.entrySet()) {
                System.out.println("      VM" + entry.getKey() + ": " + entry.getValue().size() + " task (dopo duplicazione)");
            }
            
        } catch (Exception e) {
            System.err.println("   ⚠️  Errore in LOTD, uso risultati SMGT: " + e.getMessage());
            finalAssignments = new HashMap<>(smgtAssignments);
        }
    }
    
    /**
     * Calcola le metriche per un CCR specifico ricalcolando il critical path
     * 
     * @param CCR Il valore di CCR specifico
     * @param originalCommunicationCosts I costi di comunicazione originali
     * @param vmMapping Mapping delle VM
     */
    public void calculateMetricsForCCR(double CCR, Map<String, Double> originalCommunicationCosts, Map<Integer, VM> vmMapping) {
        try {
            // STEP 1: Aggiorna i costi di comunicazione con il nuovo CCR
            Map<String, Double> updatedCommunicationCosts = updateCommunicationCostsForCCR(originalCommunicationCosts, CCR);
            
            // DEBUG: Verifica che i costi siano effettivamente cambiati
            if (CCR == 0.4 || CCR == 2.0) {
                System.out.println("   🔍 DEBUG CCR=" + CCR + " communication costs sample:");
                int count = 0;
                for (Map.Entry<String, Double> entry : updatedCommunicationCosts.entrySet()) {
                    if (count < 3) {
                        System.out.println("      " + entry.getKey() + " → " + entry.getValue());
                        count++;
                    }
                }
            }
            
            // STEP 2: Ricalcola il critical path per questo CCR
            Set<Integer> currentCriticalPath = DCP.executeDCP(smgt.getTasks(), taskLevels, exitTask, updatedCommunicationCosts, vmMapping);
            
            // STEP 3: Calcola makespan con i nuovi costi di comunicazione
            makespan = calculateMakespan(finalAssignments, smgt.getTasks(), vmMapping, updatedCommunicationCosts);
            
            // STEP 4: Calcola SLR con il critical path aggiornato
            Map<String, task> taskMap = new HashMap<>();
            for (task t : smgt.getTasks()) {
                taskMap.put("t" + t.getID(), t);
                taskMap.put(String.valueOf(t.getID()), t);
            }
            
            // Calcola SLR usando il critical path aggiornato
            double sumMinExecutionTimes = 0.0;
            for (Integer taskId : currentCriticalPath) {
                task cpTask = taskMap.get("t" + taskId);
                if (cpTask != null) {
                    double minET = Double.POSITIVE_INFINITY;
                    for (VM vm : vmMapping.values()) {
                        double et = cpTask.getSize() / vm.getCapability("processing");
                        minET = Math.min(minET, et);
                    }
                    if (minET != Double.POSITIVE_INFINITY) {
                        sumMinExecutionTimes += minET;
                    }
                }
            }
            
            if (sumMinExecutionTimes > 0) {
                slr = makespan / sumMinExecutionTimes;
            } else {
                slr = Double.POSITIVE_INFINITY;
            }
            
            // DEBUG: Mostra il risultato per questo CCR
            System.out.println("   🔄 CCR=" + CCR + " → Critical Path: " + currentCriticalPath + 
                             " → Makespan: " + String.format("%.3f", makespan) + 
                             " → SLR: " + String.format("%.3f", slr));
            
        } catch (Exception e) {
            System.err.println("   ❌ Errore calcolo metriche per CCR " + CCR + ": " + e.getMessage());
            makespan = 0.0;
            slr = Double.POSITIVE_INFINITY;
        }
    }
    
    /**
     * Aggiorna i costi di comunicazione moltiplicando per il nuovo CCR
     */
    private Map<String, Double> updateCommunicationCostsForCCR(Map<String, Double> originalCosts, double CCR) {
        Map<String, Double> updatedCosts = new HashMap<>();
        
        // Assumiamo che i costi originali siano calcolati con CCR = 1.0
        // Quindi moltiplichiamo per il nuovo CCR
        for (Map.Entry<String, Double> entry : originalCosts.entrySet()) {
            updatedCosts.put(entry.getKey(), entry.getValue() * CCR);
        }
        
        return updatedCosts;
    }

    /**
     * STEP 4: Calcolo metriche finali
     */
    private void calculateFinalMetrics(Map<String, Double> communicationCosts, Map<Integer, VM> vmMapping) {
        System.out.println("   📈 Calcolo makespan e SLR...");
        
        try {
            // Calcola makespan sui risultati finali
            makespan = calculateMakespan(finalAssignments, smgt.getTasks(), vmMapping, communicationCosts);
            
            // DEBUG: Verifica critical path
            System.out.println("   🔍 DEBUG Critical Path: " + criticalPath);
            
            // Calcola SLR
            Map<String, task> taskMap = new HashMap<>();
            for (task t : smgt.getTasks()) {
                taskMap.put("t" + t.getID(), t);
                taskMap.put(String.valueOf(t.getID()), t);
            }
            
            // DEBUG: Verifica task map
            System.out.println("   🔍 DEBUG Task Map keys: " + taskMap.keySet());
            
            if (!criticalPath.isEmpty()) {
                // DEBUG: Verifica se i task del critical path sono nella mappa
                for (Integer taskId : criticalPath) {
                    String taskKey = "t" + taskId;
                    if (!taskMap.containsKey(taskKey)) {
                        System.err.println("   ⚠️  Task " + taskKey + " non trovato nella mappa!");
                    }
                }
                
                // Calcola SLR manualmente per debug
                double sumMinExecutionTimes = 0.0;
                for (Integer taskId : criticalPath) {
                    task cpTask = taskMap.get("t" + taskId);
                    if (cpTask != null) {
                        double minET = Double.POSITIVE_INFINITY;
                        for (VM vm : vmMapping.values()) {
                            double et = cpTask.getSize() / vm.getCapability("processing");
                            minET = Math.min(minET, et);
                        }
                        if (minET != Double.POSITIVE_INFINITY) {
                            sumMinExecutionTimes += minET;
                        }
                        System.out.println("   🔍 Task t" + taskId + " minET: " + minET);
                    } else {
                        System.err.println("   ❌ Task t" + taskId + " non trovato!");
                    }
                }
                
                System.out.println("   🔍 Sum Min Execution Times: " + sumMinExecutionTimes);
                System.out.println("   🔍 Makespan: " + makespan);
                
                if (sumMinExecutionTimes > 0) {
                    slr = makespan / sumMinExecutionTimes;
                } else {
                    System.err.println("   ❌ Sum Min Execution Times è 0, usando fallback");
                    double minExecSum = calculateMinExecutionTimeSum(smgt.getTasks(), vmMapping, "processing");
                    slr = makespan / minExecSum;
                }
            } else {
                // Fallback SLR
                double minExecSum = calculateMinExecutionTimeSum(smgt.getTasks(), vmMapping, "processing");
                slr = makespan / minExecSum;
            }
            
            System.out.println("   ✅ Metriche calcolate: Makespan=" + String.format("%.3f", makespan) + 
                             ", SLR=" + String.format("%.3f", slr));
            
        } catch (Exception e) {
            System.err.println("   ⚠️  Errore calcolo metriche: " + e.getMessage());
            e.printStackTrace();
            makespan = 0.0;
            slr = Double.POSITIVE_INFINITY;
        }
    }
    
    /**
     * Stampa i risultati finali dell'algoritmo SM-CPTD
     */
    private void printResults() {
        System.out.println("\n📋 RISULTATI SM-CPTD:");
        System.out.println("==============================");
        System.out.println("🎯 Critical Path Size: " + criticalPath.size());
        System.out.println("📊 Final Makespan: " + String.format("%.3f", makespan));
        System.out.println("📈 Schedule Length Ratio: " + String.format("%.3f", slr));
        System.out.println("🖥️  VM Assignments:");
        
        for (Map.Entry<Integer, List<Integer>> entry : finalAssignments.entrySet()) {
            String taskList = entry.getValue().stream()
                .map(taskId -> "t" + taskId)
                .reduce((a, b) -> a + ", " + b)
                .orElse("(no tasks)");
            System.out.println("   VM" + entry.getKey() + ": " + taskList);
        }
    }
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Trova il task di uscita (senza successori)
     */
    private task findExitTask(List<task> tasks) {
        // Cerca task senza successori
        for (task t : tasks) {
            if (t.getSucc().isEmpty()) {
                return t;
            }
        }
        return tasks.get(tasks.size() - 1); // Fallback: ultimo task
    }
    
    /**
     * Calcola il makespan basato sugli assegnamenti CON correzione per CCR
     */
    private double calculateMakespan(Map<Integer, List<Integer>> assignments, List<task> tasks, 
                                   Map<Integer, VM> vmMapping, Map<String, Double> communicationCosts) {
        double maxFinishTime = 0.0;
        
        // STEP 1: Calcola tempo base di esecuzione
        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmId = entry.getKey();
            List<Integer> assignedTasks = entry.getValue();
            
            if (assignedTasks.isEmpty()) continue;
            
            VM vm = vmMapping.get(vmId);
            double vmFinishTime = 0.0;
            
            for (Integer taskId : assignedTasks) {
                task t = findTaskById(tasks, taskId);
                if (t != null) {
                    double execTime = t.getSize() / vm.getCapability("processing");
                    vmFinishTime += execTime;
                }
            }
            
            maxFinishTime = Math.max(maxFinishTime, vmFinishTime);
        }
        
        // STEP 2: Aggiungi correzione per communication costs (CCR)
        // Stima impatto medio dei tempi di comunicazione
        double totalCommunicationTime = 0.0;
        int communicationCount = 0;
        
        for (Double commCost : communicationCosts.values()) {
            totalCommunicationTime += commCost;
            communicationCount++;
        }
        
        if (communicationCount > 0) {
            double avgCommunicationTime = totalCommunicationTime / communicationCount;
            // Aggiungi ~20% del tempo medio di comunicazione al makespan
            maxFinishTime += avgCommunicationTime * 0.2;
        }
        
        return maxFinishTime;
    }
    
    /**
     * Trova un task per ID
     */
    private task findTaskById(List<task> tasks, int taskId) {
        for (task t : tasks) {
            if (t.getID() == taskId) {
                return t;
            }
        }
        return null;
    }
    
    /**
     * Calcola la somma dei tempi minimi di esecuzione
     */
    private double calculateMinExecutionTimeSum(List<task> tasks, Map<Integer, VM> vmMapping, String capability) {
        double sum = 0.0;
        
        // Trova la VM più veloce
        double maxProcessingCapacity = 0.0;
        for (VM vm : vmMapping.values()) {
            maxProcessingCapacity = Math.max(maxProcessingCapacity, vm.getCapability(capability));
        }
        
        // Calcola somma dei tempi minimi
        for (task t : tasks) {
            sum += t.getSize() / maxProcessingCapacity;
        }
        
        return sum;
    }
    
    // ==================== GETTERS ====================
    
    public Set<Integer> getCriticalPath() { return criticalPath; }
    public Map<Integer, List<Integer>> getSMGTAssignments() { return smgtAssignments; }
    public Map<Integer, List<Integer>> getFinalAssignments() { return finalAssignments; }
    public double getMakespan() { return makespan; }
    public double getSLR() { return slr; }
    public SMGT getSMGT() { return smgt; }
    
    /**
     * Classe per contenere i risultati dell'algoritmo SM-CPTD
     */
    public static class SMCPTDResult {
        public final double ccr;
        public final double slr;
        public final double makespan;
        public final int criticalPathLength;
        public final String workflowType;
        public final Map<Integer, List<Integer>> assignments;
        
        public SMCPTDResult(double ccr, double slr, double makespan, int criticalPathLength, 
                           String workflowType, Map<Integer, List<Integer>> assignments) {
            this.ccr = ccr;
            this.slr = slr;
            this.makespan = makespan;
            this.criticalPathLength = criticalPathLength;
            this.workflowType = workflowType;
            this.assignments = new HashMap<>(assignments);
        }
        
        @Override
        public String toString() {
            return String.format("SMCPTDResult{ccr=%.2f, slr=%.3f, makespan=%.3f, workflow=%s}", 
                               ccr, slr, makespan, workflowType);
        }
    }
}
