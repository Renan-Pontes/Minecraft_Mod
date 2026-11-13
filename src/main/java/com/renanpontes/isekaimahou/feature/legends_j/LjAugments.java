package com.renanpontes.isekaimahou.feature.legends_j;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.EntityTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Comportamento dos 3 aumentos de anime (os glifos só marcam {@code SpellStats.custom}; ver {@link LjGlyphs}).
 * Valem colocados depois da FORMA (todos os efeitos da magia) ou depois de um EFEITO (só aquele efeito).
 * <ul>
 *   <li><b>Kokusen / Raio Negro</b> — RITMO: depois de cada conjuração com ele, quando a recarga acaba, você ouve um
 *       "tim" (só você) num momento um pouco aleatório (0,4–1,2 s depois). Conjurar a MESMA magia dentro de 0,25 s
 *       depois do tim (0,5 s na Zona) = RAIO NEGRO: os próximos 6 acertos dessa magia (em até 4 s) causam ×2,5
 *       (no máximo +60 por acerto), com faíscas negras e vermelhas — e você entra na ZONA por 10 s (janela maior e
 *       +10% de dano nas magias com Raio Negro). Apertar ANTES do tim só conjura normal (e o tim recomeça).</li>
 *   <li><b>Starburst Stream</b> — cada acerto vira uma rajada de 16 golpes: mais 15 cortes azuis e brancos de duas
 *       lâminas em 1,5 s (14 × 5,5% + o 16º golpe com 18% do dano original = +95%). Você fica "preso" na sequência
 *       (Lentidão IV 1,7 s). Até 4 rajadas ao mesmo tempo por conjurador.</li>
 *   <li><b>Excalibur</b> — o golpe da luz prometida: +0,5 s de conjuração, Expandir +2 e Perfurar +3 no que vier antes
 *       (no Feixe: um raio MUITO mais grosso que atravessa a fila toda), +15% de dano (+40% em mortos-vivos) e, num
 *       feixe, uma onda dourada enorme por cima.</li>
 * </ul>
 * Os golpes extras do Starburst saem por {@code hurt} direto (não por {@code ctx.damage}): não disparam os eventos de
 * dano de magia de novo, então nada entra em laço.
 */
public final class LjAugments {
    // ---------- Raio Negro ----------
    static final int BF_WINDOW = 5;
    static final int BF_WINDOW_ZONE = 10;
    static final int BF_HITS = 6;
    static final float BF_MULT = 2.5F;
    static final float BF_MAX_BONUS = 60F;
    static final int ZONE_TICKS = 200;

    // ---------- Starburst ----------
    static final int SB_HITS = 15;
    static final float SB_SMALL = 0.055F;
    static final float SB_FINAL = 0.18F;
    static final int SB_MAX_PER_CASTER = 4;

    private static final class Rhythm {
        long cueAt = -1;
        long zoneUntil;
        long flashUntil;
        int flashHits;
    }

    private static final Map<UUID, Rhythm> RHYTHM = new HashMap<>();
    private static final Set<UUID> FLURRY_TARGETS = new HashSet<>();
    private static final Map<UUID, Integer> FLURRY_COUNT = new HashMap<>();

    static boolean has(SpellContext ctx, @Nullable SpellStats st, ResourceLocation id) {
        return (st != null && st.custom(id) > 0) || ctx.formStats().custom(id) > 0;
    }

    private static boolean spellHas(ParsedSpell spell, ResourceLocation id) {
        return spell.anyStats(s -> s.custom(id) > 0);
    }

