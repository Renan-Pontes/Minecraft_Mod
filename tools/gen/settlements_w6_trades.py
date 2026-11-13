"""Onda 6 (city_trades) — arte dos COMERCIANTES DE OFÍCIO das cidades dos jogadores.

Uso (na raiz do projeto):  py -I tools/gen/settlements_w6_trades.py

Gera SÓ arquivos novos (nada dos outros geradores do módulo é reescrito):
  - textures/entity/settlement_npc/trader_<ofício>_<0|1>.png — uma roupa por ofício (0 = homem, 1 = mulher), pintada no
    mesmo modelo "settlement_npc" (a geometria vem de tools/gen/settlements.py, sem regravar o .geo.json);
  - models/item/settlement_tradesman_spawn_egg.json.
Os ofícios e as peças do modelo que cada um mostra estão em feature/settlements/CityTrades.java (enum Job).
"""
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)
from pixelart import darken  # noqa: E402
import cities as cz  # noqa: E402
import settlements as st  # noqa: E402  (geometria e "person" dos NPCs da cidade do jogador)

A = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
H = cz.H

JOBS = ["blacksmith", "healer", "scribe", "innkeeper", "stablehand", "jeweler", "lumberjack", "miner", "arcanist"]
HAIR = ["5A3A22", "3A2A22", "C97A3A", "1E1A1A", "8A6A4A", "D9C07A", "6B6B72"]
EYES = ["3A6BC9", "4A6B3A", "5A3A22", "3A8ACF"]


