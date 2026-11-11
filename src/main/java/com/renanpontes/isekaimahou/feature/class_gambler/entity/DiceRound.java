package com.renanpontes.isekaimahou.feature.class_gambler.entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.renanpontes.isekaimahou.feature.class_gambler.GbFx;
import com.renanpontes.isekaimahou.feature.class_gambler.luck.LuckService;
import com.renanpontes.isekaimahou.glyph.SpellContext;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.phys.Vec3;

/**
 * Uma jogada da forma Dados: os dados rolam, e quando todos param a soma decide a explosão.
 * <ul>
 *   <li>poder = soma / 7 (média 1×), raio = 2,5 + soma × 0,25 blocos (antes de Expandir/Concentrar);</li>
 *   <li>Dobradinha (faces iguais) ×1,5; Olhos de Cobra (1+1) ×0,3; Doze (6+6) ×2,5 e +15 de Tensão.</li>
 * </ul>
 * A Sorte de quem joga dá chance de rolar de novo um dado baixo; Dados Viciados fazem toda face ser ≥ 3.
 */
public final class DiceRound {
    private final SpellContext ctx;
    private final boolean loaded;
    private final int luck;
    private final Vec3 dir;
    private final float baseRadius;
    private final Map<GamblerDiceEntity, Integer> faces = new LinkedHashMap<>();
    private final List<GamblerDiceEntity> dice = new ArrayList<>();
    private boolean done;

    public DiceRound(SpellContext ctx, boolean loaded, int luck, Vec3 dir, float baseRadius) {
        this.ctx = ctx;
        this.loaded = loaded;
        this.luck = luck;
        this.dir = dir;
        this.baseRadius = baseRadius;
    }

    public void add(GamblerDiceEntity d) {
        dice.add(d);
    }

    /** Face sorteada para um dado (com Sorte e Dados Viciados). */
    int faceFor(GamblerDiceEntity d) {
        int f = loaded ? 3 + d.getRandom().nextInt(4) : 1 + d.getRandom().nextInt(6);
        if (f <= 2 && luck > 0 && d.getRandom().nextFloat() < 0.06F * luck) f = Math.max(f, 1 + d.getRandom().nextInt(6));
        return f;
    }

    void settled(GamblerDiceEntity d) {
        faces.put(d, d.face());
        if (done || faces.size() < dice.size()) return;
        done = true;
        resolve();
    }

    /** Um dado sumiu antes de parar (chunk descarregado, comando): a rodada resolve com os que sobraram. */
    void lost(GamblerDiceEntity d) {
        if (done || !dice.remove(d)) return;
        if (!dice.isEmpty() && faces.size() >= dice.size()) {
            done = true;
            resolve();
        }
    }

    private void resolve() {
        ServerLevel level = ctx.level();
        int sum = 0;
        Vec3 center = Vec3.ZERO;
        List<Integer> values = new ArrayList<>(faces.values());
        for (Map.Entry<GamblerDiceEntity, Integer> e : faces.entrySet()) {
            sum += e.getValue();
            center = center.add(e.getKey().position());
        }
        center = center.scale(1.0 / Math.max(1, faces.size()));
        int n = Math.max(1, values.size());
        float mult = sum / (3.5F * n);
        boolean doubles = values.size() >= 2 && values.stream().distinct().count() == 1;
        String special = null;
        if (doubles && values.get(0) == 1) {
            mult = 0.3F;
            special = "snake_eyes";
        } else if (doubles && values.get(0) == 6) {
            mult = 2.5F;
            special = "boxcars";
            if (ctx.casterPlayer() != null && LuckService.isGambler(ctx.casterPlayer())) LuckService.addFever(ctx.casterPlayer(), 15);
        } else if (doubles) {
            mult *= 1.5F;
            special = "doubles";
        }
        float radius = ctx.formStats().radius(baseRadius + sum * 0.25F);
        SpellContext c = ctx.copy(ctx.powerMult() * mult);
        c.resolveArea(center, radius, null, false);
        c.impact(center, dir, null);
        c.end(center, dir);
        int rgb = mult >= 1.5F ? GbFx.GOLD : mult < 0.6F ? 0x6A6478 : ctx.color() & 0xFFFFFF;
        GbFx.blast(level, center, radius, rgb);
        GbFx.sound(level, center, mult >= 1.5F ? SoundEvents.NOTE_BLOCK_BELL.value() : SoundEvents.NOTE_BLOCK_SNARE.value(), 1.0F,
                0.8F + Math.min(1F, mult) * 0.6F);
        ServerPlayer sp = ctx.casterPlayer();
        if (sp != null) sp.displayClientMessage(message(values, sum, mult, special), true);
    }

    private static Component message(List<Integer> values, int sum, float mult, String special) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) sb.append(" + ");
            sb.append(values.get(i));
        }
        MutableComponent m = Component.translatable("message.isekai_mahou.gambler.dice", sb.toString(), sum,
                String.format(java.util.Locale.ROOT, "%.2f", mult));
        if (special != null) {
            m = Component.translatable(String.join("", "message.isekai_mahou.gambler.dice.", special)).append(" ").append(m);
        }
        return m.withStyle(mult >= 1.5F ? ChatFormatting.GOLD : mult < 0.6F ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE);
    }
}
