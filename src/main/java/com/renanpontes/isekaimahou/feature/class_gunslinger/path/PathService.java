package com.renanpontes.isekaimahou.feature.class_gunslinger.path;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFx;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsRegistries;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.GsOutfitTier;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.GunslingerArmorItem;
import com.renanpontes.isekaimahou.feature.class_gunslinger.item.ManaGunItem;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Servidor do Caminho do Gatilho: concluir ranks, materializar armas de mana, magias/glifos iniciais da classe e a
 * entrega dos graus do traje (Círculos 3 / 5 / 8, como os trajes das outras classes).
 */
public final class PathService {
    /** Materializar arma: fração do Foco máximo e espera entre materializações. */
    public static final float MATERIALIZE_COST = 0.25F;
    public static final int MATERIALIZE_COOLDOWN = 600;
    private static final Map<UUID, Long> LAST_MATERIALIZE = new HashMap<>();
    private static final Map<UUID, Integer> LOGIN_DELAY = new HashMap<>();

    // =====================================================================================
    //  Ranks
    // =====================================================================================
    public static void claim(ServerPlayer sp, int n) {
        MagicData d = MagicApi.get(sp);
        List<Component> missing = new ArrayList<>();
        if (!TriggerPath.meets(d, n, missing)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.not_ready").withStyle(ChatFormatting.RED), false);
            for (Component c : missing) sp.displayClientMessage(Component.literal(" • ").append(c).withStyle(ChatFormatting.GRAY), false);
            return;
        }
        grant(sp, n);
    }

