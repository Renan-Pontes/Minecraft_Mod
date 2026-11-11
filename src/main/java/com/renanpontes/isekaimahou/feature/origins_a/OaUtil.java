package com.renanpontes.isekaimahou.feature.origins_a;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Utilidades do módulo (dono de runas/familiares, chão, mensagens). */
public final class OaUtil {

    /** Jogador online com esse UUID (em qualquer dimensão), ou null. */
    public static ServerPlayer player(ServerLevel level, UUID id) {
        return id == null ? null : level.getServer().getPlayerList().getPlayer(id);
    }

    /** Dono como entidade viva: jogador online ou entidade carregada nesta dimensão. */
    public static LivingEntity owner(ServerLevel level, UUID id) {
        if (id == null) return null;
        ServerPlayer p = player(level, id);
        if (p != null) return p;
        return level.getEntity(id) instanceof LivingEntity le ? le : null;
    }

    /**
     * Aliado do dono? Ele mesmo, bichos dele, quem está do mesmo time e outros jogadores
     * quando o PvP está desligado. Funciona mesmo com o dono desconectado.
     */
    public static boolean isAlly(ServerLevel level, UUID ownerId, Entity e) {
        if (e == null || ownerId == null) return false;
        if (ownerId.equals(e.getUUID())) return true;
        if (e instanceof OwnableEntity own && ownerId.equals(own.getOwnerUUID())) return true;
        LivingEntity owner = owner(level, ownerId);
        if (owner != null && owner.isAlliedTo(e)) return true;
        return e instanceof Player && !level.getServer().isPvpAllowed();
    }

    /** Alvo válido de armadilha/barreira: vivo, não aliado, não espectador/criativo, não suporte de armadura. */
    public static boolean isHostileTo(ServerLevel level, UUID ownerId, LivingEntity e) {
        if (!e.isAlive() || e instanceof ArmorStand) return false;
        if (e instanceof Player p && (p.isSpectator() || p.isCreative())) return false;
        return !isAlly(level, ownerId, e);
    }

    /**
     * Ameaça (dispara armadilhas): monstros, mobs mirando o dono/aliados e jogadores não aliados.
     * Animais e aldeões passando por cima não disparam nem são feridos.
     */
    public static boolean isThreat(ServerLevel level, UUID ownerId, LivingEntity e) {
        if (!isHostileTo(level, ownerId, e)) return false;
        if (e instanceof Enemy || e instanceof Player) return true;
        return e instanceof Mob m && m.getTarget() != null && isAlly(level, ownerId, m.getTarget());
    }

    /** Bloco de "pé" (ar com chão firme embaixo) mais próximo do ponto, procurando um pouco para cima/baixo. */
    public static BlockPos ground(ServerLevel level, Vec3 at) {
        BlockPos pos = BlockPos.containing(at);
        for (int i = 0; i < 3 && solid(level, pos); i++) pos = pos.above();
        for (int i = 0; i < 6 && !solid(level, pos.below()); i++) pos = pos.below();
        return pos;
    }

    /** Onde colocar uma runa a partir de um acerto em bloco (topo do bloco, ou chão abaixo se acertou parede). */
    public static BlockPos runeSpot(ServerLevel level, BlockHitResult hit) {
        if (hit.getDirection() == Direction.UP) return hit.getBlockPos().above();
        return ground(level, hit.getLocation().add(Vec3.atLowerCornerOf(hit.getDirection().getNormal()).scale(0.5)));
    }

    private static boolean solid(ServerLevel level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return !s.getCollisionShape(level, pos).isEmpty();
    }

    /** Prefixo das mensagens do módulo (chaves em src/main/lang/origins_a). */
    public static final String MSG = "message.isekai_mahou.origins_a.";

    /** Mensagem curta na barra de ação. */
    public static void actionbar(Player p, String key, Object... args) {
        if (p != null) p.displayClientMessage(Component.translatable(MSG + key, args), true);
    }

    private OaUtil() {}
}
