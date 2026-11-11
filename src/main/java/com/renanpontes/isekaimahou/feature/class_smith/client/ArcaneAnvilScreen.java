package com.renanpontes.isekaimahou.feature.class_smith.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.feature.class_smith.SmithKeys;
import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.class_smith.SmithNet;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeFamily;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeMaterial;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeStats;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgeService;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;
import com.renanpontes.isekaimahou.feature.class_smith.item.ForgedBladeItem;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tela da Bigorna Arcana: à esquerda a arma da mão em tamanho grande (o modelo muda na hora a cada ajuste); à direita
 * as partes — família, comprimento, largura, curva, ponta, serrilha, empunhadura, acabamento e runa — com botões
 * −/+ e ◀/▶; embaixo material (trocar pagando o metal), qualidade (temperar com Cristais de Mana) e os números da arma.
 * Sem lâmina na mão: forjar uma nova (5 materiais) ou converter uma espada/machado/maça vanilla segurada.
 * Todo clique vira um pacote; o servidor confere e responde mexendo no item (que já é sincronizado).
 */
public class ArcaneAnvilScreen extends Screen {
    private static final int PW = 340, PH = 232;
    private final BlockPos anvil;
    private int left, top;
    private int materialPick = 0;
    private final List<Button> bladeButtons = new ArrayList<>();
    private final List<Button> newButtons = new ArrayList<>();
    private Button convertButton;
    private boolean hadBlade;

    public ArcaneAnvilScreen(BlockPos anvil) {
        super(Component.translatable("screen.isekai_mahou.class_smith.anvil"));
        this.anvil = anvil;
    }

