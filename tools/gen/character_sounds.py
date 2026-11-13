# -*- coding: utf-8 -*-
"""Sons das Transformações raciais (v1.3), sintetizados do zero com numpy (nada baixado).

Gera em src/main/resources/assets/isekai_mahou/sounds/character/:
  form_charge.ogg (carga de 2,5 s), form_burst.ogg (explosão), form_end.ogg (desfazer),
  roar_<raca>.ogg (rugido/assinatura de cada uma das 11 raças).
Todos MONO (som posicional). Também mescla as chaves no sounds.json (lê, acrescenta, grava).

Uso: py -I tools/gen/character_sounds.py
"""
import json
import os
import subprocess
import sys
import wave

import numpy as np

SR = 44100
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "sounds", "character")
SOUNDS_JSON = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou", "sounds.json")
TMP = os.path.join(ROOT, "build", "tmp_character_sounds")
rng = np.random.default_rng(1307)

RACES = ["human", "elf", "dwarf", "half_orc", "demon", "dragonborn", "beastfolk", "arachnid", "phantom", "angel", "slime"]


# ------------------------------------------------------------------ utilidades
def t_axis(sec):
    return np.arange(int(sec * SR)) / SR


def noise(n):
    return rng.standard_normal(n)


def fft_filter(x, gain_fn):
    """Filtro por espectro: gain_fn(freqs) -> ganho."""
    n = len(x)
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(n, 1 / SR)
    return np.fft.irfft(X * gain_fn(f), n)


def lowpass(x, fc, order=2):
    return fft_filter(x, lambda f: 1 / np.sqrt(1 + (f / fc) ** (2 * order)))


def highpass(x, fc, order=2):
    return fft_filter(x, lambda f: 1 / np.sqrt(1 + (fc / np.maximum(f, 1e-3)) ** (2 * order)))


def bandpass(x, fc, q):
    bw = fc / q
    return fft_filter(x, lambda f: np.exp(-0.5 * ((f - fc) / bw) ** 2))


def formants(x, peaks):
    """peaks = [(freq, largura, ganho)]: vogal/rugido."""
    def g(f):
        out = np.zeros_like(f) + 0.02
        for fc, bw, gain in peaks:
            out += gain * np.exp(-0.5 * ((f - fc) / bw) ** 2)
        return out
    return fft_filter(x, g)


def env_adsr(n, a, d, s, r):
    a, d, r = int(a * SR), int(d * SR), int(r * SR)
    sus = max(0, n - a - d - r)
    e = np.concatenate([np.linspace(0, 1, max(1, a)), np.linspace(1, s, max(1, d)), np.full(sus, s), np.linspace(s, 0, max(1, r))])
    return e[:n] if len(e) >= n else np.pad(e, (0, n - len(e)))


def glide_phase(f0, f1, n, curve=1.0):
    k = np.linspace(0, 1, n) ** curve
    freq = f0 + (f1 - f0) * k
    return 2 * np.pi * np.cumsum(freq) / SR, freq


def saw(phase):
    return 2 * ((phase / (2 * np.pi)) % 1.0) - 1


def soft_clip(x, drive=2.0):
    return np.tanh(x * drive) / np.tanh(drive)


def reverb(x, sec=1.2, mix=0.3, damp=3000):
    n = int(sec * SR)
    ir = noise(n) * np.exp(-np.linspace(0, 6, n))
    ir = lowpass(ir, damp, 1)
    ir /= np.sqrt(np.sum(ir ** 2)) + 1e-9
    m = len(x) + n
    y = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(ir, m), m)
    dry = np.pad(x, (0, n))
    return dry * (1 - mix) + y * mix


def vibrato(n, rate, depth):
    return 1 + depth * np.sin(2 * np.pi * rate * np.arange(n) / SR)


def normalize(x, peak=0.89):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) + 1e-9
    y = x / m * peak
    # cauda sem estalo
    fade = min(len(y), int(0.03 * SR))
    y[-fade:] *= np.linspace(1, 0, fade)
    y[:64] *= np.linspace(0, 1, 64)
    return y


