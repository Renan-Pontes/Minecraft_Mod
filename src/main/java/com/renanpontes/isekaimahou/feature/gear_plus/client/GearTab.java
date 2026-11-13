package com.renanpontes.isekaimahou.feature.gear_plus.client;

import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.client.menu.MenuTab;
import com.renanpontes.isekaimahou.client.menu.StatusScreen;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.feature.gear_plus.GearNet;
import com.renanpontes.isekaimahou.feature.gear_plus.GearText;
import com.renanpontes.isekaimahou.feature.gear_plus.Gems;
import com.renanpontes.isekaimahou.feature.gear_plus.KitRecovery;
import com.renanpontes.isekaimahou.feature.gear_plus.SetBonuses;
import com.renanpontes.isekaimahou.feature.gear_plus.WeaponBond;
import com.renanpontes.isekaimahou.network.ClientMagic;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Aba "Equipamento" (menu B → Personagem): tudo do módulo num lugar só, em dois painéis.
 * <ul>
 *   <li>Esquerda: o conjunto vestido (peças e bônus) e a arma da classe (Vínculo, abates, habilidade do despertar);</li>
 *   <li>Direita: o kit da classe — cada vaga mostra a peça que a recuperação devolve DE VERDADE (a melhor que o
 *       jogador já carregou; o que falta aparece marcado) —, o botão de recuperar e, para as classes que encaixam, um
 *       resumo das gemas.</li>
 * </ul>
 */
public class GearTab implements MenuTab {
    private static final int PANEL_TOP = 0xC0141020, PANEL_BOTTOM = 0xC00B0912;
    private static final int GOOD = 0xFF7CE08A, WARN = 0xFFE0B060, MISSING = 0xFFE07070;

    private Font font;
    private int x, y, w, h;
    private Button recover;
    private List<ItemStack> kit = List.of();
    private MagicPath kitPath;

    @Override
    public void init(StatusScreen screen, int x, int y, int w, int h) {
        this.x = x;
        this.y = y;
        this.w = w;
        this.h = h;
        this.font = Minecraft.getInstance().font;
        int colW = (w - 12) / 2;
        int bw = Math.min(colW - 12, 150);
        recover = screen.addTabWidget(Button.builder(Component.translatable("screen.isekai_mahou.gear_plus.kit.recover"), b ->
                PacketDistributor.sendToServer(new GearNet.Recover(ClientGear.kitWaitTicks() > 0)))
                .bounds(x + 8 + colW + (colW - bw) / 2, y + h - 24, bw, 16).build());
    }

    /** As peças que o servidor devolveria; antes da 1ª sincronização (ou em servidor antigo), o kit básico. */
    private List<ItemStack> kit(MagicPath path) {
        List<ItemStack> synced = ClientGear.kit(path);
        if (synced != null) return synced;
        if (kitPath != path) {
            kitPath = path;
            kit = KitRecovery.kit(path);
        }
        return kit;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        MagicData md = ClientMagic.data();
        MagicPath path = md.path();
        int colW = (w - 12) / 2;
        int top = y + 2, ph = h - 4;
        int lx = x + 4, rx = x + 8 + colW;
        MahouUi.roundRect(g, lx, top, colW, ph, PANEL_TOP, PANEL_BOTTOM);
        MahouUi.roundRect(g, rx, top, colW, ph, PANEL_TOP, PANEL_BOTTOM);
        ItemStack[] hoverItem = {ItemStack.EMPTY};

        g.enableScissor(lx, top, lx + colW, top + ph);
        renderLeft(g, player, md, path, lx + 6, top + 6, colW - 12);
        g.disableScissor();

        g.enableScissor(rx, top, rx + colW, top + ph);
        renderRight(g, player, md, path, rx + 6, top + 6, colW - 12, mx, my, hoverItem);
        g.disableScissor();

        if (!hoverItem[0].isEmpty()) g.renderTooltip(font, hoverItem[0], mx, my);
    }

