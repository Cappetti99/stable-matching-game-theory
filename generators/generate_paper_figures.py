import json
import pandas as pd
import matplotlib.pyplot as plt
import numpy as np
from pathlib import Path

# ============================================================================
# CONFIGURAZIONE
# ============================================================================

# Stili per gli algoritmi (quando avrai più algoritmi)
ALGO_STYLES = {
    'SM-CPTD': {'color': '#1f77b4', 'marker': 'o', 'linewidth': 2.5, 
                'markersize': 8, 'linestyle': '-', 'zorder': 5, 'label': 'SM-CPTD'},
    'TDA': {'color': '#2ca02c', 'marker': 's', 'linewidth': 1.5, 
            'markersize': 7, 'linestyle': '-', 'zorder': 3, 'label': 'TDA'},
    'GSS': {'color': '#ff7f0e', 'marker': '^', 'linewidth': 1.5, 
            'markersize': 7, 'linestyle': '-', 'zorder': 3, 'label': 'GSS'},
    'NMMWS': {'color': '#9467bd', 'marker': 'D', 'linewidth': 1.5, 
              'markersize': 7, 'linestyle': '-', 'zorder': 3, 'label': 'NMMWS'},
    'Min-Min': {'color': '#7f7f7f', 'marker': '*', 'linewidth': 1.5, 
                'markersize': 9, 'linestyle': '--', 'zorder': 2, 'label': 'Min-Min'}
}

WORKFLOW_ORDER = ['montage', 'cybershake', 'ligo', 'epigenomics']
WORKFLOW_TITLES = {
    'montage': 'Montage',
    'cybershake': 'CyberShake', 
    'ligo': 'LIGO',
    'epigenomics': 'Epigenomics'
}

CCR_VALUES = [0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0]

# ============================================================================
# FUNZIONI DI CARICAMENTO DATI
# ============================================================================

def load_experiments_data(filepath='../results/experiments_results.json'):
    """Carica dati da experiments_results.json"""
    with open(filepath, 'r') as f:
        data = json.load(f)
    return pd.DataFrame(data['experiments'])

def load_ccr_analysis(workflow_type):
    """Carica dati da ccr_analysis_results_{workflow}.json"""
    filepath = f'../algorithms/ccr_analysis_results_{workflow_type}.json'
    try:
        with open(filepath, 'r') as f:
            data = json.load(f)
        return pd.DataFrame(data['results'])
    except FileNotFoundError:
        print(f"Warning: {filepath} not found")
        return None

def prepare_data_for_plotting(experiment_name='EXP1_SMALL', algorithm='SM-CPTD'):
    """
    Prepara dati per plotting in formato richiesto
    
    Returns:
        dict strutturato come:
        {
            'montage': {
                'SM-CPTD': {'CCR': [...], 'SLR': [...], 'AVU': [...]},
                'info': {'tasks': 50, 'vms': 5}
            },
            ...
        }
    """
    # Carica dati principali
    df = load_experiments_data()
    
    # Filtra per esperimento
    df_filtered = df[df['experiment'] == experiment_name].copy()
    
    # Organizza per workflow
    results = {}
    
    for workflow in WORKFLOW_ORDER:
        workflow_data = df_filtered[df_filtered['workflow'] == workflow]
        
        if len(workflow_data) == 0:
            print(f"Warning: No data for {workflow} in {experiment_name}")
            continue
        
        # Ordina per CCR
        workflow_data = workflow_data.sort_values('ccr')
        
        # Estrai info su tasks e VMs (primo record, sono costanti per workflow/experiment)
        tasks = int(workflow_data['tasks'].iloc[0])
        vms = int(workflow_data['vms'].iloc[0])
        
        results[workflow] = {
            algorithm: {
                'CCR': workflow_data['ccr'].tolist(),
                'SLR': workflow_data['slr'].tolist(),
                'AVU': workflow_data['avu'].tolist(),
                'VF': workflow_data['vf'].tolist(),
                'MAKESPAN': workflow_data['makespan'].tolist()
            },
            'info': {'tasks': tasks, 'vms': vms}
        }
    
    return results

# ============================================================================
# FUNZIONI DI PLOTTING - FIGURA 3, 4, 5 (SLR vs CCR)
# ============================================================================

