"""
Traducoes do modulo class_gunslinger (pt_br + en_us) -> src/main/lang/class_gunslinger/{pt_br,en_us}.json
Rodado pelo tools/gen/class_gunslinger.py (ou sozinho: py -I tools/gen/class_gunslinger_lang.py).
Os textos da classe na tela de escolha (path.isekai_mahou.gunslinger.*: nome, estilo, desc, system, bonus) sao do
nucleo (lang/core_classes) e NAO sao redefinidos aqui.
"""
import json
import os

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
LANG = os.path.join(ROOT, "src", "main", "lang", "class_gunslinger")

# (chave, pt_br, en_us)
ENTRIES = []


def e(key, pt, en):
    ENTRIES.append((key, pt, en))


# ------------------------------------------------------------------ itens e entidades
e("item.isekai_mahou.mana_revolver", "Revólver de Mana", "Mana Revolver")
for n, r in ((1, "I"), (2, "II"), (3, "III"), (4, "IV")):
    e("item.isekai_mahou.mana_revolver_t%d" % n, "Revólver de Mana %s" % r, "Mana Revolver %s" % r)
e("item.isekai_mahou.mana_pistol", "Pistola de Mana", "Mana Pistol")
e("item.isekai_mahou.mana_shotgun", "Escopeta de Mana", "Mana Shotgun")
e("item.isekai_mahou.mana_dual_pistols", "Pistolas Duplas de Mana", "Mana Dual Pistols")
e("item.isekai_mahou.mana_rifle", "Fuzil de Mana", "Mana Rifle")
e("item.isekai_mahou.gunslinger_outlaw_spawn_egg", "Ovo Gerador de Forasteiro Espectral", "Spectral Outlaw Spawn Egg")
e("entity.isekai_mahou.gunslinger_outlaw", "Forasteiro Espectral", "Spectral Outlaw")
e("entity.isekai_mahou.mana_bullet", "Bala de Mana", "Mana Bullet")

TIER_NAMES = {
    "apprentice": ("Forasteiro", "Drifter", "do Forasteiro", "Drifter's"),
    "adept": ("Caçador de Recompensas", "Bounty Hunter", "do Caçador de Recompensas", "Bounty Hunter's"),
    "master": ("Xerife Rubro", "Crimson Sheriff", "do Xerife Rubro", "Crimson Sheriff's"),
    "archmage": ("Lenda do Meio-Dia", "High Noon Legend", "da Lenda do Meio-Dia", "High Noon Legend's"),
}
PIECES = {
    "helmet": ("Chapéu", "Hat"),
    "chestplate": ("Guarda-pó", "Duster"),
    "leggings": ("Calças com Coldres", "Holstered Trousers"),
    "boots": ("Botas de Espora", "Spurred Boots"),
}
FLAVOR = {
    "apprentice": ("Poeira da estrada, bandana vermelha e um revólver de mana no coldre.",
                   "Road dust, a red bandana and a mana revolver in the holster."),
    "adept": ("Guarda-pó cinza, conchas de prata e cartuchos que brilham azul: a recompensa sempre chega.",
              "Grey duster, silver conchos and cartridges glowing blue: the bounty always gets paid."),
    "master": ("Casaco carmesim, chapéu branco e a estrela de ouro: a lei mais rápida do oeste arcano.",
               "Crimson coat, white hat and a golden star: the fastest law in the arcane west."),
    "archmage": ("Preto e ouro, serape em brasa e esporas que cantam: quando o sino toca, o duelo já acabou.",
                 "Black and gold, an ember serape and singing spurs: when the bell tolls, the duel is already over."),
}
for tid, (pt_t, en_t, pt_of, en_of) in TIER_NAMES.items():
    e("outfit.isekai_mahou.gunslinger.tier." + tid, pt_t, en_t)
    e("tooltip.isekai_mahou.gunslinger.outfit." + tid, *FLAVOR[tid])
    for piece, (pt_p, en_p) in PIECES.items():
        iid = ("gunslinger_" + piece) if tid == "apprentice" else ("gunslinger_%s_%s" % (tid, piece))
        e("item.isekai_mahou." + iid, "%s %s" % (pt_p, pt_of), "%s %s" % (en_of, en_p))

# ------------------------------------------------------------------ armas (GunKind)
GUNS = {
    "revolver": ("Revólver de Mana", "Mana Revolver",
                 "Seis câmaras de mana: tiro forte e certeiro. O do kit cresce em graus (I–IV) na Guilda e nas Provações.",
                 "Six mana chambers: a strong, accurate shot. The kit revolver grows in grades (I–IV) at the Guild and Trials."),
    "pistol": ("Pistola de Mana", "Mana Pistol",
               "Automática: segure o botão. 12 cartuchos, cadência alta e dano menor por bala.",
               "Automatic: hold the button. 12 rounds, high fire rate, lower damage per bullet."),
    "shotgun": ("Escopeta de Mana", "Mana Shotgun",
                "Cano duplo serrado: 7 chumbos por tiro e um coice que empurra. Perfeita à queima-roupa. 2 cartuchos.",
                "Sawed-off double barrel: 7 pellets per shot and a kick that pushes you back. Point-blank perfection. 2 rounds."),
    "dual_pistols": ("Pistolas Duplas de Mana", "Mana Dual Pistols",
                     "Uma em cada mão (a segunda aparece na mão vazia), atirando alternado. 16 cartuchos, cadência altíssima.",
                     "One in each hand (the second appears in your empty hand), firing in turns. 16 rounds, blistering fire rate."),
    "rifle": ("Fuzil de Mana", "Mana Rifle",
              "Fuzil de alavanca com luneta (agache para mirar). A bala atravessa 1 alvo; dano altíssimo. 5 cartuchos.",
              "Lever-action rifle with a scope (sneak to aim). The bullet pierces 1 target; huge damage. 5 rounds."),
}
for gid, (pt, en, pt_d, en_d) in GUNS.items():
    e("gun.isekai_mahou.gunslinger." + gid, pt, en)
    e("gun.isekai_mahou.gunslinger." + gid + ".desc", pt_d, en_d)

