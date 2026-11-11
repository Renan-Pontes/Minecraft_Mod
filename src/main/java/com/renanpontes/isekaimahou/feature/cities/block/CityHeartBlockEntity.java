package com.renanpontes.isekaimahou.feature.cities.block;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Travel;
import com.renanpontes.isekaimahou.feature.cities.CitiesContent;
import com.renanpontes.isekaimahou.feature.cities.CityData;
import com.renanpontes.isekaimahou.feature.cities.CityLines;
import com.renanpontes.isekaimahou.feature.cities.CityNames;
import com.renanpontes.isekaimahou.feature.cities.CityRealm;
import com.renanpontes.isekaimahou.feature.cities.CitySiege;
import com.renanpontes.isekaimahou.feature.cities.entity.CityMerchant;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;
import com.renanpontes.isekaimahou.feature.cities.entity.Profession;
import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * BlockEntity do coração da cidade. Lê do NBT do molde {@code {Radius, CityType, Palette, Kingdom}} (o {@code Kingdom}
 * também pode vir nos dados persistentes {@code NeoForgeData}) e:
 * <ol>
 *   <li>no 1º tick registra o lugar nomeado ({@code city_<x>_<z>}, nome pela lista do REINO, facção do reino — o
 *       letreiro mostra "Império de Sahra"/"Reino de Nordheim"/"Reino de Aldória" —, zona segura conforme a config) e
 *       tira os monstros soltos que já estavam dentro (por alguns segundos, enquanto os chunks carregam);</li>
 *   <li>varre cada chunk da cidade UMA vez ({@link CitySweep}): some com os mobs hostis de moldes de outras estruturas
 *       (inclusive persistentes), com spawners, limpa a vegetação natural ({@link CityVegetation}) e acha o Altar de
 *       Ascensão público e a Pedra de Teleporte da praça (registrada na Viagem: vira o ponto de chegada da cidade);</li>
 *   <li>repõe os moradores mortos: 1 por dia de jogo, de manhã, até a população original ({@link CityData});</li>
 *   <li>conduz o Cerco à Cidade ({@link CitySiege}).</li>
 * </ol>
 * Reino: {@link CityKingdom#forHeart} — corações da v0.8 sem {@code Kingdom}: a capital é Aldória e a cidade comum é o
 * reino da paleta. Cidades que já existiam guardam o nome antigo ({@code LegacyNames}).
 */
public class CityHeartBlockEntity extends BlockEntity {
    /** Altura da caixa do lugar abaixo/acima do coração. */
    private static final int BOX_DOWN = 24;
    private static final int BOX_UP = 56;
    private static final int PURGE_TICKS = 600;
    /** A faixa em volta da muralha também é roçada e varrida (a entrada do portão fica livre). */
    private static final int OUTSKIRTS = 14;
    /** Chunks varridos por vez (a cada 10 ticks). */
    private static final int SWEEP_BUDGET = 4;

    private int radius = 66;
    private String cityType = "city";
    private String palette = "plains";
    /** Reino gravado no molde (v0.9) ou "" (coração antigo: deduzido por {@link CityKingdom#forHeart}). */
    private String kingdomTag = "";
    /** Cidade que já existia antes dos reinos: mantém o nome da lista antiga. */
    private boolean legacyNames;
    private boolean registered;
    private int purgeLeft;
    private int age;
    /** Chunks da cidade já limpos da vegetação natural (ver {@link CityVegetation}). */
    private final LongOpenHashSet cleaned = new LongOpenHashSet();
    private boolean vegetationDone;
    /** Chunks já varridos ({@link CitySweep}). */
    private final LongOpenHashSet swept = new LongOpenHashSet();
    private boolean sweepDone;
    private int purgedTotal;
    @Nullable private BlockPos stone;
    private final List<BlockPos> altars = new ArrayList<>();
    /** Chunks adiados (intruso lutando com um jogador) → idade do coração em que podem ser varridos de novo. Só em memória. */
    private final it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap deferredUntil = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();

    public CityHeartBlockEntity(BlockPos pos, BlockState state) {
        super(CitiesContent.CITY_HEART_BE.get(), pos, state);
    }

    public String placeId() {
        return "city_" + worldPosition.getX() + "_" + worldPosition.getZ();
    }

    public boolean capital() {
        return "capital".equals(cityType);
    }

    public int radius() {
        return radius;
    }

    public String palette() {
        return palette;
    }

    /** Reino da cidade (funciona no cliente também: vai no pacote de atualização). */
    public CityKingdom kingdom() {
        String k = kingdomTag.isEmpty() ? getPersistentData().getString("Kingdom") : kingdomTag;
        return CityKingdom.forHeart(k, cityType, palette);
    }

    /** Facção do reino ({@code kingdom}, {@code kingdom_sahra}, {@code kingdom_nordheim}). */
    public String faction() {
        return kingdom().factionId;
    }

    /** Nome da cidade (lista do reino; cidades antigas mantêm o nome que tinham). */
    public MutableComponent cityName(ServerLevel sl) {
        return CityNames.cityName(sl, worldPosition, capital(), kingdom(), legacyNames);
    }

    /** Pedra de Teleporte achada na praça (ou null). */
    @Nullable
    public BlockPos stone() {
        return stone;
    }

    /** Altares de Ascensão públicos achados na cidade (Igreja/Templo e torre da Academia). */
    public List<BlockPos> altars() {
        return Collections.unmodifiableList(altars);
    }

    /** Monstros de outras estruturas removidos pela varredura. */
    public int purgedTotal() {
        return purgedTotal;
    }

    public boolean sweepDone() {
        return sweepDone;
    }

    /** Y do chão da cidade (o coração fica no chafariz, 4 blocos acima na cidade e 5 na capital). */
    public int floorY() {
        return worldPosition.getY() - (capital() ? 5 : 4);
    }

    public BoundingBox box() {
        return new BoundingBox(worldPosition.getX() - radius, worldPosition.getY() - BOX_DOWN, worldPosition.getZ() - radius,
                worldPosition.getX() + radius, worldPosition.getY() + BOX_UP, worldPosition.getZ() + radius);
    }

    public AABB area() {
        BoundingBox b = box();
        return new AABB(b.minX(), b.minY(), b.minZ(), b.maxX() + 1, b.maxY() + 1, b.maxZ() + 1);
    }

    /** Configura um coração colocado à mão / por comando (sem NBT de molde). */
    public void configure(int radius, String type, String palette) {
        this.radius = Mth.clamp(radius, 16, 160);
        this.cityType = type;
        this.palette = palette;
        this.registered = false;
        setChanged();
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    public static void serverTick(Level level, BlockPos pos, BlockState state, CityHeartBlockEntity be) {
        if (!(level instanceof ServerLevel sl)) return;
        be.age++;
        if (!be.registered) {
            be.register(sl);
        } else if (be.age % 200 == 0) {
            // Lugar apagado (/mahou place remove), config CITY_PROTECTION mudou ou o reino mudou (cidade da v0.8
            // virando Sahra/Nordheim): registra de novo.
            Places.Place p = Places.get(sl, be.placeId());
            if (p == null || p.safeZone() != MahouConfig.CITY_PROTECTION.get() || !be.faction().equals(p.faction())) be.register(sl);
        }
        if (be.purgeLeft > 0) {
            be.purgeLeft--;
            if (be.purgeLeft % 40 == 0) be.purgeLoose(sl);
        }
        if (be.age % 100 == 50) be.respawnTick(sl);
        if (be.registered && !be.vegetationDone && be.age % 10 == 3) be.cleanVegetation(sl);
        if (be.registered && !be.sweepDone && be.age % 10 == 8) be.sweepNext(sl);
        if (be.registered && be.age % 1200 == 600) be.checkStone(sl);
        CitySiege.tickHeart(sl, be);
    }

    /** Registra (ou atualiza) o lugar nomeado da cidade. */
    public void register(ServerLevel sl) {
        CityKingdom k = kingdom();
        Places.Place place = new Places.Place(placeId(), cityName(sl), capital() ? "capital" : "city", k.factionId,
                sl.dimension(), box(), worldPosition, MahouConfig.CITY_PROTECTION.get(), CityRealm.placeColor(k, capital()));
        Places.register(sl, place);
        if (!registered) {
            registered = true;
            purgeLeft = PURGE_TICKS;
            purgeLoose(sl);
            CityData.get(sl).city(placeId());
            setChanged();
        }
        ensureStoneRegistered(sl);
    }

    /**
     * A Pedra da praça vira a chegada da cidade na Viagem. O próprio módulo travel liga a pedra à cidade (com um ponto de
     * chegada no chão ao lado dela); aqui é só a rede de segurança: registra quando ninguém registrou nada ainda, para
     * não trocar a chegada boa do travel pela posição do bloco.
     */
    private void ensureStoneRegistered(ServerLevel sl) {
        if (stone == null) return;
        net.minecraft.core.GlobalPos cur = Travel.stone(sl.getServer(), placeId());
        if (cur == null || !cur.dimension().equals(sl.dimension())) Travel.registerStone(sl, placeId(), stone);
    }

    /** Área varrida/roçada: a cidade + a faixa fora da muralha. */
    private BoundingBox sweepBox() {
        return box().inflatedBy(OUTSKIRTS, 0, OUTSKIRTS);
    }

    /** Limpa a vegetação natural de UM chunk carregado ainda não limpo (árvores do bioma por cima da cidade). */
    private void cleanVegetation(ServerLevel sl) {
        BoundingBox b = sweepBox();
        int cx0 = b.minX() >> 4, cx1 = b.maxX() >> 4, cz0 = b.minZ() >> 4, cz1 = b.maxZ() >> 4;
        boolean pending = false;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                if (cleaned.contains(key)) continue;
                pending = true;
                if (!sl.hasChunk(cx, cz)) continue;
                CityVegetation.cleanChunk(sl, new ChunkPos(cx, cz), b, floorY() - 4, worldPosition.getY() + BOX_UP);
                cleaned.add(key);
                setChanged();
                return;
            }
        }
        if (!pending) {
            vegetationDone = true;
            setChanged();
        }
    }

    /**
     * Varre até {@link #SWEEP_BUDGET} chunks ainda não varridos cujos blocos e entidades já estão carregados (ver
     * {@link CitySweep}). Cada varredura é barata (uma busca de entidades e a lista de BlockEntities do chunk), então uma
     * capital inteira fica limpa em poucos segundos — antes de o jogador chegar perto dos monstros de molde.
     */
    private void sweepNext(ServerLevel sl) {
        BoundingBox b = sweepBox();
        int cx0 = b.minX() >> 4, cx1 = b.maxX() >> 4, cz0 = b.minZ() >> 4, cz1 = b.maxZ() >> 4;
        boolean pending = false;
        int budget = SWEEP_BUDGET;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                long key = ChunkPos.asLong(cx, cz);
                if (swept.contains(key)) continue;
                pending = true;
                if (!sl.hasChunk(cx, cz) || !sl.areEntitiesLoaded(key)) continue;
                if (deferredUntil.containsKey(key) && age < deferredUntil.get(key)) continue;
                CitySweep.Result r = CitySweep.sweep(sl, sl.getChunk(cx, cz), b, floorY(), worldPosition);
                if (r.deferred) {
                    deferredUntil.put(key, age + 400);   // tenta de novo em 20 s
                } else {
                    swept.add(key);
                    deferredUntil.remove(key);
                }
                if (r.purged > 0 || r.spawners > 0) {
                    purgedTotal += r.purged;
                    IsekaiMahou.LOGGER.info("Cidade {}: chunk {},{} — {} monstro(s) de outras estruturas e {} spawner(s) removidos",
                            placeId(), cx, cz, r.purged, r.spawners);
                }
                for (BlockPos a : r.altars) {
                    if (!altars.contains(a)) altars.add(a);
                }
                if (r.stone != null && (stone == null || r.stone.distSqr(worldPosition) < stone.distSqr(worldPosition))) {
                    stone = r.stone;
                    ensureStoneRegistered(sl);
                    IsekaiMahou.LOGGER.debug("Cidade {}: Pedra de Teleporte em {}", placeId(), stone);
                }
                setChanged();
                if (--budget <= 0) return;
            }
        }
        if (!pending) {
            sweepDone = true;
            setChanged();
        }
    }

    /**
     * De tempos em tempos: a pedra ainda está lá? Quebrada → esquecida (o módulo travel tira o registro); de pé mas sem
     * registro na Viagem (ex.: dados da viagem apagados) → registra de novo. Altares quebrados saem da lista.
     */
    private void checkStone(ServerLevel sl) {
        if (stone != null && sl.isLoaded(stone)) {
            boolean there = BuiltInRegistries.BLOCK.getOptional(IsekaiMahou.id("travel_stone"))
                    .map(blk -> sl.getBlockState(stone).is(blk)).orElse(false);
            if (!there) {
                stone = null;
                setChanged();
            } else {
                ensureStoneRegistered(sl);
            }
        }
        if (!altars.isEmpty()) {
            boolean changed = BuiltInRegistries.BLOCK.getOptional(IsekaiMahou.id("ascension_core"))
                    .map(core -> altars.removeIf(a -> sl.isLoaded(a) && !sl.getBlockState(a).is(core))).orElse(false);
            if (changed) setChanged();
        }
    }

    /** Monstros soltos (spawn natural) dentro da cidade somem — os que nasceram antes de a cidade ser registrada. */
    private void purgeLoose(ServerLevel sl) {
        if (!MahouConfig.CITY_PROTECTION.get()) return;
        List<Mob> list = sl.getEntitiesOfClass(Mob.class, area(), m -> CitySweep.isIntruder(m, false));
        for (Mob m : list) m.discard();
    }

    // =====================================================================================
    //  Repovoamento
    // =====================================================================================
    private void respawnTick(ServerLevel sl) {
        CityData data = CityData.get(sl);
        CityData.City city = data.existing(placeId());
        if (city == null || city.dead.isEmpty() || CitySiege.active(placeId())) return;
        long day = sl.getDayTime() / 24000L;
        if (city.lastRespawnDay < 0) {
            city.lastRespawnDay = day;   // a primeira reposição sai no dia seguinte
            data.setDirty();
            return;
        }
        long time = sl.getDayTime() % 24000L;
        if (day <= city.lastRespawnDay || time > 12000L) return;
        if (sl.getNearestPlayer(worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), radius + 64.0, false) == null) return;
        int n = (int) Math.min(2, day - city.lastRespawnDay);
        city.lastRespawnDay = day;
        data.setDirty();
        for (int i = 0; i < n; i++) {
            CityData.Dead dead = data.pollDead(placeId());
            if (dead == null) break;
            spawnReplacement(sl, dead);
        }
    }

    /** Traz um morador novo para o lugar do que morreu (ou para a praça, se o lugar não estiver carregado). */
    @Nullable
    public CityNpc spawnReplacement(ServerLevel sl, CityData.Dead dead) {
        String key = dead.key();
        EntityType<? extends CityNpc> type = switch (key.contains(":") ? key.substring(0, key.indexOf(':')) : key) {
            case "guard" -> CitiesContent.CITY_GUARD.get();
            case "lord" -> CitiesContent.CITY_LORD.get();
            case "merchant" -> CitiesContent.CITY_MERCHANT.get();
            default -> CitiesContent.CITIZEN.get();
        };
        CityNpc npc = type.create(sl);
        if (npc == null) return null;
        if (npc instanceof CityMerchant m && key.startsWith("merchant:")) {
            Profession p = Profession.byId(key.substring("merchant:".length()));
            if (p != null) m.setProfession(p);
        }
        BlockPos home = dead.home();
        npc.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, sl.random.nextFloat() * 360.0F, 0.0F);
        if (!sl.isLoaded(home) || !sl.noCollision(npc)) {
            double a = sl.random.nextDouble() * Math.PI * 2;
            int x = worldPosition.getX() + (int) Math.round(Math.cos(a) * 9);
            int z = worldPosition.getZ() + (int) Math.round(Math.sin(a) * 9);
            int y = sl.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            npc.moveTo(x + 0.5, y, z + 0.5, sl.random.nextFloat() * 360.0F, 0.0F);
        }
        npc.finalizeSpawn(sl, sl.getCurrentDifficultyAt(npc.blockPosition()), MobSpawnType.EVENT, null);
        npc.bindToCity(placeId(), home);
        npc.joinKingdom(kingdom());
        if (!sl.addFreshEntity(npc)) return null;
        sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, npc.getX(), npc.getY() + 1.0, npc.getZ(), 12, 0.4, 0.6, 0.4, 0.0);
        sl.playSound(null, npc.blockPosition(), SoundEvents.BELL_RESONATE, SoundSource.NEUTRAL, 0.6F, 1.4F);
        Component msg = CityLines.tr("message.isekai_mahou.cities.respawn." + npc.kindId(), npc.getDisplayName(), cityName(sl));
        for (ServerPlayer p : sl.players()) {
            if (area().contains(p.position())) p.displayClientMessage(msg, true);
        }
        IsekaiMahou.LOGGER.debug("Cidade {}: {} reposto", placeId(), key);
        return npc;
    }

    // =====================================================================================
    //  NBT e cliente
    // =====================================================================================
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);   // lê os NeoForgeData (getPersistentData)
        if (tag.contains("Radius")) radius = Mth.clamp(tag.getInt("Radius"), 16, 160);
        if (tag.contains("CityType")) cityType = "capital".equals(tag.getString("CityType")) ? "capital" : "city";
        if (tag.contains("Palette")) palette = tag.getString("Palette");
        boolean hasKingdom = tag.contains("Kingdom") || getPersistentData().contains("Kingdom");
        if (tag.contains("Kingdom")) kingdomTag = tag.getString("Kingdom");
        registered = tag.getBoolean("Registered");
        // Coração da v0.8 já registrado e sem reino gravado: a cidade já tinha nome — mantém a lista antiga.
        legacyNames = tag.contains("LegacyNames") ? tag.getBoolean("LegacyNames") : (!hasKingdom && registered);
        cleaned.clear();
        for (long l : tag.getLongArray("VegClean")) cleaned.add(l);
        vegetationDone = tag.getBoolean("VegDone");
        swept.clear();
        for (long l : tag.getLongArray("Swept")) swept.add(l);
        sweepDone = tag.getBoolean("SweepDone");
        purgedTotal = tag.getInt("Purged");
        stone = tag.contains("Stone") ? BlockPos.of(tag.getLong("Stone")) : null;
        altars.clear();
        for (long l : tag.getLongArray("Altars")) altars.add(BlockPos.of(l));
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Radius", radius);
        tag.putString("CityType", cityType);
        tag.putString("Palette", palette);
        tag.putString("Kingdom", kingdom().id);
        tag.putBoolean("LegacyNames", legacyNames);
        tag.putBoolean("Registered", registered);
        tag.putLongArray("VegClean", cleaned.toLongArray());
        tag.putBoolean("VegDone", vegetationDone);
        tag.putLongArray("Swept", swept.toLongArray());
        tag.putBoolean("SweepDone", sweepDone);
        tag.putInt("Purged", purgedTotal);
        if (stone != null) tag.putLong("Stone", stone.asLong());
        long[] a = new long[altars.size()];
        for (int i = 0; i < a.length; i++) a[i] = altars.get(i).asLong();
        tag.putLongArray("Altars", a);
    }

    /** Ao cliente só vai o que o cristal precisa (cor do reino). */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("CityType", cityType);
        tag.putString("Palette", palette);
        tag.putString("Kingdom", kingdom().id);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
