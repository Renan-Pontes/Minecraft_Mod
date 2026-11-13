package com.renanpontes.isekaimahou.feature.fusions_d.effect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.classes.ResourceTraining;
import com.renanpontes.isekaimahou.feature.fusions_d.FdFx;
import com.renanpontes.isekaimahou.feature.fusions_d.FdRegistries;
import com.renanpontes.isekaimahou.feature.fusions_d.FdUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import com.renanpontes.isekaimahou.glyph.fx.Vfx;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Queimar Alma: a chama queima a ALMA, não o corpo — água e chuva não apagam. A cada segundo fere pela vida máxima
 * do alvo (4%; chefes 1,2%) e, em jogadores, consome o mana e o converte em dano. Quem morre queimando solta a alma em
 * chamas, que salta para até 2 inimigos próximos (uma vez).
 */
public final class SoulBurn {
    private static final Map<UUID, Burn> BURNS = new HashMap<>();
    private static final int MAX = 256;

    private static final class Burn {
        final SpellContext ctx;
        final ServerLevel level;
        final SpellStats hit;
        final float power;
        int ticks;
        final boolean spread;
        int age;

        Burn(SpellContext ctx, SpellStats hit, float power, int ticks, boolean spread) {
            this.ctx = ctx;
            this.level = ctx.level();
            this.hit = hit;
            this.power = power;
            this.ticks = ticks;
            this.spread = spread;
        }
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener(SoulBurn::tick);
        NeoForge.EVENT_BUS.addListener(SoulBurn::onDeath);
        NeoForge.EVENT_BUS.addListener(SoulBurn::onJoin);
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent e) -> BURNS.clear());
    }

    /** Acende (ou renova) a chama da alma no alvo. */
    public static void ignite(SpellContext ctx, LivingEntity target, int ticks, SpellStats stats, boolean spread) {
        if (BURNS.size() >= MAX && !BURNS.containsKey(target.getUUID())) return;
        Burn old = BURNS.get(target.getUUID());
        if (old != null && old.ticks > ticks && old.power >= stats.power()) return;
        // Status do núcleo bloqueados (Resguardo etc.) ou alvo imune a efeitos: a chama não pega
        if (!target.addEffect(new MobEffectInstance(FdRegistries.SOUL_BURN, ticks, 0, false, true, true), ctx.caster())
                && !target.hasEffect(FdRegistries.SOUL_BURN)) return;
        BURNS.put(target.getUUID(), new Burn(ctx, FdUtil.hitStats(stats), stats.power(), ticks, spread || (old != null && old.spread)));
        ServerLevel sl = ctx.level();
        sl.sendParticles(Vfx.soulFlame(), target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(), 24,
                target.getBbWidth() * 0.5, target.getBbHeight() * 0.4, target.getBbWidth() * 0.5, 0.06);
        FdFx.sound(sl, target.position(), SoundEvents.SOUL_ESCAPE.value(), 1.4F, 0.7F);
    }

    private static void tick(ServerTickEvent.Post e) {
        if (BURNS.isEmpty()) return;
        Iterator<Map.Entry<UUID, Burn>> it = BURNS.entrySet().iterator();
        List<Runnable> later = new ArrayList<>();
        while (it.hasNext()) {
            Map.Entry<UUID, Burn> en = it.next();
            Burn b = en.getValue();
            if (!(b.level.getEntity(en.getKey()) instanceof LivingEntity le) || !le.isAlive() || --b.ticks <= 0) {
                it.remove();
                continue;
            }
            // Purificado (leite, Purificar, Equilíbrio Absoluto...): a chama da alma se apaga junto com o efeito
            if (b.age > 2 && !le.hasEffect(FdRegistries.SOUL_BURN)) {
                it.remove();
                continue;
            }
            b.age++;
            if (b.age % 4 == 0) {
                b.level.sendParticles(Vfx.soulFlame(), le.getX(), le.getY() + le.getBbHeight() * 0.4, le.getZ(), 3,
                        le.getBbWidth() * 0.4, le.getBbHeight() * 0.3, le.getBbWidth() * 0.4, 0.02);
            }
            if (b.age % 20 != 0) continue;
            later.add(() -> burn(b, le));
        }
        // Dano fora do iterador (uma morte pode espalhar a chama e mexer no mapa)
        for (Runnable r : later) r.run();
    }

    private static void burn(Burn b, LivingEntity le) {
        float pct = FdUtil.isBoss(le) ? 0.012F : 0.04F;
        float amount = (1.5F + Math.min(30F, le.getMaxHealth() * pct)) * b.power;
        if (le instanceof Player p && !p.isCreative()) {
            MagicData d = MagicApi.get(p);
            // v1.0: drena ×3 (mana ×3); o dano extra continua o da v0.8 (0,5 por ponto de mana da escala antiga)
            float drain = Math.min(d.mana(), 6F * ResourceTraining.MANA_SCALE * b.power);
            if (drain > 0 && d.consumeMana(drain)) amount += drain * 0.5F / ResourceTraining.MANA_SCALE;
        }
        le.invulnerableTime = 0;
        b.ctx.damage(le, amount, b.hit, null);
        b.level.sendParticles(Vfx.soul(), le.getX(), le.getY() + le.getBbHeight(), le.getZ(), 2, 0.2, 0.1, 0.2, 0.03);
        FdFx.sound(b.level, le.position(), SoundEvents.SOUL_SAND_BREAK, 0.8F, 0.6F);
    }

    private static void onDeath(LivingDeathEvent e) {
        LivingEntity dead = e.getEntity();
        Burn b = BURNS.remove(dead.getUUID());
        if (b == null || b.spread || dead.level() != b.level) return;
        Vec3 c = FdUtil.center(dead);
        b.level.sendParticles(Vfx.soul(), c.x, c.y, c.z, 20, 0.3, 0.4, 0.3, 0.08);
        b.level.sendParticles(Vfx.soulFlame(), c.x, c.y, c.z, 40, 0.4, 0.5, 0.4, 0.2);
        FdFx.sound(b.level, c, SoundEvents.SOUL_ESCAPE.value(), 2F, 0.5F);
        int n = 0;
        SpellStats stats = new SpellStats();
        stats.amplify = Math.round((b.power - 1F) / 0.4F);
        for (LivingEntity next : b.ctx.enemiesAround(c, 5.5)) {
            if (next == dead || FdUtil.untouchable(next)) continue;
            FdFx.line(b.level, c, FdUtil.center(next), Vfx.soulFlame(), 3);
            ignite(b.ctx, next, 100, stats, true);
            if (++n >= 2) break;
        }
    }

    /**
     * Quem volta ao mundo (entrou de novo, trocou de dimensão, chunk recarregado) com o efeito mas sem a chama
     * rastreada não fica com um "Queimar Alma" fantasma que não queima: o efeito sai junto.
     */
    private static void onJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide() || !(e.getEntity() instanceof LivingEntity le) || !le.hasEffect(FdRegistries.SOUL_BURN)) return;
        Burn b = BURNS.get(le.getUUID());
        if (b != null && b.level == e.getLevel()) return;
        BURNS.remove(le.getUUID());
        le.removeEffect(FdRegistries.SOUL_BURN);
    }

    private SoulBurn() {}
}
