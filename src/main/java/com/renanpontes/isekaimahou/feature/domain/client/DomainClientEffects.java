package com.renanpontes.isekaimahou.feature.domain.client;

import java.util.List;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.domain.DomainNet;
import com.renanpontes.isekaimahou.feature.domain.entity.DomainDomeEntity;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * Efeitos de tela do Domínio (só cliente):
 * <ul>
 *   <li>dentro de um domo fechado: névoa e céu tingidos com a cor do domínio (o "mundo interior"),
 *       vinheta colorida e partículas ambientes do elemento;</li>
 *   <li>tremor de câmera (Cataclismo, choque de domínios), respeitando a opção "Efeitos de distorção".</li>
 * </ul>
 */
public final class DomainClientEffects {
    private static float fade;
    private static float fadePrev;
    private static int tint = Element.ARCANE.color;
    /** Cor da faixa de baixo da vinheta: a do 2º elemento num domínio multi-elemento, senão a mesma. */
    private static int tintAccent = Element.ARCANE.color;
    private static float tintRadius = 10;
    private static Element tintElement = Element.ARCANE;

    private static float shakeStrength;
    private static int shakeTicks;
    private static int shakeTotal = 1;

    static void onShake(DomainNet.Shake p) {
        float current = shakeTicks > 0 ? shakeStrength * shakeTicks / (float) shakeTotal : 0;
        if (p.strength() >= current) {
            shakeStrength = p.strength();
            shakeTotal = Math.max(1, p.ticks());
            shakeTicks = shakeTotal;
        }
    }

