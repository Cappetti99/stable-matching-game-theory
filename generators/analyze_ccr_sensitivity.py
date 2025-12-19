#!/usr/bin/env python3
"""
CCR Sensitivity Analysis and Visualization

Analyzes CCR (Communication-to-Computation Ratio) sensitivity reports generated
by CCRAnalyzer.java and creates comprehensive visualizations.

Features:
1. Communication cost distribution analysis
2. Critical Path stability heatmaps
3. Task duplication sensitivity plots
4. Metrics elasticity comparisons
5. Comprehensive sensitivity scorecards

Author: Lorenzo Cappetti
Version: 1.0
"""

import json
import glob
import os
from pathlib import Path
import matplotlib.pyplot as plt
import matplotlib.patches as mpatches
import numpy as np
from typing import Dict, List, Optional

# ============================================================================
# CONFIGURATION
# ============================================================================

WORKFLOW_TITLES = {
    'montage': 'Montage',
    'cybershake': 'CyberShake',
    'ligo': 'LIGO',
    'epigenomics': 'Epigenomics'
}

WORKFLOW_ORDER = ['montage', 'cybershake', 'ligo', 'epigenomics']

EXPERIMENT_TITLES = {
    'exp1_small': 'Small Scale',
    'exp1_medium': 'Medium Scale',
    'exp1_large': 'Large Scale'
}

# Colors
COLORS = {
    'montage': '#2E86AB',
    'cybershake': '#A23B72',
    'ligo': '#F18F01',
    'epigenomics': '#C73E1D'
}

# ============================================================================
# DATA LOADING
# ============================================================================

def load_ccr_analysis(workflow: str, experiment: str = 'exp1_small') -> Optional[Dict]:
    """
    Load CCR sensitivity analysis for a workflow
    
    Args:
        workflow: Workflow name (montage, cybershake, ligo, epigenomics)
        experiment: Experiment type (exp1_small, exp1_medium, exp1_large)
    
    Returns:
        Dictionary with analysis data or None if not found
    """
    filepath = f'../results/ccr_sensitivity/{workflow}_{experiment}_analysis.json'
    
    try:
        with open(filepath, 'r') as f:
            data = json.load(f)
        return data
    except FileNotFoundError:
        print(f"⚠️  File not found: {filepath}")
        return None
    except json.JSONDecodeError as e:
        print(f"❌ JSON decode error in {filepath}: {e}")
        return None

def load_all_workflows(experiment: str = 'exp1_small') -> Dict[str, Dict]:
    """
    Load CCR analysis for all workflows in an experiment
    
    Returns:
        Dictionary mapping workflow name to analysis data
    """
    results = {}
    
    for workflow in WORKFLOW_ORDER:
        data = load_ccr_analysis(workflow, experiment)
        if data:
            results[workflow] = data
    
    return results

def find_available_analyses() -> List[str]:
    """
    Find all available CCR sensitivity analysis files
    
    Returns:
        List of available analysis file paths
    """
    pattern = '../results/ccr_sensitivity/*_analysis.json'
    files = glob.glob(pattern)
    return sorted(files)

# ============================================================================
# PLOT 1: COMMUNICATION COST DISTRIBUTION
# ============================================================================

