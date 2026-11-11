package com.renanpontes.isekaimahou.feature.bosses_d.entity.kit;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.AvatarMove;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialAvatar;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Vento: Lâminas em leque (3/5/7 linhas), Tornados que percorrem uma linha telegrafada, Rajada em linha que empurra
 * longe, Corrente Ascendente (lança para o alto), rastro de vento na investida e o golpe máximo Olho da Tempestade
 * (só perto do chefe é seguro).
 */
public final class WindKit extends AvatarKit {
    /** Tornado em movimento (perigo próprio, andado no tickPassive). */
    private static final class Tornado {
        Vec3 pos;
        final Vec3 step;
        int life;
        final Set<UUID> hit = new HashSet<>();

        Tornado(Vec3 pos, Vec3 step, int life) {
            this.pos = pos;
            this.step = step;
            this.life = life;
        }
    }

    private final List<Tornado> tornados = new ArrayList<>();
    private Vec3 galeDir = new Vec3(0, 0, 1);

    WindKit(TrialAvatar boss) {
        super(boss, Element.WIND);
    }

    @Override
    public void flavor(LivingEntity e) {
        // Vento não dá status: o castigo é ser jogado longe (os golpes empurram)
        e.hurtMarked = true;
    }

    @Override
    protected ParticleOptions fallParticle() {
        return ParticleTypes.CLOUD;
    }

