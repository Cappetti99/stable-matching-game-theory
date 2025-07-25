#!/usr/bin/env python3
"""
🎨 STABLE MATCHING GAME THEORY - UNIFIED VISUALIZATION TOOL
============================================================

Strumento unificato per la visualizzazione di tutti i risultati:
- Analisi CCR per workflow scientifici
- Confronto tra diversi tipi di DAG
- Metriche di performance degli algoritmi
- Trend e correlazioni

Autore: Lorenzo Cappetti
Data: 25 luglio 2025
"""

import json
import matplotlib.pyplot as plt
import numpy as np
import os
import sys
from pathlib import Path

class UnifiedVisualizer:
    """Classe principale per la visualizzazione unificata dei risultati"""
    
    def __init__(self):
        self.results_dir = Path("algorithms")
        self.output_dir = Path("visualizations")
        self.output_dir.mkdir(exist_ok=True)
        
        # Configurazione stile grafici
        plt.style.use('seaborn-v0_8' if 'seaborn-v0_8' in plt.style.available else 'default')
        self.colors = {
            'cybershake': '#FF6B6B',   # Rosso
            'epigenomics': '#4ECDC4',  # Verde acqua
            'ligo': '#45B7D1',         # Blu
            'montage': '#FFA07A'       # Arancione
        }
        
        print("🎨 UNIFIED VISUALIZATION TOOL")
        print("=" * 40)
    
    def load_ccr_results(self):
        """Carica tutti i risultati CCR disponibili"""
        results_file = self.results_dir / "ccr_analysis_results.json"
        
        if not results_file.exists():
            print(f"❌ File risultati non trovato: {results_file}")
            return None
            
        try:
            with open(results_file, 'r') as f:
                data = json.load(f)
            print(f"✅ Caricati risultati per: {data['workflow_type']}")
            return data
        except Exception as e:
            print(f"❌ Errore nel caricamento: {e}")
            return None
    
    def load_all_workflow_results(self):
        """Carica risultati di tutti i workflow se disponibili"""
        all_results = {}
        workflow_types = ['cybershake', 'epigenomics', 'ligo', 'montage']
        
        for workflow in workflow_types:
            # Cerca file specifici per workflow o file generico
            specific_file = self.results_dir / f"{workflow}_ccr_analysis.json"
            if specific_file.exists():
                try:
                    with open(specific_file, 'r') as f:
                        all_results[workflow] = json.load(f)
                    print(f"✅ Caricato {workflow}")
                except Exception as e:
                    print(f"⚠️ Errore caricamento {workflow}: {e}")
        
        # Se non ci sono file specifici, carica il file generico
        if not all_results:
            generic_data = self.load_ccr_results()
            if generic_data:
                workflow_type = generic_data.get('workflow_type', 'unknown')
                all_results[workflow_type] = generic_data
                
        return all_results
    
    def plot_ccr_analysis(self, data, save_individual=True):
        """Crea grafici per analisi CCR singola"""
        if not data or 'results' not in data:
            print("❌ Dati non validi per plot CCR")
            return
            
        workflow_type = data.get('workflow_type', 'unknown')
        results = data['results']
        
        # Estrai dati
        ccr_values = [r['ccr'] for r in results]
        makespan_values = [r['makespan'] for r in results]
        slr_values = [r['slr'] for r in results if r['slr'] != float('inf')]
        
        # Crea figura con subplot
        fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(15, 12))
        fig.suptitle(f'📊 Analisi CCR - Workflow {workflow_type.upper()}', fontsize=16, fontweight='bold')
        
        # Plot 1: CCR vs Makespan
        ax1.plot(ccr_values, makespan_values, 'o-', color=self.colors.get(workflow_type, '#333333'), 
                linewidth=2, markersize=8, label=f'{workflow_type.title()}')
        ax1.set_xlabel('CCR (Communication-to-Computation Ratio)')
        ax1.set_ylabel('Makespan')
        ax1.set_title('🕒 CCR vs Makespan')
        ax1.grid(True, alpha=0.3)
        ax1.legend()
        
        # Plot 2: Distribuzione Makespan
        ax2.hist(makespan_values, bins=min(len(makespan_values), 10), alpha=0.7, 
                color=self.colors.get(workflow_type, '#333333'))
        ax2.set_xlabel('Makespan')
        ax2.set_ylabel('Frequenza')
        ax2.set_title('📈 Distribuzione Makespan')
        ax2.grid(True, alpha=0.3)
        
        # Plot 3: SLR (se disponibile)
        if slr_values and len(slr_values) > 1:
            ax3.plot(ccr_values[:len(slr_values)], slr_values, 's-', 
                    color=self.colors.get(workflow_type, '#333333'), 
                    linewidth=2, markersize=6)
            ax3.set_xlabel('CCR')
            ax3.set_ylabel('SLR (Schedule Length Ratio)')
            ax3.set_title('⚡ CCR vs SLR')
            ax3.grid(True, alpha=0.3)
        else:
            ax3.text(0.5, 0.5, 'SLR non disponibile\n(valori infiniti)', 
                    ha='center', va='center', transform=ax3.transAxes,
                    fontsize=12, style='italic')
            ax3.set_title('⚡ SLR Analysis')
        
        # Plot 4: Statistiche
        stats_text = f"""
        📊 STATISTICHE {workflow_type.upper()}
        {'=' * 25}
        
        Task totali: {data.get('parameters', {}).get('target_tasks', 'N/A')}
        VM utilizzate: {data.get('parameters', {}).get('target_vms', 'N/A')}
        
        CCR range: {min(ccr_values):.1f} - {max(ccr_values):.1f}
        
        Makespan:
        • Min: {min(makespan_values):.3f}
        • Max: {max(makespan_values):.3f}
        • Media: {np.mean(makespan_values):.3f}
        • Std: {np.std(makespan_values):.3f}
        
        Punti analizzati: {len(results)}
        """
        
        ax4.text(0.05, 0.95, stats_text, transform=ax4.transAxes, 
                fontsize=10, verticalalignment='top', fontfamily='monospace')
        ax4.set_xlim(0, 1)
        ax4.set_ylim(0, 1)
        ax4.axis('off')
        
        plt.tight_layout()
        
        if save_individual:
            output_file = self.output_dir / f"{workflow_type}_detailed_analysis.png"
            plt.savefig(output_file, dpi=300, bbox_inches='tight')
            print(f"💾 Salvato: {output_file}")
        
        return fig
    
    def plot_workflow_comparison(self, all_results):
        """Crea grafici di confronto tra workflow"""
        if len(all_results) < 2:
            print("⚠️ Necessari almeno 2 workflow per il confronto")
            return
            
        fig, ((ax1, ax2), (ax3, ax4)) = plt.subplots(2, 2, figsize=(16, 12))
        fig.suptitle('🔬 CONFRONTO WORKFLOW SCIENTIFICI', fontsize=16, fontweight='bold')
        
        # Plot 1: CCR vs Makespan per tutti i workflow
        for workflow, data in all_results.items():
            if 'results' not in data:
                continue
                
            results = data['results']
            ccr_values = [r['ccr'] for r in results]
            makespan_values = [r['makespan'] for r in results]
            
            ax1.plot(ccr_values, makespan_values, 'o-', 
                    color=self.colors.get(workflow, '#333333'),
                    linewidth=2, markersize=6, label=workflow.title())
        
        ax1.set_xlabel('CCR (Communication-to-Computation Ratio)')
        ax1.set_ylabel('Makespan')
        ax1.set_title('📊 CCR vs Makespan - Tutti i Workflow')
        ax1.grid(True, alpha=0.3)
        ax1.legend()
        
        # Plot 2: Boxplot Makespan
        makespan_data = []
        workflow_labels = []
        for workflow, data in all_results.items():
            if 'results' not in data:
                continue
            makespan_values = [r['makespan'] for r in data['results']]
            makespan_data.append(makespan_values)
            workflow_labels.append(workflow.title())
        
        if makespan_data:
            bp = ax2.boxplot(makespan_data, labels=workflow_labels, patch_artist=True)
            for patch, workflow in zip(bp['boxes'], all_results.keys()):
                patch.set_facecolor(self.colors.get(workflow, '#333333'))
                patch.set_alpha(0.7)
        
        ax2.set_ylabel('Makespan')
        ax2.set_title('📦 Distribuzione Makespan per Workflow')
        ax2.grid(True, alpha=0.3)
        plt.setp(ax2.get_xticklabels(), rotation=45)
        
        # Plot 3: Efficienza relativa
        baseline_workflow = list(all_results.keys())[0]
        baseline_makespan = np.mean([r['makespan'] for r in all_results[baseline_workflow]['results']])
        
        workflows = []
        efficiency = []
        for workflow, data in all_results.items():
            avg_makespan = np.mean([r['makespan'] for r in data['results']])
            relative_efficiency = baseline_makespan / avg_makespan * 100
            workflows.append(workflow.title())
            efficiency.append(relative_efficiency)
        
        bars = ax3.bar(workflows, efficiency, color=[self.colors.get(w.lower(), '#333333') for w in workflows], alpha=0.7)
        ax3.set_ylabel('Efficienza Relativa (%)')
        ax3.set_title(f'⚡ Efficienza Relativa (baseline: {baseline_workflow.title()})')
        ax3.grid(True, alpha=0.3, axis='y')
        ax3.axhline(y=100, color='red', linestyle='--', alpha=0.5)
        plt.setp(ax3.get_xticklabels(), rotation=45)
        
        # Aggiungi valori sulle barre
        for bar, eff in zip(bars, efficiency):
            height = bar.get_height()
            ax3.text(bar.get_x() + bar.get_width()/2., height,
                    f'{eff:.1f}%', ha='center', va='bottom')
        
        # Plot 4: Riepilogo statistiche
        summary_text = "📋 RIEPILOGO CONFRONTO\n"
        summary_text += "=" * 30 + "\n\n"
        
        for workflow, data in all_results.items():
            if 'results' not in data:
                continue
            makespan_values = [r['makespan'] for r in data['results']]
            params = data.get('parameters', {})
            
            summary_text += f"🔹 {workflow.upper()}\n"
            summary_text += f"   Task: {params.get('target_tasks', 'N/A')}\n"
            summary_text += f"   Makespan medio: {np.mean(makespan_values):.3f}\n"
            summary_text += f"   Variabilità: {np.std(makespan_values):.3f}\n\n"
        
        ax4.text(0.05, 0.95, summary_text, transform=ax4.transAxes,
                fontsize=10, verticalalignment='top', fontfamily='monospace')
        ax4.set_xlim(0, 1)
        ax4.set_ylim(0, 1)
        ax4.axis('off')
        
        plt.tight_layout()
        
        # Salva confronto
        output_file = self.output_dir / "workflow_comparison.png"
        plt.savefig(output_file, dpi=300, bbox_inches='tight')
        print(f"💾 Salvato confronto: {output_file}")
        
        return fig
    
    def create_summary_report(self, all_results):
        """Crea un report testuale riassuntivo"""
        report_file = self.output_dir / "analysis_summary.txt"
        
        with open(report_file, 'w') as f:
            f.write("🎯 STABLE MATCHING GAME THEORY - SUMMARY REPORT\n")
            f.write("=" * 50 + "\n")
            f.write(f"Data analisi: 25 luglio 2025\n\n")
            
            if not all_results:
                f.write("❌ Nessun risultato disponibile per l'analisi\n")
                return
                
            f.write(f"📊 WORKFLOW ANALIZZATI: {len(all_results)}\n")
            f.write("-" * 30 + "\n\n")
            
            for workflow, data in all_results.items():
                if 'results' not in data:
                    continue
                    
                results = data['results']
                makespan_values = [r['makespan'] for r in results]
                ccr_values = [r['ccr'] for r in results]
                params = data.get('parameters', {})
                
                f.write(f"🔹 {workflow.upper()}\n")
                f.write(f"   Tipo: Workflow scientifico\n")
                f.write(f"   Task generati: {params.get('target_tasks', 'N/A')}\n")
                f.write(f"   VM utilizzate: {params.get('target_vms', 'N/A')}\n")
                f.write(f"   Range CCR: {min(ccr_values):.1f} - {max(ccr_values):.1f}\n")
                f.write(f"   Punti analizzati: {len(results)}\n")
                f.write(f"   \n")
                f.write(f"   PERFORMANCE:\n")
                f.write(f"   • Makespan medio: {np.mean(makespan_values):.4f}\n")
                f.write(f"   • Makespan min: {min(makespan_values):.4f}\n")
                f.write(f"   • Makespan max: {max(makespan_values):.4f}\n")
                f.write(f"   • Deviazione std: {np.std(makespan_values):.4f}\n")
                f.write(f"\n")
            
            f.write("🏆 ALGORITMI UTILIZZATI:\n")
            f.write("-" * 25 + "\n")
            f.write("• SMGT (Stable Matching Game Theory)\n")
            f.write("• DCP (Deferred Choice Protocol)\n")
            f.write("• LOTD (List of Tasks Distribution)\n")
            f.write("\n")
            
            f.write("🎨 VISUALIZZAZIONI GENERATE:\n")
            f.write("-" * 30 + "\n")
            for workflow in all_results.keys():
                f.write(f"• {workflow}_detailed_analysis.png\n")
            f.write("• workflow_comparison.png\n")
            f.write("• analysis_summary.txt (questo file)\n")
        
        print(f"📋 Report salvato: {report_file}")
    
    def run_complete_visualization(self):
        """Esegue visualizzazione completa"""
        print("\n🚀 Avvio visualizzazione completa...")
        
        # Carica tutti i risultati disponibili
        all_results = self.load_all_workflow_results()
        
        if not all_results:
            print("❌ Nessun risultato trovato per la visualizzazione")
            return
        
        print(f"📊 Trovati {len(all_results)} workflow da visualizzare")
        
        # Crea grafici individuali per ogni workflow
        for workflow, data in all_results.items():
            print(f"\n📈 Creazione grafici per {workflow}...")
            self.plot_ccr_analysis(data, save_individual=True)
        
        # Crea grafici di confronto se ci sono più workflow
        if len(all_results) > 1:
            print(f"\n🔬 Creazione grafici di confronto...")
            self.plot_workflow_comparison(all_results)
        
        # Crea report riassuntivo
        print(f"\n📋 Generazione report riassuntivo...")
        self.create_summary_report(all_results)
        
        print(f"\n✅ Visualizzazione completata!")
        print(f"📁 Tutti i file salvati in: {self.output_dir}")
        
        # Mostra grafici (solo in ambiente interattivo)
        if len(plt.get_fignums()) > 0:
            print("\n🎨 Grafici salvati, usa 'open visualizations/' per visualizzarli")
            # plt.show()  # Commentato per evitare blocchi in script automatici

def main():
    """Funzione principale"""
    visualizer = UnifiedVisualizer()
    
    # Verifica argomenti
    if len(sys.argv) > 1:
        mode = sys.argv[1].lower()
        if mode == 'report':
            # Solo report testuale
            all_results = visualizer.load_all_workflow_results()
            visualizer.create_summary_report(all_results)
        elif mode == 'single':
            # Solo workflow corrente
            data = visualizer.load_ccr_results()
            if data:
                visualizer.plot_ccr_analysis(data)
                plt.show()
        else:
            print("❌ Modalità non riconosciuta. Uso: python3 visualize_results.py [report|single]")
    else:
        # Visualizzazione completa (default)
        visualizer.run_complete_visualization()

if __name__ == "__main__":
    main()
