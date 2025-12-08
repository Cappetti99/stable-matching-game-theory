#!/usr/bin/env python3
"""
Generatore di workflow Pegasus usando WfCommons
Genera workflow scientifici realistici con le dimensioni specificate per il paper

Workflow supportati:
- Montage (astronomical mosaic)
- CyberShake (seismic hazard analysis)
- LIGO (gravitational wave detection)
- Epigenomics (genome analysis)

@author: Lorenzo Cappetti
"""

import json
import csv
import random
from pathlib import Path
from wfcommons import MontageRecipe, CyclesRecipe, EpigenomicsRecipe, SrasearchRecipe
from wfcommons.wfgen import WorkflowGenerator

# Configurazione dimensioni dal paper
WORKFLOW_SIZES = {
    'small': [30, 50, 100],
    'medium': [500],
    'large': [1000, 1500]
}

VM_CONFIGS = {
    'small': 5,
    'medium': 10,
    'large': 50
}

def generate_montage_workflow(num_tasks: int, output_dir: Path):
    """Genera workflow Montage (astronomical image mosaic)"""
    print(f"🔭 Generando Montage con ~{num_tasks} task...")
    
    # Montage scala con degree (numero immagini)
    # Approssimazione: tasks ≈ 4*degree^2 + 5*degree + 1
    # Risolvo per degree dato num_tasks
    degree = max(2, int((-5 + (25 + 16*(num_tasks-1))**0.5) / 8))
    
    recipe = MontageRecipe.from_num_tasks(num_tasks)
    generator = WorkflowGenerator(recipe)
    workflow = generator.build_workflow()
    
    print(f"   ✅ Montage generato: {len(workflow.nodes)} task (target: {num_tasks})")
    return workflow

def generate_epigenomics_workflow(num_tasks: int, output_dir: Path):
    """Genera workflow Epigenomics (genome sequencing pipeline)"""
    print(f"🧬 Generando Epigenomics con ~{num_tasks} task...")
    
    recipe = EpigenomicsRecipe.from_num_tasks(num_tasks)
    generator = WorkflowGenerator(recipe)
    workflow = generator.build_workflow()
    
    print(f"   ✅ Epigenomics generato: {len(workflow.nodes)} task (target: {num_tasks})")
    return workflow

def generate_cycles_workflow(num_tasks: int, output_dir: Path):
    """Genera workflow Cycles (agricultural simulation) - simile a LIGO"""
    print(f"🌾 Generando Cycles con ~{num_tasks} task...")
    
    recipe = CyclesRecipe.from_num_tasks(num_tasks)
    generator = WorkflowGenerator(recipe)
    workflow = generator.build_workflow()
    
    print(f"   ✅ Cycles generato: {len(workflow.nodes)} task (target: {num_tasks})")
    return workflow

def generate_srasearch_workflow(num_tasks: int, output_dir: Path):
    """Genera workflow SraSearch (bioinformatics search)"""
    print(f"🔬 Generando SraSearch con ~{num_tasks} task...")
    
    recipe = SrasearchRecipe.from_num_tasks(num_tasks)
    generator = WorkflowGenerator(recipe)
    workflow = generator.build_workflow()
    
    print(f"   ✅ SraSearch generato: {len(workflow.nodes)} task (target: {num_tasks})")
    return workflow