def plot_comm_cost_distribution(data_dict: Dict[str, Dict], 
                                output_filename: str = 'ccr_comm_costs_distribution.png'):
    """
    Plot communication cost distribution across CCR values
    
    Shows how min, max, mean, and total costs change with CCR
    """
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    fig.suptitle('Communication Cost Distribution vs CCR', fontsize=16, fontweight='bold')
    
    for idx, (workflow, data) in enumerate(data_dict.items()):
        if 'communication_costs' not in data:
            continue
        
        ax = axes.flat[idx]
        stats = data['communication_costs']['stats_per_ccr']
        
        ccr_vals = [s['ccr'] for s in stats]
        min_costs = [s['min'] for s in stats]
        max_costs = [s['max'] for s in stats]
        mean_costs = [s['mean'] for s in stats]
        
        # Plot lines
        ax.plot(ccr_vals, mean_costs, 'o-', label='Mean', linewidth=2.5, 
                markersize=8, color=COLORS.get(workflow, 'blue'))
        ax.plot(ccr_vals, min_costs, 's--', label='Min', linewidth=1.5, 
                markersize=6, alpha=0.7, color='green')
        ax.plot(ccr_vals, max_costs, '^--', label='Max', linewidth=1.5, 
                markersize=6, alpha=0.7, color='red')
        
        # Fill between min and max
        ax.fill_between(ccr_vals, min_costs, max_costs, alpha=0.2, 
                        color=COLORS.get(workflow, 'blue'))
        
        # Formatting
        workflow_title = WORKFLOW_TITLES.get(workflow, workflow.title())
        num_tasks = data.get('num_tasks', '?')
        num_vms = data.get('num_vms', '?')
        ax.set_title(f'{workflow_title} ({num_tasks} tasks, {num_vms} VMs)', 
                    fontsize=12, fontweight='bold')
        ax.set_xlabel('CCR', fontsize=11)
        ax.set_ylabel('Communication Cost', fontsize=11)
        ax.legend(loc='upper left', fontsize=9)
        ax.grid(True, alpha=0.3, linestyle='--')
        ax.set_xlim(0.35, 2.05)
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    plt.savefig(str(output_path).replace('.png', '.pdf'), bbox_inches='tight')
    print(f"✅ Saved: {output_path}")
    plt.close()

# ============================================================================
# PLOT 2: CRITICAL PATH STABILITY HEATMAP
# ============================================================================

def plot_critical_path_stability(data_dict: Dict[str, Dict],
                                 output_filename: str = 'ccr_critical_path_stability.png'):
    """
    Plot Critical Path stability heatmap showing which tasks remain in CP
    """
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    fig.suptitle('Critical Path Stability Across CCR Values', fontsize=16, fontweight='bold')
    
    for idx, (workflow, data) in enumerate(data_dict.items()):
        if 'critical_path_stability' not in data:
            continue
        
        ax = axes.flat[idx]
        cp_data = data['critical_path_stability']['cp_per_ccr']
        
        # Build matrix: rows = unique tasks, cols = CCR values
        all_tasks = set()
        for cp in cp_data:
            all_tasks.update(cp['tasks'])
        
        all_tasks = sorted(all_tasks)
        ccr_vals = [cp['ccr'] for cp in cp_data]
        
        # Create binary matrix (1 if task in CP, 0 otherwise)
        matrix = np.zeros((len(all_tasks), len(ccr_vals)))
        
        for col_idx, cp in enumerate(cp_data):
            for row_idx, task_id in enumerate(all_tasks):
                if task_id in cp['tasks']:
                    matrix[row_idx, col_idx] = 1
        
        # Plot heatmap
        im = ax.imshow(matrix, aspect='auto', cmap='RdYlGn', interpolation='nearest')
        
        # Set ticks
        ax.set_xticks(range(len(ccr_vals)))
        ax.set_xticklabels([f'{ccr:.1f}' for ccr in ccr_vals], fontsize=9)
        
        # Show only some task IDs to avoid clutter
        if len(all_tasks) <= 20:
            ax.set_yticks(range(len(all_tasks)))
            ax.set_yticklabels([f't{tid}' for tid in all_tasks], fontsize=8)
        else:
            # Show every 5th task
            tick_idx = list(range(0, len(all_tasks), 5))
            ax.set_yticks(tick_idx)
            ax.set_yticklabels([f't{all_tasks[i]}' for i in tick_idx], fontsize=8)
        
        # Labels
        workflow_title = WORKFLOW_TITLES.get(workflow, workflow.title())
        stability = data['critical_path_stability'].get('stability_score', 0)
        ax.set_title(f'{workflow_title} (Stability: {stability:.1%})', 
                    fontsize=12, fontweight='bold')
        ax.set_xlabel('CCR', fontsize=11)
        ax.set_ylabel('Task ID', fontsize=11)
        
        # Add colorbar
        cbar = plt.colorbar(im, ax=ax, fraction=0.046, pad=0.04)
        cbar.set_ticks([0, 1])
        cbar.set_ticklabels(['Not in CP', 'In CP'])
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    plt.savefig(str(output_path).replace('.png', '.pdf'), bbox_inches='tight')
    print(f"✅ Saved: {output_path}")
    plt.close()

