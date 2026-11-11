package com.renanpontes.isekaimahou.feature.light_shadow;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.tags.TagKey;
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

/** Utilidades do módulo Luz/Sombra: quem é "profano", aliados de um dono, dados persistentes e trava por tick. */
public final class LsUtil {
    /** Demônios, sombras e afins (além dos mortos-vivos). Tag juntada pelo merge: data/isekai_mahou/tags/entity_type/unholy.json. */
    public static final TagKey<EntityType<?>> UNHOLY = TagKey.create(Registries.ENTITY_TYPE, IsekaiMahou.id("unholy"));

    /** Chave do compound com os dados do módulo em getPersistentData(). */
    public static final String DATA = "isekai_mahou_ls";

    public static boolean isUndead(Entity e) {
        return e.getType().is(EntityTypeTags.UNDEAD);
    }

    /** Mortos-vivos, demônios e sombras: sofrem com a Luz. */
    public static boolean isUnholy(Entity e) {
        return isUndead(e) || e.getType().is(UNHOLY);
    }

    public static boolean isBoss(Entity e) {
        return e.getType().is(Tags.EntityTypes.BOSSES);
    }

    // ------------------------------------------------------------------ dados persistentes
    public static CompoundTag data(Entity e) {
        CompoundTag root = e.getPersistentData();
        if (!root.contains(DATA)) root.put(DATA, new CompoundTag());
        return root.getCompound(DATA);
    }

    public static boolean hasData(Entity e) {
        return e.getPersistentData().contains(DATA);
    }

    /** UUID de quem possuiu este mob (ou null). Só vale enquanto o efeito Possuído estiver ativo. */
    public static UUID possessor(Entity e) {
        if (!(e instanceof LivingEntity le) || !hasData(e) || !le.hasEffect(LsRegistries.SHADOW_POSSESSION)) return null;
        CompoundTag d = e.getPersistentData().getCompound(DATA);
        return d.hasUUID("possessor") ? d.getUUID("possessor") : null;
    }

    // ------------------------------------------------------------------ aliados
    /**
     * Aliado de um "dono" (mesma regra do SpellContext.isAlly), para entidades que vivem além da conjuração
     * (invocações, zonas, efeitos). Também conta mobs possuídos por esse dono.
     */
    public static boolean isAlly(ServerLevel level, UUID owner, Entity e) {
        if (owner == null || e == null) return false;
        if (owner.equals(e.getUUID())) return true;
        if (e instanceof OwnableEntity own && owner.equals(own.getOwnerUUID())) return true;
        if (owner.equals(possessor(e))) return true;
        Entity o = level.getEntity(owner);
        if (o != null && o.isAlliedTo(e)) return true;
        if (e instanceof Player) {
            // Dono que é jogador (ou que saiu do mundo): outros jogadores são aliados sem PvP
            boolean ownerIsPlayer = o == null || o instanceof Player;
            return ownerIsPlayer && !level.getServer().isPvpAllowed();
        }
        // Bichos e invocações (ou possuídos) de um jogador aliado também são aliados (co-op sem PvP)
        UUID master = e instanceof OwnableEntity own ? own.getOwnerUUID() : null;
        if (master == null) master = possessor(e);
        if (master != null && !master.equals(e.getUUID())) {
            Player p = level.getServer().getPlayerList().getPlayer(master);
            return p != null && isAlly(level, owner, p);
        }
        return false;
    }

    /** Entidade do dono (jogador em qualquer dimensão, ou entidade carregada neste nível). */
    public static LivingEntity owner(ServerLevel level, UUID owner) {
        if (owner == null) return null;
        if (level.getEntity(owner) instanceof LivingEntity le) return le;
        Player p = level.getServer().getPlayerList().getPlayer(owner);
        return p != null && p.level() == level ? p : null;
    }

    /** Inimigos vivos de um dono numa esfera, do mais perto para o mais longe. */
    public static List<LivingEntity> enemiesOf(ServerLevel level, UUID owner, Vec3 center, double radius, Entity exclude) {
        List<LivingEntity> list = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(radius),
                e -> e.isAlive() && e != exclude && !e.isSpectator() && !(e instanceof Player p && p.isCreative())
                        && e.distanceToSqr(center) <= radius * radius && !isAlly(level, owner, e)));
        list.sort(Comparator.comparingDouble(e -> e.distanceToSqr(center)));
        return list;
    }

    // ------------------------------------------------------------------ dano
    /** Fonte de dano de magia (tipo isekai_mahou:spell) causada por {@code causing}, para XP e créditos de abate. */
    public static DamageSource spellSource(ServerLevel level, Entity direct, Entity causing) {
        Holder<DamageType> type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(CoreRegistries.SPELL_DAMAGE);
        return new DamageSource(type, direct != null ? direct : causing, causing);
    }

    /**
     * Dano de magia "solto" (fora de um SpellContext): aplica resistências/fraquezas elementais do alvo e marca
     * o elemento para XP de abate do dono.
     */
    public static boolean spellHurt(ServerLevel level, LivingEntity target, float amount, Entity direct, LivingEntity owner, Element el) {
        amount *= ElementReactions.multiplier(target.getType(), el);
        if (amount <= 0) return false;
        ElementReactions.mark(target, el, owner != null ? owner.getUUID() : null, level.getGameTime());
        DamageSource src = owner != null || direct != null ? spellSource(level, direct, owner) : level.damageSources().magic();
        return target.hurt(src, amount);
    }

    // ------------------------------------------------------------------ trava por tick
    private static final Map<String, Long> ONCE = new HashMap<>();

    /**
     * true só na primeira vez no tick para (conjurador, chave). Evita, por exemplo, invocar várias fadas quando
     * uma magia em área atinge vários alvos no mesmo instante (Atraso/Repetir caem em ticks diferentes e passam).
     */
    public static boolean oncePerTick(SpellContext ctx, String key) {
        long now = ctx.level().getGameTime();
        String k = (ctx.casterId() != null ? ctx.casterId().toString() : "none") + "/" + key;
        Long prev = ONCE.put(k, now);
        if (ONCE.size() > 256) ONCE.values().removeIf(t -> t < now - 40);
        return prev == null || prev != now;
    }

    private LsUtil() {}
}
