package com.renanpontes.isekaimahou.feature.cinematic.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.jetbrains.annotations.Nullable;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * v1.1 — Coreografia de uma cutscene: 2–4 PLANOS de câmera com cortes secos ou chicotes rápidos, mais "batidas"
 * (clarão, tremor, quadro de impacto, som, explosão de partículas) em momentos fixos. Tempos em FRAÇÃO da duração
 * (a mesma coreografia serve para 40 ou 70 ticks).
 *
 * Tipos de rede que ganham coreografia (o resto continua com a órbita única de {@link CutsceneShot}):
 * <ul>
 *   <li>{@code transform:<estilo>} — default, holy, dark, time, speed, beast, spirit, fire, ice, storm, blood</li>
 *   <li>{@code ultimate:<elemento>[:<forma>]} — G de jogador (o "ultimate" puro dos chefes segue antigo)</li>
 *   <li>{@code domain}, {@code king_crimson}, {@code bites_the_dust}</li>
 * </ul>
 */
final class Choreo {

    /** Família visual (partículas no mundo + camada 2D). */
    enum Look {
        DEFAULT, HOLY, DARK, TIME, SPEED, FIRE, BLOOD, ICE, STORM, BEAST, SPIRIT,
        WATER, EARTH, WIND, NATURE, ARCANE, DOMAIN, KING_CRIMSON, BITES_THE_DUST
    }

    // Curvas
    static final int LINEAR = 0, SMOOTH = 1, OUT = 2, IN = 3, SNAP = 4;

    // Tipos de batida
    static final int SOUND = 0, FLASH = 1, SHAKE = 2, IMPACT = 3, PUNCH = 4, BURST = 5, BOLT = 6;

    /**
     * Um plano de câmera. Ângulo (graus) relativo ao rosto do foco: 0 = de frente, 180 = atrás, +90 = lado.
     * Distância e altura em blocos (× tamanho do foco, ≥ 1); com {@link #orbit} usam a órbita antiga
     * (ângulo-base de quem assiste, raio-base e altura do foco) — seguro para chefes enormes.
     */
    static final class Cam {
        float from, to;
        boolean orbit;
        float yaw0, yaw1;
        float dist0 = 3F, dist1 = 3F;
        float h0 = 0.4F, h1 = 0.4F;
        float look0 = 0.7F, look1 = 0.7F;
        float fov0 = 1F, fov1 = 1F;
        float roll0, roll1;
        float side0, side1;
        /** Mira à frente do foco (blocos, na direção do rosto) e acima (blocos): olhar "para onde a magia vai". */
        float aimFwd, aimUp;
        int ease = SMOOTH;
        /** Ticks de transição do plano anterior (0 = corte seco). */
        float blendIn;
        /** "Gagueira": a câmera anda em saltos de N ticks (quadros congelados). */
        int stutter;

        Cam(float from, float to) {
            this.from = from;
            this.to = to;
        }

        Cam span(float a, float b) { from = a; to = b; return this; }
        Cam yaw(float a, float b) { yaw0 = a; yaw1 = b; return this; }
        Cam dist(float a, float b) { dist0 = a; dist1 = b; return this; }
        Cam h(float a, float b) { h0 = a; h1 = b; return this; }
        Cam look(float a, float b) { look0 = a; look1 = b; return this; }
        Cam fov(float a, float b) { fov0 = a; fov1 = b; return this; }
        Cam roll(float a, float b) { roll0 = a; roll1 = b; return this; }
        Cam side(float a, float b) { side0 = a; side1 = b; return this; }
        Cam aim(float fwd, float up) { aimFwd = fwd; aimUp = up; return this; }
        Cam ease(int e) { ease = e; return this; }
        Cam blend(float ticks) { blendIn = ticks; return this; }
        Cam stutter(int ticks) { stutter = ticks; return this; }
        Cam orbit() { orbit = true; return this; }
    }

    record Beat(float at, int type, float a, float b, @Nullable SoundEvent sound) {}

