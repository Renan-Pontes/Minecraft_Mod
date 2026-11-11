package com.renanpontes.isekaimahou.feature.class_smith;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.class_smith.forge.BladeMaterial;
import com.renanpontes.isekaimahou.feature.class_smith.forge.ForgeService;
import com.renanpontes.isekaimahou.feature.class_smith.glyph.SmithGlyphs;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * As Provas da Forja: o ritual de 5 ranks do Ferreiro (Aprendiz da Forja → Artesão → Mestre Ferreiro → Grão-Mestre →
 * Forjador de Lendas). Cada rank pede nível, TAREFAS de forja e combate (contadas por {@link SmithProgress}) e uma
 * OFERENDA de metal entregue numa Bigorna Arcana. Ao subir: {@code ritual_rank} +1, glifos de origem, XP, itens
 * (martelos de grau, Aço Estelar, pergaminhos de lenda) e a cerimônia na bigorna.
 * Ritmo pensado igual ao das outras classes: rank I no nível 5, II no 15, III no 30, IV no 50 e V no 70, e Círculos 1/2/4/6/8;
 * chefes: III 1, IV 2, V 4; XP 600/1500/3500/7000/14000.
 */
public final class SmithRanks {
    public static final int MAX_RANK = 5;
    /** Distância máxima da Bigorna Arcana para fazer a oferenda. */
    public static final double ANVIL_RANGE = 6.0;

    /** Uma tarefa: chave de texto, progresso atual (dos dados sincronizados) e meta. */
    public record Task(String key, ToIntFunction<MagicData> progress, int goal) {
        public int value(MagicData d) {
            return Math.min(goal, Math.max(0, progress.applyAsInt(d)));
        }

        public boolean done(MagicData d) {
            return progress.applyAsInt(d) >= goal;
        }

        public MutableComponent text() {
            return Component.translatable(SmithKeys.TASK + key, goal);
        }
    }

    public record Rank(int number, int level, List<Task> tasks, Supplier<List<ItemStack>> offering, List<String> glyphs,
                       int xp, Function<ServerPlayer, List<ItemStack>> rewards) {
        public MutableComponent title() {
            return Component.translatable(SmithKeys.RANK + number);
        }
    }

    private static final List<Rank> RANKS = new ArrayList<>();

