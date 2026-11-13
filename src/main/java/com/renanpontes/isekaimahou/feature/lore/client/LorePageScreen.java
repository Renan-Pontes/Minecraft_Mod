package com.renanpontes.isekaimahou.feature.lore.client;

import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.feature.lore.LoreBook;
import com.renanpontes.isekaimahou.feature.lore.LoreData;
import com.renanpontes.isekaimahou.feature.lore.LoreFragments;
import com.renanpontes.isekaimahou.feature.lore.LoreNetwork;
import com.renanpontes.isekaimahou.feature.lore.LoreService;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * A página que se abre quando o jogador lê um fragmento (Página Perdida ou Estela): o texto, a coleção e o lugar que
 * ele revelou, com as coordenadas. Botões: seguir/parar de seguir no rastreador, abrir a Enciclopédia, fechar.
 * Não pausa o jogo.
 */
public class LorePageScreen extends Screen {
    private static final int W = 268, H = 196;
    private static final int PAPER_TOP = 0xF2E9DCB8, PAPER_BOTTOM = 0xF2D6C49A, INK = 0xFF3A2C1C, INK_SOFT = 0xFF6B5638;

    private final LoreFragments.Fragment fragment;
    private final boolean fresh, setComplete;
    private int left, top;
    private int scroll, maxScroll;
    private Button trackButton;

    public LorePageScreen(LoreFragments.Fragment fragment, boolean fresh, boolean setComplete) {
        super(fragment.title());
        this.fragment = fragment;
        this.fresh = fresh;
        this.setComplete = setComplete;
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = Math.max(4, (height - H) / 2 - 8);
        int by = top + H + 4, bw = 86;
        trackButton = addRenderableWidget(Button.builder(Component.empty(), b -> toggleTrack()).bounds(left, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.lore.open_book"), b -> LoreTab.open(fragment.id()))
                .bounds(left + (W - bw) / 2, by, bw, 18).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose()).bounds(left + W - bw, by, bw, 18).build());
        updateTrackButton();
    }

    private boolean tracking() {
        return fragment.id().equals(LoreData.tracked(ClientLore.data()));
    }

    private void toggleTrack() {
        ClientLore.send(tracking() ? LoreNetwork.UNTRACK : LoreNetwork.TRACK, fragment.id());
    }

    private void updateTrackButton() {
        LoreData.Spot spot = LoreData.spot(ClientLore.data(), fragment.id());
        trackButton.active = spot != null && spot.state() != LoreData.VISITED;
        trackButton.setMessage(Component.translatable(tracking() ? "screen.isekai_mahou.lore.untrack" : "screen.isekai_mahou.lore.track"));
    }

    @Override
    public void tick() {
        updateTrackButton();
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        MahouUi.roundRect(g, left, top, W, H, PAPER_TOP, PAPER_BOTTOM);
        MahouUi.roundOutline(g, left, top, W, H, 0xFF8A7038);
        MahouUi.roundOutline(g, left + 3, top + 3, W - 6, H - 6, 0x508A7038);

        LoreFragments.LoreSet set = LoreFragments.set(fragment.set());
        int cx = left + W / 2, y = top + 10;
        if (set != null) {
            MutableComponent head = Component.translatable("screen.isekai_mahou.lore.page_of", set.title(), fragment.index(), set.fragments().size());
            g.drawString(font, head, cx - font.width(head) / 2, y, INK_SOFT, false);
        }
        y += 12;
        g.drawString(font, fragment.title(), cx - font.width(fragment.title()) / 2, y, INK, false);
        y += 12;
        g.fill(left + 24, y, left + W - 24, y + 1, 0x808A7038);
        y += 6;

        // Rodapé: o lugar revelado
        int footH = 30;
        int textTop = y, textBottom = top + H - footH - 8;
        List<FormattedCharSequence> lines = font.split(Component.translatable(fragment.textKey()), W - 36);
        maxScroll = Math.max(0, lines.size() * 10 - (textBottom - textTop));
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(left + 6, textTop, left + W - 6, textBottom);
        for (int i = 0; i < lines.size(); i++) {
            int ly = textTop + i * 10 - scroll;
            if (ly > textTop - 10 && ly < textBottom) g.drawString(font, lines.get(i), left + 18, ly, INK, false);
        }
        g.disableScissor();
        if (maxScroll > 0) {
            int vh = textBottom - textTop, barH = Math.max(10, vh * vh / (lines.size() * 10));
            g.fill(left + W - 10, textTop + (int) ((vh - barH) * (scroll / (float) maxScroll)), left + W - 8,
                    textTop + (int) ((vh - barH) * (scroll / (float) maxScroll)) + barH, 0xA08A7038);
        }

        int fy = top + H - footH - 2;
        g.fill(left + 24, fy - 4, left + W - 24, fy - 3, 0x808A7038);
        LoreData.Spot spot = LoreData.spot(ClientLore.data(), fragment.id());
        Component place;
        Component where = null;
        if (spot == null) {
            place = Component.translatable("screen.isekai_mahou.lore.no_place");
        } else {
            place = Component.translatable(spot.state() == LoreData.VISITED ? "screen.isekai_mahou.lore.place_visited"
                    : "screen.isekai_mahou.lore.place_revealed", fragment.placeName());
            MutableComponent coords = Component.translatable("screen.isekai_mahou.lore.coords", spot.pos().getX(), spot.pos().getZ());
            Component dist = ClientLore.distanceText(spot);
            if (dist != null) coords.append("  ·  ").append(dist);
            else if (spot.dim() != Level.OVERWORLD || (minecraft != null && minecraft.level != null && minecraft.level.dimension() != spot.dim())) {
                coords.append("  ·  ").append(LoreService.dimName(spot.dim().location().getPath()));
            }
            where = coords;
        }
        g.drawString(font, MahouUi.fit(font, place, W - 30), left + 16, fy, 0xFF7A4A12, false);
        if (where != null) g.drawString(font, MahouUi.fit(font, where, W - 30), left + 16, fy + 11, INK_SOFT, false);

        // Uma linha acima da página: novo fragmento / coleção completa (sem estardalhaço)
        Component badge = null;
        if (setComplete && set != null) {
            LoreBook.Entry truth = LoreBook.entry(set.truth());
            badge = truth != null ? Component.translatable("screen.isekai_mahou.lore.truth_unlocked", truth.title())
                    : Component.translatable("screen.isekai_mahou.lore.set_done", set.title());
        } else if (fresh) {
            badge = Component.translatable("screen.isekai_mahou.lore.new_fragment",
                    LoreData.count(ClientLore.data()), LoreFragments.total());
        }
        if (badge != null) g.drawCenteredString(font, badge, cx, top - 12, MahouUi.GOLD);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (maxScroll > 0 && sy != 0) {
            scroll = Mth.clamp(scroll - (int) Math.signum(sy) * 20, 0, maxScroll);
            return true;
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
