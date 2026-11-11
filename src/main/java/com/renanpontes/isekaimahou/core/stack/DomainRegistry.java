package com.renanpontes.isekaimahou.core.stack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Implementação de {@link Domains}: registro de leis (permanente) + domínios abertos (por servidor) + regra da barreira. */
public final class DomainRegistry {
    /** Domínio que não recebe {@code ticked} há tanto tempo (domo sumiu sem avisar) é fechado pelo núcleo. */
    private static final int STALE_TICKS = 100;
    /** A lista de inimigos de cada domínio é refeita a cada tantos ticks (o domo pode ter 100+ blocos de raio). */
    private static final int ENEMY_REFRESH = 5;
    /** Intervalo mínimo (ticks) entre avisos "a barreira não cede" para o mesmo jogador. */
    private static final int HOLD_MESSAGE_COOLDOWN = 30;

    private static final Map<ResourceLocation, Domains.LawHandler> HANDLERS = new ConcurrentHashMap<>();
    private static final Map<Integer, Entry> OPEN = new LinkedHashMap<>();
    private static final Map<UUID, Long> HOLD_MESSAGES = new HashMap<>();
    @Nullable private static volatile Domains.BarrierHandler barrier;

    private static final class Entry {
        Domains.Info info;
        long lastSeen;
        List<LivingEntity> enemies = List.of();
        long enemiesAt = Long.MIN_VALUE;

        Entry(Domains.Info info) {
            this.info = info;
            this.lastSeen = info.level().getGameTime();
        }
    }

    // ------------------------------------------------------------------ leis

    public static void register(ResourceLocation law, Domains.LawHandler handler) {
        if (law == null || handler == null) return;
        if (HANDLERS.put(law, handler) != null) IsekaiMahou.LOGGER.debug("Domains: lei {} registrada de novo (substituída)", law);
    }

    @Nullable
    public static Domains.LawHandler handler(ResourceLocation law) {
        return law == null ? null : HANDLERS.get(law);
    }

    public static void setBarrierHandler(@Nullable Domains.BarrierHandler h) {
        barrier = h;
    }

    @Nullable
    public static Domains.BarrierHandler barrierHandler() {
        return barrier;
    }

    // ------------------------------------------------------------------ ciclo de vida

    public static void opened(Domains.Info d) {
        if (d == null || d.owner() == null || d.level() == null) return;
        Entry old = OPEN.get(d.id());
        if (old != null) {
            // Já estava aberto (opened chamado de novo): só atualiza, sem repetir o onOpen
            old.info = d;
            old.lastSeen = d.level().getGameTime();
            return;
        }
        OPEN.put(d.id(), new Entry(d));
        for (ResourceLocation law : d.laws()) {
            Domains.LawHandler h = HANDLERS.get(law);
            if (h == null) continue;
            try {
                h.onOpen(d);
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.error("Erro no onOpen da lei de domínio {}", law, ex);
            }
        }
    }

    public static void ticked(Domains.Info d, int age) {
        if (d == null || d.owner() == null || d.level() == null) return;
        Entry e = OPEN.get(d.id());
        if (e == null) {
            // O módulo esqueceu o opened (ou o servidor recarregou o domo): abre agora
            opened(d);
            e = OPEN.get(d.id());
            if (e == null) return;
        }
        e.info = d;
        e.lastSeen = d.level().getGameTime();
        List<Domains.LawHandler> handlers = new ArrayList<>(d.laws().size());
        List<ResourceLocation> ids = new ArrayList<>(d.laws().size());
        for (ResourceLocation law : d.laws()) {
            Domains.LawHandler h = HANDLERS.get(law);
            if (h != null) {
                handlers.add(h);
                ids.add(law);
            }
        }
        if (handlers.isEmpty()) return;
        for (int i = 0; i < handlers.size(); i++) {
            try {
                handlers.get(i).onTick(d, age);
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.error("Erro no onTick da lei de domínio {}", ids.get(i), ex);
            }
        }
        // Uma lei pode ter fechado o domínio no onTick
        if (OPEN.get(d.id()) != e || d.owner().isRemoved()) return;
        long now = d.level().getGameTime();
        if (now - e.enemiesAt >= ENEMY_REFRESH || now < e.enemiesAt) {
            e.enemies = d.enemiesInside();
            e.enemiesAt = now;
        }
        List<LivingEntity> enemies = e.enemies;
        for (LivingEntity enemy : enemies) {
            // A lista tem até ENEMY_REFRESH ticks: confere de novo (morreu, saiu, ficou protegido)
            if (enemy.isRemoved() || !enemy.isAlive() || !d.contains(enemy) || !isEnemy(d, enemy) || shielded(enemy, d)) continue;
            for (int i = 0; i < handlers.size(); i++) {
                if (!enemy.isAlive()) break;
                try {
                    handlers.get(i).onEnemyTick(d, enemy, age);
                } catch (RuntimeException ex) {
                    IsekaiMahou.LOGGER.error("Erro no onEnemyTick da lei de domínio {}", ids.get(i), ex);
                }
            }
        }
    }

