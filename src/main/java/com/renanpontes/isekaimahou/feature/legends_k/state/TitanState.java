package com.renanpontes.isekaimahou.feature.legends_k.state;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.feature.legends_k.LkFx;
import com.renanpontes.isekaimahou.feature.legends_k.LkUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Modo Titã (Attack on Titan — o Titã Martelo de Guerra forja as próprias armas, por isso é do Ferreiro): você fica
 * ENORME por um tempo. Tudo por modificadores TRANSITÓRIOS de atributo (não vão para o save; somem ao sair, morrer ou
 * acabar a transformação):
 * <pre>
 *   tamanho ×2,2 · +20 de vida máxima · +2,5 de alcance (criaturas) · +2 (blocos) · +4 de dano · +60% resistência a
 *   empurrão · +1 de passo · +6 de queda segura · −15% de velocidade
 * </pre>
 * Golpes corpo a corpo fazem um PISÃO em volta do alvo (40% do golpe nos inimigos a até 2,5 blocos, 1× por ¾ s).
 * Vapor saindo do corpo enquanto dura, e uma nuvem grande de vapor ao sair (como o corpo do titã evaporando).
 */
public final class TitanState {
    private record Mod(Holder<Attribute> attr, ResourceLocation id, double amount, AttributeModifier.Operation op) {}

    private static final List<Mod> MODS = List.of(
            new Mod(Attributes.SCALE, IsekaiMahou.id("lk_titan_scale"), 1.2, AttributeModifier.Operation.ADD_MULTIPLIED_BASE),
            new Mod(Attributes.MAX_HEALTH, IsekaiMahou.id("lk_titan_health"), 20, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.ENTITY_INTERACTION_RANGE, IsekaiMahou.id("lk_titan_reach"), 2.5, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.BLOCK_INTERACTION_RANGE, IsekaiMahou.id("lk_titan_block_reach"), 2.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.ATTACK_DAMAGE, IsekaiMahou.id("lk_titan_damage"), 4, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("lk_titan_knockback"), 0.6, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.STEP_HEIGHT, IsekaiMahou.id("lk_titan_step"), 1.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id("lk_titan_fall"), 6.0, AttributeModifier.Operation.ADD_VALUE),
            new Mod(Attributes.MOVEMENT_SPEED, IsekaiMahou.id("lk_titan_speed"), -0.15, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));

    private record Titan(long end, float power) {}

    private static final Map<UUID, Titan> TITANS = new HashMap<>();
    private static final Map<UUID, Long> STOMP_CD = new HashMap<>();
    private static final ThreadLocal<Boolean> STOMPING = ThreadLocal.withInitial(() -> false);

    public static boolean isTitan(@Nullable Entity e) {
        return e != null && !TITANS.isEmpty() && TITANS.containsKey(e.getUUID());
    }