# ------------------------------------------------------------------ glifos de origem
GLYPHS = {
    "gun_bullet": ("Bala", "Bullet",
                   "Forma de arma do Pistoleiro: uma bala de mana muito rápida (alcance ~96 blocos) leva os efeitos da magia. "
                   "Dividir = balas lado a lado. Com uma arma de mana carregada na mão gasta 1 cartucho e sai +10% mais forte "
                   "(+5% por grau do revólver). Tiro na cabeça: ×1,5 (Pistoleiro) ou ×1,25.",
                   "Gunslinger weapon form: a very fast mana bullet (~96 block range) carries the spell's effects. Split = bullets "
                   "side by side. With a loaded mana gun in hand it spends 1 round and hits 10% harder (+5% per revolver grade). "
                   "Headshot: ×1.5 (Gunslinger) or ×1.25."),
    "gun_burst": ("Rajada de Balas", "Bullet Burst",
                  "Forma de arma: três tiros seguidos (+1 por Dividir), um a cada 3 ticks, seguindo a sua mira. Cada tiro é uma "
                  "cópia e paga os efeitos (custo por cópia).",
                  "Weapon form: three shots in a row (+1 per Split), one every 3 ticks, following your aim. Each shot is a copy "
                  "and pays for the effects (per-copy cost)."),
    "gun_charged_shot": ("Tiro Carregado", "Charged Shot",
                         "Forma de arma: segure a mira por 1 s e solte uma bala pesada com estrondo sônico — ×1,8 de poder, "
                         "atravessa 1 alvo e empurra.",
                         "Weapon form: hold your aim for 1 s and release a heavy bullet with a sonic boom — ×1.8 power, pierces "
                         "1 target and knocks back."),
    "gun_ricochet": ("Tiro Ricochete", "Ricochet Shot",
                     "Forma de arma: a bala salta de inimigo em inimigo (2 saltos + Ricochete, procura até 14 blocos) aplicando a "
                     "magia em cada um. Cada salto conta como cópia no custo.",
                     "Weapon form: the bullet bounces from enemy to enemy (2 bounces + Ricochet, seeks up to 14 blocks) applying "
                     "the spell to each one. Every bounce counts as a copy in the cost."),
    "gun_piercing": ("Bala Perfurante", "Piercing Round",
                     "Forma de arma: tiro INSTANTÂNEO em linha reta até 64 blocos (×Acelerar) que atravessa até 5 inimigos "
                     "(+Perfurar) e para no primeiro bloco sólido. Tiro na cabeça conta.",
                     "Weapon form: an INSTANT straight shot up to 64 blocks (×Accelerate) that passes through up to 5 enemies "
                     "(+Pierce) and stops at the first solid block. Headshots count."),
    "gun_high_noon": ("Doze Badaladas", "Twelve Tolls",
                      "Forma G (ultimate) do Pistoleiro: o sino do meio-dia toca três vezes — os inimigos à sua frente (até 32 "
                      "blocos, ×Expandir) ficam marcados e lentos; na última badalada você saca e acerta todos de uma vez: cada "
                      "tiro é tiro na cabeça com ×2 de poder. 6 alvos (+2 por Dividir, máx. 14). Círculo 4.",
                      "Gunslinger G form (ultimate): the noon bell tolls three times — enemies in front of you (up to 32 blocks, "
                      "×Expand) are marked and slowed; on the last toll you draw and hit them all at once: every shot is a "
                      "headshot with ×2 power. 6 targets (+2 per Split, max 14). Circle 4."),
    "gun_incendiary": ("Munição Incendiária", "Incendiary Rounds",
                       "Efeito de Fogo do Pistoleiro: dano de fogo (base 4,5), incendeia e acumula Queimando por 4 s (×Estender). "
                       "No bloco só solta faíscas (não põe fogo).",
                       "Gunslinger Fire effect: fire damage (base 4.5), sets the target ablaze and stacks Burning for 4 s "
                       "(×Extend). On blocks it only sparks (no fire)."),
    "gun_explosive": ("Munição Explosiva", "Explosive Rounds",
                      "Efeito: a bala estoura num raio de 2,5 blocos (×Expandir) — dano em todos os inimigos (base 5) e um "
                      "empurrão. Não quebra blocos.",
                      "Effect: the bullet bursts in a 2.5 block radius (×Expand) — damage to every enemy (base 5) and a "
                      "shove. Never breaks blocks."),
    "gun_mark": ("Marcar Alvo", "Mark Target",
                 "Efeito: tiro leve (base 1,5) que marca o alvo por 6 s (×Estender) — ele brilha, fica Exposto e as SUAS balas o "
                 "acertam como tiro certeiro (×1,35, soma com o tiro na cabeça).",
                 "Effect: a light shot (base 1.5) that marks the target for 6 s (×Extend) — it glows, becomes Exposed and YOUR "
                 "bullets hit it as sure shots (×1.35, stacks with headshots)."),
    "gun_fan_hammer": ("Leque do Cão", "Fan the Hammer",
                       "Aumento das formas de arma: cada tiro vira um leque de 1 + 2 balas por nível, uma a cada 2 ticks (a mão "
                       "de apoio bate no cão). Cada bala do leque é cópia e paga os efeitos.",
                       "Weapon form augment: every shot becomes a fan of 1 + 2 bullets per level, one every 2 ticks (the off "
                       "hand slaps the hammer). Each fanned bullet is a copy and pays for the effects."),
    "gun_on_reload": ("Ao Recarregar", "On Reload",
                      "Gatilho (no começo da receita): sempre que você recarrega uma arma de mana, a magia deste slot é conjurada "
                      "sozinha (paga Foco e respeita a recarga). A tecla do slot não a conjura.",
                      "Trigger (at the start of the recipe): whenever you reload a mana gun, this slot's spell casts itself "
                      "(pays Focus and respects its cooldown). The slot key does not cast it."),
}
for gid, (pt, en, pt_d, en_d) in GLYPHS.items():
    e("glyph.isekai_mahou." + gid, pt, en)
    e("glyph.isekai_mahou." + gid + ".desc", pt_d, en_d)

