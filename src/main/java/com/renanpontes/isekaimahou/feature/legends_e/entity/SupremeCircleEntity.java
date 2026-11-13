package com.renanpontes.isekaimahou.feature.legends_e.entity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.SpellStack;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.legends_e.LeFx;
import com.renanpontes.isekaimahou.feature.legends_e.LeRegistry;
import com.renanpontes.isekaimahou.feature.legends_e.LeUtil;
import com.renanpontes.isekaimahou.feature.legends_e.SupremeCasting;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Os círculos da Magia de Nível Supremo, nos pés do conjurador. Fases:
 * <ol>
 *   <li><b>Conjurando</b> (8 s; 0,8 s com a Ampulheta): camadas de círculos azul-brancos se formam (anel externo, anel
 *       do meio, selo, domo de runas, círculo no céu sobre o alvo). O conjurador fica preso no lugar. É uma pendente
 *       SUPREMA na Pilha — contrariada, tudo se estilhaça e a magia falha.</li>
 *   <li><b>Liberação</b>: clarão, tremor, letreiro; uma lança de luz (ou meteoro, se a magia for de fogo) cai sobre
 *       CADA inimigo a até 48 blocos do alvo e aplica os efeitos com poder ×3 e área ×3. Os efeitos de bloco
 *       acontecem no centro.</li>
 *   <li><b>Estilhaço/Falha</b>: os círculos racham e se desfazem.</li>
 * </ol>
 */
public class SupremeCircleEntity extends LeSpellEntity {
    public static final int CHANNEL = 160;
    public static final int INSTANT = 16;
    public static final int UNLEASH_LIFE = 70;
    public static final int BREAK_LIFE = 32;
    /** Raio dos círculos no chão. */
    public static final float RADIUS = 20F;
    /** Área dos efeitos em volta do ponto mirado. */
    public static final double REACH = 48.0;
    public static final int MAX_TARGETS = 48;
    /** Até onde o conjurador pode ser arrancado do centro antes de a magia se desfazer (até lá, volta ao círculo). */
    public static final double TETHER = 8.0;
    public static final float POWER_MULT = 3F;
    /** Expandir +4 = raio ×3 ({@code radius = base × (1 + 0,5·aoe)}). */
    public static final int AREA_BONUS = 4;

    public static final int CHANNELING = 0, UNLEASHED = 1, SHATTERED = 2, FIZZLED = 3;