    final Look look;
    final List<Cam> camList = new ArrayList<>(4);
    final List<Beat> beatList = new ArrayList<>(12);
    Cam[] cams;
    Beat[] beats;
    /** Ticks da entrada (0 = corte seco da câmera do jogo para o 1º plano) e da saída (volta à câmera do jogo). */
    float entryBlend, exitBlend = 7F;
    /** Momento (fração) em que o título entra; {@link #slam} = título "bate" na tela (transformações). */
    float titleAt = 0.15F;
    boolean slam;
    /** Momento (fração) da explosão de poder (as camadas 2D mudam a partir daí). */
    float burstAt = 0.4F;
    /** Força das camadas 2D (ultimates usam menos que transformações). */
    float overlay = 1F;

    private Choreo(Look look) {
        this.look = look;
    }

    Cam cam(float from, float to) {
        Cam c = new Cam(from, to);
        camList.add(c);
        return c;
    }

    Choreo beat(float at, int type, float a, float b) {
        beatList.add(new Beat(at, type, a, b, null));
        return this;
    }

    Choreo sound(float at, SoundEvent s, float pitch, float volume) {
        beatList.add(new Beat(at, SOUND, pitch, volume, s));
        return this;
    }

    /** Pacote padrão do "golpe": clarão, tremor, soco de FOV, quadro de impacto e explosão de partículas. */
    Choreo hit(float at, float flash, float shake, int shakeTicks, boolean impact) {
        beat(at, FLASH, flash, 0.3F);
        beat(at, SHAKE, shake, shakeTicks);
        beat(at, PUNCH, 0.12F, 0F);
        beat(at, BURST, 1F, 0F);
        if (impact) beat(at, IMPACT, 2F, 0F);
        return this;
    }

    private Choreo done() {
        cams = camList.toArray(new Cam[0]);
        Arrays.sort(cams, Comparator.comparingDouble(c -> c.from));
        beatList.sort(Comparator.comparingDouble(Beat::at));
        beats = beatList.toArray(new Beat[0]);
        return this;
    }

    // =====================================================================================
    // Biblioteca
    // =====================================================================================

    /** Parte antes do primeiro ':' ("transform:holy" → "transform"). */
    static String base(String kind) {
        int i = kind.indexOf(':');
        return i < 0 ? kind : kind.substring(0, i);
    }

    /** Parte depois do primeiro ':' ("" se não houver). */
    static String style(String kind) {
        int i = kind.indexOf(':');
        return i < 0 ? "" : kind.substring(i + 1);
    }

    /** Coreografia do tipo de rede, ou null (órbita antiga). */
    @Nullable
    static Choreo of(String kind) {
        String base = base(kind);
        String rest = style(kind).toLowerCase(Locale.ROOT);
        return switch (base) {
            case "transform" -> transform(rest.isEmpty() ? "default" : rest);
            case "ultimate" -> rest.isEmpty() ? null : ultimate(rest);   // "ultimate" puro (chefes) = órbita antiga
            case "domain" -> domain();
            case "king_crimson" -> kingCrimson();
            case "bites_the_dust" -> bitesTheDust();
            default -> null;
        };
    }

    // ------------------------------------------------------------------ transformações

