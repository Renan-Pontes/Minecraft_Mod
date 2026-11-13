package com.renanpontes.isekaimahou.feature.space_magic;

import com.renanpontes.isekaimahou.core.CoreRegistries;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.joml.Vector3f;

/**
 * Eventos de jogo do módulo: atualização dos portais/gravidade no fim do tick, o "último chão firme" de cada jogador
 * por dimensão, o brilho da Marca de Retorno (só o dono vê) e o pergaminho raro de Passo Espacial dos Endermen.
 */
final class SpaceEvents {
    /** Chance do Enderman (morto por jogador) deixar o pergaminho de Passo Espacial. */
    private static final float ENDERMAN_SCROLL_CHANCE = 0.015F;

    static void register() {
        NeoForge.EVENT_BUS.addListener(SpaceEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(SpaceEvents::onDrops);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            SpaceGates.clear();
            SpaceGravity.clear();
        });
    }

    private static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        SpaceGates.flush(server);
        SpaceGravity.tick(server);
        int tick = server.getTickCount();
        if (tick % 10 != 0) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (tick % 100 == 0 && sp.onGround() && !sp.isSpectator() && !sp.isPassenger() && !sp.isInLiquid()) {
                SpaceData.setLastPos(sp, sp.level().dimension(), sp.blockPosition());
            }
            showMark(sp);
        }
    }

    /** Coluna de brilho violeta na Marca de Retorno, só para o dono e só de perto (≤ 48 blocos). */
    private static void showMark(ServerPlayer sp) {
        SpaceData.Mark m = SpaceData.mark(sp);
        if (m == null || m.dim() != sp.level().dimension()) return;
        Vec3 p = m.pos();
        if (p.distanceToSqr(sp.position()) > 48 * 48) return;
        var level = sp.serverLevel();
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.73F, 0.55F, 1.0F), 1.0F);
        level.sendParticles(sp, dust, true, p.x, p.y + 0.15, p.z, 4, 0.25, 0.02, 0.25, 0);
        level.sendParticles(sp, ParticleTypes.END_ROD, true, p.x, p.y + 0.6, p.z, 1, 0.05, 0.4, 0.05, 0.005);
    }

    private static void onDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof EnderMan e) || !(event.getSource().getEntity() instanceof ServerPlayer)) return;
        if (e.getRandom().nextFloat() >= ENDERMAN_SCROLL_CHANCE) return;
        ItemEntity drop = new ItemEntity(e.level(), e.getX(), e.getY() + 0.5, e.getZ(), scroll(SpaceGlyphs.SPACE_STEP));
        drop.setDefaultPickUpDelay();
        event.getDrops().add(drop);
    }

    /** Pergaminho de glifo com o id dado. */
    static ItemStack scroll(ResourceLocation glyph) {
        ItemStack s = new ItemStack(CoreRegistries.GLYPH_SCROLL.get());
        s.set(CoreRegistries.GLYPH.get(), glyph);
        return s;
    }

    private SpaceEvents() {}
}