def workflow_to_csv(workflow, num_vms: int, output_dir: Path, workflow_name: str, ccr: float = 1.0):
    """
    Converte un workflow WfCommons nei file CSV per SM-CPTD
    
    File generati:
    - task.csv: id,size
    - dag.csv: pred,succ,data
    - vm.csv: id
    - processing_capacity.csv: vm_id,processing_capacity
    """
    random.seed(42)
    
    # Mappa nodi a ID numerici
    nodes = list(workflow.nodes())
    node_to_id = {node: i+1 for i, node in enumerate(nodes)}
    
    # Estrai task info
    tasks = []
    for node in nodes:
        task_data = workflow.nodes[node]
        # Usa runtime se disponibile, altrimenti genera casuale
        size = task_data.get('runtime', random.uniform(2.0, 25.0))
        if size <= 0:
            size = random.uniform(2.0, 25.0)
        tasks.append({
            'id': node_to_id[node],
            'size': size
        })
    
    # Estrai archi DAG con costi comunicazione basati su CCR
    avg_task_size = sum(t['size'] for t in tasks) / len(tasks)
    edges = []
    for u, v in workflow.edges():
        edge_data = workflow.edges[u, v]
        # Data transfer size - calcola basato su CCR
        data_size = edge_data.get('weight', avg_task_size * ccr)
        if data_size <= 0:
            data_size = avg_task_size * ccr
        edges.append({
            'pred': node_to_id[u],
            'succ': node_to_id[v],
            'data': data_size
        })
    
    # Genera VM
    vms = []
    for i in range(num_vms):
        vms.append({
            'id': i,
            'processing_capacity': random.uniform(10.0, 20.0)
        })
    
    # Genera matrice bandwidth
    bandwidth = []
    for i in range(num_vms):
        for j in range(num_vms):
            if i != j:
                bw = random.uniform(20.0, 30.0)
            else:
                bw = float('inf')  # Stesso VM = infinita
            bandwidth.append({
                'vm_i': i,
                'vm_j': j,
                'bandwidth': bw
            })
    
    # Salva file CSV
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # task.csv
    with open(output_dir / 'task.csv', 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['id', 'size'])
        writer.writeheader()
        writer.writerows(tasks)
    
    # dag.csv
    with open(output_dir / 'dag.csv', 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['pred', 'succ', 'data'])
        writer.writeheader()
        writer.writerows(edges)
    
    # vm.csv
    with open(output_dir / 'vm.csv', 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['id'])
        writer.writeheader()
        for vm in vms:
            writer.writerow({'id': vm['id']})
    
    # processing_capacity.csv
    with open(output_dir / 'processing_capacity.csv', 'w', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=['vm_id', 'processing_capacity'])
        writer.writeheader()
        for vm in vms:
            writer.writerow({'vm_id': vm['id'], 'processing_capacity': vm['processing_capacity']})
    
    print(f"   📁 Salvati CSV in {output_dir}/")
    return len(tasks), len(edges)

# Configurazione Esperimento 2: VM variabili
EXP2_FIXED_TASKS = 1000
EXP2_VM_COUNTS = [10, 20, 30, 40, 50]

def generate_exp1_workflows():
    """Genera workflow per Esperimento 1: Effetto CCR (task variabili, VM fissi)"""
    print("=" * 60)
    print("🚀 ESPERIMENTO 1: Effetto CCR")
    print("   Task: 100, 500, 1000, 1500 (VM fissi per categoria)")
    print("=" * 60)
    
    base_dir = Path("data_pegasus")
    
    workflow_generators = {
        'montage': generate_montage_workflow,
        'epigenomics': generate_epigenomics_workflow,
        'cycles': generate_cycles_workflow,
        'srasearch': generate_srasearch_workflow
    }
    
    results = []
    
    for wf_name, generator_func in workflow_generators.items():
        print(f"\n{'='*60}")
        print(f"📊 Workflow: {wf_name.upper()}")
        print("=" * 60)
        
        for category, sizes in WORKFLOW_SIZES.items():
            num_vms = VM_CONFIGS[category]
            
            for num_tasks in sizes:
                print(f"\n[{category.upper()}] {num_tasks} task, {num_vms} VM")
                
                try:
                    workflow = generator_func(num_tasks, base_dir)
                    
                    output_dir = base_dir / f"{wf_name}_{num_tasks}tasks"
                    actual_tasks, num_edges = workflow_to_csv(
                        workflow, num_vms, output_dir, wf_name
                    )
                    
                    results.append({
                        'workflow': wf_name,
                        'experiment': 'EXP1',
                        'target_tasks': num_tasks,
                        'actual_tasks': actual_tasks,
                        'edges': num_edges,
                        'vms': num_vms,
                        'category': category
                    })
                except Exception as e:
                    print(f"   ❌ Errore: {e}")
    
    return results


