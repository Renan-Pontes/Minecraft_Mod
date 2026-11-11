package com.renanpontes.isekaimahou.feature.guild;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.TamableAnimal;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Contadores do MagicData que a Guilda grava para as missões de facção (v0.8) e o Diário:
 * <ul>
 *   <li>{@code guild.kill_undead} — mortos-vivos (tag {@code #minecraft:undead}) abatidos;</li>
 *   <li>{@code guild.kill_night} — monstros abatidos à noite (contratos noturnos do Submundo);</li>
 *   <li>{@code guild.kill_city} — monstros abatidos dentro de uma cidade/capital ({@link Places});</li>
 *   <li>{@code guild.kill_horde_city} — membros da Horda abatidos a até 160 blocos do centro de uma cidade;</li>
 *   <li>{@code guild.kill_horde_forest} — membros da Horda abatidos em florestas;</li>
 *   <li>{@code guild.plant} — mudas plantadas pelo jogador;</li>
 *   <li>(em outros lugares) {@code guild.hired}, {@code guild.companion_kills}, {@code guild.faction_quests[.<facção>]}.</li>
 * </ul>
 */
final class GuildCounters {
    private static final double HORDE_CITY_RANGE = 160.0;

    static void onDeath(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (!(victim.level() instanceof ServerLevel sl)) return;
        if (!(e.getSource().getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        if (victim instanceof TamableAnimal t && t.isTame()) return;
        boolean monster = victim instanceof Enemy || victim.getType().getCategory() == MobCategory.MONSTER;
        if (victim.getType().is(EntityTypeTags.UNDEAD)) MagicApi.addCounter(sp, "guild.kill_undead", 1);
        if (monster && sl.isNight()) MagicApi.addCounter(sp, "guild.kill_night", 1);
        BlockPos pos = victim.blockPosition();
        Places.Place here = Places.at(sl, pos);
        if (monster && here != null && isCity(here)) MagicApi.addCounter(sp, "guild.kill_city", 1);
        if ("horde".equals(Reputation.factionOf(victim))) {
            if (sl.getBiome(pos).is(BiomeTags.IS_FOREST)) MagicApi.addCounter(sp, "guild.kill_horde_forest", 1);
            if (nearCity(sl, pos)) MagicApi.addCounter(sp, "guild.kill_horde_city", 1);
        }
    }

    static void onPlace(BlockEvent.EntityPlaceEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp && !(sp instanceof FakePlayer) && e.getPlacedBlock().is(BlockTags.SAPLINGS)) {
            MagicApi.addCounter(sp, "guild.plant", 1);
        }
    }

    private static boolean isCity(Places.Place p) {
        return "city".equals(p.type()) || "capital".equals(p.type());
    }

    private static boolean nearCity(ServerLevel sl, BlockPos pos) {
        for (String type : new String[]{"city", "capital"}) {
            Places.Place p = Places.nearest(sl, pos, type);
            if (p != null && p.dim() == sl.dimension() && p.center().distSqr(pos) <= HORDE_CITY_RANGE * HORDE_CITY_RANGE) return true;
        }
        return false;
    }

    private GuildCounters() {}
}
