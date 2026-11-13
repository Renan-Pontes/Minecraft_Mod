package com.renanpontes.isekaimahou.feature.class_hollow.tech;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_hollow.Hollow;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowFx;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowNetwork;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowSounds;
import com.renanpontes.isekaimahou.feature.class_hollow.HollowState;
import com.renanpontes.isekaimahou.feature.class_hollow.glyph.HollowBeam;
import com.renanpontes.isekaimahou.feature.hueco_mundo.HmTravel;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * O que cada técnica do Hollow FAZ (servidor). Usado pelas técnicas inatas ({@link HollowTechniques}, com a carga da
 * tecla) e pelos glifos antigos numa magia salva (carga cheia, e aí os aumentos da receita também valem).
 * Todo dano passa pelo {@link SpellContext} (poder do rank, da Resurrección, da fome, aliados, reações).
 */
public final class HollowMoves {
    // =====================================================================================
    //  Ajudantes
    // =====================================================================================
    static int rankOf(LivingEntity caster) {
        return caster instanceof Player p && Hollow.isHollow(p) ? Hollow.rank(MagicApi.get(p)) : 0;
    }

    public static int ceroRgb(LivingEntity caster) {
        return caster instanceof Player p ? Hollow.ceroRgb(MagicApi.get(p)) : 0xE01030;
    }

    /** O Arrancar (máscara partida) dispara da ponta dos dedos; os outros, da boca da máscara. */
    public static boolean fromHand(LivingEntity caster) {
        return rankOf(caster) >= Hollow.ARRANCAR;
    }

    /** De onde sai o Cero: boca (Hollow) ou ponta dos dedos da mão direita (Arrancar). Igual no cliente. */
    public static Vec3 muzzle(LivingEntity caster, Vec3 look, boolean hand) {
        Vec3 eye = caster.getEyePosition();
        if (!hand) return eye.add(look.scale(0.7)).add(0, -0.15, 0);
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        return eye.add(look.scale(0.85)).add(right.scale(0.38)).add(0, -0.4, 0);
    }

    private static Vec3 from(SpellContext ctx, LivingEntity caster, Vec3 look) {
        return ctx.hasOrigin() ? ctx.eyePos() : muzzle(caster, look, fromHand(caster));
    }

