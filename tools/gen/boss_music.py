#!/usr/bin/env python3
"""
Gerador das MÚSICAS TEMA dos chefes (módulo boss_music, v1.3).

Tudo é sintetizado aqui com numpy — composição ORIGINAL (progressões, motivos e ritmos escritos neste arquivo),
nada copiado de músicas existentes. Saída: assets/isekai_mahou/sounds/music/<tema>.ogg (ffmpeg libvorbis q3, 32 kHz,
estéreo) e as entradas "boss_music.<tema>" no sounds.json (mescla ler-modificar-gravar; nunca sobrescreve o resto).

Uso:
    py tools/gen/boss_music.py                 # todos os temas
    py tools/gen/boss_music.py seraph lich     # só alguns
    py tools/gen/boss_music.py --jobs 4        # em paralelo (cada tema usa ~400 MB)

Motor (resumo):
  - osciladores por wavetable com banda limitada (sem aliasing feio), vibrato por nota;
  - instrumentos: cordas (serras desafinadas), metais (mistura escuro/brilhante), coro (fonte + formantes),
    piano/celesta/harpa (parciais inarmônicos decaindo), órgão, baixo, guitarra distorcida, ney (flauta do deserto);
  - bateria sintetizada: bumbo, caixa, chimbal, prato, taikos, tímpanos, gongo, tique-taque, djembê, pandeiro;
  - barramentos com filtro espectral (FFT), saturação, sidechain do bumbo, eco pingue-pongue;
  - reverb FDN (8 linhas de atraso, matriz de Hadamard, amortecimento) processado em blocos;
  - loop sem emenda: a cauda depois do fim é dobrada para o começo; limitador de pico circular na masterização.
"""
import json
import math
import os
import re
import subprocess
import sys
import wave

import numpy as np

SR = 32000
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
ASSETS = os.path.join(ROOT, 'src', 'main', 'resources', 'assets', 'isekai_mahou')
OUT_DIR = os.path.join(ASSETS, 'sounds', 'music')
SOUNDS_JSON = os.path.join(ASSETS, 'sounds.json')
TMP_DIR = os.path.join(ROOT, 'build', 'boss_music_tmp')

TAU = 2 * math.pi


def mf(m):
    return 440.0 * 2.0 ** ((m - 69) / 12.0)


# ============================================================== wavetables

TN = 2048


