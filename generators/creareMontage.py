#!/usr/bin/env python3

"""
Generatore di Workflow Montage per analisi CCR vs SLR
Crea workflow Montage con parametri configurabili per stable matching e analizza l'impatto del CCR

Utilizzo:
    python3 creareMontage.py --images 10
    python3 creareMontage.py --scale medium --num_vms 10 --analyze_ccr
    python3 creareMontage.py --scale large --num_vms 50 --ccr_range 0.4,2.0
"""

import os
import sys
import argparse
import random
import math
import numpy as np
import matplotlib.pyplot as plt
import subprocess
import json
from pathlib import Path

# Ottieni il path della cartella data
def get_data_dir():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(script_dir)
    return os.path.join(project_root, "data")

def create_montage_workflow(num_images, degrees=0.5):
    """
    Crea un workflow Montage con numero configurabile di immagini
    Genera solo i file CSV necessari per stable matching
    
    Args:
        num_images: Numero di immagini astronomiche da processare
        degrees: Dimensione del mosaico in gradi (default: 0.5)
    """
    
    print(f"🌌 CREAZIONE WORKFLOW MONTAGE PER STABLE MATCHING")
    print(f"==================================================")
    print(f"Numero di immagini: {num_images}")
    print(f"Dimensione mosaico: {degrees} gradi")
    
    # Calcola parametri del workflow Montage
    # Ogni immagine necessita di: mProject, mDiffFit, mConcatFit, mBgModel, mBackground
    # Plus: mAdd finale e mJPEG per visualizzazione
    total_jobs = (
        num_images * 5 +  # 5 task per immagine (mProject, mDiffFit, mConcatFit, mBgModel, mBackground)
        1 +              # mAdd finale per combinare tutte le immagini
        1                # mJPEG per creare visualizzazione finale
    )
    
    print(f"Job totali stimati: {total_jobs}")
    
    # === CONFIGURAZIONE STABLE MATCHING ===
    print("\n🎯 Configurazione parametri per Stable Matching...")
    
    # Definisci le VM disponibili con le loro capacità (ottimizzate per calcoli astronomici)
    vm_configs = {
        "vm_small": {"cpu": 2, "memory": 4, "capacity": 15, "cost": 1.5},
        "vm_medium": {"cpu": 4, "memory": 8, "capacity": 30, "cost": 3.0}, 
        "vm_large": {"cpu": 8, "memory": 16, "capacity": 60, "cost": 6.0},
        "vm_xlarge": {"cpu": 16, "memory": 32, "capacity": 120, "cost": 12.0}
    }
    
    # Definisci i tipi di task Montage con i loro requisiti
    task_types = {
        "mProject": {"cpu_req": 2, "memory_req": 4, "priority": 3, "duration": 8},    # Riprojection
        "mDiffFit": {"cpu_req": 1, "memory_req": 2, "priority": 2, "duration": 3},   # Difference fitting
        "mConcatFit": {"cpu_req": 1, "memory_req": 1, "priority": 1, "duration": 2}, # Concatenate fits
        "mBgModel": {"cpu_req": 2, "memory_req": 3, "priority": 2, "duration": 4},   # Background modeling
        "mBackground": {"cpu_req": 3, "memory_req": 4, "priority": 4, "duration": 6}, # Background subtraction
        "mAdd": {"cpu_req": 4, "memory_req": 8, "priority": 5, "duration": 10},      # Image coaddition
        "mJPEG": {"cpu_req": 1, "memory_req": 2, "priority": 1, "duration": 3}      # JPEG visualization
    }
    
    # Crea file di configurazione per stable matching
    create_vm_preferences_file(vm_configs, task_types, total_jobs)
    
    print(f"✅ File di configurazione per stable matching creati")
    
    # Lista per creare i file task.csv e dag.csv
    all_tasks = []
    task_dependencies = []
    
    print(f"🖼️  Creando task per {num_images} immagini astronomiche...")
    
    # === TASK PER OGNI IMMAGINE ===
    current_task_id = 1
    
    # Liste per tracciare i task di ogni fase
    mproject_tasks = []
    mdifffit_tasks = []
    mconcatfit_tasks = []
    mbgmodel_tasks = []
    mbackground_tasks = []
    
    for img_id in range(num_images):
        img_name = f"img_{img_id:04d}"
        print(f"  Elaborando {img_name}...")
        
        # === mProject - Riproiezione immagine ===
        mproject_task_id = current_task_id
        current_task_id += 1
        mproject_tasks.append(mproject_task_id)
        
        task_info = {
            "task_id": mproject_task_id,
            "task_name": f"mproject_{img_name}",
            "task_type": "mProject",
            "cpu_req": task_types["mProject"]["cpu_req"],
            "memory_req": task_types["mProject"]["memory_req"],
            "priority": task_types["mProject"]["priority"],
            "duration": task_types["mProject"]["duration"],
            "dependencies": []  # Task indipendenti inizialmente
        }
        all_tasks.append(task_info)
        
        # === mDiffFit - Calcolo differenze ===
        mdifffit_task_id = current_task_id
        current_task_id += 1
        mdifffit_tasks.append(mdifffit_task_id)
        
        task_info = {
            "task_id": mdifffit_task_id,
            "task_name": f"mdifffit_{img_name}",
            "task_type": "mDiffFit",
            "cpu_req": task_types["mDiffFit"]["cpu_req"],
            "memory_req": task_types["mDiffFit"]["memory_req"],
            "priority": task_types["mDiffFit"]["priority"],
            "duration": task_types["mDiffFit"]["duration"],
            "dependencies": [mproject_task_id]  # Dipende da mProject
        }
        all_tasks.append(task_info)
        task_dependencies.append((mproject_task_id, mdifffit_task_id))
        
        # === mConcatFit - Concatenazione fit ===
        mconcatfit_task_id = current_task_id
        current_task_id += 1
        mconcatfit_tasks.append(mconcatfit_task_id)
        
        task_info = {
            "task_id": mconcatfit_task_id,
            "task_name": f"mconcatfit_{img_name}",
            "task_type": "mConcatFit",
            "cpu_req": task_types["mConcatFit"]["cpu_req"],
            "memory_req": task_types["mConcatFit"]["memory_req"],
            "priority": task_types["mConcatFit"]["priority"],
            "duration": task_types["mConcatFit"]["duration"],
            "dependencies": [mdifffit_task_id]  # Dipende da mDiffFit
        }
        all_tasks.append(task_info)
        task_dependencies.append((mdifffit_task_id, mconcatfit_task_id))
        
        # === mBgModel - Modellazione background ===
        mbgmodel_task_id = current_task_id
        current_task_id += 1
        mbgmodel_tasks.append(mbgmodel_task_id)
        
        task_info = {
            "task_id": mbgmodel_task_id,
            "task_name": f"mbgmodel_{img_name}",
            "task_type": "mBgModel",
            "cpu_req": task_types["mBgModel"]["cpu_req"],
            "memory_req": task_types["mBgModel"]["memory_req"],
            "priority": task_types["mBgModel"]["priority"],
            "duration": task_types["mBgModel"]["duration"],
            "dependencies": [mconcatfit_task_id]  # Dipende da mConcatFit
        }
        all_tasks.append(task_info)
        task_dependencies.append((mconcatfit_task_id, mbgmodel_task_id))
        
        # === mBackground - Sottrazione background ===
        mbackground_task_id = current_task_id
        current_task_id += 1
        mbackground_tasks.append(mbackground_task_id)
        
        task_info = {
            "task_id": mbackground_task_id,
            "task_name": f"mbackground_{img_name}",
            "task_type": "mBackground",
            "cpu_req": task_types["mBackground"]["cpu_req"],
            "memory_req": task_types["mBackground"]["memory_req"],
            "priority": task_types["mBackground"]["priority"],
            "duration": task_types["mBackground"]["duration"],
            "dependencies": [mbgmodel_task_id]  # Dipende da mBgModel
        }
        all_tasks.append(task_info)
        task_dependencies.append((mbgmodel_task_id, mbackground_task_id))
    
    # === mAdd - Combinazione finale di tutte le immagini ===
    print(f"  Creando task di combinazione finale...")
    
    madd_task_id = current_task_id
    current_task_id += 1
    
    task_info = {
        "task_id": madd_task_id,
        "task_name": "madd_final_mosaic",
        "task_type": "mAdd",
        "cpu_req": task_types["mAdd"]["cpu_req"],
        "memory_req": task_types["mAdd"]["memory_req"],
        "priority": task_types["mAdd"]["priority"],
        "duration": task_types["mAdd"]["duration"],
        "dependencies": mbackground_tasks.copy()  # Dipende da tutti i mBackground
    }
    all_tasks.append(task_info)
    
    # Aggiungi dipendenze mAdd -> mBackground
    for mbackground_task_id in mbackground_tasks:
        task_dependencies.append((mbackground_task_id, madd_task_id))
    
    # === mJPEG - Creazione visualizzazione finale ===
    print(f"  Creando task di visualizzazione...")
    
    mjpeg_task_id = current_task_id
    
    task_info = {
        "task_id": mjpeg_task_id,
        "task_name": "mjpeg_visualization",
        "task_type": "mJPEG",
        "cpu_req": task_types["mJPEG"]["cpu_req"],
        "memory_req": task_types["mJPEG"]["memory_req"],
        "priority": task_types["mJPEG"]["priority"],
        "duration": task_types["mJPEG"]["duration"],
        "dependencies": [madd_task_id]  # Dipende da mAdd
    }
    all_tasks.append(task_info)
    task_dependencies.append((madd_task_id, mjpeg_task_id))
    
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
    
    # Distribuzione delle VM per tipo (ottimizzata per calcoli astronomici)
    vm_distribution = {
        "vm_small": max(1, total_jobs // 18),    # VM piccole per task leggeri (mDiffFit, mConcatFit)
        "vm_medium": max(1, total_jobs // 12),   # VM medie per mBgModel
        "vm_large": max(1, total_jobs // 8),     # VM grandi per mProject e mBackground
        "vm_xlarge": max(1, total_jobs // 6)     # VM molto grandi per mAdd
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
        
        # Matrice di bandwidth (simmetrica) - ottimizzata per trasferimento immagini
        for i, vm1 in enumerate(vm_instances):
            row = [vm1["vm_id"]]
            for j, vm2 in enumerate(vm_instances):
                if i == j:
                    bandwidth = 0.0  # Nessuna comunicazione con se stesso
                else:
                    # Bandwidth più alta per workflow Montage (trasferimento immagini)
                    base_bandwidth = min(vm1["cpu"], vm2["cpu"]) * 75  # Aumentato da 50
                    bandwidth = base_bandwidth + random.uniform(-15, 15)
                    bandwidth = max(20.0, bandwidth)  # Minimo 20 Mbps per immagini
                row.append(f"{bandwidth:.1f}")
            f.write(" ".join(row) + "\n")
    
    # === CREA FILE processing_capacity.csv (per Main.java) ===
    with open(os.path.join(data_dir, "processing_capacity.csv"), "w") as f:
        for vm in vm_instances:
            # Formato: vm{ID} capacity
            f.write(f"{vm['vm_id']} {vm['processing_capacity']:.1f}\n")
    
    print(f"📄 Creati {len(vm_instances)} VM instances nel formato Main.java")
    return vm_instances

def create_montage_workflow_ccr(scale="medium", num_vms=10, ccr_value=1.0):
    """
    Crea un workflow Montage con parametri specifici per analisi CCR
    
    Args:
        scale: "small", "medium", "large" 
        num_vms: numero di VM (5, 10, 50)
        ccr_value: valore CCR da utilizzare (0.4-2.0)
    """
    
    # Definisci i parametri per ogni scala secondo le specifiche
    scale_params = {
        "small": {"num_images": 25, "task_size_range": (500, 600)},
        "medium": {"num_images": 50, "task_size_range": (550, 650)}, 
        "large": {"num_images": 100, "task_size_range": (600, 700)}
    }
    
    if scale not in scale_params:
        raise ValueError(f"Scale deve essere una di: {list(scale_params.keys())}")
    
    params = scale_params[scale]
    num_images = params["num_images"]
    size_min, size_max = params["task_size_range"]
    
    print(f"🌌 Creando workflow Montage - Scale: {scale}, Images: {num_images}, VMs: {num_vms}, CCR: {ccr_value}")
    
    # Calcola parametri del workflow
    total_jobs = (
        num_images * 5 +  # 5 task per immagine
        1 +              # mAdd finale
        1                # mJPEG
    )
    
    # Definisci le VM con capacità nel range 10-20 MIPS
    vm_configs = create_vm_configurations_ccr(num_vms)
    
    # Definisci i tipi di task Montage con size nel range specificato (500-700 MIP)
    task_types = {
        "mProject": {"size_range": (size_min, size_max), "priority": 3},
        "mDiffFit": {"size_range": (size_min, size_max), "priority": 2},
        "mConcatFit": {"size_range": (size_min, size_max), "priority": 1},
        "mBgModel": {"size_range": (size_min, size_max), "priority": 2},
        "mBackground": {"size_range": (size_min, size_max), "priority": 4},
        "mAdd": {"size_range": (size_min, size_max), "priority": 5},
        "mJPEG": {"size_range": (size_min, size_max), "priority": 1}
    }
    
    # Crea file di configurazione
    all_tasks = []
    task_dependencies = []
    current_task_id = 1
    
    # Task per ogni immagine
    mproject_tasks = []
    mdifffit_tasks = []
    mconcatfit_tasks = []
    mbgmodel_tasks = []
    mbackground_tasks = []
    
    for img_id in range(num_images):
        img_name = f"img_{img_id:04d}"
        
        # mProject
        mproject_task_id = current_task_id
        current_task_id += 1
        mproject_tasks.append(mproject_task_id)
        
        task_size = random.uniform(*task_types["mProject"]["size_range"])
        task_info = {
            "task_id": mproject_task_id,
            "task_name": f"mproject_{img_name}",
            "task_type": "mProject",
            "size": task_size,
            "dependencies": []
        }
        all_tasks.append(task_info)
        
        # mDiffFit
        mdifffit_task_id = current_task_id
        current_task_id += 1
        mdifffit_tasks.append(mdifffit_task_id)
        
        task_size = random.uniform(*task_types["mDiffFit"]["size_range"])
        task_info = {
            "task_id": mdifffit_task_id,
            "task_name": f"mdifffit_{img_name}",
            "task_type": "mDiffFit",
            "size": task_size,
            "dependencies": [mproject_task_id]
        }
        all_tasks.append(task_info)
        task_dependencies.append((mproject_task_id, mdifffit_task_id))
        
        # mConcatFit
        mconcatfit_task_id = current_task_id
        current_task_id += 1
        mconcatfit_tasks.append(mconcatfit_task_id)
        
        task_size = random.uniform(*task_types["mConcatFit"]["size_range"])
        task_info = {
            "task_id": mconcatfit_task_id,
            "task_name": f"mconcatfit_{img_name}",
            "task_type": "mConcatFit",
            "size": task_size,
            "dependencies": [mdifffit_task_id]
        }
        all_tasks.append(task_info)
        task_dependencies.append((mdifffit_task_id, mconcatfit_task_id))
        
        # mBgModel
        mbgmodel_task_id = current_task_id
        current_task_id += 1
        mbgmodel_tasks.append(mbgmodel_task_id)
        
        task_size = random.uniform(*task_types["mBgModel"]["size_range"])
        task_info = {
            "task_id": mbgmodel_task_id,
            "task_name": f"mbgmodel_{img_name}",
            "task_type": "mBgModel",
            "size": task_size,
            "dependencies": [mconcatfit_task_id]
        }
        all_tasks.append(task_info)
        task_dependencies.append((mconcatfit_task_id, mbgmodel_task_id))
        
        # mBackground
        mbackground_task_id = current_task_id
        current_task_id += 1
        mbackground_tasks.append(mbackground_task_id)
        
        task_size = random.uniform(*task_types["mBackground"]["size_range"])
        task_info = {
            "task_id": mbackground_task_id,
            "task_name": f"mbackground_{img_name}",
            "task_type": "mBackground",
            "size": task_size,
            "dependencies": [mbgmodel_task_id]
        }
        all_tasks.append(task_info)
        task_dependencies.append((mbgmodel_task_id, mbackground_task_id))
    
    # mAdd finale
    madd_task_id = current_task_id
    current_task_id += 1
    
    task_size = random.uniform(*task_types["mAdd"]["size_range"])
    task_info = {
        "task_id": madd_task_id,
        "task_name": "madd_final_mosaic",
        "task_type": "mAdd",
        "size": task_size,
        "dependencies": mbackground_tasks.copy()
    }
    all_tasks.append(task_info)
    
    for mbackground_task_id in mbackground_tasks:
        task_dependencies.append((mbackground_task_id, madd_task_id))
    
    # mJPEG finale
    mjpeg_task_id = current_task_id
    
    task_size = random.uniform(*task_types["mJPEG"]["size_range"])
    task_info = {
        "task_id": mjpeg_task_id,
        "task_name": "mjpeg_visualization", 
        "task_type": "mJPEG",
        "size": task_size,
        "dependencies": [madd_task_id]
    }
    all_tasks.append(task_info)
    task_dependencies.append((madd_task_id, mjpeg_task_id))
    
    # Crea file per algoritmi
    create_csv_files_ccr(all_tasks, task_dependencies, vm_configs, ccr_value)
    
    return len(all_tasks), vm_configs

def create_vm_configurations_ccr(num_vms):
    """Crea configurazioni VM con parametri nel range specificato (10-20 MIPS)"""
    vm_configs = []
    
    for vm_id in range(1, num_vms + 1):
        # Processing capacity: 10-20 MIPS
        processing_capacity = random.uniform(10.0, 20.0)
        
        vm_config = {
            "vm_id": f"vm{vm_id}",
            "processing_capacity": processing_capacity
        }
        vm_configs.append(vm_config)
    
    return vm_configs

def create_csv_files_ccr(all_tasks, task_dependencies, vm_configs, ccr_value):
    """Crea i file CSV necessari per gli algoritmi con parametri CCR"""
    
    # Assicurati che la directory data esista
    data_dir = Path("../data")
    data_dir.mkdir(exist_ok=True)
    
    # task.csv - formato: t{ID} size
    with open(data_dir / "task.csv", "w") as f:
        for task in all_tasks:
            f.write(f"t{task['task_id']} {task['size']:.1f}\n")
    
    # dag.csv - formato: t{from} t{to}
    with open(data_dir / "dag.csv", "w") as f:
        for from_task, to_task in task_dependencies:
            f.write(f"t{from_task} t{to_task}\n")
    
    # vm.csv - matrice bandwidth (20-30 Mbps secondo specifiche)
    with open(data_dir / "vm.csv", "w") as f:
        # Header
        header = "# " + " ".join([vm["vm_id"] for vm in vm_configs])
        f.write(header + "\n")
        
        # Matrice bandwidth simmetrica
        for i, vm1 in enumerate(vm_configs):
            row = [vm1["vm_id"]]
            for j, vm2 in enumerate(vm_configs):
                if i == j:
                    bandwidth = 0.0  # Nessuna comunicazione con se stesso
                else:
                    # Bandwidth range: 20-30 Mbps
                    bandwidth = random.uniform(20.0, 30.0)
                row.append(f"{bandwidth:.1f}")
            f.write(" ".join(row) + "\n")
    
    # processing_capacity.csv - formato: vm{ID} capacity
    with open(data_dir / "processing_capacity.csv", "w") as f:
        for vm in vm_configs:
            f.write(f"{vm['vm_id']} {vm['processing_capacity']:.1f}\n")
    
    print(f"✅ File CSV creati in {data_dir} con CCR={ccr_value}")

def run_algorithm_and_get_results(ccr_value=1.0):
    """Esegue l'algoritmo Java con CCR specifico e ottiene i risultati"""
    
    # Cambia nella directory algorithms
    original_dir = os.getcwd()
    algorithms_dir = Path("../algorithms")
    
    try:
        os.chdir(algorithms_dir)
        
        # Compila i file Java se non ancora compilati
        if not Path("MainCCR.class").exists():
            compile_result = subprocess.run(
                ["javac", "*.java"], 
                capture_output=True, 
                text=True,
                shell=True
            )
            
            if compile_result.returncode != 0:
                print(f"❌ Errore compilazione: {compile_result.stderr}")
                return None
        
        # Esegui MainCCR.java con parametro CCR
        run_result = subprocess.run(
            ["java", "MainCCR", str(ccr_value)], 
            capture_output=True, 
            text=True
        )
        
        if run_result.returncode != 0:
            print(f"❌ Errore esecuzione MainCCR:")
            print(f"   Stdout: {run_result.stdout}")
            print(f"   Stderr: {run_result.stderr}")
            return None
        
        output = run_result.stdout
        print(f"   ✅ Algoritmo eseguito, output: {len(output)} caratteri")
        
        # Estrai makespan e SLR dall'output
        makespan = extract_metric_from_output(output, "makespan")
        slr = extract_metric_from_output(output, "SLR")
        
        if makespan is None or slr is None:
            print(f"   ❌ Impossibile estrarre metriche dall'output")
            print(f"   Output parziale: {output[:500]}...")
            return None
        
        return {"makespan": makespan, "slr": slr, "output": output}
        
    finally:
        os.chdir(original_dir)

def extract_metric_from_output(output, metric_name):
    """Estrae una metrica specifica dall'output Java"""
    lines = output.split('\n')
    
    for line in lines:
        # Cerca nei FINAL RESULTS prima (formato inglese)
        if "=== FINAL RESULTS ===" in output:
            final_section = output.split("=== FINAL RESULTS ===")[1]
            for final_line in final_section.split('\n'):
                if metric_name in final_line and ":" in final_line:
                    try:
                        value_str = final_line.split(':')[1].strip()
                        return float(value_str)
                    except:
                        continue
        
        # Fallback: cerca nelle linee normali
        if metric_name.lower() in line.lower() and ":" in line:
            # Cerca pattern numerici nella riga
            import re
            # Pattern per numeri con punto decimale
            numbers = re.findall(r'[\d]+\.[\d]+', line)
            if numbers:
                try:
                    return float(numbers[-1])  # Prendi l'ultimo numero trovato
                except:
                    continue
            
            # Pattern per numeri interi
            numbers = re.findall(r'\b[\d]+\b', line)
            if numbers:
                try:
                    return float(numbers[-1])
                except:
                    continue
    
    return None

def analyze_ccr_impact(scale="medium", num_vms=10, ccr_range=(0.4, 2.0), num_points=10):
    """Analizza l'impatto del CCR sui risultati e genera grafici"""
    
    print(f"📊 ANALISI IMPATTO CCR SU WORKFLOW MONTAGE")
    print(f"==========================================")
    print(f"Scale: {scale}")
    print(f"Numero VMs: {num_vms}")
    print(f"CCR range: {ccr_range[0]} - {ccr_range[1]}")
    print(f"Punti di analisi: {num_points}")
    print("=" * 60)
    
    # Genera valori CCR nel range specificato
    ccr_values = np.linspace(ccr_range[0], ccr_range[1], num_points)
    
    results = {
        "ccr_values": [],
        "slr_values": [],
        "makespan_values": []
    }
    
    for i, ccr in enumerate(ccr_values):
        print(f"\n📈 Test {i+1}/{num_points}: CCR = {ccr:.2f}")
        
        # Crea workflow con CCR specifico
        num_tasks, vm_configs = create_montage_workflow_ccr(
            scale=scale, 
            num_vms=num_vms, 
            ccr_value=ccr
        )
        
        # Esegui algoritmo e ottieni risultati
        result = run_algorithm_and_get_results(ccr_value=ccr)
        
        if result and result["makespan"] and result["slr"]:
            results["ccr_values"].append(ccr)
            results["slr_values"].append(result["slr"])
            results["makespan_values"].append(result["makespan"])
            
            print(f"   ✅ Makespan: {result['makespan']:.2f}")
            print(f"   ✅ SLR: {result['slr']:.3f}")
        else:
            print(f"   ❌ Errore nell'esecuzione o parsing risultati")
    
    # Genera grafici se abbiamo risultati
    if len(results["ccr_values"]) > 0:
        create_plots(results, scale, num_vms)
        save_results_to_file(results, scale, num_vms)
        print_summary_stats(results)
    else:
        print("❌ Nessun risultato valido ottenuto")

def create_plots(results, scale, num_vms):
    """Crea grafici CCR vs SLR e CCR vs Makespan"""
    
    plt.style.use('default')
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(16, 7))
    
    # Grafico CCR vs SLR (richiesto dall'utente)
    ax1.plot(results["ccr_values"], results["slr_values"], 'o-', 
             linewidth=2.5, markersize=8, color='#1f77b4', label='SLR')
    ax1.set_xlabel('CCR (Communication-to-Computation Ratio)', fontsize=13, fontweight='bold')
    ax1.set_ylabel('SLR (Schedule Length Ratio)', fontsize=13, fontweight='bold')
    ax1.set_title(f'CCR vs SLR - Montage Workflow {scale.title()}\n{num_vms} VMs, Task Size: 500-700 MIP', 
                  fontsize=14, fontweight='bold')
    ax1.grid(True, alpha=0.3)
    ax1.set_xlim(min(results["ccr_values"]) - 0.05, max(results["ccr_values"]) + 0.05)
    
    # Aggiungi annotazioni per valori estremi
    min_slr_idx = results["slr_values"].index(min(results["slr_values"]))
    max_slr_idx = results["slr_values"].index(max(results["slr_values"]))
    
    ax1.annotate(f'Min SLR: {min(results["slr_values"]):.3f}', 
                xy=(results["ccr_values"][min_slr_idx], results["slr_values"][min_slr_idx]),
                xytext=(10, 10), textcoords='offset points', fontsize=10,
                bbox=dict(boxstyle='round,pad=0.3', facecolor='lightgreen', alpha=0.7))
    
    ax1.annotate(f'Max SLR: {max(results["slr_values"]):.3f}', 
                xy=(results["ccr_values"][max_slr_idx], results["slr_values"][max_slr_idx]),
                xytext=(10, -20), textcoords='offset points', fontsize=10,
                bbox=dict(boxstyle='round,pad=0.3', facecolor='lightcoral', alpha=0.7))
    
    # Grafico CCR vs Makespan (per completezza)
    ax2.plot(results["ccr_values"], results["makespan_values"], 'o-', 
             linewidth=2.5, markersize=8, color='#ff7f0e', label='Makespan')
    ax2.set_xlabel('CCR (Communication-to-Computation Ratio)', fontsize=13, fontweight='bold')
    ax2.set_ylabel('Makespan', fontsize=13, fontweight='bold')
    ax2.set_title(f'CCR vs Makespan - Montage Workflow {scale.title()}\n{num_vms} VMs, Processing: 10-20 MIPS', 
                  fontsize=14, fontweight='bold')
    ax2.grid(True, alpha=0.3)
    ax2.set_xlim(min(results["ccr_values"]) - 0.05, max(results["ccr_values"]) + 0.05)
    
    plt.tight_layout()
    
    # Salva il grafico
    plot_filename = f"montage_ccr_analysis_{scale}_{num_vms}vms.png"
    plt.savefig(plot_filename, dpi=300, bbox_inches='tight')
    print(f"📊 Grafico salvato: {plot_filename}")
    
    plt.show()

def save_results_to_file(results, scale, num_vms):
    """Salva i risultati in formato JSON"""
    
    results_data = {
        "workflow_type": "Montage",
        "scale": scale,
        "num_vms": num_vms,
        "parameters": {
            "task_size_range": "500-700 MIP",
            "vm_processing_capacity": "10-20 MIPS",
            "bandwidth_range": "20-30 Mbps",
            "ccr_range": f"{min(results['ccr_values']):.1f}-{max(results['ccr_values']):.1f}"
        },
        "results": results,
        "statistics": {
            "min_slr": min(results["slr_values"]),
            "max_slr": max(results["slr_values"]),
            "avg_slr": sum(results["slr_values"]) / len(results["slr_values"]),
            "min_makespan": min(results["makespan_values"]),
            "max_makespan": max(results["makespan_values"]),
            "avg_makespan": sum(results["makespan_values"]) / len(results["makespan_values"])
        }
    }
    
    filename = f"montage_ccr_results_{scale}_{num_vms}vms.json"
    with open(filename, 'w') as f:
        json.dump(results_data, f, indent=2)
    
    print(f"💾 Risultati salvati: {filename}")

def print_summary_stats(results):
    """Stampa statistiche riassuntive"""
    print(f"\n📈 STATISTICHE RIASSUNTIVE")
    print("=" * 40)
    print(f"SLR - Min: {min(results['slr_values']):.3f}, Max: {max(results['slr_values']):.3f}, Avg: {sum(results['slr_values'])/len(results['slr_values']):.3f}")
    print(f"Makespan - Min: {min(results['makespan_values']):.2f}, Max: {max(results['makespan_values']):.2f}, Avg: {sum(results['makespan_values'])/len(results['makespan_values']):.2f}")
    print(f"CCR Range: {min(results['ccr_values']):.2f} - {max(results['ccr_values']):.2f}")
    print(f"Correlazione CCR-SLR: {np.corrcoef(results['ccr_values'], results['slr_values'])[0,1]:.3f}")

def main():
    """Funzione principale con parsing degli argomenti"""
    parser = argparse.ArgumentParser(description="Generatore Workflow Montage con analisi CCR")
    
    # Parametri originali per compatibilità
    parser.add_argument("--images", "-i", type=int, 
                       help="Numero di immagini astronomiche da processare (modalità legacy)")
    parser.add_argument("--degrees", "-d", type=float, default=0.5,
                       help="Dimensione del mosaico in gradi (modalità legacy)")
    
    # Nuovi parametri per analisi CCR
    parser.add_argument("--scale", choices=["small", "medium", "large"], default="medium",
                       help="Scala del workflow: small(25), medium(50), large(100)")
    parser.add_argument("--num_vms", type=int, choices=[5, 10, 50], default=10,
                       help="Numero di VM")
    parser.add_argument("--analyze_ccr", action="store_true",
                       help="Esegui analisi CCR vs SLR")
    parser.add_argument("--ccr_range", type=str, default="0.4,2.0",
                       help="Range CCR per analisi (formato: min,max)")
    parser.add_argument("--num_points", type=int, default=10,
                       help="Numero di punti nell'analisi CCR")
    
    args = parser.parse_args()
    
    # Modalità analisi CCR
    if args.analyze_ccr:
        # Parse CCR range
        try:
            ccr_min, ccr_max = map(float, args.ccr_range.split(','))
            ccr_range = (ccr_min, ccr_max)
        except:
            print("❌ Errore nel formato CCR range. Usa formato: min,max (es: 0.4,2.0)")
            sys.exit(1)
        
        # Validazione
        if ccr_range[0] >= ccr_range[1] or ccr_range[0] < 0.1 or ccr_range[1] > 5.0:
            print("❌ CCR range non valido. Deve essere 0.1 <= min < max <= 5.0")
            sys.exit(1)
        
        # Esegui analisi
        analyze_ccr_impact(
            scale=args.scale,
            num_vms=args.num_vms,
            ccr_range=ccr_range,
            num_points=args.num_points
        )
        return
    
    # Modalità legacy (compatibilità con versione originale)
    if args.images:
        # Validazione input legacy
        if args.images < 1 or args.images > 200:
            print("❌ Errore: Il numero di immagini deve essere tra 1 e 200")
            sys.exit(1)
        
        if args.degrees < 0.1 or args.degrees > 10.0:
            print("❌ Errore: La dimensione deve essere tra 0.1 e 10.0 gradi")
            sys.exit(1)
        
        # Crea il workflow con modalità legacy
        total_jobs = create_montage_workflow(
            args.images, 
            args.degrees
        )
        
        print(f"\n🎉 WORKFLOW MONTAGE CON STABLE MATCHING GENERATO!")
        print("=" * 60)
        print(f"🖼️  Immagini astronomiche: {args.images}")
        print(f"🔢 Job totali: {total_jobs}")
        print(f"📐 Dimensione mosaico: {args.degrees} gradi")
    else:
        # Modalità singola con nuovi parametri
        print(f"🌌 Generazione singola workflow Montage")
        print(f"Scale: {args.scale}, VMs: {args.num_vms}")
        
        num_tasks, vm_configs = create_montage_workflow_ccr(
            scale=args.scale,
            num_vms=args.num_vms,
            ccr_value=1.0  # CCR default
        )
        
        print(f"\n✅ Workflow generato con {num_tasks} task e {len(vm_configs)} VM")
        print("💡 Usa --analyze_ccr per eseguire l'analisi completa CCR vs SLR")
    
    print()
    print("🎯 File per Stable Matching generati (formato Main.java):")
    print("   • task.csv - Task con formato 't{ID} size'")
    print("   • dag.csv - Dipendenze con formato 't{from} t{to}'")
    print("   • vm.csv - Matrice bandwidth VM con header")
    print("   • processing_capacity.csv - Capacità VM con formato 'vm{ID} capacity'")
    print()
    print("📊 Per applicare algoritmi stable matching:")
    print("   cd ../algorithms && javac *.java && java Main")

if __name__ == "__main__":
    main()
