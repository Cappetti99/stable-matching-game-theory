#!/usr/bin/env python3
import os
import sys
import csv
import networkx as nx
import matplotlib.pyplot as plt
from pathlib import Path
import glob
import xml.etree.ElementTree as ET

def list_available_dags(base_path="workflow"):
    """List all available DAG XML files."""
    xml_files = []
    for root, dirs, files in os.walk(base_path):
        for f in files:
            if f.endswith('.xml'):
                rel_path = os.path.relpath(os.path.join(root, f), base_path)
                xml_files.append(rel_path)
    return sorted(xml_files)

def load_dag_from_xml(xml_path):
    """Load DAG from Pegasus XML file."""
    if not os.path.exists(xml_path):
        print(f"File {xml_path} not found!")
        return None, None
    
    G = nx.DiGraph()
    job_info = {}  # Store job metadata (name, runtime, etc.)
    
    try:
        tree = ET.parse(xml_path)
        root = tree.getroot()
        
        # Handle XML namespace
        ns = {'pegasus': 'http://pegasus.isi.edu/schema/DAX'}
        
        # Parse jobs
        for job in root.findall('.//pegasus:job', ns):
            job_id = job.get('id')
            if job_id is None:
                continue
            job_name = job.get('name', 'unknown')
            runtime = float(job.get('runtime', '0') or '0')
            
            # Extract numeric ID from "ID00000" format
            if job_id.startswith('ID'):
                node_id = int(job_id[2:])
            else:
                node_id = job_id
            
            G.add_node(node_id)
            job_info[node_id] = {
                'id': job_id,
                'name': job_name,
                'runtime': runtime
            }
        
        # Parse dependencies (child-parent relationships)
        for child in root.findall('.//pegasus:child', ns):
            child_id = child.get('ref')
            if child_id is None:
                continue
            if child_id.startswith('ID'):
                child_node = int(child_id[2:])
            else:
                child_node = child_id
            
            for parent in child.findall('pegasus:parent', ns):
                parent_id = parent.get('ref')
                if parent_id is None:
                    continue
                if parent_id.startswith('ID'):
                    parent_node = int(parent_id[2:])
                else:
                    parent_node = parent_id
                
                # Edge goes from parent to child (dependency direction)
                G.add_edge(parent_node, child_node)
        
    except Exception as e:
        print(f"Error reading XML: {e}")
        import traceback
        traceback.print_exc()
        return None, None
        
    return G, job_info

def load_dag_from_csv(dag_path):
    """Load DAG from csv file (legacy support)."""
    dag_file = os.path.join(dag_path, "dag.csv")
    if not os.path.exists(dag_file):
        print(f"File {dag_file} not found!")
        return None, None
    
    G = nx.DiGraph()
    try:
        with open(dag_file, 'r') as f:
            reader = csv.DictReader(f)
            for row in reader:
                pred = row.get('pred') or row.get(' pred')
                succ = row.get('succ') or row.get(' succ')
                data = row.get('data') or row.get(' data')
                
                if pred and succ:
                    weight = float(data) if data else 0.0
                    G.add_edge(int(pred), int(succ), weight=weight)
    except Exception as e:
        print(f"Error reading CSV: {e}")
        return None, None
        
    return G, None

def hierarchy_pos(G, width=1., vert_gap=0.2, vert_loc=0, xcenter=0.5):
    """
    Position nodes in a hierarchical layout based on topological generations.
    """
    pos = {}
    
    try:
        generations = list(nx.topological_generations(G))
    except nx.NetworkXUnfeasible:
        print("Graph contains cycles! Using spring layout.")
        return nx.spring_layout(G)

    if not generations:
        return nx.spring_layout(G)
    
    for y, gen in enumerate(generations):
        layer_width = len(gen)
        x_start = xcenter - width * (layer_width - 1) / 2
        
        for i, node in enumerate(sorted(gen)):
            pos[node] = (x_start + i * width, -y * vert_gap + vert_loc)
            
    return pos

