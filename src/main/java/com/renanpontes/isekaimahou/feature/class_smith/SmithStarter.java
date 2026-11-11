package com.renanpontes.isekaimahou.feature.class_smith;

import java.util.List;
import java.util.Locale;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreGameplay;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.Progression;
import com.renanpontes.isekaimahou.feature.class_smith.glyph.SmithGlyphs;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.MoveTrigger;
import com.renanpontes.isekaimahou.glyph.SpellParser;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellSlot;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Técnicas e magias prontas do Ferreiro: ao escolher a classe ele aprende Martelada, Metal Fundido, Alongar e Afiar
 * Ponta e as magias da classe vão para o loadout 1 (o ativo; as técnicas genéricas da categoria passam para o 2):
 * "Martelada Ígnea" (Z), "Alongar Lâmina" (X, forja a lâmina da mão) e "Fio Perfurante" (C). As magias dos ranks
 * seguintes entram no slot livre do mesmo loadout ou, se não houver, viram presets (rascunhos) do Grimório.
 */
public final class SmithStarter {
    /** Glifos de origem aprendidos ao escolher a classe. */
    public static final List<String> CHOSEN_GLYPHS = List.of(SmithGlyphs.HAMMER_SLAM, SmithGlyphs.MOLTEN,
            SmithGlyphs.LENGTHEN, SmithGlyphs.SHARPEN);

    /** Uma magia pronta (nome pt/en, cor, cântico, glifos, slot desejado). */
    public record ClassSpell(String namePt, String nameEn, int color, String chantPt, int slot, List<String> glyphs) {
        public SpellRecipe recipe(String language) {
            boolean pt = language != null && language.startsWith("pt");
            return new SpellRecipe(glyphs.stream().map(IsekaiMahou::id).toList(), pt ? namePt : nameEn, color, pt ? chantPt : "");
        }
    }

    public static final List<ClassSpell> STARTER = List.of(
            new ClassSpell("Martelada Ígnea", "Blazing Hammerfall", 0xFF7A2A, "Bate, fornalha!", 0,
                    List.of(SmithGlyphs.HAMMER_SLAM, SmithGlyphs.MOLTEN)),
            new ClassSpell("Alongar Lâmina", "Lengthen Blade", 0xD8DEE8, "", 1, List.of("self", SmithGlyphs.LENGTHEN)),
            new ClassSpell("Fio Perfurante", "Piercing Edge", 0xBFD8FF, "", 2, List.of("strike", SmithGlyphs.SHARPEN)));

    /** Magias liberadas por rank (entram no loadout do Ferreiro se houver espaço; senão viram presets). */
    public static List<ClassSpell> unlocked(int rank) {
        return switch (rank) {
            case 1 -> List.of(new ClassSpell("Traçar: Ligar", "Trace On", 0x9FD8FF, "Traçar... ligar.", 2,
                    List.of("self", SmithGlyphs.TRACE_ON)));
            case 2 -> List.of(new ClassSpell("Chuva de Fagulhas", "Spark Shower", 0xFFB040, "", 1,
                    List.of(SmithGlyphs.SPARK_SHOWER, SmithGlyphs.MOLTEN)),
                    new ClassSpell("Kanshō e Bakuya", "Kanshou and Bakuya", 0xF2F4FF, "", 2,
                            List.of(SmithGlyphs.TWIN_FALCHIONS, "arcane_damage")));
            case 3 -> List.of(new ClassSpell("Runa da Forja", "Forge Rune", 0xC084FF, "", 2,
                    List.of("self", SmithGlyphs.RUNE)),
                    new ClassSpell("Rho Aias", "Rho Aias", 0xFF8AD0, "Sete pétalas, cubram o céu!", 1,
                            List.of("self", SmithGlyphs.RHO_AIAS)),
                    new ClassSpell("Espada Mágica Efêmera", "Ephemeral Magic Sword", 0xFF7A2A, "", 2,
                            List.of("projectile", SmithGlyphs.EPHEMERAL_SWORD)));
            case 4 -> List.of(new ClassSpell("Forja Estelar", "Starforge", 0x8FA8FF, "Desce, martelo das estrelas!",
                    Progression.ULTIMATE_SLOT, List.of(SmithGlyphs.STARFORGE, SmithGlyphs.MOLTEN, "amplify")),
                    new ClassSpell("Caladbolg II", "Caladbolg II", 0xC9A8FF, "Corda tensa, lâmina torcida — voa!", 2,
                            List.of(SmithGlyphs.CALADBOLG, "arcane_damage")));
            case 5 -> List.of(new ClassSpell("Mundo das Lâminas", "World of Blades", 0xE8A04A, "Eu sou o osso da minha espada.",
                    Progression.ULTIMATE_SLOT, List.of(SmithGlyphs.BLADE_WORLD, "conjured_weapon")));
            default -> List.of();
        };
    }

