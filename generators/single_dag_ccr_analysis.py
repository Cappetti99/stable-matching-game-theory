#!/usr/bin/env python3

"""
Analisi CCR semplificata per singolo DAG
Genera un DAG con 50 task e 5 VM, poi esegue analisi CCR ogni 0.2
"""

import subprocess
import json
import matplotlib.pyplot as plt
import numpy as np
import sys
import os

def run_ccr_analysis():
    """Esegue analisi CCR Java"""
    print("📊 Esecuzione analisi CCR...")
    
    os.chdir("../algorithms")
    
    # Compila Java
    result = subprocess.run(["javac"] + [f for f in os.listdir(".") if f.endswith(".java")], 
                          capture_output=True, text=True)
    if result.returncode != 0:
        print(f"❌ Errore compilazione: {result.stderr}")
        return None
    
    # Esegui analisi
    result = subprocess.run(["java", "MainCCRAnalysis"], capture_output=True, text=True)
    if result.returncode != 0:
        print(f"❌ Errore esecuzione: {result.stderr}")
        return None
    
    # Leggi risultati
    try:
        with open("ccr_analysis_results.json", "r") as f:
            data = json.load(f)
        print(f"✅ Analisi completata: {len(data['results'])} punti CCR")
        return data
    except Exception as e:
        print(f"❌ Errore lettura risultati: {e}")
        return None

def create_plot(data, dag_name):
    """Crea grafico per singolo DAG"""
    results = data['results']
    ccr_values = [r['ccr'] for r in results]
    slr_values = [r['slr'] for r in results]
    makespan_values = [r['makespan'] for r in results]
    
    plt.style.use('seaborn-v0_8')
    fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(15, 6))
    
    # CCR vs SLR
    ax1.plot(ccr_values, slr_values, 'o-', linewidth=2, markersize=8, color='blue')
    ax1.set_xlabel('CCR (Communication-to-Computation Ratio)', fontsize=12)
    ax1.set_ylabel('SLR (Schedule Length Ratio)', fontsize=12)
    ax1.set_title(f'{dag_name} - CCR vs SLR\n50 task, 5 VM', fontsize=14, fontweight='bold')
    ax1.grid(True, alpha=0.3)
    
    # Annotazioni
    ax1.text(0.05, 0.95, f'Min SLR: {min(slr_values):.3f}', transform=ax1.transAxes, 
             bbox=dict(boxstyle="round", facecolor='lightblue', alpha=0.8))
    ax1.text(0.05, 0.85, f'Max SLR: {max(slr_values):.3f}', transform=ax1.transAxes,
             bbox=dict(boxstyle="round", facecolor='lightcoral', alpha=0.8))
    
    # CCR vs Makespan
    ax2.plot(ccr_values, makespan_values, 'o-', linewidth=2, markersize=8, color='red')
    ax2.set_xlabel('CCR (Communication-to-Computation Ratio)', fontsize=12)
    ax2.set_ylabel('Makespan', fontsize=12)
    ax2.set_title(f'{dag_name} - CCR vs Makespan\n50 task, 5 VM', fontsize=14, fontweight='bold')
    ax2.grid(True, alpha=0.3)
    
    # Correlazione
    correlation = np.corrcoef(ccr_values, slr_values)[0, 1]
    ax2.text(0.05, 0.95, f'Correlazione CCR↔SLR: {correlation:.3f}', transform=ax2.transAxes,
             bbox=dict(boxstyle="round", facecolor='wheat', alpha=0.8))
    
    plt.tight_layout()
    
    filename = f"{dag_name.lower()}_ccr_analysis.png"
    plt.savefig(filename, dpi=300, bbox_inches='tight')
    print(f"📊 Grafico salvato: {filename}")
    plt.show()

