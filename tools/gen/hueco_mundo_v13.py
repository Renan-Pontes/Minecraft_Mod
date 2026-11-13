"""v1.3 — animacoes novas dos chefes do Mundo Oco (ACRESCENTA nas .animation.json existentes; pode rodar de novo).

Rode DEPOIS de hueco_mundo_models.py (que reescreve os arquivos inteiros):
    py tools/gen/hueco_mundo_v13.py

- hollow_king.slam    (2.6 s): ergue o machado com as duas maos (carga 1.3 s), crava no chao (1.5 s), segura e volta.
- sand_empress.tsunami (2.0 s): gira a espada acima da cabeca, inclina para tras e varre para a frente (1.2 s).
- thorn_queen.waltz   (4.6 s): abre as asas, sobe um pouco e gira duas vezes; termina de bracos abertos (explosao).
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
ANIMS = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "animations", "entity")


def fmt(t):
    return ("%.2f" % t).rstrip("0").rstrip(".") if t % 1 else "%.1f" % t


def keys(pairs):
    return {fmt(t): [float(x) for x in v] for t, v in sorted(pairs, key=lambda kv: kv[0])}


def anim(length, bones):
    return {"loop": False, "animation_length": length, "bones": bones}


def add(ident, name, a):
    path = os.path.join(ANIMS, ident + ".animation.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    data["animations"]["animation.%s.%s" % (ident, name)] = a
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("ok", ident, name)


def king_slam():
    arms = [(0, [-15, 0, 5]), (0.5, [-120, 0, 10]), (1.3, [-175, 0, 12]), (1.5, [-40, 0, 6]), (1.9, [-40, 0, 6]), (2.6, [-15, 0, 5])]
    arms_l = [(t, [v[0], -v[1], -v[2]]) for t, v in arms]
    return anim(2.6, {
        "arm_r": {"rotation": keys(arms)},
        "arm_l": {"rotation": keys(arms_l)},
        "axe": {"rotation": keys([(0, [0, 0, 0]), (1.3, [-25, 0, 0]), (1.5, [35, 0, 0]), (1.9, [35, 0, 0]), (2.6, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (1.3, [-14, 0, 0]), (1.5, [24, 0, 0]), (1.9, [22, 0, 0]), (2.6, [0, 0, 0])]),
                 "position": keys([(0, [0, 0, 0]), (1.3, [0, 2.5, 0]), (1.5, [0, -2.5, 1]), (1.9, [0, -2.0, 1]), (2.6, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (1.3, [-28, 0, 0]), (1.5, [18, 0, 0]), (2.6, [0, 0, 0])])},
    })


def empress_tsunami():
    return anim(2.0, {
        "arm_r": {"rotation": keys([(0, [-10, 0, 0]), (0.3, [-170, 0, 20]), (0.6, [-170, 120, 20]), (0.9, [-170, 260, 20]),
                                    (1.2, [-30, 360, -10]), (1.6, [-30, 360, -10]), (2.0, [-10, 360, 0])])},
        "arm_l": {"rotation": keys([(0, [0, 0, 0]), (0.4, [-60, 0, -40]), (1.2, [-20, 0, -70]), (2.0, [0, 0, 0])])},
        "body": {"rotation": keys([(0, [0, 0, 0]), (0.9, [-16, 25, 0]), (1.2, [22, -20, 0]), (1.6, [18, -15, 0]), (2.0, [0, 0, 0])])},
        "tail": {"rotation": keys([(0, [0, 0, 0]), (0.9, [-20, 30, 0]), (1.2, [20, -40, 0]), (2.0, [0, 0, 0])])},
        "fin": {"rotation": keys([(0, [0, 0, 0]), (1.2, [-15, 0, 0]), (2.0, [0, 0, 0])])},
        "root": {"position": keys([(0, [0, 0, 0]), (0.9, [0, 1.5, 0]), (1.2, [0, -1, 2]), (2.0, [0, 0, 0])])},
    })


def queen_waltz():
    wing_open = 55
    wings = {}
    for w, sign in (("wing_ul", 1), ("wing_ur", -1), ("wing_ll", 1), ("wing_lr", -1)):
        wings[w] = {"rotation": keys([(0, [0, 0, 0]), (0.6, [0, sign * wing_open, 0]), (3.9, [0, sign * wing_open, 0]),
                                      (4.1, [0, sign * (wing_open + 25), 0]), (4.6, [0, 0, 0])])}
    bones = {
        "root": {"rotation": keys([(0, [0, 0, 0]), (1.0, [0, 0, 0]), (2.5, [0, 360, 0]), (4.0, [0, 720, 0]), (4.6, [0, 720, 0])]),
                 "position": keys([(0, [0, 0, 0]), (1.0, [0, 4, 0]), (3.9, [0, 4, 0]), (4.1, [0, 6, 0]), (4.6, [0, 0, 0])])},
        "arm_r": {"rotation": keys([(0, [0, 0, 0]), (1.0, [0, 0, 80]), (3.9, [0, 0, 80]), (4.1, [-60, 0, 110]), (4.6, [0, 0, 0])])},
        "arm_l": {"rotation": keys([(0, [0, 0, 0]), (1.0, [0, 0, -80]), (3.9, [0, 0, -80]), (4.1, [-60, 0, -110]), (4.6, [0, 0, 0])])},
        "head": {"rotation": keys([(0, [0, 0, 0]), (1.0, [-12, 0, 0]), (4.1, [-30, 0, 0]), (4.6, [0, 0, 0])])},
    }
    bones.update(wings)
    return anim(4.6, bones)


if __name__ == "__main__":
    add("hollow_king", "slam", king_slam())
    add("sand_empress", "tsunami", empress_tsunami())
    add("thorn_queen", "waltz", queen_waltz())
