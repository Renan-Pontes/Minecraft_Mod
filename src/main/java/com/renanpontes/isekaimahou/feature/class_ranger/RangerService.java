package com.renanpontes.isekaimahou.feature.class_ranger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.joml.Vector3f;

import com.renanpontes.isekaimahou.core.CoreGameplay;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_ranger.glyph.RangerGlyphs;
import com.renanpontes.isekaimahou.feature.class_ranger.item.RangerOutfitTier;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

/**
 * Servidor da classe Ranger: ao escolher a classe (glifos iniciais + magias prontas), a Trilha do Ranger (concluir
 * rank: validação, festa e recompensas) e a entrega do Traje Selvagem nos Círculos 3/5/8.
 */
public final class RangerService {
    /** Flags que o módulo outfits usa para entregar o traje ANTIGO (v0.9) do Ranger: marcadas para ele não entregar. */
    private static final String[] OLD_OUTFIT_FLAGS = {"gear.armor.adept", "gear.armor.master", "gear.armor.archmage"};
    private static final String STARTERS_FLAG = "ranger.starters";

    // =====================================================================================
    //  Escolha da classe / entrada no mundo
    // =====================================================================================

    /** Escolheu Ranger: glifos de origem iniciais, magias prontas e companheiros no nível certo. */
    public static void onClassChosen(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.RANGER) return;
        syncGlyphs(sp);
        giveStarters(sp, d);
        claimOutfitFlags(d);
        grantOutfits(sp);
    }

    /** Entrada no mundo / renascer: garante os glifos já conquistados e o traje do Círculo (saves antigos). */
    public static void onLogin(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.RANGER) return;
        syncGlyphs(sp);
        claimOutfitFlags(d);
        RangerCompanions.refreshLevel(sp);
    }

    /** Ensina, em silêncio, os glifos iniciais e os dos ranks já concluídos (se faltarem). */
    public static void syncGlyphs(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        for (String g : RangerProgress.START_GLYPHS) learnQuiet(sp, g);
        int rank = RangerProgress.rank(d);
        for (int n = 1; n <= rank; n++) {
            RangerProgress.Rank r = RangerProgress.rankDef(n);
            if (r != null) for (String g : r.glyphs()) learnQuiet(sp, g);
        }
    }

    private static void learnQuiet(ServerPlayer sp, String id) {
        Glyph g = GlyphRegistry.get(id);
        if (g != null && !MagicApi.get(sp).knowsGlyph(g.id())) MagicApi.learnGlyphSilently(sp, g.id());
    }

    /**
     * Magias prontas do Ranger (Flecha Explosiva, Marca do Caçador, Chamado da Matilha). Se o loadout 1 ainda tem só as
     * magias prontas da Destreza do núcleo, elas vão para o loadout 2 e o 1 fica com as do Ranger; senão as do Ranger vão
     * para o primeiro loadout vazio. Uma vez por jogador.
     */
    private static void giveStarters(ServerPlayer sp, MagicData d) {
        if (d.hasFlag(STARTERS_FLAG)) return;
        List<SpellRecipe> spells = RangerGlyphs.starters(sp.clientInformation().language().toLowerCase(Locale.ROOT));
        int target = -1;
        if (coreStartersOnly(d)) {
            if (d.loadoutEmpty(1)) {
                for (int s = 0; s < 2; s++) {
                    SpellRecipe r = d.peekSlot(0, s).main();
                    if (r != null) d.setSlotRecipe(1, s, MoveTrigger.NORMAL, r);
                }
            }
            target = 0;
        } else {
            for (int l = 0; l < d.loadoutCount(); l++) {
                if (d.loadoutEmpty(l)) {
                    target = l;
                    break;
                }
            }
        }
        d.setFlag(STARTERS_FLAG, true);
        if (target < 0) return;
        for (int s = 0; s < spells.size(); s++) d.setSlotRecipe(target, s, MoveTrigger.NORMAL, spells.get(s));
        if (target == 0) d.setActiveLoadout(0);
        d.markDirty();
    }

    /** O loadout 1 tem exatamente as magias prontas da Destreza (Z/X) e nada mais? */
    private static boolean coreStartersOnly(MagicData d) {
        SpellSlot z = d.peekSlot(0, 0), x = d.peekSlot(0, 1), c = d.peekSlot(0, 2), g = d.peekSlot(0, 3);
        return same(z.main(), CoreGameplay.STARTER_ARROW) && same(x.main(), CoreGameplay.STARTER_PUSH_ARROW)
                && c.isEmpty() && g.isEmpty() && z.all().size() == 1 && x.all().size() == 1;
    }

    private static boolean same(SpellRecipe a, SpellRecipe b) {
        return a != null && b != null && a.glyphs().equals(b.glyphs());
    }

    // =====================================================================================
    //  Trilha do Ranger
    // =====================================================================================

    /** Pedido da aba: concluir o rank {@code n}. */
    public static void claim(ServerPlayer sp, int n) {
        MagicData d = MagicApi.get(sp);
        List<Component> missing = new ArrayList<>();
        if (!RangerProgress.meets(d, n, missing)) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.class_ranger.trail_missing").withStyle(ChatFormatting.GOLD));
            for (Component c : missing) sp.sendSystemMessage(Component.literal(" • ").append(c).withStyle(ChatFormatting.GRAY));
            return;
        }
        grant(sp, n);
    }

    /** Conclui o rank (também usado pelo comando de teste): ritual, glifos, arco, lendas, XP e festa. */
    public static void grant(ServerPlayer sp, int n) {
        MagicData d = MagicApi.get(sp);
        RangerProgress.Rank r = RangerProgress.rankDef(n);
        if (r == null || d.hasFlag(RangerProgress.flag(n))) return;
        d.setFlag(RangerProgress.flag(n), true);
        d.markDirty();
        MagicApi.addCounter(sp, "ritual_rank", 1);
        MagicApi.addCounter(sp, "ritual_rank.ranger", 1);
        MagicApi.giveXp(sp, r.xp(), "class_ritual");

        List<Component> lines = new ArrayList<>();
        for (String id : r.glyphs()) {
            Glyph g = GlyphRegistry.get(id);
            if (g != null && MagicApi.learnGlyph(sp, g.id())) lines.add(g.displayName());
        }
        if (r.bowTier() > 0) {
            ItemStack bow = new ItemStack(RangerRegistries.BOW_TIERS.get(r.bowTier() - 1).get());
            lines.add(bow.getHoverName());
            Rewards.give(sp, bow);
            Rewards.give(sp, new ItemStack(net.minecraft.world.item.Items.ARROW, 64));
        }
        Rewards.give(sp, new ItemStack(CoreRegistries.ESSENCES.get(Element.WIND).get(), n));
        Rewards.give(sp, new ItemStack(CoreRegistries.ESSENCES.get(Element.EARTH).get(), n));
        if (n == 1) lines.add(RangerRegistries.EAGLE.get().getDescription());
        if (n == 2) lines.add(RangerRegistries.STEED.get().getDescription());
        if (n == 3) lines.add(Component.translatable("trail.isekai_mahou.class_ranger.reward_alpha"));
        if (r.legends()) {
            for (String id : List.of(RangerGlyphIds.LEGEND_LAMENT, RangerGlyphIds.LEGEND_HECATE, RangerGlyphIds.LEGEND_BULLET_LINE,
                    RangerGlyphIds.LEGEND_SACRED, RangerGlyphIds.LEGEND_WOLF_GOD)) {
                ItemStack scroll = Legends.scroll(id);
                if (scroll.isEmpty()) continue;
                Rewards.give(sp, scroll);
                lines.add(scroll.getHoverName());
            }
        }
        RangerCompanions.refreshLevel(sp);

        MutableComponent title = RangerProgress.title(n);
        Component sub = lines.isEmpty() ? MagicPath.RANGER.displayName() : joined(lines);
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.class_ranger.rank_up", title), sub,
                new ItemStack(RangerRegistries.BOW.get()), MagicPath.RANGER.rgb());
        Rewards.banner(sp, Component.translatable("banner.isekai_mahou.class_ranger.rank_up", title), MagicPath.RANGER.rgb());
        sp.sendSystemMessage(Component.literal("✦ ").withColor(MagicPath.RANGER.rgb())
                .append(Component.translatable("message.isekai_mahou.class_ranger.rank_up", title, r.xp()).withColor(MagicPath.RANGER.rgb())));
        celebrate(sp, n);
    }

    /** Ritual do Chamado: uivo, grito da águia, espiral de folhas e penas subindo. */
    private static void celebrate(ServerPlayer sp, int n) {
        ServerLevel level = sp.serverLevel();
        RangerFx.sound(level, sp.position(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 0.9F, 1.0F);
        RangerFx.sound(level, sp.position(), SoundEvents.WOLF_HOWL, 1.0F, 0.9F);
        RangerFx.sound(level, sp.position(), SoundEvents.PARROT_IMITATE_PHANTOM, 0.9F, 1.5F);
        int rgb = MagicPath.RANGER.rgb();
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F,
                (rgb & 0xFF) / 255F), 1.3F);
        double x = sp.getX(), y = sp.getY(), z = sp.getZ();
        for (int i = 0; i < 48; i++) {
            float a = i * 0.45F;
            double rr = 1.1 - i * 0.01;
            level.sendParticles(dust, x + Mth.cos(a) * rr, y + i * 0.05, z + Mth.sin(a) * rr, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.CHERRY_LEAVES, x, y + 1.5, z, 30 + 6 * n, 0.9, 0.9, 0.9, 0.02);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER, x, y + 1.0, z, 20, 0.7, 0.8, 0.7, 0);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, x, y + 1.0, z, 30, 0.5, 0.8, 0.5, 0.3);
    }

    private static Component joined(List<Component> lines) {
        MutableComponent c = Component.empty();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) c.append(", ");
            c.append(lines.get(i));
        }
        return c;
    }

    // =====================================================================================
    //  Traje Selvagem (Círculos 3 / 5 / 8)
    // =====================================================================================

    /** Marca as flags do traje antigo (outfits) para o módulo de roupas não entregar o traje da v0.9 ao Ranger. */
    public static void claimOutfitFlags(MagicData d) {
        if (d.path() != MagicPath.RANGER) return;
        boolean changed = false;
        for (String f : OLD_OUTFIT_FLAGS) {
            if (!d.hasFlag(f)) {
                d.setFlag(f, true);
                changed = true;
            }
        }
        if (changed) d.markDirty();
    }

    /** Entrega o maior grau já alcançado e ainda não recebido (uma vez por grau). */
    public static void grantOutfits(ServerPlayer sp) {
        if (!sp.isAlive()) return;
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.RANGER) return;
        RangerOutfitTier best = null;
        for (RangerOutfitTier t : RangerOutfitTier.REWARDS) {
            if (d.circle() < t.circle || d.hasFlag(t.flag())) continue;
            d.setFlag(t.flag(), true);
            best = t;
        }
        if (best == null) return;
        d.markDirty();
        deliver(sp, best);
    }

    /** Veste o traje (espaços vazios ou com traje do Ranger de grau menor sem encantamentos) e avisa. */
    public static void deliver(ServerPlayer sp, RangerOutfitTier tier) {
        int equipped = 0;
        for (ArmorItem.Type type : RangerOutfitTier.TYPES) {
            ItemStack fresh = new ItemStack(RangerRegistries.piece(tier, type).get());
            EquipmentSlot slot = type.getSlot();
            ItemStack worn = sp.getItemBySlot(slot);
            boolean replace = worn.isEmpty() || (worn.getItem() instanceof com.renanpontes.isekaimahou.feature.class_ranger.item.RangerArmorItem old
                    && old.tier().ordinal() < tier.ordinal() && !worn.isEnchanted());
            if (replace) {
                if (!worn.isEmpty()) Rewards.give(sp, worn.copy());
                sp.setItemSlot(slot, fresh);
                equipped++;
            } else {
                Rewards.give(sp, fresh);
            }
        }
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.class_ranger.garb", tier.displayName()),
                Component.translatable("toast.isekai_mahou.class_ranger.garb_sub", tier.circle),
                new ItemStack(RangerRegistries.piece(tier, ArmorItem.Type.CHESTPLATE).get()), tier.color);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.class_ranger.garb", tier.circle,
                tier.displayName().withStyle(tier.chatColor())).withStyle(ChatFormatting.GOLD));
        sp.sendSystemMessage(Component.translatable(equipped == 4 ? "message.isekai_mahou.class_ranger.garb_equipped"
                : "message.isekai_mahou.class_ranger.garb_inventory").withStyle(ChatFormatting.GRAY));
        RangerFx.sound(sp.serverLevel(), sp.position(), SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.8F + 0.1F * tier.number);
        sp.serverLevel().sendParticles(ParticleTypes.CHERRY_LEAVES, sp.getX(), sp.getY() + 1.5, sp.getZ(), 30, 0.7, 0.7, 0.7, 0.02);
    }

    private RangerService() {}
}
