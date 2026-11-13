"""
v1.3.2 — Armas nas maos dos chefes (correcao direta nos .geo.json; idempotente: rodar de novo nao muda nada).

O problema: as armas longas eram ossos filhos do braco, mas desenhadas EM PE ao longo do braco — o cabo atravessava o
braco inteiro e saia pelo ombro, e a cabeca do machado ficava no ombro/dentro do corpo. Quando o braco girava, a arma
girava "dentro" dele.

Correcoes:
  * Barragan (Gran Caida) e o Rei Vazio/Arrogante: o cabo passa pela MAO (pegada perto do pe do cabo) e o osso ganha
    rotacao de repouso para a arma sair PARA A FRENTE da mao (como item segurado no Minecraft); as laminas ficam
    verticais (golpe de cima para baixo).
  * Lanza del Relampago (Ulquiorra): rotacao de repouso para a lanca apontar para a frente com o braco erguido.
  * Espada grande do Campeao da Cidade: aponta para a frente (antes saia pelo ombro).
  * Cajados/lancas/tridentes em pe (Lich, Rainha dos Espinhos, Arquimago, Sentinelas, Serafim, Soberano do Abismo,
    Rei Afogado, Guardiao das Mares): o cabo vai para a FRENTE do braco, encostado no punho (nao atravessa mais o braco).

Uso:  py -I tools/gen/espada_weapons_fix.py
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
GEO = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou/geo/entity")


def load(name):
    with open(os.path.join(GEO, name + ".geo.json"), encoding="utf-8") as f:
        return json.load(f)


def save(name, d):
    with open(os.path.join(GEO, name + ".geo.json"), "w", encoding="utf-8") as f:
        json.dump(d, f, indent=2, ensure_ascii=False)
        f.write("\n")


def bones(d):
    return {b["name"]: b for b in d["minecraft:geometry"][0]["bones"]}


def r(v):
    return round(v * 1000) / 1000


def translate(bone, dx, dy, dz):
    if abs(dx) + abs(dy) + abs(dz) < 1e-6:
        return
    bone["pivot"] = [r(bone["pivot"][0] + dx), r(bone["pivot"][1] + dy), r(bone["pivot"][2] + dz)]
    for c in bone.get("cubes", []):
        c["origin"] = [r(c["origin"][0] + dx), r(c["origin"][1] + dy), r(c["origin"][2] + dz)]
        if "pivot" in c:
            c["pivot"] = [r(c["pivot"][0] + dx), r(c["pivot"][1] + dy), r(c["pivot"][2] + dz)]


def haft(bone):
    """O cubo mais comprido na vertical (o cabo)."""
    return max(bone["cubes"], key=lambda c: c["size"][1])


def ext(cubes, axis):
    lo = min(c["origin"][axis] for c in cubes)
    hi = max(c["origin"][axis] + c["size"][axis] for c in cubes)
    return lo, hi


def axe_forward(name, grip_from_bottom, hand_y, bone_rot, spin_head):
    """Cabo pela mao (pegada a {grip_from_bottom} px do pe), osso girado para a frente; laminas giradas no cabo."""
    d = load(name)
    b = bones(d)["axe"]
    h = haft(b)
    dy = hand_y - (h["origin"][1] + grip_from_bottom)
    cx = h["origin"][0] + h["size"][0] / 2
    cz = h["origin"][2] + h["size"][2] / 2
    translate(b, 0, dy, 0)
    b["pivot"] = [r(cx), r(hand_y), r(cz)]
    b["rotation"] = [bone_rot, 0, 0]
    if spin_head:
        for c in b["cubes"]:
            if c is h:
                continue
            cy = c["origin"][1] + c["size"][1] / 2
            c["rotation"] = [0, 90, 0]
            c["pivot"] = [r(cx), r(cy), r(cz)]
    save(name, d)
    print(name, "axe: dy", r(dy), "rot", bone_rot)


def rest_rotation(name, bone, rot):
    d = load(name)
    bones(d)[bone]["rotation"] = rot
    save(name, d)
    print(name, bone, "rotation", rot)


def pole_in_front(name, bone):
    """Cajado em pe: o cabo vai para a frente do braco (z menor), encostado no punho."""
    d = load(name)
    bs = bones(d)
    b = bs[bone]
    arm = bs[b["parent"]]
    if not arm.get("cubes"):
        print(name, bone, "sem cubos no braco; ignorado")
        return
    arm_z0, _ = ext(arm["cubes"], 2)
    h = haft(b)
    pole_z1 = h["origin"][2] + h["size"][2]
    dz = (arm_z0 + 0.3) - pole_z1
    if dz < -0.01:
        translate(b, 0, 0, dz)
        print(name, bone, "dz", r(dz))
    else:
        print(name, bone, "ok")
    save(name, d)


def main():
    # Gran Caida: mao em y=14,5 (pe do braco em 13); cabo de 26 px, pegada a 4 px do pe; laminas viram cutelo vertical.
    axe_forward("barragan", 4.0, 14.5, 110, True)
    # Machado do Rei (= Barragan em Arrogante): punho em y=24; cabo de 34 px, pegada a 8 px do pe; lamina ja e vertical.
    axe_forward("hollow_king", 8.0, 24.0, 110, False)
    # Lanza del Relampago: com o braco erguido (-160) a lanca aponta para a frente.
    rest_rotation("ulquiorra", "lance", [160, 0, 0])
    # Espada grande do Campeao: sai para a frente da mao.
    rest_rotation("city_champion", "greatsword", [90, 0, 0])
    for name, bone in (("lich", "staff"), ("thorn_queen", "staff"), ("renegade_archmage", "staff"), ("angel_sentinel", "spear"),
                       ("trial_sentinel", "weapon"), ("abyss_sovereign", "scepter"), ("seraph", "lance"),
                       ("patron_drowned_king", "trident"), ("tide_guardian", "trident")):
        pole_in_front(name, bone)


if __name__ == "__main__":
    main()
