package com.renanpontes.isekaimahou.feature.legends_h.jojo;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.ElementFx;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.ArenaBounds;
import com.renanpontes.isekaimahou.core.api.Cutscenes;
import com.renanpontes.isekaimahou.feature.arcane.ArcaneUtil;
import com.renanpontes.isekaimahou.feature.legends_h.LhNet;
import com.renanpontes.isekaimahou.feature.legends_h.LhRegistry;
import com.renanpontes.isekaimahou.feature.legends_h.LhUtil;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * KILLER QUEEN — BITES THE DUST (servidor, em memória; v1.1). Uma bomba no TEMPO, em três atos:
 * <ol>
 *   <li><b>Plantar</b>: a bomba vai no alvo (criatura, bloco atingido ou você mesmo). Na hora, o tempo de TODOS perto
 *       da bomba (16 blocos, Expandir +, até 24) e o seu (sempre) é fotografado: posição, rotação, dimensão, arena e
 *       vida; o seu também guarda fome, saturação e mana. Pavio T = 8 s (Estender +, até 20 s).</li>
 *   <li><b>A bomba explode</b> (fim do pavio): cutscene "Bites the Dust" + tela rosa com relógio ao contrário para
 *       quem estava na foto, e todos voltam para onde estavam (mesma dimensão e mesma arena; chefes não se movem).
 *       Você volta para a VIDA que tinha (fome e um pouco de mana — até 30% da máxima — também voltam); aliados
 *       recuperam a vida perdida até a da foto; inimigos só voltam de lugar (a vida deles NÃO volta).</li>
 *   <li><b>Segunda explosão</b>: T segundos depois de voltar (o mesmo T — Estender aumenta as duas esperas), o ALVO
 *       explode: 10 × poder de dano nele (chefes levam normal) e 3 × poder nos inimigos a até 3 blocos (Expandir +).
 *       Nunca fere você nem aliados, nunca quebra blocos, e não fere ninguém em zona segura (cidade). Bomba em você:
 *       explode no inimigo da foto mais perto de você (se não houver, não explode). Bomba num bloco: explode no ponto.</li>
 * </ol>
 * Se você ia MORRER com o pavio aceso, o tempo volta na hora em vez disso (1 vez a cada 2 min). Uma bomba por
 * conjurador (a nova substitui a velha). Nunca desfaz mortes, itens, blocos nem XP.
 */
public final class Rewind {
    public static final int PINK = 0xFF7AC8;
    public static final int DEATH_COOLDOWN = 2400;
    /** Dano base da 2ª explosão no alvo (o {@code ctx.damage} multiplica pelo poder). */
    public static final float BLAST = 10F;
    /** Dano base nos inimigos em volta da 2ª explosão. */
    public static final float SPLASH = 3F;
    public static final int CUTSCENE = 50;
    public static final int MAX_SNAP = 24;

    /** A foto de uma pessoa no instante em que a bomba foi plantada. */
    private static final class Snap {
        final LivingEntity e;
        final boolean caster;
        final boolean ally;
        final Vec3 pos;
        final float yRot, xRot;
        final ResourceKey<Level> dim;
        final Optional<String> arena;
        final float health;
        final int food;
        final float saturation;
        final float mana;

        Snap(LivingEntity e, boolean caster, boolean ally) {
            this.e = e;
            this.caster = caster;
            this.ally = ally;
            this.pos = e.position();
            this.yRot = e.getYRot();
            this.xRot = e.getXRot();
            this.dim = e.level().dimension();
            this.arena = ArenaBounds.at(e.level(), e.position()).map(ArenaBounds.Arena::id);
            this.health = e.getHealth();
            this.food = e instanceof ServerPlayer sp ? sp.getFoodData().getFoodLevel() : 0;
            this.saturation = e instanceof ServerPlayer sp ? sp.getFoodData().getSaturationLevel() : 0;
            this.mana = caster && e instanceof ServerPlayer sp ? MagicApi.get(sp).mana() : -1F;
        }
    }

    private static final class Bomb {
        final SpellContext ctx;
        final SpellStats stats;
        final LivingEntity caster;
        final int fuse;
        final float blastRadius;
        final boolean selfAnchor;
        @Nullable
        final LivingEntity anchor;
        Vec3 anchorPos;
        final List<Snap> snaps = new ArrayList<>();
        /** false = pavio aceso (1º ato); true = já voltou, esperando a 2ª explosão. */
        boolean armed;
        long next;
        @Nullable
        LivingEntity target;
        Vec3 targetPos;

