package com.renanpontes.isekaimahou.feature.cities;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.cities.entity.CityGuard;
import com.renanpontes.isekaimahou.feature.cities.entity.CityNpc;
import com.renanpontes.isekaimahou.feature.cities.network.CitiesNetwork;
import com.renanpontes.isekaimahou.feature.cities.world.CityKingdom;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Crimes na cidade (§5.2), POR REINO (v0.9):
 * <ul>
 *   <li>ferir um morador: −10 no reino da cidade e Procurado nesse reino por 5 min (no máx. 1 vez a cada 10 s por
 *       morador);</li>
 *   <li>matar um morador: Procurado 15 min (o núcleo já tira a reputação do abate — não tiramos de novo);</li>
 *   <li>mexer num baú/barril de prédio da cidade (marca {@code CityChest}), quebrar prédios ou furtar moradores à vista
 *       de um guarda/morador: abordagem da guarda ({@link CityConfront}, v1.0).</li>
 * </ul>
 * Guardas DO MESMO REINO por perto vêm atrás do criminoso; os de outro reino não ligam (o guarda de Sahra não liga para
 * crime cometido em Aldória). O efeito "Procurado" é um só (dura o maior prazo); quais reinos procuram o jogador fica
 * em {@code CityPlayer/Wanted: {<facção>: {Until: tick do mundo, Bail: moedas}}}. Sem essa lista (crime da v0.8) vale
 * para todos os reinos. Bichos e companheiros do jogador só contam se o jogador "mandou" (ele mesmo atacou a vítima nos
 * últimos 10 s).
 */
public final class CityCrimes {
    private static final int HURT_WANTED = 6000;
    private static final int KILL_WANTED = 18000;
    private static final int HURT_COOLDOWN = 200;
    private static final int CHEST_COOLDOWN = 2400;
    /** jogador+vítima/baú → último tick em que contou (evita punir cada golpe/clique). */
    private static final Map<String, Long> COOLDOWNS = new HashMap<>();

