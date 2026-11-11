package com.renanpontes.isekaimahou.feature.ui_hud.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.Tags;

/**
 * O que o cliente consegue saber de uma criatura (só com dados sincronizados): se é Chefe/Elite, cores da barra de
 * vida e as fraquezas/resistências elementais (as mesmas tabelas de {@link ElementReactions} que o servidor usa).
 */
final class MobInfo {
    enum Rank { NORMAL, ELITE, BOSS }

    /** Uma linha de afinidade: multiplicador de dano do elemento contra o alvo (0 = imune). */
    record Affinity(Element element, float mult, boolean active) {
        boolean immune() { return mult <= 0.001F; }
        boolean weak() { return mult > 1.001F; }
    }

    // Marcas que o servidor deixa e que chegam ao cliente: modificadores de atributo (sincronizados) e nomes
    private static final ResourceLocation PARTY_HEALTH = IsekaiMahou.id("party_health");
    private static final ResourceLocation TRIAL_ELITE_HEALTH = IsekaiMahou.id("trial_elite_health");
    private static final ResourceLocation TRIAL_SENTINEL = IsekaiMahou.id("trial_sentinel");
    private static final String NAME_ELITE = "entity.isekai_mahou.trial_elite";
    private static final String NAME_GUARD = "entity.isekai_mahou.trial_guard";
    private static final String NAME_CHAMPION = "entity.isekai_mahou.trial_champion";

    static final int COLOR_BOSS = 0xFFFFC94F;
    static final int COLOR_ELITE = 0xFFC58CFF;
    static final int COLOR_HOSTILE = 0xFFFF6B6B;
    static final int COLOR_PASSIVE = 0xFF7CE07C;
    static final int COLOR_PLAYER = 0xFF6FD6FF;

    private MobInfo() {}

    static Rank rank(LivingEntity e) {
        if (e instanceof Player) return Rank.NORMAL;
        String key = nameKey(e);
        AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH);
        // Sentinelas e guardas da Provação são Elites mesmo escalados para o grupo
        if (EntityType.getKey(e.getType()).equals(TRIAL_SENTINEL) || NAME_GUARD.equals(key)) return Rank.ELITE;
        if (e.getType().is(Tags.EntityTypes.BOSSES) || NAME_CHAMPION.equals(key)) return Rank.BOSS;
        if (hp != null && hp.hasModifier(PARTY_HEALTH)) return Rank.BOSS;
        if (NAME_ELITE.equals(key) || hp != null && hp.hasModifier(TRIAL_ELITE_HEALTH)) return Rank.ELITE;
        return Rank.NORMAL;
    }

    /** Chave de tradução do nome personalizado (nomes dados pela Provação), ou null. */
    private static String nameKey(LivingEntity e) {
        Component name = e.getCustomName();
        if (name != null && name.getContents() instanceof TranslatableContents tc) return tc.getKey();
        return null;
    }

    static boolean hostile(LivingEntity e) {
        return e instanceof Enemy;
    }

    /** Cor de destaque do alvo (borda do painel, selo). */
    static int accent(LivingEntity e, Rank rank) {
        if (rank == Rank.BOSS) return COLOR_BOSS;
        if (rank == Rank.ELITE) return COLOR_ELITE;
        if (e instanceof Player) return COLOR_PLAYER;
        return hostile(e) ? COLOR_HOSTILE : COLOR_PASSIVE;
    }

    /** Degradê da barra de vida (topo, base) pelo tipo do alvo. */
    static int[] barColors(LivingEntity e, Rank rank) {
        if (rank == Rank.BOSS) return new int[] {0xFFFF7A4A, 0xFFB0181E};
        if (e instanceof Player) return new int[] {0xFF8FE8FF, 0xFF2A86C8};
        if (hostile(e) || rank == Rank.ELITE) return new int[] {0xFFFF6B6B, 0xFFB01E2A};
        return new int[] {0xFF8BF07A, 0xFF2E9A3A};
    }

    /** Cor da barrinha flutuante pela fração de vida: verde → amarelo → vermelho. */
    static int healthColor(float frac) {
        if (frac > 0.5F) return HudDraw.lerp((frac - 0.5F) * 2F, 0xFFFFD84F, 0xFF5BE35B);
        return HudDraw.lerp(frac * 2F, 0xFFFF4040, 0xFFFFD84F);
    }

    /**
     * Fraquezas/resistências: os 6 elementos base e, se for uma fusão, o elemento ATIVO do jogador (o que ele vai
     * usar agora). Só entram multiplicadores diferentes de 1. Ordem: fraquezas (maior primeiro), resistências, imunes.
     */
    static List<Affinity> affinities(LivingEntity e, Element active) {
        List<Affinity> out = new ArrayList<>();
        if (e instanceof Player) return out;
        EntityType<?> type = e.getType();
        for (Element el : Element.BASE) add(out, el, ElementReactions.multiplier(type, el), el == active);
        if (active != null && active.isFusion()) add(out, active, ElementReactions.multiplier(type, active), true);
        out.sort((a, b) -> {
            int ga = group(a), gb = group(b);
            if (ga != gb) return Integer.compare(ga, gb);
            return ga == 0 ? Float.compare(b.mult(), a.mult()) : Float.compare(a.mult(), b.mult());
        });
        return out;
    }

    private static void add(List<Affinity> out, Element el, float mult, boolean active) {
        if (Math.abs(mult - 1F) < 0.01F) return;
        out.add(new Affinity(el, Math.max(0F, mult), active));
    }

    private static int group(Affinity a) {
        if (a.immune()) return 2;
        return a.weak() ? 0 : 1;
    }

    /** "×2", "×0.5", "×1.75" (o imune é escrito por tradução). */
    static String multText(float m) {
        if (Math.abs(m - Math.round(m)) < 0.01F) return "×" + Math.round(m);
        String s = String.format(Locale.ROOT, "%.2f", m);
        while (s.endsWith("0")) s = s.substring(0, s.length() - 1);
        return "×" + s;
    }

    static int affinityColor(Affinity a) {
        if (a.immune()) return 0xFFFF5B5B;
        return a.weak() ? 0xFF7CFF8A : 0xFFFFB24F;
    }
}
