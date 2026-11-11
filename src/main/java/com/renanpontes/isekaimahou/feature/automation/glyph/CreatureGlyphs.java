package com.renanpontes.isekaimahou.feature.automation.glyph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.automation.AutoFx;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.MushroomCow;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.IShearable;
import net.neoforged.neoforge.items.IItemHandler;

/** Glifos de criação: Tosquiar e Reproduzir. */
public final class CreatureGlyphs {

    // =====================================================================================
    //  Tosquiar (Arcano t1)
    // =====================================================================================
    /**
     * Tosquia como uma tesoura tudo o que dá na área (ovelhas, mobs de outros mods...) e tira os favos das colmeias
     * cheias sem irritar as abelhas. Não mexe em coguvacas (virariam vacas) nem em golens de neve.
     */
    public static class Shear extends AreaEffect {
        public Shear() {
            super("shear", 5);
            element(Element.ARCANE);
            tier(1);
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit) {
            ServerLevel level = ctx.level();
            int r = AutoArea.radius(stats, 5.0F);
            ItemStack shears = new ItemStack(Items.SHEARS);
            Player player = ctx.casterPlayer();
            int sheared = 0;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(r),
                    e -> e.isAlive() && e != ctx.caster() && !(e instanceof Player) && !(e instanceof MushroomCow) && !(e instanceof SnowGolem))) {
                if (!(e instanceof IShearable sh) || !sh.isShearable(player, shears, level, e.blockPosition())) continue;
                for (ItemStack drop : sh.onSheared(player, shears, level, e.blockPosition())) sh.spawnShearedDrop(level, e.blockPosition(), drop);
                Vec3 c = e.position().add(0, e.getBbHeight() * 0.6, 0);
                level.sendParticles(ElementFx.dust(ctx.element(), 0.9F), c.x, c.y, c.z, 8, 0.4, 0.3, 0.4, 0);
                sheared++;
            }
            int combs = 0;
            for (BlockPos p : AutoArea.disk(center, r, 2, 4)) {
                BlockState s = level.getBlockState(p);
                if (!s.is(BlockTags.BEEHIVES) || !(s.getBlock() instanceof BeehiveBlock hive)
                        || s.getValue(BeehiveBlock.HONEY_LEVEL) < BeehiveBlock.MAX_HONEY_LEVELS || !AutoArea.mayEdit(ctx, p)) continue;
                BeehiveBlock.dropHoneycomb(level, p);
                hive.resetHoneyLevel(level, s, p);
                level.sendParticles(ParticleTypes.WAX_ON, p.getX() + 0.5, p.getY() + 0.8, p.getZ() + 0.5, 8, 0.3, 0.3, 0.3, 0.02);
                combs++;
            }
            if (sheared + combs == 0) return;
            Vec3 c = Vec3.atBottomCenterOf(center).add(0, 1.0, 0);
            AutoFx.ring(level, c, r * 0.6, ElementFx.dust(ctx.element(), 0.8F), 12 + r * 4);
            if (sheared > 0) AutoFx.sound(level, c, SoundEvents.SHEEP_SHEAR, 1.0F, 1.0F);
            if (combs > 0) AutoFx.sound(level, c, SoundEvents.BEEHIVE_SHEAR, 1.0F, 1.0F);
        }
    }

    // =====================================================================================
    //  Reproduzir (Arcano t2)
    // =====================================================================================
    /**
     * Alimenta os animais adultos da área com a comida certa (do seu inventário ou, num pedestal, dos baús
     * encostados nele), sempre em pares do mesmo tipo. Para de criar um tipo quando já há 24 deles por perto
     * (evita lag em fazendas automáticas).
     */
    public static class Breed extends AreaEffect {
        public static final int CROWD_CAP = 24;
        private static final int MAX_FED = 16;

        public Breed() {
            super("breed", 10);
            element(Element.ARCANE);
            tier(2);
        }

        @Override
        protected void work(SpellContext ctx, SpellStats stats, BlockPos center, Vec3 at, @Nullable Entity hit) {
            ServerLevel level = ctx.level();
            int r = AutoArea.radius(stats, 5.0F);
            Map<EntityType<?>, List<Animal>> ready = new LinkedHashMap<>();
            for (Animal a : level.getEntitiesOfClass(Animal.class, new AABB(center).inflate(r),
                    a -> a.isAlive() && a.getAge() == 0 && a.canFallInLove() && !a.isInLove())) {
                ready.computeIfAbsent(a.getType(), k -> new ArrayList<>()).add(a);
            }
            if (ready.isEmpty()) return;
            List<IItemHandler> sources = AutoArea.sources(ctx, at);
            boolean creative = AutoArea.creative(ctx);
            Player player = ctx.casterPlayer();
            int fed = 0;
            boolean hungry = false, crowded = false;
            for (Map.Entry<EntityType<?>, List<Animal>> entry : ready.entrySet()) {
                List<Animal> list = entry.getValue();
                if (list.size() < 2) continue;
                EntityType<?> type = entry.getKey();
                int crowd = level.getEntitiesOfClass(Animal.class, new AABB(center).inflate(r + 4), a -> a.getType() == type).size();
                if (crowd >= CROWD_CAP) { crowded = true; continue; }
                int pairs = Math.min(list.size() / 2, CROWD_CAP - crowd); // cada par = 1 filhote
                for (int i = 0; i < pairs * 2 && fed < MAX_FED; i++) {
                    Animal a = list.get(i);
                    ItemStack food = AutoArea.take(sources, a::isFood, creative);
                    if (food.isEmpty()) { hungry = true; break; }
                    a.setInLove(player);
                    Vec3 c = a.position().add(0, a.getBbHeight(), 0);
                    level.sendParticles(ElementFx.dust(ctx.element(), 0.8F), c.x, c.y, c.z, 5, 0.3, 0.2, 0.3, 0);
                    fed++;
                }
            }
            Vec3 c = Vec3.atBottomCenterOf(center).add(0, 1.0, 0);
            if (fed > 0) {
                AutoFx.ring(level, c, r * 0.6, ElementFx.dust(ctx.element(), 0.9F), 12 + r * 4);
                AutoFx.sound(level, c, SoundEvents.GENERIC_EAT, 0.8F, 1.1F);
                AutoFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_CHIME, 0.6F, 1.5F);
            } else if (AutoArea.firstThisTick(ctx, "breed_msg")) {
                if (hungry) AutoFx.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.automation.no_food"));
                else if (crowded) AutoFx.actionbar(ctx.caster(), Component.translatable("message.isekai_mahou.automation.too_crowded", CROWD_CAP));
            }
        }
    }

    private CreatureGlyphs() {}
}
