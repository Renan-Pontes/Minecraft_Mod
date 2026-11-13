package com.renanpontes.isekaimahou.feature.arcane;

import com.renanpontes.isekaimahou.IsekaiMahou;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Marca do glifo "Marca e Retorno", guardada nos dados persistentes da entidade
 * (jogadores: copiada ao renascer). Também mostra a marca (só para o dono) com partículas.
 */
public final class RecallMarks {
    /** Raiz dos dados do módulo em {@link Entity#getPersistentData()}. */
    public static final String ROOT = IsekaiMahou.MODID + "_arcane";
    private static final String KEY = "recall";

    public record Mark(ResourceKey<Level> dimension, Vec3 pos) {}

    public static Mark get(Entity e) {
        CompoundTag root = e.getPersistentData().getCompound(ROOT);
        if (!root.contains(KEY)) return null;
        CompoundTag t = root.getCompound(KEY);
        ResourceLocation dim = ResourceLocation.tryParse(t.getString("dim"));
        if (dim == null) return null;
        return new Mark(ResourceKey.create(Registries.DIMENSION, dim), new Vec3(t.getDouble("x"), t.getDouble("y"), t.getDouble("z")));
    }

    public static void set(Entity e, Mark m) {
        CompoundTag root = e.getPersistentData().getCompound(ROOT);
        CompoundTag t = new CompoundTag();
        t.putString("dim", m.dimension().location().toString());
        t.putDouble("x", m.pos().x);
        t.putDouble("y", m.pos().y);
        t.putDouble("z", m.pos().z);
        root.put(KEY, t);
        e.getPersistentData().put(ROOT, root);
    }

    public static void clear(Entity e) {
        CompoundTag root = e.getPersistentData().getCompound(ROOT);
        root.remove(KEY);
        e.getPersistentData().put(ROOT, root);
    }

    /** Ao renascer/voltar do End, o jogador novo herda a marca. */
    public static void onClone(PlayerEvent.Clone event) {
        CompoundTag old = event.getOriginal().getPersistentData().getCompound(ROOT);
        if (!old.isEmpty()) event.getEntity().getPersistentData().put(ROOT, old.copy());
    }

    /** A cada meio segundo, cada jogador vê a própria marca (coluna de luz) se estiver perto. */
    public static void tickParticles(MinecraftServer server) {
        if (server.getTickCount() % 10 != 0) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            Mark m = get(p);
            if (m == null || !m.dimension().equals(p.level().dimension())) continue;
            if (m.pos().distanceToSqr(p.position()) > 96 * 96) continue;
            var level = p.serverLevel();
            level.sendParticles(p, Vfx.endRod(), true, m.pos().x, m.pos().y + 0.6, m.pos().z, 3, 0.05, 0.6, 0.05, 0.01);
            double a = server.getTickCount() * 0.15;
            for (int i = 0; i < 4; i++) {
                double ang = a + i * Math.PI / 2;
                level.sendParticles(p, ArcaneUtil.dust(0xB98CFF, 1.0F), true, m.pos().x + Math.cos(ang) * 0.6, m.pos().y + 0.1,
                        m.pos().z + Math.sin(ang) * 0.6, 1, 0, 0, 0, 0);
            }
        }
    }

    private RecallMarks() {}
}
