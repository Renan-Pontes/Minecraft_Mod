"""Valida que todos os .json (e .mcmeta) dentro das pastas informadas são JSON válidos (UTF-8)."""
import json
import os
import sys

bad = 0
count = 0
for root_dir in sys.argv[1:]:
    if not os.path.isdir(root_dir):
        continue
    for dirpath, _, files in os.walk(root_dir):
        for name in files:
            if not (name.endswith(".json") or name.endswith(".mcmeta")):
                continue
            path = os.path.join(dirpath, name)
            count += 1
            try:
                with open(path, encoding="utf-8") as f:
                    json.load(f)
            except Exception as e:
                bad += 1
                print("JSON INVALIDO:", path, "->", e)
print(f"{count} arquivos JSON verificados, {bad} com erro")
sys.exit(1 if bad else 0)
