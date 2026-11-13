package com.renanpontes.isekaimahou.feature.character.client;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.feature.character.Race;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * SÓ CLIENTE (release 1.0 — "transformações raciais mais bonitas"). O que é PRÓPRIO de cada uma das 11 raças, por cima do
 * espetáculo comum do {@link FormFx}:
 * <ul>
 *   <li><b>Entrada</b>: o <b>selo</b> da raça se desenha no chão durante a carga (pentagrama do Demônio, teia do
 *       Aracnídeo, lua do Elfo, pata do Bestial, sol do Anjo...), a partícula de assinatura ({@link RaceMoteParticle})
 *       converge/sobe do jeito da raça e toca a "voz" dela ({@link #entrySound}).</li>
 *   <li><b>Explosão</b>: o selo estoura para fora num clarão e a assinatura voa em leque.</li>
 *   <li><b>Aura</b>: enquanto a forma dura, o selo fica girando sob os pés (discreto), uma casca de luz na cor da raça
 *       envolve o corpo (só no gráfico ALTO; nunca na 1ª pessoa do próprio jogador) e a assinatura cai/sobe em volta.</li>
 * </ul>
 * Tudo pelo {@link FxQuality} (quantidade e alcance); nenhuma busca: só os jogadores do mundo do cliente.
 */
final class FormAura {
    /** Selo estourando depois da explosão. */
    private record Flash(Vec3 pos, Race race, int rgb, long start) {}

    private static final int FLASH_LIFE = 22, MAX_FLASHES = 8;
    private static final List<Flash> FLASHES = new CopyOnWriteArrayList<>();

    static void init() {
        NeoForge.EVENT_BUS.addListener(FormAura::onRenderLevel);
    }

    static void clear() {
        FLASHES.clear();
    }

    // =====================================================================================
    //  Som de entrada (a "voz" da raça, no começo da carga)
    // =====================================================================================
    static void entrySound(ClientLevel level, Entity e, Race race) {
        switch (race) {
            case HUMAN -> { play(level, e, SoundEvents.BELL_RESONATE, 0.9F, 1.3F); play(level, e, SoundEvents.TRIDENT_RETURN, 0.8F, 0.6F); }
            case ELF -> { play(level, e, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.7F); play(level, e, SoundEvents.ALLAY_AMBIENT_WITHOUT_ITEM, 0.7F, 0.8F); }
            case DWARF -> { play(level, e, SoundEvents.ANVIL_USE, 0.7F, 0.5F); play(level, e, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, 1.2F, 0.7F); }
            case HALF_ORC -> { play(level, e, SoundEvents.PIGLIN_BRUTE_ANGRY, 0.9F, 0.6F); play(level, e, SoundEvents.RAVAGER_STUNNED, 0.6F, 0.7F); }
            case DEMON -> { play(level, e, SoundEvents.GHAST_WARN, 0.7F, 0.5F); play(level, e, SoundEvents.BLAZE_AMBIENT, 0.9F, 0.6F); }
            case DRAGONBORN -> play(level, e, SoundEvents.ENDER_DRAGON_AMBIENT, 0.8F, 0.8F);
            case BEASTFOLK -> { play(level, e, SoundEvents.WOLF_GROWL, 1.0F, 0.6F); play(level, e, SoundEvents.POLAR_BEAR_WARNING, 0.7F, 0.9F); }
            case ARACHNID -> { play(level, e, SoundEvents.SPIDER_AMBIENT, 1.0F, 0.5F); play(level, e, SoundEvents.SCULK_CLICKING, 0.9F, 1.2F); }
            case PHANTOM -> { play(level, e, SoundEvents.PHANTOM_AMBIENT, 0.9F, 0.6F); play(level, e, SoundEvents.SOUL_ESCAPE.value(), 1.0F, 0.7F); }
            case ANGEL -> { play(level, e, SoundEvents.BELL_RESONATE, 0.8F, 1.8F); play(level, e, SoundEvents.ALLAY_ITEM_GIVEN, 0.8F, 0.7F); }
            case SLIME -> { play(level, e, SoundEvents.SLIME_SQUISH, 1.0F, 0.6F); play(level, e, SoundEvents.HONEY_BLOCK_SLIDE, 1.0F, 0.7F); }
        }
    }

    private static void play(ClientLevel level, Entity e, SoundEvent s, float volume, float pitch) {
        level.playLocalSound(e.getX(), e.getY(), e.getZ(), s, SoundSource.PLAYERS, volume, pitch, false);
    }

    // =====================================================================================
    //  Partículas de assinatura
    // =====================================================================================
    private static void mote(ClientLevel level, Race race, int rgb, double x, double y, double z, double vx, double vy, double vz) {
        RaceMoteParticle.spawn(level, race, RaceMoteParticle.tintFor(race, rgb), x, y, z, vx, vy, vz);
    }

    /** Carga ({@code p} = 0..1): a assinatura aparece do jeito da raça, cada vez mais. */
    static void charge(ClientLevel level, Entity e, Race race, int rgb, float p, long age) {
        if (age % 2 != 0) return;
        RandomSource rnd = level.random;
        double x = e.getX(), y = e.getY(), z = e.getZ(), h = e.getBbHeight();
        int n = FxQuality.count(1 + (int) (3 * p));
        for (int i = 0; i < n; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double r = 0.6 + rnd.nextDouble() * 2.0;
            double cx = Math.cos(a), sz = Math.sin(a);
            switch (race) {
                // Caem do alto em volta
                case ELF, ANGEL -> mote(level, race, rgb, x + cx * r, y + h + 1.5 + rnd.nextDouble() * 2, z + sz * r, 0, -0.03, 0);
                // Saltam do chão rachado
                case DWARF, HALF_ORC -> mote(level, race, rgb, x + cx * r, y + 0.1, z + sz * r, cx * 0.03, 0.22 + rnd.nextDouble() * 0.2, sz * 0.03);
                // Sobem do chão
                case DEMON, PHANTOM, SLIME, HUMAN -> mote(level, race, rgb, x + cx * r, y + 0.1 + rnd.nextDouble() * 0.4, z + sz * r, 0,
                        0.05 + rnd.nextDouble() * 0.06, 0);
                // Espiral subindo rente ao corpo
                case DRAGONBORN -> {
                    double s = age * 0.4 + i * 2.1;
                    mote(level, race, rgb, x + Math.cos(s) * 1.0, y + rnd.nextDouble() * h, z + Math.sin(s) * 1.0, 0, 0.07, 0);
                }
                // Riscos no ar em volta
                case BEASTFOLK -> mote(level, race, rgb, x + cx * (0.8 + rnd.nextDouble()), y + 0.4 + rnd.nextDouble() * h, z + sz * (0.8 + rnd.nextDouble()), 0, 0, 0);
                // Fios pairando, puxados para o corpo
                case ARACHNID -> mote(level, race, rgb, x + cx * (r + 1), y + 0.3 + rnd.nextDouble() * (h + 1), z + sz * (r + 1), -cx * 0.04, 0, -sz * 0.04);
            }
        }
    }

    /** Explosão: a assinatura voa em leque, uma vez. */
    static void burst(ClientLevel level, Entity e, Race race, int rgb) {
        RandomSource rnd = level.random;
        double x = e.getX(), y = e.getY(), z = e.getZ(), h = e.getBbHeight();
        while (FLASHES.size() >= MAX_FLASHES) FLASHES.remove(0);
        FLASHES.add(new Flash(e.position(), race, rgb, level.getGameTime()));
        int n = FxQuality.count(44);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + rnd.nextDouble() * 0.2;
            double s = 0.15 + rnd.nextDouble() * 0.35;
            double cx = Math.cos(a), sz = Math.sin(a);
            switch (race) {
                case ELF, ANGEL -> mote(level, race, rgb, x + cx * (1 + rnd.nextDouble() * 3), y + h + rnd.nextDouble() * 3, z + sz * (1 + rnd.nextDouble() * 3),
                        cx * 0.05, 0.02, sz * 0.05);
                case DWARF, HALF_ORC -> mote(level, race, rgb, x, y + h * 0.5, z, cx * s, 0.25 + rnd.nextDouble() * 0.35, sz * s);
                case BEASTFOLK -> mote(level, race, rgb, x + cx * (1 + rnd.nextDouble() * 2.5), y + 0.3 + rnd.nextDouble() * (h + 0.5), z + sz * (1 + rnd.nextDouble() * 2.5), 0, 0, 0);
                case ARACHNID -> mote(level, race, rgb, x + cx * 0.5, y + h * 0.6, z + sz * 0.5, cx * s * 0.7, (rnd.nextDouble() - 0.3) * 0.15, sz * s * 0.7);
                default -> mote(level, race, rgb, x + cx * 0.4, y + rnd.nextDouble() * h, z + sz * 0.4, cx * s * 0.6, 0.05 + rnd.nextDouble() * 0.2, sz * s * 0.6);
            }
        }
    }

    /** Aura constante (chamada a cada 2 ticks pelo {@link FormFx} para quem está transformado e no alcance). */
    static void ambient(ClientLevel level, Player p, Race race, int rgb, long now) {
        RandomSource rnd = level.random;
        if (rnd.nextFloat() > 0.55F * FxQuality.particles()) return;
        double x = p.getX(), y = p.getY(), z = p.getZ(), h = p.getBbHeight(), w = p.getBbWidth();
        double a = rnd.nextDouble() * Math.PI * 2;
        double r = w * (0.7 + rnd.nextDouble() * 0.9);
        double px = x + Math.cos(a) * r, pz = z + Math.sin(a) * r;
        switch (race) {
            case ELF, ANGEL -> mote(level, race, rgb, px, y + h + 0.3 + rnd.nextDouble() * 0.6, pz, 0, -0.02, 0);
            case DWARF -> mote(level, race, rgb, px, y + h * 0.5, pz, Math.cos(a) * 0.03, 0.12, Math.sin(a) * 0.03);
            case HALF_ORC -> mote(level, race, rgb, px, y + h * 0.7, pz, Math.cos(a) * 0.04, 0.08, Math.sin(a) * 0.04);
            case BEASTFOLK -> {
                // Riscos só quando corre/ataca (parado seria ruído)
                if (p.getDeltaMovement().horizontalDistanceSqr() > 0.01 || p.swinging) mote(level, race, rgb, px, y + h * (0.3 + rnd.nextDouble() * 0.6), pz, 0, 0, 0);
            }
            case ARACHNID -> mote(level, race, rgb, px, y + rnd.nextDouble() * h, pz, 0, -0.005, 0);
            case DRAGONBORN -> mote(level, race, rgb, px, y + rnd.nextDouble() * h * 0.6, pz, 0, 0.06, 0);
            default -> mote(level, race, rgb, px, y + rnd.nextDouble() * h * 0.5, pz, 0, 0.04 + rnd.nextDouble() * 0.03, 0);
        }
        // Rastro de luz bem fino no chão sob o selo (só no ALTO)
        if (FxQuality.atLeast(Graphics.HIGH) && now % 8 == 0 && p.onGround()) {
            level.addParticle(FormFx.glow(rgb, 0.6F), x + Math.cos(a) * 1.5, y + 0.05, z + Math.sin(a) * 1.5, 0, 0.01, 0);
            if (race == Race.PHANTOM) level.addParticle(ParticleTypes.WHITE_SMOKE, px, y + 0.05, pz, 0, 0.005, 0);
        }
    }

    // =====================================================================================
    //  Mundo: o selo da raça (carga, estouro e aura) e a casca de luz
    // =====================================================================================
    private static void onRenderLevel(RenderLevelStageEvent ev) {
        if (ev.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = mc.isPaused() ? 0F : ev.getPartialTick().getGameTimeDeltaPartialTick(false);
        long tick = mc.level.getGameTime();
        double now = tick + pt;
        Vec3 cam = ev.getCamera().getPosition();
        double range = FxQuality.range();
        boolean rich = FxQuality.atLeast(Graphics.HIGH);
        BufferBuilder buf = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        boolean any = false;

        for (Player p : mc.level.players()) {
            if (p.isSpectator()) continue;
            ClientCharacter.Look look = ClientCharacter.look(p.getId());
            if (look == null || look.race() == null) continue;
            float charge = FormFx.chargeProgress(p, pt);
            ClientCharacter.Fx form = charge >= 0F ? null : ClientCharacter.form(p.getId());
            if (charge < 0F && form == null) continue;
            Vec3 pos = p.getPosition(pt);
            if (pos.distanceToSqr(cam) > range * range) continue;
            Race race = look.race();
            int rgb = FormFx.color(p, race);
            float spin = (float) (now * 0.02);
            if (charge >= 0F) {
                // Entrada: o selo se desenha crescendo de dentro para fora
                float grow = Mth.clamp(charge * 1.4F, 0F, 1F);
                sigil(buf, cam, race, pos.x, pos.y + 0.045, pos.z, 2.15F * (0.35F + 0.65F * grow), spin * 2F, rgb, 0.25F + 0.65F * charge, now);
            } else {
                // Aura: selo discreto sob os pés (só no chão ou perto dele), abrindo e fechando com a forma
                float open = Mth.clamp((float) (now - form.start()) / 12F, 0F, 1F);
                float left = (float) (form.until() - now);
                if (left < 12F) open = Math.min(open, Math.max(0F, left / 12F));
                float s = p.getScale();
                float pulse = 0.5F + 0.5F * Mth.sin((float) now * 0.12F);
                if (p.onGround()) {
                    sigil(buf, cam, race, pos.x, pos.y + 0.045, pos.z, 1.35F * Math.max(1F, s * 0.9F), spin, rgb, open * (0.22F + 0.14F * pulse), now);
                }
                // Casca de luz em volta do corpo (nunca na 1ª pessoa do próprio jogador: taparia a visão)
                boolean self1st = p == mc.player && mc.options.getCameraType().isFirstPerson();
                if (rich && !self1st && !p.isInvisible()) {
                    float r = p.getBbWidth() * 0.95F, h = p.getBbHeight() * 1.15F;
                    FormFx.wall(buf, cam, pos.x, pos.y, pos.z, r, h, 14, rgb, open * (0.10F + 0.05F * pulse), 0F);
                    FormFx.wall(buf, cam, pos.x, pos.y, pos.z, r * 1.35F, h * 0.55F, 14, rgb, open * 0.06F, 0F);
                }
            }
            any = true;
        }

        for (Flash f : FLASHES) {
            double age = now - f.start();
            if (age < 0 || age > FLASH_LIFE) {
                if (age > FLASH_LIFE) FLASHES.remove(f);
                continue;
            }
            if (f.pos().distanceToSqr(cam) > range * range) continue;
            float t = (float) (age / FLASH_LIFE);
            float ease = 1F - (1F - t) * (1F - t);
            sigil(buf, cam, f.race(), f.pos().x, f.pos().y + 0.06, f.pos().z, 2.15F + 2.6F * ease, (float) (f.start() * 0.04), FormFx.mix(f.rgb(), 0xFFFFFF, 0.4F),
                    0.95F * (1F - t), now);
            any = true;
        }

        // Sempre fecha o buffer (sem vértices o build devolve null)
        MeshData mesh = buf.build();
        if (mesh != null && any) FormFx.GLOW.draw(mesh);
        else if (mesh != null) mesh.close();
    }

    /** O selo da raça deitado no chão: raio {@code r}, girado {@code rot}, alfa {@code a}. */
    private static void sigil(BufferBuilder b, Vec3 cam, Race race, double cx, double y, double cz, float r, float rot, int rgb, float a,
                              double now) {
        if (a <= 0.01F || r <= 0.05F) return;
        int hot = FormFx.mix(rgb, 0xFFFFFF, 0.35F);
        float w = 0.07F + r * 0.015F;
        switch (race) {
            case HUMAN -> {
                // Brasão heroico: estrela de 8 pontas (dois quadrados) e um anel interno
                star(b, cam, cx, y, cz, r, 4, 1, rot, w, hot, a);
                star(b, cam, cx, y, cz, r, 4, 1, rot + Mth.PI / 4F, w, rgb, a);
                FormFx.ring(b, cam, cx, y, cz, r * 0.45F, w, 32, 0F, false, hot, a * 0.8F);
            }
            case ELF -> {
                // Lua crescente (dois arcos) com três estrelas
                arc(b, cam, cx, y, cz, r * 0.9F, rot + 0.6F, rot + 0.6F + Mth.PI * 1.25F, w * 1.3F, 28, hot, a);
                arc(b, cam, cx + Mth.cos(rot + 0.6F + Mth.PI * 0.62F) * r * 0.28F, y, cz + Mth.sin(rot + 0.6F + Mth.PI * 0.62F) * r * 0.28F,
                        r * 0.68F, rot + 0.75F, rot + 0.45F + Mth.PI * 1.25F, w, 24, rgb, a * 0.9F);
                for (int i = 0; i < 3; i++) {
                    float t = rot - 0.5F - i * 0.55F;
                    star(b, cam, cx + Mth.cos(t) * r * 0.75F, y, cz + Mth.sin(t) * r * 0.75F, r * (0.13F - i * 0.02F), 4, 1, t, w * 0.7F, hot, a);
                }
            }
            case DWARF -> {
                // Runa da forja: hexágono com três barras cruzadas
                star(b, cam, cx, y, cz, r, 6, 1, rot, w * 1.3F, rgb, a);
                star(b, cam, cx, y, cz, r * 0.62F, 6, 1, rot + Mth.PI / 6F, w, hot, a * 0.85F);
                for (int i = 0; i < 3; i++) {
                    float t = rot + i * Mth.PI / 3F;
                    line(b, cam, cx + Mth.cos(t) * r * 0.62F, y, cz + Mth.sin(t) * r * 0.62F, cx - Mth.cos(t) * r * 0.62F, cz - Mth.sin(t) * r * 0.62F, w, hot, a * 0.7F);
                }
            }
            case HALF_ORC -> {
                // Marca de guerra: anel partido e três talhos atravessando
                FormFx.ring(b, cam, cx, y, cz, r, w * 1.6F, 24, rot, true, rgb, a);
                for (int i = -1; i <= 1; i++) {
                    float ox = Mth.cos(rot + Mth.HALF_PI) * i * r * 0.32F, oz = Mth.sin(rot + Mth.HALF_PI) * i * r * 0.32F;
                    float len = r * (i == 0 ? 0.95F : 0.7F);
                    line(b, cam, cx + ox + Mth.cos(rot + 0.25F) * len, y, cz + oz + Mth.sin(rot + 0.25F) * len,
                            cx + ox - Mth.cos(rot + 0.25F) * len, cz + oz - Mth.sin(rot + 0.25F) * len, w * 1.5F, hot, a);
                }
            }
            case DEMON -> {
                // Pentagrama dentro do círculo
                FormFx.ring(b, cam, cx, y, cz, r, w * 1.2F, 48, 0F, false, rgb, a);
                star(b, cam, cx, y, cz, r, 5, 2, rot, w, hot, a);
                FormFx.ring(b, cam, cx, y, cz, r * 0.38F, w * 0.8F, 24, 0F, false, rgb, a * 0.7F);
            }
            case DRAGONBORN -> {
                // Olho do dragão: losango, pupila em fenda e quatro presas em volta
                star(b, cam, cx, y, cz, r * 0.8F, 4, 1, rot, w * 1.3F, hot, a);
                line(b, cam, cx + Mth.cos(rot) * r * 0.55F, y, cz + Mth.sin(rot) * r * 0.55F, cx - Mth.cos(rot) * r * 0.55F, cz - Mth.sin(rot) * r * 0.55F, w * 1.8F, rgb, a);
                for (int i = 0; i < 4; i++) {
                    float t = rot + Mth.PI / 4F + i * Mth.HALF_PI;
                    star(b, cam, cx + Mth.cos(t) * r * 0.82F, y, cz + Mth.sin(t) * r * 0.82F, r * 0.2F, 3, 1, t, w, rgb, a * 0.9F);
                }
            }
            case BEASTFOLK -> {
                // Pata: uma almofada grande e quatro dedos
                FormFx.ring(b, cam, cx - Mth.cos(rot) * r * 0.2F, y, cz - Mth.sin(rot) * r * 0.2F, r * 0.42F, w * 1.6F, 24, 0F, false, hot, a);
                for (int i = 0; i < 4; i++) {
                    float t = rot + (i - 1.5F) * 0.55F;
                    FormFx.ring(b, cam, cx + Mth.cos(t) * r * 0.72F, y, cz + Mth.sin(t) * r * 0.72F, r * 0.17F, w * 1.3F, 14, 0F, false, rgb, a);
                }
                FormFx.ring(b, cam, cx, y, cz, r, w * 0.8F, 40, rot, true, rgb, a * 0.6F);
            }
            case ARACHNID -> {
                // Teia: oito raios e três voltas de octógono
                for (int i = 0; i < 8; i++) {
                    float t = rot + i * Mth.PI / 4F;
                    line(b, cam, cx, y, cz, cx + Mth.cos(t) * r, cz + Mth.sin(t) * r, w * 0.7F, hot, a);
                }
                for (int k = 1; k <= 3; k++) star(b, cam, cx, y, cz, r * k / 3F, 8, 1, rot, w * 0.7F, rgb, a * (0.5F + k * 0.15F));
            }
            case PHANTOM -> {
                // Anéis ondulando (nunca param quietos)
                blob(b, cam, cx, y, cz, r, 0.10F, 5, (float) now * 0.08F, w, hot, a);
                blob(b, cam, cx, y, cz, r * 0.66F, 0.14F, 4, -(float) now * 0.11F, w, rgb, a * 0.8F);
                blob(b, cam, cx, y, cz, r * 0.33F, 0.2F, 3, (float) now * 0.15F, w * 0.8F, hot, a * 0.6F);
            }
            case ANGEL -> {
                // Sol: auréola dupla, cruz e raios
                FormFx.ring(b, cam, cx, y, cz, r * 0.62F, w * 1.3F, 40, 0F, false, hot, a);
                FormFx.ring(b, cam, cx, y, cz, r * 0.5F, w * 0.7F, 32, 0F, false, rgb, a * 0.8F);
                for (int i = 0; i < 8; i++) {
                    float t = rot + i * Mth.PI / 4F;
                    float in = i % 2 == 0 ? 0F : 0.7F, out = i % 2 == 0 ? 1F : 0.9F;
                    line(b, cam, cx + Mth.cos(t) * r * in, y, cz + Mth.sin(t) * r * in, cx + Mth.cos(t) * r * out, cz + Mth.sin(t) * r * out,
                            i % 2 == 0 ? w * 1.2F : w * 0.8F, i % 2 == 0 ? hot : rgb, a);
                }
            }
            case SLIME -> {
                // Poça que respira, com bolhas
                blob(b, cam, cx, y, cz, r * 0.9F, 0.16F, 3, (float) now * 0.06F, w * 1.6F, rgb, a);
                for (int i = 0; i < 4; i++) {
                    float t = rot * 2F + i * 1.7F;
                    float d = r * (0.25F + 0.12F * i);
                    FormFx.ring(b, cam, cx + Mth.cos(t) * d, y, cz + Mth.sin(t) * d, r * (0.09F + 0.03F * (i % 2)), w, 12, 0F, false, hot, a * 0.9F);
                }
            }
        }
    }

    // ------------------------------------------------------------------ geometria (no chão)
    private static void line(BufferBuilder b, Vec3 cam, double x1, double y, double z1, double x2, double z2, float w, int rgb, float a) {
        FormFx.seg(b, cam, x1, y, z1, x2, y, z2, w, rgb, a);
    }

    /** Polígono estrelado: {@code n} pontas ligadas de {@code skip} em {@code skip} (skip 1 = polígono simples). */
    private static void star(BufferBuilder b, Vec3 cam, double cx, double y, double cz, float r, int n, int skip, float rot, float w,
                             int rgb, float a) {
        for (int i = 0; i < n; i++) {
            double a0 = rot + Math.PI * 2 * i / n, a1 = rot + Math.PI * 2 * (i + skip) / n;
            FormFx.seg(b, cam, cx + Math.cos(a0) * r, y, cz + Math.sin(a0) * r, cx + Math.cos(a1) * r, y, cz + Math.sin(a1) * r, w, rgb, a);
        }
    }

    private static void arc(BufferBuilder b, Vec3 cam, double cx, double y, double cz, float r, float from, float to, float w, int segs,
                            int rgb, float a) {
        for (int i = 0; i < segs; i++) {
            double a0 = from + (to - from) * i / segs, a1 = from + (to - from) * (i + 1) / segs;
            // Afina nas pontas (lua)
            float k = Mth.sin((i + 0.5F) / segs * Mth.PI);
            FormFx.seg(b, cam, cx + Math.cos(a0) * r, y, cz + Math.sin(a0) * r, cx + Math.cos(a1) * r, y, cz + Math.sin(a1) * r, w * (0.35F + k), rgb, a);
        }
    }

    /** Anel de raio ondulado: {@code r·(1 + amp·sin(k·θ + fase))}. */
    private static void blob(BufferBuilder b, Vec3 cam, double cx, double y, double cz, float r, float amp, int k, float phase, float w,
                             int rgb, float a) {
        int segs = 40;
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
            double r0 = r * (1 + amp * Math.sin(k * a0 + phase)), r1 = r * (1 + amp * Math.sin(k * a1 + phase));
            FormFx.seg(b, cam, cx + Math.cos(a0) * r0, y, cz + Math.sin(a0) * r0, cx + Math.cos(a1) * r1, y, cz + Math.sin(a1) * r1, w, rgb, a);
        }
    }

    private FormAura() {}
}
