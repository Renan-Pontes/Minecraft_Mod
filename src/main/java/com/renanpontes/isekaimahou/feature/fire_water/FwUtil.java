package com.renanpontes.isekaimahou.feature.fire_water;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.EventHooks;

/** Utilidades compartilhadas pelos glifos, efeitos e entidades de Fogo/Água. */
public final class FwUtil {
    /** Aumentos "vazios" (poder 1): usado quando o dano já foi multiplicado antes (campos, invocações). */
    public static final SpellStats PLAIN = new SpellStats();

    /** (conjuração -> chave -> tick) para evitar repetir o mesmo efeito de área várias vezes na mesma tick. */
    private static final Map<SpellContext, Map<String, Long>> ONCE = new WeakHashMap<>();

    // =====================================================================================
    //  Controle de repetição
    // =====================================================================================

    /**
     * true só na primeira chamada desta tick para (conjuração, chave). Uma forma de área que acerta 6 mobs
     * chama o efeito 6 vezes: com isto a nuvem/invocação/explosão nasce uma vez só.
     */
    public static boolean once(SpellContext ctx, String key) {
        long now = ctx.level().getGameTime();
        Map<String, Long> m = ONCE.computeIfAbsent(ctx, k -> new HashMap<>());
        if (m.size() > 64) m.values().removeIf(t -> t != now);
        Long last = m.get(key);
        if (last != null && last == now) return false;
        m.put(key, now);
        return true;
    }

    /** true se a entidade ainda não levou o dano "key" desta conjuração nesta tick (explosões que se sobrepõem). */
    public static boolean firstHit(SpellContext ctx, String key, Entity e) {
        return once(ctx, key + "@" + e.getUUID());
    }

    // =====================================================================================
    //  Dano, poder e alvos
    // =====================================================================================

    /**
     * Dano mágico do contexto zerando a invencibilidade curta do alvo, para que combos na mesma magia
     * (ex.: Incendiar + Explosão) somem de verdade em vez de o segundo golpe ser absorvido.
     */
    public static float hurt(SpellContext ctx, Entity target, float base, SpellStats stats, Entity direct) {
        if (target instanceof LivingEntity le) le.invulnerableTime = 0;
        return ctx.damage(target, base, stats, direct);
    }

    /** Poder total (aumentos × conjuração × atributo de poder mágico). */
    public static float power(SpellContext ctx, SpellStats stats) {
        return stats.power() * ctx.powerMult() * ctx.casterSpellPower();
    }

    /** Amplificador de efeito de status a partir do poder (1.0 → 0, 1.4 → 1, ...). */
    public static int amp(SpellContext ctx, SpellStats stats) {
        return Mth.clamp(Math.round((power(ctx, stats) - 1F) / 0.4F), 0, 4);
    }

    public static boolean isSelf(SpellContext ctx, Entity target) {
        return ctx.caster() != null && target == ctx.caster();
    }

    /**
     * Alvos de um efeito ofensivo: o próprio alvo, ou — quando a magia é lançada em si mesmo — os inimigos
     * em volta do conjurador (uma "nova"), para que Si Mesmo + efeito ofensivo nunca machuque quem conjurou.
     */
    public static List<LivingEntity> offensiveTargets(SpellContext ctx, Entity target, double novaRadius) {
        if (isSelf(ctx, target)) return ctx.enemiesAround(target.position().add(0, target.getBbHeight() * 0.5, 0), novaRadius);
        List<LivingEntity> list = new ArrayList<>(1);
        if (target instanceof LivingEntity le) list.add(le);
        return list;
    }

    /** Direção horizontal para onde o conjurador olha (ou +Z sem conjurador). */
    public static Vec3 flatLook(SpellContext ctx) {
        Vec3 l = ctx.look();
        Vec3 flat = new Vec3(l.x, 0, l.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    /** Muda a velocidade e avisa o cliente (jogadores controlam o próprio movimento). */
    public static void setMotion(Entity e, Vec3 motion) {
        e.setDeltaMovement(motion);
        e.hasImpulse = true;
        e.hurtMarked = true;
    }

    public static void addMotion(Entity e, Vec3 add) {
        setMotion(e, e.getDeltaMovement().add(add));
    }

    /** Fonte de dano de um tipo do módulo, creditando quem conjurou por último no alvo (para XP/abates). */
    public static DamageSource source(LivingEntity victim, ResourceKey<DamageType> type) {
        Entity attacker = null;
        UUID id = ElementReactions.lastCaster(victim);
        if (id != null && victim.level() instanceof ServerLevel sl) attacker = sl.getEntity(id);
        if (attacker == victim) attacker = null; // ninguém leva crédito por se queimar sozinho
        var holder = victim.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type);
        return new DamageSource(holder, attacker, attacker);
    }

    /** Fonte de dano de magia (tipo do núcleo) para criaturas invocadas: direto = invocação, autor = dono. */
    public static DamageSource summonSpellSource(Entity direct, Entity owner) {
        var holder = direct.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CoreRegistries.SPELL_DAMAGE);
        return new DamageSource(holder, direct, owner != null ? owner : direct);
    }

