package com.renanpontes.isekaimahou.feature.legends_e.entity;

import java.util.UUID;

import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.legends_e.LeFx;
import com.renanpontes.isekaimahou.feature.legends_e.LeRegistry;
import com.renanpontes.isekaimahou.feature.legends_e.LeUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
 * Iä! Cabra Negra: o vento negro (0–50 ticks, puxa a área para o centro e escurece a visão dos inimigos), o
 * SACRIFÍCIO (50: criaturas comuns fracas morrem na hora e a essência delas escorre para o selo), o nascimento dos
 * Filhotes Sombrios (62) e o selo se fechando (até 100).
 */
public class BlackGoatStormEntity extends LeSpellEntity {
    public static final int SACRIFICE = 50;
    public static final int BIRTH = 62;
    public static final int LIFE = 100;
    /** Vida máxima de quem é sacrificado com poder 1. */
    public static final float THRESHOLD = 40F;
    public static final int PER_YOUNG = 6;
    public static final int MAX_YOUNG = 4;
    private static final int MAX_PER_CASTER = 4;

    private static final EntityDataAccessor<Float> DATA_AREA = SynchedEntityData.defineId(BlackGoatStormEntity.class, EntityDataSerializers.FLOAT);

    private UUID owner;
    private int sacrificed;

