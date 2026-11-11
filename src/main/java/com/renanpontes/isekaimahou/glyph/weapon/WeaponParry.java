package com.renanpontes.isekaimahou.glyph.weapon;

import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;

/**
 * Gatilho reativo {@code parry} (no núcleo, como os reativos do módulo Arcano). Os slots do loadout ativo do
 * elemento ativo cuja receita começa com Aparar são conjurados sozinhos — na hora, pagando o recurso e respeitando
 * recarga/silêncio — mirando o atacante, quando:
 * <ul>
 *   <li>o jogador BLOQUEIA um golpe com o escudo;</li>
 *   <li>o jogador é atingido CORPO A CORPO até {@link #WINDOW_TICKS} ticks depois de começar a atacar (golpe no tempo
 *       certo): esse dano ainda cai 25%.</li>
 * </ul>
 */
public final class WeaponParry {
    /** Janela do contra-golpe (0,3 s). */
    public static final int WINDOW_TICKS = 6;
    /** Redução do dano no contra-golpe no tempo certo. */
    public static final float TIMED_REDUCTION = 0.25F;

    private static final ResourceLocation ID = WeaponGlyphs.PARRY_ID;
    /** > 0 enquanto este gatilho está conjurando (para o bloqueio da tecla deixar passar). */
    private static int autoDepth;
    /** Reações já agendadas ("uuid|slot"), para não empilhar várias no mesmo tick. */
    private static final Set<String> PENDING = new HashSet<>();

    // =====================================================================================
    //  Eventos
    // =====================================================================================

