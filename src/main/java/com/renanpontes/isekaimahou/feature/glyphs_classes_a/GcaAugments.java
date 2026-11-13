package com.renanpontes.isekaimahou.feature.glyphs_classes_a;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
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
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellCaster;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellCost;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;
import com.renanpontes.isekaimahou.glyph.fx.Vfx;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Comportamento dos 8 aumentos de classe (servidor). Cada um marca o próprio id em {@code SpellStats.custom}; aqui os
 * eventos leem a marca:
 * <ul>
 *   <li><b>Metamagia</b> (Mago): os aumentos são repetidos no parse ({@link GcaGlyphs}); aqui a magia cobra +40% de mana.</li>
 *   <li><b>Infusão de Poção</b> (Bruxa): gasta a poção da mão secundária (ou da barra); bênçãos vão para você e aliados
 *       perto, venenos vão para cada inimigo que a magia ferir; poção persistente deixa uma nuvem no 1º alvo.</li>
 *   <li><b>Pacto de Sangue</b> (Bruxo): paga com vida (+25% de sangue); poder +1% por 1% de vida faltando (até +50%);
 *       cada abate devolve 30% do sangue (até 3 vezes).</li>
 *   <li><b>Crescer Selvagem</b> (Druida): onde a magia ferir, brota um matagal de vinhas que enraíza e fere inimigos e
 *       cura aliados.</li>
 *   <li><b>Coro dos Mortos</b> (Necromante): até 3 lacaios seus perto conjuram uma cópia (40% do poder; 15% do custo cada).</li>
 *   <li><b>Fio da Forja</b> (Ferreiro): soma 60% do dano da arma na mão; lâminas fazem sangrar, armas pesadas empurram;
 *       gasta 1 de durabilidade por golpe.</li>
 *   <li><b>Pelas Costas</b> (Ladino): ×1,6 de dano (chefes ×1,3) e sangramento se o alvo não está olhando para você;
 *       ×0,85 se ele está de frente.</li>
 *   <li><b>Desafio</b> (Cavaleiro): inimigos feridos ficam PROVOCADOS (miram só você por 6 s; −30% de dano em outros) e
 *       você ganha Absorção pelo número de provocados.</li>
 * </ul>
 */
public final class GcaAugments {
    // ---------------- Números (balanço) ----------------
    public static final float METAMAGIC_SURCHARGE = 0.4F;
    public static final float PACT_EXTRA_BLOOD = 0.25F;
    public static final float PACT_MAX_BONUS = 0.5F;
    public static final float PACT_REFUND = 0.3F;
    public static final int PACT_MAX_REFUNDS = 3;
    public static final int INFUSION_MAX_TARGETS = 8;
    public static final int CHORUS_MAX = 3;
    public static final double CHORUS_RANGE = 16.0;
    public static final float CHORUS_POWER = 0.4F;
    public static final float CHORUS_COST = 0.15F;
    public static final float EDGE_RATIO = 0.6F;
    public static final float BEHIND_MULT = 1.6F;
    public static final float BEHIND_BOSS_MULT = 1.3F;
    public static final float FACING_MULT = 0.85F;
    public static final int PROVOKE_TICKS = 120;
    public static final int PROVOKE_BOSS_TICKS = 60;
    public static final float PROVOKED_OTHER_MULT = 0.7F;
    public static final int MAX_ZONES_PER_CASTER = 3;

    /** Formas presas ao conjurador/ultimates: o Coro não copia (cada lacaio "conjuraria" em você). */
    private static final Set<String> CHORUS_DENY = Set.of("self", "aura", "stance", "seal", "portal", "turret", "touch",
            "domain_expansion", "simple_domain", "time_stop", "super_tier", "colossus", "transcend", "cataclysm", "wall");

    // ---------------- Estado ----------------
    private record Infusion(SpellRecipe recipe, List<MobEffectInstance> harmful, @Nullable PotionContents lingering, long until,
                            Set<UUID> hit) {}

    private static final class Pact {
        final SpellRecipe recipe;
        final float blood;
        final long until;
        int refunds;

        Pact(SpellRecipe recipe, float blood, long until) {
            this.recipe = recipe;
            this.blood = blood;
            this.until = until;
        }
    }

    private static final class VineZone {
        final ServerLevel level;
        final Vec3 pos;
        final float radius;
        final LivingEntity caster;
        final float power;
        long until;
        final Set<UUID> rooted = new HashSet<>();