def _tables(amp):
    """Uma tabela de ciclo único por oitava, cada uma com harmônicos só até ~Nyquist (sem aliasing)."""
    ks = np.arange(1, TN // 2)
    amps = np.array([amp(int(k)) for k in ks], dtype=float)
    out = []
    peak = None
    for b in range(12):
        ftop = 27.5 * 2 ** b
        kmax = int(max(1, min(TN // 2 - 1, (SR * 0.46) / ftop)))
        spec = np.zeros(TN // 2 + 1, dtype=complex)
        a = amps.copy()
        a[kmax:] = 0
        spec[1:TN // 2] = -1j * a
        tab = np.fft.irfft(spec, TN)
        if peak is None:
            peak = np.max(np.abs(tab)) or 1.0
        out.append(tab / peak)
    return out


SAW = _tables(lambda k: 1.0 / k)
SQR = _tables(lambda k: 1.0 / k if k % 2 else 0.0)
TRI = _tables(lambda k: ((-1) ** ((k - 1) // 2)) / k ** 2 if k % 2 else 0.0)
SINE = _tables(lambda k: 1.0 if k == 1 else 0.0)
SOFT = _tables(lambda k: 1.0 / k ** 1.55)
BRASS_D = _tables(lambda k: 1.0 / k ** 2.3)
BRASS_B = _tables(lambda k: (1.0 / k ** 0.85) if k < 48 else 0.0)
REED = _tables(lambda k: (1.0 / k) if k % 2 else (0.15 / k))
ORGAN = _tables(lambda k: {1: 1, 2: 0.75, 3: 0.55, 4: 0.45, 6: 0.32, 8: 0.25, 10: 0.1, 12: 0.08, 16: 0.05}.get(k, 0.0))
FLUTE = _tables(lambda k: {1: 1, 2: 0.22, 3: 0.12, 4: 0.035, 5: 0.02}.get(k, 0.0))
VOX = _tables(lambda k: 1.0 / k ** 1.05)


def osc(tabs, f, n, ph0=0.0):
    """Oscilador de wavetable; f pode ser escalar ou vetor (vibrato/glissando)."""
    if np.ndim(f) == 0:
        fv = np.full(n, float(f))
    else:
        fv = np.asarray(f, dtype=float)[:n]
    fmax = float(np.max(fv)) if n else 1.0
    b = 0 if fmax <= 27.5 else min(11, int(math.ceil(math.log2(fmax / 27.5))))
    tab = tabs[b]
    ph = (ph0 + np.cumsum(fv) / SR) % 1.0
    idx = ph * TN
    i0 = idx.astype(np.int64)
    fr = idx - i0
    i0 &= TN - 1
    i1 = (i0 + 1) & (TN - 1)
    return tab[i0] * (1 - fr) + tab[i1] * fr


def vib_freq(f, n, rng, rate=5.2, cents=14.0, delay=0.25, jitter=3.0):
    """Frequência com vibrato que entra devagar + um pouco de deriva aleatória (soa humano)."""
    t = np.arange(n) / SR
    depth = cents * np.clip((t - delay) / 0.4, 0, 1)
    p = rng.uniform(0, TAU)
    c = depth * np.sin(TAU * rate * t + p)
    if jitter > 0:
        k = max(2, n // 2000 + 2)
        pts = rng.normal(0, jitter, k)
        c += np.interp(np.linspace(0, k - 1, n), np.arange(k), pts)
    return f * 2.0 ** (c / 1200.0)


def adsr(n_on, a, d, s, r):
    A = max(1, int(a * SR))
    D = max(1, int(d * SR))
    R = max(1, int(r * SR))
    t = np.arange(n_on, dtype=float)
    e = np.where(t < A, t / A, s + (1 - s) * np.exp(-(t - A) / (D / 3.0)))
    last = e[-1] if n_on > 0 else 0.0
    rel = last * np.exp(-np.arange(R) / (R / 5.0))
    return np.concatenate([e, rel])


# ============================================================== filtros espectrais

def r_lp(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (f / fc) ** (2 * order))


def r_hp(fc, order=2):
    return lambda f: 1.0 / np.sqrt(1.0 + (fc / np.maximum(f, 1e-3)) ** (2 * order))


def r_peak(fc, oct_bw, db):
    return lambda f: 10 ** (db / 20.0 * np.exp(-(np.log2(np.maximum(f, 1.0) / fc)) ** 2 / (2 * (oct_bw / 2.0) ** 2)))


def r_shelf(fc, db, high=True):
    """Prateleira suave (meia oitava de transição) — agudos/graves sobem ou descem 'db'."""
    def resp(f):
        k = 1.0 / (1.0 + np.exp(-3.0 * np.log2(np.maximum(f, 1.0) / fc)))
        return 10 ** (db / 20.0 * (k if high else 1 - k))
    return resp


def r_res(fc, bw):
    return lambda f: 1.0 / np.sqrt(1.0 + ((f - fc) / (bw / 2.0)) ** 2)


VOWELS = {
    # (frequência, largura, ganho) — misturas de vozes graves e agudas
    'a': [(760, 110, 1.0), (1150, 130, 0.55), (2750, 170, 0.28), (3700, 220, 0.12)],
    'o': [(470, 90, 1.0), (820, 110, 0.4), (2750, 160, 0.1)],
    'u': [(330, 70, 1.0), (720, 90, 0.18), (2500, 200, 0.05)],
    'e': [(420, 80, 1.0), (1700, 120, 0.42), (2650, 160, 0.22)],
}


def r_vowel(v):
    fs = VOWELS[v]

    def resp(f):
        out = 0.04 + np.zeros_like(f)
        for fc, bw, g in fs:
            out = out + g * r_res(fc, bw * 1.6)(f)
        return out
    return resp


def spec_filter(x, *resps, circular=False):
    """Filtro de fase zero no domínio da frequência (x: 1-D ou (2, n)). circular=True: convolução circular (loops)."""
    if not resps:
        return x
    n = x.shape[-1]
    nf = n if circular else 1 << int(math.ceil(math.log2(max(n, 2))))
    X = np.fft.rfft(x, nf, axis=-1)
    f = np.fft.rfftfreq(nf, 1.0 / SR)
    h = np.ones_like(f)
    for r in resps:
        h = h * r(f)
    X *= h
    return np.fft.irfft(X, nf, axis=-1)[..., :n].astype(x.dtype)


# ============================================================== instrumentos (devolvem mono 1-D ou estéreo (2, n))

def _stereo(l, r):
    n = max(len(l), len(r))
    out = np.zeros((2, n))
    out[0, :len(l)] = l
    out[1, :len(r)] = r
    return out


def i_strings(m, dur, vel, rng, voices=4, att=0.28, rel=0.6, cents=9, table=None):
    """Seção de cordas: serras suaves desafinadas, vibrato independente por voz, abertas em estéreo."""
    n_on = int(dur * SR)
    env = adsr(n_on, att, 0.4, 0.85, rel)
    n = len(env)
    f = mf(m)
    tab = table or SOFT
    l = np.zeros(n)
    r = np.zeros(n)
    for v in range(voices):
        det = (v - (voices - 1) / 2) * cents / max(1, voices - 1) * 2
        fv = vib_freq(f * 2 ** (det / 1200), n, rng, rate=rng.uniform(4.6, 5.8), cents=11, delay=0.2)
        s = osc(tab, fv, n, rng.uniform())
        (l if v % 2 == 0 else r)[:] += s
    g = vel * 0.5 / voices * 2
    return _stereo(l * env * g, r * env * g)


def i_stacc(m, dur, vel, rng):
    """Cordas em staccato/spiccato para ostinatos épicos."""
    n_on = max(int(min(dur, 0.22) * SR), 200)
    t = np.arange(n_on + int(0.08 * SR)) / SR
    env = np.minimum(t / 0.006, 1) * np.exp(-t / 0.09)
    n = len(t)
    f = mf(m)
    s = osc(SAW, f * 2 ** (rng.normal(0, 4) / 1200), n, rng.uniform()) + osc(SAW, f * 2 ** (rng.normal(0, 4) / 1200 + 0.006), n, rng.uniform())
    return s * env * vel * 0.45


def i_tremolo(m, dur, vel, rng):
    """Cordas em trêmolo (arco rápido): tensão."""
    st = i_strings(m, dur, vel, rng, voices=3, att=0.12, rel=0.3)
    t = np.arange(st.shape[1]) / SR
    am = 0.6 + 0.4 * np.abs(np.sin(TAU * 7.5 * t + rng.uniform(0, 3)))
    return st * am


def i_brass(m, dur, vel, rng, voices=3, bend=0.0, wobble=1.0, bright=1.0):
    """Metais: mistura de onda escura e brilhante — o brilho sobe no ataque (o 'blat') e com a força da nota.
    bend = cents por segundo para baixo (Barragan envelhecendo); wobble multiplica o vibrato."""
    n_on = int(dur * SR)
    env = adsr(n_on, 0.05, 0.3, 0.8, 0.25)
    n = len(env)
    t = np.arange(n) / SR
    f = mf(m)
    br = np.clip((0.35 + 0.65 * vel) * bright * (0.55 + 0.45 * np.exp(-t / 0.18)) + 0.25 * np.clip(t / max(dur, 0.1), 0, 1) * vel, 0, 1)
    l = np.zeros(n)
    r = np.zeros(n)
    for v in range(voices):
        det = (v - (voices - 1) / 2) * 6
        fv = vib_freq(f * 2 ** (det / 1200), n, rng, rate=5.0, cents=9 * wobble, delay=0.35, jitter=2 * wobble)
        if bend:
            fv = fv * 2 ** (-(bend * t) / 1200)
        ph = rng.uniform()
        s = osc(BRASS_D, fv, n, ph) * (1 - br) + osc(BRASS_B, fv, n, ph) * br * 0.8
        (l if v % 2 == 0 else r)[:] += s
    g = vel * 0.75 / voices * 2
    return _stereo(l * env * g, r * env * g)


def i_horn_stab(m, dur, vel, rng):
    return i_brass(m, min(dur, 0.35), vel, rng, voices=3, bright=1.2)


def i_choir(m, dur, vel, rng, voices=4, att=0.35):
    """Coro: fonte vocal com vibrato; as vogais vêm do filtro de formantes do barramento."""
    n_on = int(dur * SR)
    env = adsr(n_on, att, 0.5, 0.9, 0.7)
    n = len(env)
    f = mf(m)
    l = np.zeros(n)
    r = np.zeros(n)
    for v in range(voices):
        det = rng.normal(0, 7)
        fv = vib_freq(f * 2 ** (det / 1200), n, rng, rate=rng.uniform(4.8, 5.8), cents=18, delay=0.15, jitter=5)
        s = osc(VOX, fv, n, rng.uniform())
        (l if v % 2 == 0 else r)[:] += s
    br = np.random.default_rng(int(rng.integers(1 << 30))).normal(0, 1, n) * 0.04
    g = vel * 0.6 / voices * 2
    return _stereo((l + br) * env * g, (r + br) * env * g)


def i_chant(m, dur, vel, rng):
    """Grito curto de coro ('Hey!') para os temas tribais."""
    c = i_choir(m, min(dur, 0.3), vel * 1.2, rng, voices=4, att=0.02)
    t = np.arange(c.shape[1]) / SR
    return c * np.exp(-t / 0.25)


def _partials(f, n, ratios, amps, taus, rng, detune=0.0):
    t = np.arange(n) / SR
    out = np.zeros(n)
    for rr, a, tau in zip(ratios, amps, taus):
        fk = f * rr
        if fk > SR * 0.45:
            continue
        ph = rng.uniform(0, TAU)
        e = np.exp(-t / tau)
        out += a * e * np.sin(TAU * fk * t + ph)
        if detune:
            out += a * 0.6 * e * np.sin(TAU * fk * (1 + detune) * t + ph * 1.3)
    return out


def i_piano(m, dur, vel, rng):
    """Piano: parciais levemente inarmônicos (corda rígida), agudos somem rápido, duas cordas que 'batem', abafador."""
    f = mf(m)
    tau0 = 0.5 + 2.2 * 2 ** ((60 - m) / 18.0)
    n_on = int(dur * SR)
    n = int(min(n_on + 0.4 * SR, (tau0 * 4 + 0.4) * SR))
    n = max(n, 400)
    B = 0.00035
    ks = np.arange(1, 15)
    ratios = ks * np.sqrt(1 + B * ks ** 2)
    hard = 0.7 + 0.6 * vel
    amps = 1.0 / ks ** (1.6 - 0.6 * vel) * np.where(ks < 3, 1.0, hard)
    taus = tau0 / (1 + 0.45 * (ks - 1))
    s = _partials(f, n, ratios, amps, taus, rng, detune=0.0006)
    t = np.arange(n) / SR
    damp = np.where(t < dur, 1.0, np.exp(-(t - dur) / 0.09))
    att = np.minimum(t / 0.002, 1)
    ham = np.random.default_rng(int(rng.integers(1 << 30))).normal(0, 1, n) * np.exp(-t / 0.004) * 0.08 * vel
    return (s * att * damp + ham) * vel * 0.32


def i_celesta(m, dur, vel, rng):
    """Celesta / caixinha de música: sino quase puro com um parcial metálico."""
    f = mf(m)
    n = int(2.6 * SR)
    s = _partials(f, n, [1, 2.0, 3.0, 4.07, 6.8], [1, 0.12, 0.25, 0.18, 0.05], [1.2, 0.5, 0.35, 0.25, 0.12], rng)
    t = np.arange(n) / SR
    return s * np.minimum(t / 0.0015, 1) * vel * 0.3


def i_bell(m, dur, vel, rng):
    """Sino grande (tubular): parciais inarmônicos longos."""
    f = mf(m)
    n = int(5.0 * SR)
    s = _partials(f, n, [0.5, 1, 1.19, 1.56, 2.0, 2.51, 2.66, 3.01, 4.1], [0.25, 1, 0.5, 0.35, 0.5, 0.25, 0.2, 0.15, 0.08],
                  [3.5, 3.0, 2.2, 1.8, 1.6, 1.1, 1.0, 0.8, 0.5], rng)
    t = np.arange(n) / SR
    return s * np.minimum(t / 0.002, 1) * vel * 0.22


def i_harp(m, dur, vel, rng):
    f = mf(m)
    n = int(2.2 * SR)
    ks = np.arange(1, 9)
    s = _partials(f, n, ks * 1.0003, 1.0 / ks ** 1.4, 1.4 / (1 + 0.6 * (ks - 1)), rng)
    t = np.arange(n) / SR
    return s * np.minimum(t / 0.002, 1) * vel * 0.3


def i_pizz(m, dur, vel, rng):
    f = mf(m)
    n = int(0.9 * SR)
    ks = np.arange(1, 8)
    s = _partials(f, n, ks.astype(float), 1.0 / ks ** 1.2, 0.35 / (1 + 0.7 * (ks - 1)), rng)
    t = np.arange(n) / SR
    return s * np.minimum(t / 0.003, 1) * vel * 0.42


def i_organ(m, dur, vel, rng):
    """Órgão de tubos: registros somados + sub-oitava + leve trêmulo."""
    n_on = int(dur * SR)
    env = adsr(n_on, 0.025, 0.1, 0.95, 0.18)
    n = len(env)
    t = np.arange(n) / SR
    f = mf(m)
    s = osc(ORGAN, f, n, rng.uniform()) + 0.35 * osc(SINE, f / 2, n) + 0.5 * osc(ORGAN, f * 1.002, n, rng.uniform())
    trem = 1 + 0.07 * np.sin(TAU * 5.8 * t)
    return s * env * trem * vel * 0.28


def i_reed(m, dur, vel, rng):
    """Palheta (fagote/clarinete) — a voz debochada do Rei Mímico."""
    n_on = int(dur * SR)
    env = adsr(n_on, 0.03, 0.15, 0.8, 0.08)
    n = len(env)
    fv = vib_freq(mf(m), n, rng, rate=5.5, cents=10, delay=0.2)
    return osc(REED, fv, n, rng.uniform()) * env * vel * 0.45


def i_ney(m, dur, vel, rng):
    """Flauta do deserto (ney): ataque com 'scoop' de afinação, sopro e vibrato largo."""
    n_on = int(dur * SR)
    env = adsr(n_on, 0.12, 0.3, 0.85, 0.35)
    n = len(env)
    t = np.arange(n) / SR
    fv = vib_freq(mf(m), n, rng, rate=4.6, cents=24, delay=0.3, jitter=4) * 2 ** (-45 * np.exp(-t / 0.06) / 1200)
    tone = osc(FLUTE, fv, n, rng.uniform())
    nz = np.random.default_rng(int(rng.integers(1 << 30))).normal(0, 1, n)
    nz = np.diff(nz, prepend=0) * 0.06
    return (tone + nz * (0.5 + 0.5 * np.exp(-t / 0.15))) * env * vel * 0.5


def i_lead(m, dur, vel, rng, glide=0.0):
    """Lead de sintetizador agressivo (duas serras, leve quadrada uma oitava abaixo)."""
    n_on = int(dur * SR)
    env = adsr(n_on, 0.008, 0.25, 0.75, 0.12)
    n = len(env)
    f = mf(m)
    fv = vib_freq(f, n, rng, rate=6.0, cents=16, delay=0.3, jitter=1)
    s = osc(SAW, fv * 2 ** (7 / 1200), n, rng.uniform()) + osc(SAW, fv * 2 ** (-7 / 1200), n, rng.uniform()) + 0.4 * osc(SQR, fv / 2, n)
    return np.tanh(s * 1.4) * env * vel * 0.38


def i_pad(m, dur, vel, rng, att=1.2):
    """Pad de sintetizador: 5 serras desafinadas, ataque lento."""
    n_on = int(dur * SR)
    env = adsr(n_on, att, 1.0, 0.9, 1.4)
    n = len(env)
    f = mf(m)
    l = np.zeros(n)
    r = np.zeros(n)
    for v, c in enumerate((-14, -6, 0, 7, 15)):
        fv = vib_freq(f * 2 ** (c / 1200), n, rng, rate=0.3 + 0.1 * v, cents=4, delay=0, jitter=0)
        s = osc(SAW, fv, n, rng.uniform())
        if v != 2:
            (l if v % 2 == 0 else r)[:] += s
        else:
            l += s * 0.7
            r += s * 0.7
    g = vel * 0.22
    return _stereo(l * env * g, r * env * g)


def i_glass(m, dur, vel, rng):
    """Pad vítreo/gelado (Las Noches): senoides + triângulo uma oitava acima, muito lento."""
    n_on = int(dur * SR)
    env = adsr(n_on, 2.5, 1.0, 0.9, 3.0)
    n = len(env)
    f = mf(m)
    l = osc(SINE, vib_freq(f, n, rng, rate=0.2, cents=5, delay=0, jitter=0), n, rng.uniform()) + 0.3 * osc(TRI, f * 2.003, n, rng.uniform())
    r = osc(SINE, vib_freq(f * 1.0015, n, rng, rate=0.27, cents=5, delay=0, jitter=0), n, rng.uniform()) + 0.3 * osc(TRI, f * 1.997, n, rng.uniform())
    return _stereo(l * env * vel * 0.3, r * env * vel * 0.3)


def i_bass(m, dur, vel, rng, kind='synth'):
    n_on = int(dur * SR)
    f = mf(m)
    if kind == 'sub':
        env = adsr(n_on, 0.02, 0.2, 0.9, 0.12)
        n = len(env)
        return (osc(SINE, f, n) + 0.25 * osc(TRI, f, n)) * env * vel * 0.8
    if kind == 'pick':
        env = adsr(n_on, 0.004, 0.25, 0.65, 0.07)
        n = len(env)
        s = osc(SAW, f, n, rng.uniform()) * 0.6 + osc(SINE, f, n) * 0.7
        return np.tanh(s * 2.2) * env * vel * 0.6
    if kind == 'drone':
        env = adsr(n_on, 1.5, 1.0, 0.95, 2.0)
        n = len(env)
        t = np.arange(n) / SR
        s = osc(SAW, f, n, rng.uniform()) * (0.5 + 0.2 * np.sin(TAU * 0.11 * t)) + osc(SINE, f, n) * 0.8 + osc(SAW, f * 1.004, n, rng.uniform()) * 0.3
        return s * env * vel * 0.5
    env = adsr(n_on, 0.006, 0.18, 0.7, 0.08)
    n = len(env)
    s = 0.55 * osc(SAW, f, n, rng.uniform()) + 0.5 * osc(SQR, f, n) * 0.5 + 0.8 * osc(SINE, f, n)
    return s * env * vel * 0.55


def i_power(m, dur, vel, rng, mute=False):
    """Guitarra distorcida: power chord (tônica, quinta, oitava) por um drive pesado. mute = palm mute."""
    n_on = int(dur * SR)
    if mute:
        n = int(min(dur, 0.16) * SR) + int(0.05 * SR)
        t = np.arange(n) / SR
        env = np.minimum(t / 0.002, 1) * np.exp(-t / 0.07)
    else:
        env = adsr(n_on, 0.003, 0.4, 0.85, 0.1)
        n = len(env)
    f = mf(m)
    s = np.zeros(n)
    for iv, a in ((0, 1.0), (7, 0.8), (12, 0.6)):
        for d in (-5, 6):
            s += a * osc(SAW if not mute else SOFT, f * 2 ** ((iv * 100 + d) / 1200), n, rng.uniform())
    s = np.tanh(s * (5.0 if not mute else 4.0))
    return s * env * vel * 0.32


# ============================================================== bateria sintetizada

def _noise(n, seed):
    return np.random.default_rng(seed).normal(0, 1, n)


def d_kick(tune=48.0, decay=0.32, click=0.3, drive=1.7):
    n = int((decay * 4 + 0.05) * SR)
    t = np.arange(n) / SR
    f = tune + tune * 2.6 * np.exp(-t / 0.026)
    body = np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t / decay)
    cl = spec_filter(_noise(n, 11), r_lp(5000)) * np.exp(-t / 0.002) * click
    return np.tanh(drive * (body + cl)) / math.tanh(drive)


def d_snare(tone=190.0, decay=0.15, snap=0.9):
    n = int(0.7 * SR)
    t = np.arange(n) / SR
    fdrop = tone * (1 + 0.25 * np.exp(-t / 0.01))
    body = (np.sin(TAU * np.cumsum(fdrop) / SR) + 0.45 * np.sin(TAU * np.cumsum(fdrop * 1.63) / SR)) * np.exp(-t / 0.055)
    nz = spec_filter(_noise(n, 12), r_hp(1500), r_lp(9000, 1)) * np.exp(-t / decay) * snap
    x = 0.8 * body + nz
    return x / np.max(np.abs(x))


def d_hat(open_=False):
    n = int((0.5 if open_ else 0.12) * SR)
    t = np.arange(n) / SR
    met = sum(np.sign(np.sin(TAU * fq * t)) for fq in (205.3 * 2, 304.4 * 2, 369.6 * 2, 522.7 * 2, 540.0 * 2, 800.0 * 2))
    x = spec_filter(0.6 * _noise(n, 13) + 0.25 * met, r_hp(7000, 3))
    x *= np.exp(-t / (0.16 if open_ else 0.025))
    return x / np.max(np.abs(x))


def d_crash(length=2.8, tone=1.0):
    n = int(length * SR)
    t = np.arange(n) / SR
    met = sum(np.sin(TAU * fq * tone * t + i) for i, fq in enumerate((410, 587, 733, 1015, 1380, 1661, 2370, 3150)))
    x = spec_filter(_noise(n, 14) + 0.15 * met, r_hp(2800, 2), r_lp(12000, 1))
    x *= np.minimum(t / 0.002, 1) * (0.7 * np.exp(-t / (length * 0.3)) + 0.3 * np.exp(-t / 0.08))
    return x / np.max(np.abs(x))


def d_taiko(tune=55.0, decay=0.6, slap=0.5):
    n = int((decay * 4 + 0.1) * SR)
    t = np.arange(n) / SR
    f = tune * (1 + 0.55 * np.exp(-t / 0.018))
    ph = TAU * np.cumsum(f) / SR
    body = np.sin(ph) * np.exp(-t / decay) + 0.3 * np.sin(ph * 1.52) * np.exp(-t / (decay * 0.4)) + 0.15 * np.sin(ph * 2.3) * np.exp(-t / (decay * 0.2))
    sl = spec_filter(_noise(n, 15), r_lp(1400), r_hp(80)) * np.exp(-t / 0.028) * slap
    x = np.tanh(1.4 * (body + sl))
    return x / np.max(np.abs(x))


def d_timp(m=40, decay=1.5):
    f = mf(m)
    n = int((decay * 3) * SR)
    t = np.arange(n) / SR
    fd = f * (1 + 0.03 * np.exp(-t / 0.05))
    ph = TAU * np.cumsum(fd) / SR
    body = (np.sin(ph) * np.exp(-t / decay) + 0.5 * np.sin(ph * 1.5) * np.exp(-t / (decay * 0.6)) + 0.3 * np.sin(ph * 1.98) * np.exp(-t / (decay * 0.5))
            + 0.15 * np.sin(ph * 2.44) * np.exp(-t / (decay * 0.3)))
    mal = spec_filter(_noise(n, 16), r_lp(2500)) * np.exp(-t / 0.01) * 0.4
    x = body + mal
    return x / np.max(np.abs(x))


def d_gong(tune=45.0, length=7.0):
    n = int(length * SR)
    t = np.arange(n) / SR
    rng = np.random.default_rng(17)
    x = np.zeros(n)
    for i, rr in enumerate((1, 1.47, 2.09, 2.56, 3.14, 3.84, 4.5, 5.21, 6.1, 7.3)):
        bloom = 1 - np.exp(-t / (0.05 + 0.08 * i))
        x += (1 / (1 + 0.3 * i)) * bloom * np.exp(-t / (length * 0.45 / (1 + 0.15 * i))) * np.sin(TAU * tune * rr * (1 + rng.normal(0, 0.003)) * t + rng.uniform(0, 6))
    hit = spec_filter(_noise(n, 18), r_lp(3000)) * np.exp(-t / 0.03) * 0.5
    x = x + hit
    return x / np.max(np.abs(x))


def d_boom(length=3.0):
    """Impacto cinematográfico grave."""
    n = int(length * SR)
    t = np.arange(n) / SR
    f = 32 + 40 * np.exp(-t / 0.08)
    body = np.sin(TAU * np.cumsum(f) / SR) * np.exp(-t / (length * 0.35))
    nz = spec_filter(_noise(n, 19), r_lp(500)) * np.exp(-t / 0.35) * 0.6
    x = np.tanh(2.0 * (body + nz))
    return x / np.max(np.abs(x))


def d_tick(freq=2600.0, wood=900.0):
    n = int(0.18 * SR)
    t = np.arange(n) / SR
    x = 0.6 * np.sin(TAU * freq * t) * np.exp(-t / 0.005) + np.sin(TAU * wood * t) * np.exp(-t / 0.028) + 0.4 * np.sin(TAU * wood * 2.76 * t) * np.exp(-t / 0.01)
    return x / np.max(np.abs(x))


def d_shaker():
    n = int(0.12 * SR)
    t = np.arange(n) / SR
    x = spec_filter(_noise(n, 20), r_hp(5000, 2)) * np.minimum(t / 0.012, 1) * np.exp(-t / 0.04)
    return x / np.max(np.abs(x))


def d_djembe(tune=330.0, slap=1.0, decay=0.08):
    n = int(0.6 * SR)
    t = np.arange(n) / SR
    fd = tune * (1 + 0.3 * np.exp(-t / 0.008))
    x = np.sin(TAU * np.cumsum(fd) / SR) * np.exp(-t / decay) + slap * spec_filter(_noise(n, 21), r_hp(900), r_lp(5000)) * np.exp(-t / 0.035)
    return x / np.max(np.abs(x))


def d_tamb():
    n = int(0.35 * SR)
    t = np.arange(n) / SR
    j = sum(np.sin(TAU * fq * t + i) for i, fq in enumerate((4520, 5230, 6100, 7340, 8800)))
    x = spec_filter(_noise(n, 22) * 0.6 + 0.4 * j, r_hp(4500, 2)) * np.minimum(t / 0.003, 1) * np.exp(-t / 0.09)
    return x / np.max(np.abs(x))


def d_clap():
    n = int(0.4 * SR)
    t = np.arange(n) / SR
    env = np.zeros(n)
    for k, off in enumerate((0, 0.011, 0.022)):
        env += (t >= off) * np.exp(-np.maximum(t - off, 0) / 0.006) * (0.8 if k < 2 else 1)
    env += (t >= 0.03) * np.exp(-np.maximum(t - 0.03, 0) / 0.09) * 0.5
    x = spec_filter(_noise(n, 23), r_hp(900), r_lp(3500)) * env
    return x / np.max(np.abs(x))


def d_riser(length=2.0):
    """'Prato ao contrário' que cresce até a próxima seção."""
    n = int(length * SR)
    t = np.arange(n) / SR
    x = spec_filter(_noise(n, 24), r_hp(2000, 1), r_lp(11000, 1)) * (t / length) ** 2.6
    sw = np.sin(TAU * np.cumsum(220 * 2 ** (2.5 * t / length)) / SR) * (t / length) ** 3 * 0.25
    x = x + sw
    return x / np.max(np.abs(x))


DRUMS = {
    'kick': lambda **k: d_kick(**k), 'snare': lambda **k: d_snare(**k), 'hat': lambda **k: d_hat(False),
    'ohat': lambda **k: d_hat(True), 'crash': lambda **k: d_crash(**k), 'taiko': lambda **k: d_taiko(**k),
    'taiko_hi': lambda **k: d_taiko(tune=k.get('tune', 130.0), decay=k.get('decay', 0.22), slap=0.8),
    'tom': lambda **k: d_taiko(tune=k.get('tune', 95.0), decay=k.get('decay', 0.25), slap=0.6),
    'timp': lambda **k: d_timp(**k), 'gong': lambda **k: d_gong(**k), 'boom': lambda **k: d_boom(**k),
    'tick': lambda **k: d_tick(**k), 'tock': lambda **k: d_tick(freq=1800.0, wood=620.0), 'shaker': lambda **k: d_shaker(),
    'djembe': lambda **k: d_djembe(**k), 'djembe_lo': lambda **k: d_djembe(tune=80.0, slap=0.35, decay=0.22),
    'tamb': lambda **k: d_tamb(), 'clap': lambda **k: d_clap(), 'riser': lambda **k: d_riser(**k),
    'heart': lambda **k: d_kick(tune=42.0, decay=0.22, click=0.05, drive=1.2),
}

SIDECHAIN_SRC = {'kick', 'taiko', 'boom', 'heart'}


# ============================================================== teoria

SCALES = {
    'major': [0, 2, 4, 5, 7, 9, 11], 'minor': [0, 2, 3, 5, 7, 8, 10], 'harm': [0, 2, 3, 5, 7, 8, 11],
    'dorian': [0, 2, 3, 5, 7, 9, 10], 'phrygian': [0, 1, 3, 5, 7, 8, 10], 'phrydom': [0, 1, 4, 5, 7, 8, 10],
    'lydian': [0, 2, 4, 6, 7, 9, 11], 'mixo': [0, 2, 4, 5, 7, 9, 10],
}
ROMAN = {'i': 0, 'ii': 2, 'iii': 4, 'iv': 5, 'v': 7, 'vi': 9, 'vii': 11}


def parse_chord(tok):
    name, beats = tok.split(':')
    acc = 0
    while name and name[0] in 'b#':
        acc += -1 if name[0] == 'b' else 1
        name = name[1:]
    mo = re.match(r'(vii|iii|ii|iv|vi|v|i|VII|III|II|IV|VI|V|I)(.*)$', name)
    rn, suf = mo.groups()
    root = ROMAN[rn.lower()] + acc
    minor = rn.islower()
    base = [0, 3, 7] if minor else [0, 4, 7]
    ext = {
        '': base, '7': base + [10], 'maj7': base + [11], 'sus4': [0, 5, 7], 'sus2': [0, 2, 7], '5': [0, 7],
        'add9': base + [14], '6': base + [9], 'o': [0, 3, 6], 'o7': [0, 3, 6, 9], '+': [0, 4, 8], 'm7b5': [0, 3, 6, 10],
    }[suf]
    return root, ext, float(beats)


def parse_prog(s):
    return [parse_chord(t) for t in s.split()]


def voicing(key, root, ivals, center, n):
    """Acorde em posição fechada perto de 'center' — condução de vozes natural (notas comuns ficam paradas)."""
    pcs = []
    for iv in ivals:
        pc = (key + root + iv) % 12
        if pc not in pcs:
            pcs.append(pc)
    if n < len(pcs):
        # poucas vozes: prioridade tônica, depois a ordem dos intervalos (3ª/5ª...)
        return sorted(pc + 12 * round((center - pc) / 12.0) for pc in pcs[:max(1, n)])
    notes = sorted(pc + 12 * round((center - pc) / 12.0) for pc in pcs)
    rootpc = (key + root) % 12
    roots = [x for x in notes if x % 12 == rootpc]
    top, bot = max(roots), min(roots)
    k = 0
    # vozes extras: dobra a tônica uma oitava acima do topo, depois abaixo do baixo, alternando
    while len(notes) < n:
        if k % 2 == 0:
            top += 12
            notes.append(top)
        else:
            bot -= 12
            notes.append(bot)
        k += 1
    return sorted(notes)


MEL_RE = re.compile(r"^([b#]*)(-?\d+)([',]*)(!?)$")


def parse_mel(s, scale):
    out = []
    for tok in s.split():
        if tok == '|':
            continue
        deg, beats = tok.split(':')
        beats = float(beats)
        if deg == 'r':
            out.append((None, beats, 0))
            continue
        mo = MEL_RE.match(deg)
        acc_s, d_s, octs, acc_mark = mo.groups()
        acc = acc_s.count('#') - acc_s.count('b')
        d = int(d_s)
        semis = scale[(d - 1) % 7] + 12 * ((d - 1) // 7) + acc + 12 * (octs.count("'") - octs.count(','))
        out.append((semis, beats, 1 if acc_mark else 0))
    return out


# ============================================================== música

class Song:
    BUS = {
        'drums': dict(hp=30, gain=0.32, rev=0.06),
        'perc': dict(hp=35, gain=0.28, rev=0.16),
        'bass': dict(lp=900, hp=32, drive=1.6, gain=0.48, sc=0.55),
        'pads': dict(lp=2600, hp=140, gain=0.55, rev=0.4, sc=0.45),
        'strings': dict(lp=5200, hp=90, gain=0.7, rev=0.32, sc=0.25),
        'lowstr': dict(lp=3000, hp=45, gain=0.7, rev=0.25, sc=0.3),
        'stacc': dict(lp=6500, hp=160, gain=1.3, rev=0.18, sc=0.2),
        'brass': dict(lp=6500, hp=70, gain=0.85, rev=0.28, sc=0.1),
        'brass_dark': dict(lp=1300, hp=60, gain=1.2, rev=0.4, sc=0.1),
        'lead': dict(lp=7000, hp=220, gain=1.3, rev=0.2, echo=0.28),
        'choir': dict(vowel='a', hp=140, gain=1.1, rev=0.5, sc=0.15),
        'choir_o': dict(vowel='o', hp=90, gain=1.1, rev=0.5, sc=0.15),
        'choir_u': dict(vowel='u', hp=80, gain=1.3, rev=0.6),
        'keys': dict(lp=9000, hp=50, gain=0.85, rev=0.3),
        'bells': dict(hp=300, gain=0.45, rev=0.5),
        'organ': dict(lp=7500, hp=45, gain=0.6, rev=0.45),
        'gtr': dict(hp=95, lp=4300, peak=(1500, 1.2, 3.5), gain=2.0, rev=0.06, sc=0.15),
        'reed': dict(lp=5000, hp=80, gain=0.6, rev=0.25),
        'ney': dict(lp=7000, hp=200, gain=0.6, rev=0.45, echo=0.18),
        'fx': dict(hp=60, gain=0.85, rev=0.35),
        'wind': dict(gain=0.6, rev=0.15),
    }

    def __init__(self, name, bpm, key, scale, bars, meter=4, seed=1, tail=6.0, tempo=None, loop=True,
                 rt60=2.4, wet=0.22, predelay=0.025, target_db=-15.5):
        self.name = name
        self.bpm = bpm
        self.key = key
        self.scale = SCALES[scale]
        self.meter = meter
        self.bars = bars
        self.loop = loop
        self.rt60, self.wet, self.predelay, self.target_db = rt60, wet, predelay, target_db
        total = bars * meter
        grid = np.arange(0, total + 64, 1 / 32.0)
        bpms = np.asarray(tempo(grid) if tempo else np.full_like(grid, bpm), dtype=float)
        dt = 60.0 / bpms / 32.0
        self.bgrid = grid
        self.tgrid = np.concatenate([[0.0], np.cumsum(dt[:-1])])
        self.loop_s = self.t(total)
        self.N = int(round(self.loop_s * SR))
        self.NT = self.N + int(tail * SR)
        self.buses = {}
        self.mix = {k: dict(v) for k, v in Song.BUS.items()}
        self.kicks = []
        self.rng = np.random.default_rng(seed)
        self.cache = {}

    # ---------- tempo
    def t(self, beat):
        return float(np.interp(beat, self.bgrid, self.tgrid))

    def bar(self, b):
        return b * self.meter

    def spb(self, beat=0.0):
        return self.t(beat + 1) - self.t(beat)

    # ---------- buffers
    def buf(self, name):
        if name not in self.buses:
            self.buses[name] = np.zeros((2, self.NT), dtype=np.float32)
        return self.buses[name]

    def add(self, bus, sig, t0, pan=0.0, gain=1.0):
        i = int(round(t0 * SR))
        if i >= self.NT:
            return
        if i < 0:
            # adiantado antes do início (humanização): num loop, o pedaço antes do 0 vai para o fim da volta
            if self.loop:
                pre = min(-i, sig.shape[-1])
                self._mix_at(bus, sig[..., :pre], self.N + i, pan, gain)
            sig = sig[..., -i:]
            i = 0
        self._mix_at(bus, sig, i, pan, gain)

    def _mix_at(self, bus, sig, i, pan, gain):
        if sig.shape[-1] == 0 or i >= self.NT:
            return
        b = self.buf(bus)
        th = (pan + 1) * math.pi / 4
        gl, gr = math.cos(th) * math.sqrt(2) * gain, math.sin(th) * math.sqrt(2) * gain
        n = min(sig.shape[-1], self.NT - i)
        if sig.ndim == 1:
            b[0, i:i + n] += sig[:n] * gl
            b[1, i:i + n] += sig[:n] * gr
        else:
            b[0, i:i + n] += sig[0, :n] * gl
            b[1, i:i + n] += sig[1, :n] * gr

    # ---------- notas
    def note(self, bus, inst, m, beat, dur, vel=0.8, pan=0.0, human=0.006, **kw):
        t0 = self.t(beat) + (self.rng.normal(0, human) if human else 0)
        d = max(0.03, self.t(beat + dur) - self.t(beat))
        v = float(np.clip(vel * (1 + self.rng.normal(0, 0.05)), 0.05, 1.2))
        self.add(bus, inst(m, d, v, self.rng, **kw), t0, pan)

    def hit(self, name, beat, vel=1.0, pan=0.0, bus='drums', **kw):
        key = (name, tuple(sorted(kw.items())))
        if key not in self.cache:
            self.cache[key] = DRUMS[name](**kw).astype(np.float32)
        t0 = self.t(beat) + self.rng.normal(0, 0.002)
        if name in SIDECHAIN_SRC and vel > 0.4:
            self.kicks.append((t0, vel))
        self.add(bus, self.cache[key] * vel * (1 + self.rng.normal(0, 0.04)), t0, pan)

    def deg(self, d, octave=0):
        """Grau da escala (1 = tônica) → semitons a partir da tônica."""
        return self.scale[(d - 1) % 7] + 12 * ((d - 1) // 7) + 12 * octave

    # ---------- harmonia
    def chords(self, prog, start_bar, bars):
        """Repete a progressão até preencher 'bars' compassos. Devolve [(beat, dur, root, ivals)]."""
        pr = parse_prog(prog) if isinstance(prog, str) else prog
        out = []
        b = self.bar(start_bar)
        end = self.bar(start_bar + bars)
        i = 0
        while b < end - 1e-6:
            root, iv, du = pr[i % len(pr)]
            du = min(du, end - b)
            out.append((b, du, root, iv))
            b += du
            i += 1
        return out

    def pad(self, bus, inst, chords, center=60, n=4, vel=0.6, pan=0.0, gap=0.0, **kw):
        for beat, du, root, iv in chords:
            for m in voicing(self.key, root, iv, center, n):
                self.note(bus, inst, m, beat, du - gap, vel, pan, human=0.01, **kw)

    def arp(self, bus, inst, chords, pattern, step=0.25, center=64, n=3, vel=0.6, accent=4, gate=0.9, pan=0.0, **kw):
        """Arpejo/ostinato: 'pattern' = índices na voicing (com oitavas acima quando passa do tamanho)."""
        for beat, du, root, iv in chords:
            v = voicing(self.key, root, iv, center, n)
            k = 0
            x = beat
            while x < beat + du - 1e-6:
                idx = pattern[k % len(pattern)]
                if idx is not None:
                    m = v[idx % len(v)] + 12 * (idx // len(v))
                    a = 1.0 if (k % accent == 0) else 0.78
                    self.note(bus, inst, m, x, step * gate, vel * a, pan, human=0.004, **kw)
                x += step
                k += 1

    def bassline(self, chords, style='eighths', low=33, vel=0.8, bus='bass', kind='synth'):
        for beat, du, root, iv in chords:
            r = (self.key + root - low) % 12 + low
            fifth = r + 7 if iv[-1] != 6 else r + 6
            x = beat
            end = beat + du
            if style == 'whole':
                self.note(bus, i_bass, r, beat, du, vel, kind=kind)
                continue
            if style == 'drone':
                self.note(bus, i_bass, r, beat, du, vel, kind='drone')
                continue
            seq = {
                'eighths': [(0.5, 0, 1.0), (0.5, 0, 0.75)],
                'octaves': [(0.5, 0, 1.0), (0.5, 12, 0.8)],
                'gallop': [(0.5, 0, 1.0), (0.25, 0, 0.7), (0.25, 0, 0.75)],
                'pulse': [(1.0, 0, 1.0)],
                'halves': [(2.0, 0, 1.0)],
                'drive16': [(0.25, 0, 1.0), (0.25, 0, 0.6), (0.25, 0, 0.8), (0.25, 0, 0.6)],
                'sync': [(1.5, 0, 1.0), (1.5, 0, 0.85), (1.0, 7, 0.8)],
                'waltz': [(1.0, 0, 1.0), (2.0, None, 0)],
                'waltz5': [(1.0, 7, 0.9), (2.0, None, 0)],
            }[style]
            k = 0
            while x < end - 1e-6:
                d, iv2, a = seq[k % len(seq)]
                if iv2 is not None:
                    m = r + iv2 if iv2 != 7 else fifth
                    self.note(bus, i_bass, m, x, min(d, end - x) * 0.92, vel * a, kind=kind)
                x += d
                k += 1

    def stabs(self, bus, inst, chords, pattern, center=60, n=4, vel=0.8, steps=16, pan=0.0, **kw):
        """Acordes rítmicos: pattern por compasso ('X' forte, 'x' médio, 'o' fraco, '-' sustenta)."""
        stepb = self.meter / steps
        for beat, du, root, iv in chords:
            v = voicing(self.key, root, iv, center, n)
            x = beat
            while x < beat + du - 1e-6:
                pos = int(round(((x - beat) % self.meter) / stepb)) % steps
                c = pattern[pos]
                if c in 'Xxo':
                    ln = 1
                    while pos + ln < steps and pattern[pos + ln] == '-':
                        ln += 1
                    a = {'X': 1.0, 'x': 0.78, 'o': 0.5}[c]
                    for m in v:
                        self.note(bus, inst, m, x, ln * stepb * 0.9, vel * a, pan, human=0.004, **kw)
                x += stepb

    def melody(self, bus, inst, mel, start_beat, base=None, vel=0.8, pan=0.0, gate=0.96, octave=0, harm=None, max_beats=None, **kw):
        notes = parse_mel(mel, self.scale) if isinstance(mel, str) else mel
        base = (self.key + 12) if base is None else base
        x = start_beat
        for semis, du, acc in notes:
            if max_beats is not None and x - start_beat >= max_beats - 1e-6:
                break
            if semis is not None:
                m = base + semis + 12 * octave
                self.note(bus, inst, m, x, du * gate, vel * (1.12 if acc else 1.0), pan, **kw)
                if harm is not None:
                    self.note(bus, inst, m + harm, x, du * gate, vel * 0.7, -pan, **kw)
            x += du
        return x

    def mel_len(self, mel):
        return sum(b for _, b, _ in parse_mel(mel, self.scale))

    # ---------- bateria
    def pattern(self, name, bar, pat, vel=1.0, bus='drums', pan=0.0, **kw):
        steps = len(pat)
        stepb = self.meter / steps
        b0 = self.bar(bar)
        for i, c in enumerate(pat):
            a = {'X': 1.0, 'x': 0.78, 'o': 0.5, 'g': 0.28}.get(c)
            if a:
                self.hit(name, b0 + i * stepb, vel * a, pan, bus, **kw)

    def drums(self, style, start_bar, bars, inten=1.0, fill=True, crash=True, boom=False, riser=False):
        for k in range(bars):
            b = start_bar + k
            last = fill and k == bars - 1
            DRUM_STYLES[style](self, b, inten, last)
        if crash:
            self.hit('crash', self.bar(start_bar), 0.75 * min(1, inten + 0.2), 0.3)
        if boom:
            self.hit('boom', self.bar(start_bar), 0.95, 0, 'perc')
        if riser:
            self.riser(self.bar(start_bar + bars), 4)

    def riser(self, at_beat, beats=4):
        ln = self.t(at_beat) - self.t(at_beat - beats)
        key = ('riser', round(ln, 2))
        if key not in self.cache:
            self.cache[key] = d_riser(ln).astype(np.float32)
        self.add('fx', self.cache[key] * 0.55, self.t(at_beat - beats), 0)

    def wind(self, start_bar, bars, vel=0.5, center=700):
        """Vento do deserto: ruído filtrado com rajadas lentas (duas bandas que se movem defasadas)."""
        t0 = self.t(self.bar(start_bar))
        n = int((self.t(self.bar(start_bar + bars)) - t0) * SR) + SR
        rng = np.random.default_rng(int(self.rng.integers(1 << 30)))
        out = np.zeros((2, n))
        for ch in range(2):
            for fc, g in ((center, 1.0), (center * 2.3, 0.45)):
                nz = spec_filter(rng.normal(0, 1, n), r_res(fc, fc * 0.7))
                k = n // (SR // 2) + 3
                gust = np.interp(np.linspace(0, k - 1, n), np.arange(k), rng.uniform(0.15, 1.0, k))
                w = SR // 4
                cs = np.cumsum(np.concatenate([np.full(w, gust[0]), gust]))
                gust = (cs[w:] - cs[:-w]) / w
                out[ch] += nz * gust * g
        t = np.arange(n) / SR
        fade = np.minimum(1, np.minimum(t / 2.0, (n / SR - t) / 2.0))
        out *= fade * vel * 0.25
        self.add('wind', out, t0)

    # ---------- render
    def _sidechain(self):
        env = np.ones(self.NT, dtype=np.float32)
        A = int(0.004 * SR)
        R = int(0.32 * SR)
        shape = np.concatenate([np.linspace(0, 1, A), np.exp(-np.arange(R) / (0.11 * SR))]).astype(np.float32)
        for t0, v in self.kicks:
            i = max(0, int(t0 * SR))
            if i >= self.NT:
                continue
            n = min(len(shape), self.NT - i)
            np.minimum(env[i:i + n], 1 - shape[:n] * min(1.0, v), out=env[i:i + n])
        return env

    def render(self):
        sc = self._sidechain() if self.kicks else None
        dry = np.zeros((2, self.NT), dtype=np.float32)
        send = np.zeros((2, self.NT), dtype=np.float32)
        spb = 60.0 / self.bpm
        for name, x in self.buses.items():
            cfg = self.mix.get(name, {})
            resp = []
            if cfg.get('hp'):
                resp.append(r_hp(cfg['hp']))
            if cfg.get('lp'):
                resp.append(r_lp(cfg['lp']))
            if cfg.get('peak'):
                resp.append(r_peak(*cfg['peak']))
            if cfg.get('vowel'):
                resp.append(r_vowel(cfg['vowel']))
            y = spec_filter(x, *resp) if resp else x
            if cfg.get('drive'):
                d = cfg['drive']
                y = (np.tanh(y * d) / math.tanh(d)).astype(np.float32)
            if sc is not None and cfg.get('sc'):
                y = y * (1 - cfg['sc'] * (1 - sc))
            if cfg.get('echo'):
                fb = cfg['echo']
                d1 = int(spb * 0.75 * SR)
                e = np.zeros_like(y)
                mono = (y[0] + y[1]) * 0.5
                for k in range(1, 5):
                    sh = d1 * k
                    if sh >= self.NT:
                        break
                    # pingue-pongue: ecos ímpares à direita, pares à esquerda
                    e[k % 2, sh:] += mono[:-sh] * (fb ** k)
                y = y + spec_filter(e, r_lp(3500), r_hp(300))
            g = cfg.get('gain', 0.7)
            if os.environ.get('BM_DEBUG'):
                yy = (y[0] * g) ** 2
                w = yy[:len(yy) // SR * SR].reshape(-1, SR).mean(axis=1)
                act = np.sqrt(w[w > 1e-7]) if np.any(w > 1e-7) else np.array([1e-9])
                print(f"  [{self.name}] {name:10s} rms ativo p75 {20 * math.log10(float(np.percentile(act, 75))):6.1f} dB")
            dry += y * g
            send += y * g * cfg.get('rev', 0.2)
        wet = fdn_reverb(send, self.rt60, self.predelay)
        mix = dry + wet * self.wet * 4.0
        if self.loop:
            tail = self.NT - self.N
            mix[:, :tail] += mix[:, self.N:self.N + tail]
            mix = mix[:, :self.N]
        else:
            n = mix.shape[1]
            t = np.arange(n) / SR
            mix *= np.clip((n / SR - t) / 1.5, 0, 1)
        return master(mix, self.target_db, self.loop)


def fdn_reverb(x, rt60=2.4, predelay=0.025, damp=0.35):
    """Reverb FDN: 8 linhas de atraso (primos), Hadamard, amortecimento por linha, processado em blocos vetoriais."""
    n = x.shape[1]
    mono = (x[0] + x[1]) * 0.5
    pd = int(predelay * SR)
    inp = np.zeros(n)
    inp[pd:] = mono[:n - pd]
    inp = spec_filter(inp, r_hp(180), r_lp(7000))
    delays = np.array([1031, 1327, 1523, 1801, 2053, 2311, 2633, 2903])
    g = 10 ** (-3.0 * delays / (rt60 * SR))
    H = np.array([[1]])
    while H.shape[0] < 8:
        H = np.block([[H, H], [H, -H]])
    H = H / math.sqrt(8)
    P = int(delays.max()) + 4
    y = np.zeros((8, n + P))
    B = int(delays.min()) - 2
    rows = np.arange(8)[:, None]
    ing = np.array([1, -1, 1, -1, 1, -1, 1, -1]) * 0.35
    for s in range(0, n, B):
        e = min(n, s + B)
        m = e - s
        idx = s + P - delays[:, None] + np.arange(m)[None, :]
        r = (1 - damp) * y[rows, idx] + damp * y[rows, idx - 1]
        r *= g[:, None]
        y[:, s + P:e + P] = H @ r + ing[:, None] * inp[None, s:e]
    y = y[:, P:]
    cl = np.array([1, 1, -1, -1, 1, 1, -1, -1]) / 2.0
    cr = np.array([1, -1, 1, -1, -1, 1, -1, 1]) / 2.0
    out = np.stack([cl @ y, cr @ y])
    return spec_filter(out, r_hp(160), r_lp(8000, 1)).astype(np.float32)


def master(x, target_db, loop):
    """EQ suave, normaliza RMS para o alvo e limita picos (envelope circular quando é loop)."""
    # inclinação: menos sub (alto-falante pequeno/fone não sofre), mais presença e brilho
    x = spec_filter(x.astype(np.float64), r_hp(36, 2), r_shelf(85, -4.0, high=False), r_shelf(2600, 4.5), r_peak(3500, 1.5, 1.5),
                    r_peak(300, 1.2, -1.0), circular=loop)
    rms = math.sqrt(float(np.mean(x ** 2))) + 1e-9
    x *= 10 ** (target_db / 20.0) / rms
    ceil = 0.89
    blk = 64
    n = x.shape[1]
    pk = np.max(np.abs(x), axis=0)
    nb = (n + blk - 1) // blk
    pkb = np.zeros(nb * blk)
    pkb[:n] = pk
    pkb = pkb.reshape(nb, blk).max(axis=1)
    need = np.maximum(1.0, pkb / ceil)
    # olhar à frente 1 bloco + soltura exponencial; em loop roda duas voltas para o envelope casar na emenda
    need = np.maximum(need, np.roll(need, -1) if loop else np.concatenate([need[1:], need[-1:]]))
    rel = math.exp(-blk / (0.12 * SR))
    gains = np.empty(nb)
    cur = 1.0
    passes = 2 if loop else 1
    for p in range(passes):
        for i in range(nb):
            cur = max(need[i], 1.0 + (cur - 1.0) * rel)
            gains[i] = cur
    gs = np.repeat(1.0 / gains, blk)[:n]
    k = blk
    gs = np.convolve(np.concatenate([gs[-k:], gs, gs[:k]]) if loop else np.pad(gs, k, mode='edge'), np.ones(k) / k, mode='same')[k:-k]
    x = x * gs
    x = np.tanh(x / 0.95) * 0.95
    return x


def write_ogg(name, x):
    os.makedirs(TMP_DIR, exist_ok=True)
    os.makedirs(OUT_DIR, exist_ok=True)
    wav = os.path.join(TMP_DIR, name + '.wav')
    ogg = os.path.join(OUT_DIR, name + '.ogg')
    dither = (np.random.default_rng(5).random(x.shape) - np.random.default_rng(6).random(x.shape)) / 32768.0
    pcm = np.clip((x + dither) * 32767, -32768, 32767).astype('<i2')
    with wave.open(wav, 'wb') as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.T.copy().tobytes())
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', wav, '-c:a', 'libvorbis', '-q:a', '3', ogg], check=True)
    os.remove(wav)
    return ogg


# ============================================================== estilos de bateria (um compasso)

def ds_heroic(s, b, it, fill):
    s.pattern('kick', b, 'X.....x.X.x.....' if it < 0.9 else 'X.....x.X.x...x.')
    s.pattern('snare', b, '....X.......X...', 0.85)
    s.pattern('hat', b, 'xoxoxoxoxoxoxoxo' if it >= 0.7 else 'x.o.x.o.x.o.x.o.', 0.45, pan=0.35)
    if it >= 0.6:
        s.pattern('taiko', b, 'X.......X.....x.', 0.8, 'perc')
    if fill:
        _fill(s, b, it)


def ds_epic(s, b, it, fill):
    s.pattern('taiko', b, 'X.....X...X.....', 1.0, 'perc')
    if it >= 0.5:
        s.pattern('taiko_hi', b, '....x..x....x.xx', 0.7, 'perc', pan=-0.3)
    if it >= 0.75:
        s.pattern('snare', b, '....X.......X...', 0.75)
        s.pattern('kick', b, 'X.........X.....', 0.9)
    if it >= 0.9:
        s.pattern('hat', b, 'x.x.x.x.x.x.x.x.', 0.35, pan=0.35)
    if fill:
        _fill(s, b, it, perc=True)


def ds_halftime(s, b, it, fill):
    s.pattern('kick', b, 'X.........X.....' if it < 0.8 else 'X..x......X..x..')
    s.pattern('snare', b, '........X.......', 0.95)
    s.pattern('taiko', b, 'X.......x.......', 0.9, 'perc')
    if it >= 0.5:
        s.pattern('hat', b, 'x...x...x...x...' if it < 0.8 else 'x.x.x.x.x.x.x.x.', 0.4, pan=0.3)
    if it >= 0.85:
        s.pattern('taiko_hi', b, '..x...x...x...xx', 0.6, 'perc', pan=-0.3)
    if fill:
        _fill(s, b, it, perc=True)


def ds_tribal(s, b, it, fill):
    s.pattern('taiko', b, 'X..x..X...X..x..', 1.0, 'perc')
    s.pattern('djembe', b, '..x..x..x.xx..x.', 0.7, 'perc', pan=0.4)
    s.pattern('djembe_lo', b, 'X.....X.....X...', 0.7, 'perc', pan=-0.4)
    if it >= 0.5:
        s.pattern('shaker', b, 'xoxoxoxoxoxoxoxo', 0.35, pan=0.5)
    if it >= 0.8:
        s.pattern('tom', b, '......x.......x.', 0.7, 'perc', pan=-0.2)
        s.pattern('kick', b, 'X.......X.......', 0.7)
    if fill:
        _fill(s, b, it, perc=True)


def ds_rock(s, b, it, fill):
    if it >= 0.9:
        s.pattern('kick', b, 'XxXxXxXxXxXxXxXx', 0.8)
    else:
        s.pattern('kick', b, 'X.x...x.X.x..x..')
    s.pattern('snare', b, '....X.......X...', 1.0)
    s.pattern('hat' if it < 0.8 else 'ohat', b, 'x.x.x.x.x.x.x.x.', 0.4 if it < 0.8 else 0.28, pan=0.35)
    if fill:
        _fill(s, b, it)


def ds_halfrock(s, b, it, fill):
    s.pattern('kick', b, 'X..X..X...X..X..', 1.0)
    s.pattern('snare', b, '........X.......', 1.0)
    s.pattern('ohat', b, 'x...x...x...x...', 0.3, pan=0.35)
    if fill:
        _fill(s, b, it)


def ds_waltz(s, b, it, fill):
    s.pattern('kick', b, 'X...........', 0.7)
    s.pattern('snare', b, '....o...o...', 0.45)
    s.pattern('tick', b, '....x...x...', 0.35, pan=0.4, freq=3400.0, wood=1500.0)
    if it >= 0.7:
        s.pattern('tamb', b, 'x.o.x.o.x.o.', 0.35, pan=-0.4)
    if fill:
        s.pattern('snare', b, '........xxxX', 0.6)


def ds_march(s, b, it, fill):
    s.pattern('snare', b, 'X.xxX.x.X.xxX.x.' if it >= 0.6 else 'X...x...X...x.o.', 0.7)
    s.pattern('kick', b, 'X.......X.......', 0.8)
    m1 = s.key - 12 + 0
    while m1 > 45:
        m1 -= 12
    while m1 < 38:
        m1 += 12
    s.hit('timp', s.bar(b), 0.8, 0, 'perc', m=m1)
    s.hit('timp', s.bar(b) + 2, 0.7, 0, 'perc', m=m1 + 7 if m1 + 7 <= 52 else m1 - 5)
    if fill:
        for k in range(8):
            s.hit('timp', s.bar(b) + 2 + k * 0.25, 0.4 + 0.07 * k, 0, 'perc', m=m1)


def ds_clock(s, b, it, fill):
    s.pattern('tick', b, 'x.......x.......', 0.55, pan=0.25)
    s.pattern('tock', b, '....x.......x...', 0.5, pan=-0.25)
    if it >= 0.3:
        s.pattern('heart', b, 'X..x............', 0.85)
    if it >= 0.6:
        s.pattern('tick', b, '..o...o...o...o.', 0.3, pan=0.5, freq=4200.0, wood=1700.0)
        s.pattern('snare', b, '........X.......', 0.6)
    if it >= 0.85:
        s.pattern('taiko', b, 'X.....X...X.....', 0.85, 'perc')
        s.pattern('hat', b, 'xoxoxoxoxoxoxoxo', 0.3, pan=0.35)
    if fill:
        _fill(s, b, it, perc=True)


def ds_pulse(s, b, it, fill):
    s.pattern('heart', b, 'X.......X.......', 0.7)
    if it >= 0.6:
        s.pattern('snare', b, '........X.......', 0.5)
        s.pattern('hat', b, 'x.x.x.x.x.x.x.x.', 0.25, pan=0.3)
    if it >= 0.85:
        s.pattern('taiko', b, 'X.....x...x.....', 0.75, 'perc')
    if fill and it >= 0.6:
        _fill(s, b, it, perc=True)


def ds_frame(s, b, it, fill):
    """Tambor de moldura do deserto: esparso, eco longo."""
    s.pattern('djembe_lo', b, 'X.....x...x.....', 0.6 * it + 0.2, 'perc', pan=-0.2)
    if it >= 0.5:
        s.pattern('djembe', b, '...x......x..x..', 0.4, 'perc', pan=0.3, tune=260.0)
    if it >= 0.8:
        s.pattern('taiko', b, 'X.......X.......', 0.7, 'perc')


def _fill(s, b, it, perc=False):
    b0 = s.bar(b) + s.meter - 1
    if perc:
        for k in range(4):
            s.hit('taiko_hi' if k % 2 else 'taiko', b0 + k * 0.25, 0.55 + 0.12 * k, (-0.3 if k % 2 else 0.3), 'perc')
        return
    for k in range(4):
        s.hit('tom', b0 + k * 0.25, 0.6 + 0.1 * k, 0.4 - 0.25 * k, 'perc', tune=150.0 - 25 * k)
    s.hit('snare', b0 + 0.75, 0.9)


DRUM_STYLES = {
    'heroic': ds_heroic, 'epic': ds_epic, 'halftime': ds_halftime, 'tribal': ds_tribal, 'rock': ds_rock,
    'halfrock': ds_halfrock, 'waltz': ds_waltz, 'march': ds_march, 'clock': ds_clock, 'pulse': ds_pulse, 'frame': ds_frame,
}

ARP16 = [0, 1, 2, 1, 0, 1, 2, 3]
ARP_UP = [0, 1, 2, 3]
ALBERTI = [0, 2, 1, 2]


# ============================================================== temas (composição original)

def th_elemental_guardian():
    """Heroico, 140 bpm, Ré menor — o Guardião Elemental: metais nobres, ostinato de cordas, final em Ré MAIOR."""
    s = Song('elemental_guardian', 140, 50, 'minor', 48, seed=101, rt60=2.2)
    A = "i:4 bVI:4 bIII:4 bVII:4"
    theme = ("1:1 5:1.5 4:0.5 3:1 | 3:1.5 2:0.5 1:1 6,:1 | 7,:1 1:0.5 3:0.5 5:2 | 4:1.5 3:0.5 2:2 |"
             " 1:1 5:1.5 4:0.5 3:1 | 6:1.5 5:0.5 4:1 3:1 | 5:1 3:0.5 5:0.5 8:2 | 7:1.5 8:0.5 9:2")
    counter = ("4:2 5:1 6:1 | 5:2 4:1 3:1 | 3:2 2:1 1:1 | #7,:2 2:2 |"
               " 4:2 5:1 6:1 | 8:2 6:1 5:1 | 5:2 3:1 1:1 | #7:2 5:2")
    major = ("6:1.5 5:0.5 4:1 3:1 | 7,:1 2:1 4:1 5:1 | #3:1 1:1 5:2 | 8:4 |"
             " 6:1.5 5:0.5 4:1 3:1 | 7,:1 2:1 4:1 5:1 | #3:1 5:1 8:1 #10:1 | 12:4")
    # intro 0-3
    c = s.chords("i:16", 0, 4)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 62, 3, 0.55)
    s.stabs('brass', i_horn_stab, s.chords("i:8 bVII:4 V:4", 0, 4), 'X.....X.....X...', 55, 3, 0.7)
    s.drums('epic', 0, 4, 0.6, boom=True, riser=True)
    s.bassline(c, 'pulse', 38, 0.7)
    # A 4-11
    c = s.chords(A, 4, 8)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 64, 3, 0.6)
    s.pad('strings', i_strings, c, 57, 4, 0.5)
    s.bassline(c, 'octaves', 38, 0.8)
    s.melody('brass', i_brass, theme, s.bar(4), vel=0.85)
    s.drums('heroic', 4, 8, 0.75)
    # B 12-19
    c = s.chords("iv:4 bVI:4 i:4 V:4", 12, 8)
    s.pad('strings', i_strings, c, 60, 4, 0.55)
    s.pad('choir_o', i_choir, c, 60, 3, 0.45)
    s.melody('strings', i_strings, counter, s.bar(12), base=s.key + 12, vel=0.75, att=0.08)
    s.arp('stacc', i_stacc, c, [0, 2, 1, 2], 0.5, 62, 3, 0.45)
    s.bassline(c, 'halves', 38, 0.8)
    s.drums('epic', 12, 8, 0.7, riser=True)
    # A2 20-27
    c = s.chords(A, 20, 8)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 70, 3, 0.6)
    s.pad('strings', i_strings, c, 57, 4, 0.55)
    s.pad('choir', i_choir, c, 62, 3, 0.5)
    s.bassline(c, 'octaves', 38, 0.85)
    s.melody('brass', i_brass, theme, s.bar(20), vel=0.95)
    s.melody('lead', i_strings, theme, s.bar(20), base=s.key + 24, vel=0.5, att=0.05)
    s.drums('heroic', 20, 8, 1.0, boom=True)
    # ponte 28-35
    c = s.chords("bVI:4 bVII:4 i:8 bVI:4 bVII:4 V:8", 28, 8)
    s.stabs('brass', i_horn_stab, c, 'X..X..X...X..X..', 50, 3, 0.7)
    s.arp('stacc', i_stacc, c, [0, 1, 2, 3, 2, 1], 0.25, 60, 4, 0.5)
    s.pad('choir_o', i_choir, c, 58, 4, 0.45)
    s.bassline(c, 'gallop', 38, 0.8)
    s.drums('tribal', 28, 8, 0.9, riser=True)
    # clímax 36-43: Ré maior (Picardia)
    c = s.chords("bVI:4 bVII:4 I:8", 36, 8)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 68, 3, 0.65)
    s.pad('strings', i_strings, c, 60, 5, 0.6)
    s.pad('choir', i_choir, c, 64, 4, 0.6)
    s.bassline(c, 'octaves', 38, 0.9)
    s.melody('brass', i_brass, major, s.bar(36), vel=1.0)
    s.melody('lead', i_strings, major, s.bar(36), base=s.key + 24, vel=0.5, att=0.05)
    s.drums('heroic', 36, 8, 1.0, boom=True)
    # volta 44-47
    c = s.chords("i:4 bVII:4 bVI:4 V:4", 44, 4)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 62, 3, 0.55)
    s.pad('strings', i_strings, c, 57, 4, 0.5)
    s.bassline(c, 'eighths', 38, 0.75)
    s.melody('brass', i_brass, "5:3 4:1 | 3:3 2:1 | 1:2 3:2 | #7,:4", s.bar(44), vel=0.8)
    s.drums('heroic', 44, 4, 0.7, riser=True)
    return s


def th_abyss_sovereign():
    """Sombrio e pesado, 76 bpm, Dó# frígio — o Soberano do Abismo: gongo, coro grave, metais graves, meio-tempo."""
    s = Song('abyss_sovereign', 76, 49, 'phrygian', 28, seed=202, rt60=3.4, wet=0.26, target_db=-15)
    s.mix['bass']['drive'] = 3.0
    s.mix['bass']['lp'] = 700
    c = s.chords("i:16", 0, 4)
    s.hit('gong', 0, 0.9, 0, 'perc', tune=40.0)
    s.hit('boom', 0, 1.0, 0, 'perc')
    s.bassline(c, 'drone', 25, 0.9)
    s.pad('choir_u', i_choir, c, 49, 3, 0.55)
    s.pad('pads', i_pad, c, 56, 3, 0.4)
    s.melody('lowstr', i_strings, "1:4 2:4 | 1:4 7,:4 | 1:8", s.bar(0), base=s.key - 12, vel=0.6)
    s.riser(s.bar(4), 6)
    A = "i:4 bII:4 i:4 bVII:4"
    mel = "1:2 2:1 1:1 | 2:2 3:2 | 1:3 7,:1 | 1:4 | 5:2 6:1 5:1 | 6:2 8:2 | 7:3 6:1 | 5:4"
    c = s.chords(A, 4, 8)
    s.bassline(c, 'halves', 25, 0.9)
    s.bassline(c, 'eighths', 37, 0.45, bus='gtr', kind='pick')
    s.pad('pads', i_pad, c, 56, 3, 0.45)
    s.pad('choir_o', i_choir, c, 52, 3, 0.5)
    s.melody('brass_dark', i_brass, mel, s.bar(4), base=s.key - 12, vel=0.9, harm=-12)
    s.drums('halftime', 4, 8, 0.7, boom=True)
    c = s.chords("bVI:4 bII:4 iv:4 V:4", 12, 8)
    s.bassline(c, 'halves', 25, 0.9)
    s.pad('strings', i_strings, c, 56, 4, 0.55)
    s.arp('stacc', i_stacc, c, [0, 1, 2, 1], 0.25, 52, 3, 0.45)
    s.melody('choir', i_choir, "5:3 4:1 | 3:2 2:2 | 6:3 5:1 | #7,:4 | 5:3 6:1 | 8:2 7:2 | 6:2 5:1 4:1 | #7,:2 2:2", s.bar(12), base=s.key, vel=0.8)
    s.drums('halftime', 12, 8, 0.85, riser=True)
    c = s.chords("i:4 bII:4 bVII:4 bII:4", 20, 8)
    s.bassline(c, 'halves', 25, 1.0)
    s.bassline(c, 'drive16', 37, 0.35, bus='gtr', kind='pick')
    s.pad('choir', i_choir, c, 56, 4, 0.6)
    s.pad('strings', i_strings, c, 52, 4, 0.55)
    s.stabs('brass', i_horn_stab, c, 'X......X..X.....', 44, 3, 0.85)
    s.melody('brass_dark', i_brass, mel, s.bar(20), base=s.key - 12, vel=1.0, harm=-12)
    s.drums('halftime', 20, 8, 1.0, boom=True)
    s.hit('gong', s.bar(20), 0.7, 0, 'perc', tune=40.0)
    return s


def th_seraph():
    """Santo e majestoso, 96 bpm, Mi maior com cor lídia — o Serafim: coro, órgão, sinos, harpa."""
    s = Song('seraph', 96, 52, 'major', 36, seed=303, rt60=3.6, wet=0.3, target_db=-16)
    intro = "I:8 II:8"
    c = s.chords(intro, 0, 4)
    s.pad('choir', i_choir, c, 64, 4, 0.55)
    s.arp('bells', i_celesta, c, [0, 1, 2, 3, 2, 1, 2, 3], 0.5, 76, 4, 0.5)
    s.arp('keys', i_harp, c, [0, 1, 2, 3, 4, 5, 6, 7], 0.25, 60, 3, 0.45)
    s.bassline(c, 'whole', 40, 0.5, kind='sub')
    s.riser(s.bar(4), 4)
    A = "I:4 II:4 IV:4 I:4 vi:4 II:4 IV:4 V:4"
    mel = ("5:2 6:1 7:1 | 9:3 8:1 | 6:2 5:1 4:1 | 5:4 | 3:2 4:1 5:1 | #4:2 6:1 5:1 | 4:1.5 3:0.5 2:1 1:1 | 2:4")
    c = s.chords(A, 4, 8)
    s.melody('choir', i_choir, mel, s.bar(4), base=s.key + 12, vel=0.85)
    s.pad('organ', i_organ, c, 60, 4, 0.45)
    s.pad('strings', i_strings, c, 56, 3, 0.4)
    s.arp('keys', i_harp, c, [0, 1, 2, 3, 4, 5, 4, 3], 0.25, 60, 3, 0.4)
    s.bassline(c, 'halves', 40, 0.7, kind='sub')
    s.drums('epic', 4, 8, 0.45, crash=False)
    c = s.chords("vi:4 IV:4 I:4 V:4 vi:4 IV:4 II:4 V:4", 12, 8)
    s.melody('brass', i_brass, "3:2 2:1 1:1 | 6,:2 1:2 | 5:3 4:1 | 2:4 | 3:2 4:1 5:1 | 6:2 8:2 | #4:2 6:2 | 7:4", s.bar(12), vel=0.8)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 64, 3, 0.5)
    s.pad('strings', i_strings, c, 60, 4, 0.55)
    s.pad('choir_o', i_choir, c, 62, 3, 0.45)
    s.bassline(c, 'octaves', 40, 0.75)
    s.drums('epic', 12, 8, 0.75, riser=True)
    c = s.chords(A, 20, 8)
    s.melody('choir', i_choir, mel, s.bar(20), base=s.key + 12, vel=1.0, harm=-12)
    s.melody('brass', i_brass, mel, s.bar(20), base=s.key, vel=0.75)
    s.pad('organ', i_organ, c, 60, 5, 0.55)
    s.pad('strings', i_strings, c, 64, 4, 0.55)
    s.arp('bells', i_celesta, c, [0, 1, 2, 3], 0.5, 80, 4, 0.45)
    s.bassline(c, 'octaves', 40, 0.85)
    s.drums('epic', 20, 8, 1.0, boom=True)
    for k in range(8):
        s.hit('timp', s.bar(20 + k), 0.75, 0, 'perc', m=40)
    c = s.chords("IV:8 I:8 II:8 V:8", 28, 8)
    s.pad('choir_o', i_choir, c, 62, 4, 0.5)
    s.arp('keys', i_harp, c, [0, 1, 2, 3, 4, 5, 6, 7, 6, 5, 4, 3], 0.25, 60, 3, 0.45)
    s.arp('bells', i_celesta, c, [0, 2, 1, 3], 1.0, 76, 4, 0.4)
    s.melody('strings', i_strings, "6:6 5:2 | 3:8 | #4:6 5:2 | 2:8", s.bar(28), base=s.key + 12, vel=0.55)
    s.bassline(c, 'whole', 40, 0.6, kind='sub')
    s.drums('epic', 28, 8, 0.3, crash=False, riser=True)
    return s


def th_twilight_herald():
    """Tenso, 120 bpm, Si menor — o Arauto do Crepúsculo: relógio tiquetaqueando, coração, cordas pulsando."""
    s = Song('twilight_herald', 120, 59, 'harm', 44, seed=404, rt60=2.6)
    c = s.chords("i:8 bVI:8", 0, 8)
    s.drums('clock', 0, 8, 0.35, crash=False)
    s.arp('stacc', i_stacc, c, [0], 0.5, 47, 1, 0.5)
    s.pad('pads', i_pad, c, 54, 3, 0.4)
    s.bassline(c, 'whole', 35, 0.6, kind='sub')
    s.riser(s.bar(8), 8)
    P = "i:4 bVI:4 iv:4 V:4"
    motif = "5:1 6:1 5:2 | 4:1 3:1 1:2 | 3:1 4:1 5:1 6:1 | 7:4 | 5:1 6:1 5:2 | 8:1 7:1 6:2 | 4:1 3:1 4:1 6:1 | 7:2 2:2"
    c = s.chords(P, 8, 8)
    s.drums('clock', 8, 8, 0.65, crash=False)
    s.arp('stacc', i_stacc, c, [0, 0, 1, 0], 0.5, 52, 3, 0.55)
    s.melody('keys', i_piano, motif, s.bar(8), base=s.key + 12, vel=0.7)
    s.pad('strings', i_strings, c, 54, 3, 0.45)
    s.bassline(c, 'pulse', 35, 0.7)
    c = s.chords(P, 16, 8)
    s.drums('clock', 16, 8, 0.85, riser=True)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 59, 3, 0.55)
    s.melody('strings', i_strings, motif, s.bar(16), base=s.key + 12, vel=0.7, att=0.06)
    s.melody('keys', i_piano, motif, s.bar(16), base=s.key, vel=0.5)
    s.pad('choir_o', i_choir, c, 58, 3, 0.45)
    s.bassline(c, 'eighths', 35, 0.75)
    c = s.chords("bVI:4 bVII:4 i:8 iv:4 bVI:4 V:8", 24, 8)
    s.drums('epic', 24, 8, 1.0, boom=True)
    s.drums('clock', 24, 8, 0.6, crash=False, fill=False)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 64, 3, 0.6)
    s.melody('brass', i_brass, "3:2 4:1 5:1 | 6:2 5:2 | 5:3 4:1 | 3:4 | 6:2 5:1 4:1 | 3:2 1:2 | 2:2 #2:2 | 3:2 7,:2",
             s.bar(24), base=s.key, vel=0.9, harm=-12)
    s.pad('choir', i_choir, c, 62, 4, 0.55)
    s.pad('strings', i_strings, c, 57, 4, 0.55)
    s.bassline(c, 'octaves', 35, 0.85)
    c = s.chords("i:2 bII:2 i:2 bII:2 V:8", 32, 8)
    s.drums('clock', 32, 8, 1.0, crash=True)
    s.arp('stacc', i_stacc, c, [0, 1, 0, 2], 0.25, 60, 3, 0.6)
    s.stabs('brass', i_horn_stab, c, 'X.......X.......', 50, 3, 0.8)
    s.melody('strings', i_tremolo, "8:8 b9:8 | 8:8 7:8 | 8:8 b9:8 | 7:16", s.bar(32), base=s.key, vel=0.6)
    s.pad('choir_o', i_choir, c, 60, 4, 0.5)
    s.bassline(c, 'drive16', 35, 0.7)
    s.riser(s.bar(40), 4)
    c = s.chords("i:16", 40, 4)
    s.drums('clock', 40, 4, 0.35, crash=True, fill=False)
    s.hit('boom', s.bar(40), 0.8, 0, 'perc')
    s.arp('stacc', i_stacc, c, [0], 0.5, 47, 1, 0.45)
    s.pad('pads', i_pad, c, 54, 3, 0.35)
    s.bassline(c, 'whole', 35, 0.55, kind='sub')
    return s


def th_primordial_dragon():
    """Épico final, 150 bpm, Dó menor — o Dragão Primordial: coro + taikos + orquestra inteira, clímax em Dó maior."""
    s = Song('primordial_dragon', 150, 48, 'minor', 56, seed=505, rt60=2.8, wet=0.24, target_db=-14.5)
    theme = ("1:1.5 5,:0.5 1:1 2:1 | 3:3 2:0.5 3:0.5 | 5:1.5 4:0.5 3:1 2:1 | 2:2 4,:2 |"
             " 1:1.5 5,:0.5 1:1 2:1 | 3:2 5:2 | 6:1.5 5:0.5 4:1 3:1 | 2:2 7,:2")
    major = ("3:1.5 4:0.5 5:2 | 6:1.5 5:0.5 4:2 | #3:1 5:1 8:2 | 8:4 | 3:1.5 4:0.5 5:2 | 6:1 8:1 9:2 | #10:2 9:1 8:1 | 8:4")
    A = "i:4 bVI:4 bIII:4 bVII:4"
    c = s.chords("i:16", 0, 4)
    s.hit('boom', 0, 1.0, 0, 'perc')
    s.pad('choir', i_choir, c, 60, 4, 0.6)
    s.stabs('brass', i_horn_stab, c, 'X..X..X.....X.X.', 48, 3, 0.85)
    s.drums('epic', 0, 4, 0.8, riser=True)
    s.bassline(c, 'eighths', 36, 0.8)
    c = s.chords(A, 4, 8)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 63, 3, 0.6)
    s.pad('strings', i_strings, c, 58, 4, 0.5)
    s.pad('choir_o', i_choir, c, 60, 3, 0.45)
    s.melody('brass', i_brass, theme, s.bar(4), vel=0.9, harm=-12)
    s.bassline(c, 'octaves', 36, 0.85)
    s.drums('epic', 4, 8, 0.85)
    c = s.chords("bVI:4 bVII:4 i:8 bVI:4 bVII:4 V:8", 12, 8)
    s.melody('choir', i_choir, "3:3 4:1 | 5:4 | 5:2 4:1 3:1 | 2:4 | 3:3 4:1 | 5:2 6:2 | 7:4 | #7:4", s.bar(12), base=s.key + 12, vel=0.9)
    s.arp('stacc', i_stacc, c, [0, 1, 2, 1], 0.25, 60, 3, 0.55)
    s.pad('strings', i_strings, c, 62, 4, 0.55)
    s.bassline(c, 'gallop', 36, 0.85)
    s.drums('tribal', 12, 8, 0.9, riser=True)
    c = s.chords(A, 20, 8)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 70, 3, 0.65)
    s.pad('strings', i_strings, c, 58, 5, 0.6)
    s.melody('brass', i_brass, theme, s.bar(20), vel=1.0, harm=-12)
    s.melody('choir', i_choir, theme, s.bar(20), base=s.key + 12, vel=0.8)
    s.bassline(c, 'octaves', 36, 0.9)
    s.drums('heroic', 20, 8, 1.0, boom=True)
    s.drums('epic', 20, 8, 0.6, crash=False, fill=False)
    c = s.chords("i:8 bII:8", 28, 8)
    s.drums('tribal', 28, 8, 1.0, boom=True)
    s.stabs('choir', i_chant, c, 'X.....X.....X.X.', 55, 3, 0.75)
    s.melody('brass_dark', i_brass, "1:3 2:1 | 1:4 | b2:3 1:1 | b2:4 | 1:3 2:1 | 3:2 2:2 | b2:4 | 1:2 7,:2", s.bar(28), base=s.key - 12, vel=0.95)
    s.bassline(c, 'sync', 36, 0.85)
    s.riser(s.bar(36), 8)
    c = s.chords("bVI:4 bVII:4 I:8", 36, 8)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 67, 3, 0.65)
    s.pad('choir', i_choir, c, 64, 4, 0.65)
    s.pad('strings', i_strings, c, 60, 5, 0.6)
    s.melody('brass', i_brass, major, s.bar(36), vel=1.05, harm=-12)
    s.melody('lead', i_strings, major, s.bar(36), base=s.key + 24, vel=0.5, att=0.05)
    s.bassline(c, 'octaves', 36, 0.95)
    s.drums('heroic', 36, 8, 1.0, boom=True)
    s.drums('epic', 36, 8, 0.7, crash=False, fill=False)
    c = s.chords("iv:4 bVI:4 V:8 iv:4 bVI:4 V:8", 44, 8)
    s.arp('stacc', i_stacc, c, [0, 1, 2, 3, 2, 1, 0, 1], 0.25, 62, 4, 0.6)
    s.pad('strings', i_tremolo, c, 60, 4, 0.5)
    s.pad('choir_o', i_choir, c, 60, 4, 0.5)
    s.melody('brass', i_brass, "4:2 5:1 6:1 | 8:4 | 7:4 | #7:4 | 4:2 5:1 6:1 | 8:2 9:2 | #7:8", s.bar(44), vel=0.85)
    s.bassline(c, 'eighths', 36, 0.8)
    s.drums('epic', 44, 8, 0.9)
    c = s.chords("i:4 bVII:4 bVI:4 V:4", 52, 4)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 62, 3, 0.55)
    s.pad('choir', i_choir, c, 60, 4, 0.5)
    s.bassline(c, 'eighths', 36, 0.8)
    s.drums('epic', 52, 4, 0.8, riser=True)
    return s


