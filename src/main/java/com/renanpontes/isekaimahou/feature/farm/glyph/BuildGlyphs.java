package com.renanpontes.isekaimahou.feature.farm.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.farm.FarmFx;
import com.renanpontes.isekaimahou.feature.farm.build.BuildJob;
import com.renanpontes.isekaimahou.feature.farm.build.BuildMaterial;
import com.renanpontes.isekaimahou.feature.farm.build.Placer;
import com.renanpontes.isekaimahou.feature.farm.heist.ShrineHeist;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Glifos de Construção Arcana (todos Arcanos, montáveis com qualquer forma: Toque, Projétil, Si Mesmo, Zona, Chuva...).
 * <ul>
 *   <li><b>Erguer Parede</b> — parede na frente do impacto (Expandir = mais larga, Amplificar = mais alta).</li>
 *   <li><b>Pavimentar</b> — piso quadrado no nível do impacto, só onde está vazio (pontes, plataformas).</li>
 *   <li><b>Preencher</b> — enche buracos/poços numa esfera abaixo do impacto.</li>
 *   <li><b>Transmutar Blocos</b> — troca os blocos do tipo atingido em volta pelo material (o velho volta como item).</li>
 *   <li><b>Extração Arcana</b> — recolhe o bloco atingido (e os iguais com Expandir) para o inventário; com Toque Suave
 *       sai inteiro — e arranca até o Altar dos Santuários (vira Pedestal Elemental).</li>
 * </ul>
 */
public final class BuildGlyphs {

    // =====================================================================================
    //  Erguer Parede
    // =====================================================================================
    public static class ArcaneWall extends BuildEffect {
        public ArcaneWall() {
            super("arcane_wall", 8);
            element(Element.ARCANE);
            tier(1);
        }

        @Override
        public float hintRadius() {
            return 2F;
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos hit, Direction face, Vec3 at, Worker w) {
            Block mat = material(w);
            if (mat == null) return;
            BlockPos base = face == Direction.UP ? hit.above() : hit.relative(face);
            Direction look = w.player().getDirection();
            Direction along = look.getClockWise();
            int half = radius(stats, 2F, 6);
            int height = Math.min(9, 3 + 2 * Math.max(0, stats.amplify));
            BlockState state = BuildMaterial.orient(mat, look);
            List<BuildJob.Op> ops = new ArrayList<>();
            for (int y = 0; y < height; y++) {
                for (int i = -half; i <= half; i++) {
                    ops.add(new BuildJob.Op(base.relative(along, i).above(y), state, BuildJob.Kind.PLACE, null));
                }
            }
            submit(ctx, w, ops, Vec3.atCenterOf(base));
        }
    }

    // =====================================================================================
    //  Pavimentar
    // =====================================================================================
    public static class ArcaneFloor extends BuildEffect {
        public ArcaneFloor() {
            super("arcane_floor", 8);
            element(Element.ARCANE);
            tier(1);
        }

