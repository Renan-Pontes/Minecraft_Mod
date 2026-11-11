package com.renanpontes.isekaimahou.feature.ritual_druid.client;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.feature.ritual_druid.DruidNetwork;
import com.renanpontes.isekaimahou.feature.ritual_druid.DruidStages;
import com.renanpontes.isekaimahou.feature.ritual_druid.Harmony;
import com.renanpontes.isekaimahou.feature.ritual_druid.entity.AnimalSpiritEntity;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Tela da Árvore Coração: estágio, Harmonia do bosque por categoria (com dicas), a oferenda e a tarefa de estação
 * do próximo estágio, e o botão para a árvore crescer (só o dono, quando tudo estiver pronto).
 */
public class HeartTreeScreen extends Screen {
    private static final int W = 280, H = 250;
    private static final int BG = 0xE8102117, BG2 = 0xE8183020, BORDER = 0xFF6FBF5A, GOLD = 0xFFFFD86B, TEXT = 0xFFE8F5E0,
            DIM = 0xFF9DB89A, OK = 0xFF7BE495, BAD = 0xFFFF7B6B;

    private DruidNetwork.TreeView view;
    private Button growButton;
    private int left, top;

    /** Uma categoria da Harmonia na tela. */
    private record Cat(String key, int value, int max, int kinds, int rgb, boolean negative) {}

    public HeartTreeScreen(DruidNetwork.TreeView view) {
        super(Component.translatable("gui.isekai_mahou.ritual_druid.title"));
        this.view = view;
    }

    public DruidNetwork.TreeView view() {
        return view;
    }

    /** Chegou uma visão nova da mesma árvore (ex.: depois de tentar crescer). */
    public void update(DruidNetwork.TreeView v) {
        this.view = v;
        refreshButton();
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        growButton = addRenderableWidget(Button.builder(Component.translatable("gui.isekai_mahou.ritual_druid.grow"), b -> {
            PacketDistributor.sendToServer(new DruidNetwork.Grow(view.pos()));
            onClose();
        }).bounds(left + W / 2 - 75, top + H - 27, 150, 20).build());
        refreshButton();
    }

    private boolean ready() {
        if (view.harmonyRequired() < 0) return false;
        if (view.harmony().total() < view.harmonyRequired()) return false;
        if (view.taskDone() < view.taskRequired()) return false;
        for (DruidNetwork.OfferLine o : view.offerings()) if (o.delivered() < o.stack().getCount()) return false;
        return true;
    }

