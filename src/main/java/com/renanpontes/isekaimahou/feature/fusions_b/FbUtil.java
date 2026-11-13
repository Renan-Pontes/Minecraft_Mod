package com.renanpontes.isekaimahou.feature.fusions_b;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/** Utilidades compartilhadas pelos glifos, efeitos e entidades das fusões B. */
public final class FbUtil {
    /** Aumentos "vazios" (poder 1): usado quando o dano já foi multiplicado antes (campos, invocações). */
    public static final SpellStats PLAIN = new SpellStats();

    /** (conjuração -> chave -> tick) para não repetir o mesmo efeito de área várias vezes na mesma tick. */
    private static final Map<SpellContext, Map<String, Long>> ONCE = new WeakHashMap<>();

    // =====================================================================================
    //  Controle de repetição
    // =====================================================================================

    /**
     * true só na primeira chamada desta tick para (conjuração, chave). Uma forma de área que acerta 6 mobs
     * chama o efeito 6 vezes: com isto o portal/campo/teleporte acontece uma vez só.
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

    /** Já houve um {@link #once} com esta chave nesta tick? (não marca nada) */
    public static boolean usedThisTick(SpellContext ctx, String key) {
        Map<String, Long> m = ONCE.get(ctx);
        if (m == null) return false;
        Long last = m.get(key);
        return last != null && last == ctx.level().getGameTime();
    }

    /** Chave de deduplicação por lugar (bloco): uma nuvem/área por ponto, mesmo com vários alvos. */
    public static String posKey(String key, Vec3 p) {
        return key + "@" + BlockPos.containing(p).asLong();
    }

    /** true se a entidade ainda não recebeu o efeito "key" desta conjuração nesta tick. */
    public static boolean firstHit(SpellContext ctx, String key, Entity e) {
        return once(ctx, key + "@" + e.getUUID());
    }

    /**
     * Efeitos que também agem em blocos (wantsBlocks): a forma Si Mesmo chama o efeito no conjurador e depois no
     * bloco sob os pés. Isto diz se o acerto de bloco é só esse "eco" e deve ser ignorado.
     */
    public static boolean isSelfFeetEcho(SpellContext ctx, BlockHitResult hit, String key) {
        LivingEntity c = ctx.caster();
        if (c == null) return false;
        boolean nearFeet = Vec3.atCenterOf(hit.getBlockPos()).distanceToSqr(c.position()) < 2.5 * 2.5;
        return nearFeet && usedThisTick(ctx, key);
    }

    // =====================================================================================
    //  Dano, poder e alvos
    // =====================================================================================

    /** Dano mágico do contexto zerando a invencibilidade curta do alvo (combos na mesma magia somam). */
    public static float hurt(SpellContext ctx, Entity target, float base, SpellStats stats, Entity direct) {
        if (target instanceof LivingEntity le) le.invulnerableTime = 0;
        return ctx.damage(target, base, stats, direct);
    }

    /**
     * Poder total (aumentos × conjuração × atributo de poder mágico). Use para o que NÃO passa por
     * {@link SpellContext#damage} (força de puxão, duração, invocações, dano de efeitos de status).
     */
    public static float power(SpellContext ctx, SpellStats stats) {
        return stats.power() * ctx.powerMult() * ctx.casterSpellPower();
    }

    /**
     * Aumentos para o dano que entidades de magia (fendas, tentáculos, fragmentos...) causam depois via
     * {@link SpellContext#damage}: mantém Crítico e Vampírico, mas com poder 1 — o dano guardado já vem
     * multiplicado por {@code stats.power()} e o contexto aplica a conjuração e o poder mágico na hora do golpe
     * (sem isso o poder mágico era contado duas vezes).
     */
    public static SpellStats hitStats(SpellStats stats) {
        if (!stats.crit && !stats.vampiric) return PLAIN;
        SpellStats s = new SpellStats();
        s.crit = stats.crit;
        s.vampiric = stats.vampiric;
        return s;
    }

