package com.renanpontes.isekaimahou.feature.cities.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.feature.cities.network.CitiesNetwork;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tela de diálogo com um NPC da cidade: retrato 3D (segue o mouse), nome, ofício, facção + patamar colorido, a fala
 * (aparece letra a letra) e os botões Conversar / Trocar / Serviços / Rumores / Despedir-se. "Serviços" troca a área
 * da fala pela lista de serviços com preço. Tudo que muda vem pronto do servidor.
 */
public class CityDialogueScreen extends Screen {
    private static final int MAX_W = 430;
    private static final int MAX_H = 232;
    private static final int PORTRAIT_W = 112;

    private CitiesNetwork.Dialogue data;
    private boolean servicesView;
    private int lineAge;
    private int left, top, panelW, panelH;

    private CityDialogueScreen(CitiesNetwork.Dialogue data) {
        super(data.name());
        this.data = data;
    }

    /** Abre a tela ou atualiza a que já está aberta para o mesmo NPC. */
    public static void openOrUpdate(CitiesNetwork.Dialogue d) {
        Minecraft mc = Minecraft.getInstance();
        if (d.has(CitiesNetwork.BTN_CLOSE_NOW)) {
            // A abordagem virou briga/fuga: fecha a tela sem avisar o servidor de novo.
            if (mc.screen instanceof CityDialogueScreen s && s.data.entityId() == d.entityId()) s.closeQuietly();
            return;
        }
        if (mc.screen instanceof CityDialogueScreen s && s.data.entityId() == d.entityId()) {
            // Toda resposta do servidor traz uma fala nova (ex.: "Pronto, curado!" / "Faltam moedas"): volta para a
            // área da fala para o jogador ler; o botão "Serviços" reabre a lista já atualizada.
            s.data = d;
            s.lineAge = 0;
            s.servicesView = false;
            s.rebuild();
        } else {
            mc.setScreen(new CityDialogueScreen(d));
        }
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 16, MAX_W);
        panelH = Math.min(height - 16, MAX_H);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
        rebuild();
    }

    private boolean confront() {
        return data.has(CitiesNetwork.BTN_CONFRONT);
    }

    /** Linhas de botões da abordagem (2 por linha). */
    private int confrontRows() {
        return (data.services().size() + 1) / 2;
    }

    private void rebuild() {
        clearWidgets();
        int textX = left + PORTRAIT_W + 14;
        int textW = panelW - PORTRAIT_W - 24;
        if (confront()) {
            // v1.0: abordagem da guarda — as respostas em duas colunas embaixo da acusação.
            List<CitiesNetwork.Service> opts = data.services();
            int rows = confrontRows();
            int gap = 4;
            int bw = (textW - gap) / 2;
            for (int i = 0; i < opts.size(); i++) {
                CitiesNetwork.Service s = opts.get(i);
                boolean affordable = s.cost() == 0 || s.cost() <= data.coins();
                Component label = s.cost() > 0
                        ? Component.translatable("screen.isekai_mahou.cities.service_cost", s.label(), s.cost())
                        : s.label();
                int color = switch (s.id()) {
                    case "fight" -> 0xFF7A6A;
                    case "flee" -> 0xF0B060;
                    case "persuade" -> 0xB9E8FF;
                    case "return" -> 0xA8E07A;
                    case "bribe" -> 0xFFD86B;
                    default -> 0xFFFFFF;
                };
                int r = i / 2;
                boolean last = i == opts.size() - 1 && opts.size() % 2 == 1;
                int bx = textX + (i % 2) * (bw + gap);
                int by = top + panelH - 30 - (rows - 1 - r) * 24;
                Button b = Button.builder(label.copy().withColor(color), btn -> send("confront", s.id()))
                        .bounds(bx, by, last ? textW : bw, 20)
                        .tooltip(Tooltip.create(s.available() && !affordable
                                ? Component.translatable("screen.isekai_mahou.cities.no_coins") : s.description()))
                        .build();
                b.active = s.available() && affordable;
                addRenderableWidget(b);
            }
            return;
        }
        if (servicesView) {
            int y = top + 62;
            for (CitiesNetwork.Service s : data.services()) {
                boolean affordable = s.cost() <= data.coins() || s.cost() == 0;
                Component label = s.cost() > 0
                        ? Component.translatable("screen.isekai_mahou.cities.service_cost", s.label(), s.cost())
                        : s.label();
                Button b = Button.builder(label, btn -> send("service", s.id())).bounds(textX, y, textW, 20)
                        .tooltip(Tooltip.create(s.available()
                                ? (affordable ? s.description() : Component.translatable("screen.isekai_mahou.cities.no_coins"))
                                : Component.translatable("screen.isekai_mahou.cities.service_unavailable")))
                        .build();
                b.active = s.available() && affordable;
                addRenderableWidget(b);
                y += 23;
                if (y > top + panelH - 50) break;
            }
            addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.cities.back"), btn -> {
                servicesView = false;
                rebuild();
            }).bounds(textX, top + panelH - 28, 90, 20).build());
            return;
        }
        List<Button> row = new ArrayList<>();
        if (!data.has(CitiesNetwork.BTN_ENEMY)) {
            row.add(Button.builder(Component.translatable("screen.isekai_mahou.cities.talk"), b -> send("talk", "")).build());
        }
        if (data.has(CitiesNetwork.BTN_TRADE) || data.has(CitiesNetwork.BTN_TRADE_LOCKED)) {
            Button trade = Button.builder(Component.translatable("screen.isekai_mahou.cities.trade"), b -> send("trade", "")).build();
            if (data.has(CitiesNetwork.BTN_TRADE_LOCKED)) {
                trade.setTooltip(Tooltip.create(Component.translatable("screen.isekai_mahou.cities.trade_locked")));
            }
            row.add(trade);
        }
        if (data.has(CitiesNetwork.BTN_SERVICES)) {
            row.add(Button.builder(Component.translatable("screen.isekai_mahou.cities.services"), b -> {
                servicesView = true;
                rebuild();
            }).build());
        }
        if (data.has(CitiesNetwork.BTN_RUMORS)) {
            row.add(Button.builder(Component.translatable("screen.isekai_mahou.cities.rumors"), b -> send("rumor", "")).build());
        }
        row.add(Button.builder(Component.translatable("screen.isekai_mahou.cities.bye"), b -> onClose()).build());
        // Botões em uma ou duas linhas embaixo da fala.
        int perRow = textW >= 300 ? row.size() : 3;
        int rows = (row.size() + perRow - 1) / perRow;
        int gap = 4;
        for (int i = 0; i < row.size(); i++) {
            int r = i / perRow;
            int inRow = Math.min(perRow, row.size() - r * perRow);
            int bw = (textW - gap * (inRow - 1)) / inRow;
            int bx = textX + (i % perRow) * (bw + gap);
            int by = top + panelH - 30 - (rows - 1 - r) * 24;
            Button b = row.get(i);
            b.setRectangle(bw, 20, bx, by);
            addRenderableWidget(b);
        }
    }

    private void send(String action, String arg) {
        PacketDistributor.sendToServer(new CitiesNetwork.Action(data.entityId(), action, arg));
    }

    @Override
    public void tick() {
        lineAge++;
        Minecraft mc = Minecraft.getInstance();
        Entity e = mc.level == null ? null : mc.level.getEntity(data.entityId());
        if (e == null || !e.isAlive() || mc.player == null || e.distanceToSqr(mc.player) > 10 * 10) onClose();
        else if (lineAge % 3 == 0 && lineAge < 60 && revealed() < data.line().getString().length()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 2.0F, 0.08F));
        }
    }

    private int revealed() {
        return lineAge * 3;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        int accent = confront() ? 0xFFD8573A : 0xFF000000 | data.factionRgb();
        MahouUi.frame(g, left, top, panelW, panelH, accent);
        // Moldura do retrato.
        int px = left + 8;
        int py = top + 10;
        MahouUi.roundRect(g, px, py, PORTRAIT_W, panelH - 20, 0xC0283456, 0xC00C0E1C);
        MahouUi.roundOutline(g, px, py, PORTRAIT_W, panelH - 20, MahouUi.alpha(accent, 0xA0));
        // Área da fala.
        int tx = left + PORTRAIT_W + 14;
        int tw = panelW - PORTRAIT_W - 24;
        if (confront()) {
            int boxBottom = top + panelH - 36 - (confrontRows() - 1) * 24;
            MahouUi.roundRect(g, tx - 4, top + 58, tw + 8, boxBottom - (top + 58), 0x90281410, 0x90140808);
            MahouUi.roundOutline(g, tx - 4, top + 58, tw + 8, boxBottom - (top + 58), MahouUi.alpha(accent, 0x70));
        } else if (!servicesView) {
            int boxBottom = top + panelH - 36 - (tw >= 300 ? 0 : 24);
            MahouUi.roundRect(g, tx - 4, top + 58, tw + 8, boxBottom - (top + 58), 0x90101426, 0x90080A14);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        Minecraft mc = Minecraft.getInstance();
        int px = left + 8;
        int py = top + 10;
        Entity e = mc.level == null ? null : mc.level.getEntity(data.entityId());
        if (e instanceof LivingEntity le) {
            CityNpcRenderer.portrait = true;
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 4, py + 6, px + PORTRAIT_W - 4, py + panelH - 52, 46,
                        0.0625F, mouseX, mouseY, le);
            } finally {
                CityNpcRenderer.portrait = false;
            }
        }
        // Nome e ofício sob o retrato.
        g.drawCenteredString(font, MahouUi.fit(font, data.name(), PORTRAIT_W - 6), px + PORTRAIT_W / 2, py + panelH - 44, MahouUi.GOLD);
        g.drawCenteredString(font, MahouUi.fit(font, data.role(), PORTRAIT_W - 6), px + PORTRAIT_W / 2, py + panelH - 32, MahouUi.TEXT_DIM);

        int tx = left + PORTRAIT_W + 14;
        int tw = panelW - PORTRAIT_W - 24;
        // Cabeçalho: nome grande, ofício, facção — patamar.
        g.pose().pushPose();
        g.pose().translate(tx, top + 12, 0);
        g.pose().scale(1.4F, 1.4F, 1.0F);
        int nameW = confront() ? tw - font.width(Component.translatable("screen.isekai_mahou.cities.confront.tag")) - 10 : tw;
        g.drawString(font, MahouUi.fit(font, data.name(), (int) (nameW / 1.4F)), 0, 0, MahouUi.GOLD_PALE, true);
        g.pose().popPose();
        g.drawString(font, MahouUi.fit(font, data.role(), tw), tx, top + 28, MahouUi.TEXT_DIM, false);
        Component fac = Component.empty().append(data.faction().copy().withColor(data.factionRgb()))
                .append(Component.literal("  •  ").withColor(0x8A8098)).append(data.standing());
        MahouUi.diamond(g, tx + 3, top + 44, 3, 0xFF000000 | data.factionRgb());
        g.drawString(font, MahouUi.fit(font, fac, tw - 10), tx + 10, top + 40, MahouUi.TEXT, false);

        if (servicesView) {
            g.drawString(font, Component.translatable("screen.isekai_mahou.cities.services_title"), tx, top + 52, MahouUi.GOLD, false);
        } else {
            // Fala letra a letra.
            String full = data.line().getString();
            String shown = full.substring(0, Mth.clamp(revealed(), 0, full.length()));
            List<FormattedCharSequence> lines = font.split(Component.literal(shown), tw - 4);
            int y = top + 64;
            for (FormattedCharSequence l : lines) {
                g.drawString(font, l, tx, y, MahouUi.TEXT, false);
                y += 11;
            }
            if (confront() && !data.note().getString().isEmpty()) {
                // A acusação: o que viram, a reputação perdida e a reincidência.
                int boxBottom = top + panelH - 36 - (confrontRows() - 1) * 24;
                y = Math.max(y + 4, top + 64 + 3 * 11 + 4);
                for (FormattedCharSequence l : font.split(data.note(), tw - 4)) {
                    if (y + 9 > boxBottom - 2) break;
                    g.drawString(font, l, tx, y, 0xFFF0A043, false);
                    y += 10;
                }
            }
        }
        if (confront()) {
            // Selo "Abordagem" no canto (pisca de leve).
            Component tag = Component.translatable("screen.isekai_mahou.cities.confront.tag");
            float pulse = 0.65F + 0.35F * Mth.sin((lineAge + partialTick) * 0.25F);
            int a = (int) (pulse * 255.0F) << 24;
            g.drawString(font, tag, left + panelW - 10 - font.width(tag), top + 14, a | 0xFF6A4A, true);
        }
        // Moedas.
        Component coins = Component.translatable("screen.isekai_mahou.cities.coins", data.coins());
        g.drawString(font, coins, left + panelW - 10 - font.width(coins), top + 28, 0xFFE8B84A, false);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        send("close", "");
        super.onClose();
    }

    private void closeQuietly() {
        super.onClose();
    }
}
