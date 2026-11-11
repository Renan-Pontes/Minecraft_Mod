package com.renanpontes.isekaimahou.feature.structures_c;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Paredes encantadas do Labirinto do Minotauro: enquanto o Minotauro estiver vivo lá dentro, ninguém (fora o
 * modo criativo) quebra blocos, constrói pilares/pontes/escadas ou explode paredes — o labirinto tem que ser
 * resolvido andando. Blocos sem colisão (tochas, por exemplo) continuam permitidos para marcar o caminho.
 * Pérolas do Ender e frutas do coro não teleportam para dentro dele, e quem chega ao topo das paredes de outro
 * jeito (élitros, saltos mágicos...) é puxado de volta para a entrada mais próxima.
 * Se a criatura "minotaur" não existir no jogo, a proteção simplesmente não liga.
 */
public final class LabyrinthWard {
    /**
     * Altura (a partir do piso do molde) a partir da qual o jogador está "em cima das paredes". O piso dos
     * corredores fica em +1, os braseiros mais altos da arena em +6 e o topo das paredes (laje) em +8.5.
     */
    private static final double WALL_TOP = 7.5;
    /** Quanto acima do topo das paredes ainda conta (voar bem alto por cima é permitido). */
    private static final int CEILING = 12;
    private static final String WARD_MESSAGE = "message.isekai_mahou.structures_c.labyrinth_ward";
    private static final Map<UUID, Long> LAST_WARNING = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(LabyrinthWard::onBreak);
        NeoForge.EVENT_BUS.addListener(LabyrinthWard::onPlace);
        NeoForge.EVENT_BUS.addListener(LabyrinthWard::onExplosion);
        NeoForge.EVENT_BUS.addListener(LabyrinthWard::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((EntityTeleportEvent.EnderPearl e) -> blockTeleport(e, e.getPlayer()));
        NeoForge.EVENT_BUS.addListener((EntityTeleportEvent.ChorusFruit e) -> {
            if (e.getEntityLiving() instanceof Player p) blockTeleport(e, p);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> LAST_WARNING.remove(e.getEntity().getUUID()));
    }

    private static void onBreak(BlockEvent.BreakEvent event) {
        Player player = event.getPlayer();
        if (player.isCreative() || !(event.getLevel() instanceof ServerLevel level)) return;
        if (wardedBlock(level, event.getPos())) {
            event.setCanceled(true);
            feedback(level, player, event.getPos(), WARD_MESSAGE);
        }
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player p && p.isCreative()) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        BlockState placed = event.getPlacedBlock();
        // Tochas e afins (sem colisão) não servem para escalar nem fazer ponte: liberadas. Andaimes, trepadeiras
        // e neve fofa também não têm colisão, mas dão para subir — esses não.
        boolean climbable = placed.is(BlockTags.CLIMBABLE) || placed.is(Blocks.POWDER_SNOW);
        if (!climbable && placed.getCollisionShape(level, event.getPos()).isEmpty()) return;
        if (wardedBlock(level, event.getPos())) {
            event.setCanceled(true);
            if (entity instanceof Player p) feedback(level, p, event.getPos(), WARD_MESSAGE);
        }
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // Explosão no labirinto ou na margem dele (creeper encostado na muralha de fora): o molde fica intacto.
        StructureStart start = warded(level, BlockPos.containing(event.getExplosion().center()));
        if (start == null) return;
        event.getAffectedBlocks().removeIf(pos -> insidePiece(start, pos));
    }

    /** Pérola do Ender / fruta do coro não pulam paredes: o teleporte para dentro do labirinto é anulado. */
    private static void blockTeleport(EntityTeleportEvent event, Player player) {
        if (player.isCreative() || !(player.level() instanceof ServerLevel level)) return;
        BlockPos target = BlockPos.containing(event.getTargetX(), event.getTargetY(), event.getTargetZ());
        if (!wardedBlock(level, target)) return;
        event.setCanceled(true);
        feedback(level, player, target, "message.isekai_mahou.structures_c.labyrinth_teleport");
    }

