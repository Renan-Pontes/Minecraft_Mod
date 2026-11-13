package com.renanpontes.isekaimahou.feature.class_ranger.glyph;

import com.renanpontes.isekaimahou.feature.class_ranger.RangerFxNet;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.weapon.WeaponFx;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;

/**
 * Flechas espectrais que caem do céu (Chuva de Flechas e Tempestade das Mil Penas). São flechas vanilla conjuradas
 * (não recolhíveis, sem o dano do vanilla): o acerto é cancelado e quem decide o efeito é a {@link Volley} — cada
 * inimigo leva no máximo {@link Volley#maxHits} acertos por saraivada, aliados são atravessados e as flechas se desfazem
 * no chão. Só ficam em memória (teto {@link #MAX_ACTIVE}); um save no meio da chuva as deixa como flechas comuns
 * sem dono que somem sozinhas.
 */
public final class RainArrows {
    /** Tag das flechas de chuva (o bônus de "flechas mais rápidas" do Ranger não mexe nelas). */
    public static final String TAG = "isekai_mahou.ranger_rain";
    private static final int LIFETIME = 80;
    private static final int MAX_ACTIVE = 400;

    /** Uma saraivada: contexto, teto de acertos por alvo e o que fazer ao acertar entidade/bloco. */
    public static final class Volley {
        final SpellContext ctx;
        final int maxHits;
        final int rgb;
        final BiConsumer<Entity, AbstractArrow> onEntity;
        @Nullable final BiConsumer<BlockHitResult, AbstractArrow> onBlock;
        int blockBudget;
        final Map<UUID, Integer> hits = new HashMap<>();

        public Volley(SpellContext ctx, int maxHits, int blockBudget, BiConsumer<Entity, AbstractArrow> onEntity,
                      @Nullable BiConsumer<BlockHitResult, AbstractArrow> onBlock) {
            this.ctx = ctx;
            this.maxHits = maxHits;
            this.blockBudget = blockBudget;
            this.rgb = ctx.color() & 0xFFFFFF;
            this.onEntity = onEntity;
            this.onBlock = onBlock;
        }
    }

    private record Tracked(AbstractArrow arrow, Volley volley) {}

    private static final Map<UUID, Tracked> ACTIVE = new HashMap<>();

    /** Solta uma flecha em {@code from} com velocidade {@code vel}. */
    public static void spawn(Volley v, Vec3 from, Vec3 vel) {
        if (ACTIVE.size() >= MAX_ACTIVE) return;
        ServerLevel level = v.ctx.level();
        LivingEntity caster = v.ctx.caster();
        Arrow arrow = caster != null ? new Arrow(level, caster, new ItemStack(Items.ARROW), null)
                : new Arrow(level, from.x, from.y, from.z, new ItemStack(Items.ARROW), null);
        arrow.setPos(from.x, from.y, from.z);
        arrow.setDeltaMovement(vel);
        arrow.setYRot((float) (Math.atan2(vel.x, vel.z) * (180F / Math.PI)));
        arrow.setXRot((float) (Math.atan2(vel.y, vel.horizontalDistance()) * (180F / Math.PI)));
        arrow.yRotO = arrow.getYRot();
        arrow.xRotO = arrow.getXRot();
        arrow.setBaseDamage(0.0);
        arrow.setCritArrow(true);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.addTag(TAG);
        ACTIVE.put(arrow.getUUID(), new Tracked(arrow, v));
        if (!level.addFreshEntity(arrow)) ACTIVE.remove(arrow.getUUID());
    }

    /** Ponto aleatório (uniforme) no disco de raio {@code r} em volta de {@code c}. */
    public static Vec3 randomIn(ServerLevel level, Vec3 c, double r) {
        double a = level.random.nextDouble() * Math.PI * 2;
        double d = Math.sqrt(level.random.nextDouble()) * r;
        return new Vec3(c.x + Math.cos(a) * d, c.y, c.z + Math.sin(a) * d);
    }

    public static void onImpact(ProjectileImpactEvent event) {
        if (!(event.getProjectile() instanceof AbstractArrow arrow) || !(arrow.level() instanceof ServerLevel level)) return;
        Tracked t = ACTIVE.get(arrow.getUUID());
        if (t == null) return;
        Volley v = t.volley();
        HitResult hit = event.getRayTraceResult();
        if (hit instanceof EntityHitResult ehr) {
            Entity target = ehr.getEntity();
            // o acerto vanilla nunca acontece: atravessa aliados e aplica o efeito da saraivada nos inimigos
            event.setCanceled(true);
            if (v.ctx.isAlly(target)) return;
            ACTIVE.remove(arrow.getUUID());
            int n = v.hits.merge(target.getUUID(), 1, Integer::sum);
            if (n <= v.maxHits) {
                SpellScheduler.schedule(1, () -> {
                    if (!target.isAlive() || target.level() != level) return;
                    target.invulnerableTime = 0;
                    v.onEntity.accept(target, arrow);
                });
            }
            arrow.discard();
        } else if (hit instanceof BlockHitResult bhr && bhr.getType() == HitResult.Type.BLOCK) {
            ACTIVE.remove(arrow.getUUID());
            if (v.onBlock != null && v.blockBudget > 0) {
                v.blockBudget--;
                v.onBlock.accept(bhr, arrow);
            }
            Vec3 at = bhr.getLocation();
            RangerFxNet.at(level, RangerFxNet.RAIN_LAND, at, Vec3.ZERO, 1F, v.rgb, -1);
            // a flecha conjurada se desfaz (não enche o chão de flechas)
            SpellScheduler.schedule(2, arrow::discard);
        }
    }

    public static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        Iterator<Tracked> it = ACTIVE.values().iterator();
        while (it.hasNext()) {
            Tracked t = it.next();
            AbstractArrow a = t.arrow();
            if (a.isRemoved() || !(a.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            if (a.tickCount > LIFETIME) {
                it.remove();
                a.discard();
                continue;
            }
            // o rastro é desenhado no cliente (RangerArrowFx manda o id da flecha ao nascer)
        }
    }

    public static void clear() {
        ACTIVE.clear();
    }

    private RainArrows() {}
}