def plot_slr_vs_ccr(data, scale='small', output_filename='figure3_slr_vs_ccr.png'):
    """
    Genera Figura 3 (small), 4 (medium), o 5 (large): SLR vs CCR
    
    Args:
        data: dict con struttura {workflow: {algorithm: {'CCR': [...], 'SLR': [...]}}}
        scale: 'small', 'medium', o 'large'
        output_filename: nome file output
    """
    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    fig.suptitle(f'Comparison of SLR in CCRs and {scale}-scale workflows', 
                 fontsize=14, fontweight='bold')
    
    for idx, (ax, workflow) in enumerate(zip(axes.flat, WORKFLOW_ORDER)):
        if workflow not in data:
            print(f"Warning: {workflow} not in data")
            continue
            
        workflow_results = data[workflow]
        
        # Estrai info su tasks e VMs
        info = workflow_results.get('info', {})
        tasks = info.get('tasks', '?')
        vms = info.get('vms', '?')
        
        # Plot per ogni algoritmo
        for algo_name, algo_data in workflow_results.items():
            if algo_name == 'info':  # Salta il campo info
                continue
            
            style = ALGO_STYLES.get(algo_name, {})
            
            ccr_vals = algo_data['CCR']
            slr_vals = algo_data['SLR']
            
            ax.plot(ccr_vals, slr_vals, 
                   label=style.get('label', algo_name),
                   color=style.get('color', None),
                   marker=style.get('marker', 'o'),
                   linewidth=style.get('linewidth', 1.5),
                   markersize=style.get('markersize', 7),
                   linestyle=style.get('linestyle', '-'),
                   zorder=style.get('zorder', 3))
        
        # Configurazione assi
        ax.set_xlabel('CCR', fontsize=11)
        ax.set_ylabel('SLR', fontsize=11)
        ax.set_title(f'{WORKFLOW_TITLES[workflow]} ({tasks}×{vms})', fontsize=12, fontweight='bold')
        
        # Grid
        ax.grid(True, linestyle='--', alpha=0.3, color='gray', linewidth=0.5)
        
        # Legenda
        ax.legend(loc='upper left', fontsize=9, framealpha=0.9, 
                 edgecolor='black', fancybox=False)
        
        # Limiti assi
        ax.set_xlim(0.35, 2.05)
        ax.set_xticks(CCR_VALUES)
        
        # Auto-scale Y ma con margine
        if len(slr_vals) > 0:
            y_min = min([min(algo_data['SLR']) for key, algo_data in workflow_results.items() if key != 'info'])
            y_max = max([max(algo_data['SLR']) for key, algo_data in workflow_results.items() if key != 'info'])
            margin = (y_max - y_min) * 0.1
            ax.set_ylim(y_min - margin, y_max + margin)
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"Saved: {output_path}")
    plt.close()

# ============================================================================
# FUNZIONI DI PLOTTING - FIGURA 6, 7, 8 (AVU vs CCR)
# ============================================================================