def write(name, x):
    os.makedirs(TMP, exist_ok=True)
    os.makedirs(OUT, exist_ok=True)
    x = normalize(x)
    wav = os.path.join(TMP, name + ".wav")
    with wave.open(wav, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((x * 32767).astype(np.int16).tobytes())
    ogg = os.path.join(OUT, name + ".ogg")
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", wav, "-ac", "1", "-c:a", "libvorbis", "-q:a", "4", ogg], check=True)
    print("ok", name, round(len(x) / SR, 2), "s")


def growl(sec, f0, f1, peaks, rough=0.6, drive=3.0, vib=(5.5, 0.04)):
    """Rugido: serra com tremor + ruído, moldados por formantes e saturados."""
    n = int(sec * SR)
    ph, _ = glide_phase(f0, f1, n, 0.7)
    ph = ph * 1.0
    jitter = np.cumsum(noise(n)) / SR * 40  # oscilação irregular de garganta
    ph = ph + jitter * 0.6
    v = vibrato(n, *vib)
    src = saw(ph * v) * 0.7 + saw(ph * 1.007) * 0.5 + saw(ph * 0.5) * 0.4
    am = 1 + rough * lowpass(noise(n), 40, 1) * 3  # aspereza
    src = src * am + noise(n) * 0.5
    y = formants(src, peaks)
    return soft_clip(y / (np.max(np.abs(y)) + 1e-9), drive)


def boom(sec, f0=70, f1=28, decay=4.0):
    n = int(sec * SR)
    ph, _ = glide_phase(f0, f1, n, 0.5)
    sub = np.sin(ph) * np.exp(-np.linspace(0, decay, n))
    crack = lowpass(noise(n), 2500, 1) * np.exp(-np.linspace(0, 14, n))
    rumble = lowpass(noise(n), 180, 2) * np.exp(-np.linspace(0, decay * 0.8, n)) * 2.5
    return sub * 1.4 + crack * 0.8 + rumble


# ------------------------------------------------------------------ sons comuns
def form_charge():
    sec = 2.6
    n = int(sec * SR)
    t = np.arange(n) / SR
    grow = (t / sec) ** 1.6
    ph, _ = glide_phase(38, 74, n, 1.4)
    sub = np.sin(ph) * (0.4 + 0.6 * grow)
    rumble = lowpass(noise(n), 140, 2) * (0.3 + 1.2 * grow) * 3
    ph2, _ = glide_phase(220, 1300, n, 2.2)
    whine = (np.sin(ph2) + 0.5 * np.sin(ph2 * 1.5)) * grow ** 2 * 0.35
    air = highpass(noise(n), 3000) * grow ** 2.5 * 0.25
    pulse = 0.75 + 0.25 * np.sin(2 * np.pi * (2 + 8 * grow) * t)
    y = (sub * 1.2 + rumble + whine + air) * pulse
    return reverb(y, 0.8, 0.2)


def form_burst():
    y = boom(1.8, 90, 26, 3.5)
    n = len(y)
    shimmer = highpass(noise(n), 5000) * np.exp(-np.linspace(0, 6, n)) * 0.5
    return reverb(soft_clip(y + shimmer, 1.6), 1.6, 0.35, 2200)


def form_end():
    sec = 1.3
    n = int(sec * SR)
    k = np.linspace(0, 1, n)
    wh = noise(n)
    # varredura de filtro descendo (whoosh reverso que se apaga)
    out = np.zeros(n)
    seg = 8
    for i in range(seg):
        a, b = i * n // seg, (i + 1) * n // seg
        fc = 4000 * (1 - i / seg) + 200
        out[a:b] = bandpass(wh, fc, 1.5)[a:b]
    ph, _ = glide_phase(660, 180, n, 0.8)
    tone = np.sin(ph) * 0.25
    e = np.sin(np.pi * np.minimum(1, k * 1.6)) ** 1.5
    return reverb((out * 1.5 + tone) * e, 1.0, 0.4)


# ------------------------------------------------------------------ assinaturas por raça
def roar_human():
    # Herói Desperto: acorde de metais heroico (maior) + sino
    sec = 2.2
    n = int(sec * SR)
    y = np.zeros(n)
    for f, g in [(130.8, 1.0), (196.0, 0.8), (261.6, 0.7), (329.6, 0.6), (392.0, 0.45)]:
        ph, _ = glide_phase(f * 0.97, f, n, 0.3)
        y += saw(ph * vibrato(n, 5, 0.004)) * g
    y = lowpass(y, 1800, 2) * env_adsr(n, 0.15, 0.3, 0.8, 0.9)
    bell = np.zeros(n)
    for f, d in [(784, 3), (1568 * 1.01, 5), (2350, 7)]:
        bell += np.sin(2 * np.pi * f * t_axis(sec)) * np.exp(-np.linspace(0, d, n))
    return reverb(y + bell * 0.35 + boom(sec, 70, 40, 6) * 0.5, 1.6, 0.35)


def roar_elf():
    # Espírito da lua: arpejo de sinos etéreos + pad de ar
    sec = 2.4
    n = int(sec * SR)
    t = t_axis(sec)
    y = np.zeros(n)
    notes = [659.3, 880, 1046.5, 1318.5, 1760]
    for i, f in enumerate(notes):
        start = int(i * 0.12 * SR)
        m = n - start
        e = np.exp(-np.linspace(0, 4, m))
        tone = np.sin(2 * np.pi * f * t[:m]) + 0.3 * np.sin(2 * np.pi * f * 2.76 * t[:m]) * np.exp(-np.linspace(0, 9, m))
        y[start:] += tone * e * 0.6
    pad = sum(np.sin(2 * np.pi * f * t * vibrato(n, 0.3, 0.003)) for f in (220, 329.6, 440)) * env_adsr(n, 0.6, 0.2, 0.7, 1.0) * 0.4
    air = bandpass(noise(n), 6000, 1.2) * env_adsr(n, 0.8, 0.2, 0.5, 1.0) * 0.25
    return reverb(y + pad + air, 2.2, 0.5, 5000)


def roar_dwarf():
    # Colosso de pedra: rocha moendo + bigorna + rosnado grave
    sec = 2.2
    n = int(sec * SR)
    t = t_axis(sec)
    grind = bandpass(noise(n), 250, 0.8) * (1 + lowpass(noise(n), 25, 1) * 4) * env_adsr(n, 0.2, 0.4, 0.7, 0.8)
    anvil = np.zeros(n)
    for hit in (0.0, 0.55):
        s = int(hit * SR)
        m = n - s
        for f, d in [(1180, 6), (2650, 9), (3720, 12), (520, 4)]:
            anvil[s:] += np.sin(2 * np.pi * f * t[:m]) * np.exp(-np.linspace(0, d, m))
    g = growl(sec, 70, 55, [(300, 120, 1.0), (700, 200, 0.6)], rough=0.8, drive=3)
    return reverb(grind * 1.4 + anvil * 0.4 + g * 0.6 + boom(sec, 60, 30, 3) * 0.8, 1.2, 0.3, 2000)


def roar_half_orc():
    # Berserker: grito gutural saturado
    sec = 1.9
    n = int(sec * SR)
    g = growl(sec, 110, 85, [(520, 140, 1.0), (1100, 220, 0.8), (2400, 400, 0.3)], rough=1.0, drive=4)
    e = env_adsr(n, 0.08, 0.2, 0.85, 0.6)
    return reverb(g * e + boom(sec, 65, 35, 5) * 0.5, 1.0, 0.25)


def roar_demon():
    # Demônio: rugido em camadas (grave + oitava abaixo) com crepitar de fogo infernal
    sec = 2.4
    n = int(sec * SR)
    g1 = growl(sec, 75, 52, [(380, 120, 1.0), (850, 200, 0.7), (1900, 300, 0.25)], rough=1.0, drive=5)
    g2 = growl(sec, 38, 30, [(220, 100, 1.0), (480, 150, 0.5)], rough=0.7, drive=4)
    crackle = highpass((rng.random(n) > 0.997).astype(float) * noise(n) * 4, 1500) * env_adsr(n, 0.3, 0.2, 0.8, 0.8)
    fire = bandpass(noise(n), 900, 0.6) * env_adsr(n, 0.4, 0.3, 0.6, 1.0) * 0.6
    e = env_adsr(n, 0.15, 0.3, 0.85, 0.9)
    return reverb((g1 + g2 * 0.8) * e + crackle * 0.5 + fire, 1.8, 0.35, 1800)


def roar_dragonborn():
    # Dragão: rugido longo descendo + respiro de fumaça
    sec = 2.6
    n = int(sec * SR)
    g = growl(sec, 95, 48, [(420, 150, 1.0), (980, 250, 0.8), (2200, 350, 0.4)], rough=0.9, drive=4.5, vib=(7, 0.05))
    breath = bandpass(noise(n), 1200, 0.5) * env_adsr(n, 0.05, 0.5, 0.4, 1.2) * 0.5
    e = env_adsr(n, 0.12, 0.4, 0.8, 1.0)
    return reverb(g * e + breath + boom(sec, 55, 28, 2.5) * 0.7, 2.0, 0.35, 1600)


def roar_beastfolk():
    # Uivo de lobisomem: sobe, sustenta com tremor, cai
    sec = 2.6
    n = int(sec * SR)
    k = np.linspace(0, 1, n)
    freq = 260 + 260 * np.clip(k * 3, 0, 1) - 120 * np.clip((k - 0.7) / 0.3, 0, 1)
    freq = freq * vibrato(n, 6, 0.015)
    ph = 2 * np.pi * np.cumsum(freq) / SR
    src = np.sin(ph) + 0.5 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph) + noise(n) * 0.08
    y = formants(src, [(600, 200, 1.0), (1000, 250, 0.6), (2600, 400, 0.15)])
    g = growl(0.6, 90, 70, [(450, 150, 1.0), (1000, 200, 0.5)], rough=1.0, drive=4)
    g = np.pad(g * env_adsr(len(g), 0.02, 0.1, 0.8, 0.2), (0, n - len(g)))
    e = env_adsr(n, 0.25, 0.2, 0.9, 0.7)
    return reverb(y * e + g * 0.7, 2.4, 0.45, 3500)


