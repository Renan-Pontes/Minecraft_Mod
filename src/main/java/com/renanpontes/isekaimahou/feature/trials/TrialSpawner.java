package com.renanpontes.isekaimahou.feature.trials;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.boss.BossKit;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * Criação das criaturas e chefes das Câmaras: empoderamento por rank/grupo, Elites com nome e brilho, chefes de
 * provação (por id, módulo bosses_d), Guardiões e um "Campeão" de reserva caso nenhum chefe exista.
 */
public final class TrialSpawner {
    private static final ResourceLocation HP = IsekaiMahou.id("trial_health");
    private static final ResourceLocation DMG = IsekaiMahou.id("trial_damage");
    private static final ResourceLocation ELITE_HP = IsekaiMahou.id("trial_elite_health");
    private static final ResourceLocation ELITE_DMG = IsekaiMahou.id("trial_elite_damage");
    private static final ResourceLocation ELITE_SCALE = IsekaiMahou.id("trial_elite_scale");
    private static final ResourceLocation ELITE_ARMOR = IsekaiMahou.id("trial_elite_armor");

    /** Chaves lidas pelos chefes de provação (bosses_d) no 1º tick. */
    public static final String KEY_ELEMENT = "isekai_mahou.trial_element";
    public static final String KEY_RANK = "isekai_mahou.trial_rank";

    /** Um chefe a invocar: id em texto, ponto e se é o "principal" (os outros são escolta). */
    public record BossSpec(String id, Vec3 pos, boolean main) {}

    // =====================================================================================
    //  Ondas
    // =====================================================================================

    /**
     * Cria e adiciona ao mundo uma criatura da onda. {@code role}: {@link TrialInstance#ROLE_MOB} ou
     * {@link TrialInstance#ROLE_ELITE}. Devolve null se o tipo não for um Mob.
     */
    @Nullable
    public static Mob spawnMob(TrialInstance inst, EntityType<?> type, Vec3 pos, int role, int players) {
        ServerLevel level = inst.level;
        Entity e = type.create(level);
        if (!(e instanceof Mob mob)) {
            if (e != null) e.discard();
            return null;
        }
        RandomSource r = level.random;
        mob.moveTo(pos.x, pos.y, pos.z, r.nextFloat() * 360F, 0F);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(BlockPos.containing(pos)), MobSpawnType.MOB_SUMMONED, null);
        mob.setPersistenceRequired();
        if (mob instanceof Slime slime) slime.setSize(role == TrialInstance.ROLE_ELITE ? 3 : 1 + r.nextInt(2), true);
        empower(mob, inst.rank, players);
        if (role == TrialInstance.ROLE_ELITE) makeElite(mob, inst.element);
        sunProof(mob, inst.element);
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
        TrialInstance.tag(mob, inst.id, role);
        for (Entity passenger : mob.getIndirectPassengers()) TrialInstance.tag(passenger, inst.id, TrialInstance.ROLE_ADD);
        level.addFreshEntityWithPassengers(mob);