# ============================================================================
# PLOT 3: DUPLICATION SENSITIVITY
# ============================================================================

def plot_duplication_sensitivity(data_dict: Dict[str, Dict],
                                output_filename: str = 'ccr_duplication_analysis.png'):
    """
    Plot task duplication decisions vs CCR
    """
    fig, axes = plt.subplots(2, 2, figsize=(14, 10))
    fig.suptitle('Task Duplication Sensitivity to CCR', fontsize=16, fontweight='bold')
    
    for idx, (workflow, data) in enumerate(data_dict.items()):
        if 'duplication_analysis' not in data:
            continue
        
        ax = axes.flat[idx]
        dup_data = data['duplication_analysis']['duplications_per_ccr']
        
        ccr_vals = [d['ccr'] for d in dup_data]
        total_dups = [d['total_duplications'] for d in dup_data]
        vms_with_dups = [d['vms_with_dups'] for d in dup_data]
        
        # Bar chart
        x = np.arange(len(ccr_vals))
        width = 0.35
        
        bars1 = ax.bar(x - width/2, total_dups, width, label='Total Duplications',
                      color=COLORS.get(workflow, 'blue'), alpha=0.8)
        bars2 = ax.bar(x + width/2, vms_with_dups, width, label='VMs with Dups',
                      color='orange', alpha=0.8)
        
        # Trend line for total duplications
        if len(ccr_vals) >= 2:
            z = np.polyfit(ccr_vals, total_dups, 1)
            p = np.poly1d(z)
            ax.plot(ccr_vals, p(ccr_vals), "r--", alpha=0.5, linewidth=2, label='Trend')
        
        # Formatting
        workflow_title = WORKFLOW_TITLES.get(workflow, workflow.title())
        correlation = data['duplication_analysis'].get('correlation_strength', 'unknown')
        ax.set_title(f'{workflow_title} (Correlation: {correlation})', 
                    fontsize=12, fontweight='bold')
        ax.set_xlabel('CCR', fontsize=11)
        ax.set_ylabel('Count', fontsize=11)
        ax.set_xticks(x)
        ax.set_xticklabels([f'{ccr:.1f}' for ccr in ccr_vals], fontsize=9)
        ax.legend(fontsize=9)
        ax.grid(True, alpha=0.3, linestyle='--', axis='y')
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    plt.savefig(str(output_path).replace('.png', '.pdf'), bbox_inches='tight')
    print(f"✅ Saved: {output_path}")
    plt.close()

# ============================================================================
# PLOT 4: SENSITIVITY SCORECARD
# ============================================================================