    public static void closed(Domains.Info d) {
        if (d == null) return;
        Entry e = OPEN.remove(d.id());
        // Só fecha o que estava aberto: chamar closed duas vezes não repete o onClose
        if (e != null) close(e.info);
    }

    private static void close(Domains.Info d) {
        for (ResourceLocation law : d.laws()) {
            Domains.LawHandler h = HANDLERS.get(law);
            if (h == null) continue;
            try {
                h.onClose(d);
            } catch (RuntimeException ex) {
                IsekaiMahou.LOGGER.error("Erro no onClose da lei de domínio {}", law, ex);
            }
        }
    }

    // ------------------------------------------------------------------ consultas

    public static List<Domains.Info> active(ServerLevel level) {
        List<Domains.Info> out = new ArrayList<>();
        for (Entry e : OPEN.values()) {
            Domains.Info d = e.info;
            if (d.level() == level && !d.owner().isRemoved()) out.add(d);
        }
        return out;
    }

    @Nullable
    public static Domains.Info at(ServerLevel level, Vec3 pos) {
        if (level == null || pos == null) return null;
        Domains.Info best = null;
        for (Entry e : OPEN.values()) {
            Domains.Info d = e.info;
            if (d.level() != level || d.owner().isRemoved() || !d.contains(pos)) continue;
            if (best == null || d.openedTick() >= best.openedTick()) best = d;
        }
        return best;
    }

    @Nullable
    public static Domains.Info innermost(ServerLevel level, Vec3 pos) {
        if (level == null || pos == null) return null;
        Domains.Info closed = null, open = null;
        for (Entry e : OPEN.values()) {
            Domains.Info d = e.info;
            if (d.level() != level || d.owner().isRemoved() || d.simple() || !d.contains(pos)) continue;
            if (d.closed()) {
                if (closed == null || smaller(d, closed)) closed = d;
            } else if (open == null || smaller(d, open)) {
                open = d;
            }
        }
        return closed != null ? closed : open;
    }

    /** Menor raio; empate: o mais recente. */
    private static boolean smaller(Domains.Info a, Domains.Info b) {
        return a.radius() < b.radius() || (a.radius() == b.radius() && a.openedTick() > b.openedTick());
    }

    public static boolean shielded(LivingEntity target, Domains.Info attacker) {
        if (target == null || attacker == null || OPEN.size() < 2) return false;
        UUID attackerOwner = attacker.owner().getUUID();
        for (Entry e : OPEN.values()) {
            Domains.Info d = e.info;
            if (d.id() == attacker.id() || d.level() != attacker.level() || !d.closed() || d.owner().isRemoved()) continue;
            // Só uma barreira MENOR de outro dono protege (o domínio maior só ataca por fora)
            if (d.radius() >= attacker.radius() || d.owner().getUUID().equals(attackerOwner)) continue;
            if (d.contains(target)) return true;
        }
        return false;
    }

    public static boolean sureHitOn(LivingEntity target) {
        if (target == null || !(target.level() instanceof ServerLevel level) || OPEN.isEmpty()) return false;
        for (Domains.Info d : active(level)) {
            if (!d.hasLaw(Domains.SURE_HIT) || d.simple() || d.owner() == target || !d.contains(target)) continue;
            if (!isEnemy(d, target) || shielded(target, d) || protectedBySimple(level, target, d)) continue;
            return true;
        }
        return false;
    }

    /** {@code target} está num Domínio Simples dele mesmo ou de um aliado (que não seja o dono do domínio atacante)? */
    private static boolean protectedBySimple(ServerLevel level, LivingEntity target, Domains.Info attacker) {
        for (Domains.Info d : active(level)) {
            if (!d.simple() || d.owner() == attacker.owner() || !d.contains(target)) continue;
            if (d.owner() == target || allied(d, target)) return true;
        }
        return false;
    }

    /** Mesma regra do domo do módulo domain. */
    public static boolean isEnemy(Domains.Info d, LivingEntity e) {
        LivingEntity owner = d.owner();
        if (e == null || owner == null || e == owner || !e.isAlive()) return false;
        if (e instanceof Player p && (p.isCreative() || p.isSpectator())) return false;
        if (allied(d, e)) return false;
        if (owner instanceof Player) return true;
        if (e instanceof Player) return true;
        if (e instanceof OwnableEntity own && own.getOwnerUUID() != null) return true;
        if (e instanceof Mob m && m.getTarget() == owner) return true;
        return owner instanceof Mob om && om.getTarget() == e;
    }

