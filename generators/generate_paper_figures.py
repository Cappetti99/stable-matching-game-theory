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
    output_path = Path('../assets') / output_filename
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    plt.savefig(str(output_path).replace('.png', '.pdf'), bbox_inches='tight')
    print(f"Saved: {output_path}")
    plt.show()

# ============================================================================
# FUNZIONI DI PLOTTING - FIGURA 6, 7, 8 (AVU vs CCR)
# ============================================================================

def plot_avu_vs_ccr(data, scale='small', output_filename='figure6_avu_vs_ccr.png'):
    """
    Genera Figura 6 (small), 7 (medium), o 8 (large): AVU vs CCR
    """
    fig, axes = plt.subplots(2, 2, figsize=(12, 10))
    fig.suptitle(f'Comparison of AVU in CCRs and {scale}-scale workflows', 
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
            avu_vals = [v * 100 for v in algo_data['AVU']]  # Converti in percentuale
            
            ax.plot(ccr_vals, avu_vals, 
                   label=style.get('label', algo_name),
                   color=style.get('color', None),
                   marker=style.get('marker', 'o'),
                   linewidth=style.get('linewidth', 1.5),
                   markersize=style.get('markersize', 7),
                   linestyle=style.get('linestyle', '-'),
                   zorder=style.get('zorder', 3))
        
        # Configurazione assi
        ax.set_xlabel('CCR', fontsize=11)
        ax.set_ylabel('AVU (%)', fontsize=11)
        ax.set_title(f'{WORKFLOW_TITLES[workflow]} ({tasks}×{vms})', fontsize=12, fontweight='bold')
        
        # Grid
        ax.grid(True, linestyle='--', alpha=0.3, color='gray', linewidth=0.5)
        
        # Legenda
        ax.legend(loc='upper right', fontsize=9, framealpha=0.9, 
                 edgecolor='black', fancybox=False)
        
        # Limiti assi
        ax.set_xlim(0.35, 2.05)
        ax.set_xticks(CCR_VALUES)
        ax.set_ylim(0, 100)  # AVU sempre 0-100%
    
    plt.tight_layout()
    output_path = Path('../assets') / output_filename
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    plt.savefig(str(output_path).replace('.png', '.pdf'), bbox_inches='tight')
    print(f"Saved: {output_path}")
    plt.show()

# ============================================================================
# FUNZIONE PRINCIPALE
# ============================================================================

def generate_all_figures():
    """Genera tutte le figure (3-8) dal paper"""
    
    print("="*70)
    print("GENERAZIONE FIGURE 3-8: SLR e AVU vs CCR")
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
    
    print("\n" + "="*70)
    print("✅ COMPLETATO! Tutti i grafici sono stati generati.")
    print("="*70)
    print("\nFile generati in assets/:")
    print("  - figure3_slr_vs_ccr_small.png (+ PDF)")
    print("  - figure4_slr_vs_ccr_medium.png (+ PDF)")
    print("  - figure5_slr_vs_ccr_large.png (+ PDF)")
    print("  - figure6_avu_vs_ccr_small.png (+ PDF)")
    print("  - figure7_avu_vs_ccr_medium.png (+ PDF)")
    print("  - figure8_avu_vs_ccr_large.png (+ PDF)")

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
    # Verifica e stampa info sui dati
    print_data_summary()
    data_complete = verify_data_completeness()
    
    # Genera tutte le figure
    if data_complete:
        generate_all_figures()
    else:
        print("\n⚠️  WARNING: Dati incompleti! Procedo comunque...")
        response = input("Continuare con la generazione dei grafici? (y/n): ")
        if response.lower() == 'y':
            generate_all_figures()
        else:
            print("Operazione annullata.")