def th_lich():
    """Sinistro, 96 bpm, Lá menor harmônico — o Lich: órgão em arpejos, coro fantasma, sinos."""
    s = Song('lich', 96, 57, 'harm', 36, seed=606, rt60=3.8, wet=0.3, target_db=-16)
    c = s.chords("i:8 V:8", 0, 4)
    s.hit('gong', 0, 0.8, 0, 'perc', tune=38.0)
    s.arp('organ', i_organ, c, [0, 2, 1, 2, 0, 2, 1, 3], 0.25, 57, 4, 0.55)
    s.pad('choir_u', i_choir, c, 52, 3, 0.5)
    s.bassline(c, 'whole', 33, 0.6, kind='sub')
    P = "i:4 iv:4 V:4 i:4 bVI:4 iio:4 V:8"
    mel = "5:2 6:1 5:1 | 4:2 3:1 2:1 | 7,:1 2:1 4:1 6:1 | 5:4 | 5:2 6:1 8:1 | 9:2 8:1 6:1 | 7:1 6:1 5:1 4:1 | 3:2 7,:2"
    c = s.chords(P, 4, 8)
    s.arp('organ', i_organ, c, [0, 2, 1, 2, 0, 2, 1, 3], 0.25, 57, 4, 0.45)
    s.melody('organ', i_organ, mel, s.bar(4), base=s.key + 12, vel=0.7)
    s.pad('choir_o', i_choir, c, 57, 3, 0.4)
    s.bassline(c, 'halves', 33, 0.7, kind='sub')
    s.drums('halftime', 4, 8, 0.35)
    c = s.chords(P, 12, 8)
    s.melody('choir', i_choir, mel, s.bar(12), base=s.key, vel=0.85)
    s.arp('stacc', i_stacc, c, [0, 1, 2, 1], 0.25, 60, 3, 0.45)
    s.pad('strings', i_strings, c, 55, 4, 0.5)
    s.arp('bells', i_celesta, c, [2, None, 1, None, 0, None, 1, None], 0.5, 81, 3, 0.4)
    s.bassline(c, 'eighths', 33, 0.75)
    s.drums('halftime', 12, 8, 0.7, riser=True)
    c = s.chords("i:4 bVI:4 iv:4 V:4", 20, 8)
    s.arp('organ', i_organ, c, [0, 1, 2, 3, 4, 3, 2, 1], 0.25, 60, 4, 0.5)
    s.melody('brass_dark', i_brass, "1:3 2:1 | 3:4 | 4:2 3:1 2:1 | 7,:4 | 1:3 2:1 | 3:2 5:2 | 6:2 5:1 4:1 | 7,:4", s.bar(20), base=s.key - 12, vel=0.95)
    s.melody('choir', i_choir, "8:4 | 8:4 | 6:4 | 7:4 | 8:4 | 8:4 | 6:4 | 7:4", s.bar(20), base=s.key, vel=0.7)
    s.pad('strings', i_tremolo, c, 60, 4, 0.5)
    s.bassline(c, 'octaves', 33, 0.85)
    s.drums('halftime', 20, 8, 1.0, boom=True)
    s.hit('gong', s.bar(20), 0.6, 0, 'perc', tune=38.0)
    c = s.chords("i:8 iv:8 bVI:8 V:8", 28, 8)
    s.arp('bells', i_celesta, c, [0, 1, 2, 3, 2, 1], 0.5, 76, 4, 0.45)
    s.pad('choir_u', i_choir, c, 55, 4, 0.5)
    s.pad('organ', i_organ, c, 52, 3, 0.3)
    s.melody('keys', i_piano, "5:2 6:2 | 5:4 | 4:2 3:2 | 2:4 | 3:2 4:2 | 5:4 | 7,:4 | 2:4", s.bar(28), base=s.key, vel=0.55)
    s.bassline(c, 'whole', 33, 0.55, kind='sub')
    s.drums('pulse', 28, 8, 0.4, crash=False)
    return s


