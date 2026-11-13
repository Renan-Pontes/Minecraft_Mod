package com.renanpontes.isekaimahou.feature.forms_b.client;

import org.lwjgl.glfw.GLFW;

import com.renanpontes.isekaimahou.client.CoreKeys;
import com.renanpontes.isekaimahou.client.fx.FxQuality;
import com.renanpontes.isekaimahou.feature.forms_b.summon.SummonForge;
import com.renanpontes.isekaimahou.feature.forms_b.summon.SummonNet;
import com.renanpontes.isekaimahou.feature.forms_b.util.FormsBUtil;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Cliente da Forja de Invocações: a tecla de ordem (padrão O) e a cor do elemento dos corpos.
 * <p>
 * A cor chega por pacote e é gravada no {@code persistentData} da criatura no cliente (chave
 * {@link SummonForge#COLOR_KEY}); os renderers dos corpos, nos módulos elementais, leem dali por texto. Aqui também sai
 * uma poeira discreta na cor do elemento (frequência pelo {@link FxQuality}).
 */
public final class SummonForgeClient {
    /** Tecla de ordem das invocações: passa para a próxima ordem (seguir, guardar, caçar, ficar). */
    public static final KeyMapping ORDER = new KeyMapping("key.isekai_mahou.summon_order", GLFW.GLFW_KEY_O, CoreKeys.CATEGORY);

    /** id de rede da criatura para {cor RGB, ticks sem achar a entidade}. */
    private static final Int2ObjectMap<int[]> LOOKS = new Int2ObjectOpenHashMap<>();
    /** Sem a entidade por este tempo, esquece a cor (ela saiu do alcance; ao voltar, o servidor manda de novo). */
    private static final int FORGET_TICKS = 200;

    private SummonForgeClient() {}

    static void registerKeys(RegisterKeyMappingsEvent e) {
        e.register(ORDER);
    }

    static void onLook(SummonNet.Look look) {
        Minecraft mc = Minecraft.getInstance();
        if (look.color() < 0) {
            LOOKS.remove(look.entityId());
            Entity e = mc.level != null ? mc.level.getEntity(look.entityId()) : null;
            if (e != null) e.getPersistentData().remove(SummonForge.COLOR_KEY);
            return;
        }
        LOOKS.put(look.entityId(), new int[] {look.color() & 0xFFFFFF, 0});
    }

    static void logout(ClientPlayerNetworkEvent.LoggingOut e) {
        LOOKS.clear();
    }

    static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        while (ORDER.consumeClick()) PacketDistributor.sendToServer(new SummonNet.OrderKey());
        if (LOOKS.isEmpty() || mc.isPaused()) return;
        RandomSource rnd = mc.level.random;
        float chance = Math.min(1.0F, FxQuality.particles());
        for (ObjectIterator<Int2ObjectMap.Entry<int[]>> it = LOOKS.int2ObjectEntrySet().iterator(); it.hasNext();) {
            Int2ObjectMap.Entry<int[]> en = it.next();
            int[] v = en.getValue();
            Entity e = mc.level.getEntity(en.getIntKey());
            if (e == null || !e.isAlive()) {
                // o pacote pode chegar antes da criatura (ou ela saiu do alcance): espera um pouco antes de esquecer
                if (++v[1] > FORGET_TICKS) it.remove();
                continue;
            }
            v[1] = 0;
            int argb = 0xFF000000 | tint(v[0]);
            if (e.getPersistentData().getInt(SummonForge.COLOR_KEY) != argb) e.getPersistentData().putInt(SummonForge.COLOR_KEY, argb);
            // poeira do elemento subindo do corpo: discreta, e mais rara nos presets de gráficos baixos
            if ((e.tickCount & 3) != 0 || !FxQuality.inRange(e.distanceToSqr(mc.player)) || rnd.nextFloat() > chance) continue;
            mc.level.addParticle(FormsBUtil.dust(v[0], 0.8F), e.getX() + (rnd.nextDouble() - 0.5) * e.getBbWidth(),
                    e.getY() + rnd.nextDouble() * e.getBbHeight(), e.getZ() + (rnd.nextDouble() - 0.5) * e.getBbWidth(), 0, 0.02, 0);
        }
    }

    /** A textura do corpo é multiplicada por esta cor: metade branco, metade elemento (não apaga os detalhes). */
    private static int tint(int rgb) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        return ((r + 255) / 2 << 16) | ((g + 255) / 2 << 8) | ((b + 255) / 2);
    }
}
