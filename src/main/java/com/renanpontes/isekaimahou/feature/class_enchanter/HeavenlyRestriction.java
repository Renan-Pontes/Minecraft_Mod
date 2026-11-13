package com.renanpontes.isekaimahou.feature.class_enchanter;

import com.renanpontes.isekaimahou.feature.class_enchanter.EnchFxNet;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * RESTRIÇÃO CELESTIAL (homenagem: o lutador que nasceu sem energia e por isso tem o corpo mais forte). Encantamento de
 * assinatura, só em si mesmo, níveis I–V:
 * <ul>
 *   <li>ao ativar, TODO o recurso é consumido e fica travado em zero (regen parada): enquanto durar, não conjura nada;</li>
 *   <li>corpo absurdo por nível: +2 de dano e +12%, +7% velocidade, +10% ataque, armadura/resistência/firmeza, +4 de vida,
 *   pulo, queda segura, degrau — e −5% de todo dano recebido por nível;</li>
 *   <li>sentidos: visão noturna e os inimigos por perto "marcados" (só ele vê);</li>
 *   <li>invisível para detecção mágica: sem Brilho/Revelar/Exposto, criaturas mágicas só o notam de perto;</li>
 *   <li>imune ao Acerto Garantido de domínios/barreiras (o domínio mira mana — ele não tem): pulsos de domínio não o
 *   atingem; e imune a Silêncio.</li>
 * </ul>
 * Para encerrar antes: agachar + trocar de mão (F). Inverter num inimigo: "Selo Celestial" — drena a mana dele.
 */
public final class HeavenlyRestriction {
    private static final Map<UUID, Long> WARN = new HashMap<>();

