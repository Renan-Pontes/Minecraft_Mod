package com.renanpontes.isekaimahou.feature.ambience.client;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.ambience.AmbienceConfig;
import com.renanpontes.isekaimahou.feature.structures_b.StructuresBContent;
import com.renanpontes.isekaimahou.feature.world_events.WorldEventType;
import com.renanpontes.isekaimahou.feature.world_events.client.WeClientState;
import com.renanpontes.isekaimahou.glyph.fx.FxOptions;
import com.renanpontes.isekaimahou.glyph.fx.SpellFxParticles;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.common.Tags;

/**
 * SÓ CLIENTE. "Mundo vivo": partículas ambientes esparsas em volta do jogador, escolhidas pelo bioma e pela hora —
 * vaga-lumes nas noites quentes, folhas caindo das copas, pólen nos campos floridos, motas de mana nos biomas de
 * cristal, fogos-fátuos no pântano assombrado, brasas no Nether — e estrelas cadentes nas noites limpas (muitas na
 * Chuva de Estrelas, evento do mundo).
 * <p>
 * Custo: uma rodada a cada 4 ticks com 1–4 tentativas (algumas leituras de bloco/luz cada); o perfil de cada bioma é
 * calculado uma vez e guardado. Nada no preset BAIXO; os provedores das partículas ainda afinam pelo preset.
 * Desliga em Mods &gt; Isekai Mahou &gt; Config ({@code worldLife}). Nada na tela (HUD).
 */
public final class AmbientLife {
    private static final int PERIOD = 4;
    private static final int RADIUS = 16;
    private static final int MAX_STARS = 6;

    /** O que cada bioma tem (calculado uma vez por bioma e mundo). */
    private record Profile(boolean fireflies, boolean leaves, boolean pollen, int mote, int wisp, int ember) {
        static final Profile NONE = new Profile(false, false, false, 0, 0, 0);
    }

    private static final Map<Biome, Profile> CACHE = new IdentityHashMap<>();
    private static ClientLevel cachedFor;
    private static long ticks;

    /** Estrela cadente em voo: posição, direção (blocos/tick) e vida. Só desenha um rastro de partículas. */
    private static final class Star {
        double x, y, z;
        final double vx, vy, vz;
        final int life;
        final int rgb;
        int age;

        Star(double x, double y, double z, double vx, double vy, double vz, int life, int rgb) {
            this.x = x; this.y = y; this.z = z;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.life = life;
            this.rgb = rgb;
        }
    }

    private static final List<Star> STARS = new ArrayList<>();

    private AmbientLife() {}

    public static void reset() {
        CACHE.clear();
        cachedFor = null;
        STARS.clear();
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        if (level == null || player == null || mc.isPaused()) return;
        if (level != cachedFor) {
            CACHE.clear();
            STARS.clear();
            cachedFor = level;
        }
        if (!AmbienceConfig.on(AmbienceConfig.WORLD_LIFE) || !FxQuality.atLeast(MahouConfig.Graphics.MEDIUM)) {
            STARS.clear();
            return;
        }
        ticks++;
        stars(level, player);
        if (ticks % PERIOD != 0) return;
        Holder<Biome> biome = level.getBiome(player.blockPosition());
        Profile p = CACHE.computeIfAbsent(biome.value(), b -> profile(biome));
        if (p == Profile.NONE) return;
        RandomSource r = level.random;
        long time = Math.floorMod(level.getDayTime(), 24000L);
        boolean night = time >= 13000 && time < 23000;
        boolean dusk = time >= 11500 && time < 13000;
        boolean wet = level.isRaining();
        if (p.fireflies && (night || dusk) && !wet && r.nextFloat() < (night ? 0.9F : 0.35F)) firefly(level, player, r);
        if (p.leaves && r.nextFloat() < 0.75F) leaf(level, player, r);
        if (p.pollen && !night && !wet && r.nextFloat() < 0.45F) pollen(level, player, r);
        if (p.mote != 0) mote(level, player, r, p.mote, night);
        if (p.wisp != 0 && (night || dusk) && r.nextFloat() < 0.35F) wisp(level, player, r, p.wisp);
        if (p.ember != 0 && r.nextFloat() < 0.8F) ember(level, player, r, p.ember);
    }

