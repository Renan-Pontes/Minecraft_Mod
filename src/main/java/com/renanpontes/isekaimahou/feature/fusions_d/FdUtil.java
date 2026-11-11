package com.renanpontes.isekaimahou.feature.fusions_d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.core.stack.DamageCapture;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Utilidades das Misturas D: deduplicação de áreas, mira, chão, poder e dano com tipo próprio. */
public final class FdUtil {
    /** Aumentos neutros (poder 1) para danos já multiplicados. */
    public static final SpellStats PLAIN = new SpellStats();

    /** "conjurador|chave" → tick: uma área por conjuração, mesmo quando a forma acerta vários alvos no mesmo tick. */
    private static final Map<String, Long> ONCE = new HashMap<>();

    /**
     * true só na 1ª chamada deste tick para (conjurador, chave). Chaveado pelo conjurador (não pelo contexto),
     * porque efeitos de outro elemento rodam numa "visão" nova do contexto a cada alvo.
     */
    public static boolean once(SpellContext ctx, String key) {
        long now = ctx.level().getGameTime();
        if (ONCE.size() > 256) ONCE.values().removeIf(t -> t < now - 1);
        String k = ctx.casterId() + "|" + key;
        Long last = ONCE.get(k);
        if (last != null && last == now) return false;
        ONCE.put(k, now);
        return true;
    }

    /** "conjurador|chave" → último tick em que a manifestação pesada nasceu (ver {@link #ready}). */
    private static final Map<String, Long> LAST = new HashMap<>();

    /**
     * Intervalo mínimo entre manifestações pesadas do MESMO conjurador (Ragnarök, Chuva de Estrelas...): uma zona ou
     * torre que reaplica o efeito a cada pulso não empilha dezenas de ondas de pilares ao mesmo tempo.
     */
    public static boolean ready(SpellContext ctx, String key, int interval) {
        if (interval <= 0) return true;
        long now = ctx.level().getGameTime();
        if (LAST.size() > 512) LAST.values().removeIf(t -> t < now - 1200);
        String k = ctx.casterId() + "|" + key;
        Long last = LAST.get(k);
        if (last != null && now - last < interval && now >= last) {
            busy(ctx, key, now, interval - (now - last));
            return false;
        }
        LAST.put(k, now);
        return true;
    }

    /** "jogador|chave" → último aviso de "ainda se manifestando" (no máx. um a cada 3 s). */
    private static final Map<String, Long> WARNED = new HashMap<>();

    /** Avisa o jogador (barra de ação) que a manifestação anterior ainda está ativa — não some em silêncio. */
    private static void busy(SpellContext ctx, String key, long now, long left) {
        ServerPlayer sp = ctx.casterPlayer();
        if (sp == null) return;
        if (WARNED.size() > 256) WARNED.values().removeIf(t -> t < now - 60);
        String k = ctx.casterId() + "|" + key;
        Long last = WARNED.get(k);
        if (last != null && now - last < 60 && now >= last) return;
        WARNED.put(k, now);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.fd_busy",
                glyphName(key),
                String.format(java.util.Locale.ROOT, "%.1f", Math.max(0.1, left / 20.0))).withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    /** Nome traduzido do glifo (pelo registro; o id cru se não existir). */
    private static Component glyphName(String key) {
        Glyph g = GlyphRegistry.get(IsekaiMahou.id(key));
        return g != null ? g.displayName() : Component.literal(key);
    }

    public static void clear() {
        ONCE.clear();
        LAST.clear();
        WARNED.clear();
    }

    public static boolean isSelf(SpellContext ctx, Entity target) {
        return ctx.caster() != null && target == ctx.caster();
    }

    /**
     * Si Mesmo chama o efeito no conjurador e logo depois no bloco sob os pés: esse 2º acerto é só "eco" e é
     * ignorado (a área já nasceu onde o conjurador olha).
     */
    public static boolean isSelfFeetEcho(SpellContext ctx, BlockHitResult hit) {
        LivingEntity c = ctx.caster();
        return c != null && Vec3.atCenterOf(hit.getBlockPos()).distanceToSqr(c.position()) < 2.5 * 2.5;
    }

