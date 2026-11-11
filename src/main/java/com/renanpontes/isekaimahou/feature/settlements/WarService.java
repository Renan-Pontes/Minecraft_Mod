package com.renanpontes.isekaimahou.feature.settlements;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Allegiance;
import com.renanpontes.isekaimahou.core.api.Places;
import com.renanpontes.isekaimahou.core.api.Reputation;
import com.renanpontes.isekaimahou.feature.settlements.entity.GuardNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.MilitiaNpc;
import com.renanpontes.isekaimahou.feature.settlements.entity.SettlementNpc;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Guerra entre cidades usando o {@link Allegiance} do núcleo (docs/v10/API_social.md §4):
 * <ul>
 *   <li><b>Declarar inimiga</b> exige AFINIDADE MÁXIMA do povo; a guerra vale para o grupo do Senhor e os cidadãos;</li>
 *   <li><b>Paz</b>: cidade com Senhor jogador precisa aceitar a proposta; cidade de reino sem Senhor aceita um tributo;</li>
 *   <li><b>Milícia</b>: guerreiros treinados com moedas do cofre (afinidade máxima; cidade própria precisa do Quartel);</li>
 *   <li>NPCs de QUALQUER cidade inimiga (também as de reino, do módulo cities) lutam ou fogem e não negociam;</li>
 *   <li>Guardas defendem o Senhor, o grupo dele e os cidadãos; toque de recolher pelo decreto dos portões.</li>
 * </ul>
 */
public final class WarService {
    public static final int MILITIA_COST = 25;
    public static final int PEACE_TRIBUTE = 100;
    private static final int CURFEW_GRACE = 60;
    private static final Set<String> CITY_NPCS = Set.of("city_guard", "citizen", "city_merchant", "city_lord");

    /** Segundos de cada jogador desobedecendo o toque de recolher (por cidade). */
    private static final Map<UUID, Curfew> CURFEW = new HashMap<>();
    /** Último aviso de "território inimigo" por jogador (tempo do jogo). */
    private static final Map<UUID, Long> WARNED = new HashMap<>();

    private record Curfew(String city, int seconds) {}

    // =====================================================================================
    //  Declarar, paz, milícia
    // =====================================================================================
    public static boolean canDeclare(Settlement s) {
        return s.affinity >= Settlement.MAX_AFFINITY;
    }

