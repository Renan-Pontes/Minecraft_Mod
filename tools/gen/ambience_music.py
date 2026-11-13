#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Gerador das MÚSICAS AMBIENTES (módulo ambience, v1.0 RELEASE) — "sempre música baixa e de dark fantasy".

Tudo é sintetizado aqui com numpy: composição ORIGINAL (progressões, motivos e timbres escritos neste arquivo),
nada baixado, nada copiado. Saída: assets/isekai_mahou/sounds/ambience/music/<zona>.ogg (ffmpeg libvorbis, 32 kHz,
estéreo) e as entradas "ambience.music.<zona>" no sounds.json (ler-modificar-gravar; nunca sobrescreve o resto).

Zonas: dungeon (masmorras), citadel (Cidadela Demoníaca), library (Biblioteca do End), tower (torres),
city (cidades NPC e do jogador), abyss (Abismo), home (Reino do Lar, calmo).

Uso:
    py -I tools/gen/ambience_music.py              # todas
    py -I tools/gen/ambience_music.py city abyss   # só algumas
    py -I tools/gen/ambience_music.py --json       # só refaz as entradas do sounds.json

Motor (resumo): osciladores por wavetable de banda limitada, cordas/coro (formantes nos harmônicos), harpa/alaúde,
celesta, flauta, piano grave, taiko; mesa com mandada de reverb; reverb por convolução CIRCULAR (a cauda dá a volta,
então o loop não tem emenda); masterização com volume médio igual entre as faixas.
"""
import json
import math
import os
import subprocess
import sys
import wave

import numpy as np

SR = 32000
TAU = 2 * math.pi
TN = 2048
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'isekai_mahou')
OUT_DIR = os.path.join(ASSETS, 'sounds', 'ambience', 'music')
SOUNDS_JSON = os.path.join(ASSETS, 'sounds.json')
TMP_DIR = os.path.join(ROOT, 'build', 'ambience_tmp')

ZONES = ['dungeon', 'citadel', 'library', 'tower', 'city', 'abyss', 'home']


def mf(m):
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


# ============================================================== mesa (buffer circular)

class Song:
    """Buffer estéreo CIRCULAR: o que passa do fim volta para o começo (loop sem emenda)."""

    def __init__(self, seconds, seed):
        self.n = int(round(seconds * SR))
        self.dry = np.zeros((2, self.n))
        self.send = np.zeros((2, self.n))
        self.rng = np.random.default_rng(seed)

    def add(self, t, sig, pan=0.0, gain=1.0, send=0.3):
        a = (min(1.0, max(-1.0, pan)) + 1) * math.pi / 4
        gl, gr = math.cos(a) * gain, math.sin(a) * gain
        start = int(round(t * SR)) % self.n
        pos, total = 0, len(sig)
        while pos < total:
            m = min(total - pos, self.n - start)
            seg = sig[pos:pos + m]
            self.dry[0, start:start + m] += seg * gl
            self.dry[1, start:start + m] += seg * gr
            if send > 0:
                self.send[0, start:start + m] += seg * gl * send
                self.send[1, start:start + m] += seg * gr * send
            pos += m
            start = 0

    def jit(self, amount=0.012):
        return float(self.rng.uniform(-amount, amount))


def spectral(x, gain_fn):
    """Filtro por espectro (circular): gain_fn(freqs) -> ganho."""
    n = x.shape[-1]
    f = np.fft.rfftfreq(n, 1 / SR)
    return np.fft.irfft(np.fft.rfft(x) * gain_fn(f), n)


def g_lp(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def g_hp(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (fc / np.maximum(f, 1e-3)) ** (2 * order))


def g_bp(fc, q):
    return lambda f: np.exp(-0.5 * ((f - fc) / (fc / q)) ** 2)


# ============================================================== osciladores

def make_tab(f, amp):
    """Um ciclo com harmônicos só até ~Nyquist. amp(k, freq_do_harmonico) -> amplitude."""
    kmax = max(1, min(TN // 2 - 1, int(0.45 * SR / f)))
    k = np.arange(1, kmax + 1)
    spec = np.zeros(TN // 2 + 1, dtype=complex)
    spec[1:kmax + 1] = -1j * amp(k.astype(float), k * f)
    tab = np.fft.irfft(spec, TN)
    pk = np.max(np.abs(tab))
    return tab / (pk if pk > 0 else 1.0)


def osc(tab, f, n, rng, vib_rate=0.0, vib_cents=0.0, vib_delay=0.0):
    t = np.arange(n) / SR
    if vib_cents:
        depth = vib_cents * np.clip((t - vib_delay) / 0.5, 0, 1)
        fv = f * 2.0 ** (depth * np.sin(TAU * vib_rate * t + rng.uniform(0, TAU)) / 1200.0)
        ph = rng.random() + np.cumsum(fv) / SR
    else:
        ph = rng.random() + f * t
    idx = (ph % 1.0) * TN
    i0 = idx.astype(np.int64)
    fr = idx - i0
    i0 &= TN - 1
    return tab[i0] * (1 - fr) + tab[(i0 + 1) & (TN - 1)] * fr


def env(n, a, r):
    """Ataque e soltura em cosseno levantado."""
    e = np.ones(n)
    na, nr = min(n, max(1, int(a * SR))), min(n, max(1, int(r * SR)))
    e[:na] *= 0.5 - 0.5 * np.cos(np.linspace(0, math.pi, na))
    e[-nr:] *= 0.5 + 0.5 * np.cos(np.linspace(0, math.pi, nr))
    return e


def decl(y, ms=4.0):
    """Tira o estalo do começo e do fim."""
    k = min(len(y) // 2, max(2, int(ms / 1000.0 * SR)))
    y[:k] *= np.linspace(0, 1, k)
    y[-k:] *= np.linspace(1, 0, k)
    return y


# ============================================================== instrumentos (mono)

def strings(f, dur, rng, bright=1800.0, a=1.8, r=2.6, voices=3, vib=5.0):
    n = int((dur + r) * SR)
    tab = make_tab(f, lambda k, fr: (1.0 / k) / (1.0 + (fr / bright) ** 2))
    y = np.zeros(n)
    for _ in range(voices):
        det = 2.0 ** (rng.uniform(-8, 8) / 1200.0)
        y += osc(tab, f * det, n, rng, 4.6 + rng.uniform(-0.6, 0.6), vib, 0.7)
    return y / voices * env(n, a, r)


VOWELS = {
    'ah': [(700, 110, 1.0), (1150, 130, 0.5), (2700, 220, 0.18)],
    'oo': [(340, 80, 1.0), (800, 110, 0.32), (2400, 220, 0.06)],
    'oh': [(480, 90, 1.0), (850, 120, 0.45), (2500, 220, 0.1)],
}


def choir(f, dur, rng, vowel='oo', a=2.0, r=3.0, voices=4):
    peaks = VOWELS[vowel]

    def amp(k, fr):
        g = np.full_like(fr, 0.012)
        for fc, bw, gain in peaks:
            g = g + gain * np.exp(-0.5 * ((fr - fc) / bw) ** 2)
        return g / k ** 0.7

    n = int((dur + r) * SR)
    tab = make_tab(f, amp)
    y = np.zeros(n)
    for _ in range(voices):
        det = 2.0 ** (rng.uniform(-11, 11) / 1200.0)
        y += osc(tab, f * det, n, rng, 5.0 + rng.uniform(-0.7, 0.7), 12, 0.4)
    return y / voices * env(n, a, r)


def pluck(f, dur, rng, bright=1.0, decay=2.4):
    """Harpa/alaúde: parte escura que dura + parte brilhante que some rápido."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    dark = make_tab(f, lambda k, fr: 1.0 / k ** 2.0)
    brt = make_tab(f, lambda k, fr: (1.0 / k ** (1.15 / bright)) * (fr < 6500))
    y = osc(dark, f, n, rng) * np.exp(-t * decay * 0.55) + 0.55 * osc(brt, f * 1.002, n, rng) * np.exp(-t * decay * 3.2)
    return decl(y, 2.5)


