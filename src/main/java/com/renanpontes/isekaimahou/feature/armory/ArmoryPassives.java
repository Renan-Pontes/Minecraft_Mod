package com.renanpontes.isekaimahou.feature.armory;

import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.feature.armory.item.ArmoryWeapon;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Vector3f;

/**
 * Passivas das armas (servidor):
 * <ul>
 *   <li>Cajado do Arquimago (IV) — Eco Arcano: 10% de chance de devolver o mana da magia.</li>
 *   <li>Varinha IV — Maldição Persistente: acertos de magia aplicam Corrompido.</li>
 *   <li>Lâmina do Pacto IV — Sede do Pacto: golpes roubam 25% do dano como vida.</li>
 *   <li>Cajado de Galhos IV — Seiva Viva: regenera o portador e os aliados próximos.</li>
 *   <li>Foices (todas) — ceifam almas para o Filactério do módulo ritual_necro (por id, sem depender da classe);
 *       a IV colhe uma alma extra (sem Filactério, a alma vira mana).</li>
 * </ul>
 */
public final class ArmoryPassives {
    private static final String MSG = "message." + IsekaiMahou.MODID + ".armory.";

    /** Eco Arcano: chance de devolver o custo. */
    public static final float MANA_ECHO_CHANCE = 0.10F;
    /** Maldição Persistente: Corrompido II por 5 s (1,5 de dano por segundo; o I dos chefes dá só 0,5/s). */
    public static final int CURSE_TICKS = 100;
    public static final int CURSE_AMPLIFIER = 1;
    /** Sede do Pacto: fração do dano devolvida como vida, e o teto por golpe (v1.0: ×HEAL_SCALE — a vida base dobrou). */
    public static final float LIFESTEAL = 0.25F;
    public static final float LIFESTEAL_CAP = 5.0F * Progression.HEAL_SCALE;
    /** Seiva Viva: cura a cada 2 s, raio dos aliados (v1.0: 1 coração, era meio). */
    public static final int REGEN_INTERVAL = 40;
    public static final float REGEN_AMOUNT = 1.0F * Progression.HEAL_SCALE;
    public static final double REGEN_RADIUS = 6.0;
    /** Mana dado pela alma extra quando não há Filactério (v1.0: ×MANA_SCALE, a mana é 3× maior). */
    public static final float SOUL_MANA = 6.0F * ResourceTraining.MANA_SCALE;

