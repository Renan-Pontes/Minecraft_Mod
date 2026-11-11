package com.renanpontes.isekaimahou.feature.bosses_d.entity.kit;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.AvatarMove;
import com.renanpontes.isekaimahou.feature.bosses_d.entity.TrialAvatar;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Conjunto de golpes de um elemento do Avatar. Os dois PRIMEIROS golpes de {@link #build()} são a "assinatura"
 * (emprestada quando o elemento é o pai B de uma fusão). Todos os golpes usam {@link Telegraph} com dono = chefe:
 * se ele morre ou troca de fase, a telegrafia some sem acertar ninguém.
 */
public abstract class AvatarKit {
    protected final TrialAvatar boss;
    /** Elemento do kit (status/visual próprios); a COR da telegrafia é sempre a do chefe. */
    protected final Element kitElement;
    private boolean secondary;
    @Nullable private List<AvatarMove> all;
    @Nullable private AvatarMove ult;

    protected AvatarKit(TrialAvatar boss, Element kitElement) {
        this.boss = boss;
        this.kitElement = kitElement;
    }

    /** Golpes do elemento (os 2 primeiros = assinatura). */
    protected abstract List<AvatarMove> build();

    /** Golpe máximo da fase 3 (null = sem). */
    @Nullable
    protected abstract AvatarMove buildUltimate();

    /** Status/efeito do elemento em quem é atingido. */
    public abstract void flavor(LivingEntity e);

    /** Investida comum terminou: rastro do elemento entre {@code from} e {@code to}. */
    public void onDashTrail(ServerLevel sl, Vec3 from, Vec3 to) {
    }

    /** Perigos próprios (tornados...) — chamado todo tick do servidor. */
    public void tickPassive(ServerLevel sl) {
    }

    /** Troca de fase/morte: some com perigos próprios. */
    public void reset() {
    }

    void markSecondary() {
        this.secondary = true;
    }

    public final List<AvatarMove> moves() {
        if (all == null) {
            all = build();
            ult = buildUltimate();
        }
        return secondary ? all.subList(0, Math.min(2, all.size())) : all;
    }

    @Nullable
    public final AvatarMove ultimate() {
        moves();
        return secondary ? null : ult;
    }

    // =====================================================================================
    //  Atalhos
    // =====================================================================================

    protected int col() {
        return boss.rgb();
    }

    protected int warm(int base) {
        return boss.warm(base);
    }

    protected Element fxEl() {
        return boss.element();
    }

    protected int phase() {
        return boss.phase();
    }

    protected int rank() {
        return boss.rank();
    }

    protected void announce(String key) {
        boss.announceMove(key);
    }

    protected Telegraph.Shape circle(Vec3 c, float r, int w) {
        return Telegraph.Shape.circle(c, r).warmup(w).color(col()).owner(boss);
    }

    protected Telegraph.Shape ring(Vec3 c, float in, float out, int w) {
        return Telegraph.Shape.ring(c, in, out).warmup(w).color(col()).owner(boss);
    }

    protected Telegraph.Shape line(Vec3 a, Vec3 b, float width, int w) {
        return Telegraph.Shape.line(a, b, width).warmup(w).color(col()).owner(boss);
    }

    protected Telegraph.Shape cone(Vec3 o, float yaw, float angle, float range, int w) {
        return Telegraph.Shape.cone(o, yaw, angle, range).warmup(w).color(col()).owner(boss);
    }

    protected Telegraph.Shape follow(Entity e, float r, int w) {
        return Telegraph.Shape.follow(e, r).warmup(w).color(col()).owner(boss);
    }

    protected int show(ServerLevel sl, Telegraph.Shape s) {
        return Telegraph.show(sl, s);
    }

    protected int delayed(ServerLevel sl, Telegraph.Shape s, Consumer<List<LivingEntity>> cb) {
        return Telegraph.delayed(sl, s, cb);
    }

    /** Roda depois de {@code delay} ticks, só se o chefe ainda vive e não trocou de fase. */
    protected void sched(ServerLevel sl, int delay, Runnable r) {
        int ep = boss.epoch();
        BossKit.schedule(sl, Math.max(1, delay), () -> {
            if (boss.isAlive() && boss.epoch() == ep) r.run();
        });
    }

    /** Dano + status do elemento em cada inimigo atingido. */
    protected void hitAll(List<LivingEntity> hits, float mult, @Nullable Consumer<LivingEntity> extra) {
        for (LivingEntity e : hits) {
            if (!boss.isFoe(e)) continue;
            boss.hitFoe(e, mult);
            if (extra != null) extra.accept(e);
        }
    }

    /** Inimigos marcáveis (jogadores primeiro), até {@code max}, a até {@code r} blocos do chefe. */
    protected List<LivingEntity> marks(int max, double r) {
        List<LivingEntity> all = boss.foesNear(boss.position(), r);
        return all.size() > max ? new ArrayList<>(all.subList(0, max)) : all;
    }

    /** Ponto de chão aleatório entre {@code minR} e {@code maxR} de {@code around}, dentro da arena. */
    protected Vec3 randomSpot(Vec3 around, double minR, double maxR) {
        for (int i = 0; i < 12; i++) {
            double a = boss.getRandom().nextDouble() * Math.PI * 2;
            double d = minR + boss.getRandom().nextDouble() * Math.max(0.1, maxR - minR);
            Vec3 p = new Vec3(around.x + Math.cos(a) * d, around.y, around.z + Math.sin(a) * d);
            Vec3 g = boss.groundAt(p);
            if (g != null && boss.inArena(g)) return g;
        }
        return around;
    }

    /** Círculo que persegue o alvo e trava no fim; {@code impact} recebe o ponto travado e quem estava lá. */
    protected void markBlast(ServerLevel sl, LivingEntity t, float r, int w, BiConsumer<Vec3, List<LivingEntity>> impact) {
        markBlast(sl, t, r, w, false, impact);
    }

    /** Igual, com {@code fromSky} = algo cai do céu (meteoro, lança); senão o chão borbulha antes de estourar. */
    protected void markBlast(ServerLevel sl, LivingEntity t, float r, int w, boolean fromSky,
                             BiConsumer<Vec3, List<LivingEntity>> impact) {
        int[] id = new int[1];
        // Trava cedo o bastante para sair ANDANDO (~0,2 bloco/tick) do círculo depois que ele para (raio + meia
        // largura + folga). Antes de travar, persegue no mínimo 8 ticks (metade do aquecimento, se ele for curto).
        int need = Math.max(10, (int) Math.ceil((r + 0.5F) / 0.2F));
        int lock = Math.min(Math.max(w - 8, w / 2), need);
        id[0] = delayed(sl, follow(t, r, w).lock(lock), hits -> {
            Vec3 at = Telegraph.centerOf(id[0]);
            impact.accept(at != null ? at : t.position(), hits);
        });
        Supplier<Vec3> spot = () -> {
            Vec3 c = Telegraph.centerOf(id[0]);
            return c != null ? c : t.position();
        };
        if (fromSky) fallFx(sl, spot, w);
        else rumbleFx(sl, spot, w, r);
    }

    /** Círculo fixo atrasado. */
    protected void spotBlast(ServerLevel sl, Vec3 spot, float r, int w, BiConsumer<Vec3, List<LivingEntity>> impact) {
        delayed(sl, circle(spot, r, w), hits -> impact.accept(spot, hits));
    }

    /** "Algo caindo do céu" nos últimos 12 ticks antes do impacto (meteoro, lança, rocha). */
    protected void fallFx(ServerLevel sl, Supplier<Vec3> spot, int impactTick) {
        int start = Math.max(1, impactTick - 12);
        for (int k = start; k < impactTick; k++) {
            float prog = (k - start) / (float) Math.max(1, impactTick - start);
            sched(sl, k, () -> {
                Vec3 s = spot.get();
                double h = 14 * (1 - prog);
                ParticleOptions p = fallParticle();
                sl.sendParticles(p, s.x, s.y + h, s.z, 4, 0.25, 0.25, 0.25, 0.01);
                sl.sendParticles(ElementFx.dust(fxEl(), 2.0F), s.x, s.y + h + 0.5, s.z, 3, 0.2, 0.4, 0.2, 0);
            });
        }
    }

    /** O chão "ferve" nos últimos 10 ticks antes de um golpe que sai da terra (gêiser, pilar, espinho). */
    protected void rumbleFx(ServerLevel sl, Supplier<Vec3> spot, int impactTick, float r) {
        for (int k = Math.max(1, impactTick - 10); k < impactTick; k += 2) {
            sched(sl, k, () -> {
                Vec3 s = spot.get();
                sl.sendParticles(ElementFx.signature(fxEl()), s.x, s.y + 0.15, s.z, 4, r * 0.4, 0.05, r * 0.4, 0.03);
                sl.sendParticles(ElementFx.dust(fxEl(), 1.0F), s.x, s.y + 0.1, s.z, 3, r * 0.45, 0.02, r * 0.45, 0);
            });
        }
    }

    /** Partícula do que cai em {@link #fallFx}. */
    protected ParticleOptions fallParticle() {
        return ElementFx.signature(fxEl());
    }

    // ---------------- efeitos visuais/sonoros ----------------
    protected void burst(ServerLevel sl, Vec3 at, int count, double spread) {
        ElementFx.burst(sl, at, fxEl(), count, spread);
    }

    protected void ringFx(ServerLevel sl, Vec3 c, double r) {
        ElementFx.ring(sl, c.add(0, 0.15, 0), fxEl(), r, Math.max(16, (int) (r * 7)));
    }

    protected void column(ServerLevel sl, Vec3 base, double height, ParticleOptions p, int perBlock) {
        for (double y = 0; y < height; y += 1.0 / Math.max(1, perBlock)) {
            sl.sendParticles(p, base.x, base.y + y, base.z, 1, 0.15, 0.05, 0.15, 0.01);
        }
    }

    protected void lineFx(ServerLevel sl, Vec3 a, Vec3 b, ParticleOptions p, double step) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-3) return;
        Vec3 u = d.scale(1 / len);
        for (double s = 0; s <= len; s += step) {
            Vec3 q = a.add(u.scale(s));
            sl.sendParticles(p, q.x, q.y + 0.2, q.z, 1, 0.1, 0.1, 0.1, 0.01);
        }
    }

    protected void sound(ServerLevel sl, Vec3 at, SoundEvent s, float vol, float pitch) {
        sl.playSound(null, at.x, at.y, at.z, s, SoundSource.HOSTILE, vol, pitch);
    }

    protected void shake(ServerLevel sl, Vec3 at, double r, float intensity, int ticks) {
        BossKit.shake(sl, at, r, intensity, ticks);
    }

    // ---------------- empurrões e status ----------------
    protected void push(LivingEntity e, Vec3 from, double strength, double up) {
        Vec3 d = e.position().subtract(from).multiply(1, 0, 1);
        d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
        double k = strength * (1.0 - Mth.clamp(e.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE), 0, 0.8));
        e.push(d.x * k, up, d.z * k);
        e.hurtMarked = true;
    }

    protected void pushDir(LivingEntity e, Vec3 dir, double strength, double up) {
        Vec3 d = dir.multiply(1, 0, 1);
        d = d.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : d.normalize();
        e.push(d.x * strength, up, d.z * strength);
        e.hurtMarked = true;
    }

    protected void launch(LivingEntity e, double up) {
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * 0.5, Math.max(v.y, up), v.z * 0.5);
        e.hurtMarked = true;
        if (e instanceof Player) e.fallDistance = 0;
    }

    protected void status(LivingEntity e, Holder<MobEffect> eff, int ticks, int amp) {
        e.addEffect(new MobEffectInstance(eff, ticks, amp), boss);
    }

    /** Direção horizontal do chefe até o alvo (ou o olhar). */
    protected Vec3 dirTo(LivingEntity t) {
        Vec3 d = t.position().subtract(boss.position()).multiply(1, 0, 1);
        if (d.lengthSqr() < 1.0E-4) d = boss.getLookAngle().multiply(1, 0, 1);
        return d.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : d.normalize();
    }

    protected static Vec3 rotateY(Vec3 v, double deg) {
        double r = Math.toRadians(deg);
        double c = Math.cos(r), s = Math.sin(r);
        return new Vec3(v.x * c - v.z * s, 0, v.x * s + v.z * c);
    }

    protected static float yawOf(Vec3 dir) {
        return (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90F;
    }

    // =====================================================================================
    //  Padrões reaproveitados por vários elementos
    // =====================================================================================

    /** Nova "dentro → fora": círculo interno (saia!) e depois anel externo (volte!). */
    protected AvatarMove insideOutNova(String id, float innerMult, float outerMult) {
        Vec3[] center = new Vec3[1];
        return AvatarMove.of(id, 60, 200, (sl, target, i) -> {
            if (i == 0) {
                Vec3 c = boss.position();
                center[0] = c;
                announce(id);
                // Tempos fixos (não encurtam no rank alto): do centro dá para sair a tempo andando
                delayed(sl, circle(c, 6F, 28), hits -> {
                    hitAll(hits, innerMult, e -> {
                        flavor(e);
                        push(e, c, 0.6, 0.3);
                    });
                    ringFx(sl, c, 3);
                    ringFx(sl, c, 6);
                    burst(sl, c.add(0, 1, 0), 50, 2.0);
                    sound(sl, c, explodeSound(), 1.6F, 0.9F);
                    shake(sl, c, 18, 1.0F, 12);
                });
                delayed(sl, ring(c, 6F, 12.5F, 46), hits -> {
                    hitAll(hits, outerMult, this::flavor);
                    ringFx(sl, c, 8);
                    ringFx(sl, c, 10.5);
                    ringFx(sl, c, 12.5);
                    sound(sl, c, explodeSound(), 1.8F, 0.7F);
                    shake(sl, c, 22, 1.2F, 14);
                });
            }
            if (i < 46 && i % 3 == 0 && center[0] != null) {
                // Energia girando para dentro durante a carga
                double a = i * 0.35;
                for (int k = 0; k < 3; k++) {
                    double ang = a + k * Math.PI * 2 / 3;
                    double r = i < 28 ? 6 - i * 0.18 : 12 - (i - 28) * 0.3;
                    sl.sendParticles(ElementFx.dust(fxEl(), 1.4F), center[0].x + Math.cos(ang) * r, center[0].y + 0.4,
                            center[0].z + Math.sin(ang) * r, 1, 0, 0, 0, 0);
                }
            }
        }).anim("nova").range(0, 10).weight(8);
    }

    /** Feixes giratórios (linha): telegrafia das posições iniciais e depois N feixes girando ao redor do chefe. */
    protected AvatarMove rotatingBeams(String id) {
        float[] speed = new float[1];
        return AvatarMove.of(id, 110, 260, (sl, target, i) -> {
            if (i == 0) {
                int n = phase() >= 3 ? 3 : 2;
                Vec3 o = boss.position();
                Vec3 d = dirTo(target);
                float start = (float) Math.toDegrees(Math.atan2(d.z, d.x));
                speed[0] = (phase() >= 3 ? 2.3F : 1.7F) * (boss.getRandom().nextBoolean() ? 1 : -1);
                boss.setYRot(yawOf(d));
                for (int k = 0; k < n; k++) {
                    double a = Math.toRadians(start + k * 360.0 / n);
                    Vec3 end = o.add(Math.cos(a) * 18, 0, Math.sin(a) * 18);
                    show(sl, line(o, end, 1.4F, warm(26)));
                    // Seta curta mostrando para que lado o feixe vai girar
                    double a2 = a + Math.toRadians(Math.signum(speed[0]) * 12);
                    show(sl, line(o.add(Math.cos(a2) * 6, 0, Math.sin(a2) * 6), o.add(Math.cos(a2) * 12, 0, Math.sin(a2) * 12), 0.6F,
                            warm(26)).noFlash().noRunes().color(Telegraph.WARNING));
                }
                announce(id);
                sound(sl, o, net.minecraft.sounds.SoundEvents.BEACON_POWER_SELECT, 2.0F, 0.8F);
                sched(sl, warm(26), () -> {
                    // O golpe pode ter sido interrompido (alvo caiu/saiu): sem isto os feixes ficariam girando no
                    // cliente para sempre, sem dano e sem ninguém para desligá-los
                    if (!id.equals(boss.currentMoveId())) return;
                    boss.startBeams(n, speed[0], 18F, start);
                    sound(sl, boss.position(), net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 2.0F, 1.4F);
                });
            }
            if (boss.beamsActive()) {
                boss.tickBeamDamage(0.8F, this::flavor);
                if (i % 10 == 0) sound(sl, boss.position(), net.minecraft.sounds.SoundEvents.BEACON_AMBIENT, 1.5F, 1.6F);
            }
            if (i == 104) boss.stopBeams();
        }).anim("beam").range(0, 18).weight(7).locked().onEnd(boss::stopBeams);
    }

    /** Prisão: marca que persegue e prende (enraizado) quem ficar no círculo. */
    protected AvatarMove prison(String id, float mult, Consumer<LivingEntity> trapped) {
        return AvatarMove.of(id, 46, 220, (sl, target, i) -> {
            if (i == 6) {
                for (LivingEntity f : marks(Math.max(1, phase()), 28)) {
                    markBlast(sl, f, 1.8F, warm(34), (at, hits) -> {
                        hitAll(hits, mult, e -> {
                            flavor(e);
                            trapped.accept(e);
                            status(e, com.renanpontes.isekaimahou.core.CoreRegistries.ROOTED, 40, 0);
                            cage(sl, e, 40);
                        });
                        burst(sl, at.add(0, 1, 0), 30, 0.8);
                    });
                }
            }
        }).anim("vortex").range(0, 28).weight(8);
    }

    /** Partículas de "jaula" em volta de alguém preso por {@code ticks}. */
    protected void cage(ServerLevel sl, LivingEntity e, int ticks) {
        for (int k = 0; k < ticks; k += 4) {
            sched(sl, k, () -> {
                if (!e.isAlive()) return;
                for (int j = 0; j < 8; j++) {
                    double a = j * Math.PI / 4 + e.tickCount * 0.2;
                    sl.sendParticles(ElementFx.dust(fxEl(), 1.2F), e.getX() + Math.cos(a) * 0.8, e.getY() + 0.2 + (j % 4) * 0.5,
                            e.getZ() + Math.sin(a) * 0.8, 1, 0, 0, 0, 0);
                }
            });
        }
    }

    /** Golpe máximo "zonas seguras": tudo fora dos círculos verdes é atingido. */
    protected AvatarMove safeZones(String id, float mult) {
        return AvatarMove.of(id, 86, 600, (sl, target, i) -> {
            if (i == 0) {
                announce(id);
                Vec3 c = boss.position();
                int n = 3;
                List<Vec3> zones = new ArrayList<>();
                double base = boss.getRandom().nextDouble() * Math.PI * 2;
                for (int k = 0; k < n; k++) {
                    double a = base + k * Math.PI * 2 / n;
                    Vec3 p = new Vec3(c.x + Math.cos(a) * 9, c.y, c.z + Math.sin(a) * 9);
                    Vec3 g = boss.groundAt(p);
                    zones.add(g != null && boss.inArena(g) ? g : randomSpot(c, 5, 10));
                }
                int w = 64;
                for (Vec3 z : zones) Telegraph.show(sl, Telegraph.Shape.safe(z, 3.2F).warmup(w).owner(boss));
                delayed(sl, circle(c, 26F, w).noRunes(), hits -> {
                    for (LivingEntity e : hits) {
                        if (!boss.isFoe(e)) continue;
                        boolean safe = false;
                        for (Vec3 z : zones) if (Telegraph.Shape.safe(z, 3.2F).contains(e)) safe = true;
                        if (safe) continue;
                        boss.hitFoe(e, mult);
                        flavor(e);
                    }
                    for (int k = 0; k < 6; k++) ringFx(sl, c, 4 + k * 4);
                    burst(sl, c.add(0, 1.5, 0), 120, 6.0);
                    sound(sl, c, explodeSound(), 3.0F, 0.6F);
                    shake(sl, c, 40, 2.2F, 24);
                });
            }
            if (i < 64 && i % 4 == 0) {
                Vec3 c = boss.position();
                for (int k = 0; k < 10; k++) {
                    double a = boss.getRandom().nextDouble() * Math.PI * 2;
                    double r = 3 + boss.getRandom().nextDouble() * 20;
                    sl.sendParticles(ElementFx.signature(fxEl()), c.x + Math.cos(a) * r, c.y + 0.3, c.z + Math.sin(a) * r, 1, 0, 0.1, 0, 0.02);
                }
            }
        }).anim("channel").ultimate();
    }

    protected SoundEvent explodeSound() {
        return net.minecraft.sounds.SoundEvents.GENERIC_EXPLODE.value();
    }
}
