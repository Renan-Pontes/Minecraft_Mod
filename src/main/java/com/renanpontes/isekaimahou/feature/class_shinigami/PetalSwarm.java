package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.status.StatusEngine;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.ZanpakutoItem;
import com.renanpontes.isekaimahou.glyph.SpellCost;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Senbonzakura de verdade (arquétipo Pétalas de Lâmina): um ENXAME CONTROLÁVEL de mil pétalas-lâmina.
 * <ul>
 *   <li><b>Shikai — "Chire"</b>: com a Shikai liberada, SEGURE o botão de usar com a zanpakutō: a lâmina se desfaz em
 *   pétalas que fluem da mão e seguem a MIRA (até 28 blocos; gruda num inimigo mirado). Cada inimigo que o enxame
 *   atravessa leva um corte (1 a cada 0,5 s por alvo) e Sangramento de vez em quando. Custa um pouco de Vigor por
 *   segundo. Soltou: as pétalas voltam e a lâmina se refaz. Um toque rápido (sem segurar) sela a espada como antes.</li>
 *   <li><b>Bankai — "Senbonzakura Kageyoshi"</b>: lâminas gigantes afundam no chão e viram um OCEANO de pétalas em
 *   volta do Shinigami. Segurar usar = o oceano vira uma torrente maior seguindo a mira. Soltar mirando um inimigo =
 *   <b>Gōkei</b>: as pétalas fecham uma esfera em volta dele e esmagam (recarga 8 s). Agachado + usar = <b>Senkei</b>:
 *   um anel de mil espadas brilhantes prende quem está dentro (8 s) e, no fim, todas descem juntas.</li>
 * </ul>
 * O servidor simula só a "cabeça" do enxame (e um rastro curto para o dano); o cliente desenha as centenas de pétalas
 * ({@link ShinigamiFxNet.Swarm} a cada 2 ticks). Estado só em memória.
 */
public final class PetalSwarm {
    public static final int OFF = 0;
    public static final int STREAM = 1;
    public static final int OCEAN = 2;
    public static final int GOKEI = 3;
    public static final int SENKEI = 4;
    public static final int RETURN = 5;

    /** Dano por corte (antes da escala do núcleo e do poder do rank). */
    public static final float CUT_DAMAGE = 1.6F;
    public static final float CUT_DAMAGE_BANKAI = 2.2F;
    public static final float GOKEI_DAMAGE = 8.0F;
    public static final float SENKEI_TICK_DAMAGE = 1.2F;
    public static final float SENKEI_FINAL_DAMAGE = 6.0F;
    /** Vigor por segundo segurando o enxame (fração do máximo). */
    public static final float CHANNEL_COST = 0.012F;
    private static final int HIT_COOLDOWN = 10;
    private static final int GOKEI_TICKS = 24;
    private static final int GOKEI_COOLDOWN = 160;
    private static final int SENKEI_TICKS = 160;
    private static final int SENKEI_COOLDOWN = 400;
    private static final double RANGE = 28.0;
    /** Raio do anel de espadas do Senkei (o cliente desenha igual). */
    public static final float SENKEI_RADIUS = 7.0F;

    private static final class Swarm {
        final ServerLevel level;
        boolean bankai;
        boolean channel;
        int mode = OFF;
        Vec3 head;
        Vec3 center = Vec3.ZERO;
        float radius;
        int target = -1;
        int modeAge;
        int channelAge;
        long gokeiReady;
        long senkeiReady;
        final ArrayDeque<Vec3> trail = new ArrayDeque<>();
        final Map<Integer, Long> cooldown = new HashMap<>();
        final Map<Integer, Integer> hits = new HashMap<>();

        Swarm(ServerLevel level, Vec3 head) {
            this.level = level;
            this.head = head;
        }
    }

    private static final Map<UUID, Swarm> SWARMS = new HashMap<>();

    /** A Senbonzakura está ativa (é pétalas e a espada está liberada)? */
    public static boolean petals(ServerPlayer sp) {
        return Release.released(sp) && Shinigami.archetype(MagicApi.get(sp)) == Archetype.PETALS;
    }

    /** Posição da lâmina na mão (de onde as pétalas saem e para onde voltam). */
    public static Vec3 bladePos(LivingEntity e) {
        Vec3 look = e.getLookAngle();
        Vec3 right = new Vec3(-look.z, 0, look.x);
        right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
        return e.getEyePosition().add(look.scale(0.7)).add(right.scale(0.35)).add(0, -0.45, 0);
    }