        VineZone(ServerLevel level, Vec3 pos, float radius, LivingEntity caster, float power, long until) {
            this.level = level;
            this.pos = pos;
            this.radius = radius;
            this.caster = caster;
            this.power = power;
            this.until = until;
        }
    }

    private record Provoke(Mob mob, LivingEntity knight, long until) {}

    private static final Map<UUID, Infusion> INFUSIONS = new HashMap<>();
    private static final Map<UUID, Pact> PACTS = new HashMap<>();
    private static final List<VineZone> ZONES = new ArrayList<>();
    private static final Map<UUID, Provoke> PROVOKED = new HashMap<>();
    /** Desafio: quantos inimigos cada conjurador provocou no tick atual (para a Absorção). */
    private static final Map<UUID, long[]> PROVOKE_COUNT = new HashMap<>();
    /** Uma vez por tick por conjurador (durabilidade da arma, som). */
    private static final Map<UUID, Long> EDGE_TICK = new HashMap<>();

    // =====================================================================================
    //  Auxiliares
    // =====================================================================================

    /** Conjuração de um jogador "de verdade" (não pedestal/FakePlayer, não mob). */
    @Nullable
    private static ServerPlayer realPlayer(SpellContext ctx) {
        ServerPlayer sp = ctx.isPlayerCast() ? ctx.casterPlayer() : null;
        return sp instanceof FakePlayer ? null : sp;
    }

    /** O aumento modifica o glifo que causou este dano (o efeito) ou a forma do estágio? */
    private static boolean has(SpellDamageEvent e, ResourceLocation id) {
        return e.stats().custom(id) > 0 || e.ctx().formStats().custom(id) > 0;
    }

    private static void sound(ServerLevel level, Vec3 at, net.minecraft.sounds.SoundEvent s, float vol, float pitch) {
        level.playSound(null, at.x, at.y, at.z, s, SoundSource.PLAYERS, vol, pitch);
    }