    // Ids do módulo ritual_necro (resolvidos em tempo de execução; se o módulo não existir, a colheita vira mana)
    private static final ResourceLocation PHYLACTERY_ID = IsekaiMahou.id("phylactery");
    private static final ResourceLocation SOULS_COMPONENT_ID = IsekaiMahou.id("necro_souls");
    private static final TagKey<EntityType<?>> SOULLESS = TagKey.create(Registries.ENTITY_TYPE, IsekaiMahou.id("necro_soulless"));
    private static final String NECRO_OWNER_TAG = "isekai_mahou.necro_owner";
    private static final String SOULS_COUNTER = "necro.souls";
    private static final int MAX_SOULS_PER_TYPE = 99;

    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.85F, 0.08F, 0.12F), 1.0F);

    // =====================================================================================
    //  Cajado do Arquimago — Eco Arcano
    // =====================================================================================
    public static void onSpellPost(SpellCastEvent.Post event) {
        if (!(event.getCaster() instanceof ServerPlayer sp) || sp.getAbilities().instabuild) return;
        if (!event.getContext().isPlayerCast()) return;
        if (!ArmoryWeapon.is(sp.getMainHandItem(), WeaponKind.MAGE_STAFF, 4)) return;
        ParsedSpell spell = event.getContext().spell();
        // Sobrecarga paga com vida: não há mana para devolver
        if (spell == null || spell.manaCost() <= 0 || spell.anyStats(s -> s.overload)) return;
        if (sp.getRandom().nextFloat() >= MANA_ECHO_CHANCE) return;
        MagicData d = MagicApi.get(sp);
        d.addMana(spell.manaCost());
        ServerLevel level = sp.serverLevel();
        level.sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.2, sp.getZ(), 30, 0.5, 0.7, 0.5, 0.6);
        level.sendParticles(ParticleTypes.END_ROD, sp.getX(), sp.getY() + 1.0, sp.getZ(), 8, 0.3, 0.5, 0.3, 0.03);
        level.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 0.9F, 1.4F);
        sp.displayClientMessage(Component.translatable(MSG + "mana_echo", spell.manaCost()).withStyle(ChatFormatting.AQUA), true);
    }

    // =====================================================================================
    //  Varinha IV (Corrompido) e Lâmina IV (roubo de vida)
    // =====================================================================================
    public static void onDamagePost(LivingDamageEvent.Post event) {
        float dealt = event.getNewDamage();
        if (dealt <= 0) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer sp)) return;
        LivingEntity target = event.getEntity();
        if (target == sp || !target.isAlive()) return;
        ItemStack main = sp.getMainHandItem();
        boolean spell = source.is(CoreRegistries.SPELL_DAMAGE);

        if (spell && ArmoryWeapon.is(main, WeaponKind.WITCH_WAND, 4)) {
            boolean fresh = !target.hasEffect(CoreRegistries.CORRUPTED);
            target.addEffect(new MobEffectInstance(CoreRegistries.CORRUPTED, CURSE_TICKS, CURSE_AMPLIFIER), sp);
            if (fresh && target.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.WITCH, target.getX(), target.getY() + target.getBbHeight() * 0.6, target.getZ(),
                        10, 0.3, 0.4, 0.3, 0.02);
            }
        } else if (!spell && source.getDirectEntity() == sp && ArmoryWeapon.is(main, WeaponKind.PACT_BLADE, 4)) {
            if (sp.getHealth() >= sp.getMaxHealth()) return;
            float heal = Math.min(LIFESTEAL_CAP, dealt * LIFESTEAL);
            sp.heal(heal);
            ServerLevel level = sp.serverLevel();
            // Fiozinho de sangue do alvo até o jogador
            Vec3 from = target.position().add(0, target.getBbHeight() * 0.6, 0);
            Vec3 to = sp.position().add(0, sp.getBbHeight() * 0.6, 0);
            for (int i = 0; i <= 6; i++) {
                Vec3 p = from.lerp(to, i / 6.0);
                level.sendParticles(BLOOD, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0);
            }
        }
    }

    // =====================================================================================
    //  Cajado de Galhos IV — Seiva Viva
    // =====================================================================================
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer sp) || sp.tickCount % REGEN_INTERVAL != 0) return;
        if (!sp.isAlive() || sp.isSpectator() || !ArmoryWeapon.is(sp.getMainHandItem(), WeaponKind.DRUID_STAFF, 4)) return;
        ServerLevel level = sp.serverLevel();
        List<LivingEntity> near = level.getEntitiesOfClass(LivingEntity.class, sp.getBoundingBox().inflate(REGEN_RADIUS),
                e -> e.isAlive() && ArmoryAllies.isFriend(sp, e) && e.distanceToSqr(sp) <= REGEN_RADIUS * REGEN_RADIUS);
        for (LivingEntity e : near) {
            if (e.getHealth() >= e.getMaxHealth()) continue;
            e.heal(REGEN_AMOUNT);
            level.sendParticles(ParticleTypes.HAPPY_VILLAGER, e.getX(), e.getY() + e.getBbHeight() * 0.7, e.getZ(),
                    4, 0.3, 0.3, 0.3, 0.0);
        }
    }

    // =====================================================================================
    //  Foices — Colheita de Almas
    // =====================================================================================
    public static void onDeath(LivingDeathEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) return;
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer sp) || sp instanceof FakePlayer || sp.isSpectator()) return;
        if (source.getDirectEntity() != sp || source.is(CoreRegistries.SPELL_DAMAGE)) return;
        ArmoryWeapon weapon = ArmoryWeapon.of(sp.getMainHandItem());
        if (weapon == null || weapon.kind() != WeaponKind.REAPER_SCYTHE) return;
        if (!(victim instanceof Mob) || victim.getType().is(SOULLESS)) return;
        // Os próprios servos/mascotes não rendem almas (mesma regra da Foice de Ossos)
        if (victim instanceof OwnableEntity own && sp.getUUID().equals(own.getOwnerUUID())) return;
        CompoundTag data = victim.getPersistentData();
        if (data.hasUUID(NECRO_OWNER_TAG) && sp.getUUID().equals(data.getUUID(NECRO_OWNER_TAG))) return;

        Item phylacteryItem = BuiltInRegistries.ITEM.getOptional(PHYLACTERY_ID).orElse(null);
        // Filactério na mão secundária: o módulo ritual_necro já guarda 1 alma; a foice só soma a extra do tier IV
        boolean handled = phylacteryItem != null && sp.getOffhandItem().is(phylacteryItem);
        int souls = (weapon.tier() >= WeaponKind.MAX_TIER ? 1 : 0) + (handled ? 0 : 1);
        if (souls <= 0) return;

        Vec3 from = victim.position().add(0, victim.getBbHeight() * 0.6, 0);
        ItemStack phylactery = handled ? sp.getOffhandItem() : findPhylactery(sp, phylacteryItem);
        ResourceLocation type = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        int stored = phylactery.isEmpty() ? -1 : addSouls(phylactery, type, souls);
        if (stored > 0) {
            boolean boss = victim.getType().is(Tags.EntityTypes.BOSSES);
            int xp = boss ? Mth.clamp(40 + Math.round(victim.getMaxHealth() / 10F), 40, 120)
                    : Mth.clamp(Math.round(victim.getMaxHealth() / 8F), 1, 25);
            MagicApi.giveXp(sp, xp * stored, "soul");
            MagicApi.giveElementXp(sp, Element.SHADOW, (boss ? 10 : 1) * stored);
            MagicApi.addCounter(sp, SOULS_COUNTER, stored);
            sp.displayClientMessage(Component.translatable(MSG + "soul_harvest", stored, victim.getType().getDescription())
                    .withStyle(ChatFormatting.DARK_AQUA), true);
        } else if (weapon.tier() >= WeaponKind.MAX_TIER) {
            // Sem Filactério (ou cheio): a alma extra alimenta o mana do ceifador
            MagicApi.get(sp).addMana(SOUL_MANA);
            sp.displayClientMessage(Component.translatable(MSG + "soul_mana", (int) SOUL_MANA).withStyle(ChatFormatting.DARK_AQUA), true);
        } else {
            return;
        }
        soulWisp(level, from, sp);
    }

    /** Rastro de almas do corpo até o jogador (instantâneo, só partículas) + som. */
    private static void soulWisp(ServerLevel level, Vec3 from, ServerPlayer sp) {
        Vec3 to = sp.position().add(0, sp.getBbHeight() * 0.6, 0);
        level.sendParticles(ParticleTypes.SCULK_SOUL, from.x, from.y, from.z, 6, 0.25, 0.25, 0.25, 0.02);
        for (int i = 1; i <= 8; i++) {
            Vec3 p = from.lerp(to, i / 8.0).add(0, Math.sin(i / 8.0 * Math.PI) * 0.6, 0);
            level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, p.x, p.y, p.z, 1, 0.03, 0.03, 0.03, 0.0);
        }
        level.playSound(null, from.x, from.y, from.z, SoundEvents.SOUL_ESCAPE, SoundSource.PLAYERS, 1.1F, 0.9F);
    }

    /** Filactério do inventário com mais almas (como o do ritual_necro escolhe). */
    private static ItemStack findPhylactery(Player player, Item phylactery) {
        if (phylactery == null) return ItemStack.EMPTY;
        ItemStack best = ItemStack.EMPTY;
        int bestTotal = -1;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(phylactery)) continue;
            int total = soulTotal(s);
            if (total > bestTotal) {
                best = s;
                bestTotal = total;
            }
        }
        return best;
    }

    private static int soulTotal(ItemStack stack) {
        JsonObject obj = readSouls(stack);
        int t = 0;
        for (var e : obj.entrySet()) t += e.getValue().getAsInt();
        return t;
    }

    /** Lê o componente de almas como JSON {tipo: quantidade} (vazio se não existir). */
    private static JsonObject readSouls(ItemStack stack) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(SOULS_COMPONENT_ID);
        return type == null ? new JsonObject() : read(stack, type);
    }

    private static <T> JsonObject read(ItemStack stack, DataComponentType<T> type) {
        T value = stack.get(type);
        Codec<T> codec = type.codec();
        if (value == null || codec == null) return new JsonObject();
        return codec.encodeStart(JsonOps.INSTANCE, value).result()
                .filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElseGet(JsonObject::new);
    }

    /**
     * Soma almas no Filactério pelo codec do componente (o tipo Java é do outro módulo).
     * @return quantas couberam (0 se cheio ou se o componente não existe)
     */
    private static int addSouls(ItemStack phylactery, ResourceLocation mob, int amount) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(SOULS_COMPONENT_ID);
        return type == null ? 0 : add(phylactery, type, mob, amount);
    }

    private static <T> int add(ItemStack stack, DataComponentType<T> type, ResourceLocation mob, int amount) {
        Codec<T> codec = type.codec();
        if (codec == null) return 0;
        JsonObject obj = read(stack, type);
        String key = mob.toString();
        int have = obj.has(key) ? obj.get(key).getAsInt() : 0;
        int added = Math.min(amount, MAX_SOULS_PER_TYPE - have);
        if (added <= 0) return 0;
        obj.addProperty(key, have + added);
        T parsed = codec.parse(JsonOps.INSTANCE, obj).result().orElse(null);
        if (parsed == null) return 0;
        stack.set(type, parsed);
        return added;
    }

    private ArmoryPassives() {}
}
