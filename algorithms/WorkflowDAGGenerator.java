import java.io.*;
import java.util.*;
import java.util.Locale;

/**
 * Generator per workflow DAG scientifici in Java
 * Sostituisce i generatori Python con API Java nativa
 */
public class WorkflowDAGGenerator {
    
    // Workflow types supported
    public enum WorkflowType {
        CYBERSHAKE, EPIGENOMICS, LIGO, MONTAGE
    }
    
    // Configuration per la generazione
    public static class WorkflowConfig {
        public WorkflowType type;
        public int numTasks;
        public int numVMs;
        public double minTaskSize;
        public double maxTaskSize;
        public double minVMCapacity;
        public double maxVMCapacity;
        public double minBandwidth;
        public double maxBandwidth;
        
        public WorkflowConfig(WorkflowType type, int numTasks, int numVMs) {
            this.type = type;
            this.numTasks = numTasks;
            this.numVMs = numVMs;
            // Default values conformi al progetto
            this.minTaskSize = 2.0;
            this.maxTaskSize = 25.0;
            this.minVMCapacity = 10.0;
            this.maxVMCapacity = 20.0;
            this.minBandwidth = 20.0;
            this.maxBandwidth = 30.0;
        }
    }
    
    // Task structure per l'export
    public static class TaskInfo {
        public int id;
        public double size;
        public List<Integer> predecessors;
        public List<Integer> successors;
        
        public TaskInfo(int id, double size) {
            this.id = id;
            this.size = size;
            this.predecessors = new ArrayList<>();
            this.successors = new ArrayList<>();
        }
    }
    
    // VM configuration structure
    public static class VMInfo {
        public int id;
        public double processingCapacity;
        public Map<Integer, Double> bandwidthMatrix;
        
        public VMInfo(int id, double capacity) {
            this.id = id;
            this.processingCapacity = capacity;
            this.bandwidthMatrix = new HashMap<>();
        }
    }
    
    private Random random;
    private WorkflowConfig config;
    
    public WorkflowDAGGenerator(WorkflowConfig config) {
        this.config = config;
        this.random = new Random(42); // Seed fisso per riproducibilità
    }
    
    /**
     * Genera workflow completo secondo il tipo specificato
     */
    public WorkflowData generateWorkflow() {
        System.out.println("🔧 Generando workflow " + config.type + " con " + config.numTasks + " task e " + config.numVMs + " VM");
        
        switch (config.type) {
            case CYBERSHAKE:
                return generateCyberShakeWorkflow();
            case EPIGENOMICS:
                return generateEpigenomicsWorkflow();
            case LIGO:
                return generateLIGOWorkflow();
            case MONTAGE:
                return generateMontageWorkflow();
            default:
                throw new IllegalArgumentException("Tipo workflow non supportato: " + config.type);
        }
    }
    
