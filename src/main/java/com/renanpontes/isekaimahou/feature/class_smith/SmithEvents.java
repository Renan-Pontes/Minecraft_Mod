package com.renanpontes.isekaimahou.feature.class_smith;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeFamily;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeStats;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgedBlade;
import com.renanpontes.isekaimahou.feature.class_smith.glyph.SmithGlyphs;
import com.renanpontes.isekaimahou.feature.class_smith.item.ForgedBladeItem;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithHammerItem;
import com.renanpontes.isekaimahou.feature.class_smith.item.SmithOutfitItem;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Eventos do Ferreiro (servidor):
 * <ul>
 *   <li>Golpes da Lâmina Forjada: ponta perfura armadura e acerta em cheio, serrilha faz sangrar, runa queima com o
 *   elemento, acabamento fosco/brilhante, família (martelo atordoa, foice drena, machado parte armadura);</li>
 *   <li>BÔNUS DE CLASSE: armas forjadas e martelos batem +10% (+3% por rank); equipamento vestido e nas mãos se repara
 *   sozinho; a pele de forja corta 15% do dano de fogo (mais a roupa da classe);</li>
 *   <li>contadores das Provas (abates com lâmina, abates de elite, minérios, técnicas conjuradas);</li>
 *   <li>magias prontas ao escolher a classe e a entrega da roupa de grau nos Círculos 3/5/8.</li>
 * </ul>
 */
public final class SmithEvents {
    /** Cadência do reparo passivo (ticks). */
    public static final int REPAIR_INTERVAL = 100;
    /** Vida máxima (já com a escala dos mobs) para um abate contar como "de elite". */
    public static final float ELITE_HEALTH = 80F;
    /** Fogo: o Ferreiro sente 15% menos (pele de forja). */
    public static final float SMITH_FIRE_WARD = 0.15F;