    // ---------------------------------------------------------------- Esquerda: conjunto + arma
    private void renderLeft(GuiGraphics g, Player player, MagicData md, MagicPath path, int px, int py, int pw) {
        int cy = title(g, Component.translatable("screen.isekai_mahou.gear_plus.set.title"), px, py, pw);
        // Família em destaque: a do conjunto completo, ou a da primeira peça vestida que faz parte de um conjunto
        String family = null;
        for (EquipmentSlot slot : SetBonuses.ARMOR_SLOTS) {
            family = SetBonuses.familyOf(player.getItemBySlot(slot));
            if (family != null) break;
        }
        SetBonuses.Active active = SetBonuses.active(player);
        if (active != null) family = active.family();
        if (family == null) {
            cy = text(g, Component.translatable("screen.isekai_mahou.gear_plus.set.none"), px, cy, pw, MahouUi.TEXT_DIM);
        } else {
            int[] worn = SetBonuses.worn(player, family);
            boolean complete = active != null;
            cy = text(g, Component.translatable("screen.isekai_mahou.gear_plus.set.pieces", SetBonuses.familyName(family), worn[0],
                    SetBonuses.PIECES), px, cy, pw, complete ? MahouUi.GOLD : MahouUi.TEXT);
            for (SetBonuses.Line line : SetBonuses.lines(family)) {
                cy = text(g, Component.literal(" ").append(line.describe(worn[1])), px, cy, pw, complete ? GOOD : MahouUi.TEXT_DIM);
            }
            if (!complete) {
                cy = text(g, Component.translatable("screen.isekai_mahou.gear_plus.set.incomplete"), px, cy, pw, MahouUi.TEXT_DIM);
            }
        }

        cy += 8;
        cy = title(g, Component.translatable("screen.isekai_mahou.gear_plus.bond.title"), px, cy, pw);
        if (!WeaponBond.hasBond(path)) {
            text(g, Component.translatable("screen.isekai_mahou.gear_plus.bond.none"), px, cy, pw, MahouUi.TEXT_DIM);
            return;
        }
        int kills = ClientGear.kills();
        int level = WeaponBond.level(kills, md.level());
        ItemStack weapon = ClassKits.weapon(path);
        if (!weapon.isEmpty()) g.renderItem(weapon, px, cy);
        int tx = weapon.isEmpty() ? px : px + 20;
        g.drawString(font, MahouUi.fit(font, Component.translatable("screen.isekai_mahou.gear_plus.bond.level", level,
                WeaponBond.MAX_LEVEL), pw - (tx - px)), tx, cy, MahouUi.GOLD, false);
        boolean maxed = level >= WeaponBond.MAX_LEVEL;
        int next = WeaponBond.killsFor(Math.min(WeaponBond.MAX_LEVEL, level + 1));
        int prev = WeaponBond.killsFor(level);
        float frac = maxed || next <= prev ? 1F : Math.min(1F, (kills - prev) / (float) (next - prev));
        MahouUi.bar(g, tx, cy + 10, pw - (tx - px), 4, frac, 0xFF000000 | path.rgb());
        cy += 20;
        if (!maxed) {
            boolean waiting = kills >= WeaponBond.killsFor(WeaponBond.MAX_LEVEL);
            cy = text(g, waiting
                    ? Component.translatable("screen.isekai_mahou.gear_plus.bond.waiting", WeaponBond.AWAKEN_PLAYER_LEVEL)
                    : Component.translatable("screen.isekai_mahou.gear_plus.bond.kills", kills, next), px, cy, pw,
                    waiting ? WARN : MahouUi.TEXT);
        }
        int pct = Math.round(WeaponBond.damageBonus(level) * 1000F);
        cy = text(g, Component.translatable("screen.isekai_mahou.gear_plus.bond.damage", GearText.fmt(pct / 10.0)), px, cy, pw,
                pct > 0 ? GOOD : MahouUi.TEXT_DIM);
        cy += 3;
        cy = text(g, Component.translatable(maxed ? "screen.isekai_mahou.gear_plus.bond.ability"
                : "screen.isekai_mahou.gear_plus.bond.ability_locked", WeaponBond.abilityName(path)), px, cy, pw,
                maxed ? (0xFF000000 | path.rgb()) : MahouUi.TEXT_DIM);
        cy = text(g, WeaponBond.abilityDesc(path), px, cy, pw, MahouUi.TEXT_DIM);
        if (maxed) {
            text(g, Component.translatable("screen.isekai_mahou.gear_plus.bond.how", WeaponBond.RESONANCE_HITS), px, cy, pw, MahouUi.TEXT_DIM);
        }
    }