def plot_sensitivity_scorecard(data_dict: Dict[str, Dict],
                               output_filename: str = 'ccr_sensitivity_scorecard.png'):
    """
    Create a comprehensive sensitivity scorecard for all workflows
    """
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 6))
    fig.suptitle('CCR Sensitivity Scorecard', fontsize=16, fontweight='bold')
    
    workflows = []
    cp_stability = []
    slr_elasticity = []
    makespan_elasticity = []
    sensitivity_classes = []
    
    for workflow, data in data_dict.items():
        workflows.append(WORKFLOW_TITLES.get(workflow, workflow.title()))
        
        # CP Stability
        stability = data.get('critical_path_stability', {}).get('stability_score', 0)
        cp_stability.append(stability * 100)  # Convert to percentage
        
        # Metrics elasticity
        metrics = data.get('metrics_elasticity', {})
        slr_elast = abs(metrics.get('slr', {}).get('elasticity_coeff', 0))
        makespan_elast = abs(metrics.get('makespan', {}).get('elasticity_coeff', 0))
        slr_elasticity.append(slr_elast)
        makespan_elasticity.append(makespan_elast)
        
        sensitivity_classes.append(metrics.get('sensitivity_class', 'unknown'))
    
    # Plot 1: CP Stability
    colors_cp = ['green' if s > 90 else 'orange' if s > 70 else 'red' for s in cp_stability]
    bars1 = ax1.barh(workflows, cp_stability, color=colors_cp, alpha=0.7)
    ax1.set_xlabel('CP Stability (%)', fontsize=12, fontweight='bold')
    ax1.set_title('Critical Path Stability', fontsize=13, fontweight='bold')
    ax1.set_xlim(0, 100)
    ax1.axvline(x=90, color='green', linestyle='--', alpha=0.5, label='High (>90%)')
    ax1.axvline(x=70, color='orange', linestyle='--', alpha=0.5, label='Medium (>70%)')
    ax1.legend(fontsize=9)
    ax1.grid(True, alpha=0.3, axis='x')
    
    # Add value labels
    for i, (bar, val) in enumerate(zip(bars1, cp_stability)):
        ax1.text(val + 2, i, f'{val:.1f}%', va='center', fontsize=10, fontweight='bold')
    
    # Plot 2: Elasticity Coefficients
    x = np.arange(len(workflows))
    width = 0.35
    
    bars2 = ax2.bar(x - width/2, slr_elasticity, width, label='SLR Elasticity', 
                   color='steelblue', alpha=0.8)
    bars3 = ax2.bar(x + width/2, makespan_elasticity, width, label='Makespan Elasticity',
                   color='coral', alpha=0.8)
    
    ax2.set_ylabel('Elasticity Coefficient (abs)', fontsize=12, fontweight='bold')
    ax2.set_title('Metrics Sensitivity to CCR', fontsize=13, fontweight='bold')
    ax2.set_xticks(x)
    ax2.set_xticklabels(workflows, rotation=45, ha='right')
    ax2.legend(fontsize=10)
    ax2.grid(True, alpha=0.3, axis='y')
    
    # Add sensitivity class labels
    for i, (slr_val, make_val, sens_class) in enumerate(zip(slr_elasticity, makespan_elasticity, sensitivity_classes)):
        max_val = max(slr_val, make_val)
        ax2.text(i, max_val + 0.02, sens_class.upper(), ha='center', va='bottom',
                fontsize=8, fontweight='bold', 
                color='red' if sens_class == 'high' else 'orange' if sens_class == 'medium' else 'green')
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    plt.savefig(str(output_path).replace('.png', '.pdf'), bbox_inches='tight')
    print(f"✅ Saved: {output_path}")
    plt.close()

# ============================================================================
# NEW PLOT 5: NORMALIZED PERFORMANCE VS CCR (LINE PLOT)
# ============================================================================