def plot_avu_vs_ccr(data, scale='small', output_filename='figure6_avu_vs_ccr.png'):
    """
    Genera Figura 6 (small), 7 (medium), o 8 (large): AVU vs CCR
    ENHANCED: Uses adaptive Y-axis scaling to highlight AVU decreasing trend
    """
    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    fig.suptitle(f'AVU vs CCR for {scale}-scale workflows (decreasing trend with communication overhead)', 
                 fontsize=13, fontweight='bold')
    
    for idx, (ax, workflow) in enumerate(zip(axes.flat, WORKFLOW_ORDER)):
        if workflow not in data:
            continue
            
        workflow_results = data[workflow]
        
        # Estrai info su tasks e VMs
        info = workflow_results.get('info', {})
        tasks = info.get('tasks', '?')
        vms = info.get('vms', '?')
        
        # Collect all AVU values for this workflow to determine Y-axis range
        all_avu_vals = []
        
        # Plot per ogni algoritmo
        for algo_name, algo_data in workflow_results.items():
            if algo_name == 'info':  # Salta il campo info
                continue
            
            style = ALGO_STYLES.get(algo_name, {})
            
            ccr_vals = algo_data['CCR']
            avu_vals = [v * 100 for v in algo_data['AVU']]  # Converti in percentuale
            all_avu_vals.extend(avu_vals)
            
            ax.plot(ccr_vals, avu_vals, 
                   label=style.get('label', algo_name),
                   color=style.get('color', None),
                   marker=style.get('marker', 'o'),
                   linewidth=style.get('linewidth', 1.5),
                   markersize=style.get('markersize', 7),
                   linestyle=style.get('linestyle', '-'),
                   zorder=style.get('zorder', 3))
            
            # Add trend annotation for SM-CPTD
            if algo_name == 'SM-CPTD' and len(avu_vals) >= 2:
                avu_start = avu_vals[0]
                avu_end = avu_vals[-1]
                decrease_pct = ((avu_end - avu_start) / avu_start) * 100
                
                # Add annotation showing the decrease
                ax.annotate(f'{decrease_pct:.1f}%', 
                           xy=(ccr_vals[-1], avu_end),
                           xytext=(10, -5),
                           textcoords='offset points',
                           fontsize=9,
                           color=style.get('color', 'black'),
                           weight='bold',
                           bbox=dict(boxstyle='round,pad=0.3', 
                                   facecolor='white', 
                                   edgecolor=style.get('color', 'black'),
                                   alpha=0.8))
        
        # Configurazione assi
        ax.set_xlabel('CCR', fontsize=11)
        ax.set_ylabel('AVU (%)', fontsize=11)
        ax.set_title(f'{WORKFLOW_TITLES[workflow]} ({tasks}×{vms})', 
                    fontsize=12, fontweight='bold')
        
        # Grid
        ax.grid(True, linestyle='--', alpha=0.3, color='gray', linewidth=0.5)
        
        # Legenda
        ax.legend(loc='upper right', fontsize=9, framealpha=0.9, 
                 edgecolor='black', fancybox=False)
        
        # ADAPTIVE Y-axis scaling: show full range but with padding to highlight trend
        if all_avu_vals:
            min_avu = min(all_avu_vals)
            max_avu = max(all_avu_vals)
            avu_range = max_avu - min_avu
            
            # Add 20% padding above and below for clarity
            y_margin = max(avu_range * 0.2, 1.0)  # At least 1% margin
            y_min = max(0, min_avu - y_margin)
            y_max = min(100, max_avu + y_margin)
            
            ax.set_ylim(y_min, y_max)
            
            # Add horizontal reference line at max AVU
            ax.axhline(y=max_avu, color='gray', linestyle=':', alpha=0.3, linewidth=1)
            ax.axhline(y=min_avu, color='gray', linestyle=':', alpha=0.3, linewidth=1)
        
        # X-axis configuration
        ax.set_xlim(0.35, 2.05)
        ax.set_xticks(CCR_VALUES)
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"Saved: {output_path}")
    plt.close()

# ============================================================================
# FUNZIONI DI PLOTTING - FIGURA AVU (Allocation VM Usage)
# ============================================================================

def plot_vf_vs_ccr(data, scale='large', output_filename='figure_vf_large.png'):
    """
    Genera grafico VF (Violation Frequency) vs CCR per large workflows
    """
    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    fig.suptitle(f'Comparison of VF in different {scale}-scale workflows', 
                 fontsize=14, fontweight='bold')
    
    for idx, (ax, workflow) in enumerate(zip(axes.flat, WORKFLOW_ORDER)):
        if workflow not in data:
            continue
            
        workflow_results = data[workflow]
        
        # Estrai info su tasks e VMs
        info = workflow_results.get('info', {})
        tasks = info.get('tasks', '?')
        vms = info.get('vms', '?')
        
        # Plot per ogni algoritmo
        for algo_name, algo_data in workflow_results.items():
            if algo_name == 'info':  # Salta il campo info
                continue
            
            style = ALGO_STYLES.get(algo_name, {})
            
            ccr_vals = algo_data['CCR']
            vf_vals = algo_data['VF']
            
            ax.plot(ccr_vals, vf_vals, 
                   label=style.get('label', algo_name),
                   color=style.get('color', None),
                   marker=style.get('marker', 'o'),
                   linewidth=style.get('linewidth', 1.5),
                   markersize=style.get('markersize', 7),
                   linestyle=style.get('linestyle', '-'),
                   zorder=style.get('zorder', 3))
        
        # Configurazione assi
        ax.set_xlabel('CCR', fontsize=11)
        ax.set_ylabel('VF', fontsize=11)
        ax.set_title(f'{WORKFLOW_TITLES[workflow]} ({tasks}×{vms})', fontsize=12, fontweight='bold')
        
        # Grid
        ax.grid(True, linestyle='--', alpha=0.3, color='gray', linewidth=0.5)
        
        # Legenda
        ax.legend(loc='best', fontsize=9, framealpha=0.9, 
                 edgecolor='black', fancybox=False)
        
        # Limiti assi
        ax.set_xlim(0.35, 2.05)
        ax.set_xticks(CCR_VALUES)
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"Saved: {output_path}")
    plt.close()

# ============================================================================
# FUNZIONI DI PLOTTING - FIGURA 9, 10 (VM Effect - Experiment 2)
# ============================================================================