def th_beast():
    """Tribal, 126 bpm, Mi frígio dominante — Bestas (hidra, basilisco, wyvern, mantícora, colosso): tambores e gritos."""
    s = Song('beast', 126, 40, 'phrydom', 44, seed=707, rt60=2.0, wet=0.2, target_db=-14.5)
    c = s.chords("I:16", 0, 4)
    for k in range(4):
        ds_tribal(s, k, 0.4 + 0.2 * k, k == 3)
    s.bassline(c, 'drone', 28, 0.8)
    s.riser(s.bar(4), 4)
    P = "I:8 bII:8 I:8 bvii:4 bII:4"
    horn = "1:1.5 2:0.5 3:2 | 2:1 1:1 7,:2 | 1:1.5 2:0.5 3:1 5:1 | 4:2 3:1 2:1 | 3:1.5 4:0.5 5:2 | 6:1 5:1 4:2 | 3:1 2:1 1:1 2:1 | 1:4"
    c = s.chords(P, 4, 8)
    s.drums('tribal', 4, 8, 0.75, boom=True)
    s.bassline(c, 'drone', 28, 0.8)
    s.melody('brass', i_brass, horn, s.bar(4), base=s.key + 12, vel=0.9, harm=-12)
    for k in range(8):
        s.note('choir', i_chant, s.key + 19, s.bar(4 + k) + 3.5, 0.3, 0.75)
    c = s.chords(P, 12, 8)
    s.drums('tribal', 12, 8, 1.0)
    s.bassline(c, 'sync', 28, 0.9)
    s.arp('stacc', i_stacc, c, [0, 1, 0, 2, 0, 1, 0, 3], 0.25, 52, 4, 0.55)
    s.stabs('brass_dark', i_horn_stab, c, 'X..X..X...X.....', 40, 3, 0.9)
    s.pad('choir_o', i_choir, c, 52, 3, 0.5)
    s.melody('lead', i_ney, horn, s.bar(12), base=s.key + 24, vel=0.6)
    c = s.chords("I:16 bII:16", 20, 8)
    s.drums('tribal', 20, 8, 0.9, crash=False, riser=True)
    s.bassline(c, 'drone', 28, 0.7)
    for k in range(8):
        s.note('choir', i_chant, s.key + 16, s.bar(20 + k) + 1, 0.3, 0.8)
        s.note('choir', i_chant, s.key + 19, s.bar(20 + k) + 3, 0.3, 0.8)
    c = s.chords(P, 28, 12)
    s.drums('tribal', 28, 12, 1.0, boom=True)
    s.drums('heroic', 28, 12, 0.5, crash=False, fill=False)
    s.bassline(c, 'gallop', 28, 0.9)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 59, 3, 0.55)
    s.pad('strings', i_strings, c, 56, 4, 0.55)
    s.pad('choir', i_choir, c, 60, 3, 0.55)
    s.melody('brass', i_brass, horn + " | " + horn.split('|', 4)[0] + "| 2:1 1:1 7,:2 | 1:1.5 2:0.5 3:1 5:1 | 1:4 r:0", s.bar(28), base=s.key + 12, vel=1.0, harm=-12, max_beats=48)
    c = s.chords("I:16", 40, 4)
    for k in range(4):
        ds_tribal(s, 40 + k, 0.7, k == 3)
    s.bassline(c, 'drone', 28, 0.7)
    s.riser(s.bar(44), 4)
    return s


