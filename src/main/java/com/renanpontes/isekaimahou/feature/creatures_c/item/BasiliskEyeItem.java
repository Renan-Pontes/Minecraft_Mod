package com.renanpontes.isekaimahou.feature.creatures_c.item;

import com.renanpontes.isekaimahou.feature.creatures_c.CcFx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Olho de Basilisco: ao usar, o olhar petrificante atinge as criaturas num cone à frente (até 14 blocos),
 * deixando-as Atordoadas e muito lentas por 4 s. Gasta o olho e tem recarga.
 */
public class BasiliskEyeItem extends CcLoreItem {
    private static final double RANGE = 14.0;
    /** cos(~25°): largura do cone do olhar. */
    private static final double CONE = 0.9;

    public BasiliskEyeItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel sl) {
            Vec3 eye = player.getEyePosition();
            Vec3 look = player.getViewVector(1.0F);
            boolean pvp = sl.getServer().isPvpAllowed();
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(RANGE),
                    // e.isAlliedTo(player) cobre os bichos domados do próprio jogador (lobos, Lobo Atroz, gatos...)
                    e -> e != player && e.isAlive() && !e.isSpectator() && !player.isAlliedTo(e) && !e.isAlliedTo(player))) {
                if (e instanceof Player && !pvp) continue;
                Vec3 to = e.getBoundingBox().getCenter().subtract(eye);
                double d = to.length();
                if (d > RANGE || d < 0.01 || to.scale(1.0 / d).dot(look) < CONE) continue;
                if (!player.hasLineOfSight(e)) continue;
                CcFx.petrify(sl, e, 80);
            }
            // Feixe dourado do olhar
            for (int i = 1; i <= 14; i++) {
                Vec3 p = eye.add(look.scale(i));
                double spread = 0.08 * i;
                sl.sendParticles(CcFx.GAZE_DUST, p.x, p.y, p.z, 3, spread, spread, spread, 0.0);
            }
            sl.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ELDER_GUARDIAN_CURSE, SoundSource.PLAYERS, 0.5F, 1.7F);
            player.getCooldowns().addCooldown(this, 100);
            player.awardStat(Stats.ITEM_USED.get(this));
            stack.consume(1, player);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }
}