# ------------------------------------------------------------------ lendas
e("legend.isekai_mahou.preachers_cross", "Cruz do Pregador", "Preacher's Cross")
e("legend.isekai_mahou.preachers_cross.desc",
  "Homenagem à cruz-metralhadora de um pregador errante: uma rajada dupla de balas explosivas e incendiárias que varre a "
  "frente do Pistoleiro. (Rank III do Caminho do Gatilho.)",
  "A tribute to a wandering preacher's machine-gun cross: a double burst of explosive, incendiary bullets sweeping the "
  "Gunslinger's front. (Trigger Path rank III.)")
e("legend.isekai_mahou.silver_jackal", "Chacal de Prata", "Silver Jackal")
e("legend.isekai_mahou.silver_jackal.desc",
  "Homenagem à pistola gigante de um caçador de monstros imortal: uma bala perfurante amplificada duas vezes que marca e "
  "explode tudo o que estiver na linha. (Rank V do Caminho do Gatilho.)",
  "A tribute to an immortal monster hunter's giant pistol: a twice-amplified piercing round that marks and blows up "
  "everything in its line. (Trigger Path rank V.)")

# ------------------------------------------------------------------ Caminho do Gatilho
RANKS = {1: ("Mão Rápida", "Quick Hand"), 2: ("Caçador de Recompensas", "Bounty Hunter"),
         3: ("Duelista do Meio-Dia", "High Noon Duelist"), 4: ("Olho de Águia", "Eagle Eye"),
         5: ("Lenda do Oeste Arcano", "Legend of the Arcane West")}
for n, (pt, en) in RANKS.items():
    e("path.isekai_mahou.gunslinger.rank.%d" % n, pt, en)
e("path.isekai_mahou.gunslinger.need_class", "Só o Pistoleiro trilha o Caminho do Gatilho", "Only a Gunslinger walks the Trigger Path")
e("path.isekai_mahou.gunslinger.need_previous", "Conclua o rank anterior", "Complete the previous rank")
e("path.isekai_mahou.gunslinger.need_level", "Nível %s", "Level %s")
e("path.isekai_mahou.gunslinger.need_circle", "Círculo %s", "Circle %s")
e("path.isekai_mahou.gunslinger.req.hits", "Acerte %s balas", "Land %s bullets")
e("path.isekai_mahou.gunslinger.req.headshots", "%s tiros na cabeça", "%s headshots")
e("path.isekai_mahou.gunslinger.req.kills", "%s abates com bala", "%s bullet kills")
e("path.isekai_mahou.gunslinger.req.longshots", "%s abates de longe (28+ blocos)", "%s long-range kills (28+ blocks)")
e("path.isekai_mahou.gunslinger.req.reloads", "Recarregue %s vezes", "Reload %s times")
e("path.isekai_mahou.gunslinger.req.duel", "Vença o Forasteiro Espectral ao meio-dia", "Beat the Spectral Outlaw at high noon")
e("path.isekai_mahou.gunslinger.req.duel2", "Vença o Ceifador do Meio-Dia", "Beat the High Noon Reaper")
e("path.isekai_mahou.gunslinger.req.boss", "Derrote %s chefe(s)", "Defeat %s boss(es)")

# ------------------------------------------------------------------ aba do menu B
e("tab.isekai_mahou.gunslinger_path", "Caminho do Gatilho", "Trigger Path")
e("screen.isekai_mahou.gunslinger.title", "Caminho do Gatilho", "Trigger Path")
e("screen.isekai_mahou.gunslinger.subtitle", "Cinco ranks de pólvora e mana", "Five ranks of gunpowder and mana")
e("screen.isekai_mahou.gunslinger.arsenal", "Arsenal de Mana", "Mana Arsenal")
e("screen.isekai_mahou.gunslinger.materialize", "Materializar", "Materialize")
e("screen.isekai_mahou.gunslinger.materialize.tip",
  "Cria a arma com 25%% do seu Foco máximo (uma vez a cada 30 s). Uma de cada tipo no inventário.",
  "Creates the gun with 25%% of your max Focus (once every 30 s). One of each kind in your inventory.")
