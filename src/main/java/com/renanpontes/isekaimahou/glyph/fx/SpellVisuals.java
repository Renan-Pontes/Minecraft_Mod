package com.renanpontes.isekaimahou.glyph.fx;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Visual das conjurações (servidor → partículas fx): círculo mágico aos pés/na frente da mão enquanto conjura
 * ({@link #channelCircle}, chamado por {@link ElementFx#channeling}) e o "disparo" do círculo quando a magia sai
 * ({@link #onCast}, ouvinte de {@link SpellCastEvent.Post}). Só visual: não muda dano, custo nem acerto.
 * <p>
 * Tier visual ({@link #tier}): 0 = magia fraca (sutil) .. 4 = Círculo alto, 5 = ultimate (círculos em camadas,
 * círculo no céu, pilar de luz).
 */
public final class SpellVisuals {
    /** Raio do círculo aos pés por tier. */
    private static final float[] RADIUS = {0.9F, 1.2F, 1.6F, 2.1F, 2.7F, 3.6F};
    /** Intervalo mínimo (ticks) entre dois "mantém vivo" do círculo de conjuração de uma mesma entidade. */
    private static final int KEEPALIVE_EVERY = 5;
    private static final int KEEPALIVE_LIFE = 11;

    private static final Map<Integer, Long> LAST_SENT = new HashMap<>();
    private static final Map<UUID, Cached> CACHE = new HashMap<>();

    private record Cached(SpellRecipe recipe, int tier, int rgb, boolean directional) {}

    // =====================================================================================
    //  Tier
    // =====================================================================================

    /** Tier visual de uma magia (0..4; ultimate = 5). {@code power} = multiplicador de poder (chefes/cânticos). */
    public static int tier(@Nullable ParsedSpell spell, float power) {
        if (spell == null) return 1;
        if (spell.isUltimate()) return 5;
        int g = spell.form() != null ? spell.form().tier() : 1;
        for (ParsedSpell.EffectEntry ef : spell.effects()) g = Math.max(g, ef.effect().tier());
        int cost = spell.baseCost();
        int ct = cost < 20 ? 0 : cost < 45 ? 1 : cost < 80 ? 2 : cost < 130 ? 3 : 4;
        int t = Math.max(g - 1, ct);
        if (power >= 1.5F) t++;
        return Math.max(0, Math.min(4, t));
    }

    /** Tier visual de uma conjuração (usa a magia raiz, que guarda o custo). */
    public static int tier(@Nullable SpellContext ctx) {
        if (ctx == null) return 1;
        return tier(ctx.rootSpell(), ctx.powerMult());
    }

    public static float radius(int tier) {
        return RADIUS[Math.max(0, Math.min(5, tier))];
    }

    // =====================================================================================
    //  Conjuração em andamento
    // =====================================================================================

    /** Mantém o círculo mágico de quem está conjurando (chamado a cada poucos ticks; limita o envio sozinho). */
    public static void channelCircle(ServerLevel level, Entity caster, Element e) {
        long now = level.getGameTime();
        Long last = LAST_SENT.get(caster.getId());
        if (last != null && now >= last && now - last < KEEPALIVE_EVERY) return;
        if (LAST_SENT.size() > 1024) LAST_SENT.clear();
        LAST_SENT.put(caster.getId(), now);

        int tier = 1;
        int rgb = e.color;
        boolean directional = true;
        if (caster instanceof ServerPlayer sp && !(caster instanceof FakePlayer)) {
            Cached c = cached(sp, e);
            if (c != null) {
                tier = c.tier;
                rgb = c.rgb;
                directional = c.directional;
            }
        }
        int ka = SpellFxParticles.FLAG_KEEPALIVE;
        float r = radius(tier);
        ElementFx.castCircle(level, caster, rgb, r, KEEPALIVE_LIFE,
                SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, tier <= 0 ? 3 : 0) | ka);
        if (tier >= 3) {
            ElementFx.castCircle(level, caster, ElementFx.lighten(rgb, 0.3F), r * 0.55F, KEEPALIVE_LIFE,
                    SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, 1) | ka | SpellFxParticles.FLAG_REVERSE);
        }
        if (tier >= 2 && directional) {
            ElementFx.castCircle(level, caster, rgb, 0.45F + 0.1F * tier, KEEPALIVE_LIFE,
                    SpellFxParticles.flags(tier, SpellFxParticles.MODE_FRONT, tier >= 4 ? 0 : 3) | ka);
        }
        if (tier >= 5) {
            ElementFx.castCircle(level, caster, rgb, r * 1.4F, KEEPALIVE_LIFE,
                    SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, 2) | ka | SpellFxParticles.FLAG_REVERSE);
            ElementFx.castCircle(level, caster, rgb, r * 1.6F, KEEPALIVE_LIFE,
                    SpellFxParticles.flags(tier, SpellFxParticles.MODE_SKY, 0) | ka);
        }
    }

    /** Tier/cor da magia que o jogador está conjurando (cache por jogador: só reanalisa se a receita mudar). */
    @Nullable
    private static Cached cached(ServerPlayer sp, Element e) {
        MagicData data;
        try {
            data = MagicApi.get(sp);
        } catch (RuntimeException ex) {
            return null;
        }
        SpellRecipe recipe = data == null ? null : data.channelRecipe;
        if (recipe == null) return null;
        Cached c = CACHE.get(sp.getUUID());
        if (c != null && c.recipe.equals(recipe)) return c;
        SpellParser.Result r = SpellParser.parse(recipe, e, null);
        ParsedSpell spell = r.ok() ? r.spell() : null;
        int rgb = recipe.color() >= 0 ? recipe.color() & 0xFFFFFF : (spell != null && spell.primary() != null ? spell.primary().color : e.color);
        c = new Cached(recipe, tier(spell, 1.0F), rgb, spell == null || spell.form() == null || spell.form().stageable());
        if (CACHE.size() > 256) CACHE.clear();
        CACHE.put(sp.getUUID(), c);
        return c;
    }

    // =====================================================================================
    //  Disparo
    // =====================================================================================

    /** A magia saiu: o círculo "explode" para fora; ultimates ganham camadas, círculo no céu e pilar de luz. */
    public static void onCast(SpellCastEvent.Post event) {
        LivingEntity caster = event.getCaster();
        SpellContext ctx = event.getContext();
        if (caster == null || ctx == null || !(caster.level() instanceof ServerLevel level)) return;
        ParsedSpell spell = ctx.rootSpell();
        if (spell == null) return;
        int tier = tier(spell, ctx.powerMult());
        if (spell.formStats().silent && tier < 5) return;
        int rgb = ctx.color() & 0xFFFFFF;
        boolean directional = spell.form() != null && spell.form().stageable();
        float r = radius(tier);
        int life = 12 + 2 * tier;
        int rel = SpellFxParticles.FLAG_RELEASE;

        // Conjurado de um ponto (pedestal, torre, chefe à distância) ou por um FakePlayer: círculo solto na origem
        if (ctx.hasOrigin() || caster instanceof FakePlayer) {
            if (tier < 1) return;
            Vec3 o = ctx.eyePos();
            Vec3 d = ctx.look();
            ElementFx.sendFar(level, ElementFx.fx(SpellFxParticles.CIRCLE, rgb, 0.5F + 0.12F * tier, life, -1,
                    SpellFxParticles.flags(tier, SpellFxParticles.MODE_NORMAL, tier >= 3 ? 0 : 3) | rel),
                    o.x, o.y, o.z, 0, d.x, d.y, d.z, 1.0);
            return;
        }

        ElementFx.castCircle(level, caster, rgb, r, life, SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, tier <= 1 ? 3 : 0) | rel);
        if (directional && tier >= 1) {
            ElementFx.castCircle(level, caster, rgb, 0.45F + 0.12F * tier, life,
                    SpellFxParticles.flags(tier, SpellFxParticles.MODE_FRONT, tier >= 3 ? 0 : 3) | rel);
        }
        Vec3 chest = caster.position().add(0, caster.getBbHeight() * 0.55, 0);
        if (tier >= 3) {
            ElementFx.castCircle(level, caster, ElementFx.lighten(rgb, 0.3F), r * 0.55F, life,
                    SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, 1) | rel | SpellFxParticles.FLAG_REVERSE);
        }
        if (tier >= 4) {
            ElementFx.shockwave(level, caster.position().add(0, 0.1, 0), rgb, r * 1.8F, 14, null);
            ElementFx.flash(level, chest, rgb, 1.2F + 0.3F * tier);
        }
        if (tier >= 5) {
            // Ultimate: três camadas no chão, círculo no céu, pilar de luz e uma onda enorme
            ElementFx.castCircle(level, caster, rgb, r * 1.45F, 40, SpellFxParticles.flags(tier, SpellFxParticles.MODE_GROUND, 2) | rel);
            ElementFx.castCircle(level, caster, rgb, r * 1.7F, 50, SpellFxParticles.flags(tier, SpellFxParticles.MODE_SKY, 0) | rel);
            ElementFx.castCircle(level, caster, ElementFx.lighten(rgb, 0.35F), r, 50,
                    SpellFxParticles.flags(tier, SpellFxParticles.MODE_SKY, 1) | rel | SpellFxParticles.FLAG_REVERSE);
            ElementFx.pillar(level, caster.position(), rgb, 1.3F, 14.0F, 30);
            ElementFx.shockwave(level, caster.position().add(0, 0.15, 0), rgb, 10.0F, 22, null);
            ElementFx.runeCircle(level, caster.position().add(0, 0.04, 0), rgb, r * 2.2F, 60, 5);
            Element el = ctx.element() != null ? ctx.element() : Element.ARCANE;
            level.sendParticles(ElementFx.style(el, 1.4F), chest.x, chest.y, chest.z, 40, 1.6, 1.0, 1.6, 0.25);
        }
    }

    public static void clear() {
        LAST_SENT.clear();
        CACHE.clear();
    }

    private SpellVisuals() {}
}
