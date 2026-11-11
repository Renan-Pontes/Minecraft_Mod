package com.renanpontes.isekaimahou.feature.cities.entity;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.cities.CityCrimes;
import com.renanpontes.isekaimahou.feature.cities.CityLines;
import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Guarda da cidade: elmo com pluma, lança, escudo e capa do reino (azul de Aldória, laranja-sol de Sahra, azul-gelo de
 * Nordheim). Patrulha perto do posto, ataca monstros dentro da cidade e jogadores Procurados NO REINO DELE ou com o
 * reino dele em Hostil/Inimigo — o guarda de Sahra não liga para crime cometido em Aldória. Aceita fiança no diálogo.
 * <p>v1.0: também ataca quem está em guerra com a cidade dele ({@code Allegiance}) e ABORDA quem ele viu saqueando
 * ({@link com.renanpontes.isekaimahou.feature.cities.CityConfront}): vai até o jogador, para na frente dele e reclama.
 */
public class CityGuard extends CityNpc {
    private static final Set<String> BONES = Set.of("helmet", "plume", "spear", "shield", "pauldron_r", "pauldron_l", "cape");
    private static final int VARIANTS = 4;
    private int regenCooldown;
    /** Jogador que este guarda está abordando (v1.0, só no servidor; não é salvo — a abordagem vive na memória). */
    @Nullable private UUID confronting;

