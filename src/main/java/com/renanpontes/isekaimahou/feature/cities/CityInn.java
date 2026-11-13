package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.feature.cities.entity.CityMerchant;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;
import com.renanpontes.isekaimahou.feature.cities.network.CitiesNetwork;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.Vec3;

/**
 * Estalagem (release 1.0 — "estalagem pra alugar"). Serviços do estalajadeiro:
 * <ul>
 *   <li><b>Descansar</b> ({@code inn_rest}): paga, dorme na hora — vida e mana cheias, tira a exaustão e dá
 *       "Bem Descansado". De noite, se ninguém mais estiver acordado no mundo, a noite passa. De graça para hóspedes.</li>
 *   <li><b>Alugar quarto</b> por 3 ou 7 dias ({@code inn_rent3}/{@code inn_rent7}; alugar de novo estende): o quarto
 *       vira a "casa" do jogador — ponto de renascimento (o Modo Paz já trata o ponto de renascimento como casa).
 *       Quando o aluguel vence, o ponto de renascimento de antes volta. {@code inn_checkout} encerra antes.</li>
 *   <li><b>Rumores pagos</b> ({@code inn_tip_boss}/{@code inn_tip_place}): marca no rastreador o covil de um chefe ou
 *       uma masmorra/torre/portal por perto (só cobra se achar).</li>
 * </ul>
 * Chaves no jogador ({@link CityPlayer#tag}): {@code RoomBed}, {@code RoomDim}, {@code RoomDay} (da v0.8) e as novas,
 * opcionais, {@code RoomUntil}, {@code RoomCity}, {@code RoomForced}, {@code RoomWarned}, {@code RoomPrev}.
 */
public final class CityInn {
    static final int REST_COST = 5, RENT3_COST = 14, RENT7_COST = 28, TIP_BOSS_COST = 8, TIP_PLACE_COST = 5;
    /** Não dá para pagar mais do que isto adiantado (dias de jogo). */
    static final int MAX_DAYS_AHEAD = 14;
    /** Duração do "Bem Descansado" (10 min). */
    static final int RESTED_TICKS = 12000;
    private static final int TIP_COOLDOWN = 200;

    /** Covis de chefe (ids das estruturas) para o rumor pago. */
    private static final Set<String> BOSS_LAIRS = Set.of("hydra_lair", "false_hydra_lair", "manticore_den", "basilisk_tomb",
            "minotaur_labyrinth", "wyvern_peak", "colossus_ruins", "renegade_tower", "troll_bridge", "goblin_fortress",
            "profane_altar", "steelball_saint_tomb", "nether_citadel", "end_library");
    /** Masmorras, torres e passagens para outras dimensões. */
    private static final Set<String> DUNGEONS = Set.of("depths_dungeon", "forgotten_crypt", "mage_tower", "arcane_ruins",
            "mimic_vault", "dwarf_hold", "tower_frost", "tower_sand", "tower_stone", "ascension_sanctum", "elemental_gate",
            "abyssal_chasm", "celestial_stairway", "twilight_rupture", "fey_ring", "garganta_site", "kobold_den",
            "witch_hut_ancient");

    // =====================================================================================
    //  Lista e execução
    // =====================================================================================
    static void services(ServerPlayer p, CityMerchant inn, List<CitiesNetwork.Service> out) {
        boolean guest = isGuestHere(p, inn);
        int left = daysLeft(p);
        com.renanpontes.isekaimahou.feature.ui_hud.Hints.show(p, "cities.inn");   // dica de primeira vez na estalagem
        out.add(CityServices.service("inn_rest", guest ? 0 : CityServices.price(p, inn, REST_COST), true));
        boolean room = !guest || left + 3 <= MAX_DAYS_AHEAD;
        out.add(CityServices.service("inn_rent3", CityServices.price(p, inn, RENT3_COST), room));
        out.add(CityServices.service("inn_rent7", CityServices.price(p, inn, RENT7_COST), !guest || left + 7 <= MAX_DAYS_AHEAD));
        out.add(CityServices.service("inn_tip_boss", CityServices.price(p, inn, TIP_BOSS_COST), true));
        out.add(CityServices.service("inn_tip_place", CityServices.price(p, inn, TIP_PLACE_COST), true));
        if (guest) out.add(CityServices.service("inn_checkout", 0, true));
    }