def roar_arachnid():
    # Aracne: estalos de quelíceras + chiado + zumbido grave
    sec = 2.0
    n = int(sec * SR)
    t = t_axis(sec)
    clicks = np.zeros(n)
    pos = 0.0
    while pos < sec - 0.02:
        s = int(pos * SR)
        m = min(n - s, int(0.012 * SR))
        clicks[s:s + m] += noise(m) * np.exp(-np.linspace(0, 8, m))
        pos += rng.uniform(0.018, 0.07)
    clicks = highpass(clicks, 1800)
    hiss = bandpass(noise(n), 5200, 1.0) * env_adsr(n, 0.3, 0.3, 0.8, 0.6)
    drone = np.sin(2 * np.pi * 55 * t) * (1 + 0.5 * np.sin(2 * np.pi * 23 * t)) * env_adsr(n, 0.2, 0.2, 0.8, 0.6)
    return reverb(clicks * 1.2 + hiss * 0.7 + drone * 0.6, 1.4, 0.35)


def roar_phantom():
    # Lamento fantasma: senoide com vibrato lento e curvas + sussurros
    sec = 2.6
    n = int(sec * SR)
    k = np.linspace(0, 1, n)
    freq = 520 + 180 * np.sin(np.pi * k * 1.5) + 40 * np.sin(2 * np.pi * 3.5 * k * 2.6)
    ph = 2 * np.pi * np.cumsum(freq) / SR
    wail = (np.sin(ph) + 0.35 * np.sin(ph * 1.5 + 0.3) + 0.2 * np.sin(ph * 0.5)) * env_adsr(n, 0.5, 0.3, 0.8, 1.0)
    whisper = formants(noise(n), [(900, 300, 1.0), (2500, 500, 0.8)]) * (0.5 + 0.5 * lowpass(noise(n), 6, 1) * 6) * 0.25
    chain = np.zeros(n)
    for hit in (0.4, 0.9, 1.5):
        s = int(hit * SR)
        m = n - s
        for f in (2100, 3300, 4700):
            chain[s:] += np.sin(2 * np.pi * f * np.arange(m) / SR) * np.exp(-np.linspace(0, 14, m))
    return reverb(wail + whisper + chain * 0.12, 2.8, 0.55, 3000)


