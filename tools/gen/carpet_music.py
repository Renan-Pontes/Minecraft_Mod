#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Música do TAPETE VOADOR — estilo indiano, composição ORIGINAL sintetizada aqui com numpy (nada baixado/copiado).

Instrumentos: tanpura (drone Pa-Sa-Sa-Sa com "jawari" — o zumbido da ponte), sitar (Karplus-Strong + jawari + meend,
as deslizadas de nota), bansuri (flauta com sopro e vibrato) e tabla (dayan agudo que "canta" + bayan grave que desliza),
no ciclo Teentaal (16 tempos). Escala: raga Bhairav (Sa re Ga ma Pa dha Ni) — a mais "indiana" ao ouvido.

Saída: assets/isekai_mahou/sounds/music/flying_carpet.ogg (loop sem emenda: buffer circular) + entrada
"boss_music.flying_carpet" no sounds.json. Se o Renan puser uma música em MUSICAS_DOS_CHEFES/flying_carpet,
o tools/music_import.py troca esta pela dele (a original fica guardada em tools/gen/boss_music_original/).

Uso: py -I tools/gen/carpet_music.py
"""
import json
import math
import os
import subprocess
import wave

import numpy as np

SR = 32000
TAU = 2 * math.pi
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'isekai_mahou')
OUT = os.path.join(ASSETS, 'sounds', 'music', 'flying_carpet.ogg')
SOUNDS_JSON = os.path.join(ASSETS, 'sounds.json')
TMP = os.path.join(ROOT, 'build', 'carpet_tmp.wav')

BPM = 104
BEAT = 60.0 / BPM
CYCLE = 16  # Teentaal
CYCLES = 8
SECONDS = BEAT * CYCLE * CYCLES  # ~74 s
SA = 50  # D3 (MIDI) — tônica
# Bhairav: Sa re(b2) Ga(3) ma(4) Pa(5) dha(b6) Ni(7)
BHAIRAV = [0, 1, 4, 5, 7, 8, 11]


def hz(m):
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


def deg(d, octave=0):
    """Grau da escala (0 = Sa) → MIDI."""
    o, i = divmod(d, 7)
    return SA + 12 * (o + octave) + BHAIRAV[i]


class Song:
    def __init__(self, seconds, seed=7):
        self.n = int(round(seconds * SR))
        self.buf = np.zeros((2, self.n))
        self.rev = np.zeros((2, self.n))
        self.rng = np.random.default_rng(seed)

    def add(self, t, sig, pan=0.0, gain=1.0, send=0.25):
        a = (max(-1.0, min(1.0, pan)) + 1) * math.pi / 4
        i0 = int(round(t * SR)) % self.n
        idx = (np.arange(len(sig)) + i0) % self.n  # circular: o loop não tem emenda
        for ch, g in ((0, math.cos(a) * gain), (1, math.sin(a) * gain)):
            np.add.at(self.buf[ch], idx, sig * g)
            np.add.at(self.rev[ch], idx, sig * g * send)

    def mix(self):
        # reverb por convolução circular (cauda dá a volta no loop)
        ln = int(SR * 2.2)
        t = np.arange(ln) / SR
        out = self.buf.copy()
        for ch in range(2):
            ir = self.rng.normal(0, 1, ln) * np.exp(-t * 3.2)
            ir[: int(SR * 0.012)] = 0
            ir /= np.sqrt(np.sum(ir ** 2))
            n2 = self.n
            wet = np.real(np.fft.ifft(np.fft.fft(self.rev[ch]) * np.fft.fft(ir, n2)))
            out[ch] += wet * 0.55
        out /= np.max(np.abs(out)) + 1e-9
        return np.tanh(out * 1.4) / np.tanh(1.4) * 0.75


def jawari(x, amt=0.35):
    """Zumbido da ponte do sitar/tanpura: um pouco de distorção que realça harmônicos agudos."""
    return (1 - amt) * x + amt * np.tanh(3.0 * x)


def pluck(f0, dur, bright=0.6, decay=0.996, bend=None, rng=None):
    """Karplus-Strong. bend = (t_inicio, semitons, duração) → meend (deslizada) na afinação."""
    n = int(dur * SR)
    out = np.zeros(n)
    period = SR / f0
    size = int(period) + 2
    line = (rng.random(size) * 2 - 1) if rng is not None else np.random.uniform(-1, 1, size)
    # suaviza o ruído inicial para brilho menor
    for _ in range(int((1 - bright) * 4)):
        line = 0.5 * (line + np.roll(line, 1))
    pos = 0.0
    ln = len(line)
    for i in range(n):
        t = i / SR
        p = period
        if bend is not None and t >= bend[0]:
            k = min(1.0, (t - bend[0]) / bend[2])
            k = 0.5 - 0.5 * math.cos(math.pi * k)
            p = period / (2.0 ** (bend[1] * k / 12.0))
        pos = (pos + 1) % ln
        j = int(pos)
        frac = pos - j
        d = p if p < ln - 1 else ln - 2
        r = (j - d) % ln
        ri = int(r)
        rf = r - ri
        a = line[ri] * (1 - rf) + line[(ri + 1) % ln] * rf
        b = line[(ri + 1) % ln] * (1 - rf) + line[(ri + 2) % ln] * rf
        v = decay * 0.5 * (a + b)
        line[j] = v
        out[i] = v
    env = np.minimum(1.0, np.arange(n) / (SR * 0.002))
    return jawari(out * env, 0.4)


def tanpura_note(f0, dur, rng):
    """Uma corda de tanpura: muitos harmônicos com 'jawari' que ondula (o brilho passeando)."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    sig = np.zeros(n)
    sweep = 0.5 + 0.5 * np.sin(TAU * t / dur * 0.9 + rng.random() * 6)
    for h in range(1, 16):
        amp = (1.0 / h ** 0.8) * (0.35 + 0.65 * np.clip(1 - abs(h - (3 + 8 * sweep)) / 4.0, 0, 1))
        sig += amp * np.sin(TAU * f0 * h * t * (1 + 0.0004 * rng.normal()) + rng.random() * TAU)
    env = np.minimum(1, t / 0.03) * np.exp(-t / (dur * 0.9))
    return sig * env / 6.0


