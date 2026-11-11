package com.renanpontes.isekaimahou.feature.world_v09;

import java.util.Map;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.PlaceEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * Liga as estruturas do world_v09 ao resto do jogo:
 * <ul>
 *   <li>quando o chunk de INÍCIO de uma delas carrega (inclusive recém-gerado), registra um {@code Places.Place}
 *       com o nome traduzido ("Covil da Hidra do Pântano"): letreiro de entrada, "novo lugar descoberto", flag
 *       {@code visit.place.<id>} e destino de Viagem (a Pedra de Teleporte de dentro vira o ponto de chegada);</li>
 *   <li>ao entrar: dica na barra de ação (como despertar o portal / o que espera no covil) e, na 1ª visita, contador
 *       {@code visit.site.<estrutura>} (+1) para o Diário;</li>
 *   <li>o chefe que vem no molde do covil (tag {@link #LAIR_TAG}) ganha a arena ({@code BossKit.setArena}) no lugar onde
 *       nasceu, com o raio gravado no molde.</li>
 * </ul>
 */
public final class V09Places {
    /** Tag de entidade dos chefes colocados pelos moldes dos covis. */
    public static final String LAIR_TAG = "isekai_mahou.lair_boss";
    /** persistentData (NeoForgeData no molde) com {r: raio da arena, site: id da estrutura}; some depois de lido. */
    public static final String LAIR_KEY = "isekai_mahou.lair";

    public static void init() {
        NeoForge.EVENT_BUS.addListener(V09Places::onChunkLoad);
        NeoForge.EVENT_BUS.addListener(V09Places::onEnter);
        NeoForge.EVENT_BUS.addListener(V09Places::onJoin);
    }

    /** Id do lugar: estrutura + chunk de início (único por mundo). */
    public static String placeId(V09Site site, int chunkX, int chunkZ) {
        return site.id + "_" + chunkX + "_" + chunkZ;
    }

    private static void onChunkLoad(ChunkEvent.Load e) {
        if (!(e.getChunk() instanceof LevelChunk chunk) || !(chunk.getLevel() instanceof ServerLevel level)) return;
        Map<Structure, StructureStart> starts = chunk.getAllStarts();
        if (starts.isEmpty()) return;
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, StructureStart> en : starts.entrySet()) {
            StructureStart start = en.getValue();
            if (start == null || !start.isValid()) continue;
            ResourceLocation key = reg.getKey(en.getKey());
            if (key == null || !key.getNamespace().equals(IsekaiMahou.MODID)) continue;
            V09Site site = V09Site.byId(key.getPath());
            if (site != null) register(level, site, start);
        }
    }

    private static void register(ServerLevel level, V09Site site, StructureStart start) {
        String id = placeId(site, start.getChunkPos().x, start.getChunkPos().z);
        if (Places.get(level, id) != null) return;
        // Caixa das PEÇAS (o molde): StructureStart#getBoundingBox vem inflada em 12 blocos quando a estrutura tem
        // terrain_adaptation (beard_thin) — o centro ficaria 12 blocos abaixo do chão e o letreiro dispararia longe.
        BoundingBox bb = BoundingBox.encapsulatingBoxes(start.getPieces().stream().map(StructurePiece::getBoundingBox).toList())
                .orElseGet(start::getBoundingBox);
        BoundingBox box = new BoundingBox(bb.minX() - 2, bb.minY() - 2, bb.minZ() - 2, bb.maxX() + 2, bb.maxY() + 10, bb.maxZ() + 2);
        BlockPos center = new BlockPos((bb.minX() + bb.maxX()) / 2, bb.minY() + site.ground, (bb.minZ() + bb.maxZ()) / 2);
        Places.register(level, new Places.Place(id, site.displayName(), site.kind.placeType, "", level.dimension(), box, center,
                site.kind.safe, site.color));
    }

    private static void onEnter(PlaceEvent.Enter e) {
        Places.Place place = e.getPlace();
        V09Site site = siteOf(place);
        if (site == null) return;
        ServerPlayer p = e.getPlayer();
        p.displayClientMessage(site.hint().copy().withStyle(ChatFormatting.ITALIC).withStyle(s -> s.withColor(site.color)), true);
        if (e.isFirstVisit()) {
            MagicApi.addCounter(p, "visit.site." + site.id, 1);
            arrivalSound(p, site);
        }
    }

    /** O lugar é de uma estrutura do world_v09? (pelo prefixo do id e pelo tipo) */
    private static V09Site siteOf(Places.Place place) {
        for (V09Site s : V09Site.values()) {
            if (place.type().equals(s.kind.placeType) && place.id().startsWith(s.id + "_")) return s;
        }
        return null;
    }

    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof Mob mob) || !mob.getTags().contains(LAIR_TAG)) return;
        CompoundTag pd = mob.getPersistentData();
        if (!pd.contains(LAIR_KEY)) return;
        double r = pd.getCompound(LAIR_KEY).getDouble("r");
        pd.remove(LAIR_KEY);
        if (BossKit.arenaCenter(mob) == null && r > 0) BossKit.setArena(mob, mob.position(), r);
        mob.setPersistenceRequired();
    }

    /** Som de chegada na 1ª visita (o letreiro é do núcleo): ameaça nos covis, ressonância nos portais. */
    private static void arrivalSound(ServerPlayer p, V09Site site) {
        switch (site.kind) {
            case LAIR -> p.playNotifySound(SoundEvents.WARDEN_HEARTBEAT, SoundSource.AMBIENT, 1.0F, 0.6F);
            case PORTAL -> p.playNotifySound(SoundEvents.END_PORTAL_FRAME_FILL, SoundSource.AMBIENT, 0.8F, 0.7F);
            case SANCTUM -> p.playNotifySound(SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.AMBIENT, 1.0F, 1.2F);
        }
    }

    private V09Places() {}
}