    static Component perform(ServerPlayer p, CityMerchant inn, CitiesNetwork.Service s) {
        return switch (s.id()) {
            case "inn_rest" -> rest(p, inn, s.cost());
            case "inn_rent3" -> rent(p, inn, 3, s.cost());
            case "inn_rent7" -> rent(p, inn, 7, s.cost());
            case "inn_tip_boss" -> tip(p, inn, true, s.cost());
            case "inn_tip_place" -> tip(p, inn, false, s.cost());
            case "inn_checkout" -> {
                expire(p, true);
                yield CityLines.line("service.inn_checkout.done");
            }
            default -> CityLines.line("service.unavailable");
        };
    }

    /** Cobra; devolve a fala de "faltam moedas" (e toca o som) se não der. */
    @Nullable
    private static Component charge(ServerPlayer p, CityNpc npc, int cost) {
        if (CityPlayer.pay(p, cost)) return null;
        p.level().playSound(null, npc.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
        return CityLines.line("service.no_coins", cost);
    }

    // =====================================================================================
    //  Descansar
    // =====================================================================================
    private static Component rest(ServerPlayer p, CityMerchant inn, int cost) {
        Component broke = charge(p, inn, cost);
        if (broke != null) return broke;
        ServerLevel sl = p.serverLevel();
        boolean skipped = skipNight(sl, p);
        applyRest(p);
        // Piscar de olhos: a noite (ou a soneca) passou.
        p.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 30, 0, false, false, false));
        sl.playSound(null, p.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.8F);
        Reputation.addDaily(p, inn.kingdomFaction(), 1, "cities.service", 20, Component.translatable("reason.isekai_mahou.cities.service"));
        return CityLines.line(skipped ? "service.inn_rest.done_night" : "service.inn_rest.done");
    }

    /** O descanso em si: vida, fome, mana, fim da exaustão e "Bem Descansado". Também usado ao acordar no quarto. */
    public static void applyRest(ServerPlayer p) {
        p.setHealth(p.getMaxHealth());
        p.getFoodData().eat(6, 0.6F);
        p.getFoodData().setExhaustion(0.0F);
        MagicData d = MagicApi.get(p);
        d.fillMana();
        d.markDirty();
        List<Holder<MobEffect>> tired = List.of(CoreRegistries.MAGIC_EXHAUSTION, MobEffects.WEAKNESS, MobEffects.MOVEMENT_SLOWDOWN,
                MobEffects.DIG_SLOWDOWN, MobEffects.HUNGER);
        for (Holder<MobEffect> h : tired) p.removeEffect(h);
        // Quem dorme na estalagem não é caçado por phantoms.
        p.resetStat(Stats.CUSTOM.get(Stats.TIME_SINCE_REST));
        p.addEffect(new MobEffectInstance(CitiesContent.WELL_RESTED, RESTED_TICKS, 0, false, false, true));
        p.serverLevel().sendParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), 5, 0.4, 0.3, 0.4, 0.0);
    }

    /**
     * De noite (ou em tempestade), se todos os outros jogadores do mundo já estão dormindo (ou não há outros), a noite
     * passa — como dormir numa cama. Num servidor com gente acordada o tempo não muda (o descanso vale mesmo assim).
     */
    private static boolean skipNight(ServerLevel sl, ServerPlayer p) {
        if (sl.dimensionType().hasFixedTime() || !(sl.isNight() || sl.isThundering())) return false;
        if (!sl.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) return false;
        for (ServerPlayer o : sl.players()) {
            if (o != p && !o.isSpectator() && !o.isSleeping()) return false;
        }
        passNight(sl);
        return true;
    }

    private static void passNight(ServerLevel sl) {
        long next = sl.getDayTime() + 24000L;
        sl.setDayTime(net.neoforged.neoforge.event.EventHooks.onSleepFinished(sl, next - next % 24000L, sl.getDayTime()));
        for (ServerPlayer o : new ArrayList<>(sl.players())) {
            if (o.isSleeping()) o.stopSleepInBed(false, true);
        }
    }

    // =====================================================================================
    //  Quarto alugado
    // =====================================================================================
    private static long day(ServerPlayer p) {
        return p.server.overworld().getDayTime() / 24000L;
    }

    /** Dia (exclusivo) em que o aluguel vence; 0 = sem quarto. Aluguel da v0.8 (sem prazo) vale por uma noite. */
    private static long until(CompoundTag t) {
        if (!t.contains("RoomBed")) return 0;
        return t.contains("RoomUntil") ? t.getLong("RoomUntil") : t.getLong("RoomDay") + 2;
    }

    public static boolean isRenting(ServerPlayer p) {
        return until(CityPlayer.tag(p)) > day(p);
    }

    /** Dias de aluguel que ainda faltam (0 = sem quarto). */
    public static int daysLeft(ServerPlayer p) {
        return (int) Math.max(0, until(CityPlayer.tag(p)) - day(p));
    }

    /** O jogador é hóspede da cidade deste estalajadeiro? */
    private static boolean isGuestHere(ServerPlayer p, CityNpc inn) {
        if (!isRenting(p)) return false;
        CompoundTag t = CityPlayer.tag(p);
        if (!p.level().dimension().location().toString().equals(t.getString("RoomDim"))) return false;
        Places.Place here = CityPlaces.cityAt(p.serverLevel(), inn.blockPosition());
        String city = t.getString("RoomCity");
        if (here != null && !city.isEmpty()) return here.id().equals(city);
        return BlockPos.of(t.getLong("RoomBed")).closerThan(inn.blockPosition(), 48.0);
    }

    private static Component rent(ServerPlayer p, CityMerchant inn, int days, int cost) {
        ServerLevel sl = p.serverLevel();
        CompoundTag t = CityPlayer.tag(p);
        // Aluguel antigo já vencido (a checagem de 2 s ainda não passou): encerra antes, para o "ponto de antes" ser o certo.
        if (t.contains("RoomBed") && !isRenting(p)) expire(p, true);
        boolean extend = isGuestHere(p, inn);
        BlockPos anchor = inn.home() != null ? inn.home() : inn.blockPosition();
        BlockPos bed = extend ? BlockPos.of(t.getLong("RoomBed")) : findBed(sl, anchor, 24);
        boolean forced = extend && t.getBoolean("RoomForced");
        if (!extend && bed == null) {
            // Estalagem sem cama livre: um canto perto do balcão serve de quarto (renasce ali).
            bed = findCorner(sl, inn.blockPosition());
            forced = true;
            if (bed == null) return CityLines.line("service.room.none");
        }
        Component broke = charge(p, inn, cost);
        if (broke != null) return broke;
        long today = day(p);
        long until = (extend ? until(t) : today) + days;
        if (!extend) {
            if (!isRenting(p)) savePrevious(p, t);   // trocando de estalagem: o "antes" continua sendo o original
            t.putLong("RoomBed", bed.asLong());
            t.putString("RoomDim", sl.dimension().location().toString());
            t.putLong("RoomDay", today);
            t.putBoolean("RoomForced", forced);
            Places.Place city = CityPlaces.cityAt(sl, inn.blockPosition());
            t.putString("RoomCity", city != null ? city.id() : "");
            p.setRespawnPosition(sl.dimension(), bed, p.getYRot(), forced, false);
            Telegraph.show(sl, Telegraph.Shape.circle(Vec3.atBottomCenterOf(bed), 1.4F).warmup(100).color(Telegraph.SAFE).noFlash());
            sl.sendParticles(ParticleTypes.HEART, bed.getX() + 0.5, bed.getY() + 1.0, bed.getZ() + 0.5, 6, 0.4, 0.3, 0.4, 0.0);
        }
        t.putLong("RoomUntil", until);
        t.remove("RoomWarned");
        sl.playSound(null, p.blockPosition(), SoundEvents.WOOL_PLACE, SoundSource.PLAYERS, 1.0F, 0.9F);
        Reputation.addDaily(p, inn.kingdomFaction(), 1, "cities.service", 20, Component.translatable("reason.isekai_mahou.cities.service"));
        inn.playAction("bow");
        int left = (int) (until - today);
        return extend ? CityLines.line("service.inn_rent.extended", left)
                : CityLines.line("service.inn_rent.done", bed.getX(), bed.getY(), bed.getZ(), left);
    }

    /** Guarda o ponto de renascimento de antes do aluguel (volta quando o aluguel vence). */
    private static void savePrevious(ServerPlayer p, CompoundTag t) {
        CompoundTag prev = new CompoundTag();
        BlockPos pos = p.getRespawnPosition();
        if (pos != null) {
            prev.putLong("Pos", pos.asLong());
            prev.putString("Dim", p.getRespawnDimension().location().toString());
            prev.putFloat("Angle", p.getRespawnAngle());
            prev.putBoolean("Forced", p.isRespawnForced());
        }
        t.put("RoomPrev", prev);
    }

    /**
     * Fim do aluguel (vencido ou encerrado no balcão): se o ponto de renascimento ainda é o quarto, volta o de antes
     * (ou nenhum — o jogador renasce na cidade inicial). Se ele já dormiu em outra cama, não mexe.
     */
    public static void expire(ServerPlayer p, boolean quiet) {
        CompoundTag t = CityPlayer.tag(p);
        if (!t.contains("RoomBed")) return;
        BlockPos bed = BlockPos.of(t.getLong("RoomBed"));
        String dim = t.getString("RoomDim");
        Component cityName = cityName(p.server, dim, t.getString("RoomCity"));
        boolean stillHome = bed.equals(p.getRespawnPosition()) && p.getRespawnDimension().location().toString().equals(dim);
        if (stillHome) {
            CompoundTag prev = t.getCompound("RoomPrev");
            ResourceLocation prevDim = prev.contains("Pos") ? ResourceLocation.tryParse(prev.getString("Dim")) : null;
            if (prevDim != null) {
                p.setRespawnPosition(ResourceKey.create(Registries.DIMENSION, prevDim), BlockPos.of(prev.getLong("Pos")),
                        prev.getFloat("Angle"), prev.getBoolean("Forced"), false);
            } else {
                p.setRespawnPosition(Level.OVERWORLD, null, 0.0F, false, false);
            }
        }
        for (String k : new String[] {"RoomBed", "RoomDim", "RoomDay", "RoomUntil", "RoomCity", "RoomForced", "RoomWarned", "RoomPrev"}) {
            t.remove(k);
        }
        if (!quiet) p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.inn.expired", cityName), false);
    }

    private static Component cityName(MinecraftServer server, String dim, String cityId) {
        ResourceLocation id = ResourceLocation.tryParse(dim);
        ServerLevel sl = id == null ? null : server.getLevel(ResourceKey.create(Registries.DIMENSION, id));
        Places.Place place = sl == null || cityId.isEmpty() ? null : Places.get(sl, cityId);
        return place != null ? place.name() : Component.translatable("message.isekai_mahou.cities.inn.the_inn");
    }

    @Nullable
    private static BlockPos findBed(ServerLevel sl, BlockPos center, int r) {
        BlockPos best = null;
        double bestD = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-r, -6, -r), center.offset(r, 10, r))) {
            BlockState st = sl.getBlockState(pos);
            if (!(st.getBlock() instanceof BedBlock) || st.getValue(BedBlock.PART) != BedPart.HEAD) continue;
            if (st.getValue(BedBlock.OCCUPIED)) continue;
            double d = pos.distSqr(center);
            if (d < bestD) {
                bestD = d;
                best = pos.immutable();
            }
        }
        return best;
    }

    /** Um canto livre a poucos passos do estalajadeiro. */
    @Nullable
    private static BlockPos findCorner(ServerLevel sl, BlockPos center) {
        for (int r = 1; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    for (int dy = 1; dy >= -1; dy--) {
                        BlockPos feet = center.offset(dx, dy, dz);
                        if (CityStart.standable(sl, feet)) return feet;
                    }
                }
            }
        }
        return null;
    }

    /**
     * A cada 2 s: aluguel vencido → encerra; véspera do vencimento → um aviso curto; e quem dorme na cama do quarto faz
     * a noite passar mesmo com outros jogadores acordados (no mundo solo o jogo já faz isso sozinho).
     */
    public static void tickRooms(MinecraftServer server) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            CompoundTag t = CityPlayer.tag(p);
            if (!t.contains("RoomBed")) continue;
            long today = day(p);
            long until = until(t);
            if (today >= until) {
                expire(p, false);
                continue;
            }
            if (until - today == 1 && t.getLong("RoomWarned") != until && server.overworld().getDayTime() % 24000L < 3000L) {
                t.putLong("RoomWarned", until);
                p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.inn.last_day",
                        cityName(server, t.getString("RoomDim"), t.getString("RoomCity"))), true);
            }
            if (!p.isSleepingLongEnough()) continue;
            ServerLevel sl = p.serverLevel();
            if (!sl.dimension().location().toString().equals(t.getString("RoomDim"))) continue;
            BlockPos bed = BlockPos.of(t.getLong("RoomBed"));
            if (p.getSleepingPos().map(sp -> !sp.closerThan(bed, 2.5)).orElse(true)) continue;
            Component msg = Component.translatable("message.isekai_mahou.cities.room_night", p.getDisplayName());
            for (ServerPlayer o : sl.players()) {
                if (!o.isSleeping()) o.displayClientMessage(msg, true);
            }
            if (sl.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT)) passNight(sl);
            else p.stopSleepInBed(false, true);
        }
    }

    /** Acordou na cama do quarto alugado: Bem Descansado (toda noite, enquanto durar o aluguel). */
    public static void onWake(ServerPlayer p) {
        CompoundTag t = CityPlayer.tag(p);
        if (!t.contains("RoomBed") || !isRenting(p)) return;
        if (!p.level().dimension().location().toString().equals(t.getString("RoomDim"))) return;
        if (!p.blockPosition().closerThan(BlockPos.of(t.getLong("RoomBed")), 3.0)) return;
        applyRest(p);
        p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.rested"), true);
        p.level().playSound(null, p.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.5F, 1.4F);
    }

    // =====================================================================================
    //  Rumores pagos
    // =====================================================================================
    private static Component tip(ServerPlayer p, CityMerchant inn, boolean boss, int cost) {
        ServerLevel sl = p.serverLevel();
        CompoundTag t = CityPlayer.tag(p);
        long now = sl.getGameTime();
        if (t.contains("TipAt") && now >= t.getLong("TipAt") && now - t.getLong("TipAt") < TIP_COOLDOWN) {
            return CityLines.line("service.inn_tip.wait");
        }
        if (!p.getAbilities().instabuild && CityPlayer.coins(p) < cost) {
            sl.playSound(null, inn.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 0.8F, 1.0F);
            return CityLines.line("service.no_coins", cost);
        }
        t.putLong("TipAt", now);
        Set<String> set = boss ? BOSS_LAIRS : DUNGEONS;
        CityRumors.Found found = CityRumors.search(sl, inn.blockPosition(), inn.getRandom(), set::contains, 5);
        if (found == null) return CityLines.line("service.inn_tip.none");   // não achou: não cobra
        CityPlayer.pay(p, cost);
        CityRumors.mark(p, found);
        sl.playSound(null, p.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 0.8F, 0.9F);
        inn.playAction("wave");
        int dist = (int) Math.sqrt(inn.blockPosition().distSqr(found.pos()));
        return CityLines.line(boss ? "service.inn_tip_boss.done" : "service.inn_tip_place.done", CityRumors.rumorName(found.key()),
                dist, CityRumors.direction(inn.blockPosition(), found.pos()));
    }

    // =====================================================================================
    //  Comandos de teste
    // =====================================================================================
    /** Aluga (ou estende) o quarto na cama mais perto do jogador, sem estalajadeiro nem moedas. */
    public static boolean debugRent(ServerPlayer p, int days) {
        ServerLevel sl = p.serverLevel();
        CompoundTag t = CityPlayer.tag(p);
        long today = day(p);
        if (isRenting(p)) {
            t.putLong("RoomUntil", until(t) + days);
            return true;
        }
        BlockPos bed = findBed(sl, p.blockPosition(), 16);
        boolean forced = bed == null;
        if (bed == null) bed = p.blockPosition();
        savePrevious(p, t);
        t.putLong("RoomBed", bed.asLong());
        t.putString("RoomDim", sl.dimension().location().toString());
        t.putLong("RoomDay", today);
        t.putLong("RoomUntil", today + days);
        t.putBoolean("RoomForced", forced);
        Places.Place city = CityPlaces.cityAt(sl, p.blockPosition());
        t.putString("RoomCity", city != null ? city.id() : "");
        p.setRespawnPosition(sl.dimension(), bed, p.getYRot(), forced, false);
        return true;
    }

    /** Onde fica o quarto (para o comando de estado); vazio = sem quarto. */
    public static Optional<BlockPos> room(ServerPlayer p) {
        CompoundTag t = CityPlayer.tag(p);
        return t.contains("RoomBed") && isRenting(p) ? Optional.of(BlockPos.of(t.getLong("RoomBed"))) : Optional.empty();
    }

    private CityInn() {}
}