def prepare_exp2_data(algorithm='SM-CPTD'):
    """
    Prepara dati Esperimento 2 (VM Effect) per plotting
    
    Returns:
        dict strutturato come:
        {
            'montage': {
                'SM-CPTD': {'VMs': [...], 'SLR': [...], 'AVU': [...]},
                'info': {'tasks': 1000, 'ccr': 1.0}
            },
            ...
        }
    """
    # Carica dati principali
    df = load_experiments_data()
    
    # Filtra per EXP2_VM
    df_filtered = df[df['experiment'] == 'EXP2_VM'].copy()
    
    # Organizza per workflow
    results = {}
    
    for workflow in WORKFLOW_ORDER:
        workflow_data = df_filtered[df_filtered['workflow'] == workflow]
        
        if len(workflow_data) == 0:
            print(f"Warning: No EXP2 data for {workflow}")
            continue
        
        # Ordina per numero di VM
        workflow_data = workflow_data.sort_values('vms')
        
        # Estrai info su tasks e CCR (primo record, sono costanti per workflow in EXP2)
        tasks = int(workflow_data['tasks'].iloc[0])
        ccr = float(workflow_data['ccr'].iloc[0])
        
        results[workflow] = {
            algorithm: {
                'VMs': workflow_data['vms'].tolist(),
                'SLR': workflow_data['slr'].tolist(),
                'AVU': workflow_data['avu'].tolist(),
                'VF': workflow_data['vf'].tolist(),
                'MAKESPAN': workflow_data['makespan'].tolist()
            },
            'info': {'tasks': tasks, 'ccr': ccr}
        }
    
    return results

def plot_slr_vs_vms(data, output_filename='figure9_slr_vs_vms.png'):
    """
    Genera Figura 9: SLR vs VM Count (Experiment 2)
    
    Args:
        data: dict con struttura {workflow: {algorithm: {'VMs': [...], 'SLR': [...]}}}
        output_filename: nome file output
    """
    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    fig.suptitle('Comparison of SLR with different VM counts (CCR=1.0)', 
                 fontsize=14, fontweight='bold')
    
    for idx, (ax, workflow) in enumerate(zip(axes.flat, WORKFLOW_ORDER)):
        if workflow not in data:
            print(f"Warning: {workflow} not in data")
            continue
            
        workflow_results = data[workflow]
        
        # Estrai info su tasks e CCR
        info = workflow_results.get('info', {})
        tasks = info.get('tasks', '?')
        ccr = info.get('ccr', '?')
        
        # Plot per ogni algoritmo
        for algo_name, algo_data in workflow_results.items():
            if algo_name == 'info':  # Salta il campo info
                continue
            
            style = ALGO_STYLES.get(algo_name, {})
            
            vms_vals = algo_data['VMs']
            slr_vals = algo_data['SLR']
            
            ax.plot(vms_vals, slr_vals, 
                   label=style.get('label', algo_name),
                   color=style.get('color', None),
                   marker=style.get('marker', 'o'),
                   linewidth=style.get('linewidth', 1.5),
                   markersize=style.get('markersize', 7),
                   linestyle=style.get('linestyle', '-'),
                   zorder=style.get('zorder', 3))
        
        # Configurazione assi
        ax.set_xlabel('Number of VMs', fontsize=11)
        ax.set_ylabel('SLR', fontsize=11)
        ax.set_title(f'{WORKFLOW_TITLES[workflow]} ({tasks} tasks, CCR={ccr})', 
                    fontsize=12, fontweight='bold')
        
        # Grid
        ax.grid(True, linestyle='--', alpha=0.3, color='gray', linewidth=0.5)
        
        # Legenda
        ax.legend(loc='best', fontsize=9, framealpha=0.9, 
                 edgecolor='black', fancybox=False)
        
        # Limiti assi
        if len(vms_vals) > 0:
            vm_min = min(vms_vals)
            vm_max = max(vms_vals)
            ax.set_xlim(vm_min - 2, vm_max + 2)
            ax.set_xticks(vms_vals)
            
            # Auto-scale Y ma con margine
            y_min = min([min(algo_data['SLR']) for key, algo_data in workflow_results.items() if key != 'info'])
            y_max = max([max(algo_data['SLR']) for key, algo_data in workflow_results.items() if key != 'info'])
            margin = (y_max - y_min) * 0.1
            ax.set_ylim(y_min - margin, y_max + margin)
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"Saved: {output_path}")
    plt.close()

