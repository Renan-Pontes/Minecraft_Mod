package com.renanpontes.isekaimahou.feature.automation.glyph;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.feature.automation.ManaNet;
import com.renanpontes.isekaimahou.feature.automation.Pedestals;
import com.renanpontes.isekaimahou.feature.automation.block.ArcaneCollectorBlockEntity;
import com.renanpontes.isekaimahou.feature.automation.block.RunicPedestalBlockEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

/** Glifos de itens: Recolher e Forja Arcana. */
public final class ItemGlyphs {

    // =====================================================================================
    //  Recolher (Arcano t1)
    // =====================================================================================
    /**
     * Puxa os itens soltos (e orbes de XP) da área para o conjurador. Num pedestal, os itens vão direto para o
     * Coletor Arcano mais próximo (até 16 blocos); sem coletor, para um baú encostado no pedestal; sem baú, para o
     * topo do pedestal.
     */
    public static class CollectItems extends AreaEffect {
        public CollectItems() {
            super("collect_items", 5);
            element(Element.ARCANE);
            tier(1);
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit) {
            ServerLevel level = ctx.level();
            Entity caster = ctx.caster();
            if (caster == null) return;
            int r = AutoArea.radius(stats, 6.0F);
            AABB box = new AABB(center).inflate(r);
            List<ItemEntity> items = level.getEntitiesOfClass(ItemEntity.class, box, e -> e.isAlive() && !e.getItem().isEmpty());
            List<ExperienceOrb> orbs = level.getEntitiesOfClass(ExperienceOrb.class, box, Entity::isAlive);
            if (items.isEmpty() && orbs.isEmpty()) return;

            RunicPedestalBlockEntity pedestal = Pedestals.of(ctx, at);
            ArcaneCollectorBlockEntity collector = pedestal != null ? ManaNet.nearestCollector(level, pedestal.getBlockPos()) : null;
            Vec3 dest = pedestal != null ? pedestal.castPoint() : caster.position().add(0, 0.3, 0);
            if (collector != null) dest = Vec3.atCenterOf(collector.getBlockPos()).add(0, 0.6, 0);
            ElementFx.burst(level, at, ctx.element(), 8, 0.4);

            int moved = 0;
            for (ItemEntity ie : items) {
                Vec3 from = ie.position().add(0, 0.2, 0);
                ItemStack stack = ie.getItem();
                ItemStack rest = stack;
                if (collector != null) rest = collector.insert(stack.copy());
                else if (pedestal != null) rest = insertAdjacent(level, pedestal.getBlockPos(), stack.copy());
                if (rest.isEmpty()) {
                    ie.discard();
                } else {
                    if (rest.getCount() != stack.getCount()) ie.setItem(rest);
                    // O que sobrou voa até o destino (o jogador pega sozinho)
                    ie.teleportTo(dest.x, dest.y, dest.z);
                    ie.setDeltaMovement(Vec3.ZERO);
                    if (pedestal == null) ie.setNoPickUpDelay();
                }
                if (moved++ < 6) AutoFx.line(level, from, dest, ElementFx.dust(ctx.element(), 0.6F), 0.5);
                level.sendParticles(ParticleTypes.REVERSE_PORTAL, from.x, from.y, from.z, 4, 0.1, 0.1, 0.1, 0.02);
            }
            Vec3 orbDest = pedestal != null ? pedestal.castPoint() : caster.position().add(0, 0.3, 0);
            for (ExperienceOrb orb : orbs) {
                orb.teleportTo(orbDest.x, orbDest.y, orbDest.z);
                orb.setDeltaMovement(Vec3.ZERO);
            }
            AutoFx.sound(level, dest, SoundEvents.ITEM_PICKUP, 0.5F, 0.7F);
            AutoFx.sound(level, at, SoundEvents.ENDERMAN_TELEPORT, 0.3F, 1.6F);
        }

        /** Tenta guardar nos inventários encostados no pedestal. */
        private static ItemStack insertAdjacent(ServerLevel level, BlockPos pedestal, ItemStack stack) {
            ItemStack rest = stack;
            for (Direction d : Direction.values()) {
                IItemHandler h = level.getCapability(Capabilities.ItemHandler.BLOCK, pedestal.relative(d), d.getOpposite());
                if (h == null) continue;
                rest = ItemHandlerHelper.insertItemStacked(h, rest, false);
                if (rest.isEmpty()) break;
            }
            return rest;
        }
    }

