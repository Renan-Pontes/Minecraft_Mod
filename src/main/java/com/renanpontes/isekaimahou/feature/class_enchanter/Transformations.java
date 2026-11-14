package com.renanpontes.isekaimahou.feature.class_enchanter;

import com.renanpontes.isekaimahou.core.anim.MahouAnims;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import com.renanpontes.isekaimahou.feature.class_enchanter.EnchFxNet;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Party;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.class_enchanter.effect.TransformEffect;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * TRANSFORMAÇÕES do Encantador — o pacote de técnicas de anime do corpo encantado (todas montáveis como glifos de
 * origem, ver {@code glyph/AnimeGlyphs}). Regras de cada uma:
 * <ul>
 *   <li><b>Oito Portões Internos</b> (homenagem aos lutadores de taijutsu): cada conjuração abre o PRÓXIMO portão (+1 por
 *   Amplificar) até o limite do rank (Lição 3; Encantador 3 + rank, até 8). Abrir custa vida na hora; do 4º em diante a
 *   vida drena por segundo. Do 6º em diante os socos soltam uma onda em área. O 8º (Portão da Morte) dura 10 s com
 *   poder máximo e, ao fechar, deixa você com 1 de vida, exausto, e os portões travam por 60 s — nunca mata.</li>
 *   <li><b>Kaioken</b>: ×2 → ×3 → ×10 (cada conjuração sobe um degrau; ×3 no rank III, ×10 no rank V). Multiplica o dano
 *   corpo a corpo (e as técnicas pela metade do ganho) e drena 2%/4%/10% da vida por segundo; se a vida chegar a 1, o corpo
 *   não aguenta e o Kaioken quebra.</li>
 *   <li><b>Segunda Marcha</b>: o sangue bombeado vira vapor — velocidade, ataque e pulo muito maiores; custa um pouco de
 *   vida aos poucos.</li>
 *   <li><b>Quarta Marcha: Homem-Mola</b>: o corpo infla (tamanho ×1,6), alcance e defesa maiores e cada soco é um
 *   canhão (onda em área). Drena o recurso por segundo; ao acabar, exaustão.</li>
 *   <li><b>Instinto Superior</b>: o corpo esquiva SOZINHO de N ataques (corpo a corpo, projéteis, magias com autor); depois
 *   de esquivar, o próximo golpe em 2 s é um contra-ataque (+50%). Pode ser dado a aliados.</li>
 * </ul>
 */
public final class Transformations {
    /** Preço em vida (fração da vida máxima) para abrir cada portão. */
    public static final float[] GATE_PRICE = {0.04F, 0.04F, 0.05F, 0.06F, 0.07F, 0.08F, 0.10F, 0.15F};
    /** Cor da aura de cada portão. */
    public static final int[] GATE_COLOR = {0x7FD8FF, 0x6FE0C8, 0x6FE08A, 0x9FE05A, 0xE0D85A, 0x5AE0E0, 0x4AA8FF, 0xFF2A2A};
    /** Preço inicial do Kaioken (fração da vida) e dreno por segundo, por degrau. */
    public static final float[] KAIOKEN_PRICE = {0.05F, 0.08F, 0.15F};
    public static final float[] KAIOKEN_DRAIN = {0.02F, 0.04F, 0.10F};
    /** Multiplicador das técnicas (dano de magia) por degrau do Kaioken. */
    public static final float[] KAIOKEN_SPELL = {1.5F, 2.0F, 5.5F};
    public static final int DEATH_GATE_TICKS = 200;
    public static final int GATE_LOCK_TICKS = 1200;
    public static final int COUNTER_WINDOW = 40;