def roar_angel():
    # Coro celestial "aah" (acorde maior) + brilho
    sec = 2.8
    n = int(sec * SR)
    y = np.zeros(n)
    for f in (261.6, 329.6, 392.0, 523.3, 659.3):
        for det in (0.996, 1.0, 1.004):
            ph = 2 * np.pi * f * det * np.arange(n) / SR
            y += saw(ph * vibrato(n, 5.2 * det, 0.006)) * 0.3
    y = formants(y, [(800, 150, 1.0), (1150, 160, 0.6), (2900, 300, 0.3)])
    y *= env_adsr(n, 0.5, 0.3, 0.85, 1.0)
    sparkle = highpass(noise(n), 7000) * (rng.random(n) > 0.995) * 6 * env_adsr(n, 0.2, 0.2, 0.8, 1.0)
    return reverb(y + sparkle * 0.3, 3.0, 0.55, 6000)


def roar_slime():
    # Rei Slime: borbulhar grave e gosmento + "glorp"
    sec = 2.0
    n = int(sec * SR)
    y = np.zeros(n)
    pos = 0.0
    while pos < sec - 0.15:
        s = int(pos * SR)
        dur = rng.uniform(0.06, 0.16)
        m = min(n - s, int(dur * SR))
        f0 = rng.uniform(70, 220)
        ph, _ = glide_phase(f0, f0 * rng.uniform(1.6, 2.6), m, 0.6)
        y[s:s + m] += np.sin(ph) * np.sin(np.pi * np.linspace(0, 1, m)) ** 2
        pos += rng.uniform(0.04, 0.12)
    ph, _ = glide_phase(140, 45, n, 0.4)
    glorp = np.sin(ph + 3 * np.sin(2 * np.pi * 7 * np.arange(n) / SR)) * env_adsr(n, 0.05, 0.6, 0.4, 0.8)
    wet = lowpass(noise(n), 400, 2) * env_adsr(n, 0.1, 0.4, 0.5, 0.8) * 2
    return reverb(lowpass(y * 0.8 + glorp + wet, 1500, 2), 1.0, 0.3, 1500)


