package com.renanpontes.isekaimahou.feature.legends_h.jojo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.ArenaBounds;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.feature.legends_h.LhNet;
import com.renanpontes.isekaimahou.feature.legends_h.LhRegistry;
import com.renanpontes.isekaimahou.feature.legends_h.LhUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * KILLER QUEEN — BITES THE DUST (servidor, em memória). O glifo planta uma "bomba" no tempo do alvo: o estado de
 * agora fica guardado e, quando o pavio acaba, o tempo dele VOLTA para aquele instante.
 * <ul>
 *   <li><b>Você ou um aliado</b>: volta para onde estava e recupera a vida perdida desde então (no máximo 40% da vida
 *       máxima × poder, até 60%); você também recupera fome e um pouco de mana (até 30% da máxima). Se ia MORRER com a
 *       marca, o tempo volta na hora em vez de morrer — uma vez a cada 2 minutos por pessoa.</li>
 *   <li><b>Um inimigo</b>: é puxado de volta para onde estava e fica atordoado por 1 s ("perdeu esses segundos").
 *       A vida dele NÃO volta. Chefes não são movidos (só atordoam por 0,4 s).</li>
 * </ul>
 * Nunca desfaz mortes, itens, blocos nem XP. Só volta a posição na mesma dimensão e na mesma arena (não serve para
 * entrar/sair de arenas de chefe nem de salas de torre/masmorra).
 */
public final class Rewind {
    public static final int PINK = 0xFF7AC8;
    public static final int DEATH_COOLDOWN = 2400;

    private static final class Mark {
        final LivingEntity e;
        final boolean friendly;
        final boolean self;
        @Nullable
        final LivingEntity caster;
        final Vec3 pos;
        final float yRot, xRot;
        final ResourceKey<Level> dim;
        final Optional<String> arena;
        final float health;
        final int food;
        final float saturation;
        final float mana;
        final float healCap;
        final List<Vec3> path = new ArrayList<>();
        final long end;

        Mark(LivingEntity e, @Nullable LivingEntity caster, boolean friendly, boolean self, float healCap, long end) {
            this.e = e;
            this.caster = caster;
            this.friendly = friendly;
            this.self = self;
            this.pos = e.position();
            this.yRot = e.getYRot();
            this.xRot = e.getXRot();
            this.dim = e.level().dimension();
            this.arena = ArenaBounds.at(e.level(), e.position()).map(ArenaBounds.Arena::id);
            this.health = e.getHealth();
            this.food = e instanceof ServerPlayer sp ? sp.getFoodData().getFoodLevel() : 0;
            this.saturation = e instanceof ServerPlayer sp ? sp.getFoodData().getSaturationLevel() : 0;
            this.mana = self && e instanceof ServerPlayer sp ? MagicApi.get(sp).mana() : -1F;
            this.healCap = healCap;
            this.end = end;
            path.add(pos);
        }
    }

    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    private static final Map<UUID, Long> DEATH_CD = new HashMap<>();

    /** Planta a marca (substitui uma anterior no mesmo alvo). */
    public static void mark(LivingEntity target, @Nullable LivingEntity caster, boolean friendly, boolean self, float power, int ticks) {
        if (!(target.level() instanceof ServerLevel level)) return;
        float healCap = Math.min(0.6F, 0.4F * Math.max(0.5F, power));
        MARKS.put(target.getUUID(), new Mark(target, caster, friendly, self, healCap, level.getGameTime() + ticks));
        target.addEffect(new MobEffectInstance(LhRegistry.REWIND_MARK, ticks, 0, false, false, true));
        Vec3 c = LhUtil.center(target);
        ElementFx.runeCircle(level, target.position().add(0, 0.05, 0), PINK, 1.2F, 30, 3);
        level.sendParticles(LhUtil.dust(PINK, 1.3F), c.x, c.y + target.getBbHeight() * 0.5, c.z, 10, 0.2, 0.2, 0.2, 0);
        LhUtil.sound(level, c, SoundEvents.STONE_BUTTON_CLICK_ON, 1.0F, 1.6F);
        LhUtil.sound(level, c, SoundEvents.BELL_BLOCK, 0.6F, 1.8F);
    }

    public static boolean marked(LivingEntity e) {
        return MARKS.containsKey(e.getUUID());
    }

