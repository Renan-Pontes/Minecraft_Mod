"""
v1.3.2 — Barragan em Arrogante usa o modelo do Rei Vazio (hollow_king.geo.json). As animacoes do modelo do rei sao
copiadas com os NOMES do Barragan (animation.barragan.*), para o mesmo controlador do chefe tocar tudo sem trocar nada
no Java. Rode de novo se as animacoes do Rei mudarem.

Uso:  py -I tools/gen/espada_arrogante_anims.py
"""
import copy
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
ANIM = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/animations/entity")

# nome no Barragan <- nome no Rei Vazio
MAP = {
    "idle": "idle", "walk": "walk", "sleep": "sleep", "intro": "intro", "death": "death", "fly": "idle",
    "transform": "resurrect", "release": "resurrect",
    "axe_smash": "cleave", "axe_throw": "throw", "respira": "breath", "grab": "breath", "dome": "respira",
    "summon": "summon",
    # golpes do Rei que o Barragan ganha em Arrogante
    "ruin": "slam", "respira_domain": "respira", "pools": "breath",
}


def main():
    with open(os.path.join(ANIM, "hollow_king.animation.json"), encoding="utf-8") as f:
        king = json.load(f)
    src = king["animations"]
    out = {}
    for name, k in MAP.items():
        a = src.get("animation.hollow_king." + k)
        if a is None:
            raise SystemExit("falta animation.hollow_king." + k)
        out["animation.barragan." + name] = copy.deepcopy(a)
    with open(os.path.join(ANIM, "barragan_arrogante.animation.json"), "w", encoding="utf-8") as f:
        json.dump({"format_version": king.get("format_version", "1.8.0"), "animations": out}, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("ok:", len(out), "animacoes")


if __name__ == "__main__":
    main()
