package com.renanpontes.isekaimahou.feature.bosses_f.entity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_f.BfRegistry;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;

/**
 * Hidra do Pântano (covil {@code hydra_lair}). Corpo enorme com 5 encaixes de pescoço — começa com 3 cabeças.
 * <ul>
 *   <li><b>Cabeças</b> são caixas de acerto próprias (vida = 12% da vida máxima). Cortada, vira um toco aberto que em
 *       10 s <b>regenera em dobro</b> (volta + nasce outra num encaixe vazio, até 5) — a não ser que a Hidra pegue fogo
 *       com o toco aberto (fogo/Em Chamas/flecha flamejante/Aspecto Flamejante): o toco é <b>cauterizado</b> para sempre;</li>
 *   <li>o corpo aguenta quase tudo enquanto houver cabeça viva (45%, +10% por toco cauterizado); sem cabeças, fica
 *       exposto (135%). Cortar e cauterizar arrancam um pedaço da vida;</li>
 *   <li><b>Mordidas</b>: cada cabeça morde sozinha (marca redonda no alvo, 14 ticks) e envenena; cortar a cabeça no
 *       meio do bote cancela a mordida;</li>
 *   <li><b>Varrida de Cauda</b> em quem fica atrás; <b>Sopro Pestilento</b> (cone, todas as cabeças);</li>
 *   <li>fase 2: <b>Cuspe Venenoso</b> (bola em arco + poça) e <b>Investida</b> em linha;</li>
 *   <li>fase 3: <b>Emboscada do Lodo</b> (afunda e irrompe embaixo de um jogador, marca que segue e trava), cuspe
 *       triplo e brotam cabeças novas se restarem poucas.</li>
 * </ul>
 */
public class SwampHydra extends OverworldBoss {
    public static final int SLOTS = 5;
    private static final int NONE = 0, ALIVE = 1, STUMP = 2, SEARED = 3;
    private static final int COLOR = 0x6FB83A;
    private static final int VENOM = 0x8BE04A;
    private static final int GAS = 0x7BC043;
    /** Centro de cada cabeça em repouso (px, espaço Java do modelo) — saída de tools/gen/bosses_f.py. */
    private static final double[][] HEAD_REST = {
            {-29.67, 60.10, -45.14}, {-15.11, 60.10, -48.27}, {0.00, 60.10, -49.34}, {15.11, 60.10, -48.27}, {29.67, 60.10, -45.14}};
    private static final EntityDataAccessor<Integer> DATA_HEADS = SynchedEntityData.defineId(SwampHydra.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(SwampHydra.class, EntityDataSerializers.INT);
    private static final int ST_NORMAL = 0, ST_SINK = 1, ST_HIDDEN = 2, ST_RISE = 3;

    private static final int BITE_WARMUP = 14;
    private static final float BITE_RADIUS = 2.0F;
    private static final double BITE_REACH = 7.5;
    private static final int TAIL_WARMUP = 16;
    private static final int BREATH_WARMUP = 26;
    private static final int CHARGE_WARMUP = 20;
    private static final int SINK_TICKS = 30;
    private static final int AMBUSH_WARMUP = 44;
    private static final int RISE_TICKS = 20;

    private final float[] headHp = new float[SLOTS];
    private final int[] regrow = new int[SLOTS];
    private final int[] biteCd = new int[SLOTS];
    private final int[] biteTele = new int[SLOTS];
    private final int[] biting = new int[SLOTS];
    private int biteGap;
    private int tailCd = 40, breathCd = 160, spitCd = 120, chargeCd = 160, ambushCd = 200;
    private int tailTimer, breathTimer, spitTimer, chargeWind, chargeRun, chargeTicks;
    private float attackYaw;
    private Vec3 chargeDir = Vec3.ZERO;
    private final Set<UUID> chargeHit = new HashSet<>();
    private int stateTimer;
    @Nullable
    private Vec3 ambushAt;
    private boolean sproutedP3;
    private int waterHintCd;
    /** Falas repetidas (corte, regeneração, cauterização) no máx. 1 a cada 4 s. */
    private int talkCd;

    public SwampHydra(EntityType<? extends SwampHydra> type, Level level) {
        super(type, level);
        this.xpReward = 250;
        setPathfindingMalus(PathType.WATER, 0.0F);
        setPathfindingMalus(PathType.WATER_BORDER, 0.0F);
        BossPart[] heads = new BossPart[SLOTS];
        for (int i = 0; i < SLOTS; i++) heads[i] = new BossPart(this, i, 1.2F, 1.1F);
        initParts(heads);
        java.util.Arrays.fill(biteTele, -1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(380.0, 10.0, 0.23, 11.0)
                .add(Attributes.STEP_HEIGHT, 1.5)
                .add(Attributes.ATTACK_KNOCKBACK, 1.0);
    }

    @Override
    public String bossId() {
        return "swamp_hydra";
    }

    @Override
    public int bossColor() {
        return COLOR;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.GREEN;
    }

    @Override
    protected void configureBar(ServerBossEvent bossBar) {
        bossBar.setCreateWorldFog(true);
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.66F, 0.33F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("roar", "tail", "charge");
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return !effect.is(MobEffects.POISON) && super.canBeAffected(effect);
    }

    // ------------------------------------------------------------------ Encaixes das cabeças

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_HEADS, initialHeads());
        builder.define(DATA_STATE, ST_NORMAL);
    }