    /** Amplificador de efeito de status a partir do poder (1.0 → 0, 1.4 → 1, ...). */
    public static int amp(SpellContext ctx, SpellStats stats) {
        return Mth.clamp(Math.round((power(ctx, stats) - 1F) / 0.4F), 0, 4);
    }

    public static boolean isSelf(SpellContext ctx, Entity target) {
        return ctx.caster() != null && target == ctx.caster();
    }

    /**
     * Alvos de um efeito ofensivo: o próprio alvo, ou — quando a magia é lançada em si mesmo — os inimigos em
     * volta do conjurador (uma "nova"), para que Si Mesmo + efeito ofensivo nunca machuque quem conjurou.
     */
    public static List<LivingEntity> offensiveTargets(SpellContext ctx, Entity target, double novaRadius) {
        if (isSelf(ctx, target)) return ctx.enemiesAround(center(target), novaRadius);
        List<LivingEntity> list = new ArrayList<>(1);
        if (target instanceof LivingEntity le && le.isAlive()) list.add(le);
        return list;
    }

    /** Inimigo vivo mais próximo do ponto (ou null). */
    @Nullable
    public static LivingEntity nearestEnemy(SpellContext ctx, Vec3 at, double radius) {
        LivingEntity best = null;
        double bestD = Double.MAX_VALUE;
        for (LivingEntity e : ctx.enemiesAround(at, radius)) {
            double d = e.distanceToSqr(at);
            if (d < bestD) { bestD = d; best = e; }
        }
        return best;
    }

    /** Chefes (tag c:bosses) resistem a controle forte (teleporte, prisão longa). */
    public static boolean isBoss(Entity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES);
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
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

    /** Faz um mob esquecer o alvo (cegueira de tinta, banimento). */
    public static void forgetTarget(LivingEntity e) {
        if (e instanceof Mob m) {
            m.setTarget(null);
            m.getNavigation().stop();
        }
    }

    /** Fonte de dano de um tipo do módulo, creditando quem conjurou por último no alvo (para XP/abates). */
    public static DamageSource source(LivingEntity victim, ResourceKey<DamageType> type) {
        Entity attacker = null;
        UUID id = ElementReactions.lastCaster(victim);
        if (id != null && victim.level() instanceof ServerLevel sl) attacker = sl.getEntity(id);
        return source(victim.level(), type, attacker, attacker);
    }

