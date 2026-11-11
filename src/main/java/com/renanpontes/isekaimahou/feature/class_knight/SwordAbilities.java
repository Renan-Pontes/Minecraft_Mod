package com.renanpontes.isekaimahou.feature.class_knight;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_knight.glyph.KnightGlyphs;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.weapon.MeleeSwings;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Poderes das Espadas Lendárias:
 * <ul>
 *   <li><b>Liberação</b> (botão direito; 30 s; 20% do recurso máximo): Luz do Lago (mini-Excalibur), Juramento do Rei
 *   (Caliburn: bênção do grupo), Rugido Carmesim (leque de chamas), Juízo do Trovão (raios nos inimigos), Inverno
 *   Eterno (anel de gelo), Eclipse Voraz (drena vida em volta).</li>
 *   <li><b>Eco</b>: golpes principais têm 35% (Cavaleiro) / 15% (outros) de soltar uma pequena onda do elemento
 *   (intervalo de 1 s).</li>
 *   <li><b>Passiva</b> enquanto empunhada (conferida a cada segundo).</li>
 * </ul>
 * Quem não é Cavaleiro Rúnico usa as Liberações com 70% do poder.
 */
public final class SwordAbilities {
    private static final Map<UUID, Long> LAST_ECHO = new HashMap<>();
    private static final net.minecraft.resources.ResourceLocation STORM_SPEED = IsekaiMahou.id("knight_storm_speed");

    /** Contexto mínimo para o dano das espadas sair como dano mágico do elemento (com a inflação e as reações). */
    @Nullable
    private static SpellContext context(LivingEntity owner, Element element, float power) {
        if (!(owner.level() instanceof ServerLevel level)) return null;
        SpellRecipe recipe = new SpellRecipe(List.of(KnightGlyphs.id(KnightGlyphs.SWORD_BEAM), IsekaiMahou.id("arcane_damage")),
                "", -1, "");
        SpellParser.Result r = SpellParser.parse(recipe, null, null);
        if (!r.ok()) return null;
        SpellContext ctx = new SpellContext(level, owner, element, recipe, r.spell());
        ctx.setPowerMult(power);
        return ctx;
    }

    private static void hit(SpellContext ctx, LivingEntity target, float base, Element el) {
        target.invulnerableTime = 0;
        ctx.damage(target, base, new SpellStats(), null, el);
    }

