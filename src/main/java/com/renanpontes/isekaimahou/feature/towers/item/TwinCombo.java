package com.renanpontes.isekaimahou.feature.towers.item;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.feature.towers.TwProgress;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Torrente Estelar: 16 golpes alternando as mãos, um a cada 3 ticks (2,4 s). Cada golpe acerta o alvo travado (ou o
 * inimigo mais perto à frente) com 42% do dano de ataque; o 16º golpe dá 110% e empurra. Pequenos avanços mantêm o
 * jogador colado no alvo. Só acerta inimigos (nunca jogadores nem bichos do próprio jogador).
 */
public final class TwinCombo {
    private static final int HITS = 16;
    private static final int INTERVAL = 3;
    private static final Map<UUID, State> ACTIVE = new HashMap<>();
    private static final DustParticleOptions DARK = new DustParticleOptions(new Vector3f(0.42F, 0.23F, 1.0F), 1.3F);
    private static final DustParticleOptions LIGHT = new DustParticleOptions(new Vector3f(0.5F, 0.91F, 1.0F), 1.3F);

    private static final class State {
        int hit;
        int timer;
        @Nullable
        UUID target;
        int landed;
    }

    public static boolean isActive(Player p) {
        return ACTIVE.containsKey(p.getUUID());
    }

    public static void start(ServerPlayer sp) {
        ACTIVE.put(sp.getUUID(), new State());
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.tower_twin_blade.starburst")
                .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), true);
        sp.serverLevel().playSound(null, sp.blockPosition(), SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 1.0F, 1.6F);
        TwProgress.flag(sp, TwProgress.FLAG_TWIN);
    }

    public static void stop(Player p) {
        ACTIVE.remove(p.getUUID());
    }

    public static void tick(ServerPlayer sp) {
        State s = ACTIVE.get(sp.getUUID());
        if (s == null) return;
        if (!sp.isAlive() || !TwinBladeItem.isPaired(sp)) {
            ACTIVE.remove(sp.getUUID());
            return;
        }
        if (--s.timer > 0) return;
        s.timer = INTERVAL;
        strike(sp, s);
        if (++s.hit >= HITS) {
            ACTIVE.remove(sp.getUUID());
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.tower_twin_blade.finish", s.landed)
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    private static void strike(ServerPlayer sp, State s) {
        ServerLevel sl = sp.serverLevel();
        boolean main = s.hit % 2 == 0;
        boolean last = s.hit == HITS - 1;
        InteractionHand hand = main ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        sp.swing(hand, true);
        DustParticleOptions dust = main ? DARK : LIGHT;
        sl.playSound(null, sp.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.PLAYERS, 0.8F, 0.9F + s.hit * 0.05F);

        LivingEntity target = resolve(sp, s);
        if (target == null) {
            Vec3 p = sp.getEyePosition().add(sp.getLookAngle().scale(1.8));
            sl.sendParticles(dust, p.x, p.y - 0.3, p.z, 10, 0.5, 0.3, 0.5, 0.0);
            return;
        }
        float base = (float) sp.getAttributeValue(Attributes.ATTACK_DAMAGE);
        float dmg = base * (last ? 1.1F : 0.42F);
        target.invulnerableTime = 0;
        if (target.hurt(sp.damageSources().playerAttack(sp), dmg)) s.landed++;
        if (last) {
            target.knockback(1.2, sp.getX() - target.getX(), sp.getZ() - target.getZ());
            sl.sendParticles(ParticleTypes.FLASH, target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 1, 0, 0, 0, 0);
            sl.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.2F, 0.7F);
            sl.playSound(null, target.blockPosition(), SoundEvents.TRIDENT_THUNDER.value(), SoundSource.PLAYERS, 0.5F, 1.6F);
        }
        double y = target.getY() + target.getBbHeight() * 0.55;
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), y, target.getZ(), 1, 0.2, 0.2, 0.2, 0.0);
        sl.sendParticles(dust, target.getX(), y, target.getZ(), 14, 0.5, 0.5, 0.5, 0.0);
        sl.sendParticles(ParticleTypes.CRIT, target.getX(), y, target.getZ(), 6, 0.3, 0.3, 0.3, 0.3);
        // Mantém o jogador colado no alvo
        if (sp.distanceTo(target) > 2.6) {
            Vec3 dir = target.position().subtract(sp.position()).multiply(1, 0, 1);
            if (dir.lengthSqr() > 1.0E-4) {
                dir = dir.normalize();
                sp.setDeltaMovement(dir.x * 0.5, sp.getDeltaMovement().y, dir.z * 0.5);
                sp.hurtMarked = true;
            }
        }
        if (s.hit % 4 == 3) {
            ItemStack blade = sp.getItemInHand(hand);
            blade.hurtAndBreak(1, sp, main ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND);
        }
    }

    @Nullable
    private static LivingEntity resolve(ServerPlayer sp, State s) {
        if (s.target != null) {
            Entity e = sp.serverLevel().getEntity(s.target);
            if (e instanceof LivingEntity le && valid(sp, le) && sp.distanceTo(le) < 6.5) return le;
        }
        Vec3 look = sp.getLookAngle().multiply(1, 0, 1);
        List<LivingEntity> list = sp.serverLevel().getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(5.5),
                e -> valid(sp, e));
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : list) {
            Vec3 to = e.position().subtract(sp.position()).multiply(1, 0, 1);
            if (to.lengthSqr() > 1.0E-4 && look.lengthSqr() > 1.0E-4 && to.normalize().dot(look.normalize()) < 0.2) continue;
            double d = sp.distanceToSqr(e);
            if (d < bd) {
                bd = d;
                best = e;
            }
        }
        s.target = best == null ? null : best.getUUID();
        return best;
    }

    private static boolean valid(ServerPlayer sp, LivingEntity e) {
        if (e == sp || !e.isAlive() || e instanceof Player || e instanceof ArmorStand) return false;
        if (e instanceof OwnableEntity own && sp.getUUID().equals(own.getOwnerUUID())) return false;
        if (e.isAlliedTo(sp)) return false;
        return e instanceof Enemy || (e instanceof Mob m && m.getTarget() == sp);
    }

    private TwinCombo() {}
}
