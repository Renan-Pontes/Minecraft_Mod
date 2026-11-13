package com.renanpontes.isekaimahou.feature.light_shadow.entity;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.feature.light_shadow.LsFx;
import com.renanpontes.isekaimahou.feature.light_shadow.LsUtil;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Barreira Sagrada: domo de luz onde mortos-vivos, demônios e sombras inimigos não entram. Quem estiver dentro
 * é empurrado para fora e queima; projéteis disparados por eles se desfazem ao tocar a barreira.
 */
public class SacredBarrierZoneEntity extends HolyZoneEntity {

    public SacredBarrierZoneEntity(EntityType<? extends SacredBarrierZoneEntity> type, Level level) {
        super(type, level);
    }

    @Override
    protected void onStart(ServerLevel level) {
        LsFx.sound(level, position(), SoundEvents.BEACON_ACTIVATE, 1.2F, 1.3F);
        LsFx.holyBurst(level, position().add(0, 1, 0), 30, radius() * 0.4);
    }

    @Override
    protected void serverTick(ServerLevel level) {
        Vec3 c = position();
        float r = radius();
        for (LivingEntity e : inside(LivingEntity.class, 0.6)) {
            if (!LsUtil.isUnholy(e) || LsUtil.isAlly(level, owner, e)) continue;
            Vec3 out = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(c);
            if (out.lengthSqr() < 1.0E-3) out = new Vec3(1, 0, 0);
            Vec3 dir = new Vec3(out.x, Math.max(0, out.y) * 0.3, out.z).normalize();
            double depth = 1.0 - Math.min(1.0, out.length() / (r + 0.6));
            double push = 0.35 + 0.6 * depth;
            e.setDeltaMovement(dir.x * push, 0.15 + 0.1 * depth, dir.z * push);
            e.hurtMarked = true;
            if (age % 10 == 0) {
                LsUtil.spellHurt(level, e, 2.0F * power, this, LsUtil.owner(level, owner), Element.LIGHT);
                e.igniteForSeconds(2);
                level.sendParticles(Vfx.endRod(), e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 6, 0.3, 0.4, 0.3, 0.05);
                LsFx.sound(level, e.position(), SoundEvents.AMETHYST_BLOCK_HIT, 0.8F, 0.6F);
            }
        }
        // Projéteis de profanos se desfazem na barreira
        for (Projectile p : inside(Projectile.class, 0.5)) {
            Entity shooter = p.getOwner();
            if (shooter != null && LsUtil.isUnholy(shooter) && !LsUtil.isAlly(level, owner, shooter)) {
                level.sendParticles(ParticleTypes.FLASH, p.getX(), p.getY(), p.getZ(), 1, 0, 0, 0, 0);
                LsFx.holyBurst(level, p.position(), 6, 0.15);
                p.discard();
            }
        }
        if (age % 80 == 0) LsFx.sound(level, c, SoundEvents.BEACON_AMBIENT, 0.8F, 1.4F);
    }

    @Override
    protected void onEnd(ServerLevel level) {
        LsFx.sound(level, position(), SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.3F);
        LsFx.holyBurst(level, position().add(0, 1, 0), 20, radius() * 0.4);
    }

    @Override
    protected void clientTick() {
        Level lv = level();
        float r = radius();
        boolean reduced = !com.renanpontes.isekaimahou.client.fx.FxQuality.atLeast(MahouConfig.Graphics.HIGH);
        float fadeOut = 1F - Math.max(0F, (progress() - 0.85F) / 0.15F);
        // Pontos aleatórios na casca do domo
        int n = (int) ((reduced ? 3 : 9) * fadeOut) + 1;
        for (int i = 0; i < n; i++) {
            double th = random.nextDouble() * Math.PI * 2;
            double ph = Math.acos(random.nextDouble());
            double x = getX() + r * Math.sin(ph) * Math.cos(th);
            double y = getY() + r * Math.cos(ph);
            double z = getZ() + r * Math.sin(ph) * Math.sin(th);
            lv.addParticle(LsFx.dust(random.nextInt(3) == 0 ? LsFx.WHITE_GOLD : LsFx.GOLD, 1.1F), x, y, z, 0, 0, 0);
        }
        // Anel no chão girando e faíscas subindo pela borda
        if (tickCount % 2 == 0) {
            int pts = reduced ? 12 : 28;
            double rot = tickCount * 0.04;
            for (int i = 0; i < pts; i++) {
                double a = rot + Math.PI * 2 * i / pts;
                lv.addParticle(LsFx.dust(LsFx.GOLD, 0.9F), getX() + Math.cos(a) * r, getY() + 0.08, getZ() + Math.sin(a) * r, 0, 0, 0);
            }
        }
        if (!reduced && tickCount % 5 == 0) {
            double a = random.nextDouble() * Math.PI * 2;
            lv.addParticle(Vfx.endRod(), getX() + Math.cos(a) * r, getY() + 0.1, getZ() + Math.sin(a) * r, 0, 0.08, 0);
        }
    }
}
