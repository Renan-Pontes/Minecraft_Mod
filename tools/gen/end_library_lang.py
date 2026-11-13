"""
Traducoes (pt_br principal + en_us) e capitulo do Diario do modulo end_library.
Rodar a partir da raiz do projeto:  py -I tools/gen/end_library_lang.py
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src/main/lang/end_library")
JOURNAL = os.path.join(ROOT, "src/main/resources/data/isekai_mahou/journal/end_library.json")
NS = "isekai_mahou"
L = {}


def t(key, pt, en):
    L[key] = (pt, en)


# ---------------------------------------------------------------- entidades, blocos, itens
for eid, pt, en in (("star_devourer", "Devorador de Estrelas", "Star Devourer"), ("void_scribe", "Escriba do Vazio", "Void Scribe"),
                    ("star_wisp", "Fagulha Estelar", "Star Wisp")):
    t(f"entity.{NS}.{eid}", pt, en)
    t(f"item.{NS}.{eid}_spawn_egg", f"Ovo Gerador de {pt}", f"{en} Spawn Egg")
t(f"entity.{NS}.void_treasure_blade", "Arma do Tesouro", "Treasure Blade")
t(f"block.{NS}.star_fragment_ore", "Minério de Fragmento Estelar", "Star Fragment Ore")
t(f"block.{NS}.void_bookshelf", "Estante do Vazio", "Void Bookshelf")
t(f"item.{NS}.star_fragment", "Fragmento Estelar", "Star Fragment")
t(f"item.{NS}.star_ingot", "Lingote Estelar", "Star Ingot")
tpl = f"item.{NS}.star_upgrade_smithing_template"
t(tpl, "Molde de Ferraria", "Smithing Template")
t(tpl + ".applies_to", "Equipamento de Netherita", "Netherite Equipment")
t(tpl + ".ingredients", "Lingote Estelar", "Star Ingot")
t(tpl + ".upgrade", "Melhoria Estelar", "Star Upgrade")
t(tpl + ".base_slot", "Coloque uma arma, ferramenta ou armadura de netherita", "Add netherite armor, weapon, or tool")
t(tpl + ".additions_slot", "Coloque um Lingote Estelar", "Add a Star Ingot")
t(f"item.{NS}.star_sword", "Espada Estelar", "Star Sword")
t(f"item.{NS}.star_pickaxe", "Picareta Estelar", "Star Pickaxe")
t(f"item.{NS}.star_helmet", "Elmo Estelar", "Star Helmet")
t(f"item.{NS}.star_chestplate", "Peitoral Estelar", "Star Chestplate")
t(f"item.{NS}.star_leggings", "Calças Estelares", "Star Leggings")
t(f"item.{NS}.star_boots", "Botas Estelares", "Star Boots")
t(f"item.{NS}.star_gear.requires", "Só obedece a quem chegou ao Círculo %s.", "Only obeys those who reached Circle %s.")
t(f"item.{NS}.star_gear.set_bonus", "Conjunto completo: visão no escuro e Passo Estelar (cair no vazio do End te devolve ao chão).",
  "Full set: night vision and Star Step (falling into the End's void returns you to solid ground).")
t(f"item.{NS}.void_tome", "Tomo Selado do Vazio", "Sealed Void Tome")
t(f"item.{NS}.void_tome.desc", "Um livro acorrentado da Biblioteca do Vazio. Algo lá dentro sussurra sobre o espaço.",
  "A chained book from the Void Library. Something inside whispers about space.")
t(f"item.{NS}.void_tome.use", "Usar: rompe o selo e revela um Pergaminho de Glifo espacial.", "Use: breaks the seal and reveals a spatial Glyph Scroll.")
t(f"item.{NS}.void_tome.opened", "O selo se rompe... %s!", "The seal breaks... %s!")
t(f"item.{NS}.void_tome.empty", "As páginas estão em branco. Nenhum glifo respondeu.", "The pages are blank. No glyph answered.")
t(f"item.{NS}.void_treasure_gate", "Portão do Tesouro", "Gate of Treasures")
t(f"item.{NS}.void_treasure_gate.desc", "A chave de um tesouro sem fim. Os portais se abrem às suas costas.",
  "The key to an endless treasury. The portals open behind you.")
t(f"item.{NS}.void_treasure_gate.use", "Usar: abre portais dourados que disparam armas para onde você mira (%s de mana; mais armas com o Círculo).",
  "Use: opens golden portals that fire weapons where you aim (%s mana; more weapons with your Circle).")
t(f"item.{NS}.void_treasure_gate.opened", "Os Portões do Tesouro se abrem!", "The Gates of Treasure open!")
st = f"item.{NS}.void_overlord_staff"
t(st, "Cajado do Rei Supremo", "Staff of the Supreme King")
t(st + ".desc", "Sete serpentes douradas seguram sete joias. Os mortos se curvam a quem o empunha.",
  "Seven golden serpents hold seven gems. The dead bow to whoever wields it.")
t(st + ".use", "Usar: ergue 3 Guerreiros de Osso aliados por 60 s (%s de mana).", "Use: raises 3 allied Bone Warriors for 60 s (%s mana).")
t(st + ".use_sneak", "Agachado + usar: Queda do Céu — um pilar de luz cai onde você mira (Círculo %s, %s de mana, recarga 5 min).",
  "Sneak + use: Fall of the Sky — a pillar of light strikes where you aim (Circle %s, %s mana, 5 min cooldown).")
t(st + ".raised", "%s guerreiros erguem-se para servi-lo.", "%s warriors rise to serve you.")
t(st + ".no_undead", "Nenhum morto responde (as invocações de mortos-vivos não estão neste mundo).",
  "No dead answer (undead summons are not present in this world).")
t(st + ".need_circle", "A Queda do Céu exige o Círculo %s.", "Fall of the Sky requires Circle %s.")
t(st + ".skyfall", "Você rasga o céu... olhe para cima!", "You tear the sky open... look up!")
t(st + ".skyfall_cd", "O céu ainda não se recompôs (%s s).", "The sky has not mended yet (%s s).")
t(st + ".skyfall_name", "Queda do Céu", "Fall of the Sky")

# ---------------------------------------------------------------- mensagens e comandos
m = f"message.{NS}.end_library."
t(m + "found", "Você encontrou a Biblioteca do Vazio. Estantes sem fim flutuam sobre o nada...",
  "You found the Void Library. Endless shelves float above nothingness...")
t(m + "no_mana", "Mana insuficiente (precisa de %s).", "Not enough mana (needs %s).")
t(m + "star_step", "Passo Estelar! As estrelas te devolvem ao chão.", "Star Step! The stars return you to solid ground.")
t(m + "star_rejects", "A armadura estelar rejeita você (exige Círculo %s).", "The star armor rejects you (requires Circle %s).")
t(m + "star_too_weak", "O equipamento estelar não obedece (exige Círculo %s).", "The star gear does not obey (requires Circle %s).")
c = f"command.{NS}.end_library."
t(c + "given", "Kit da Biblioteca do Vazio entregue.", "Void Library kit given.")
t(c + "no_end", "O End não existe neste servidor.", "The End does not exist on this server.")
t(c + "not_found", "Nenhuma Biblioteca do Vazio encontrada por perto.", "No Void Library found nearby.")
t(c + "located", "Biblioteca do Vazio no End em X %s, Z %s.", "Void Library in the End at X %s, Z %s.")
t(c + "place_failed", "Não foi possível gerar aqui.", "Could not generate here.")
t(c + "placed", "Biblioteca do Vazio gerada (centro X %s, Z %s).", "Void Library generated (center X %s, Z %s).")
t(c + "spawned", "%s invocado.", "%s summoned.")
t(f"place.{NS}.end_library", "Biblioteca do Vazio", "Void Library")
t(f"attack.{NS}.void_scribe.gravity_line", "Escriba do Vazio — Linha de Gravidade", "Void Scribe — Gravity Line")

# ---------------------------------------------------------------- chefe
b = "star_devourer"
t(f"boss.{NS}.{b}.subtitle", "Aquele que Come o Céu", "The One Who Eats the Sky")
t(f"boss.{NS}.{b}.phase1", "Chuva de Meteoros", "Meteor Rain")
t(f"boss.{NS}.{b}.phase2", "Buraco Negro", "Black Hole")
for k, pt, en in (("intro", "Mais uma estrela entrou no meu observatório... e estrelas, eu DEVORO.",
                   "Another star entered my observatory... and stars, I DEVOUR."),
                  ("phase1", "O céu está com fome. Que ele caia sobre vocês!", "The sky is hungry. Let it fall upon you!"),
                  ("phase2", "Vejam o fim de toda luz: o vazio que tudo engole!", "Behold the end of all light: the void that swallows all!"),
                  ("death", "Eu... era... o céu inteiro...", "I... was... the whole sky..."),
                  ("meteor", "Estrelas, caiam!", "Stars, fall!"),
                  ("black_hole", "NADA escapa da minha fome!", "NOTHING escapes my hunger!"),
                  ("summon", "Venham, minhas pequenas estrelas.", "Come, my little stars."),
                  ("flip", "Em cima vira embaixo.", "Up becomes down.")):
    t(f"announce.{NS}.{b}.{k}", pt, en)
for k, pt, en in (("gravity_well", "Devorador de Estrelas — Poço Gravitacional", "Star Devourer — Gravity Well"),
                  ("gravity_flip", "Devorador de Estrelas — Inversão de Gravidade", "Star Devourer — Gravity Flip"),
                  ("meteor_rain", "Devorador de Estrelas — Chuva de Meteoros", "Star Devourer — Meteor Rain"),
                  ("star_beam", "Devorador de Estrelas — Rajada Estelar", "Star Devourer — Star Beam"),
                  ("black_hole", "Devorador de Estrelas — Colapso do Buraco Negro", "Star Devourer — Black Hole Collapse")):
    t(f"attack.{NS}.{b}.{k}", pt, en)
for k, pt, en in (("gravity_flip", "Saia do círculo que te segue antes que ele pare: quem fica dentro flutua e é jogado no chão!",
                   "Leave the circle following you before it stops: whoever stays inside floats and is slammed down!"),
                  ("black_hole", "O Buraco Negro puxa tudo! Corra para FORA do círculo vermelho antes do colapso.",
                   "The Black Hole pulls everything! Run OUT of the red circle before it collapses."),
                  ("unreachable", "O Devorador atravessa o espaço até você!", "The Devourer crosses space to reach you!")):
    t(f"message.{NS}.{b}.{k}", pt, en)

# ---------------------------------------------------------------- Diario
j = f"journal.{NS}.end_library."
t(j + "title", "A Biblioteca do Vazio", "The Void Library")
t(j + "intro", "Dizem que além do Dragão do Fim, nas ilhas que flutuam sobre o nada, existe uma biblioteca que guarda os segredos do próprio espaço. Vamos ler o que o vazio escreveu?",
  "They say that beyond the Ender Dragon, on islands floating over nothingness, there is a library that keeps the secrets of space itself. Shall we read what the void wrote?")
t(j + "lore", "Uma torre de estantes em ruínas sobre as ilhas externas do End. No salão, Escribas encapuzados ainda copiam livros que ninguém lê; no Arquivo, tomos acorrentados guardam glifos do espaço; e no Observatório, no topo, algo enorme dorme olhando para as estrelas — e comendo-as.",
  "A ruined tower of shelves on the End's outer islands. In the hall, hooded Scribes still copy books no one reads; in the Archive, chained tomes keep glyphs of space; and in the Observatory at the top, something enormous sleeps watching the stars — and eating them.")
t(j + "outro", "O Devorador se desfaz numa chuva de luz. Pela primeira vez em eras, as estrelas sobre a Biblioteca brilham inteiras.",
  "The Devourer dissolves into a rain of light. For the first time in ages, the stars above the Library shine whole.")
steps = [
    ("enter_end", "Chegue ao End", "Reach the End",
     "Encontre a Fortaleza com os Olhos do Ender, ative o portal e entre no End. A Biblioteca fica nas ilhas EXTERNAS: depois do dragão, use o portal de saída (o pequeno, de ender pearl).",
     "Find the Stronghold with Eyes of Ender, light the portal and enter the End. The Library is on the OUTER islands: after the dragon, use the exit gateway (the small one, via ender pearl).",
     "Além do Dragão do Fim, o vazio tem bibliotecários.", "Beyond the Ender Dragon, the void has librarians.", "Fortaleza", "Stronghold"),
    ("find", "Encontre a Biblioteca do Vazio", "Find the Void Library",
     "Nas ilhas externas do End (planaltos e encostas), procure uma torre redonda de purpura com estantes, sempre sobre terra firme. O rastreador aponta a mais próxima.",
     "On the End's outer islands (highlands and midlands), look for a round purpur tower full of shelves, always on solid ground. The tracker points to the nearest one.",
     "Estantes sem fim sobre o nada.", "Endless shelves over nothingness.", "Ilhas externas do End", "End outer islands"),
    ("tome", "Abra um Tomo Selado do Vazio", "Open a Sealed Void Tome",
     "Os baús do Salão e do Arquivo (e às vezes os Escribas) guardam Tomos Selados. Abra um para revelar um Pergaminho de Glifo espacial.",
     "Chests in the Hall and the Archive (and sometimes Scribes) keep Sealed Tomes. Open one to reveal a spatial Glyph Scroll.",
     "Inventário Dimensional, portais, teleporte... o espaço tem gramática.", "Dimensional Inventory, portals, teleport... space has a grammar.",
     "Baús da Biblioteca", "Library chests"),
    ("fragment", "Consiga um Fragmento Estelar", "Obtain a Star Fragment",
     "Minério raro dentro da pedra do End nas ilhas externas (picareta de diamante). Fagulhas Estelares e os baús também deixam alguns.",
     "Rare ore inside End stone on the outer islands (diamond pickaxe). Star Wisps and the chests also drop some.",
     "Pedaços de estrela caídos que ninguém viu cair.", "Pieces of fallen stars no one saw fall.", "Pedra do End", "End stone"),
    ("boss", "Derrote o Devorador de Estrelas", "Defeat the Star Devourer",
     "Suba pelas escadas do Salão e do Arquivo até o Observatório. Fase 1: saia dos círculos de gravidade. Fase 2: meteoros caem onde você está — não pare. Fase 3: corra para fora do Buraco Negro antes do colapso.",
     "Climb the Hall and Archive stairs to the Observatory. Phase 1: leave the gravity circles. Phase 2: meteors fall where you stand — keep moving. Phase 3: run out of the Black Hole before it collapses.",
     "Ele come estrelas. Você parece uma.", "It eats stars. You look like one.", "Observatório", "Observatory"),
    ("star_gear", "Vista o equipamento estelar", "Wear the star gear",
     "Lingote Estelar = 4 Fragmentos + 2 ouro + 1 diamante + 1 Olho do Ender. Na mesa de ferraria: Molde Estelar + peça de netherita + Lingote. Só obedece a quem chegou ao Círculo 7.",
     "Star Ingot = 4 Fragments + 2 gold + 1 diamond + 1 Eye of Ender. At the smithing table: Star Template + netherite piece + Ingot. Only obeys those at Circle 7.",
     "O topo do que um ferreiro pode forjar.", "The peak of what a smith can forge.", "Mesa de ferraria", "Smithing table"),
]
for sid, pt, en, hpt, hen, lpt, len_, tpt, ten in steps:
    t(j + sid, pt, en)
    t(j + sid + ".hint", hpt, hen)
    t(j + sid + ".lore", lpt, len_)
    t(j + sid + ".tip", tpt, ten)


def write_lang():
    os.makedirs(LANG, exist_ok=True)
    for idx, name in ((0, "pt_br"), (1, "en_us")):
        with open(os.path.join(LANG, name + ".json"), "w", encoding="utf-8") as f:
            json.dump({k: v[idx] for k, v in sorted(L.items())}, f, indent=2, ensure_ascii=False)
            f.write("\n")


def write_journal():
    lib = {"structure": f"{NS}:end_library", "dimension": "minecraft:the_end"}
    # v1.0 (onda 5): o capítulo saiu de depois do Dragão (order 64) para perto do fim (Ato V, antes do Crepúsculo); XP na escala dos vizinhos.
    chapter = {
        "id": "end_library", "order": 48, "color": "#B98CFF", "intro": True,
        "icon": [f"{NS}:void_tome", f"{NS}:star_fragment", f"{NS}:void_treasure_gate"],
        "steps": [
            {"id": "enter_end", "condition": {"type": "visit", "dimension": "minecraft:the_end"}, "xp": 250,
             "icon": "minecraft:ender_eye", "locate": {"structure": "minecraft:stronghold", "dimension": "minecraft:overworld"}},
            {"id": "find", "condition": {"type": "flag", "flag": "end_library.found"}, "xp": 400,
             "icon": f"{NS}:void_bookshelf", "locate": lib},
            {"id": "tome", "condition": {"type": "flag", "flag": "end_library.tome_read"}, "optional": True, "xp": 250,
             "icon": f"{NS}:void_tome", "locate": lib},
            {"id": "fragment", "condition": {"type": "item", "item": f"{NS}:star_fragment"}, "optional": True, "xp": 300,
             "icon": f"{NS}:star_fragment"},
            {"id": "boss", "condition": {"type": "bosses", "ids": ["star_devourer"], "min": 1}, "xp": 1100,
             "icon": f"{NS}:star_devourer_spawn_egg", "locate": lib, "requires": [f"{NS}:star_devourer"],
             "rewards": {"items": [{"id": "minecraft:golden_apple", "count": 3}]}},
            {"id": "star_gear", "condition": {"type": "flag", "flag": "end_library.star_gear"}, "optional": True, "xp": 600,
             "icon": f"{NS}:star_chestplate"},
        ],
        "rewards": {"xp": 3000, "items": [{"id": f"{NS}:void_tome", "count": 1}, {"id": f"{NS}:mana_crystal", "count": 16}]},
    }
    os.makedirs(os.path.dirname(JOURNAL), exist_ok=True)
    with open(JOURNAL, "w", encoding="utf-8") as f:
        json.dump({"chapters": [chapter]}, f, indent=2, ensure_ascii=False)
        f.write("\n")


if __name__ == "__main__":
    write_lang()
    write_journal()
    print("end_library: %d traducoes + diario" % len(L))
