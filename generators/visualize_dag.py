#!/usr/bin/env python3
"""
Visualizzatore di DAG per workflow scientifici
Genera immagini dei grafi DAG per CyberShake, LIGO, Montage, Epigenomics
"""

import os
import sys

# Check for required packages
try:
    import matplotlib.pyplot as plt
    import matplotlib.patches as mpatches
    import numpy as np
except ImportError:
    print("Installing required packages...")
    os.system("pip3 install matplotlib numpy")
    import matplotlib.pyplot as plt
    import matplotlib.patches as mpatches
    import numpy as np

def load_dag(dag_file, task_file):
    """Carica il DAG da file CSV"""
    tasks = {}
    edges = []
    
    # Carica task
    with open(task_file, 'r') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            parts = line.split()
            task_id = int(parts[0].replace('t', ''))
            size = float(parts[1].replace(',', '.'))  # Handle both comma and dot decimal separators
            tasks[task_id] = {'size': size, 'predecessors': [], 'successors': []}
    
    # Carica edges
    with open(dag_file, 'r') as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith('#'):
                continue
            parts = line.split()
            from_id = int(parts[0].replace('t', ''))
            to_id = int(parts[1].replace('t', ''))
            edges.append((from_id, to_id))
            if from_id in tasks:
                tasks[from_id]['successors'].append(to_id)
            if to_id in tasks:
                tasks[to_id]['predecessors'].append(from_id)
    
    return tasks, edges

def calculate_levels(tasks):
    """Calcola i livelli (profondità) di ogni task nel DAG"""
    levels = {}
    
    # Task senza predecessori sono al livello 0
    for task_id, task in tasks.items():
        if not task['predecessors']:
            levels[task_id] = 0
    
    # Calcola livelli iterativamente
    changed = True
    while changed:
        changed = False
        for task_id, task in tasks.items():
            if task_id not in levels and task['predecessors']:
                pred_levels = [levels.get(p) for p in task['predecessors']]
                if all(l is not None for l in pred_levels):
                    levels[task_id] = max(pred_levels) + 1
                    changed = True
    
    return levels

