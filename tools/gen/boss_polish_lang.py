"""v1.0 (boss_polish): falas reativas dos chefes — provocação ao derrubar um jogador ("taunt"), último fôlego
aos 15% de vida ("last_stand") e últimas palavras ("last_words") — em pt_br e en_us.

Uso:  py -I tools/gen/boss_polish_lang.py
ACRESCENTA as chaves no fim dos arquivos de tradução dos módulos de chefe (sem reescrever o resto do arquivo);
chave que já existe fica como está, então pode rodar de novo à vontade.
"""
import json
import os

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
LANG = os.path.join(ROOT, 'src', 'main', 'lang')

# módulo -> { chave: (pt_br, en_us) }
LINES = {
    'bosses_a': {
        'boss.isekai_mahou.fallen': ('Derrotado', 'Defeated'),
        'command.isekai_mahou.bosspolish.none': ('Nenhum chefe a até 48 blocos.', 'No boss within 48 blocks.'),
        'command.isekai_mahou.bosspolish.survival': ('Use no modo sobrevivência: o chefe precisa derrubar você de verdade.',
                                                     'Use it in survival mode: the boss has to really take you down.'),
        'command.isekai_mahou.bosspolish.done': ('Feito em %s.', 'Done on %s.'),
        'boss.isekai_mahou.ignis.taunt': ('Cinzas. É só isso que vocês são.', 'Ashes. That is all you are.'),
        'boss.isekai_mahou.ignis.last_stand': ('A fornalha... ainda... não apagou!', 'The furnace... has not... gone out yet!'),
        'boss.isekai_mahou.tide_guardian.taunt': ('Mais um para o fundo do mar.', 'One more for the bottom of the sea.'),
        'boss.isekai_mahou.tide_guardian.last_stand': ('A maré... recua... mas sempre volta!', 'The tide... recedes... but it always returns!'),
        'boss.isekai_mahou.primordial_golem.taunt': ('...Pó... volta... ao pó.', '...Dust... returns... to dust.'),
        'boss.isekai_mahou.primordial_golem.last_stand': ('...Rachado... mas... de pé.', '...Cracked... but... standing.'),
        'boss.isekai_mahou.zephyr.taunt': ('Hahaha! Caiu feito folha seca!', 'Hahaha! Fell like a dry leaf!'),
        'boss.isekai_mahou.zephyr.last_stand': ('Tch... o vento ainda sopra, pequeno!', 'Tch... the wind still blows, little one!'),
    },
    'bosses_b': {
        'announce.isekai_mahou.abyss_sovereign.taunt': ('Mais uma vela apagada.', 'One more candle snuffed out.'),
        'announce.isekai_mahou.abyss_sovereign.last_stand': ('O Abismo... não se curva... a ninguém!', 'The Abyss... bows... to no one!'),
        'announce.isekai_mahou.abyss_sovereign.last_words': ('A escuridão... só... adormece...', 'The darkness... only... sleeps...'),
        'announce.isekai_mahou.seraph.taunt': ('Julgado. E achado em falta.', 'Judged. And found wanting.'),
        'announce.isekai_mahou.seraph.last_stand': ('Minhas asas pesam... mas a luz não cede!', 'My wings grow heavy... but the light does not yield!'),
        'announce.isekai_mahou.seraph.last_words': ('Então... vocês são dignos... da escada do céu.', 'So... you are worthy... of the stairway to heaven.'),
        'announce.isekai_mahou.twilight_herald.taunt': ('Mais um invocado que não verá a aurora.', 'One more summoned soul who will not see the dawn.'),
        'announce.isekai_mahou.twilight_herald.last_stand': ('O crepúsculo... ainda não... acabou!', 'The twilight... is not... over yet!'),
    },
    'bosses_c': {
        'announce.isekai_mahou.renegade_archmage.taunt': ('Reprovado. Próximo aprendiz!', 'Failed. Next apprentice!'),
        'announce.isekai_mahou.renegade_archmage.last_stand': ('Não... eu ainda tenho um último feitiço!', 'No... I still have one last spell!'),
        'announce.isekai_mahou.corrupted_witch.taunt': ('Hihihi! Mais um para o caldeirão!', 'Heehee! One more for the cauldron!'),
        'announce.isekai_mahou.corrupted_witch.last_stand': ('Minhas poções... onde estão minhas poções?!', 'My potions... where are my potions?!'),
        'announce.isekai_mahou.imprisoned_patron.taunt': ('Sua alma serve. Obrigado, mortal.', 'Your soul will do. Thank you, mortal.'),
        'announce.isekai_mahou.imprisoned_patron.last_stand': ('Não... de volta ao selo NÃO!', 'No... NOT back into the seal!'),
        'announce.isekai_mahou.corrupted_ent.taunt': ('Adubo... para as... raízes...', 'Fertilizer... for the... roots...'),
        'announce.isekai_mahou.corrupted_ent.last_stand': ('A podridão... seca... HRRRM!', 'The rot... withers... HRRRM!'),
        'announce.isekai_mahou.lich.taunt': ('Levante-se. Agora você serve a mim.', 'Rise. You serve me now.'),
        'announce.isekai_mahou.lich.last_stand': ('Meus ossos racham... mas a morte é minha aliada!', 'My bones crack... but death is my ally!'),
    },
    'bosses_d': {
        'announce.isekai_mahou.trial_avatar.taunt': ('Ainda não é digno. Levante-se e tente de novo.', 'Not worthy yet. Stand up and try again.'),
        'announce.isekai_mahou.trial_avatar.last_stand': ('Quase... mostre-me o golpe final!', 'Almost... show me the final blow!'),
    },
    'bosses_f': {
        'announce.isekai_mahou.wyvern_king.taunt': ('O céu não tem lugar para vermes!', 'The sky has no room for worms!'),
        'announce.isekai_mahou.wyvern_king.last_stand': ('Um rei... não cai... do próprio céu!', 'A king... does not fall... from his own sky!'),
        'announce.isekai_mahou.swamp_hydra.taunt': ('Sssss... engolido inteiro...', 'Sssss... swallowed whole...'),
        'announce.isekai_mahou.swamp_hydra.last_stand': ('As bocas... se calam... MAS AINDA MORDEM!', 'The mouths... go quiet... BUT THEY STILL BITE!'),
        'announce.isekai_mahou.desert_basilisk.taunt': ('Mais uma estátua para o meu túmulo.', 'One more statue for my tomb.'),
        'announce.isekai_mahou.desert_basilisk.last_stand': ('Meus olhos... ardem... OLHEM!', 'My eyes... burn... LOOK!'),
        'announce.isekai_mahou.ruin_colossus.taunt': ('...INTRUSO... ELIMINADO...', '...INTRUDER... ELIMINATED...'),
        'announce.isekai_mahou.ruin_colossus.last_stand': ('...DANO... CRÍTICO... ÚLTIMO... PROTOCOLO...', '...CRITICAL... DAMAGE... FINAL... PROTOCOL...'),
    },
    'bosses_g': {
        'announce.isekai_mahou.manticore.taunt': ('Presa fácil. Quem é o próximo?', 'Easy prey. Who is next?'),
        'announce.isekai_mahou.manticore.last_stand': ('GRRR... uma fera ferida morde mais forte!', 'GRRR... a wounded beast bites harder!'),
        'announce.isekai_mahou.mimic_king.taunt': ('Hehehe... mais um para o cofre!', 'Hehehe... one more for the vault!'),
        'announce.isekai_mahou.mimic_king.last_stand': ('Meu ouro... NINGUÉM leva o meu ouro!', 'My gold... NO ONE takes my gold!'),
        'announce.isekai_mahou.false_hydra.taunt': ('Shhh... ninguém vai lembrar de você.', 'Shhh... no one will remember you.'),
        'announce.isekai_mahou.false_hydra.last_stand': ('Minha canção... falha... NÃO ME ESQUEÇAM!', 'My song... falters... DO NOT FORGET ME!'),
    },
    'espada': {
        'quote.isekai_mahou.ulquiorra.taunt': ('Como esperado. Lixo.', 'As expected. Trash.'),
        'quote.isekai_mahou.barragan.taunt': ('Pó. Como tudo diante do rei.', 'Dust. Like everything before the king.'),
        'quote.isekai_mahou.grimmjow.taunt': ('Já era? Levanta, eu ainda não terminei!', "Done already? Get up, I'm not finished!"),
    },
}


def append(path, entries):
    """Insere as chaves novas antes da chave final '}' mantendo o fim de linha e o recuo do arquivo."""
    raw = open(path, encoding='utf-8', newline='').read()
    have = json.loads(raw)
    new = [(k, v) for k, v in entries if k not in have]
    if not new:
        return 0
    eol = '\r\n' if '\r\n' in raw else '\n'
    body = raw.rstrip()
    assert body.endswith('}'), path
    body = body[:-1].rstrip()
    text = (',' + eol).join('  %s: %s' % (json.dumps(k, ensure_ascii=False), json.dumps(v, ensure_ascii=False)) for k, v in new)
    out = body + (',' + eol if have else eol) + text + eol + '}' + eol
    json.loads(out)  # valida antes de gravar
    open(path, 'w', encoding='utf-8', newline='').write(out)
    return len(new)


def main():
    total = 0
    for module, lines in LINES.items():
        for idx, lang in enumerate(('pt_br', 'en_us')):
            path = os.path.join(LANG, module, lang + '.json')
            total += append(path, [(k, v[idx]) for k, v in lines.items()])
    print('boss_polish_lang: %d chaves acrescentadas' % total)


if __name__ == '__main__':
    main()
