package com.renanpontes.isekaimahou.feature.armory.item;

import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.feature.armory.WeaponKind;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Besta Arcana (Artífice) e Besta de Caçador: bestas de verdade (carregar segurando o botão, disparar com outro clique,
 * Carga Rápida, Disparo Múltiplo, Perfuração...), com a recarga do tier ({@link WeaponKind#chargeSeconds}) e virotes
 * mais rápidos. A recarga do vanilla é fixa (método estático), então carregar foi reescrito aqui com o tempo próprio;
 * o disparo continua sendo o do vanilla. Bônus de dano e passivas: {@code ArmoryArrows}.
 */
public class ArmoryCrossbowItem extends CrossbowItem implements ArmoryWeapon {
    private static final ChargingSounds DEFAULT_SOUNDS = new ChargingSounds(Optional.of(SoundEvents.CROSSBOW_LOADING_START),
            Optional.of(SoundEvents.CROSSBOW_LOADING_MIDDLE), Optional.of(SoundEvents.CROSSBOW_LOADING_END));

    private final WeaponKind kind;
    private final int tier;

    public ArmoryCrossbowItem(WeaponKind kind, int tier, Properties props) {
        super(props.stacksTo(1).durability(WeaponKind.DURABILITY[tier - 1])
                .component(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.EMPTY)
                .attributes(ArmoryWeapon.attributes(kind, tier)));
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

    /** Recarga mínima (ticks): mais rápida que a besta comum com Carga Rápida III (10), mas nunca uma metralhadora. */
    public static final int MIN_CHARGE_TICKS = 8;

    /**
     * Ticks até carregar (com Carga Rápida); o cliente usa no modelo "puxando". A Carga Rápida reduz na MESMA
     * proporção que na besta comum (1,25 s → 0,5 s no nível III), aplicada sobre o tempo próprio do tier — somar os
     * −0,25 s/nível direto no tempo já curto da Besta Arcana IV dava recarga de 2 ticks.
     */
    public int chargeTicks(ItemStack stack, LivingEntity shooter) {
        float vanilla = EnchantmentHelper.modifyCrossbowChargingTime(stack, shooter, VANILLA_CHARGE);
        float seconds = Math.max(0F, vanilla) * (kind.chargeSeconds(tier) / VANILLA_CHARGE);
        return Math.max(MIN_CHARGE_TICKS, Mth.floor(seconds * 20.0F));
    }

    private static final float VANILLA_CHARGE = 1.25F;

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return chargeTicks(stack, entity) + 3;
    }

    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int timeLeft) {
        int used = getUseDuration(stack, entity) - timeLeft;
        if (used < chargeTicks(stack, entity) || isCharged(stack)) return;
        List<ItemStack> loaded = draw(stack, entity.getProjectile(stack), entity);
        if (loaded.isEmpty()) return;
        stack.set(DataComponents.CHARGED_PROJECTILES, ChargedProjectiles.of(loaded));
        sounds(stack).end().ifPresent(s -> level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), s.value(),
                entity.getSoundSource(), 1.0F, 1.0F / (level.getRandom().nextFloat() * 0.5F + 1.0F) + 0.2F));
        if (level instanceof ServerLevel server && kind == WeaponKind.ARCANE_CROSSBOW) {
            // Os virotes da Besta Arcana "acendem" ao travar
            Vec3 p = entity.getEyePosition().add(entity.getLookAngle().scale(0.8)).add(0, -0.35, 0);
            server.sendParticles(ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 6, 0.15, 0.1, 0.15, 0.05);
            server.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 0.8F, 1.6F);
        }
    }

    @Override
    public void onUseTick(Level level, LivingEntity entity, ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server)) return;
        int charge = chargeTicks(stack, entity);
        int used = getUseDuration(stack, entity) - remaining;
        // Sons de carregar sem estado (o item é um só para todos os jogadores): no tick exato de 20% e 50%
        ChargingSounds sounds = sounds(stack);
        if (used == Math.max(1, Math.round(charge * 0.2F))) {
            sounds.start().ifPresent(s -> level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), s.value(),
                    SoundSource.PLAYERS, 0.5F, 1.0F));
        } else if (used == Math.round(charge * 0.5F)) {
            sounds.mid().ifPresent(s -> level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), s.value(),
                    SoundSource.PLAYERS, 0.5F, 1.0F));
        }
        if (kind == WeaponKind.ARCANE_CROSSBOW && used <= charge && used % 3 == 0 && !isCharged(stack)) {
            // Runas sendo sugadas para a besta enquanto carrega
            Vec3 hand = entity.getEyePosition().add(entity.getLookAngle().scale(0.7)).add(0, -0.4, 0);
            server.sendParticles(ParticleTypes.ENCHANT, hand.x, hand.y + 0.6, hand.z, 4, 0.5, 0.3, 0.5, 0.4);
        }
    }

    private static ChargingSounds sounds(ItemStack stack) {
        return EnchantmentHelper.pickHighestLevel(stack, EnchantmentEffectComponents.CROSSBOW_CHARGING_SOUNDS).orElse(DEFAULT_SOUNDS);
    }

    @Override
    protected void shootProjectile(LivingEntity shooter, Projectile projectile, int index, float velocity, float inaccuracy,
                                   float angle, @Nullable LivingEntity target) {
        super.shootProjectile(shooter, projectile, index, velocity * kind.projectileSpeed(tier), inaccuracy, angle, target);
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
        super.appendHoverText(stack, context, tooltip, flag); // virote carregado
        ArmoryWeapon.appendTooltip(this, getDescriptionId() + ".desc", tooltip);
    }
}