    /** Concede o rank (sem conferir requisitos: comando de teste e claim já validado). */
    public static void grant(ServerPlayer sp, int n) {
        TriggerPath.Rank r = TriggerPath.rank(n);
        if (r == null) return;
        MagicData d = MagicApi.get(sp);
        if (d.hasFlag(TriggerPath.flag(n))) return;
        d.setFlag(TriggerPath.flag(n), true);
        d.markDirty();
        MagicApi.addCounter(sp, TriggerPath.RITUAL_RANK, 1);
        MagicApi.addCounter(sp, TriggerPath.RITUAL_RANK_OWN, 1);
        for (String g : r.glyphs()) MagicApi.learnGlyph(sp, IsekaiMahou.id(g));
        MagicApi.giveXp(sp, r.xp(), "gunslinger_rank");
        if (r.weapon() != null) {
            Rewards.give(sp, gunStack(r.weapon()));
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gunslinger.weapon_unlocked", r.weapon().displayName())
                    .withStyle(ChatFormatting.YELLOW));
        }
        if (r.legend() != null) {
            ItemStack scroll = Legends.scroll(r.legend());
            if (!scroll.isEmpty()) Rewards.give(sp, scroll);
        }
        Component title = TriggerPath.title(n);
        Rewards.banner(sp, Component.translatable("message.isekai_mahou.gunslinger.rank_up", n, title), GsFx.GOLD);
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.gunslinger.rank", n),
                title.copy(), new ItemStack(r.weapon() != null ? GsRegistries.GUNS.get(r.weapon()).get() : GsRegistries.MANA_REVOLVER.get()),
                GsFx.GOLD);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gunslinger.rank_chat", n, title).withStyle(ChatFormatting.GOLD));
        celebrate(sp, n);
        com.renanpontes.isekaimahou.feature.class_gunslinger.GsTechniques.check(sp);
    }

    private static void celebrate(ServerPlayer sp, int n) {
        ServerLevel level = sp.serverLevel();
        GsFx.sound(level, sp.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        GsFx.sound(level, sp.position(), SoundEvents.BELL_BLOCK, 1.0F, 0.6F + 0.1F * n);
        for (int i = 0; i < 6; i++) {
            float pitch = 1.2F + 0.1F * i;
            GsFx.sound(level, sp.position(), SoundEvents.FIREWORK_ROCKET_BLAST, 0.5F, pitch);
        }
        for (int i = 0; i < 40; i++) {
            float a = i * 0.5F;
            double r = 1.0 - i * 0.012;
            level.sendParticles(GsFx.dust(0xFFD86B, 1.2F), sp.getX() + Mth.cos(a) * r, sp.getY() + i * 0.05, sp.getZ() + Mth.sin(a) * r,
                    1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.FIREWORK, sp.getX(), sp.getY() + 1.4, sp.getZ(), 30, 0.4, 0.6, 0.4, 0.08);
    }

    /** Avisa (uma vez) quando o próximo rank pode ser concluído. */
    public static void checkReady(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.GUNSLINGER) return;
        int next = TriggerPath.rankOf(d) + 1;
        if (next > TriggerPath.MAX_RANK) return;
        String flag = "gunslinger.ready." + next;
        if (d.hasFlag(flag) || !TriggerPath.meets(d, next, null)) return;
        d.setFlag(flag, true);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gunslinger.ready", next, TriggerPath.title(next))
                .withStyle(ChatFormatting.GOLD));
        GsFx.sound(sp.serverLevel(), sp.position(), SoundEvents.NOTE_BLOCK_BELL.value(), 1.0F, 1.2F);
    }

    // =====================================================================================
    //  Armas de mana
    // =====================================================================================
    public static ItemStack gunStack(GunKind kind) {
        return new ItemStack(GsRegistries.GUNS.get(kind).get());
    }

    /** Cria a arma com mana (aba do menu B). Só o Pistoleiro; arma liberada; 25% do Foco; 30 s de espera. */
    public static void materialize(ServerPlayer sp, @Nullable GunKind kind) {
        if (kind == null) return;
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.GUNSLINGER) {
            sp.displayClientMessage(Component.translatable("path.isekai_mahou.gunslinger.need_class").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!TriggerPath.unlocked(d, kind)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.locked", kind.unlockRank)
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (hasKind(sp, kind)) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.already_have", kind.displayName())
                    .withStyle(ChatFormatting.GRAY), true);
            return;
        }
        long now = sp.serverLevel().getGameTime();
        Long last = LAST_MATERIALIZE.get(sp.getUUID());
        boolean creative = sp.getAbilities().instabuild;
        if (!creative && last != null && now - last < MATERIALIZE_COOLDOWN) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.materialize_wait",
                    (MATERIALIZE_COOLDOWN - (now - last)) / 20 + 1).withStyle(ChatFormatting.RED), true);
            return;
        }
        float cost = d.maxMana() * MATERIALIZE_COST;
        if (!creative && d.mana() < cost) {
            sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.no_focus", MagicApi.resourceName(sp))
                    .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!creative) d.consumeMana(cost);
        LAST_MATERIALIZE.put(sp.getUUID(), now);
        Rewards.give(sp, gunStack(kind));
        ServerLevel level = sp.serverLevel();
        var hand = sp.getEyePosition().add(sp.getLookAngle().scale(0.7)).add(0, -0.3, 0);
        GsFx.ring(level, sp.position().add(0, 0.1, 0), 1.2, 24, GsFx.dust(0xFFD86B, 1.0F));
        level.sendParticles(ParticleTypes.ENCHANT, hand.x, hand.y, hand.z, 30, 0.3, 0.3, 0.3, 0.6);
        level.sendParticles(ParticleTypes.END_ROD, hand.x, hand.y, hand.z, 8, 0.15, 0.15, 0.15, 0.03);
        GsFx.sound(level, hand, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 1.2F);
        GsFx.sound(level, hand, SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 1.4F);
        sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.materialized", kind.displayName())
                .withStyle(ChatFormatting.GOLD), true);
    }

    private static boolean hasKind(ServerPlayer sp, GunKind kind) {
        for (ItemStack s : sp.getInventory().items) {
            ManaGunItem g = ManaGunItem.of(s);
            if (g != null && g.kind() == kind) return true;
        }
        ManaGunItem off = ManaGunItem.of(sp.getOffhandItem());
        return off != null && off.kind() == kind;
    }

    // =====================================================================================
    //  Início da classe
    // =====================================================================================

    /** Escolheu Pistoleiro (ou entrou já sendo): glifos e magias iniciais (uma vez). */
    public static void ensureStarter(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.GUNSLINGER) return;
        for (String g : TriggerPath.STARTER_GLYPHS) MagicApi.learnGlyphSilently(sp, IsekaiMahou.id(g));
        if (d.hasFlag("gunslinger.starter")) return;
        d.setFlag("gunslinger.starter", true);
        boolean pt = sp.clientInformation().language().toLowerCase(Locale.ROOT).startsWith("pt");
        List<SpellRecipe> spells = starterSpells(pt);
        // Troca só o que ainda é a magia inicial genérica (Flecha) — nunca apaga magia montada pelo jogador
        for (int i = 0; i < spells.size(); i++) {
            SpellRecipe cur = d.peekSlot(0, i).main();
            if (cur == null || cur.isEmpty() || isCoreStarter(cur)) d.setSlotRecipe(0, i, MoveTrigger.NORMAL, spells.get(i));
        }
        d.markDirty();
    }

    /** A receita é uma das magias prontas da Destreza que o núcleo grava (Flecha Arcana / Flecha Repulsora)? */
    private static boolean isCoreStarter(SpellRecipe r) {
        for (SpellRecipe core : com.renanpontes.isekaimahou.core.CoreGameplay.starterSpells(MagicPath.Category.DEXTERITY)) {
            if (core.glyphs().equals(r.glyphs())) return true;
        }
        return false;
    }

    /** Magias iniciais nos slots Z/X/C: Tiro de Mana, Tiro Marcado e Tiro Repulsor. */
    public static List<SpellRecipe> starterSpells(boolean pt) {
        return List.of(
                recipe(pt ? "Tiro de Mana" : "Mana Shot", 0xD9A441, "gun_bullet", "arcane_damage"),
                recipe(pt ? "Tiro Marcado" : "Marking Shot", 0xFFD86B, "gun_bullet", "gun_mark", "arcane_damage"),
                recipe(pt ? "Tiro Repulsor" : "Repelling Shot", 0xB98CFF, "gun_bullet", "push"));
    }

    private static SpellRecipe recipe(String name, int color, String... glyphs) {
        List<ResourceLocation> l = new ArrayList<>();
        for (String g : glyphs) {
            Glyph glyph = GlyphRegistry.get(g);
            if (glyph != null) l.add(glyph.id());
        }
        return new SpellRecipe(l, name, color, "");
    }

    // =====================================================================================
    //  Traje (Círculos 3 / 5 / 8)
    // =====================================================================================
    public static void scheduleLoginCheck(ServerPlayer sp) {
        LOGIN_DELAY.put(sp.getUUID(), 60);
    }

    /** Todo tick do jogador: conferência atrasada depois de entrar (o cliente precisa estar pronto para o toast). */
    public static void tick(ServerPlayer sp) {
        Integer left = LOGIN_DELAY.get(sp.getUUID());
        if (left == null) return;
        if (left > 0) {
            LOGIN_DELAY.put(sp.getUUID(), left - 1);
            return;
        }
        if (!sp.isAlive()) return;
        LOGIN_DELAY.remove(sp.getUUID());
        ensureStarter(sp);
        grantOutfit(sp);
        checkReady(sp);
        com.renanpontes.isekaimahou.feature.class_gunslinger.GsTechniques.check(sp);
    }

    /** Entrega o maior grau do traje já alcançado e ainda não recebido. */
    public static void grantOutfit(ServerPlayer sp) {
        if (!sp.isAlive()) return;
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.GUNSLINGER) return;
        GsOutfitTier best = null;
        for (GsOutfitTier t : GsOutfitTier.REWARDS) {
            if (d.circle() < t.circle || d.hasFlag(t.flag())) continue;
            d.setFlag(t.flag(), true);
            best = t;
        }
        if (best != null) deliverOutfit(sp, best);
    }

    public static void deliverOutfit(ServerPlayer sp, GsOutfitTier tier) {
        int equipped = 0;
        for (ArmorItem.Type type : GsOutfitTier.TYPES) {
            ItemStack fresh = new ItemStack(GsRegistries.piece(tier, type).get());
            EquipmentSlot slot = type.getSlot();
            ItemStack worn = sp.getItemBySlot(slot);
            boolean replace = worn.isEmpty() || (worn.getItem() instanceof GunslingerArmorItem old && old.tier().ordinal() < tier.ordinal()
                    && !worn.isEnchanted());
            if (replace) {
                if (!worn.isEmpty()) Rewards.give(sp, worn.copy());
                sp.setItemSlot(slot, fresh);
                equipped++;
            } else {
                Rewards.give(sp, fresh);
            }
        }
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.gunslinger.outfit", tier.displayName()),
                Component.translatable("toast.isekai_mahou.gunslinger.outfit.sub", tier.circle),
                new ItemStack(GsRegistries.piece(tier, ArmorItem.Type.CHESTPLATE).get()), tier.color);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.gunslinger.outfit", tier.circle,
                tier.displayName().withStyle(tier.chatColor())).withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(Component.translatable(equipped == GsOutfitTier.TYPES.length ? "message.isekai_mahou.gunslinger.outfit.equipped"
                : "message.isekai_mahou.gunslinger.outfit.inventory").withStyle(ChatFormatting.GRAY));
        GsFx.sound(sp.serverLevel(), sp.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F + 0.1F * tier.number);
        GsFx.sound(sp.serverLevel(), sp.position(), SoundEvents.ARMOR_EQUIP_LEATHER.value(), 1.0F, 1.0F);
        sp.serverLevel().sendParticles(ParticleTypes.ENCHANT, sp.getX(), sp.getY() + 1.2, sp.getZ(), 50, 0.4, 0.7, 0.4, 0.8);
    }

    public static void forget(UUID id) {
        LAST_MATERIALIZE.remove(id);
        LOGIN_DELAY.remove(id);
    }

    public static void clear() {
        LAST_MATERIALIZE.clear();
        LOGIN_DELAY.clear();
    }

    private PathService() {}
}
