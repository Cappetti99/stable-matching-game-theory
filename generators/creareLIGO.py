#!/usr/bin/env python3


"""
Generatore di Workflow LIGO personalizzabile
Crea un workflow LIGO per rilevamento onde gravitazionali con numero di segmenti configurabile dall'utente

Utilizzo:
    python3 creareLIGO.py --segments 20
    python3 creareLIGO.py --segments 50 --duration 4096
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

def create_ligo_workflow(num_segments=20, segment_duration=4096, inspiral_templates=10):
    """
    Crea un workflow LIGO per analisi di onde gravitazionali
    
    Parametri:
    - num_segments: numero di segmenti di dati da analizzare
    - segment_duration: durata di ogni segmento in secondi
    - inspiral_templates: numero di template per l'analisi inspiraling
    
    Struttura del workflow:
    1. DataFind: trova i dati grezzi per ogni segmento
    2. TemplateBank: genera template per la ricerca
    3. Inspiral: cerca segnali candidati in ogni segmento usando i template
    4. Coincidence: trova coincidenze tra diversi rivelatori 
    5. TrigBank: unisce i trigger di diversi segmenti
    6. Injection: test con segnali simulati
    7. Thinca: analisi finale delle coincidenze temporali
    8. PostProcessing: generazione risultati finali
    """
    
    print(f"🌌 CREAZIONE WORKFLOW LIGO PER STABLE MATCHING")
    print("=" * 50)
    print(f"Numero di segmenti: {num_segments}")
    print(f"Durata segmento: {segment_duration}s")
    print(f"Template inspiraling: {inspiral_templates}")
    
    # Calcola il numero totale di job
    datafind_jobs = num_segments  # 1 DataFind per segmento
    templatebank_jobs = 1  # 1 TemplateBank globale
    inspiral_jobs = num_segments * inspiral_templates  # N template per ogni segmento
    coincidence_jobs = math.ceil(num_segments / 4)  # Gruppi di 4 segmenti
    trigbank_jobs = 1  # 1 TrigBank finale
    injection_jobs = max(1, num_segments // 10)  # Test injection
    thinca_jobs = 1  # 1 Thinca finale
    postprocess_jobs = 1  # 1 PostProcessing finale
    
    total_jobs = (datafind_jobs + templatebank_jobs + inspiral_jobs + 
                  coincidence_jobs + trigbank_jobs + injection_jobs + 
                  thinca_jobs + postprocess_jobs)
    
    print(f"Job totali stimati: {total_jobs}")
    print()
    
    print("🎯 Configurazione parametri per Stable Matching...")
    
    # === CONFIGURAZIONE VM per LIGO (computazione scientifica intensiva) ===
    vm_configs = [
        # VM CPU-intensive per analisi numerica
        {"type": "cpu_intensive", "count": 4, "capacity": 15.0, "cost": 0.5},
        {"type": "cpu_medium", "count": 6, "capacity": 30.0, "cost": 0.8}, 
        {"type": "cpu_high", "count": 8, "capacity": 60.0, "cost": 1.2},
        {"type": "cpu_ultra", "count": 12, "capacity": 120.0, "cost": 2.0},
        
        # VM GPU per accelerazione computazionale  
        {"type": "gpu_standard", "count": 3, "capacity": 200.0, "cost": 3.0},
        {"type": "gpu_high", "count": 2, "capacity": 400.0, "cost": 5.0}
    ]
    
    # Calcola VM instances totali
    total_vms = sum(config["count"] for config in vm_configs)
    
    # Crea configurazione VM
    create_vm_preferences_file(vm_configs, total_jobs)
    
    print(f"📄 Creati {total_vms} VM instances nel formato Main.java")
    print("✅ File di configurazione per stable matching creati")
    
    # === CREAZIONE TASK del WORKFLOW LIGO ===
    tasks = []
    task_id = 1
    
    # Task types con dimensioni diverse (in GB di dati da elaborare)
    task_types = {
        "DataFind": {"size": 2.0, "description": "Trova e prepara dati grezzi"},
        "TemplateBank": {"size": 15.0, "description": "Genera template di ricerca"},
        "Inspiral": {"size": 8.0, "description": "Ricerca segnali candidati"},
        "Coincidence": {"size": 12.0, "description": "Analisi coincidenze"},
        "TrigBank": {"size": 20.0, "description": "Unione trigger"},
        "Injection": {"size": 5.0, "description": "Test segnali simulati"},
        "Thinca": {"size": 25.0, "description": "Analisi temporale finale"},
        "PostProcess": {"size": 10.0, "description": "Generazione risultati"}
    }
    
    # 1. DATAFIND JOBS - trova dati per ogni segmento
    print("🔍 Creando task DataFind per tutti i segmenti...")
    datafind_tasks = []
    for seg in range(num_segments):
        task = {
            "id": task_id,
            "name": f"DataFind_seg{seg:03d}",
            "type": "DataFind",
            "size": task_types["DataFind"]["size"],
            "segment": seg,
            "dependencies": []
        }
        tasks.append(task)
        datafind_tasks.append(task_id)
        print(f"  Elaborando segmento {seg:03d}...")
        task_id += 1
    
    # 2. TEMPLATEBANK JOB - genera template globali
    print("🧬 Creando task TemplateBank...")
    templatebank_task = {
        "id": task_id,
        "name": "TemplateBank_global",
        "type": "TemplateBank", 
        "size": task_types["TemplateBank"]["size"],
        "dependencies": []  # Indipendente
    }
    tasks.append(templatebank_task)
    templatebank_id = task_id
    task_id += 1
    
    # 3. INSPIRAL JOBS - analisi per ogni segmento e template
    print("🌀 Creando task Inspiral per tutti i segmenti e template...")
    inspiral_tasks = []
    for seg in range(num_segments):
        seg_inspirals = []
        for tpl in range(inspiral_templates):
            task = {
                "id": task_id,
                "name": f"Inspiral_seg{seg:03d}_tpl{tpl:02d}",
                "type": "Inspiral",
                "size": task_types["Inspiral"]["size"],
                "segment": seg,
                "template": tpl,
                "dependencies": [datafind_tasks[seg], templatebank_id]
            }
            tasks.append(task)
            seg_inspirals.append(task_id)
            task_id += 1
        inspiral_tasks.append(seg_inspirals)
    
    # 4. COINCIDENCE JOBS - trova coincidenze tra gruppi di segmenti
    print("🎯 Creando task Coincidence per gruppi di segmenti...")
    coincidence_tasks = []
    for group in range(0, num_segments, 4):
        group_segments = list(range(group, min(group + 4, num_segments)))
        
        # Dipende da tutti gli inspiral dei segmenti nel gruppo
        dependencies = []
        for seg in group_segments:
            dependencies.extend(inspiral_tasks[seg])
        
        task = {
            "id": task_id,
            "name": f"Coincidence_group{group//4:02d}",
            "type": "Coincidence",
            "size": task_types["Coincidence"]["size"],
            "group": group_segments,
            "dependencies": dependencies
        }
        tasks.append(task)
        coincidence_tasks.append(task_id)
        task_id += 1
    
    # 5. TRIGBANK JOB - unisce tutti i trigger
    print("🔗 Creando task TrigBank finale...")
    trigbank_task = {
        "id": task_id,
        "name": "TrigBank_final",
        "type": "TrigBank",
        "size": task_types["TrigBank"]["size"],
        "dependencies": coincidence_tasks
    }
    tasks.append(trigbank_task)
    trigbank_id = task_id
    task_id += 1
    
    # 6. INJECTION JOBS - test con segnali simulati
    print("💉 Creando task Injection per validazione...")
    injection_tasks = []
    for inj in range(injection_jobs):
        task = {
            "id": task_id,
            "name": f"Injection_test{inj:02d}",
            "type": "Injection",
            "size": task_types["Injection"]["size"],
            "dependencies": [templatebank_id]
        }
        tasks.append(task)
        injection_tasks.append(task_id)
        task_id += 1
    
    # 7. THINCA JOB - analisi finale delle coincidenze temporali
    print("⏰ Creando task Thinca per analisi temporale...")
    thinca_task = {
        "id": task_id,
        "name": "Thinca_final",
        "type": "Thinca",
        "size": task_types["Thinca"]["size"],
        "dependencies": [trigbank_id] + injection_tasks
    }
    tasks.append(thinca_task)
    thinca_id = task_id
    task_id += 1
    
    # 8. POSTPROCESSING JOB - risultati finali
    print("📊 Creando task PostProcessing finale...")
    postprocess_task = {
        "id": task_id,
        "name": "PostProcess_final",
        "type": "PostProcess",
        "size": task_types["PostProcess"]["size"],
        "dependencies": [thinca_id]
    }
    tasks.append(postprocess_task)
    task_id += 1
    
    # === SALVA FILE CSV per stable matching ===
    print("📊 Creazione file task.csv per stable matching...")
    data_dir = get_data_dir()
    with open(os.path.join(data_dir, "task.csv"), "w") as f:
        f.write("# Task list for LIGO workflow (format: t{ID} size)\n")
        for task in tasks:
            f.write(f"t{task['id']} {task['size']}\n")
    
    print("📊 Creazione file dag.csv per dipendenze...")
    with open(os.path.join(data_dir, "dag.csv"), "w") as f:
        f.write("# DAG dependencies for LIGO workflow (format: t{from} t{to})\n")
        for task in tasks:
            for dep_id in task["dependencies"]:
                f.write(f"t{dep_id} t{task['id']}\n")
    
    print(f"✅ Creati file task.csv e dag.csv con {len(tasks)} task")
    print()
    
    # === REPORT FINALE ===
    print("🎉 WORKFLOW LIGO CON STABLE MATCHING GENERATO!")
    print("=" * 60)
    print(f"🔬 Segmenti di dati: {num_segments}")
    print(f"🔢 Job totali: {len(tasks)}")
    print(f"⏱️ Durata segmento: {segment_duration}s")
    print(f"🧬 Template inspiral: {inspiral_templates}")
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
    print("📊 Struttura del workflow LIGO:")
    print(f"   {num_segments} × DataFind → TemplateBank")
    print(f"   → {len(tasks)} × Inspiral → {len(coincidence_tasks)} × Coincidence")
    print(f"   → TrigBank → {injection_jobs} × Injection → Thinca → PostProcess")
    print()
    print("🌌 Workflow LIGO ottimizzato per rilevamento onde gravitazionali!")
    
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
        
        # Matrice di bandwidth (simmetrica) - ottimizzata per LIGO
        for i, vm1 in enumerate(vm_instances):
            row = [vm1["vm_id"]]
            for j, vm2 in enumerate(vm_instances):
                if i == j:
                    bandwidth = 0.0  # Nessuna comunicazione con se stesso
                else:
                    # Bandwidth basata sui tipi di VM (per LIGO)
                    if "gpu" in vm1["type"] and "gpu" in vm2["type"]:
                        bandwidth = random.uniform(800, 1200)  # GPU-GPU alta bandwidth
                    elif "gpu" in vm1["type"] or "gpu" in vm2["type"]:
                        bandwidth = random.uniform(400, 800)   # GPU-CPU media bandwidth
                    elif "ultra" in vm1["type"] and "ultra" in vm2["type"]:
                        bandwidth = random.uniform(600, 1000)  # CPU alta-alta
                    else:
                        bandwidth = random.uniform(200, 600)   # CPU normale
                
                row.append(f"{bandwidth:.2f}")
            
            f.write(" ".join(row) + "\n")
    
    # === CREA FILE processing_capacity.csv ===
    with open(os.path.join(data_dir, "processing_capacity.csv"), "w") as f:
        f.write("# VM processing capacities for LIGO workflow\n")
        for vm in vm_instances:
            f.write(f"{vm['vm_id']} {vm['processing_capacity']}\n")

def main():
    parser = argparse.ArgumentParser(description="Generatore Workflow LIGO per Stable Matching")
    parser.add_argument("--segments", type=int, default=20, 
                       help="Numero di segmenti di dati da analizzare (default: 20)")
    parser.add_argument("--duration", type=int, default=4096,
                       help="Durata di ogni segmento in secondi (default: 4096)")
    parser.add_argument("--templates", type=int, default=10,
                       help="Numero di template per l'analisi inspiral (default: 10)")
    
    args = parser.parse_args()
    
    # Validazione parametri
    if args.segments < 1 or args.segments > 1000:
        print("❌ Errore: Il numero di segmenti deve essere tra 1 e 1000")
        sys.exit(1)
    
    if args.templates < 1 or args.templates > 50:
        print("❌ Errore: Il numero di template deve essere tra 1 e 50")
        sys.exit(1)
    
    # Genera il workflow LIGO
    total_jobs = create_ligo_workflow(
        num_segments=args.segments,
        segment_duration=args.duration, 
        inspiral_templates=args.templates
    )

if __name__ == "__main__":
    main()
