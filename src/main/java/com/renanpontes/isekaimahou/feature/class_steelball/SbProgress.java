package com.renanpontes.isekaimahou.feature.class_steelball;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * Progressão própria do Steel Ball: as 5 LIÇÕES do Mestre da Rotação (ranks I–V). Cada lição pede Círculo, Partes do
 * Cadáver Santo absorvidas e um treino/desafio (contadores). O Mestre confere e, se tudo estiver pronto, ensina:
 * glifos de origem, XP, itens, e {@code ritual_rank} +1 (convenção entre módulos). Os ATOS da unha giratória vêm
 * do rank: Ato 1 (I), Ato 2 (II), Ato 3 (III–IV), Ato 4 — Rotação Infinita (V).
 */
public final class SbProgress {
    public static final String RANK = "ritual_rank.steelball";
    public static final String GLOBAL_RANK = "ritual_rank";
    public static final String BALL_HITS = "steelball.ball_hits";
    public static final String NAIL_HITS = "steelball.nail_hits";
    public static final String MOUNTED_KILLS = "steelball.mounted_kills";
    public static final String SPIN_HEALS = "steelball.spin_heals";
    public static final String GOLDEN_HITS = "steelball.golden_hits";
    public static final String DUEL = "steelball.mentor_duel";
    public static final String BOSS_KILLS = "steelball.boss_kills";
    public static final int MAX_RANK = 5;
    /** Prefixos das chaves de tradução montadas pelo número/contador. */
    private static final String ACT_KEY = "gui.isekai_mahou.steelball.act.";
    private static final String RANK_KEY = "gui.isekai_mahou.steelball.rank.";
    private static final String TASK_KEY = "gui.isekai_mahou.steelball.task.";
    /** Cor do Steel Ball (aço). */
    public static final int RGB = 0xB4C0CC;

    /** Um treino da lição: contador e quantidade. */
    public record Task(String counter, int amount) {
        public MutableComponent describe() {
            return Component.translatable(TASK_KEY + counter.substring(counter.indexOf('.') + 1), amount);
        }

        public int progress(MagicData d) {
            return Math.min(amount, d.counter(counter));
        }

        public boolean done(MagicData d) {
            return d.counter(counter) >= amount;
        }
    }

    /** Uma lição (rank N): requisitos e recompensas. */
    public record Rank(int number, int circle, int parts, List<Task> tasks, int xp, List<String> glyphs) {
        public MutableComponent title() {
            return Component.translatable(RANK_KEY + number);
        }

        public MutableComponent lesson() {
            return Component.translatable(RANK_KEY + number + ".lesson");
        }
    }

    private static final Rank[] RANKS = {
            new Rank(1, 1, 0, List.of(new Task(BALL_HITS, 30)), 600, List.of("spin_nail", "spin_heal")),
            new Rank(2, 2, 2, List.of(new Task(MOUNTED_KILLS, 25), new Task(BALL_HITS, 120)), 1500, List.of("gallop_spin", "muscle_lock")),
            new Rank(3, 4, 3, List.of(new Task(NAIL_HITS, 150), new Task(SPIN_HEALS, 15), new Task(BOSS_KILLS, 1)), 3500,
                    List.of("spin_vortex", "spin_drill")),
            new Rank(4, 6, 5, List.of(new Task(DUEL, 1), new Task(BOSS_KILLS, 2)), 7000, List.of("golden_rectangle")),
            new Rank(5, 8, 6, List.of(new Task(GOLDEN_HITS, 60), new Task(MOUNTED_KILLS, 150), new Task(BOSS_KILLS, 4)), 14000,
                    List.of("infinite_rotation")),
    };

    public static List<Rank> ranks() {
        return List.of(RANKS);
    }

    @Nullable
    public static Rank get(int n) {
        return n >= 1 && n <= MAX_RANK ? RANKS[n - 1] : null;
    }

    public static int rank(MagicData d) {
        return d == null ? 0 : Math.max(0, Math.min(MAX_RANK, d.counter(RANK)));
    }

    /** Ato da unha giratória pelo rank: 0 (ainda sem lição), 1, 2, 3 (ranks III e IV) ou 4. */
    public static int act(MagicData d) {
        int r = rank(d);
        if (r >= 5) return 4;
        if (r >= 3) return 3;
        return r;
    }

    /** Nome do Ato ("A Unha que Persegue"...). */
    public static MutableComponent actName(int act) {
        return Component.translatable(ACT_KEY + Math.max(0, Math.min(4, act)));
    }

    /** Ato de quem conjura: Steel Ball pelo rank; outras classes (Lição) usam o Ato 1; mobs, o Ato 2. */
    public static int actOf(@Nullable LivingEntity caster) {
        if (caster instanceof Player p) {
            MagicData d = MagicApi.get(p);
            return d.path() == MagicPath.STEELBALL ? Math.max(1, act(d)) : 1;
        }
        return 2;
    }

    public static boolean isSteelBall(@Nullable LivingEntity e) {
        return e instanceof Player p && MagicApi.get(p).path() == MagicPath.STEELBALL;
    }

