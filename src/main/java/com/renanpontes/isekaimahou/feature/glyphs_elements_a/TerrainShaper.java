package com.renanpontes.isekaimahou.feature.glyphs_elements_a;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Obra do Aplainar Terreno: uma fila própria, poucas colunas por tick (o terreno vai se nivelando do centro para fora).
 * <p>
 * Para cada coluna do círculo: apaga tudo acima do nível (até {@code cutHeight} blocos), nivela o bloco do topo
 * (terreno natural vira o "topo" da paleta, ex.: grama) e tapa buracos/água abaixo com a paleta até achar chão firme
 * (no máx. {@link #FILL_DEPTH}). Cada bloco é checado na hora com {@link Destruction#protectedAt} (inquebráveis, blocos com
 * dados, arenas protegidas, spawn) e blocos com inventário nunca são tocados. Troca SEM drops e sem atualizar vizinhos
 * (mesmas flags da fila de destruição do núcleo), então nada é duplicado.
 */
public final class TerrainShaper {
    public static final float BASE_RADIUS = 4.0F;
    public static final float MAX_RADIUS = 24.0F;
    /** Mana extra por coluna aplainada (raio 6 ≈ 17 de mana; raio 12 ≈ 68). */
    public static final float MANA_PER_COLUMN = 0.15F;
    static final int COLUMNS_PER_TICK = 12;
    static final int FILL_DEPTH = 12;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;

    private static final Map<UUID, Job> JOBS = new HashMap<>();
    /** Último pedido aceito de cada jogador (Zonas/Auras pedem de novo a cada pulso: ignora os repetidos). */
    private static final Map<UUID, Request> LAST = new HashMap<>();

    private record Request(BlockPos at, long tick) {}

    /** Paleta do aterro: topo, 2 camadas logo abaixo e o fundo. */
    private record Palette(BlockState top, BlockState sub, BlockState deep) {}

    // =====================================================================================
    //  Pedido
    // =====================================================================================
    public static void request(SpellContext ctx, Vec3 center, SpellStats stats) {
        if (!(ctx.caster() instanceof ServerPlayer p) || p instanceof FakePlayer || p.isSpectator() || !p.mayBuild()) return;
        ServerLevel level = ctx.level();
        if (p.level() != level || JOBS.containsKey(p.getUUID())) return;
        long now = level.getGameTime();
        BlockPos at = BlockPos.containing(center);
        Request last = LAST.get(p.getUUID());
        if (last != null && now - last.tick < 100 && now >= last.tick && last.at.distSqr(at) < 9) return;

        // Altura: a dos seus pés (se você estiver perto em altura), senão a do ponto atingido
        double feet = p.getY();
        int topY = Mth.floor((Math.abs(center.y - feet) <= 16 ? feet : center.y) - 0.01);
        if (level.isOutsideBuildHeight(topY) || level.isOutsideBuildHeight(topY + 1)) return;

        MagicData d = MagicApi.get(p);
        int aoe = Math.max(-1, stats.aoe + ctx.formStats().aoe);
        float r = BASE_RADIUS * (1.0F + 0.5F * aoe);
        if (stats.concentrate || ctx.formStats().concentrate) r *= 0.6F;
        r *= 0.8F + 0.2F * Math.min(3.0F, stats.power() * ctx.powerMult());
        float cap = Math.min(MAX_RADIUS, 6.0F + 2.0F * d.circle());
        r = Mth.clamp(r, 1.5F, cap);

        // Mana extra pela área (criativo: grátis). Sem mana para tudo: o raio encolhe até caber.
        boolean creative = p.getAbilities().instabuild;
        if (!creative) {
            float maxCols = d.mana() / MANA_PER_COLUMN;
            if (Mth.PI * r * r > maxCols) r = (float) Math.sqrt(maxCols / Mth.PI);
            if (r < 1.5F) {
                p.displayClientMessage(Component.translatable("message.isekai_mahou.glyphs_elements_a.flatten_no_mana")
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
        }
        List<int[]> cols = columns(r);
        if (!creative) d.consumeMana(cols.size() * MANA_PER_COLUMN);

        Palette pal = palette(level, at.getX(), topY, at.getZ());
        int cut = Mth.clamp(Mth.ceil(r) + 8, 10, 32);
        JOBS.put(p.getUUID(), new Job(level, p.getUUID(), at.getX(), topY, at.getZ(), cols, pal, cut, r));
        LAST.put(p.getUUID(), new Request(at, now));
        Vec3 c = new Vec3(at.getX() + 0.5, topY + 1.05, at.getZ() + 0.5);
        ElementFx.runeCircle(level, c, Element.EARTH.color, r, 40, 1);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.ROOTED_DIRT_PLACE, SoundSource.BLOCKS, 1.2F, 0.6F);
        p.displayClientMessage(Component.translatable("message.isekai_mahou.glyphs_elements_a.flatten_start",
                Math.round(r), cols.size()).withStyle(ChatFormatting.GOLD), true);
    }

    /** Colunas (dx, dz) do círculo, do centro para fora. */
    private static List<int[]> columns(float r) {
        int R = Mth.ceil(r);
        List<int[]> out = new ArrayList<>();
        float r2 = r * r;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                if (dx * dx + dz * dz <= r2) out.add(new int[] {dx, dz});
            }
        }
        out.sort(Comparator.comparingInt(a -> a[0] * a[0] + a[1] * a[1]));
        return out;
    }

    // =====================================================================================
    //  Paleta (o "chão do lugar")
    // =====================================================================================
    private static Palette palette(ServerLevel level, int x, int topY, int z) {
        BlockState sample = null;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        for (int y = topY + 4; y >= topY - 8; y--) {
            BlockState st = level.getBlockState(m.set(x, y, z));
            if (!st.isAir() && !st.canBeReplaced() && st.getFluidState().isEmpty() && natural(st)) {
                sample = st;
                break;
            }
        }
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState(), dirt = Blocks.DIRT.defaultBlockState();
        if (level.dimension() == Level.NETHER) {
            BlockState n = Blocks.NETHERRACK.defaultBlockState();
            if (sample != null && (sample.is(Blocks.SOUL_SAND) || sample.is(Blocks.SOUL_SOIL))) {
                BlockState s = Blocks.SOUL_SOIL.defaultBlockState();
                return new Palette(s, s, n);
            }
            if (sample != null && (sample.is(Blocks.CRIMSON_NYLIUM) || sample.is(Blocks.WARPED_NYLIUM))) return new Palette(sample, n, n);
            return new Palette(n, n, n);
        }
        if (level.dimension() == Level.END) {
            BlockState e = Blocks.END_STONE.defaultBlockState();
            return new Palette(e, e, e);
        }
        BlockState stone = Blocks.STONE.defaultBlockState();
        if (sample == null) return new Palette(grass, dirt, stone);
        if (sample.is(Blocks.RED_SAND)) {
            BlockState rs = Blocks.RED_SANDSTONE.defaultBlockState();
            return new Palette(Blocks.RED_SAND.defaultBlockState(), rs, rs);
        }
        if (sample.is(BlockTags.SAND) || sample.is(Blocks.SANDSTONE)) {
            BlockState ss = Blocks.SANDSTONE.defaultBlockState();
            return new Palette(Blocks.SAND.defaultBlockState(), ss, ss);
        }
        if (sample.is(Blocks.MYCELIUM) || sample.is(Blocks.PODZOL)) return new Palette(sample.getBlock().defaultBlockState(), dirt, stone);
        if (sample.is(Blocks.DEEPSLATE)) return new Palette(grass, dirt, Blocks.DEEPSLATE.defaultBlockState());
        return new Palette(grass, dirt, stone);
    }

    /** Terreno natural (pode ser nivelado/trocado pelo topo da paleta). Construções nunca são trocadas. */
    private static boolean natural(BlockState st) {
        return st.is(BlockTags.DIRT) || st.is(BlockTags.SAND) || st.is(BlockTags.BASE_STONE_OVERWORLD)
                || st.is(BlockTags.BASE_STONE_NETHER) || st.is(BlockTags.NYLIUM) || st.is(Blocks.GRAVEL) || st.is(Blocks.CLAY)
                || st.is(Blocks.SNOW_BLOCK) || st.is(Blocks.SANDSTONE) || st.is(Blocks.RED_SANDSTONE) || st.is(Blocks.FARMLAND)
                || st.is(Blocks.DIRT_PATH) || st.is(Blocks.SOUL_SAND) || st.is(Blocks.SOUL_SOIL) || st.is(Blocks.END_STONE)
                || st.is(BlockTags.TERRACOTTA) || st.is(Blocks.POWDER_SNOW) || st.is(Blocks.CALCITE) || st.is(Blocks.TUFF);
    }

    // =====================================================================================
    //  Fila
    // =====================================================================================
    static void tick(net.minecraft.server.MinecraftServer server) {
        if (JOBS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Job>> it = JOBS.entrySet().iterator();
        while (it.hasNext()) {
            Job job = it.next().getValue();
            ServerPlayer p = server.getPlayerList().getPlayer(job.owner);
            // Dono saiu ou mudou de dimensão: a obra para onde está
            if (p == null || p.level() != job.level) {
                it.remove();
                continue;
            }
            job.step(p);
            if (job.done()) {
                job.finish(p);
                it.remove();
            }
        }
    }

    static void forget(UUID id) {
        JOBS.remove(id);
        LAST.remove(id);
    }

    static void clear() {
        JOBS.clear();
        LAST.clear();
    }

    private static final class Job {
        final ServerLevel level;
        final UUID owner;
        final int cx, topY, cz;
        final List<int[]> cols;
        final Palette pal;
        final int cut;
        final float radius;
        int index;
        int changed;
        final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        Job(ServerLevel level, UUID owner, int cx, int topY, int cz, List<int[]> cols, Palette pal, int cut, float radius) {
            this.level = level;
            this.owner = owner;
            this.cx = cx;
            this.topY = topY;
            this.cz = cz;
            this.cols = cols;
            this.pal = pal;
            this.cut = cut;
            this.radius = radius;
        }

        boolean done() {
            return index >= cols.size();
        }

        void step(ServerPlayer p) {
            int lastX = cx, lastZ = cz;
            for (int n = 0; n < COLUMNS_PER_TICK && index < cols.size(); n++) {
                int[] c = cols.get(index++);
                lastX = cx + c[0];
                lastZ = cz + c[1];
                column(p, lastX, lastZ);
            }
            // Retorno visual/sonoro da obra avançando
            BlockState shown = pal.top;
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, shown), lastX + 0.5, topY + 1.1, lastZ + 0.5,
                    6, 0.4, 0.1, 0.4, 0.05);
            if (index % (COLUMNS_PER_TICK * 4) < COLUMNS_PER_TICK) {
                level.playSound(null, lastX + 0.5, topY + 1, lastZ + 0.5, SoundEvents.ROOTED_DIRT_BREAK, SoundSource.BLOCKS, 0.6F,
                        0.7F + level.random.nextFloat() * 0.3F);
            }
        }

        private void column(ServerPlayer p, int x, int z) {
            if (!level.isLoaded(m.set(x, topY, z))) return;
            // 1) Corta tudo acima do nível (de cima para baixo)
            int maxY = Math.min(level.getMaxBuildHeight() - 1, topY + cut);
            for (int y = maxY; y > topY; y--) {
                BlockState st = level.getBlockState(m.set(x, y, z));
                if (st.isAir()) continue;
                set(p, st, Blocks.AIR.defaultBlockState());
            }
            // 2) Topo: buraco/água vira o topo da paleta; terreno natural é nivelado para o topo
            BlockState top = level.getBlockState(m.set(x, topY, z));
            boolean hole = hollow(top);
            if (hole || (natural(top) && !top.is(pal.top.getBlock()))) set(p, top, pal.top);
            // 3) Aterro: desce tapando buracos até achar chão firme
            if (!hole) return;
            int minY = Math.max(level.getMinBuildHeight(), topY - FILL_DEPTH);
            for (int y = topY - 1; y >= minY; y--) {
                BlockState st = level.getBlockState(m.set(x, y, z));
                if (!hollow(st)) break;
                set(p, st, topY - y <= 2 ? pal.sub : pal.deep);
            }
        }

        /** Ar, planta, neve fina ou líquido: conta como buraco. */
        private boolean hollow(BlockState st) {
            return st.isAir() || st.canBeReplaced() || !st.getFluidState().isEmpty() && !st.isSolid();
        }

        private void set(ServerPlayer p, BlockState from, BlockState to) {
            if (from == to || from.hasBlockEntity()) return;
            if (Destruction.protectedAt(level, m, p)) return;
            level.setBlock(m, to, FLAGS);
            changed++;
        }

        void finish(ServerPlayer p) {
            Vec3 c = new Vec3(cx + 0.5, topY + 1.05, cz + 0.5);
            ElementFx.ring(level, c, Element.EARTH, radius, Math.max(16, Math.round(radius * 6)));
            level.playSound(null, c.x, c.y, c.z, SoundEvents.ANVIL_LAND, SoundSource.BLOCKS, 0.4F, 0.6F);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.glyphs_elements_a.flatten_done", changed)
                    .withStyle(ChatFormatting.GREEN), true);
        }
    }

    private TerrainShaper() {}
}