    /** Ninguém atravessa o labirinto por cima das paredes: volta para a entrada mais próxima. */
    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()) return;
        if ((player.tickCount + player.getId()) % 10 != 0) return;
        ServerLevel level = player.serverLevel();
        StructureStart start = RpgStructure.MINOTAUR_LABYRINTH.startAt(level, player.blockPosition());
        if (start == null || start.getPieces().isEmpty()) return;
        BoundingBox maze = start.getPieces().get(0).getBoundingBox();
        double x = player.getX();
        double z = player.getZ();
        if (x < maze.minX() || x >= maze.maxX() + 1 || z < maze.minZ() || z >= maze.maxZ() + 1) return;
        double above = player.getY() - maze.minY();
        if (above < WALL_TOP || above > maze.getYSpan() + CEILING) return;
        if (warded(level, player.blockPosition()) == null) return;
        BlockPos gate = nearestGate(level, maze, x, z);
        if (gate == null) return;
        level.sendParticles(ParticleTypes.REVERSE_PORTAL, x, player.getY() + 1.0, z, 40, 0.4, 0.8, 0.4, 0.05);
        level.playSound(null, x, player.getY(), z, SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 0.8F, 0.6F);
        player.stopFallFlying();
        player.stopRiding();   // montaria voadora fica para trás (teleportar montado dessincroniza)
        player.teleportTo(gate.getX() + 0.5, gate.getY(), gate.getZ() + 0.5);
        player.setDeltaMovement(0.0, 0.0, 0.0);
        player.resetFallDistance();
        level.sendParticles(ParticleTypes.ENCHANT, gate.getX() + 0.5, gate.getY() + 1.0, gate.getZ() + 0.5, 30, 0.4, 0.8, 0.4, 0.6);
        level.playSound(null, gate, SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 0.6F, 0.8F);
        player.displayClientMessage(Component.translatable("message.isekai_mahou.structures_c.labyrinth_repel")
                .withStyle(ChatFormatting.DARK_RED), true);
    }

    /**
     * A abertura da borda do labirinto (entrada ou saída) mais perto de (x, z): coluna livre no nível dos
     * corredores numa das quatro bordas do molde.
     */
    @Nullable
    private static BlockPos nearestGate(ServerLevel level, BoundingBox maze, double x, double z) {
        int y = maze.minY() + 1;
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int bx = maze.minX(); bx <= maze.maxX(); bx++) {
            for (int bz = maze.minZ(); bz <= maze.maxZ(); bz++) {
                if (bx != maze.minX() && bx != maze.maxX() && bz != maze.minZ() && bz != maze.maxZ()) continue;
                if (!open(level, p.set(bx, y, bz)) || !open(level, p.set(bx, y + 1, bz))) continue;
                double d = (bx + 0.5 - x) * (bx + 0.5 - x) + (bz + 0.5 - z) * (bz + 0.5 - z);
                if (d < bestDist) {
                    bestDist = d;
                    best = new BlockPos(bx, y, bz);
                }
            }
        }
        return best;
    }

    private static boolean open(ServerLevel level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }

    /**
     * Bloco protegido: dentro do próprio labirinto (paredes, piso, arena) com o Minotauro vivo. A margem de
     * adaptação do terreno em volta fica livre — quebrar grama ou construir do lado de fora não é trapaça (e
     * quem escala pelo lado de fora cai no {@link #onPlayerTick}).
     */
    private static boolean wardedBlock(ServerLevel level, BlockPos pos) {
        StructureStart start = warded(level, pos);
        return start != null && insidePiece(start, pos);
    }

    private static boolean insidePiece(StructureStart start, BlockPos pos) {
        for (var piece : start.getPieces()) {
            if (piece.getBoundingBox().isInside(pos)) return true;
        }
        return false;
    }

    /** O labirinto (caixa total, com a margem do terreno) que contém a posição, com o Minotauro vivo, ou null. */
    @Nullable
    static StructureStart warded(ServerLevel level, BlockPos pos) {
        StructureStart start = RpgStructure.MINOTAUR_LABYRINTH.startAt(level, pos);
        if (start == null) return null;
        Optional<EntityType<?>> minotaur = BuiltInRegistries.ENTITY_TYPE.getOptional(IsekaiMahou.id("minotaur"));
        if (minotaur.isEmpty()) return null;
        EntityType<?> type = minotaur.get();
        AABB area = AABB.of(start.getBoundingBox()).inflate(8.0);
        return level.getEntitiesOfClass(Entity.class, area, e -> e.getType() == type && e.isAlive()).isEmpty() ? null : start;
    }

    private static void feedback(ServerLevel level, Player player, BlockPos pos, String messageKey) {
        level.sendParticles(ParticleTypes.ENCHANT, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 18, 0.35, 0.35, 0.35, 0.6);
        long now = level.getGameTime();
        Long last = LAST_WARNING.get(player.getUUID());
        if (last != null && now - last < 30) return;
        LAST_WARNING.put(player.getUUID(), now);
        level.playSound(null, pos, SoundEvents.ENCHANTMENT_TABLE_USE, SoundSource.BLOCKS, 0.9F, 0.5F);
        player.displayClientMessage(Component.translatable(messageKey).withStyle(ChatFormatting.DARK_RED), true);
    }

    private LabyrinthWard() {}
}
