import java.io.*;
import java.util.*;
import java.text.SimpleDateFormat;

/**
 * CCRAnalyzer - Analyzes CCR (Communication-to-Computation Ratio) sensitivity
 * 
 * Tracks how different CCR values affect:
 * 1. Communication costs between tasks
 * 2. Critical Path composition
 * 3. Task duplication decisions (LOTD)
 * 4. Overall performance metrics (SLR, AVU, VF)
 * 
 * Output: JSON report for visualization and analysis
 * 
 * @author Lorenzo Cappetti
 * @version 1.0
 */
public class CCRAnalyzer {
    
    private String workflowName;
    private int numTasks;
    private int numVMs;
    private List<CCRSnapshot> snapshots;
    private String experimentType; // EXP1_SMALL, EXP1_MEDIUM, etc.
    
    /**
     * Constructor
     */
    public CCRAnalyzer(String workflowName, int numTasks, int numVMs, String experimentType) {
        this.workflowName = workflowName;
        this.numTasks = numTasks;
        this.numVMs = numVMs;
        this.experimentType = experimentType;
        this.snapshots = new ArrayList<>();
    }
    
    /**
     * Captures a snapshot of one CCR experiment run
     */
    public void captureSnapshot(
            double ccr,
            Map<String, Double> communicationCosts,
            Set<Integer> criticalPath,
            Map<Integer, Set<Integer>> duplicatedTasks,
            double slr,
            double avu,
            double vf,
            double makespan) {
        
        CCRSnapshot snapshot = new CCRSnapshot(
            ccr,
            communicationCosts,
            criticalPath,
            duplicatedTasks,
            slr,
            avu,
            vf,
            makespan
        );
        
        snapshots.add(snapshot);
    }
    
    /**
     * Generates sensitivity analysis report
     */
    public SensitivityReport generateReport() {
        if (snapshots.isEmpty()) {
            System.out.println("⚠️  No snapshots to analyze");
            return null;
        }
        
        // Sort by CCR
        snapshots.sort(Comparator.comparingDouble(s -> s.ccr));
        
        SensitivityReport report = new SensitivityReport();
        report.workflowName = workflowName;
        report.numTasks = numTasks;
        report.numVMs = numVMs;
        report.experimentType = experimentType;
        report.numCCRValues = snapshots.size();
        
        // Communication cost analysis
        report.commCostAnalysis = analyzeCommCosts();
        
        // Critical path stability
        report.cpStability = analyzeCriticalPathStability();
        
        // Duplication sensitivity
        report.duplicationSensitivity = analyzeDuplicationSensitivity();
        
        // Metrics elasticity
        report.metricsElasticity = analyzeMetricsElasticity();
        
        return report;
    }
    
    /**
     * Analyzes how communication costs change with CCR
     */
    private CommCostAnalysis analyzeCommCosts() {
        CommCostAnalysis analysis = new CommCostAnalysis();
        
        for (CCRSnapshot snapshot : snapshots) {
            CommCostStats stats = new CommCostStats();
            stats.ccr = snapshot.ccr;
            
            if (snapshot.communicationCosts != null && !snapshot.communicationCosts.isEmpty()) {
                Collection<Double> costs = snapshot.communicationCosts.values();
                
                stats.minCost = costs.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                stats.maxCost = costs.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                stats.meanCost = costs.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                stats.totalCost = costs.stream().mapToDouble(Double::doubleValue).sum();
                stats.numEdges = costs.size();
                
                // Calculate median
                List<Double> sortedCosts = new ArrayList<>(costs);
                Collections.sort(sortedCosts);
                int mid = sortedCosts.size() / 2;
                stats.medianCost = sortedCosts.size() % 2 == 0 
                    ? (sortedCosts.get(mid - 1) + sortedCosts.get(mid)) / 2.0
                    : sortedCosts.get(mid);
            }
            
            analysis.ccrStats.add(stats);
        }
        
        // Calculate overall change
        if (analysis.ccrStats.size() >= 2) {
            CommCostStats first = analysis.ccrStats.get(0);
            CommCostStats last = analysis.ccrStats.get(analysis.ccrStats.size() - 1);
            
            analysis.totalCostIncrease = last.totalCost - first.totalCost;
            analysis.totalCostIncreasePercent = (analysis.totalCostIncrease / first.totalCost) * 100.0;
            analysis.meanCostIncrease = last.meanCost - first.meanCost;
        }
        
        return analysis;
    }
    