        @Override
        public float hintRadius() {
            return 2F;
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos hit, Direction face, Vec3 at, Worker w) {
            Block mat = material(w);
            if (mat == null) return;
            BlockPos center = face.getAxis().isHorizontal() ? hit.relative(face) : hit;
            int r = radius(stats, 2F, 7);
            BlockState state = BuildMaterial.orient(mat, w.player().getDirection());
            List<BuildJob.Op> ops = new ArrayList<>();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    ops.add(new BuildJob.Op(center.offset(x, 0, z), state, BuildJob.Kind.PLACE, null));
                }
            }
            // Do centro para fora: o piso "se espalha"
            ops.sort(Comparator.comparingDouble(o -> o.pos().distSqr(center)));
            submit(ctx, w, ops, Vec3.atCenterOf(center).add(0, 0.6, 0));
        }
    }

    // =====================================================================================
    //  Preencher
    // =====================================================================================
    public static class ArcaneFill extends BuildEffect {
        public ArcaneFill() {
            super("arcane_fill", 10);
            element(Element.ARCANE);
            tier(2);
        }

        @Override
        public float hintRadius() {
            return 2.5F;
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos hit, Direction face, Vec3 at, Worker w) {
            Block mat = material(w);
            if (mat == null) return;
            BlockPos center = hit.relative(face);
            int r = radius(stats, 2.5F, 5);
            BlockState state = BuildMaterial.orient(mat, w.player().getDirection());
            ServerLevel level = ctx.level();
            List<BuildJob.Op> ops = new ArrayList<>();
            for (int y = -r; y <= 0; y++) {
                for (int x = -r; x <= r; x++) {
                    for (int z = -r; z <= r; z++) {
                        if (x * x + y * y + z * z > r * r + r) continue;
                        BlockPos p = center.offset(x, y, z);
                        if (Placer.replaceable(level, p)) ops.add(new BuildJob.Op(p, state, BuildJob.Kind.PLACE, null));
                    }
                }
            }
            ops.sort(Comparator.comparingInt((BuildJob.Op o) -> o.pos().getY()).thenComparingDouble(o -> o.pos().distSqr(center)));
            submit(ctx, w, ops, Vec3.atCenterOf(center));
        }
    }

    // =====================================================================================
    //  Transmutar Blocos
    // =====================================================================================
    public static class ArcaneTransmute extends BuildEffect {
        public ArcaneTransmute() {
            super("arcane_transmute", 12);
            element(Element.ARCANE);
            tier(2);
        }

        @Override
        public float hintRadius() {
            return 1.5F;
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos hit, Direction face, Vec3 at, Worker w) {
            Block mat = material(w);
            if (mat == null) return;
            ServerLevel level = ctx.level();
            Block target = level.getBlockState(hit).getBlock();
            if (target == mat || level.getBlockState(hit).isAir()) return;
            int r = radius(stats, 1.5F, 5);
            BlockState state = BuildMaterial.orient(mat, w.player().getDirection());
            List<BuildJob.Op> ops = new ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(hit.offset(-r, -r, -r), hit.offset(r, r, r))) {
                if (level.getBlockState(p).getBlock() == target) ops.add(new BuildJob.Op(p.immutable(), state, BuildJob.Kind.SWAP, target));
            }
            ops.sort(Comparator.comparingDouble(o -> o.pos().distSqr(hit)));
            submit(ctx, w, ops, Vec3.atCenterOf(hit));
        }
    }

    // =====================================================================================
    //  Extração Arcana
    // =====================================================================================
    public static class ArcaneExtract extends BuildEffect {
        /** Teto de blocos por acerto. */
        private static final int MAX_BLOCKS = 64;

        public ArcaneExtract() {
            super("arcane_extract", 10);
            element(Element.ARCANE);
            tier(2);
        }

        @Override
        public float hintRadius() {
            return 0F;
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos hit, Direction face, Vec3 at, Worker w) {
            ServerLevel level = ctx.level();
            BlockState first = level.getBlockState(hit);
            if (first.isAir()) return;
            // O roubo do Altar do Santuário (só com Toque Suave)
            if (ShrineHeist.isShrineAltar(first)) {
                if (!stats.silkTouch) {
                    w.player().displayClientMessage(Component.translatable("farm.isekai_mahou.heist.need_silk").withStyle(ChatFormatting.GOLD), true);
                    return;
                }
                if (!w.automated()) ShrineHeist.steal(level, hit, w.player());
                return;
            }
            Block target = first.getBlock();
            int r = Math.min(3, Math.max(0, stats.aoe));
            ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
            if (stats.fortune && !stats.silkTouch) {
                tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), 3);
            }
            List<BlockPos> list = new ArrayList<>();
            for (BlockPos p : BlockPos.betweenClosed(hit.offset(-r, -r, -r), hit.offset(r, r, r))) {
                if (level.getBlockState(p).getBlock() == target) list.add(p.immutable());
            }
            list.sort(Comparator.comparingDouble(p -> p.distSqr(hit)));
            int n = 0;
            Vec3 to = w.player().getEyePosition().add(0, -0.4, 0);
            for (BlockPos p : list) {
                if (n >= MAX_BLOCKS) break;
                if (!Placer.remove(level, w.automated() ? null : w.player(), p, stats.silkTouch, tool, w.supply()::give)) continue;
                n++;
                if (n <= 6) FarmFx.beam(level, Vec3.atCenterOf(p), to, ElementFx.dust(ctx.element(), 0.6F), 0.5);
            }
            if (n > 0) {
                FarmFx.sound(level, Vec3.atCenterOf(hit), SoundEvents.ITEM_PICKUP, 0.7F, 0.8F);
                FarmFx.sound(level, Vec3.atCenterOf(hit), SoundEvents.ENDERMAN_TELEPORT, 0.3F, 1.8F);
            }
        }
    }

    private BuildGlyphs() {}
}
