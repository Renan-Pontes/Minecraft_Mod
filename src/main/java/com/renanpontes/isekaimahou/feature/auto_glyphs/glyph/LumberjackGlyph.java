package com.renanpontes.isekaimahou.feature.auto_glyphs.glyph;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.auto_glyphs.Delivery;
import com.renanpontes.isekaimahou.feature.auto_glyphs.Filters;
import com.renanpontes.isekaimahou.feature.auto_glyphs.WorkMana;
import com.renanpontes.isekaimahou.feature.auto_glyphs.WorkProbe;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.Pedestals;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.glyph.AreaEffect;
import com.renanpontes.isekaimahou.feature.automation.glyph.AutoArea;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Lenhador (Arcano t1): derruba árvores INTEIRAS da área (tronco + galhos ligados + folhas naturais) e replanta uma
 * muda na base. A muda vem, nesta ordem: do FILTRO (pedestal) / mão secundária (jogador), das próprias folhas
 * derrubadas, dos baús do pedestal / seu inventário.
 * <ul>
 *   <li>1 árvore por conjuração (+1 por Amplificar, até 4). Área: raio 5 (Expandir aumenta).</li>
 *   <li>Só conta como árvore: troncos ligados (até 256) com a base em terra/grama/musgo e pelo menos 4 folhas
 *       NATURAIS encostadas — casas de madeira sem folhas naturais ficam em pé.</li>
 *   <li>Custo: a magia cobre ~6 troncos; o resto é mana por bloco (tronco ~3,5, folha ~0,8). Sem mana para a árvore
 *       inteira, ela não é cortada (nunca deixa meia árvore).</li>
 *   <li>Drops: Coletor Arcano → baús do pedestal → topo do pedestal (na mão: seu inventário). Fortuna: mais mudas/maçãs.</li>
 * </ul>
 */
public class LumberjackGlyph extends AreaEffect implements WorkProbe {
    public static final String ID = "lumberjack";
    public static final float BASE_RADIUS = 5.0F;
    private static final float INCLUDED = 20F;
    private static final int MAX_LOGS = 256;
    private static final int MAX_LEAVES = 400;
    private static final int MIN_NATURAL_LEAVES = 4;
    private static final int SPREAD = 9;
    private static final int HEIGHT = 48;

    public LumberjackGlyph() {
        super(ID, 8);
        element(Element.ARCANE);
        tier(1);
    }

    @Override
    public float hintRadius() {
        return BASE_RADIUS;
    }

    /** Uma árvore achada: troncos, folhas naturais e as bases (troncos de baixo sobre terra). */
    private record Tree(Set<BlockPos> logs, Set<BlockPos> leaves, List<BlockPos> bases, float points) {}

