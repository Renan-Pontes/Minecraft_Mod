package com.renanpontes.isekaimahou.feature.hueco_mundo.boss;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmFx;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRealm;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmRegistries;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Rei Vazio — soberano do Palácio da Noite Eterna (homenagem ao rei ancião de Hueco Mundo, com nome e golpes próprios).
 * Esqueleto coroado de manto negro e machado enorme; tudo o que ele toca envelhece.
 * <ul>
 *   <li>Fase 1: <b>Corte do Trono</b> (leque curto), <b>Grande Queda</b> (arremessa o machado em linha), <b>Sopro da
 *       Senescência</b> (cone que seca: definhar, fraqueza, lentidão), <b>Legião de Ossos</b> (Vazios Rastejantes).</li>
 *   <li>Fase 2 (60%): <b>Ressurreição — Coroa do Fim</b>: vira só ossos, mais rápido; <b>Poças da Decadência</b>
 *       seguem cada jogador e ficam no chão.</li>
 *   <li>v1.3 — <b>Ruína do Trono</b> (fase 2+): ergue o machado (cinzas se juntam na lâmina), crava no chão e QUATRO
 *       ondas de decadência se espalham em anéis; quem pisa onde a onda já passou escapa das seguintes.</li>
 *   <li>Fase 3 (25%): <b>Respira</b> — o domínio da decadência cobre a arena; só as zonas verdes salvam. Depois ele
 *       fica exausto (janela de dano).</li>
 * </ul>
 */
public class HollowKing extends HmBoss {
    private static final ResourceLocation SPEED_ID = IsekaiMahou.id("hm_hollow_king_resurrection");
    private int busy, cleaveCd = 20, throwCd = 60, breathCd = 100, legionCd = 160, poolsCd = 60, respiraCd = 80, ruinCd = 140, exhausted;

    public HollowKing(EntityType<? extends HollowKing> type, Level level) {
        super(type, level);
    }

    /**
     * v1.3.2 — o Rei de Hueco Mundo agora é o próprio Barragan. Um Rei Vazio que já existe (mundo salvo, ovo, comando)
     * vira, no primeiro tick do servidor, um Barragan JÁ em Arrogante (o rei de ossos, mesmo modelo) no mesmo lugar,
     * com a mesma arena/vaga do palácio. Sem o módulo espada, continua sendo o Rei Vazio de sempre.
     */
    @Override
    public void tick() {
        if (!level().isClientSide && !isRemoved() && isAlive() && tickCount > 2 && level() instanceof ServerLevel sl && convertToBarragan(sl)) return;
        super.tick();
    }