    // =====================================================================================
    //  Liberação
    // =====================================================================================
    /** Usa a Liberação. @return true se saiu (paga e entra em recarga). */
    public static boolean release(ServerPlayer player, LegendSword s) {
        MagicData data = MagicApi.get(player);
        float cost = data.maxMana() * 0.20F;
        if (!player.getAbilities().instabuild && !data.consumeMana(cost)) {
            player.displayClientMessage(MagicApi.noResourceMessage(player).copy().withStyle(ChatFormatting.RED), true);
            return false;
        }
        float power = (KnightPower.isKnight(player) ? 1.0F : 0.7F) * KnightPower.mult(player, s.element);
        SpellContext ctx = context(player, s.element, power);
        if (ctx == null) return false;
        ServerLevel level = player.serverLevel();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        switch (s) {
            case LAKE -> lakeLight(ctx, player, level, eye, look);
            case CALIBURN -> kingsOath(ctx, player, level);
            case FLAME -> fan(ctx, player, level, eye, look, s, 5, 8F, (e) -> {
                Statuses.apply(e, Statuses.Kind.BURNING, 2, 100, player, ctx.powerMult());
                e.igniteForSeconds(5F);
            });
            case STORM -> thunder(ctx, player, level, eye, look);
            case FROST -> winter(ctx, player, level);
            case DARK -> eclipse(ctx, player, level);
        }
        player.displayClientMessage(Component.translatable("message.isekai_mahou.class_knight.release", s.abilityName())
                .withColor(s.rgb), true);
        player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, true);
        return true;
    }

    /** Luz do Lago: um feixe reto de 32 blocos (raio 1,8) — 14 de dano de Luz e Exposto. */
    private static void lakeLight(SpellContext ctx, ServerPlayer p, ServerLevel level, Vec3 eye, Vec3 look) {
        Vec3 from = eye.add(0, -0.3, 0).add(look);
        Vec3 to = from.add(look.scale(32));
        KnightFx.line(level, from, to, 0xFFFFFF, 0.5, 1.8F);
        KnightFx.line(level, from, to, LegendSword.LAKE.rgb, 0.8, 2.6F);
        KnightFx.pillar(level, p.position(), 8, 0.5, LegendSword.LAKE.rgb);
        KnightFx.sound(level, from, SoundEvents.BEACON_ACTIVATE, 1.2F, 1.4F);
        KnightFx.sound(level, from, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8F, 1.6F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(from, to).inflate(2.5),
                e -> e != p && e.isAlive() && !ctx.isAlly(e))) {
            Vec3 d = KnightFx.center(e).subtract(from);
            double along = d.dot(look);
            if (along < -1 || along > 33 || d.subtract(look.scale(along)).length() > 1.8 + e.getBbWidth() * 0.5) continue;
            hit(ctx, e, 14F, Element.LIGHT);
            Statuses.apply(e, Statuses.Kind.EXPOSED, 1, 120, p, ctx.powerMult());
        }
    }

    /** Juramento do Rei: você e aliados em 10 blocos ganham Força I, Regeneração I e Absorção; inimigos em 6 são empurrados. */
    private static void kingsOath(SpellContext ctx, ServerPlayer p, ServerLevel level) {
        Vec3 c = p.position();
        KnightFx.runeCircle(level, c, 6, KnightFx.GOLD, 48, 0F);
        KnightFx.runeCircle(level, c, 10, KnightFx.GOLD, 64, 0.5F);
        KnightFx.pillar(level, c, 10, 1.0, KnightFx.GOLD);
        KnightFx.sound(level, c, SoundEvents.BELL_BLOCK, 1.5F, 0.8F);
        KnightFx.sound(level, c, SoundEvents.PLAYER_LEVELUP, 0.8F, 0.7F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(10), LivingEntity::isAlive)) {
            if (e == p || ctx.isAlly(e)) {
                e.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 300, 0), p);
                e.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 200, 0), p);
                e.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 300, 1), p);
                Vec3 q = KnightFx.center(e);
                level.sendParticles(KnightFx.dust(KnightFx.GOLD, 1.2F), q.x, q.y, q.z, 12, 0.3, 0.5, 0.3, 0);
            } else if (e.distanceToSqr(p) < 36) {
                Vec3 push = e.position().subtract(c).multiply(1, 0, 1);
                if (push.lengthSqr() < 1.0E-4) push = new Vec3(0, 0, 1);
                push = push.normalize().scale(1.2);
                e.push(push.x, 0.4, push.z);
                e.hurtMarked = true;
                hit(ctx, e, 5F, Element.LIGHT);
            }
        }
    }

    /** Leque de ondas do elemento (Rugido Carmesim). */
    private static void fan(SpellContext ctx, ServerPlayer p, ServerLevel level, Vec3 eye, Vec3 look, LegendSword s, int n, float dmg,
                            java.util.function.Consumer<LivingEntity> extra) {
        Vec3 from = eye.add(0, -0.35, 0).add(look.scale(0.8));
        for (int i = 0; i < n; i++) {
            float off = (i - (n - 1) / 2F) * 14F;
            float r = off * Mth.DEG_TO_RAD;
            Vec3 d = new Vec3(look.x * Math.cos(r) - look.z * Math.sin(r), look.y, look.x * Math.sin(r) + look.z * Math.cos(r)).normalize();
            BladeWaves.launch(level, p, from, d, 1.5, 18, 1.6, 4, false, s.rgb, true, e -> !ctx.isAlly(e),
                    (e, at) -> {
                        hit(ctx, e, dmg, s.element);
                        extra.accept(e);
                    }, null);
        }
        level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 30, 0.6, 0.3, 0.6, 0.08);
        KnightFx.sound(level, from, SoundEvents.BLAZE_SHOOT, 1.2F, 0.7F);
        KnightFx.sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
    }

    /** Juízo do Trovão: até 5 inimigos perto do ponto mirado (12 blocos) recebem um raio (só visual) — 10 de Vento + Eletrizado. */
    private static void thunder(SpellContext ctx, ServerPlayer p, ServerLevel level, Vec3 eye, Vec3 look) {
        Vec3 aim = eye.add(look.scale(10));
        List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(aim, aim).inflate(12),
                e -> e != p && e.isAlive() && !ctx.isAlly(e));
        targets.sort((a, b) -> Double.compare(a.distanceToSqr(aim), b.distanceToSqr(aim)));
        int n = 0;
        for (LivingEntity e : targets) {
            if (n++ >= 5) break;
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                bolt.moveTo(e.getX(), e.getY(), e.getZ());
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
            hit(ctx, e, 10F, Element.WIND);
            Statuses.apply(e, Statuses.Kind.SHOCKED, 2, 100, p, ctx.powerMult());
        }
        KnightFx.sound(level, p.position(), SoundEvents.TRIDENT_THUNDER.value(), 1.2F, 1.0F);
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, eye.x, eye.y, eye.z, 30, 0.5, 0.5, 0.5, 0.3);
    }

    /** Inverno Eterno: anel de gelo em 7 blocos — 7 de Água, Gelado ×3 (quem já estava gelado congela). */
    private static void winter(SpellContext ctx, ServerPlayer p, ServerLevel level) {
        Vec3 c = p.position();
        for (int ring = 1; ring <= 7; ring++) {
            int r = ring;
            com.renanpontes.isekaimahou.glyph.SpellScheduler.schedule(ring, () ->
                    KnightFx.runeCircle(level, c, r, LegendSword.FROST.rgb, 10 + r * 6, r * 0.3F));
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, c.x, c.y + 1, c.z, 80, 3.5, 0.8, 3.5, 0.05);
        KnightFx.sound(level, c, SoundEvents.GLASS_BREAK, 1.2F, 0.6F);
        KnightFx.sound(level, c, SoundEvents.POWDER_SNOW_STEP, 1.5F, 0.5F);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(7),
                e -> e != p && e.isAlive() && !ctx.isAlly(e) && e.distanceToSqr(p) < 49)) {
            boolean chilled = Statuses.has(e, Statuses.Kind.CHILLED);
            hit(ctx, e, 7F, Element.WATER);
            Statuses.apply(e, Statuses.Kind.CHILLED, 3, 120, p, ctx.powerMult());
            if (chilled) Statuses.apply(e, Statuses.Kind.FROZEN, 1, 60, p, ctx.powerMult());
        }
    }

    /** Eclipse Voraz: inimigos em 8 blocos — 9 de Sombra, Corrompido ×2 e Cegueira; você cura 30% do dano. */
    private static void eclipse(SpellContext ctx, ServerPlayer p, ServerLevel level) {
        Vec3 c = KnightFx.center(p);
        level.sendParticles(ParticleTypes.SQUID_INK, c.x, c.y, c.z, 60, 3.0, 1.0, 3.0, 0.05);
        level.sendParticles(KnightFx.dust(LegendSword.DARK.rgb, 1.6F), c.x, c.y, c.z, 40, 4.0, 1.0, 4.0, 0);
        KnightFx.sound(level, c, SoundEvents.WARDEN_SONIC_CHARGE, 0.8F, 1.4F);
        KnightFx.sound(level, c, SoundEvents.WITHER_AMBIENT, 0.6F, 1.6F);
        float drained = 0F;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(8),
                e -> e != p && e.isAlive() && !ctx.isAlly(e) && e.distanceToSqr(p) < 64)) {
            float before = e.getHealth();
            hit(ctx, e, 9F, Element.SHADOW);
            drained += Math.max(0F, before - e.getHealth());
            Statuses.apply(e, Statuses.Kind.CORRUPTED, 2, 120, p, ctx.powerMult());
            e.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), p);
            KnightFx.line(level, KnightFx.center(e), c, LegendSword.DARK.rgb, 0.5, 0.8F);
        }
        if (drained > 0) p.heal(drained * 0.3F);
    }

    // =====================================================================================
    //  Eco nos golpes
    // =====================================================================================
    public static void onDamage(LivingDamageEvent.Post event) {
        LivingEntity target = event.getEntity();
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof ServerPlayer p) || src.getDirectEntity() != p || !src.is(DamageTypes.PLAYER_ATTACK)) return;
        LegendSword s = KnightSwords.legendIn(p);
        if (s == null || !(p.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        if (MeleeSwings.mainHitStrength(p, target, now) < 0.8F) return;
        Long last = LAST_ECHO.get(p.getUUID());
        if (last != null && now - last < 20) return;
        boolean knight = KnightPower.isKnight(p);
        if (p.getRandom().nextFloat() > (knight ? 0.35F : 0.15F)) return;
        LAST_ECHO.put(p.getUUID(), now);
        SpellContext ctx = context(p, s.element, knight ? 1.0F : 0.7F);
        if (ctx == null) return;
        Vec3 look = p.getLookAngle();
        Vec3 from = p.getEyePosition().add(0, -0.35, 0).add(look.scale(0.8));
        BladeWaves.launch(level, p, from, look, 1.5, 14, 1.2, 2, false, s.rgb, false, e -> !ctx.isAlly(e),
                (e, at) -> {
                    hit(ctx, e, 4F * KnightPower.mult(p, s.element), s.element);
                    echoStatus(s, e, p, ctx);
                }, null);
        KnightFx.sound(level, from, SoundEvents.PLAYER_ATTACK_SWEEP, 0.8F, 1.5F);
    }

    private static void echoStatus(LegendSword s, LivingEntity e, ServerPlayer p, SpellContext ctx) {
        switch (s) {
            case FLAME -> Statuses.apply(e, Statuses.Kind.BURNING, 1, 80, p, ctx.powerMult());
            case STORM -> Statuses.apply(e, Statuses.Kind.SHOCKED, 1, 80, p, ctx.powerMult());
            case FROST -> Statuses.apply(e, Statuses.Kind.CHILLED, 1, 80, p, ctx.powerMult());
            case DARK -> {
                Statuses.apply(e, Statuses.Kind.CORRUPTED, 1, 80, p, ctx.powerMult());
                p.heal(1.5F);
            }
            case CALIBURN, LAKE -> Statuses.apply(e, Statuses.Kind.EXPOSED, 1, 80, p, ctx.powerMult());
        }
    }

    // =====================================================================================
    //  Passivas (a cada segundo, para quem empunha)
    // =====================================================================================
    public static void passive(ServerPlayer p) {
        LegendSword s = KnightSwords.legendIn(p);
        AttributeInstance speed = p.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            if (s == LegendSword.STORM) {
                speed.addOrUpdateTransientModifier(new AttributeModifier(STORM_SPEED, 0.10, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
            } else if (speed.getModifier(STORM_SPEED) != null) {
                speed.removeModifier(STORM_SPEED);
            }
        }
        if (s == null) return;
        MagicData d = MagicApi.get(p);
        switch (s) {
            // Dama do Lago: na água ou na chuva o Vigor volta (1,5% do máximo por segundo)
            case LAKE -> {
                if (p.isInWaterOrRain() && d.mana() < d.maxMana()) d.addMana(d.maxMana() * 0.015F);
            }
            // Caliburn: aliados em 8 blocos ficam firmes (Resistência I curta)
            case CALIBURN -> {
                for (Player o : p.level().getEntitiesOfClass(Player.class, p.getBoundingBox().inflate(8), o -> o != p && o.isAlive())) {
                    if (com.renanpontes.isekaimahou.core.api.Party.allyOverride(p, o) == Boolean.TRUE || !p.serverLevel().getServer().isPvpAllowed()) {
                        o.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 40, 0, true, false, true), p);
                    }
                }
            }
            case FLAME -> p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false, true));
            case FROST -> {
                p.setTicksFrozen(0);
                p.removeEffect(MobEffects.MOVEMENT_SLOWDOWN);
            }
            case DARK -> p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, true));
            case STORM -> { }
        }
    }

    public static void forget(UUID id) {
        LAST_ECHO.remove(id);
    }

    public static void clear() {
        LAST_ECHO.clear();
    }

    private SwordAbilities() {}
}
