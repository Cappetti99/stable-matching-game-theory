#!/usr/bin/env python3

"""
Verifica semplificata della correttezza dei DAG generati
Analizza la struttura senza dipendenze esterne
"""

import os
from collections import defaultdict, deque

def load_dag_from_files(data_dir="../data"):
    """Carica DAG dai file CSV"""
    task_file = os.path.join(data_dir, "task.csv")
    dag_file = os.path.join(data_dir, "dag.csv")
    
    # Carica task
    tasks = {}
    with open(task_file, 'r') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#'):
                parts = line.split()
                task_id = parts[0]
                size = float(parts[1])
                tasks[task_id] = size
    
    # Carica dipendenze
    edges = []
    adj_list = defaultdict(list)  # from -> [to1, to2, ...]
    in_degree = defaultdict(int)
    
    with open(dag_file, 'r') as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith('#'):
                parts = line.split()
                from_task = parts[0]
                to_task = parts[1]
                edges.append((from_task, to_task))
                adj_list[from_task].append(to_task)
                in_degree[to_task] += 1
                # Assicura che tutti i task abbiano una entry
                if from_task not in in_degree:
                    in_degree[from_task] = 0
    
    return tasks, edges, adj_list, in_degree

def is_dag_valid(tasks, adj_list, in_degree):
    """Verifica se il grafo è un DAG valido usando Kahn's algorithm"""
    
    # Copia in_degree per non modificare l'originale
    temp_in_degree = dict(in_degree)
    
    # Trova nodi con in_degree 0 (radici)
    queue = deque([task for task in tasks.keys() if temp_in_degree.get(task, 0) == 0])
    processed = 0
    
    while queue:
        current = queue.popleft()
        processed += 1
        
        # Rimuovi archi uscenti
        for neighbor in adj_list[current]:
            temp_in_degree[neighbor] -= 1
            if temp_in_degree[neighbor] == 0:
                queue.append(neighbor)
    
    return processed == len(tasks)

def analyze_dag_structure(tasks, edges, adj_list, in_degree):
    """Analizza la struttura del DAG"""
    
    # Trova radici e foglie
    roots = [task for task in tasks.keys() if in_degree.get(task, 0) == 0]
    leaves = [task for task in tasks.keys() if len(adj_list[task]) == 0]
    
    # Calcola profondità usando BFS
    max_depth = 0
    if roots:
        depths = {}
        queue = deque([(root, 0) for root in roots])
        
        while queue:
            task, depth = queue.popleft()
            if task in depths:
                depths[task] = max(depths[task], depth)
            else:
                depths[task] = depth
            max_depth = max(max_depth, depth)
            
            for neighbor in adj_list[task]:
                queue.append((neighbor, depth + 1))
    
    # Calcola critical path length (approssimazione)
    critical_path_length = 0
    if roots and leaves:
        # BFS dalla radice per trovare il cammino più lungo
        max_path = 0
        for root in roots:
            visited = set()
            queue = deque([(root, 1)])
            
            while queue:
                task, path_len = queue.popleft()
                if task in visited:
                    continue
                visited.add(task)
                
                if task in leaves:
                    max_path = max(max_path, path_len)
                
                for neighbor in adj_list[task]:
                    if neighbor not in visited:
                        queue.append((neighbor, path_len + 1))
        
        critical_path_length = max_path
    
    analysis = {
        'num_tasks': len(tasks),
        'num_dependencies': len(edges),
        'is_dag': is_dag_valid(tasks, adj_list, in_degree),
        'num_roots': len(roots),
        'num_leaves': len(leaves),
        'max_depth': max_depth,
        'roots': roots,
        'leaves': leaves,
        'critical_path_length': critical_path_length
    }
    
    return analysis

def print_dag_analysis(workflow_type, analysis):
    """Stampa l'analisi del DAG"""
    
    print(f"\n🔍 ANALISI DAG - {workflow_type.upper()}")
    print("=" * 50)
    print(f"📊 Task totali: {analysis['num_tasks']}")
    print(f"🔗 Dipendenze: {analysis['num_dependencies']}")
    print(f"✅ È un DAG valido: {'Sì' if analysis['is_dag'] else 'No'}")
    print(f"🌱 Task radice: {analysis['num_roots']} ({', '.join(analysis['roots'][:5])}{'...' if len(analysis['roots']) > 5 else ''})")
    print(f"🍃 Task foglia: {analysis['num_leaves']} ({', '.join(analysis['leaves'][:5])}{'...' if len(analysis['leaves']) > 5 else ''})")
    print(f"📏 Profondità massima: {analysis['max_depth']} livelli")
    print(f"🎯 Critical Path: {analysis['critical_path_length']} task")
    
    # Calcola densità del grafo
    max_edges = analysis['num_tasks'] * (analysis['num_tasks'] - 1)
    density = analysis['num_dependencies'] / max_edges if max_edges > 0 else 0
    print(f"📈 Densità grafo: {density:.3f} ({analysis['num_dependencies']}/{max_edges})")
    
    # Verifica correttezza
    issues = []
    if not analysis['is_dag']:
        issues.append("❌ Il grafo contiene cicli!")
    if analysis['num_roots'] == 0:
        issues.append("⚠️  Nessun task di inizio (potrebbe essere ciclico)")
    if analysis['num_leaves'] == 0:
        issues.append("⚠️  Nessun task finale (potrebbe essere ciclico)")
    if analysis['num_tasks'] < 20:
        issues.append(f"⚠️  DAG molto piccolo: {analysis['num_tasks']} task")
    if analysis['num_dependencies'] < analysis['num_tasks'] - 1:
        issues.append(f"⚠️  Potrebbe essere sconnesso: {analysis['num_dependencies']} archi per {analysis['num_tasks']} task")
    
    if issues:
        print(f"\n🚨 PROBLEMI RILEVATI:")
        for issue in issues:
            print(f"   {issue}")
    else:
        print(f"\n✅ DAG strutturalmente corretto!")
    
    # Classificazione tipo DAG
    if analysis['num_roots'] == 1 and analysis['num_leaves'] == 1:
        dag_type = "Pipeline (1 inizio → 1 fine)"
    elif analysis['num_roots'] == 1:
        dag_type = "Fan-out (1 inizio → molti fini)"
    elif analysis['num_leaves'] == 1:
        dag_type = "Fan-in (molti inizi → 1 fine)"
    else:
        dag_type = "Misto (molti inizi → molti fini)"
    
    print(f"🏗️  Tipo struttura: {dag_type}")

