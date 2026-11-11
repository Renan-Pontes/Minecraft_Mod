"""
Assets do pacote de técnicas de anime do Ferreiro (class_smith).
Uso (na raiz do projeto):  py -I tools/gen/class_smith_techniques.py

Saídas:
  - models/item/smith_projection.json: a Projeção (Traçar: Ligar) é desenhada por um BEWLR que mostra o modelo da arma
    copiada, então o modelo é "builtin/entity" SEM transformações próprias (as da arma copiada valem) e luz de frente no
    inventário. A partícula reaproveita a textura da Lâmina Forjada.
As entidades novas (smith_blade, smith_rho_aias, smith_blade_world) são desenhadas em código (modelo do item, pétalas de
luz e partículas) e não precisam de arquivo.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    write_json(os.path.join(A, "models", "item", "smith_projection.json"), {
        "parent": "minecraft:builtin/entity",
        "gui_light": "front",
        "textures": {"particle": "isekai_mahou:item/smith_forged_blade"},
    })
    print("class_smith_techniques: modelo da Projeção gerado")


if __name__ == "__main__":
    main()