    /**
     * Analyzes Critical Path stability across CCR values
     */
    private CPStabilityAnalysis analyzeCriticalPathStability() {
        CPStabilityAnalysis analysis = new CPStabilityAnalysis();
        
        // Track which tasks appear in CP at each CCR
        for (CCRSnapshot snapshot : snapshots) {
            CPSnapshot cpSnap = new CPSnapshot();
            cpSnap.ccr = snapshot.ccr;
            cpSnap.tasks = new ArrayList<>(snapshot.criticalPath);
            Collections.sort(cpSnap.tasks);
            cpSnap.length = cpSnap.tasks.size();
            
            analysis.cpSnapshots.add(cpSnap);
        }
        
        // Calculate stability: % of tasks that remain in CP across all CCR values
        if (!snapshots.isEmpty()) {
            Set<Integer> allCPTasks = new HashSet<>();
            Set<Integer> alwaysCPTasks = new HashSet<>(snapshots.get(0).criticalPath);
            
            for (CCRSnapshot snapshot : snapshots) {
                allCPTasks.addAll(snapshot.criticalPath);
                alwaysCPTasks.retainAll(snapshot.criticalPath);
            }
            
            analysis.totalUniqueCPTasks = allCPTasks.size();
            analysis.alwaysInCPTasks = alwaysCPTasks.size();
            analysis.stabilityScore = allCPTasks.isEmpty() ? 0.0 
                : (double) alwaysCPTasks.size() / allCPTasks.size();
            
            // Count CP changes
            for (int i = 1; i < snapshots.size(); i++) {
                Set<Integer> prev = snapshots.get(i - 1).criticalPath;
                Set<Integer> curr = snapshots.get(i).criticalPath;
                
                if (!prev.equals(curr)) {
                    analysis.cpChanges++;
                }
            }
        }
        
        return analysis;
    }
    
    /**
     * Analyzes task duplication sensitivity to CCR
     */
    private DuplicationAnalysis analyzeDuplicationSensitivity() {
        DuplicationAnalysis analysis = new DuplicationAnalysis();
        
        for (CCRSnapshot snapshot : snapshots) {
            DuplicationSnapshot dupSnap = new DuplicationSnapshot();
            dupSnap.ccr = snapshot.ccr;
            
            // Count total duplications
            if (snapshot.duplicatedTasks != null) {
                for (Set<Integer> dups : snapshot.duplicatedTasks.values()) {
                    dupSnap.totalDuplications += dups.size();
                }
                
                dupSnap.vmsWithDuplications = (int) snapshot.duplicatedTasks.values().stream()
                    .filter(set -> !set.isEmpty())
                    .count();
            }
            
            analysis.duplicationSnapshots.add(dupSnap);
        }
        
        // Calculate correlation between CCR and duplication count
        if (analysis.duplicationSnapshots.size() >= 2) {
            DuplicationSnapshot first = analysis.duplicationSnapshots.get(0);
            DuplicationSnapshot last = analysis.duplicationSnapshots.get(analysis.duplicationSnapshots.size() - 1);
            
            analysis.duplicationIncrease = last.totalDuplications - first.totalDuplications;
            
            if (first.totalDuplications > 0) {
                analysis.duplicationIncreasePercent = 
                    (double) analysis.duplicationIncrease / first.totalDuplications * 100.0;
            }
            
            // Simple correlation: if duplications increase monotonically with CCR
            boolean isMonotonic = true;
            for (int i = 1; i < analysis.duplicationSnapshots.size(); i++) {
                if (analysis.duplicationSnapshots.get(i).totalDuplications < 
                    analysis.duplicationSnapshots.get(i - 1).totalDuplications) {
                    isMonotonic = false;
                    break;
                }
            }
            
            analysis.isMonotonicIncrease = isMonotonic;
            analysis.correlationStrength = isMonotonic ? "strong" : "weak";
        }
        
        return analysis;
    }
    
