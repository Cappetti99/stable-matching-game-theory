#!/usr/bin/env python3
"""
Plot dei risultati degli esperimenti SM-CPTD
Genera grafici SLR, AVU, VF al variare del CCR per ogni workflow
"""

import json
import matplotlib.pyplot as plt
import numpy as np
from pathlib import Path

def load_results():
    """Carica i risultati dal file JSON"""
    results_file = Path("results/experiments_results.json")
    if not results_file.exists():
        print("❌ File results/experiments_results.json non trovato!")
        print("   Esegui prima: cd algorithms && java ExperimentRunner")
        return None
    
    with open(results_file, 'r') as f:
        data = json.load(f)
    
    return data['experiments']

def organize_by_workflow_and_size(experiments):
    """Organizza i dati Esperimento 1 (CCR effect) per workflow e dimensione"""
    organized = {}
    
    for exp in experiments:
        # Filtra solo esperimenti EXP1 (CCR effect)
        exp_type = exp.get('experiment', '')
        if exp_type == 'EXP2_VM':
            continue
            
        workflow = exp['workflow']
        tasks = exp['tasks']
        key = f"{workflow}_{tasks}"
        
        if key not in organized:
            organized[key] = {
                'workflow': workflow,
                'tasks': tasks,
                'vms': exp['vms'],
                'ccr': [],
                'slr': [],
                'avu': [],
                'vf': [],
                'makespan': []
            }
        
        organized[key]['ccr'].append(exp['ccr'])
        organized[key]['slr'].append(exp['slr'])
        organized[key]['avu'].append(exp['avu'])
        organized[key]['vf'].append(exp['vf'])
        organized[key]['makespan'].append(exp['makespan'])
    
    return organized

