package com.renanpontes.isekaimahou.feature.legends_a;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.legends_a.entity.ExplosionSigilEntity;
import com.renanpontes.isekaimahou.feature.legends_a.glyph.AllInAugment;
import com.renanpontes.isekaimahou.feature.legends_a.glyph.ColossalAugment;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Regras de conjuração das Magias Lendárias A (servidor):
 * <ul>
 *   <li><b>Colossal</b>: custo ×2,5 (checado antes e cobrado depois) e poder ×1,6;</li>
 *   <li><b>Tudo ou Nada</b>: poder ×(1 + mana extra/custo, máx. ×4), gasta todo o mana e o conjurador desmaia;</li>
 *   <li><b>Explosão Colossal</b>: círculos mágicos empilhados no céu durante a conjuração.</li>
 * </ul>
 */
public final class LegendCasting {
    public static final float COLOSSAL_COST = 2.5F;
    public static final float COLOSSAL_POWER = 1.6F;
    public static final float ALL_IN_MAX = 4.0F;
    /** Mobs não têm mana: Tudo ou Nada vale ×1,5 para eles (e eles também desmaiam). */
    public static final float ALL_IN_MOB = 1.5F;
    public static final int COLLAPSE_TICKS = 120;

    private static final ResourceLocation CRATER = com.renanpontes.isekaimahou.IsekaiMahou.id("crater");
    /** Altura dos círculos acima do ponto mirado. */
    private static final double SIGIL_HEIGHT = 11.0;

    private static final Map<UUID, ExplosionSigilEntity> SIGILS = new HashMap<>();
    /** Início da conjuração já checada para o custo do Colossal (um aviso por conjuração). */
    private static final Map<UUID, Long> CHECKED_CHANNEL = new HashMap<>();
    /** Multiplicador do Tudo ou Nada calculado no Pre, para a mensagem do Post. */
    private static final Map<SpellContext, Float> ALL_IN_MULT = new java.util.WeakHashMap<>();

    public static void clear() {
        SIGILS.clear();
        CHECKED_CHANNEL.clear();
        ALL_IN_MULT.clear();
    }

    private static boolean has(ParsedSpell spell, ResourceLocation id) {
        return spell.anyStats(s -> s.custom(id) > 0);
    }

    private static boolean overload(ParsedSpell spell) {
        return spell.anyStats(s -> s.overload);
    }

    /** Conjuração de um jogador "de verdade" (não pedestal/FakePlayer, não mob). */
    private static ServerPlayer realPlayer(SpellContext ctx) {
        ServerPlayer sp = ctx.isPlayerCast() ? ctx.casterPlayer() : null;
        return sp instanceof FakePlayer ? null : sp;
    }

