package com.renanpontes.isekaimahou.feature.ultimates;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Impacto de um meteoro: clarão, cogumelo de fumaça, lava espirrando, onda de choque que arremessa, tremor, os
 * efeitos da magia em área e uma cratera de verdade ({@link Destruction#crater}, que respeita a regra
 * {@code mahouSpellDestruction}, as cidades e a dimensão das Provações).
 */
public final class MeteorImpact {
    /** Cor do fogo do meteoro (misturada com a cor da magia). */
    public static final int FIRE = 0xFF7A2A;

    /**
     * @param size         tamanho da rocha (1 = um bloco); escala visual e empurrão
     * @param radius       raio dos efeitos da magia
     * @param crater       raio da cratera
     * @param impactDamage dano base do choque (multiplicado pelo poder) em quem está no miolo
     * @param guardCharge  ultimate: o dano não recarrega a Carga de Ultimate
     * @param direct       a rocha (fonte direta do dano) ou null
     */
    public static void explode(SpellContext ctx, ServerLevel level, Vec3 at, Vec3 dir, float size, float radius, float crater,
                               float impactDamage, boolean guardCharge, @Nullable Entity direct, float volume) {
        Vec3 core = at.add(0, 0.6, 0);
        LivingEntity caster = ctx.caster();
        int rgb = UltFx.rgb(ctx);
        Element element = ctx.element();

        // ---------------- Visual e som ----------------
        visuals(level, at, size, radius, element, rgb, Mth.clamp(volume, 0.4F, 1F));
        UltFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), (3.0F + size) * volume, 0.55F);
        UltFx.sound(level, at, SoundEvents.DRAGON_FIREBALL_EXPLODE, 2.5F * volume, 0.6F);
        UltFx.sound(level, at, SoundEvents.LIGHTNING_BOLT_IMPACT, 2.0F * volume, 0.5F);
        UltFx.sound(level, at, ElementFx.castSoundOf(element), 1.5F * volume, 0.6F);
        BossKit.shake(level, at, 20 + radius * 5, Math.min(2.4F, (0.5F + 0.45F * size) * Math.max(0.5F, volume)), 16);

        // ---------------- Magia programável: cada vítima é um impacto (sem ninguém, o chão) ----------------
        List<LivingEntity> victims = new ArrayList<>(ctx.enemiesAround(core, radius));
        victims.removeIf(e -> e instanceof ArmorStand || e.isSpectator());
        victims.sort(Comparator.comparingDouble(e -> e.distanceToSqr(core)));
        if (victims.isEmpty()) ctx.impact(core, dir, null);
        for (LivingEntity v : victims) ctx.impact(v.getBoundingBox().getCenter(), dir, v);

        // ---------------- Efeitos + choque ----------------
        float[] snap = guardCharge ? UltFx.chargeSnapshot(caster) : null;
        ctx.resolveArea(core, radius, direct, true);
        if (impactDamage > 0) {
            for (LivingEntity e : ctx.enemiesAround(core, radius * 0.6F)) {
                if (e instanceof ArmorStand || e.isSpectator()) continue;
                e.invulnerableTime = 0;
                ctx.damage(e, impactDamage, ctx.formStats(), direct);
            }
        }
        UltFx.chargeRestore(caster, snap);

        // Onda de choque: arremessa para fora quem não é aliado
        double reach = radius * 1.7;
        double push = 0.9 + 0.35 * Math.sqrt(size);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(core, core).inflate(reach),
                e -> e.isAlive() && !e.isSpectator() && e != caster && !(e instanceof ArmorStand))) {
            if (ctx.isAlly(e)) continue;
            double dx = e.getX() - at.x, dz = e.getZ() - at.z;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > reach) continue;
            double s = push * (1.0 - d / reach) * (BossKit.isBossLike(e) ? 0.25 : 1.0);
            Vec3 out = d < 0.01 ? new Vec3(1, 0, 0) : new Vec3(dx / d, 0, dz / d);
            e.push(out.x * s, 0.25 + 0.3 * s, out.z * s);
            e.hurtMarked = true;
        }

        // ---------------- Terreno ----------------
        if (crater > 0.5F) Destruction.crater(level, at, Mth.clamp(crater, 1.0F, 24.0F), caster);
    }

    /**
     * A rocha se espatifa na parede de um Domínio inimigo: clarão, brasas, estrondo e tremor, mas a barreira segura
     * o golpe — nada de efeitos, choque ou cratera (quem está do outro lado fica protegido; a barreira já perdeu
     * integridade em {@code Domains.hitBarrier}).
     */
    public static void shatterOnBarrier(ServerLevel level, Vec3 at, float size, int rgb, float volume) {
        float v = Mth.clamp(volume, 0.4F, 1F);
        UltNet.fx(level, UltNet.FX_FLASH, at, 0F, 2.0F + size * 1.2F, UltFx.mix(FIRE, rgb, 0.35F), 8);
        UltFx.far(level, ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0, 0, 0, 0);
        UltFx.far(level, ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.2, 0.2, 0.2, 0);
        UltFx.far(level, ParticleTypes.LAVA, at.x, at.y, at.z, (int) ((10 + 8 * size) * v), 0.6 * size, 0.6 * size, 0.6 * size, 0);
        UltFx.far(level, Vfx.flame(), at.x, at.y, at.z, (int) ((30 + 15 * size) * v), 0.8 * size, 0.8 * size, 0.8 * size, 0.15);
        UltFx.far(level, Vfx.largeSmoke(), at.x, at.y, at.z, (int) ((20 + 10 * size) * v), 1.0 * size, 1.0 * size, 1.0 * size, 0.05);
        UltFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), (2.5F + size) * volume, 0.6F);
        UltFx.sound(level, at, SoundEvents.GLASS_BREAK, 2.5F * volume, 0.5F);
        BossKit.shake(level, at, 16 + size * 6, Math.min(1.6F, (0.4F + 0.35F * size) * v), 12);
    }

    /** Só o espetáculo. {@code k} escala a quantidade de partículas (a Chuva usa menos por rocha). */
    public static void visuals(ServerLevel level, Vec3 at, float size, float radius, Element element, int rgb, float k) {
        Vec3 core = at.add(0, 0.6, 0);
        int fire = UltFx.mix(FIRE, rgb, 0.35F);
        UltNet.fx(level, UltNet.FX_FLASH, core, 0F, 1.5F + radius * 0.55F, fire, 9);
        UltNet.fx(level, UltNet.FX_RING, at.add(0, 0.2, 0), 0F, radius * 1.9F, fire, 16);

        BlockState ground = level.getBlockState(BlockPos.containing(at).below());
        if (ground.isAir()) ground = Blocks.STONE.defaultBlockState();
        UltFx.far(level, ParticleTypes.FLASH, core.x, core.y, core.z, 1, 0, 0, 0, 0);
        UltFx.far(level, ParticleTypes.EXPLOSION_EMITTER, core.x, core.y, core.z, 1 + (int) (size / 1.5F), radius * 0.2, 0.3,
                radius * 0.2, 0);
        UltFx.far(level, ParticleTypes.LAVA, core.x, core.y, core.z, (int) ((12 + 10 * size) * k), radius * 0.3, 0.4, radius * 0.3, 0);
        UltFx.far(level, Vfx.flame(), core.x, core.y + 0.4, core.z, (int) ((14 + 10 * size) * k), radius * 0.45, 0.6,
                radius * 0.45, 0.12);
        UltFx.far(level, new BlockParticleOption(ParticleTypes.BLOCK, ground), core.x, core.y, core.z, (int) ((40 + 30 * size) * k),
                radius * 0.4, 0.5, radius * 0.4, 0.4);
        // v1.3: a parte "do elemento" é montada no cliente pelo preset gráfico — nova inteira numa rocha só (Meteoro),
        // impacto forte em cada rocha da Chuva (k < 1); o tremor já vem de quem chamou
        if (k >= 0.9F) ElementFx.nova(level, at, element, rgb, Math.min(32F, radius * 1.2F), 5, false);
        else com.renanpontes.isekaimahou.glyph.fx.FxBurst.send(level, com.renanpontes.isekaimahou.glyph.fx.FxBurst.IMPACT, element, rgb,
                core, new Vec3(0, 1, 0), Math.min(4F, 0.8F + size * 0.5F), 4, -1, 0);
        UltFx.far(level, ElementFx.signature(element), core.x, core.y + 1, core.z, (int) ((8 + 4 * size) * k), radius * 0.35, 0.8,
                radius * 0.35, 0.1);
        // Cogumelo: coluna que sobe (fumaça de fogueira dura bastante) + chapéu de fumaça grossa
        for (int i = 0; i < 4 + (int) (size * 2); i++) {
            UltFx.far(level, ParticleTypes.CAMPFIRE_SIGNAL_SMOKE, core.x, core.y + i * 0.8, core.z, 2, 0.6 + size * 0.2, 0.3,
                    0.6 + size * 0.2, 0.02);
        }
        UltFx.far(level, Vfx.largeSmoke(), core.x, core.y + 4 + size * 2, core.z, (int) ((30 + 15 * size) * k),
                1.5 + size, 0.6, 1.5 + size, 0.03);
    }

    private MeteorImpact() {}
}
