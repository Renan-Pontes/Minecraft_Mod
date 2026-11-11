package com.renanpontes.isekaimahou.feature.settlements.entity;

import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.WarService;
import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Guarda da cidade (cidade própria) ou da Guarda Pessoal do Senhor (cidade conquistada): patrulha, defende o Senhor,
 * o grupo dele e os cidadãos ({@code SettlementService.onPlayerHurt}), ataca jogadores de cidades INIMIGAS, monstros
 * dentro do território e quem desobedece o toque de recolher. Castelo/Esfinge = guardas mais fortes; o Reino dá
 * armadura extra (muralha de pedra).
 */
public class GuardNpc extends SettlementNpc {
    private static final ResourceLocation POWER_HP = IsekaiMahou.id("settlement_guard_hp");
    private static final ResourceLocation POWER_DMG = IsekaiMahou.id("settlement_guard_dmg");
    private static final ResourceLocation POWER_ARMOR = IsekaiMahou.id("settlement_guard_armor");
    private static final Set<String> KINGDOM = Set.of("helmet", "plume", "spear", "shield", "cape", "pauldron_r", "pauldron_l");
    private static final Set<String> DESERT = Set.of("helmet", "spear", "shield", "cape", "hood");
    private static final Set<String> NORDIC = Set.of("helmet", "horns", "axe", "shield", "cape", "beard");

    private int powerCheck;

    public GuardNpc(EntityType<? extends GuardNpc> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.3);
    }

    @Override
    public Settlement.Role role() {
        return Settlement.Role.GUARD;
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
    protected int wanderRadius() {
        return CityLayout.WALL;
    }

    @Override
    protected RawAnimation idleAnimation() {
        return guardIdle();
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, SettlementNpc.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isHostilePlayer));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false, this::isThreat));
    }

    /** Inimigo de guerra ou desobedecendo o toque de recolher. */
    private boolean isHostilePlayer(LivingEntity e) {
        if (isEnemy(e)) return true;
        Settlement s = settlement();
        return s != null && e instanceof ServerPlayer sp && level() instanceof ServerLevel sl && WarService.isTrespasser(sl, s, sp);
    }

    /** Monstro dentro do território (ou mirando alguém protegido pela cidade). */
    private boolean isThreat(LivingEntity e) {
        if (!(e instanceof Enemy) || e instanceof SettlementNpc) return false;
        if (home == null) return false;
        return e.distanceToSqr(home.getX(), e.getY(), home.getZ()) < (double) CityLayout.TERRITORY * CityLayout.TERRITORY;
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        triggerAnim("action", "attack");
        return super.doHurtTarget(target);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (!level().isClientSide && --powerCheck <= 0) {
            powerCheck = 400;
            refreshPower(settlement());
        }
    }

    /** Fortalece pelos prédios (Castelo/Esfinge), nível da cidade e estilo (Reino = mais armadura). */
    public void refreshPower(@Nullable Settlement s) {
        int lvl = s == null ? 1 : s.level;
        boolean power = s != null && s.guardPower();
        set(Attributes.MAX_HEALTH, POWER_HP, 4.0 * (lvl - 1) + (power ? 20.0 : 0.0));
        set(Attributes.ATTACK_DAMAGE, POWER_DMG, (lvl - 1) * 0.75 + (power ? 4.0 : 0.0));
        set(Attributes.ARMOR, POWER_ARMOR, style().guardArmor + (power ? 4.0 : 0.0));
    }

    private void set(net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr, ResourceLocation id, double v) {
        AttributeInstance inst = getAttribute(attr);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (cur != null && cur.amount() == v) return;
        inst.removeModifier(id);
        if (v != 0) inst.addPermanentModifier(new AttributeModifier(id, v, AttributeModifier.Operation.ADD_VALUE));
    }

    @Override
    @Nullable
    protected net.minecraft.network.chat.Component ambientLine(Player near) {
        Settlement s = settlement();
        if (s != null && level().isNight() && s.gates != Settlement.GATES_OPEN && random.nextInt(2) == 0) return line("guard.curfew", 2);
        return super.ambientLine(near);
    }
}