    // =====================================================================================
    //  Tick: em qual domo a câmera está
    // =====================================================================================
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            fade = fadePrev = 0;
            shakeTicks = 0;
            return;
        }
        if (mc.isPaused()) return;
        if (shakeTicks > 0) shakeTicks--;

        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        DomainDomeEntity inside = null;
        for (Entity ent : level.entitiesForRendering()) {
            if (ent instanceof DomainDomeEntity d && d.isEnclosing()) {
                float r = d.visualRadius(1.0F);
                if (cam.distanceToSqr(d.position()) < r * r && (inside == null || d.radius() < inside.radius())) inside = d;
            }
        }
        fadePrev = fade;
        float target = inside != null ? inside.presence(1.0F) : 0;
        fade += (target - fade) * 0.25F;
        if (Math.abs(target - fade) < 0.002F) fade = target;
        if (inside != null) {
            // Barreira invertida: o "mundo interior" fica em negativo
            int flip = inside.isInverted() ? 0xFFFFFF : 0;
            tint = inside.color() ^ flip;
            tintAccent = (inside.elements().size() > 1 ? inside.accentColor() : inside.color()) ^ flip;
            tintRadius = inside.radius();
            tintElement = inside.element();
            ambient(level, inside, cam);
        }
    }

    /** Partículas ambientes do elemento em volta da câmera (só dentro do domo). */
    private static void ambient(ClientLevel level, DomainDomeEntity d, Vec3 cam) {
        int n = FxQuality.count(4);
        RandomSource rnd = level.random;
        // Magias multi-elemento: o "mundo interior" mistura as partículas de todos os elementos da magia
        List<Element> els = d.elements();
        float r = d.radius();
        for (int i = 0; i < n; i++) {
            double x = cam.x + (rnd.nextDouble() - 0.5) * 18;
            double y = cam.y + (rnd.nextDouble() - 0.35) * 8;
            double z = cam.z + (rnd.nextDouble() - 0.5) * 18;
            if (d.position().distanceToSqr(x, y, z) > r * r) continue;
            Element el = els.get(rnd.nextInt(els.size()));
            Element base = el.isFusion() ? (rnd.nextBoolean() ? el.parentA : el.parentB) : el;
            switch (base) {
                case FIRE -> level.addParticle(rnd.nextInt(6) == 0 ? ParticleTypes.LAVA : ParticleTypes.FLAME, x, y, z, 0, 0.03, 0);
                case WATER -> level.addParticle(Vfx.water(), x, y + 4, z, 0, 0, 0);
                case EARTH -> level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.DEEPSLATE.defaultBlockState()),
                        x, y + 4, z, 0, 0, 0);
                case WIND -> level.addParticle(Vfx.cloud(), x, y, z, 0.15, 0.0, 0.05);
                case LIGHT -> level.addParticle(Vfx.endRod(), x, y, z, 0, 0.015, 0);
                case SHADOW -> level.addParticle(rnd.nextBoolean() ? Vfx.ash() : Vfx.smoke(), x, y, z, 0, -0.01, 0);
                default -> level.addParticle(Vfx.enchant(), x, y, z, 0, 0.05, 0);
            }
            ParticleOptions dust = ElementFx.dust(el, 0.9F);
            level.addParticle(dust, x + rnd.nextGaussian(), y + rnd.nextGaussian(), z + rnd.nextGaussian(), 0, 0.01, 0);
            // Lei Ruína: o céu do domínio desmorona — poeira de pedra e cinzas caindo sem parar
            if (d.has(DomainDomeEntity.F_RUIN)) {
                level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.COBBLED_DEEPSLATE.defaultBlockState()),
                        x, y + 5, z, 0, 0, 0);
                level.addParticle(Vfx.ash(), x + rnd.nextGaussian() * 2, y + 2, z + rnd.nextGaussian() * 2, 0, -0.02, 0);
            }
        }
    }

    private static float fade(double partialTick) {
        return Mth.lerp((float) partialTick, fadePrev, fade);
    }

    /** Luz/Sol/Pureza/Gelo/Cristal ficam claros por dentro; o resto, escuro (céu "noturno" do domínio). */
    private static float interiorBrightness(Element e) {
        return switch (e) {
            case LIGHT, SUN, PURITY, ICE, CRYSTAL, ASTRAL -> 0.75F;
            case WIND, STEAM, SAND -> 0.6F;
            default -> 0.3F;
        };
    }

    // =====================================================================================
    //  Névoa / céu
    // =====================================================================================
    static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float f = fade(event.getPartialTick());
        if (f <= 0.001F) return;
        float k = interiorBrightness(tintElement);
        float m = f * 0.85F;
        event.setRed(Mth.lerp(m, event.getRed(), ((tint >> 16) & 255) / 255F * k));
        event.setGreen(Mth.lerp(m, event.getGreen(), ((tint >> 8) & 255) / 255F * k));
        event.setBlue(Mth.lerp(m, event.getBlue(), (tint & 255) / 255F * k));
    }

    static void onRenderFog(ViewportEvent.RenderFog event) {
        float f = fade(event.getPartialTick());
        if (f <= 0.001F || event.getType() != FogType.NONE) return;
        if (event.getMode() == FogRenderer.FogMode.FOG_SKY) {
            // O céu inteiro vira a cor do domínio
            event.setNearPlaneDistance(Mth.lerp(f, event.getNearPlaneDistance(), 0.0F));
            event.setFarPlaneDistance(Mth.lerp(f, event.getFarPlaneDistance(), 2.0F));
        } else {
            // O mundo lá fora some na névoa: só o interior do domo fica nítido
            float far = Math.min(event.getFarPlaneDistance(), tintRadius * 2.2F + 18.0F);
            event.setFarPlaneDistance(Mth.lerp(f, event.getFarPlaneDistance(), far));
            event.setNearPlaneDistance(Mth.lerp(f, event.getNearPlaneDistance(), far * 0.25F));
        }
        event.setCanceled(true);
    }

    // =====================================================================================
    //  Tremor de câmera
    // =====================================================================================
    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (shakeTicks <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = (float) event.getPartialTick();
        float k = Math.max(0, (shakeTicks - pt) / shakeTotal);
        float s = shakeStrength * k * mc.options.screenEffectScale().get().floatValue() * MahouConfig.shakeScale();
        if (s <= 0.001F) return;
        double t = mc.level.getGameTime() + pt;
        event.setPitch(event.getPitch() + (float) (Math.sin(t * 2.3) * s));
        event.setYaw(event.getYaw() + (float) (Math.sin(t * 1.9 + 1.3) * s * 0.8));
        event.setRoll(event.getRoll() + (float) (Math.sin(t * 3.1 + 0.7) * s * 0.6));
    }

    // =====================================================================================
    //  Vinheta colorida (camada de HUD)
    // =====================================================================================
    static void renderVignette(GuiGraphics g, DeltaTracker delta) {
        Minecraft mc = Minecraft.getInstance();
        float f = fade(delta.getGameTimeDeltaPartialTick(false));
        if (f < 0.02F || mc.options.hideGui) return;
        int a = Mth.clamp((int) (f * 110), 0, 255);
        int rgb = tint & 0xFFFFFF;
        int low = tintAccent & 0xFFFFFF;
        int w = g.guiWidth(), h = g.guiHeight(), band = h / 4;
        g.fillGradient(0, 0, w, band, (a << 24) | rgb, rgb);
        g.fillGradient(0, h - band, w, h, low, (a << 24) | low);
    }

    private DomainClientEffects() {}
}
