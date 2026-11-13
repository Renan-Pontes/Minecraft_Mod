package com.renanpontes.isekaimahou.feature.glyphs_classes_a;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
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
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;

/**
 * Conjuração automática dos 8 gatilhos de classe (mesmo modelo do Aparar e do Ao Jackpot): quando a condição acontece,
 * cada slot do loadout ativo cuja receita COMEÇA com o gatilho sai sozinho no próximo tick — pagando o recurso,
 * respeitando recarga, Silêncio e o slot G — virado para um alvo (quem causou, ou o inimigo mais próximo). Apertar a
 * tecla de um slot com um desses gatilhos não conjura (protege a magia reativa de ser gasta à toa).
 * <p>
 * Cada gatilho tem um intervalo interno por jogador ({@link #icd}) para não disparar em rajada.
 */
public final class GcaTriggerEngine {
    /** Alcance da mira automática quando ninguém causou o gatilho. */
    public static final double AIM_RANGE = 16.0;

    private static final Set<ResourceLocation> TRIGGERS = Set.of(GcaGlyphs.MAGE_WEAVE, GcaGlyphs.WITCH_DRINK,
            GcaGlyphs.WARLOCK_CURSED, GcaGlyphs.DRUID_ROOTED, GcaGlyphs.NECRO_MINION, GcaGlyphs.SMITH_HAMMER,
            GcaGlyphs.ROGUE_STEAL, GcaGlyphs.KNIGHT_GUARD);

    /** > 0 enquanto este módulo está conjurando sozinho (o bloqueio da tecla deixa passar). */
    private static int autoDepth;
    /** Próximo tick em que o gatilho pode disparar de novo ("uuid|gatilho"). */
    private static final Map<String, Long> NEXT = new HashMap<>();
    /** Slots já agendados ("uuid|slot"), para não sair duas vezes no mesmo tick. */
    private static final Set<String> PENDING = new HashSet<>();

    /** Conjurando sozinho agora? (outros detectores ignoram as próprias conjurações automáticas) */
    public static boolean autoCasting() {
        return autoDepth > 0;
    }

    /** Intervalo interno de cada gatilho (ticks). */
    private static int icd(ResourceLocation trigger) {
        if (trigger.equals(GcaGlyphs.MAGE_WEAVE)) return 100;
        if (trigger.equals(GcaGlyphs.WARLOCK_CURSED)) return 80;
        if (trigger.equals(GcaGlyphs.ROGUE_STEAL)) return 60;
        if (trigger.equals(GcaGlyphs.KNIGHT_GUARD)) return 60;
        if (trigger.equals(GcaGlyphs.SMITH_HAMMER)) return 30;
        if (trigger.equals(GcaGlyphs.DRUID_ROOTED)) return 40;
        return 20; // Ao Beber Poção, Ao Morrer um Lacaio
    }

    // =====================================================================================
    //  Bloqueio da tecla
    // =====================================================================================
    public static void onPre(SpellCastEvent.Pre event) {
        if (autoDepth > 0 || !(event.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (!event.getContext().isPlayerCast()) return;
        for (TriggerGlyph t : event.getContext().spell().triggers()) {
            if (TRIGGERS.contains(t.id())) {
                event.setCanceled(true);
                p.displayClientMessage(Component.translatable("message.isekai_mahou.glyphs_classes_a.reactive_only",
                        t.displayName()).withStyle(ChatFormatting.GOLD), true);
                return;
            }
        }
    }

    // =====================================================================================
    //  Consulta
    // =====================================================================================

    /** A receita começa com o gatilho (entre os gatilhos antes da forma)? */
    public static boolean leadsWith(SpellRecipe recipe, ResourceLocation trigger) {
        if (recipe == null) return false;
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (!(g instanceof TriggerGlyph)) return false;
            if (trigger.equals(id)) return true;
        }
        return false;
    }

    /** O loadout ativo do jogador tem algum slot que começa com o gatilho? (barato: 4 receitas) */
    public static boolean hasTrigger(ServerPlayer p, ResourceLocation trigger) {
        MagicData d = MagicApi.get(p);
        int loadout = d.activeLoadout();
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            if (leadsWith(d.peekSlot(loadout, slot).main(), trigger)) return true;
        }
        return false;
    }