    @Override
    protected void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit) {
        ServerLevel level = ctx.level();
        int r = AutoArea.radius(stats, BASE_RADIUS);
        List<BlockPos> bases = findBases(level, center, r);
        if (bases.isEmpty()) return;
        RunicPedestalBlockEntity pedestal = Pedestals.of(ctx, at);
        int maxTrees = Mth.clamp(1 + Math.max(0, stats.amplify), 1, 4);
        FarmTools tools = new FarmTools(level, stats);
        ItemStack axe = tools.axe();
        WorkMana mana = WorkMana.open(ctx, pedestal, INCLUDED);
        Delivery out = Delivery.open(ctx, pedestal, at);
        ItemStack filter = Filters.of(ctx, true);
        Item preferred = isSapling(filter) ? filter.getItem() : null;
        boolean city = FarmTools.nearCity(level, center);
        Entity caster = ctx.caster();

        Set<BlockPos> seen = new HashSet<>();
        List<ItemStack> drops = new ArrayList<>();
        int felled = 0, logs = 0, replanted = 0;
        boolean noMana = false, noSapling = false;
        for (BlockPos base : bases) {
            if (felled >= maxTrees) break;
            if (seen.contains(base) || !AutoArea.firstTouch(ctx, base)) continue;
            Tree tree = scan(level, base);
            if (tree == null) continue;
            seen.addAll(tree.logs);
            if (!allowed(ctx, level, tree, caster, city)) continue;
            if (!mana.pay(tree.points)) {
                noMana = true;
                break;
            }
            List<ItemStack> treeDrops = new ArrayList<>();
            int fx = 0;
            for (BlockPos p : tree.leaves) {
                BlockState s = level.getBlockState(p);
                if (!s.is(BlockTags.LEAVES)) continue;
                treeDrops.addAll(Block.getDrops(s, level, p, null, caster, axe));
                level.setBlock(p, s.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
            }
            for (BlockPos p : tree.logs) {
                BlockState s = level.getBlockState(p);
                if (!s.is(BlockTags.LOGS)) continue;
                treeDrops.addAll(Block.getDrops(s, level, p, null, caster, axe));
                if (fx++ < 5) level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, p, Block.getId(s));
                level.removeBlock(p, false);
                logs++;
            }
            // Replantar em cada base (árvores 2×2 voltam com 4 mudas, se houver)
            for (BlockPos b : tree.bases) {
                if (!level.isEmptyBlock(b)) continue;
                BlockState plant = takeSapling(ctx, level, b, preferred, treeDrops, at);
                if (plant == null) {
                    noSapling = true;
                    continue;
                }
                level.setBlock(b, plant, Block.UPDATE_ALL);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, b.getX() + 0.5, b.getY() + 0.3, b.getZ() + 0.5, 5, 0.3, 0.2, 0.3, 0);
                replanted++;
            }
            drops.addAll(treeDrops);
            felled++;
        }
        mana.commit();
        out.give(drops);
        if (felled > 0) {
            Vec3 c = Vec3.atBottomCenterOf(center).add(0, 1.05, 0);
            AutoFx.ring(level, c, r + 0.3, ElementFx.dust(ctx.element(), 1.0F), 14 + r * 5);
            level.sendParticles(ParticleTypes.COMPOSTER, c.x, c.y + 0.4, c.z, 10 + logs, r * 0.4, 0.6, r * 0.4, 0);
            AutoFx.sound(level, c, SoundEvents.WOOD_BREAK, 1.0F, 0.7F);
            AutoFx.sound(level, c, SoundEvents.AZALEA_LEAVES_BREAK, 1.0F, 0.9F);
            if (replanted > 0) AutoFx.sound(level, c, SoundEvents.CROP_PLANTED, 0.8F, 1.0F);
            out.trail(ctx, c);
            if (noSapling) AutoFx.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.auto_glyphs.no_sapling"));
        } else if (noMana) {
            AutoFx.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.auto_glyphs.no_work_mana").withStyle(ChatFormatting.RED));
        }
    }

    // =====================================================================================
    //  Achar árvores
    // =====================================================================================

    /** Bases de tronco na área (o tronco atingido também desce até a base dele), da mais perto à mais longe. */
    static List<BlockPos> findBases(ServerLevel level, BlockPos center, int r) {
        List<BlockPos> out = new ArrayList<>();
        if (level.isLoaded(center) && level.getBlockState(center).is(BlockTags.LOGS)) {
            BlockPos p = center;
            for (int i = 0; i < HEIGHT && level.getBlockState(p.below()).is(BlockTags.LOGS); i++) p = p.below();
            if (isBase(level, p)) out.add(p.immutable());
        }
        for (BlockPos p : AutoArea.disk(center, r, 3, 4)) {
            if (!level.isLoaded(p)) continue;
            if (level.getBlockState(p).is(BlockTags.LOGS) && isBase(level, p) && !out.contains(p)) out.add(p);
        }
        out.sort(Comparator.comparingDouble(p -> p.distSqr(center)));
        return out;
    }

    private static boolean isBase(ServerLevel level, BlockPos log) {
        BlockState below = level.getBlockState(log.below());
        return !below.is(BlockTags.LOGS) && below.is(BlockTags.DIRT);
    }

    /** Troncos ligados (26 vizinhos) + folhas naturais; null = não parece uma árvore (ou é grande demais). */
    @Nullable
    private static Tree scan(ServerLevel level, BlockPos base) {
        Set<BlockPos> logs = new LinkedHashSet<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        logs.add(base);
        queue.add(base);
        while (!queue.isEmpty()) {
            BlockPos p = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) { // nunca desce: a base é o ponto mais baixo
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        BlockPos n = p.offset(dx, dy, dz);
                        if (Math.abs(n.getX() - base.getX()) > SPREAD || Math.abs(n.getZ() - base.getZ()) > SPREAD
                                || n.getY() - base.getY() > HEIGHT || logs.contains(n) || !level.isLoaded(n)) continue;
                        if (!level.getBlockState(n).is(BlockTags.LOGS)) continue;
                        logs.add(n);
                        if (logs.size() > MAX_LOGS) return null;
                        queue.add(n);
                    }
                }
            }
        }
        // Folhas naturais: a partir dos troncos, espalhando por folhas (até 6 passos)
        Set<BlockPos> leaves = new LinkedHashSet<>();
        Map<BlockPos, Integer> dist = new HashMap<>();
        ArrayDeque<BlockPos> lq = new ArrayDeque<>();
        for (BlockPos l : logs) {
            for (Direction d : Direction.values()) {
                BlockPos n = l.relative(d);
                if (!dist.containsKey(n) && naturalLeaf(level, n)) {
                    dist.put(n, 1);
                    leaves.add(n);
                    lq.add(n);
                }
            }
        }
        if (leaves.size() < MIN_NATURAL_LEAVES) return null;
        while (!lq.isEmpty() && leaves.size() < MAX_LEAVES) {
            BlockPos p = lq.poll();
            int dd = dist.get(p);
            if (dd >= 6) continue;
            for (Direction d : Direction.values()) {
                BlockPos n = p.relative(d);
                if (dist.containsKey(n) || logs.contains(n) || !naturalLeaf(level, n)) continue;
                dist.put(n, dd + 1);
                leaves.add(n);
                lq.add(n);
            }
        }
        List<BlockPos> bases = new ArrayList<>();
        float points = 0;
        for (BlockPos l : logs) {
            if (l.getY() == base.getY() && isBase(level, l)) bases.add(l);
            points += WorkMana.blockPoints(level.getBlockState(l).getDestroySpeed(level, l));
        }
        points += leaves.size() * WorkMana.blockPoints(0.2F);
        return new Tree(logs, leaves, bases, points);
    }

    private static boolean naturalLeaf(ServerLevel level, BlockPos p) {
        if (!level.isLoaded(p)) return false;
        BlockState s = level.getBlockState(p);
        if (!s.is(BlockTags.LEAVES)) return false;
        return !s.hasProperty(LeavesBlock.PERSISTENT) || !s.getValue(LeavesBlock.PERSISTENT);
    }

    /** Todos os troncos podem ser quebrados por este conjurador? (proteções, prédios, permissão) */
    private static boolean allowed(SpellContext ctx, ServerLevel level, Tree tree, @Nullable Entity caster, boolean city) {
        for (BlockPos p : tree.logs) {
            BlockState s = level.getBlockState(p);
            if (FarmTools.untouchable(level, p, s, caster, city) || !AutoArea.mayBreak(ctx, p, s)) return false;
        }
        return true;
    }

    // =====================================================================================
    //  Mudas
    // =====================================================================================
    static boolean isSapling(ItemStack s) {
        return !s.isEmpty() && s.getItem() instanceof BlockItem bi && bi.getBlock().defaultBlockState().is(BlockTags.SAPLINGS);
    }

    /** Escolhe e GASTA uma muda (filtro → drops da árvore → baús/inventário). null = nenhuma serve aqui. */
    @Nullable
    private static BlockState takeSapling(SpellContext ctx, ServerLevel level, BlockPos spot, @Nullable Item preferred,
                                          List<ItemStack> drops, Vec3 at) {
        boolean creative = AutoArea.creative(ctx);
        List<IItemHandler> sources = AutoArea.sources(ctx, at);
        if (preferred != null) {
            BlockState plant = plantOf(preferred, level, spot);
            if (plant == null) return null;
            if (takeFromDrops(drops, preferred) || !AutoArea.take(sources, st -> st.is(preferred), creative).isEmpty()) return plant;
            return null;
        }
        for (ItemStack d : drops) {
            if (!isSapling(d)) continue;
            BlockState plant = plantOf(d.getItem(), level, spot);
            if (plant == null) continue;
            d.shrink(1);
            return plant;
        }
        ItemStack got = AutoArea.take(sources, st -> isSapling(st) && plantOf(st.getItem(), level, spot) != null, creative);
        return got.isEmpty() ? null : plantOf(got.getItem(), level, spot);
    }

    private static boolean takeFromDrops(List<ItemStack> drops, Item item) {
        for (ItemStack d : drops) {
            if (d.is(item) && !d.isEmpty()) {
                d.shrink(1);
                return true;
            }
        }
        return false;
    }

    @Nullable
    private static BlockState plantOf(Item item, ServerLevel level, BlockPos spot) {
        if (!(item instanceof BlockItem bi)) return null;
        BlockState plant = bi.getBlock().defaultBlockState();
        if (!plant.is(BlockTags.SAPLINGS) || !plant.canSurvive(level, spot)) return null;
        return plant;
    }

    @Override
    public boolean hasWork(ServerLevel level, BlockPos center, @Nullable BlockPos pedestal, SpellStats stats) {
        int r = AutoArea.radius(stats, BASE_RADIUS);
        for (BlockPos p : AutoArea.disk(center, r, 3, 4)) {
            if (level.isLoaded(p) && level.getBlockState(p).is(BlockTags.LOGS) && isBase(level, p)) return true;
        }
        return false;
    }
}
