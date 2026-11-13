package com.renanpontes.isekaimahou.feature.combat_feel;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.renanpontes.isekaimahou.core.anim.GuardEvent;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.CriticalHitEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Micro-pausa (hit-stop) — o SERVIDOR só decide QUANDO: golpe pesado dado ou levado, crítico, explosão grande (do
 * Minecraft ou uma magia recém-conjurada que pega vários inimigos de uma vez) e aparo (o aparo do próprio jogador é
 * ouvido direto no cliente). A pausa em si é só visual e acontece na tela de quem recebe
 * o pacote ({@code client/HitStop}); nada no servidor para.
 * <p>
 * Barato: só ouvintes de eventos que já existem, sem busca por entidades (a explosão percorre a lista de jogadores do
 * mundo). Um limite por jogador evita metralhar pacotes em dano em área.
 */
final class HitStopServer {
    /** Ticks mínimos entre dois avisos para o mesmo jogador (um mais forte passa na frente). */
    private static final int GAP = 6;
    /** Raio a partir do qual uma explosão conta como "grande" (dinamite = 4, creeper = 3). */
    private static final float BIG_BLAST = 3.5F;

    private record Crit(long tick, int targetId) {}

    private record Sent(long tick, byte kind) {}

    /** Inimigos que uma magia precisa acertar no MESMO tick para contar como "explosão grande". */
    private static final int SPELL_BLAST_TARGETS = 4;
    /** Só a magia recém-conjurada conta (zonas e torres que ficam batendo não pausam a tela a cada pulso). */
    private static final int SPELL_BLAST_WINDOW = 40;

    /** Última conjuração de cada jogador: quando foi, e os acertos do tick atual. */
    private static final class Cast {
        long tick;
        long hitTick;
        int hits;
        boolean used;
    }

    private static final Map<UUID, Cast> CASTS = new ConcurrentHashMap<>();

    private static final Map<UUID, Crit> CRITS = new ConcurrentHashMap<>();
    private static final Map<UUID, Sent> LAST = new ConcurrentHashMap<>();
    private static long lastBlastShake = Long.MIN_VALUE / 2;