def plot_normalized_performance(data_dict: Dict[str, Dict],
                                output_filename: str = 'ccr_normalized_performance.png'):
    """
    Plot normalized performance metrics (SLR, Makespan, AVU) vs CCR
    Shows relative performance degradation - normalized to CCR=0.4 baseline
    """
    fig, axes = plt.subplots(2, 2, figsize=(16, 11))
    fig.suptitle('Normalized Performance Metrics vs CCR (Baseline: CCR=0.4)', 
                 fontsize=16, fontweight='bold')
    
    # Collect data for all workflows
    workflow_data = {}
    for workflow, data in data_dict.items():
        if 'communication_costs' not in data:
            continue
        
        stats = data['communication_costs']['stats_per_ccr']
        ccr_vals = [s['ccr'] for s in stats]
        
        # Extract metrics from JSON (we need to get actual values)
        # For now, we'll compute from elasticity data
        metrics = data.get('metrics_elasticity', {})
        
        workflow_data[workflow] = {
            'ccr': ccr_vals,
            'metrics': metrics
        }
    
    # Plot 1: SLR normalized
    ax1 = axes[0, 0]
    for workflow, wdata in workflow_data.items():
        ccr_vals = wdata['ccr']
        slr_data = wdata['metrics'].get('slr', {})
        
        # Calculate normalized values (0.4 = 100%, 2.0 = 100 + percent_change)
        percent_change = slr_data.get('percent_change', 0)
        baseline = 100
        final = 100 + percent_change
        
        # Linear interpolation between baseline and final
        normalized = [baseline + (final - baseline) * (ccr - ccr_vals[0]) / (ccr_vals[-1] - ccr_vals[0]) 
                     for ccr in ccr_vals]
        
        ax1.plot(ccr_vals, normalized, 'o-', label=WORKFLOW_TITLES.get(workflow, workflow.title()),
                linewidth=2.5, markersize=8, color=COLORS.get(workflow, 'blue'))
    
    ax1.set_xlabel('CCR', fontsize=12, fontweight='bold')
    ax1.set_ylabel('Normalized SLR (%)', fontsize=12, fontweight='bold')
    ax1.set_title('Schedule Length Ratio (SLR)', fontsize=13, fontweight='bold')
    ax1.legend(fontsize=10, loc='upper left')
    ax1.grid(True, alpha=0.3, linestyle='--')
    ax1.axhline(y=100, color='gray', linestyle='--', alpha=0.5, linewidth=1)
    ax1.set_xlim(0.35, 2.05)
    
    # Plot 2: Makespan normalized
    ax2 = axes[0, 1]
    for workflow, wdata in workflow_data.items():
        ccr_vals = wdata['ccr']
        makespan_data = wdata['metrics'].get('makespan', {})
        
        percent_change = makespan_data.get('percent_change', 0)
        baseline = 100
        final = 100 + percent_change
        
        normalized = [baseline + (final - baseline) * (ccr - ccr_vals[0]) / (ccr_vals[-1] - ccr_vals[0]) 
                     for ccr in ccr_vals]
        
        ax2.plot(ccr_vals, normalized, 'o-', label=WORKFLOW_TITLES.get(workflow, workflow.title()),
                linewidth=2.5, markersize=8, color=COLORS.get(workflow, 'blue'))
    
    ax2.set_xlabel('CCR', fontsize=12, fontweight='bold')
    ax2.set_ylabel('Normalized Makespan (%)', fontsize=12, fontweight='bold')
    ax2.set_title('Makespan', fontsize=13, fontweight='bold')
    ax2.legend(fontsize=10, loc='upper left')
    ax2.grid(True, alpha=0.3, linestyle='--')
    ax2.axhline(y=100, color='gray', linestyle='--', alpha=0.5, linewidth=1)
    ax2.set_xlim(0.35, 2.05)
    
    # Plot 3: AVU normalized
    ax3 = axes[1, 0]
    for workflow, wdata in workflow_data.items():
        ccr_vals = wdata['ccr']
        avu_data = wdata['metrics'].get('avu', {})
        
        percent_change = avu_data.get('percent_change', 0)
        baseline = 100
        final = 100 + percent_change
        
        normalized = [baseline + (final - baseline) * (ccr - ccr_vals[0]) / (ccr_vals[-1] - ccr_vals[0]) 
                     for ccr in ccr_vals]
        
        ax3.plot(ccr_vals, normalized, 'o-', label=WORKFLOW_TITLES.get(workflow, workflow.title()),
                linewidth=2.5, markersize=8, color=COLORS.get(workflow, 'blue'))
    
    ax3.set_xlabel('CCR', fontsize=12, fontweight='bold')
    ax3.set_ylabel('Normalized AVU (%)', fontsize=12, fontweight='bold')
    ax3.set_title('Average VM Utilization (AVU)', fontsize=13, fontweight='bold')
    ax3.legend(fontsize=10, loc='lower left')
    ax3.grid(True, alpha=0.3, linestyle='--')
    ax3.axhline(y=100, color='gray', linestyle='--', alpha=0.5, linewidth=1)
    ax3.set_xlim(0.35, 2.05)
    
    # Plot 4: Combined summary (all metrics on one plot)
    ax4 = axes[1, 1]
    for workflow, wdata in workflow_data.items():
        ccr_vals = wdata['ccr']
        metrics = wdata['metrics']
        
        # Get percent changes
        slr_change = metrics.get('slr', {}).get('percent_change', 0)
        makespan_change = metrics.get('makespan', {}).get('percent_change', 0)
        avu_change = abs(metrics.get('avu', {}).get('percent_change', 0))
        
        # Plot bars
        workflow_title = WORKFLOW_TITLES.get(workflow, workflow.title())
        x_pos = list(data_dict.keys()).index(workflow)
        
        ax4.bar(x_pos - 0.25, slr_change, 0.25, label='SLR' if x_pos == 0 else '', 
               color='steelblue', alpha=0.8)
        ax4.bar(x_pos, makespan_change, 0.25, label='Makespan' if x_pos == 0 else '', 
               color='coral', alpha=0.8)
        ax4.bar(x_pos + 0.25, avu_change, 0.25, label='AVU' if x_pos == 0 else '', 
               color='lightgreen', alpha=0.8)
    
    ax4.set_ylabel('Performance Change (%)', fontsize=12, fontweight='bold')
    ax4.set_title('Total Performance Impact (CCR: 0.4 → 2.0)', fontsize=13, fontweight='bold')
    ax4.set_xticks(range(len(data_dict)))
    ax4.set_xticklabels([WORKFLOW_TITLES.get(w, w.title()) for w in data_dict.keys()], 
                        rotation=30, ha='right')
    ax4.legend(fontsize=10)
    ax4.grid(True, alpha=0.3, axis='y', linestyle='--')
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    plt.savefig(str(output_path).replace('.png', '.pdf'), bbox_inches='tight')
    print(f"✅ Saved: {output_path}")
    plt.close()