    public BlackGoatStormEntity(EntityType<? extends BlackGoatStormEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void defineExtraData(SynchedEntityData.Builder b) {
        b.define(DATA_AREA, 9F);
    }

    public float area() { return entityData.get(DATA_AREA); }

    public static void spawn(SpellContext ctx, Vec3 center, SpellStats stats) {
        ServerLevel level = ctx.level();
        float area = Mth.clamp(stats.radius(9F), 5F, 32F);
        if (!LeUtil.claim("black_goat", level, ctx.casterId(), center, area, 60)) return;
        UUID id = ctx.casterId();
        if (id != null && level.getEntities(LeRegistry.BLACK_GOAT_STORM.get(), e -> id.equals(e.owner)).size() >= MAX_PER_CASTER) return;
        BlackGoatStormEntity e = new BlackGoatStormEntity(LeRegistry.BLACK_GOAT_STORM.get(), level);
        e.bind(ctx, stats.copy());
        e.owner = id;
        e.entityData.set(DATA_AREA, area);
        e.setPos(center.x, center.y, center.z);
        level.addFreshEntity(e);
        LeFx.sound(level, center, SoundEvents.WARDEN_EMERGE, 2.5F, 0.6F);
        LeFx.sound(level, center, SoundEvents.GOAT_SCREAMING_AMBIENT, 2.5F, 0.5F);
    }

    @Override
    protected void serverTick(ServerLevel level, SpellContext ctx) {
        Vec3 c = position();
        float area = area();
        if (age < SACRIFICE) {
            if (age % 5 == 0) pull(level, ctx, c, area);
            if (age % 15 == 0) LeFx.sound(level, c, SoundEvents.ELYTRA_FLYING, 2.0F, 0.5F);
            if (age % 20 == 10) LeFx.sound(level, c, SoundEvents.GOAT_SCREAMING_AMBIENT, 1.8F, 0.4F + age / 100F);
            if (age % 2 == 0) {
                double a = age * 0.35, r = area * (0.4 + 0.5 * random.nextDouble());
                LeFx.send(level, Vfx.ink(), c.x + Math.cos(a) * r, c.y + 1 + random.nextDouble() * 4, c.z + Math.sin(a) * r,
                        2, 0.4, 0.4, 0.4, 0.02);
            }
        }
        if (age == SACRIFICE) sacrifice(level, ctx, c, area);
        if (age == BIRTH) birth(level, ctx, c, area);
        if (age > SACRIFICE && age < BIRTH + 20 && age % 2 == 0) {
            LeFx.send(level, Vfx.sculkSoul(), c.x, c.y + 0.3, c.z, 3, area * 0.3, 0.1, area * 0.3, 0.05);
        }
        if (age >= LIFE) discard();
    }

    /** O vento negro: puxa para o centro e cega os inimigos que estão dentro. */
    private void pull(ServerLevel level, SpellContext ctx, Vec3 c, float area) {
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(area, 8, area),
                e -> e.isAlive() && (LeUtil.isFoe(ctx, e) || sacrificable(ctx, e)))) {
            Vec3 d = c.subtract(e.position()).multiply(1, 0, 1);
            double len = d.length();
            if (len > area || len < 1.0) continue;
            if (!Statuses.isBossLike(e)) {
                Vec3 v = d.scale(0.16 / len);
                e.push(v.x, 0.02, v.z);
                e.hurtMarked = true;
            }
            if (e instanceof Player p && LeUtil.isFoe(ctx, p)) p.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0), ctx.caster());
        }
    }

    /**
     * Criatura que pode ser oferecida: INIMIGO comum (não-chefe, não-jogador, não aliado). Bichos pacíficos, aldeões e
     * NPCs das cidades ficam de fora (a não ser que estejam atacando você): no Supremo o vento chega a ~27 blocos e
     * não pode limpar a fazenda/vila de quem conjura perto de casa.
     */
    private static boolean sacrificable(SpellContext ctx, LivingEntity e) {
        if (!(e instanceof Mob) || LeUtil.resistsExecution(e) || ctx.isAlly(e) || e == ctx.caster()) return false;
        return LeUtil.isFoe(ctx, e);
    }

    private void sacrifice(ServerLevel level, SpellContext ctx, Vec3 c, float area) {
        SpellStats s = stats != null ? stats : new SpellStats();
        float limit = THRESHOLD * Mth.clamp(LeUtil.power(ctx, s), 1F, 8F);
        LivingEntity caster = ctx.caster();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(area, 10, area),
                e -> e.isAlive() && e.distanceToSqr(c) <= (area + 2) * (area + 2))) {
            if (sacrificable(ctx, e) && e.getHealth() + e.getAbsorptionAmount() <= limit) {
                Vec3 from = LeUtil.center(e);
                // A essência escorre até o selo
                Vec3 d = c.add(0, 0.5, 0).subtract(from);
                for (int i = 0; i <= 8; i++) {
                    Vec3 q = from.add(d.scale(i / 8.0));
                    LeFx.send(level, LeFx.dust(0x2A0A3A, 1.6F), q.x, q.y, q.z, 2, 0.1, 0.1, 0.1, 0);
                }
                LeFx.send(level, LeFx.fade(0x5AFF3A, 0x000000, 1.3F), from, 20, 0.4, 0.02);
                LeFx.send(level, Vfx.sculkSoul(), from, 4, 0.3, 0.05);
                if (LeUtil.execute(ctx, e, s, this)) sacrificed++;
            } else if (LeUtil.isFoe(ctx, e)) {
                ctx.damage(e, 14F, s, this);
                if (e.isAlive()) {
                    Statuses.apply(e, Statuses.Kind.CORRUPTED, 2, 120, caster, ctx.powerMult());
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1), caster);
                }
            }
        }
        if (caster != null && !s.silent) {
            Announce.send(caster, Component.translatable("announce.isekai_mahou.legends_e.black_goat"), 0x7DFF4A, Announce.STYLE_ULTIMATE);
        }
        LeFx.flash(level, c, area + 24, 0x0A0012, 16, 0.55F);
        BossKit.shake(level, c, area + 24, 1.6F, 26);
        LeFx.ring(level, c.add(0, 0.25, 0), new Vec3(0, 1, 0), area * 1.2F, 0x5AFF3A, 22);
        LeFx.sound(level, c, SoundEvents.WITHER_SPAWN, 2.0F, 0.5F);
        LeFx.sound(level, c, SoundEvents.GOAT_HORN_SOUND_VARIANTS.get(random.nextInt(SoundEvents.GOAT_HORN_SOUND_VARIANTS.size())), 3.0F, 0.5F);
        LeFx.sound(level, c, SoundEvents.WARDEN_ROAR, 2.5F, 0.6F);
    }

    private void birth(ServerLevel level, SpellContext ctx, Vec3 c, float area) {
        SpellStats s = stats != null ? stats : new SpellStats();
        int n = Mth.clamp(Math.max(1, sacrificed / PER_YOUNG), 1, MAX_YOUNG);
        float power = Mth.clamp(LeUtil.power(ctx, s), 1F, 4F);
        for (int i = 0; i < n; i++) {
            double a = Math.PI * 2 * i / n + random.nextDouble() * 0.5;
            double r = n == 1 ? 0 : Math.min(area * 0.5, 3.5);
            DarkYoungEntity.summon(ctx, c.add(Math.cos(a) * r, 0.5, Math.sin(a) * r), power, DarkYoungEntity.LIFE);
        }
        LeFx.sound(level, c, SoundEvents.WARDEN_DIG, 3.0F, 0.5F);
        LeFx.send(level, Vfx.ink(), c.x, c.y + 0.5, c.z, 80, 2.5, 0.5, 2.5, 0.2);
    }

    /** 0..1: força do vento (cresce, segura e some depois do sacrifício). */
    public float wind(float ageF) {
        if (ageF < SACRIFICE) return Mth.clamp(ageF / 12F, 0F, 1F);
        return Mth.clamp(1F - (ageF - SACRIFICE) / 14F, 0F, 1F);
    }

    /** 0..1: o selo no chão (acende com o vento, brilha no sacrifício, fecha no fim). */
    public float sigil(float ageF) {
        float in = Mth.clamp(ageF / 20F, 0F, 1F);
        float out = Mth.clamp((LIFE - ageF) / 20F, 0F, 1F);
        return Math.min(in, out);
    }
}
