package com.renanpontes.isekaimahou.feature.hueco_mundo.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;
import com.renanpontes.isekaimahou.feature.hueco_mundo.entity.HmAllies;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Lâmina da Maré de Areia (Imperatriz das Areias): espada larga de dente de tubarão. Botão direito lança uma Onda
 * Areia-Azul em linha (10 blocos): dano, empurrão e cegueira curta. Recarga de 6 s, gasta durabilidade.
 */
public class TideBladeItem extends SwordItem {
    private static final int COOLDOWN = 120;

    public TideBladeItem(Tier tier, Properties props) {
        super(tier, props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);
        ServerLevel sl = sp.serverLevel();
        Vec3 dir = sp.getLookAngle().multiply(1, 0, 1);
        if (dir.lengthSqr() < 1.0E-4) dir = new Vec3(0, 0, 1);
        dir = dir.normalize();
        Vec3 from = sp.position();
        for (int i = 1; i <= 10; i++) {
            Vec3 at = from.add(dir.scale(i));
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, HmRegistries.HOLLOW_SAND.get().defaultBlockState()),
                    at.x, at.y + 0.4, at.z, 6, 0.4, 0.3, 0.4, 0.1);
            sl.sendParticles(HmFx.dust(0x3FA7C9, 1.3F), at.x, at.y + 0.8, at.z, 2, 0.3, 0.3, 0.3, 0);
        }
        AABB box = new AABB(from, from.add(dir.scale(10))).inflate(1.5, 1.5, 1.5);
        float dmg = 6F + (float) sp.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE) * 0.5F;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != sp && e.isAlive() && !HmAllies.isAlly(sp, e))) {
            Vec3 rel = e.position().subtract(from);
            double along = rel.dot(dir);
            if (along < 0 || along > 10.5 || rel.subtract(dir.scale(along)).horizontalDistanceSqr() > 2.0 * 2.0) continue;
            if (e instanceof Player other && !sp.canHarmPlayer(other)) continue;
            if (e.hurt(sl.damageSources().playerAttack(sp), dmg)) {
                e.setDeltaMovement(e.getDeltaMovement().add(dir.x * 0.9, 0.35, dir.z * 0.9));
                e.hurtMarked = true;
                e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.BLINDNESS, 30, 0), sp);
            }
        }
        sl.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_SPLASH_HIGH_SPEED, SoundSource.PLAYERS, 1.0F, 0.7F);
        stack.hurtAndBreak(2, sp, hand == InteractionHand.MAIN_HAND ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        sp.getCooldowns().addCooldown(this, COOLDOWN);
        sp.swing(hand, true);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        lines.add(Component.translatable("item.isekai_mahou.tide_sand_blade.desc").withStyle(ChatFormatting.AQUA));
    }
}
