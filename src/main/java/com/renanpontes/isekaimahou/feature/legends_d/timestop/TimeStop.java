package com.renanpontes.isekaimahou.feature.legends_d.timestop;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.boss.Telegraph;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.legends_d.LdNet;
import com.renanpontes.isekaimahou.feature.legends_d.LdRegistry;
import com.renanpontes.isekaimahou.feature.legends_d.LdUtil;
import com.renanpontes.isekaimahou.feature.legends_d.entity.LdSpellEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * PARAR O TEMPO (servidor). Uma parada = esfera (centro, raio) por alguns segundos. Dentro dela, tudo que não é o
 * conjurador nem aliado dele PARA: o tick das entidades (mobs, projéteis, itens, blocos caindo, experiência...) é
 * cancelado ({@link EntityTickEvent.Pre}); jogadores recebem o efeito {@code time_stopped} e ficam presos no lugar.
 * <ul>
 *   <li>Projéteis do conjurador (e aliados) andam 0,5 s e então param no ar; retomam quando o tempo volta.</li>
 *   <li>Dano em quem está parado é GUARDADO (com a regra de invulnerabilidade do vanilla) e cai de uma vez no fim.</li>
 *   <li>No fim, os efeitos da receita são aplicados em todos os inimigos que ficaram parados.</li>
 *   <li>Chefes se soltam na metade do tempo (e levam o dano guardado ali).</li>
 *   <li>Sempre termina: fim do tempo, conjurador morto/fora/em outra dimensão, nível descarregado, servidor parando.
 *       Jogadores com o efeito fora de qualquer parada são soltos.</li>
 * </ul>
 */
public final class TimeStop {
    /** Projéteis do conjurador voam isso antes de parar. */
    public static final int PROJECTILE_FREE_TICKS = 10;
    public static final int GRAY = 0xC8C8D2;
    /** Máximo de alvos que recebem os efeitos da receita quando o tempo volta (multidões/fazendas). */
    public static final int MAX_EFFECT_TARGETS = 64;
    private static final ResourceLocation DOMAIN_DOME = IsekaiMahou.id("domain_dome");
    private static final List<Zone> ZONES = new ArrayList<>();
    private static int nextId = 1;

    /** Dano guardado num alvo parado. */
    private static final class Stored {
        float total;
        float lastAccepted;
        long lastTick = -100;
        DamageSource source;
        int hits;
    }

    private static final class Zone {
        final int id;
        final ServerLevel level;
        final SpellContext ctx;
        final UUID casterId;
        final Vec3 center;
        final float radius;
        final long start;
        final long end;
        final Set<UUID> frozen = new HashSet<>();
        final Map<UUID, Stored> stored = new HashMap<>();
        final Map<UUID, Long> projectileSeen = new HashMap<>();
        final Set<UUID> brokenFree = new HashSet<>();
        final Map<UUID, Vec3> anchors = new HashMap<>();
        /** Montados tirados da montaria parada (passageiro → montaria), remontados quando o tempo volta. */
        final Map<UUID, UUID> unmounted = new HashMap<>();
        boolean dirty = true;
        int[] lastExempt = new int[0];

        Zone(int id, SpellContext ctx, Vec3 center, float radius, long start, int duration) {
            this.id = id;
            this.level = ctx.level();
            this.ctx = ctx;
            this.casterId = ctx.casterId();
            this.center = center;
            this.radius = radius;
            this.start = start;
            this.end = start + duration;
        }

        boolean contains(Entity e) {
            return e.level() == level && e.position().distanceToSqr(center) <= (double) radius * radius;
        }

        @Nullable
        LivingEntity caster() {
            LivingEntity c = ctx.caster();
            return c != null && c.isAlive() && !c.isRemoved() && c.level() == level ? c : null;
        }
    }

    // =====================================================================================
    //  API
    // =====================================================================================
    /** Começa uma parada. Uma por conjurador: uma nova substitui a antiga (que termina agora). */
    public static void start(SpellContext ctx, Vec3 center, float radius, int duration) {
        ServerLevel level = ctx.level();
        if (ctx.casterId() != null) {
            for (Zone z : new ArrayList<>(ZONES)) if (ctx.casterId().equals(z.casterId)) end(z);
        }
        Zone z = new Zone(nextId++, ctx, center, radius, level.getGameTime(), duration);
        ZONES.add(z);
        LdUtil.sound(level, center, SoundEvents.BELL_BLOCK, 4.0F, 0.5F);
        LdUtil.sound(level, center, SoundEvents.BEACON_ACTIVATE, 4.0F, 0.4F);
        LdUtil.sound(level, center, SoundEvents.ILLUSIONER_MIRROR_MOVE, 4.0F, 0.5F);
        LdUtil.send(level, ParticleTypes.FLASH, center.add(0, 1, 0), 1, 0, 0);
        LdUtil.send(level, Vfx.reversePortal(), center.add(0, 1, 0), 60, 1.5, 0.8);
        sync(z, true);
    }

