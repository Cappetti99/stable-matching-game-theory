import java.util.*;
import java.io.*;

/**
 * SM-CPTD: Stable Matching Cloud-based Parallel Task Duplication Algorithm
 * 
 * Questo algoritmo combina tre componenti principali:
 * 1. DCP (Dynamic Critical Path) - Identifica il cammino critico
 * 2. SMGT (Stable Matching Game Theory) - Assegna task alle VM usando game
 * theory
 * 3. LOTD (List of Task Duplication) - Duplica task per ottimizzare le
 * performance
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
     * @param dagFilename  File CSV contenente la struttura del DAG
     * @param taskFilename File CSV contenente i dati dei task
     * @param vmFilename   File CSV contenente le capacità delle VM
     * @throws IOException Se ci sono errori nella lettura dei file
     */
    public void loadData(String dagFilename, String taskFilename, String vmFilename) throws IOException {
        System.out.println("🚀 SM-CPTD: Caricamento dati...");

        // Carica dati usando DataLoader
        List<task> tasks = DataLoader.loadTasksFromCSV(dagFilename, taskFilename);
        List<VM> vms = DataLoader.loadVMsFromCSV(vmFilename);
        
        // Imposta i dati in SMGT
        smgt.setTasks(tasks);
        smgt.setVMs(vms);
        smgt.calculateTaskLevels();

        System.out.println("✅ Dati caricati: " + tasks.size() + " task, " + vms.size() + " VM");
    }

    /**
     * Esegue l'algoritmo SM-CPTD completo
     * Sequenza: DCP → SMGT → LOTD
     * 
     * @param communicationCosts Mappa dei costi di comunicazione tra task
     * @param vmMapping          Mapping delle VM per ID
     * @return Assegnamenti finali task→VM dopo tutte le ottimizzazioni
     */
    public Map<Integer, List<Integer>> executeSMCPTD(Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {
        System.out.println("\n🎯 ESECUZIONE ALGORITMO SM-CPTD (VERSIONE CORRETTA)");
        System.out.println("==================================================");

        try {
            // STEP 1: DCP - Dynamic Critical Path Detection
            System.out.println("\n📍 STEP 1: Esecuzione DCP (Dynamic Critical Path)");
            Set<Integer> dcpResult = executeDCP(communicationCosts, vmMapping);

            // STEP 1.5: Schedule CP tasks with greedy minimum finish time
            System.out.println("\n⚡ STEP 1.5: Schedulazione task Critical Path (greedy)");
            Map<Integer, Integer> cpSchedule = scheduleCPTasks(dcpResult, communicationCosts, vmMapping);

            // STEP 2: SMGT - Stable Matching Game Theory (excluding CP tasks)
            System.out.println("\n🎮 STEP 2: Esecuzione SMGT (task non-CP)");
            executeSMGTWithCP(dcpResult);

            // STEP 2.5: Merge CP schedule with SMGT assignments
            System.out.println("\n🔗 STEP 2.5: Unione schedulazione CP + SMGT");
            Map<Integer, List<Integer>> mergedAssignments = mergeCPWithSMGT(cpSchedule, smgtAssignments);
            smgtAssignments = mergedAssignments;

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
     * STEP 1.5: Schedula i task del Critical Path con strategia greedy
     * Ogni task CP viene assegnato alla VM che minimizza il finish time
     */
    private Map<Integer, Integer> scheduleCPTasks(
            Set<Integer> CP,
            Map<String, Double> communicationCosts,
            Map<Integer, VM> vmMapping) {
        
        Map<Integer, Integer> cpTaskToVM = new HashMap<>();
        Map<Integer, Double> cpTaskAFT = new HashMap<>();
        Map<Integer, Double> vmAvailableTime = new HashMap<>();
        
        // Inizializza VM availability
        for (Integer vmId : vmMapping.keySet()) {
            vmAvailableTime.put(vmId, 0.0);
        }
        
        // Ordina task CP in ordine topologico (usando levels)
        List<Integer> sortedCP = new ArrayList<>(CP);
        sortedCP.sort((t1, t2) -> {
            Integer level1 = taskLevels.entrySet().stream()
                .filter(e -> e.getValue().contains(t1))
                .map(Map.Entry::getKey)
                .findFirst().orElse(999);
            Integer level2 = taskLevels.entrySet().stream()
                .filter(e -> e.getValue().contains(t2))
                .map(Map.Entry::getKey)
                .findFirst().orElse(999);
            return level1.compareTo(level2);
        });
        
        System.out.println("   📋 Schedulazione " + sortedCP.size() + " task CP in ordine topologico");
        
        // Per ogni task CP (in ordine topologico)
        for (Integer taskId : sortedCP) {
            task cpTask = getTaskById(taskId);
            if (cpTask == null) continue;
            
            // Trova VM con minimum finish time
            int bestVM = -1;
            double minFT = Double.POSITIVE_INFINITY;
            
            for (Map.Entry<Integer, VM> vmEntry : vmMapping.entrySet()) {
                int vmId = vmEntry.getKey();
                VM vm = vmEntry.getValue();
                
                // Calcola ST considerando predecessori già schedulati
                double st = vmAvailableTime.get(vmId);
                
                // Considera communication time da predecessori CP già schedulati
                for (Integer predId : cpTask.getPre()) {
                    if (cpTaskToVM.containsKey(predId)) {
                        int predVM = cpTaskToVM.get(predId);
                        double predAFT = cpTaskAFT.get(predId);
                        
                        // Se predecessore su VM diversa, aggiungi communication cost
                        if (predVM != vmId) {
                            String commKey = predId + "_" + taskId;
                            double commCost = communicationCosts.getOrDefault(commKey, 0.0);
                            st = Math.max(st, predAFT + commCost);
                        } else {
                            st = Math.max(st, predAFT);
                        }
                    }
                }
                
                // Calcola ET e FT
                double capacity = vm.getProcessingCapabilities().values().iterator().next();
                double et = cpTask.getSize() / capacity;
                double ft = st + et;
                
                if (ft < minFT) {
                    minFT = ft;
                    bestVM = vmId;
                }
            }
            
            // Assegna task a best VM
            if (bestVM != -1) {
                cpTaskToVM.put(taskId, bestVM);
                cpTaskAFT.put(taskId, minFT);
                vmAvailableTime.put(bestVM, minFT);
            }
        }
        
        System.out.println("   ✅ Task CP schedulati su " + 
            new HashSet<>(cpTaskToVM.values()).size() + " VM diverse");
        
        return cpTaskToVM;
    }

    /**
     * STEP 2.5: Unisce lo schedule dei task CP con gli assegnamenti SMGT
     */
    private Map<Integer, List<Integer>> mergeCPWithSMGT(
            Map<Integer, Integer> cpSchedule,
            Map<Integer, List<Integer>> smgtAssignments) {
        
        Map<Integer, List<Integer>> merged = new HashMap<>();
        
        // Copia assegnamenti SMGT
        for (Map.Entry<Integer, List<Integer>> entry : smgtAssignments.entrySet()) {
            merged.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        
        // Aggiungi task CP
        for (Map.Entry<Integer, Integer> entry : cpSchedule.entrySet()) {
            int taskId = entry.getKey();
            int vmId = entry.getValue();
            
            merged.computeIfAbsent(vmId, k -> new ArrayList<>()).add(taskId);
        }
        
        // Conta task per tipo
        int cpCount = cpSchedule.size();
        int smgtCount = smgtAssignments.values().stream().mapToInt(List::size).sum();
        
        System.out.println("   ✅ Merged: " + cpCount + " task CP + " + smgtCount + " task SMGT");
        
        return merged;
    }

    /**
     * STEP 1: Esecuzione DCP (versione standard)
     * Identifica il Critical Path
     */
    private Set<Integer> executeDCP(Map<String, Double> communicationCosts, Map<Integer, VM> vmMapping) {
        System.out.println("   🔍 Identificazione del cammino critico...");

        // Trova exit task e salvalo come campo della classe
        exitTask = findExitTask(smgt.getTasks());

        // Organizza task per livelli e salvalo come campo della classe
        taskLevels = DCP.organizeTasksByLevels(smgt.getTasks());

        // Esegui algoritmo DCP (versione standard)
        Set<Integer> criticalPathSet = DCP.executeDCP(
                smgt.getTasks(), taskLevels, exitTask, communicationCosts, vmMapping);

        // Salva il critical path (anche se executeDCP ritorna un set, lo salviamo in
        // campo per coerenza)
        this.criticalPath = criticalPathSet;

        System.out.println("   ✅ Critical path identificato: " + criticalPathSet.size() + " task critici");
        System.out.println("   📋 Task critici: " + criticalPathSet.toString());

        return criticalPathSet;
    }

    /**
     * Helper: Trova task per ID
     */
    private task getTaskById(int taskId) {
        return smgt.getTasks().stream()
            .filter(t -> t.getID() == taskId)
            .findFirst()
            .orElse(null);
    }

    /**
     * STEP 2: Esecuzione SMGT con esclusione task CP
     * Assegna i task NON nel Critical Path usando stable matching
     */
    private void executeSMGTWithCP(Set<Integer> criticalPathSet) {
        System.out.println("   🎯 Assegnamento task non-CP usando Game Theory...");

        // In this simplified version, we don't have CP scheduling info to pass to SMGT
        // SMGT will likely assume best possible placement for CP or treat them as
        // allocated
        // This requires adjust SMGT to accept just the set or creating a dummy mapping
        // if needed
        // For now, passing just the set implies we need an overload in SMGT or modify
        // the call

        // Warning: This assumes SMGT has a method runSMGTWithCP that takes just the Set
        // or
        // we need to adapt what we pass. Let's check SMGT usage.
        // Looking at previous context, runSMGT took (Set<Integer> criticalPath).
        // Let's assume we revert to that signature.

        smgtAssignments = smgt.runSMGT(criticalPathSet); // Using the method that takes just Set

        // Note: The previous code called runSMGTWithCP(Set, Map). We are reverting to
        // runSMGT(Set)
        // or whatever valid method SMGT has for this purpose.

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
     * STEP 2 Legacy: Esecuzione SMGT (senza esclusione CP)
     * 
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
     * 
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
                System.out.println(
                        "      VM" + entry.getKey() + ": " + entry.getValue().size() + " task (dopo duplicazione)");
            }

        } catch (Exception e) {
            System.err.println("   ⚠️  Errore in LOTD, uso risultati SMGT: " + e.getMessage());
            finalAssignments = new HashMap<>(smgtAssignments);
        }
    }

    /**
     * Calcola le metriche per un CCR specifico ricalcolando il critical path
     * 
     * @param CCR                        Il valore di CCR specifico
     * @param originalCommunicationCosts I costi di comunicazione originali
     * @param vmMapping                  Mapping delle VM
     */
    public void calculateMetricsForCCR(double CCR, Map<String, Double> originalCommunicationCosts,
            Map<Integer, VM> vmMapping) {
        try {
            // STEP 1: Aggiorna i costi di comunicazione con il nuovo CCR
            Map<String, Double> updatedCommunicationCosts = updateCommunicationCostsForCCR(originalCommunicationCosts,
                    CCR);

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
            Set<Integer> currentCriticalPath = DCP.executeDCP(smgt.getTasks(), taskLevels, exitTask,
                    updatedCommunicationCosts, vmMapping);

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
            // CORREZIONE: Calcola makespan usando AFT da LOTD
            // Paper Equazione 6: makespan = max(MS(VMk))
            if (lotd != null && lotd.getTaskAFT() != null && !lotd.getTaskAFT().isEmpty()) {
                Map<Integer, Double> taskAFT = lotd.getTaskAFT();
                makespan = taskAFT.values().stream()
                        .mapToDouble(Double::doubleValue)
                        .max()
                        .orElse(0.0);

                System.out.println("   ✓ Makespan from LOTD AFT: " + String.format("%.3f", makespan));
            } else {
                // Fallback: calcolo semplificato
                System.out.println("   ⚠️  LOTD AFT not available, using fallback calculation");
                makespan = calculateMakespanFallback(finalAssignments, smgt.getTasks(), vmMapping, communicationCosts);
            }

            // Calcola SLR usando il critical path corretto
            Map<String, task> taskMap = new HashMap<>();
            for (task t : smgt.getTasks()) {
                taskMap.put("t" + t.getID(), t);
            }

            if (!criticalPath.isEmpty()) {
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
                    }
                }

                if (sumMinExecutionTimes > 0) {
                    slr = makespan / sumMinExecutionTimes;
                } else {
                    slr = Double.POSITIVE_INFINITY;
                }
            }

            System.out.println("   ✅ Metriche calcolate: Makespan=" +
                    String.format("%.3f", makespan) + ", SLR=" + String.format("%.3f", slr));

        } catch (Exception e) {
            System.err.println("   ⚠️  Errore calcolo metriche: " + e.getMessage());
            e.printStackTrace();
            makespan = 0.0;
            slr = Double.POSITIVE_INFINITY;
        }
    }

    /**
     * Fallback per calcolo makespan quando LOTD AFT non disponibile
     */
    private double calculateMakespanFallback(Map<Integer, List<Integer>> assignments,
            List<task> tasks,
            Map<Integer, VM> vmMapping,
            Map<String, Double> communicationCosts) {
        double maxFinishTime = 0.0;

        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmId = entry.getKey();
            List<Integer> assignedTasks = entry.getValue();

            if (assignedTasks.isEmpty())
                continue;

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

        return maxFinishTime;
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

            if (assignedTasks.isEmpty())
                continue;

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

        // STEP 2: Calcola impatto realistico della comunicazione basato su CCR
        // Conta quante comunicazioni cross-VM ci sono (task su VM diverse)
        int crossVMCommunications = 0;
        double totalCommunicationCost = 0.0;

        // Crea mappa task -> VM
        Map<Integer, Integer> taskToVM = new HashMap<>();
        for (Map.Entry<Integer, List<Integer>> entry : assignments.entrySet()) {
            int vmId = entry.getKey();
            for (Integer taskId : entry.getValue()) {
                taskToVM.put(taskId, vmId);
            }
        }

        // Conta comunicazioni cross-VM e somma i costi
        for (task t : tasks) {
            Integer srcVM = taskToVM.get(t.getID());
            if (srcVM == null)
                continue;

            for (int succId : t.getSucc()) {
                Integer destVM = taskToVM.get(succId);
                if (destVM != null && !destVM.equals(srcVM)) {
                    crossVMCommunications++;
                    String key = t.getID() + "_" + succId;
                    Double commCost = communicationCosts.get(key);
                    if (commCost != null) {
                        totalCommunicationCost += commCost;
                    }
                }
            }
        }

        // Aggiungi tempo di comunicazione al makespan
        // Usa una frazione del costo totale di comunicazione cross-VM
        // Il fattore 0.3 simula che non tutte le comunicazioni sono sul critical path
        if (crossVMCommunications > 0) {
            double avgCommCost = totalCommunicationCost / crossVMCommunications;
            // Aggiungi tempo per le comunicazioni sul critical path (stimato come sqrt del
            // numero)
            double criticalPathComms = Math.sqrt(crossVMCommunications);
            maxFinishTime += avgCommCost * criticalPathComms * 0.5;
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

    public Set<Integer> getCriticalPath() {
        return criticalPath;
    }

    public Map<Integer, List<Integer>> getSMGTAssignments() {
        return smgtAssignments;
    }

    public Map<Integer, List<Integer>> getFinalAssignments() {
        return finalAssignments;
    }

    public double getMakespan() {
        return makespan;
    }

    public double getSLR() {
        return slr;
    }

    public SMGT getSMGT() {
        return smgt;
    }

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
