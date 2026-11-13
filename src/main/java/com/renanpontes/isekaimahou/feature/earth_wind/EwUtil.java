package com.renanpontes.isekaimahou.feature.earth_wind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterials;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Utilitarios do modulo: movimento, chao, permissoes, "uma vez por tick", protecao contra queda, tremor de tela. */
public final class EwUtil {
    /** (contexto -> chave -> tick) para efeitos de area que nao devem repetir por alvo no mesmo tick. */
    private static final Map<SpellContext, Map<String, Long>> ONCE = new WeakHashMap<>();
    /** Entidades protegidas contra dano de queda ate o tick indicado (impulsos, pilares). */
    private static final Map<UUID, Long> FALL_GUARD = new HashMap<>();

    // =====================================================================================
    //  Movimento
    // =====================================================================================

    /** Este lado (cliente/servidor) e quem calcula o movimento desta entidade? */
    public static boolean controlsMovement(Entity e) {
        if (e instanceof Player p) return p.level().isClientSide && p.isLocalPlayer();
        return !e.level().isClientSide;
    }

    public static Vec3 horizontal(Vec3 v) {
        return new Vec3(v.x, 0, v.z);
    }

    /** Fator de empurrao (Pele de Pedra/golens resistem). Nunca zero, para os ventos sempre "pegarem" um pouco. */
    public static double knockbackFactor(Entity e) {
        if (e instanceof LivingEntity le && le.getAttributes().hasAttribute(Attributes.KNOCKBACK_RESISTANCE)) {
            return Math.max(0.15, 1.0 - le.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
        }
        return 1.0;
    }

    /** Soma velocidade (considerando resistencia a empurrao) e sincroniza com o cliente. */
    public static void push(Entity e, Vec3 horizontal, double up) {
        double k = knockbackFactor(e);
        e.setDeltaMovement(e.getDeltaMovement().add(horizontal.x * k, up * Math.max(0.5, k), horizontal.z * k));
        e.hurtMarked = true;
        e.hasImpulse = true;
    }

    /**
     * Jogador preso no ar pelo vento (tornado, redemoinho, vacuo): uma Levitacao invisivel de poucos ticks evita que
     * o anti-voo do servidor (allow-flight=false) o expulse por "flutuar" mais de 4 s. Some sozinha ao ser solto.
     */
    public static void holdAloft(Entity e) {
        if (e instanceof ServerPlayer sp && !sp.onGround() && !sp.isSpectator()) {
            sp.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 6, 0, false, false, false));
        }
    }

    /** Define a velocidade e sincroniza com o cliente (jogadores precisam do hurtMarked). */
    public static void setVelocity(Entity e, Vec3 v) {
        e.setDeltaMovement(v);
        e.hurtMarked = true;
        e.hasImpulse = true;
    }

    // =====================================================================================
    //  Chao e blocos
    // =====================================================================================

    /** Bloco "chao" (com colisao) mais alto a partir de {@code start} descendo ate {@code maxDown}; null se nao achar. */
    public static BlockPos groundBelow(Level level, BlockPos start, int maxDown) {
        BlockPos.MutableBlockPos p = start.mutable();
        for (int i = 0; i <= maxDown; i++) {
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() && s.getFluidState().isEmpty()) return p.immutable();
            p.move(0, -1, 0);
            if (p.getY() < level.getMinBuildHeight()) return null;
        }
        return null;
    }

    /** Chao sob uma entidade (o bloco em que ela pisa), ou null se estiver alto demais. */
    public static BlockPos groundUnder(Entity e, int maxDown) {
        return groundBelow(e.level(), BlockPos.containing(e.getX(), e.getY() + 0.2, e.getZ()), maxDown);
    }

    /** Pode MEXER (colocar/trocar temporariamente) neste bloco? Respeita protecao de area e mobGriefing. */
    public static boolean canEdit(SpellContext ctx, BlockPos pos) {
        ServerLevel level = ctx.level();
        // v1.0 r9: torre em escalada / masmorra são indestrutíveis (nem blocos temporários de magia)
        if (com.renanpontes.isekaimahou.core.api.Destruction.wardedFor(level, pos, ctx.caster())) return false;
        if (!level.isInWorldBounds(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        LivingEntity caster = ctx.caster();
        if (caster instanceof ServerPlayer sp) {
            return sp.mayBuild() && level.mayInteract(sp, pos) && !sp.blockActionRestricted(level, pos, sp.gameMode.getGameModeForPlayer());
        }
        // mobs conjuradores: regra mobGriefing (e o evento do NeoForge que outros mods podem ajustar)
        if (caster instanceof Mob) return EventHooks.canEntityGrief(level, caster);
        return true;
    }

    /**
     * Pode QUEBRAR (ou trocar/escavar temporariamente um bloco solido) aqui? Alem do canEdit, dispara o evento de
     * quebra para jogadores: mods de protecao e areas protegidas de outros modulos (labirintos, dominios) vetam.
     */
    public static boolean canBreak(SpellContext ctx, BlockPos pos, BlockState state) {
        if (!canEdit(ctx, pos)) return false;
        if (ctx.caster() instanceof ServerPlayer sp) {
            return !NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(ctx.level(), pos, state, sp)).isCanceled();
        }
        return true;
    }

    /** Ferramenta virtual de mineracao (picareta de diamante) com Fortuna/Toque Suave vindos dos aumentos. */
    public static ItemStack miningTool(ServerLevel level, SpellStats stats) {
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        var enchants = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
        if (stats.silkTouch) tool.enchant(enchants.getOrThrow(Enchantments.SILK_TOUCH), 1);
        else if (stats.fortune) tool.enchant(enchants.getOrThrow(Enchantments.FORTUNE), 3);
        return tool;
    }

    /** Entidade "metalica" para o Magnetismo: golem de ferro ou armadura/arma de ferro ou cota de malha. */
    public static boolean isMagnetic(LivingEntity e) {
        if (e instanceof IronGolem) return true;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack s = e.getItemBySlot(slot);
            if (s.isEmpty()) continue;
            if (s.getItem() instanceof ArmorItem armor
                    && (armor.getMaterial().equals(ArmorMaterials.IRON) || armor.getMaterial().equals(ArmorMaterials.CHAIN))) {
                return true;
            }
            String path = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
            if (path.startsWith("iron_") || path.startsWith("chainmail_")) return true;
        }
        return false;
    }

    // =====================================================================================
    //  Controle de repeticao e protecao de queda
    // =====================================================================================

    /** true so na primeira chamada desta chave neste tick para este contexto (efeitos de area). */
    public static boolean once(SpellContext ctx, String key) {
        long now = ctx.level().getGameTime();
        Map<String, Long> m = ONCE.computeIfAbsent(ctx, c -> new HashMap<>());
        Long last = m.get(key);
        if (last != null && last == now) return false;
        m.put(key, now);
        return true;
    }

    public static void protectFall(LivingEntity e, int ticks) {
        long until = e.level().getGameTime() + ticks;
        FALL_GUARD.merge(e.getUUID(), until, Math::max);
        e.resetFallDistance();
    }

    public static boolean isFallProtected(LivingEntity e) {
        Long until = FALL_GUARD.get(e.getUUID());
        if (until == null) return false;
        if (until < e.level().getGameTime()) {
            FALL_GUARD.remove(e.getUUID());
            return false;
        }
        return true;
    }

    // =====================================================================================
    //  Tremor de tela
    // =====================================================================================

    /** Treme a tela de jogadores perto (intensidade cai com a distancia). */
    public static void shake(ServerLevel level, Vec3 center, double radius, float intensity, int ticks) {
        for (ServerPlayer p : level.players()) {
            double d = p.position().distanceTo(center);
            if (d > radius) continue;
            float k = (float) (intensity * (1.0 - 0.6 * d / radius));
            PacketDistributor.sendToPlayer(p, new EwNetwork.Shake(k, ticks));
        }
    }

    /** Remove protecoes de queda ja vencidas de entidades que nunca mais cairam. */
    static void pruneFallGuard(long now) {
        FALL_GUARD.values().removeIf(until -> until < now);
    }

    static void clear() {
        ONCE.clear();
        FALL_GUARD.clear();
    }

    private EwUtil() {}
}