    @Override
    protected void init() {
        left = (width - PW) / 2;
        top = (height - PH) / 2;
        bladeButtons.clear();
        newButtons.clear();
        int rx = left + 150, ry = top + 30, rowH = 15;
        // família (◀ ▶)
        cycle(rx, ry, () -> send(SmithNet.OP_FAMILY, blade().family().previous().ordinal()),
                () -> send(SmithNet.OP_FAMILY, blade().family().next().ordinal()));
        // partes (− +)
        for (int i = 0; i < ForgedBlade.Part.VALUES.length; i++) {
            final int part = i;
            int y = ry + rowH * (i + 1);
            bladeButtons.add(addRenderableWidget(Button.builder(Component.literal("−"), b -> send(SmithNet.OP_PART, part * 2))
                    .bounds(rx + 128, y - 2, 13, 12).build()));
            bladeButtons.add(addRenderableWidget(Button.builder(Component.literal("+"), b -> send(SmithNet.OP_PART, part * 2 + 1))
                    .bounds(rx + 172, y - 2, 13, 12).build()));
        }
        int y = ry + rowH * 6;
        cycle(rx, y, () -> send(SmithNet.OP_GRIP, cycleIndex(blade().grip().ordinal(), -1, ForgedBlade.Grip.VALUES.length)),
                () -> send(SmithNet.OP_GRIP, blade().grip().next().ordinal()));
        cycle(rx, y + rowH, () -> send(SmithNet.OP_FINISH, cycleIndex(blade().finish().ordinal(), -1, ForgedBlade.Finish.VALUES.length)),
                () -> send(SmithNet.OP_FINISH, cycleIndex(blade().finish().ordinal(), 1, ForgedBlade.Finish.VALUES.length)));
        cycle(rx, y + rowH * 2, () -> send(SmithNet.OP_RUNE, runeStep(-1)), () -> send(SmithNet.OP_RUNE, runeStep(1)));
        // material: escolhe o alvo e confirma; qualidade: temperar
        int by = top + PH - 52;
        bladeButtons.add(addRenderableWidget(Button.builder(Component.literal("◀"), b -> materialPick = cycleIndex(materialPick, -1, BladeMaterial.VALUES.length))
                .bounds(left + 150, by, 13, 14).build()));
        bladeButtons.add(addRenderableWidget(Button.builder(Component.literal("▶"), b -> materialPick = cycleIndex(materialPick, 1, BladeMaterial.VALUES.length))
                .bounds(left + 232, by, 13, 14).build()));
        bladeButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.class_smith.swap"),
                b -> send(SmithNet.OP_MATERIAL, materialPick)).bounds(left + 248, by, 82, 14).build()));
        bladeButtons.add(addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.class_smith.temper"),
                b -> send(SmithNet.OP_TEMPER, 0)).bounds(left + 248, by + 17, 82, 14).build()));
        // sem lâmina: forjar nova (um botão por material) ou converter a arma vanilla
        for (int i = 0; i < BladeMaterial.VALUES.length; i++) {
            final int m = i;
            newButtons.add(addRenderableWidget(Button.builder(BladeMaterial.VALUES[i].displayName(), b -> send(SmithNet.OP_FORGE_NEW, m))
                    .bounds(left + 150, top + 58 + i * 22, 90, 18).build()));
        }
        convertButton = addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.class_smith.convert"),
                b -> send(SmithNet.OP_CONVERT, 0)).bounds(left + 150, top + 58 + 5 * 22 + 6, 180, 18).build());
        newButtons.add(convertButton);
        addRenderableWidget(Button.builder(net.minecraft.network.chat.CommonComponents.GUI_DONE, b -> onClose()).bounds(left + PW - 58, top + PH - 18, 52, 14).build());
        refresh();
    }

    private void cycle(int rx, int y, Runnable prev, Runnable next) {
        bladeButtons.add(addRenderableWidget(Button.builder(Component.literal("◀"), b -> prev.run()).bounds(rx + 100, y - 2, 13, 12).build()));
        bladeButtons.add(addRenderableWidget(Button.builder(Component.literal("▶"), b -> next.run()).bounds(rx + 172, y - 2, 13, 12).build()));
    }

    private static int cycleIndex(int i, int d, int n) {
        return ((i + d) % n + n) % n;
    }

    /** Próxima runa da lista (nenhuma, Arcano, 6 base) — o servidor pede a essência. */
    private int runeStep(int d) {
        List<Element> options = new ArrayList<>();
        options.add(null);
        options.add(Element.ARCANE);
        options.addAll(Element.BASE);
        Element cur = blade().runeElement();
        int i = Math.max(0, options.indexOf(cur));
        Element next = options.get(cycleIndex(i, d, options.size()));
        return next == null ? -1 : next.ordinal();
    }

    private ItemStack held() {
        var p = Minecraft.getInstance().player;
        if (p == null) return ItemStack.EMPTY;
        if (ForgedBladeItem.is(p.getMainHandItem())) return p.getMainHandItem();
        if (ForgedBladeItem.is(p.getOffhandItem())) return p.getOffhandItem();
        return ItemStack.EMPTY;
    }

    private ForgedBlade blade() {
        return ForgedBladeItem.get(held());
    }

    private boolean convertible() {
        var p = Minecraft.getInstance().player;
        return p != null && ForgeService.convertible(p.getMainHandItem()) != null;
    }

    private void send(int op, int arg) {
        PacketDistributor.sendToServer(new SmithNet.ForgeEdit(anvil, op, arg));
    }

    private void refresh() {
        boolean has = !held().isEmpty();
        hadBlade = has;
        bladeButtons.forEach(b -> b.visible = has);
        newButtons.forEach(b -> b.visible = !has);
        convertButton.visible = !has && convertible();
        if (has) materialPick = blade().material().ordinal();
    }

    @Override
    public void tick() {
        super.tick();
        var p = Minecraft.getInstance().player;
        if (p == null || p.distanceToSqr(anvil.getCenter()) > SmithNet.ANVIL_REACH * SmithNet.ANVIL_REACH) {
            onClose();
            return;
        }
        boolean has = !held().isEmpty();
        if (has != hadBlade) refresh();
        if (!has) convertButton.visible = convertible();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Font font = this.font;
        g.drawString(font, title.copy().withStyle(ChatFormatting.BOLD), left + 10, top + 8, MahouUi.GOLD, true);
        ItemStack stack = held();
        // vitrine da arma
        int vx = left + 8, vy = top + 24, vw = 132, vh = 150;
        MahouUi.roundRect(g, vx, vy, vw, vh, 0xC0302014, 0xC0100804);
        MahouUi.roundOutline(g, vx, vy, vw, vh, MahouUi.GOLD_DIM);
        ItemStack shown = stack.isEmpty() ? (convertible() ? Minecraft.getInstance().player.getMainHandItem() : new ItemStack(Items.ANVIL)) : stack;
        float bob = Mth.sin((Minecraft.getInstance().player.tickCount + pt) * 0.08F) * 2F;
        g.pose().pushPose();
        g.pose().translate(vx + vw / 2F, vy + vh / 2F + bob, 150);
        g.pose().scale(7.5F, 7.5F, 7.5F);
        g.renderItem(shown, -8, -8);
        g.pose().popPose();
        if (stack.isEmpty()) {
            renderNoBlade(g, font);
            return;
        }
        ForgedBlade b = ForgedBladeItem.get(stack);
        BladeStats s = BladeStats.of(b);
        g.drawCenteredString(font, MahouUi.fit(font, stack.getHoverName(), vw - 6), vx + vw / 2, vy + vh + 4, 0xFFFFE8C0);
        g.drawCenteredString(font, Component.literal(ForgedBladeItem.stars(b.quality())), vx + vw / 2, vy + vh + 15, MahouUi.GOLD);

        int rx = left + 150, ry = top + 30, rowH = 15;
        label(g, font, rx, ry, Component.translatable("screen.isekai_mahou.class_smith.row.family"), b.family().familyName(), 0xFFE8D2A0);
        for (int i = 0; i < ForgedBlade.Part.VALUES.length; i++) {
            ForgedBlade.Part part = ForgedBlade.Part.VALUES[i];
            int y = ry + rowH * (i + 1);
            g.drawString(font, part.displayName(), rx, y, MahouUi.TEXT, false);
            // pips da parte
            for (int k = 0; k < part.max; k++) {
                int px = rx + 145 + k * 6;
                g.fill(px, y + 1, px + 4, y + 7, k < b.part(part) ? 0xFFFFB040 : 0xFF3A3040);
            }
        }
        int y = ry + rowH * 6;
        label(g, font, rx, y, Component.translatable("screen.isekai_mahou.class_smith.row.grip"), b.grip().displayName(), MahouUi.TEXT);
        label(g, font, rx, y + rowH, Component.translatable("screen.isekai_mahou.class_smith.row.finish"), b.finish().displayName(), MahouUi.TEXT);
        Element rune = b.runeElement();
        label(g, font, rx, y + rowH * 2, Component.translatable("screen.isekai_mahou.class_smith.row.rune"),
                rune == null ? Component.translatable("screen.isekai_mahou.class_smith.none") : rune.displayName(), MahouUi.TEXT);

        // pontos de forja
        int py = y + rowH * 3 + 2;
        int pts = b.points(), cap = b.capacity();
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_smith.points", pts, cap), rx, py, pts >= cap ? 0xFFFFD86B : MahouUi.TEXT_DIM, false);
        MahouUi.bar(g, rx + 80, py + 1, 100, 6, cap == 0 ? 0 : pts / (float) cap, pts >= cap ? 0xFFFFC040 : 0xFFB06A30);

        // material e qualidade
        int by = top + PH - 52;
        BladeMaterial pick = BladeMaterial.VALUES[materialPick];
        g.drawCenteredString(font, pick.displayName(), left + 197, by + 3, pick == b.material() ? MahouUi.TEXT_DIM : 0xFF9FE6FF);
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_smith.quality", ForgedBladeItem.stars(b.quality())),
                left + 150, by + 20, MahouUi.GOLD, false);

        // números
        int sy = top + PH - 18;
        MutableComponent stats = Component.translatable("screen.isekai_mahou.class_smith.stats", fmt(s.hitDamage()),
                fmt(s.attacksPerSecond()), (s.reach() >= 0 ? "+" : "") + fmt(s.reach()),
                Component.translatable(SmithKeys.BLADE_WEIGHT + s.weightClass()));
        g.drawString(font, MahouUi.fit(font, stats, PW - 75), left + 10, sy + 3, 0xFFD8D0E8, false);
        var d = ClientMagic.data();
        if (d != null) {
            float cost = d.maxMana() * (d.path() == MagicPath.SMITH ? 0.015F : 0.03F);
            g.drawString(font, Component.translatable("screen.isekai_mahou.class_smith.step_cost", Math.round(cost), d.path().resource().displayName()),
                    left + 10, sy - 10, MahouUi.TEXT_DIM, false);
        }
        tooltips(g, font, mx, my, b);
    }

    private void tooltips(GuiGraphics g, Font font, int mx, int my, ForgedBlade b) {
        int by = top + PH - 52;
        if (mx >= left + 248 && mx < left + 330 && my >= by && my < by + 14) {
            BladeMaterial pick = BladeMaterial.VALUES[materialPick];
            g.renderTooltip(font, Component.translatable("screen.isekai_mahou.class_smith.cost", ForgeService.costText(pick.cost())), mx, my);
        } else if (mx >= left + 248 && mx < left + 330 && my >= by + 17 && my < by + 31) {
            g.renderTooltip(font, Component.translatable("screen.isekai_mahou.class_smith.temper_tip", b.quality() + 1,
                    ForgedBlade.capacityOf(b.quality() + 1)), mx, my);
        }
    }

    private void renderNoBlade(GuiGraphics g, Font font) {
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_smith.no_blade"), left + 150, top + 30, MahouUi.TEXT, false);
        g.drawString(font, Component.translatable("screen.isekai_mahou.class_smith.forge_new"), left + 150, top + 44, MahouUi.GOLD, false);
        for (int i = 0; i < BladeMaterial.VALUES.length; i++) {
            BladeMaterial m = BladeMaterial.VALUES[i];
            g.drawString(font, MahouUi.fit(font, ForgeService.costText(m.cost()), 90), left + 244, top + 63 + i * 22, MahouUi.TEXT_DIM, false);
        }
        int ty = top + PH - 30;
        for (var line : font.split(Component.translatable("screen.isekai_mahou.class_smith.no_blade_hint"), PW - 80)) {
            g.drawString(font, line, left + 10, ty, MahouUi.TEXT_DIM, false);
            ty += 10;
        }
    }

    private static void label(GuiGraphics g, Font font, int x, int y, Component name, Component value, int color) {
        g.drawString(font, name, x, y, MahouUi.TEXT, false);
        int w = font.width(value);
        g.drawString(font, value, x + 142 + 0 - w / 2, y, color, false);
    }

    private static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        MahouUi.roundRect(g, left, top, PW, PH, 0xF0201510, 0xF00C0806);
        MahouUi.frame(g, left, top, PW, PH, 0xFFE8742C);
    }
}
