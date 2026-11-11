package com.renanpontes.isekaimahou.feature.class_gambler.outfit;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.ClassKits;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Traje do Apostador em 4 graus (16 peças): materiais, itens, kit (grau I) e a entrega dos graus II/III/IV ao chegar nos
 * Círculos 3/5/8 (uma vez cada; flag {@code gambler_outfit.<grau>}), como os trajes das outras classes.
 */
public final class GamblerOutfits {
    public static final DeferredRegister<ArmorMaterial> MATERIALS = DeferredRegister.create(Registries.ARMOR_MATERIAL, IsekaiMahou.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(IsekaiMahou.MODID);
    public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
            ArmorItem.Type.BOOTS};

    private static final Map<GamblerOutfitTier, Map<ArmorItem.Type, DeferredItem<GamblerOutfitItem>>> PIECES =
            new EnumMap<>(GamblerOutfitTier.class);
    private static final Map<UUID, Integer> LOGIN_DELAY = new HashMap<>();

    static {
        for (GamblerOutfitTier tier : GamblerOutfitTier.values()) {
            DeferredHolder<ArmorMaterial, ArmorMaterial> material = MATERIALS.register(tier.materialId(), () -> material(tier));
            Map<ArmorItem.Type, DeferredItem<GamblerOutfitItem>> pieces = new EnumMap<>(ArmorItem.Type.class);
            for (ArmorItem.Type type : TYPES) {
                Item.Properties props = new Item.Properties().durability(type.getDurability(tier.durabilityFactor)).rarity(tier.rarity);
                if (tier == GamblerOutfitTier.FORTUNE_LORD) props = props.fireResistant();
                Item.Properties p = props;
                pieces.put(type, ITEMS.register(tier.itemId(type), () -> new GamblerOutfitItem(tier, material, type, p)));
            }
            PIECES.put(tier, pieces);
        }
    }

    private static ArmorMaterial material(GamblerOutfitTier tier) {
        Map<ArmorItem.Type, Integer> defense = new EnumMap<>(ArmorItem.Type.class);
        for (ArmorItem.Type t : ArmorItem.Type.values()) defense.put(t, tier.defense(t));
        Holder<net.minecraft.sounds.SoundEvent> sound = switch (tier) {
            case DEALER, HIGH_ROLLER -> SoundEvents.ARMOR_EQUIP_LEATHER;
            case CASINO_KING -> SoundEvents.ARMOR_EQUIP_GOLD;
            case FORTUNE_LORD -> SoundEvents.ARMOR_EQUIP_NETHERITE;
        };
        return new ArmorMaterial(defense, 20, sound,
                tier == GamblerOutfitTier.DEALER ? () -> Ingredient.of(Items.LEATHER, Items.BLACK_WOOL)
                        : () -> Ingredient.of(CoreRegistries.MANA_CRYSTAL.get()),
                List.of(new ArmorMaterial.Layer(IsekaiMahou.id(tier.materialId()))), tier.toughness, 0F);
    }

    public static DeferredItem<GamblerOutfitItem> piece(GamblerOutfitTier tier, ArmorItem.Type type) {
        return PIECES.get(tier).get(type);
    }

    /** Todas as peças, grau a grau (aba criativa). */
    public static List<ItemStack> all() {
        List<ItemStack> out = new ArrayList<>();
        for (GamblerOutfitTier t : GamblerOutfitTier.values()) for (ArmorItem.Type type : TYPES) out.add(new ItemStack(piece(t, type).get()));
        return out;
    }

    /** Veste as 4 peças do traje do Apostador (qualquer grau)? */
    public static boolean fullSet(LivingEntity e) {
        for (ArmorItem.Type type : TYPES) {
            if (!(e.getItemBySlot(type.getSlot()).getItem() instanceof GamblerOutfitItem)) return false;
        }
        return true;
    }

    public static void register(IEventBus modBus) {
        MATERIALS.register(modBus);
        ITEMS.register(modBus);
        for (ArmorItem.Type type : TYPES) {
            EquipmentSlot slot = type.getSlot();
            ClassKits.setArmor(MagicPath.GAMBLER, slot, () -> new ItemStack(piece(GamblerOutfitTier.DEALER, type).get()));
        }
        NeoForge.EVENT_BUS.addListener(GamblerOutfits::onCircleChanged);
        NeoForge.EVENT_BUS.addListener(GamblerOutfits::onPathChosen);
        NeoForge.EVENT_BUS.addListener(GamblerOutfits::onLogin);
        NeoForge.EVENT_BUS.addListener(GamblerOutfits::onLogout);
        NeoForge.EVENT_BUS.addListener(GamblerOutfits::onPlayerTick);
    }