    static void init(IEventBus bus) {
        bus.addListener(EventPriority.LOWEST, false, CriticalHitEvent.class, HitStopServer::onCrit);
        bus.addListener(EventPriority.LOWEST, false, LivingDamageEvent.Post.class, HitStopServer::onDamage);
        bus.addListener(EventPriority.LOWEST, false, ExplosionEvent.Detonate.class, HitStopServer::onExplosion);
        bus.addListener(GuardEvent.Parry.class, HitStopServer::onParry);
        bus.addListener(EventPriority.LOWEST, false, SpellCastEvent.Post.class, HitStopServer::onSpellCast);
        bus.addListener(EventPriority.LOWEST, false, SpellDamageEvent.Post.class, HitStopServer::onSpellDamage);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> {
            CRITS.remove(e.getEntity().getUUID());
            LAST.remove(e.getEntity().getUUID());
            CASTS.remove(e.getEntity().getUUID());
        });
        bus.addListener(ServerStoppedEvent.class, e -> {
            CRITS.clear();
            LAST.clear();
            CASTS.clear();
            lastBlastShake = Long.MIN_VALUE / 2;
        });
    }

    /** Manda a micro-pausa {@code kind} para o jogador (respeita o limite por jogador). */
    static void send(ServerPlayer player, byte kind) {
        long now = player.serverLevel().getGameTime();
        Sent last = LAST.get(player.getUUID());
        if (last != null && now - last.tick < GAP && now >= last.tick && kind <= last.kind) return;
        LAST.put(player.getUUID(), new Sent(now, kind));
        PacketDistributor.sendToPlayer(player, new FeelPayloads.HitStop(kind));
    }

    private static void onCrit(CriticalHitEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || !event.isCriticalHit()) return;
        // O crítico é decidido antes do dano: guarda e confirma no dano do mesmo tick, no mesmo alvo
        CRITS.put(sp.getUUID(), new Crit(sp.serverLevel().getGameTime(), event.getTarget().getId()));
    }

    private static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide()) return;
        float dmg = event.getNewDamage();
        if (dmg <= 0F) return;
        DamageSource src = event.getSource();
        boolean blast = src.is(DamageTypeTags.IS_EXPLOSION);

        // Golpe pesado LEVADO (só de um atacante ou explosão: queda, fogo e afogamento não pausam nada)
        if (victim instanceof ServerPlayer vp && (src.getEntity() != null || blast)
                && dmg >= Math.max(5F, vp.getMaxHealth() * 0.25F)) {
            send(vp, blast ? FeelPayloads.STOP_BLAST : FeelPayloads.STOP_HURT);
        }

        if (!(src.getEntity() instanceof ServerPlayer sp) || sp == victim) return;
        Crit crit = CRITS.remove(sp.getUUID());
        boolean wasCrit = crit != null && crit.tick == sp.serverLevel().getGameTime() && crit.targetId == victim.getId();
        if (blast && dmg >= 8F) send(sp, FeelPayloads.STOP_BLAST);
        else if (heavy(victim, dmg)) send(sp, FeelPayloads.STOP_HEAVY);
        else if (wasCrit) send(sp, FeelPayloads.STOP_CRIT);
    }

    /**
     * Golpe pesado: tira 30% da vida de um inimigo comum de uma vez (mínimo 8 de dano); em chefe, 3% da vida
     * (mínimo 12) — senão toda pancada em chefe ou nenhuma contaria.
     */
    private static boolean heavy(LivingEntity victim, float dmg) {
        float max = victim.getMaxHealth();
        if (BossKit.isBossLike(victim)) return dmg >= Math.max(12F, max * 0.03F);
        return dmg >= Math.max(8F, max * 0.3F);
    }

    private static void onExplosion(ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        Explosion ex = event.getExplosion();
        float r = ex.radius();
        if (r < BIG_BLAST) return;
        Vec3 c = ex.center();
        double reach = Math.min(48.0, r * 4.0 + 8.0);
        double reach2 = reach * reach;
        boolean any = false;
        for (ServerPlayer p : level.players()) {
            if (p.isSpectator() || p.distanceToSqr(c) > reach2) continue;
            send(p, FeelPayloads.STOP_BLAST);
            any = true;
        }
        // Tremor curto pelo sistema que já existe (o cliente multiplica pela opção "tremor de tela").
        // Uma fileira de dinamite não empilha tremores: um a cada 4 ticks.
        long now = level.getGameTime();
        if (any && (now - lastBlastShake >= 4 || now < lastBlastShake)) {
            lastBlastShake = now;
            BossKit.shake(level, c, reach, Math.min(1.4F, r * 0.2F), 9);
        }
    }

    // ---- Magia recém-conjurada que acerta vários inimigos de uma vez = explosão grande
    private static void onSpellCast(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof ServerPlayer sp)) return;
        Cast c = CASTS.computeIfAbsent(sp.getUUID(), u -> new Cast());
        c.tick = sp.serverLevel().getGameTime();
        c.hits = 0;
        c.used = false;
    }

    private static void onSpellDamage(SpellDamageEvent.Post event) {
        if (!event.landed() || !(event.ctx().caster() instanceof ServerPlayer sp) || event.target() == sp) return;
        Cast c = CASTS.get(sp.getUUID());
        if (c == null || c.used) return;
        long now = sp.serverLevel().getGameTime();
        if (now - c.tick > SPELL_BLAST_WINDOW || now < c.tick) return;
        if (c.hitTick != now) {
            c.hitTick = now;
            c.hits = 0;
        }
        if (++c.hits >= SPELL_BLAST_TARGETS) {
            c.used = true;
            send(sp, FeelPayloads.STOP_BLAST);
        }
    }

    /** Quem TEVE o golpe aparado (outro jogador) também sente a pancada seca. */
    private static void onParry(GuardEvent.Parry event) {
        if (event.getAttacker() instanceof ServerPlayer attacker && attacker != event.getPlayer()) {
            send(attacker, FeelPayloads.STOP_PARRY);
        }
    }

    private HitStopServer() {}
}
