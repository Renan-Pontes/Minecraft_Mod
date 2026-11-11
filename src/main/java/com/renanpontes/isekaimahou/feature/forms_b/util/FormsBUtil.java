package com.renanpontes.isekaimahou.feature.forms_b.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MahouConfig;
import com.renanpontes.isekaimahou.glyph.LinkGlyph;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Utilidades compartilhadas pelas formas do módulo forms_b (mira, chão, alvos, reembolso, partículas). */
public final class FormsBUtil {
    /** Contextos que já foram reembolsados (Eco pode repetir a forma com o mesmo contexto). */
    private static final Set<SpellContext> REFUNDED = Collections.newSetFromMap(new WeakHashMap<>());
    /** Contextos em que a forma já saiu ao menos uma vez (Eco não reembolsa repetição que falhou). */
    private static final Set<SpellContext> SUCCEEDED = Collections.newSetFromMap(new WeakHashMap<>());

    // =====================================================================================
    //  Mira e chão (v0.7: tudo sai da origem/direção do ESTÁGIO, ver SpellContext#eyePos/look/anchor)
    // =====================================================================================

    /**
     * Direção da mira: a do estágio ({@code ctx.look()}). Numa conjuração "da mão" (sem origem própria), um mob
     * mira no alvo dele — o olhar de mob nem sempre aponta para o alvo, e a magia sairia torta.
     */
    public static Vec3 aimDir(SpellContext ctx) {
        if (!ctx.hasOrigin()) {
            LivingEntity t = mobTarget(ctx);
            if (t != null) {
                Vec3 d = t.getBoundingBox().getCenter().subtract(ctx.eyePos());
                if (d.lengthSqr() > 1.0E-6) return d.normalize();
            }
        }
        return ctx.look();
    }

    /** Alvo vivo do mob que conjurou (null para jogadores ou mob sem alvo). */
    public static LivingEntity mobTarget(SpellContext ctx) {
        if (ctx.caster() instanceof Mob m && m.getTarget() != null && m.getTarget().isAlive()
                && m.getTarget().level() == ctx.level()) {
            return m.getTarget();
        }
        return null;
    }

