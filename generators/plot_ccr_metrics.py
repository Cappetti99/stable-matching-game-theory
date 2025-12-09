#!/usr/bin/env python3
"""
Plot CCR vs SLR and CCR vs AVU from experiment results
"""

import json
import matplotlib.pyplot as plt
import numpy as np
from collections import defaultdict

# Read results
with open('results/experiments_results.json', 'r') as f:
    data_json = json.load(f)
    results = data_json['experiments']

# Group by workflow and CCR
workflows = set()
ccr_values = set()
data = defaultdict(lambda: defaultdict(lambda: {'slr': [], 'avu': []}))

for result in results:
    workflow = result['workflow']
    ccr = result['ccr']
    slr = result['slr']
    avu = result['avu']
    
    workflows.add(workflow)
    ccr_values.add(ccr)
    
    data[workflow][ccr]['slr'].append(slr)
    data[workflow][ccr]['avu'].append(avu)

workflows = sorted(workflows)
ccr_values = sorted(ccr_values)

# Compute averages
avg_data = defaultdict(lambda: defaultdict(lambda: {'slr': 0, 'avu': 0}))
for workflow in workflows:
    for ccr in ccr_values:
        if ccr in data[workflow]:
            avg_data[workflow][ccr]['slr'] = np.mean(data[workflow][ccr]['slr'])
            avg_data[workflow][ccr]['avu'] = np.mean(data[workflow][ccr]['avu'])

# Create plots
fig, (ax1, ax2) = plt.subplots(1, 2, figsize=(14, 5))

# Plot 1: SLR vs CCR
for workflow in workflows:
    ccrs = []
    slrs = []
    for ccr in ccr_values:
        if ccr in avg_data[workflow]:
            ccrs.append(ccr)
            slrs.append(avg_data[workflow][ccr]['slr'])
    ax1.plot(ccrs, slrs, marker='o', label=workflow.upper(), linewidth=2)

ax1.set_xlabel('CCR (Communication-to-Computation Ratio)', fontsize=12)
ax1.set_ylabel('SLR (Schedule Length Ratio)', fontsize=12)
ax1.set_title('Impact of CCR on Schedule Length Ratio', fontsize=14, fontweight='bold')
ax1.legend()
ax1.grid(True, alpha=0.3)
ax1.set_xticks(ccr_values)

# Plot 2: AVU vs CCR
for workflow in workflows:
    ccrs = []
    avus = []
    for ccr in ccr_values:
        if ccr in avg_data[workflow]:
            ccrs.append(ccr)
            avus.append(avg_data[workflow][ccr]['avu'])
    ax2.plot(ccrs, avus, marker='s', label=workflow.upper(), linewidth=2)

ax2.set_xlabel('CCR (Communication-to-Computation Ratio)', fontsize=12)
ax2.set_ylabel('AVU (Average VM Utilization)', fontsize=12)
ax2.set_title('Impact of CCR on VM Utilization', fontsize=14, fontweight='bold')
ax2.legend()
ax2.grid(True, alpha=0.3)
ax2.set_xticks(ccr_values)
ax2.set_ylim(0, 1)

plt.tight_layout()
plt.savefig('results/ccr_analysis.png', dpi=300, bbox_inches='tight')
print("✅ Grafici salvati in results/ccr_analysis.png")

# Print summary statistics
print("\n" + "="*70)
print("📊 SUMMARY STATISTICS")
print("="*70)
for workflow in workflows:
    print(f"\n{workflow.upper()}")
    print("-" * 50)
    for ccr in ccr_values:
        if ccr in avg_data[workflow]:
            slr = avg_data[workflow][ccr]['slr']
            avu = avg_data[workflow][ccr]['avu']
            print(f"  CCR={ccr:3.1f}: SLR={slr:6.4f}, AVU={avu:6.4f} ({avu*100:5.2f}%)")

plt.show()
