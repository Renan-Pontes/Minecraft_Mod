package com.renanpontes.isekaimahou.feature.travel.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.math.Axis;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.onboard.ClientTravel;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * Conteúdo da tela "Viagem", usado pela tela da Pedra de Teleporte ({@link TravelScreen}) e pela aba do menu B
 * ({@link TravelTab}): mapa de cima (norte para cima, você no centro, pontos coloridos por tipo, rolar = zoom), lista
 * com nome, tipo, distância e custo, e o botão Viajar. A lista vem do servidor ({@link ClientTravel}).
 */
final class TravelView {
    private static final int HEADER_H = 28, ROW_H = 22, GROUP_H = 13, FOOTER_H = 34;
    private static final int OK = 0xFF7CE07C, BAD = 0xFFE06A6A, WARN = 0xFFFFC56A;
    private static final String[] TYPE_ORDER = {"capital", "city", "village", Travel.WAYSTONE_TYPE, Travel.HUB_TYPE};

    /** A tela de viagem aberta agora (para o resultado do pedido). */
    @Nullable
    private static TravelView active;

    private final boolean stoneMode;
    private int x, y, w, h;
    private int mapX, mapY, mapS, listX, listY, listW, listH;
    private Button go;

    private List<ClientTravel.Destination> dests = List.of();
    private final List<Row> rows = new ArrayList<>();
    /** "Em combate" do núcleo dura 5 s desde o último golpe: depois disso o botão volta (o servidor confere). */
    private static final long COMBAT_RETRY_MS = 5500;

    private int seenVersion = Integer.MIN_VALUE;
    /** Quando esta tela recebeu a lista atual (ms). */
    private long listAt;
    private boolean loaded;
    @Nullable
    private String selectedId;
    private double scroll;
    private float zoom = 1F;
    private boolean waiting;
    private long waitingSince;
    @Nullable
    private Component flash;
    private long flashUntil;
    private long lastClickAt;
    @Nullable
    private String lastClickId;
    private boolean cooldownWasRunning;
    @Nullable
    private List<Component> tooltip;

    /** Linha da lista: cabeçalho de dimensão ou destino. */
    private record Row(@Nullable ResourceKey<Level> group, @Nullable ClientTravel.Destination dest, int top, int height) {}

    TravelView(boolean stoneMode) {
        this.stoneMode = stoneMode;
    }

