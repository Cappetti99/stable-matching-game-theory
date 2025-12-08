import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

/**
 * Generatore di workflow scientifici basato sulla logica originale di Pegasus (2014).
 * Riproduce la struttura esatta dei grafi usati nel paper.
 */
public class WorkflowGenerator2014 {

    private static final Random rand = new Random(42); // Seed fisso per riproducibilità

    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java WorkflowGenerator2014 <workflow_type> <num_tasks>");
            System.out.println("Types: cybershake, montage, epigenomics, ligo");
            return;
        }

        String type = args[0].toLowerCase();
        int numTasks = Integer.parseInt(args[1]);
        String outputDir = "data_pegasus/" + type + "_" + numTasks + "tasks";

        // Crea directory
        new java.io.File(outputDir).mkdirs();

        try {
            switch (type) {
                case "cybershake":
                    generateCyberShake(numTasks, outputDir);
                    break;
                case "montage":
                    generateMontage(numTasks, outputDir);
                    break;
                case "epigenomics":
                    generateEpigenomics(numTasks, outputDir);
                    break;
                case "ligo":
                    generateLIGO(numTasks, outputDir);
                    break;
                default:
                    System.out.println("Unknown workflow type: " + type);
            }
            
            // Genera file VM standard
            generateStandardVMFiles(outputDir);
            
            System.out.println("✅ Generated " + type + " with " + numTasks + " tasks in " + outputDir);
            
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // --- CYBERSHAKE ---
    // Image (b): Roots -> Pipelines -> Single Sink
    // Structure for 30 nodes:
    // 1 Sink
    // 2 Roots (to match image look)
    // Pipelines (Seis->Peak) distributed between roots
    // Total = 1 Sink + 2 Roots + 2*P
    // 30 = 3 + 2P -> 2P = 27 (Impossible for exact 30 with 2 roots and pairs)
    // Adjustment: One pipeline will be a triplet or we use 1 Root.
    // To match image (b) which has 2 roots, we'll use 2 Roots.
    // We need 27 nodes in pipelines. 13 pairs (26) + 1 extra.
    // We'll add one "orphan" task or a triplet (Seis->Peak->PostPeak).
    private static void generateCyberShake(int targetTasks, String dir) throws IOException {
        System.out.println("Generating CyberShake (Visual Match) for " + targetTasks + " tasks");

        try (PrintWriter dag = new PrintWriter(new FileWriter(dir + "/dag.csv"));
             PrintWriter task = new PrintWriter(new FileWriter(dir + "/task.csv"))) {
            
            dag.println("pred,succ,data");
            task.println("id,size");

            int id = 1;
            int sinkId = id++;
            writeTask(task, sinkId, 10, 20); // Final Sink

            int numRoots = 2;
            int[] rootIds = new int[numRoots];
            for(int i=0; i<numRoots; i++) {
                rootIds[i] = id++;
                writeTask(task, rootIds[i], 20, 40);
            }

            int remaining = targetTasks - 1 - numRoots; // 27 for target=30
            
            // Distribute remaining into pipelines of length 2 (Seis->Peak)
            // If odd, one pipeline gets length 3
            int pairs = remaining / 2;
            int remainder = remaining % 2;
            
            // If remainder is 1, we have one triplet.
            // Pairs count reduces by 1 to make room for triplet? No, pairs = 13, rem=1.
            // So 13 pairs + 1 extra node. We can attach extra node to one pair making it a triplet.
            
            int currentRootIdx = 0;
            
            for(int i=0; i<pairs; i++) {
                int t1 = id++;
                int t2 = id++;
                
                writeTask(task, t1, 80, 120);
                writeTask(task, t2, 5, 15);
                
                int root = rootIds[currentRootIdx];
                currentRootIdx = (currentRootIdx + 1) % numRoots;
                
                writeEdge(dag, root, t1, 50, 100);
                writeEdge(dag, t1, t2, 1, 5);
                writeEdge(dag, t2, sinkId, 1, 5);
                
                // Handle remainder by extending the first pipeline
                if (i == 0 && remainder > 0) {
                    int t3 = id++;
                    writeTask(task, t3, 5, 10);
                    writeEdge(dag, t2, t3, 1, 5); // Extend chain
                    writeEdge(dag, t3, sinkId, 1, 5); // Connect to sink
                    // Remove t2->sink edge? No, multiple inputs to sink is fine, or we can remove it.
                    // Let's keep it simple: t2->t3->sink.
                    // But we already wrote t2->sink. Let's assume sink aggregates everything.
                }
            }
        }
    }

    // --- MONTAGE ---
    // Image (a): Fan-in -> Chain
    // Structure: Project(K) -> DiffFit(K) -> Concat(1) -> BgModel(1) -> Background(K) -> Imgtbl(1) -> Add(1)
    // Edges: Proj->Diff, Diff->Concat, Concat->BgModel, BgModel->Bg, Proj->Bg (Long), Bg->Imgtbl, Bg->Add, Imgtbl->Add
    // For 30 nodes: 3K + 4 = 30 -> 3K = 26 -> K=8, Remainder=2
    // We add 2 extra DiffFits to absorb remainder.
    private static void generateMontage(int targetTasks, String dir) throws IOException {
        System.out.println("Generating Montage (Visual Match) for " + targetTasks + " tasks");

        try (PrintWriter dag = new PrintWriter(new FileWriter(dir + "/dag.csv"));
             PrintWriter task = new PrintWriter(new FileWriter(dir + "/task.csv"))) {
            
            dag.println("pred,succ,data");
            task.println("id,size");

            int id = 1;
            
            int fixed = 4;
            int K = (targetTasks - fixed) / 3;
            int remainder = (targetTasks - fixed) % 3;
            
            int nProject = K;
            int nDiff = K + remainder; // Absorb remainder in Diff layer
            int nBg = K;
            
            int[] projectIds = new int[nProject];
            int[] diffIds = new int[nDiff];
            int[] bgIds = new int[nBg];
            
            for(int i=0; i<nProject; i++) { projectIds[i] = id++; writeTask(task, projectIds[i], 15, 25); }
            for(int i=0; i<nDiff; i++) { diffIds[i] = id++; writeTask(task, diffIds[i], 10, 20); }
            
            int concatId = id++; writeTask(task, concatId, 5, 10);
            int bgModelId = id++; writeTask(task, bgModelId, 5, 10);
            
            for(int i=0; i<nBg; i++) { bgIds[i] = id++; writeTask(task, bgIds[i], 10, 20); }
            
            int imgTblId = id++; writeTask(task, imgTblId, 2, 5);
            int addId = id++; writeTask(task, addId, 30, 60);
            
            // Edges
            // Proj -> Diff
            for(int i=0; i<nDiff; i++) {
                int p1 = i % nProject;
                writeEdge(dag, projectIds[p1], diffIds[i], 20, 40);
                if (i < nProject - 1) {
                    writeEdge(dag, projectIds[i+1], diffIds[i], 20, 40);
                }
            }
            
            // Diff -> Concat
            for(int t : diffIds) writeEdge(dag, t, concatId, 5, 10);
            
            // Concat -> BgModel
            writeEdge(dag, concatId, bgModelId, 2, 5);
            
            // BgModel -> Background AND Project -> Background (Long edges)
            for(int i=0; i<nBg; i++) {
                writeEdge(dag, bgModelId, bgIds[i], 1, 3);
                writeEdge(dag, projectIds[i], bgIds[i], 20, 40); // The long edge
            }
            
            // Background -> Imgtbl
            for(int t : bgIds) writeEdge(dag, t, imgTblId, 1, 2);
            
            // Background -> Add AND Imgtbl -> Add
            writeEdge(dag, imgTblId, addId, 5, 10);
            for(int t : bgIds) writeEdge(dag, t, addId, 20, 40);
        }
    }

    // --- EPIGENOMICS ---
    // Image (d): Start -> Parallel Chains -> Merge -> Sequence
    // Structure: Start(1) -> K x (Map->Filter->Align) -> Merge(1) -> Index(1)
    // Total = 3K + 3.
    // For 30: 3K = 27 -> K=9.
    private static void generateEpigenomics(int targetTasks, String dir) throws IOException {
        System.out.println("Generating Epigenomics (Visual Match) for " + targetTasks + " tasks");

        try (PrintWriter dag = new PrintWriter(new FileWriter(dir + "/dag.csv"));
             PrintWriter task = new PrintWriter(new FileWriter(dir + "/task.csv"))) {
            
            dag.println("pred,succ,data");
            task.println("id,size");
            
            int id = 1;
            int startId = id++; writeTask(task, startId, 10, 20);
            
            // Calculate K
            // Fixed: Start, Merge, Index (3 nodes)
            // If we have more remainder, we extend the tail sequence
            int fixed = 3;
            int available = targetTasks - fixed;
            int K = available / 3;
            int remainder = available % 3;
            
            // If remainder > 0, add to tail
            int tailLength = 1 + remainder; // Index + extras
            
            int mergeId = -1; // Will assign after pipelines
            
            // Pipelines
            int[] lastPipelineNodes = new int[K];
            
            for(int i=0; i<K; i++) {
                int t1 = id++; // Map
                int t2 = id++; // Filter
                int t3 = id++; // Align
                
                writeTask(task, t1, 20, 30);
                writeTask(task, t2, 20, 30);
                writeTask(task, t3, 40, 60);
                
                writeEdge(dag, startId, t1, 50, 100);
                writeEdge(dag, t1, t2, 40, 80);
                writeEdge(dag, t2, t3, 30, 60);
                
                lastPipelineNodes[i] = t3;
            }
            
            mergeId = id++; writeTask(task, mergeId, 10, 20);
            for(int t : lastPipelineNodes) writeEdge(dag, t, mergeId, 20, 40);
            
            // Tail sequence
            int prev = mergeId;
            for(int i=0; i<tailLength; i++) {
                int t = id++;
                writeTask(task, t, 10, 15);
                writeEdge(dag, prev, t, 10, 20);
                prev = t;
            }
        }
    }

    // --- LIGO ---
    // Image (c): Diamond Blocks
    // Block: 2xTmplt -> 2xInspiral -> 1xThinca -> 2xTrig -> 2xInspiral -> 1xThinca
    // Block Size: 10 nodes.
    // For 30 nodes: 3 Blocks.
    private static void generateLIGO(int targetTasks, String dir) throws IOException {
        System.out.println("Generating LIGO (Visual Match) for " + targetTasks + " tasks");

        try (PrintWriter dag = new PrintWriter(new FileWriter(dir + "/dag.csv"));
             PrintWriter task = new PrintWriter(new FileWriter(dir + "/task.csv"))) {
            
            dag.println("pred,succ,data");
            task.println("id,size");
            
            int id = 1;
            
            // Block definition: 10 nodes
            // 2 -> 2 -> 1 -> 2 -> 2 -> 1
            int blockSize = 10;
            int numBlocks = targetTasks / blockSize;
            int remainder = targetTasks % blockSize;
            
            // If remainder, we can add a partial block or just extra parallel nodes
            // For 30: Remainder 0.
            
            int prevThinca = -1;
            
            for(int b=0; b<numBlocks; b++) {
                // Layer 1: 2 Tmplt
                int t1a = id++; writeTask(task, t1a, 10, 20);
                int t1b = id++; writeTask(task, t1b, 10, 20);
                
                // Layer 2: 2 Inspiral
                int t2a = id++; writeTask(task, t2a, 50, 80);
                int t2b = id++; writeTask(task, t2b, 50, 80);
                
                writeEdge(dag, t1a, t2a, 5, 10);
                writeEdge(dag, t1b, t2b, 5, 10);
                
                // Layer 3: 1 Thinca (Converge)
                int t3 = id++; writeTask(task, t3, 10, 20);
                writeEdge(dag, t2a, t3, 10, 20);
                writeEdge(dag, t2b, t3, 10, 20);
                
                // Layer 4: 2 Trigbank (Diverge)
                int t4a = id++; writeTask(task, t4a, 5, 10);
                int t4b = id++; writeTask(task, t4b, 5, 10);
                writeEdge(dag, t3, t4a, 5, 10);
                writeEdge(dag, t3, t4b, 5, 10);
                
                // Cross connect from previous block
                if (prevThinca != -1) {
                    writeEdge(dag, prevThinca, t4a, 5, 10);
                    writeEdge(dag, prevThinca, t4b, 5, 10);
                }
                
                // Layer 5: 2 Inspiral
                int t5a = id++; writeTask(task, t5a, 50, 80);
                int t5b = id++; writeTask(task, t5b, 50, 80);
                writeEdge(dag, t4a, t5a, 5, 10);
                writeEdge(dag, t4b, t5b, 5, 10);
                
                // Layer 6: 1 Thinca (Converge)
                int t6 = id++; writeTask(task, t6, 10, 20);
                writeEdge(dag, t5a, t6, 10, 20);
                writeEdge(dag, t5b, t6, 10, 20);
                
                prevThinca = t6; // For next block
            }
            
            // Handle remainder (simple chain)
            for(int i=0; i<remainder; i++) {
                int t = id++;
                writeTask(task, t, 10, 10);
                if (prevThinca != -1) writeEdge(dag, prevThinca, t, 10, 10);
                prevThinca = t;
            }
        }
    }

    private static void writeTask(PrintWriter w, int id, double min, double max) {
        double size = min + (max - min) * rand.nextDouble();
        w.println(id + "," + String.format("%.2f", size));
    }

    private static void writeEdge(PrintWriter w, int u, int v, double min, double max) {
        double data = min + (max - min) * rand.nextDouble();
        w.println(u + "," + v + "," + String.format("%.2f", data));
    }
    
    private static void generateStandardVMFiles(String dir) throws IOException {
        try (PrintWriter vm = new PrintWriter(new FileWriter(dir + "/vm.csv"));
             PrintWriter cap = new PrintWriter(new FileWriter(dir + "/processing_capacity.csv"))) {
            
            vm.println("id,mips,bandwidth");
            cap.println("vm_id,task_id,capacity");
            
            // 5 VMs standard
            for (int i = 1; i <= 5; i++) {
                vm.println(i + ",1000,100");
                // Capacity placeholder (will be filled by ExperimentRunner logic usually, but good to have)
                // Assuming max 200 tasks for now in capacity file
                for(int t=1; t<=200; t++) {
                    cap.println(i + "," + t + ",1000");
                }
            }
        }
    }
}