def bansuri(f0, dur, rng, bend=None):
    n = int(dur * SR)
    t = np.arange(n) / SR
    vib = 1 + 0.006 * np.sin(TAU * 5.2 * t) * np.minimum(1, t / 0.4)
    fr = np.full(n, f0) * vib
    if bend is not None:
        k = np.clip((t - bend[0]) / bend[2], 0, 1)
        k = 0.5 - 0.5 * np.cos(np.pi * k)
        fr *= 2.0 ** (bend[1] * k / 12.0)
    ph = np.cumsum(fr) / SR * TAU
    tone = np.sin(ph) + 0.25 * np.sin(2 * ph) + 0.08 * np.sin(3 * ph)
    breath = rng.normal(0, 1, n)
    breath = np.convolve(breath, np.ones(6) / 6, mode='same') * 0.12
    env = np.minimum(1, t / 0.08) * np.minimum(1, (dur - t) / 0.15).clip(0)
    return (tone + breath) * env * 0.5


def tabla(kind, rng):
    """dha = na + ge; na/ta = dayan aberto; tin = dayan agudo; ge = bayan grave deslizante; ke = batida seca."""
    n = int(0.5 * SR)
    t = np.arange(n) / SR
    out = np.zeros(n)
    if kind in ('na', 'dha', 'tin', 'ta'):
        f = 520.0 if kind != 'tin' else 700.0
        ring = (np.sin(TAU * f * t) + 0.45 * np.sin(TAU * f * 2.0 * t) + 0.25 * np.sin(TAU * f * 2.98 * t))
        out += ring * np.exp(-t * (14 if kind != 'tin' else 9)) * 0.55
        click = rng.normal(0, 1, n) * np.exp(-t * 300) * 0.25
        out += click
    if kind in ('ge', 'dha'):
        f = 95.0 * (1 + 0.6 * np.exp(-t * 18))  # o bayan "geme" descendo
        ph = np.cumsum(f) / SR * TAU
        out += np.sin(ph) * np.exp(-t * 6) * 0.9
    if kind == 'ke':
        out += rng.normal(0, 1, n) * np.exp(-t * 90) * 0.4
    return out


# Teentaal (16 tempos): dha dhin dhin dha | dha dhin dhin dha | dha tin tin ta | ta dhin dhin dha
THEKA = ['dha', 'dha', 'dha', 'dha', 'dha', 'dha', 'dha', 'dha', 'dha', 'tin', 'tin', 'ta', 'ta', 'dha', 'dha', 'dha']
THEKA_B = ['ge', 'na', 'na', 'ge', 'ge', 'na', 'na', 'ge', 'ge', 'tin', 'tin', 'na', 'na', 'na', 'na', 'ge']