    public static void init() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, SmithEvents::onPathChosen);
        NeoForge.EVENT_BUS.addListener(SmithEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(SmithEvents::onCircle);
        NeoForge.EVENT_BUS.addListener(SmithEvents::onIncoming);
        NeoForge.EVENT_BUS.addListener(SmithEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(SmithEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(SmithEvents::onCast);
        NeoForge.EVENT_BUS.addListener(SmithEvents::onPlayerTick);
        SmithTechniques.init();
    }

    private static boolean isSmith(Player p) {
        return p instanceof ServerPlayer sp && MagicApi.get(sp).path() == MagicPath.SMITH;
    }

    // =====================================================================================
    //  Classe escolhida / login / Círculo
    // =====================================================================================
    private static void onPathChosen(MagicProgressEvent.PathChosen e) {
        SmithStarter.giveStarter(e.getPlayer());
        SmithOutfits.grantPending(e.getPlayer());
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            SmithStarter.giveStarter(sp);
            SmithTechniques.catchUp(sp);
        }
    }

    private static void onCircle(MagicProgressEvent.CircleChanged e) {
        if (e.newCircle > e.oldCircle) SmithOutfits.grantPending(e.getPlayer());
    }

    // =====================================================================================
    //  Golpes
    // =====================================================================================
    private static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity target = e.getEntity();
        if (target.level().isClientSide) return;
        DamageSource src = e.getSource();
        // Roupa do Ferreiro + pele de forja contra fogo/lava
        if (src.is(DamageTypeTags.IS_FIRE)) {
            float ward = SmithOutfitItem.fireWard(target);
            if (target instanceof Player p && isSmith(p)) ward += SMITH_FIRE_WARD;
            if (ward > 0) e.setAmount(e.getAmount() * (1F - Math.min(0.75F, ward)));
        }
        if (!(src.getEntity() instanceof Player attacker) || src.getDirectEntity() != attacker || attacker == target) return;
        if (!src.is(net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK)) return;
        ItemStack weapon = attacker.getMainHandItem();
        float amount = e.getAmount();
        boolean smithWeapon = false;
        if (ForgedBladeItem.is(weapon)) {
            amount = bladeHit(attacker, target, ForgedBladeItem.get(weapon), amount);
            smithWeapon = true;
        } else if (weapon.getItem() instanceof SmithHammerItem) {
            smithWeapon = true;
        }
        if (smithWeapon && isSmith(attacker)) {
            amount *= 1.10F + 0.03F * SmithProgress.rank(MagicApi.get(attacker));
        }
        e.setAmount(amount);
    }

    /** Efeitos das partes da lâmina num golpe corpo a corpo. Devolve o dano novo. */
    private static float bladeHit(Player attacker, LivingEntity target, ForgedBlade b, float amount) {
        ServerLevel sl = (ServerLevel) target.level();
        Vec3 c = SmithFx.center(target);
        // Ponta: perfura armadura (proporcional à armadura do alvo) e chance de golpe certeiro
        if (b.tip() > 0) {
            float armor = Math.min(1F, target.getArmorValue() / 10F);
            amount *= 1F + BladeStats.PIERCE_PER_TIP * b.tip() * armor;
            if (sl.random.nextFloat() < BladeStats.CRIT_PER_TIP * b.tip()) {
                amount *= 1.5F;
                sl.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.3, 0.3, 0.3, 0.35);
                SmithFx.sound(sl, c, net.minecraft.sounds.SoundEvents.PLAYER_ATTACK_CRIT, 0.8F, 1.2F);
            }
        }
        // Acabamento
        switch (b.finish()) {
            case MATTE -> {
                Vec3 facing = target.getLookAngle();
                Vec3 toAttacker = attacker.position().subtract(target.position()).normalize();
                if (facing.dot(toAttacker) < -0.3) {
                    amount *= 1.25F;
                    sl.sendParticles(ParticleTypes.SMOKE, c.x, c.y, c.z, 6, 0.2, 0.2, 0.2, 0.01);
                }
            }
            case BRIGHT -> {
                if (target.getType().is(EntityTypeTags.UNDEAD)) {
                    amount *= 1.2F;
                    sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 5, 0.2, 0.3, 0.2, 0.02);
                }
            }
            case RUNIC -> { }
        }
        // Família
        if (b.family() == BladeFamily.HAMMER && sl.random.nextFloat() < 0.15F) {
            target.addEffect(new MobEffectInstance(CoreRegistries.STUNNED, 24, 0), attacker);
        } else if (b.family() == BladeFamily.AXE && target.getArmorValue() >= 10) {
            amount *= 1.15F;
        } else if (b.family() == BladeFamily.SCYTHE) {
            attacker.heal(amount * 0.08F);
        }
        // Serrilha: sangramento que acumula
        if (b.serration() > 0) {
            Statuses.applyUpTo(target, Statuses.Kind.BLEEDING, b.serration(), 80, attacker, 1F, 3 * b.serration());
        }
        // Runa: dano extra do elemento + o "toque" de cada elemento
        Element rune = b.runeElement();
        if (rune != null) {
            float extra = (1.5F + 0.5F * b.quality()) * (b.finish() == ForgedBlade.Finish.RUNIC ? 1.5F : 1F);
            if (rune.matches(Element.LIGHT) && target.getType().is(EntityTypeTags.UNDEAD)) extra *= 1.5F;
            amount += extra;
            if (rune.matches(Element.FIRE)) Statuses.apply(target, Statuses.Kind.BURNING, 1, 60, attacker, 1F);
            if (rune.matches(Element.WATER)) Statuses.apply(target, Statuses.Kind.WET, 1, 80, attacker, 1F);
            if (rune.matches(Element.EARTH)) target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40, 0), attacker);
            if (rune.matches(Element.WIND)) {
                target.push(0, 0.25, 0);
                target.hurtMarked = true;
            }
            if (rune.matches(Element.SHADOW)) target.addEffect(new MobEffectInstance(MobEffects.WITHER, 40, 0), attacker);
            ElementFx.burst(sl, c, rune, 8, 0.3);
        }
        return amount;
    }

    // =====================================================================================
    //  Contadores das Provas
    // =====================================================================================
    private static void onDeath(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level().isClientSide || !(e.getSource().getEntity() instanceof ServerPlayer sp) || !isSmith(sp)) return;
        if (e.getSource().getDirectEntity() != sp) return;
        ItemStack weapon = sp.getMainHandItem();
        if (!ForgedBladeItem.is(weapon) && !(weapon.getItem() instanceof SmithHammerItem)) return;
        MagicApi.addCounter(sp, SmithProgress.BLADE_KILLS, 1);
        if (victim.getMaxHealth() >= ELITE_HEALTH || BossKit.isBossLike(victim)) MagicApi.addCounter(sp, SmithProgress.ELITE_KILLS, 1);
    }

    private static void onBreak(BlockEvent.BreakEvent e) {
        if (e.getState().is(Tags.Blocks.ORES) && isSmith(e.getPlayer()) && !e.getPlayer().isCreative()) {
            MagicApi.addCounter((ServerPlayer) e.getPlayer(), SmithProgress.ORES, 1);
        }
    }

    private static void onCast(SpellCastEvent.Post e) {
        if (e.getCaster() instanceof ServerPlayer sp && isSmith(sp) && SmithGlyphs.usesSmithGlyph(e.getContext().recipe())) {
            MagicApi.addCounter(sp, SmithProgress.CASTS, 1);
        }
    }

    // =====================================================================================
    //  Reparo passivo + entrega da roupa
    // =====================================================================================
    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || !sp.isAlive()) return;
        int phase = (sp.tickCount + sp.getId()) % REPAIR_INTERVAL;
        if (phase != 0 && phase != REPAIR_INTERVAL / 2) return;
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.SMITH) return;
        if (phase == REPAIR_INTERVAL / 2) {
            SmithOutfits.grantPending(sp);
            return;
        }
        int amount = 1 + SmithProgress.rank(d) / 2;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() == EquipmentSlot.Type.ANIMAL_ARMOR) continue;
            ItemStack s = sp.getItemBySlot(slot);
            if (!s.isEmpty() && s.isDamageableItem() && s.isDamaged()) s.setDamageValue(Math.max(0, s.getDamageValue() - amount));
        }
    }

    private SmithEvents() {}
}