# ============================================================================
# NEW PLOT 6: SENSITIVITY CLASSIFICATION MATRIX
# ============================================================================

def plot_sensitivity_matrix(output_filename: str = 'ccr_sensitivity_matrix.png'):
    """
    Create a sensitivity classification matrix heatmap
    Rows: Workflows, Columns: Scales (Small, Medium, Large)
    Cell color: Sensitivity intensity, Cell value: % change
    """
    # Load data for all scales
    scales = ['exp1_small', 'exp1_medium', 'exp1_large']
    scale_titles = ['Small\n(47-50 tasks, 5 VMs)', 'Medium\n(100 tasks, 10 VMs)', 'Large\n(997-1000 tasks, 50 VMs)']
    
    matrix_data = []
    workflow_labels = []
    
    for workflow in WORKFLOW_ORDER:
        row = []
        workflow_labels.append(WORKFLOW_TITLES.get(workflow, workflow.title()))
        
        for scale in scales:
            data = load_ccr_analysis(workflow, scale)
            if data:
                metrics = data.get('metrics_elasticity', {})
                slr_change = metrics.get('slr', {}).get('percent_change', 0)
                row.append(slr_change)
            else:
                row.append(0)
        
        matrix_data.append(row)
    
    matrix_data = np.array(matrix_data)
    
    # Create figure
    fig, ax = plt.subplots(figsize=(10, 8))
    fig.suptitle('CCR Sensitivity Classification Matrix\n(SLR % Change: CCR 0.4 → 2.0)', 
                 fontsize=16, fontweight='bold')
    
    # Custom colormap: Green (0%) → Yellow (10%) → Orange (20%) → Red (30%+)
    from matplotlib.colors import LinearSegmentedColormap
    colors_list = ['#2ecc71', '#f1c40f', '#e67e22', '#e74c3c']
    n_bins = 100
    cmap = LinearSegmentedColormap.from_list('sensitivity', colors_list, N=n_bins)
    
    # Plot heatmap
    im = ax.imshow(matrix_data, cmap=cmap, aspect='auto', vmin=0, vmax=30)
    
    # Set ticks
    ax.set_xticks(np.arange(len(scales)))
    ax.set_yticks(np.arange(len(workflow_labels)))
    ax.set_xticklabels(scale_titles, fontsize=11)
    ax.set_yticklabels(workflow_labels, fontsize=12, fontweight='bold')
    
    # Add value labels in cells
    for i in range(len(workflow_labels)):
        for j in range(len(scales)):
            value = matrix_data[i, j]
            
            # Determine sensitivity class
            if value < 1:
                sens_class = 'INSENSITIVE'
                text_color = 'black'
            elif value < 10:
                sens_class = 'LOW'
                text_color = 'black'
            elif value < 20:
                sens_class = 'MEDIUM'
                text_color = 'white'
            else:
                sens_class = 'HIGH'
                text_color = 'white'
            
            ax.text(j, i, f'{value:.1f}%\n{sens_class}', 
                   ha='center', va='center', fontsize=10, fontweight='bold',
                   color=text_color)
    
    # Colorbar
    cbar = plt.colorbar(im, ax=ax, fraction=0.046, pad=0.04)
    cbar.set_label('SLR % Change', fontsize=12, fontweight='bold')
    
    # Add legend for sensitivity classes
    legend_elements = [
        mpatches.Patch(facecolor='#2ecc71', label='Insensitive (0-1%)'),
        mpatches.Patch(facecolor='#f1c40f', label='Low (1-10%)'),
        mpatches.Patch(facecolor='#e67e22', label='Medium (10-20%)'),
        mpatches.Patch(facecolor='#e74c3c', label='High (>20%)')
    ]
    ax.legend(handles=legend_elements, loc='upper left', bbox_to_anchor=(1.15, 1), 
             fontsize=10, framealpha=0.9)
    
    plt.tight_layout()
    output_path = Path('../results/figures') / output_filename
    output_path.parent.mkdir(parents=True, exist_ok=True)
    plt.savefig(output_path, dpi=300, bbox_inches='tight')
    plt.savefig(str(output_path).replace('.png', '.pdf'), bbox_inches='tight')
    print(f"✅ Saved: {output_path}")
    plt.close()