    static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpellCastEvent.Pre.class, HeavenlyRestriction::onCast);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, LivingIncomingDamageEvent.class, HeavenlyRestriction::onIncoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, SpellDamageEvent.Pre.class, HeavenlyRestriction::onSpellDamage);
        NeoForge.EVENT_BUS.addListener(MobEffectEvent.Applicable.class, HeavenlyRestriction::onApplicable);
        NeoForge.EVENT_BUS.addListener(LivingChangeTargetEvent.class, HeavenlyRestriction::onTarget);
        NeoForge.EVENT_BUS.addListener(LivingSwapItemsEvent.Hands.class, HeavenlyRestriction::onSwap);
    }

    public static int level(LivingEntity e) {
        return Enchants.level(e, EnchRegistries.HEAVENLY);
    }

    public static boolean active(LivingEntity e) {
        return e.hasEffect(EnchRegistries.HEAVENLY);
    }

    // =====================================================================================
    //  Ativar / encerrar
    // =====================================================================================
    public static int activate(SpellContext ctx, LivingEntity self, SpellStats stats) {
        int cap = Enchants.signatureCap(self);
        int cur = level(self);
        int lvl = Math.max(cur, Math.min(cap, 1 + Math.max(0, stats.amplify) + Enchants.signatureBonus(self)));
        int ticks = Enchants.duration(self, stats, 900);
        // as duas assinaturas não convivem: o corpo sem mana dispensa a Anti-Magia
        if (self.hasEffect(EnchRegistries.ANTI_MAGIC)) self.removeEffect(EnchRegistries.ANTI_MAGIC);
        Enchants.apply(self, self, EnchRegistries.HEAVENLY, lvl - cur, cap, ticks);
        if (self instanceof ServerPlayer sp) {
            MagicApi.get(sp).setMana(0);
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.heavenly_on", AntiMagic.roman(lvl))
                    .withStyle(ChatFormatting.GRAY), true);
        }
        if (self.level() instanceof ServerLevel level) {
            Vec3 feet = self.position().add(0, 0.1, 0);
            // a "explosão" de um corpo sem energia: poeira e pressão, nada de brilho
            level.sendParticles(ParticleTypes.CLOUD, self.getX(), self.getY() + 0.2, self.getZ(), 8, 1.2, 0.1, 1.2, 0.08);
            EnchFxNet.at(level, EnchFxNet.HEAVENLY, self.position(), Vec3.ZERO, 1F, EnchFx.STEEL, self.getId());
            EnchFx.sound(level, feet, SoundEvents.WARDEN_HEARTBEAT, 1.2F, 0.7F);
            EnchFx.sound(level, feet, SoundEvents.PLAYER_ATTACK_STRONG, 1.0F, 0.5F);
            EnchFx.sound(level, feet, SoundEvents.RAVAGER_ROAR, 0.5F, 1.6F);
            if (cur == 0 && !ctx.formStats().silent) {
                Announce.send(self, Component.translatable("announce.isekai_mahou.enchanter.heavenly", AntiMagic.roman(lvl)), EnchFx.STEEL,
                        Announce.STYLE_SPELL);
            }
        }
        return lvl;
    }

    /** Encerra (agachar+F ou ao ligar a Anti-Magia). */
    public static void end(LivingEntity e, boolean message) {
        if (!active(e)) return;
        e.removeEffect(EnchRegistries.HEAVENLY);
        if (e.level() instanceof ServerLevel level) {
            EnchFx.sound(level, e.position(), SoundEvents.ARMOR_EQUIP_CHAIN.value(), 0.8F, 0.6F);
            level.sendParticles(ParticleTypes.POOF, e.getX(), e.getY() + 1.0, e.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        }
        if (message && e instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.heavenly_off").withStyle(ChatFormatting.GRAY), true);
        }
    }

    /** Inverter num inimigo: drena 35% da mana atual (jogador) ou silencia e enfraquece (criatura). */
    public static void seal(SpellContext ctx, LivingEntity target, SpellStats stats) {
        if (target instanceof ServerPlayer sp) {
            MagicData d = MagicApi.get(sp);
            d.setMana(d.mana() * 0.65F);
        }
        Statuses.apply(target, Statuses.Kind.SILENCED, 1, stats.duration(40), ctx.caster(), ctx.powerMult());
        ctx.status(target, MobEffects.WEAKNESS, 100, 0, stats);
        EnchFx.ring(ctx.level(), target.position().add(0, 0.1, 0), 1.0, EnchFx.STEEL, 16, 1.0F);
        EnchFx.sound(ctx.level(), target.position(), SoundEvents.CHAIN_PLACE, 1.0F, 0.6F);
    }

    // =====================================================================================
    //  Tique (chamado pelo efeito, todo tick)
    // =====================================================================================
    public static void tick(LivingEntity e, int lvl) {
        if (!(e.level() instanceof ServerLevel level)) return;
        if (e instanceof ServerPlayer sp) {
            MagicData d = MagicApi.get(sp);
            if (d.mana() > 0F) d.setMana(0);
            if (sp.tickCount % 20 == 0) senses(sp, lvl);
        }
        if (e.tickCount % 10 == 0 && e.isSprinting()) {
            level.sendParticles(ParticleTypes.CLOUD, e.getX(), e.getY() + 0.1, e.getZ(), 2, 0.2, 0.02, 0.2, 0.01);
        }
    }

    /** Sentidos: visão noturna e inimigos marcados a até 16 + 4/nível blocos (no máx. 10; só ele vê). */
    private static void senses(ServerPlayer sp, int lvl) {
        MobEffectInstance nv = sp.getEffect(MobEffects.NIGHT_VISION);
        if (nv == null || nv.getDuration() < 260) sp.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 400, 0, false, false, false));
        double r = 16 + 4 * lvl;
        List<Mob> list = sp.serverLevel().getEntitiesOfClass(Mob.class, new AABB(sp.blockPosition()).inflate(r),
                m -> m.isAlive() && m instanceof Enemy && m.distanceToSqr(sp) <= r * r);
        int n = 0;
        for (Mob m : list) {
            if (n++ >= 10) break;
            Vec3 top = m.position().add(0, m.getBbHeight() + 0.4, 0);
            EnchFx.private_(sp, EnchFx.dust(EnchFx.RED, 1.1F), top, 3, 0.05);
        }
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    /** Não conjura enquanto ativa (nem pagando com vida). */
    private static void onCast(SpellCastEvent.Pre e) {
        LivingEntity c = e.getCaster();
        if (!active(c)) return;
        e.setCanceled(true);
        if (c instanceof ServerPlayer sp) {
            long now = sp.serverLevel().getGameTime();
            Long last = WARN.get(sp.getUUID());
            if (last == null || now - last > 40) {
                WARN.put(sp.getUUID(), now);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.heavenly_no_cast").withStyle(ChatFormatting.RED), true);
            }
        }
    }

    /** −5% de todo dano recebido por nível (o corpo aguenta). */
    private static void onIncoming(LivingIncomingDamageEvent e) {
        int lvl = level(e.getEntity());
        if (lvl <= 0) return;
        e.setAmount(e.getAmount() * (1F - 0.05F * Math.min(5, lvl)));
    }

    /** Domínios miram a mana do alvo: o Acerto Garantido (e os pulsos do domínio) não o encontram (de chefe: só 30%). */
    private static void onSpellDamage(SpellDamageEvent.Pre e) {
        LivingEntity t = e.target();
        if (!active(t)) return;
        SpellContext ctx = e.ctx();
        var form = ctx.spell().form();
        if (form == null || !form.acceptsLaws()) return;
        // o domínio de um CHEFE é o próprio ambiente desabando: o acerto garantido falha, mas sobra 30% do impacto
        if (Enchants.isBoss(ctx.caster())) {
            e.setAmount(e.getAmount() * 0.3F);
        } else {
            e.setCanceled(true);
        }
        if (t.level() instanceof ServerLevel level && t.tickCount % 10 == 0) {
            level.sendParticles(ParticleTypes.SMOKE, t.getX(), t.getY() + 1.0, t.getZ(), 6, 0.3, 0.5, 0.3, 0.01);
        }
        if (t instanceof ServerPlayer sp) {
            long now = sp.serverLevel().getGameTime();
            Long last = WARN.get(sp.getUUID());
            if (last == null || now - last > 100) {
                WARN.put(sp.getUUID(), now);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.heavenly_domain").withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    /** Invisível para detecção mágica: sem Brilho, Revelar/Exposto ou Silêncio vindos de outros. */
    private static void onApplicable(MobEffectEvent.Applicable e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide || !active(t)) return;
        Entity src = e.getEffectSource();
        if (src == t) return;
        Holder<MobEffect> eff = e.getEffectInstance().getEffect();
        if (eff.is(MobEffects.GLOWING) || eff.is(CoreRegistries.EXPOSED) || eff.is(Statuses.Kind.SILENCED.effect())) {
            e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        }
    }

    /** Criaturas mágicas (do mod, não chefes) só o percebem de perto (8 blocos) ou se ele as atacou. */
    private static void onTarget(LivingChangeTargetEvent e) {
        LivingEntity target = e.getNewAboutToBeSetTarget();
        if (target == null || !active(target)) return;
        LivingEntity mob = e.getEntity();
        if (Statuses.isBossLike(mob)) return;
        var key = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
        if (!IsekaiMahou.MODID.equals(key.getNamespace())) return;
        if (mob.getLastHurtByMob() == target) return;
        if (mob.distanceToSqr(target) > 64.0) e.setCanceled(true);
    }

    /** Agachar + F (trocar de mão) encerra a Restrição (ou, sem ela, a Anti-Magia). */
    private static void onSwap(LivingSwapItemsEvent.Hands e) {
        LivingEntity le = e.getEntity();
        if (le.level().isClientSide || !le.isShiftKeyDown()) return;
        if (active(le)) {
            end(le, true);
            e.setCanceled(true);
        } else if (le.hasEffect(EnchRegistries.ANTI_MAGIC)) {
            le.removeEffect(EnchRegistries.ANTI_MAGIC);
            if (le instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.enchanter.anti_magic_ended").withStyle(ChatFormatting.GRAY), true);
            }
            if (le.level() instanceof ServerLevel level) EnchFx.shatter(level, EnchFx.center(le), 1.0);
            e.setCanceled(true);
        }
    }

    /** Glifo de assinatura na receita? (usado pelo detalhamento de dicas) */
    static boolean known() {
        return GlyphRegistry.get("heavenly_restriction") != null;
    }

    static void clear() {
        WARN.clear();
    }

    private HeavenlyRestriction() {}
}
