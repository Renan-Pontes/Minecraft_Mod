package com.renanpontes.isekaimahou.feature.journal.client;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import com.mojang.blaze3d.platform.Lighting;
import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Modelo 3D de uma criatura dentro de uma caixa da tela (Bestiário). Cria UMA instância por tipo (sem adicionar ao mundo),
 * gira devagar (ou pelo arrasto do mouse) e anima pelo relógio do jogo. Se a criatura falhar ao criar ou desenhar,
 * o tipo é marcado e não tentamos de novo.
 */
final class EntityPreview {
    private static final Map<EntityType<?>, LivingEntity> CACHE = new HashMap<>();
    private static final Set<EntityType<?>> BROKEN = new HashSet<>();
    private static ClientLevel cacheLevel;

    static void clear() {
        CACHE.clear();
        BROKEN.clear();
        cacheLevel = null;
    }

    /** Instância de exibição (ou null se não der para criar). */
    static LivingEntity get(EntityType<?> type) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) return null;
        if (level != cacheLevel) {
            CACHE.clear();
            cacheLevel = level;
        }
        if (BROKEN.contains(type)) return null;
        LivingEntity cached = CACHE.get(type);
        if (cached != null) return cached;
        try {
            Entity e = type.create(level);
            if (e instanceof LivingEntity living) {
                CACHE.put(type, living);
                return living;
            }
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.warn("Bestiario: nao foi possivel criar {} para exibir", EntityType.getKey(type), ex);
        }
        BROKEN.add(type);
        return null;
    }

    /** Desenha a criatura centrada na caixa, virada {@code yaw} graus (0 = de frente). Retorna false se falhar. */
    static boolean render(GuiGraphics g, EntityType<?> type, int x0, int y0, int x1, int y1, float yaw) {
        LivingEntity e = get(type);
        if (e == null) return false;
        float bbH = Math.max(0.25F, e.getBbHeight()), bbW = Math.max(0.25F, e.getBbWidth());
        float scale = Mth.clamp(Math.min((y1 - y0) * 0.70F / bbH, (x1 - x0) * 0.60F / bbW), 3F, 90F);
        float cx = (x0 + x1) / 2F, cy = (y0 + y1) / 2F + 4;

        Minecraft mc = Minecraft.getInstance();
        float rot = 180F + yaw;
        e.tickCount = mc.level == null ? 0 : (int) (mc.level.getGameTime() % 1_000_000L);
        e.yBodyRot = e.yBodyRotO = rot;
        e.setYRot(rot);
        e.yRotO = rot;
        e.yHeadRot = e.yHeadRotO = rot;
        e.setXRot(0F);
        e.xRotO = 0F;

        float pitch = -8F * Mth.DEG_TO_RAD;
        Quaternionf pose = new Quaternionf().rotateZ(Mth.PI);
        Quaternionf camera = new Quaternionf().rotateX(pitch);
        pose.mul(camera);
        g.enableScissor(x0, y0, x1, y1);
        try {
            InventoryScreen.renderEntityInInventory(g, cx, cy, scale, new Vector3f(0F, bbH / 2F, 0F), pose, camera, e);
            return true;
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.warn("Bestiario: falha ao desenhar {}", EntityType.getKey(type), ex);
            BROKEN.add(type);
            CACHE.remove(type);
            // Devolve a pilha de transformações ao estado da tela (o renderer pode ter parado no meio).
            while (!g.pose().clear()) g.pose().popPose();
            mc.getEntityRenderDispatcher().setRenderShadow(true);
            Lighting.setupFor3DItems();
            return false;
        } finally {
            g.disableScissor();
        }
    }

    private EntityPreview() {}
}
