"""
Modulo "glyphs_classes_a" (v1.4) — traducoes e icones dos 16 glifos de CLASSE (rode da raiz:
    py -I tools/gen/glyphs_classes_a_icons.py            traducoes + icones
    py -I tools/gen/glyphs_classes_a_icons.py --no-icons so as traducoes

Traducoes: src/main/lang/glyphs_classes_a/{pt_br,en_us}.json (o script REESCREVE as chaves dele, mantendo as outras).
Icones: textures/gui/glyph/<id>.png (32x32), desenhados com a biblioteca do tools/gen/icons.py (mesma moldura por TIPO,
gema na COR DA CLASSE e o mesmo sombreado), com pictogramas proprios deste modulo. So grava os 16 ids daqui —
NUNCA roda a geracao completa do icons.py (ela sobrescreveria icones feitos a mao).
Confere que todo '%' e um marcador valido (%s, %N$s ou %%).
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
sys.path.insert(0, os.path.join(ROOT, "tools"))
sys.path.insert(0, HERE)
LANG = os.path.join(ROOT, "src", "main", "lang", "glyphs_classes_a")
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "textures", "gui", "glyph")
G = "glyph.isekai_mahou."
M = "message.isekai_mahou.glyphs_classes_a."
E = []


def e(key, pt, en):
    E.append((key, pt, en))


# ====================================================================================== mensagens
e(M + "reactive_only", "%s: esta magia sai sozinha quando a condição acontece (a tecla não conjura).",
  "%s: this spell fires on its own when the condition happens (the key doesn't cast it).")
e(M + "weave", "Trama elemental: %s/3", "Elemental weave: %s/3")
e(M + "guard", "Você protegeu %s!", "You protected %s!")
e(M + "metamagic_cost", "Metamagia: a magia precisa de %s de mana (custo +40%%).",
  "Metamagic: the spell needs %s mana (cost +40%%).")
e(M + "infusion_none", "Infusão de Poção: nenhuma poção na mão secundária nem na barra.",
  "Potion Infusion: no potion in your off hand or hotbar.")
e(M + "chorus_no_mana", "Coro dos Mortos: sem mana para mais vozes.", "Chorus of the Dead: no mana for more voices.")
e(M + "learned", "Novo glifo de classe: %s (%s). Outras classes só aprendem por Lição sua.",
  "New class glyph: %s (%s). Other classes can only learn it from your Lesson.")

LEARN_PT = " Outras classes: só por Lição de alguém da classe %s."
LEARN_EN = " Other classes: only from a Lesson by a %s."

# ====================================================================================== glifos
# (id, nome pt, nome en, descricao pt, descricao en)
GLYPHS = [
    ("mage_metamagic", "Metamagia", "Metamagic",
     "AUMENTO do Mago (Círculo 4). Magia sobre a magia: REPETE os aumentos que vieram ANTES dela neste glifo — cada "
     "Amplificar, Expandir, Estender, Acelerar, Dividir, Perfurar, Ricochete, Encadear e Carregar conta de novo (até +2 "
     "cada; Repetir até +1). Logo depois da forma/efeito, sem aumentos antes, não faz nada. Custo: a magia gasta +40%% de "
     "mana (ou de vida, com Sobrecarga) e leva +0,3 s para conjurar. Não acumula. Aprendizado: 3ª Tese do Mago (ou "
     "Círculo 6).",
     "Mage AUGMENT (Circle 4). Magic about magic: REPEATS the augments placed BEFORE it on this glyph — every Amplify, "
     "Expand, Extend, Accelerate, Split, Pierce, Ricochet, Chain and Charge counts again (up to +2 each; Repeat up to +1). "
     "Right after the form/effect with no augments before it, it does nothing. Cost: the spell spends +40%% mana (or "
     "health, with Overload) and takes +0.3 s to cast. Doesn't stack. Learned: the Mage's 3rd Thesis (or Circle 6).",
     "Mago", "Mage"),
    ("mage_trigger_weave", "Ao Tecer Elementos", "On Elemental Weave",
     "GATILHO do Mago (Círculo 4). Conjure 3 magias de elementos DIFERENTES (o Arcano não conta) em até 8 s: a magia deste "
     "slot sai SOZINHA, mirando o inimigo mais próximo. A barra mostra a trama (1/3, 2/3). A tecla do slot não conjura. "
     "Intervalo: 5 s. Aprendizado: 4ª Tese do Mago (ou Círculo 7).",
     "Mage TRIGGER (Circle 4). Cast 3 spells of DIFFERENT elements (Arcane doesn't count) within 8 s: this slot's spell "
     "fires ON ITS OWN, aimed at the nearest enemy. The action bar shows the weave (1/3, 2/3). The slot key doesn't cast it. "
     "Interval: 5 s. Learned: the Mage's 4th Thesis (or Circle 7).",
     "Mago", "Mage"),
    ("witch_potion_infusion", "Infusão de Poção", "Potion Infusion",
     "AUMENTO da Bruxa (Círculo 3). Ao conjurar, a magia GASTA a poção da mão secundária (ou a primeira da barra): efeitos "
     "bons vão para você e aliados a até 5 blocos (60%% da duração); efeitos ruins vão em cada inimigo que a magia ferir "
     "(50%% da duração, até 8 alvos). Poção persistente ainda deixa uma nuvem no primeiro alvo. Sem poção, não faz nada. "
     "Aprendizado: 3ª Revelação da Bruxa (ou Círculo 6).",
     "Witch AUGMENT (Circle 3). When cast, the spell CONSUMES the potion in your off hand (or the first one in your "
     "hotbar): good effects go to you and allies within 5 blocks (60%% duration); bad effects go on every enemy the spell "
     "hurts (50%% duration, up to 8 targets). A lingering potion also leaves a cloud on the first target. Without a potion "
     "it does nothing. Learned: the Witch's 3rd Revelation (or Circle 6).",
     "Bruxa", "Witch"),
    ("witch_trigger_potion", "Ao Beber Poção", "On Drinking a Potion",
     "GATILHO da Bruxa (Círculo 4). Quando você termina de BEBER uma poção (ou um preparado do caldeirão), a magia deste "
     "slot sai sozinha, mirando o inimigo mais próximo. A tecla do slot não conjura. Intervalo: 1 s. Aprendizado: 4ª "
     "Revelação da Bruxa (ou Círculo 7).",
     "Witch TRIGGER (Circle 4). When you finish DRINKING a potion (or a cauldron brew), this slot's spell fires on its "
     "own, aimed at the nearest enemy. The slot key doesn't cast it. Interval: 1 s. Learned: the Witch's 4th Revelation "
     "(or Circle 7).",
     "Bruxa", "Witch"),
    ("warlock_blood_pact", "Pacto de Sangue", "Blood Pact",
     "AUMENTO do Bruxo (Círculo 3). O Patrono cobra em SANGUE: a magia é paga com VIDA em vez de mana (como a Sobrecarga, "
     "com o mesmo +40%% de poder) e cobra +25%% de sangue a mais. Quanto menos vida, mais forte: +1%% de poder para cada "
     "1%% de vida que falta (até +50%%). Cada inimigo que a magia matar devolve 30%% do sangue pago (até 3). Nunca deixa "
     "você com menos de meio coração. Aprendizado: 5º pacto do Bruxo (ou Círculo 6).",
     "Warlock AUGMENT (Circle 3). The Patron collects in BLOOD: the spell is paid with HEALTH instead of mana (like "
     "Overload, with the same +40%% power) and costs +25%% extra blood. The less health you have, the stronger it gets: "
     "+1%% power for every 1%% of missing health (up to +50%%). Every enemy the spell kills refunds 30%% of the blood paid "
     "(up to 3). Never leaves you below half a heart. Learned: the Warlock's 5th pact (or Circle 6).",
     "Bruxo", "Warlock"),
    ("warlock_trigger_cursed", "Ao Ser Amaldiçoado", "On Being Cursed",
     "GATILHO do Bruxo (Círculo 4). Quando outra criatura aplica um efeito NEGATIVO em você (veneno, murchar, lentidão, "
     "fraqueza, cegueira, queimadura, maldições...), o Patrono devolve: a magia deste slot sai sozinha mirando QUEM "
     "amaldiçoou (ou o inimigo mais próximo). Silêncio e Exaustão não contam. A tecla do slot não conjura. Intervalo: 4 s. "
     "Aprendizado: 7º pacto do Bruxo (ou Círculo 7).",
     "Warlock TRIGGER (Circle 4). When another creature puts a NEGATIVE effect on you (poison, wither, slowness, weakness, "
     "blindness, burning, curses...), the Patron strikes back: this slot's spell fires on its own aimed at WHOEVER cursed "
     "you (or the nearest enemy). Silence and Exhaustion don't count. The slot key doesn't cast it. Interval: 4 s. "
     "Learned: the Warlock's 7th pact (or Circle 7).",
     "Bruxo", "Warlock"),
    ("druid_overgrowth", "Crescer Selvagem", "Wild Overgrowth",
     "AUMENTO do Druida (Círculo 3). Onde a magia ferir, a terra BROTA: um matagal de vinhas (raio 2,5 — Expandir na forma "
     "+; 6 s — Estender +) que ENRAÍZA cada inimigo na primeira vez que ele entra (2 s; chefes só ficam lentos), deixa "
     "lento quem está dentro e fere com espinhos; aliados dentro recuperam vida. Até 3 matagais seus ao mesmo tempo. "
     "Aprendizado: 5º estágio da Árvore Coração (ou Círculo 6).",
     "Druid AUGMENT (Circle 3). Wherever the spell hurts, the earth SPROUTS: a thicket of vines (radius 2.5 — Expand on the "
     "form +; 6 s — Extend +) that ROOTS each enemy the first time it enters (2 s; bosses are only slowed), slows everyone "
     "inside and stabs with thorns; allies inside regain health. Up to 3 thickets of yours at once. Learned: the Heart "
     "Tree's 5th stage (or Circle 6).",
     "Druida", "Druid"),
    ("druid_trigger_rooted", "Ao Fincar Raízes", "On Taking Root",
     "GATILHO do Druida (Círculo 4). Fique 3 s PARADO em chão natural (terra, grama, musgo, lama, areia ou folhas): você "
     "finca raízes e a magia deste slot sai sozinha — mirando o inimigo mais próximo — e repete a cada 6 s enquanto você "
     "continuar enraizado. Só dispara com inimigo por perto ou se você estiver ferido. A tecla do slot não conjura. "
     "Aprendizado: 7º estágio da Árvore Coração (ou Círculo 7).",
     "Druid TRIGGER (Circle 4). Stand STILL for 3 s on natural ground (dirt, grass, moss, mud, sand or leaves): you take "
     "root and this slot's spell fires on its own — aimed at the nearest enemy — and repeats every 6 s while you stay "
     "rooted. It only fires with an enemy nearby or while you are hurt. The slot key doesn't cast it. Learned: the Heart "
     "Tree's 7th stage (or Circle 7).",
     "Druida", "Druid"),
    ("necro_dead_chorus", "Coro dos Mortos", "Chorus of the Dead",
     "AUMENTO do Necromante (Círculo 4). Seus mortos conjuram com você: até 3 lacaios seus a até 16 blocos (servos, "
     "invocações, pets) soltam uma CÓPIA da magia da posição deles, mirando o alvo deles (ou para onde você olha), com 40%% "
     "do poder. Cada cópia custa 15%% da mana da magia. Não vale para formas presas a você (Si Mesmo, Aura, Postura, "
     "Toque...), Domínios e ultimates. Aprendizado: 4º Rito Ossuário (ou Círculo 6).",
     "Necromancer AUGMENT (Circle 4). Your dead cast with you: up to 3 of your minions within 16 blocks (servants, "
     "summons, pets) release a COPY of the spell from where they stand, aimed at their target (or where you look), at 40%% "
     "power. Each copy costs 15%% of the spell's mana. Doesn't work on forms tied to you (Self, Aura, Stance, Touch...), "
     "Domains or ultimates. Learned: the 4th Ossuary Rite (or Circle 6).",
     "Necromante", "Necromancer"),
    ("necro_trigger_minion_death", "Ao Morrer um Lacaio Seu", "On Minion Death",
     "GATILHO do Necromante (Círculo 4). Quando um lacaio seu (servo, invocação ou pet) morre a até 48 blocos, a alma dele "
     "volta para você e a magia deste slot sai sozinha, mirando QUEM o matou (ou o inimigo mais próximo do corpo). A tecla "
     "do slot não conjura. Intervalo: 1 s. Aprendizado: 5º Rito Ossuário (ou Círculo 7).",
     "Necromancer TRIGGER (Circle 4). When one of your minions (servant, summon or pet) dies within 48 blocks, its soul "
     "returns to you and this slot's spell fires on its own, aimed at WHOEVER killed it (or the enemy nearest the body). "
     "The slot key doesn't cast it. Interval: 1 s. Learned: the 5th Ossuary Rite (or Circle 7).",
     "Necromante", "Necromancer"),
    ("smith_forged_edge", "Fio da Forja", "Forged Edge",
     "AUMENTO do Ferreiro (Círculo 3). A magia leva o FIO da arma que você segura: cada acerto soma 60%% do dano da arma "
     "(×1,5). Lâminas (espadas, machados) fazem SANGRAR; armas pesadas (martelos, maças...) EMPURRAM. Custo: 1 de "
     "durabilidade da arma por golpe. Com a mão vazia, não faz nada. Aprendizado: Provas da Forja, rank II (ou Círculo 6).",
     "Smith AUGMENT (Circle 3). The spell carries the EDGE of the weapon you hold: each hit adds 60%% of the weapon's damage "
     "(×1.5). Blades (swords, axes) make the target BLEED; heavy weapons (hammers, maces...) KNOCK BACK. Cost: 1 weapon "
     "durability per hit. Empty-handed it does nothing. Learned: Forge Trials, rank II (or Circle 6).",
     "Ferreiro", "Smith"),
    ("smith_trigger_hammer", "Ao Malhar o Ferro", "On Striking the Iron",
     "GATILHO do Ferreiro (Círculo 4). Como o martelo na bigorna: o 3º golpe corpo a corpo SEGUIDO no mesmo alvo (até 3 s "
     "entre golpes) faz a magia deste slot sair sozinha nele. Forjar ou remodelar uma arma na Forja também dispara. A "
     "tecla do slot não conjura. Intervalo: 1,5 s. Aprendizado: Provas da Forja, rank III (ou Círculo 7).",
     "Smith TRIGGER (Circle 4). Like a hammer on the anvil: the 3rd melee hit IN A ROW on the same target (up to 3 s "
     "between hits) makes this slot's spell fire on its own at it. Forging or reshaping a weapon at the Forge also fires "
     "it. The slot key doesn't cast it. Interval: 1.5 s. Learned: Forge Trials, rank III (or Circle 7).",
     "Ferreiro", "Smith"),
    ("rogue_from_behind", "Pelas Costas", "From Behind",
     "AUMENTO do Ladino (Círculo 3). Se o alvo NÃO está olhando para você (você está atrás dele, ou invisível), o dano da "
     "magia é ×1,6 (chefes ×1,3) e ele sangra por 2 s. Se ele está de frente para você, ×0,85. Aprendizado: Livro-Razão, "
     "rank II (ou Círculo 6).",
     "Rogue AUGMENT (Circle 3). If the target is NOT looking at you (you are behind it, or invisible), the spell deals "
     "×1.6 damage (bosses ×1.3) and it bleeds for 2 s. If it is facing you, ×0.85. Learned: Ledger, rank II (or Circle 6).",
     "Ladino", "Rogue"),
    ("rogue_trigger_steal", "Ao Roubar", "On Stealing",
     "GATILHO do Ladino (Círculo 4). Quando você ROUBA alguma coisa — item, mana, bênção ou magia —, a magia deste slot "
     "sai sozinha, mirando a vítima mais próxima. A tecla do slot não conjura. Intervalo: 3 s. Aprendizado: Livro-Razão, "
     "rank III (ou Círculo 7).",
     "Rogue TRIGGER (Circle 4). When you STEAL something — an item, mana, a blessing or a spell —, this slot's spell fires "
     "on its own, aimed at the nearest victim. The slot key doesn't cast it. Interval: 3 s. Learned: Ledger, rank III (or "
     "Circle 7).",
     "Ladino", "Rogue"),
    ("knight_challenge", "Desafio", "Challenge",
     "AUMENTO do Cavaleiro (Círculo 3). Cada inimigo ferido pela magia fica PROVOCADO por 6 s (chefes 3 s): só consegue "
     "mirar você e causa −30%% de dano em quem não for você. Você ganha Absorção por 6 s — mais forte quanto mais inimigos "
     "provocar de uma vez (até nível III). Aprendizado: 2º Juramento (ou Círculo 6).",
     "Knight AUGMENT (Circle 3). Every enemy hurt by the spell is PROVOKED for 6 s (bosses 3 s): it can only target you "
     "and deals −30%% damage to anyone else. You gain Absorption for 6 s — stronger the more enemies you provoke at once "
     "(up to level III). Learned: 2nd Oath (or Circle 6).",
     "Cavaleiro", "Knight"),
    ("knight_trigger_guard", "Ao Proteger um Aliado", "On Protecting an Ally",
     "GATILHO do Cavaleiro (Círculo 4). Quando um aliado a até 8 blocos (jogador do grupo, pet, invocação) é atacado, você "
     "entra na frente: o golpe nele cai 25%% e a magia deste slot sai sozinha, mirando o atacante. A tecla do slot não "
     "conjura. Intervalo: 3 s. Aprendizado: 3º Juramento (ou Círculo 7).",
     "Knight TRIGGER (Circle 4). When an ally within 8 blocks (party player, pet, summon) is attacked, you step in: the "
     "blow on them drops 25%% and this slot's spell fires on its own, aimed at the attacker. The slot key doesn't cast it. "
     "Interval: 3 s. Learned: 3rd Oath (or Circle 7).",
     "Cavaleiro", "Knight"),
]

for gid, npt, nen, dpt, den, cpt, cen in GLYPHS:
    e(G + gid, npt, nen)
    e(G + gid + ".desc", dpt + LEARN_PT % cpt, den + LEARN_EN % cen)

VALID = re.compile(r"%(?:\d+\$)?s|%%")


def check(text, key):
    if "%" in VALID.sub("", text):
        sys.exit("'%' solto em " + key + ": " + text)


def write_lang():
    keys = set()
    for key, p, n in E:
        if key in keys:
            sys.exit("chave repetida: " + key)
        keys.add(key)
        check(p, key)
        check(n, key)
    os.makedirs(LANG, exist_ok=True)
    for name, idx in (("pt_br", 1), ("en_us", 2)):
        path = os.path.join(LANG, name + ".json")
        data = {}
        if os.path.exists(path):
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
        for entry in E:
            data[entry[0]] = entry[idx]
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print("glyphs_classes_a: %d chaves de traducao" % len(E))


# ====================================================================================== icones
# (id, tipo, classe = cor da gema, pictograma)
ICONS = [
    ("mage_metamagic", "augment", "mage", "gca_metamagic"),
    ("mage_trigger_weave", "trigger", "mage", "gca_weave"),
    ("witch_potion_infusion", "augment", "witch", "gca_infusion"),
    ("witch_trigger_potion", "trigger", "witch", "gca_drink"),
    ("warlock_blood_pact", "augment", "warlock", "gca_blood_pact"),
    ("warlock_trigger_cursed", "trigger", "warlock", "gca_cursed"),
    ("druid_overgrowth", "augment", "druid", "gca_overgrowth"),
    ("druid_trigger_rooted", "trigger", "druid", "gca_rooted"),
    ("necro_dead_chorus", "augment", "necromancer", "gca_chorus"),
    ("necro_trigger_minion_death", "trigger", "necromancer", "gca_minion_death"),
    ("smith_forged_edge", "augment", "smith", "gca_forged_edge"),
    ("smith_trigger_hammer", "trigger", "smith", "gca_hammer"),
    ("rogue_from_behind", "augment", "rogue", "gca_behind"),
    ("rogue_trigger_steal", "trigger", "rogue", "gca_steal"),
    ("knight_challenge", "augment", "knight", "gca_challenge"),
    ("knight_trigger_guard", "trigger", "knight", "gca_guard"),
]


def register_pics(ic):
    pic, sword, skull, shield, flask, drop, leaf, ghost = (ic.pic, ic.sword, ic.skull, ic.shield, ic.flask, ic.drop,
                                                         ic.leaf, ic.ghost)
    sparkle, orb, chevron, bag, figure, ground, spiral = (ic.sparkle, ic.orb, ic.chevron, ic.bag, ic.figure, ic.ground,
                                                         ic.spiral)

    @pic("gca_metamagic")
    def _(d):
        # Metamagia: uma runa no centro e DUAS setas girando em volta (o aumento conta duas vezes)
        d.arc_arrow(10, 10, 7.4, -70, 140, 1.8, 3.4, "p", g=0)
        d.arc_arrow(10, 10, 7.4, 110, 320, 1.8, 3.4, "mana", g=1)
        d.star(10, 10, 4.4, 1.8, 6, -90, "W", g=3)
        d.disc(10, 10, 1.2, "violet", g=4)

    @pic("gca_weave")
    def _(d):
        # Ao Tecer Elementos: tres orbes (fogo, agua, vento) ligados por fios tecidos
        d.line(10, 4, 4, 15, 1.2, "p.l", g=0)
        d.line(4, 15, 16, 15, 1.2, "p.l", g=0)
        d.line(16, 15, 10, 4, 1.2, "p.l", g=0)
        orb(d, 10, 4.2, 3.0, "fire", g=2)
        orb(d, 4.0, 15.0, 3.0, "water", g=3)
        orb(d, 16.0, 15.0, 3.0, "teal", g=4)
        sparkle(d, 10, 11.4, 2.6, "W", g=6)

    @pic("gca_infusion")
    def _(d):
        # Infusao de Pocao: frasco verde derramando gotas numa faisca da magia
        flask(d, 8.0, 11.0, "poison", g=0, level=0.6)
        drop(d, 16.0, 13.4, 2.4, "poison", 1.4, shine=False, g=4)
        drop(d, 16.8, 18.0, 1.4, "poison", 1.2, shine=False, g=5)
        sparkle(d, 15.6, 4.4, 3.4, "p", g=6)

    @pic("gca_drink")
    def _(d):
        # Ao Beber Pocao: frasco rosado inclinado com bolhas subindo
        flask(d, 10.0, 12.0, "pink", g=0, level=0.7)
        for i, (x, y, r) in enumerate(((5.0, 3.0, 1.4), (14.6, 2.0, 1.1), (16.4, 6.6, 0.9))):
            d.ring(x, y, r + 0.6, r - 0.2, "W", g=4 + i)

    @pic("gca_blood_pact")
    def _(d):
        # Pacto de Sangue: coracao atravessado por uma adaga, pingando
        d.heart(9.4, 10.6, 0.82, "blood", g=0)
        d.line(2.0, 18.0, 15.4, 4.6, 1.8, "steel", g=2)
        d.line(15.0, 5.0, 18.4, 1.6, 1.4, "obsidian", g=3)
        d.line(13.2, 3.6, 16.6, 7.0, 1.4, "gold", g=4)
        drop(d, 6.0, 18.0, 1.6, "blood", 1.3, shine=False, g=6)

    @pic("gca_cursed")
    def _(d):
        # Ao Ser Amaldicoado: pentagrama roxo e uma seta vermelha voltando para quem lancou
        d.ring(10, 11, 7.6, 6.4, "purple", g=0)
        d.star(10, 11, 6.2, 2.4, 5, -90, "purple.d", g=1)
        d.arc_arrow(10, 11, 9.0, 200, 330, 1.6, 3.2, "red", g=3)
        d.disc(10, 11, 1.6, "blood", g=5)

    @pic("gca_overgrowth")
    def _(d):
        # Crescer Selvagem: vinhas brotando do chao, com folhas e um espinho
        ground(d, 17, "dirt")
        d.curve([(5.0, 17.0), (3.0, 11.0), (6.0, 5.0)], 1.6, "leaf.d", g=1)
        d.curve([(10.0, 17.0), (12.0, 10.0), (9.0, 2.0)], 1.8, "leaf.d", g=1)
        d.curve([(15.0, 17.0), (17.4, 12.0), (15.0, 7.0)], 1.6, "leaf.d", g=1)
        for i, (x, y, r) in enumerate(((6.4, 5.0, -40), (9.0, 2.6, 30), (15.6, 7.2, 40), (13.0, 11.0, -30))):
            leaf(d, x, y, 0.36, r, "leaf", g=3 + i)
        d.tri((3.0, 12.0), (0.6, 11.0), (3.4, 10.4), "bone", g=8)

    @pic("gca_rooted")
    def _(d):
        # Ao Fincar Raizes: o druida parado com raizes descendo dos pes
        figure(d, "stand", 10.0, "p", g=0, oy=-2)
        for i, (x0, x1) in enumerate(((8.0, 3.0), (9.6, 8.0), (11.4, 13.0), (12.0, 17.4))):
            d.curve([(x0 + 0.4, 15.0), ((x0 + x1) / 2, 17.4), (x1, 19.4)], 1.2, "wood", g=3)
        leaf(d, 16.0, 4.0, 0.36, 30, "leaf", g=5)

    @pic("gca_chorus")
    def _(d):
        # Coro dos Mortos: um cranio grande e dois pequenos cantando junto (fios de alma)
        skull(d, 4.2, 13.0, 0.4, jaw=False, g=0)
        skull(d, 15.8, 13.0, 0.4, jaw=False, g=2)
        skull(d, 10.0, 9.6, 0.5, g=4)
        for i, x in enumerate((4.2, 10.0, 15.8)):
            d.curve([(x, 6.4 - (2.6 if x == 10.0 else 0)), (x + 1.4, 4.0 - (2.6 if x == 10.0 else 0)),
                     (x - 0.6, 1.6 - (1.4 if x == 10.0 else 0))], 1.0, "cyan", g=7 + i)

    @pic("gca_minion_death")
    def _(d):
        # Ao Morrer um Lacaio: ossos no chao e a alma subindo para o mestre
        ic.bone(d, 2.0, 17.4, 11.0, 15.0, "bone", 1.8, 1.3, g=0)
        skull(d, 14.0, 15.0, 0.4, g=2)
        ghost(d, 9.0, 6.6, 0.55, "cyan", g=4)
        d.arrow(15.6, 9.4, 17.6, 2.0, 1.2, 2.6, "cyan", g=6)

    @pic("gca_forged_edge")
    def _(d):
        # Fio da Forja: espada com o fio em brasa e faiscas
        sword(d, 3.0, 17.0, 17.0, 3.0, blade="steel", guard="iron", grip="wood", w=2.6, g=0)
        d.recolor(lambda x, y: x + y < 20 and x > 9, "ember", g=1, only="steel")
        sparkle(d, 15.6, 9.6, 3.2, "yellow", g=4)
        sparkle(d, 8.6, 3.6, 2.2, "flame", g=5)

    @pic("gca_hammer")
    def _(d):
        # Ao Malhar o Ferro: martelo descendo na bigorna, faiscas e tres batidas
        d.poly([(3, 13), (17, 13), (15, 15), (12, 15), (12, 17), (15, 18), (5, 18), (8, 17), (8, 15), (5, 15)], "iron", g=0)
        d.line(13.0, 2.0, 7.0, 9.0, 1.6, "wood", g=2)
        d.poly([(10.4, 2.4), (15.4, 7.2), (17.6, 4.8), (12.6, 0.2)], "steel", g=3)
        sparkle(d, 9.0, 11.0, 3.0, "yellow", g=5)
        for i in range(3):
            d.disc(2.0 + 2.2 * i, 2.0, 0.8, "ember", g=7)

    @pic("gca_behind")
    def _(d):
        # Pelas Costas: olho FECHADO (nao ve) e a adaga vindo por tras
        d.arc(7.4, 6.0, 5.2, 4.0, 20, 160, "p", g=0)
        for x in (4.0, 7.4, 10.8):
            d.line(x, 10.6, x - 0.6, 12.4, 1.0, "p.d", g=1)
        d.line(8.0, 18.6, 17.0, 9.6, 2.0, "steel", g=3)
        d.line(15.4, 8.0, 18.6, 11.2, 1.4, "obsidian", g=4)
        d.line(17.0, 9.6, 19.4, 7.2, 1.6, "wood", g=5)
        drop(d, 6.4, 18.0, 1.4, "blood", 1.2, shine=False, g=6)

    @pic("gca_steal")
    def _(d):
        # Ao Roubar: saco de moedas sendo puxado pela seta do ladino
        bag(d, 8.0, 11.0, "brown", "gold", g=0)
        d.disc(8.0, 13.4, 1.6, "gold", g=3)
        d.arrow(18.6, 4.0, 13.0, 4.0, 1.6, 3.0, "p", g=5)
        sparkle(d, 15.6, 15.6, 2.4, "gold", g=6)

    @pic("gca_challenge")
    def _(d):
        # Desafio: escudo com o "!" vermelho e espada cruzada atras
        sword(d, 2.0, 18.0, 18.0, 2.0, blade="steel", guard="gold", grip="wood", w=2.0, g=0)
        shield(d, 10.0, 10.4, 0.82, "steel", trim="p", g=2)
        d.rect(9, 5, 10, 10, "red", g=5)
        d.rect(9, 12, 10, 13, "red", g=5)

    @pic("gca_guard")
    def _(d):
        # Ao Proteger um Aliado: o escudo na frente de um aliado e a flecha batendo nele
        figure(d, "stand", 14.6, "p.l", g=0)
        shield(d, 8.4, 10.6, 0.72, "steel", trim="gold", g=2)
        d.arrow(-0.4, 4.0, 3.6, 8.0, 1.2, 2.6, "red", g=5)
        sparkle(d, 3.4, 9.4, 2.2, "W", g=6)


def write_icons():
    saved = sys.argv[:]
    sys.argv = [sys.argv[0]]
    try:
        import icons as ic
    finally:
        sys.argv = saved
    register_pics(ic)
    os.makedirs(OUT, exist_ok=True)
    seen = {}
    for gid, kind, cls, name in ICONS:
        spec = {"kind": kind, "elem": cls, "pic": name, "badges": [], "legend": False}
        img = ic.make_glyph_icon(spec)
        key = repr(img)
        if key in seen:
            print("ATENCAO: icone igual a", seen[key], "->", gid)
        seen[key] = gid
        ic.save_px(img, os.path.join(OUT, gid + ".png"))
    print("glyphs_classes_a: %d icones" % len(ICONS))
    if "--preview" in sys.argv:
        path = sys.argv[sys.argv.index("--preview") + 1]
        imgs = [(gid, ic.make_glyph_icon({"kind": k, "elem": c, "pic": n, "badges": [], "legend": False}))
                for gid, k, c, n in ICONS]
        ic.sheet(imgs, 4, 8, path)
        print("previa:", path)


def main():
    write_lang()
    if "--no-icons" not in sys.argv:
        write_icons()


if __name__ == "__main__":
    main()