def plot_vf_vs_vms(data, output_filename='figure_vf_vs_vms.png'):
    """
    Genera grafico VF vs VM Count (Experiment 2)
    
    Args:
        data: dict con struttura {workflow: {algorithm: {'VMs': [...], 'VF': [...]}}}
        output_filename: nome file output
    """
    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    fig.suptitle('Comparison of VF with different VM counts (CCR=1.0)', 
                 fontsize=14, fontweight='bold')
    
    for idx, (ax, workflow) in enumerate(zip(axes.flat, WORKFLOW_ORDER)):
        if workflow not in data:
            continue
            
        workflow_results = data[workflow]
        
        # Estrai info su tasks e CCR
        info = workflow_results.get('info', {})
        tasks = info.get('tasks', '?')
        ccr = info.get('ccr', '?')
        
        # Plot per ogni algoritmo
        for algo_name, algo_data in workflow_results.items():
            if algo_name == 'info':  # Salta il campo info
                continue
            
            style = ALGO_STYLES.get(algo_name, {})
            
            vms_vals = algo_data['VMs']
            vf_vals = algo_data['VF']
            
            ax.plot(vms_vals, vf_vals, 
                   label=style.get('label', algo_name),
                   color=style.get('color', None),
                   marker=style.get('marker', 'o'),
                   linewidth=style.get('linewidth', 1.5),
                   markersize=style.get('markersize', 7),
                   linestyle=style.get('linestyle', '-'),
                   zorder=style.get('zorder', 3))
        
        # Configurazione assi
        ax.set_xlabel('Number of VMs', fontsize=11)
        ax.set_ylabel('VF', fontsize=11)
        ax.set_title(f'{WORKFLOW_TITLES[workflow]} ({tasks} tasks, CCR={ccr})', 
                    fontsize=12, fontweight='bold')
        
        # Grid
        ax.grid(True, linestyle='--', alpha=0.3, color='gray', linewidth=0.5)
        
        # Legenda
        ax.legend(loc='best', fontsize=9, framealpha=0.9, 
                 edgecolor='black', fancybox=False)
        
        # Limiti assi
        if len(vms_vals) > 0:
            vm_min = min(vms_vals)
            vm_max = max(vms_vals)
            ax.set_xlim(vm_min - 2, vm_max + 2)
            ax.set_xticks(vms_vals)
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"Saved: {output_path}")
    plt.close()

def plot_avu_vs_vms(data, output_filename='figure10_avu_vs_vms.png'):
    """
    Genera Figura 10: AVU vs VM Count (Experiment 2)
    
    Args:
        data: dict con struttura {workflow: {algorithm: {'VMs': [...], 'AVU': [...]}}}
        output_filename: nome file output
    """
    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    fig.suptitle('Comparison of AVU with different VM counts (CCR=1.0)', 
                 fontsize=14, fontweight='bold')
    
    for idx, (ax, workflow) in enumerate(zip(axes.flat, WORKFLOW_ORDER)):
        if workflow not in data:
            continue
            
        workflow_results = data[workflow]
        
        # Estrai info su tasks e CCR
        info = workflow_results.get('info', {})
        tasks = info.get('tasks', '?')
        ccr = info.get('ccr', '?')
        
        # Plot per ogni algoritmo
        for algo_name, algo_data in workflow_results.items():
            if algo_name == 'info':  # Salta il campo info
                continue
            
            style = ALGO_STYLES.get(algo_name, {})
            
            vms_vals = algo_data['VMs']
            avu_vals = [v * 100 for v in algo_data['AVU']]  # Converti in percentuale
            
            ax.plot(vms_vals, avu_vals, 
                   label=style.get('label', algo_name),
                   color=style.get('color', None),
                   marker=style.get('marker', 'o'),
                   linewidth=style.get('linewidth', 1.5),
                   markersize=style.get('markersize', 7),
                   linestyle=style.get('linestyle', '-'),
                   zorder=style.get('zorder', 3))
        
        # Configurazione assi
        ax.set_xlabel('Number of VMs', fontsize=11)
        ax.set_ylabel('AVU (%)', fontsize=11)
        ax.set_title(f'{WORKFLOW_TITLES[workflow]} ({tasks} tasks, CCR={ccr})', 
                    fontsize=12, fontweight='bold')
        
        # Grid
        ax.grid(True, linestyle='--', alpha=0.3, color='gray', linewidth=0.5)
        
        # Legenda
        ax.legend(loc='best', fontsize=9, framealpha=0.9, 
                 edgecolor='black', fancybox=False)
        
        # Limiti assi
        if len(vms_vals) > 0:
            vm_min = min(vms_vals)
            vm_max = max(vms_vals)
            ax.set_xlim(vm_min - 2, vm_max + 2)
            ax.set_xticks(vms_vals)
            
            # AVU sempre 0-100% ma con auto-scale più preciso
            if len(avu_vals) > 0:
                y_max = max([max([v * 100 for v in algo_data['AVU']]) 
                            for key, algo_data in workflow_results.items() if key != 'info'])
                # Usa range dinamico ma almeno fino a 10%
                ax.set_ylim(0, max(y_max * 1.2, 10))
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"Saved: {output_path}")
    plt.close()

