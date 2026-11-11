package com.renanpontes.isekaimahou.feature.automation.glyph;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.items.IItemHandler;

/** Glifos de lavoura: Colheita Fértil, Arar e Semear. */
public final class FarmGlyphs {

    // =====================================================================================
    //  Colheita Fértil (Arcano t1)
    // =====================================================================================
    /**
     * Colhe tudo o que está maduro na área e replanta usando uma semente da própria colheita; de vez em quando
     * faz um broto crescer um estágio. Fortuna: colheita com Fortuna III. Expandir: área maior.
     */
    public static class HarvestReplant extends AreaEffect {
        public HarvestReplant() {
            super("harvest_replant", 6);
            element(Element.ARCANE);
            tier(1);
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit) {
            ServerLevel level = ctx.level();
            int r = AutoArea.radius(stats, 4.0F);
            ItemStack tool = new ItemStack(Items.DIAMOND_HOE);
            if (stats.fortune) tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.FORTUNE), 3);
            float growChance = 0.08F * stats.power() * ctx.powerMult();
            int harvested = 0, grown = 0;
            for (BlockPos p : AutoArea.disk(center, r, 2, 2)) {
                if (harvestOne(ctx, level, p, tool)) {
                    AutoArea.firstTouch(ctx, p); // o broto replantado não ganha o "crescer" de outra área neste tick
                    harvested++;
                } else if (grow(ctx, level, p, growChance)) {
                    grown++;
                }
            }
            if (harvested + grown == 0) return;
            Vec3 c = Vec3.atBottomCenterOf(center).add(0, 1.05, 0);
            AutoFx.ring(level, c, r + 0.3, ElementFx.dust(ctx.element(), 1.0F), 14 + r * 6);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, c.x, c.y + 0.2, c.z, 6 + harvested + grown, r * 0.55, 0.3, r * 0.55, 0);
            if (harvested > 0) AutoFx.sound(level, c, SoundEvents.CROP_BREAK, 1.0F, 1.0F);
            if (grown > 0) AutoFx.sound(level, c, SoundEvents.BONE_MEAL_USE, 0.7F, 1.2F);
        }

        /** Faz um broto (não maduro) avançar 1 estágio com a chance dada. */
        private static boolean grow(SpellContext ctx, ServerLevel level, BlockPos p, float chance) {
            BlockState s = level.getBlockState(p);
            // Uma chance por broto e por tick, mesmo com várias áreas sobrepostas (Nova acertando vários alvos...)
            if (!(s.getBlock() instanceof CropBlock crop) || crop.isMaxAge(s) || !AutoArea.firstTouch(ctx, p)
                    || level.random.nextFloat() >= chance) return false;
            if (!AutoArea.mayEdit(ctx, p)) return false;
            level.setBlock(p, crop.getStateForAge(Math.min(crop.getMaxAge(), crop.getAge(s) + 1)), Block.UPDATE_CLIENTS);
            level.sendParticles(ParticleTypes.COMPOSTER, p.getX() + 0.5, p.getY() + 0.5, p.getZ() + 0.5, 3, 0.25, 0.25, 0.25, 0);
            return true;
        }

        private static boolean harvestOne(SpellContext ctx, ServerLevel level, BlockPos p, ItemStack tool) {
            BlockState s = level.getBlockState(p);
            Block b = s.getBlock();
            BlockState replant;
            if (b instanceof CropBlock crop) {
                if (!crop.isMaxAge(s)) return false;
                replant = crop.getStateForAge(0);
            } else if (b instanceof NetherWartBlock) {
                if (s.getValue(NetherWartBlock.AGE) < NetherWartBlock.MAX_AGE) return false;
                replant = s.setValue(NetherWartBlock.AGE, 0);
            } else if (b instanceof CocoaBlock) {
                if (s.getValue(CocoaBlock.AGE) < CocoaBlock.MAX_AGE) return false;
                replant = s.setValue(CocoaBlock.AGE, 0);
            } else if (b instanceof SweetBerryBushBlock) {
                int age = s.getValue(SweetBerryBushBlock.AGE);
                if (age < 2 || !AutoArea.mayBreak(ctx, p, s)) return false;
                // Igual a colher com a mão: 1-2 frutas (+1 se madura); o arbusto fica
                Block.popResource(level, p, new ItemStack(Items.SWEET_BERRIES, 1 + level.random.nextInt(2) + (age == 3 ? 1 : 0)));
                level.setBlock(p, s.setValue(SweetBerryBushBlock.AGE, 1), Block.UPDATE_CLIENTS);
                level.playSound(null, p, SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES, SoundSource.BLOCKS, 1.0F, 0.8F + level.random.nextFloat() * 0.4F);
                return true;
            } else if ((s.is(Blocks.MELON) || s.is(Blocks.PUMPKIN)) && hasAttachedStem(level, p)) {
                replant = Blocks.AIR.defaultBlockState();
            } else if ((s.is(Blocks.SUGAR_CANE) || s.is(Blocks.CACTUS) || s.is(Blocks.BAMBOO)) && level.getBlockState(p.below()).is(b)
                    && !level.getBlockState(p.below(2)).is(b)) {
                // Corta acima da base (a base continua crescendo); o que estiver em cima cai junto
                replant = Blocks.AIR.defaultBlockState();
            } else {
                return false;
            }
            if (!AutoArea.mayBreak(ctx, p, s)) return false;
            List<ItemStack> drops = new ArrayList<>(Block.getDrops(s, level, p, null, ctx.caster(), tool));
            if (!replant.isAir()) {
                // Replantar "gasta" uma semente da colheita; sem semente, o pé não volta
                boolean seed = false;
                for (ItemStack d : drops) {
                    if (d.getItem() instanceof BlockItem bi && bi.getBlock() == b) {
                        d.shrink(1);
                        seed = true;
                        break;
                    }
                }
                if (!seed) replant = Blocks.AIR.defaultBlockState();
            }
            level.levelEvent(LevelEvent.PARTICLES_DESTROY_BLOCK, p, Block.getId(s));
            level.setBlock(p, replant, Block.UPDATE_ALL);
            for (ItemStack d : drops) if (!d.isEmpty()) Block.popResource(level, p, d);
            return true;
        }

        private static boolean hasAttachedStem(ServerLevel level, BlockPos p) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(p.relative(d)).getBlock() instanceof AttachedStemBlock) return true;
            }
            return false;
        }
    }

    // =====================================================================================
    //  Arar (Terra t1)
    // =====================================================================================
    /** Transforma grama/terra da área em terra arada (como uma enxada) e umedece a terra arada que já existe. */
    public static class Till extends AreaEffect {
        public Till() {
            super("till", 4);
            element(Element.EARTH);
            tier(1);
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit) {
            ServerLevel level = ctx.level();
            int r = AutoArea.radius(stats, 3.0F);
            int tilled = 0, wet = 0;
            for (BlockPos p : AutoArea.disk(center, r, 2, 1)) {
                BlockState s = level.getBlockState(p);
                if (s.is(Blocks.FARMLAND)) {
                    if (s.getValue(FarmBlock.MOISTURE) < FarmBlock.MAX_MOISTURE && AutoArea.mayEdit(ctx, p)) {
                        level.setBlock(p, s.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE), Block.UPDATE_CLIENTS);
                        wet++;
                    }
                    continue;
                }
                BlockState target = tilled(s);
                if (target == null || !AutoArea.mayEdit(ctx, p)) continue;
                BlockPos above = p.above();
                BlockState up = level.getBlockState(above);
                if (!up.isAir()) {
                    // Mato baixo (grama, samambaia, neve fina) sai do caminho; qualquer outra coisa impede
                    if (!up.canBeReplaced() || !up.getFluidState().isEmpty() || !AutoArea.mayBreak(ctx, above, up)) continue;
                    level.destroyBlock(above, true, ctx.caster());
                }
                if (s.is(Blocks.ROOTED_DIRT)) Block.popResource(level, above, new ItemStack(Items.HANGING_ROOTS));
                level.setBlock(p, target.is(Blocks.FARMLAND) ? target.setValue(FarmBlock.MOISTURE, FarmBlock.MAX_MOISTURE) : target,
                        Block.UPDATE_ALL_IMMEDIATE);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, s), p.getX() + 0.5, p.getY() + 1.0, p.getZ() + 0.5,
                        4, 0.3, 0.05, 0.3, 0);
                tilled++;
            }
            if (tilled + wet == 0) return;
            Vec3 c = Vec3.atBottomCenterOf(center).add(0, 1.02, 0);
            AutoFx.ring(level, c, r + 0.3, ElementFx.dust(Element.EARTH, 1.1F), 12 + r * 6);
            if (wet > 0) level.sendParticles(ParticleTypes.SPLASH, c.x, c.y, c.z, 6 + wet, r * 0.5, 0.1, r * 0.5, 0);
            AutoFx.sound(level, c, SoundEvents.HOE_TILL, 1.0F, 0.9F);
            if (tilled > 4) AutoFx.sound(level, c, SoundEvents.ROOTED_DIRT_BREAK, 0.8F, 0.8F);
        }

        /** O que a enxada faz com este bloco (null = nada). */
        @Nullable
        private static BlockState tilled(BlockState s) {
            if (s.is(Blocks.GRASS_BLOCK) || s.is(Blocks.DIRT) || s.is(Blocks.DIRT_PATH)) return Blocks.FARMLAND.defaultBlockState();
            if (s.is(Blocks.COARSE_DIRT) || s.is(Blocks.ROOTED_DIRT)) return Blocks.DIRT.defaultBlockState();
            return null;
        }
    }

    // =====================================================================================
    //  Semear (Arcano t1)
    // =====================================================================================
    /**
     * Planta sementes na terra arada vazia da área (verruga do Nether na areia das almas), tirando do seu
     * inventário — ou, num pedestal, dos baús encostados nele. Repete a semente dos vizinhos para manter as fileiras.
     */
    public static class Sow extends AreaEffect {
        public Sow() {
            super("sow", 5);
            element(Element.ARCANE);
            tier(1);
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit) {
            ServerLevel level = ctx.level();
            int r = AutoArea.radius(stats, 4.0F);
            List<IItemHandler> sources = AutoArea.sources(ctx, at);
            boolean creative = AutoArea.creative(ctx);
            int planted = 0;
            boolean emptySpot = false;
            for (BlockPos soil : AutoArea.disk(center, r, 2, 1)) {
                BlockState s = level.getBlockState(soil);
                if (!s.is(Blocks.FARMLAND) && !s.is(Blocks.SOUL_SAND)) continue;
                BlockPos spot = soil.above();
                if (!level.isEmptyBlock(spot) || !AutoArea.mayEdit(ctx, spot)) continue;
                emptySpot = true;
                Item preferred = neighborSeed(level, spot);
                ItemStack seed = preferred == null ? ItemStack.EMPTY
                        : AutoArea.take(sources, st -> st.is(preferred) && plantable(st, level, spot), creative);
                if (seed.isEmpty()) seed = AutoArea.take(sources, st -> plantable(st, level, spot), creative);
                if (seed.isEmpty()) continue;
                BlockState plant = ((BlockItem) seed.getItem()).getBlock().defaultBlockState();
                level.setBlock(spot, plant, Block.UPDATE_ALL);
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, spot.getX() + 0.5, spot.getY() + 0.2, spot.getZ() + 0.5, 2, 0.2, 0.1, 0.2, 0);
                planted++;
            }
            Vec3 c = Vec3.atBottomCenterOf(center).add(0, 1.02, 0);
            if (planted > 0) {
                AutoFx.ring(level, c, r + 0.3, ElementFx.dust(ctx.element(), 0.9F), 12 + r * 6);
                AutoFx.sound(level, c, SoundEvents.CROP_PLANTED, 1.0F, 1.0F);
            } else if (emptySpot && AutoArea.firstThisTick(ctx, "sow_empty")) {
                AutoFx.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.automation.no_seeds"));
            }
        }

        /** Item que vira uma planta de lavoura (tag minecraft:crops ou verruga) e que sobrevive neste lugar. */
        private static boolean plantable(ItemStack stack, ServerLevel level, BlockPos spot) {
            if (!(stack.getItem() instanceof BlockItem bi)) return false;
            BlockState plant = bi.getBlock().defaultBlockState();
            if (!plant.is(BlockTags.CROPS) && !(bi.getBlock() instanceof NetherWartBlock)) return false;
            return plant.canSurvive(level, spot);
        }

        /** Semente da planta vizinha (para continuar a fileira igual). */
        @Nullable
        private static Item neighborSeed(ServerLevel level, BlockPos spot) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                BlockState n = level.getBlockState(spot.relative(d));
                if (n.is(BlockTags.CROPS) || n.getBlock() instanceof NetherWartBlock) {
                    ItemStack clone = n.getBlock().getCloneItemStack(level, spot.relative(d), n);
                    if (!clone.isEmpty() && clone.getItem() instanceof BlockItem) return clone.getItem();
                }
            }
            return null;
        }
    }

    private FarmGlyphs() {}
}
