package com.renanpontes.isekaimahou.feature.ritual_warlock.client;

import java.util.List;

import com.renanpontes.isekaimahou.feature.ritual_warlock.entity.PatronEntity;
import com.renanpontes.isekaimahou.feature.ritual_warlock.network.WarlockPayloads;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PactRitual;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PatronDemand;
import com.renanpontes.isekaimahou.feature.ritual_warlock.ritual.PatronKind;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Diálogo com o Patrono preso no círculo: retrato animado, fala com efeito de máquina de escrever,
 * o tributo exigido (ícone + texto + prazo/progresso) e os botões Aceitar/Recusar ou Entregar/Fechar.
 * Fechar com ESC não recusa (é só clicar no Patrono ou no altar para reabrir).
 */
public class PatronDialogScreen extends Screen {
    private static final int W = 330;
    private static final int H = 196;
    private static final int PORTRAIT_W = 96;
    private static final ResourceLocation HEART = ResourceLocation.withDefaultNamespace("hud/heart/full");

    private final WarlockPayloads.PatronDialog data;
    private final PatronKind kind;
    private final String speech;
    private int left;
    private int top;
    private int shown;
    private int ticks;

    public PatronDialogScreen(WarlockPayloads.PatronDialog data) {
        super(PatronKind.byId(data.kind()).displayName());
        this.data = data;
        this.kind = PatronKind.byId(data.kind());
        this.speech = data.line().getString();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int by = top + H - 26;
        int bx = left + PORTRAIT_W + 16;
        int bw = (W - PORTRAIT_W - 30) / 2;
        switch (data.mode()) {
            case WarlockPayloads.MODE_OFFER -> {
                addRenderableWidget(Button.builder(Component.translatable("gui.isekai_mahou.warlock.accept").withStyle(ChatFormatting.GOLD),
                        b -> answer(WarlockPayloads.ANSWER_ACCEPT)).bounds(bx, by, bw, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("gui.isekai_mahou.warlock.refuse").withStyle(ChatFormatting.RED),
                        b -> answer(WarlockPayloads.ANSWER_REFUSE)).bounds(bx + bw + 6, by, bw, 20).build());
            }
            case WarlockPayloads.MODE_PENDING -> {
                addRenderableWidget(Button.builder(Component.translatable("gui.isekai_mahou.warlock.deliver").withStyle(ChatFormatting.GOLD),
                        b -> answer(WarlockPayloads.ANSWER_DELIVER)).bounds(bx, by, bw, 20).build());
                addRenderableWidget(Button.builder(Component.translatable("gui.isekai_mahou.warlock.close"),
                        b -> onClose()).bounds(bx + bw + 6, by, bw, 20).build());
            }
            default -> addRenderableWidget(Button.builder(Component.translatable("gui.isekai_mahou.warlock.close"),
                    b -> onClose()).bounds(bx + bw + 6, by, bw, 20).build());
        }
        if (ticks == 0 && minecraft != null) {
            minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.SOUL_ESCAPE.value(), 0.6F, 1.0F));
        }
    }

    private void answer(int action) {
        PacketDistributor.sendToServer(new WarlockPayloads.PatronAnswer(data.entityId(), action));
        onClose();
    }

    private LivingEntity patron() {
        if (minecraft == null || minecraft.level == null) return null;
        Entity e = minecraft.level.getEntity(data.entityId());
        return e instanceof LivingEntity le ? le : null;
    }

    @Override
    public void tick() {
        ticks++;
        if (shown < speech.length()) shown = Math.min(speech.length(), shown + 2);
        LivingEntity p = patron();
        // O Patrono saiu do círculo (selado, liberto) ou ficou longe demais: o diálogo acaba.
        if (p == null || p.isRemoved() || (p instanceof PatronEntity pe && pe.isFree())
                || (minecraft != null && minecraft.player != null && minecraft.player.distanceToSqr(p) > 24 * 24)) {
            onClose();
        }
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (shown < speech.length()) {
            shown = speech.length();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        drawPanel(g, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphics g, int mouseX, int mouseY) {
        int c = kind.color;
        int glow = 0xFF000000 | c;
        // Moldura dupla: vermelho-sangue por fora, cor do Patrono por dentro.
        g.fill(left - 3, top - 3, left + W + 3, top + H + 3, 0xFF2A0606);
        g.fill(left - 2, top - 2, left + W + 2, top + H + 2, 0xFF7A1414);
        g.fill(left - 1, top - 1, left + W + 1, top + H + 1, 0xFF140708);
        g.fill(left, top, left + W, top + H, 0xF20C0608);
        g.fillGradient(left, top, left + W, top + 46, 0x50000000 | (c & 0xFFFFFF), 0x00000000);
        g.fillGradient(left, top + H - 40, left + W, top + H, 0x00000000, 0x40400808);
        drawCornerRunes(g, glow);

        // Retrato do Patrono.
        int px0 = left + 8, py0 = top + 8, px1 = left + 8 + PORTRAIT_W, py1 = top + H - 8;
        g.fill(px0 - 1, py0 - 1, px1 + 1, py1 + 1, glow);
        g.fillGradient(px0, py0, px1, py1, 0xFF05030A, 0xFF1A0A10);
        float pulse = 0.5F + 0.5F * Mth.sin(ticks * 0.12F);
        int ring = ((int) (40 + 50 * pulse) << 24) | (c & 0xFFFFFF);
        g.fillGradient(px0, py1 - 40, px1, py1, 0x00000000, ring);
        LivingEntity p = patron();
        if (p != null) {
            int scale = (int) Math.max(12, (py1 - py0 - 16) / Math.max(1.0F, p.getBbHeight()));
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, px0, py0, px1, py1, scale, 0.0625F, mouseX, mouseY, p);
        }
    }

    private void drawCornerRunes(GuiGraphics g, int color) {
        int[][] corners = {{left + 2, top + 2}, {left + W - 9, top + 2}, {left + 2, top + H - 9}, {left + W - 9, top + H - 9}};
        for (int[] k : corners) {
            int x = k[0], y = k[1];
            g.fill(x + 3, y, x + 4, y + 7, color);
            g.fill(x, y + 3, x + 7, y + 4, color);
            g.fill(x + 1, y + 1, x + 2, y + 2, color);
            g.fill(x + 5, y + 5, x + 6, y + 6, color);
            g.fill(x + 5, y + 1, x + 6, y + 2, color);
            g.fill(x + 1, y + 5, x + 2, y + 6, color);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = left + PORTRAIT_W + 16;
        int textW = W - PORTRAIT_W - 26;

        // Nome (grande) e subtítulo.
        g.pose().pushPose();
        g.pose().translate(x, top + 9, 0);
        g.pose().scale(1.5F, 1.5F, 1.0F);
        g.drawString(font, kind.displayName().withStyle(ChatFormatting.BOLD), 0, 0, 0xFFFFFF, true);
        g.pose().popPose();
        Component sub = Component.empty().append(kind.epithet()).append(" · ")
                .append(Component.translatable("gui.isekai_mahou.warlock.pact_number", data.pactNumber()));
        g.drawString(font, sub.copy().withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), x, top + 25, 0xA0A0A0, false);

        // Fala (máquina de escrever).
        int sy = top + 38;
        g.fill(x - 4, sy - 3, x + textW + 4, sy + 52, 0x80200A0C);
        g.fill(x - 4, sy - 3, x - 3, sy + 52, 0xFF000000 | kind.color);
        String typed = speech.substring(0, Math.min(shown, speech.length()));
        List<FormattedCharSequence> lines = font.split(Component.literal("“" + typed + (shown < speech.length() ? "" : "”"))
                .withStyle(ChatFormatting.ITALIC), textW);
        int ly = sy;
        for (int i = 0; i < Math.min(5, lines.size()); i++) {
            g.drawString(font, lines.get(i), x, ly, 0xF0E6D8, false);
            ly += 10;
        }

        if (data.mode() == WarlockPayloads.MODE_STRANGER) return;

        // Tributo.
        int dy = top + 95;
        g.fill(x - 4, dy - 3, x + textW + 4, dy + 56, 0x90120608);
        g.drawString(font, Component.translatable("gui.isekai_mahou.warlock.demand_title").withStyle(ChatFormatting.BOLD), x, dy,
                kind.color, false);
        int iy = dy + 14;
        PatronDemand.Type type = PatronDemand.Type.values()[Mth.clamp(data.demandType(), 0, PatronDemand.Type.values().length - 1)];
        if (type == PatronDemand.Type.HEARTS) {
            int hearts = Mth.clamp(data.icon().getCount(), 1, 6);
            for (int i = 0; i < hearts; i++) g.blitSprite(HEART, x + (i % 2) * 8, iy + (i / 2) * 8, 9, 9);
        } else if (!data.icon().isEmpty()) {
            g.renderItem(data.icon(), x, iy);
            g.renderItemDecorations(font, data.icon(), x, iy);
        }
        int tx = x + 22;
        int ty = iy;
        for (FormattedCharSequence line : font.split(data.demand(), textW - 22)) {
            g.drawString(font, line, tx, ty, 0xFFFFFF, false);
            ty += 10;
        }
        ty = Math.max(ty, iy + 18) + 2;
        for (FormattedCharSequence line : font.split(data.detail(), textW)) {
            g.drawString(font, line, x, ty, 0xB8B0C8, false);
            ty += 10;
        }

        // Paciência do Patrono (só ao oferecer).
        if (data.mode() == WarlockPayloads.MODE_OFFER && data.patience() > 0) {
            int left20 = Math.max(0, data.patience() - ticks);
            float frac = Mth.clamp(left20 / (float) PatronEntity.OFFER_PATIENCE, 0F, 1F);
            int bw = textW;
            int by = top + H - 32;
            g.fill(x, by, x + bw, by + 3, 0xFF2A1010);
            g.fill(x, by, x + Math.round(bw * frac), by + 3, frac < 0.25F ? 0xFFE04040 : 0xFF000000 | kind.color);
            Component pat = Component.translatable("gui.isekai_mahou.warlock.patience", PactRitual.formatTicks(left20));
            g.drawString(font, pat, x + bw - font.width(pat), by - 10, 0x9A90A8, false);
        }
    }
}
