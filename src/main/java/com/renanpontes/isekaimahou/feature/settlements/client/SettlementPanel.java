package com.renanpontes.isekaimahou.feature.settlements.client;

import com.renanpontes.isekaimahou.feature.settlements.SettlementsLang;
import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Painel de uma cidade (usado na tela do coração e na aba "Cidade" do menu B). Desenha tudo à mão com áreas
 * clicáveis, lendo o estado de {@link ClientSettlements}. Páginas do Senhor: Visão geral, Plantas, Obras, Decretos e
 * Guerra; visitante: informações e cidadania; cidade de reino: Juramento e desafio.
 */
public final class SettlementPanel {
    static final int TEXT = 0xFFF3E6C8, MUTED = 0xFFB8A27A, FAINT = 0xFF7D6C52;
    static final int GREEN = 0xFF7DDA6B, RED = 0xFFE8735E, GOLD = 0xFFFFD86B, BLUE = 0xFF8FC8FF;
    static final int CARD = 0xC0201810, CARD_HOVER = 0xE0302418, BORDER = 0xFFC9A55A, BORDER_DIM = 0x70C9A55A;
    private static final String[] ROLES = {"builder", "resident", "guard", "merchant", "militia"};
    private static final ItemStack[] ROLE_ICONS = {new ItemStack(Items.IRON_AXE), new ItemStack(Items.RED_BED),
            new ItemStack(Items.IRON_SWORD), new ItemStack(Items.EMERALD), new ItemStack(Items.SHIELD)};

    private record Hit(int x, int y, int w, int h, Runnable action) {
        boolean contains(double mx, double my) {
            return mx >= x && my >= y && mx < x + w && my < y + h;
        }
    }

    int x, y, w, h;
    private String page = "overview";
    int scroll;
    int maxScroll;
    private final List<Hit> hits = new ArrayList<>();
    @Nullable List<Component> tooltip;
    /** v1.0 (city_life): páginas Felicidade, Caravanas e Defesa. */
    private final CityLifePages life = new CityLifePages(this);
    /** Onda 6 (city_monuments): sub-aba "Bônus" de Geral (monumentos ativos e a bênção). */
    private final MonumentPage monuments = new MonumentPage(this);
    /** Onda 6 (city_trades): sub-aba Comerciantes (dentro de Caravanas). */
    private final CityTradesPage shops = new CityTradesPage(this);
    private String confirm = "";
    private long confirmUntil;
    // ---- v1.4: planejamento
    private int selected = -1;
    private boolean catalogMode;
    /** v1.0 (city_plan): o catálogo aberto pelo botão "Posição livre" — escolher um prédio leva à prévia no mundo. */
    private boolean freeMode;
    private String catStyle = "";
    /** Onda 5 (city_styles): estilo escolhido na Reforma de estilo (sub-aba Porte). */
    private String reformStyle = "";
    private int terrainOffset;
    /** Onda 7: o jogador mexeu na altura deste lote? Então o "Construir" também usa essa altura (cava/aterra). */
    private boolean terrainTouched;
    /** Rolagem da planta (cidades grandes) e a área dela na tela: x, y, largura, altura, rolagem máxima. */
    private int panI = Integer.MIN_VALUE, panJ;
    @Nullable private int[] planGrid;
    private static final java.util.Map<String, ItemStack> ICONS = new java.util.HashMap<>();

    static {
        icon(Items.OAK_DOOR, "house_small");
        icon(Items.RED_BED, "house_large");
        icon(Items.IRON_AXE, "builders_hall");
        icon(Items.WHEAT, "farm");
        icon(Items.HAY_BLOCK, "granary");
        icon(Items.WATER_BUCKET, "well");
        icon(Items.POPPY, "garden");
        icon(Items.FISHING_ROD, "port");
        icon(Items.EMERALD, "market");
        icon(Items.IRON_SWORD, "barracks");
        icon(Items.SPYGLASS, "watchtower");
        icon(Items.HONEY_BOTTLE, "tavern");
        icon(Items.BELL, "temple");
        icon(Items.LECTERN, "town_hall");
        icon(Items.ANVIL, "smithy");
        icon(Items.SADDLE, "stable");
        icon(Items.BREWING_STAND, "hospital");
        icon(Items.OAK_LOG, "sawmill");
        icon(Items.CRAFTING_TABLE, "workshop");
        icon(Items.MINECART, "garage");
        icon(Items.IRON_PICKAXE, "mine");
        icon(Items.ARMOR_STAND, "statue");
        icon(Items.STONE_BRICK_WALL, "arch");
        icon(Items.BOOKSHELF, "library");
        icon(Items.WHITE_BANNER, "guild");
        icon(Items.TARGET, "arena");
        icon(Items.BLAZE_POWDER, "temple_fire");
        icon(Items.PRISMARINE_SHARD, "temple_water");
        icon(Items.MOSS_BLOCK, "temple_earth");
        icon(Items.FEATHER, "temple_wind");
        icon(Items.GLOWSTONE_DUST, "temple_light");
        icon(Items.ECHO_SHARD, "temple_shadow");
        icon(Items.AMETHYST_SHARD, "temple_arcane");
        icon(Items.ENCHANTING_TABLE, "magic_tower");
        icon(Items.IRON_HELMET, "castle");
        icon(Items.CHISELED_SANDSTONE, "obelisk");
        icon(Items.GOLD_BLOCK, "pyramid");
        icon(Items.SANDSTONE, "sphinx");
        icon(Items.HEART_OF_THE_SEA, "arcane_ward");
        icon(Items.CRYING_OBSIDIAN, "null_obelisk");
        icon(Items.AMETHYST_CLUSTER, "mana_spire");
        icon(Items.ENDER_EYE, "arcane_beacon");
        icon(Items.IRON_BARS, "trap_field");
    }

    private static void icon(net.minecraft.world.item.Item item, String key) {
        ICONS.put(key, new ItemStack(item));
    }

    static ItemStack iconFor(String key) {
        ItemStack known = ICONS.get(key);
        // onda 6 (city_monuments): os monumentos novos têm os ícones na página deles
        return known != null ? known : MonumentPage.icon(key);
    }

    public void init(int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
    }

    Font font() {
        return Minecraft.getInstance().font;
    }

    private CompoundTag st() {
        return ClientSettlements.state();
    }