def visualize_dag(tasks, edges, title, output_file):
    """Visualizza il DAG con layout gerarchico"""
    levels = calculate_levels(tasks)
    
    if not levels:
        print(f"Errore: impossibile calcolare i livelli per {title}")
        return
    
    # Raggruppa task per livello
    level_tasks = {}
    for task_id, level in levels.items():
        if level not in level_tasks:
            level_tasks[level] = []
        level_tasks[level].append(task_id)
    
    # Calcola posizioni
    max_level = max(level_tasks.keys())
    positions = {}
    
    for level, task_list in level_tasks.items():
        n = len(task_list)
        for i, task_id in enumerate(sorted(task_list)):
            x = (i - (n - 1) / 2) * 1.5  # Spread orizzontale
            y = -level * 2  # Livelli dall'alto al basso
            positions[task_id] = (x, y)
    
    # Crea figura
    fig, ax = plt.subplots(1, 1, figsize=(16, 12))
    
    # Disegna edges
    for from_id, to_id in edges:
        if from_id in positions and to_id in positions:
            x1, y1 = positions[from_id]
            x2, y2 = positions[to_id]
            ax.annotate('', xy=(x2, y2), xytext=(x1, y1),
                       arrowprops=dict(arrowstyle='->', color='gray', alpha=0.6, lw=0.8))
    
    # Disegna nodi
    node_colors = []
    for task_id in positions.keys():
        level = levels[task_id]
        # Colora per livello
        color = plt.cm.viridis(level / (max_level + 1))
        node_colors.append(color)
    
    xs = [positions[t][0] for t in positions]
    ys = [positions[t][1] for t in positions]
    sizes = [tasks[t]['size'] * 20 + 100 for t in positions]  # Size proporzionale
    
    scatter = ax.scatter(xs, ys, s=sizes, c=range(len(positions)), 
                        cmap='viridis', alpha=0.8, edgecolors='black', linewidth=0.5)
    
    # Etichette task (solo se non troppi)
    if len(tasks) <= 60:
        for task_id, (x, y) in positions.items():
            ax.annotate(f't{task_id}', (x, y), ha='center', va='center', 
                       fontsize=6, fontweight='bold', color='white')
    
    # Configurazione grafico
    ax.set_title(f'{title}\n({len(tasks)} tasks, {len(edges)} edges, {max_level + 1} levels)', 
                fontsize=14, fontweight='bold')
    ax.set_xlabel('Parallelismo (task allo stesso livello)')
    ax.set_ylabel('Profondità DAG (livelli)')
    ax.grid(True, alpha=0.3)
    
    # Legenda livelli
    legend_elements = []
    for i in range(min(6, max_level + 1)):
        color = plt.cm.viridis(i / (max_level + 1))
        legend_elements.append(mpatches.Patch(color=color, label=f'Level {i}'))
    if max_level >= 6:
        legend_elements.append(mpatches.Patch(color=plt.cm.viridis(1.0), label=f'Level {max_level}'))
    ax.legend(handles=legend_elements, loc='upper right', fontsize=8)
    
    # Statistiche
    stats_text = f"Entry tasks: {sum(1 for t in tasks.values() if not t['predecessors'])}\n"
    stats_text += f"Exit tasks: {sum(1 for t in tasks.values() if not t['successors'])}\n"
    stats_text += f"Max parallelism: {max(len(v) for v in level_tasks.values())}\n"
    stats_text += f"Avg task size: {np.mean([t['size'] for t in tasks.values()]):.1f}"
    ax.text(0.02, 0.98, stats_text, transform=ax.transAxes, fontsize=9,
           verticalalignment='top', bbox=dict(boxstyle='round', facecolor='wheat', alpha=0.5))
    
    plt.tight_layout()
    plt.savefig(output_file, dpi=150, bbox_inches='tight')
    plt.close()
    print(f"✅ Salvato: {output_file}")