# ============================================================================
# FUNZIONE COMPARATIVA - SLR, AVU, VF per diversi metodi
# ============================================================================

def plot_metrics_comparison(data, scale='large', output_filename='figure_metrics_comparison.png'):
    """
    Genera grafico comparativo di SLR, AVU e VF per diversi algoritmi
    Mostra 4 workflow, ognuno con 3 subplot (SLR, AVU, VF)
    """
    fig, axes = plt.subplots(4, 3, figsize=(15, 14))
    fig.suptitle(f'Comparison of SLR, AVU and VF for different methods ({scale}-scale)', 
                 fontsize=15, fontweight='bold')
    
    for row_idx, workflow in enumerate(WORKFLOW_ORDER):
        if workflow not in data:
            continue
            
        workflow_results = data[workflow]
        info = workflow_results.get('info', {})
        tasks = info.get('tasks', '?')
        vms = info.get('vms', '?')
        
        # SLR subplot (colonna 0)
        ax_slr = axes[row_idx, 0]
        # AVU subplot (colonna 1)
        ax_avu = axes[row_idx, 1]
        # VF subplot (colonna 2)
        ax_vf = axes[row_idx, 2]
        
        # Plot per ogni algoritmo
        for algo_name, algo_data in workflow_results.items():
            if algo_name == 'info':
                continue
            
            style = ALGO_STYLES.get(algo_name, {})
            ccr_vals = algo_data['CCR']
            
            # SLR
            ax_slr.plot(ccr_vals, algo_data['SLR'], 
                       label=style.get('label', algo_name),
                       color=style.get('color', None),
                       marker=style.get('marker', 'o'),
                       linewidth=style.get('linewidth', 1.5),
                       markersize=style.get('markersize', 7),
                       linestyle=style.get('linestyle', '-'),
                       zorder=style.get('zorder', 3))
            
            # AVU (in percentuale)
            avu_percent = [v * 100 for v in algo_data['AVU']]
            ax_avu.plot(ccr_vals, avu_percent, 
                       label=style.get('label', algo_name),
                       color=style.get('color', None),
                       marker=style.get('marker', 'o'),
                       linewidth=style.get('linewidth', 1.5),
                       markersize=style.get('markersize', 7),
                       linestyle=style.get('linestyle', '-'),
                       zorder=style.get('zorder', 3))
            
            # VF
            ax_vf.plot(ccr_vals, algo_data['VF'], 
                      label=style.get('label', algo_name),
                      color=style.get('color', None),
                      marker=style.get('marker', 'o'),
                      linewidth=style.get('linewidth', 1.5),
                      markersize=style.get('markersize', 7),
                      linestyle=style.get('linestyle', '-'),
                      zorder=style.get('zorder', 3))
        
        # Configurazione SLR
        ax_slr.set_ylabel(f'{WORKFLOW_TITLES[workflow]} ({tasks}×{vms})\nSLR', fontsize=11, fontweight='bold')
        ax_slr.grid(True, linestyle='--', alpha=0.3, color='gray', linewidth=0.5)
        ax_slr.set_xlim(0.35, 2.05)
        ax_slr.set_xticks(CCR_VALUES)
        if row_idx == 0:
            ax_slr.set_title('SLR', fontsize=12, fontweight='bold')
        if row_idx == 3:
            ax_slr.set_xlabel('CCR', fontsize=11)
        else:
            ax_slr.set_xticklabels([])
        
        # Configurazione AVU - ADAPTIVE SCALING to show trend
        ax_avu.set_ylabel('AVU (%)', fontsize=11)
        ax_avu.grid(True, linestyle='--', alpha=0.3, color='gray', linewidth=0.5)
        ax_avu.set_xlim(0.35, 2.05)
        ax_avu.set_xticks(CCR_VALUES)
        
        # Collect all AVU values for this workflow
        all_avu_percent = []
        for algo_name, algo_data in workflow_results.items():
            if algo_name != 'info':
                all_avu_percent.extend([v * 100 for v in algo_data['AVU']])
        
        # Use adaptive Y-axis to highlight decreasing trend
        if all_avu_percent:
            min_avu = min(all_avu_percent)
            max_avu = max(all_avu_percent)
            avu_range = max_avu - min_avu
            y_margin = max(avu_range * 0.2, 1.0)
            y_min = max(0, min_avu - y_margin)
            y_max = min(100, max_avu + y_margin)
            ax_avu.set_ylim(y_min, y_max)
        
        if row_idx == 0:
            ax_avu.set_title('AVU (adaptive scale)', fontsize=12, fontweight='bold')
        if row_idx == 3:
            ax_avu.set_xlabel('CCR', fontsize=11)
        else:
            ax_avu.set_xticklabels([])
        
        # Configurazione VF
        ax_vf.set_ylabel('VF', fontsize=11)
        ax_vf.grid(True, linestyle='--', alpha=0.3, color='gray', linewidth=0.5)
        ax_vf.set_xlim(0.35, 2.05)
        ax_vf.set_xticks(CCR_VALUES)
        if row_idx == 0:
            ax_vf.set_title('VF', fontsize=12, fontweight='bold')
        if row_idx == 3:
            ax_vf.set_xlabel('CCR', fontsize=11)
        else:
            ax_vf.set_xticklabels([])
        
        # Legenda solo nella prima riga
        if row_idx == 0:
            ax_slr.legend(loc='best', fontsize=9, framealpha=0.9, 
                         edgecolor='black', fancybox=False)
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    print(f"Saved: {output_path}")
    plt.close()

