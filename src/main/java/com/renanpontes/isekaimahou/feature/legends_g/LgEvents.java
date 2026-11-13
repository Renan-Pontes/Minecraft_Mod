package com.renanpontes.isekaimahou.feature.legends_g;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.legends_g.glyph.LloydTechniques;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Regras de jogo do legends_g (servidor):
 * <ul>
 *   <li><b>Núcleo de Mana</b> (aumento): o dano do glifo aumentado sai +30% por cópia (até 3); ao conjurar, paga 3% da
 *       mana máxima por cópia e o núcleo brilha no peito.</li>
 *   <li><b>Sem Encantamento</b> (aumento): o "na hora" e o silêncio já são do núcleo (Instantâneo + Silencioso); aqui só
 *       o selo que pisca na mão ao conjurar.</li>
 *   <li><b>Forma Negra</b> (efeito): dano de MAGIA recebido −40% (de chefes −25%), Silêncio/Corrupção saem, golpes corpo
 *       a corpo arrancam 1 bênção e interrompem conjurações; ao acabar, Fraqueza 6 s.</li>
 * </ul>
 */
public final class LgEvents {
    public static final int MANA_CORE_MAX = 3;
    public static final float MANA_CORE_BONUS = 0.3F;
    public static final float MANA_CORE_COST = 0.03F;

    /** Quem está (ou estava) na Forma Negra → ainda não recebeu o "corpo pesado". */
    private static final Map<UUID, LivingEntity> BLACK = new HashMap<>();

    public static void register() {
        var bus = NeoForge.EVENT_BUS;
        bus.addListener(EventPriority.LOW, SpellDamageEvent.Pre.class, LgEvents::spellDamage);
        bus.addListener(SpellCastEvent.Post.class, LgEvents::castPost);
        bus.addListener(LivingIncomingDamageEvent.class, LgEvents::meleeHit);
    }

    // =====================================================================================
    //  Aumentos (Núcleo de Mana, Sem Encantamento)
    // =====================================================================================
    /** Cópias de um aumento nos stats do efeito OU da forma (aumento depois da forma vale para a magia toda). */
    static int copies(SpellContext ctx, SpellStats st, ResourceLocation id) {
        int a = st == null ? 0 : st.custom(id);
        return Math.max(a, ctx.formStats().custom(id));
    }

    /** Cópias de um aumento na magia inteira (todos os estágios, forma e efeitos). */
    static int copiesInSpell(ParsedSpell spell, ResourceLocation id) {
        int n = 0;
        for (ParsedSpell s : spell.stages()) {
            n += s.formStats().custom(id);
            for (ParsedSpell.EffectEntry e : s.effects()) n += e.stats().custom(id);
        }
        return n;
    }

    private static void castPost(SpellCastEvent.Post e) {
        SpellContext ctx = e.getContext();
        LivingEntity c = e.getCaster();
        ServerLevel l = ctx.level();
        int core = Math.min(MANA_CORE_MAX, copiesInSpell(ctx.spell(), LloydTechniques.MANA_CORE));
        if (core > 0) {
            if (c instanceof ServerPlayer sp && !sp.getAbilities().instabuild) {
                MagicData d = MagicApi.get(sp);
                d.consumeMana(d.maxMana() * MANA_CORE_COST * core);
            }
            Vec3 chest = c.position().add(0, c.getBbHeight() * 0.62, 0).add(c.getLookAngle().multiply(1, 0, 1).normalize().scale(0.3));
            ElementFx.flash(l, chest, LloydTechniques.CORE_COLOR, 0.6F + 0.3F * core);
            l.sendParticles(new DustParticleOptions(new Vector3f(0.5F, 0.92F, 1F), 1.0F), chest.x, chest.y, chest.z,
                    4 + 3 * core, 0.15, 0.15, 0.15, 0.02);
            LgUtil.sound(l, chest, SoundEvents.RESPAWN_ANCHOR_CHARGE, 0.5F, 1.6F + 0.1F * core);
        }
        if (copiesInSpell(ctx.spell(), LloydTechniques.CHANTLESS) > 0) {
            Vec3 hand = c.getEyePosition().add(c.getLookAngle().scale(0.8)).add(0, -0.3, 0);
            ElementFx.flash(l, hand, LloydTechniques.COLOR, 0.5F);
            l.sendParticles(ParticleTypes.ENCHANT, hand.x, hand.y, hand.z, 8, 0.2, 0.2, 0.2, 0.4);
        }
    }

