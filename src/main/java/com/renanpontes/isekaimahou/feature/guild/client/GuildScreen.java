package com.renanpontes.isekaimahou.feature.guild.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.guild.GuildContent;
import com.renanpontes.isekaimahou.feature.guild.GuildData;
import com.renanpontes.isekaimahou.feature.guild.GuildFaction;
import com.renanpontes.isekaimahou.feature.guild.item.FactionBoardItem;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.OfferView;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.QuestView;
import com.renanpontes.isekaimahou.feature.guild.GuildRank;
import com.renanpontes.isekaimahou.feature.guild.GuildService;
import com.renanpontes.isekaimahou.feature.guild.client.GuildUi.CardButton;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tela da Guilda. Três modos:
 * <ul>
 *   <li>Recepção (recepcionista/balcão): abas Recepção, Quadro, Ativas e Loja;</li>
 *   <li>Quadro de Missões (bloco): abas Quadro e Ativas;</li>
 *   <li>Mestre da Guilda: rank atual, pontos e a prova de promoção.</li>
 * </ul>
 * v0.8: o Quadro de Facção (Reino, Academia, Igreja, Submundo) usa o modo Quadro com as missões daquela facção, o brasão,
 * o patamar de reputação e a barra até o próximo patamar; missões trancadas dizem o patamar que falta.
 * Todas as ações vão ao servidor, que responde com a tela atualizada.
 */
public class GuildScreen extends Screen {
    private enum Tab { RECEPTION, BOARD, ACTIVE, SHOP }

    private GuildNetwork.ScreenData data;
    private Tab tab;
    private final GuildUi.Frame frame = new GuildUi.Frame();
    private int left, top, panelW, panelH;
    private int scroll, maxScroll;
    /** Abandono pede confirmação (segundo clique em até 3 s). */
    private ResourceLocation confirmAbandon;
    private long confirmUntil;

    public GuildScreen(GuildNetwork.ScreenData data) {
        super(Component.translatable("screen.isekai_mahou.guild.title"));
        this.data = data;
        this.tab = defaultTab(data);
    }