    private static Choreo transform(String style) {
        Look look = switch (style) {
            case "holy" -> Look.HOLY;
            case "dark" -> Look.DARK;
            case "time" -> Look.TIME;
            case "speed" -> Look.SPEED;
            case "beast" -> Look.BEAST;
            case "spirit" -> Look.SPIRIT;
            case "fire" -> Look.FIRE;
            case "ice" -> Look.ICE;
            case "storm" -> Look.STORM;
            case "blood" -> Look.BLOOD;
            default -> Look.DEFAULT;
        };
        Choreo c = new Choreo(look);
        c.slam = true;
        c.entryBlend = 0F;     // corte seco para o close
        c.exitBlend = 6F;
        float burst = switch (look) {
            case DARK -> 0.45F;
            case SPEED -> 0.28F;
            case BEAST, TIME -> 0.32F;
            default -> 0.38F;
        };
        c.burstAt = burst;
        c.titleAt = burst;

        // (a) Close extremo no rosto/olhos, lente fechada, aproximando devagar
        Cam a = c.cam(0F, burst).yaw(8F, -6F).dist(1.45F, 1.15F).h(0.06F, 0.02F).look(0.93F, 0.93F)
                .fov(0.5F, 0.4F).roll(-2F, -4F).ease(SMOOTH);
        // (b) Corte para o plano heroico de baixo para cima enquanto o poder explode
        Cam b = c.cam(burst, 0.8F).yaw(28F, 48F).dist(4.0F, 4.6F).h(-1.05F, -0.85F).look(0.62F, 0.66F)
                .fov(1.18F, 1.0F).roll(7F, 3F).ease(OUT);
        // (c) Chicote para trás do personagem, abrindo, e volta à câmera do jogo
        Cam d = c.cam(0.8F, 1F).yaw(150F, 175F).dist(3.2F, 5.2F).h(0.9F, 1.6F).look(0.7F, 0.7F)
                .fov(1.0F, 1.06F).blend(4F).ease(SMOOTH);

        switch (look) {
            case HOLY -> {
                a.h(0.35F, 0.3F).fov(0.5F, 0.42F).roll(0F, 0F);
                b.yaw(15F, 30F).dist(4.5F, 5F).h(-1.3F, -1.2F).look(0.85F, 0.95F).fov(1.25F, 1.05F).roll(0F, 0F);
                d.yaw(170F, 185F).dist(3F, 4.5F).h(2.5F, 4.0F).look(0.5F, 0.5F);
                c.sound(0F, SoundEvents.BEACON_ACTIVATE, 1.4F, 0.8F);
                c.sound(0F, SoundEvents.AMETHYST_BLOCK_RESONATE, 0.7F, 0.9F);
                c.sound(burst, SoundEvents.TOTEM_USE, 1.25F, 0.45F);
                c.sound(burst, SoundEvents.BELL_RESONATE, 1.2F, 0.8F);
                c.hit(burst, 1.0F, 1.0F, 12, true);
            }
            case DARK -> {
                a.dist(1.35F, 1.0F).fov(0.45F, 0.36F).roll(0F, -2F);
                b.h(-1.1F, -0.95F).roll(-6F, -2F);
                c.sound(0F, SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.0F);
                c.sound(0.2F, SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.0F);
                c.sound(0.36F, SoundEvents.WARDEN_HEARTBEAT, 0.7F, 1.0F);
                c.sound(burst, SoundEvents.WARDEN_SONIC_BOOM, 0.7F, 0.45F);
                c.sound(burst, SoundEvents.WITHER_SHOOT, 0.5F, 0.5F);
                c.hit(burst, 0.55F, 1.6F, 16, true);
            }
            case TIME -> {
                // Plano (b) "rebobinando": a câmera recua pelo caminho e anda em saltos
                b.yaw(60F, 20F).dist(5.0F, 4.0F).h(-0.6F, -0.8F).fov(1.12F, 1.0F).roll(-4F, 0F).stutter(2).ease(LINEAR);
                d.yaw(200F, 180F);
                c.sound(0F, SoundEvents.BEACON_AMBIENT, 1.6F, 1.0F);
                c.sound(0.1F, SoundEvents.NOTE_BLOCK_HAT.value(), 0.7F, 0.6F);
                c.sound(0.2F, SoundEvents.NOTE_BLOCK_HAT.value(), 0.7F, 0.6F);
                c.sound(burst, SoundEvents.BELL_BLOCK, 0.5F, 0.9F);
                c.sound(burst, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 0.8F);
                c.hit(burst, 0.6F, 0.8F, 10, true);
            }
            case SPEED -> {
                a.fov(0.55F, 0.5F).dist(1.35F, 1.25F);
                // (b) de longe, com zoom-relâmpago
                b.span(burst, 0.62F).yaw(35F, 40F).dist(7F, 6.5F).h(-0.4F, -0.4F).look(0.6F, 0.6F).fov(1.0F, 0.55F).roll(0F, 4F).ease(SNAP);
                d.span(0.62F, 1F).yaw(120F, 200F).dist(3F, 4.5F).h(0.5F, 1.2F).look(0.7F, 0.7F).fov(1.18F, 1.0F)
                        .blend(2F).ease(SMOOTH);
                c.sound(0F, SoundEvents.BREEZE_CHARGE, 1.4F, 0.8F);
                c.sound(burst, SoundEvents.TRIDENT_RIPTIDE_3.value(), 1.3F, 0.8F);
                c.sound(burst, SoundEvents.FIREWORK_ROCKET_BLAST, 0.7F, 0.6F);
                c.sound(0.62F, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.8F);
                c.hit(burst, 0.7F, 0.8F, 8, true);
                c.beat(0.62F, PUNCH, 0.1F, 0F).beat(0.62F, SHAKE, 0.6F, 6F);
            }
            case FIRE -> {
                b.roll(9F, 4F);
                c.sound(0F, SoundEvents.FIRECHARGE_USE, 0.6F, 0.8F);
                c.sound(0.15F, SoundEvents.BLAZE_AMBIENT, 0.6F, 0.6F);
                c.sound(burst, SoundEvents.BLAZE_SHOOT, 0.5F, 0.9F);
                c.sound(burst, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.45F);
                c.hit(burst, 0.85F, 1.6F, 16, true);
            }
            case BLOOD -> {
                a.dist(1.25F, 1.0F);
                b.roll(-9F, -4F);
                c.sound(0F, SoundEvents.WARDEN_HEARTBEAT, 0.6F, 1.0F);
                c.sound(0.22F, SoundEvents.WARDEN_HEARTBEAT, 0.6F, 1.0F);
                c.sound(burst, SoundEvents.WITHER_AMBIENT, 0.6F, 0.6F);
                c.sound(burst, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 0.4F);
                c.hit(burst, 0.75F, 1.6F, 16, true);
            }
            case ICE -> {
                b.yaw(20F, 55F).ease(SMOOTH);
                c.sound(0F, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.8F);
                c.sound(0.15F, SoundEvents.POWDER_SNOW_BREAK, 0.6F, 0.8F);
                c.sound(burst, SoundEvents.GLASS_BREAK, 0.6F, 0.9F);
                c.sound(burst, SoundEvents.PLAYER_HURT_FREEZE, 0.6F, 0.8F);
                c.hit(burst, 0.8F, 1.0F, 12, true);
            }
            case STORM -> {
                b.h(-0.9F, -0.7F).roll(-5F, 0F);
                c.sound(0F, SoundEvents.TRIDENT_THUNDER.value(), 1.2F, 0.35F);
                c.beat(0.15F, BOLT, 0.6F, 0F).beat(0.15F, FLASH, 0.45F, 0.6F);
                c.sound(burst, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 0.9F);
                c.sound(burst, SoundEvents.LIGHTNING_BOLT_THUNDER, 1.2F, 0.35F);
                c.beat(burst, BOLT, 1F, 0F);
                c.hit(burst, 1.0F, 1.4F, 14, true);
                c.beat(0.66F, BOLT, 0.7F, 0F).beat(0.66F, FLASH, 0.4F, 0.6F);
                c.sound(0.66F, SoundEvents.LIGHTNING_BOLT_IMPACT, 1.1F, 0.6F);
            }
            case BEAST -> {
                a.roll(5F, 7F).fov(0.48F, 0.4F);
                b.dist(3.0F, 3.6F).h(-1.1F, -0.95F).roll(12F, 6F).fov(1.25F, 1.05F);
                c.sound(0F, SoundEvents.WOLF_HOWL, 0.5F, 0.7F);
                c.sound(burst, SoundEvents.WARDEN_ROAR, 0.8F, 0.8F);
                c.sound(burst, SoundEvents.RAVAGER_ROAR, 0.6F, 0.8F);
                c.sound(burst, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 0.4F);
                c.hit(burst, 0.5F, 3.0F, 30, true);
            }
            case SPIRIT -> {
                b.dist(5F, 6F).h(-0.9F, -0.8F).look(0.8F, 0.9F).fov(1.2F, 1.05F).roll(0F, 0F);
                d.h(2F, 3.5F).look(0.55F, 0.55F);
                c.sound(0F, SoundEvents.SOUL_ESCAPE.value(), 0.6F, 1.0F);
                c.sound(0.18F, SoundEvents.SOUL_ESCAPE.value(), 0.8F, 1.0F);
                c.sound(burst, SoundEvents.BEACON_ACTIVATE, 0.8F, 0.9F);
                c.sound(burst, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.6F, 0.8F);
                c.hit(burst, 0.8F, 1.4F, 16, true);
            }
            default -> {
                c.sound(0F, SoundEvents.BEACON_POWER_SELECT, 0.7F, 0.8F);
                c.sound(0.12F, SoundEvents.ENCHANTMENT_TABLE_USE, 0.6F, 1.0F);
                c.sound(burst, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.7F, 0.8F);
                c.sound(burst, SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 0.4F);
                c.hit(burst, 0.85F, 1.4F, 14, true);
            }
        }
        return c.done();
    }

