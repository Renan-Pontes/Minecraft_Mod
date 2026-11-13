package com.renanpontes.isekaimahou.feature.legends_i;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.legends_i.effect.AmaterasuEffect;
import com.renanpontes.isekaimahou.feature.legends_i.glyph.OnePieceGlyphs;
import com.renanpontes.isekaimahou.feature.legends_i.glyph.UchihaGlyphs;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Regras de jogo dos glifos de anime (servidor):
 * <ul>
 *   <li><b>Haki do Armamento</b> (aumento): o dano da magia +10% por cópia (máx. 3), ignora 30% por cópia (máx. 75%) da
 *       redução da armadura, das proteções de encantamento e da Resistência, desfaz metade das reduções "intangíveis"
 *       (forma Fantasma, peles mágicas...) e arranca a Invisibilidade do alvo. Ao conjurar: braços negros por 3 s.</li>
 *   <li><b>Asas de Papel</b> (aumento): ao conjurar, asas de papel por 10 s (+5 s por cópia; máx. 30 s).</li>
 *   <li><b>Haki da Observação</b>: desvio do "futuro" e a varredura de presenças a cada 2 s.</li>
 *   <li><b>Susanoo</b>: −35% de dano recebido (−20% de chefes), corte espectral nos golpes, Fraqueza quando acaba.</li>
 *   <li><b>Haki do Rei</b>: quem está apavorado não consegue mirar em ninguém.</li>
 * </ul>
 */
public final class LiEvents {
    /** Alvo do dano de magia com Haki do Armamento em andamento (o hurt vem logo depois do evento Pre). */
    @Nullable
    private static LivingEntity hakiTarget;
    private static int hakiStacks;
    private static long hakiTick;
    /** jogador → até quando não pode desviar de novo (Observação). */
    private static final Map<UUID, Long> DODGE_CD = new HashMap<>();
    /** dono do Susanoo → próximo tick em que o corte espectral pode sair. */
    private static final Map<UUID, Long> SLASH_CD = new HashMap<>();
    /** conjurador → próximo tick em que os braços negros do Haki podem ser reenviados. */
    private static final Map<UUID, Long> HAKI_FX_CD = new HashMap<>();

