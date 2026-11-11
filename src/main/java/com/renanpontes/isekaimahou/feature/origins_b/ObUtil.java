package com.renanpontes.isekaimahou.feature.origins_b;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.feature.origins_b.entity.ObSummonEntity;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;

/** Utilidades do módulo: aliados de um dono, dano "solto" de magia, travas por tick, lugares livres e limites de invocação. */
public final class ObUtil {
    /** Dados do módulo em getPersistentData(). */
    public static final String DATA = "isekai_mahou_ob";

    private ObUtil() {}

    // ------------------------------------------------------------------ poder
    /** Poder final de um efeito (aumentos × conjuração × atributo spell_power). */
    public static float power(SpellContext ctx, SpellStats stats) {
        return stats.power() * ctx.powerMult() * ctx.casterSpellPower();
    }

    // ------------------------------------------------------------------ aliados
    /** Mesma regra do SpellContext.isAlly, para coisas que vivem além da conjuração (invocações, marcas). */
    public static boolean isAlly(ServerLevel level, UUID owner, Entity e) {
        if (owner == null || e == null) return false;
        if (owner.equals(e.getUUID())) return true;
        if (e instanceof OwnableEntity own && owner.equals(own.getOwnerUUID())) return true;
        Entity o = level.getEntity(owner);
        if (o != null && o.isAlliedTo(e)) return true;
        if (e instanceof Player) {
            // Dono jogador (ou que saiu do mundo): outros jogadores são aliados se o PvP estiver desligado
            boolean ownerIsPlayer = o == null || o instanceof Player;
            return ownerIsPlayer && !level.getServer().isPvpAllowed();
        }
        return false;
    }

    /** Entidade do dono: jogador em qualquer lugar deste nível, ou entidade carregada nele. */
    public static LivingEntity owner(ServerLevel level, UUID owner) {
        if (owner == null) return null;
        if (level.getEntity(owner) instanceof LivingEntity le) return le;
        Player p = level.getServer().getPlayerList().getPlayer(owner);
        return p != null && p.level() == level ? p : null;
    }

    public static boolean isUndead(Entity e) {
        return e.getType().is(EntityTypeTags.UNDEAD);
    }