        Bomb(SpellContext ctx, SpellStats stats, LivingEntity caster, @Nullable LivingEntity anchor, Vec3 anchorPos,
             boolean selfAnchor, int fuse, float blastRadius, long next) {
            this.ctx = ctx;
            this.stats = stats;
            this.caster = caster;
            this.anchor = anchor;
            this.anchorPos = anchorPos;
            this.targetPos = anchorPos;
            this.selfAnchor = selfAnchor;
            this.fuse = fuse;
            this.blastRadius = blastRadius;
            this.next = next;
        }
    }

    /** Uma bomba por conjurador. */
    private static final Map<UUID, Bomb> BOMBS = new HashMap<>();
    private static final Map<UUID, Long> DEATH_CD = new HashMap<>();

    /**
     * Planta a bomba (substitui a anterior do mesmo conjurador).
     * @param anchor     criatura com a bomba (null = bloco/ponto {@code at})
     * @param selfAnchor a bomba está em você (Si Mesmo)
     * @param fuse       T em ticks (pavio e espera da 2ª explosão)
     * @param radius     raio da foto
     * @param blastRadius raio da 2ª explosão
     */
    public static void plant(SpellContext ctx, SpellStats stats, @Nullable LivingEntity anchor, Vec3 at, boolean selfAnchor,
                             int fuse, double radius, float blastRadius) {
        LivingEntity caster = ctx.caster();
        if (caster == null || !(caster.level() instanceof ServerLevel level)) return;
        Bomb old = BOMBS.remove(caster.getUUID());
        if (old != null) disarm(old);
        Bomb b = new Bomb(ctx, stats, caster, anchor, at, selfAnchor, fuse, blastRadius, level.getGameTime() + fuse);

        // A foto: você sempre; e todo mundo perto da bomba (mais perto primeiro)
        b.snaps.add(new Snap(caster, true, true));
        double r2 = radius * radius;
        List<LivingEntity> around = level.getEntitiesOfClass(LivingEntity.class, new net.minecraft.world.phys.AABB(at, at).inflate(radius),
                e -> e != caster && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStand) && e.distanceToSqr(at) <= r2);
        around.sort(Comparator.comparingDouble(e -> e.distanceToSqr(at)));
        for (LivingEntity e : around) {
            if (b.snaps.size() > MAX_SNAP) break;
            b.snaps.add(new Snap(e, false, ctx.isAlly(e)));
        }
        BOMBS.put(caster.getUUID(), b);

