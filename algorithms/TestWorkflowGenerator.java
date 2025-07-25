/**
 * Test standalone per il generatore DAG Java
 */
public class TestWorkflowGenerator {
    
    public static void main(String[] args) {
        System.out.println("🧪 TEST GENERATORE WORKFLOW JAVA");
        System.out.println("=================================");
        
        // Test tutti i tipi di workflow
        WorkflowDAGGenerator.WorkflowType[] types = {
            WorkflowDAGGenerator.WorkflowType.CYBERSHAKE,
            WorkflowDAGGenerator.WorkflowType.EPIGENOMICS,
            WorkflowDAGGenerator.WorkflowType.LIGO,
            WorkflowDAGGenerator.WorkflowType.MONTAGE
        };
        
        for (WorkflowDAGGenerator.WorkflowType type : types) {
            System.out.println("\n🔧 Testing " + type + "...");
            
            try {
                // Crea configurazione
                WorkflowDAGGenerator.WorkflowConfig config = 
                    new WorkflowDAGGenerator.WorkflowConfig(type, 50, 5);
                
                // Genera workflow
                WorkflowDAGGenerator generator = new WorkflowDAGGenerator(config);
                WorkflowDAGGenerator.WorkflowData workflow = generator.generateWorkflow();
                
                // Verifica risultati
                System.out.println("   ✅ Task generati: " + workflow.tasks.size());
                System.out.println("   ✅ VM configurate: " + workflow.vms.size());
                
                // Verifica struttura DAG
                int dependencies = 0;
                for (WorkflowDAGGenerator.TaskInfo task : workflow.tasks) {
                    dependencies += task.successors.size();
                }
                System.out.println("   ✅ Dipendenze: " + dependencies);
                
                // Test export CSV
                String outputDir = "../data_test_" + type.toString().toLowerCase();
                java.io.File dir = new java.io.File(outputDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                
                workflow.exportToCSV(outputDir);
                System.out.println("   ✅ CSV esportati in: " + outputDir);
                
            } catch (Exception e) {
                System.err.println("   ❌ Errore: " + e.getMessage());
                e.printStackTrace();
            }
        }
        
        System.out.println("\n🎉 Test completato!");
    }
}