    public static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOW, SpellDamageEvent.Pre.class, LiEvents::hakiPre);
        bus.addListener(SpellDamageEvent.Post.class, e -> hakiTarget = null);
        bus.addListener(EventPriority.HIGH, LivingIncomingDamageEvent.class, LiEvents::dodge);
        bus.addListener(LivingIncomingDamageEvent.class, LiEvents::incoming);
        bus.addListener(EventPriority.LOWEST, LivingIncomingDamageEvent.class, LiEvents::hakiPierce);
        bus.addListener(SpellCastEvent.Post.class, LiEvents::castPost);
        bus.addListener(LivingChangeTargetEvent.class, LiEvents::changeTarget);
        bus.addListener(MobEffectEvent.Expired.class, e -> {
            if (e.getEffectInstance() != null) effectEnded(e.getEntity(), e.getEffectInstance().getEffect());
        });
        bus.addListener(MobEffectEvent.Remove.class, e -> effectEnded(e.getEntity(), e.getEffect()));
        bus.addListener(PlayerTickEvent.Post.class, LiEvents::playerTick);
        bus.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> forget(e.getEntity().getUUID()));
    }

    // =====================================================================================
    //  Haki do Armamento
    // =====================================================================================
    private static void hakiPre(SpellDamageEvent.Pre e) {
        SpellContext ctx = e.ctx();
        int n = Math.min(3, LiGlyphs.stacks(ctx, e.stats(), LiGlyphs.HAKI_ARMAMENT));
        if (n <= 0) {
            hakiTarget = null;
            return;
        }
        LivingEntity t = e.target();
        e.setAmount(e.getAmount() * (1F + 0.1F * n));
        hakiTarget = t;
        hakiStacks = n;
        hakiTick = ctx.level().getGameTime();
        if (t.hasEffect(MobEffects.INVISIBILITY)) t.removeEffect(MobEffects.INVISIBILITY);
        ServerLevel l = ctx.level();
        Vec3 c = LiUtil.center(t);
        ElementFx.sendFar(l, ElementFx.glow(0x14081C, 1.3F), c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0.08);
        ElementFx.sendFar(l, ElementFx.glow(OnePieceGlyphs.HAKI_RED, 0.7F), c.x, c.y, c.z, 3, 0.2, 0.3, 0.2, 0.15);
    }

    private static boolean hakiHit(LivingEntity e) {
        return hakiTarget == e && e.level().getGameTime() == hakiTick;
    }

    private static void incoming(LivingIncomingDamageEvent e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide) return;
        if (hakiHit(t)) {
            float keep = 1F - Math.min(0.75F, 0.3F * hakiStacks);
            e.addReductionModifier(DamageContainer.Reduction.ARMOR, (c, r) -> r * keep);
            e.addReductionModifier(DamageContainer.Reduction.ENCHANTMENTS, (c, r) -> r * keep);
            e.addReductionModifier(DamageContainer.Reduction.MOB_EFFECTS, (c, r) -> r * keep);
        }
        // Susanoo: a armadura espectral segura o golpe
        if (t.hasEffect(LiRegistry.SUSANOO) && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            Entity src = e.getSource().getEntity();
            boolean boss = src instanceof LivingEntity le && LiUtil.boss(le);
            e.setAmount(e.getAmount() * (boss ? 0.8F : 0.65F));
        }
        // Susanoo: golpe corpo a corpo de quem está no Susanoo solta o corte espectral
        Entity src = e.getSource().getEntity();
        if (src instanceof LivingEntity att && e.getSource().getDirectEntity() == att && att != t && att.hasEffect(LiRegistry.SUSANOO)
                && (e.getSource().is(DamageTypes.PLAYER_ATTACK) || e.getSource().is(DamageTypes.MOB_ATTACK))
                && att.level() instanceof ServerLevel sl) {
            long now = sl.getGameTime();
            Long next = SLASH_CD.get(att.getUUID());
            if (next == null || next <= now) {
                SLASH_CD.put(att.getUUID(), now + 15);
                float power = UchihaGlyphs.Susanoo.POWER.getOrDefault(att.getUUID(), 1F);
                UchihaGlyphs.Susanoo.slash(att, att.getLookAngle(), 6F, UchihaGlyphs.Susanoo.SLASH * power * Progression.DAMAGE_SCALE, 0.6, 8);
            }
        }
    }

    /** Por último: o Haki acerta o "intangível" — desfaz metade do que os outros ouvintes tiraram do golpe. */
    private static void hakiPierce(LivingIncomingDamageEvent e) {
        LivingEntity t = e.getEntity();
        if (!hakiHit(t)) return;
        hakiTarget = null;
        float orig = e.getOriginalAmount();
        if (e.getAmount() < orig) e.setAmount(e.getAmount() + (orig - e.getAmount()) * 0.5F);
    }

    // =====================================================================================
    //  Haki da Observação — o desvio
    // =====================================================================================
    private static void dodge(LivingIncomingDamageEvent e) {
        LivingEntity t = e.getEntity();
        if (!(t.level() instanceof ServerLevel sl)) return;
        MobEffectInstance inst = t.getEffect(LiRegistry.OBSERVATION);
        if (inst == null || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Entity src = e.getSource().getEntity();
        if (!(src instanceof LivingEntity att) || att == t) return;
        long now = sl.getGameTime();
        Long cd = DODGE_CD.get(t.getUUID());
        if (cd != null && cd > now) return;
        if (sl.random.nextFloat() >= OnePieceGlyphs.Observation.dodgeChance(inst.getAmplifier(), LiUtil.boss(att))) return;
        DODGE_CD.put(t.getUUID(), now + 30);
        e.setCanceled(true);
        // passo para o lado, longe do golpe
        Vec3 from = att.position().subtract(t.position());
        Vec3 side = new Vec3(-from.z, 0, from.x);
        if (side.lengthSqr() > 1.0E-4) {
            side = side.normalize().scale(sl.random.nextBoolean() ? 0.55 : -0.55);
            t.push(side.x, 0.08, side.z);
            t.hurtMarked = true;
        }
        Vec3 c = LiUtil.center(t);
        ElementFx.sendFar(sl, ElementFx.glow(OnePieceGlyphs.HAKI_RED, 1.1F), c.x, c.y, c.z, 10, 0.3, 0.6, 0.3, 0.01);
        sl.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        LiUtil.sound(sl, c, SoundEvents.PLAYER_ATTACK_NODAMAGE, 1.0F, 1.4F);
        LiUtil.actionbar(t, Component.translatable("message.isekai_mahou.legends_i.foresight").withStyle(ChatFormatting.RED));
    }

    // =====================================================================================
    //  Conjuração: braços negros do Haki e Asas de Papel
    // =====================================================================================
    private static void castPost(SpellCastEvent.Post e) {
        LivingEntity c = e.getCaster();
        if (c == null || c.level().isClientSide) return;
        ParsedSpell spell = e.getContext().spell();
        long now = c.level().getGameTime();
        if (spell.anyStats(s -> s.custom(LiGlyphs.HAKI_ARMAMENT) > 0)) {
            Long next = HAKI_FX_CD.get(c.getUUID());
            if (next == null || next <= now) {
                HAKI_FX_CD.put(c.getUUID(), now + 40);
                LiUtil.state(c, LiNet.S_HAKI, 60);
            }
        }
        int wings = 0;
        for (ParsedSpell st : spell.stages()) {
            if (st == null) continue;
            wings = Math.max(wings, st.formStats().custom(LiGlyphs.PAPER_WINGS));
            for (ParsedSpell.EffectEntry en : st.effects()) wings = Math.max(wings, en.stats().custom(LiGlyphs.PAPER_WINGS));
        }
        if (wings > 0) {
            int ticks = Math.min(600, 200 + 100 * (wings - 1));
            c.addEffect(new MobEffectInstance(LiRegistry.PAPER_WINGS, ticks, 0, false, false, true), c);
            c.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 30, 0, false, false, false), c);
            LiUtil.state(c, LiNet.S_WINGS, ticks);
            if (c.level() instanceof ServerLevel sl) {
                LiUtil.sound(sl, c.position(), SoundEvents.BOOK_PAGE_TURN, 1.2F, 0.7F);
                LiUtil.sound(sl, c.position(), SoundEvents.PHANTOM_FLAP, 0.9F, 1.3F);
            }
        }
    }

    // =====================================================================================
    //  Haki do Rei — apavorados não miram
    // =====================================================================================
    private static void changeTarget(LivingChangeTargetEvent e) {
        if (e.getNewAboutToBeSetTarget() == null) return;
        Long until = OnePieceGlyphs.Conqueror.FEARED.get(e.getEntity().getUUID());
        if (until == null) return;
        if (until > e.getEntity().level().getGameTime()) e.setCanceled(true);
        else OnePieceGlyphs.Conqueror.FEARED.remove(e.getEntity().getUUID());
    }

    // =====================================================================================
    //  Ticks e fim de efeitos
    // =====================================================================================
    private static void playerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp)) return;
        if (sp.hasEffect(LiRegistry.PAPER_WINGS)) sp.resetFallDistance();
        if (sp.tickCount % 40 == 0 && sp.hasEffect(LiRegistry.OBSERVATION)) {
            Float r = OnePieceGlyphs.Observation.SENSE_RADIUS.get(sp.getUUID());
            if (r != null) OnePieceGlyphs.Observation.sense(sp, r, 50);
        }
    }

    private static void effectEnded(LivingEntity e, @Nullable Holder<MobEffect> effect) {
        if (effect == null || e.level().isClientSide) return;
        if (effect.is(LiRegistry.SUSANOO)) {
            UchihaGlyphs.Susanoo.POWER.remove(e.getUUID());
            LiUtil.state(e, LiNet.S_SUSANOO, 0);
            // o Susanoo cobra o corpo (não em quem acabou de morrer)
            if (!e.isAlive() || e.isDeadOrDying()) return;
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 0, false, true));
            if (e.level() instanceof ServerLevel sl) {
                ElementFx.sendFar(sl, ElementFx.glow(UchihaGlyphs.SUSANOO, 1.4F), e.getX(), e.getY() + 1.2, e.getZ(), 16, 0.6, 0.8, 0.6, 0.05);
                LiUtil.sound(sl, e.position(), SoundEvents.BEACON_DEACTIVATE, 0.8F, 0.7F);
            }
        } else if (effect.is(LiRegistry.PAPER_WINGS)) {
            LiUtil.state(e, LiNet.S_WINGS, 0);
            e.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 40, 0, false, false, false));
        } else if (effect.is(LiRegistry.OBSERVATION)) {
            OnePieceGlyphs.Observation.SENSE_RADIUS.remove(e.getUUID());
        } else if (effect.is(LiRegistry.AMATERASU)) {
            AmaterasuEffect.forget(e.getUUID());
        }
    }

    static void forget(UUID id) {
        DODGE_CD.remove(id);
        SLASH_CD.remove(id);
        HAKI_FX_CD.remove(id);
        OnePieceGlyphs.Observation.SENSE_RADIUS.remove(id);
        UchihaGlyphs.Susanoo.POWER.remove(id);
    }

    /** Limpeza periódica (a cada 5 s). */
    static void cleanup(long now) {
        DODGE_CD.values().removeIf(t -> t < now);
        SLASH_CD.values().removeIf(t -> t < now);
        HAKI_FX_CD.values().removeIf(t -> t < now);
        OnePieceGlyphs.Conqueror.FEARED.values().removeIf(t -> t < now);
        com.renanpontes.isekaimahou.feature.legends_i.glyph.PainGlyphs.Shinra.LOCKED.values().removeIf(t -> t < now);
    }

    static void clear() {
        hakiTarget = null;
        DODGE_CD.clear();
        SLASH_CD.clear();
        HAKI_FX_CD.clear();
        OnePieceGlyphs.Observation.SENSE_RADIUS.clear();
        OnePieceGlyphs.Conqueror.FEARED.clear();
        UchihaGlyphs.Susanoo.POWER.clear();
        com.renanpontes.isekaimahou.feature.legends_i.glyph.PainGlyphs.Shinra.LOCKED.clear();
        AmaterasuEffect.clear();
    }

    private LiEvents() {}
}
