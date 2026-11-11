package com.renanpontes.isekaimahou.client.social;

import java.util.ArrayList;
import java.util.List;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.social.SocialPayloads;
import com.renanpontes.isekaimahou.core.social.SocialPayloads.Profile;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/** Perfil de outro jogador: modelo 3D, classe, nível, Círculo, título, cidade, grupo, duelos, abates e linhas de módulos. */
public class ProfileScreen extends Screen {
    private static final int W = 270, H = 182;
    private final Profile profile;
    private int left, top;

    ProfileScreen(Profile profile) {
        super(Component.translatable("screen.isekai_mahou.profile.title", profile.name()));
        this.profile = profile;
    }

    static void open(Profile p) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.setScreen(new ProfileScreen(p));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
        int by = top + H - 26;
        int bx = left + 90;
        boolean self = minecraft != null && minecraft.player != null && minecraft.player.getUUID().equals(profile.id());
        Button invite = addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.profile.invite"),
                b -> act(SocialPayloads.ACTION_PARTY)).bounds(bx, by, 56, 18).build());
        Button trade = addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.profile.trade"),
                b -> act(SocialPayloads.ACTION_TRADE)).bounds(bx + 59, by, 56, 18).build());
        Button duel = addRenderableWidget(Button.builder(Component.translatable("screen.isekai_mahou.profile.duel"),
                b -> act(SocialPayloads.ACTION_DUEL)).bounds(bx + 118, by, 56, 18).build());
        invite.active = !self && !profile.sameParty();
        trade.active = !self;
        duel.active = !self;
        addRenderableWidget(Button.builder(Component.literal("✕"), b -> onClose()).bounds(left + W - 16, top + 4, 12, 12).build());
    }

    private void act(int action) {
        PacketDistributor.sendToServer(new SocialPayloads.Action(profile.id(), action));
        onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Font font = this.font;
        MagicPath path = MagicPath.byId(profile.path());
        int accent = path.isPlayable() ? path.argb() : MahouUi.GOLD;
        // Modelo (se o jogador está carregado no mundo) ou o rosto
        int mx0 = left + 8, my0 = top + 22;
        g.fillGradient(mx0, my0, mx0 + 74, my0 + 120, 0x60201830, 0x60100C18);
        g.renderOutline(mx0, my0, 74, 120, MahouUi.alpha(accent, 0x90));
        Player p = minecraft == null || minecraft.level == null ? null : minecraft.level.getPlayerByUUID(profile.id());
        if (p != null) {
            InventoryScreen.renderEntityInInventoryFollowsMouse(g, mx0 + 2, my0 + 2, mx0 + 72, my0 + 118, 48, 0.0625F, mx, my, p);
        }
        if (p instanceof AbstractClientPlayer acp) PlayerFaceRenderer.draw(g, acp.getSkin(), mx0 + 3, my0 + 3, 12);

        // Cabeçalho
        int tx = left + 90;
        MutableComponent name = Component.literal(profile.name()).withStyle(ChatFormatting.BOLD);
        g.drawString(font, name, tx, top + 10, MahouUi.GOLD_PALE, true);
        int yy = top + 22;
        if (profile.title().isPresent()) {
            g.drawString(font, MahouUi.fit(font, profile.title().get(), W - 100), tx, yy, MahouUi.GOLD, false);
            yy += 11;
        }
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.isekai_mahou.profile.class", path.displayName()));
        lines.add(Component.translatable("screen.isekai_mahou.profile.level", profile.level(), profile.circle()));
        lines.add(Component.translatable("screen.isekai_mahou.profile.city", profile.allegiance().isPresent()
                ? profile.allegiance().get() : Component.translatable("screen.isekai_mahou.profile.none").withStyle(ChatFormatting.DARK_GRAY)));
        lines.add(profile.partySize() > 0
                ? Component.translatable("screen.isekai_mahou.profile.party", profile.partySize(), profile.partyLeader())
                : Component.translatable("screen.isekai_mahou.profile.party_none"));
        lines.add(Component.translatable("screen.isekai_mahou.profile.duels", profile.duelWins(), profile.duelLosses()));
        lines.add(Component.translatable("screen.isekai_mahou.profile.kills", profile.kills(), profile.bosses()));
        lines.addAll(profile.extra());
        if (profile.sameParty()) lines.add(Component.translatable("screen.isekai_mahou.profile.same_party").withColor(0x7CFF8A));
        if (profile.atWar()) lines.add(Component.translatable("screen.isekai_mahou.profile.at_war").withColor(0xFF6B5A));
        int maxY = top + H - 30;
        for (Component line : lines) {
            if (yy + 9 > maxY) break;
            g.drawString(font, MahouUi.fit(font, line, W - 100), tx, yy, MahouUi.TEXT, false);
            yy += 10;
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        super.renderBackground(g, mx, my, pt);
        MagicPath path = MagicPath.byId(profile.path());
        MahouUi.frame(g, left, top, W, H, path.isPlayable() ? path.argb() : MahouUi.GOLD);
    }
}
