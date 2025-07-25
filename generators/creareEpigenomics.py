#!/usr/bin/env python3

"""
Generatore di Workflow Epigenomics personalizzabile
Crea un workflow Epigenomics per analisi bioinformatiche con numero di campioni configurabile dall'utente

Utilizzo:
    python3 creareEpigenomics.py --samples 10
    python3 creareEpigenomics.py --samples 25 --chromosomes 22
"""

import os
import sys
import argparse
import random
import math

# Ottieni il path della cartella data
def get_data_dir():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    return os.path.join(project_root, "data")

def create_epigenomics_workflow(num_samples=10, num_chromosomes=22, analysis_types=3):
    """
    Crea un workflow Epigenomics per analisi bioinformatiche
    
    Parametri:
    - num_samples: numero di campioni biologici da analizzare
    - num_chromosomes: numero di cromosomi da processare (1-22 + X,Y)
    - analysis_types: tipi di analisi epigenetiche (ChIP-seq, ATAC-seq, etc.)
    
    Struttura del workflow:
    1. FastQC: controllo qualità dei dati raw
    2. Trimming: rimozione di sequenze adattatori e bassa qualità
    3. Alignment: allineamento al genoma di riferimento
    4. Sorting: ordinamento file BAM
    5. Deduplication: rimozione duplicati PCR
    6. PeakCalling: identificazione picchi di arricchimento
    7. Annotation: annotazione funzionale dei picchi
    8. DifferentialAnalysis: analisi differenziale tra condizioni
    9. Visualization: generazione grafici e tracce
    10. FinalReport: report finale integrato
    """
    
    print(f"🧬 CREAZIONE WORKFLOW EPIGENOMICS PER STABLE MATCHING")
    print("=" * 55)
    print(f"Numero di campioni: {num_samples}")
    print(f"Cromosomi da analizzare: {num_chromosomes}")
    print(f"Tipi di analisi: {analysis_types}")
    
    # Calcola il numero totale di job
    fastqc_jobs = num_samples * 2  # R1 e R2 per paired-end
    trimming_jobs = num_samples  # 1 trimming per campione
    alignment_jobs = num_samples  # 1 alignment per campione
    sorting_jobs = num_samples  # 1 sorting per campione
    dedup_jobs = num_samples  # 1 deduplication per campione
    peakcalling_jobs = num_samples * analysis_types  # Diversi tipi di analisi
    annotation_jobs = num_samples  # 1 annotazione per campione
    differential_jobs = max(1, num_samples // 2)  # Confronti tra condizioni
    visualization_jobs = num_samples + differential_jobs  # Plots individuali + comparativi
    report_jobs = 1  # 1 report finale
    
    total_jobs = (fastqc_jobs + trimming_jobs + alignment_jobs + sorting_jobs + 
                  dedup_jobs + peakcalling_jobs + annotation_jobs + 
                  differential_jobs + visualization_jobs + report_jobs)
    
    print(f"Job totali stimati: {total_jobs}")
    print()
    
    print("🎯 Configurazione parametri per Stable Matching...")
    
    # === CONFIGURAZIONE VM per Epigenomics (bioinformatica intensiva) ===
    vm_configs = [
        # VM CPU per bioinformatica generale
        {"type": "bio_standard", "count": 6, "capacity": 20.0, "cost": 0.6},
        {"type": "bio_medium", "count": 8, "capacity": 40.0, "cost": 1.0}, 
        {"type": "bio_high", "count": 10, "capacity": 80.0, "cost": 1.5},
        {"type": "bio_ultra", "count": 8, "capacity": 160.0, "cost": 2.5},
        
        # VM memoria-intensive per grandi dataset genomici
        {"type": "mem_intensive", "count": 4, "capacity": 100.0, "cost": 2.0},
        {"type": "mem_ultra", "count": 2, "capacity": 200.0, "cost": 4.0}
    ]
    
    # Calcola VM instances totali
    total_vms = sum(config["count"] for config in vm_configs)
    
    # Crea configurazione VM
    create_vm_preferences_file(vm_configs, total_jobs)
    
    print(f"📄 Creati {total_vms} VM instances nel formato Main.java")
    print("✅ File di configurazione per stable matching creati")
    
    # === CREAZIONE TASK del WORKFLOW EPIGENOMICS ===
    tasks = []
    task_id = 1
    
    # Task types con dimensioni diverse (in GB di dati da elaborare)
    task_types = {
        "FastQC": {"size": 3.0, "description": "Controllo qualità sequenze"},
        "Trimming": {"size": 5.0, "description": "Rimozione adattatori"},
        "Alignment": {"size": 15.0, "description": "Allineamento al genoma"},
        "Sorting": {"size": 8.0, "description": "Ordinamento file BAM"},
        "Deduplication": {"size": 6.0, "description": "Rimozione duplicati PCR"},
        "PeakCalling": {"size": 12.0, "description": "Identificazione picchi"},
        "Annotation": {"size": 10.0, "description": "Annotazione funzionale"},
        "DifferentialAnalysis": {"size": 18.0, "description": "Analisi differenziale"},
        "Visualization": {"size": 7.0, "description": "Generazione grafici"},
        "FinalReport": {"size": 25.0, "description": "Report finale integrato"}
    }
    
    # 1. FASTQC JOBS - controllo qualità per ogni file FASTQ
    print("🔍 Creando task FastQC per tutti i campioni...")
    fastqc_tasks = []
    for sample in range(num_samples):
        # R1 (forward reads)
        task_r1 = {
            "id": task_id,
            "name": f"FastQC_sample{sample:03d}_R1",
            "type": "FastQC",
            "size": task_types["FastQC"]["size"],
            "sample": sample,
            "read": "R1",
            "dependencies": []
        }
        tasks.append(task_r1)
        fastqc_tasks.append(task_id)
        task_id += 1
        
        # R2 (reverse reads)
        task_r2 = {
            "id": task_id,
            "name": f"FastQC_sample{sample:03d}_R2",
            "type": "FastQC",
            "size": task_types["FastQC"]["size"],
            "sample": sample,
            "read": "R2",
            "dependencies": []
        }
        tasks.append(task_r2)
        fastqc_tasks.append(task_id)
        print(f"  Processando campione {sample:03d}...")
        task_id += 1
    
    # 2. TRIMMING JOBS - rimozione adattatori
    print("✂️ Creando task Trimming per tutti i campioni...")
    trimming_tasks = []
    for sample in range(num_samples):
        # Dipende dai FastQC del campione corrente
        r1_fastqc = fastqc_tasks[sample * 2]
        r2_fastqc = fastqc_tasks[sample * 2 + 1]
        
        task = {
            "id": task_id,
            "name": f"Trimming_sample{sample:03d}",
            "type": "Trimming",
            "size": task_types["Trimming"]["size"],
            "sample": sample,
            "dependencies": [r1_fastqc, r2_fastqc]
        }
        tasks.append(task)
        trimming_tasks.append(task_id)
        task_id += 1
    
    # 3. ALIGNMENT JOBS - allineamento al genoma
    print("🎯 Creando task Alignment per tutti i campioni...")
    alignment_tasks = []
    for sample in range(num_samples):
        task = {
            "id": task_id,
            "name": f"Alignment_sample{sample:03d}",
            "type": "Alignment",
            "size": task_types["Alignment"]["size"],
            "sample": sample,
            "dependencies": [trimming_tasks[sample]]
        }
        tasks.append(task)
        alignment_tasks.append(task_id)
        task_id += 1
    
    # 4. SORTING JOBS - ordinamento file BAM
    print("📊 Creando task Sorting per tutti i campioni...")
    sorting_tasks = []
    for sample in range(num_samples):
        task = {
            "id": task_id,
            "name": f"Sorting_sample{sample:03d}",
            "type": "Sorting",
            "size": task_types["Sorting"]["size"],
            "sample": sample,
            "dependencies": [alignment_tasks[sample]]
        }
        tasks.append(task)
        sorting_tasks.append(task_id)
        task_id += 1
    
    # 5. DEDUPLICATION JOBS - rimozione duplicati PCR
    print("🔄 Creando task Deduplication per tutti i campioni...")
    dedup_tasks = []
    for sample in range(num_samples):
        task = {
            "id": task_id,
            "name": f"Deduplication_sample{sample:03d}",
            "type": "Deduplication",
            "size": task_types["Deduplication"]["size"],
            "sample": sample,
            "dependencies": [sorting_tasks[sample]]
        }
        tasks.append(task)
        dedup_tasks.append(task_id)
        task_id += 1
    
    # 6. PEAKCALLING JOBS - identificazione picchi per diversi tipi di analisi
    print("⛰️ Creando task PeakCalling per tutti i campioni e analisi...")
    peakcalling_tasks = []
    analysis_names = ["ChIP-seq", "ATAC-seq", "CUT&RUN"]
    for sample in range(num_samples):
        sample_peaks = []
        for analysis in range(analysis_types):
            task = {
                "id": task_id,
                "name": f"PeakCalling_sample{sample:03d}_{analysis_names[analysis % 3]}",
                "type": "PeakCalling",
                "size": task_types["PeakCalling"]["size"],
                "sample": sample,
                "analysis": analysis_names[analysis % 3],
                "dependencies": [dedup_tasks[sample]]
            }
            tasks.append(task)
            sample_peaks.append(task_id)
            task_id += 1
        peakcalling_tasks.append(sample_peaks)
    
    # 7. ANNOTATION JOBS - annotazione funzionale
    print("📝 Creando task Annotation per tutti i campioni...")
    annotation_tasks = []
    for sample in range(num_samples):
        # Dipende da tutti i PeakCalling del campione
        dependencies = peakcalling_tasks[sample]
        
        task = {
            "id": task_id,
            "name": f"Annotation_sample{sample:03d}",
            "type": "Annotation",
            "size": task_types["Annotation"]["size"],
            "sample": sample,
            "dependencies": dependencies
        }
        tasks.append(task)
        annotation_tasks.append(task_id)
        task_id += 1
    
    # 8. DIFFERENTIAL ANALYSIS JOBS - analisi differenziale tra condizioni
    print("📈 Creando task DifferentialAnalysis...")
    differential_tasks = []
    for diff in range(differential_jobs):
        # Seleziona campioni per il confronto
        start_sample = diff * 2
        end_sample = min(start_sample + 2, num_samples)
        compare_samples = list(range(start_sample, end_sample))
        
        # Dipende dalle annotazioni dei campioni coinvolti
        dependencies = [annotation_tasks[s] for s in compare_samples if s < len(annotation_tasks)]
        
        task = {
            "id": task_id,
            "name": f"DifferentialAnalysis_group{diff:02d}",
            "type": "DifferentialAnalysis",
            "size": task_types["DifferentialAnalysis"]["size"],
            "group": compare_samples,
            "dependencies": dependencies
        }
        tasks.append(task)
        differential_tasks.append(task_id)
        task_id += 1
    
    # 9. VISUALIZATION JOBS - generazione grafici
    print("📊 Creando task Visualization...")
    visualization_tasks = []
    
    # Plots individuali per ogni campione
    for sample in range(num_samples):
        task = {
            "id": task_id,
            "name": f"Visualization_sample{sample:03d}",
            "type": "Visualization",
            "size": task_types["Visualization"]["size"],
            "sample": sample,
            "dependencies": [annotation_tasks[sample]]
        }
        tasks.append(task)
        visualization_tasks.append(task_id)
        task_id += 1
    
    # Plots comparativi per analisi differenziali
    for diff in range(differential_jobs):
        task = {
            "id": task_id,
            "name": f"Visualization_differential{diff:02d}",
            "type": "Visualization",
            "size": task_types["Visualization"]["size"],
            "analysis": "differential",
            "dependencies": [differential_tasks[diff]]
        }
        tasks.append(task)
        visualization_tasks.append(task_id)
        task_id += 1
    
    # 10. FINAL REPORT JOB - report finale integrato
    print("📋 Creando task FinalReport...")
    final_report_task = {
        "id": task_id,
        "name": "FinalReport_Epigenomics",
        "type": "FinalReport",
        "size": task_types["FinalReport"]["size"],
        "dependencies": differential_tasks + visualization_tasks
    }
    tasks.append(final_report_task)
    task_id += 1
    
    # === SALVA FILE CSV per stable matching ===
    print("📊 Creazione file task.csv per stable matching...")
    data_dir = get_data_dir()
    with open(os.path.join(data_dir, "task.csv"), "w") as f:
        f.write("# Task list for Epigenomics workflow (format: t{ID} size)\n")
        for task in tasks:
            f.write(f"t{task['id']} {task['size']}\n")
    
    print("📊 Creazione file dag.csv per dipendenze...")
    with open(os.path.join(data_dir, "dag.csv"), "w") as f:
        f.write("# DAG dependencies for Epigenomics workflow (format: t{from} t{to})\n")
        for task in tasks:
            for dep_id in task["dependencies"]:
                f.write(f"t{dep_id} t{task['id']}\n")
    
    print(f"✅ Creati file task.csv e dag.csv con {len(tasks)} task")
    print()
    
    # === REPORT FINALE ===
    print("🎉 WORKFLOW EPIGENOMICS CON STABLE MATCHING GENERATO!")
    print("=" * 65)
    print(f"🧬 Campioni biologici: {num_samples}")
    print(f"🔢 Job totali: {len(tasks)}")
    print(f"🧪 Cromosomi analizzati: {num_chromosomes}")
    print(f"📊 Tipi di analisi: {analysis_types}")
    print()
    print("🎯 File per Stable Matching generati (formato Main.java):")
    print("   • task.csv - Task con formato 't{ID} size'")
    print("   • dag.csv - Dipendenze con formato 't{from} t{to}'")
    print("   • vm.csv - Matrice bandwidth VM con header")
    print("   • processing_capacity.csv - Capacità VM con formato 'vm{ID} capacity'")
    print()
    print("📊 Per applicare algoritmi stable matching:")
    print("   javac *.java && java Main")
    print()
    print("📊 Struttura del workflow Epigenomics:")
    print(f"   {num_samples}×2 × FastQC → {num_samples} × Trimming → {num_samples} × Alignment")
    print(f"   → {num_samples} × Sorting → {num_samples} × Deduplication → {len([t for sublist in peakcalling_tasks for t in sublist])} × PeakCalling")
    print(f"   → {num_samples} × Annotation → {differential_jobs} × DifferentialAnalysis")
    print(f"   → {len(visualization_tasks)} × Visualization → FinalReport")
    print()
    print("🧬 Workflow Epigenomics ottimizzato per analisi bioinformatiche!")
    
    return len(tasks)

def create_vm_preferences_file(vm_configs, total_jobs):
    """Crea i file di configurazione VM per il Main.java"""
    
    # Stima il numero di VM necessari (bilanciamento carico)
    vm_instances = []
    vm_id = 1
    
    for config in vm_configs:
        for i in range(config["count"]):
            vm_instances.append({
                "vm_id": f"vm{vm_id}",
                "type": config["type"],
                "capacity": config["capacity"],
                "cost": config["cost"],
                "processing_capacity": config["capacity"]  # Capacità di elaborazione
            })
            vm_id += 1
    
    # === CREA FILE vm.csv (matrice di bandwidth per Main.java) ===
    data_dir = get_data_dir()
    with open(os.path.join(data_dir, "vm.csv"), "w") as f:
        # Header con nomi VM
        header = "# " + " ".join([vm["vm_id"] for vm in vm_instances])
        f.write(header + "\n")
        
        # Matrice di bandwidth (simmetrica) - ottimizzata per Epigenomics
        for i, vm1 in enumerate(vm_instances):
            row = [vm1["vm_id"]]
            for j, vm2 in enumerate(vm_instances):
                if i == j:
                    bandwidth = 0.0  # Nessuna comunicazione con se stesso
                else:
                    # Bandwidth basata sui tipi di VM (per bioinformatica)
                    if "mem" in vm1["type"] and "mem" in vm2["type"]:
                        bandwidth = random.uniform(700, 1100)  # Memoria-Memoria alta bandwidth
                    elif "mem" in vm1["type"] or "mem" in vm2["type"]:
                        bandwidth = random.uniform(400, 700)   # Memoria-CPU media bandwidth
                    elif "ultra" in vm1["type"] and "ultra" in vm2["type"]:
                        bandwidth = random.uniform(500, 900)   # CPU alta-alta
                    else:
                        bandwidth = random.uniform(200, 500)   # CPU normale
                
                row.append(f"{bandwidth:.2f}")
            
            f.write(" ".join(row) + "\n")
    
    # === CREA FILE processing_capacity.csv ===
    with open(os.path.join(data_dir, "processing_capacity.csv"), "w") as f:
        f.write("# VM processing capacities for Epigenomics workflow\n")
        for vm in vm_instances:
            f.write(f"{vm['vm_id']} {vm['processing_capacity']}\n")

def main():
    parser = argparse.ArgumentParser(description="Generatore Workflow Epigenomics per Stable Matching")
    parser.add_argument("--samples", type=int, default=10, 
                       help="Numero di campioni biologici da analizzare (default: 10)")
    parser.add_argument("--chromosomes", type=int, default=22,
                       help="Numero di cromosomi da processare (default: 22)")
    parser.add_argument("--analyses", type=int, default=3,
                       help="Numero di tipi di analisi epigenetiche (default: 3)")
    
    args = parser.parse_args()
    
    # Validazione parametri
    if args.samples < 1 or args.samples > 100:
        print("❌ Errore: Il numero di campioni deve essere tra 1 e 100")
        sys.exit(1)
    
    if args.chromosomes < 1 or args.chromosomes > 24:
        print("❌ Errore: Il numero di cromosomi deve essere tra 1 e 24")
        sys.exit(1)
        
    if args.analyses < 1 or args.analyses > 5:
        print("❌ Errore: Il numero di tipi di analisi deve essere tra 1 e 5")
        sys.exit(1)
    
    # Genera il workflow Epigenomics
    total_jobs = create_epigenomics_workflow(
        num_samples=args.samples,
        num_chromosomes=args.chromosomes, 
        analysis_types=args.analyses
    )

if __name__ == "__main__":
    main()