    private static final EntityDataAccessor<Integer> DATA_STATE = SynchedEntityData.defineId(SupremeCircleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_STATE_AGE = SynchedEntityData.defineId(SupremeCircleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DURATION = SynchedEntityData.defineId(SupremeCircleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_TARGET = SynchedEntityData.defineId(SupremeCircleEntity.class, EntityDataSerializers.VECTOR3);

    @Nullable
    private SpellStack.Pending pending;
    private int telegraph = -1;
    @Nullable
    private LivingEntity locked;
    private Vec3 target = Vec3.ZERO;
    private int impactSounds;

    public SupremeCircleEntity(EntityType<? extends SupremeCircleEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder b) {
        b.define(DATA_STATE, CHANNELING);
        b.define(DATA_STATE_AGE, 0);
        b.define(DATA_DURATION, CHANNEL);
        b.define(DATA_TARGET, new Vector3f());
    }

    public int state() { return entityData.get(DATA_STATE); }
    public int stateAge() { return entityData.get(DATA_STATE_AGE); }
    public int duration() { return entityData.get(DATA_DURATION); }
    public boolean instant() { return duration() < CHANNEL; }

    /** Ponto mirado relativo à entidade. */
    public Vec3 targetOffset() {
        Vector3f v = entityData.get(DATA_TARGET);
        return new Vec3(v.x(), v.y(), v.z());
    }

    /** 0..1: quanto da conjuração já passou. */
    public float progress(float ageF) {
        return Mth.clamp(ageF / duration(), 0F, 1F);
    }

    // =====================================================================================
    //  Início
    // =====================================================================================

    public static void start(SpellContext ctx, Vec3 target, boolean instant) {
        ServerLevel level = ctx.level();
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        SupremeCircleEntity e = new SupremeCircleEntity(LeRegistry.SUPREME_CIRCLE.get(), level);
        e.bind(ctx, ctx.formStats());
        e.setPos(caster.getX(), caster.getY() + 0.02, caster.getZ());
        e.target = target;
        Vec3 off = target.subtract(e.position());
        e.entityData.set(DATA_TARGET, new Vector3f((float) off.x, (float) off.y, (float) off.z));
        int dur = instant ? INSTANT : CHANNEL;
        e.entityData.set(DATA_DURATION, dur);
        level.addFreshEntity(e);

        int rgb = e.color();
        // v1.3: energia subindo para o céu durante a canalização (o círculo gigante é do próprio renderer)
        com.renanpontes.isekaimahou.core.ElementFx.gatherAt(level, caster.position().add(0, 1.2, 0), com.renanpontes.isekaimahou.core.Element.ARCANE, rgb, 4.5F, 5, dur);
        com.renanpontes.isekaimahou.core.ElementFx.shake(level, caster.position(), 32, 0.3F, dur);
        // Trava e marca ANTES de entrar na Pilha: uma resposta imediata ("Em Resposta") já encontra tudo para desfazer
        e.telegraph = Telegraph.show(level, Telegraph.Shape.circle(target, (float) REACH).warmup(dur).color(LeFx.mix(rgb, 0xFFFFFF, 0.4F)));
        SupremeCasting.lock(caster, e.getId());
        e.locked = caster;
        if (!instant) {
            e.pending = SpellStack.push(level, caster, caster.position(), RADIUS, CHANNEL, SpellStack.Tier.ULTIMATE,
                    spellName(ctx), ctx.element(), e::countered);
            // Uma resposta imediata pode ter contrariado dentro do push: aí não há cântico
            if (e.state() == CHANNELING && !ctx.recipe().chant().isEmpty() && !ctx.formStats().silent) {
                Announce.chant(caster, ctx.recipe().chant(), CHANNEL);
            }
        } else if (caster instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_e.hourglass_used").withStyle(ChatFormatting.AQUA), true);
            LeFx.sound(level, caster.position(), SoundEvents.AMETHYST_CLUSTER_BREAK, 1.6F, 0.7F);
            LeFx.sound(level, caster.position(), SoundEvents.BELL_RESONATE, 1.4F, 1.6F);
        }
        LeFx.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 2.0F, 0.7F);
        LeFx.sound(level, caster.position(), SoundEvents.ILLUSIONER_PREPARE_MIRROR, 1.6F, 0.6F);
        LeFx.sound(level, target, SoundEvents.BEACON_AMBIENT, 2.5F, 0.6F);
    }

    private static Component spellName(SpellContext ctx) {
        String n = ctx.recipe().name();
        return n.isEmpty() ? Component.translatable("glyph.isekai_mahou.super_tier") : Component.literal(n);
    }

    // =====================================================================================
    //  Servidor
    // =====================================================================================

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        int st = state();
        if (st != CHANNELING) {
            int life = st == UNLEASHED ? UNLEASH_LIFE : BREAK_LIFE;
            if (age - stateAge() > life) discard();
            return;
        }
        LivingEntity c = ctx.caster();
        double drift = c == null ? 0 : horizontal2(c.position(), position());
        if (c == null || !c.isAlive() || c.isRemoved() || c.level() != level || drift > TETHER * TETHER) {
            fizzle(level);
            return;
        }
        // O conjurador fica parado: a trava de atributo cuida de andar/pular; mobs também perdem o caminho
        if (c instanceof Mob m) m.getNavigation().stop();
        if (!(c instanceof Player)) c.setDeltaMovement(0, Math.min(0, c.getDeltaMovement().y), 0);
        // Empurrões (golpes, explosões, voo do criativo) não tiram o conjurador do centro: ele volta para o círculo.
        // Arrancado para longe (teleporte, pérola, explosão enorme) → a magia se desfaz (acima).
        if (c.isPassenger()) c.stopRiding();
        if (drift > 1.0) {
            if (c instanceof ServerPlayer sp) {
                sp.connection.teleport(getX(), sp.getY(), getZ(), sp.getYRot(), sp.getXRot());
            } else {
                c.teleportTo(getX(), c.getY(), getZ());
            }
            c.setDeltaMovement(0, Math.min(0, c.getDeltaMovement().y), 0);
            c.hurtMarked = true;
        }
        channelFx(level, c);
        if (age >= duration()) unleash(level, ctx, c);
    }

    private static double horizontal2(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private void setState(int s) {
        entityData.set(DATA_STATE, s);
        entityData.set(DATA_STATE_AGE, age);
        syncAge();
    }

    private void channelFx(ServerLevel level, LivingEntity c) {
        int dur = duration();
        float p = age / (float) dur;
        Vec3 pos = position();
        if (age % 20 == 0) LeFx.sound(level, pos, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.8F, 0.5F + p);
        if (age == dur / 3) LeFx.sound(level, pos, SoundEvents.EVOKER_PREPARE_SUMMON, 2.0F, 0.6F);
        if (age == dur * 2 / 3) {
            LeFx.sound(level, pos, SoundEvents.BEACON_POWER_SELECT, 2.2F, 0.6F);
            LeFx.sound(level, target, SoundEvents.BEACON_POWER_SELECT, 2.5F, 0.5F);
        }
        if (age % 40 == 20 || age == dur - 10) BossKit.shake(level, pos, 28, 0.2F + 0.9F * p, 24);
        // Faíscas subindo no ponto mirado (o lado de quem vai levar a magia também vê que algo vem)
        if (age % 4 == 0) {
            double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * REACH * 0.8;
            Vec3 q = target.add(Math.cos(a) * r, 0.3, Math.sin(a) * r);
            LeFx.send(level, Vfx.endRod(), q.x, q.y, q.z, 2, 0.3, 1.5, 0.3, 0.06);
        }
    }

    /** Contrariada pela Pilha (o núcleo já puniu e mostrou o vidro quebrando): os círculos racham e somem. */
    private void countered() {
        if (state() != CHANNELING || !(level() instanceof ServerLevel level)) return;
        setState(SHATTERED);
        release(level);
        Vec3 p = position();
        LeFx.sound(level, p, SoundEvents.GLASS_BREAK, 2.5F, 0.5F);
        LeFx.sound(level, p, SoundEvents.AMETHYST_BLOCK_BREAK, 2.5F, 0.6F);
        LeFx.sound(level, p, SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.5F);
        LeFx.send(level, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState()),
                p.x, p.y + 0.5, p.z, 120, RADIUS * 0.5, 0.4, RADIUS * 0.5, 0.2);
        LeFx.send(level, Vfx.endRod(), p.x, p.y + 1, p.z, 60, RADIUS * 0.4, 1.0, RADIUS * 0.4, 0.15);
        BossKit.shake(level, p, 32, 1.2F, 16);
    }

    /** O conjurador morreu, saiu ou foi tirado do lugar: a magia se desfaz (sai da Pilha sem acontecer). */
    private void fizzle(ServerLevel level) {
        if (pending != null) SpellStack.cancel(pending);
        setState(FIZZLED);
        release(level);
        LeFx.sound(level, position(), SoundEvents.BEACON_DEACTIVATE, 2.0F, 0.7F);
        LeFx.sound(level, position(), SoundEvents.FIRE_EXTINGUISH, 1.5F, 0.6F);
    }

    /** Solta o conjurador e apaga a marca do alvo (se a magia não saiu). */
    private void release(ServerLevel level) {
        if (locked != null) {
            SupremeCasting.unlock(locked);
            locked = null;
        }
        if (telegraph >= 0 && state() != UNLEASHED) Telegraph.cancel(level, telegraph);
        telegraph = -1;
    }

    @Override
    public void onRemovedFromLevel() {
        super.onRemovedFromLevel();
        if (!(level() instanceof ServerLevel level)) return;
        if (state() == CHANNELING && pending != null) SpellStack.cancel(pending);
        if (locked != null) {
            SupremeCasting.unlock(locked);
            locked = null;
        }
        if (telegraph >= 0 && state() == CHANNELING) Telegraph.cancel(level, telegraph);
    }

    // =====================================================================================
    //  Liberação
    // =====================================================================================

    private void unleash(ServerLevel level, SpellContext ctx, LivingEntity caster) {
        if (pending != null) SpellStack.resolve(pending);
        if (locked != null) {
            SupremeCasting.unlock(locked);
            locked = null;
        }
        setState(UNLEASHED);
        telegraph = -1;
        int rgb = color();
        int bright = LeFx.mix(rgb, 0xFFFFFF, 0.55F);

        String name = ctx.recipe().name();
        Component title = name.isEmpty() ? Component.translatable("announce.isekai_mahou.legends_e.supreme")
                : Component.translatable("announce.isekai_mahou.legends_e.supreme_named", name);
        if (!ctx.formStats().silent) Announce.send(caster, title, LeFx.mix(rgb, 0xFFFFFF, 0.3F), Announce.STYLE_ULTIMATE);
        LeFx.flash(level, target, 96, 0xEAF6FF, 16, 0.75F);
        LeFx.flash(level, position(), 40, 0xEAF6FF, 12, 0.5F);
        BossKit.shake(level, target, 80, 2.4F, 36);
        BossKit.shake(level, position(), 40, 1.4F, 20);
        LeFx.sound(level, target, SoundEvents.LIGHTNING_BOLT_THUNDER, 4.0F, 0.6F);
        LeFx.sound(level, target, SoundEvents.END_PORTAL_SPAWN, 3.0F, 0.8F);
        LeFx.sound(level, position(), SoundEvents.BEACON_POWER_SELECT, 2.5F, 1.2F);
        LeFx.ring(level, position().add(0, 0.2, 0), new Vec3(0, 1, 0), RADIUS * 1.6F, bright, 24);

        // Poder ×3 e área ×3 (Expandir +4) em cada efeito
        SpellContext strong = ctx.copy(ctx.powerMult() * POWER_MULT);
        List<ParsedSpell.EffectEntry> boosted = new ArrayList<>();
        boolean fiery = ElementReactions.isFire(ctx.element());
        boolean beneficial = false;
        for (ParsedSpell.EffectEntry en : ctx.spell().effects()) {
            SpellStats s = en.stats().copy();
            s.aoe += AREA_BONUS;
            boosted.add(new ParsedSpell.EffectEntry(en.effect(), s));
            fiery |= ElementReactions.isFire(en.effect().element());
            beneficial |= en.effect().isBeneficial();
        }
        boolean meteors = fiery;

        // Golpe central: efeitos de bloco (crateras, terreno...) no ponto mirado
        int travel = meteors ? 16 : 7;
        Vec3 sky = target.add(0, 64, 0);
        if (meteors) LeFx.meteor(level, sky.add(18, 10, -14), target, rgb, travel, 4.5F);
        else LeFx.lance(level, sky, target, bright, travel, 3.2F);
        BossKit.schedule(level, travel, () -> centerImpact(level, strong, boosted, ctx.formStats().sensitive, rgb));

        // Um golpe do céu por alvo, em cascata
        List<LivingEntity> targets = LeUtil.foesAround(ctx, target, REACH, 24, 64, MAX_TARGETS);
        if (beneficial) {
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(target, target).inflate(REACH, 32, REACH),
                    e -> e.isAlive() && e != caster && ctx.isAlly(e) && !(e instanceof net.minecraft.world.entity.decoration.ArmorStand))) {
                if (targets.size() >= MAX_TARGETS) break;
                targets.add(e);
            }
        }
        int n = targets.size();
        int last = travel;
        for (int i = 0; i < n; i++) {
            LivingEntity e = targets.get(i);
            int delay = 6 + Math.round(i * 34F / Math.max(1, n)) + random.nextInt(3);
            int t = meteors ? 14 : 6;
            last = Math.max(last, delay + t);
            BossKit.schedule(level, delay, () -> {
                if (!e.isAlive() || e.level() != level) return;
                Vec3 hit = LeUtil.center(e);
                Vec3 from = hit.add((random.nextDouble() - 0.5) * 14, 38 + random.nextDouble() * 10, (random.nextDouble() - 0.5) * 14);
                if (meteors) LeFx.meteor(level, from, hit, rgb, t, 1.2F + random.nextFloat() * 0.8F);
                else LeFx.lance(level, from, hit, bright, t, 1.1F);
                BossKit.schedule(level, t, () -> strike(level, strong, boosted, e, rgb, meteors));
            });
        }
        int endAt = last + 2;
        BossKit.schedule(level, endAt, () -> ctx.end(target, new Vec3(0, -1, 0)));
    }

    /** Impacto central: os efeitos de bloco da receita (com a área ×3) e uma onda de choque enorme. */
    private void centerImpact(ServerLevel level, SpellContext strong, List<ParsedSpell.EffectEntry> boosted, boolean formSensitive, int rgb) {
        BlockHitResult bh = new BlockHitResult(target, Direction.UP, BlockPos.containing(target.subtract(0, 0.1, 0)), false);
        for (ParsedSpell.EffectEntry en : boosted) {
            EffectGlyph g = en.effect();
            if (!(g.wantsBlocks() || en.stats().sensitive || formSensitive)) continue;
            SpellContext c = !strong.isPlayerCast() || g.origin() != null || strong.element().canUseGlyphOf(g.element())
                    ? strong : strong.forElement(g.element());
            g.onBlock(c, bh, en.stats());
        }
        LeFx.ring(level, target.add(0, 0.3, 0), new Vec3(0, 1, 0), (float) REACH, LeFx.mix(rgb, 0xFFFFFF, 0.5F), 30);
        LeFx.ring(level, target.add(0, 0.6, 0), new Vec3(0, 1, 0), (float) REACH * 0.6F, 0xFFFFFF, 20);
        LeFx.send(level, ParticleTypes.EXPLOSION_EMITTER, target, 3, 2.0, 0);
        LeFx.send(level, Vfx.endRod(), target.x, target.y + 1, target.z, 120, 6, 4, 6, 0.4);
        LeFx.sound(level, target, SoundEvents.GENERIC_EXPLODE, 4.0F, 0.5F);
        LeFx.sound(level, target, SoundEvents.LIGHTNING_BOLT_IMPACT, 4.0F, 0.7F);
    }

    /** Um alvo é atingido pelo golpe do céu: efeitos com poder ×3 e área ×3. */
    private void strike(ServerLevel level, SpellContext strong, List<ParsedSpell.EffectEntry> boosted, LivingEntity e, int rgb, boolean meteor) {
        if (!e.isAlive() || e.level() != level) return;
        Vec3 hit = LeUtil.center(e);
        strong.impact(hit, new Vec3(0, -1, 0), e);
        for (ParsedSpell.EffectEntry en : boosted) {
            if (strong.isValidTarget(en.effect(), en.stats(), e)) strong.applyEffect(en, e);
        }
        LeFx.send(level, ParticleTypes.FLASH, hit, 1, 0, 0);
        LeFx.send(level, meteor ? ParticleTypes.LAVA : Vfx.endRod(), hit.x, hit.y, hit.z, 14, 0.6, 0.6, 0.6, 0.25);
        LeFx.send(level, LeFx.dust(LeFx.mix(rgb, 0xFFFFFF, 0.4F), 1.6F), hit.x, hit.y, hit.z, 10, 0.8, 0.8, 0.8, 0);
        LeFx.ring(level, e.position().add(0, 0.2, 0), new Vec3(0, 1, 0), 4.5F, meteor ? 0xFF8A3A : LeFx.mix(rgb, 0xFFFFFF, 0.5F), 12);
        if (impactSounds++ < 10) {
            LeFx.sound(level, hit, meteor ? SoundEvents.GENERIC_EXPLODE.value() : SoundEvents.LIGHTNING_BOLT_IMPACT, 1.8F, meteor ? 0.8F : 1.3F);
        }
    }

    // =====================================================================================
    //  Cliente: partículas
    // =====================================================================================

    @Override
    protected void clientTick() {
        boolean reduced = LeUtil.reducedParticles();
        int st = state();
        if (st == CHANNELING) {
            float p = progress(age);
            int n = reduced ? 1 : 3;
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = Math.sqrt(random.nextDouble()) * RADIUS;
                level().addParticle(Vfx.endRod(), getX() + Math.cos(a) * r, getY() + 0.2, getZ() + Math.sin(a) * r,
                        0, 0.04 + 0.12 * p, 0);
            }
            double a = random.nextDouble() * Math.PI * 2, r = 3 + random.nextDouble() * 6;
            level().addParticle(Vfx.enchant(), getX(), getY() + 1.4, getZ(), Math.cos(a) * r, 1.0 + random.nextDouble() * 2, Math.sin(a) * r);
            if (p > 0.45F && !reduced) {
                Vec3 t = position().add(targetOffset());
                double b = random.nextDouble() * Math.PI * 2, rr = Math.sqrt(random.nextDouble()) * 22;
                level().addParticle(Vfx.endRod(), t.x + Math.cos(b) * rr, t.y + 34, t.z + Math.sin(b) * rr, 0, -0.25, 0);
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (key != DATA_STATE || !level().isClientSide) return;
        int st = state();
        int n = LeUtil.reducedParticles() ? 30 : 90;
        if (st == UNLEASHED) {
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2;
                level().addParticle(Vfx.endRod(), getX(), getY() + 0.5, getZ(), Math.cos(a) * 0.9, 0.05 + random.nextDouble() * 0.3, Math.sin(a) * 0.9);
            }
        } else if (st == SHATTERED || st == FIZZLED) {
            for (int i = 0; i < n; i++) {
                double a = random.nextDouble() * Math.PI * 2, r = RADIUS * (0.3 + 0.7 * random.nextDouble());
                level().addParticle(st == SHATTERED ? ParticleTypes.CRIT : ParticleTypes.SMOKE,
                        getX() + Math.cos(a) * r, getY() + 0.3, getZ() + Math.sin(a) * r, 0, 0.1 + random.nextDouble() * 0.2, 0);
            }
        }
    }
}
