package com.renanpontes.isekaimahou.client.perf;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.MahouConfig.Graphics;
import com.renanpontes.isekaimahou.core.MahouConfig.PcMode;

import net.minecraft.client.CloudStatus;
import net.minecraft.client.GraphicsStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.ParticleStatus;
import net.minecraft.network.chat.Component;

/**
 * SÓ CLIENTE. "Modo do PC": um clique ajusta o preset do mod ({@link MahouConfig#GRAPHICS}, partículas reduzidas) e as
 * opções de vídeo do Minecraft que mais pesam (distâncias, gráficos, partículas, sombras, nuvens, mistura de biomas,
 * distância das entidades, mipmaps) — e, se o Distant Horizons estiver instalado, o alcance do terreno distante.
 * <p>
 * Os valores foram escolhidos para FPS estável, não para o máximo: "Bom" é o padrão do Minecraft com um pouco mais de
 * alcance; "Fraco" deixa jogável em notebook com vídeo integrado.
 */
public enum PcPreset {
    //      modo         efeitos           reduz  render sim  gráficos               partículas                sombra nuvens              mistura entDist mip  DH(chunks)
    LOW(PcMode.LOW, Graphics.LOW, true, 6, 5, GraphicsStatus.FAST, ParticleStatus.MINIMAL, false, CloudStatus.OFF, 0, 0.75D, 0, 48),
    OK(PcMode.OK, Graphics.MEDIUM, true, 8, 6, GraphicsStatus.FAST, ParticleStatus.DECREASED, false, CloudStatus.FAST, 1, 1.0D, 2, 96),
    GOOD(PcMode.GOOD, Graphics.HIGH, false, 12, 8, GraphicsStatus.FANCY, ParticleStatus.ALL, true, CloudStatus.FANCY, 2, 1.0D, 4, 192),
    ULTRA(PcMode.ULTRA, Graphics.ULTRA, false, 16, 10, GraphicsStatus.FANCY, ParticleStatus.ALL, true, CloudStatus.FANCY, 3, 1.25D, 4, 320);

    public final PcMode mode;
    public final Graphics graphics;
    public final boolean reducedParticles;
    public final int renderDistance;
    public final int simulationDistance;
    public final GraphicsStatus graphicsMode;
    public final ParticleStatus particles;
    public final boolean entityShadows;
    public final CloudStatus clouds;
    public final int biomeBlend;
    public final double entityDistance;
    public final int mipmaps;
    /** Alcance do Distant Horizons (chunks de LOD), se o mod estiver instalado. */
    public final int dhChunks;

