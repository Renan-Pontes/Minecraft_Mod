# Importa as músicas que o Renan coloca em MUSICAS_DOS_CHEFES/<tema>/ para dentro do mod.
# Uso: py -X utf8 tools/music_import.py   (depois: ./gradlew build --offline)
#
# Para cada tema com um arquivo de áudio:
#  - acha o "drop" (onde a música explode depois da intro) — automático ou pelo ajuste manual em DROPS;
#  - corta a intro para no máximo KEEP s antes do drop (fade in curto) e grava sounds/music/<tema>.ogg;
#  - grava em BossMusicCues.java quantos ticks faltam do início da faixa até o drop: a ENTRADA do chefe
#    (feature/boss_music/BossEntrance) dura exatamente isso e o chefe "acorda" no drop;
#  - recorta o FINAL da música (a última frase forte + cauda) como vinheta de vitória: sounds/music/victory/<tema>.ogg.
# A faixa procedural original fica em tools/gen/boss_music_original/; pasta vazia = volta a original.
#
# Release 1.0 (perf): as faixas saem em Vorbis q2 (~90 kbps, antes q3 ~106 kbps) — ~19% menores. As fontes são MP3 com
# corte em 16 kHz, então a diferença não é audível no jogo. Para RECOMPRIMIR o que já está no mod sem mexer em mais nada
# (não toca em sounds.json nem em BossMusicCues.java; mesmos cortes, mesma duração):
#     py -X utf8 tools/music_import.py --recompress            (usa QUALITY)
#     py -X utf8 tools/music_import.py --recompress --q 1.5    (ainda menor: ~-31%; 1 = ~-36%)
#     py -X utf8 tools/music_import.py --recompress --dry      (só mostra o que mudaria)
import json, os, shutil, subprocess, sys
import numpy as np

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "MUSICAS_DOS_CHEFES")
RES = os.path.join(ROOT, "src", "main", "resources", "assets", "isekai_mahou")
DST = os.path.join(RES, "sounds", "music")
VIC = os.path.join(DST, "victory")
BAK = os.path.join(ROOT, "tools", "gen", "boss_music_original")
CUES_JAVA = os.path.join(ROOT, "src", "main", "java", "com", "renanpontes", "isekaimahou", "feature", "boss_music",
                         "BossMusicCues.java")
AUDIO = {".mp3", ".m4a", ".wav", ".ogg", ".flac", ".webm", ".opus", ".aac", ".wma", ".mp4", ".mkv"}
SR = 22050
HOP = 512
KEEP = 12.0  # segundos máximos de intro antes do drop (a entrada do chefe dura isso)
QUALITY = 2  # qualidade do libvorbis (-q:a). 3 = ~106 kbps (até a v1.6), 2 = ~90 kbps, 1.5 = ~78 kbps, 1 = ~70 kbps