e("screen.isekai_mahou.gunslinger.unlock_at", "Liberada no rank %s", "Unlocked at rank %s")
e("screen.isekai_mahou.gunslinger.bonus", "Bônus do Pistoleiro", "Gunslinger Bonuses")
e("screen.isekai_mahou.gunslinger.bonus.desc",
  "• Recarga 40%% mais rápida e pela metade do Foco\n• Tiro na cabeça ×1,5 (outras classes ×1,25)\n"
  "• Formas de arma devolvem 20%% do Foco e recarregam 25%% mais rápido\n• +4%% de dano de bala por rank\n"
  "• Traje completo: recarga mais 15%% rápida",
  "• Reload 40%% faster for half the Focus\n• Headshots ×1.5 (other classes ×1.25)\n"
  "• Weapon forms refund 20%% Focus and cool down 25%% faster\n• +4%% bullet damage per rank\n"
  "• Full outfit: reload another 15%% faster")
e("screen.isekai_mahou.gunslinger.other_class", "(você não é Pistoleiro)", "(you are not a Gunslinger)")
e("screen.isekai_mahou.gunslinger.done", "Concluído", "Done")
e("screen.isekai_mahou.gunslinger.next", "Próximo", "Next")
e("screen.isekai_mahou.gunslinger.locked", "Bloqueado", "Locked")
e("screen.isekai_mahou.gunslinger.req_level", "Nível %s · Círculo %s", "Level %s · Circle %s")
e("screen.isekai_mahou.gunslinger.reward", "Recompensa:", "Reward:")
e("screen.isekai_mahou.gunslinger.reward_weapon", "Nova arma de mana: %s", "New mana gun: %s")
e("screen.isekai_mahou.gunslinger.reward_legend", "+ Lenda", "+ Legend")
e("screen.isekai_mahou.gunslinger.reward_legend.tip", "Pergaminho Lendário: %s", "Legendary Scroll: %s")
e("screen.isekai_mahou.gunslinger.reward_xp", "+%s XP", "+%s XP")
e("screen.isekai_mahou.gunslinger.claim", "Concluir rank", "Claim rank")
e("screen.isekai_mahou.gunslinger.duel", "Desafiar ao Meio-Dia", "Challenge at Noon")
e("screen.isekai_mahou.gunslinger.duel_tip1",
  "Entre 10h e 14h do jogo, a céu aberto: o Forasteiro Espectral aparece a 14 blocos. Espere o sino e o SAQUE! — atirar antes é falta.",
  "Between 10:00 and 14:00 in-game, under open sky: the Spectral Outlaw appears 14 blocks away. Wait for the bell and DRAW! — "
  "shooting early is a foul.")
e("screen.isekai_mahou.gunslinger.duel_tip2",
  "Ao meio-dia: o Ceifador do Meio-Dia — mais vida, saque mais rápido, esquivas e o leque do cão. Recarregue quando ele recarregar.",
  "At noon: the High Noon Reaper — more health, a faster draw, dodges and fanning. Reload when he reloads.")

# ------------------------------------------------------------------ HUD, dicas e tooltips
e("hud.isekai_mahou.gunslinger.reloading", "Recarregando...", "Reloading...")
e("hud.isekai_mahou.gunslinger.reload_hint", "Recarregar [%s]", "Reload [%s]")
e("tooltip.isekai_mahou.gunslinger.more", "Segure Shift para detalhes", "Hold Shift for details")
e("tooltip.isekai_mahou.gunslinger.gun.tier", "Grau %s", "Grade %s")
e("tooltip.isekai_mahou.gunslinger.gun.rank_scaling", "Fica mais forte a cada rank do Caminho do Gatilho",
  "Grows stronger with every Trigger Path rank")
e("tooltip.isekai_mahou.gunslinger.gun.drum", "Tambor: %s/%s", "Drum: %s/%s")
e("tooltip.isekai_mahou.gunslinger.gun.stats", "Dano base %s · %s tiros/s · recarga %s s", "Base damage %s · %s shots/s · reload %s s")
e("tooltip.isekai_mahou.gunslinger.gun.auto", "Automática: segure o botão direito", "Automatic: hold right-click")
e("tooltip.isekai_mahou.gunslinger.gun.semi", "Um tiro por clique", "One shot per click")
e("tooltip.isekai_mahou.gunslinger.gun.reload_cost", "Recarga completa: %s%% do Foco máximo (metade para o Pistoleiro)",
  "Full reload: %s%% of max Focus (half for a Gunslinger)")
e("tooltip.isekai_mahou.gunslinger.gun.scope", "Agache para usar a luneta", "Sneak to use the scope")
e("tooltip.isekai_mahou.gunslinger.gun.spells",
  "Na mão: as formas de arma (Bala, Rajada...) gastam 1 cartucho e saem mais fortes. Recarregue com a tecla de trocar de mão.",
  "In hand: weapon forms (Bullet, Burst...) spend 1 round and hit harder. Reload with the swap-hands key.")
e("tooltip.isekai_mahou.gunslinger.outfit.tier", "Grau %s · %s (Círculo %s)", "Grade %s · %s (Circle %s)")
e("tooltip.isekai_mahou.gunslinger.outfit.set", "Traje completo: %s de armadura, +%s Foco, +%s%% velocidade",
  "Full outfit: %s armor, +%s Focus, +%s%% speed")