    /**
     * Genera workflow CyberShake (seismic hazard analysis)
     * Struttura: PreCVM → GenCVM → GenSGT → PSA → ZipPSA → PostProcess
     */
    private WorkflowData generateCyberShakeWorkflow() {
        List<TaskInfo> tasks = new ArrayList<>();
        List<VMInfo> vms = generateVMs();
        
        // Calcola parametri per ottenere esattamente numTasks
        int sites = calculateOptimalSites();
        int sgtVariations = 2;
        int psaFilters = calculateOptimalPSAFilters(sites);
        
        System.out.println("🌊 CyberShake: " + sites + " siti, " + sgtVariations + " SGT, " + psaFilters + " PSA");
        
        int taskId = 1;
        
        // 1. PreCVM (preprocessing)
        TaskInfo preCvm = new TaskInfo(taskId++, randomTaskSize());
        tasks.add(preCvm);
        
        // 2. GenCVM jobs (one per site)
        List<TaskInfo> genCvmTasks = new ArrayList<>();
        for (int site = 0; site < sites; site++) {
            TaskInfo genCvm = new TaskInfo(taskId++, randomTaskSize());
            genCvm.predecessors.add(preCvm.id);
            preCvm.successors.add(genCvm.id);
            genCvmTasks.add(genCvm);
            tasks.add(genCvm);
        }
        
        // 3. GenSGT jobs (sgtVariations per site)
        List<TaskInfo> sgtTasks = new ArrayList<>();
        for (TaskInfo genCvm : genCvmTasks) {
            for (int v = 0; v < sgtVariations; v++) {
                TaskInfo sgt = new TaskInfo(taskId++, randomTaskSize());
                sgt.predecessors.add(genCvm.id);
                genCvm.successors.add(sgt.id);
                sgtTasks.add(sgt);
                tasks.add(sgt);
            }
        }
        
        // 4. PSA jobs (psaFilters per SGT)
        List<TaskInfo> psaTasks = new ArrayList<>();
        for (TaskInfo sgt : sgtTasks) {
            for (int p = 0; p < psaFilters; p++) {
                TaskInfo psa = new TaskInfo(taskId++, randomTaskSize());
                psa.predecessors.add(sgt.id);
                sgt.successors.add(psa.id);
                psaTasks.add(psa);
                tasks.add(psa);
            }
        }
        
        // 5. ZipPSA jobs (one per site)
        List<TaskInfo> zipTasks = new ArrayList<>();
        for (int site = 0; site < sites; site++) {
            TaskInfo zip = new TaskInfo(taskId++, randomTaskSize());
            // Connect to all PSA tasks from this site
            for (int i = site * sgtVariations * psaFilters; 
                 i < (site + 1) * sgtVariations * psaFilters && i < psaTasks.size(); i++) {
                TaskInfo psa = psaTasks.get(i);
                zip.predecessors.add(psa.id);
                psa.successors.add(zip.id);
            }
            zipTasks.add(zip);
            tasks.add(zip);
        }
        
        // 6. PostProcess (final aggregation)
        TaskInfo postProcess = new TaskInfo(taskId++, randomTaskSize());
        for (TaskInfo zip : zipTasks) {
            postProcess.predecessors.add(zip.id);
            zip.successors.add(postProcess.id);
        }
        tasks.add(postProcess);
        
        System.out.println("✅ CyberShake generato: " + tasks.size() + " task");
        return new WorkflowData(tasks, vms, config.type);
    }
    
