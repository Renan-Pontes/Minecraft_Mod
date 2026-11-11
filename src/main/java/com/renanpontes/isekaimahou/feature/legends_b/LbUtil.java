package com.renanpontes.isekaimahou.feature.legends_b;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.api.Destruction;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.common.Tags;

/**
 * Utilitários de servidor do módulo legends_b: mira, geometria, alvos, dano que ignora armadura e
 * a regra de "apagar blocos" das magias lendárias (config, mobGriefing, proteções).
 */
public final class LbUtil {
    /** Blocos que as lendas nunca apagam (bedrock, portais, altares dos outros sistemas...). Tag de dados, ajustável. */
    public static final TagKey<Block> UNERASABLE = TagKey.create(Registries.BLOCK, IsekaiMahou.id("legends_b_unerasable"));
    /** Dimensão das Câmaras de Provação: a arena é protegida, nada de apagar blocos lá. */
    private static final ResourceLocation TRIAL_REALM = IsekaiMahou.id("trial_realm");

    /** Base ortonormal (frente, direita, cima) em volta de uma direção. */
    public record Frame(Vec3 forward, Vec3 right, Vec3 up) {
        public static Frame of(Vec3 dir) {
            Vec3 f = dir == null || dir.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : dir.normalize();
            Vec3 r = f.cross(new Vec3(0, 1, 0));
            r = r.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : r.normalize();
            return new Frame(f, r, r.cross(f).normalize());
        }
    }

    // =====================================================================================
    //  Conjurador e mira
    // =====================================================================================

    /** O conjurador ainda está no mundo da magia (formas atrasadas não saem de quem saiu/renasceu/mudou de dimensão). */
    public static boolean casterReady(SpellContext ctx) {
        LivingEntity c = ctx.caster();
        return c != null && !c.isRemoved() && c.isAlive() && c.level() == ctx.level();
    }

    /** Direção de mira: a do estágio se houver origem; mob com alvo mira no alvo; senão o olhar. */
    public static Vec3 aimDir(SpellContext ctx) {
        if (ctx.hasOrigin()) return ctx.look();
        LivingEntity c = ctx.caster();
        if (c instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            Vec3 d = center(mob.getTarget()).subtract(c.getEyePosition());
            if (d.lengthSqr() > 1.0E-4) return d.normalize();
        }
        return ctx.look();
    }

