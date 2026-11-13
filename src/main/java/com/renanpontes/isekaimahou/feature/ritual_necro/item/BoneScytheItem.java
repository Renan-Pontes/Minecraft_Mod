package com.renanpontes.isekaimahou.feature.ritual_necro.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.ritual_necro.NecroContent;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Foice de Ossos: arma do Necromante. Mortes feitas com ela guardam a alma no Filactério do inventário.
 * Golpes podem murchar o alvo; botão direito faz o "Ceifar": um giro que atinge tudo em volta.
 */
public class BoneScytheItem extends SwordItem {
    /** Material: entre ferro e diamante no dano, lenta, encantável como ouro; conserta com Bloco de Osso. */
    public static final Tier TIER = new Tier() {
        @Override public int getUses() { return 720; }
        @Override public float getSpeed() { return 6.0F; }
        @Override public float getAttackDamageBonus() { return 2.5F; }
        @Override public TagKey<Block> getIncorrectBlocksForDrops() { return BlockTags.INCORRECT_FOR_IRON_TOOL; }
        @Override public int getEnchantmentValue() { return 18; }
        @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.BONE_BLOCK); }
    };
    private static final int REAP_COOLDOWN = 70;
    private static final double REAP_RADIUS = 3.2;

    public BoneScytheItem(Properties properties) {
        super(TIER, properties);
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        super.postHurtEnemy(stack, target, attacker);
        // A lâmina de osso "ceifa" a vida: chance de Murchar por pouco tempo
        if (!attacker.level().isClientSide && attacker.getRandom().nextFloat() < 0.3F && !target.isInvertedHealAndHarm()) {
            target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), attacker);
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND) return InteractionResultHolder.pass(stack);
        if (level instanceof ServerLevel server) reap(server, player, stack);
        player.getCooldowns().addCooldown(this, REAP_COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Ceifar: golpe giratório em volta do jogador (75% do dano da arma), com rastro de almas. */
    private static void reap(ServerLevel level, Player player, ItemStack stack) {
        float damage = (float) player.getAttributeValue(Attributes.ATTACK_DAMAGE) * 0.75F;
        Vec3 c = player.position();
        AABB box = new AABB(c.x - REAP_RADIUS, c.y - 0.5, c.z - REAP_RADIUS, c.x + REAP_RADIUS, c.y + 2.5, c.z + REAP_RADIUS);
        // Só inimigos (monstros, ou quem está mirando o jogador): aldeões, animais e servos ficam de fora
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, box, e -> e != player && e.isAlive()
                && !(e instanceof Player) && !e.isAlliedTo(player)
                && !(e instanceof OwnableEntity o && player.getUUID().equals(o.getOwnerUUID()))
                && (e instanceof Enemy || (e instanceof Mob m && m.getTarget() == player))
                && e.distanceToSqr(player) <= REAP_RADIUS * REAP_RADIUS);
        int hits = 0;
        for (LivingEntity e : targets) {
            if (e.hurt(player.damageSources().playerAttack(player), damage)) {
                hits++;
                Vec3 push = e.position().subtract(c).normalize().scale(0.45);
                e.push(push.x, 0.15, push.z);
            }
        }
        // Rastro do giro: anel de varridas e chamas de alma
        for (int i = 0; i < 16; i++) {
            double a = i * Math.PI * 2 / 16;
            double x = c.x + Math.cos(a) * 2.2, z = c.z + Math.sin(a) * 2.2;
            level.sendParticles(Vfx.soulFlame(), x, c.y + 1.0, z, 1, 0.05, 0.1, 0.05, 0.01);
            if (i % 4 == 0) level.sendParticles(ParticleTypes.SWEEP_ATTACK, x, c.y + 1.0, z, 1, 0, 0, 0, 0);
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 0.6F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SKELETON_AMBIENT, SoundSource.PLAYERS, 0.5F, 1.4F);
        player.causeFoodExhaustion(0.6F);
        if (hits > 0) stack.hurtAndBreak(Math.min(3, hits), player, LivingEntity.getSlotForHand(InteractionHand.MAIN_HAND));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.isekai_mahou.bone_scythe.1").withStyle(ChatFormatting.DARK_AQUA));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.bone_scythe.2").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.isekai_mahou.bone_scythe.3").withStyle(ChatFormatting.GRAY));
    }

    /** true se o item for a Foice (atalho usado na colheita de almas). */
    public static boolean is(ItemStack stack) {
        return stack.is(NecroContent.BONE_SCYTHE.get());
    }
}