def th_mimic_king():
    """Valsa brincalhona e sinistra, 3/4, 168 bpm, Fá menor — o Rei Mímico: caixinha de música, fagote, pizzicato."""
    s = Song('mimic_king', 168, 53, 'harm', 72, meter=3, seed=808, rt60=2.0, wet=0.2, target_db=-16)
    melA = ("5:2 #4:1 | 5:2 6:1 | 5:1 4:1 3:1 | 2:3 | 2:2 #1:1 | 2:2 4:1 | 7,:1 2:1 4:1 | 5:3 |"
            " 4:2 3:1 | 4:2 6:1 | 5:1 3:1 1:1 | 5:3 | b2:2 1:1 | 7,:2 2:1 | 1:3 | r:3")
    PA = "i:3 i:3 V7:3 V7:3 V7:3 V7:3 i:3 i:3 iv:3 iv:3 i:3 i:3 bII:3 V7:3 i:3 i:3"
    # intro 0-3: caixinha de música sozinha
    s.melody('bells', i_celesta, "5:2 #4:1 | 5:2 6:1 | 5:1 4:1 3:1 | 2:3", 0, base=s.key + 24, vel=0.75)
    s.drums('waltz', 0, 4, 0.2, crash=False, fill=False)
    # A 4-19
    c = s.chords(PA, 4, 16)
    s.bassline(c, 'waltz', 36, 0.8, bus='keys', kind='sub')
    for beat, du, root, iv in c:
        x = beat
        while x < beat + du - 1e-6:
            v = voicing(s.key, root, iv, 62, 3)
            for m in v:
                s.note('strings', i_pizz, m, x + 1, 0.5, 0.55)
                s.note('strings', i_pizz, m, x + 2, 0.5, 0.5)
            x += 3
    s.melody('bells', i_celesta, melA, s.bar(4), base=s.key + 24, vel=0.75)
    s.melody('reed', i_reed, melA, s.bar(4), base=s.key, vel=0.6)
    s.drums('waltz', 4, 16, 0.5, crash=False)
    # B 20-35: sinistro
    PB = "bVI:3 bVI:3 bII:3 bII:3 V7:3 V7:3 i:3 i:3"
    c = s.chords(PB, 20, 16)
    s.pad('lowstr', i_tremolo, c, 48, 3, 0.55)
    s.bassline(c, 'waltz', 36, 0.8, bus='keys', kind='sub')
    melB = "6:3 | 5:2 #4:1 | b2:3 | 1:2 7,:1 | 7,:2 b7,:1 | 6,:2 #4,:1 | 5,:3 | r:3"
    s.melody('reed', i_reed, melB + " | " + melB, s.bar(20), base=s.key, vel=0.75)
    s.melody('bells', i_celesta, "8:1 r:2 | b9:1 r:2 | 8:1 r:2 | 7:1 r:2 | " * 4, s.bar(20), base=s.key + 12, vel=0.45, max_beats=48)
    s.drums('waltz', 20, 16, 0.45, crash=False)
    s.riser(s.bar(36), 6)
    # A2 36-51: completo
    c = s.chords(PA, 36, 16)
    s.bassline(c, 'waltz', 36, 0.85, bus='keys', kind='sub')
    s.stabs('brass', i_horn_stab, c, '....x...x...', 60, 3, 0.55, steps=12)
    s.melody('strings', i_strings, melA, s.bar(36), base=s.key + 12, vel=0.65, att=0.04)
    s.melody('bells', i_celesta, melA, s.bar(36), base=s.key + 24, vel=0.7)
    s.melody('reed', i_reed, melA, s.bar(36), base=s.key - 12, vel=0.55)
    s.drums('waltz', 36, 16, 0.85)
    # C 52-67: frenesi
    PC = "i:3 iv:3 V7:3 i:3 bVI:3 bII:3 V7:3 V7:3"
    c = s.chords(PC, 52, 16)
    s.arp('stacc', i_stacc, c, [0, 1, 2, 3, 2, 1], 0.5, 60, 4, 0.5)
    s.bassline(c, 'waltz', 36, 0.85, bus='keys', kind='sub')
    s.bassline(c, 'waltz5', 36, 0.6, bus='keys', kind='sub')
    melC = "1:1 3:1 5:1 | 8:2 7:1 | 7:1 6:1 5:1 | 5:3 | 6:1 5:1 4:1 | b2:2 1:1 | 7,:1 2:1 4:1 | 7,:3"
    s.melody('reed', i_reed, melC + " | " + melC, s.bar(52), base=s.key + 12, vel=0.75)
    s.melody('bells', i_celesta, melC + " | " + melC, s.bar(52), base=s.key + 24, vel=0.55)
    s.pad('choir_o', i_choir, c, 60, 3, 0.4)
    s.drums('waltz', 52, 16, 1.0)
    # volta 68-71
    c = s.chords("V7:12", 68, 4)
    s.melody('reed', i_reed, "5:0.5 #4:0.5 4:0.5 #3:0.5 3:0.5 #2:0.5 | 2:0.5 #1:0.5 1:0.5 7,:0.5 b7,:0.5 6,:0.5 | 5,:3 | r:3", s.bar(68), base=s.key + 12, vel=0.6)
    s.bassline(c, 'waltz', 36, 0.7, bus='keys', kind='sub')
    s.drums('waltz', 68, 4, 0.3, crash=False)
    return s


