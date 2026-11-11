package com.renanpontes.isekaimahou.client.social;

import java.util.UUID;

import javax.annotation.Nullable;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.client.MahouUi;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.social.ClientSocialView;
import com.renanpontes.isekaimahou.core.social.Requests;
import com.renanpontes.isekaimahou.core.social.SocialPayloads;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Roda de interação com outro jogador: Agachar + scroll olhando para ele. Quatro opções em cruz — Grupo (cima), Trocar
 * (direita), Duelar (baixo), Perfil (esquerda). Escolha com o mouse (direção), o scroll ou as teclas 1–4; clique/Enter
 * confirma, Esc ou botão direito fecha. Os textos mudam com o estado (ex.: "Aceitar convite" se ele já convidou).
 */
public class InteractionWheelScreen extends Screen {
    private static final double LOOK_RANGE = 8.0;
    private static final int RADIUS = 58;
    private static final int[] ACTIONS = {SocialPayloads.ACTION_PARTY, SocialPayloads.ACTION_TRADE, SocialPayloads.ACTION_DUEL,
            SocialPayloads.ACTION_PROFILE};
    private static final int[] COLORS = {0xFF7CFF8A, 0xFFFFD86B, 0xFFFF7A6B, 0xFF8FD3FF};

    private final UUID target;
    private final String targetName;
    private final ItemStack[] icons = new ItemStack[4];
    private int selected;
    private final long opened = Util.getMillis();
    private boolean mouseMoved;
    private double lastMouseX = -1, lastMouseY = -1;

    InteractionWheelScreen(Player target, int initial) {
        super(Component.translatable("screen.isekai_mahou.wheel.title"));
        this.target = target.getUUID();
        this.targetName = target.getGameProfile().getName();
        this.selected = Mth.clamp(initial, 0, 3);
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.set(DataComponents.PROFILE, new ResolvableProfile(target.getGameProfile()));
        icons[0] = new ItemStack(Items.BELL);
        icons[1] = new ItemStack(Items.EMERALD);
        icons[2] = new ItemStack(Items.IRON_SWORD);
        icons[3] = head;
    }