    private boolean convertToBarragan(ServerLevel sl) {
        var type = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("barragan"));
        if (type.isEmpty()) return false;
        net.minecraft.world.entity.Entity e = type.get().create(sl);
        if (!(e instanceof net.minecraft.world.entity.Mob king)) return false;
        king.moveTo(getX(), getY(), getZ(), getYRot(), 0F);
        king.setYHeadRot(getYRot());
        king.setYBodyRot(getYRot());
        // Contrato com o módulo espada (EspadaBoss.START_FORM_KEY): nasce já na forma 1 = Arrogante.
        king.getPersistentData().putInt("isekai_mahou.espada.start_form", 1);
        Vec3 arena = BossKit.arenaCenter(this);
        if (isSiteBoss()) {
            com.renanpontes.isekaimahou.feature.hueco_mundo.HmSites.markSiteKing(king, arena != null ? arena : position(),
                    BossKit.arenaRadius(this) > 0 ? BossKit.arenaRadius(this) : com.renanpontes.isekaimahou.feature.hueco_mundo.HmSites.KING_ARENA);
        } else if (arena != null) {
            BossKit.setArena(king, arena, BossKit.arenaRadius(this));
        }
        if (hasCustomName()) king.setCustomName(getCustomName());
        king.finalizeSpawn(sl, sl.getCurrentDifficultyAt(blockPosition()), net.minecraft.world.entity.MobSpawnType.CONVERSION, null);
        sl.addFreshEntity(king);
        sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.5, getZ(), 30, 0.8, 1.2, 0.8, 0.03);
        bar().removeAllPlayers();
        discard();
        return true;
    }

    public static AttributeSupplier.Builder createAttributes() {
        return bossAttributes(520, 14, 0.26, 14);
    }

    @Override
    public String bossId() {
        return "hollow_king";
    }

    @Override
    public int bossColor() {
        return 0xC9A23A;
    }

    @Override
    public HmRealm side() {
        return HmRealm.HOLLOW;
    }

    @Override
    protected BossEvent.BossBarColor barColor() {
        return BossEvent.BossBarColor.YELLOW;
    }

    @Override
    protected float[] phaseThresholds() {
        return new float[] {0.6F, 0.25F};
    }

    @Override
    protected List<String> actionAnimations() {
        return List.of("cleave", "throw", "breath", "summon", "respira", "resurrect", "slam");
    }

    @Override
    public String textureSuffix() {
        return phase() >= 1 ? "_p2" : "";
    }

    @Override
    protected boolean isBusy() {
        return busy > 0;
    }

    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || busy > 0;
    }

    @Override
    protected float siteYaw() {
        return 0F;
    }

    @Override
    protected void onPhaseChange(ServerLevel sl, int phase) {
        super.onPhaseChange(sl, phase);
        if (phase == 1) {
            action("resurrect");
            busy = 40;
            AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null && speed.getModifier(SPEED_ID) == null) {
                speed.addPermanentModifier(new AttributeModifier(SPEED_ID, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            }
            HmFx.sphere(sl, position().add(0, 1.6, 0), 2.5, 60, HmFx.dust(bossColor(), 1.8F));
            sl.sendParticles(ParticleTypes.SOUL, getX(), getY() + 1.5, getZ(), 40, 1.0, 1.2, 1.0, 0.05);
            playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 2.0F, 0.4F);
            hintOnce("resurrect");
        } else if (phase == 2) {
            respiraCd = 30;
            hintOnce("respira");
        }
    }

    @Override
    protected void onReset(ServerLevel sl) {
        busy = 0;
        exhausted = 0;
        AttributeInstance speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) speed.removeModifier(SPEED_ID);
    }

    @Override
    protected void tickCombat(ServerLevel sl, @Nullable LivingEntity target) {
        if (exhausted > 0) {
            exhausted--;
            if (exhausted % 10 == 0) sl.sendParticles(ParticleTypes.SMOKE, getX(), getY() + 2.6, getZ(), 6, 0.3, 0.2, 0.3, 0.01);
        }
        if (busy > 0) {
            busy--;
            getNavigation().stop();
            return;
        }
        if (cleaveCd > 0) cleaveCd--;
        if (throwCd > 0) throwCd--;
        if (breathCd > 0) breathCd--;
        if (legionCd > 0) legionCd--;
        if (poolsCd > 0) poolsCd--;
        if (respiraCd > 0) respiraCd--;
        if (ruinCd > 0) ruinCd--;
        if (target == null) return;
        double d = distanceTo(target);
        if (phase() >= 2 && respiraCd == 0) {
            respira(sl);
        } else if (phase() >= 1 && ruinCd == 0 && d < 16) {
            throneRuin(sl);
        } else if (phase() >= 1 && poolsCd == 0) {
            decayPools(sl);
        } else if (legionCd == 0) {
            boneLegion(sl);
        } else if (d < 4.8 && cleaveCd == 0) {
            cleave(sl, target);
        } else if (d < 12 && breathCd == 0 && hasLineOfSight(target)) {
            breath(sl, target);
        } else if (d > 6 && d < 24 && throwCd == 0 && hasLineOfSight(target)) {
            axeThrow(sl, target);
        } else {
            chase(target, 1.0);
        }
    }

    private void cleave(ServerLevel sl, LivingEntity t) {
        cleaveCd = 30;
        busy = 16;
        float yaw = yawTo(t.position());
        holdYaw(yaw);
        action("cleave");
        float dmg = scaled(14F);
        Telegraph.delayed(sl, Telegraph.Shape.cone(position(), yaw, 120F, 5.2F).warmup(12).color(Telegraph.DANGER).owner(this).noRunes(), hits -> {
            hurtAll(hits, dmg, false);
            for (LivingEntity e : hits) if (isFoe(e)) e.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 0), this);
            HmFx.ring(sl, position().add(0, 1.0, 0), 3.5, 24, ParticleTypes.SWEEP_ATTACK, 0);
            playSound(SoundEvents.PLAYER_ATTACK_SWEEP, 1.6F, 0.5F);
        });
    }

    private void breath(ServerLevel sl, LivingEntity t) {
        breathCd = 140;
        busy = 30;
        float yaw = yawTo(t.position());
        holdYaw(yaw);
        action("breath");
        float dmg = scaled(8F);
        Telegraph.delayed(sl, Telegraph.Shape.cone(position(), yaw, 70F, 12F).warmup(24).color(0x8A7A5A).owner(this), hits -> {
            hurtAll(hits, dmg, true);
            for (LivingEntity e : hits) {
                if (!isFoe(e)) continue;
                e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1), this);
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0), this);
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 1), this);
                e.addEffect(new MobEffectInstance(CoreRegistries.EXPOSED, 120, 0), this);
            }
            Vec3 dir = Vec3.directionFromRotation(0, yaw);
            for (int i = 1; i <= 6; i++) {
                Vec3 p = position().add(dir.scale(i * 2)).add(0, 1.4, 0);
                sl.sendParticles(ParticleTypes.ASH, p.x, p.y, p.z, 12, i * 0.3, 0.4, i * 0.3, 0.02);
                sl.sendParticles(HmFx.dust(0xB8A77A, 1.6F), p.x, p.y, p.z, 4, i * 0.25, 0.3, i * 0.25, 0);
            }
            playSound(SoundEvents.WITHER_SHOOT, 1.4F, 0.5F);
        });
        hintOnce("breath");
    }

    private void axeThrow(ServerLevel sl, LivingEntity t) {
        throwCd = 110;
        busy = 26;
        Vec3 dir = t.position().subtract(position()).multiply(1, 0, 1).normalize();
        holdYaw(yawTo(t.position()));
        Vec3 from = position();
        Vec3 to = lineEnd(sl, from, dir, 22);
        action("throw");
        float dmg = scaled(16F);
        Telegraph.delayedLine(sl, from, to, 2.6F, 20, 0xC9A23A, hits -> {
            hurtAll(hits, dmg, false);
            HmFx.line(sl, from.add(0, 1.2, 0), to.add(0, 1.2, 0), HmFx.dust(0xC9A23A, 1.8F), 2.0);
            sl.sendParticles(ParticleTypes.EXPLOSION, to.x, to.y + 0.5, to.z, 1, 0, 0, 0, 0);
            playSound(SoundEvents.TRIDENT_THROW.value(), 1.6F, 0.5F);
        });
    }

    private void boneLegion(ServerLevel sl) {
        legionCd = 600;
        busy = 20;
        action("summon");
        summonMinions(sl, HmRegistries.HOLLOW_CRAWLER.get(), 3, 6);
        playSound(SoundEvents.WITHER_SKELETON_AMBIENT, 2.0F, 0.6F);
        hintOnce("legion");
    }

    private void decayPools(ServerLevel sl) {
        poolsCd = 200;
        busy = 20;
        action("breath");
        float dmg = scaled(4F);
        List<ServerPlayer> targets = new ArrayList<>(BossKit.partyAround(this, 40));
        for (int i = 0; i < Math.min(4, targets.size()); i++) {
            ServerPlayer p = targets.get(i);
            int[] id = new int[1];
            id[0] = Telegraph.delayed(sl, Telegraph.Shape.follow(p, 3.2F).warmup(40).hold(80).color(0x6E5A2E).owner(this), hits -> {
                Vec3 at = Telegraph.centerOf(id[0]);
                if (at == null) return;
                hurtAll(hits, dmg * 2, true);
                for (int k = 10; k <= 80; k += 10) {
                    BossKit.schedule(sl, k, () -> {
                        if (!isAlive()) return;
                        sl.sendParticles(ParticleTypes.ASH, at.x, at.y + 0.3, at.z, 10, 1.6, 0.1, 1.6, 0.01);
                        for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.circle(at, 3.2F))) {
                            if (!isFoe(e)) continue;
                            e.hurt(damageSources().indirectMagic(this, this), dmg);
                            e.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 1), this);
                        }
                    });
                }
            });
        }
        hintOnce("pools");
    }

    private void respira(ServerLevel sl) {
        respiraCd = 420;
        busy = 80;
        action("respira");
        Vec3 c = arenaCenter() != null ? arenaCenter() : position();
        double r = arenaRadius() + 4;
        Telegraph.show(sl, Telegraph.Shape.circle(c, (float) r).warmup(70).color(0x6E5A2E).owner(this).noRunes());
        List<Vec3> safes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Vec3 s = arenaSpot(sl, 5, Math.max(7, arenaRadius() - 2));
            if (s == null) continue;
            safes.add(s);
            Telegraph.safe(sl, s, 3.2F, 70);
        }
        announce("respira");
        float dmg = scaled(22F);
        BossKit.schedule(sl, 70, () -> {
            if (!isAlive()) return;
            for (ServerPlayer p : BossKit.partyAround(this, r + 6)) {
                if (!isFoe(p)) continue;
                boolean safe = false;
                for (Vec3 s : safes) if (Telegraph.Shape.safe(s, 3.2F).contains(p)) safe = true;
                if (safe) continue;
                p.hurt(damageSources().indirectMagic(this, this), dmg);
                p.addEffect(new MobEffectInstance(MobEffects.WITHER, 160, 2), this);
                p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 200, 1), this);
            }
            HmFx.ring(sl, c.add(0, 0.5, 0), r * 0.5, 48, ParticleTypes.ASH, 0.05);
            HmFx.ring(sl, c.add(0, 0.5, 0), r * 0.9, 64, HmFx.dust(0x6E5A2E, 2.0F), 0.0);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 3.0F, 0.5F);
            BossKit.shake(sl, c, r + 8, 1.8F, 30);
            exhausted = 60;
            busy = 60;
            hintOnce("exhausted");
        });
    }

    /**
     * Ruína do Trono: antecipação (1,5 s: cinzas e ouro convergem para o machado, ronco subindo), impacto (clarão,
     * cratera de pó de osso, tremor forte) e quatro anéis de decadência em sequência; rescaldo com cinza e ossos
     * caindo devagar na arena.
     */
    private void throneRuin(ServerLevel sl) {
        ruinCd = 340;
        busy = 60;
        action("slam");
        Vec3 c = position();
        for (int t = 0; t < 30; t += 3) {
            final int tick = t;
            BossKit.schedule(sl, t, () -> {
                if (!isAlive()) return;
                double r = 6.0 * (1 - tick / 30.0) + 0.8;
                Vec3 hand = position().add(0, 3.6 + tick * 0.02, 0);
                HmFx.ring(sl, hand, r, 18, ParticleTypes.ASH, 0.0);
                HmFx.ring(sl, hand, r * 0.6, 10, HmFx.dust(0xC9A23A, 1.4F), 0.0);
                if (tick % 9 == 0) playSound(SoundEvents.WITHER_AMBIENT, 1.6F, 0.4F + tick * 0.02F);
            });
        }
        float dmg = scaled(13F);
        for (int k = 0; k < 4; k++) {
            final float inner = k == 0 ? 0F : 2F + 5F * k - 1F, outer = 2F + 5F * (k + 1);
            final int warm = 30 + k * 7;
            Telegraph.delayedRing(sl, c, inner, outer, warm, 0x6E5A2E, hits -> {
                if (!isAlive()) return;
                hurtAll(hits, dmg, true);
                for (LivingEntity e : hits) {
                    if (!isFoe(e)) continue;
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.55, 0));
                    e.hurtMarked = true;
                    e.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1), this);
                }
                double mid = (inner + outer) / 2;
                HmFx.ring(sl, c.add(0, 0.3, 0), mid, 48, new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                        net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState()), 0.15);
                HmFx.ring(sl, c.add(0, 0.6, 0), mid, 40, HmFx.dust(0x6E5A2E, 2.0F), 0.0);
                playSound(SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.5F + inner * 0.02F);
            });
        }
        BossKit.schedule(sl, 30, () -> {
            if (!isAlive()) return;
            sl.sendParticles(ParticleTypes.FLASH, c.x, c.y + 1, c.z, 2, 0.2, 0.2, 0.2, 0);
            sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y + 0.5, c.z, 1, 0, 0, 0, 0);
            sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK,
                    net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState()), c.x, c.y + 0.4, c.z, 70, 1.6, 0.3, 1.6, 0.35);
            playSound(SoundEvents.ANVIL_LAND, 2.0F, 0.4F);
            playSound(SoundEvents.WARDEN_SONIC_BOOM, 1.4F, 0.6F);
            BossKit.shake(sl, c, 34, 2.4F, 24);
        });
        // Rescaldo: cinza e lascas de osso caindo devagar sobre a arena.
        for (int t = 60; t <= 120; t += 10) {
            BossKit.schedule(sl, t, () -> {
                double r = arenaRadius();
                sl.sendParticles(ParticleTypes.ASH, c.x, c.y + 5, c.z, 30, r * 0.6, 2.0, r * 0.6, 0.0);
                sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.FALLING_DUST,
                        net.minecraft.world.level.block.Blocks.BONE_BLOCK.defaultBlockState()), c.x, c.y + 6, c.z, 12, r * 0.5, 1.0, r * 0.5, 0);
            });
        }
        announce("ruin");
        hintOnce("ruin");
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        // Exausto depois do Respira: janela de dano extra.
        return super.hurt(source, exhausted > 0 ? amount * 1.3F : amount);
    }

    @Override
    protected void dropBossLoot(ServerLevel level) {
        spawnAtLocation(new ItemStack(HmRegistries.DECAY_AXE.get()));
        spawnAtLocation(new ItemStack(HmRegistries.ARRANCAR_SOUL_SHARD.get(), 2 + random.nextInt(3)));
        spawnAtLocation(new ItemStack(HmRegistries.REISHI_CRYSTAL.get(), 2 + random.nextInt(2)));
        spawnAtLocation(new ItemStack(HmRegistries.GARGANTA_KEY.get(), 1));
        dropEssence(Element.SHADOW, 3, 5);
        dropEssence(Element.ARCANE, 1, 2);
        dropScroll("hollow_gran_cero");
        if (random.nextFloat() < 0.5F) dropScroll("visored_mask");
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.WITHER_SKELETON_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.WITHER_SKELETON_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.WITHER_DEATH;
    }

    @Override
    public float getVoicePitch() {
        return 0.5F;
    }
}