def analyze_task_distribution(tasks):
    """Analizza distribuzione dimensioni task"""
    
    sizes = list(tasks.values())
    if not sizes:
        return
    
    print(f"\n📊 DISTRIBUZIONE TASK:")
    print(f"   Dimensione min: {min(sizes):.1f}")
    print(f"   Dimensione max: {max(sizes):.1f}")
    print(f"   Dimensione media: {sum(sizes)/len(sizes):.1f}")
    
    # Istogramma semplice
    size_ranges = [0, 5, 10, 15, 20, 25, float('inf')]
    range_names = ['0-5', '5-10', '10-15', '15-20', '20-25', '25+']
    histogram = [0] * (len(size_ranges) - 1)
    
    for size in sizes:
        for i in range(len(size_ranges) - 1):
            if size_ranges[i] <= size < size_ranges[i+1]:
                histogram[i] += 1
                break
    
    print(f"   Distribuzione:")
    for i, (range_name, count) in enumerate(zip(range_names, histogram)):
        if count > 0:
            bar = '█' * (count * 30 // max(histogram))
            print(f"     {range_name:>6}: {count:3d} {bar}")

def identify_workflow_type():
    """Identifica il tipo di workflow dai file"""
    
    task_file = "../data/task.csv"
    try:
        with open(task_file, 'r') as f:
            first_line = f.readline()
            if 'Epigenomics' in first_line:
                return 'Epigenomics'
            elif 'CyberShake' in first_line:
                return 'CyberShake'
            elif 'Montage' in first_line:
                return 'Montage'
            elif 'LIGO' in first_line:
                return 'LIGO'
            else:
                return 'Unknown'
    except:
        return 'Unknown'

def print_sample_dependencies(edges, adj_list):
    """Stampa campione delle dipendenze"""
    
    print(f"\n🔗 DIPENDENZE (primi 10):")
    for i, (from_task, to_task) in enumerate(edges[:10]):
        print(f"   {from_task} → {to_task}")
    
    if len(edges) > 10:
        print(f"   ... e altre {len(edges)-10} dipendenze")
    
    # Mostra task con più dipendenze
    max_out = max((len(adj_list[task]), task) for task in adj_list.keys()) if adj_list else (0, "none")
    if max_out[0] > 0:
        print(f"\n📤 Task con più uscite: {max_out[1]} ({max_out[0]} dipendenze)")

def main():
    print("🔍 VERIFICA CORRETTEZZA DAG")
    print("=" * 40)
    
    # Identifica tipo di workflow
    workflow_type = identify_workflow_type()
    print(f"🏷️  Tipo workflow: {workflow_type}")
    
    try:
        # Carica DAG
        tasks, edges, adj_list, in_degree = load_dag_from_files()
        print(f"📂 Caricati {len(tasks)} task e {len(edges)} dipendenze")
        
        # Analizza struttura
        analysis = analyze_dag_structure(tasks, edges, adj_list, in_degree)
        
        # Stampa risultati
        print_dag_analysis(workflow_type, analysis)
        
        # Analizza distribuzione task
        analyze_task_distribution(tasks)
        
        # Mostra dipendenze campione
        print_sample_dependencies(edges, adj_list)
        
        # Riassunto finale
        if analysis['is_dag']:
            print(f"\n🎉 VERDETTO: DAG {workflow_type} è strutturalmente valido!")
            print(f"   ✅ {analysis['num_tasks']} task organizzati in {analysis['max_depth']+1} livelli")
            print(f"   ✅ {analysis['num_dependencies']} dipendenze senza cicli")
            print(f"   ✅ Critical path di {analysis['critical_path_length']} task")
        else:
            print(f"\n❌ VERDETTO: DAG {workflow_type} ha problemi strutturali!")
            
    except FileNotFoundError as e:
        print(f"❌ File non trovato: {e}")
        print("💡 Genera prima un DAG con:")
        print("   python3 single_dag_ccr_analysis.py <tipo_dag>")
    except Exception as e:
        print(f"❌ Errore: {e}")
        import traceback
        traceback.print_exc()

if __name__ == "__main__":
    main()