    // =====================================================================================
    //  Cero
    // =====================================================================================
    /**
     * Cero: feixe que atravessa todos os inimigos e explode no fim. {@code charge} 0..1 (carga da tecla; 1 = cheio):
     * mais grosso, mais longe e mais forte. Murciélago liberado: a Lanza del Relámpago (mais grossa, ×1,5).
     */
    public static void cero(SpellContext ctx, float charge) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        int rank = rankOf(caster);
        float c = Math.max(0F, Math.min(1F, charge));
        boolean lance = caster instanceof Player p && HollowState.released(p) == Hollow.Release.MURCIELAGO;
        double range = 26 + 3 * rank + 8 * c + 4 * Math.max(0, s.speed);
        float width = (0.9F + 0.25F * rank) * (0.75F + 0.5F * c) + 0.4F * Math.max(0, s.aoe) + (lance ? 0.6F : 0F);
        float dmg = (6F + 1.5F * rank) * (0.65F + 0.6F * c) * (lance ? 1.5F : 1F);
        int rgb = ceroRgb(caster);
        Vec3 look = ctx.look();
        MahouAnims.play(caster, MahouAnims.CERO_FIRE);
        HollowBeam.Shot shot = HollowBeam.fire(ctx, caster, from(ctx, caster, look), look, range, width, dmg, 1.5F + width + (lance ? 2F : 0F),
                s.ghost, rgb, false);
        // w10: a Lanza del Relámpago termina numa explosão GIGANTE (e a onda de choque logo depois)
        if (lance && shot != null) lanceBlast(ctx, caster, shot.end(), dmg, c, rgb);
        if (caster instanceof ServerPlayer sp) {
            MagicApi.addCounter(sp, Hollow.CEROS, 1);
            if (!s.silent && ctx.stage() <= 0 && c >= 0.5F) {
                Announce.send(sp, Hollow.msg(lance ? "lance_shout" : "cero_shout"), rgb, Announce.STYLE_SPELL);
            }
        }
    }

    /**
     * w10 — a explosão da Lanza del Relámpago (Murciélago): no fim do feixe, clarão, pilar de luz verde de 28 blocos,
     * onda de choque e tremor; dano em área (raio 6 + 3 x carga, 60% do Cero, cai com a distância) e, 8 ticks depois,
     * uma ONDA DE CHOQUE até 2,4 x o raio (20%, empurra). Não quebra blocos. No máx. 24 alvos por onda.
     */
    private static void lanceBlast(SpellContext ctx, LivingEntity caster, Vec3 at, float dmg, float charge, int rgb) {
        ServerLevel level = ctx.level();
        float radius = 6F + 3F * charge;
        HollowNetwork.fxNear(level, HollowNetwork.CERO_BEAM, caster.getId(), at, at.add(0, 28, 0), radius * 0.3F, rgb, 128);
        HollowNetwork.fxNear(level, HollowNetwork.SHOCKWAVE, caster.getId(), at, Vec3.ZERO, radius * 1.4F, rgb, 128);
        HollowNetwork.fxNear(level, HollowNetwork.SHAKE, caster.getId(), at, Vec3.ZERO, 1.8F, rgb, 72);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 0.5, at.z, 2, radius * 0.25, 0.5, radius * 0.25, 0);
        level.sendParticles(HollowFx.dust(rgb, 2.2F), at.x, at.y + 1, at.z, 60, radius * 0.45, radius * 0.4, radius * 0.45, 0.1);
        level.sendParticles(ParticleTypes.END_ROD, at.x, at.y + 1, at.z, 30, radius * 0.3, radius * 0.5, radius * 0.3, 0.2);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y + 0.5, at.z, 40, radius * 0.5, 1.0, radius * 0.5, 0.3);
        HollowFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 3.0F, 0.5F);
        HollowFx.sound(level, at, SoundEvents.LIGHTNING_BOLT_THUNDER, 2.5F, 0.9F);
        int n = 0;
        for (LivingEntity e : ctx.enemiesAround(at, radius)) {
            if (n++ >= 24) break;
            float k = (float) Math.max(0.4, Math.min(1.0, 1.15 - e.position().distanceTo(at) / radius));
            ctx.damage(e, dmg * 0.6F * k, ctx.formStats(), caster);
            push(e, at, 1.4 * k, 0.6 * k);
        }
        SpellScheduler.schedule(8, () -> {
            if (caster.level() != level) return;
            float outer = radius * 2.4F;
            HollowNetwork.fxNear(level, HollowNetwork.SHOCKWAVE, caster.getId(), at.add(0, 0.1, 0), Vec3.ZERO, outer, rgb, 128);
            HollowNetwork.fxNear(level, HollowNetwork.SHAKE, caster.getId(), at, Vec3.ZERO, 0.9F, rgb, 64);
            HollowFx.sound(level, at, SoundEvents.WARDEN_SONIC_BOOM, 1.6F, 0.7F);
            int m = 0;
            for (LivingEntity e : ctx.enemiesAround(at, outer)) {
                double d = e.position().distanceTo(at);
                if (d < radius * 0.5) continue;
                if (m++ >= 24) break;
                ctx.damage(e, dmg * 0.2F, ctx.formStats(), caster);
                push(e, at, 1.0, 0.4);
            }
        });
    }

    /** Empurra para longe de {@code from}. */
    private static void push(LivingEntity e, Vec3 from, double strength, double up) {
        Vec3 d = e.position().subtract(from).multiply(1, 0, 1);
        d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
        e.setDeltaMovement(e.getDeltaMovement().add(d.x * strength, up, d.z * strength));
        e.hurtMarked = true;
    }

    // =====================================================================================
    //  Desgarrón (Pantera)
    // =====================================================================================
    /**
     * w10 — Desgarrón da Pantera: o golpe solta 3 marcas de garra azuis LANÇADAS para a frente (20 blocos, 1,5
     * bloco/tick; param em parede) que atravessam os inimigos — cada garra corta cada alvo uma vez (máx. 10), faz
     * sangrar e empurra. Dano de cada corte: {@code dmg}; {@code skip} (o alvo do soco) não leva o corte. Usa dano de "ataque de criatura" (não reativa o soco).
     */
    public static void desgarron(ServerPlayer sp, float dmg, @Nullable Entity skip) {
        ServerLevel sl = sp.serverLevel();
        Vec3 look = sp.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, sp.getYRot()) : flat.normalize();
        final Vec3 dir = flat;
        double range = 20.0, speed = 1.5;
        Vec3 base = sp.position().add(0, 1.1, 0).add(dir.scale(0.8));
        BlockHitResult wall = sl.clip(new ClipContext(base, base.add(dir.scale(range)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        double reach = wall.getType() == HitResult.Type.BLOCK ? base.distanceTo(wall.getLocation()) : range;
        int rgb = Hollow.Release.PANTERA.rgb;
        HollowNetwork.fxNear(sl, HollowNetwork.CLAWS, sp.getId(), base, dir.scale(reach), (float) speed, rgb, 96);
        HollowFx.sound(sl, base, SoundEvents.PLAYER_ATTACK_SWEEP, 1.2F, 0.6F);
        HollowFx.sound(sl, base, SoundEvents.ENDER_DRAGON_FLAP, 0.6F, 1.6F);
        Set<Integer> cut = new HashSet<>();
        if (skip != null) cut.add(skip.getId());
        int steps = (int) Math.ceil(reach / speed);
        for (int t = 1; t <= steps; t++) {
            final double d = Math.min(reach, t * speed);
            SpellScheduler.schedule(t, () -> {
                if (!sp.isAlive() || sp.level() != sl || cut.size() >= 10) return;
                Vec3 c = base.add(dir.scale(d));
                AABB box = new AABB(c, c).inflate(1.8, 1.6, 1.8);
                for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, e -> e != sp && e.isAlive() && !cut.contains(e.getId())
                        && !(e instanceof Player other && (other.isAlliedTo(sp) || other.isCreative() || !sl.getServer().isPvpAllowed()))
                        && !(e instanceof OwnableEntity own && sp.getUUID().equals(own.getOwnerUUID())))) {
                    if (cut.size() >= 10) break;
                    cut.add(e.getId());
                    if (e.hurt(sp.damageSources().mobAttack(sp), dmg)) {
                        Statuses.apply(e, Statuses.Kind.BLEEDING, 1, 80, sp, 1F);
                        push(e, c.subtract(dir), 0.5, 0.15);
                        sl.sendParticles(HollowFx.dust(rgb, 1.2F), e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 6, 0.2, 0.3, 0.2, 0.05);
                    }
                }
            });
        }
    }

    /**
     * Cero Oscuras (Arrancar liberado, carga máxima): o Cero NEGRO com borda na cor do arquétipo — atravessa paredes,
     * definha quem toca e deixa um vazio no fim que pulsa 3 vezes.
     */
    public static void oscuras(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        int rgb = ceroRgb(caster);
        Vec3 look = ctx.look();
        MahouAnims.play(caster, MahouAnims.CERO_FIRE);
        HollowBeam.Shot shot = HollowBeam.fire(ctx, caster, from(ctx, caster, look), look, 56, 2.6F, 24F, 5F, true, rgb, true,
                HollowNetwork.OSCURAS_BEAM);
        for (LivingEntity e : shot.struck()) e.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1), caster);
        Vec3 end = shot.end();
        // o vazio que fica: 3 pulsos (1 s) de 25% do dano em volta do ponto final
        for (int i = 1; i <= 3; i++) {
            SpellScheduler.schedule(20 * i, () -> {
                if (caster.isRemoved() || caster.level() != level) return;
                for (LivingEntity e : ctx.enemiesAround(end, 4.0)) ctx.damage(e, 6F, ctx.formStats(), caster);
                HollowNetwork.fxNear(level, HollowNetwork.SHOCKWAVE, caster.getId(), end, Vec3.ZERO, 4F, 0x1A0010, 64);
                level.sendParticles(HollowFx.BLACK, end.x, end.y, end.z, 16, 1.2, 1.2, 1.2, 0.05);
                HollowFx.sound(level, end, SoundEvents.WARDEN_HEARTBEAT, 1.2F, 0.5F);
            });
        }
        HollowFx.sound(level, caster.position(), SoundEvents.WITHER_SHOOT, 1.0F, 0.5F);
        if (caster instanceof ServerPlayer sp) {
            MagicApi.addCounter(sp, Hollow.CEROS, 1);
            Announce.send(sp, Hollow.msg("oscuras_shout"), rgb, Announce.STYLE_ULTIMATE);
        }
    }

    // =====================================================================================
    //  Gran Rey Cero
    // =====================================================================================
    /**
     * Gran Rey Cero: o Cero misturado ao próprio sangue — feixe COLOSSAL de 64 blocos que atravessa paredes e treme o
     * chão. Paga 2 de vida (o sangue). Liberado: ×1,5. {@code charge} 0..1 acima da carga mínima (até +15%).
     */
    public static void granRey(SpellContext ctx, float charge) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        boolean released = caster instanceof Player p && HollowState.released(p) != null;
        float dmg = 22F * (released ? 1.5F : 1F) * (0.9F + 0.15F * Math.max(0F, Math.min(1F, charge)));
        int rgb = ceroRgb(caster);
        Vec3 look = ctx.look();
        float width = 3.2F + 0.5F * Math.max(0, ctx.formStats().aoe);
        MahouAnims.play(caster, MahouAnims.CERO_FIRE);
        caster.hurt(caster.damageSources().generic(), 2F);
        HollowBeam.fire(ctx, caster, from(ctx, caster, look), look, 64, width, dmg, 7F, true, rgb, true);
        if (caster instanceof ServerPlayer sp && !ctx.formStats().silent) {
            Announce.send(sp, Hollow.msg("gran_rey_shout"), rgb, Announce.STYLE_ULTIMATE);
        }
        // recuo: o corpo é empurrado para trás
        caster.setDeltaMovement(caster.getDeltaMovement().add(look.scale(-0.6)));
        caster.hurtMarked = true;
    }

    // =====================================================================================
    //  Bala
    // =====================================================================================
    /** Bala: Reiatsu socado num projétil instantâneo, rápido e fraco, até 24 blocos (+1 tiro por Dividir). Empurra. */
    public static void bala(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        int shots = 1 + Math.max(0, s.split);
        int rank = rankOf(caster);
        int rgb = ceroRgb(caster);
        Vec3 look = ctx.look();
        Vec3 from = ctx.hasOrigin() ? ctx.eyePos() : caster.getEyePosition().add(look.scale(0.6)).add(0, -0.25, 0);
        for (int i = 0; i < shots; i++) {
            Vec3 dir = i == 0 ? look : look.add((ctx.random().nextFloat() - 0.5) * 0.12, (ctx.random().nextFloat() - 0.5) * 0.08,
                    (ctx.random().nextFloat() - 0.5) * 0.12).normalize();
            Vec3 to = from.add(dir.scale(24 + 4 * Math.max(0, s.speed)));
            HitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (!s.ghost && block.getType() == HitResult.Type.BLOCK) to = block.getLocation();
            AABB box = new AABB(from, to).inflate(0.6);
            EntityHitResult hit = ProjectileUtil.getEntityHitResult(level, caster, from, to, box,
                    e -> e instanceof LivingEntity le && le.isAlive() && e != caster && !ctx.isAlly(e) && !ctx.ignores(e), 0.35F);
            Vec3 end = to;
            if (hit != null) {
                LivingEntity t = (LivingEntity) hit.getEntity();
                end = hit.getLocation();
                ctx.impact(end, dir, t);
                ctx.damage(t, 2.5F + 0.5F * rank, s, caster);
                if (t.isAlive() && !ctx.spell().effects().isEmpty()) ctx.resolveOnEntity(t, caster);
                t.setDeltaMovement(t.getDeltaMovement().add(dir.x * 0.35, 0.08, dir.z * 0.35));
                t.hurtMarked = true;
                level.sendParticles(HollowFx.dust(rgb, 1.0F), end.x, end.y, end.z, 4, 0.15, 0.15, 0.15, 0.05);
            }
            HollowNetwork.fxNear(level, HollowNetwork.BALA, caster.getId(), from, end, 1F, rgb, 64);
            ctx.end(end, dir);
        }
        MahouAnims.play(caster, MahouAnims.CAST_BOLT);
        HollowFx.sound(level, from, SoundEvents.SHULKER_SHOOT, 0.7F, 1.8F);
        HollowFx.sound(level, from, SoundEvents.FIREWORK_ROCKET_BLAST, 0.5F, 1.6F);
    }

    // =====================================================================================
    //  Pesquisa
    // =====================================================================================
    /** Pesquisa: um pulso revela tudo o que vive a 32 blocos (+8 por Expandir), através das paredes, por 10 s. */
    public static void pesquisa(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        double r = 32 + 8 * Math.max(0, s.aoe);
        int ticks = s.duration(200);
        List<LivingEntity> found = level.getEntitiesOfClass(LivingEntity.class, caster.getBoundingBox().inflate(r),
                e -> e != caster && e.isAlive() && !e.isSpectator());
        int n = Math.min(128, found.size());
        int[] ids = new int[n];
        byte[] kinds = new byte[n];
        for (int i = 0; i < n; i++) {
            LivingEntity e = found.get(i);
            ids[i] = e.getId();
            kinds[i] = (byte) (e.getMaxHealth() >= 80F ? 3 : e instanceof Player ? 2 : e instanceof Enemy ? 1 : 0);
        }
        if (caster instanceof ServerPlayer sp) {
            PacketDistributor.sendToPlayer(sp, new HollowNetwork.Sense(ids, kinds, ticks));
            sp.displayClientMessage(Hollow.msg("pesquisa", n), true);
        }
        HollowNetwork.fx(caster, HollowNetwork.PESQUISA, caster.position().add(0, 1, 0), Vec3.ZERO, (float) r, Hollow.REIATSU_RGB);
        MahouAnims.play(caster, MahouAnims.KNEEL_CHANNEL);
        HollowFx.sound(level, caster.position(), SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.2F);
        HollowFx.sound(level, caster.position(), SoundEvents.SCULK_CLICKING, 1.0F, 0.6F);
        ctx.end(caster.position().add(0, 1, 0), ctx.look());
    }

    // =====================================================================================
    //  Regeneração
    // =====================================================================================
    /** Regeneração Instantânea: cura 30% (40% do Vasto Lorde em diante). Paga 5 Almas; sem Almas, a fome. false = não pagou. */
    public static boolean regen(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return false;
        ServerLevel level = ctx.level();
        if (caster instanceof ServerPlayer sp && !sp.getAbilities().instabuild) {
            MagicData d = MagicApi.get(sp);
            if (Hollow.souls(d) >= 5) {
                MagicApi.addCounter(sp, Hollow.SOULS, -5);
            } else if (sp.getFoodData().getFoodLevel() >= 6) {
                sp.getFoodData().setFoodLevel(sp.getFoodData().getFoodLevel() - 6);
                sp.displayClientMessage(Hollow.msg("regen_hunger").withStyle(ChatFormatting.DARK_RED), true);
            } else {
                sp.displayClientMessage(Hollow.msg("regen_nothing").withStyle(ChatFormatting.RED), true);
                return false;
            }
        }
        float pct = rankOf(caster) >= Hollow.VASTO_LORDE ? 0.4F : 0.3F;
        caster.heal(caster.getMaxHealth() * pct * Math.min(2F, ctx.formStats().power()));
        caster.clearFire();
        caster.removeEffect(MobEffects.POISON);
        caster.removeEffect(MobEffects.WITHER);
        HollowNetwork.fx(caster, HollowNetwork.REGEN, caster.position(), Vec3.ZERO, 1F, 0xB01020);
        level.sendParticles(HollowFx.REIATSU, caster.getX(), caster.getY() + 1, caster.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        HollowFx.sound(level, caster.position(), SoundEvents.SLIME_SQUISH, 1.0F, 0.5F);
        HollowFx.sound(level, caster.position(), SoundEvents.ZOMBIE_VILLAGER_CURE, 0.4F, 1.6F);
        if (!ctx.spell().effects().isEmpty()) ctx.resolveOnSelf();
        ctx.end(caster.position().add(0, 1, 0), ctx.look());
        return true;
    }

    // =====================================================================================
    //  Hierro
    // =====================================================================================
    /** Hierro concentrado: −30% de dano por 10 s; do Vasto Lorde em diante −50%, sem ser empurrado, por 8 s. */
    public static void hierro(SpellContext ctx) {
        if (!(ctx.caster() instanceof ServerPlayer sp)) return;
        boolean strong = Hollow.rank(MagicApi.get(sp)) >= Hollow.VASTO_LORDE;
        int ticks = ctx.formStats().duration(strong ? 160 : 200);
        HollowState.hierro(sp, ticks, strong);
        if (strong) sp.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 0, false, false, true));
        HollowNetwork.fx(sp, HollowNetwork.HIERRO, sp.position(), Vec3.ZERO, strong ? 1.5F : 1F, Hollow.BONE_RGB);
        MahouAnims.play(sp, MahouAnims.PALM_WARD);
        HollowFx.sound(sp.serverLevel(), sp.position(), SoundEvents.ANVIL_PLACE, 0.6F, 0.6F);
        HollowFx.sound(sp.serverLevel(), sp.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, 0.6F);
        sp.displayClientMessage(Hollow.msg(strong ? "hierro_strong" : "hierro").withColor(Hollow.BONE_RGB), true);
        if (!ctx.spell().effects().isEmpty()) ctx.resolveOnSelf();
        ctx.end(sp.position().add(0, 1, 0), ctx.look());
    }

    // =====================================================================================
    //  Sonído
    // =====================================================================================
    /**
     * Sonído: some com um estalo e reaparece até 10 blocos à frente (+2 por rank), com imagem residual. Mirando um
     * inimigo, reaparece ÀS COSTAS dele, virado para ele.
     */
    public static void sonido(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || caster.isPassenger()) return;
        ServerLevel level = ctx.level();
        SpellStats s = ctx.formStats();
        double max = 10 + 2 * rankOf(caster) + 3 * Math.max(0, s.aoe);
        Vec3 look = caster.getLookAngle();
        Vec3 start = caster.position();
        LivingEntity target = aimed(ctx, caster, caster.getEyePosition(), look, max);
        Vec3 end;
        if (target != null) {
            Vec3 flat = new Vec3(target.getX() - start.x, 0, target.getZ() - start.z);
            flat = flat.lengthSqr() < 1.0E-4 ? new Vec3(look.x, 0, look.z).normalize() : flat.normalize();
            Vec3 behind = target.position().add(flat.scale(target.getBbWidth() / 2 + 1.1));
            end = free(level, caster, behind) ? behind : dash(level, caster, start, flat, max);
        } else {
            Vec3 dir = new Vec3(look.x, Math.max(-0.3, Math.min(0.3, look.y)), look.z);
            end = dash(level, caster, start, dir.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : dir.normalize(), max);
        }
        HollowNetwork.fxNear(level, HollowNetwork.SONIDO, caster.getId(), start, end, caster.getBbHeight(), Hollow.DARK_RGB, 64);
        MahouAnims.play(caster, MahouAnims.SONIDO_STEP);
        HollowFx.sound(level, start, HollowSounds.SONIDO.get(), 0.9F, 1.0F);
        HollowFx.sound(level, start, SoundEvents.ENDER_DRAGON_FLAP, 0.35F, 2.0F);
        float yaw = caster.getYRot(), pitch = caster.getXRot();
        if (target != null) {
            Vec3 face = target.position().subtract(end);
            yaw = (float) (Math.atan2(-face.x, face.z) * (180 / Math.PI));
        }
        if (caster instanceof ServerPlayer sp) sp.teleportTo(level, end.x, end.y, end.z, yaw, pitch);
        else caster.moveTo(end.x, end.y, end.z, yaw, pitch);
        caster.fallDistance = 0;
        caster.setDeltaMovement(Vec3.ZERO);
        caster.hurtMarked = true;
        HollowFx.sound(level, end, SoundEvents.PLAYER_ATTACK_NODAMAGE, 0.8F, 1.6F);
        if (target != null) {
            ctx.impact(target.position().add(0, target.getBbHeight() / 2, 0), look, target);
            if (!ctx.spell().effects().isEmpty()) ctx.resolveOnEntity(target, caster);
        }
        ctx.end(end.add(0, caster.getBbHeight() * 0.6, 0), look);
    }

    @Nullable
    static LivingEntity aimed(SpellContext ctx, LivingEntity caster, Vec3 eye, Vec3 look, double max) {
        Vec3 to = eye.add(look.scale(max + 2));
        AABB box = caster.getBoundingBox().expandTowards(look.scale(max + 2)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(caster.level(), caster, eye, to, box,
                e -> e instanceof LivingEntity le && le.isAlive() && e != caster && !ctx.isAlly(e) && !e.isSpectator(), 0.3F);
        if (hit == null) return null;
        Entity e = hit.getEntity();
        HitResult clip = caster.level().clip(new ClipContext(eye, e.getEyePosition(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return clip.getType() == HitResult.Type.BLOCK ? null : (LivingEntity) e;
    }

    public static boolean free(ServerLevel level, LivingEntity caster, Vec3 at) {
        return level.noCollision(caster, caster.getBoundingBox().move(at.subtract(caster.position())));
    }

    public static Vec3 dash(ServerLevel level, LivingEntity caster, Vec3 start, Vec3 dir, double max) {
        double dist = 0;
        for (double d = 0.5; d <= max; d += 0.5) {
            if (!level.noCollision(caster, caster.getBoundingBox().move(dir.scale(d)))) break;
            dist = d;
        }
        return start.add(dir.scale(dist));
    }

    // =====================================================================================
    //  Garganta
    // =====================================================================================
    /** Garganta: rasga o céu — leva ao Mundo Oco (ou de volta). false = não abriu (recarga dos atalhos, lugar ruim). */
    public static boolean garganta(SpellContext ctx) {
        if (!(ctx.caster() instanceof ServerPlayer sp)) return false;
        Vec3 at = HmTravel.spotAhead(sp);
        boolean ok = HmTravel.shortcut(sp, at, true);
        if (ok) {
            ServerLevel sl = sp.serverLevel();
            HollowNetwork.fxNear(sl, HollowNetwork.GARGANTA, sp.getId(), at.add(0, 1.6, 0), sp.getLookAngle(), 3.5F, Hollow.DARK_RGB, 96);
            MahouAnims.play(sp, MahouAnims.THROW_OVERHAND);
            HollowFx.sound(sl, at, HollowSounds.GARGANTA.get(), 1.4F, 0.9F);
            HollowFx.sound(sl, at, SoundEvents.WARDEN_EMERGE, 0.8F, 1.2F);
            sl.sendParticles(HollowFx.BLACK, at.x, at.y + 1.6, at.z, 30, 0.6, 1.2, 0.2, 0.02);
        }
        ctx.end(at.add(0, 1, 0), ctx.look());
        return ok;
    }

    // =====================================================================================
    //  Descorrer
    // =====================================================================================
    /** Descorrer: abre um rasgo e atravessa até onde olha (40 blocos, +8 por Expandir). false = sem lugar. */
    public static boolean descorrer(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null || caster.isPassenger()) return false;
        ServerLevel level = ctx.level();
        double max = 40 + 8 * Math.max(0, ctx.formStats().aoe);
        Vec3 eye = caster.getEyePosition();
        Vec3 look = ctx.look();
        BlockHitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(max)), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 aim = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation().subtract(look.scale(0.8)) : hit.getLocation();
        Vec3 end = landing(level, caster, aim);
        if (end == null) {
            if (caster instanceof ServerPlayer sp) sp.displayClientMessage(Hollow.msg("descorrer_blocked").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        Vec3 start = caster.position();
        HollowNetwork.fxNear(level, HollowNetwork.RIFT, caster.getId(), start.add(0, 1, 0), end.add(0, 1, 0), 2.6F, Hollow.DARK_RGB, 96);
        HollowFx.sound(level, start, HollowSounds.GARGANTA.get(), 1.0F, 1.3F);
        if (caster instanceof ServerPlayer sp) sp.teleportTo(level, end.x, end.y, end.z, caster.getYRot(), caster.getXRot());
        else caster.moveTo(end.x, end.y, end.z, caster.getYRot(), caster.getXRot());
        caster.fallDistance = 0;
        caster.setDeltaMovement(Vec3.ZERO);
        caster.hurtMarked = true;
        HollowFx.sound(level, end, SoundEvents.ENDERMAN_TELEPORT, 0.8F, 0.5F);
        level.sendParticles(HollowFx.BLACK, end.x, end.y + 1, end.z, 20, 0.5, 0.8, 0.5, 0.05);
        if (!ctx.spell().effects().isEmpty()) ctx.resolveArea(end, 3F, caster, false);
        ctx.end(end.add(0, 1, 0), look);
        return true;
    }

    @Nullable
    private static Vec3 landing(ServerLevel level, LivingEntity caster, Vec3 aim) {
        BlockPos base = BlockPos.containing(aim);
        for (int dy = 0; dy >= -6; dy--) {
            Vec3 at = Vec3.atBottomCenterOf(base.offset(0, dy, 0));
            if (free(level, caster, at) && !level.getBlockState(base.offset(0, dy - 1, 0)).getCollisionShape(level, base.offset(0, dy - 1, 0)).isEmpty()) {
                return at;
            }
        }
        Vec3 at = Vec3.atBottomCenterOf(base);
        return free(level, caster, at) ? at : null;
    }

    // =====================================================================================
    //  Resurrección (o estouro, depois da invocação)
    // =====================================================================================
    /**
     * O ESTOURO da Resurrección: o casulo se rompe, a forma liberada vem (ver {@link HollowState#release}), a pressão
     * empurra e machuca tudo em 8 blocos, o chão treme. false = sem arquétipo escolhido.
     */
    public static boolean resurreccionBurst(SpellContext ctx, int ticks) {
        if (!(ctx.caster() instanceof ServerPlayer sp)) return false;
        MagicData d = MagicApi.get(sp);
        Hollow.Release r = Hollow.release(d);
        if (r == null || !Hollow.isHollow(sp)) {
            sp.displayClientMessage(Hollow.msg("release_none").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        HollowState.release(sp, r, ticks);
        ServerLevel sl = sp.serverLevel();
        Vec3 at = sp.position();
        MahouAnims.play(sp, MahouAnims.TRANSFORM_ROAR);
        HollowNetwork.fxNear(sl, HollowNetwork.RESURRECCION, sp.getId(), at, Vec3.ZERO, 14F, r.rgb, 96);
        HollowNetwork.fxNear(sl, HollowNetwork.SHAKE, sp.getId(), at, Vec3.ZERO, 1.5F, r.rgb, 48);
        HollowNetwork.fxNear(sl, HollowNetwork.SHOCKWAVE, sp.getId(), at, Vec3.ZERO, 9F, r.rgb, 96);
        sl.sendParticles(HollowFx.dust(r.rgb, 1.8F), at.x, at.y + 1, at.z, 50, 0.9, 1.4, 0.9, 0.1);
        sl.sendParticles(HollowFx.BONE, at.x, at.y + 1.7, at.z, 30, 0.4, 0.3, 0.4, 0.15);
        sl.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        HollowFx.sound(sl, at, HollowSounds.ROAR.get(), 2.0F, 0.9F);
        HollowFx.sound(sl, at, SoundEvents.WITHER_SPAWN, 0.7F, 0.8F);
        HollowFx.sound(sl, at, SoundEvents.GLASS_BREAK, 1.5F, 0.5F);
        HollowFx.sound(sl, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.6F);
        List<LivingEntity> near = new ArrayList<>(ctx.enemiesAround(at, 8.0));
        for (LivingEntity e : near) {
            Vec3 push = e.position().subtract(at);
            push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0.6) : push.normalize();
            ctx.damage(e, 6F, ctx.formStats(), sp);
            e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.6, 0.6, push.z * 1.6));
            e.hurtMarked = true;
            if (!ctx.spell().effects().isEmpty()) ctx.resolveOnEntity(e, sp);
        }
        sp.displayClientMessage(Hollow.msg("release_start", r.displayName(), ticks / 20), true);
        return true;
    }

    private HollowMoves() {}
}
