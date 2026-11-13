package com.renanpontes.isekaimahou.feature.glyphs_elements_b;

import static com.renanpontes.isekaimahou.feature.glyphs_elements_b.GebGlyphs.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.ElementReactions;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.api.Statuses.Kind;
import com.renanpontes.isekaimahou.core.event.ElementReactionEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.TriggerGlyph;
import com.renanpontes.isekaimahou.glyph.fx.Vfx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingFallEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Motor dos 15 gatilhos de fusão. Regra igual à dos outros gatilhos reativos (Ao Receber Dano, Ao Jackpot): quando a
 * condição acontece, cada slot do loadout ATIVO cuja receita principal começa com o gatilho sai SOZINHO — mirando o
 * alvo da condição (ou o inimigo mais próximo), pagando mana e respeitando recarga, Silêncio e Exaustão. Apertar a
 * tecla desses slots não conjura (protege a magia de emergência).
 *
 * <p>Detecção: eventos do jogo (queda, efeitos, reações, dano, drops) e, para os de "estado" (sol, mergulho, limiar
 * de vida, teleporte), uma checagem leve por jogador — só para quem tem esses gatilhos equipados (cache de 1 s).
 */
public final class GebTriggers {
    private static final double AIM_RANGE = 24;
    /** > 0 enquanto o módulo conjura sozinho (o bloqueio da tecla não vale para ele). */
    private static int autoDepth;
    /** Slots já agendados ("uuid|loadout|slot") para não empilhar o mesmo slot no mesmo tick. */
    private static final Set<String> PENDING = new HashSet<>();

    /** Cache: gatilhos deste módulo no loadout ativo de cada jogador (recalculado a cada 20 ticks). */
    private static final Map<UUID, Set<ResourceLocation>> EQUIPPED = new HashMap<>();
    private static final Map<UUID, Long> EQUIPPED_AT = new HashMap<>();
    // Estados por jogador (null = primeira amostra: só grava, não dispara)
    private static final Map<UUID, Boolean> SUNLIT = new HashMap<>();
    private static final Map<UUID, Boolean> SUBMERGED = new HashMap<>();
    private static final Map<UUID, Boolean> LOW_HALF = new HashMap<>();
    private static final Map<UUID, Boolean> WAS_BURNING = new HashMap<>();
    private static final Map<UUID, Vec3> LAST_POS = new HashMap<>();
    private static final Map<UUID, ResourceKey<Level>> LAST_DIM = new HashMap<>();

