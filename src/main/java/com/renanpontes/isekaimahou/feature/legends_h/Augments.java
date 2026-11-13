package com.renanpontes.isekaimahou.feature.legends_h;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.event.SpellCastEvent;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.legends_h.jojo.TimeSkip;
import com.renanpontes.isekaimahou.feature.legends_h.mha.FullCowl;
import com.renanpontes.isekaimahou.glyph.ParsedSpell;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * As regras dos AUMENTOS de anime do legends_h (servidor), aplicadas na hora da conjuração — por isso funcionam em
 * QUALQUER magia, com qualquer forma, e somam com maestria/mutações:
 * <ul>
 *   <li><b>Boost Gear</b>: cada vez que você conjura de novo uma magia com Boost em até 2,5 s (ou enquanto canaliza),
 *       "Boost!" — o poder dobra o bônus a cada passo: ×1 → ×1,25 → ×1,5 → ×2 → ×3. Teto pelo Círculo (C1–3: ×1,5;
 *       C4–5: ×2; C6+: ×3). Parar 2,5 s zera.</li>
 *   <li><b>One For All</b>: 1 cópia = 20%, 2 = 45%, 3 = 100%: poder ×(1 + 1,5 × %). Acima da porcentagem segura do
 *       seu Círculo (Full Cowl ligado: +10%), o corpo quebra depois da magia — dano em si mesmo (nunca mata) e braço
 *       quebrado.</li>
 *   <li><b>Genki Dama</b>: junta a energia dos seres vivos em 32 blocos — aliados contam 3, animais/aldeões/bichos de
 *       estimação 1 (máx. 30 pontos) — poder ×(1 + 0,05 por ponto), até ×2,5. A magia só fere INIMIGOS.</li>
 *   <li><b>Epitáfio</b>: quem a magia acerta fica previsto por 6 s (Estender +) e o primeiro ataque dele em você é
 *       esquivado (ver {@link TimeSkip}).</li>
 * </ul>
 * O efeito "Transfer!" ({@link LhRegistry#BOOSTED}) também é aplicado aqui: +15% de dano por nível.
 */
public final class Augments {
    public static final float[] BOOST_STEPS = {1.0F, 1.25F, 1.5F, 2.0F, 3.0F};
    public static final int BOOST_WINDOW = 50;
    public static final float[] OFA_PERCENT = {0.20F, 0.45F, 1.0F};

    /** Por conjurador: {último tick com Boost, passo atual}. */
    private static final Map<UUID, long[]> BOOST = new HashMap<>();
    /** One For All usado em cada conjuração (para o preço no fim). */
    private static final Map<SpellContext, Float> OFA = new WeakHashMap<>();

    // =====================================================================================
    //  Leitura dos aumentos numa magia
    // =====================================================================================

    /** Maior quantidade do aumento {@code id} em algum glifo da magia (cada estágio conta). */
    public static int count(ParsedSpell spell, ResourceLocation id) {
        int n = 0;
        for (ParsedSpell s : spell.stages()) {
            n = Math.max(n, s.formStats().custom(id));
            for (ParsedSpell.EffectEntry e : s.effects()) n = Math.max(n, e.stats().custom(id));
        }
        return n;
    }

    /** Teto de passos do Boost pelo Círculo. */
    public static int boostCap(int circle) {
        return circle <= 3 ? 2 : circle <= 5 ? 3 : 4;
    }

    /** Passo do Boost do conjurador agora (0 = nenhum). Lido pelo Transfer e pelo Balance Breaker. */
    public static int boostStep(LivingEntity e) {
        long[] b = BOOST.get(e.getUUID());
        if (b == null || e.level().getGameTime() - b[0] > BOOST_WINDOW) return 0;
        return (int) b[1];
    }

    /** Porcentagem do One For All de {@code copies} cópias. */
    public static float ofaPercent(int copies) {
        return copies <= 0 ? 0F : OFA_PERCENT[Math.min(OFA_PERCENT.length, copies) - 1];
    }

    // =====================================================================================
    //  Conjuração
    // =====================================================================================

    public static void onCastPre(SpellCastEvent.Pre event) {
        SpellContext ctx = event.getContext();
        LivingEntity caster = event.getCaster();
        if (caster == null) return;
        ParsedSpell spell = ctx.spell();
        float mult = 1F;
        ServerLevel level = ctx.level();

        if (count(spell, LhIds.BOOST_GEAR) > 0) {
            long now = level.getGameTime();
            long[] b = BOOST.get(caster.getUUID());
            int cap = boostCap(LhUtil.circle(caster));
            int step = b != null && now - b[0] <= BOOST_WINDOW ? Math.min(cap, (int) b[1] + 1) : 0;
            if (!(caster instanceof Player)) step = Math.min(step, 1);
            BOOST.put(caster.getUUID(), new long[] {now, step});
            mult *= BOOST_STEPS[step];
            Vec3 hand = caster.getEyePosition().add(caster.getLookAngle().scale(0.6)).add(0, -0.4, 0);
            level.sendParticles(LhUtil.dust(0x3CFF6A, 1.0F + 0.3F * step), hand.x, hand.y, hand.z, 4 + 2 * step, 0.12, 0.12, 0.12, 0);
            if (step > 0) {
                boolean top = step >= cap;
                LhUtil.sound(level, caster.position(), SoundEvents.NOTE_BLOCK_BELL.value(), 0.9F, 0.8F + 0.15F * step);
                LhUtil.sound(level, caster.position(), SoundEvents.ARMOR_EQUIP_NETHERITE.value(), 0.7F, 1.4F);
                if (top) ElementFx.shockwave(level, caster.position().add(0, 0.2, 0), 0xD0102A, 3F, 12, null);
                LhUtil.actionbar(caster, Component.translatable(top ? "message.isekai_mahou.legends_h.boost_top"
                                : "message.isekai_mahou.legends_h.boost", fmt(BOOST_STEPS[step]))
                        .withStyle(top ? ChatFormatting.RED : ChatFormatting.GREEN));
            }
        }

        int ofa = count(spell, LhIds.ONE_FOR_ALL);
        if (ofa > 0) {
            float p = ofaPercent(ofa);
            mult *= 1F + 1.5F * p;
            OFA.put(ctx, p);
            Vec3 c = LhUtil.center(caster);
            level.sendParticles(LhUtil.dust(FullCowl.GREEN, 1.0F + p), c.x, c.y, c.z, 6 + Math.round(10 * p), 0.4, 0.6, 0.4, 0);
            LhUtil.sound(level, c, SoundEvents.TRIDENT_THUNDER.value(), 0.4F + 0.5F * p, 1.8F - 0.6F * p);
        }

        if (count(spell, LhIds.GENKI_DAMA) > 0) mult *= genki(ctx, caster);

        if (mult != 1F) ctx.setPowerMult(ctx.powerMult() * mult);
    }

    public static void onCastPost(SpellCastEvent.Post event) {
        Float p = OFA.remove(event.getContext());
        LivingEntity c = event.getCaster();
        if (p == null || c == null || !c.isAlive()) return;
        float safe = FullCowl.safe(LhUtil.circle(c)) + (FullCowl.active(c) ? 0.1F : 0F);
        if (p <= safe + 1.0E-4F) {
            LhUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_h.ofa_ok", Math.round(p * 100))
                    .withStyle(ChatFormatting.GREEN));
            return;
        }
        float over = p - safe;
        LhUtil.strain(c, over * 0.3F * c.getMaxHealth());
        FullCowl.breakArm(c, Math.round(100 + 300 * over));
        LhUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_h.ofa_broke", Math.round(p * 100), Math.round(safe * 100))
                .withStyle(ChatFormatting.RED));
    }

    /** Genki Dama: pede energia a tudo que vive em volta. Devolve o multiplicador. */
    private static float genki(SpellContext ctx, LivingEntity caster) {
        ServerLevel level = ctx.level();
        Vec3 top = caster.position().add(0, caster.getBbHeight() + 2.5, 0);
        int points = 0, drawn = 0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, new AABB(caster.position(), caster.position()).inflate(32),
                e -> e.isAlive() && e != caster && !e.isSpectator())) {
            int w;
            if (e instanceof Player && ctx.isAlly(e)) w = 3;
            else if (e instanceof OwnableEntity own && own.getOwnerUUID() != null && ctx.isAlly(e)) w = 2;
            else if (e instanceof Animal || e instanceof AbstractVillager) w = 1;
            else continue;
            points += w;
            if (drawn < 12) {
                drawn++;
                LhUtil.line(level, LhUtil.center(e), top, 0x9FD8FF, 0.8F, 18);
            }
            if (e instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.legends_h.genki_ask", caster.getDisplayName())
                        .withStyle(ChatFormatting.AQUA), true);
            }
            if (points >= 30) break;
        }
        points = Math.min(30, points);
        float mult = caster instanceof Player ? Math.min(2.5F, 1F + 0.05F * points) : 1.2F;
        ElementFx.gatherAt(level, top, com.renanpontes.isekaimahou.core.Element.LIGHT, 0xBFE8FF, 2F + points * 0.08F, 4, 30);
        level.sendParticles(LhUtil.dust(0xDFF4FF, 2.5F), top.x, top.y, top.z, 20 + points, 0.6 + points * 0.03, 0.6 + points * 0.03,
                0.6 + points * 0.03, 0);
        LhUtil.sound(level, caster.position(), SoundEvents.BEACON_POWER_SELECT, 1.2F, 0.7F);
        LhUtil.actionbar(caster, Component.translatable("message.isekai_mahou.legends_h.genki", points, fmt(mult))
                .withStyle(ChatFormatting.AQUA));
        return mult;
    }

    // =====================================================================================
    //  Dano
    // =====================================================================================

    public static void onSpellDamagePre(SpellDamageEvent.Pre e) {
        LivingEntity c = e.ctx().caster();
        if (c == null) return;
        MobEffectInstance b = c.getEffect(LhRegistry.BOOSTED);
        if (b != null) e.setAmount(e.getAmount() * (1F + 0.15F * (b.getAmplifier() + 1)));
        // Genki Dama: "não fere quem tem o coração puro" — só inimigos
        if (e.stats().custom(LhIds.GENKI_DAMA) > 0 || e.ctx().formStats().custom(LhIds.GENKI_DAMA) > 0) {
            if (!LhUtil.isFoe(e.ctx(), e.target())) e.setCanceled(true);
        }
    }

    public static void onSpellDamagePost(SpellDamageEvent.Post e) {
        if (!e.landed()) return;
        SpellStats s = e.stats();
        SpellContext ctx = e.ctx();
        LivingEntity c = ctx.caster();
        if (c == null || e.target() == c) return;
        int ep = Math.max(s.custom(LhIds.EPITAPH), ctx.formStats().custom(LhIds.EPITAPH));
        if (ep > 0 && e.target().isAlive()) TimeSkip.foresee(c, e.target(), s.duration(120), true);
    }

    /** "Transfer!" também vale para golpes corpo a corpo e flechas (as magias já contam no {@link #onSpellDamagePre}). */
    public static void onIncomingDamage(LivingIncomingDamageEvent e) {
        Entity src = e.getSource().getEntity();
        if (!(src instanceof LivingEntity a) || e.getSource().is(CoreRegistries.SPELL_DAMAGE)) return;
        MobEffectInstance b = a.getEffect(LhRegistry.BOOSTED);
        if (b != null) e.setAmount(e.getAmount() * (1F + 0.15F * (b.getAmplifier() + 1)));
    }

    static String fmt(float v) {
        return String.format(java.util.Locale.ROOT, "%.2f", v).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    /** O Boost foi entregue ("Transfer!"): o conjurador volta ao passo zero. */
    public static void resetBoost(LivingEntity e) {
        BOOST.remove(e.getUUID());
    }

    public static void forget(UUID id) {
        BOOST.remove(id);
    }

    public static void sweep(long now) {
        BOOST.values().removeIf(b -> now - b[0] > 1200);
    }

    public static void clear() {
        BOOST.clear();
        OFA.clear();
    }

    private Augments() {}
}