    public static void declare(ServerPlayer sp, Settlement s, String target) {
        MinecraftServer server = sp.server;
        if (target.equals(s.id) || Allegiance.areEnemies(server, s.id, target)) return;
        if (!canDeclare(s)) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.war.need_affinity").withColor(0xE8735E), true);
            return;
        }
        if (findPlace(server, target) == null) return;
        if (Allegiance.declareEnemy(server, s.id, target, sp)) {
            s.affinity = Math.max(0, s.affinity - 10);
            s.peaceOffers.remove(target);
            Settlement other = SettlementData.get(server).city(target);
            if (other != null) other.peaceOffers.remove(s.id);
        }
    }

    public static void proposePeace(ServerPlayer sp, Settlement s, String target) {
        MinecraftServer server = sp.server;
        if (!Allegiance.areEnemies(server, s.id, target)) return;
        Settlement other = SettlementData.get(server).city(target);
        if (other == null || other.owner == null) {
            // cidade de reino sem Senhor: aceita um tributo
            if (s.treasury < PEACE_TRIBUTE) {
                sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.treasury.poor", PEACE_TRIBUTE).withColor(0xE8735E), true);
                return;
            }
            s.treasury -= PEACE_TRIBUTE;
            Allegiance.makePeace(server, s.id, target);
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.war.peace_tribute", Allegiance.cityName(server, target),
                    SettlementService.coins(PEACE_TRIBUTE)).withColor(0x7DDA6B), false);
            return;
        }
        if (s.peaceOffers.contains(target)) {
            acceptPeace(sp, s, target);
            return;
        }
        other.peaceOffers.add(s.id);
        sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.war.peace_sent", SettlementService.cityName(server, other))
                .withColor(0x8FC8FF), true);
        ServerPlayer lord = server.getPlayerList().getPlayer(other.owner);
        if (lord != null) {
            lord.displayClientMessage(Component.translatable("settlements.isekai_mahou.war.peace_offer", SettlementService.cityName(server, s))
                    .withColor(0x8FC8FF), false);
            lord.playNotifySound(SoundEvents.BELL_BLOCK, SoundSource.PLAYERS, 1.0F, 1.5F);
        }
    }

    public static void acceptPeace(ServerPlayer sp, Settlement s, String from) {
        if (!s.peaceOffers.remove(from)) return;
        Allegiance.makePeace(sp.server, s.id, from);
    }

    public static boolean canTrain(Settlement s) {
        return s.affinity >= Settlement.MAX_AFFINITY && s.population(Settlement.Role.MILITIA) < s.cap(Settlement.Role.MILITIA);
    }

    public static void trainMilitia(ServerPlayer sp, Settlement s) {
        if (s.affinity < Settlement.MAX_AFFINITY) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.war.need_affinity").withColor(0xE8735E), true);
            return;
        }
        if (s.cap(Settlement.Role.MILITIA) <= 0) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.militia.need_barracks").withColor(0xE8735E), true);
            return;
        }
        if (s.population(Settlement.Role.MILITIA) >= s.cap(Settlement.Role.MILITIA)) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.militia.full").withColor(0xE8735E), true);
            return;
        }
        if (s.treasury < MILITIA_COST) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.treasury.poor", MILITIA_COST).withColor(0xE8735E), true);
            return;
        }
        ServerLevel sl = SettlementService.level(sp.server, s);
        if (sl == null || !sl.hasChunk(s.heart.getX() >> 4, s.heart.getZ() >> 4)) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.militia.far").withColor(0xE8735E), true);
            return;
        }
        s.treasury -= MILITIA_COST;
        SettlementNpc npc = SettlementService.spawnNpc(sl, s, Settlement.Role.MILITIA,
                s.heart.offset(sl.random.nextInt(5) - 2, 0, sl.random.nextInt(5) - 2), s.heart);
        if (npc != null) {
            npc.triggerAnim("action", "cheer");
            sl.playSound(null, npc.blockPosition(), SoundEvents.ARMOR_EQUIP_IRON.value(), SoundSource.NEUTRAL, 1.2F, 0.9F);
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.militia.trained", npc.getDisplayName()).withColor(s.style.color), true);
        }
    }

    /** Cidades que podem ser alvo (outras cidades/capitais do Overworld e cidades de jogadores), da mais perto. */
    public static List<Places.Place> targets(MinecraftServer server, Settlement s) {
        ServerLevel sl = SettlementService.level(server, s);
        if (sl == null) return List.of();
        return Places.all(sl).stream()
                .filter(p -> SettlementService.isCity(p) && !p.id().equals(s.id) && p.dim() == sl.dimension())
                .sorted((a, b) -> Double.compare(a.center().distSqr(s.heart), b.center().distSqr(s.heart)))
                .limit(24).toList();
    }

    @Nullable
    private static Places.Place findPlace(MinecraftServer server, String id) {
        return Places.get(server.overworld(), id);
    }

    // =====================================================================================
    //  Território inimigo: NPCs lutam ou fogem; toque de recolher
    // =====================================================================================
    public static void tickPlayers(MinecraftServer server) {
        SettlementData data = SettlementData.get(server);
        for (ServerPlayer sp : server.getPlayerList().getPlayers()) {
            if (sp.isCreative() || sp.isSpectator()) continue;
            ServerLevel sl = sp.serverLevel();
            Places.Place p = SettlementService.cityAt(sl, sp.blockPosition());
            if (p == null) {
                CURFEW.remove(sp.getUUID());
                continue;
            }
            if (Allegiance.isEnemyOf(sp, p.id())) {
                enemyInCity(sl, sp, p);
                CURFEW.remove(sp.getUUID());
                continue;
            }
            Settlement s = data.city(p.id());
            if (s != null) curfew(sl, s, sp);
            else CURFEW.remove(sp.getUUID());
        }
    }

    private static void enemyInCity(ServerLevel sl, ServerPlayer sp, Places.Place p) {
        long now = sl.getGameTime();
        Long last = WARNED.get(sp.getUUID());
        if (last == null || now - last > 200) {
            WARNED.put(sp.getUUID(), now);
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.war.territory", p.name()).withColor(0xE8735E), true);
        }
        // NPCs da cidade (cities: guarda, cidadão, mercador, lorde; aldeões e golens do lugar) — os nossos têm IA própria
        int fighters = 0;
        for (PathfinderMob mob : sl.getEntitiesOfClass(PathfinderMob.class, sp.getBoundingBox().inflate(20.0, 8.0, 20.0),
                m -> m.isAlive() && !(m instanceof SettlementNpc) && belongsToCity(m, p))) {
            boolean fights = mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                    && mob.getAttributeValue(Attributes.ATTACK_DAMAGE) > 0 && !(mob instanceof AbstractVillager);
            if (fights) {
                if (fighters++ < 6 && (mob.getTarget() == null || !(mob.getTarget() instanceof Player))) mob.setTarget(sp);
            } else if (mob.distanceToSqr(sp) < 14 * 14 && mob.getNavigation().isDone()) {
                Vec3 away = DefaultRandomPos.getPosAway(mob, 16, 6, sp.position());
                if (away != null) mob.getNavigation().moveTo(away.x, away.y, away.z, 1.3);
            }
        }
    }

    private static boolean belongsToCity(Entity e, Places.Place p) {
        if (!p.contains(e.blockPosition())) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(e.getType());
        if (IsekaiMahou.MODID.equals(id.getNamespace()) && CITY_NPCS.contains(id.getPath())) return true;
        if (e.getType() == EntityType.VILLAGER || e.getType() == EntityType.IRON_GOLEM) return true;
        String f = Reputation.factionOf(e);
        return f != null && !f.isEmpty() && f.equals(p.faction());
    }

    /** Toque de recolher: de noite (ou sempre, com os portões fechados) quem não é da cidade tem 60 s para sair. */
    private static void curfew(ServerLevel sl, Settlement s, ServerPlayer sp) {
        boolean active = s.gates == Settlement.GATES_CLOSED || s.gates == Settlement.GATES_NIGHT && sl.isNight();
        if (!active || SettlementService.isProtected(sl.getServer(), s, sp) || Allegiance.of(sp) != null && Allegiance.of(sp).equals(s.id)) {
            CURFEW.remove(sp.getUUID());
            return;
        }
        Curfew c = CURFEW.get(sp.getUUID());
        int secs = c != null && c.city().equals(s.id) ? c.seconds() + 1 : 1;
        CURFEW.put(sp.getUUID(), new Curfew(s.id, secs));
        if (secs == 1 || secs % 15 == 0 && secs < CURFEW_GRACE) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.curfew.warn", SettlementService.cityName(sl.getServer(), s),
                    CURFEW_GRACE - secs).withColor(0xFFB060), true);
        }
        if (secs == CURFEW_GRACE) {
            sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.curfew.arrest").withColor(0xE8735E), true);
            sp.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
        }
    }

    /** O jogador desobedece o toque de recolher (os guardas da cidade vão atrás). */
    public static boolean isTrespasser(ServerLevel sl, Settlement s, ServerPlayer sp) {
        Curfew c = CURFEW.get(sp.getUUID());
        return c != null && c.city().equals(s.id) && c.seconds() >= CURFEW_GRACE;
    }

    // =====================================================================================
    //  Guardas defendem quem a cidade protege
    // =====================================================================================
    public static void onDamage(LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer victim) || !(e.getSource().getEntity() instanceof LivingEntity attacker)) return;
        if (attacker == victim) return;
        ServerLevel sl = victim.serverLevel();
        Places.Place p = SettlementService.cityAt(sl, victim.blockPosition());
        if (p == null) return;
        Settlement s = SettlementData.get(sl.getServer()).city(p.id());
        if (s == null || !SettlementService.isProtected(sl.getServer(), s, victim)) return;
        if (attacker instanceof Player ap && SettlementService.isProtected(sl.getServer(), s, ap)) return;
        if (attacker instanceof SettlementNpc n && n.cityId().equals(s.id)) return;
        AABB box = victim.getBoundingBox().inflate(32.0, 12.0, 32.0);
        int n = 0;
        for (Mob guard : sl.getEntitiesOfClass(Mob.class, box, m -> m.isAlive() && isDefender(m, s, p))) {
            if (guard.getTarget() == null || !guard.getTarget().isAlive()) {
                guard.setTarget(attacker);
                if (n++ >= 6) break;
            }
        }
    }

    private static boolean isDefender(Mob m, Settlement s, Places.Place p) {
        if (m instanceof GuardNpc g) return g.cityId().equals(s.id);
        if (m instanceof MilitiaNpc mi) return mi.cityId().equals(s.id);
        if (s.own()) return false;
        ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(m.getType());
        boolean cityGuard = IsekaiMahou.MODID.equals(id.getNamespace()) && id.getPath().equals("city_guard");
        return (cityGuard || m.getType() == EntityType.IRON_GOLEM) && p.contains(m.blockPosition());
    }

    /** Monstros abatidos dentro de uma cidade governada contam para a afinidade (defesa). */
    public static void onDeath(LivingDeathEvent e) {
        if (!(e.getSource().getEntity() instanceof ServerPlayer sp) || !(e.getEntity() instanceof net.minecraft.world.entity.monster.Enemy)) return;
        Places.Place p = SettlementService.cityAt(sp.serverLevel(), e.getEntity().blockPosition());
        if (p == null) return;
        Settlement s = SettlementData.get(sp.server).city(p.id());
        if (s != null && SettlementService.isProtected(sp.server, s, sp)) s.killsToday++;
    }

    // =====================================================================================
    //  Ninguém da cidade inimiga negocia
    // =====================================================================================
    public static void onInteract(PlayerInteractEvent.EntityInteract e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.isCreative() || e.getTarget() instanceof SettlementNpc) return;
        Entity t = e.getTarget();
        if (!(t instanceof LivingEntity)) return;
        Places.Place p = SettlementService.cityAt(sp.serverLevel(), t.blockPosition());
        if (p == null || !belongsToCity(t, p) || !Allegiance.isEnemyOf(sp, p.id())) return;
        e.setCanceled(true);
        e.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
        sp.displayClientMessage(Component.translatable("settlements.isekai_mahou.war.refuse", p.name()).withColor(0xE8735E), true);
        sp.serverLevel().sendParticles(ParticleTypes.ANGRY_VILLAGER, t.getX(), t.getY() + t.getBbHeight() + 0.3, t.getZ(), 3, 0.3, 0.2, 0.3, 0);
        sp.playNotifySound(SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
    }

    public static void forget(UUID player) {
        CURFEW.remove(player);
        WARNED.remove(player);
    }

    public static void clear() {
        CURFEW.clear();
        WARNED.clear();
    }

    private WarService() {}
}
