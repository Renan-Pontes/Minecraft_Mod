package com.renanpontes.isekaimahou.feature.stands;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_steelball.SbProgress;
import com.renanpontes.isekaimahou.glyph.MasteryMutations;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Quando um STAND aparece. Regra do dono: <b>só</b> para quem escolheu a classe Steel Ball
 * ({@link MagicPath#STEELBALL}). Para todas as outras classes os mesmos glifos funcionam igualzinho, mas nenhum
 * espírito aparece.
 * <p>
 * Ao conjurar (jogador pela tecla), o primeiro glifo de JoJo da receita, por prioridade, escolhe o stand e a ação.
 * Glifos de JoJo de outros módulos (ex.: D4C / Gold Experience Requiem do legends_k) podem se registrar com
 * {@link #register}; ids desconhecidos com "d4c"/"dirty_deeds" ou "requiem"/"gold_experience"/"ponto_zero" no nome
 * também são reconhecidos.
 */
public final class StandTriggers {
    /** Um gatilho: stand, ação, duração (ticks) e prioridade (maior vence quando a receita tem vários). */
    public record Trigger(Stand stand, Stand.Action action, int ticks, int priority) {}

    private static final Map<ResourceLocation, Trigger> MAP = new HashMap<>();
    /** Um stand por jogador a cada poucos ticks (uma rajada de Canalizar não manda 20 pacotes por segundo). */
    private static final Map<UUID, Long> NEXT = new HashMap<>();
    private static final int MIN_GAP = 8;

    static {
        // JoJo comuns (legends_h)
        register(id("ora_rush"), Stand.STAR_PLATINUM, Stand.Action.BARRAGE, 22, 50);
        register(id("king_crimson"), Stand.KING_CRIMSON, Stand.Action.ERASE, 110, 60);
        register(id("epitaph"), Stand.KING_CRIMSON, Stand.Action.GUARD, 30, 10);
        register(id("killer_queen"), Stand.KILLER_QUEEN, Stand.Action.SWITCH, 26, 40);
        register(id("bites_the_dust"), Stand.KILLER_QUEEN, Stand.Action.SWITCH, 40, 55);
        register(id("sheer_heart_attack"), Stand.KILLER_QUEEN, Stand.Action.POINT, 24, 30);
        // Steel Ball Run (class_steelball): Atos do Tusk, a unha giratória (pelo Ato), Ball Breaker e Love Train (D4C)
        register(id("tusk_act1"), Stand.TUSK_ACT1, Stand.Action.POINT, 24, 45);
        register(id("tusk_act2"), Stand.TUSK_ACT2, Stand.Action.POINT, 24, 45);
        register(id("tusk_act3"), Stand.TUSK_ACT3, Stand.Action.POINT, 30, 45);
        register(id("infinite_rotation"), Stand.TUSK_ACT4, Stand.Action.SLAM, 34, 48);
        register(id("spin_nail"), Stand.TUSK_ACT1, Stand.Action.POINT, 22, 20);
        register(id("ball_breaker"), Stand.BALL_BREAKER, Stand.Action.SLAM, 50, 46);
        // "7 páginas" = a Surra de 7 Páginas do Tusk ACT4 (o dono confirmou 13/11)
        register(id("seven_page_beatdown"), Stand.TUSK_ACT4, Stand.Action.BARRAGE, 64, 58);
        register(id("steelball_love_train"), Stand.D4C, Stand.Action.SPREAD, 240, 70);
        // legends_k (outro agente): D4C "Entre Dimensões" e Gold Experience Requiem "Ponto Zero"
        register(id("lk_d4c"), Stand.D4C, Stand.Action.SPREAD, 60, 52);
        register(id("lk_return_to_zero"), Stand.GOLD_EXPERIENCE_REQUIEM, Stand.Action.SLAM, 50, 65);
    }

    private static ResourceLocation id(String path) {
        return IsekaiMahou.id(path);
    }

    /** API: outro módulo declara que o glifo {@code glyph} manifesta um stand (só para Steel Ball). */
    public static void register(ResourceLocation glyph, Stand stand, Stand.Action action, int ticks, int priority) {
        MAP.put(glyph, new Trigger(stand, action, ticks, priority));
    }

    /** Gatilho do glifo (registrado ou reconhecido pelo nome); null = não é de JoJo. */
    @Nullable
    public static Trigger of(ResourceLocation glyph) {
        Trigger t = MAP.get(glyph);
        if (t != null) return t;
        String p = glyph.getPath().toLowerCase(Locale.ROOT);
        if (p.contains("d4c") || p.contains("dirty_deeds")) return new Trigger(Stand.D4C, Stand.Action.SPREAD, 60, 52);
        if (p.contains("requiem") || p.contains("gold_experience") || p.contains("ponto_zero") || p.contains("zero_point") || p.contains("return_to_zero")) {
            return new Trigger(Stand.GOLD_EXPERIENCE_REQUIEM, Stand.Action.SLAM, 50, 65);
        }
        return null;
    }

    /** A regra do dono: stands só para a classe Steel Ball (jogador de verdade). */
    public static boolean canManifest(@Nullable Player p) {
        return p != null && !(p instanceof FakePlayer) && !p.isSpectator() && MagicApi.get(p).path() == MagicPath.STEELBALL;
    }

    static void onCast(SpellCastEvent.Post e) {
        SpellContext ctx = e.getContext();
        if (ctx == null || !ctx.isPlayerCast() || !(e.getCaster() instanceof ServerPlayer sp) || !canManifest(sp)) return;
        Trigger best = null;
        ResourceLocation bestId = null;
        for (ResourceLocation g : ctx.recipe().glyphs()) {
            Trigger t = of(g);
            if (t != null && (best == null || t.priority() > best.priority())) {
                best = t;
                bestId = g;
            }
        }
        if (best == null) return;
        long now = sp.serverLevel().getGameTime();
        Long next = NEXT.get(sp.getUUID());
        if (next != null && now < next && next - now <= MIN_GAP) return;
        NEXT.put(sp.getUUID(), now + MIN_GAP);
        show(sp, bestId, best);
    }

    /** Ajustes por glifo (Ato da unha, estágio de evolução) e envio. */
    private static void show(ServerPlayer sp, ResourceLocation glyph, Trigger t) {
        Stand stand = t.stand();
        int ticks = t.ticks();
        if (glyph.getPath().equals("spin_nail")) {
            // a Unha Giratória mostra o Tusk do Ato em que você está
            stand = switch (SbProgress.act(MagicApi.get(sp))) {
                case 4 -> Stand.TUSK_ACT4;
                case 3 -> Stand.TUSK_ACT3;
                case 2 -> Stand.TUSK_ACT2;
                default -> Stand.TUSK_ACT1;
            };
        }
        // evolução própria: a rajada do ORA fica mais longa nos estágios
        if (glyph.getPath().equals("ora_rush")) ticks += 8 * MasteryMutations.stageOf(sp, glyph);
        if (stand == Stand.KING_CRIMSON) StandNet.showSelf(sp, stand, t.action(), ticks);
        else StandNet.showAll(sp, stand, t.action(), ticks);
    }

    static void forget(UUID id) {
        NEXT.remove(id);
    }

    static void clear() {
        NEXT.clear();
    }

    private StandTriggers() {}
}
