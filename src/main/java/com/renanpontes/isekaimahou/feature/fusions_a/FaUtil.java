package com.renanpontes.isekaimahou.feature.fusions_a;

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
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.EventHooks;

/** Utilidades compartilhadas pelos glifos, efeitos, blocos e entidades das fusões (conjunto A). */
public final class FaUtil {
    /** Aumentos "vazios" (poder 1): para danos que já vêm multiplicados. */
    public static final SpellStats PLAIN = new SpellStats();

    /** (conjuração -> chave -> tick): evita criar a mesma zona/explosão várias vezes na mesma tick. */
    private static final Map<SpellContext, Map<String, Long>> ONCE = new WeakHashMap<>();

    // =====================================================================================
    //  Controle de repetição
    // =====================================================================================

    /**
     * true só na primeira chamada desta tick para (conjuração, chave). Formas de área chamam o efeito uma vez
     * por alvo: com isto, efeitos que criam zonas/pontes/invocações acontecem uma vez só.
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

    // =====================================================================================
    //  Dano, poder e alvos
    // =====================================================================================

    /** Dano mágico zerando a invencibilidade curta (combos na mesma magia somam de verdade). */
    public static float hurt(SpellContext ctx, Entity target, float base, SpellStats stats, Entity direct) {
        if (target instanceof LivingEntity le) le.invulnerableTime = 0;
        return ctx.damage(target, base, stats, direct);
    }

    /** Poder total (aumentos × conjuração × atributo de poder mágico). */
    public static float power(SpellContext ctx, SpellStats stats) {
        return stats.power() * ctx.powerMult() * ctx.casterSpellPower();
    }

    /** Amplificador de status a partir do poder (1.0 → 0, 1.4 → 1, ...). */
    public static int amp(SpellContext ctx, SpellStats stats) {
        return Mth.clamp(Math.round((power(ctx, stats) - 1F) / 0.4F), 0, 4);
    }

    public static boolean isSelf(SpellContext ctx, Entity target) {
        return ctx.caster() != null && target == ctx.caster();
    }

    /**
     * Alvos de um efeito ofensivo: o próprio alvo ou, quando a magia é lançada em si mesmo, os inimigos em volta
     * (uma "nova") — Si Mesmo + efeito ofensivo nunca machuca quem conjurou.
     */
    public static List<LivingEntity> offensiveTargets(SpellContext ctx, Entity target, double novaRadius) {
        if (isSelf(ctx, target)) return ctx.enemiesAround(target.position().add(0, target.getBbHeight() * 0.5, 0), novaRadius);
        List<LivingEntity> list = new ArrayList<>(1);
        if (target instanceof LivingEntity le) list.add(le);
        return list;
    }

    public static boolean isUndead(Entity e) {
        return e.getType().is(EntityTypeTags.UNDEAD) || (e instanceof LivingEntity le && le.isInvertedHealAndHarm());
    }