    // ------------------------------------------------------------------ ultimates (G)

    /** {@code rest} = "<elemento>[:<forma>]". */
    private static Choreo ultimate(String rest) {
        int i = rest.indexOf(':');
        String element = i < 0 ? rest : rest.substring(0, i);
        String form = i < 0 ? "" : rest.substring(i + 1);
        Look look = switch (element) {
            case "fire", "magma", "sun" -> Look.FIRE;
            case "hellfire" -> Look.BLOOD;
            case "water", "abyss", "purity", "steam" -> Look.WATER;
            case "earth", "sand", "crystal", "gravity" -> Look.EARTH;
            case "wind", "astral" -> Look.WIND;
            case "lightning" -> Look.STORM;
            case "light" -> Look.HOLY;
            case "shadow", "void", "twilight" -> Look.DARK;
            case "ice" -> Look.ICE;
            case "nature" -> Look.NATURE;
            default -> Look.ARCANE;
        };
        Choreo c = new Choreo(look);
        c.slam = true;
        c.overlay = 0.65F;
        c.entryBlend = 2F;
        c.exitBlend = 7F;
        float burst = 0.3F;
        c.burstAt = burst;
        c.titleAt = 0.08F;

        // (a) close no rosto enquanto grita o nome
        c.cam(0F, burst).yaw(-12F, 4F).dist(1.6F, 1.25F).h(0.1F, 0.04F).look(0.92F, 0.92F).fov(0.55F, 0.45F)
                .roll(3F, 0F).ease(SMOOTH);
        // (b) corte: plano baixo, aberto, com a explosão do elemento
        Cam b = c.cam(burst, 0.72F).yaw(-35F, -60F).dist(4.2F, 5.0F).h(-1.0F, -0.8F).look(0.62F, 0.7F)
                .fov(1.2F, 1.0F).roll(-6F, -2F).ease(OUT);
        // (c) por cima do ombro, olhando para onde a magia vai
        Cam d = c.cam(0.72F, 1F).yaw(195F, 180F).dist(2.6F, 4.2F).h(1.0F, 1.8F).side(0.6F, 0.4F).look(0.8F, 0.8F)
                .aim(7F, 0F).fov(1.0F, 1.08F).blend(4F).ease(SMOOTH);

        if (form.contains("domain")) {
            // Domínio: o (c) vira a grua que revela o domo
            d.orbit();
            d.yaw(120F, 230F).dist(1.4F, 1.9F).h(0.6F, 1.7F).look(0.5F, 0.5F).aim(0F, 0F).side(0F, 0F).fov(1.05F, 0.95F);
        } else if (form.contains("meteor") || form.contains("cataclysm")) {
            // Chuva de meteoros / Cataclismo: olha o céu
            d.h(-0.4F, -0.2F).look(0.9F, 0.9F).aim(5F, 9F).fov(1.12F, 1.05F);
        }

        switch (look) {
            case FIRE, BLOOD -> {
                b.roll(-9F, -3F);
                c.sound(0F, SoundEvents.FIRECHARGE_USE, 0.7F, 0.7F);
                c.sound(burst, SoundEvents.BLAZE_SHOOT, 0.6F, 0.9F);
                c.sound(burst, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.4F);
            }
            case WATER -> {
                c.sound(0F, SoundEvents.AMBIENT_UNDERWATER_ENTER, 1.2F, 0.6F);
                c.sound(burst, SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 0.7F, 0.8F);
                c.sound(burst, SoundEvents.GENERIC_SPLASH, 0.6F, 0.8F);
            }
            case EARTH -> {
                b.h(-1.15F, -1.0F);
                c.sound(0F, SoundEvents.HEAVY_CORE_PLACE, 0.6F, 1.0F);
                c.sound(burst, SoundEvents.MACE_SMASH_GROUND_HEAVY, 0.7F, 1.0F);
                c.sound(burst, SoundEvents.ANVIL_LAND, 0.5F, 0.4F);
            }
            case WIND -> {
                b.yaw(-20F, -90F);
                c.sound(0F, SoundEvents.BREEZE_IDLE_AIR, 0.8F, 1.0F);
                c.sound(burst, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.7F, 1.0F);
                c.sound(burst, SoundEvents.ELYTRA_FLYING, 1.6F, 0.3F);
            }
            case STORM -> {
                c.sound(0F, SoundEvents.TRIDENT_THUNDER.value(), 1.3F, 0.35F);
                c.beat(burst, BOLT, 1F, 0F);
                c.sound(burst, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.9F, 0.9F);
            }
            case HOLY -> {
                b.h(-1.25F, -1.1F).look(0.85F, 0.9F);
                c.sound(0F, SoundEvents.BEACON_ACTIVATE, 1.3F, 0.7F);
                c.sound(burst, SoundEvents.BELL_RESONATE, 1.3F, 0.8F);
                c.sound(burst, SoundEvents.TOTEM_USE, 1.4F, 0.35F);
            }
            case DARK -> {
                c.sound(0F, SoundEvents.WARDEN_HEARTBEAT, 0.8F, 1.0F);
                c.sound(burst, SoundEvents.WARDEN_SONIC_BOOM, 0.8F, 0.4F);
                c.sound(burst, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.6F, 0.35F);
            }
            case ICE -> {
                c.sound(0F, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.5F, 0.8F);
                c.sound(burst, SoundEvents.GLASS_BREAK, 0.7F, 0.9F);
                c.sound(burst, SoundEvents.PLAYER_HURT_FREEZE, 0.7F, 0.7F);
            }
            case NATURE -> {
                c.sound(0F, SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.0F);
                c.sound(burst, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.9F, 0.7F);
                c.sound(burst, SoundEvents.ZOMBIE_VILLAGER_CURE, 1.6F, 0.25F);
            }
            default -> {
                c.sound(0F, SoundEvents.BEACON_ACTIVATE, 1.3F, 0.7F);
                c.sound(burst, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 0.7F, 0.6F);
                c.sound(burst, SoundEvents.EVOKER_CAST_SPELL, 0.7F, 0.8F);
            }
        }
        float shake = look == Look.EARTH ? 2.2F : 1.3F;
        c.hit(burst, 0.8F, shake, look == Look.EARTH ? 22 : 14, true);
        return c.done();
    }