    // ------------------------------------------------------------------ entrega por Círculo
    private static void onCircleChanged(MagicProgressEvent.CircleChanged e) {
        if (e.newCircle > e.oldCircle) grantPending(e.getPlayer());
    }

    private static void onPathChosen(MagicProgressEvent.PathChosen e) {
        grantPending(e.getPlayer());
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer sp) LOGIN_DELAY.put(sp.getUUID(), 60);
    }

    private static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        LOGIN_DELAY.remove(e.getEntity().getUUID());
    }

    private static void onPlayerTick(PlayerTickEvent.Post e) {
        if (LOGIN_DELAY.isEmpty() || !(e.getEntity() instanceof ServerPlayer sp)) return;
        Integer left = LOGIN_DELAY.get(sp.getUUID());
        if (left == null) return;
        if (left > 0) {
            LOGIN_DELAY.put(sp.getUUID(), left - 1);
            return;
        }
        if (!sp.isAlive()) return;
        LOGIN_DELAY.remove(sp.getUUID());
        grantPending(sp);
    }

    /** Entrega o maior grau alcançado e ainda não recebido (só para o Apostador). */
    public static void grantPending(ServerPlayer player) {
        if (!player.isAlive()) return;
        MagicData d = MagicApi.get(player);
        if (d.path() != MagicPath.GAMBLER) return;
        GamblerOutfitTier best = null;
        for (GamblerOutfitTier t : GamblerOutfitTier.REWARDS) {
            if (d.circle() < t.circle || d.hasFlag(t.flag())) continue;
            d.setFlag(t.flag(), true);
            best = t;
        }
        if (best != null) deliver(player, best);
    }

    public static void deliver(ServerPlayer player, GamblerOutfitTier tier) {
        int equipped = 0;
        for (ArmorItem.Type type : TYPES) {
            ItemStack fresh = new ItemStack(piece(tier, type).get());
            EquipmentSlot slot = type.getSlot();
            ItemStack worn = player.getItemBySlot(slot);
            boolean replace = worn.isEmpty() || (worn.getItem() instanceof GamblerOutfitItem old && old.tier().ordinal() < tier.ordinal()
                    && !worn.isEnchanted());
            if (replace) {
                if (!worn.isEmpty()) Rewards.give(player, worn.copy());
                player.setItemSlot(slot, fresh);
                equipped++;
            } else {
                Rewards.give(player, fresh);
            }
        }
        ItemStack icon = new ItemStack(piece(tier, ArmorItem.Type.CHESTPLATE).get());
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.gambler.outfit", tier.displayName()),
                Component.translatable("toast.isekai_mahou.gambler.outfit.sub", tier.circle), icon, tier.color);
        player.sendSystemMessage(Component.translatable("message.isekai_mahou.gambler.outfit_granted", tier.circle,
                tier.displayName().withStyle(tier.chatColor()), tier.totalDefense(), (int) tier.totalFocus()).withStyle(ChatFormatting.GOLD));
        player.sendSystemMessage(Component.translatable(equipped == TYPES.length ? "message.isekai_mahou.gambler.outfit_equipped"
                : "message.isekai_mahou.gambler.outfit_inventory").withStyle(ChatFormatting.GRAY));
        ServerLevel level = player.serverLevel();
        double x = player.getX(), y = player.getY(), z = player.getZ();
        level.playSound(null, x, y, z, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 0.8F + 0.1F * tier.number);
        level.playSound(null, x, y, z, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.8F, 1.2F);
        int rgb = tier.color;
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F,
                (rgb & 0xFF) / 255F), 1.2F);
        for (int i = 0; i < 48; i++) {
            float a = i * 0.45F;
            double r = 0.9 - i * 0.008;
            level.sendParticles(dust, x + Mth.cos(a) * r, y + i * 0.045, z + Mth.sin(a) * r, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.ENCHANT, x, y + 1.2, z, 50, 0.4, 0.7, 0.4, 0.8);
    }

    private GamblerOutfits() {}
}