    /** Esta entidade está parada no tempo agora (servidor)? */
    public static boolean isFrozen(Entity e) {
        if (ZONES.isEmpty() || e.level().isClientSide) return false;
        if (e instanceof Player p) return p.hasEffect(LdRegistry.TIME_STOPPED);
        return zoneFreezing(e, e.level().getGameTime()) != null;
    }

    public static void clearAll() {
        ZONES.clear();
    }

    // =====================================================================================
    //  Regra: quem para
    // =====================================================================================
    @Nullable
    private static Zone zoneFreezing(Entity e, long now) {
        for (Zone z : ZONES) {
            if (z.contains(e) && !exempt(z, e, now)) return z;
        }
        return null;
    }

    /** Quem continua se mexendo dentro da parada (jogadores à parte: eles são presos pelo efeito). */
    private static boolean exempt(Zone z, Entity e, long now) {
        if (e.isSpectator() || e.getUUID().equals(z.casterId)) return true;
        if (z.brokenFree.contains(e.getUUID())) return true;
        if (DOMAIN_DOME.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))) return true;
        LivingEntity caster = z.ctx.caster();
        if (caster != null && (e.hasPassenger(caster) || caster.hasPassenger(e) || e.isPassengerOfSameVehicle(caster))) return true;
        Entity owner = ownerOf(e);
        boolean friendlyOwner = owner != null && (owner.getUUID().equals(z.casterId) || z.ctx.isAlly(owner));
        if (e instanceof Projectile) {
            if (!friendlyOwner) return false;
            Long seen = z.projectileSeen.get(e.getUUID());
            if (seen == null) {
                z.projectileSeen.put(e.getUUID(), now);
                z.dirty = true;
                return true;
            }
            return now - seen < PROJECTILE_FREE_TICKS;
        }
        if (e instanceof LivingEntity && z.ctx.isAlly(e)) return true;
        return friendlyOwner;
    }

    @Nullable
    private static Entity ownerOf(Entity e) {
        if (e instanceof Projectile p) return p.getOwner();
        if (e instanceof OwnableEntity o) return o.getOwner();
        if (e instanceof TraceableEntity t) return t.getOwner();
        if (e instanceof LdSpellEntity s) return s.casterEntity();
        return null;
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    /** O coração da magia: o tick de quem está parado não roda. */
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (ZONES.isEmpty()) return;
        Entity e = event.getEntity();
        if (e.level().isClientSide || e instanceof Player) return;
        long now = e.level().getGameTime();
        Zone z = zoneFreezing(e, now);
        if (z == null) return;
        event.setCanceled(true);
        // O tempo não passa para ele (idade/animações), e ele fica exatamente onde está
        e.tickCount--;
        // Passageiros (jóqueis, mobs em barcos/carrinhos) tickam pelo rideTick, que o evento não cancela: descem da
        // montaria parada, ficam no mesmo lugar (e param como qualquer outro) e remontam quando o tempo volta
        if (e.isVehicle()) {
            for (Entity p : List.copyOf(e.getPassengers())) {
                if (p instanceof Player || exempt(z, p, now)) continue;
                Vec3 at = p.position();
                float yRot = p.getYRot(), xRot = p.getXRot();
                p.stopRiding();
                p.moveTo(at.x, at.y, at.z, yRot, xRot);
                p.setDeltaMovement(Vec3.ZERO);
                z.unmounted.put(p.getUUID(), e.getUUID());
            }
        }
        if (z.frozen.add(e.getUUID())) {
            // Golpes telegrafados de quem parou também param (não explodem durante a parada)
            if (e instanceof LivingEntity) Telegraph.cancelAll(e);
            if (e instanceof Projectile) {
                z.dirty = true;
                // O cliente simula projéteis sozinho: manda a posição exata em que ele parou
                z.level.getChunkSource().broadcast(e, new ClientboundTeleportEntityPacket(e));
            }
        }
    }

    /**
     * Algo do conjurador (projétil, arma conjurada, zona...) nascendo dentro da parada: o cliente fica sabendo logo
     * (não trava até o próximo sincronismo).
     */
    public static void onJoin(EntityJoinLevelEvent event) {
        if (ZONES.isEmpty() || event.getLevel().isClientSide() || event.getEntity() instanceof Player) return;
        Entity e = event.getEntity();
        long now = event.getLevel().getGameTime();
        for (Zone z : ZONES) {
            if (z.contains(e) && exempt(z, e, now)) z.dirty = true;
        }
    }

    /** Dano em quem está parado fica guardado para quando o tempo voltar. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (ZONES.isEmpty()) return;
        LivingEntity t = event.getEntity();
        if (t.level().isClientSide || event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        long now = t.level().getGameTime();
        Zone z = t instanceof Player p ? playerZone(p) : zoneFreezing(t, now);
        if (z == null) return;
        event.setCanceled(true);
        float amount = event.getAmount();
        if (amount <= 0) return;
        Stored s = z.stored.computeIfAbsent(t.getUUID(), k -> new Stored());
        // Mesma regra de invulnerabilidade do vanilla (0,5 s): golpes seguidos só somam o que passar do anterior
        if (now - s.lastTick < 10) {
            if (amount <= s.lastAccepted) return;
            s.total += amount - s.lastAccepted;
        } else {
            s.total += amount;
        }
        s.lastAccepted = amount;
        s.lastTick = now;
        s.hits++;
        if (s.source == null || event.getSource().getEntity() != null) s.source = event.getSource();
        LdUtil.send(z.level, Vfx.magicCrit(), LdUtil.center(t), 6, t.getBbWidth() * 0.4, 0.0);
        LdUtil.send(z.level, Vfx.crit(), LdUtil.center(t), 3, t.getBbWidth() * 0.4, 0.0);
        LdUtil.sound(z.level, t.position(), SoundEvents.AMETHYST_BLOCK_HIT, 0.8F, 1.6F);
    }

    @Nullable
    private static Zone playerZone(Player p) {
        if (!p.hasEffect(LdRegistry.TIME_STOPPED)) return null;
        for (Zone z : ZONES) if (z.anchors.containsKey(p.getUUID())) return z;
        return null;
    }

    private static boolean held(@Nullable Entity e) {
        return e instanceof Player p && !p.isCreative() && p.hasEffect(LdRegistry.TIME_STOPPED);
    }

    public static void onAttack(AttackEntityEvent e) {
        if (held(e.getEntity())) e.setCanceled(true);
    }

    public static void onRightClickItem(PlayerInteractEvent.RightClickItem e) {
        if (!held(e.getEntity())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock e) {
        if (!held(e.getEntity())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
    }

    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        if (held(e.getEntity())) e.setCanceled(true);
    }

    public static void onEntityInteract(PlayerInteractEvent.EntityInteract e) {
        if (!held(e.getEntity())) return;
        e.setCanceled(true);
        e.setCancellationResult(InteractionResult.FAIL);
    }

    public static void onUseStart(LivingEntityUseItemEvent.Start e) {
        if (held(e.getEntity())) e.setCanceled(true);
    }

    public static void onCast(SpellCastEvent.Pre e) {
        LivingEntity c = e.getCaster();
        if (c == null || c.level().isClientSide) return;
        if (held(c) || (!(c instanceof Player) && isFrozen(c))) {
            e.setCanceled(true);
            LdUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_d.time_no_cast").withStyle(ChatFormatting.GRAY));
        }
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        Player p = e.getEntity();
        if (p.hasEffect(LdRegistry.TIME_STOPPED) && playerZone(p) == null) p.removeEffect(LdRegistry.TIME_STOPPED);
    }

    public static void onLevelUnload(LevelEvent.Unload e) {
        if (e.getLevel() instanceof ServerLevel sl) ZONES.removeIf(z -> z.level == sl);
    }

    // =====================================================================================
    //  Tick do servidor
    // =====================================================================================
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        // Rede de segurança: ninguém fica com Tempo Parado fora de uma parada
        if (server.getTickCount() % 10 == 0) {
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.hasEffect(LdRegistry.TIME_STOPPED) && playerZone(p) == null) p.removeEffect(LdRegistry.TIME_STOPPED);
            }
        }
        if (ZONES.isEmpty()) return;
        for (Zone z : new ArrayList<>(ZONES)) {
            long now = z.level.getGameTime();
            LivingEntity caster = z.caster();
            if (caster == null || now >= z.end || server.getLevel(z.level.dimension()) != z.level) {
                end(z);
                continue;
            }
            tickZone(z, now);
        }
    }

    private static void tickZone(Zone z, long now) {
        int elapsed = (int) (now - z.start);
        int duration = (int) (z.end - z.start);
        // Jogadores dentro (que não são aliados): presos no lugar
        for (ServerPlayer p : z.level.players()) {
            if (!z.contains(p) || p.isSpectator() || p.isCreative() || p.getUUID().equals(z.casterId) || z.ctx.isAlly(p)) continue;
            Vec3 anchor = z.anchors.get(p.getUUID());
            if (anchor == null) {
                anchor = p.position();
                z.anchors.put(p.getUUID(), anchor);
                p.setDeltaMovement(Vec3.ZERO);
                p.hurtMarked = true;
            }
            int left = (int) (z.end - now) + 10;
            MobEffectInstance cur = p.getEffect(LdRegistry.TIME_STOPPED);
            if (cur == null || cur.getDuration() < left - 5) {
                p.addEffect(new MobEffectInstance(LdRegistry.TIME_STOPPED, left, 0, true, false, true));
            }
            if (p.position().distanceToSqr(anchor) > 0.09) {
                p.connection.teleport(anchor.x, anchor.y, anchor.z, p.getYRot(), p.getXRot());
            }
            p.fallDistance = 0F;
        }
        // Chefes se soltam na metade da parada (e levam ali o dano guardado)
        if (elapsed >= Math.max(30, duration / 2) && elapsed % 5 == 0) {
            for (UUID id : new ArrayList<>(z.frozen)) {
                if (z.brokenFree.contains(id) || !(z.level.getEntity(id) instanceof LivingEntity le) || !LdUtil.isBoss(le)) continue;
                z.brokenFree.add(id);
                z.dirty = true;
                releaseDamage(z, le);
                LdUtil.send(z.level, Vfx.reversePortal(), LdUtil.center(le), 50, le.getBbWidth() * 0.6, 0.4);
                LdUtil.sound(z.level, le.position(), SoundEvents.GLASS_BREAK, 2.0F, 0.5F);
                Component msg = Component.translatable("message.isekai_mahou.legends_d.boss_breaks_time", le.getDisplayName())
                        .withStyle(ChatFormatting.RED);
                LivingEntity caster = z.caster();
                if (caster != null) LdUtil.actionbar(caster, msg);
            }
        }
        // O relógio: tique e taque, cada vez mais graves
        if (elapsed % 10 == 0) {
            boolean tick = (elapsed / 10) % 2 == 0;
            LdUtil.sound(z.level, z.center, SoundEvents.NOTE_BLOCK_HAT, 3.0F, tick ? 1.7F : 1.2F);
            LdUtil.sound(z.level, z.center, SoundEvents.WOODEN_BUTTON_CLICK_ON, 2.0F, tick ? 0.9F : 0.6F);
        }
        if (elapsed % 20 == 10) {
            // Poeira parada no ar ao redor do conjurador (o ar também parou)
            LivingEntity caster = z.caster();
            if (caster != null) LdUtil.send(z.level, LdUtil.dust(GRAY, 0.8F), LdUtil.center(caster), 8, 3.0, 0);
        }
        if (z.dirty || elapsed % 10 == 0) sync(z, false);
        // Esquece projéteis que já sumiram
        if (elapsed % 40 == 0) z.projectileSeen.keySet().removeIf(id -> z.level.getEntity(id) == null);
    }

    /** Manda o estado para os jogadores perto (a lista de quem continua se mexendo só quando muda, ou a cada 10 ticks). */
    private static void sync(Zone z, boolean first) {
        long now = z.level.getGameTime();
        List<Integer> ids = new ArrayList<>();
        LivingEntity caster = z.caster();
        if (caster != null) ids.add(caster.getId());
        double r = z.radius + 4;
        for (Entity e : z.level.getEntities((Entity) null, new AABB(z.center, z.center).inflate(r),
                e -> !(e instanceof Player) && z.contains(e))) {
            if (exempt(z, e, now)) ids.add(e.getId());
            if (ids.size() >= 4000) break;
        }
        int[] arr = ids.stream().mapToInt(Integer::intValue).toArray();
        z.dirty = false;
        if (!first && java.util.Arrays.equals(arr, z.lastExempt) && (now - z.start) % 10 != 0) return;
        z.lastExempt = arr;
        LdNet.near(z.level, z.center, z.radius + 96, new LdNet.TimeStopSync(z.id, z.center, z.radius,
                (int) (now - z.start), (int) Math.max(0, z.end - now), caster != null ? caster.getId() : -1, arr));
    }

    // =====================================================================================
    //  Fim: o tempo volta a andar
    // =====================================================================================
    private static void end(Zone z) {
        if (!ZONES.remove(z)) return;
        ServerLevel level = z.level;
        LdNet.near(level, z.center, z.radius + 96, new LdNet.TimeStopEnd(z.id));
        // Solta os jogadores
        for (UUID id : z.anchors.keySet()) {
            if (level.getEntity(id) instanceof Player p && playerZone(p) == null) p.removeEffect(LdRegistry.TIME_STOPPED);
        }
        LdUtil.sound(level, z.center, SoundEvents.BELL_RESONATE, 4.0F, 0.6F);
        LdUtil.sound(level, z.center, SoundEvents.BEACON_DEACTIVATE, 4.0F, 0.5F);
        // Todos que ficaram parados (e ainda estão no mundo): efeitos da receita nos inimigos, depois o dano guardado
        LivingEntity caster = z.caster();
        Set<UUID> ids = new HashSet<>(z.frozen);
        ids.addAll(z.anchors.keySet());
        ids.addAll(z.stored.keySet());
        List<LivingEntity> victims = new ArrayList<>();
        for (UUID id : ids) {
            if (level.getEntity(id) instanceof LivingEntity le && le.isAlive()) victims.add(le);
        }
        // Os mais perto do centro primeiro (o teto de efeitos vale para eles)
        victims.sort(java.util.Comparator.comparingDouble(le -> le.distanceToSqr(z.center)));
        int fx = 0, applied = 0;
        for (LivingEntity le : victims) {
            if (!le.isAlive()) continue;
            if (caster != null && le != caster && !z.ctx.isAlly(le) && !z.brokenFree.contains(le.getUUID())
                    && applied < MAX_EFFECT_TARGETS) {
                applied++;
                le.invulnerableTime = 0;
                z.ctx.resolveOnEntity(le, null);
            }
            if (releaseDamage(z, le) && fx++ < 24) {
                LdUtil.send(level, ParticleTypes.FLASH, LdUtil.center(le), 1, 0, 0);
            }
        }
        // Quem desceu da montaria parada volta para ela (se os dois ainda existem e estão juntos)
        for (Map.Entry<UUID, UUID> en : z.unmounted.entrySet()) {
            Entity p = level.getEntity(en.getKey()), v = level.getEntity(en.getValue());
            if (p != null && v != null && p.isAlive() && v.isAlive() && !p.isPassenger() && p.distanceToSqr(v) < 36) {
                p.startRiding(v, true);
            }
        }
        // Ligação "Ao Terminar" depois da parada (sai do conjurador; sem conjurador no mundo, nada nasce)
        if (caster != null) z.ctx.end(caster.getEyePosition(), caster.getLookAngle());
    }

    /** Aplica o dano guardado de uma vez (com clarão). Devolve true se havia algo. */
    private static boolean releaseDamage(Zone z, LivingEntity le) {
        Stored s = z.stored.remove(le.getUUID());
        if (s == null || s.total <= 0 || !le.isAlive()) return false;
        le.invulnerableTime = 0;
        le.hurt(s.source != null ? s.source : le.damageSources().magic(), s.total);
        Vec3 c = LdUtil.center(le);
        LdUtil.send(z.level, Vfx.crit(), c, 10 + Math.min(30, s.hits * 3), le.getBbWidth() * 0.5, 0.4);
        LdUtil.send(z.level, Vfx.magicCrit(), c, 10 + Math.min(30, s.hits * 3), le.getBbWidth() * 0.5, 0.4);
        LdUtil.sound(z.level, le.position(), SoundEvents.PLAYER_ATTACK_CRIT, 1.4F, 0.7F);
        if (s.hits >= 4) LdUtil.sound(z.level, le.position(), SoundEvents.ANVIL_LAND, 0.6F, 1.6F);
        return true;
    }

    private TimeStop() {}
}