    public static void start(SpellContext ctx, LivingEntity self, int ticks, float power) {
        if (!(self.level() instanceof ServerLevel l)) return;
        TITANS.put(self.getUUID(), new Titan(l.getGameTime() + ticks, power));
        for (Mod m : MODS) {
            AttributeInstance inst = self.getAttribute(m.attr());
            if (inst != null) inst.addOrUpdateTransientModifier(new AttributeModifier(m.id(), m.amount(), m.op()));
        }
        self.heal(20F);
        Vec3 c = self.position();
        // o raio da transformação: um clarão alaranjado e o estrondo
        ElementFx.flash(l, c.add(0, 2, 0), 0xFFB060, 4F);
        ElementFx.shockwave(l, c.add(0, 0.1, 0), 0xFFB060, 6F, 20, null);
        l.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 1.5, c.z, 24, 1.0, 1.5, 1.0, 0.05);
        LkUtil.sound(l, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 0.6F);
        LkUtil.sound(l, c, SoundEvents.RAVAGER_ROAR, 1.0F, 0.6F);
        LkUtil.say(self, "titan_start", ChatFormatting.GOLD);
    }

    /** Fim do Modo Titã: tira os modificadores e o corpo evapora. */
    public static void stop(LivingEntity self) {
        if (TITANS.remove(self.getUUID()) == null) return;
        removeMods(self);
        STOMP_CD.remove(self.getUUID());
        if (self.level() instanceof ServerLevel l) {
            Vec3 c = self.position();
            l.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 1.0, c.z, 40, 1.2, 1.4, 1.2, 0.04);
            l.sendParticles(ParticleTypes.WHITE_SMOKE, c.x, c.y + 0.6, c.z, 16, 0.8, 0.6, 0.8, 0.02);
            LkUtil.sound(l, c, SoundEvents.LAVA_EXTINGUISH, 1.0F, 0.6F);
            LkUtil.say(self, "titan_end", ChatFormatting.GRAY);
        }
        if (self.getHealth() > self.getMaxHealth()) self.setHealth(self.getMaxHealth());
    }

    private static void removeMods(LivingEntity self) {
        for (Mod m : MODS) {
            AttributeInstance inst = self.getAttribute(m.attr());
            if (inst != null) inst.removeModifier(m.id());
        }
    }

    public static void tick(MinecraftServer server) {
        if (TITANS.isEmpty()) return;
        for (Map.Entry<UUID, Titan> en : new ArrayList<>(TITANS.entrySet())) {
            LivingEntity e = find(server, en.getKey());
            if (e == null) {
                TITANS.remove(en.getKey());
                continue;
            }
            if (!e.isAlive() || e.level().getGameTime() >= en.getValue().end()) {
                stop(e);
                continue;
            }
            // um fio de vapor saindo do corpo (1× por segundo)
            if (e.tickCount % 20 == 0 && e.level() instanceof ServerLevel l) {
                Vec3 c = e.position().add(0, e.getBbHeight() * 0.75, 0);
                l.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 2, e.getBbWidth() * 0.4, 0.4, e.getBbWidth() * 0.4, 0.01);
            }
        }
    }

    @Nullable
    private static LivingEntity find(MinecraftServer server, UUID id) {
        ServerPlayer sp = server.getPlayerList().getPlayer(id);
        if (sp != null) return sp;
        for (ServerLevel l : server.getAllLevels()) {
            if (l.getEntity(id) instanceof LivingEntity le) return le;
        }
        return null;
    }

    /** Pisão: golpe corpo a corpo do titã respinga nos inimigos em volta do alvo. */
    public static void onIncoming(LivingIncomingDamageEvent e) {
        if (TITANS.isEmpty() || STOMPING.get()) return;
        DamageSource src = e.getSource();
        Entity a = src.getEntity();
        if (a == null || src.getDirectEntity() != a || !(a instanceof LivingEntity titan) || !isTitan(titan)) return;
        if (!(titan.level() instanceof ServerLevel l)) return;
        long now = l.getGameTime();
        Long cd = STOMP_CD.get(titan.getUUID());
        if (cd != null && now < cd) return;
        STOMP_CD.put(titan.getUUID(), now + 15);
        LivingEntity v = e.getEntity();
        Vec3 c = v.position();
        float dmg = e.getAmount() * 0.4F;
        ElementFx.shockwave(l, c.add(0, 0.1, 0), 0xC8A070, 2.5F, 12, null);
        l.sendParticles(ParticleTypes.POOF, c.x, c.y + 0.2, c.z, 8, 1.0, 0.1, 1.0, 0.02);
        LkUtil.sound(l, c, SoundEvents.GENERIC_EXPLODE, 0.35F, 0.5F);
        STOMPING.set(true);
        try {
            for (LivingEntity o : l.getEntitiesOfClass(LivingEntity.class, v.getBoundingBox().inflate(2.5),
                    o -> o != v && o != titan && o.isAlive() && !(o.isAlliedTo(titan)) && isFoeOf(titan, o))) {
                o.hurt(src, dmg);
                Vec3 away = o.position().subtract(c);
                away = new Vec3(away.x, 0, away.z);
                if (away.lengthSqr() > 1e-4) LkUtil.impulse(o, away.normalize().scale(0.6).add(0, 0.3, 0));
            }
        } finally {
            STOMPING.set(false);
        }
    }

    /** Respingo só em monstros e em quem está brigando com o titã (nada de pisar no gado ou no amigo). */
    private static boolean isFoeOf(LivingEntity titan, LivingEntity o) {
        if (o instanceof net.minecraft.world.entity.player.Player p) {
            return titan instanceof net.minecraft.world.entity.player.Player tp && tp.canHarmPlayer(p);
        }
        if (o instanceof net.minecraft.world.entity.monster.Enemy) return true;
        return o instanceof net.minecraft.world.entity.Mob m && m.getTarget() == titan;
    }

    public static void forget(LivingEntity e) {
        if (TITANS.containsKey(e.getUUID())) {
            TITANS.remove(e.getUUID());
            removeMods(e);
        }
        STOMP_CD.remove(e.getUUID());
    }

    public static void clear() {
        TITANS.clear();
        STOMP_CD.clear();
    }

    private TitanState() {}
}
