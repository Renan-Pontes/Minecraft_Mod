package com.renanpontes.isekaimahou.feature.ultimates.entity;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.ultimates.UltFx;
import com.renanpontes.isekaimahou.feature.ultimates.UltNet;
import com.renanpontes.isekaimahou.feature.ultimates.UltRegistries;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Colosso Espectral: gigante translúcido (~12 blocos) que surge do chão em volta de quem conjurou e o acompanha.
 * <ul>
 *   <li><b>Corte</b>: arco largo (150°, 11 blocos) na frente — dano da espada + efeitos da receita;</li>
 *   <li><b>Pisão</b>: quando há 3+ inimigos perto ou o alvo está longe — esmaga uma área de raio 5 (telegrafada), abre
 *       uma cratera ({@link Destruction}) e lança os inimigos para cima;</li>
 *   <li><b>Guarda</b>: projéteis hostis que vêm pela FRENTE na direção do dono se desfazem no escudo.</li>
 * </ul>
 * Só ataca inimigos de verdade ({@link UltFx#isFoe}). Não é uma criatura (não leva dano, não é salvo), dura 30 s e
 * some se o dono morrer, sair ou mudar de dimensão.
 */
public class SpectralColossusEntity extends Entity implements GeoEntity, TraceableEntity {
    private static final EntityDataAccessor<Integer> DATA_COLOR = SynchedEntityData.defineId(SpectralColossusEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_DESPAWN = SynchedEntityData.defineId(SpectralColossusEntity.class, EntityDataSerializers.BOOLEAN);

    public static final int DESPAWN_TICKS = 30;
    /** Ticks da animação de surgir (sem atacar enquanto isso). */
    public static final int SPAWN_TICKS = 30;
    /** Poder dos golpes (são muitos em 30 s). */
    public static final float ATTACK_POWER = 0.8F;
    public static final float SLASH_RANGE = 11F;
    public static final float SLASH_ANGLE = 150F;
    public static final float SWORD_DAMAGE = 7F;
    public static final float SLAM_DAMAGE = 9F;

    private static final RawAnimation SPAWN_IDLE = RawAnimation.begin().thenPlay("animation.spectral_colossus.spawn")
            .thenLoop("animation.spectral_colossus.idle");
    private static final RawAnimation DESPAWN = RawAnimation.begin().thenPlayAndHold("animation.spectral_colossus.despawn");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // ---------------- Servidor ----------------
    @Nullable private SpellContext ctx;
    @Nullable private UUID ownerId;
    private int life;
    private int maxLife = 600;
    private int despawnAt = -1;
    private int attackCd = SPAWN_TICKS;
    /** 0 = nenhum, 1 = corte, 2 = pisão. */
    private int pendingKind;
    private int pendingTimer;
    private float attackYaw;
    private Vec3 slamPoint = Vec3.ZERO;
    private float slamRadius = 5F;
    private int guardCd;

    // ---------------- Cliente ----------------
    /** Tick (do cliente) em que o desfazer começou; -1 = não começou. Usado pelo renderer para o esmaecer. */
    private int clientDespawnTick = -1;

    public SpectralColossusEntity(EntityType<? extends SpectralColossusEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    /** Invoca o Colosso de {@code owner} (o anterior dele se desfaz). */
    public static void summon(SpellContext ctx, LivingEntity owner, int ticks) {
        ServerLevel level = ctx.level();
        for (SpectralColossusEntity old : level.getEntities(UltRegistries.SPECTRAL_COLOSSUS.get(),
                owner.getBoundingBox().inflate(96), c -> owner.getUUID().equals(c.ownerId))) {
            old.startDespawn();
        }
        SpectralColossusEntity c = new SpectralColossusEntity(UltRegistries.SPECTRAL_COLOSSUS.get(), level);
        c.ctx = ctx.copy(ctx.powerMult() * ATTACK_POWER);
        c.ownerId = owner.getUUID();
        c.maxLife = ticks;
        c.slamRadius = Mth.clamp(ctx.formStats().radius(5F), 4F, 12F);
        int rgb = UltFx.rgb(ctx);
        c.entityData.set(DATA_COLOR, rgb);
        Vec3 pos = c.followPoint(owner);
        c.moveTo(pos.x, pos.y, pos.z, owner.getYRot(), 0F);
        level.addFreshEntity(c);

        // Surgir: pilar de luz, anel no chão, chão rachando
        BlockState ground = groundState(level, pos);
        UltFx.far(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), pos.x, pos.y + 0.2, pos.z, 80, 2.5, 0.3, 2.5, 0.3);
        UltFx.far(level, Vfx.poof(), pos.x, pos.y + 0.5, pos.z, 40, 3.0, 0.4, 3.0, 0.05);
        UltFx.far(level, dust(rgb, 2.5F), pos.x, pos.y + 6, pos.z, 50, 2.0, 5.0, 2.0, 0);
        // v1.3: o colosso sobe de um portal no chão, dentro de um pilar de luz (montados no cliente)
        com.renanpontes.isekaimahou.core.ElementFx.portal(level, pos.add(0, 0.08, 0), null, com.renanpontes.isekaimahou.core.Element.ARCANE,
                rgb, 4.5F, 5, 40);
        com.renanpontes.isekaimahou.core.ElementFx.pillar(level, pos, rgb, 2.6F, 32F, 36);
        com.renanpontes.isekaimahou.core.ElementFx.nova(level, pos.add(0, 0.1, 0), com.renanpontes.isekaimahou.core.Element.ARCANE, rgb, 8F, 5, false);
        UltNet.fx(level, UltNet.FX_RING, pos.add(0, 0.2, 0), 0F, 12F, rgb, 18);
        UltNet.fx(level, UltNet.FX_FLASH, pos.add(0, 6, 0), 0F, 5F, rgb, 10);
        UltFx.sound(level, pos, SoundEvents.WARDEN_EMERGE, 2.5F, 0.7F);
        UltFx.sound(level, pos, SoundEvents.BEACON_ACTIVATE, 2.5F, 0.6F);
        UltFx.sound(level, pos, SoundEvents.ANVIL_LAND, 1.5F, 0.4F);
        BossKit.shake(level, pos, 40, 1.2F, 24);
        if (owner instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.ultimates.colossus_summon", ticks / 20)
                    .withStyle(ChatFormatting.AQUA), true);
        }
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_COLOR, 0xB98CFF);
        builder.define(DATA_DESPAWN, false);
    }

    public int color() { return entityData.get(DATA_COLOR); }
    public boolean isDespawning() { return entityData.get(DATA_DESPAWN); }

    /** Opacidade 0..1 no cliente: surge nos primeiros ticks e esmaece ao se desfazer. */
    public float visibility(float partial) {
        float t = tickCount + partial;
        float v = Mth.clamp(t / SPAWN_TICKS, 0F, 1F);
        if (clientDespawnTick >= 0) v = Math.min(v, 1F - Mth.clamp((t - clientDespawnTick) / DESPAWN_TICKS, 0F, 1F));
        return v;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientTick();
            return;
        }
        if (!(level() instanceof ServerLevel sl) || ctx == null) {
            discard();
            return;
        }
        LivingEntity owner = owner(sl);
        if (despawnAt >= 0) {
            if (tickCount - despawnAt >= DESPAWN_TICKS) discard();
            else if (owner != null) follow(sl, owner, null);
            return;
        }
        life++;
        if (owner == null || life >= maxLife) {
            startDespawn();
            return;
        }
        LivingEntity target = pendingKind == 0 ? pickTarget(sl, owner) : null;
        follow(sl, owner, target);
        guard(sl, owner);
        combat(sl, owner, target);
    }

    @Nullable
    private LivingEntity owner(ServerLevel sl) {
        if (ownerId == null) return null;
        Entity e = sl.getEntity(ownerId);
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isRemoved() || le.isSpectator()) return null;
        return le;
    }

    /** Ponto onde o Colosso fica: um pouco atrás do dono, no chão (ou até 6 blocos abaixo dele, se voando). */
    private Vec3 followPoint(LivingEntity owner) {
        Vec3 fwd = forward(owner.getYRot());
        Vec3 p = owner.position().subtract(fwd.scale(1.6));
        if (level() instanceof ServerLevel sl) {
            Vec3 g = UltFx.groundBelow(sl, p.add(0, 1, 0), 24);
            return new Vec3(p.x, Math.max(g.y, owner.getY() - 6), p.z);
        }
        return p;
    }

    private void follow(ServerLevel sl, LivingEntity owner, @Nullable LivingEntity target) {
        Vec3 want = followPoint(owner);
        Vec3 pos = position();
        double dist = pos.distanceTo(want);
        if (dist > 24) {
            UltFx.far(sl, dust(color(), 2F), pos.x, pos.y + 5, pos.z, 60, 1.5, 4, 1.5, 0);
            setPos(want);
            UltFx.far(sl, dust(color(), 2F), want.x, want.y + 5, want.z, 60, 1.5, 4, 1.5, 0);
        } else if (dist > 0.05) {
            Vec3 step = want.subtract(pos).scale(0.35);
            if (step.length() > 1.4) step = step.normalize().scale(1.4);
            setPos(pos.add(step));
        }
        float wantYaw = pendingKind != 0 ? attackYaw : target != null ? yawTo(target.position()) : owner.getYRot();
        setYRot(Mth.approachDegrees(getYRot(), wantYaw, 9F));
    }

    // ---------------- Guarda ----------------
    private void guard(ServerLevel sl, LivingEntity owner) {
        if (guardCd > 0) guardCd--;
        Vec3 oc = owner.position().add(0, owner.getBbHeight() * 0.5, 0);
        Vec3 face = forward(getYRot());
        int blocked = 0;
        for (Projectile p : sl.getEntitiesOfClass(Projectile.class, owner.getBoundingBox().inflate(9))) {
            if (!p.isAlive()) continue;
            Entity po = p.getOwner();
            if (po == owner || po == this || (po != null && ctx.isAlly(po))) continue;
            Vec3 v = p.getDeltaMovement();
            if (v.lengthSqr() < 0.01) continue;
            Vec3 rel = p.position().subtract(oc);
            if (rel.lengthSqr() > 64) continue;
            if (v.dot(oc.subtract(p.position())) <= 0) continue;
            Vec3 flat = new Vec3(rel.x, 0, rel.z);
            if (flat.lengthSqr() < 0.25 || face.dot(flat.normalize()) < 0.05) continue;
            // Bloqueado: o projétil se desfaz no escudo
            Vec3 at = p.position();
            UltFx.far(sl, Vfx.electric(), at.x, at.y, at.z, 12, 0.3, 0.3, 0.3, 0.2);
            UltFx.far(sl, dust(color(), 1.6F), at.x, at.y, at.z, 10, 0.4, 0.4, 0.4, 0);
            UltFx.far(sl, Vfx.crit(), at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.3);
            UltFx.sound(sl, at, SoundEvents.SHIELD_BLOCK, 1.5F, 0.6F);
            UltFx.sound(sl, at, SoundEvents.AMETHYST_BLOCK_HIT, 1.5F, 0.6F);
            p.discard();
            if (++blocked >= 8) break;
        }
        if (blocked > 0 && guardCd <= 0) {
            triggerAnim("action", "guard");
            guardCd = 16;
        }
    }

    // ---------------- Combate ----------------
    @Nullable
    private LivingEntity pickTarget(ServerLevel sl, LivingEntity owner) {
        List<LivingEntity> foes = UltFx.foesAround(ctx, owner, position(), 17);
        if (foes.isEmpty()) return null;
        LivingEntity hit = owner.getLastHurtMob();
        if (hit != null && foes.contains(hit)) return hit;
        LivingEntity by = owner.getLastHurtByMob();
        if (by != null && foes.contains(by)) return by;
        return foes.get(0);
    }

    private void combat(ServerLevel sl, LivingEntity owner, @Nullable LivingEntity target) {
        if (attackCd > 0) attackCd--;
        if (pendingKind != 0) {
            if (--pendingTimer <= 0) {
                int kind = pendingKind;
                pendingKind = 0;
                float[] snap = UltFx.chargeSnapshot(ctx.caster());
                if (kind == 1) slash(sl, owner);
                else slam(sl, owner);
                UltFx.chargeRestore(ctx.caster(), snap);
            }
            return;
        }
        if (attackCd > 0 || target == null) return;
        Vec3 c = position();
        double d = Math.sqrt(horizontalDistSqr(c, target.position()));
        int crowd = UltFx.foesAround(ctx, owner, c, SLASH_RANGE).size();
        attackYaw = yawTo(target.position());
        int rgb = color();
        if (crowd >= 3 || (d > SLASH_RANGE - 1 && d <= 18)) {
            Vec3 toward = forward(attackYaw);
            Vec3 aim = d <= SLASH_RANGE - 1 ? c.add(toward.scale(Math.min(6, d))) : target.position();
            slamPoint = UltFx.groundBelow(sl, aim.add(0, 2, 0), 12);
            pendingKind = 2;
            pendingTimer = 16;
            attackCd = 46;
            triggerAnim("action", "slam");
            Telegraph.show(sl, Telegraph.Shape.circle(slamPoint, slamRadius).warmup(16).color(rgb));
            UltFx.sound(sl, c, SoundEvents.ENDER_DRAGON_FLAP, 2.5F, 0.5F);
        } else if (d <= SLASH_RANGE) {
            pendingKind = 1;
            pendingTimer = 10;
            attackCd = 30;
            triggerAnim("action", "slash");
            Telegraph.show(sl, Telegraph.Shape.cone(c, attackYaw, SLASH_ANGLE, SLASH_RANGE).warmup(10).color(rgb).noRunes());
            UltFx.sound(sl, c, SoundEvents.TRIDENT_RIPTIDE_1.value(), 2.0F, 0.5F);
        }
    }

    private void slash(ServerLevel sl, LivingEntity owner) {
        Vec3 c = position();
        Vec3 face = forward(attackYaw);
        double cosHalf = Math.cos(Math.toRadians(SLASH_ANGLE / 2));
        int rgb = color();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(SLASH_RANGE + 2, 12, SLASH_RANGE + 2))) {
            if (!UltFx.isFoe(ctx, owner, e)) continue;
            Vec3 rel = new Vec3(e.getX() - c.x, 0, e.getZ() - c.z);
            double dist = rel.length();
            if (dist > SLASH_RANGE + e.getBbWidth() * 0.5) continue;
            if (dist > 1.0 && face.dot(rel.scale(1 / dist)) < cosHalf) continue;
            double dy = e.getY() - getY();
            if (dy < -3 || dy > 11) continue;
            e.invulnerableTime = 0;
            ctx.damage(e, SWORD_DAMAGE, ctx.formStats(), this);
            ctx.resolveOnEntity(e, this);
            double kb = BossKit.isBossLike(e) ? 0.2 : 1.1;
            Vec3 out = dist < 0.01 ? face : rel.scale(1 / dist);
            e.push(out.x * kb, 0.3 * kb, out.z * kb);
            e.hurtMarked = true;
            UltFx.far(sl, ParticleTypes.SWEEP_ATTACK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 1, 0, 0, 0, 0);
        }
        UltNet.fx(sl, UltNet.FX_SLASH, c.add(0, 3.2, 0), attackYaw, SLASH_RANGE, rgb, 10);
        // Rastro da lâmina no ar (partículas ao longo do arco)
        for (int i = 0; i <= 14; i++) {
            float yaw = attackYaw + SLASH_ANGLE / 2 - SLASH_ANGLE * i / 14F;
            Vec3 dir = forward(yaw);
            for (double r = 4; r <= SLASH_RANGE; r += 3.5) {
                Vec3 p = c.add(dir.scale(r)).add(0, 2.5 + (i % 2) * 0.6, 0);
                UltFx.far(sl, dust(rgb, 1.8F), p.x, p.y, p.z, 1, 0.1, 0.1, 0.1, 0);
            }
            if (i % 3 == 0) {
                Vec3 p = c.add(dir.scale(SLASH_RANGE * 0.7)).add(0, 2.5, 0);
                UltFx.far(sl, ParticleTypes.SWEEP_ATTACK, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }
        UltFx.sound(sl, c, SoundEvents.PLAYER_ATTACK_SWEEP, 3.0F, 0.45F);
        UltFx.sound(sl, c, SoundEvents.WARDEN_ATTACK_IMPACT, 1.5F, 0.7F);
        BossKit.shake(sl, c, 20, 0.5F, 8);
    }

    private void slam(ServerLevel sl, LivingEntity owner) {
        Vec3 p = slamPoint;
        int rgb = color();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(slamRadius + 1, 6, slamRadius + 1))) {
            if (!UltFx.isFoe(ctx, owner, e)) continue;
            double dist = Math.sqrt(horizontalDistSqr(p, e.position()));
            if (dist > slamRadius + e.getBbWidth() * 0.5 || Math.abs(e.getY() - p.y) > 5) continue;
            e.invulnerableTime = 0;
            ctx.damage(e, SLAM_DAMAGE, ctx.formStats(), this);
            ctx.resolveOnEntity(e, this);
            double up = BossKit.isBossLike(e) ? 0.15 : 0.9 * (1 - 0.5 * dist / slamRadius);
            Vec3 out = dist < 0.01 ? Vec3.ZERO : new Vec3((e.getX() - p.x) / dist, 0, (e.getZ() - p.z) / dist);
            e.push(out.x * 0.5, up, out.z * 0.5);
            e.hurtMarked = true;
        }
        BlockState ground = groundState(sl, p);
        UltFx.far(sl, ParticleTypes.EXPLOSION_EMITTER, p.x, p.y + 0.5, p.z, 1, 0, 0, 0, 0);
        UltFx.far(sl, new BlockParticleOption(ParticleTypes.BLOCK, ground), p.x, p.y + 0.3, p.z, 90, slamRadius * 0.5, 0.4,
                slamRadius * 0.5, 0.45);
        UltFx.far(sl, Vfx.poof(), p.x, p.y + 0.4, p.z, 40, slamRadius * 0.5, 0.2, slamRadius * 0.5, 0.08);
        UltFx.far(sl, dust(rgb, 2.2F), p.x, p.y + 1, p.z, 30, slamRadius * 0.5, 1.0, slamRadius * 0.5, 0);
        // v1.3: o soco abre o chão — impacto de terra pesado montado no cliente
        com.renanpontes.isekaimahou.core.ElementFx.impactFx(sl, p.add(0, 0.2, 0), com.renanpontes.isekaimahou.core.Element.EARTH, rgb, 4,
                null, Math.max(1F, slamRadius / 3F));
        UltNet.fx(sl, UltNet.FX_RING, p.add(0, 0.2, 0), 0F, slamRadius * 1.7F, rgb, 14);
        UltNet.fx(sl, UltNet.FX_FLASH, p.add(0, 0.8, 0), 0F, slamRadius * 0.6F, rgb, 7);
        UltFx.sound(sl, p, SoundEvents.GENERIC_EXPLODE.value(), 2.5F, 0.6F);
        UltFx.sound(sl, p, SoundEvents.ANVIL_LAND, 2.0F, 0.4F);
        UltFx.sound(sl, p, SoundEvents.WARDEN_ATTACK_IMPACT, 2.0F, 0.5F);
        BossKit.shake(sl, p, 32, 1.4F, 14);
        Destruction.crater(sl, p, Mth.clamp(slamRadius * 0.5F, 2.5F, 5F), ctx.caster());
    }

    /** Começa a se desfazer (animação de 1,5 s) e some. */
    public void startDespawn() {
        if (despawnAt >= 0 || !(level() instanceof ServerLevel sl)) return;
        despawnAt = tickCount;
        pendingKind = 0;
        entityData.set(DATA_DESPAWN, true);
        Vec3 c = position();
        UltFx.far(sl, dust(color(), 2.2F), c.x, c.y + 6, c.z, 120, 2.0, 5.0, 2.0, 0);
        UltFx.sound(sl, c, SoundEvents.BEACON_DEACTIVATE, 2.5F, 0.5F);
        UltFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_BREAK, 2.0F, 0.5F);
    }

    // ---------------- Cliente: brilho espectral ----------------
    private void clientTick() {
        if (isDespawning() && clientDespawnTick < 0) clientDespawnTick = tickCount;
        RandomSource r = random;
        boolean reduced = !FxQuality.atLeast(MahouConfig.Graphics.HIGH);
        if (tickCount % (reduced ? 4 : 2) != 0) return;
        ParticleOptions d = dust(color(), 1.4F);
        int n = isDespawning() ? 6 : 2;
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2;
            double rad = 1.2 + r.nextDouble() * 1.6;
            level().addParticle(d, getX() + Math.cos(a) * rad, getY() + r.nextDouble() * 11, getZ() + Math.sin(a) * rad, 0, 0.03, 0);
        }
        if (r.nextInt(4) == 0) {
            level().addParticle(Vfx.endRod(), getX() + (r.nextDouble() - 0.5) * 3, getY() + 4 + r.nextDouble() * 5,
                    getZ() + (r.nextDouble() - 0.5) * 3, 0, 0.02, 0);
        }
    }

    // =====================================================================================
    //  Ajudantes
    // =====================================================================================
    /** Direção horizontal de um yaw do Minecraft (0 = sul/+Z). */
    public static Vec3 forward(float yaw) {
        float rad = yaw * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(rad), 0, Mth.cos(rad));
    }

    private float yawTo(Vec3 p) {
        double dx = p.x - getX(), dz = p.z - getZ();
        return (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90F;
    }

    private static double horizontalDistSqr(Vec3 a, Vec3 b) {
        double dx = a.x - b.x, dz = a.z - b.z;
        return dx * dx + dz * dz;
    }

    private static BlockState groundState(ServerLevel level, Vec3 p) {
        BlockState g = level.getBlockState(BlockPos.containing(p).below());
        return g.isAir() ? Blocks.STONE.defaultBlockState() : g;
    }

    private static ParticleOptions dust(int rgb, float size) {
        // v1.3: brilho fx aditivo (mesmo tamanho do pó do vanilla; respeita o preset gráfico no cliente)
        return com.renanpontes.isekaimahou.core.ElementFx.glow(rgb, size);
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(7.0, 2.0, 7.0);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 192.0 * 192.0;
    }

    @Override
    public boolean isPickable() { return false; }

    @Override
    public boolean isPushable() { return false; }

    @Override
    public boolean hurt(DamageSource source, float amount) { return false; }

    @Override
    @Nullable
    public Entity getOwner() {
        if (ctx != null && ctx.caster() != null) return ctx.caster();
        if (ownerId != null && level() instanceof ServerLevel sl) return sl.getEntity(ownerId);
        return null;
    }

    // Nunca é salvo (EntityType.noSave): nada a gravar.
    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    // =====================================================================================
    //  GeckoLib
    // =====================================================================================
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<SpectralColossusEntity> base = new AnimationController<>(this, "base", 6,
                s -> s.setAndContinue(isDespawning() ? DESPAWN : SPAWN_IDLE));
        AnimationController<SpectralColossusEntity> action = new AnimationController<>(this, "action", 3, s -> PlayState.STOP);
        for (String a : new String[] {"slash", "slam", "guard"}) {
            action.triggerableAnim(a, RawAnimation.begin().thenPlay("animation.spectral_colossus." + a));
        }
        controllers.add(base, action);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
