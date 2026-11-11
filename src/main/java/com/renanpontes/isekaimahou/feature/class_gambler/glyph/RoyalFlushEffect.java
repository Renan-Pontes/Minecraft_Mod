package com.renanpontes.isekaimahou.feature.class_gambler.glyph;

import java.util.Locale;

import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.glyph.EffectGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * Mão Real: puxa uma mão de pôquer do baralho mágico para cada alvo. O dano (8 base) é multiplicado pela mão:
 * carta alta ×0,6 · par ×0,9 · dois pares ×1,2 · trinca ×1,5 · sequência ×1,9 · flush ×2,2 · full house ×2,6 ·
 * quadra ×3,2 · straight flush ×4 · ROYAL FLUSH ×6. A Sorte dá cartas extras (até 8) e fica a melhor mão de 5.
 * Leque de cartas vermelhas/pretas sobre o alvo; mãos boas avisam o conjurador.
 */
public class RoyalFlushEffect extends EffectGlyph {
    public static final float BASE = 8F;

    public enum Hand {
        HIGH_CARD(0.6F), PAIR(0.9F), TWO_PAIR(1.2F), THREE(1.5F), STRAIGHT(1.9F), FLUSH(2.2F), FULL_HOUSE(2.6F),
        FOUR(3.2F), STRAIGHT_FLUSH(4.0F), ROYAL_FLUSH(6.0F);

        public final float mult;

        Hand(float mult) {
            this.mult = mult;
        }
    }

    public RoyalFlushEffect() {
        super("royal_flush", 16);
        origin(MagicPath.GAMBLER);
        tier(4);
        minCircle(4);
    }

    @Override
    public float baseDamage() {
        // valor esperado aproximado com a Sorte de um Apostador iniciante (6 cartas)
        return BASE * 0.95F;
    }

    @Override
    public void onEntity(SpellContext ctx, Entity target, SpellStats stats) {
        int luck = ctx.caster() != null ? Math.max(0, LuckService.luck(ctx.caster())) : 0;
        Hand hand = draw(ctx.random(), 5 + Math.min(3, luck));
        ctx.damage(target, BASE * hand.mult, stats, null);
        Vec3 top = target.position().add(0, target.getBbHeight() + 0.5, 0);
        for (int i = 0; i < 5; i++) {
            double a = Math.PI * (0.15 + 0.175 * i);
            ctx.level().sendParticles(GbFx.dust(i % 2 == 0 ? 0xD8303A : 0x26222C, 1.1F), top.x + Math.cos(a) * 0.6, top.y + Math.sin(a) * 0.4,
                    top.z, 2, 0.02, 0.02, 0.02, 0);
        }
        if (hand.ordinal() >= Hand.STRAIGHT.ordinal()) {
            ctx.level().sendParticles(ParticleTypes.FIREWORK, top.x, top.y, top.z, 10 + hand.ordinal() * 2, 0.3, 0.3, 0.3, 0.08);
            GbFx.sound(ctx.level(), top, SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F, 0.8F + hand.ordinal() * 0.08F);
        } else {
            GbFx.sound(ctx.level(), top, SoundEvents.BOOK_PAGE_TURN, 0.6F, 1.5F);
        }
        ServerPlayer sp = ctx.casterPlayer();
        if (sp != null && hand.ordinal() >= Hand.THREE.ordinal()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gambler.hand",
                            Component.translatable(String.join("", "poker.isekai_mahou.", hand.name().toLowerCase(Locale.ROOT))),
                            String.format(Locale.ROOT, "%.1f", hand.mult))
                    .withStyle(hand.ordinal() >= Hand.STRAIGHT_FLUSH.ordinal() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD), true);
        }
    }

    /** Puxa {@code n} cartas (5..8) de um baralho embaralhado e devolve a melhor mão de 5. */
    public static Hand draw(RandomSource rnd, int n) {
        int[] deck = new int[52];
        for (int i = 0; i < 52; i++) deck[i] = i;
        int count = Math.max(5, Math.min(8, n));
        for (int i = 0; i < count; i++) {
            int j = i + rnd.nextInt(52 - i);
            int t = deck[i];
            deck[i] = deck[j];
            deck[j] = t;
        }
        Hand best = Hand.HIGH_CARD;
        int[] pick = new int[5];
        // todas as combinações de 5 entre as cartas puxadas (no máx. C(8,5) = 56)
        for (int a = 0; a < count; a++)
            for (int b = a + 1; b < count; b++)
                for (int c = b + 1; c < count; c++)
                    for (int d = c + 1; d < count; d++)
                        for (int e = d + 1; e < count; e++) {
                            pick[0] = deck[a];
                            pick[1] = deck[b];
                            pick[2] = deck[c];
                            pick[3] = deck[d];
                            pick[4] = deck[e];
                            Hand h = evaluate(pick);
                            if (h.ordinal() > best.ordinal()) best = h;
                        }
        return best;
    }

    /** Mão de 5 cartas (carta = naipe × 13 + valor; valor 0 = 2 ... 12 = Ás). */
    public static Hand evaluate(int[] cards) {
        int[] count = new int[13];
        int suit0 = cards[0] / 13;
        boolean flush = true;
        int mask = 0;
        for (int c : cards) {
            count[c % 13]++;
            mask |= 1 << (c % 13);
            if (c / 13 != suit0) flush = false;
        }
        boolean straight = false;
        int high = -1;
        for (int top = 12; top >= 4; top--) {
            int need = 0b11111 << (top - 4);
            if ((mask & need) == need) {
                straight = true;
                high = top;
                break;
            }
        }
        // roda: A-2-3-4-5
        if (!straight && (mask & 0b1000000001111) == 0b1000000001111) {
            straight = true;
            high = 3;
        }
        if (straight && flush) return high == 12 ? Hand.ROYAL_FLUSH : Hand.STRAIGHT_FLUSH;
        int pairs = 0, threes = 0, fours = 0;
        for (int k : count) {
            if (k == 4) fours++;
            else if (k == 3) threes++;
            else if (k == 2) pairs++;
        }
        if (fours > 0) return Hand.FOUR;
        if (threes > 0 && pairs > 0) return Hand.FULL_HOUSE;
        if (flush) return Hand.FLUSH;
        if (straight) return Hand.STRAIGHT;
        if (threes > 0) return Hand.THREE;
        if (pairs >= 2) return Hand.TWO_PAIR;
        if (pairs == 1) return Hand.PAIR;
        return Hand.HIGH_CARD;
    }
}