    // =====================================================================================
    //  Conjuração
    // =====================================================================================
    static void onCastPre(SpellCastEvent.Pre e) {
        if (!(e.getCaster() instanceof ServerPlayer sp)) return;
        ParsedSpell spell = e.getContext().spell();
        if (!spellHas(spell, LjGlyphs.BLACK_FLASH)) return;
        Rhythm r = RHYTHM.computeIfAbsent(sp.getUUID(), k -> new Rhythm());
        ServerLevel l = sp.serverLevel();
        long now = l.getGameTime();
        long press = now - Math.max(0, spell.castTicks());
        boolean zone = r.zoneUntil >= now;
        int window = zone ? BF_WINDOW_ZONE : BF_WINDOW;
        boolean perfect = r.cueAt > 0 && press >= r.cueAt - 1 && press <= r.cueAt + window;
        r.cueAt = -1;
        if (!perfect) return;
        r.flashUntil = now + 80;
        r.flashHits = BF_HITS;
        r.zoneUntil = now + ZONE_TICKS;
        Vec3 chest = sp.position().add(0, sp.getBbHeight() * 0.6, 0);
        LjFx.sparks(l, chest, LjFx.FLASH_BLACK, LjFx.FLASH_RED, 1.6, 6);
        LjUtil.sound(l, chest, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.8F, 1.6F);
        LjUtil.sound(l, chest, SoundEvents.ANVIL_LAND, 0.5F, 1.8F);
        sp.displayClientMessage(Component.translatable(zone ? "message.isekai_mahou.legends_j.black_flash_zone"
                : "message.isekai_mahou.legends_j.black_flash").withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
    }

    static void onCastPost(SpellCastEvent.Post e) {
        SpellContext ctx = e.getContext();
        ParsedSpell spell = ctx.spell();
        LivingEntity caster = e.getCaster();
        if (caster instanceof ServerPlayer sp && spellHas(spell, LjGlyphs.BLACK_FLASH)) scheduleCue(sp, spell);
        if (caster != null && spellHas(spell, LjGlyphs.EXCALIBUR)) excaliburWave(ctx, caster);
    }

    /** Marca o próximo "tim" do Raio Negro (depois da recarga, num momento um pouco aleatório). */
    private static void scheduleCue(ServerPlayer sp, ParsedSpell spell) {
        Rhythm r = RHYTHM.computeIfAbsent(sp.getUUID(), k -> new Rhythm());
        long now = sp.serverLevel().getGameTime();
        boolean zone = r.zoneUntil >= now;
        int delay = Math.max(0, spell.cooldown()) + 8 + sp.getRandom().nextInt(zone ? 9 : 17);
        long cue = now + delay;
        r.cueAt = cue;
        SpellScheduler.schedule(delay, () -> {
            Rhythm cur = RHYTHM.get(sp.getUUID());
            if (cur == null || cur.cueAt != cue || sp.isRemoved() || !sp.isAlive()) return;
            // Só quem conjurou ouve o tim; a faísca na mão todo mundo vê
            sp.playNotifySound(SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.PLAYERS, 0.9F, 1.9F);
            Vec3 hand = sp.getEyePosition().add(sp.getLookAngle().scale(0.5)).add(0, -0.3, 0);
            LjFx.sparks(sp.serverLevel(), hand, LjFx.FLASH_BLACK, LjFx.FLASH_RED, 0.5, 2);
        });
    }

    /** Excalibur num feixe: a onda dourada por cima do raio + o grito. */
    private static void excaliburWave(SpellContext ctx, LivingEntity caster) {
        ServerLevel l = ctx.level();
        String form = ctx.spell().form().id().getPath();
        Vec3 eye = caster.getEyePosition();
        Vec3 dir = caster.getLookAngle();
        Vec3 hand = eye.add(dir.scale(0.6)).add(0, -0.25, 0);
        ElementFx.flash(l, hand, 0xFFF6D8, 3F);
        ElementFx.pillar(l, caster.position(), 0xFFE27A, 0.8F, 5F, 16);
        LjUtil.sound(l, hand, SoundEvents.BEACON_ACTIVATE, 1.2F, 1.5F);
        LjUtil.sound(l, hand, SoundEvents.BELL_RESONATE, 1.0F, 1.2F);
        if (form.contains("beam")) {
            Vec3 end = LjUtil.lookPoint(caster, 24 + 4 * Math.max(0, ctx.formStats().speed));
            ElementFx.beam(l, hand, end, Element.LIGHT, 0xFFE27A, 1.6F, 5);
            ElementFx.shockwave(l, hand, 0xFFE27A, 2.5F, 14, dir);
            ElementFx.shake(l, hand, 24, 0.8F, 10);
        }
        LjUtil.actionbar(caster, Component.translatable("message.isekai_mahou.legends_j.excalibur").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
    }

    // =====================================================================================
    //  Dano
    // =====================================================================================
    static void onDamagePre(SpellDamageEvent.Pre e) {
        SpellContext ctx = e.ctx();
        LivingEntity t = e.target();
        if (has(ctx, e.stats(), LjGlyphs.EXCALIBUR)) {
            e.setAmount(e.getAmount() * (t.getType().is(EntityTypeTags.UNDEAD) ? 1.4F : 1.15F));
        }
        if (!(ctx.caster() instanceof ServerPlayer sp) || !has(ctx, e.stats(), LjGlyphs.BLACK_FLASH)) return;
        Rhythm r = RHYTHM.get(sp.getUUID());
        if (r == null) return;
        long now = ctx.level().getGameTime();
        if (r.flashUntil >= now && r.flashHits > 0) {
            r.flashHits--;
            float amt = e.getAmount();
            e.setAmount(Math.min(amt * BF_MULT, amt + BF_MAX_BONUS));
            ServerLevel l = ctx.level();
            Vec3 c = LjUtil.center(t);
            LjFx.sparks(l, c, LjFx.FLASH_BLACK, LjFx.FLASH_RED, 1.4 + t.getBbHeight() * 0.3, 5);
            ElementFx.flash(l, c, LjFx.FLASH_RED, 2.2F);
            ElementFx.shockwave(l, c, LjFx.FLASH_BLACK, 1.8F, 8, null);
            LjUtil.sound(l, c, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.5F);
            LjUtil.sound(l, c, SoundEvents.LIGHTNING_BOLT_IMPACT, 0.6F, 1.9F);
        } else if (r.zoneUntil >= now) {
            e.setAmount(e.getAmount() * 1.1F);
        }
    }

    static void onDamagePost(SpellDamageEvent.Post e) {
        if (!e.landed() || !(e.dealt() > 0F)) return;
        SpellContext ctx = e.ctx();
        if (!has(ctx, e.stats(), LjGlyphs.STARBURST)) return;
        LivingEntity c = ctx.caster();
        LivingEntity t = e.target();
        if (c == null || !t.isAlive() || t == c) return;
        if (FLURRY_TARGETS.contains(t.getUUID())) return;
        if (FLURRY_COUNT.getOrDefault(c.getUUID(), 0) >= SB_MAX_PER_CASTER) return;
        starburst(ctx, c, t, e.getAmount());
    }

    private static void starburst(SpellContext ctx, LivingEntity c, LivingEntity t, float base) {
        ServerLevel l = ctx.level();
        UUID tid = t.getUUID(), cid = c.getUUID();
        FLURRY_TARGETS.add(tid);
        FLURRY_COUNT.merge(cid, 1, Integer::sum);
        if (LjUtil.once(ctx, "starburst_root")) {
            c.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 34, 3, false, false));
            LjUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_j.starburst").withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD));
        }
        for (int i = 1; i <= SB_HITS; i++) {
            final int hit = i;
            SpellScheduler.schedule(i * 2, () -> {
                boolean last = hit == SB_HITS;
                if (t.isAlive() && t.level() == l && !t.isRemoved()) {
                    float amt = base * (last ? SB_FINAL : SB_SMALL);
                    t.invulnerableTime = 0;
                    t.hurt(ctx.damageSource(null), amt);
                    t.invulnerableTime = 0;
                    Vec3 cc = LjUtil.center(t);
                    double a = hit * 2.3;
                    Vec3 dir = new Vec3(Math.cos(a), (hit % 2 == 0 ? 0.6 : -0.6), Math.sin(a)).normalize();
                    int rgb = hit % 2 == 0 ? 0x7FC8FF : 0xF4F8FF;
                    ElementFx.slash(l, cc, dir, Element.ARCANE, rgb, last ? 3.2F : 1.8F, last ? 4 : 2);
                    if (hit % 2 == 1 || last) {
                        LjUtil.sound(l, cc, SoundEvents.PLAYER_ATTACK_SWEEP, 0.6F, 1.1F + hit * 0.04F);
                    }
                    if (last) {
                        ElementFx.flash(l, cc, 0xD8ECFF, 2.5F);
                        LjUtil.sound(l, cc, SoundEvents.PLAYER_ATTACK_CRIT, 1.0F, 0.8F);
                    }
                }
                if (last) {
                    FLURRY_TARGETS.remove(tid);
                    FLURRY_COUNT.computeIfPresent(cid, (k, v) -> v <= 1 ? null : v - 1);
                }
            });
        }
    }

    // =====================================================================================
    //  Limpeza
    // =====================================================================================
    static void forget(UUID player) {
        RHYTHM.remove(player);
    }

    static void clear() {
        RHYTHM.clear();
        FLURRY_TARGETS.clear();
        FLURRY_COUNT.clear();
    }

    private LjAugments() {}
}
