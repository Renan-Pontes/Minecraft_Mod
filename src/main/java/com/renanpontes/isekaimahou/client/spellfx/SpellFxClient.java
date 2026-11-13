package com.renanpontes.isekaimahou.client.spellfx;

import com.mojang.blaze3d.systems.RenderSystem;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.glyph.fx.FxOptions;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ParticleType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;

/**
 * Cliente dos efeitos do sistema de glifos: providers das partículas {@code fx_*}, volta da mistura de cores ao
 * padrão depois das partículas aditivas e limpeza ao sair do mundo. O renderer do projétil de magia
 * ({@link SpellProjectileRenderer}) é registrado pelo núcleo ({@code IsekaiMahouClient}).
 */
public final class SpellFxClient {
    /**
     * true enquanto o próprio cliente monta um efeito composto ({@link FxBurstClient}): as quantidades já vieram
     * ajustadas por {@link FxQuality#count}, então os providers não cortam de novo.
     */
    static boolean LOCAL;
    /** Artes do círculo mágico por magia (formas e depois símbolos, na ordem do fx_circle_style.json). */
    @javax.annotation.Nullable
    private static SpriteSet styleSprites;

    public static void init(IEventBus modBus) {
        // opções de cliente dos efeitos (aura ao canalizar) em arquivo próprio; aparecem na tela de config do mod
        ModLoadingContext.get().getActiveContainer().registerConfig(ModConfig.Type.CLIENT, SpellFxConfig.CLIENT_SPEC,
                "isekai_mahou-spellfx-client.toml");
        NeoForge.EVENT_BUS.addListener(ChannelAura::onClientTick);
        modBus.addListener(SpellFxClient::providers);
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            // Partículas aditivas mudam a função de mistura: devolve o padrão para quem desenha depois
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) RenderSystem.defaultBlendFunc();
        });
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut e) -> {
            FlatFxParticle.clear();
            FxBurstClient.clear();
        });
        NeoForge.EVENT_BUS.addListener(FxBurstClient::onClientTick);
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.Clone e) -> FlatFxParticle.prune());
    }

    private static void providers(RegisterParticleProvidersEvent e) {
        loose(e, SpellFxParticles.GLOW, FxParticle.Kind.GLOW);
        loose(e, SpellFxParticles.EMBER, FxParticle.Kind.EMBER);
        loose(e, SpellFxParticles.FROST, FxParticle.Kind.FROST);
        loose(e, SpellFxParticles.DROPLET, FxParticle.Kind.DROPLET);
        loose(e, SpellFxParticles.LEAF, FxParticle.Kind.LEAF);
        loose(e, SpellFxParticles.STONE, FxParticle.Kind.STONE);
        loose(e, SpellFxParticles.MOTE, FxParticle.Kind.MOTE);
        loose(e, SpellFxParticles.WISP, FxParticle.Kind.WISP);
        loose(e, SpellFxParticles.RUNE, FxParticle.Kind.RUNE);
        loose(e, SpellFxParticles.SMOKE, FxParticle.Kind.SMOKE);
        e.registerSpriteSet(SpellFxParticles.FLASH.get(), s -> (o, lvl, x, y, z, vx, vy, vz) ->
                new FxParticle(lvl, x, y, z, 0, 0, 0, o, s, FxParticle.Kind.FLASH));
        e.registerSpriteSet(SpellFxParticles.SPARK.get(), s -> (o, lvl, x, y, z, vx, vy, vz) ->
                skip() ? null : new StreakParticle(lvl, x, y, z, vx, vy, vz, o, s, true));
        e.registerSpriteSet(SpellFxParticles.WIND.get(), s -> (o, lvl, x, y, z, vx, vy, vz) ->
                skip() ? null : new StreakParticle(lvl, x, y, z, vx, vy, vz, o, s, false));
        e.registerSpriteSet(SpellFxParticles.SHOCKWAVE.get(), s -> (o, lvl, x, y, z, vx, vy, vz) ->
                FlatFxParticle.create(o, lvl, x, y, z, vx, vy, vz, s, true));
        e.registerSpriteSet(SpellFxParticles.CIRCLE.get(), s -> (o, lvl, x, y, z, vx, vy, vz) ->
                FlatFxParticle.create(o, lvl, x, y, z, vx, vy, vz, s, false));
        e.registerSpriteSet(SpellFxParticles.PILLAR.get(), s -> (o, lvl, x, y, z, vx, vy, vz) ->
                PillarParticle.create(o, lvl, x, y, z, s));
        // Só carrega as artes no atlas (formas/símbolos do círculo por magia); nunca vira partícula
        e.registerSpriteSet(SpellFxParticles.CIRCLE_STYLE.get(), s -> {
            styleSprites = s;
            return (o, lvl, x, y, z, vx, vy, vz) -> null;
        });
    }

    /** Arte {@code index} do círculo por magia: 0..SHAPES-1 = formas, depois os símbolos. null se ainda não carregou. */
    @javax.annotation.Nullable
    static TextureAtlasSprite styleSprite(int index) {
        int last = com.renanpontes.isekaimahou.glyph.CircleStyle.SHAPES + com.renanpontes.isekaimahou.glyph.CircleStyle.SYMBOLS - 1;
        if (styleSprites == null || index < 0 || index > last) return null;
        try {
            return styleSprites.get(index, last);
        } catch (RuntimeException ex) {
            // pacote de recursos sem as artes: o círculo volta ao padrão
            return null;
        }
    }

    private static void loose(RegisterParticleProvidersEvent e, DeferredHolder<ParticleType<?>, ParticleType<FxOptions>> type,
                              FxParticle.Kind kind) {
        e.registerSpriteSet(type.get(), (SpriteSet s) -> new Provider(s, kind));
    }

    /**
     * Partículas soltas vindas do servidor ({@code sendParticles}) não conhecem o preset: aqui elas são cortadas na
     * proporção do {@link FxQuality} (LOW mantém 30%, MEDIUM 60%; HIGH/ULTRA tudo). Efeitos montados no cliente já
     * vêm contados e passam direto.
     */
    private static boolean skip() {
        if (LOCAL) return false;
        float keep = FxQuality.particles();
        return keep < 1F && Math.random() >= keep;
    }

    private record Provider(SpriteSet sprites, FxParticle.Kind kind) implements ParticleProvider<FxOptions> {
        @Override
        public Particle createParticle(FxOptions o, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            if (skip()) return null;
            return new FxParticle(level, x, y, z, vx, vy, vz, o, sprites, kind);
        }
    }

    private SpellFxClient() {}
}
