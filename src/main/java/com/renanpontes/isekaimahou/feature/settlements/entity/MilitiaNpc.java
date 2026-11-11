package com.renanpontes.isekaimahou.feature.settlements.entity;

import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Miliciano: guerreiro treinado pela cidade para a guerra (precisa de afinidade máxima). Ordem do Senhor:
 * <b>Seguir</b> (vai junto, teleporta se ficar para trás, ataca o que fere o Senhor/grupo e o que o Senhor ataca)
 * ou <b>Defender</b> (fica no coração). Sempre ataca jogadores inimigos da cidade.
 */
public class MilitiaNpc extends SettlementNpc {
    private static final Set<String> KINGDOM = Set.of("helmet", "sword", "shield", "pauldron_r", "pauldron_l");
    private static final Set<String> DESERT = Set.of("hood", "sword", "shield");
    private static final Set<String> NORDIC = Set.of("helmet", "horns", "axe", "shield", "beard");

    private int orderCheck;

    public MilitiaNpc(EntityType<? extends MilitiaNpc> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 36.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 5.0)
                .add(Attributes.MOVEMENT_SPEED, 0.32)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.2);
    }

    @Override
    public Settlement.Role role() {
        return Settlement.Role.MILITIA;
    }

    @Override
    public Set<String> visibleBones() {
        return switch (style()) {
            case DESERT -> DESERT;
            case NORDIC -> NORDIC;
            default -> KINGDOM;
        };
    }

    @Override
    protected RawAnimation idleAnimation() {
        return guardIdle();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.25, true));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, SettlementNpc.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isEnemy));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("action", "attack");
        return super.doHurtTarget(target);
    }

    @Nullable
    private ServerPlayer owner() {
        Settlement s = settlement();
        if (s == null || s.owner == null || !(level() instanceof ServerLevel sl)) return null;
        ServerPlayer p = sl.getServer().getPlayerList().getPlayer(s.owner);
        return p != null && p.level() == level() && p.isAlive() && !p.isSpectator() ? p : null;
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || --orderCheck > 0) return;
        orderCheck = 10;
        Settlement s = settlement();
        if (s == null) return;
        ServerPlayer owner = owner();
        if (s.militiaOrder == Settlement.ORDER_FOLLOW && owner != null) {
            restrictTo(owner.blockPosition(), 16);
            helpOwner(owner);
            double d = distanceToSqr(owner);
            if (d > 32 * 32 && owner.onGround()) teleportNear(owner);
            else if (d > 6 * 6 && getTarget() == null) getNavigation().moveTo(owner, 1.15);
        } else if (home != null && !home.equals(getRestrictCenter())) {
            restrictTo(home, CityLayout.CELL);
        }
    }

    /** Quem feriu o Senhor (ou foi atacado por ele) vira alvo — menos o próprio grupo. */
    private void helpOwner(ServerPlayer owner) {
        if (getTarget() != null && getTarget().isAlive()) return;
        LivingEntity hurtBy = owner.getLastHurtByMob();
        if (valid(owner, hurtBy) && owner.tickCount - owner.getLastHurtByMobTimestamp() < 200) {
            setTarget(hurtBy);
            return;
        }
        LivingEntity hit = owner.getLastHurtMob();
        if (valid(owner, hit) && owner.tickCount - owner.getLastHurtMobTimestamp() < 200) setTarget(hit);
    }

    private boolean valid(ServerPlayer owner, @Nullable LivingEntity e) {
        if (e == null || !e.isAlive() || e == this || e instanceof SettlementNpc n && n.cityId().equals(cityId)) return false;
        if (e instanceof Player p && (p == owner || Party.sameParty(p, owner))) return false;
        return distanceToSqr(e) < 24 * 24;
    }

    private void teleportNear(ServerPlayer owner) {
        BlockPos base = owner.blockPosition();
        for (int i = 0; i < 10; i++) {
            BlockPos p = base.offset(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
            BlockState below = level().getBlockState(p.below());
            if (below.isSolid() && level().getBlockState(p).isAir() && level().getBlockState(p.above()).isAir()) {
                moveTo(p.getX() + 0.5, p.getY(), p.getZ() + 0.5, getYRot(), getXRot());
                getNavigation().stop();
                if (level() instanceof ServerLevel sl) {
                    sl.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 1, getZ(), 8, 0.3, 0.5, 0.3, 0.02);
                }
                return;
            }
        }
    }

    /** O Senhor alterna a ordem clicando num miliciano. */
    @Override
    protected InteractionResult interact(ServerPlayer sp) {
        Settlement s = settlement();
        if (s != null && sp.getUUID().equals(s.owner)) {
            s.militiaOrder = s.militiaOrder == Settlement.ORDER_FOLLOW ? Settlement.ORDER_DEFEND : Settlement.ORDER_FOLLOW;
            sp.displayClientMessage(SettlementsLang.tr("militia.order." + s.militiaOrder), true);
            triggerAnim("action", "bow");
            playSound(SoundEvents.ARMOR_EQUIP_IRON.value(), 1.0F, 1.0F);
            return InteractionResult.CONSUME;
        }
        return super.interact(sp);
    }
}