    // =====================================================================================
    //  Forja Arcana (Fogo t2)
    // =====================================================================================
    /**
     * Cozinha os itens soltos na área com as receitas da fornalha (minério bruto → lingote, carne crua → assada,
     * areia → vidro...), até 32 itens por conjuração (Amplificar aumenta) e solta a XP da fornalha.
     * Uma criatura atingida pega fogo.
     */
    public static class SmeltItems extends AreaEffect {
        private static final String MARK = "isekai_mahou_smelted";

        public SmeltItems() {
            super("smelt_items", 12);
            element(Element.FIRE);
            tier(2);
        }

        @Override
        protected void onTarget(SpellContext ctx, Entity target, SpellStats stats) {
            if (target instanceof LivingEntity le && target != ctx.caster() && !ctx.isAlly(le)) {
                le.igniteForSeconds(Math.max(2F, stats.duration(60) / 20F));
            }
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit) {
            ServerLevel level = ctx.level();
            int r = AutoArea.radius(stats, 4.0F);
            long now = level.getGameTime();
            int budget = Math.max(8, Math.round(32 * stats.power() * ctx.powerMult()));
            float xp = 0;
            int done = 0;
            for (ItemEntity ie : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(r),
                    e -> e.isAlive() && !e.getItem().isEmpty() && e.getPersistentData().getLong(MARK) != now)) {
                if (budget <= 0) break;
                ItemStack input = ie.getItem();
                SingleRecipeInput recipeInput = new SingleRecipeInput(input);
                Optional<RecipeHolder<SmeltingRecipe>> recipe = level.getRecipeManager().getRecipeFor(RecipeType.SMELTING, recipeInput, level);
                if (recipe.isEmpty()) continue;
                ItemStack result = recipe.get().value().assemble(recipeInput, level.registryAccess());
                if (result.isEmpty()) continue;
                int n = Math.min(input.getCount(), budget);
                budget -= n;
                done += n;
                xp += recipe.get().value().getExperience() * n;
                int total = result.getCount() * n;
                Vec3 p = ie.position();
                // Troca a pilha pelo resultado (pilhas grandes viram mais de uma entidade)
                ItemStack leftover = input.copyWithCount(input.getCount() - n);
                boolean first = true;
                while (total > 0) {
                    int c = Math.min(total, result.getMaxStackSize());
                    total -= c;
                    ItemStack out = result.copyWithCount(c);
                    if (first && leftover.isEmpty()) {
                        ie.setItem(out);
                        ie.getPersistentData().putLong(MARK, now);
                    } else {
                        ItemEntity extra = new ItemEntity(level, p.x, p.y, p.z, out);
                        extra.setDefaultPickUpDelay();
                        extra.getPersistentData().putLong(MARK, now);
                        level.addFreshEntity(extra);
                    }
                    first = false;
                }
                if (!leftover.isEmpty()) {
                    ie.setItem(leftover);
                    ie.getPersistentData().putLong(MARK, now); // o resto espera a próxima conjuração
                }
                level.sendParticles(ParticleTypes.FLAME, p.x, p.y + 0.2, p.z, 6, 0.15, 0.1, 0.15, 0.02);
                level.sendParticles(ParticleTypes.SMOKE, p.x, p.y + 0.3, p.z, 3, 0.1, 0.1, 0.1, 0.01);
            }
            if (done == 0) return;
            int orbs = Mth.floor(xp) + (level.random.nextFloat() < Mth.frac(xp) ? 1 : 0);
            if (orbs > 0) ExperienceOrb.award(level, Vec3.atCenterOf(center).add(0, 0.6, 0), orbs);
            Vec3 c = Vec3.atBottomCenterOf(center).add(0, 1.0, 0);
            AutoFx.ring(level, c, r * 0.6, ElementFx.dust(Element.FIRE, 1.0F), 12 + r * 4);
            level.sendParticles(ParticleTypes.LAVA, c.x, c.y, c.z, Math.min(10, 2 + done / 4), r * 0.3, 0.1, r * 0.3, 0);
            AutoFx.sound(level, c, SoundEvents.FIRECHARGE_USE, 0.6F, 1.2F);
            AutoFx.sound(level, c, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, 1.0F, 1.0F);
        }
    }

    private ItemGlyphs() {}
}
