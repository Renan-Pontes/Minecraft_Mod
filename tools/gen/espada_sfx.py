"""
Sons sintetizados do modulo espada (numpy -> wav -> ffmpeg ogg), em assets/isekai_mahou/sounds/espada/.
Tambem junta as entradas no sounds.json (ler-mudar-gravar: nunca sobrescreve as chaves dos outros modulos).
Nada de audio baixado: tudo sai de osciladores, ruido e envelopes.

Uso:  py -I tools/gen/espada_sfx.py
"""
import json
import os
import subprocess
import sys
import tempfile
import wave

import numpy as np

ROOT = os.path.abspath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/isekai_mahou")
OUT = os.path.join(ASSETS, "sounds/espada")
SR = 44100
RNG = np.random.default_rng(1303)


def t_axis(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, attack, release, curve=2.0):
    """Envelope de ataque linear e queda exponencial (em segundos)."""
    t = np.arange(n) / SR
    a = np.clip(t / max(attack, 1e-4), 0, 1)
    total = n / SR
    r = np.clip((total - t) / max(release, 1e-4), 0, 1) ** curve
    return a * r


def noise(n):
    return RNG.uniform(-1, 1, n)


def lowpass(x, cutoff):
    """Passa-baixas de 1 polo (cutoff pode variar por amostra)."""
    cutoff = np.broadcast_to(np.asarray(cutoff, dtype=float), x.shape)
    y = np.zeros_like(x)
    acc = 0.0
    for i in range(len(x)):
        a = 1 - np.exp(-2 * np.pi * cutoff[i] / SR)
        acc += a * (x[i] - acc)
        y[i] = acc
    return y


def highpass(x, cutoff):
    return x - lowpass(x, cutoff)


def sweep(f0, f1, sec, shape="sine"):
    n = int(SR * sec)
    f = np.geomspace(max(f0, 1), max(f1, 1), n)
    ph = 2 * np.pi * np.cumsum(f) / SR
    if shape == "saw":
        return 2 * ((ph / (2 * np.pi)) % 1.0) - 1
    if shape == "square":
        return np.sign(np.sin(ph))
    return np.sin(ph)


def reverb(x, decay=0.5, taps=((0.031, 0.5), (0.047, 0.4), (0.071, 0.32), (0.113, 0.25), (0.163, 0.18))):
    out = np.copy(x)
    for d, g in taps:
        k = int(d * SR)
        pad = np.zeros(k)
        out = out + g * decay * np.concatenate([pad, x])[:len(x)]
    return out


def norm(x, peak=0.89):
    m = np.max(np.abs(x)) + 1e-9
    return x / m * peak


def save(name, x):
    os.makedirs(OUT, exist_ok=True)
    x = norm(x)
    data = (x * 32767).astype(np.int16)
    with tempfile.TemporaryDirectory() as tmp:
        wav_path = os.path.join(tmp, name + ".wav")
        with wave.open(wav_path, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(SR)
            w.writeframes(data.tobytes())
        ogg = os.path.join(OUT, name + ".ogg")
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav_path, "-c:a", "libvorbis", "-q:a", "4", ogg], check=True)


# ---------------------------------------------------------------- sons
def s_sonido():
    sec = 0.32
    n = int(SR * sec)
    hiss = highpass(noise(n), 2500) * env(n, 0.005, 0.25, 3)
    snap = sweep(3200, 500, sec) * env(n, 0.001, 0.08, 4)
    return hiss * 0.8 + snap * 0.5


def s_cero_charge():
    sec = 1.8
    t = t_axis(sec)
    n = len(t)
    f = np.geomspace(70, 260, n)
    ph = 2 * np.pi * np.cumsum(f) / SR
    hum = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.25 * np.sin(3.01 * ph)
    trem = 0.7 + 0.3 * np.sin(2 * np.pi * np.geomspace(4, 22, n) * t)
    crackle = (RNG.random(n) < np.linspace(0.001, 0.02, n)) * noise(n)
    amp = np.linspace(0.2, 1.0, n) ** 1.5 * env(n, 0.05, 0.08, 1)
    return (hum * trem * 0.6 + lowpass(crackle, 3000) * 0.8) * amp


def s_cero_fire():
    sec = 1.8
    t = t_axis(sec)
    n = len(t)
    thump = np.sin(2 * np.pi * np.cumsum(np.geomspace(90, 35, n)) / SR) * env(n, 0.002, 0.9, 2)
    roar = lowpass(noise(n), np.geomspace(5000, 400, n)) * env(n, 0.003, 1.6, 2)
    zap = sweep(1400, 200, sec, "saw") * env(n, 0.001, 0.4, 3) * 0.3
    return reverb(thump * 1.0 + roar * 0.9 + zap, 0.6)


def s_lanza_charge():
    sec = 2.4
    t = t_axis(sec)
    n = len(t)
    tone = sweep(180, 900, sec) * (0.6 + 0.4 * np.sin(2 * np.pi * 30 * t))
    zaps = np.zeros(n)
    for _ in range(70):
        p = int(RNG.uniform(0, n - 2000))
        L = int(RNG.uniform(200, 1800))
        zaps[p:p + L] += noise(L) * np.exp(-np.arange(L) / (L / 4)) * RNG.uniform(0.3, 1.0)
    zaps = highpass(zaps, 1500)
    amp = np.linspace(0.25, 1.0, n) * env(n, 0.05, 0.1, 1)
    return (tone * 0.45 + zaps * 0.8) * amp


