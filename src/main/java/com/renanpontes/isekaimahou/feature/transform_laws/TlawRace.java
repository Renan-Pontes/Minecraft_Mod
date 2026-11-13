package com.renanpontes.isekaimahou.feature.transform_laws;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicHover;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.character.CharacterContent;
import com.renanpontes.isekaimahou.feature.character.CharacterData;
import com.renanpontes.isekaimahou.feature.character.CharacterNetwork;
import com.renanpontes.isekaimahou.feature.character.Race;
import com.renanpontes.isekaimahou.feature.character.RaceForms;
import com.renanpontes.isekaimahou.feature.character.RaceFx;
import com.renanpontes.isekaimahou.glyph.TransformLawGlyph;
import com.renanpontes.isekaimahou.glyph.TransformTargets;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * Leis ESCOLHIDAS na aba Votos (sem receita de magia) para as transformações que não são magias:
 * <ul>
 *   <li>{@link #RACE}: a <b>Transformação Racial</b> (tecla V, módulo character). A sessão começa sozinha quando a
 *       forma entra — sem mexer no código das raças: o tick só olha {@link RaceForms#formOf} a cada 5 ticks;</li>
 *   <li>{@link #HOLLOW}: a <b>Resurrección</b> do Hollow (só para quem é Hollow), ligada pelos ouvintes
 *       {@code HollowRelease.ON_START/ON_END} (ver {@link TlawHollow}).</li>
 * </ul>
 * Até {@link TransformTargets#maxLaws} leis conhecidas por transformação, guardadas como marcas
 * {@code tlaw.race.<lei>} / {@code tlaw.hollow.<lei>} nos dados (que já sincronizam com o cliente). Trocar as escolhas
 * no meio da forma não vale até a próxima transformação.
 */
final class TlawRace {
    public static final int RACE = 0, HOLLOW = 1;
    static final String PICK_FLAG = "tlaw.race.";
    static final String HOLLOW_FLAG = "tlaw.hollow.";
    /** Jogadores cuja forma atual já foi vista (com ou sem leis): não começa sessão no meio da forma. */
    private static final Set<UUID> SEEN = ConcurrentHashMap.newKeySet();

    static String prefix(int slot) {
        return slot == HOLLOW ? HOLLOW_FLAG : PICK_FLAG;
    }

    static void tick(ServerPlayer sp) {
        Race form = RaceForms.formOf(sp);
        if (form == null) {
            SEEN.remove(sp.getUUID());
            return;
        }
        if (!SEEN.add(sp.getUUID())) return;
        if (TlawSessions.hasSource(sp, TlawSessions.RACE_SOURCE)) return;
        // Release 1.0: sem votos soltos a chamada ainda vale — a receita escolhida para a forma racial (aba Votos) entra
        // por cima da base da raça. Sem votos e sem receita, begin não faz nada (igual a antes).
        List<TransformLawGlyph> laws = usablePicks(sp, RACE);
        TlawSessions.INSTANCE.begin(sp, TlawSessions.RACE_SOURCE, laws, RaceForms.DURATION, CharacterContent.form(form));
        // Último Suspiro (solto ou da receita) esticou a forma: o visual do cliente e a liberação de voo acompanham
        MobEffectInstance inst = sp.getEffect(CharacterContent.form(form));
        // Onda 5: a receita também estica (Estender no corpo) — vale o tempo que o efeito tem de fato
        if (inst != null && inst.getDuration() > RaceForms.DURATION + 20) {
            RaceFx.entityFx(sp, CharacterNetwork.FX_FORM, inst.getDuration());
            MagicHover.allow(sp, inst.getDuration() + 100);
        }
        // Onda 5: a receita mexe na recarga da tecla V (Acelerar/Retardar no corpo) e a duração nova empurra a recarga
        // junto — a recarga conta a partir do fim da forma. Não de novo quando a sessão só voltou do relog.
        if (ForgeSessions.fresh(sp, TlawSessions.RACE_SOURCE)) {
            float dm = TransformForge.activeDurationMult(sp, TransformForge.RACE_SLOT);
            float cm = TransformForge.activeCooldownMult(sp, TransformForge.RACE_SLOT);
            long delta = Math.round(RaceForms.DURATION * (dm - 1F) + RaceForms.COOLDOWN * (cm - 1F));
            if (delta != 0) {
                CharacterData cd = CharacterContent.data(sp);
                cd.setFormReadyAt(cd.formReadyAt() + delta);
            }
        }
    }

    static void forget(UUID id) {
        SEEN.remove(id);
    }

    static void clearAll() {
        SEEN.clear();
    }

    /** Escolhas marcadas para a forma racial (na ordem de exibição), conhecidas ou não. */
    static List<TransformLawGlyph> picks(MagicData d) {
        return picks(d, RACE);
    }

    static List<TransformLawGlyph> picks(MagicData d, int slot) {
        String pre = prefix(slot);
        List<TransformLawGlyph> out = new ArrayList<>();
        for (TransformLawGlyph l : TlawGlyphs.all()) if (d.hasFlag(pre + l.id().getPath())) out.add(l);
        return out;
    }

    /** As escolhas que valem agora: conhecidas, liberadas pelo Círculo/nível e dentro do limite. */
    static List<TransformLawGlyph> usablePicks(ServerPlayer sp, int slot) {
        MagicData d = MagicApi.get(sp);
        int max = TransformTargets.maxLaws(d.level(), d.circle());
        List<TransformLawGlyph> out = new ArrayList<>();
        for (TransformLawGlyph l : picks(d, slot)) {
            if (out.size() >= max) break;
            if (d.knowsGlyph(l.id()) && l.usableAt(d.level(), d.circle())) out.add(l);
        }
        return out;
    }

    /** Pedido da aba Votos (cliente → servidor). */
    static void setPick(ServerPlayer sp, int slot, String path, boolean on) {
        TransformLawGlyph law = TlawGlyphs.get(path);
        if (law == null || (slot != RACE && slot != HOLLOW)) return;
        MagicData d = MagicApi.get(sp);
        String flag = prefix(slot) + path;
        if (on == d.hasFlag(flag)) return;
        if (slot == HOLLOW && d.path() != MagicPath.HOLLOW) return;
        if (TlawSessions.hasSource(sp, slot == HOLLOW ? TlawSessions.HOLLOW_SOURCE : TlawSessions.RACE_SOURCE)) {
            fail(sp, Component.translatable("message.isekai_mahou.tlaw.pick_transformed"));
            return;
        }
        if (on) {
            if (!d.knowsGlyph(law.id())) {
                fail(sp, Component.translatable("message.isekai_mahou.tlaw.pick_unknown", law.displayName()));
                return;
            }
            if (!law.usableAt(d.level(), d.circle())) {
                fail(sp, Component.translatable("message.isekai_mahou.tlaw.pick_locked", law.displayName(), law.minCircle(), law.minLevel()));
                return;
            }
            int max = TransformTargets.maxLaws(d.level(), d.circle());
            if (picks(d, slot).size() >= max) {
                fail(sp, Component.translatable("message.isekai_mahou.tlaw.pick_full", max));
                return;
            }
        }
        d.setFlag(flag, on);
        TlawFx.sound(sp, on ? SoundEvents.CHAIN_PLACE : SoundEvents.CHAIN_BREAK, 0.7F, on ? 1.2F : 1.0F);
        String key = slot == HOLLOW ? (on ? "message.isekai_mahou.tlaw.pick_on_hollow" : "message.isekai_mahou.tlaw.pick_off_hollow")
                : (on ? "message.isekai_mahou.tlaw.pick_on" : "message.isekai_mahou.tlaw.pick_off");
        sp.displayClientMessage(Component.translatable(key, law.displayName().withColor(law.rgbColor())), true);
    }

    private static void fail(ServerPlayer sp, Component msg) {
        sp.displayClientMessage(msg.copy().withColor(0xFF7070), true);
        TlawFx.sound(sp, SoundEvents.VILLAGER_NO, 0.6F, 1.0F);
    }

    private TlawRace() {}
}