    private static Swarm get(ServerPlayer sp) {
        Swarm s = SWARMS.get(sp.getUUID());
        if (s == null || s.level != sp.serverLevel()) {
            s = new Swarm(sp.serverLevel(), bladePos(sp));
            SWARMS.put(sp.getUUID(), s);
        }
        return s;
    }

    // =====================================================================================
    //  Entrada (chamado pela zanpakutō)
    // =====================================================================================
    /** Começou a segurar usar: "Chire" (Shikai) ou a torrente (Bankai). */
    public static void beginChannel(ServerPlayer sp) {
        Swarm s = get(sp);
        s.bankai = Release.mode(sp) == Release.Mode.BANKAI;
        if (s.mode == GOKEI || s.mode == SENKEI) return;
        if (s.mode == OFF || s.mode == RETURN && s.head.distanceToSqr(bladePos(sp)) < 1.0) {
            s.head = bladePos(sp);
            s.trail.clear();
        }
        s.channel = true;
        s.channelAge = 0;
        setMode(s, STREAM);
        ServerLevel l = sp.serverLevel();
        Vec3 at = bladePos(sp);
        l.sendParticles(ParticleTypes.CHERRY_LEAVES, at.x, at.y, at.z, 10, 0.2, 0.2, 0.2, 0.05);
        ShinigamiFx.sound(l, at, SoundEvents.CHERRY_LEAVES_BREAK, 1.0F, 0.6F);
        ShinigamiFx.sound(l, at, SoundEvents.AMETHYST_CLUSTER_BREAK, 0.6F, 1.6F);
        if (s.channelAge == 0 && !s.bankai) {
            sp.displayClientMessage(Shinigami.msg("petals_chire").withColor(Archetype.PETALS.rgb), true);
        }
    }

    /** Soltou usar. Na Bankai, mirando um inimigo perto da torrente, fecha o Gōkei nele. */
    public static void endChannel(ServerPlayer sp) {
        Swarm s = SWARMS.get(sp.getUUID());
        if (s == null || !s.channel) return;
        s.channel = false;
        if (s.mode != STREAM) return;
        if (s.bankai && Release.mode(sp) == Release.Mode.BANKAI) {
            LivingEntity t = s.target >= 0 && s.level.getEntity(s.target) instanceof LivingEntity le && le.isAlive() ? le : null;
            if (t == null) t = aimedEnemy(sp, RANGE);
            long now = s.level.getGameTime();
            if (t != null && now >= s.gokeiReady) {
                s.gokeiReady = now + GOKEI_COOLDOWN;
                s.target = t.getId();
                s.center = t.position().add(0, t.getBbHeight() * 0.5, 0);
                s.radius = Math.max(2.5F, t.getBbWidth() + 2.5F);
                setMode(s, GOKEI);
                sp.displayClientMessage(Shinigami.msg("petals_gokei").withColor(Archetype.PETALS.rgb), true);
                ShinigamiFx.sound(s.level, s.center, SoundEvents.CHERRY_LEAVES_BREAK, 1.4F, 0.5F);
                ShinigamiFx.sound(s.level, s.center, SoundEvents.ELYTRA_FLYING, 0.6F, 1.6F);
                return;
            }
        }
        setMode(s, RETURN);
        ShinigamiFx.sound(s.level, s.head, SoundEvents.CHERRY_LEAVES_BREAK, 0.8F, 1.2F);
    }

    /** Bankai começou (pétalas): o oceano de pétalas em volta e as lâminas gigantes afundando. */
    public static void bankai(ServerPlayer sp) {
        Swarm s = get(sp);
        s.bankai = true;
        s.channel = false;
        s.head = sp.position().add(0, 1, 0);
        s.trail.clear();
        setMode(s, OCEAN);
        ShinigamiFxNet.burst(sp, ShinigamiFxNet.KAGEYOSHI, sp.position(), Vec3.ZERO, 4.5F, Archetype.PETALS.rgb, null);
        ShinigamiTasks.later(26, () -> {
            if (!sp.isAlive()) return;
            ServerLevel l = sp.serverLevel();
            l.sendParticles(ParticleTypes.CHERRY_LEAVES, sp.getX(), sp.getY() + 0.3, sp.getZ(), 60, 3.0, 0.4, 3.0, 0.15);
            ShinigamiFx.sound(l, sp.position(), SoundEvents.CHERRY_LEAVES_BREAK, 2.0F, 0.5F);
            ShinigamiFx.sound(l, sp.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.6F);
        });
    }

