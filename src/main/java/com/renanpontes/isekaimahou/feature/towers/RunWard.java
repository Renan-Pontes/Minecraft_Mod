package com.renanpontes.isekaimahou.feature.towers;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.BossArena;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.feature.depths.DepthsDungeon;
import com.renanpontes.isekaimahou.feature.depths.puzzle.PuzzleCoreBlockEntity;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.FireChargeItem;
import net.minecraft.world.item.FlintAndSteelItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.SolidBucketItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.RedstoneTorchBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityMobGriefingEvent;
import net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * <b>Torres e masmorras indestrutíveis</b> (v1.0 r9 — "a torre quando alguém tiver subindo tem que ser indestrutível,
 * ou descendo se for uma dungeon").
 * <ul>
 *   <li><b>Torres de Desafio</b> (tag {@code isekai_mahou:towers}): a caixa inteira da estrutura, enquanto o pináculo
 *       NÃO foi conquistado (ou seja: durante toda a escalada; depois da conquista volta ao normal, como antes);</li>
 *   <li><b>Masmorra em Camadas</b> ({@code depths_dungeon}): todas as peças (ruína, 4 andares, ninho), SEMPRE — não tem
 *       "conquista" e a descida pode ser retomada a qualquer momento (sempre ligado é mais simples e não dá brecha de
 *       cavar por fora enquanto ninguém está dentro).</li>
 * </ul>
 * Dentro dessas áreas (quem está no criativo pode tudo, para depurar):
 * <ul>
 *   <li>quebrar blocos: não (exceto enfeites sem colisão — tochas, teias, liquens, trilhos —, vasos, velas, cristais de
 *       aglomerado e geradores de monstros; peças de enigma, placas, alavancas, botões e placas de pressão nunca);</li>
 *   <li>colocar blocos: não (sem pilares/pontes/escadas de mão); só tochas para iluminar;</li>
 *   <li>baldes, isqueiro e bola de fogo: não; fluido que "cria" bloco (lava + água, fogo de lava): não;</li>
 *   <li>explosões (creeper, TNT, magias, chefes): não quebram nada da área; monstros não mexem em blocos (mobGriefing,
 *       Wither/Dragão/ravager); a {@link Destruction} das magias e os blocos temporários de magia (muros de terra, gelo…)
 *       também respeitam ({@link Destruction#addWard}).</li>
 * </ul>
 * Alavancas, botões, espelhos, barris dos enigmas etc. continuam funcionando (usar não é quebrar).
 * Custo: as caixas das estruturas ficam em cache por chunk (1 min); o teste por bloco é só "está dentro de uma caixa?".
 */
public final class RunWard {
    private record WardBox(BoundingBox box, boolean tower, long key) {}

    private record Entry(long expires, List<WardBox> boxes) {}

    private static final int CACHE_TICKS = 1200;
    private static final Map<ResourceKey<Level>, Long2ObjectOpenHashMap<Entry>> CACHE = new HashMap<>();
    private static final Map<UUID, Long> LAST_HINT = new HashMap<>();

    private RunWard() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RunWard::onBreak);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RunWard::onPlace);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, RunWard::onExplosion);
        NeoForge.EVENT_BUS.addListener(RunWard::onGriefing);
        NeoForge.EVENT_BUS.addListener(RunWard::onLivingDestroy);
        NeoForge.EVENT_BUS.addListener(RunWard::onFluidPlace);
        NeoForge.EVENT_BUS.addListener(RunWard::onUseOnBlock);
        NeoForge.EVENT_BUS.addListener(RunWard::onUseItem);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> LAST_HINT.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            CACHE.clear();
            LAST_HINT.clear();
        });
        Destruction.addWard(RunWard::warded);
    }

    // ------------------------------------------------------------------ consulta

    /** A posição está numa torre em escalada (não conquistada) ou numa Masmorra em Camadas? Só a thread do servidor. */
    public static boolean warded(ServerLevel level, BlockPos pos) {
        if (level == null || !level.getServer().isSameThread()) return false;
        for (WardBox b : boxes(level, pos.getX() >> 4, pos.getZ() >> 4)) {
            if (!b.box().isInside(pos)) continue;
            if (!b.tower() || !TowerData.get(level).isConquered(b.key())) return true;
        }
        return false;
    }

    private static boolean exempt(Entity e) {
        return e instanceof Player p && p.getAbilities().instabuild;
    }

    private static List<WardBox> boxes(ServerLevel level, int cx, int cz) {
        long key = ChunkPos.asLong(cx, cz);
        long now = level.getGameTime();
        Long2ObjectOpenHashMap<Entry> map = CACHE.computeIfAbsent(level.dimension(), k -> new Long2ObjectOpenHashMap<>());
        Entry e = map.get(key);
        if (e != null && e.expires() > now && e.expires() - now <= CACHE_TICKS) return e.boxes();
        if (!level.hasChunk(cx, cz)) return List.of();
        List<WardBox> out = new ArrayList<>(1);
        try {
            LevelChunk chunk = level.getChunk(cx, cz);
            Map<Structure, LongSet> refs = chunk.getAllReferences();
            if (!refs.isEmpty()) {
                Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
                ResourceLocation depths = DepthsDungeon.KEY.location();
                for (Map.Entry<Structure, LongSet> ref : refs.entrySet()) {
                    boolean tower = reg.wrapAsHolder(ref.getKey()).is(TowerData.TOWERS);
                    boolean dungeon = !tower && depths.equals(reg.getKey(ref.getKey()));
                    if (!tower && !dungeon) continue;
                    level.structureManager().fillStartsForStructure(ref.getKey(), ref.getValue(), start -> {
                        if (!start.isValid()) return;
                        if (tower) out.add(new WardBox(start.getBoundingBox(), true, TowerData.keyOf(start)));
                        else for (StructurePiece piece : start.getPieces()) out.add(new WardBox(piece.getBoundingBox(), false, 0L));
                    });
                }
            }
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.error("[towers] falha ao ler estruturas protegidas no chunk {},{}", cx, cz, ex);
        }
        if (map.size() > 4096) map.clear();
        List<WardBox> list = List.copyOf(out);
        map.put(key, new Entry(now + CACHE_TICKS, list));
        return list;
    }

    private static void hint(Player p) {
        long now = p.level().getGameTime();
        Long last = LAST_HINT.get(p.getUUID());
        if (last != null && now - last < 30) return;
        LAST_HINT.put(p.getUUID(), now);
        p.displayClientMessage(Component.translatable("message.isekai_mahou.ward.protected").withStyle(ChatFormatting.AQUA), true);
    }

    /** Pode quebrar este bloco mesmo na área protegida? (enfeites que não ajudam a trapacear) */
    private static boolean breakable(ServerLevel sl, BlockPos pos, BlockState st) {
        if (PuzzleCoreBlockEntity.enigmaAt(sl, pos) != null || PuzzleCoreBlockEntity.find(sl, pos) != null) return false;
        if (st.getBlock() instanceof SignBlock || st.getBlock() instanceof LeverBlock || st.getBlock() instanceof ButtonBlock
                || st.getBlock() instanceof BasePressurePlateBlock) return false;
        if (st.is(Blocks.SPAWNER) || st.is(Blocks.DECORATED_POT) || st.is(BlockTags.CANDLES)) return true;
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(st.getBlock());
        if (id.getPath().endsWith("_cluster") || id.getPath().endsWith("_bud")) return true;
        return st.getFluidState().isEmpty() && st.getCollisionShape(sl, pos).isEmpty();
    }

    // ------------------------------------------------------------------ eventos

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl) || exempt(e.getPlayer())) return;
        if (!warded(sl, e.getPos()) || breakable(sl, e.getPos(), e.getState())) return;
        e.setCanceled(true);
        hint(e.getPlayer());
    }

    private static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        Entity who = e.getEntity();
        if (exempt(who) || !warded(sl, e.getPos())) return;
        BlockState placed = e.getPlacedBlock();
        // tochas para iluminar: sim (não servem de degrau); de redstone, não
        if (who instanceof Player && placed.getBlock() instanceof BaseTorchBlock && !(placed.getBlock() instanceof RedstoneTorchBlock)) return;
        e.setCanceled(true);
        if (who instanceof Player p) hint(p);
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel sl)) return;
        e.getAffectedBlocks().removeIf(p -> warded(sl, p));
    }

    /** Monstros não mexem em blocos lá dentro (endermen, zumbis em portas, Wither...). Chefes do mod: os efeitos temporários seguem. */
    private static void onGriefing(EntityMobGriefingEvent e) {
        Entity ent = e.getEntity();
        if (ent == null || !(ent.level() instanceof ServerLevel sl) || ent instanceof Player) return;
        if (BossArena.isArenaBoss(ent) && IsekaiMahou.MODID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(ent.getType()).getNamespace())) return;
        if (warded(sl, ent.blockPosition())) e.setCanGrief(false);
    }

    private static void onLivingDestroy(LivingDestroyBlockEvent e) {
        if (e.getEntity().level() instanceof ServerLevel sl && warded(sl, e.getPos())) e.setCanceled(true);
    }

    private static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent e) {
        if (e.getLevel() instanceof ServerLevel sl && warded(sl, e.getPos())) e.setCanceled(true);
    }

    private static boolean changesTerrain(Item item) {
        return item instanceof BucketItem || item instanceof SolidBucketItem || item instanceof FlintAndSteelItem || item instanceof FireChargeItem;
    }

    private static void onUseOnBlock(PlayerInteractEvent.RightClickBlock e) {
        Player p = e.getEntity();
        if (!(p.level() instanceof ServerLevel sl) || exempt(p) || !changesTerrain(e.getItemStack().getItem())) return;
        Direction face = e.getFace() != null ? e.getFace() : Direction.UP;
        if (!warded(sl, e.getPos()) && !warded(sl, e.getPos().relative(face))) return;
        e.setCanceled(true);
        hint(p);
    }

    private static void onUseItem(PlayerInteractEvent.RightClickItem e) {
        Player p = e.getEntity();
        if (!(p.level() instanceof ServerLevel sl) || exempt(p) || !changesTerrain(e.getItemStack().getItem())) return;
        if (!warded(sl, p.blockPosition()) && !warded(sl, BlockPos.containing(p.getEyePosition().add(p.getLookAngle().scale(4))))) return;
        e.setCanceled(true);
        hint(p);
    }
}