# ============================================================================
# FUNZIONE PRINCIPALE
# ============================================================================

def generate_all_figures():
    """Genera tutte le figure (3-8) dal paper"""
    
    print("="*70)
    print("GENERAZIONE FIGURE 3-10: SLR, AVU vs CCR e VM Count")
    print("="*70)
    
    # SMALL WORKFLOWS (Figure 3 e 6)
    print("\n[1/3] Processing SMALL workflows...")
    data_small = prepare_data_for_plotting('EXP1_SMALL', 'SM-CPTD')
    
    print("  → Generating Figure 3 (SLR vs CCR - Small)...")
    plot_slr_vs_ccr(data_small, scale='small', 
                    output_filename='figure3_slr_vs_ccr_small.png')
    
    print("  → Generating Figure 6 (AVU vs CCR - Small)...")
    plot_avu_vs_ccr(data_small, scale='small', 
                    output_filename='figure6_avu_vs_ccr_small.png')
    
    # MEDIUM WORKFLOWS (Figure 4 e 7)
    print("\n[2/3] Processing MEDIUM workflows...")
    data_medium = prepare_data_for_plotting('EXP1_MEDIUM', 'SM-CPTD')
    
    print("  → Generating Figure 4 (SLR vs CCR - Medium)...")
    plot_slr_vs_ccr(data_medium, scale='medium', 
                    output_filename='figure4_slr_vs_ccr_medium.png')
    
    print("  → Generating Figure 7 (AVU vs CCR - Medium)...")
    plot_avu_vs_ccr(data_medium, scale='medium', 
                    output_filename='figure7_avu_vs_ccr_medium.png')
    
    # LARGE WORKFLOWS (Figure 5 e 8)
    print("\n[3/3] Processing LARGE workflows...")
    data_large = prepare_data_for_plotting('EXP1_LARGE', 'SM-CPTD')
    
    print("  → Generating Figure 5 (SLR vs CCR - Large)...")
    plot_slr_vs_ccr(data_large, scale='large', 
                    output_filename='figure5_slr_vs_ccr_large.png')
    
    print("  → Generating Figure 8 (AVU vs CCR - Large)...")
    plot_avu_vs_ccr(data_large, scale='large', 
                    output_filename='figure8_avu_vs_ccr_large.png')
    
    print("  → Generating VF Comparison (Large)...")
    plot_vf_vs_ccr(data_large, scale='large', 
                   output_filename='figure_vf_large.png')
    
    print("  → Generating Metrics Comparison (SLR, AVU, VF - Large)...")
    plot_metrics_comparison(data_large, scale='large', 
                           output_filename='figure_metrics_comparison_large.png')
    
    # EXPERIMENT 2: VM Effect (Figures 9-10)
    print("\n[4/4] Processing EXPERIMENT 2 (VM Effect)...")
    data_exp2 = prepare_exp2_data('SM-CPTD')
    
    if data_exp2:
        print("  → Generating Figure 9 (SLR vs VM Count)...")
        plot_slr_vs_vms(data_exp2, output_filename='figure9_slr_vs_vms.png')
        
        print("  → Generating Figure 10 (AVU vs VM Count)...")
        plot_avu_vs_vms(data_exp2, output_filename='figure10_avu_vs_vms.png')
        
        print("  → Generating VF vs VM Count...")
        plot_vf_vs_vms(data_exp2, output_filename='figure_vf_vs_vms.png')
    else:
        print("  ⚠️  No EXP2_VM data found, skipping Figures 9-10")
    
    print("\n" + "="*70)
    print("✅ COMPLETATO! Tutti i grafici sono stati generati.")
    print("="*70)
    print("\nFile generati in results/figures/:")
    print("  - figure3_slr_vs_ccr_small.png")
    print("  - figure4_slr_vs_ccr_medium.png")
    print("  - figure5_slr_vs_ccr_large.png")
    print("  - figure6_avu_vs_ccr_small.png")
    print("  - figure7_avu_vs_ccr_medium.png")
    print("  - figure8_avu_vs_ccr_large.png")
    print("  - figure9_slr_vs_vms.png")
    print("  - figure10_avu_vs_vms.png")
    print("  - figure_vf_vs_vms.png")
    print("  - figure_vf_large.png")
    print("  - figure_metrics_comparison_large.png")

