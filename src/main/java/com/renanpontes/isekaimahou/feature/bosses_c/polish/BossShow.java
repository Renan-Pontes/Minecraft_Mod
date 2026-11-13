package com.renanpontes.isekaimahou.feature.bosses_c.polish;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.boss.BossKit;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * v1.0 (boss_polish): o "espetáculo" que todos os chefes do módulo dividem — morte em três atos (a energia vaza,
 * converge e estoura), a câmera da derrota, a onda de troca de fase e a trava das falas reativas (provocação ao
 * derrubar um jogador / último fôlego). Cada módulo de chefe tem a SUA cópia desta classe (módulos não dependem
 * uns dos outros); mude nas duas pontas se mexer aqui.
 *
 * Tudo roda no servidor com poucas partículas por tick (nada de varredura); o tremor passa pelo {@link BossKit#shake},
 * que já respeita a opção "tremor de tela" do cliente.
 */
public final class BossShow {
    /** Subtítulo da câmera da derrota (tradução única, no lang de bosses_a). */
    public static final String FALLEN_KEY = "boss.isekai_mahou.fallen";
    /** Últimos ticks da morte em que o corpo se desfaz (o renderer usa para o esmaecer). */
    public static final int DISSOLVE_TICKS = 14;

    private static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F), size);
    }

    private static Vec3 chest(LivingEntity boss) {
        return boss.position().add(0, boss.getBbHeight() * 0.55, 0);
    }

    // ------------------------------------------------------------------ morte

    /**
     * Golpe final: clarão, estalo grave e (se {@code killCam}) a câmera da derrota — cutscene curta em volta do
     * chefe caindo, que não trava ninguém e obedece a opção de cutscenes do jogador.
     */
    public static void deathStart(ServerLevel level, LivingEntity boss, int rgb, int totalTicks, boolean killCam) {
        Vec3 c = chest(boss);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        shockRing(level, boss.position().add(0, 0.25, 0), boss.getBbWidth() * 0.5 + 0.3, 28, 0.45);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.HOSTILE, 1.6F, 0.55F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BELL_RESONATE, SoundSource.HOSTILE, 2.0F, 0.6F);
        if (killCam) {
            Cutscenes.playNear(boss, 40, "boss_phase", boss.getDisplayName(), Component.translatable(FALLEN_KEY), rgb,
                    Mth.clamp(totalTicks - 6, 30, 46), null);
        }
    }

    /**
     * Chame a cada tick da morte ({@code t} = deathTime, {@code total} = duração). Ato 1: a energia vaza do corpo em
     * jatos, com um batimento que acelera. Ato 2: um anel no chão se fecha e faíscas são puxadas para o peito.
     * O estouro final é {@link #deathFinale}.
     */
    public static void deathTick(ServerLevel level, LivingEntity boss, int t, int total, int rgb) {
        if (total <= 0 || t > total) return;
        float k = t / (float) total;
        Vec3 c = chest(boss);
        double w = Math.max(0.8, boss.getBbWidth()), h = Math.max(1.0, boss.getBbHeight());
        RandomSource r = boss.getRandom();

        // Ato 1 — vazamento: jatos subindo de pontos do corpo, cada vez mais frequentes
        int every = k < 0.3F ? 4 : k < 0.6F ? 3 : 2;
        if (t % every == 0) {
            double x = c.x + (r.nextDouble() - 0.5) * w * 0.8;
            double y = boss.getY() + r.nextDouble() * h * 0.9;
            double z = c.z + (r.nextDouble() - 0.5) * w * 0.8;
            level.sendParticles(dust(rgb, 1.5F), x, y, z, 3, 0.1, 0.3, 0.1, 0);
            // count 0 = (dx,dy,dz) vira a velocidade: a faísca sobe
            level.sendParticles(ParticleTypes.END_ROD, x, y, z, 0, (r.nextDouble() - 0.5) * 0.2, 1, (r.nextDouble() - 0.5) * 0.2, 0.14 + k * 0.2);
        }
        int beat = k < 0.4F ? 14 : k < 0.7F ? 9 : 5;
        if (t % beat == 0) {
            level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.HOSTILE, 2.5F, 0.7F + k * 0.6F);
        }

        // Ato 2 — convergência: anel no chão fechando + faíscas puxadas para o peito
        if (k >= 0.5F) {
            if (t == Mth.ceil(total * 0.5F)) BossKit.shake(level, c, 28, 0.6F, total - t);
            if (t % 2 == 0) {
                double close = 1 - (k - 0.5F) / 0.5F;
                double rad = close * (w * 1.1 + 3.0) + 0.4;
                Vec3 g = boss.position().add(0, 0.15, 0);
                DustParticleOptions d = dust(rgb, 1.2F);
                for (int i = 0; i < 12; i++) {
                    double a = i * Math.PI / 6 + t * 0.21;
                    level.sendParticles(d, g.x + Math.cos(a) * rad, g.y, g.z + Math.sin(a) * rad, 1, 0, 0, 0, 0);
                }
                for (int i = 0; i < 2; i++) {
                    Vec3 dir = new Vec3(r.nextDouble() - 0.5, r.nextDouble() * 0.6, r.nextDouble() - 0.5).normalize();
                    Vec3 from = c.add(dir.scale(rad + 1.5));
                    level.sendParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 0, -dir.x, -dir.y, -dir.z, 0.32);
                }
            }
        }
    }

    /** Estouro final (chame logo antes de remover o corpo): clarão, onda de choque, coluna de luz e tremor. */
    public static void deathFinale(ServerLevel level, LivingEntity boss, int rgb) {
        Vec3 c = chest(boss);
        Vec3 g = boss.position().add(0, 0.3, 0);
        double w = Math.max(0.8, boss.getBbWidth());
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        shockRing(level, g, w * 0.4, 40, 0.62);
        // Casca de pó da cor do chefe
        DustParticleOptions d = dust(rgb, 2.2F);
        level.sendParticles(d, c.x, c.y, c.z, 46, w * 0.55, boss.getBbHeight() * 0.4, w * 0.55, 0);
        // Coluna de luz: a "alma" sobe
        for (double y = 0; y < 11; y += 0.7) {
            level.sendParticles(ParticleTypes.END_ROD, c.x, c.y + y, c.z, 0, 0, 1, 0, 0.22 + y * 0.02);
            if (((int) (y * 10)) % 2 == 0) level.sendParticles(d, c.x, c.y + y, c.z, 1, 0.12, 0.2, 0.12, 0);
        }
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.HOSTILE, 2.5F, 0.55F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_DEACTIVATE, SoundSource.HOSTILE, 3.0F, 0.6F);
        BossKit.shake(level, c, 32, 1.3F, 18);
        BossKit.flashNear(level, c, 20, rgb, 0.2F, 10);
    }

    /** Anel que se espalha rente ao chão (as partículas saem com velocidade para fora). */
    private static void shockRing(ServerLevel level, Vec3 g, double r0, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            double cx = Math.cos(a), cz = Math.sin(a);
            level.sendParticles(ParticleTypes.END_ROD, g.x + cx * r0, g.y, g.z + cz * r0, 0, cx, 0.02, cz, speed);
            if ((i & 1) == 0) level.sendParticles(ParticleTypes.POOF, g.x + cx * r0, g.y, g.z + cz * r0, 0, cx, 0, cz, speed * 0.6);
        }
    }

    // ------------------------------------------------------------------ fase / impacto

    /** Onda da troca de fase: anel para fora, pó da cor do chefe subindo e um tremor curto (sem dano, sem empurrão). */
    public static void phaseBurst(ServerLevel level, LivingEntity boss, int rgb) {
        Vec3 c = chest(boss);
        shockRing(level, boss.position().add(0, 0.25, 0), boss.getBbWidth() * 0.5 + 0.3, 32, 0.55);
        level.sendParticles(dust(rgb, 2.0F), c.x, c.y, c.z, 30, boss.getBbWidth() * 0.5, boss.getBbHeight() * 0.4, boss.getBbWidth() * 0.5, 0);
        level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.HOSTILE, 2.0F, 0.6F);
        BossKit.shake(level, c, 28, 0.5F, 16);
    }

    /** Acento de impacto de um golpe pesado: faíscas baixas para fora e tremor proporcional. */
    public static void impact(ServerLevel level, Vec3 pos, int rgb, float size) {
        int points = Mth.clamp((int) (10 + size * 6), 10, 28);
        DustParticleOptions d = dust(rgb, 1.4F);
        for (int i = 0; i < points; i++) {
            double a = i * Math.PI * 2 / points;
            double cx = Math.cos(a), cz = Math.sin(a);
            level.sendParticles(ParticleTypes.POOF, pos.x + cx * 0.4, pos.y + 0.15, pos.z + cz * 0.4, 0, cx, 0.05, cz, 0.22 + size * 0.05);
            if ((i & 1) == 0) level.sendParticles(d, pos.x + cx * size * 0.5, pos.y + 0.2, pos.z + cz * size * 0.5, 1, 0.1, 0.1, 0.1, 0);
        }
        BossKit.shake(level, pos, 14 + size * 4, Math.min(1.2F, 0.35F + size * 0.15F), 8);
    }

    // ------------------------------------------------------------------ falas

    /**
     * Trava das falas reativas: no máximo uma a cada 15 s (tela limpa) e "último fôlego" uma vez por luta.
     * Não é salvo: depois de recarregar o mundo o chefe pode repetir a fala, o que não faz mal.
     */
    public static final class Voice {
        private static final int GAP = 300;
        private long nextAt;
        private boolean lastStandSaid;

        /** Pode provocar agora? (um jogador acabou de cair) */
        public boolean taunt(LivingEntity boss) {
            long now = boss.level().getGameTime();
            if (!boss.isAlive() || now < nextAt) return false;
            nextAt = now + GAP;
            return true;
        }

        /** Verdadeiro UMA vez, quando a vida cai a {@code frac} ou menos (volta a valer se o chefe se curar/resetar). */
        public boolean lastStand(LivingEntity boss, float frac) {
            float hp = boss.getHealth() / Math.max(1F, boss.getMaxHealth());
            if (lastStandSaid) {
                if (hp > 0.6F) lastStandSaid = false;
                return false;
            }
            if (!boss.isAlive() || hp > frac) return false;
            lastStandSaid = true;
            nextAt = boss.level().getGameTime() + GAP / 2;
            return true;
        }
    }

    private BossShow() {}
}
