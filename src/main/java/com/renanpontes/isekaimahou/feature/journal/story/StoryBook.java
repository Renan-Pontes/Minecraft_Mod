package com.renanpontes.isekaimahou.feature.journal.story;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * A CAMPANHA (v1.0 release): o fio narrativo opcional que corre junto da história do Diário, tirado de
 * {@code docs/LORE.md} (seção 14). São sete Atos (grupos de capítulos) e "batidas" curtas disparadas nos marcos da
 * progressão — abrir um capítulo, cumprir um passo, romper um Círculo, derrubar um chefe.
 * <p>
 * Onda 5 — <i>"o Dragão é o fim... depois dele é só a torre infinita"</i>: as masmorras, torres, biomas, a Cidade Anã,
 * a Cidadela e a Biblioteca agora ficam DENTRO dos Atos (antes do Dragão, pelo "order" de cada capítulo); o Epílogo
 * (Ato 8) é só o Abismo Infinito.
 * <p>
 * Palavra do dono: <i>"sem forçar ela, se o player pular, pulou"</i>. Por isso nada aqui trava nada: uma batida é só uma
 * fala do Eco (o Espírito Guia) na caixa de diálogo, às vezes aberta por uma cutscene curta de marco; pulada ou com a
 * campanha desligada, ela fica guardada no Diário ("Ecos" do capítulo) para quem quiser ler depois.
 * <p>
 * Classe comum (servidor e cliente): só ids e a ordem. Os textos ficam em {@code src/main/lang/story}
 * ({@code tools/gen/journal_story.py} confere se cada id daqui tem texto):
 * {@code story.isekai_mahou.<batida>.title}, {@code .narration} (só cenas) e {@code .1 … .n} (falas).
 */
public final class StoryBook {
    /** O que dispara uma batida. */
    public enum Trigger {
        /** Um capítulo do Diário abriu ({@code key} = id do capítulo). */
        CHAPTER_BEGIN,
        /** Um capítulo do Diário foi concluído. */
        CHAPTER_DONE,
        /** Um passo foi cumprido ({@code key} = "capitulo.passo"). */
        STEP,
        /** Um contador do MagicData chegou a {@code min} ({@code key} = contador; vários separados por "|"). */
        COUNTER,
        /** O jogador rompeu o Círculo {@code min}. */
        CIRCLE
    }

    /**
     * Um Ato: agrupa os capítulos da história a partir de {@code fromOrder} (campo "order" do capítulo).
     * Textos: {@code story.isekai_mahou.act.<n>} (nome) e {@code .question} (a pergunta do ato).
     */
    public record Act(int number, int fromOrder, int color) {
        public MutableComponent title() {
            return Component.translatable("story.isekai_mahou.act." + number);
        }

        public MutableComponent question() {
            return Component.translatable("story.isekai_mahou.act." + number + ".question");
        }
    }

    /** Um trecho de fala: quem fala ({@code story.isekai_mahou.speaker.<id>}) e quantas linhas seguidas. */
    public record Part(String speaker, int lines) {}

    /**
     * Uma batida da campanha.
     *
     * @param chapter capítulo do Diário onde ela fica guardada ("Ecos"); vazio = não aparece em capítulo nenhum
     * @param scene   abre com a cutscene curta de marco (título + narração); sem cutscenes, a narração vira a 1ª fala
     * @param delay   espera (ticks) entre o gatilho e a fala — chefes: a cena de morte e a câmera do abate (2 s) passam antes
     */
    public record Beat(String id, int act, String chapter, Trigger trigger, String key, int min, boolean scene,
                       List<Part> parts, int rgb, int delay) {
        public String textKey() {
            return "story.isekai_mahou." + id;
        }

        public MutableComponent title() {
            return Component.translatable(textKey() + ".title");
        }

        public MutableComponent narration() {
            return Component.translatable(textKey() + ".narration");
        }

        public MutableComponent line(int n) {
            return Component.translatable(textKey() + "." + n);
        }

        /** Total de falas (somando os trechos). */
        public int lines() {
            int n = 0;
            for (Part p : parts) n += p.lines();
            return n;
        }

        /** Marcador "já passou" (vista, pulada ou com a campanha desligada): libera a leitura no Diário. */
        public String seenFlag() {
            return "story." + id;
        }

        /** Marcador "na fila" (sobrevive a sair do mundo: a fala toca quando o jogador voltar). */
        public String queuedFlag() {
            return "story.q." + id;
        }

        /** O contador {@code counter} dispara esta batida? */
        public boolean matchesCounter(String counter) {
            if (trigger != Trigger.COUNTER) return false;
            if (key.indexOf('|') < 0) return key.equals(counter);
            for (String k : key.split("\\|")) if (k.equals(counter)) return true;
            return false;
        }
    }

