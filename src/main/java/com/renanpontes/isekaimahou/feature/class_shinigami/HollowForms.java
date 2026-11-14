package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.feature.class_shinigami.entity.HollowBeamEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Os poderes do VAZIO INTERIOR (homenagem aos Visored e à forma Hollow completa do Ichigo), domado no Mundo Interior:
 * <ul>
 *   <li><b>Máscara do Vazio</b> ({@link #VISORED}): a máscara branca de listras rubras no rosto por 20 s (+5 s a cada 10
 *   usos, até 45 s): técnicas Shinigami +30% de poder, Força I e Velocidade I, o Cero com força total e a VOZ DUPLA
 *   (o que você fala no chat ecoa distorcido; as técnicas soam em dobro). Ao acabar, a máscara se parte;</li>
 *   <li><b>Vasto Lorde</b> ({@link #VASTO}, forma G): a transformação completa — máscara com chifres longos, buraco no
 *   peito, Força III, Resistência II, Velocidade II, regeneração (3% da vida por segundo), golpes ×1,5, um pulso por
 *   segundo que aplica os efeitos da magia em volta e um CERO GIGANTE a cada 5 s. ARRISCADA: ao acabar, o corpo
 *   desaba (vida cai para 25%, Fraqueza II, Lentidão e o Vigor zerado).</li>
 * </ul>
 * O Cero ({@link #fireCero}) é usado pela forma {@code shinigami_cero} e pelo Vasto Lorde. O visual da máscara vai para os
 * clientes pelo pacote {@link ShinigamiNetwork.HollowMask} (camada no modelo do jogador). Estado só em memória.
 */
public final class HollowForms {
    public static final int NONE = 0;
    public static final int VISORED = 1;
    public static final int VASTO = 2;
    public static final int CERO_RGB = 0xE01030;
    public static final int MASK_RGB = 0xF2EEE6;
    /** Poder das técnicas Shinigami com a máscara / no Vasto Lorde. */
    public static final float VISORED_POWER = 1.3F;
    public static final float VASTO_POWER = 1.5F;
    public static final float VASTO_MELEE = 1.5F;
    private static final int SYNC = 40;
    private static final DustParticleOptions CERO = ShinigamiFx.dust(CERO_RGB, 1.4F);
    private static final DustParticleOptions CERO_DARK = ShinigamiFx.dust(0x2A0006, 1.6F);
    private static final DustParticleOptions MASK = ShinigamiFx.dust(MASK_RGB, 1.0F);

    private static final class State {
        final int kind;
        final SpellContext ctx;
        final ServerLevel level;
        final long end;
        final float radius;
        int age;

        State(int kind, SpellContext ctx, ServerLevel level, long end, float radius) {
            this.kind = kind;
            this.ctx = ctx;
            this.level = level;
            this.end = end;
            this.radius = radius;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    public static int kind(Entity e) {
        State s = ACTIVE.get(e.getUUID());
        return s == null ? NONE : s.kind;
    }

    public static boolean masked(Entity e) {
        return ACTIVE.containsKey(e.getUUID());
    }

    /** Multiplicador das técnicas Shinigami (máscara +30%, Vasto Lorde +50%). */
    public static float castPower(Entity e) {
        return switch (kind(e)) {
            case VISORED -> VISORED_POWER;
            case VASTO -> VASTO_POWER;
            default -> 1.0F;
        };
    }

    /** Duração da máscara pelo treino: 20 s + 5 s a cada 10 usos (até 45 s). */
    public static int visoredTicks(MagicData d) {
        return 400 + 100 * Math.min(5, d.counter(Shinigami.VISORED_USES) / 10);
    }

    // =====================================================================================
    //  Começar
    // =====================================================================================
    /** Põe a máscara. Falso (sem gastar nada além da conjuração) se o Vasto Lorde estiver ativo. */
    public static boolean startVisored(SpellContext ctx, int ticks) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return false;
        if (kind(caster) == VASTO) {
            if (caster instanceof ServerPlayer sp) {
                sp.displayClientMessage(Shinigami.msg("vasto_busy").withStyle(ChatFormatting.GRAY), true);
                // onda 5: a máscara não entrou — a receita moldada dela (que entra depois da conjuração) é desfeita logo
                sp.server.tell(new net.minecraft.server.TickTask(sp.server.getTickCount(), () -> ReleaseForge.end(sp, "shinigami_visored")));
            }
            return false;
        }
        ServerLevel level = ctx.level();
        ACTIVE.put(caster.getUUID(), new State(VISORED, ctx, level, level.getGameTime() + ticks, 4F));
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 0, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 0, false, false, true));
        sync(caster, VISORED, ticks);
        ShinigamiFxNet.burst(caster, ShinigamiFxNet.VISORED, caster.getEyePosition(), Vec3.ZERO, 1F, CERO_RGB, null);
        // a máscara se forma: estilhaços brancos convergindo para o rosto, listras rubras
        Vec3 face = caster.getEyePosition().add(caster.getLookAngle().scale(0.3));
        for (int k = 0; k < 14; k++) {
            double a = k * Mth.TWO_PI / 14;
            Vec3 out = face.add(Math.cos(a) * 1.4, Math.sin(a) * 1.0, Math.sin(a * 2) * 0.6);
            ShinigamiFx.line(level, out, face, k % 4 == 0 ? CERO : MASK, 0.25);
        }
        level.sendParticles(CERO_DARK, caster.getX(), caster.getY() + 1, caster.getZ(), 40, 0.6, 0.9, 0.6, 0.05);
        ShinigamiFx.sound(level, face, SoundEvents.SKELETON_AMBIENT, 1.0F, 0.4F);
        ShinigamiFx.sound(level, face, SoundEvents.SCULK_SHRIEKER_SHRIEK, 0.5F, 1.4F);
        ShinigamiFx.sound(level, face, SoundEvents.BONE_BLOCK_PLACE, 1.0F, 0.6F);
        if (caster instanceof ServerPlayer sp) {
            if (!ctx.formStats().silent && !com.renanpontes.isekaimahou.feature.transform_laws.TransformForge.inForge(ctx)) {
                Announce.send(sp, Shinigami.msg("visored_shout", sp.getDisplayName()), CERO_RGB, Announce.STYLE_SPELL);
            }
            if (Shinigami.isShinigami(sp)) MagicApi.addCounter(sp, Shinigami.VISORED_USES, 1);
        }
        // a explosão de Reiatsu ao colocar a máscara aplica os efeitos em volta
        for (LivingEntity e : ctx.enemiesAround(caster.position(), 4.0)) {
            Vec3 push = e.position().subtract(caster.position());
            push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0.4) : push.normalize().scale(0.8);
            e.setDeltaMovement(e.getDeltaMovement().add(push.x, 0.3, push.z));
            e.hurtMarked = true;
            ctx.resolveOnEntity(e, caster);
        }
        return true;
    }

    /** A forma Hollow completa (forma G). */
    public static void startVasto(SpellContext ctx, int ticks, float radius) {
        LivingEntity caster = ctx.caster();
        if (caster == null) return;
        ServerLevel level = ctx.level();
        // os pulsos e o Cero gigante saem com o poder do Vasto Lorde (troca o da máscara, se havia)
        ctx.setPowerMult(ctx.powerMult() * VASTO_POWER / castPower(caster));
        // onda 5: a máscara dá lugar ao Vasto Lorde — a receita moldada da máscara sai junto
        if (kind(caster) == VISORED && caster instanceof ServerPlayer vp) ReleaseForge.end(vp, "shinigami_visored");
        ACTIVE.remove(caster.getUUID());
        ACTIVE.put(caster.getUUID(), new State(VASTO, ctx, level, level.getGameTime() + ticks, radius));
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, ticks, 2, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, ticks, 1, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 1, false, false, true));
        caster.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 1, false, false, true));
        sync(caster, VASTO, ticks);
        ShinigamiFxNet.burst(caster, ShinigamiFxNet.BANKAI, caster.position(), Vec3.ZERO, Math.max(10F, radius + 4), CERO_RGB, null);
        ShinigamiFxNet.burst(caster, ShinigamiFxNet.VISORED, caster.getEyePosition(), Vec3.ZERO, 2F, 0x2A0006, null);
        Vec3 at = caster.position();
        ShinigamiFx.pillar(level, at, 18.0, 180, CERO_DARK);
        ShinigamiFx.pillar(level, at, 12.0, 90, CERO);
        for (int k = 0; k < 4; k++) ShinigamiFx.ring(level, at.add(0, 0.2 + k * 0.5, 0), 2 + k * 2.2, 30 + k * 10, k * 0.3F, k % 2 == 0 ? CERO : CERO_DARK);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y + 1, at.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y + 1, at.z, 40, 1.0, 1.2, 1.0, 0.1);
        ShinigamiFx.sound(level, at, SoundEvents.ENDER_DRAGON_GROWL, 1.2F, 0.6F);
        ShinigamiFx.sound(level, at, SoundEvents.WARDEN_ROAR, 1.0F, 0.7F);
        ShinigamiFx.sound(level, at, SoundEvents.WITHER_SPAWN, 0.5F, 0.6F);
        for (LivingEntity e : ctx.enemiesAround(at, radius + 2)) {
            Vec3 push = e.position().subtract(at);
            push = push.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 0.6) : push.normalize();
            e.setDeltaMovement(e.getDeltaMovement().add(push.x * 1.4, 0.6, push.z * 1.4));
            e.hurtMarked = true;
        }
        if (caster instanceof ServerPlayer sp && !ctx.formStats().silent && !com.renanpontes.isekaimahou.feature.transform_laws.TransformForge.inForge(ctx)) {
            Announce.send(sp, Shinigami.msg("vasto_shout", sp.getDisplayName()), MASK_RGB, Announce.STYLE_ULTIMATE);
        }
    }

    // =====================================================================================
    //  Tick
    // =====================================================================================
    static void tick(MinecraftServer server) {
        if (ACTIVE.isEmpty()) return;
        List<Runnable> after = new ArrayList<>();
        Iterator<Map.Entry<UUID, State>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, State> me = it.next();
            State s = me.getValue();
            Entity ent = s.level.getEntity(me.getKey());
            // trocou de dimensão: a forma acaba (com o preço)
            if (ent == null) ent = server.getPlayerList().getPlayer(me.getKey());
            if (!(ent instanceof LivingEntity e) || !e.isAlive() || e.level() != s.level || s.level.getGameTime() >= s.end) {
                it.remove();
                if (ent instanceof LivingEntity le) after.add(() -> finish(s, le, le.isAlive()));
                continue;
            }
            s.age++;
            if (s.age % SYNC == 0) sync(e, s.kind, (int) Math.max(1, s.end - s.level.getGameTime()));
            if (s.kind == VISORED) {
                if (s.age % 4 == 0) {
                    s.level.sendParticles(CERO_DARK, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 2, 0.35, 0.6, 0.35, 0.01);
                    s.level.sendParticles(CERO, e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ(), 1, 0.4, 0.6, 0.4, 0.01);
                }
            } else {
                vastoTick(s, e);
            }
        }
        for (Runnable r : after) r.run();
    }

    private static void vastoTick(State s, LivingEntity e) {
        ServerLevel l = s.level;
        if (s.age % 2 == 0) {
            l.sendParticles(CERO_DARK, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 3, 0.4, 0.8, 0.4, 0.02);
            l.sendParticles(CERO, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), 2, 0.5, 0.8, 0.5, 0.02);
        }
        if (s.age % 10 == 0) l.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 0.2, e.getZ(), 3, 0.5, 0.1, 0.5, 0.01);
        if (s.age % 20 == 0) {
            e.heal(e.getMaxHealth() * 0.03F);
            int hit = 0;
            for (LivingEntity t : s.ctx.enemiesAround(e.position(), s.radius)) {
                if (hit++ >= 16) break;
                s.ctx.resolveOnEntity(t, e);
            }
            ShinigamiFx.ring(l, e.position().add(0, 0.15, 0), s.radius, Math.min(60, (int) (s.radius * 7)), s.age * 0.05F, CERO_DARK);
            ShinigamiFx.sound(l, e.position(), SoundEvents.WARDEN_HEARTBEAT, 0.8F, 0.6F);
        }
        // o Cero gigante: carrega 0,75 s (brilho entre os chifres) e dispara para onde o Vasto Lorde olha
        int phase = s.age % 100;
        if (phase >= 45 && phase < 60 && s.age % 3 == 0) {
            Vec3 mouth = e.getEyePosition().add(e.getLookAngle().scale(0.9)).add(0, 0.25, 0);
            l.sendParticles(CERO, mouth.x, mouth.y, mouth.z, 6, 0.15, 0.15, 0.15, 0.0);
            l.sendParticles(CERO_DARK, mouth.x, mouth.y, mouth.z, 3, 0.08, 0.08, 0.08, 0.0);
            if (phase == 45) {
                ShinigamiFx.sound(l, mouth, SoundEvents.WARDEN_SONIC_CHARGE, 1.0F, 0.7F);
                ShinigamiFxNet.burst(e, ShinigamiFxNet.CERO_CHARGE, mouth, Vec3.ZERO, 15F, CERO_RGB, null);
            }
        }
        if (phase == 60) {
            Vec3 from = e.getEyePosition().add(e.getLookAngle().scale(0.9));
            fireCero(s.ctx, e, from, e.getLookAngle(), 40, 2.2F, 10F, false);
        }
    }

    /** Fim natural (o tempo acabou) ou forçado (morte/saída). */
    private static void finish(State s, LivingEntity e, boolean natural) {
        // onda 5: a receita moldada para a Máscara / o Vasto Lorde (aba Votos) acaba junto com a forma
        if (e instanceof ServerPlayer fp) ReleaseForge.end(fp, s.kind == VASTO ? "shinigami_vasto_lorde" : "shinigami_visored");
        sync(e, NONE, 0);
        ServerLevel l = s.level;
        Vec3 face = e.getEyePosition();
        // a máscara se parte
        l.sendParticles(MASK, face.x, face.y, face.z, 30, 0.3, 0.3, 0.3, 0.08);
        l.sendParticles(ParticleTypes.ITEM_SNOWBALL, face.x, face.y, face.z, 10, 0.2, 0.2, 0.2, 0.1);
        ShinigamiFx.sound(l, face, SoundEvents.GLASS_BREAK, 1.0F, 0.7F);
        if (!natural || !e.isAlive()) return;
        if (s.kind == VASTO) {
            // o preço da forma Hollow completa: o corpo desaba
            float floor = Math.max(1F, e.getMaxHealth() * 0.25F);
            if (e.getHealth() > floor) e.setHealth(floor);
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 0));
            e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 1));
            l.sendParticles(CERO_DARK, e.getX(), e.getY() + 1, e.getZ(), 50, 0.6, 0.9, 0.6, 0.05);
            ShinigamiFx.sound(l, e.position(), SoundEvents.WITHER_DEATH, 0.4F, 1.4F);
            if (e instanceof ServerPlayer sp) {
                MagicApi.get(sp).setMana(0F);
                sp.displayClientMessage(Shinigami.msg("vasto_end").withStyle(ChatFormatting.DARK_RED), true);
            }
        } else if (e instanceof ServerPlayer sp) {
            sp.displayClientMessage(Shinigami.msg("visored_end").withStyle(ChatFormatting.GRAY), true);
        }
    }

    // =====================================================================================
    //  Cero
    // =====================================================================================
    /**
     * Dispara um Cero: feixe rubro de núcleo negro em linha reta (para nos blocos, a menos que {@code ghost}), que
     * atravessa TODOS os inimigos no caminho (dano próprio + efeitos da magia) e explode no fim (metade do dano em
     * quem não foi atingido pelo feixe). Não quebra blocos.
     */
    public static void fireCero(SpellContext ctx, LivingEntity caster, Vec3 from, Vec3 dir, double range, float width, float bonus,
                                boolean ghost) {
        ServerLevel level = ctx.level();
        Vec3 d = dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
        Vec3 to = from.add(d.scale(range));
        if (!ghost) {
            HitResult r = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
            if (r.getType() == HitResult.Type.BLOCK) to = r.getLocation();
        }
        Vec3 end = to;
        List<LivingEntity> along = new ArrayList<>();
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(width + 1), LivingEntity::isAlive)) {
            if (e == caster || ctx.isAlly(e) || ctx.ignores(e)) continue;
            if (e.getBoundingBox().inflate(width * 0.5).clip(from, end).isPresent()) along.add(e);
        }
        along.sort(Comparator.comparingDouble(e -> e.distanceToSqr(from)));
        Set<UUID> struck = new HashSet<>();
        for (LivingEntity e : along) {
            struck.add(e.getUUID());
            Vec3 at = e.position().add(0, e.getBbHeight() / 2, 0);
            ctx.impact(at, d, e);
            if (bonus > 0) ctx.damage(e, bonus, ctx.formStats(), caster);
            if (e.isAlive()) ctx.resolveOnEntity(e, caster);
            level.sendParticles(CERO, at.x, at.y, at.z, 12, 0.3, 0.4, 0.3, 0.08);
        }
        // explosão no fim
        float blast = 1.5F + width;
        for (LivingEntity e : ctx.enemiesAround(end, blast)) {
            if (struck.contains(e.getUUID()) || bonus <= 0) continue;
            ctx.damage(e, bonus * 0.5F, ctx.formStats(), caster);
        }
        level.sendParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1 + (int) width, width * 0.5, width * 0.5, width * 0.5, 0);
        level.sendParticles(CERO, end.x, end.y, end.z, 30 + (int) (width * 15), blast * 0.4, blast * 0.4, blast * 0.4, 0.1);
        level.sendParticles(CERO_DARK, end.x, end.y, end.z, 20 + (int) (width * 10), blast * 0.3, blast * 0.3, blast * 0.3, 0.06);
        // o feixe (entidade só visual) e o lampejo na saída
        level.addFreshEntity(HollowBeamEntity.of(level, from, end, width, CERO_RGB));
        level.sendParticles(CERO, from.x, from.y, from.z, 16, 0.2, 0.2, 0.2, 0.05);
        level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
        ShinigamiFx.sound(level, from, SoundEvents.WARDEN_SONIC_BOOM, 1.0F, width > 1.5F ? 0.5F : 0.8F);
        ShinigamiFx.sound(level, end, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.9F);
        ctx.end(end, d);
    }

    // =====================================================================================
    //  Rede e limpeza
    // =====================================================================================
    static void sync(LivingEntity e, int kind, int ticks) {
        if (e.level().isClientSide) return;
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(e, new ShinigamiNetwork.HollowMask(e.getId(), kind, ticks));
    }

    /** Saiu do jogo: acaba já (o preço do Vasto Lorde é cobrado mesmo assim). */
    static void forget(Entity e) {
        State s = ACTIVE.remove(e.getUUID());
        if (s != null && e instanceof LivingEntity le) finish(s, le, le.isAlive());
    }

    /** v1.1: a Transformação que tinha a Máscara/o Vasto Lorde como corpo acabou — fim com o preço de sempre. */
    public static void stopForm(LivingEntity e) {
        State s = ACTIVE.remove(e.getUUID());
        if (s != null) finish(s, e, e.isAlive());
    }

    /** Fim forçado sem preço (o Mugetsu consome a máscara). */
    static void cancel(LivingEntity e) {
        if (ACTIVE.remove(e.getUUID()) != null) sync(e, NONE, 0);
    }

    static void clear() {
        ACTIVE.clear();
    }

    /**
     * Gancho da v1.3 (classe Hollow): o Shinigami foi tomado pelo Vazio Interior — sela a zanpakutō (sem preço) e tira a
     * Máscara/Vasto Lorde ativos sem cobrar, para a hollowificação começar limpa.
     */
    public static void purgeForHollowfication(ServerPlayer sp) {
        Release.seal(sp, false);
        cancel(sp);
    }

    private HollowForms() {}
}
