package com.renanpontes.isekaimahou.feature.espada.entity;

import com.renanpontes.isekaimahou.feature.espada.EspadaPayloads;
import com.renanpontes.isekaimahou.feature.espada.EspadaPayloads.Fx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Efeitos dos Espada, lado servidor: cada chamada vira UM pacote pequeno; o cliente faz a coreografia
 * ("antecipação → impacto → rescaldo") respeitando o preset gráfico. Ver {@code client/EspadaFxClient}.
 */
public final class EspadaFx {
    public static final int SONIDO = 0, CHARGE = 1, BEAM = 2, EXPLOSION = 3, RAIN = 4, DARKNESS = 5, BREATH = 6, CRUMBLE = 7,
            CLAWS = 8, ROAR = 9, RELEASE = 10, SPARK = 11, HIERRO_BREAK = 12, LANCE_FORM = 13, SLASH = 14, FLASH = 15,
            REGEN = 17, GRAB = 18, AURA_PULSE = 19, LIGHTNING = 20, CLAW_LAUNCH = 21, BREATH_SPHERE = 22;

    // Cores
    public static final int ULQ_GREEN = 0x3DFF8E;
    public static final int ULQ_DEEP = 0x0F7A3E;
    public static final int ULQ_BLACK = 0x060A08;
    public static final int RESPIRA = 0x3A1F4D;
    public static final int RESPIRA_LIGHT = 0x7A4E96;
    public static final int AGED = 0xA89068;
    public static final int GRIMM_BLUE = 0x39C2FF;
    public static final int BLOOD = 0xC0182E;

    /** Estilos de explosão/feixe ({@code aux}). */
    public static final int STYLE_GREEN = 0, STYLE_DARK = 1, STYLE_BLOOD = 2, STYLE_RESPIRA = 3, STYLE_BLUE = 4;

    public static void send(ServerLevel l, int kind, Entity e, Vec3 a, Vec3 b, float p1, float p2, int color, int aux) {
        EspadaPayloads.send(l, new Fx(kind, e == null ? -1 : e.getId(), a, b, p1, p2, color, aux));
    }

    public static void sonido(ServerLevel l, Entity e, Vec3 from, Vec3 to, int color) {
        send(l, SONIDO, e, from, to, 0, 0, color, 0);
    }

    /** Energia se juntando em {@code at} por {@code ticks} (Cero, Gran Rey Cero, Cero Oscuras). */
    public static void charge(ServerLevel l, Entity e, Vec3 at, int ticks, float size, int color, int style) {
        send(l, CHARGE, e, at, Vec3.ZERO, ticks, size, color, style);
    }

    /** Feixe de {@code from} a {@code to}: largura, duração e estilo (verde, escuro com borda verde, sangue). */
    public static void beam(ServerLevel l, Entity e, Vec3 from, Vec3 to, float width, int life, int color, int style) {
        send(l, BEAM, e, from, to, width, life, color, style);
    }

    public static void explosion(ServerLevel l, Vec3 at, float radius, int color, int style) {
        send(l, EXPLOSION, null, at, Vec3.ZERO, radius, 0, color, style);
    }

    public static void rain(ServerLevel l, Entity e, Vec3 center, float radius, int ticks, int color) {
        send(l, RAIN, e, center, Vec3.ZERO, radius, ticks, color, 0);
    }

    public static void darkness(ServerLevel l, Entity e, int ticks) {
        send(l, DARKNESS, e, e.position(), Vec3.ZERO, ticks, 0, ULQ_BLACK, 0);
    }

    /**
     * Nuvem de Respira (v1.3.2): o cliente simula os MESMOS "bolos" de miasma do servidor — {@code puffs} bolos saindo de
     * {@code origin} em leque de {@code angleDeg} graus na direção {@code dir} (ou em anel, {@code ring}), andando
     * {@code speed} blocos/tick e crescendo por {@code life} ticks, parando em paredes. Assim o que se vê é o que envelhece.
     */
    public static void respiraCloud(ServerLevel l, Entity e, Vec3 origin, Vec3 dir, float angleDeg, double speed, int life, int puffs, boolean ring) {
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : dir.normalize();
        send(l, BREATH, e, origin, d.scale(speed), angleDeg, life, RESPIRA, (ring ? 1 : 0) | (Math.max(1, puffs) << 1));
    }

    /** Anel de miasma: {@code puffs} bolos num círculo de raio {@code startRadius} em volta de {@code center}, andando para fora a {@code speed} (negativo = para dentro). */
    public static void respiraRing(ServerLevel l, Entity e, Vec3 center, float startRadius, double speed, int life, int puffs) {
        // No modo anel, p1 é o raio inicial e o sinal da velocidade vai no vetor (x).
        send(l, BREATH, e, center, new Vec3(speed, 0, 0), startRadius, life, RESPIRA, 1 | (Math.max(1, puffs) << 1));
    }

