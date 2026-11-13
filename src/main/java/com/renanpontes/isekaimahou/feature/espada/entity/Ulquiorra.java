package com.renanpontes.isekaimahou.feature.espada.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.espada.EspadaLegends;
import com.renanpontes.isekaimahou.feature.espada.EspadaRegistries;
import com.renanpontes.isekaimahou.feature.espada.EspadaSounds;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * <b>Ulquiorra Cifer</b>, Cuarta Espada — o chefe principal do Las Noches. Três estágios:
 * <ol>
 *   <li><b>Selado</b> (100%–60%): espada, Sonído (some e aparece atrás de você), Bala em rajada, Cero verde,
 *       <b>Hierro</b> (pele de ferro: golpes fracos quase não passam; golpes FORTES racham e quebram por 8 s);</li>
 *   <li><b>Murciélago</b> (60%): "Enclávate" — chuva de reiatsu negro-verde, asas, voo, Luz de la Luna (lanças verdes),
 *       <b>Cero Oscuras</b> (feixe negro de borda verde, enorme e bem marcado) e regeneração instantânea;</li>
 *   <li><b>Segunda Etapa</b> (25%): a escuridão cai, o tema muda, cauda que agarra e arremessa,
 *       <b>Lanza del Relámpago</b> (lança gigante: alvo marcado que segue e trava; a lança é ARREMESSADA e explode no
 *       impacto numa grande explosão de raios verdes) e Cero Oscuras mais rápido.</li>
 * </ol>
 * <b>Dentro de Las Noches</b> (persistentData {@code isekai_mahou.las_noches} com a arena do terraço, gravada pelo
 * módulo las_noches): um Cero Oscuras ou a Lanza ali dentro derrubaria a cúpula — então, na primeira vez que ele for
 * usar um deles (ou liberar a Segunda Etapa), Ulquiorra sobe e LEVA A LUTA PARA FORA: ele e os jogadores da luta vão
 * para o terraço no topo da cúpula, a arena (e a proteção) muda para lá e a luta segue com tudo. Fora de Las Noches,
 * nada disso: luta normal. (Legado: com {@link #DOME_TAG} e sem os dados do Las Noches, a Segunda Etapa espera céu
 * aberto.) Morre virando cinza devagar, com uma última frase.
 */
public class Ulquiorra extends EspadaBoss {
    /** Tag opcional (posta por quem invoca): Segunda Etapa só a céu aberto. */
    public static final String DOME_TAG = "isekai_mahou.espada.dome_only";
    /** persistentData gravado pelo Las Noches (home_x/y/z da sala, dome_x/y/z/r da arena no terraço da cúpula). */
    public static final String LN_KEY = "isekai_mahou.las_noches";
    /** Ticks da subida antes de levar a luta para o terraço. */
    private static final int OUTSIDE_TICKS = 50;
    private static final EntityDataAccessor<Boolean> DATA_LANCE = SynchedEntityData.defineId(Ulquiorra.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_HIERRO_BROKEN = SynchedEntityData.defineId(Ulquiorra.class, EntityDataSerializers.BOOLEAN);

    public static final int FORM_SEALED = 0, FORM_MURCIELAGO = 1, FORM_SEGUNDA = 2;
    private static final int A_NONE = 0, A_SLASH = 1, A_BALA = 2, A_CERO = 3, A_LUZ = 4, A_OSCURAS = 5, A_LANZA = 6, A_TAIL = 7,
            A_OUTSIDE = 8;

    private int slashCd = 20, sonidoCd = 60, balaCd = 80, ceroCd = 140, luzCd = 60, oscurasCd = 60, lanzaCd = 40, tailCd = 80;
    private int regenCd;
    private int attack = A_NONE;
    private int attackTimer;
    private float attackYaw;
    private int balaShots;
    @Nullable
    private UUID attackTarget;
    private Vec3 beamFrom = Vec3.ZERO, beamTo = Vec3.ZERO;
    private int lanzaTelegraph = -1;
    // Hierro
    private float hierro;
    private int hierroBrokenTicks;
    private int sparkCd;
    // Cauda
    @Nullable
    private UUID grabbed;
    private int grabTicks;
    // Voo
    private float orbitAngle;
    private int pendingSegunda;
    private int pendingRegen;
    // "Lá fora": a luta já subiu para o terraço da cúpula? / a subida em andamento libera a Segunda Etapa no fim?
    private boolean onDome;
    private boolean outsideThenSegunda;
    private final List<UUID> outsidePlayers = new ArrayList<>();

    public Ulquiorra(EntityType<? extends Ulquiorra> type, Level level) {
        super(type, level);
        this.xpReward = 500;
        this.hierro = hierroMax();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(520.0, 10.0, 0.30, 12.0).add(Attributes.ATTACK_KNOCKBACK, 0.6);
    }

    @Override
    public String bossId() {
        return "ulquiorra";
    }

    @Override
    public int bossColor() {
        return EspadaFx.ULQ_GREEN;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.GREEN;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.6F, 0.25F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("slash", "sonido", "bala", "cero", "cero_oscuras", "luz_luna", "lanza_charge", "lanza_throw", "tail_grab",
                "tail_throw", "regen");
    }

    @Override
    public String musicTheme() {
        return form() >= FORM_SEGUNDA ? "ulquiorra_segunda_etapa" : "ulquiorra";
    }

    @Override
    public String textureName() {
        return switch (form()) {
            case FORM_MURCIELAGO -> "ulquiorra_murcielago";
            case FORM_SEGUNDA -> "ulquiorra_segunda";
            default -> "ulquiorra";
        };
    }

    private static final List<String> BONES = List.of("helmet", "helmet_horns", "hair_long", "wing_l", "wing_r", "horns", "tail1", "fur_l",
            "fur_r", "coat", "robe", "sword", "lance");

    @Override
    public List<String> toggledBones() {
        return BONES;
    }

    @Override
    public boolean isBoneVisible(String bone) {
        int f = form();
        return switch (bone) {
            case "helmet" -> f <= FORM_MURCIELAGO;
            case "helmet_horns", "hair_long", "robe" -> f == FORM_MURCIELAGO;
            case "wing_l", "wing_r" -> f >= FORM_MURCIELAGO;
            case "horns", "tail1", "fur_l", "fur_r" -> f == FORM_SEGUNDA;
            case "coat", "sword" -> f == FORM_SEALED;
            case "lance" -> entityData.get(DATA_LANCE);
            default -> true;
        };
    }

    public boolean hierroBroken() {
        return entityData.get(DATA_HIERRO_BROKEN);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LANCE, false);
        builder.define(DATA_HIERRO_BROKEN, false);
    }

    @Override
    protected int deathTicks() {
        return 140;
    }

    @Override
    protected int rewardXp() {
        return 700;
    }

    @Override
    protected double arenaRadius() {
        return 34.0;
    }

    @Override
    protected String[] combatQuotes() {
        return form() == FORM_SEGUNDA ? new String[] {"q_seg1", "q_seg2", "q_seg3"}
                : form() == FORM_MURCIELAGO ? new String[] {"q_mur1", "q_mur2", "q_mur3"}
                : new String[] {"q1", "q2", "q3", "q4"};
    }

    @Override
    protected boolean isBusy() {
        return attack != A_NONE || grabbed != null;
    }

    @Override
    protected boolean canChangePhase() {
        // Não muda de fase no meio da subida para o terraço (a Segunda Etapa vem logo depois dela).
        return super.canChangePhase() && attack != A_OUTSIDE;
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (!isDeadOrDying() && introTicks() <= 0 && isTransforming()) {
            return loop(transformTarget() == FORM_SEGUNDA ? "transform2" : "transform");
        }
        return super.mainAnimation(moving);
    }

    // ------------------------------------------------------------------ Hierro e regeneração

    private float hierroMax() {
        return switch (form()) {
            case FORM_MURCIELAGO -> 60F;
            case FORM_SEGUNDA -> 75F;
            default -> 45F;
        };
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !isAwake() || introTicks() > 0 || isTransforming() || isDeadOrDying()
                || source.is(net.minecraft.tags.DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        ServerLevel sl = (ServerLevel) level();
        float dealt = amount;
        if (!hierroBroken()) {
            // Hierro: golpes fracos (< 8) passam só 30%; golpes fortes passam 70% e racham a pele.
            boolean big = amount >= 8F;
            dealt = amount * (big ? 0.7F : 0.3F);
            hierro -= big ? amount : amount * 0.25F;
            if (sparkCd <= 0) {
                sparkCd = 5;
                Entity src = source.getDirectEntity() != null ? source.getDirectEntity() : source.getEntity();
                Vec3 at = src != null ? position().add(0, 1.2, 0).add(src.position().subtract(position()).normalize().scale(0.5))
                        : position().add(0, 1.2, 0);
                EspadaFx.spark(sl, at, 0xDFFFE8);
                EspadaSounds.play(this, EspadaSounds.HIERRO, 0.7F, big ? 0.8F : 1.3F);
                if (!big && source.getEntity() instanceof Player) hintOnce(sl, "hierro");
            }
            if (hierro <= 0) breakHierro(sl);
        }
        boolean ok = super.hurt(source, dealt);
        if (ok && amount >= 12F && form() >= FORM_MURCIELAGO && regenCd <= 0) {
            regenCd = 600;
            pendingRegen = 30;
        }
        return ok;
    }

    private void breakHierro(ServerLevel sl) {
        hierro = 0;
        hierroBrokenTicks = 160;
        entityData.set(DATA_HIERRO_BROKEN, true);
        EspadaFx.hierroBreak(sl, this);
        playSound(SoundEvents.SHIELD_BREAK, 1.6F, 0.6F);
        playSound(SoundEvents.GLASS_BREAK, 1.4F, 0.5F);
        BossKit.shake(sl, position(), 16, 0.6F, 8);
        announce("hierro_break");
    }

    private void tickHierro(ServerLevel sl) {
        if (sparkCd > 0) sparkCd--;
        if (hierroBrokenTicks > 0 && --hierroBrokenTicks == 0) {
            hierro = hierroMax();
            entityData.set(DATA_HIERRO_BROKEN, false);
            EspadaFx.regen(sl, this);
            playSound(SoundEvents.ANVIL_PLACE, 0.6F, 1.6F);
        }
        if (regenCd > 0) regenCd--;
        if (pendingRegen > 0 && --pendingRegen == 0 && isAlive()) {
            // Regeneração instantânea: o membro ferido se refaz.
            heal(getMaxHealth() * 0.03F);
            action("regen");
            EspadaFx.regen(sl, this);
            playSound(SoundEvents.ZOMBIE_VILLAGER_CURE, 0.7F, 1.6F);
            quote("regen");
        }
    }

    // ------------------------------------------------------------------ Fases e liberações

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        if (phase == 1) {
            startMurcielago(level);
        } else if (phase == 2) {
            if (insideLasNoches()) {
                startOutside(level, true);
            } else if (domeBlocked(level)) {
                pendingSegunda = 1;
                hintOnce(level, "dome");
            } else {
                startSegunda(level);
            }
        }
    }

    /** Marcado para a cúpula e sem céu aberto em cima? (então a Segunda Etapa espera) — legado, sem dados do Las Noches. */
    private boolean domeBlocked(ServerLevel level) {
        return !onDome && !getPersistentData().contains(LN_KEY) && getTags().contains(DOME_TAG) && !level.canSeeSky(blockPosition().above(2));
    }

    /** Lutando DENTRO de Las Noches (ainda não subiu para o terraço)? */
    private boolean insideLasNoches() {
        if (onDome) return false;
        CompoundTag t = getPersistentData().getCompound(LN_KEY);
        return t.contains("dome_x") && t.contains("dome_y") && t.contains("dome_z");
    }

    private void startMurcielago(ServerLevel sl) {
        endAttack(sl);
        startTransform(sl, FORM_MURCIELAGO, 110);
        Cutscenes.playNear(this, 48, "boss_phase", Component.translatable("boss.isekai_mahou.ulquiorra.release1"),
                Component.translatable("boss.isekai_mahou.ulquiorra.release1.sub"), EspadaFx.ULQ_GREEN, 110, null);
        quote("release1");
        Vec3 c = BossKit.arenaCenter(this);
        EspadaFx.rain(sl, this, c != null ? c : position(), 26F, 110, EspadaFx.ULQ_DEEP);
        EspadaSounds.play(this, EspadaSounds.CERO_CHARGE, 2.0F, 0.5F);
    }

    private void startSegunda(ServerLevel sl) {
        pendingSegunda = 0;
        endAttack(sl);
        startTransform(sl, FORM_SEGUNDA, 130);
        Cutscenes.playNear(this, 48, "boss_phase", Component.translatable("boss.isekai_mahou.ulquiorra.release2"),
                Component.translatable("boss.isekai_mahou.ulquiorra.release2.sub"), 0x1A3A26, 130, null);
        quote("release2");
        EspadaFx.darkness(sl, this, 60);
        EspadaSounds.play(this, EspadaSounds.RELEASE, 2.4F, 0.6F);
    }

    @Override
    protected void transformTick(ServerLevel sl, int t, int total, int form) {
        if (form == FORM_MURCIELAGO) {
            // Sobe devagar no meio da chuva negra; as asas abrem na explosão.
            if (t == 8) setFlying(true);
            if (t < 60) setDeltaMovement(0, 0.06, 0);
            else setDeltaMovement(Vec3.ZERO);
            if (t == 60) {
                setForm(FORM_MURCIELAGO);
                EspadaFx.release(sl, this, EspadaFx.ULQ_GREEN, 0);
                EspadaSounds.play(this, EspadaSounds.RELEASE, 2.6F, 0.8F);
                playSound(SoundEvents.PHANTOM_FLAP, 2.0F, 0.5F);
                BossKit.shake(sl, position(), 40, 1.8F, 30);
                pushAway(sl, 10, 1.2);
            }
        } else if (form == FORM_SEGUNDA) {
            if (t == 4) setFlying(true);
            setDeltaMovement(Vec3.ZERO);
            if (t % 20 == 0 && t < 90) EspadaFx.auraPulse(sl, this, 3F + t * 0.06F, EspadaFx.ULQ_BLACK);
            if (t == 90) {
                setForm(FORM_SEGUNDA);
                EspadaFx.release(sl, this, EspadaFx.ULQ_BLACK, 1);
                EspadaSounds.play(this, EspadaSounds.LANZA_EXPLODE, 2.2F, 0.5F);
                BossKit.shake(sl, position(), 48, 2.6F, 40);
                pushAway(sl, 12, 1.6);
                EspadaFx.darkness(sl, this, 20);
            }
        }
    }

    private void pushAway(ServerLevel sl, double radius, double strength) {
        for (LivingEntity e : foesNear(sl, position(), radius)) knock(e, position(), strength, 0.4);
    }

    @Override
    protected void onTransformDone(ServerLevel level, int form) {
        hierro = hierroMax();
        hierroBrokenTicks = 0;
        entityData.set(DATA_HIERRO_BROKEN, false);
        oscurasCd = form == FORM_MURCIELAGO ? 120 : 80;
        lanzaCd = 60;
        luzCd = 40;
        setFlying(true);
        hintOnce(level, form == FORM_SEGUNDA ? "segunda" : "murcielago");
    }

    @Override
    protected void onReset(ServerLevel level) {
        endAttack(level);
        hierro = hierroMax();
        hierroBrokenTicks = 0;
        pendingSegunda = 0;
        entityData.set(DATA_HIERRO_BROKEN, false);
        if (onDome) {
            // Todos foram embora: volta para a sala (a arena volta a ser a sala; o EspadaBoss teleporta para o centro).
            onDome = false;
            CompoundTag t = getPersistentData().getCompound(LN_KEY);
            if (t.contains("home_x")) {
                Vec3 home = new Vec3(t.getDouble("home_x"), t.getDouble("home_y"), t.getDouble("home_z"));
                BossKit.setArena(this, home, 24.0);
                restrictTo(BlockPos.containing(home), 24);
            }
        }
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        quote("intro");
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected void tickCombat(ServerLevel sl, @Nullable LivingEntity target) {
        tickHierro(sl);
        if (slashCd > 0) slashCd--;
        if (sonidoCd > 0) sonidoCd--;
        if (balaCd > 0) balaCd--;
        if (ceroCd > 0) ceroCd--;
        if (luzCd > 0) luzCd--;
        if (oscurasCd > 0) oscurasCd--;
        if (lanzaCd > 0) lanzaCd--;
        if (tailCd > 0) tailCd--;
        if (pendingSegunda > 0 && tickCount % 20 == 0 && !domeBlocked(sl) && attack == A_NONE && grabbed == null) {
            if (insideLasNoches()) startOutside(sl, true);
            else startSegunda(sl);
        }

        if (grabbed != null) {
            tickGrab(sl);
            return;
        }
        if (attack != A_NONE) {
            tickAttack(sl);
            return;
        }
        if (target == null) {
            if (isFlying()) setDeltaMovement(getDeltaMovement().scale(0.8));
            return;
        }
        double d = distanceTo(target);
        boolean los = hasLineOfSight(target);
        int f = form();

        // Escolha do golpe (do mais forte ao mais comum). Dentro de Las Noches, Lanza e Cero Oscuras derrubariam a
        // cúpula: ele leva a luta para o terraço primeiro.
        boolean wantsLanza = f == FORM_SEGUNDA && lanzaCd <= 0 && los;
        boolean wantsOscuras = f >= FORM_MURCIELAGO && oscurasCd <= 0 && los && d < 34;
        if ((wantsLanza || wantsOscuras) && insideLasNoches()) {
            startOutside(sl, false);
        } else if (wantsLanza) {
            startLanza(sl, target);
        } else if (wantsOscuras) {
            startOscuras(sl, target);
        } else if (f == FORM_SEGUNDA && tailCd <= 0 && d < 8 && los) {
            startTail(sl, target);
        } else if (d < 3.6 && slashCd <= 0) {
            startSlash(sl, target);
        } else if (sonidoCd <= 0 && d > 5 && d < 18) {
            sonidoBehind(sl, target);
        } else if (f >= FORM_MURCIELAGO && luzCd <= 0 && los && d > 5 && d < 30) {
            startLuz(sl, target);
        } else if (ceroCd <= 0 && los && d > 4 && d < 28) {
            startCero(sl, target);
        } else if (balaCd <= 0 && los && d > 5 && d < 26) {
            startBala(sl, target);
        } else {
            move(sl, target, d);
        }
    }

    private void move(ServerLevel sl, LivingEntity target, double d) {
        if (isFlying()) {
            // Paira em volta do alvo, alto e calmo.
            orbitAngle += 0.02F;
            double r = d < 4 ? 5.0 : 7.0;
            Vec3 ground = groundAt(sl, target.position());
            Vec3 dest = new Vec3(target.getX() + Math.cos(orbitAngle) * r, Math.max(ground.y, target.getY()) + 3.5,
                    target.getZ() + Math.sin(orbitAngle) * r);
            flyTo(dest, 0.45);
            getLookControl().setLookAt(target, 30F, 30F);
            faceTowards(target);
        } else if (d > 6) {
            chase(target, 0.95);
        } else {
            kite(target, 2.5, 6, 0.8);
        }
    }

    private void endAttack(ServerLevel sl) {
        if (attack == A_OUTSIDE) outsidePlayers.clear();
        attack = A_NONE;
        attackTimer = 0;
        balaShots = 0;
        attackTarget = null;
        entityData.set(DATA_LANCE, false);
        if (lanzaTelegraph >= 0) Telegraph.cancel(sl, lanzaTelegraph);
        lanzaTelegraph = -1;
        releaseGrab();
    }

    @Nullable
    private LivingEntity attackTarget(ServerLevel sl) {
        if (attackTarget == null) return getTarget();
        Entity e = sl.getEntity(attackTarget);
        return e instanceof LivingEntity le && le.isAlive() ? le : getTarget();
    }

    private Vec3 hand() {
        Vec3 dir = Vec3.directionFromRotation(0, getYRot());
        Vec3 right = new Vec3(-dir.z, 0, dir.x);
        return position().add(0, 1.45, 0).add(dir.scale(0.7)).add(right.scale(-0.35));
    }

    private void tickAttack(ServerLevel sl) {
        attackTimer--;
        if (isFlying()) setDeltaMovement(getDeltaMovement().scale(0.6));
        else getNavigation().stop();
        LivingEntity t = attackTarget(sl);
        switch (attack) {
            case A_BALA -> {
                if (t != null) faceTowards(t);
                // 10 ticks de mira, depois uma bala a cada 4 ticks.
                if (attackTimer <= balaShots * 4 && attackTimer % 4 == 0 && balaShots > 0 && t != null) fireBala(sl, t);
            }
            case A_CERO, A_OSCURAS -> holdYaw(attackYaw);
            case A_LANZA -> {
                if (t != null) faceTowards(t);
            }
            case A_OUTSIDE -> tickOutside(sl);
            default -> holdYaw(attackYaw);
        }
        if (attack == A_OUTSIDE && attackTimer <= 0) {
            attack = A_NONE;
            goOutside(sl);
            return;
        }
        if (attackTimer <= 0) {
            // Lança contrariada (Pilha) ou interrompida: ela some da mão.
            if (attack == A_LANZA) entityData.set(DATA_LANCE, false);
            attack = A_NONE;
            attackTarget = null;
        }
    }

    // ---- Espada e Sonído

    private void startSlash(ServerLevel sl, LivingEntity target) {
        slashCd = form() == FORM_SEALED ? 26 : 20;
        attack = A_SLASH;
        attackTimer = 12;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("slash");
        playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.7F);
        float dmg = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        Vec3 origin = position();
        float yaw = attackYaw;
        Telegraph.delayed(sl, Telegraph.Shape.cone(origin, yaw, 110F, 4.6F).warmup(9).color(EspadaFx.ULQ_GREEN).owner(this).noRunes(),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && e.hurt(damageSources().mobAttack(this), dmg)) knock(e, origin, 0.5, 0.15);
                    }
                    EspadaFx.slash(sl, this, origin.add(0, 1.1, 0), Vec3.directionFromRotation(0, yaw), 3.2F,
                            form() == FORM_SEGUNDA ? EspadaFx.ULQ_BLACK : 0xE8FFF0);
                    playSound(SoundEvents.PLAYER_ATTACK_STRONG, 1.2F, 0.8F);
                });
    }

    /** Sonído: some e reaparece atrás do alvo; quase sempre emenda um corte. */
    private void sonidoBehind(ServerLevel sl, LivingEntity target) {
        sonidoCd = form() >= FORM_MURCIELAGO ? 60 : 90;
        Vec3 back = target.getLookAngle().multiply(1, 0, 1);
        back = back.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : back.normalize();
        Vec3 from = position();
        Vec3 spot = target.position().subtract(back.scale(2.2));
        boolean ok = false;
        if (sl.noCollision(this, getBoundingBox().move(spot.subtract(position())))) {
            teleportTo(spot.x, spot.y, spot.z);
            ok = true;
        } else {
            ok = BossKit.blinkNear(this, target, 2.5);
        }
        if (!ok) return;
        action("sonido");
        setDeltaMovement(Vec3.ZERO);
        faceTowards(target);
        EspadaFx.sonido(sl, this, from, position(), EspadaFx.ULQ_GREEN);
        EspadaSounds.play(this, EspadaSounds.SONIDO, 1.2F, 1.0F);
        slashCd = 0;
        startSlash(sl, target);
    }

    // ---- Bala

    private void startBala(ServerLevel sl, LivingEntity target) {
        balaCd = form() == FORM_SEALED ? 110 : 80;
        attack = A_BALA;
        balaShots = form() == FORM_SEALED ? 5 : 7;
        attackTimer = 10 + balaShots * 4;
        attackTarget = target.getUUID();
        action("bala");
        faceTowards(target);
        // Aviso: a mira aparece antes da primeira bala.
        Vec3 from = hand();
        Vec3 to = lineEnd(sl, from, target.position().add(0, 1, 0).subtract(from).normalize(), 26);
        Telegraph.show(sl, Telegraph.Shape.line(groundAt(sl, from), groundAt(sl, to), 1.2F).warmup(10).color(EspadaFx.ULQ_GREEN)
                .owner(this).noRunes().castName(attackName("bala")));
    }

    private void fireBala(ServerLevel sl, LivingEntity t) {
        balaShots--;
        Vec3 from = hand();
        Vec3 aim = t.position().add(0, t.getBbHeight() * 0.55, 0).subtract(from).normalize();
        EspadaBolt.shoot(sl, this, EspadaBolt.BALA, from, aim, 2.4F, scaled(5F), 30);
        EspadaSounds.play(this, EspadaSounds.BALA, 0.9F, 0.9F + random.nextFloat() * 0.3F);
    }

    // ---- Cero (verde) e Cero Oscuras

    private void startCero(ServerLevel sl, LivingEntity target) {
        ceroCd = form() == FORM_SEALED ? 170 : 140;
        beam(sl, target, false);
    }

    /**
     * Cero Oscuras: o Cero mais forte dele — orbe NEGRO de borda verde que cresce na ponta dos dedos (a sombra em volta
     * escurece), faixa marcada no chão e então um feixe negro enorme com bordas e raios verdes, que corrompe quem pega.
     */
    private void startOscuras(ServerLevel sl, LivingEntity target) {
        oscurasCd = form() == FORM_SEGUNDA ? 220 : 320;
        beam(sl, target, true);
        announce("oscuras");
        hintOnce(sl, "oscuras");
    }

    private void beam(ServerLevel sl, LivingEntity target, boolean dark) {
        int charge = dark ? (form() == FORM_SEGUNDA ? 40 : 56) : 24;
        float width = dark ? 6.0F : 2.2F;
        double range = dark ? 46 : 28;
        attack = dark ? A_OSCURAS : A_CERO;
        attackTimer = charge + (dark ? 18 : 8);
        faceTowards(target);
        attackYaw = getYRot();
        action(dark ? "cero_oscuras" : "cero");
        Vec3 from = hand();
        Vec3 aim = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(from).normalize();
        Vec3 to = lineEnd(sl, from, aim, range);
        beamFrom = from;
        beamTo = to;
        int color = dark ? EspadaFx.ULQ_BLACK : EspadaFx.ULQ_GREEN;
        EspadaFx.charge(sl, this, from, charge, dark ? 2.0F : 0.8F, EspadaFx.ULQ_GREEN, dark ? EspadaFx.STYLE_DARK : EspadaFx.STYLE_GREEN);
        EspadaSounds.play(this, EspadaSounds.CERO_CHARGE, dark ? 2.2F : 1.4F, dark ? 0.6F : 1.0F);
        Vec3 g1 = groundAt(sl, from), g2 = groundAt(sl, to);
        Telegraph.Shape s = Telegraph.Shape.line(g1, g2, width).warmup(charge).color(dark ? 0x10C060 : EspadaFx.ULQ_GREEN).owner(this)
                .castName(attackName(dark ? "cero_oscuras" : "cero")).element(Element.SHADOW).vertical(2.0, 40.0);
        float dmg = scaled(dark ? 30F : 14F);
        Telegraph.delayed(sl, s, ignored -> fireBeam(sl, dark, width, dmg, color));
    }

    private void fireBeam(ServerLevel sl, boolean dark, float width, float dmg, int color) {
        if (!isAlive() || isTransforming()) return;
        Vec3 from = beamFrom, to = beamTo;
        EspadaFx.beam(sl, this, from, to, width, dark ? 24 : 10, EspadaFx.ULQ_GREEN, dark ? EspadaFx.STYLE_DARK : EspadaFx.STYLE_GREEN);
        if (dark) {
            // Raios verdes correndo pelo feixe negro e a sombra caindo sobre quem está perto.
            EspadaFx.lightning(sl, from, to, width, EspadaFx.ULQ_GREEN, EspadaFx.STYLE_DARK);
            EspadaFx.darkness(sl, this, 14);
        }
        EspadaSounds.play(this, EspadaSounds.CERO_FIRE, dark ? 3.0F : 1.8F, dark ? 0.55F : 0.9F);
        BossKit.shake(sl, from, dark ? 40 : 20, dark ? 2.2F : 0.9F, dark ? 20 : 10);
        for (LivingEntity e : foesAlong(sl, from, to, width * 0.5)) {
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                knock(e, from, dark ? 1.7 : 0.8, 0.4);
                if (dark) Statuses.apply(e, Statuses.Kind.CORRUPTED, 2, 140, this, 1F);
            }
        }
        if (dark) EspadaFx.explosion(sl, to, 5.5F, EspadaFx.ULQ_GREEN, EspadaFx.STYLE_DARK);
        else EspadaFx.explosion(sl, to, 2F, EspadaFx.ULQ_GREEN, EspadaFx.STYLE_GREEN);
    }

    // ---- Luz de la Luna

    private void startLuz(ServerLevel sl, LivingEntity target) {
        luzCd = form() == FORM_SEGUNDA ? 110 : 140;
        attack = A_LUZ;
        attackTimer = 40;
        attackTarget = target.getUUID();
        action("luz_luna");
        playSound(SoundEvents.TRIDENT_RIPTIDE_2.value(), 1.4F, 0.7F);
        for (int i = 0; i < 3; i++) {
            int delay = 6 + i * 9;
            BossKit.schedule(sl, delay, () -> throwLance(sl));
        }
    }

    private void throwLance(ServerLevel sl) {
        LivingEntity t = attackTarget(sl);
        if (!isAlive() || t == null || isTransforming()) return;
        faceTowards(t);
        Vec3 from = position().add(0, 2.2, 0);
        Vec3 aim = t.position().add(0, t.getBbHeight() * 0.5, 0).subtract(from).normalize();
        Vec3 to = lineEnd(sl, from, aim, 30);
        float dmg = scaled(9F);
        Telegraph.delayed(sl, Telegraph.Shape.line(groundAt(sl, from), groundAt(sl, to), 1.6F).warmup(12).color(EspadaFx.ULQ_GREEN)
                .owner(this).noRunes().castName(attackName("luz_luna")).vertical(2.0, 30.0), ignored -> {
                    if (!isAlive()) return;
                    EspadaBolt.shoot(sl, this, EspadaBolt.LANCE, from, aim, 2.2F, dmg, 32);
                    playSound(SoundEvents.TRIDENT_THROW.value(), 1.6F, 0.6F);
                });
    }

    // ---- Lanza del Relámpago

    private void startLanza(ServerLevel sl, LivingEntity target) {
        lanzaCd = 400;
        attack = A_LANZA;
        attackTimer = 56;
        attackTarget = target.getUUID();
        entityData.set(DATA_LANCE, true);
        action("lanza_charge");
        announce("lanza");
        hintOnce(sl, "lanza");
        EspadaFx.lanceForm(sl, this, 44);
        EspadaSounds.play(this, EspadaSounds.LANZA_CHARGE, 2.6F, 0.8F);
        // Alvo marcado que SEGUE o jogador e trava no fim: dá para correr do ponto final.
        float dmg = scaled(34F);
        lanzaTelegraph = Telegraph.delayed(sl, Telegraph.Shape.follow(target, LANZA_RADIUS).warmup(44).lock(10).color(0x2BFF7A).owner(this)
                .castName(attackName("lanza")).element(Element.SHADOW), ignored -> throwLanza(sl, dmg));
    }

    /** w10: raio do núcleo da explosão (dano cheio caindo com a distância) e da onda de choque que vem depois. */
    private static final float LANZA_RADIUS = 12F;
    public static final float LANZA_SHOCK_RADIUS = 22F;
    /** Ticks entre a explosão e a onda de choque. */
    public static final int LANZA_SHOCK_DELAY = 8;
    private static final float LANZA_SPEED = 2.6F;

    /** Arremessa a lança no ponto travado: ela VOA (projétil) e explode no impacto — entidade, bloco ou fim do alcance. */
    private void throwLanza(ServerLevel sl, float dmg) {
        Vec3 c = lanzaTelegraph >= 0 ? Telegraph.centerOf(lanzaTelegraph) : null;
        lanzaTelegraph = -1;
        entityData.set(DATA_LANCE, false);
        if (!isAlive() || isTransforming()) return;
        LivingEntity t = attackTarget(sl);
        Vec3 center = groundAt(sl, c != null ? c : t != null ? t.position() : position());
        action("lanza_throw");
        Vec3 from = position().add(0, getBbHeight() + 0.7, 0);
        Vec3 aim = center.add(0, 0.4, 0).subtract(from);
        double dist = Math.max(1.0, aim.length());
        EspadaBolt.shoot(sl, this, EspadaBolt.LANZA, from, aim.normalize(), LANZA_SPEED, dmg, dist + 4.0);
        // Onde a lança vai cair (aviso só visual; quem estiver no caminho leva a explosão antes).
        int eta = Math.max(3, (int) Math.ceil(dist / LANZA_SPEED));
        Telegraph.show(sl, Telegraph.Shape.circle(center, LANZA_RADIUS).warmup(eta).color(Telegraph.DANGER).owner(this)
                .castName(attackName("lanza_blast")).uncounterable().noRunes().vertical(3.0, 8.0));
        // w10: e a onda de choque que vem logo depois (anel verde até LANZA_SHOCK_RADIUS).
        Telegraph.show(sl, Telegraph.Shape.ring(center, LANZA_RADIUS, LANZA_SHOCK_RADIUS).warmup(eta + LANZA_SHOCK_DELAY).color(EspadaFx.ULQ_GREEN)
                .owner(this).uncounterable().noRunes());
        EspadaFx.flash(sl, from, 2.5F, 0xCFFFE0);
        playSound(SoundEvents.TRIDENT_THROW.value(), 2.4F, 0.5F);
        playSound(SoundEvents.TRIDENT_THUNDER.value(), 1.6F, 1.4F);
    }

    /**
     * Explosão da Lanza del Relámpago (chamada pelo projétil no impacto) — w10: GIGANTE. Clarão, cúpula e pilar verdes
     * enormes, raios caindo e correndo pelo chão, tremor forte; dano em área (raio 12) com queda pela distância, empurrão
     * e Choque; {@value #LANZA_SHOCK_DELAY} ticks depois uma ONDA DE CHOQUE verde corre até {@value #LANZA_SHOCK_RADIUS}
     * blocos (30% do dano, empurra e deixa lento). Terreno: só pelo {@link Destruction} (respeita arena selada, lugares
     * protegidos — Las Noches, cidades, torres/masmorras — e as regras do mundo).
     */
    public static void lanzaBlast(ServerLevel sl, Vec3 at, @Nullable LivingEntity owner, @Nullable Entity direct, float dmg) {
        blast(sl, at, owner, direct, dmg, LANZA_RADIUS, LANZA_SHOCK_RADIUS, null);
    }

    /**
     * A mesma explosão gigante, lançada por um jogador (Magia Lendária "Lanza del Relámpago"): alvos e dano pela magia
     * ({@code dmg} é o dano BASE, a magia aplica o poder). NÃO quebra blocos (só as rachaduras falsas no chão).
     */
    public static void lanzaBlast(ServerLevel sl, Vec3 at, @Nullable LivingEntity owner, @Nullable Entity direct, float dmg, SpellContext ctx) {
        float boost = Math.max(0, ctx.formStats().aoe);
        blast(sl, at, owner, direct, dmg, Math.min(16F, 10F + 1.5F * boost), Math.min(26F, 18F + 2F * boost), ctx);
    }

    private static void blast(ServerLevel sl, Vec3 at, @Nullable LivingEntity owner, @Nullable Entity direct, float dmg, float radius, float shock,
                              @Nullable SpellContext ctx) {
        // Visual: o cliente trata raio >= 14 como a explosão GIGANTE (cúpula, pilar alto, ondas atrasadas).
        EspadaFx.explosion(sl, at, radius * 1.6F, EspadaFx.ULQ_GREEN, EspadaFx.STYLE_GREEN);
        EspadaFx.lightning(sl, at, at, radius + 4F, EspadaFx.ULQ_GREEN, EspadaFx.STYLE_GREEN);
        EspadaFx.flash(sl, at, radius * 1.7F, 0xCFFFE0);
        SoundSource cat = ctx != null ? SoundSource.PLAYERS : SoundSource.HOSTILE;
        sl.playSound(null, at.x, at.y, at.z, EspadaSounds.LANZA_EXPLODE.get(), cat, 6.0F, 0.7F);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_THUNDER, cat, 4.0F, 0.9F);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE.value(), cat, 4.0F, 0.5F);
        BossKit.shake(sl, at, 80, 3.0F, 50);
        BossKit.crackFloor(sl, at, radius * 0.75, 8, 100);
        // Terreno: só o chefe abre cratera (e só onde as regras deixam); a lenda do jogador não quebra blocos.
        if (ctx == null) Destruction.crater(sl, at, 4.5F, owner);
        DamageSource src = sl.damageSources().explosion(direct != null ? direct : owner, owner);
        for (LivingEntity e : blastTargets(sl, at, radius, owner, ctx)) {
            double d = e.position().distanceTo(at);
            float k = (float) Mth.clamp(1.15 - d / radius, 0.4, 1.0);
            boolean hit = ctx != null ? ctx.damage(e, dmg * k, ctx.formStats(), direct, Element.SHADOW) > 0F : e.hurt(src, dmg * k);
            if (hit) {
                knock(e, at, 1.9 * k, 0.75 * k);
                Statuses.apply(e, Statuses.Kind.SHOCKED, 2, 100, owner, 1F);
            }
        }
        // A onda de choque atrasada: anel que corre para fora (quem estava longe do núcleo ainda sente).
        BossKit.schedule(sl, LANZA_SHOCK_DELAY, () -> {
            // (o anel verde que corre pelo chão é desenhado pelo cliente, no mesmo tempo, junto da explosão gigante)
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.LIGHTNING_BOLT_IMPACT, cat, 3.0F, 0.5F);
            sl.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, cat, 2.0F, 0.6F);
            BossKit.shake(sl, at, shock + 24, 1.6F, 20);
            DamageSource src2 = sl.damageSources().explosion(direct != null && direct.isAlive() ? direct : owner, owner);
            for (LivingEntity e : blastTargets(sl, at, shock, owner, ctx)) {
                double d = e.position().distanceTo(at);
                if (d < radius * 0.5) continue; // o núcleo já levou a explosão inteira
                float k = (float) Mth.clamp(1.1 - d / shock, 0.35, 1.0);
                boolean hit = ctx != null ? ctx.damage(e, dmg * 0.3F * k, ctx.formStats(), owner, Element.SHADOW) > 0F
                        : e.hurt(src2, dmg * 0.3F * k);
                if (hit) {
                    knock(e, at, 1.3 * k, 0.45 * k);
                    e.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 50, 1));
                }
            }
        });
    }

    /** Alvos da explosão (no máx. 40, limite de desempenho): da magia do jogador, ou todos menos os Espada e lacaios. */
    private static List<LivingEntity> blastTargets(ServerLevel sl, Vec3 at, float radius, @Nullable LivingEntity owner, @Nullable SpellContext ctx) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius + 1.0), e -> e.isAlive()
                && e != owner && !(e instanceof Player p && (p.isCreative() || p.isSpectator()))
                && (ctx != null ? !ctx.isAlly(e) && !ctx.ignores(e) : !(e instanceof EspadaBoss) && !isMinion(e))
                && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(at) <= radius + e.getBbWidth() * 0.5)) {
            out.add(e);
            if (out.size() >= 40) break;
        }
        return out;
    }

    // ---- "Lá fora": a luta sobe para o terraço da cúpula de Las Noches

    /** Começa a subida (fala + cena curta); no fim, ele e os jogadores da luta vão para o terraço. */
    private void startOutside(ServerLevel sl, boolean thenSegunda) {
        endAttack(sl);
        Telegraph.cancelAll(this);
        attack = A_OUTSIDE;
        attackTimer = OUTSIDE_TICKS;
        outsideThenSegunda = thenSegunda;
        if (thenSegunda) pendingSegunda = 0;
        outsidePlayers.clear();
        for (ServerPlayer p : BossKit.partyAround(this, 48)) {
            if (validPlayer(p)) outsidePlayers.add(p.getUUID());
        }
        setFlying(true);
        quote("outside");
        Cutscenes.playNear(this, 48, "boss_phase", Component.translatable("boss.isekai_mahou.ulquiorra.outside"),
                Component.translatable("boss.isekai_mahou.ulquiorra.outside.sub"), EspadaFx.ULQ_GREEN, OUTSIDE_TICKS + 10, null);
        hintOnce(sl, "outside");
        EspadaFx.charge(sl, this, position().add(0, getBbHeight() * 0.6, 0), OUTSIDE_TICKS, 1.0F, EspadaFx.ULQ_GREEN, EspadaFx.STYLE_DARK);
        EspadaSounds.play(this, EspadaSounds.CERO_CHARGE, 1.8F, 0.6F);
    }

    private void tickOutside(ServerLevel sl) {
        int t = OUTSIDE_TICKS - attackTimer;
        // Sobe devagar, olhando para os jogadores; reiatsu verde subindo em espiral.
        setDeltaMovement(0, t < 30 ? 0.12 : 0.0, 0);
        hasImpulse = true;
        if (t % 10 == 0) EspadaFx.auraPulse(sl, this, 2.5F + t * 0.04F, EspadaFx.ULQ_GREEN);
        if (t == 30) EspadaFx.flash(sl, position().add(0, 1.2, 0), 4F, 0xCFFFE0);
    }

    /** Leva ele e os jogadores da luta para o terraço e muda a arena (proteção, leash e barra seguem). */
    private void goOutside(ServerLevel sl) {
        CompoundTag t = getPersistentData().getCompound(LN_KEY);
        if (!t.contains("dome_x")) {
            onDome = true;
            if (outsideThenSegunda) pendingSegunda = 1;
            return;
        }
        Vec3 dome = new Vec3(t.getDouble("dome_x"), t.getDouble("dome_y"), t.getDouble("dome_z"));
        double r = t.contains("dome_r") ? t.getDouble("dome_r") : 24.0;
        sl.getChunkAt(BlockPos.containing(dome));
        Vec3 from = position();
        // Quem estava na luta (marcado no começo da subida) + quem chegou perto durante a subida.
        List<ServerPlayer> moved = new ArrayList<>();
        for (UUID id : outsidePlayers) {
            if (sl.getEntity(id) instanceof ServerPlayer p && validPlayer(p) && p.distanceToSqr(from) < 64 * 64) moved.add(p);
        }
        for (ServerPlayer p : BossKit.partyAround(this, 48)) {
            if (validPlayer(p) && !moved.contains(p)) moved.add(p);
        }
        Vec3 center = standSpot(sl, dome);
        teleportTo(center.x, center.y + 4.0, center.z);
        setDeltaMovement(Vec3.ZERO);
        setFlying(true);
        BossKit.setArena(this, center, r);
        restrictTo(BlockPos.containing(center), (int) r);
        BossArena.move(this, center, r);
        EspadaFx.sonido(sl, this, from, position(), EspadaFx.ULQ_GREEN);
        EspadaFx.release(sl, this, EspadaFx.ULQ_GREEN, 0);
        EspadaSounds.play(this, EspadaSounds.SONIDO, 2.0F, 0.7F);
        BossKit.shake(sl, center, 40, 1.4F, 20);
        // Os jogadores chegam num anel em volta dele, olhando para ele.
        int n = Math.max(1, moved.size());
        for (int i = 0; i < moved.size(); i++) {
            ServerPlayer p = moved.get(i);
            double a = Math.PI * 0.5 + Math.PI * 2 * i / n;
            Vec3 spot = standSpot(sl, center.add(Math.cos(a) * 9.0, 0, Math.sin(a) * 9.0));
            float yaw = (float) (Mth.atan2(center.z - spot.z, center.x - spot.x) * (180.0 / Math.PI)) - 90.0F;
            Vec3 pf = p.position();
            p.teleportTo(sl, spot.x, spot.y, spot.z, yaw, 0F);
            p.resetFallDistance();
            p.setDeltaMovement(Vec3.ZERO);
            EspadaFx.sonido(sl, p, pf, spot, EspadaFx.ULQ_GREEN);
        }
        outsidePlayers.clear();
        onDome = true;
        Cutscenes.playNear(this, 48, "boss_phase", Component.translatable("boss.isekai_mahou.ulquiorra.dome"),
                Component.translatable("boss.isekai_mahou.ulquiorra.dome.sub"), EspadaFx.ULQ_GREEN, 60, null);
        quote("dome");
        if (outsideThenSegunda) {
            pendingSegunda = 1;
        } else {
            oscurasCd = Math.min(oscurasCd, 30);
        }
        outsideThenSegunda = false;
    }

    /**
     * Pés num lugar firme perto de {@code at} (procura de 6 acima a 8 abaixo). Se o terraço não tiver chão ali, faz
     * uma plataforma 3×3 de quartzo liso (só onde é ar) — ninguém cai da cúpula.
     */
    private Vec3 standSpot(ServerLevel sl, Vec3 at) {
        BlockPos base = BlockPos.containing(at);
        for (int dy = 6; dy >= -8; dy--) {
            BlockPos p = base.above(dy);
            BlockPos below = p.below();
            if (sl.getBlockState(below).isFaceSturdy(sl, below, Direction.UP)
                    && sl.getBlockState(p).getCollisionShape(sl, p).isEmpty()
                    && sl.getBlockState(p.above()).getCollisionShape(sl, p.above()).isEmpty()) {
                return Vec3.atBottomCenterOf(p);
            }
        }
        BlockPos floor = base.below();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos q = floor.offset(dx, 0, dz);
                if (sl.getBlockState(q).isAir()) sl.setBlock(q, Blocks.SMOOTH_QUARTZ.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        for (BlockPos q : new BlockPos[] {base, base.above()}) {
            if (!sl.getBlockState(q).getCollisionShape(sl, q).isEmpty()) sl.setBlock(q, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        return Vec3.atBottomCenterOf(base);
    }

    // ---- Cauda (Segunda Etapa)

    private void startTail(ServerLevel sl, LivingEntity target) {
        tailCd = 170;
        attack = A_TAIL;
        attackTimer = 16;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        action("tail_grab");
        playSound(SoundEvents.WARDEN_ATTACK_IMPACT, 1.0F, 1.4F);
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = groundAt(sl, position());
        Vec3 to = lineEnd(sl, from.add(0, 0.5, 0), dir, 9).subtract(0, 0.5, 0);
        Telegraph.delayed(sl, Telegraph.Shape.line(from, to, 1.8F).warmup(12).color(EspadaFx.ULQ_GREEN).owner(this).noRunes()
                .castName(attackName("tail")).vertical(2.0, 6.0), hits -> {
                    if (!isAlive() || grabbed != null) return;
                    LivingEntity best = null;
                    for (LivingEntity e : hits) {
                        if (isFoe(e) && (best == null || e.distanceToSqr(this) < best.distanceToSqr(this))) best = e;
                    }
                    if (best == null) return;
                    grabbed = best.getUUID();
                    grabTicks = 24;
                    attack = A_NONE;
                    best.hurt(damageSources().mobAttack(this), scaled(5F));
                    EspadaFx.grab(sl, this, tailTip(), best.position(), EspadaFx.ULQ_GREEN);
                    quote("tail");
                });
    }

    private Vec3 tailTip() {
        Vec3 dir = Vec3.directionFromRotation(0, getYRot());
        return position().add(dir.scale(2.4)).add(0, 2.4, 0);
    }

    private void tickGrab(ServerLevel sl) {
        Entity e = grabbed != null ? sl.getEntity(grabbed) : null;
        if (!(e instanceof LivingEntity le) || !le.isAlive() || !isAlive()) {
            releaseGrab();
            return;
        }
        if (isFlying()) setDeltaMovement(Vec3.ZERO);
        Vec3 tip = tailTip();
        le.teleportTo(tip.x, tip.y - le.getBbHeight() * 0.5, tip.z);
        le.setDeltaMovement(Vec3.ZERO);
        le.fallDistance = 0;
        if (grabTicks % 6 == 0) le.hurt(damageSources().mobAttack(this), scaled(2F));
        if (--grabTicks <= 0) {
            action("tail_throw");
            Vec3 dir = Vec3.directionFromRotation(0, getYRot() + 180F + (random.nextBoolean() ? 50 : -50));
            le.hurt(damageSources().mobAttack(this), scaled(9F));
            le.setDeltaMovement(dir.x * 2.2, 0.9, dir.z * 2.2);
            le.hurtMarked = true;
            playSound(SoundEvents.PLAYER_ATTACK_KNOCKBACK, 1.6F, 0.6F);
            EspadaFx.sonido(sl, le, tip, tip.add(dir.scale(6)), EspadaFx.ULQ_GREEN);
            releaseGrab();
        }
    }

    private void releaseGrab() {
        grabbed = null;
        grabTicks = 0;
    }

    // ------------------------------------------------------------------ Anti-trapaça (voa até você)

    @Override
    protected void onUnreachable(ServerLevel sl, LivingEntity target) {
        if (form() >= FORM_MURCIELAGO) {
            Vec3 from = position();
            Vec3 to = target.position().add(0, 2.5, 0);
            if (sl.noCollision(this, getBoundingBox().move(to.subtract(position())))) {
                teleportTo(to.x, to.y, to.z);
                EspadaFx.sonido(sl, this, from, to, EspadaFx.ULQ_GREEN);
                EspadaSounds.play(this, EspadaSounds.SONIDO, 1.2F, 0.8F);
                return;
            }
        }
        super.onUnreachable(sl, target);
    }

    // ------------------------------------------------------------------ Morte: cinzas

    @Override
    protected void onDefeated(ServerLevel sl) {
        endAttack(sl);
        super.onDefeated(sl);
        quote("death");
    }

    @Override
    protected void deathTick(ServerLevel sl, int t) {
        setDeltaMovement(getDeltaMovement().multiply(0.5, 0.5, 0.5));
        if (t == 70) quote("death2");
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        dropItem(EspadaRegistries.MURCIELAGO_WING.get(), 1, 1);
        dropItem(EspadaRegistries.REISHI_RESTORATION.get(), 1, 2);
        ItemStack legend = EspadaLegends.scroll(EspadaLegends.ULQUIORRA);
        if (!legend.isEmpty()) spawnAtLocation(legend);
        // w10: quem lutou aprende a técnica (a forma da Magia Lendária)
        EspadaLegends.teach(this, EspadaLegends.ULQUIORRA);
        var shadow = CoreRegistries.ESSENCES.get(Element.SHADOW);
        if (shadow != null) dropItem(shadow.get(), 4, 7);
        dropById("mana_crystal", 3, 5);
        dropById("hollow_mask_fragment", 1, 2);
    }

    // ------------------------------------------------------------------ Salvar

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putFloat("Hierro", hierro);
        // Subida interrompida pelo salvamento: a Segunda Etapa fica pendente (ele sobe de novo ao carregar).
        tag.putInt("PendingSegunda", attack == A_OUTSIDE && outsideThenSegunda ? 1 : pendingSegunda);
        tag.putBoolean("OnDome", onDome);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        hierro = tag.contains("Hierro") ? tag.getFloat("Hierro") : hierroMax();
        pendingSegunda = tag.getInt("PendingSegunda");
        onDome = tag.getBoolean("OnDome");
    }

    // ------------------------------------------------------------------ Teste

    @Override
    public List<String> debugAttacks() {
        return List.of("lanza", "oscuras", "cero", "luz", "bala", "outside", "murcielago", "segunda");
    }

    @Override
    public boolean debugCast(ServerLevel sl, String name, @Nullable LivingEntity target) {
        if (isTransforming() || introTicks() > 0) return false;
        endAttack(sl);
        switch (name) {
            case "murcielago" -> startMurcielago(sl);
            case "segunda" -> {
                if (insideLasNoches()) startOutside(sl, true);
                else startSegunda(sl);
            }
            case "outside" -> {
                if (!insideLasNoches()) return false;
                startOutside(sl, false);
            }
            default -> {
                if (target == null) return false;
                switch (name) {
                    case "lanza" -> startLanza(sl, target);
                    case "oscuras" -> startOscuras(sl, target);
                    case "cero" -> startCero(sl, target);
                    case "luz" -> startLuz(sl, target);
                    case "bala" -> startBala(sl, target);
                    default -> {
                        return false;
                    }
                }
            }
        }
        return true;
    }
}