    // =====================================================================================
    //  Bloqueio da tecla
    // =====================================================================================
    static void onCastPre(SpellCastEvent.Pre e) {
        if (autoDepth > 0 || !(e.getCaster() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        if (!e.getContext().isPlayerCast()) return;
        if (leadsWithOurs(e.getContext().rootSpell())) {
            e.setCanceled(true);
            p.displayClientMessage(Component.translatable("message.isekai_mahou.arcane_reactive_only").withStyle(ChatFormatting.GOLD), true);
        }
    }

    private static boolean leadsWithOurs(ParsedSpell spell) {
        for (TriggerGlyph t : spell.triggers()) if (GebGlyphs.triggers().contains(t.id())) return true;
        return false;
    }

    // =====================================================================================
    //  Detecção (eventos)
    // =====================================================================================

    /** Ao Aterrissar (você cai 5+ blocos) e Ao Esmagar no Chão (um inimigo que você feriu cai 3+ blocos). */
    static void onFall(LivingFallEvent e) {
        LivingEntity ent = e.getEntity();
        if (ent.level().isClientSide) return;
        if (ent instanceof ServerPlayer p) {
            if (e.getDistance() >= 5.0F && !p.isInWater() && !p.isFallFlying()) react(p, MAGMA_ON_LAND, null);
            return;
        }
        if (e.getDistance() >= 3.0F && ent.getLastHurtByMob() instanceof ServerPlayer p
                && ent.tickCount - ent.getLastHurtByMobTimestamp() <= 100) {
            react(p, GRAVITY_ON_SLAM, ent);
        }
    }

    /** Ao Evaporar (Vapor/Derreter) e Ao Sobrecarregar (Eletrocutar). */
    static void onReaction(ElementReactionEvent e) {
        if (!(e.getCauser() instanceof ServerPlayer p)) return;
        ElementReactions.Reaction r = e.getReaction();
        if (r == ElementReactions.Reaction.VAPOR || r == ElementReactions.Reaction.MELT) react(p, STEAM_ON_VAPOR, e.getTarget());
        else if (r == ElementReactions.Reaction.ELECTROCUTE) react(p, LIGHTNING_ON_OVERLOAD, e.getTarget());
    }

    /** Ao Sobrecarregar: seu golpe deixou o inimigo com Eletrizado no máximo (III). */
    static void onSpellDamage(SpellDamageEvent.Post e) {
        if (!e.landed() || !(e.ctx().caster() instanceof ServerPlayer p)) return;
        LivingEntity t = e.target();
        if (!t.isAlive() || Statuses.stacks(t, Kind.SHOCKED) < Kind.SHOCKED.maxStacks()) return;
        if (GebUtil.ready("overload|" + t.getUUID(), t.level().getGameTime(), 60)) react(p, LIGHTNING_ON_OVERLOAD, t);
    }

    /** Efeitos aplicados: Congelar/Enraizar/Petrificar/Cegar alguém; você amaldiçoado; você pegando fogo. */
    static void onEffectAdded(MobEffectEvent.Added e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide) return;
        MobEffectInstance inst = e.getEffectInstance();
        MobEffectInstance old = e.getOldEffectInstance();
        Kind kind = Kind.of(inst.getEffect());
        Entity src = e.getEffectSource();

        if (t instanceof ServerPlayer self) {
            if (old == null && src != null && src != self && !inst.isInfiniteDuration() && inst.getDuration() >= 40
                    && inst.getEffect().value().getCategory() == MobEffectCategory.HARMFUL) {
                react(self, PURITY_ON_DEBUFF, src instanceof LivingEntity le && le != self ? le : null);
            }
            if (kind == Kind.BURNING && old == null && GebUtil.ready("burn|" + self.getUUID(), self.level().getGameTime(), 40)) {
                react(self, HELLFIRE_ON_BURN, null);
            }
            return;
        }
        ServerPlayer p = ownerPlayer(src);
        if (p == null || friendly(p, t)) return;
        if (kind == Kind.FROZEN && old == null) react(p, ICE_ON_FREEZE, t);
        else if (kind == Kind.ROOTED && old == null) react(p, NATURE_ON_ROOT, t);
        else if (kind == Kind.PETRIFIED && inst.getAmplifier() >= 2 && (old == null || old.getAmplifier() < 2)) react(p, CRYSTAL_ON_PETRIFY, t);
        else if (old == null && inst.is(MobEffects.BLINDNESS)) react(p, SAND_ON_BLIND, t);
    }

    /** Ao Queimar: dano de fogo em você (lava, chamas, Em Chamas). */
    static void onPlayerDamaged(LivingDamageEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || e.getNewDamage() <= 0 || !e.getSource().is(DamageTypeTags.IS_FIRE)) return;
        if (GebUtil.ready("burn|" + p.getUUID(), p.level().getGameTime(), 40)) {
            react(p, HELLFIRE_ON_BURN, e.getSource().getEntity() instanceof LivingEntity le && le != p ? le : null);
        }
    }

