import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * Parser per file XML Pegasus DAX
 * Converte workflow CyberShake ed Epigenomics in formato CSV per SM-CPTD
 * 
 * IMPORTANTE: I valori numerici nei CSV generati (task sizes, VM capacities, bandwidth)
 * vengono IGNORATI da DataLoader, che genera valori con distribuzione uniforme:
 * - Task sizes: [500, 700] MIPS (uniform)
 * - VM capacities: [10, 20] MIPS (uniform)
 * - Bandwidth: [20, 30] Mbps (uniform)
 * 
 * I CSV sono usati SOLO per:
 * - Struttura del DAG (predecessori/successori)
 * - Numero e ID di task e VM
 * 
 * Input: file .xml Pegasus DAX
 * Output: task.csv, dag.csv, vm.csv, processing_capacity.csv, bandwidth.csv
 */
public class PegasusXMLParser {
    
    private static class Job {
        String id;
        String name;
        double runtime;
        List<String> inputFiles = new ArrayList<>();
        List<String> outputFiles = new ArrayList<>();
        long totalInputSize = 0;
        long totalOutputSize = 0;
    }
    
    public static void parseAndConvert(String xmlFile, String outputDir, int numVMs) throws Exception {
        System.out.println("📄 Parsing: " + xmlFile);
        
        // Parse XML
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.parse(new File(xmlFile));
        doc.getDocumentElement().normalize();
        
        // Estrai nome workflow
        String workflowName = new File(xmlFile).getName().replace(".xml", "");
        
        // Estrai job
        NodeList jobNodes = doc.getElementsByTagName("job");
        Map<String, Job> jobs = new HashMap<>();
        Map<String, Integer> jobIdToTaskId = new HashMap<>();
        int taskId = 1;
        
        for (int i = 0; i < jobNodes.getLength(); i++) {
            Element jobElement = (Element) jobNodes.item(i);
            Job job = new Job();
            job.id = jobElement.getAttribute("id");
            job.name = jobElement.getAttribute("name");
            
            String runtimeStr = jobElement.getAttribute("runtime");
            job.runtime = runtimeStr.isEmpty() ? 10.0 : Double.parseDouble(runtimeStr);
            
            // Estrai file uses
            NodeList usesNodes = jobElement.getElementsByTagName("uses");
            for (int j = 0; j < usesNodes.getLength(); j++) {
                Element useElement = (Element) usesNodes.item(j);
                String file = useElement.getAttribute("file");
                String link = useElement.getAttribute("link");
                String sizeStr = useElement.getAttribute("size");
                long size = sizeStr.isEmpty() ? 1000 : Long.parseLong(sizeStr);
                
                if ("input".equals(link)) {
                    job.inputFiles.add(file);
                    job.totalInputSize += size;
                } else if ("output".equals(link)) {
                    job.outputFiles.add(file);
                    job.totalOutputSize += size;
                }
            }
            
            jobs.put(job.id, job);
            jobIdToTaskId.put(job.id, taskId++);
        }
        
        System.out.println("  ✓ Jobs parsed: " + jobs.size());
        
        // Costruisci DAG dalle dipendenze implicite (output-input)
        Map<String, List<String>> fileProducers = new HashMap<>();
        Map<String, List<String>> fileConsumers = new HashMap<>();
        
        for (Job job : jobs.values()) {
            for (String outFile : job.outputFiles) {
                fileProducers.put(outFile, new ArrayList<>());
                fileProducers.get(outFile).add(job.id);
            }
            for (String inFile : job.inputFiles) {
                fileConsumers.putIfAbsent(inFile, new ArrayList<>());
                fileConsumers.get(inFile).add(job.id);
            }
        }
        
        // Estrai child dependencies (se presenti)
        List<int[]> edges = new ArrayList<>();
        NodeList childNodes = doc.getElementsByTagName("child");
        
        if (childNodes.getLength() > 0) {
            // Usa le dipendenze esplicite dal XML
            for (int i = 0; i < childNodes.getLength(); i++) {
                Element childElement = (Element) childNodes.item(i);
                String childId = childElement.getAttribute("ref");
                
                NodeList parentNodes = childElement.getElementsByTagName("parent");
                for (int j = 0; j < parentNodes.getLength(); j++) {
                    Element parentElement = (Element) parentNodes.item(j);
                    String parentId = parentElement.getAttribute("ref");
                    
                    Integer predTaskId = jobIdToTaskId.get(parentId);
                    Integer succTaskId = jobIdToTaskId.get(childId);
                    
                    if (predTaskId != null && succTaskId != null) {
                        edges.add(new int[]{predTaskId, succTaskId});
                    }
                }
            }
        } else {
            // Ricostruisci dipendenze da file input/output
            for (Map.Entry<String, List<String>> entry : fileProducers.entrySet()) {
                String file = entry.getKey();
                List<String> producers = entry.getValue();
                List<String> consumers = fileConsumers.getOrDefault(file, new ArrayList<>());
                
                for (String prodId : producers) {
                    for (String consId : consumers) {
                        Integer predTaskId = jobIdToTaskId.get(prodId);
                        Integer succTaskId = jobIdToTaskId.get(consId);
                        
                        if (predTaskId != null && succTaskId != null && !predTaskId.equals(succTaskId)) {
                            edges.add(new int[]{predTaskId, succTaskId});
                        }
                    }
                }
            }
        }
        
        System.out.println("  ✓ Edges found: " + edges.size());
        
        // Crea directory output
        new File(outputDir).mkdirs();
        
        // Scrivi task.csv
        // Paper: task sizes uniformly distributed in [500, 700]
        Random taskRand = SeededRandom.forScope("PegasusXMLParser.taskSizes:" + xmlFile + ":" + numVMs);
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputDir + "/task.csv"))) {
            writer.println("id,size");
            for (Map.Entry<String, Job> entry : jobs.entrySet()) {
                int id = jobIdToTaskId.get(entry.getKey());
                // Genera size secondo paper: [500, 700] uniforme
                double size = 500.0 + taskRand.nextDouble() * 200.0;
                writer.printf("%.0f,%.2f\n", (double)id, size);
            }
        }
        
        // Scrivi dag.csv con costi comunicazione basati su file size
        Set<String> edgeSet = new HashSet<>();
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputDir + "/dag.csv"))) {
            writer.println("pred,succ,data");
            
            // Rimuovi duplicati
            for (int[] edge : edges) {
                String edgeKey = edge[0] + "-" + edge[1];
                if (edgeSet.add(edgeKey)) {
                    // Calcola data transfer (usa media file size / 1MB come unità)
                    Job predJob = jobs.values().stream()
                        .filter(j -> jobIdToTaskId.get(j.id) == edge[0])
                        .findFirst().orElse(null);
                    
                    double dataSize = 1.0; // Default
                    if (predJob != null && predJob.totalOutputSize > 0) {
                        dataSize = predJob.totalOutputSize / 1000000.0; // MB
                    }
                    
                    writer.printf("%d,%d,%.2f\n", edge[0], edge[1], dataSize);
                }
            }
        }
        
        // Scrivi vm.csv
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputDir + "/vm.csv"))) {
            writer.println("id");
            for (int i = 0; i < numVMs; i++) {
                writer.println(i);
            }
        }
        
        // Scrivi processing_capacity.csv
        // VM processing capacities uniformly distributed in [10, 20]
        Random vmRand = SeededRandom.forScope("PegasusXMLParser.vmCapacity:" + xmlFile + ":" + numVMs);
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputDir + "/processing_capacity.csv"))) {
            writer.println("vm_id,processing_capacity");
            for (int i = 0; i < numVMs; i++) {
                double capacity = 10.0 + vmRand.nextDouble() * 10.0; // [10, 20]
                writer.printf(Locale.US, "%d,%.2f\n", i, capacity);
            }
        }
        
        // Scrivi bandwidth.csv
        // Paper: bandwidth between VMs uniformly distributed in [20, 30]
        Random bwRand = SeededRandom.forScope("PegasusXMLParser.bandwidth:" + xmlFile + ":" + numVMs);
        try (PrintWriter writer = new PrintWriter(new FileWriter(outputDir + "/bandwidth.csv"))) {
            writer.println("vm_i,vm_j,bandwidth");
            for (int i = 0; i < numVMs; i++) {
                for (int j = 0; j < numVMs; j++) {
                    if (i == j) {
                        // Bandwidth to self = 0 (no communication needed)
                        writer.printf("%d,%d,0.00\n", i, j);
                    } else {
                        // [20, 30] uniforme
                        double bandwidth = 20.0 + bwRand.nextDouble() * 10.0;
                        writer.printf("%d,%d,%.2f\n", i, j, bandwidth);
                    }
                }
            }
        }
        
        System.out.println("  ✓ Output saved to: " + outputDir);
        System.out.println("    - Tasks: " + jobs.size() + " (sizes: [500, 700] uniform)");
        System.out.println("    - Edges: " + edgeSet.size());
        System.out.println("    - VMs: " + numVMs + " (capacity: [10, 20] uniform, bandwidth: [20, 30] uniform)");
    }
    
    public static void main(String[] args) {
        System.out.println("════════════════════════════════════════════════════════════");
        System.out.println("   PEGASUS XML TO CSV CONVERTER");
        System.out.println("   All 4 workflows from the paper");
        System.out.println("════════════════════════════════════════════════════════════\n");
        
        try {
            // Parse CyberShake workflows
            String[] cyberShakeFiles = {
                "../workflow/cybershake/CyberShake_30.xml",
                "../workflow/cybershake/CyberShake_50.xml",
                "../workflow/cybershake/CyberShake_100.xml",
                "../workflow/cybershake/CyberShake_1000.xml"
            };
            
            System.out.println("🌊 CYBERSHAKE WORKFLOWS\n");
            for (String xmlFile : cyberShakeFiles) {
                if (new File(xmlFile).exists()) {
                    String name = new File(xmlFile).getName().replace(".xml", "");
                    String outputDir = "../data_pegasus_xml/" + name.toLowerCase();
                    int numVMs = name.contains("1000") ? 50 : name.contains("100") ? 10 : 5;
                    parseAndConvert(xmlFile, outputDir, numVMs);
                    System.out.println();
                }
            }
            
            // Parse Epigenomics workflows
            String[] epigenomicsFiles = {
                "../workflow/epigenomics/Epigenomics_47.xml",
                "../workflow/epigenomics/Epigenomics_100.xml",
                "../workflow/epigenomics/Epigenomics_997.xml"
            };
            
            System.out.println("🧬 EPIGENOMICS WORKFLOWS\n");
            for (String xmlFile : epigenomicsFiles) {
                if (new File(xmlFile).exists()) {
                    String name = new File(xmlFile).getName().replace(".xml", "");
                    String outputDir = "../data_pegasus_xml/" + name.toLowerCase();
                    int numVMs = name.contains("997") ? 50 : name.contains("100") ? 10 : 5;
                    parseAndConvert(xmlFile, outputDir, numVMs);
                    System.out.println();
                }
            }
            
            // Parse LIGO workflows
            String[] ligoFiles = {
                "../workflow/ligo/Ligo_50.xml",
                "../workflow/ligo/Ligo_100.xml",
                "../workflow/ligo/Ligo_1000.xml"
            };
            
            System.out.println("🌌 LIGO WORKFLOWS\n");
            for (String xmlFile : ligoFiles) {
                if (new File(xmlFile).exists()) {
                    String name = new File(xmlFile).getName().replace(".xml", "");
                    String outputDir = "../data_pegasus_xml/" + name.toLowerCase();
                    int numVMs = name.contains("1000") ? 50 : name.contains("100") ? 10 : 5;
                    parseAndConvert(xmlFile, outputDir, numVMs);
                    System.out.println();
                }
            }
            
            // Parse Montage workflows
            String[] montageFiles = {
                "../workflow/montage/Montage_50.xml",
                "../workflow/montage/Montage_100.xml",
                "../workflow/montage/Montage_1000.xml"
            };
            
            System.out.println("🔭 MONTAGE WORKFLOWS\n");
            for (String xmlFile : montageFiles) {
                if (new File(xmlFile).exists()) {
                    String name = new File(xmlFile).getName().replace(".xml", "");
                    String outputDir = "../data_pegasus_xml/" + name.toLowerCase();
                    int numVMs = name.contains("1000") ? 50 : name.contains("100") ? 10 : 5;
                    parseAndConvert(xmlFile, outputDir, numVMs);
                    System.out.println();
                }
            }
            
            System.out.println("════════════════════════════════════════════════════════════");
            System.out.println("✅ CONVERSION COMPLETED");
            System.out.println("════════════════════════════════════════════════════════════");
            System.out.println("Output directory: ../data_pegasus_xml/");
            System.out.println("\nWorkflows ready:");
            System.out.println("  - CyberShake: 50, 100, 1000 tasks");
            System.out.println("  - Epigenomics: 47, 100, 997 tasks");
            System.out.println("  - LIGO: 50, 100, 1000 tasks");
            System.out.println("  - Montage: 50, 100, 1000 tasks");
            System.out.println("\nTotal: 13 workflow configurations");
            System.out.println("\nNext: Run ExperimentRunner with these real Pegasus workflows!");
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
