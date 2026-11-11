package com.renanpontes.isekaimahou.feature.class_shinigami;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.event.MagicProgressEvent;
import com.renanpontes.isekaimahou.feature.class_shinigami.item.ShinigamiArmorItem;

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
 * Traje Shinigami em 4 graus (o I vem no kit; II/III/IV chegam nos Círculos 3/5/8, como os trajes das outras classes):
 * I Shihakushō (quimono negro e faixa branca) · II Tenente (braçadeira e manto curto) · III Capitão (haori branco com o
 * emblema do esquadrão) · IV Manto da Bankai (casaco longo rasgado com forro rubro e Reiatsu que brilha).
 * <p>Números da inflação v1.0 (defesa ×1,5; Vigor ×3). Conjunto completo por grau: defesa 14/19/24/29 · resistência
 * 0/2/6/11 · +36/84/144/234 Vigor · regen +4/10/16/26% · velocidade +3/6/9/12% · dano corpo a corpo 0/3/6/10%.
 */
public final class ShinigamiArmor {
    private static final String FLAVOR_PREFIX = "tooltip.isekai_mahou.shinigami_outfit_flavor.";
    public static final ArmorItem.Type[] TYPES = {ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
            ArmorItem.Type.BOOTS};

    public enum Tier {
        //            id         n círc  defesa {cab,peito,pernas,pés}  tough durab raridade
        APPRENTICE("apprentice", 1, 1, new int[]{2, 6, 4, 2}, 0.0F, 14, Rarity.COMMON, ChatFormatting.WHITE, 0xC8C8D0,
                new double[]{6, 12, 12, 6}, new double[]{0, 0.02, 0, 0.02}, new double[]{0, 0, 0.01, 0.02}, new double[]{0, 0, 0, 0}),
        ADEPT("adept", 2, 3, new int[]{3, 7, 6, 3}, 0.5F, 22, Rarity.UNCOMMON, ChatFormatting.YELLOW, 0xD9483B,
                new double[]{18, 30, 24, 12}, new double[]{0.02, 0.04, 0.02, 0.02}, new double[]{0, 0.02, 0.02, 0.02}, new double[]{0, 0.02, 0.01, 0}),
        MASTER("master", 3, 5, new int[]{4, 9, 7, 4}, 1.5F, 30, Rarity.RARE, ChatFormatting.AQUA, 0xF2F2F2,
                new double[]{30, 54, 36, 24}, new double[]{0.04, 0.06, 0.03, 0.03}, new double[]{0, 0.03, 0.03, 0.03}, new double[]{0.01, 0.03, 0.02, 0}),
        ARCHMAGE("archmage", 4, 8, new int[]{4, 11, 9, 5}, 2.75F, 38, Rarity.EPIC, ChatFormatting.LIGHT_PURPLE, 0xFF5A40,
                new double[]{48, 90, 60, 36}, new double[]{0.06, 0.10, 0.05, 0.05}, new double[]{0, 0.04, 0.04, 0.04}, new double[]{0.02, 0.05, 0.03, 0});

        public final String id;
        public final int number;
        public final int circle;
        final int[] defense;
        public final float toughness;
        public final int durability;
        public final Rarity rarity;
        public final ChatFormatting chat;
        public final int color;
        public final double[] vigor, regen, speed, melee;

        Tier(String id, int number, int circle, int[] defense, float toughness, int durability, Rarity rarity, ChatFormatting chat,
             int color, double[] vigor, double[] regen, double[] speed, double[] melee) {
            this.id = id;
            this.number = number;
            this.circle = circle;
            this.defense = defense;
            this.toughness = toughness;
            this.durability = durability;
            this.rarity = rarity;
            this.chat = chat;
            this.color = color;
            this.vigor = vigor;
            this.regen = regen;
            this.speed = speed;
            this.melee = melee;
        }

        public int defense(ArmorItem.Type type) {
            return defense[pieceIndex(type)];
        }

        /** {@code shinigami_<peça>} no grau I; {@code shinigami_<grau>_<peça>} nos outros. */
        public String itemId(ArmorItem.Type type) {
            return this == APPRENTICE ? "shinigami_" + type.getName() : "shinigami_" + id + "_" + type.getName();
        }

        /** Modelo/textura do GeckoLib e camada vanilla de reserva. */
        public String modelName() {
            return this == APPRENTICE ? "shinigami_outfit" : "shinigami_outfit_" + id;
        }

        public String flag() {
            return "shinigami.armor." + id;
        }

        public String flavorKey() {
            return FLAVOR_PREFIX + id;
        }

        public MutableComponent displayName() {
            return Component.translatable(Shinigami.TIER_KEY + id);
        }

        @Nullable
        public static Tier byId(String id) {
            for (Tier t : values()) if (t.id.equals(id)) return t;
            return null;
        }
    }

    static final Map<Tier, Map<ArmorItem.Type, DeferredItem<ShinigamiArmorItem>>> PIECES = new EnumMap<>(Tier.class);

    public static DeferredItem<ShinigamiArmorItem> piece(Tier tier, ArmorItem.Type type) {
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
        NeoForge.EVENT_BUS.addListener(ShinigamiArmor::onPlayerTick);
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

    /** Entrega o maior grau já alcançado e ainda não recebido (só Shinigami). */
    public static void grantPending(ServerPlayer player) {
        if (!player.isAlive()) return;
        MagicData d = MagicApi.get(player);
        if (d.path() != MagicPath.SHINIGAMI) return;
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
            boolean replace = worn.isEmpty() || (worn.getItem() instanceof ShinigamiArmorItem old && old.tier().ordinal() < tier.ordinal()
                    && !worn.isEnchanted());
            if (replace) {
                if (!worn.isEmpty()) Rewards.give(player, worn.copy());
                player.setItemSlot(slot, fresh);
                equipped++;
            } else {
                Rewards.give(player, fresh);
            }
        }
        Rewards.toast(player, Component.translatable("toast.isekai_mahou.shinigami.outfit", tier.displayName()),
                Component.translatable("toast.isekai_mahou.shinigami.outfit_sub", tier.circle),
                new ItemStack(piece(tier, ArmorItem.Type.CHESTPLATE).get()), tier.color);
        player.sendSystemMessage(Shinigami.msg("outfit_granted", tier.circle, tier.displayName().withStyle(tier.chat)).withColor(Shinigami.RGB));
        player.sendSystemMessage(Shinigami.msg(equipped == TYPES.length ? "outfit_equipped" : "outfit_inventory").withStyle(ChatFormatting.GRAY));
        ServerLevel level = player.serverLevel();
        Vec3 at = player.position();
        ShinigamiFx.sound(level, at, SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 1.0F, 0.8F + 0.1F * tier.number);
        ShinigamiFx.ring(level, at.add(0, 0.1, 0), 1.4, 24, 0F, ShinigamiFx.REIATSU);
        ShinigamiFx.pillar(level, at, 2.6, 40, ShinigamiFx.REIATSU);
        level.sendParticles(ParticleTypes.ENCHANT, at.x, at.y + 1.2, at.z, 30, 0.4, 0.7, 0.4, 0.8);
    }

    private ShinigamiArmor() {}
}