def look(job, v):
    """Pintores de um ofício (variante 1 = mulher, como no Java)."""
    female = v % 2 == 1
    rnd = random.Random(f"trader/{job}/{v}")
    skin = rnd.choice(st.SKIN)
    hair = rnd.choice(HAIR)
    eye = rnd.choice(EYES)
    hstyle = "long" if female else rnd.choice(["short", "short", "bald"])

    def person(shirt_p, sleeve, pants, boots, **kw):
        return st.person(skin, hair, hstyle, eye, shirt_p, sleeve, pants, boots, female=female, **kw)

    if job == "blacksmith":
        # camisa escura de mangas arregaçadas, avental de couro chamuscado, luvas e o martelo de forja
        shirt = "4A3A32"
        kit = person(cz.body(shirt, belt="2A1E16", buckle="9AA4B0"), shirt, "3A3A42", "2A1E16", stubble=not female)
        kit["arm_r"] = cz.arm(shirt, skin, sleeve_rows=3, glove="3A2618")
        kit["arm_l"] = cz.arm(shirt, skin, sleeve_rows=3, glove="3A2618")
        kit["apron"] = cz.apron("6B4A2E", pocket="3A2618", stains=True)
        kit["hammer_handle"] = cz.wood_rod("4A3220")
        kit["hammer_head"] = cz.metal("4E545C")
        return kit
    if job == "healer":
        # túnica clara com faixa verde, capuz verde de barra branca e a bolsa de ervas
        robe = "EDE8DC"
        kit = person(cz.body(robe, sash="5FB86B", collar="FFFFFF", emblem="C9433A"), robe, "CFC8B8", "8A6A4A")
        kit["arm_r"] = cz.arm(robe, skin, sleeve_rows=9, cuff="5FB86B")
        kit["arm_l"] = cz.arm(robe, skin, sleeve_rows=9, cuff="5FB86B")
        kit["hood"] = cz.hood("4E9A5A", trim="FFFFFF")
        kit["hood_drape"] = cz.solid("3E8A4A")
        kit["satchel"] = cz.satchel("6B8E3A", "3A2A1E")
        return kit
    if job == "scribe":
        # manto azul-tinta de botões dourados, gorro com faixa, capa curta e punhos manchados de tinta
        robe = "3A4A6B"
        kit = person(cz.body(robe, collar="EDE3C8", buttons="E8B84A", robe_trim="E8B84A", belt="2A2A3A"), robe, "2A2A3A", "2A1E16")
        kit["arm_r"] = cz.arm(robe, skin, sleeve_rows=9, cuff="1E1E2A")
        kit["arm_l"] = cz.arm(robe, skin, sleeve_rows=9, cuff="EDE3C8")
        kit["cap_top"] = cz.cap_top("2B3A67", band_c="E8B84A")
        kit["cap_brim"] = cz.solid("1E2A4A")
        kit["cape"] = cz.cape("2B3A67", "1E2A4A", trim="E8B84A")
        return kit
    if job == "innkeeper":
        # camisa creme, colete cor de vinho e o avental branco de quem serve o balcão
        shirt = "E8DCC4"
        kit = person(cz.body(shirt, vest="7A2E2A", buttons="E8B84A", belt="3A2A1E", collar="FFFFFF"), shirt, "4A3A2E",
                     "2A1E16")
        kit["arm_r"] = cz.arm(shirt, skin, sleeve_rows=4)
        kit["arm_l"] = cz.arm(shirt, skin, sleeve_rows=4)
        kit["apron"] = cz.apron("F2EEE4", pocket="D9CFB8")
        return kit
    if job == "stablehand":
        # roupa de couro com suspensórios, boné de aba, botas altas e a bolsa de ração
        shirt = "A8804E"
        kit = person(cz.body(shirt, straps="3A2A1E", belt="2A1E16", buckle="C9A27A"), shirt, "5A4632", "3A2618",
                     freckles=True)
        kit["arm_r"] = cz.arm(shirt, skin, sleeve_rows=6, glove="5A3E26")
        kit["arm_l"] = cz.arm(shirt, skin, sleeve_rows=6, glove="5A3E26")
        kit["leg"] = cz.leg("5A4632", "3A2618", boot_rows=6)
        kit["cap_top"] = cz.cap_top("6B4A2E", band_c="C9A27A")
        kit["cap_brim"] = cz.solid("4A3220")
        kit["satchel"] = cz.satchel("C9A27A", "3A2A1E")
        return kit
    if job == "jeweler":
        # colete roxo de botões de ouro, avental escuro de bancada e a bolsinha das gemas
        shirt = "EDE3F2"
        kit = person(cz.body(shirt, vest="5A2E7A", buttons="FFD86B", belt="2A1A3A", collar="FFFFFF", emblem="E07BD0"), shirt,
                     "2A2430", "1E1A22")
        kit["arm_r"] = cz.arm(shirt, skin, sleeve_rows=8, cuff="FFD86B")
        kit["arm_l"] = cz.arm(shirt, skin, sleeve_rows=8, cuff="FFD86B")
        kit["apron"] = cz.apron("2A2430", pocket="FFD86B")
        kit["satchel"] = cz.satchel("E8B84A", "2A1A3A")
        return kit
    if job == "lumberjack":
        # camisa xadrez vermelha, suspensórios e o machado
        shirt = "A8372B"
        kit = person(cz.body(shirt, stripes="5A1E18", straps="3A2A1E", belt="2A1E16", buckle="9AA4B0"), shirt, "3A4A5A", "3A2618",
                     stubble=not female)
        kit["arm_r"] = cz.arm(shirt, skin, sleeve_rows=5, glove="5A3E26")
        kit["arm_l"] = cz.arm(shirt, skin, sleeve_rows=5, glove="5A3E26")
        kit["leg"] = cz.leg("3A4A5A", "3A2618", boot_rows=5)
        kit["axe_handle"] = cz.wood_rod("7A5636")
        kit["axe_head"] = cz.metal("A8B2BE", rivets=False)
        return kit
    if job == "miner":
        # roupa cinza de poeira, capacete amarelo com aba e a marreta
        shirt = "6B6258"
        kit = person(cz.body(shirt, straps="2A1E16", belt="2A1E16", buckle="D9A040"), shirt, "4A443C", "2A2420",
                     stubble=not female)
        kit["arm_r"] = cz.arm(shirt, skin, sleeve_rows=6, glove="3A3028")
        kit["arm_l"] = cz.arm(shirt, skin, sleeve_rows=6, glove="3A3028")
        kit["leg"] = cz.leg("4A443C", "2A2420", boot_rows=5, patch="6B6258")
        kit["helmet"] = cz.helmet("D9A040", trim="FFE9A0")
        kit["helmet_rim"] = cz.metal(darken(H("D9A040"), 0.15), rivets=False)
        kit["hammer_handle"] = cz.wood_rod("5A3E26")
        kit["hammer_head"] = cz.metal("8E99A8")
        return kit
    # arcanist: manto violeta de barra lilás, capuz e capa com o emblema dourado
    robe = "3A2A6B"
    kit = person(cz.body(robe, robe_trim="B98CFF", emblem="FFD86B", sash="B98CFF", collar="D8C8FF"), robe, "2A1A4A", "1E1430")
    kit["arm_r"] = cz.arm(robe, skin, sleeve_rows=10, cuff="B98CFF")
    kit["arm_l"] = cz.arm(robe, skin, sleeve_rows=10, cuff="B98CFF")
    kit["hood"] = cz.hood("4A2A8A", trim="B98CFF")
    kit["hood_drape"] = cz.solid("3A2070")
    kit["cape"] = cz.cape("2A1A5A", "1A1040", trim="B98CFF", emblem="FFD86B")
    return kit


def main():
    g = st.npc_geo()
    out = os.path.join(A, "textures/entity/settlement_npc")
    n = 0
    for job in JOBS:
        for v in range(2):
            g.paint(look(job, v), 9100 + n).save(os.path.join(out, f"trader_{job}_{v}.png"))
            n += 1
    cz.write_json(os.path.join(A, "models/item/settlement_tradesman_spawn_egg.json"), {"parent": "minecraft:item/template_spawn_egg"})
    print(f"settlements_w6_trades: {n} texturas de comerciantes de ofício + modelo do ovo")


if __name__ == "__main__":
    main()
