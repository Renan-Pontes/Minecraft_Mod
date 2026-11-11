package com.renanpontes.isekaimahou.feature.light_shadow.effect;

import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;
import com.renanpontes.isekaimahou.feature.light_shadow.LsUtil;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Acorrentado (Correntes): o alvo fica preso a uma âncora no chão por 4 correntes sombrias. Se for empurrado
 * ou tentar voar, as correntes o puxam de volta. Funciona junto com o Enraizado do núcleo.
 */
public class ShackledEffect extends MobEffect {
    public ShackledEffect() {
        super(MobEffectCategory.HARMFUL, 0x55556A);
    }

    public static void setAnchor(LivingEntity e, Vec3 pos) {
        CompoundTag d = LsUtil.data(e);
        d.putDouble("chain_x", pos.x);
        d.putDouble("chain_y", pos.y);
        d.putDouble("chain_z", pos.z);
    }

    public static void clearAnchor(LivingEntity e) {
        if (!LsUtil.hasData(e)) return;
        CompoundTag d = LsUtil.data(e);
        d.remove("chain_x");
        d.remove("chain_y");
        d.remove("chain_z");
    }

    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel sl)) return true;
        CompoundTag d = LsUtil.data(e);
        if (!d.contains("chain_x")) setAnchor(e, e.position());
        Vec3 anchor = new Vec3(d.getDouble("chain_x"), d.getDouble("chain_y"), d.getDouble("chain_z"));
        Vec3 off = e.position().subtract(anchor);
        double dist = off.length();
        if (dist > 3.0) {
            e.teleportTo(anchor.x, anchor.y, anchor.z);
        } else if (dist > 0.35) {
            e.setDeltaMovement(off.scale(-0.4));
            e.hurtMarked = true;
        }
        if (e.tickCount % 3 == 0) drawChains(sl, e, anchor);
        return true;
    }

    /** 4 correntes saindo do chão em volta da âncora até o corpo do alvo. */
    private static void drawChains(ServerLevel sl, LivingEntity e, Vec3 anchor) {
        Vec3 body = LsFx.mid(e);
        double r = Math.max(1.4, e.getBbWidth() + 1.0);
        for (int k = 0; k < 4; k++) {
            double a = Math.PI / 4 + k * Math.PI / 2;
            Vec3 ground = anchor.add(Math.cos(a) * r, 0.05, Math.sin(a) * r);
            Vec3 dir = body.subtract(ground);
            int links = Math.max(3, (int) (dir.length() * 4));
            for (int i = 0; i <= links; i++) {
                Vec3 p = ground.add(dir.scale(i / (double) links));
                // Elos alternando cinza escuro e violeta, para parecer corrente
                sl.sendParticles(i % 2 == 0 ? LsFx.dust(LsFx.CHAIN_GREY, 0.9F) : LsFx.dust(LsFx.VIOLET, 0.6F),
                        p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
            if (sl.random.nextInt(4) == 0) {
                sl.sendParticles(ParticleTypes.SMOKE, ground.x, ground.y + 0.1, ground.z, 1, 0.05, 0, 0.05, 0.01);
            }
        }
    }
}