    /**
     * Genera workflow Epigenomics (genomic analysis)
     * Struttura: FastQC → Trimming → Alignment → Sorting → Dedup → PeakCalling → Annotation → Analysis
     */
    private WorkflowData generateEpigenomicsWorkflow() {
        List<TaskInfo> tasks = new ArrayList<>();
        List<VMInfo> vms = generateVMs();
        
        // Calcola parametri per target numTasks
        int samples = calculateOptimalSamples();
        int chromosomes = 22; // Standard human chromosomes
        int analysisTypes = 3;
        
        System.out.println("🧬 Epigenomics: " + samples + " campioni, " + chromosomes + " cromosomi, " + analysisTypes + " analisi");
        
        int taskId = 1;
        
        // 1. FastQC jobs (per sample)
        List<TaskInfo> fastqcTasks = new ArrayList<>();
        for (int s = 0; s < samples; s++) {
            TaskInfo fastqc = new TaskInfo(taskId++, randomTaskSize());
            fastqcTasks.add(fastqc);
            tasks.add(fastqc);
        }
        
        // 2. Trimming jobs (per sample)
        List<TaskInfo> trimmingTasks = new ArrayList<>();
        for (int s = 0; s < samples; s++) {
            TaskInfo trimming = new TaskInfo(taskId++, randomTaskSize());
            trimming.predecessors.add(fastqcTasks.get(s).id);
            fastqcTasks.get(s).successors.add(trimming.id);
            trimmingTasks.add(trimming);
            tasks.add(trimming);
        }
        
        // 3. Alignment jobs (per sample)
        List<TaskInfo> alignmentTasks = new ArrayList<>();
        for (int s = 0; s < samples; s++) {
            TaskInfo alignment = new TaskInfo(taskId++, randomTaskSize());
            alignment.predecessors.add(trimmingTasks.get(s).id);
            trimmingTasks.get(s).successors.add(alignment.id);
            alignmentTasks.add(alignment);
            tasks.add(alignment);
        }
        
        // 4. Sorting jobs (per sample)
        List<TaskInfo> sortingTasks = new ArrayList<>();
        for (int s = 0; s < samples; s++) {
            TaskInfo sorting = new TaskInfo(taskId++, randomTaskSize());
            sorting.predecessors.add(alignmentTasks.get(s).id);
            alignmentTasks.get(s).successors.add(sorting.id);
            sortingTasks.add(sorting);
            tasks.add(sorting);
        }
        
        // 5. Deduplication jobs (per sample)
        List<TaskInfo> dedupTasks = new ArrayList<>();
        for (int s = 0; s < samples; s++) {
            TaskInfo dedup = new TaskInfo(taskId++, randomTaskSize());
            dedup.predecessors.add(sortingTasks.get(s).id);
            sortingTasks.get(s).successors.add(dedup.id);
            dedupTasks.add(dedup);
            tasks.add(dedup);
        }
        
        // 6. Peak Calling jobs (per sample per analysis type)
        List<TaskInfo> peakTasks = new ArrayList<>();
        for (int s = 0; s < samples; s++) {
            for (int a = 0; a < analysisTypes; a++) {
                TaskInfo peak = new TaskInfo(taskId++, randomTaskSize());
                peak.predecessors.add(dedupTasks.get(s).id);
                dedupTasks.get(s).successors.add(peak.id);
                peakTasks.add(peak);
                tasks.add(peak);
            }
        }
        
        // 7. Annotation jobs (per sample)
        List<TaskInfo> annotationTasks = new ArrayList<>();
        for (int s = 0; s < samples; s++) {
            TaskInfo annotation = new TaskInfo(taskId++, randomTaskSize());
            // Connect to all peak calling tasks for this sample
            for (int a = 0; a < analysisTypes; a++) {
                int peakIndex = s * analysisTypes + a;
                if (peakIndex < peakTasks.size()) {
                    annotation.predecessors.add(peakTasks.get(peakIndex).id);
                    peakTasks.get(peakIndex).successors.add(annotation.id);
                }
            }
            annotationTasks.add(annotation);
            tasks.add(annotation);
        }
        
        // 8. Differential Analysis (combines all annotations)
        TaskInfo diffAnalysis = new TaskInfo(taskId++, randomTaskSize());
        for (TaskInfo annotation : annotationTasks) {
            diffAnalysis.predecessors.add(annotation.id);
            annotation.successors.add(diffAnalysis.id);
        }
        tasks.add(diffAnalysis);
        
        // 9. Visualization jobs (per analysis type)
        List<TaskInfo> vizTasks = new ArrayList<>();
        for (int a = 0; a < analysisTypes; a++) {
            TaskInfo viz = new TaskInfo(taskId++, randomTaskSize());
            viz.predecessors.add(diffAnalysis.id);
            diffAnalysis.successors.add(viz.id);
            vizTasks.add(viz);
            tasks.add(viz);
        }
        
        // 10. Final Report
        TaskInfo finalReport = new TaskInfo(taskId++, randomTaskSize());
        for (TaskInfo viz : vizTasks) {
            finalReport.predecessors.add(viz.id);
            viz.successors.add(finalReport.id);
        }
        tasks.add(finalReport);
        
        System.out.println("✅ Epigenomics generato: " + tasks.size() + " task");
        return new WorkflowData(tasks, vms, config.type);
    }
    
