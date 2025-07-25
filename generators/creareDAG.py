#!/usr/bin/env python3

"""
Generatore di Workflow CyberShake personalizzabile
Crea un workflow CyberShake con numero di nodi configurabile dall'utente

Utilizzo:
    python3 creareDAG.py --sites 10
    python3 creareDAG.py --sites 5 --sgt-variations 3 --psa-filters 4
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

def create_cybershake_workflow(num_sites, num_sgt_variations=2, num_psa_filter=3):
    """
    Crea un workflow CyberShake con numero configurabile di siti
    Genera solo i file CSV necessari per stable matching
    
    Args:
        num_sites: Numero di siti sismici da simulare
        num_sgt_variations: Numero di variazioni SGT per sito
        num_psa_filter: Numero di filtri PSA per sito
    """
    
    print(f"🌊 CREAZIONE WORKFLOW CYBERSHAKE PER STABLE MATCHING")
    print(f"==================================================")
    print(f"Numero di siti: {num_sites}")
    print(f"Variazioni SGT per sito: {num_sgt_variations}")
    print(f"Filtri PSA per sito: {num_psa_filter}")
    
    # Calcola il numero totale di job
    total_jobs = (
        1 +  # PreCVM job
        num_sites * (1 + num_sgt_variations + num_psa_filter) +  # Per ogni sito: 1 CVM + N SGT + M PSA
        num_sites +  # ZipPSA per ogni sito
        1  # PostProcessing finale
    )
    
    print(f"Job totali stimati: {total_jobs}")
    
    # === CONFIGURAZIONE STABLE MATCHING ===
    print("\n🎯 Configurazione parametri per Stable Matching...")
    
    # Definisci le VM disponibili con le loro capacità
    vm_configs = {
        "vm_small": {"cpu": 1, "memory": 2, "capacity": 10, "cost": 1.0},
        "vm_medium": {"cpu": 2, "memory": 4, "capacity": 20, "cost": 2.0}, 
        "vm_large": {"cpu": 4, "memory": 8, "capacity": 40, "cost": 4.0},
        "vm_xlarge": {"cpu": 8, "memory": 16, "capacity": 80, "cost": 8.0}
    }
    
    # Definisci i tipi di task con i loro requisiti
    task_types = {
        "PreCVM": {"cpu_req": 1, "memory_req": 1, "priority": 1, "duration": 2},
        "GenCVM": {"cpu_req": 2, "memory_req": 2, "priority": 3, "duration": 3},
        "GenSGT": {"cpu_req": 4, "memory_req": 4, "priority": 5, "duration": 5},
        "PSA": {"cpu_req": 3, "memory_req": 3, "priority": 4, "duration": 4},
        "ZipPSA": {"cpu_req": 1, "memory_req": 1, "priority": 2, "duration": 2},
        "PostProcess": {"cpu_req": 2, "memory_req": 2, "priority": 1, "duration": 3}
    }
    
    # Crea file di configurazione per stable matching
    create_vm_preferences_file(vm_configs, task_types, total_jobs)
    
    print(f"✅ File di configurazione per stable matching creati")
    
    # Lista per creare i file task.csv e dag.csv
    all_tasks = []
    task_dependencies = []
    
    # === JOB 1: PreCVM - Preparazione modello velocità ===
    print(f"  Creando PreCVM job...")
    
    task_info = {
        "task_id": 1,
        "task_name": "precvm_job",
        "task_type": "PreCVM", 
        "cpu_req": task_types["PreCVM"]["cpu_req"],
        "memory_req": task_types["PreCVM"]["memory_req"],
        "priority": task_types["PreCVM"]["priority"],
        "duration": task_types["PreCVM"]["duration"],
        "dependencies": []
    }
    all_tasks.append(task_info)
    
    # === JOB PER OGNI SITO ===
    current_task_id = 2
    
    for site_id in range(num_sites):
        site_name = f"site_{site_id:03d}"
        print(f"  Creando job per {site_name}...")
        
        # === GenCVM per questo sito ===
        gencvm_task_id = current_task_id
        current_task_id += 1
        
        task_info = {
            "task_id": gencvm_task_id,
            "task_name": f"gencvm_{site_name}",
            "task_type": "GenCVM",
            "cpu_req": task_types["GenCVM"]["cpu_req"],
            "memory_req": task_types["GenCVM"]["memory_req"],
            "priority": task_types["GenCVM"]["priority"],
            "duration": task_types["GenCVM"]["duration"],
            "dependencies": [1]  # Dipende da PreCVM
        }
        all_tasks.append(task_info)
        task_dependencies.append((1, gencvm_task_id))  # PreCVM -> GenCVM
        
        # === GenSGT jobs per questo sito ===
        site_sgt_task_ids = []
        for sgt_id in range(num_sgt_variations):
            gensgt_task_id = current_task_id
            current_task_id += 1
            site_sgt_task_ids.append(gensgt_task_id)
            
            task_info = {
                "task_id": gensgt_task_id,
                "task_name": f"gensgt_{site_name}_var{sgt_id}",
                "task_type": "GenSGT",
                "cpu_req": task_types["GenSGT"]["cpu_req"],
                "memory_req": task_types["GenSGT"]["memory_req"],
                "priority": task_types["GenSGT"]["priority"],
                "duration": task_types["GenSGT"]["duration"],
                "dependencies": [gencvm_task_id]  # Dipende da GenCVM
            }
            all_tasks.append(task_info)
            task_dependencies.append((gencvm_task_id, gensgt_task_id))  # GenCVM -> GenSGT
        
        # === PSA jobs per questo sito ===
        site_psa_task_ids = []
        for psa_id in range(num_psa_filter):
            psa_task_id = current_task_id
            current_task_id += 1
            site_psa_task_ids.append(psa_task_id)
            
            task_info = {
                "task_id": psa_task_id,
                "task_name": f"psa_{site_name}_filter{psa_id}",
                "task_type": "PSA",
                "cpu_req": task_types["PSA"]["cpu_req"],
                "memory_req": task_types["PSA"]["memory_req"],
                "priority": task_types["PSA"]["priority"],
                "duration": task_types["PSA"]["duration"],
                "dependencies": site_sgt_task_ids.copy()  # Dipende da tutti gli SGT del sito
            }
            all_tasks.append(task_info)
            
            # Aggiungi dipendenze PSA -> SGT
            for sgt_task_id in site_sgt_task_ids:
                task_dependencies.append((sgt_task_id, psa_task_id))
        
        # === ZipPSA per questo sito ===
        zippsa_task_id = current_task_id
        current_task_id += 1
        
        task_info = {
            "task_id": zippsa_task_id,
            "task_name": f"zippsa_{site_name}",
            "task_type": "ZipPSA",
            "cpu_req": task_types["ZipPSA"]["cpu_req"],
            "memory_req": task_types["ZipPSA"]["memory_req"],
            "priority": task_types["ZipPSA"]["priority"],
            "duration": task_types["ZipPSA"]["duration"],
            "dependencies": site_psa_task_ids.copy()  # Dipende da tutti i PSA del sito
        }
        all_tasks.append(task_info)
        
        # Aggiungi dipendenze ZipPSA -> PSA
        for psa_task_id in site_psa_task_ids:
            task_dependencies.append((psa_task_id, zippsa_task_id))
    
    # === JOB FINALE: PostProcess ===
    print(f"  Creando PostProcess job...")
    
    postprocess_task_id = current_task_id
    
    # Trova tutti i ZipPSA task IDs
    zippsa_task_ids = [task["task_id"] for task in all_tasks if task["task_type"] == "ZipPSA"]
    
    task_info = {
        "task_id": postprocess_task_id,
        "task_name": "postprocess_job",
        "task_type": "PostProcess",
        "cpu_req": task_types["PostProcess"]["cpu_req"],
        "memory_req": task_types["PostProcess"]["memory_req"],
        "priority": task_types["PostProcess"]["priority"],
        "duration": task_types["PostProcess"]["duration"],
        "dependencies": zippsa_task_ids.copy()  # Dipende da tutti i ZipPSA
    }
    all_tasks.append(task_info)
    
    # Aggiungi dipendenze PostProcess -> ZipPSA
    for zippsa_task_id in zippsa_task_ids:
        task_dependencies.append((zippsa_task_id, postprocess_task_id))
    
    # === CREAZIONE FILE TASK.CSV (formato Main.java) ===
    print("📊 Creazione file task.csv per stable matching...")
    data_dir = get_data_dir()
    with open(os.path.join(data_dir, "task.csv"), "w") as f:
        for task in all_tasks:
            # Formato: t{ID} size
            # Usiamo la duration come size del task
            f.write(f"t{task['task_id']} {task['duration']}\n")
    
    # === CREAZIONE FILE DAG.CSV (dipendenze per Main.java) ===
    print("📊 Creazione file dag.csv per dipendenze...")
    with open(os.path.join(data_dir, "dag.csv"), "w") as f:
        for from_task, to_task in task_dependencies:
            # Formato: t{from} t{to}
            f.write(f"t{from_task} t{to_task}\n")
    
    print(f"✅ Creati file task.csv e dag.csv con {len(all_tasks)} task")
    
    return len(all_tasks)

def create_vm_preferences_file(vm_configs, task_types, total_jobs):
    """Crea i file delle VM nel formato compatibile con Main.java"""
    
    # Crea VM instances basate sulla capacità totale richiesta
    vm_instances = []
    
    # Distribuzione delle VM per tipo
    vm_distribution = {
        "vm_small": max(1, total_jobs // 20),    # VM piccole per task leggeri
        "vm_medium": max(1, total_jobs // 15),   # VM medie
        "vm_large": max(1, total_jobs // 10),    # VM grandi  
        "vm_xlarge": max(1, total_jobs // 8)     # VM molto grandi
    }
    
    vm_id = 1
    for vm_type, config in vm_configs.items():
        num_instances = vm_distribution[vm_type]
        for i in range(num_instances):
            vm_instances.append({
                "vm_id": f"vm{vm_id}",
                "type": vm_type,
                "cpu": config["cpu"],
                "memory": config["memory"], 
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
        
        # Matrice di bandwidth (simmetrica)
        for i, vm1 in enumerate(vm_instances):
            row = [vm1["vm_id"]]
            for j, vm2 in enumerate(vm_instances):
                if i == j:
                    bandwidth = 0.0  # Nessuna comunicazione con se stesso
                else:
                    # Bandwidth basata sul tipo di VM
                    base_bandwidth = min(vm1["cpu"], vm2["cpu"]) * 50
                    bandwidth = base_bandwidth + random.uniform(-10, 10)
                    bandwidth = max(10.0, bandwidth)  # Minimo 10 Mbps
                row.append(f"{bandwidth:.1f}")
            f.write(" ".join(row) + "\n")
    
    # === CREA FILE processing_capacity.csv (per Main.java) ===
    with open(os.path.join(data_dir, "processing_capacity.csv"), "w") as f:
        for vm in vm_instances:
            # Formato: vm{ID} capacity
            f.write(f"{vm['vm_id']} {vm['processing_capacity']:.1f}\n")
    
    print(f"📄 Creati {len(vm_instances)} VM instances nel formato Main.java")
    return vm_instances

def main():
    """Funzione principale con parsing degli argomenti"""
    parser = argparse.ArgumentParser(description="Generatore Workflow CyberShake")
    parser.add_argument("--sites", "-s", type=int, default=5, 
                       help="Numero di siti sismici da simulare (default: 5)")
    parser.add_argument("--sgt-variations", type=int, default=2,
                       help="Numero di variazioni SGT per sito (default: 2)")
    parser.add_argument("--psa-filters", type=int, default=3,
                       help="Numero di filtri PSA per sito (default: 3)")
    
    args = parser.parse_args()
    
    # Validazione input
    if args.sites < 1 or args.sites > 100:
        print("❌ Errore: Il numero di siti deve essere tra 1 e 100")
        sys.exit(1)
    
    if args.sgt_variations < 1 or args.sgt_variations > 10:
        print("❌ Errore: Il numero di variazioni SGT deve essere tra 1 e 10")
        sys.exit(1)
    
    if args.psa_filters < 1 or args.psa_filters > 10:
        print("❌ Errore: Il numero di filtri PSA deve essere tra 1 e 10")
        sys.exit(1)
    
    # Crea il workflow
    total_jobs = create_cybershake_workflow(
        args.sites, 
        args.sgt_variations, 
        args.psa_filters
    )
    
    print(f"\n🎉 WORKFLOW CYBERSHAKE CON STABLE MATCHING GENERATO!")
    print("=" * 60)
    print(f"🏢 Siti sismici: {args.sites}")
    print(f"🔢 Job totali: {total_jobs}")
    print(f"⚡ Variazioni SGT per sito: {args.sgt_variations}")
    print(f"🔍 Filtri PSA per sito: {args.psa_filters}")
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
    print("📊 Struttura del workflow:")
    print(f"   PreCVM → {args.sites} × GenCVM → {args.sites * args.sgt_variations} × GenSGT")
    print(f"   → {args.sites * args.psa_filters} × PSA → {args.sites} × ZipPSA → PostProcess")

if __name__ == "__main__":
    main()