package com.renanpontes.isekaimahou.feature.cities.entity;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.cities.CityCrimes;
import com.renanpontes.isekaimahou.feature.cities.CityLines;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Cidadão: anda pela cidade, conversa, conta rumores, foge de monstros e de noite fica em casa.
 * 10 variantes (pele, cabelo, roupa); a tabela {@link #LOOKS} bate com a ordem do tools/gen/cities.py.
 */
public class Citizen extends CityNpc {
    /** Por variante: feminina?, barba, bolsa, boné. */
    private static final boolean[][] LOOKS = {
            {false, false, false, true},
            {true, false, false, false},
            {false, true, false, false},
            {true, false, true, false},
            {false, false, true, false},
            {true, false, false, true},
            {false, true, false, true},
            {true, false, false, false},
            {false, false, false, false},
            {true, false, true, false},
    };

    public Citizen(EntityType<? extends Citizen> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 24.0);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new PanicGoal(this, 1.35));
        goalSelector.addGoal(2, new AvoidEntityGoal<>(this, Mob.class, Citizen::isMonster, 10.0F, 0.8, 1.2, e -> true));
        goalSelector.addGoal(3, new AvoidEntityGoal<>(this, Player.class, this::isFeared, 8.0F, 0.75, 1.1, e -> true));
    }

    static boolean isMonster(LivingEntity e) {
        // Invocações/companheiros de jogadores (com dono) não assustam ninguém.
        return e instanceof Enemy && !(e instanceof CityNpc)
                && !(e instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwnerUUID() != null);
    }

    /** Civis evitam quem está Procurado NO REINO DELES e quem está em guerra com a cidade deles (fogem). */
    boolean isFeared(LivingEntity e) {
        return e instanceof Player p && !p.isCreative() && (CityCrimes.isWanted(p, kingdomFaction()) || isWarEnemy(p));
    }

    @Override
    public String kindId() {
        return "citizen";
    }

    @Override
    protected int variantCount() {
        return LOOKS.length;
    }

    @Override
    public boolean female() {
        return LOOKS[Math.floorMod(variant(), LOOKS.length)][0];
    }

    @Override
    public Set<String> visibleBones() {
        boolean[] l = LOOKS[Math.floorMod(variant(), LOOKS.length)];
        Set<String> s = new HashSet<>();
        if (l[0]) {
            s.add("hair_long");
            s.add("skirt");
        }
        if (l[1]) s.add("beard");
        if (l[2]) s.add("satchel");
        if (l[3]) s.add("cap");
        return s;
    }

    @Override
    protected int wanderRadius() {
        return 22;
    }

    @Override
    protected int currentRadius() {
        // De noite (e no cerco) os cidadãos voltam para casa.
        return level().isNight() ? 3 : wanderRadius();
    }

    @Override
    @Nullable
    protected Component ambientLine(Player near) {
        if (isWarEnemy(near)) return CityLines.pick("citizen.enemy", random, kingdomName());
        if (isFeared(near)) return CityLines.pick("citizen.wanted", random);
        if (!level().isNight() && random.nextInt(100) < 30) return CityLines.kingdomFlavor(kingdom(), random);
        return CityLines.pick(level().isNight() ? "citizen.night" : "citizen.ambient", random, kingdomName());
    }
}