    // ------------------------------------------------------------------ perfil do bioma

    private static Profile profile(Holder<Biome> h) {
        String path = h.unwrapKey().map(k -> k.location()).filter(l -> IsekaiMahou.MODID.equals(l.getNamespace()))
                .map(ResourceLocation::getPath).orElse("");
        // Biomas mágicos do mod
        switch (path) {
            case "crystal_forest" -> { return new Profile(true, true, false, 0x7FE8FF, 0, 0); }
            case "crystal_cavern" -> { return new Profile(false, false, false, 0x7FE8FF, 0, 0); }
            case "twilight_grove" -> { return new Profile(true, true, false, 0xB48CFF, 0xD8C8FF, 0); }
            case "haunted_swamp" -> { return new Profile(false, true, false, 0, 0xC8FFE8, 0); }
            case "astral_gardens" -> { return new Profile(false, false, false, 0xC89BFF, 0, 0); }
            case "ember_crystal_wastes" -> { return new Profile(false, false, false, 0, 0, 0xFF7A2A); }
            case "sakura_fields", "flat_lands_kingdom_meadows", "fey_glade" -> { return new Profile(true, false, true, 0, 0, 0); }
            case "flat_lands_golden_steppe" -> { return new Profile(true, false, false, 0, 0, 0); }
            default -> { }
        }
        if (h.is(BiomeTags.IS_NETHER)) {
            return h.is(Biomes.NETHER_WASTES) || h.is(Biomes.CRIMSON_FOREST) ? new Profile(false, false, false, 0, 0, 0xFF6A1A)
                    : Profile.NONE;
        }
        if (!h.is(BiomeTags.IS_OVERWORLD) || h.is(BiomeTags.IS_OCEAN) || h.is(Tags.Biomes.IS_CAVE)) return Profile.NONE;
        boolean cold = h.is(Tags.Biomes.IS_SNOWY) || h.is(Tags.Biomes.IS_COLD_OVERWORLD) || h.value().getBaseTemperature() < 0.3F;
        boolean wooded = h.is(BiomeTags.IS_FOREST) || h.is(BiomeTags.IS_JUNGLE) || h.is(BiomeTags.IS_TAIGA);
        boolean green = wooded || h.is(Tags.Biomes.IS_PLAINS) || h.is(Tags.Biomes.IS_SWAMP) || h.is(BiomeTags.IS_RIVER)
                || h.is(Biomes.MEADOW) || h.is(Biomes.CHERRY_GROVE);
        boolean floral = h.is(Tags.Biomes.IS_FLORAL) || h.is(Biomes.MEADOW) || h.is(Biomes.SUNFLOWER_PLAINS)
                || h.is(Biomes.FLOWER_FOREST);
        boolean fireflies = green && !cold && !h.is(Tags.Biomes.IS_DRY);
        boolean pollen = floral && !cold;
        if (!fireflies && !wooded && !pollen) return Profile.NONE;
        return new Profile(fireflies, wooded, pollen, 0, 0, 0);
    }

    // ------------------------------------------------------------------ cada tipo

    /** Vaga-lume perto do chão, ao ar livre (ou sob as copas), longe das luzes da cidade. */
    private static void firefly(ClientLevel level, LocalPlayer pl, RandomSource r) {
        BlockPos.MutableBlockPos m = around(pl, r, RADIUS, -1, 4);
        if (!level.isEmptyBlock(m) || level.getBrightness(LightLayer.SKY, m) < 10 || level.getBrightness(LightLayer.BLOCK, m) > 7) return;
        if (!groundBelow(level, m, 4)) return;
        level.addParticle(StructuresBContent.FAIRY_FIREFLY.get(), m.getX() + r.nextDouble(), m.getY() + r.nextDouble(),
                m.getZ() + r.nextDouble(), 0, 0, 0);
    }