e("tooltip.isekai_mahou.gunslinger.outfit.set_bonus", "Traje completo do Pistoleiro: recarga 15%% mais rápida",
  "Full Gunslinger outfit: reload 15%% faster")
e("tooltip.isekai_mahou.gunslinger.outfit.next", "Próximo grau: %s (Círculo %s)", "Next grade: %s (Circle %s)")

# ------------------------------------------------------------------ mensagens
M = {
    "already_have": ("Você já tem: %s.", "You already have: %s."),
    "materialized": ("Você materializou com mana: %s!", "You materialized with mana: %s!"),
    "materialize_wait": ("A mana ainda está se assentando: espere %s s.", "Your mana is still settling: wait %s s."),
    "locked": ("Arma liberada no rank %s do Caminho do Gatilho.", "Gun unlocked at Trigger Path rank %s."),
    "no_focus": ("%s insuficiente.", "Not enough %s."),
    "full": ("O tambor já está cheio.", "The drum is already full."),
    "headshot": ("Na cabeça!", "Headshot!"),
    "long_shot": ("Tiro de longe! %s blocos", "Long shot! %s blocks"),
    "noon_empty": ("Nenhum inimigo à vista para as Doze Badaladas.", "No enemy in sight for the Twelve Tolls."),
    "reload_only": ("Esta magia tem o gatilho Ao Recarregar: ela dispara sozinha quando você recarrega.",
                    "This spell has the On Reload trigger: it fires by itself when you reload."),
    "not_ready": ("Ainda não dá para concluir este rank:", "You can't claim this rank yet:"),
    "ready": ("O rank %s (%s) do Caminho do Gatilho pode ser concluído! Menu (B) → Caminho do Gatilho.",
              "Trigger Path rank %s (%s) is ready to claim! Menu (B) → Trigger Path."),
    "rank_up": ("RANK %s · %s", "RANK %s · %s"),
    "rank_chat": ("Caminho do Gatilho — rank %s concluído: %s", "Trigger Path — rank %s claimed: %s"),
    "weapon_unlocked": ("Nova arma de mana: %s! (Materialize outras cópias pelo menu B.)",
                        "New mana gun: %s! (Materialize more from the B menu.)"),
    "outfit": ("Círculo %s: o traje %s é seu!", "Circle %s: the %s outfit is yours!"),
    "outfit.equipped": ("O traje novo foi vestido (as peças antigas foram para o inventário).",
                        "The new outfit was equipped (old pieces went to your inventory)."),
    "outfit.inventory": ("As peças novas foram para o inventário (você estava vestindo outras).",
                         "The new pieces went to your inventory (you were wearing others)."),
    "duel_start": ("O Forasteiro Espectral aceitou o desafio. Ao meio-dia, um de vocês cai.",
                   "The Spectral Outlaw accepts the challenge. At high noon, one of you falls."),
    "duel_start2": ("O Ceifador do Meio-Dia surge da poeira. Ele não erra duas vezes.",
                    "The High Noon Reaper rises from the dust. He never misses twice."),
    "duel_rules": ("Regras: espere o sino e o letreiro SAQUE! — atirar ou conjurar antes é falta. Acertar logo depois do sinal = "
                   "Saque Perfeito (dano ×2).",
                   "Rules: wait for the bell and the DRAW! sign — shooting or casting before it is a foul. Hitting right after "
                   "the signal = Perfect Draw (×2 damage)."),
    "duel_active": ("Você já está num duelo.", "You are already in a duel."),
    "duel_wait": ("Espere %s s para desafiar de novo.", "Wait %s s to challenge again."),
    "duel_noon": ("Duelos só ao MEIO-DIA (entre 10h e 14h do jogo), a céu aberto no Mundo Superior.",
                  "Duels only at HIGH NOON (10:00–14:00 in-game), under open sky in the Overworld."),
    "duel_space": ("Sem espaço para o duelo: procure um campo aberto e plano.", "No room for a duel: find open, flat ground."),
    "duel_not_needed": ("Nenhum duelo pendente no Caminho do Gatilho agora.", "No duel pending on the Trigger Path right now."),
    "duel_foul": ("Falta! Você sacou antes do sinal — o forasteiro some, desapontado. Tente de novo mais tarde.",
                  "Foul! You drew before the signal — the outlaw vanishes, disappointed. Try again later."),
    "duel_win": ("Vitória no duelo! A lenda do gatilho cresce.", "Duel won! The legend of the trigger grows."),
    "duel_lose.gone": ("O duelo acabou: você deixou o campo (ou caiu). O forasteiro tira o chapéu e some.",
                       "The duel is over: you left the field (or fell). The outlaw tips his hat and vanishes."),
    "duel_lose.timeout": ("O sol passou do meio-dia... o forasteiro some no vento. Duelo perdido.",
                          "The sun has passed noon... the outlaw fades into the wind. Duel lost."),
    "perfect_draw": ("SAQUE PERFEITO! Dano ×2", "PERFECT DRAW! ×2 damage"),
}
for k, (pt, en) in M.items():
    e("message.isekai_mahou.gunslinger." + k, pt, en)

