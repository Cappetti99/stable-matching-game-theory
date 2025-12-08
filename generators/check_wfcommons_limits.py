#!/usr/bin/env python3
from wfcommons import EpigenomicsRecipe, MontageRecipe, SrasearchRecipe
from wfcommons.wfgen import WorkflowGenerator
import sys

def check_min_size():
    recipes = {
        "Epigenomics": EpigenomicsRecipe,
        "Montage": MontageRecipe,
        "LIGO (SraSearch)": SrasearchRecipe
    }
    
    target = 30
    
    for name, RecipeClass in recipes.items():
        print(f"Testing {name} with {target} tasks...")
        try:
            recipe = RecipeClass.from_num_tasks(target)
            generator = WorkflowGenerator(recipe)
            workflow = generator.build_workflow()
            print(f"✅ {name}: Success! Generated {len(workflow.nodes)} tasks.")
        except Exception as e:
            print(f"❌ {name}: Failed. {str(e)}")

if __name__ == "__main__":
    check_min_size()
