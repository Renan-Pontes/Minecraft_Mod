package com.renanpontes.isekaimahou.feature.guild.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.feature.guild.GuildContent;
import com.renanpontes.isekaimahou.feature.guild.GuildData;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.QuestView;
import com.renanpontes.isekaimahou.feature.guild.client.GuildUi.CardButton;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Guilda" do menu B: Cartão de Aventureiro (rank, pontos, próxima prova), os companheiros contratados e as missões
 * ativas (da Guilda e de facção) com o progresso. Dá para abandonar uma missão daqui (segundo clique confirma); entregar
 * só no quadro/recepção da facção da missão.
 */
public class GuildTab implements MenuTab {
    private static final ResourceLocation NONE = ResourceLocation.withDefaultNamespace("empty");

    private final GuildUi.Frame frame = new GuildUi.Frame();
    private int x, y, w, h;
    private int scroll, maxScroll;
    private ResourceLocation confirm;
    private long confirmUntil;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        frame.clear();
        Font font = Minecraft.getInstance().font;
        GuildNetwork.State s = GuildNetwork.clientState;
        if (s.rank() <= 0 && s.active().isEmpty() && GuildNetwork.clientCompanions.isEmpty()) {
            renderUnregistered(g, font);
            return;
        }
        Component name = Minecraft.getInstance().player != null ? Minecraft.getInstance().player.getName() : Component.empty();
        int cardW = Math.min(150, w / 3);
        GuildUi.adventurerCard(g, font, x, y, cardW, s, name);
        int ty = y + 98;
        List<GuildNetwork.CompanionView> companions = GuildNetwork.clientCompanions;
        if (!companions.isEmpty()) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.guild.companions_title").withStyle(ChatFormatting.BOLD), x, ty,
                    GuildUi.GOLD, true);
            ty += 12;
            for (GuildNetwork.CompanionView v : companions) {
                if (ty + 20 > y + h) break;
                GuildUi.rankBadge(g, font, x, ty, 11, v.guildRank(), false);
                g.drawString(font, GuildUi.ellipsize(font, v.name(), cardW - 16), x + 14, ty + 1, GuildUi.TEXT, false);
                float frac = v.maxHealth() > 0 ? v.health() / v.maxHealth() : 0F;
                GuildUi.progressBar(g, x + 14, ty + 11, cardW - 16, 4, frac, frac > 0.6F ? GuildUi.GREEN : frac > 0.3F ? GuildUi.GOLD : GuildUi.RED);
                ty += 18;
            }
            ty += 2;
        }
        ty += GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.tab_hint"), x, ty, cardW, GuildUi.FAINT, 6);

        // Lista de missões (rolável)
        int lx = x + cardW + 8, lw = w - cardW - 14;
        g.drawString(font, Component.translatable("screen.isekai_mahou.guild.active_title_both", s.activeCount(true), GuildData.MAX_ACTIVE,
                s.activeCount(false), GuildData.MAX_FACTION_ACTIVE).withStyle(ChatFormatting.BOLD), lx, y, GuildUi.GOLD, true);
        int top = y + 13, bottom = y + h;
        List<QuestView> list = new ArrayList<>(s.active());
        s.trial().ifPresent(list::add);
        if (list.isEmpty()) {
            GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.active_empty"), lx, top, lw, GuildUi.MUTED, 4);
            maxScroll = 0;
            scroll = 0;
            frame.tooltips(g, font, mx, my);
            return;
        }
        g.enableScissor(lx, top, lx + lw, bottom);
        int cy = top - scroll;
        long now = System.currentTimeMillis();
        for (QuestView q : list) {
            boolean trial = s.trial().isPresent() && s.trial().get() == q;
            boolean confirming = q.id().equals(confirm) && now < confirmUntil;
            Component label = Component.translatable(confirming ? "screen.isekai_mahou.guild.confirm"
                    : trial ? "screen.isekai_mahou.guild.give_up" : "screen.isekai_mahou.guild.abandon");
            List<CardButton> buttons = List.of(new CardButton(label, GuildUi.RED, true, () -> {
                if (q.id().equals(confirm) && System.currentTimeMillis() < confirmUntil) {
                    PacketDistributor.sendToServer(GuildNetwork.Action.of(trial ? GuildNetwork.ACT_TRIAL_ABANDON : GuildNetwork.ACT_ABANDON,
                            trial ? NONE : q.id()));
                    confirm = null;
                } else {
                    confirm = q.id();
                    confirmUntil = System.currentTimeMillis() + 3000;
                }
            }));
            GuildUi.quest(g, font, frame, q, lx, cy, lw, true, mx, my, buttons, null, 0);
            if (trial || q.state() == GuildNetwork.Q_READY) {
                Component tag = Component.translatable(trial ? "screen.isekai_mahou.guild.state_trial" : "screen.isekai_mahou.guild.state_ready");
                int tw = font.width(tag);
                g.drawString(font, tag, lx + lw - tw - 6, cy + GuildUi.questHeight(q, true) - 12, trial ? 0xFFD9A8FF : GuildUi.GREEN, true);
            }
            cy += GuildUi.questHeight(q, true) + 4;
        }
        g.disableScissor();
        frame.hits.removeIf(hit -> hit.x() >= lx && (hit.y() + hit.h() <= top || hit.y() >= bottom));
        int contentH = cy + scroll - top;
        maxScroll = Math.max(0, contentH - (bottom - top));
        scroll = Math.min(scroll, maxScroll);
        if (maxScroll > 0) {
            int vh = bottom - top;
            int barH = Math.max(14, vh * vh / Math.max(1, contentH));
            int barY = top + (int) ((vh - barH) * (scroll / (float) maxScroll));
            g.fill(lx + lw + 2, top, lx + lw + 4, bottom, 0x40000000);
            g.fill(lx + lw + 2, barY, lx + lw + 4, barY + barH, GuildUi.BORDER);
        }
        frame.tooltips(g, font, mx, my);
    }

    private void renderUnregistered(GuiGraphics g, Font font) {
        g.pose().pushPose();
        g.pose().translate(x + 8, y + 12, 0);
        g.pose().scale(4.0F, 4.0F, 1);
        g.renderItem(new ItemStack(GuildContent.QUEST_BOARD_ITEM.get()), 0, 0);
        g.pose().popPose();
        int tx = x + 90, tw = w - 96;
        g.drawString(font, Component.translatable("screen.isekai_mahou.guild.tab_unregistered_title").withStyle(ChatFormatting.BOLD),
                tx, y + 10, GuildUi.GOLD, true);
        int ty = y + 26;
        ty += GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.tab_unregistered"), tx, ty, tw, GuildUi.TEXT, 8) + 6;
        GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.welcome_ranks"), tx, ty, tw, GuildUi.MUTED, 5);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        return button == 0 && frame.click(mx, my);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (maxScroll <= 0) return false;
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (sy * 18)));
        return true;
    }
}
