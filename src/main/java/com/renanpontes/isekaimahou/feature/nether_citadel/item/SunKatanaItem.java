package com.renanpontes.isekaimahou.feature.nether_citadel.item;

import java.util.List;

import com.renanpontes.isekaimahou.feature.nether_citadel.NcDemons;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Katana do Sol (Demon Slayer): leve e rápida; +6 de dano em demônios e mortos-vivos ({@code NcEvents}).
 * Usar: <b>Investida Flamejante</b> — corre ~7 blocos na direção do olhar cortando todos no caminho (fogo, +dano em
 * demônios). Recarga 7 s. Para em paredes (não atravessa blocos).
 */
public class SunKatanaItem extends SwordItem {
    public static final int COOLDOWN = 140;
    public static final float UNHOLY_BONUS = 6.0F;
    private static final double RANGE = 7.0;

    public SunKatanaItem(Tier tier, Properties props) {
        super(tier, props);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        if (flat.lengthSqr() < 1.0E-4) return InteractionResultHolder.fail(stack);
        flat = flat.normalize();
        player.swing(hand);
        if (level instanceof ServerLevel sl) {
            dash(sl, player, flat);
            stack.hurtAndBreak(1, player, LivingEntity.getSlotForHand(hand));
        }
        player.getCooldowns().addCooldown(this, COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static void dash(ServerLevel sl, Player player, Vec3 dir) {
        Vec3 start = player.position();
        Vec3 probe = start.add(0, 0.9, 0);
        HitResult hit = sl.clip(new ClipContext(probe, probe.add(dir.scale(RANGE)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        double len = hit.getType() == HitResult.Type.MISS ? RANGE : Math.max(0.0, hit.getLocation().distanceTo(probe) - 0.7);
        // Não termina dentro de um bloco baixo (degrau, laje): recua até caber.
        while (len > 0.0 && !sl.noCollision(player, player.getBoundingBox().move(dir.scale(len)))) len -= 0.5;
        len = Math.max(0.0, len);
        Vec3 end = start.add(dir.scale(len));
        AABB swept = new AABB(start, end.add(0, player.getBbHeight(), 0)).inflate(1.3);
        for (Entity e : sl.getEntities(player, swept)) {
            if (!NcDemons.isEnemyOf(player, e)) continue;
            if (distToSegment(e.getBoundingBox().getCenter(), start.add(0, 1, 0), end.add(0, 1, 0)) > 1.8 + e.getBbWidth() * 0.5) continue;
            // o +6 contra demônios/mortos-vivos é somado pelo NcEvents (como em todo golpe da katana): aqui só o dano base
            if (e.hurt(player.damageSources().playerAttack(player), 8.0F)) e.igniteForSeconds(5.0F);
        }
        // Rastro de chamas (laranja → dourado) ao longo do caminho.
        int steps = Math.max(2, (int) (len * 3));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = start.add(dir.scale(len * i / steps)).add(0, 1.0, 0);
            sl.sendParticles(ParticleTypes.FLAME, p.x, p.y, p.z, 3, 0.25, 0.35, 0.25, 0.02);
            if (i % 3 == 0) sl.sendParticles(ParticleTypes.LAVA, p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0.0);
        }
        player.teleportTo(end.x, end.y, end.z);
        player.fallDistance = 0.0F;
        sl.playSound(null, player.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 1.0F, 1.4F);
        sl.playSound(null, player.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 1.0F, 1.2F);
    }

    private static double distToSegment(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double t = ab.lengthSqr() < 1.0E-6 ? 0 : Math.max(0, Math.min(1, p.subtract(a).dot(ab) / ab.lengthSqr()));
        return p.distanceTo(a.add(ab.scale(t)));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> lines, TooltipFlag flag) {
        NcLoreItem.lore(stack, lines);
        lines.add(Component.translatable("item.isekai_mahou.citadel_sun_katana.passive").withStyle(ChatFormatting.YELLOW));
    }
}
