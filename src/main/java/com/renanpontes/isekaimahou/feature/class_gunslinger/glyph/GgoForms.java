package com.renanpontes.isekaimahou.feature.class_gunslinger.glyph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.renanpontes.isekaimahou.core.MagicApi;
import com.renanpontes.isekaimahou.core.MagicPath;
import com.renanpontes.isekaimahou.core.api.Statuses;
import com.renanpontes.isekaimahou.core.boss.BossKit;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GgoState;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsFx;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsNetwork;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GsTechniques;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunCombat;
import com.renanpontes.isekaimahou.feature.class_gunslinger.GunKind;
import com.renanpontes.isekaimahou.feature.class_gunslinger.entity.PhotonSlashEntity;
import com.renanpontes.isekaimahou.glyph.FormGlyph;
import com.renanpontes.isekaimahou.glyph.SpellContext;
import com.renanpontes.isekaimahou.glyph.SpellScheduler;
import com.renanpontes.isekaimahou.glyph.SpellStats;

import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Técnicas "Gun Gale" do Pistoleiro (homenagem a Gun Gale Online, com nomes próprios): Espada de Fótons, Linha de Bala,
 * Círculo de Mira, Camuflagem Óptica, Estrela Negra, Atirador de Elite (fuzil antimaterial) e a forma G Rajada
 * Estelar. Glifos de origem liberados pelo Caminho do Gatilho ({@link GsTechniques}).
 */
public final class GgoForms {
    public static final int PHOTON = 0xB98CFF;
    public static final int PHOTON_CORE = 0x8FD8FF;

    // =====================================================================================
    //  Raio instantâneo (comum)
    // =====================================================================================

    /** Um acerto do raio: alvo, ponto e se foi na cabeça. */
    public record RayHit(LivingEntity target, Vec3 at, boolean head) {}

