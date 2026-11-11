package com.renanpontes.isekaimahou.client.social;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.hud.HudLayout;
import com.renanpontes.isekaimahou.client.hud.HudToasts;
import com.renanpontes.isekaimahou.core.social.ClientSocialView;
import com.renanpontes.isekaimahou.core.social.Requests;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.ArenaInfo;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.Req;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.State;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.LayeredDraw;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Linhas curtas logo abaixo da mira (região ARENA do {@link HudLayout}, antes do aviso de limite): duelo (contagem e
 * tempo), arena de chefe (ao selar e enquanto libera) e "chat de grupo ligado". Também transforma pedidos novos
 * (convite, troca, duelo) em avisos na coluna da esquerda.
 */
public class SocialStatus implements LayeredDraw.Layer {
    static final String RESERVE_ID = "social_status";
    private static final int LINE_H = 10;
    /** Mostra "Arena selada" por este tempo depois de selar. */
    private static final int SEAL_SHOW_TICKS = 80;

    private static final Set<String> SEEN_REQUESTS = new HashSet<>();
    private static boolean wasInArena;
    private static int arenaShownTicks;
    private static int lines;

    static void onState(State s) {
        // Pedido novo → aviso na coluna da esquerda
        Set<String> now = new HashSet<>();
        for (Req r : s.requests()) {
            String key = r.from() + ":" + r.kind();
            now.add(key);
            if (SEEN_REQUESTS.contains(key)) continue;
            Requests.Kind kind = Requests.Kind.byIndex(r.kind());
            ItemStack icon = switch (kind) {
                case PARTY -> new ItemStack(Items.BELL);
                case TRADE -> new ItemStack(Items.EMERALD);
                case DUEL -> new ItemStack(Items.IRON_SWORD);
            };
            int rgb = switch (kind) {
                case PARTY -> 0x7CFF8A;
                case TRADE -> 0xFFD86B;
                case DUEL -> 0xFF7A6B;
            };
            HudToasts.push(Component.translatable("hud.isekai_mahou.request." + kind.id, r.name()),
                    Component.translatable("hud.isekai_mahou.request.hint"), icon, rgb);
        }
        SEEN_REQUESTS.clear();
        SEEN_REQUESTS.addAll(now);
        boolean inArena = s.arena().isPresent() && !s.arena().get().releasing();
        if (inArena && !wasInArena) arenaShownTicks = SEAL_SHOW_TICKS;
        wasInArena = inArena;
    }

    static void tick() {
        if (arenaShownTicks > 0) arenaShownTicks--;
    }

    static void clear() {
        SEEN_REQUESTS.clear();
        wasInArena = false;
        arenaShownTicks = 0;
        lines = 0;
    }

    /** Altura reservada na região ARENA (o aviso de limite do núcleo desce isto). */
    static int height() {
        return lines == 0 ? 0 : lines * LINE_H + 4;
    }

    private static List<Component> collect() {
        State s = ClientSocialView.state();
        List<Component> out = new ArrayList<>();
        UUID duel = s.duelWith().orElse(null);
        if (duel != null) {
            int secs = (s.duelTicks() + 19) / 20;
            if (s.duelPhase() == 1) {
                out.add(Component.translatable("hud.isekai_mahou.duel.countdown", s.duelName()).withColor(0xFFD86B));
            } else {
                out.add(Component.translatable("hud.isekai_mahou.duel.fighting", s.duelName(),
                        String.format("%d:%02d", secs / 60, secs % 60)).withColor(0xFF9A6B));
            }
        }
        if (s.arena().isPresent()) {
            ArenaInfo a = s.arena().get();
            if (a.releasing()) {
                out.add(Component.translatable("hud.isekai_mahou.arena.releasing", (a.ticksLeft() + 19) / 20).withColor(0xC8C0D8));
            } else if (arenaShownTicks > 0) {
                out.add(Component.translatable("hud.isekai_mahou.arena.sealed", a.boss()).withColor(0xFF8A5C));
            }
        }
        if (s.partyChat()) out.add(Component.translatable("hud.isekai_mahou.party.chat_on").withColor(0x7CFF8A));
        return out;
    }

    @Override
    public void render(GuiGraphics g, DeltaTracker dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            lines = 0;
            return;
        }
        List<Component> list = collect();
        lines = list.size();
        if (list.isEmpty()) return;
        Font font = mc.font;
        int y = g.guiHeight() / 2 + 22 + HudLayout.reservedBefore(HudLayout.Region.ARENA, RESERVE_ID);
        int cx = g.guiWidth() / 2;
        for (Component c : list) {
            int w = Math.round(font.width(c) * 0.85F);
            MahouUi.roundRect(g, cx - w / 2 - 4, y - 1, w + 8, LINE_H, 0x90100C18, 0x90080610);
            MahouUi.smallText(g, font, c, cx - w / 2F, y + 1, 0.85F, 0xFFFFFFFF, true);
            y += LINE_H;
        }
    }
}
