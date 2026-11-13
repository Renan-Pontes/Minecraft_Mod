package com.renanpontes.isekaimahou.client.spellfx;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.client.ux.MeditationPose;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.glyph.fx.FxOptions;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * release 1.0 — AURA AO CANALIZAR, discreta e só no cliente: fios de luz subindo devagar em volta de quem
 * <ul>
 *   <li>segura uma conjuração LONGA (o círculo de conjuração já está vivo há {@link #LONG_HOLD} ticks) — na cor do
 *       círculo; quem avisa é o próprio círculo ({@link FlatFxParticle});</li>
 *   <li>está meditando ({@link MeditationPose#isMeditating}) — na cor do elemento em uso (os outros jogadores: lilás).</li>
 * </ul>
 * Magia rápida não ganha aura nenhuma. Desliga em {@link SpellFxConfig#CHANNEL_AURA} (tela de config do mod).
 * Em primeira pessoa a própria aura nasce só perto do chão e mais longe, para não tampar a visão.
 */
public final class ChannelAura {
    /** Ticks segurando a conjuração até a aura aparecer (1,25 s). */
    static final int LONG_HOLD = 25;
    private static final int OTHERS_MEDITATION_RGB = 0xB98CFF;

    private static int tick;

    /** Chamado pelo círculo de conjuração (aos pés) a cada tick enquanto ele segue {@code caster}. */
    static void castTick(ClientLevel level, Entity caster, int rgb, int tier, int age) {
        if (age < LONG_HOLD || (age & 1) != 0 || !SpellFxConfig.channelAura()) return;
        if (!visible(caster)) return;
        // aparece aos poucos: nos primeiros ticks metade das partículas
        if (age < LONG_HOLD + 20 && level.random.nextBoolean()) return;
        mote(level, caster, FxDraw.lighten(rgb, 0.2F), tier >= 3 ? 0.85F : 0.7F, 0.045);
        if (tier >= 4 && FxQuality.atLeast(Graphics.HIGH)) mote(level, caster, rgb, 0.7F, 0.06);
    }

    /** Todo tick do cliente: aura de quem medita (1 partícula a cada 4 ticks por jogador). */
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.isPaused()) return;
        if ((++tick & 3) != 0 || !SpellFxConfig.channelAura()) return;
        for (Player p : mc.level.players()) {
            if (p.isSpectator() || p.isInvisible() || !MeditationPose.isMeditating(p) || !visible(p)) continue;
            int rgb = OTHERS_MEDITATION_RGB;
            if (p == mc.player) {
                Element e = ClientMagic.data().activeElement();
                rgb = (e != null ? e : Element.ARCANE).color;
            }
            mote(mc.level, p, FxDraw.lighten(rgb, 0.3F), 0.6F, 0.03);
        }
    }

    private static boolean visible(Entity e) {
        Minecraft mc = Minecraft.getInstance();
        Entity cam = mc.getCameraEntity();
        return cam == null || FxQuality.inRange(cam.distanceToSqr(e));
    }

    /** Um fio de luz que nasce num anel em volta do corpo e sobe devagar. */
    private static void mote(ClientLevel level, Entity e, int rgb, float scale, double rise) {
        DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> type = SpellFxParticles.MOTE;
        if (!type.isBound()) return;
        Minecraft mc = Minecraft.getInstance();
        boolean firstPersonSelf = e == mc.getCameraEntity() && mc.options.getCameraType().isFirstPerson();
        RandomSource r = level.random;
        double a = r.nextDouble() * Math.PI * 2;
        double rad = firstPersonSelf ? 0.9 + r.nextDouble() * 0.4 : 0.45 + r.nextDouble() * 0.35;
        double h = firstPersonSelf ? 0.05 + r.nextDouble() * 0.3 : 0.1 + r.nextDouble() * e.getBbHeight() * 0.55;
        boolean was = SpellFxClient.LOCAL;
        // efeito montado no cliente: os providers não cortam de novo pelo preset gráfico
        SpellFxClient.LOCAL = true;
        try {
            level.addParticle(new FxOptions(type.get(), rgb, scale, 18 + r.nextInt(8), -1, 0),
                    e.getX() + Math.cos(a) * rad, e.getY() + h, e.getZ() + Math.sin(a) * rad, 0, rise, 0);
        } finally {
            SpellFxClient.LOCAL = was;
        }
    }

    private ChannelAura() {}
}