def celesta(f, dur, rng, decay=3.0):
    n = int(dur * SR)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for ratio, g, d in ((1.0, 1.0, 1.0), (2.0, 0.28, 1.6), (2.76, 0.16, 2.6), (5.4, 0.07, 4.5)):
        if f * ratio < SR * 0.45:
            y += g * np.sin(TAU * f * ratio * t + rng.uniform(0, TAU)) * np.exp(-t * decay * d)
    return decl(y, 2.0)


def bell(f, dur, rng, decay=1.2):
    """Sino grave/distante: parciais inarmônicos."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    y = np.zeros(n)
    for ratio, g, d in ((0.5, 0.5, 0.7), (1.0, 1.0, 1.0), (1.19, 0.55, 1.2), (1.56, 0.4, 1.5), (2.0, 0.45, 1.8),
                        (2.51, 0.28, 2.4), (3.01, 0.2, 3.0), (4.1, 0.1, 4.0)):
        if f * ratio < SR * 0.45:
            y += g * np.sin(TAU * f * ratio * t + rng.uniform(0, TAU)) * np.exp(-t * decay * d)
    return decl(y, 2.0)


def flute(f, dur, rng, a=0.12, r=0.35):
    n = int((dur + r) * SR)
    tab = make_tab(f, lambda k, fr: np.where(k == 1, 1.0, np.where(k == 2, 0.2, np.where(k == 3, 0.09, 0.02 / k))))
    y = osc(tab, f, n, rng, 4.8, 14, 0.35)
    breath = spectral(rng.standard_normal(n), g_bp(f * 2.0, 1.2)) * 0.9
    breath /= np.max(np.abs(breath)) + 1e-9
    return (y + breath * 0.07) * env(n, a, r)


def piano(f, dur, rng, decay=1.1):
    n = int(dur * SR)
    t = np.arange(n) / SR
    tab = make_tab(f, lambda k, fr: (1.0 / k ** 1.25) * (fr < 5000))
    y = (osc(tab, f, n, rng) + osc(tab, f * 1.0015, n, rng)) * 0.5
    y *= np.exp(-t * decay) * (0.75 + 0.25 * np.exp(-t * 9))
    return decl(y, 3.0)


def horn(f, dur, rng, a=0.35, r=0.9):
    """Metal escuro e distante (trompa)."""
    n = int((dur + r) * SR)
    tab = make_tab(f, lambda k, fr: (1.0 / k ** 1.4) / (1.0 + (fr / 900.0) ** 2))
    y = (osc(tab, f, n, rng, 4.5, 6, 0.5) + osc(tab, f * 1.003, n, rng, 4.2, 6, 0.6)) * 0.5
    return y * env(n, a, r)


def taiko(rng, f0=86.0, f1=44.0, dur=1.3, noise_amt=0.5):
    n = int(dur * SR)
    t = np.arange(n) / SR
    freq = f1 + (f0 - f1) * np.exp(-t * 14)
    y = np.sin(TAU * np.cumsum(freq) / SR) * np.exp(-t * 4.2)
    skin = spectral(rng.standard_normal(n), g_lp(700, 2)) * np.exp(-t * 26)
    skin /= np.max(np.abs(skin)) + 1e-9
    return decl(y + skin * noise_amt, 1.5)


def drip(rng):
    n = int(0.09 * SR)
    t = np.arange(n) / SR
    f0 = rng.uniform(1500, 2300)
    freq = f0 * (1 + 4.5 * t)
    return decl(np.sin(TAU * np.cumsum(freq) / SR) * np.exp(-t * 55), 1.0)


def drone(f, n, rng, wobble=0.07):
    """Senoide grave com respiração lenta (dura o loop inteiro; os ciclos fecham no fim)."""
    t = np.arange(n) / SR
    sec = n / SR
    f_fit = round(f * sec) / sec                       # número inteiro de ciclos: o loop fecha sem estalo
    lfo = 1 + wobble * np.sin(TAU * round(0.07 * sec) / sec * t + rng.uniform(0, TAU))
    return (np.sin(TAU * f_fit * t) + 0.35 * np.sin(TAU * 2 * f_fit * t + 1.0) + 0.12 * np.sin(TAU * 3 * f_fit * t)) * lfo


def bed(n, rng, fc=220.0, q=None):
    """Cama de ruído escuro (vento de caverna); circular por construção (filtro por FFT)."""
    x = rng.standard_normal(n)
    y = spectral(x, g_bp(fc, q) if q else g_lp(fc, 2))
    slow = spectral(rng.standard_normal(n), g_lp(0.12, 2))
    slow = slow / (np.max(np.abs(slow)) + 1e-9)
    y = y * (0.65 + 0.35 * slow)
    return y / (np.max(np.abs(y)) + 1e-9)


def glide_tone(f0, f1, dur, rng, a=1.5, r=2.5, vib=18):
    """Lamento que escorrega (canto de baleia / voz do Abismo)."""
    n = int(dur * SR)
    t = np.arange(n) / SR
    k = 0.5 - 0.5 * np.cos(np.linspace(0, math.pi, n))
    freq = (f0 + (f1 - f0) * k) * 2.0 ** (vib * np.sin(TAU * 3.1 * t + rng.uniform(0, TAU)) / 1200.0)
    ph = TAU * np.cumsum(freq) / SR
    return (np.sin(ph) + 0.3 * np.sin(2 * ph + 0.4) + 0.1 * np.sin(3 * ph)) * env(n, a, r)


# ============================================================== ajudas de composição

def arp_of(notes):
    """Notas do acorde em duas oitavas (índices dos motivos caem sempre em nota do acorde)."""
    base = sorted(notes)
    return base + [m + 12 for m in base]


def pad_chord(song, t, notes, dur, gain, inst=strings, send=0.45, spread=0.7, **kw):
    for i, m in enumerate(notes):
        pan = spread * ((i / max(1, len(notes) - 1)) * 2 - 1) if len(notes) > 1 else 0.0
        song.add(t, inst(mf(m), dur, song.rng, **kw), pan, gain, send)
        song.add(t, inst(mf(m), dur, song.rng, **kw), -pan * 0.6, gain * 0.7, send)


def motif(song, inst, t0, beat, arp, notes, gain, pan=0.0, send=0.4, octave=0, hold=1.0, **kw):
    """notes = [(batida, índice no arpejo, duração em batidas)]."""
    for b, idx, ln in notes:
        m = arp[idx % len(arp)] + 12 * (idx // len(arp)) + octave
        g = gain * float(song.rng.uniform(0.85, 1.1))
        song.add(t0 + b * beat + song.jit(), inst(mf(m), ln * beat * hold, song.rng, **kw), pan + song.jit(0.08), g, send)


# ============================================================== faixas

def track_city():
    """Cidade: ré dórico, alaúde e flauta sobre cordas mornas — saudade de taverna, sem alegria demais."""
    beat, bars = 0.8, 16
    s = Song(beat * 8 * bars, 101)
    DM, G, C, AM, F, BB = ((38, [57, 62, 65, 69]), (43, [55, 59, 62, 67]), (36, [55, 60, 64, 67]),
                           (33, [57, 60, 64, 69]), (41, [57, 60, 65, 69]), (34, [58, 62, 65, 70]))
    prog = [DM, G, DM, C, F, C, AM, DM, DM, F, C, G, BB, F, AM, DM]
    lute_a = [(0, 0, 3), (1.5, 2, 2), (2, 1, 2), (3, 3, 3), (4, 2, 2), (5.5, 1, 2), (6, 3, 2), (7, 2, 2)]
    lute_b = [(0, 0, 3), (1, 1, 2), (2, 2, 2), (3, 4, 3), (4.5, 3, 2), (5, 2, 2), (6, 1, 3)]
    flute_lines = {8: [(0, 5, 3), (4, 4, 2), (6, 6, 2)], 9: [(0, 5, 4), (5, 3, 3)], 10: [(0, 6, 2), (2, 5, 2), (4, 4, 4)],
                   11: [(0, 4, 5)], 12: [(0, 5, 3), (4, 6, 2), (6, 7, 2)], 13: [(0, 6, 4), (5, 5, 3)],
                   14: [(0, 5, 2), (2, 4, 2), (4, 3, 4)], 15: [(0, 4, 6)],
                   4: [(2, 5, 4)], 6: [(0, 4, 3), (4, 3, 3)]}
    for i, (bass, pad) in enumerate(prog):
        t = i * beat * 8
        pad_chord(s, t, pad, beat * 8, 0.085, bright=1300, a=2.2, r=3.0)
        s.add(t, pluck(mf(bass + 12), beat * 7, s.rng, 0.7, 1.3), 0.0, 0.34, 0.25)
        s.add(t + beat * 4, pluck(mf(bass + 19), beat * 4, s.rng, 0.7, 1.6), 0.0, 0.2, 0.25)
        motif(s, pluck, t, beat, arp_of(pad), lute_a if i % 2 == 0 else lute_b, 0.26, -0.3, 0.38, octave=-12, bright=1.1)
        if i in flute_lines:
            motif(s, flute, t, beat, arp_of(pad), flute_lines[i], 0.2, 0.3, 0.55)
    return s, dict(rev_sec=3.8, damp=4200, wet=0.9)


def track_home():
    """Reino do Lar: fá maior manso, celesta e flauta — o único lugar do mundo sem medo."""
    beat, bars = 0.8, 16
    s = Song(beat * 8 * bars, 202)
    F, AM, DM, BB, C = ((41, [57, 60, 65, 69]), (45, [57, 60, 64, 69]), (38, [57, 62, 65, 69]),
                        (46, [58, 62, 65, 69]), (36, [55, 60, 64, 67]))
    prog = [F, AM, DM, BB, F, C, DM, BB, F, AM, DM, BB, F, C, BB, F]
    cel_a = [(0, 4, 3), (2, 5, 2), (3, 6, 3), (4, 5, 2), (6, 4, 3)]
    cel_b = [(0, 6, 3), (1.5, 5, 2), (3, 4, 3), (5, 5, 2), (6, 7, 3)]
    fl = {8: [(0, 5, 4), (5, 6, 3)], 9: [(0, 6, 3), (4, 5, 4)], 10: [(0, 5, 3), (4, 4, 4)], 11: [(1, 5, 6)],
          12: [(0, 6, 4), (5, 7, 3)], 13: [(0, 6, 3), (4, 5, 4)], 14: [(0, 4, 4), (5, 5, 3)], 15: [(0, 4, 7)]}
    for i, (bass, pad) in enumerate(prog):
        t = i * beat * 8
        pad_chord(s, t, pad, beat * 8, 0.1, bright=1100, a=2.4, r=3.2)
        s.add(t, pluck(mf(bass + 12), beat * 8, s.rng, 0.6, 1.0), 0.0, 0.3, 0.3)
        motif(s, celesta, t, beat, arp_of(pad), cel_a if i % 2 == 0 else cel_b, 0.13, 0.25, 0.6, decay=2.2)
        motif(s, pluck, t, beat, arp_of(pad), [(0, 0, 4), (2, 2, 4), (4, 1, 4), (6, 3, 3)], 0.14, -0.35, 0.4, octave=-12)
        if i in fl:
            motif(s, flute, t, beat, arp_of(pad), fl[i], 0.15, -0.15, 0.6)
    return s, dict(rev_sec=4.2, damp=4500, wet=1.0)


def track_tower():
    """Torre: lá menor em 12/8, ostinato de harpa que sobe sem parar — a escada que não acaba."""
    beat, bars = 0.5, 16
    s = Song(beat * 12 * bars, 303)
    AM, F, C, GB, DM, E = ((33, [57, 60, 64, 69]), (41, [57, 60, 65, 69]), (36, [55, 60, 64, 67]),
                           (35, [55, 59, 62, 67]), (38, [57, 62, 65, 69]), (40, [56, 59, 64, 68]))
    prog = [AM, F, C, GB, AM, F, DM, E, AM, F, C, GB, DM, F, E, AM]
    ost = [(0, 0, 2), (1, 1, 2), (2, 2, 2), (3, 3, 2), (4, 2, 2), (5, 1, 2), (6, 0, 2), (7, 1, 2), (8, 2, 2), (9, 4, 2),
           (10, 2, 2), (11, 1, 2)]
    lead = {8: [(0, 4, 5), (6, 5, 5)], 9: [(0, 6, 5), (6, 5, 5)], 10: [(0, 5, 5), (6, 4, 5)], 11: [(0, 4, 10)],
            12: [(0, 5, 5), (6, 6, 5)], 13: [(0, 7, 5), (6, 6, 5)], 14: [(0, 6, 5), (6, 5, 5)], 15: [(0, 4, 11)]}
    for i, (bass, pad) in enumerate(prog):
        t = i * beat * 12
        pad_chord(s, t, pad, beat * 12, 0.08 if i < 8 else 0.1, bright=2000, a=1.6, r=2.6)
        s.add(t, piano(mf(bass + 12), beat * 11, s.rng, 0.9), 0.0, 0.19, 0.3)
        motif(s, pluck, t, beat, arp_of(pad), ost, 0.2, -0.25, 0.45, bright=1.0, hold=1.6)
        s.add(t + s.jit(0.004), taiko(s.rng, 74, 40, 1.2, 0.3), 0.1, 0.15, 0.3)
        s.add(t + beat * 6 + s.jit(0.004), taiko(s.rng, 96, 56, 0.9, 0.3), -0.1, 0.09, 0.3)
        if i >= 8:
            s.add(t + beat * 9, taiko(s.rng, 96, 56, 0.9, 0.3), -0.2, 0.09, 0.3)
        if i in lead:
            motif(s, celesta, t, beat, arp_of(pad), lead[i], 0.13, 0.3, 0.6, octave=12, decay=1.6)
            motif(s, horn, t, beat, arp_of(pad), [(0, 0, 10)], 0.1, 0.15, 0.55, octave=-12)
    return s, dict(rev_sec=4.0, damp=3800, wet=0.9)


def track_dungeon():
    """Masmorra: ré menor oco — pedal grave, piano solto no escuro, goteiras e um coro que mal se ouve."""
    bar, bars = 8.0, 12
    s = Song(bar * bars, 404)
    n = s.n
    DM, BB, GM, A5 = [50, 53, 57], [46, 53, 58], [43, 50, 55, 58], [45, 52, 57]
    prog = [DM, DM, BB, GM, DM, BB, GM, A5, DM, GM, BB, A5]
    d = drone(mf(38), n, s.rng)
    s.add(0, d, 0.0, 0.065, 0.15)
    b = bed(n, s.rng, 320.0)
    s.add(0, b, -0.5, 0.05, 0.2)
    s.add(0, np.roll(b, n // 3), 0.5, 0.05, 0.2)
    lines = [[(0, 50, 5)], [(1, 53, 3), (4, 52, 4)], [(0, 46, 4), (3.5, 50, 4)], [(1, 55, 3), (5, 53, 3)],
             [(0, 57, 3), (2.5, 53, 3), (5, 50, 3)], [(1, 58, 5)], [(0, 55, 3), (3, 50, 3), (6, 46, 2)], [(1, 57, 3), (4, 56, 4)],
             [(0, 62, 3), (2, 57, 3), (5, 53, 3)], [(1, 58, 3), (4, 55, 4)], [(0, 53, 3), (3, 50, 5)], [(2, 49, 6)]]
    for i, ch in enumerate(prog):
        t = i * bar
        pad_chord(s, t, ch, bar, 0.06, bright=1200, a=3.0, r=4.0)
        if i >= 4:
            pad_chord(s, t, [m + 12 for m in ch[:2]], bar, 0.055, inst=choir, send=0.7, vowel='oo', a=3.0, r=4.0)
        for beat, m, ln in lines[i]:
            s.add(t + beat + s.jit(0.03), piano(mf(m + 12), ln + 1.5, s.rng, 0.75), s.jit(0.3), 0.36, 0.75)
        if i % 3 == 2:
            s.add(t + 5.0, bell(mf(81 + (1 if i % 2 else 0)), 5.0, s.rng, 1.0), 0.6 if i % 2 else -0.6, 0.055, 0.9)
        if i % 4 == 0:
            s.add(t + 0.1, taiko(s.rng, 60, 30, 2.4, 0.2), 0.0, 0.11, 0.6)
    t = 1.3
    while t < bar * bars - 1:
        s.add(t, drip(s.rng), float(s.rng.uniform(-0.8, 0.8)), float(s.rng.uniform(0.03, 0.075)), 1.4)
        t += float(s.rng.uniform(1.6, 6.5))
    return s, dict(rev_sec=6.5, damp=2600, wet=1.15)


def track_citadel():
    """Cidadela Demoníaca: dó frígio pesado — tambores de guerra lentos, coro grave e trompas ao longe."""
    beat, bars = 0.75, 16
    s = Song(beat * 8 * bars, 505)
    n = s.n
    CM, DB, BBM, AB, G = [48, 51, 55], [49, 53, 56], [46, 49, 53], [44, 48, 51], [43, 50, 56]
    prog = [CM, CM, DB, CM, BBM, AB, DB, G, CM, DB, CM, BBM, AB, DB, G, CM]
    s.add(0, drone(mf(36), n, s.rng, 0.1), 0.0, 0.06, 0.12)
    b = bed(n, s.rng, 520.0, 0.8)
    s.add(0, b, -0.6, 0.045, 0.3)
    s.add(0, np.roll(b, n // 2), 0.6, 0.045, 0.3)
    horn_line = {2: [(0, 49, 6)], 3: [(0, 48, 7)], 6: [(0, 53, 4), (4, 51, 4)], 7: [(0, 50, 7)],
                 9: [(0, 56, 4), (4, 53, 4)], 10: [(0, 55, 3), (3, 51, 5)], 11: [(0, 53, 4), (4, 49, 4)],
                 13: [(0, 56, 3), (3, 53, 5)], 14: [(0, 55, 4), (4, 50, 4)], 15: [(0, 48, 8)]}
    for i, ch in enumerate(prog):
        t = i * beat * 8
        pad_chord(s, t, [m - 12 for m in ch], beat * 8, 0.06, bright=1000, a=1.6, r=2.4)
        pad_chord(s, t, ch, beat * 8, 0.085 if i < 4 else 0.12, inst=choir, send=0.6, vowel='oh', a=1.8, r=2.6)
        for bpos, g, lo in ((0, 0.24, True), (3, 0.13, False), (4, 0.18, True), (6.5, 0.1, False), (7, 0.12, False)):
            if i < 2 and not lo:
                continue
            dr = taiko(s.rng, 78, 38, 1.5, 0.45) if lo else taiko(s.rng, 104, 60, 0.9, 0.5)
            s.add(t + bpos * beat + s.jit(0.006), dr, 0.15 if lo else -0.25, g, 0.4)
        if i % 4 == 3:
            s.add(t + beat * 4, bell(mf(43), 6.0, s.rng, 0.8), 0.5, 0.06, 0.9)
        for bpos, m, ln in horn_line.get(i, []):
            s.add(t + bpos * beat, horn(mf(m + 12), ln * beat, s.rng), -0.3, 0.2, 0.6)
            s.add(t + bpos * beat, horn(mf(m), ln * beat, s.rng), 0.3, 0.2, 0.6)
    return s, dict(rev_sec=5.0, damp=2400, wet=1.0)


def track_library():
    """Biblioteca do End: mi menor com cor lídia — celesta em arpejos, vidro e um coro de estrelas."""
    beat, bars = 0.8, 16
    s = Song(beat * 8 * bars, 606)
    EM9, CM7, AM9, BS, GA, DF, C11 = ((40, [59, 62, 66, 67]), (36, [55, 59, 64, 67]), (33, [55, 59, 60, 64]),
                                      (35, [54, 57, 59, 64]), (43, [55, 59, 62, 67]), (42, [57, 62, 66, 69]),
                                      (36, [55, 60, 64, 66]))
    prog = [EM9, CM7, AM9, BS, EM9, CM7, AM9, BS, EM9, GA, DF, C11, AM9, CM7, BS, EM9]
    arp_a = [(0, 0, 3), (0.5, 1, 3), (1, 2, 3), (1.5, 3, 3), (2, 4, 3), (3, 5, 3), (4, 4, 3), (4.5, 3, 3), (5, 2, 3), (6, 6, 4)]
    arp_b = [(0, 2, 3), (1, 4, 3), (1.5, 5, 3), (2.5, 6, 3), (3.5, 5, 3), (4, 3, 3), (5, 4, 3), (5.5, 6, 3), (6.5, 7, 3)]
    for i, (bass, pad) in enumerate(prog):
        t = i * beat * 8
        pad_chord(s, t, pad, beat * 8, 0.06, bright=2600, a=2.6, r=3.4, vib=3.0)
        if i >= 4:
            pad_chord(s, t, [pad[1] + 12, pad[3] + 12], beat * 8, 0.03, inst=choir, send=0.9, vowel='ah', a=2.8, r=3.4)
        s.add(t, piano(mf(bass + 12), beat * 8, s.rng, 0.6), 0.0, 0.2, 0.5)
        motif(s, celesta, t, beat, arp_of(pad), arp_a if i % 2 == 0 else arp_b, 0.12, 0.2 if i % 2 else -0.2, 0.75, decay=2.6)
        if i % 4 == 3:
            s.add(t + beat * 6, bell(mf(76), 5.0, s.rng, 1.3), -0.5, 0.04, 1.0)
    t = 0.7
    scale = [76, 78, 79, 83, 86, 88, 90, 91]
    while t < beat * 8 * bars - 0.5:
        s.add(t, celesta(mf(int(s.rng.choice(scale)) + 12), 1.6, s.rng, 5.0), float(s.rng.uniform(-0.9, 0.9)),
              float(s.rng.uniform(0.012, 0.035)), 2.0)
        t += float(s.rng.uniform(0.9, 3.8))
    return s, dict(rev_sec=6.0, damp=5200, wet=1.2)


def track_abyss():
    """Abismo: quase sem melodia — pedal sub-grave, acordes de segundas menores que incham e somem, vozes que escorregam."""
    bar, bars = 12.0, 8
    s = Song(bar * bars, 707)
    n = s.n
    s.add(0, drone(mf(37), n, s.rng, 0.12), 0.0, 0.075, 0.1)
    s.add(0, drone(mf(25), n, s.rng, 0.2), 0.0, 0.03, 0.0)
    b = bed(n, s.rng, 300.0)
    s.add(0, b, -0.7, 0.055, 0.3)
    s.add(0, np.roll(b, n // 2), 0.7, 0.055, 0.3)
    clusters = [[49, 50, 56], [49, 52, 55], [47, 49, 54, 56], [48, 49, 55], [49, 50, 56, 61], [45, 52, 53], [46, 49, 54],
                [49, 51, 56, 57]]
    for i, ch in enumerate(clusters):
        t = i * bar
        pad_chord(s, t + 1.0, [m + 12 for m in ch], bar - 3.0, 0.07, bright=1400, a=4.5, r=5.0, vib=9.0)
        if i % 2 == 1:
            pad_chord(s, t + 3.0, [ch[0] + 12, ch[-1] + 12], bar - 5.0, 0.055, inst=choir, send=1.0, vowel='oo', a=4.0, r=5.0)
        f0 = mf(57 + (i * 5) % 7)
        s.add(t + 4.0, glide_tone(f0, f0 * (0.84 if i % 2 == 0 else 1.12), 7.0, s.rng), 0.6 if i % 2 else -0.6, 0.075, 1.2)
        if i % 2 == 0:
            s.add(t + 8.5, bell(mf(49 + (i % 3)), 8.0, s.rng, 0.6), -0.4 if i % 4 else 0.4, 0.08, 1.0)
        # batida de coração bem funda, só nos compassos pares
        if i % 2 == 0:
            for off in (0.0, 0.42, 3.0, 3.42, 6.0, 6.42):
                s.add(t + off, taiko(s.rng, 52, 30, 1.0, 0.1), 0.0, 0.1 if off % 3 < 0.1 else 0.065, 0.25)
    return s, dict(rev_sec=7.5, damp=2000, wet=1.3)


TRACKS = {'dungeon': track_dungeon, 'citadel': track_citadel, 'library': track_library, 'tower': track_tower,
          'city': track_city, 'abyss': track_abyss, 'home': track_home}


# ============================================================== masterização e saída

def master(song, rev_sec, damp, wet, target_rms=0.085):
    n = song.n
    rng = np.random.default_rng(999)
    out = np.zeros((2, n))
    m = int(rev_sec * SR)
    pre = int(0.028 * SR)
    for ch in range(2):
        ir = rng.standard_normal(m) * np.exp(-np.linspace(0, 6.9, m))
        ir = spectral(ir, g_lp(damp, 1))
        ir = np.concatenate([np.zeros(pre), ir])
        ir /= math.sqrt(float(np.sum(ir ** 2))) + 1e-12
        out[ch] = np.fft.irfft(np.fft.rfft(song.send[ch]) * np.fft.rfft(ir, n), n)
    y = song.dry + out * wet
    y = spectral(y, lambda f: g_hp(28, 2)(f) * g_lp(11000, 2)(f))
    rms = math.sqrt(float(np.mean(y ** 2))) + 1e-12
    y *= target_rms / rms
    # limitador suave: só encosta nos picos, o resto passa igual
    y = np.tanh(y * 1.6) / 1.6
    pk = float(np.max(np.abs(y)))
    if pk > 0.9:
        y *= 0.9 / pk
    return y


def write_ogg(name, y):
    os.makedirs(TMP_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    wav = os.path.join(TMP_DIR, name + '.wav')
    pcm = (np.clip(y.T, -1, 1) * 32767).astype(np.int16)
    with wave.open(wav, 'wb') as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    ogg = os.path.join(OUT_DIR, name + '.ogg')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '2', ogg], check=True)
    os.remove(wav)
    print('ok', name, round(y.shape[1] / SR, 1), 's', os.path.getsize(ogg) // 1024, 'KB', flush=True)


def merge_sounds_json():
    """Acrescenta as chaves das faixas (stream) sem tocar no resto; grava por arquivo temporário + troca."""
    data = {}
    if os.path.exists(SOUNDS_JSON):
        with open(SOUNDS_JSON, encoding='utf-8') as f:
            txt = f.read().strip()
            data = json.loads(txt) if txt else {}
    for z in ZONES:
        if os.path.exists(os.path.join(OUT_DIR, z + '.ogg')):
            data['ambience.music.' + z] = {'sounds': [{'name': 'isekai_mahou:ambience/music/' + z, 'stream': True}]}
    tmp = SOUNDS_JSON + '.ambience_tmp'
    with open(tmp, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')
    os.replace(tmp, SOUNDS_JSON)
    print('sounds.json:', len(data), 'chaves')


def main():
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if '--json' not in sys.argv:
        for name in ZONES:
            if args and name not in args:
                continue
            song, opts = TRACKS[name]()
            write_ogg(name, master(song, **opts))
    if '--nojson' not in sys.argv:
        merge_sounds_json()


if __name__ == '__main__':
    main()