    public static boolean isBoss(Entity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES);
    }

    // ------------------------------------------------------------------ dano
    public static DamageSource spellSource(ServerLevel level, Entity direct, Entity causing) {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CoreRegistries.SPELL_DAMAGE);
        return new DamageSource(type, direct != null ? direct : causing, causing);
    }

    /** Dano de magia fora de um SpellContext (invocações): marca o elemento para o XP de abate do dono. */
    public static boolean spellHurt(ServerLevel level, LivingEntity target, float amount, Entity direct, LivingEntity owner, Element el) {
        if (amount <= 0) return false;
        ElementReactions.mark(target, el, owner != null ? owner.getUUID() : null, level.getGameTime());
        Entity causing = owner != null ? owner : direct;
        DamageSource src = causing != null ? spellSource(level, direct, causing) : level.damageSources().magic();
        return target.hurt(src, amount);
    }

    // ------------------------------------------------------------------ alvo do dono
    private record Attack(UUID target, long time) {}

    private static final Map<UUID, Attack> LAST_ATTACK = new HashMap<>();

    /** Guarda quem um jogador feriu por último (magias não preenchem getLastHurtMob, e as invocações precisam saber). */
    public static void recordAttack(LivingEntity attacker, LivingEntity victim) {
        long now = attacker.level().getGameTime();
        LAST_ATTACK.put(attacker.getUUID(), new Attack(victim.getUUID(), now));
        if (LAST_ATTACK.size() > 512) LAST_ATTACK.values().removeIf(a -> a.time() < now - 1200);
    }

    /** Último alvo atingido pelo dono (por qualquer meio) nos últimos {@code maxAge} ticks, ou null. */
    public static LivingEntity lastAttacked(ServerLevel level, LivingEntity owner, int maxAge) {
        Attack a = LAST_ATTACK.get(owner.getUUID());
        if (a == null || level.getGameTime() - a.time() > maxAge) return null;
        return level.getEntity(a.target()) instanceof LivingEntity le && le.isAlive() ? le : null;
    }

    public static long lastAttackTime(LivingEntity owner) {
        Attack a = LAST_ATTACK.get(owner.getUUID());
        return a == null ? -1 : a.time();
    }

    // ------------------------------------------------------------------ travas
    private static final Map<String, Long> ONCE = new HashMap<>();

    /** true só na primeira vez no tick para (conjurador, chave): uma magia em área não invoca 10 vezes no mesmo instante. */
    public static boolean oncePerTick(SpellContext ctx, String key) {
        long now = ctx.level().getGameTime();
        String k = (ctx.casterId() != null ? ctx.casterId().toString() : "none") + "/" + key;
        Long prev = ONCE.put(k, now);
        if (ONCE.size() > 256) ONCE.values().removeIf(t -> t < now - 40);
        return prev == null || prev != now;
    }

    // ------------------------------------------------------------------ invocações
    /** Um ponto perto de {@code near} onde a entidade cabe (tenta subir e espalhar um pouco). */
    public static Vec3 freeSpot(ServerLevel level, EntityType<?> type, Vec3 near, double spread) {
        for (int i = 0; i < 14; i++) {
            double ox = i == 0 ? 0 : (level.random.nextDouble() - 0.5) * spread * 2;
            double oz = i == 0 ? 0 : (level.random.nextDouble() - 0.5) * spread * 2;
            for (int dy = 0; dy <= 2; dy++) {
                Vec3 p = near.add(ox, dy, oz);
                if (level.noCollision(type.getSpawnAABB(p.x, p.y, p.z))) return p;
            }
        }
        return near;
    }

    /** Invocações vivas de um dono (de uma classe), da mais nova para a mais antiga. */
    public static <T extends ObSummonEntity> List<T> summonsOf(ServerLevel level, Class<T> cls, UUID owner, Vec3 near) {
        List<T> list = new ArrayList<>(level.getEntitiesOfClass(cls, new AABB(near, near).inflate(160),
                e -> e.isAlive() && owner.equals(e.getOwnerUUID())));
        list.sort(Comparator.comparingInt((T e) -> e.tickCount));
        return list;
    }

    /**
     * Abre espaço para {@code adding} novas invocações respeitando {@code max}: as temporárias mais antigas se desfazem.
     * As permanentes (presentes do Rito Ossuário) nunca são desfeitas. Retorna quantas cabem agora.
     */
    public static <T extends ObSummonEntity> int makeRoom(ServerLevel level, Class<T> cls, UUID owner, Vec3 near, int max, int adding) {
        List<T> mine = summonsOf(level, cls, owner, near);
        int free = max - mine.size();
        for (int i = mine.size() - 1; i >= 0 && free < adding; i--) {
            T e = mine.get(i);
            if (!e.isTemporary()) continue;
            e.dissolve();
            free++;
        }
        return Math.max(0, Math.min(adding, free));
    }

    // ------------------------------------------------------------------ dados persistentes e mensagens
    public static CompoundTag data(Entity e) {
        CompoundTag root = e.getPersistentData();
        if (!root.contains(DATA)) root.put(DATA, new CompoundTag());
        return root.getCompound(DATA);
    }

    public static boolean hasData(Entity e) {
        return e.getPersistentData().contains(DATA);
    }

    /**
     * Planar (mobs com Asas/Forma de Lich): limita a queda e zera a distância de queda. Feito direto no movimento
     * porque adicionar Queda Lenta dentro do tick de um efeito modificaria o mapa de efeitos durante a iteração.
     */
    public static void glide(LivingEntity e) {
        Vec3 v = e.getDeltaMovement();
        if (v.y < -0.12) e.setDeltaMovement(v.x, -0.12, v.z);
        e.resetFallDistance();
    }

    /** Mensagem curta na barra de ação (só jogadores). */
    public static void actionbar(Entity e, Component msg) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(msg, true);
    }
}
