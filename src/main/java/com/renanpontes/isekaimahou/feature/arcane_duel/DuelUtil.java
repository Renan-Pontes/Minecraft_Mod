package com.renanpontes.isekaimahou.feature.arcane_duel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.player.Player;

/** Auxiliares do módulo (servidor). */
public final class DuelUtil {
    /** "conjurador|receita|chave" → tick em que já rodou (efeitos de área que só devem rodar 1× por conjuração). */
    private static final Map<String, Long> RAN = new HashMap<>();

    /**
     * true só na primeira vez neste tick para esta conjuração (um efeito de área aplicado em vários alvos da mesma
     * forma — Ira Divina, Copiar Magia — roda uma vez só).
     */
    public static boolean firstThisTick(SpellContext ctx, String key) {
        long now = ctx.level().getGameTime();
        String k = ctx.casterId() + "|" + System.identityHashCode(ctx.rootSpell()) + "|" + key;
        Long last = RAN.put(k, now);
        if (RAN.size() > 512) {
            for (Iterator<Map.Entry<String, Long>> it = RAN.entrySet().iterator(); it.hasNext(); ) {
                if (it.next().getValue() < now - 2) it.remove();
            }
        }
        return last == null || last != now;
    }

    /** "conjurador|receita|chave" → tick da última vez (efeitos que só podem rodar 1× por conjuração, mesmo em zonas). */
    private static final Map<String, Long> WINDOW = new HashMap<>();

    /**
     * true se esta conjuração ainda não rodou {@code key} nos últimos {@code window} ticks. Uma zona/Eco que reaplica o
     * efeito a cada pulso não repete um efeito "único" (Ira Divina).
     */
    public static boolean firstWithin(SpellContext ctx, String key, int window) {
        long now = ctx.level().getGameTime();
        String k = ctx.casterId() + "|" + System.identityHashCode(ctx.rootSpell()) + "|" + key;
        Long last = WINDOW.get(k);
        if (last != null && now - last < window) return false;
        WINDOW.put(k, now);
        if (WINDOW.size() > 256) WINDOW.values().removeIf(t -> now - t > 1200);
        return true;
    }

    /** Efeito "Procurado" do módulo cities (lido por id: pode não existir). */
    private static final ResourceLocation WANTED = IsekaiMahou.id("wanted");

    /**
     * Efeitos ruins que são REGRA, não status: Exaustão Mágica (Domínio) e Procurado (ficha criminal das cidades).
     * O Resguardo não os bloqueia nem os limpa.
     */
    public static boolean isRuleEffect(Holder<MobEffect> effect) {
        return effect.is(CoreRegistries.MAGIC_EXHAUSTION.getKey()) || effect.is(WANTED);
    }

    public static boolean isBoss(@Nullable Entity e) {
        return BossKit.isBossLike(e);
    }

    /** Bicho domado (cão, gato, cavalo...): a Ira Divina poupa. Invocações (com dono, mas não domadas) não. */
    public static boolean isTamed(Entity e) {
        return (e instanceof TamableAnimal t && t.isTame()) || (e instanceof AbstractHorse h && h.isTamed());
    }

    public static void actionbar(@Nullable LivingEntity e, Component msg) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(msg, true);
    }

    public static void actionbar(@Nullable LivingEntity e, Component msg, ChatFormatting color) {
        actionbar(e, msg.copy().withStyle(color));
    }

    public static boolean isCreative(@Nullable LivingEntity e) {
        return e instanceof Player p && p.getAbilities().instabuild;
    }

    /** A magia (qualquer estágio) tem o efeito {@code id}? */
    public static boolean hasEffect(ParsedSpell spell, ResourceLocation id) {
        for (ParsedSpell st : spell.stages()) {
            for (ParsedSpell.EffectEntry e : st.effects()) {
                EffectGlyph g = e.effect();
                if (g.id().equals(id)) return true;
            }
        }
        return false;
    }

    /** O aumento de módulo {@code id} está no glifo que causou o dano OU na forma (vale para todos os efeitos). */
    public static boolean augmented(SpellContext ctx, com.renanpontes.isekaimahou.glyph.SpellStats stats, ResourceLocation id) {
        return stats.custom(id) > 0 || ctx.formStats().custom(id) > 0;
    }

    public static void clear() {
        RAN.clear();
        WINDOW.clear();
    }

    private DuelUtil() {}
}