    // ------------------------------------------------------------------ domínio (jogador e chefes)

    private static Choreo domain() {
        Choreo c = new Choreo(Look.DOMAIN);
        c.entryBlend = 6F;
        c.exitBlend = 9F;
        c.burstAt = 0.4F;
        c.titleAt = 0.4F;
        // (a) órbita baixa e fechada (as mãos fechando o selo)
        c.cam(0F, 0.4F).orbit().yaw(20F, 70F).dist(0.9F, 0.8F).h(-0.1F, 0.05F).look(0.75F, 0.75F)
                .fov(0.95F, 0.85F).roll(4F, 0F).ease(SMOOTH);
        // (b) corte: grua que sobe e revela o domo
        c.cam(0.4F, 1F).orbit().yaw(120F, 260F).dist(1.4F, 1.9F).h(0.6F, 1.7F).look(0.55F, 0.5F)
                .fov(1.08F, 0.95F).ease(SMOOTH);
        c.sound(0F, SoundEvents.END_PORTAL_SPAWN, 1.2F, 0.45F);
        c.sound(0F, SoundEvents.BEACON_ACTIVATE, 0.7F, 0.7F);
        c.sound(0.4F, SoundEvents.RESPAWN_ANCHOR_DEPLETE.value(), 0.6F, 0.8F);
        c.sound(0.4F, SoundEvents.BELL_RESONATE, 0.7F, 0.7F);
        c.beat(0.4F, FLASH, 0.9F, 0.4F).beat(0.4F, SHAKE, 1.6F, 20F).beat(0.4F, PUNCH, 0.1F, 0F);
        return c.done();
    }