    /** Gancho do pacote: abre a tela, ou atualiza a que já está aberta no mesmo modo (mantém aba e rolagem). */
    static void openOrUpdate(GuildNetwork.ScreenData data) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof GuildScreen gs && gs.data.screenMode() == data.screenMode() && gs.data.faction().equals(data.faction())) {
            gs.data = data;
            return;
        }
        if (!data.isRefresh()) mc.setScreen(new GuildScreen(data));
    }

    private static Tab defaultTab(GuildNetwork.ScreenData d) {
        if (d.screenMode() == GuildService.MODE_BOARD) return Tab.BOARD;
        if (d.screenMode() == GuildService.MODE_RECEPTION && GuildNetwork.clientState.rank() > 0) {
            for (QuestView q : GuildNetwork.clientState.active()) if (q.state() == GuildNetwork.Q_READY) return Tab.ACTIVE;
        }
        return Tab.RECEPTION;
    }

    private List<Tab> tabs() {
        return switch (data.screenMode()) {
            case GuildService.MODE_RECEPTION -> List.of(Tab.RECEPTION, Tab.BOARD, Tab.ACTIVE, Tab.SHOP);
            case GuildService.MODE_BOARD -> List.of(Tab.BOARD, Tab.ACTIVE);
            default -> List.of();
        };
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 16, 440);
        panelH = Math.min(height - 16, 256);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
    }

    private void send(int action, ResourceLocation id, int index, int amount) {
        PacketDistributor.sendToServer(new GuildNetwork.Action(action, id, index, amount));
    }

    private void send(int action, ResourceLocation id) {
        send(action, id, 0, 1);
    }

    private static final ResourceLocation NONE = ResourceLocation.withDefaultNamespace("empty");
    private static final String TAB_KEY = "screen.isekai_mahou.guild.tab.";
    private static final String TIP_KEY = "screen.isekai_mahou.guild.tip.";
    private static final int TIPS = 8;
    /** Texto de abertura de cada Quadro de Facção ("...faction_intro.kingdom"). */
    private static final String INTRO_KEY = "screen.isekai_mahou.guild.faction_intro.";

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        frame.clear();
        Font font = this.font;
        GuildUi.panel(g, left, top, panelW, panelH);

        // Cabeçalho
        GuildFaction faction = data.guildFaction();
        boolean factionBoard = data.screenMode() == GuildService.MODE_BOARD && !faction.isGuild();
        Component heading = factionBoard
                ? Component.translatable("screen.isekai_mahou.guild.heading_faction", faction.displayName()).withStyle(ChatFormatting.BOLD)
                : Component.translatable(switch (data.screenMode()) {
                    case GuildService.MODE_BOARD -> "screen.isekai_mahou.guild.heading_board";
                    case GuildService.MODE_MASTER -> "screen.isekai_mahou.guild.heading_master";
                    default -> "screen.isekai_mahou.guild.heading_reception";
                }).withStyle(ChatFormatting.BOLD);
        g.drawString(font, heading, left + 10, top + 9, factionBoard ? faction.argb() : GuildUi.GOLD, true);
        GuildUi.coinCounter(g, font, left + panelW - 10 - 40, top + 5);
        GuildNetwork.State s = GuildNetwork.clientState;
        if (s.rank() > 0 && !factionBoard) GuildUi.rankBadge(g, font, left + panelW - 10 - 40 - 22, top + 5, 16, s.guildRank(), false);
        GuildUi.divider(g, left + 8, top + 24, panelW - 16);

        // Coluna esquerda: retrato do NPC (ou o quadro) e as abas
        int colW = 96;
        int cx = left + 8, cy = top + 30;
        renderPortrait(g, cx, cy, colW, mx, my);
        int ty = cy + 96;
        for (Tab t : tabs()) {
            boolean sel = t == tab;
            Component label = Component.translatable(TAB_KEY + t.name().toLowerCase(java.util.Locale.ROOT));
            if (t == Tab.ACTIVE) label = factionBoard
                    ? Component.translatable("screen.isekai_mahou.guild.tab.active_n", s.activeCount(false), GuildData.MAX_FACTION_ACTIVE)
                    : Component.translatable("screen.isekai_mahou.guild.tab.active_n", s.activeCount(true), GuildData.MAX_ACTIVE);
            GuildUi.button(g, font, frame, cx, ty, colW, 16, label, mx, my, !sel, sel ? GuildUi.GOLD : GuildUi.BORDER, () -> {
                tab = t;
                scroll = 0;
            });
            if (sel) {
                g.fill(cx, ty, cx + colW, ty + 16, 0x40FFD86B);
                g.renderOutline(cx, ty, colW, 16, GuildUi.GOLD);
                Component shown = GuildUi.ellipsize(font, label, colW - 6);
                g.drawString(font, shown, cx + (colW - font.width(shown)) / 2, ty + 4, GuildUi.GOLD, true);
            }
            ty += 19;
        }

        // Conteúdo
        int x = cx + colW + 8, y = top + 30, w = left + panelW - 8 - x, h = top + panelH - 8 - y;
        g.enableScissor(x, y, x + w, y + h);
        int contentH;
        if (data.screenMode() == GuildService.MODE_MASTER) contentH = renderMaster(g, font, x, y - scroll, w, mx, my);
        else contentH = switch (tab) {
            case RECEPTION -> renderReception(g, font, x, y - scroll, w, mx, my);
            case BOARD -> renderBoard(g, font, x, y - scroll, w, mx, my);
            case ACTIVE -> renderActive(g, font, x, y - scroll, w, mx, my, data.screenMode() != GuildService.MODE_MASTER);
            case SHOP -> renderShop(g, font, x, y - scroll, w, mx, my);
        };
        g.disableScissor();
        // Cliques fora da área visível do conteúdo não valem.
        frame.hits.removeIf(hit -> hit.x() >= x && hit.x() < x + w && (hit.y() + hit.h() <= y || hit.y() >= y + h));
        maxScroll = Math.max(0, contentH - h);
        // A lista pode encolher (missão entregue/abandonada): a rolagem não pode passar do fim.
        scroll = Math.min(scroll, maxScroll);
        if (maxScroll > 0) {
            int barH = Math.max(14, h * h / Math.max(1, contentH));
            int barY = y + (int) ((h - barH) * (scroll / (float) maxScroll));
            g.fill(x + w + 2, y, x + w + 4, y + h, 0x40000000);
            g.fill(x + w + 2, barY, x + w + 4, barY + barH, GuildUi.BORDER);
        }
        frame.tooltips(g, font, mx, my);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        renderTransparentBackground(g);
    }

    private void renderPortrait(GuiGraphics g, int x, int y, int w, int mx, int my) {
        g.fill(x, y, x + w, y + 90, 0xFF0F0B07);
        g.renderOutline(x, y, w, 90, GuildUi.BORDER_DIM);
        Entity e = data.npcId() >= 0 && minecraft != null && minecraft.level != null ? minecraft.level.getEntity(data.npcId()) : null;
        if (e instanceof LivingEntity le) {
            // O próprio método recorta (scissor) na caixa; a entidade fica centrada na altura.
            GuildNpcRenderer.portrait = true;
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, x + 2, y + 2, x + w - 2, y + 76, 30, 0.0625F, mx, my, le);
            } finally {
                GuildNpcRenderer.portrait = false;
            }
            Component name = le.getDisplayName();
            g.drawString(font, GuildUi.ellipsize(font, name, w - 6), x + (w - Math.min(w - 6, font.width(name))) / 2, y + 79, GuildUi.TEXT, true);
        } else if (!data.guildFaction().isGuild()) {
            renderFactionCrest(g, x, y, w);
        } else {
            g.pose().pushPose();
            g.pose().translate(x + w / 2.0F - 24, y + 21, 0);
            g.pose().scale(3.0F, 3.0F, 1);
            g.renderItem(new ItemStack(GuildContent.QUEST_BOARD_ITEM.get()), 0, 0);
            g.pose().popPose();
        }
    }

    /** Quadro de Facção: brasão (emblema do núcleo, ou o item do quadro), patamar e barra até o próximo patamar. */
    /** Emblemas que existem nos pacotes de recursos (consultado uma vez por emblema, não a cada quadro). */
    private static final java.util.Map<net.minecraft.resources.ResourceLocation, Boolean> EMBLEM_EXISTS = new java.util.HashMap<>();

    private void renderFactionCrest(GuiGraphics g, int x, int y, int w) {
        GuildFaction f = data.guildFaction();
        g.fillGradient(x + 1, y + 1, x + w - 1, y + 89, GuildUi.mix(f.argb(), 0xFF0F0B07, 0.7F), 0xFF0F0B07);
        Reputation.Faction rf = Reputation.faction(f.id());
        if (rf != null && minecraft != null && EMBLEM_EXISTS.computeIfAbsent(rf.emblem(),
                id -> minecraft.getResourceManager().getResource(id).isPresent())) {
            g.blit(rf.emblem(), x + w / 2 - 20, y + 4, 0, 0, 40, 40, 40, 40);
        } else {
            g.pose().pushPose();
            g.pose().translate(x + w / 2.0F - 20, y + 4, 0);
            g.pose().scale(2.5F, 2.5F, 1);
            g.renderItem(FactionBoardItem.of(GuildContent.FACTION_BOARD_ITEM.get(), f), 0, 0);
            g.pose().popPose();
        }
        if (minecraft == null || minecraft.player == null) return;
        int value = Reputation.get(minecraft.player, f.id());
        Reputation.Standing st = Reputation.standing(minecraft.player, f.id());
        Component name = st.displayName();
        g.drawString(font, name, x + (w - font.width(name)) / 2, y + 50, st.argb(), true);
        Reputation.Standing next = st.next();
        float frac = next == null ? 1F : (value - st.min) / (float) Math.max(1, next.min - st.min);
        GuildUi.progressBar(g, x + 6, y + 62, w - 12, 5, frac, st.argb());
        Component num = next == null ? Component.literal(String.valueOf(value)) : Component.literal(value + " / " + next.min);
        g.drawString(font, num, x + (w - font.width(num)) / 2, y + 71, GuildUi.MUTED, false);
        float mult = Reputation.priceMultiplier(minecraft.player, f.id());
        if (mult != 1F) {
            Component price = Component.translatable("screen.isekai_mahou.guild.price_mult", String.format("%+d", Math.round((mult - 1F) * 100)));
            g.drawString(font, price, x + (w - font.width(price)) / 2, y + 80, mult < 1F ? GuildUi.GREEN : GuildUi.RED, false);
        }
    }

    // ---------------------------------------------------------------- Recepção
    private int renderReception(GuiGraphics g, Font font, int x, int y, int w, int mx, int my) {
        GuildNetwork.State s = GuildNetwork.clientState;
        int y0 = y;
        if (s.rank() <= 0) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.guild.welcome_title").withStyle(ChatFormatting.BOLD), x, y + 2, GuildUi.GOLD, true);
            y += 16;
            y += GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.welcome_text"), x, y, w - 4, GuildUi.TEXT, 8) + 6;
            y += GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.welcome_ranks"), x, y, w - 4, GuildUi.MUTED, 4) + 8;
            GuildUi.button(g, font, frame, x + (w - 170) / 2, y, 170, 20, Component.translatable("screen.isekai_mahou.guild.register"),
                    mx, my, true, GuildUi.GREEN, () -> send(GuildNetwork.ACT_REGISTER, NONE));
            return y + 24 - y0;
        }
        Component name = minecraft != null && minecraft.player != null ? minecraft.player.getName() : Component.empty();
        int cardW = Math.min(170, w / 2);
        GuildUi.adventurerCard(g, font, x, y, cardW, s, name);
        int rx = x + cardW + 8, rw = w - cardW - 8;
        int ry = y;
        g.drawString(font, Component.translatable("screen.isekai_mahou.guild.desk_title").withStyle(ChatFormatting.BOLD), rx, ry, GuildUi.GOLD, true);
        ry += 13;
        int ready = 0;
        for (QuestView q : s.active()) if (q.state() == GuildNetwork.Q_READY) ready++;
        ry += GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.desk_active", s.active().size(), GuildData.MAX_ACTIVE),
                rx, ry, rw, GuildUi.TEXT, 2);
        if (ready > 0) {
            ry += GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.desk_ready", ready), rx, ry, rw, GuildUi.GREEN, 2);
        }
        if (s.trialReady()) {
            ry += GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.desk_trial"), rx, ry, rw, 0xFFD9A8FF, 3);
        }
        ry += 4;
        GuildUi.button(g, font, frame, rx, ry, rw, 16, Component.translatable("screen.isekai_mahou.guild.go_board"), mx, my, true, GuildUi.BORDER,
                () -> { tab = Tab.BOARD; scroll = 0; });
        ry += 19;
        if (ready > 0) {
            GuildUi.button(g, font, frame, rx, ry, rw, 16, Component.translatable("screen.isekai_mahou.guild.go_turn_in"), mx, my, true, GuildUi.GREEN,
                    () -> { tab = Tab.ACTIVE; scroll = 0; });
            ry += 19;
        }
        GuildUi.button(g, font, frame, rx, ry, rw, 16, Component.translatable("screen.isekai_mahou.guild.go_shop"), mx, my, true, GuildUi.GOLD,
                () -> { tab = Tab.SHOP; scroll = 0; });
        ry += 19;
        if (!data.hasCard()) {
            boolean can = GuildUi.coins() >= GuildService.REISSUE_PRICE;
            GuildUi.button(g, font, frame, rx, ry, rw, 16, Component.translatable("screen.isekai_mahou.guild.reissue", GuildService.REISSUE_PRICE),
                    mx, my, can, GuildUi.BLUE, () -> send(GuildNetwork.ACT_REISSUE_CARD, NONE));
            ry += 19;
        }
        int bottom = Math.max(y + 96, ry);
        bottom += 4;
        GuildUi.divider(g, x, bottom, w);
        bottom += 6;
        long time = minecraft != null && minecraft.level != null ? minecraft.level.getGameTime() / 200 : 0;
        bottom += GuildUi.wrapped(g, font, Component.translatable(TIP_KEY + (int) (time % TIPS)), x, bottom, w, GuildUi.FAINT, 3);
        return bottom - y0 + 4;
    }

    // ---------------------------------------------------------------- Quadro
    private int renderBoard(GuiGraphics g, Font font, int x, int y, int w, int mx, int my) {
        int y0 = y;
        GuildFaction faction = data.guildFaction();
        Component boardTitle = faction.isGuild() ? Component.translatable("screen.isekai_mahou.guild.board_title")
                : Component.translatable("screen.isekai_mahou.guild.board_title_faction", faction.displayName());
        g.drawString(font, GuildUi.ellipsize(font, boardTitle.copy().withStyle(ChatFormatting.BOLD), w * 2 / 3), x, y,
                faction.isGuild() ? GuildUi.GOLD : faction.argb(), true);
        Component sub = Component.translatable("screen.isekai_mahou.guild.board_sub");
        g.drawString(font, sub, x + w - font.width(sub), y, GuildUi.FAINT, false);
        y += 13;
        if (!faction.isGuild()) {
            y += GuildUi.wrapped(g, font, Component.translatable(INTRO_KEY + faction.id()), x, y, w,
                    GuildUi.MUTED, 3) + 4;
        }
        if (data.board().isEmpty()) {
            return y + GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.board_empty"), x, y, w, GuildUi.MUTED, 3) - y0;
        }
        boolean registered = GuildNetwork.clientState.rank() > 0;
        boolean full = faction.isGuild() ? GuildNetwork.clientState.activeCount(true) >= GuildData.MAX_ACTIVE
                : GuildNetwork.clientState.activeCount(false) >= GuildData.MAX_FACTION_ACTIVE;
        boolean trusted = minecraft == null || minecraft.player == null || Reputation.canTrade(minecraft.player, faction.id());
        for (QuestView q : data.board()) {
            List<CardButton> buttons = new ArrayList<>();
            Component status = null;
            int statusColor = GuildUi.MUTED;
            switch (q.state()) {
                case GuildNetwork.Q_AVAILABLE -> buttons.add(new CardButton(Component.translatable(full
                        ? "screen.isekai_mahou.guild.full" : "screen.isekai_mahou.guild.accept"), GuildUi.GREEN, !full,
                        () -> send(GuildNetwork.ACT_ACCEPT, q.id())));
                case GuildNetwork.Q_READY -> buttons.add(new CardButton(Component.translatable("screen.isekai_mahou.guild.turn_in"),
                        GuildUi.GREEN, true, () -> send(GuildNetwork.ACT_TURN_IN, q.id())));
                case GuildNetwork.Q_ACTIVE -> { status = Component.translatable("screen.isekai_mahou.guild.state_active"); statusColor = GuildUi.BLUE; }
                case GuildNetwork.Q_TAKEN -> status = Component.translatable("screen.isekai_mahou.guild.state_taken");
                case GuildNetwork.Q_LOCKED -> {
                    if (!faction.isGuild()) {
                        Reputation.Standing need = Reputation.Standing.byId(q.standing());
                        status = !trusted ? Component.translatable("screen.isekai_mahou.guild.state_refused")
                                : Component.translatable("screen.isekai_mahou.guild.state_standing",
                                need == null ? Component.literal(q.standing()) : need.displayName());
                    } else {
                        status = registered ? Component.translatable("screen.isekai_mahou.guild.state_locked", q.guildRank().letter())
                                : Component.translatable("screen.isekai_mahou.guild.state_unregistered");
                    }
                    statusColor = GuildUi.RED;
                }
                default -> { }
            }
            GuildUi.quest(g, font, frame, q, x, y, w, true, mx, my, buttons, status, statusColor);
            y += GuildUi.questHeight(q, true) + 4;
        }
        return y - y0;
    }

    // ---------------------------------------------------------------- Ativas
    private int renderActive(GuiGraphics g, Font font, int x, int y, int w, int mx, int my, boolean canTurnIn) {
        int y0 = y;
        GuildNetwork.State s = GuildNetwork.clientState;
        g.drawString(font, Component.translatable("screen.isekai_mahou.guild.active_title_both", s.activeCount(true), GuildData.MAX_ACTIVE,
                s.activeCount(false), GuildData.MAX_FACTION_ACTIVE).withStyle(ChatFormatting.BOLD), x, y, GuildUi.GOLD, true);
        y += 13;
        if (s.active().isEmpty() && s.trial().isEmpty()) {
            return y + GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.active_empty"), x, y, w, GuildUi.MUTED, 3) - y0;
        }
        List<QuestView> list = new ArrayList<>(s.active());
        s.trial().ifPresent(list::add);
        long now = System.currentTimeMillis();
        for (QuestView q : list) {
            boolean trial = s.trial().isPresent() && s.trial().get() == q;
            List<CardButton> buttons = new ArrayList<>();
            // Entrega só no quadro/balcão da facção da missão.
            boolean here = q.faction().equals(data.faction());
            if (!trial && q.state() == GuildNetwork.Q_READY && canTurnIn && here) {
                buttons.add(new CardButton(Component.translatable("screen.isekai_mahou.guild.turn_in"), GuildUi.GREEN, true,
                        () -> send(GuildNetwork.ACT_TURN_IN, q.id())));
            }
            if (!trial) {
                boolean confirming = q.id().equals(confirmAbandon) && now < confirmUntil;
                buttons.add(new CardButton(Component.translatable(confirming ? "screen.isekai_mahou.guild.confirm" : "screen.isekai_mahou.guild.abandon"),
                        GuildUi.RED, true, () -> {
                    if (q.id().equals(confirmAbandon) && System.currentTimeMillis() < confirmUntil) {
                        send(GuildNetwork.ACT_ABANDON, q.id());
                        confirmAbandon = null;
                    } else {
                        confirmAbandon = q.id();
                        confirmUntil = System.currentTimeMillis() + 3000;
                    }
                }));
            }
            Component status = trial ? Component.translatable("screen.isekai_mahou.guild.state_trial") : null;
            if (!trial && q.state() == GuildNetwork.Q_READY && !here) {
                buttons.add(0, new CardButton(Component.translatable("screen.isekai_mahou.guild.turn_in_at", q.guildFaction().displayName()),
                        GuildUi.FAINT, false, () -> { }));
            }
            GuildUi.quest(g, font, frame, q, x, y, w, true, mx, my, buttons, status, 0xFFD9A8FF);
            y += GuildUi.questHeight(q, true) + 4;
        }
        return y - y0;
    }

    // ---------------------------------------------------------------- Loja
    private int renderShop(GuiGraphics g, Font font, int x, int y, int w, int mx, int my) {
        int y0 = y;
        g.drawString(font, Component.translatable("screen.isekai_mahou.guild.shop_title").withStyle(ChatFormatting.BOLD), x, y, GuildUi.GOLD, true);
        Component sub = Component.translatable("screen.isekai_mahou.guild.shop_sub");
        g.drawString(font, GuildUi.ellipsize(font, sub, w / 2), x + w - Math.min(w / 2, font.width(sub)), y, GuildUi.FAINT, false);
        y += 13;
        if (data.shop().isEmpty()) {
            return y + GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.shop_empty"), x, y, w, GuildUi.MUTED, 3) - y0;
        }
        int cols = w >= 300 ? 2 : 1;
        int cardW = (w - (cols - 1) * 4) / cols, cardH = 30;
        int coins = GuildUi.coins();
        int i = 0;
        for (OfferView o : data.shop()) {
            int cx = x + (i % cols) * (cardW + 4);
            int cy = y + (i / cols) * (cardH + 4);
            GuildRank rank = GuildRank.byLevel(o.rank());
            boolean hover = GuildUi.over(mx, my, cx, cy, cardW, cardH);
            GuildUi.card(g, cx, cy, cardW, cardH, o.locked() ? GuildUi.CARD_DIM : hover ? GuildUi.CARD_HOVER : GuildUi.CARD,
                    rank == null ? GuildUi.BORDER : rank.argb);
            GuildUi.item(g, frame, o.stack(), cx + 6, cy + 7, mx, my);
            int tx = cx + 26;
            int btnW = 48;
            g.drawString(font, GuildUi.ellipsize(font, o.stack().getHoverName(), cardW - 26 - btnW - 8), tx, cy + 5,
                    o.locked() ? GuildUi.FAINT : GuildUi.TEXT, false);
            g.pose().pushPose();
            g.pose().translate(tx, cy + 15, 0);
            g.pose().scale(0.625F, 0.625F, 1);
            g.renderItem(new ItemStack(GuildContent.GUILD_COIN.get()), 0, 0);
            g.pose().popPose();
            boolean afford = coins >= o.price();
            g.drawString(font, String.valueOf(o.price()), tx + 12, cy + 17, afford ? GuildUi.GOLD : GuildUi.RED, false);
            if (rank != null) GuildUi.rankBadge(g, font, tx + 14 + font.width(String.valueOf(o.price())) + 4, cy + 15, 11, rank, o.locked());
            Component label = Component.translatable(o.locked() ? "screen.isekai_mahou.guild.locked" : "screen.isekai_mahou.guild.buy");
            if (o.locked() && !o.standing().isEmpty() && GuildUi.over(mx, my, cx, cy, cardW, cardH)) {
                Reputation.Standing need = Reputation.Standing.byId(o.standing());
                if (need != null) frame.hoverText = List.of(Component.translatable("screen.isekai_mahou.guild.shop_standing", need.displayName()));
            }
            GuildUi.button(g, font, frame, cx + cardW - btnW - 5, cy + 8, btnW, 14, label, mx, my, !o.locked() && afford, GuildUi.GOLD,
                    () -> send(GuildNetwork.ACT_BUY, NONE, o.index(), hasShiftDown() ? 5 : 1));
            if (hover && !o.locked() && GuildUi.over(mx, my, cx + cardW - btnW - 5, cy + 8, btnW, 14)) {
                frame.hoverText = List.of(Component.translatable("screen.isekai_mahou.guild.buy_hint").withStyle(ChatFormatting.GRAY));
            }
            i++;
        }
        int rows = (data.shop().size() + cols - 1) / cols;
        return 13 + rows * (cardH + 4);
    }

    // ---------------------------------------------------------------- Mestre
    private int renderMaster(GuiGraphics g, Font font, int x, int y, int w, int mx, int my) {
        int y0 = y;
        GuildNetwork.State s = GuildNetwork.clientState;
        GuildRank rank = s.guildRank();
        if (rank == null) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.guild.master_title").withStyle(ChatFormatting.BOLD), x, y, GuildUi.GOLD, true);
            y += 14;
            return y + GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.master_unregistered"), x, y, w, GuildUi.TEXT, 5) - y0;
        }
        GuildRank next = rank.next();
        // Rank atual → próximo
        GuildUi.rankBadge(g, font, x, y, 30, rank, false);
        if (next != null) {
            g.drawString(font, "➜", x + 38, y + 11, GuildUi.GOLD, true);
            GuildUi.rankBadge(g, font, x + 52, y, 30, next, s.points() < next.pointsRequired);
        }
        int tx = x + (next != null ? 90 : 40);
        int tw = w - (tx - x);
        g.drawString(font, Component.translatable("screen.isekai_mahou.guild.master_title").withStyle(ChatFormatting.BOLD), tx, y + 1, GuildUi.GOLD, true);
        if (next != null) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.guild.card_points", s.points(), next.pointsRequired), tx, y + 13, GuildUi.TEXT, false);
            GuildUi.progressBar(g, tx, y + 24, tw, 6, (s.points() - rank.pointsRequired) / (float) Math.max(1, next.pointsRequired - rank.pointsRequired),
                    s.points() >= next.pointsRequired ? GuildUi.GREEN : rank.argb);
        } else {
            g.drawString(font, Component.translatable("screen.isekai_mahou.guild.card_points_max", s.points()), tx, y + 13, GuildUi.GOLD, false);
        }
        y += 38;
        GuildUi.divider(g, x, y, w);
        y += 6;
        if (next == null) {
            return y + GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.master_max"), x, y, w, GuildUi.GOLD, 5) - y0;
        }
        if (s.trial().isPresent()) {
            QuestView t = s.trial().get();
            y += GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.master_trial_going", next.letter()), x, y, w, GuildUi.TEXT, 3) + 4;
            List<CardButton> buttons = List.of(
                    new CardButton(Component.translatable("screen.isekai_mahou.guild.trial_report"), GuildUi.GREEN,
                            t.state() == GuildNetwork.Q_READY, () -> send(GuildNetwork.ACT_TRIAL_REPORT, NONE)),
                    new CardButton(Component.translatable(confirmAbandon != null && confirmAbandon.equals(t.id())
                            && System.currentTimeMillis() < confirmUntil ? "screen.isekai_mahou.guild.confirm" : "screen.isekai_mahou.guild.give_up"),
                            GuildUi.RED, true, () -> {
                        if (t.id().equals(confirmAbandon) && System.currentTimeMillis() < confirmUntil) {
                            send(GuildNetwork.ACT_TRIAL_ABANDON, NONE);
                            confirmAbandon = null;
                        } else {
                            confirmAbandon = t.id();
                            confirmUntil = System.currentTimeMillis() + 3000;
                        }
                    }));
            GuildUi.quest(g, font, frame, t, x, y, w, true, mx, my, buttons, null, 0);
            return y + GuildUi.questHeight(t, true) + 4 - y0;
        }
        boolean enough = s.points() >= next.pointsRequired;
        Component intro = enough ? Component.translatable("screen.isekai_mahou.guild.master_offer", next.letter())
                : Component.translatable("screen.isekai_mahou.guild.master_need", next.pointsRequired - s.points(), next.letter());
        y += GuildUi.wrapped(g, font, intro, x, y, w, enough ? GuildUi.TEXT : GuildUi.MUTED, 4) + 4;
        if (data.trialOffer().isPresent()) {
            QuestView t = data.trialOffer().get();
            List<CardButton> buttons = List.of(new CardButton(Component.translatable("screen.isekai_mahou.guild.trial_accept"),
                    0xFFB46BFF, enough, () -> send(GuildNetwork.ACT_TRIAL_ACCEPT, NONE)));
            GuildUi.quest(g, font, frame, t, x, y, w, true, mx, my, buttons, null, 0);
            y += GuildUi.questHeight(t, true) + 4;
            // O Mestre sorteia a prova por jogador e por dia (GuildQuests.trialFor): avisa que amanhã pode ser outra.
            if (data.dailyTrial()) {
                y += GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.master_daily"), x, y, w, GuildUi.FAINT, 3);
            }
        } else {
            y += GuildUi.wrapped(g, font, Component.translatable("screen.isekai_mahou.guild.master_no_trial"), x, y, w, GuildUi.MUTED, 3);
        }
        return y - y0;
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && frame.click(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (maxScroll > 0) {
            scroll = Math.max(0, Math.min(maxScroll, scroll - (int) (sy * 18)));
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (minecraft != null && minecraft.options.keyInventory.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
