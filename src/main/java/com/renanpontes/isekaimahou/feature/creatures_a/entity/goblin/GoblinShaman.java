package com.renanpontes.isekaimahou.feature.creatures_a.entity.goblin;

import java.util.EnumSet;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Xamã goblin: fica atrás do bando conjurando magias de verdade (glifos do mod) com
 * {@link SpellCaster#castAsMob}: Projétil + Incendiar (se o glifo existir) ou Projétil + Dano Arcano.
 * Também cura goblins feridos por perto.
 */
public class GoblinShaman extends AbstractGoblin {
    private static final ResourceLocation PROJECTILE = IsekaiMahou.id("projectile");
    private static final ResourceLocation IGNITE = IsekaiMahou.id("ignite");
    private static final ResourceLocation ARCANE_DAMAGE = IsekaiMahou.id("arcane_damage");
    private static final SpellRecipe FIRE_BOLT = new SpellRecipe(List.of(PROJECTILE, IGNITE), "", -1, "");
    private static final SpellRecipe ARCANE_BOLT = new SpellRecipe(List.of(PROJECTILE, ARCANE_DAMAGE), "", -1, "");

    public GoblinShaman(EntityType<? extends GoblinShaman> type, Level level) {
        super(type, level);
        this.xpReward = 8;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return baseAttributes()
                .add(Attributes.MAX_HEALTH, 16.0)
                .add(Attributes.MOVEMENT_SPEED, 0.28)
                .add(Attributes.ATTACK_DAMAGE, 2.0);
    }

    @Override
    protected void addCombatGoals() {
        goalSelector.addGoal(2, new CastGoal(this));
    }

    @Override
    protected void equipGear(RandomSource random, DifficultyInstance difficulty) {
        // O cajado faz parte do modelo; nada na mão.
    }

    @Override
    public boolean canSteal() {
        return false;
    }

    /** Elemento usado na próxima magia (Fogo só se o glifo Incendiar estiver registrado). */
    private Element pickElement() {
        boolean hasIgnite = GlyphRegistry.get(IGNITE) != null;
        return hasIgnite && random.nextFloat() < 0.6F ? Element.FIRE : Element.ARCANE;
    }

    /** Vira o corpo e a cabeça para o alvo (as formas leem a rotação de quem conjura). */
    private void aimAt(LivingEntity target) {
        double dx = target.getX() - getX();
        double dy = target.getY(0.5) - getEyeY();
        double dz = target.getZ() - getZ();
        double horiz = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0F;
        float pitch = (float) (-(Mth.atan2(dy + horiz * 0.04, horiz) * (180.0 / Math.PI)));
        setYRot(yaw);
        setXRot(pitch);
        yHeadRot = yaw;
        yBodyRot = yaw;
    }

    void castAt(LivingEntity target, Element element) {
        aimAt(target);
        triggerAnim("action", "cast");
        float power = 0.55F + 0.1F * level().getDifficulty().getId();
        SpellContext ctx = SpellCaster.castAsMob(this, element, element == Element.FIRE ? FIRE_BOLT : ARCANE_BOLT, power);
        if (ctx == null && element == Element.FIRE) ctx = SpellCaster.castAsMob(this, Element.ARCANE, ARCANE_BOLT, power);
        if (ctx == null) {
            // Glifos indisponíveis (ou magia cancelada): bola de fogo pequena "à moda antiga".
            Vec3 dir = new Vec3(target.getX() - getX(), target.getY(0.5) - getY(0.5), target.getZ() - getZ()).normalize();
            SmallFireball ball = new SmallFireball(level(), this, dir);
            ball.setPos(getX(), getEyeY() - 0.1, getZ());
            level().addFreshEntity(ball);
            playSound(SoundEvents.BLAZE_SHOOT, 1.0F, 1.4F);
        }
    }

    boolean tryHealAlly() {
        AbstractGoblin hurt = level().getEntitiesOfClass(AbstractGoblin.class, getBoundingBox().inflate(12.0),
                        g -> g != this && g.isAlive() && g.getHealth() < g.getMaxHealth() * 0.6F && hasLineOfSight(g))
                .stream().findFirst().orElse(null);
        if (hurt == null || !(level() instanceof ServerLevel sl)) return false;
        triggerAnim("action", "cast");
        hurt.heal(6.0F);
        ElementFx.line(sl, getEyePosition(), hurt.position().add(0, hurt.getBbHeight() * 0.6, 0), Element.LIGHT);
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, hurt.getX(), hurt.getY() + hurt.getBbHeight() * 0.5, hurt.getZ(),
                12, 0.4, 0.5, 0.4, 0.0);
        playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 1.3F);
        return true;
    }

    @Override
    protected float voiceBase() {
        return 1.35F;
    }

    /** Mantém distância do alvo, carrega a magia (partículas + som) e dispara; cura aliados de tempos em tempos. */
    static class CastGoal extends Goal {
        private static final int WINDUP = 24;
        private final GoblinShaman shaman;
        private int castCooldown = 20;
        private int healCooldown = 60;
        private int windup;
        private Element element = Element.ARCANE;
        private int repath;

        CastGoal(GoblinShaman shaman) {
            this.shaman = shaman;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            LivingEntity t = shaman.getTarget();
            return t != null && t.isAlive() && shaman.distanceToSqr(t) < 24 * 24;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void stop() {
            windup = 0;
            shaman.setCasting(false);
            shaman.setAggressive(false);
        }

        @Override
        public void start() {
            shaman.setAggressive(true);
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            LivingEntity t = shaman.getTarget();
            if (t == null) return;
            shaman.getLookControl().setLookAt(t, 30.0F, 30.0F);
            double d = shaman.distanceTo(t);
            boolean sees = shaman.getSensing().hasLineOfSight(t);

            // Posição: longe o bastante para não apanhar, perto o bastante para acertar.
            if (++repath % 10 == 0) {
                if (d < 6.0) {
                    Vec3 away = DefaultRandomPos.getPosAway(shaman, 8, 4, t.position());
                    if (away != null) shaman.getNavigation().moveTo(away.x, away.y, away.z, 1.25);
                } else if (d > 14.0 || !sees) {
                    shaman.getNavigation().moveTo(t, 1.0);
                } else if (windup > 0) {
                    shaman.getNavigation().stop();
                }
            }

            if (healCooldown > 0) healCooldown--;
            else if (windup == 0 && shaman.tryHealAlly()) healCooldown = 200;

            if (windup > 0) {
                windup--;
                if (shaman.level() instanceof ServerLevel sl && windup % 2 == 0) ElementFx.channeling(sl, shaman, element);
                if (windup == 0) {
                    shaman.setCasting(false);
                    if (sees) shaman.castAt(t, element);
                    castCooldown = 45 + shaman.getRandom().nextInt(35);
                }
            } else if (--castCooldown <= 0 && sees && d < 20.0) {
                element = shaman.pickElement();
                windup = WINDUP;
                shaman.setCasting(true);
                shaman.playSound(SoundEvents.EVOKER_PREPARE_ATTACK, 1.0F, 1.5F);
            }
        }
    }
}
