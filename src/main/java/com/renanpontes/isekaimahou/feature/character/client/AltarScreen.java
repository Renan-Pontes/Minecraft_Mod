package com.renanpontes.isekaimahou.feature.character.client;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.client.GlyphArt;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.character.CharacterData;
import com.renanpontes.isekaimahou.feature.character.CharacterNetwork;
import com.renanpontes.isekaimahou.feature.character.Reincarnation;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * release 1.0 — a tela dos dois altares (uma tela só, limpa, sem nada piscando):
 * <ul>
 *   <li><b>Altar do Recomeço</b> ({@link View#RENEWAL}): explica o que volta (pontos de talento e especialização), o
 *       custo e um botão de confirmar.</li>
 *   <li><b>Altar da Reencarnação</b> ({@link View#HUB}): dois cartões — "Trocar de Raça" (abre a tela de raça no modo
 *       altar) e "Trocar de Classe" ({@link View#CLASS}: lista das 15 classes, o que se perde e o custo).</li>
 * </ul>
 * Trocar de classe pede DOIS cliques no botão (o segundo confirma): é caro e não tem volta.
 */
public class AltarScreen extends Screen {
    private enum View { HUB, CLASS, RENEWAL }

    private static final int PAD = 8, FOOT_H = 24, HEAD_H = 10;
    private static final int GREEN = 0x9FE8B0, PURPLE = 0xB98CFF;

    private final BlockPos altar;
    private View view;
    private MagicPath pick;
    /** O botão de trocar de classe já foi clicado uma vez (o próximo clique confirma). */
    private boolean armed;

    private int left, top, panelW, panelH, contentTop, contentBottom;
    private int listW, rowH, gap = 2, detailX, detailW, scroll, contentH;
    private Button confirmButton, backButton;

    public AltarScreen(int mode, BlockPos altar) {
        super(Component.translatable(mode == CharacterNetwork.MODE_RENEWAL
                ? "block.isekai_mahou.new_beginnings_altar" : "screen.isekai_mahou.character.altar_title"));
        this.altar = altar;
        this.view = mode == CharacterNetwork.MODE_RENEWAL ? View.RENEWAL : View.HUB;
        MagicPath now = ClientMagic.data().path();
        this.pick = now;
        for (MagicPath p : MagicPath.PLAYABLE) {
            if (p != now) {
                this.pick = p;
                break;
            }
        }
    }

    private static MagicPath current() {
        return ClientMagic.data().path();
    }

    private int accent() {
        return view == View.RENEWAL ? 0xFF000000 | GREEN : view == View.CLASS ? pick.argb() : 0xFF000000 | PURPLE;
    }

    // =====================================================================================
    //  Layout
    // =====================================================================================
    @Override
    protected void init() {
        boolean big = view == View.CLASS;
        panelW = Mth.clamp(width - 24, 300, big ? 500 : 380);
        panelH = Mth.clamp(height - 40, 190, big ? 320 : 210);
        left = (width - panelW) / 2;
        top = Math.max(14, (height - panelH) / 2 + 2);
        contentTop = top + PAD + HEAD_H;
        contentBottom = top + panelH - PAD - FOOT_H;
        int n = MagicPath.PLAYABLE.length;
        listW = Mth.clamp(panelW * 30 / 100, 104, 140);
        // Janela baixa: as 15 linhas apertam (sem passar do rodapé)
        gap = contentBottom - contentTop < n * 12 ? 1 : 2;
        rowH = Mth.clamp((contentBottom - contentTop - (n - 1) * gap) / n, 8, 18);
        detailX = left + PAD + listW + 10;
        detailW = left + panelW - PAD - detailX - 4;

        int fy = top + panelH - PAD - 20;
        int confirmW = Math.min(170, panelW / 2 - 12);
        confirmButton = null;
        backButton = null;
        if (view != View.HUB) {
            confirmButton = addRenderableWidget(Button.builder(confirmLabel(), b -> confirm())
                    .bounds(left + panelW - PAD - confirmW, fy, confirmW, 20).build());
        }
        if (view == View.CLASS) {
            backButton = addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.character.altar_back"),
                    b -> show(View.HUB)).bounds(left + PAD, fy, 60, 20).build());
        }
        updateConfirm();
    }

    private void show(View v) {
        view = v;
        armed = false;
        scroll = 0;
        rebuildWidgets();
        click(1.0F);
    }

    private Component confirmLabel() {
        if (view == View.RENEWAL) return Component.translatable("screen.isekai_mahou.character.renewal_confirm");
        return Component.translatable(armed ? "screen.isekai_mahou.character.class_confirm_sure" : "screen.isekai_mahou.character.class_confirm");
    }

    private Reincarnation.Cost cost() {
        return view == View.RENEWAL ? Reincarnation.RENEWAL : Reincarnation.CLASS;
    }

    private boolean canPay() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && cost().canPay(mc.player);
    }

    private void updateConfirm() {
        if (confirmButton == null) return;
        confirmButton.setMessage(confirmLabel());
        boolean ok = canPay();
        if (view == View.CLASS) ok &= pick != current() && current().pickableAtStart();
        confirmButton.active = ok;
    }

    private static void click(float pitch) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), pitch, 0.6F));
    }

    private void confirm() {
        if (view == View.CLASS && !armed) {
            // 1º clique: só arma (a troca é cara e definitiva)
            armed = true;
            updateConfirm();
            return;
        }
        if (view == View.RENEWAL) PacketDistributor.sendToServer(new CharacterNetwork.AltarAction(Reincarnation.ACT_RENEW, 0));
        else PacketDistributor.sendToServer(new CharacterNetwork.AltarAction(Reincarnation.ACT_CLASS, pick.ordinal()));
        minecraft.setScreen(null);
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    private int hubCardW() { return (panelW - 2 * PAD - 8) / 2; }
    private int hubCardY() { return contentTop + 4; }
    private int hubCardH() { return contentBottom - contentTop + FOOT_H - 8; }

    private void selectClass(int i) {
        MagicPath[] all = MagicPath.PLAYABLE;
        MagicPath p = all[Math.floorMod(i, all.length)];
        if (p == pick) return;
        pick = p;
        armed = false;
        scroll = 0;
        updateConfirm();
        click(1.2F);
    }

    private int indexOfPick() {
        MagicPath[] all = MagicPath.PLAYABLE;
        for (int i = 0; i < all.length; i++) if (all[i] == pick) return i;
        return 0;
    }

    private int rowY(int i) { return contentTop + i * (rowH + gap); }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) return true;
        if (view == View.HUB) {
            int w = hubCardW(), y = hubCardY(), h = hubCardH();
            if (CharacterUi.in(mx, my, left + PAD, y, w, h)) {
                click(1.1F);
                minecraft.setScreen(new CreationScreen(CharacterNetwork.MODE_ALTAR, altar));
                return true;
            }
            if (CharacterUi.in(mx, my, left + PAD + w + 8, y, w, h)) {
                show(View.CLASS);
                return true;
            }
        } else if (view == View.CLASS) {
            for (int i = 0; i < MagicPath.PLAYABLE.length; i++) {
                if (CharacterUi.in(mx, my, left + PAD, rowY(i), listW, rowH)) {
                    selectClass(i);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (view == View.CLASS) {
            if (mx >= detailX && contentH > contentBottom - contentTop) {
                scroll = Mth.clamp(scroll - (int) Math.round(sy * 12), 0, contentH - (contentBottom - contentTop));
                return true;
            }
            if (mx < detailX && sy != 0) {
                selectClass(indexOfPick() + (sy > 0 ? -1 : 1));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, sx, sy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (view == View.CLASS) {
            if (key == GLFW.GLFW_KEY_UP) { selectClass(indexOfPick() - 1); return true; }
            if (key == GLFW.GLFW_KEY_DOWN) { selectClass(indexOfPick() + 1); return true; }
        }
        return super.keyPressed(key, scan, mods);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        updateConfirm(); // o inventário pode mudar com a tela aberta
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        MahouUi.frame(g, left, top, panelW, panelH, accent());
        CharacterUi.titlePlate(g, font, title, left, top, panelW);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        switch (view) {
            case HUB -> renderHub(g, mx, my);
            case CLASS -> renderClass(g, mx, my);
            case RENEWAL -> renderRenewal(g);
        }
    }

    /** Linha do custo: "Custo: 64 Cristais de Mana (você tem N) + 1 Núcleo de Guardião (você tem N)". */
    private void costLines(GuiGraphics g, Reincarnation.Cost c, int x, int y, int w) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int crystals = mc.player.getInventory().countItem(CoreRegistries.MANA_CRYSTAL.get());
        boolean creative = mc.player.getAbilities().instabuild;
        Component a = Component.translatable("screen.isekai_mahou.character.altar_cost", c.crystals(),
                CoreRegistries.MANA_CRYSTAL.get().getDescription(), crystals);
        g.drawString(font, MahouUi.fit(font, a, w), x, y, creative || crystals >= c.crystals() ? MahouUi.TEXT_DIM : 0xFFE07A7A, false);
        if (c.cores() > 0) {
            int cores = Reincarnation.countCores(mc.player);
            Component b = Component.translatable("screen.isekai_mahou.character.altar_cost_core", c.cores(), cores);
            g.drawString(font, MahouUi.fit(font, b, w), x, y + 10, creative || cores >= c.cores() ? MahouUi.TEXT_DIM : 0xFFE07A7A, false);
        }
    }

    // ---------------------------------------------------------------- Altar da Reencarnação: raça ou classe
    private void renderHub(GuiGraphics g, int mx, int my) {
        int w = hubCardW(), y = hubCardY(), h = hubCardH();
        CharacterData d = ClientCharacter.data();
        hubCard(g, left + PAD, y, w, h, mx, my, d.created() ? d.race().argb() : MahouUi.GOLD,
                Component.translatable("screen.isekai_mahou.character.hub_race"),
                Component.translatable("screen.isekai_mahou.character.hub_now", d.created() ? d.race().displayName()
                        : Component.translatable("screen.isekai_mahou.character.hub_none")),
                Component.translatable("screen.isekai_mahou.character.hub_race_desc"), Reincarnation.RACE);
        MagicPath now = current();
        hubCard(g, left + PAD + w + 8, y, w, h, mx, my, now.argb(),
                Component.translatable("screen.isekai_mahou.character.hub_class"),
                Component.translatable("screen.isekai_mahou.character.hub_now", now.displayName()),
                Component.translatable("screen.isekai_mahou.character.hub_class_desc"), Reincarnation.CLASS);
        g.drawCenteredString(font, Component.translatable("screen.isekai_mahou.character.hub_note"), width / 2,
                Math.min(height - 10, top + panelH + 5), MahouUi.TEXT_DIM);
    }

    private void hubCard(GuiGraphics g, int x, int y, int w, int h, int mx, int my, int color, Component title, Component now,
                         Component desc, Reincarnation.Cost c) {
        boolean hover = CharacterUi.in(mx, my, x, y, w, h);
        MahouUi.roundRect(g, x, y, w, h, hover ? MahouUi.mix(0xFF2A2240, color, 0.18F) : 0xE0201A30, 0xE0120E1A);
        MahouUi.roundOutline(g, x, y, w, h, hover ? color : 0xFF3E3458);
        int tx = x + 8, tw = w - 16, ty = y + 8;
        g.drawString(font, MahouUi.fit(font, title.copy().withColor(MahouUi.GOLD & 0xFFFFFF), tw), tx, ty, 0xFFFFFFFF, true);
        ty += 12;
        g.drawString(font, MahouUi.fit(font, now, tw), tx, ty, MahouUi.TEXT, false);
        ty += 14;
        ty = CharacterUi.paragraph(g, font, desc, tx, ty, tw, MahouUi.TEXT_DIM);
        costLines(g, c, tx, y + h - 26, tw);
    }

    // ---------------------------------------------------------------- Altar da Reencarnação: lista de classes
    private void renderClass(GuiGraphics g, int mx, int my) {
        int x = left + PAD;
        MagicPath now = current();
        g.drawString(font, Component.translatable("screen.isekai_mahou.character.classes").withColor(MahouUi.GOLD & 0xFFFFFF),
                x, top + PAD, 0xFFFFFFFF, true);
        int dx = left + PAD + listW + 4;
        g.fill(dx, contentTop, dx + 1, contentBottom, MahouUi.alpha(MahouUi.GOLD_DIM, 0x80));
        MagicPath[] all = MagicPath.PLAYABLE;
        for (int i = 0; i < all.length; i++) {
            MagicPath p = all[i];
            int y = rowY(i);
            boolean sel = p == pick, mine = p == now;
            boolean hover = CharacterUi.in(mx, my, x, y, listW, rowH);
            if (sel) {
                MahouUi.roundRect(g, x, y, listW, rowH, MahouUi.mix(0xFF2A2240, p.argb(), 0.2F), 0xFF17131F);
                MahouUi.roundOutline(g, x, y, listW, rowH, p.argb());
            } else {
                MahouUi.roundRect(g, x, y, listW, rowH, hover ? 0xE0262036 : 0xC0181422, hover ? 0xE0181422 : 0xC0100C18);
                MahouUi.roundOutline(g, x, y, listW, rowH, hover ? 0xFF6C5C90 : 0xFF2E2642);
            }
            int icon = Math.max(6, Math.min(14, rowH - 2));
            classIcon(g, p, x + 4, y + (rowH - icon) / 2, icon);
            int tx = x + 8 + icon;
            g.drawString(font, MahouUi.fit(font, p.displayName(), listW - (tx - x) - 12), tx, y + (rowH - 8) / 2, 0xFFFFFFFF, true);
            if (mine) MahouUi.diamond(g, x + listW - 7, y + rowH / 2, 2, MahouUi.GOLD);
        }

        // Detalhes da classe escolhida
        int w = detailW, viewH = contentBottom - contentTop;
        g.enableScissor(detailX - 2, contentTop, detailX + detailW + 2, contentBottom);
        int y = contentTop - scroll;
        classIcon(g, pick, detailX, y, 32);
        g.pose().pushPose();
        g.pose().translate(detailX + 38, y + 3, 0);
        g.pose().scale(1.5F, 1.5F, 1F);
        g.drawString(font, MahouUi.fit(font, pick.displayName(), (int) ((w - 38) / 1.5F)), 0, 0, 0xFFFFFFFF, true);
        g.pose().popPose();
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.character.class_tag",
                pick.category().displayName(), pick.resource().displayName()), w - 38), detailX + 38, y + 20, MahouUi.TEXT_DIM, false);
        y += 38;
        y = CharacterUi.paragraph(g, font, pick.description(), detailX, y, w, 0xFFDDD8EA);
        y += 5;
        g.drawString(font, MahouUi.fit(font, pick.systemName().withColor(pick.rgb()), w), detailX, y, 0xFFFFFFFF, true);
        y += 11;
        y = CharacterUi.paragraph(g, font, pick.systemDescription(), detailX, y, w, MahouUi.TEXT_DIM);
        y += 7;
        // O que fica / o que se perde
        g.drawString(font, Component.translatable("screen.isekai_mahou.character.class_keep_title").withColor(0x8CE07A), detailX, y, 0xFFFFFFFF, true);
        y += 11;
        y = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.class_keep"), detailX + 4, y, w - 4, MahouUi.TEXT);
        y += 5;
        g.drawString(font, Component.translatable("screen.isekai_mahou.character.class_lose_title").withColor(0xE07A7A), detailX, y, 0xFFFFFFFF, true);
        y += 11;
        y = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.class_lose", now.displayName()),
                detailX + 4, y, w - 4, MahouUi.TEXT);
        y += 4;
        g.disableScissor();
        contentH = y + scroll - contentTop;
        scroll = Mth.clamp(scroll, 0, Math.max(0, contentH - viewH));
        CharacterUi.scrollbar(g, detailX + detailW + 2, contentTop, viewH, scroll, contentH);

        // Rodapé: custo (ou por que não dá)
        g.fill(left + 4, contentBottom + 2, left + panelW - 4, contentBottom + 3, MahouUi.alpha(MahouUi.GOLD_DIM, 0x60));
        int fx = left + PAD + 66, fy = top + panelH - PAD - 20;
        int fw = Math.max(40, (confirmButton == null ? panelW : confirmButton.getX() - 6) - fx);
        if (!now.pickableAtStart()) {
            g.drawString(font, MahouUi.fit(font, Component.translatable("message.isekai_mahou.character.class_story_locked", now.displayName()), fw),
                    fx, fy + 6, 0xFFE07A7A, false);
        } else if (pick == now) {
            g.drawString(font, MahouUi.fit(font, Component.translatable("message.isekai_mahou.character.same_class", now.displayName()), fw),
                    fx, fy + 6, 0xFFE07A7A, false);
        } else {
            costLines(g, Reincarnation.CLASS, fx, fy + 1, fw);
        }
    }

    private void classIcon(GuiGraphics g, MagicPath p, int x, int y, int size) {
        if (GlyphArt.exists(p.iconTexture())) {
            GlyphArt.icon(g, p.iconTexture(), x, y, size);
        } else {
            MahouUi.diamond(g, x + size / 2, y + size / 2, size / 2, p.argb());
        }
    }

    // ---------------------------------------------------------------- Altar do Recomeço
    private void renderRenewal(GuiGraphics g) {
        int x = left + PAD + 4, w = panelW - 2 * PAD - 8;
        int y = contentTop + 4;
        MagicPath now = current();
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.character.renewal_head", now.displayName()), w),
                x, y, MahouUi.TEXT, true);
        y += 16;
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.isekai_mahou.character.renewal_line1"));
        lines.add(Component.translatable("screen.isekai_mahou.character.renewal_line2"));
        lines.add(Component.translatable("screen.isekai_mahou.character.renewal_line3"));
        for (Component line : lines) {
            MahouUi.diamond(g, x + 3, y + 4, 2, 0xFF000000 | GREEN);
            y = CharacterUi.paragraph(g, font, line, x + 10, y, w - 10, 0xFFDDD8EA) + 4;
        }
        y = CharacterUi.paragraph(g, font, Component.translatable("screen.isekai_mahou.character.renewal_other")
                .withStyle(s -> s.withItalic(true)), x, y + 2, w, MahouUi.TEXT_DIM);
        g.fill(left + 4, contentBottom + 2, left + panelW - 4, contentBottom + 3, MahouUi.alpha(MahouUi.GOLD_DIM, 0x60));
        int fw = Math.max(40, (confirmButton == null ? panelW : confirmButton.getX() - 6) - (left + PAD));
        costLines(g, Reincarnation.RENEWAL, left + PAD, top + panelH - PAD - 14, fw);
    }
}
