package com.renanpontes.isekaimahou.feature.settlements.entity;

import java.util.EnumSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.Settlement;
import com.renanpontes.isekaimahou.feature.settlements.SettlementStyle;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;

/**
 * Construtor: recebe uma obra do coração da cidade, caminha até ela e martela enquanto os blocos sobem (o coração só
 * coloca blocos com o construtor PERTO — ver {@code ConstructionEngine}). Sem obra, passeia pela cidade.
 */
public class BuilderNpc extends SettlementNpc {
    private static final Set<String> KINGDOM = Set.of("hammer", "cap", "apron", "satchel");
    private static final Set<String> DESERT = Set.of("hammer", "hood", "apron", "satchel");
    private static final Set<String> NORDIC = Set.of("hammer", "cap", "apron", "beard");

    /** Obra atual (centro do canteiro) e o bloco sendo colocado. */
    @Nullable private BlockPos site;
    @Nullable private BlockPos focus;
    private int siteAge;

    public BuilderNpc(EntityType<? extends BuilderNpc> type, Level level) {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 24.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 48.0);
    }

    @Override
    public Settlement.Role role() {
        return Settlement.Role.BUILDER;
    }

    @Override
    public Set<String> visibleBones() {
        return style() == SettlementStyle.DESERT ? DESERT : style() == SettlementStyle.NORDIC ? NORDIC : KINGDOM;
    }

    @Override
    protected int wanderRadius() {
        return 40;
    }

    /** O coração dá a obra (null = sem obra). Chamado a cada passo da construção. */
    public void assign(@Nullable BlockPos site, @Nullable BlockPos focus) {
        this.site = site;
        this.focus = focus;
        siteAge = 0;
        if (site == null) setWorking(false);
    }

    @Nullable
    public BlockPos site() {
        return site;
    }

    /** Está perto o bastante da obra para trabalhar? */
    public boolean atSite(BlockPos pos, double range) {
        return distanceToSqr(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5) <= range * range;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new FleeEnemiesGoal(this));
        goalSelector.addGoal(2, new WorkGoal());
    }

    /** No canteiro ele às vezes fica entre blocos novos: não sufoca nem leva bloco na cabeça. */
    @Override
    public boolean hurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        if (source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_BLOCK)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        // Obra sem notícias do coração por 10 s (coração descarregado): larga o martelo.
        if (!level().isClientSide && site != null && ++siteAge > 200) assign(null, null);
    }

    /** Vai até a obra e martela olhando para o bloco da vez. */
    private final class WorkGoal extends Goal {
        private int repath;

        WorkGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return site != null && getTarget() == null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            setWorking(false);
        }

        @Override
        public void tick() {
            if (site == null) return;
            BlockPos look = focus != null ? focus : site;
            // distância na horizontal: os blocos altos da obra ficam acima dele, o construtor trabalha do chão
            double dx = getX() - (look.getX() + 0.5), dz = getZ() - (look.getZ() + 0.5);
            if (dx * dx + dz * dz > 25.0) {
                setWorking(false);
                if (--repath <= 0) {
                    repath = 20;
                    getNavigation().moveTo(look.getX() + 0.5, Math.min(look.getY(), site.getY()), look.getZ() + 0.5, 0.9);
                }
            } else {
                getNavigation().stop();
                setWorking(true);
            }
            getLookControl().setLookAt(look.getX() + 0.5, look.getY() + 0.5, look.getZ() + 0.5, 30.0F, 30.0F);
        }
    }
}