    // =====================================================================================
    //  SpellCastEvent
    // =====================================================================================
    public static void onPre(SpellCastEvent.Pre event) {
        SpellContext ctx = event.getContext();
        ParsedSpell spell = ctx.spell();
        boolean colossal = has(spell, ColossalAugment.ID);
        boolean allIn = has(spell, AllInAugment.ID);
        if (!colossal && !allIn) return;

        float mult = colossal ? COLOSSAL_POWER : 1.0F;
        ServerPlayer sp = realPlayer(ctx);
        if (sp != null) {
            MagicData d = MagicApi.get(sp);
            boolean creative = sp.getAbilities().instabuild;
            float cost = spell.manaCost() * (colossal ? COLOSSAL_COST : 1.0F);
            if (colossal && !creative && !canPayColossal(sp, d, spell)) {
                event.setCanceled(true);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_a.colossal_no_mana", Math.round(cost))
                        .withStyle(ChatFormatting.RED), true);
                return;
            }
            if (allIn) {
                float extra = Math.max(0.0F, d.mana() - cost);
                float m = Math.min(ALL_IN_MAX, 1.0F + extra / Math.max(1.0F, cost));
                mult *= m;
                ALL_IN_MULT.put(ctx, m);
            }
        } else if (allIn) {
            mult *= ALL_IN_MOB;
        }
        ctx.setPowerMult(ctx.powerMult() * mult);
    }

    public static void onPost(SpellCastEvent.Post event) {
        SpellContext ctx = event.getContext();
        ParsedSpell spell = ctx.spell();
        LivingEntity caster = event.getCaster();
        ServerPlayer sp = realPlayer(ctx);

        if (sp != null && ctx.recipe().glyphs().contains(CRATER)) releaseSigil(sp);

        boolean colossal = has(spell, ColossalAugment.ID);
        boolean allIn = has(spell, AllInAugment.ID);
        if (!colossal && !allIn) return;

        if (sp != null && !sp.getAbilities().instabuild) {
            MagicData d = MagicApi.get(sp);
            if (colossal) {
                // O núcleo já cobrou 1× o custo: o Colossal cobra os outros 1,5×
                float extra = spell.manaCost() * (COLOSSAL_COST - 1.0F);
                if (overload(spell)) sp.setHealth(Math.max(1.0F, sp.getHealth() - extra / 5.0F));
                else d.setMana(d.mana() - extra);
            }
        }
        if (allIn) {
            float m = ALL_IN_MULT.getOrDefault(ctx, ALL_IN_MOB);
            ALL_IN_MULT.remove(ctx);
            int drained = 0;
            if (sp != null) {
                MagicData d = MagicApi.get(sp);
                drained = Math.round(d.mana());
                if (!sp.getAbilities().instabuild) d.setMana(0);
            }
            collapse(caster, sp, m, drained);
        }
    }

    /** Dá para pagar 2,5× o custo (com mana; ou com vida se a magia usa Sobrecarga)? */
    private static boolean canPayColossal(ServerPlayer sp, MagicData d, ParsedSpell spell) {
        float cost = spell.manaCost() * COLOSSAL_COST;
        if (overload(spell)) return sp.getHealth() > cost / 5.0F + 1.0F;
        return d.mana() >= cost;
    }

    /** O conjurador desmaia: Exaustão Mágica + lentidão + fraqueza, com partículas e som de colapso. */
    private static void collapse(LivingEntity caster, ServerPlayer sp, float mult, int drained) {
        if (!(caster.level() instanceof ServerLevel sl)) return;
        caster.addEffect(new MobEffectInstance(CoreRegistries.MAGIC_EXHAUSTION, COLLAPSE_TICKS, 0, false, true, true));
        caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, COLLAPSE_TICKS, 3, false, true, true));
        caster.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, COLLAPSE_TICKS, 1, false, true, true));
        Vec3 p = caster.position();
        sl.sendParticles(Vfx.soul(), p.x, p.y + 1.0, p.z, 18, 0.4, 0.6, 0.4, 0.02);
        sl.sendParticles(Vfx.largeSmoke(), p.x, p.y + 0.2, p.z, 14, 0.5, 0.1, 0.5, 0.01);
        sl.sendParticles(LegendFx.dust(Element.ARCANE.color, 1.5F), p.x, p.y + 1.2, p.z, 30, 0.5, 0.7, 0.5, 0);
        LegendFx.sound(sl, p, SoundEvents.BEACON_DEACTIVATE, 1.2F, 0.6F);
        LegendFx.sound(sl, p, SoundEvents.SOUL_ESCAPE, 1.0F, 0.7F);
        // Fumacinha de "energia saindo" enquanto está caído
        for (int t = 10; t <= COLLAPSE_TICKS; t += 15) {
            SpellScheduler.schedule(t, () -> {
                if (!caster.isAlive() || caster.level() != sl) return;
                Vec3 q = caster.position();
                sl.sendParticles(Vfx.ash(), q.x, q.y + 1.0, q.z, 10, 0.4, 0.5, 0.4, 0.01);
                sl.sendParticles(Vfx.smoke(), q.x, q.y + 0.4, q.z, 4, 0.3, 0.1, 0.3, 0.01);
            });
        }
        if (sp != null) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_a.all_in_collapse",
                    String.format(java.util.Locale.ROOT, "%.1f", mult), drained).withStyle(ChatFormatting.LIGHT_PURPLE), true);
        }
    }

    // =====================================================================================
    //  Tick do jogador: aviso de custo do Colossal e círculos da Explosão Colossal
    // =====================================================================================
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer) return;
        MagicData d = MagicApi.get(sp);
        SpellRecipe recipe = d.channelSlot >= 0 ? d.channelRecipe : null;
        UUID id = sp.getUUID();
        if (recipe == null) {
            CHECKED_CHANNEL.remove(id);
            SIGILS.remove(id);
            return;
        }
        if (recipe.glyphs().contains(ColossalAugment.ID) && !sp.getAbilities().instabuild
                && !Long.valueOf(d.channelStart).equals(CHECKED_CHANNEL.get(id))) {
            CHECKED_CHANNEL.put(id, d.channelStart);
            checkColossalAtStart(sp, d, recipe);
            if (d.channelSlot < 0) return;
        }
        if (recipe.glyphs().contains(CRATER)) tickSigil(sp, d, recipe);
    }

    /** Avisa já no começo da conjuração (em vez de no fim) que não há mana para os 2,5×. */
    private static void checkColossalAtStart(ServerPlayer sp, MagicData d, SpellRecipe recipe) {
        SpellParser.Result r = SpellParser.parse(recipe, d.channelElement, d);
        if (!r.ok() || !has(r.spell(), ColossalAugment.ID) || canPayColossal(sp, d, r.spell())) return;
        SpellCaster.interrupt(sp);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_a.colossal_no_mana",
                Math.round(r.spell().manaCost() * COLOSSAL_COST)).withStyle(ChatFormatting.RED), true);
    }

    private static void tickSigil(ServerPlayer sp, MagicData d, SpellRecipe recipe) {
        ServerLevel sl = sp.serverLevel();
        Vec3 target = aimPoint(sp, 48.0);
        Vec3 at = target.add(0, SIGIL_HEIGHT, 0);
        ExplosionSigilEntity sigil = SIGILS.get(sp.getUUID());
        if (sigil == null || sigil.isRemoved() || sigil.level() != sl || sigil.state() != ExplosionSigilEntity.CHARGING) {
            int color = recipe.color() >= 0 ? recipe.color() & 0xFFFFFF : LegendFx.CRIMSON;
            int layers = Math.min(6, 3 + d.channelTicks / 25);
            sigil = ExplosionSigilEntity.charging(sl, at, color, 6.5F, layers, d.channelTicks);
            sl.addFreshEntity(sigil);
            SIGILS.put(sp.getUUID(), sigil);
        }
        sigil.keepAlive(sl, at);
    }

    private static void releaseSigil(ServerPlayer sp) {
        ExplosionSigilEntity sigil = SIGILS.remove(sp.getUUID());
        if (sigil == null || sigil.isRemoved()) return;
        Vec3 below = LegendFx.groundBelow(sp.serverLevel(), sigil.position(), 64);
        sigil.release(below != null ? (float) (sigil.getY() - below.y) : (float) SIGIL_HEIGHT);
    }

    /**
     * Ponto mirado pelo jogador (criatura ou chão) até {@code range}, com a mesma regra das formas de área (Queda):
     * mirando o céu ou uma parede, o ponto desce até o chão — os círculos ficam em cima de onde a magia vai cair.
     */
    private static Vec3 aimPoint(ServerPlayer sp, double range) {
        ServerLevel sl = sp.serverLevel();
        Vec3 eye = sp.getEyePosition();
        Vec3 end = eye.add(sp.getLookAngle().scale(range));
        BlockHitResult block = sl.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, sp));
        boolean miss = block.getType() == HitResult.Type.MISS;
        Vec3 stop = miss ? end : block.getLocation();
        EntityHitResult ent = ProjectileUtil.getEntityHitResult(sl, sp, eye, stop, new AABB(eye, stop).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator());
        Entity hit = ent != null ? ent.getEntity() : null;
        if (hit != null) return hit.position();
        if (miss) return orSelf(LegendFx.groundBelow(sl, end, 64), end);
        if (block.getDirection() == Direction.UP) return stop;
        Vec3 out = stop.add(Vec3.atLowerCornerOf(block.getDirection().getNormal()).scale(0.3));
        return orSelf(LegendFx.groundBelow(sl, out, 24), out);
    }

    private static Vec3 orSelf(@Nullable Vec3 v, Vec3 fallback) {
        return v != null ? v : fallback;
    }

    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.getEntity().getUUID();
        SIGILS.remove(id);
        CHECKED_CHANNEL.remove(id);
    }

    private LegendCasting() {}
}