def build():
    s = Song(SECONDS)
    rng = s.rng
    cycle_s = BEAT * CYCLE

    # 1) Tanpura: Pa-Sa'-Sa'-Sa, 4 cordas por ~4,6 s, o loop todo
    strings = [hz(SA - 5), hz(SA), hz(SA), hz(SA - 12)]
    t = 0.0
    k = 0
    step = cycle_s / 4
    while t < SECONDS:
        s.add(t, tanpura_note(strings[k % 4], step * 3.2, rng), pan=-0.35 + 0.2 * (k % 2), gain=0.55, send=0.35)
        t += step / 4
        k += 1

    # 2) Tabla: entra no 2º ciclo (o 1º é alaap: só drone + sitar livre)
    for c in range(1, CYCLES):
        for b in range(CYCLE):
            tt = c * cycle_s + b * BEAT
            kind = THEKA[b] if (b != 9 and b != 10) else 'tin'
            s.add(tt, tabla(kind, rng), pan=0.15, gain=0.6, send=0.12)
            # contratempos leves (ke / na) para balançar
            if b % 2 == 1 and rng.random() < 0.6:
                s.add(tt + BEAT / 2, tabla('na' if rng.random() < 0.6 else 'ke', rng), pan=0.2, gain=0.3, send=0.1)
            if b in (0, 4, 12) and c % 2 == 0:
                s.add(tt + BEAT * 0.75, tabla('ge', rng), pan=0.05, gain=0.35, send=0.1)
        if c % 2 == 1:  # tihai simples no fim do ciclo
            for r in range(3):
                s.add(c * cycle_s + (CYCLE - 1.5 + r * 0.5) * BEAT, tabla('dha', rng), pan=0.15, gain=0.55)

    # 3) Sitar: alaap (livre, lento) no 1º ciclo; gat (frases rítmicas) depois. Melodia original em Bhairav.
    alaap = [(0, 1.6, None), (1, 1.0, (0.2, -1, 0.5)), (2, 1.4, None), (3, 0.9, None), (4, 2.2, (0.6, 1, 0.6)),
             (5, 1.2, (0.3, -1, 0.4)), (4, 1.6, None), (2, 1.0, None), (1, 0.8, (0.1, -1, 0.3)), (0, 2.8, None)]
    t = 0.3
    for d, dur, bend in alaap:
        s.add(t, pluck(hz(deg(d, 1)), dur + 1.5, 0.7, 0.9965, bend, rng), pan=-0.1, gain=0.75, send=0.4)
        t += dur * BEAT * 1.6

    gat_phrases = [
        [(4, 1), (5, 0.5), (4, 0.5), (2, 1), (3, 1), (2, 0.5), (1, 0.5), (0, 2), (6, 1, -1), (0, 2, 'up')],
        [(0, 0.5), (1, 0.5), (2, 1), (4, 1), (5, 1), (6, 0.5), (7, 0.5), (6, 1), (5, 1), (4, 2), (2, 2)],
        [(7, 1), (6, 0.5), (5, 0.5), (4, 1), (5, 0.5), (4, 0.5), (2, 1), (1, 1), (2, 0.5), (1, 0.5), (0, 3)],
        [(4, 0.5), (4, 0.5), (5, 1), (4, 0.5), (2, 0.5), (3, 1), (2, 1), (1, 0.5), (2, 0.5), (0, 1), (6, 1, -1), (0, 3)],
    ]
    for c in range(1, CYCLES):
        ph = gat_phrases[(c - 1) % len(gat_phrases)]
        t = c * cycle_s + (BEAT * 0.5 if c % 2 else 0.0)
        for note in ph:
            d, beats = note[0], note[1]
            octv = 1
            if len(note) > 2 and note[2] == -1:
                d, octv = d, 0
            bend = (0.08, 1, 0.12) if len(note) > 2 and note[2] == 'up' else (
                (beats * BEAT * 0.4, -1, 0.15) if rng.random() < 0.25 and beats >= 1 else None)
            dur = beats * BEAT
            s.add(t, pluck(hz(deg(d, octv)), dur + 0.9, 0.65, 0.995, bend, rng), pan=-0.15, gain=0.7, send=0.3)
            # chikari: a corda aguda de ritmo tocando junto de vez em quando
            if beats >= 1 and rng.random() < 0.35:
                s.add(t + dur * 0.5, pluck(hz(SA + 24), 0.35, 0.9, 0.99, None, rng), pan=-0.3, gain=0.18, send=0.2)
            t += dur

    # 4) Bansuri: responde ao sitar nos ciclos 3, 5 e 7 (pergunta e resposta)
    flute = [(4, 2, None), (5, 1, (0.4, -1, 0.3)), (4, 1, None), (2, 2, None), (1, 1, (0.3, -1, 0.3)), (0, 3, None)]
    for c in (3, 5, 7):
        t = c * cycle_s + BEAT * 8
        for d, beats, bend in flute:
            dur = beats * BEAT
            s.add(t, bansuri(hz(deg(d, 2)), dur * 1.05, rng, bend), pan=0.35, gain=0.32, send=0.45)
            t += dur
    return s.mix()


def write(stereo):
    os.makedirs(os.path.dirname(TMP), exist_ok=True)
    pcm = (np.clip(stereo.T, -1, 1) * 32767).astype(np.int16)
    with wave.open(TMP, 'wb') as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    subprocess.run(['ffmpeg', '-y', '-v', 'error', '-i', TMP, '-c:a', 'libvorbis', '-q:a', '2', OUT], check=True)
    os.remove(TMP)


def update_json():
    with open(SOUNDS_JSON, encoding='utf-8') as f:
        data = json.load(f)
    data['boss_music.flying_carpet'] = {'sounds': [{'name': 'isekai_mahou:music/flying_carpet', 'stream': True}]}
    with open(SOUNDS_JSON, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


if __name__ == '__main__':
    write(build())
    update_json()
    print('ok:', OUT, os.path.getsize(OUT) // 1024, 'KB', round(SECONDS, 1), 's')
