#!/usr/bin/env python3
import csv
import os
import random
import shutil
from pathlib import Path

class SyntheticWorkflowGenerator:
    def __init__(self, output_base_dir="data_pegasus"):
        self.output_base_dir = Path(output_base_dir)
        self.output_base_dir.mkdir(exist_ok=True, parents=True)

    def save_dag(self, name, nodes, edges, task_sizes, edge_weights):
        output_dir = self.output_base_dir / name
        output_dir.mkdir(exist_ok=True, parents=True)
        
        print(f"💾 Saving {name} to {output_dir}...")
        
        # 1. task.csv
        with open(output_dir / "task.csv", "w") as f:
            writer = csv.writer(f)
            writer.writerow(["id", "size"])
            for n in nodes:
                writer.writerow([n, f"{task_sizes[n]:.2f}"])
                
        # 2. dag.csv
        with open(output_dir / "dag.csv", "w") as f:
            writer = csv.writer(f)
            writer.writerow(["pred", "succ", "data"])
            for u, v in edges:
                w = edge_weights.get((u, v), 1.0)
                writer.writerow([u, v, f"{w:.2f}"])
                
        # 3. vm.csv & processing_capacity.csv (Defaults)
        with open(output_dir / "vm.csv", "w") as f:
            f.write("id,mips,bandwidth\n")
            for i in range(1, 6): # 5 VMs default
                f.write(f"{i},1000,100\n")
                
        with open(output_dir / "processing_capacity.csv", "w") as f:
            f.write("vm_id,task_id,capacity\n")
            for i in range(1, 6):
                for n in nodes:
                    f.write(f"{i},{n},1000\n")

    def generate_cybershake(self, num_tasks):
        """
        Genera un workflow CyberShake sintetico.
        Struttura: N pipeline parallele da 5 task ciascuna.
        """
        tasks_per_row = 5
        rows = num_tasks // tasks_per_row
        
        # Se non è divisibile esattamente, aggiungiamo una riga parziale o adattiamo
        # Per ora forziamo multipli di 5 per semplicità strutturale
        if num_tasks % tasks_per_row != 0:
            print(f"⚠️ CyberShake structure requires multiples of 5. Adjusting {num_tasks} -> {rows * 5}")
            
        actual_tasks = rows * tasks_per_row
        name = f"cybershake_{actual_tasks}tasks"
        
        nodes = []
        edges = []
        task_sizes = {}
        edge_weights = {}
        
        current_id = 1
        
        for _ in range(rows):
            # Pipeline structure:
            # T1 (ExtractSGT) -> T2 (SeismogramSynthesis) -> T3 (PeakValCalc)
            # T1 -> T5 (ZipSGT)
            # T2 -> T4 (ZipSeis)
            
            t1 = current_id
            t2 = current_id + 1
            t3 = current_id + 2
            t4 = current_id + 3
            t5 = current_id + 4
            
            row_nodes = [t1, t2, t3, t4, t5]
            nodes.extend(row_nodes)
            
            # Edges
            new_edges = [
                (t1, t2), # Extract -> Seis
                (t2, t3), # Seis -> Peak
                (t2, t4), # Seis -> ZipSeis
                (t1, t5)  # Extract -> ZipSGT
            ]
            edges.extend(new_edges)
            
            # Sizes (Randomized but realistic relative sizes)
            task_sizes[t1] = random.uniform(20, 40)  # ExtractSGT
            task_sizes[t2] = random.uniform(80, 120) # SeismogramSynthesis (Heavy)
            task_sizes[t3] = random.uniform(5, 15)   # PeakValCalc (Light)
            task_sizes[t4] = random.uniform(10, 20)  # ZipSeis
            task_sizes[t5] = random.uniform(10, 20)  # ZipSGT
            
            # Edge Weights (Data transfer)
            edge_weights[(t1, t2)] = random.uniform(50, 100) # Large SGT files
            edge_weights[(t2, t3)] = random.uniform(1, 5)    # Small values
            edge_weights[(t2, t4)] = random.uniform(10, 30)  # Seismograms
            edge_weights[(t1, t5)] = random.uniform(10, 30)  # SGTs
            
            current_id += 5
            
        self.save_dag(name, nodes, edges, task_sizes, edge_weights)
        return name

if __name__ == "__main__":
    gen = SyntheticWorkflowGenerator()
    
    # Genera CyberShake 30 nodi
    name = gen.generate_cybershake(30)
    print(f"✅ Generated {name}")
