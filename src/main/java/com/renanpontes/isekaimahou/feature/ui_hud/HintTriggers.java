package com.renanpontes.isekaimahou.feature.ui_hud;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.MahouCommands;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.OnboardingEvent;
import com.renanpontes.isekaimahou.core.event.PlaceEvent;

import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Dicas de primeira vez dos SISTEMAS PRINCIPAIS (núcleo): cada uma dispara uma vez, na hora em que o assunto aparece
 * de verdade para o jogador. Os outros módulos chamam {@link Hints#show} para as deles. Também aplica no servidor a
 * opção "Tutorial do Espírito Guia" (pula o tutorial de quem desligou) e os comandos de teste {@code /mahou hints}.
 * <p>
 * Nada aqui aparece nos primeiros minutos de jogo: o começo é do tutorial do Espírito Guia (uma coisa de cada vez).
 */
final class HintTriggers {
    /** Dicas deste arquivo (o comando de teste {@code /mahou hints all} mostra todas). */
    static final List<String> BUILT_IN = List.of("level_up", "circle", "element", "glyph", "first_kill", "safe_zone",
            "dimension", "low_mana", "level_cap", "ultimate_ready", "exhaustion", "boss_telegraph", "settings");

    /** Tempo de jogo (ticks) antes da primeira dica: o tutorial fala primeiro. */
    private static final int QUIET_START_TICKS = 20 * 60 * 4;
    /** Tempo de jogo (ticks) para lembrar onde ficam as opções. */
    private static final int SETTINGS_AFTER_TICKS = 20 * 60 * 12;
    private static final int CHECK_EVERY = 40;

    private HintTriggers() {}

    static void init() {
        IEventBus bus = NeoForge.EVENT_BUS;
        bus.addListener(MagicProgressEvent.LevelUp.class, e -> {
            if (settled(e.getPlayer())) Hints.show(e.getPlayer(), "level_up", menuKey());
        });
        bus.addListener(MagicProgressEvent.CircleChanged.class, e -> {
            if (e.newCircle > e.oldCircle && settled(e.getPlayer())) Hints.show(e.getPlayer(), "circle");
        });
        bus.addListener(MagicProgressEvent.ElementAwakened.class, e -> {
            if (settled(e.getPlayer())) Hints.show(e.getPlayer(), "element", menuKey());
        });
        bus.addListener(MagicProgressEvent.GlyphLearned.class, e -> {
            if (settled(e.getPlayer())) Hints.show(e.getPlayer(), "glyph", menuKey());
        });
        bus.addListener(MagicProgressEvent.Counter.class, e -> {
            if ("kill.total".equals(e.key) && settled(e.getPlayer())) Hints.show(e.getPlayer(), "first_kill", menuKey());
        });
        bus.addListener(PlaceEvent.Enter.class, e -> {
            if (e.getPlace().safeZone() && settled(e.getPlayer())) {
                Hints.show(e.getPlayer(), "safe_zone", Component.keybind("key.isekai_mahou.peace_mode"));
            }
        });
        bus.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp && IsekaiMahou.MODID.equals(e.getTo().location().getNamespace())) {
                Hints.show(sp, "dimension", menuKey());
            }
        });
        // Golpe de chefe que realmente acertou: os chefes do mod marcam no chão onde o golpe cai
        bus.addListener(EventPriority.LOWEST, false, LivingIncomingDamageEvent.class, e -> {
            if (e.getEntity() instanceof ServerPlayer sp && e.getAmount() > 0 && e.getSource().getEntity() != null
                    && e.getSource().getEntity().getType().is(Tags.EntityTypes.BOSSES)) {
                Hints.show(sp, "boss_telegraph");
            }
        });
        bus.addListener(PlayerTickEvent.Post.class, HintTriggers::onTick);
        // Opção "Tutorial do Espírito Guia" desligada: pula ANTES de o Diário começar a ensinar (ele vê o tutorial
        // como concluído e segue direto para a história)
        bus.addListener(EventPriority.HIGHEST, false, OnboardingEvent.ClassCongratsDone.class, e -> skipTutorialIfUnwanted(e.getPlayer()));
        bus.addListener(EventPriority.HIGHEST, false, OnboardingEvent.TutorialResume.class, e -> skipTutorialIfUnwanted(e.getPlayer()));
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            Hints.forget(e.getEntity().getUUID());
            MahouConfig.clearPlayerPrefs(e.getEntity().getUUID());
        });
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("hints")
                .then(Commands.literal("reset").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    Hints.reset(sp);
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.ui_hud.hints_reset"), false);
                    return 1;
                }))
                .then(Commands.literal("all").executes(c -> {
                    ServerPlayer sp = c.getSource().getPlayerOrException();
                    for (String id : BUILT_IN) Hints.force(sp, id, sampleArgs(sp, id));
                    c.getSource().sendSuccess(() -> Component.translatable("command.isekai_mahou.ui_hud.hints_all", BUILT_IN.size()), false);
                    return BUILT_IN.size();
                }))
                .then(Commands.literal("show").then(Commands.argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(BUILT_IN, b))
                        .executes(c -> {
                            ServerPlayer sp = c.getSource().getPlayerOrException();
                            String id = StringArgumentType.getString(c, "id");
                            Hints.force(sp, id, sampleArgs(sp, id));
                            return 1;
                        })))));
    }

    /** Checagens baratas a cada 2 s por jogador (só leitura de dados que já estão na memória). */
    private static void onTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % CHECK_EVERY != 0 || sp.isSpectator()) return;
        int played = sp.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME));
        if (played < QUIET_START_TICKS) return;
        MagicData d = MagicApi.get(sp);
        if (d == null || d.path() == MagicPath.NONE) return;
        int max = d.maxMana();
        if (max > 0 && d.mana() < max * 0.2F && !sp.isCreative()) Hints.show(sp, "low_mana", MagicApi.resourceName(sp));
        if (d.atLevelCap() && d.circle() < Progression.MAX_CIRCLE) Hints.show(sp, "level_cap");
        if (Progression.ultimatesAllowed(d.circle()) && d.charge() >= Progression.MAX_CHARGE) {
            Hints.show(sp, "ultimate_ready", Component.keybind("key.isekai_mahou.ultimate"));
        }
        if (sp.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) Hints.show(sp, "exhaustion");
        if (played >= SETTINGS_AFTER_TICKS) Hints.show(sp, "settings");
    }

    private static void skipTutorialIfUnwanted(ServerPlayer sp) {
        if (!MahouConfig.wantsTutorial(sp) && !Onboarding.tutorialDone(sp)) Onboarding.skipTutorial(sp);
    }

    /** Passou o começo (tutorial)? As dicas esperam alguns minutos de jogo. */
    private static boolean settled(ServerPlayer sp) {
        return sp.getStats().getValue(Stats.CUSTOM.get(Stats.PLAY_TIME)) >= QUIET_START_TICKS;
    }

    private static Component menuKey() {
        return Component.keybind("key.isekai_mahou.menu");
    }

    /** Argumentos de exemplo para os comandos de teste (os mesmos que o gatilho real passaria). */
    private static Object[] sampleArgs(ServerPlayer sp, String id) {
        return switch (id) {
            case "level_up", "element", "glyph", "first_kill", "dimension" -> new Object[] {menuKey()};
            case "safe_zone" -> new Object[] {Component.keybind("key.isekai_mahou.peace_mode")};
            case "low_mana" -> new Object[] {MagicApi.resourceName(sp)};
            case "ultimate_ready" -> new Object[] {Component.keybind("key.isekai_mahou.ultimate")};
            default -> new Object[0];
        };
    }
}
