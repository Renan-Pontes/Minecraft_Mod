"""v1.0 (boss_polish): deixa as animações de ATAQUE e de MORTE dos chefes mais marcantes.

Uso:  py -I tools/gen/boss_polish.py            (aplica)
      py -I tools/gen/boss_polish.py --check    (só mostra o que mudaria)

NÃO gera os arquivos do zero: abre os `.animation.json` que os geradores de cada módulo (tools/gen/bosses_*.py)
escreveram e retoca só duas coisas, sem mexer no TEMPO dos golpes (o servidor aplica o dano em ticks fixos):

1. Ataques ("impactify"): acha o trecho mais rápido da animação (o golpe) e dá peso a ele —
   a preparação recua um pouco mais (antecipação), o membro ACELERA até o impacto em vez de ir em linha reta,
   passa do ponto e volta (overshoot) e o corpo inteiro "amassa e estica" de leve (escala do osso raiz).
2. Morte ("dramatize"): antes da queda que já existia entram três batidas — o golpe (tranco para trás),
   o chefe se dobra e treme, e um último rugido (a pose do próprio rugido dele) — e só então ele cai.

Cada animação retocada ganha a chave "polish": 1 (o GeckoLib ignora), então rodar de novo não empilha o efeito.
Se um gerador de módulo recriar o arquivo, rode este script outra vez.
"""
import json
import os
import re
import sys

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..'))
ANIMS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'isekai_mahou', 'animations', 'entity')
GEO = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'isekai_mahou', 'geo', 'entity')

# Animações que NÃO são golpes (estados, transformações, viagens): nunca passam pelo "impactify".
NOT_ATTACK = {
    'idle', 'walk', 'move', 'fly', 'float', 'swim', 'hover', 'rest', 'sleep', 'tired', 'exhausted', 'stunned', 'exposed',
    'intro', 'phase', 'death', 'collapse', 'reform', 'spawn', 'awaken', 'emerge', 'burrow', 'burrow_move', 'submerge',
    'hidden', 'buried', 'kneel', 'teleport', 'shift', 'blink', 'drink', 'shield', 'shield_spin', 'block', 'takeoff',
    'core_closed', 'core_open', 'core_cracked', 'singing', 'sing', 'chew', 'maw', 'gulp', 'choke', 'roar', 'wail', 'scream',
    'pray', 'channel', 'domain', 'summon',
    # golpes sustentados/corridas: não têm UM instante de impacto para marcar
    'charge', 'flame_charge', 'dash', 'barrage', 'beams', 'beam', 'breath', 'wind_curtain', 'vacuum', 'rain', 'ember_rain',
}

