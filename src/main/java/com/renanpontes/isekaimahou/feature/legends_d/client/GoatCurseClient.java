package com.renanpontes.isekaimahou.feature.legends_d.client;

import java.util.HashMap;
import java.util.Map;

import com.mojang.blaze3d.vertex.PoseStack;
import com.renanpontes.isekaimahou.feature.legends_d.LdNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

/**
 * Maldição da Cabra (cliente): o jogador amaldiçoado é desenhado como uma CABRA (manequim de cabra do vanilla que
 * copia posição, giro, passos e o nome dele); em 1ª pessoa a mão some.
 */
public final class GoatCurseClient {
    private static final Map<Integer, Integer> CURSED = new HashMap<>();
    private static final Map<Integer, Goat> DUMMIES = new HashMap<>();
    private static int ticks;
    private static ClientLevel level;

    public static void onSync(LdNet.GoatCurse p) {
        check();
        if (p.ticks() <= 0) {
            CURSED.remove(p.entityId());
            DUMMIES.remove(p.entityId());
        } else {
            CURSED.put(p.entityId(), ticks + p.ticks());
        }
    }

    private static void check() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            CURSED.clear();
            DUMMIES.clear();
            level = mc.level;
        }
    }

    public static void tick(ClientTickEvent.Post e) {
        check();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) return;
        ticks++;
        if (CURSED.isEmpty()) return;
        CURSED.entrySet().removeIf(en -> {
            boolean gone = en.getValue() < ticks;
            if (gone) DUMMIES.remove(en.getKey());
            return gone;
        });
        for (int id : CURSED.keySet()) {
            Entity en = mc.level.getEntity(id);
            if (!(en instanceof Player p)) continue;
            Goat goat = dummy(p);
            if (goat == null) continue;
            float dist = (float) Mth.length(p.getX() - p.xo, 0, p.getZ() - p.zo);
            goat.walkAnimation.update(Math.min(dist * 4F, 1F), 0.4F);
            goat.tickCount = p.tickCount;
        }
    }

    public static void onRenderPlayer(RenderPlayerEvent.Pre e) {
        if (CURSED.isEmpty()) return;
        Player p = e.getEntity();
        if (!CURSED.containsKey(p.getId()) || p.isSpectator()) return;
        Goat goat = dummy(p);
        if (goat == null) return;
        e.setCanceled(true);
        // O manequim fica onde o jogador está (a distância do letreiro de nome e a luz usam a posição dele)
        goat.setPos(p.getX(), p.getY(), p.getZ());
        goat.xo = p.xo;
        goat.yo = p.yo;
        goat.zo = p.zo;
        goat.yBodyRot = p.yBodyRot;
        goat.yBodyRotO = p.yBodyRotO;
        goat.yHeadRot = p.yHeadRot;
        goat.yHeadRotO = p.yHeadRotO;
        goat.setXRot(p.getXRot());
        goat.xRotO = p.xRotO;
        goat.setOnGround(p.onGround());
        goat.hurtTime = p.hurtTime;
        goat.setInvisible(p.isInvisible());
        Minecraft mc = Minecraft.getInstance();
        boolean showName = p != mc.getCameraEntity() && Minecraft.renderNames() && !p.isInvisible();
        goat.setCustomName(showName ? p.getDisplayName() : null);
        goat.setCustomNameVisible(showName);
        float pt = e.getPartialTick();
        PoseStack ps = e.getPoseStack();
        EntityRenderer<? super Goat> r = mc.getEntityRenderDispatcher().getRenderer(goat);
        r.render(goat, Mth.lerp(pt, p.yRotO, p.getYRot()), pt, ps, e.getMultiBufferSource(), e.getPackedLight());
    }

    public static void onRenderHand(RenderHandEvent e) {
        Player p = Minecraft.getInstance().player;
        if (p != null && CURSED.containsKey(p.getId())) e.setCanceled(true);
    }

    private static Goat dummy(Player p) {
        if (level == null) return null;
        return DUMMIES.computeIfAbsent(p.getId(), k -> {
            Goat g = EntityType.GOAT.create(level);
            if (g != null) {
                g.setScreamingGoat(true);
                g.setPos(p.getX(), p.getY(), p.getZ());
            }
            return g;
        });
    }

    private GoatCurseClient() {}
}