# ============================================================================
# FUNZIONI AGGIUNTIVE - STATISTICHE E VERIFICA
# ============================================================================

def print_data_summary():
    """Stampa un riepilogo dei dati caricati"""
    df = load_experiments_data()
    
    print("\n" + "="*70)
    print("RIEPILOGO DATI")
    print("="*70)
    
    print(f"\nTotale esperimenti: {len(df)}")
    print(f"\nEsperimenti per scala:")
    print(df['experiment'].value_counts())
    
    print(f"\nWorkflow disponibili:")
    print(df['workflow'].value_counts())
    
    print(f"\nConfigurazione VM per scala:")
    for exp in df['experiment'].unique():
        vms = df[df['experiment'] == exp]['vms'].iloc[0]
        tasks_range = df[df['experiment'] == exp]['tasks'].agg(['min', 'max'])
        print(f"  {exp}: {vms} VMs, {tasks_range['min']}-{tasks_range['max']} tasks")
    
    print(f"\nValori CCR testati:")
    print(sorted(df['ccr'].unique()))
    
    print(f"\nRange metriche:")
    print(f"  SLR: {df['slr'].min():.2f} - {df['slr'].max():.2f}")
    print(f"  AVU: {df['avu'].min():.2%} - {df['avu'].max():.2%}")
    print(f"  VF: {df['vf'].min():.6f} - {df['vf'].max():.6f} (con NaN)")

def verify_data_completeness():
    """Verifica che ci siano tutti i dati necessari per le figure"""
    df = load_experiments_data()
    
    print("\n" + "="*70)
    print("VERIFICA COMPLETEZZA DATI")
    print("="*70)
    
    expected_experiments = ['EXP1_SMALL', 'EXP1_MEDIUM', 'EXP1_LARGE']
    expected_workflows = ['montage', 'cybershake', 'ligo', 'epigenomics']
    expected_ccr = [0.4, 0.6, 0.8, 1.0, 1.2, 1.4, 1.6, 1.8, 2.0]
    
    all_complete = True
    
    for exp in expected_experiments:
        print(f"\n{exp}:")
        for workflow in expected_workflows:
            subset = df[(df['experiment'] == exp) & (df['workflow'] == workflow)]
            ccr_count = len(subset)
            expected_count = len(expected_ccr)
            
            if ccr_count == expected_count:
                status = "✅"
            else:
                status = "❌"
                all_complete = False
            
            print(f"  {status} {workflow}: {ccr_count}/{expected_count} punti CCR")
            
            if ccr_count < expected_count:
                missing_ccr = set(expected_ccr) - set(subset['ccr'].tolist())
                print(f"      Missing CCR: {sorted(missing_ccr)}")
    
    if all_complete:
        print("\n✅ Tutti i dati necessari sono presenti!")
    else:
        print("\n⚠️  Alcuni dati mancano. I grafici saranno incompleti.")
    
    return all_complete

# ============================================================================
# ESECUZIONE
# ============================================================================

if __name__ == '__main__':
    import sys
    
    # Verifica e stampa info sui dati
    print_data_summary()
    data_complete = verify_data_completeness()
    
    # Check if running in non-interactive mode (from Java)
    auto_mode = '--auto' in sys.argv or not sys.stdin.isatty()
    
    # Genera tutte le figure
    if data_complete or auto_mode:
        generate_all_figures()
    else:
        print("\n⚠️  WARNING: Dati incompleti! Procedo comunque...")
        response = input("Continuare con la generazione dei grafici? (y/n): ")
        if response.lower() == 'y':
            generate_all_figures()
        else:
            print("Operazione annullata.")
