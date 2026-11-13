"""Sintetiza os sons da classe Hollow (v1.3) com numpy e codifica em .ogg com ffmpeg, e junta as entradas no
sounds.json (lê, soma as chaves "hollow.*", grava — nunca sobrescreve o arquivo inteiro).

Sons: roar (rugido do Hollow), cero_charge (carga subindo), cero_fire (disparo), sonido (estalo do passo),
garganta (céu rasgando), devour (almas sugadas), mask (a máscara se fechando).

Uso: py -I tools/gen/class_hollow_sfx.py
"""
import json
import os
import subprocess
import tempfile
import wave

import numpy as np

SR = 44100
ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "src", "main", "resources", "assets", "isekai_mahou")
OUT = os.path.join(ROOT, "sounds", "class_hollow")
rng = np.random.default_rng(13)


def t(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, a, r):
    """Ataque/soltura lineares (em amostras)."""
    e = np.ones(n)
    a = max(1, int(a))
    r = max(1, int(r))
    e[:a] = np.linspace(0, 1, a)
    e[-r:] *= np.linspace(1, 0, r)
    return e


def lowpass(x, k):
    k = max(1, int(k))
    return np.convolve(x, np.ones(k) / k, mode="same")


def norm(x, peak=0.9):
    m = np.max(np.abs(x)) or 1.0
    return x / m * peak


def roar():
    tt = t(2.4)
    n = len(tt)
    f0 = 70 + 40 * np.exp(-tt * 1.2) + 8 * np.sin(tt * 11)
    ph = 2 * np.pi * np.cumsum(f0) / SR
    voice = np.sign(np.sin(ph)) * 0.5 + np.sin(ph * 2) * 0.3 + np.sin(ph * 3.01) * 0.2
    noise = lowpass(rng.normal(0, 1, n), 6) * 0.8
    growl = (1 + 0.6 * np.sin(2 * np.pi * 23 * tt)) * voice
    x = np.tanh((growl + noise * (0.6 + 0.4 * np.sin(tt * 3))) * 2.5)
    # segunda voz (a voz dupla do Hollow), uma oitava acima e atrasada
    ph2 = 2 * np.pi * np.cumsum(f0 * 2.02) / SR
    x += 0.35 * np.tanh(np.sin(ph2) * 3) * env(n, SR * 0.3, SR * 0.8)
    return norm(lowpass(x, 3) * env(n, SR * 0.08, SR * 1.0))


def cero_charge():
    tt = t(1.2)
    n = len(tt)
    f = 180 + 900 * (tt / tt[-1]) ** 1.6
    ph = 2 * np.pi * np.cumsum(f) / SR
    x = np.sin(ph) * 0.5 + np.sin(ph * 1.5) * 0.25 + rng.normal(0, 0.15, n) * (tt / tt[-1])
    x *= 0.6 + 0.4 * np.sin(2 * np.pi * (8 + 30 * tt) * tt)
    return norm(x * env(n, SR * 0.2, SR * 0.05))


def cero_fire():
    tt = t(1.4)
    n = len(tt)
    boom = rng.normal(0, 1, n)
    boom = lowpass(boom, 30) * np.exp(-tt * 3.0) * 3
    zap_f = 1400 * np.exp(-tt * 4) + 90
    zap = np.sin(2 * np.pi * np.cumsum(zap_f) / SR) * np.exp(-tt * 2.5)
    sub = np.sin(2 * np.pi * 45 * tt) * np.exp(-tt * 2)
    x = np.tanh((boom + zap * 0.8 + sub * 1.2) * 1.8)
    return norm(x * env(n, SR * 0.004, SR * 0.4))


def sonido():
    tt = t(0.35)
    n = len(tt)
    click = rng.normal(0, 1, n) * np.exp(-tt * 60)
    whoosh = lowpass(rng.normal(0, 1, n), 4) * np.exp(-((tt - 0.08) ** 2) / 0.002)
    tone = np.sin(2 * np.pi * (2400 * np.exp(-tt * 20) + 300) * tt) * np.exp(-tt * 30)
    return norm(click * 0.9 + whoosh * 0.6 + tone * 0.4)


def garganta():
    tt = t(2.0)
    n = len(tt)
    # rasgar: ruído em rajadas (pano rasgando) + um grave que abre
    bursts = np.zeros(n)
    pos = 0
    while pos < n * 0.7:
        ln = int(SR * rng.uniform(0.01, 0.04))
        bursts[pos:pos + ln] += rng.normal(0, 1, min(ln, n - pos)) * rng.uniform(0.4, 1.0)
        pos += int(ln * rng.uniform(0.6, 1.4))
    bursts = bursts - lowpass(bursts, 20)
    low = np.sin(2 * np.pi * (40 + 30 * tt) * tt) * (1 - np.exp(-tt * 3)) * np.exp(-(tt - 1.0).clip(0) * 2)
    wind = lowpass(rng.normal(0, 1, n), 40) * 2 * np.sin(np.pi * tt / tt[-1])
    return norm(np.tanh(bursts * 1.5 + low * 1.4 + wind) * env(n, SR * 0.01, SR * 0.4))


def devour():
    tt = t(0.9)
    n = len(tt)
    f = 900 * np.exp(-tt * 2.5) + 120
    wail = np.sin(2 * np.pi * np.cumsum(f) / SR) * (0.5 + 0.5 * np.sin(2 * np.pi * 7 * tt))
    suck = lowpass(rng.normal(0, 1, n), 8) * np.linspace(0.2, 1, n)
    gulp = np.sin(2 * np.pi * 70 * tt) * np.exp(-((tt - 0.75) ** 2) / 0.002) * 2
    return norm((wail * 0.5 + suck * 0.6 + gulp) * env(n, SR * 0.05, SR * 0.1))


def mask():
    tt = t(0.8)
    n = len(tt)
    crack = np.zeros(n)
    for k in range(6):
        at = int(SR * (0.02 + k * 0.05 + rng.uniform(0, 0.02)))
        ln = int(SR * 0.03)
        crack[at:at + ln] += rng.normal(0, 1, ln) * np.exp(-np.arange(ln) / (SR * 0.006))
    thud = np.sin(2 * np.pi * 110 * tt) * np.exp(-((tt - 0.33) ** 2) / 0.0015) * 1.5
    hiss = rng.normal(0, 0.2, n) * np.exp(-tt * 3)
    return norm(crack + thud + hiss)


SOUNDS = {
    "roar": roar,
    "cero_charge": cero_charge,
    "cero_fire": cero_fire,
    "sonido": sonido,
    "garganta": garganta,
    "devour": devour,
    "mask": mask,
}


def write_ogg(name, data):
    os.makedirs(OUT, exist_ok=True)
    pcm = (np.clip(data, -1, 1) * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav = os.path.join(tmp, name + ".wav")
        with wave.open(wav, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(pcm.tobytes())
        ogg = os.path.join(OUT, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-c:a", "libvorbis", "-q:a", "4", ogg], check=True)
    print("ok", name)


def merge_json():
    path = os.path.join(ROOT, "sounds.json")
    data = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
    for name in SOUNDS:
        data["hollow." + name] = {
            "subtitle": "subtitles.isekai_mahou.hollow." + name,
            "sounds": [{"name": "isekai_mahou:class_hollow/" + name, "stream": False}],
        }
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("sounds.json:", len(data), "entradas")


if __name__ == "__main__":
    for name, fn in SOUNDS.items():
        write_ogg(name, fn())
    merge_json()