# ------------------------------------------------------------------ letreiros, avisos e comandos
e("title.isekai_mahou.gunslinger.duel", "DUELO AO MEIO-DIA", "HIGH NOON DUEL")
e("title.isekai_mahou.gunslinger.duel.sub", "Mão longe do gatilho até o sinal...", "Hand off the trigger until the signal...")
e("title.isekai_mahou.gunslinger.draw", "SAQUE!", "DRAW!")
e("title.isekai_mahou.gunslinger.foul", "FALTA!", "FOUL!")
e("title.isekai_mahou.gunslinger.foul.sub", "Você sacou antes do sinal", "You drew before the signal")
e("title.isekai_mahou.gunslinger.win", "VITÓRIA", "VICTORY")
e("title.isekai_mahou.gunslinger.win.sub", "O Forasteiro Espectral tira o chapéu para você", "The Spectral Outlaw tips his hat to you")
e("title.isekai_mahou.gunslinger.win.sub2", "O Ceifador do Meio-Dia caiu na poeira", "The High Noon Reaper bites the dust")
e("title.isekai_mahou.gunslinger.noon", "DOZE BADALADAS", "TWELVE TOLLS")
e("title.isekai_mahou.gunslinger.noon.sub", "%s alvos na mira", "%s targets marked")
e("toast.isekai_mahou.gunslinger.rank", "Caminho do Gatilho · Rank %s", "Trigger Path · Rank %s")
e("toast.isekai_mahou.gunslinger.outfit", "Traje: %s", "Outfit: %s")
e("toast.isekai_mahou.gunslinger.outfit.sub", "Círculo %s alcançado", "Circle %s reached")
e("command.isekai_mahou.gunslinger.rank", "Caminho do Gatilho concedido até o rank %s.", "Trigger Path granted up to rank %s.")
e("command.isekai_mahou.gunslinger.no_gun", "Segure uma arma de mana na mão principal.", "Hold a mana gun in your main hand.")
e("command.isekai_mahou.gunslinger.reloaded", "Tambor cheio.", "Drum full.")