    /** Ao Apagar: um inimigo que você matou some sem deixar nada (roda por último e também vê drops cancelados). */
    static void onDrops(LivingDropsEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || !(e.getSource().getEntity() instanceof ServerPlayer p) || v == p) return;
        if (e.isCanceled() || e.getDrops().isEmpty() || GebAugments.wasErased(v.getUUID())) {
            Vec3 c = GebUtil.center(v);
            if (v.level() instanceof ServerLevel sl) sl.sendParticles(Vfx.reversePortal(),
                    c.x, c.y, c.z, 10, 0.2, 0.3, 0.2, 0.01);
            react(p, VOID_ON_ERASE, null);
        }
    }

    /** Teleporte por magia/efeito de outro módulo (o Passo Estelar chama). Também é detectado pelo salto de posição. */
    public static void teleported(ServerPlayer p) {
        if (GebUtil.ready("tp|" + p.getUUID(), p.level().getGameTime(), 20)) react(p, ASTRAL_ON_TELEPORT, null);
        LAST_POS.put(p.getUUID(), p.position());
        LAST_DIM.put(p.getUUID(), p.level().dimension());
    }

    /** Estados (Ao Sair ao Sol, Ao Submergir, No Limiar, Ao Evaporar na água, Ao Teleportar). */
    static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p instanceof FakePlayer) return;
        UUID id = p.getUUID();
        if (!p.isAlive() || p.isSpectator()) {
            resetStates(id);
            return;
        }
        Set<ResourceLocation> eq = equipped(p);
        if (eq.isEmpty()) {
            resetStates(id);
            return;
        }
        ServerLevel level = p.serverLevel();

        // Ao Teleportar: deslocamento > 6 blocos num tick (pérola, corifruta, piscar, comandos...)
        if (eq.contains(ASTRAL_ON_TELEPORT)) {
            Vec3 prev = LAST_POS.get(id);
            boolean sameDim = level.dimension().equals(LAST_DIM.get(id));
            if (prev != null && sameDim && p.tickCount > 20 && !p.isPassenger() && !p.isFallFlying()
                    && prev.distanceToSqr(p.position()) > 36.0) {
                if (GebUtil.ready("tp|" + id, level.getGameTime(), 20)) react(p, ASTRAL_ON_TELEPORT, null);
            }
            LAST_POS.put(id, p.position());
            LAST_DIM.put(id, level.dimension());
        } else {
            LAST_POS.remove(id);
            LAST_DIM.remove(id);
        }

        if (p.tickCount % 5 == 0) {
            if (eq.contains(ABYSS_ON_DIVE)) {
                boolean sub = p.isUnderWater();
                if (Boolean.FALSE.equals(SUBMERGED.put(id, sub)) && sub) react(p, ABYSS_ON_DIVE, null);
            } else {
                SUBMERGED.remove(id);
            }
            if (eq.contains(TWILIGHT_ON_THRESHOLD)) {
                boolean low = p.getHealth() < p.getMaxHealth() * 0.5F;
                Boolean before = LOW_HALF.put(id, low);
                if (before != null && before != low) react(p, TWILIGHT_ON_THRESHOLD, null);
            } else {
                LOW_HALF.remove(id);
            }
            if (eq.contains(STEAM_ON_VAPOR)) {
                boolean burning = p.isOnFire() || Statuses.has(p, Kind.BURNING);
                if (Boolean.TRUE.equals(WAS_BURNING.get(id)) && p.isInWater()) {
                    react(p, STEAM_ON_VAPOR, null);
                    burning = false;
                    level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.8, p.getZ(), 12, 0.3, 0.3, 0.3, 0.05);
                }
                WAS_BURNING.put(id, burning);
            } else {
                WAS_BURNING.remove(id);
            }
        }
        if (p.tickCount % 10 == 0) {
            if (eq.contains(SUN_ON_SUNLIGHT)) {
                BlockPos eye = BlockPos.containing(p.getEyePosition());
                boolean lit = level.dimensionType().hasSkyLight() && level.isDay() && level.canSeeSky(eye)
                        && !level.isRainingAt(eye);
                if (Boolean.FALSE.equals(SUNLIT.put(id, lit)) && lit) react(p, SUN_ON_SUNLIGHT, null);
            } else {
                SUNLIT.remove(id);
            }
        }
    }

    // =====================================================================================
    //  Conjuração automática
    // =====================================================================================

    /** Agenda os slots do loadout ativo cuja receita começa com {@code trigger}. */
    public static void react(ServerPlayer p, ResourceLocation trigger, @Nullable LivingEntity target) {
        if (p == null || p instanceof FakePlayer || !p.isAlive() || p.isSpectator()) return;
        MagicData d = MagicApi.get(p);
        int loadout = d.activeLoadout();
        int delay = 1;
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe recipe = d.peekSlot(loadout, slot).main();
            if (recipe == null || recipe.isEmpty() || !leadsWith(recipe, trigger)) continue;
            String key = p.getUUID() + "|" + loadout + "|" + slot;
            if (!PENDING.add(key)) continue;
            int s = slot;
            // Fora do meio do evento (dano, morte, efeito) e um slot por vez
            SpellScheduler.schedule(delay, () -> {
                PENDING.remove(key);
                if (!p.isRemoved() && p.isAlive()) autoCast(p, loadout, s, recipe, target, trigger);
            });
            delay += 4;
        }
    }

    private static void autoCast(ServerPlayer p, int loadout, int slot, SpellRecipe recipe, @Nullable LivingEntity target,
                                 ResourceLocation trigger) {
        if (p.isSpectator() || p.hasEffect(CoreRegistries.SILENCED) || p.hasEffect(CoreRegistries.MAGIC_EXHAUSTION)) return;
        MagicData d = MagicApi.get(p);
        if (d.activeLoadout() != loadout) return;
        boolean creative = p.getAbilities().instabuild;
        Element el = SpellCost.primaryElement(recipe);
        SpellParser.Result r = SpellParser.parse(recipe, el, creative ? null : d);
        if (!r.ok()) return;
        ParsedSpell spell = r.spell();
        if (spell.isUltimate() != (slot == Progression.ULTIMATE_SLOT)) return;
        if (spell.isUltimate() && !creative && d.charge() < Progression.MAX_CHARGE) return;
        String cdKey = MagicData.slotKey(loadout, slot);
        if (p.serverLevel().getGameTime() < d.cooldowns.getOrDefault(cdKey, 0L)) return;
        if (!creative && !canPay(p, d, spell)) return;

        LivingEntity aim = target != null && target.isAlive() && target.level() == p.level()
                && target.distanceToSqr(p) < 32 * 32 && target != p ? target : nearestEnemy(p);
        float yRot = p.getYRot(), xRot = p.getXRot(), headRot = p.getYHeadRot();
        if (aim != null) {
            Vec3 to = aim.position().add(0, aim.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
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
            if (aim != null) {
                p.setYRot(yRot);
                p.setXRot(xRot);
                p.setYHeadRot(headRot);
            }
        }
        if (ok) {
            // "O gatilho disparou": anel na cor da fusão do gatilho
            Glyph g = GlyphRegistry.get(trigger);
            Element te = g != null ? g.element() : el;
            ElementFx.ring(p.serverLevel(), p.position(), te, 0.9, 14);
            GebUtil.sound(p.serverLevel(), p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.5F, 1.8F);
        }
    }

    private static boolean canPay(ServerPlayer p, MagicData d, ParsedSpell spell) {
        if (spell.anyStats(s -> s.overload)) return p.getHealth() > SpellCaster.overloadHealthCost(spell) + 1.0F;
        return d.mana() >= spell.manaCost();
    }

    @Nullable
    private static LivingEntity nearestEnemy(ServerPlayer p) {
        LivingEntity best = null;
        double bestD = AIM_RANGE * AIM_RANGE;
        for (LivingEntity e : p.serverLevel().getEntitiesOfClass(LivingEntity.class, new AABB(p.blockPosition()).inflate(AIM_RANGE),
                e -> e != p && e.isAlive() && e instanceof Enemy && p.hasLineOfSight(e))) {
            double dd = e.distanceToSqr(p);
            if (dd < bestD) {
                bestD = dd;
                best = e;
            }
        }
        return best;
    }

    // =====================================================================================
    //  Auxiliares
    // =====================================================================================

    /** A receita começa com o gatilho (entre os gatilhos antes da forma)? */
    static boolean leadsWith(SpellRecipe recipe, ResourceLocation trigger) {
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (!(g instanceof TriggerGlyph)) return false;
            if (trigger.equals(id)) return true;
        }
        return false;
    }

    /** Gatilhos deste módulo no loadout ativo (cache de 20 ticks). */
    private static Set<ResourceLocation> equipped(ServerPlayer p) {
        UUID id = p.getUUID();
        long now = p.serverLevel().getGameTime();
        Long at = EQUIPPED_AT.get(id);
        Set<ResourceLocation> cached = EQUIPPED.get(id);
        if (cached != null && at != null && now >= at && now - at < 20) return cached;
        Set<ResourceLocation> out = new HashSet<>();
        MagicData d = MagicApi.get(p);
        int loadout = d.activeLoadout();
        for (int slot = 0; slot < Progression.SLOTS; slot++) {
            SpellRecipe recipe = d.peekSlot(loadout, slot).main();
            if (recipe == null) continue;
            for (ResourceLocation gid : recipe.glyphs()) {
                Glyph g = GlyphRegistry.get(gid);
                if (!(g instanceof TriggerGlyph)) break;
                if (GebGlyphs.triggers().contains(gid)) out.add(gid);
            }
        }
        EQUIPPED.put(id, out);
        EQUIPPED_AT.put(id, now);
        return out;
    }

    /** Jogador por trás da fonte de um efeito (ele mesmo, o projétil dele ou o bicho dele). */
    @Nullable
    private static ServerPlayer ownerPlayer(@Nullable Entity src) {
        if (src instanceof ServerPlayer p) return p instanceof FakePlayer ? null : p;
        if (src instanceof Projectile proj && proj.getOwner() instanceof ServerPlayer p) return p instanceof FakePlayer ? null : p;
        if (src instanceof OwnableEntity own && own.getOwner() instanceof ServerPlayer p) return p instanceof FakePlayer ? null : p;
        return null;
    }

    private static boolean friendly(ServerPlayer p, LivingEntity t) {
        if (t == p) return true;
        if (t instanceof OwnableEntity own && p.getUUID().equals(own.getOwnerUUID())) return true;
        if (p.isAlliedTo(t)) return true;
        return t instanceof Player && p.getServer() != null && !p.getServer().isPvpAllowed();
    }

    private static void resetStates(UUID id) {
        SUNLIT.remove(id);
        SUBMERGED.remove(id);
        LOW_HALF.remove(id);
        WAS_BURNING.remove(id);
        LAST_POS.remove(id);
        LAST_DIM.remove(id);
    }

    static void forget(UUID id) {
        resetStates(id);
        EQUIPPED.remove(id);
        EQUIPPED_AT.remove(id);
        PENDING.removeIf(k -> k.startsWith(id.toString()));
    }

    static void clear() {
        PENDING.clear();
        EQUIPPED.clear();
        EQUIPPED_AT.clear();
        SUNLIT.clear();
        SUBMERGED.clear();
        LOW_HALF.clear();
        WAS_BURNING.clear();
        LAST_POS.clear();
        LAST_DIM.clear();
        autoDepth = 0;
    }

    private GebTriggers() {}
}
