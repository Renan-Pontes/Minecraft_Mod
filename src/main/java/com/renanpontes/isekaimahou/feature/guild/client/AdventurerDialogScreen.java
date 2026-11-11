package com.renanpontes.isekaimahou.feature.guild.client;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.feature.guild.GuildContent;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork;
import com.renanpontes.isekaimahou.feature.guild.GuildNetwork.AdvDialog;
import com.renanpontes.isekaimahou.feature.guild.GuildRank;
import com.renanpontes.isekaimahou.feature.guild.entity.AdventurerEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Diálogo com um aventureiro: retrato 3D, nome, classe e rank, a fala (que muda com o contrato e a reputação), ficha de
 * combate (abates, experiência até o próximo rank, elemento) e os botões Conversar / Trocar / Contratar (preço e duração)
 * — ou, se ele é seu companheiro, Renovar / Seguir-Esperar / Dispensar — e Despedir-se.
 */
public class AdventurerDialogScreen extends Screen {
    private AdvDialog data;
    private final GuildUi.Frame frame = new GuildUi.Frame();
    private int left, top, panelW, panelH;
    /** Momento (ms) em que a fala atual chegou: efeito de "máquina de escrever". */
    private long lineAt = System.currentTimeMillis();
    /** Dispensar pede confirmação (segundo clique em até 3 s). */
    private long confirmUntil;

    public AdventurerDialogScreen(AdvDialog data) {
        super(Component.translatable("screen.isekai_mahou.guild.adv.title"));
        this.data = data;
    }