    private static int initialHeads() {
        int v = 0;
        for (int i = 1; i <= 3; i++) v |= ALIVE << (i * 2);
        return v;
    }

    public int slot(int i) {
        return (entityData.get(DATA_HEADS) >> (i * 2)) & 3;
    }

    private void setSlot(int i, int state) {
        int v = entityData.get(DATA_HEADS);
        v &= ~(3 << (i * 2));
        v |= (state & 3) << (i * 2);
        entityData.set(DATA_HEADS, v);
    }

    public int aliveHeads() {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) if (slot(i) == ALIVE) n++;
        return n;
    }

    private int countSlots(int state) {
        int n = 0;
        for (int i = 0; i < SLOTS; i++) if (slot(i) == state) n++;
        return n;
    }

    public int state() {
        return entityData.get(DATA_STATE);
    }

    private float headMaxHp() {
        return getMaxHealth() * 0.12F;
    }

    @Override
    public boolean isPartActive(int index) {
        return slot(index) == ALIVE && state() == ST_NORMAL;
    }

    @Override
    protected void positionParts() {
        BossPart[] heads = bossParts();
        float t = tickCount * 0.07F;
        for (int i = 0; i < SLOTS; i++) {
            double[] r = HEAD_REST[i];
            double sway = Math.sin(t + i * 1.3) * 1.2;
            Vec3 p = modelPoint(r[0] + sway, r[1] + Math.sin(t * 0.8 + i) * 0.8, r[2], 1.0);
            double sink = state() == ST_NORMAL ? 0 : 4.0;
            heads[i].moveTo(p.x, p.y - 0.55 - sink, p.z);
        }
    }

    private Vec3 headPos(int i) {
        BossPart h = bossParts()[i];
        return new Vec3(h.getX(), h.getY() + 0.55, h.getZ());
    }

    @Override
    public List<String> toggledBones() {
        List<String> out = new ArrayList<>(SLOTS * 3);
        for (int i = 0; i < SLOTS; i++) {
            out.add("neck_" + i);
            out.add("stump_" + i);
            out.add("seared_" + i);
        }
        return out;
    }

    @Override
    public boolean isBoneVisible(String bone) {
        int i = bone.charAt(bone.length() - 1) - '0';
        if (i < 0 || i >= SLOTS) return true;
        int s = slot(i);
        if (bone.startsWith("neck_")) return s == ALIVE;
        if (bone.startsWith("stump_")) return s == STUMP;
        return s == SEARED;
    }

    // ------------------------------------------------------------------ Dano: cabeças, fogo, corpo

    @Override
    public boolean isPickable() {
        return state() == ST_NORMAL && super.isPickable();
    }

    @Override
    protected float modifyIncomingDamage(@Nullable BossPart part, DamageSource source, float amount) {
        if (state() != ST_NORMAL) return 0F;
        if (isFireHit(source)) searStumps((ServerLevel) level());
        if (part != null) return amount * 0.6F;
        int alive = aliveHeads();
        if (alive == 0) return amount * 1.35F;
        return amount * Math.min(1.0F, 0.45F + 0.10F * countSlots(SEARED));
    }

    /** Fogo de verdade: dano de fogo, projétil em chamas, ou magia de fogo que acabou de acertar o corpo. */
    private boolean isFireHit(DamageSource source) {
        if (source.is(DamageTypeTags.IS_FIRE)) return true;
        Entity direct = source.getDirectEntity();
        if (direct != null && direct != source.getEntity() && direct.isOnFire()) return true;
        if (source.is(CoreRegistries.SPELL_DAMAGE)) {
            Element el = ElementReactions.lastElement(this, level().getGameTime(), 2);
            return el != null && ElementReactions.isFire(el);
        }
        return false;
    }

    @Override
    protected void onDamaged(@Nullable BossPart part, DamageSource source, float raw, float dealt) {
        if (part == null || !(level() instanceof ServerLevel sl)) return;
        int i = part.index();
        if (slot(i) != ALIVE) return;
        headHp[i] -= raw;
        Vec3 hp = headPos(i);
        sl.sendParticles(BfFx.dust(0x8E1E2A, 1.2F), hp.x, hp.y, hp.z, 6, 0.3, 0.3, 0.3, 0);
        if (headHp[i] <= 0) sever(sl, i);
    }

    private void sever(ServerLevel sl, int i) {
        Vec3 hp = headPos(i);
        setSlot(i, STUMP);
        regrow[i] = regrowTicks();
        biting[i] = 0;
        if (biteTele[i] >= 0) {
            Telegraph.cancel(sl, biteTele[i]);
            biteTele[i] = -1;
        }
        sl.sendParticles(BfFx.dust(0x8E1E2A, 2.0F), hp.x, hp.y, hp.z, 40, 0.5, 0.5, 0.5, 0);
        sl.sendParticles(BfFx.dust(VENOM, 1.4F), hp.x, hp.y, hp.z, 20, 0.4, 0.4, 0.4, 0);
        sl.sendParticles(ParticleTypes.ITEM_SLIME, hp.x, hp.y, hp.z, 20, 0.4, 0.4, 0.4, 0.2);
        playSound(SoundEvents.RAVAGER_HURT, 2.5F, 0.6F);
        playSound(SoundEvents.SLIME_BLOCK_BREAK, 2.0F, 0.5F);
        chunkDamage(0.04F);
        talk("head_cut");
        hintOnce(sl, "head_cut");
    }

    private int regrowTicks() {
        return phase() >= 2 ? 160 : 200;
    }

    /** Fogo com tocos abertos: cauteriza todos (para sempre). */
    private void searStumps(ServerLevel sl) {
        boolean any = false;
        for (int i = 0; i < SLOTS; i++) {
            if (slot(i) != STUMP) continue;
            setSlot(i, SEARED);
            regrow[i] = 0;
            any = true;
            Vec3 c = modelPoint(HEAD_REST[i][0] * 0.35, 36, -22, 1.0);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, c.x, c.y, c.z, 20, 0.3, 0.3, 0.3, 0.03);
            sl.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 16, 0.3, 0.2, 0.3, 0.02);
        }
        if (!any) return;
        playSound(SoundEvents.FIRE_EXTINGUISH, 2.0F, 0.6F);
        playSound(SoundEvents.RAVAGER_HURT, 2.5F, 0.5F);
        chunkDamage(0.04F);
        talk("seared");
        hintOnce(sl, "seared");
        if (aliveHeads() == 0) {
            announce("headless");
            hintOnce(sl, "headless");
        }
    }

    /** Pedaço fixo da vida máxima (cortar/cauterizar). Não mata por fora do dano normal: deixa 1 de vida. */
    private void chunkDamage(float frac) {
        setHealth(Math.max(1.0F, getHealth() - getMaxHealth() * frac));
    }

    private void regrowSlot(ServerLevel sl, int i) {
        setSlot(i, ALIVE);
        headHp[i] = headMaxHp();
        triggerAnim("head" + i, "regrow_" + i);
        int extra = -1;
        // A cabeça nova nasce no encaixe vazio mais perto do toco.
        for (int d = 1; d < SLOTS && extra < 0; d++) {
            for (int s : new int[] {i - d, i + d}) {
                if (s >= 0 && s < SLOTS && slot(s) == NONE) {
                    extra = s;
                    break;
                }
            }
        }
        if (extra >= 0 && aliveHeads() < SLOTS) {
            setSlot(extra, ALIVE);
            headHp[extra] = headMaxHp();
            triggerAnim("head" + extra, "regrow_" + extra);
        }
        Vec3 c = headPos(i);
        sl.sendParticles(BfFx.dust(0xE07A7A, 1.6F), c.x, c.y - 1.5, c.z, 30, 0.5, 1.0, 0.5, 0);
        playSound(SoundEvents.SLIME_BLOCK_PLACE, 2.0F, 0.4F);
        playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 1.2F);
        talk("regrow");
        hintOnce(sl, "regrow");
    }

    private void talk(String key) {
        if (talkCd > 0) return;
        talkCd = 80;
        announce(key);
    }

    private void tickHeads(ServerLevel sl) {
        if (talkCd > 0) talkCd--;
        // Pegando fogo (Em Chamas do núcleo ou fogo vanilla), ou partes em chamas (flecha flamejante na cabeça): cauteriza.
        boolean burning = isOnFire() || Statuses.has(this, Statuses.Kind.BURNING);
        for (BossPart p : bossParts()) {
            if (p.getRemainingFireTicks() > 0) {
                p.clearFire();
                burning = true;
                if (!isInWaterOrRain()) igniteForSeconds(3);
            }
        }
        if (burning) searStumps(sl);
        for (int i = 0; i < SLOTS; i++) {
            if (slot(i) != STUMP) continue;
            if (tickCount % 5 == 0) {
                Vec3 c = modelPoint(HEAD_REST[i][0] * 0.35, 38, -22, 1.0);
                sl.sendParticles(BfFx.dust(0xC2474A, 1.0F), c.x, c.y, c.z, 3, 0.2, 0.2, 0.2, 0);
            }
            if (--regrow[i] <= 0) regrowSlot(sl, i);
        }
        if (waterHintCd > 0) waterHintCd--;
        if (countSlots(STUMP) > 0 && isInWater() && waterHintCd <= 0) {
            waterHintCd = 400;
            hintOnce(sl, "water");
        }
    }

    // ------------------------------------------------------------------ Combate

    @Override
    protected boolean isBusy() {
        return tailTimer > 0 || breathTimer > 0 || spitTimer > 0 || chargeWind > 0 || chargeRun > 0 || state() != ST_NORMAL;
    }

    @Override
    protected void tickWhileHomeward(ServerLevel sl, @Nullable LivingEntity target) {
        tickHeads(sl);
    }

    @Override
    protected void tickCombat(ServerLevel sl, @Nullable LivingEntity target) {
        tickHeads(sl);
        int ph = phase();
        tailCd--;
        breathCd--;
        spitCd--;
        chargeCd--;
        ambushCd--;
        if (biteGap > 0) biteGap--;
        for (int i = 0; i < SLOTS; i++) {
            if (biteCd[i] > 0) biteCd[i]--;
            if (biting[i] > 0) biting[i]--;
        }

        if (state() != ST_NORMAL) {
            tickAmbush(sl, target);
            return;
        }
        if (tailTimer > 0) {
            getNavigation().stop();
            holdYaw(attackYaw);
            tailTimer--;
            return;
        }
        if (breathTimer > 0) {
            getNavigation().stop();
            holdYaw(attackYaw);
            if (--breathTimer < 6) breathFx(sl);
            return;
        }
        if (chargeWind > 0) {
            getNavigation().stop();
            holdYaw(attackYaw);
            if (--chargeWind == 0) chargeRun = chargeTicks;
            return;
        }
        if (chargeRun > 0) {
            runCharge(sl);
            return;
        }
        if (spitTimer > 0) {
            if (--spitTimer == 0 && target != null) spit(sl, target);
        }
        if (target == null) return;

        double dist = distanceTo(target);
        float ang = Math.abs(angleTo(target.position()));
        if (ph >= 2 && ambushCd <= 0 && dist < 26) {
            startAmbush(sl);
            return;
        }
        if (tailCd <= 0 && dist < 8.0 && (ang > 105F || aliveHeads() == 0)) {
            startTail(sl, ang <= 105F);
            return;
        }
        if (breathCd <= 0 && aliveHeads() >= 2 && dist < 10.5 && ang < 40F) {
            startBreath(sl, target);
            return;
        }
        if ((ph >= 1 || aliveHeads() == 0) && chargeCd <= 0 && dist > 7 && dist < 18 && hasLineOfSight(target)) {
            startCharge(sl, target);
            return;
        }
        if (ph >= 1 && spitCd <= 0 && spitTimer <= 0 && aliveHeads() > 0 && dist > 5) {
            spitTimer = 10;
            spitCd = ph >= 2 ? 120 : 170;
            int h = randomAliveHead();
            if (h >= 0) triggerAnim("head" + h, "spit_" + h);
            playSound(SoundEvents.LLAMA_SPIT, 2.0F, 0.5F);
        }
        tryBites(sl, target, ph);

        // Anda até ficar com as cabeças ao alcance; perto, só vira o corpo.
        if (dist > 5.5) chase(target, 1.0);
        else {
            getNavigation().stop();
            turnToward(target.position(), 5F);
        }
    }

    private void turnToward(Vec3 p, float step) {
        float want = yawTo(p);
        float yaw = Mth.approachDegrees(yBodyRot, want, step);
        setYRot(yaw);
        yBodyRot = yaw;
        yHeadRot = yaw;
    }

    private int randomAliveHead() {
        List<Integer> alive = new ArrayList<>();
        for (int i = 0; i < SLOTS; i++) if (slot(i) == ALIVE) alive.add(i);
        return alive.isEmpty() ? -1 : alive.get(random.nextInt(alive.size()));
    }

    // ------------------------------------------------------------------ Mordidas (por cabeça)

    private void tryBites(ServerLevel sl, LivingEntity target, int ph) {
        if (biteGap > 0) return;
        int concurrent = 0;
        for (int i = 0; i < SLOTS; i++) if (biting[i] > 0) concurrent++;
        int maxConcurrent = ph >= 2 ? 3 : 2;
        if (concurrent >= maxConcurrent) return;
        // Cabeça mais perto do alvo, pronta para morder.
        int best = -1;
        double bestD = BITE_REACH * BITE_REACH;
        for (int i = 0; i < SLOTS; i++) {
            if (slot(i) != ALIVE || biteCd[i] > 0 || biting[i] > 0) continue;
            double d = headPos(i).subtract(target.getX(), target.getY() + 1.0, target.getZ()).lengthSqr();
            if (d < bestD) {
                bestD = d;
                best = i;
            }
        }
        if (best < 0) return;
        startBite(sl, best, target, ph);
    }

    private void startBite(ServerLevel sl, int i, LivingEntity target, int ph) {
        Vec3 at = groundAt(sl, target.position(), 4);
        biting[i] = BITE_WARMUP + 6;
        biteCd[i] = (int) ((50 + random.nextInt(30)) * (ph >= 2 ? 0.7F : ph >= 1 ? 0.85F : 1.0F));
        biteGap = ph >= 2 ? 5 : 8;
        triggerAnim("head" + i, "bite_" + i);
        playSound(SoundEvents.RAVAGER_ATTACK, 1.4F, 1.3F + random.nextFloat() * 0.2F);
        float dmg = attackDamage() * 0.8F;
        int amp = ph >= 1 ? 1 : 0;
        int[] id = new int[1];
        id[0] = Telegraph.delayed(sl, Telegraph.Shape.circle(at, BITE_RADIUS).warmup(BITE_WARMUP).color(Telegraph.DANGER).owner(this).noRunes(),
                hits -> {
                    if (biteTele[i] == id[0]) biteTele[i] = -1;
                    if (!isAlive() || slot(i) != ALIVE) return;
                    for (LivingEntity e : hits) {
                        if (!isFoe(e)) continue;
                        if (e.hurt(damageSources().mobAttack(this), dmg)) e.addEffect(new MobEffectInstance(MobEffects.POISON, 80, amp), this);
                    }
                    sl.sendParticles(BfFx.dust(VENOM, 1.4F), at.x, at.y + 0.6, at.z, 14, 0.6, 0.3, 0.6, 0);
                    sl.sendParticles(ParticleTypes.CRIT, at.x, at.y + 0.8, at.z, 8, 0.5, 0.3, 0.5, 0.2);
                    playSound(SoundEvents.EVOKER_FANGS_ATTACK, 1.2F, 0.7F);
                });
        biteTele[i] = id[0];
    }

    // ------------------------------------------------------------------ Varrida de Cauda

    /** {@code around}: sem cabeças e com o alvo na frente, a cauda gira o corpo inteiro (círculo em volta). */
    private void startTail(ServerLevel sl, boolean around) {
        tailTimer = TAIL_WARMUP + 4;
        tailCd = phase() >= 1 ? 70 : 90;
        attackYaw = yBodyRot;
        action("tail");
        playSound(SoundEvents.RAVAGER_STEP, 2.0F, 0.6F);
        float dmg = attackDamage() * 1.1F;
        Vec3 c = position();
        Telegraph.Shape shape = around ? Telegraph.Shape.circle(c, 6.0F) : Telegraph.Shape.cone(c, attackYaw + 180F, 150F, 7.5F);
        Telegraph.delayed(sl, shape.warmup(TAIL_WARMUP).color(Telegraph.WARNING).owner(this).castName(attackName("tail")),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (!isFoe(e)) continue;
                        if (e.hurt(damageSources().mobAttack(this), dmg)) {
                            Vec3 push = e.position().subtract(c).multiply(1, 0, 1).normalize();
                            e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.5, 0.5, push.z * 1.5));
                            e.hurtMarked = true;
                        }
                    }
                    Vec3 back = c.add(Vec3.directionFromRotation(0, attackYaw + 180F).scale(4));
                    BfFx.groundBurst(sl, back, 4.0, 30);
                    playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.5F);
                });
    }

    // ------------------------------------------------------------------ Sopro Pestilento

    private void startBreath(ServerLevel sl, LivingEntity target) {
        int alive = aliveHeads();
        breathTimer = BREATH_WARMUP + 10;
        breathCd = phase() >= 2 ? 170 : phase() >= 1 ? 210 : 250;
        attackYaw = yawTo(target.position());
        holdYaw(attackYaw);
        getNavigation().stop();
        for (int i = 0; i < SLOTS; i++) if (slot(i) == ALIVE) triggerAnim("head" + i, "breath_" + i);
        announce("breath");
        playSound(SoundEvents.RAVAGER_ROAR, 2.0F, 1.4F);
        float angle = Math.min(110F, 40F + 14F * alive);
        float dmg = scaled(5.0F + alive);
        Vec3 o = position();
        Telegraph.delayed(sl, Telegraph.Shape.cone(o, attackYaw, angle, 11F).warmup(BREATH_WARMUP).color(GAS).owner(this)
                        .castName(attackName("breath")),
                hits -> {
                    if (!isAlive()) return;
                    for (LivingEntity e : hits) {
                        if (!isFoe(e)) continue;
                        e.hurt(damageSources().indirectMagic(this, this), dmg);
                        e.addEffect(new MobEffectInstance(MobEffects.POISON, 120, 1), this);
                    }
                    playSound(SoundEvents.DRAGON_FIREBALL_EXPLODE, 1.6F, 0.6F);
                });
    }

    private void breathFx(ServerLevel sl) {
        Vec3 dir = Vec3.directionFromRotation(0, attackYaw);
        for (int i = 0; i < SLOTS; i++) {
            if (slot(i) != ALIVE) continue;
            Vec3 h = headPos(i);
            for (int k = 0; k < 4; k++) {
                double sp = 0.4 + random.nextDouble() * 0.5;
                Vec3 v = dir.add((random.nextDouble() - 0.5) * 0.7, -0.25, (random.nextDouble() - 0.5) * 0.7).normalize().scale(sp);
                sl.sendParticles(BfFx.dust(GAS, 2.2F), h.x, h.y, h.z, 0, v.x, v.y, v.z, 1.0);
                sl.sendParticles(ParticleTypes.SNEEZE, h.x, h.y, h.z, 0, v.x, v.y, v.z, 1.0);
            }
        }
    }

    // ------------------------------------------------------------------ Cuspe Venenoso

    private void spit(ServerLevel sl, LivingEntity target) {
        List<LivingEntity> targets = new ArrayList<>();
        targets.add(target);
        if (phase() >= 2) {
            for (ServerPlayer p : BossKit.partyAround(this, 24)) {
                if (targets.size() >= 3) break;
                if (p != target) targets.add(p);
            }
        }
        float dmg = scaled(8.0F);
        int shots = phase() >= 2 ? 3 : 1;
        for (int k = 0; k < shots; k++) {
            LivingEntity t = targets.get(k % targets.size());
            int h = randomAliveHead();
            if (h < 0) return;
            Vec3 from = headPos(h);
            Vec3 aim = t.position().add(t.getDeltaMovement().multiply(10, 0, 10));
            if (k >= targets.size()) aim = aim.add((random.nextDouble() - 0.5) * 6, 0, (random.nextDouble() - 0.5) * 6);
            Vec3 land = groundAt(sl, aim, 6);
            int ticks = HydraVenomGlob.throwAt(this, from, land, dmg);
            Telegraph.show(sl, Telegraph.Shape.circle(land, HydraVenomGlob.RADIUS).warmup(ticks).hold(HydraVenomGlob.POOL_TICKS)
                    .color(GAS).owner(this).noRunes());
        }
        playSound(SoundEvents.LLAMA_SPIT, 2.0F, 0.4F);
    }

    // ------------------------------------------------------------------ Investida

    private void startCharge(ServerLevel sl, LivingEntity target) {
        attackYaw = yawTo(target.position());
        chargeDir = Vec3.directionFromRotation(0, attackYaw);
        Vec3 from = position();
        Vec3 to = lineEnd(sl, from, chargeDir, 18.0);
        // Corre exatamente o comprimento da faixa marcada (0,85 bloco/tick).
        chargeTicks = Math.max(4, (int) Math.ceil(from.distanceTo(to) / 0.85));
        chargeWind = CHARGE_WARMUP;
        chargeCd = phase() >= 2 ? 180 : 240;
        chargeHit.clear();
        holdYaw(attackYaw);
        getNavigation().stop();
        action("charge");
        announce("charge");
        playSound(SoundEvents.RAVAGER_ROAR, 2.2F, 0.8F);
        Telegraph.show(sl, Telegraph.Shape.line(from, to, getBbWidth() + 0.8F).warmup(CHARGE_WARMUP).hold(chargeTicks).color(Telegraph.WARNING).owner(this));
        chargeRun = 0;
    }

    private void runCharge(ServerLevel sl) {
        chargeRun--;
        holdYaw(attackYaw);
        Vec3 before = position();
        setDeltaMovement(chargeDir.x * 0.85, getDeltaMovement().y, chargeDir.z * 0.85);
        if (tickCount % 2 == 0) BfFx.groundBurst(sl, position(), 2.0, 10);
        float dmg = attackDamage() * 1.2F;
        AABB box = getBoundingBox().inflate(0.6);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, this::isFoe)) {
            if (!chargeHit.add(e.getUUID())) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                e.setDeltaMovement(e.getDeltaMovement().add(chargeDir.x * 1.2, 0.7, chargeDir.z * 1.2));
                e.hurtMarked = true;
            }
        }
        // Bateu em algo (parede/fim da linha): para com um tremor.
        if (chargeRun <= 0 || (tickCount % 2 == 0 && horizontalCollision && before.distanceToSqr(position()) < 0.01)) {
            chargeRun = 0;
            setDeltaMovement(Vec3.ZERO);
            BossKit.shake(sl, position(), 18, 0.9F, 10);
            playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.0F, 0.7F);
        }
    }

    // ------------------------------------------------------------------ Emboscada do Lodo

    private void startAmbush(ServerLevel sl) {
        ambushCd = 360 + random.nextInt(80);
        entityData.set(DATA_STATE, ST_SINK);
        stateTimer = SINK_TICKS;
        getNavigation().stop();
        Telegraph.cancelAll(this);
        for (int i = 0; i < SLOTS; i++) {
            biting[i] = 0;
            biteTele[i] = -1;
        }
        announce("ambush");
        playSound(SoundEvents.MUD_BREAK, 2.5F, 0.4F);
        playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2.0F, 0.5F);
    }

    private void tickAmbush(ServerLevel sl, @Nullable LivingEntity target) {
        getNavigation().stop();
        stateTimer--;
        if (tickCount % 3 == 0) {
            sl.sendParticles(ParticleTypes.BUBBLE_POP, getX(), getY() + 0.3, getZ(), 12, 1.5, 0.1, 1.5, 0.05);
            BfFx.groundBurst(sl, position(), 3.0, 8);
        }
        switch (state()) {
            case ST_SINK -> {
                if (stateTimer <= 0) {
                    entityData.set(DATA_STATE, ST_HIDDEN);
                    stateTimer = AMBUSH_WARMUP + 2;
                    ambushAt = null;
                    LivingEntity t = target != null ? target : sl.getNearestPlayer(this, 30);
                    // Sem alvo: sobe onde está quando o tempo acabar (ver ST_HIDDEN).
                    if (t == null || (t instanceof Player p && !validPlayer(p))) return;
                    int[] id = new int[1];
                    id[0] = Telegraph.delayed(sl, Telegraph.Shape.follow(t, 3.6F).warmup(AMBUSH_WARMUP).lock(14).color(GAS).owner(this)
                                    .castName(attackName("ambush")),
                            hits -> {
                                Vec3 at = Telegraph.centerOf(id[0]);
                                if (at != null) ambushAt = at;
                                emerge(sl, hits);
                            });
                }
            }
            case ST_HIDDEN -> {
                // Bolhas andando até o alvo (dá para ver por onde ela vem).
                if (target != null && tickCount % 2 == 0) {
                    Vec3 dir = target.position().subtract(position()).multiply(1, 0, 1);
                    double d = dir.length();
                    if (d > 0.5) {
                        Vec3 p = position().add(dir.scale(Math.min(1.0, (AMBUSH_WARMUP - stateTimer) / (double) AMBUSH_WARMUP)));
                        sl.sendParticles(ParticleTypes.BUBBLE_POP, p.x, p.y + 0.2, p.z, 4, 0.6, 0.05, 0.6, 0.02);
                    }
                }
                if (stateTimer <= 0 && ambushAt == null) {
                    // Sem alvo/marca: sobe onde está.
                    emerge(sl, List.of());
                }
            }
            case ST_RISE -> {
                if (stateTimer <= 0) entityData.set(DATA_STATE, ST_NORMAL);
            }
            default -> entityData.set(DATA_STATE, ST_NORMAL);
        }
    }

    private void emerge(ServerLevel sl, List<LivingEntity> hits) {
        if (!isAlive() || state() != ST_HIDDEN) return;
        Vec3 at = ambushAt != null ? groundAt(sl, ambushAt, 6) : position();
        teleportTo(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        entityData.set(DATA_STATE, ST_RISE);
        stateTimer = RISE_TICKS;
        float dmg = scaled(14.0F);
        for (LivingEntity e : hits) {
            if (!isFoe(e)) continue;
            if (e.hurt(damageSources().mobAttack(this), dmg)) {
                e.setDeltaMovement(e.getDeltaMovement().add(0, 0.95, 0));
                e.hurtMarked = true;
                e.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 1), this);
            }
        }
        BfFx.groundBurst(sl, at, 4.0, 80);
        sl.sendParticles(ParticleTypes.SPLASH, at.x, at.y + 0.5, at.z, 60, 2.0, 0.5, 2.0, 0.3);
        playSound(SoundEvents.GENERIC_SPLASH, 2.5F, 0.5F);
        playSound(SoundEvents.RAVAGER_ROAR, 2.5F, 0.7F);
        BossKit.shake(sl, at, 26, 1.4F, 16);
        ambushAt = null;
    }

    // ------------------------------------------------------------------ Fases, reset, loot

    @Override
    protected void onPhaseChange(ServerLevel level, int phase) {
        super.onPhaseChange(level, phase);
        action("roar");
        BossKit.roar(this, SoundEvents.RAVAGER_ROAR, 0.6F);
        BossKit.shake(level, position(), 28, 1.3F, 20);
        if (phase >= 2 && !sproutedP3 && aliveHeads() < 3) {
            // Fúria: brotam cabeças nos encaixes vazios (não nos cauterizados).
            sproutedP3 = true;
            int added = 0;
            for (int i : new int[] {2, 1, 3, 0, 4}) {
                if (added >= 2) break;
                if (slot(i) == NONE) {
                    setSlot(i, ALIVE);
                    headHp[i] = headMaxHp();
                    triggerAnim("head" + i, "regrow_" + i);
                    added++;
                }
            }
            if (added > 0) announce("sprout");
        }
        if (phase >= 2) ambushCd = Math.min(ambushCd, 80);
        spitCd = Math.min(spitCd, 40);
        chargeCd = Math.min(chargeCd, 100);
    }

    @Override
    protected void onIntroEnd(ServerLevel level) {
        for (int i = 0; i < SLOTS; i++) if (slot(i) == ALIVE && headHp[i] <= 0) headHp[i] = headMaxHp();
    }

    @Override
    protected void startIntro(ServerLevel sl) {
        super.startIntro(sl);
        // A escala de grupo mudou a vida máxima: cabeças cheias de acordo.
        for (int i = 0; i < SLOTS; i++) headHp[i] = headMaxHp();
    }

    @Override
    protected void onIntroStart(ServerLevel level) {
        playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.5F);
        playSound(SoundEvents.PLAYER_SPLASH_HIGH_SPEED, 2.0F, 0.5F);
    }

    @Override
    protected void introTick(ServerLevel level, int t) {
        if (t % 4 == 0) {
            level.sendParticles(ParticleTypes.SPLASH, getX(), getY() + 0.4, getZ(), 20, 2.0, 0.3, 2.0, 0.2);
            BfFx.groundBurst(level, position(), 3.0, 10);
        }
        if (t == 40) {
            playSound(SoundEvents.RAVAGER_ROAR, 3.0F, 0.6F);
            BossKit.shake(level, position(), 28, 1.2F, 16);
        }
    }

    @Override
    protected void onReset(ServerLevel level) {
        entityData.set(DATA_HEADS, initialHeads());
        entityData.set(DATA_STATE, ST_NORMAL);
        for (int i = 0; i < SLOTS; i++) {
            headHp[i] = headMaxHp();
            regrow[i] = 0;
            biteCd[i] = 0;
            biting[i] = 0;
            biteTele[i] = -1;
        }
        tailTimer = breathTimer = spitTimer = chargeWind = chargeRun = 0;
        tailCd = 40;
        breathCd = 160;
        spitCd = 120;
        chargeCd = 160;
        ambushCd = 200;
        sproutedP3 = false;
        ambushAt = null;
        clearFire();
    }

    @Override
    protected RawAnimation mainAnimation(boolean moving) {
        if (!isDeadOrDying() && introTicks() <= 0) {
            switch (state()) {
                case ST_SINK -> {
                    return hold("submerge");
                }
                case ST_HIDDEN -> {
                    return loop("hidden");
                }
                case ST_RISE -> {
                    return play("emerge");
                }
                default -> {
                }
            }
        }
        return super.mainAnimation(moving);
    }

    @Override
    protected void addExtraControllers(AnimatableManager.ControllerRegistrar controllers) {
        for (int i = 0; i < SLOTS; i++) {
            AnimationController<SwampHydra> c = new AnimationController<>(this, "head" + i, 2, state -> PlayState.STOP);
            for (String a : new String[] {"bite_", "breath_", "spit_", "regrow_"}) c.triggerableAnim(a + i, play(a + i));
            controllers.add(c);
        }
    }

    @Override
    protected void dropBossLoot(ServerLevel level, DamageSource source) {
        int n = trophyCount();
        dropItem(BfRegistry.HYDRA_HEART.get(), n, n);
        dropItem(BfRegistry.HYDRA_FANG.get(), 2 + n, 4 + n);
        dropEssence(Element.WATER, 3, 5);
        dropEssence(Element.EARTH, 1, 2);
        dropArcaneScrolls(2);
    }

    @Override
    protected void clientTick() {
        int every = BfFx.reduced() ? 8 : 3;
        for (int i = 0; i < SLOTS; i++) {
            if (random.nextInt(every) != 0) continue;
            int s = slot(i);
            BossPart h = bossParts()[i];
            if (s == ALIVE && state() == ST_NORMAL) {
                level().addParticle(BfFx.dust(VENOM, 0.8F), h.getX() + (random.nextDouble() - 0.5) * 0.4, h.getY() + 0.1,
                        h.getZ() + (random.nextDouble() - 0.5) * 0.4, 0, -0.05, 0);
            } else if (s == SEARED && random.nextInt(3) == 0) {
                Vec3 c = modelPoint(HEAD_REST[i][0] * 0.35, 37, -22, 1.0);
                level().addParticle(ParticleTypes.SMOKE, c.x, c.y, c.z, 0, 0.03, 0);
            } else if (s == STUMP) {
                Vec3 c = modelPoint(HEAD_REST[i][0] * 0.35, 38, -22, 1.0);
                level().addParticle(BfFx.dust(0xC2474A, 0.9F), c.x, c.y, c.z, 0, 0.02, 0);
            }
        }
    }

    // ------------------------------------------------------------------ Sons e dados

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.65F;
    }

    @Override
    protected int deathTicks() {
        return 50;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        CompoundTag h = new CompoundTag();
        h.putInt("Slots", entityData.get(DATA_HEADS));
        for (int i = 0; i < SLOTS; i++) {
            h.putFloat("Hp" + i, headHp[i]);
            h.putInt("Regrow" + i, regrow[i]);
        }
        h.putBoolean("Sprouted", sproutedP3);
        tag.put("HydraHeads", h);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (!tag.contains("HydraHeads")) return;
        CompoundTag h = tag.getCompound("HydraHeads");
        entityData.set(DATA_HEADS, h.getInt("Slots"));
        for (int i = 0; i < SLOTS; i++) {
            headHp[i] = h.getFloat("Hp" + i);
            regrow[i] = h.getInt("Regrow" + i);
        }
        sproutedP3 = h.getBoolean("Sprouted");
    }
}
