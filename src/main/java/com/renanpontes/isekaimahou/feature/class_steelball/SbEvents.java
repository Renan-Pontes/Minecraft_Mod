package com.renanpontes.isekaimahou.feature.class_steelball;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.class_steelball.glyph.SbGlyphs;
import com.renanpontes.isekaimahou.feature.class_steelball.glyph.SbTechniqueGlyphs;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.storage.loot.entries.EmptyLootItem;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.providers.number.ConstantValue;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.LootTableLoadEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Eventos de jogo do Steel Ball:
 * <ul>
 *   <li>escolha da classe: glifos e magias iniciais nos slots;</li>
 *   <li>bônus de classe: "Giro Perfeito" (+15% +5%/Ato nas magias com técnica de rotação), montado (+25% de Poder
 *   Mágico, montaria +20% mais rápida e sem dano de queda), Retângulo Dourado e Energia do Galope no dano;</li>
 *   <li>Partes do Cadáver: atributos permanentes e os Olhos (revelar);</li>
 *   <li>treinos das lições (contadores), roupa por Círculo, Relicários nos baús de estruturas e nos chefes.</li>
 * </ul>
 */
public final class SbEvents {
    private static final ResourceLocation MOUNTED = IsekaiMahou.id("steelball_mounted");
    private static final ResourceLocation RIDER = IsekaiMahou.id("steelball_rider");
    /** Jogador → montaria com o bônus de velocidade (para tirar quando ele desce). */
    private static final Map<UUID, UUID> STEEDS = new HashMap<>();
    /** Último acerto contado no Retângulo Dourado (alvo, tick) por jogador — um por golpe, não por efeito. */
    private static final Map<UUID, Long> LAST_GOLDEN = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SbEvents::onPathChosen);
        NeoForge.EVENT_BUS.addListener(SbEvents::onCircle);
        NeoForge.EVENT_BUS.addListener(SbEvents::onLogin);
        NeoForge.EVENT_BUS.addListener(SbEvents::onRespawn);
        NeoForge.EVENT_BUS.addListener(SbEvents::onLogout);
        NeoForge.EVENT_BUS.addListener(SbEvents::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(SbEvents::onDamagePre);
        NeoForge.EVENT_BUS.addListener(SbEvents::onDamagePost);
        NeoForge.EVENT_BUS.addListener(SbEvents::onCast);
        NeoForge.EVENT_BUS.addListener(SbEvents::onDeath);
        NeoForge.EVENT_BUS.addListener(SbEvents::onIncoming);
        NeoForge.EVENT_BUS.addListener(SbEvents::onLootLoad);
        NeoForge.EVENT_BUS.addListener(SbTechniques::onIncoming);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> SbTechniqueGlyphs.GoldenGallopForm.tickRuns(e.getServer().overworld()));
        SbLoveTrain.register();
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> {
            STEEDS.clear();
            LAST_GOLDEN.clear();
            SbTechniques.clear();
            SbTechniqueGlyphs.GoldenGallopForm.clear();
            SbLoveTrain.clear();
        });
    }

    // =====================================================================================
    //  Escolha da classe: técnicas e magias iniciais
    // =====================================================================================

    private static void onPathChosen(MagicProgressEvent.PathChosen e) {
        ServerPlayer sp = e.getPlayer();
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.STEELBALL) return;
        MagicApi.learnGlyphSilently(sp, IsekaiMahou.id(SbGlyphs.SPIN_BALL));
        MagicApi.learnGlyphSilently(sp, IsekaiMahou.id("rotation_shock"));
        boolean pt = sp.clientInformation().language().toLowerCase(Locale.ROOT).startsWith("pt");
        // as magias prontas do núcleo (Flecha) dão lugar às da classe, só se o jogador ainda não mexeu nos slots
        SpellRecipe ball = new SpellRecipe(java.util.List.of(IsekaiMahou.id(SbGlyphs.SPIN_BALL), IsekaiMahou.id("rotation_shock")),
                pt ? "Bola de Aço" : "Steel Ball", SbFx.STEEL, "");
        SpellRecipe push = new SpellRecipe(java.util.List.of(IsekaiMahou.id(SbGlyphs.SPIN_BALL), IsekaiMahou.id("push")),
                pt ? "Bola Repulsora" : "Repelling Ball", 0x9FB0C0, "");
        replaceStarter(d, 0, ball);
        replaceStarter(d, 1, push);
        SbOutfits.grantPending(sp);
    }

    private static void replaceStarter(MagicData d, int slot, SpellRecipe recipe) {
        SpellRecipe cur = d.peekSlot(0, slot).get(MoveTrigger.NORMAL);
        boolean coreStarter = cur == null || cur.isEmpty() || cur.glyphs().contains(IsekaiMahou.id("arrow"));
        if (!coreStarter) return;
        d.setSlotRecipe(0, slot, MoveTrigger.NORMAL, recipe);
        d.markDirty();
    }

    private static void onCircle(MagicProgressEvent.CircleChanged e) {
        if (e.newCircle > e.oldCircle) SbOutfits.grantPending(e.getPlayer());
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) {
            syncParts(sp);
            SbOutfits.grantPending(sp);
            SbTechniques.check(sp);
            SbLoveTrain.check(sp);
        }
    }

    private static void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) syncParts(sp);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) clearSteed(sp);
        LAST_GOLDEN.remove(e.getEntity().getUUID());
        SbTechniques.forget(e.getEntity().getUUID());
        SbTechniqueGlyphs.GoldenGallopForm.forget(e.getEntity().getUUID());
        SbLoveTrain.forget(e.getEntity().getUUID());
    }

    // =====================================================================================
    //  Tick: montado, Olhos do Santo, atributos das partes
    // =====================================================================================

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || sp.tickCount % 10 != 0) return;
        MagicData d = MagicApi.get(sp);
        boolean steelball = d.path() == MagicPath.STEELBALL;
        Entity vehicle = sp.getVehicle();
        boolean mounted = steelball && vehicle instanceof LivingEntity;
        setMod(sp, CoreRegistries.SPELL_POWER, MOUNTED, 0.25, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, mounted, false);
        UUID old = STEEDS.get(sp.getUUID());
        if (mounted) {
            if (old != null && !old.equals(vehicle.getUUID())) clearSteed(sp);
            LivingEntity steed = (LivingEntity) vehicle;
            setMod(steed, Attributes.MOVEMENT_SPEED, RIDER, 0.20, AttributeModifier.Operation.ADD_MULTIPLIED_BASE, true, false);
            STEEDS.put(sp.getUUID(), steed.getUUID());
            if (sp.tickCount % 40 == 0) SbFx.spinRing(sp.serverLevel(), steed.position().add(0, 0.2, 0), SbFx.STEEL, 0.9, 8);
        } else if (old != null) {
            clearSteed(sp);
        }
        // Olhos do Santo: agachado, revela as criaturas hostis por perto
        if (sp.tickCount % 40 == 0 && sp.isShiftKeyDown() && SbPart.EYES.owned(d)) {
            for (Mob m : sp.serverLevel().getEntitiesOfClass(Mob.class, sp.getBoundingBox().inflate(16), x -> x instanceof Enemy && x.isAlive())) {
                m.addEffect(new MobEffectInstance(MobEffects.GLOWING, 50, 0, false, false));
            }
        }
        if (sp.tickCount % 200 == 0) syncParts(sp);
    }

    private static void clearSteed(ServerPlayer sp) {
        UUID id = STEEDS.remove(sp.getUUID());
        if (id == null) return;
        Entity steed = sp.serverLevel().getEntity(id);
        if (steed instanceof LivingEntity le) {
            AttributeInstance inst = le.getAttribute(Attributes.MOVEMENT_SPEED);
            if (inst != null) inst.removeModifier(RIDER);
        }
    }

    /** Atributos permanentes das Partes do Cadáver (põe/tira conforme as flags; nunca duplica). */
    public static void syncParts(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        boolean heart = SbPart.HEART.owned(d), spine = SbPart.SPINE.owned(d), ears = SbPart.EARS.owned(d), legs = SbPart.LEGS.owned(d);
        boolean torso = SbPart.TORSO.owned(d), skull = SbPart.SKULL.owned(d);
        var add = AttributeModifier.Operation.ADD_VALUE;
        var base = AttributeModifier.Operation.ADD_MULTIPLIED_BASE;
        setMod(sp, Attributes.MAX_HEALTH, IsekaiMahou.id("steelball_part_heart"), 8.0, add, heart, true);
        setMod(sp, Attributes.KNOCKBACK_RESISTANCE, IsekaiMahou.id("steelball_part_spine"), 0.25, add, spine, true);
        setMod(sp, Attributes.ARMOR_TOUGHNESS, IsekaiMahou.id("steelball_part_spine_toughness"), 2.0, add, spine, true);
        setMod(sp, CoreRegistries.MANA_REGEN, IsekaiMahou.id("steelball_part_ears"), 0.15, base, ears, true);
        setMod(sp, Attributes.MOVEMENT_SPEED, IsekaiMahou.id("steelball_part_legs"), 0.08, base, legs, true);
        setMod(sp, Attributes.SAFE_FALL_DISTANCE, IsekaiMahou.id("steelball_part_legs_fall"), 2.0, add, legs, true);
        setMod(sp, Attributes.ARMOR, IsekaiMahou.id("steelball_part_torso"), 4.0, add, torso, true);
        setMod(sp, CoreRegistries.SPELL_POWER, IsekaiMahou.id("steelball_part_skull"), 0.08, base, skull, true);
        setMod(sp, CoreRegistries.MAX_MANA, IsekaiMahou.id("steelball_part_skull_focus"), 20.0 * ResourceTraining.MANA_SCALE, add, skull, true);
    }

    private static void setMod(LivingEntity e, Holder<Attribute> attr, ResourceLocation id, double amount,
            AttributeModifier.Operation op, boolean on, boolean permanent) {
        AttributeInstance inst = e.getAttribute(attr);
        if (inst == null) return;
        AttributeModifier cur = inst.getModifier(id);
        if (!on) {
            if (cur != null) inst.removeModifier(id);
            return;
        }
        if (cur != null && cur.amount() == amount && cur.operation() == op) return;
        inst.removeModifier(id);
        AttributeModifier m = new AttributeModifier(id, amount, op);
        if (permanent) inst.addPermanentModifier(m);
        else inst.addTransientModifier(m);
    }

    // =====================================================================================
    //  Dano de magia: bônus de classe, Retângulo Dourado, Energia do Galope, Braços do Santo
    // =====================================================================================

    private static void onDamagePre(SpellDamageEvent.Pre e) {
        SpellContext ctx = e.ctx();
        LivingEntity caster = ctx.caster();
        float m = 1F;
        boolean spinSpell = hasSpinGlyph(ctx.recipe());
        if (spinSpell) m *= SbProgress.spinMultiplier(caster, true);
        if (SbGlyphs.golden(ctx, e.stats())) m *= SbGlyphs.GOLDEN_MULT;
        if (SbGlyphs.gallop(ctx, e.stats())) m *= SbGlyphs.mounted(caster) ? SbGlyphs.GALLOP_MOUNTED : SbGlyphs.GALLOP_ON_FOOT;
        if (caster instanceof ServerPlayer sp && spinSpell) {
            MagicData d = MagicApi.get(sp);
            String form = ctx.spell().form().id().getPath();
            if (form.equals(SbGlyphs.SPIN_NAIL) && SbPart.LEFT_ARM.owned(d)) m *= 1.2F;
            if (form.equals(SbGlyphs.SPIN_BALL) && SbPart.RIGHT_ARM.owned(d)) m *= 1.2F;
        }
        if (m != 1F) e.setAmount(e.getAmount() * m);
    }

    private static void onDamagePost(SpellDamageEvent.Post e) {
        if (!e.landed() || !(e.ctx().caster() instanceof ServerPlayer sp)) return;
        if (!SbGlyphs.golden(e.ctx(), e.stats())) return;
        long key = e.target().getId() * 31L + sp.level().getGameTime();
        Long last = LAST_GOLDEN.put(sp.getUUID(), key);
        if (last != null && last == key) return;
        MagicApi.addCounter(sp, SbProgress.GOLDEN_HITS, 1);
    }

    /** A receita tem algum glifo de origem do Steel Ball? */
    private static boolean hasSpinGlyph(@Nullable SpellRecipe recipe) {
        if (recipe == null) return false;
        for (ResourceLocation id : recipe.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && g.origin() == MagicPath.STEELBALL) return true;
        }
        return false;
    }

    /** Energia do Galope: a montaria dispara junto com a técnica. */
    private static void onCast(SpellCastEvent.Post e) {
        LivingEntity caster = e.getCaster();
        if (caster == null || !SbGlyphs.gallop(e.getContext(), null) || !(caster.getVehicle() instanceof LivingEntity steed)) return;
        steed.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 60, 1, false, false));
        if (caster.level() instanceof ServerLevel sl) {
            SbFx.spinRing(sl, steed.position().add(0, 0.3, 0), SbFx.GOLD, 1.1, 14);
            SbFx.sound(sl, steed.position(), net.minecraft.sounds.SoundEvents.HORSE_GALLOP, 0.9F, 1.2F);
        }
    }

    // =====================================================================================
    //  Abates montado, Relicário dos chefes, montaria sem dano de queda
    // =====================================================================================

    private static void onDeath(LivingDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (victim.level() instanceof ServerLevel vl && !(victim instanceof net.minecraft.world.entity.player.Player)
                && BossKit.isBossLike(victim)) {
            heartDrop(vl, victim);
        }
        if (!(e.getSource().getEntity() instanceof ServerPlayer sp) || victim instanceof net.minecraft.world.entity.player.Player) return;
        if (victim instanceof Enemy && sp.getVehicle() instanceof LivingEntity) MagicApi.addCounter(sp, SbProgress.MOUNTED_KILLS, 1);
        if (!BossKit.isBossLike(victim)) return;
        // chefes por perto contam para as lições (quem bateu por último e o grupo a até 48 blocos)
        for (ServerPlayer p : sp.serverLevel().getEntitiesOfClass(ServerPlayer.class, victim.getBoundingBox().inflate(48))) {
            if (MagicApi.get(p).path() == MagicPath.STEELBALL) MagicApi.addCounter(p, SbProgress.BOSS_KILLS, 1);
        }
        if (MagicApi.get(sp).path() == MagicPath.STEELBALL && sp.getRandom().nextFloat() < 0.35F) {
            victim.spawnAtLocation(new ItemStack(SbRegistry.RELIC.get()));
        }
    }

    /**
     * O CORAÇÃO DO CADÁVER SANTO (a última parte): quando um chefe morre, cada jogador a até 48 blocos que já tem as
     * outras 8 partes no corpo (e ainda não carrega um Coração) ouve o coração bater dentro dele — o Coração cai ali,
     * com coluna de luz e batidas.
     */
    private static void heartDrop(ServerLevel sl, LivingEntity boss) {
        for (ServerPlayer p : sl.getEntitiesOfClass(ServerPlayer.class, boss.getBoundingBox().inflate(48))) {
            if (!SbPart.readyForHeart(MagicApi.get(p)) || carriesHeart(p)) continue;
            ItemStack heart = new ItemStack(SbRegistry.PARTS.get(SbPart.FINAL).get());
            var drop = boss.spawnAtLocation(heart);
            if (drop != null) {
                drop.setGlowingTag(true);
                drop.setUnlimitedLifetime();
            }
            Vec3 c = boss.position().add(0, 0.5, 0);
            SbFx.sound(sl, c, net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.8F);
            SbFx.sound(sl, c, net.minecraft.sounds.SoundEvents.BEACON_ACTIVATE, 1.4F, 1.3F);
            for (int k = 1; k <= 3; k++) {
                SpellScheduler.schedule(12 * k, () -> SbFx.sound(sl, c, net.minecraft.sounds.SoundEvents.WARDEN_HEARTBEAT, 2.0F, 0.9F));
            }
            for (int i = 0; i < 24; i++) {
                double y = c.y + i * 0.5;
                sl.sendParticles(SbFx.dust(SbFx.GOLD, 1.4F), c.x, y, c.z, 2, 0.08, 0.1, 0.08, 0);
                sl.sendParticles(ParticleTypes.END_ROD, c.x, y, c.z, 1, 0.05, 0.1, 0.05, 0.01);
            }
            SbFx.spiral(sl, boss.position(), 0xFFE6A0, 1.4, 3.0, 40, 0F);
            p.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.isekai_mahou.steelball.heart_found")
                    .withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD));
            SbTechniques.callout(p, net.minecraft.network.chat.Component.translatable("callout.isekai_mahou.steelball.heart_found"),
                    net.minecraft.network.chat.Component.translatable("callout.isekai_mahou.steelball.heart_found.sub"), 0xFFE6A0);
        }
    }

    /** O jogador já tem um Coração no inventário (para não cair outro)? */
    private static boolean carriesHeart(ServerPlayer p) {
        var item = SbRegistry.PARTS.get(SbPart.FINAL).get();
        for (ItemStack st : p.getInventory().items) if (st.is(item)) return true;
        return p.getOffhandItem().is(item);
    }

    private static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity le = e.getEntity();
        if (!e.getSource().is(DamageTypes.FALL) || !le.isVehicle()) return;
        for (Entity p : le.getPassengers()) {
            if (p instanceof ServerPlayer sp && MagicApi.get(sp).path() == MagicPath.STEELBALL) {
                e.setCanceled(true);
                return;
            }
        }
    }

    // =====================================================================================
    //  Relicários nos baús de estruturas (todas as tabelas "chests/", vanilla e do mod)
    // =====================================================================================

    private static void onLootLoad(LootTableLoadEvent e) {
        ResourceLocation name = e.getName();
        String path = name.getPath();
        if (!path.startsWith("chests/") || path.equals("chests/steelball_saint_tomb")) return;
        // estruturas grandes/perigosas têm mais chance
        boolean rich = path.contains("ancient_city") || path.contains("stronghold") || path.contains("end_city")
                || path.contains("bastion") || path.contains("woodland_mansion") || path.contains("jungle_temple")
                || path.contains("desert_pyramid") || path.contains("buried_treasure") || name.getNamespace().equals(IsekaiMahou.MODID);
        int empty = rich ? 15 : 45;
        e.getTable().addPool(LootPool.lootPool()
                .name(IsekaiMahou.MODID + ":steelball_relic")
                .setRolls(ConstantValue.exactly(1))
                .add(LootItem.lootTableItem(SbRegistry.RELIC.get()).setWeight(1))
                .add(EmptyLootItem.emptyItem().setWeight(empty))
                .build());
    }

    private SbEvents() {}
}