    // =====================================================================================
    //  Disparo
    // =====================================================================================

    /**
     * A condição do gatilho aconteceu: agenda (próximo tick) os slots prontos que começam com ele.
     * @param aim quem deve virar o alvo (null = o inimigo mais próximo à vista, se houver)
     * @return true se algum slot vai sair (e o intervalo interno começou)
     */
    public static boolean react(ServerPlayer p, ResourceLocation trigger, @Nullable LivingEntity aim) {
        if (p == null || p instanceof FakePlayer || p.isSpectator() || !p.isAlive()) return false;
        if (p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) return false;
        long now = p.serverLevel().getGameTime();
        String icdKey = p.getUUID() + "|" + trigger;
        if (now < NEXT.getOrDefault(icdKey, 0L)) return false;

        MagicData d = MagicApi.get(p);
        int loadout = d.activeLoadout();
        boolean creative = p.getAbilities().instabuild;
        boolean any = false, broke = false;
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe recipe = d.peekSlot(loadout, slot).main();
            if (recipe == null || recipe.isEmpty() || !leadsWith(recipe, trigger)) continue;
            String cdKey = MagicData.slotKey(loadout, slot);
            if (now < d.cooldowns.getOrDefault(cdKey, 0L)) continue;
            Element el = SpellCost.primaryElement(recipe);
            SpellParser.Result parsed = SpellParser.parse(recipe, el, creative ? null : d);
            if (!parsed.ok() || parsed.spell().isUltimate() != (slot == Progression.ULTIMATE_SLOT)) continue;
            if (parsed.spell().isUltimate() && !creative && d.charge() < Progression.MAX_CHARGE) continue;
            if (!creative && !canPay(p, d, parsed.spell())) {
                broke = true;
                continue;
            }
            String key = p.getUUID() + "|" + cdKey;
            if (!PENDING.add(key)) continue;
            any = true;
            int s = slot;
            SpellScheduler.schedule(1, () -> {
                PENDING.remove(key);
                if (!p.isRemoved() && p.isAlive()) autoCast(p, loadout, s, recipe, trigger, aim);
            });
        }
        if (any) NEXT.put(icdKey, now + icd(trigger));
        else if (broke) p.displayClientMessage(MagicApi.noResourceMessage(p).withStyle(ChatFormatting.RED), true);
        return any;
    }

    /** Valida como o {@code SpellCaster.beginCast} e conjura na hora, virado para o alvo. */
    private static void autoCast(ServerPlayer p, int loadout, int slot, SpellRecipe recipe, ResourceLocation trigger,
                                 @Nullable LivingEntity aim) {
        if (p.isSpectator() || p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) return;
        MagicData d = MagicApi.get(p);
        if (d.activeLoadout() != loadout) return;
        boolean creative = p.getAbilities().instabuild;
        SpellParser.Result r = SpellParser.parse(recipe, SpellCost.primaryElement(recipe), creative ? null : d);
        if (!r.ok()) return;
        ParsedSpell spell = r.spell();
        if (spell.isUltimate() != (slot == Progression.ULTIMATE_SLOT)) return;
        if (spell.isUltimate() && !creative && d.charge() < Progression.MAX_CHARGE) return;
        String cdKey = MagicData.slotKey(loadout, slot);
        if (p.serverLevel().getGameTime() < d.cooldowns.getOrDefault(cdKey, 0L)) return;
        if (!creative && !canPay(p, d, spell)) return;

        LivingEntity target = aim != null && aim.isAlive() && aim.level() == p.level() && aim != p ? aim : nearestEnemy(p, AIM_RANGE);
        float yRot = p.getYRot(), xRot = p.getXRot(), headRot = p.getYHeadRot();
        if (target != null) {
            Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
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
            ok = SpellCaster.performCast(p, d, spell.primary(), recipe, spell, cdKey, false);
        } finally {
            autoDepth--;
            if (target != null) {
                p.setYRot(yRot);
                p.setXRot(xRot);
                p.setYHeadRot(headRot);
            }
        }
        if (ok) firedFx(p, trigger);
    }

    private static boolean canPay(ServerPlayer p, MagicData d, ParsedSpell spell) {
        if (spell.anyStats(s -> s.overload)) return p.getHealth() > SpellCaster.overloadHealthCost(spell) + 1.0F;
        return d.mana() >= spell.manaCost();
    }

    /** "O gatilho disparou": círculo pequeno na cor da classe dona do gatilho + um som da classe. */
    private static void firedFx(ServerPlayer p, ResourceLocation trigger) {
        ServerLevel level = p.serverLevel();
        Glyph g = GlyphRegistry.get(trigger);
        MagicPath path = g != null && g.origin() != null ? g.origin() : MagicPath.MAGE;
        ElementFx.runeCircle(level, p.position().add(0, 0.05, 0), path.rgb(), 1.1F, 16, 1);
        level.sendParticles(ElementFx.glow(path.rgb(), 0.9F), p.getX(), p.getY() + 1.0, p.getZ(), 10, 0.35, 0.5, 0.35, 0.02);
        SoundEvent sound = switch (path) {
            case WITCH -> SoundEvents.WITCH_CELEBRATE;
            case WARLOCK -> SoundEvents.WITHER_AMBIENT;
            case DRUID -> SoundEvents.AZALEA_LEAVES_PLACE;
            case NECROMANCER -> SoundEvents.SOUL_ESCAPE.value();
            case SMITH -> SoundEvents.ANVIL_USE;
            case ROGUE -> SoundEvents.PLAYER_ATTACK_SWEEP;
            case KNIGHT -> SoundEvents.SHIELD_BLOCK;
            default -> SoundEvents.AMETHYST_BLOCK_CHIME;
        };
        float vol = path == MagicPath.WARLOCK ? 0.25F : path == MagicPath.SMITH ? 0.35F : 0.7F;
        level.playSound(null, p.getX(), p.getY(), p.getZ(), sound, SoundSource.PLAYERS, vol, 1.3F);
    }

    // =====================================================================================
    //  Auxiliares
    // =====================================================================================

    /** Inimigo vivo mais próximo à vista (monstros, ou quem está mirando o jogador). */
    @Nullable
    public static LivingEntity nearestEnemy(ServerPlayer p, double range) {
        return nearestEnemyAround(p, p.position(), range);
    }

    /** Inimigo de {@code p} mais próximo de {@code center} (com linha de visão do jogador). */
    @Nullable
    public static LivingEntity nearestEnemyAround(ServerPlayer p, Vec3 center, double range) {
        LivingEntity best = null;
        double bestD = range * range;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(range),
                e -> e != p && e.isAlive() && !e.isSpectator() && isHostileTo(p, e))) {
            double dd = e.distanceToSqr(center);
            if (dd < bestD && p.hasLineOfSight(e)) {
                bestD = dd;
                best = e;
            }
        }
        return best;
    }

    /** Monstro, ou criatura que está mirando o jogador (e que não é aliada dele). */
    public static boolean isHostileTo(ServerPlayer p, LivingEntity e) {
        if (GcaAllies.isAlly(p, e)) return false;
        return e instanceof Enemy || (e instanceof Mob m && m.getTarget() == p);
    }

    public static void forget(UUID player) {
        String prefix = player + "|";
        NEXT.keySet().removeIf(k -> k.startsWith(prefix));
        PENDING.removeIf(k -> k.startsWith(prefix));
    }

    public static void clear() {
        NEXT.clear();
        PENDING.clear();
        autoDepth = 0;
    }

    private GcaTriggerEngine() {}
}
