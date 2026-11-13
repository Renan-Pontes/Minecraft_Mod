package com.renanpontes.isekaimahou.feature.class_hollow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.anim.MahouAnims;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.feature.class_hollow.glyph.CeroFusionAugment;
import com.renanpontes.isekaimahou.feature.class_hollow.glyph.DevourTrigger;
import com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowTech;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * A EVOLUÇÃO do Hollow: devorar almas faz o corpo crescer e mudar. Cada estágio pede Almas devoradas (na vida toda),
 * Círculo e chefes derrotados por perto; ao cumprir, a evolução acontece SOZINHA (com cena), exceto a última — virar
 * Arrancar pede escolher a Resurrección (tela; reabre pela aba).
 * <pre>
 *  0  Hollow        (ao ser tomado)                       → Cero, Bala, Pesquisa, Regeneração
 *  I  Gillian       C2 · 120 almas                         → Hierro (passivo 10% + concentrar) · gatilho "Ao Devorar uma Alma"
 *  II Adjuchas      C4 · 450 almas · 1 chefe               → Sonído, Garganta · aumento "Cero Fusionado"
 *  III Vasto Lorde  C6 · 1200 almas · 3 chefes             → Gran Rey Cero, Negación, Hierro 25%, Cero negro
 *  IV Arrancar      C8 · 3000 almas · 5 chefes · escolha   → Resurrección, Descorrer, Cero Oscuras (liberado)
 * </pre>
 * v1.4: as técnicas são inatas ({@link com.renanpontes.isekaimahou.feature.class_hollow.tech.HollowTech}, liberadas pelo
 * rank); a evolução ensina só os glifos novos da classe (o gatilho e o aumento).
 */
public final class HollowRanks {
    public record Req(String key, int amount, ToIntFunction<MagicData> current) {
        public MutableComponent label() {
            return Component.translatable(Hollow.REQ_KEY + key, amount);
        }

        public boolean ok(MagicData d) {
            return current.applyAsInt(d) >= amount;
        }
    }

    public record Rank(int number, int circle, int xp, List<Req> reqs, List<String> glyphs) {}

    private static List<Rank> ranks;

    private static Req souls(int n) {
        return new Req("souls", n, Hollow::soulsTotal);
    }

    private static Req bosses(int n) {
        return new Req("bosses", n, HollowRanks::bossKills);
    }

    /**
     * Os glifos que o Hollow tem desde o primeiro momento. v1.4: nenhum — as técnicas são inatas (os glifos de forma
     * antigos ficam só para magias salvas).
     */
    public static final List<String> BASE_GLYPHS = List.of();

    public static synchronized List<Rank> ranks() {
        if (ranks == null) {
            ranks = List.of(
                    new Rank(1, 2, 1500, List.of(souls(120)), List.of(DevourTrigger.PATH)),
                    new Rank(2, 4, 3500, List.of(souls(450), bosses(1)), List.of(CeroFusionAugment.PATH)),
                    new Rank(3, 6, 7000, List.of(souls(1200), bosses(3)), List.of()),
                    new Rank(4, 8, 14000, List.of(souls(3000), bosses(5)), List.of()));
        }
        return ranks;
    }

    @Nullable
    public static Rank rank(int number) {
        return number >= 1 && number <= Hollow.MAX_RANK ? ranks().get(number - 1) : null;
    }

    /** Soma dos contadores {@code boss.*} (chefes derrotados por perto — convenção dos módulos de chefes). */
    public static int bossKills(MagicData d) {
        int sum = 0;
        for (Map.Entry<String, Integer> e : d.counters().entrySet()) {
            if (e.getKey().startsWith("boss.") && e.getValue() > 0) sum += e.getValue();
        }
        return sum;
    }

    public static boolean meets(MagicData d, Rank r, @Nullable List<Component> missing) {
        boolean ok = true;
        if (d.circle() < r.circle()) {
            ok = false;
            if (missing != null) missing.add(Component.translatable(Hollow.REQ_KEY + "circle", r.circle()));
        }
        for (Req q : r.reqs()) {
            if (!q.ok(d)) {
                ok = false;
                if (missing != null) missing.add(q.label().append(" (" + Math.min(q.current().applyAsInt(d), q.amount()) + "/" + q.amount() + ")"));
            }
        }
        return ok;
    }

    public static List<Component> missing(MagicData d) {
        List<Component> out = new ArrayList<>();
        Rank next = rank(Hollow.rank(d) + 1);
        if (next != null) meets(d, next, out);
        return out;
    }

