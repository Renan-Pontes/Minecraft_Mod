package com.renanpontes.isekaimahou.feature.settlements;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.feature.settlements.build.CityLayout;
import com.renanpontes.isekaimahou.feature.settlements.entity.BuilderNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.MilitiaNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;
import com.renanpontes.isekaimahou.feature.ui_hud.Hints;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Onda 8 (water_city) — a VIDA na Cidade das Águas:
 * <ul>
 *   <li><b>Traço "Porto"</b>: cidadãos respiram o dobro do tempo debaixo d'água dentro do território (sem ícone na
 *       tela: o fôlego só acaba mais devagar); a pesca que abastece a cidade fica em {@link WaterCity#fishing}.</li>
 *   <li><b>NPCs nunca se perdem</b> ({@link #rescue}): quem caiu na água volta para o piso firme mais perto no
 *       próximo sinal de vida.</li>
 *   <li><b>Invasões pelo mar</b> ({@link #raidSpot}, {@link #raidPool}, {@link #raidRecover}): a horda nasce na borda
 *       das plataformas, com respingos, e os afogados entram no elenco.</li>
 *   <li>Dica do primeiro passeio de barco pelos canais.</li>
 * </ul>
 * Tudo sai na primeira linha quando o servidor não tem nenhuma cidade das águas.
 */
public final class WaterLife {
    /** Cidades das águas do servidor (lista pequena, refeita a cada 10 s). */
    private static volatile Settlement[] cities = new Settlement[0];

    public static void register() {
        NeoForge.EVENT_BUS.addListener(WaterLife::onServerTick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.server.ServerStoppedEvent e) -> {
            cities = new Settlement[0];
            WaterWorks.clear();
        });
    }

    public static void refresh(MinecraftServer server) {
        List<Settlement> out = new ArrayList<>();
        for (Settlement s : SettlementData.get(server).all()) if (s.water) out.add(s);
        cities = out.toArray(new Settlement[0]);
    }

    /** A cidade das águas em cujo território o jogador está (ou null). */
    @Nullable
    private static Settlement at(ServerPlayer sp) {
        for (Settlement s : cities) {
            if (s.dim != sp.level().dimension()) continue;
            int h = CityLayout.half(s) + 24;
            if (Math.abs(sp.getX() - s.heart.getX()) <= h && Math.abs(sp.getZ() - s.heart.getZ()) <= h
                    && Math.abs(sp.getY() - s.heart.getY()) <= 48) return s;
        }
        return null;
    }

    private static void onServerTick(ServerTickEvent.Post e) {
        MinecraftServer server = e.getServer();
        int t = server.getTickCount();
        if (t % 200 == 110) refresh(server);
        if (cities.length == 0 || t % 20 != 6) return;
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (!sp.isAlive() || sp.isSpectator()) continue;
            Settlement s = at(sp);
            if (s == null) continue;
            // Porto: o fôlego dos cidadãos acaba na metade da velocidade (20 de ar por segundo → 10)
            if (sp.isEyeInFluid(FluidTags.WATER) && sp.getAirSupply() < sp.getMaxAirSupply() && sp.getAirSupply() > 0
                    && CityEconomy.isMember(server, s, sp)) {
                sp.setAirSupply(Math.min(sp.getMaxAirSupply(), sp.getAirSupply() + 10));
            }
            if (sp.getVehicle() instanceof Boat) Hints.show(sp, "city_water_boat");
        }
    }

    // =====================================================================================
    //  NPCs: ninguém se perde
    // =====================================================================================
    /**
     * Sinal de vida de um NPC de cidade das águas: quem está dentro d'água é devolvido ao piso firme mais perto
     * (passarela, deque ou margem). Construtor trabalhando num canteiro e miliciano seguindo o Senhor ficam onde estão.
     */
    public static void rescue(ServerLevel sl, Settlement s, SettlementNpc npc) {
        if (!s.water || !npc.isAlive() || npc.isPassenger() || !npc.isInWater()) return;
        if (npc instanceof BuilderNpc b && b.site() != null && sl.getFluidState(npc.blockPosition().above()).isEmpty()) return;
        if (npc instanceof MilitiaNpc && s.militiaOrder == Settlement.ORDER_FOLLOW && s.owner != null) {
            ServerPlayer lord = sl.getServer().getPlayerList().getPlayer(s.owner);
            if (lord != null && lord.level() == sl && lord.isAlive() && !lord.isSpectator()) return;
        }
        BlockPos to = WaterCity.firm(sl, s, npc.getBlockX(), npc.getBlockZ(), 16);
        if (to == null) to = WaterCity.firmOrPlaza(sl, s, s.heart.offset(2, 0, 2), 6);
        splash(sl, npc.getX(), npc.getY(), npc.getZ());
        npc.getNavigation().stop();
        npc.setDeltaMovement(Vec3.ZERO);
        npc.teleportTo(to.getX() + 0.5, to.getY(), to.getZ() + 0.5);
        npc.setAirSupply(npc.getMaxAirSupply());
        npc.resetFallDistance();
    }

    private static void splash(ServerLevel sl, double x, double y, double z) {
        sl.sendParticles(ParticleTypes.SPLASH, x, y + 0.6, z, 24, 0.4, 0.3, 0.4, 0.1);
        sl.playSound(null, x, y, z, SoundEvents.GENERIC_SPLASH, SoundSource.NEUTRAL, 0.6F, 1.0F);
    }

    // =====================================================================================
    //  Invasões: a horda chega pelo mar
    // =====================================================================================
    /** Elenco das invasões numa cidade das águas: o de sempre e os afogados (duas vezes, para aparecerem bastante). */
    public static List<EntityType<? extends Mob>> raidPool(Settlement s, List<EntityType<? extends Mob>> pool) {
        if (!s.water) return pool;
        List<EntityType<? extends Mob>> out = new ArrayList<>(pool);
        out.add(EntityType.DROWNED);
        out.add(EntityType.DROWNED);
        return out;
    }

    /**
     * Onde um atacante nasce: na borda das plataformas — o piso firme mais perto do ponto pedido, vindo de fora para
     * dentro. null = não há piso carregado por ali (o chamador usa o ponto de sempre: o atacante chega nadando).
     */
    @Nullable
    public static BlockPos raidSpot(ServerLevel sl, Settlement s, int x, int z) {
        int hx = s.heart.getX(), hz = s.heart.getZ();
        int dx = x - hx, dz = z - hz, n = Math.max(1, Math.max(Math.abs(dx), Math.abs(dz)));
        for (int i = n; i > 6; i -= 4) {
            BlockPos f = WaterCity.firm(sl, s, hx + Math.round(dx * (float) i / n), hz + Math.round(dz * (float) i / n), 3);
            if (f != null) return f;
        }
        return null;
    }

    /** A chegada "pelo mar": respingos onde o atacante aparece. */
    public static void raidFx(ServerLevel sl, double x, double y, double z) {
        sl.sendParticles(ParticleTypes.SPLASH, x, y + 0.3, z, 30, 0.5, 0.4, 0.5, 0.2);
        sl.sendParticles(ParticleTypes.BUBBLE_POP, x, y + 0.6, z, 8, 0.4, 0.4, 0.4, 0.02);
        sl.playSound(null, x, y, z, SoundEvents.PLAYER_SPLASH, SoundSource.HOSTILE, 0.7F, 0.8F);
    }

    /**
     * Atacante que ficou boiando longe de tudo (não é afogado): de vez em quando sobe na plataforma mais perto, para o
     * ataque não ficar esperando por ele. Devolve true se foi trazido.
     */
    public static boolean raidRecover(ServerLevel sl, Settlement s, Mob m) {
        if (!s.water || m instanceof Drowned || !m.isInWater() || m.getTarget() != null && m.distanceToSqr(m.getTarget()) < 36) return false;
        if (sl.random.nextInt(6) != 0) return false;
        BlockPos spot = WaterCity.firm(sl, s, m.getBlockX(), m.getBlockZ(), 12);
        if (spot == null) spot = raidSpot(sl, s, m.getBlockX(), m.getBlockZ());
        if (spot == null) return false;
        m.getNavigation().stop();
        m.setDeltaMovement(Vec3.ZERO);
        m.teleportTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        raidFx(sl, spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5);
        return true;
    }

    private WaterLife() {}
}