    // =====================================================================================
    //  Ciclo de vida
    // =====================================================================================
    void init(int x, int y, int w, int h, Consumer<Button> addWidget) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        int bodyH = h - HEADER_H;
        mapS = Mth.clamp(Math.min(bodyH - 14, (int) (w * 0.44F)), 60, 200);
        mapX = x + 2;
        mapY = y + HEADER_H;
        listX = mapX + mapS + 8;
        listW = x + w - listX - 2;
        listY = mapY;
        listH = Math.max(ROW_H, y + h - FOOTER_H - listY);
        go = Button.builder(Component.empty(), b -> travel()).bounds(listX, y + h - 21, listW, 20).build();
        addWidget.accept(go);
        active = this;
        seenVersion = Integer.MIN_VALUE;
        refresh();
    }

    void removed() {
        if (active == this) active = null;
    }

    void tick() {
        refresh();
        if (waiting && Util.getMillis() - waitingSince > 5000) waiting = false; // sem resposta: libera o botão
        if (!stoneMode) {
            // Recarga terminou com a aba aberta: pede a lista de novo (os destinos ficam disponíveis)
            boolean running = ClientTravel.cooldownLeft() > 0;
            if (cooldownWasRunning && !running) ClientTravel.requestMenu();
            cooldownWasRunning = running;
        }
        updateButton();
    }

    /** A lista do servidor chegou ou mudou: reconstrói (só se for do mesmo tipo desta tela). */
    private void refresh() {
        int v = ClientTravel.version();
        if (v == seenVersion) return;
        seenVersion = v;
        ClientTravel.Menu m = ClientTravel.menu();
        loaded = m != null && m.fromStone() == stoneMode;
        dests = loaded ? m.destinations() : List.of();
        listAt = Util.getMillis();
        rebuildRows();
        if (selectedId == null || dests.stream().noneMatch(d -> d.id().equals(selectedId))) {
            selectedId = dests.stream().filter(this::ready).findFirst()
                    .or(() -> dests.stream().findFirst()).map(ClientTravel.Destination::id).orElse(null);
        }
        waiting = false;
        updateButton();
    }

    private void rebuildRows() {
        rows.clear();
        Map<ResourceKey<Level>, List<ClientTravel.Destination>> groups = new LinkedHashMap<>();
        for (ClientTravel.Destination d : dests) groups.computeIfAbsent(d.dim(), k -> new ArrayList<>()).add(d);
        boolean headers = groups.size() > 1;
        int top = 0;
        for (Map.Entry<ResourceKey<Level>, List<ClientTravel.Destination>> e : groups.entrySet()) {
            if (headers) {
                rows.add(new Row(e.getKey(), null, top, GROUP_H));
                top += GROUP_H;
            }
            for (ClientTravel.Destination d : e.getValue()) {
                rows.add(new Row(null, d, top, ROW_H));
                top += ROW_H;
            }
        }
    }

    private int contentHeight() {
        return rows.isEmpty() ? 0 : rows.get(rows.size() - 1).top() + rows.get(rows.size() - 1).height();
    }

    /**
     * Estado do destino AGORA. A lista é do momento em que chegou (a da pedra não pode ser pedida de novo sem virar
     * viagem de menu), então o que muda com o tempo é recalculado aqui: o recurso (a barra enche/esvazia com a tela
     * aberta — "recurso insuficiente" é a última regra do núcleo, então as outras já passaram) e o "em combate" (só
     * 5 s; depois disso o botão volta e o servidor confere de novo).
     */
    private Travel.TravelResult status(ClientTravel.Destination d) {
        Travel.TravelResult s = d.status();
        boolean enough = d.cost() <= 0 || ClientMagic.mana + 0.001F >= d.cost();
        return switch (s) {
            case OK, NO_RESOURCE -> enough ? Travel.TravelResult.OK : Travel.TravelResult.NO_RESOURCE;
            case IN_COMBAT -> enough && Util.getMillis() - listAt > COMBAT_RETRY_MS ? Travel.TravelResult.OK : s;
            default -> s;
        };
    }

    private boolean ready(ClientTravel.Destination d) {
        return status(d) == Travel.TravelResult.OK;
    }

    @Nullable
    private ClientTravel.Destination selected() {
        if (selectedId == null) return null;
        for (ClientTravel.Destination d : dests) if (d.id().equals(selectedId)) return d;
        return null;
    }

    private void updateButton() {
        if (go == null) return;
        ClientTravel.Destination d = selected();
        if (!loaded) {
            go.active = false;
            go.setMessage(Component.translatable("screen.isekai_mahou.travel.loading"));
        } else if (d == null) {
            go.active = false;
            go.setMessage(Component.translatable("screen.isekai_mahou.travel.pick"));
        } else if (waiting) {
            go.active = false;
            go.setMessage(Component.translatable("screen.isekai_mahou.travel.going"));
        } else if (ready(d)) {
            go.active = true;
            go.setMessage(d.cost() <= 0 ? Component.translatable("screen.isekai_mahou.travel.go_free")
                    : Component.translatable("screen.isekai_mahou.travel.go", d.cost(), resourceName()));
        } else {
            go.active = false;
            go.setMessage(shortStatus(status(d)));
        }
    }

    private void travel() {
        ClientTravel.Destination d = selected();
        if (d == null || !ready(d) || waiting) return;
        waiting = true;
        waitingSince = Util.getMillis();
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.3F, 0.9F));
        ClientTravel.travel(d.id());
        updateButton();
    }

    /** Resultado de um pedido de viagem (registrado no init do cliente). */
    static void onOutcome(String placeId, Travel.TravelResult result) {
        TravelView v = active;
        if (v == null) return;
        if (result.success()) {
            Minecraft.getInstance().setScreen(null);
            return;
        }
        v.waiting = false;
        v.flash = result.message();
        v.flashUntil = Util.getMillis() + 4000;
        v.listAt = Util.getMillis(); // "em combate" de novo: espera mais 5 s antes de liberar o botão
        // A lista da aba é pedida de novo para os estados ficarem certos (a da pedra não: viraria viagem de menu)
        if (!v.stoneMode) ClientTravel.requestMenu();
        v.updateButton();
    }

    static void reset() {
        active = null;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    void render(GuiGraphics g, int mx, int my, float pt) {
        Font font = Minecraft.getInstance().font;
        tooltip = null;
        renderHeader(g, font);
        if (!loaded) {
            renderNotice(g, font, Component.translatable("screen.isekai_mahou.travel.loading"), null);
            return;
        }
        if (dests.isEmpty()) {
            renderNotice(g, font, Component.translatable("screen.isekai_mahou.travel.empty_title"),
                    Component.translatable("screen.isekai_mahou.travel.empty_hint"));
            return;
        }
        renderMap(g, font, mx, my);
        renderList(g, font, mx, my);
        renderFooter(g, font, mx, my);
        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mx, my);
    }

    private Component resourceName() {
        ClientTravel.Menu m = ClientTravel.menu();
        return m != null ? m.resourceName() : Component.translatable("screen.isekai_mahou.travel.resource");
    }

    private int resourceColor() {
        ClientTravel.Menu m = ClientTravel.menu();
        return 0xFF000000 | (m != null ? m.resourceRgb() : 0x5AA8FF);
    }

    private void renderHeader(GuiGraphics g, Font font) {
        Component title = Component.translatable(stoneMode ? "screen.isekai_mahou.travel.title_stone" : "screen.isekai_mahou.travel.title")
                .withStyle(ChatFormatting.BOLD);
        g.drawString(font, title, x + 4, y + 2, MahouUi.GOLD, true);
        Component sub = Component.translatable(stoneMode ? "screen.isekai_mahou.travel.sub_stone" : "screen.isekai_mahou.travel.sub_menu");
        int rightW = Mth.clamp(w / 4, 80, 128);
        g.pose().pushPose();
        g.pose().translate(x + 4, y + 13, 0);
        g.pose().scale(0.75F, 0.75F, 1F);
        g.drawString(font, MahouUi.fit(font, sub, Math.max(40, (int) ((w - rightW - 12) / 0.75F))), 0, 0, MahouUi.TEXT_DIM, false);
        g.pose().popPose();

        // Recurso (Mana/Vigor/Foco) à direita
        int rx = x + w - rightW - 2;
        int col = resourceColor();
        float frac = ClientMagic.maxMana <= 0 ? 0 : ClientMagic.mana / ClientMagic.maxMana;
        MutableComponent res = resourceName().copy().append(Component.literal(" " + (int) ClientMagic.mana + "/" + ClientMagic.maxMana));
        MahouUi.smallText(g, font, res, rx, y + 1, 0.75F, col, false);
        MahouUi.bar(g, rx, y + 9, rightW, 3, frac, col);
        if (!stoneMode) {
            int left = ClientTravel.cooldownLeft();
            ClientTravel.Menu m = ClientTravel.menu();
            int total = m != null && m.cooldownTotal() > 0 ? m.cooldownTotal() : Travel.MENU_COOLDOWN_TICKS;
            Component cd = left > 0 ? Component.translatable("screen.isekai_mahou.travel.cooldown", time(left))
                    : Component.translatable("screen.isekai_mahou.travel.ready");
            MahouUi.smallText(g, font, cd, rx, y + 14, 0.75F, left > 0 ? WARN : OK, false);
            MahouUi.bar(g, rx, y + 22, rightW, 2, 1F - (float) left / total, left > 0 ? WARN : OK);
        }
        g.fill(x + 2, y + HEADER_H - 3, x + w - 2, y + HEADER_H - 2, MahouUi.alpha(MahouUi.GOLD_DIM, 0x90));
    }

    private void renderNotice(GuiGraphics g, Font font, Component title, @Nullable Component hint) {
        int bw = Math.min(w - 20, 320), bx = x + (w - bw) / 2, by = y + HEADER_H + (h - HEADER_H) / 2 - 34;
        MahouUi.roundRect(g, bx, by, bw, 68, 0xC0182030, 0xC00A1018);
        MahouUi.roundOutline(g, bx, by, bw, 68, MahouUi.alpha(0xFF6FE3FF, 0xA0));
        g.drawCenteredString(font, title, bx + bw / 2, by + 10, MahouUi.GOLD);
        if (hint == null) return;
        int ly = by + 26;
        for (var line : font.split(hint, bw - 16)) {
            g.drawString(font, line, bx + 8, ly, MahouUi.TEXT_DIM, false);
            ly += 10;
        }
    }

    // ---------------------------------------------------------------- Mapa
    private double range(LocalPlayer p) {
        double far = 0;
        for (ClientTravel.Destination d : dests) {
            if (!d.dim().equals(p.level().dimension())) continue;
            far = Math.max(far, Math.hypot(d.pos().getX() - p.getX(), d.pos().getZ() - p.getZ()));
        }
        return Math.max(96, far * 1.15) / zoom;
    }

    private void renderMap(GuiGraphics g, Font font, int mx, int my) {
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        int cx = mapX + mapS / 2, cy = mapY + mapS / 2, radius = mapS / 2 - 6;
        MahouUi.roundRect(g, mapX, mapY, mapS, mapS, 0xF0122236, 0xF0081220);
        g.enableScissor(mapX + 1, mapY + 1, mapX + mapS - 1, mapY + mapS - 1);

        double range = range(p);
        double scale = radius / range;
        // Grade alinhada ao mundo (passo "redondo")
        double step = niceStep(range / 2);
        int gridCol = 0x16FFFFFF;
        double startX = Math.floor((p.getX() - range * 1.5) / step) * step;
        for (double wx = startX; wx <= p.getX() + range * 1.5; wx += step) {
            int sx = cx + (int) Math.round((wx - p.getX()) * scale);
            g.fill(sx, mapY + 1, sx + 1, mapY + mapS - 1, gridCol);
        }
        double startZ = Math.floor((p.getZ() - range * 1.5) / step) * step;
        for (double wz = startZ; wz <= p.getZ() + range * 1.5; wz += step) {
            int sy = cy + (int) Math.round((wz - p.getZ()) * scale);
            g.fill(mapX + 1, sy, mapX + mapS - 1, sy + 1, gridCol);
        }
        // Anéis de alcance
        ring(g, cx, cy, radius / 2, 0x30A8E8FF);
        ring(g, cx, cy, radius, 0x50A8E8FF);

        // Destinos desta dimensão
        long now = Util.getMillis();
        ClientTravel.Destination hover = null;
        int others = 0;
        ClientTravel.Destination sel = selected();
        int[] selPos = null;
        for (ClientTravel.Destination d : dests) {
            if (!d.dim().equals(p.level().dimension())) {
                others++;
                continue;
            }
            double dx = (d.pos().getX() + 0.5 - p.getX()) * scale, dz = (d.pos().getZ() + 0.5 - p.getZ()) * scale;
            double len = Math.hypot(dx, dz);
            boolean clamped = len > radius;
            if (clamped) {
                dx = dx / len * radius;
                dz = dz / len * radius;
            }
            int sx = cx + (int) Math.round(dx), sy = cy + (int) Math.round(dz);
            int col = typeColor(d);
            boolean usable = ready(d) || status(d) == Travel.TravelResult.ALREADY_HERE;
            int fill = usable ? col : MahouUi.darken(col, 0.45F);
            if (clamped) {
                MahouUi.diamond(g, sx, sy, 2, MahouUi.alpha(fill, 0xB0));
            } else {
                MahouUi.diamond(g, sx, sy, 4, 0xFF0A0F18);
                MahouUi.diamond(g, sx, sy, 3, fill);
                g.fill(sx - 1, sy - 1, sx, sy, MahouUi.lighten(fill, 0.5F));
            }
            if (d == sel) selPos = new int[]{sx, sy};
            if (Math.abs(mx - sx) <= 4 && Math.abs(my - sy) <= 4) hover = d;
        }
        // Destaque pulsante no escolhido + nome
        if (sel != null && selPos != null) {
            float pulse = 0.5F + 0.5F * Mth.sin(now / 180F);
            int r = 6 + Math.round(pulse * 2);
            int col = MahouUi.alpha(MahouUi.lighten(typeColor(sel), 0.3F), (int) (140 + 115 * pulse));
            g.renderOutline(selPos[0] - r, selPos[1] - r, r * 2 + 1, r * 2 + 1, col);
            Component name = sel.name();
            int tw = Math.min(font.width(name), mapS - 8);
            int lx = Mth.clamp(selPos[0] - tw / 2, mapX + 3, mapX + mapS - 3 - tw);
            int ly = selPos[1] - r - 11 < mapY + 2 ? selPos[1] + r + 3 : selPos[1] - r - 11;
            g.fill(lx - 2, ly - 1, lx + tw + 2, ly + 9, 0xB0000000);
            g.drawString(font, MahouUi.fit(font, name, tw), lx, ly, 0xFFFFFFFF, false);
        }
        // Você (seta na direção do olhar)
        g.pose().pushPose();
        g.pose().translate(cx + 0.5F, cy + 0.5F, 0);
        g.pose().mulPose(Axis.ZP.rotationDegrees(p.getYRot() + 180F));
        g.fill(-1, -5, 1, -3, 0xFFFFFFFF);
        g.fill(-2, -3, 2, -1, 0xFFFFFFFF);
        g.fill(-3, -1, 3, 2, 0xFFFFFFFF);
        g.fill(-1, 1, 1, 3, 0xFF1A2230);
        g.pose().popPose();
        g.disableScissor();

        // Moldura, rosa dos ventos e avisos
        MahouUi.roundOutline(g, mapX, mapY, mapS, mapS, MahouUi.GOLD_DIM);
        MahouUi.brackets(g, mapX + 2, mapY + 2, mapS - 4, mapS - 4, 5, MahouUi.alpha(0xFF6FE3FF, 0xA0));
        g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.travel.north"), cx, mapY + 3, MahouUi.GOLD);
        MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.travel.scale", (int) Math.round(step)),
                mapX + 4, mapY + mapS - 8, 0.6F, MahouUi.TEXT_DIM, false);
        if (others > 0) {
            Component o = Component.translatable("screen.isekai_mahou.travel.other_dims", others);
            float ow = font.width(o) * 0.6F;
            MahouUi.smallText(g, font, o, mapX + mapS - 4 - ow, mapY + mapS - 8, 0.6F, 0xFFC77DFF, false);
        }
        renderLegend(g, font);

        if (hover != null) tooltip = destTooltip(hover, true);
        else if (mx >= mapX && mx < mapX + mapS && my >= mapY && my < mapY + mapS) {
            tooltip = List.of(Component.translatable("screen.isekai_mahou.travel.map_hint").withStyle(ChatFormatting.GRAY));
        }
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        int n = Math.max(24, r * 3);
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            int px = cx + (int) Math.round(Math.cos(a) * r), py = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(px, py, px + 1, py + 1, color);
        }
    }

    private static double niceStep(double target) {
        double[] steps = {16, 32, 64, 128, 256, 512, 1000, 2000, 5000, 10000, 25000, 50000};
        for (double s : steps) if (s >= target) return s;
        return steps[steps.length - 1];
    }

    private void renderLegend(GuiGraphics g, Font font) {
        int lx = mapX + 1, ly = mapY + mapS + 4;
        int maxX = mapX + mapS;
        for (String type : TYPE_ORDER) {
            boolean present = dests.stream().anyMatch(d -> d.type().equals(type));
            if (!present) continue;
            Component label = typeLabel(type);
            int lw = 8 + Math.round(font.width(label) * 0.6F) + 5;
            if (lx + lw > maxX) break;
            MahouUi.diamond(g, lx + 3, ly + 3, 2, typeColor(type, 0x6FE3FF));
            MahouUi.smallText(g, font, label, lx + 8, ly + 1, 0.6F, MahouUi.TEXT_DIM, false);
            lx += lw;
        }
    }

    // ---------------------------------------------------------------- Lista
    private void renderList(GuiGraphics g, Font font, int mx, int my) {
        int content = contentHeight();
        scroll = Mth.clamp(scroll, 0, Math.max(0, content - listH));
        MahouUi.roundRect(g, listX - 2, listY, listW + 4, listH, 0x80141022, 0x800A0812);
        g.enableScissor(listX - 1, listY + 1, listX + listW + 1, listY + listH - 1);
        LocalPlayer p = Minecraft.getInstance().player;
        long now = Util.getMillis();
        int barW = content > listH ? 4 : 0;
        for (Row row : rows) {
            int ry = listY + row.top() - (int) scroll;
            if (ry + row.height() < listY || ry > listY + listH) continue;
            if (row.group() != null) {
                Component dn = dimName(row.group());
                g.fill(listX, ry + GROUP_H - 3, listX + listW - barW, ry + GROUP_H - 2, MahouUi.alpha(MahouUi.GOLD_DIM, 0x80));
                MahouUi.smallText(g, font, dn, listX + 2, ry + 3, 0.7F, MahouUi.GOLD_PALE, false);
                continue;
            }
            ClientTravel.Destination d = row.dest();
            if (d == null) continue;
            boolean sel = d.id().equals(selectedId);
            boolean hov = mx >= listX && mx < listX + listW - barW && my >= ry && my < ry + ROW_H && my >= listY && my < listY + listH;
            int col = typeColor(d);
            if (sel) {
                float pulse = 0.5F + 0.5F * Mth.sin(now / 300F);
                MahouUi.roundRect(g, listX, ry + 1, listW - barW, ROW_H - 2, MahouUi.alpha(col, (int) (70 + 30 * pulse)), MahouUi.alpha(col, 24));
                MahouUi.roundOutline(g, listX, ry + 1, listW - barW, ROW_H - 2, MahouUi.alpha(MahouUi.lighten(col, 0.3F), 0xD0));
            } else if (hov) {
                g.fill(listX, ry + 1, listX + listW - barW, ry + ROW_H - 1, 0x22FFFFFF);
            }
            boolean usable = ready(d);
            MahouUi.diamond(g, listX + 7, ry + 11, 4, 0xFF0A0F18);
            MahouUi.diamond(g, listX + 7, ry + 11, 3, usable ? col : MahouUi.darken(col, 0.45F));

            // custo à direita (vermelho se faltar recurso)
            int costW = 0;
            if (d.cost() > 0) {
                Component cost = Component.literal(String.valueOf(d.cost()));
                int cc = ClientMagic.mana + 0.001F < d.cost() ? BAD : resourceColor();
                costW = font.width(cost);
                g.drawString(font, cost, listX + listW - barW - 4 - costW, ry + 3, cc, true);
            }
            int nameW = listW - barW - 18 - costW - 8;
            g.drawString(font, MahouUi.fit(font, d.name(), nameW), listX + 15, ry + 3, usable ? MahouUi.TEXT : MahouUi.TEXT_DIM, false);

            MutableComponent info = typeLabel(d.type()).copy().append(" · ").append(distance(p, d));
            MahouUi.smallText(g, font, info, listX + 15, ry + 13, 0.65F, MahouUi.TEXT_DIM, false);
            if (!usable) {
                Component st = shortStatus(status(d));
                float sw = font.width(st) * 0.65F;
                int sc = status(d) == Travel.TravelResult.ALREADY_HERE ? MahouUi.GOLD : BAD;
                MahouUi.smallText(g, font, st, listX + listW - barW - 4 - sw, ry + 13, 0.65F, sc, false);
            }
            if (hov) tooltip = destTooltip(d, false);
        }
        g.disableScissor();
        if (barW > 0) {
            int trackX = listX + listW - 3;
            g.fill(trackX, listY + 2, trackX + 2, listY + listH - 2, 0x40FFFFFF);
            int bh = Math.max(10, (listH - 4) * listH / content);
            int by = listY + 2 + (int) ((listH - 4 - bh) * (scroll / Math.max(1, content - listH)));
            g.fill(trackX, by, trackX + 2, by + bh, MahouUi.GOLD);
        }
    }

    private void renderFooter(GuiGraphics g, Font font, int mx, int my) {
        int fy = y + h - FOOTER_H + 2;
        if (flash != null && Util.getMillis() < flashUntil) {
            g.drawCenteredString(font, MahouUi.fit(font, flash, listW), listX + listW / 2, fy + 1, BAD);
        } else if (stoneMode) {
            MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.travel.tip_stone"), listX, fy + 2, 0.65F,
                    MahouUi.TEXT_DIM, false);
        } else {
            MahouUi.smallText(g, font, Component.translatable("screen.isekai_mahou.travel.tip_menu"), listX, fy + 2, 0.65F,
                    MahouUi.TEXT_DIM, false);
        }
        ClientTravel.Destination d = selected();
        if (go != null && !go.active && d != null && !waiting && mx >= go.getX() && mx < go.getX() + go.getWidth()
                && my >= go.getY() && my < go.getY() + go.getHeight()) {
            tooltip = destTooltip(d, false);
        }
    }

    // ---------------------------------------------------------------- Textos
    private List<Component> destTooltip(ClientTravel.Destination d, boolean fromMap) {
        List<Component> lines = new ArrayList<>();
        lines.add(d.name().copy().withStyle(s -> s.withColor(typeColor(d) & 0xFFFFFF).withBold(true)));
        LocalPlayer p = Minecraft.getInstance().player;
        lines.add(typeLabel(d.type()).copy().append(" · ").append(distance(p, d)).withStyle(ChatFormatting.GRAY));
        if (d.cost() > 0) {
            lines.add(Component.translatable("screen.isekai_mahou.travel.cost_line", d.cost(), resourceName())
                    .withStyle(ClientMagic.mana + 0.001F < d.cost() ? ChatFormatting.RED : ChatFormatting.AQUA));
        } else {
            lines.add(Component.translatable("screen.isekai_mahou.travel.free").withStyle(ChatFormatting.AQUA));
        }
        if (ready(d)) {
            lines.add(Component.translatable(fromMap ? "screen.isekai_mahou.travel.click_select"
                    : "screen.isekai_mahou.travel.double_click").withStyle(ChatFormatting.DARK_GRAY));
        } else {
            lines.add(statusLine(d).withStyle(status(d) == Travel.TravelResult.ALREADY_HERE ? ChatFormatting.GOLD : ChatFormatting.RED));
        }
        return lines;
    }

    private MutableComponent statusLine(ClientTravel.Destination d) {
        return switch (status(d)) {
            case NO_RESOURCE -> Component.translatable("travel.isekai_mahou.result.no_resource_detail", resourceName(),
                    (int) ClientMagic.mana, d.cost());
            case COOLDOWN -> Component.translatable("travel.isekai_mahou.result.cooldown_detail", time(ClientTravel.cooldownLeft()));
            default -> status(d).message().copy();
        };
    }

    private static Component shortStatus(Travel.TravelResult r) {
        String key = "screen.isekai_mahou.travel.short." + r.name().toLowerCase(Locale.ROOT);
        return Component.translatable(key);
    }

    private static Component distance(@Nullable LocalPlayer p, ClientTravel.Destination d) {
        if (p == null || !d.dim().equals(p.level().dimension())) return dimName(d.dim());
        double dist = Math.hypot(d.pos().getX() + 0.5 - p.getX(), d.pos().getZ() + 0.5 - p.getZ());
        if (dist < 1000) return Component.translatable("screen.isekai_mahou.travel.dist_blocks", (int) Math.round(dist));
        return Component.translatable("screen.isekai_mahou.travel.dist_km", String.format("%.1f", dist / 1000.0));
    }

    private static Component dimName(ResourceKey<Level> dim) {
        String key = "screen.isekai_mahou.travel.dim." + dim.location().getNamespace() + "." + dim.location().getPath().replace('/', '.');
        return I18n.exists(key) ? Component.translatable(key) : Component.literal(dim.location().getPath());
    }

    private static Component typeLabel(String type) {
        String key = "screen.isekai_mahou.travel.type." + type;
        return Component.translatable(I18n.exists(key) ? key : "screen.isekai_mahou.travel.type.other");
    }

    private static int typeColor(ClientTravel.Destination d) {
        return typeColor(d.type(), d.color());
    }

    private static int typeColor(String type, int fallback) {
        int rgb = switch (type) {
            case "capital" -> 0xFFD86B;
            case "city" -> 0x6FA8FF;
            case "village" -> 0x7CE07C;
            case Travel.WAYSTONE_TYPE -> 0x6FE3FF;
            case Travel.HUB_TYPE -> 0xC77DFF;
            default -> fallback != 0 ? fallback : 0xB98CFF;
        };
        return 0xFF000000 | rgb;
    }

    private static String time(int ticks) {
        int s = Math.max(0, (ticks + 19) / 20);
        return (s / 60) + ":" + String.format("%02d", s % 60);
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    boolean mouseClicked(double mx, double my, int button) {
        if (button != 0 || !loaded || dests.isEmpty()) return false;
        // Ponto do mapa
        LocalPlayer p = Minecraft.getInstance().player;
        if (p != null && mx >= mapX && mx < mapX + mapS && my >= mapY && my < mapY + mapS) {
            int cx = mapX + mapS / 2, cy = mapY + mapS / 2, radius = mapS / 2 - 6;
            double scale = radius / range(p);
            ClientTravel.Destination best = null;
            double bestD = 7 * 7;
            for (ClientTravel.Destination d : dests) {
                if (!d.dim().equals(p.level().dimension())) continue;
                double dx = (d.pos().getX() + 0.5 - p.getX()) * scale, dz = (d.pos().getZ() + 0.5 - p.getZ()) * scale;
                double len = Math.hypot(dx, dz);
                if (len > radius) {
                    dx = dx / len * radius;
                    dz = dz / len * radius;
                }
                double dd = (cx + dx - mx) * (cx + dx - mx) + (cy + dz - my) * (cy + dz - my);
                if (dd < bestD) {
                    bestD = dd;
                    best = d;
                }
            }
            if (best != null) {
                select(best.id(), true);
                return true;
            }
            return false;
        }
        // Linha da lista
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            for (Row row : rows) {
                if (row.dest() == null) continue;
                int ry = listY + row.top() - (int) scroll;
                if (my >= ry && my < ry + ROW_H) {
                    String id = row.dest().id();
                    long now = Util.getMillis();
                    boolean dbl = id.equals(lastClickId) && now - lastClickAt < 350;
                    lastClickId = id;
                    lastClickAt = now;
                    select(id, false);
                    if (dbl) travel();
                    return true;
                }
            }
        }
        return false;
    }

    private void select(String id, boolean scrollTo) {
        if (!id.equals(selectedId)) {
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.4F, 0.35F));
        }
        selectedId = id;
        if (scrollTo) {
            for (Row row : rows) {
                if (row.dest() != null && row.dest().id().equals(id)) {
                    if (row.top() < scroll) scroll = row.top();
                    else if (row.top() + ROW_H > scroll + listH) scroll = row.top() + ROW_H - listH;
                }
            }
        }
        updateButton();
    }

    boolean mouseScrolled(double mx, double my, double sy) {
        if (mx >= mapX && mx < mapX + mapS && my >= mapY && my < mapY + mapS) {
            zoom = Mth.clamp(sy > 0 ? zoom * 1.25F : zoom / 1.25F, 0.5F, 24F);
            return true;
        }
        if (mx >= listX && mx < listX + listW && my >= listY && my < listY + listH) {
            scroll -= sy * ROW_H;
            return true;
        }
        return false;
    }

    boolean keyPressed(int key) {
        // Setas: escolhe o destino anterior/próximo; Enter: viaja
        if (!loaded || dests.isEmpty()) return false;
        if (key == 264 || key == 265) { // baixo / cima
            int idx = 0;
            for (int i = 0; i < dests.size(); i++) if (dests.get(i).id().equals(selectedId)) idx = i;
            idx = Mth.clamp(idx + (key == 264 ? 1 : -1), 0, dests.size() - 1);
            select(dests.get(idx).id(), true);
            return true;
        }
        if (key == 257 || key == 335) { // Enter
            travel();
            return true;
        }
        return false;
    }
}