    public static void tick(MinecraftServer server) {
        if (MARKS.isEmpty()) return;
        long tick = server.getTickCount();
        List<Mark> due = new ArrayList<>();
        for (Iterator<Mark> it = MARKS.values().iterator(); it.hasNext(); ) {
            Mark m = it.next();
            LivingEntity e = m.e;
            if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel level)) {
                it.remove();
                continue;
            }
            if (level.getGameTime() >= m.end) {
                it.remove();
                due.add(m);
                continue;
            }
            if (tick % 10 == 0 && m.path.size() < 48) m.path.add(e.position());
            if (tick % 20 == 0) {
                Vec3 c = e.position().add(0, e.getBbHeight() + 0.4, 0);
                level.sendParticles(LhUtil.dust(PINK, 0.9F), c.x, c.y, c.z, 2, 0.05, 0.05, 0.05, 0);
            }
        }
        for (Mark m : due) rewind(m, false);
        if (tick % 600 == 0) {
            long now = server.overworld().getGameTime();
            DEATH_CD.values().removeIf(t -> t < now);
        }
    }

    /** Ia morrer com a marca (aliada): o tempo volta na hora. */
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Mark m = MARKS.get(v.getUUID());
        if (m == null || !m.friendly) return;
        long now = v.level().getGameTime();
        Long cd = DEATH_CD.get(v.getUUID());
        if (cd != null && cd > now) return;
        MARKS.remove(v.getUUID());
        DEATH_CD.put(v.getUUID(), now + DEATH_COOLDOWN);
        e.setCanceled(true);
        v.setHealth(Math.max(1F, v.getMaxHealth() * 0.2F));
        rewind(m, true);
    }

    private static void rewind(Mark m, boolean death) {
        LivingEntity e = m.e;
        if (!(e.level() instanceof ServerLevel level)) return;
        e.removeEffect(LhRegistry.REWIND_MARK);
        Vec3 from = e.position();
        boolean boss = Statuses.isBossLike(e);
        boolean sameArena = ArenaBounds.at(level, e.position()).map(ArenaBounds.Arena::id).equals(m.arena);
        boolean move = level.dimension() == m.dim && sameArena && !boss && from.distanceToSqr(m.pos) > 0.25;
        if (move) ArcaneUtil.teleport(e, m.pos, m.yRot, m.xRot);
        if (m.friendly) {
            float lost = Math.max(0F, m.health - e.getHealth());
            float heal = Math.min(lost, e.getMaxHealth() * m.healCap);
            if (heal > 0) e.setHealth(Math.min(e.getMaxHealth(), e.getHealth() + heal));
            e.clearFire();
            e.setAirSupply(e.getMaxAirSupply());
            e.resetFallDistance();
            if (e instanceof ServerPlayer sp) {
                if (sp.getFoodData().getFoodLevel() < m.food) sp.getFoodData().setFoodLevel(m.food);
                if (sp.getFoodData().getSaturationLevel() < m.saturation) sp.getFoodData().setSaturation(m.saturation);
                if (m.mana >= 0) {
                    MagicData d = MagicApi.get(sp);
                    float back = Math.min(Math.max(0F, m.mana - d.mana()), d.maxMana() * 0.3F);
                    if (back > 0) d.addMana(back);
                }
                LhUtil.screen(sp, LhNet.REWIND, sp.getId(), 30);
                sp.displayClientMessage(Component.translatable(death ? "message.isekai_mahou.legends_h.rewind_death"
                        : "message.isekai_mahou.legends_h.rewind_self").withStyle(ChatFormatting.LIGHT_PURPLE), true);
            }
        } else {
            Statuses.apply(e, Statuses.Kind.STUNNED, 1, boss ? 8 : 20, m.caster, 1F);
            if (e instanceof ServerPlayer sp) LhUtil.screen(sp, LhNet.REWIND, sp.getId(), 20);
        }
        // O caminho desfeito: um rastro rosa corre de volta pelo trajeto (do fim para o começo)
        List<Vec3> path = new ArrayList<>(m.path);
        path.add(from);
        int steps = Math.min(10, path.size());
        for (int i = 0; i < steps; i++) {
            int idx = path.size() - 1 - (int) ((path.size() - 1) * (i / (float) Math.max(1, steps - 1)));
            Vec3 p = path.get(Math.max(0, idx)).add(0, e.getBbHeight() * 0.5, 0);
            BossKit.schedule(level, i, () -> level.sendParticles(LhUtil.dust(PINK, 1.4F), p.x, p.y, p.z, 6, 0.2, 0.4, 0.2, 0));
        }
        Vec3 c = LhUtil.center(e);
        ElementFx.flash(level, c, 0xFFB0E0, 3F);
        ElementFx.shockwave(level, c, PINK, 4F, 12, null);
        ElementFx.runeCircle(level, e.position().add(0, 0.05, 0), PINK, 2F, 26, 4);
        LhUtil.sound(level, c, SoundEvents.BELL_BLOCK, 1.2F, 0.6F);
        LhUtil.sound(level, c, SoundEvents.TRIDENT_RETURN, 1.0F, 0.6F);
        LhUtil.sound(level, from, SoundEvents.GENERIC_EXPLODE.value(), 0.5F, 1.8F);
    }

    public static void forget(UUID id) {
        MARKS.remove(id);
    }

    public static void clear() {
        MARKS.clear();
        DEATH_CD.clear();
    }

    private Rewind() {}
}
