package com.renanpontes.isekaimahou.feature.world_events.event;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.world_events.WeSites;
import com.renanpontes.isekaimahou.feature.world_events.WeState;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * <b>Terremoto</b>: o chão treme por uns 10 segundos (tremor de tela, respeitando a opção do jogador) e 1 a 3
 * <b>fendas pequenas</b> se abrem NO ERMO, perto de um jogador: rachaduras de ~12 blocos, 1–2 de largura e 3–7 de
 * fundura, com minérios comuns expostos nas paredes (carvão, cobre, ferro; raramente ouro, redstone, lápis,
 * esmeralda e, só no fundo, diamante).
 * <p>
 * Cada fenda só abre num lugar aprovado por {@link WeSites#areaClean} (longe de cidades, estruturas e blocos de
 * jogador) e cada bloco ainda passa por {@link WeSites#canEdit}. Sem nenhum lugar livre por perto, não há terremoto.
 */
public final class Earthquake {
    private static final int SHAKE_TICKS = 220;
    private static final int CARVE_START = 60;
    private static final int CARVE_EVERY = 4;
    private static final int SITE_RADIUS = 9;
    private static final int SHAKE_RANGE = 140;
    private static final float ORE_CHANCE = 0.10F;

    /** Colunas já abertas neste terremoto (uma coluna nunca é cavada duas vezes: a fenda não vira poço). */
    private static final LongOpenHashSet CARVED = new LongOpenHashSet();

    private Earthquake() {}

    @Nullable
    public static BlockPos findSite(ServerLevel level, ServerPlayer around) {
        return WeSites.findSite(level, around.blockPosition(), 24, 72, SITE_RADIUS, 12);
    }

    public static void start(ServerLevel level, WeState s, @Nullable ServerPlayer around) {
        RandomSource rnd = level.random;
        CARVED.clear();
        ListTag list = new ListTag();
        if (s.site != null) list.add(fissure(s.site, rnd));
        // Mais uma ou duas fendas, se houver lugar livre (nunca coladas umas nas outras)
        int more = rnd.nextInt(3);
        for (int i = 0; i < more && around != null; i++) {
            BlockPos p = WeSites.findSite(level, around.blockPosition(), 24, 72, SITE_RADIUS, 6);
            if (p == null) continue;
            boolean far = true;
            for (Tag t : list) {
                if (BlockPos.of(((CompoundTag) t).getLong("pos")).distSqr(p) < 26 * 26) far = false;
            }
            if (far) list.add(fissure(p, rnd));
        }
        s.extra.put("fissures", list);
    }

    private static CompoundTag fissure(BlockPos pos, RandomSource rnd) {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos.asLong());
        t.putFloat("ang", rnd.nextFloat() * Mth.TWO_PI);
        t.putInt("len", 10 + rnd.nextInt(5));
        t.putFloat("wobble", rnd.nextFloat() * 6F);
        return t;
    }

    public static boolean tick(ServerLevel level, WeState s, int age) {
        BlockPos site = s.site;
        if (site == null) return false;
        if (age < SHAKE_TICKS && age % 20 == 0) rumble(level, site, age);
        if (age >= CARVE_START && (age - CARVE_START) % CARVE_EVERY == 0) {
            int step = (age - CARVE_START) / CARVE_EVERY;
            ListTag list = s.extra.getList("fissures", Tag.TAG_COMPOUND);
            for (Tag t : list) carveStep(level, (CompoundTag) t, step);
        }
        return true;
    }

    private static void rumble(ServerLevel level, BlockPos site, int age) {
        Vec3 c = Vec3.atCenterOf(site);
        // cresce, segura e some
        float k = age < 60 ? 0.6F : age < 160 ? 1.3F : 0.7F;
        BossKit.shake(level, c, SHAKE_RANGE, k, 30);
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState());
        for (ServerPlayer p : level.players()) {
            if (p.blockPosition().distSqr(site) > (double) SHAKE_RANGE * SHAKE_RANGE) continue;
            p.playNotifySound(SoundEvents.WARDEN_DIG, SoundSource.AMBIENT, 0.9F, 0.5F);
            if (p.onGround()) level.sendParticles(p, dust, false, p.getX(), p.getY() + 0.1, p.getZ(), 10, 3, 0.1, 3, 0.05);
        }
    }

    // ------------------------------------------------------------------ fendas
    private static void carveStep(ServerLevel level, CompoundTag f, int step) {
        int len = f.getInt("len");
        if (step < 0 || step >= len) return;
        BlockPos start = BlockPos.of(f.getLong("pos"));
        float ang = f.getFloat("ang");
        double dx = Math.cos(ang), dz = Math.sin(ang);
        // começa atrás do centro: a fenda fica centrada no lugar aprovado
        double along = step - len / 2.0;
        double side = Math.sin(step * 0.9 + f.getFloat("wobble")) * 1.2;
        double cx = start.getX() + 0.5 + dx * along - dz * side;
        double cz = start.getZ() + 0.5 + dz * along + dx * side;
        int depth = 3 + (int) Math.round(4 * Math.sin(Math.PI * (step + 0.5) / len));
        boolean wide = step > 1 && step < len - 2;

        carveColumn(level, start, Mth.floor(cx), Mth.floor(cz), depth);
        if (wide) carveColumn(level, start, Mth.floor(cx - dz), Mth.floor(cz + dx), depth - 1);
    }

    private static void carveColumn(ServerLevel level, BlockPos site, int x, int z, int depth) {
        if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) == null) return;
        if (!CARVED.add(BlockPos.asLong(x, 0, z))) return;
        int gy = WeSites.groundY(level, x, z);
        if (gy == Integer.MIN_VALUE || Math.abs(gy - site.getY()) > 8) return;
        RandomSource rnd = level.random;
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        BlockState air = Blocks.AIR.defaultBlockState();

        // mato e neve de cima
        for (int y = gy + 1; y <= gy + 2; y++) {
            m.set(x, y, z);
            if (level.getBlockState(m).canBeReplaced() && WeSites.canEdit(level, m)) level.setBlock(m, air, 3);
        }
        BlockPos top = new BlockPos(x, gy, z);
        BlockState topState = level.getBlockState(top);
        boolean opened = false;
        for (int i = 0; i < depth; i++) {
            m.set(x, gy - i, z);
            if (!WeSites.canEdit(level, m) || !level.getFluidState(m).isEmpty()) break;
            level.setBlock(m, air, 3);
            opened = true;
            // paredes: a partir do 3º bloco de fundura, às vezes o minério aparece
            if (i < 2) continue;
            for (Direction dir : Direction.Plane.HORIZONTAL) {
                if (rnd.nextFloat() >= ORE_CHANCE) continue;
                BlockPos wall = m.relative(dir);
                exposeOre(level, wall, rnd, false);
            }
        }
        if (!opened) return;
        if (rnd.nextFloat() < 0.3F) exposeOre(level, new BlockPos(x, gy - depth, z), rnd, true);
        // estalo e poeira do bloco que rachou
        level.levelEvent(2001, top, Block.getId(topState));
        level.playSound(null, top, SoundEvents.DEEPSLATE_BREAK, SoundSource.BLOCKS, 1.2F, 0.6F);
    }

    private static void exposeOre(ServerLevel level, BlockPos pos, RandomSource rnd, boolean bottom) {
        if (!WeSites.canEdit(level, pos)) return;
        BlockState st = level.getBlockState(pos);
        if (!st.is(BlockTags.BASE_STONE_OVERWORLD) && !st.is(BlockTags.DIRT)) return;
        boolean deep = st.is(Blocks.DEEPSLATE) || st.is(Blocks.TUFF);
        level.setBlock(pos, pickOre(rnd, bottom, deep).defaultBlockState(), 3);
    }

    /** Minérios comuns na maioria; os bons são raros e o diamante só aparece no FUNDO da fenda. */
    private static Block pickOre(RandomSource rnd, boolean bottom, boolean deep) {
        int r = rnd.nextInt(100);
        if (bottom && r < 4) return deep ? Blocks.DEEPSLATE_DIAMOND_ORE : Blocks.DIAMOND_ORE;
        if (r < 34) return deep ? Blocks.DEEPSLATE_COAL_ORE : Blocks.COAL_ORE;
        if (r < 58) return deep ? Blocks.DEEPSLATE_COPPER_ORE : Blocks.COPPER_ORE;
        if (r < 80) return deep ? Blocks.DEEPSLATE_IRON_ORE : Blocks.IRON_ORE;
        if (r < 87) return deep ? Blocks.DEEPSLATE_REDSTONE_ORE : Blocks.REDSTONE_ORE;
        if (r < 92) return deep ? Blocks.DEEPSLATE_LAPIS_ORE : Blocks.LAPIS_ORE;
        if (r < 98) return deep ? Blocks.DEEPSLATE_GOLD_ORE : Blocks.GOLD_ORE;
        return deep ? Blocks.DEEPSLATE_EMERALD_ORE : Blocks.EMERALD_ORE;
    }
}