    // ------------------------------------------------------------------ JoJo

    /** King Crimson: o tempo é APAGADO — quadro congelado que gagueja, e corte para trás de quem usou. */
    private static Choreo kingCrimson() {
        Choreo c = new Choreo(Look.KING_CRIMSON);
        c.entryBlend = 0F;
        c.exitBlend = 8F;
        c.burstAt = 0.5F;
        c.titleAt = 0.06F;
        c.slam = true;
        c.overlay = 1F;
        c.cam(0F, 0.5F).yaw(40F, 52F).dist(4.2F, 3.8F).h(0.4F, 0.35F).look(0.6F, 0.6F).fov(0.95F, 0.88F)
                .roll(-3F, -5F).stutter(4).ease(LINEAR);
        c.cam(0.5F, 1F).yaw(180F, 180F).dist(2.1F, 1.6F).h(0.45F, 0.4F).side(0.55F, 0.5F).look(0.85F, 0.85F)
                .aim(4F, 0F).fov(1.0F, 0.92F).ease(SMOOTH);
        c.sound(0F, SoundEvents.BEACON_DEACTIVATE, 0.5F, 0.9F);
        c.sound(0F, SoundEvents.ENDERMAN_TELEPORT, 0.5F, 0.7F);
        c.beat(0F, IMPACT, 2F, 1F).beat(0F, SHAKE, 0.6F, 6F);
        for (float t = 0.1F; t < 0.49F; t += 0.1F) {
            c.sound(t, SoundEvents.NOTE_BLOCK_HAT.value(), 0.5F, 0.7F);
            c.beat(t, SHAKE, 0.35F, 3F);
        }
        c.sound(0.5F, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 0.9F);
        c.sound(0.5F, SoundEvents.ENDERMAN_TELEPORT, 1.2F, 0.7F);
        c.beat(0.5F, FLASH, 0.4F, 1F).beat(0.5F, PUNCH, 0.08F, 0F);
        return c.done();
    }