# ============================================================================
# SUMMARY STATISTICS
# ============================================================================

def generate_summary_statistics(data_dict: Dict[str, Dict]):
    """
    Generate and print summary statistics for all workflows
    """
    print("\n" + "="*70)
    print("CCR SENSITIVITY ANALYSIS SUMMARY")
    print("="*70)
    
    for workflow, data in data_dict.items():
        print(f"\n📊 {WORKFLOW_TITLES.get(workflow, workflow.title()).upper()}")
        print("-" * 70)
        
        # Basic info
        num_tasks = data.get('num_tasks', '?')
        num_vms = data.get('num_vms', '?')
        num_ccr = data.get('num_ccr_values', '?')
        print(f"   Configuration: {num_tasks} tasks, {num_vms} VMs, {num_ccr} CCR values")
        
        # Communication costs
        if 'communication_costs' in data:
            comm = data['communication_costs']
            print(f"   Comm Cost Increase: {comm.get('total_cost_increase_percent', 0):.2f}%")
        
        # CP Stability
        if 'critical_path_stability' in data:
            cp = data['critical_path_stability']
            print(f"   CP Stability: {cp.get('stability_score', 0):.1%}")
            print(f"   CP Changes: {cp.get('cp_changes', 0)} times")
            print(f"   Always in CP: {cp.get('always_in_cp_tasks', 0)}/{cp.get('total_unique_cp_tasks', 0)} tasks")
        
        # Duplication
        if 'duplication_analysis' in data:
            dup = data['duplication_analysis']
            print(f"   Duplication Increase: {dup.get('duplication_increase', 0)} tasks")
            print(f"   Correlation: {dup.get('correlation_strength', 'unknown')}")
        
        # Metrics elasticity
        if 'metrics_elasticity' in data:
            metrics = data['metrics_elasticity']
            print(f"   SLR Change: {metrics.get('slr', {}).get('percent_change', 0):.2f}%")
            print(f"   AVU Change: {metrics.get('avu', {}).get('percent_change', 0):.2f}%")
            print(f"   Makespan Change: {metrics.get('makespan', {}).get('percent_change', 0):.2f}%")
            print(f"   Sensitivity Class: {metrics.get('sensitivity_class', 'unknown').upper()}")
    
    print("\n" + "="*70)

