package com.renanpontes.isekaimahou.core.classes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.glyph.weapon.MeleeSwings;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileWeaponItem;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Regras do recurso por categoria (§2.2). O valor é o mesmo "mana" salvo; muda como ele volta:
 * <ul>
 *   <li><b>Mana</b> (Mágicas): como sempre.</li>
 *   <li><b>Vigor</b> (Guerreiras): regen passiva ×0,35; +3 (+5% do máximo) por golpe corpo a corpo que acerta com a barra de
 *       ataque ≥ 80%; +2 ao receber dano (no máx. 1× a cada 10 ticks).</li>
 *   <li><b>Foco</b> (À Distância): regen passiva ×0,5; +4 por projétil do jogador que acerta (flecha, tridente, ou projétil
 *       com a tag de entidade {@link #FOCUS_SHOT_TAG}); +1/s parado, agachado e mirando com arco/besta.</li>
 * </ul>
 * A regen passiva menor é um modificador TEMPORÁRIO no atributo {@code isekai_mahou:mana_regen} (o núcleo já multiplica a
 * regen por ele), conferido a cada segundo — então trocar de classe por comando também ajusta. Meditação vale para todos.
 */
public final class ClassResources {
    /** Modificador da regen passiva por categoria. */
    public static final ResourceLocation REGEN_MODIFIER = IsekaiMahou.id("class_resource_regen");
    /**
     * Tag de ENTIDADE para projéteis de módulos que contam como "tiro" do Foco (ex.: a forma {@code throw}):
     * {@code projectile.addTag(ClassResources.FOCUS_SHOT_TAG)}. Flechas e tridentes já contam sozinhos.
     */
    public static final String FOCUS_SHOT_TAG = "isekai_mahou.focus_shot";

    public static final float VIGOR_HIT = 3F;
    public static final float VIGOR_HIT_PCT = 0.05F;
    public static final float VIGOR_MIN_STRENGTH = 0.8F;
    public static final float VIGOR_HURT = 2F;
    public static final int VIGOR_HURT_COOLDOWN = 10;
    public static final float FOCUS_HIT = 4F;
    public static final float FOCUS_AIM = 1F;
    public static final int FOCUS_AIM_INTERVAL = 20;

    /** Itens que contam como "mirando" para o Foco (padrão: arcos e bestas). Módulos podem somar (ex.: o alaúde). */
    private static final List<Predicate<ItemStack>> AIM_ITEMS = new CopyOnWriteArrayList<>();

    static {
        AIM_ITEMS.add(s -> s.getItem() instanceof ProjectileWeaponItem);
    }

    private static final class State {
        long lastHurtGain = Long.MIN_VALUE;
        UUID lastShot;
        UUID lastShotTarget;
        int aimTicks;
        double lx, ly, lz;
    }

    private static final Map<UUID, State> STATES = new HashMap<>();

    /** Soma um item que conta como "mirando" (segurando/usando) para o Foco. */
    public static void addAimItem(Predicate<ItemStack> test) {
        if (test != null) AIM_ITEMS.add(test);
    }

    /** Dá recurso (Vigor/Foco/Mana) ao jogador, respeitando o máximo. */
    public static void gain(ServerPlayer p, float amount) {
        if (amount <= 0 || p.isSpectator()) return;
        MagicApi.get(p).addMana(amount);
    }

    private static State state(ServerPlayer p) {
        return STATES.computeIfAbsent(p.getUUID(), k -> new State());
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || p.isSpectator()) return;
        if (p.tickCount % 20 == 0) syncRegenModifier(p);
        MagicData d = MagicApi.get(p);
        State st = state(p);
        double dx = p.getX() - st.lx, dy = p.getY() - st.ly, dz = p.getZ() - st.lz;
        st.lx = p.getX(); st.ly = p.getY(); st.lz = p.getZ();
        if (d.path().resource() != MagicPath.Resource.FOCUS) {
            st.aimTicks = 0;
            return;
        }
        boolean still = dx * dx + dy * dy + dz * dz < 1.0E-4;
        if (still && p.isShiftKeyDown() && p.onGround() && aiming(p)) {
            if (++st.aimTicks % FOCUS_AIM_INTERVAL == 0) gain(p, FOCUS_AIM);
        } else {
            st.aimTicks = 0;
        }
    }

    /** Mirando: puxando o arco (ou outro item de mira em uso) ou segurando uma besta carregada. */
    private static boolean aiming(ServerPlayer p) {
        if (p.isUsingItem()) {
            ItemStack using = p.getUseItem();
            for (Predicate<ItemStack> t : AIM_ITEMS) if (t.test(using)) return true;
        }
        for (ItemStack held : List.of(p.getMainHandItem(), p.getOffhandItem())) {
            if (held.getItem() instanceof CrossbowItem && CrossbowItem.isCharged(held)) return true;
        }
        return false;
    }

    public static void onDamage(LivingDamageEvent.Post event) {
        if (event.getNewDamage() <= 0) return;
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        long now = victim.level().getGameTime();
        DamageSource src = event.getSource();

        // Vigor: apanhar também enche (um pouco)
        if (victim instanceof ServerPlayer vp && !(vp instanceof FakePlayer)
                && MagicApi.get(vp).path().resource() == MagicPath.Resource.VIGOR && src.getEntity() != vp) {
            State st = state(vp);
            if (now - st.lastHurtGain >= VIGOR_HURT_COOLDOWN) {
                st.lastHurtGain = now;
                gain(vp, VIGOR_HURT);
            }
        }

        if (!(src.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || victim == p) return;
        MagicPath.Resource res = MagicApi.get(p).path().resource();
        if (res == MagicPath.Resource.VIGOR) {
            // Golpe corpo a corpo principal (não a varrida) com a barra de ataque quase cheia
            if (src.is(DamageTypes.PLAYER_ATTACK) && src.getDirectEntity() == p
                    && MeleeSwings.mainHitStrength(p, victim, now) >= VIGOR_MIN_STRENGTH) {
                gain(p, VIGOR_HIT + MagicApi.get(p).maxMana() * VIGOR_HIT_PCT);
            }
        } else if (res == MagicPath.Resource.FOCUS) {
            if (src.getDirectEntity() instanceof Projectile proj
                    && (proj instanceof AbstractArrow || proj.getTags().contains(FOCUS_SHOT_TAG))) {
                // Uma vez por projétil por alvo (a flecha e a magia dela causam dano no mesmo alvo, em ticks seguidos)
                State st = state(p);
                if (proj.getUUID().equals(st.lastShot) && victim.getUUID().equals(st.lastShotTarget)) return;
                st.lastShot = proj.getUUID();
                st.lastShotTarget = victim.getUUID();
                gain(p, FOCUS_HIT);
            }
        }
    }

    /** Põe/tira o modificador da regen passiva conforme a categoria da classe. */
    public static void syncRegenModifier(ServerPlayer p) {
        AttributeInstance inst = p.getAttribute(CoreRegistries.MANA_REGEN);
        if (inst == null) return;
        double want = MagicApi.get(p).path().resource().passiveRegen - 1.0;
        AttributeModifier cur = inst.getModifier(REGEN_MODIFIER);
        if (Math.abs(want) < 1.0E-6) {
            if (cur != null) inst.removeModifier(REGEN_MODIFIER);
            return;
        }
        if (cur != null && Math.abs(cur.amount() - want) < 1.0E-6 && cur.operation() == AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL) return;
        inst.removeModifier(REGEN_MODIFIER);
        inst.addTransientModifier(new AttributeModifier(REGEN_MODIFIER, want, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
    }

    public static void forget(UUID player) {
        STATES.remove(player);
    }

    public static void clear() {
        STATES.clear();
    }

    private ClassResources() {}
}
