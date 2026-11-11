package com.renanpontes.isekaimahou.glyph;

import java.util.HashMap;
import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.resources.ResourceLocation;

/**
 * Soma dos aumentos aplicados a um glifo (forma ou efeito). Mutável durante o parse; depois só leitura.
 *
 * Campos inteiros podem ser negativos (Enfraquecer = amplify -1, Encurtar = duration -1, Retardar = speed -1).
 * Use os helpers ({@link #power()}, {@link #radius}, {@link #duration}...) em vez de ler os campos crus.
 */
public final class SpellStats {
    // ---------- Potência ----------
    public int amplify;         // Amplificar (+1) / Enfraquecer (-1)
    public boolean concentrate; // Concentrar: área menor, mais dano
    public boolean crit;        // Crítico: chance de dano ×1.75
    public boolean vampiric;    // Vampírico: 25% do dano cura o conjurador
    public boolean overload;    // Sobrecarga: paga com vida, +40% poder
    public boolean efficient;   // Eficiente: -25% mana, +50% conjuração

    // ---------- Área e tempo ----------
    public int aoe;             // Expandir área
    public int duration;        // Estender (+1) / Encurtar (-1)
    public boolean persistent;  // Persistente: zonas duram ×2.5
    public int delay;           // Atraso (cada = 0.5s)
    public int repeat;          // Repetir (Eco): repete N vezes, 1s depois

    // ---------- Trajetória ----------
    public int speed;           // Acelerar (+1) / Retardar (-1)
    public int split;           // Dividir: +1 projétil/alvo cada
    public boolean spread;      // Espalhar: direções aleatórias
    public int pierce;          // Perfurar: atravessa N alvos
    public int ricochet;        // Ricochete: quica N vezes
    public int chain;           // Encadear: pula para N alvos próximos
    public boolean homing;      // Teleguiado
    public boolean ghost;       // Fantasma: atravessa blocos
    public boolean arc;         // Arco: cai como granada
    public boolean sensitive;   // Sensível: também atinge blocos

    // ---------- Alvo ----------
    public boolean enemiesOnly;
    public boolean alliesOnly;
    public boolean inverted;    // Inverter: efeito oposto (cada efeito decide o que significa)

    // ---------- Conjuração ----------
    public int charge;          // Carregar: +poder, +tempo de conjuração
    public boolean channel;     // Canalizar: repete enquanto segura a tecla
    public boolean silent;      // Silencioso: sem anúncio/som alto
    public boolean fortune;     // Fortuna (Quebrar/Colher)
    public boolean silkTouch;   // Toque Suave (Quebrar)

    /** Aumentos de módulos (id do aumento -> quantas vezes). */
    public final Map<ResourceLocation, Integer> custom = new HashMap<>();

    /** v0.7: id do aumento Insaciável (relentless). Fica em {@link #custom} para não mudar o formato dos stats. */
    public static final ResourceLocation RELENTLESS = IsekaiMahou.id("relentless");

    public int custom(ResourceLocation id) {
        return custom.getOrDefault(id, 0);
    }

    /** Insaciável: o Encadear deste glifo continua pulando enquanto houver inimigo e mana (ver SpellContext). */
    public boolean relentless() {
        return custom(RELENTLESS) > 0;
    }

    /**
     * v0.8: id do aumento Instantâneo (módulo arcane_duel). O núcleo lê: magia com ele em qualquer glifo tem tempo de
     * conjuração 0, custa +50% e por isso nunca entra na Pilha (não pode ser contrariada).
     */
    public static final ResourceLocation INSTANT = IsekaiMahou.id("instant");

    /** Instantâneo (ver {@link #INSTANT}). */
    public boolean instant() {
        return custom(INSTANT) > 0;
    }

    // ---------- Helpers ----------
    /** Multiplicador de poder (dano, cura, força de empurrão...). */
    public float power() {
        float p = 1.0F + 0.4F * amplify + 0.3F * charge;
        if (concentrate) p *= 1.3F;
        if (overload) p *= 1.4F;
        return Math.max(0.25F, p);
    }

    /** Raio final de uma área com base no raio padrão da forma/efeito. */
    public float radius(float base) {
        float r = base * (1.0F + 0.5F * aoe);
        if (concentrate) r *= 0.6F;
        return Math.max(0.5F, r);
    }

    /** Duração final em ticks. */
    public int duration(int baseTicks) {
        float d = baseTicks * (1.0F + 0.5F * duration);
        if (persistent) d *= 2.5F;
        return Math.max(10, Math.round(d));
    }

    /** Multiplicador de velocidade de projéteis/ondas. */
    public float speedMult() {
        return Math.max(0.3F, 1.0F + 0.3F * speed);
    }

    /** Quantos projéteis/feixes/alvos (1 + Dividir). */
    public int count() {
        return 1 + Math.max(0, split);
    }

    /** Atraso em ticks. */
    public int delayTicks() {
        return Math.max(0, delay) * 10;
    }

    public SpellStats copy() {
        SpellStats s = new SpellStats();
        s.amplify = amplify; s.concentrate = concentrate; s.crit = crit; s.vampiric = vampiric;
        s.overload = overload; s.efficient = efficient; s.aoe = aoe; s.duration = duration;
        s.persistent = persistent; s.delay = delay; s.repeat = repeat; s.speed = speed; s.split = split;
        s.spread = spread; s.pierce = pierce; s.ricochet = ricochet; s.chain = chain; s.homing = homing;
        s.ghost = ghost; s.arc = arc; s.sensitive = sensitive; s.enemiesOnly = enemiesOnly;
        s.alliesOnly = alliesOnly; s.inverted = inverted; s.charge = charge; s.channel = channel;
        s.silent = silent; s.fortune = fortune; s.silkTouch = silkTouch;
        s.custom.putAll(custom);
        return s;
    }
}