    /** Folha que se solta de uma copa perto do jogador, na cor da folhagem do bioma. */
    private static void leaf(ClientLevel level, LocalPlayer pl, RandomSource r) {
        int x = Mth.floor(pl.getX()) + r.nextInt(RADIUS * 2 + 1) - RADIUS;
        int z = Mth.floor(pl.getZ()) + r.nextInt(RADIUS * 2 + 1) - RADIUS;
        int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
        if (top < pl.getY() - 4 || top > pl.getY() + 24) return;
        BlockPos pos = new BlockPos(x, top, z);
        BlockState s = level.getBlockState(pos);
        if (!s.is(BlockTags.LEAVES)) return;
        // procura a parte de baixo da copa (no máximo 6 blocos) para a folha sair por baixo
        BlockPos.MutableBlockPos m = pos.mutable();
        for (int i = 0; i < 6 && level.getBlockState(m.move(0, -1, 0)).is(BlockTags.LEAVES); i++) { }
        if (!level.isEmptyBlock(m)) return;
        int rgb = BiomeColors.getAverageFoliageColor(level, pos);
        FxOptions o = new FxOptions(SpellFxParticles.LEAF.get(), rgb, 1.1F, 90 + r.nextInt(60), -1, 0);
        level.addParticle(o, x + r.nextDouble(), m.getY() + 0.7, z + r.nextDouble(), (r.nextDouble() - 0.5) * 0.04, 0,
                (r.nextDouble() - 0.5) * 0.04);
    }

    /** Pólen dourado flutuando de dia nos campos floridos. */
    private static void pollen(ClientLevel level, LocalPlayer pl, RandomSource r) {
        BlockPos.MutableBlockPos m = around(pl, r, 12, 0, 3);
        if (!level.isEmptyBlock(m) || level.getBrightness(LightLayer.SKY, m) < 12) return;
        FxOptions o = new FxOptions(SpellFxParticles.GLOW.get(), 0xFFF2A8, 0.35F, 70 + r.nextInt(40), -1, 0);
        level.addParticle(o, m.getX() + r.nextDouble(), m.getY() + r.nextDouble(), m.getZ() + r.nextDouble(),
                (r.nextDouble() - 0.5) * 0.03, 0.004, (r.nextDouble() - 0.5) * 0.03);
    }

    /** Mota de mana subindo devagar (biomas de cristal, Bosque Crepuscular, Jardins Astrais). Mais à noite. */
    private static void mote(ClientLevel level, LocalPlayer pl, RandomSource r, int rgb, boolean night) {
        if (r.nextFloat() > (night ? 0.9F : 0.5F)) return;
        BlockPos.MutableBlockPos m = around(pl, r, 12, -1, 5);
        if (!level.isEmptyBlock(m)) return;
        FxOptions o = new FxOptions(SpellFxParticles.MOTE.get(), rgb, 0.7F, 50 + r.nextInt(40), -1, 0);
        level.addParticle(o, m.getX() + r.nextDouble(), m.getY() + r.nextDouble(), m.getZ() + r.nextDouble(),
                0, 0.012 + r.nextDouble() * 0.01, 0);
    }

    /** Fogo-fátuo pálido a meia altura (pântano assombrado / bosque crepuscular), só ao anoitecer e à noite. */
    private static void wisp(ClientLevel level, LocalPlayer pl, RandomSource r, int rgb) {
        BlockPos.MutableBlockPos m = around(pl, r, 14, 0, 3);
        if (!level.isEmptyBlock(m) || level.getBrightness(LightLayer.BLOCK, m) > 7) return;
        FxOptions o = new FxOptions(SpellFxParticles.GLOW.get(), rgb, 1.1F, 60 + r.nextInt(40), -1, 0);
        level.addParticle(o, m.getX() + r.nextDouble(), m.getY() + r.nextDouble(), m.getZ() + r.nextDouble(),
                (r.nextDouble() - 0.5) * 0.02, 0.003, (r.nextDouble() - 0.5) * 0.02);
    }

