package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_gambler.entity.IdleDeathDomainEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * Expansão de Domínio: MORTE OCIOSA (forma G, ultimate do rank V da Casa): abre um salão de pachinko de raio 11
 * (Expandir vale) por 15 s (Estender vale). Rodadas a cada 2 s: a sorte rola, os efeitos acertam todos os inimigos
 * dentro (Acerto Garantido) e o "Reach" decide a chance de JACKPOT — que vira o Jackpot Imortal (os 4:11 em ticks).
 * Aceita Leis de Domínio. Custo por pulsos moderado (1 + raiz das rodadas). Ver {@link IdleDeathDomainEntity}.
 */
public class IdleDeathGambleForm extends FormGlyph {
    public static final float BASE_RADIUS = 11F;
    public static final int BASE_DURATION = 300;

    public IdleDeathGambleForm() {
        super("idle_death_gamble", 80);
        origin(MagicPath.GAMBLER);
        tier(5);
        minCircle(8);
    }

    @Override
    public boolean isUltimate() {
        return true;
    }

    @Override
    public boolean acceptsLaws() {
        return true;
    }

    @Override
    public boolean isPersistent() {
        return true;
    }

    @Override
    public int baseCastTicks() {
        return 25;
    }

    @Override
    public int baseCooldown() {
        return 400;
    }

    @Override
    public float copies(SpellStats stats, int circle) {
        return SpellCost.pulseCopies(stats.duration(BASE_DURATION), IdleDeathDomainEntity.ROUND);
    }

    @Override
    public float hintRadius() {
        return BASE_RADIUS;
    }

    @Override
    public int hintDuration() {
        return BASE_DURATION;
    }

    @Override
    public float hintRange() {
        return -1F;
    }

    @Override
    public void cast(SpellContext ctx) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        SpellStats s = ctx.formStats();
        IdleDeathDomainEntity e = IdleDeathDomainEntity.open(ctx, caster, s.radius(BASE_RADIUS), s.duration(BASE_DURATION));
        ctx.level().addFreshEntity(e);
        if (!s.silent) {
            Announce.send(caster, Component.translatable("announce.isekai_mahou.gambler.idle_death"), 0x3AE07A, Announce.STYLE_ULTIMATE);
        }
        if (caster instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.idle_death_open"), true);
        }
    }
}