    public static boolean isBoss(Entity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES);
    }

    /** Direção horizontal para onde o conjurador olha (ou +Z sem conjurador). */
    public static Vec3 flatLook(SpellContext ctx) {
        Vec3 l = ctx.look();
        Vec3 flat = new Vec3(l.x, 0, l.z);
        return flat.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : flat.normalize();
    }

    /** Direção horizontal do conjurador para o alvo (ou o olhar, se forem o mesmo ponto). */
    public static Vec3 awayFromCaster(SpellContext ctx, Entity target) {
        if (ctx.caster() == null || ctx.caster() == target) return flatLook(ctx);
        Vec3 d = target.position().subtract(ctx.caster().position());
        d = new Vec3(d.x, 0, d.z);
        return d.lengthSqr() < 1.0E-4 ? flatLook(ctx) : d.normalize();
    }

    /** Resistência a empurrão (0..1) transformada em fator de força. */
    public static double kbFactor(Entity e) {
        if (!(e instanceof LivingEntity le)) return 1.0;
        double r = le.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE);
        return Math.max(0.15, 1.0 - r);
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

    /** Fonte de dano de magia creditando quem conjurou por último no alvo (para efeitos de status contínuos). */
    public static DamageSource creditedSource(LivingEntity victim) {
        Entity attacker = null;
        UUID id = ElementReactions.lastCaster(victim);
        if (id != null && victim.level() instanceof ServerLevel sl) attacker = sl.getEntity(id);
        var holder = victim.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CoreRegistries.SPELL_DAMAGE);
        return new DamageSource(holder, attacker, attacker);
    }

    /** Fonte de dano de magia para invocações: direto = invocação, autor = dono. */
    public static DamageSource summonSource(Entity direct, Entity owner) {
        var holder = direct.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
                .getHolderOrThrow(CoreRegistries.SPELL_DAMAGE);
        return new DamageSource(holder, direct, owner != null ? owner : direct);
    }

    // =====================================================================================
    //  Blocos: permissões e chão
    // =====================================================================================

    /** Pode transformar/quebrar este bloco? (spawn, aventura, proteções, mobGriefing). */
    public static boolean canBreak(SpellContext ctx, BlockPos pos) {
        ServerLevel level = ctx.level();
        // v1.0 r9: torre em escalada / masmorra são indestrutíveis (nem blocos temporários de magia)
        if (com.renanpontes.isekaimahou.core.api.Destruction.wardedFor(level, pos, ctx.caster())) return false;
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        // Bloco que já é uma magia temporária: a permissão já foi checada quando ele foi criado
        if (FaTempBlocks.isTracked(level, pos)) return true;
        BlockState state = level.getBlockState(pos);
        if (state.getDestroySpeed(level, pos) < 0 || state.hasBlockEntity()) return false;
        LivingEntity caster = ctx.caster();
        if (caster instanceof ServerPlayer sp) {
            if (!sp.mayBuild() || !sp.mayInteract(level, pos)) return false;
            return !CommonHooks.fireBlockBreak(level, sp.gameMode.getGameModeForPlayer(), sp, pos, state).isCanceled();
        }
        if (caster != null) return CommonHooks.canEntityDestroy(level, pos, caster);
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /** Pode colocar algo temporário aqui (posição de ar/planta)? */
    public static boolean canPlace(SpellContext ctx, BlockPos pos) {
        ServerLevel level = ctx.level();
        // v1.0 r9: torre em escalada / masmorra são indestrutíveis (nem blocos temporários de magia)
        if (com.renanpontes.isekaimahou.core.api.Destruction.wardedFor(level, pos, ctx.caster())) return false;
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        if (level.isOutsideBuildHeight(pos)) return false;
        LivingEntity caster = ctx.caster();
        if (caster instanceof ServerPlayer sp) {
            return sp.mayBuild() && sp.mayInteract(level, pos)
                    && !sp.blockActionRestricted(level, pos, sp.gameMode.getGameModeForPlayer());
        }
        if (caster != null) return EventHooks.canEntityGrief(level, caster);
        return level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING);
    }

    /** Espaço livre para pôr um bloco temporário: ar, plantas baixas, neve fina, fogo (nunca líquido-fonte). */
    public static boolean isOpen(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        if (s.isAir()) return true;
        if (!s.getFluidState().isEmpty()) return false;
        return s.canBeReplaced() && !s.hasBlockEntity();
    }

    /** Bloco cheio e sólido (chão de verdade). */
    public static boolean isSolidGround(Level level, BlockPos pos) {
        BlockState s = level.getBlockState(pos);
        return s.isCollisionShapeFullBlock(level, pos) && !s.hasBlockEntity();
    }

    /** Primeira posição com chão sólido abaixo (até {@code maxDown} blocos). */
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

    /** Posição do bloco de chão (sólido) sob um ponto, procurando um pouco acima e abaixo; null se não houver. */
    public static BlockPos groundBlock(Level level, BlockPos start, int up, int down) {
        for (int dy = up; dy >= -down; dy--) {
            BlockPos p = start.offset(0, dy, 0);
            if (isSolidGround(level, p) && isOpen(level, p.above())) return p;
        }
        return null;
    }

    // =====================================================================================
    //  Partículas e sons (servidor)
    // =====================================================================================

    public static ParticleOptions dust(int rgb, float size) {
        // v1.3: brilho fx aditivo (mesmo tamanho do pó do vanilla; respeita o preset gráfico no cliente)
        return com.renanpontes.isekaimahou.core.ElementFx.glow(rgb, size);
    }

    public static DustColorTransitionOptions dustFade(int from, int to, float size) {
        return new DustColorTransitionOptions(vec(from), vec(to), Math.min(4F, size));
    }

    private static Vector3f vec(int rgb) {
        return new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F, (rgb & 255) / 255F);
    }

    /** Anel horizontal de partículas. */
    public static void ring(ServerLevel level, Vec3 c, ParticleOptions p, double radius, int points, double speed) {
        for (int i = 0; i < points; i++) {
            double a = Math.PI * 2 * i / points;
            level.sendParticles(p, c.x + Math.cos(a) * radius, c.y, c.z + Math.sin(a) * radius, 1, 0.03, 0.03, 0.03, speed);
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

    /** Coluna vertical de partículas. */
    public static void column(ServerLevel level, Vec3 base, ParticleOptions p, double height, int perBlock, double spread, double speed) {
        int n = Math.max(2, (int) (height * perBlock));
        for (int i = 0; i < n; i++) {
            double y = base.y + height * i / n;
            level.sendParticles(p, base.x, y, base.z, 1, spread, 0.05, spread, speed);
        }
    }

    /** Espiral subindo em volta de uma entidade (vinhas, água, luz). */
    public static void spiral(ServerLevel level, Entity e, ParticleOptions p, int turns, int points) {
        double r = Math.max(0.45, e.getBbWidth() * 0.75);
        double h = e.getBbHeight();
        for (int i = 0; i < points; i++) {
            double t = i / (double) points;
            double a = t * Math.PI * 2 * turns;
            level.sendParticles(p, e.getX() + Math.cos(a) * r, e.getY() + t * h, e.getZ() + Math.sin(a) * r, 1, 0, 0, 0, 0);
        }
    }

    /** Partículas em volta do corpo de uma entidade. */
    public static void around(ServerLevel level, Entity e, ParticleOptions p, int count, double speed) {
        level.sendParticles(p, e.getX(), e.getY() + e.getBbHeight() * 0.5, e.getZ(), count,
                e.getBbWidth() * 0.5, e.getBbHeight() * 0.4, e.getBbWidth() * 0.5, speed);
    }

    /** Linha reta de partículas. */
    public static void line(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions p, double perBlock) {
        Vec3 d = to.subtract(from);
        int steps = Math.max(2, (int) (d.length() * perBlock));
        for (int i = 0; i <= steps; i++) {
            Vec3 q = from.add(d.scale(i / (double) steps));
            level.sendParticles(p, q.x, q.y, q.z, 1, 0.01, 0.01, 0.01, 0);
        }
    }

    /** Linha em zigue-zague (arco elétrico) entre dois pontos. */
    public static void zigzag(ServerLevel level, Vec3 from, Vec3 to, ParticleOptions core, ParticleOptions glow) {
        Vec3 d = to.subtract(from);
        double len = d.length();
        int segs = Math.max(3, (int) (len * 1.5));
        Vec3 prev = from;
        var r = level.random;
        for (int i = 1; i <= segs; i++) {
            Vec3 next = from.add(d.scale(i / (double) segs));
            if (i < segs) {
                double j = Math.min(0.6, len * 0.08);
                next = next.add((r.nextDouble() - 0.5) * j * 2, (r.nextDouble() - 0.5) * j * 2, (r.nextDouble() - 0.5) * j * 2);
            }
            line(level, prev, next, core, 5);
            level.sendParticles(glow, next.x, next.y, next.z, 1, 0.05, 0.05, 0.05, 0.02);
            prev = next;
        }
    }

    /** Raio puramente visual (sem fogo nem dano do vanilla): o dano é sempre o da magia. */
    public static void visualLightning(ServerLevel level, Vec3 pos) {
        LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
        if (bolt == null) return;
        bolt.moveTo(pos.x, pos.y, pos.z);
        bolt.setVisualOnly(true);
        level.addFreshEntity(bolt);
    }

    public static void sound(ServerLevel level, Vec3 pos, SoundEvent s, float volume, float pitch) {
        level.playSound(null, pos.x, pos.y, pos.z, s, SoundSource.PLAYERS, volume,
                pitch * (0.9F + level.random.nextFloat() * 0.2F));
    }

    /** Config de partículas reduzidas (lida no cliente). */
    public static boolean reducedParticles() {
        try {
            return !com.renanpontes.isekaimahou.client.fx.FxQuality.atLeast(MahouConfig.Graphics.HIGH);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private FaUtil() {}
}
