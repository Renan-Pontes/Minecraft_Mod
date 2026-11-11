package com.renanpontes.isekaimahou.feature.journal;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Onboarding;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.OnboardingEvent;
import com.renanpontes.isekaimahou.feature.journal.bestiary.Bestiary;
import com.renanpontes.isekaimahou.feature.journal.data.JournalBook;
import com.renanpontes.isekaimahou.feature.journal.data.JournalCondition;
import com.renanpontes.isekaimahou.feature.journal.data.JournalLoader;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Ouvintes de jogo do Diário (servidor): recarga dos capítulos, avaliação, sincronização, fila do "Localizar",
 * contadores de magia ({@link JournalSpellCounters}), blocos colocados, registros do Bestiário e a entrada no jogo
 * (eventos {@code OnboardingEvent}/{@code DialogueEvent} do núcleo → capítulo 1 + tutorial do Espírito Guia).
 */
final class JournalEvents {
    private static final String KILL_PREFIX = "kill." + IsekaiMahou.MODID + ".";
    private static final String BOSS_PREFIX = "boss.";

    static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener((AddReloadListenerEvent e) -> e.addListener(new JournalLoader()));
        // Dispara no login (um jogador) e no /reload (todos): manda o livro para a aba do cliente.
        bus.addListener((OnDatapackSyncEvent e) -> e.getRelevantPlayers().forEach(p -> {
            JournalNetwork.sendBook(p);
            JournalService.markDirty(p);
        }));
        bus.addListener((PlayerTickEvent.Post e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) JournalService.tick(sp);
        });
        bus.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) JournalService.forget(sp);
        });
        bus.addListener((PlayerEvent.PlayerChangedDimensionEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) JournalService.markDirty(sp);
        });
        bus.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) JournalService.markDirty(sp);
        });
        bus.addListener((ServerStoppedEvent e) -> JournalService.clearAll());
        // Fila de buscas de estrutura do "Localizar": no máximo uma por tick do servidor.
        bus.addListener((ServerTickEvent.Post e) -> JournalLocator.tick(e.getServer()));
        bus.addListener(JournalSpellCounters::onCast);
        bus.addListener(EventPriority.LOWEST, JournalEvents::onPlace);

        // Qualquer progresso mágico pode completar um passo: avalia no próximo tick (uma vez só).
        bus.addListener((MagicProgressEvent.LevelUp e) -> JournalService.markDirty(e.getPlayer()));
        bus.addListener((MagicProgressEvent.CircleChanged e) -> JournalService.markDirty(e.getPlayer()));
        bus.addListener((MagicProgressEvent.ElementAwakened e) -> JournalService.markDirty(e.getPlayer()));
        bus.addListener((MagicProgressEvent.ElementXp e) -> JournalService.markDirty(e.getPlayer()));
        bus.addListener((MagicProgressEvent.GlyphLearned e) -> JournalService.markDirty(e.getPlayer()));
        bus.addListener((MagicProgressEvent.PathChosen e) -> JournalService.markDirty(e.getPlayer()));
        bus.addListener((MagicProgressEvent.Counter e) -> {
            JournalService.markDirty(e.getPlayer());
            if (e.value == 1) bestiaryCounter(e.getPlayer(), e.key);
        });
        bus.addListener(JournalEvents::onInteract);

        // Entrada no jogo (v0.9): o capítulo 1 começa depois do "Parabéns!" da classe; o Espírito Guia roda o tutorial
        // pela caixa de diálogo e, no fim (ou ao pular), conclui o tutorial do núcleo.
        bus.addListener((OnboardingEvent.ClassCongratsDone e) -> JournalService.onCongratsDone(e.getPlayer()));
        bus.addListener((OnboardingEvent.TutorialResume e) -> JournalService.onTutorialResume(e.getPlayer()));
        bus.addListener((OnboardingEvent.TutorialDone e) -> JournalService.onTutorialDone(e.getPlayer()));
        bus.addListener(JournalTutorial::onFinished);
    }

    /** Primeiro abate/derrota de uma criatura do mod: "Nova entrada no Bestiário". */
    private static void bestiaryCounter(ServerPlayer player, String key) {
        String path;
        if (key.startsWith(KILL_PREFIX)) path = key.substring(KILL_PREFIX.length());
        else if (key.startsWith(BOSS_PREFIX)) path = key.substring(BOSS_PREFIX.length());
        else if (key.startsWith("bestiary." + IsekaiMahou.MODID + ".")) path = key.substring(("bestiary." + IsekaiMahou.MODID + ".").length());
        else return;
        if (!ResourceLocation.isValidPath(path)) return;
        ResourceLocation id = IsekaiMahou.id(path);
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(id).orElse(null);
        if (type == null || !Bestiary.isCandidate(type) || Bestiary.category(type).alwaysKnown()) return;
        MagicData d = MagicApi.get(player);
        // Só na PRIMEIRA forma de registro (abate, chefe e encontro somam 1 neste momento).
        int total = d.counter(Bestiary.killKey(id)) + d.counter(Bestiary.bossKey(id)) + d.counter(Bestiary.encounterKey(id));
        if (total != 1) return;
        // Entrando no jogo: sem chat (o registro fica no Bestiário do mesmo jeito).
        if (Onboarding.quiet(player)) return;
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.journal.bestiary_new",
                type.getDescription().copy().withStyle(s -> s.withColor(Bestiary.category(type).color)))
                .withStyle(ChatFormatting.GRAY));
        player.playNotifySound(SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 1.4F);
    }

    /** Blocos citados em condições "placed" (ex.: Pedestal Rúnico): conta quem os coloca. */
    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.isCanceled() || !(e.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(e.getPlacedBlock().getBlock());
        if (JournalBook.server().placedBlocks().contains(id)) MagicApi.addCounter(sp, JournalCondition.placedKey(id), 1);
    }

    /** Clicar numa criatura pacífica do mod a registra no Bestiário (montarias, fadas, espíritos...). */
    private static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer player) || e.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(e.getTarget() instanceof LivingEntity target) || target instanceof Enemy) return;
        EntityType<?> type = target.getType();
        if (!Bestiary.isCandidate(type) || Bestiary.category(type).alwaysKnown()) return;
        ResourceLocation id = EntityType.getKey(type);
        if (Bestiary.recorded(MagicApi.get(player), id)) return;
        MagicApi.addCounter(player, Bestiary.encounterKey(id), 1);
    }

    private JournalEvents() {}
}