    private static boolean allied(Domains.Info d, LivingEntity e) {
        SpellContext c = d.ctx();
        if (c != null) return c.isAlly(e);
        LivingEntity owner = d.owner();
        UUID ownerId = owner.getUUID();
        if (e instanceof OwnableEntity own && ownerId.equals(own.getOwnerUUID())) return true;
        return owner.isAlliedTo(e);
    }

    // ------------------------------------------------------------------ barreira

    @Nullable
    public static Domains.Info barrierAt(ServerLevel level, Vec3 pos) {
        if (level == null || pos == null) return null;
        Domains.Info best = null;
        double bestGap = Domains.WALL_TOLERANCE;
        for (Entry e : OPEN.values()) {
            Domains.Info d = e.info;
            if (d.level() != level || !d.closed() || d.owner().isRemoved()) continue;
            double gap = d.wallDistance(pos);
            if (gap <= bestGap) {
                bestGap = gap;
                best = d;
            }
        }
        return best;
    }

    public static float hitBarrier(ServerLevel level, Vec3 hitPos, Vec3 attackerPos, @Nullable Entity attacker, float amount) {
        if (level == null || hitPos == null || !(amount > 0F)) return 0F;
        Domains.Info d = barrierAt(level, hitPos);
        if (d == null) return 0F;
        Entity author = author(attacker);
        // O lado do dono nunca derruba a própria barreira (projéteis dele batendo na parede de dentro, etc.)
        if (author instanceof LivingEntity le && (le == d.owner() || le.getUUID().equals(d.owner().getUUID()) || allied(d, le))) return 0F;
        Vec3 from = attackerPos != null ? attackerPos : author != null ? author.position() : hitPos;
        boolean fromInside = from.distanceToSqr(d.center()) < (double) d.radius() * d.radius();
        if (!d.breaksFrom(fromInside)) {
            barrierHolds(level, d, hitPos, author, fromInside);
            return 0F;
        }
        Domains.BarrierHandler h = barrier;
        if (h == null) return 0F;
        try {
            float dealt = h.damage(d.id(), amount, fromInside, attacker);
            return Float.isNaN(dealt) ? 0F : Math.max(0F, dealt);
        } catch (RuntimeException ex) {
            IsekaiMahou.LOGGER.error("Erro no BarrierHandler.damage do domínio {}", d.id(), ex);
            return 0F;
        }
    }

    /** Quem está por trás do ataque (dono do projétil/invocação, senão a própria entidade). */
    @Nullable
    private static Entity author(@Nullable Entity attacker) {
        if (attacker instanceof TraceableEntity t) {
            Entity owner = t.getOwner();
            if (owner != null) return owner;
        }
        return attacker;
    }

    /** O golpe veio do lado que não quebra: faíscas, som abafado e aviso (com intervalo) para o jogador. */
    private static void barrierHolds(ServerLevel level, Domains.Info d, Vec3 at, @Nullable Entity author, boolean fromInside) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.12);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 3, 0.15, 0.15, 0.15, 0.01);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.SHIELD_BLOCK, SoundSource.PLAYERS, 0.45F, 0.55F);
        if (!(author instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        long now = level.getGameTime();
        Long last = HOLD_MESSAGES.get(sp.getUUID());
        if (last != null && now - last >= 0 && now - last < HOLD_MESSAGE_COOLDOWN) return;
        HOLD_MESSAGES.put(sp.getUUID(), now);
        String key = fromInside ? "message.isekai_mahou.barrier.holds_inside" : "message.isekai_mahou.barrier.holds_outside";
        sp.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC), true);
    }

    // ------------------------------------------------------------------ limpeza

    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        if (server.getTickCount() % 20 != 0) return;
        if (!HOLD_MESSAGES.isEmpty()) {
            long now = server.overworld().getGameTime();
            HOLD_MESSAGES.values().removeIf(t -> now - t >= HOLD_MESSAGE_COOLDOWN || now < t);
        }
        if (OPEN.isEmpty()) return;
        List<Entry> stale = new ArrayList<>();
        for (Entry e : OPEN.values()) {
            Domains.Info d = e.info;
            boolean levelGone = server.getLevel(d.level().dimension()) != d.level();
            if (levelGone || d.level().getGameTime() - e.lastSeen > STALE_TICKS) stale.add(e);
        }
        for (Entry e : stale) {
            if (OPEN.get(e.info.id()) != e) continue;
            OPEN.remove(e.info.id());
            close(e.info);
        }
    }

    static void onLevelUnload(LevelEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        // O nível está indo embora: só esquece (handlers não devem mexer num mundo descarregando)
        OPEN.values().removeIf(e -> e.info.level() == level);
    }

    static void onServerStopped(ServerStoppedEvent event) {
        OPEN.clear();
        HOLD_MESSAGES.clear();
    }

    private DomainRegistry() {}
}