    /** Poder total (aumentos × conjuração × poder mágico) para o que não passa por {@code ctx.damage}. */
    public static float power(SpellContext ctx, SpellStats stats) {
        return stats.power() * ctx.powerMult() * ctx.casterSpellPower();
    }

    /** Aumentos do golpe de entidades (Crítico/Vampírico; poder 1 porque o dano guardado já vem multiplicado). */
    public static SpellStats hitStats(SpellStats stats) {
        if (!stats.crit && !stats.vampiric) return PLAIN;
        SpellStats s = new SpellStats();
        s.crit = stats.crit;
        s.vampiric = stats.vampiric;
        return s;
    }

    /** Ponto mirado: onde o olhar do estágio bate num bloco (ou o fim do alcance). */
    public static Vec3 lookPoint(SpellContext ctx, double range) {
        Vec3 eye = ctx.eyePos();
        Vec3 end = eye.add(ctx.look().scale(range));
        BlockHitResult hit = ctx.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                ctx.caster()));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation().subtract(ctx.look().scale(0.4));
    }

    /** Onde nasce uma magia de área aplicada a {@code target}: no alvo, ou — em Si Mesmo — onde você olha. */
    public static Vec3 areaPoint(SpellContext ctx, Entity target, double selfRange) {
        if (isSelf(ctx, target)) return lookPoint(ctx, selfRange);
        return target.position();
    }

    /** Chão firme abaixo (até {@code maxDown}); se o ponto estiver dentro de um bloco, sobe até achar ar. */
    public static Vec3 ground(Level level, Vec3 pos, int maxDown) {
        BlockPos p = BlockPos.containing(pos);
        for (int i = 0; i < 4 && !level.getBlockState(p).getCollisionShape(level, p).isEmpty(); i++) p = p.above();
        for (int i = 0; i <= maxDown; i++) {
            BlockPos below = p.below();
            if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty()) return new Vec3(pos.x, p.getY(), pos.z);
            p = below;
        }
        return pos;
    }

    /** Bloco natural/sólido bom para virar destroço (sem dados, com modelo, não líquido). */
    public static boolean rubbleable(BlockState s) {
        return !s.isAir() && s.getRenderShape() == RenderShape.MODEL && !s.hasBlockEntity() && s.getFluidState().isEmpty();
    }

    /** O bloco mais alto (sólido) da coluna, procurando de y+span até y-span. */
    @Nullable
    public static BlockPos surface(Level level, int x, int z, int yTop, int yBottom) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, yTop, z);
        for (int y = yTop; y >= yBottom; y--) {
            m.setY(y);
            if (!level.isLoaded(m)) return null;
            BlockState s = level.getBlockState(m);
            if (rubbleable(s) && !s.getCollisionShape(level, m).isEmpty()) return m.immutable();
        }
        return null;
    }

    public static boolean untouchable(Entity e) {
        return e instanceof Player p && (p.isCreative() || p.isSpectator());
    }

    public static boolean isBoss(Entity e) {
        return BossKit.isBossLike(e);
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    public static void setMotion(Entity e, Vec3 motion) {
        e.setDeltaMovement(motion);
        e.hasImpulse = true;
        e.hurtMarked = true;
    }

    /** Inimigos vivos e "tocáveis" numa esfera (sem criativo/espectador). */
    public static List<LivingEntity> enemies(SpellContext ctx, Vec3 at, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : ctx.enemiesAround(at, radius)) if (!untouchable(e) && !e.isSpectator()) out.add(e);
        return out;
    }

    public static void forgetTarget(LivingEntity e) {
        if (e instanceof Mob m) {
            m.setTarget(null);
            m.getNavigation().stop();
        }
    }

    /**
     * Dano mágico com tipo de dano próprio (o Corte Dimensional ignora armadura), mantendo poder, crítico,
     * reações, Vampírico, crédito e carga de ultimate do conjurador.
     */
    public static float typedSpellDamage(SpellContext ctx, LivingEntity target, float base, SpellStats stats,
                                         ResourceKey<DamageType> type, Element el, @Nullable Entity direct) {
        ServerLevel level = ctx.level();
        float amount = base * stats.power() * ctx.powerMult() * ctx.casterSpellPower();
        if (stats.crit && level.random.nextFloat() < 0.25F) {
            amount *= 1.75F;
            level.sendParticles(ParticleTypes.CRIT, target.getX(), target.getY() + target.getBbHeight() / 2, target.getZ(),
                    12, 0.3, 0.3, 0.3, 0.2);
        }
        amount = ElementReactions.onSpellHit(ctx, target, el, amount);
        ElementReactions.mark(target, el, ctx.casterId(), level.getGameTime());
        if (amount <= 0) return 0;
        LivingEntity caster = ctx.caster();
        var holder = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type);
        DamageSource src = new DamageSource(holder, direct != null ? direct : caster, caster);
        // Mesmos eventos do ctx.damage (Infinito, Toque Mortal, Atropelar... enxergam o golpe)
        float requested = amount;
        SpellDamageEvent.Pre pre = new SpellDamageEvent.Pre(ctx, target, el, stats, direct, src, amount);
        if (NeoForge.EVENT_BUS.post(pre).isCanceled()) return 0;
        amount = pre.getAmount();
        if (amount != requested && !(amount > 0F)) return 0;
        float before = target.getHealth();
        boolean wasAlive = target.isAlive() && !target.isDeadOrDying();
        target.invulnerableTime = 0;
        boolean hurt;
        float applied;
        DamageCapture.begin(target);
        try {
            hurt = target.hurt(src, amount);
        } finally {
            applied = DamageCapture.end(target);
        }
        float dealt = 0F, overkill = 0F;
        boolean killed = false;
        if (hurt) {
            dealt = Math.max(0, before - target.getHealth());
            killed = wasAlive && target.isDeadOrDying();
            if (killed) overkill = Math.max(0F, (applied >= 0F ? applied : dealt) - before);
            if (stats.vampiric && caster != null) caster.heal(dealt * 0.25F);
            ServerPlayer sp = ctx.casterPlayer();
            if (sp != null) MagicApi.onSpellDamage(sp, ctx.primaryElement(), dealt);
        }
        NeoForge.EVENT_BUS.post(new SpellDamageEvent.Post(ctx, target, el, stats, direct, src, amount, hurt, dealt, overkill, killed));
        return amount;
    }

    /**
     * Abate pelo Corte Dimensional (tipo de dano próprio, para ignorar armadura): o núcleo só reconhece "abate por
     * magia" pelo tipo isekai_mahou:spell, então aqui completamos o que faltaria — contador do elemento, XP de
     * elemento e a diferença de XP entre abate comum e abate por magia (só para quem matou).
     */
    public static void onDeath(LivingDeathEvent event) {
        DamageSource src = event.getSource();
        if (!src.is(FdRegistries.DIMENSION_SLASH_DAMAGE)) return;
        if (!(src.getEntity() instanceof ServerPlayer player) || player instanceof FakePlayer) return;
        LivingEntity victim = event.getEntity();
        if (victim instanceof Player) return;
        Element el = ElementReactions.lastElement(victim, victim.level().getGameTime(), 100);
        if (el == null) el = Element.VOID;
        float hp = victim.getMaxHealth();
        MagicApi.addCounter(player, "kill.spell." + el.id, 1);
        MagicApi.giveElementXp(player, el, Progression.killElementXp(hp));
        int extra = Progression.killXp(hp, true) - Progression.killXp(hp, false);
        if (extra > 0) MagicApi.giveXp(player, extra, "kill");
    }

    /** Dono de uma entidade de magia (pode estar descarregado). */
    @Nullable
    public static LivingEntity owner(ServerLevel level, @Nullable UUID id) {
        return id != null && level.getEntity(id) instanceof LivingEntity le ? le : null;
    }

    private FdUtil() {}
}