def s_lanza_explode():
    sec = 4.0
    t = t_axis(sec)
    n = len(t)
    boom = np.sin(2 * np.pi * np.cumsum(np.geomspace(70, 22, n)) / SR) * env(n, 0.003, 3.5, 1.6)
    blast = lowpass(noise(n), np.geomspace(8000, 150, n)) * env(n, 0.002, 3.8, 1.8)
    rumble = lowpass(noise(n), 120) * env(n, 0.3, 3.0, 1) * 3.0
    crack = highpass(noise(n), 3000) * env(n, 0.001, 0.25, 4)
    return reverb(boom * 1.2 + blast + rumble + crack * 0.7, 0.8)


def s_release():
    sec = 2.8
    t = t_axis(sec)
    n = len(t)
    drone = (np.sin(2 * np.pi * 55 * t) + 0.6 * np.sin(2 * np.pi * 82.5 * t) + 0.4 * np.sin(2 * np.pi * 110.7 * t))
    swell = np.clip(t / 1.6, 0, 1) ** 2
    burst_at = int(1.6 * SR)
    burst = np.zeros(n)
    m = n - burst_at
    burst[burst_at:] = lowpass(noise(m), np.geomspace(6000, 300, m)) * env(m, 0.002, 1.1, 2) * 1.6
    wind = lowpass(noise(n), 600 + 500 * np.sin(2 * np.pi * 0.7 * t) ** 2) * swell
    return reverb(drone * swell * env(n, 0.05, 0.6, 1) * 0.7 + burst + wind * 0.8, 0.7)


def s_respira():
    sec = 2.2
    t = t_axis(sec)
    n = len(t)
    breath = lowpass(noise(n), 900 + 700 * np.sin(np.pi * t / sec)) * np.sin(np.pi * np.clip(t / sec, 0, 1)) ** 0.8
    rasp = highpass(noise(n), 2000) * 0.25 * (0.5 + 0.5 * np.sin(2 * np.pi * 7 * t)) * np.sin(np.pi * t / sec)
    low = np.sin(2 * np.pi * 48 * t) * 0.35 * np.sin(np.pi * t / sec)
    return reverb(breath + rasp + low, 0.5)


def s_crumble():
    sec = 0.9
    n = int(SR * sec)
    x = np.zeros(n)
    dens = np.linspace(0.006, 0.0005, n)
    hits = np.where(RNG.random(n) < dens)[0]
    for h in hits:
        L = int(RNG.uniform(60, 500))
        seg = noise(min(L, n - h)) * np.exp(-np.arange(min(L, n - h)) / (L / 5))
        x[h:h + len(seg)] += seg * RNG.uniform(0.3, 1.0)
    return lowpass(x, 3500) + highpass(noise(n), 4000) * env(n, 0.01, 0.6, 2) * 0.15


def s_claw():
    sec = 0.45
    t = t_axis(sec)
    n = len(t)
    swish = highpass(noise(n), np.geomspace(800, 6000, n)) * env(n, 0.01, 0.35, 2)
    ring = (np.sin(2 * np.pi * 2300 * t) + 0.5 * np.sin(2 * np.pi * 3710 * t)) * env(n, 0.001, 0.3, 3) * 0.3
    return swish + ring


def s_roar():
    sec = 1.6
    t = t_axis(sec)
    n = len(t)
    f = 95 + 25 * np.sin(2 * np.pi * 3 * t) + 18 * lowpass(noise(n), 40) * 8
    ph = 2 * np.pi * np.cumsum(f) / SR
    growl = (2 * ((ph / (2 * np.pi)) % 1.0) - 1)
    growl = lowpass(growl, 1400) * (0.8 + 0.2 * lowpass(noise(n), 25) * 6)
    breath = lowpass(noise(n), 1800) * 0.5
    amp = env(n, 0.08, 1.0, 1.5)
    return reverb((growl + breath) * amp, 0.5)


def s_bala():
    sec = 0.18
    n = int(SR * sec)
    zap = sweep(1500, 350, sec) * env(n, 0.001, 0.15, 2)
    click = highpass(noise(n), 3000) * env(n, 0.0005, 0.03, 4)
    return zap + click * 0.6


def s_hierro():
    sec = 0.8
    t = t_axis(sec)
    n = len(t)
    parts = [(620, 1.0, 0.5), (1563, 0.6, 0.35), (2471, 0.4, 0.25), (3890, 0.25, 0.15)]
    x = sum(a * np.sin(2 * np.pi * f * t) * np.exp(-t / d) for f, a, d in parts)
    hit = highpass(noise(n), 2000) * env(n, 0.0005, 0.04, 4)
    return x + hit * 0.8


SOUNDS = {
    "sonido": s_sonido, "cero_charge": s_cero_charge, "cero_fire": s_cero_fire, "lanza_charge": s_lanza_charge,
    "lanza_explode": s_lanza_explode, "release": s_release, "respira": s_respira, "crumble": s_crumble, "claw": s_claw,
    "roar": s_roar, "bala": s_bala, "hierro": s_hierro,
}


def merge_sounds_json():
    path = os.path.join(ASSETS, "sounds.json")
    data = {}
    if os.path.exists(path):
        with open(path, encoding="utf-8") as f:
            txt = f.read().strip()
            data = json.loads(txt) if txt else {}
    for name in SOUNDS:
        data["espada." + name] = {"subtitle": "subtitles.isekai_mahou.espada." + name,
                                  "sounds": [{"name": "isekai_mahou:espada/" + name}]}
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")


def main():
    only = sys.argv[1:]
    for name, fn in SOUNDS.items():
        if only and name not in only:
            continue
        save(name, fn())
        print("som:", name)
    merge_sounds_json()
    print("sounds.json atualizado")


if __name__ == "__main__":
    main()
