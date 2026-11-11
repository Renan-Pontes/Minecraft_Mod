package com.renanpontes.isekaimahou.feature.class_rogue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.CoreRegistries;
import com.renanpontes.isekaimahou.core.Element;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Announce;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.feature.class_rogue.entity.RogueAfterimageEntity;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Estados das técnicas de anime do Ladino (servidor):
 * <ul>
 *   <li><b>Espreitar</b> (homenagem ao aventureiro azarado que rouba calcinhas): invisível e esquecido pelos monstros
 *   (quem não estiver colado perde o alvo); atacar ou levar dano revela — e o ataque que revela é pelas costas;</li>
 *   <li><b>Velocidade Divina</b> (homenagem ao corpo elétrico do assassino de cabelo branco): reflexo automático — o
 *   primeiro golpe que viria a cada 1 s é DESVIADO (fica uma imagem residual), você aparece nas costas de quem atacou e
 *   contra-ataca com raio; golpes corpo a corpo eletrocutam;</li>
 *   <li><b>Maldição de Um Corte</b> (homenagem à espada amaldiçoada que mata com um corte): depois de 3 s o veneno
 *   amaldiçoado mata criaturas comuns; chefes e jogadores perdem uma parte grande da vida.</li>
 * </ul>
 * Também entrega as técnicas novas para quem já tinha ranks do Livro-Razão (login). Limpo ao sair/parar o servidor.
 */
public final class RogueTechniques {
    /** Pergaminhos das lendas do pacote já entregues. */
    public static final String LEGENDS_FLAG = "rogue.tech_legends";
    /** Distância em que um monstro ainda "sente" quem está espreitando. */
    public static final double LURK_SENSE = 2.5;
    public static final int GODSPEED_DODGE_GAP = 20;
    public static final float GODSPEED_COUNTER = 6.0F;
    public static final int CURSE_DELAY = 60;
    private static final int CAP = 128;

    private record Lurk(LivingEntity e, long until) {}

    private static final class God {
        final LivingEntity e;
        final long until;
        @Nullable final SpellContext ctx;
        @Nullable final SpellStats stats;
        long nextDodge;

        God(LivingEntity e, long until, @Nullable SpellContext ctx, @Nullable SpellStats stats) {
            this.e = e;
            this.until = until;
            this.ctx = ctx;
            this.stats = stats;
        }
    }

    private record Curse(LivingEntity target, SpellContext ctx, SpellStats stats, long fireAt) {}

    private static final Map<UUID, Lurk> LURK = new HashMap<>();
    private static final Map<UUID, God> GODSPEED = new HashMap<>();
    private static final Map<UUID, Curse> CURSES = new HashMap<>();