    /**
     * Respira em 360° (w10): {@code puffs} bolos saindo de {@code origin} em TODAS as direções (ver {@link #respiraDirs}),
     * andando {@code speed} blocos/tick e crescendo por {@code life} ticks, parando em paredes. O cliente simula igual.
     */
    public static void respiraSphere(ServerLevel l, Entity e, Vec3 origin, double speed, int life, int puffs) {
        send(l, BREATH_SPHERE, e, origin, new Vec3(speed, 0, 0), 0, life, RESPIRA, Math.max(1, puffs));
    }

    /**
     * Direções dos bolos do Respira em 360° (servidor e cliente usam a MESMA conta, então a nuvem que se vê é a que
     * envelhece): ~60% num anel quase rente ao chão (4° para baixo), o resto num anel inclinado para cima (30°),
     * defasado meio passo — vira uma esfera/cúpula de miasma em volta de quem sopra.
     */
    public static Vec3[] respiraDirs(int n) {
        n = Math.max(1, n);
        int low = Math.max(1, Math.round(n * 0.6F));
        Vec3[] out = new Vec3[n];
        for (int k = 0; k < n; k++) {
            if (k < low) {
                out[k] = Vec3.directionFromRotation(4F, 360F * k / low);
            } else {
                int hiN = n - low;
                int j = k - low;
                out[k] = Vec3.directionFromRotation(-30F, 360F * (j + 0.5F) / hiN);
            }
        }
        return out;
    }

    /**
     * Raios: com {@code from != to}, raios verdes crepitando ao longo do feixe (largura {@code radius}); com
     * {@code from == to}, explosão de raios caindo do céu e correndo pelo chão num raio {@code radius}.
     */
    public static void lightning(ServerLevel l, Vec3 from, Vec3 to, float radius, int color, int style) {
        send(l, LIGHTNING, null, from, to, radius, 0, color, style);
    }

    /** Desgarrón: o golpe das garras (cinco arcos azuis na frente das mãos) no momento em que as lâminas saem. */
    public static void clawLaunch(ServerLevel l, Entity e, Vec3 origin, Vec3 dir, float spacing, int count, int color) {
        send(l, CLAW_LAUNCH, e, origin, dir, spacing, count, color, 0);
    }

    public static void crumble(ServerLevel l, Vec3 at) {
        send(l, CRUMBLE, null, at, Vec3.ZERO, 0, 0, AGED, 0);
    }

    public static void claws(ServerLevel l, Entity e, Vec3 origin, Vec3 dir, float length, int count, int color) {
        send(l, CLAWS, e, origin, dir, length, count, color, 0);
    }

    public static void roar(ServerLevel l, Entity e, float radius, int color) {
        send(l, ROAR, e, e.position(), Vec3.ZERO, radius, 0, color, 0);
    }

    /** Explosão de liberação (Resurrección): pilar, onda e partículas da cor. {@code variant} por chefe/forma. */
    public static void release(ServerLevel l, Entity e, int color, int variant) {
        send(l, RELEASE, e, e.position(), Vec3.ZERO, 0, 0, color, variant);
    }

    public static void spark(ServerLevel l, Vec3 at, int color) {
        send(l, SPARK, null, at, Vec3.ZERO, 0, 0, color, 0);
    }

    public static void hierroBreak(ServerLevel l, Entity e) {
        send(l, HIERRO_BREAK, e, e.position().add(0, e.getBbHeight() * 0.6, 0), Vec3.ZERO, 0, 0, ULQ_GREEN, 0);
    }

    public static void lanceForm(ServerLevel l, Entity e, int ticks) {
        send(l, LANCE_FORM, e, e.position(), Vec3.ZERO, ticks, 0, ULQ_GREEN, 0);
    }

    public static void slash(ServerLevel l, Entity e, Vec3 center, Vec3 dir, float radius, int color) {
        send(l, SLASH, e, center, dir, radius, 0, color, 0);
    }

    public static void flash(ServerLevel l, Vec3 at, float radius, int color) {
        send(l, FLASH, null, at, Vec3.ZERO, radius, 0, color, 0);
    }

    public static void regen(ServerLevel l, Entity e) {
        send(l, REGEN, e, e.position(), Vec3.ZERO, 0, 0, ULQ_GREEN, 0);
    }

    public static void grab(ServerLevel l, Entity e, Vec3 from, Vec3 to, int color) {
        send(l, GRAB, e, from, to, 0, 0, color, 0);
    }

    public static void auraPulse(ServerLevel l, Entity e, float radius, int color) {
        send(l, AURA_PULSE, e, e.position(), Vec3.ZERO, radius, 0, color, 0);
    }

    private EspadaFx() {}
}
