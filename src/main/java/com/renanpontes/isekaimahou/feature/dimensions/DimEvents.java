package com.renanpontes.isekaimahou.feature.dimensions;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Cutscenes;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Eventos de jogo do módulo: contagem de visitas + cutscene da primeira chegada, proteção dos hubs e das peças de
 * portal nas estruturas do Mundo Normal, hubs no {@code Places} (Viagem), relâmpagos dos Céus da Tempestade, volta do
 * Arauto do Crepúsculo e o agendador de tarefas.
 */
public final class DimEvents {
    public static final ResourceKey<Biome> STORM_SKIES = ResourceKey.create(Registries.BIOME, IsekaiMahou.id("storm_skies"));
    private static final Map<UUID, Integer> VISIT_SNAPSHOT = new HashMap<>();

    public static void register(IEventBus bus) {
        // Contador de visita: o módulo de Caminhos também soma visit.*; foto antes e conferência depois
        // garantem exatamente +1 por entrada, exista ou não o outro módulo.
        bus.addListener(EventPriority.HIGHEST, PlayerEvent.PlayerChangedDimensionEvent.class, DimEvents::beforeDimension);
        bus.addListener(EventPriority.LOWEST, PlayerEvent.PlayerChangedDimensionEvent.class, DimEvents::afterDimension);
        bus.addListener(BlockEvent.BreakEvent.class, DimEvents::onBreak);
        bus.addListener(ExplosionEvent.Detonate.class, DimEvents::onExplosion);
        bus.addListener(LivingDeathEvent.class, DimEvents::onDeath);
        bus.addListener(ServerTickEvent.Post.class, DimEvents::onServerTick);
        bus.addListener(ServerStartedEvent.class, e -> DimHubs.registerBuiltHubs(e.getServer()));
        bus.addListener(ServerStoppedEvent.class, e -> {
            DimTasks.clear();
            VISIT_SNAPSHOT.clear();
        });
    }

    private static void beforeDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        DimKind kind = DimKind.of(e.getTo());
        if (kind != null && e.getEntity() instanceof ServerPlayer p) {
            VISIT_SNAPSHOT.put(p.getUUID(), MagicApi.get(p).counter(kind.visitKey()));
        }
    }

    private static void afterDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        DimKind kind = DimKind.of(e.getTo());
        if (kind == null || !(e.getEntity() instanceof ServerPlayer p)) return;
        MagicData d = MagicApi.get(p);
        Integer before = VISIT_SNAPSHOT.remove(p.getUUID());
        if (before == null || d.counter(kind.visitKey()) == before) MagicApi.addCounter(p, kind.visitKey(), 1);

        ServerLevel level = p.serverLevel();
        DimHubs.ensureHub(level, kind);
        if (kind == DimKind.TWILIGHT) DimHubs.checkHerald(level);

        String seen = "dimensions.seen." + kind.id;
        if (!d.hasFlag(seen)) {
            d.setFlag(seen, true);
            // Pequeno atraso: o cliente termina de carregar a dimensão antes da câmera girar.
            DimTasks.later(20, () -> {
                if (!p.isRemoved() && p.level().dimension().equals(kind.key)) {
                    Cutscenes.play(p, "portal", p, kind.displayName().withStyle(ChatFormatting.BOLD),
                            kind.arrivalSubtitle(), kind.color, 70, true);
                }
            });
        }
    }

    /**
     * Os hubs são protegidos por magia antiga: só no criativo dá para quebrar blocos ali. Nas estruturas de portal do
     * Mundo Normal, só as peças do portal (moldura, Farol e o ouro dele, Fenda) ficam protegidas: sem receitas, o
     * portal da estrutura é o caminho — não dá para desmontá-lo e levar para casa.
     */
    private static void onBreak(BlockEvent.BreakEvent e) {
        if (!(e.getLevel() instanceof ServerLevel level) || e.getPlayer().getAbilities().instabuild) return;
        DimKind kind = DimKind.of(level.dimension());
        if (kind != null && kind.isProtected(e.getPos())) {
            e.setCanceled(true);
            e.getPlayer().displayClientMessage(Component.translatable("message.isekai_mahou.dimensions.protected")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        } else if (kind == null && DimSites.isProtectedPiece(level, e.getPos(), e.getState())) {
            e.setCanceled(true);
            e.getPlayer().displayClientMessage(Component.translatable("message.isekai_mahou.dimensions.site_protected")
                    .withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    private static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        DimKind kind = DimKind.of(level.dimension());
        if (kind != null) {
            e.getAffectedBlocks().removeIf(kind::isProtected);
        } else {
            // Primeiro o teste barato (é peça de portal?); só essas consultam os lugares.
            e.getAffectedBlocks().removeIf(p -> DimSites.isProtectedPiece(level, p, level.getBlockState(p)));
        }
    }

    private static void onDeath(LivingDeathEvent e) {
        if (!(e.getEntity().level() instanceof ServerLevel level) || !level.dimension().equals(DimKind.TWILIGHT.key)) return;
        if (IsekaiMahou.id("twilight_herald").equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getEntity().getType()))) {
            DimHubs.heraldDefeated(level);
        }
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        DimTasks.tick();
        if (e.getServer().getTickCount() % 40 != 0) return;
        ServerLevel plane = e.getServer().getLevel(DimKind.ELEMENTAL.key);
        if (plane != null) stormLightning(plane);
    }

    /** Relâmpagos só visuais (sem fogo nem dano) caindo ao longe nos Céus da Tempestade. */
    private static void stormLightning(ServerLevel level) {
        RandomSource r = level.getRandom();
        for (ServerPlayer p : level.players()) {
            if (r.nextInt(4) != 0 || !level.getBiome(p.blockPosition()).is(STORM_SKIES)) continue;
            double ang = r.nextDouble() * Math.PI * 2;
            double dist = 24 + r.nextDouble() * 40;
            int x = (int) (p.getX() + Math.cos(ang) * dist);
            int z = (int) (p.getZ() + Math.sin(ang) * dist);
            // getHeight carregaria (e geraria) o chunk na hora: só usa chunks que já estão carregados.
            if (!level.getChunkSource().hasChunk(x >> 4, z >> 4)) continue;
            int surface = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            int y = surface > level.getMinBuildHeight() + 1 ? surface : (int) p.getY() - 10 - r.nextInt(30);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) continue;
            bolt.moveTo(x + 0.5, y, z + 0.5);
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    private DimEvents() {}
}
