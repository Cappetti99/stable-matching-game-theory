#!/usr/bin/env python3
import os
import sys
import csv
import networkx as nx
import matplotlib.pyplot as plt
from pathlib import Path
import glob

def list_available_dags(base_path="data_pegasus"):
    """List all available DAG directories."""
    return sorted([os.path.basename(p) for p in glob.glob(os.path.join(base_path, "*")) if os.path.isdir(p)])

def load_dag(dag_path):
    """Load DAG from csv file."""
    dag_file = os.path.join(dag_path, "dag.csv")
    if not os.path.exists(dag_file):
        print(f"❌ File {dag_file} not found!")
        return None
    
    G = nx.DiGraph()
    try:
        with open(dag_file, 'r') as f:
            reader = csv.DictReader(f)
            for row in reader:
                # Check if keys exist, handle potential whitespace
                pred = row.get('pred') or row.get(' pred')
                succ = row.get('succ') or row.get(' succ')
                data = row.get('data') or row.get(' data')
                
                if pred and succ:
                    weight = float(data) if data else 0.0
                    G.add_edge(int(pred), int(succ), weight=weight)
    except Exception as e:
        print(f"Error reading CSV: {e}")
        return None
        
    return G

def hierarchy_pos(G, root=None, width=1., vert_gap = 0.2, vert_loc = 0, xcenter = 0.5):
    """
    If the graph is a tree this will return the positions to plot this in a 
    hierarchical layout.
    """
    # NOTE: This is a simplified version. For general DAGs, we can use generations.
    # A better approach for DAGs is to use topological generations.
    
    pos = {}
    
    # Calculate topological generations
    try:
        generations = list(nx.topological_generations(G))
    except nx.NetworkXUnfeasible:
        print("⚠️ Graph contains cycles! Using spring layout.")
        return nx.spring_layout(G)

    # Assign y coordinates based on generation
    # Assign x coordinates to spread nodes in each generation
    
    max_width = max(len(gen) for gen in generations)
    
    for y, gen in enumerate(generations):
        # y is the vertical layer (0 is top)
        # We want 0 at top, so we can use -y or invert later.
        # Let's use -y for vertical position.
        
        layer_width = len(gen)
        # Center the layer
        x_start = xcenter - width * (layer_width - 1) / 2
        
        for i, node in enumerate(gen):
            pos[node] = (x_start + i * width, -y * vert_gap + vert_loc)
            
    return pos

def visualize_dag(dag_name, base_path="data_pegasus"):
    """Visualize the specified DAG."""
    dag_path = os.path.join(base_path, dag_name)
    print(f"🔍 Loading DAG from: {dag_path}")
    
    G = load_dag(dag_path)
    if G is None:
        return

    print(f"📊 Graph stats: {G.number_of_nodes()} nodes, {G.number_of_edges()} edges")

    plt.figure(figsize=(12, 8))
    
    # Layout
    # Try to find a root or use topological sort for layout
    pos = hierarchy_pos(G, width=0.5, vert_gap=0.5)
    
    # Draw
    nx.draw_networkx_nodes(G, pos, node_size=300, node_color='lightblue', alpha=0.9)
    nx.draw_networkx_edges(G, pos, edge_color='gray', arrows=True, arrowsize=15, alpha=0.6)
    nx.draw_networkx_labels(G, pos, font_size=8, font_family="sans-serif")
    
    plt.title(f"DAG Visualization: {dag_name}")
    plt.axis('off')
    
    output_file = f"assets/dag_{dag_name}.png"
    Path("assets").mkdir(exist_ok=True)
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"✅ Visualization saved to {output_file}")
    # plt.show()

if __name__ == "__main__":
    dags = list_available_dags()
    
    if len(sys.argv) > 1:
        dag_name = sys.argv[1]
    else:
        print("Available DAGs:")
        for i, d in enumerate(dags):
            print(f"{i+1}. {d}")
        
        # Default to the first one or ask user
        # For automation, I'll pick a small one if available, e.g., montage_100tasks
        default_dag = "montage_100tasks"
        if default_dag in dags:
            dag_name = default_dag
        else:
            dag_name = dags[0]
        
        print(f"\nNo DAG specified. Defaulting to: {dag_name}")
        print("Usage: python3 generators/visualize_dag.py <dag_name>")

    visualize_dag(dag_name)
