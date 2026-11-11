package com.renanpontes.isekaimahou.core.social;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.social.SocialData.PartyRec;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Perfil de outro jogador (Ver perfil na roda de interação). Módulos somam linhas com {@code Party.addProfileLine}. */
public final class ProfileService {
    private static final List<Function<ServerPlayer, Component>> LINES = new CopyOnWriteArrayList<>();
    private static final int MAX_EXTRA = 12;

    public static void addLine(Function<ServerPlayer, Component> line) {
        if (line != null) LINES.add(line);
    }

    public static void show(ServerPlayer viewer, ServerPlayer target) {
        MagicData d = MagicApi.get(target);
        PartyRec party = PartyService.partyOf(target);
        String city = AllegianceService.of(target.server, target.getUUID());
        int bosses = 0;
        for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
            if (e.getKey().startsWith("boss.") && e.getValue() > 0) bosses++;
        }
        List<Component> extra = new ArrayList<>();
        Reputation.Faction best = Reputation.best(target);
        if (best != null && Reputation.get(target, best.id()) > 0) {
            extra.add(Component.translatable("screen.isekai_mahou.profile.best_faction", best.coloredName(),
                    Reputation.standing(target, best.id()).displayName()));
        }
        for (Function<ServerPlayer, Component> f : LINES) {
            if (extra.size() >= MAX_EXTRA) break;
            try {
                Component c = f.apply(target);
                if (c != null) extra.add(c);
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.error("[social] linha de perfil falhou", ex);
            }
        }
        SocialPayloads.Profile p = new SocialPayloads.Profile(target.getUUID(), target.getGameProfile().getName(), d.path().id,
                d.level(), d.circle(), Optional.ofNullable(Reputation.selectedTitle(target)),
                Optional.ofNullable(city == null ? null : AllegianceService.cityName(target.server, city)),
                party == null ? 0 : party.members.size(), party == null ? "" : party.name(party.leader),
                d.counter("duel.win"), d.counter("duel.loss"), d.counter("kill.total"), bosses, extra,
                PartyService.sameParty(viewer.server, viewer.getUUID(), target.getUUID()),
                AllegianceService.atWar(viewer.server, viewer.getUUID(), target.getUUID()));
        PacketDistributor.sendToPlayer(viewer, p);
    }

    private ProfileService() {}
}