    /** Gancho do pacote: abre o diálogo ou atualiza o já aberto (mesmo aventureiro). */
    static void openOrUpdate(AdvDialog d) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen instanceof AdventurerDialogScreen s && s.data.entityId() == d.entityId()) {
            if (!s.data.line().getString().equals(d.line().getString())) s.lineAt = System.currentTimeMillis();
            s.data = d;
            return;
        }
        if (!d.refresh()) mc.setScreen(new AdventurerDialogScreen(d));
    }

    @Override
    protected void init() {
        panelW = Math.min(width - 16, 340);
        panelH = Math.min(height - 16, 206);
        left = (width - panelW) / 2;
        top = (height - panelH) / 2;
    }

    private void send(int action) {
        PacketDistributor.sendToServer(new GuildNetwork.AdvAction(data.entityId(), action));
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float partialTick) {
        renderTransparentBackground(g);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partialTick) {
        super.render(g, mx, my, partialTick);
        frame.clear();
        Font font = this.font;
        GuildUi.panel(g, left, top, panelW, panelH);
        AdventurerEntity.Variant variant = AdventurerEntity.Variant.values()[Mth.clamp(data.variant(), 0, AdventurerEntity.Variant.values().length - 1)];
        GuildRank rank = data.guildRank();

        // ---- Retrato
        int px = left + 8, py = top + 8, pw = 96, ph = 120;
        g.fill(px, py, px + pw, py + ph, 0xFF0F0B07);
        g.fillGradient(px + 1, py + 1, px + pw - 1, py + ph - 1, GuildUi.mix(rank.argb, 0xFF0F0B07, 0.82F), 0xFF0F0B07);
        g.renderOutline(px, py, pw, ph, rank.argb);
        Entity e = minecraft != null && minecraft.level != null ? minecraft.level.getEntity(data.entityId()) : null;
        if (e instanceof LivingEntity le) {
            GuildNpcRenderer.portrait = true;
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, px + 2, py + 2, px + pw - 2, py + ph - 4, 38, 0.0625F, mx, my, le);
            } finally {
                GuildNpcRenderer.portrait = false;
            }
        }
        GuildUi.rankBadge(g, font, px + pw - 22, py + 4, 18, rank, false);
        // Nome e classe sob o retrato
        int ny = py + ph + 4;
        g.drawString(font, GuildUi.ellipsize(font, data.name(), pw), px, ny, GuildUi.TEXT, true);
        Component cls = variant.displayName().withStyle(ChatFormatting.ITALIC);
        g.drawString(font, cls, px, ny + 11, GuildUi.MUTED, false);
        if (variant == AdventurerEntity.Variant.MAGE || variant == AdventurerEntity.Variant.CLERIC) {
            Element el = Element.values()[Mth.clamp(data.element(), 0, Element.values().length - 1)];
            g.drawString(font, el.displayName(), px, ny + 22, 0xFF000000 | el.color, false);
        }

        // ---- Fala (com efeito de digitação)
        int rx = px + pw + 10, rw = left + panelW - 10 - rx;
        int sy = top + 10;
        String full = data.line().getString();
        int shown = (int) Math.min(full.length(), (System.currentTimeMillis() - lineAt) / 18);
        Component speech = Component.literal("“" + full.substring(0, shown) + (shown < full.length() ? "" : "”"));
        int speechH = 48;
        g.fill(rx, sy, rx + rw, sy + speechH, 0xC0120D08);
        g.renderOutline(rx, sy, rw, speechH, GuildUi.BORDER_DIM);
        GuildUi.wrapped(g, font, speech, rx + 5, sy + 5, rw - 10, GuildUi.TEXT, 4);

        // ---- Ficha
        int fy = sy + speechH + 6;
        g.drawString(font, Component.translatable("screen.isekai_mahou.guild.adv.kills", data.kills()), rx, fy, GuildUi.MUTED, false);
        if (data.xpNext() > 0) {
            GuildRank next = rank.next();
            Component lbl = Component.translatable("screen.isekai_mahou.guild.adv.xp", data.xp(), data.xpNext(),
                    next == null ? Component.empty() : next.letter());
            g.drawString(font, lbl, rx, fy + 11, GuildUi.MUTED, false);
            GuildUi.progressBar(g, rx, fy + 21, rw, 5, data.xp() / (float) Math.max(1, data.xpNext()), rank.argb);
        } else {
            g.drawString(font, Component.translatable("screen.isekai_mahou.guild.adv.max_rank"), rx, fy + 11, GuildUi.GOLD, false);
        }
        fy += 32;

        // ---- Contrato
        Component status;
        int statusColor;
        if (data.hire() == GuildNetwork.HIRE_MINE) {
            status = Component.translatable(data.following() ? "screen.isekai_mahou.guild.adv.mine_following"
                    : "screen.isekai_mahou.guild.adv.mine_waiting", time(data.ticksLeft()));
            statusColor = GuildUi.GREEN;
        } else if (data.hire() == GuildNetwork.HIRE_OTHER) {
            status = Component.translatable("screen.isekai_mahou.guild.adv.other");
            statusColor = GuildUi.FAINT;
        } else if (data.refused()) {
            status = Component.translatable("screen.isekai_mahou.guild.adv.refused");
            statusColor = GuildUi.RED;
        } else {
            status = Component.translatable("screen.isekai_mahou.guild.adv.offer", data.price(), data.days());
            statusColor = GuildUi.GOLD;
        }
        fy += GuildUi.wrapped(g, font, status, rx, fy, rw, statusColor, 2) + 2;
        Component slots = Component.translatable("screen.isekai_mahou.guild.adv.slots", data.companions(), data.maxCompanions());
        g.drawString(font, slots, rx, fy, data.companions() >= data.maxCompanions() ? GuildUi.RED : GuildUi.FAINT, false);
        GuildUi.coinCounter(g, font, left + panelW - 10 - 40, fy - 4);

        // ---- Botões
        int by = top + panelH - 44;
        int bw = (rw - 6) / 3;
        int coins = GuildUi.coins();
        GuildUi.button(g, font, frame, rx, by, bw, 16, Component.translatable("screen.isekai_mahou.guild.adv.talk"), mx, my, true,
                GuildUi.BORDER, () -> send(GuildNetwork.ADV_TALK));
        GuildUi.button(g, font, frame, rx + bw + 3, by, bw, 16, Component.translatable("screen.isekai_mahou.guild.adv.trade"), mx, my,
                data.canTrade(), GuildUi.BLUE, () -> {
                    send(GuildNetwork.ADV_TRADE);
                    onClose();
                });
        GuildUi.button(g, font, frame, rx + 2 * (bw + 3), by, bw, 16, Component.translatable("screen.isekai_mahou.guild.adv.bye"), mx, my, true,
                GuildUi.BORDER, () -> {
                    send(GuildNetwork.ADV_BYE);
                    onClose();
                });
        by += 19;
        if (data.hire() == GuildNetwork.HIRE_MINE) {
            GuildUi.button(g, font, frame, rx, by, bw, 16, Component.translatable("screen.isekai_mahou.guild.adv.renew", data.price()), mx, my,
                    coins >= data.price(), GuildUi.GOLD, () -> send(GuildNetwork.ADV_RENEW));
            GuildUi.button(g, font, frame, rx + bw + 3, by, bw, 16, Component.translatable(data.following()
                    ? "screen.isekai_mahou.guild.adv.wait" : "screen.isekai_mahou.guild.adv.follow"), mx, my, true, GuildUi.GREEN,
                    () -> send(GuildNetwork.ADV_FOLLOW));
            boolean confirming = System.currentTimeMillis() < confirmUntil;
            GuildUi.button(g, font, frame, rx + 2 * (bw + 3), by, bw, 16, Component.translatable(confirming
                    ? "screen.isekai_mahou.guild.confirm" : "screen.isekai_mahou.guild.adv.dismiss"), mx, my, true, GuildUi.RED, () -> {
                if (System.currentTimeMillis() < confirmUntil) {
                    send(GuildNetwork.ADV_DISMISS);
                    confirmUntil = 0;
                } else {
                    confirmUntil = System.currentTimeMillis() + 3000;
                }
            });
        } else if (data.hire() == GuildNetwork.HIRE_FREE) {
            boolean can = !data.refused() && coins >= data.price() && data.companions() < data.maxCompanions();
            Component label = Component.translatable("screen.isekai_mahou.guild.adv.hire", data.price());
            GuildUi.button(g, font, frame, rx, by, rw, 16, label, mx, my, can, GuildUi.GOLD, () -> send(GuildNetwork.ADV_HIRE));
            if (GuildUi.over(mx, my, rx, by, rw, 16)) {
                frame.hoverText = java.util.List.of(Component.translatable("screen.isekai_mahou.guild.adv.hire_tip", data.days()),
                        Component.translatable("screen.isekai_mahou.guild.adv.hire_tip2").withStyle(ChatFormatting.GRAY));
            }
            g.pose().pushPose();
            g.pose().translate(rx + 4, by + 3, 0);
            g.pose().scale(0.625F, 0.625F, 1);
            g.renderItem(new ItemStack(GuildContent.GUILD_COIN.get()), 0, 0);
            g.pose().popPose();
        } else {
            g.pose().pushPose();
            g.pose().translate(rx, by, 0);
            g.renderItem(new ItemStack(Items.PAPER), 0, 0);
            g.pose().popPose();
        }
        frame.tooltips(g, font, mx, my);
    }

    /** "2d 5h" (dias e horas de jogo). */
    static Component time(long ticks) {
        long days = ticks / 24000L;
        long hours = (ticks % 24000L) / 1000L;
        return days > 0 ? Component.translatable("screen.isekai_mahou.guild.adv.time_dh", days, hours)
                : Component.translatable("screen.isekai_mahou.guild.adv.time_h", Math.max(1, hours));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0 && frame.click(mx, my)) return true;
        return super.mouseClicked(mx, my, button);
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
    public void tick() {
        // O aventureiro sumiu, morreu ou ficou longe: fecha.
        Entity e = minecraft != null && minecraft.level != null ? minecraft.level.getEntity(data.entityId()) : null;
        if (e == null || !e.isAlive() || (minecraft.player != null && minecraft.player.distanceToSqr(e) > 10 * 10)) onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