    public CityGuard(EntityType<? extends CityGuard> type, Level level) {
        super(type, level);
        xpReward = 0;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 40.0)
                .add(Attributes.ATTACK_DAMAGE, 7.0)
                .add(Attributes.ARMOR, 10.0)
                .add(Attributes.ARMOR_TOUGHNESS, 2.0)
                .add(Attributes.MOVEMENT_SPEED, 0.3)
                .add(Attributes.FOLLOW_RANGE, 32.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.4)
                .add(Attributes.ENTITY_INTERACTION_RANGE, 3.5);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2, true));
        goalSelector.addGoal(3, new ConfrontGoal());
        targetSelector.addGoal(1, new HurtByTargetGoal(this, CityNpc.class).setAlertOthers());
        targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isOutlaw));
        targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Mob.class, 5, true, false, this::isCityThreat));
    }

    /** Jogador que os guardas DESTE reino atacam à vista: Procurado aqui, ou Hostil/Inimigo com o reino. */
    public static boolean isOutlaw(LivingEntity e, String kingdomFaction) {
        if (!(e instanceof Player p) || p.isCreative() || p.isSpectator()) return false;
        return CityCrimes.isWanted(p, kingdomFaction) || Reputation.isHostile(p, kingdomFaction);
    }

    /** Fora da lei para ESTE guarda (inclui quem está em guerra com a cidade dele). */
    public boolean isOutlaw(LivingEntity e) {
        return isOutlaw(e, kingdomFaction()) || e instanceof Player p && isWarEnemy(p);
    }

    // =====================================================================================
    //  Abordagem (v1.0)
    // =====================================================================================
    /** Passa a abordar este jogador (o CityConfront chama a cada poucos ticks enquanto a abordagem durar). */
    public void setConfronting(Player p) {
        confronting = p.getUUID();
    }

    public void clearConfronting() {
        confronting = null;
        getNavigation().stop();
    }

    @Nullable
    public UUID confronting() {
        return confronting;
    }

    @Nullable
    private Player confrontPlayer() {
        if (confronting == null) return null;
        Player p = level().getPlayerByUUID(confronting);
        return p != null && p.isAlive() && !p.isSpectator() && p.level() == level() ? p : null;
    }

    /** Vai até o jogador abordado e para na frente dele, olhando-o (sem atacar). Tem prioridade sobre os passeios. */
    private final class ConfrontGoal extends Goal {
        private int repath;

        ConfrontGoal() {
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return getTarget() == null && confrontPlayer() != null;
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void tick() {
            Player p = confrontPlayer();
            if (p == null) return;
            getLookControl().setLookAt(p, 30.0F, 30.0F);
            if (distanceToSqr(p) > 2.6 * 2.6) {
                if (--repath <= 0) {
                    repath = 10;
                    getNavigation().moveTo(p, 1.15);
                }
            } else {
                getNavigation().stop();
            }
        }
    }

    @Override
    public String textureName() {
        CityKingdom k = kingdom();
        return k == CityKingdom.ALDORIA ? super.textureName() : "guard_" + k.id + "_" + variant();
    }

    /** Monstro dentro da cidade (ou perto do posto do guarda). */
    private boolean isCityThreat(LivingEntity e) {
        if (!(e instanceof Enemy) || e instanceof CityNpc || !(level() instanceof ServerLevel sl)) return false;
        if (e instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame()) return false;
        // Invocações e companheiros de jogadores (OwnableEntity com dono) não são ameaça — só os monstros do cerco e os selvagens.
        if (!com.renanpontes.isekaimahou.feature.cities.CitySiege.isSiegeMob(e) && e instanceof net.minecraft.world.entity.OwnableEntity o && o.getOwnerUUID() != null) return false;
        if (com.renanpontes.isekaimahou.feature.cities.CityPlaces.cityAt(sl, e.blockPosition()) != null) return true;
        return home() != null && e.blockPosition().closerThan(home(), 14);
    }

    @Override
    public String kindId() {
        return "guard";
    }

    @Override
    protected int variantCount() {
        return VARIANTS;
    }

    @Override
    public boolean female() {
        return variant() == 2;
    }

    @Override
    public Set<String> visibleBones() {
        boolean north = kingdom() == CityKingdom.NORDHEIM;
        if (!female() && !north) return BONES;
        Set<String> s = new HashSet<>(BONES);
        if (female()) s.add("hair_long");
        else s.add("beard");   // os guardas de Nordheim usam barba trançada
        return s;
    }

    @Override
    protected int wanderRadius() {
        return 10;
    }

    @Override
    protected int strollInterval() {
        return 160;
    }

    @Override
    public boolean isAlliedTo(Entity other) {
        if (other instanceof Player p) return !isOutlaw(p) && p != getLastHurtByMob();
        if (other instanceof net.minecraft.world.entity.animal.IronGolem) return true;
        return super.isAlliedTo(other);
    }

    @Override
    public boolean doHurtTarget(Entity target) {
        playAction("attack");
        boolean hit = super.doHurtTarget(target);
        if (hit && target instanceof LivingEntity le) {
            // Estocada de lança: empurra um pouco para trás.
            le.knockback(0.45, getX() - le.getX(), getZ() - le.getZ());
        }
        return hit;
    }

    @Override
    public void setTarget(@Nullable LivingEntity target) {
        // v1.0: ninguém manda o guarda atacar quem não é fora da lei (ex.: o furto do Ladino chama quem viu) — ele
        // ABORDA o jogador (CityConfront) e só ataca se a conversa virar briga.
        if (!level().isClientSide && target instanceof net.minecraft.server.level.ServerPlayer sp && !isOutlaw(sp)
                && sp != getLastHurtByMob() && com.renanpontes.isekaimahou.feature.cities.CityConfront.onGuardProvoked(this, sp)) {
            return;
        }
        LivingEntity old = getTarget();
        super.setTarget(target);
        if (target != null && confronting != null && target.getUUID().equals(confronting)) confronting = null;   // a conversa virou briga
        if (!level().isClientSide && target != null && old != target && random.nextFloat() < 0.6F) {
            if (target instanceof Player) say(CityLines.pick("guard.wanted", random));
            else say(CityLines.pick("guard.battle", random));
            level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 0.8F, 0.9F);
        }
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) return;
        // Fora de combate o guarda se recupera aos poucos (senão as patrulhas morreriam ao longo dos dias).
        if (getTarget() == null && getHealth() < getMaxHealth() && --regenCooldown <= 0) {
            regenCooldown = 60;
            heal(1.0F);
        }
        // Alvo que deixou de ser fora da lei (pagou a fiança) é esquecido.
        if (getTarget() instanceof Player p && !isOutlaw(p) && p != getLastHurtByMob()) setTarget(null);
    }

    @Override
    @Nullable
    protected Component ambientLine(Player near) {
        if (confronting != null) return null;   // no meio de uma bronca não puxa outro assunto
        if (isWarEnemy(near)) return CityLines.pick("guard.enemy", random, kingdomName());
        if (isOutlaw(near)) return CityLines.pick("guard.wanted", random);
        if (random.nextInt(100) < 35) return CityLines.kingdomFlavor(kingdom(), random);
        return CityLines.pick(level().isNight() ? "guard.night" : "guard.ambient", random, kingdomName());
    }
}