    private static void spellDamage(SpellDamageEvent.Pre e) {
        LivingEntity t = e.target();
        // Núcleo de Mana: sobrecarga
        int core = Math.min(MANA_CORE_MAX, copies(e.ctx(), e.stats(), LloydTechniques.MANA_CORE));
        if (core > 0) {
            e.setAmount(e.getAmount() * (1F + MANA_CORE_BONUS * core));
            if (t.level() instanceof ServerLevel sl) {
                sl.sendParticles(ParticleTypes.END_ROD, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 2 + core, 0.2, 0.3, 0.2, 0.03);
            }
        }
        // Forma Negra: a pele bebe a magia
        if (t.hasEffect(LgRegistry.BLACK_FORM)) {
            LivingEntity caster = e.ctx().caster();
            boolean boss = caster != null && Statuses.isBossLike(caster);
            e.setAmount(e.getAmount() * (boss ? 0.75F : 0.6F));
            if (t.level() instanceof ServerLevel sl && t.tickCount % 4 == 0) {
                sl.sendParticles(ParticleTypes.SQUID_INK, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 4, 0.3, 0.4, 0.3, 0.02);
            }
        }
    }

    // =====================================================================================
    //  Forma Negra
    // =====================================================================================
    /** Chamado ao entrar na Forma Negra (para o "corpo pesado" no fim). */
    public static void blackFormStarted(LivingEntity e) {
        BLACK.put(e.getUUID(), e);
    }

    private static void meleeHit(LivingIncomingDamageEvent e) {
        Entity src = e.getSource().getEntity();
        if (!(src instanceof LivingEntity attacker) || e.getSource().getDirectEntity() != attacker) return;
        if (!attacker.hasEffect(LgRegistry.BLACK_FORM) || attacker.level().isClientSide) return;
        LivingEntity t = e.getEntity();
        int n = LgUtil.stripBuffs(t, 1);
        if (t instanceof ServerPlayer sp) SpellCaster.interrupt(sp);
        if (t.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SQUID_INK, t.getX(), t.getY() + t.getBbHeight() * 0.5, t.getZ(), 6, 0.3, 0.3, 0.3, 0.05);
            if (n > 0) LgUtil.sound(sl, t.position(), SoundEvents.GLASS_BREAK, 0.6F, 1.4F);
        }
    }

    /** Todo tick do servidor: Silêncio/Corrupção saem da Forma Negra; ao acabar, o corpo pesa. */
    public static void serverTick(MinecraftServer server) {
        if (BLACK.isEmpty() || server.getTickCount() % 5 != 0) return;
        for (Iterator<Map.Entry<UUID, LivingEntity>> it = BLACK.entrySet().iterator(); it.hasNext(); ) {
            LivingEntity e = it.next().getValue();
            if (e.isRemoved() || !e.isAlive()) {
                it.remove();
                continue;
            }
            if (e.hasEffect(LgRegistry.BLACK_FORM)) {
                Statuses.clear(e, Statuses.Kind.SILENCED);
                Statuses.clear(e, Statuses.Kind.CORRUPTED);
                continue;
            }
            it.remove();
            e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 0, false, true));
            e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 60, 0, false, true));
            if (e instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_g.black_form_off")
                        .withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    public static void clear() {
        BLACK.clear();
    }

    private LgEvents() {}
}