def th_hollow_king():
    """Deserto desolado, 66 bpm, Ré frígio dominante — o Rei Hollow: vento, drones graves, ney distante."""
    s = Song('hollow_king', 66, 50, 'phrydom', 24, seed=909, rt60=4.2, wet=0.32, target_db=-17)
    s.wind(0, 24, 0.9)
    c = s.chords("I:32", 0, 8)
    s.bassline(c, 'drone', 26, 0.85)
    s.note('lowstr', i_strings, 45, 0, 32, 0.45)
    s.pad('choir_u', i_choir, s.chords("I:16 bII:16", 0, 8), 57, 3, 0.4)
    s.drums('frame', 0, 8, 0.3, crash=False, fill=False)
    s.hit('gong', 0, 0.6, 0, 'perc', tune=36.0)
    ney = "5:3 6:1 | 5:2 4:1 3:1 | 2:4 | 1:4 | 3:1 4:1 5:2 | 6:2 5:1 4:1 | 3:2 2:2 | 1:4"
    c = s.chords("I:8 bII:8 iv:8 I:8", 8, 8)
    s.bassline(c, 'drone', 26, 0.85)
    s.melody('ney', i_ney, ney, s.bar(8), base=s.key + 12, vel=0.75)
    s.pad('strings', i_strings, c, 55, 3, 0.4, att=1.5)
    s.drums('frame', 8, 8, 0.55, crash=False, fill=False)
    c = s.chords("iv:8 bII:8 I:8 bII:8", 16, 8)
    s.bassline(c, 'drone', 26, 0.9)
    s.melody('ney', i_ney, "8:3 9:1 | 10:2 9:1 8:1 | 9:4 | 8:4 | 6:2 5:2 | 4:2 3:2 | 2:4 | 1:4", s.bar(16), base=s.key + 12, vel=0.8)
    s.melody('lowstr', i_strings, "4,:8 | 2:8 | 1:8 | 2:8", s.bar(16), base=s.key - 12, vel=0.55)
    s.pad('choir_o', i_choir, c, 57, 3, 0.45)
    s.pad('strings', i_strings, c, 60, 3, 0.4, att=1.5)
    s.drums('frame', 16, 8, 0.85, crash=False, fill=False)
    s.hit('gong', s.bar(16), 0.5, 0, 'perc', tune=36.0)
    return s


