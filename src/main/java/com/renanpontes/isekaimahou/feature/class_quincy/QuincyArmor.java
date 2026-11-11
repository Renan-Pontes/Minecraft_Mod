package com.renanpontes.isekaimahou.feature.class_quincy;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.class_quincy.item.QuincyArmorItem;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;

/**
 * Traje Quincy em 4 graus (o I vem no kit; II/III/IV são entregues ao chegar nos Círculos 3/5/8, como os trajes das
 * outras classes). Números da inflação v1.0: defesa ×1,5 e Foco ×3 em relação aos trajes leves da v0.9.
 * <p>Conjunto completo por grau: defesa 14/19/23/28 · resistência 0/2/6/12 · +30/75/135/225 Foco ·
 * regen +5/8/15/25% · velocidade +4/6/9/12% · Poder Mágico 0/3/7/13%.
 */
public final class QuincyArmor {
    private static final String FLAVOR_PREFIX = "tooltip.isekai_mahou.quincy_outfit_flavor.";
    public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
            ArmorItem.Type.BOOTS};

    public enum Tier {
        //           id           n  círc defesa {cab,peito,pernas,pés}   tough  durab raridade
        APPRENTICE("apprentice", 1, 1, new int[]{2, 6, 4, 2}, 0.0F, 13, Rarity.COMMON, ChatFormatting.WHITE, 0xCFE8FF,
                new double[]{5, 10, 10, 5}, new double[]{0, 0, 0, 0.05}, new double[]{0, 0, 0.02, 0.02}, new double[]{0, 0, 0, 0}),
        ADEPT("adept", 2, 3, new int[]{3, 7, 6, 3}, 0.5F, 20, Rarity.UNCOMMON, ChatFormatting.YELLOW, 0x9FD0FF,
                new double[]{15, 30, 15, 15}, new double[]{0.04, 0, 0, 0.04}, new double[]{0, 0, 0.03, 0.03}, new double[]{0, 0.03, 0, 0}),
        MASTER("master", 3, 5, new int[]{3, 9, 7, 4}, 1.5F, 28, Rarity.RARE, ChatFormatting.AQUA, 0xFFD24A,
                new double[]{30, 50, 30, 25}, new double[]{0.05, 0.05, 0, 0.05}, new double[]{0, 0.03, 0.03, 0.03}, new double[]{0.03, 0.04, 0, 0}),
        ARCHMAGE("archmage", 4, 8, new int[]{4, 11, 9, 4}, 3.0F, 37, Rarity.EPIC, ChatFormatting.LIGHT_PURPLE, 0xB8ECFF,
                new double[]{45, 90, 45, 45}, new double[]{0.10, 0.05, 0, 0.10}, new double[]{0, 0.04, 0.04, 0.04}, new double[]{0.05, 0.05, 0.03, 0});

        public final String id;
        public final int number;
        public final int circle;
        final int[] defense;
        public final float toughness;
        public final int durability;
        public final Rarity rarity;
        public final ChatFormatting chat;
        public final int color;
        public final double[] focus, regen, speed, power;

        Tier(String id, int number, int circle, int[] defense, float toughness, int durability, Rarity rarity, ChatFormatting chat,
             int color, double[] focus, double[] regen, double[] speed, double[] power) {
            this.id = id;
            this.number = number;
            this.circle = circle;
            this.defense = defense;
            this.toughness = toughness;
            this.durability = durability;
            this.rarity = rarity;
            this.chat = chat;
            this.color = color;
            this.focus = focus;
            this.regen = regen;
            this.speed = speed;
            this.power = power;
        }

        public int defense(ArmorItem.Type type) {
            return defense[pieceIndex(type)];
        }

        /** {@code quincy_<peça>} no grau I; {@code quincy_<grau>_<peça>} nos outros. */
        public String itemId(ArmorItem.Type type) {
            return this == APPRENTICE ? "quincy_" + type.getName() : "quincy_" + id + "_" + type.getName();
        }

        /** Modelo/textura do GeckoLib e camada vanilla de reserva. */
        public String modelName() {
            return this == APPRENTICE ? "quincy_outfit" : "quincy_outfit_" + id;
        }

        public String flag() {
            return "quincy.armor." + id;
        }

        /** Frase de ambientação da peça ({@code tooltip.isekai_mahou.quincy_outfit_flavor.<grau>}). */
        public String flavorKey() {
            return FLAVOR_PREFIX + id;
        }

        public MutableComponent displayName() {
            return Component.translatable(Quincy.TIER_KEY + id);
        }

        @Nullable
        public static Tier byId(String id) {
            for (Tier t : values()) if (t.id.equals(id)) return t;
            return null;
        }
    }

    static final Map<Tier, Map<ArmorItem.Type, DeferredItem<QuincyArmorItem>>> PIECES = new EnumMap<>(Tier.class);

    public static DeferredItem<QuincyArmorItem> piece(Tier tier, ArmorItem.Type type) {
        return PIECES.get(tier).get(type);
    }

    public static int pieceIndex(ArmorItem.Type type) {
        return switch (type) {
            case HELMET -> 0;
            case CHESTPLATE, BODY -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    public static int totalDefense(Tier t) {
        int s = 0;
        for (int d : t.defense) s += d;
        return s;
    }

    public static double sum(double[] v) {
        double s = 0;
        for (double d : v) s += d;
        return s;
    }

    public static String fmt(double v) {
        double r = Math.round(v * 10.0) / 10.0;
        return r == Math.rint(r) ? String.valueOf((long) r) : String.format(java.util.Locale.ROOT, "%.1f", r);
    }

    // =====================================================================================
    //  Entrega dos graus II–IV (Círculos 3/5/8)
    // =====================================================================================
    private static final Map<UUID, Integer> LOGIN_DELAY = new HashMap<>();

    static void registerEvents() {
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.CircleChanged e) -> {
            if (e.newCircle > e.oldCircle) grantPending(e.getPlayer());
        });
        NeoForge.EVENT_BUS.addListener((MagicProgressEvent.PathChosen e) -> grantPending(e.getPlayer()));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp) LOGIN_DELAY.put(sp.getUUID(), 60);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerRespawnEvent e) -> {
            if (e.getEntity() instanceof ServerPlayer sp && !e.isEndConquered()) LOGIN_DELAY.put(sp.getUUID(), 20);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e) -> LOGIN_DELAY.remove(e.getEntity().getUUID()));
        NeoForge.EVENT_BUS.addListener(QuincyArmor::onPlayerTick);
    }

    static void clear() {
        LOGIN_DELAY.clear();
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

    /** Entrega o maior grau já alcançado e ainda não recebido (só Quincy). */
    public static void grantPending(ServerPlayer player) {
        if (!player.isAlive()) return;
        MagicData d = MagicApi.get(player);
        if (d.path() != MagicPath.QUINCY) return;
        Tier best = null;
        for (Tier t : Tier.values()) {
            if (t == Tier.APPRENTICE || d.circle() < t.circle || d.hasFlag(t.flag())) continue;
            d.setFlag(t.flag(), true);
            best = t;
        }
        if (best != null) deliver(player, best);
    }

    public static void deliver(ServerPlayer player, Tier tier) {
        int equipped = 0;
        for (ArmorItem.Type type : TYPES) {
            ItemStack fresh = new ItemStack(piece(tier, type).get());
            EquipmentSlot slot = type.getSlot();
            ItemStack worn = player.getItemBySlot(slot);
            boolean replace = worn.isEmpty() || (worn.getItem() instanceof QuincyArmorItem old && old.tier().ordinal() < tier.ordinal()
                    && !worn.isEnchanted());
            if (replace) {
                if (!worn.isEmpty()) Rewards.give(player, worn.copy());
                player.setItemSlot(slot, fresh);
                equipped++;
            } else {
                Rewards.give(player, fresh);
            }
        }
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.quincy.outfit", tier.displayName()),
                Component.translatable("toast.isekai_mahou.quincy.outfit_sub", tier.circle),
                new ItemStack(piece(tier, ArmorItem.Type.CHESTPLATE).get()), tier.color);
        player.sendSystemMessage(Quincy.msg("outfit_granted", tier.circle, tier.displayName().withStyle(tier.chat)).withStyle(ChatFormatting.AQUA));
        player.sendSystemMessage(Quincy.msg(equipped == TYPES.length ? "outfit_equipped" : "outfit_inventory").withStyle(ChatFormatting.GRAY));
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position();
        QuincyFx.sound(level, at, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.9F + 0.1F * tier.number);
        QuincyFx.pentacle(level, at.add(0, 0.1, 0), 1.6, 0F);
        QuincyFx.pillar(level, at, 2.4, 40);
        level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 1.2, at.z, 40, 0.4, 0.7, 0.4, 0.8);
    }

    static ArmorItem.Type[] types() {
        return TYPES;
    }

    static String layerId(Tier t) {
        return IsekaiMahou.id(t.modelName()).toString();
    }

    private QuincyArmor() {}
}