    /**
     * Genera workflow LIGO (gravitational wave detection)
     * Struttura complessa con multiple pipeline di analisi
     */
    private WorkflowData generateLIGOWorkflow() {
        List<TaskInfo> tasks = new ArrayList<>();
        List<VMInfo> vms = generateVMs();
        
        // Calcola parametri per target numTasks
        int detectors = 3; // Hanford, Livingston, Virgo
        int segments = calculateOptimalSegments();
        int templates = 5;
        
        System.out.println("🌌 LIGO: " + detectors + " detector, " + segments + " segmenti, " + templates + " template");
        
        int taskId = 1;
        
        // 1. Data conditioning (per detector)
        List<TaskInfo> conditioningTasks = new ArrayList<>();
        for (int d = 0; d < detectors; d++) {
            TaskInfo conditioning = new TaskInfo(taskId++, randomTaskSize());
            conditioningTasks.add(conditioning);
            tasks.add(conditioning);
        }
        
        // 2. Segmentation (per detector per segment)
        List<TaskInfo> segmentTasks = new ArrayList<>();
        for (int d = 0; d < detectors; d++) {
            for (int s = 0; s < segments; s++) {
                TaskInfo segment = new TaskInfo(taskId++, randomTaskSize());
                segment.predecessors.add(conditioningTasks.get(d).id);
                conditioningTasks.get(d).successors.add(segment.id);
                segmentTasks.add(segment);
                tasks.add(segment);
            }
        }
        
        // 3. Template matching (per segment per template)
        List<TaskInfo> matchingTasks = new ArrayList<>();
        for (TaskInfo segment : segmentTasks) {
            for (int t = 0; t < templates; t++) {
                TaskInfo matching = new TaskInfo(taskId++, randomTaskSize());
                matching.predecessors.add(segment.id);
                segment.successors.add(matching.id);
                matchingTasks.add(matching);
                tasks.add(matching);
            }
        }
        
        // 4. Coincidence detection (per detector combination)
        List<TaskInfo> coincidenceTasks = new ArrayList<>();
        int combinations = detectors * (detectors - 1) / 2; // Combinations of detector pairs
        for (int c = 0; c < combinations; c++) {
            TaskInfo coincidence = new TaskInfo(taskId++, randomTaskSize());
            // Connect to subset of matching tasks
            int startIdx = c * matchingTasks.size() / combinations;
            int endIdx = (c + 1) * matchingTasks.size() / combinations;
            for (int i = startIdx; i < endIdx && i < matchingTasks.size(); i++) {
                coincidence.predecessors.add(matchingTasks.get(i).id);
                matchingTasks.get(i).successors.add(coincidence.id);
            }
            coincidenceTasks.add(coincidence);
            tasks.add(coincidence);
        }
        
        // 5. Parameter estimation
        List<TaskInfo> paramTasks = new ArrayList<>();
        for (TaskInfo coincidence : coincidenceTasks) {
            TaskInfo param = new TaskInfo(taskId++, randomTaskSize());
            param.predecessors.add(coincidence.id);
            coincidence.successors.add(param.id);
            paramTasks.add(param);
            tasks.add(param);
        }
        
        // 6. Final analysis
        TaskInfo finalAnalysis = new TaskInfo(taskId++, randomTaskSize());
        for (TaskInfo param : paramTasks) {
            finalAnalysis.predecessors.add(param.id);
            param.successors.add(finalAnalysis.id);
        }
        tasks.add(finalAnalysis);
        
        System.out.println("✅ LIGO generato: " + tasks.size() + " task");
        return new WorkflowData(tasks, vms, config.type);
    }
    
