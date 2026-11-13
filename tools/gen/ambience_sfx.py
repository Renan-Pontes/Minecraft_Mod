#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Sons PRÓPRIOS das magias (módulo ambience, v1.0 RELEASE), sintetizados do zero com numpy (nada baixado).

Por FAMÍLIA de elemento (fire, water, earth, wind, light, shadow, lightning, ice, nature, arcane) e por TAMANHO:
  cast_s / cast_m / cast_l  (conjuração pequena, média, grande)   impact_s / impact_l  (impacto pequeno, grande)
Saída: assets/isekai_mahou/sounds/ambience/spell/<familia>_<tipo>.ogg (MONO: som posicional) e as chaves
"ambience.spell.<familia>.<tipo>" no sounds.json (ler-modificar-gravar; nunca sobrescreve o resto).

Uso:
    py -I tools/gen/ambience_sfx.py            # tudo
    py -I tools/gen/ambience_sfx.py fire ice   # só algumas famílias
    py -I tools/gen/ambience_sfx.py --json     # só refaz as entradas do sounds.json
"""
import json
import math
import os
import subprocess
import sys
import wave

import numpy as np

SR = 44100
TAU = 2 * math.pi
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'isekai_mahou')
OUT_DIR = os.path.join(ASSETS, 'sounds', 'ambience', 'spell')
SOUNDS_JSON = os.path.join(ASSETS, 'sounds.json')
TMP_DIR = os.path.join(ROOT, 'build', 'ambience_tmp')
rng = np.random.default_rng(2026)

FAMILIES = ['fire', 'water', 'earth', 'wind', 'light', 'shadow', 'lightning', 'ice', 'nature', 'arcane']
KINDS = ['cast_s', 'cast_m', 'cast_l', 'impact_s', 'impact_l']
# alcance (blocos) em que cada tamanho é ouvido
RANGE = {'cast_s': 20, 'cast_m': 32, 'cast_l': 56, 'impact_s': 24, 'impact_l': 48}
# tamanho -> (fator de altura, grave extra, reverb)
PF = [1.0, 0.82, 0.62]
SUB = [0.0, 0.45, 1.0]
REV = [0.14, 0.24, 0.38]
PEAK = [0.68, 0.78, 0.85]
# corpo (RMS da parte audível) que cada tamanho procura
LOUD = [0.2, 0.24, 0.28]


# ------------------------------------------------------------------ utilidades
def N(sec):
    return int(sec * SR)


def noise(n):
    return rng.standard_normal(n)


def filt(x, gain_fn):
    n = len(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    return np.fft.irfft(np.fft.rfft(x) * gain_fn(f), n)


def lp(x, fc, order=2):
    return filt(x, lambda f: 1 / np.sqrt(1 + (f / fc) ** (2 * order)))


def hp(x, fc, order=2):
    return filt(x, lambda f: 1 / np.sqrt(1 + (fc / np.maximum(f, 1e-3)) ** (2 * order)))


def bp(x, fc, q):
    return filt(x, lambda f: np.exp(-0.5 * ((f - fc) / (fc / q)) ** 2))


def unit(x):
    return x / (np.max(np.abs(x)) + 1e-9)


def sweep(x, f0, f1, q, segs=14):
    """Passa-banda com o centro andando de f0 a f1 (whoosh)."""
    n = len(x)
    out = np.zeros(n)
    centers = np.linspace(0, n, segs)
    width = n / (segs - 1)
    t = np.arange(n)
    for i, c in enumerate(centers):
        w = np.clip(1 - np.abs(t - c) / width, 0, None)
        fc = f0 * (f1 / f0) ** (i / (segs - 1))
        out += unit(bp(x, fc, q)) * w
    return out


def glide(f0, f1, n, curve=1.0):
    k = np.linspace(0, 1, n) ** curve
    return TAU * np.cumsum(f0 * (f1 / f0) ** k) / SR


def dec(n, rate):
    return np.exp(-np.linspace(0, rate, n))


def arc(n, peak=0.3, sharp=1.0):
    """Sobe até `peak` (fração) e cai."""
    k = np.linspace(0, 1, n)
    up = np.clip(k / peak, 0, 1) ** sharp
    down = np.clip((1 - k) / (1 - peak), 0, 1) ** 1.6
    return np.minimum(up, 1) * down


def boom(n, f0=70, f1=30, rate=5.0):
    return np.sin(glide(f0, f1, n, 0.5)) * dec(n, rate)


def put(buf, at, sig, gain=1.0):
    s = int(at * SR)
    m = min(len(sig), len(buf) - s)
    if m > 0:
        buf[s:s + m] += sig[:m] * gain


def clicks(n, density, tone=3000.0):
    """Estalos esparsos (crepitar, faíscas, cascalho)."""
    y = (rng.random(n) < density / SR).astype(float) * noise(n)
    return hp(lp(y, tone * 2.5, 1), tone * 0.4, 1) * 6


def ping(f, sec, rate=7.0):
    n = N(sec)
    t = np.arange(n) / SR
    return (np.sin(TAU * f * t) + 0.3 * np.sin(TAU * f * 2.76 * t)) * dec(n, rate)


def reverb(x, sec, mix, damp=3500):
    n = N(sec)
    ir = lp(noise(n) * dec(n, 6.5), damp, 1)
    ir /= math.sqrt(float(np.sum(ir ** 2))) + 1e-9
    m = len(x) + n
    wet = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(ir, m), m)
    return np.pad(x, (0, n)) * (1 - mix) + wet * mix * 1.6


def finish(x, size, rev_sec=0.9, damp=3500):
    y = reverb(x, rev_sec * (0.7 + 0.5 * size), REV[size], damp)
    y = unit(y - np.mean(y))
    # som feito só de estalos fica "fino": quanto menor o corpo, mais a saturação aperta os picos e levanta o resto
    body = math.sqrt(float(np.mean(y[np.abs(y) > 0.02] ** 2))) if np.any(np.abs(y) > 0.02) else 0.1
    drive = min(6.0, max(1.5, 0.2 / max(body, 1e-3)))
    y = np.tanh(y * drive) / math.tanh(drive)
    # corta o silêncio do fim (arquivo menor) e fecha sem estalo
    idx = np.nonzero(np.abs(y) > 0.004)[0]
    y = y[:min(len(y), (idx[-1] if len(idx) else len(y) - 1) + N(0.03))]
    y[:48] *= np.linspace(0, 1, 48)
    fade = min(len(y), N(0.04))
    y[-fade:] *= np.linspace(1, 0, fade)
    # volume parecido entre as famílias: mira no corpo do som, com teto de pico por tamanho
    body = math.sqrt(float(np.mean(y[np.abs(y) > 0.02] ** 2)))
    return y * min(PEAK[size], LOUD[size] / max(body, 1e-3))


# ------------------------------------------------------------------ famílias: cast(size 0..2), impact(size 0 ou 2)
def fire_cast(z):
    n = N([0.5, 0.85, 1.5][z])
    y = sweep(noise(n), 320 * PF[z], 2600 * PF[z], 1.1) * arc(n, 0.35, 1.5)
    y += unit(lp(noise(n), 320, 2) * (1 + 2.5 * unit(lp(noise(n), 28, 1)))) * arc(n, 0.5) * (0.5 + 0.6 * SUB[z])
    y += clicks(n, 60 + 60 * z, 3500) * arc(n, 0.4) * 0.35
    if z == 2:
        put(y, 0.55, boom(N(0.9), 72, 30, 4.0), 1.4)
    return finish(y, z, 0.8, 3000)


def fire_impact(z):
    n = N(0.6 if z == 0 else 1.4)
    y = boom(n, 95 * PF[z], 34, 5.0 if z else 9.0) * (1.0 + SUB[z])
    y += unit(lp(noise(n), 2600, 1)) * dec(n, 16 if z == 0 else 9) * 0.9
    y += clicks(n, 220, 4000) * dec(n, 5) * 0.45
    y += unit(bp(noise(n), 3200, 0.8)) * dec(n, 7) * 0.2
    return finish(y, z, 1.0, 2600)


def water_cast(z):
    n = N([0.5, 0.8, 1.4][z])
    y = sweep(noise(n), 3200 * PF[z], 700 * PF[z], 1.3) * arc(n, 0.2) * 0.8
    for _ in range(6 + 6 * z):
        m = N(float(rng.uniform(0.04, 0.11)))
        f0 = float(rng.uniform(300, 900)) * PF[z]
        b = np.sin(glide(f0, f0 * float(rng.uniform(1.6, 2.6)), m, 0.7)) * np.sin(np.pi * np.linspace(0, 1, m)) ** 2
        put(y, float(rng.uniform(0, n / SR - 0.12)), b, 0.5)
    if z >= 1:
        m = N(0.5)
        ph = glide(190 * PF[z], 85 * PF[z], m, 0.6)
        put(y, 0.12, np.sin(ph + 2.5 * np.sin(TAU * 9 * np.arange(m) / SR)) * dec(m, 4), 0.7 * SUB[z] + 0.3)
    return finish(y, z, 0.8, 4500)


def water_impact(z):
    n = N(0.6 if z == 0 else 1.3)
    y = unit(bp(noise(n), 2400, 0.6)) * dec(n, 13 if z == 0 else 7)
    y += boom(n, 110 * PF[z], 45, 9.0) * (0.7 + SUB[z])
    for _ in range(5 + 3 * z):
        put(y, float(rng.uniform(0.08, n / SR * 0.7)), ping(float(rng.uniform(1400, 3000)), 0.06, 9), 0.18)
    return finish(y, z, 0.9, 4500)


def earth_cast(z):
    n = N([0.5, 0.85, 1.5][z])
    rough = 1 + 3.5 * unit(lp(noise(n), 22, 1))
    y = unit(bp(noise(n), 230 * PF[z], 0.8) * rough) * arc(n, 0.6, 1.2)
    y += unit(lp(noise(n), 90, 2)) * arc(n, 0.7) * (0.4 + SUB[z])
    for _ in range(4 + 4 * z):
        m = N(0.05)
        put(y, float(rng.uniform(0.05, n / SR - 0.08)), unit(lp(noise(m), float(rng.uniform(900, 2200)), 2)) * dec(m, 9), 0.45)
    put(y, n / SR * 0.72, boom(N(0.35), 80 * PF[z], 40, 7.0), 0.8 + SUB[z])
    return finish(y, z, 0.7, 2200)


def earth_impact(z):
    n = N(0.6 if z == 0 else 1.4)
    y = boom(n, 82 * PF[z], 30, 7.0 if z == 0 else 4.5) * (1.3 + SUB[z])
    y += unit(lp(noise(n), 1700, 2)) * dec(n, 22) * 1.0
    y += clicks(n, 90 + 80 * z, 1500) * dec(n, 4.5) * 0.5
    y += unit(lp(noise(n), 140, 2)) * dec(n, 5) * 0.6 * SUB[z]
    return finish(y, z, 0.8, 2000)


def wind_cast(z):
    n = N([0.5, 0.85, 1.5][z])
    half = n // 2
    y = np.concatenate([sweep(noise(half), 500 * PF[z], 3400 * PF[z], 2.6, 9), sweep(noise(n - half), 3400 * PF[z], 900 * PF[z], 2.6, 9)])
    y = y * arc(n, 0.45, 1.3)
    t = np.arange(n) / SR
    wh = np.sin(glide(700 * PF[z], 1500 * PF[z], n, 0.8) + 0.5 * np.sin(TAU * 6.5 * t)) * arc(n, 0.5, 2.0)
    y += wh * 0.1
    y += unit(lp(noise(n), 200, 2)) * arc(n, 0.5) * 0.35 * SUB[z]
    return finish(y, z, 0.9, 5000)


def wind_impact(z):
    n = N(0.5 if z == 0 else 1.2)
    y = unit(bp(noise(n), 1300 * PF[z], 0.7)) * dec(n, 12 if z == 0 else 6)
    y += unit(hp(noise(n), 5000)) * dec(n, 40) * 0.5
    y += sweep(noise(n), 2600, 500, 2.0, 8) * dec(n, 5) * 0.5
    y += boom(n, 90, 40, 8.0) * 0.6 * SUB[z]
    return finish(y, z, 0.9, 5000)


def light_cast(z):
    n = N([0.55, 0.9, 1.6][z])
    y = np.zeros(n)
    base = 880 * PF[z]
    steps = [1.0, 1.26, 1.5, 2.0, 2.52, 3.0][:4 + z]
    gap = (n / SR - 0.35) / len(steps)
    for i, r in enumerate(steps):
        put(y, i * gap, ping(base * r, 0.5 + 0.2 * z, 6.0), 0.55)
    y += unit(hp(noise(n), 7000)) * (rng.random(n) < 0.02) * 5 * arc(n, 0.6) * 0.25
    t = np.arange(n) / SR
    y += np.sin(TAU * base * 0.5 * t) * arc(n, 0.7, 2.0) * 0.25
    if z == 2:
        y += (np.sin(TAU * base * 0.25 * t) + np.sin(TAU * base * 0.375 * t)) * arc(n, 0.8, 2.0) * 0.3
    return finish(y, z, 1.4, 7000)


def light_impact(z):
    n = N(0.7 if z == 0 else 1.5)
    y = np.zeros(n)
    base = 1046 * PF[z]
    for r in (1.0, 1.26, 1.5, 2.0, 3.0):
        put(y, 0.0, ping(base * r, n / SR, 5.0 if z else 8.0), 0.4)
    y += unit(hp(noise(n), 6000)) * dec(n, 12) * 0.45
    y += boom(n, 120, 60, 9.0) * 0.5 * SUB[z]
    return finish(y, z, 1.5, 7000)


def shadow_cast(z):
    n = N([0.55, 0.9, 1.6][z])
    t = np.arange(n) / SR
    f = 82 * PF[z]
    saw = lambda fr: 2 * ((fr * t) % 1.0) - 1
    swell = unit(lp(saw(f) + saw(f * 1.012) + saw(f * 0.5) + saw(f * 1.498), 520, 2))
    rise = (np.linspace(0, 1, n) ** 2.4) * np.clip((1 - np.linspace(0, 1, n)) / 0.06, 0, 1)
    y = swell * rise
    wsp = unit(filt(noise(n), lambda fq: np.exp(-0.5 * ((fq - 900) / 320) ** 2) + 0.7 * np.exp(-0.5 * ((fq - 2500) / 500) ** 2)))
    y += wsp * (0.5 + 0.5 * unit(lp(noise(n), 7, 1)) * 2).clip(0, 2) * rise * 0.4
    y += np.sin(glide(300 * PF[z], 70 * PF[z], n, 0.6)) * rise * 0.3
    put(y, max(0.0, n / SR - 0.2), boom(N(0.2), 60, 30, 5.0), 0.5 * SUB[z])
    return finish(y, z, 1.2, 1800)


def shadow_impact(z):
    n = N(0.7 if z == 0 else 1.5)
    t = np.arange(n) / SR
    y = boom(n, 120 * PF[z], 28, 6.0 if z == 0 else 4.0) * (1.2 + SUB[z])
    y += np.sin(glide(340 * PF[z], 55, n, 0.5)) * (1 + 0.6 * np.sin(TAU * 17 * t)) * dec(n, 6) * 0.45
    y += unit(lp(noise(n), 700, 2)) * dec(n, 9) * 0.7
    y += unit(bp(noise(n), 2200, 1.0)) * dec(n, 5) * 0.12
    return finish(y, z, 1.3, 1700)


def lightning_cast(z):
    n = N([0.45, 0.75, 1.4][z])
    t = np.arange(n) / SR
    m = N(0.2 + 0.08 * z)
    zap = (2 * ((glide(2600 * PF[z], 260 * PF[z], m, 0.6) / TAU) % 1.0) - 1) * np.sign(np.sin(TAU * 96 * np.arange(m) / SR))
    y = np.zeros(n)
    put(y, 0.02, zap * dec(m, 3.5), 1.2)
    y += clicks(n, 420, 5000) * arc(n, 0.15) * 0.45
    y += unit(bp(noise(n), 1700 * PF[z], 0.7)) * arc(n, 0.15) * 0.55
    buzz = (2 * ((110 * PF[z] * t) % 1.0) - 1) * (0.5 + 0.5 * np.sign(np.sin(TAU * 31 * t)))
    y += unit(hp(buzz, 200)) * arc(n, 0.2) * 0.7
    y += unit(hp(noise(n), 4000)) * dec(n, 30) * 0.4
    if z >= 1:
        y += unit(lp(noise(n), 150, 2)) * arc(n, 0.3) * 0.9 * SUB[z]
    return finish(y, z, 0.9, 5000)


def lightning_impact(z):
    n = N(0.6 if z == 0 else 1.6)
    y = noise(n) * dec(n, 60) * 1.0
    y += unit(bp(noise(n), 1900, 0.6)) * dec(n, 10 if z == 0 else 8) * 1.1
    y += clicks(n, 300, 5000) * dec(n, 7) * 0.5
    roll = unit(lp(noise(n), 130, 2) * (1 + 1.5 * unit(lp(noise(n), 9, 1))))
    y += roll * dec(n, 3.5) * 1.6 * SUB[z]
    y += boom(n, 100, 40, 8.0) * (0.9 + SUB[z])
    return finish(y, z, 1.3, 3500)


def ice_cast(z):
    n = N([0.5, 0.8, 1.4][z])
    y = unit(bp(noise(n), 7200, 1.2)) * arc(n, 0.7, 1.6) * 0.35
    for _ in range(7 + 6 * z):
        put(y, float(rng.uniform(0, n / SR - 0.15)), ping(float(rng.uniform(2200, 6200)) * (0.8 + 0.2 * PF[z]), 0.16, 8), 0.3)
    y += clicks(n, 120, 6000) * arc(n, 0.6) * 0.3
    t = np.arange(n) / SR
    y += (np.sin(TAU * 1320 * PF[z] * t) + 0.5 * np.sin(TAU * 1980 * PF[z] * t)) * arc(n, 0.75, 2.0) * 0.14
    if z == 2:
        put(y, 0.9, boom(N(0.4), 90, 45, 6.0), 0.6)
    return finish(y, z, 1.1, 8000)


def ice_impact(z):
    n = N(0.6 if z == 0 else 1.3)
    y = unit(hp(noise(n), 2200)) * dec(n, 45) * 1.2
    for _ in range(18 + 22 * (1 if z else 0)):
        put(y, float(rng.uniform(0, 0.22 + 0.2 * (1 if z else 0))), ping(float(rng.uniform(1500, 7000)), float(rng.uniform(0.05, 0.3)), 9),
            float(rng.uniform(0.12, 0.3)))
    y += boom(n, 105 * PF[z], 45, 9.0) * (0.5 + SUB[z])
    return finish(y, z, 1.0, 8000)


def nature_cast(z):
    n = N([0.5, 0.85, 1.5][z])
    grains = np.clip(unit(lp(noise(n), 35, 1)) * 2.2, 0, None)
    y = unit(hp(noise(n), 2800)) * grains * arc(n, 0.5) * 0.55
    t = np.arange(n) / SR
    f = 392 * PF[z]
    y += (np.sin(glide(f, f * 1.5, n, 0.5)) + 0.2 * np.sin(2 * glide(f, f * 1.5, n, 0.5))) * arc(n, 0.6, 1.5) * 0.3
    for i in range(2 + z):
        m = N(0.18)
        put(y, 0.04 + i * 0.16, (np.sin(TAU * f * 0.5 * (1 + 0.25 * i) * np.arange(m) / SR)) * dec(m, 12) + unit(bp(noise(m), 520, 4)) * dec(m, 20) * 0.6, 0.6)
    y += unit(lp(noise(n), 120, 2)) * arc(n, 0.6) * 0.5 * SUB[z]
    return finish(y, z, 0.9, 5000)


def nature_impact(z):
    n = N(0.55 if z == 0 else 1.2)
    t = np.arange(n) / SR
    y = unit(bp(noise(n), 430 * PF[z], 3.5)) * dec(n, 24) * 1.2 + np.sin(TAU * 165 * PF[z] * t) * dec(n, 16)
    y += unit(hp(noise(n), 3000)) * np.clip(unit(lp(noise(n), 30, 1)) * 2, 0, None) * dec(n, 5) * 0.5
    y += boom(n, 85, 38, 7.0) * (0.3 + 0.9 * SUB[z])
    return finish(y, z, 0.8, 4500)


def arcane_cast(z):
    n = N([0.5, 0.85, 1.5][z])
    t = np.arange(n) / SR
    car = glide(300 * PF[z], 880 * PF[z], n, 1.4)
    idx = 3.0 * (1 - np.linspace(0, 1, n)) + 0.3
    y = np.sin(car + idx * np.sin(car * 2.0)) * arc(n, 0.8, 1.6) * 0.6
    y += np.sin(car * 1.5 + 0.3) * arc(n, 0.85, 2.2) * 0.25
    y += unit(hp(noise(n), 6500)) * (rng.random(n) < 0.03) * 5 * arc(n, 0.7) * 0.2
    put(y, n / SR * 0.8, ping(1320 * PF[z], 0.3, 7), 0.35)
    y += np.sin(TAU * 55 * t) * arc(n, 0.8, 2.0) * 0.5 * SUB[z]
    return finish(y, z, 1.1, 6000)


def arcane_impact(z):
    n = N(0.6 if z == 0 else 1.4)
    click = np.zeros(n)
    click[:N(0.004)] = noise(N(0.004))
    y = unit(bp(click, 720 * PF[z], 14)) + 0.6 * unit(bp(click, 1440 * PF[z], 14))
    y += np.sin(glide(640 * PF[z], 150 * PF[z], n, 0.5)) * dec(n, 7) * 0.7
    y += unit(hp(noise(n), 6000)) * dec(n, 10) * 0.25
    y += boom(n, 100, 36, 6.0) * (0.5 + SUB[z])
    return finish(y, z, 1.2, 6000)


# ------------------------------------------------------------------ saída
def write(name, y):
    os.makedirs(TMP_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    wav = os.path.join(TMP_DIR, name + '.wav')
    with wave.open(wav, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(y, -1, 1) * 32767).astype(np.int16).tobytes())
    ogg = os.path.join(OUT_DIR, name + '.ogg')
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-ac', '1', '-c:a', 'libvorbis', '-q:a', '3', ogg], check=True)
    os.remove(wav)
    print('ok', name, round(len(y) / SR, 2), 's', os.path.getsize(ogg) // 1024, 'KB', flush=True)


def merge_sounds_json():
    data = {}
    if os.path.exists(SOUNDS_JSON):
        with open(SOUNDS_JSON, encoding='utf-8') as f:
            txt = f.read().strip()
            data = json.loads(txt) if txt else {}
    for fam in FAMILIES:
        for kind in KINDS:
            if not os.path.exists(os.path.join(OUT_DIR, fam + '_' + kind + '.ogg')):
                continue
            sub = 'subtitles.isekai_mahou.ambience.spell_' + ('cast' if kind.startswith('cast') else 'impact')
            # o alcance de quem OUVE vem daqui (attenuation_distance), igual ao de AmbienceSounds no Java
            data['ambience.spell.' + fam + '.' + kind] = {
                'subtitle': sub, 'sounds': [{'name': 'isekai_mahou:ambience/spell/' + fam + '_' + kind, 'stream': False,
                                             'attenuation_distance': RANGE[kind]}]}
    tmp = SOUNDS_JSON + '.ambience_tmp'
    with open(tmp, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')
    os.replace(tmp, SOUNDS_JSON)
    print('sounds.json:', len(data), 'chaves')


def main():
    only = [a for a in sys.argv[1:] if not a.startswith('--')]
    if '--json' not in sys.argv:
        g = globals()
        for fam in FAMILIES:
            if only and fam not in only:
                continue
            for z, kind in enumerate(('cast_s', 'cast_m', 'cast_l')):
                write(fam + '_' + kind, g[fam + '_cast'](z))
            write(fam + '_impact_s', g[fam + '_impact'](0))
            write(fam + '_impact_l', g[fam + '_impact'](2))
    if '--nojson' not in sys.argv:
        merge_sounds_json()


if __name__ == '__main__':
    main()