    private static final Map<UUID, Long> GATE_LOCK = new HashMap<>();
    private static final Map<UUID, Integer> DODGES = new HashMap<>();
    private static final Map<UUID, Long> COUNTER = new HashMap<>();
    private static final Map<UUID, Long> SPLASH_CD = new HashMap<>();
    private static final Set<UUID> BROKEN = new HashSet<>();
    private static final Set<UUID> SILENT_END = new HashSet<>();
    private static boolean splashing;

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, Transformations::onDodge);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, LivingIncomingDamageEvent.class, Transformations::onMelee);
        NeoForge.EVENT_BUS.addListener(SpellDamageEvent.Pre.class, Transformations::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Expired.class, e -> ended(e.getEntity(), e.getEffectInstance()));
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, MobEffectEvent.Remove.class, e -> ended(e.getEntity(), e.getEffectInstance()));
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> forget(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(ServerStoppedEvent.class, e -> clear());
    }

    // =====================================================================================
    //  Limites por rank
    // =====================================================================================
    /** Quantos portões o corpo aguenta: Lição 3; Encantador 3 + rank (rank V = os 8). */
    public static int gateCap(LivingEntity e) {
        int rank = Enchants.rank(e);
        return rank < 0 ? 3 : Math.min(8, 3 + rank);
    }

    /** Degrau máximo do Kaioken (0 = ×2, 1 = ×3, 2 = ×10): Lição ×2; Encantador ×3 no rank III e ×10 no rank V. */
    public static int kaiokenCap(LivingEntity e) {
        int rank = Enchants.rank(e);
        if (rank < 0) return 0;
        return rank >= 5 ? 2 : rank >= 3 ? 1 : 0;
    }

    public static int gate(LivingEntity e) {
        MobEffectInstance i = e.getEffect(EnchRegistries.GATES);
        return i == null ? 0 : i.getAmplifier() + 1;
    }

    public static int kaiokenStep(LivingEntity e) {
        MobEffectInstance i = e.getEffect(EnchRegistries.KAIOKEN);
        return i == null ? -1 : Math.min(2, i.getAmplifier());
    }

    private static boolean creative(LivingEntity e) {
        return e instanceof Player p && p.getAbilities().instabuild;
    }

    /** Tira uma fração da vida máxima sem nunca matar (fica no mínimo com 1). */
    private static void pay(LivingEntity e, float fraction) {
        if (creative(e) || fraction <= 0F) return;
        e.setHealth(Math.max(1F, e.getHealth() - e.getMaxHealth() * fraction));
    }

    private static void tell(LivingEntity e, Component msg) {
        if (e instanceof ServerPlayer sp) sp.displayClientMessage(msg, true);
    }

    // =====================================================================================
    //  Oito Portões
    // =====================================================================================
    public static void openGates(SpellContext ctx, LivingEntity self, SpellStats stats) {
        ServerLevel level = ctx.level();
        long now = level.getGameTime();
        Long lock = GATE_LOCK.get(self.getUUID());
        if (lock != null && now < lock && !creative(self)) {
            tell(self, Component.translatable("message.isekai_mahou.enchanter.gates_locked", (lock - now + 19) / 20).withStyle(ChatFormatting.RED));
            EnchFx.sound(level, self.position(), SoundEvents.WARDEN_HEARTBEAT, 0.8F, 0.6F);
            return;
        }
        int cur = gate(self);
        int cap = gateCap(self);
        if (cur >= cap) {
            tell(self, Component.translatable(cur >= 8 ? "message.isekai_mahou.enchanter.gates_max" : "message.isekai_mahou.enchanter.gates_cap", cap)
                    .withStyle(ChatFormatting.GRAY));
            return;
        }
        int next = Math.min(cap, cur + 1 + Math.max(0, stats.amplify));
        float price = 0F;
        for (int g = cur + 1; g <= next; g++) price += GATE_PRICE[g - 1];
        pay(self, price);
        MobEffectInstance old = self.getEffect(EnchRegistries.GATES);
        int ticks = next >= 8 ? DEATH_GATE_TICKS : Math.max(Enchants.duration(self, stats, 400), old == null ? 0 : old.getDuration());
        // v1.1: como corpo de uma Transformação, os portões ficam abertos a transformação inteira (o 8º continua curto)
        int body = com.renanpontes.isekaimahou.feature.transform_laws.TransformForge.bodyTicks(ctx, self);
        if (body > 0 && next < 8) ticks = body;
        self.addEffect(new MobEffectInstance(EnchRegistries.GATES, ticks, next - 1, false, false, true), self);
        Component name = gateName(next);
        tell(self, Component.translatable("message.isekai_mahou.enchanter.gate_open", roman(next), name, Math.round(price * 100))
                .withStyle(next >= 8 ? ChatFormatting.RED : ChatFormatting.GREEN));
        if (!ctx.formStats().silent && body <= 0) {
            Announce.send(self, Component.translatable("announce.isekai_mahou.enchanter.gate", roman(next), name), GATE_COLOR[next - 1],
                    next >= 7 ? Announce.STYLE_ULTIMATE : Announce.STYLE_SPELL);
        }
        gateBurst(level, self, next);
        // o corpo explode de energia um tick depois do gesto de conjuração
        SpellScheduler.schedule(1, () -> MahouAnims.play(self, next >= 8 ? "transform_roar" : "transform_burst"));
        if (next >= 8) deathGateShock(ctx, self, stats);
        if (self instanceof ServerPlayer sp && next >= 8) MagicApi.addCounter(sp, "enchanter.death_gate", 1);
    }

    private static void gateBurst(ServerLevel level, LivingEntity e, int gate) {
        int rgb = GATE_COLOR[gate - 1];
        Vec3 feet = e.position().add(0, 0.1, 0);
        // ondas de pressão, aura subindo e vapor: coreografia do cliente
        EnchFxNet.at(level, EnchFxNet.GATE, e.position(), Vec3.ZERO, 1F, rgb, e.getId(), gate);
        level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.3, e.getZ(), 4 + gate, 0.6, 0.2, 0.6, 0.08 + gate * 0.01);
        EnchFx.sound(level, feet, SoundEvents.WARDEN_HEARTBEAT, 1.3F, 0.6F + gate * 0.08F);
        EnchFx.sound(level, feet, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.5F + gate * 0.05F);
        if (gate >= 6) EnchFx.sound(level, feet, SoundEvents.LAVA_EXTINGUISH, 1.0F, 0.7F);
    }

    /** Ao abrir o 8º portão: a pressão do corpo empurra e fere todos os inimigos por perto (sem quebrar blocos). */
    private static void deathGateShock(SpellContext ctx, LivingEntity self, SpellStats stats) {
        ServerLevel level = ctx.level();
        for (LivingEntity o : ctx.enemiesAround(self.position(), 6.0)) {
            o.invulnerableTime = 0;
            ctx.damage(o, 6F, stats, null);
            Vec3 away = o.position().subtract(self.position()).multiply(1, 0, 1);
            away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
            double kb = 1.4 * (1.0 - o.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            o.push(away.x * kb, 0.6, away.z * kb);
            o.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, self.getX(), self.getY() + 0.5, self.getZ(), 1, 0, 0, 0, 0);
        EnchFxNet.at(level, EnchFxNet.DEATH_GATE, self.position(), Vec3.ZERO, 1F, EnchFx.RED, self.getId());
        EnchFx.sound(level, self.position(), SoundEvents.WARDEN_SONIC_BOOM, 1.2F, 0.8F);
        EnchFx.sound(level, self.position(), SoundEvents.GENERIC_EXPLODE.value(), 0.9F, 0.6F);
    }

    // =====================================================================================
    //  Kaioken
    // =====================================================================================
    public static void kaioken(SpellContext ctx, LivingEntity self, SpellStats stats) {
        ServerLevel level = ctx.level();
        int cap = kaiokenCap(self);
        int cur = kaiokenStep(self);
        int step = Math.min(cap, cur + 1 + Math.max(0, stats.amplify));
        if (step <= cur) {
            tell(self, Component.translatable("message.isekai_mahou.enchanter.kaioken_cap", TransformEffect.KAIOKEN_MULT[cap]).withStyle(ChatFormatting.GRAY));
            return;
        }
        pay(self, KAIOKEN_PRICE[step]);
        int ticks = step >= 2 ? stats.duration(100) : stats.duration(300);
        // v1.1: como corpo de uma Transformação, o Kaioken ×2 dura a transformação inteira (×3/×10 continuam curtos)
        int body = com.renanpontes.isekaimahou.feature.transform_laws.TransformForge.bodyTicks(ctx, self);
        if (body > 0 && step < 2) ticks = body;
        self.addEffect(new MobEffectInstance(EnchRegistries.KAIOKEN, Math.max(20, ticks), step, false, false, true), self);
        int mult = TransformEffect.KAIOKEN_MULT[step];
        tell(self, Component.translatable("message.isekai_mahou.enchanter.kaioken_on", mult, mult, fmt(KAIOKEN_SPELL[step]),
                Math.round(KAIOKEN_DRAIN[step] * 100)).withStyle(ChatFormatting.RED));
        if (!ctx.formStats().silent && body <= 0) {
            Announce.send(self, Component.translatable("announce.isekai_mahou.enchanter.kaioken", mult), 0xFF3B30,
                    step >= 2 ? Announce.STYLE_ULTIMATE : Announce.STYLE_SPELL);
        }
        Vec3 c = EnchFx.center(self);
        level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 10 + step * 6, 0.5, 0.8, 0.5, 0.06);
        EnchFxNet.at(level, EnchFxNet.KAIOKEN, self.position(), Vec3.ZERO, 1F, 0xFF3B30, self.getId(), step);
        EnchFx.sound(level, c, SoundEvents.BLAZE_SHOOT, 1.0F, 0.6F);
        EnchFx.sound(level, c, SoundEvents.FIRECHARGE_USE, 1.0F, 0.5F + step * 0.15F);
        EnchFx.sound(level, c, SoundEvents.RAVAGER_ROAR, 0.6F, 1.5F);
    }

    // =====================================================================================
    //  Segunda e Quarta Marcha
    // =====================================================================================
    public static void gearSecond(SpellContext ctx, LivingEntity self, SpellStats stats) {
        ServerLevel level = ctx.level();
        if (self.hasEffect(EnchRegistries.GEAR_FOURTH)) {
            SILENT_END.add(self.getUUID());
            self.removeEffect(EnchRegistries.GEAR_FOURTH);
            SILENT_END.remove(self.getUUID());
        }
        boolean had = self.hasEffect(EnchRegistries.GEAR_SECOND);
        pay(self, had ? 0F : 0.04F);
        float bonus = Enchants.isEnchanter(self) ? 1.0F + 0.08F * Math.max(0, Enchants.rank(self)) : 1F;
        int ticks = Math.round(stats.duration(400) * bonus);
        // v1.1: como corpo de uma Transformação, dura a transformação inteira
        int body = com.renanpontes.isekaimahou.feature.transform_laws.TransformForge.bodyTicks(ctx, self);
        if (body > 0) ticks = body;
        self.addEffect(new MobEffectInstance(EnchRegistries.GEAR_SECOND, ticks, Math.min(2, Math.max(0, stats.amplify)), false, false, true), self);
        tell(self, Component.translatable("message.isekai_mahou.enchanter.gear_second_on", ticks / 20).withStyle(ChatFormatting.LIGHT_PURPLE));
        if (!had && !ctx.formStats().silent && body <= 0) {
            Announce.send(self, Component.translatable("announce.isekai_mahou.enchanter.gear_second"), 0xFF8FA0, Announce.STYLE_SPELL);
        }
        Vec3 c = EnchFx.center(self);
        level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 40, 0.5, 0.8, 0.5, 0.12);
        level.sendParticles(ParticleTypes.WHITE_SMOKE, c.x, c.y + 0.3, c.z, 20, 0.4, 0.6, 0.4, 0.05);
        level.sendParticles(EnchFx.dust(0xFF8FA0, 1.4F), c.x, c.y, c.z, 24, 0.4, 0.7, 0.4, 0);
        EnchFx.sound(level, c, SoundEvents.PISTON_EXTEND, 1.0F, 0.6F);
        EnchFx.sound(level, c, SoundEvents.LAVA_EXTINGUISH, 1.0F, 1.2F);
        EnchFx.sound(level, c, SoundEvents.WARDEN_HEARTBEAT, 1.2F, 1.5F);
    }

    public static void gearFourth(SpellContext ctx, LivingEntity self, SpellStats stats) {
        ServerLevel level = ctx.level();
        if (self instanceof ServerPlayer sp && !creative(sp) && !self.hasEffect(EnchRegistries.GEAR_FOURTH)) {
            MagicData d = MagicApi.get(sp);
            if (d.mana() < d.maxMana() * 0.15F) {
                tell(sp, Component.translatable("message.isekai_mahou.enchanter.gear_fourth_tired").withStyle(ChatFormatting.RED));
                return;
            }
        }
        if (self.hasEffect(EnchRegistries.GEAR_SECOND)) {
            SILENT_END.add(self.getUUID());
            self.removeEffect(EnchRegistries.GEAR_SECOND);
            SILENT_END.remove(self.getUUID());
        }
        boolean had = self.hasEffect(EnchRegistries.GEAR_FOURTH);
        int ticks = Math.max(60, stats.duration(400));
        // v1.1: como corpo de uma Transformação, dura a transformação inteira
        int body = com.renanpontes.isekaimahou.feature.transform_laws.TransformForge.bodyTicks(ctx, self);
        if (body > 0) ticks = body;
        self.addEffect(new MobEffectInstance(EnchRegistries.GEAR_FOURTH, ticks, Math.min(2, Math.max(0, stats.amplify)), false, false, true), self);
        if (!had) self.heal(20F);
        tell(self, Component.translatable("message.isekai_mahou.enchanter.gear_fourth_on", ticks / 20).withStyle(ChatFormatting.DARK_RED));
        if (!had && !ctx.formStats().silent && body <= 0) {
            Announce.send(self, Component.translatable("announce.isekai_mahou.enchanter.gear_fourth"), 0xC8303C, Announce.STYLE_ULTIMATE);
        }
        Vec3 c = EnchFx.center(self);
        level.sendParticles(ParticleTypes.CLOUD, c.x, c.y + 0.5, c.z, 60, 0.9, 1.2, 0.9, 0.15);
        level.sendParticles(EnchFx.dust(0x1A1A1A, 2.2F), c.x, c.y, c.z, 40, 0.7, 1.0, 0.7, 0);
        level.sendParticles(EnchFx.dust(0xC8303C, 1.8F), c.x, c.y, c.z, 30, 0.7, 1.0, 0.7, 0);
        for (int k = 0; k < 3; k++) EnchFx.ring(level, self.position().add(0, 0.1, 0), 1.2 + k, k == 1 ? 0xC8303C : 0x1A1A1A, 30, 1.6F);
        EnchFx.sound(level, c, SoundEvents.PUFFER_FISH_BLOW_UP, 1.5F, 0.5F);
        EnchFx.sound(level, c, SoundEvents.PUFFER_FISH_BLOW_UP, 1.5F, 0.7F);
        EnchFx.sound(level, c, SoundEvents.SLIME_JUMP, 1.2F, 0.5F);
        EnchFx.sound(level, c, SoundEvents.RAVAGER_ROAR, 0.7F, 1.2F);
    }

    // =====================================================================================
    //  Instinto Superior
    // =====================================================================================
    public static void ultraInstinct(SpellContext ctx, LivingEntity target, SpellStats stats) {
        ServerLevel level = ctx.level();
        LivingEntity caster = ctx.caster();
        int rank = Enchants.rank(caster);
        int add = 3 + 2 * Math.max(0, stats.amplify) + (rank >= 0 ? 1 + rank / 2 : 0);
        int dodges = Math.min(12, DODGES.getOrDefault(target.getUUID(), 0) + add);
        boolean had = target.hasEffect(EnchRegistries.ULTRA_INSTINCT);
        float bonus = rank >= 0 ? 1.0F + 0.1F * rank : 1F;
        int ticks = Math.round(stats.duration(160) * bonus);
        // v1.1 ("ultra instinto... vão tudo pra transformação"): como corpo de uma Transformação em quem conjura, o
        // Instinto Superior dura a transformação inteira e começa com o máximo de esquivas
        int body = com.renanpontes.isekaimahou.feature.transform_laws.TransformForge.bodyTicks(ctx, target);
        if (body > 0) {
            ticks = body;
            dodges = 12;
        }
        MobEffectInstance old = target.getEffect(EnchRegistries.ULTRA_INSTINCT);
        if (old != null) ticks = Math.max(ticks, old.getDuration());
        DODGES.put(target.getUUID(), dodges);
        target.addEffect(new MobEffectInstance(EnchRegistries.ULTRA_INSTINCT, ticks, 0, false, false, true), caster);
        tell(target, Component.translatable("message.isekai_mahou.enchanter.ultra_on", dodges, ticks / 20).withStyle(ChatFormatting.WHITE));
        if (!had && !ctx.formStats().silent && body <= 0) {
            Announce.send(target, Component.translatable("announce.isekai_mahou.enchanter.ultra"), 0xD8E4FF, Announce.STYLE_ULTIMATE);
        }
        Vec3 c = EnchFx.center(target);
        level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 30, 0.4, 0.8, 0.4, 0.05);
        level.sendParticles(EnchFx.dust(0xD8E4FF, 1.4F), c.x, c.y, c.z, 30, 0.5, 0.9, 0.5, 0);
        EnchFx.ring(level, target.position().add(0, 0.1, 0), 1.3, 0xFFFFFF, 30, 1.0F);
        EnchFx.sound(level, c, SoundEvents.BEACON_ACTIVATE, 0.9F, 2.0F);
        EnchFx.sound(level, c, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.6F);
    }

    public static int dodgesLeft(LivingEntity e) {
        return DODGES.getOrDefault(e.getUUID(), 0);
    }

    // =====================================================================================
    //  Inverter (num inimigo): versões "maldição"
    // =====================================================================================
    public static void curse(SpellContext ctx, LivingEntity t, SpellStats stats, TransformEffect.Kind kind) {
        ServerLevel level = ctx.level();
        Vec3 c = EnchFx.center(t);
        switch (kind) {
            case GATES -> {
                ctx.damage(t, 4F, stats, null);
                ctx.status(t, MobEffects.WEAKNESS, 120, 1, stats);
                ctx.status(t, MobEffects.MOVEMENT_SLOWDOWN, 120, 1, stats);
                Statuses.apply(t, Statuses.Kind.EXPOSED, 1, stats.duration(120), ctx.caster(), ctx.powerMult());
                for (int k = 0; k < 8; k++) EnchFx.ring(level, c.add(0, -0.4 + k * 0.12, 0), 0.6, GATE_COLOR[k], 10, 0.8F);
                EnchFx.sound(level, c, SoundEvents.IRON_DOOR_CLOSE, 1.0F, 0.6F);
            }
            case KAIOKEN -> {
                ctx.damage(t, 6F, stats, null);
                Statuses.apply(t, Statuses.Kind.BURNING, 2, stats.duration(100), ctx.caster(), ctx.powerMult());
                level.sendParticles(ParticleTypes.FLAME, c.x, c.y, c.z, 24, 0.4, 0.6, 0.4, 0.05);
                level.sendParticles(EnchFx.dust(0xFF3B30, 1.5F), c.x, c.y, c.z, 20, 0.4, 0.6, 0.4, 0);
                EnchFx.sound(level, c, SoundEvents.BLAZE_SHOOT, 0.9F, 0.8F);
            }
            case GEAR_SECOND -> {
                ctx.damage(t, 5F, stats, null);
                ctx.status(t, MobEffects.BLINDNESS, 40, 0, stats);
                level.sendParticles(ParticleTypes.CLOUD, c.x, c.y, c.z, 30, 0.5, 0.5, 0.5, 0.12);
                EnchFx.sound(level, c, SoundEvents.LAVA_EXTINGUISH, 1.0F, 1.0F);
            }
            case GEAR_FOURTH -> {
                ctx.damage(t, 12F, stats, null);
                Vec3 dir = ctx.caster() != null ? t.position().subtract(ctx.caster().position()).multiply(1, 0, 1) : ctx.look();
                dir = dir.lengthSqr() < 1.0E-4 ? ctx.look().multiply(1, 0, 1) : dir.normalize();
                double kb = 2.2 * (1.0 - t.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                t.push(dir.x * kb, 0.5, dir.z * kb);
                t.hurtMarked = true;
                level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 2, 0.3, 0.3, 0.3, 0);
                level.sendParticles(EnchFx.dust(0x1A1A1A, 2.0F), c.x, c.y, c.z, 24, 0.5, 0.5, 0.5, 0);
                EnchFx.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.8F, 0.8F);
                EnchFx.sound(level, c, SoundEvents.SLIME_JUMP, 1.2F, 0.5F);
            }
            case ULTRA_INSTINCT -> {
                ctx.damage(t, 3F, stats, null);
                ctx.status(t, MobEffects.CONFUSION, 100, 0, stats);
                ctx.status(t, MobEffects.MOVEMENT_SLOWDOWN, 80, 1, stats);
                ctx.status(t, MobEffects.WEAKNESS, 80, 0, stats);
                level.sendParticles(EnchFx.dust(0x8A94B0, 1.2F), c.x, c.y, c.z, 20, 0.4, 0.5, 0.4, 0);
                EnchFx.sound(level, c, SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.8F, 0.6F);
            }
        }
    }

    // =====================================================================================
    //  Tique (chamado pelo efeito, todo tick, no servidor)
    // =====================================================================================
    /** @return false = a transformação acabou à força (o efeito sai) */
    public static boolean tick(TransformEffect.Kind kind, LivingEntity e, int amp) {
        if (!(e.level() instanceof ServerLevel level)) return true;
        int t = e.tickCount;
        switch (kind) {
            case GATES -> {
                int gate = Math.min(8, amp + 1);
                if (t % 2 == 0) gatesAura(level, e, gate, t);
                if (t % 20 == 0 && gate >= 4) pay(e, 0.004F * (gate - 3));
            }
            case KAIOKEN -> {
                int step = Math.min(2, amp);
                if (t % 2 == 0) kaiokenAura(level, e, step, t);
                if (t % 20 == 0 && !creative(e)) {
                    float drain = e.getMaxHealth() * KAIOKEN_DRAIN[step];
                    if (e.getHealth() - drain <= 1.0F) {
                        e.setHealth(1F);
                        BROKEN.add(e.getUUID());
                        return false;
                    }
                    e.setHealth(e.getHealth() - drain);
                }
            }
            case GEAR_SECOND -> {
                if (t % 2 == 0) steam(level, e, t);
                if (t % 40 == 0) pay(e, 0.01F);
            }
            case GEAR_FOURTH -> {
                if (t % 2 == 0) boundman(level, e, t);
                if (t % 20 == 0 && e instanceof ServerPlayer sp && !creative(sp)) {
                    MagicData d = MagicApi.get(sp);
                    if (!d.consumeMana(d.maxMana() * 0.03F)) {
                        d.setMana(0);
                        tell(sp, Component.translatable("message.isekai_mahou.enchanter.gear_fourth_out").withStyle(ChatFormatting.RED));
                        return false;
                    }
                }
            }
            case ULTRA_INSTINCT -> {
                if (t % 2 == 0) silverAura(level, e, t);
                if (dodgesLeft(e) <= 0) return false;
            }
        }
        return true;
    }

    private static void gatesAura(ServerLevel level, LivingEntity e, int gate, int t) {
        int rgb = GATE_COLOR[gate - 1];
        double h = e.getBbHeight();
        double r = Math.max(0.5, e.getBbWidth() * 0.9);
        for (int k = 0; k < Math.min(4, 1 + gate / 2); k++) {
            double a = t * 0.3 + k * Math.PI * 2 / Math.min(4, 1 + gate / 2);
            double y = e.getY() + (t * 0.07 + k * 0.4) % h;
            level.sendParticles(EnchFx.dust(rgb, 0.9F + gate * 0.06F), e.getX() + Math.cos(a) * r, y, e.getZ() + Math.sin(a) * r, 1, 0, 0.02, 0, 0);
        }
        // do 6º portão em diante o suor vira vapor; no 8º, vapor de sangue
        if (gate >= 6 && t % 4 == 0) {
            level.sendParticles(gate >= 8 ? EnchFx.dust(0xB01818, 1.8F) : ParticleTypes.CLOUD, e.getX(), e.getY() + h * 0.7, e.getZ(),
                    gate >= 8 ? 4 : 2, 0.3, 0.3, 0.3, 0.03);
        }
        if (gate >= 8 && t % 2 == 0) {
            level.sendParticles(ParticleTypes.WHITE_SMOKE, e.getX(), e.getY() + h, e.getZ(), 2, 0.25, 0.1, 0.25, 0.04);
            EnchFx.ring(level, e.position().add(0, 0.05, 0), 1.0 + (t % 10) * 0.12, 0xFF2A2A, 14, 1.0F);
        }
        if (t % 40 == 0) EnchFx.sound(level, e.position(), SoundEvents.WARDEN_HEARTBEAT, 0.5F, 0.8F + gate * 0.06F);
    }

    private static void kaiokenAura(ServerLevel level, LivingEntity e, int step, int t) {
        double h = e.getBbHeight();
        double r = Math.max(0.55, e.getBbWidth() * 0.95) + step * 0.15;
        int n = 3 + step * 3;
        for (int k = 0; k < n; k++) {
            double a = level.random.nextDouble() * Math.PI * 2;
            double y = e.getY() + level.random.nextDouble() * h * 1.15;
            // chama da aura: mais larga embaixo, afina em cima
            double rr = r * (1.0 - (y - e.getY()) / (h * 1.6));
            level.sendParticles(EnchFx.dust(k % 3 == 0 ? 0xFFB0A0 : 0xFF3B30, 1.1F + step * 0.3F), e.getX() + Math.cos(a) * rr, y,
                    e.getZ() + Math.sin(a) * rr, 0, 0, 1, 0, 0.08);
        }
        if (t % 6 == 0) level.sendParticles(ParticleTypes.FLAME, e.getX(), e.getY() + 0.2, e.getZ(), 1 + step, 0.3, 0.1, 0.3, 0.02);
        if (t % 30 == 0) EnchFx.sound(level, e.position(), SoundEvents.FIRE_AMBIENT, 1.0F, 0.6F);
    }

    private static void steam(ServerLevel level, LivingEntity e, int t) {
        double h = e.getBbHeight();
        level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + h * 0.6, e.getZ(), 1, 0.3, 0.4, 0.3, 0.02);
        if (t % 4 == 0) level.sendParticles(EnchFx.dust(0xFF8FA0, 0.8F), e.getX(), e.getY() + h * 0.5, e.getZ(), 2, 0.3, 0.4, 0.3, 0);
        if (e.isSprinting()) level.sendParticles(ParticleTypes.WHITE_SMOKE, e.getX(), e.getY() + 0.3, e.getZ(), 2, 0.2, 0.1, 0.2, 0.01);
        if (t % 40 == 0) EnchFx.sound(level, e.position(), SoundEvents.LAVA_EXTINGUISH, 0.3F, 1.6F);
    }

    private static void boundman(ServerLevel level, LivingEntity e, int t) {
        double h = e.getBbHeight();
        level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + h * 0.85, e.getZ(), 1, 0.4, 0.1, 0.4, 0.03);
        if (t % 4 == 0) {
            double a = t * 0.2;
            for (int k = 0; k < 2; k++) {
                double ang = a + Math.PI * k;
                level.sendParticles(EnchFx.dust(k == 0 ? 0x1A1A1A : 0xC8303C, 1.4F), e.getX() + Math.cos(ang) * 0.9, e.getY() + h * 0.5,
                        e.getZ() + Math.sin(ang) * 0.9, 1, 0, 0, 0, 0);
            }
        }
        if (e.onGround() && t % 10 == 0) EnchFx.sound(level, e.position(), SoundEvents.SLIME_SQUISH, 0.5F, 0.6F);
    }

    private static void silverAura(ServerLevel level, LivingEntity e, int t) {
        double h = e.getBbHeight();
        double a = t * 0.25;
        for (int k = 0; k < 2; k++) {
            double ang = a + Math.PI * k;
            double y = e.getY() + (t * 0.09 + k * 0.5) % h;
            level.sendParticles(EnchFx.dust(k == 0 ? 0xFFFFFF : 0xA8B8E8, 0.8F), e.getX() + Math.cos(ang) * 0.6, y, e.getZ() + Math.sin(ang) * 0.6,
                    1, 0, 0, 0, 0);
        }
        if (t % 8 == 0) level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + h * 0.8, e.getZ(), 1, 0.3, 0.3, 0.3, 0.01);
    }

    // =====================================================================================
    //  Fim de cada transformação (expirou ou foi tirada)
    // =====================================================================================
    private static void ended(LivingEntity e, @Nullable MobEffectInstance inst) {
        if (inst == null || e.level().isClientSide) return;
        Holder<MobEffect> eff = inst.getEffect();
        if (!(eff.value() instanceof TransformEffect te)) return;
        UUID id = e.getUUID();
        if (te.kind() == TransformEffect.Kind.ULTRA_INSTINCT) {
            DODGES.remove(id);
            COUNTER.remove(id);
        }
        if (SILENT_END.contains(id) || !e.isAlive() || !(e.level() instanceof ServerLevel level)) {
            BROKEN.remove(id);
            return;
        }
        switch (te.kind()) {
            case GATES -> {
                int gate = inst.getAmplifier() + 1;
                if (gate >= 8) {
                    if (!creative(e)) e.setHealth(1F);
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600, 2));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 600, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 600, 1));
                    GATE_LOCK.put(id, level.getGameTime() + GATE_LOCK_TICKS);
                    tell(e, Component.translatable("message.isekai_mahou.enchanter.death_gate_end").withStyle(ChatFormatting.DARK_RED));
                    level.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1, e.getZ(), 30, 0.4, 0.6, 0.4, 0.03);
                    level.sendParticles(EnchFx.dust(0x6A0A0A, 1.5F), e.getX(), e.getY() + 1, e.getZ(), 30, 0.4, 0.6, 0.4, 0);
                    EnchFx.sound(level, e.position(), SoundEvents.WARDEN_HEARTBEAT, 1.5F, 0.4F);
                } else if (gate >= 5) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 0));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
                    tell(e, Component.translatable("message.isekai_mahou.enchanter.gates_end").withStyle(ChatFormatting.GRAY));
                }
                level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 1, e.getZ(), 12, 0.3, 0.5, 0.3, 0.03);
            }
            case KAIOKEN -> {
                if (BROKEN.remove(id)) {
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 160, 1));
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 1));
                    tell(e, Component.translatable("message.isekai_mahou.enchanter.kaioken_break").withStyle(ChatFormatting.DARK_RED));
                    EnchFx.sound(level, e.position(), SoundEvents.FIRE_EXTINGUISH, 1.0F, 0.6F);
                } else if (inst.getAmplifier() >= 2) {
                    e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
                }
                level.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 1, e.getZ(), 14, 0.3, 0.5, 0.3, 0.02);
            }
            case GEAR_SECOND -> {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0));
                level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            }
            case GEAR_FOURTH -> {
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
                tell(e, Component.translatable("message.isekai_mahou.enchanter.gear_fourth_end").withStyle(ChatFormatting.GRAY));
                level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 1, e.getZ(), 40, 0.6, 0.8, 0.6, 0.08);
                EnchFx.sound(level, e.position(), SoundEvents.PUFFER_FISH_BLOW_OUT, 1.5F, 0.5F);
            }
            case ULTRA_INSTINCT -> {
                tell(e, Component.translatable("message.isekai_mahou.enchanter.ultra_end").withStyle(ChatFormatting.GRAY));
                level.sendParticles(ParticleTypes.END_ROD, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.5, 0.3, 0.03);
            }
        }
    }

    // =====================================================================================
    //  Eventos de combate
    // =====================================================================================
    /** Instinto Superior: esquiva automática (chefes acertam 50% das vezes mesmo assim). */
    private static void onDodge(LivingIncomingDamageEvent e) {
        LivingEntity t = e.getEntity();
        if (!(t.level() instanceof ServerLevel level) || !t.hasEffect(EnchRegistries.ULTRA_INSTINCT)) return;
        DamageSource src = e.getSource();
        Entity att = src.getEntity();
        if (att == null || att == t || src.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        int left = dodgesLeft(t);
        if (left <= 0) return;
        if (att instanceof LivingEntity la && Enchants.isBoss(la) && t.getRandom().nextFloat() < 0.5F) return;
        e.setCanceled(true);
        DODGES.put(t.getUUID(), left - 1);
        COUNTER.put(t.getUUID(), level.getGameTime() + COUNTER_WINDOW);
        sidestep(level, t, att);
        tell(t, Component.translatable("message.isekai_mahou.enchanter.ultra_dodge", left - 1).withStyle(ChatFormatting.WHITE));
        if (t instanceof ServerPlayer sp) MagicApi.addCounter(sp, "enchanter.dodges", 1);
    }

    /** Desvia para o lado (ou para trás) sem atravessar blocos; deixa uma imagem prateada no lugar. */
    private static void sidestep(ServerLevel level, LivingEntity t, Entity att) {
        Vec3 away = t.position().subtract(att.position()).multiply(1, 0, 1);
        if (away.lengthSqr() < 1.0E-4) away = t.getLookAngle().multiply(-1, 0, -1);
        if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
        away = away.normalize();
        Vec3 side = new Vec3(-away.z, 0, away.x);
        if (t.getRandom().nextBoolean()) side = side.scale(-1);
        Vec3 old = t.position();
        for (Vec3 off : List.of(side.scale(1.6), side.scale(-1.6), away.scale(1.4), side.scale(0.8))) {
            if (level.noCollision(t, t.getBoundingBox().move(off))) {
                t.teleportTo(old.x + off.x, old.y, old.z + off.z);
                t.fallDistance = 0;
                break;
            }
        }
        double h = t.getBbHeight();
        for (int i = 0; i < 10; i++) {
            level.sendParticles(EnchFx.dust(i % 2 == 0 ? 0xFFFFFF : 0xA8B8E8, 1.0F), old.x, old.y + h * i / 10.0, old.z, 2, 0.15, 0.02, 0.15, 0);
        }
        level.sendParticles(ParticleTypes.END_ROD, old.x, old.y + h * 0.5, old.z, 6, 0.2, 0.4, 0.2, 0.02);
        EnchFx.sound(level, old, SoundEvents.PHANTOM_FLAP, 0.8F, 1.8F);
        EnchFx.sound(level, old, SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0F, 1.6F);
    }

    /** Golpes corpo a corpo: contra-ataque do Instinto; onda em área da Quarta Marcha e dos portões 6+. */
    private static void onMelee(LivingIncomingDamageEvent e) {
        if (splashing) return;
        DamageSource src = e.getSource();
        if (!(src.getEntity() instanceof LivingEntity att) || src.getDirectEntity() != att) return;
        LivingEntity victim = e.getEntity();
        if (victim == att || !(victim.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();
        Long until = COUNTER.get(att.getUUID());
        if (until != null && now <= until) {
            COUNTER.remove(att.getUUID());
            e.setAmount(e.getAmount() * 1.5F);
            Vec3 c = EnchFx.center(victim);
            level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.4);
            level.sendParticles(EnchFx.dust(0xFFFFFF, 1.2F), c.x, c.y, c.z, 10, 0.3, 0.3, 0.3, 0);
            EnchFx.sound(level, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 1.4F);
        }
        boolean fourth = att.hasEffect(EnchRegistries.GEAR_FOURTH);
        int gate = gate(att);
        if (att.hasEffect(EnchRegistries.KAIOKEN)) {
            Vec3 c = EnchFx.center(victim);
            level.sendParticles(EnchFx.dust(0xFF3B30, 1.3F), c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0);
        }
        if (!fourth && gate < 6) return;
        Long last = SPLASH_CD.get(att.getUUID());
        if (last != null && now - last < 12) return;
        if (SPLASH_CD.size() > 512) SPLASH_CD.clear();
        SPLASH_CD.put(att.getUUID(), now);
        float frac = fourth ? 0.6F : gate >= 8 ? 0.7F : 0.4F;
        double r = fourth ? 3.5 : 2.5;
        float amount = e.getAmount() * frac;
        Vec3 at = EnchFx.center(victim);
        splashing = true;
        try {
            for (LivingEntity o : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r),
                    x -> x != victim && x != att && x.isAlive() && splashTarget(att, x) && x.distanceToSqr(at) <= r * r)) {
                o.invulnerableTime = 0;
                o.hurt(src, amount);
                Vec3 away = o.position().subtract(victim.position()).multiply(1, 0, 1);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(0, 0, 1) : away.normalize();
                double kb = (fourth ? 1.2 : 0.7) * (1.0 - o.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
                o.push(away.x * kb, 0.35, away.z * kb);
                o.hurtMarked = true;
            }
        } finally {
            splashing = false;
        }
        if (fourth) {
            // "canhão": o punho gigante abre uma onda de choque
            level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4, 0.3, 0.4, 0);
            EnchFx.ring(level, victim.position().add(0, 0.2, 0), r, 0x1A1A1A, 30, 1.6F);
            EnchFx.ring(level, victim.position().add(0, 0.4, 0), r * 0.7, 0xC8303C, 24, 1.3F);
            EnchFx.sound(level, at, SoundEvents.GENERIC_EXPLODE.value(), 0.6F, 1.1F);
        } else {
            int rgb = GATE_COLOR[Math.min(8, gate) - 1];
            EnchFx.ring(level, victim.position().add(0, 0.3, 0), r, rgb, 26, 1.3F);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 2, 0.4, 0.2, 0.4, 0);
            EnchFx.sound(level, at, SoundEvents.PLAYER_ATTACK_SWEEP, 1.0F, 0.6F);
        }
    }

    /** Quem a onda dos socos pode atingir: inimigos (monstros, quem mira você, jogadores fora do grupo com PvP). */
    private static boolean splashTarget(LivingEntity owner, LivingEntity x) {
        if (friendly(owner, x)) return false;
        return x instanceof Enemy || (x instanceof Mob m && m.getTarget() == owner) || x instanceof Player;
    }

    public static boolean friendly(LivingEntity owner, Entity e) {
        if (e == owner) return true;
        if (e instanceof OwnableEntity o && owner.getUUID().equals(o.getOwnerUUID())) return true;
        if (owner.isAlliedTo(e)) return true;
        Boolean social = Party.allyOverride(owner, e);
        if (social != null) return social;
        return owner instanceof Player && e instanceof Player && owner.level().getServer() != null && !owner.level().getServer().isPvpAllowed();
    }

    /** As técnicas (dano de magia) de quem está transformado também ficam mais fortes. */
    private static void onSpellDamage(SpellDamageEvent.Pre e) {
        LivingEntity caster = e.ctx().caster();
        if (caster == null || e.target() == caster) return;
        float mult = 1F;
        int step = kaiokenStep(caster);
        if (step >= 0) mult *= KAIOKEN_SPELL[step];
        int gate = gate(caster);
        if (gate > 0) mult *= 1F + 0.06F * gate;
        if (caster.hasEffect(EnchRegistries.GEAR_SECOND)) mult *= 1.15F;
        if (caster.hasEffect(EnchRegistries.GEAR_FOURTH)) mult *= 1.4F;
        if (mult != 1F) e.setAmount(e.getAmount() * mult);
    }

    // =====================================================================================
    //  Utilidades
    // =====================================================================================
    private static final String GATE_NAME_KEY = "message.isekai_mahou.enchanter.gate_name.";

    /** Nome do portão (1..8). */
    public static Component gateName(int gate) {
        return Component.translatable(GATE_NAME_KEY + Math.max(1, Math.min(8, gate)));
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            default -> String.valueOf(n);
        };
    }

    private static String fmt(float v) {
        return Mth.equal(v, Math.round(v)) ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    /** Saiu do servidor: limpa o estado curto (a trava dos portões fica até vencer). */
    static void forget(UUID id) {
        DODGES.remove(id);
        COUNTER.remove(id);
        SPLASH_CD.remove(id);
        BROKEN.remove(id);
        SILENT_END.remove(id);
    }

    public static void clear() {
        GATE_LOCK.clear();
        DODGES.clear();
        COUNTER.clear();
        SPLASH_CD.clear();
        BROKEN.clear();
        SILENT_END.clear();
        splashing = false;
    }

    private Transformations() {}
}