def generate_exp2_workflows():
    """Genera workflow per Esperimento 2: Effetto numero VM (task fissi, VM variabili)"""
    print("\n" + "=" * 60)
    print("🚀 ESPERIMENTO 2: Effetto numero VM")
    print(f"   Task fissi: {EXP2_FIXED_TASKS}, VM: {EXP2_VM_COUNTS}")
    print("=" * 60)
    
    base_dir = Path("data_pegasus")
    
    workflow_generators = {
        'montage': generate_montage_workflow,
        'epigenomics': generate_epigenomics_workflow,
        'cycles': generate_cycles_workflow,
        'srasearch': generate_srasearch_workflow
    }
    
    results = []
    
    for wf_name, generator_func in workflow_generators.items():
        print(f"\n{'='*60}")
        print(f"📊 Workflow: {wf_name.upper()}")
        print("=" * 60)
        
        # Genera workflow una volta con 1000 task
        print(f"\n[EXP2] {EXP2_FIXED_TASKS} task")
        
        try:
            workflow = generator_func(EXP2_FIXED_TASKS, base_dir)
            
            # Crea versioni con diversi numeri di VM
            for num_vms in EXP2_VM_COUNTS:
                print(f"   VM={num_vms}: ", end="")
                
                output_dir = base_dir / f"{wf_name}_{EXP2_FIXED_TASKS}tasks_{num_vms}vms"
                actual_tasks, num_edges = workflow_to_csv(
                    workflow, num_vms, output_dir, wf_name
                )
                
                results.append({
                    'workflow': wf_name,
                    'experiment': 'EXP2',
                    'target_tasks': EXP2_FIXED_TASKS,
                    'actual_tasks': actual_tasks,
                    'edges': num_edges,
                    'vms': num_vms,
                    'category': 'exp2_vm_effect'
                })
                
                print(f"✅ ({actual_tasks} task, {num_edges} edges)")
        except Exception as e:
            print(f"   ❌ Errore: {e}")
    
    return results


def generate_all_workflows():
    """Genera tutti i workflow per entrambi gli esperimenti del paper"""
    print("=" * 70)
    print("🚀 GENERAZIONE WORKFLOW PEGASUS PER ESPERIMENTI SM-CPTD")
    print("=" * 70)
    
    results = []
    
    # Esperimento 1: Effetto CCR
    results.extend(generate_exp1_workflows())
    
    # Esperimento 2: Effetto numero VM
    results.extend(generate_exp2_workflows())
    
    # Salva riepilogo
    base_dir = Path("data_pegasus")
    
    print("\n" + "=" * 70)
    print("📋 RIEPILOGO GENERAZIONE")
    print("=" * 70)
    
    print(f"\n{'Exp':<6} {'Workflow':<15} {'Target':<8} {'Actual':<8} {'Edges':<8} {'VMs':<6}")
    print("-" * 70)
    for r in results:
        print(f"{r['experiment']:<6} {r['workflow']:<15} {r['target_tasks']:<8} {r['actual_tasks']:<8} {r['edges']:<8} {r['vms']:<6}")
    
    # Salva come JSON
    with open(base_dir / 'generation_summary.json', 'w') as f:
        json.dump(results, f, indent=2)
    
    print(f"\n✅ Generazione completata! Directory: {base_dir}/")
    print(f"   Esperimento 1: {sum(1 for r in results if r['experiment'] == 'EXP1')} configurazioni")
    print(f"   Esperimento 2: {sum(1 for r in results if r['experiment'] == 'EXP2')} configurazioni")
    return results


if __name__ == '__main__':
    import sys
    
    if len(sys.argv) > 1:
        if sys.argv[1] == '--exp1':
            generate_exp1_workflows()
        elif sys.argv[1] == '--exp2':
            generate_exp2_workflows()
        else:
            generate_all_workflows()
    else:
        generate_all_workflows()