ULQ_M1 = "5:1.5 4:0.5 3:1 1:1 | 3:2 2:1 1:1 | 6,:1.5 7,:0.5 1:1 3:1 | 2:4"
ULQ_M2 = "5:1.5 4:0.5 3:1 8:1 | 8:2 7:1 6:1 | 6:1.5 5:0.5 4:1 3:1 | #7,:2 2:2"
ULQ_P = "i:4 bVI:4 iv:4 V:4"


def th_ulquiorra():
    """Frio e melancólico, 76 bpm, Si menor — Ulquiorra: piano sozinho, cordas entram, construção lenta."""
    s = Song('ulquiorra', 76, 59, 'minor', 28, seed=1010, rt60=3.2, wet=0.28, target_db=-17)
    s.mix['keys']['rev'] = 0.4
    c = s.chords(ULQ_P, 0, 8)
    s.arp('keys', i_piano, c, [0, 1, 2, 1, 3, 1, 2, 1], 0.5, 50, 3, 0.4)
    s.melody('keys', i_piano, ULQ_M1 + " | " + ULQ_M2, 0, base=s.key, vel=0.6)
    c = s.chords(ULQ_P, 8, 8)
    s.arp('keys', i_piano, c, [0, 1, 2, 1, 3, 1, 2, 1], 0.5, 50, 3, 0.42)
    s.melody('keys', i_piano, ULQ_M1 + " | " + ULQ_M2, s.bar(8), base=s.key + 12, vel=0.6)
    s.pad('strings', i_strings, c, 57, 4, 0.4, att=0.8)
    s.melody('lowstr', i_strings, "3:4 | 1:4 | 1:4 | #7,:4 | 3:4 | 3:4 | 1:2 6,:2 | #7,:4", s.bar(8), base=s.key - 24, vel=0.5, att=0.5)
    c = s.chords(ULQ_P, 16, 8)
    s.arp('keys', i_piano, c, [0, 1, 2, 3, 2, 1, 2, 3], 0.25, 55, 3, 0.4)
    s.melody('strings', i_strings, ULQ_M1 + " | " + ULQ_M2, s.bar(16), base=s.key + 12, vel=0.7, att=0.1)
    s.melody('keys', i_piano, ULQ_M1 + " | " + ULQ_M2, s.bar(16), base=s.key + 24, vel=0.45)
    s.pad('choir_o', i_choir, c, 59, 3, 0.45)
    s.pad('lowstr', i_strings, c, 45, 2, 0.5)
    s.bassline(c, 'halves', 35, 0.55, kind='sub')
    s.drums('pulse', 16, 4, 0.5, crash=False, fill=False)
    s.drums('pulse', 20, 4, 0.9, crash=False, fill=True)
    s.riser(s.bar(24), 4)
    c = s.chords(ULQ_P, 24, 4)
    s.hit('boom', s.bar(24), 0.6, 0, 'perc')
    s.arp('keys', i_piano, c, [0, 1, 2, 1, 3, 1, 2, 1], 0.5, 50, 3, 0.38)
    s.melody('keys', i_piano, ULQ_M1, s.bar(24), base=s.key, vel=0.55)
    s.pad('strings', i_strings, c, 57, 3, 0.3, att=0.6)
    return s


def th_ulquiorra_segunda_etapa():
    """Furioso, 168 bpm — Segunda Etapa: o MESMO motivo, distorcido, rápido, guitarras e coro."""
    s = Song('ulquiorra_segunda_etapa', 168, 59, 'minor', 56, seed=1111, rt60=2.0, wet=0.2, target_db=-14)
    gk = 47
    c = s.chords("i:16", 0, 4)
    s.stabs('gtr', i_power, c, 'X.xxX.xxX.xxX.xx', gk, 1, 0.8, mute=True)
    s.drums('rock', 0, 4, 0.7, boom=True, riser=True)
    s.bassline(c, 'drive16', 35, 0.7, kind='pick')
    c = s.chords(ULQ_P, 4, 8)
    s.stabs('gtr', i_power, c, 'X.xxX.xxX.x.X.x.', gk, 1, 0.8, mute=True)
    s.stabs('gtr', i_power, c, 'X-------........', gk, 1, 0.7)
    s.melody('lead', i_lead, ULQ_M1 + " | " + ULQ_M2, s.bar(4), base=s.key + 12, vel=0.8)
    s.bassline(c, 'eighths', 35, 0.85, kind='pick')
    s.drums('rock', 4, 8, 0.85)
    c = s.chords("bVI:8 bVII:8 i:8 V:8", 12, 8)
    s.stabs('gtr', i_power, c, 'X-------X-------', gk, 1, 0.7)
    aug = " ".join(f"{t.split(':')[0]}:{float(t.split(':')[1]) * 2}" for t in ULQ_M1.replace('|', ' ').split())
    s.melody('choir', i_choir, aug, s.bar(12), base=s.key + 12, vel=0.9)
    s.melody('brass', i_brass, aug, s.bar(12), base=s.key, vel=0.85)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 64, 3, 0.55)
    s.bassline(c, 'octaves', 35, 0.85, kind='pick')
    s.drums('rock', 12, 8, 0.75, riser=True)
    c = s.chords("i:4 bII:4", 20, 8)
    s.stabs('gtr', i_power, c, 'X..X..X...X..X..', gk, 1, 0.85, mute=True)
    s.stabs('brass', i_horn_stab, c, 'X..X..X.........', 47, 3, 0.9)
    s.pad('choir_o', i_choir, c, 59, 3, 0.55)
    s.bassline(c, 'sync', 35, 0.9, kind='pick')
    s.drums('halfrock', 20, 8, 1.0, boom=True)
    c = s.chords(ULQ_P, 28, 8)
    s.stabs('gtr', i_power, c, 'X.xxX.xxX.xxX.xx', gk, 1, 0.75, mute=True)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 66, 3, 0.6)
    s.melody('lead', i_lead, ULQ_M1 + " | " + ULQ_M2, s.bar(28), base=s.key + 12, vel=0.85, harm=-12)
    s.melody('choir', i_choir, ULQ_M1 + " | " + ULQ_M2, s.bar(28), base=s.key, vel=0.6)
    s.pad('strings', i_strings, c, 59, 4, 0.5)
    s.bassline(c, 'eighths', 35, 0.9, kind='pick')
    s.drums('rock', 28, 8, 1.0, boom=True)
    c = s.chords("bVI:4 bVII:4 i:8 bVI:4 bVII:4 V:8", 36, 8)
    s.stabs('gtr', i_power, c, 'X-------X-------', gk, 1, 0.75)
    s.melody('lead', i_lead, ULQ_M2 + " | " + ULQ_M2, s.bar(36), base=s.key + 24, vel=0.8)
    s.melody('brass', i_brass, ULQ_M2 + " | " + ULQ_M2, s.bar(36), base=s.key, vel=0.9)
    s.pad('choir', i_choir, c, 62, 4, 0.6)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 64, 3, 0.6)
    s.bassline(c, 'octaves', 35, 0.9, kind='pick')
    s.drums('rock', 36, 8, 1.0)
    c = s.chords("i:4 bVI:4 iv:4 V:4", 44, 8)
    s.stabs('gtr', i_power, c, 'X.xxX.xxX.x.X.x.', gk, 1, 0.8, mute=True)
    s.melody('lead', i_lead, ULQ_M1 + " | " + ULQ_M2, s.bar(44), base=s.key + 12, vel=0.75)
    s.bassline(c, 'eighths', 35, 0.85, kind='pick')
    s.drums('rock', 44, 8, 0.85)
    c = s.chords("i:4 bVII:4 bVI:4 V:4", 52, 4)
    s.stabs('gtr', i_power, c, 'X.......X...X...', gk, 1, 0.8)
    s.bassline(c, 'drive16', 35, 0.75, kind='pick')
    s.drums('rock', 52, 4, 0.8, riser=True)
    return s


def th_barragan():
    """Pomposo e decadente — Barragan: marcha de metais em Si♭ maior que envelhece, desafina e desacelera (Respira)."""
    def tempo(b):
        b = np.asarray(b, dtype=float)
        # 104 bpm nos 8 primeiros compassos, cai até 76 no fim (o tempo apodrece junto com o rei)
        return np.where(b < 32, 104.0, 104.0 - 28.0 * np.clip((b - 32) / 96.0, 0, 1) ** 1.3)
    s = Song('barragan', 104, 46, 'major', 32, seed=1212, rt60=2.8, wet=0.24, tempo=tempo, target_db=-15.5)
    fan = ("1:0.75 1:0.25 1:1 3:1 5:1 | 8:2 7:1 6:1 | 5:1.5 4:0.5 3:1 2:1 | 1:3 r:1 |"
           " 6:1.5 5:0.5 4:1 6:1 | 5:1.5 4:0.5 3:1 5:1 | 4:1 2:1 7,:1 2:1 | 1:4")
    P = "I:4 IV:4 V:4 I:4 vi:4 IV:4 V7:4 I:4"
    c = s.chords(P, 0, 8)
    s.melody('brass', i_brass, fan, 0, base=s.key + 12, vel=0.95, harm=-12)
    s.stabs('brass', i_horn_stab, c, '....x.......x...', 53, 3, 0.65)
    s.pad('strings', i_strings, c, 58, 4, 0.45)
    s.bassline(c, 'pulse', 34, 0.8)
    s.drums('march', 0, 8, 1.0)
    c = s.chords(P, 8, 8)
    s.melody('brass', i_brass, fan, s.bar(8), base=s.key + 12, vel=0.85, harm=-12, bend=6.0, wobble=1.8)
    s.melody('reed', i_reed, fan, s.bar(8), base=s.key, vel=0.45)
    s.pad('strings', i_strings, c, 58, 4, 0.45)
    s.bassline(c, 'pulse', 34, 0.75)
    s.drums('march', 8, 8, 0.7)
    s.scale = SCALES['minor']
    c = s.chords("i:4 iv:4 V:4 i:4 bVI:4 iv:4 V:8", 16, 8)
    s.melody('brass_dark', i_brass, fan, s.bar(16), base=s.key + 12, vel=0.85, harm=-12, bend=14.0, wobble=3.0)
    s.pad('choir_o', i_choir, c, 55, 3, 0.4)
    s.pad('lowstr', i_strings, c, 46, 3, 0.5)
    s.bassline(c, 'halves', 34, 0.7)
    s.drums('march', 16, 8, 0.45, crash=False)
    s.drums('clock', 16, 8, 0.0, crash=False, fill=False)
    c = s.chords("i:4 bVI:4 iv:4 V:4", 24, 8)
    s.melody('brass_dark', i_brass, "5:2 4:1 3:1 | 3:2 2:2 | 1:3 7,:1 | 7,:4 | 5:2 4:1 3:1 | 3:2 2:2 | 1:2 7,:2 | #7,:4", s.bar(24),
             base=s.key + 12, vel=0.75, bend=25.0, wobble=4.5)
    s.pad('choir_u', i_choir, c, 53, 3, 0.5)
    s.pad('lowstr', i_tremolo, c, 46, 3, 0.45)
    s.bassline(c, 'whole', 34, 0.7, kind='sub')
    s.drums('clock', 24, 7, 0.35, crash=False, fill=False)
    s.hit('gong', s.bar(24), 0.5, 0, 'perc', tune=44.0)
    for k in range(16):
        s.hit('timp', s.bar(31) + k * 0.25, 0.3 + 0.045 * k, 0, 'perc', m=41)
    s.riser(s.bar(32), 4)
    return s