    // =====================================================================================
    //  Abrir: Agachar + scroll olhando para um jogador
    // =====================================================================================
    static void onScroll(InputEvent.MouseScrollingEvent e) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer me = mc.player;
        if (mc.screen != null || me == null || me.isSpectator() || mc.level == null) return;
        if (e.getScrollDeltaY() == 0 || !mc.options.keyShift.isDown()) return;
        Player target = lookTarget(mc, me);
        if (target == null) return;
        e.setCanceled(true);
        mc.setScreen(new InteractionWheelScreen(target, e.getScrollDeltaY() > 0 ? 0 : 2));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.5F, 0.8F));
    }

    /** Jogador na mira a até 8 blocos, sem parede no meio. */
    @Nullable
    static Player lookTarget(Minecraft mc, LocalPlayer me) {
        float pt = mc.getTimer().getGameTimeDeltaPartialTick(true);
        Vec3 eye = me.getEyePosition(pt);
        Vec3 look = me.getViewVector(pt);
        Vec3 end = eye.add(look.scale(LOOK_RANGE));
        AABB box = me.getBoundingBox().expandTowards(look.scale(LOOK_RANGE)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(me, eye, end, box,
                ent -> ent instanceof Player p && p != me && p.isAlive() && !p.isSpectator() && !p.isInvisibleTo(me),
                LOOK_RANGE * LOOK_RANGE);
        if (hit == null || !(hit.getEntity() instanceof Player p)) return null;
        HitResult wall = mc.level.clip(new ClipContext(eye, hit.getLocation(), ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, me));
        if (wall.getType() != HitResult.Type.MISS && wall.getLocation().distanceToSqr(eye) < hit.getLocation().distanceToSqr(eye) - 0.01) {
            return null;
        }
        return p;
    }

    // =====================================================================================
    //  Opções
    // =====================================================================================
    private record Option(Component label, @Nullable Component hint, boolean enabled) {}

    private Option option(int i) {
        boolean member = ClientSocialView.isMember(target);
        switch (i) {
            case 0 -> {
                if (ClientSocialView.requestFrom(target, Requests.Kind.PARTY.ordinal()) != null) {
                    return new Option(Component.translatable("screen.isekai_mahou.wheel.party_accept"), null, true);
                }
                if (member) return new Option(Component.translatable("screen.isekai_mahou.wheel.party_member"), null, false);
                UUID leader = ClientSocialView.leader();
                Minecraft mc = Minecraft.getInstance();
                if (leader != null && mc.player != null && !leader.equals(mc.player.getUUID())) {
                    return new Option(Component.translatable("screen.isekai_mahou.wheel.party_invite"),
                            Component.translatable("screen.isekai_mahou.wheel.party_not_leader"), false);
                }
                if (ClientSocialView.partyMembers().size() >= Party.MAX_MEMBERS) {
                    return new Option(Component.translatable("screen.isekai_mahou.wheel.party_invite"),
                            Component.translatable("screen.isekai_mahou.wheel.party_full"), false);
                }
                return new Option(Component.translatable("screen.isekai_mahou.wheel.party_invite"), null, true);
            }
            case 1 -> {
                if (ClientSocialView.requestFrom(target, Requests.Kind.TRADE.ordinal()) != null) {
                    return new Option(Component.translatable("screen.isekai_mahou.wheel.trade_accept"), null, true);
                }
                return new Option(Component.translatable("screen.isekai_mahou.wheel.trade"), null, true);
            }
            case 2 -> {
                if (target.equals(ClientSocialView.duelOpponent())) {
                    return new Option(Component.translatable("screen.isekai_mahou.wheel.dueling"), null, false);
                }
                if (ClientSocialView.duelOpponent() != null) {
                    return new Option(Component.translatable("screen.isekai_mahou.wheel.duel"),
                            Component.translatable("screen.isekai_mahou.wheel.duel_busy"), false);
                }
                if (ClientSocialView.requestFrom(target, Requests.Kind.DUEL.ordinal()) != null) {
                    return new Option(Component.translatable("screen.isekai_mahou.wheel.duel_accept"), null, true);
                }
                return new Option(Component.translatable("screen.isekai_mahou.wheel.duel"),
                        member ? Component.translatable("screen.isekai_mahou.wheel.duel_party_hint") : null, true);
            }
            default -> {
                return new Option(Component.translatable("screen.isekai_mahou.wheel.profile"), null, true);
            }
        }
    }

    // =====================================================================================
    //  Entrada
    // =====================================================================================
    private void select(int i) {
        int next = Math.floorMod(i, 4);
        if (next != selected) {
            selected = next;
            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.8F, 0.25F));
        }
    }

    private void confirm() {
        Option o = option(selected);
        Minecraft mc = Minecraft.getInstance();
        if (!o.enabled()) {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.VILLAGER_NO, 1.0F, 0.5F));
            return;
        }
        PacketDistributor.sendToServer(new SocialPayloads.Action(target, ACTIONS[selected]));
        mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_RESONATE, 1.3F, 0.9F));
        onClose();
    }

    @Override
    public void mouseMoved(double mx, double my) {
        if (lastMouseX >= 0 && (Math.abs(mx - lastMouseX) > 0.5 || Math.abs(my - lastMouseY) > 0.5)) mouseMoved = true;
        lastMouseX = mx;
        lastMouseY = my;
        if (!mouseMoved) return;
        double dx = mx - width / 2.0, dy = my - height / 2.0;
        if (dx * dx + dy * dy < 14 * 14) return;
        // 0 cima, 1 direita, 2 baixo, 3 esquerda
        double ang = Math.atan2(dx, -dy);
        select((int) Math.round(ang / (Math.PI / 2)));
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (sy != 0) select(selected + (sy > 0 ? -1 : 1));
        return true;
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (button == 0) {
            mouseMoved(mx, my);
            confirm();
            return true;
        }
        if (button == 1) {
            onClose();
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER || key == GLFW.GLFW_KEY_SPACE) {
            confirm();
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_4) {
            select(key - GLFW.GLFW_KEY_1);
            confirm();
            return true;
        }
        if (key == GLFW.GLFW_KEY_UP || key == GLFW.GLFW_KEY_W) select(0);
        else if (key == GLFW.GLFW_KEY_RIGHT || key == GLFW.GLFW_KEY_D) select(1);
        else if (key == GLFW.GLFW_KEY_DOWN || key == GLFW.GLFW_KEY_S) select(2);
        else if (key == GLFW.GLFW_KEY_LEFT || key == GLFW.GLFW_KEY_A) select(3);
        else return super.keyPressed(key, scan, mods);
        return true;
    }

    @Override
    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            onClose();
            return;
        }
        Player p = mc.level.getPlayerByUUID(target);
        if (p == null || !p.isAlive() || p.distanceTo(mc.player) > Party.INTERACT_RANGE + 2) {
            mc.player.displayClientMessage(Component.translatable("screen.isekai_mahou.wheel.gone", targetName), true);
            onClose();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // =====================================================================================
    //  Desenho
    // =====================================================================================
    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {
        // Vinheta leve: o mundo continua visível atrás da roda
        g.fillGradient(0, 0, width, height, 0x30000000, 0x60000000);
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        super.render(g, mx, my, pt);
        Font font = this.font;
        int cx = width / 2, cy = height / 2;
        float open = Mth.clamp((Util.getMillis() - opened) / 140F, 0F, 1F);
        float ease = 1F - (1F - open) * (1F - open);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0);
        g.pose().scale(0.7F + 0.3F * ease, 0.7F + 0.3F * ease, 1F);
        g.pose().translate(-cx, -cy, 0);

        // Anel e raios
        ring(g, cx, cy, RADIUS - 18, MahouUi.alpha(COLORS[selected], 0x50));
        for (int i = 0; i < 4; i++) {
            int[] p = pos(i, cx, cy, RADIUS - 30);
            MahouUi.diamond(g, p[0], p[1], i == selected ? 3 : 2, i == selected ? COLORS[i] : 0x80FFFFFF);
        }

        // Centro: rosto + nome
        int face = 24;
        MahouUi.roundRect(g, cx - face / 2 - 3, cy - face / 2 - 3, face + 6, face + 6, 0xE0201830, 0xE0100C18);
        MahouUi.roundOutline(g, cx - face / 2 - 3, cy - face / 2 - 3, face + 6, face + 6, MahouUi.alpha(COLORS[selected], 0xD0));
        Minecraft mc = Minecraft.getInstance();
        Player tp = mc.level == null ? null : mc.level.getPlayerByUUID(target);
        if (tp instanceof AbstractClientPlayer acp) PlayerFaceRenderer.draw(g, acp.getSkin(), cx - face / 2, cy - face / 2, face);
        Component name = Component.literal(targetName).withStyle(s -> s.withBold(true));
        int tagColor = ClientSocialView.isMember(target) ? 0xFF7CFF8A : ClientSocialView.isEnemy(target) ? 0xFFFF6B5A : MahouUi.GOLD_PALE;
        int nw = font.width(name);
        MahouUi.roundRect(g, cx - nw / 2 - 4, cy + face / 2 + 6, nw + 8, 12, 0xD0181226, 0xD00B0912);
        g.drawString(font, name, cx - nw / 2, cy + face / 2 + 8, tagColor, true);

        // Opções
        for (int i = 0; i < 4; i++) drawOption(g, font, i, cx, cy);
        g.pose().popPose();

        // Dica de uso
        Component tip = Component.translatable("screen.isekai_mahou.wheel.tip");
        g.drawCenteredString(font, tip, cx, height - 30, 0xB0E8E2F4);
    }

    private void drawOption(GuiGraphics g, Font font, int i, int cx, int cy) {
        Option o = option(i);
        boolean sel = i == selected;
        int color = o.enabled() ? COLORS[i] : 0xFF6E6880;
        int[] p = pos(i, cx, cy, RADIUS);
        int labelW = Math.max(font.width(o.label()), o.hint() != null ? Math.round(font.width(o.hint()) * 0.75F) : 0);
        int w = Math.max(64, labelW + 30), h = o.hint() != null ? 30 : 24;
        int x = p[0] - w / 2, y = p[1] - h / 2;
        // A opção da esquerda/direita cresce para fora (não cobre o centro)
        if (i == 1) x = p[0] - 12;
        if (i == 3) x = p[0] - w + 12;
        int top = sel ? MahouUi.alpha(MahouUi.darken(color, 0.55F), 0xF0) : 0xE0201830;
        int bottom = sel ? MahouUi.alpha(MahouUi.darken(color, 0.8F), 0xF0) : 0xE00E0B16;
        if (sel) g.fill(x - 2, y - 2, x + w + 2, y + h + 2, MahouUi.alpha(color, 0x40));
        MahouUi.roundRect(g, x, y, w, h, top, bottom);
        MahouUi.roundOutline(g, x, y, w, h, sel ? MahouUi.lighten(color, 0.3F) : MahouUi.alpha(color, 0x90));
        g.renderItem(icons[i], x + 4, y + (h - 16) / 2);
        int tx = x + 24;
        g.drawString(font, o.label(), tx, y + (o.hint() != null ? 5 : 8), o.enabled() ? (sel ? 0xFFFFFFFF : MahouUi.TEXT) : 0xFF8A82A0, sel);
        if (o.hint() != null) MahouUi.smallText(g, font, o.hint(), tx, y + 17, 0.75F, 0xFFB8B0C8, false);
        // número da tecla
        MahouUi.smallText(g, font, Component.literal(String.valueOf(i + 1)), x + w - 7, y + 2, 0.6F, MahouUi.alpha(color, 0xC0), false);
    }

    private static int[] pos(int i, int cx, int cy, int r) {
        return switch (i) {
            case 0 -> new int[] {cx, cy - r};
            case 1 -> new int[] {cx + r, cy};
            case 2 -> new int[] {cx, cy + r};
            default -> new int[] {cx - r, cy};
        };
    }

    private static void ring(GuiGraphics g, int cx, int cy, int r, int color) {
        int n = 48;
        for (int k = 0; k < n; k++) {
            double a = k * Math.PI * 2 / n;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, color);
        }
    }
}