def visualize_dag(dag_path, base_path="workflow", show=True):
    """Visualize the specified DAG."""
    full_path = os.path.join(base_path, dag_path)
    print(f"Loading DAG from: {full_path}")
    
    # Determine file type and load accordingly
    if dag_path.endswith('.xml'):
        G, job_info = load_dag_from_xml(full_path)
    else:
        G, job_info = load_dag_from_csv(full_path)
    
    if G is None:
        return
    
    print(f"Graph stats: {G.number_of_nodes()} nodes, {G.number_of_edges()} edges")
    
    # Calculate graph metrics
    if G.number_of_nodes() > 0:
        sources = [n for n in G.nodes() if G.in_degree(n) == 0]
        sinks = [n for n in G.nodes() if G.out_degree(n) == 0]
        print(f"Entry nodes (sources): {len(sources)} - {sources[:5]}{'...' if len(sources) > 5 else ''}")
        print(f"Exit nodes (sinks): {len(sinks)} - {sinks[:5]}{'...' if len(sinks) > 5 else ''}")
        
        try:
            longest_path = nx.dag_longest_path_length(G)
            print(f"Critical path length: {longest_path} edges")
        except Exception:
            pass
    
    # Adjust figure size based on number of nodes
    n_nodes = G.number_of_nodes()
    fig_width = max(12, n_nodes * 0.3)
    fig_height = max(10, n_nodes * 0.2)
    
    plt.figure(figsize=(min(fig_width, 20), min(fig_height, 16)))
    
    # Layout
    pos = hierarchy_pos(G, width=0.8, vert_gap=0.8)
    
    # Color nodes by type if we have job info
    node_colors = []
    if job_info:
        color_map = {
            'ExtractSGT': '#ff6b6b',      # Red
            'SeismogramSynthesis': '#4ecdc4',  # Teal
            'PeakValCalcOkaya': '#45b7d1',    # Blue
            'ZipPSA': '#96ceb4',              # Green
            'ZipSeis': '#ffeaa7',             # Yellow
        }
        for node in G.nodes():
            if node in job_info:
                job_name = job_info[node]['name']
                node_colors.append(color_map.get(job_name, '#dfe6e9'))
            else:
                node_colors.append('#dfe6e9')
    else:
        node_colors = ['#74b9ff'] * G.number_of_nodes()
    
    # Draw nodes
    nx.draw_networkx_nodes(G, pos, node_size=400, node_color=node_colors, 
                          alpha=0.9, edgecolors='black', linewidths=1)
    
    # Draw edges
    nx.draw_networkx_edges(G, pos, edge_color='gray', arrows=True, 
                          arrowsize=15, alpha=0.6, connectionstyle="arc3,rad=0.1")
    
    # Create labels
    if job_info:
        labels = {node: f"{node}\n{job_info[node]['name'][:8]}" 
                 for node in G.nodes() if node in job_info}
    else:
        labels = {node: str(node) for node in G.nodes()}
    
    nx.draw_networkx_labels(G, pos, labels, font_size=7, font_family="sans-serif")
    
    # Title
    dag_name = os.path.basename(dag_path).replace('.xml', '')
    plt.title(f"DAG Visualization: {dag_name}\n({G.number_of_nodes()} tasks, {G.number_of_edges()} dependencies)", 
              fontsize=14, fontweight='bold')
    plt.axis('off')
    
    # Add legend if we have job types
    if job_info:
        from matplotlib.patches import Patch
        unique_jobs = set(job_info[n]['name'] for n in job_info)
        color_map = {
            'ExtractSGT': '#ff6b6b',
            'SeismogramSynthesis': '#4ecdc4',
            'PeakValCalcOkaya': '#45b7d1',
            'ZipPSA': '#96ceb4',
            'ZipSeis': '#ffeaa7',
        }
        legend_elements = [Patch(facecolor=color_map.get(job, '#dfe6e9'), 
                                edgecolor='black', label=job) 
                         for job in unique_jobs if job in color_map]
        if legend_elements:
            plt.legend(handles=legend_elements, loc='upper left', fontsize=8)
    
    # Save to file
    output_file = f"assets/dag_{dag_name}.png"
    Path("assets").mkdir(exist_ok=True)
    plt.savefig(output_file, dpi=300, bbox_inches='tight')
    print(f"Visualization saved to {output_file}")
    
    if show:
        plt.show()
    else:
        plt.close()