# ============================================================================
# MAIN EXECUTION
# ============================================================================

def analyze_experiment(experiment: str = 'exp1_small'):
    """
    Run complete analysis for one experiment
    """
    print(f"\n{'='*70}")
    print(f"ANALYZING: {EXPERIMENT_TITLES.get(experiment, experiment)}")
    print(f"{'='*70}\n")
    
    # Load data
    print("📂 Loading data...")
    data_dict = load_all_workflows(experiment)
    
    if not data_dict:
        print(f"❌ No data found for {experiment}")
        return
    
    print(f"✅ Loaded {len(data_dict)} workflows: {', '.join(data_dict.keys())}\n")
    
    # Generate plots
    print("📊 Generating visualizations...")
    
    exp_suffix = f"_{experiment}"
    
    # Original plots
    plot_comm_cost_distribution(data_dict, 
        output_filename=f'ccr_comm_costs_distribution{exp_suffix}.png')
    
    plot_critical_path_stability(data_dict,
        output_filename=f'ccr_critical_path_stability{exp_suffix}.png')
    
    plot_duplication_sensitivity(data_dict,
        output_filename=f'ccr_duplication_analysis{exp_suffix}.png')
    
    plot_sensitivity_scorecard(data_dict,
        output_filename=f'ccr_sensitivity_scorecard{exp_suffix}.png')
    
    # NEW: Enhanced plots
    plot_normalized_performance(data_dict,
        output_filename=f'ccr_normalized_performance{exp_suffix}.png')
    
    # Generate summary
    generate_summary_statistics(data_dict)
    
    print(f"\n✅ Analysis complete for {experiment}!")

def main():
    """
    Main entry point
    """
    print("="*70)
    print("CCR SENSITIVITY ANALYSIS TOOL")
    print("="*70)
    
    # Check what's available
    print("\n🔍 Scanning for available analyses...")
    available = find_available_analyses()
    
    if not available:
        print("❌ No CCR sensitivity analysis files found!")
        print("   Expected location: ../results/ccr_sensitivity/*_analysis.json")
        print("   Run experiments first to generate analysis data.")
        return
    
    print(f"✅ Found {len(available)} analysis files:\n")
    for filepath in available:
        print(f"   - {os.path.basename(filepath)}")
    
    # Determine which experiments to analyze
    experiments_found = set()
    for filepath in available:
        basename = os.path.basename(filepath)
        if 'exp1_small' in basename:
            experiments_found.add('exp1_small')
        elif 'exp1_medium' in basename:
            experiments_found.add('exp1_medium')
        elif 'exp1_large' in basename:
            experiments_found.add('exp1_large')
    
    # Analyze each experiment
    for experiment in sorted(experiments_found):
        analyze_experiment(experiment)
    
    # Generate cross-scale comparison if we have multiple scales
    if len(experiments_found) >= 2:
        print(f"\n{'='*70}")
        print("GENERATING CROSS-SCALE COMPARISON")
        print(f"{'='*70}\n")
        print("📊 Creating sensitivity matrix across scales...")
        plot_sensitivity_matrix(output_filename='ccr_sensitivity_matrix.png')
    
    print("\n" + "="*70)
    print("✅ ALL ANALYSES COMPLETE!")
    print("="*70)
    print(f"\nGenerated figures saved to: results/figures/")
    print("Files created:")
    print("  - ccr_comm_costs_distribution_*.png (+ PDF)")
    print("  - ccr_critical_path_stability_*.png (+ PDF)")
    print("  - ccr_duplication_analysis_*.png (+ PDF)")
    print("  - ccr_sensitivity_scorecard_*.png (+ PDF)")
    print("  - ccr_normalized_performance_*.png (+ PDF) [NEW]")
    print("  - ccr_sensitivity_matrix.png (+ PDF) [NEW - Cross-scale]")

if __name__ == '__main__':
    main()