    /** Agachado + usar na Bankai (pétalas): Senkei, o anel de mil espadas. */
    public static void senkei(ServerPlayer sp) {
        Swarm s = get(sp);
        long now = s.level.getGameTime();
        if (now < s.senkeiReady && !sp.getAbilities().instabuild) {
            sp.displayClientMessage(Shinigami.msg("petals_senkei_cd", (s.senkeiReady - now + 19) / 20)
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        s.senkeiReady = now + SENKEI_COOLDOWN;
        s.bankai = true;
        s.channel = false;
        s.center = sp.position();
        s.radius = SENKEI_RADIUS;
        setMode(s, SENKEI);
        sp.displayClientMessage(Shinigami.msg("petals_senkei").withColor(Archetype.PETALS.rgb), true);
        ShinigamiFx.sound(s.level, s.center, SoundEvents.ANVIL_LAND, 0.8F, 1.8F);
        ShinigamiFx.sound(s.level, s.center, SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
        ShinigamiFx.sound(s.level, s.center, SoundEvents.TRIDENT_RETURN, 1.0F, 0.6F);
    }

    private static void setMode(Swarm s, int mode) {
        s.mode = mode;
        s.modeAge = 0;
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    static void tick(MinecraftServer server) {
        if (SWARMS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Swarm>> it = SWARMS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Swarm> e = it.next();
            Swarm s = e.getValue();
            ServerPlayer sp = server.getPlayerList().getPlayer(e.getKey());
            if (sp == null || !sp.isAlive() || sp.serverLevel() != s.level || !petals(sp)) {
                if (sp != null && s.mode != OFF) send(sp, s, OFF);
                it.remove();
                continue;
            }
            boolean bankaiNow = Release.mode(sp) == Release.Mode.BANKAI;
            if (!bankaiNow && s.bankai && s.mode == OCEAN) setMode(s, OFF);
            s.bankai = bankaiNow;
            // soltou sem o evento (trocou de item, abriu um menu...)
            if (s.channel && !(sp.isUsingItem() && sp.getUseItem().getItem() instanceof ZanpakutoItem)) endChannel(sp);
            s.modeAge++;
            switch (s.mode) {
                case STREAM -> stream(sp, s);
                case RETURN -> back(sp, s);
                case GOKEI -> gokei(sp, s);
                case SENKEI -> senkeiTick(sp, s);
                case OCEAN -> s.head = sp.position().add(0, 1, 0);
                default -> {}
            }
            if (s.mode == OFF) {
                if (s.bankai) {
                    setMode(s, OCEAN);
                } else {
                    send(sp, s, OFF);
                    it.remove();
                    continue;
                }
            }
            if (s.modeAge % 2 == 0 || s.modeAge == 1) send(sp, s, s.mode);
            if (s.cooldown.size() > 64) s.cooldown.clear();
        }
    }

    private static void send(ServerPlayer sp, Swarm s, int mode) {
        Vec3 p = mode == GOKEI || mode == SENKEI ? s.center : s.head;
        float param = switch (mode) {
            case GOKEI -> s.modeAge / (float) GOKEI_TICKS;
            case SENKEI -> s.modeAge / (float) SENKEI_TICKS;
            default -> s.radius;
        };
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(sp,
                new ShinigamiFxNet.Swarm(sp.getId(), mode, (float) p.x, (float) p.y, (float) p.z, s.target, param, s.bankai));
    }

    /** A torrente segue a mira; corta quem atravessa. */
    private static void stream(ServerPlayer sp, Swarm s) {
        s.channelAge++;
        if (s.channelAge % 20 == 0 && !Shinigami.spendVigor(sp, s.bankai ? CHANNEL_COST * 0.6F : CHANNEL_COST)) {
            sp.displayClientMessage(MagicApi.noResourceMessage(sp), true);
            sp.stopUsingItem();
            endChannel(sp);
            return;
        }
        // ponto mirado: inimigo na mira (gruda nele) ou o bloco/fim do alcance
        LivingEntity aimed = aimedEnemy(sp, RANGE);
        Vec3 aim;
        if (aimed != null) {
            s.target = aimed.getId();
            aim = aimed.position().add(0, aimed.getBbHeight() * 0.55, 0);
        } else {
            s.target = -1;
            Vec3 eye = sp.getEyePosition();
            Vec3 to = eye.add(sp.getLookAngle().scale(RANGE));
            HitResult r = s.level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
            aim = r.getType() == HitResult.Type.BLOCK ? r.getLocation().add(sp.getLookAngle().scale(-0.8)) : to;
        }
        moveHead(s, aim, s.bankai ? 2.3 : 1.6);
        cut(sp, s, s.bankai ? 2.2 : 1.4, s.bankai ? CUT_DAMAGE_BANKAI : CUT_DAMAGE);
        if (s.modeAge % 5 == 0) ShinigamiFx.sound(s.level, s.head, SoundEvents.CHERRY_LEAVES_STEP, 0.9F, 0.7F + s.level.random.nextFloat() * 0.6F);
        if (s.modeAge % 14 == 0) ShinigamiFx.sound(s.level, s.head, SoundEvents.ELYTRA_FLYING, 0.25F, 1.8F);
    }

    /** As pétalas voltam para a mão; ao chegar, a lâmina se refaz. */
    private static void back(ServerPlayer sp, Swarm s) {
        Vec3 hand = bladePos(sp);
        moveHead(s, hand, 2.0);
        if (s.head.distanceToSqr(hand) < 1.2 || s.modeAge > 60) {
            ShinigamiFx.sound(s.level, hand, SoundEvents.TRIDENT_RETURN, 0.8F, 1.3F);
            s.level.sendParticles(ParticleTypes.CHERRY_LEAVES, hand.x, hand.y, hand.z, 6, 0.15, 0.15, 0.15, 0.02);
            setMode(s, s.bankai ? OCEAN : OFF);
        }
    }

    private static void moveHead(Swarm s, Vec3 aim, double speed) {
        Vec3 d = aim.subtract(s.head);
        double len = d.length();
        if (len > 1.0E-3) {
            // aceleração suave: longe = rápido, perto = desacelera (parece um cardume)
            double step = Math.min(len, Math.max(0.25, Math.min(speed, len * 0.35)));
            s.head = s.head.add(d.scale(step / len));
        }
        s.trail.addFirst(s.head);
        while (s.trail.size() > 10) s.trail.removeLast();
    }

    /** Corta os inimigos perto da cabeça e do rastro (cada um no máximo a cada {@link #HIT_COOLDOWN} ticks). */
    private static void cut(ServerPlayer sp, Swarm s, double radius, float damage) {
        Set<LivingEntity> found = new HashSet<>();
        int i = 0;
        for (Vec3 p : s.trail) {
            if (i++ % 2 != 0) continue;
            for (LivingEntity e : s.level.getEntitiesOfClass(LivingEntity.class, new AABB(p, p).inflate(radius), LivingEntity::isAlive)) {
                if (valid(sp, e)) found.add(e);
            }
            if (found.size() > 24) break;
        }
        long now = s.level.getGameTime();
        MagicData d = MagicApi.get(sp);
        float dmg = damage * SpellCost.damageScale() * Shinigami.rankPower(d);
        for (LivingEntity e : found) {
            Long cd = s.cooldown.get(e.getId());
            if (cd != null && now < cd) continue;
            s.cooldown.put(e.getId(), now + HIT_COOLDOWN);
            if (hurt(sp, e, dmg)) {
                int n = s.hits.merge(e.getId(), 1, Integer::sum);
                if (n % 4 == 1) Signatures.apply(sp, e, Archetype.PETALS, s.bankai ? 1.0F : 0.5F, 60);
                s.level.sendParticles(ParticleTypes.CHERRY_LEAVES, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 4, 0.3, 0.4, 0.3, 0.05);
                ShinigamiFx.sound(s.level, e.position(), SoundEvents.PLAYER_ATTACK_SWEEP, 0.35F, 1.7F);
            }
        }
        if (s.hits.size() > 64) s.hits.clear();
    }

    private static void gokei(ServerPlayer sp, Swarm s) {
        Entity t = s.target >= 0 ? s.level.getEntity(s.target) : null;
        if (t instanceof LivingEntity le && le.isAlive()) {
            s.center = le.position().add(0, le.getBbHeight() * 0.5, 0);
            // a esfera prende
            if (s.modeAge < GOKEI_TICKS) {
                le.setDeltaMovement(le.getDeltaMovement().multiply(0.3, 0.3, 0.3));
                le.hurtMarked = true;
            }
        }
        s.head = s.center;
        if (s.modeAge == GOKEI_TICKS) {
            MagicData d = MagicApi.get(sp);
            float dmg = GOKEI_DAMAGE * SpellCost.damageScale() * Shinigami.rankPower(d);
            for (LivingEntity e : s.level.getEntitiesOfClass(LivingEntity.class, new AABB(s.center, s.center).inflate(s.radius * 0.8),
                    LivingEntity::isAlive)) {
                if (!valid(sp, e)) continue;
                if (hurt(sp, e, dmg) && e.isAlive()) Signatures.apply(sp, e, Archetype.PETALS, 1.4F, 100);
            }
            ShinigamiFxNet.burst(sp, ShinigamiFxNet.GOKEI_HIT, s.center, Vec3.ZERO, s.radius, Archetype.PETALS.rgb, null);
            s.level.sendParticles(ParticleTypes.CHERRY_LEAVES, s.center.x, s.center.y, s.center.z, 50, s.radius * 0.5, s.radius * 0.5,
                    s.radius * 0.5, 0.2);
            ShinigamiFx.sound(s.level, s.center, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 1.5F);
            ShinigamiFx.sound(s.level, s.center, SoundEvents.PLAYER_ATTACK_SWEEP, 1.4F, 0.5F);
        }
        if (s.modeAge >= GOKEI_TICKS + 6) {
            s.head = s.center;
            setMode(s, RETURN);
        }
    }

    private static void senkeiTick(ServerPlayer sp, Swarm s) {
        s.head = s.center.add(0, 1, 0);
        double r = s.radius;
        List<LivingEntity> inside = new ArrayList<>();
        for (LivingEntity e : s.level.getEntitiesOfClass(LivingEntity.class, new AABB(s.center, s.center).inflate(r + 2, 4, r + 2),
                LivingEntity::isAlive)) {
            if (!valid(sp, e)) continue;
            Vec3 off = e.position().subtract(s.center);
            double dist = Math.sqrt(off.x * off.x + off.z * off.z);
            if (dist <= r) {
                inside.add(e);
                // quem tenta sair é empurrado de volta pelas espadas
                if (dist > r - 1.2 && dist > 1.0E-3) {
                    e.setDeltaMovement(e.getDeltaMovement().add(-off.x / dist * 0.35, 0.05, -off.z / dist * 0.35));
                    e.hurtMarked = true;
                }
            }
        }
        MagicData d = MagicApi.get(sp);
        float scale = SpellCost.damageScale() * Shinigami.rankPower(d);
        if (s.modeAge % 20 == 0 && s.modeAge < SENKEI_TICKS) {
            for (LivingEntity e : inside) hurt(sp, e, SENKEI_TICK_DAMAGE * scale);
            ShinigamiFx.sound(s.level, s.center, SoundEvents.CHAIN_PLACE, 0.6F, 1.8F);
        }
        if (s.modeAge == SENKEI_TICKS) {
            for (LivingEntity e : inside) {
                if (hurt(sp, e, SENKEI_FINAL_DAMAGE * scale) && e.isAlive()) Signatures.apply(sp, e, Archetype.PETALS, 1.4F, 100);
            }
            ShinigamiFx.sound(s.level, s.center, SoundEvents.TRIDENT_THUNDER.value(), 1.0F, 1.4F);
            ShinigamiFx.sound(s.level, s.center, SoundEvents.ANVIL_LAND, 1.0F, 1.2F);
        }
        if (s.modeAge >= SENKEI_TICKS + 10) setMode(s, OCEAN);
    }

    // =====================================================================================
    //  Ajudantes
    // =====================================================================================
    private static boolean valid(ServerPlayer sp, LivingEntity e) {
        if (e == sp || e instanceof ArmorStand || e.isSpectator() || e.isInvulnerable()) return false;
        Boolean social = Party.allyOverride(sp, e);
        if (social != null) return !social;
        return !StatusEngine.friendly(sp, e);
    }

    /** Corte das pétalas: dano de ataque do jogador, mas sem ser "golpe de espada" (não devolve Vigor nem dobra a assinatura). */
    private static boolean hurt(ServerPlayer sp, LivingEntity e, float amount) {
        DamageSource src = new DamageSource(sp.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(DamageTypes.PLAYER_ATTACK), null, sp);
        int prev = e.invulnerableTime;
        e.invulnerableTime = 0;
        boolean ok = e.hurt(src, amount);
        if (!ok) e.invulnerableTime = prev;
        return ok;
    }

    @Nullable
    private static LivingEntity aimedEnemy(ServerPlayer sp, double range) {
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        Vec3 to = eye.add(look.scale(range));
        HitResult block = sp.level().clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, sp));
        if (block.getType() == HitResult.Type.BLOCK) to = block.getLocation();
        AABB box = sp.getBoundingBox().expandTowards(to.subtract(eye)).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(sp.level(), sp, eye, to, box,
                e -> e instanceof LivingEntity le && le.isAlive() && valid(sp, le), 0.6F);
        return hit != null && hit.getEntity() instanceof LivingEntity le ? le : null;
    }

    static void forget(UUID id) {
        SWARMS.remove(id);
    }

    static void clear() {
        SWARMS.clear();
    }

    private PetalSwarm() {}
}
