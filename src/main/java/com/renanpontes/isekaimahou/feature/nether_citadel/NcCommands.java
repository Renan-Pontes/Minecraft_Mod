package com.renanpontes.isekaimahou.feature.nether_citadel;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;

/**
 * {@code /mahou nether_citadel give|tp|boss|pact|debt} e {@code /mahou spawnboss demon_lord} (só op).
 * {@code tp} leva ao centro da Cidadela mais próxima no Nether (de qualquer dimensão).
 */
public final class NcCommands {
    public static final TagKey<Structure> CITADEL_TAG = TagKey.create(Registries.STRUCTURE, IsekaiMahou.id("nether_citadel"));

    public static void register() {
        MahouCommands.extend((root, ctx) -> {
            root.then(MahouCommands.op("nether_citadel")
                    .then(Commands.literal("give").executes(c -> give(c.getSource().getPlayerOrException())))
                    .then(Commands.literal("tp").executes(c -> tp(c.getSource(), c.getSource().getPlayerOrException())))
                    .then(Commands.literal("boss").executes(c -> spawnBoss(c.getSource())))
                    .then(Commands.literal("pact").executes(c -> pact(c.getSource().getPlayerOrException())))
                    .then(Commands.literal("debt").executes(c -> clearDebt(c.getSource().getPlayerOrException()))));
            root.then(MahouCommands.op("spawnboss").then(Commands.literal("demon_lord").executes(c -> spawnBoss(c.getSource()))));
        });
    }

    private static int give(ServerPlayer sp) {
        for (var item : NcRegistries.worldItems()) sp.getInventory().placeItemBackInInventory(new ItemStack(item.get(), item == NcRegistries.ADAMANTITE_INGOT ? 32 : 1));
        for (var item : NcRegistries.gearItems()) sp.getInventory().placeItemBackInInventory(new ItemStack(item.get()));
        for (var egg : NcRegistries.eggs()) sp.getInventory().placeItemBackInInventory(new ItemStack(egg.get()));
        sp.sendSystemMessage(Component.translatable("command.isekai_mahou.nether_citadel.given").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private static int tp(CommandSourceStack source, ServerPlayer sp) {
        ServerLevel nether = source.getServer().getLevel(Level.NETHER);
        if (nether == null) return 0;
        BlockPos origin = sp.level() == nether ? sp.blockPosition()
                : BlockPos.containing(sp.getX() / 8.0, 64, sp.getZ() / 8.0);
        BlockPos found = nether.findNearestMapStructure(CITADEL_TAG, origin, 100, false);
        if (found == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.nether_citadel.not_found"));
            return 0;
        }
        Vec3 dest = Vec3.atBottomCenterOf(found.atY(64));
        Structure s = nether.registryAccess().registryOrThrow(Registries.STRUCTURE).get(NcEvents.CITADEL);
        if (s != null) {
            StructureStart start = nether.structureManager().getStartForStructure(SectionPos.of(found), s,
                    nether.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS));
            if (start != null && start.isValid()) {
                // v1.0: a estrutura agora tem a estrada como peças extras — o centro que interessa é o da fortaleza (1ª peça).
                BoundingBox bb = start.getPieces().isEmpty() ? start.getBoundingBox() : start.getPieces().get(0).getBoundingBox();
                // Piso do pátio = 3 blocos acima da base do molde.
                dest = new Vec3(bb.getCenter().getX() + 0.5, bb.minY() + 4, bb.getCenter().getZ() + 0.5);
            }
        }
        sp.teleportTo(nether, dest.x, dest.y, dest.z, sp.getYRot(), sp.getXRot());
        Vec3 at = dest;
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.nether_citadel.tp",
                (int) at.x, (int) at.y, (int) at.z), true);
        return 1;
    }

    private static int spawnBoss(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        Vec3 pos = source.getPosition();
        Entity viewer = source.getEntity();
        if (viewer != null) {
            Vec3 look = viewer.getLookAngle().multiply(1, 0, 1);
            if (look.lengthSqr() > 1.0E-4) pos = viewer.position().add(look.normalize().scale(6.0));
        }
        EntityType<?> type = NcRegistries.DEMON_LORD.get();
        Entity boss = type.spawn(level, BlockPos.containing(pos), MobSpawnType.COMMAND);
        if (boss == null) {
            source.sendFailure(Component.translatable("command.isekai_mahou.nether_citadel.spawn_fail"));
            return 0;
        }
        Component name = boss.getDisplayName().copy().withStyle(ChatFormatting.GOLD);
        source.sendSuccess(() -> Component.translatable("command.isekai_mahou.nether_citadel.spawn_done", name), true);
        return 1;
    }

    private static int pact(ServerPlayer sp) {
        // Tirar um pacto ativo cobra a dívida: tira o pacto ANTES de limpar a dívida.
        sp.removeEffect(NcRegistries.DEMON_PACT);
        sp.removeEffect(NcRegistries.DEMON_DEBT);
        sp.addEffect(new MobEffectInstance(NcRegistries.DEMON_PACT, NcPact.pactTicks(sp), 0, false, true, true));
        sp.sendSystemMessage(Component.translatable("block.isekai_mahou.demon_pact_altar.sealed").withColor(NcPact.COLOR));
        return 1;
    }

    private static int clearDebt(ServerPlayer sp) {
        sp.removeEffect(NcRegistries.DEMON_DEBT);
        sp.sendSystemMessage(Component.translatable("command.isekai_mahou.nether_citadel.debt_cleared").withStyle(ChatFormatting.GREEN));
        return 1;
    }

    private NcCommands() {}
}