def merge_sounds_json():
    data = {}
    if os.path.exists(SOUNDS_JSON):
        with open(SOUNDS_JSON, encoding="utf-8") as f:
            txt = f.read().strip()
            data = json.loads(txt) if txt else {}
    def entry(path, subtitle):
        return {"subtitle": subtitle, "sounds": [{"name": "isekai_mahou:character/" + path, "stream": False}]}
    data["race.form_charge"] = entry("form_charge", "subtitles.isekai_mahou.race.form_charge")
    data["race.form_burst"] = entry("form_burst", "subtitles.isekai_mahou.race.form_burst")
    data["race.form_end"] = entry("form_end", "subtitles.isekai_mahou.race.form_end")
    for r in RACES:
        data["race.roar." + r] = entry("roar_" + r, "subtitles.isekai_mahou.race.roar")
    with open(SOUNDS_JSON, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("sounds.json:", len(data), "chaves")


def main():
    only = sys.argv[1:]
    jobs = {"form_charge": form_charge, "form_burst": form_burst, "form_end": form_end}
    for r in RACES:
        jobs["roar_" + r] = globals()["roar_" + r]
    for name, fn in jobs.items():
        if only and name not in only:
            continue
        write(name, fn())
    merge_sounds_json()


if __name__ == "__main__":
    main()