    /**
     * Genera workflow Montage (astronomical image processing)
     * Struttura: mProject → mDiffFit → mConcatFit → mBgModel → mBackground → mAdd → mJPEG
     */
    private WorkflowData generateMontageWorkflow() {
        List<TaskInfo> tasks = new ArrayList<>();
        List<VMInfo> vms = generateVMs();
        
        // Calcola numero di immagini per target numTasks
        int images = calculateOptimalImages();
        
        System.out.println("🔭 Montage: " + images + " immagini astronomiche");
        
        int taskId = 1;
        
        // 1. mProject jobs (per image)
        List<TaskInfo> projectTasks = new ArrayList<>();
        for (int i = 0; i < images; i++) {
            TaskInfo project = new TaskInfo(taskId++, randomTaskSize());
            projectTasks.add(project);
            tasks.add(project);
        }
        
        // 2. mDiffFit jobs (per image pair)
        List<TaskInfo> diffFitTasks = new ArrayList<>();
        for (int i = 0; i < images - 1; i++) {
            TaskInfo diffFit = new TaskInfo(taskId++, randomTaskSize());
            diffFit.predecessors.add(projectTasks.get(i).id);
            diffFit.predecessors.add(projectTasks.get(i + 1).id);
            projectTasks.get(i).successors.add(diffFit.id);
            projectTasks.get(i + 1).successors.add(diffFit.id);
            diffFitTasks.add(diffFit);
            tasks.add(diffFit);
        }
        
        // 3. mConcatFit (combines all diffs)
        TaskInfo concatFit = new TaskInfo(taskId++, randomTaskSize());
        for (TaskInfo diffFit : diffFitTasks) {
            concatFit.predecessors.add(diffFit.id);
            diffFit.successors.add(concatFit.id);
        }
        tasks.add(concatFit);
        
        // 4. mBgModel jobs (per image)
        List<TaskInfo> bgModelTasks = new ArrayList<>();
        for (int i = 0; i < images; i++) {
            TaskInfo bgModel = new TaskInfo(taskId++, randomTaskSize());
            bgModel.predecessors.add(concatFit.id);
            concatFit.successors.add(bgModel.id);
            bgModelTasks.add(bgModel);
            tasks.add(bgModel);
        }
        
        // 5. mBackground jobs (per image)
        List<TaskInfo> backgroundTasks = new ArrayList<>();
        for (int i = 0; i < images; i++) {
            TaskInfo background = new TaskInfo(taskId++, randomTaskSize());
            background.predecessors.add(projectTasks.get(i).id);
            background.predecessors.add(bgModelTasks.get(i).id);
            projectTasks.get(i).successors.add(background.id);
            bgModelTasks.get(i).successors.add(background.id);
            backgroundTasks.add(background);
            tasks.add(background);
        }
        
        // 6. mAdd (final mosaic)
        TaskInfo mAdd = new TaskInfo(taskId++, randomTaskSize());
        for (TaskInfo background : backgroundTasks) {
            mAdd.predecessors.add(background.id);
            background.successors.add(mAdd.id);
        }
        tasks.add(mAdd);
        
        // 7. mJPEG (visualization)
        TaskInfo mJpeg = new TaskInfo(taskId++, randomTaskSize());
        mJpeg.predecessors.add(mAdd.id);
        mAdd.successors.add(mJpeg.id);
        tasks.add(mJpeg);
        
        System.out.println("✅ Montage generato: " + tasks.size() + " task");
        return new WorkflowData(tasks, vms, config.type);
    }
    
    // Helper methods per il calcolo dei parametri ottimali
    private int calculateOptimalSites() {
        // Per CyberShake: 1 + sites + sites*2 + sites*2*4 + sites + 1 = numTasks
        // Risolvi per sites
        for (int sites = 1; sites <= 20; sites++) {
            int total = 1 + sites + sites * 2 + sites * 2 * 4 + sites + 1;
            if (total >= config.numTasks - 5 && total <= config.numTasks + 5) {
                return sites;
            }
        }
        return 6; // Default sicuro
    }
    
    private int calculateOptimalPSAFilters(int sites) {
        // Ottimizza PSA filters per arrivare al target
        return 4; // Default che funziona bene
    }
    
    private int calculateOptimalSamples() {
        // Per Epigenomics: calcola campioni per target numTasks
        for (int samples = 1; samples <= 10; samples++) {
            int total = samples * 7 + 1 + 3 + 1; // FastQC+Trim+Align+Sort+Dedup+Peak*3+Anno + DiffAnal + Viz*3 + Report
            if (total >= config.numTasks - 5 && total <= config.numTasks + 5) {
                return samples;
            }
        }
        return 2; // Default sicuro
    }
    
    private int calculateOptimalSegments() {
        // Per LIGO: calcola segmenti per target numTasks
        return Math.max(1, config.numTasks / 20); // Approx scaling
    }
    
