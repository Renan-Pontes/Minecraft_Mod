package com.renanpontes.isekaimahou.feature.towers;

import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.feature.towers.block.FloorCoreBlockEntity;
import com.renanpontes.isekaimahou.feature.towers.item.TwinCombo;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Eventos de jogo das Torres: paredes protegidas até o pináculo ser conquistado ({@link RunWard}), morte do guardião,
 * entrada na torre (diário + lugar no {@link Places}), o combo das Lâminas Gêmeas e, desde a v1.0, os afixos de andar
 * ({@link TwAffixEvents}) e os lugares do céu ({@link SkySites}: corrente ascendente e queda amortecida).
 */
public final class TwEvents {
    public static final String PLACE_TYPE = "boss_lair";

    public static void register() {
        // v1.0 r9: proteção completa (quebrar, colocar, explosões, fluidos, monstros, magias) — torres E masmorra
        RunWard.register();
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, TwEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(TwEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> {
            TwinCombo.stop(e.getEntity());
            SkySites.forget(e.getEntity().getUUID());
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> SkySites.clearAll());
        TwAffixEvents.register();
    }

    // ------------------------------------------------------------------ Proteção: ver RunWard (v1.0 r9)

    // ------------------------------------------------------------------ Guardião

    private static void onDeath(LivingDeathEvent event) {
        LivingEntity e = event.getEntity();
        if (event.isCanceled() || !(e.level() instanceof ServerLevel sl) || !e.getTags().contains(FloorCoreBlockEntity.GUARDIAN_TAG)) return;
        if (!e.getPersistentData().contains(FloorCoreBlockEntity.CORE_KEY)) return;
        BlockPos core = BlockPos.of(e.getPersistentData().getLong(FloorCoreBlockEntity.CORE_KEY));
        if (sl.isLoaded(core) && sl.getBlockEntity(core) instanceof FloorCoreBlockEntity be) be.onGuardianDefeated(sl);
    }

    // ------------------------------------------------------------------ Jogador

    private static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        TwinCombo.tick(sp);
        SkySites.tick(sp);
        if (sp.tickCount % 40 != 0 || sp.level().dimension() != Level.OVERWORLD || sp.isSpectator()) return;
        ServerLevel sl = sp.serverLevel();
        StructureStart s = TowerData.towerAt(sl, sp.blockPosition());
        if (!s.isValid()) return;
        registerPlace(sl, s);
        if (TwProgress.flag(sp, TwProgress.FLAG_ENTERED)) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.towers.entered").withColor(0x7FD4FF));
            sl.playSound(null, sp.blockPosition(), SoundEvents.BELL_BLOCK, SoundSource.AMBIENT, 1.0F, 0.7F);
        }
    }

    /** Torre vira um lugar com nome (letreiro de entrada, viagem), uma vez por torre. */
    private static void registerPlace(ServerLevel sl, StructureStart s) {
        BoundingBox bb = s.getBoundingBox();
        String id = "tower_" + bb.getCenter().getX() + "_" + bb.getCenter().getZ();
        if (Places.get(sl, id) != null) return;
        ResourceLocation key = sl.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(s.getStructure());
        String name = key != null ? key.getPath() : "tower_stone";
        Places.register(sl, new Places.Place(id, Component.translatable("place.isekai_mahou." + name), PLACE_TYPE, "",
                sl.dimension(), bb, TowerData.entrance(sl, s), false, 0x7FD4FF));
    }

    private TwEvents() {}
}