    /**
     * Analyzes elasticity of metrics (SLR, AVU, VF) with respect to CCR
     */
    private MetricsElasticity analyzeMetricsElasticity() {
        MetricsElasticity elasticity = new MetricsElasticity();
        
        if (snapshots.size() >= 2) {
            CCRSnapshot first = snapshots.get(0);
            CCRSnapshot last = snapshots.get(snapshots.size() - 1);
            
            // SLR elasticity
            elasticity.slrChange = last.slr - first.slr;
            elasticity.slrPercentChange = (elasticity.slrChange / first.slr) * 100.0;
            
            // AVU elasticity
            elasticity.avuChange = last.avu - first.avu;
            elasticity.avuPercentChange = first.avu > 0 
                ? (elasticity.avuChange / first.avu) * 100.0 : 0.0;
            
            // VF elasticity
            elasticity.vfChange = last.vf - first.vf;
            elasticity.vfPercentChange = first.vf > 0 
                ? (elasticity.vfChange / first.vf) * 100.0 : 0.0;
            
            // Makespan elasticity
            elasticity.makespanChange = last.makespan - first.makespan;
            elasticity.makespanPercentChange = (elasticity.makespanChange / first.makespan) * 100.0;
            
            // Calculate elasticity coefficients (% change in metric / % change in CCR)
            double ccrPercentChange = ((last.ccr - first.ccr) / first.ccr) * 100.0;
            
            if (ccrPercentChange != 0) {
                elasticity.slrElasticityCoeff = elasticity.slrPercentChange / ccrPercentChange;
                elasticity.avuElasticityCoeff = elasticity.avuPercentChange / ccrPercentChange;
                elasticity.makespanElasticityCoeff = elasticity.makespanPercentChange / ccrPercentChange;
            }
            
            // Classify sensitivity
            double avgElasticity = Math.abs(elasticity.slrElasticityCoeff);
            if (avgElasticity < 0.01) {
                elasticity.sensitivityClass = "insensitive";
            } else if (avgElasticity < 0.1) {
                elasticity.sensitivityClass = "low";
            } else if (avgElasticity < 0.5) {
                elasticity.sensitivityClass = "medium";
            } else {
                elasticity.sensitivityClass = "high";
            }
        }
        
        return elasticity;
    }
    