def print_summary(data, dag_name):
    """Stampa riassunto risultati"""
    results = data['results']
    ccr_values = [r['ccr'] for r in results]
    slr_values = [r['slr'] for r in results]
    makespan_values = [r['makespan'] for r in results]
    
    print(f"\n📋 RIASSUNTO - {dag_name}")
    print("=" * 40)
    print(f"Task: 50, VM: 5")
    print(f"CCR range: {min(ccr_values):.1f} → {max(ccr_values):.1f} ({len(ccr_values)} punti)")
    print(f"SLR range: {min(slr_values):.3f} → {max(slr_values):.3f}")
    print(f"Makespan range: {min(makespan_values):.3f} → {max(makespan_values):.3f}")
    
    increase = (max(slr_values) / min(slr_values) - 1) * 100
    print(f"Aumento SLR: +{increase:.1f}%")
    
    correlation = np.corrcoef(ccr_values, slr_values)[0, 1]
    print(f"Correlazione CCR↔SLR: {correlation:.3f}")

def modify_vm_count_to_5():
    """Modifica il file vm.csv per avere solo 5 VM"""
    vm_file = "../data/vm.csv"
    
    print("🔧 Modificando configurazione per 5 VM...")
    
    # Crea configurazione 5x5 VM
    vm_content = "# vm1 vm2 vm3 vm4 vm5\n"
    for i in range(5):
        row = [f"vm{i+1}"]
        for j in range(5):
            if i == j:
                bandwidth = 0.0
            else:
                bandwidth = 25.0  # 20-30 Mbps range
            row.append(f"{bandwidth:.1f}")
        vm_content += " ".join(row) + "\n"
    
    with open(vm_file, 'w') as f:
        f.write(vm_content)
    
    # Modifica processing_capacity.csv per 5 VM
    pc_file = "../data/processing_capacity.csv"
    pc_content = ""
    for i in range(5):
        capacity = 15.0  # 10-20 MIPS range
        pc_content += f"vm{i+1} {capacity:.1f}\n"
    
    with open(pc_file, 'w') as f:
        f.write(pc_content)
    
    print("✅ Configurazione aggiornata a 5 VM")

def main():
    if len(sys.argv) != 2:
        print("Uso: python3 single_dag_ccr_analysis.py <dag_type>")
        print("DAG disponibili: cybershake, epigenomics, ligo, montage")
        sys.exit(1)
    
    dag_type = sys.argv[1].lower()
    
    print(f"🔍 ANALISI CCR - {dag_type.upper()}")
    print("=" * 40)
    print("Configurazione: 50 task, 5 VM, CCR 0.4→2.0 (step 0.2)")
    print()
    
    # Genera DAG specifico
    if dag_type == "cybershake":
        print("🌊 Generando CyberShake...")
        subprocess.run(["python3", "creareDAG.py", "--sites", "6", "--sgt-variations", "2", "--psa-filters", "4"], check=True)
        dag_name = "CyberShake"
    
    elif dag_type == "epigenomics":
        print("🧬 Generando Epigenomics...")
        subprocess.run(["python3", "creareEpigenomics.py", "--samples", "2", "--chromosomes", "22"], check=True)
        dag_name = "Epigenomics"
    
    elif dag_type == "ligo":
        print("🌌 Generando LIGO...")
        subprocess.run(["python3", "creareLIGO.py", "--segments", "15"], check=True)
        dag_name = "LIGO"
    
    elif dag_type == "montage":
        print("🔭 Generando Montage...")
        subprocess.run(["python3", "montage_ccr_analysis.py", "--scale", "small", "--num_vms", "5"], check=True)
        dag_name = "Montage"
    
    else:
        print(f"❌ DAG '{dag_type}' non riconosciuto")
        sys.exit(1)
    
    # Modifica per 5 VM
    modify_vm_count_to_5()
    
    # Esegui analisi CCR
    data = run_ccr_analysis()
    if data is None:
        print("❌ Analisi fallita")
        sys.exit(1)
    
    # Crea grafico
    os.chdir("../generators")
    create_plot(data, dag_name)
    
    # Stampa riassunto
    print_summary(data, dag_name)
    
    print(f"\n🎉 Analisi {dag_name} completata!")

if __name__ == "__main__":
    main()