    /** Cor do Eco (a mesma do Espírito Guia sem capítulo). */
    public static final int ECHO = 0x9FE8FF;
    private static final int HERALD = 0xB98CFF, DRAGON = 0xFF9A4D, SAGE = 0xFFD86B, DARK = 0xC06A6A;
    /** Depois da morte de um chefe: a cena de morte (até ~94 ticks) e a câmera do abate terminam antes da fala. */
    private static final int AFTER_BOSS = 180;
    /** Depois do Dragão: espera do final do Diário (200) + cutscene + 7 linhas de epílogo a cada 70 ticks, com folga. */
    private static final int AFTER_FINALE = 1000;

    private static final List<Act> ACTS = List.of(
            new Act(1, Integer.MIN_VALUE, 0x8FD16A),
            new Act(2, 20, 0xFFD86B),
            new Act(3, 27, 0xC06A6A),
            new Act(4, 30, 0x7FD4FF),
            new Act(5, 40, 0xE8E0FF),
            new Act(6, 50, 0xB98CFF),
            new Act(7, 60, 0xFF9A4D),
            // Depois do Dragão: só o Abismo Infinito (o capítulo pós-final, escondido até o Dragão cair).
            new Act(8, 61, 0x9FE8FF));

    private static final Map<String, Beat> BEATS = new LinkedHashMap<>();

    static {
        // ---------------- Ato I — O Chamado
        scene("a1_call", 1, "awakening", Trigger.CHAPTER_BEGIN, "awakening", 0, ECHO, 160, echo(2));
        talk("a1_city", 1, "guild_and_trials", Trigger.STEP, "guild_and_trials.reach_city", 0, ECHO, 100, echo(2));
        talk("a1_trial", 1, "guild_and_trials", Trigger.STEP, "guild_and_trials.trial_one", 0, ECHO, 160, echo(1));
        // ---------------- Ato II — Os Seis
        scene("a2_six", 2, "six_shrines", Trigger.CHAPTER_BEGIN, "six_shrines", 0, SAGE, 140, echo(2));
        talk("a2_shrine", 2, "six_shrines", Trigger.STEP, "six_shrines.shrine_trial", 0, SAGE, 120, part("sage", 1), echo(1));
        talk("a2_shadow", 2, "six_shrines", Trigger.CIRCLE, "", 2, ECHO, 220, echo(2));
        talk("a2_goblin", 2, "six_shrines", Trigger.STEP, "six_shrines.goblin_king", 0, ECHO, AFTER_BOSS, echo(1));
        talk("a2_watcher", 2, "six_shrines", Trigger.CIRCLE, "", 3, HERALD, 220, part("unknown", 1), echo(1));
        talk("a2_seventh", 2, "programmed_magic", Trigger.STEP, "programmed_magic.renegade_archmage", 0, ECHO, AFTER_BOSS, echo(2));
        // ---------------- Ato III — As Sobras da Ruptura
        scene("a3_rupture", 3, "dark_masters", Trigger.CHAPTER_BEGIN, "dark_masters", 0, DARK, 140, echo(1));
        talk("a3_lich", 3, "dark_masters", Trigger.STEP, "dark_masters.lich", 0, ECHO, AFTER_BOSS, echo(1));
        talk("a3_colossus", 3, "wild_titans", Trigger.COUNTER, "boss.ruin_colossus", 1, ECHO, AFTER_BOSS, echo(1));
        talk("a3_hydra", 3, "hidden_horrors", Trigger.STEP, "hidden_horrors.false_hydra", 0, ECHO, AFTER_BOSS, echo(1));
        // ---------------- Ato IV — As Mãos do Dragão
        scene("a4_plane", 4, "elemental_plane", Trigger.STEP, "elemental_plane.enter_plane", 0, 0x7FD4FF, 80, echo(1));
        talk("a4_guardian", 4, "elemental_plane", Trigger.STEP, "elemental_plane.first_guardian", 0, DRAGON, AFTER_BOSS,
                part("guardian", 1), echo(1));
        talk("a4_fusion", 4, "elemental_plane", Trigger.STEP, "elemental_plane.first_fusion", 0, ECHO, 100, echo(1));
        // ---------------- Ato V — Luz e Sombra
        scene("a5_war", 5, "light_and_shadow", Trigger.CHAPTER_BEGIN, "light_and_shadow", 0, 0xE8E0FF, 140, echo(1));
        talk("a5_sovereign", 5, "light_and_shadow", Trigger.STEP, "light_and_shadow.abyss_sovereign", 0, ECHO, 200, echo(2));
        // Os dois podem cair em qualquer ordem: a fala do equilíbrio quebrado espera o capítulo fechar.
        talk("a5_balance", 5, "light_and_shadow", Trigger.CHAPTER_DONE, "light_and_shadow", 0, ECHO, 240, echo(2));
        talk("a5_demon", 5, "nether_citadel", Trigger.COUNTER, "boss.demon_lord", 1, ECHO, AFTER_BOSS, echo(2));
        // ---------------- Ato VI — O Crepúsculo
        scene("a6_called", 6, "twilight", Trigger.CIRCLE, "", 9, HERALD, 240, part("herald", 1), echo(2));
        talk("a6_rift", 6, "twilight", Trigger.STEP, "twilight.enter_rift", 0, ECHO, 100, echo(2));
        scene("a6_herald", 6, "twilight", Trigger.STEP, "twilight.herald", 0, HERALD, 220, echo(2));
        talk("a6_hollow", 6, "hollow_world", Trigger.STEP, "hollow_world.garganta", 0, ECHO, 100, echo(1));
        // ---------------- Ato VII — A Origem
        scene("a7_dragon", 7, "primordial", Trigger.CHAPTER_BEGIN, "primordial", 0, DRAGON, 160, echo(2));
        // Depois do final: a cutscene e o epílogo narrado do Diário (~45 s) passam antes de o Eco fechar a história.
        talk("a7_after", 7, "primordial", Trigger.CHAPTER_DONE, "primordial", 0, ECHO, AFTER_FINALE, echo(2));
        // ---------------- Epílogo — depois do Dragão só existe o Abismo Infinito
        // O 1º marco de 10 andares (as Sombras já apareceram no 5): o Eco explica o que são.
        talk("e_abyss", 8, "beyond_limit", Trigger.STEP, "beyond_limit.abyss_floor_10", 0, ECHO, 120, echo(2));
        // ---------------- Fora da rota (o mundo aberto): falas soltas, guardadas no capítulo mais próximo do assunto
        talk("x_lore", 0, "guild_and_trials", Trigger.COUNTER, "lore.fragments", 1, ECHO, 120, echo(1));
        talk("x_truth", 0, "guild_and_trials", Trigger.COUNTER, "lore.sets", 1, ECHO, 160, echo(1));
        talk("x_city", 0, "kingdoms", Trigger.COUNTER, "settlements.founded|settlements.lord", 1, ECHO, 200, echo(2));
        // Capítulos que a onda 5 trouxe para antes do Dragão: cada fala no Ato onde o capítulo ficou.
        talk("x_towers", 2, "towers", Trigger.STEP, "towers.enter", 0, SAGE, 80, part("inscription", 1), echo(1));
        talk("x_depths", 4, "depths", Trigger.STEP, "depths.enter", 0, ECHO, 80, echo(1));
        talk("x_dwarf", 4, "dwarf_hold", Trigger.STEP, "dwarf_hold.enter", 0, ECHO, 80, echo(1));
        talk("x_library", 5, "end_library", Trigger.STEP, "end_library.find", 0, ECHO, 80, echo(1));
    }