THEMES = [
    ("ulquiorra", "Ulquiorra (1a fase e Murcielago)"),
    ("ulquiorra_segunda_etapa", "Ulquiorra - Segunda Etapa"),
    ("barragan", "Barragan"),
    ("grimmjow", "Grimmjow"),
    ("hollow_king", "Rei Hollow (Hueco Mundo)"),
    ("las_noches", "Ambiente dentro do palacio Las Noches"),
    ("flying_carpet", "Musica tocando enquanto voa no Tapete Voador"),
    ("elemental_guardian", "Guardioes Elementais"),
    ("abyss_sovereign", "Soberano do Abismo"),
    ("seraph", "Serafim"),
    ("twilight_herald", "Arauto do Crepusculo"),
    ("primordial_dragon", "Dragao Primordial"),
    ("lich", "Lich da Cripta"),
    ("beast", "Feras: Hidra, Basilisco, Wyvern, Manticora, Colosso"),
    ("mimic_king", "Rei Mimico"),
    ("generic_boss", "Qualquer outro chefe"),
    ("boss_victory", "Vinheta de vitoria padrao (opcional: cada musica ja gera a sua pelo proprio final)"),
]
# Pastas OPCIONAIS por chefe (id da entidade): com música, o chefe toca a dele; vazia, toca o tema da família acima.
# Não há faixa original: esvaziar a pasta tira a faixa do mod (sounds.json e BossMusicCues.BOSS_TRACKS).
BOSS_FOLDERS = [
    ("ignis", "Ignis (guardiao do fogo)"),
    ("tide_guardian", "Mare (guardiao da agua)"),
    ("zephyr", "Zefiro (guardiao do vento)"),
    ("primordial_golem", "Golem Primordial (guardiao da terra)"),
    ("renegade_archmage", "Arquimago Renegado"),
    ("corrupted_witch", "Bruxa Corrompida"),
    ("imprisoned_patron", "Patrono Aprisionado"),
    ("corrupted_ent", "Ent Corrompido"),
    ("trial_avatar", "Avatar Elemental (provacoes)"),
    ("swamp_hydra", "Hidra do Pantano"),
    ("wyvern_king", "Wyvern Rei dos Picos"),
    ("desert_basilisk", "Basilisco do Deserto"),
    ("ruin_colossus", "Colosso das Ruinas"),
    ("false_hydra", "Falsa Hidra"),
    ("manticore", "Manticora"),
    ("king_slime", "Rei Slime"),
    ("goblin_king", "Rei Goblin"),
    ("orc_lord", "Lorde Orc"),
    ("minotaur", "Minotauro"),
    ("depths_worm_king", "Verme-Rei das Profundezas"),
    ("star_devourer", "Devorador de Estrelas"),
    ("sand_empress", "Imperatriz das Areias"),
    ("thorn_queen", "Rainha dos Espinhos"),
    ("demon_lord", "Lorde Demonio (Cidadela do Nether)"),
    ("tower_kobold_lord", "Lorde Kobold do Pinaculo"),
]
BOSS_IDS = {tid for tid, _ in BOSS_FOLDERS}
# Temas sem entrada de chefe (ambiente / troca de fase): não corta a intro.
NO_CUT = {"las_noches", "flying_carpet", "ulquiorra_segunda_etapa", "boss_victory"}
# Ajuste manual do drop (segundos no arquivo ORIGINAL), conferido no gráfico de volume. Vale só se o nome do arquivo
# contiver o trecho indicado (trocou a música → volta ao automático).
DROPS = {
    "abyss_sovereign": ("INTO MADNESS", 30.0),
    "barragan": ("Whisper of the Apocalypse", 43.0),
    "hollow_king": ("Sealed Vessel", 42.8),
    "lich": ("Puss In Boots", 18.0),
    "primordial_dragon": ("Fairytail", 20.5),
    "seraph": ("Lacrimosa", 11.0),
    "twilight_herald": ("PURITY X ENMITY", 9.0),
    "ulquiorra": ("Murcielago", 42.0),
    "grimmjow": ("Power To Strive", 0.0),
    "beast": ("Passionate Duelist", 0.0),
    "generic_boss": ("Jonathan Joestar", 1.5),
    "mimic_king": ("Gourmet Race", 1.0),
}


def setup():
    os.makedirs(SRC, exist_ok=True)
    for tid, _ in THEMES + BOSS_FOLDERS:
        os.makedirs(os.path.join(SRC, tid), exist_ok=True)
    lines = "\r\n".join(f"  {tid:<26} {desc}" for tid, desc in THEMES)
    boss_lines = "\r\n".join(f"  {tid:<26} {desc}" for tid, desc in BOSS_FOLDERS)
    readme = (
        "MUSICAS DOS CHEFES\r\n==================\r\n\r\n"
        "1. Jogue UMA musica (mp3, m4a, wav, ogg...) dentro da pasta do chefe.\r\n"
        "   Nao precisa renomear o arquivo.\r\n"
        "2. Avise o Claude: \"coloquei as musicas\". Ele converte, poe no mod e faz a versao nova.\r\n\r\n"
        "Pasta vazia = o chefe usa a musica original do mod.\r\n"
        "A intro longa e cortada: o chefe faz a ENTRADA durante a intro e acorda no momento em que a musica explode.\r\n"
        "O final de cada musica vira a vinheta de vitoria daquele chefe.\r\n\r\n"
        "PASTAS\r\n" + lines + "\r\n\r\n"
        "PASTAS DE CADA CHEFE (opcional)\r\n"
        "Com musica: aquele chefe toca a dele. Vazia: ele toca o tema da familia (lista de cima).\r\n"
        + boss_lines + "\r\n"
    )
    with open(os.path.join(SRC, "LEIA-ME.txt"), "w", encoding="utf-8-sig", newline="") as f:
        f.write(readme)


