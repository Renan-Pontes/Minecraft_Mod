package com.renanpontes.isekaimahou.feature.class_steelball;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.IsekaiMahou;
import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicData;
import com.renanpontes.isekaimahou.core.api.Domains;
import com.renanpontes.isekaimahou.core.api.Legends;
import com.renanpontes.isekaimahou.core.api.Rewards;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.event.SpellDamageEvent;
import com.renanpontes.isekaimahou.feature.class_steelball.entity.LoveTrainEntity;
import com.renanpontes.isekaimahou.feature.class_steelball.glyph.LoveTrainForm;
import com.renanpontes.isekaimahou.feature.class_steelball.glyph.SbGlyphs;
import com.renanpontes.isekaimahou.glyph.Glyph;
import com.renanpontes.isekaimahou.glyph.GlyphRegistry;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellRecipe;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * LOVE TRAIN — a luz do Coração (homenagem a Steel Ball Run). Servidor.
 * <ul>
 *   <li><b>Despertar</b>: quando o Santo fica inteiro no corpo (o CORAÇÃO DO CADÁVER SANTO é sempre a última parte),
 *   o jogador aprende a forma G {@code steelball_love_train} com letreiro, sons, colunas de luz e um vislumbre da
 *   cortina; ganha também o Pergaminho Lendário "Love Train — A Luz do Coração". Vale para qualquer classe: é o Santo
 *   que escolhe o portador.</li>
 *   <li><b>A cortina</b> ({@link LoveTrainEntity}): por 12 s (×Estender, até 15 s) o portador fica envolto em luz
 *   dimensional. TODO dano ({@link SpellDamageEvent.Pre} e {@link LivingIncomingDamageEvent}) e todo efeito ruim
 *   ({@link MobEffectEvent.Applicable}) que iriam acertá-lo viram DESGRAÇA: são desviados para o inimigo mais próximo (até
 *   {@value #NEAR} blocos) ou, sem ninguém perto, para um inimigo ao acaso a até {@value #RANGE} blocos (sem ninguém,
 *   a desgraça se dissolve na luz). Projéteis se curvam para longe. Quem recebe a desgraça também recebe os efeitos da
 *   receita (1×/s por inimigo).</li>
 *   <li><b>O que atravessa a luz</b> (ataques "de dentro da luz"): Acerto Garantido de domínios
 *   ({@link Domains#sureHitOn}) — a luz falha por completo enquanto durar —, dano verdadeiro/vazio
 *   ({@code bypasses_invulnerability}) e a ROTAÇÃO DOURADA (magias com Rotação Infinita ou Retângulo Dourado).</li>
 * </ul>
 */
public final class SbLoveTrain {
    public static final int BASE_TICKS = 240;
    public static final int MAX_TICKS = 300;
    public static final int MIN_TICKS = 100;
    /** Intervalo mínimo entre pulsos dos efeitos da receita no mesmo inimigo. */
    public static final int PULSE_TICKS = 20;
    public static final double RANGE = 24.0;
    public static final double NEAR = 8.0;
    /** Raio da cortina (além da metade da largura do portador) — também usado pelo cliente. */
    public static final double CURTAIN_RADIUS = 1.15;
    /** Distância em que os projéteis começam a se curvar. */
    public static final double DEFLECT_RADIUS = 5.0;
    /** Branco-dourado da luz. */
    public static final int RGB = 0xFFF0B8;
    public static final int GOLD = 0xFFD24A;
    /** Desgraça desviada = dano × este fator × poder da forma. */
    public static final float MISFORTUNE = 1.0F;
    /** Flag gravada quando o Love Train desperta (o despertar dramático acontece uma vez só). */
    public static final String FLAG = "steelball.love_train";

    private static final class Run {
        final SpellContext ctx;
        final LivingEntity holder;
        final long until;
        final Map<UUID, Long> pulsed = new HashMap<>();
        @Nullable LoveTrainEntity curtain;
        long lastEnv = -100;
        long lastPierceMsg = -200;
        int redirected;

        Run(SpellContext ctx, LivingEntity holder, long until) {
            this.ctx = ctx;
            this.holder = holder;
            this.until = until;
        }
    }

    private static final Map<UUID, Run> RUNS = new HashMap<>();
    /** Portadores cujo golpe de magia já foi liberado no Pre (o LivingIncomingDamage não decide de novo). */
    private static final Set<Integer> PASS = new HashSet<>();
    /** > 0 enquanto uma desgraça está sendo entregue: o que acertar outro portador agora só se dissolve (sem laço). */
    private static int redirecting;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, SpellDamageEvent.Pre.class, SbLoveTrain::onSpellPre);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, LivingIncomingDamageEvent.class, SbLoveTrain::onIncoming);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOW, MobEffectEvent.Applicable.class, SbLoveTrain::onEffect);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGH, ProjectileImpactEvent.class, SbLoveTrain::onImpact);
        NeoForge.EVENT_BUS.addListener((ServerTickEvent.Post e) -> tick(e.getServer()));
    }

    public static int duration(SpellStats stats) {
        return Mth.clamp(stats.duration(BASE_TICKS), MIN_TICKS, MAX_TICKS);
    }

    @Nullable
    private static Run run(LivingEntity e) {
        Run r = RUNS.get(e.getUUID());
        if (r == null || r.holder != e || e.level() != r.ctx.level() || r.ctx.level().getGameTime() > r.until) return null;
        return r;
    }

    /** A cortina está de pé neste portador? */
    public static boolean active(LivingEntity e) {
        return run(e) != null;
    }

    // =====================================================================================
    //  Despertar (o Coração completa o Santo)
    // =====================================================================================

    /** Confere: Santo inteiro → aprende o Love Train (o despertar dramático só na primeira vez). */
    public static void check(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        if (!SbPart.complete(d)) return;
        grant(sp);
    }

    /** Ensina o Love Train (e a lenda). Na primeira vez, o despertar com letreiro, luz e som. Usado também pelo comando. */
    public static void grant(ServerPlayer sp) {
        MagicData d = MagicApi.get(sp);
        ResourceLocation id = IsekaiMahou.id(LoveTrainForm.ID);
        Glyph g = GlyphRegistry.get(id);
        if (g == null) return;
        boolean fresh = !d.hasFlag(FLAG);
        if (!d.knowsGlyph(id)) MagicApi.learnGlyphSilently(sp, id);
        String legendFlag = "steelball.legend." + SbLegends.LOVE_TRAIN;
        if (!d.hasFlag(legendFlag)) {
            d.setFlag(legendFlag, true);
            ItemStack scroll = Legends.scroll(SbLegends.LOVE_TRAIN);
            if (!scroll.isEmpty()) Rewards.give(sp, scroll);
        }
        if (fresh) {
            d.setFlag(FLAG, true);
            awaken(sp, g);
        }
        d.markDirty();
    }

    private static void awaken(ServerPlayer sp, Glyph g) {
        ServerLevel sl = sp.serverLevel();
        SbTechniques.callout(sp, Component.translatable("callout.isekai_mahou.steelball.love_train"),
                Component.translatable("callout.isekai_mahou.steelball.love_train.awaken"), RGB);
        Rewards.banner(sp, Component.translatable("banner.isekai_mahou.steelball.love_train"), GOLD);
        Rewards.toast(sp, Component.translatable("toast.isekai_mahou.steelball.love_train"), g.displayName(),
                new ItemStack(SbRegistry.PARTS.get(SbPart.FINAL).get()), GOLD);
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.steelball.love_train.awaken")
                .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));
        sp.sendSystemMessage(g.description().withStyle(ChatFormatting.GRAY));
        sp.sendSystemMessage(Component.translatable("message.isekai_mahou.steelball.love_train.howto").withStyle(ChatFormatting.YELLOW));
        Vec3 p = sp.position();
        SbFx.sound(sl, p, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        SbFx.sound(sl, p, SoundEvents.BEACON_ACTIVATE, 1.5F, 1.2F);
        SbFx.sound(sl, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 0.8F);
        SbFx.sound(sl, p, SoundEvents.BELL_RESONATE, 1.0F, 1.4F);
        // o Coração bate: 4 batidas, cada uma com uma onda de luz e as colunas subindo
        for (int k = 0; k < 4; k++) {
            final int beat = k;
            SpellScheduler.schedule(8 + k * 14, () -> {
                if (!sp.isAlive() || sp.serverLevel() != sl) return;
                Vec3 c = sp.position();
                SbFx.sound(sl, c, SoundEvents.WARDEN_HEARTBEAT, 1.8F, 0.9F + beat * 0.08F);
                SbFx.spinRing(sl, c.add(0, 0.15, 0), RGB, 1.2 + beat * 0.9, 24 + beat * 8);
                rays(sl, c, 6 + beat * 2, 10 + beat * 3, beat * 0.4F);
            });
        }
        rays(sl, p, 8, 16, 0F);
        sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1, p.z, 60, 0.5, 1.0, 0.5, 0.12);
        // um vislumbre da cortina (3 s, sem proteção)
        LoveTrainEntity.spawnPreview(sl, sp, 70);
    }

    /** Colunas de luz em volta de um ponto (raios do despertar e da conjuração). */
    private static void rays(ServerLevel sl, Vec3 base, int count, double height, float phase) {
        for (int i = 0; i < count; i++) {
            double a = phase + Math.PI * 2 * i / count;
            double r = 1.6 + (i % 2) * 0.9;
            double x = base.x + Math.cos(a) * r, z = base.z + Math.sin(a) * r;
            for (double y = 0; y < height; y += 0.55) {
                sl.sendParticles(SbFx.dust(i % 3 == 0 ? GOLD : RGB, 1.3F), x, base.y + y, z, 1, 0.03, 0.05, 0.03, 0);
            }
            sl.sendParticles(ParticleTypes.END_ROD, x, base.y + height * 0.5, z, 3, 0.05, height * 0.3, 0.05, 0.02);
        }
    }

    // =====================================================================================
    //  Conjuração
    // =====================================================================================

    /** A forma G: a cortina de luz desce sobre o conjurador. */
    public static void start(SpellContext ctx) {
        LivingEntity c = ctx.caster();
        if (c == null) return;
        ServerLevel sl = ctx.level();
        int ticks = duration(ctx.formStats());
        Run old = RUNS.remove(c.getUUID());
        if (old != null && old.curtain != null) old.curtain.discard();
        Run r = new Run(ctx, c, sl.getGameTime() + ticks);
        r.curtain = LoveTrainEntity.spawn(sl, c, ticks);
        RUNS.put(c.getUUID(), r);
        c.clearFire();
        // a desgraça que já estava no corpo vai embora junto
        List<MobEffectInstance> bad = new ArrayList<>();
        for (MobEffectInstance i : c.getActiveEffects()) {
            if (i.getEffect().value().getCategory() == MobEffectCategory.HARMFUL && !i.isInfiniteDuration()) bad.add(new MobEffectInstance(i));
        }
        for (MobEffectInstance i : bad) {
            Statuses.Kind k = Statuses.Kind.of(i.getEffect());
            if (k != null) Statuses.clear(c, k);
            else c.removeEffect(i.getEffect());
            redirectEffect(r, i, null);
        }
        Vec3 p = c.position();
        SbFx.sound(sl, p, SoundEvents.BEACON_ACTIVATE, 1.4F, 1.5F);
        SbFx.sound(sl, p, SoundEvents.BELL_BLOCK, 1.2F, 0.7F);
        SbFx.sound(sl, p, SoundEvents.AMETHYST_BLOCK_RESONATE, 1.2F, 1.2F);
        SbFx.sound(sl, p, SoundEvents.ILLUSIONER_CAST_SPELL, 0.8F, 1.6F);
        rays(sl, p, 10, 14, 0F);
        SbFx.spinRing(sl, p.add(0, 0.1, 0), RGB, 2.2, 36);
        SbFx.spinRing(sl, p.add(0, 0.1, 0), GOLD, 3.0, 40);
        sl.sendParticles(ParticleTypes.END_ROD, p.x, p.y + 1, p.z, 40, 0.4, 0.9, 0.4, 0.1);
        if (c instanceof ServerPlayer sp) {
            SbTechniques.callout(sp, Component.translatable("callout.isekai_mahou.steelball.love_train"),
                    Component.translatable("callout.isekai_mahou.steelball.love_train.sub"), RGB);
        }
        // quem está perto também vê o letreiro (é uma técnica lendária)
        for (ServerPlayer other : sl.getEntitiesOfClass(ServerPlayer.class, c.getBoundingBox().inflate(32), o -> o != c)) {
            other.displayClientMessage(Component.translatable("callout.isekai_mahou.steelball.love_train").withColor(RGB)
                    .withStyle(ChatFormatting.BOLD), true);
        }
    }

    // =====================================================================================
    //  Tick: cortina, projéteis, fim
    // =====================================================================================

    private static void tick(MinecraftServer server) {
        if (RUNS.isEmpty()) {
            PASS.clear();
            return;
        }
        for (Map.Entry<UUID, Run> entry : new ArrayList<>(RUNS.entrySet())) {
            Run r = entry.getValue();
            LivingEntity h = r.holder;
            ServerLevel sl = r.ctx.level();
            if (!h.isAlive() || h.isRemoved() || h.level() != sl || sl.getGameTime() > r.until) {
                end(entry.getKey(), r);
                continue;
            }
            int left = (int) (r.until - sl.getGameTime());
            if (r.curtain == null || r.curtain.isRemoved()) r.curtain = LoveTrainEntity.spawn(sl, h, left);
            boolean pierced = Domains.sureHitOn(h);
            r.curtain.setPierced(pierced);
            if (pierced) {
                if (h instanceof ServerPlayer sp && sl.getGameTime() - r.lastPierceMsg > 100) {
                    r.lastPierceMsg = sl.getGameTime();
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.love_train.pierced")
                            .withStyle(ChatFormatting.RED), true);
                }
                continue;
            }
            h.clearFire();
            deflect(sl, r);
            if (sl.getGameTime() % 40 == 0) SbFx.sound(sl, h.position(), SoundEvents.BEACON_AMBIENT, 0.7F, 1.6F);
            if (sl.getGameTime() % 5 == 0) {
                double a = sl.random.nextDouble() * Math.PI * 2;
                double rad = CURTAIN_RADIUS + h.getBbWidth() * 0.5;
                sl.sendParticles(SbFx.dust(sl.random.nextBoolean() ? RGB : GOLD, 1.0F), h.getX() + Math.cos(a) * rad,
                        h.getY() + sl.random.nextDouble() * h.getBbHeight(), h.getZ() + Math.sin(a) * rad, 2, 0.05, 0.3, 0.05, 0);
            }
            if (left == 60 && h instanceof ServerPlayer sp) {
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.steelball.love_train.fading")
                        .withStyle(ChatFormatting.YELLOW), true);
            }
        }
        PASS.clear();
    }

    private static void end(UUID id, Run r) {
        RUNS.remove(id, r);
        if (r.curtain != null) r.curtain.discard();
        LivingEntity h = r.holder;
        if (h.level() instanceof ServerLevel sl && h.isAlive()) {
            SbFx.sound(sl, h.position(), SoundEvents.BEACON_DEACTIVATE, 1.0F, 1.3F);
            sl.sendParticles(ParticleTypes.END_ROD, h.getX(), h.getY() + 1, h.getZ(), 24, 0.5, 0.9, 0.5, 0.06);
            if (h instanceof ServerPlayer sp) {
                sp.sendSystemMessage(Component.translatable("message.isekai_mahou.steelball.love_train.end", r.redirected)
                        .withStyle(ChatFormatting.GOLD));
            }
        }
    }

    /** Projéteis de quem não é aliado se curvam para longe da luz (nunca chegam). */
    private static void deflect(ServerLevel sl, Run r) {
        LivingEntity h = r.holder;
        Vec3 c = center(h);
        for (Projectile p : sl.getEntitiesOfClass(Projectile.class, h.getBoundingBox().inflate(DEFLECT_RADIUS), Entity::isAlive)) {
            Entity owner = p.getOwner();
            if (owner == h || (owner != null && r.ctx.isAlly(owner))) continue;
            Vec3 v = p.getDeltaMovement();
            double speed = v.length();
            if (speed < 0.05) continue;
            Vec3 to = c.subtract(p.position());
            double dist = to.length();
            if (dist > DEFLECT_RADIUS || v.dot(to) <= 0) continue;
            Vec3 dir = v.scale(1.0 / speed);
            Vec3 off = p.position().subtract(c);
            Vec3 lateral = off.subtract(dir.scale(off.dot(dir)));
            if (lateral.lengthSqr() < 1.0E-4) lateral = dir.cross(new Vec3(0, 1, 0));
            if (lateral.lengthSqr() < 1.0E-4) lateral = new Vec3(1, 0, 0);
            lateral = lateral.normalize();
            double k = Mth.clamp(1.0 - dist / DEFLECT_RADIUS, 0.15, 1.0);
            p.setDeltaMovement(dir.add(lateral.scale(0.9 * k + 0.25)).normalize().scale(speed));
            p.hurtMarked = true;
            if ((p.tickCount + p.getId()) % 3 == 0) {
                sl.sendParticles(SbFx.dust(RGB, 0.9F), p.getX(), p.getY(), p.getZ(), 2, 0.05, 0.05, 0.05, 0);
                sl.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY(), p.getZ(), 1, 0.02, 0.02, 0.02, 0.01);
            }
        }
    }

    // =====================================================================================
    //  Desgraça: dano e efeitos desviados
    // =====================================================================================

    /** A Rotação Dourada vem "de dentro da luz": magias com Rotação Infinita ou Retângulo Dourado atravessam. */
    private static boolean goldenSpin(SpellContext ctx) {
        SpellRecipe recipe = ctx.recipe();
        if (recipe == null) return false;
        for (ResourceLocation id : recipe.glyphs()) {
            String path = id.getPath();
            if (path.equals(SbGlyphs.INFINITE) || id.equals(SbGlyphs.GOLDEN)) return true;
        }
        return false;
    }

    private static void onSpellPre(SpellDamageEvent.Pre e) {
        LivingEntity t = e.target();
        if (t.level().isClientSide) return;
        Run r = run(t);
        if (r == null) return;
        if (Domains.sureHitOn(t) || goldenSpin(e.ctx())) {
            PASS.add(t.getId());
            if (goldenSpin(e.ctx())) pierceFx(t);
            return;
        }
        e.setCanceled(true);
        if (redirecting > 0) return;
        LivingEntity caster = e.ctx().caster();
        misfortune(r, e.getAmount(), caster, caster != null ? caster.getEyePosition() : e.ctx().eyePos());
    }

    private static void onIncoming(LivingIncomingDamageEvent e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide) return;
        Run r = run(t);
        if (r == null) return;
        if (PASS.remove(t.getId())) return;
        DamageSource s = e.getSource();
        if (s.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || Domains.sureHitOn(t)) return;
        e.setCanceled(true);
        if (redirecting > 0) return;
        Entity attacker = s.getEntity();
        Entity direct = s.getDirectEntity();
        long now = r.ctx.level().getGameTime();
        if (attacker == null && direct == null) {
            // fogo, queda, afogamento, lava...: a luz desvia no máximo 2× por segundo (senão vira chuva de desgraça)
            if (now - r.lastEnv < 10) return;
            r.lastEnv = now;
        }
        if (attacker == t) attacker = null;
        Vec3 from = direct != null ? direct.position() : attacker != null ? attacker.position() : s.getSourcePosition();
        misfortune(r, e.getAmount(), attacker, from);
    }

    private static void onEffect(MobEffectEvent.Applicable e) {
        LivingEntity t = e.getEntity();
        if (t.level().isClientSide) return;
        Run r = run(t);
        if (r == null) return;
        MobEffectInstance inst = e.getEffectInstance();
        if (inst == null || inst.getEffect().value().getCategory() != MobEffectCategory.HARMFUL) return;
        // efeitos infinitos são regras (Exaustão do Domínio, Procurado...): valem sempre
        if (inst.isInfiniteDuration() || Domains.sureHitOn(t)) return;
        e.setResult(MobEffectEvent.Applicable.Result.DO_NOT_APPLY);
        if (redirecting > 0) return;
        MobEffectInstance copy = new MobEffectInstance(inst);
        Entity src = e.getEffectSource();
        // entrega no próximo tick (fora do addEffect de quem estava aplicando)
        SpellScheduler.schedule(1, () -> {
            Run now = run(t);
            if (now != null) redirectEffect(now, copy, src);
        });
    }

    private static void onImpact(ProjectileImpactEvent e) {
        if (!(e.getRayTraceResult() instanceof EntityHitResult hit) || !(hit.getEntity() instanceof LivingEntity t)) return;
        if (t.level().isClientSide) return;
        Run r = run(t);
        if (r == null || Domains.sureHitOn(t)) return;
        Projectile p = e.getProjectile();
        Entity owner = p.getOwner();
        if (owner == t || (owner != null && r.ctx.isAlly(owner))) return;
        e.setCanceled(true);
        // escorrega pela luz: segue em frente desviado para o lado
        Vec3 v = p.getDeltaMovement();
        if (v.lengthSqr() > 1.0E-4) {
            Vec3 side = v.cross(new Vec3(0, 1, 0));
            if (side.lengthSqr() < 1.0E-4) side = new Vec3(1, 0, 0);
            p.setDeltaMovement(v.add(side.normalize().scale(v.length() * 0.6)));
            p.hurtMarked = true;
        }
        if (t.level() instanceof ServerLevel sl) {
            sl.sendParticles(SbFx.dust(RGB, 1.1F), p.getX(), p.getY(), p.getZ(), 6, 0.1, 0.1, 0.1, 0);
            SbFx.sound(sl, p.position(), SoundEvents.AMETHYST_BLOCK_CHIME, 0.8F, 1.8F);
        }
    }

    /** Quem recebe a desgraça: o inimigo mais próximo (até NEAR); sem ninguém perto, um ao acaso até RANGE. */
    @Nullable
    private static LivingEntity pickVictim(Run r, @Nullable Entity attacker) {
        LivingEntity h = r.holder;
        Vec3 c = center(h);
        List<LivingEntity> list = new ArrayList<>();
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity e : r.ctx.enemiesAround(c, RANGE)) {
            if (e == h || e instanceof ArmorStand || active(e) || !hostile(h, e, attacker)) continue;
            list.add(e);
            double d = e.distanceToSqr(h);
            if (d < best) {
                best = d;
                nearest = e;
            }
        }
        if (nearest == null) return null;
        if (best <= NEAR * NEAR) return nearest;
        return list.get(r.ctx.level().random.nextInt(list.size()));
    }

    private static boolean hostile(LivingEntity h, LivingEntity e, @Nullable Entity attacker) {
        if (e == attacker || e instanceof Enemy || e instanceof Player) return true;
        if (e instanceof Mob m && m.getTarget() == h) return true;
        return h.getLastHurtByMob() == e;
    }

    /** Entrega o dano desviado (e os efeitos da receita) a um inimigo. */
    private static void misfortune(Run r, float amount, @Nullable Entity attacker, @Nullable Vec3 from) {
        if (!(amount > 0F)) return;
        LivingEntity h = r.holder;
        ServerLevel sl = r.ctx.level();
        absorbFx(sl, h, from);
        LivingEntity v = pickVictim(r, attacker);
        if (v == null) return;
        float dmg = amount * MISFORTUNE * Math.max(1F, r.ctx.formStats().power());
        redirecting++;
        try {
            v.invulnerableTime = 0;
            v.hurt(r.ctx.damageSource(r.curtain), dmg);
            pulse(r, v);
        } finally {
            redirecting--;
        }
        r.redirected++;
        streakFx(sl, h, v);
    }

    /** Um efeito ruim desviado vai para um inimigo (as condições do núcleo passam pelo Statuses). */
    private static void redirectEffect(Run r, MobEffectInstance inst, @Nullable Entity src) {
        LivingEntity v = pickVictim(r, src);
        if (v == null) return;
        redirecting++;
        try {
            Statuses.Kind k = Statuses.Kind.of(inst.getEffect());
            if (k != null) Statuses.apply(v, k, 1, Math.max(20, inst.getDuration()), r.holder, 1F);
            else v.addEffect(inst, r.holder);
            pulse(r, v);
        } finally {
            redirecting--;
        }
        r.redirected++;
        streakFx(r.ctx.level(), r.holder, v);
    }

    /** Os efeitos da receita pulsam em quem recebe a desgraça (1×/s por inimigo). */
    private static void pulse(Run r, LivingEntity v) {
        if (r.ctx.spell().effects().isEmpty() || !v.isAlive()) return;
        long now = r.ctx.level().getGameTime();
        Long last = r.pulsed.get(v.getUUID());
        if (last != null && now - last < PULSE_TICKS) return;
        r.pulsed.put(v.getUUID(), now);
        r.ctx.copy(r.ctx.powerMult()).resolveOnEntity(v, r.curtain);
    }

    // =====================================================================================
    //  Visual
    // =====================================================================================

    private static Vec3 center(Entity e) {
        return e.position().add(0, e.getBbHeight() * 0.5, 0);
    }

    /** A luz engole o golpe: onda branca-dourada no ponto de onde ele vinha. */
    private static void absorbFx(ServerLevel sl, LivingEntity h, @Nullable Vec3 from) {
        Vec3 c = center(h);
        Vec3 n = from == null ? new Vec3(0, 0, 1) : from.subtract(c);
        n = n.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : n.normalize();
        Vec3 at = c.add(n.scale(CURTAIN_RADIUS + h.getBbWidth() * 0.5));
        sl.sendParticles(SbFx.dust(RGB, 1.3F), at.x, at.y, at.z, 10, 0.25, 0.35, 0.25, 0);
        sl.sendParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 4, 0.15, 0.25, 0.15, 0.03);
        SbFx.sound(sl, at, SoundEvents.AMETHYST_BLOCK_HIT, 1.0F, 1.6F);
    }

    /** O rastro da desgraça: um fio de luz que vai do portador ao azarado, e o estalo nele. */
    private static void streakFx(ServerLevel sl, LivingEntity h, LivingEntity v) {
        Vec3 a = center(h), b = center(v);
        Vec3 d = b.subtract(a);
        int n = Mth.clamp((int) (d.length() * 2), 4, 40);
        for (int i = 1; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n));
            sl.sendParticles(SbFx.dust(i % 4 == 0 ? GOLD : RGB, 0.8F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        sl.sendParticles(ParticleTypes.CRIT, b.x, b.y, b.z, 10, 0.25, 0.3, 0.25, 0.2);
        sl.sendParticles(ParticleTypes.END_ROD, b.x, b.y, b.z, 5, 0.2, 0.3, 0.2, 0.05);
        SbFx.sound(sl, b, SoundEvents.GLASS_BREAK, 0.7F, 0.6F);
    }

    /** A Rotação Dourada atravessou a luz. */
    private static void pierceFx(LivingEntity t) {
        if (!(t.level() instanceof ServerLevel sl)) return;
        Vec3 c = center(t);
        SbFx.spinRing(sl, c, GOLD, CURTAIN_RADIUS + 0.3, 16);
        SbFx.sound(sl, c, SoundEvents.AMETHYST_CLUSTER_BREAK, 1.0F, 0.8F);
    }

    public static void forget(UUID id) {
        Run r = RUNS.remove(id);
        if (r != null && r.curtain != null) r.curtain.discard();
    }

    public static void clear() {
        RUNS.clear();
        PASS.clear();
        redirecting = 0;
    }

    private SbLoveTrain() {}
}
