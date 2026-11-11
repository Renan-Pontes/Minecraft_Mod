package com.renanpontes.isekaimahou.feature.domain.glyph;

import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Cataclismo (ultimate): mira um ponto a até 48 blocos, prepara por 1,5 s (pilar de energia, anéis e tremor)
 * e então aplica os efeitos da magia, com o dobro do poder, numa área ENORME (raio 14, +50% por Expandir) com ondas de choque
 * e uma CRATERA no ponto do impacto (v0.8, pela API Destruction: respeita a regra de destruição).
 * Exemplo: Cataclismo → Incendiar → Explosão → Amplificar → Expandir.
 */
public class CataclysmForm extends FormGlyph {
    public static final double RANGE = 48.0;
    /** Multiplicador de poder dos efeitos no impacto: um golpe só, com aviso de 1,5 s, precisa valer a ultimate. */
    public static final float POWER = 2.0F;
    /** v0.8: área ×1,75 (raio base 8 → 14). */
    public static final float AREA = 1.75F;

    public CataclysmForm() {
        super("cataclysm", 80);
        tier(4);
        minCircle(4);
    }

    @Override
    public boolean isUltimate() { return true; }

    @Override
    public int baseCastTicks() { return 10; }

    @Override
    public int baseCooldown() { return 200; }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        Vec3 target = pickTarget(ctx, caster);
        float radius = ctx.formStats().radius(8.0F) * AREA;
        new CataclysmSequence(ctx, target, radius).start();
    }

    /**
     * Alvo: o inimigo/bloco na mira, sempre "assentado" no chão. Sai de {@code ctx.eyePos()/look()} (chefes e
     * pedestais podem conjurar de outro ponto); sem origem própria, mobs miram no alvo deles.
     */
    private static Vec3 pickTarget(SpellContext ctx, LivingEntity caster) {
        ServerLevel level = ctx.level();
        Vec3 aim;
        if (!ctx.hasOrigin() && caster instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            aim = mob.getTarget().position();
        } else {
            Vec3 eye = ctx.eyePos();
            Vec3 end = eye.add(ctx.look().scale(RANGE));
            BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            Vec3 limit = block.getType() == HitResult.Type.MISS ? end : block.getLocation();
            EntityHitResult ent = ProjectileUtil.getEntityHitResult(level, caster, eye, limit, new AABB(eye, limit).inflate(1.5),
                    e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != caster && !ctx.isAlly(e));
            aim = ent != null ? ent.getEntity().position() : limit;
        }
        return groundBelow(level, aim);
    }

    /** Desce até achar chão (até 32 blocos); se o ponto estiver dentro de um bloco, sobe para fora dele. */
    static Vec3 groundBelow(ServerLevel level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        for (int i = 0; i < 6 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) pos = pos.above();
        for (int i = 0; i < 32; i++) {
            BlockPos below = pos.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return new Vec3(p.x, pos.getY(), p.z);
            }
            pos = below;
        }
        return p;
    }
}
