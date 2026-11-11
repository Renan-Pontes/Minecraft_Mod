package com.renanpontes.isekaimahou.feature.armory.item;

import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.armory.WeaponKind;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Arco Longo do Patrulheiro: um arco de verdade (flechas do inventário, encantamentos de arco, Infinidade...), com a
 * puxada mais longa ({@link WeaponKind#drawTicks}) e as flechas mais rápidas ({@link WeaponKind#projectileSpeed}) —
 * e, por isso, mais fortes. O bônus de dano do tier e a passiva do tier IV entram no acerto ({@code ArmoryArrows}),
 * pela arma gravada na flecha, então valem também para a técnica Flecha.
 */
public class ArmoryBowItem extends BowItem implements ArmoryWeapon {
    private final WeaponKind kind;
    private final int tier;

    public ArmoryBowItem(WeaponKind kind, int tier, Properties props) {
        super(props.stacksTo(1).durability(WeaponKind.DURABILITY[tier - 1]).attributes(ArmoryWeapon.attributes(kind, tier)));
        this.kind = kind;
        this.tier = tier;
    }

    @Override
    public WeaponKind kind() {
        return kind;
    }

    @Override
    public int tier() {
        return tier;
    }

    /** Ticks da puxada completa (o cliente usa no modelo e no zoom). */
    public int drawTicks() {
        return kind.drawTicks(tier);
    }

    /** Força do disparo (0..1) para os ticks puxados: a mesma curva do arco comum, esticada na puxada longa. */
    public float powerFor(int ticks) {
        float f = ticks / (float) drawTicks();
        f = (f * f + f * 2.0F) / 3.0F;
        return Math.min(1.0F, f);
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        if (!(entity instanceof Player player)) return;
        ItemStack ammo = player.getProjectile(stack);
        if (ammo.isEmpty()) return;
        int used = EventHooks.onArrowLoose(stack, level, player, getUseDuration(stack, entity) - timeLeft, true);
        if (used < 0) return;
        float power = powerFor(used);
        if (power < 0.1F) return;
        List<ItemStack> shots = draw(stack, ammo, player);
        if (level instanceof ServerLevel server && !shots.isEmpty()) {
            shoot(server, player, player.getUsedItemHand(), stack, shots, power * 3.0F, 1.0F, power >= 1.0F, null);
            if (power >= 1.0F) {
                // Corda pesada: o estalo e um sopro de vento na mão marcam o disparo completo
                Vec3 look = player.getLookAngle();
                Vec3 p = player.getEyePosition().add(look.scale(1.2)).add(0, -0.2, 0);
                server.sendParticles(ParticleTypes.CLOUD, p.x, p.y, p.z, 3 + tier, 0.08, 0.08, 0.08, 0.04);
                server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_SHOOT,
                        SoundSource.PLAYERS, 0.5F, 1.5F);
            }
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                1.0F, 1.0F / (level.getRandom().nextFloat() * 0.4F + 1.2F) + power * 0.5F - 0.15F);
        player.awardStat(Stats.ITEM_USED.get(this));
    }

    @Override
    protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy,
                                   float angle, @Nullable LivingEntity target) {
        // Mais rápida e um pouco mais precisa a cada tier
        super.shootProjectile(shooter, projectile, index, velocity * kind.projectileSpeed(tier),
                inaccuracy * (1.0F - 0.1F * tier), angle, target);
    }

    @Override
    public int getEnchantmentValue(ItemStack stack) {
        return 6 + tier * 2;
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return repair.is(CoreRegistries.MANA_CRYSTAL.get()) || super.isValidRepairItem(toRepair, repair);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        ArmoryWeapon.appendTooltip(this, getDescriptionId() + ".desc", tooltip);
    }
}