    /**
     * Bônus de classe "Giro Perfeito" nas técnicas de rotação: +15% e +5% por Ato; montado, mais 30% (só para o
     * dano físico da bola — nas magias o bônus montado entra pelo Poder Mágico).
     */
    public static float spinMultiplier(@Nullable LivingEntity caster, boolean spell) {
        if (!isSteelBall(caster)) return 1F;
        MagicData d = MagicApi.get((Player) caster);
        float m = 1.15F + 0.05F * act(d);
        if (!spell && caster.getVehicle() instanceof LivingEntity) m *= 1.3F;
        if (SbPart.RIGHT_ARM.owned(d) && !spell) m *= 1.2F;
        return m;
    }

    /** Próxima lição (null = mestre completo). */
    @Nullable
    public static Rank next(MagicData d) {
        return get(rank(d) + 1);
    }

    /** Linhas do que falta para a lição {@code r} (vazia = pronto). */
    public static List<MutableComponent> missing(MagicData d, Rank r) {
        List<MutableComponent> out = new ArrayList<>();
        if (d.circle() < r.circle()) out.add(Component.translatable("gui.isekai_mahou.steelball.need.circle", r.circle(), d.circle()));
        int parts = SbPart.count(d);
        if (parts < r.parts()) out.add(Component.translatable("gui.isekai_mahou.steelball.need.parts", r.parts(), parts));
        for (Task t : r.tasks()) {
            if (!t.done(d)) out.add(Component.translatable("gui.isekai_mahou.steelball.need.task", t.describe(), t.progress(d), t.amount()));
        }
        return out;
    }

    public static boolean ready(MagicData d, Rank r) {
        return missing(d, r).isEmpty();
    }

    /** Itens de recompensa de cada lição (resolvidos na hora; nada de outro módulo). */
    private static List<Supplier<ItemStack>> rewards(int n) {
        return switch (n) {
            case 1 -> List.of(() -> new ItemStack(SbRegistry.HORSE_WHISTLE.get()), () -> new ItemStack(SbRegistry.SAINT_COMPASS.get()));
            case 2 -> List.of(() -> new ItemStack(Items.GOLDEN_CARROT, 16), () -> new ItemStack(CoreRegistries.ESSENCES.get(Element.EARTH).get(), 3),
                    () -> new ItemStack(CoreRegistries.ESSENCES.get(Element.WIND).get(), 3));
            case 3 -> List.of(() -> new ItemStack(SbRegistry.RELIC.get()), () -> new ItemStack(Items.SADDLE));
            case 4 -> List.of(() -> Legends.scroll(SbLegends.GOLDEN_RIDER), () -> new ItemStack(CoreRegistries.MANA_CRYSTAL.get(), 4));
            default -> List.of(() -> Legends.scroll(SbLegends.INFINITE_ACT), () -> new ItemStack(Items.ENCHANTED_GOLDEN_APPLE));
        };
    }

    /** Sobe um rank (o Mestre já conferiu {@link #ready}). Cerimônia, glifos, XP, itens e contadores. */
    public static void rankUp(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        Rank r = next(d);
        if (r == null) return;
        int oldAct = act(d);
        MagicApi.addCounter(sp, RANK, 1);
        MagicApi.addCounter(sp, GLOBAL_RANK, 1);
        for (String g : r.glyphs()) MagicApi.learnGlyph(sp, IsekaiMahou.id(g));
        MagicApi.giveXp(sp, r.xp(), "ritual");
        for (Supplier<ItemStack> s : rewards(r.number())) {
            ItemStack st = s.get();
            if (!st.isEmpty()) Rewards.give(sp, st);
        }
        int newAct = act(d);
        Rewards.banner(sp, Component.translatable("banner.isekai_mahou.steelball.rank", roman(r.number()), r.title()), RGB);
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.steelball.rank", roman(r.number())), r.title(),
                new ItemStack(SbRegistry.STEEL_BALL.get()), RGB);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.steelball.rank_up", roman(r.number()), r.title())
                .withStyle(ChatFormatting.GOLD));
        for (String g : r.glyphs()) {
            var glyph = com.renanpontes.isekaimahou.glyph.GlyphRegistry.get(IsekaiMahou.id(g));
            if (glyph != null) {
                sp.sendSystemMessage(Component.translatable("message.isekai_mahou.steelball.glyph", glyph.displayName())
                        .withStyle(ChatFormatting.AQUA));
            }
        }
        if (newAct > oldAct) {
            sp.sendSystemMessage(Component.translatable("message.isekai_mahou.steelball.act", newAct,
                    actName(newAct)).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        celebrate(sp, r.number());
        SbTechniques.check(sp);
    }

    private static void celebrate(ServerPlayer sp, int n) {
        ServerLevel sl = sp.serverLevel();
        Vec3 p = sp.position();
        SbFx.sound(sl, p, SoundEvents.PLAYER_LEVELUP, 1.0F, 0.9F);
        SbFx.sound(sl, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.0F, 0.7F + 0.1F * n);
        for (int i = 0; i < 3; i++) SbFx.spiral(sl, p, n >= 4 ? SbFx.GOLD : SbFx.STEEL, 1.1 - i * 0.2, 2.4, 32, i * 2.1F);
        SbFx.goldenSpiral(sl, p.add(0, 1.2, 0), sp.getLookAngle(), 1.2 + 0.2 * n);
        sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1, p.z, 20 + 6 * n, 0.4, 0.8, 0.4, 0.05);
    }

    public static String roman(int n) {
        return switch (n) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> "0";
        };
    }

    private SbProgress() {}
}