    public static DamageSource source(Level level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity attacker) {
        var holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type);
        return new DamageSource(holder, direct, attacker);
    }

    /** Fonte de dano de magia (tipo do núcleo) para criaturas invocadas: direto = invocação, autor = dono. */
    public static DamageSource summonSpellSource(Entity direct, @Nullable Entity owner) {
        return source(direct.level(), CoreRegistries.SPELL_DAMAGE, direct, owner != null ? owner : direct);
    }

    /**
     * Dano mágico com um tipo de dano próprio (ex.: Apagar, que ignora armadura), mantendo tudo o que o
     * {@link SpellContext#damage} faz: poder, crítico, reações, Vampírico e XP/carga do conjurador.
     */
    public static float typedSpellDamage(SpellContext ctx, LivingEntity target, float base, SpellStats stats,
                                         ResourceKey<DamageType> type, Element el) {
        ServerLevel level = ctx.level();
        float amount = base * power(ctx, stats);
        if (stats.crit && level.random.nextFloat() < 0.25F) {
            amount *= 1.75F;
            level.sendParticles(Vfx.crit(), target.getX(), target.getY() + target.getBbHeight() / 2,
                    target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
        }
        amount = ElementReactions.onSpellHit(ctx, target, el, amount);
        ElementReactions.mark(target, el, ctx.casterId(), level.getGameTime());
        if (amount <= 0) return 0;
        float before = target.getHealth();
        target.invulnerableTime = 0;
        LivingEntity caster = ctx.caster();
        if (target.hurt(source(level, type, caster, caster), amount)) {
            float dealt = Math.max(0, before - target.getHealth());
            if (stats.vampiric && caster != null) caster.heal(dealt * 0.25F);
            ServerPlayer sp = ctx.casterPlayer();
            if (sp != null) MagicApi.onSpellDamage(sp, el, dealt);
        }
        return amount;
    }

    // =====================================================================================
    //  Teleporte seguro
    // =====================================================================================

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

    /** A entidade cabe nesta posição (pés em {@code feet}) sem colidir e sem líquido? */
    public static boolean fits(Entity e, Vec3 feet) {
        Level level = e.level();
        if (!level.isLoaded(BlockPos.containing(feet)) || !level.getWorldBorder().isWithinBounds(BlockPos.containing(feet))) return false;
        if (feet.y < level.getMinBuildHeight() + 1 || feet.y > level.getMaxBuildHeight() - 2) return false;
        AABB box = e.getDimensions(e.getPose()).makeBoundingBox(feet);
        return level.noCollision(e, box) && !level.containsAnyLiquid(box);
    }

    /** A entidade cabe nesta posição sem ficar presa em blocos (aceita água; usado na troca de lugar). */
    public static boolean fitsBlocks(Entity e, Vec3 feet) {
        Level level = e.level();
        if (!level.isLoaded(BlockPos.containing(feet))) return false;
        return level.noCollision(e, e.getDimensions(e.getPose()).makeBoundingBox(feet));
    }

    /** Há chão firme logo abaixo (até 3 blocos de queda)? Evita jogar a entidade no vazio ou na lava. */
    public static boolean hasSafeFloor(Entity e, Vec3 feet) {
        Level level = e.level();
        BlockPos p = BlockPos.containing(feet);
        for (int i = 0; i < 4; i++) {
            BlockPos below = p.below(i + 1);
            var state = level.getBlockState(below);
            if (!state.getFluidState().isEmpty()) return false;
            if (!state.getCollisionShape(level, below).isEmpty()) {
                return !state.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                        && !state.is(net.minecraft.tags.BlockTags.FIRE)
                        && !state.is(net.minecraft.world.level.block.Blocks.CACTUS);
            }
        }
        return false;
    }

    /**
     * Procura um lugar seguro perto do ponto pedido: primeiro na mesma coluna (subindo/descendo até
     * {@code vertical}), depois em volta. Retorna os pés do lugar ou null.
     */
    @Nullable
    public static Vec3 findSafeSpot(Entity e, Vec3 wanted, int vertical, int horizontal) {
        for (int r = 0; r <= horizontal; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) continue;
                    Vec3 base = new Vec3(Math.floor(wanted.x) + 0.5 + dx, Math.floor(wanted.y), Math.floor(wanted.z) + 0.5 + dz);
                    for (int dy = 0; dy <= vertical; dy++) {
                        for (int sign : new int[] {1, -1}) {
                            if (dy == 0 && sign < 0) continue;
                            Vec3 p = base.add(0, dy * sign, 0);
                            if (fits(e, p) && hasSafeFloor(e, p)) {
                                // Assenta no chão, a não ser que o chão seja alto demais (cerca, muro)
                                Vec3 g = ground(e.level(), p, 4);
                                return fits(e, g) ? g : p;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    /** Teleporta mantendo rotação, zera queda e para a navegação. Funciona com jogadores (pacote de posição). */
    public static void teleport(Entity e, Vec3 feet) {
        if (e instanceof ServerPlayer sp) {
            sp.teleportTo(sp.serverLevel(), feet.x, feet.y, feet.z, sp.getYRot(), sp.getXRot());
        } else {
            e.teleportTo(feet.x, feet.y, feet.z);
        }
        e.resetFallDistance();
        e.setDeltaMovement(Vec3.ZERO);
        e.hurtMarked = true;
        if (e instanceof Mob m) m.getNavigation().stop();
    }

    /** Ponto onde o olhar do conjurador bate em um bloco (ou o fim do alcance). */
    public static BlockHitResult lookClip(LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        return caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
    }

    public static boolean hitBlock(HitResult hit) {
        return hit.getType() == HitResult.Type.BLOCK;
    }

    /** Jogador em modo criativo/espectador não deve receber controle (exceto do próprio conjurador). */
    public static boolean untouchable(Entity e) {
        return e instanceof Player p && (p.isCreative() || p.isSpectator());
    }

    private FbUtil() {}
}
