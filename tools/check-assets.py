"""
Verificações leves de um módulo (só AVISOS, não falha o check):
  1. chaves de tradução literais usadas no Java do módulo que não existem em nenhum src/main/lang/*/pt_br.json ou en_us.json
  2. glifos registrados no módulo (super("id", ...)) sem "glyph.isekai_mahou.<id>" / ".desc"
  3. modelos em assets/isekai_mahou/models que apontam para texturas isekai_mahou que não existem
Uso: py -I tools/check-assets.py <raiz_do_projeto> <modulo>
"""
import json
import os
import re
import sys

root, mod = sys.argv[1], sys.argv[2]
java_dir = os.path.join(root, "src/main/java/com/renanpontes/isekaimahou/feature", mod)
lang_root = os.path.join(root, "src/main/lang")
assets = os.path.join(root, "src/main/resources/assets/isekai_mahou")

langs = {"pt_br": {}, "en_us": {}}
for d in os.listdir(lang_root):
    for lg in langs:
        p = os.path.join(lang_root, d, lg + ".json")
        if os.path.exists(p):
            try:
                langs[lg].update(json.load(open(p, encoding="utf-8")))
            except Exception:
                pass

src = ""
for dp, _, files in os.walk(java_dir):
    for f in files:
        if f.endswith(".java"):
            src += open(os.path.join(dp, f), encoding="utf-8").read() + "\n"

warn = 0
keys = set(re.findall(r'translatable\(\s*"([a-z0-9_.]+)"', src))
for k in sorted(keys):
    for lg, m in langs.items():
        if k not in m:
            print(f"AVISO tradução faltando [{lg}]: {k}")
            warn += 1

glyph_ids = set(re.findall(r'super\(\s*"([a-z0-9_]+)"\s*,\s*-?\d+', src))
for g in sorted(glyph_ids):
    for suffix in ("", ".desc"):
        k = f"glyph.isekai_mahou.{g}{suffix}"
        for lg, m in langs.items():
            if k not in m:
                print(f"AVISO glifo sem tradução [{lg}]: {k}")
                warn += 1

tex_root = os.path.join(assets, "textures")
for sub in ("models/item", "models/block"):
    d = os.path.join(assets, sub)
    if not os.path.isdir(d):
        continue
    for f in os.listdir(d):
        if not f.endswith(".json"):
            continue
        try:
            m = json.load(open(os.path.join(d, f), encoding="utf-8"))
        except Exception:
            continue
        for t in (m.get("textures") or {}).values():
            if isinstance(t, str) and t.startswith("isekai_mahou:"):
                path = os.path.join(tex_root, t.split(":", 1)[1] + ".png")
                if not os.path.exists(path):
                    print(f"AVISO textura não existe: {t} (usada em {sub}/{f})")
                    warn += 1
print(f"{warn} avisos de assets/traduções")