    private String city() {
        return st().getString("City");
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    public void render(GuiGraphics g, int mx, int my, float pt) {
        lastMx = mx;
        lastMy = my;
        hits.clear();
        tooltip = null;
        planGrid = null;
        maxScroll = 0;
        CompoundTag t = st();
        String mode = t.getString("Mode");
        if (mode.isEmpty() || mode.equals("none")) {
            empty(g);
            return;
        }
        int color = 0xFF000000 | (t.contains("Color") ? t.getInt("Color") : 0xFFD86B);
        header(g, t, color);
        int top = y + 34;
        switch (mode) {
            case "manage" -> manage(g, t, mx, my, top, color);
            case "candidate" -> candidate(g, t, mx, my, top);
            default -> visitor(g, t, mx, my, top);
        }
        if (tooltip != null) g.renderComponentTooltip(font(), tooltip, mx, my);
    }

    private void empty(GuiGraphics g) {
        int cy = y + h / 2 - 20;
        center(g, Component.translatable("settlements.isekai_mahou.ui.none.title"), cy, GOLD);
        wrap(g, Component.translatable("settlements.isekai_mahou.ui.none.text"), x + 20, cy + 16, w - 40, MUTED);
    }

    private void header(GuiGraphics g, CompoundTag t, int color) {
        Font f = font();
        g.fill(x, y, x + w, y + 30, 0x90000000);
        g.fill(x, y + 30, x + w, y + 31, color);
        g.pose().pushPose();
        g.pose().translate(x + 6, y + 4, 0);
        g.pose().scale(1.4F, 1.4F, 1F);
        g.drawString(f, ClientSettlements.text(t, "Name"), 0, 0, color, true);
        g.pose().popPose();
        List<Component> sub = new ArrayList<>();
        String kind = t.getString("Kind");
        if (t.contains("Style")) sub.add(SettlementsLang.tr("style." + t.getString("Style")));
        sub.add(SettlementsLang.tr("ui.kind." + kind));
        if (!t.getString("Lord").isEmpty()) sub.add(Component.translatable("settlements.isekai_mahou.ui.lord", t.getString("Lord")));
        // onda 5: o degrau da cidade (Acampamento, Vila, Burgo, Cidade, Capital) vem antes do número
        if (t.contains("Rank")) sub.add(ClientSettlements.text(t.getCompound("Rank"), "Name").copy().withColor(GOLD));
        if (t.contains("Level")) sub.add(Component.translatable("settlements.isekai_mahou.ui.level", t.getInt("Level"), t.getInt("MaxLevel")));
        MutableComponent line = Component.empty();
        for (int i = 0; i < sub.size(); i++) {
            if (i > 0) line.append(Component.literal("  ·  ").withColor(FAINT));
            line.append(sub.get(i));
        }
        g.drawString(f, line, x + 6, y + 19, MUTED, false);
        if (t.getBoolean("Citizen")) {
            Component c = Component.translatable("settlements.isekai_mahou.ui.you_citizen");
            g.drawString(f, c, x + w - f.width(c) - 6, y + 4, GREEN, false);
        }
        if (t.getBoolean("Enemy")) {
            Component c = Component.translatable("settlements.isekai_mahou.ui.you_enemy");
            g.drawString(f, c, x + w - f.width(c) - 6, y + 4, RED, false);
        }
    }

    // =====================================================================================
    //  Senhor
    // =====================================================================================
    private void manage(GuiGraphics g, CompoundTag t, int mx, int my, int top, int color) {
        boolean own = "own".equals(t.getString("Kind"));
        boolean lord = t.getBoolean("Owner");
        // v1.0: Felicidade, Caravanas e Defesa entram como abas; Conselho e Guerra viram sub-abas (a barra não lota)
        String[] pages = own ? (lord ? new String[] {"overview", "plan", "happy", "needs", "works", "trade", "defense", "decrees"}
                : new String[] {"overview", "plan", "happy", "needs", "works", "trade", "defense"})
                : new String[] {"overview", "happy", "needs", "decrees", "war"};
        boolean subs = own && lord;
        // onda 5: "Porte" (o degrau da cidade) é sub-aba de Geral
        String tab = subs && page.equals("council") ? "decrees" : subs && page.equals("war") ? "defense"
                : own && page.equals("tier") ? "overview" : own && page.equals("diary") ? "works" : page;
        // onda 6 (city_monuments): "Bônus" (monumentos ativos) é sub-aba de Geral
        if (own && page.equals("bonus")) tab = "overview";
        // onda 6 (city_trades): "Comerciantes" é sub-aba de Caravanas
        if (own && page.equals("shops")) tab = "trade";
        boolean valid = false;
        for (String p : pages) valid |= p.equals(tab);
        if (!valid) {
            page = "overview";
            tab = page;
        }
        int bw = Math.min(96, (w - 8) / pages.length - 3);
        for (int i = 0; i < pages.length; i++) {
            String p = pages[i];
            button(g, mx, my, x + 4 + i * (bw + 4), top, bw, 14, SettlementsLang.tr("ui.page." + p),
                    true, tab.equals(p), () -> {
                        page = p;
                        scroll = 0;
                    }, List.of(SettlementsLang.tr("ui.page." + p + ".tip")));
        }
        int cy = top + 20;
        boolean rankTabs = own && tab.equals("overview");
        // onda 6 (city_pace): "Diário" (o que andou e o que terminou) é sub-aba de Obras
        boolean workTabs = own && tab.equals("works");
        if (rankTabs || workTabs || subs && (tab.equals("decrees") || tab.equals("defense"))) {
            String[] pair = rankTabs ? new String[] {"overview", "tier", "bonus"} : workTabs ? new String[] {"works", "diary"}
                    : tab.equals("decrees") ? new String[] {"decrees", "council"} : new String[] {"defense", "war"};
            for (int i = 0; i < pair.length; i++) {
                String p = pair[i];
                button(g, mx, my, x + 6 + i * 84, cy, 80, 12, SettlementsLang.tr("ui.page." + p), true, page.equals(p), () -> {
                    page = p;
                    scroll = 0;
                }, null);
            }
            cy += 16;
        }
        // onda 6 (city_trades): Caravanas | Comerciantes (quem a cidade tem, quem falta e qual prédio traz)
        if (own && tab.equals("trade")) {
            String[] tradeTabs = {"trade", "shops"};
            for (int i = 0; i < 2; i++) {
                String p = tradeTabs[i];
                button(g, mx, my, x + 6 + i * 84, cy, 80, 12, SettlementsLang.tr("ui.page." + p), true, page.equals(p), () -> {
                    page = p;
                    scroll = 0;
                }, null);
            }
            cy += 16;
        }
        switch (page) {
            case "happy" -> life.happy(g, t, mx, my, cy);
            case "shops" -> shops.render(g, t, mx, my, cy);
            case "trade" -> life.trade(g, t, mx, my, cy);
            case "defense" -> life.defense(g, t, mx, my, cy);
            case "blueprints" -> blueprints(g, t, mx, my, cy);
            case "plan" -> plan(g, t, mx, my, cy);
            case "needs" -> needs(g, t, mx, my, cy, true);
            case "council" -> council(g, t, mx, my, cy);
            case "works" -> works(g, t, mx, my, cy);
            case "diary" -> diary(g, t, mx, my, cy);
            case "decrees" -> decrees(g, t, mx, my, cy);
            case "war" -> war(g, t, mx, my, cy);
            case "tier" -> tier(g, t, mx, my, cy, color);
            case "bonus" -> monuments.render(g, t, mx, my, cy);
            default -> overview(g, t, mx, my, cy, own, color);
        }
    }

    private void overview(GuiGraphics g, CompoundTag t, int mx, int my, int cy, boolean own, int color) {
        Font f = font();
        int col = (w - 16) / 2;
        int lx = x + 6, rx = x + 10 + col;
        int ly = cy;
        if (own) ly = center(g, t, mx, my, lx, ly, col, color);
        int aff = t.getInt("Affinity");
        CompoundTag hp = t.getCompound("Happy");
        String mood = hp.getString("Mood").isEmpty() ? "content" : hp.getString("Mood");
        int moodColor = CityLifePages.moodColor(mood);
        g.drawString(f, SettlementsLang.tr("ui.happy.title", aff), lx, ly, TEXT, false);
        Component moodName = SettlementsLang.tr("happy.mood." + mood);
        g.drawString(f, moodName, lx + col - f.width(moodName), ly, moodColor, false);
        bar(g, lx, ly + 11, col, 6, aff / 100F, moodColor);
        int d = t.contains("Happy") ? hp.getInt("Delta") : t.getInt("AffDelta");
        g.drawString(f, SettlementsLang.tr("ui.happy.today", (d >= 0 ? "+" : "") + d), lx, ly + 20, d >= 0 ? GREEN : RED, false);
        if (mx >= lx && mx < lx + col && my >= ly && my < ly + 30) tooltip = List.of(SettlementsLang.tr("ui.happy.see"));
        ly += 32;
        if (aff >= 100) ly = wrap(g, Component.translatable("settlements.isekai_mahou.ui.aff_max"), lx, ly, col, GOLD) + 4;
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.treasury", t.getInt("Treasury")), lx, ly, GOLD, false);
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.income", t.getInt("Income"), t.getInt("TradesToday")),
                lx, ly + 10, MUTED, false);
        ly += 24;
        boolean atHeart = t.getBoolean("AtHeart") && t.getBoolean("Owner");
        List<Component> heartTip = atHeart ? null : List.of(Component.translatable("settlements.isekai_mahou.treasury.at_heart"));
        int half = (col - 4) / 2;
        button(g, mx, my, lx, ly, half, 14, Component.translatable("settlements.isekai_mahou.ui.withdraw", 64),
                atHeart && t.getInt("Treasury") > 0, false, () -> act("withdraw", "64"), heartTip);
        // v1.0 (coleta fácil): impostos + mercadorias do armazém num botão só
        int stashed = 0;
        ListTag stash = t.getList("Stash", Tag.TAG_COMPOUND);
        for (int i = 0; i < stash.size(); i++) stashed += stash.getCompound(i).getInt("Count");
        button(g, mx, my, lx + half + 4, ly, half, 14, SettlementsLang.tr("ui.collect_all"),
                atHeart && (t.getInt("Treasury") > 0 || stashed > 0), false, () -> act("collect", ""),
                atHeart ? List.of(SettlementsLang.tr("ui.collect.tip"), SettlementsLang.tr("ui.stash", stashed).withColor(MUTED)) : heartTip);
        ly += 18;
        CompoundTag fest = t.getCompound("Fest");
        if (fest.getInt("Day") > 0) {
            g.drawString(f, SettlementsLang.tr("ui.fest.running", fest.getInt("Day"), Math.max(2, fest.getInt("Days"))), lx, ly, GOLD, false);
            ly += 12;
        }
        // onda 7 (sky_ui): Ilha Celeste — altitude, traço e "a terra está se formando" no pé da coluna (chão: nada)
        SkyPanel.overview(this, g, t, mx, my, lx, ly, col, y + h);
        // onda 8 (water_city): as duas linhas da Cidade das Águas, no mesmo lugar (uma cidade é de um tipo só)
        WaterPanel.overview(this, g, t, mx, my, lx, ly, col, y + h);

        int ry = cy;
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.population"), rx, ry, TEXT, false);
        ry += 12;
        CompoundTag pop = t.getCompound("Pop"), cap = t.getCompound("Cap");
        for (int i = 0; i < ROLES.length; i++) {
            if (cap.getInt(ROLES[i]) <= 0 && pop.getInt(ROLES[i]) <= 0) continue;
            g.renderItem(ROLE_ICONS[i], rx, ry - 2);
            g.drawString(f, SettlementsLang.tr("role." + ROLES[i]), rx + 20, ry + 2, TEXT, false);
            Component n = Component.literal(pop.getInt(ROLES[i]) + " / " + cap.getInt(ROLES[i]));
            g.drawString(f, n, rx + col - f.width(n) - 4, ry + 2, pop.getInt(ROLES[i]) >= cap.getInt(ROLES[i]) ? GREEN : MUTED, false);
            ry += 18;
        }
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.citizens", t.getInt("Citizens")), rx, ry + 2, MUTED, false);
        ry += 14;
        CompoundTag nd = t.getCompound("Needs");
        if (t.contains("Needs")) {
            g.drawString(f, SettlementsLang.tr("ui.people", nd.getInt("People"), nd.getInt("Housing")), rx, ry, TEXT, false);
            ry += 12;
        }
        ry = raidLine(g, t, rx, ry, col);
        wars(g, t, mx, my, rx, ry, col, false);
        if (!t.getBoolean("Owner")) return;
        String key = own ? "abandon" : "abdicate";
        boolean armed = confirm.equals(key) && Util.getMillis() < confirmUntil;
        button(g, mx, my, rx, y + h - 18, col, 14,
                SettlementsLang.tr("ui." + key + (armed ? ".confirm" : "")), true, armed, () -> {
                    if (confirm.equals(key) && Util.getMillis() < confirmUntil) {
                        act(key, "confirm");
                        confirm = "";
                    } else {
                        confirm = key;
                        confirmUntil = Util.getMillis() + 4000;
                    }
                }, List.of(SettlementsLang.tr("ui." + key + ".tip")));
    }

