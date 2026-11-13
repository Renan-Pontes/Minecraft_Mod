package com.renanpontes.isekaimahou.feature.market.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.item.SoulBinding;
import com.renanpontes.isekaimahou.feature.market.MarketNet;
import com.renanpontes.isekaimahou.feature.market.MarketService;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Mercado" do menu B — leilão entre jogadores, em três modos:
 * <ul>
 *   <li><b>Comprar</b>: lista de anúncios com busca e ordem; comprar pede um segundo clique (confirmação).</li>
 *   <li><b>Vender</b>: escolha um item do inventário, a quantidade e o preço; à direita, os seus anúncios (cancelar).</li>
 *   <li><b>Retirar</b>: moedas das vendas e itens devolvidos/comprados esperando na caixa.</li>
 * </ul>
 * Tudo é validado no servidor; a aba só pede. A lista se atualiza sozinha a cada 2 s enquanto a aba está aberta
 * (o servidor só responde quando algo mudou).
 */
public class MarketTab implements MenuTab {
    private static final int MODE_BUY = 0, MODE_SELL = 1, MODE_BOX = 2;
    private static final int ROW_H = 21, CELL = 18;
    private static final int GREEN = 0xFF7CFF8A, RED = 0xFFFF6B6B, MUTED = 0xFF8A82A0, EDGE = 0xFF6E5A9A, BLUE = 0xFF8FD3FF;

    // Estado que sobrevive à remontagem da aba (trocar de modo recria os campos de texto)
    private static int mode = MODE_BUY;
    private static String searchText = "";
    private static int sort;
    private static int selSlot = -1, qty = 1;
    private static String priceText = "";

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    private final List<Hit> hits = new ArrayList<>();
    @Nullable private List<Component> tooltip;
    private ItemStack tipStack = ItemStack.EMPTY;
    private StatusScreen screen;
    private int x, y, w, h, mouseX, mouseY;
    @Nullable private EditBox search, price;
    private float scroll, mineScroll;
    private int content, mineContent;
    /** Faixa visível da lista rolável (chips fora dela não recebem clique). */
    private int clipTop = Integer.MIN_VALUE, clipBottom = Integer.MAX_VALUE;
    /** Confirmação de compra/cancelamento: id do anúncio e até quando vale o segundo clique. */
    private long confirmId = -1, confirmUntil;
    private int ticks;
    /** Lista filtrada (refeita quando a lista, a busca ou a ordem mudam). */
    private List<MarketNet.Entry> view = List.of();
    private int viewRevision = -1, viewSort = -1;
    private String viewQuery = "";