def print_dag_info(dag_path, base_path="workflow"):
    """Print detailed DAG information."""
    full_path = os.path.join(base_path, dag_path)
    
    if dag_path.endswith('.xml'):
        G, job_info = load_dag_from_xml(full_path)
    else:
        G, job_info = load_dag_from_csv(full_path)
    
    if G is None:
        return
    
    print("\n" + "="*50)
    print(f"DAG: {dag_path}")
    print("="*50)
    print(f"Total tasks: {G.number_of_nodes()}")
    print(f"Total dependencies: {G.number_of_edges()}")
    
    if job_info:
        # Count by job type
        job_types = {}
        total_runtime = 0.0
        for node, info in job_info.items():
            jtype = info['name']
            job_types[jtype] = job_types.get(jtype, 0) + 1
            total_runtime += info['runtime']
        
        print(f"\nTask types:")
        for jtype, count in sorted(job_types.items()):
            print(f"  - {jtype}: {count}")
        
        print(f"\nTotal runtime (sum): {total_runtime:.2f}s")
        
        # Critical path
        try:
            critical_path = nx.dag_longest_path(G)
            critical_runtime = sum(job_info[n]['runtime'] for n in critical_path if n in job_info)
            print(f"Critical path length: {len(critical_path)} tasks")
            print(f"Critical path runtime: {critical_runtime:.2f}s")
        except Exception:
            pass
    
    print("="*50 + "\n")

if __name__ == "__main__":
    import argparse
    
    parser = argparse.ArgumentParser(description='Visualize Pegasus DAG workflows')
    parser.add_argument('dag_name', nargs='?', default=None, help='DAG file name or path (omit to visualize all)')
    parser.add_argument('--no-show', action='store_true', help='Do not show interactive window (just save to file)')
    parser.add_argument('--show', action='store_true', help='Show interactive window (default: auto-detect)')
    parser.add_argument('--list', action='store_true', help='List available DAGs')
    parser.add_argument('--all', action='store_true', help='Visualize all DAGs')
    parser.add_argument('--base-path', default='workflow', help='Base path for workflow files')
    
    args = parser.parse_args()
    base_path = args.base_path
    dags = list_available_dags(base_path)
    
    if args.list:
        print("Available DAGs in workflow/:")
        print("-" * 40)
        for i, d in enumerate(dags, 1):
            print(f"  {i}. {d}")
        print("-" * 40)
        sys.exit(0)
    
    # Determine show mode
    if args.no_show:
        show = False
    elif args.show:
        show = True
    else:
        # Don't show interactively when processing all DAGs
        show = False if (args.all or not args.dag_name) else True
    
    dag_name = args.dag_name
    
    # If no DAG specified or --all flag, visualize all DAGs
    if args.all or not dag_name:
        if not dags:
            print("\nNo XML DAG files found in workflow/ directory.")
            sys.exit(1)
        
        print(f"Visualizing all {len(dags)} DAGs...")
        print("=" * 60)
        
        for i, dag in enumerate(dags, 1):
            print(f"\n[{i}/{len(dags)}] Processing: {dag}")
            print_dag_info(dag, base_path)
            visualize_dag(dag, base_path, show=False)
        
        print("\n" + "=" * 60)
        print(f"Successfully visualized all {len(dags)} DAGs!")
        print(f"Output directory: assets/")
        sys.exit(0)
    
    # Single DAG mode
    if dag_name:
        # Check if it's a full path or just a name
        if not dag_name.endswith('.xml') and not os.path.exists(os.path.join(base_path, dag_name)):
            # Try to find it
            matches = [d for d in dags if dag_name in d]
            if matches:
                dag_name = matches[0]
            else:
                print(f"DAG '{dag_name}' not found!")
                sys.exit(1)
        
        # Print info and visualize
        print_dag_info(dag_name, base_path)
        visualize_dag(dag_name, base_path, show=show)