    // =====================================================================================
    //  Conjuração
    // =====================================================================================
    public static void onCastPre(SpellCastEvent.Pre event) {
        SpellContext ctx = event.getContext();
        ParsedSpell spell = ctx.spell();
        LivingEntity caster = event.getCaster();
        ServerPlayer sp = realPlayer(ctx);

        // Metamagia: precisa ter o custo + 40% (com Sobrecarga/Pacto, em vida)
        if (sp != null && !sp.getAbilities().instabuild && GcaGlyphs.anywhere(spell, GcaGlyphs.MAGE_METAMAGIC)) {
            MagicData d = MagicApi.get(sp);
            ParsedSpell root = ctx.rootSpell();
            boolean blood = root.anyStats(s -> s.overload);
            boolean ok = blood ? sp.getHealth() > SpellCaster.overloadHealthCost(root) * (1F + METAMAGIC_SURCHARGE) + 1F
                    : d.mana() >= root.manaCost() * (1F + METAMAGIC_SURCHARGE);
            if (!ok) {
                event.setCanceled(true);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.glyphs_classes_a.metamagic_cost",
                        Math.round(root.manaCost() * (1F + METAMAGIC_SURCHARGE))).withStyle(ChatFormatting.RED), true);
                return;
            }
        }

        // Pacto de Sangue: quanto menos vida, mais poder
        if (caster != null && GcaGlyphs.anywhere(spell, GcaGlyphs.WARLOCK_PACT)) {
            float missing = 1F - caster.getHealth() / Math.max(1F, caster.getMaxHealth());
            float bonus = 1F + Math.max(0F, Math.min(PACT_MAX_BONUS, missing));
            ctx.setPowerMult(ctx.powerMult() * bonus);
        }
    }

    public static void onCastPost(SpellCastEvent.Post event) {
        SpellContext ctx = event.getContext();
        ParsedSpell spell = ctx.spell();
        LivingEntity caster = event.getCaster();
        if (caster == null || !(caster.level() instanceof ServerLevel level)) return;
        ServerPlayer sp = realPlayer(ctx);
        boolean creative = sp != null && sp.getAbilities().instabuild;
        long now = level.getGameTime();

        if (GcaGlyphs.anywhere(spell, GcaGlyphs.MAGE_METAMAGIC)) {
            if (sp != null && !creative) {
                ParsedSpell root = ctx.rootSpell();
                if (root.anyStats(s -> s.overload)) {
                    float hp = SpellCaster.overloadHealthCost(root) * METAMAGIC_SURCHARGE;
                    sp.setHealth(Math.max(1F, sp.getHealth() - hp));
                } else {
                    MagicData d = MagicApi.get(sp);
                    d.setMana(Math.max(0F, d.mana() - root.manaCost() * METAMAGIC_SURCHARGE));
                }
            }
            ElementFx.runeCircle(level, caster.position().add(0, 0.05, 0), MagicPath.MAGE.rgb(), 1.8F, 24, 3);
            level.sendParticles(Vfx.enchant(), caster.getX(), caster.getY() + 1.4, caster.getZ(), 24, 0.6, 0.5, 0.6, 0.6);
            sound(level, caster.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 0.8F, 1.5F);
        }

        if (GcaGlyphs.anywhere(spell, GcaGlyphs.WARLOCK_PACT)) {
            float blood = 0F;
            if (sp != null && !creative) {
                ParsedSpell root = ctx.rootSpell();
                float paid = SpellCaster.overloadHealthCost(root);
                float extra = paid * PACT_EXTRA_BLOOD;
                sp.setHealth(Math.max(1F, sp.getHealth() - extra));
                blood = paid + extra;
            }
            if (caster instanceof ServerPlayer) PACTS.put(caster.getUUID(), new Pact(ctx.recipe(), blood, now + 200));
            Vec3 c = caster.position().add(0, 1.0, 0);
            level.sendParticles(Vfx.crimsonSpore(), c.x, c.y, c.z, 20, 0.4, 0.6, 0.4, 0.03);
            level.sendParticles(ElementFx.glow(MagicPath.WARLOCK.rgb(), 1.2F), c.x, c.y, c.z, 10, 0.3, 0.5, 0.3, 0.02);
            sound(level, c, SoundEvents.WARDEN_HEARTBEAT, 1.0F, 0.8F);
        }

        if (sp != null && GcaGlyphs.anywhere(spell, GcaGlyphs.WITCH_INFUSION)) infuse(sp, ctx, now);

        if (sp != null && GcaGlyphs.anywhere(spell, GcaGlyphs.NECRO_CHORUS) && !spell.isUltimate()
                && !CHORUS_DENY.contains(spell.form().id().getPath()) && !spell.form().acceptsLaws()) {
            chorus(sp, ctx);
        }
    }

    // ---------------------------------------------------------------- Bruxa: Infusão de Poção
    private static void infuse(ServerPlayer sp, SpellContext ctx, long now) {
        ItemStack potion = findPotion(sp);
        ServerLevel level = sp.serverLevel();
        if (potion.isEmpty()) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.glyphs_classes_a.infusion_none")
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        PotionContents pc = potion.get(DataComponents.POTION_CONTENTS);
        if (pc == null || !pc.hasEffects()) return;
        List<MobEffectInstance> harmful = new ArrayList<>();
        List<MobEffectInstance> good = new ArrayList<>();
        for (MobEffectInstance mi : pc.getAllEffects()) {
            if (mi.getEffect().value().getCategory() == MobEffectCategory.BENEFICIAL) good.add(mi);
            else harmful.add(mi);
        }
        boolean lingering = potion.is(Items.LINGERING_POTION);
        boolean plain = potion.is(Items.POTION);
        if (!sp.getAbilities().instabuild) {
            potion.shrink(1);
            if (plain) {
                ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE);
                if (!sp.getInventory().add(bottle)) sp.drop(bottle, false);
            }
        }
        // Bênçãos: você e aliados a até 5 blocos (60% da duração)
        if (!good.isEmpty()) {
            for (LivingEntity ally : level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(5.0),
                    e -> e.isAlive() && GcaAllies.isAlly(sp, e))) {
                for (MobEffectInstance mi : good) applyPotionEffect(sp, ally, mi, 0.6F);
                level.sendParticles(Vfx.happy(), ally.getX(), ally.getY() + ally.getBbHeight(), ally.getZ(), 4, 0.3, 0.2, 0.3, 0.01);
            }
        }
        if (!harmful.isEmpty() || lingering) {
            INFUSIONS.put(sp.getUUID(), new Infusion(ctx.recipe(), harmful, lingering ? pc : null, now + 200, new HashSet<>()));
        }
        level.sendParticles(Vfx.witch(), sp.getX(), sp.getY() + 1.3, sp.getZ(), 20, 0.4, 0.4, 0.4, 0.05);
        level.sendParticles(ElementFx.glow(pc.getColor() & 0xFFFFFF, 1.0F), sp.getX(), sp.getY() + 1.3, sp.getZ(), 12, 0.3, 0.3, 0.3, 0.02);
        sound(level, sp.position(), SoundEvents.SPLASH_POTION_BREAK, 0.7F, 1.3F);
    }

    /** Poção na mão secundária; senão a primeira poção da barra. */
    private static ItemStack findPotion(ServerPlayer sp) {
        ItemStack off = sp.getOffhandItem();
        if (isPotion(off)) return off;
        for (int i = 0; i < 9; i++) {
            ItemStack s = sp.getInventory().getItem(i);
            if (isPotion(s)) return s;
        }
        return ItemStack.EMPTY;
    }

    private static boolean isPotion(ItemStack s) {
        if (s.isEmpty() || !(s.is(Items.POTION) || s.is(Items.SPLASH_POTION) || s.is(Items.LINGERING_POTION))) return false;
        PotionContents pc = s.get(DataComponents.POTION_CONTENTS);
        return pc != null && pc.hasEffects();
    }

    private static void applyPotionEffect(LivingEntity source, LivingEntity target, MobEffectInstance mi, float durMult) {
        if (mi.getEffect().value().isInstantenous()) {
            mi.getEffect().value().applyInstantenousEffect(source, source, target, mi.getAmplifier(), durMult);
        } else {
            int dur = Math.max(20, Math.round(mi.getDuration() * durMult));
            target.addEffect(new MobEffectInstance(mi.getEffect(), dur, mi.getAmplifier(), mi.isAmbient(), mi.isVisible(), true), source);
        }
    }

    // ---------------------------------------------------------------- Necromante: Coro dos Mortos
    private static void chorus(ServerPlayer sp, SpellContext ctx) {
        ServerLevel level = sp.serverLevel();
        UUID id = sp.getUUID();
        List<Mob> minions = new ArrayList<>(level.getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(CHORUS_RANGE),
                m -> m.isAlive() && GcaAllies.ownedBy(m, id)));
        if (minions.isEmpty()) return;
        minions.sort((a, b) -> Double.compare(a.distanceToSqr(sp), b.distanceToSqr(sp)));
        boolean creative = sp.getAbilities().instabuild;
        float echoCost = ctx.rootSpell().manaCost() * CHORUS_COST;
        Vec3 aimPoint = sp.getEyePosition().add(sp.getLookAngle().scale(24.0));
        SpellRecipe recipe = ctx.recipe();
        Element el = ctx.element();
        float power = ctx.powerMult() * CHORUS_POWER;
        int n = 0;
        for (Mob m : minions) {
            if (n >= CHORUS_MAX) break;
            if (!creative && !MagicApi.get(sp).consumeMana(echoCost)) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.glyphs_classes_a.chorus_no_mana")
                        .withStyle(ChatFormatting.DARK_AQUA), true);
                break;
            }
            n++;
            ElementFx.line(level, sp.position().add(0, 1.2, 0), m.position().add(0, m.getBbHeight() * 0.7, 0), Element.SHADOW);
            SpellScheduler.schedule(4 * n, () -> {
                if (!m.isAlive() || !sp.isAlive() || m.level() != sp.level()) return;
                Vec3 from = m.getEyePosition();
                LivingEntity tgt = m.getTarget();
                Vec3 to = tgt != null && tgt.isAlive() && !GcaAllies.isAlly(sp, tgt)
                        ? tgt.position().add(0, tgt.getBbHeight() * 0.5, 0) : aimPoint;
                Vec3 dir = to.subtract(from);
                if (dir.lengthSqr() < 1.0E-4) dir = sp.getLookAngle();
                SpellCaster.castFrom(sp, el, recipe, power, from, dir.normalize(), false);
                level.sendParticles(Vfx.sculkSoul(), from.x, from.y, from.z, 8, 0.2, 0.2, 0.2, 0.03);
                sound(level, from, SoundEvents.SOUL_ESCAPE.value(), 0.6F, 0.9F);
            });
        }
        if (n > 0) sound(level, sp.position(), SoundEvents.WITHER_SKELETON_AMBIENT, 0.5F, 0.6F);
    }

    // =====================================================================================
    //  Dano de magia
    // =====================================================================================
    public static void onDamagePre(SpellDamageEvent.Pre event) {
        LivingEntity caster = event.ctx().caster();
        LivingEntity target = event.target();
        if (caster == null || target == caster || !(caster.level() instanceof ServerLevel level)) return;

        // Ferreiro: Fio da Forja
        if (has(event, GcaGlyphs.SMITH_EDGE)) {
            double weapon = caster.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
                    ? caster.getAttributeValue(Attributes.ATTACK_DAMAGE) - 1.0 : 0.0;
            if (weapon > 0) {
                event.setAmount(event.getAmount() + (float) (weapon * EDGE_RATIO * SpellCost.damageScale()));
                ItemStack held = caster.getMainHandItem();
                boolean sharp = held.getItem() instanceof SwordItem || held.getItem() instanceof AxeItem;
                if (sharp) target.addEffect(new MobEffectInstance(CoreRegistries.BLEEDING, 60, 0), caster);
                else target.knockback(0.5, caster.getX() - target.getX(), caster.getZ() - target.getZ());
                long now = level.getGameTime();
                Long last = EDGE_TICK.put(caster.getUUID(), now);
                if (last == null || last != now) {
                    if (caster instanceof Player pl && !pl.getAbilities().instabuild && held.isDamageableItem()) {
                        held.hurtAndBreak(1, caster, EquipmentSlot.MAINHAND);
                    }
                    Vec3 at = target.position().add(0, target.getBbHeight() * 0.6, 0);
                    level.sendParticles(Vfx.crit(), at.x, at.y, at.z, 10, 0.25, 0.25, 0.25, 0.25);
                    level.sendParticles(Vfx.smallFlame(), at.x, at.y, at.z, 4, 0.2, 0.2, 0.2, 0.05);
                    sound(level, at, SoundEvents.ANVIL_LAND, 0.15F, 1.8F);
                }
            }
        }

        // Ladino: Pelas Costas
        if (has(event, GcaGlyphs.ROGUE_BEHIND)) {
            Vec3 toCaster = new Vec3(caster.getX() - target.getX(), 0, caster.getZ() - target.getZ());
            Vec3 look = target.getLookAngle();
            look = new Vec3(look.x, 0, look.z);
            double dot = toCaster.lengthSqr() < 1.0E-4 || look.lengthSqr() < 1.0E-4 ? 1.0 : toCaster.normalize().dot(look.normalize());
            boolean unseen = caster.isInvisible() || dot < -0.2;
            Vec3 at = target.position().add(0, target.getBbHeight() * 0.7, 0);
            if (unseen) {
                event.setAmount(event.getAmount() * (BossKit.isBossLike(target) ? BEHIND_BOSS_MULT : BEHIND_MULT));
                target.addEffect(new MobEffectInstance(CoreRegistries.BLEEDING, 40, 0), caster);
                level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 14, 0.3, 0.3, 0.3, 0.3);
                level.sendParticles(Vfx.ink(), at.x, at.y, at.z, 6, 0.2, 0.2, 0.2, 0.02);
                sound(level, at, SoundEvents.PLAYER_ATTACK_CRIT, 0.8F, 0.8F);
            } else if (dot > 0.6) {
                event.setAmount(event.getAmount() * FACING_MULT);
            }
        }
    }

    public static void onDamagePost(SpellDamageEvent.Post event) {
        if (!event.landed()) return;
        SpellContext ctx = event.ctx();
        LivingEntity caster = ctx.caster();
        LivingEntity target = event.target();
        if (caster == null || target == caster || !(caster.level() instanceof ServerLevel level)) return;
        long now = level.getGameTime();

        // Bruxa: Infusão (o veneno vai em cada inimigo ferido pela magia infundida)
        Infusion inf = INFUSIONS.get(caster.getUUID());
        if (inf != null && now <= inf.until() && inf.recipe().equals(ctx.recipe()) && inf.hit().size() < INFUSION_MAX_TARGETS
                && inf.hit().add(target.getUUID())) {
            for (MobEffectInstance mi : inf.harmful()) applyPotionEffect(caster, target, mi, 0.5F);
            if (inf.lingering() != null && inf.hit().size() == 1) {
                AreaEffectCloud cloud = new AreaEffectCloud(level, target.getX(), target.getY(), target.getZ());
                cloud.setOwner(caster);
                cloud.setRadius(2.5F);
                cloud.setDuration(100);
                cloud.setRadiusPerTick(-0.01F);
                cloud.setPotionContents(inf.lingering());
                level.addFreshEntity(cloud);
            }
            level.sendParticles(Vfx.witch(), target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(), 8, 0.3, 0.3, 0.3, 0.02);
        }

        // Bruxo: Pacto — abates devolvem sangue
        if (event.killed()) {
            Pact pact = PACTS.get(caster.getUUID());
            if (pact != null && now <= pact.until && pact.recipe.equals(ctx.recipe()) && pact.refunds < PACT_MAX_REFUNDS && pact.blood > 0) {
                pact.refunds++;
                caster.heal(pact.blood * PACT_REFUND);
                ElementFx.line(level, target.position().add(0, target.getBbHeight() * 0.5, 0), caster.position().add(0, 1.0, 0), Element.HELLFIRE);
                sound(level, caster.position(), SoundEvents.SOUL_ESCAPE.value(), 0.8F, 0.6F);
            }
        }

        // Druida: Crescer Selvagem
        if (has(event, GcaGlyphs.DRUID_OVERGROWTH)) {
            SpellStats fs = ctx.formStats();
            float radius = Math.min(6F, fs.radius(2.5F));
            int ticks = Math.min(300, fs.duration(120));
            float power = Math.min(3F, ctx.powerMult() * event.stats().power());
            grow(level, target.position(), radius, caster, power, now + ticks, ticks);
        }

        // Cavaleiro: Desafio
        if (has(event, GcaGlyphs.KNIGHT_CHALLENGE) && target instanceof Mob mob && !GcaAllies.isAlly(caster, mob)) provoke(level, caster, mob, now);
    }

    // ---------------------------------------------------------------- Druida: matagal de vinhas
    private static void grow(ServerLevel level, Vec3 at, float radius, LivingEntity caster, float power, long until, int ticks) {
        int mine = 0;
        VineZone oldest = null;
        for (VineZone z : ZONES) {
            if (z.caster != caster || z.level != level) continue;
            if (z.pos.distanceToSqr(at) < 4.0) {
                z.until = Math.max(z.until, until); // já tem mato aqui: só renova
                return;
            }
            mine++;
            if (oldest == null || z.until < oldest.until) oldest = z;
        }
        if (mine >= MAX_ZONES_PER_CASTER && oldest != null) ZONES.remove(oldest);
        if (ZONES.size() >= 64) return;
        Vec3 ground = new Vec3(at.x, Math.floor(at.y) + 0.05, at.z);
        ZONES.add(new VineZone(level, ground, radius, caster, power, until));
        ElementFx.area(level, ground, Element.NATURE, MagicPath.DRUID.rgb(), radius, 2, ticks);
        level.sendParticles(Vfx.composter(), ground.x, ground.y + 0.2, ground.z, 20, radius * 0.5, 0.1, radius * 0.5, 0.02);
        sound(level, ground, SoundEvents.ROOTED_DIRT_PLACE, 1.0F, 0.6F);
        sound(level, ground, SoundEvents.AZALEA_LEAVES_PLACE, 1.0F, 0.8F);
    }

    private static void tickZones(long now, boolean pulse) {
        Iterator<VineZone> it = ZONES.iterator();
        while (it.hasNext()) {
            VineZone z = it.next();
            if (now > z.until || !z.caster.isAlive() || z.caster.isRemoved()) {
                it.remove();
                continue;
            }
            ServerLevel level = z.level;
            double r2 = z.radius * z.radius;
            for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(z.pos, z.pos).inflate(z.radius, 2.0, z.radius),
                    e -> e.isAlive() && !e.isSpectator() && e.distanceToSqr(z.pos) <= r2 + 2.0)) {
                if (GcaAllies.isAlly(z.caster, e)) {
                    e.heal(0.25F * Progression.HEAL_SCALE * Math.min(2F, z.power));
                    continue;
                }
                boolean boss = BossKit.isBossLike(e);
                if (!boss && z.rooted.add(e.getUUID())) {
                    e.addEffect(new MobEffectInstance(CoreRegistries.ROOTED, 40, 0), z.caster);
                    level.sendParticles(Vfx.composter(), e.getX(), e.getY() + 0.2, e.getZ(), 10, 0.3, 0.2, 0.3, 0.02);
                }
                e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 15, 1), z.caster);
                e.hurt(level.damageSources().thorns(z.caster), 0.5F * Progression.DAMAGE_SCALE * z.power);
            }
            if (pulse) {
                level.sendParticles(Vfx.composter(), z.pos.x, z.pos.y + 0.2, z.pos.z, 6, z.radius * 0.5, 0.1, z.radius * 0.5, 0.0);
            }
        }
    }

    // ---------------------------------------------------------------- Cavaleiro: provocação
    private static void provoke(ServerLevel level, LivingEntity knight, Mob mob, long now) {
        boolean boss = BossKit.isBossLike(mob);
        long until = now + (boss ? PROVOKE_BOSS_TICKS : PROVOKE_TICKS);
        Provoke old = PROVOKED.put(mob.getUUID(), new Provoke(mob, knight, until));
        if (mob.getTarget() != knight) mob.setTarget(knight);
        Vec3 head = mob.position().add(0, mob.getBbHeight() + 0.3, 0);
        level.sendParticles(ParticleTypes.ANGRY_VILLAGER, head.x, head.y, head.z, 3, 0.25, 0.1, 0.25, 0.0);
        if (old != null && old.knight() == knight && old.until() > now) return; // já provocado: não conta de novo
        long[] c = PROVOKE_COUNT.computeIfAbsent(knight.getUUID(), k -> new long[2]);
        if (c[0] != now) {
            c[0] = now;
            c[1] = 0;
        }
        c[1]++;
        int amp = (int) Math.min(2, c[1] - 1);
        knight.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 120, amp, false, true, true));
        sound(level, knight.position(), SoundEvents.SHIELD_BLOCK, 0.7F, 0.7F);
        sound(level, head, SoundEvents.ANVIL_PLACE, 0.15F, 0.6F);
    }

    /** Provocado batendo em outro que não o cavaleiro: −30%. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        if (PROVOKED.isEmpty() || !(event.getSource().getEntity() instanceof Mob attacker)) return;
        Provoke p = PROVOKED.get(attacker.getUUID());
        if (p == null || p.knight() == event.getEntity()) return;
        if (attacker.level().getGameTime() > p.until()) return;
        event.setAmount(event.getAmount() * PROVOKED_OTHER_MULT);
    }

    private static void tickProvoked(long now) {
        Iterator<Provoke> it = PROVOKED.values().iterator();
        while (it.hasNext()) {
            Provoke p = it.next();
            Mob m = p.mob();
            LivingEntity k = p.knight();
            if (now > p.until() || !m.isAlive() || m.isRemoved() || !k.isAlive() || k.isRemoved() || k.level() != m.level()
                    || k.distanceToSqr(m) > 24 * 24) {
                it.remove();
                continue;
            }
            if (m.getTarget() != k) m.setTarget(k);
        }
    }

    // =====================================================================================
    //  Tick e limpeza
    // =====================================================================================
    public static void onServerTick(ServerTickEvent.Post event) {
        int tick = event.getServer().getTickCount();
        if (tick % 10 != 0) return;
        long now = event.getServer().overworld().getGameTime();
        if (!ZONES.isEmpty()) tickZones(now, tick % 20 == 0);
        if (!PROVOKED.isEmpty()) tickProvoked(now);
        if (tick % 200 == 0) {
            INFUSIONS.values().removeIf(i -> now > i.until());
            PACTS.values().removeIf(p -> now > p.until);
            PROVOKE_COUNT.values().removeIf(c -> now - c[0] > 200);
            EDGE_TICK.values().removeIf(t -> now - t > 200);
        }
    }

    public static void forget(UUID id) {
        INFUSIONS.remove(id);
        PACTS.remove(id);
        PROVOKE_COUNT.remove(id);
        EDGE_TICK.remove(id);
        ZONES.removeIf(z -> z.caster.getUUID().equals(id));
        PROVOKED.values().removeIf(p -> p.knight().getUUID().equals(id));
    }

    public static void clear() {
        INFUSIONS.clear();
        PACTS.clear();
        ZONES.clear();
        PROVOKED.clear();
        PROVOKE_COUNT.clear();
        EDGE_TICK.clear();
    }

    private GcaAugments() {}
}