# Chefe -> como dramatizar a morte. torso/head/arms: ossos que levam o tranco; sway: ossos que tremem (asas, cauda,
# pescoços); heavy: gigantes de pedra (tranco menor, mais lento); length: nova duração da animação em segundos
# (None = mantém). Chefes cujo corpo carrega as pernas (feras) não têm "torso": só pescoço/cabeça reagem.
DEATHS = {
    # bosses_a — Guardiões (3,0 s; o corpo some nos 14 ticks seguintes)
    'ignis': dict(torso='chest', head='head', jaw='jaw', arms=('arm_left', 'arm_right')),
    'tide_guardian': dict(torso='torso', head='head', arms=('arm_left', 'arm_right'), sway=('tail2', 'tail3', 'tail4')),
    'primordial_golem': dict(torso='torso', head='head', arms=('arm_left', 'arm_right'), heavy=True),
    'zephyr': dict(torso='torso', head='head', arms=('arm_left', 'arm_right'), sway=('wing_left', 'wing_right', 'tail2')),
    # bosses_b (4,0 s). O Arauto tem a cena final própria (7 s) e fica como está.
    'abyss_sovereign': dict(torso='body', head='head', arms=('arm_l', 'arm_r'), sway=('cape',)),
    'seraph': dict(torso='body', head='head', arms=('arm_l', 'arm_r'), sway=('wing_l1', 'wing_r1', 'wing_l2', 'wing_r2')),
    # bosses_c — minichefes de classe (todas passam a 2,5 s; a morte no servidor dura 64 ticks)
    'renegade_archmage': dict(torso='body', head='head', arms=('left_arm', 'right_arm'), sway=('cape',), length=2.5),
    'corrupted_witch': dict(torso='body', head='head', arms=('left_arm', 'right_arm'), length=2.5),
    'imprisoned_patron': dict(torso='body', head='head', arms=('left_arm', 'right_arm'),
                              sway=('left_wing', 'right_wing', 'tail'), length=2.5),
    'corrupted_ent': dict(torso='trunk_upper', head='crown', arms=('left_arm', 'right_arm'), heavy=True, length=2.5),
    'lich': dict(torso='body', head='head', jaw='jaw', arms=('left_arm', 'right_arm'), length=2.5),
    # bosses_f — chefes do mundo (2,5 s; Colosso 3,5 s)
    'wyvern_king': dict(head='neck', jaw='jaw', sway=('wing_l', 'wing_r', 'tail2', 'tail3')),
    'swamp_hydra': dict(sway=('neck_0', 'neck_1', 'neck_2', 'neck_3', 'neck_4', 'tail2')),
    'desert_basilisk': dict(head='neck', jaw='jaw', sway=('tail2', 'tail3')),
    'ruin_colossus': dict(torso='body', head='head', arms=('arm_l', 'arm_r'), heavy=True),
    # bosses_g
    'manticore': dict(head='head', jaw='jaw', sway=('wing_left', 'wing_right', 'tail2', 'tail3')),
    'mimic_king': dict(torso='body', sway=('crown',)),
    'false_hydra': dict(jaw='jaw', sway=('stump_0', 'stump_1', 'stump_2', 'stump_3', 'stump_4')),
}

# Arquivos cujos ataques ganham peso (os de DEATHS + o Arauto, o Avatar e a Sentinela).
IMPACT_FILES = sorted(set(DEATHS) | {'twilight_herald', 'trial_avatar', 'trial_sentinel'})

# De qual animação do próprio chefe sai a pose do "último rugido" (a primeira que existir).
ROAR_SOURCES = ('roar', 'wail', 'scream', 'phase')


# ------------------------------------------------------------------ utilidades de keyframe

def tkey(t):
    """Tempo -> chave de texto curta ("0.0", "0.35", "1.275")."""
    s = ('%.4f' % t).rstrip('0')
    return s + '0' if s.endswith('.') else s


def keys_of(channel):
    """Canal {tempo: [x,y,z]} -> lista ordenada [(t, [x,y,z])] (cópias)."""
    return sorted((float(t), list(v)) for t, v in channel.items())


def to_channel(keys):
    """Lista [(t, vec)] -> canal ordenado por tempo (o GeckoLib lê na ordem do arquivo). Tempos iguais: vale o último."""
    out = {}
    for t, v in sorted(keys, key=lambda kv: kv[0]):
        out[tkey(max(0.0, t))] = [round(x, 3) for x in v]
    return out


def sample(keys, t):
    """Valor do canal no tempo t (interpolação linear, segura fora das pontas)."""
    if t <= keys[0][0]:
        return list(keys[0][1])
    for (t0, v0), (t1, v1) in zip(keys, keys[1:]):
        if t <= t1:
            k = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [a + (b - a) * k for a, b in zip(v0, v1)]
    return list(keys[-1][1])


def clamp(x, lo, hi):
    return max(lo, min(hi, x))


def short(name):
    return name.rsplit('.', 1)[-1]


# ------------------------------------------------------------------ 1. ataques

