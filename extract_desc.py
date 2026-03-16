import os
import re

impl_dir = r"C:\Users\ardas\IdeaProjects\Spellbreak\src\main\java\me\ratatamakata\spellbreak\abilities\impl"
files = [f for f in os.listdir(impl_dir) if f.endswith(".java")]

descriptions = []
for file in files:
    with open(os.path.join(impl_dir, file), 'r', encoding='utf-8') as f:
        content = f.read()
    
    # regex to find getDescription method and its return value
    m = re.search(r'public String getDescription\(\)\s*\{\s*return\s*"(.*?)";\s*\}', content, re.DOTALL)
    if m:
        descriptions.append(file + ": " + m.group(1))

with open(r"C:\Users\ardas\IdeaProjects\Spellbreak\desc_out.txt", 'w', encoding='utf-8') as f:
    f.write('\n'.join(descriptions))