    private int calculateOptimalImages() {
        // Per Montage: calcola immagini per target numTasks
        for (int images = 5; images <= 50; images++) {
            int total = images + (images - 1) + 1 + images + images + 1 + 1;
            if (total >= config.numTasks - 3 && total <= config.numTasks + 3) {
                return images;
            }
        }
        return 25; // Default sicuro
    }
    
    // Helper methods
    private double randomTaskSize() {
        return config.minTaskSize + random.nextDouble() * (config.maxTaskSize - config.minTaskSize);
    }
    
    private double randomVMCapacity() {
        return config.minVMCapacity + random.nextDouble() * (config.maxVMCapacity - config.minVMCapacity);
    }
    
    private double randomBandwidth() {
        return config.minBandwidth + random.nextDouble() * (config.maxBandwidth - config.minBandwidth);
    }
    
    /**
     * Genera configurazione VM standardizzata
     */
    private List<VMInfo> generateVMs() {
        List<VMInfo> vms = new ArrayList<>();
        
        for (int i = 0; i < config.numVMs; i++) {
            VMInfo vm = new VMInfo(i, randomVMCapacity());
            
            // Genera matrice bandwidth
            for (int j = 0; j < config.numVMs; j++) {
                if (i == j) {
                    vm.bandwidthMatrix.put(j, Double.MAX_VALUE); // Bandwidth infinita per stesso VM
                } else {
                    vm.bandwidthMatrix.put(j, randomBandwidth());
                }
            }
            
            vms.add(vm);
        }
        
        return vms;
    }
    
    /**
     * Container per dati del workflow generato
     */
    public static class WorkflowData {
        public List<TaskInfo> tasks;
        public List<VMInfo> vms;
        public WorkflowType type;
        
        public WorkflowData(List<TaskInfo> tasks, List<VMInfo> vms, WorkflowType type) {
            this.tasks = tasks;
            this.vms = vms;
            this.type = type;
        }
        
        public void exportToCSV(String outputDir) throws IOException {
            exportTasksCSV(outputDir + "/task.csv");
            exportDAGCSV(outputDir + "/dag.csv");
            exportVMsCSV(outputDir + "/vm.csv");
            exportProcessingCapacityCSV(outputDir + "/processing_capacity.csv");
        }
        
        private void exportTasksCSV(String filename) throws IOException {
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                writer.println("# Task list for " + type + " workflow (format: t{ID} size)");
                for (TaskInfo task : tasks) {
                    writer.printf(Locale.US, "t%d %.1f%n", task.id, task.size);
                }
            }
        }
        
        private void exportDAGCSV(String filename) throws IOException {
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                writer.println("# DAG dependencies for " + type + " workflow (format: t{from} t{to})");
                for (TaskInfo task : tasks) {
                    for (Integer successor : task.successors) {
                        writer.printf("t%d t%d%n", task.id, successor);
                    }
                }
            }
        }
        
        private void exportVMsCSV(String filename) throws IOException {
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                // Header
                writer.print("VM");
                for (int i = 0; i < vms.size(); i++) {
                    writer.print(",vm" + i);
                }
                writer.println();
                
                // Bandwidth matrix
                for (int i = 0; i < vms.size(); i++) {
                    writer.print("vm" + i);
                    VMInfo vm = vms.get(i);
                    for (int j = 0; j < vms.size(); j++) {
                        double bandwidth = vm.bandwidthMatrix.get(j);
                        if (bandwidth == Double.MAX_VALUE) {
                            writer.print(",1000"); // High bandwidth for same VM
                        } else {
                            writer.printf(Locale.US, ",%.1f", bandwidth);
                        }
                    }
                    writer.println();
                }
            }
        }
        
        private void exportProcessingCapacityCSV(String filename) throws IOException {
            try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
                writer.println("# VM processing capacities for " + type + " workflow");
                for (VMInfo vm : vms) {
                    writer.printf(Locale.US, "vm%d %.1f%n", vm.id, vm.processingCapacity);
                }
            }
        }
    }
}