def find_strike(anim):
    """O golpe = o trecho de rotação mais rápido (>= 25 graus em até 0,5 s). Devolve (t_preparo, t_impacto) ou None."""
    best = None
    length = float(anim.get('animation_length', 1e9))
    for chans in anim.get('bones', {}).values():
        rot = chans.get('rotation')
        if not isinstance(rot, dict):
            continue
        ks = keys_of(rot)
        for (t0, v0), (t1, v1) in zip(ks, ks[1:]):
            dt = t1 - t0
            if dt < 0.04 or dt > 0.5 or t1 >= length - 0.02:   # o último trecho é a volta ao repouso, não o golpe
                continue
            delta = max(abs(a - b) for a, b in zip(v0, v1))
            if delta < 25:
                continue
            speed = delta / dt
            if best is None or speed > best[0]:
                best = (speed, t0, t1)
    return (best[1], best[2]) if best else None


def impactify(anim):
    """Dá peso ao golpe. Não muda o tempo de nenhum keyframe existente. True se mexeu."""
    strike = find_strike(anim)
    if strike is None:
        return False
    tw, ts = strike
    dt = ts - tw
    length = float(anim.get('animation_length', ts + 0.5))
    bones = anim['bones']
    for name, chans in bones.items():
        rot = chans.get('rotation')
        if not isinstance(rot, dict) or name == 'root':
            continue
        ks = keys_of(rot)
        times = [t for t, _ in ks]
        if not any(abs(t - tw) < 1e-6 for t in times) or not any(abs(t - ts) < 1e-6 for t in times):
            continue
        iw = next(i for i, t in enumerate(times) if abs(t - tw) < 1e-6)
        is_ = next(i for i, t in enumerate(times) if abs(t - ts) < 1e-6)
        vw, vs = ks[iw][1], ks[is_][1]
        swing = [b - a for a, b in zip(vw, vs)]
        if max(abs(x) for x in swing) < 10:
            continue
        extra = []
        # Antecipação: a preparação recua mais 12% (só se existe preparação de verdade, não a pose parada)
        if tw > 0.05:
            ks[iw] = (tw, [a - clamp(s * 0.12, -12, 12) for a, s in zip(vw, swing)])
            vw = ks[iw][1]
        # Aceleração: no meio do caminho o membro só andou 22% — o resto vem de uma vez
        if dt >= 0.1:
            extra.append((tw + dt * 0.55, [a + (b - a) * 0.22 for a, b in zip(vw, vs)]))
        # Overshoot: o impacto passa do ponto e volta para a pose original logo depois
        nxt = ks[is_ + 1][0] if is_ + 1 < len(ks) else length
        if nxt - ts >= 0.12:
            over = [b + clamp(s * 0.14, -14, 14) for b, s in zip(vs, swing)]
            ks[is_] = (ts, over)
            extra.append((ts + min(0.1, (nxt - ts) * 0.5), list(vs)))
        chans['rotation'] = to_channel(ks + extra)
    # Corpo inteiro: amassa na preparação, estica no impacto, assenta (só a ESCALA da raiz — nenhuma animação de
    # base usa esse canal, então não briga com andar/flutuar)
    root = bones.setdefault('root', {})
    if 'scale' not in root:
        keys = [(0.0, [1, 1, 1])]
        if tw > 0.08:
            keys.append((tw, [1.035, 0.955, 1.035]))
        keys.append((ts, [0.97, 1.04, 0.97]))
        settle = min(length, ts + 0.12)
        if settle > ts + 0.03:
            keys.append((settle, [1.02, 0.98, 1.02]))
        end = min(length, ts + 0.32)
        if end > settle + 0.03:
            keys.append((end, [1, 1, 1]))
        elif settle >= length - 1e-6:
            keys[-1] = (length, [1, 1, 1])
        root['scale'] = to_channel(keys)
    return True


# ------------------------------------------------------------------ 2. morte

