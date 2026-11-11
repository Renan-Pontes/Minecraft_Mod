package com.renanpontes.isekaimahou.feature.cities.entity;

import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.cities.CityLines;
import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Lorde/Lady da cidade: coroa, capa real e cetro. Dá notícias do reino, orienta para as missões do Reino e entrega
 * recompensas especiais quando o jogador chega a Honrado e Exaltado. Luta com o cetro se for atacado.
 */
public class CityLord extends CityNpc {
    private static final Set<String> LORD = Set.of("crown", "cape", "scepter", "beard");
    private static final Set<String> LADY = Set.of("crown", "cape", "scepter", "skirt", "hair_long");

    public CityLord(EntityType<? extends CityLord> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 60.0)
                .add(Attributes.ATTACK_DAMAGE, 6.0)
                .add(Attributes.ARMOR, 6.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.FOLLOW_RANGE, 24.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.5);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.1, true));
        targetSelector.addGoal(1, new HurtByTargetGoal(this, CityNpc.class));
        // v1.0: o Lorde defende a cidade de quem está em guerra com ela.
        targetSelector.addGoal(2, new net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal<>(this, Player.class, 10,
                true, false, e -> e instanceof Player p && isWarEnemy(p)));
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        playAction("attack");
        return super.doHurtTarget(target);
    }

    @Override
    public String kindId() {
        return "lord";
    }

    /** Lorde/Lady de Aldória (manto vermelho/azul), Sultão/Sultana de Sahra, Jarl de Nordheim. */
    @Override
    public String textureName() {
        CityKingdom k = kingdom();
        return k == CityKingdom.ALDORIA ? super.textureName() : "lord_" + k.id + "_" + variant();
    }

    @Override
    protected int variantCount() {
        return 2;
    }

    @Override
    public boolean female() {
        return variant() == 1;
    }

    @Override
    public Set<String> visibleBones() {
        return female() ? LADY : LORD;
    }

    @Override
    protected int wanderRadius() {
        return 6;
    }

    @Override
    protected int strollInterval() {
        return 300;
    }

    @Override
    @Nullable
    protected Component ambientLine(Player near) {
        if (random.nextInt(100) < 40) return CityLines.kingdomLord(kingdom(), random);
        return CityLines.pick("lord.ambient", random, kingdomName());
    }
}
