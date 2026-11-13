package com.renanpontes.isekaimahou.feature.magic_biomes;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.datafixers.util.Pair;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MahouCommands;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Comandos de teste (cheats):
 * <pre>
 * /mahou magic_biomes tp &lt;bioma&gt;   acha o bioma mais perto (na dimensão certa) e leva você até lá
 * /mahou magic_biomes where         diz o bioma onde você está
 * /mahou magic_biomes give          os 5 blocos do módulo
 * /mahou magic_biomes reset         esquece as visitas (o capítulo do Diário volta a contar)
 * </pre>
 * Biomas: crystal_forest, haunted_swamp, twilight_grove, crystal_cavern, ember_crystal_wastes, astral_gardens.
 * Também vale o do vanilla: {@code /locate biome isekai_mahou:crystal_forest}. Só nascem em terreno NOVO.
 */
public final class MagicBiomesCommands {
    private static final int SEARCH_RADIUS = 6400;

    public static void init() {
        MahouCommands.extend((root, ctx) -> root.then(MahouCommands.op("magic_biomes")
                .then(Commands.literal("tp").then(Commands.argument("biome", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(
                                MagicBiomes.ALL.stream().map(k -> k.location().getPath()), b))
                        .executes(c -> tp(c.getSource(), StringArgumentType.getString(c, "biome")))))
                .then(Commands.literal("where").executes(c -> where(c.getSource())))
                .then(Commands.literal("give").executes(c -> give(c.getSource())))
                .then(Commands.literal("reset").executes(c -> reset(c.getSource())))));
    }

    private static Component name(ResourceKey<Biome> key) {
        return Component.translatable("biome." + key.location().getNamespace() + "." + key.location().getPath());
    }

    private static ResourceKey<Level> dimensionOf(ResourceKey<Biome> key) {
        if (key == MagicBiomes.EMBER_WASTES) return Level.NETHER;
        if (key == MagicBiomes.ASTRAL_GARDENS) return Level.END;
        return Level.OVERWORLD;
    }

    private static int tp(CommandSourceStack src, String path) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        ResourceKey<Biome> key = MagicBiomes.ALL.stream().filter(k -> k.location().getPath().equals(path)).findFirst().orElse(null);
        if (key == null) {
            src.sendFailure(Component.translatable("command.isekai_mahou.magic_biomes.not_found", path, SEARCH_RADIUS));
            return 0;
        }
        ResourceKey<Level> dim = dimensionOf(key);
        ServerLevel level = src.getServer().getLevel(dim);
        if (level == null) {
            src.sendFailure(Component.translatable("command.isekai_mahou.magic_biomes.no_level", dim.location().toString()));
            return 0;
        }
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.magic_biomes.searching", name(key)), false);
        // De onde procurar: a posição do jogador se ele já está na dimensão; senão um ponto de partida razoável dela.
        BlockPos from = sp.level() == level ? sp.blockPosition()
                : dim == Level.END ? new BlockPos(1500, 64, 0) : new BlockPos(sp.getBlockX() / 8, 64, sp.getBlockZ() / 8);
        Pair<BlockPos, Holder<Biome>> found = level.findClosestBiome3d(h -> h.is(key), from, SEARCH_RADIUS, 32, 64);
        if (found == null) {
            src.sendFailure(Component.translatable("command.isekai_mahou.magic_biomes.not_found", name(key), SEARCH_RADIUS));
            return 0;
        }
        BlockPos at = standingSpot(level, found.getFirst(), key);
        sp.teleportTo(level, at.getX() + 0.5, at.getY(), at.getZ() + 0.5, sp.getYRot(), sp.getXRot());
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.magic_biomes.found", name(key),
                at.getX(), at.getY(), at.getZ(), dim.location().toString()), true);
        return 1;
    }

    /**
     * Um lugar para ficar de pé perto do ponto achado. Superfície: o topo do terreno. Caverna/Nether: o bolsão de ar
     * mais próximo na coluna (e nas vizinhas). End: a ilha mais próxima em volta. Se não achar, devolve o próprio ponto.
     */
    private static BlockPos standingSpot(ServerLevel level, BlockPos found, ResourceKey<Biome> key) {
        boolean surface = key == MagicBiomes.CRYSTAL_FOREST || key == MagicBiomes.HAUNTED_SWAMP
                || key == MagicBiomes.TWILIGHT_GROVE || key == MagicBiomes.ASTRAL_GARDENS;
        List<BlockPos> columns = new ArrayList<>();
        columns.add(found);
        for (int r = 8; r <= 48; r += 8) {
            columns.add(found.offset(r, 0, 0));
            columns.add(found.offset(-r, 0, 0));
            columns.add(found.offset(0, 0, r));
            columns.add(found.offset(0, 0, -r));
        }
        for (BlockPos col : columns) {
            level.getChunk(col); // gera o chunk se for terreno novo
            if (surface) {
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, col.getX(), col.getZ());
                if (y > level.getMinBuildHeight() + 1) return new BlockPos(col.getX(), y, col.getZ());
                continue;
            }
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int dy = 0; dy <= 40; dy++) {
                for (int sign = -1; sign <= 1; sign += 2) {
                    int y = col.getY() + dy * sign;
                    if (y <= level.getMinBuildHeight() + 2 || y >= level.getMaxBuildHeight() - 3) continue;
                    p.set(col.getX(), y, col.getZ());
                    if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()
                            && level.getBlockState(p.below()).isSolid() && level.getFluidState(p).isEmpty()) {
                        return p.immutable();
                    }
                }
            }
        }
        return found;
    }

    private static int where(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        Holder<Biome> biome = sp.serverLevel().getBiome(sp.blockPosition());
        String id = biome.unwrapKey().map(k -> k.location().toString()).orElse("?");
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.magic_biomes.where", id,
                String.valueOf(biome.is(MagicBiomes.TAG))), false);
        return 1;
    }

    private static int give(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        MagicBiomesContent.BLOCK_ITEMS.forEach(i -> {
            ItemStack stack = new ItemStack(i.get(), 16);
            if (!sp.getInventory().add(stack)) sp.drop(stack, false);
        });
        return 1;
    }

    private static int reset(CommandSourceStack src) throws CommandSyntaxException {
        ServerPlayer sp = src.getPlayerOrException();
        MagicData d = MagicApi.get(sp);
        MagicBiomes.ALL.forEach(k -> d.setFlag(MagicBiomesEvents.VISIT_PREFIX + k.location().getPath(), false));
        src.sendSuccess(() -> Component.translatable("command.isekai_mahou.magic_biomes.visits_reset"), false);
        return 1;
    }

    private MagicBiomesCommands() {}
}