def th_grimmjow():
    """Agressivo, 176 bpm, Mi menor — Grimmjow: riffs de guitarra distorcida, bateria rock, lead selvagem."""
    s = Song('grimmjow', 176, 52, 'minor', 56, seed=1313, rt60=1.6, wet=0.16, target_db=-13.5)
    gk = 40
    R = {'x': None, '3': 3, '5': 5, '6': 6, '7': 7, '8': 8, 'a': 10, 'c': 12, '1': 1}

    def riff(bar, pats, vel=0.85):
        for k, pat in enumerate(pats):
            b0 = s.bar(bar + k)
            for i, ch in enumerate(pat):
                if ch == 'x':
                    s.note('gtr', i_power, gk, b0 + i * 0.25, 0.2, vel, mute=True)
                elif ch in R and R[ch] is not None:
                    ln = 1
                    while i + ln < len(pat) and pat[i + ln] == '-':
                        ln += 1
                    s.note('gtr', i_power, gk + R[ch], b0 + i * 0.25, ln * 0.25 * 0.95, vel)
                    s.note('bass', i_bass, gk - 12 + R[ch] + (12 if gk - 12 + R[ch] < 28 else 0), b0 + i * 0.25, ln * 0.25 * 0.95, 0.85, kind='pick')
                if ch == 'x':
                    s.note('bass', i_bass, gk - 12 + 12 * 0, b0 + i * 0.25, 0.2, 0.75, kind='pick')
    riffA = ["x.xx3--.x.xx5-6-", "x.xx3--.x.x.a---"]
    riffB = ["x..x..x.3--.5---", "x..x..x.6--.5-3-"]
    for k in range(2):
        riff(k * 2, riffA)
    s.drums('rock', 2, 2, 0.7, boom=False, riser=True)
    for k in range(4):
        riff(4 + k * 2, riffA)
    s.drums('rock', 4, 8, 0.85, boom=True)
    lead = "5:1 4:0.5 3:0.5 4:1 5:1 | 8:2 7:1 5:1 | 6:1 5:0.5 4:0.5 3:1 2:1 | 2:2 5,:2 | 5:1 4:0.5 3:0.5 4:1 5:1 | 8:2 9:1 10:1 | 9:1 8:0.5 7:0.5 6:1 5:1 | 2:4"
    c = s.chords("i:4 bVI:4 bIII:4 bVII:4", 12, 8)
    s.stabs('gtr', i_power, c, 'X-----X-----X-x-', gk, 1, 0.8)
    s.bassline(c, 'eighths', 28, 0.85, kind='pick')
    s.melody('lead', i_lead, lead, s.bar(12), base=s.key + 12, vel=0.85)
    s.stabs('brass', i_horn_stab, c, 'X...........x...', 55, 3, 0.7)
    s.drums('rock', 12, 8, 0.95, boom=True)
    for k in range(4):
        riff(20 + k * 2, riffB, 0.9)
    s.drums('halfrock', 20, 8, 1.0, boom=True)
    c = s.chords("i:4 bVII:4 bVI:4 bVII:4", 28, 8)
    s.stabs('gtr', i_power, c, 'X.xxX.xxX.xxX.xx', gk, 1, 0.75, mute=True)
    s.bassline(c, 'eighths', 28, 0.85, kind='pick')
    pent = [0, 3, 5, 7, 10, 12, 15, 17]
    for k in range(8 * 16):
        b = s.bar(28) + k * 0.25
        if (k // 4) % 4 == 3 and k % 4 >= 2:
            continue
        i = (k * 3 + (k // 8) * 2) % len(pent)
        if (k // 16) % 2:
            i = len(pent) - 1 - i
        s.note('lead', i_lead, s.key + 12 + pent[i], b, 0.22, 0.6 + 0.2 * ((k % 4) == 0))
    s.drums('rock', 28, 8, 1.0, riser=True)
    c = s.chords("i:4 bVI:4 bIII:4 bVII:4", 36, 8)
    s.stabs('gtr', i_power, c, 'X-----X-----X-x-', gk, 1, 0.85)
    s.bassline(c, 'octaves', 28, 0.9, kind='pick')
    s.melody('lead', i_lead, lead, s.bar(36), base=s.key + 24, vel=0.85, harm=-12)
    s.stabs('brass', i_horn_stab, c, 'X..X..X...X.....', 55, 3, 0.75)
    s.drums('rock', 36, 8, 1.0, boom=True)
    for k in range(4):
        riff(44 + k * 2, riffA, 0.9)
    s.drums('rock', 44, 8, 0.95)
    riff(52, ["x.xx3--.x.xx5-6-", "3---5---6---7---"])
    riff(54, ["x.x.x.x.xxxxxxxx", "c-------........"])
    s.drums('rock', 52, 4, 0.9, riser=True)
    return s


def th_las_noches():
    """Ambiente — Las Noches: silêncio branco, pads distantes e frios, celesta rara, vento leve. Sem bateria."""
    s = Song('las_noches', 60, 57, 'minor', 20, seed=1414, rt60=5.5, wet=0.4, target_db=-22, tail=9.0)
    s.mix['bells']['rev'] = 0.8
    s.mix['pads']['sc'] = 0
    P = "iadd9:8 bVImaj7:8 bIIImaj7:8 v7:8 iadd9:8 IVadd9:8 bVImaj7:8 bVIImaj7:8 iadd9:8 v7:8"
    c = s.chords(P, 0, 20)
    s.pad('pads', i_glass, c, 64, 4, 0.55)
    s.pad('strings', i_strings, c, 76, 2, 0.18, att=2.5, voices=2)
    s.pad('choir_u', i_choir, s.chords("i:16 bVI:16 i:16 IV:16 bVI:8 v:8", 0, 20), 57, 2, 0.3, att=2.0)
    s.wind(0, 20, 0.3, center=1600)
    s.bassline(c, 'whole', 33, 0.3, kind='sub')
    rng = np.random.default_rng(77)
    for beat, du, root, iv in c:
        v = voicing(s.key, root, iv, 76, 4)
        x = beat + float(rng.choice([1.0, 1.5, 2.0]))
        while x < beat + du - 0.5:
            s.note('bells', i_celesta, int(rng.choice(v)) + (12 if rng.random() < 0.3 else 0), x, 1.0, 0.35 + 0.15 * rng.random())
            x += float(rng.choice([1.5, 2.5, 3.0, 4.0]))
    return s


def th_generic_boss():
    """Batalha genérica, 132 bpm, Lá menor — qualquer chefe sem tema próprio."""
    s = Song('generic_boss', 132, 57, 'minor', 44, seed=1515, rt60=2.0, target_db=-14.5)
    mel = ("1:1 3:1 5:1.5 4:0.5 | 3:2 1:2 | 2:1 3:1 4:1.5 3:0.5 | 1:4 |"
           " 1:1 3:1 5:1.5 6:0.5 | 5:2 3:2 | 4:1.5 3:0.5 2:1 1:1 | #7,:4")
    c = s.chords("i:8 bVII:4 V:4", 0, 4)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 60, 3, 0.55)
    s.bassline(c, 'eighths', 33, 0.8)
    s.drums('epic', 0, 4, 0.7, boom=True, riser=True)
    c = s.chords("i:4 bVI:4 bVII:4 i:4 i:4 bVI:4 iv:4 V:4", 4, 8)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 64, 3, 0.6)
    s.pad('strings', i_strings, c, 57, 4, 0.5)
    s.melody('brass', i_brass, mel, s.bar(4), vel=0.9)
    s.bassline(c, 'octaves', 33, 0.85)
    s.drums('heroic', 4, 8, 0.8)
    c = s.chords("bVI:4 bVII:4 bIII:4 bVI:4 iv:4 bVI:4 V:8", 12, 8)
    s.pad('strings', i_strings, c, 60, 4, 0.55)
    s.pad('choir_o', i_choir, c, 60, 3, 0.45)
    s.melody('strings', i_strings, "3:3 4:1 | 5:2 4:2 | 5:3 6:1 | 8:4 | 6:2 5:1 4:1 | 3:2 1:2 | #7,:4 | 2:4", s.bar(12), base=s.key + 12, vel=0.7, att=0.06)
    s.arp('stacc', i_stacc, c, [0, 2, 1, 2], 0.25, 62, 3, 0.5)
    s.bassline(c, 'gallop', 33, 0.85)
    s.drums('epic', 12, 8, 0.8, riser=True)
    c = s.chords("i:4 bVII:4 bVI:4 bVII:4", 20, 8)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 69, 3, 0.6)
    s.stabs('gtr', i_power, c, 'X.xxX.xxX.xxX.xx', 45, 1, 0.6, mute=True)
    s.pad('choir', i_choir, c, 62, 3, 0.5)
    s.melody('brass', i_brass, mel, s.bar(20), vel=1.0, harm=-12)
    s.melody('lead', i_lead, mel, s.bar(20), base=s.key + 24, vel=0.45)
    s.bassline(c, 'octaves', 33, 0.9)
    s.drums('rock', 20, 8, 1.0, boom=True)
    c = s.chords("i:8 bII:8", 28, 8)
    s.drums('tribal', 28, 8, 0.9)
    s.stabs('brass_dark', i_horn_stab, c, 'X..X..X...X..X..', 45, 3, 0.9)
    s.pad('strings', i_tremolo, c, 57, 4, 0.5)
    s.bassline(c, 'sync', 33, 0.85)
    s.riser(s.bar(36), 4)
    c = s.chords("bVI:4 bVII:4 i:4 i:4 bVI:4 bVII:4 V:8", 36, 8)
    s.arp('stacc', i_stacc, c, ARP16, 0.25, 66, 3, 0.6)
    s.pad('choir', i_choir, c, 62, 4, 0.55)
    s.pad('strings', i_strings, c, 57, 4, 0.55)
    s.melody('brass', i_brass, "3:2 4:1 5:1 | 6:2 7:2 | 8:3 7:1 | 8:4 | 3:2 4:1 5:1 | 6:2 7:2 | #7:4 | #7:4", s.bar(36), vel=1.0, harm=-12)
    s.bassline(c, 'octaves', 33, 0.9)
    s.drums('heroic', 36, 8, 1.0, boom=True)
    return s


def th_boss_victory():
    """Vinheta de vitória (~6 s, sem loop): bVI–bVII–I em Dó maior, tímpanos, prato, coro e harpa."""
    s = Song('boss_victory', 110, 60, 'major', 3, seed=1616, loop=False, tail=4.0, rt60=2.6, wet=0.26, target_db=-14)
    for k in range(12):
        s.hit('timp', k * (2.0 / 12), 0.35 + 0.05 * k, 0, 'perc', m=43)
    s.melody('brass', i_brass, "1:0.333 1:0.333 1:0.334 1:1 b6:1 b7:1 8:5", 0.0, base=s.key, vel=1.0, harm=-12)
    s.melody('brass', i_brass, "3:0.333 3:0.333 3:0.334 3:1 1:1 2:1 3:5", 0.0, base=s.key, vel=0.75)
    c = [(0, 2, 0, [0, 4, 7]), (2, 1, 8, [0, 4, 7]), (3, 1, 10, [0, 4, 7]), (4, 5, 0, [0, 4, 7])]
    s.pad('strings', i_strings, c, 60, 4, 0.6, att=0.05)
    s.pad('choir', i_choir, [(4, 5, 0, [0, 4, 7])], 67, 4, 0.7, att=0.1)
    s.bassline([(2, 1, 8, [0, 4, 7]), (3, 1, 10, [0, 4, 7]), (4, 5, 0, [0, 4, 7])], 'whole', 36, 0.9)
    s.hit('crash', 4, 1.0, 0.2)
    s.hit('boom', 4, 0.9, 0, 'perc')
    s.hit('timp', 2, 0.9, 0, 'perc', m=44)
    s.hit('timp', 3, 0.9, 0, 'perc', m=46)
    s.hit('timp', 4, 1.0, 0, 'perc', m=36)
    for k, d in enumerate([1, 3, 5, 8, 10, 12, 15, 17, 19, 22]):
        s.note('keys', i_harp, s.key + d, 4 + k * 0.08, 1.5, 0.6)
    for k, d in enumerate([0, 4, 7, 12, 7, 4]):
        s.note('bells', i_celesta, s.key + 24 + d, 4.5 + k * 0.25, 0.5, 0.45)
    return s


THEMES = {
    'elemental_guardian': th_elemental_guardian, 'abyss_sovereign': th_abyss_sovereign, 'seraph': th_seraph,
    'twilight_herald': th_twilight_herald, 'primordial_dragon': th_primordial_dragon, 'lich': th_lich, 'beast': th_beast,
    'mimic_king': th_mimic_king, 'hollow_king': th_hollow_king, 'ulquiorra': th_ulquiorra,
    'ulquiorra_segunda_etapa': th_ulquiorra_segunda_etapa, 'barragan': th_barragan, 'grimmjow': th_grimmjow,
    'las_noches': th_las_noches, 'generic_boss': th_generic_boss, 'boss_victory': th_boss_victory,
}


def build(name):
    import time
    t0 = time.time()
    s = THEMES[name]()
    x = s.render()
    path = write_ogg(name, x)
    size = os.path.getsize(path)
    pk = float(np.max(np.abs(x)))
    rms = 20 * math.log10(math.sqrt(float(np.mean(x ** 2))) + 1e-9)
    return f"{name}: {x.shape[1] / SR:.1f}s  {size / 1e6:.2f} MB  pico {pk:.2f}  rms {rms:.1f} dB  ({time.time() - t0:.0f}s)"


def merge_sounds_json():
    """Ler-modificar-gravar: só adiciona/atualiza as chaves boss_music.* (outros módulos também escrevem aqui)."""
    data = {}
    if os.path.exists(SOUNDS_JSON):
        with open(SOUNDS_JSON, encoding='utf-8') as f:
            data = json.load(f)
    for name in THEMES:
        data['boss_music.' + name] = {'sounds': [{'name': 'isekai_mahou:music/' + name, 'stream': True}]}
    with open(SOUNDS_JSON, 'w', encoding='utf-8') as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write('\n')


def main(argv):
    jobs = 1
    names = []
    i = 0
    while i < len(argv):
        if argv[i] == '--jobs':
            jobs = int(argv[i + 1])
            i += 2
            continue
        names.append(argv[i])
        i += 1
    names = names or list(THEMES)
    for n in names:
        if n not in THEMES:
            raise SystemExit(f"tema desconhecido: {n} (temas: {', '.join(THEMES)})")
    merge_sounds_json()
    if jobs > 1:
        from concurrent.futures import ProcessPoolExecutor
        with ProcessPoolExecutor(jobs) as ex:
            for line in ex.map(build, names):
                print(line, flush=True)
    else:
        for n in names:
            print(build(n), flush=True)


if __name__ == '__main__':
    main(sys.argv[1:])