    PcPreset(PcMode mode, Graphics graphics, boolean reducedParticles, int renderDistance, int simulationDistance,
            GraphicsStatus graphicsMode, ParticleStatus particles, boolean entityShadows, CloudStatus clouds, int biomeBlend,
            double entityDistance, int mipmaps, int dhChunks) {
        this.mode = mode;
        this.graphics = graphics;
        this.reducedParticles = reducedParticles;
        this.renderDistance = renderDistance;
        this.simulationDistance = simulationDistance;
        this.graphicsMode = graphicsMode;
        this.particles = particles;
        this.entityShadows = entityShadows;
        this.clouds = clouds;
        this.biomeBlend = biomeBlend;
        this.entityDistance = entityDistance;
        this.mipmaps = mipmaps;
        this.dhChunks = dhChunks;
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    public Component title() {
        return Component.translatable("screen.isekai_mahou.perf.preset." + key());
    }

    public Component description() {
        return Component.translatable("screen.isekai_mahou.perf.preset." + key() + ".desc");
    }

    /** Cor de destaque do botão (vermelho → dourado → violeta). */
    public int accent() {
        return switch (this) {
            case LOW -> 0xFFE07A4A;
            case OK -> 0xFFE8C24A;
            case GOOD -> 0xFF6FD08A;
            case ULTRA -> 0xFFB98CFF;
        };
    }

    public static PcPreset of(PcMode mode) {
        for (PcPreset p : values()) if (p.mode == mode) return p;
        return null;
    }

    /** Uma linha de "o que vai mudar": nome da opção, valor atual e valor novo. */
    public record Change(String key, Component label, Component from, Component to) {
        /** Mudar isto recarrega texturas/chunks (leva alguns segundos). */
        public boolean reloads() {
            return key.equals("mipmaps") || key.equals("graphics");
        }
    }

    /** Diferenças entre as opções atuais e este preset (só o que muda de fato). */
    public List<Change> diff() {
        List<Change> out = new ArrayList<>();
        Options o = Minecraft.getInstance().options;
        Graphics curFx = MahouConfig.safe(MahouConfig.GRAPHICS);
        if (curFx != graphics) out.add(change("fx", fxName(curFx), fxName(graphics)));
        boolean curReduced = MahouConfig.safe(MahouConfig.REDUCED_PARTICLES);
        if (curReduced != reducedParticles) out.add(change("reduced", onOff(curReduced), onOff(reducedParticles)));
        int rd = o.renderDistance().get();
        if (rd != renderDistance) out.add(change("render", chunks(rd), chunks(renderDistance)));
        int sd = o.simulationDistance().get();
        if (sd != simulationDistance) out.add(change("simulation", chunks(sd), chunks(simulationDistance)));
        GraphicsStatus gm = o.graphicsMode().get();
        if (gm != graphicsMode) out.add(change("graphics", Component.translatable(gm.getKey()), Component.translatable(graphicsMode.getKey())));
        ParticleStatus ps = o.particles().get();
        if (ps != particles) out.add(change("particles", Component.translatable(ps.getKey()), Component.translatable(particles.getKey())));
        boolean sh = o.entityShadows().get();
        if (sh != entityShadows) out.add(change("shadows", onOff(sh), onOff(entityShadows)));
        CloudStatus cs = o.cloudStatus().get();
        if (cs != clouds) out.add(change("clouds", Component.translatable(cs.getKey()), Component.translatable(clouds.getKey())));
        int bb = o.biomeBlendRadius().get();
        if (bb != biomeBlend) out.add(change("biome_blend", blend(bb), blend(biomeBlend)));
        double ed = o.entityDistanceScaling().get();
        if (Math.abs(ed - entityDistance) > 0.001D) out.add(change("entity_distance", percent(ed), percent(entityDistance)));
        int mm = o.mipmapLevels().get();
        if (mm != mipmaps) out.add(change("mipmaps", Component.literal(String.valueOf(mm)), Component.literal(String.valueOf(mipmaps))));
        if (DhBridge.present()) {
            int cur = DhBridge.chunkRenderDistance();
            if (cur != dhChunks) {
                out.add(change("dh", cur < 0 ? Component.literal("?") : chunks(cur), chunks(dhChunks)));
            }
        }
        return out;
    }

    /**
     * Aplica tudo. Só toca nas opções que mudam (mipmaps e gráficos recarregam texturas/chunks: evitar à toa) e salva
     * o options.txt e a config do mod.
     */
    public void apply() {
        Minecraft mc = Minecraft.getInstance();
        Options o = mc.options;
        try {
            MahouConfig.GRAPHICS.set(graphics);
            MahouConfig.REDUCED_PARTICLES.set(reducedParticles);
            MahouConfig.PC_MODE.set(mode);
            MahouConfig.CLIENT_SPEC.save();
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.warn("Não foi possível salvar o preset do PC na config do mod", e);
        }
        setIfChanged(o.renderDistance(), renderDistance);
        setIfChanged(o.simulationDistance(), simulationDistance);
        setIfChanged(o.graphicsMode(), graphicsMode);
        setIfChanged(o.particles(), particles);
        setIfChanged(o.entityShadows(), entityShadows);
        setIfChanged(o.cloudStatus(), clouds);
        setIfChanged(o.biomeBlendRadius(), biomeBlend);
        if (Math.abs(o.entityDistanceScaling().get() - entityDistance) > 0.001D) o.entityDistanceScaling().set(entityDistance);
        setIfChanged(o.mipmapLevels(), mipmaps);
        o.save();
        DhBridge.apply(this);
    }

    private static <T> void setIfChanged(OptionInstance<T> opt, T value) {
        try {
            if (!value.equals(opt.get())) opt.set(value);
        } catch (RuntimeException e) {
            IsekaiMahou.LOGGER.debug("Opção de vídeo recusou o valor {}", value, e);
        }
    }

    private static Change change(String key, Component from, Component to) {
        return new Change(key, Component.translatable("screen.isekai_mahou.perf.opt." + key), from, to);
    }

    static Component fxName(Graphics g) {
        return Component.translatable("screen.isekai_mahou.perf.fx." + g.name().toLowerCase(java.util.Locale.ROOT));
    }

    private static Component onOff(boolean on) {
        return Component.translatable(on ? "options.on" : "options.off");
    }

    private static Component chunks(int n) {
        return Component.translatable("options.chunks", n);
    }

    private static Component blend(int r) {
        return r <= 0 ? Component.translatable("options.off") : Component.literal((r * 2 + 1) + "x" + (r * 2 + 1));
    }

    private static Component percent(double d) {
        return Component.literal(Math.round(d * 100) + "%");
    }
}