    private static Part echo(int lines) {
        return new Part("echo", lines);
    }

    private static Part part(String speaker, int lines) {
        return new Part(speaker, lines);
    }

    private static void talk(String id, int act, String chapter, Trigger trigger, String key, int min, int rgb, int delay, Part... parts) {
        BEATS.put(id, new Beat(id, act, chapter, trigger, key, min, false, List.of(parts), rgb, delay));
    }

    private static void scene(String id, int act, String chapter, Trigger trigger, String key, int min, int rgb, int delay, Part... parts) {
        BEATS.put(id, new Beat(id, act, chapter, trigger, key, min, true, List.of(parts), rgb, delay));
    }

    public static List<Act> acts() {
        return ACTS;
    }

    public static List<Beat> beats() {
        return List.copyOf(BEATS.values());
    }

    @Nullable
    public static Beat byId(String id) {
        return BEATS.get(id);
    }

    /** O Ato de um capítulo da história, pelo campo "order" dele. */
    public static Act actOf(int chapterOrder) {
        Act out = ACTS.get(0);
        for (Act a : ACTS) if (chapterOrder >= a.fromOrder()) out = a;
        return out;
    }

    /** Batidas guardadas num capítulo do Diário, na ordem da campanha. */
    public static List<Beat> forChapter(String chapterId) {
        List<Beat> out = new ArrayList<>(4);
        for (Beat b : BEATS.values()) if (b.chapter().equals(chapterId)) out.add(b);
        return out;
    }

    /** Nome de quem fala (aba da caixa de diálogo). */
    public static MutableComponent speaker(String id) {
        return Component.translatable("story.isekai_mahou.speaker." + id);
    }

    private StoryBook() {}
}
