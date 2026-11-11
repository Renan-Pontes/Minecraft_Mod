package com.renanpontes.isekaimahou.feature.fusions_c.glyph;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.api.Statuses.Kind;

import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/** Purificação (Pureza): tirar condições ruins de aliados — uma de cada vez (Fonte Sagrada) ou todas (Purgar). */
public final class FcCleansing {
    /** Condições do núcleo, da pior para a mais leve (a Fonte tira a primeira que achar). */
    private static final Kind[] WORST_FIRST = {
            Kind.FROZEN, Kind.PETRIFIED, Kind.STUNNED, Kind.SILENCED, Kind.ROOTED, Kind.BURNING, Kind.SHOCKED,
            Kind.BLEEDING, Kind.CORRUPTED, Kind.CHILLED, Kind.EXPOSED, Kind.WET
    };

    /** Uma condição removida: do núcleo ({@code kind}) ou um efeito ruim do jogo ({@code vanilla}). */
    public record Removed(Kind kind, int stacks, MobEffectInstance vanilla) {
        /** "Peso" para virar dano: acúmulos (condição) ou nível (efeito). */
        public int weight() {
            return kind != null ? Math.max(1, stacks) : vanilla.getAmplifier() + 1;
        }
    }

    /** Tira a PIOR condição ruim (ou um efeito ruim do jogo). true se tirou algo. */
    public static boolean cleanseWorst(LivingEntity e) {
        for (Kind k : WORST_FIRST) {
            if (Statuses.has(e, k)) {
                Statuses.clear(e, k);
                return true;
            }
        }
        for (MobEffectInstance inst : new ArrayList<>(e.getActiveEffects())) {
            if (isVanillaHarmful(inst.getEffect())) {
                e.removeEffect(inst.getEffect());
                return true;
            }
        }
        return false;
    }

    /** Tira TODAS as condições e efeitos ruins e devolve a lista do que saiu (para transferir/converter em dano). */
    public static List<Removed> purgeAll(LivingEntity e) {
        List<Removed> out = new ArrayList<>();
        for (Kind k : WORST_FIRST) {
            int s = Statuses.stacks(e, k);
            if (s > 0) {
                Statuses.clear(e, k);
                out.add(new Removed(k, s, null));
            }
        }
        for (MobEffectInstance inst : new ArrayList<>(e.getActiveEffects())) {
            if (isVanillaHarmful(inst.getEffect())) {
                out.add(new Removed(null, 0, new MobEffectInstance(inst)));
                e.removeEffect(inst.getEffect());
            }
        }
        return out;
    }

    /**
     * Efeitos ruins que são REGRA, não condição: Exaustão Mágica (depois de um Domínio) e Procurado (ficha criminal das
     * cidades, lido por id: o módulo pode não existir). A purificação não os tira nem os transfere.
     */
    private static final ResourceLocation MAGIC_EXHAUSTION = IsekaiMahou.id("magic_exhaustion");
    private static final ResourceLocation WANTED = IsekaiMahou.id("wanted");

    /** Efeito "ruim" que não é uma condição do núcleo (essas são tratadas por tipo) nem uma regra. */
    private static boolean isVanillaHarmful(Holder<MobEffect> h) {
        return h.value().getCategory() == MobEffectCategory.HARMFUL && Kind.of(h) == null
                && !h.is(MAGIC_EXHAUSTION) && !h.is(WANTED);
    }

    private FcCleansing() {}
}