    /**
     * Saves report to JSON file
     */
    public void saveToJSON(String outputPath) {
        SensitivityReport report = generateReport();
        
        if (report == null) {
            System.out.println("⚠️  Cannot save report: no data");
            return;
        }
        
        try {
            File file = new File(outputPath);
            file.getParentFile().mkdirs();
            
            PrintWriter writer = new PrintWriter(file);
            writer.println("{");
            writer.println("  \"workflow\": \"" + workflowName + "\",");
            writer.println("  \"experiment_type\": \"" + experimentType + "\",");
            writer.println("  \"num_tasks\": " + numTasks + ",");
            writer.println("  \"num_vms\": " + numVMs + ",");
            writer.println("  \"num_ccr_values\": " + snapshots.size() + ",");
            writer.println("  \"analysis_date\": \"" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + "\",");
            
            // Communication costs
            writer.println("  \"communication_costs\": {");
            writer.println("    \"stats_per_ccr\": [");
            for (int i = 0; i < report.commCostAnalysis.ccrStats.size(); i++) {
                CommCostStats stats = report.commCostAnalysis.ccrStats.get(i);
                writer.print("      {");
                writer.printf(Locale.US, "\"ccr\": %.1f, ", stats.ccr);
                writer.printf(Locale.US, "\"min\": %.6f, ", stats.minCost);
                writer.printf(Locale.US, "\"max\": %.6f, ", stats.maxCost);
                writer.printf(Locale.US, "\"mean\": %.6f, ", stats.meanCost);
                writer.printf(Locale.US, "\"median\": %.6f, ", stats.medianCost);
                writer.printf(Locale.US, "\"total\": %.6f, ", stats.totalCost);
                writer.printf("\"num_edges\": %d", stats.numEdges);
                writer.print("}");
                if (i < report.commCostAnalysis.ccrStats.size() - 1) writer.print(",");
                writer.println();
            }
            writer.println("    ],");
            writer.printf(Locale.US, "    \"total_cost_increase\": %.6f,%n", report.commCostAnalysis.totalCostIncrease);
            writer.printf(Locale.US, "    \"total_cost_increase_percent\": %.2f,%n", report.commCostAnalysis.totalCostIncreasePercent);
            writer.printf(Locale.US, "    \"mean_cost_increase\": %.6f%n", report.commCostAnalysis.meanCostIncrease);
            writer.println("  },");
            
            // Critical Path stability
            writer.println("  \"critical_path_stability\": {");
            writer.println("    \"cp_per_ccr\": [");
            for (int i = 0; i < report.cpStability.cpSnapshots.size(); i++) {
                CPSnapshot cp = report.cpStability.cpSnapshots.get(i);
                writer.print("      {");
                writer.printf(Locale.US, "\"ccr\": %.1f, ", cp.ccr);
                writer.printf("\"length\": %d, ", cp.length);
                writer.print("\"tasks\": [");
                for (int j = 0; j < cp.tasks.size(); j++) {
                    writer.print(cp.tasks.get(j));
                    if (j < cp.tasks.size() - 1) writer.print(", ");
                }
                writer.print("]}");
                if (i < report.cpStability.cpSnapshots.size() - 1) writer.print(",");
                writer.println();
            }
            writer.println("    ],");
            writer.printf("    \"total_unique_cp_tasks\": %d,%n", report.cpStability.totalUniqueCPTasks);
            writer.printf("    \"always_in_cp_tasks\": %d,%n", report.cpStability.alwaysInCPTasks);
            writer.printf(Locale.US, "    \"stability_score\": %.4f,%n", report.cpStability.stabilityScore);
            writer.printf("    \"cp_changes\": %d%n", report.cpStability.cpChanges);
            writer.println("  },");
            
            // Duplication analysis
            writer.println("  \"duplication_analysis\": {");
            writer.println("    \"duplications_per_ccr\": [");
            for (int i = 0; i < report.duplicationSensitivity.duplicationSnapshots.size(); i++) {
                DuplicationSnapshot dup = report.duplicationSensitivity.duplicationSnapshots.get(i);
                writer.print("      {");
                writer.printf(Locale.US, "\"ccr\": %.1f, ", dup.ccr);
                writer.printf("\"total_duplications\": %d, ", dup.totalDuplications);
                writer.printf("\"vms_with_dups\": %d", dup.vmsWithDuplications);
                writer.print("}");
                if (i < report.duplicationSensitivity.duplicationSnapshots.size() - 1) writer.print(",");
                writer.println();
            }
            writer.println("    ],");
            writer.printf("    \"duplication_increase\": %d,%n", report.duplicationSensitivity.duplicationIncrease);
            writer.printf(Locale.US, "    \"duplication_increase_percent\": %.2f,%n", 
                report.duplicationSensitivity.duplicationIncreasePercent);
            writer.printf("    \"is_monotonic_increase\": %b,%n", report.duplicationSensitivity.isMonotonicIncrease);
            writer.printf("    \"correlation_strength\": \"%s\"%n", report.duplicationSensitivity.correlationStrength);
            writer.println("  },");
            
            // Metrics elasticity
            writer.println("  \"metrics_elasticity\": {");
            writer.println("    \"slr\": {");
            writer.printf(Locale.US, "      \"change\": %.6f,%n", report.metricsElasticity.slrChange);
            writer.printf(Locale.US, "      \"percent_change\": %.2f,%n", report.metricsElasticity.slrPercentChange);
            writer.printf(Locale.US, "      \"elasticity_coeff\": %.4f%n", report.metricsElasticity.slrElasticityCoeff);
            writer.println("    },");
            writer.println("    \"avu\": {");
            writer.printf(Locale.US, "      \"change\": %.6f,%n", report.metricsElasticity.avuChange);
            writer.printf(Locale.US, "      \"percent_change\": %.2f,%n", report.metricsElasticity.avuPercentChange);
            writer.printf(Locale.US, "      \"elasticity_coeff\": %.4f%n", report.metricsElasticity.avuElasticityCoeff);
            writer.println("    },");
            writer.println("    \"vf\": {");
            writer.printf(Locale.US, "      \"change\": %.6f,%n", report.metricsElasticity.vfChange);
            writer.printf(Locale.US, "      \"percent_change\": %.2f%n", report.metricsElasticity.vfPercentChange);
            writer.println("    },");
            writer.println("    \"makespan\": {");
            writer.printf(Locale.US, "      \"change\": %.6f,%n", report.metricsElasticity.makespanChange);
            writer.printf(Locale.US, "      \"percent_change\": %.2f,%n", report.metricsElasticity.makespanPercentChange);
            writer.printf(Locale.US, "      \"elasticity_coeff\": %.4f%n", report.metricsElasticity.makespanElasticityCoeff);
            writer.println("    },");
            writer.printf("    \"sensitivity_class\": \"%s\"%n", report.metricsElasticity.sensitivityClass);
            writer.println("  }");
            
            writer.println("}");
            writer.close();
            
            System.out.println("✅ CCR analysis saved: " + outputPath);
            
        } catch (IOException e) {
            System.err.println("❌ Error saving CCR analysis: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // ============================================================================
    // DATA STRUCTURES
    // ============================================================================
    
    /**
     * Snapshot of one CCR experiment run
     */
    private static class CCRSnapshot {
        double ccr;
        Map<String, Double> communicationCosts;
        Set<Integer> criticalPath;
        Map<Integer, Set<Integer>> duplicatedTasks;
        double slr;
        double avu;
        double vf;
        double makespan;
        
        CCRSnapshot(double ccr, Map<String, Double> communicationCosts, Set<Integer> criticalPath,
                    Map<Integer, Set<Integer>> duplicatedTasks, double slr, double avu, double vf, double makespan) {
            this.ccr = ccr;
            this.communicationCosts = communicationCosts;
            this.criticalPath = new HashSet<>(criticalPath);
            this.duplicatedTasks = new HashMap<>();
            if (duplicatedTasks != null) {
                for (Map.Entry<Integer, Set<Integer>> entry : duplicatedTasks.entrySet()) {
                    this.duplicatedTasks.put(entry.getKey(), new HashSet<>(entry.getValue()));
                }
            }
            this.slr = slr;
            this.avu = avu;
            this.vf = vf;
            this.makespan = makespan;
        }
    }
    
    /**
     * Complete sensitivity report
     */
    private static class SensitivityReport {
        String workflowName;
        String experimentType;
        int numTasks;
        int numVMs;
        int numCCRValues;
        CommCostAnalysis commCostAnalysis;
        CPStabilityAnalysis cpStability;
        DuplicationAnalysis duplicationSensitivity;
        MetricsElasticity metricsElasticity;
    }
    
    /**
     * Communication cost analysis
     */
    private static class CommCostAnalysis {
        List<CommCostStats> ccrStats = new ArrayList<>();
        double totalCostIncrease;
        double totalCostIncreasePercent;
        double meanCostIncrease;
    }
    
    private static class CommCostStats {
        double ccr;
        double minCost;
        double maxCost;
        double meanCost;
        double medianCost;
        double totalCost;
        int numEdges;
    }
    
    /**
     * Critical Path stability analysis
     */
    private static class CPStabilityAnalysis {
        List<CPSnapshot> cpSnapshots = new ArrayList<>();
        int totalUniqueCPTasks;
        int alwaysInCPTasks;
        double stabilityScore; // 0.0 to 1.0
        int cpChanges;
    }
    
    private static class CPSnapshot {
        double ccr;
        List<Integer> tasks;
        int length;
    }
    
    /**
     * Duplication sensitivity analysis
     */
    private static class DuplicationAnalysis {
        List<DuplicationSnapshot> duplicationSnapshots = new ArrayList<>();
        int duplicationIncrease;
        double duplicationIncreasePercent;
        boolean isMonotonicIncrease;
        String correlationStrength;
    }
    
    private static class DuplicationSnapshot {
        double ccr;
        int totalDuplications;
        int vmsWithDuplications;
    }
    
    /**
     * Metrics elasticity analysis
     */
    private static class MetricsElasticity {
        double slrChange;
        double slrPercentChange;
        double slrElasticityCoeff;
        
        double avuChange;
        double avuPercentChange;
        double avuElasticityCoeff;
        
        double vfChange;
        double vfPercentChange;
        
        double makespanChange;
        double makespanPercentChange;
        double makespanElasticityCoeff;
        
        String sensitivityClass; // "insensitive", "low", "medium", "high"
    }
}