    private void blueprints(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = font();
        ListTag list = t.getList("Blueprints", Tag.TAG_COMPOUND);
        int rowH = 24;
        int area = y + h - cy - 2;
        maxScroll = Math.max(0, list.size() * rowH - area);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(x, cy, x + w, cy + area);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            int ry = cy + i * rowH - scroll;
            if (ry + rowH < cy || ry > cy + area) continue;
            boolean ok = b.getString("Problem").isEmpty();
            boolean hover = mx >= x + 4 && mx < x + w - 4 && my >= ry && my < ry + rowH - 2 && my >= cy && my < cy + area;
            g.fill(x + 4, ry, x + w - 4, ry + rowH - 2, hover && ok ? CARD_HOVER : CARD);
            Component name = Component.translatable(b.getString("Lang"));
            int nameColor = b.getBoolean("Wonder") ? GOLD : TEXT;
            g.drawString(f, b.getBoolean("Wonder") ? Component.literal("★ ").append(name) : name, x + 8, ry + 3, nameColor, false);
            Component info = Component.translatable("settlements.isekai_mahou.ui.bp.info", b.getInt("MinLevel"), b.getInt("Points"),
                    b.getInt("Count") + b.getInt("Building"), b.getInt("Max"));
            g.drawString(f, info, x + 8, ry + 13, FAINT, false);
            Component status = ok ? Component.translatable("settlements.isekai_mahou.ui.bp.build")
                    : ClientSettlements.text(b, "Problem");
            int sw = f.width(status);
            g.drawString(f, status, x + w - 10 - sw, ry + 8, ok ? GREEN : RED, false);
            if (hover && my >= cy && my < cy + area) {
                List<Component> tip = new ArrayList<>();
                tip.add(name.copy().withColor(nameColor));
                for (FormattedCharSequence s : f.split(Component.translatable(b.getString("Lang") + ".desc"), 220)) {
                    tip.add(Component.literal(toString(s)).withColor(MUTED));
                }
                tooltip = tip;
                if (ok) {
                    String key = b.getString("Key");
                    hits.add(new Hit(x + 4, Math.max(ry, cy), w - 8, rowH - 2, () -> act("build", key)));
                }
            }
        }
        g.disableScissor();
        if (list.isEmpty()) wrap(g, Component.translatable("settlements.isekai_mahou.ui.bp.none"), x + 8, cy + 4, w - 16, MUTED);
    }

    private void works(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = font();
        ListTag list = t.getList("Projects", Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            wrap(g, Component.translatable("settlements.isekai_mahou.ui.works.none"), x + 8, cy + 4, w - 16, MUTED);
            return;
        }
        // v1.0: equipe e fila numa linha só; a lista rola (a fila ficou bem maior)
        g.drawString(f, SettlementsLang.tr("ui.works.crew", t.getCompound("Pop").getInt("builder"), t.getCompound("Cap").getInt("builder"),
                list.size(), t.getInt("QueueMax")), x + 8, cy + 1, MUTED, false);
        // onda 6 (city_pace): o ritmo de agora (estilo, prédios de obra, felicidade, festival), à direita
        CompoundTag pace = t.getCompound("Pace");
        // onda 7 (city_pause): "Pausar tudo / Retomar tudo" na ponta direita do cabeçalho (o ritmo fica à esquerda dele)
        int pauseW = CityPauseUi.header(this, g, mx, my, list, cy);
        if (t.contains("Pace")) {
            Component sp = SettlementsLang.tr("ui.pace.speed", pace.getInt("Speed"));
            int sx = x + w - 8 - f.width(sp) - pauseW;
            g.drawString(f, sp, sx, cy + 1, pace.getInt("Speed") > 100 ? GREEN : pace.getInt("Speed") < 100 ? RED : MUTED, false);
            if (mx >= sx && mx < sx + f.width(sp) && my >= cy && my < cy + 11) {
                tooltip = List.of(SettlementsLang.tr("ui.pace.speed.tip1").withColor(TEXT), SettlementsLang.tr("ui.pace.speed.tip2").withColor(MUTED),
                        SettlementsLang.tr("ui.pace.speed.tip3").withColor(MUTED));
            }
        }
        int top = cy + 13, rowH = 30, area = y + h - 16 - top;
        maxScroll = Math.max(0, list.size() * rowH - area);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(x, top, x + w, top + area);
        for (int i = 0; i < list.size(); i++) {
            int ry = top + i * rowH - scroll;
            if (ry + rowH < top || ry > top + area) continue;
            CompoundTag p = list.getCompound(i);
            g.fill(x + 4, ry, x + w - 4, ry + 26, CARD);
            boolean active = p.getBoolean("Active");
            Component pname = p.contains("Name") ? ClientSettlements.text(p, "Name") : Component.translatable(p.getString("Lang"));
            g.drawString(f, pname, x + 8, ry + 3, TEXT, false);
            // onda 7 (city_pause): obra pausada (ou sendo desmontada) troca o "em obra" por um estado de tom discreto
            Component held = CityPauseUi.state(p);
            Component state = held != null ? held
                    : Component.translatable(active ? "settlements.isekai_mahou.ui.works.active" : "settlements.isekai_mahou.ui.works.queued");
            g.drawString(f, state, x + 8 + f.width(pname) + 8, ry + 3, held != null ? CityPauseUi.stateColor(p) : active ? GREEN : FAINT, false);
            // onda 6 (city_pace): "dia 2 de 3 · Paredes" (ou quanto leva, se ainda está na fila), à direita do nome
            if (p.contains("Days")) {
                Component when = p.getBoolean("Started") || active
                        ? SettlementsLang.tr("ui.pace.row", p.getInt("Day"), p.getInt("Days"), ClientSettlements.text(p, "Stage"))
                        : SettlementsLang.tr("ui.pace.takes", ClientSettlements.text(p, "Left"));
                int ww = f.width(when);
                if (x + 8 + f.width(pname) + 8 + f.width(state) + 8 + ww < x + w - 8) {
                    g.drawString(f, when, x + w - 8 - ww, ry + 3, active ? MUTED : FAINT, false);
                }
                if (mx >= x + 4 && mx < x + w - 4 && my >= ry && my < ry + 13 && my >= top && my < top + area) {
                    tooltip = List.of(pname.copy().withColor(TEXT), when.copy().withColor(MUTED),
                            SettlementsLang.tr("ui.pace.left", ClientSettlements.text(p, "Left")).withColor(GOLD));
                }
            }
            // botões à direita da barra: Cancelar (ou Apressar o conserto) e Materiais
            // onda 7 (city_pause): "Desistir" vale em qualquer etapa (apagado, com o motivo, quando só dá para pausar)
            // e cada obra ganha o botão pequeno de pausar/retomar
            boolean cancel = CityPauseUi.showCancel(p), rush = !cancel && p.getBoolean("Rush"), help = p.getBoolean("Help");
            boolean pausable = CityPauseUi.showPause(p);
            int right = x + w - 8;
            int cancelX = 0, helpX = 0, pauseX = 0;
            if (cancel) {
                cancelX = right - CityPauseUi.CANCEL_W;
                right = cancelX - 4;
            } else if (rush) {
                cancelX = right - 66;
                right = cancelX - 4;
            }
            if (pausable) {
                pauseX = right - CityPauseUi.PAUSE_W;
                right = pauseX - 4;
            }
            if (help) {
                helpX = right - 58;
                right = helpX - 4;
            }
            int bw = right - 30 - (x + 8);
            bar(g, x + 8, ry + 15, bw, 6, p.getInt("Pct") / 100F, active ? GOLD : 0xFF6A5A40);
            g.drawString(f, Component.literal(p.getInt("Pct") + "%"), x + 12 + bw, ry + 14, MUTED, false);
            // botões só nas linhas inteiras dentro da lista (nada de clicar num botão cortado pela rolagem)
            if (ry < top || ry + 26 > top + area) continue;
            int idx = i;
            if (pausable) CityPauseUi.pauseButton(this, g, mx, my, pauseX, ry + 11, p, idx);
            if (cancel) {
                CityPauseUi.cancelButton(this, g, mx, my, cancelX, ry + 11, p, idx);
            } else if (rush) {
                int cost = p.getInt("RushCost");
                button(g, mx, my, cancelX, ry + 11, 66, 14, SettlementsLang.tr("ui.rush", cost), t.getInt("Treasury") >= cost, false,
                        () -> act("rush", Integer.toString(idx)), List.of(SettlementsLang.tr("ui.rush.tip", cost)));
            }
            if (help) {
                button(g, mx, my, helpX, ry + 11, 58, 14, SettlementsLang.tr("ui.pace.deliver"), t.getBoolean("InCity"), false,
                        () -> act("pace_deliver", Integer.toString(idx)), List.of(SettlementsLang.tr("ui.pace.deliver.tip1").withColor(TEXT),
                                SettlementsLang.tr("ui.pace.deliver.tip2").withColor(MUTED)));
            }
        }
        g.disableScissor();
        wrap(g, SettlementsLang.tr("ui.pace.hint"), x + 8, y + h - 12, w - 16, FAINT);
    }

    /**
     * Onda 6 (city_pace) — "Diário da cidade": o que começou, o que mudou de etapa, o que foi inaugurado e quem ajudou,
     * do mais novo para o mais antigo, agrupado por dia.
     */
    private void diary(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = font();
        ListTag list = t.getCompound("Pace").getList("Diary", Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            wrap(g, SettlementsLang.tr("ui.diary.none"), x + 8, cy + 4, w - 16, MUTED);
            return;
        }
        int bottom = y + h - 4;
        maxScroll = Math.max(0, diaryHeight - (bottom - cy));
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(x, cy, x + w, bottom);
        int ly = cy - scroll + 2;
        int lastAgo = -1;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int ago = e.getInt("Ago");
            if (ago != lastAgo) {
                lastAgo = ago;
                if (i > 0) ly += 4;
                g.drawString(f, ago == 0 ? SettlementsLang.tr("ui.diary.today") : ago == 1 ? SettlementsLang.tr("ui.diary.yesterday")
                        : SettlementsLang.tr("ui.diary.ago", ago), x + 8, ly, GOLD, false);
                ly += 11;
            }
            ly = wrap(g, Component.literal("• ").append(ClientSettlements.text(e, "Text")), x + 12, ly, w - 24, ago == 0 ? TEXT : MUTED) + 1;
        }
        g.disableScissor();
        diaryHeight = ly + scroll - cy + 4;
    }

    /** Altura do Diário no último quadro (para a rolagem). */
    private int diaryHeight;

    private void decrees(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        int ry = cy;
        ry = options(g, mx, my, ry, "tax", t.getInt("Tax"), 3);
        ry = options(g, mx, my, ry + 4, "gates", t.getInt("Gates"), 3);
        ry = options(g, mx, my, ry + 4, "order", t.getInt("Order"), 2);
        if (!t.getBoolean("WallDone") && "own".equals(t.getString("Kind"))) {
            wrap(g, Component.translatable("settlements.isekai_mahou.ui.decree.no_wall"), x + 8, ry + 2, w - 16, FAINT);
        }
    }

    private int options(GuiGraphics g, int mx, int my, int ry, String decree, int value, int count) {
        Font f = font();
        g.drawString(f, SettlementsLang.tr("ui.decree." + decree), x + 8, ry, TEXT, false);
        ry += 11;
        int bw = Math.min(120, (w - 16) / count - 4);
        for (int i = 0; i < count; i++) {
            int v = i;
            button(g, mx, my, x + 8 + i * (bw + 4), ry, bw, 14,
                    SettlementsLang.tr("ui.decree." + decree + "." + i), true, value == i,
                    () -> act(decree, Integer.toString(v)), null);
        }
        ry += 17;
        return wrap(g, SettlementsLang.tr("ui.decree." + decree + "." + value + ".desc"), x + 8, ry, w - 16, MUTED) + 2;
    }

    private void war(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = font();
        int col = (w - 16) / 2;
        int lx = x + 6, rx = x + 10 + col;
        boolean can = t.getBoolean("CanDeclare");
        int ly = wrap(g, Component.translatable(can ? "settlements.isekai_mahou.ui.war.ready" : "settlements.isekai_mahou.ui.war.need",
                t.getInt("Affinity")), lx, cy, col, can ? GOLD : MUTED) + 4;
        ly = wars(g, t, mx, my, lx, ly, col, true) + 4;
        CompoundTag pop = t.getCompound("Pop"), cap = t.getCompound("Cap");
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.militia", pop.getInt("militia"), cap.getInt("militia")),
                lx, ly, TEXT, false);
        ly += 12;
        button(g, mx, my, lx, ly, col, 14, Component.translatable("settlements.isekai_mahou.ui.train", 25), t.getBoolean("CanTrain"), false,
                () -> act("train", ""), List.of(Component.translatable("settlements.isekai_mahou.ui.train.tip")));
        ly += 18;
        wrap(g, Component.translatable("settlements.isekai_mahou.ui.militia.hint"), lx, ly, col, FAINT);

        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.targets"), rx, cy, TEXT, false);
        ListTag list = t.getList("Targets", Tag.TAG_COMPOUND);
        int top = cy + 12, rowH = 18, area = y + h - top - 2;
        maxScroll = Math.max(0, list.size() * rowH - area);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        g.enableScissor(rx, top, rx + col, top + area);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int ry = top + i * rowH - scroll;
            if (ry + rowH < top || ry > top + area) continue;
            g.fill(rx, ry, rx + col, ry + rowH - 2, CARD);
            Component name = ClientSettlements.text(e, "Name");
            g.drawString(f, f.substrByWidth(name, col - 80).getString(), rx + 4, ry + 4, e.getBoolean("War") ? RED : TEXT, false);
            g.drawString(f, Component.literal(e.getInt("Dist") + "m"), rx + col - 74, ry + 4, FAINT, false);
            if (!e.getBoolean("War") && my >= top && my < top + area) {
                String id = e.getString("Id");
                button(g, mx, my, rx + col - 44, ry + 1, 42, 14, Component.translatable("settlements.isekai_mahou.ui.declare"), can, false,
                        () -> act("declare", id), List.of(Component.translatable("settlements.isekai_mahou.ui.declare.tip")));
            } else if (e.getBoolean("War")) {
                g.drawString(f, Component.literal("⚔"), rx + col - 14, ry + 4, RED, false);
            }
        }
        g.disableScissor();
    }

    /** Lista de guerras (com paz) — devolve o y seguinte. */
    private int wars(GuiGraphics g, CompoundTag t, int mx, int my, int lx, int ly, int col, boolean actions) {
        Font f = font();
        ListTag wars = t.getList("Wars", Tag.TAG_COMPOUND);
        if (wars.isEmpty()) {
            g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.peace"), lx, ly, GREEN, false);
            return ly + 12;
        }
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.at_war"), lx, ly, RED, false);
        ly += 12;
        for (int i = 0; i < wars.size() && i < 5; i++) {
            CompoundTag e = wars.getCompound(i);
            g.drawString(f, Component.literal("⚔ ").append(ClientSettlements.text(e, "Name")), lx + 4, ly + 3, RED, false);
            if (actions) {
                String id = e.getString("Id");
                Component label;
                String action;
                boolean enabled = true;
                List<Component> tip;
                if (e.getBoolean("OfferIn")) {
                    label = Component.translatable("settlements.isekai_mahou.ui.peace.accept");
                    action = "accept_peace";
                    tip = null;
                } else if (e.getBoolean("OfferOut")) {
                    label = Component.translatable("settlements.isekai_mahou.ui.peace.sent");
                    action = "peace";
                    enabled = false;
                    tip = null;
                } else {
                    label = Component.translatable("settlements.isekai_mahou.ui.peace.offer");
                    action = "peace";
                    tip = List.of(Component.translatable(e.getBoolean("PlayerLed") ? "settlements.isekai_mahou.ui.peace.tip_lord"
                            : "settlements.isekai_mahou.ui.peace.tip_tribute", 100));
                }
                button(g, mx, my, lx + col - 70, ly, 68, 14, label, enabled, false, () -> act(action, id), tip);
            }
            ly += 16;
        }
        return ly;
    }

    // =====================================================================================
    //  Visitante e candidato
    // =====================================================================================
    private void visitor(GuiGraphics g, CompoundTag t, int mx, int my, int top) {
        Font f = font();
        int lx = x + 8, col = w - 16;
        int ly = top + 4;
        if (t.contains("Faction")) {
            g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.faction", ClientSettlements.text(t, "Faction"),
                    ClientSettlements.text(t, "Standing")), lx, ly, TEXT, false);
            ly += 12;
        }
        if (t.contains("Affinity")) {
            g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.affinity", t.getInt("Affinity")), lx, ly, TEXT, false);
            ly += 12;
        }
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.citizens", t.getInt("Citizens")), lx, ly, MUTED, false);
        ly += 14;
        ly = wars(g, t, mx, my, lx, ly, col, false) + 6;
        citizenship(g, t, mx, my, lx, ly, Math.min(200, col));
        if (t.getBoolean("Claimable")) {
            button(g, mx, my, lx + Math.min(200, col) + 6, ly, 160, 14, SettlementsLang.tr("ui.claim"), true, false,
                    () -> act("claim", ""), List.of(SettlementsLang.tr("ui.claim.tip")));
        }
        if (t.contains("Needs")) needs(g, t, mx, my, ly + 22, t.getBoolean("Member"));
    }

    private void citizenship(GuiGraphics g, CompoundTag t, int mx, int my, int lx, int ly, int bw) {
        boolean citizen = t.getBoolean("Citizen");
        boolean enemy = t.getBoolean("Enemy");
        button(g, mx, my, lx, ly, bw, 14, Component.translatable(citizen ? "settlements.isekai_mahou.ui.leave" : "settlements.isekai_mahou.ui.join"),
                !enemy, false, () -> act(citizen ? "leave" : "join", ""),
                List.of(Component.translatable(citizen ? "settlements.isekai_mahou.ui.leave.tip" : "settlements.isekai_mahou.ui.join.tip")));
    }

    private void candidate(GuiGraphics g, CompoundTag t, int mx, int my, int top) {
        Font f = font();
        int lx = x + 8, col = w - 16;
        int ly = top + 2;
        if (t.contains("Faction")) {
            g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.faction", ClientSettlements.text(t, "Faction"),
                    ClientSettlements.text(t, "Standing")), lx, ly, TEXT, false);
            ly += 12;
        }
        Component lord = t.getString("Lord").isEmpty() ? Component.translatable("settlements.isekai_mahou.ui.no_lord")
                : Component.translatable("settlements.isekai_mahou.ui.lord", t.getString("Lord"));
        g.drawString(f, lord, lx, ly, MUTED, false);
        ly += 14;
        g.drawString(f, Component.translatable("settlements.isekai_mahou.ui.conquest"), lx, ly, GOLD, false);
        ly += 12;
        boolean exalted = t.getBoolean("Exalted");
        ly = step(g, lx, ly, col, exalted, Component.translatable("settlements.isekai_mahou.ui.step.exalted"));
        boolean active = t.getBoolean("OathActive");
        boolean seal = t.getBoolean("Seal");
        ly = step(g, lx, ly, col, seal, Component.translatable("settlements.isekai_mahou.ui.step.oath"));
        if (!active) {
            if (t.contains("OathOther")) {
                ly = wrap(g, Component.translatable("settlements.isekai_mahou.ui.oath.other", ClientSettlements.text(t, "OathOther")),
                        lx + 14, ly, col - 14, FAINT) + 2;
            }
            button(g, mx, my, lx + 14, ly, 160, 14, Component.translatable("settlements.isekai_mahou.ui.oath.start"), exalted, false,
                    () -> act("oath", ""), List.of(Component.translatable("settlements.isekai_mahou.ui.oath.tip")));
            ly += 18;
        } else {
            int bw = Math.min(160, col / 2);
            ly = progress(g, lx + 14, ly, bw, "kills", t.getInt("Kills"), t.getInt("KillsNeed"));
            int ty = ly;
            ly = progress(g, lx + 14, ly, bw, "tribute", t.getInt("Tribute"), t.getInt("TributeNeed"));
            if (t.getInt("Tribute") < t.getInt("TributeNeed")) {
                button(g, mx, my, lx + 20 + bw + 40, ty, 90, 14, Component.translatable("settlements.isekai_mahou.ui.oath.pay"), true, false,
                        () -> act("tribute", ""), List.of(Component.translatable("settlements.isekai_mahou.ui.oath.pay.tip")));
            }
            ly = progress(g, lx + 14, ly, bw, "vigil", t.getInt("Vigil"), t.getInt("VigilNeed"));
            ly = wrap(g, Component.translatable("settlements.isekai_mahou.ui.oath.vigil_hint"), lx + 14, ly, col - 14, FAINT) + 2;
        }
        ly = step(g, lx, ly, col, false, Component.translatable("settlements.isekai_mahou.ui.step.challenge"));
        int retry = t.getInt("Retry");
        boolean running = t.getBoolean("Running");
        boolean canFight = seal && t.getBoolean("HasSeal") && retry <= 0 && !running;
        Component label = running ? Component.translatable("settlements.isekai_mahou.ui.challenge.running")
                : retry > 0 ? Component.translatable("settlements.isekai_mahou.ui.challenge.retry", retry)
                : Component.translatable("settlements.isekai_mahou.ui.challenge");
        button(g, mx, my, lx + 14, ly, 160, 14, label, canFight, false, () -> act("challenge", ""),
                List.of(Component.translatable("settlements.isekai_mahou.ui.challenge.tip")));
        citizenship(g, t, mx, my, x + w - 168, y + h - 18, 160);
    }

    private int step(GuiGraphics g, int lx, int ly, int col, boolean done, Component text) {
        g.drawString(font(), Component.literal(done ? "✔ " : "• ").append(text), lx, ly, done ? GREEN : TEXT, false);
        return ly + 12;
    }

    private int progress(GuiGraphics g, int lx, int ly, int bw, String key, int cur, int need) {
        Font f = font();
        g.drawString(f, SettlementsLang.tr("ui.oath." + key, Math.min(cur, need), need), lx, ly,
                cur >= need ? GREEN : MUTED, false);
        bar(g, lx, ly + 10, bw, 4, cur / (float) Math.max(1, need), cur >= need ? GREEN : GOLD);
        return ly + 18;
    }

    // =====================================================================================
    //  v1.4: centro, necessidades, planejamento, conselho
    // =====================================================================================
    /** Nível do centro (porte da cidade), requisitos do próximo nível e o botão de reforma. Devolve o y seguinte. */
    private int center(GuiGraphics g, CompoundTag t, int mx, int my, int lx, int ly, int col, int color) {
        Font f = font();
        int lvl = t.getInt("Level");
        g.drawString(f, SettlementsLang.tr("ui.center", lvl, ClientSettlements.text(t, "Tier")), lx, ly, color, false);
        ly += 11;
        ListTag reqs = t.getList("CenterReqs", Tag.TAG_COMPOUND);
        if (reqs.isEmpty()) {
            g.drawString(f, SettlementsLang.tr("ui.center.max"), lx, ly, GOLD, false);
            return ly + 14;
        }
        // onda 5 (tela limpa): aqui só o resumo; a lista do que falta e do que o degrau libera fica na sub-aba "Porte"
        int ok = 0;
        for (int i = 0; i < reqs.size(); i++) if (reqs.getCompound(i).getBoolean("Ok")) ok++;
        boolean busy = t.getBoolean("CenterBusy");
        Component next = ClientSettlements.text(t.getCompound("Rank"), "Next");
        g.drawString(f, busy ? SettlementsLang.tr("ui.center.busy") : SettlementsLang.tr("ui.rank.progress", next, ok, reqs.size()), lx, ly,
                busy ? GOLD : ok >= reqs.size() ? GREEN : MUTED, false);
        ly += 11;
        button(g, mx, my, lx, ly, col, 14, SettlementsLang.tr("ui.rank.open"), true, false, () -> {
            page = "tier";
            scroll = 0;
        }, List.of(SettlementsLang.tr("ui.center.tip")));
        return ly + 20;
    }

    /** Texto (JSON de Component) vindo solto numa lista do servidor. */
    private static Component json(String raw) {
        CompoundTag tmp = new CompoundTag();
        tmp.putString("T", raw);
        return ClientSettlements.text(tmp, "T");
    }

    /**
     * Onda 5 — sub-aba "Porte": a trilha dos degraus (o atual em destaque), à esquerda o que falta para o próximo (com
     * o botão de subir e o custo) e à direita o que ele libera; embaixo, o andamento da reforma da cidade. A página
     * rola se não couber.
     */
    private void tier(GuiGraphics g, CompoundTag t, int mx, int my, int cy, int color) {
        Font f = font();
        CompoundTag rk = t.getCompound("Rank");
        int col = (w - 16) / 2;
        int lx = x + 6, rx = x + 10 + col;
        int lvl = t.getInt("Level");
        int bottom = y + h - 4;
        scroll = Mth.clamp(scroll, 0, Math.max(0, tierHeight - (bottom - cy)));
        maxScroll = Math.max(0, tierHeight - (bottom - cy));
        g.enableScissor(x, cy, x + w, bottom);
        int top = cy - scroll;
        MutableComponent chain = Component.empty();
        ListTag names = rk.getList("Chain", Tag.TAG_STRING);
        for (int i = 0; i < names.size(); i++) {
            if (i > 0) chain.append(Component.literal(" › ").withColor(FAINT));
            chain.append(json(names.getString(i)).copy().withColor(i + 1 == lvl ? color : i + 1 < lvl ? MUTED : FAINT));
        }
        int ly = wrap(g, chain, lx, top, w - 12, TEXT) + 4;
        int ry = ly;
        ListTag reqs = t.getList("CenterReqs", Tag.TAG_COMPOUND);
        boolean max = reqs.isEmpty();
        Component next = ClientSettlements.text(rk, "Next");
        if (max) {
            ly = wrap(g, SettlementsLang.tr("ui.rank.max"), lx, ly, col, GOLD) + 4;
        } else {
            g.drawString(f, SettlementsLang.tr("ui.rank.need", next), lx, ly, TEXT, false);
            ly += 11;
            for (int i = 0; i < reqs.size(); i++) {
                CompoundTag r = reqs.getCompound(i);
                boolean ok = r.getBoolean("Ok");
                ly = wrap(g, Component.literal(ok ? "✔ " : "✘ ").append(ClientSettlements.text(r, "Text")), lx + 4, ly, col - 4, ok ? GREEN : RED);
            }
            boolean busy = t.getBoolean("CenterBusy");
            boolean can = t.getBoolean("Planner") && t.getBoolean("CenterReady") && !busy;
            boolean visible = ly + 2 >= cy && ly + 16 <= bottom;
            button(g, mx, my, lx, ly + 2, col, 14, busy ? SettlementsLang.tr("ui.center.busy")
                            : SettlementsLang.tr("ui.rank.upgrade", next, rk.getInt("Cost")), can && visible, false,
                    () -> act("center_upgrade", ""), List.of(SettlementsLang.tr("ui.rank.upgrade.tip")));
            ly += 19;
            // onda 6 (city_pace): a obra do degrau leva dias — avisa antes
            if (!busy && t.getCompound("Pace").contains("RankDays")) {
                g.drawString(f, SettlementsLang.tr("ui.pace.takes", ClientSettlements.text(t.getCompound("Pace"), "RankDays")), lx, ly, FAINT, false);
                ly += 10;
            }
            ly += 3;
        }
        // onda 5 (city_styles): Reforma de estilo — só o Lorde; custo alto (cofre), obra gradual
        if (t.getBoolean("Owner") && rk.contains("ReformCost")) {
            ly += 4;
            g.drawString(f, SettlementsLang.tr("ui.reform.title"), lx, ly, GOLD, false);
            ly += 11;
            String cur = t.getString("Style");
            List<com.renanpontes.isekaimahou.feature.settlements.SettlementStyle> opts = new ArrayList<>();
            for (var o : com.renanpontes.isekaimahou.feature.settlements.SettlementStyle.playable()) if (!o.id.equals(cur)) opts.add(o);
            var pick = com.renanpontes.isekaimahou.feature.settlements.SettlementStyle.byId(reformStyle);
            if (pick == null || !opts.contains(pick)) pick = opts.get(0);
            var tgt = pick;
            int idx = opts.indexOf(tgt);
            boolean vis = ly >= cy && ly + 14 <= bottom;
            button(g, mx, my, lx, ly, 14, 14, Component.literal("◀"), vis, false, () -> {
                reformStyle = opts.get(Math.floorMod(idx - 1, opts.size())).id;
                confirm = "";
            }, null);
            Component tname = SettlementsLang.tr("style." + tgt.id);
            g.drawString(f, tname, lx + (col - f.width(tname)) / 2, ly + 3, 0xFF000000 | tgt.color, false);
            button(g, mx, my, lx + col - 14, ly, 14, 14, Component.literal("▶"), vis, false, () -> {
                reformStyle = opts.get(Math.floorMod(idx + 1, opts.size())).id;
                confirm = "";
            }, null);
            ly += 17;
            ly = wrap(g, tgt.bonus(), lx, ly, col, MUTED) + 2;
            int pending = rk.getInt("ReformPending");
            if (pending > 0) ly = wrap(g, SettlementsLang.tr("ui.reform.pending", pending), lx, ly, col, FAINT) + 2;
            boolean armed = confirm.equals("reform" + tgt.id) && Util.getMillis() < confirmUntil;
            boolean vis2 = ly >= cy && ly + 14 <= bottom;
            button(g, mx, my, lx, ly, col, 14, SettlementsLang.tr(armed ? "ui.reform.confirm" : "ui.reform.go", tname, rk.getInt("ReformCost")),
                    vis2 && !t.getBoolean("CenterBusy"), armed, () -> {
                        if (confirm.equals("reform" + tgt.id) && Util.getMillis() < confirmUntil) {
                            act("reform_style", tgt.id);
                            confirm = "";
                        } else {
                            confirm = "reform" + tgt.id;
                            confirmUntil = Util.getMillis() + 4000;
                        }
                    }, List.of(SettlementsLang.tr("ui.reform.tip")));
            ly += 20;
        }
        g.drawString(f, max ? SettlementsLang.tr("ui.rank.has") : SettlementsLang.tr("ui.rank.gains", next), rx, ry, GOLD, false);
        ry += 11;
        ListTag gains = rk.getList("Gains", Tag.TAG_STRING);
        for (int i = 0; i < gains.size(); i++) {
            ry = wrap(g, Component.literal("• ").append(json(gains.getString(i))), rx, ry, col - 4, TEXT) + 1;
        }
        int renew = rk.getInt("Renew");
        if (renew >= 0) {
            ry += 4;
            g.drawString(f, SettlementsLang.tr("ui.rank.renew", renew), rx, ry, MUTED, false);
            bar(g, rx, ry + 10, col - 4, 4, renew / 100F, color);
            ry += 18;
        }
        g.disableScissor();
        tierHeight = Math.max(ly, ry) - top + 4;
    }

    /** Altura do conteúdo da sub-aba "Porte" no último quadro (para a rolagem). */
    private int tierHeight;

    /** Linha do ataque de monstros (aviso/andamento). */
    private int raidLine(GuiGraphics g, CompoundTag t, int lx, int ly, int col) {
        int raid = t.contains("Raid") ? t.getInt("Raid") : -1;
        if (raid > 0) {
            ly = wrap(g, SettlementsLang.tr("ui.raid.soon", ClientSettlements.text(t, "RaidSide"), raid), lx, ly, col, RED) + 2;
        } else if (raid == 0) {
            ly = wrap(g, SettlementsLang.tr("ui.raid.now"), lx, ly, col, RED) + 2;
        } else if (t.getBoolean("RaidDue")) {
            ly = wrap(g, SettlementsLang.tr("ui.raid.due"), lx, ly, col, 0xFFE8A440) + 2;
        }
        return ly;
    }

    /** Página "Necessidades": barras, entregas, doação, festival e Pedidos do Povo. */
    private void needs(GuiGraphics g, CompoundTag t, int mx, int my, int cy, boolean member) {
        Font f = font();
        CompoundTag n = t.getCompound("Needs");
        int col = (w - 16) / 2;
        int lx = x + 6, rx = x + 10 + col;
        int ly = cy;
        int days = n.getInt("FoodDays");
        ly = need(g, lx, ly, col, "food", n.getInt("Food"), n.getInt("FoodCap"), 0xFFE8B04A,
                SettlementsLang.tr("ui.need.flow", n.getInt("FoodProd"), n.getInt("FoodUse"), days >= 99 ? "∞" : Integer.toString(days)),
                days < 2 ? RED : MUTED, List.of(SettlementsLang.tr("ui.need.food.tip")));
        int fdays = n.getInt("FuelDays");
        ly = need(g, lx, ly, col, "fuel", n.getInt("Fuel"), n.getInt("FuelCap"), 0xFFB07A4A,
                SettlementsLang.tr("ui.need.flow", n.getInt("FuelProd"), n.getInt("FuelUse"), fdays >= 99 ? "∞" : Integer.toString(fdays)),
                fdays < 2 ? RED : MUTED, List.of(SettlementsLang.tr("ui.need.fuel.tip")));
        int sec = n.getInt("Sec"), secNeed = Math.max(1, n.getInt("SecNeed"));
        ly = need(g, lx, ly, col, "security", sec, secNeed, sec >= secNeed ? GREEN : 0xFFE8A440,
                SettlementsLang.tr("ui.need.security.line", Math.round(sec * 100F / secNeed), n.getInt("RaidsWon"), n.getInt("RaidsLost")),
                sec * 2 < secNeed ? RED : MUTED, List.of(SettlementsLang.tr("ui.need.security.tip")));
        int aff = t.getInt("Affinity");
        ly = need(g, lx, ly, col, "approval", aff, 100, aff >= 70 ? GOLD : aff >= 40 ? 0xFFE07BA8 : RED,
                SettlementsLang.tr("ui.need.approval.line", n.getInt("ProdPct"), n.getInt("People"), n.getInt("Housing")),
                aff < 25 ? RED : MUTED, List.of(SettlementsLang.tr("ui.need.approval.tip")));
        if (member) {
            int half = (col - 4) / 2;
            button(g, mx, my, lx, ly, half, 14, SettlementsLang.tr("ui.deposit"), true, false, () -> act("deposit", ""),
                    List.of(SettlementsLang.tr("ui.deposit.tip")));
            button(g, mx, my, lx + half + 4, ly, half, 14, SettlementsLang.tr("ui.donate"), true, false, () -> act("donate", ""),
                    List.of(SettlementsLang.tr("ui.donate.tip")));
            ly += 18;
            if (t.getBoolean("Planner")) {
                button(g, mx, my, lx, ly, col, 14, SettlementsLang.tr("ui.festival2", 40, n.getInt("Festival")), t.getBoolean("FestivalOk"),
                        false, () -> act("festival", ""), List.of(Component.translatable("settlements.isekai_mahou.ui.festival.tip")));
                ly += 18;
            }
        }
        raidLine(g, t, lx, ly, col);

        g.drawString(f, SettlementsLang.tr("ui.requests"), rx, cy, GOLD, false);
        int ry = cy + 12;
        ListTag rq = t.getList("Requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < rq.size(); i++) {
            CompoundTag r = rq.getCompound(i);
            int got = r.getInt("Got"), need = r.getInt("Need");
            boolean done = got >= need;
            g.fill(rx, ry, rx + col, ry + 24, CARD);
            var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(r.getString("Icon")));
            g.renderItem(new ItemStack(item), rx + 3, ry + 4);
            Component name = ClientSettlements.text(r, "Name");
            g.drawString(f, f.substrByWidth(name, col - 90).getString(), rx + 22, ry + 3, done ? GREEN : TEXT, false);
            g.drawString(f, Component.literal(Math.min(got, need) + " / " + need), rx + 22, ry + 13, done ? GREEN : MUTED, false);
            if (!done && member) {
                int idx = i;
                button(g, mx, my, rx + col - 58, ry + 5, 54, 14, SettlementsLang.tr("ui.deliver"), true, false,
                        () -> act("deliver", Integer.toString(idx)), List.of(SettlementsLang.tr("ui.deliver.tip")));
            } else if (done) {
                g.drawString(f, Component.literal("✔"), rx + col - 12, ry + 8, GREEN, false);
            }
            ry += 27;
        }
        wrap(g, SettlementsLang.tr("ui.requests.hint"), rx, ry + 2, col, FAINT);
    }

    private int need(GuiGraphics g, int lx, int ly, int col, String key, int cur, int max, int color, Component line, int lineColor,
                     List<Component> tip) {
        Font f = font();
        Component title = SettlementsLang.tr("ui.need." + key, cur, max);
        g.drawString(f, title, lx, ly, TEXT, false);
        bar(g, lx, ly + 10, col, 5, cur / (float) Math.max(1, max), color);
        g.drawString(f, line, lx, ly + 17, lineColor, false);
        int mx0 = lastMx, my0 = lastMy;
        if (mx0 >= lx && mx0 < lx + col && my0 >= ly && my0 < ly + 26) tooltip = tip;
        return ly + 29;
    }

    private int lastMx, lastMy;

    /** Página "Planejar": planta da cidade (cresce com a expansão; rola quando não cabe) + o terreno escolhido. */
    private void plan(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = font();
        ListTag plots = t.getList("Plots", Tag.TAG_COMPOUND);
        if (plots.isEmpty()) return;
        int view = Math.max(2, t.getInt("View"));
        int radius = Math.max(2, t.getInt("Radius"));
        int n = 2 * view + 1;
        java.util.Map<Integer, CompoundTag> byCell = new java.util.HashMap<>();
        CompoundTag[][] grid = new CompoundTag[n][n];
        for (int k = 0; k < plots.size(); k++) {
            CompoundTag p = plots.getCompound(k);
            byCell.put(p.getInt("Cell"), p);
            int i = p.contains("I") ? p.getInt("I") : p.getInt("Cell") / 5 - 2;
            int j = p.contains("J") ? p.getInt("J") : p.getInt("Cell") % 5 - 2;
            if (Math.abs(i) <= view && Math.abs(j) <= view) grid[i + view][j + view] = p;
        }
        // v1.0 (city_plan): prédios em posição livre entram na mesma seleção (melhorar, consertar, demolir, mover)
        ListTag freePlots = t.getList("FreePlots", Tag.TAG_COMPOUND);
        for (int k = 0; k < freePlots.size(); k++) byCell.put(freePlots.getCompound(k).getInt("Cell"), freePlots.getCompound(k));
        // tamanho dos quadrados: cabe tudo se der; senão, quadrados de 16 e a planta rola (setas / roda do mouse)
        int areaH = y + h - cy - 57, areaW = w / 2 - 20;
        int fit = Math.min(areaH, areaW) / n;
        int cs = Mth.clamp(fit, 16, 34);
        int vis = Math.max(3, Math.min(n, Math.min(areaH, areaW) / cs));
        int maxPan = n - vis;
        if (panI == Integer.MIN_VALUE || panI > maxPan || panJ > maxPan) {
            panI = maxPan / 2;
            panJ = maxPan / 2;
        }
        panI = Mth.clamp(panI, 0, maxPan);
        panJ = Mth.clamp(panJ, 0, maxPan);
        planGrid = maxPan > 0 ? new int[] {x + 6, cy, vis * cs, vis * cs, maxPan} : null;
        int gx = x + 6, gy = cy;
        int centerLevel = t.getInt("Level");
        for (int a = 0; a < vis; a++) {
            for (int b = 0; b < vis; b++) {
                CompoundTag p = grid[a + panI][b + panJ];
                if (p == null) {
                    // v1.0: o anel de fora não vem no pacote — desenha o terreno "expanda para usar"
                    p = new CompoundTag();
                    p.putInt("Cell", -1000 - a * 100 - b);
                    p.putBoolean("Outside", true);
                }
                // i = x (oeste→leste), j = z (norte→sul)
                plotCell(g, f, p, gx + a * cs, gy + b * cs, cs, mx, my, centerLevel);
            }
        }
        // muralhas desenhadas na planta (entre os anéis 2k e 2k+1)
        ListTag walls = t.getList("Walls", Tag.TAG_COMPOUND);
        g.enableScissor(gx - 2, gy - 2, gx + vis * cs + 1, gy + vis * cs + 1);
        for (int k = 0; k < walls.size(); k++) {
            CompoundTag wl = walls.getCompound(k);
            int state = wl.getInt("State");
            if (state == 0) continue;
            int ring = 2 * wl.getInt("K");
            int x0 = gx + (view - ring - panI) * cs - 2, x1 = gx + (view + ring + 1 - panI) * cs - 1;
            int y0 = gy + (view - ring - panJ) * cs - 2, y1 = gy + (view + ring + 1 - panJ) * cs - 1;
            int col = state == 2 ? 0xFFB0B0B8 : 0xFFE8A440;
            g.fill(x0, y0, x1, y0 + 2, col);
            g.fill(x0, y1 - 1, x1, y1 + 1, col);
            g.fill(x0, y0, x0 + 2, y1 + 1, col);
            g.fill(x1 - 1, y0, x1 + 1, y1 + 1, col);
        }
        // prédios em posição livre: por cima da grade, no lugar em que estão de verdade
        boolean overMap = mx >= gx && my >= gy && mx < gx + vis * cs && my < gy + vis * cs;
        for (int k = 0; k < freePlots.size(); k++) {
            CompoundTag fp = freePlots.getCompound(k);
            int fx = gx + Math.round((fp.getFloat("FI") + view - panI) * cs), fy = gy + Math.round((fp.getFloat("FJ") + view - panJ) * cs);
            if (fx <= gx - cs || fy <= gy - cs || fx >= gx + vis * cs || fy >= gy + vis * cs) continue;
            freePlot(g, f, fp, fx, fy, cs, mx, my, overMap);
        }
        g.disableScissor();

        // embaixo da planta: rolar, expandir, muralhas
        int by0 = gy + vis * cs + 2;
        int gw = vis * cs - 2;
        int bx0 = gx;
        if (maxPan > 0) {
            // setas de rolar a planta: no canto direito da linha das muralhas
            int ax = gx + gw - 62, ay = by0 + 17;
            button(g, mx, my, ax, ay, 14, 14, Component.literal("<"), panI > 0, false, () -> panI--, null);
            button(g, mx, my, ax + 16, ay, 14, 14, Component.literal("^"), panJ > 0, false, () -> panJ--, null);
            button(g, mx, my, ax + 32, ay, 14, 14, Component.literal("v"), panJ < maxPan, false, () -> panJ++, null);
            button(g, mx, my, ax + 48, ay, 14, 14, Component.literal(">"), panI < maxPan, false, () -> panI++, null);
        }
        int size = 2 * radius + 1;
        String exProblem = t.getString("ExpandProblem");
        boolean exMax = t.getBoolean("ExpandMax");
        List<Component> exTip = new ArrayList<>();
        exTip.add(SettlementsLang.tr("ui.plan.expand.size", size, size).withColor(TEXT));
        if (exMax) {
            exTip.add(SettlementsLang.tr("ui.plan.expand.max").withColor(MUTED));
        } else {
            exTip.add(SettlementsLang.tr("ui.plan.expand.tip", size + 2, size + 2).withColor(MUTED));
            ListTag exReqs = t.getList("ExpandReqs", Tag.TAG_COMPOUND);
            for (int k = 0; k < exReqs.size(); k++) {
                CompoundTag r = exReqs.getCompound(k);
                exTip.add(Component.literal(r.getBoolean("Ok") ? "\u2714 " : "\u2718 ").append(ClientSettlements.text(r, "Text"))
                        .withColor(r.getBoolean("Ok") ? GREEN : RED));
            }
            if (exReqs.isEmpty() && !exProblem.isEmpty()) exTip.add(ClientSettlements.text(t, "ExpandProblem").copy().withColor(RED));
        }
        button(g, mx, my, bx0, by0, Math.max(60, gx + gw - bx0), 14,
                exMax ? SettlementsLang.tr("ui.plan.expand.done", size, size) : SettlementsLang.tr("ui.plan.expand", size + 2, size + 2),
                !exMax && exProblem.isEmpty(), false, () -> act("plan_expand", ""), exTip);
        int wy = by0 + 17;
        Component wlabel = SettlementsLang.tr("ui.plan.walls");
        g.drawString(f, wlabel, gx, wy + 3, MUTED, false);
        int wx = gx + f.width(wlabel) + 4;
        for (int k = 0; k < walls.size(); k++) {
            CompoundTag wl = walls.getCompound(k);
            int kk = wl.getInt("K"), state = wl.getInt("State");
            String problem = wl.getString("Problem");
            List<Component> tip = new ArrayList<>();
            tip.add(SettlementsLang.tr("ui.plan.wall.n", roman(kk)).withColor(TEXT));
            tip.add(SettlementsLang.tr(state == 2 ? "ui.plan.wall.done" : state == 1 ? "ui.plan.wall.building" : "ui.plan.wall.tip")
                    .withColor(state == 2 ? GREEN : MUTED));
            if (state == 0 && t.getCompound("Pace").contains("WallDays")) {
                tip.add(SettlementsLang.tr("ui.pace.takes", ClientSettlements.text(t.getCompound("Pace"), "WallDays")).withColor(GOLD));
            }
            if (state == 0 && !problem.isEmpty()) tip.add(ClientSettlements.text(wl, "Problem").copy().withColor(RED));
            Component lbl = Component.literal((state == 2 ? "\u2714" : state == 1 ? "\u2026" : "+") + roman(kk));
            int bw = f.width(lbl) + 8;
            button(g, mx, my, wx, wy, bw, 14, lbl, state == 0 && problem.isEmpty(), state == 2,
                    () -> act("plan_wall", Integer.toString(kk)), tip);
            wx += bw + 2;
        }

        // v1.0 (city_plan): construir fora da grade — escolhe o prédio no catálogo e posiciona no mundo
        boolean freeOpen = catalogMode && freeMode;
        button(g, mx, my, gx, wy + 17, Math.max(60, gw), 14, SettlementsLang.tr("ui.plan.free"), true, freeOpen, () -> {
            boolean open = !(catalogMode && freeMode);
            catalogMode = open;
            freeMode = open;
            scroll = 0;
        }, List.of(SettlementsLang.tr("ui.plan.free.tip").withColor(MUTED)));

        int px = gx + vis * cs + 8, pw = x + w - px - 4;
        if (freeOpen) {
            catalog(g, t, mx, my, px, cy, pw, new CompoundTag());
            return;
        }
        CompoundTag sel = byCell.get(selected);
        if (sel == null || sel.getBoolean("Center")) {
            int hy = wrap(g, SettlementsLang.tr("ui.plan.hint"), px, cy + 2, pw, MUTED) + 6;
            // v1.0: o que falta para a cidade crescer (gente + felicidade + prédios)
            ListTag exReqs = t.getList("ExpandReqs", Tag.TAG_COMPOUND);
            if (!exReqs.isEmpty()) {
                g.drawString(f, SettlementsLang.tr("ui.plan.grow", size + 2, size + 2), px, hy, GOLD, false);
                hy += 11;
                for (int k = 0; k < exReqs.size(); k++) {
                    CompoundTag r = exReqs.getCompound(k);
                    boolean ok = r.getBoolean("Ok");
                    hy = wrap(g, Component.literal(ok ? "\u2714 " : "\u2718 ").append(ClientSettlements.text(r, "Text")), px + 2, hy, pw - 2,
                            ok ? GREEN : RED);
                }
            }
            return;
        }
        CompoundTag p = sel;
        if (p.getBoolean("Outside")) {
            wrap(g, SettlementsLang.tr("ui.plan.outside"), px, cy + 2, pw, RED);
            return;
        }
        if (p.getInt("Unlock") > centerLevel) {
            wrap(g, SettlementsLang.tr("ui.plan.locked", p.getInt("Unlock")), px, cy + 2, pw, RED);
            return;
        }
        if (catalogMode) {
            catalog(g, t, mx, my, px, cy, pw, p);
            return;
        }
        int ly = cy;
        String cell = Integer.toString(selected);
        int terrain = p.getInt("Terrain");
        boolean terrainKnown = p.contains("Terrain");
        Component height = p.contains("Height") ? SettlementsLang.tr("ui.plan.height", signed(p.getInt("Height")))
                : SettlementsLang.tr("ui.plan.height.auto");
        if (p.contains("Busy")) {
            g.drawString(f, ClientSettlements.text(p, "Busy"), px, ly, 0xFFE8A440, false);
            bar(g, px, ly + 11, pw, 5, p.getInt("BusyPct") / 100F, GOLD);
            ly += 20;
            if (p.contains("BusyDays")) {
                g.drawString(f, SettlementsLang.tr("ui.pace.day", p.getInt("BusyDay"), p.getInt("BusyDays")), px, ly, MUTED, false);
                ly += 11;
            }
            ly += 2;
            wrap(g, SettlementsLang.tr("ui.plan.busy"), px, ly, pw, FAINT);
            return;
        }
        if (p.contains("Key")) {
            g.renderItem(iconFor(p.getString("Key")), px, ly);
            g.drawString(f, plotName(p), px + 20, ly + 1, TEXT, false);
            // onda 6 (city_monuments): monumentos param no nível 3
            int maxLv = p.contains("MaxLv") ? p.getInt("MaxLv") : 5;
            g.drawString(f, SettlementsLang.tr("ui.plan.level_style", p.getInt("Level"), Math.max(maxLv, p.getInt("Level")),
                    SettlementsLang.tr("style." + p.getString("Style"))), px + 20, ly + 11, GOLD, false);
            ly += 24;
            ly = wrap(g, SettlementsLang.tr("ui.plan.now", plotFx(p, 0)), px, ly, pw, MUTED) + 2;
            if (p.getInt("Level") < maxLv) ly = wrap(g, SettlementsLang.tr("ui.plan.next", plotFx(p, 1)), px, ly, pw, FAINT) + 2;
            // onda 6 (city_pace): quanto a melhoria leva, antes de enfileirar
            if (p.contains("UpDays")) ly = wrap(g, SettlementsLang.tr("ui.pace.up", ClientSettlements.text(p, "UpDays")), px, ly, pw, FAINT) + 2;
            // onda 8 (city_looks): uma linha com o que muda no VISUAL do prédio no próximo nível
            if (p.getInt("Level") < maxLv) {
                ly = wrap(g, SettlementsLang.tr("ui.looks.next", p.getInt("Level") + 1,
                        SettlementsLang.tr(com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.lineKey(p.getString("Key"), p.getInt("Level") + 1))), px, ly, pw, FAINT) + 2;
            }
            g.drawString(f, height, px, ly, FAINT, false);
            ly += 12;
            int lvl = p.getInt("Level");
            boolean damaged = p.getBoolean("Damaged");
            if (damaged) {
                ly = wrap(g, SettlementsLang.tr("ui.plan.damaged"), px, ly, pw, RED) + 2;
                button(g, mx, my, px, ly, pw, 14, SettlementsLang.tr("ui.plan.repair"), true, false, () -> act("plan_repair", cell),
                        List.of(SettlementsLang.tr("ui.plan.repair.tip")));
                ly += 18;
            }
            boolean canUp = lvl < maxLv && lvl + 1 <= centerLevel && !damaged;
            List<Component> upTip = lvl >= maxLv ? List.of(SettlementsLang.tr("ui.plan.upgrade.max"))
                    : lvl + 1 > centerLevel ? List.of(SettlementsLang.tr("ui.plan.upgrade.cap", centerLevel))
                    : List.of(SettlementsLang.tr("ui.plan.upgrade.tip"));
            button(g, mx, my, px, ly, pw, 14, SettlementsLang.tr("ui.plan.upgrade", Math.min(5, lvl + 1)), canUp, false,
                    () -> act("plan_upgrade", cell), upTip);
            ly += 18;
            // onda 5 (city_styles): prédio de outro estilo (a cidade foi reformada) → refazer no estilo da cidade
            if (p.getBoolean("Restyle")) {
                int rc = com.renanpontes.isekaimahou.feature.settlements.CityReform.buildingCost(lvl, centerLevel);
                button(g, mx, my, px, ly, pw, 14, SettlementsLang.tr("ui.reform.building", SettlementsLang.tr("style." + t.getString("Style")), rc),
                        !damaged, false, () -> act("plan_restyle", cell), List.of(SettlementsLang.tr("ui.reform.building.tip")));
                ly += 18;
            }
            // onda 8 (city_looks): prédio erguido antes do visual novo → "Refazer no visual novo" (barato, nunca automático)
            if (p.getBoolean("OldLook") && !p.getBoolean("Restyle")) {
                int rc = com.renanpontes.isekaimahou.feature.settlements.CityReform.buildingCost(lvl, centerLevel);
                button(g, mx, my, px, ly, pw, 14, SettlementsLang.tr("ui.looks.redo", rc), !damaged, false, () -> act("plan_relook", cell),
                        List.of(SettlementsLang.tr("ui.looks.redo.tip")));
                ly += 18;
            }
            int half = (pw - 4) / 2;
            button(g, mx, my, px, ly, half, 14, SettlementsLang.tr("ui.plan.replace"), true, false, () -> {
                catalogMode = true;
                freeMode = false;
                scroll = 0;
            }, List.of(SettlementsLang.tr("ui.plan.replace.tip")));
            boolean armed = confirm.equals("demolish" + selected) && Util.getMillis() < confirmUntil;
            button(g, mx, my, px + half + 4, ly, half, 14, SettlementsLang.tr(armed ? "ui.plan.demolish.confirm" : "ui.plan.demolish"), true, armed, () -> {
                if (confirm.equals("demolish" + selected) && Util.getMillis() < confirmUntil) {
                    act("plan_demolish", cell);
                    confirm = "";
                } else {
                    confirm = "demolish" + selected;
                    confirmUntil = Util.getMillis() + 4000;
                }
            }, List.of(SettlementsLang.tr("ui.plan.demolish.tip")));
            ly += 18;
            // v1.0 (city_plan): mudar de lugar / girar (custa moedas do cofre; a prévia aparece no mundo)
            Component moveName = plotName(p);
            int moveRot = com.renanpontes.isekaimahou.feature.settlements.build.CityLayout.facingHeart(selected).ordinal();
            String cityId = city();
            int cellId = selected;
            button(g, mx, my, px, ly, pw, 14,
                    SettlementsLang.tr("ui.plan.move", com.renanpontes.isekaimahou.feature.settlements.CityPlots.moveCost(lvl)), !damaged, false,
                    () -> CityPlacer.startMove(cityId, cellId, moveName, moveRot, lvl), List.of(SettlementsLang.tr("ui.plan.move.tip").withColor(MUTED)));
            ly += 18;
            wrap(g, SettlementsLang.tr("ui.plan.containers"), px, ly, pw, FAINT);
            return;
        }
        if (p.getBoolean("Blocked")) {
            wrap(g, SettlementsLang.tr("ui.plan.blocked"), px, ly, pw, MUTED);
            return;
        }
        // terreno vazio
        g.drawString(f, SettlementsLang.tr("ui.plan.empty"), px, ly, TEXT, false);
        ly += 11;
        int tc = terrain == 1 ? 0xFFE8A440 : terrain == 2 ? BLUE : terrain < 0 ? FAINT : GREEN;
        if (terrainKnown) {
            ly = wrap(g, SettlementsLang.tr("ui.plan.terrain." + (terrain < 0 ? "unknown" : Integer.toString(Math.min(2, terrain)))), px, ly, pw, tc) + 1;
        }
        g.drawString(f, height, px, ly, FAINT, false);
        ly += 14;
        // onda 7: com a altura escolhida ali embaixo, o botão avisa que o prédio vai nela ("Construir · altura -1")
        Component buildLabel = terrainTouched ? SettlementsLang.tr("ui.plan.build").copy().append(" · ")
                .append(SettlementsLang.tr("ui.plan.offset", signed(terrainOffset))) : SettlementsLang.tr("ui.plan.build");
        button(g, mx, my, px, ly, pw, 16, buildLabel, true, false, () -> {
            catalogMode = true;
            freeMode = false;
            scroll = 0;
        }, null);
        ly += 22;
        g.drawString(f, SettlementsLang.tr("ui.plan.terraform"), px, ly, TEXT, false);
        ly += 11;
        button(g, mx, my, px, ly, 16, 14, Component.literal("-"), terrainOffset > -4, false, () -> {
            terrainOffset--;
            terrainTouched = true;
        }, null);
        Component off = SettlementsLang.tr("ui.plan.offset", signed(terrainOffset));
        g.drawString(f, off, px + 20 + (60 - f.width(off)) / 2, ly + 3, GOLD, false);
        button(g, mx, my, px + 84, ly, 16, 14, Component.literal("+"), terrainOffset < 4, false, () -> {
            terrainOffset++;
            terrainTouched = true;
        }, null);
        button(g, mx, my, px + 104, ly, Math.max(40, pw - 104), 14, SettlementsLang.tr("ui.plan.terraform.go"), true, false,
                () -> act("plan_terrain", cell + "|" + terrainOffset), List.of(SettlementsLang.tr("ui.plan.terraform.tip")));
        ly += 20;
        wrap(g, SettlementsLang.tr("ui.plan.terraform.hint"), px, ly, pw, FAINT);
    }

    /** Um quadrado da planta. */
    private void plotCell(GuiGraphics g, Font f, CompoundTag p, int bx, int by, int cs, int mx, int my, int centerLevel) {
        int c = p.getInt("Cell");
        boolean hover = mx >= bx && my >= by && mx < bx + cs - 2 && my < by + cs - 2;
        boolean center = p.getBoolean("Center");
        boolean outside = p.getBoolean("Outside");
        boolean locked = !center && (outside || p.getInt("Unlock") > centerLevel);
        int bg = center ? 0xE05A4420 : outside ? 0x60101010 : locked ? 0x90101010 : p.contains("Key") ? 0xC0283818 : 0xC0201810;
        if (hover && !locked) bg = CARD_HOVER;
        g.fill(bx, by, bx + cs - 2, by + cs - 2, bg);
        int border = c == selected ? GOLD : p.getBoolean("Damaged") ? RED : p.contains("Busy") ? 0xFFE8A440 : outside ? 0x30C9A55A : BORDER_DIM;
        g.fill(bx, by, bx + cs - 2, by + 1, border);
        g.fill(bx, by + cs - 3, bx + cs - 2, by + cs - 2, border);
        g.fill(bx, by, bx + 1, by + cs - 2, border);
        g.fill(bx + cs - 3, by, bx + cs - 2, by + cs - 2, border);
        float sc = Math.min(1F, (cs - 4) / 16F);
        if (center) {
            item(g, new ItemStack(Items.BEACON), bx, by, cs, sc);
        } else if (outside) {
            // fora da cidade: vazio (expanda para usar)
        } else if (locked) {
            g.drawString(f, Component.literal("\uD83D\uDD12" + p.getInt("Unlock")), bx + 2, by + 2, FAINT, false);
        } else if (p.contains("Key")) {
            item(g, iconFor(p.getString("Key")), bx, by, cs, sc);
            if (cs >= 18) g.drawString(f, Component.literal(Integer.toString(p.getInt("Level"))), bx + cs - 9, by + cs - 11, GOLD, true);
        } else if (p.contains("Busy")) {
            item(g, new ItemStack(Items.SCAFFOLDING), bx, by, cs, sc);
        } else if (!p.getBoolean("Blocked")) {
            g.drawString(f, Component.literal("+"), bx + (cs - 2) / 2 - 2, by + (cs - 2) / 2 - 4, FAINT, false);
        }
        // onda 7 (sky_ui): terreno da Ilha Celeste cuja terra ainda não se formou (véu azulado + motivo na dica)
        if (!center && !outside) SkyPanel.plotTint(g, c, bx, by, cs - 2);
        if (!hover) return;
        List<Component> tip = new ArrayList<>();
        if (center) tip.add(SettlementsLang.tr("ui.plan.center", centerLevel));
        else if (outside) tip.add(SettlementsLang.tr("ui.plan.outside").withColor(MUTED));
        else if (locked) tip.add(SettlementsLang.tr("ui.plan.locked", p.getInt("Unlock")));
        else if (p.contains("Key")) {
            tip.add(plotName(p).copy().withColor(TEXT));
            tip.add(SettlementsLang.tr("ui.plan.level", p.getInt("Level"), 5).withColor(GOLD));
            tip.add(plotFx(p, 0).copy().withColor(MUTED));
        } else tip.add(SettlementsLang.tr(p.getBoolean("Blocked") ? "ui.plan.blocked" : "ui.plan.empty").withColor(MUTED));
        if (p.contains("Busy")) tip.add(ClientSettlements.text(p, "Busy").copy().append(" " + p.getInt("BusyPct") + "%").withColor(0xFFE8A440));
        if (!center && !outside) SkyPanel.plotTip(tip, c);
        tooltip = tip;
        if (!center) {
            hits.add(new Hit(bx, by, cs - 2, cs - 2, () -> {
                selected = c;
            terrainTouched = false;
            terrainOffset = 0;
                catalogMode = false;
                freeMode = false;
                scroll = 0;
            }));
        }
    }

    /**
     * Um prédio em posição livre (v1.0): desenhado por cima da planta, com borda azul-clara e um traço dourado no lado
     * da entrada. Clicar seleciona como qualquer terreno.
     */
    private void freePlot(GuiGraphics g, Font f, CompoundTag p, int bx, int by, int cs, int mx, int my, boolean overMap) {
        int c = p.getInt("Cell");
        int sz = cs - 2;
        boolean hover = overMap && mx >= bx && my >= by && mx < bx + sz && my < by + sz;
        g.fill(bx, by, bx + sz, by + sz, hover ? CARD_HOVER : 0xF0182838);
        int border = c == selected ? GOLD : p.getBoolean("Damaged") ? RED : p.contains("Busy") ? 0xFFE8A440 : 0xFF6FC3E8;
        g.fill(bx, by, bx + sz, by + 1, border);
        g.fill(bx, by + sz - 1, bx + sz, by + sz, border);
        g.fill(bx, by, bx + 1, by + sz, border);
        g.fill(bx + sz - 1, by, bx + sz, by + sz, border);
        // entrada: 0 = sul (baixo), 1 = oeste (esquerda), 2 = norte (cima), 3 = leste (direita)
        int mid = sz / 2;
        switch (Math.floorMod(p.getInt("Rot"), 4)) {
            case 0 -> g.fill(bx + mid - 3, by + sz - 2, bx + mid + 3, by + sz, GOLD);
            case 1 -> g.fill(bx, by + mid - 3, bx + 2, by + mid + 3, GOLD);
            case 2 -> g.fill(bx + mid - 3, by, bx + mid + 3, by + 2, GOLD);
            default -> g.fill(bx + sz - 2, by + mid - 3, bx + sz, by + mid + 3, GOLD);
        }
        float sc = Math.min(1F, (cs - 4) / 16F);
        item(g, p.contains("Key") ? iconFor(p.getString("Key")) : new ItemStack(Items.SCAFFOLDING), bx, by, cs, sc);
        if (p.contains("Key") && cs >= 18) g.drawString(f, Component.literal(Integer.toString(p.getInt("Level"))), bx + cs - 9, by + cs - 11, GOLD, true);
        if (!hover) return;
        List<Component> tip = new ArrayList<>();
        if (p.contains("Key")) {
            tip.add(plotName(p).copy().withColor(TEXT));
            tip.add(SettlementsLang.tr("ui.plan.level", p.getInt("Level"), 5).withColor(GOLD));
            tip.add(plotFx(p, 0).copy().withColor(MUTED));
        }
        tip.add(SettlementsLang.tr("ui.plan.freeplot").withColor(0xFF6FC3E8));
        if (p.contains("Busy")) tip.add(ClientSettlements.text(p, "Busy").copy().append(" " + p.getInt("BusyPct") + "%").withColor(0xFFE8A440));
        SkyPanel.plotTip(tip, c);
        tooltip = tip;
        hits.add(new Hit(bx, by, sz, sz, () -> {
            selected = c;
            terrainTouched = false;
            terrainOffset = 0;
            catalogMode = false;
            freeMode = false;
            scroll = 0;
        }));
    }

    /** Nome do prédio de um terreno: pela chave de tradução (pacote novo) ou pelo texto pronto (pacote antigo). */
    private static Component plotName(CompoundTag p) {
        return p.contains("Lang") ? Component.translatable(p.getString("Lang")) : ClientSettlements.text(p, "Name");
    }

    /** Efeitos do prédio no nível atual (+{@code up}): calculados aqui com a mesma tabela do servidor. */
    private static Component plotFx(CompoundTag p, int up) {
        if (!p.contains("Lang") && p.contains(up > 0 ? "FxNext" : "Fx")) return ClientSettlements.text(p, up > 0 ? "FxNext" : "Fx");
        return com.renanpontes.isekaimahou.feature.settlements.CityViewExtra.fxLine(p.getString("Key"), Math.min(5, p.getInt("Level") + up));
    }

    private static void item(GuiGraphics g, ItemStack stack, int bx, int by, int cs, float sc) {
        float size = 16 * sc;
        float ix = bx + (cs - 2 - size) / 2F, iy = by + (cs - 2 - size) / 2F;
        g.pose().pushPose();
        g.pose().translate(ix, iy, 0);
        g.pose().scale(sc, sc, 1F);
        g.renderItem(stack, 0, 0);
        g.pose().popPose();
    }

    private static String roman(int k) {
        String[] r = {"I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        return k >= 1 && k <= r.length ? r[k - 1] : Integer.toString(k);
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : Integer.toString(v);
    }

    /** Catálogo de prédios para o terreno escolhido, com o estilo. */
    private void catalog(GuiGraphics g, CompoundTag t, int mx, int my, int px, int cy, int pw, CompoundTag plot) {
        Font f = font();
        if (catStyle.isEmpty()) catStyle = t.getString("Style");
        // onda 5: com 7 estilos os botões lado a lado não cabem — setas (e o próprio nome) trocam o estilo do catálogo
        List<com.renanpontes.isekaimahou.feature.settlements.SettlementStyle> styles =
                com.renanpontes.isekaimahou.feature.settlements.SettlementStyle.playable();
        int si = Math.max(0, styles.indexOf(com.renanpontes.isekaimahou.feature.settlements.SettlementStyle.byId(catStyle)));
        int sx0 = px + 44, sw = pw - 44;
        button(g, mx, my, px, cy, 40, 14, SettlementsLang.tr("ui.back"), true, false, () -> {
            catalogMode = false;
            freeMode = false;
            scroll = 0;
        }, null);
        Runnable prevStyle = () -> {
            catStyle = styles.get(Math.floorMod(si - 1, styles.size())).id;
            scroll = 0;
        };
        Runnable nextStyle = () -> {
            catStyle = styles.get(Math.floorMod(si + 1, styles.size())).id;
            scroll = 0;
        };
        button(g, mx, my, sx0, cy, 14, 14, Component.literal("◀"), true, false, prevStyle, null);
        button(g, mx, my, sx0 + 16, cy, sw - 32, 14, SettlementsLang.tr("style." + styles.get(si).id), true, true, nextStyle,
                List.of(SettlementsLang.tr("ui.plan.style.tip")));
        button(g, mx, my, sx0 + sw - 14, cy, 14, 14, Component.literal("▶"), true, false, nextStyle, null);
        int top = cy + 18, rowH = 30, area = y + h - top - 2;
        ListTag list = t.getList("Catalog", Tag.TAG_COMPOUND);
        maxScroll = Math.max(0, list.size() * rowH - area);
        scroll = Mth.clamp(scroll, 0, maxScroll);
        int centerLevel = t.getInt("Level");
        String current = plot.getString("Key");
        g.enableScissor(px, top, px + pw, top + area);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag b = list.getCompound(i);
            int ry = top + i * rowH - scroll;
            if (ry + rowH < top || ry > top + area) continue;
            String key = b.getString("Key");
            boolean styleOk = false;
            for (Tag s2 : b.getList("Styles", Tag.TAG_STRING)) styleOk |= s2.getAsString().equals(catStyle);
            int count = b.getInt("Count") - (key.equals(current) ? 1 : 0);
            Component days = b.contains("Days") ? ClientSettlements.text(b, "Days") : null;
            Component problem = !styleOk ? SettlementsLang.tr("ui.cat.style")
                    : b.getInt("MinLevel") > centerLevel ? SettlementsLang.tr("ui.cat.level", b.getInt("MinLevel"))
                    : count >= b.getInt("Max") ? SettlementsLang.tr("ui.cat.max") : null;
            boolean hover = mx >= px && mx < px + pw && my >= Math.max(ry, top) && my < Math.min(ry + rowH - 2, top + area);
            g.fill(px, ry, px + pw, ry + rowH - 2, hover && problem == null ? CARD_HOVER : CARD);
            g.renderItem(iconFor(key), px + 3, ry + 6);
            Component name = ClientSettlements.text(b, "Name");
            g.drawString(f, f.substrByWidth(name, pw - 110).getString(), px + 22, ry + 3, b.getBoolean("Wonder") ? GOLD : TEXT, false);
            Component info = SettlementsLang.tr("ui.cat.info", b.getInt("MinLevel"), Math.max(0, count), b.getInt("Max"));
            g.drawString(f, info, px + pw - f.width(info) - 4, ry + 3, FAINT, false);
            // onda 6 (city_pace): os dias da obra à direita ("3 dias"), antes de escolher
            int dw = problem == null && days != null ? f.width(days) + 8 : 0;
            g.drawString(f, f.substrByWidth(ClientSettlements.text(b, "Fx"), pw - 26 - dw).getString(), px + 22, ry + 15, problem == null ? MUTED : FAINT, false);
            if (problem != null) {
                g.drawString(f, problem, px + pw - f.width(problem) - 4, ry + 15, RED, false);
            } else if (days != null) {
                g.drawString(f, days, px + pw - f.width(days) - 4, ry + 15, GOLD, false);
            }
            if (hover) {
                List<Component> tip = new ArrayList<>();
                tip.add(name.copy().withColor(TEXT));
                for (FormattedCharSequence line : f.split(ClientSettlements.text(b, "Desc"), 220)) tip.add(Component.literal(toString(line)).withColor(MUTED));
                if (days != null) tip.add(SettlementsLang.tr("ui.pace.takes", days).withColor(GOLD));
                // onda 8 (city_looks): como o prédio nasce (cada nível depois muda o visual — a linha aparece no lote)
                if (com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.supports(key)) tip.add(SettlementsLang.tr("ui.looks.first", SettlementsLang.tr(com.renanpontes.isekaimahou.feature.settlements.build.LookBuildings.lineKey(key, 1))).withColor(FAINT));
                tip.add(SettlementsLang.tr("ui.pace.gain", ClientSettlements.text(b, "Fx")).withColor(GREEN));
                if (problem != null) tip.add(problem.copy().withColor(RED));
                tooltip = tip;
                if (problem == null) {
                    // onda 7: a altura escolhida no lote vai junto (vazio = automática)
                    String heightArg = terrainTouched ? Integer.toString(terrainOffset) : "";
                    String arg = key + "|" + selected + "|" + catStyle + "|" + heightArg;
                    boolean free = freeMode;
                    String cityId = city(), styleId = catStyle;
                    hits.add(new Hit(px, Math.max(ry, top), pw, rowH - 2, () -> {
                        // posição livre: fecha a tela e o jogador escolhe o lugar no mundo (prévia fantasma)
                        if (free) CityPlacer.startBuild(cityId, key, styleId, name, heightArg);
                        else act("plan_build", arg);
                        catalogMode = false;
                        freeMode = false;
                    }));
                }
            }
        }
        g.disableScissor();
    }

    /** Página "Conselho": cidadãos que podem planejar a cidade (o Senhor escolhe). */
    private void council(GuiGraphics g, CompoundTag t, int mx, int my, int cy) {
        Font f = font();
        int ly = wrap(g, SettlementsLang.tr("ui.council.hint"), x + 8, cy, w - 16, MUTED) + 4;
        ListTag list = t.getList("Council", Tag.TAG_COMPOUND);
        if (list.isEmpty()) {
            wrap(g, SettlementsLang.tr("ui.council.none"), x + 8, ly, w - 16, FAINT);
            return;
        }
        int col = (w - 20) / 2;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            int cx = x + 8 + (i % 2) * (col + 4), ry = ly + (i / 2) * 18;
            if (ry > y + h - 16) break;
            g.fill(cx, ry, cx + col, ry + 16, CARD);
            g.drawString(f, Component.literal((e.getBoolean("Online") ? "● " : "○ ") + e.getString("Name")), cx + 4, ry + 4,
                    e.getBoolean("Council") ? GOLD : TEXT, false);
            boolean on = e.getBoolean("Council");
            String id = e.getUUID("Id").toString();
            button(g, mx, my, cx + col - 84, ry + 1, 82, 14, SettlementsLang.tr(on ? "ui.council.remove" : "ui.council.add"), true, on,
                    () -> act("council", id), null);
        }
    }

    // =====================================================================================
    //  Peças
    // =====================================================================================
    void act(String action, String arg) {
        ClientSettlements.action(city(), action, arg);
    }

    /** Área clicável solta (linhas de lista). */
    void click(int bx, int by, int bw, int bh, Runnable action) {
        hits.add(new Hit(bx, by, bw, bh, action));
    }

    void button(GuiGraphics g, int mx, int my, int bx, int by, int bw, int bh, Component label, boolean enabled,
                        boolean selected, Runnable action, @Nullable List<Component> tip) {
        boolean hover = mx >= bx && my >= by && mx < bx + bw && my < by + bh;
        int bg = !enabled ? 0x80201810 : selected ? 0xE05A4420 : hover ? 0xE0403020 : 0xC0281E14;
        g.fill(bx, by, bx + bw, by + bh, bg);
        int border = selected ? GOLD : enabled ? (hover ? BORDER : BORDER_DIM) : 0x40C9A55A;
        g.fill(bx, by, bx + bw, by + 1, border);
        g.fill(bx, by + bh - 1, bx + bw, by + bh, border);
        g.fill(bx, by, bx + 1, by + bh, border);
        g.fill(bx + bw - 1, by, bx + bw, by + bh, border);
        Font f = font();
        String s = f.substrByWidth(label, bw - 6).getString();
        g.drawString(f, s, bx + (bw - f.width(s)) / 2, by + (bh - 8) / 2, enabled ? (selected ? GOLD : TEXT) : FAINT, false);
        if (enabled) hits.add(new Hit(bx, by, bw, bh, action));
        if (hover && tip != null) tooltip = tip;
    }

    static void bar(GuiGraphics g, int bx, int by, int bw, int bh, float frac, int color) {
        g.fill(bx, by, bx + bw, by + bh, 0xA0000000);
        int fw = (int) (bw * Mth.clamp(frac, 0F, 1F));
        g.fill(bx, by, bx + fw, by + bh, color);
        g.fill(bx, by, bx + fw, by + 1, 0x60FFFFFF);
    }

    int wrap(GuiGraphics g, Component text, int tx, int ty, int tw, int color) {
        Font f = font();
        for (FormattedCharSequence line : f.split(text, Math.max(40, tw))) {
            g.drawString(f, line, tx, ty, color, false);
            ty += 10;
        }
        return ty;
    }

    private void center(GuiGraphics g, Component text, int ty, int color) {
        Font f = font();
        g.drawString(f, text, x + (w - f.width(text)) / 2, ty, color, true);
    }

    static String toString(FormattedCharSequence seq) {
        StringBuilder b = new StringBuilder();
        seq.accept((i, style, cp) -> {
            b.appendCodePoint(cp);
            return true;
        });
        return b.toString();
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    public boolean mouseClicked(double mx, double my, int button) {
        if (button != 0) return false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit h = hits.get(i);
            if (h.contains(mx, my)) {
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                h.action().run();
                return true;
            }
        }
        return false;
    }

    public boolean mouseScrolled(double mx, double my, double dy) {
        int[] pg = planGrid;
        if ("plan".equals(page) && pg != null && mx >= pg[0] && my >= pg[1] && mx < pg[0] + pg[2] && my < pg[1] + pg[3]) {
            // roda = norte/sul; com Shift = oeste/leste
            if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) panI = Mth.clamp(panI - (int) Math.signum(dy), 0, pg[4]);
            else panJ = Mth.clamp(panJ - (int) Math.signum(dy), 0, pg[4]);
            return true;
        }
        if (maxScroll <= 0) return false;
        scroll = Mth.clamp(scroll - (int) (dy * 16), 0, maxScroll);
        return true;
    }
}