def decode(path):
    r = subprocess.run(["ffmpeg", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                       capture_output=True)
    return np.frombuffer(r.stdout, dtype=np.float32)


def onset_curve(x):
    """Fluxo espectral (só aumentos), 1 valor a cada HOP amostras — picos = ataques/batidas."""
    n = 2048
    win = np.hanning(n).astype(np.float32)
    frames = np.lib.stride_tricks.sliding_window_view(x, n)[::HOP] * win
    mag = np.abs(np.fft.rfft(frames, axis=1))
    mag = np.log1p(mag[:, : n // 4])  # até ~5.5 kHz
    flux = np.maximum(0, np.diff(mag, axis=0)).sum(axis=1)
    return np.concatenate([[0], flux])


def loud_db(x, sec=0.25):
    h = int(SR * sec)
    return np.array([20 * np.log10(np.sqrt(np.mean(x[i:i + h] ** 2)) + 1e-9) for i in range(0, len(x) - h, h)])


def peak_near(flux, t, before, after):
    a = max(0, int((t - before) * SR / HOP))
    b = min(len(flux), int((t + after) * SR / HOP) + 1)
    if b <= a:
        return t
    return (a + int(np.argmax(flux[a:b]))) * HOP / SR


def auto_drop(x, flux):
    db = loud_db(x)
    sm = np.convolve(db, np.ones(8) / 8, "same")
    ref = np.percentile(sm, 75)
    for i in range(len(sm)):
        if sm[i] >= ref - 2 and np.all(sm[i:i + 24] >= ref - 5):
            return i * 0.25
    return 0.0


def outro(x, flux):
    """(início, fim) em segundos da vinheta: última frase forte da música + a cauda."""
    db = loud_db(x)
    ref = np.percentile(db, 75)
    loud = np.where(db > ref - 20)[0]
    end_hit = (loud[-1] + 1) * 0.25 if len(loud) else len(x) / SR
    dur = len(x) / SR
    start = peak_near(flux, end_hit - 6.5, 2.5, 2.0)
    stop = min(dur, end_hit + 3.0)
    if stop - start < 4:
        start = max(0, stop - 7)
    return start, stop


def ffmpeg_ogg(src, dest, start=0.0, length=None, fade_in=0.0, fade_out=0.0, q=None):
    q = QUALITY if q is None else q
    af = []
    if fade_in > 0:
        af.append(f"afade=t=in:st=0:d={fade_in}")
    if fade_out > 0 and length:
        af.append(f"afade=t=out:st={max(0, length - fade_out):.2f}:d={fade_out}")
    af.append("loudnorm=I=-16:TP=-1.5")
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
    if start > 0:
        cmd += ["-ss", f"{start:.3f}"]
    cmd += ["-i", src]
    if length:
        cmd += ["-t", f"{length:.3f}"]
    cmd += ["-vn", "-map_metadata", "-1", "-ac", "2", "-ar", "44100", "-af", ",".join(af), "-c:a", "libvorbis",
            "-q:a", str(q), dest]
    return subprocess.run(cmd).returncode == 0


def source_of(tid, quiet=False):
    """Arquivo de áudio que o Renan colocou na pasta do tema (ou None). Arquivos de 0 byte são ignorados."""
    folder = os.path.join(SRC, tid)
    files = sorted(os.path.join(folder, n) for n in os.listdir(folder)) if os.path.isdir(folder) else []
    files = [p for p in files if os.path.isfile(p) and os.path.splitext(p)[1].lower() in AUDIO]
    for p in [p for p in files if os.path.getsize(p) == 0]:
        if not quiet:
            print(f"[VAZIO] {tid}: {os.path.basename(p)} tem 0 bytes (download falhou?) — ignorado")
    files = [p for p in files if os.path.getsize(p) > 0]
    if len(files) > 1 and not quiet:
        print(f"[aviso] {tid}: {len(files)} arquivos, usando {os.path.basename(files[0])}")
    return files[0] if files else None


def intro_cut(tid, name, x, flux):
    """(corte em s, ticks até o drop ou None, 'ajuste'/'auto'/'') — onde a faixa começa e quanto falta para o drop."""
    if tid in NO_CUT:
        return 0.0, None, ""
    hint = DROPS.get(tid)
    if hint and hint[0].lower() in name.lower():
        drop, how = hint[1], "ajuste"
    else:
        drop, how = auto_drop(x, flux), "auto"
    if drop > 0.5:
        drop = peak_near(flux, drop, 1.2, 1.2)
    cut = 0.0
    if drop > KEEP + 0.5:
        cut = peak_near(flux, drop - KEEP, 0.8, 0.8)
    intro = max(0.0, drop - cut)
    return cut, int(round(intro * 20)), how


def duration(path):
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", path],
                       capture_output=True, text=True)
    try:
        return float(r.stdout.strip())
    except ValueError:
        return -1.0


def swap_if_smaller(tmp, dest, label, dry):
    """Troca dest pelo tmp só se a duração bate (os cues do chefe dependem dela) e o arquivo ficou menor."""
    if not os.path.exists(tmp):
        print(f"[ERRO] {label}: ffmpeg não gerou o arquivo")
        return 0
    old, new = os.path.getsize(dest), os.path.getsize(tmp)
    d_old, d_new = duration(dest), duration(tmp)
    saved = 0
    if abs(d_old - d_new) > 0.12:
        print(f"[pulado] {label}: duração mudou ({d_old:.2f}s → {d_new:.2f}s) — a fonte não é a mesma do import")
    elif new >= old * 0.97:
        print(f"[igual] {label}: já está nesse tamanho ({old // 1024} KB)")
    else:
        saved = old - new
        print(f"[{'iria' if dry else 'ok'}] {label}: {old // 1024} KB → {new // 1024} KB (-{100 * saved // old}%)")
        if not dry:
            shutil.copyfile(tmp, dest)
    os.remove(tmp)
    return saved


def recompress(q, dry):
    """Recodifica as faixas já importadas a partir da FONTE (não do .ogg, para não somar perdas), com os mesmos cortes."""
    total_old = sum(os.path.getsize(os.path.join(dp, f)) for dp, _, fs in os.walk(DST) for f in fs if f.endswith(".ogg"))
    saved = 0
    for tid, _ in THEMES:
        dest = os.path.join(DST, tid + ".ogg")
        vic = os.path.join(VIC, tid + ".ogg")
        src = source_of(tid, quiet=True)
        if not src or not os.path.exists(dest):
            if os.path.exists(dest):
                print(f"[mantido] {tid}: sem fonte em MUSICAS_DOS_CHEFES (faixa original do mod)")
            continue
        x = decode(src)
        if len(x) < SR * 3:
            print(f"[ERRO] {tid}: não consegui ler {os.path.basename(src)}")
            continue
        flux = onset_curve(x)
        dur = len(x) / SR
        tmp = dest + ".tmp.ogg"
        if tid == "boss_victory":
            ffmpeg_ogg(src, tmp, length=min(dur, 14.0), fade_out=1.5 if dur > 14 else 0, q=q)
            saved += swap_if_smaller(tmp, dest, tid, dry)
            continue
        cut, _, _ = intro_cut(tid, os.path.basename(src), x, flux)
        ffmpeg_ogg(src, tmp, start=cut, fade_in=0.8 if cut > 0 else 0.0, q=q)
        saved += swap_if_smaller(tmp, dest, tid, dry)
        if tid != "las_noches" and os.path.exists(vic):
            a, b = outro(x, flux)
            tmp = vic + ".tmp.ogg"
            ffmpeg_ogg(src, tmp, start=a, length=b - a, fade_in=0.15, fade_out=min(2.0, (b - a) / 3), q=q)
            saved += swap_if_smaller(tmp, vic, "victory/" + tid, dry)
    print(f"músicas: {total_old / 1048576:.1f} MB → {(total_old - saved) / 1048576:.1f} MB "
          f"({'simulação' if dry else 'gravado'}, q={q})")


def write_cues(cues, boss_tracks=()):
    body = ",\n".join(f'            Map.entry("{k}", {v})' for k, v in sorted(cues.items()))
    java = f"""package com.renanpontes.isekaimahou.feature.boss_music;

import java.util.Map;

/**
 * GERADO por {{@code tools/music_import.py}} — não editar à mão. Para cada tema: ticks do início da faixa até o "drop"
 * (onde a música explode). A entrada do chefe ({{@link BossEntrance}}) dura isso e ele acorda no drop.
 */
public final class BossMusicCues {{
    private static final Map<String, Integer> INTRO = Map.ofEntries(
{body if body else '            Map.entry("generic_boss", 0)'});

    /** Chefes com faixa PRÓPRIA importada (pasta do chefe com música). */
    private static final java.util.Set<String> BOSS_TRACKS = java.util.Set.of({", ".join(f'"{b}"' for b in sorted(boss_tracks))});

    public static boolean hasBossTrack(String boss) {{
        return boss != null && BOSS_TRACKS.contains(boss);
    }}

    /** Ticks até o drop (0 = a música já começa forte). */
    public static int introTicks(String theme) {{
        return theme == null ? 0 : INTRO.getOrDefault(theme, 0);
    }}

    private BossMusicCues() {{}}
}}
"""
    with open(CUES_JAVA, "w", encoding="utf-8", newline="\n") as f:
        f.write(java)


def update_sounds_json(victories, boss_tracks=()):
    path = os.path.join(RES, "sounds.json")
    with open(path, encoding="utf-8") as f:
        data = json.load(f)
    for tid in BOSS_IDS:
        data.pop("boss_music." + tid, None)
    for tid in sorted(boss_tracks):
        data["boss_music." + tid] = {"sounds": [{"name": f"isekai_mahou:music/{tid}", "stream": True}]}
    for k in [k for k in data if k.startswith("boss_music.victory_")]:
        del data[k]
    for tid in sorted(victories):
        data["boss_music.victory_" + tid] = {"sounds": [{"name": f"isekai_mahou:music/victory/{tid}", "stream": True}]}
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def run():
    os.makedirs(BAK, exist_ok=True)
    os.makedirs(VIC, exist_ok=True)
    cues, victories, changed, boss_tracks = {}, set(), 0, set()
    for tid, _ in THEMES + BOSS_FOLDERS:
        src = source_of(tid)
        dest = os.path.join(DST, tid + ".ogg")
        bak = os.path.join(BAK, tid + ".ogg")
        vic = os.path.join(VIC, tid + ".ogg")
        if not src and tid in BOSS_IDS:
            # chefe sem música própria: tira a faixa (se havia) — ele volta ao tema da família
            for f in (dest, vic):
                if os.path.exists(f):
                    os.remove(f)
                    changed += 1
            continue
        if not src:
            if os.path.exists(bak):
                shutil.copyfile(bak, dest)
                os.remove(bak)
                print(f"[original] {tid}")
                changed += 1
            if os.path.exists(vic):
                os.remove(vic)
            continue
        name = os.path.basename(src)
        if os.path.exists(dest) and not os.path.exists(bak) and tid not in BOSS_IDS:
            shutil.copyfile(dest, bak)
        x = decode(src)
        if len(x) < SR * 3:
            print(f"[ERRO] {tid}: não consegui ler {name}")
            continue
        flux = onset_curve(x)
        dur = len(x) / SR

        if tid == "boss_victory":
            ok = ffmpeg_ogg(src, dest, length=min(dur, 14.0), fade_out=1.5 if dur > 14 else 0)
            print(f"[ok] boss_victory <- {name}" if ok else f"[ERRO] boss_victory: {name}")
            changed += ok
            continue

        cut, cue, how = intro_cut(tid, name, x, flux)
        if cue is not None:
            cues[tid] = cue
        ok = ffmpeg_ogg(src, dest, start=cut, fade_in=0.8 if cut > 0 else 0.0)
        if not ok:
            print(f"[ERRO] {tid}: falhou ao converter {name}")
            if tid in BOSS_IDS and os.path.exists(dest):
                os.remove(dest)
            if os.path.exists(bak):
                shutil.copyfile(bak, dest)
            cues.pop(tid, None)
            continue
        msg = f"[ok] {tid} <- {name[:48]} ({os.path.getsize(dest) // 1024} KB"
        if tid in cues:
            msg += f", corta {cut:.1f}s, drop em {cues[tid] / 20:.1f}s ({how})"
        print(msg + ")")
        changed += 1
        if tid in BOSS_IDS:
            boss_tracks.add(tid)

        if tid not in ("las_noches", "flying_carpet"):
            a, b = outro(x, flux)
            if ffmpeg_ogg(src, vic, start=a, length=b - a, fade_in=0.15, fade_out=min(2.0, (b - a) / 3)):
                victories.add(tid)
                print(f"     vitória: {a:.1f}s → {b:.1f}s ({b - a:.1f}s)")
    write_cues(cues, boss_tracks)
    update_sounds_json(victories, boss_tracks)
    print(f"{changed} faixa(s) alterada(s); {len(victories)} vinheta(s) de vitória; cues: {cues}; chefes com faixa própria: {sorted(boss_tracks)}")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    setup()
    if "--recompress" in sys.argv:
        q = float(sys.argv[sys.argv.index("--q") + 1]) if "--q" in sys.argv else QUALITY
        recompress(q, "--dry" in sys.argv)
    elif "--setup" not in sys.argv:
        run()
