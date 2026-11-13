package com.renanpontes.isekaimahou.feature.glyphs_classes_b.augment;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.class_enchanter.Enchants;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbFx;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbIds;
import com.renanpontes.isekaimahou.feature.glyphs_classes_b.GcbUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

/**
 * Aumento do Encantador — <b>Runa Gravada</b>: todo inimigo que o glifo modificado ferir ganha uma runa por 6 s (9 s se
 * quem conjurou é Encantador). O próximo golpe CORPO A CORPO nele — seu ou de um aliado — detona a runa: os efeitos do
 * estágio se repetem no alvo com 60% do poder (75% para o Encantador). Uma runa por alvo; detonar não grava outra.
 */
public final class EngravedRune {
    public static final int TICKS = 120;
    public static final float POWER = 0.6F;
    public static final float POWER_ENCHANTER = 0.75F;
    private static final int MAX_RUNES = 256;

    private record Rune(SpellContext ctx, float power, long until) {}

    private static final Map<UUID, Rune> RUNES = new HashMap<>();
    /** true enquanto uma runa detona (o dano dela não grava runa nova). */
    private static boolean detonating;

    public static void onSpellDamage(SpellDamageEvent.Post e) {
        if (detonating || !e.landed() || e.killed()) return;
        if (GcbUtil.here(e.stats(), e.ctx().formStats(), GcbIds.ENGRAVED_RUNE) <= 0) return;
        LivingEntity target = e.target();
        SpellContext ctx = e.ctx();
        if (ctx.caster() == null || !target.isAlive() || ctx.isAlly(target)) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        if (RUNES.size() >= MAX_RUNES && !RUNES.containsKey(target.getUUID())) return;
        boolean enchanter = Enchants.isEnchanter(ctx.caster());
        int ticks = enchanter ? TICKS * 3 / 2 : TICKS;
        RUNES.put(target.getUUID(), new Rune(ctx, ctx.powerMult() * (enchanter ? POWER_ENCHANTER : POWER), sl.getGameTime() + ticks));
        Vec3 c = GcbFx.center(target);
        GcbFx.ring(sl, c, target.getBbWidth() * 0.8 + 0.3, 10, GcbFx.dust(GcbFx.ENCHANTER, 0.8F));
        sl.sendParticles(ParticleTypes.ENCHANT, c.x, c.y + 0.4, c.z, 10, 0.3, 0.4, 0.3, 0.6);
        GcbFx.sound(sl, c, SoundEvents.ENCHANTMENT_TABLE_USE, 0.5F, 1.6F);
    }

    /** Golpe corpo a corpo (de quem gravou ou de um aliado) num alvo com runa: detona. */
    public static void onDamage(LivingDamageEvent.Post e) {
        if (RUNES.isEmpty() || detonating) return;
        LivingEntity target = e.getEntity();
        Rune rune = RUNES.get(target.getUUID());
        if (rune == null) return;
        DamageSource src = e.getSource();
        if (src.is(CoreRegistries.SPELL_DAMAGE) || !(src.getEntity() instanceof LivingEntity attacker) || src.getDirectEntity() != attacker) return;
        if (!(target.level() instanceof ServerLevel sl)) return;
        if (sl.getGameTime() > rune.until()) {
            RUNES.remove(target.getUUID());
            return;
        }
        LivingEntity caster = rune.ctx().caster();
        if (caster == null || caster.isRemoved()) {
            RUNES.remove(target.getUUID());
            return;
        }
        if (attacker != caster && !rune.ctx().isAlly(attacker)) return;
        RUNES.remove(target.getUUID());
        // Próximo tick: fora do meio do cálculo do golpe
        SpellScheduler.schedule(1, () -> detonate(sl, rune, target, attacker));
    }

    private static void detonate(ServerLevel sl, Rune rune, LivingEntity target, Entity attacker) {
        if (!target.isAlive() || target.level() != sl) return;
        Vec3 c = GcbFx.center(target);
        GcbFx.ring(sl, c, 1.1, 16, GcbFx.dust(GcbFx.ENCHANTER, 1.2F));
        GcbFx.ring(sl, c.add(0, 0.5, 0), 0.6, 10, GcbFx.dust(0xFFFFFF, 0.8F));
        sl.sendParticles(ParticleTypes.ENCHANTED_HIT, c.x, c.y, c.z, 14, 0.35, 0.4, 0.35, 0.25);
        GcbFx.sound(sl, c, SoundEvents.AMETHYST_BLOCK_BREAK, 1.0F, 0.8F);
        GcbFx.sound(sl, c, SoundEvents.ENCHANTMENT_TABLE_USE, 0.8F, 0.6F);
        detonating = true;
        try {
            rune.ctx().copy(rune.power()).resolveOnEntity(target, attacker);
        } finally {
            detonating = false;
        }
    }

    /** A cada 10 ticks: brilho das runas ativas (no máx. 24 desenhadas) e limpeza das vencidas. */
    public static void tick(MinecraftServer server) {
        if (RUNES.isEmpty() || server.getTickCount() % 10 != 0) return;
        int drawn = 0;
        Iterator<Map.Entry<UUID, Rune>> it = RUNES.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Rune> en = it.next();
            ServerLevel sl = en.getValue().ctx().level();
            if (sl.getGameTime() > en.getValue().until()) {
                it.remove();
                continue;
            }
            Entity t = sl.getEntity(en.getKey());
            if (t == null || !t.isAlive()) {
                it.remove();
                continue;
            }
            if (drawn >= 24) continue;
            drawn++;
            Vec3 c = t.position().add(0, t.getBbHeight() + 0.35, 0);
            double a = (sl.getGameTime() % 40) / 40.0 * Math.PI * 2;
            sl.sendParticles(GcbFx.dust(GcbFx.ENCHANTER, 0.7F), c.x + Math.cos(a) * 0.4, c.y, c.z + Math.sin(a) * 0.4, 1, 0, 0, 0, 0);
            sl.sendParticles(GcbFx.dust(GcbFx.ENCHANTER, 0.7F), c.x - Math.cos(a) * 0.4, c.y, c.z - Math.sin(a) * 0.4, 1, 0, 0, 0, 0);
        }
    }

    public static void forget(UUID entity) {
        RUNES.remove(entity);
    }

    public static void clear() {
        RUNES.clear();
        detonating = false;
    }

    private EngravedRune() {}
}