    // =====================================================================================
    //  Montagem / entrada
    // =====================================================================================
    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.screen = screen;
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        Font font = Minecraft.getInstance().font;
        search = null;
        price = null;
        if (mode == MODE_BUY) {
            search = screen.addTabWidget(new EditBox(font, x + 6, y + 19, Math.min(150, w / 3), 13, tr("search")));
            search.setMaxLength(32);
            search.setValue(searchText);
            search.setHint(tr("search").withStyle(ChatFormatting.DARK_GRAY));
            search.setResponder(s -> {
                searchText = s;
                scroll = 0;
            });
        } else if (mode == MODE_SELL) {
            price = screen.addTabWidget(new EditBox(font, x + 48, formTop() + 33, 40, 13, tr("price")));
            price.setMaxLength(4);
            price.setFilter(s -> s.chars().allMatch(c -> c >= '0' && c <= '9'));
            price.setValue(priceText);
            price.setResponder(s -> priceText = s);
            price.visible = selSlot >= 0;
        }
        MarketClientState.query();
    }

    @Override
    public void tick() {
        if (++ticks % 40 == 0) MarketClientState.query();
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            if (hits.get(i).contains(mx, my)) {
                hits.get(i).action().run();
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (mx < x || mx >= x + w || my < y || my >= y + h) return false;
        if (mode == MODE_BUY) {
            scroll = Mth.clamp(scroll - (float) sy * ROW_H, 0F, Math.max(0, content - listHeight()));
            return true;
        }
        if (mode == MODE_SELL && mx >= x + leftW()) {
            mineScroll = Mth.clamp(mineScroll - (float) sy * ROW_H, 0F, Math.max(0, mineContent - (h - 36)));
            return true;
        }
        return false;
    }

    private void setMode(int m) {
        if (mode == m) return;
        mode = m;
        confirmId = -1;
        screen.refreshTab();
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (me == null) return;
        Font font = mc.font;
        hits.clear();
        tooltip = null;
        tipStack = ItemStack.EMPTY;
        mouseX = mx;
        mouseY = my;
        if (confirmId >= 0 && System.currentTimeMillis() > confirmUntil) confirmId = -1;

        panel(g, x, y, w, h, MahouUi.GOLD);
        header(g, font, me);
        switch (mode) {
            case MODE_SELL -> renderSell(g, font, me);
            case MODE_BOX -> renderBox(g, font);
            default -> renderBuy(g, font, me);
        }
        resultLine(g, font);
        if (!tipStack.isEmpty()) g.renderTooltip(font, tipStack, mx, my);
        else if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    /** Modos à esquerda; à direita, quanto o jogador pode gastar (inventário + saldo do Mercado). */
    private void header(GuiGraphics g, Font font, LocalPlayer me) {
        int cx = x + 6;
        cx = tabChip(g, font, cx, y + 4, tr("mode.buy"), MODE_BUY);
        cx = tabChip(g, font, cx + 3, y + 4, tr("mode.sell"), MODE_SELL);
        int waiting = MarketClientState.boxItems.size() + (MarketClientState.boxCoins > 0 ? 1 : 0);
        tabChip(g, font, cx + 3, y + 4, waiting > 0 ? tr("mode.box_n", waiting) : tr("mode.box"), MODE_BOX);

        int total = MarketService.coinsIn(me) + MarketClientState.boxCoins;
        Component have = tr("have", total);
        int tw = font.width(have);
        int hx = x + w - 8 - tw;
        g.drawString(font, have, hx, y + 5, MahouUi.GOLD_PALE, false);
        coinIcon(g, hx - 10, y + 5);
        if (mouseX >= hx - 10 && mouseX < x + w - 6 && mouseY >= y + 3 && mouseY < y + 15) {
            tooltip = List.of(tr("have_tip", MarketService.coinsIn(me), MarketClientState.boxCoins));
        }
    }

    // ---------------------------------------------------------------- Comprar
    private int listTop() {
        return y + 36;
    }

    private int listHeight() {
        return h - 36 - 13;
    }

    private void renderBuy(GuiGraphics g, Font font, LocalPlayer me) {
        // Ordem (ao lado da busca)
        int sx = x + 6 + Math.min(150, w / 3) + 6;
        leftChip(g, font, sx, y + 20, tr("sort." + sort), BLUE, () -> {
            sort = (sort + 1) % 3;
            scroll = 0;
        }, tr("sort_tip"));

        refreshView();
        int top = listTop(), bottom = top + listHeight();
        if (view.isEmpty()) {
            Component empty = MarketClientState.listings.isEmpty() ? tr("empty") : tr("empty_search");
            g.drawCenteredString(font, empty, x + w / 2, top + 20, MUTED);
            return;
        }
        content = view.size() * ROW_H;
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - (bottom - top)));
        int total = MarketService.coinsIn(me) + MarketClientState.boxCoins;
        UUID myId = me.getUUID();
        g.enableScissor(x + 1, top, x + w - 1, bottom);
        clipTop = top;
        clipBottom = bottom;
        int yy = top - Math.round(scroll);
        for (MarketNet.Entry e : view) {
            if (yy + ROW_H > top && yy < bottom) row(g, font, e, x + 5, yy, w - 14, e.seller().equals(myId), total);
            yy += ROW_H;
        }
        g.disableScissor();
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
        scrollBar(g, x + w - 6, top, bottom, scroll, content);
    }

    private void refreshView() {
        String q = searchText.strip().toLowerCase(Locale.ROOT);
        if (viewRevision == MarketClientState.revision && viewSort == sort && viewQuery.equals(q)) return;
        viewRevision = MarketClientState.revision;
        viewSort = sort;
        viewQuery = q;
        List<MarketNet.Entry> out = new ArrayList<>();
        for (MarketNet.Entry e : MarketClientState.listings) {
            if (q.isEmpty() || e.stack().getHoverName().getString().toLowerCase(Locale.ROOT).contains(q)
                    || e.sellerName().toLowerCase(Locale.ROOT).contains(q)) out.add(e);
        }
        Comparator<MarketNet.Entry> newest = Comparator.comparingLong(MarketNet.Entry::id).reversed();
        switch (sort) {
            case 1 -> out.sort(Comparator.comparingInt(MarketNet.Entry::price).thenComparing(newest));
            case 2 -> out.sort(Comparator.comparingInt(MarketNet.Entry::price).reversed().thenComparing(newest));
            default -> out.sort(newest);
        }
        view = out;
    }

    /** Uma linha de anúncio: item, nome, vendedor + prazo, preço e o botão (Comprar / Cancelar se for seu). */
    private void row(GuiGraphics g, Font font, MarketNet.Entry e, int rx, int ry, int rw, boolean mine, int total) {
        boolean inClip = mouseY >= clipTop && mouseY < clipBottom;
        boolean hov = inClip && mouseX >= rx && mouseX < rx + rw && mouseY >= ry && mouseY < ry + ROW_H - 1;
        MahouUi.roundRect(g, rx, ry, rw, ROW_H - 1, hov ? 0x60302848 : 0x40000000, hov ? 0x40201830 : 0x28000000);
        g.renderItem(e.stack(), rx + 3, ry + 2);
        g.renderItemDecorations(font, e.stack(), rx + 3, ry + 2);
        if (hov && mouseX < rx + rw - 110) tipStack = e.stack();

        int tx = rx + 24;
        g.drawString(font, MahouUi.fit(font, e.stack().getHoverName(), rw - 150), tx, ry + 2, MahouUi.TEXT, false);
        Component sub = mine ? tr("row.mine", timeLeft(e.hoursLeft())) : tr("row.seller", e.sellerName(), timeLeft(e.hoursLeft()));
        MahouUi.smallText(g, font, sub, tx, ry + 12, 0.7F, mine ? GREEN : MahouUi.TEXT_DIM, false);

        // Direita: botão e, antes dele, o preço
        boolean confirming = confirmId == e.id();
        int bx;
        if (mine) {
            bx = chip(g, font, rx + rw - 4, ry + 5, confirming ? tr("confirm") : tr("cancel"), RED, () -> confirmOr(e, true),
                    tr("cancel_tip"));
        } else if (total < e.price()) {
            bx = chip(g, font, rx + rw - 4, ry + 5, tr("buy"), MUTED, () -> {}, tr("cant_afford", e.price() - total));
        } else {
            bx = chip(g, font, rx + rw - 4, ry + 5, confirming ? tr("confirm") : tr("buy"), confirming ? MahouUi.GOLD : GREEN,
                    () -> confirmOr(e, false), confirming ? null : tr("buy_tip", e.price()));
        }
        String p = String.valueOf(e.price());
        int pw = font.width(p);
        coinIcon(g, bx - 13, ry + 6);
        g.drawString(font, p, bx - 15 - pw, ry + 6, total >= e.price() || mine ? MahouUi.GOLD : 0xFFB08A6A, false);
    }

    /** Primeiro clique arma a confirmação (3 s); o segundo manda a ação. */
    private void confirmOr(MarketNet.Entry e, boolean cancel) {
        if (confirmId != e.id()) {
            confirmId = e.id();
            confirmUntil = System.currentTimeMillis() + 3000L;
            return;
        }
        confirmId = -1;
        PacketDistributor.sendToServer(cancel ? MarketNet.Op.cancel(e.id()) : MarketNet.Op.buy(e.id(), e.price()));
    }

    // ---------------------------------------------------------------- Vender
    private int leftW() {
        return 9 * CELL + 16;
    }

    private int gridTop() {
        return y + 20;
    }

    private int formTop() {
        return gridTop() + 4 * CELL + 6;
    }

    private void renderSell(GuiGraphics g, Font font, LocalPlayer me) {
        Inventory inv = me.getInventory();
        int gx = x + 8, gy = gridTop();
        // Inventário: 3 fileiras da mochila e, separada, a barra rápida
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 9; col++) {
                int slot = row < 3 ? 9 + row * 9 + col : col;
                int sx = gx + col * CELL, sy = gy + row * CELL + (row == 3 ? 3 : 0);
                slot(g, font, inv.items.get(slot), slot, sx, sy);
            }
        }
        ItemStack sel = selSlot >= 0 && selSlot < inv.items.size() ? inv.items.get(selSlot) : ItemStack.EMPTY;
        if (sel.isEmpty()) selSlot = -1;

        int fx = x + 8, fy = formTop() + 3;
        if (sel.isEmpty()) {
            for (var line : font.split(tr("sell.pick"), leftW() - 12)) {
                g.drawString(font, line, fx, fy + 2, MUTED, false);
                fy += 10;
            }
            if (price != null) price.visible = false;
        } else {
            if (price != null) price.visible = true;
            qty = Mth.clamp(qty, 1, sel.getCount());
            g.renderItem(sel, fx, fy - 2);
            g.drawString(font, MahouUi.fit(font, sel.getHoverName(), leftW() - 34), fx + 20, fy + 2, MahouUi.TEXT, false);
            // Quantidade
            int qy = fy + 16;
            g.drawString(font, tr("sell.qty"), fx, qy + 1, MahouUi.TEXT_DIM, false);
            int cx = fx + 40;
            cx = leftChip(g, font, cx, qy, Component.literal("-"), BLUE, () -> qty = Math.max(1, qty - (shift() ? 10 : 1)), null);
            String n = String.valueOf(qty);
            g.drawString(font, n, cx + 4, qy + 1, MahouUi.TEXT, false);
            cx += 8 + font.width(n);
            int max = sel.getCount();
            cx = leftChip(g, font, cx, qy, Component.literal("+"), BLUE, () -> qty = Math.min(max, qty + (shift() ? 10 : 1)), null);
            leftChip(g, font, cx + 3, qy, tr("sell.all"), BLUE, () -> qty = max, null);
            // Preço (o campo de texto fica ao lado do rótulo)
            int py = fy + 31;
            g.drawString(font, tr("sell.price"), fx, py + 1, MahouUi.TEXT_DIM, false);
            coinIcon(g, fx + 84, py + 1);
            int value = parsePrice();
            Component why = sellBlock(sel, value);
            chip(g, font, x + leftW() - 4, py, tr("sell.list"), why == null ? GREEN : MUTED, () -> {
                if (sellBlock(sel, parsePrice()) != null) return;
                PacketDistributor.sendToServer(MarketNet.Op.sell(selSlot, qty, parsePrice(), BuiltInRegistries.ITEM.getId(sel.getItem())));
                selSlot = -1;
            }, why);
            // Taxa
            if (value > 0) {
                int fee = MarketService.fee(value);
                MahouUi.smallText(g, font, tr("sell.fee", MarketService.FEE_PERCENT, fee, value - fee), fx, py + 16, 0.7F, MahouUi.TEXT_DIM, false);
            }
        }

        // Direita: meus anúncios
        int rx = x + leftW() + 2, rw = w - leftW() - 6;
        g.fill(rx - 3, y + 20, rx - 2, y + h - 14, MahouUi.alpha(EDGE, 0x60));
        List<MarketNet.Entry> mine = new ArrayList<>();
        for (MarketNet.Entry e : MarketClientState.listings) if (e.seller().equals(me.getUUID())) mine.add(e);
        g.drawString(font, tr("sell.mine", mine.size(), MarketService.MAX_PER_PLAYER).withStyle(ChatFormatting.BOLD), rx, y + 21, MahouUi.GOLD, false);
        int top = y + 33, bottom = y + h - 14;
        if (mine.isEmpty()) {
            int yy = top + 2;
            for (var line : font.split(tr("sell.none", MarketService.DURATION_DAYS, MarketService.FEE_PERCENT), rw - 4)) {
                g.drawString(font, line, rx, yy, MUTED, false);
                yy += 10;
            }
            return;
        }
        mineContent = mine.size() * ROW_H;
        mineScroll = Mth.clamp(mineScroll, 0, Math.max(0, mineContent - (bottom - top)));
        g.enableScissor(rx - 1, top, x + w - 1, bottom);
        clipTop = top;
        clipBottom = bottom;
        int yy = top - Math.round(mineScroll);
        for (MarketNet.Entry e : mine) {
            if (yy + ROW_H > top && yy < bottom) row(g, font, e, rx, yy, rw - 4, true, Integer.MAX_VALUE);
            yy += ROW_H;
        }
        g.disableScissor();
        clipTop = Integer.MIN_VALUE;
        clipBottom = Integer.MAX_VALUE;
        scrollBar(g, x + w - 6, top, bottom, mineScroll, mineContent);
    }

    /** Por que o anúncio não pode sair agora (null = pode). */
    @Nullable
    private Component sellBlock(ItemStack sel, int value) {
        if (sel.is(MarketService.coin())) return Component.translatable("market.isekai_mahou.err.no_coins_item");
        if (SoulBinding.isClassItem(sel) || SoulBinding.owner(sel) != null) return Component.translatable("market.isekai_mahou.err.bound");
        if (value < 1) return tr("sell.need_price");
        int mine = 0;
        LocalPlayer me = Minecraft.getInstance().player;
        if (me != null) for (MarketNet.Entry e : MarketClientState.listings) if (e.seller().equals(me.getUUID())) mine++;
        if (mine >= MarketService.MAX_PER_PLAYER) return Component.translatable("market.isekai_mahou.err.too_many", MarketService.MAX_PER_PLAYER);
        return null;
    }

    private static int parsePrice() {
        try {
            return priceText.isEmpty() ? 0 : Math.min(MarketService.MAX_PRICE, Integer.parseInt(priceText));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean shift() {
        return net.minecraft.client.gui.screens.Screen.hasShiftDown();
    }

    private void slot(GuiGraphics g, Font font, ItemStack stack, int slot, int sx, int sy) {
        boolean hov = mouseX >= sx && mouseX < sx + CELL - 1 && mouseY >= sy && mouseY < sy + CELL - 1;
        boolean selected = slot == selSlot;
        g.fill(sx, sy, sx + CELL - 1, sy + CELL - 1, selected ? 0x705A4A20 : hov ? 0x50403860 : 0x40000000);
        if (selected) g.renderOutline(sx - 1, sy - 1, CELL + 1, CELL + 1, MahouUi.GOLD);
        if (stack.isEmpty()) return;
        g.renderItem(stack, sx, sy);
        g.renderItemDecorations(font, stack, sx, sy);
        if (hov) tipStack = stack;
        hits.add(new Hit(sx, sy, CELL - 1, CELL - 1, () -> {
            selSlot = slot;
            qty = stack.getCount();
        }));
    }

    // ---------------------------------------------------------------- Retirar
    private void renderBox(GuiGraphics g, Font font) {
        int cx = x + 10, yy = y + 24;
        int coins = MarketClientState.boxCoins;
        List<ItemStack> items = MarketClientState.boxItems;
        if (coins <= 0 && items.isEmpty()) {
            g.drawCenteredString(font, tr("box.empty"), x + w / 2, y + 44, MUTED);
            for (var line : font.split(tr("box.how"), Math.min(w - 40, 300))) {
                g.drawCenteredString(font, line, x + w / 2, yy + 36, MahouUi.TEXT_DIM);
                yy += 10;
            }
            return;
        }
        coinIcon(g, cx, yy + 1);
        g.drawString(font, tr("box.balance", coins), cx + 11, yy + 1, MahouUi.GOLD, false);
        if (coins > 0) {
            chip(g, font, x + w - 10, yy, tr("box.take_coins"), GREEN, () -> collect(MarketNet.COLLECT_COINS), null);
        }
        MahouUi.smallText(g, font, tr("box.balance_tip"), cx, yy + 12, 0.7F, MahouUi.TEXT_DIM, false);
        yy += 26;
        g.drawString(font, tr("box.items", items.size()), cx, yy + 1, MahouUi.TEXT, false);
        if (!items.isEmpty()) {
            chip(g, font, x + w - 10, yy, tr("box.take_items"), GREEN, () -> collect(MarketNet.COLLECT_ITEMS), null);
        }
        yy += 14;
        int cols = Math.max(1, (w - 20) / CELL);
        int maxRows = Math.max(1, (y + h - 16 - yy) / CELL);
        for (int i = 0; i < items.size() && i < cols * maxRows; i++) {
            int sx = cx + (i % cols) * CELL, sy = yy + (i / cols) * CELL;
            boolean hov = mouseX >= sx && mouseX < sx + CELL - 1 && mouseY >= sy && mouseY < sy + CELL - 1;
            g.fill(sx, sy, sx + CELL - 1, sy + CELL - 1, hov ? 0x50403860 : 0x40000000);
            g.renderItem(items.get(i), sx, sy);
            g.renderItemDecorations(font, items.get(i), sx, sy);
            if (hov) tipStack = items.get(i);
        }
    }

    private static void collect(int what) {
        PacketDistributor.sendToServer(MarketNet.Op.collect(what));
    }

    // =====================================================================================
    //  Peças
    // =====================================================================================
    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable("market.isekai_mahou.ui." + key, args);
    }

    private static Component timeLeft(int hours) {
        return hours >= 48 ? tr("days", hours / 24) : tr("hours", hours);
    }

    /** Última resposta do servidor, por 6 s, no rodapé. */
    private void resultLine(GuiGraphics g, Font font) {
        Component r = MarketClientState.result;
        if (r == null) return;
        long age = System.currentTimeMillis() - MarketClientState.resultAt;
        if (age > 6000L) return;
        int a = age > 5000L ? (int) (255 * (6000L - age) / 1000L) : 255;
        if (a < 8) return;
        int color = MahouUi.alpha(MarketClientState.resultOk ? GREEN : RED, a);
        g.drawString(font, MahouUi.fit(font, r, w - 16), x + 8, y + h - 11, color, false);
    }

    /** Moeda miúda (8×8) para acompanhar os preços. */
    private static void coinIcon(GuiGraphics g, int cx, int cy) {
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(0.5F, 0.5F, 1F);
        g.renderItem(new ItemStack(MarketService.coin()), 0, 0);
        g.pose().popPose();
    }

    /** Chip de modo (aceso no modo atual). Devolve o x da direita. */
    private int tabChip(GuiGraphics g, Font font, int cx, int cy, Component label, int m) {
        boolean on = mode == m;
        int cw = font.width(label) + 10, ch = 12;
        boolean hov = mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;
        int color = on ? MahouUi.GOLD : EDGE;
        MahouUi.roundRect(g, cx, cy, cw, ch, MahouUi.alpha(color, on ? 0x60 : hov ? 0x50 : 0x28), MahouUi.alpha(color, on ? 0x30 : 0x14));
        MahouUi.roundOutline(g, cx, cy, cw, ch, MahouUi.alpha(color, on || hov ? 0xFF : 0xA0));
        g.drawString(font, label, cx + 5, cy + 2, on ? MahouUi.GOLD_PALE : hov ? 0xFFFFFFFF : MahouUi.TEXT_DIM, false);
        if (!on) hits.add(new Hit(cx, cy, cw, ch, () -> setMode(m)));
        return cx + cw;
    }

    /** Botãozinho alinhado pela DIREITA em {@code right}. Devolve o x da esquerda. */
    private int chip(GuiGraphics g, Font font, int right, int cy, Component label, int color, Runnable action, @Nullable Component tip) {
        int cw = Math.round(font.width(label) * 0.75F) + 8;
        drawChip(g, font, right - cw, cy, cw, label, color, action, tip);
        return right - cw;
    }

    /** Botãozinho alinhado pela ESQUERDA em {@code cx}. Devolve o x da direita. */
    private int leftChip(GuiGraphics g, Font font, int cx, int cy, Component label, int color, Runnable action, @Nullable Component tip) {
        int cw = Math.round(font.width(label) * 0.75F) + 8;
        drawChip(g, font, cx, cy, cw, label, color, action, tip);
        return cx + cw;
    }

    private void drawChip(GuiGraphics g, Font font, int cx, int cy, int cw, Component label, int color, Runnable action, @Nullable Component tip) {
        int ch = 10;
        boolean visible = cy >= clipTop && cy + ch <= clipBottom;
        boolean hov = visible && mouseX >= cx && mouseX < cx + cw && mouseY >= cy && mouseY < cy + ch;
        MahouUi.roundRect(g, cx, cy, cw, ch, MahouUi.alpha(color, hov ? 0x70 : 0x38), MahouUi.alpha(color, hov ? 0x40 : 0x18));
        MahouUi.roundOutline(g, cx, cy, cw, ch, MahouUi.alpha(color, hov ? 0xFF : 0xA0));
        MahouUi.smallText(g, font, label, cx + 4, cy + 2, 0.75F, hov ? 0xFFFFFFFF : MahouUi.lighten(color, 0.3F), false);
        if (visible) hits.add(new Hit(cx, cy, cw, ch, action));
        if (hov && tip != null) tooltip = List.of(tip);
    }

    private static void scrollBar(GuiGraphics g, int sx, int top, int bottom, float scroll, int content) {
        int view = bottom - top;
        if (content <= view) return;
        int bar = Math.max(10, view * view / content);
        int off = Math.round((view - bar) * scroll / (content - view));
        g.fill(sx, top, sx + 2, bottom, 0x40000000);
        g.fill(sx, top + off, sx + 2, top + off + bar, MahouUi.alpha(MahouUi.GOLD_DIM, 0xD0));
    }

    /** Só a moldura: a aba é desenhada DEPOIS dos campos de texto, então um fundo cheio os cobriria. */
    private static void panel(GuiGraphics g, int px, int py, int pw, int ph, int accent) {
        g.renderOutline(px, py, pw, ph, MahouUi.alpha(EDGE, 0x8C));
        MahouUi.brackets(g, px - 1, py - 1, pw + 2, ph + 2, 5, MahouUi.lighten(accent, 0.2F));
    }
}