    // =====================================================================================
    //  Blocos: permissões
    // =====================================================================================

    /** Pode quebrar/transformar este bloco? (proteção de spawn, modo aventura, mods de proteção, mobGriefing). */
    public static boolean canBreak(SpellContext ctx, BlockPos pos) {
        ServerLevel level = ctx.level();
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        BlockState state = level.getBlockState(pos);
        if (state.getDestroySpeed(level, pos) < 0) return false;
        LivingEntity caster = ctx.caster();
        if (caster instanceof ServerPlayer sp) {
            // Conjurador em outra dimensão (zona deixada para trás): o evento de quebra mandaria ao cliente dele
            // uma atualização de bloco da dimensão errada. Sem ele por perto, a magia não mexe no terreno.
            if (sp.level() != level || sp.isRemoved()) return false;
            if (!sp.mayBuild() || !sp.mayInteract(level, pos)) return false;
            return !CommonHooks.fireBlockBreak(level, sp.gameMode.getGameModeForPlayer(), sp, pos, state).isCanceled();
        }
        if (caster != null) return CommonHooks.canEntityDestroy(level, pos, caster);
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /** Pode colocar/alterar levemente algo aqui (água, fogo, apagar fogo, umedecer terra)? */
    public static boolean canPlace(SpellContext ctx, BlockPos pos) {
        ServerLevel level = ctx.level();
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        LivingEntity caster = ctx.caster();
        if (caster instanceof ServerPlayer sp) {
            if (sp.isRemoved()) return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
            return sp.mayBuild() && sp.mayInteract(level, pos)
                    && !sp.blockActionRestricted(level, pos, sp.gameMode.getGameModeForPlayer());
        }
        if (caster != null) return EventHooks.canEntityGrief(level, caster);
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /** Primeira posição com chão sólido abaixo (até {@code maxDown} blocos), para assentar campos no solo. */
    public static Vec3 ground(Level level, Vec3 pos, int maxDown) {
        BlockPos p = BlockPos.containing(pos);
        for (int i = 0; i <= maxDown; i++) {
            BlockPos below = p.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                return new Vec3(pos.x, p.getY(), pos.z);
            }
            p = below;
        }
        return pos;
    }

    // =====================================================================================
    //  Partículas e sons (servidor)
    // =====================================================================================

    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F),
                Math.min(4F, size));
    }

    /** Anel horizontal de partículas. */
    public static void ring(ServerLevel level, Vec3 c, ParticleOptions p, double radius, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            double x = c.x + Math.cos(a) * radius, z = c.z + Math.sin(a) * radius;
            level.sendParticles(p, x, c.y, z, 1, 0.03, 0.03, 0.03, speed);
        }
    }

    /** Anel que se espalha para fora (partículas com velocidade radial). */
    public static void shockwave(ServerLevel level, Vec3 c, ParticleOptions p, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x, c.y, c.z, 0, Math.cos(a), 0.05, Math.sin(a), speed);
        }
    }

    /** Pontos aleatórios na superfície de uma esfera (bolhas, escudos). */
    public static void sphere(ServerLevel level, Vec3 c, ParticleOptions p, double radius, int points) {
        var r = level.random;
        for (int i = 0; i < points; i++) {
            double u = r.nextDouble() * 2 - 1, th = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - u * u);
            level.sendParticles(p, c.x + s * Math.cos(th) * radius, c.y + u * radius, c.z + s * Math.sin(th) * radius,
                    1, 0, 0, 0, 0);
        }
    }

    /** Partículas em volta do corpo de uma entidade. */
    public static void around(ServerLevel level, Entity e, ParticleOptions p, int count, double speed) {
        level.sendParticles(p, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), count,
                e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, speed);
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent s, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, s, SoundSource.PLAYERS, volume,
                pitch * (0.9F + level.random.nextFloat() * 0.2F));
    }

    /** Lido só no cliente: config de partículas reduzidas. */
    public static boolean reducedParticles() {
        try {
            return MahouConfig.REDUCED_PARTICLES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private FwUtil() {}
}