        Vec3 c = pos.add(0, 0.5, 0);
        boolean elite = role == TrialInstance.ROLE_ELITE;
        ElementFx.burst(level, c, inst.element, elite ? 50 : 22, elite ? 0.8 : 0.45);
        level.sendParticles(ParticleTypes.POOF, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.02);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIAL_SPAWNER_SPAWN_MOB, SoundSource.HOSTILE, 0.9F, 0.9F + r.nextFloat() * 0.2F);
        if (elite) level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.35F, 1.7F);
        return mob;
    }

    /** Vida +20%·(rank−1) +10% por jogador extra; dano +10%·(rank−1). */
    private static void empower(Mob mob, int rank, int players) {
        double hp = 0.2 * (rank - 1) + 0.1 * Math.max(0, players - 1);
        if (hp > 0) addMod(mob, Attributes.MAX_HEALTH, HP, hp);
        if (rank > 1) addMod(mob, Attributes.ATTACK_DAMAGE, DMG, 0.1 * (rank - 1));
        mob.setHealth(mob.getMaxHealth());
    }

    /** Elite: vida ×2, dano +30%, 20% maior, armadura extra, nome e brilho permanentes. */
    private static void makeElite(Mob mob, Element element) {
        addMod(mob, Attributes.MAX_HEALTH, ELITE_HP, 1.0);
        addMod(mob, Attributes.ATTACK_DAMAGE, ELITE_DMG, 0.3);
        addMod(mob, Attributes.SCALE, ELITE_SCALE, 0.2);
        AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.addOrReplacePermanentModifier(new AttributeModifier(ELITE_ARMOR, 4.0, AttributeModifier.Operation.ADD_VALUE));
        mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(Component.translatable("entity.isekai_mahou.trial_elite", mob.getType().getDescription()).withColor(element.color));
        mob.setCustomNameVisible(true);
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
    }

    private static void addMod(Mob mob, net.minecraft.core.Holder<net.minecraft.world.entity.ai.attributes.Attribute> attr,
                               ResourceLocation id, double amount) {
        AttributeInstance inst = mob.getAttribute(attr);
        if (inst != null) inst.addOrReplacePermanentModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_BASE));
    }

    /** Mortos-vivos de reserva não queimam (a arena não tem sol, mas por garantia): elmo de couro na cor do elemento. */
    private static void sunProof(Mob mob, Element element) {
        if (!mob.getType().is(EntityTypeTags.UNDEAD) || !mob.getItemBySlot(EquipmentSlot.HEAD).isEmpty()) return;
        ItemStack helmet = new ItemStack(Items.LEATHER_HELMET);
        helmet.set(DataComponents.DYED_COLOR, new DyedItemColor(element.color, false));
        mob.setItemSlot(EquipmentSlot.HEAD, helmet);
        mob.setDropChance(EquipmentSlot.HEAD, 0F);
    }

    // =====================================================================================
    //  Chefes
    // =====================================================================================

    /**
     * Quem aparece no fim (design §4): I–IV Avatar; V Guardião (fusão: Avatar V); VI Avatar + 2 Sentinelas;
     * VII Guardião + Avatar juntos (fusão: Guardião do pai A + Avatar).
     */
    public static List<BossSpec> lineup(Element element, int rank, Vec3 center) {
        List<BossSpec> out = new ArrayList<>();
        Vec3 north = center.add(0, 0, -7);
        String guardian = TrialTheme.guardian(element);
        if (rank <= 4) {
            out.add(new BossSpec(TrialTheme.AVATAR, north, true));
        } else if (rank == 5) {
            out.add(new BossSpec(element.isFusion() || guardian == null ? TrialTheme.AVATAR : guardian, north, true));
        } else if (rank == 6) {
            out.add(new BossSpec(TrialTheme.AVATAR, north, true));
            out.add(new BossSpec(TrialTheme.SENTINEL, center.add(-8, 0, -3), false));
            out.add(new BossSpec(TrialTheme.SENTINEL, center.add(8, 0, -3), false));
        } else {
            out.add(new BossSpec(guardian == null ? TrialTheme.AVATAR : guardian, center.add(-7, 0, -5), true));
            out.add(new BossSpec(TrialTheme.AVATAR, center.add(7, 0, -5), true));
        }
        return out;
    }

    /** Nome do chefe principal para títulos/painel (tradução do tipo, ou "Campeão" se o tipo não existir). */
    public static Component bossName(String id) {
        Optional<EntityType<?>> t = TrialTheme.type(id);
        if (t.isPresent()) return t.get().getDescription();
        return Component.translatable("entity.isekai_mahou.trial_champion");
    }

    /** Invoca um chefe da lista (com as reservas: Guardião ausente → Avatar; Avatar/Sentinela ausente → Campeão/Elite). */
    @Nullable
    public static Mob spawnBoss(TrialInstance inst, BossSpec spec) {
        ServerLevel level = inst.level;
        Optional<EntityType<?>> type = TrialTheme.type(spec.id());
        if (type.isEmpty() && !spec.id().equals(TrialTheme.AVATAR) && !spec.id().equals(TrialTheme.SENTINEL)) {
            type = TrialTheme.type(TrialTheme.AVATAR);
        }
        Mob mob = null;
        boolean fallback = type.isEmpty();
        if (!fallback && type.get().create(level) instanceof Mob m) {
            mob = m;
        }
        if (mob == null) {
            fallback = true;
            // sem o módulo de chefes: a criatura mais forte que o rank comporta (I–II tier 2, III+ tier 3)
            Entity e = TrialTheme.pick(inst.element, inst.rank <= 2 ? 2 : 3, level.random).create(level);
            if (!(e instanceof Mob m)) {
                if (e != null) e.discard();
                return null;
            }
            mob = m;
        }
        mob.getPersistentData().putString(KEY_ELEMENT, inst.element.id);
        mob.getPersistentData().putInt(KEY_RANK, inst.rank);
        mob.moveTo(spec.pos().x, spec.pos().y, spec.pos().z, 0F, 0F);
        mob.setYHeadRot(0F);
        EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(BlockPos.containing(spec.pos())), MobSpawnType.EVENT, null);
        mob.setPersistenceRequired();
        if (fallback) makeChampion(mob, inst, spec.main());
        BossKit.markTrialBoss(mob);
        BossKit.setArena(mob, Vec3.atBottomCenterOf(inst.arenaCenter).add(0, 1, 0), inst.radius);
        TrialInstance.tag(mob, inst.id, TrialInstance.ROLE_BOSS);
        level.addFreshEntityWithPassengers(mob);
        BossKit.scaleToParty(mob, inst.radius + 24);

        Vec3 c = spec.pos().add(0, 1, 0);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, c.x, c.y, c.z, 60, 1.0, 1.2, 1.0, 0.08);
        ElementFx.burst(level, c, inst.element, 90, 1.4);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.TRIAL_SPAWNER_OMINOUS_ACTIVATE, SoundSource.HOSTILE, 2.0F, 0.7F);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 0.8F, 1.0F);
        return mob;
    }

    /** Campeão de reserva (sem o módulo de chefes): criatura forte do elemento muito reforçada, com nome. */
    private static void makeChampion(Mob mob, TrialInstance inst, boolean main) {
        double hp = main ? 2.0 + inst.rank * 0.5 : 1.0 + inst.rank * 0.3;
        addMod(mob, Attributes.MAX_HEALTH, ELITE_HP, hp);
        addMod(mob, Attributes.ATTACK_DAMAGE, ELITE_DMG, 0.4 + 0.1 * inst.rank);
        addMod(mob, Attributes.SCALE, ELITE_SCALE, main ? 0.45 : 0.25);
        AttributeInstance armor = mob.getAttribute(Attributes.ARMOR);
        if (armor != null) armor.addOrReplacePermanentModifier(new AttributeModifier(ELITE_ARMOR, 4.0 + inst.rank, AttributeModifier.Operation.ADD_VALUE));
        AttributeInstance kb = mob.getAttribute(Attributes.KNOCKBACK_RESISTANCE);
        if (kb != null) kb.setBaseValue(Math.max(kb.getBaseValue(), 0.6));
        mob.setHealth(mob.getMaxHealth());
        mob.setCustomName(Component.translatable(main ? "entity.isekai_mahou.trial_champion" : "entity.isekai_mahou.trial_guard")
                .withColor(inst.element.color));
        mob.setCustomNameVisible(true);
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        if (mob instanceof Slime slime) slime.setSize(4, true);
    }

    private TrialSpawner() {}
}