    @Override
    public void tickPassive(ServerLevel sl) {
        Iterator<Tornado> it = tornados.iterator();
        while (it.hasNext()) {
            Tornado t = it.next();
            if (--t.life < 0 || !boss.isAlive()) {
                it.remove();
                continue;
            }
            t.pos = t.pos.add(t.step);
            for (double y = 0; y < 4.5; y += 0.6) {
                double a = boss.tickCount * 0.6 + y * 1.3;
                double r = 0.4 + y * 0.25;
                sl.sendParticles(ParticleTypes.CLOUD, t.pos.x + Math.cos(a) * r, t.pos.y + y, t.pos.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
            }
            sl.sendParticles(ElementFx.dust(fxEl(), 1.4F), t.pos.x, t.pos.y + 1.5, t.pos.z, 3, 0.6, 1.0, 0.6, 0);
            if (boss.tickCount % 6 == 0) sl.sendParticles(ParticleTypes.GUST, t.pos.x, t.pos.y + 0.5, t.pos.z, 1, 0.2, 0.1, 0.2, 0);
            if (boss.tickCount % 10 == 0) sound(sl, t.pos, SoundEvents.BREEZE_WHIRL, 0.8F, 1.2F);
            for (LivingEntity e : boss.foesNear(t.pos.add(0, 1, 0), 2.2)) {
                if (Math.hypot(e.getX() - t.pos.x, e.getZ() - t.pos.z) > 1.6 || e.getY() > t.pos.y + 4.5 || !t.hit.add(e.getUUID())) continue;
                boss.hitFoe(e, 0.8F);
                launch(e, 0.95);
                sound(sl, t.pos, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.0F, 1.0F);
            }
        }
    }

    @Override
    public void reset() {
        tornados.clear();
    }

    @Override
    protected List<AvatarMove> build() {
        List<AvatarMove> l = new ArrayList<>();
        // 1) Lâminas em leque
        l.add(AvatarMove.of("blade_fan", 34, 90, (sl, target, i) -> {
            if (i == 8) {
                int n = 3 + 2 * (phase() - 1);
                Vec3 d0 = dirTo(target);
                Set<UUID> once = new HashSet<>();
                int w = warm(12);
                for (int k = 0; k < n; k++) {
                    Vec3 d = rotateY(d0, (k - (n - 1) / 2.0) * 16);
                    Vec3 from = boss.position().add(d);
                    Vec3 to = from.add(d.scale(16));
                    delayed(sl, line(from, to, 1.3F, w).noRunes(), hits -> {
                        for (LivingEntity e : hits) {
                            if (!boss.isFoe(e) || !once.add(e.getUUID())) continue;
                            boss.hitFoe(e, 0.9F);
                            pushDir(e, d, 0.4, 0.15);
                        }
                        lineFx(sl, from, to, ParticleTypes.SWEEP_ATTACK, 1.6);
                        lineFx(sl, from, to, ElementFx.dust(fxEl(), 1.0F), 0.6);
                    });
                }
                sched(sl, w, () -> sound(sl, boss.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 2.0F, 0.6F));
            }
        }).anim("wave").range(0, 18).weight(12));

        // 2) Tornados em linha
        l.add(AvatarMove.of("tornado", 46, 200, (sl, target, i) -> {
            if (i == 10) {
                int n = 1 + phase();
                List<LivingEntity> foes = marks(4, 30);
                if (foes.isEmpty()) foes = List.of(target);
                int w = warm(20);
                for (int k = 0; k < n; k++) {
                    LivingEntity f = foes.get(k % foes.size());
                    Vec3 start = randomSpot(boss.position(), 2, 3.5);
                    Vec3 d = f.position().subtract(start).multiply(1, 0, 1);
                    if (d.lengthSqr() < 1.0E-3) d = dirTo(f);
                    d = rotateY(d.normalize(), (k - (n - 1) / 2.0) * 8);
                    Vec3 end = start.add(d.scale(22));
                    show(sl, line(start, end, 3.0F, w).hold(40).noFlash());
                    Vec3 step = d.scale(22.0 / 40);
                    sched(sl, w, () -> tornados.add(new Tornado(start, step, 40)));
                }
                announce("tornado");
                sound(sl, boss.position(), SoundEvents.BREEZE_SHOOT, 2.0F, 0.5F);
            }
        }).anim("vortex").range(0, 30).weight(9));

        // 3) Rajada em linha que empurra
        l.add(AvatarMove.of("gale", 40, 120, (sl, target, i) -> {
            if (i == 0) {
                galeDir = dirTo(target);
                boss.faceDir(galeDir);
                Vec3 from = boss.position();
                Vec3 to = from.add(galeDir.scale(20));
                Vec3 dir = galeDir;
                delayed(sl, line(from, to, 4.0F, warm(20)), hits -> {
                    hitAll(hits, 0.7F, e -> pushDir(e, dir, 1.9, 0.35));
                    lineFx(sl, from.add(0, 1, 0), to.add(0, 1, 0), ParticleTypes.GUST, 2.5);
                    lineFx(sl, from.add(0, 0.6, 0), to.add(0, 0.6, 0), ParticleTypes.CLOUD, 0.6);
                    sound(sl, from, SoundEvents.WIND_CHARGE_BURST.value(), 2.0F, 0.6F);
                });
            }
            boss.faceDir(galeDir);
        }).anim("breath").range(0, 20).weight(9).locked());

        // 4) Corrente Ascendente: lança quem estiver na marca
        l.add(AvatarMove.of("updraft", 44, 160, (sl, target, i) -> {
            if (i == 8) {
                for (LivingEntity f : marks(4, 28)) {
                    markBlast(sl, f, 2.0F, warm(30), (at, hits) -> {
                        hitAll(hits, 0.6F, e -> launch(e, 1.15));
                        column(sl, at, 6, ParticleTypes.CLOUD, 3);
                        sl.sendParticles(ParticleTypes.GUST_EMITTER_SMALL, at.x, at.y + 0.5, at.z, 1, 0, 0, 0, 0);
                        sound(sl, at, SoundEvents.BREEZE_WIND_CHARGE_BURST.value(), 1.4F, 0.8F);
                    });
                }
            }
        }).anim("meteor").range(0, 28).weight(8));
        return l;
    }

    /** Rastro: corredor de vento que empurra para o lado (sem dano) por 1 s. */
    @Override
    public void onDashTrail(ServerLevel sl, Vec3 from, Vec3 to) {
        if (from.distanceToSqr(to) < 4) return;
        show(sl, line(from, to, 2.4F, 3).hold(20).noFlash().noRunes().color(Telegraph.FROST));
        Vec3 d = to.subtract(from).multiply(1, 0, 1).normalize();
        Vec3 side = new Vec3(-d.z, 0, d.x);
        for (int t = 3; t <= 23; t += 5) {
            sched(sl, t, () -> {
                for (LivingEntity e : Telegraph.entitiesIn(sl, Telegraph.Shape.line(from, to, 2.4F))) {
                    if (!boss.isFoe(e)) continue;
                    Vec3 s = side.dot(e.position().subtract(from)) >= 0 ? side : side.scale(-1);
                    pushDir(e, s, 0.5, 0.1);
                }
                lineFx(sl, from, to, ParticleTypes.CLOUD, 1.0);
            });
        }
    }

    @Override
    protected AvatarMove buildUltimate() {
        // Olho da Tempestade: só o círculo verde em volta do chefe é seguro (bom para quem luta de perto)
        return AvatarMove.of("eye_of_storm", 88, 600, (sl, target, i) -> {
            if (i == 0) {
                announce("eye_of_storm");
                Vec3 c = boss.position();
                Telegraph.show(sl, Telegraph.Shape.safe(c, 5F).warmup(66).owner(boss));
                delayed(sl, ring(c, 5F, 26F, 66).noRunes(), hits -> {
                    hitAll(hits, 2.0F, e -> launch(e, 1.0));
                    for (int k = 0; k < 6; k++) ringFx(sl, c, 6 + k * 3.5);
                    sl.sendParticles(ParticleTypes.GUST_EMITTER_LARGE, c.x, c.y + 1, c.z, 3, 6, 0.5, 6, 0);
                    sound(sl, c, SoundEvents.WIND_CHARGE_BURST.value(), 3.0F, 0.5F);
                    shake(sl, c, 40, 2.0F, 20);
                });
            }
            if (i == 30 && target.isAlive()) {
                // Pressão no meio do golpe: lâminas em direção ao alvo
                Vec3 d0 = dirTo(target);
                for (int k = -1; k <= 1; k++) {
                    Vec3 d = rotateY(d0, k * 18);
                    Vec3 from = boss.position().add(d.scale(5.5));
                    Vec3 to = from.add(d.scale(14));
                    delayed(sl, line(from, to, 1.3F, 16).noRunes(), hits -> {
                        hitAll(hits, 0.7F, null);
                        lineFx(sl, from, to, ParticleTypes.SWEEP_ATTACK, 1.6);
                    });
                }
            }
            if (i < 66 && i % 2 == 0) {
                Vec3 c = boss.position();
                for (int k = 0; k < 8; k++) {
                    double a = i * 0.3 + k * Math.PI / 4;
                    double r = 8 + (k % 4) * 4;
                    sl.sendParticles(ParticleTypes.CLOUD, c.x + Math.cos(a) * r, c.y + 0.5 + (k % 3), c.z + Math.sin(a) * r, 1, 0, 0, 0, 0);
                }
                if (i % 10 == 0) sound(sl, c, SoundEvents.BREEZE_IDLE_AIR, 1.6F, 0.6F);
            }
        }).anim("channel").ultimate();
    }
}