    /** Uma vez por personagem Ferreiro. Chamado ao escolher a classe e no login (saves antigos). */
    public static void giveStarter(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.SMITH || d.hasFlag(SmithProgress.STARTER_FLAG)) return;
        d.setFlag(SmithProgress.STARTER_FLAG, true);
        for (String g : CHOSEN_GLYPHS) learn(sp, g);
        int loadout = freeLoadout(d);
        String lang = sp.clientInformation().language().toLowerCase(Locale.ROOT);
        for (ClassSpell cs : STARTER) place(sp, d, cs, loadout, lang);
        d.markDirty();
    }

    /** Magias que o rank novo libera (os glifos já foram ensinados pelo rank). */
    public static void placeUnlocked(ServerPlayer sp, int rank) {
        MagicData d = MagicApi.get(sp);
        String lang = sp.clientInformation().language().toLowerCase(Locale.ROOT);
        int loadout = smithLoadout(d);
        for (ClassSpell cs : unlocked(rank)) place(sp, d, cs, loadout, lang);
        d.markDirty();
    }

    private static void place(ServerPlayer sp, MagicData d, ClassSpell cs, int loadout, String lang) {
        SpellRecipe recipe = cs.recipe(lang);
        for (ResourceLocation id : recipe.glyphs()) {
            if (GlyphRegistry.get(id) == null) return; // glifo de outro módulo ausente: não grava magia quebrada
        }
        if (!SpellParser.parse(recipe, d).ok()) {
            // ainda não pode conjurar (Círculo baixo...): guarda como rascunho para depois
            d.savePreset(d.uniquePresetName(recipe.name()), recipe);
            return;
        }
        if (loadout >= 0 && d.peekSlot(loadout, cs.slot()).isEmpty()) {
            d.setSlotRecipe(loadout, cs.slot(), MoveTrigger.NORMAL, recipe);
            return;
        }
        if (d.savePreset(d.uniquePresetName(recipe.name()), recipe) >= 0) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.class_smith.spell_preset", recipe.name())
                    .withStyle(ChatFormatting.DARK_AQUA));
        }
    }

    /**
     * Loadout que recebe as magias do Ferreiro (fica gravado para os ranks seguintes). Personagem novo: o loadout 1
     * (o ativo) — as técnicas genéricas da categoria que o núcleo pôs lá vão para o primeiro loadout vazio, nada se
     * perde. Save antigo com magias próprias no 1: o primeiro loadout totalmente vazio. −1 se não houver nenhum.
     */
    private static int freeLoadout(MagicData d) {
        if (onlyCoreStarters(d, 0)) {
            int spare = firstEmpty(d, 1);
            if (spare >= 0) {
                for (int s = 0; s < Progression.ULTIMATE_SLOT; s++) {
                    SpellRecipe r = d.peekSlot(0, s).main();
                    if (r != null && !r.isEmpty()) d.setSlotRecipe(spare, s, MoveTrigger.NORMAL, r);
                }
            }
            for (int s = 0; s < Progression.ULTIMATE_SLOT; s++) d.setSlotRecipe(0, s, MoveTrigger.NORMAL, null);
            d.setActiveLoadout(0);
            d.setFlag("smith.loadout.0", true);
            return 0;
        }
        int l = firstEmpty(d, 1);
        if (l >= 0) d.setFlag("smith.loadout." + l, true);
        return l;
    }

    private static int firstEmpty(MagicData d, int from) {
        for (int l = from; l < d.loadoutCount(); l++) if (d.loadoutEmpty(l)) return l;
        return -1;
    }

    /** O loadout só tem (no máximo) as magias prontas da categoria que o núcleo entrega? */
    private static boolean onlyCoreStarters(MagicData d, int loadout) {
        if (d.loadoutEmpty(loadout)) return true;
        List<List<ResourceLocation>> core = CoreGameplay.starterSpells(MagicPath.Category.PHYSICAL).stream()
                .map(SpellRecipe::glyphs).toList();
        for (int s = 0; s < Progression.SLOTS; s++) {
            SpellSlot slot = d.peekSlot(loadout, s);
            for (MoveTrigger t : MoveTrigger.values()) {
                SpellRecipe r = slot.get(t);
                if (r == null || r.isEmpty()) continue;
                if (t != MoveTrigger.NORMAL || !core.contains(r.glyphs())) return false;
            }
        }
        return true;
    }

    /** O loadout que recebeu as magias do Ferreiro (−1 se nenhum). */
    private static int smithLoadout(MagicData d) {
        for (int l = 0; l < d.loadoutCount(); l++) if (d.hasFlag("smith.loadout." + l)) return l;
        return -1;
    }

    private static void learn(ServerPlayer sp, String id) {
        ResourceLocation rl = IsekaiMahou.id(id);
        if (GlyphRegistry.get(rl) != null && !MagicApi.get(sp).knowsGlyph(rl)) MagicApi.learnGlyphSilently(sp, rl);
    }

    /** Comando de teste: refaz glifos e magias prontas. */
    public static void reset(ServerPlayer sp) {
        MagicApi.get(sp).setFlag(SmithProgress.STARTER_FLAG, false);
        giveStarter(sp);
    }

    private SmithStarter() {}
}