# ------------------------------------------------------------------ v1.0 — técnicas de anime (homenagem Gun Gale Online etc.)
TECH_GLYPHS = {
    "photon_sword": ("Espada de Fótons", "Photon Sword",
                     "Forma de origem (homenagem à lâmina de luz do espadachim de Gun Gale): cria a lâmina de energia, que corta em "
                     "arco à sua frente (4 blocos ×Expandir, 140°) — os efeitos saem com ×1,25 — e CORTA as balas, flechas e "
                     "magias no caminho, rebatendo para quem atirou; por 0,4 s depois de cada corte, o que vier em você é "
                     "rebatido. Dividir = cortes a mais (até 4). Libera: rank III do Caminho do Gatilho.",
                     "Origin form (homage to the Gun Gale swordsman's light blade): the energy blade slashes in an arc in front "
                     "of you (4 blocks ×Expand, 140°) — effects at ×1.25 — and CUTS bullets, arrows and spells in its path, "
                     "deflecting them back at the shooter; for 0.4 s after each slash, whatever comes at you is deflected. "
                     "Split = extra slashes (up to 4). Unlock: Trigger Path rank III."),
    "bullet_line": ("Linha de Bala", "Bullet Line",
                    "Forma de origem (previsão) que cria as linhas de bala: por 10 s (×Estender) você vê LINHAS VERMELHAS de quem mira em você e dos "
                    "projéteis vindo na sua direção (só você vê) e ganha 3 esquivas (+1 por Dividir, até 6) — o golpe previsto "
                    "não acerta e você dá um passo de lado. Com efeitos na magia, cada esquiva revida um tiro (80%). Funciona "
                    "sem efeito. Libera: rank II.",
                    "Origin form (prediction): for 10 s (×Extend) you see RED LINES from whoever aims at you and from projectiles "
                    "heading your way (only you see them) and gain 3 dodges (+1 per Split, up to 6) — the predicted hit misses "
                    "and you sidestep. With effects in the spell, each dodge fires a counter-shot (80%). Works with no effect. "
                    "Unlock: rank II."),
    "aim_circle": ("Círculo de Mira", "Bullet Circle",
                   "Forma de origem (tiro de precisão) que cria o Círculo de Mira: o círculo verde aparece na sua mira (só você vê) e FECHA enquanto você "
                   "fica parado (agachado fecha mais rápido); andar abre e o coração o faz pulsar. Fechou (≈1,2 s): o tiro "
                   "sai sozinho como CRÍTICO (×2,5 e conta como tiro na cabeça). Se não fechar em 2 s, sai como estiver — mais "
                   "fraco e desviado. Tiro instantâneo, 128 blocos. Libera: rank I.",
                   "Origin form (precision shot): the green circle appears on your sight (only you see it) and CLOSES while you "
                   "stand still (sneaking closes faster); moving widens it and your heartbeat makes it pulse. Once closed "
                   "(≈1.2 s) the shot fires on its own as a CRITICAL (×2.5, counts as a headshot). If it doesn't close in 2 s, "
                   "it fires as it is — weaker and off-target. Instant shot, 128 blocks. Unlock: rank I."),
    "optical_camo": ("Camuflagem Óptica", "Optical Camouflage",
                     "Forma de origem (homenagem ao manto do atirador da caveira): o manto liga e você some — Invisível por 8 s "
                     "(×Estender), as criaturas perdem você de vista e não o escolhem como alvo (só coladas, a 3 blocos). O "
                     "PRIMEIRO ataque é EMBOSCADA (×1,5) e desliga o manto. Funciona sem efeito; efeitos benéficos vão em você. "
                     "Libera: rank IV.",
                     "Origin form (homage to the skull sniper's cloak): the cloak switches on and you vanish — Invisible for 8 s "
                     "(×Extend), creatures lose sight of you and won't pick you as a target (only point-blank, 3 blocks). Your "
                     "FIRST attack is an AMBUSH (×1.5) and turns the cloak off. Works with no effect; beneficial effects go on "
                     "you. Unlock: rank IV."),
    "black_star": ("Estrela Negra", "Black Star",
                   "Forma de origem (homenagem à pistola do atirador da caveira): você faz o sinal da cruz e dispara a bala "
                   "negra — instantânea, 96 blocos, ×1,6 (cabeça conta). EXECUÇÃO: criatura comum que fique com 30% da vida ou "
                   "menos cai na hora. Jogadores e chefes não são executados: ficam Expostos III e com Murchar. Saindo da "
                   "Camuflagem Óptica, o tiro é silencioso e soma a emboscada. Libera: rank IV + 10 emboscadas camuflado.",
                   "Origin form (homage to the skull sniper's pistol): you make the sign of the cross and fire the black bullet "
                   "— instant, 96 blocks, ×1.6 (headshots count). EXECUTION: a common creature left at 30% health or less "
                   "falls at once. Players and bosses are never executed: they get Exposed III and Wither. Out of Optical "
                   "Camouflage the shot is silent and the ambush stacks. Unlock: rank IV + 10 camouflaged ambushes."),
    "anti_materiel": ("Atirador de Elite — Fuzil Antimaterial", "Sharpshooter — Anti-Materiel Rifle",
                      "Forma de origem (homenagem à atiradora de gelo e seu fuzil gigante): mira 1,2 s e dispara um tiro "
                      "instantâneo de até 192 blocos (×Acelerar) que atravessa 3 alvos (+Perfurar) e quebra a defesa "
                      "(Exposto II). AGACHADO (no bipé): ×2,4, sem desvio. Em pé: ×1,4, desvio e o coice joga você para trás. "
                      "Libera: rank IV.",
                      "Origin form (homage to the ice sniper and her giant rifle): aim for 1.2 s and fire an instant shot up to "
                      "192 blocks (×Accelerate) that passes through 3 targets (+Pierce) and breaks defenses (Exposed II). "
                      "SNEAKING (on the bipod): ×2.4, no spread. Standing: ×1.4, spread and the recoil throws you back. "
                      "Unlock: rank IV."),
    "stellar_burst": ("Rajada Estelar", "Stellar Burst",
                      "Forma G (homenagem ao combo de 16 golpes do espadachim negro): trava no inimigo à sua frente (10 blocos) "
                      "e dispara 16 golpes em 1,6 s, ALTERNANDO corte de fótons e tiro, girando em volta dele a cada 4 golpes. "
                      "Cada golpe aplica os efeitos (65%); o 16º sai com ×2. Se o alvo cair, pula para o próximo. "
                      "Resistência IV durante o combo. Círculo 8. Libera: rank V + 150 acertos de fótons.",
                      "G form (homage to the black swordsman's 16-hit combo): locks onto the enemy in front of you (10 blocks) "
                      "and unleashes 16 hits in 1.6 s, ALTERNATING photon slashes and gunshots, circling around it every 4 "
                      "hits. Each hit applies the effects (65%); the 16th hits ×2. If the target falls, it jumps to the next. "
                      "Resistance IV during the combo. Circle 8. Unlock: rank V + 150 photon hits."),
    "spirit_gun": ("Dedo Espiritual", "Spirit Finger",
                   "Forma de origem (homenagem ao tiro espiritual do detetive de outro mundo): sem arma — você concentra o "
                   "espírito na ponta do dedo (0,7 s) e dispara uma esfera azul que atravessa 1 alvo e EXPLODE (raio 2,5, "
                   "×Expandir). Mais Foco = mais forte (×1,3 vazio até ×2,0 cheio); gasta mais 10% do Foco atual. Libera: "
                   "rank II.",
                   "Origin form (homage to the spirit detective's finger shot): no gun needed — you focus your spirit on your "
                   "fingertip (0.7 s) and fire a blue orb that passes through 1 target and EXPLODES (radius 2.5, ×Expand). "
                   "More Focus = stronger (×1.3 empty up to ×2.0 full); spends another 10% of your current Focus. Unlock: "
                   "rank II."),
    "two_hands": ("Duas Mãos", "Two Hands",
                  "Forma de origem (homenagem à pistoleira das duas pistolas): você gira e dispara em TODAS as direções — 12 "
                  "tiros (+4 por Dividir, até 24) em 1,2 s, um de cada mão; cada tiro procura o inimigo mais próximo do seu "
                  "setor. Cada bala é uma cópia (55% do poder). Libera: rank III.",
                  "Origin form (homage to the two-pistol gunwoman): you spin and shoot in EVERY direction — 12 shots (+4 per "
                  "Split, up to 24) in 1.2 s, one from each hand; each shot seeks the nearest enemy in its sector. Each "
                  "bullet is a copy (55% power). Unlock: rank III."),
}
for gid, (pt, en, pt_d, en_d) in TECH_GLYPHS.items():
    e("glyph.isekai_mahou." + gid, pt, en)
    e("glyph.isekai_mahou." + gid + ".desc", pt_d, en_d)