def roar_pose(anims, prefix):
    """Pose mais extrema do rugido do próprio chefe: {osso: [x,y,z]} (só rotação, sem a raiz)."""
    for src in ROAR_SOURCES:
        anim = anims.get(prefix + src)
        if not anim:
            continue
        chans = {b: keys_of(c['rotation']) for b, c in anim.get('bones', {}).items()
                 if b != 'root' and isinstance(c.get('rotation'), dict)}
        if not chans:
            continue
        times = sorted({t for ks in chans.values() for t, _ in ks})
        def energy(t):
            return sum(sum(abs(a - b) for a, b in zip(sample(ks, t), ks[0][1])) for ks in chans.values())
        best = max(times, key=energy)
        if energy(best) < 20:
            continue
        return {b: sample(ks, best) for b, ks in chans.items()}
    return {}


def model_bones(boss_id):
    """Ossos que existem no modelo do chefe (para nunca animar um osso que não está lá)."""
    path = os.path.join(GEO, boss_id + '.geo.json')
    if not os.path.exists(path):
        return None
    geo = json.load(open(path, encoding='utf-8'))
    return {b['name'] for g in geo.get('minecraft:geometry', []) for b in g.get('bones', [])}


def dramatize_death(anims, prefix, spec, have=None):
    anim = anims.get(prefix + 'death')
    if anim is None:
        return False
    if have is not None:   # tira do plano os ossos que o modelo não tem
        spec = dict(spec)
        for k in ('torso', 'head', 'jaw'):
            if spec.get(k) not in have:
                spec.pop(k, None)
        spec['arms'] = tuple(a for a in spec.get('arms', ()) if a in have)
        spec['sway'] = tuple(a for a in spec.get('sway', ()) if a in have)
    old_len = float(anim.get('animation_length', 2.5))
    length = float(spec.get('length') or old_len)
    pre = length * 0.42                      # as três batidas novas ocupam 42% do tempo
    scale = (length - pre) / old_len         # a queda antiga é espremida no resto
    heavy = spec.get('heavy', False)
    amp = 0.65 if heavy else 1.0
    bones = anim.setdefault('bones', {})
    roar = roar_pose(anims, prefix)

    def base(bone, chan='rotation'):
        c = bones.get(bone, {}).get(chan)
        if isinstance(c, dict):
            ks = keys_of(c)
            if ks and ks[0][0] <= 1e-6:
                return ks[0][1]
        return [1.0, 1.0, 1.0] if chan == 'scale' else [0.0, 0.0, 0.0]

    def add(b, off):
        return [x + o for x, o in zip(b, off)]

    # --- batidas novas por osso: lista [(fração de `pre`, deslocamento em graus)]
    beats = {}
    torso, head, jaw = spec.get('torso'), spec.get('head'), spec.get('jaw')
    if torso:
        beats[torso] = [(0.10, [-14 * amp, 0, 0]), (0.28, [14 * amp, 0, 0]), (0.40, [15 * amp, 0, 5 * amp]),
                        (0.50, [14 * amp, 0, -5 * amp]), (0.60, [13 * amp, 0, 4 * amp]), ('roar', [-12 * amp, 0, 0])]
    if head:
        beats[head] = [(0.10, [-24 * amp, 0, 0]), (0.28, [20 * amp, 0, 0]), (0.44, [22 * amp, 6, 0]),
                       (0.56, [20 * amp, -6, 0]), ('roar', [-30 * amp, 0, 0])]
    if jaw:
        beats[jaw] = [(0.10, [22, 0, 0]), (0.28, [6, 0, 0]), ('roar', [30, 0, 0])]
    for i, arm in enumerate(spec.get('arms', ())):
        late = 0.03 * i                      # um braço chega um instante depois do outro (menos "robô")
        beats[arm] = [(0.10 + late, [-32 * amp, 0, 0]), (0.28 + late, [-62 * amp, 0, 0]), (0.60, [-58 * amp, 0, 0]),
                      ('roar', [-18 * amp, 0, 0])]
    for i, bone in enumerate(spec.get('sway', ())):
        s = 1 if i % 2 == 0 else -1          # vizinhos tremem em sentidos opostos
        beats[bone] = [(0.10, [-6, 0, 0]), (0.30, [3, 5 * s, 6 * s]), (0.42, [3, -5 * s, -6 * s]),
                       (0.54, [3, 4 * s, 5 * s]), (0.64, [2, -3 * s, -4 * s]), ('roar', [-4, 0, 0])]

    for bone, plan in beats.items():
        b = base(bone)
        keys = [(0.0, list(b))]
        for frac, off in plan:
            if frac == 'roar':
                # último rugido: a pose do rugido do próprio chefe, se ele tiver uma para este osso
                pose = roar.get(bone)
                # pose de rugido absurda para uma morte (giro inteiro etc.): usa a batida genérica
                if pose is None or max(abs(p - q) for p, q in zip(pose, b)) > 70:
                    pose = add(b, off)
                keys.append((pre * 0.80, list(pose)))
                keys.append((pre * 0.95, [p + (p - q) * 0.06 for p, q in zip(pose, b)]))
            else:
                keys.append((pre * frac, add(b, off)))
        old = bones.get(bone, {}).get('rotation')
        if isinstance(old, dict):
            keys += [(pre + t * scale, v) for t, v in keys_of(old) if t > 1e-6]
        else:
            keys.append((pre + (length - pre) * 0.35, list(b)))   # sem queda antiga: relaxa de volta
        bones.setdefault(bone, {})['rotation'] = to_channel(keys)

    # --- o resto da queda antiga: só anda no tempo (e fica parado até a queda começar)
    for bone, chans in bones.items():
        for chan in list(chans):
            c = chans[chan]
            if not isinstance(c, dict) or (chan == 'rotation' and bone in beats):
                continue
            ks = keys_of(c)
            keys = []
            for t, v in ks:
                if t <= 1e-6:
                    keys.append((0.0, v))
                    keys.append((pre, list(v)))
                else:
                    keys.append((pre + t * scale, v))
            chans[chan] = to_channel(keys)
    anim['animation_length'] = round(length, 3)
    return True