def create_workflow_structure_diagram():
    """Crea diagramma delle strutture tipiche dei workflow"""
    fig, axes = plt.subplots(2, 2, figsize=(16, 14))
    
    workflows = {
        'CyberShake': {
            'structure': [
                ('PreCVM', 1),
                ('GenCVM', 'N sites'),
                ('GenSGT', 'N×2'),
                ('PSA', 'N×2×M'),
                ('ZipPSA', 'N'),
                ('PostProcess', 1)
            ],
            'description': 'Seismic Hazard Analysis\nPipeline: Pre → CVM → SGT → PSA → Zip → Post'
        },
        'Montage': {
            'structure': [
                ('mProject', 'N images'),
                ('mDiffFit', 'N'),
                ('mConcatFit', 1),
                ('mBgModel', 1),
                ('mBackground', 'N'),
                ('mAdd', 1),
                ('mShrink', 1)
            ],
            'description': 'Astronomical Image Mosaic\nPipeline: Project → Diff → Concat → Bg → Add'
        },
        'LIGO': {
            'structure': [
                ('DataCond', '3 detectors'),
                ('Segment', '3×S'),
                ('Template', '3×S×T'),
                ('Coincidence', 'Groups'),
                ('Injection', 1),
                ('PostProc', 1)
            ],
            'description': 'Gravitational Wave Detection\nPipeline: Cond → Segment → Match → Coincide'
        },
        'Epigenomics': {
            'structure': [
                ('FastQC', 'N samples'),
                ('Trim', 'N'),
                ('Align', 'N'),
                ('Sort', 'N'),
                ('Dedup', 'N'),
                ('Peak', 'N×A'),
                ('Annotate', 'N'),
                ('Analysis', 1)
            ],
            'description': 'Genomic Analysis Pipeline\nPipeline: QC → Trim → Align → Sort → Peak'
        }
    }
    
    for idx, (name, info) in enumerate(workflows.items()):
        ax = axes[idx // 2, idx % 2]
        
        # Disegna struttura semplificata
        n_stages = len(info['structure'])
        y_positions = np.linspace(0.9, 0.1, n_stages)
        
        for i, (stage, count) in enumerate(info['structure']):
            y = y_positions[i]
            
            # Box per lo stage
            color = plt.cm.Set3(i / n_stages)
            rect = mpatches.FancyBboxPatch((0.2, y - 0.04), 0.6, 0.06,
                                           boxstyle="round,pad=0.01",
                                           facecolor=color, edgecolor='black', linewidth=1.5)
            ax.add_patch(rect)
            ax.text(0.5, y, f'{stage}\n({count})', ha='center', va='center', 
                   fontsize=9, fontweight='bold')
            
            # Freccia verso il prossimo stage
            if i < n_stages - 1:
                ax.annotate('', xy=(0.5, y_positions[i+1] + 0.04), 
                           xytext=(0.5, y - 0.04),
                           arrowprops=dict(arrowstyle='->', color='darkgray', lw=2))
        
        ax.set_xlim(0, 1)
        ax.set_ylim(0, 1)
        ax.set_title(f'{name} Workflow', fontsize=14, fontweight='bold', pad=10)
        ax.text(0.5, 0.02, info['description'], ha='center', va='bottom', 
               fontsize=10, style='italic', wrap=True)
        ax.axis('off')
    
    plt.suptitle('Scientific Workflow DAG Structures', fontsize=16, fontweight='bold', y=0.98)
    plt.tight_layout()
    plt.savefig('../visualizations/workflow_structures.png', dpi=150, bbox_inches='tight')
    plt.close()
    print("✅ Salvato: ../visualizations/workflow_structures.png")

def main():
    """Main function"""
    print("=" * 60)
    print("🔬 VISUALIZZATORE DAG - Workflow Scientifici")
    print("=" * 60)
    
    # Directory
    data_dir = '../data'
    output_dir = '../visualizations'
    os.makedirs(output_dir, exist_ok=True)
    
    # Workflow directories
    workflow_dirs = {
        'montage': '../data_test_montage',
        'cybershake': '../data_test_cybershake', 
        'ligo': '../data_test_ligo',
        'epigenomics': '../data_test_epigenomics'
    }
    
    # Prima visualizza il DAG attuale in data/
    print("\n📊 Visualizzando DAG corrente...")
    dag_file = os.path.join(data_dir, 'dag.csv')
    task_file = os.path.join(data_dir, 'task.csv')
    
    if os.path.exists(dag_file) and os.path.exists(task_file):
        tasks, edges = load_dag(dag_file, task_file)
        # Leggi tipo workflow dal commento
        with open(dag_file, 'r') as f:
            first_line = f.readline()
            workflow_type = 'Unknown'
            for wf in ['MONTAGE', 'CYBERSHAKE', 'LIGO', 'EPIGENOMICS']:
                if wf in first_line.upper():
                    workflow_type = wf
                    break
        visualize_dag(tasks, edges, f'{workflow_type} Workflow DAG', 
                     os.path.join(output_dir, 'current_dag.png'))
    
    # Visualizza tutti i workflow disponibili
    print("\n📊 Visualizzando workflow specifici...")
    for name, dir_path in workflow_dirs.items():
        dag_file = os.path.join(dir_path, 'dag.csv')
        task_file = os.path.join(dir_path, 'task.csv')
        
        if os.path.exists(dag_file) and os.path.exists(task_file):
            tasks, edges = load_dag(dag_file, task_file)
            visualize_dag(tasks, edges, f'{name.upper()} Workflow DAG',
                         os.path.join(output_dir, f'{name}_dag.png'))
        else:
            print(f"⚠️  {name}: file non trovati in {dir_path}")
    
    # Crea diagramma strutture
    print("\n📐 Creando diagramma strutture workflow...")
    create_workflow_structure_diagram()
    
    print("\n" + "=" * 60)
    print("✅ Visualizzazione completata!")
    print(f"📁 Output in: {output_dir}")
    print("=" * 60)

if __name__ == '__main__':
    main()