    /** Apertar a tecla de um slot com Aparar não conjura (protege a técnica de ser gasta à toa). */
    public static void onPre(SpellCastEvent.Pre event) {
        if (autoDepth > 0 || !(event.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (!event.getContext().isPlayerCast()) return;
        if (event.getContext().spell().hasTrigger(WeaponGlyphs.PARRY)) {
            event.setCanceled(true);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.classes.parry_reactive_only")
                    .withStyle(ChatFormatting.GOLD), true);
        }
    }

    public static void onShieldBlock(LivingShieldBlockEvent event) {
        if (!event.getBlocked() || !(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        LivingEntity attacker = event.getDamageSource().getEntity() instanceof LivingEntity le && le != p ? le : null;
        react(p, attacker);
    }

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer || event.getAmount() <= 0) return;
        if (p.isBlocking() || !p.swinging || p.swingTime < 0 || p.swingTime >= WINDOW_TICKS) return;
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof LivingEntity attacker) || attacker == p || src.getDirectEntity() != attacker) return;
        if (src.is(DamageTypeTags.IS_PROJECTILE) || src.is(DamageTypeTags.IS_EXPLOSION) || src.is(CoreRegistries.SPELL_DAMAGE)) return;
        if (!react(p, attacker)) return;
        event.setAmount(event.getAmount() * (1F - TIMED_REDUCTION));
        ServerLevel level = p.serverLevel();
        Vec3 at = p.getEyePosition().add(attacker.getEyePosition().subtract(p.getEyePosition()).normalize().scale(0.8));
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 12, 0.2, 0.2, 0.2, 0.3);
        level.sendParticles(ParticleTypes.ENCHANTED_HIT, at.x, at.y, at.z, 8, 0.2, 0.2, 0.2, 0.2);
        WeaponFx.sound(level, at, SoundEvents.SHIELD_BLOCK, 0.9F, 1.5F);
        WeaponFx.sound(level, at, SoundEvents.ANVIL_LAND, 0.15F, 2.0F);
    }

    // =====================================================================================
    //  Conjuração automática
    // =====================================================================================

    /**
     * Agenda (próximo tick) os slots com Aparar prontos (sem recarga). Retorna true se algum foi agendado.
     */
    public static boolean react(ServerPlayer p, @Nullable LivingEntity attacker) {
        if (p.isSpectator() || !p.isAlive() || p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) {
            return false;
        }
        MagicData d = MagicApi.get(p);
        int loadout = d.activeLoadout();
        long now = p.serverLevel().getGameTime();
        boolean creative = p.getAbilities().instabuild;
        boolean any = false, broke = false;
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe recipe = d.peekSlot(loadout, slot).main();
            if (recipe == null || recipe.isEmpty() || !leadsWithParry(recipe)) continue;
            String cdKey = MagicData.slotKey(loadout, slot);
            if (now < d.cooldowns.getOrDefault(cdKey, 0L)) continue;
            // v1.0: o elemento vem dos glifos da magia do slot
            Element el = SpellCost.primaryElement(recipe);
            // Só conta como "aparou" (e reduz o dano) se a técnica vai mesmo sair: válida e com recurso
            SpellParser.Result parsed = SpellParser.parse(recipe, el, creative ? null : d);
            if (!parsed.ok() || parsed.spell().isUltimate() != (slot == Progression.ULTIMATE_SLOT)) continue;
            if (!creative && !canPay(p, d, parsed.spell())) {
                broke = true;
                continue;
            }
            String key = p.getUUID() + "|" + cdKey;
            any = true;
            // Já agendado neste tick (2 golpes ao mesmo tempo): conta como aparado, mas sai uma vez só
            if (!PENDING.add(key)) continue;
            int s = slot;
            SpellScheduler.schedule(1, () -> {
                PENDING.remove(key);
                if (!p.isRemoved() && p.isAlive()) autoCast(p, el, loadout, s, recipe, attacker);
            });
        }
        // Tinha Aparar pronto mas faltou recurso: avisa (senão o jogador não entende por que não saiu)
        if (!any && broke) p.displayClientMessage(MagicApi.noResourceMessage(p).withStyle(ChatFormatting.RED), true);
        return any;
    }

    /** Valida como o {@code SpellCaster.beginCast} e conjura na hora, virado para o atacante. */
    private static boolean autoCast(ServerPlayer p, Element el, int loadout, int slot, SpellRecipe recipe, @Nullable LivingEntity attacker) {
        if (p.isSpectator() || p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) return false;
        MagicData d = MagicApi.get(p);
        boolean creative = p.getAbilities().instabuild;
        SpellParser.Result r = SpellParser.parse(recipe, el, creative ? null : d);
        if (!r.ok()) return false;
        ParsedSpell spell = r.spell();
        if (spell.isUltimate() != (slot == Progression.ULTIMATE_SLOT)) return false;
        if (spell.isUltimate() && !creative && d.charge() < Progression.MAX_CHARGE) return false;
        el = spell.primary();
        String cdKey = MagicData.slotKey(loadout, slot);
        if (p.serverLevel().getGameTime() < d.cooldowns.getOrDefault(cdKey, 0L)) return false;
        if (!creative && !canPay(p, d, spell)) {
            p.displayClientMessage(MagicApi.noResourceMessage(p).withStyle(ChatFormatting.RED), true);
            return false;
        }

        // Mira no atacante: vira o jogador só durante a conjuração (as formas leem o olhar na hora)
        float yRot = p.getYRot(), xRot = p.getXRot(), headRot = p.getYHeadRot();
        boolean aim = attacker != null && attacker.isAlive() && attacker.level() == p.level();
        if (aim) {
            Vec3 to = attacker.position().add(0, attacker.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
            double horiz = Math.sqrt(to.x * to.x + to.z * to.z);
            float yaw = (float) (Mth.atan2(to.z, to.x) * Mth.RAD_TO_DEG) - 90.0F;
            float pitch = (float) -(Mth.atan2(to.y, horiz) * Mth.RAD_TO_DEG);
            p.setYRot(yaw);
            p.setXRot(Mth.clamp(pitch, -90F, 90F));
            p.setYHeadRot(yaw);
        }
        autoDepth++;
        boolean ok;
        try {
            ok = SpellCaster.performCast(p, d, el, recipe, spell, cdKey, false);
        } finally {
            autoDepth--;
            if (aim) {
                p.setYRot(yRot);
                p.setXRot(xRot);
                p.setYHeadRot(headRot);
            }
        }
        if (ok) {
            ElementFx.ring(p.serverLevel(), p.position(), el, 0.9, 14);
            WeaponFx.sound(p.serverLevel(), p.position(), SoundEvents.SHIELD_BLOCK, 0.7F, 1.8F);
        }
        return ok;
    }

    private static boolean canPay(ServerPlayer p, MagicData d, ParsedSpell spell) {
        if (spell.anyStats(s -> s.overload)) return p.getHealth() > SpellCaster.overloadHealthCost(spell) + 1.0F;
        return d.mana() >= spell.manaCost();
    }

    /** A receita começa com Aparar (entre os gatilhos antes da forma)? */
    public static boolean leadsWithParry(SpellRecipe recipe) {
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (!(g instanceof TriggerGlyph)) return false;
            if (ID.equals(id)) return true;
        }
        return false;
    }

    public static void clear() {
        PENDING.clear();
        autoDepth = 0;
    }

    private WeaponParry() {}
}
