package com.renanpontes.isekaimahou.feature.legends_d.entity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicHover;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.legends_d.LdNet;
import com.renanpontes.isekaimahou.feature.legends_d.LdRegistry;
import com.renanpontes.isekaimahou.feature.legends_d.LdUtil;
import com.renanpontes.isekaimahou.feature.legends_d.atomic.AtomicBubble;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * EU SOU ATÔMICO (v0.9, melhorado). Carga de 3 s ({@link #CHARGE}): o conjurador flutua, anéis azul-violeta em camadas
 * giram em volta dele, a luz é sugada para o centro, o letreiro "Eu..." "...sou..." aparece letra a letra e a câmera
 * treme cada vez mais; quem está perto recebe "Abriguem-se!" com a contagem (é uma conjuração ULTIMATE na Pilha: dá
 * para contrariar!). Meio segundo antes, os ALIADOS entram numa bolha protetora azul ({@link AtomicBubble}).
 * Detonação: "ATÔMICO!", clarão, cúpula azul-violeta em camadas que se expande até o raio final causando dano enorme
 * que ignora armadura em quem a frente da onda toca, anel de choque correndo pelo chão, nuvem em cogumelo subindo,
 * cratera gigante ({@link Destruction#crater}) e tremor. Quem está na bolha não leva dano, não é empurrado e, se o
 * chão sumir, flutua 3 s e desce devagar.
 */
public class AtomicNovaEntity extends LdSpellEntity {
    public static final int CHARGE = 60;
    public static final int EXPAND = 18;
    public static final int HOLD = 12;
    public static final int FADE = 44;
    /** Quantos ticks antes da detonação as bolhas dos aliados se formam. */
    public static final int BUBBLE_LEAD = 10;
    public static final int VIOLET = 0x8A5CFF;
    public static final int BLUE = 0x4A7BFF;
    /** Golpes por tick no máximo (multidões: o resto é atingido nos ticks seguintes). */
    private static final int MAX_HITS_PER_TICK = 48;
    /** Bolhas por explosão no máximo. */
    private static final int MAX_BUBBLES = 40;

    /** Conjuradores com uma carga em andamento (uma por vez) e janela de imunidade à própria explosão. */
    private static final Map<UUID, Long> CHARGING = new HashMap<>();
    private static final Map<UUID, Long> IMMUNE = new HashMap<>();

    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(AtomicNovaEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Boolean> DATA_BLAST = SynchedEntityData.defineId(AtomicNovaEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_FOLLOW = SynchedEntityData.defineId(AtomicNovaEntity.class, EntityDataSerializers.BOOLEAN);

    private SpellStats stats = new SpellStats();
    private final Set<UUID> hit = new HashSet<>();
    @Nullable
    private SpellStack.Pending pending;
    private boolean aborted;
    private boolean finished;
    private double liftBaseY = Double.NaN;
    private double groundY = Double.NaN;
    /** Só no cliente: altura do chão sob o ponto (o renderer procura uma vez). */
    public double clientGround = Double.NaN;

    public AtomicNovaEntity(EntityType<? extends AtomicNovaEntity> type, Level level) {
        super(type, level);
    }

    /**
     * @param follow true = a explosão sai do próprio conjurador (Si Mesmo): o ponto acompanha o corpo dele no ar
     */
    public static AtomicNovaEntity create(SpellContext ctx, Vec3 center, float radius, SpellStats stats, boolean follow) {
        AtomicNovaEntity e = new AtomicNovaEntity(LdRegistry.ATOMIC_NOVA.get(), ctx.level());
        e.bind(ctx, VIOLET);
        e.stats = stats.copy();
        e.setPos(center.x, center.y, center.z);
        e.entityData.set(DATA_RADIUS, radius);
        e.entityData.set(DATA_FOLLOW, follow);
        return e;
    }

    /** Há uma carga deste conjurador no mundo agora? (com prazo de segurança: nunca trava para sempre) */
    public static boolean isCharging(UUID caster, long now) {
        Long since = CHARGING.get(caster);
        if (since == null) return false;
        if (now - since > CHARGE + 40) {
            CHARGING.remove(caster);
            return false;
        }
        return true;
    }

    /** O conjurador está protegido da própria explosão agora? */
    public static boolean immune(LivingEntity e) {
        Long until = IMMUNE.get(e.getUUID());
        if (until == null) return false;
        if (e.level().getGameTime() > until) {
            IMMUNE.remove(e.getUUID());
            return false;
        }
        return true;
    }

    public static void clearAll() {
        CHARGING.clear();
        IMMUNE.clear();
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 12F);
        builder.define(DATA_BLAST, false);
        builder.define(DATA_FOLLOW, false);
    }

    public float radius() { return entityData.get(DATA_RADIUS); }

    public boolean blasting() { return entityData.get(DATA_BLAST); }

    public boolean follows() { return entityData.get(DATA_FOLLOW); }

    /** 0..1 da carga. */
    public float charge(float ageF) {
        return Mth.clamp(ageF / CHARGE, 0F, 1F);
    }

    /** Raio atual da esfera (detonação). */
    public float blastRadius(float ageF) {
        float t = ageF - CHARGE;
        float grow = LdUtil.easeOut(t / EXPAND);
        float drift = t > EXPAND ? 1F + 0.08F * Mth.clamp((t - EXPAND) / (HOLD + FADE), 0F, 1F) : 1F;
        return radius() * Math.max(0.02F, grow) * drift;
    }

    /** 0..1: intensidade da esfera (some no fim). */
    public float blastAlpha(float ageF) {
        float t = ageF - CHARGE;
        if (t < EXPAND + HOLD) return 1F;
        return Mth.clamp(1F - (t - EXPAND - HOLD) / FADE, 0F, 1F);
    }

    /** 0..1: intensidade da cúpula (some antes da nuvem). */
    public float domeAlpha(float ageF) {
        float t = ageF - CHARGE;
        if (t < EXPAND + HOLD) return 1F;
        return Mth.clamp(1F - (t - EXPAND - HOLD) / (FADE * 0.6F), 0F, 1F);
    }

    /** 0..1: quanto a nuvem em cogumelo já subiu. */
    public float cloudRise(float ageF) {
        float t = ageF - CHARGE - EXPAND * 0.6F;
        return LdUtil.easeOut(t / (HOLD + FADE * 0.7F));
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================
    @Override
    protected void serverTick(ServerLevel sl, SpellContext c) {
        LivingEntity caster = c.caster();
        boolean casterOk = caster != null && caster.isAlive() && !caster.isRemoved() && caster.level() == sl;
        if (!blasting()) {
            if (age == 1) begin(sl, caster);
            if (aborted) {
                fizzle(sl, caster);
                return;
            }
            if (follows()) {
                if (!casterOk) {
                    fizzle(sl, caster);
                    return;
                }
                Vec3 at = LdUtil.center(caster);
                setPos(at.x, at.y, at.z);
            }
            if (casterOk) lift(caster);
            if (age == 1 || age == 20 || age == 40) warn(sl, c, casterOk ? caster : null);
            if (age == CHARGE - BUBBLE_LEAD) raiseBubbles(sl, c, casterOk ? caster : null);
            chargeFx(sl, casterOk ? caster : null);
            if (age >= CHARGE) detonate(sl, casterOk ? caster : null);
            return;
        }
        int t = age - CHARGE;
        if (t <= EXPAND + 3) {
            float cur = blastRadius(age);
            wave(sl, c, cur, caster);
            if (t <= EXPAND) shellFx(sl, cur, t);
        }
        if (t > EXPAND * 0.6F && t < EXPAND + HOLD + FADE - 6) cloudFx(sl, t);
        if (t > EXPAND + HOLD + FADE) discard();
    }

    private void begin(ServerLevel sl, @Nullable LivingEntity caster) {
        float r = radius();
        Vec3 ground = LdUtil.groundBelow(sl, position(), 64);
        groundY = ground.y;
        Telegraph.show(sl, Telegraph.Shape.circle(ground, r).warmup(CHARGE).color(VIOLET));
        if (caster != null) {
            liftBaseY = caster.getY();
            pending = SpellStack.push(sl, caster, position(), r, CHARGE, SpellStack.Tier.ULTIMATE,
                    Component.translatable("glyph.isekai_mahou.atomic"), Element.ARCANE, () -> aborted = true);
            if (caster instanceof Player p) MagicHover.allow(p, CHARGE + 240);
        }
        LdNet.near(sl, position(), 112, new LdNet.Fx(LdNet.ATOMIC_TEXT, position(), r, caster != null ? caster.getId() : getId(), CHARGE));
        LdUtil.sound(sl, position(), SoundEvents.BEACON_ACTIVATE, 3.0F, 0.6F);
        LdUtil.sound(sl, position(), SoundEvents.RESPAWN_ANCHOR_CHARGE, 2.0F, 0.6F);
        // v1.3: selo gigante no chão durante a carga inteira + luz do mundo convergindo para o ponto (montado no cliente)
        ElementFx.runeCircle(sl, ground.add(0, 0.05, 0), VIOLET, Math.min(40F, r), CHARGE + 10, 5);
        ElementFx.gatherAt(sl, position(), Element.ARCANE, VIOLET, Math.min(12F, 4F + r * 0.2F), 5, CHARGE);
    }

    /**
     * "Abriguem-se!": todo jogador perto (menos quem conjura) recebe o aviso com a contagem — os aliados sabem que vão
     * ficar na bolha; os outros, que precisam sair do raio. Repetido a cada segundo para quem chegou depois.
     */
    private void warn(ServerLevel sl, SpellContext c, @Nullable LivingEntity caster) {
        Vec3 center = position();
        float r = radius();
        double range = r * 1.6 + 24;
        int left = Math.max(1, CHARGE - age);
        int casterNet = caster != null ? caster.getId() : getId();
        for (ServerPlayer sp : sl.players()) {
            if (sp == caster || sp.isSpectator() || sp.distanceToSqr(center) > range * range) continue;
            boolean safe = AtomicBubble.protects(c, caster, sp);
            LdNet.to(sp, new LdNet.Fx(safe ? LdNet.ATOMIC_WARN_SAFE : LdNet.ATOMIC_WARN_DANGER, center, r, casterNet, left));
            sp.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 1.0F, safe ? 0.9F : 0.6F);
            if (!safe) sp.playNotifySound(SoundEvents.NOTE_BLOCK_DIDGERIDOO.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
        }
    }

    /**
     * Meio segundo antes da detonação: a bolha azul se forma em cada aliado perto do raio — e também na borda
     * chamuscada da cratera (até 1,6× o raio dela, onde o chão pega fogo).
     */
    private void raiseBubbles(ServerLevel sl, SpellContext c, @Nullable LivingEntity caster) {
        UUID owner = casterId != null ? casterId : getUUID();
        Vec3 center = position();
        float r = radius();
        float reach = Math.max(r + 6F, Math.min(48F, r * 0.85F) * 1.6F + 3F);
        long until = sl.getGameTime() + BUBBLE_LEAD + EXPAND + HOLD;
        int n = 0;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(reach),
                e -> e.isAlive() && !e.isSpectator())) {
            if (n >= MAX_BUBBLES) break;
            if (LdUtil.center(e).distanceToSqr(center) > reach * reach) continue;
            if (!AtomicBubble.protects(c, caster, e)) continue;
            if (AtomicBubble.shield(sl, e, owner, until)) n++;
        }
    }

    /** O conjurador sobe ~3 blocos e fica flutuando durante a carga. */
    private void lift(LivingEntity caster) {
        if (Double.isNaN(liftBaseY)) liftBaseY = caster.getY();
        double dy = liftBaseY + 3.0 - caster.getY();
        caster.setDeltaMovement(0, Mth.clamp(dy * 0.25, -0.12, 0.32), 0);
        caster.fallDistance = 0F;
        caster.hurtMarked = true;
        if (caster instanceof Mob m) m.getNavigation().stop();
    }

    private void chargeFx(ServerLevel sl, @Nullable LivingEntity caster) {
        Vec3 c = position();
        float k = charge(age);
        // A luz do mundo sendo sugada para o centro (cada vez mais forte)
        if (age % 2 == 0) {
            for (int i = 0; i < 3 + (int) (k * 6); i++) {
                Vec3 off = randomDir().scale(4 + random.nextDouble() * (3 + 6 * k));
                LdUtil.moving(sl, Vfx.portal(), c, off, 1.0);
            }
            LdUtil.send(sl, LdUtil.dust(random.nextBoolean() ? VIOLET : BLUE, 1.6F), c, 4, 0.6 + k, 0);
            // Poeira do chão subindo em espiral para o ponto
            if (!Double.isNaN(groundY) && k > 0.3F) {
                double ang = age * 0.6;
                double rr = 3 + 5 * (1 - k);
                Vec3 g = new Vec3(c.x + Math.cos(ang) * rr, groundY + 0.2, c.z + Math.sin(ang) * rr);
                LdUtil.moving(sl, Vfx.reversePortal(), g, c.subtract(g).normalize(), 0.6);
                LdUtil.send(sl, Vfx.cloud(), g, 1, 0.3, 0.02);
            }
        }
        if (age % 4 == 0) LdUtil.send(sl, ParticleTypes.ELECTRIC_SPARK, c, 6 + (int) (k * 6), 0.8 + k * 1.5, 0.15);
        // Anéis de partículas em camadas em volta do conjurador (o renderer desenha os anéis de verdade)
        if (caster != null && age % 3 == 0) {
            Vec3 cc = LdUtil.center(caster);
            double a = age * 0.35;
            for (int layer = 0; layer < 3; layer++) {
                double rr = 1.6 + layer * 0.8 + k;
                double dir = layer % 2 == 0 ? 1 : -1;
                double ang = a * dir + layer * 1.3;
                Vec3 p = cc.add(Math.cos(ang) * rr, (layer - 1) * 0.7, Math.sin(ang) * rr);
                LdUtil.send(sl, LdUtil.dust(layer == 1 ? BLUE : VIOLET, 1.2F), p, 1, 0.02, 0);
            }
        }
        // "Eu..." · "...sou..." · o ar treme cada vez mais antes do fim
        if (age == 4) LdUtil.sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 2.5F, 0.7F);
        if (age == 24) {
            LdUtil.sound(sl, c, SoundEvents.AMETHYST_BLOCK_CHIME, 2.5F, 0.5F);
            LdUtil.sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.5F);
        }
        if (age % 10 == 0) LdUtil.sound(sl, c, SoundEvents.BEACON_AMBIENT, 2.0F + 2.0F * k, 0.5F + 0.9F * k);
        if (age == CHARGE - 20) LdUtil.sound(sl, c, SoundEvents.WARDEN_SONIC_CHARGE, 4.0F, 0.7F);
        if (age == CHARGE - BUBBLE_LEAD) LdUtil.sound(sl, c, SoundEvents.CONDUIT_ACTIVATE, 3.0F, 0.6F);
        if (age % 5 == 0) BossKit.shake(sl, c, radius() * 2 + 24, 0.08F + 1.1F * k * k, 8);
    }

    private void detonate(ServerLevel sl, @Nullable LivingEntity caster) {
        entityData.set(DATA_BLAST, true);
        syncAge();
        if (pending != null) {
            SpellStack.resolve(pending);
            pending = null;
        }
        if (casterId != null) CHARGING.remove(casterId);
        Vec3 c = position();
        float r = radius();
        if (caster != null) {
            IMMUNE.put(caster.getUUID(), sl.getGameTime() + EXPAND + HOLD + FADE + 120);
            caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, true, false, true));
        }
        LdNet.near(sl, c, Math.max(176, r * 4), new LdNet.Fx(LdNet.ATOMIC_FLASH, c, r, caster != null ? caster.getId() : getId(), 40));
        LdUtil.sound(sl, c, SoundEvents.GENERIC_EXPLODE, 12.0F, 0.45F);
        LdUtil.sound(sl, c, SoundEvents.WARDEN_SONIC_BOOM, 8.0F, 0.6F);
        LdUtil.sound(sl, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 12.0F, 0.55F);
        LdUtil.sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 5.0F, 0.5F);
        LdUtil.sound(sl, c, SoundEvents.DRAGON_FIREBALL_EXPLODE, 8.0F, 0.5F);
        LdUtil.send(sl, ParticleTypes.EXPLOSION_EMITTER, c, 4, 2.0, 0);
        LdUtil.send(sl, ParticleTypes.FLASH, c, 6, 1.5, 0);
        LdUtil.send(sl, ParticleTypes.SONIC_BOOM, c, 8, 2.5, 0);
        LdUtil.send(sl, Vfx.endRod(), c, 30, 1.0, 1.2);
        // v1.3: nova arcana + pilar de luz violeta (clarões, ondas, detritos e rescaldo pelo preset gráfico)
        ElementFx.nova(sl, c, Element.ARCANE, VIOLET, Math.min(40F, r * 0.7F), 5, false);
        ElementFx.pillar(sl, c, BLUE, Math.max(1.5F, r * 0.12F), 64F, 40);
        BossKit.shake(sl, c, r * 3 + 32, 3.0F, 60);
        Destruction.crater(sl, c, Math.min(48F, r * 0.85F), caster);
    }

    /** A frente da onda: quem ela toca pela primeira vez leva o golpe (e voa para fora). Aliados: bolha. */
    private void wave(ServerLevel sl, SpellContext c, float cur, @Nullable LivingEntity caster) {
        Vec3 center = position();
        float r = radius();
        int hits = 0;
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(cur + 2),
                e -> e.isAlive() && !e.isSpectator() && !hit.contains(e.getUUID()))) {
            double d = LdUtil.center(e).distanceTo(center);
            if (d > cur + e.getBbWidth() * 0.5) continue;
            hit.add(e.getUUID());
            if (AtomicBubble.protects(c, caster, e)) {
                // Chegou depois (ou não coube na lista): a bolha se forma na hora em que a onda passa
                if (!AtomicBubble.inBubble(e)) {
                    AtomicBubble.shield(sl, e, casterId != null ? casterId : getUUID(),
                            sl.getGameTime() + Math.max(10, EXPAND + HOLD - (age - CHARGE)));
                }
                continue;
            }
            if (hits++ >= MAX_HITS_PER_TICK) {
                hit.remove(e.getUUID());
                continue;
            }
            float ratio = (float) Mth.clamp(d / Math.max(1F, r), 0, 1);
            float falloff = ratio < 0.55F ? 1F : Mth.lerp((ratio - 0.55F) / 0.45F, 1F, 0.45F);
            boolean boss = LdUtil.isBoss(e);
            float base = (55F + e.getMaxHealth() * (boss ? 0.10F : 0.30F)) * falloff;
            LdUtil.pierceDamage(c, e, base, stats);
            Vec3 out = LdUtil.center(e).subtract(center);
            out = out.lengthSqr() < 1.0E-4 ? new Vec3(0, 1, 0) : out.normalize();
            double push = (boss ? 0.5 : 2.2) * falloff;
            e.setDeltaMovement(e.getDeltaMovement().add(out.x * push, 0.5 + 0.4 * push, out.z * push));
            e.hurtMarked = true;
            LdUtil.send(sl, LdUtil.fade(0xFFFFFF, VIOLET, 2.0F), LdUtil.center(e), 12, e.getBbWidth() * 0.5, 0.05);
        }
    }

    /** Casca da esfera + anel de poeira e choque correndo pelo chão. */
    private void shellFx(ServerLevel sl, float cur, int t) {
        Vec3 c = position();
        for (int i = 0; i < 22; i++) {
            Vec3 p = c.add(randomDir().scale(cur));
            LdUtil.send(sl, LdUtil.dust(i % 3 == 0 ? 0xFFFFFF : (i % 2 == 0 ? VIOLET : BLUE), 3.0F), p, 1, 0.3, 0);
            if (i % 4 == 0) LdUtil.send(sl, ParticleTypes.END_ROD, p, 1, 0.2, 0.05);
        }
        if (age % 3 == 0) LdUtil.send(sl, ParticleTypes.EXPLOSION, c.add(randomDir().scale(cur * 0.7)), 2, 1.0, 0);
        if (!Double.isNaN(groundY) && t % 2 == 0) {
            double ring = cur * 1.15;
            int pts = 20;
            for (int i = 0; i < pts; i++) {
                double a = (i + random.nextDouble() * 0.5) * Math.PI * 2 / pts;
                Vec3 p = new Vec3(c.x + Math.cos(a) * ring, groundY + 0.3, c.z + Math.sin(a) * ring);
                Vec3 out = new Vec3(Math.cos(a), 0.05, Math.sin(a));
                LdUtil.moving(sl, Vfx.cloud(), p, out, 0.5);
                if (i % 4 == 0) LdUtil.moving(sl, ParticleTypes.CAMPFIRE_COSY_SMOKE, p, out, 0.25);
            }
        }
    }

    /** Nuvem em cogumelo: coluna de fumaça e brasas violeta subindo, chapéu se abrindo lá em cima. */
    private void cloudFx(ServerLevel sl, int t) {
        if (t % 2 != 0) return;
        Vec3 c = position();
        float r = radius();
        float rise = cloudRise(age);
        double base = Double.isNaN(groundY) ? c.y - r * 0.3 : groundY;
        double top = base + r * (0.6 + 1.1 * rise);
        double stemR = r * 0.18;
        for (int i = 0; i < 3; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double h = base + random.nextDouble() * (top - base);
            Vec3 p = new Vec3(c.x + Math.cos(a) * stemR * random.nextDouble(), h, c.z + Math.sin(a) * stemR * random.nextDouble());
            LdUtil.moving(sl, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, p, new Vec3(0, 1, 0), 0.08);
        }
        double capR = r * (0.35 + 0.45 * rise);
        for (int i = 0; i < 4; i++) {
            double a = random.nextDouble() * Math.PI * 2;
            double rr = capR * Math.sqrt(random.nextDouble());
            Vec3 p = new Vec3(c.x + Math.cos(a) * rr, top + (random.nextDouble() - 0.3) * r * 0.2, c.z + Math.sin(a) * rr);
            LdUtil.moving(sl, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, p, new Vec3(Math.cos(a), 0.2, Math.sin(a)), 0.05);
            if (i % 2 == 0) LdUtil.send(sl, LdUtil.dust(i == 0 ? VIOLET : BLUE, 3.0F), p, 1, 0.5, 0);
        }
    }

    /** Contrariada / conjurador caiu: a energia se desfaz sem explodir. */
    private void fizzle(ServerLevel sl, @Nullable LivingEntity caster) {
        if (finished) return;
        finished = true;
        Vec3 c = position();
        LdUtil.send(sl, Vfx.reversePortal(), c, 60, 1.2, 0.3);
        LdUtil.send(sl, Vfx.largeSmoke(), c, 20, 0.8, 0.05);
        LdUtil.sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 2.5F, 1.4F);
        LdUtil.sound(sl, c, SoundEvents.GLASS_BREAK, 2.0F, 0.6F);
        LdNet.near(sl, c, 112, new LdNet.Fx(LdNet.ATOMIC_ABORT, c, 0, caster != null ? caster.getId() : getId(), 0));
        if (caster != null && caster.isAlive()) caster.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 80, 0, true, false, true));
        discard();
    }

    private Vec3 randomDir() {
        double z = random.nextDouble() * 2 - 1, a = random.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        return new Vec3(Math.cos(a) * s, z, Math.sin(a) * s);
    }

    @Override
    public void onAddedToLevel() {
        super.onAddedToLevel();
        if (!level().isClientSide && casterId != null && !blasting()) CHARGING.put(casterId, level().getGameTime());
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (level().isClientSide) return;
        // Saiu do mundo de qualquer jeito (descarregou, contrariada, fim): a Pilha e a trava do conjurador são soltas
        if (pending != null) {
            SpellStack.cancel(pending);
            pending = null;
        }
        if (casterId != null && !blasting()) CHARGING.remove(casterId);
    }
}