    /** Brasa subindo do chão quente. */
    private static void ember(ClientLevel level, LocalPlayer pl, RandomSource r, int rgb) {
        BlockPos.MutableBlockPos m = around(pl, r, 14, -2, 3);
        if (!level.isEmptyBlock(m)) return;
        FxOptions o = new FxOptions(SpellFxParticles.EMBER.get(), rgb, 0.6F, 40 + r.nextInt(30), -1, 0);
        level.addParticle(o, m.getX() + r.nextDouble(), m.getY() + r.nextDouble(), m.getZ() + r.nextDouble(),
                (r.nextDouble() - 0.5) * 0.02, 0.02, (r.nextDouble() - 0.5) * 0.02);
    }

    // ------------------------------------------------------------------ estrelas cadentes

    /**
     * Noite limpa, ao ar livre no mundo normal: de vez em quando uma estrela risca o céu (≈ uma por minuto); na Chuva
     * de Estrelas, várias por segundo. Cada estrela é só um rastro de partículas "sempre visíveis" bem no alto.
     */
    private static void stars(ClientLevel level, LocalPlayer pl) {
        for (int i = STARS.size() - 1; i >= 0; i--) {
            Star s = STARS.get(i);
            if (++s.age > s.life) {
                STARS.remove(i);
                continue;
            }
            s.x += s.vx; s.y += s.vy; s.z += s.vz;
            float fade = 1F - s.age / (float) s.life;
            level.addAlwaysVisibleParticle(new FxOptions(SpellFxParticles.GLOW.get(), s.rgb, 5.5F * (0.5F + fade * 0.5F), 7, -1, 0),
                    true, s.x, s.y, s.z, 0, 0, 0);
            level.addAlwaysVisibleParticle(new FxOptions(SpellFxParticles.MOTE.get(), s.rgb, 3.0F, 12, -1, 0),
                    true, s.x - s.vx * 0.5, s.y - s.vy * 0.5, s.z - s.vz * 0.5, 0, 0, 0);
        }
        if (level.dimension() != Level.OVERWORLD || STARS.size() >= MAX_STARS) return;
        if (level.getStarBrightness(1F) < 0.35F || level.getRainLevel(1F) > 0.2F) return;
        if (level.getBrightness(LightLayer.SKY, pl.blockPosition().above()) < 12) return;
        boolean shower = WeClientState.active() == WorldEventType.STAR_SHOWER;
        RandomSource r = level.random;
        if (r.nextInt(shower ? 25 : 1200) != 0) return;
        // longe o bastante para parecer céu, perto o bastante para a névoa não esconder
        double reach = Math.min(90, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 * 0.6);
        double ang = r.nextDouble() * Math.PI * 2;
        double dist = reach * (0.55 + r.nextDouble() * 0.45);
        double x = pl.getX() + Math.cos(ang) * dist;
        double z = pl.getZ() + Math.sin(ang) * dist;
        double y = pl.getY() + 45 + r.nextDouble() * 30;
        double dir = r.nextDouble() * Math.PI * 2;
        double speed = 1.8 + r.nextDouble() * 1.2;
        int rgb = r.nextInt(4) == 0 ? 0xFFE6A0 : 0xDDEBFF;
        STARS.add(new Star(x, y, z, Math.cos(dir) * speed, -0.25 - r.nextDouble() * 0.35, Math.sin(dir) * speed,
                14 + r.nextInt(12), rgb));
    }

    // ------------------------------------------------------------------ ajudantes

    private static BlockPos.MutableBlockPos around(LocalPlayer pl, RandomSource r, int radius, int dyMin, int dyMax) {
        return new BlockPos.MutableBlockPos(
                Mth.floor(pl.getX()) + r.nextInt(radius * 2 + 1) - radius,
                Mth.floor(pl.getY()) + dyMin + r.nextInt(dyMax - dyMin + 1),
                Mth.floor(pl.getZ()) + r.nextInt(radius * 2 + 1) - radius);
    }

    /** Há chão firme até {@code max} blocos abaixo? (o vaga-lume não nasce no meio de um abismo) */
    private static boolean groundBelow(ClientLevel level, BlockPos pos, int max) {
        BlockPos.MutableBlockPos m = pos.mutable();
        for (int i = 0; i < max; i++) {
            m.move(0, -1, 0);
            if (!level.getBlockState(m).isAir()) return true;
        }
        return false;
    }
}