    /** Bloco para onde a magia mira, a partir da origem do estágio (ou null se não acertou nada até {@code maxDist}). */
    public static BlockHitResult aim(SpellContext ctx, double maxDist) {
        Vec3 eye = ctx.eyePos();
        Vec3 end = eye.add(aimDir(ctx).scale(maxDist));
        LivingEntity c = ctx.caster();
        CollisionContext cc = c != null ? CollisionContext.of(c) : CollisionContext.empty();
        BlockHitResult hit = ctx.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, cc));
        return hit.getType() == HitResult.Type.BLOCK ? hit : null;
    }

    /**
     * Ponto no CHÃO onde uma forma de área nasce:
     * <ul>
     *   <li>estágio nascido de um impacto ({@code ctx.anchor()}): o chão no ponto atingido;</li>
     *   <li>mob sem origem própria: o chão sob o alvo dele (até {@code maxDist});</li>
     *   <li>senão, para onde a magia mira (até {@code maxDist}). Mirando numa parede, o chão logo à frente dela;
     *       no céu, o chão embaixo do ponto final. Sem chão: os pés do conjurador (ou a origem do estágio).</li>
     * </ul>
     * Nunca devolve null.
     */
    public static Vec3 aimGround(SpellContext ctx, double maxDist) {
        Vec3 anchor = ctx.anchor();
        if (anchor != null) return groundAtAnchor(ctx, anchor);
        Level level = ctx.level();
        LivingEntity c = ctx.caster();
        Vec3 eye = ctx.eyePos();
        if (!ctx.hasOrigin()) {
            LivingEntity t = mobTarget(ctx);
            if (t != null && t.position().distanceToSqr(eye) <= maxDist * maxDist) {
                Vec3 g = groundBelow(level, t.position().add(0, 0.5, 0), 8);
                if (g != null) return g;
            }
        }
        BlockHitResult hit = aim(ctx, maxDist);
        Vec3 probe;
        if (hit != null) {
            if (hit.getDirection() == Direction.UP) return hit.getLocation();
            // Parede ou teto: recua um pouco na direção da face para ficar no espaço livre
            Vec3 n = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
            probe = hit.getLocation().add(n.scale(0.4));
        } else {
            probe = eye.add(aimDir(ctx).scale(maxDist));
        }
        Vec3 g = groundBelow(level, probe, 24);
        if (g != null) return g;
        return c != null && !ctx.hasOrigin() ? c.position() : eye;
    }

    /**
     * Chão do ponto de nascimento de um estágio (Ao Acertar/Terminar/Abater/Saltar), ou null se o contexto não tem
     * origem própria (conjuração normal: cada forma decide).
     */
    public static Vec3 stageGround(SpellContext ctx) {
        if (ctx.anchor() != null) return groundAtAnchor(ctx, ctx.anchor());
        if (!ctx.hasOrigin()) return null;
        Vec3 g = groundBelow(ctx.level(), ctx.eyePos(), 24);
        return g != null ? g : ctx.eyePos();
    }

    /**
     * Desce do ponto de impacto até o chão. O ponto pode estar na face de um bloco (parede, teto, chão): se cair
     * dentro de um bloco sólido, recua contra a direção do estágio e, se preciso, sobe — para não nascer em cima
     * da parede atingida.
     */
    public static Vec3 groundAtAnchor(SpellContext ctx, Vec3 anchor) {
        Level level = ctx.level();
        Vec3 p = anchor;
        if (solidAt(level, p)) p = anchor.subtract(ctx.look().scale(0.45));
        if (solidAt(level, p)) p = p.add(0, 1.0, 0);
        Vec3 g = groundBelow(level, p.add(0, 0.25, 0), 24);
        return g != null ? g : p;
    }

    private static boolean solidAt(Level level, Vec3 p) {
        BlockPos pos = BlockPos.containing(p);
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /** Ângulo horizontal (yaw do Minecraft) que aponta na direção {@code dir}. */
    public static float yawOf(Vec3 dir) {
        return (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** Primeiro chão sólido de {@code from} para baixo (no máximo {@code maxDown} blocos). */
    public static Vec3 groundBelow(Level level, Vec3 from, int maxDown) {
        BlockPos.MutableBlockPos pos = BlockPos.containing(from).mutable();
        for (int i = 0; i <= maxDown; i++) {
            BlockState st = level.getBlockState(pos);
            VoxelShape shape = st.getCollisionShape(level, pos, CollisionContext.empty());
            if (!shape.isEmpty()) {
                double top = pos.getY() + shape.max(Direction.Axis.Y);
                return new Vec3(from.x, top, from.z);
            }
            pos.move(Direction.DOWN);
            if (pos.getY() < level.getMinBuildHeight()) break;
        }
        return null;
    }

    /** Há espaço livre (sem colisão e sem lava) para uma criatura de 2 blocos de altura neste bloco? */
    public static boolean hasHeadroom(Level level, BlockPos feet) {
        return level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                && !level.getFluidState(feet).is(FluidTags.LAVA) && !level.getFluidState(feet.above()).is(FluidTags.LAVA);
    }

    /** Linha de visão livre entre dois pontos (blocos com colisão bloqueiam). */
    public static boolean canSee(Level level, Vec3 from, Vec3 to) {
        return level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                CollisionContext.empty())).getType() == HitResult.Type.MISS;
    }

    // =====================================================================================
    //  Alvos
    // =====================================================================================

    /**
     * Uma "ameaça" para formas automáticas (torre, invocação, órbita, runa): monstros (se quem conjurou
     * não é monstro), quem mira no conjurador/aliados, quem o conjurador atacou ou o atacou, e jogadores
     * inimigos (PvP, ou qualquer jogador se quem conjurou é um monstro). Animais que ninguém provocou
     * não são atacados sozinhos.
     */
    public static boolean isThreat(SpellContext ctx, LivingEntity e) {
        if (e == null || !e.isAlive() || e.isSpectator() || e instanceof ArmorStand || ctx.isAlly(e)) return false;
        if (e instanceof Player p) return !p.isCreative();
        LivingEntity caster = ctx.caster();
        if (caster != null) {
            if (e == caster.getLastHurtByMob() || e == caster.getLastHurtMob()) return true;
            if (caster instanceof Mob m && m.getTarget() == e) return true;
        }
        if (e instanceof Enemy) return !(caster instanceof Enemy);
        return e instanceof Mob m && m.getTarget() != null && ctx.isAlly(m.getTarget());
    }

    /** A ameaça mais próxima de {@code center} até {@code range}, opcionalmente exigindo linha de visão. */
    public static LivingEntity nearestThreat(SpellContext ctx, Vec3 center, double range, boolean needSight) {
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity e : ctx.enemiesAround(center, range)) {
            if (!isThreat(ctx, e)) continue;
            double d = e.distanceToSqr(center);
            if (d >= bestD) continue;
            if (needSight && !canSee(ctx.level(), center, e.getBoundingBox().getCenter())) continue;
            bestD = d;
            best = e;
        }
        return best;
    }

    /** Todos os efeitos da magia são benéficos (cura, escudo...) e nenhum foi invertido? */
    public static boolean onlyBeneficial(SpellContext ctx) {
        if (ctx.spell().effects().isEmpty()) return false;
        for (ParsedSpell.EffectEntry e : ctx.spell().effects()) {
            if (!e.effect().isBeneficial() || e.stats().inverted || e.stats().enemiesOnly) return false;
        }
        return true;
    }

    /** A magia tem ao menos um efeito benéfico (cura, escudo...) que não foi invertido? */
    public static boolean anyBeneficial(SpellContext ctx) {
        for (ParsedSpell.EffectEntry e : ctx.spell().effects()) {
            if (e.effect().isBeneficial() && !e.stats().inverted) return true;
        }
        return false;
    }

    /**
     * Aplica no PRÓPRIO conjurador só os efeitos benéficos (cura/escudo): quem fica dentro da sua
     * zona/aura/pulso de cura também é curado. {@link SpellContext#applyEffect} cuida de Atraso/Repetir
     * e usa o elemento do glifo em magias multi-elemento.
     */
    public static void applyBeneficialToCaster(SpellContext ctx) {
        LivingEntity c = ctx.caster();
        if (c == null || !c.isAlive()) return;
        if (ctx.formStats().enemiesOnly) return;
        for (ParsedSpell.EffectEntry entry : ctx.spell().effects()) {
            SpellStats st = entry.stats();
            if (!entry.effect().isBeneficial() || st.inverted || st.enemiesOnly) continue;
            ctx.applyEffect(entry, c);
        }
    }

    // =====================================================================================
    //  Magias programáveis (estágios)
    // =====================================================================================

    /** Este estágio sai por Ao Acertar ou Saltar (vale a pena procurar quem foi atingido para avisar o núcleo). */
    public static boolean wantsImpact(SpellContext ctx) {
        LinkGlyph.LinkKind k = ctx.spell().linkKind();
        return k == LinkGlyph.LinkKind.HIT || k == LinkGlyph.LinkKind.LEAP;
    }

    /**
     * Quem uma forma de ÁREA "acertou de verdade" num disparo (para {@code ctx.impact}): o inimigo mais próximo do
     * centro — ou, numa magia só de cura/proteção, o aliado mais próximo (nunca o próprio conjurador).
     * Formas de área não filtram {@code ctx.ignores(e)}: atingir de novo quem está no ponto de impacto é o objetivo.
     * @param exclude entidade que nunca conta (a própria forma, ex.: o clone da Ilusão), ou null
     */
    public static LivingEntity areaHit(SpellContext ctx, Vec3 center, double radius, Entity exclude) {
        boolean support = onlyBeneficial(ctx);
        LivingEntity caster = ctx.caster();
        LivingEntity best = null;
        double bestD = radius * radius;
        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e != caster && e != exclude)) {
            if (!countsAsHit(ctx, e, support)) continue;
            double d = e.getBoundingBox().getCenter().distanceToSqr(center);
            if (d > bestD) continue;
            bestD = d;
            best = e;
        }
        return best;
    }

    /**
     * Tocar {@code e} conta como "acerto de verdade" para Ao Acertar/Saltar? Inimigos sim; aliados só numa magia de
     * cura/proteção ({@code support}, ver {@link #onlyBeneficial}). Um aliado que encosta numa muralha de dano não
     * dispara o próximo estágio.
     */
    public static boolean countsAsHit(SpellContext ctx, Entity e, boolean support) {
        if (e == null || e == ctx.caster() || (ctx.casterId() != null && ctx.casterId().equals(e.getUUID()))) return false;
        return support == ctx.isAlly(e);
    }

    /**
     * Avisa o núcleo de um impacto real numa entidade (dispara Ao Acertar / Saltar, se houver). Uma zona/runa que
     * sobreviveu ao conjurador (mob morto) continua ferindo, mas não gera estágios novos sem ele.
     */
    public static void impactOn(SpellContext ctx, Entity hit) {
        LivingEntity c = ctx.caster();
        if (c == null || !c.isAlive()) return;
        ctx.impact(hit.getBoundingBox().getCenter(), ctx.look(), hit);
    }

    /**
     * Elementos da magia inteira para o visual (o primário primeiro). Magias multi-elemento mostram todos;
     * as de um elemento só, o próprio.
     */
    public static List<Element> spellElements(SpellContext ctx) {
        List<Element> out = new ArrayList<>();
        Element primary = ctx.primaryElement();
        out.add(primary);
        for (Element e : ctx.rootSpell().elements()) if (e != primary) out.add(e);
        return out;
    }

    /** Cor de destaque: a do 2º elemento (multi-elemento sem cor escolhida) ou a cor da magia clareada. */
    public static int accentColor(SpellContext ctx) {
        int main = ctx.color() & 0xFFFFFF;
        List<Element> els = spellElements(ctx);
        if (ctx.recipe().color() < 0 && els.size() > 1) return els.get(1).color & 0xFFFFFF;
        int r = (main >> 16) & 255, g = (main >> 8) & 255, b = main & 255;
        r += (255 - r) * 35 / 100;
        g += (255 - g) * 35 / 100;
        b += (255 - b) * 35 / 100;
        return (r << 16) | (g << 8) | b;
    }

    /** Entidade está dentro de uma esfera (pelos pés ou pelo centro). */
    public static boolean inside(Entity e, Vec3 center, double radius) {
        double r2 = radius * radius;
        return e.position().distanceToSqr(center) <= r2 || e.getBoundingBox().getCenter().distanceToSqr(center) <= r2;
    }

    // =====================================================================================
    //  Falha depois de pagar
    // =====================================================================================

    /** Avisa o jogador (barra de ação) e devolve o mana se a forma não conseguiu sair. */
    public static void fail(SpellContext ctx, String key) {
        ServerPlayer sp = ctx.casterPlayer();
        if (sp == null) return;
        sp.displayClientMessage(Component.translatable(key).withStyle(ChatFormatting.RED), true);
        refund(ctx);
    }

    /**
     * A forma saiu com sucesso neste contexto. Com Eco a forma roda de novo com o mesmo contexto: se uma
     * repetição falhar depois de um sucesso, não devolve o mana (a magia já valeu).
     */
    public static void succeeded(SpellContext ctx) {
        SUCCEEDED.add(ctx);
    }

    /** Devolve o mana gasto (só jogador fora do criativo, sem Sobrecarga e uma vez por contexto). */
    public static void refund(SpellContext ctx) {
        ServerPlayer sp = ctx.casterPlayer();
        if (sp == null || sp.getAbilities().instabuild || SUCCEEDED.contains(ctx) || !REFUNDED.add(ctx)) return;
        ParsedSpell s = ctx.spell();
        boolean overload = s.formStats().overload || s.effects().stream().anyMatch(e -> e.stats().overload);
        if (overload) return;
        MagicApi.get(sp).addMana(s.manaCost());
    }

    // =====================================================================================
    //  Visual
    // =====================================================================================

    /** Partícula de pó com uma cor RGB qualquer (a cor escolhida para a magia). */
    public static DustParticleOptions dust(int rgb, float size) {
        return new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F,
                (rgb & 255) / 255F), size);
    }

    /** Configuração de cliente "reduzir partículas" (falso se a config não estiver carregada). */
    public static boolean reducedParticles() {
        try {
            return MahouConfig.REDUCED_PARTICLES.get();
        } catch (RuntimeException e) {
            return false;
        }
    }

    /** Limite de magias persistentes por jogador (6 se a config ainda não carregou). */
    public static int maxPersistent() {
        try {
            return MahouConfig.MAX_PERSISTENT_SPELLS.get();
        } catch (RuntimeException e) {
            return 6;
        }
    }

    private FormsBUtil() {}
}
