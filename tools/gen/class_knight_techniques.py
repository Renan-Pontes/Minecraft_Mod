"""
Pacote de técnicas de anime do Cavaleiro Rúnico (class_knight): traduções e o ícone do efeito novo.
Uso (na raiz do projeto):  py -I tools/gen/class_knight_techniques.py

  - Junta as chaves abaixo em src/main/lang/class_knight/{pt_br,en_us}.json (as traduções do módulo são escritas à mão;
    este script só ACRESCENTA/ATUALIZA as chaves do pacote, sem mexer nas outras).
  - Gera textures/mob_effect/knight_invisible_air.png (Vento Invisível: espada apagada envolta em correntes de vento).
Confere que todo '%' é um marcador válido (%s, %N$s ou %%).
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
from pixelart import Canvas, hex_rgba  # noqa: E402

LANG = os.path.join(ROOT, "src", "main", "lang", "class_knight")
A = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
G = "glyph.isekai_mahou."
M = "message.isekai_mahou.class_knight."
O = "oath.isekai_mahou.class_knight."
E = []


def e(key, pt, en):
    E.append((key, pt, en))


# ------------------------------------------------------------------ efeito, anúncios, mensagens, lendas
e("effect.isekai_mahou.knight_invisible_air", "Vento Invisível", "Invisible Air")
e("announce.isekai_mahou.knight_strike_air", "Vento Invisível — Golpe do Ar!", "Invisible Air — Strike Air!")
e("announce.isekai_mahou.knight_anti_magic", "Anti-Magia!", "Anti-Magic!")
e("announce.isekai_mahou.knight_caliburn_strike", "Caliburn — a Espada da Seleção!", "Caliburn — the Sword of Selection!")
e("announce.isekai_mahou.knight_balmung", "BALMUNG — Queda do Crepúsculo!", "BALMUNG — Twilight's Fall!")
e("announce.isekai_mahou.knight_avalon", "AVALON — A Bainha Distante", "AVALON — The Distant Sheath")
e(M + "avalon", "Avalon: longe de toda ferida por %s s.", "Avalon: beyond every wound for %s s.")
e(M + "anti_magic", "Postura Anti-Magia: %s s — magias não pegam em você.", "Anti-Magic Stance: %s s — spells won't stick to you.")
e(M + "tech_catchup", "Técnicas novas do Cavaleiro aprendidas: %s.", "New Knight techniques learned: %s.")
e("legend.isekai_mahou.knight_avalon_utopia", "Avalon — A Utopia Distante", "Avalon — The Distant Utopia")
e("legend.isekai_mahou.knight_balmung_twilight", "Balmung — Crepúsculo do Dragão", "Balmung — Dragon's Twilight")
e("legend.isekai_mahou.knight_black_divider", "Corte Negro Anti-Magia", "Black Anti-Magic Cut")

# ------------------------------------------------------------------ recompensas dos Juramentos (aba do menu B)
e(O + "1.reward", "Aura de Espada, Investida Rúnica e Vento Invisível (Golpe do Ar) · 3 Essências da Luz · XP",
  "Blade Aura, Runic Charge and Invisible Air (Strike Air) · 3 Light Essences · XP")
e(O + "2.reward", "Égide Rúnica, Chuva de Espadas e Lâmina Anti-Magia · Lâmina Rúnica II · XP",
  "Runic Aegis, Sword Rain and Anti-Magic Blade · Runic Blade II · XP")
e(O + "4.reward", "Sentença do Juramento e Balmung · +1 espaço de runa em toda lâmina · Lâmina Rúnica IV · XP",
  "Oath's Judgment and Balmung · +1 rune slot on every blade · Runic Blade IV · XP")
e(O + "5.reward", "Coroa Rúnica: técnicas do Cavaleiro +15%% e +2 de Armadura com espada · 8 Cristais de Mana · "
  "3 lendas: Avalon, Balmung e Corte Negro Anti-Magia · XP",
  "Runic Crown: Knight techniques +15%% and +2 Armor with a sword · 8 Mana Crystals · "
  "3 legends: Avalon, Balmung and Black Anti-Magic Cut · XP")
e("sword.isekai_mahou.class_knight.caliburn.passive", "aliados em 8 blocos ficam firmes (Resistência I). Despertar libera a "
  "técnica Caliburn — Espada da Seleção.",
  "allies within 8 blocks stand firm (Resistance I). Awakening unlocks the technique Caliburn — Sword of Selection.")
e("sword.isekai_mahou.class_knight.lake.passive", "na água ou na chuva o Vigor volta sozinho. Despertar libera a técnica "
  "Avalon — A Bainha Distante.",
  "in water or rain your Vigor refills by itself. Awakening unlocks the technique Avalon — The Distant Sheath.")

# ------------------------------------------------------------------ glifos
e(G + "knight_strike_air", "Vento Invisível — Golpe do Ar", "Invisible Air — Strike Air")
e(G + "knight_strike_air.desc", "FORMA do Cavaleiro (Vento; homenagem ao \"Strike Air\" do rei cavaleiro). Você solta de uma vez a "
  "barreira de vento que esconde a lâmina: um túnel de vento em espiral corre 16 blocos à frente (Acelerar +3) com 1,8 de raio "
  "(Expandir +); cada criatura no caminho recebe os efeitos uma vez e é arremessada. Depois o vento volta a envolver a espada: "
  "VENTO INVISÍVEL por 20 s — a lâmina some da vista (sua e dos outros), +2 de dano, +1 de alcance e golpes que empurram. "
  "Dividir = túneis paralelos. Pode ser estágio. Desbloqueio: Juramento I.",
  "Knight FORM (Wind; homage to the knight king's \"Strike Air\"). You release at once the wind barrier that hides your blade: a "
  "spiraling wind tunnel runs 16 blocks ahead (Accelerate +3) with a 1.8 radius (Expand +); every creature in its path takes the "
  "effects once and is hurled away. Then the wind wraps the sword again: INVISIBLE AIR for 20 s — the blade vanishes from sight "
  "(yours and others'), +2 damage, +1 reach and blows that push. Split = parallel tunnels. Can be a stage. Unlock: Oath I.")
e(G + "knight_anti_magic", "Lâmina Anti-Magia", "Anti-Magic Blade")
e(G + "knight_anti_magic.desc", "EFEITO do Cavaleiro (homenagem ao garoto sem mana das espadas negras). A lâmina se cobre de "
  "anti-magia. Num inimigo: corta 8 (×1,5 em quem estava com bênçãos ou é criatura mágica) e ANULA a magia dele — tira todas as "
  "bênçãos, Avalon e placas de Égide, interrompe a conjuração de jogadores e Silencia por 3 s (Estender +). Em você (Si "
  "Mesmo): Postura Anti-Magia por 6 s — dano de magia −75%%, maldições saem na hora e projéteis inimigos que chegam perto são "
  "cortados no ar. Desbloqueio: Juramento II.",
  "Knight EFFECT (homage to the manaless boy with the black swords). The blade is coated in anti-magic. On an enemy: cuts for 8 "
  "(×1.5 against anyone with blessings or a magical creature) and NULLIFIES their magic — strips every blessing, Avalon and Aegis "
  "plates, interrupts players' casting and Silences for 3 s (Extend +). On yourself (Self): Anti-Magic Stance for 6 s — spell "
  "damage −75%%, curses are removed at once and enemy projectiles that come close are cut in mid-air. Unlock: Oath II.")
e(G + "knight_caliburn_strike", "Caliburn — Espada da Seleção", "Caliburn — Sword of Selection")
e(G + "knight_caliburn_strike.desc", "EFEITO do Cavaleiro (Luz). A lâmina dourada da pedra desce sobre o alvo: 9 de dano (×1,4 com "
  "a Caliburn ou a Espada do Lago na mão). A seleção abençoa quem a empunha: você ganha 4 corações de Absorção por 10 s "
  "(Estender +) e os aliados a 4 blocos do alvo ganham Regeneração por 3 s. Desbloqueio: feito — despertar a Caliburn no "
  "Altar do Juramento.",
  "Knight EFFECT (Light). The golden blade of the stone falls upon the target: 9 damage (×1.4 with Caliburn or the Lake Sword in "
  "hand). The selection blesses its wielder: you gain 4 hearts of Absorption for 10 s (Extend +) and allies within 4 blocks of the "
  "target gain Regeneration for 3 s. Unlock: feat — awaken Caliburn at the Oath Altar.")
e(G + "knight_balmung", "Balmung — Queda do Crepúsculo", "Balmung — Twilight's Fall")
e(G + "knight_balmung.desc", "FORMA do Cavaleiro (Luz, Círculo 5; homenagem a Gram/Balmung, a espada que matou o dragão). A lâmina "
  "libera o brilho do crepúsculo num LEQUE de 140° e 12 blocos (Expandir +) que varre o campo em 5 ondas: todo inimigo no "
  "leque recebe os efeitos uma vez. MATADORA DE DRAGÕES: dragões, serpes e wyverns levam os efeitos com 175%% do poder; chefes, "
  "135%%. Dividir = mais varreduras. Pode ser estágio. Desbloqueio: Juramento IV.",
  "Knight FORM (Light, Circle 5; homage to Gram/Balmung, the sword that slew the dragon). The blade releases the glow of twilight "
  "in a 140° FAN of 12 blocks (Expand +) that sweeps the field in 5 waves: every enemy in the fan takes the effects once. "
  "DRAGON SLAYER: dragons, serpents and wyverns take the effects at 175%% power; bosses, 135%%. Split = more sweeps. Can be a "
  "stage. Unlock: Oath IV.")
e(G + "knight_avalon", "Avalon — A Bainha Distante", "Avalon — The Distant Sheath")
e(G + "knight_avalon.desc", "EFEITO do Cavaleiro (Luz, benéfico, Círculo 6). A bainha da espada do rei se abre em mil partículas de "
  "luz e afasta o alvo (você com Si Mesmo, ou aliados) do mundo por 4 s (Estender +; ×1,5 com a Espada do Lago na mão): QUASE "
  "INVULNERÁVEL — só o vazio passa. Cura na hora 25%% da vida máxima (× poder, até 40%%), tira as maldições, apaga o fogo e dá "
  "Regeneração II por 6 s. Desbloqueio: feito — despertar a Espada do Lago.",
  "Knight EFFECT (Light, beneficial, Circle 6). The sheath of the king's sword opens into a thousand motes of light and sets the "
  "target (you with Self, or allies) apart from the world for 4 s (Extend +; ×1.5 with the Lake Sword in hand): NEARLY "
  "INVULNERABLE — only the void gets through. Instantly heals 25%% of max health (× power, up to 40%%), removes curses, puts out "
  "fire and grants Regeneration II for 6 s. Unlock: feat — awaken the Lake Sword.")

VALID = re.compile(r"%(?:\d+\$)?s|%%")


def check(text, key):
    if "%" in VALID.sub("", text):
        sys.exit("'%' solto em " + key + ": " + text)


def merge():
    keys = set()
    for key, p, n in E:
        if key in keys:
            sys.exit("chave repetida: " + key)
        keys.add(key)
        check(p, key)
        check(n, key)
    for name, idx in (("pt_br", 1), ("en_us", 2)):
        path = os.path.join(LANG, name + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        for entry in E:
            data[entry[0]] = entry[idx]
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")


def icon():
    """Ícone 18x18: espada translúcida (só o contorno) com três correntes de vento em volta."""
    c = Canvas(18, 18)
    edge = hex_rgba("e8fbff", 200)
    ghost = hex_rgba("bff4ff", 90)
    wind = hex_rgba("7fdbff", 255)
    white = hex_rgba("ffffff", 255)
    # lâmina diagonal (contorno claro e miolo quase transparente)
    for i in range(3, 14):
        c.set(i, 16 - i, edge)
        c.set(i + 1, 16 - i, ghost)
        c.set(i, 15 - i, ghost)
    c.set(14, 2, white)
    c.set(15, 1, white)
    # guarda e cabo
    c.line(2, 12, 6, 16, edge)
    c.line(1, 16, 3, 14, hex_rgba("9ab8c8", 230))
    # correntes de vento
    for (x0, y0) in ((2, 5), (8, 2), (9, 11)):
        c.line(x0, y0, x0 + 4, y0, wind)
        c.set(x0 + 5, y0 + 1, wind)
        c.set(x0 + 4, y0 + 2, white)
    c.save(os.path.join(A, "textures", "mob_effect", "knight_invisible_air.png"))


if __name__ == "__main__":
    merge()
    icon()
    print("class_knight_techniques: %d chaves + ícone do Vento Invisível" % len(E))