# ------------------------------------------------------------------ principal

def process(boss_id, check):
    path = os.path.join(ANIMS, boss_id + '.animation.json')
    if not os.path.exists(path):
        print('  (sem arquivo) ' + boss_id)
        return 0, 0
    data = json.load(open(path, encoding='utf-8'))
    anims = data['animations']
    prefix = 'animation.%s.' % boss_id
    hits = deaths = 0
    if boss_id in DEATHS:
        death = anims.get(prefix + 'death')
        if death is not None and not death.get('polish'):
            if dramatize_death(anims, prefix, DEATHS[boss_id], model_bones(boss_id)):
                death['polish'] = 1
                deaths += 1
    for name, anim in anims.items():
        s = short(name)
        if anim.get('polish') or anim.get('loop') is True:
            continue
        if s in NOT_ATTACK or re.search(r'_\d$', s):   # *_0..4 = cabeças da Hidra (vários controladores ao mesmo tempo)
            continue
        if impactify(anim):
            anim['polish'] = 1
            hits += 1
    if (hits or deaths) and not check:
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            json.dump(data, f, indent=2, ensure_ascii=False)
            f.write('\n')
    print('  %-20s ataques: %2d   morte: %s' % (boss_id, hits, 'sim' if deaths else '-'))
    return hits, deaths


def main():
    check = '--check' in sys.argv
    print('boss_polish' + (' (--check: nada é gravado)' if check else ''))
    th = td = 0
    for boss_id in IMPACT_FILES:
        h, d = process(boss_id, check)
        th += h
        td += d
    print('total: %d ataques, %d mortes' % (th, td))


if __name__ == '__main__':
    main()