    static {
        RANKS.add(new Rank(1, 5, List.of(
                level(5),
                circle(1),
                new Task("forged", d -> d.counter(SmithProgress.FORGED), 1),
                new Task("reshaped", d -> d.counter(SmithProgress.RESHAPED), 5),
                new Task("blade_kills", d -> d.counter(SmithProgress.BLADE_KILLS), 20)),
                () -> List.of(new ItemStack(Items.IRON_INGOT, 8)),
                List.of(SmithGlyphs.WIDEN, SmithGlyphs.SERRATE, SmithGlyphs.TRACE_ON), 600,
                sp -> List.of(hammer(1), new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 4), essence(Element.FIRE, 2))));
        RANKS.add(new Rank(2, 15, List.of(
                level(15),
                circle(2),
                new Task("ores", d -> d.counter(SmithProgress.ORES), 64),
                new Task("runes", d -> d.counter(SmithProgress.RUNES), 1),
                new Task("reshaped", d -> d.counter(SmithProgress.RESHAPED), 20),
                new Task("blade_kills", d -> d.counter(SmithProgress.BLADE_KILLS), 80)),
                () -> List.of(new ItemStack(Items.IRON_INGOT, 8), new ItemStack(Items.GOLD_INGOT, 4)),
                List.of(SmithGlyphs.MATTE, SmithGlyphs.SPARK_SHOWER, "conjured_weapon", SmithGlyphs.TWIN_FALCHIONS), 1500,
                sp -> List.of(hammer(2), essence(Element.EARTH, 3), new ItemStack(Items.GOLDEN_APPLE, 1))));
        RANKS.add(new Rank(3, 30, List.of(
                level(30),
                circle(4),
                new Task("families", SmithProgress::families, 3),
                new Task("diamond", d -> SmithProgress.hasMaterial(d, BladeMaterial.DIAMOND) ? 1 : 0, 1),
                new Task("elite_kills", d -> d.counter(SmithProgress.ELITE_KILLS), 5),
                new Task("bosses", SmithProgress::bosses, 1),
                new Task("blade_kills", d -> d.counter(SmithProgress.BLADE_KILLS), 200)),
                () -> List.of(new ItemStack(Items.DIAMOND, 4)),
                List.of(SmithGlyphs.RUNE, SmithGlyphs.REFORGE, "blade_field", SmithGlyphs.RHO_AIAS, SmithGlyphs.EPHEMERAL_SWORD), 3500,
                sp -> List.of(hammer(3), essence(Element.ARCANE, 6), new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 8))));
        RANKS.add(new Rank(4, 50, List.of(
                level(50),
                circle(6),
                new Task("netherite", d -> SmithProgress.hasMaterial(d, BladeMaterial.NETHERITE) ? 1 : 0, 1),
                new Task("elite_kills", d -> d.counter(SmithProgress.ELITE_KILLS), 20),
                new Task("casts", d -> d.counter(SmithProgress.CASTS), 150),
                new Task("bosses", SmithProgress::bosses, 2)),
                () -> List.of(new ItemStack(Items.NETHERITE_INGOT, 2)),
                List.of("arsenal_gate", SmithGlyphs.STARFORGE, SmithGlyphs.CALADBOLG), 7000,
                sp -> List.of(new ItemStack(SmithRegistries.STAR_METAL.get(), 2), Legends.scroll("infinite_blades"),
                        new ItemStack(Items.GOLDEN_APPLE, 2))));
        RANKS.add(new Rank(5, 70, List.of(
                level(70),
                circle(8),
                new Task("starmetal", d -> SmithProgress.hasMaterial(d, BladeMaterial.STARMETAL) ? 1 : 0, 1),
                new Task("masterwork", d -> d.hasFlag(SmithProgress.MASTERWORK_FLAG) ? 1 : 0, 1),
                new Task("bosses", SmithProgress::bosses, 4),
                new Task("blade_kills", d -> d.counter(SmithProgress.BLADE_KILLS), 600)),
                () -> List.of(new ItemStack(Items.NETHER_STAR, 1)),
                List.of(SmithGlyphs.BLADE_WORLD), 14000,
                sp -> {
                    // as lendas do pacote de técnicas vêm junto (e não se repetem no login)
                    MagicApi.get(sp).setFlag(SmithTechniques.LEGENDS_FLAG, true);
                    List<ItemStack> out = new ArrayList<>(List.of(hammer(4), Legends.scroll(SmithLegends.STARFORGE_GOD),
                            Legends.scroll(SmithLegends.BROKEN_PHANTASM), new ItemStack(Items.ENCHANTED_GOLDEN_APPLE, 1)));
                    out.addAll(SmithTechniques.legendScrolls());
                    return out;
                }));
    }

    private static Task level(int lv) {
        return new Task("level", MagicData::level, lv);
    }

    private static Task circle(int c) {
        return new Task("circle", MagicData::circle, c);
    }

    public static List<Rank> all() {
        return RANKS;
    }

    @Nullable
    public static Rank rank(int n) {
        return n >= 1 && n <= RANKS.size() ? RANKS.get(n - 1) : null;
    }

    public static boolean tasksDone(MagicData d, Rank r) {
        for (Task t : r.tasks()) if (!t.done(d)) return false;
        return true;
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "—";
        };
    }

    private static ItemStack hammer(int tier) {
        return new ItemStack(SmithRegistries.HAMMER_TIERS.get(tier - 1).get());
    }

    private static ItemStack essence(Element e, int n) {
        var item = CoreRegistries.ESSENCES.get(e);
        return item == null ? ItemStack.EMPTY : new ItemStack(item.get(), n);
    }

    // =====================================================================================
    //  Subir de rank (servidor)
    // =====================================================================================

    /**
     * Tenta subir para o próximo rank na bigorna {@code anvil}: confere classe, tarefas e oferenda, paga a oferenda e faz
     * tudo. Devolve a mensagem de erro (null = subiu).
     */
    @Nullable
    public static MutableComponent tryAdvance(ServerPlayer sp, Vec3 anvil) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.SMITH) return msg("rank_smith_only");
        int current = SmithProgress.rank(d);
        Rank r = rank(current + 1);
        if (r == null) return msg("rank_max");
        if (!tasksDone(d, r)) return msg("rank_tasks");
        if (!sp.isCreative() && !ForgeService.take(sp, r.offering().get())) {
            return msg("rank_offering", ForgeService.costText(r.offering().get()));
        }
        grant(sp, r, anvil);
        return null;
    }

    /** Concede o rank (também usado pelo comando de teste). */
    public static void grant(ServerPlayer sp, Rank r, Vec3 at) {
        MagicApi.addCounter(sp, SmithProgress.RANK, 1);
        MagicApi.addCounter(sp, "ritual_rank", 1);
        List<Glyph> learned = new ArrayList<>();
        for (String id : r.glyphs()) {
            Glyph g = GlyphRegistry.get(IsekaiMahou.id(id));
            if (g == null || MagicApi.get(sp).knowsGlyph(g.id())) continue;
            MagicApi.learnGlyphSilently(sp, g.id());
            learned.add(g);
        }
        MagicApi.giveXp(sp, r.xp(), "ritual");
        MagicData d = MagicApi.get(sp);
        if (d.isAwakened(Element.FIRE)) MagicApi.giveElementXp(sp, Element.FIRE, 25 * r.number());
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack s : r.rewards().apply(sp)) if (!s.isEmpty()) items.add(s);
        Rewards.giveAll(sp, items);
        SmithStarter.placeUnlocked(sp, r.number());
        ceremony(sp, r, at, learned);
    }

    private static void ceremony(ServerPlayer sp, Rank r, Vec3 at, List<Glyph> learned) {
        ServerLevel sl = sp.serverLevel();
        int rgb = MagicPath.SMITH.rgb();
        Vec3 top = at.add(0, 1.1, 0);
        for (int i = 0; i < 18; i++) {
            double y = top.y + i * 0.6;
            sl.sendParticles(SmithFx.dust(i % 3 == 0 ? 0xFFE08A : rgb, 1.6F), top.x, y, top.z, 4, 0.15, 0.1, 0.15, 0);
        }
        SmithFx.ring(sl, at.add(0, 0.1, 0), 2.5, SmithFx.dust(0xFFB040, 1.3F), 36);
        SmithFx.ring(sl, at.add(0, 0.1, 0), 4.0, ParticleTypes.FLAME, 40);
        SmithFx.forgeSparks(sl, top, SmithFx.MOLTEN, 60);
        sl.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, sp.getX(), sp.getY() + 1, sp.getZ(), 30, 0.6, 0.9, 0.6, 0.3);
        SmithFx.sound(sl, top, SoundEvents.ANVIL_LAND, 1.0F, 0.8F);
        SmithFx.sound(sl, top, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        SmithFx.sound(sl, top, SoundEvents.BLAZE_SHOOT, 0.8F, 0.6F);
        BossKit.shake(sl, top, 10, 0.4F, 10);

        Rewards.banner(sp, Component.translatable("message.isekai_mahou.class_smith.rank_up", roman(r.number()), r.title()), rgb);
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.class_smith.rank_up", roman(r.number())), r.title(),
                new ItemStack(r.number() >= 4 ? SmithRegistries.STAR_METAL.get() : SmithRegistries.SMITH_HAMMER.get()), rgb);
        for (Glyph g : learned) {
            sp.sendSystemMessage(Component.literal("⚒ ").withColor(rgb).append(Component.translatable(
                    "message.isekai_mahou.class_smith.glyph_learned", g.displayName().withColor(rgb)).withStyle(ChatFormatting.GRAY)));
        }
        sp.sendSystemMessage(Component.translatable(r.number() < MAX_RANK ? "message.isekai_mahou.class_smith.next_hint"
                : "message.isekai_mahou.class_smith.all_done").withStyle(r.number() < MAX_RANK ? ChatFormatting.DARK_AQUA : ChatFormatting.GOLD));
    }

    private static MutableComponent msg(String key, Object... args) {
        return Component.translatable(SmithKeys.MESSAGE + key, args).withStyle(ChatFormatting.RED);
    }

    private SmithRanks() {}
}
