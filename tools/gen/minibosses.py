"""
Assets dos mini-chefes v0.7 (Rei Slime, Rei Goblin, Lorde Orc, Minotauro): animações novas e a corneta do Rei Goblin.

  - animations/entity/king_slime.animation.json   (arquivo PRÓPRIO do Rei Slime: idle/air + leap/slam/split + intro/rain/death)
  - animations/entity/goblin_king.animation.json  (arquivo PRÓPRIO do Rei Goblin: tudo o que ele usa)
  - geo/entity/goblin_king.geo.json               (+ osso "horn": corneta dourada na mão esquerda, escondida fora do toque)
  - animations/entity/orc_lord.animation.json     (MESCLA: slam refeito, throw, stomp, leap, land, death)
  - animations/entity/minotaur.animation.json     (MESCLA: spin, throw, stomp novo, leap, slam, death)

Os arquivos do Lorde Orc / Minotauro / geo do Rei Goblin são gerados por creatures_a.py / creatures_b.py:
se algum deles for rodado de novo, rode ESTE script depois (ele é idempotente).

Rodar a partir da raiz do projeto:  py -I tools/gen/minibosses.py
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
ANIM = os.path.join(A, "animations/entity")


def read_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def fmt(t):
    s = ("%.4f" % t).rstrip("0").rstrip(".")
    return s if "." in s else s + ".0"


def kf(*pairs):
    """Keyframes: kf((0, [x, y, z]), (0.5, [...]))."""
    return {fmt(t): [float(v) if not isinstance(v, str) else v for v in val] for t, val in pairs}


def once(length, bones):
    return {"loop": False, "animation_length": length, "bones": bones}


def hold(length, bones):
    # "hold_on_last_frame": o controlador do Java usa thenPlayAndHold; aqui só documenta a intenção.
    return {"loop": "hold_on_last_frame", "animation_length": length, "bones": bones}


def loop(length, bones):
    return {"loop": True, "animation_length": length, "bones": bones}


HIDDEN = {"scale": [0, 0, 0]}


# =================================================================================================
#  REI SLIME
# =================================================================================================
def king_slime():
    src = read_json(os.path.join(ANIM, "mahou_slime.animation.json"))["animations"]
    king_bones = {"body", "core", "right_eye", "left_eye", "mouth", "brows", "crown"}

    def only_king(a):
        a = json.loads(json.dumps(a))
        a["bones"] = {k: v for k, v in a["bones"].items() if k in king_bones}
        return a

    anims = {
        # O controlador "body" do MahouSlime usa estes nomes.
        "animation.mahou_slime.idle": only_king(src["animation.mahou_slime.idle"]),
        "animation.mahou_slime.air": only_king(src["animation.mahou_slime.air"]),
        "animation.king_slime.leap": once(0.6, {
            "body": {"scale": kf((0, [1, 1, 1]), (0.15, [1.3, 0.65, 1.3]), (0.35, [0.82, 1.3, 0.82]), (0.6, [0.92, 1.1, 0.92]))},
            "crown": {"position": kf((0, [0, 0, 0]), (0.35, [0, 1.4, 0]), (0.6, [0, 0.6, 0]))},
            "right_eye": {"scale": kf((0, [1, 1, 1]), (0.15, [1, 0.3, 1]), (0.35, [1.25, 1.3, 1]), (0.6, [1.15, 1.2, 1]))},
            "left_eye": {"scale": kf((0, [1, 1, 1]), (0.15, [1, 0.3, 1]), (0.35, [1.25, 1.3, 1]), (0.6, [1.15, 1.2, 1]))},
        }),
        "animation.king_slime.slam": once(0.7, {
            "body": {"scale": kf((0, [1.45, 0.5, 1.45]), (0.2, [0.88, 1.18, 0.88]), (0.4, [1.08, 0.94, 1.08]), (0.7, [1, 1, 1]))},
            "crown": {"position": kf((0, [0, -1.5, 0]), (0.2, [0, 1.8, 0]), (0.45, [0, 0, 0])),
                      "rotation": kf((0.2, [0, 0, 9]), (0.45, [0, 0, -4]), (0.7, [0, 0, 0]))},
            "right_eye": {"scale": kf((0, [1, 0.3, 1]), (0.3, [1, 1, 1]))},
            "left_eye": {"scale": kf((0, [1, 0.3, 1]), (0.3, [1, 1, 1]))},
        }),
        "animation.king_slime.split": once(0.8, {
            "body": {"scale": kf((0, [1, 1, 1]), (0.2, [1.2, 0.85, 0.9]), (0.4, [0.9, 1.1, 1.2]), (0.6, [1.1, 0.95, 0.95]), (0.8, [1, 1, 1]))},
            "crown": {"rotation": kf((0, [0, 0, 0]), (0.2, [0, 0, -10]), (0.4, [0, 0, 10]), (0.8, [0, 0, 0]))},
        }),
        # Apresentação: quica duas vezes, a coroa salta girando e ele "grita".
        "animation.king_slime.intro": once(2.0, {
            "body": {"scale": kf((0, [1, 1, 1]), (0.25, [1.25, 0.7, 1.25]), (0.5, [0.85, 1.3, 0.85]), (0.75, [1.15, 0.85, 1.15]),
                                 (1.0, [1, 1, 1]), (1.2, [1.32, 0.62, 1.32]), (1.45, [0.8, 1.42, 0.8]), (1.75, [1.08, 0.94, 1.08]),
                                 (2.0, [1, 1, 1]))},
            "crown": {"position": kf((0, [0, 0, 0]), (0.5, [0, 2.5, 0]), (0.75, [0, 0, 0]), (1.2, [0, -1, 0]), (1.45, [0, 4, 0]),
                                     (1.75, [0, 0, 0])),
                      "rotation": kf((0, [0, 0, 0]), (0.5, [0, 0, 8]), (0.75, [0, 0, 0]), (1.2, [0, 0, 0]), (1.45, [0, 180, -6]),
                                     (1.75, [0, 360, 0]), (2.0, [0, 360, 0]))},
            "right_eye": {"scale": kf((0, [1, 1, 1]), (0.2, [1, 0.2, 1]), (0.45, [1.3, 1.3, 1]), (1.45, [1.45, 1.45, 1]), (2.0, [1, 1, 1]))},
            "left_eye": {"scale": kf((0, [1, 1, 1]), (0.2, [1, 0.2, 1]), (0.45, [1.3, 1.3, 1]), (1.45, [1.45, 1.45, 1]), (2.0, [1, 1, 1]))},
            "brows": {"position": kf((0, [0, 0, 0]), (0.2, [0, -0.6, 0]), (1.2, [0, -0.6, 0]), (1.45, [0, 0.7, 0]), (2.0, [0, 0, 0]))},
            "mouth": {"scale": kf((0, [1, 1, 1]), (1.2, [1, 1, 1]), (1.45, [1.35, 2.4, 1]), (1.8, [1.3, 2.2, 1]), (2.0, [1, 1, 1]))},
            "core": {"scale": kf((0, [1, 1, 1]), (1.45, [1.45, 1.45, 1.45]), (2.0, [1, 1, 1])),
                     "rotation": kf((0, [0, 0, 0]), (2.0, [0, 540, 0]))},
        }),
        # Chuva de Gosma: incha, comprime e cospe gel para o céu (o cuspe é no tick 14 = 0,7 s).
        "animation.king_slime.rain": once(1.4, {
            "body": {"scale": kf((0, [1, 1, 1]), (0.5, [1.3, 1.2, 1.3]), (0.62, [1.38, 0.72, 1.38]), (0.75, [0.74, 1.52, 0.74]),
                                 (1.0, [1.1, 0.92, 1.1]), (1.4, [1, 1, 1]))},
            "mouth": {"scale": kf((0, [1, 1, 1]), (0.5, [1.2, 1.2, 1]), (0.75, [1.6, 2.7, 1]), (1.1, [1, 1, 1]))},
            "crown": {"position": kf((0, [0, 0, 0]), (0.62, [0, -1, 0]), (0.75, [0, 2.6, 0]), (1.1, [0, 0, 0]))},
            "right_eye": {"scale": kf((0, [1, 1, 1]), (0.5, [1, 0.4, 1]), (0.75, [1.3, 1.3, 1]), (1.1, [1, 1, 1]))},
            "left_eye": {"scale": kf((0, [1, 1, 1]), (0.5, [1, 0.4, 1]), (0.75, [1.3, 1.3, 1]), (1.1, [1, 1, 1]))},
            "core": {"position": kf((0, [0, 0, 0]), (0.5, [0, 1, 0]), (0.75, [0, 3, 0]), (1.0, [0, 0, 0])),
                     "scale": kf((0, [1, 1, 1]), (0.5, [1.3, 1.3, 1.3]), (0.75, [0.6, 0.6, 0.6]), (1.2, [1, 1, 1]))},
        }),
        # Morte: derrete numa poça; a coroa (escala inversa para não achatar) escorrega e tomba de lado.
        "animation.king_slime.death": hold(2.0, {
            "body": {"scale": kf((0, [1, 1, 1]), (0.15, [1.15, 0.85, 1.15]), (0.35, [0.9, 1.12, 0.9]), (0.8, [1.3, 0.62, 1.3]),
                                 (1.4, [1.45, 0.42, 1.45]), (2.0, [1.5, 0.36, 1.5]))},
            "crown": {"scale": kf((0, [1, 1, 1]), (0.35, [1.1, 0.9, 1.1]), (0.8, [0.77, 1.6, 0.77]), (1.4, [0.69, 2.38, 0.69]),
                                  (2.0, [0.667, 2.78, 0.667])),
                      "position": kf((0, [0, 0, 0]), (0.35, [0, 1.2, 0]), (0.8, [0.6, 0, 0]), (1.4, [1.6, -0.6, 0.4]), (2.0, [1.8, -0.8, 0.4])),
                      "rotation": kf((0, [0, 0, 0]), (0.35, [0, 0, -8]), (0.8, [0, 10, -18]), (1.4, [4, 18, -32]), (2.0, [4, 18, -34]))},
            "right_eye": {"scale": kf((0, [1, 1, 1]), (0.2, [1.2, 1.2, 1]), (0.5, [1, 0.12, 1]), (2.0, [1, 0.1, 1]))},
            "left_eye": {"scale": kf((0, [1, 1, 1]), (0.2, [1.2, 1.2, 1]), (0.5, [1, 0.12, 1]), (2.0, [1, 0.1, 1]))},
            "brows": {"position": kf((0, [0, 0, 0]), (0.5, [0, -0.4, 0])), "rotation": kf((0, [0, 0, 0]), (0.5, [0, 0, 6]))},
            "mouth": {"scale": kf((0, [1, 1, 1]), (0.2, [1.3, 2, 1]), (0.6, [1.4, 0.4, 1]), (2.0, [1.4, 0.35, 1]))},
            "core": {"scale": kf((0, [1, 1, 1]), (0.4, [1.3, 1.3, 1.3]), (1.4, [0.4, 0.4, 0.4]), (2.0, [0.3, 0.3, 0.3])),
                     "position": kf((0, [0, 0, 0]), (1.4, [0, -1.5, 0]))},
        }),
    }
    write_json(os.path.join(ANIM, "king_slime.animation.json"), {"format_version": "1.8.0", "animations": anims})


# =================================================================================================
#  REI GOBLIN
# =================================================================================================
def goblin_king_horn():
    """Osso "horn" (corneta dourada) na mão esquerda do Rei Goblin. Usa UVs douradas que já existem na textura."""
    path = os.path.join(A, "geo/entity/goblin_king.geo.json")
    geo = read_json(path)
    g = geo["minecraft:geometry"][0]
    g["bones"] = [b for b in g["bones"] if b["name"] != "horn"]

    def faces(uv, size):
        return {f: {"uv": list(uv), "uv_size": list(size)} for f in ("north", "east", "south", "west", "up", "down")}

    gold_d = faces((29, 34), (2, 2))      # pomo do machado (ouro escuro)
    gold = faces((46, 14), (6, 2))        # aro da coroa (ouro claro)
    g["bones"].append({
        "name": "horn", "parent": "left_arm", "pivot": [6.0, 4.5, -0.5],
        "cubes": [
            {"origin": [5.4, 4.0, -1.1], "size": [1.2, 1.2, 1.2], "uv": gold},          # bocal
            {"origin": [5.25, 1.5, -1.25], "size": [1.5, 2.5, 1.5], "uv": gold_d},      # tubo
            {"origin": [5.0, -0.8, -1.5], "size": [2.0, 2.3, 2.0], "uv": gold_d},       # corpo
            {"origin": [4.9, -0.2, -1.6], "size": [2.2, 0.5, 2.2], "uv": gold},         # anel
            {"origin": [4.5, -2.3, -2.0], "size": [3.0, 1.5, 3.0], "uv": gold},         # campana
        ],
    })
    write_json(path, geo)


def goblin_king():
    def s(period):
        return "math.sin(query.anim_time * %s)" % fmt(360.0 / period)

    i = s(3.0)
    w = s(1.1)
    r = s(0.7)
    anims = {
        "animation.goblin_king.idle": loop(3.0, {
            "body": {"rotation": ["%s * 1.5" % i, 0, 0]},
            "right_arm": {"rotation": ["-8 + %s * 2" % i, 0, "5 + %s * 1.5" % i]},
            "left_arm": {"rotation": ["-%s * 3" % i, 0, "-6 - %s * 2" % i]},
            "right_ear": {"rotation": kf((0, [0, 0, 0]), (1.8, [0, 0, 0]), (1.9, [0, 0, 12]), (2.05, [0, 0, 0]))},
            "left_ear": {"rotation": kf((0, [0, 0, 0]), (0.7, [0, 0, 0]), (0.8, [0, 0, -12]), (0.95, [0, 0, 0]))},
            "cape": {"rotation": ["5 + %s * 2" % i, 0, 0]},
            "crown": {"rotation": [0, 0, "%s * 1.2" % i]},
            "horn": HIDDEN,
        }),
        "animation.goblin_king.walk": loop(1.1, {
            "right_leg": {"rotation": ["%s * 28" % w, 0, 0]},
            "left_leg": {"rotation": ["-%s * 28" % w, 0, 0]},
            "right_arm": {"rotation": ["-12 - %s * 10" % w, 0, 5]},
            "left_arm": {"rotation": ["%s * 24" % w, 0, -5]},
            "body": {"position": [0, "math.abs(%s) * 0.7" % w, 0], "rotation": [4, "%s * 5" % w, 0]},
            "right_ear": {"rotation": [0, 0, "%s * 6" % w]},
            "left_ear": {"rotation": [0, 0, "-%s * 6" % w]},
            "cape": {"rotation": ["10 + %s * 5" % w, 0, 0]},
            "horn": HIDDEN,
        }),
        "animation.goblin_king.run": loop(0.7, {
            "right_leg": {"rotation": ["%s * 44" % r, 0, 0]},
            "left_leg": {"rotation": ["-%s * 44" % r, 0, 0]},
            "right_arm": {"rotation": ["-30 - %s * 16" % r, 0, 8]},
            "left_arm": {"rotation": ["-20 + %s * 38" % r, 0, -8]},
            "body": {"position": [0, "math.abs(%s) * 1.0" % r, 0], "rotation": [14, 0, 0]},
            "right_ear": {"rotation": [-20, 0, "-10 + %s * 8" % r]},
            "left_ear": {"rotation": [-20, 0, "10 - %s * 8" % r]},
            "cape": {"rotation": ["32 + %s * 8" % r, 0, 0]},
            "horn": HIDDEN,
        }),
        # Machadada larga: impacto em 0,5 s (= 10 ticks de aviso do cone no chão).
        "animation.goblin_king.cleave": once(0.8, {
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.3, [-100, 70, 0]), (0.42, [-102, 76, 0]), (0.5, [-90, -55, 0]),
                                         (0.6, [-60, -45, 0]), (0.8, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.3, [0, 32, 0]), (0.42, [-3, 36, 0]), (0.5, [8, -30, 0]), (0.8, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.3, [-25, 0, -30]), (0.5, [10, 0, -15]), (0.8, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.42, [-10, 0, 0]), (0.8, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.42, [10, 0, 0]), (0.8, [0, 0, 0]))},
        }),
        # Salto: agacha e decola com o machado acima da cabeça; segura a pose no ar até o "slam".
        "animation.goblin_king.leap": hold(0.8, {
            "body": {"rotation": kf((0, [0, 0, 0]), (0.15, [24, 0, 0]), (0.3, [-12, 0, 0]), (0.8, [-6, 0, 0])),
                     "position": kf((0, [0, 0, 0]), (0.15, [0, -1.8, 0]), (0.3, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.15, [-35, 0, 0]), (0.3, [25, 0, 0]), (0.8, [10, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.15, [-35, 0, 0]), (0.3, [25, 0, 0]), (0.8, [-5, 0, 0]))},
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.15, [35, 0, 10]), (0.35, [-170, 0, 6]), (0.8, [-170, 0, 6]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.15, [35, 0, -10]), (0.35, [-160, 0, -6]), (0.8, [-160, 0, -6]))},
            "cape": {"rotation": kf((0, [5, 0, 0]), (0.35, [40, 0, 0]), (0.8, [35, 0, 0]))},
        }),
        "animation.goblin_king.slam": once(0.7, {
            "right_arm": {"rotation": kf((0, [-170, 0, 6]), (0.12, [-25, 0, 0]), (0.7, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [-160, 0, -6]), (0.12, [-20, 0, 0]), (0.7, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.12, [28, 0, 0]), (0.7, [0, 0, 0])),
                     "position": kf((0, [0, 0, 0]), (0.12, [0, -1.4, 0]), (0.7, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.12, [-15, 0, 0]), (0.7, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.12, [-15, 0, 0]), (0.7, [0, 0, 0]))},
            "cape": {"rotation": kf((0, [35, 0, 0]), (0.12, [-5, 0, 0]), (0.7, [5, 0, 0]))},
        }),
        # Corneta "Às armas!": leva a corneta à boca (o som sai no tick 8 = 0,4 s) e sopra até ~1,3 s.
        "animation.goblin_king.horn": once(1.6, {
            "horn": {"scale": kf((0, [0, 0, 0]), (0.1, [0, 0, 0]), (0.16, [1, 1, 1]), (1.4, [1, 1, 1]), (1.5, [0, 0, 0]), (1.6, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.3, [-118, -10, 38]), (0.8, [-121, -10, 38]), (1.3, [-122, -10, 38]),
                                        (1.6, [0, 0, 0]))},
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.35, [-150, 0, 12]), (1.3, [-155, 0, 12]), (1.6, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.35, [-18, 0, 0]), (1.3, [-20, 0, 0]), (1.6, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.35, [-12, 0, 0]), (0.5, [-14, 0, 1.5]), (0.65, [-13, 0, -1.5]), (0.8, [-14, 0, 1.5]),
                                    (0.95, [-13, 0, -1.5]), (1.1, [-14, 0, 1.5]), (1.3, [-12, 0, 0]), (1.6, [0, 0, 0]))},
            "right_ear": {"rotation": kf((0, [0, 0, 0]), (0.35, [-20, 0, 10]), (1.3, [-20, 0, 10]), (1.6, [0, 0, 0]))},
            "left_ear": {"rotation": kf((0, [0, 0, 0]), (0.35, [-20, 0, -10]), (1.3, [-20, 0, -10]), (1.6, [0, 0, 0]))},
            "cape": {"rotation": kf((0, [5, 0, 0]), (0.4, [20, 0, 0]), (1.3, [24, 0, 0]), (1.6, [5, 0, 0]))},
        }),
        # Golpe Giratório: carrega o giro 0,7 s (14 ticks de aviso do anel) e dá uma volta completa com o machado.
        "animation.goblin_king.spin": once(1.0, {
            "body": {"rotation": kf((0, [0, 0, 0]), (0.5, [0, 55, 0]), (0.62, [-4, 60, 0]), (0.7, [4, -100, 0]), (0.82, [4, -260, 0]),
                                    (0.92, [0, -360, 0]), (1.0, [0, -360, 0])),
                     "position": kf((0, [0, 0, 0]), (0.5, [0, -1, 0]), (0.92, [0, -1, 0]), (1.0, [0, 0, 0]))},
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.5, [-85, 0, 80]), (0.92, [-85, 0, 80]), (1.0, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.5, [-40, 0, -70]), (0.92, [-40, 0, -70]), (1.0, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.5, [0, 0, 12]), (0.92, [0, 0, 12]), (1.0, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.5, [0, 0, -12]), (0.92, [0, 0, -12]), (1.0, [0, 0, 0]))},
            "cape": {"rotation": kf((0, [5, 0, 0]), (0.7, [40, 0, 0]), (0.92, [30, 0, 0]), (1.0, [5, 0, 0]))},
        }),
        # Apresentação (também usada na Fúria): crava o machado no chão e ruge de braços abertos.
        "animation.goblin_king.intro": once(2.5, {
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.5, [-165, 0, 8]), (0.7, [-25, 0, 0]), (1.2, [-25, 0, 0]), (1.5, [-35, 0, 55]),
                                         (2.2, [-38, 0, 60]), (2.5, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.5, [-150, 0, -8]), (0.7, [-20, 0, 0]), (1.2, [-20, 0, 0]), (1.5, [-35, 0, -55]),
                                        (2.2, [-38, 0, -60]), (2.5, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.5, [-10, 0, 0]), (0.7, [26, 0, 0]), (1.2, [22, 0, 0]), (1.5, [-15, 0, 0]),
                                    (1.7, [-16, 0, 1]), (1.9, [-16, 0, -1]), (2.2, [-16, 0, 0]), (2.5, [0, 0, 0])),
                     "position": kf((0, [0, 0, 0]), (0.7, [0, -1.2, 0]), (1.2, [0, -1, 0]), (1.5, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (1.2, [0, 0, 0]), (1.5, [-22, 0, 0]), (2.2, [-22, 0, 0]), (2.5, [0, 0, 0]))},
            "right_ear": {"rotation": kf((0, [0, 0, 0]), (1.5, [-25, 0, 15]), (2.2, [-25, 0, 15]), (2.5, [0, 0, 0]))},
            "left_ear": {"rotation": kf((0, [0, 0, 0]), (1.5, [-25, 0, -15]), (2.2, [-25, 0, -15]), (2.5, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.7, [-12, 0, 0]), (1.5, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.7, [10, 0, 0]), (1.5, [0, 0, 0]))},
            "cape": {"rotation": kf((0, [5, 0, 0]), (0.7, [-5, 0, 0]), (1.5, [28, 0, 0]), (2.2, [25, 0, 0]), (2.5, [5, 0, 0]))},
            "crown": {"position": kf((0, [0, 0, 0]), (0.7, [0, 0.8, 0]), (0.85, [0, 0, 0]))},
        }),
        # Morte: cambaleia, desaba de cara no chão; a coroa rola para longe.
        "animation.goblin_king.death": hold(2.5, {
            "root": {"rotation": kf((0, [0, 0, 0]), (1.0, [0, 0, 0]), (1.25, [40, 0, 0]), (1.55, [86, 0, 4]), (1.7, [82, 0, 4]), (1.8, [86, 0, 4])),
                     "position": kf((0, [0, 0, 0]), (1.0, [0, -1, 0]), (1.55, [0, 3, 0]), (2.5, [0, 3, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.3, [-14, 0, 0]), (0.7, [16, 0, 6]), (1.0, [24, 0, 0]), (1.55, [10, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.3, [-20, 0, 0]), (0.7, [18, 0, 0]), (1.55, [-10, 0, 15]))},
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.3, [-40, 0, 30]), (0.7, [10, 0, 15]), (1.0, [15, 0, 12]), (1.55, [-150, 0, 25]),
                                         (1.8, [-155, 0, 25]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.3, [-40, 0, -30]), (0.7, [10, 0, -10]), (1.55, [-140, 0, -25]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.7, [-10, 0, 0]), (1.55, [5, 0, 5]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.7, [8, 0, 0]), (1.55, [5, 0, -5]))},
            "crown": {"position": kf((0, [0, 0, 0]), (1.4, [0, 0, 0]), (1.7, [1.5, 4, -1]), (2.0, [2.5, 6, -2.5]), (2.2, [2.8, 6.4, -3])),
                      "rotation": kf((0, [0, 0, 0]), (1.4, [0, 0, 0]), (1.7, [0, 0, -50]), (2.0, [10, 0, -95]), (2.2, [10, 0, -90]))},
            "right_ear": {"rotation": kf((0, [0, 0, 0]), (1.55, [0, 0, -20]))},
            "left_ear": {"rotation": kf((0, [0, 0, 0]), (1.55, [0, 0, 20]))},
            "cape": {"rotation": kf((0, [5, 0, 0]), (1.55, [-10, 0, 0]))},
            "horn": HIDDEN,
        }),
    }
    write_json(os.path.join(ANIM, "goblin_king.animation.json"), {"format_version": "1.8.0", "animations": anims})


# =================================================================================================
#  LORDE ORC (mescla no arquivo do creatures_b)
# =================================================================================================
def orc_lord():
    path = os.path.join(ANIM, "orc_lord.animation.json")
    data = read_json(path)
    p = "animation.orc_lord."
    data["animations"].update({
        # Machado Arremessado: arma sai no tick 12 (0,6 s); a mão busca outro machado nas costas.
        p + "throw": once(1.1, {
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.4, [-185, 0, -12]), (0.5, [-190, 0, -12]), (0.6, [-60, 0, 6]),
                                         (0.75, [-25, 0, 4]), (0.88, [10, 0, -25]), (1.0, [-10, 0, -10]), (1.1, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.4, [-14, 22, 0]), (0.5, [-16, 24, 0]), (0.6, [18, -16, 0]), (0.75, [12, -10, 0]),
                                    (1.1, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.4, [-70, 0, -15]), (0.6, [5, 0, -20]), (1.1, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.4, [-8, -18, 0]), (0.6, [6, 12, 0]), (1.1, [0, 0, 0]))},
            "weapon": {"scale": kf((0, [1, 1, 1]), (0.6, [1, 1, 1]), (0.62, [0, 0, 0]), (0.9, [0, 0, 0]), (1.0, [1, 1, 1]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.4, [12, 0, 0]), (0.6, [-14, 0, 0]), (1.1, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.4, [-14, 0, 0]), (0.6, [10, 0, 0]), (1.1, [0, 0, 0]))},
            "cape": {"rotation": kf((0, [0, 0, 0]), (0.5, [-6, 0, 0]), (0.6, [20, 0, 0]), (1.1, [0, 0, 0]))},
        }),
        # Pisão de Guerra: três pisões (0,5 / 1,0 / 1,5 s = ticks 10, 20, 30), cada um solta um anel.
        p + "stomp": once(1.8, {
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.35, [-55, 0, 0]), (0.5, [6, 0, 0]), (0.6, [0, 0, 0]), (1.2, [0, 0, 0]),
                                         (1.35, [-60, 0, 0]), (1.5, [6, 0, 0]), (1.6, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.6, [0, 0, 0]), (0.85, [-55, 0, 0]), (1.0, [6, 0, 0]), (1.1, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.35, [-8, 0, -5]), (0.5, [12, 0, 4]), (0.6, [6, 0, 0]), (0.85, [-8, 0, 5]),
                                    (1.0, [12, 0, -4]), (1.1, [6, 0, 0]), (1.35, [-10, 0, -5]), (1.5, [16, 0, 4]), (1.8, [0, 0, 0])),
                     "position": kf((0, [0, 0, 0]), (0.5, [0, -1.2, 0]), (0.62, [0, 0, 0]), (1.0, [0, -1.2, 0]), (1.12, [0, 0, 0]),
                                    (1.5, [0, -1.6, 0]), (1.8, [0, 0, 0]))},
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.2, [-35, 0, 45]), (0.5, [-20, 0, 40]), (0.7, [-35, 0, 45]), (1.0, [-20, 0, 40]),
                                         (1.2, [-35, 0, 45]), (1.5, [-20, 0, 40]), (1.8, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.2, [-35, 0, -45]), (0.5, [-20, 0, -40]), (0.7, [-35, 0, -45]), (1.0, [-20, 0, -40]),
                                        (1.2, [-35, 0, -45]), (1.5, [-20, 0, -40]), (1.8, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.2, [-12, 0, 0]), (1.6, [-12, 0, 0]), (1.8, [0, 0, 0]))},
            "cape": {"rotation": kf((0, [0, 0, 0]), (0.5, [10, 0, 0]), (1.0, [12, 0, 0]), (1.5, [14, 0, 0]), (1.8, [0, 0, 0]))},
        }),
        # Golpe no Chão (refeito): ergue o cutelo com as duas mãos até 0,8 s e crava no chão em 1,0 s
        # (= 20 ticks de aviso do círculo no SlamGoal); fica abaixado até 1,25 s e volta.
        p + "slam": once(1.5, {
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.55, [-160, 0, 6]), (0.8, [-172, 0, 4]), (1.0, [-40, 0, 0]),
                                         (1.25, [-40, 0, 0]), (1.5, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.55, [-160, 0, -6]), (0.8, [-172, 0, -4]), (1.0, [-40, 0, 0]),
                                        (1.25, [-40, 0, 0]), (1.5, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.55, [-10, 0, 0]), (0.8, [-14, 0, 0]), (1.0, [24, 0, 0]), (1.25, [20, 0, 0]),
                                    (1.5, [0, 0, 0])),
                     "position": kf((0, [0, 0, 0]), (0.8, [0, 0.4, 0]), (1.0, [0, -1.4, 0]), (1.25, [0, -1.2, 0]), (1.5, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.8, [-14, 0, 0]), (1.0, [12, 0, 0]), (1.5, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.8, [-6, 0, 0]), (1.0, [-16, 0, 0]), (1.25, [-14, 0, 0]), (1.5, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.8, [6, 0, 0]), (1.0, [12, 0, 0]), (1.25, [10, 0, 0]), (1.5, [0, 0, 0]))},
            "cape": {"rotation": kf((0, [0, 0, 0]), (0.8, [-8, 0, 0]), (1.0, [22, 0, 0]), (1.5, [0, 0, 0]))},
        }),
        # Salto anti-pilar: agacha e decola; segura no ar até o "land" do pouso.
        p + "leap": hold(0.9, {
            "body": {"rotation": kf((0, [0, 0, 0]), (0.2, [22, 0, 0]), (0.4, [-10, 0, 0]), (0.9, [-4, 0, 0])),
                     "position": kf((0, [0, 0, 0]), (0.2, [0, -2, 0]), (0.4, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.2, [-30, 0, 0]), (0.4, [20, 0, 0]), (0.9, [15, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.2, [-30, 0, 0]), (0.4, [-10, 0, 0]), (0.9, [-5, 0, 0]))},
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.2, [30, 0, 15]), (0.45, [-175, 0, 8]), (0.9, [-175, 0, 8]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.2, [30, 0, -15]), (0.45, [-165, 0, -8]), (0.9, [-165, 0, -8]))},
            "cape": {"rotation": kf((0, [0, 0, 0]), (0.45, [35, 0, 0]), (0.9, [30, 0, 0]))},
        }),
        # Pouso do salto: o cutelo (erguido no "leap") desce de uma vez no chão.
        p + "land": once(0.7, {
            "right_arm": {"rotation": kf((0, [-175, 0, 8]), (0.12, [-35, 0, 0]), (0.45, [-30, 0, 0]), (0.7, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [-165, 0, -8]), (0.12, [-30, 0, 0]), (0.45, [-25, 0, 0]), (0.7, [0, 0, 0]))},
            "body": {"rotation": kf((0, [-4, 0, 0]), (0.12, [26, 0, 0]), (0.45, [18, 0, 0]), (0.7, [0, 0, 0])),
                     "position": kf((0, [0, 0, 0]), (0.12, [0, -1.6, 0]), (0.45, [0, -1.2, 0]), (0.7, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [15, 0, 0]), (0.12, [-16, 0, 0]), (0.7, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [-5, 0, 0]), (0.12, [-16, 0, 0]), (0.7, [0, 0, 0]))},
            "cape": {"rotation": kf((0, [30, 0, 0]), (0.12, [-6, 0, 0]), (0.7, [0, 0, 0]))},
        }),
        # Morte: cambaleia para trás, desaba e cai de cara.
        p + "death": hold(3.0, {
            "root": {"rotation": kf((0, [0, 0, 0]), (1.1, [0, 0, 0]), (1.4, [35, 0, 0]), (1.75, [86, 0, -3]), (1.9, [82, 0, -3]), (2.0, [86, 0, -3])),
                     "position": kf((0, [0, 0, 0]), (0.6, [0, -1.5, 0]), (1.1, [0, -2.5, 0]), (1.75, [0, 4.2, 0]), (3.0, [0, 4.2, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.3, [-14, 0, 0]), (0.6, [20, 0, 5]), (1.1, [28, 0, 0]), (1.75, [8, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.3, [-25, 0, 0]), (0.6, [15, 0, 0]), (1.75, [-5, 0, 20]))},
            "right_arm": {"rotation": kf((0, [0, 0, 0]), (0.3, [-50, 0, 35]), (0.6, [5, 0, 20]), (1.1, [10, 0, 15]), (1.75, [-160, 0, 30]),
                                         (2.0, [-165, 0, 30]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.3, [-50, 0, -35]), (0.6, [5, 0, -20]), (1.1, [10, 0, -15]), (1.75, [-150, 0, -30]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.6, [-18, 0, 0]), (1.1, [-25, 0, 0]), (1.75, [0, 0, 6]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.6, [-8, 0, 0]), (1.1, [-12, 0, 0]), (1.75, [0, 0, -6]))},
            "cape": {"rotation": kf((0, [0, 0, 0]), (0.6, [10, 0, 0]), (1.75, [-12, 0, 0]))},
        }),
    })
    write_json(path, data)


# =================================================================================================
#  MINOTAURO (mescla no arquivo do creatures_b)
# =================================================================================================
def minotaur():
    path = os.path.join(ANIM, "minotaur.animation.json")
    data = read_json(path)
    p = "animation.minotaur."
    data["animations"].update({
        # Labrys Giratória: carrega 0,8 s (16 ticks) e gira 3 voltas; cada volta = um golpe (ticks 16, 24, 32, 40).
        p + "spin": once(2.4, {
            "body": {"rotation": kf((0, [0, 0, 0]), (0.6, [6, 60, 0]), (0.8, [8, 65, 0]), (1.2, [6, -295, 0]), (1.6, [6, -655, 0]),
                                    (2.0, [6, -1015, 0]), (2.2, [2, -1080, 0]), (2.4, [0, -1080, 0])),
                     "position": kf((0, [0, 0, 0]), (0.6, [0, -1.5, 0]), (2.2, [0, -1.5, 0]), (2.4, [0, 0, 0]))},
            "right_arm": {"rotation": kf((0, [-20, 0, 0]), (0.6, [-88, 0, 72]), (2.2, [-88, 0, 72]), (2.4, [-20, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.6, [-45, 0, -65]), (2.2, [-45, 0, -65]), (2.4, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.6, [10, 0, 0]), (2.2, [10, 0, 0]), (2.4, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.6, [0, 0, 14]), (2.2, [0, 0, 14]), (2.4, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.6, [0, 0, -14]), (2.2, [0, 0, -14]), (2.4, [0, 0, 0]))},
        }),
        # Lábris Arremessada: a arma sai no tick 10 (0,5 s) e outra "volta" à mão no fim.
        p + "throw": once(1.0, {
            "right_arm": {"rotation": kf((0, [-20, 0, 0]), (0.35, [-185, 0, -10]), (0.42, [-190, 0, -10]), (0.5, [-55, 0, 6]),
                                         (0.65, [-25, 0, 4]), (1.0, [-20, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.35, [-14, 25, 0]), (0.5, [16, -20, 0]), (0.65, [10, -12, 0]), (1.0, [0, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.35, [-75, 0, -20]), (0.5, [5, 0, -15]), (1.0, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.35, [-6, -20, 0]), (0.5, [8, 14, 0]), (1.0, [0, 0, 0]))},
            "axe": {"scale": kf((0, [1, 1, 1]), (0.5, [1, 1, 1]), (0.52, [0, 0, 0]), (0.85, [0, 0, 0]), (0.95, [1, 1, 1]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.35, [14, 0, 0]), (0.5, [-12, 0, 0]), (1.0, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.35, [-12, 0, 0]), (0.5, [10, 0, 0]), (1.0, [0, 0, 0]))},
        }),
        # Pisotear (fase 2): ergue a pata devagar e pisa em 0,9 s = 18 ticks de aviso do círculo (dá para sair andando).
        p + "stomp": once(1.3, {
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.55, [-55, 0, 0]), (0.75, [-66, 0, 0]), (0.9, [6, 0, 0]), (1.3, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.75, [8, 0, 0]), (0.9, [-6, 0, 0]), (1.3, [0, 0, 0]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.55, [-10, 0, 0]), (0.75, [-14, 0, 0]), (0.9, [14, 0, 0]), (1.3, [0, 0, 0])),
                     "position": kf((0, [0, 0, 0]), (0.75, [0, 0.6, 0]), (0.9, [0, -1.5, 0]), (1.05, [0, 0, 0]))},
            "right_arm": {"rotation": kf((0, [-20, 0, 0]), (0.55, [-60, 0, -20]), (0.75, [-70, 0, -22]), (0.9, [-10, 0, 0]),
                                         (1.3, [-20, 0, 0]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.55, [-60, 0, 20]), (0.75, [-70, 0, 22]), (0.9, [-10, 0, 0]), (1.3, [0, 0, 0]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.55, [-12, 0, 0]), (0.75, [-16, 0, 0]), (0.9, [10, 0, 0]), (1.3, [0, 0, 0]))},
        }),
        p + "leap": hold(1.0, {
            "body": {"rotation": kf((0, [0, 0, 0]), (0.25, [26, 0, 0]), (0.45, [-12, 0, 0]), (1.0, [-6, 0, 0])),
                     "position": kf((0, [0, 0, 0]), (0.25, [0, -2.5, 0]), (0.45, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.25, [-32, 0, 0]), (0.45, [24, 0, 0]), (1.0, [18, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.25, [-32, 0, 0]), (0.45, [-12, 0, 0]), (1.0, [-8, 0, 0]))},
            "right_arm": {"rotation": kf((0, [-20, 0, 0]), (0.25, [25, 0, 15]), (0.5, [-170, 0, 8]), (1.0, [-170, 0, 8]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.25, [25, 0, -15]), (0.5, [-160, 0, -8]), (1.0, [-160, 0, -8]))},
            "head": {"rotation": kf((0, [0, 0, 0]), (0.45, [-15, 0, 0]), (1.0, [-10, 0, 0]))},
        }),
        # Pouso do salto: desce a lábris no chão.
        p + "slam": once(0.7, {
            "right_arm": {"rotation": kf((0, [-170, 0, 8]), (0.12, [-30, 0, 0]), (0.7, [-20, 0, 0]))},
            "left_arm": {"rotation": kf((0, [-160, 0, -8]), (0.12, [-25, 0, 0]), (0.7, [0, 0, 0]))},
            "body": {"rotation": kf((0, [-8, 0, 0]), (0.12, [26, 0, 0]), (0.7, [0, 0, 0])),
                     "position": kf((0, [0, 0, 0]), (0.12, [0, -1.5, 0]), (0.7, [0, 0, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (0.12, [-14, 0, 0]), (0.7, [0, 0, 0]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (0.12, [-14, 0, 0]), (0.7, [0, 0, 0]))},
        }),
        # Morte: urra de dor com os braços para o alto e tomba de costas.
        p + "death": hold(3.0, {
            "head": {"rotation": kf((0, [0, 0, 0]), (0.4, [-35, 0, 0]), (0.9, [-35, 0, 0]), (1.2, [-10, 0, 0]), (1.8, [15, 0, 10]))},
            "body": {"rotation": kf((0, [0, 0, 0]), (0.4, [-16, 0, 0]), (0.55, [-15, 0, 2]), (0.7, [-16, 0, -2]), (0.9, [-12, 0, 0]),
                                    (1.2, [10, 0, 0]), (1.8, [-5, 0, 0]))},
            "right_arm": {"rotation": kf((0, [-20, 0, 0]), (0.4, [-140, 0, 30]), (0.9, [-140, 0, 35]), (1.2, [-20, 0, 20]), (1.8, [-10, 0, 85]),
                                         (2.0, [-10, 0, 80]))},
            "left_arm": {"rotation": kf((0, [0, 0, 0]), (0.4, [-140, 0, -30]), (0.9, [-140, 0, -35]), (1.2, [-20, 0, -20]),
                                        (1.8, [-10, 0, -85]), (2.0, [-10, 0, -80]))},
            "root": {"rotation": kf((0, [0, 0, 0]), (1.2, [0, 0, 0]), (1.45, [-30, 0, 0]), (1.8, [-88, 0, 3]), (1.95, [-84, 0, 3]), (2.05, [-88, 0, 3])),
                     "position": kf((0, [0, 0, 0]), (1.2, [0, 0, 0]), (1.8, [0, 5, 0]), (3.0, [0, 5, 0]))},
            "right_leg": {"rotation": kf((0, [0, 0, 0]), (1.2, [-10, 0, 0]), (1.8, [-15, 0, 8]))},
            "left_leg": {"rotation": kf((0, [0, 0, 0]), (1.2, [8, 0, 0]), (1.8, [-5, 0, -8]))},
        }),
    })
    write_json(path, data)


if __name__ == "__main__":
    king_slime()
    goblin_king_horn()
    goblin_king()
    orc_lord()
    minotaur()
    print("minibosses: animações e corneta gerados")
