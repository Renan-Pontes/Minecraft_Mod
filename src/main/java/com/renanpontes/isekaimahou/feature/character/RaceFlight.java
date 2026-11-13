package com.renanpontes.isekaimahou.feature.character;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicHover;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Statuses;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * <b>Voo planado das formas aladas</b> (v1.3.1) — Anjo, Demônio e Dracônico voam como com <b>élitros</b>, não mais com o
 * voo "criativo" reto (que ficava feio em velocidade). O Fantasma continua flutuando devagar ({@link RaceForms}).
 * <ul>
 *   <li><b>Decolar</b>: pular e apertar Pular de novo no ar (2× rápido) = salto forte + batida de asas; as asas abrem no
 *       topo do salto. Pular no ar caindo (sem ter acabado de pular) = só abre as asas.</li>
 *   <li><b>Planar</b>: o estado vanilla de élitro ({@code startFallFlying}) sem item no peito — o mixin
 *       {@code LivingEntityRaceGlideMixin} chama {@link #handleFallFlying} para manter o estado enquanto a forma durar.
 *       Olhar para baixo mergulha e ganha velocidade; pousar (ou cair na água) fecha as asas; Shift fecha no ar.</li>
 *   <li><b>Batida de asas</b>: segurar Pular ou Correr planando = impulso (um foguete fraco) a cada {@link Wing#interval}
 *       ticks, custa um pouco do recurso mágico. É assim que se sobe a partir do chão.</li>
 *   <li><b>Golpe planando</b> (tecla racial no ar): olhando para baixo = <b>mergulho</b> com impacto em área; o Dracônico
 *       olhando reto = <b>Sopro Rasante</b> (sopro contínuo no chão por onde passa).</li>
 * </ul>
 * O movimento do jogador é do cliente (como o foguete no élitro): o cliente prevê o impulso na hora e o servidor só valida a
 * recarga, cobra o recurso e mostra a batida para os outros. Sem buscas por tick: só o mergulho/sopro ativos fazem uma busca
 * pequena, e com intervalo.
 */
public final class RaceFlight {
    /** Ações do pacote {@link CharacterNetwork.Glide}. */
    public static final int ACT_TAKEOFF = 0, ACT_START = 1, ACT_BEAT = 2, ACT_STOP = 3;
    /** Tipos da batida em {@link CharacterNetwork#FX_BEAT} ({@code ticks}). */
    public static final int BEAT_NORMAL = 1, BEAT_TAKEOFF = 2;
    /** Pular 2× no chão: a 2ª tecla precisa vir em até tantos ticks depois da 1ª. */
    public static final int TAKEOFF_WINDOW = 9;
    /** Ângulo (graus para baixo) a partir do qual a tecla racial planando vira mergulho. */
    public static final float DIVE_PITCH = 20F;
    private static final int DIVE_MAX = 80;
    private static final int STRAFE_TICKS = 30, STRAFE_EVERY = 5;
    /** Velocidade máxima depois de uma batida (blocos/tick). */
    public static final double MAX_BEAT_SPEED = 2.3;

    /**
     * Asas de cada raça: intervalo entre batidas, empuxo (velocidade-alvo na direção do olhar), quanto puxa para ela
     * (0..1), sustentação extra para cima e o custo (fração do recurso máximo).
     */
    public record Wing(int interval, double thrust, double pull, double lift, float costPct) {}

    private static final Wing DRAGON = new Wing(12, 1.85, 0.45, 0.30, 0.015F);
    private static final Wing DEMON = new Wing(10, 1.95, 0.40, 0.26, 0.013F);
    private static final Wing ANGEL = new Wing(8, 1.70, 0.33, 0.28, 0.010F);

    /** Mergulho em andamento (mutável, só no servidor). */
    private static final class Dive {
        final Race race;
        final long start;
        Vec3 last;
        double speed, peak;

        Dive(Race race, long start, Vec3 at) {
            this.race = race;
            this.start = start;
            this.last = at;
        }
    }

    private record Strafe(long until, Element element) {}

    /** Jogadores numa forma alada (servidor) — o mixin só olha isto. */
    private static final Set<UUID> GLIDERS = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> LAST_BEAT = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> NO_MANA_MSG = new ConcurrentHashMap<>();
    private static final Map<UUID, Dive> DIVES = new ConcurrentHashMap<>();
    private static final Map<UUID, Strafe> STRAFES = new ConcurrentHashMap<>();

    /** A forma tem asas (voo planado)? */
    public static boolean winged(@Nullable Race r) {
        return r == Race.ANGEL || r == Race.DEMON || r == Race.DRAGONBORN;
    }

    public static Wing wing(Race r) {
        return switch (r) {
            case DEMON -> DEMON;
            case ANGEL -> ANGEL;
            default -> DRAGON;
        };
    }

    /** Custo de uma batida (decolar = o dobro); mínimo 2. */
    public static float cost(Race r, int maxMana, boolean takeoff) {
        float c = Math.max(2F, maxMana * wing(r).costPct());
        return takeoff ? c * 2F : c;
    }

    /** Velocidade depois de uma batida no ar: puxa para o olhar (como um foguete curto) e dá sustentação. */
    public static Vec3 beatVelocity(Vec3 v, Vec3 look, Wing w) {
        Vec3 target = look.scale(w.thrust());
        Vec3 n = v.add(target.subtract(v).scale(w.pull())).add(0, w.lift(), 0);
        double len = n.length();
        return len > MAX_BEAT_SPEED ? n.scale(MAX_BEAT_SPEED / len) : n;
    }

    /** Velocidade da decolagem: salto forte para cima e um empurrão para onde olha. */
    public static Vec3 takeoffVelocity(Vec3 v, Vec3 look, Wing w) {
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? Vec3.ZERO : flat.normalize();
        return new Vec3(v.x * 0.6 + flat.x * 0.45, Math.max(v.y, 0.95 + w.lift() * 0.4), v.z * 0.6 + flat.z * 0.45);
    }

    // =====================================================================================
    //  Mixin: manter o élitro sem élitro
    // =====================================================================================
    /**
     * Chamado no começo de {@code LivingEntity.updateFallFlying} (só jogadores). Numa forma alada, o servidor mantém o
     * planar enquanto estiver no ar (sem precisar de élitro no peito) e devolve {@code true} (o vanilla é pulado).
     */
    public static boolean handleFallFlying(Player p) {
        if (GLIDERS.isEmpty() || p.level().isClientSide() || !GLIDERS.contains(p.getUUID())) return false;
        boolean keep = p.isFallFlying() && !p.onGround() && !p.isPassenger() && !p.hasEffect(MobEffects.LEVITATION)
                && !p.isInWater() && !p.isInLava() && !p.getAbilities().flying && !p.isSpectator();
        if (keep) {
            if ((p.getFallFlyingTicks() + 1) % 10 == 0) p.gameEvent(GameEvent.ELYTRA_GLIDE);
        } else if (p.isFallFlying()) {
            p.stopFallFlying();
        }
        return true;
    }

    // =====================================================================================
    //  Pacote do cliente
    // =====================================================================================
    static void onAction(ServerPlayer sp, int act) {
        Race form = RaceForms.formOf(sp);
        boolean ok = form != null && winged(form) && sp.isAlive() && !sp.isSpectator() && !sp.isPassenger();
        if (!ok) {
            // Cliente achou que podia planar: desfaz (o stopFallFlying força a sincronização)
            if (sp.isFallFlying() && !realElytra(sp)) sp.stopFallFlying();
            return;
        }
        switch (act) {
            case ACT_START -> {
                if (sp.onGround() || sp.isInWater() || sp.isInLava() || sp.hasEffect(MobEffects.LEVITATION) || sp.getAbilities().flying) {
                    sp.stopFallFlying();
                    return;
                }
                sp.startFallFlying();
                sp.resetFallDistance();
                MagicHover.allow(sp, 100);
                ServerLevel level = sp.serverLevel();
                RaceFx.sound(level, sp.position(), flapSound(form), 0.5F, flapPitch(form) * 1.15F, sp);
            }
            case ACT_STOP -> {
                if (sp.isFallFlying() && !realElytra(sp)) sp.stopFallFlying();
                sp.resetFallDistance();
            }
            case ACT_TAKEOFF, ACT_BEAT -> beat(sp, form, act == ACT_TAKEOFF);
            default -> { }
        }
    }

    private static void beat(ServerPlayer sp, Race form, boolean takeoff) {
        long now = sp.serverLevel().getGameTime();
        Wing w = wing(form);
        Long last = LAST_BEAT.get(sp.getUUID());
        // Folga de 3 ticks para o atraso da rede; mais rápido que isso é ignorado (sem custo, sem efeito)
        if (last != null && now - last < w.interval() - 3) return;
        if (!takeoff && !sp.isFallFlying()) return;
        if (!sp.getAbilities().instabuild) {
            MagicData md = MagicApi.get(sp);
            if (!md.consumeMana(cost(form, md.maxMana(), takeoff))) {
                Long msg = NO_MANA_MSG.get(sp.getUUID());
                if (msg == null || now - msg > 60) {
                    NO_MANA_MSG.put(sp.getUUID(), now);
                    sp.displayClientMessage(MagicApi.noResourceMessage(sp), true);
                }
                return;
            }
        }
        LAST_BEAT.put(sp.getUUID(), now);
        sp.resetFallDistance();
        MagicHover.allow(sp, 160);
        ServerLevel level = sp.serverLevel();
        // O próprio jogador já viu e ouviu (previsão do cliente): o resto vê a batida
        PacketDistributor.sendToPlayersTrackingEntity(sp, new CharacterNetwork.RaceFx(sp.getId(), CharacterNetwork.FX_BEAT,
                takeoff ? BEAT_TAKEOFF : BEAT_NORMAL));
        RaceFx.sound(level, sp.position(), flapSound(form), takeoff ? 1.2F : 0.9F, flapPitch(form), sp);
        if (takeoff) {
            RaceFx.sound(level, sp.position(), SoundEvents.ENDER_DRAGON_FLAP, 0.7F, 0.6F, sp);
            BlockState ground = level.getBlockState(sp.blockPosition().below());
            if (!ground.isAir()) RaceFx.ring(level, sp.position().add(0, 0.1, 0), 1.6, 10, new BlockParticleOption(ParticleTypes.BLOCK, ground));
        }
    }

    /** Som da batida: dragão pesado, morcego demoníaco, asas de luz. */
    public static SoundEvent flapSound(Race r) {
        return switch (r) {
            case DEMON -> SoundEvents.PHANTOM_FLAP;
            case ANGEL -> SoundEvents.ENDER_DRAGON_FLAP;
            default -> SoundEvents.ENDER_DRAGON_FLAP;
        };
    }

    public static float flapPitch(Race r) {
        return switch (r) {
            case DEMON -> 0.7F;
            case ANGEL -> 1.45F;
            default -> 0.85F;
        };
    }

    // =====================================================================================
    //  Tick (RaceForms.tick, todo tick)
    // =====================================================================================
    static void tick(ServerPlayer sp, @Nullable Race form) {
        UUID id = sp.getUUID();
        boolean w = winged(form) && sp.isAlive();
        if (!w) {
            if (GLIDERS.remove(id)) {
                // As asas sumiram (fim da forma, leite, morte): fecha e cai devagar
                DIVES.remove(id);
                STRAFES.remove(id);
                LAST_BEAT.remove(id);
                if (sp.isAlive() && sp.isFallFlying() && !realElytra(sp)) {
                    sp.stopFallFlying();
                    sp.resetFallDistance();
                    MagicHover.allow(sp, 200);
                    sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 160, 0, true, false, true));
                }
            }
            return;
        }
        GLIDERS.add(id);
        Dive d = DIVES.get(id);
        if (d != null) tickDive(sp, d);
        Strafe s = STRAFES.get(id);
        if (s != null) tickStrafe(sp, s);
    }

    /** O jogador está mergulhando (o dano de batida na parede vira o impacto)? */
    static boolean diving(Player p) {
        return DIVES.containsKey(p.getUUID());
    }

    static boolean realElytra(Player p) {
        return p.getItemBySlot(EquipmentSlot.CHEST).canElytraFly(p);
    }

    // =====================================================================================
    //  Golpe planando (tecla racial no ar)
    // =====================================================================================
    /** Mergulho (olhando para baixo) ou, no Dracônico, Sopro Rasante. {@code false} = usa o golpe normal da forma. */
    static boolean glidePower(ServerPlayer sp, CharacterData d, Race r) {
        if (!winged(r) || !sp.isFallFlying()) return false;
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if (sp.getXRot() >= DIVE_PITCH) {
            // Mergulho: aponta para baixo com tudo; o impacto vem quando tocar o chão (ou bater numa parede)
            Vec3 look = sp.getLookAngle();
            Vec3 dir = new Vec3(look.x, Math.min(look.y, -0.6), look.z).normalize();
            sp.setDeltaMovement(dir.scale(2.2));
            sp.hurtMarked = true;
            sp.resetFallDistance();
            MagicHover.allow(sp, DIVE_MAX + 60);
            DIVES.put(sp.getUUID(), new Dive(r, now, sp.position()));
            RaceFx.entityFx(sp, CharacterNetwork.FX_DIVE, DIVE_MAX);
            RaceFx.sound(level, sp.position(), RaceSounds.roar(r), 1.6F, 1.25F);
            RaceFx.sound(level, sp.position(), SoundEvents.PHANTOM_SWOOP, 1.2F, 0.6F);
            sp.displayClientMessage(Component.translatable("character.isekai_mahou.dive." + r.id).withColor(r.rgb)
                    .withStyle(st -> st.withBold(true)), true);
            return true;
        }
        if (r == Race.DRAGONBORN) {
            STRAFES.put(sp.getUUID(), new Strafe(now + STRAFE_TICKS, d.ancestry()));
            RaceFx.sound(level, sp.getEyePosition(), SoundEvents.ENDER_DRAGON_GROWL, 1.0F, 1.2F);
            sp.displayClientMessage(Component.translatable("character.isekai_mahou.strafe").withColor(d.ancestry().color)
                    .withStyle(st -> st.withBold(true)), true);
            return true;
        }
        return false;
    }

    private static void tickDive(ServerPlayer sp, Dive d) {
        long el = sp.serverLevel().getGameTime() - d.start;
        Vec3 pos = sp.position();
        double speed = pos.distanceTo(d.last);
        double prev = d.speed;
        d.last = pos;
        d.speed = speed;
        d.peak = Math.max(d.peak, speed);
        sp.resetFallDistance();
        if (sp.isInWater() || sp.isInLava()) {
            endDive(sp);
            return;
        }
        boolean ground = el > 1 && sp.onGround();
        // Bateu numa parede: colisão de lado com a velocidade despencando (os dois juntos: pacotes atrasados sozinhos não contam)
        boolean wall = el > 3 && sp.horizontalCollision && Math.max(prev, d.peak) > 0.8 && speed < Math.max(prev, 0.9) * 0.5;
        if (ground || wall) {
            impact(sp, d);
            return;
        }
        if (el > DIVE_MAX) endDive(sp);
    }

    private static void endDive(ServerPlayer sp) {
        DIVES.remove(sp.getUUID());
        RaceFx.entityFx(sp, CharacterNetwork.FX_DIVE, 0);
    }

    /** O impacto do mergulho: dano em área (cresce com a velocidade), atordoa, joga longe, cratera de luz no chão. */
    private static void impact(ServerPlayer sp, Dive d) {
        endDive(sp);
        ServerLevel level = sp.serverLevel();
        Vec3 at = sp.position();
        double power = Mth.clamp(d.peak / 2.0, 0.6, 1.4);
        double radius = 4.5 + power * 1.5;
        float base = (float) (16.0 * power);
        CharacterData cd = CharacterContent.data(sp);
        Element el = element(d.race, cd);
        int rgb = rgb(d.race, cd);
        for (LivingEntity le : RaceForms.foesAround(sp, radius)) {
            float dmg = d.race == Race.ANGEL && le.getType().is(EntityTypeTags.UNDEAD) ? base * 1.6F : base;
            RaceForms.hit(sp, le, dmg);
            Vec3 away = le.position().subtract(at);
            away = new Vec3(away.x, 0, away.z);
            away = away.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : away.normalize();
            le.push(away.x * 1.1 * power, 0.55, away.z * 1.1 * power);
            le.hurtMarked = true;
            Statuses.apply(le, Statuses.Kind.STUNNED, 1, 30, sp, 1F);
            switch (d.race) {
                case DRAGONBORN -> RaceForms.elementEffect(sp, le, el);
                case DEMON -> {
                    le.igniteForSeconds(5F);
                    Statuses.apply(le, Statuses.Kind.BURNING, 2, 100, sp, 1F);
                    Statuses.apply(le, Statuses.Kind.CORRUPTED, 1, 100, sp, 1F);
                }
                case ANGEL -> {
                    le.addEffect(new MobEffectInstance(MobEffects.GLOWING, 80, 0, false, false, true), sp);
                    Statuses.apply(le, Statuses.Kind.EXPOSED, 1, 100, sp, 1F);
                }
                default -> { }
            }
        }
        if (d.race == Race.ANGEL) {
            for (Player p : level.getEntitiesOfClass(Player.class, sp.getBoundingBox().inflate(radius), p -> p.isAlive() && !RaceForms.foe(sp, p))) {
                p.heal(6F * Progression.HEAL_SCALE);
            }
        }
        // Visual: impacto composto (montado no cliente pelo preset, com tremor), onda de choque e as rachaduras do FormFx
        Vec3 fx = at.add(0, 0.1, 0);
        ElementFx.impactFx(level, fx, el, rgb, 5, null, (float) (1.0 + 0.3 * power));
        ElementFx.shockwave(level, fx, rgb, (float) radius + 2F, 16, null);
        RaceFx.entityFx(sp, CharacterNetwork.FX_IMPACT, 1);
        BlockState ground = level.getBlockState(sp.blockPosition().below());
        if (!ground.isAir()) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2, at.z, 30, radius * 0.3, 0.2, radius * 0.3, 0.3);
        }
        RaceFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.7F);
        RaceFx.sound(level, at, RaceSounds.FORM_BURST.get(), 1.0F, 1.3F);
        RaceFx.sound(level, at, RaceSounds.roar(d.race), 1.4F, 1.1F);
        // Quica de leve depois do impacto
        if (sp.isFallFlying() && !realElytra(sp)) sp.stopFallFlying();
        sp.resetFallDistance();
        sp.setDeltaMovement(sp.getDeltaMovement().multiply(0.2, 0, 0.2).add(0, 0.45, 0));
        sp.hurtMarked = true;
        MagicHover.allow(sp, 100);
    }

    /** Sopro Rasante: a cada {@value #STRAFE_EVERY} ticks, sopro para a frente e para baixo de onde o dragão passa. */
    private static void tickStrafe(ServerPlayer sp, Strafe s) {
        ServerLevel level = sp.serverLevel();
        long now = level.getGameTime();
        if (now > s.until() || !sp.isFallFlying()) {
            STRAFES.remove(sp.getUUID());
            return;
        }
        if ((s.until() - now) % STRAFE_EVERY != 0) return;
        Vec3 eye = sp.getEyePosition();
        Vec3 look = sp.getLookAngle();
        Vec3 dir = new Vec3(look.x, Math.min(look.y, 0) - 0.55, look.z).normalize();
        Vec3 c = eye.add(dir.scale(4.5));
        AABB box = new AABB(c, c).inflate(3.0, 3.5, 3.0);
        int n = 0;
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box, e -> RaceForms.foe(sp, e))) {
            if (++n > 12) break; // orçamento
            RaceForms.hit(sp, le, 4F);
            RaceForms.elementEffect(sp, le, s.element());
        }
        // Jato de partículas (poucas: o servidor não sabe o preset gráfico de ninguém)
        for (double k = 1.0; k <= 8.0; k += 1.4) {
            Vec3 p = eye.add(dir.scale(k));
            double r = 0.2 + k * 0.18;
            level.sendParticles(ElementFx.signature(s.element()), p.x, p.y, p.z, 2, r, r, r, 0.02);
            level.sendParticles(ParticleTypes.DRAGON_BREATH, p.x, p.y, p.z, 1, r * 0.6, r * 0.6, r * 0.6, 0.01);
        }
        if ((s.until() - now) % (STRAFE_EVERY * 2) == 0) RaceFx.sound(level, eye, SoundEvents.BLAZE_SHOOT, 0.8F, 0.6F);
    }

    private static Element element(Race r, CharacterData d) {
        return switch (r) {
            case DEMON -> Element.HELLFIRE;
            case ANGEL -> Element.LIGHT;
            default -> d.ancestry();
        };
    }

    private static int rgb(Race r, CharacterData d) {
        return switch (r) {
            case DEMON -> 0xFF3A2A;
            case ANGEL -> 0xFFF0B0;
            default -> d.ancestry().color;
        };
    }

    // =====================================================================================
    //  Limpeza
    // =====================================================================================
    static void forget(UUID id) {
        GLIDERS.remove(id);
        LAST_BEAT.remove(id);
        NO_MANA_MSG.remove(id);
        DIVES.remove(id);
        STRAFES.remove(id);
    }

    static void clearAll() {
        GLIDERS.clear();
        LAST_BEAT.clear();
        NO_MANA_MSG.clear();
        DIVES.clear();
        STRAFES.clear();
    }

    private RaceFlight() {}
}