    /** Bites the Dust: explosão, tom rosa, ponteiros correndo para trás e a câmera se afastando da âncora. */
    private static Choreo bitesTheDust() {
        Choreo c = new Choreo(Look.BITES_THE_DUST);
        c.entryBlend = 0F;
        c.exitBlend = 8F;
        c.burstAt = 0F;
        c.titleAt = 0.1F;
        c.slam = true;
        c.cam(0F, 0.22F).yaw(20F, 10F).dist(1.6F, 1.4F).h(0.2F, 0.2F).look(0.8F, 0.8F).fov(0.7F, 0.65F).ease(SMOOTH);
        c.cam(0.22F, 1F).yaw(10F, -50F).dist(2.0F, 9.0F).h(0.4F, 3.2F).look(0.6F, 0.5F).fov(0.85F, 1.1F)
                .blend(3F).ease(SMOOTH);
        c.sound(0F, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 0.5F);
        c.beat(0F, FLASH, 0.7F, 0.8F).beat(0F, SHAKE, 1.2F, 12F).beat(0F, IMPACT, 2F, 0F).beat(0F, BURST, 1F, 0F);
        c.sound(0.22F, SoundEvents.BELL_BLOCK, 0.5F, 0.9F);
        c.sound(0.22F, SoundEvents.BEACON_POWER_SELECT, 0.5F, 0.7F);
        for (float t = 0.3F; t < 0.9F; t += 0.08F) c.sound(t, SoundEvents.NOTE_BLOCK_HAT.value(), 1.5F, 0.45F);
        c.sound(0.9F, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.0F);
        return c.done();
    }

}