    public static void register() {
        NeoForge.EVENT_BUS.addListener(RogueTechniques::onChangeTarget);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, RogueTechniques::onIncoming);
        NeoForge.EVENT_BUS.addListener(RogueTechniques::onDamaged);
    }

    // =====================================================================================
    //  Espreitar
    // =====================================================================================
    public static void lurk(LivingEntity e, int ticks) {
        if (!(e.level() instanceof ServerLevel sl) || (LURK.size() >= CAP && !LURK.containsKey(e.getUUID()))) return;
        LURK.put(e.getUUID(), new Lurk(e, sl.getGameTime() + ticks));
        e.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false, true));
        forgetMe(sl, e);
        RogueFx.vanish(sl, e.position());
        RogueFx.sound(sl, e.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.7F, 0.8F);
        RogueFx.actionbar(e, Component.translatable("message.isekai_mahou.class_rogue.lurk", ticks / 20), RogueFx.PURPLE);
    }

    public static boolean lurking(LivingEntity e) {
        Lurk l = LURK.get(e.getUUID());
        return l != null && e.level().getGameTime() <= l.until();
    }

    /** Sai da espreita (atacou ou levou dano). */
    private static void reveal(LivingEntity e, boolean attacked) {
        if (LURK.remove(e.getUUID()) == null) return;
        e.removeEffect(MobEffects.INVISIBILITY);
        if (e.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, e.getX(), e.getY() + 1, e.getZ(), 10, 0.3, 0.5, 0.3, 0.02);
            RogueFx.sound(sl, e.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 0.6F, 1.4F);
        }
        if (attacked) RogueCombat.startAmbush(e, 2);
        RogueFx.actionbar(e, Component.translatable(attacked ? "message.isekai_mahou.class_rogue.lurk_strike"
                : "message.isekai_mahou.class_rogue.lurk_broken"), attacked ? RogueFx.BLOOD : 0x9A9AB0);
    }

    /** Monstros perto que miram em {@code e} e não estão colados perdem o alvo. */
    private static void forgetMe(ServerLevel sl, LivingEntity e) {
        for (Mob mob : sl.getEntitiesOfClass(Mob.class, e.getBoundingBox().inflate(24))) {
            if (mob.getTarget() == e && mob.distanceTo(e) > LURK_SENSE && !Statuses.isBossLike(mob)) mob.setTarget(null);
        }
    }

    private static void onChangeTarget(LivingChangeTargetEvent event) {
        if (LURK.isEmpty()) return;
        LivingEntity t = event.getNewAboutToBeSetTarget();
        if (t == null || !lurking(t)) return;
        LivingEntity mob = event.getEntity();
        if (mob.distanceTo(t) > LURK_SENSE && !Statuses.isBossLike(mob)) event.setNewAboutToBeSetTarget(null);
    }

    // =====================================================================================
    //  Velocidade Divina
    // =====================================================================================
    public static void godspeed(LivingEntity e, @Nullable SpellContext ctx, @Nullable SpellStats stats, int ticks) {
        if (!(e.level() instanceof ServerLevel sl) || (GODSPEED.size() >= CAP && !GODSPEED.containsKey(e.getUUID()))) return;
        GODSPEED.put(e.getUUID(), new God(e, sl.getGameTime() + ticks, ctx, stats));
        e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, ticks, 2, false, false, true));
        e.addEffect(new MobEffectInstance(MobEffects.JUMP, ticks, 1, false, false, true));
        e.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, ticks, 1, false, false, true));
        Vec3 c = RogueFx.center(e);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 40, 0.5, 0.9, 0.5, 0.4);
        sl.sendParticles(RogueFx.dust(0x9FE8FF, 1.4F), c.x, c.y, c.z, 30, 0.5, 0.9, 0.5, 0);
        RogueFx.sound(sl, c, SoundEvents.LIGHTNING_BOLT_THUNDER, 0.6F, 2.0F);
        RogueFx.sound(sl, c, SoundEvents.BEACON_POWER_SELECT, 1.0F, 1.8F);
        RogueFx.actionbar(e, Component.translatable("message.isekai_mahou.class_rogue.godspeed", ticks / 20), 0x9FE8FF);
    }

    public static boolean godspeedActive(LivingEntity e) {
        God g = GODSPEED.get(e.getUUID());
        return g != null && e.level().getGameTime() <= g.until;
    }

    // =====================================================================================
    //  Maldição de Um Corte
    // =====================================================================================
    public static void curse(LivingEntity target, SpellContext ctx, SpellStats stats) {
        if (!(target.level() instanceof ServerLevel sl) || (CURSES.size() >= CAP && !CURSES.containsKey(target.getUUID()))) return;
        Curse old = CURSES.get(target.getUUID());
        long fire = old != null ? old.fireAt() : sl.getGameTime() + CURSE_DELAY;
        CURSES.put(target.getUUID(), new Curse(target, ctx, stats, fire));
        RogueFx.sound(sl, target.position(), SoundEvents.ELDER_GUARDIAN_CURSE, 0.6F, 1.5F);
    }

    /** A maldição chega ao coração. */
    private static void fireCurse(ServerLevel sl, Curse c) {
        LivingEntity t = c.target();
        if (!t.isAlive()) return;
        SpellContext ctx = c.ctx();
        LivingEntity caster = ctx.caster();
        Vec3 at = RogueFx.center(t);
        sl.sendParticles(RogueFx.dust(0x2A0008, 2.2F), at.x, at.y, at.z, 50, 0.4, 0.7, 0.4, 0);
        sl.sendParticles(RogueFx.dust(RogueFx.BLOOD, 1.6F), at.x, at.y, at.z, 40, 0.4, 0.7, 0.4, 0.05);
        sl.sendParticles(ParticleTypes.SQUID_INK, at.x, at.y, at.z, 20, 0.3, 0.5, 0.3, 0.05);
        RogueFx.sound(sl, at, SoundEvents.ELDER_GUARDIAN_CURSE, 1.0F, 0.7F);
        RogueFx.sound(sl, at, SoundEvents.WITHER_BREAK_BLOCK, 0.6F, 1.6F);
        if (t instanceof Player || Statuses.isBossLike(t)) {
            // ninguém morre na hora: chefes e jogadores perdem uma parte grande da vida e murcham
            float part = t instanceof Player ? 0.30F : 0.12F;
            float amount = Math.min(400F, t.getMaxHealth() * part) * Math.max(0.5F, c.stats().power() * ctx.powerMult());
            t.invulnerableTime = 0;
            t.hurt(ctx.damageSource(null), amount);
            if (t.isAlive()) t.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1), caster);
            return;
        }
        if (execute(t, ctx.damageSource(null))) {
            if (caster instanceof ServerPlayer sp) {
                MagicApi.addCounter(sp, "rogue.one_cut", 1);
                Announce.send(sp, Component.translatable("announce.isekai_mahou.rogue_one_cut.kill"), RogueFx.BLOOD, Announce.STYLE_SPELL);
            }
        }
    }

    /** Mata de verdade (crédito para quem causou): golpe enorme; se sobrar, outro sem absorção. */
    public static boolean execute(LivingEntity t, DamageSource src) {
        float amount = t.getHealth() + t.getAbsorptionAmount() + t.getMaxHealth() * 4F + 100F;
        t.invulnerableTime = 0;
        t.hurt(src, amount);
        if (t.isAlive() && !t.isDeadOrDying()) {
            t.invulnerableTime = 0;
            t.setAbsorptionAmount(0F);
            t.hurt(src, amount * 4F);
        }
        return !t.isAlive() || t.isDeadOrDying();
    }

    // =====================================================================================
    //  Eventos
    // =====================================================================================
    private static void onIncoming(LivingIncomingDamageEvent event) {
        LivingEntity victim = event.getEntity();
        if (victim.level().isClientSide) return;
        DamageSource src = event.getSource();
        // quem espreitava atacou: revela, e este golpe conta como pelas costas
        if (!LURK.isEmpty() && src.getEntity() instanceof LivingEntity attacker && attacker != victim && lurking(attacker)) {
            reveal(attacker, true);
        }
        if (!LURK.isEmpty() && lurking(victim) && event.getAmount() > 0F) reveal(victim, false);
        // Velocidade Divina: desvia e contra-ataca
        if (GODSPEED.isEmpty()) return;
        God g = GODSPEED.get(victim.getUUID());
        if (g == null || !(victim.level() instanceof ServerLevel sl) || sl.getGameTime() > g.until || sl.getGameTime() < g.nextDodge) return;
        if (src.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || !(src.getEntity() instanceof LivingEntity attacker) || attacker == victim
                || !attacker.isAlive() || attacker.distanceToSqr(victim) > 24 * 24) return;
        g.nextDodge = sl.getGameTime() + GODSPEED_DODGE_GAP;
        event.setCanceled(true);
        Vec3 old = victim.position();
        sl.addFreshEntity(RogueAfterimageEntity.still(sl, victim, old, victim.getYRot(), 14, 0x9FE8FF,
                RogueAfterimageEntity.POSE_DASH, true));
        boolean behind = RogueCombat.teleportBehind(sl, victim, attacker, 24);
        Vec3 c = RogueFx.center(attacker);
        RogueFx.line(sl, old.add(0, 1, 0), victim.position().add(0, 1, 0), RogueFx.dust(0x9FE8FF, 0.9F), 0.4);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 24, 0.4, 0.6, 0.4, 0.3);
        RogueFx.sound(sl, c, SoundEvents.TRIDENT_THUNDER.value(), 0.5F, 1.8F);
        if (behind && g.ctx != null && g.stats != null) {
            attacker.invulnerableTime = 0;
            g.ctx.damage(attacker, GODSPEED_COUNTER, g.stats, null, Element.LIGHTNING);
            if (attacker.isAlive()) Statuses.apply(attacker, Statuses.Kind.SHOCKED, 1, 60, victim, 1F);
        }
        RogueFx.actionbar(victim, Component.translatable("message.isekai_mahou.class_rogue.godspeed_dodge"), 0x9FE8FF);
    }

    /** Golpes corpo a corpo de quem está na Velocidade Divina eletrocutam. */
    private static void onDamaged(LivingDamageEvent.Post event) {
        if (GODSPEED.isEmpty()) return;
        DamageSource src = event.getSource();
        if (!(src.getEntity() instanceof LivingEntity attacker) || src.getDirectEntity() != attacker
                || (!src.is(DamageTypes.PLAYER_ATTACK) && !src.is(DamageTypes.MOB_ATTACK))) return;
        if (!godspeedActive(attacker) || src.is(CoreRegistries.SPELL_DAMAGE)) return;
        LivingEntity t = event.getEntity();
        if (!t.isAlive() || !(t.level() instanceof ServerLevel sl)) return;
        Statuses.apply(t, Statuses.Kind.SHOCKED, 1, 60, attacker, 1F);
        Vec3 c = RogueFx.center(t);
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 10, 0.3, 0.4, 0.3, 0.2);
    }

    // =====================================================================================
    //  Tique
    // =====================================================================================
    public static void tick(MinecraftServer server) {
        int t = server.getTickCount();
        if (!LURK.isEmpty()) {
            Iterator<Lurk> it = LURK.values().iterator();
            while (it.hasNext()) {
                Lurk l = it.next();
                LivingEntity e = l.e();
                if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel sl) || sl.getGameTime() > l.until()) {
                    it.remove();
                    if (!e.isRemoved()) RogueFx.actionbar(e, Component.translatable("message.isekai_mahou.class_rogue.lurk_end"), 0x9A9AB0);
                    continue;
                }
                if (t % 10 == 0) forgetMe(sl, e);
                if (t % 20 == 0) sl.sendParticles(ParticleTypes.SMOKE, e.getX(), e.getY() + 0.1, e.getZ(), 2, 0.2, 0.02, 0.2, 0.005);
            }
        }
        if (!GODSPEED.isEmpty()) {
            Iterator<God> it = GODSPEED.values().iterator();
            while (it.hasNext()) {
                God g = it.next();
                LivingEntity e = g.e;
                if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel sl) || sl.getGameTime() > g.until) {
                    it.remove();
                    continue;
                }
                if (t % 2 == 0) {
                    double a = t * 0.7;
                    for (int i = 0; i < 2; i++) {
                        double ang = a + i * Math.PI;
                        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, e.getX() + Math.cos(ang) * 0.45,
                                e.getY() + 0.2 + (t % 18) * 0.1, e.getZ() + Math.sin(ang) * 0.45, 1, 0, 0, 0, 0);
                    }
                }
                if (t % 5 == 0) {
                    sl.sendParticles(RogueFx.dust(0x9FE8FF, 0.8F), e.getX(), e.getY() + 1.0, e.getZ(), 3, 0.3, 0.6, 0.3, 0);
                }
                if (t % 30 == 0) RogueFx.sound(sl, e.position(), SoundEvents.BEEHIVE_WORK, 0.25F, 2.0F);
            }
        }
        if (!CURSES.isEmpty()) {
            List<Curse> due = new ArrayList<>();
            Iterator<Curse> it = CURSES.values().iterator();
            while (it.hasNext()) {
                Curse c = it.next();
                LivingEntity e = c.target();
                if (e.isRemoved() || !e.isAlive() || !(e.level() instanceof ServerLevel sl)) {
                    it.remove();
                    continue;
                }
                long now = sl.getGameTime();
                if (now >= c.fireAt()) {
                    it.remove();
                    due.add(c);
                    continue;
                }
                if (t % 4 == 0) {
                    // marcas da maldição subindo pelo corpo
                    float k = 1F - (c.fireAt() - now) / (float) CURSE_DELAY;
                    double y = e.getY() + e.getBbHeight() * Mth.clamp(k, 0F, 1F);
                    for (int i = 0; i < 4; i++) {
                        double ang = t * 0.4 + i * Math.PI / 2;
                        double r = e.getBbWidth() * 0.6;
                        sl.sendParticles(RogueFx.dust(i % 2 == 0 ? 0x2A0008 : RogueFx.BLOOD, 1.0F), e.getX() + Math.cos(ang) * r, y,
                                e.getZ() + Math.sin(ang) * r, 1, 0, 0, 0, 0);
                    }
                }
                if (t % 20 == 0) RogueFx.sound(sl, e.position(), SoundEvents.WARDEN_HEARTBEAT, 1.0F, 1.2F);
            }
            for (Curse c : due) {
                if (c.target().level() instanceof ServerLevel sl) fireCurse(sl, c);
            }
        }
    }

    // =====================================================================================
    //  Entrega para saves antigos
    // =====================================================================================
    public static void catchUp(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (d.path() != MagicPath.ROGUE) return;
        int rank = RogueRanks.rank(d);
        List<Glyph> learned = new ArrayList<>();
        for (int r = 1; r <= rank; r++) {
            for (ResourceLocation id : RogueRanks.glyphsFor(r)) {
                Glyph g = GlyphRegistry.get(id);
                if (g == null || d.knowsGlyph(id)) continue;
                MagicApi.learnGlyphSilently(sp, id);
                learned.add(g);
            }
        }
        if (rank >= RogueRanks.MAX_RANK && !d.hasFlag(LEGENDS_FLAG)) {
            d.setFlag(LEGENDS_FLAG, true);
            d.markDirty();
            Rewards.giveAll(sp, legendScrolls());
        }
        if (learned.isEmpty()) return;
        MutableComponent names = Component.empty();
        for (int i = 0; i < learned.size(); i++) {
            if (i > 0) names.append(Component.literal(", "));
            names.append(learned.get(i).displayName().withColor(RogueFx.PURPLE));
        }
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.class_rogue.tech_catchup", names).withStyle(ChatFormatting.LIGHT_PURPLE));
    }

    public static List<ItemStack> legendScrolls() {
        List<ItemStack> out = new ArrayList<>();
        for (String id : RogueGlyphs.TECH_LEGENDS) {
            ItemStack st = Legends.scroll(id);
            if (!st.isEmpty()) out.add(st);
        }
        return out;
    }

    public static void forget(UUID id) {
        LURK.remove(id);
        GODSPEED.remove(id);
    }

    public static void clear() {
        LURK.clear();
        GODSPEED.clear();
        CURSES.clear();
    }

    private RogueTechniques() {}
}
