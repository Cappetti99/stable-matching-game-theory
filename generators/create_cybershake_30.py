#!/usr/bin/env python3
from wfcommons import CyclesRecipe
from wfcommons.wfgen import WorkflowGenerator
from pathlib import Path
import csv
import os
import random

def save_workflow_csv(workflow, output_dir: Path):
    """Salva il workflow in formato CSV (dag.csv, task.csv)"""
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # 1. task.csv: id,size
    # Size in MI (Million Instructions)
    # WfCommons dà runtime in secondi. Assumiamo 1000 MIPS per normalizzare
    # size = runtime * mips
    MIPS = 1000.0
    
    task_map = {} # map name -> id
    
    with open(output_dir / "task.csv", "w") as f:
        writer = csv.writer(f)
        writer.writerow(["id", "size"])
        
        for i, task in enumerate(workflow.nodes, 1):
            # WfCommons nodes might be objects or strings depending on version/recipe
            task_name = task.name if hasattr(task, 'name') else str(task)
            task_map[task_name] = i
            
            # Usa runtime stimato come proxy per size
            runtime = task.runtime if hasattr(task, 'runtime') else random.uniform(1, 10)
            size = runtime * MIPS / 1000.0 # Scaliamo per avere numeri ragionevoli
            writer.writerow([i, size])

    # 2. dag.csv: pred,succ,data
    # Data in MB
    with open(output_dir / "dag.csv", "w") as f:
        writer = csv.writer(f)
        writer.writerow(["pred", "succ", "data"])
        
        for task in workflow.nodes:
            task_name = task.name if hasattr(task, 'name') else str(task)
            u = task_map[task_name]
            
            for child in workflow.successors(task):
                child_name = child.name if hasattr(child, 'name') else str(child)
                v = task_map[child_name]
                
                # Calcola data transfer
                data_size = 0.0
                # Check if task has outputs attribute
                if hasattr(task, 'outputs') and hasattr(child, 'inputs'):
                    for file in task.outputs:
                        if file in child.inputs:
                            data_size += file.size / (1024*1024) # Convert to MB
                else:
                     data_size = random.uniform(0.1, 5.0) # Fallback random data size
                
                if data_size == 0: data_size = 1.0 # Minimo
                
                writer.writerow([u, v, data_size])
                
    # 3. vm.csv e processing_capacity.csv (dummy per visualizzazione)
    with open(output_dir / "vm.csv", "w") as f:
        f.write("id,mips,bandwidth\n1,1000,100\n")
    with open(output_dir / "processing_capacity.csv", "w") as f:
        f.write("vm_id,task_id,capacity\n1,1,1000\n")

def generate_single_cybershake(num_tasks=30):
    output_dir = Path("data_pegasus/cycles_30tasks")
    print(f"Generating CyberShake (Cycles) with ~{num_tasks} tasks in {output_dir}...")
    
    # CyberShake (Cycles) ha un minimo di nodi più alto per la sua struttura
    # Proviamo con un numero più alto se 30 fallisce, o usiamo Montage che scala meglio verso il basso
    try:
        recipe = CyclesRecipe.from_num_tasks(num_tasks)
        generator = WorkflowGenerator(recipe)
        workflow = generator.build_workflow()
    except ValueError as e:
        print(f"⚠️ Errore generazione Cycles con {num_tasks}: {e}")
        print("   Provo con SraSearch che supporta dimensioni molto piccole...")
        from wfcommons import SrasearchRecipe
        # SraSearch supporta anche pochi task
        recipe = SrasearchRecipe.from_num_tasks(num_tasks)
        generator = WorkflowGenerator(recipe)
        workflow = generator.build_workflow()
        output_dir = Path("data_pegasus/srasearch_30tasks") # Cambiamo output dir per correttezza
    
    print(f"Generated workflow with {len(workflow.nodes)} tasks.")
    save_workflow_csv(workflow, output_dir)
    print(f"Done. Saved to {output_dir}")

if __name__ == "__main__":
    generate_single_cybershake(30)