        if (anchor != null) anchor.addEffect(new MobEffectInstance(LhRegistry.REWIND_MARK, fuse, 0, false, false, true));
        Vec3 c = anchor != null ? LhUtil.center(anchor) : at;
        Vec3 feet = anchor != null ? anchor.position() : at;
        ElementFx.runeCircle(level, feet.add(0, 0.05, 0), PINK, 1.2F, 30, 3);
        level.sendParticles(LhUtil.dust(PINK, 1.3F), c.x, c.y, c.z, 10, 0.2, 0.2, 0.2, 0);
        LhUtil.sound(level, c, SoundEvents.STONE_BUTTON_CLICK_ON, 1.0F, 1.6F);
        LhUtil.sound(level, c, SoundEvents.BELL_BLOCK, 0.6F, 1.8F);
    }

    public static void tick(MinecraftServer server) {
        if (BOMBS.isEmpty()) return;
        long tick = server.getTickCount();
        List<Bomb> rewind = new ArrayList<>();
        List<Bomb> blast = new ArrayList<>();
        for (Iterator<Bomb> it = BOMBS.values().iterator(); it.hasNext(); ) {
            Bomb b = it.next();
            LivingEntity c = b.caster;
            ServerLevel level = b.ctx.level();
            // Quem plantou saiu/morreu: a bomba some
            if (c.isRemoved() || !c.isAlive()) {
                it.remove();
                disarm(b);
                continue;
            }
            long now = level.getGameTime();
            if (!b.armed) {
                if (b.anchor != null && b.anchor.isAlive() && !b.anchor.isRemoved() && b.anchor.level() == level) {
                    b.anchorPos = b.anchor.position();
                }
                if (now >= b.next) {
                    rewind.add(b);
                    continue;
                }
                if (tick % 10 == 0) marker(level, b.anchor, b.anchorPos);
                if (tick % 20 == 0) countdown(c, "btd_fuse", b.next - now);
            } else {
                if (b.target != null && b.target.isAlive() && !b.target.isRemoved() && b.target.level() == level) {
                    b.targetPos = b.target.position();
                }
                if (now >= b.next) {
                    it.remove();
                    blast.add(b);
                    continue;
                }
                if (tick % 10 == 0) marker(level, b.target, b.targetPos);
                if (tick % 20 == 0) countdown(c, "btd_armed", b.next - now);
            }
        }
        for (Bomb b : rewind) {
            if (!rewind(b, false)) BOMBS.remove(b.caster.getUUID(), b);
        }
        for (Bomb b : blast) blast(b);
        if (tick % 600 == 0) {
            long now = server.overworld().getGameTime();
            DEATH_CD.values().removeIf(t -> t < now);
        }
    }

    private static void marker(ServerLevel level, @Nullable LivingEntity on, Vec3 at) {
        Vec3 c = on != null && on.isAlive() && on.level() == level ? on.position().add(0, on.getBbHeight() + 0.4, 0) : at.add(0, 0.3, 0);
        level.sendParticles(LhUtil.dust(PINK, 0.9F), c.x, c.y, c.z, 2, 0.05, 0.05, 0.05, 0);
    }

    private static void countdown(LivingEntity c, String key, long ticksLeft) {
        LhUtil.actionbar(c, Component.translatable("message.isekai_mahou.legends_h." + key, String.format("%.0f", Math.ceil(ticksLeft / 20.0)))
                .withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    /** Ia morrer com o pavio aceso: o tempo volta na hora (1 vez a cada 2 min). */
    public static void onDeath(LivingDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (v.level().isClientSide || e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return;
        Bomb b = BOMBS.get(v.getUUID());
        if (b == null || b.armed || b.caster != v) return;
        long now = v.level().getGameTime();
        Long cd = DEATH_CD.get(v.getUUID());
        if (cd != null && cd > now) return;
        DEATH_CD.put(v.getUUID(), now + DEATH_COOLDOWN);
        e.setCanceled(true);
        v.setHealth(Math.max(1F, v.getMaxHealth() * 0.2F));
        if (!rewind(b, true)) BOMBS.remove(v.getUUID(), b);
    }

    /**
     * 2º ato: a bomba explode e o tempo volta para a foto.
     * @return true se ainda haverá a 2ª explosão (a bomba fica armada)
     */
    private static boolean rewind(Bomb b, boolean death) {
        ServerLevel level = b.ctx.level();
        LivingEntity caster = b.caster;
        if (b.anchor != null) b.anchor.removeEffect(LhRegistry.REWIND_MARK);
        // O clique e a explosão rosa no ponto da bomba
        Vec3 p = b.anchor != null && b.anchor.isAlive() && b.anchor.level() == level ? LhUtil.center(b.anchor) : b.anchorPos;
        ElementFx.flash(level, p, 0xFFB0E0, 3F);
        ElementFx.shockwave(level, p, PINK, 6F, 14, null);
        LhUtil.sound(level, p, SoundEvents.STONE_BUTTON_CLICK_OFF, 1.2F, 1.4F);
        LhUtil.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 1.6F);

        // Cutscene para quem está perto (o dono trava, os outros só assistem)
        Entity focus = b.anchor != null && b.anchor.isAlive() && b.anchor.level() == level ? b.anchor : caster;
        Cutscenes.playNear(focus, 32, "bites_the_dust", Component.literal("Bites the Dust"),
                Component.translatable("cutscene.isekai_mahou.legends_h.btd_sub"), PINK, CUTSCENE,
                caster instanceof ServerPlayer sp ? sp : null);

        for (Snap s : b.snaps) restore(s);

        LhUtil.actionbar(caster, Component.translatable(death ? "message.isekai_mahou.legends_h.rewind_death"
                : "message.isekai_mahou.legends_h.rewind_self").withStyle(ChatFormatting.LIGHT_PURPLE));

        // Quem explode na 2ª vez
        LivingEntity target = null;
        if (b.selfAnchor) {
            double best = Double.MAX_VALUE;
            for (Snap s : b.snaps) {
                LivingEntity e = s.e;
                if (s.caster || s.ally || !e.isAlive() || e.isRemoved() || e.level() != caster.level() || !LhUtil.isFoe(b.ctx, e)) continue;
                double d = e.distanceToSqr(caster);
                if (d < best) {
                    best = d;
                    target = e;
                }
            }
            if (target == null) return false;
        } else if (b.anchor != null) {
            target = b.anchor;
        }
        b.target = target;
        b.targetPos = target != null ? target.position() : b.anchorPos;
        b.armed = true;
        b.next = level.getGameTime() + b.fuse;
        if (target != null && target.isAlive()) {
            target.addEffect(new MobEffectInstance(LhRegistry.REWIND_MARK, b.fuse, 0, false, false, true));
        }
        return true;
    }

    /** Uma pessoa da foto volta para onde estava (você também volta para a vida que tinha). */
    private static void restore(Snap s) {
        LivingEntity e = s.e;
        if (e.isRemoved() || !e.isAlive() && !s.caster || !(e.level() instanceof ServerLevel level) || level.dimension() != s.dim) return;
        if (e instanceof ServerPlayer sp && sp.hasDisconnected()) return;
        Vec3 from = e.position();
        boolean sameArena = ArenaBounds.at(level, from).map(ArenaBounds.Arena::id).equals(s.arena);
        if (sameArena && !LhUtil.boss(e) && from.distanceToSqr(s.pos) > 0.25) {
            // O caminho desfeito: um rastro rosa corre de volta até a foto
            Vec3 h = new Vec3(0, e.getBbHeight() * 0.5, 0);
            LhUtil.line(level, from.add(h), s.pos.add(h), PINK, 1.0F, 16);
            ArcaneUtil.teleport(e, s.pos, s.yRot, s.xRot);
            e.setDeltaMovement(Vec3.ZERO);
            e.hurtMarked = true;
        }
        if (s.caster) {
            // "Você volta para a vida que estava"
            e.setHealth(Mth.clamp(s.health, 1F, e.getMaxHealth()));
        } else if (s.ally && e.getHealth() < s.health) {
            e.setHealth(Math.min(e.getMaxHealth(), s.health));
        }
        if (s.caster || s.ally) {
            e.clearFire();
            e.setAirSupply(e.getMaxAirSupply());
            e.resetFallDistance();
            if (e instanceof ServerPlayer sp) {
                if (sp.getFoodData().getFoodLevel() < s.food) sp.getFoodData().setFoodLevel(s.food);
                if (sp.getFoodData().getSaturationLevel() < s.saturation) sp.getFoodData().setSaturation(s.saturation);
                if (s.mana >= 0) {
                    MagicData d = MagicApi.get(sp);
                    float back = Math.min(Math.max(0F, s.mana - d.mana()), d.maxMana() * 0.3F);
                    if (back > 0) d.addMana(back);
                }
            }
        }
        if (e instanceof ServerPlayer sp) LhUtil.screen(sp, LhNet.REWIND, sp.getId(), CUTSCENE);
        Vec3 c = LhUtil.center(e);
        level.sendParticles(LhUtil.dust(PINK, 1.3F), c.x, c.y, c.z, 10, 0.3, 0.5, 0.3, 0);
        if (s.caster) {
            ElementFx.runeCircle(level, e.position().add(0, 0.05, 0), PINK, 2F, 26, 4);
            LhUtil.sound(level, c, SoundEvents.BELL_BLOCK, 1.2F, 0.6F);
            LhUtil.sound(level, c, SoundEvents.TRIDENT_RETURN, 1.0F, 0.6F);
        }
    }

    /** 3º ato: o alvo explode (nunca você/aliados, nunca blocos, nada em zona segura). */
    private static void blast(Bomb b) {
        ServerLevel level = b.ctx.level();
        LivingEntity t = b.target;
        boolean live = t != null && t.isAlive() && !t.isRemoved() && t.level() == level;
        Vec3 p = live ? LhUtil.center(t) : b.targetPos.add(0, 0.5, 0);
        if (t != null) t.removeEffect(LhRegistry.REWIND_MARK);
        float r = b.blastRadius;
        if (!LhUtil.safeAt(level, p)) {
            LivingEntity main = live && LhUtil.isFoe(b.ctx, t) ? t : null;
            // Bomba num bloco: quem estiver em cima do ponto leva o golpe cheio
            if (main == null && b.anchor == null && !b.selfAnchor) {
                List<LivingEntity> near = LhUtil.foesAround(b.ctx, p, 1.5, 1);
                if (!near.isEmpty()) main = near.get(0);
            }
            if (main != null) {
                main.invulnerableTime = 0;
                b.ctx.damage(main, BLAST, b.stats, null, Element.ARCANE);
            }
            for (LivingEntity e : LhUtil.foesAround(b.ctx, p, r, 12)) {
                if (e == main || e == b.caster) continue;
                e.invulnerableTime = 0;
                b.ctx.damage(e, SPLASH, b.stats, null, Element.ARCANE);
            }
        }
        ElementFx.flash(level, p, 0xFFD0E8, r);
        ElementFx.nova(level, p, Element.ARCANE, PINK, r, 3);
        level.sendParticles(LhUtil.dust(PINK, 2F), p.x, p.y, p.z, 24, r * 0.3, r * 0.3, r * 0.3, 0);
        LhUtil.sound(level, p, SoundEvents.STONE_BUTTON_CLICK_OFF, 1.2F, 1.4F);
        LhUtil.sound(level, p, SoundEvents.GENERIC_EXPLODE.value(), 1.4F, 1.1F);
    }

    /** A bomba some sem efeito (substituída ou o dono saiu). */
    private static void disarm(Bomb b) {
        if (b.anchor != null) b.anchor.removeEffect(LhRegistry.REWIND_MARK);
        if (b.target != null) b.target.removeEffect(LhRegistry.REWIND_MARK);
    }

    public static void forget(UUID id) {
        Bomb b = BOMBS.remove(id);
        if (b != null) disarm(b);
    }

    public static void clear() {
        BOMBS.clear();
        DEATH_CD.clear();
    }

    private Rewind() {}
}