e("entity.isekai_mahou.photon_slash", "Corte de Fótons", "Photon Slash")

e("legend.isekai_mahou.gunslinger_starburst", "Rajada Estelar", "Stellar Burst")
e("legend.isekai_mahou.gunslinger_starburst.desc",
  "A forma G com crítico e dano arcano amplificado: dezesseis golpes de luz e chumbo.",
  "The G form with critical and amplified arcane damage: sixteen strikes of light and lead.")
e("legend.isekai_mahou.gunslinger_black_star", "Execução da Estrela Negra", "Black Star Execution")
e("legend.isekai_mahou.gunslinger_black_star.desc",
  "A bala negra marca o alvo e o atinge com dano arcano amplificado — e executa quem ficar fraco.",
  "The black bullet marks the target and hits it with amplified arcane damage — and executes whoever is left weak.")
e("legend.isekai_mahou.gunslinger_moon_goddess", "Tiro da Deusa da Lua Gelada", "Shot of the Frozen Moon Goddess")
e("legend.isekai_mahou.gunslinger_moon_goddess.desc",
  "O fuzil antimaterial atravessa a fila, congela e explode cada alvo.",
  "The anti-materiel rifle pierces the line, freezing and blowing up every target.")

C = "callout.isekai_mahou.gunslinger."
e(C + "photon_sword", "ESPADA DE FÓTONS!", "PHOTON SWORD!")
e(C + "bullet_line", "LINHA DE BALA", "BULLET LINE")
e(C + "bullet_line.sub", "Você vê os tiros antes de virem · %s esquivas", "You see the shots before they come · %s dodges")
e(C + "aim_circle", "CÍRCULO DE MIRA — fique parado...", "BULLET CIRCLE — hold still...")
e(C + "optical_camo", "CAMUFLAGEM ÓPTICA", "OPTICAL CAMOUFLAGE")
e(C + "optical_camo.sub", "O manto ligou: ninguém te vê", "The cloak is on: no one sees you")
e(C + "black_star", "ESTRELA NEGRA", "BLACK STAR")
e(C + "black_star.sub", "O sinal da cruz... e a bala negra", "The sign of the cross... and the black bullet")
e(C + "anti_materiel", "FUZIL ANTIMATERIAL — APOIADO!", "ANTI-MATERIEL RIFLE — BRACED!")
e(C + "anti_materiel.standing", "FUZIL ANTIMATERIAL (em pé: agache para firmar)", "ANTI-MATERIEL RIFLE (standing: sneak to brace)")
e(C + "stellar_burst", "RAJADA ESTELAR", "STELLAR BURST")
e(C + "stellar_burst.sub", "Dezesseis golpes — luz e chumbo", "Sixteen strikes — light and lead")
e(C + "spirit_gun", "DEDO ESPIRITUAL!", "SPIRIT FINGER!")
e(C + "two_hands", "DUAS MÃOS!", "TWO HANDS!")

M = "message.isekai_mahou.gunslinger."
e(M + "lines_end", "As linhas de bala se apagaram.", "The bullet lines faded.")
e(M + "dodge", "Esquiva! (%s restantes)", "Dodge! (%s left)")
e(M + "deflect", "Cortado e rebatido!", "Cut and deflected!")
e(M + "aim_perfect", "CÍRCULO FECHADO — CRÍTICO!", "CIRCLE CLOSED — CRITICAL!")
e(M + "aim_partial", "O círculo não fechou (%s%%): tiro desviado", "The circle didn't close (%s%%): shot off-target")
e(M + "execute", "EXECUÇÃO!", "EXECUTION!")
e(M + "burst_empty", "Nenhum inimigo à frente para a Rajada Estelar.", "No enemy in front for the Stellar Burst.")
e(M + "burst_count", "✦ RAJADA ESTELAR %s/%s", "✦ STELLAR BURST %s/%s")
e(M + "ambush", "EMBOSCADA! ×1,5", "AMBUSH! ×1.5")
e(M + "tech", "Nova técnica de pistoleiro: %s!", "New gunslinger technique: %s!")
e("banner.isekai_mahou.gunslinger.tech", "TÉCNICA — %s", "TECHNIQUE — %s")
e("toast.isekai_mahou.gunslinger.tech", "Nova técnica do Pistoleiro!", "New Gunslinger technique!")
e("screen.isekai_mahou.gunslinger.tech.title", "Técnica: %s", "Technique: %s")
e("screen.isekai_mahou.gunslinger.tech.rank", "Rank %s do Caminho do Gatilho", "Trigger Path rank %s")
e("screen.isekai_mahou.gunslinger.tech.camo_shots", "%s emboscadas saindo da Camuflagem Óptica", "%s ambushes out of Optical Camouflage")
e("screen.isekai_mahou.gunslinger.tech.photon_hits", "%s acertos com a Espada de Fótons", "%s Photon Sword hits")



def main():
    pt, en = {}, {}
    for k, p, n in ENTRIES:
        if k in pt:
            raise RuntimeError("chave duplicada: " + k)
        pt[k], en[k] = p, n
    os.makedirs(LANG, exist_ok=True)
    for name, data in (("pt_br", pt), ("en_us", en)):
        with open(os.path.join(LANG, name + ".json"), "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write("\n")
    print("  traducoes: %d chaves (pt_br + en_us)" % len(pt))


if __name__ == "__main__":
    main()