def plot_metric_by_workflow(organized, metric, metric_label, output_file):
    """Crea un grafico 2x2 per una metrica specifica"""
    
    # Raggruppa per workflow
    workflows = {}
    for key, data in organized.items():
        wf = data['workflow']
        if wf not in workflows:
            workflows[wf] = []
        workflows[wf].append(data)
    
    # Colori per diverse dimensioni
    colors = {30: '#2196F3', 50: '#4CAF50', 100: '#FF9800', 500: '#9C27B0', 1000: '#F44336', 1500: '#795548'}
    markers = {30: 'o', 50: 's', 100: '^', 500: 'D', 1000: 'p', 1500: 'h'}
    
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    fig.suptitle(f'SM-CPTD: {metric_label} vs CCR', fontsize=16, fontweight='bold')
    
    # Workflow Pegasus disponibili
    workflow_names = ['MONTAGE', 'EPIGENOMICS', 'CYCLES', 'SRASEARCH']
    
    for idx, wf_name in enumerate(workflow_names):
        ax = axes[idx // 2, idx % 2]
        
        if wf_name in workflows:
            for data in sorted(workflows[wf_name], key=lambda x: x['tasks']):
                tasks = data['tasks']
                ccr = data['ccr']
                values = data[metric]
                
                # Ordina per CCR
                sorted_pairs = sorted(zip(ccr, values))
                ccr_sorted = [p[0] for p in sorted_pairs]
                values_sorted = [p[1] for p in sorted_pairs]
                
                ax.plot(ccr_sorted, values_sorted, 
                       color=colors.get(tasks, '#000000'),
                       marker=markers.get(tasks, 'o'),
                       linewidth=2, markersize=8,
                       label=f'{tasks} tasks ({data["vms"]} VMs)')
        
        ax.set_xlabel('CCR (Communication-to-Computation Ratio)', fontsize=10)
        ax.set_ylabel(metric_label, fontsize=10)
        ax.set_title(f'{wf_name}', fontsize=12, fontweight='bold')
        ax.legend(loc='best', fontsize=8)
        ax.grid(True, alpha=0.3)
        ax.set_xlim(0.3, 2.1)
    
    plt.tight_layout()
    plt.savefig(output_file, dpi=150, bbox_inches='tight')
    plt.close()
    print(f"✅ Salvato: {output_file}")

def plot_all_metrics_single_size(organized, tasks_filter, output_file):
    """Crea un grafico con SLR, AVU, VF per una singola dimensione"""
    
    # Filtra per dimensione
    filtered = {k: v for k, v in organized.items() if v['tasks'] == tasks_filter}
    
    if not filtered:
        print(f"⚠️ Nessun dato per {tasks_filter} tasks")
        return
    
    fig, axes = plt.subplots(1, 3, figsize=(15, 5))
    fig.suptitle(f'SM-CPTD Performance ({tasks_filter} tasks) vs CCR', fontsize=14, fontweight='bold')
    
    colors = {'MONTAGE': '#2196F3', 'EPIGENOMICS': '#4CAF50', 'CYCLES': '#FF9800', 'SRASEARCH': '#9C27B0'}
    markers = {'MONTAGE': 'o', 'EPIGENOMICS': 's', 'CYCLES': '^', 'SRASEARCH': 'D'}
    
    metrics = [('slr', 'SLR (Schedule Length Ratio)'), 
               ('avu', 'AVU (Average VM Utilization)'),
               ('vf', 'VF (Variance of Fairness)')]
    
    for ax_idx, (metric, label) in enumerate(metrics):
        ax = axes[ax_idx]
        
        for key, data in filtered.items():
            wf = data['workflow']
            ccr = data['ccr']
            values = data[metric]
            
            # Ordina per CCR
            sorted_pairs = sorted(zip(ccr, values))
            ccr_sorted = [p[0] for p in sorted_pairs]
            values_sorted = [p[1] for p in sorted_pairs]
            
            ax.plot(ccr_sorted, values_sorted,
                   color=colors.get(wf, '#000000'),
                   marker=markers.get(wf, 'o'),
                   linewidth=2, markersize=8,
                   label=wf)
        
        ax.set_xlabel('CCR', fontsize=10)
        ax.set_ylabel(label, fontsize=10)
        ax.legend(loc='best', fontsize=9)
        ax.grid(True, alpha=0.3)
        ax.set_xlim(0.3, 2.1)
    
    plt.tight_layout()
    plt.savefig(output_file, dpi=150, bbox_inches='tight')
    plt.close()
    print(f"✅ Salvato: {output_file}")

def plot_summary_table(organized):
    """Crea una tabella riassuntiva"""
    print("\n" + "="*80)
    print("📊 RIASSUNTO RISULTATI SM-CPTD")
    print("="*80)
    
    print(f"\n{'Workflow':<12} {'Tasks':<6} {'VMs':<4} {'SLR Range':<16} {'AVU Range':<16} {'VF Range':<20}")
    print("-"*80)
    
    for key in sorted(organized.keys()):
        data = organized[key]
        slr_min, slr_max = min(data['slr']), max(data['slr'])
        avu_min, avu_max = min(data['avu']), max(data['avu'])
        vf_min, vf_max = min(data['vf']), max(data['vf'])
        
        print(f"{data['workflow']:<12} {data['tasks']:<6} {data['vms']:<4} "
              f"{slr_min:.4f}-{slr_max:.4f}  {avu_min:.4f}-{avu_max:.4f}  "
              f"{vf_min:.6f}-{vf_max:.6f}")

def main():
    print("🎨 Generazione grafici risultati SM-CPTD")
    print("="*50)
    
    # Carica dati
    experiments = load_results()
    if experiments is None:
        return
    
    print(f"📊 Caricati {len(experiments)} punti dati")
    
    # Conta esperimenti per tipo
    exp1_count = sum(1 for e in experiments if e.get('experiment', '').startswith('EXP1'))
    exp2_count = sum(1 for e in experiments if e.get('experiment', '') == 'EXP2_VM')
    print(f"   Esperimento 1 (CCR): {exp1_count} punti")
    print(f"   Esperimento 2 (VM): {exp2_count} punti")
    
    # Organizza dati Esperimento 1 (CCR effect)
    organized = organize_by_workflow_and_size(experiments)
    print(f"📁 Organizzati EXP1 in {len(organized)} gruppi (workflow + size)")
    
    # Organizza dati Esperimento 2 (VM effect)
    organized_exp2 = organize_exp2_by_workflow(experiments)
    print(f"📁 Organizzati EXP2 in {len(organized_exp2)} workflow")
    
    # Crea cartella output
    output_dir = Path("assets")
    output_dir.mkdir(exist_ok=True)
    
    # Plot per ogni metrica (tutti i workflow, tutte le dimensioni)
    print("\n📈 Generazione grafici per metrica...")
    plot_metric_by_workflow(organized, 'slr', 'SLR (Schedule Length Ratio)', 
                           output_dir / 'exp_slr_by_ccr.png')
    plot_metric_by_workflow(organized, 'avu', 'AVU (Average VM Utilization)', 
                           output_dir / 'exp_avu_by_ccr.png')
    plot_metric_by_workflow(organized, 'vf', 'VF (Variance of Fairness)', 
                           output_dir / 'exp_vf_by_ccr.png')
    
    # Plot combinati per dimensione (se ci sono dati)
    print("\n📈 Generazione grafici combinati per dimensione...")
    for tasks in [30, 50, 100, 500, 1000, 1500]:
        if any(d['tasks'] == tasks for d in organized.values()):
            plot_all_metrics_single_size(organized, tasks, 
                                        output_dir / f'exp_all_metrics_{tasks}tasks.png')
    
    # Tabella riassuntiva
    plot_summary_table(organized)
    
    # Plot paper-style per categoria (Esperimento 1)
    print("\n📈 Generazione grafici paper-style Esperimento 1 (CCR)...")
    plot_paper_style_by_category(organized, output_dir)
    
    # Plot paper-style Esperimento 2 (VM effect)
    print("\n📈 Generazione grafici paper-style Esperimento 2 (VM)...")
    plot_exp2_vm_effect(organized_exp2, output_dir)
    
    print("\n✅ Tutti i grafici generati in assets/")

def plot_paper_style_by_category(organized, output_dir):
    """Crea grafici in stile paper, separati per categoria (Small, Medium, Large)"""
    
    # Categorie con range di task (Pegasus non genera esattamente il numero richiesto)
    categories = {
        'small': {'tasks_range': (50, 150), 'title': 'Small Workflows (~100 tasks, 5 VMs)'},
        'medium': {'tasks_range': (400, 600), 'title': 'Medium Workflows (~500 tasks, 10 VMs)'},
        'large': {'tasks_range': (900, 1600), 'title': 'Large Workflows (1000-1500 tasks, 50 VMs)'}
    }
    
    colors = {'MONTAGE': '#1f77b4', 'EPIGENOMICS': '#ff7f0e', 'CYCLES': '#2ca02c', 'SRASEARCH': '#d62728'}
    markers = {'MONTAGE': 'o', 'EPIGENOMICS': 's', 'CYCLES': '^', 'SRASEARCH': 'D'}
    
    for cat_name, cat_config in categories.items():
        fig, axes = plt.subplots(1, 3, figsize=(15, 4.5))
        fig.suptitle(f"SM-CPTD Performance - {cat_config['title']}", fontsize=14, fontweight='bold')
        
        metrics = [('slr', 'SLR'), ('avu', 'AVU (%)'), ('vf', 'VF')]
        
        for ax_idx, (metric, ylabel) in enumerate(metrics):
            ax = axes[ax_idx]
            has_data = False
            
            for key, data in organized.items():
                tasks = data['tasks']
                min_t, max_t = cat_config['tasks_range']
                
                # Matching flessibile per range di task
                if not (min_t <= tasks <= max_t):
                    continue
                
                wf = data['workflow']
                ccr = data['ccr']
                values = data[metric].copy()
                
                # Converti AVU in percentuale
                if metric == 'avu':
                    values = [v * 100 for v in values]
                
                # Ordina per CCR
                sorted_pairs = sorted(zip(ccr, values))
                ccr_sorted = [p[0] for p in sorted_pairs]
                values_sorted = [p[1] for p in sorted_pairs]
                
                label = f"{wf} ({tasks}t)"
                ax.plot(ccr_sorted, values_sorted,
                       color=colors.get(wf, '#000000'),
                       marker=markers.get(wf, 'o'),
                       linewidth=2, markersize=6,
                       label=label)
                has_data = True
            
            ax.set_xlabel('CCR', fontsize=11)
            ax.set_ylabel(ylabel, fontsize=11)
            if has_data:
                ax.legend(loc='best', fontsize=8)
            ax.grid(True, alpha=0.3, linestyle='--')
            ax.set_xlim(0.35, 2.05)
        
        plt.tight_layout()
        output_file = output_dir / f'paper_exp1_{cat_name}.png'
        plt.savefig(output_file, dpi=200, bbox_inches='tight')
        plt.close()
        print(f"✅ Salvato: {output_file}")


def organize_exp2_by_workflow(experiments):
    """Organizza i dati dell'Esperimento 2 per workflow (VM variabili)"""
    organized = {}
    
    for exp in experiments:
        # Filtra solo esperimenti EXP2_VM
        if exp.get('experiment', '') != 'EXP2_VM':
            continue
            
        workflow = exp['workflow']
        
        if workflow not in organized:
            organized[workflow] = {
                'workflow': workflow,
                'tasks': exp['tasks'],
                'ccr': exp['ccr'],
                'vms': [],
                'slr': [],
                'avu': [],
                'vf': [],
                'makespan': []
            }
        
        organized[workflow]['vms'].append(exp['vms'])
        organized[workflow]['slr'].append(exp['slr'])
        organized[workflow]['avu'].append(exp['avu'])
        organized[workflow]['vf'].append(exp['vf'])
        organized[workflow]['makespan'].append(exp['makespan'])
    
    return organized


def plot_exp2_vm_effect(organized_exp2, output_dir):
    """
    Crea grafici Esperimento 2: Effetto numero VM (Figure 9-10 del paper)
    - Figura 9: SLR vs Number of VMs
    - Figura 10: AVU vs Number of VMs
    """
    
    if not organized_exp2:
        print("⚠️ Nessun dato per Esperimento 2 (VM effect)")
        return
    
    colors = {'MONTAGE': '#1f77b4', 'EPIGENOMICS': '#ff7f0e', 'CYCLES': '#2ca02c', 'SRASEARCH': '#d62728'}
    markers = {'MONTAGE': 'o', 'EPIGENOMICS': 's', 'CYCLES': '^', 'SRASEARCH': 'D'}
    
    # Figura 9: SLR vs VMs (2x2 subplots, uno per workflow)
    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    fig.suptitle('SM-CPTD: SLR vs Number of VMs (1000 tasks, CCR=1.0)', fontsize=14, fontweight='bold')
    
    workflow_order = ['MONTAGE', 'EPIGENOMICS', 'CYCLES', 'SRASEARCH']
    
    for idx, wf_name in enumerate(workflow_order):
        ax = axes[idx // 2, idx % 2]
        
        if wf_name in organized_exp2:
            data = organized_exp2[wf_name]
            vms = data['vms']
            slr = data['slr']
            
            # Ordina per numero di VM
            sorted_pairs = sorted(zip(vms, slr))
            vms_sorted = [p[0] for p in sorted_pairs]
            slr_sorted = [p[1] for p in sorted_pairs]
            
            ax.plot(vms_sorted, slr_sorted,
                   color=colors.get(wf_name, '#000000'),
                   marker=markers.get(wf_name, 'o'),
                   linewidth=2.5, markersize=10,
                   label=f'SM-CPTD')
            
            ax.set_xlabel('Number of VMs', fontsize=11)
            ax.set_ylabel('SLR (Schedule Length Ratio)', fontsize=11)
            ax.set_title(f'{wf_name}', fontsize=12, fontweight='bold')
            ax.legend(loc='best', fontsize=10)
            ax.grid(True, alpha=0.3, linestyle='--')
            ax.set_xlim(5, 55)
            ax.set_xticks([10, 20, 30, 40, 50])
    
    plt.tight_layout()
    output_file = output_dir / 'paper_exp2_slr.png'
    plt.savefig(output_file, dpi=200, bbox_inches='tight')
    plt.close()
    print(f"✅ Salvato: {output_file}")
    
    # Figura 10: AVU vs VMs (2x2 subplots, uno per workflow)
    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    fig.suptitle('SM-CPTD: AVU vs Number of VMs (1000 tasks, CCR=1.0)', fontsize=14, fontweight='bold')
    
    for idx, wf_name in enumerate(workflow_order):
        ax = axes[idx // 2, idx % 2]
        
        if wf_name in organized_exp2:
            data = organized_exp2[wf_name]
            vms = data['vms']
            avu = [v * 100 for v in data['avu']]  # Converti in percentuale
            
            # Ordina per numero di VM
            sorted_pairs = sorted(zip(vms, avu))
            vms_sorted = [p[0] for p in sorted_pairs]
            avu_sorted = [p[1] for p in sorted_pairs]
            
            ax.plot(vms_sorted, avu_sorted,
                   color=colors.get(wf_name, '#000000'),
                   marker=markers.get(wf_name, 'o'),
                   linewidth=2.5, markersize=10,
                   label=f'SM-CPTD')
            
            ax.set_xlabel('Number of VMs', fontsize=11)
            ax.set_ylabel('AVU (%)', fontsize=11)
            ax.set_title(f'{wf_name}', fontsize=12, fontweight='bold')
            ax.legend(loc='best', fontsize=10)
            ax.grid(True, alpha=0.3, linestyle='--')
            ax.set_xlim(5, 55)
            ax.set_xticks([10, 20, 30, 40, 50])
            ax.set_ylim(0, 100)
    
    plt.tight_layout()
    output_file = output_dir / 'paper_exp2_avu.png'
    plt.savefig(output_file, dpi=200, bbox_inches='tight')
    plt.close()
    print(f"✅ Salvato: {output_file}")
    
    # Figura combinata: SLR e AVU affiancati per confronto
    fig, axes = plt.subplots(1, 2, figsize=(14, 5))
    fig.suptitle('SM-CPTD: VM Effect on Performance (1000 tasks, CCR=1.0)', fontsize=14, fontweight='bold')
    
    # SLR subplot
    ax = axes[0]
    for wf_name in workflow_order:
        if wf_name in organized_exp2:
            data = organized_exp2[wf_name]
            vms = data['vms']
            slr = data['slr']
            
            sorted_pairs = sorted(zip(vms, slr))
            vms_sorted = [p[0] for p in sorted_pairs]
            slr_sorted = [p[1] for p in sorted_pairs]
            
            ax.plot(vms_sorted, slr_sorted,
                   color=colors.get(wf_name, '#000000'),
                   marker=markers.get(wf_name, 'o'),
                   linewidth=2, markersize=8,
                   label=wf_name)
    
    ax.set_xlabel('Number of VMs', fontsize=11)
    ax.set_ylabel('SLR', fontsize=11)
    ax.set_title('Schedule Length Ratio', fontsize=12, fontweight='bold')
    ax.legend(loc='best', fontsize=9)
    ax.grid(True, alpha=0.3, linestyle='--')
    ax.set_xlim(5, 55)
    ax.set_xticks([10, 20, 30, 40, 50])
    
    # AVU subplot
    ax = axes[1]
    for wf_name in workflow_order:
        if wf_name in organized_exp2:
            data = organized_exp2[wf_name]
            vms = data['vms']
            avu = [v * 100 for v in data['avu']]
            
            sorted_pairs = sorted(zip(vms, avu))
            vms_sorted = [p[0] for p in sorted_pairs]
            avu_sorted = [p[1] for p in sorted_pairs]
            
            ax.plot(vms_sorted, avu_sorted,
                   color=colors.get(wf_name, '#000000'),
                   marker=markers.get(wf_name, 'o'),
                   linewidth=2, markersize=8,
                   label=wf_name)
    
    ax.set_xlabel('Number of VMs', fontsize=11)
    ax.set_ylabel('AVU (%)', fontsize=11)
    ax.set_title('Average VM Utilization', fontsize=12, fontweight='bold')
    ax.legend(loc='best', fontsize=9)
    ax.grid(True, alpha=0.3, linestyle='--')
    ax.set_xlim(5, 55)
    ax.set_xticks([10, 20, 30, 40, 50])
    ax.set_ylim(0, 100)
    
    plt.tight_layout()
    output_file = output_dir / 'paper_exp2_combined.png'
    plt.savefig(output_file, dpi=200, bbox_inches='tight')
    plt.close()
    print(f"✅ Salvato: {output_file}")


if __name__ == '__main__':
    main()