    // ---------------------------------------------------------------- Direita: kit + gemas
    private void renderRight(GuiGraphics g, Player player, MagicData md, MagicPath path, int px, int py, int pw,
                             int mx, int my, ItemStack[] hoverItem) {
        int cy = title(g, Component.translatable("screen.isekai_mahou.gear_plus.kit.title", path.displayName()), px, py, pw);
        List<ItemStack> items = kit(path);
        int missing = 0;
        int perRow = Math.max(1, pw / 20);
        for (int i = 0; i < items.size(); i++) {
            ItemStack st = items.get(i);
            int ix = px + (i % perRow) * 20, iy = cy + (i / perRow) * 20;
            boolean has = KitRecovery.carries(player, path, st);
            if (!has) {
                missing++;
                MahouUi.roundOutline(g, ix - 1, iy - 1, 18, 18, MISSING);
            }
            g.renderItem(st, ix, iy);
            if (mx >= ix && mx < ix + 16 && my >= iy && my < iy + 16) hoverItem[0] = st;
        }
        cy += ((items.size() + perRow - 1) / perRow) * 20 + 2;

        long wait = ClientGear.kitWaitTicks();
        Component status;
        int color;
        if (items.isEmpty()) {
            status = Component.translatable("screen.isekai_mahou.gear_plus.kit.empty");
            color = MahouUi.TEXT_DIM;
        } else if (missing == 0) {
            status = Component.translatable("screen.isekai_mahou.gear_plus.kit.complete");
            color = GOOD;
        } else if (wait > 0) {
            status = Component.translatable("screen.isekai_mahou.gear_plus.kit.wait", missing, GearText.time(wait),
                    KitRecovery.SKIP_COST, CoreRegistries.MANA_CRYSTAL.get().getDescription());
            color = WARN;
        } else {
            status = Component.translatable("screen.isekai_mahou.gear_plus.kit.ready", missing);
            color = MahouUi.GOLD;
        }
        cy = text(g, status, px, cy, pw, color);
        cy = text(g, Component.translatable("screen.isekai_mahou.gear_plus.kit.note"), px, cy, pw, MahouUi.TEXT_DIM);

        if (recover != null) {
            recover.active = missing > 0;
            recover.setMessage(wait > 0 && missing > 0
                    ? Component.translatable("screen.isekai_mahou.gear_plus.kit.skip", KitRecovery.SKIP_COST)
                    : Component.translatable("screen.isekai_mahou.gear_plus.kit.recover"));
        }

        if (!Gems.canUse(path)) return;
        cy += 8;
        cy = title(g, Component.translatable("screen.isekai_mahou.gear_plus.gems.title"), px, cy, pw);
        int weapon = 0;
        for (int need : Gems.WEAPON_LEVELS) if (md.level() >= need) weapon++;
        cy = text(g, Component.translatable("screen.isekai_mahou.gear_plus.gems.weapon", weapon, Gems.WEAPON_SOCKETS), px, cy, pw,
                weapon > 0 ? MahouUi.TEXT : MahouUi.TEXT_DIM);
        boolean armor = md.level() >= Gems.ARMOR_LEVEL;
        // (como encaixar está na dica da própria gema: aqui só o que o nível já liberou, para caber acima do botão)
        text(g, armor ? Component.translatable("screen.isekai_mahou.gear_plus.gems.armor")
                : Component.translatable("screen.isekai_mahou.gear_plus.gems.armor_locked", Gems.ARMOR_LEVEL), px, cy, pw,
                armor ? MahouUi.TEXT : MahouUi.TEXT_DIM);
    }

    // ---------------------------------------------------------------- Texto
    private int title(GuiGraphics g, Component c, int px, int py, int pw) {
        g.drawString(font, MahouUi.fit(font, c.copy().withStyle(ChatFormatting.BOLD), pw), px, py, MahouUi.GOLD, true);
        g.fill(px, py + 10, px + pw, py + 11, MahouUi.GOLD_DIM);
        return py + 15;
    }

    /** Desenha o texto quebrando em linhas; devolve o y logo abaixo. */
    private int text(GuiGraphics g, Component c, int px, int py, int pw, int color) {
        for (FormattedCharSequence line : font.split(c, pw)) {
            g.drawString(font, line, px, py, color, false);
            py += 10;
        }
        return py;
    }
}