    private void refreshButton() {
        if (growButton == null) return;
        boolean complete = view.harmonyRequired() < 0;
        growButton.visible = view.isOwner() && !complete;
        growButton.active = ready() && !view.growing();
        growButton.setMessage(Component.translatable(view.growing() ? "gui.isekai_mahou.ritual_druid.growing"
                : "gui.isekai_mahou.ritual_druid.grow"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(g, mouseX, mouseY, partialTick);
        g.fillGradient(left, top, left + W, top + H, BG, BG2);
        g.renderOutline(left, top, W, H, BORDER);
        g.renderOutline(left + 2, top + 2, W - 4, H - 4, 0x553E7A35);
        // Folhinhas nos cantos
        int[][] corners = {{left + 4, top + 4}, {left + W - 8, top + 4}, {left + 4, top + H - 8}, {left + W - 8, top + H - 8}};
        for (int[] c : corners) {
            g.fill(c[0], c[1], c[0] + 4, c[1] + 4, 0xFF4FA444);
            g.fill(c[0] + 1, c[1] + 1, c[0] + 3, c[1] + 3, GOLD);
        }
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        List<Component> tooltip = null;
        ItemStack itemTip = ItemStack.EMPTY;

        // ---------------- Cabeçalho ----------------
        g.drawCenteredString(font, title.copy().withStyle(ChatFormatting.BOLD), left + W / 2, top + 8, OK);
        MutableComponent sub = DruidStages.stageName(view.stage()).append(Component.literal(" / " + DruidStages.roman(DruidStages.MAX_STAGE))
                .withStyle(ChatFormatting.GOLD));
        if (!view.ownerName().isEmpty()) {
            sub.append(Component.literal("  ")).append(Component.translatable("gui.isekai_mahou.ritual_druid.owner", view.ownerName())
                    .withStyle(ChatFormatting.GRAY));
        }
        g.drawCenteredString(font, sub, left + W / 2, top + 20, TEXT);
        if (view.growing()) {
            g.drawCenteredString(font, Component.translatable("gui.isekai_mahou.ritual_druid.growing_note"), left + W / 2, top + 31, GOLD);
        }

        // ---------------- Harmonia ----------------
        Harmony h = view.harmony();
        int y = top + 44;
        int req = view.harmonyRequired();
        int total = h.total();
        Component hl = req >= 0 ? Component.translatable("gui.isekai_mahou.ritual_druid.harmony", total, req)
                : Component.translatable("gui.isekai_mahou.ritual_druid.harmony_plain", total);
        g.drawString(font, hl, left + 12, y, req < 0 || total >= req ? OK : TEXT);
        int barW = W - 24;
        float scaleMax = Math.max(req > 0 ? req * 1.25F : 0F, Math.max(total, 1));
        bar(g, left + 12, y + 11, barW, 6, total / scaleMax, total >= req ? OK : 0xFF58A6FF);
        if (req > 0) {
            int mx = left + 12 + Math.round(barW * (req / scaleMax));
            g.fill(mx, y + 9, mx + 1, y + 19, GOLD);
        }

        Cat[] cats = {
                new Cat("flowers", h.flowers(), Harmony.MAX_FLOWERS, h.flowerKinds(), 0xFFFF9EC7, false),
                new Cat("trees", h.trees(), Harmony.MAX_TREES, h.treeKinds(), 0xFF6FBF5A, false),
                new Cat("crops", h.crops(), Harmony.MAX_CROPS, h.cropKinds(), 0xFFE8C14A, false),
                new Cat("animals", h.animals(), Harmony.MAX_ANIMALS, h.animalKinds(), 0xFFD9A06B, false),
                new Cat("water", h.water(), Harmony.MAX_WATER, h.waterBlocks(), 0xFF58A6FF, false),
                new Cat("corruption", h.corruption(), Harmony.MAX_CORRUPTION, h.hostiles(), 0xFFB04BD6, true),
        };
        int colW = (W - 24 - 8) / 2;
        for (int i = 0; i < cats.length; i++) {
            Cat c = cats[i];
            int cx = left + 12 + (i % 2) * (colW + 8);
            int cy = y + 24 + (i / 2) * 18;
            String catKey = "gui.isekai_mahou.ritual_druid.cat." + c.key();
            Component name = Component.translatable(catKey);
            g.drawString(font, name, cx, cy, c.negative() && c.value() > 0 ? BAD : TEXT, false);
            String val = (c.negative() && c.value() > 0 ? "-" : "") + c.value();
            g.drawString(font, val, cx + colW - font.width(val), cy, c.negative() ? (c.value() > 0 ? BAD : OK) : GOLD, false);
            bar(g, cx, cy + 10, colW, 3, c.value() / (float) c.max(), c.rgb());
            if (mouseX >= cx && mouseX < cx + colW && mouseY >= cy - 1 && mouseY < cy + 14) {
                tooltip = categoryTooltip(c, h);
            }
        }

        // ---------------- Próximo estágio: oferenda e tarefa ----------------
        y = top + 128;
        g.fill(left + 10, y - 4, left + W - 10, y - 3, 0x664FA444);
        if (req < 0) {
            g.drawCenteredString(font, Component.translatable("message.isekai_mahou.ritual_druid.complete"), left + W / 2, y + 20, GOLD);
            g.drawCenteredString(font, Component.translatable("gui.isekai_mahou.ritual_druid.complete_note"), left + W / 2, y + 34, DIM);
        } else {
            g.drawString(font, Component.translatable("gui.isekai_mahou.ritual_druid.offering"), left + 12, y, TEXT, false);
            int ix = left + 12;
            for (DruidNetwork.OfferLine o : view.offerings()) {
                int need = o.stack().getCount();
                boolean done = o.delivered() >= need;
                g.fill(ix - 1, y + 11, ix + 17, y + 29, done ? 0x664FBF5A : 0x44000000);
                g.renderItem(o.stack(), ix, y + 12);
                String cnt = Math.min(o.delivered(), need) + "/" + need;
                g.pose().pushPose();
                g.pose().translate(0, 0, 200);
                g.drawString(font, cnt, ix + 8 - font.width(cnt) / 2, y + 31, done ? OK : TEXT, true);
                g.pose().popPose();
                if (mouseX >= ix && mouseX < ix + 16 && mouseY >= y + 12 && mouseY < y + 28) itemTip = o.stack();
                ix += 30;
            }
            Component offerNote = Component.translatable(allOffered() ? "gui.isekai_mahou.ritual_druid.offering_done"
                    : "gui.isekai_mahou.ritual_druid.offering_hint");
            int nx = Math.max(ix + 4, left + 100);
            g.drawWordWrap(font, offerNote, nx, y + 12, left + W - 12 - nx, allOffered() ? OK : DIM);

            int ty = y + 46;
            Component season = view.season() >= 0 ? DruidStages.Season.values()[view.season()].displayName() : Component.empty();
            g.drawString(font, Component.translatable("gui.isekai_mahou.ritual_druid.task_header", season), left + 12, ty, TEXT, false);
            if (view.task() >= 0) {
                DruidStages.Task task = DruidStages.Task.values()[view.task()];
                g.renderItem(new ItemStack(task.icon), left + 12, ty + 11);
                g.drawString(font, task.describe(view.taskRequired()), left + 32, ty + 12, TEXT, false);
                if (view.taskDone() >= 0) {
                    int done = Math.min(view.taskDone(), view.taskRequired());
                    bar(g, left + 32, ty + 23, W - 44 - 40, 4, done / (float) Math.max(1, view.taskRequired()),
                            done >= view.taskRequired() ? OK : GOLD);
                    String p = done + "/" + view.taskRequired();
                    g.drawString(font, p, left + W - 12 - font.width(p), ty + 21, done >= view.taskRequired() ? OK : TEXT, false);
                } else {
                    g.drawString(font, Component.translatable("gui.isekai_mahou.ritual_druid.task_unknown"), left + 32, ty + 22, DIM, false);
                }
            }
            if (view.spirit() >= 0) {
                Component spirit = Component.translatable("gui.isekai_mahou.ritual_druid.spirit_waits",
                        AnimalSpiritEntity.Kind.byIndex(view.spirit()).displayName());
                g.drawCenteredString(font, spirit, left + W / 2, top + H - 40, DIM);
            }
            if (!view.isOwner()) {
                g.drawCenteredString(font, Component.translatable("gui.isekai_mahou.ritual_druid.not_owner"), left + W / 2, top + H - 22, DIM);
            }
        }

        if (tooltip != null) g.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        else if (!itemTip.isEmpty()) g.renderTooltip(font, itemTip, mouseX, mouseY);
    }

    private boolean allOffered() {
        for (DruidNetwork.OfferLine o : view.offerings()) if (o.delivered() < o.stack().getCount()) return false;
        return true;
    }

    private List<Component> categoryTooltip(Cat c, Harmony h) {
        List<Component> lines = new ArrayList<>();
        String base = "gui.isekai_mahou.ritual_druid.cat." + c.key();
        lines.add(Component.translatable(base).withStyle(s -> s.withColor(c.rgb() & 0xFFFFFF)));
        if (c.negative()) {
            lines.add(Component.translatable(base + ".detail", h.hostiles(), h.corruptBlocks()).withStyle(ChatFormatting.GRAY));
        } else {
            lines.add(Component.translatable(base + ".detail", c.kinds()).withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.isekai_mahou.ritual_druid.cat_max", c.value(), c.max()).withStyle(ChatFormatting.DARK_GRAY));
        }
        lines.add(Component.translatable(base + ".hint").withStyle(ChatFormatting.DARK_GREEN, ChatFormatting.ITALIC));
        return lines;
    }

    private static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        frac = Math.max(0F, Math.min(1F, frac));
        g.fill(x, y, x + w, y + h, 0xAA000000);
        g.fill(x, y, x + Math.round(w * frac), y + h, color);
        if (h >= 4) g.fill(x, y, x + Math.round(w * frac), y + 1, 0x55FFFFFF);
    }
}
