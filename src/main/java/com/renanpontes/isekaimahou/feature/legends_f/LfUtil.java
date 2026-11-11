package com.renanpontes.isekaimahou.feature.legends_f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Utilitários de servidor das técnicas do legends_f. */
public final class LfUtil {

    /** Poder efetivo de um glifo (aumentos × conjuração × atributo de poder mágico). */
    public static float power(SpellContext ctx, SpellStats stats) {
        return Math.max(0.25F, stats.power() * ctx.powerMult() * ctx.casterSpellPower());
    }

    public static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** Desce de {@code p} até o primeiro bloco sólido ou líquido (até {@code depth} blocos). */
    public static Vec3 groundBelow(ServerLevel level, Vec3 p, double depth) {
        BlockHitResult r = level.clip(new ClipContext(p, p.subtract(0, depth, 0), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.ANY, CollisionContext.empty()));
        return r.getType() == HitResult.Type.MISS ? p : r.getLocation();
    }

    /** Jogador "falso" (pedestais, automação): não se transforma nem monta. */
    public static boolean isFake(@Nullable Entity e) {
        return e instanceof FakePlayer;
    }

    /**
     * Inimigo de verdade para efeitos de área (não fere bichos pacíficos à toa): monstros, chefes, jogadores não aliados
     * e qualquer criatura que esteja atacando você ou um aliado. Aldeões/NPCs só se atacarem.
     */
    public static boolean isFoe(SpellContext ctx, Entity e) {
        if (!(e instanceof LivingEntity le) || !le.isAlive() || le.isSpectator() || e instanceof ArmorStand) return false;
        LivingEntity c = ctx.caster();
        if (e == c || (ctx.casterId() != null && e.getUUID().equals(ctx.casterId())) || ctx.isAlly(e)) return false;
        if (e instanceof Player p) return !p.isCreative();
        boolean attacking = e instanceof Mob m && m.getTarget() != null && m.getTarget().isAlive()
                && (m.getTarget() == c || ctx.isAlly(m.getTarget()));
        if (e instanceof Npc) return attacking;
        return attacking || e instanceof Enemy || Statuses.isBossLike(le);
    }

    /** Inimigos numa esfera, mais perto primeiro. */
    public static List<LivingEntity> foesAround(SpellContext ctx, Vec3 c, double radius, int max) {
        List<LivingEntity> list = new ArrayList<>(ctx.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(c, c).inflate(radius), e -> isFoe(ctx, e) && e.distanceToSqr(c) <= radius * radius));
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(c)));
        return list.size() > max ? new ArrayList<>(list.subList(0, max)) : list;
    }

    /** Aliados (inclui quem conjurou) numa esfera. */
    public static List<LivingEntity> alliesAround(SpellContext ctx, Vec3 c, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : ctx.level().getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(radius),
                e -> e.isAlive() && !e.isSpectator() && e.distanceToSqr(c) <= radius * radius)) {
            if (e == ctx.caster() || ctx.isAlly(e)) out.add(e);
        }
        return out;
    }

    // =====================================================================================
    //  Recurso
    // =====================================================================================

    /**
     * Gasta {@code amount} de Mana/Vigor/Foco (número "antigo", escalado por {@link ResourceTraining#MANA_SCALE}).
     * Criativo não gasta. Falso se não houver o bastante.
     */
    public static boolean spend(ServerPlayer sp, float amount) {
        if (sp.getAbilities().instabuild) return true;
        return MagicApi.get(sp).consumeMana(amount * ResourceTraining.MANA_SCALE);
    }

    public static int circle(LivingEntity e) {
        return e instanceof ServerPlayer sp ? MagicApi.get(sp).circle() : 6;
    }

    public static int rank(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        return Math.max(0, d.counter("ritual_rank"));
    }

    // =====================================================================================
    //  Mensagens, sons e efeitos
    // =====================================================================================

    public static void actionbar(@Nullable Entity e, Component msg) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(msg, true);
    }

    public static void chat(@Nullable Entity e, Component msg) {
        if (e instanceof ServerPlayer sp) sp.sendSystemMessage(msg);
    }

    public static void sound(ServerLevel level, Vec3 p, SoundEvent s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    public static void sound(ServerLevel level, Vec3 p, Holder<SoundEvent> s, float vol, float pitch) {
        level.playSound(null, p.x, p.y, p.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    /** Letreiro grande com o nome da técnica (desenhado pelo módulo cinematic). */
    public static void banner(LivingEntity source, String key, int rgb, boolean ultimate) {
        Announce.send(source, Component.translatable(key), rgb, ultimate ? Announce.STYLE_ULTIMATE : Announce.STYLE_SPELL);
    }

    /** Efeito curto para quem está perto (até 96 blocos). */
    public static void fx(ServerLevel level, byte kind, Vec3 pos, Vec3 dir, int color, int life, float size) {
        PacketDistributor.sendToPlayersNear(level, null, pos.x, pos.y, pos.z, 96, new LfNet.Fx(kind, pos, dir, color, life, size));
    }

    public static void ring(ServerLevel level, Vec3 pos, int color, int life, float size) {
        fx(level, LfNet.RING, pos, new Vec3(0, 1, 0), color, life, size);
    }

    // =====================================================================================
    //  "Uma vez por conjuração"
    // =====================================================================================
    private static final WeakHashMap<SpellContext, Set<String>> ONCE = new WeakHashMap<>();
    private static final WeakHashMap<SpellContext, Long> ONCE_TICK = new WeakHashMap<>();

    /**
     * Verdadeiro só na primeira chamada de {@code key} para esta conjuração neste tick (formas de área chamam o efeito
     * uma vez por alvo; algumas técnicas — montar a vassoura, o letreiro — só podem acontecer uma vez).
     */
    public static boolean oncePerTick(SpellContext ctx, String key) {
        long now = ctx.level().getGameTime();
        Long last = ONCE_TICK.get(ctx);
        if (last == null || last != now) {
            ONCE_TICK.put(ctx, now);
            ONCE.put(ctx, new HashSet<>());
        }
        return ONCE.computeIfAbsent(ctx, k -> new HashSet<>()).add(key);
    }

    public static BlockPos below(Entity e) {
        return e.blockPosition().below();
    }

    private LfUtil() {}
}