    public static void onDamage(LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof CityNpc victim) || !(victim.level() instanceof ServerLevel sl) || e.getNewDamage() <= 0) return;
        if (!victim.isAlive()) return;   // a morte é tratada em onDeath
        ServerPlayer p = culprit(e.getSource(), victim);
        if (p == null) return;
        if (!cooldown(p.getStringUUID() + "/" + victim.getId(), sl.getGameTime(), HURT_COOLDOWN)) {
            alertGuards(sl, victim, p);
            return;
        }
        Reputation.add(p, victim.kingdomFaction(), -10, Component.translatable("reason.isekai_mahou.cities.hurt"));
        makeWanted(p, victim.kingdomFaction(), HURT_WANTED, CityServices.HURT_BAIL);
        victim.say(CityLines.line("crime.help"));
        alertGuards(sl, victim, p);
    }

    /**
     * Durante um Cerco, as magias de área de quem está defendendo a cidade não ferem os moradores (eles se abaixam):
     * sem isso, uma bola de fogo na Horda que respingasse num guarda viraria crime no meio da batalha.
     * Quem já está Procurado/Hostil não tem essa proteção.
     */
    public static void onSpellDamage(SpellDamageEvent.Pre e) {
        if (!(e.target() instanceof CityNpc npc) || !(npc.level() instanceof ServerLevel sl)) return;
        if (!(e.ctx().casterPlayer() instanceof ServerPlayer p) || CityGuard.isOutlaw(p, npc.kingdomFaction())) return;
        if (CitySiege.underSiege(sl, npc.blockPosition())) e.setCanceled(true);
    }

    public static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (!(dead.level() instanceof ServerLevel sl)) return;
        if (dead instanceof CityNpc victim) {
            ServerPlayer p = culprit(e.getSource(), victim);
            if (p == null) return;
            makeWanted(p, victim.kingdomFaction(), KILL_WANTED, CityServices.KILL_BAIL);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.murder").withColor(0xD03A2C), false);
            alertGuards(sl, victim, p);
            return;
        }
        // Ajudar os guardas: matar o monstro com que um guarda está lutando.
        if (dead instanceof Enemy && e.getSource().getEntity() instanceof ServerPlayer p && !(p instanceof FakePlayer)) {
            for (CityGuard g : sl.getEntitiesOfClass(CityGuard.class, dead.getBoundingBox().inflate(16))) {
                if (g.getTarget() == dead || g.getLastHurtByMob() == dead) {
                    Reputation.addDaily(p, g.kingdomFaction(), 2, "cities.help_guard", 20,
                            Component.translatable("reason.isekai_mahou.cities.help_guard"));
                    g.say(CityLines.line("crime.thanks"));
                    break;
                }
            }
        }
    }

    /**
     * Baús e barris marcados como "da cidade" (NBT CityChest no template). v1.0: quem viu chama a guarda e o guarda
     * vem abordar o jogador ({@link CityConfront}); a reputação perdida fica lá.
     */
    public static void onInteract(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || p.isCreative() || p.isSpectator()) return;
        if (!(p.level() instanceof ServerLevel sl)) return;
        BlockPos pos = e.getPos();
        BlockEntity be = sl.getBlockEntity(pos);
        if (be == null || !be.getPersistentData().getBoolean("CityChest")) return;
        if (CityPlaces.cityAt(sl, pos) == null) return;
        CityConfront.onOpenChest(p, sl, pos);
    }

    /** Quem responde pelo golpe: o jogador, ou o dono do bicho/companheiro se ele mesmo atacou a vítima há pouco. */
    @Nullable
    static ServerPlayer culprit(DamageSource src, LivingEntity victim) {
        Entity e = src.getEntity();
        ServerPlayer p = null;
        if (e instanceof ServerPlayer sp) p = sp;
        else if (e instanceof OwnableEntity o && o.getOwner() instanceof ServerPlayer owner
                && owner.getLastHurtMob() == victim && owner.tickCount - owner.getLastHurtMobTimestamp() < 200) {
            p = owner;
        }
        if (p == null || p instanceof FakePlayer || p.isCreative() || p.isSpectator()) return null;
        return p;
    }

    // =====================================================================================
    //  Procurado por reino
    // =====================================================================================
    private static final String WANTED = "Wanted";

    private static long now(ServerPlayer p) {
        return p.server.overworld().getGameTime();
    }

    /**
     * Procurado neste reino por {@code ticks} (nunca encurta) e a fiança dele fica no maior valor devido. O efeito
     * "Procurado" dura o maior prazo entre os reinos.
     */
    public static void makeWanted(ServerPlayer p, String kingdomFaction, int ticks, int bail) {
        MobEffectInstance cur = p.getEffect(CitiesContent.WANTED);
        CompoundTag t = CityPlayer.tag(p);
        if (cur == null) {
            // "Procurado" antigo que já expirou (ou sumiu com a morte) não se acumula com o crime novo.
            t.remove(WANTED);
            t.remove("Bail");
        } else if (!t.contains(WANTED)) {
            // Crime da v0.8 (sem reino): vira dívida com Aldória.
            CompoundTag old = new CompoundTag();
            old.putLong("Until", now(p) + cur.getDuration());
            old.putInt("Bail", Math.max(CityServices.HURT_BAIL, t.getInt("Bail")));
            CompoundTag all = new CompoundTag();
            all.put("kingdom", old);
            t.put(WANTED, all);
        }
        boolean already = isWanted(p, kingdomFaction);
        CompoundTag all = t.getCompound(WANTED);
        CompoundTag e = all.getCompound(kingdomFaction);
        long until = now(p) + ticks;
        e.putLong("Until", Math.max(already ? e.getLong("Until") : 0L, until));
        e.putInt("Bail", Math.max(already ? e.getInt("Bail") : 0, bail));
        all.put(kingdomFaction, e);
        t.put(WANTED, all);
        t.remove("Bail");
        if (cur == null || cur.getDuration() < ticks) {
            p.addEffect(new MobEffectInstance(CitiesContent.WANTED, ticks, 0, false, false, true));
        }
        if (!already) {
            CityKingdom k = CityRealm.byFaction(kingdomFaction);
            Component where = k != null ? CityRealm.shortName(k) : Component.literal(kingdomFaction);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.cities.wanted", where).withColor(0xD03A2C), true);
            p.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.HOSTILE, 1.0F, 0.6F);
        }
        syncWanted(p);
    }

    /** Manda ao cliente quais reinos procuram o jogador (linha "Procurado em ..." e seta para o guarda certo). */
    public static void syncWanted(ServerPlayer p) {
        CitiesNetwork.send(p, new CitiesNetwork.Wanted(wantedIn(p)));
    }

    /**
     * O jogador é procurado pelos guardas deste reino? No cliente (sem os dados do servidor) vale o efeito. Crime da
     * v0.8 (efeito sem a lista de reinos) vale para todos.
     */
    public static boolean isWanted(Player p, String kingdomFaction) {
        if (!p.hasEffect(CitiesContent.WANTED)) return false;
        if (!(p instanceof ServerPlayer sp)) return true;
        CompoundTag t = CityPlayer.tag(sp);
        if (!t.contains(WANTED)) return true;
        CompoundTag all = t.getCompound(WANTED);
        if (all.isEmpty()) return true;
        if (!all.contains(kingdomFaction)) return false;
        return all.getCompound(kingdomFaction).getLong("Until") > now(sp);
    }

    /** Fiança devida a este reino. */
    public static int bail(ServerPlayer p, String kingdomFaction) {
        if (!isWanted(p, kingdomFaction)) return CityServices.HURT_BAIL;
        CompoundTag t = CityPlayer.tag(p);
        int b = t.contains(WANTED) && t.getCompound(WANTED).contains(kingdomFaction)
                ? t.getCompound(WANTED).getCompound(kingdomFaction).getInt("Bail") : t.getInt("Bail");
        return b > 0 ? b : CityServices.HURT_BAIL;
    }

    /** Reinos (facções) que procuram o jogador agora (lista vazia com o efeito = crime antigo, vale para todos). */
    public static List<String> wantedIn(ServerPlayer p) {
        List<String> out = new ArrayList<>();
        if (!p.hasEffect(CitiesContent.WANTED)) return out;
        CompoundTag all = CityPlayer.tag(p).getCompound(WANTED);
        long now = now(p);
        for (String k : all.getAllKeys()) {
            if (all.getCompound(k).getLong("Until") > now) out.add(k);
        }
        return out;
    }

    /** Fiança paga a este reino: ele esquece; se nenhum outro procura o jogador, o efeito some. */
    public static void clearWanted(ServerPlayer p, String kingdomFaction) {
        CompoundTag t = CityPlayer.tag(p);
        if (!t.contains(WANTED)) {
            clearAllWanted(p);
            return;
        }
        CompoundTag all = t.getCompound(WANTED);
        all.remove(kingdomFaction);
        if (all.isEmpty()) {
            clearAllWanted(p);
            return;
        }
        t.put(WANTED, all);
        refreshEffect(p);
        syncWanted(p);
    }

    /** Ficha limpa em todos os reinos (receptador, comando). */
    public static void clearAllWanted(ServerPlayer p) {
        p.removeEffect(CitiesContent.WANTED);
        CompoundTag t = CityPlayer.tag(p);
        t.remove(WANTED);
        t.remove("Bail");
        syncWanted(p);
    }

    /** O efeito passa a durar o maior prazo que sobrou (ou some). */
    private static void refreshEffect(ServerPlayer p) {
        long left = 0;
        CompoundTag all = CityPlayer.tag(p).getCompound(WANTED);
        long now = now(p);
        for (String k : all.getAllKeys()) left = Math.max(left, all.getCompound(k).getLong("Until") - now);
        if (left <= 20) {
            clearAllWanted(p);
            return;
        }
        p.removeEffect(CitiesContent.WANTED);
        p.addEffect(new MobEffectInstance(CitiesContent.WANTED, (int) Math.min(Integer.MAX_VALUE, left), 0, false, false, true));
    }

    /**
     * A cada 2 s: reinos cujo prazo acabou esquecem o jogador; sem nenhum, o efeito some. Efeito que sumiu (morte,
     * tempo) apaga a lista.
     */
    public static void tickWanted(ServerPlayer p) {
        CompoundTag t = CityPlayer.tag(p);
        if (!t.contains(WANTED)) return;
        if (!p.hasEffect(CitiesContent.WANTED)) {
            t.remove(WANTED);
            t.remove("Bail");
            syncWanted(p);
            return;
        }
        CompoundTag all = t.getCompound(WANTED);
        long now = now(p);
        List<String> expired = new ArrayList<>();
        for (String k : all.getAllKeys()) if (all.getCompound(k).getLong("Until") <= now) expired.add(k);
        if (expired.isEmpty()) return;
        for (String k : expired) all.remove(k);
        if (all.isEmpty()) {
            clearAllWanted(p);
        } else {
            t.put(WANTED, all);
            syncWanted(p);
        }
    }

    /** Guardas DO REINO da vítima a até 32 blocos vêm atrás do criminoso. */
    private static void alertGuards(ServerLevel sl, CityNpc victim, ServerPlayer p) {
        callGuards(sl, victim, p, victim.kingdomFaction());
    }

    /** Guardas deste reino a até 32 blocos de {@code center} vêm atrás do jogador (que já precisa ser fora da lei). */
    static void callGuards(ServerLevel sl, Entity center, ServerPlayer p, String faction) {
        for (CityGuard g : sl.getEntitiesOfClass(CityGuard.class, center.getBoundingBox().inflate(32))) {
            if (g != center && g.getTarget() != p && g.kingdomFaction().equals(faction)) g.setTarget(p);
        }
    }

    static boolean cooldown(String key, long now, int ticks) {
        Long last = COOLDOWNS.get(key);
        if (last != null && now - last < ticks && now >= last) return false;
        COOLDOWNS.put(key, now);
        if (COOLDOWNS.size() > 4096) COOLDOWNS.entrySet().removeIf(en -> now - en.getValue() > CHEST_COOLDOWN);
        return true;
    }

    public static void clear() {
        COOLDOWNS.clear();
        CityConfront.clear();
    }

    private CityCrimes() {}
}