    /**
     * Confere a evolução (depois de devorar, ao logar, ao subir de Círculo). Até Vasto Lorde sobe sozinho; o Arrancar
     * abre a escolha da Resurrección (uma vez por sessão; a aba reabre).
     */
    public static void check(ServerPlayer sp) {
        if (!Hollow.isHollow(sp) || Hollowfication.inProgress(sp)) return;
        MagicData d = MagicApi.get(sp);
        Rank next = rank(Hollow.rank(d) + 1);
        if (next == null || !meets(d, next, null)) return;
        if (next.number() == Hollow.ARRANCAR) {
            String flag = "hollow.arrancar_offered";
            if (!d.hasFlag(flag)) {
                d.setFlag(flag, true);
                sp.sendSystemMessage(Hollow.msg("arrancar_ready").withColor(Hollow.REIATSU_RGB));
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new HollowNetwork.Open(HollowNetwork.SCREEN_RELEASE));
            }
            return;
        }
        grant(sp, next);
    }

    /**
     * v1.4: quem já passou de um estágio (mundos antigos) recebe os glifos novos dele. Chamado ao entrar no jogo.
     */
    public static void catchUp(ServerPlayer sp) {
        if (!Hollow.isHollow(sp)) return;
        int cur = Hollow.rank(MagicApi.get(sp));
        for (Rank r : ranks()) {
            if (r.number() > cur) break;
            for (String g : r.glyphs()) MagicApi.learnGlyph(sp, IsekaiMahou.id(g));
        }
    }

    /** As técnicas que chegam neste rank (para a cerimônia e a aba). */
    public static List<HollowTech> techsAt(int rank) {
        List<HollowTech> out = new ArrayList<>();
        for (HollowTech t : HollowTech.values()) if (t.rank == rank) out.add(t);
        return out;
    }

    /** Pode virar Arrancar agora (para a tela)? */
    public static boolean arrancarReady(MagicData d) {
        Rank r = rank(Hollow.ARRANCAR);
        return r != null && Hollow.rank(d) == Hollow.VASTO_LORDE && meets(d, r, null);
    }

    /** Sobe para o rank {@code r} (sem checar requisitos — comandos e a escolha da Resurrección usam direto). */
    public static void grant(ServerPlayer sp, Rank r) {
        MagicData d = MagicApi.get(sp);
        int delta = r.number() - Hollow.rank(d);
        if (delta <= 0) return;
        MagicApi.addCounter(sp, Hollow.RANK, delta);
        MagicApi.addCounter(sp, Hollow.GLOBAL_RANK, delta);
        MagicApi.giveXp(sp, r.xp(), "ritual");
        // quem pulou estágios (comando) aprende tudo o que ficou para trás
        for (Rank prev : ranks()) {
            if (prev.number() > r.number()) break;
            for (String g : prev.glyphs()) MagicApi.learnGlyph(sp, IsekaiMahou.id(g));
        }
        HollowEvents.syncLook(sp);
        ceremony(sp, r);
    }

    private static void ceremony(ServerPlayer sp, Rank r) {
        ServerLevel sl = sp.serverLevel();
        Vec3 at = sp.position();
        MagicData d = MagicApi.get(sp);
        int rgb = Hollow.ceroRgb(d);
        MutableComponent title = Hollow.msg("evolve_title", Hollow.roman(r.number()));
        MutableComponent sub = Hollow.rankTitle(r.number());
        for (ServerPlayer viewer : sl.players()) {
            if (viewer.distanceToSqr(sp) <= 32 * 32) Cutscenes.play(viewer, "ascension", sp, title, sub, Hollow.REIATSU_RGB, 80, viewer == sp);
        }
        Announce.send(sp, Hollow.msg("evolve_announce", sp.getDisplayName(), sub), Hollow.REIATSU_RGB, Announce.STYLE_SYSTEM);
        Rewards.toast(sp, title, sub, new ItemStack(Items.BONE_BLOCK), Hollow.REIATSU_RGB);
        sp.sendSystemMessage(Hollow.msg("evolve", Hollow.roman(r.number()), sub, r.xp()).withColor(Hollow.RGB));
        sp.sendSystemMessage(Component.translatable("hollow.isekai_mahou.rank_tip." + r.number()).withStyle(ChatFormatting.GRAY));
        List<HollowTech> techs = techsAt(r.number());
        if (!techs.isEmpty()) {
            MutableComponent list = Component.empty();
            for (int i = 0; i < techs.size(); i++) {
                if (i > 0) list.append(", ");
                list.append(techs.get(i).displayName().withColor(Hollow.REIATSU_RGB));
            }
            sp.sendSystemMessage(Hollow.msg("tech_unlocked", list).withStyle(ChatFormatting.GOLD));
        }
        // Arrancar: a máscara se parte (rasga o rosto); antes, o corpo cresce/ruge
        MahouAnims.play(sp, r.number() == Hollow.ARRANCAR ? MahouAnims.HOLLOW_MASK_TEAR
                : r.number() >= Hollow.VASTO_LORDE ? MahouAnims.TRANSFORM_ROAR : MahouAnims.TRANSFORM_RISE);
        // casulo de Reiatsu que estoura + onda no chão + tremor
        HollowNetwork.fx(sp, HollowNetwork.RESURRECCION, at, Vec3.ZERO, 5F + r.number() * 1.5F, rgb);
        HollowNetwork.fxNear(sl, HollowNetwork.SHAKE, sp.getId(), at, Vec3.ZERO, 0.6F + 0.15F * r.number(), rgb, 32);
        sl.sendParticles(HollowFx.BLACK, at.x, at.y + 1, at.z, 30, 0.6, 1.0, 0.6, 0.05);
        sl.sendParticles(HollowFx.REIATSU, at.x, at.y + 1, at.z, 30, 0.6, 1.0, 0.6, 0.05);
        HollowFx.sound(sl, at, HollowSounds.ROAR.get(), 1.4F, 1.1F - 0.1F * r.number());
        HollowFx.sound(sl, at, SoundEvents.WARDEN_ROAR, 0.8F, 0.9F);
        HollowFx.sound(sl, at, SoundEvents.BONE_BLOCK_BREAK, 1.0F, 0.5F);
    }

    private HollowRanks() {}
}