    /** Alvos ao longo da linha (até {@code max}), do mais perto ao mais longe, parando no primeiro bloco. */
    public static List<RayHit> ray(SpellContext ctx, LivingEntity caster, Vec3 eye, Vec3 dir, double range, int max, Vec3[] endOut) {
        ServerLevel level = ctx.level();
        Vec3 to = eye.add(dir.scale(range));
        BlockHitResult block = level.clip(new ClipContext(eye, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        Vec3 end = block.getType() == HitResult.Type.MISS ? to : block.getLocation();
        if (endOut != null && endOut.length > 0) endOut[0] = end;
        List<Entity> along = new ArrayList<>(level.getEntities(caster, new AABB(eye, end).inflate(1.0),
                e -> e instanceof LivingEntity && e.isAlive() && e.isPickable() && !e.isSpectator() && !ctx.ignores(e) && !ctx.isAlly(e)));
        along.sort(Comparator.comparingDouble(e -> e.distanceToSqr(eye)));
        List<RayHit> out = new ArrayList<>();
        for (Entity e : along) {
            if (out.size() >= max) break;
            var clip = e.getBoundingBox().inflate(0.3).clip(eye, end);
            if (clip.isEmpty()) continue;
            LivingEntity le = (LivingEntity) e;
            Vec3 at = clip.get();
            boolean head = le.getBbHeight() > 0.6F && at.y >= le.getEyeY() - 0.3;
            out.add(new RayHit(le, at, head));
        }
        if (block.getType() == HitResult.Type.BLOCK && endOut != null && endOut.length > 1) endOut[1] = Vec3.atLowerCornerOf(block.getBlockPos());
        return out;
    }

    /** Mira do conjurador (mobs: no alvo). */
    static Vec3 aim(LivingEntity caster) {
        if (caster instanceof Mob mob && mob.getTarget() != null) {
            return mob.getTarget().getEyePosition().subtract(caster.getEyePosition()).normalize();
        }
        return caster.getLookAngle();
    }

    /** Poder do rank do Caminho para técnicas que não usam a arma na mão (+4% por rank, só o Pistoleiro). */
    static float rankPower(@Nullable LivingEntity caster) {
        if (caster instanceof Player p && MagicApi.get(p).path() == MagicPath.GUNSLINGER) {
            return 1.0F + GunCombat.RANK_DAMAGE * com.renanpontes.isekaimahou.feature.class_gunslinger.path.TriggerPath.rankOf(MagicApi.get(p));
        }
        return 1.0F;
    }

    // =====================================================================================
    //  Espada de Fótons
    // =====================================================================================

    /**
     * Espada de Fótons (homenagem à lâmina de luz do espadachim de Gun Gale): a lâmina de energia sai do cabo e corta
     * em arco à sua frente (4 blocos ×Expandir, 140°). Cada corte aplica os efeitos com ×1,25 e CORTA os projéteis no
     * caminho (rebate para quem atirou); por 0,4 s depois de cada corte, o que vier em você é rebatido. Dividir = cortes
     * a mais (até 4, alternando o lado).
     */
    public static class PhotonSwordForm extends FormGlyph {
        public static final float REACH = 4.0F;
        public static final float POWER = 1.25F;

        public PhotonSwordForm() {
            super("photon_sword", 12);
            origin(MagicPath.GUNSLINGER);
            tier(3);
            minCircle(3);
        }

        public static int slashes(SpellStats s) {
            return Math.min(4, 1 + Math.max(0, s.split));
        }

        @Override public float copies(SpellStats s, int circle) { return slashes(s); }
        @Override public int baseCooldown() { return 16; }
        @Override public float hintRange() { return REACH; }
        @Override public float hintRadius() { return REACH; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            SpellContext use = ctx.copy(ctx.powerMult() * POWER * rankPower(caster));
            int n = slashes(ctx.formStats());
            GsFx.sound(ctx.level(), caster.position(), SoundEvents.BEACON_ACTIVATE, 0.6F, 2.0F);
            if (caster instanceof ServerPlayer sp) GsTechniques.actionbar(sp, GsTechniques.key("photon_sword"), PHOTON);
            for (int i = 0; i < n; i++) {
                final int k = i;
                Runnable r = () -> slash(use, caster, k, color(ctx));
                if (i == 0) r.run();
                else SpellScheduler.schedule(4 * i, r);
            }
        }

        static int color(SpellContext ctx) {
            int c = ctx.color() & 0xFFFFFF;
            return c == 0 ? PHOTON : c;
        }

        /** Um corte: arco de luz, acerta quem está no arco e rebate projéteis. */
        static void slash(SpellContext ctx, LivingEntity caster, int index, int rgb) {
            if (!caster.isAlive() || caster.isRemoved()) return;
            ServerLevel level = ctx.level();
            if (caster.level() != level) return;
            float reach = ctx.formStats().radius(REACH);
            Vec3 look = aim(caster);
            Vec3 eye = caster.getEyePosition();
            float roll = switch (index % 4) {
                case 0 -> 18F;
                case 1 -> 162F;
                case 2 -> -40F;
                default -> 220F;
            };
            PhotonSlashEntity.spawn(level, eye.add(look.scale(0.3)).subtract(0, 0.25, 0), look, roll, reach * 0.75F, rgb, 6);
            caster.swing(index % 2 == 0 ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, true);
            GsFx.sound(level, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 1.6F + 0.1F * index);
            GsFx.sound(level, eye, SoundEvents.BEACON_DEACTIVATE, 0.5F, 2.0F);
            GgoState.deflect(caster, 8);
            for (LivingEntity e : ctx.enemiesAround(caster.position(), reach + 0.8)) {
                Vec3 to = e.position().add(0, e.getBbHeight() * 0.5, 0).subtract(eye);
                if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) < 0.34) continue;
                e.invulnerableTime = 0;
                ctx.resolveOnEntity(e, caster);
                Vec3 c = e.position().add(0, e.getBbHeight() * 0.5, 0);
                level.sendParticles(GsFx.dust(rgb, 1.1F), c.x, c.y, c.z, 8, 0.25, 0.3, 0.25, 0);
                level.sendParticles(ParticleTypes.SWEEP_ATTACK, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                if (caster instanceof ServerPlayer sp) MagicApi.addCounter(sp, GsTechniques.PHOTON_HITS, 1);
            }
            // corta as balas/flechas que estão no arco
            for (Projectile p : level.getEntitiesOfClass(Projectile.class, caster.getBoundingBox().inflate(reach + 1),
                    p -> p.isAlive() && p.getOwner() != caster && (p.getOwner() == null || !ctx.isAlly(p.getOwner())))) {
                Vec3 to = p.position().subtract(eye);
                if (to.lengthSqr() > 1.0E-4 && to.normalize().dot(look) < 0.2) continue;
                GgoState.reflect(caster, p);
            }
        }
    }

    // =====================================================================================
    //  Linha de Bala
    // =====================================================================================

    /**
     * Linha de Bala (previsão): por 10 s (×Estender) você vê LINHAS VERMELHAS de quem mira em você e dos projéteis
     * vindo na sua direção (só você vê) e ganha 3 esquivas (+1 por Dividir, até 6): o golpe previsto não acerta e você
     * dá um passo de lado. Com efeitos na magia, cada esquiva revida um tiro em quem atacou (80%).
     */
    public static class BulletLineForm extends FormGlyph {
        public static final int DURATION = 200;

        public BulletLineForm() {
            super("bullet_line", 8);
            origin(MagicPath.GUNSLINGER);
            tier(2);
            minCircle(2);
        }

        public static int charges(SpellStats s) {
            return Math.min(6, 3 + Math.max(0, s.split));
        }

        @Override public boolean requiresEffect() { return false; }
        @Override public float copies(SpellStats s, int circle) { return charges(s); }
        @Override public int baseCooldown() { return 400; }
        @Override public int hintDuration() { return DURATION; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (!(caster instanceof ServerPlayer sp)) {
                if (caster != null) caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, false, false));
                return;
            }
            int ticks = ctx.formStats().duration(DURATION);
            int charges = charges(ctx.formStats());
            GgoState.startLines(sp, ctx, ticks, charges);
            ServerLevel level = ctx.level();
            Vec3 c = sp.position().add(0, sp.getEyeHeight(), 0);
            level.sendParticles(sp, new DustParticleOptions(new org.joml.Vector3f(1F, 0.15F, 0.15F), 1.0F), true, c.x, c.y, c.z, 20, 0.6, 0.4,
                    0.6, 0);
            sp.playNotifySound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 1.0F, 0.6F);
            sp.playNotifySound(SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.6F, 2.0F);
            GsTechniques.callout(sp, GsTechniques.key("bullet_line"),
                    Component.translatable("callout.isekai_mahou.gunslinger.bullet_line.sub", charges), GgoState.RED);
        }
    }

    // =====================================================================================
    //  Círculo de Mira
    // =====================================================================================

    /**
     * Círculo de Mira (tiro de precisão): aparece o círculo verde na sua mira (só você vê) e ele vai FECHANDO enquanto
     * você fica parado (agachado fecha mais rápido); andar abre, e o coração bate e o faz pulsar. Quando fecha (≈1,2 s),
     * o tiro sai sozinho: CRÍTICO (×2,5 de poder e conta como tiro na cabeça). Se não fechar em 2 s, sai do jeito que
     * estiver — mais fraco e desviado. Tiro instantâneo, alcance 128.
     */
    public static class AimCircleForm extends FormGlyph {
        public static final int MAX_TICKS = 40;
        public static final double RANGE = 128.0;
        private static final Map<UUID, Object> ACTIVE = new HashMap<>();

        public AimCircleForm() {
            super("aim_circle", 8);
            origin(MagicPath.GUNSLINGER);
            tier(2);
        }

        @Override public float copies(SpellStats s, int circle) { return s.count(); }
        @Override public int baseCooldown() { return 50; }
        @Override public float hintRange() { return (float) RANGE; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            if (!(caster instanceof ServerPlayer sp)) {
                fire(ctx, caster, 0.6F);
                return;
            }
            Object token = new Object();
            ACTIVE.put(sp.getUUID(), token);
            float[] closure = {0F};
            Vec3[] last = {sp.position()};
            int[] t = {0};
            GsTechniques.actionbar(sp, GsTechniques.key("aim_circle"), 0x7CFF6A);
            Runnable[] step = new Runnable[1];
            step[0] = () -> {
                if (ACTIVE.get(sp.getUUID()) != token) return; // outro círculo começou
                if (!sp.isAlive() || sp.isRemoved() || sp.level() != ctx.level()) {
                    ACTIVE.remove(sp.getUUID(), token);
                    return;
                }
                t[0]++;
                boolean moved = sp.position().distanceToSqr(last[0]) > 0.0025 || sp.isSprinting();
                last[0] = sp.position();
                closure[0] += moved ? -1F / 12F : (sp.isShiftKeyDown() ? 1F / 16F : 1F / 24F);
                if (t[0] % 12 == 0) {
                    closure[0] -= sp.isShiftKeyDown() ? 0.03F : 0.07F;
                    sp.playNotifySound(SoundEvents.NOTE_BLOCK_BASEDRUM.value(), SoundSource.PLAYERS, 0.8F, 0.5F);
                }
                closure[0] = Math.max(0F, Math.min(1F, closure[0]));
                if (t[0] % 2 == 0) drawCircle(sp, closure[0]);
                if (closure[0] >= 1F || t[0] >= MAX_TICKS) {
                    ACTIVE.remove(sp.getUUID(), token);
                    fire(ctx, sp, closure[0]);
                } else {
                    SpellScheduler.schedule(1, step[0]);
                }
            };
            SpellScheduler.schedule(1, step[0]);
        }

        /** O círculo a 6 blocos na frente: raio = 6·tan(0,5° + 7°·(1 − fechamento)); verde, dourado quando fecha. */
        private static void drawCircle(ServerPlayer sp, float closure) {
            ServerLevel level = sp.serverLevel();
            Vec3 look = sp.getLookAngle();
            Vec3 center = sp.getEyePosition().add(look.scale(6));
            double deg = 0.5 + 7.0 * (1 - closure);
            double r = 6 * Math.tan(Math.toRadians(deg));
            Vec3 right = look.cross(new Vec3(0, 1, 0));
            if (right.lengthSqr() < 1.0E-4) right = new Vec3(1, 0, 0);
            right = right.normalize();
            Vec3 up = right.cross(look).normalize();
            boolean near = closure > 0.9F;
            DustParticleOptions dust = new DustParticleOptions(near ? new org.joml.Vector3f(1F, 0.85F, 0.3F) : new org.joml.Vector3f(0.5F, 1F, 0.42F),
                    0.35F);
            int pts = 14;
            for (int i = 0; i < pts; i++) {
                double a = Math.PI * 2 * i / pts;
                Vec3 p = center.add(right.scale(Math.cos(a) * r)).add(up.scale(Math.sin(a) * r));
                level.sendParticles(sp, dust, true, p.x, p.y, p.z, 1, 0, 0, 0, 0);
            }
        }

        static void fire(SpellContext ctx, LivingEntity caster, float closure) {
            ServerLevel level = ctx.level();
            boolean perfect = closure >= 0.999F;
            float deg = (1F - closure) * 6F;
            Vec3 look = GunCombat.rotate(aim(caster), (ctx.random().nextFloat() - 0.5F) * 2F * deg, (ctx.random().nextFloat() - 0.5F) * 2F * deg);
            float power = GunCombat.formPower(ctx) * (1.0F + closure) * (perfect ? 1.25F : 1F);
            SpellContext use = ctx.copy(ctx.powerMult() * power);
            Vec3 eye = caster.getEyePosition();
            Vec3[] end = new Vec3[1];
            int rgb = perfect ? 0xFFD86B : 0x7CFF6A;
            int shots = Math.max(1, ctx.formStats().count());
            boolean any = false;
            for (int s = 0; s < shots; s++) {
                Vec3 dir = s == 0 ? look : GunCombat.rotate(look, (s % 2 == 0 ? 1 : -1) * 2.5F * ((s + 1) / 2), 0);
                List<RayHit> hits = ray(ctx, caster, eye, dir, RANGE, 1 + Math.max(0, ctx.formStats().pierce), end);
                Vec3 from = GsFx.muzzle(caster, dir, 1);
                for (RayHit h : hits) {
                    float m = !perfect && h.head() ? GunCombat.headshotMultiplier(caster) : 1F;
                    SpellContext shot = m > 1.001F ? use.copy(use.powerMult() * m) : use;
                    ctx.impact(h.at(), dir, h.target());
                    h.target().invulnerableTime = 0;
                    shot.resolveOnEntity(h.target(), null);
                    GsFx.impact(level, h.at(), rgb, true);
                    if (caster instanceof ServerPlayer sp) GunCombat.onBulletHit(sp, h.target(), perfect || h.head());
                    any = true;
                }
                GsFx.tracer(level, from, end[0] == null ? eye.add(dir.scale(RANGE)) : end[0], rgb, perfect ? 1.2F : 0.8F);
                GsFx.shot(level, from, dir, rgb, GunCombat.heldKind(caster));
            }
            if (!any) ctx.end(end[0] == null ? eye.add(look.scale(RANGE)) : end[0], look);
            if (caster instanceof ServerPlayer sp) {
                GsNetwork.anim(sp, GsNetwork.SHOOT, GunCombat.heldKind(caster).ordinal(), rgb);
                if (perfect) {
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.aim_perfect").withColor(0xFFD86B), true);
                    sp.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 1.8F);
                } else {
                    sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.aim_partial", Math.round(closure * 100))
                            .withStyle(ChatFormatting.GRAY), true);
                }
            }
        }

        public static void forget(UUID id) {
            ACTIVE.remove(id);
        }

        public static void clear() {
            ACTIVE.clear();
        }
    }

    // =====================================================================================
    //  Camuflagem Óptica
    // =====================================================================================

    /**
     * Camuflagem Óptica (homenagem ao manto do atirador da caveira): o manto liga e você some — Invisível por 8 s
     * (×Estender), as criaturas perdem você de vista e não o escolhem como alvo (a não ser coladas). O PRIMEIRO ataque
     * sai como EMBOSCADA (×1,5 de dano) e desliga o manto. Funciona sem efeito; efeitos (benéficos) vão em você.
     */
    public static class OpticalCamoForm extends FormGlyph {
        public static final int DURATION = 160;

        public OpticalCamoForm() {
            super("optical_camo", 14);
            origin(MagicPath.GUNSLINGER);
            tier(4);
            minCircle(5);
        }

        @Override public boolean requiresEffect() { return false; }
        @Override public float copies(SpellStats s, int circle) { return 1F; }
        @Override public int baseCooldown() { return 500; }
        @Override public int hintDuration() { return DURATION; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            int ticks = ctx.formStats().duration(DURATION);
            caster.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, ticks, 0, false, false));
            GgoState.startCamo(caster, ticks);
            GgoState.dropAggro(caster, 32);
            // o manto se fecha dos pés à cabeça
            for (int i = 0; i < 6; i++) {
                final double y = caster.getBbHeight() * i / 5.0;
                SpellScheduler.schedule(1 + i, () -> {
                    if (caster.isRemoved()) return;
                    Vec3 c = caster.position().add(0, y, 0);
                    GsFx.ring(level, c, caster.getBbWidth() * 0.9 + 0.2, 12, GsFx.dust(0xD8F0FF, 0.6F));
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, c.x, c.y, c.z, 2, 0.2, 0.05, 0.2, 0.02);
                });
            }
            GsFx.sound(level, caster.position(), SoundEvents.ILLUSIONER_MIRROR_MOVE, 1.0F, 1.2F);
            GsFx.sound(level, caster.position(), SoundEvents.BEACON_DEACTIVATE, 0.6F, 1.8F);
            if (!ctx.spell().effects().isEmpty()) ctx.resolveOnSelf();
            GsTechniques.callout(caster, GsTechniques.key("optical_camo"),
                    Component.translatable("callout.isekai_mahou.gunslinger.optical_camo.sub"), 0x9FB8C8);
        }
    }

    // =====================================================================================
    //  Estrela Negra
    // =====================================================================================

    /**
     * Estrela Negra (homenagem à pistola do atirador da caveira): você faz o sinal da cruz e atira uma bala negra
     * (instantânea, 96 blocos) — ×1,6 de poder (cabeça conta). EXECUÇÃO: uma criatura comum que fique com 30% da vida
     * ou menos depois do tiro cai na hora. Em jogadores e chefes não há execução: a marca da estrela os deixa Expostos
     * III e com Murchar. Saindo da Camuflagem Óptica, o tiro é silencioso (e a emboscada soma).
     */
    public static class BlackStarForm extends FormGlyph {
        public static final double RANGE = 96.0;
        public static final float POWER = 1.6F;
        public static final float EXECUTE = 0.30F;
        public static final int BLACK = 0x1A0008;
        public static final int RED_CORE = 0xFF1030;

        public BlackStarForm() {
            super("black_star", 26);
            origin(MagicPath.GUNSLINGER);
            tier(5);
            minCircle(6);
        }

        @Override public float copies(SpellStats s, int circle) { return 1F; }
        @Override public int baseCastTicks() { return 16; }
        @Override public int baseCooldown() { return 240; }
        @Override public float hintRange() { return (float) RANGE; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            boolean silent = GgoState.camouflaged(caster);
            // o sinal da cruz diante do peito
            Vec3 look = aim(caster);
            Vec3 chest = caster.getEyePosition().add(look.scale(0.6)).subtract(0, 0.35, 0);
            Vec3 right = look.cross(new Vec3(0, 1, 0));
            right = right.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : right.normalize();
            for (int i = -3; i <= 3; i++) {
                Vec3 v = chest.add(0, i * 0.08, 0);
                Vec3 h = chest.add(0, 0.08, 0).add(right.scale(i * 0.05));
                level.sendParticles(GsFx.dust(RED_CORE, 0.6F), v.x, v.y, v.z, 1, 0, 0, 0, 0);
                if (Math.abs(i) <= 2) level.sendParticles(GsFx.dust(RED_CORE, 0.6F), h.x, h.y, h.z, 1, 0, 0, 0, 0);
            }
            if (!silent) GsFx.sound(level, chest, SoundEvents.BELL_RESONATE, 0.7F, 0.5F);
            GsTechniques.callout(caster, GsTechniques.key("black_star"),
                    Component.translatable("callout.isekai_mahou.gunslinger.black_star.sub"), RED_CORE);
            SpellScheduler.schedule(6, () -> shoot(ctx, caster, silent));
        }

        private static void shoot(SpellContext ctx, LivingEntity caster, boolean silent) {
            if (!caster.isAlive() || caster.isRemoved() || caster.level() != ctx.level()) return;
            ServerLevel level = ctx.level();
            Vec3 look = aim(caster);
            Vec3 eye = caster.getEyePosition();
            Vec3[] end = new Vec3[1];
            float power = GunCombat.formPower(ctx) * POWER;
            List<RayHit> hits = ray(ctx, caster, eye, look, RANGE, 1, end);
            Vec3 from = GsFx.muzzle(caster, look, 1);
            Vec3 stop = end[0] == null ? eye.add(look.scale(RANGE)) : end[0];
            if (!hits.isEmpty()) stop = hits.get(0).at();
            GsFx.tracer(level, from, stop, BLACK, 1.4F);
            GsFx.tracer(level, from, stop, RED_CORE, 0.5F);
            level.sendParticles(ParticleTypes.FLASH, from.x, from.y, from.z, 1, 0, 0, 0, 0);
            level.sendParticles(ParticleTypes.SMOKE, from.x, from.y, from.z, 6, 0.05, 0.05, 0.05, 0.02);
            if (silent) {
                GsFx.sound(level, from, SoundEvents.ARROW_SHOOT, 0.4F, 0.5F);
            } else {
                GsFx.sound(level, from, SoundEvents.WITHER_SHOOT, 0.9F, 0.6F);
                GsFx.sound(level, from, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.0F, 0.7F);
            }
            if (caster instanceof ServerPlayer sp) GsNetwork.anim(sp, GsNetwork.SHOOT, GunKind.PISTOL.ordinal(), RED_CORE);
            if (hits.isEmpty()) {
                ctx.end(stop, look);
                return;
            }
            RayHit h = hits.get(0);
            LivingEntity target = h.target();
            float m = h.head() ? GunCombat.headshotMultiplier(caster) : 1F;
            ctx.impact(h.at(), look, target);
            target.invulnerableTime = 0;
            ctx.copy(ctx.powerMult() * power * m).resolveOnEntity(target, null);
            if (caster instanceof ServerPlayer sp) GunCombat.onBulletHit(sp, target, h.head());
            star(level, target);
            if (!target.isAlive()) return;
            boolean immune = BossKit.isBossLike(target) || target instanceof Player;
            if (!immune && target.getHealth() <= target.getMaxHealth() * EXECUTE) {
                target.invulnerableTime = 0;
                target.hurt(ctx.damageSource(null), 1.0E6F);
                level.sendParticles(ParticleTypes.SOUL, target.getX(), target.getY() + 1, target.getZ(), 12, 0.3, 0.5, 0.3, 0.03);
                GsFx.sound(level, target.position(), SoundEvents.WITHER_DEATH, 0.35F, 1.8F);
                GsTechniques.actionbar(caster, Component.translatable("message.isekai_mahou.gunslinger.execute"), RED_CORE);
            } else {
                Statuses.apply(target, Statuses.Kind.EXPOSED, 3, 120, caster, ctx.powerMult());
                target.addEffect(new MobEffectInstance(MobEffects.WITHER, 60, 1), caster);
            }
        }

        /** A estrela de 5 pontas negra e vermelha sobre o alvo. */
        private static void star(ServerLevel level, LivingEntity target) {
            Vec3 c = target.position().add(0, target.getBbHeight() + 0.6, 0);
            double r = 0.55;
            Vec3[] pts = new Vec3[5];
            for (int i = 0; i < 5; i++) {
                double a = -Math.PI / 2 + i * 2 * Math.PI / 5;
                pts[i] = c.add(Math.cos(a) * r, -Math.sin(a) * r, 0);
            }
            for (int i = 0; i < 5; i++) {
                Vec3 a = pts[i], b = pts[(i + 2) % 5];
                for (int k = 0; k <= 6; k++) {
                    Vec3 p = a.add(b.subtract(a).scale(k / 6.0));
                    level.sendParticles(GsFx.dust(k % 2 == 0 ? RED_CORE : BLACK, 0.7F), p.x, p.y, p.z, 1, 0, 0, 0, 0);
                }
            }
        }
    }

    // =====================================================================================
    //  Atirador de Elite (fuzil antimaterial)
    // =====================================================================================

    /**
     * Atirador de Elite — fuzil antimaterial (homenagem à atiradora de gelo e seu fuzil gigante): mira por 1,2 s e
     * dispara um tiro instantâneo de até 192 blocos (×Acelerar) que atravessa 3 alvos (+Perfurar) e quebra a defesa
     * (Exposto II). AGACHADO (apoiado no bipé): ×2,4 de poder, sem desvio. Em pé: ×1,4, desvio e o coice joga você para
     * trás. Cabeça conta.
     */
    public static class AntiMaterielForm extends FormGlyph {
        public static final double RANGE = 192.0;

        public AntiMaterielForm() {
            super("anti_materiel", 16);
            origin(MagicPath.GUNSLINGER);
            tier(4);
            minCircle(5);
        }

        @Override public float copies(SpellStats s, int circle) { return 1F + 0.5F * (2 + Math.max(0, s.pierce)); }
        @Override public int baseCastTicks() { return 24; }
        @Override public int baseCooldown() { return 70; }
        @Override public float hintRange() { return (float) RANGE; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            boolean braced = caster.isShiftKeyDown() || caster instanceof Mob;
            Vec3 look = aim(caster);
            if (!braced) look = GunCombat.rotate(look, (ctx.random().nextFloat() - 0.5F) * 6F, (ctx.random().nextFloat() - 0.5F) * 4F);
            float power = GunCombat.formPower(ctx) * (braced ? 2.4F : 1.4F);
            Vec3 eye = caster.getEyePosition();
            double range = RANGE * Math.max(0.5F, ctx.formStats().speedMult());
            Vec3[] end = new Vec3[2];
            List<RayHit> hits = ray(ctx, caster, eye, look, range, 3 + Math.max(0, ctx.formStats().pierce), end);
            Vec3 from = GsFx.muzzle(caster, look, 1);
            Vec3 stop = end[0] == null ? eye.add(look.scale(range)) : end[0];
            int rgb = 0x9AF0FF;
            for (RayHit h : hits) {
                float m = h.head() ? GunCombat.headshotMultiplier(caster) : 1F;
                ctx.impact(h.at(), look, h.target());
                h.target().invulnerableTime = 0;
                ctx.copy(ctx.powerMult() * power * m).resolveOnEntity(h.target(), null);
                if (h.target().isAlive()) Statuses.apply(h.target(), Statuses.Kind.EXPOSED, 2, 100, caster, ctx.powerMult());
                level.sendParticles(ParticleTypes.EXPLOSION, h.at().x, h.at().y, h.at().z, 1, 0, 0, 0, 0);
                if (caster instanceof ServerPlayer sp) GunCombat.onBulletHit(sp, h.target(), h.head());
            }
            // estrondo: onda de choque no cano, rastro grosso e o eco
            level.sendParticles(ParticleTypes.SONIC_BOOM, from.x, from.y, from.z, 1, 0, 0, 0, 0);
            GsFx.ring(level, from, 1.2, 18, GsFx.dust(0xE8F8FF, 1.2F));
            GsFx.tracer(level, from, stop, rgb, 1.8F);
            GsFx.tracer(level, from, stop, 0xFFFFFF, 0.6F);
            level.sendParticles(ParticleTypes.CLOUD, from.x, from.y, from.z, 10, 0.2, 0.2, 0.2, 0.08);
            GsFx.sound(level, from, SoundEvents.GENERIC_EXPLODE.value(), 1.2F, 0.6F);
            GsFx.sound(level, from, SoundEvents.TRIDENT_THUNDER.value(), 0.8F, 1.6F);
            GsFx.sound(level, from, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 1.0F, 0.5F);
            if (hits.isEmpty()) {
                if (end[1] != null) ctx.impact(stop, look, null);
                else ctx.end(stop, look);
                level.sendParticles(ParticleTypes.EXPLOSION, stop.x, stop.y, stop.z, 1, 0, 0, 0, 0);
            }
            if (!braced) {
                caster.push(-look.x * 0.9, 0.1, -look.z * 0.9);
                caster.hurtMarked = true;
                caster.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 1, false, false));
            }
            if (caster instanceof ServerPlayer sp) {
                GsNetwork.anim(sp, GsNetwork.SHOOT, GunKind.RIFLE.ordinal(), rgb);
                GsTechniques.actionbar(sp, Component.translatable(braced ? "callout.isekai_mahou.gunslinger.anti_materiel"
                        : "callout.isekai_mahou.gunslinger.anti_materiel.standing"), rgb);
            }
        }
    }

    // =====================================================================================
    //  Rajada Estelar (forma G)
    // =====================================================================================

    /**
     * Rajada Estelar (forma G — homenagem ao combo de 16 golpes do espadachim negro): você trava no inimigo à sua
     * frente (10 blocos) e solta 16 golpes em 1,6 s, ALTERNANDO corte de fótons e tiro, girando em volta dele a cada 4
     * golpes. Cada golpe aplica os efeitos (65%); o 16º sai com ×2. Se o alvo cair, a rajada pula para o próximo.
     * Resistência IV durante o combo. Círculo 8.
     */
    public static class StellarBurstForm extends FormGlyph {
        public static final int HITS = 16;
        public static final float POWER = 0.65F;
        public static final double RANGE = 10.0;

        public StellarBurstForm() {
            super("stellar_burst", 80);
            origin(MagicPath.GUNSLINGER);
            legendary();
            minCircle(8);
        }

        @Override public boolean isUltimate() { return true; }
        @Override public float copies(SpellStats s, int circle) { return HITS; }
        @Override public int baseCastTicks() { return 10; }
        @Override public int baseCooldown() { return 300; }
        @Override public float hintRange() { return (float) RANGE; }
        @Override public int hintDuration() { return HITS * 2; }

        @Override
        public void cast(SpellContext ctx) {
            LivingEntity caster = ctx.caster();
            if (caster == null) return;
            ServerLevel level = ctx.level();
            LivingEntity first = pick(ctx, caster, null);
            if (first == null) {
                GsTechniques.actionbar(caster, Component.translatable("message.isekai_mahou.gunslinger.burst_empty"), PHOTON_CORE);
                ctx.end(caster.getEyePosition(), caster.getLookAngle());
                return;
            }
            caster.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, HITS * 2 + 10, 3, false, false));
            GsTechniques.callout(caster, GsTechniques.key("stellar_burst"),
                    Component.translatable("callout.isekai_mahou.gunslinger.stellar_burst.sub"), PHOTON_CORE);
            GsFx.sound(level, caster.position(), SoundEvents.BEACON_ACTIVATE, 1.0F, 1.6F);
            GsFx.sound(level, caster.position(), SoundEvents.TRIDENT_RIPTIDE_3.value(), 0.8F, 1.5F);
            if (caster instanceof ServerPlayer sp) GsNetwork.anim(sp, GsNetwork.DRAW, 0, PHOTON_CORE);
            LivingEntity[] target = {first};
            int slashRgb = PhotonSwordForm.color(ctx);
            for (int i = 0; i < HITS; i++) {
                final int k = i;
                Runnable r = () -> hit(ctx, caster, target, k, slashRgb);
                if (i == 0) r.run();
                else SpellScheduler.schedule(2 * i, r);
            }
        }

        @Nullable
        private static LivingEntity pick(SpellContext ctx, LivingEntity caster, @Nullable LivingEntity not) {
            Vec3 eye = caster.getEyePosition();
            Vec3 look = aim(caster);
            LivingEntity best = null;
            double bestScore = Double.MAX_VALUE;
            for (LivingEntity e : ctx.enemiesAround(caster.position(), RANGE)) {
                if (e == not || !caster.hasLineOfSight(e)) continue;
                Vec3 to = e.getEyePosition().subtract(eye);
                double dot = to.lengthSqr() < 1.0E-4 ? 1 : to.normalize().dot(look);
                if (not == null && dot < 0.2) continue;
                double score = to.length() * (2.0 - dot);
                if (score < bestScore) {
                    bestScore = score;
                    best = e;
                }
            }
            return best;
        }

        private static void hit(SpellContext ctx, LivingEntity caster, LivingEntity[] holder, int k, int slashRgb) {
            if (!caster.isAlive() || caster.isRemoved() || caster.level() != ctx.level()) return;
            ServerLevel level = ctx.level();
            LivingEntity target = holder[0];
            if (target == null || !target.isAlive() || target.level() != level) {
                target = pick(ctx, caster, target);
                holder[0] = target;
                if (target == null) return;
            }
            if (k > 0 && k % 4 == 0) blink(caster, target, k / 4);
            Vec3 eye = caster.getEyePosition();
            Vec3 dir = target.getEyePosition().subtract(eye);
            dir = dir.lengthSqr() < 1.0E-4 ? caster.getLookAngle() : dir.normalize();
            boolean last = k == HITS - 1;
            float power = POWER * rankPower(caster) * (last ? 2F : 1F);
            Vec3 c = target.position().add(0, target.getBbHeight() * 0.5, 0);
            if (k % 2 == 0) {
                PhotonSlashEntity.spawn(level, eye.add(dir.scale(0.4)).subtract(0, 0.2, 0), dir, (k % 4 == 0 ? 25F : -30F) + k * 11F,
                        last ? 3.0F : 2.2F, slashRgb, 5);
                GsFx.sound(level, eye, SoundEvents.PLAYER_ATTACK_SWEEP, 0.9F, 1.4F + 0.04F * k);
                caster.swing(InteractionHand.MAIN_HAND, true);
            } else {
                Vec3 from = GsFx.muzzle(caster, dir, -1);
                GsFx.tracer(level, from, c, PHOTON_CORE, 0.9F);
                GsFx.shot(level, from, dir, PHOTON_CORE, GunKind.PISTOL);
                caster.swing(InteractionHand.OFF_HAND, true);
            }
            target.invulnerableTime = 0;
            ctx.copy(ctx.powerMult() * power).resolveOnEntity(target, caster);
            level.sendParticles(GsFx.dust(k % 2 == 0 ? slashRgb : PHOTON_CORE, 1.2F), c.x, c.y, c.z, 6, 0.25, 0.3, 0.25, 0);
            level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 6, 0.2, 0.3, 0.2, 0.3);
            if (caster instanceof ServerPlayer sp) {
                MagicApi.addCounter(sp, GsTechniques.PHOTON_HITS, 1);
                sp.displayClientMessage(Component.translatable("message.isekai_mahou.gunslinger.burst_count", k + 1, HITS)
                        .withColor(k % 2 == 0 ? slashRgb : PHOTON_CORE), true);
            }
            if (last) {
                level.sendParticles(ParticleTypes.FLASH, c.x, c.y, c.z, 1, 0, 0, 0, 0);
                level.sendParticles(ParticleTypes.FIREWORK, c.x, c.y, c.z, 40, 0.4, 0.5, 0.4, 0.25);
                level.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 20, 0.5, 0.5, 0.5, 0.15);
                GsFx.sound(level, c, SoundEvents.FIREWORK_ROCKET_TWINKLE, 1.2F, 1.0F);
                GsFx.sound(level, c, SoundEvents.GENERIC_EXPLODE.value(), 0.7F, 1.4F);
            }
        }

        /** Gira em volta do alvo: a cada 4 golpes você aparece em outro lado dele (se houver espaço). */
        private static void blink(LivingEntity caster, LivingEntity target, int turn) {
            Vec3 rel = caster.position().subtract(target.position()).multiply(1, 0, 1);
            double base = rel.lengthSqr() < 1.0E-4 ? 0 : Math.atan2(rel.z, rel.x);
            double a = base + Math.PI / 2;
            double dist = target.getBbWidth() * 0.5 + 1.8;
            Vec3 dest = target.position().add(Math.cos(a) * dist, 0, Math.sin(a) * dist);
            if (!caster.level().noCollision(caster, caster.getDimensions(caster.getPose()).makeBoundingBox(dest))) return;
            Vec3 old = caster.position();
            Vec3 to = target.position().subtract(dest);
            float yaw = (float) Math.toDegrees(Math.atan2(-to.x, to.z));
            if (caster instanceof ServerPlayer sp) sp.teleportTo(sp.serverLevel(), dest.x, dest.y, dest.z, yaw, sp.getXRot());
            else caster.teleportTo(dest.x, dest.y, dest.z);
            if (caster.level() instanceof ServerLevel sl) {
                sl.sendParticles(GsFx.dust(PHOTON_CORE, 1.0F), old.x, old.y + 1, old.z, 10, 0.2, 0.5, 0.2, 0);
                sl.sendParticles(ParticleTypes.END_ROD, dest.x, dest.y + 1, dest.z, 6, 0.2, 0.4, 0.2, 0.05);
                GsFx.sound(sl, dest, SoundEvents.ENDERMAN_TELEPORT, 0.4F, 1.8F + 0.05F * turn);
            }
        }
    }

    private GgoForms() {}
}