    /** Mesma regra de {@link #aimDir}, mas a partir de uma entidade qualquer (usado enquanto a magia "carrega"). */
    public static Vec3 aimDirOf(LivingEntity c) {
        if (c instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()) {
            Vec3 d = center(mob.getTarget()).subtract(c.getEyePosition());
            if (d.lengthSqr() > 1.0E-4) return d.normalize();
        }
        return c.getLookAngle();
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Ponto da caixa da entidade mais perto de {@code p}. */
    public static Vec3 closestPoint(Entity e, Vec3 p) {
        AABB b = e.getBoundingBox();
        return new Vec3(Mth.clamp(p.x, b.minX, b.maxX), Mth.clamp(p.y, b.minY, b.maxY), Mth.clamp(p.z, b.minZ, b.maxZ));
    }

    /** Algum efeito da magia vale para este alvo? (formas atravessam quem a magia ignora). */
    public static boolean affects(SpellContext ctx, Entity target) {
        for (ParsedSpell.EffectEntry e : ctx.spell().effects()) {
            if (ctx.isValidTarget(e.effect(), e.stats(), target)) return true;
        }
        return false;
    }

    /** ClipContext que aceita fonte nula (contexto recarregado sem conjurador). */
    public static BlockHitResult clip(ServerLevel level, Vec3 from, Vec3 to, ClipContext.Fluid fluid, @Nullable Entity source) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, fluid,
                source == null ? CollisionContext.empty() : CollisionContext.of(source)));
    }

    /** Desce de {@code p} até o primeiro bloco sólido ou superfície de líquido (até {@code depth} blocos). */
    public static Vec3 groundBelow(ServerLevel level, Vec3 p, double depth) {
        BlockHitResult r = clip(level, p, p.subtract(0, depth, 0), ClipContext.Fluid.ANY, null);
        return r.getType() == HitResult.Type.MISS ? p : r.getLocation();
    }

    /** Entidades "acertáveis" que um raio de largura {@code width} cruza, da mais perto para a mais longe. */
    public static List<Entity> entitiesAlong(SpellContext ctx, Vec3 from, Vec3 to, double width, Predicate<Entity> filter) {
        record Hit(Entity e, double d) {}
        List<Hit> hits = new ArrayList<>();
        Entity caster = ctx.caster();
        for (Entity e : ctx.level().getEntities(caster, new AABB(from, to).inflate(width + 1.0),
                e -> e.isAlive() && !e.isSpectator() && e.isPickable() && filter.test(e))) {
            AABB box = e.getBoundingBox().inflate(e.getPickRadius() + width);
            Optional<Vec3> p = box.contains(from) ? Optional.of(from) : box.clip(from, to);
            p.ifPresent(v -> hits.add(new Hit(e, v.distanceTo(from))));
        }
        hits.sort(Comparator.comparingDouble(Hit::d));
        List<Entity> out = new ArrayList<>(hits.size());
        for (Hit h : hits) out.add(h.e());
        return out;
    }

    /**
     * Ponto mirado no chão até {@code range} blocos: âncora do estágio (se houver), o alvo do mob, a criatura na mira
     * ou o chão onde o olhar bate (mirando o céu: o chão embaixo do fim do alcance).
     */
    public static Vec3 aimGround(SpellContext ctx, double range) {
        ServerLevel level = ctx.level();
        if (ctx.anchor() != null) return groundBelow(level, ctx.anchor().add(0, 0.5, 0), 24);
        LivingEntity c = ctx.caster();
        if (!ctx.hasOrigin() && c instanceof Mob mob && mob.getTarget() != null && mob.getTarget().isAlive()
                && mob.distanceTo(mob.getTarget()) <= range) {
            return mob.getTarget().position();
        }
        Vec3 eye = ctx.eyePos();
        Vec3 end = eye.add(aimDir(ctx).scale(range));
        BlockHitResult bh = clip(level, eye, end, ClipContext.Fluid.ANY, c);
        Vec3 limit = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        List<Entity> ents = entitiesAlong(ctx, eye, limit, 0.4, e -> e instanceof LivingEntity && e != c && affects(ctx, e));
        if (!ents.isEmpty()) return ents.get(0).position();
        if (bh.getType() == HitResult.Type.MISS) return groundBelow(level, end, 64);
        if (bh.getDirection() == Direction.UP) return bh.getLocation();
        Vec3 out = bh.getLocation().add(Vec3.atLowerCornerOf(bh.getDirection().getNormal()).scale(0.3));
        return groundBelow(level, out, 24);
    }

    /** Chefes (tag comum c:bosses ou vida máxima de chefe) resistem a arremessos. */
    public static boolean isBoss(Entity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES) || (e instanceof LivingEntity le && le.getMaxHealth() >= 150F);
    }

    // =====================================================================================
    //  Dano que ignora armadura (sem trocar o tipo de dano: abates continuam contando como magia)
    // =====================================================================================

    /**
     * Quanto dano "bruto" é preciso para que, depois da armadura do alvo, sobrem {@code wanted} pontos.
     * Mantém o tipo de dano mágico do núcleo (XP de abate por magia, Ao Abater, carga de ultimate) e só
     * compensa a redução da armadura (encantamentos de proteção continuam valendo).
     */
    public static float armorPierce(LivingEntity target, float wanted) {
        if (wanted <= 0) return wanted;
        float armor = target.getArmorValue();
        if (armor <= 0) return wanted;
        float tough = (float) target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float lo = wanted, hi = wanted * 6.0F;
        for (int i = 0; i < 24; i++) {
            float mid = (lo + hi) * 0.5F;
            if (afterArmor(mid, armor, tough) < wanted) lo = mid;
            else hi = mid;
        }
        return hi;
    }

    /** Fórmula de armadura do vanilla (CombatRules), sem os encantamentos da arma. */
    private static float afterArmor(float damage, float armor, float toughness) {
        float f = 2.0F + toughness / 4.0F;
        float r = Mth.clamp(armor - damage / f, armor * 0.2F, 20.0F) / 25.0F;
        return damage * (1.0F - r);
    }

    // =====================================================================================
    //  Apagar blocos (lendas)
    // =====================================================================================

    /**
     * As lendas podem mexer no terreno AGORA? v0.8: a regra é a do núcleo ({@link Destruction#allowed}: regra de jogo
     * {@code mahouSpellDestruction}, config, {@code mobGriefing} para mobs, modo de jogo, Provações).
     */
    public static boolean erasureEnabled(SpellContext ctx) {
        ServerLevel level = ctx.level();
        if (!Destruction.allowed(level, ctx.caster())) return false;
        if (level.dimension().location().equals(TRIAL_REALM)) return false;
        // Jogador em outra dimensão (magia deixada para trás): sem ele por perto, não mexe no terreno
        return !(ctx.caster() instanceof ServerPlayer sp) || (sp.level() == level && !sp.isRemoved());
    }

    /**
     * Este bloco pode ser apagado? Pula ar, líquidos (e blocos alagados), indestrutíveis, a tag
     * {@link #UNERASABLE}, blocos com dados (baús, máquinas, altares) e respeita proteções: jogador = evento de
     * quebra (mods de proteção, spawn, modo aventura); mob = regra de grief do NeoForge.
     * Chame {@link #erasureEnabled} antes (uma vez por golpe).
     */
    public static boolean canErase(SpellContext ctx, BlockPos pos, BlockState state) {
        ServerLevel level = ctx.level();
        if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) return false;
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos) || !level.isInWorldBounds(pos)) return false;
        if (state.getDestroySpeed(level, pos) < 0 || state.is(UNERASABLE)) return false;
        if (Destruction.protectedAt(level, pos, ctx.caster())) return false;
        LivingEntity caster = ctx.caster();
        if (caster instanceof ServerPlayer sp) {
            if (!sp.mayBuild() || !sp.mayInteract(level, pos)) return false;
            return !CommonHooks.fireBlockBreak(level, sp.gameMode.getGameModeForPlayer(), sp, pos, state).isCanceled();
        }
        if (caster != null) return CommonHooks.canEntityDestroy(level, pos, caster);
        return true;
    }

    /** Bloco que segura o conjurador (embaixo dos pés): a lenda nunca derruba quem a lançou. */
    private static boolean supportsCaster(SpellContext ctx, BlockPos pos) {
        LivingEntity c = ctx.caster();
        if (c == null || c.level() != ctx.level()) return false;
        AABB feet = c.getBoundingBox().inflate(0.8, 0, 0.8).expandTowards(0, -2.2, 0);
        return feet.intersects(new AABB(pos));
    }

    /**
     * Apaga (sem drops) os blocos dentro de uma esfera. {@code front} != null: só a metade da frente (a esfera
     * que avança já apagou a parte de trás). Retorna quantos blocos sumiram.
     */
    public static int eraseSphere(SpellContext ctx, Vec3 center, double radius, int max, @Nullable Vec3 front, @Nullable List<BlockPos> erased) {
        if (radius < 0.4) return 0;
        ServerLevel level = ctx.level();
        int r = Mth.ceil(radius);
        BlockPos c = BlockPos.containing(center);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        double r2 = radius * radius;
        int count = 0;
        for (int dy = r; dy >= -r; dy--) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    m.set(c.getX() + dx, c.getY() + dy, c.getZ() + dz);
                    double ox = m.getX() + 0.5 - center.x, oy = m.getY() + 0.5 - center.y, oz = m.getZ() + 0.5 - center.z;
                    if (ox * ox + oy * oy + oz * oz > r2) continue;
                    if (front != null && ox * front.x + oy * front.y + oz * front.z < -0.6) continue;
                    // Nunca ler bloco de chunk descarregado (getBlockState carregaria o chunk na hora)
                    if (!level.isLoaded(m)) continue;
                    BlockState state = level.getBlockState(m);
                    if (state.isAir() || supportsCaster(ctx, m) || !canErase(ctx, m, state)) continue;
                    level.setBlock(m, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    if (erased != null) erased.add(m.immutable());
                    if (++count >= max) return count;
                }
            }
        }
        return count;
    }

    /**
     * Planta/folhagem que um corte de vento derruba (grama, flores, folhas). Plantações ficam de fora (ninguém quer
     * a horta cortada por um Domínio). Respeita as mesmas proteções de {@link #canErase}.
     */
    public static boolean canCutFoliage(SpellContext ctx, BlockPos pos, BlockState state) {
        if (state.isAir() || state.is(BlockTags.CROPS)) return false;
        boolean soft = state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS);
        return soft && canErase(ctx, pos, state);
    }

    private LbUtil() {}
}
